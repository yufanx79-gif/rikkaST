package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.handleTextGenerationResult
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.preset.SpresetCompat
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.findSafeInsertIndex
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.assembleContext
import me.rerere.rikkahub.data.model.assembleCharacterCardMessages
import me.rerere.rikkahub.data.model.assembleMainPrompt
import me.rerere.rikkahub.data.model.buildExampleMessages
import me.rerere.rikkahub.data.model.effectiveMaxToolSteps
import me.rerere.rikkahub.data.model.effectiveToolPolicy
import me.rerere.rikkahub.data.export.parseActiveStPresetConfig
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024
private const val MAX_PROVIDER_NETWORK_RETRIES = 3
private const val INITIAL_PROVIDER_RETRY_DELAY_MS = 1_000L

private class StreamChunkHandlingException(cause: Throwable) : RuntimeException(cause)

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationLoop(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
) {
    /** [v234 I1] STREAM_TOKEN_RECEIVED 节流时间戳（每次生成重置；~150ms 采样间隔） */
    private var lastStreamTokenEmitAt: Long = 0L

    fun describeTool(name: String): String = when {
        name.startsWith("execute_python") -> "🔧 Python → 正在执行代码..."
        name.startsWith("execute_command") -> "🔧 Shell → 正在执行命令..."
        name == "file" -> "🔧 文件 → 正在操作..."
        name.startsWith("database_") -> "🔧 数据库 → 正在查询..."
        name.startsWith("search_web") || name.startsWith("scrape_") -> "🔧 搜索 → 正在搜索..."
        name.startsWith("use_skill") -> "🔧 知识 → 正在读取..."
        name.startsWith("clipboard") -> "🔧 剪贴板 → 正在操作..."
        name.startsWith("get_time") -> "🔧 时间 → 获取中..."
        name.startsWith("text_to_speech") -> "🔧 语音 → 正在朗读..."
        name.startsWith("present_file") -> "🔧 文件 → 正在分享..."
        name.startsWith("eval_javascript") -> "🔧 JS → 正在执行..."
        name.startsWith("memory_") -> "🔧 记忆 → 正在处理..."
            else -> "🔧 $name → 正在处理..."
    }

    /**
     * 缓存 system prompt（循环不变，避免每步重建 PromptContext + tool.systemPrompt）
     */
    suspend fun buildCachedSystemPrompt(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        memories: List<AssistantMemory>,
        conversationSystemPrompt: String?,
        tools: List<Tool>,
        model: Model,
        context: android.content.Context,
        conversationRepo: me.rerere.rikkahub.data.repository.ConversationRepository,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
    ): List<UIMessage> {
        val activePersona = settings.personas
            .find { it.id == settings.activePersonaId }
            ?.takeIf { it.enabled && (it.lockedCharacterIds.isEmpty() || assistant.id in it.lockedCharacterIds) }
        val personaDesc = activePersona?.description?.takeIf { it.isNotBlank() } ?: ""
        // 官方：人设只在 IN_PROMPT 位置通过 {{persona}} 嵌入系统提示词，其余位置由独立消息注入
        val personaDescForPrompt = if (activePersona?.position == me.rerere.rikkahub.data.model.PersonaInjectionPosition.IN_PROMPT) {
            personaDesc
        } else {
            ""
        }
        val userName = settings.displaySetting.userNickname.ifBlank { "User" }

        // 官方 Chat Completion 结构：默认模板的角色卡拆成独立消息（主提示 + 角色卡字段）
        // 默认模板（空 或 与内置默认完全一致）才按官方拆分；contextTemplate 无 UI 入口，
        // 默认值就是内置模板文本，因此绝大多数角色卡都走官方拆分
        val useOfficialSplit = assistant.tavernData != null &&
            (assistant.contextTemplate.isBlank() ||
                assistant.contextTemplate.trim() == me.rerere.rikkahub.data.model.DEFAULT_CONTEXT_TEMPLATE)
        val conversationOverride = assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()

        val activePresetCfg = parseActiveStPresetConfig(
            settings.tavernPresets,
            enabledPresetGroups(assistant, settings, conversationModeInjectionIds),
        )
        val mainIdentity = if ("main" in activePresetCfg.disabledMarkers && !conversationOverride) {
            ""
        } else if (conversationOverride) {
            conversationSystemPrompt
        } else if (useOfficialSplit) {
            // 官方默认 Main Prompt：角色卡没有 system_prompt 时必须明确"下一句由角色回复"，
            // 否则模型只能靠内容猜角色——/sendas 等插入助手消息后会把角色认反
            assistant.assembleMainPrompt(
                forbidOverrides = activePresetCfg.forbidOverridesMain,
                presetMainPrompt = activePresetCfg.mainPromptContent,
            ).ifBlank {
                "Write ${assistant.name}'s next reply in a fictional chat between ${assistant.name} and $userName."
            }
        } else if (assistant.tavernData != null) {
            assistant.assembleContext(userName = userName, personaDesc = personaDescForPrompt)
        } else {
            assistant.systemPrompt
        }

        // 酒馆角色卡模式（assistant.tavernData != null）下不注入 Coding Agent 的 <tool_selection> / <work_ethic> / Working directory，
        // 避免污染角色扮演人设与文风。
        val mainText = if (assistant.tavernData != null) {
            buildString {
                append(mainIdentity)
                if (assistant.enableRecentChatsReference) {
                    val recent = buildRecentChatsPrompt(assistant, conversationRepo)
                    if (recent.isNotBlank()) {
                        if (isNotEmpty()) appendLine()
                        append(recent)
                    }
                }
                if (tools.isNotEmpty()) {
                    if (isNotEmpty()) appendLine()
                    appendLine("除非用户明确要求执行搜索、读取文件、运行命令等操作，否则不要调用工具；用叙事直接回应用户，不要用工具代替角色扮演。")
                    tools.forEach { tool ->
                        if (isNotEmpty()) appendLine()
                        append(tool.systemPrompt(model, messages))
                    }
                }
            }
        } else {
            val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
                identitySection = mainIdentity,
                leadInInstructions = if (tools.isEmpty()) "" else buildString {
                    appendLine("<tool_selection>")
                    appendLine("Workspace files → workspace_read/write/edit (/workspace/...)")
                    appendLine("Workspace shell → workspace_shell (git, builds, Unix tools in sandbox)")
                    appendLine("Device files → file action=\"read/write/patch/list/search/copy/move/delete\" (Download/skills dirs)")
                    appendLine("Device shell → execute_command (logcat, device info only)")
                    appendLine("Python → execute_python (data processing, API)")
                    appendLine("Math → calculator (NOT execute_python)")
                    appendLine("Web → web_search / web_fetch")
                    appendLine("Memory → memory_tool")
                    appendLine("</tool_selection>")
                    appendLine()
                    appendLine("<work_ethic>")
                    appendLine("❌ Do NOT describe what you will do — just do it")
                    appendLine("❌ Do NOT stop after writing a stub — complete then report")
                    appendLine("❌ Do NOT fabricate results — if a tool fails, say so")
                    appendLine("❌ Do NOT use shell when a dedicated tool exists")
                    appendLine("❌ Do NOT use execute_python for math (use calculator)")
                    appendLine("✅ If you need user input, use ask_user directly")
                    appendLine("</work_ethic>")
                    appendLine()
                },
                workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
                extraInstructions = buildString {
                    if (assistant.enableRecentChatsReference) {
                        appendLine()
                        append(buildRecentChatsPrompt(assistant, conversationRepo))
                    }
                },
                constraints = emptyList(),
            )
            val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)
            buildString {
                append(system)
                tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(model, messages))
                }
            }
        }
        return buildList {
            if (mainText.isNotBlank()) {
                add(UIMessage.system(prompt = mainText))
            }
            // 官方拆分：角色卡字段独立消息（按预设 prompt_order 插槽顺序与启停控制装配 charDescription/charPersonality/scenario）
            if (useOfficialSplit && !conversationOverride) {
                addAll(
                    assistant.assembleCharacterCardMessages(
                        personalityFormat = activePresetCfg.personalityFormat,
                        scenarioFormat = activePresetCfg.scenarioFormat,
                        orderedMarkers = activePresetCfg.orderedMarkers,
                        disabledMarkers = activePresetCfg.disabledMarkers,
                    )
                )
            }
        }
    }

    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 16,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationId: Uuid? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        generationType: me.rerere.rikkahub.data.model.GenerationType = me.rerere.rikkahub.data.model.GenerationType.NORMAL,
        maxTokensOverride: Int? = null,
        /** messageId → swipe 元数据（宏 `{{currentSwipeId}}` / `{{lastSwipeId}}` 数据源） */
        swipeMeta: Map<Uuid, me.rerere.rikkahub.data.model.SwipeMeta> = emptyMap(),
    ): Flow<GenerationChunk> = flow {
        // [v222] ST `GENERATION_STARTED`（vendor/st/events.js:23）：生成流真正开始的第一件事。
        // 走可复用的事件出口（焦点守卫 + developerMode 懒同步），codex 与 ChatService 的语义一致。
        TavernRuntimeManager.fireConversationEvent(
            conversationId = conversationId?.toString(),
            name = TavernRuntimeManager.EVENT_GENERATION_STARTED,
            payloadJson = "{\"args\":[]}",
            developerMode = settings.developerMode,
        )
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages
        val toolPolicy = assistant.effectiveToolPolicy()
        val effectiveTools = if (toolPolicy == me.rerere.rikkahub.data.model.ToolPolicy.NONE) emptyList<Tool>() else tools
        val effectiveMaxSteps = assistant.effectiveMaxToolSteps().let {
            if (toolPolicy == me.rerere.rikkahub.data.model.ToolPolicy.NONE) 1 else it
        }.coerceAtLeast(1)

    // ── 预构建：tools + systemPrompt（循环不变，移到外面）──
    val toolsInternal: List<Tool> = buildList {
        Log.i(TAG, "generateInternal: build tools($assistant) enabled=${toolPolicy != me.rerere.rikkahub.data.model.ToolPolicy.NONE}")
        if (assistant?.enableMemory == true && toolPolicy != me.rerere.rikkahub.data.model.ToolPolicy.NONE) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            addAll(
                buildMemoryTools(
                    repository = memoryRepo,
                    assistantId = memoryAssistantId,
                )
            )
        }
        addAll(effectiveTools)
    }
    val statusTrackedTools: List<Tool> = toolsInternal.map { tool ->
        if (tool.name == "ask_user") tool else tool.copy(
            execute = { args ->
                processingStatus.value = describeTool(tool.name)
                try {
                    val result = tool.execute(args)
                    processingStatus.value = null
                    result
                } catch (e: Exception) {
                    processingStatus.value = null
                    throw e
                }
            }
        )
    }
    // ── 预构建：system 消息列表（循环不变，移到外面）──
    val prebuiltSystemMessages: List<UIMessage> = buildCachedSystemPrompt(assistant, settings, messages, memories ?: emptyList(), conversationSystemPrompt, effectiveTools, model, context, conversationRepo, conversationModeInjectionIds)

        for (stepIndex in 0 until effectiveMaxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()

            val toolsToProcess: List<UIMessagePart.Tool>

            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings,
                            conversationId = conversationId,
                            workspaceCwd = workspaceCwd,
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings,
                                    conversationId = conversationId,
                                    workspaceCwd = workspaceCwd,
                                )
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = statusTrackedTools,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    conversationId = conversationId,
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                    prebuiltSystemMessages = prebuiltSystemMessages,
                    workspaceCwd = workspaceCwd,
                    generationType = generationType,
                    maxTokensOverride = maxTokensOverride,
                    swipeMeta = swipeMeta,
                    isFinalStep = stepIndex == effectiveMaxSteps - 1,
                )
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings,
                    conversationId = conversationId,
                    workspaceCwd = workspaceCwd,
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings,
                    conversationId = conversationId,
                    workspaceCwd = workspaceCwd,
                )
                // [v243 BUGFIX B1] 空列表防御：下面两处 last() 在空列表上会抛 NoSuchElementException。
                // 放宽 StreamChunkHandler.handle() 的 require(messages.isNotEmpty()) 之后，
                // 空列表可以走到这里（重新生成第 0 条助手消息就是空列表起步）；
                // 若模型又恰好返回零个 chunk（内容过滤 / 空响应），旧代码会在这里崩。
                // 没有助手消息可写时直接结束本轮，不要崩。
                if (messages.isEmpty()) break
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                val toolCalls = messages.last().getTools().filter { !it.isExecuted }
                if (toolCalls.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // 1. Deduplicate tools: same (toolName, input) only execute once
                val seenTools = mutableSetOf<Pair<String, String>>()
                val uniqueTools = toolCalls.filter { tool ->
                    val key = tool.toolName to tool.input
                    if (key in seenTools) {
                        Log.w(TAG, "Deduplicated duplicate tool call: ${tool.toolName}")
                        false
                    } else {
                        seenTools.add(key)
                        true
                    }
                }

                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = uniqueTools.map { tool ->
                    val toolDef = statusTrackedTools.find { it.name == tool.toolName }
                    when {
                        // Tool needs approval and state is Auto -> set to Pending
                        toolDef?.needsApproval(tool.inputAsJson()) == true && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }

                // If any tools were updated to Pending, update the message and break
                if (updatedTools != uniqueTools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                // 3. Guardrail: 同批同名工具超限时「只丢弃超限那些」，不要整批 break。
                //    [P0 修复] 旧实现 break 发生在执行之前：同批读两个文件（参数不同，去重救不了）
                //    → 两个都不执行、循环结束、无正文且无提示。
                val perToolLimit = assistant.toolRecurringLimit.coerceAtLeast(1)
                val seenPerTool = HashMap<String, Int>()
                val keptTools = ArrayList<UIMessagePart.Tool>(updatedTools.size)
                val droppedTools = ArrayList<UIMessagePart.Tool>()
                for (tool in updatedTools) {
                    val n = (seenPerTool[tool.toolName] ?: 0) + 1
                    seenPerTool[tool.toolName] = n
                    if (n > perToolLimit) droppedTools += tool else keptTools += tool
                }
                val uniqueForExecution = keptTools
                if (droppedTools.isNotEmpty()) {
                    TavernRuntimeManager.appendLog(
                        "warn",
                        "[guardrail] dropped ${droppedTools.size} recurring tool call(s) over limit $perToolLimit: " +
                            droppedTools.map { it.toolName }.distinct(),
                    )
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = uniqueForExecution
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                // [P0 修复] 续跑的工具必须在 toolsInternal 里真的有实现。酒馆卡默认 ToolPolicy.NONE
                // （toolsInternal 为空）+ effectiveMaxSteps 被压到 1 时，旧实现会「跳过请求 -> 执行一个
                // 查不到的工具 -> 循环结束」，整轮一次 chat completion 都不发且没有任何提示。
                val byName = toolsInternal.associateBy { it.name }
                toolsToProcess = messages.last().getTools()
                    .filter { it.canResumeExecution }
                    .filter { byName.containsKey(it.toolName) }
                TavernRuntimeManager.appendLog(
                    "info",
                    "[resume] resumable=${pendingTools.size} executable=${toolsToProcess.size} " +
                        "registered=${toolsInternal.map { it.name }}",
                )
            }

            // Handle tools (execute approved tools, handle denied tools)
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            val isParallel = assistant.enableParallelToolExecution && toolsToProcess.size > 1

            if (isParallel) {
                // 并行执行所有工具
                coroutineScope {
                    val deferreds = toolsToProcess.map { tool ->
                        async {
                            tool to runCatching {
                                kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                                    executeToolCall(tool, toolsInternal, json)
                                }
                            }
                        }
                    }
                    deferreds.forEach { deferred ->
                        val (tool, result) = deferred.await()
                        addToolResult(executedTools, tool, result, json)
                    }
                }
            } else {
                // 顺序执行（原版行为）
                toolsToProcess.forEach { tool ->
                    val result = runCatching {
                        kotlinx.coroutines.withTimeout(assistant.toolExecTimeout * 1000L) {
                            executeToolCall(tool, toolsInternal, json)
                        }
                    }
                    addToolResult(executedTools, tool, result, json)
                }
            }

            if (executedTools.isEmpty()) {
                // [P0 修复] 不能静默 break：pendingTools 非空但我们一个都执行不了时，
                // 说明工具实现缺失，必须给出可见错误，而不是「转圈 -> 什么都没有」。
                if (pendingTools.isNotEmpty() && toolsToProcess.isEmpty()) {
                    throw GenerationAbortedException(
                        reason = "待续跑的工具没有可用实现（${pendingTools.map { it.toolName }.distinct().joinToString()}）",
                    )
                }
                // No results to add (all tools were pending)
                break
            }

            // 2.5.6 上游新增：工具输出过长时落盘并截断（仅在有 shell 访问时）
            val hasShellAccess = toolsInternal.any { it.name == "workspace_shell" }
            val finalExecutedTools = executedTools.map { tool ->
                if (tool.approvalState is ToolApprovalState.Denied ||
                    tool.approvalState is ToolApprovalState.Answered
                ) {
                    tool
                } else {
                    tool.copy(
                        output = maybeTruncateToolOutput(tool.toolCallId, tool.output, hasShellAccess)
                    )
                }
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    finalExecutedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings,
                        conversationId = conversationId,
                        workspaceCwd = workspaceCwd,
                    )
                )
            )
        }

        // ── [P0 修复] 收尾请求 ────────────────────────────────────────────────
        // 步数耗尽 / REQUIRED / guardrail 都可能让整轮结束时「只有工具卡、没有正文」。
        // 这里补一次不带工具的请求，保证用户至少拿到一段叙事；仍无正文就明确报错，
        // 而不是让用户看到「转圈结束、什么都没有」。
        val lastAssistant = messages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
        val hasNarration = lastAssistant?.parts?.any {
            it is UIMessagePart.Text && it.text.isNotBlank()
        } ?: false
        if (!hasNarration && lastAssistant != null &&
            lastAssistant.parts.any { it is UIMessagePart.Tool }
        ) {
            TavernRuntimeManager.appendLog(
                "info",
                "[final-turn] no narration after ${effectiveMaxSteps} step(s), requesting a tool-free completion",
            )
            generateInternal(
                assistant = assistant,
                settings = settings,
                messages = messages,
                onUpdateMessages = {
                    messages = it.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings,
                        conversationId = conversationId,
                        workspaceCwd = workspaceCwd,
                    )
                    emit(
                        GenerationChunk.Messages(
                            messages.visualTransforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant,
                                settings = settings,
                                conversationId = conversationId,
                                workspaceCwd = workspaceCwd,
                            )
                        )
                    )
                },
                transformers = inputTransformers,
                model = model,
                providerImpl = providerImpl,
                provider = provider,
                tools = emptyList(),
                memories = memories ?: emptyList(),
                stream = assistant.streamOutput,
                processingStatus = processingStatus,
                conversationSystemPrompt = conversationSystemPrompt,
                conversationId = conversationId,
                conversationModeInjectionIds = conversationModeInjectionIds,
                conversationLorebookIds = conversationLorebookIds,
                prebuiltSystemMessages = prebuiltSystemMessages,
                workspaceCwd = workspaceCwd,
                generationType = generationType,
                maxTokensOverride = maxTokensOverride,
                swipeMeta = swipeMeta,
                isFinalStep = true,
            )
            messages = messages.visualTransforms(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant,
                settings = settings,
                conversationId = conversationId,
                workspaceCwd = workspaceCwd,
            )
            val narrated = messages.lastOrNull()?.parts?.any {
                it is UIMessagePart.Text && it.text.isNotBlank()
            } ?: false
            if (!narrated) {
                throw GenerationAbortedException(
                    reason = "工具调用轮数已用尽，模型仍未产出正文",
                )
            }
        }

    }.flowOn(Dispatchers.IO)

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationId: Uuid? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        prebuiltSystemMessages: List<UIMessage> = emptyList(),
        workspaceCwd: String? = null,
        generationType: me.rerere.rikkahub.data.model.GenerationType = me.rerere.rikkahub.data.model.GenerationType.NORMAL,
        maxTokensOverride: Int? = null,
        swipeMeta: Map<Uuid, me.rerere.rikkahub.data.model.SwipeMeta> = emptyMap(),
        /** 当前是否已是本次生成的最后一步：是则不允许再强制 REQUIRED，否则永远出不来正文。 */
        isFinalStep: Boolean = false,
    ) {
        // 工具策略在本函数内解析（generateText 的 toolPolicy 不在此作用域）
        val toolPolicy = assistant.effectiveToolPolicy()
        // ST兼容（/hide）：隐藏消息不进入上下文与提示词（对齐 ST `coreChat = chat.filter(x => !x.is_system ...)`）
        // 例外：启用工具时保留含工具调用的隐藏消息，避免 tool_call/tool 链断裂（对齐 ST canUseTools 分支）
        val visibleMessages = messages.filter { msg ->
            !msg.isHidden || (tools.isNotEmpty() && msg.parts.any { it is UIMessagePart.Tool })
        }
        val limitedChat = visibleMessages.limitContext(assistant.contextMessageLimit)
        // DeepSeek 缓存优化：暂存动态 userContext，在 transforms 之后追加到消息列表末尾（保持前缀缓存稳定）
        var deepseekTailInjection: String? = null
        val transformedMessages = buildList {
            val fallbackSystem = buildString {
                // ── s10: 使用 SystemPromptAssembler 替代硬编码 ──
                val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
                identitySection = buildString {
                    val effectiveSystemPrompt =
                        if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                            conversationSystemPrompt
                        } else {
                            val fallbackPersona = settings.personas
                                .find { it.id == settings.activePersonaId }
                                ?.takeIf { it.enabled && (it.lockedCharacterIds.isEmpty() || assistant.id in it.lockedCharacterIds) }
                            val fallbackPersonaDesc = fallbackPersona?.description?.takeIf { it.isNotBlank() } ?: ""
                            val fallbackPersonaForPrompt =
                                if (fallbackPersona?.position == me.rerere.rikkahub.data.model.PersonaInjectionPosition.IN_PROMPT) {
                                    fallbackPersonaDesc
                                } else {
                                    ""
                                }
                            if (assistant.tavernData != null) {
                                assistant.assembleContext(
                                    userName = settings.displaySetting.userNickname.ifBlank { "User" },
                                    personaDesc = fallbackPersonaForPrompt,
                                )
                            } else {
                                assistant.systemPrompt
                            }
                        }
                    append(effectiveSystemPrompt)
                },
                leadInInstructions = buildString {
                    appendLine("Guidelines:")
                    appendLine("- Prefer dedicated tools over shell commands for file operations")
                    appendLine("- When a tool fails, try an alternative approach before giving up")
                    appendLine("- If you need clarification, ask the user directly")
                },
                workspaceDescription = "Working directory: ${context.filesDir?.absolutePath ?: "."}",
                extraInstructions = buildString {
                    if (assistant.enableRecentChatsReference) {
                        appendLine()
                        append(buildRecentChatsPrompt(assistant, conversationRepo))
                    }
                },
                constraints = emptyList(),
            )
            val system = me.rerere.rikkahub.data.ai.prompts.SystemPromptAssembler.assemble(assemblerContext)

            // ── 工具prompt（追加在 assembler 结果之后）──
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
            }
            if (prebuiltSystemMessages.isNotEmpty()) {
                addAll(prebuiltSystemMessages)
            } else if (fallbackSystem.isNotBlank()) {
                add(UIMessage.system(prompt = fallbackSystem))
            }

            val activePresetCfg = parseActiveStPresetConfig(
                settings.tavernPresets,
                enabledPresetGroups(assistant, settings, conversationModeInjectionIds),
            )
            // ── 官方 mes_example：作为示例消息注入（若预设 prompt_order 中显式禁用 dialogueExamples 则跳过）──
            if (assistant.tavernData != null && "dialogueExamples" !in activePresetCfg.disabledMarkers) {
                addAll(
                    assistant.buildExampleMessages(
                        userName = settings.displaySetting.userNickname.ifBlank { "User" },
                        newExampleChatPrompt = activePresetCfg.newExampleChatPrompt,
                    )
                )
            }

            // ── s10: getUserContext — 用户上下文通过 <system-reminder> UserMessage 注入 ──
            // 酒馆角色卡模式（assistant.tavernData != null）下不插入 <system-reminder> UserMessage，
            // 避免隔断 prebuiltSystemMessages 与 new_chat_prompt 导致 squash_system_messages 失效。
            if (assistant.tavernData == null) {
                val userContext = buildUserContext(memories, assistant, settings)
                if (userContext.isNotBlank()) {
                    if (settings.deepseekCacheOptimization) {
                        deepseekTailInjection = userContext
                    } else {
                        add(UIMessage.user(prompt = userContext))
                    }
                }
            }

            val activeGroupChat = settings.groupChats.firstOrNull { gc ->
                (conversationId != null && gc.conversationId == conversationId) || assistant.id in gc.memberIds
            }
            val startChatPromptRaw = if (activeGroupChat != null && !activePresetCfg.newGroupChatPrompt.isNullOrBlank()) {
                activePresetCfg.newGroupChatPrompt
            } else {
                activePresetCfg.newChatPrompt
            }
            if ((assistant.tavernData != null || activeGroupChat != null) && !startChatPromptRaw.isNullOrBlank()) {
                val groupNames = activeGroupChat?.memberIds?.mapNotNull { mid ->
                    settings.assistants.firstOrNull { it.id == mid }?.name
                }?.joinToString(", ").orEmpty().ifBlank { assistant.name }
                val expandedStartPrompt = startChatPromptRaw
                    .replace("{{group}}", groupNames, ignoreCase = true)
                    .replace("{{charIfNotGroup}}", groupNames, ignoreCase = true)
                    .replace("{{char}}", assistant.name, ignoreCase = true)
                    .replace("{{user}}", settings.displaySetting.userNickname.ifBlank { "User" }, ignoreCase = true)
                add(UIMessage.system(prompt = expandedStartPrompt).copy(isSynthetic = true))
            }
            if ("chatHistory" !in activePresetCfg.disabledMarkers) {
                addAll(limitedChat.withMessageNames())
            }
        }.let { base ->
            val activePresetCfgForPersona = parseActiveStPresetConfig(
                settings.tavernPresets,
                enabledPresetGroups(assistant, settings, conversationModeInjectionIds),
            )
            val persona = settings.personas.find { it.id == settings.activePersonaId }
            if ("personaDescription" !in activePresetCfgForPersona.disabledMarkers &&
                persona != null && persona.enabled && persona.description.isNotBlank() &&
                (persona.lockedCharacterIds.isEmpty() || assistant.id in persona.lockedCharacterIds)
            ) {
                val personaText = "[User Persona]\n${persona.description}"
                when (persona.position) {
                    me.rerere.rikkahub.data.model.PersonaInjectionPosition.IN_PROMPT -> {
                        // 官方拆分路径（主提示词不含人设）才注入独立 SYSTEM 消息；
                        // 自定义上下文模板已通过 {{persona}} 嵌入时不重复注入
                        val template = assistant.contextTemplate.ifBlank { me.rerere.rikkahub.data.model.DEFAULT_CONTEXT_TEMPLATE }
                        val useOfficialSplit = assistant.tavernData != null &&
                            (assistant.contextTemplate.isBlank() ||
                                assistant.contextTemplate.trim() == me.rerere.rikkahub.data.model.DEFAULT_CONTEXT_TEMPLATE)
                        val conversationOverride =
                            assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()
                        val embedded = assistant.tavernData != null && !useOfficialSplit && !conversationOverride &&
                            template.contains("{{persona}}")
                        if (!embedded) {
                            // 官方顺序（promptManagerDefaultPromptOrder）：人设位于 before_char 世界书之后、角色卡字段之前
                            val cardStart = base.indexOfFirst { msg ->
                                msg.annotations.any { it is me.rerere.ai.ui.UIMessageAnnotation.CharacterCardData }
                            }
                            val idx = if (cardStart >= 0) cardStart
                            else (base.indexOfLast { it.role == MessageRole.SYSTEM } + 1).coerceAtLeast(0)
                            base.take(idx) + UIMessage.system(personaText) + base.drop(idx)
                        } else {
                            base
                        }
                    }

                    me.rerere.rikkahub.data.model.PersonaInjectionPosition.AT_DEPTH -> {
                        val depth = persona.depth.coerceAtLeast(0)
                        val idx = findSafeInsertIndex(
                            base,
                            (base.size - minOf(depth, limitedChat.size))
                                .coerceIn(base.size - limitedChat.size, base.size),
                        )
                        val personaMsg = when (persona.role) {
                            MessageRole.ASSISTANT -> UIMessage.assistant(personaText)
                            MessageRole.USER -> UIMessage.user(personaText)
                            else -> UIMessage.system(personaText)
                        }
                        base.take(idx) + personaMsg + base.drop(idx)
                    }

                    else -> base
                }
            } else {
                base
            }
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            conversationModeInjectionIds = conversationModeInjectionIds,
            conversationLorebookIds = conversationLorebookIds,
            processingStatus = processingStatus,
            workspaceCwd = workspaceCwd,
            conversationId = conversationId,
            generationType = generationType,
            chatUserMessageCount = visibleMessages.count { it.role == MessageRole.USER },
            chatMessageCount = limitedChat.size,
        )
        // DeepSeek 缓存优化：把动态上下文（日期/记忆）追加为最后一条 user 消息，
        // 位于全部历史之后 —— 跨天/记忆更新时只有末尾新增部分失效，system+历史前缀全部命中缓存
        val postPresetCfg = parseActiveStPresetConfig(
            settings.tavernPresets,
            enabledPresetGroups(assistant, settings, conversationModeInjectionIds),
        )
        var internalMessages = deepseekTailInjection
            ?.takeIf { settings.deepseekCacheOptimization }
            ?.let { transformedMessages + UIMessage.user(prompt = it) }
            ?: transformedMessages
        val postGroupChat = settings.groupChats.firstOrNull { gc ->
            (conversationId != null && gc.conversationId == conversationId) || assistant.id in gc.memberIds
        }
        if (postGroupChat != null && !postPresetCfg.groupNudgePrompt.isNullOrBlank() &&
            generationType != me.rerere.rikkahub.data.model.GenerationType.IMPERSONATE
        ) {
            val groupNames = postGroupChat.memberIds.mapNotNull { mid ->
                settings.assistants.firstOrNull { it.id == mid }?.name
            }.joinToString(", ").ifBlank { assistant.name }
            val expandedGroupNudge = postPresetCfg.groupNudgePrompt
                .replace("{{group}}", groupNames, ignoreCase = true)
                .replace("{{charIfNotGroup}}", groupNames, ignoreCase = true)
                .replace("{{char}}", assistant.name, ignoreCase = true)
                .replace("{{user}}", settings.displaySetting.userNickname.ifBlank { "User" }, ignoreCase = true)
            internalMessages = internalMessages + UIMessage.system(prompt = expandedGroupNudge).copy(isSynthetic = true)
        }
        if (postPresetCfg.wrapInQuotes && internalMessages.isNotEmpty()) {
            val lastIdx = internalMessages.indexOfLast { it.role == MessageRole.USER && !it.isSynthetic }
            if (lastIdx >= 0) {
                val uMsg = internalMessages[lastIdx]
                val updatedParts = uMsg.parts.map { p ->
                    if (p is UIMessagePart.Text) {
                        val t = p.text.trim()
                        if (t.isNotEmpty() && !t.startsWith("\"") && !t.endsWith("\"")) {
                            p.copy(text = "\"$t\"")
                        } else p
                    } else p
                }
                internalMessages = internalMessages.toMutableList().apply { set(lastIdx, uMsg.copy(parts = updatedParts)) }
            }
        }
        val combinedPrefill = listOfNotNull(
            postPresetCfg.startReplyWith.takeIf { it.isNotBlank() },
            postPresetCfg.assistantPrefill?.takeIf { it.isNotBlank() },
        ).distinct().joinToString("")
        if (combinedPrefill.isNotBlank() &&
            generationType != me.rerere.rikkahub.data.model.GenerationType.IMPERSONATE
        ) {
            internalMessages = internalMessages + UIMessage.assistant(combinedPrefill).copy(isSynthetic = true)
        }
        val squashSep = postPresetCfg.spresetChatSquashSeparator.ifEmpty { "\n\n" }
        val spresetCfg = postPresetCfg.spresetConfig
        if (spresetCfg.enabled) {
            // MessageInjections 属于预设请求结构的一部分，必须先于 ChatSquash 折叠。
            internalMessages = runCatching {
                SpresetCompat.applyMessageInjections(internalMessages, spresetCfg)
            }.getOrDefault(internalMessages)
        }
        val chatSquash = spresetCfg.chatSquash
        if (spresetCfg.enabled && chatSquash?.enabled == true && internalMessages.size > 1) {
            // SPreset ChatSquash 完整语义（conditional 块合并 / 旧版角色合并、
            // prefix/suffix、<|sep|>、squashed_post_script）。失败时保留原消息。
            internalMessages = runCatching {
                SpresetCompat.applyChatSquash(
                    messages = internalMessages,
                    cfg = chatSquash,
                    userName = settings.displaySetting.userNickname.ifBlank { "User" },
                    charName = assistant.name,
                )
            }.getOrDefault(internalMessages)
        } else if (postPresetCfg.squashSystemMessages && internalMessages.size > 1) {
            internalMessages = squashAdjacentSystemMessages(internalMessages, squashSep)
        }
        val forcedPostMode = spresetCfg.forcedPostProcessing
            ?.takeIf { spresetCfg.enabled && it.enabled && it.mode.isNotBlank() }
            ?.mode
        val effectivePostProcessing = forcedPostMode ?: postPresetCfg.promptPostProcessing
        if (effectivePostProcessing.isNotBlank() && internalMessages.size > 1) {
            internalMessages = applyPromptPostProcessing(internalMessages, effectivePostProcessing, squashSep)
        }
        // [v211] ST 生成前钩子（CHAT_COMPLETION_SETTINGS_READY）：让扩展（ST-PT 的 EJS 模板 /
        //  MVU 的 <UpdateVariable> 剥离）就地改写最终 messages。ST 契约：openai.js:1616-1623
        //   const eventData = { chat, dryRun }; await eventSource.emit(CHAT_COMPLETION_PROMPT_READY, eventData);
        //   return [chat, ...]  ← 改写后的结果继续用。ST-PT handler.ts:980 监听 SETTINGS_READY 并
        //   data.messages = await processGenerateAfter(data.messages)；MVU bundle 5 处监听（含 eventMakeLast）。
        // 仅对酒馆卡助手生效；WebView 未就绪 / 超时 / 无监听器 → null → 原样直通（零开销）。
        if (assistant.tavernData != null || TavernRuntimeManager.isStarted) {
            var abortedByInterceptor = false
            runCatching {
                val payload = me.rerere.rikkahub.data.st.runtime.buildStMessagesJson(internalMessages)
                val rewritten = me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.runGenerationHooks(payload)
                if (rewritten != null) {
                    val hookOutcome = runCatching { me.rerere.rikkahub.data.st.runtime.parseStGenerationHookResult(rewritten) }.getOrNull()
                    if (hookOutcome != null && hookOutcome.aborted) {
                        abortedByInterceptor = true
                        TavernRuntimeManager.appendLog("info", "[gen-hooks] generation aborted by extension generate_interceptor")
                    } else if (hookOutcome != null && hookOutcome.messages.isNotEmpty()) {
                        internalMessages = hookOutcome.messages
                        TavernRuntimeManager.appendLog("info", "[gen-hooks] messages rewritten by extensions (${hookOutcome.messages.size})")
                    } else {
                        TavernRuntimeManager.appendLog("warn", "[gen-hooks] rewrite rejected (empty messages or parse failed)")
                    }
                } else {
                    TavernRuntimeManager.appendLog("debug", "[gen-hooks] no rewrite (null/timeout/not-ready)")
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                TavernRuntimeManager.appendLog("error", "[gen-hooks] failed: ${e.message}")
            }
            if (abortedByInterceptor) {
                TavernRuntimeManager.recordSendDiagnostic(
                    stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                    conversationId = conversationId?.toString().orEmpty(),
                    detail = "status=ABORTED_BY_INTERCEPTOR",
                )
                // [P0 修复] 旧实现直接 return：flow 正常完成 -> ChatService 记 SUCCESS ->
                // 用户只看到转圈消失、没有请求、没有报错。改为抛出，由 onFailure 走 addError。
                throw GenerationAbortedException("扩展中止了本次生成（generate_interceptor）")
            }
        }
        var messages: List<UIMessage> = messages
        var params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature ?: postPresetCfg.temperature,
            topP = assistant.topP ?: postPresetCfg.topP,
            maxTokens = maxTokensOverride ?: assistant.maxTokens ?: postPresetCfg.maxTokens,
            tools = tools,
            // [P0 修复] REQUIRED 最后一步必须切回 AUTO：循环唯一的正常出口是「本轮没有 tool call」，
            // 每步都强制工具就永远拿不到叙事正文。
            toolChoice = if (toolPolicy == me.rerere.rikkahub.data.model.ToolPolicy.REQUIRED && !isFinalStep) {
                me.rerere.ai.provider.ToolChoice.REQUIRED
            } else {
                me.rerere.ai.provider.ToolChoice.AUTO
            },
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildPresetMergedCustomBodies(
                assistantBodies = assistant.customBodies,
                modelBodies = model.customBodies,
                preset = postPresetCfg,
            ),
            sessionId = (conversationId ?: Uuid.random()).toString(),
        )
        // SPreset FinalRequestProcessing：在请求发出前执行预设私有 JS。
        // 典型用途：把 system 中的 <run_tool_replacement> 标记与 assistant 长上下文
        // 折叠成 { system, user } + 一个 context 工具描述；失败时原请求直通。
        val finalRequestProcessor = spresetCfg.finalRequestProcessing
        if (spresetCfg.enabled && finalRequestProcessor.enabled && finalRequestProcessor.script.isNotBlank() &&
            toolPolicy != me.rerere.rikkahub.data.model.ToolPolicy.NONE
        ) {
            val rewritten = runCatching {
                SpresetCompat.applyFinalRequestProcessing(
                    messages = internalMessages,
                    tools = params.tools,
                    p = finalRequestProcessor,
                )
            }.getOrNull()
            if (rewritten != null && rewritten.first.isNotEmpty()) {
                internalMessages = rewritten.first
                params = params.copy(tools = rewritten.second)
            }
        }
        TavernRuntimeManager.recordSendDiagnostic(
            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_6_MODEL_REQUEST_SENT,
            conversationId = conversationId?.toString().orEmpty(),
            detail = "model=${model.modelId} internalMessages=${internalMessages.size} stream=$stream",
        )
        try {
            if (stream) {
                // 每次重试都从本次模型调用开始前的消息快照重新合并，避免将重试响应
                // 追加到已经展示的半截回复后面。预先创建助手消息可让所有尝试复用同一 ID，
                // ChatService 因而会覆盖当前分支，而不是创建新的候选消息。
                val responseBaseMessages =
                    if (messages.lastOrNull()?.role == MessageRole.ASSISTANT) {
                        messages
                    } else {
                        messages + UIMessage(
                            role = MessageRole.ASSISTANT,
                            parts = emptyList(),
                            modelId = model.id,
                        )
                    }
                var retryCount = 0

                while (true) {
                    val streamChunkHandler = StreamChunkHandler(model)
                    var attemptMessages = responseBaseMessages
                    try {
                        SpresetCompat.processStream(
                            providerImpl.streamText(
                                providerSetting = provider,
                                messages = internalMessages,
                                params = params,
                            ),
                            spresetCfg,
                        ).collect { chunk ->
                            try {
                                if (retryCount > 0) {
                                    processingStatus.value = null
                                }
                                attemptMessages = streamChunkHandler.handle(attemptMessages, chunk)
                                onUpdateMessages(attemptMessages)
                                // [v234 I1] ST STREAM_TOKEN_RECEIVED（script.js:3901）：载荷 = 累积全文（this.result 语义）。
                                // 逐 token 类事件先量化再放量（红线 6）：developerMode 守卫 + ~150ms 节流。
                                if (settings.developerMode && chunk is me.rerere.ai.ui.StreamChunk.TextDelta) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastStreamTokenEmitAt >= 150) {
                                        lastStreamTokenEmitAt = now
                                        val accText = attemptMessages.lastOrNull()?.parts
                                            ?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text } ?: ""
                                        TavernRuntimeManager.fireConversationEvent(
                                            conversationId = conversationId?.toString(),
                                            name = TavernRuntimeManager.EVENT_STREAM_TOKEN_RECEIVED,
                                            payloadJson = org.json.JSONObject.quote(accText),
                                            developerMode = settings.developerMode,
                                        )
                                    }
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                // 下游消息转换或 UI 更新失败不属于网络故障，不能重放模型请求。
                                throw StreamChunkHandlingException(error)
                            }
                        }
                        messages = runCatching {
                            SpresetCompat.finalizeAssistantOutput(
                                messages = attemptMessages,
                                config = spresetCfg,
                                toolCallsAlreadyFormatted = true,
                            )
                        }.getOrDefault(attemptMessages)
                        onUpdateMessages(messages)
                        break
                    } catch (error: Throwable) {
                        if (error is StreamChunkHandlingException) {
                            throw error.cause ?: error
                        }
                        retryCount = awaitNetworkRetryOrThrow(
                            error = error,
                            retryCount = retryCount,
                            processingStatus = processingStatus,
                            enabled = settings.networkSetting.enableAutoRetry,
                        )
                    }
                }
            } else {
                val result = executeProviderRequestWithRetry(
                    processingStatus = processingStatus,
                    enabled = settings.networkSetting.enableAutoRetry,
                ) {
                    providerImpl.generateText(
                        providerSetting = provider,
                        messages = internalMessages,
                        params = params,
                    )
                }
                messages = messages.handleTextGenerationResult(result = result, model = model)
                messages = runCatching {
                    SpresetCompat.finalizeAssistantOutput(
                        messages = messages,
                        config = spresetCfg,
                        toolCallsAlreadyFormatted = false,
                    )
                }.getOrDefault(messages)
                onUpdateMessages(messages)
            }
        } finally {
            processingStatus.value = null
        }
    }

    private suspend fun <T> executeProviderRequestWithRetry(
        processingStatus: MutableStateFlow<String?>,
        enabled: Boolean,
        block: suspend () -> T,
    ): T {
        var retryCount = 0
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                retryCount = awaitNetworkRetryOrThrow(
                    error = error,
                    retryCount = retryCount,
                    processingStatus = processingStatus,
                    enabled = enabled,
                )
            }
        }
    }

    private suspend fun awaitNetworkRetryOrThrow(
        error: Throwable,
        retryCount: Int,
        processingStatus: MutableStateFlow<String?>,
        enabled: Boolean,
    ): Int {
        // 用户主动停止生成时，底层连接也可能以 IOException("canceled") 收尾；
        // 先检查协程状态，且若异常本身即为 Canceled 也直接按取消抛出，确保取消绝不会被当作网络波动重新拉起。
        currentCoroutineContext().ensureActive()
        if (error is CancellationException) throw error
        if (error is IOException && error.message?.contains("cancel", ignoreCase = true) == true) {
            throw CancellationException("Network call canceled", error)
        }
        if (!enabled || error !is IOException || retryCount >= MAX_PROVIDER_NETWORK_RETRIES) {
            throw error
        }

        val nextRetryCount = retryCount + 1
        val retryDelay = INITIAL_PROVIDER_RETRY_DELAY_MS shl retryCount
        processingStatus.value = context.getString(
            R.string.chat_generation_network_retrying,
            getNetworkErrorMessage(error),
            nextRetryCount,
            MAX_PROVIDER_NETWORK_RETRIES,
        )
        Log.w(
            TAG,
            "Provider connection failed, retrying in ${retryDelay}ms " +
                    "($nextRetryCount/$MAX_PROVIDER_NETWORK_RETRIES)",
            error,
        )
        delay(retryDelay)
        return nextRetryCount
    }

    private fun getNetworkErrorMessage(error: IOException): String {
        val messageRes = when (error) {
            is UnknownHostException -> R.string.chat_generation_network_unknown_host
            is SocketTimeoutException -> R.string.chat_generation_network_timeout
            is ConnectException, is NoRouteToHostException -> R.string.chat_generation_network_unreachable
            else -> R.string.chat_generation_network_disconnected
        }
        return context.getString(messageRes)
    }

    private fun maybeTruncateToolOutput(
        toolCallId: String,
        output: List<UIMessagePart>,
        hasShellAccess: Boolean,
    ): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val nonTextParts = output.filter { it !is UIMessagePart.Text }
        val totalChars = textParts.sumOf { it.text.length }

        if (totalChars <= MAX_TOOL_OUTPUT_CHARS || !hasShellAccess) return output

        Log.i(TAG, "maybeTruncateToolOutput: truncating tool $toolCallId output ($totalChars chars)")

        val fullText = textParts.joinToString("\n") { it.text }
        val preview = fullText.take(TOOL_OUTPUT_PREVIEW_CHARS)

        val fileName = "${toolCallId}.txt"
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(preview)
                }
            )
        ) + nonTextParts
    }

}

