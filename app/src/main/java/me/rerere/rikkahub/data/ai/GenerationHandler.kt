package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.handleTextGenerationResult
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.findSafeInsertIndex
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.assembleContext
import me.rerere.rikkahub.data.model.assembleCharacterCardMessages
import me.rerere.rikkahub.data.model.assembleMainPrompt
import me.rerere.rikkahub.data.model.buildExampleMessages
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
) {
    /** [v234 I1] STREAM_TOKEN_RECEIVED 节流时间戳（每次生成重置；~150ms 采样间隔） */
    private var lastStreamTokenEmitAt: Long = 0L
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        workspaceCwd: String? = null,
        conversationId: Uuid? = null,
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

        val mainIdentity = if (conversationOverride) {
            conversationSystemPrompt
        } else if (useOfficialSplit) {
            // 官方默认 Main Prompt：角色卡没有 system_prompt 时必须明确"下一句由角色回复"，
            // 否则模型只能靠内容猜角色——/sendas 等插入助手消息后会把角色认反
            assistant.assembleMainPrompt().ifBlank {
                "Write ${assistant.name}'s next reply in a fictional chat between ${assistant.name} and $userName."
            }
        } else if (assistant.tavernData != null) {
            assistant.assembleContext(userName = userName, personaDesc = personaDescForPrompt)
        } else {
            assistant.systemPrompt
        }

        val assemblerContext = me.rerere.rikkahub.data.ai.prompts.PromptContext(
            identitySection = mainIdentity,
            leadInInstructions = buildString {
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
        val mainText = buildString {
            append(system)
            tools.forEach { tool ->
                appendLine()
                append(tool.systemPrompt(model, messages))
            }
        }
        return buildList {
            if (mainText.isNotBlank()) {
                add(UIMessage.system(prompt = mainText))
            }
            // 官方拆分：角色卡字段独立消息（charDescription/charPersonality/scenario，世界书 before/after char 锚点）
            if (useOfficialSplit && !conversationOverride) {
                addAll(assistant.assembleCharacterCardMessages())
            }
        }
    }

    // ── 预构建：tools + systemPrompt（循环不变，移到外面）──
    val toolsInternal = buildList {
        Log.i(TAG, "generateInternal: build tools($assistant)")
        if (assistant?.enableMemory == true) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            buildMemoryTools(
                json = json,
                onCreation = { content ->
                    memoryRepo.addMemory(memoryAssistantId, content)
                },
                onUpdate = { id, content ->
                    memoryRepo.updateContent(id, content)
                },
                onDelete = { id ->
                    memoryRepo.deleteMemory(id)
                },
                stableDate = settings.deepseekCacheOptimization,
            ).let(this::addAll)
        }
        addAll(tools)
    }
    val statusTrackedTools = toolsInternal.map { tool ->
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
    val prebuiltSystemMessages = buildCachedSystemPrompt(assistant, settings, messages, memories ?: emptyList(), conversationSystemPrompt, tools, model, context, conversationRepo)

    for (stepIndex in 0 until maxSteps) {
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
                    conversationModeInjectionIds = conversationModeInjectionIds,
                    conversationLorebookIds = conversationLorebookIds,
                    prebuiltSystemMessages = prebuiltSystemMessages,
                    workspaceCwd = workspaceCwd,
                    conversationId = conversationId,
                    generationType = generationType,
                    maxTokensOverride = maxTokensOverride,
                    swipeMeta = swipeMeta,
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
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }

                // 1. Deduplicate tools: same (toolName, input) only execute once
                val seenTools = mutableSetOf<Pair<String, String>>()
                val uniqueTools = tools.filter { tool ->
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

                // 3. Guardrail: same tool called N+ times in one batch → break
                if (!hasPendingApproval) {
                    val toolNameCount = updatedTools.groupingBy { it.toolName }.eachCount()
                    val looped = toolNameCount.entries.find { it.value >= assistant.toolRecurringLimit }
                    if (looped != null) {
                        Log.w(TAG, "Guardrail: ${looped.key} called ${looped.value} times in one batch, breaking")
                        break
                    }
                }

                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }

                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
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
                // No results to add (all tools were pending)
                break
            }

            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
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
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
        conversationLorebookIds: Set<Uuid> = emptySet(),
        prebuiltSystemMessages: List<UIMessage> = emptyList(),
        workspaceCwd: String? = null,
        conversationId: Uuid? = null,
        generationType: me.rerere.rikkahub.data.model.GenerationType = me.rerere.rikkahub.data.model.GenerationType.NORMAL,
        maxTokensOverride: Int? = null,
        swipeMeta: Map<Uuid, me.rerere.rikkahub.data.model.SwipeMeta> = emptyMap(),
    ) {
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

            // ── 官方 mes_example：作为示例消息注入（story string 之后、聊天历史之前）──
            if (assistant.tavernData != null) {
                addAll(
                    assistant.buildExampleMessages(
                        userName = settings.displaySetting.userNickname.ifBlank { "User" }
                    )
                )
            }

            // ── s10: getUserContext — 用户上下文通过 <system-reminder> UserMessage 注入 ──
            // 对标 Claude Code context.ts → prependUserContext()
            // getUserContext 返回 { claudeMd, currentDate }，此处映射为 memories + currentDate
            val userContext = buildUserContext(memories, assistant, settings)
            if (userContext.isNotBlank()) {
                if (settings.deepseekCacheOptimization) {
                    // DeepSeek 缓存优化：不在此处注入（否则日期/记忆变化会断掉其后全部历史缓存），
                    // 改为 transforms 之后追加到请求末尾
                    deepseekTailInjection = userContext
                } else {
                    add(UIMessage.user(prompt = userContext))
                }
            }

            addAll(limitedChat.withMessageNames())
        }.let { base ->
            val persona = settings.personas.find { it.id == settings.activePersonaId }
            if (persona != null && persona.enabled && persona.description.isNotBlank() &&
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
        var internalMessages = deepseekTailInjection
            ?.takeIf { settings.deepseekCacheOptimization }
            ?.let { transformedMessages + UIMessage.user(prompt = it) }
            ?: transformedMessages
        // [v211] ST 生成前钩子（CHAT_COMPLETION_SETTINGS_READY）：让扩展（ST-PT 的 EJS 模板 /
        //  MVU 的 <UpdateVariable> 剥离）就地改写最终 messages。ST 契约：openai.js:1616-1623
        //   const eventData = { chat, dryRun }; await eventSource.emit(CHAT_COMPLETION_PROMPT_READY, eventData);
        //   return [chat, ...]  ← 改写后的结果继续用。ST-PT handler.ts:980 监听 SETTINGS_READY 并
        //   data.messages = await processGenerateAfter(data.messages)；MVU bundle 5 处监听（含 eventMakeLast）。
        // 仅对酒馆卡助手生效；WebView 未就绪 / 超时 / 无监听器 → null → 原样直通（零开销）。
        if (assistant.tavernData != null) {
            runCatching {
                val payload = me.rerere.rikkahub.data.st.runtime.buildStMessagesJson(internalMessages)
                val rewritten = me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.runGenerationHooks(payload)
                if (rewritten != null) {
                    val parsed = runCatching { me.rerere.rikkahub.data.st.runtime.parseStMessagesJson(rewritten) }.getOrNull()
                    if (parsed != null && parsed.size == internalMessages.size) {
                        internalMessages = parsed
                        android.util.Log.i("RikkaTavern", "[gen-hooks] messages rewritten by extensions (${parsed.size})")
                    } else {
                        android.util.Log.w("RikkaTavern", "[gen-hooks] rewrite rejected (size mismatch or parse failed)")
                    }
                } else {
                    android.util.Log.i("RikkaTavern", "[gen-hooks] no rewrite (null/timeout/not-ready)")
                }
            }.onFailure { android.util.Log.w("RikkaTavern", "[gen-hooks] failed: ${it.message}") }
        }
        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = maxTokensOverride ?: assistant.maxTokens,
            tools = tools,
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            // Streaming: retry once on transient error (429/5xx/timeout)
            val streamChunkHandler = StreamChunkHandler(model)
            try {
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect { chunk ->
                    messages = streamChunkHandler.handle(messages, chunk)
                    // [v234 I1] ST STREAM_TOKEN_RECEIVED（script.js:3901）：载荷 = 累积全文（this.result 语义）。
                    // 逐 token 类事件先量化再放量（红线 6）：developerMode 守卫 + ~150ms 节流。
                    if (settings.developerMode && chunk is me.rerere.ai.ui.StreamChunk.TextDelta) {
                        val now = System.currentTimeMillis()
                        if (now - lastStreamTokenEmitAt >= 150) {
                            lastStreamTokenEmitAt = now
                            val accText = messages.lastOrNull()?.parts
                                ?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text } ?: ""
                            TavernRuntimeManager.fireConversationEvent(
                                conversationId = conversationId?.toString(),
                                name = TavernRuntimeManager.EVENT_STREAM_TOKEN_RECEIVED,
                                payloadJson = org.json.JSONObject.quote(accText),
                                developerMode = settings.developerMode,
                            )
                        }
                    }
                    onUpdateMessages(messages)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429 ") || msg.contains("5") || msg.contains("timeout") || msg.contains("reset")) {
                    Log.w(TAG, "streamText: retrying once after: ${e.message}")
                    providerImpl.streamText(
                        providerSetting = provider,
                        messages = internalMessages,
                        params = params
                    ).collect {
                        messages = streamChunkHandler.handle(messages, it)
                        onUpdateMessages(messages)
                    }
                } else {
                    throw e
                }
            }
        } else {
            val result = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            messages = messages.handleTextGenerationResult(result = result, model = model)

            onUpdateMessages(messages)
        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""
            val streamChunkHandler = StreamChunkHandler(model)

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = streamChunkHandler.handle(messages, chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = result.message.toText()

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
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
