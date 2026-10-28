package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import org.koin.java.KoinJavaComponent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.canResumeToolExecution
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.ConversationMetaGenerator
import me.rerere.rikkahub.data.ai.GenerationLoop
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.UsageRecorder
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.ai.TranslationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createWorkspaceTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createFileTools
import me.rerere.rikkahub.data.ai.tools.createShellTools
import me.rerere.rikkahub.data.ai.tools.createPythonTool
import me.rerere.rikkahub.data.ai.tools.createDatabaseQueryTool
import me.rerere.rikkahub.data.ai.tools.createCalculatorTool
import me.rerere.rikkahub.data.ai.tools.createWebFetchTool
import me.rerere.rikkahub.data.ai.tools.createTaskTools
import me.rerere.rikkahub.data.ai.tools.createConversationTools
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.InvalidMcpServerNamesException
import me.rerere.rikkahub.data.ai.tools.shouldUseExternalWebSearch
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.UpdateVariableOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.StRegexInputTransformer
import me.rerere.rikkahub.data.ai.transformers.EjsInputTransformer
import me.rerere.rikkahub.data.ai.transformers.InstructModeTransformer
import me.rerere.rikkahub.data.ai.transformers.MacrosMacroPass
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.InjectedPromptCleanupTransformer
import me.rerere.rikkahub.data.ai.transformers.InjectedPromptsTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.AuthorsNoteTransformer
import me.rerere.rikkahub.data.ai.transformers.SkillAutoTriggerTransformer
import me.rerere.rikkahub.data.ai.transformers.KnowledgeBaseTransformer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.st.runtime.TavernChatCompletionsApi
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.withAssistantUpdate
import me.rerere.rikkahub.data.model.withoutConversationFields
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getStoredAssistantOf
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.effectiveMaxToolSteps
import me.rerere.rikkahub.data.model.effectiveToolsEnabled
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.getStRegexed
import me.rerere.rikkahub.data.st.regex.mergeActiveRegexScripts
import me.rerere.rikkahub.data.st.regex.mergeRegexScripts
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.rikkahub.data.export.parseActiveStPresetConfig
import me.rerere.rikkahub.data.model.GenerationType
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.model.checkpointLinkAt
import me.rerere.rikkahub.data.model.checkpointLinks
import me.rerere.rikkahub.data.model.checkpointParentLink
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.model.withCheckpointLink
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

internal fun backgroundTextGenerationParams(
    model: Model,
    conversationId: Uuid = Uuid.random(),
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = conversationId.toString(),
)

private val forkTitleSuffixRegex = Regex("""\((\d+)\)$""")

internal fun forkConversationTitle(sourceTitle: String, existingTitles: Set<String>): String {
    // 源标题已带 (N) 后缀时递增序号，避免多次 fork 后叠加成 xxx(1)(1)(1)
    val suffix = forkTitleSuffixRegex.find(sourceTitle)
    val baseTitle = suffix?.let { sourceTitle.removeRange(it.range) } ?: sourceTitle
    val start = suffix?.groupValues?.get(1)?.toIntOrNull()?.plus(1) ?: 1
    return generateSequence(start) { it + 1 }
        .map { "$baseTitle($it)" }
        .first { it !in existingTitles }
}

internal fun createForkConversation(
    source: Conversation,
    messageNodes: List<MessageNode>,
    existingTitles: Set<String> = emptySet(),
): Conversation = Conversation(
    id = Uuid.random(),
    assistantId = source.assistantId,
    title = forkConversationTitle(source.title, existingTitles),
    messageNodes = messageNodes,
    customSystemPrompt = source.customSystemPrompt,
    modeInjectionIds = source.modeInjectionIds,
    lorebookIds = source.lorebookIds,
    workspaceCwd = source.workspaceCwd,
    folderId = source.folderId,
)

private const val TAG = "ChatService"

/** [v240 W3] 表情立绘分类超时（毫秒）：分类失败/超时绝不能影响正常聊天 */
private const val EXPRESSION_CLASSIFY_TIMEOUT_MS = 20_000L

/**
 * [P0 根治] 发送轮看门狗：**连续多久没有任何进展**才算卡死。
 *
 * 为什么是"无进展"而不是"总时长"：
 * 一次正常的发送轮里，工具执行可以合法地跑满 `toolExecTimeout`（默认 **120 秒**），
 * 慢模型的首 token 也可能要几十秒。用总时长做阈值必然误杀。
 * 所以这里只看"最后一次观察到进展"到现在的间隔 —— 进展包括：
 * 流式 token、工具卡片落库、processingStatus 变化、发送阶段推进。
 *
 * 取值依据：4 个 WebView 前置钩子最坏合计 36.2 秒（1.2+10+10+15），
 * 加上落库与组装，一次"卡住但仍会自愈"的窗口最坏约 45 秒。
 * 给 150 秒（> 工具默认 120 秒 + 余量）既能兜住真正的死锁，又不会误杀合法的长工具调用。
 */
private const val SEND_WATCHDOG_STALL_MS = 150_000L

/** 看门狗轮询间隔：越短反应越快，越长越省电。1 秒对用户感知足够。 */
private const val WATCHDOG_POLL_MS = 1_000L

/**
 * [P0] **请求前**硬截止：从发送轮开始到 STAGE_6（真正发出 HTTP）之间允许的最长时间。
 *
 * 为什么必须单独有这一条：
 * [SEND_WATCHDOG_STALL_MS] 判的是「连续多久没有任何进展」，它天然无法区分
 * 「已经发出请求、正在等模型吐字」和「压根没发出去」—— 后者才是用户最痛的
 * 「转圈但上游零请求」。而用户真实体感是：转了几分钟，监控一条请求都没有。
 *
 * 取值依据：STAGE_3 → STAGE_6 之间**全是本地工作**（落库、组装 prompt、WebView 前置钩子），
 * 4 个钩子最坏合计 36.2 秒（1.2+10+10+15），加上落库与组装最坏约 45 秒。
 * 取 60 秒：正常路径远用不到，一旦超时就说明本地真的卡死了。
 */
private const val SEND_PREREQUEST_DEADLINE_MS = 60_000L

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val solution: ChatErrorSolution? = null,
)

enum class ChatErrorSolution {
    CheckFastModelSettings,
}

/** 删除整楼的结果：UI 必须据此给出明确反馈，不能静默失败。 */
sealed interface DeleteMessageResult {
    data object Success : DeleteMessageResult
    data object NotFound : DeleteMessageResult
    data object Generating : DeleteMessageResult
    data class Failed(val error: Throwable) : DeleteMessageResult
}