/**
 * 执行单个工具调用（提取逻辑以避免并行/串行分支重复）
 */
private suspend fun executeToolCall(
    tool: UIMessagePart.Tool,
    toolsInternal: List<Tool>,
    json: kotlinx.serialization.json.Json,
): UIMessagePart.Tool {
    return when (tool.approvalState) {
        is ToolApprovalState.Denied -> {
            val reason = (tool.approvalState as ToolApprovalState.Denied).reason
            tool.copy(
                output = listOf(
                    UIMessagePart.Text(
                        json.encodeToString(
                            buildJsonObject {
                                put(
                                    "error",
                                    JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                )
                            }
                        )
                    )
                )
            )
        }

        is ToolApprovalState.Answered -> {
            val answer = (tool.approvalState as ToolApprovalState.Answered).answer
            tool.copy(
                output = listOf(UIMessagePart.Text(answer))
            )
        }

        is ToolApprovalState.Pending -> tool

        else -> {
            val toolDef = toolsInternal.find { it.name == tool.toolName }
                ?: error("Tool ${tool.toolName} not found")
            val args = runCatching {
                json.parseToJsonElement(tool.input.ifBlank { "{}" })
            }.getOrElse {
                error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}")
            }
            Log.i(TAG, "generateText: executing tool ${toolDef.name} with args: $args")

            val result = toolDef.execute(args)

            tool.copy(output = result)
        }
    }
}

