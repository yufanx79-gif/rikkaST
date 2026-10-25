package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.st.preset.SpresetCompat
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.workspace.WorkspaceShellStatus

private const val TAG = "ChatToolFactory"

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val json: Json,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwd: String? = null,
        conversationModeInjectionIds: Set<kotlin.uuid.Uuid> = emptySet(),
    ): List<Tool> = buildList {
        if (assistant.enableMemory) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            addAll(
                buildMemoryTools(
                    repository = memoryRepository,
                    assistantId = memoryAssistantId,
                )
            )
        }
        if (shouldUseExternalWebSearch(assistant, model)) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools))
        if (assistant.enableRecentChatsReference) {
            addAll(createConversationTools(conversationRepository, assistant.id))
        }
        addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd))
        if (assistant.enabledSkills.isNotEmpty()) {
            addAll(
                createSkillTools(
                    enabledSkills = assistant.enabledSkills,
                    allSkills = skillManager.listSkills(),
                )
            )
        }

        val mcpTools = mcpManager.getAllAvailableTools(assistant)
        val invalidNames = mcpTools
            .map { it.second }
            .distinct()
            .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
        if (invalidNames.isNotEmpty()) {
            throw InvalidMcpServerNamesException(invalidNames)
        }
        mcpTools.forEach { (serverId, serverName, tool) ->
            add(
                Tool(
                    name = "mcp__${serverName}__${tool.name}",
                    description = tool.description ?: "",
                    parameters = { tool.inputSchema },
                    needsApproval = { tool.needsApproval },
                    execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                )
            )
        }

        // 酒馆兼容层动态注册的 JS 函数工具（registerFunctionTool）与 STscript 闭包工具（/tools-register）：
        // 严格保护原生与 MCP 工具名称不被覆盖（仅在同名工具不存在时追加）。
        val jsTools = me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.getRegisteredJsFunctionTools()
        for (spec in jsTools) {
            if (any { it.name.equals(spec.name, ignoreCase = true) }) continue
            val parsedSchema = parseStToolInputSchema(json, spec.parametersJson)
            add(
                Tool(
                    name = spec.name,
                    description = spec.description.ifBlank { spec.displayName },
                    parameters = { parsedSchema },
                    needsApproval = { false },
                    execute = { argsEl ->
                        val out = me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
                            .executeJsFunctionTool(spec.name, argsEl.toString())
                            ?: ""
                        listOf(me.rerere.ai.ui.UIMessagePart.Text(out))
                    },
                )
            )
        }
        val slashTools = me.rerere.rikkahub.data.st.script.StSlashExecutor.getRegisteredSlashToolNames()
        for (toolName in slashTools) {
            if (any { it.name.equals(toolName, ignoreCase = true) }) continue
            add(
                Tool(
                    name = toolName,
                    description = "STscript registered tool: $toolName",
                    parameters = { me.rerere.ai.core.InputSchema.Obj(properties = kotlinx.serialization.json.JsonObject(emptyMap())) },
                    needsApproval = { false },
                    execute = { argsEl ->
                        val out = me.rerere.rikkahub.data.st.script.StSlashExecutor.invokeRegisteredSlashTool(toolName, argsEl.toString())
                        listOf(me.rerere.ai.ui.UIMessagePart.Text(out))
                    },
                )
            )
        }

        // SPreset ToolBindings：预设内定义的动态工具（如“小猫之神”的 game_content）。
        // 解析/QuickJS 执行失败时静默跳过，绝不阻断聊天。
        runCatching {
            val spresetConfig = SpresetCompat.parseActivePreset(
                tavernPresetsJson = settings.tavernPresets,
                enabledGroups = enabledPresetGroups(assistant, settings, conversationModeInjectionIds),
            )
            val dynamicTools = SpresetCompat.createDynamicTools(
                config = spresetConfig,
                existingNames = mapTo(mutableSetOf()) { it.name },
            )
            dynamicTools.forEach { tool ->
                if (none { it.name.equals(tool.name, ignoreCase = true) }) add(tool)
            }
        }
    }

    internal fun parseStToolInputSchema(
        jsonParser: Json,
        parametersJson: String,
    ): me.rerere.ai.core.InputSchema.Obj {
        return runCatching {
            val root = jsonParser.parseToJsonElement(parametersJson) as? kotlinx.serialization.json.JsonObject
                ?: return@runCatching me.rerere.ai.core.InputSchema.Obj(properties = kotlinx.serialization.json.JsonObject(emptyMap()))
            val props = (root["properties"] as? kotlinx.serialization.json.JsonObject)
                ?: kotlinx.serialization.json.JsonObject(emptyMap())
            val req = (root["required"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotEmpty() } }
            me.rerere.ai.core.InputSchema.Obj(properties = props, required = req)
        }.getOrElse {
            me.rerere.ai.core.InputSchema.Obj(properties = kotlinx.serialization.json.JsonObject(emptyMap()))
        }
    }

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String?): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
    }
}