private val inputTransformers by lazy {
    listOf(
        // [v241] JSR injectPrompts：注入必须发生在世界书扫描之前（should_scan 内容要参与扫描）
        InjectedPromptsTransformer,
        TimeReminderTransformer,
        AuthorsNoteTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        StRegexInputTransformer,
        EjsInputTransformer,
        MacrosMacroPass,
        DocumentAsPromptTransformer,
        OcrTransformer,
        SkillAutoTriggerTransformer,
        // [v241] JSR 注入清理：scan-only 整条丢弃、in_chat 剥离内部前缀。
        // 放在内置链末尾；其后只有 Template/Knowledge/Instruct 包装（不会重新引入注入标记）
        InjectedPromptCleanupTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
        UpdateVariableOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val appEventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val generationLoop: GenerationLoop,
    private val translationHandler: TranslationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val chatToolFactory: ChatToolFactory,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val knowledgeBaseTransformer: KnowledgeBaseTransformer,
    private val folderRepository: FolderRepository,
) {
    // workspace 系统提示注入 (依赖 workspaceRepository, 故在类内构造)
    private val workspaceReminderTransformer = WorkspaceReminderTransformer(workspaceRepository)

    private fun newConversation(conversationId: Uuid): Conversation = Conversation.ofId(
        conversationId,
        assistantId = settingsStore.settingsFlow.value.getCurrentAssistant().id,
    )

    private val conversationStore = object : ConversationStore {
        override suspend fun prepareWrite(): (Conversation) -> Conversation = { conversation -> conversation }

        override suspend fun write(previous: Conversation?, current: Conversation) {
            withContext(Dispatchers.IO) {
                if (previous == null && !conversationRepo.existsConversationById(current.id)) {
                    conversationRepo.insertConversation(current)
                } else {
                    if (previous != null && previous.messageNodes != current.messageNodes) {
                        checkFilesDelete(current, previous)
                    }
                    conversationRepo.updateConversation(current)
                }
            }
        }
    }

    private val sessionManager = ConversationSessionManager(
        scope = appScope,
        store = conversationStore,
        createInitialConversation = ::newConversation,
        onGenerationFinished = ::onSessionGenerationFinished,
    )

    private val database: AppDatabase by lazy {
        KoinJavaComponent.get<AppDatabase>(AppDatabase::class.java)
    }

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    ) {
        if (error is CancellationException) return
        _errors.update {
            it + ChatError(title = title, error = error, conversationId = conversationId, solution = solution)
        }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                _isForeground.value = true
                stopGenerationForeground()
            }
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        wireTavernChatCompletions()
    }

    /**
     * [v229 A1] 接上酒馆运行时的 `/api/backends/chat-completions/{status,generate}` 兼容路由。
     *
     * 背景：JSR 的 `generate()` / `generateRaw()` 全部落在 ST 的这两个端点上（index.ts:46 /
     * responseGenerator.ts:343/:438）。宿主以前对 `/api/` 零命中 -> 404 -> 卡的生成能力全废。
     *
     * 本方法只做「**复用宿主已有 Provider 管线**」的桥接：上游差异由 `Provider.streamText` 归一化成
     * `Flow<StreamChunk>`，wire 格式（OpenAI delta / [DONE]）由 TavernChatCompletionsApi 拼装。
     * 这样卡片就不需要知道宿主用的是哪家 Provider。
     */
    private fun wireTavernChatCompletions() {
        TavernRuntimeManager.textGenerationHook = { bodyJson ->
            val req = TavernChatCompletionsApi.parseGenerateRequest(bodyJson)
                ?: throw IllegalArgumentException("invalid /generate request body")
            val settings = settingsStore.settingsFlow.value
            val resolved = resolveJsrChatModel(req, settings)
                ?: throw IllegalStateException(
                    "no chat model configured for JSR generate (model=${req.modelId})",
                )
            val (model, provider) = resolved
            val handler = providerManager.getProviderByType(provider)
            flow {
                emitAll(
                    handler.streamText(
                        providerSetting = provider,
                        messages = uiMessagesOf(req),
                        params = TextGenerationParams(
                            model = model,
                            temperature = req.temperature?.toFloat(),
                            topP = req.topP?.toFloat(),
                            maxTokens = req.maxTokens,
                            customHeaders = model.customHeaders,
                            customBody = model.customBodies,
                        ),
                    ),
                )
            }
        }
        TavernRuntimeManager.generationModelIdsSupplier = {
            val settings = settingsStore.settingsFlow.value
            val out = LinkedHashSet<String>()
            settings.providers.filter { it.enabled }.forEach { p ->
                p.models.filter { it.type == ModelType.CHAT }.forEach { m ->
                    val name = m.displayName.ifBlank { m.modelId }
                    if (name.isNotBlank()) out.add(name)
                }
            }
            out.toList()
        }
        TavernRuntimeManager.currentModelIdSupplier = {
            val settings = settingsStore.settingsFlow.value
            settings.findModelById(settings.chatModelId)
                ?.let { it.displayName.ifBlank { it.modelId } }
        }
        Log.i(TAG, "wireTavernChatCompletions: A1 hook installed")
    }

    /**
     * [v229 A1] 按 JSR 请求里的模型名找宿主模型：优先 displayName，其次 modelId；
     * 名字对不上时回退「当前会话助手的 chatModelId」->「全局 chatModelId」（与 /status 口径一致）。
     */
    private fun resolveJsrChatModel(
        req: TavernChatCompletionsApi.GenerateRequest,
        settings: Settings,
    ): Pair<Model, me.rerere.ai.provider.ProviderSetting>? {
        val wanted = req.modelId.trim()
        val all = settings.providers.filter { it.enabled }.flatMap { p ->
            p.models.filter { it.type == ModelType.CHAT }.map { m -> m to p }
        }
        val byName = if (wanted.isEmpty()) null else all.firstOrNull { (m, _) ->
            m.displayName.equals(wanted, ignoreCase = true) || m.modelId.equals(wanted, ignoreCase = true)
        }
        val fallbackId = settings.getCurrentAssistant().chatModelId
            ?: settings.chatModelId
        val byFallback = all.firstOrNull { (m, _) -> m.id == fallbackId }
        val chosen = byName ?: byFallback ?: all.firstOrNull()
        return chosen
    }
    /**
     * [v229 A1] OpenAI 线格式消息 -> 宿主 UIMessage（角色经 normalizeRole 收敛，未知角色当 user）。
     */
    private fun uiMessagesOf(req: TavernChatCompletionsApi.GenerateRequest): List<UIMessage> =
        req.messages.map { m ->
            when (TavernChatCompletionsApi.normalizeRole(m.role)) {
                "system" -> UIMessage.system(m.content)
                "assistant" -> UIMessage.assistant(m.content)
                else -> UIMessage.user(m.content)
            }
        }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessionManager.cleanup()
    }

    private fun onSessionGenerationFinished(session: ConversationSession, cause: Throwable?) {
        if (cause != null) session.messageQueue.pause()
        if (session.state.value.currentMessages.any { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) {
            session.messageQueue.failReplyWaiters(context.getString(R.string.chat_page_voice_tool_approval))
        }
        appScope.launch { dispatchNextQueuedMessage(session.id) }
    }

    // 保留 UI/Web 的入口，生命周期和状态查询统一交给 SessionManager。
    fun addConversationReference(conversationId: Uuid) {
        sessionManager.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionManager.release(conversationId)
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionManager.getConversationFlow(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionManager.getGenerationJobStateFlow(conversationId)

    suspend fun getConversation(conversationId: Uuid): Conversation? =
        sessionManager.get(conversationId)?.state?.value
            ?: conversationRepo.getConversationById(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionManager.getProcessingStatusFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionManager.getConversationJobs()

    private fun launchGenerationJob(
        conversationId: Uuid,
        keepAliveInBackground: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        if (!keepAliveInBackground) return appScope.launch(start = CoroutineStart.LAZY) { block() }

        return appScope.launch(start = CoroutineStart.LAZY) {
            val generationId = Uuid.random()
            val foregroundStarted = ChatGenerationForegroundService.acquire(
                context = context,
                generationId = generationId,
                conversationId = conversationId,
            )
            try {
                block()
            } finally {
                if (foregroundStarted) {
                    ChatGenerationForegroundService.release(context, generationId)
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize(
                load = { conversationRepo.getConversationById(conversationId) },
                create = {
                    // 新建对话, 并添加预设消息
                    val currentSettings = settingsStore.settingsFlowRaw.first()
                    val assistant = currentSettings.getCurrentAssistant()
                    Conversation.ofId(
                        id = conversationId,
                        assistantId = assistant.id,
                        newConversation = true
                    ).updateCurrentMessages(assistant.presetMessages)
                },
            )
            settingsStore.updateAssistant(session.state.value.assistantId)
        }
    }

    // ---- 发送消息 ----

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        sessionManager.getMessageQueueFlow(conversationId)

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.remove(messageId)?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? =
        sessionManager.get(conversationId)?.messageQueue?.beginEdit(messageId)

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        sessionManager.get(conversationId)?.messageQueue?.finishEdit(messageId, parts)
            ?.let(::cleanupQueuedAttachments)
        dispatchNextQueuedMessage(conversationId)
    }

    private fun cleanupQueuedAttachments(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = sessionManager.snapshot()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w(TAG, "Failed to clean queued attachments", e)
            }
        }
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        sessionManager.get(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(
        conversationId: Uuid,
        content: List<UIMessagePart>,
        answer: Boolean = true,
        onPersisted: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (content.isEmptyInputMessage()) {
            onPersisted?.invoke(false)
            return false
        }
        val session = sessionManager.getOrCreate(conversationId)
        val persistence = if (onPersisted != null) CompletableDeferred<Boolean>() else null
        if (onPersisted != null && persistence != null) {
            appScope.launch {
                val ok = persistence.await()
                withContext(Dispatchers.Main.immediate) { onPersisted(ok) }
            }
        }
        synchronized(session) {
            // [Bug B 修复] 用户显式点击发送时总是恢复队列（resume），避免上一轮网络/工具异常触发 pause() 后
            // 队列非空导致 messages.isEmpty() 为 false、新消息被静默困在暂停队列中无法发出。
            session.messageQueue.resume()
            session.messageQueue.enqueue(content, answer, persisted = persistence)
            val activeJob = session.getJob()
            TavernRuntimeManager.appendLog(
                "info",
                "[send-diag] ChatService.sendMessage conv=$conversationId queued=${session.messageQueue.state.value.messages.size} activeJob=${activeJob != null} answer=$answer",
            )
            dispatchNextQueuedMessage(conversationId)
        }
        return true
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = sessionManager.getOrCreate(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    private fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = sessionManager.get(conversationId)
        if (session == null) {
            TavernRuntimeManager.appendLog(
                "info",
                "[dispatch-blocked] reason=no-session conv=$conversationId",
            )
            return null
        }
        synchronized(session) {
            val activeJob = session.getJob()
            if (activeJob != null) {
                val startedAt = session.generationJobStartedAtMs
                val jobAgeMs = if (startedAt > 0L) System.currentTimeMillis() - startedAt else -1L
                TavernRuntimeManager.appendLog(
                    "info",
                    "[dispatch-blocked] reason=activeJob isActive=${activeJob.isActive} jobAgeMs=$jobAgeMs " +
                        "queued=${session.messageQueue.state.value.messages.size} " +
                        "paused=${session.messageQueue.state.value.paused} " +
                        "isExecutingSendTurn=${session.isExecutingSendTurn} " +
                        "activeJobs=${session.activeJobCount()} " +
                        "processingStatus=${session.processingStatus.value} " +
                        "lastStage=${TavernRuntimeManager.lastSendStage(conversationId.toString())?.stage?.name} " +
                        "conv=$conversationId",
                )
                return null
            }
            // [Bug B 修复] 仅在当前没有排队用户消息待覆盖、或最后一条消息确实处于待审批暂停且队列已暂停时才阻止自动调度；
            // 当用户主动发送新消息（paused == false）且无活跃生成任务时，必须允许进入 sendQueuedMessage，
            // 由 sendQueuedMessage 首行 finishInterruptedPendingTools(conversationId) 清理残留未决工具，避免死锁。
            if (session.messageQueue.state.value.paused && session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) {
                TavernRuntimeManager.appendLog(
                    "info",
                    "[dispatch-blocked] reason=paused-pending-tool " +
                        "queued=${session.messageQueue.state.value.messages.size} paused=true " +
                        "isExecutingSendTurn=${session.isExecutingSendTurn} " +
                        "activeJobs=${session.activeJobCount()} " +
                        "processingStatus=${session.processingStatus.value} " +
                        "lastStage=${TavernRuntimeManager.lastSendStage(conversationId.toString())?.stage?.name} " +
                        "conv=$conversationId",
                )
                return null
            }
            val next = session.messageQueue.takeNext()
            if (next == null) {
                val queueState = session.messageQueue.state.value
                val reason = if (queueState.paused) "queue-paused" else "queue-empty"
                TavernRuntimeManager.appendLog(
                    "info",
                    "[dispatch-blocked] reason=$reason queued=${queueState.messages.size} " +
                        "paused=${queueState.paused} isExecutingSendTurn=${session.isExecutingSendTurn} " +
                        "activeJobs=${session.activeJobCount()} " +
                        "processingStatus=${session.processingStatus.value} " +
                        "lastStage=${TavernRuntimeManager.lastSendStage(conversationId.toString())?.stage?.name} " +
                        "conv=$conversationId",
                )
                return null
            }
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    /**
     * [P0 根治] 通用生成轮看门狗：**任何**生成入口（发送 / 重试 / /continue / /trigger /
     * /sysgen / /impersonate / swipe / regenerate …）都必须挂一个。
     *
     * 为什么必须通用：真机取证（2026-10-28 17:17）显示用户点「重试」后卡死，
     * 日志停在 STAGE_5 之后 60 秒零输出，且**没有任何看门狗行** —— 因为旧实现只给
     * `sendQueuedMessage` 装了看门狗，其余 8 个入口完全没有保护，只能无限转圈。
     *
     * 必须跑在 [Dispatchers.Default]：AppScope 默认是 Dispatchers.Main（RikkaHubApp.kt:344），
     * 主线程一旦被同步阻塞，跑在 Main 上的看门狗连 delay 都恢复不了，形同不存在。
     */
    private fun startGenerationWatchdog(
        session: ConversationSession,
        job: Job,
        titleRes: Int = R.string.error_title_send_message,
    ): Job {
        val conversationId = session.id
        return appScope.launch(Dispatchers.Default) {

            // [P0 根治] 看门狗必须是**基于活动**的，不能基于"最后一条消息有没有正文"。
            //
            // 为什么：`generateText` 的循环里，一次 step = 一次请求；
            // 工具执行（`toolExecTimeout` 默认 **120 秒**）发生在 `generateInternal` **返回之后**
            // （GenerationLoop.kt:519-543），此时 `lastSendStage` 仍然是上一轮的 STAGE_6。
            // 如果按"有无正文"判定，纯工具轮（助手消息只有 Tool part、没有 Text）
            // 会被误判成「没出字」→ 正常的长工具调用被看门狗干掉。
            // 所以改为：只要**阈值内出现过任何进展**（token 流、工具状态、阶段推进）就重置计时；
            // 只有连续 [SEND_WATCHDOG_STALL_MS] 完全没有进展，才判定真的卡死。
            val stateFlow = session.state
            val statusFlow = session.processingStatus
            val turnStartedAt = System.currentTimeMillis()
            var lastProgressAt = turnStartedAt
            // [健壮性] 只认「本轮」的阶段记录。
            //
            // sendDiagnosticRecords 是跨轮次共用的环形缓冲，同一会话上一轮的 STAGE_6/STAGE_7 会一直留在
            // 里面。正常发送路径上本轮一定会先写入 STAGE_1/STAGE_2，所以旧写法（直接 lastSendStage）
            // 在**实测路径**上并没有出错；但一旦出现「本轮一条阶段记录都没有」的情况（例如未来有人
            // 挪动埋点、或 STAGE_1/2 因异常未执行），旧写法会读到上一轮的 STAGE_6，
            // 让 `reachedRequest` 恒为 true → 下面的硬截止永不触发 → 无上限转圈。
            // 按轮次序号过滤后，判据在任何情况下都只描述本轮。
            val turnStartSeq = TavernRuntimeManager.currentSendDiagnosticSeq()
            var lastStage = TavernRuntimeManager.furthestSendStageSince(conversationId.toString(), turnStartSeq)?.stage

            fun noteProgress() {
                lastProgressAt = System.currentTimeMillis()
            }

            fun lastAssistantMessage(): UIMessage? =
                session.state.value.messageNodes.lastOrNull()?.currentMessage

            /** 当前是否正在执行工具（processingStatus 非空即「有可见活动」）。 */
            fun isToolActive(): Boolean = session.processingStatus.value != null

            // 进展信号 1：会话状态变化（流式 token、工具卡落库、消息增删）
            val stateJob = launch {
                stateFlow.collect { noteProgress() }
            }
            // 进展信号 2：处理状态变化（工具执行时会写明当前工具名）
            val statusJob = launch {
                statusFlow.collect { noteProgress() }
            }
            try {
                while (job.isActive) {
                    delay(WATCHDOG_POLL_MS)
                    // 阶段推进也算进展
                    val stage = TavernRuntimeManager.furthestSendStageSince(conversationId.toString(), turnStartSeq)?.stage
                    if (stage != null && stage != lastStage) {
                        lastStage = stage
                        noteProgress()
                    }
                    // 正在执行工具（可能连续多个、每个最长 toolExecTimeout）：只要还有可见活动，
                    // 就不算静默。工具执行由各自 withTimeout(toolExecTimeout) 兜底，不需要看门狗插手。
                    if (isToolActive()) {
                        noteProgress()
                    }
                    // 已经产出助手正文 → 交付完成，看门狗退出
                    val last = lastAssistantMessage()
                    if (last != null && last.role == MessageRole.ASSISTANT &&
                        last.parts.any { it is UIMessagePart.Text && it.text.isNotBlank() }
                    ) {
                        return@launch
                    }
                    // [P0] 请求前硬截止：还没到 STAGE_6 就超时 → 本地卡死，必须给可见失败。
                    //
                    // 注意**不能**简单写成「没到 STAGE_6 就计时」：工具续跑轮
                    // （GenerationLoop 里 pendingTools 非空的那些 step）**不会**调用 generateInternal，
                    // 因而永远不会写 STAGE_6；而单个工具最长可跑 toolExecTimeout（默认 120 秒）。
                    // 那种轮次是**合法的长时间运行**，不是卡死。
                    //
                    // 因此只有在「本轮从未到达过 STAGE_6，且当前没有可见的工具活动」时才启用硬截止。
                    // 工具活动期间由 isToolActive() 走下面的「无进展」判据（150s）兜底。
                    val reachedRequest = stage != null &&
                        stage.stepNumber >= TavernRuntimeManager.SendDiagnosticStage.STAGE_6_MODEL_REQUEST_SENT.stepNumber
                    val sinceStartMs = System.currentTimeMillis() - turnStartedAt
                    if (!reachedRequest && !isToolActive() && sinceStartMs >= SEND_PREREQUEST_DEADLINE_MS) {
                        TavernRuntimeManager.appendLog(
                            "error",
                            "[send-watchdog] 请求前超时：${sinceStartMs}ms 内未到达 STAGE_6（上游必然收不到）" +
                                " stuckAt=${stage?.name ?: "UNKNOWN"} conv=$conversationId",
                        )
                        TavernRuntimeManager.recordSendDiagnostic(
                            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                            conversationId = conversationId.toString(),
                            detail = "status=PREREQUEST_DEADLINE stuckAt=${stage?.name ?: "UNKNOWN"} elapsedMs=$sinceStartMs",
                        )
                        session.cancelJobs()
                        // 文案只描述**事实**：本地已等待到上限、请求未发出。
                        // 不再引导用户去查「网络/代理/服务商」—— 真机取证显示这类卡死发生在本机
                        // （STAGE_5 之后 60 秒零日志），把用户引向网络方向是误导。
                        addError(
                            IllegalStateException(
                                context.getString(
                                    R.string.error_send_prerequest_timeout,
                                    stage?.label ?: context.getString(R.string.error_send_watchdog_unknown_stage),
                                    sinceStartMs / 1000,
                                ),
                            ),
                            conversationId,
                            title = context.getString(titleRes),
                        )
                        return@launch
                    }

                    val idleMs = System.currentTimeMillis() - lastProgressAt
                    if (idleMs < SEND_WATCHDOG_STALL_MS) continue

                    TavernRuntimeManager.appendLog(
                        "error",
                        "[send-watchdog] 连续 ${idleMs}ms 无任何进展（token/工具/阶段）stuckAt=" +
                            "${stage?.name ?: "UNKNOWN"} conv=$conversationId",
                    )
                    TavernRuntimeManager.recordSendDiagnostic(
                        stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                        conversationId = conversationId.toString(),
                        detail = "status=WATCHDOG_STALL stuckAt=${stage?.name ?: "UNKNOWN"} idleMs=$idleMs",
                    )
                    session.cancelJobs()
                    addError(
                        IllegalStateException(
                            context.getString(
                                R.string.error_send_watchdog_timeout,
                                stage?.label ?: context.getString(R.string.error_send_watchdog_unknown_stage),
                            ),
                        ),
                        conversationId,
                        title = context.getString(titleRes),
                    )
                    return@launch
                }
            } finally {
                stateJob.cancel()
                statusJob.cancel()
            }
                }.also { watchdog ->
            job.invokeOnCompletion { watchdog.cancel() }
        }
    }

    private fun sendQueuedMessage(session: ConversationSession, queued: QueuedMessage): Job {
        val conversationId = session.id
        val content = queued.parts
        val answer = queued.answer
        session.isExecutingSendTurn = answer
        var stageJobTag = 0
        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = answer,
        ) {
            session.isExecutingSendTurn = answer
            var userMessagePersisted = false
            // [send-stage] 诊断：记录当前阶段，异常时能定位死在哪个 step。
            var currentStep = "none"
            var stepStartedAtMs = 0L
            fun beginStep(step: String, detail: String = "") {
                currentStep = step
                stepStartedAtMs = System.currentTimeMillis()
                TavernRuntimeManager.appendLog(
                    "info",
                    "[send-stage] begin step=$step${if (detail.isEmpty()) "" else " $detail"} " +
                        "conv=$conversationId job=$stageJobTag",
                )
            }

            fun endStep(step: String, detail: String = "") {
                val elapsedMs = if (stepStartedAtMs > 0L) System.currentTimeMillis() - stepStartedAtMs else -1L
                TavernRuntimeManager.appendLog(
                    "info",
                    "[send-stage] end step=$step elapsedMs=$elapsedMs" +
                        "${if (detail.isEmpty()) "" else " $detail"} conv=$conversationId",
                )
            }

            try {
                if (!session.isInitialized) {
                    beginStep("initialize")
                    initializeConversation(conversationId)
                    endStep("initialize")
                }
                beginStep("finishInterruptedPendingTools")
                finishInterruptedPendingTools(conversationId)
                endStep("finishInterruptedPendingTools")

                beginStep("settings")
                val currentConversation = session.state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                endStep("settings")
                beginStep("preprocess")
                val processedContent = withContext(Dispatchers.Default) {
                    preprocessUserInputParts(content, assistant, settings, currentConversation.modeInjectionIds)
                }
                endStep("preprocess")

                // 添加消息到列表并先行同步更新内存态（让 UI 与滚动立即感知新消息，不等 SQLite/FTS I/O）
                val userMsg = UIMessage(
                    role = MessageRole.USER,
                    parts = processedContent,
                )
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + userMsg.toMessageNode(),
                )
                beginStep("updateConversation")
                updateConversation(conversationId, newConversation, checkFiles = false)
                endStep("updateConversation")
                session.submittingMessage = null
                // [P0 修复] 落库保持在关键路径上（内存权威、语义简单、不会出现旧快照回写），
                // 但落库本身已经不再是 O(整段会话)：
                //   · ConversationRepository.updateConversation 仍是全删全插（正确性优先）；
                //   · 真正的大头 FTS 重建已改为单事务（见 MessageFtsManager.indexConversation），
                //     实测把 800 行 4KB 文本的 4.45s 压到 50ms 级；
                //   · 并且 FTS 只在「节点/内容真的变化」时才重建。
                // 之所以没有把落库整体丢到后台：saveConversation 会用传入快照覆盖内存，
                // 异步执行时会用旧快照把流式新内容冲掉（本轮已在此处踩过一次）。
                beginStep("saveConversation", detail = "nodes=${newConversation.messageNodes.size}")
                saveConversation(conversationId, newConversation, checkFiles = false)
                endStep("saveConversation", detail = "nodes=${newConversation.messageNodes.size}")
                userMessagePersisted = true
                queued.persisted?.complete(true)
                TavernRuntimeManager.recordSendDiagnostic(
                    stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_3_USER_MSG_PERSISTED,
                    conversationId = conversationId.toString(),
                    detail = "userMsgId=${userMsg.id} nodes=${newConversation.messageNodes.size} answer=$answer",
                )

                // 酒馆事件桥：用户消息已写入 → 通知 JS 运行时（MVU/脚本）
                beginStep("fireEvent.message_sent")
                if (TavernRuntimeManager.activeConversationId == conversationId.toString()) {
                    TavernRuntimeManager.fireEvent(
                        "message_sent",
                        "{\"args\":[${newConversation.currentMessages.lastIndex}]}",
                    )
                }
                endStep("fireEvent.message_sent")

                // 开始补全
                // [P0 修复] isExecutingSendTurn 不能在 handleMessageComplete **之前**清掉。
                // triggerGeneration 的防重入守卫是 `if (session.isExecutingSendTurn && ...) return`，
                // 而 message_sent 事件（上方 fireEvent）走 mainHandler.post 异步派发、用户 QuickReply
                // 也在 onPersisted 回调后异步执行 —— 它们完全可能在本行之后、handleMessageComplete
                // 真正发出请求之前调用 /trigger。旧实现在这里就置 false，导致守卫失效，
                // previousJob?.cancel() 把正在准备上下文的主发送协程直接掐断：
                // 上游收不到请求，UI 只剩「转圈然后什么都没有」。
                // 正确语义：整个发送轮（含 handleMessageComplete）期间都算「正在执行发送」，
                // 由下方 finally 统一复位。
                if (answer) {
                    beginStep("handleMessageComplete")
                    handleMessageComplete(conversationId)
                    endStep("handleMessageComplete")
                }

                queued.reply?.completeWith(runCatching {
                    val messages = session.state.value.currentMessages
                    check(!session.messageQueue.state.value.paused) { context.getString(R.string.chat_page_voice_generation_failed) }
                    check(messages.none { message -> message.parts.any { it is UIMessagePart.Tool && it.isPending } }) {
                        context.getString(R.string.chat_page_voice_tool_approval)
                    }
                    val previousIds = currentConversation.currentMessages.map { it.id }.toSet()
                    messages.filter { it.id !in previousIds && it.role == MessageRole.ASSISTANT }
                        .joinToString("\n") { it.toText() }
                })
                // Voice owns playback, including when its observer has already left the page.
                // The ordinary autoplay collector must not read a late voice reply again.
                if (queued.reply == null) _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                TavernRuntimeManager.appendLog(
                    "error",
                    "[send-stage] abort step=$currentStep error=${e.javaClass.simpleName}:${e.message} conv=$conversationId",
                )
                session.isExecutingSendTurn = false
                if (!userMessagePersisted) queued.persisted?.complete(false)
                queued.reply?.completeExceptionally(e)
                e.printStackTrace()
                if (e is CancellationException) {
                    TavernRuntimeManager.recordSendDiagnostic(
                        stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                        conversationId = conversationId.toString(),
                        detail = "status=CANCELLED_IN_SEND_TURN reason=${e.message}",
                    )
                    throw e
                }
                session.messageQueue.pause()
                TavernRuntimeManager.recordSendDiagnostic(
                    stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                    conversationId = conversationId.toString(),
                    detail = "status=FAILED_IN_SEND_TURN error=${e.javaClass.simpleName}:${e.message}",
                )
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            } finally {
                session.isExecutingSendTurn = false
            }
        }
        stageJobTag = job.hashCode()
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                queued.reply?.completeExceptionally(cause)
                queued.persisted?.complete(false)
            }
            synchronized(session) {
                if (session.submittingMessage?.id == queued.id) session.submittingMessage = null
            }
        }
        session.setJob(job)
        // [P0 根治] 发送轮看门狗（必须在 setJob 之后启动，否则 session.getJob() 还不是这个 job）。
        //
        // 用户能感知的只有「点发送之后一直转圈」。到达 STAGE_6（真正发出 HTTP）之前有太多
        // 会无声挂住的路径：整份会话重写 + FTS 逐行 fsync（长会话数十秒到数分钟）、
        // WebView 钩子漏回调（固定 15s）……；到达 STAGE_6 之后还有「上游连上了但一直不吐
        // token」而 OkHttp readTimeout 是 10 分钟。与其在每一处零散补超时，不如在发送轮这一层兜底。
        //
        // 判据见 [SEND_WATCHDOG_STALL_MS]：连续多久完全没有进展才算卡死（不是总时长，
        // 因为工具执行可以合法跑满 120 秒、慢模型首 token 也可能几十秒）。
        // [P0 修复] 看门狗**必须**跑在 Default 而不是 AppScope 默认的 Dispatchers.Main。
        //
        // AppScope 的调度器是 Dispatchers.Main（RikkaHubApp.kt:344-350），发送 job 本身也在 Main 上跑。
        // 只要发送链路上出现一次同步阻塞（落库、jieba 分词、WebView 同步桥、synchronized 争用），
        // Main 线程就被占住，跑在 Main 上的看门狗连 delay 都无法恢复 → 看门狗形同不存在，
        // 用户看到的就是「无限转圈」。放到 Default 上才能保证：**即使主线程死了，超时也能生效**。
        val watchdog = startGenerationWatchdog(session, job)
        return job
    }

    /**
     * 插入指定角色消息（不触发生成）
     *
     * 用于 /sys（系统消息）和 /sendas（以助手身份发言）等官方斜杠命令
     */
    fun insertMessage(
        conversationId: Uuid,
        role: MessageRole,
        content: List<UIMessagePart>,
        name: String? = null,
        at: Int? = null,
    ) {
        if (content.isEmptyInputMessage()) return

        val session = sessionManager.getOrCreate(conversationId)
        synchronized(session) {
            val currentConversation = session.state.value
            val scope = when (role) {
                MessageRole.USER -> AssistantAffectScope.USER
                MessageRole.ASSISTANT -> AssistantAffectScope.ASSISTANT
                else -> null
            }
            val settings = settingsStore.settingsFlow.value
            val assistant = settings.getAssistantById(currentConversation.assistantId)
                ?: settings.getCurrentAssistant()
            val processedContent = if (scope != null) {
                val activeScripts = mergeActiveRegexScripts(settings, assistant, currentConversation.modeInjectionIds)
                content.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = part.text
                                .replaceRegexes(assistant, scope, visual = false)
                                .getStRegexed(
                                    activeScripts,
                                    if (role == MessageRole.USER) RegexPlacement.USER_INPUT else RegexPlacement.SLASH_COMMAND,
                                    isPrompt = false,
                                )
                        )
                    } else {
                        part
                    }
                }
            } else {
                content
            }
            val node = UIMessage(
                role = role,
                parts = processedContent,
                name = name,
            ).toMessageNode()
            val messageNodes = if (at == null) {
                currentConversation.messageNodes + node
            } else {
                val index = when {
                    at < 0 -> (currentConversation.messageNodes.size + at).coerceIn(0, currentConversation.messageNodes.size)
                    else -> at.coerceIn(0, currentConversation.messageNodes.size)
                }
                currentConversation.messageNodes.toMutableList().apply { add(index, node) }
            }
            val newConversation = currentConversation.copy(messageNodes = messageNodes)
            // [问题2核心修复] 同步更新内存 session.state 且绝不占用 session.setJob(job)：
            // 预设开场白之后的消息面板按钮常执行 `/send <text> | /trigger`，
            // 若 insertMessage 异步挂到 session.setJob(job)，紧随其后的 `/trigger` (triggerGeneration)
            // 会立即执行 previousJob?.cancel() 把尚未落地的 /send 取消掉，导致消息丢失并卡死！
            updateConversation(conversationId, newConversation, checkFiles = false)
            TavernRuntimeManager.recordSendDiagnostic(
                stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_3_USER_MSG_PERSISTED,
                conversationId = conversationId.toString(),
                detail = "insertMessage role=$role nodes=${newConversation.messageNodes.size}",
            )
            appScope.launch(Dispatchers.IO) {
                try {
                    saveConversation(conversationId, newConversation)
                    if (role == MessageRole.USER && TavernRuntimeManager.activeConversationId == conversationId.toString()) {
                        TavernRuntimeManager.fireEvent(
                            "message_sent",
                            "{\"args\":[${newConversation.currentMessages.lastIndex}]}",
                        )
                    }
                    _generationDoneFlow.emit(conversationId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
                }
            }
        }
    }

    /**
     * 触发一次 AI 回复（/trigger，不添加新消息）
     */
    fun triggerGeneration(conversationId: Uuid, generationType: GenerationType = GenerationType.NORMAL) {
        val session = sessionManager.getOrCreate(conversationId)
        if (session.isExecutingSendTurn && generationType == GenerationType.NORMAL) {
            TavernRuntimeManager.appendLog(
                "info",
                "[trigger] triggerGeneration coalesced into active sendQueuedMessage turn conv=$conversationId",
            )
            return
        }
        val previousJob = session.getJob()
        previousJob?.cancel()
        // [v244 BUGFIX D2] /trigger 真机取证点：确认「命令到达宿主 → 生成任务真的被排上」。
        TavernRuntimeManager.appendLog(
            "info",
            "[trigger] triggerGeneration conv=" + conversationId +
                " type=" + generationType +
                " prevJob=" + (previousJob != null) +
                " msgs=" + session.state.value.currentMessages.size,
        )

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)
                handleMessageComplete(conversationId, generationType = generationType)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    /**
     * 生成系统旁白并插入聊天（/sysgen，不触发普通回复）
     *
     * 参考官方 /sysgen：按提示词让模型写一条系统叙述消息，
     * 生成结果以 SYSTEM 角色插入对话历史（AI 下次回复可见）。
     */
    fun generateSystemNarration(conversationId: Uuid, prompt: String, name: String? = null, at: Int? = null, trim: Boolean = false) {
        if (prompt.isBlank()) return

        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val currentConversation = session.state.value
                if (currentConversation.currentMessages.isEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.slash_error_sysgen_no_messages)),
                        conversationId,
                        title = context.getString(R.string.error_title_send_message),
                    )
                    return@launch
                }

                session.processingStatus.value = context.getString(R.string.slash_sysgen_status)

                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getAssistantById(currentConversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                val provider = model?.findProvider(settings.providers)
                if (model == null || provider == null) {
                    addError(
                        IllegalStateException(context.getString(R.string.slash_error_no_model)),
                        conversationId,
                        title = context.getString(R.string.error_title_send_message),
                    )
                    return@launch
                }

                val providerHandler = providerManager.getProviderByType(provider)
                val history = currentConversation.currentMessages.takeLast(12)
                val narratorSystem = UIMessage.system(
                    "You are the system narrator of a roleplay story. " +
                        "Read the chat history, then write a short narration according to the user's instruction. " +
                        "Output ONLY the narration text itself, without quotes, prefixes, or explanations."
                )
                val result = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(narratorSystem) + history + UIMessage.user(prompt),
                    params = backgroundTextGenerationParams(model, conversationId),
                )
                // 官方 /sysgen trim=true：先按最后一个句子边界裁剪（trimToEndSentence），再走 getRegexedString(message, SLASH_COMMAND)
                val rawNarration = result.message.toText()
                val trimmed = if (trim) trimToEndSentence(rawNarration) else rawNarration.trim()
                val narration = trimmed
                    .replaceRegexes(assistant, AssistantAffectScope.ASSISTANT, visual = false)
                    .getStRegexed(mergeActiveRegexScripts(settings, assistant), RegexPlacement.SLASH_COMMAND, isPrompt = true)
                if (narration.isBlank()) {
                    addError(
                        IllegalStateException(context.getString(R.string.slash_error_sysgen_empty)),
                        conversationId,
                        title = context.getString(R.string.error_title_send_message),
                    )
                    return@launch
                }

                // 以 SYSTEM 角色插入对话历史（不触发回复），支持官方 name= 与 at=
                val latest = getConversationFlow(conversationId).value
                val node = UIMessage(
                    role = MessageRole.SYSTEM,
                    parts = listOf(UIMessagePart.Text(narration)),
                    name = name,
                ).toMessageNode()
                val messageNodes = if (at == null) {
                    latest.messageNodes + node
                } else {
                    val index = when {
                        at < 0 -> (latest.messageNodes.size + at).coerceIn(0, latest.messageNodes.size)
                        else -> at.coerceIn(0, latest.messageNodes.size)
                    }
                    latest.messageNodes.toMutableList().apply { add(index, node) }
                }
                saveConversation(
                    conversationId,
                    latest.copy(messageNodes = messageNodes),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            } finally {
                session.processingStatus.value = null
                _generationDoneFlow.emit(conversationId)
            }
        }
        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    /**
     * 官方 utils.js trimToEndSentence：从尾部向前找最后一个标点/emoji 作为句子边界，
     * 标点前是空白则连标点一起裁掉，否则保留标点；找不到边界时整体 trimEnd。
     */
    private fun trimToEndSentence(input: String): String {
        if (input.isEmpty()) return ""
        val punctuation = setOf('.', '!', '?', '*', '"', ')', '}', '`', ']', '$', '。', '！', '？', '”', '）', '】', '’', '」', '_')
            .map { it.code }
        val cps = input.codePoints().toArray()
        var last = -1
        for (i in cps.indices.reversed()) {
            val cp = cps[i]
            val emoji = isEmojiCodePoint(cp)
            if (cp in punctuation || emoji) {
                last = if (!emoji && i > 0 && Character.isWhitespace(cps[i - 1])) i - 1 else i
                break
            }
        }
        if (last == -1) return input.trimEnd()
        return String(cps, 0, last + 1).trimEnd()
    }

    // 近似官方 \p{Emoji_Presentation}|\p{Extended_Pictographic}：覆盖常用 emoji 区段
    private fun isEmojiCodePoint(cp: Int): Boolean {
        return cp == 0xFE0F || cp in 0x1F300..0x1F5FF || cp in 0x1F600..0x1F64F ||
            cp in 0x1F680..0x1F6FF || cp in 0x1F700..0x1F77F || cp in 0x1F900..0x1F9FF ||
            cp in 0x1FA70..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF
    }

    private fun preprocessUserInputParts(
        parts: List<UIMessagePart>,
        assistant: Assistant,
        settings: Settings? = null,
        conversationModeInjectionIds: Set<Uuid> = emptySet(),
    ): List<UIMessagePart> {
        val activeStRegex = if (settings != null) {
            mergeActiveRegexScripts(settings, assistant, conversationModeInjectionIds)
                .filter { !it.promptOnly }
        } else {
            emptyList()
        }
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    val afterLegacy = part.text.replaceRegexes(
                        assistant = assistant,
                        scope = AssistantAffectScope.USER,
                        visual = false
                    )
                    val afterSt = if (activeStRegex.isNotEmpty()) {
                        afterLegacy.getStRegexed(
                            scripts = activeStRegex,
                            placement = RegexPlacement.USER_INPUT,
                            isPrompt = false,
                        )
                    } else {
                        afterLegacy
                    }
                    part.copy(text = afterSt)
                }

                else -> part
            }
        }
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = message.role == MessageRole.USER || regenerateAssistantMsg,
        ) {
            try {
                previousJob?.join()

                suspend fun runRegenerate() {
                val conversation = session.state.value

                // [P0 修复] 必须按 id 查找：UI 传来的 UIMessage 可能是旧快照（流式/翻译/swipe 之后），
                // 用结构相等匹配会返回 null -> indexOf(null) == -1 -> subList(0, 0) 把整个会话清空并落库。
                val targetId = message.id
                val node = conversation.getMessageNodeByMessageId(targetId)
                    ?: conversation.messageNodes.firstOrNull { n -> n.messages.any { it.id == targetId } }
                if (node == null) {
                    TavernRuntimeManager.appendLog(
                        "warn",
                        "[regenerate] target message not found, aborting without touching history messageId=$targetId",
                    )
                    addError(
                        IllegalStateException(context.getString(R.string.error_regenerate_target_missing)),
                        conversationId,
                        title = context.getString(R.string.error_title_regenerate_message),
                    )
                    return
                }

                if (node.role == MessageRole.USER) {
                    // 截止到当前消息：在最新状态上原子截断，找不到索引就放弃（绝不写成空列表）
                    val truncated = session.tryUpdate { current ->
                        val index = current.messageNodes.indexOfFirst { it.id == node.id }
                        if (index < 0) return@tryUpdate null
                        current.copy(messageNodes = current.messageNodes.take(index + 1))
                    }
                    if (truncated == null) {
                        TavernRuntimeManager.appendLog(
                            "warn",
                            "[regenerate] node disappeared before truncation nodeId=${node.id}",
                        )
                        return
                    }
                    session.flush()
                    handleMessageComplete(conversationId, generationType = GenerationType.REGENERATE)
                } else {
                    if (regenerateAssistantMsg) {
                        val nodeIndex = conversation.messageNodes.indexOfFirst { it.id == node.id }
                        if (nodeIndex <= 0) {
                            // 第 0 楼没有可截断的上文，直接整轮重生成（等价于空 messageRange）
                            handleMessageComplete(conversationId, generationType = GenerationType.REGENERATE)
                        } else {
                            handleMessageComplete(
                                conversationId,
                                messageRange = 0..<nodeIndex,
                                generationType = GenerationType.REGENERATE,
                            )
                        }
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }
                }

                runRegenerate()
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) = synchronized(sessionManager.getOrCreate(conversationId)) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()

        val hasOtherPendingTools = session.state.value.messageNodes.any { node ->
            node.currentMessage.parts.any { part ->
                part is UIMessagePart.Tool && part.isPending && part.toolCallId != toolCallId
            }
        }

        val job = launchGenerationJob(
            conversationId = conversationId,
            keepAliveInBackground = !hasOtherPendingTools,
        ) {
            try {
                afterPreviousGeneration(previousJob) {
                    val conversation = session.state.value
                    // [P0 修复] 双击 / 过期审批：job 在外层已经 setJob 挂上（UI 会转圈），
                    // 旧实现直接 return -> 零请求、零提示。必须给出可见错误。
                    if (conversation.currentMessages.none { message ->
                            message.getTools().any { it.toolCallId == toolCallId && it.isPending }
                        }) {
                        TavernRuntimeManager.appendLog(
                            "warn",
                            "[tool-approval] stale or duplicate approval ignored toolCallId=$toolCallId",
                        )
                        addError(
                            IllegalStateException(context.getString(R.string.error_tool_approval_stale)),
                            conversationId,
                            title = context.getString(R.string.error_title_tool_approval),
                        )
                        return@afterPreviousGeneration
                    }
                    val newApprovalState = when {
                        answer != null -> ToolApprovalState.Answered(answer)
                        approved -> ToolApprovalState.Approved
                        else -> ToolApprovalState.Denied(reason)
                    }

                    // Update the tool approval state
                    val updatedNodes = conversation.messageNodes.map { node ->
                        node.copy(
                            messages = node.messages.map { msg ->
                                msg.copy(
                                    parts = msg.parts.map { part ->
                                        when {
                                            part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                                part.copy(approvalState = newApprovalState)
                                            }

                                            else -> part
                                        }
                                    }
                                )
                            }
                        )
                    }
                    val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                    saveConversation(conversationId, updatedConversation)

                    // Check if there are still pending tools
                    val hasPendingTools = updatedNodes.any { node ->
                        node.currentMessage.parts.any { part ->
                            part is UIMessagePart.Tool && part.isPending
                        }
                    }

                    // Only continue generation when all pending tools are handled
                    if (!hasPendingTools) {
                        handleMessageComplete(conversationId)
                    }

                    _generationDoneFlow.emit(conversationId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                session.messageQueue.pause()
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job, cancelPrevious = false)
        startGenerationWatchdog(session, job)
    }

    // ---- 继续上一条回复（官方 /continue） ----

    /**
     * 官方 /continue：续写最后一条助手消息（追加到原消息，不新开一条）
     */
    fun continueGeneration(conversationId: Uuid, extraPrompt: String? = null) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val settings = settingsStore.settingsFlow.first()
                val conversation = getConversationFlow(conversationId).value
                val nodes = conversation.messageNodes
                // 官方（script.js Generate continue 分支）：最后一条不是助手消息时退化为正常回复，
                // 在用户最新消息后生成回复，用户发言不会从上下文丢失
                if (nodes.lastOrNull()?.role != MessageRole.ASSISTANT) {
                    handleMessageComplete(conversationId, generationType = GenerationType.NORMAL)
                    _generationDoneFlow.emit(conversationId)
                    return@launch
                }
                val lastAssistantIndex = nodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                if (lastAssistantIndex < 0) {
                    addError(
                        IllegalStateException(context.getString(R.string.slash_error_continue_no_message)),
                        conversationId,
                        title = context.getString(R.string.error_title_generation),
                    )
                    return@launch
                }
                val assistant = settings.getAssistantById(conversation.assistantId)
                    ?: settings.getCurrentAssistant()
                val targetNode = nodes[lastAssistantIndex]
                val lastText = targetNode.messages.getOrNull(targetNode.selectIndex)?.toText().orEmpty()
                val activePresetCfgForCont = parseActiveStPresetConfig(
                    settings.tavernPresets,
                    enabledPresetGroups(assistant, settings, conversation.modeInjectionIds),
                )
                val userName = settings.personas.firstOrNull { it.id == settings.activePersonaId }?.name
                    ?: settings.displaySetting.userNickname.ifBlank { "User" }
                val continueNudgeSys = activePresetCfgForCont.continueNudgePrompt
                    ?.takeIf { it.isNotBlank() }
                    ?.let { rawNudge ->
                        val expanded = rawNudge
                            .replace("{{lastChatMessage}}", lastText, ignoreCase = true)
                            .replace("{{lastMessage}}", lastText, ignoreCase = true)
                            .replace("{{char}}", assistant.name, ignoreCase = true)
                            .replace("{{user}}", userName, ignoreCase = true)
                        listOf(UIMessage.system(expanded))
                    }.orEmpty()
                val prompt = buildString {
                    append(lastText)
                    extraPrompt?.trim()?.takeIf { it.isNotBlank() }?.let {
                        appendLine()
                        append(it)
                    }
                }
                val continuation = generateForAssistant(
                    assistant = assistant,
                    settings = settings,
                    prompt = prompt,
                    promptRole = MessageRole.ASSISTANT,
                    history = nodes.take(lastAssistantIndex).map { node ->
                        UIMessage(
                            role = node.role,
                            parts = listOf(UIMessagePart.Text(
                                node.messages.getOrNull(node.selectIndex)?.toText().orEmpty()
                            )),
                        )
                    },
                    conversationId = conversationId,
                    generationType = GenerationType.CONTINUE,
                    extraSystemMessages = continueNudgeSys,
                )
                if (continuation.isBlank()) return@launch

                updateConversationState(conversationId) { conv ->
                    val idx = conv.messageNodes.indexOfLast { it.role == MessageRole.ASSISTANT }
                    if (idx < 0) return@updateConversationState conv
                    val node = conv.messageNodes[idx]
                    conv.copy(
                        messageNodes = conv.messageNodes.mapIndexed { i, n ->
                            if (i != idx) n else node.copy(
                                messages = node.messages.mapIndexed { mi, m ->
                                    if (mi == node.selectIndex) {
                                        val activePresetCfg = parseActiveStPresetConfig(
                                            settings.tavernPresets,
                                            enabledPresetGroups(assistant, settings, conversation.modeInjectionIds),
                                        )
                                        val sep = when (activePresetCfg.continuePostfix?.lowercase()) {
                                            "", "none" -> ""
                                            " ", "space" -> " "
                                            "\n", "newline" -> "\n"
                                            "\n\n", "double_newline" -> "\n\n"
                                            else -> "\n\n"
                                        }
                                        m.copy(parts = m.parts + UIMessagePart.Text(sep + continuation))
                                    } else m
                                }
                            )
                        }
                    )
                }
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    // ---- 生成你的发言草稿（官方 /impersonate） ----

    /**
     * 官方 /impersonate：让 AI 站在 {{user}} 的视角生成“你下一条要说的话”，
     * 结果通过 onDraft 交回（本地填入输入框），不保存进聊天、不自动发送。
     * extraInstruction 作为补充系统提示词（官方 quiet_prompt）。
     */
    fun impersonateDraft(conversationId: Uuid, extraInstruction: String?, onDraft: (String) -> Unit) {
        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val settings = settingsStore.settingsFlow.first()
                val conversation = getConversationFlow(conversationId).value
                val assistant = settings.getAssistantById(conversation.assistantId)
                    ?: settings.getCurrentAssistant()
                // 官方 name1：激活人设名优先，其次临时用户名
                val userName = settings.personas.firstOrNull { it.id == settings.activePersonaId }?.name
                    ?: settings.displaySetting.userNickname.ifBlank { "User" }
                val history = conversation.messageNodes.map { node ->
                    UIMessage(
                        role = node.role,
                        parts = listOf(UIMessagePart.Text(
                            node.messages.getOrNull(node.selectIndex)?.toText().orEmpty()
                        )),
                    )
                }
                // 官方 /impersonate：prompt 作为 quiet_prompt（quietToLoud=true）追加到历史最后一行
                // （script.js modifyLastPromptLine，非 instruct 模式 \n${prompt}），无额外系统指令；
                // 提示词末尾加 "name1:" 引导，模型续写即用户发言（non-instruct impersonation line）
                val effectiveHistory = history.toMutableList()
                val instruction = extraInstruction?.trim()?.takeIf { it.isNotBlank() }
                if (instruction != null && effectiveHistory.isNotEmpty()) {
                    val last = effectiveHistory.last()
                    val lastText = last.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    effectiveHistory[effectiveHistory.size - 1] = last.copy(
                        parts = listOf(UIMessagePart.Text("$lastText\n$instruction"))
                    )
                }
                val activePresetCfg = parseActiveStPresetConfig(
                    settings.tavernPresets,
                    enabledPresetGroups(assistant, settings, conversation.modeInjectionIds),
                )
                val impSystemList = activePresetCfg.impersonationPrompt
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf(UIMessage.system(it.replace("{{user}}", userName).replace("{{char}}", assistant.name))) }
                    ?: emptyList()
                val draftHistory = effectiveHistory + impSystemList + UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("$userName:")),
                )
                val draft = generateForAssistant(
                    assistant = assistant,
                    settings = settings,
                    prompt = "",
                    promptRole = MessageRole.USER,
                    history = draftHistory,
                    conversationId = conversationId,
                    generationType = GenerationType.IMPERSONATE,
                    // 官方 onProgressStreaming：结果流式写入输入框（sendTextarea.value = processedText）
                    onChunk = { text, _ ->
                        onDraft(text)
                    },
                )
                if (draft.isBlank()) return@launch
                onDraft(draft.trim())
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    // ---- 安静生成（官方 /gen） ----

    /**
     * 官方 /gen 的 as= 参数：generateCallback 中 `as = args?.as || 'system'`，
     * 只有 char 特殊（quietToLoud=true），其余一律按系统指令处理。
     */
    enum class QuietPromptAs {
        /** as=system / 缺省：prompt 作为系统指令注入 */
        SYSTEM,

        /** as=char：quietToLoud，prompt 以角色发言注入（名字用角色名） */
        CHAR,
    }

    /**
     * 官方 /gen 参数集（generateCallback）：lock/trim/as/length/name + 本地适配。
     */
    data class GenArgs(
        val prompt: String,
        val asRole: QuietPromptAs = QuietPromptAs.SYSTEM,
        val lock: Boolean = false,
        val length: Int = 0,
        val name: String? = null,
        val trim: Boolean = false,
    )

    /**
     * 官方 /gen（includeHistory = true）与 /genraw（includeHistory = false）：静默生成，
     * 返回完整生成文本作为管道值，绝不向会话历史插入新消息，也不覆写用户输入框。
     */
    suspend fun quietGenerate(
        conversationId: String,
        prompt: String,
        includeHistory: Boolean = true,
        systemPrompt: String = "",
        asRole: QuietPromptAs = QuietPromptAs.SYSTEM,
        length: Int = 0,
        name: String? = null,
        trim: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (prompt.isBlank()) return@runCatching ""
            val convUuid = runCatching { Uuid.parse(conversationId) }.getOrNull()
            val settings = settingsStore.settingsFlow.first()
            val conversation = convUuid?.let { getConversationFlow(it).value }
            val assistant = conversation?.let { settings.getAssistantById(it.assistantId) }
                ?: settings.getCurrentAssistant()

            val rawOutput = if (!includeHistory) {
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                    ?: settings.findModelById(settings.fastModelId)
                    ?: error("No chat model selected")
                val provider = model.findProvider(settings.providers)
                    ?: error("Provider not found")
                val providerHandler = providerManager.getProviderByType(provider)
                val rawMessages = buildList {
                    if (systemPrompt.isNotBlank()) add(UIMessage.system(systemPrompt))
                    add(UIMessage.user(prompt))
                }
                val baseParams = backgroundTextGenerationParams(model, convUuid ?: Uuid.random())
                val params = if (length > 0) baseParams.copy(maxTokens = length) else baseParams
                providerHandler.generateText(
                    providerSetting = provider,
                    messages = rawMessages,
                    params = params,
                ).message.toText()
            } else {
                val history = conversation?.messageNodes?.map { node ->
                    UIMessage(
                        role = node.role,
                        parts = listOf(UIMessagePart.Text(
                            node.messages.getOrNull(node.selectIndex)?.toText().orEmpty()
                        )),
                    )
                }.orEmpty()
                val charName = if (asRole == QuietPromptAs.CHAR) {
                    name?.takeIf { it.isNotBlank() }
                        ?: (assistant.tavernData?.name?.takeIf { it.isNotBlank() } ?: assistant.name)
                } else {
                    null
                }
                val effectivePrompt = if (!charName.isNullOrBlank()) {
                    "$charName: $prompt"
                } else {
                    prompt
                }
                val promptRole = when (asRole) {
                    QuietPromptAs.SYSTEM -> MessageRole.SYSTEM
                    QuietPromptAs.CHAR -> MessageRole.ASSISTANT
                }
                val extraSys = if (systemPrompt.isNotBlank()) {
                    listOf(UIMessage.system(systemPrompt))
                } else {
                    emptyList()
                }
                generateForAssistant(
                    assistant = assistant,
                    settings = settings,
                    prompt = effectivePrompt,
                    promptRole = promptRole,
                    history = history,
                    conversationId = convUuid,
                    generationType = GenerationType.QUIET,
                    extraSystemMessages = extraSys,
                    maxTokensOverride = length.takeIf { it > 0 },
                )
            }
            val trimmed = if (trim) trimToEndSentence(rawOutput.trim()) else rawOutput.trim()
            trimmed
                .replaceRegexes(assistant, AssistantAffectScope.ASSISTANT, visual = false)
                .getStRegexed(
                    mergeActiveRegexScripts(settings, assistant, conversation?.modeInjectionIds.orEmpty()),
                    RegexPlacement.SLASH_COMMAND,
                    isPrompt = false,
                )
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * 兼容回调式入口（供旧调用方使用）。
     */
    fun quietGenerate(
        conversationId: Uuid,
        args: GenArgs,
        onDraft: (String) -> Unit,
    ) {
        if (args.prompt.isBlank()) return

        val session = sessionManager.getOrCreate(conversationId)
        val previousJob = session.getJob()
        previousJob?.cancel()

        val job = appScope.launch {
            try {
                runCatching { previousJob?.join() }
                finishInterruptedPendingTools(conversationId)

                val settings = settingsStore.settingsFlow.first()
                val conversation = getConversationFlow(conversationId).value
                val assistant = settings.getAssistantById(conversation.assistantId)
                    ?: settings.getCurrentAssistant()

                // 官方 lock=true：生成期间禁用发送按钮防递归；本地生成期间 UI 已有 loading 保护，
                // 且 /gen 不写入聊天，lock 无需特殊处理，保留参数兼容。
                val history = conversation.messageNodes.map { node ->
                    UIMessage(
                        role = node.role,
                        parts = listOf(UIMessagePart.Text(
                            node.messages.getOrNull(node.selectIndex)?.toText().orEmpty()
                        )),
                    )
                }
                // 官方 as=char（quietToLoud）：prompt 以角色发言注入，instruct 模式名字用 name2；
                // name= 优先，其次角色卡名（本地单角色，官方 name= 是多角色选卡）
                val charName = if (args.asRole == QuietPromptAs.CHAR) {
                    args.name?.takeIf { it.isNotBlank() }
                        ?: (assistant.tavernData?.name?.takeIf { it.isNotBlank() } ?: assistant.name)
                } else {
                    null
                }
                val effectivePrompt = if (!charName.isNullOrBlank()) {
                    "$charName: ${args.prompt}"
                } else {
                    args.prompt
                }
                val promptRole = when (args.asRole) {
                    QuietPromptAs.SYSTEM -> MessageRole.SYSTEM
                    QuietPromptAs.CHAR -> MessageRole.ASSISTANT
                }
                val result = generateForAssistant(
                    assistant = assistant,
                    settings = settings,
                    prompt = effectivePrompt,
                    promptRole = promptRole,
                    history = history,
                    conversationId = conversationId,
                    generationType = GenerationType.QUIET,
                    // 官方 length=：TempResponseLength 临时设置模型响应长度（max_tokens），非拼 prompt
                    maxTokensOverride = args.length.takeIf { it > 0 },
                )
                // 官方 trim=true：trimToEndSentence 按最后一个句子边界裁剪
                val finalText = if (args.trim) trimToEndSentence(result.trim()) else result.trim()
                if (finalText.isBlank()) return@launch
                onDraft(finalText)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
        startGenerationWatchdog(session, job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null,
        generationType: GenerationType = GenerationType.NORMAL,
    ) {
        val settings = settingsStore.settingsFlow.first()
        val initialConversation = getConversationFlow(conversationId).value
        val assistant = settings.getAssistantById(initialConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val model = assistant.chatModelId?.let { settings.findModelById(it) }
            ?: settings.findModelById(settings.chatModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No chat model selected")
        TavernRuntimeManager.recordSendDiagnostic(
            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_5_GENERATION_STARTED,
            conversationId = conversationId.toString(),
            detail = "model=${model.modelId} assistant=${assistant.id} type=$generationType msgs=${initialConversation.currentMessages.size}",
        )

        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }
        val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)

        runCatching {

            // reset suggestions
            updateConversation(conversationId, initialConversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (useExternalWebSearch || mcpManager.getAllAvailableTools(assistant).isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)
            val conversation = getConversationFlow(conversationId).value

            val tools = if (!assistant.effectiveToolsEnabled()) {
                TavernRuntimeManager.appendLog(
                    "info",
                    "[tools] disabled by policy for assistant=${assistant.id} tavern=${assistant.tavernData != null}",
                )
                emptyList<Tool>()
            } else try {
                val officialTools = chatToolFactory.createTools(
                    settings = settings,
                    assistant = assistant,
                    model = model,
                    workspaceCwd = conversation.workspaceCwd,
                    conversationModeInjectionIds = conversation.modeInjectionIds,
                )
                // rikkaST 自研工具（上游 ChatToolFactory 只含官方工具集），补在 official 之后并去重
                val localExtraTools = buildList {
                    if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                        addAll(createFileTools())
                    }
                    if (assistant.localTools.contains(LocalToolOption.ShellTools)) {
                        addAll(createShellTools())
                    }
                    if (assistant.localTools.contains(LocalToolOption.PythonEngine)) {
                        add(createPythonTool(context, assistant.toolExecTimeout))
                    }
                    if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) {
                        add(createDatabaseQueryTool(database))
                    }
                    if (assistant.localTools.contains(LocalToolOption.Calculator)) {
                        add(createCalculatorTool(context))
                    }
                    add(createWebFetchTool())
                    if (assistant.localTools.contains(LocalToolOption.TaskTools)) {
                        addAll(createTaskTools())
                    }
                }
                me.rerere.rikkahub.data.ai.tools.deduplicateTools(officialTools + localExtraTools)
            } catch (error: InvalidMcpServerNamesException) {
                sessionManager.get(conversationId)?.messageQueue?.pause()
                addError(
                    error = IllegalStateException(
                        context.getString(
                            R.string.error_mcp_invalid_server_name,
                            error.names.joinToString(", "),
                        )
                    ),
                    conversationId = conversationId,
                )
                return
            }

            // start generating
            val session = sessionManager.getOrCreate(conversationId)
            val myGenerationEpoch = session.currentGenerationEpoch()

            // 如果不在前台，提前启动前台 Service：异步启动 + 失败兜底，绝不让 Service 启动阻塞/中断生成
            if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                appScope.launch {
                    runCatching { startGenerationForeground(senderName, conversationId.toString()) }
                }
            }

            // 监听前后台切换：切后台 600ms 后才启动 FG Service 保活（防短暂切走造成反复启停），
            // 启动失败不影响生成（Android 12+ 后台启动限制等）
            val fgJob: Job? = if (settings.displaySetting.enableNotificationOnMessageGeneration) {
                appScope.launch {
                    isForeground.drop(1).debounce(600).collect { foreground ->
                        if (!foreground) {
                            runCatching { startGenerationForeground(senderName, conversationId.toString()) }
                        } else {
                            stopGenerationForeground()
                        }
                    }
                }
            } else null

            generationLoop.generateText(
                settings = settings,
                model = model,
                generationType = generationType,
                processingStatus = session.processingStatus,
                messages = conversation.currentMessages.let { list ->
                    if (messageRange != null) {
                        // [v243 BUGFIX B1] 越界防护：messageRange 是 messageNodes 的下标区间，
                        // 而这里切的是 currentMessages；两者长度在 swipe / fork / 分支编辑后可能不一致，
                        // 旧写法 subList(start, endInclusive + 1) 会抛 IndexOutOfBoundsException。
                        // nodeIndex == 0（重新生成助手预设开场白）时区间是空的，切片结果就是空列表 ——
                        // 由 StreamChunkHandler.handle() 的空列表分支兜底成「一条新助手消息」。
                        val from = messageRange.start.coerceIn(0, list.size)
                        val to = (messageRange.endInclusive + 1).coerceIn(from, list.size)
                        list.subList(from, to).toList()
                    } else {
                        list
                    }
                },
                assistant = assistant,
                maxSteps = assistant.effectiveMaxToolSteps(),
                conversationSystemPrompt = conversation.customSystemPrompt,
                conversationModeInjectionIds = conversation.modeInjectionIds,
                conversationLorebookIds = conversation.lorebookIds,
                workspaceCwd = conversation.workspaceCwd,
                conversationId = conversation.id,
                swipeMeta = conversation.swipeMetaByMessageId,
                memories = if (assistant.useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                    add(knowledgeBaseTransformer)
                    add(workspaceReminderTransformer)
                    // Instruct 序列（高级格式化）：最后应用，作为发送格式的最外层包裹
                    add(InstructModeTransformer)
                },
                outputTransformers = outputTransformers,
                tools = tools,
            ).onCompletion {
                // 取消前台服务；通知由 ChatNotificationManager 通过 AppEventBus 消费
                fgJob?.cancel()
                if (session.currentGenerationEpoch() == myGenerationEpoch) {
                    stopGenerationForeground()

                    // 可能被取消了，或者意外结束，兜底更新（仅在未被新世代接管时执行，防止取消后立即重发被旧完成回调打断）
                    val updatedConversation = session.finishGeneration { conversation ->
                        saveConversation(conversationId, conversation)
                    }

                    // 酒馆事件桥：AI 回复已完成 → 通知 JS 运行时（MVU/脚本解析 <UpdateVariable>）
                    if (TavernRuntimeManager.activeConversationId == conversationId.toString()) {
                        val tavernLast = updatedConversation.currentMessages.lastOrNull()
                        if (tavernLast != null) {
                            val evt = if (tavernLast.role == MessageRole.USER) "message_sent" else "message_received"
                            TavernRuntimeManager.fireEvent(
                                evt,
                                "{\"args\":[${updatedConversation.currentMessages.lastIndex}]}",
                            )
                        }

                        // [R7] ST 顺序 CHARACTER_MESSAGE_RENDERED → GENERATION_ENDED；排在 message_received 之后
                        fireTavernEvent(
                            conversationId,
                            TavernRuntimeManager.EVENT_GENERATION_ENDED,
                            "{\"args\":[${updatedConversation.currentMessages.size}]}",
                        )
                    }

                    // 生成结束：取消 Live Update 通知，后台时发送完成通知
                    appEventBus.emit(
                        AppEvent.ChatGenerationEnded(
                            conversationId = conversationId,
                            senderName = senderName,
                            contentPreview = updatedConversation.currentMessages.lastOrNull()
                                ?.toText()?.take(50)?.trim() ?: "",
                        )
                    )
                }
            }.collect { chunk ->
                currentCoroutineContext().ensureActive()
                if (session.currentGenerationEpoch() != myGenerationEpoch) return@collect
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        // [Bug C 修复] 流式逐 token 更新仅追加/刷新当前消息内容，不会删除历史附件，
                        // 跳过 checkFilesDelete 全会话节点扫描，消除长对话流式输出时的主线程 GC 与掉帧。
                        updateConversation(conversationId, updatedConversation, checkFiles = false)

                        // 前台时停止前台 Service（用户切回来了）
                        if (isForeground.value) {
                            stopGenerationForeground()
                        }

                        // 通知等边缘副作用由 ChatNotificationManager 消费；
                        // tryEmit 不挂起，事件丢失只影响单次通知更新，不能反压生成链
                        chunk.messages.lastOrNull()?.let { lastMessage ->
                            appEventBus.tryEmit(
                                AppEvent.ChatGenerationUpdate(conversationId, lastMessage, senderName)
                            )
                        }
                    }
                }
            }
        }.onFailure {
            // 兜底取消 Live Update 通知（生成开始前失败时 onCompletion 不会执行）
            appEventBus.tryEmit(AppEvent.ChatGenerationEnded(conversationId, senderName, null))
            if (it is CancellationException) {
                TavernRuntimeManager.recordSendDiagnostic(
                    stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                    conversationId = conversationId.toString(),
                    detail = "status=CANCELLED reason=${it.message}",
                )
                throw it
            }
            sessionManager.get(conversationId)?.messageQueue?.pause()
            TavernRuntimeManager.recordSendDiagnostic(
                stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                conversationId = conversationId.toString(),
                detail = "status=FAILED error=${it.javaClass.simpleName}:${it.message}",
            )

            // 取消前台服务
            stopGenerationForeground()

            it.printStackTrace()
            // [P0 修复] 中止类错误单独给文案：让用户知道「请求根本没发出去」，而不是一个泛化的失败。
            if (it is me.rerere.rikkahub.data.ai.GenerationAbortedException) {
                addError(
                    IllegalStateException(
                        context.getString(R.string.error_generation_aborted, it.reason),
                        it,
                    ),
                    conversationId,
                    title = context.getString(R.string.error_title_generation),
                )
            } else {
                addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            }
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            TavernRuntimeManager.recordSendDiagnostic(
                stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                conversationId = conversationId.toString(),
                detail = "status=SUCCESS nodes=${finalConversation.messageNodes.size}",
            )

            sessionManager.launchWithSession(conversationId) {
                generateTitle(conversationId, finalConversation)
            }
            sessionManager.launchWithSession(conversationId) {
                generateSuggestion(conversationId, finalConversation)
            }
        }
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { _, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Keep messages that are ready to resume, such as approved/denied/answered tools.
                val hasResumableTool = node.currentMessage.getTools().any {
                    !it.isExecuted && it.approvalState.canResumeToolExecution()
                }
                if (hasResumableTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove messages that still have unresolved tool approvals.
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    private fun cancelToolByUser(tool: UIMessagePart.Tool): UIMessagePart.Tool {
        return tool.copy(
            output = listOf(
                UIMessagePart.Text(
                    """{"status":"cancelled","error":"Generation cancelled by user before tool execution completed."}"""
                )
            )
        )
    }

    private suspend fun finishInterruptedPendingTools(conversationId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val lastNode = currentConversation.messageNodes.lastOrNull() ?: return
        val lastMessage = lastNode.currentMessage
        val updatedMessage = lastMessage.finishPendingTools(::cancelToolByUser)
        if (updatedMessage == lastMessage) {
            return
        }

        val updatedConversation = currentConversation.copy(
            messageNodes = currentConversation.messageNodes.dropLast(1) + lastNode.copy(
                messages = lastNode.messages.map { message ->
                    if (message.id == lastMessage.id) updatedMessage else message
                }
            )
        )
        // [P0 修复] 这里也在发送关键路径上（sendQueuedMessage 首行）。
        // 内存态必须先同步更新（后续组装 prompt 要看到「工具已收尾」），
        // 落库改成后台写，避免同样的「整份会话重写 + FTS 全量重建」拖住发送。
        updateConversation(conversationId, updatedConversation, checkFiles = false)
        appScope.launch {
            runCatching { persistLatestSnapshot(conversationId) }
                .onFailure {
                    TavernRuntimeManager.appendLog(
                        "warn",
                        "[send] 收尾未决工具的落库失败（内存态已更新）conv=$conversationId: ${it.message}",
                    )
                }
        }
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return@withContext

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.fastModelId)
                ?: throw IllegalStateException(context.getString(R.string.error_fast_model_not_found))
            val provider = model.findProvider(settings.providers)
                ?: throw IllegalStateException(context.getString(R.string.error_fast_model_provider_not_found))

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText() })
                    ),
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.message.toText().trim())
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(
                error = it,
                conversationId = conversationId,
                title = context.getString(R.string.error_title_generate_title),
                solution = ChatErrorSolution.CheckFastModelSettings,
            )
        }
    }

    // ---- [v240 W3] 表情立绘分类 ----

    /**
     * Character Expressions 分类调用（原生重写，只对齐官方语义，不移植官方 index.js）。
     *
     * 官方 `getExpressionLabel`（extensions/expressions/index.js:1074-1095）的 llm 分支：
     * 用当前聊天模型 + 官方提示词对最后一条消息做一次分类；rikkaST 天然是 LLM 客户端，
     * 所以不需要任何服务端接口。
     *
     * 与聊天主链路完全解耦：
     * - 独立一次性调用：不写会话、不占生成任务、不触发 UI loading；
     * - [withTimeoutOrNull] 包裹，任何异常一律吞掉返回 null（分类失败绝不能影响正常聊天）。
     *
     * @param assistant 目标助手（用它的 chatModelId，缺省回退全局 chatModelId → fastModelId）
     * @param text 最后一条助手消息文本
     * @param labels 该角色实际拥有的立绘标签（官方 filterAvailable 语义：只让模型从可用标签里选）
     * @return 命中的标签；失败/超时/解析不出标签时返回 null（调用方回退兜底标签）
     */
    suspend fun classifyExpression(
        assistant: Assistant,
        text: String,
        labels: List<String>,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || labels.isEmpty()) return@withContext null
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(assistant.chatModelId, fallback = settings.fastModelId)
                ?: return@runCatching null
            val provider = model.findProvider(settings.providers) ?: return@runCatching null
            val providerHandler = providerManager.getProviderByType(provider)
            val result = withTimeoutOrNull(EXPRESSION_CLASSIFY_TIMEOUT_MS) {
                providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.system(ExpressionLabels.buildPrompt(labels)),
                        UIMessage.user(ExpressionLabels.sampleText(text)),
                    ),
                    // 分类要稳定：temperature 0（官方 llm 模式也走一次短生成，解析失败回退）
                    params = backgroundTextGenerationParams(model).copy(temperature = 0f),
                )
            } ?: return@runCatching null
            val label = ExpressionLabels.parseResponse(result.message.toText(), labels)
            // [v240 W3] 真机验证观测点：分类是否返回标签（进 tavern-runtime.log）
            TavernRuntimeManager.appendLog(
                "info",
                "[expression] classify labels=${labels.size} -> ${label ?: "null"}",
            )
            label
        }.onFailure {
            TavernRuntimeManager.appendLog(
                "warn",
                "[expression] classify error: ${it.message ?: it.javaClass.simpleName}",
            )
        }.getOrNull()
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(
        conversationId: Uuid,
        conversation: Conversation,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            if (!settings.enableSuggestion) return@runCatching
            val model = settings.findModelById(settings.fastModelId)
                ?: return@runCatching
            val provider = model.findProvider(settings.providers) ?: return@runCatching

            sessionManager.get(conversationId)?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = backgroundTextGenerationParams(model, conversationId, settings.fastModelReasoningLevel),
            )
            val suggestions =
                result.message.toText().split("\n").map { it.trim() }
                    .filter { it.isNotBlank() }

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessionManager.get(conversationId)?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    /**
     * 为指定 Assistant 生成回复（群聊用），支持流式回调
     */
    suspend fun generateForAssistant(
        assistant: Assistant,
        settings: Settings,
        prompt: String,
        history: List<UIMessage>,
        conversationId: Uuid? = null,
        generationType: GenerationType = GenerationType.NORMAL,
        promptRole: MessageRole = MessageRole.USER,
        extraSystemMessages: List<UIMessage> = emptyList(),
        maxTokensOverride: Int? = null,
        onChunk: ((String, List<UIMessagePart>?) -> Unit)? = null,
    ): String {
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
            ?: error("No model configured for assistant '${assistant.name}'")

        val messages = history + extraSystemMessages + if (prompt.isBlank()) {
            emptyList()
        } else {
            listOf(UIMessage(
                role = promptRole,
                parts = listOf(UIMessagePart.Text(prompt)),
            ))
        }
        var result = ""

        // MCP 工具：对齐上游校验服务器名（仅字母数字），非法名直接报错返回
        val mcpTools = mcpManager.getAllAvailableTools(assistant)
        val invalidMcpNames = mcpTools
            .map { it.second }
            .distinct()
            .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
        if (invalidMcpNames.isNotEmpty()) {
            addError(
                error = IllegalStateException(
                    context.getString(
                        R.string.error_mcp_invalid_server_name,
                        invalidMcpNames.joinToString(", ")
                    )
                ),
                conversationId = conversationId,
            )
            return ""
        }

        generationLoop.generateText(
            settings = settings,
            model = model,
            messages = messages,
            assistant = assistant,
            generationType = generationType,
            conversationId = conversationId,
            memories = if (assistant.useGlobalMemory) {
                memoryRepository.getGlobalMemories()
            } else {
                memoryRepository.getMemoriesOfAssistant(assistant.id.toString())
            },
            tools = if (!assistant.effectiveToolsEnabled()) emptyList<Tool>() else buildList {
                if (assistant.localTools.contains(LocalToolOption.FileTools)) {
                    addAll(createFileTools())
                }
                if (assistant.enableWebSearch) {
                    addAll(createSearchTools(settings))
                }
                addAll(localTools.getTools(assistant.localTools))
                if (assistant.localTools.contains(LocalToolOption.ShellTools)) {
                    addAll(createShellTools())
                }
                if (assistant.localTools.contains(LocalToolOption.DatabaseQuery)) {
                    add(createDatabaseQueryTool(database))
                }
                if (assistant.localTools.contains(LocalToolOption.Calculator)) {
                    add(createCalculatorTool(context))
                }
                add(createWebFetchTool())
                if (assistant.localTools.contains(LocalToolOption.TaskTools)) {
                    addAll(createTaskTools())
                }
                if (assistant.enabledSkills.isNotEmpty()) {
                    addAll(
                        createSkillTools(
                            enabledSkills = assistant.enabledSkills,
                            allSkills = skillManager.listSkills(),
                        )
                    )
                }
                mcpTools.forEach { (serverId, serverName, tool) ->
                    add(
                        Tool(
                            name = "mcp__${serverName}__${tool.name}",
                            description = tool.description ?: "",
                            parameters = { tool.inputSchema },
                            needsApproval = { tool.needsApproval },
                            execute = {
                                mcpManager.callTool(serverId, tool.name, it.jsonObject)
                            },
                        )
                    )
                }
            },
            inputTransformers = buildList {
                addAll(inputTransformers)
                add(templateTransformer)
                add(knowledgeBaseTransformer)
                // Instruct 序列（高级格式化）：最后应用，作为发送格式的最外层包裹
                add(InstructModeTransformer)
            },
            outputTransformers = outputTransformers,
            // 官方 /gen length=：临时覆盖响应长度（TempResponseLength 语义），用完即弃
            maxTokensOverride = maxTokensOverride,
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> {
                    val lastMsg = chunk.messages.lastOrNull()
                    val text = lastMsg?.toText() ?: ""
                    result = text
                    onChunk?.invoke(text, lastMsg?.parts)
                }
            }
        }

        return result
    }

    // ---- 压缩对话历史 ----

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText() }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = backgroundTextGenerationParams(model, conversationId),
            )

            return result.message.toText().trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // 通知已迁移至 ChatNotificationManager（通过 AppEventBus 通信）

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String? = null): List<Tool> {
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

    // 通知已迁移至 ChatNotificationManager（通过 AppEventBus 通信）

    // region Foreground Service — 后台生成时保持进程存活

    private fun startGenerationForeground(title: String, conversationId: String) {
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_START
            putExtra(GenerationForegroundService.EXTRA_TITLE, title)
            putExtra(GenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateGenerationForeground(text: String) {
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_UPDATE
            putExtra(GenerationForegroundService.EXTRA_TEXT, text.take(200))
        }
        context.startService(intent)
    }

    private fun stopGenerationForeground() {
        val intent = Intent(context, GenerationForegroundService::class.java).apply {
            action = GenerationForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    // endregion

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(
        conversationId: Uuid,
        conversation: Conversation,
        checkFiles: Boolean = true,
    ) {
        if (conversation.id != conversationId) return
        val session = sessionManager.getOrCreate(conversationId)
        if (checkFiles) {
            checkFilesDelete(conversation, session.state.value)
        }
        session.updateConversation(conversation)
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private suspend fun updateConversationMetadata(
        conversationId: Uuid,
        update: (Conversation) -> Conversation,
        persist: suspend (Conversation) -> Unit,
    ) {
        sessionManager.withSession(conversationId) { session ->
            session.initialize(
                load = {
                    conversationRepo.getConversationById(conversationId)
                        ?: throw NotFoundException("Conversation not found")
                },
            )
            session.updateMetadata(update, persist)
        }
    }

    suspend fun toggleConversationPinned(conversationId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            update = { it.copy(isPinned = !it.isPinned) },
            persist = { conversationRepo.updatePinStatus(conversationId, it.isPinned) },
        )
    }

    suspend fun moveConversationToAssistant(conversationId: Uuid, assistantId: Uuid) {
        updateConversationMetadata(
            conversationId = conversationId,
            // 文件夹属于助手，移动后清除原助手的文件夹归属。
            update = { it.copy(assistantId = assistantId, folderId = null) },
            persist = { conversationRepo.updateConversationAssistant(conversationId, it.assistantId) },
        )
    }

    /**
     * 移动会话到文件夹（folderId 为 null 表示移出到未归类）。
     *
     * 若该会话当前有活跃 session（正在查看或后台生成），先同步内存态再落库：
     * 否则仅改数据库 folder_id，而内存里那份 Conversation 仍是旧 folderId，
     * 后续任意 saveConversation(id, state.value) 会用整对象把 folder_id 覆盖回旧值，导致移动丢失。
     * 先改内存可确保这段窗口内的整对象保存也带上新 folderId。
     */
    suspend fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        if (sessionManager.get(conversationId) != null) {
            updateConversationState(conversationId) { it.copy(folderId = folderId) }
        }
        conversationRepo.updateConversationFolderId(conversationId, folderId)
    }

    /**
     * 文件夹内是否存在正在生成回复的会话（对齐上游行为）。
     * 仅活跃 session 可能在生成；内存态 folderId 为权威。
     */
    fun hasGeneratingConversationInFolder(folderId: Uuid): Boolean {
        return sessionManager.snapshot().any { it.isGenerating && it.state.value.folderId == folderId }
    }

    /**
     * 删除文件夹前，先把内存中归属该文件夹的活跃 session folderId 置空，
     * 避免后续整对象保存写回一个已被删除的 folder_id（对齐上游 deleteFolder 语义）。
     */
    fun clearFolderFromSessions(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
    }

    suspend fun deleteFolder(folderId: Uuid) {
        sessionManager.snapshot()
            .filter { it.state.value.folderId == folderId }
            .forEach { updateConversationState(it.id) { c -> c.copy(folderId = null) } }
        folderRepository.deleteFolder(folderId)
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val session = sessionManager.get(newConversation.id)
        val queuedFiles = (session?.messageQueue?.state?.value?.messages.orEmpty() +
                listOfNotNull(session?.submittingMessage))
            .flatMap { it.parts }.localFileUrls().map { it.toUri() }
        val newFiles = newConversation.files + queuedFiles
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation, checkFiles: Boolean = true) {
        val updatedConversation = conversation.copy()
        // 先同步刷新内存状态，确保 UI 列表和后续逻辑无需等待磁盘 I/O 即可读取最新消息
        if (updatedConversation.title.isNotBlank() || updatedConversation.messageNodes.isNotEmpty()) {
            updateConversation(conversationId, updatedConversation, checkFiles = checkFiles)
        }

        withContext(Dispatchers.IO) {
            val exists = conversationRepo.existsConversationById(updatedConversation.id)
            if (!exists && updatedConversation.title.isBlank() && updatedConversation.messageNodes.isEmpty()) {
                return@withContext // 新会话且为空时不保存
            }
            if (!exists) {
                conversationRepo.insertConversation(updatedConversation)
            } else {
                conversationRepo.updateConversation(updatedConversation)
            }
        }
        // 删除消息或切换分支也可能解除工具审批阻塞，保存成功后重新检查队列。
        // 调度器仍会检查当前生成任务、待审批工具、暂停状态及编辑占位。
        dispatchNextQueuedMessage(conversationId)
    }

    /**
     * [P0 新增] 后台落库：始终写**执行时刻的最新内存状态**，绝不写调用时的旧快照。
     *
     * 为什么需要它：`saveConversation` 会先用传入的 conversation 覆盖内存，然后落库。
     * 如果把它整个丢到后台，等到真正执行时内存里可能已经流入了新的 token，
     * 用旧快照回写会把新内容冲掉（本轮已在这里踩过一次）。
     * 所以后台落库必须「取最新状态再写」，这也是唯一安全的异步写法。
     *
     * 调用方必须先同步 `updateConversation(...)` 把变化写进内存。
     */
    private suspend fun persistLatestSnapshot(conversationId: Uuid) {
        val session = sessionManager.get(conversationId) ?: return
        withContext(Dispatchers.IO) {
            // 取最新内存快照（含调用方刚刚写入的变化），而不是调用方传入的那份旧对象
            val latest = session.state.value
            val exists = conversationRepo.existsConversationById(latest.id)
            if (!exists && latest.title.isBlank() && latest.messageNodes.isEmpty()) return@withContext
            if (!exists) {
                conversationRepo.insertConversation(latest)
            } else {
                conversationRepo.updateConversation(latest)
            }
        }
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                translationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return

        val currentConversation = getConversationFlow(conversationId).value
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getAssistantById(currentConversation.assistantId)
            ?: settings.getCurrentAssistant()
        val processedParts = preprocessUserInputParts(parts, assistant)
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))

        // [R4] ST MESSAGE_EDITED / MESSAGE_UPDATED：index 必须是 number（node 与 currentMessages 一一对应）
        val editedIndex = currentConversation.currentMessages
            .indexOfFirst { it.id == messageId }
            .takeIf { it >= 0 }
            ?: currentConversation.messageNodes.indexOfFirst { node -> node.messages.any { it.id == messageId } }
        if (editedIndex >= 0) {
            val payload = "{\"args\":[$editedIndex]}"
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_EDITED, payload)
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_UPDATED, payload)
        }
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = copyNodesUntil(currentConversation, targetNodeIndex)

        val existingTitles = conversationRepo
            .getConversationsOfAssistant(currentConversation.assistantId)
            .first()
            .mapTo(mutableSetOf()) { it.title }
        val forkConversation = createForkConversation(currentConversation, copiedNodes, existingTitles)

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    /** 复制会话到 [targetNodeIndex]（含）的快照节点：重生成节点 id、复制文件引用（分支 / 检查点共用）。 */
    private fun copyNodesUntil(
        conversation: Conversation,
        targetNodeIndex: Int
    ): List<MessageNode> {
        return conversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }
    }

    // ---- ST Checkpoints（书签）----

    /**
     * 创建检查点（对齐 ST `/checkpoint-create` / bookmarks.js `createNewBookmark`）。
     *
     * 检查点 = 从聊天开头复制到 [nodeIndex]（含）的独立快照会话：
     * - 新会话 `stParentConversationId` 指向源会话（`checkpoint-exit` 返回目标）；
     * - 源消息挂 [withCheckpointLink]（点击旗帜打开检查点）；
     * - 新会话最后一条消息挂父链接（供 `checkpoint-parent` 读取父标题）。
     *
     * @param nodeIndex 0 基消息索引（= MessageNode 索引）；null 表示最后一条。
     * @param name 指定名字（对齐 ST forceName）；空白表示按 `<标题> - Checkpoint #N` 自动生成并去重。
     */
    suspend fun createCheckpoint(
        conversationId: Uuid,
        nodeIndex: Int? = null,
        name: String? = null,
    ): Conversation {
        val conversation = getConversationFlow(conversationId).value
        val nodes = conversation.messageNodes
        if (nodes.isEmpty()) throw BadRequestException("No messages to checkpoint")
        val targetIndex = nodeIndex ?: nodes.lastIndex
        if (targetIndex !in nodes.indices) throw NotFoundException("Message not found")

        val defaultBase = context.getString(R.string.checkpoint_default_title)
        val parentTitle = conversation.title.ifBlank { defaultBase }
        val existingTitles = conversationRepo
            .getConversationsOfAssistant(conversation.assistantId)
            .first()
            .map { it.title }
        val checkpointName = buildCheckpointName(
            sourceTitle = conversation.title,
            requested = name,
            existingTitles = existingTitles,
            defaultBase = defaultBase,
        )

        val copiedNodes = copyNodesUntil(conversation, targetIndex).toMutableList()
        // 新会话最后一条消息挂父链接（= checkpoint-parent / checkpoint-exit 的数据源）
        val lastNode = copiedNodes.last()
        copiedNodes[copiedNodes.lastIndex] = lastNode.copy(
            messages = lastNode.messages.map {
                it.withCheckpointLink(conversation.id.toString(), parentTitle)
            }
        )

        val checkpoint = Conversation(
            id = Uuid.random(),
            assistantId = conversation.assistantId,
            title = checkpointName,
            messageNodes = copiedNodes,
            customSystemPrompt = conversation.customSystemPrompt,
            modeInjectionIds = conversation.modeInjectionIds,
            lorebookIds = conversation.lorebookIds,
            stParentConversationId = conversation.id,
        )
        saveConversation(checkpoint.id, checkpoint)

        // 源消息挂检查点链接（对齐 ST extra.bookmark_link；已有链接则覆盖 = 重新创建）
        val updatedSourceNodes = conversation.messageNodes.mapIndexed { index, node ->
            if (index == targetIndex) {
                node.copy(
                    messages = node.messages.map {
                        it.withCheckpointLink(checkpoint.id.toString(), checkpointName)
                    }
                )
            } else {
                node
            }
        }
        if (updatedSourceNodes != conversation.messageNodes) {
            saveConversation(
                conversationId,
                conversation.copy(messageNodes = updatedSourceNodes, updateAt = Instant.now()),
            )
        }

        return checkpoint
    }

    /** 读取第 [nodeIndex]（null = 最后一条）条消息上的检查点链接（内存态，同步）。 */
    fun getCheckpointLinkAt(conversationId: Uuid, nodeIndex: Int?): UIMessageAnnotation.StCheckpointLink? {
        return getConversationFlow(conversationId).value.checkpointLinkAt(nodeIndex)
    }

    /** 会话内全部检查点链接（node 索引 → 链接；内存态，同步）。 */
    fun listCheckpointLinks(conversationId: Uuid): List<Pair<Int, UIMessageAnnotation.StCheckpointLink>> {
        return getConversationFlow(conversationId).value.checkpointLinks()
    }

    /** 检查点会话的父链接（checkpoint-exit / checkpoint-parent 数据源；内存态，同步）。 */
    fun getCheckpointParentLink(conversationId: Uuid): UIMessageAnnotation.StCheckpointLink? {
        return getConversationFlow(conversationId).value.checkpointParentLink()
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))

        // [R4] ST MESSAGE_SWIPED / MESSAGE_UPDATED：index 必须是 number
        val swipedIndex = currentConversation.messageNodes.indexOfFirst { it.id == nodeId }
        if (swipedIndex >= 0) {
            val payload = "{\"args\":[$swipedIndex]}"
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_SWIPED, payload)
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_UPDATED, payload)
        }
    }

    /**
     * [v239] 手机端 swipe 分支切换：补发 ST MESSAGE_SWIPED / MESSAGE_UPDATED。
     *
     * 手机 UI 的 `‹ 1/3 ›` 走 `ChatPage.onUpdateMessage -> ChatVM.updateConversation`，
     * 绕开了 [selectMessageNode] 这个唯一发射点；这里按 nodeId 定位楼层索引后补发事件。
     * 只补发事件，不改 UI / 不改渲染。
     */
    fun notifyMessageSwiped(conversationId: Uuid, nodeId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val swipedIndex = currentConversation.messageNodes.indexOfFirst { it.id == nodeId }
        if (swipedIndex >= 0) {
            val payload = "{\"args\":[$swipedIndex]}"
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_SWIPED, payload)
            fireTavernEvent(conversationId, TavernRuntimeManager.EVENT_MESSAGE_UPDATED, payload)
        }
    }

    /**
     * 原子删除整楼（MessageNode 及其全部 swipe 变体）。
     *
     * 旧实现先读快照再 saveConversation，流式更新/标题生成会在这段窗口里覆盖删除；
     * 现在删除变换在 ConversationSession.tryUpdate 的同一临界区内完成，落库走 session.flush()
     * （写入内存此刻的最新状态），不会再被旧快照回滚。
     */
    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ): DeleteMessageResult {
        val session = sessionManager.getOrCreate(conversationId)
        if (!session.isInitialized) {
            runCatching {
                session.initialize(
                    load = {
                        conversationRepo.getConversationById(conversationId)
                            ?: throw NotFoundException("Conversation not found")
                    },
                )
            }.onFailure {
                return if (failIfMissing) DeleteMessageResult.Failed(it) else DeleteMessageResult.NotFound
            }
        }
        val snapshot = session.state.value
        val targetIndex = snapshot.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetIndex == -1) return DeleteMessageResult.NotFound
        // 正在生成的最后一楼被流式写回时可能“删了又出现”；历史楼允许删除，
        // 当前生成目标给出明确状态，由 UI 提示先停止生成。
        if (targetIndex == snapshot.messageNodes.lastIndex && session.isGenerating) {
            return DeleteMessageResult.Generating
        }
        val removed = session.tryUpdate { current ->
            buildConversationAfterMessageDelete(current, messageId)
        } ?: return DeleteMessageResult.NotFound
        val deletedNodeCount = session.state.value.messageNodes.size
        return try {
            session.flush()
            // 删除可能解除工具审批阻塞；沿用旧 saveConversation 的队列推进语义。
            dispatchNextQueuedMessage(conversationId)
            // [R4] ST MESSAGE_DELETED：ST 载荷为删除后的 chat.length
            fireTavernEvent(
                conversationId,
                TavernRuntimeManager.EVENT_MESSAGE_DELETED,
                "{\"args\":[${removed.currentMessages.size}]}",
            )
            TavernRuntimeManager.recordSendDiagnostic(
                stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
                conversationId = conversationId.toString(),
                detail = "delete_node ok nodes=$deletedNodeCount messageId=$messageId",
            )
            DeleteMessageResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // 落库失败且期间没有别的更新：回滚内存，避免 UI 显示已删除但数据库仍有。
            if (session.state.value === removed) {
                runCatching { session.updateConversation(snapshot) }
            }
            DeleteMessageResult.Failed(e)
        }
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ): DeleteMessageResult = deleteMessage(conversationId, message.id)

    /**
     * [v234 I2] 删除节点的单个 swipe 变体（对齐 ST script.js deleteSwipe / MESSAGE_SWIPE_DELETED）：
     * - 至少保留 1 条变体（只剩 1 条时报错，对应 ST toastr.warning）;
     * - [swipeIndex] 0 基；null = 删当前选中变体；越界报错（对应 ST Invalid swipe ID）;
     * - 删当前变体时新选中 = 被删位（右邻补位）或左邻（删末位），对齐 ST newSwipeId 计算;
     * - 发 MESSAGE_SWIPE_DELETED，载荷 {messageId, swipeId, newSwipeId}（ST script.js:9388 逐字对齐；
     *   订阅实证 JSR src/function/event.ts:197 / src/swipe.ts:8）。
     */
    suspend fun swipeDeleteNode(
        conversationId: Uuid,
        nodeId: Uuid,
        swipeIndex: Int?,
    ) {
        val session = sessionManager.getOrCreate(conversationId)
        val currentConversation = getConversationFlow(conversationId).value
        val nodeIndex = currentConversation.messageNodes.indexOfFirst { it.id == nodeId }
        if (nodeIndex == -1) throw NotFoundException("Message node not found")
        if (nodeIndex == currentConversation.messageNodes.lastIndex && session.isGenerating) {
            throw BadRequestException("Message is being generated")
        }
        val node = currentConversation.messageNodes[nodeIndex]
        val total = node.messages.size
        if (total <= 1) throw BadRequestException("Can't delete the last swipe.")
        val target = swipeIndex ?: node.selectIndex
        if (target !in node.messages.indices) throw BadRequestException("Invalid swipe ID: ${target + 1}")
        val newSwipeId = when {
            target < node.selectIndex -> node.selectIndex - 1
            target > node.selectIndex -> node.selectIndex
            else -> minOf(target, total - 2)
        }
        val updatedNodes = currentConversation.messageNodes.map { existing ->
            if (existing.id != nodeId) existing
            else existing.copy(
                messages = existing.messages.filterIndexed { i, _ -> i != target },
                selectIndex = newSwipeId,
            )
        }
        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
        fireTavernEvent(
            conversationId,
            TavernRuntimeManager.EVENT_MESSAGE_SWIPE_DELETED,
            "{\"messageId\":$nodeIndex,\"swipeId\":$target,\"newSwipeId\":$newSwipeId}",
        )
    }

    /**
     * 产品语义：「删除消息」= 删除整楼（MessageNode 的所有 swipe 变体）。
     * 只删当前变体请走 [swipeDeleteNode]。旧实现只删当前 message，
     * 多 swipe 节点删完会选中下一条，用户看起来像“没删掉”。
     */
    internal fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.filterIndexed { index, _ ->
            index != targetNodeIndex
        }

        // 同步清理群聊 speakerMap，避免残留已删除节点的发言人映射
        return conversation.copy(
            messageNodes = updatedNodes,
            speakerMap = conversation.speakerMap.filterKeys { id -> updatedNodes.any { it.id == id } },
        )
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存，带 2.5s 有界等待与 JSR 并发生成取消）
    suspend fun stopGeneration(conversationId: Uuid) {
        TavernRuntimeManager.cancelAllPendingOperations("stopGeneration:$conversationId")
        val session = sessionManager.get(conversationId) ?: return
        val (jobs, stopEpoch) = synchronized(session) {
            session.isExecutingSendTurn = false
            session.submittingMessage = null
            session.messageQueue.pause()
            val cancelled = session.cancelJobs()
            cancelled to session.currentGenerationEpoch()
        }
        TavernRuntimeManager.recordSendDiagnostic(
            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED,
            conversationId = conversationId.toString(),
            detail = "status=STOPPED_BY_USER activeJobs=${jobs.size}",
        )
        if (jobs.isNotEmpty()) {
            val joinedInTime = withTimeoutOrNull(2500L) {
                jobs.forEach { it.join() }
                true
            } ?: false
            if (!joinedInTime) {
                TavernRuntimeManager.appendLog(
                    "warn",
                    "[cancel] stopGeneration timed out waiting for jobs (2500ms), forcing state cleanup conv=$conversationId",
                )
            }
        }
        var shouldFinalizeStoppedTurn = false
        synchronized(session) {
            val activeJob = session.getJob()
            // Only clear session state if no new generation/resend job was started while waiting for cancelled jobs to join
            if (activeJob == null || activeJob in jobs) {
                if (session.currentGenerationEpoch() == stopEpoch) {
                session.isExecutingSendTurn = false
                session.submittingMessage = null
                    session.setJob(null, cancelPrevious = false)
                    shouldFinalizeStoppedTurn = true
                }
            }
        }
        if (shouldFinalizeStoppedTurn) {
            finishInterruptedPendingTools(conversationId)

            // [R8] job.cancel 已由 onCompletion 发 GENERATION_ENDED；此处补 STOPPED
            fireTavernEvent(
                conversationId,
                TavernRuntimeManager.EVENT_GENERATION_STOPPED,
                "{\"args\":[]}",
            )
        }
    }

    /**
     * [R4/R9] 宿主生命周期事件统一出口：仅当前焦点会话发射，payload 形如 {"args":[...]}。
     * developerMode 决定 TavernRuntimeManager.fireEvent 是否写 [event] 日志。
     */
    private fun fireTavernEvent(conversationId: Uuid, name: String, payloadJson: String) {
        if (TavernRuntimeManager.activeConversationId != conversationId.toString()) return
        TavernRuntimeManager.setDeveloperMode(settingsStore.settingsFlow.value.developerMode)
        TavernRuntimeManager.fireEvent(name, payloadJson)
    }
    private suspend fun loadedSession(conversationId: Uuid): ConversationSession {
        val session = sessionManager.getOrCreate(conversationId)
        if (!session.isInitialized) {
            session.initialize(
                load = { conversationRepo.getConversationById(conversationId) },
                create = { newConversation(conversationId) },
            )
        }
        return session
    }

    suspend fun deleteConversation(conversationId: Uuid) {
        val session = sessionManager.getOrCreate(conversationId)
        discardQueuedMessages(session)
        session.replace(newConversation(conversationId), existsInDatabase = false) { previous ->
            conversationRepo.getConversationById(conversationId)?.let { conversationRepo.deleteConversation(it) }
            previous?.let { filesManager.deleteChatFiles(it.files) }
        }
        sessionManager.removeIfIdle(session)
    }

    suspend fun restoreConversation(conversation: Conversation) {
        val session = sessionManager.getOrCreate(conversation.id)
        session.replace(conversation, existsInDatabase = true) {
            conversationStore.write(previous = null, current = conversation)
        }
        sessionManager.removeIfIdle(session)
    }

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid) {
        val persisted = conversationRepo.getConversationsOfAssistant(assistantId).first().map { it.id }
        val loaded = sessionManager.snapshot()
            .filter { it.isInitialized && it.state.value.assistantId == assistantId }
            .map { it.id }
        (persisted + loaded).distinct().forEach { deleteConversation(it) }
    }

    suspend fun updateChatAssistant(conversationId: Uuid, update: (Assistant) -> Assistant) {
        val session = loadedSession(conversationId)
        val settings = settingsStore.awaitLoaded()
        val conversation = session.state.value
        val stored = settings.getStoredAssistantOf(conversation)
        val updated = update(settings.getAssistantOf(conversation))
        val assistant = updated.withoutConversationFields(conversation, stored)
        session.updateMetadata(
            transform = { it.withAssistantUpdate(updated, settings) },
            persist = { conversationRepo.updateConversationConfig(it) },
        )
        if (assistant != stored) {
            settingsStore.update { latest ->
                latest.copy(assistants = latest.assistants.map { if (it.id == assistant.id) assistant else it })
            }
        }
    }

    suspend fun updateChatSearch(
        conversationId: Uuid,
        enableWebSearch: Boolean? = null,
        builtInSearch: Boolean? = null,
    ) {
        val session = loadedSession(conversationId)
        val conversation = session.state.value
        if (conversation.config != null) {
            session.updateMetadata(
                transform = { current ->
                    val config = current.config
                    if (config == null) current else current.copy(
                        config = config.copy(
                            enableWebSearch = enableWebSearch ?: config.enableWebSearch,
                            builtInSearch = builtInSearch ?: config.builtInSearch,
                        )
                    )
                },
                persist = { conversationRepo.updateConversationConfig(it) },
            )
            return
        }
        val settings = settingsStore.awaitLoaded()
        val assistant = settings.getAssistantOf(conversation)
        val model = settings.getChatModelOf(conversation)
        settingsStore.update { latest ->
            latest.copy(
                assistants = if (enableWebSearch == null) latest.assistants else latest.assistants.map {
                    if (it.id == assistant.id) it.copy(enableWebSearch = enableWebSearch) else it
                },
                providers = if (builtInSearch == null || model == null) latest.providers else latest.providers.map { provider ->
                    provider.editModel(
                        model.copy(
                            tools = if (builtInSearch) model.tools + BuiltInTools.Search else model.tools - BuiltInTools.Search
                        )
                    )
                },
            )
        }
    }
    suspend fun updateTitle(conversationId: Uuid, title: String) {
        val session = loadedSession(conversationId)
        session.updateMetadata(
            transform = { it.copy(title = title) },
            persist = { conversationRepo.updateTitle(conversationId, title) },
        )
    }

    suspend fun updateWorkspaceCwd(conversationId: Uuid, cwd: String?) {
        val session = loadedSession(conversationId)
        session.updateMetadata(
            transform = { it.copy(workspaceCwd = cwd) },
            persist = { conversationRepo.updateConversationConfig(it) },
        )
    }

    suspend fun updateConversationSystemPrompt(conversationId: Uuid, prompt: String?) {
        val session = loadedSession(conversationId)
        session.updateMetadata(
            transform = { it.copy(customSystemPrompt = prompt) },
            persist = { conversationRepo.updateCustomSystemPrompt(conversationId, prompt) },
        )
    }

    suspend fun updateDraftConversationFolder(conversationId: Uuid, folderId: Uuid?) {
        val session = loadedSession(conversationId)
        session.updateDraft { latest -> latest.copy(folderId = folderId) }
    }

    private fun discardQueuedMessages(session: ConversationSession) {
        session.messageQueue.pause()
        session.messageQueue.state.value.messages.forEach { queued ->
            session.messageQueue.remove(queued.id)?.let(::cleanupQueuedAttachments)
        }
    }

    fun discardQueuedMessages(conversationId: Uuid) {
        sessionManager.get(conversationId)?.let(::discardQueuedMessages)
    }
}

/**
 * 生成检查点名字（对齐 ST `getUniqueName` + `buildCheckpointName`）。
 *
 * 基准 = 源标题剥掉旧的 ` - Checkpoint #N` 后缀；候选从 #1 递增直到不与既有标题冲突。
 * 指定 [requested] 时直接采用（对齐 ST forceName：不做去重）。
 */
internal fun buildCheckpointName(
    sourceTitle: String,
    requested: String?,
    existingTitles: Collection<String>,
    defaultBase: String,
): String {
    val requestedName = requested?.trim()?.takeIf { it.isNotBlank() }
    if (requestedName != null) return requestedName

    val suffixRegex = Regex("\\s*-\\s*Checkpoint\\s*#\\d+\\s*$")
    val base = sourceTitle
        .replace(suffixRegex, "")
        .trim()
        .ifBlank { defaultBase }

    var index = 1
    while (true) {
        val candidate = "$base - Checkpoint #$index"
        if (candidate !in existingTitles) return candidate
        index++
    }

}