/**
 * 将工具执行结果添加到列表中（处理成功和失败两种情况）
 */
private fun addToolResult(
    executedTools: ArrayList<UIMessagePart.Tool>,
    tool: UIMessagePart.Tool,
    result: Result<UIMessagePart.Tool>,
    json: kotlinx.serialization.json.Json,
) {
    result.onSuccess { executedTools.add(it) }
        .onFailure {
            // 取消必须向上传播，否则用户点“停止”会被误报为工具执行错误
            // （对齐官方 2.5.6 GenerationLoop.kt:273-276）
            // 例外：withTimeout 抛的 TimeoutCancellationException 属于工具超时失败，照常记录
            if (it is CancellationException && it !is TimeoutCancellationException) throw it
            it.printStackTrace()
            executedTools.add(
                tool.copy(
                    output = listOf(
                        UIMessagePart.Text(
                            json.encodeToString(
                                buildJsonObject {
                                    put(
                                        "error",
                                        JsonPrimitive(buildString {
                                            append("[${it.javaClass.name}] ${it.message}")
                                            append("\n${it.stackTraceToString()}")
                                        })
                                    )
                                }
                            )
                        )
                    )
                )
            )
        }
}

/**
 * ── s10: getUserContext ──
 * 对标 Claude Code context.ts → getUserContext() → prependUserContext()
 *
 * CC 源码 (context.ts):
 *   getUserContext = memoize(async (): Promise<{claudeMd, currentDate}> => {
 *     const claudeMd = getClaudeMds(filterInjectedMemoryFiles(await getMemoryFiles()))
 *     return { ...(claudeMd && { claudeMd }), currentDate: "Today's date is ..." }
 *   })
 *
 * CC 源码 (api.ts → prependUserContext):
 *   createUserMessage({
 *     content: `<system-reminder>\nAs you answer the user's questions, you can use the following context:\n${
 *       Object.entries(context).map(([key, value]) => `# ${key}\n${value}`).join('\n')
 *     }\n\nIMPORTANT: this context may or may not be relevant...\n</system-reminder>\n`,
 *     isMeta: true,
 *   })
 *
 * 记忆：整轮对话缓存（memoize），仅当记忆列表变化时重建
 */
private var _lastUserContextKey: String? = null
private var _lastUserContext: String? = null

private fun buildUserContext(
    memories: List<AssistantMemory>,
    assistant: Assistant,
    settings: Settings,
): String {
    val contextMap = linkedMapOf<String, String>()

    // 对标 CC getUserContext: claudeMd (CLAUDE.md content)
    if (assistant.enableMemory && memories.isNotEmpty()) {
        val memoryText = memories.joinToString("\n") { memory ->
            "- ${memory.content.take(200)}"
        }
        contextMap["memories"] = memoryText
    }

    // 对标 CC getUserContext: currentDate
    contextMap["currentDate"] = "Today's date is ${java.time.LocalDate.now()}."

    if (contextMap.isEmpty()) return ""

    // Memoize: 当 contextMap 内容不变时复用
    val key = contextMap.entries.joinToString("|") { "${it.key}=${it.value}" }
    if (key == _lastUserContextKey && _lastUserContext != null) {
        return _lastUserContext!!
    }

    val result = buildString {
        appendLine("<system-reminder>")
        appendLine("As you answer the user's questions, you can use the following context:")
        contextMap.forEach { (key, value) ->
            appendLine("# $key")
            appendLine(value)
        }
        appendLine()
        appendLine("IMPORTANT: this context may or may not be relevant to your tasks. You should not respond to this context unless it is highly relevant to your task.")
        append("</system-reminder>")
    }

    _lastUserContextKey = key
    _lastUserContext = result
    return result
}

/**
 * name= 注入：发送给模型前把消息自带的名字写入内容（对齐官方 names_behavior.DEFAULT），
 * 让 AI 明确知道这条消息是谁说的；本地保存与 UI 保持原始文本不变。
 *
 * 官方规则（openai.js:581-605）：sendas/群聊等 assistant 消息拼 "名字: " 前缀；
 * SYSTEM 角色（narrator，/sys /sysgen）不拼 —— 官方 narrator 消息带独立 name 字段但不进 content，
 * 否则会泄漏 "System: " 污染提示词。
 */
/**
 * 合并相邻的无名称、无工具调用的 SYSTEM 消息（对齐官方 SillyTavern `squash_system_messages` 语义）。
 */
internal fun buildPresetMergedCustomBodies(
    assistantBodies: List<me.rerere.ai.provider.CustomBody>,
    preset: me.rerere.rikkahub.data.export.StActivePresetConfig,
): List<me.rerere.ai.provider.CustomBody> =
    buildPresetMergedCustomBodies(assistantBodies = assistantBodies, modelBodies = emptyList(), preset = preset)

internal fun buildPresetMergedCustomBodies(
    assistantBodies: List<me.rerere.ai.provider.CustomBody>,
    modelBodies: List<me.rerere.ai.provider.CustomBody>,
    preset: me.rerere.rikkahub.data.export.StActivePresetConfig,
): List<me.rerere.ai.provider.CustomBody> {
    val existingKeys = (assistantBodies.map { it.key } + modelBodies.map { it.key }).toSet()
    val presetBodies = mutableListOf<me.rerere.ai.provider.CustomBody>()
    fun putIfAbsent(key: String, value: kotlinx.serialization.json.JsonElement) {
        if (key !in existingKeys) {
            presetBodies += me.rerere.ai.provider.CustomBody(key = key, value = value)
        }
    }
    if (preset.customStoppingStrings.isNotEmpty()) {
        putIfAbsent(
            "stop",
            kotlinx.serialization.json.JsonArray(
                preset.customStoppingStrings.map { kotlinx.serialization.json.JsonPrimitive(it) }
            ),
        )
    }
    if (preset.logitBias.isNotEmpty()) {
        putIfAbsent(
            "logit_bias",
            kotlinx.serialization.json.buildJsonObject {
                preset.logitBias.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
            },
        )
    }
    if (preset.seed >= 0) {
        putIfAbsent("seed", kotlinx.serialization.json.JsonPrimitive(preset.seed))
    }
    preset.frequencyPenalty?.takeIf { it != 0f }?.let {
        putIfAbsent("frequency_penalty", kotlinx.serialization.json.JsonPrimitive(it))
    }
    preset.presencePenalty?.takeIf { it != 0f }?.let {
        putIfAbsent("presence_penalty", kotlinx.serialization.json.JsonPrimitive(it))
    }
    preset.repetitionPenalty?.takeIf { it != 1f }?.let {
        putIfAbsent("repetition_penalty", kotlinx.serialization.json.JsonPrimitive(it))
    }
    preset.topK?.takeIf { it > 0 }?.let {
        putIfAbsent("top_k", kotlinx.serialization.json.JsonPrimitive(it))
    }
    preset.minP?.takeIf { it > 0f }?.let {
        putIfAbsent("min_p", kotlinx.serialization.json.JsonPrimitive(it))
    }
    preset.topA?.takeIf { it > 0f }?.let {
        putIfAbsent("top_a", kotlinx.serialization.json.JsonPrimitive(it))
    }
    if (preset.cfgScale != 1.0f && preset.cfgScale > 0f) {
        putIfAbsent("guidance_scale", kotlinx.serialization.json.JsonPrimitive(preset.cfgScale))
    }
    if (preset.negativePrompt.isNotBlank()) {
        putIfAbsent("negative_prompt", kotlinx.serialization.json.JsonPrimitive(preset.negativePrompt))
    }
    return buildList {
        addAll(presetBodies)
        addAll(assistantBodies)
        addAll(modelBodies)
    }
}

/**
 * 应用 SillyTavern `prompt_post_processing` 消息合并与角色归一化策略：
 * - `merge` / `merge_tools`：合并相邻同角色（SYSTEM/USER/ASSISTANT）纯文本消息；
 * - `semi` / `semi_tools`：合并相邻 SYSTEM 消息；
 * - `strict` / `strict_tools` / `single`：除首条 SYSTEM 消息外，其余 SYSTEM 消息转为 USER，并合并相邻同角色消息。
 */
internal fun applyPromptPostProcessing(
    messages: List<UIMessage>,
    mode: String,
    separator: String = "\n\n",
): List<UIMessage> {
    val norm = mode.trim().lowercase()
    if (norm.isEmpty() || norm == "none" || messages.size <= 1) return messages
    val sep = separator.ifEmpty { "\n\n" }
    if (norm == "semi" || norm == "semi_tools") {
        val squashed = squashAdjacentSystemMessages(messages, sep)
        return squashed.mapIndexed { idx, msg ->
            if (idx > 0 && msg.role == MessageRole.SYSTEM && msg.getTools().isEmpty()) {
                msg.copy(role = MessageRole.USER)
            } else {
                msg
            }
        }
    }
    val roleConverted = if (norm == "strict" || norm == "strict_tools" || norm == "single") {
        messages.mapIndexed { idx, msg ->
            if (idx > 0 && msg.role == MessageRole.SYSTEM && msg.getTools().isEmpty()) {
                msg.copy(role = MessageRole.USER)
            } else {
                msg
            }
        }
    } else {
        messages
    }
    if (norm in setOf("merge", "merge_tools", "strict", "strict_tools", "single")) {
        val merged = mutableListOf<UIMessage>()
        for (msg in roleConverted) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.role == msg.role &&
                prev.name.isNullOrBlank() && msg.name.isNullOrBlank() &&
                prev.getTools().isEmpty() && msg.getTools().isEmpty() &&
                prev.parts.all { it is UIMessagePart.Text } && msg.parts.all { it is UIMessagePart.Text }
            ) {
                val prevText = prev.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                val curText = msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                val combined = listOf(prevText, curText).filter { it.isNotBlank() }.joinToString(sep)
                merged[merged.lastIndex] = prev.copy(parts = listOf(UIMessagePart.Text(combined)))
            } else {
                merged.add(msg)
            }
        }
        return merged
    }
    return messages
}

internal fun squashAdjacentSystemMessages(
    messages: List<UIMessage>,
    separator: String = "\n\n",
): List<UIMessage> {
    if (messages.size <= 1) return messages
    val squashSep = separator.ifEmpty { "\n\n" }
    val squashed = mutableListOf<UIMessage>()
    for (msg in messages) {
        val prev = squashed.lastOrNull()
        if (prev != null && prev.role == MessageRole.SYSTEM && msg.role == MessageRole.SYSTEM &&
            prev.name.isNullOrBlank() && msg.name.isNullOrBlank() &&
            prev.getTools().isEmpty() && msg.getTools().isEmpty()
        ) {
            val prevText = prev.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            val curText = msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            val mergedText = listOf(prevText, curText).filter { it.isNotBlank() }.joinToString(squashSep)
            squashed[squashed.lastIndex] = prev.copy(parts = listOf(UIMessagePart.Text(mergedText)))
        } else {
            squashed.add(msg)
        }
    }
    return squashed
}

private fun List<UIMessage>.withMessageNames(): List<UIMessage> = map { message ->
    if (message.role == MessageRole.SYSTEM) return@map message
    val name = message.name?.takeIf { it.isNotBlank() } ?: return@map message
    val textIndex = message.parts.indexOfFirst { it is UIMessagePart.Text }
    if (textIndex >= 0) {
        val part = message.parts[textIndex] as UIMessagePart.Text
        message.copy(
            parts = message.parts.toMutableList().also { list ->
                list[textIndex] = part.copy(text = "$name: ${part.text}")
            }
        )
    } else {
        message.copy(parts = listOf(UIMessagePart.Text("$name: ")) + message.parts)
    }
}
