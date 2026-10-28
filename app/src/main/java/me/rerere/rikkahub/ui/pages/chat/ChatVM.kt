package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.withHidden
import me.rerere.rikkahub.data.model.withoutHidden
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.st.runtime.applyLorebookDisablePatch
import me.rerere.rikkahub.data.st.runtime.applyLorebookUpsertPatch
import me.rerere.rikkahub.data.st.runtime.buildLorebookFromJsrEntries
import me.rerere.rikkahub.data.st.runtime.updateWorldNameInExtensionsRaw
import me.rerere.rikkahub.data.st.runtime.parseRuntimeChatInserts
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore
import me.rerere.rikkahub.data.model.toMessageNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.rikkahub.data.st.runtime.applyTavernChatUpdates
import me.rerere.rikkahub.data.st.runtime.extractWorldName
import me.rerere.rikkahub.data.st.runtime.findLorebookIndexByName
import me.rerere.rikkahub.data.st.runtime.lorebookNameMatches
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager

import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)

    /**
     * 批次十一 T1（bug C）：某助手最近一次会话的 id（列表视图不需要 nodes）。
     * 切助手时用它决定复用哪个会话，避免又开一个空会话或停在本页面路由 id 上不动。
     */
    suspend fun latestConversationOfAssistant(assistantId: Uuid): Uuid? =
        conversationRepo
            .getConversationsOfAssistant(assistantId)
            .first()
            .firstOrNull()
            ?.id
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    val voiceSession = VoiceSessionController(viewModelScope, context::getString) {
        chatService.enqueueVoiceMessage(_conversationId, it)
    }

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)
        // 批次十一 T1 诊断埋点：切助手时对照真机日志里的 [route] 行即可判定机理 ——
        //   没有新的 [route] ChatVM created = 导航根本没发生（路由/入口问题）；
        //   有新行却仍显示旧消息 = 数据层/会话复用问题。
        TavernRuntimeManager.appendLog(
            "info",
            "[route] ChatVM created: conversationId=$_conversationId",
        )

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId)
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        voiceSession.stop()
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settingsFlow.value)

    // 网络搜索(每个助手独立)
    val enableWebSearch = settings.map {
        it.getCurrentAssistant().enableWebSearch
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 当前模型（Eagerly 订阅并以当前权威设置快照初始化，防止切换会话/面板触发时尚未收集导致 currentChatModel 为 null）
    val currentChatModel = settings.map { settings ->
        val convAssistantId = conversation.value.assistantId
        val convAssistant = settings.getAssistantById(convAssistantId) ?: settings.getCurrentAssistant()
        convAssistant.chatModelId?.let { settings.findModelById(it) }
            ?: settings.getCurrentChatModel()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        settingsStore.settingsFlow.value.getCurrentChatModel(),
    )

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    val messageQueue = chatService.getMessageQueueFlow(_conversationId)

    fun removeQueuedMessage(id: Uuid) = chatService.removeQueuedMessage(_conversationId, id)

    fun beginEditQueuedMessage(id: Uuid) = chatService.beginEditQueuedMessage(_conversationId, id)

    fun finishEditQueuedMessage(id: Uuid, parts: List<UIMessagePart>?) =
        chatService.finishEditQueuedMessage(_conversationId, id, parts)

    fun resumeMessageQueue() = chatService.resumeMessageQueue(_conversationId)

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(transform: (Settings) -> Settings): Job = updateSettings(transform(settings.value))

    fun updateSettings(newSettings: Settings): Job {
        return viewModelScope.launch {
            val oldSettings = settingsStore.settingsFlow.value
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
        }
    }

    /** [P0-3 修复] 基于 SettingsStore 当前最新权威快照的 CAS 更新，避免后台回调（如 thirdPartySettingsSaver）拿旧快照回滚用户刚导入的预设。 */
    fun patchSettings(transform: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            settingsStore.update { current ->
                val next = transform(current)
                checkUserAvatarDelete(current, next)
                next
            }
        }
    }

    /** 同步更新设置并等待完成 — 供切换助手等场景使用 */
    suspend fun updateSettingsAndWait(newSettings: Settings) {
        val oldSettings = settings.value
        checkUserAvatarDelete(oldSettings, newSettings)
        settingsStore.update(newSettings)
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 设置聊天模型
    fun setChatModel(assistant: Assistant, model: Model) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            it.copy(
                                chatModelId = model.id
                            )
                        } else {
                            it
                        }
                    })
            }
        }
    }

    // Update checker
    val updateState = settingsStore.settingsFlow
        .map { settings ->
            !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.checkUpdate() else flowOf(UiState.Loading)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            UiState.Loading,
        )

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(
        content: List<UIMessagePart>,
        answer: Boolean = true,
        onPersisted: ((Boolean) -> Unit)? = null,
    ): Boolean {
        TavernRuntimeManager.appendLog(
            "info",
            "[send-entry] ChatVM.handleMessageSend conv=$_conversationId parts=${content.size} answer=$answer",
        )
        if (content.isEmptyInputMessage()) {
            TavernRuntimeManager.appendLog(
                "warn",
                "[send-diag] handleMessageSend rejected: empty input conv=$_conversationId",
            )
            onPersisted?.invoke(false)
            return false
        }
        TavernRuntimeManager.recordSendDiagnostic(
            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_2_HANDLER_ENTERED,
            conversationId = _conversationId.toString(),
            detail = "parts=${content.size} answer=$answer",
        )
        return chatService.sendMessage(_conversationId, content, answer, onPersisted)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        viewModelScope.launch {
            chatService.editMessage(_conversationId, messageId, parts)
        }
    }

    /**
     * 插入指定角色消息（/sys、/sendas，不触发生成）
     */
    fun handleInsertMessage(role: MessageRole, text: String, name: String? = null, at: Int? = null) {
        if (text.isBlank()) return
        chatService.insertMessage(_conversationId, role, listOf(UIMessagePart.Text(text)), name, at)
    }

    /**
     * 触发一次 AI 回复（/trigger，不添加新消息）
     */
    fun handleTriggerGeneration() {
        chatService.triggerGeneration(_conversationId)
    }

    /**
     * [v240 W3] 表情立绘分类（Character Expressions）。
     *
     * best-effort：失败/超时返回 null，UI 侧回退兜底标签；任何情况下不影响正常聊天。
     */
    suspend fun classifyExpression(assistant: Assistant, text: String, labels: List<String>): String? =
        chatService.classifyExpression(assistant, text, labels)

    /**
     * 生成系统旁白并插入聊天（/sysgen）
     */
    fun handleGenerateSystemNarration(prompt: String, name: String? = null, at: Int? = null, trim: Boolean = false) {
        if (prompt.isBlank()) return
        chatService.generateSystemNarration(_conversationId, prompt, name, at, trim)
    }

    /**
     * 创建检查点（/checkpoint-create：复制聊天到指定消息处生成快照会话）
     */
    suspend fun createCheckpoint(nodeIndex: Int? = null, name: String? = null): Conversation {
        return chatService.createCheckpoint(_conversationId, nodeIndex, name)
    }

    /** 读取消息上的检查点链接（/checkpoint-get；内存态） */
    fun getCheckpointLinkAt(nodeIndex: Int?): UIMessageAnnotation.StCheckpointLink? {
        return chatService.getCheckpointLinkAt(_conversationId, nodeIndex)
    }

    /** 会话内全部检查点链接（/checkpoint-list；内存态） */
    fun listCheckpointLinks(): List<Pair<Int, UIMessageAnnotation.StCheckpointLink>> {
        return chatService.listCheckpointLinks(_conversationId)
    }

    /** 检查点会话的父链接（/checkpoint-parent、/checkpoint-exit；内存态） */
    fun getCheckpointParentLink(): UIMessageAnnotation.StCheckpointLink? {
        return chatService.getCheckpointParentLink(_conversationId)
    }

    /**
     * 压缩上下文
     */
    fun handleCompressContext(additionalPrompt: String, keepRecentMessages: Int): Job {
        return handleCompressContext(
            additionalPrompt = additionalPrompt,
            targetTokens = settings.value.getCurrentChatModel()?.contextLength ?: 4096,
            keepRecentMessages = keepRecentMessages,
        )
    }

    fun updateAssistant(assistant: Assistant) {
        val current = settings.value
        updateSettings(
            current.copy(
                assistants = current.assistants.map { existing ->
                    if (existing.id == assistant.id) assistant else existing
                },
            )
        )
    }
    fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int): Job {
        return viewModelScope.launch {
            chatService.compressConversation(
                _conversationId,
                conversation.value,
                additionalPrompt,
                targetTokens,
                keepRecentMessages
            ).onFailure {
                chatService.addError(it, title = context.getString(R.string.error_title_compress_conversation))
            }
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(_conversationId, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            when (val result = chatService.deleteMessage(_conversationId, message)) {
                me.rerere.rikkahub.service.DeleteMessageResult.Success -> Unit
                me.rerere.rikkahub.service.DeleteMessageResult.NotFound -> {
                    chatService.addError(
                        error = IllegalStateException("消息不存在，可能已被其它操作删除"),
                        conversationId = _conversationId,
                        title = context.getString(R.string.error_title_operation),
                    )
                }
                me.rerere.rikkahub.service.DeleteMessageResult.Generating -> {
                    chatService.addError(
                        error = IllegalStateException("生成中暂时无法删除这条消息，请稍后重试"),
                        conversationId = _conversationId,
                        title = context.getString(R.string.error_title_operation),
                    )
                }
                is me.rerere.rikkahub.service.DeleteMessageResult.Failed -> {
                    chatService.addError(
                        error = result.error,
                        conversationId = _conversationId,
                        title = context.getString(R.string.error_title_operation),
                    )
                }
            }
        }
    }

    /** [v234 I2] 删除指定楼层的当前 swipe 变体（产品入口：消息菜单「删除此轮变体」）。 */
    fun deleteSwipeVariant(node: MessageNode) {
        viewModelScope.launch {
            runCatching {
                chatService.swipeDeleteNode(_conversationId, node.id, null)
            }.onFailure {
                chatService.addError(
                    error = it,
                    conversationId = _conversationId,
                    title = context.getString(R.string.error_title_operation),
                )
            }
        }
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException("请先停止生成再删除消息"),
            conversationId = _conversationId,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        chatService.regenerateAtMessage(_conversationId, message, regenerateAssistantMsg)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatService.stopGeneration(_conversationId)
        }
    }
    /** 官方 /continue：续写最后一条助手回复 */
    fun continueGeneration(prompt: String) {
        chatService.continueGeneration(_conversationId, prompt.ifBlank { null })
    }

    /** 官方 /impersonate：生成你的发言草稿，填入输入框待发送 */
    fun impersonateDraft(prefill: String = "", onDraft: (String) -> Unit) {
        chatService.impersonateDraft(_conversationId, prefill.ifBlank { null }, onDraft)
    }

    /** 官方 /gen：安静生成，结果填入输入框待确认 */
    fun quietGenerate(args: ChatService.GenArgs, onDraft: (String) -> Unit) {
        chatService.quietGenerate(_conversationId, args, onDraft)
    }

    suspend fun quietGenerateSync(
        prompt: String,
        includeHistory: Boolean = true,
        systemPrompt: String = "",
        asRole: ChatService.QuietPromptAs = ChatService.QuietPromptAs.SYSTEM,
        length: Int = 0,
        name: String? = null,
        trim: Boolean = false,
    ): Result<String> = chatService.quietGenerate(
        conversationId = _conversationId.toString(),
        prompt = prompt,
        includeHistory = includeHistory,
        systemPrompt = systemPrompt,
        asRole = asRole,
        length = length,
        name = name,
        trim = trim,
    )

    fun saveConversationAsync() {
        viewModelScope.launch {
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            val updatedConversation = conversation.value.copy(title = title)
            chatService.saveConversation(_conversationId, updatedConversation)
        }
    }

    fun deleteConversation(conversation: Conversation): Job =
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
            if (conversation.id == _conversationId) {
                settingsStore.updateAssistant(targetAssistantId)
            }
        }
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        chatService.translateMessage(_conversationId, message, targetLanguage)
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            chatService.generateTitle(_conversationId, conversationFull, force)
        }
    }

    /** 自定义标题：手动重命名对话（区别于 generateTitle 的模型总结）。 */
    fun renameConversation(conversation: Conversation, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            conversationRepo.updateConversation(conversationFull.copy(title = title))
        }
    }

    fun generateSuggestion(conversation: Conversation) {
        viewModelScope.launch {
            chatService.generateSuggestion(_conversationId, conversation)
        }
    }

    fun clearTranslationField(messageId: Uuid) {
        chatService.clearTranslationField(_conversationId, messageId)
    }

    /**
     * [v239] 手机端分支切换 → 补发 ST MESSAGE_SWIPED（对齐 selectMessageNode 语义）。
     * 只补发事件，不改 UI / 不改渲染。
     */
    fun notifyMessageSwiped(node: MessageNode) {
        chatService.notifyMessageSwiped(_conversationId, node.id)
    }

    fun updateConversation(newConversation: Conversation) {
        chatService.updateConversationState(_conversationId) {
            newConversation
        }
    }

    /**
     * 酒馆运行时聊天记录回写（JSR `setChatMessages` / MVU `saveChat`）：
     * 同步更新会话状态并异步落盘，保证预设按钮切换 swipe_id 后紧随其后的脚本读取或触发立刻生效。
     */
    fun applyRuntimeChatUpdates(jsonText: String): Boolean {
        val current = conversation.value
        val currentSettings = settingsStore.settingsFlow.value
        val assistant = currentSettings.getAssistantById(current.assistantId) ?: currentSettings.getCurrentAssistant()
        val altGreetings = assistant.tavernData?.alternateGreetings.orEmpty()
        val updated = applyTavernChatUpdates(current, jsonText, altGreetings) ?: return false
        updateConversation(updated)
        viewModelScope.launch {
            runCatching { chatService.saveConversation(_conversationId, updated) }
        }
        return true
    }

    /**
     * [v243 BUGFIX A2] JS `RikkaBridge.insertChatMessages`（JSR `createChatMessages` 的宿主落点）。
     *
     * 必须**同步**更新会话状态，不能走 [me.rerere.rikkahub.service.ChatService.insertMessage] 的协程队列：
     * 卡脚本的标准序列是 `await createChatMessages([...]); await triggerSlash('/trigger')`，
     * 而 ChatService 的每个入口（含 /trigger -> triggerGeneration）都会 `session.getJob()?.cancel()`
     * —— 排在队列里的插入会被紧随其后的 /trigger 直接取消，楼层丢失且无任何报错。
     *
     * [updateConversation] 同步写 session.state 并持久化，因此紧随其后的 /trigger 一定能看到新楼层。
     *
     * @return 是否受理（载荷为空/解析失败 false）。
     */
    fun insertRuntimeChatMessages(jsonText: String): Boolean {
        val items = parseRuntimeChatInserts(jsonText)
        if (items.isEmpty()) return false
        val current = conversation.value
        val nodes = items.map { item ->
            UIMessage(
                role = item.role,
                parts = listOf(UIMessagePart.Text(item.message)),
                name = item.name,
            ).toMessageNode()
        }
        // 消息级变量（MVU stat_data 等）必须与楼层下标对齐：追加等量条目，避免卡脚本写错楼层。
        runCatching {
            val existing = TavernVariableStore.getMessageVars(_conversationId.toString())
            val list = (existing as? JsonArray)?.toMutableList() ?: mutableListOf()
            items.forEach { list += (it.data ?: JsonObject(emptyMap())) }
            TavernVariableStore.replaceMessageVars(_conversationId.toString(), JsonArray(list))
        }
        // ① 同步写内存状态：紧随其后的 /trigger 必须立刻看到新楼层（updateConversation 只改 state，不落库）
        val updated = current.copy(messageNodes = current.messageNodes + nodes)
        updateConversation(updated)
        // ② 异步落库：不能阻塞 JavaBridge 线程；落库失败也不影响已经生效的内存状态
        viewModelScope.launch {
            runCatching { chatService.saveConversation(_conversationId, updated) }
        }
        TavernRuntimeManager.appendLog("info", "[chat] insertChatMessages n=${items.size} roles=${items.map { it.role.name }}")
        return true
    }

    /** 读取 SettingsStore 最新权威快照（避免组合期旧 settings.value 滞后）。 */
    fun latestSettingsSnapshot(): Settings = settingsStore.settingsFlow.value

    /**
     * 按 0 基下标集合同步删除消息楼层（供 STscript `/del` `/cut` 与 JS `deleteChatMessages` 共用），
     * 同时同步裁剪 `TavernVariableStore` 消息级变量数组，并返回被删除楼层的文本列表。
     */
    fun deleteChatMessageIndices(indices: Set<Int>): List<String> {
        if (indices.isEmpty()) return emptyList()
        val current = conversation.value
        val nodes = current.messageNodes
        val valid = indices.filter { it in nodes.indices }.sorted()
        if (valid.isEmpty()) return emptyList()
        val validSet = valid.toSet()
        val removedTexts = valid.map { idx ->
            val node = nodes[idx]
            node.messages.getOrNull(node.selectIndex)?.toText().orEmpty()
        }
        val remainingNodes = nodes.filterIndexed { idx, _ -> idx !in validSet }
        runCatching {
            val existing = TavernVariableStore.getMessageVars(_conversationId.toString())
            val list = (existing as? JsonArray)?.toMutableList()
            if (list != null && list.isNotEmpty()) {
                val filteredVars = list.filterIndexed { idx, _ -> idx !in validSet }
                TavernVariableStore.replaceMessageVars(_conversationId.toString(), JsonArray(filteredVars))
            }
        }
        val updated = current.copy(messageNodes = remainingNodes)
        updateConversation(updated)
        viewModelScope.launch {
            runCatching { chatService.saveConversation(_conversationId, updated) }
        }
        TavernRuntimeManager.appendLog("info", "[chat] deleteChatMessages indices=$valid remaining=${remainingNodes.size}")
        return removedTexts
    }

    /**
     * JS `RikkaBridge.deleteChatMessages(idsJson)` 宿主落点：解析下标 JSON 数组并同步删除。
     */
    fun deleteRuntimeChatMessages(idsJson: String): Boolean {
        val ids = runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(idsJson) as? JsonArray ?: return@runCatching emptySet()
            arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }.toSet()
        }.getOrDefault(emptySet())
        if (ids.isEmpty()) return false
        return deleteChatMessageIndices(ids).isNotEmpty()
    }

    /**
     * [v243 BUGFIX A3] JSR 世界书条目写入（JS `RikkaBridge.upsertLorebookEntries`）。
     *
     * 匹配优先级与 [applyLorebookUpdate] 完全一致（卡内嵌书 → 全局书库）。
     * 目前只实现**卡内嵌书**写入：卡脚本用的 `getCharWorldbookNames('current').primary`
     * 就是角色的 `data.extensions.world`，在 rikkaST 里落到 assistant.tavernData.embeddedBook。
     *
     * @return 是否命中并产生实际变更。
     */
    fun applyLorebookUpsert(bookName: String, payloadJson: String): Boolean {
        val currentSettings = settingsStore.settingsFlow.value
        val requested = bookName.trim()
        if (requested.isEmpty()) return false

        val assistant = currentSettings.getAssistantById(conversation.value.assistantId)
        val tav = assistant?.tavernData
        val embedded = tav?.embeddedBook
        if (assistant != null && tav != null && embedded != null) {
            val worldName = extractWorldName(tav.extensionsRaw)
            val name = embedded.name.ifBlank { worldName ?: "" }
            val matched = requested == "current" ||
                lorebookNameMatches(name, bookName) || lorebookNameMatches(worldName, bookName)
            if (matched) {
                val updatedBook = applyLorebookUpsertPatch(embedded, payloadJson)
                if (updatedBook != null) {
                    val newAssistant = assistant.copy(tavernData = tav.copy(embeddedBook = updatedBook))
                    patchSettings { s ->
                        s.copy(
                            assistants = s.assistants.map {
                                if (it.id == assistant.id) newAssistant else it
                            },
                        )
                    }
                }
                TavernRuntimeManager.appendLog(
                    "info",
                    "[lorebook] upsert embedded name='$name' requested='$requested' changed=${updatedBook != null}" +
                        " before=${embedded.entries.size} after=${updatedBook?.entries?.size ?: embedded.entries.size}" +
                        " requestedEntries=${describeUpsertPayload(payloadJson)}",
                )
                return updatedBook != null
            }
        }
        // ② 全局世界书库（[v243 BUGFIX A3b] 本轮补齐）
        val index = findLorebookIndexByName(currentSettings.lorebooks, bookName)
        if (index >= 0) {
            val target = currentSettings.lorebooks[index]
            val updatedBook = applyLorebookUpsertPatch(target, payloadJson)
            if (updatedBook != null) {
                patchSettings { s ->
                    val idx = findLorebookIndexByName(s.lorebooks, bookName)
                    if (idx >= 0) {
                        val list = s.lorebooks.toMutableList().also { it[idx] = updatedBook }
                        s.copy(lorebooks = list)
                    } else s
                }
            }
            TavernRuntimeManager.appendLog(
                "info",
                "[lorebook] upsert global name='${target.name}' requested='$requested' changed=${updatedBook != null}" +
                    " before=${target.entries.size} after=${updatedBook?.entries?.size ?: target.entries.size}" +
                    " requestedEntries=${describeUpsertPayload(payloadJson)}",
            )
            return updatedBook != null
        }

        TavernRuntimeManager.appendLog("warn", "[lorebook] upsert miss requested='$requested'")
        return false
    }

    /**
     * [v214] 世界书写回（JS `RikkaBridge.updateLorebookEntries`）。
     *
     * 匹配优先级与 buildLorebookJson 一致：
     * 1. 卡内嵌书（book.name / data.extensions.world / "current"）→ 按 [TavernBookEntry.id] == uid 改 disable；
     * 2. Settings.lorebooks 中 name == bookName 的全局书 → 按条目下标 uid 改 enabled = !disable。
     * [v216] 两处都改为「精确优先 → 双侧 trim 兜底」（存量书名可能带尾随空格，见交接文档 §11.6），
     * 命中后用该书真实 name 落盘与记日志。
     * 改完经 [updateSettings] 持久化。
     * @return 是否命中并处理了目标书（未命中 false；桥据此回报 JS）。
     */
    fun applyLorebookUpdate(bookName: String, entriesJson: String): Boolean {
        val currentSettings = settingsStore.settingsFlow.value
        val requested = bookName.trim()
        if (requested.isEmpty()) return false

        // ① 卡内嵌书（CAS patchSettings，遵守铁律 2）
        val assistant = currentSettings.getAssistantById(conversation.value.assistantId)
        val tav = assistant?.tavernData
        val embedded = tav?.embeddedBook
        if (assistant != null && tav != null && embedded != null) {
            val worldName = extractWorldName(tav.extensionsRaw)
            val name = embedded.name.ifBlank { worldName ?: "" }
            val matched = requested == "current" || lorebookNameMatches(name, bookName) || lorebookNameMatches(worldName, bookName)
            if (matched) {
                val updatedBook = applyLorebookDisablePatch(embedded, entriesJson)
                if (updatedBook != null) {
                    val newAssistant = assistant.copy(tavernData = tav.copy(embeddedBook = updatedBook))
                    patchSettings { s ->
                        s.copy(
                            assistants = s.assistants.map {
                                if (it.id == assistant.id) newAssistant else it
                            },
                        )
                    }
                }
                TavernRuntimeManager.appendLog(
                    "info",
                    "[lorebook] apply embedded name='$name' requested='$requested' changed=${updatedBook != null}",
                )
                return true
            }
        }

        // ② 全局世界书库（[v216] 精确优先 → 双侧 trim 兜底，CAS patchSettings）
        val index = findLorebookIndexByName(currentSettings.lorebooks, bookName)
        if (index >= 0) {
            val target = currentSettings.lorebooks[index]
            val updatedBook = applyLorebookDisablePatch(target, entriesJson)
            if (updatedBook != null) {
                patchSettings { s ->
                    val idx = findLorebookIndexByName(s.lorebooks, bookName)
                    if (idx >= 0) {
                        val newList = s.lorebooks.toMutableList().also { it[idx] = updatedBook }
                        s.copy(lorebooks = newList)
                    } else s
                }
            }
            TavernRuntimeManager.appendLog(
                "info",
                "[lorebook] apply global name='${target.name}' requested='$requested' trimFallback=${target.name != bookName} changed=${updatedBook != null}",
            )
            return true
        }

        TavernRuntimeManager.appendLog("warn", "[lorebook] apply miss requested='$requested'")
        return false
    }

    /**
     * JSR `createOrReplaceWorldbook(name, entries)` / `createWorldbook(name, entries)` 宿主落点：
     * 在 `Settings.lorebooks` 中创建或替换同名世界书并持久化。
     */
    fun createOrReplaceLorebook(bookName: String, entriesJson: String, replaceIfExists: Boolean = true): Boolean {
        val requested = bookName.trim()
        if (requested.isEmpty()) return false
        val currentSettings = settingsStore.settingsFlow.value
        val existingIdx = findLorebookIndexByName(currentSettings.lorebooks, requested)
        if (existingIdx >= 0 && !replaceIfExists) {
            return true
        }
        patchSettings { s ->
            val idx = findLorebookIndexByName(s.lorebooks, requested)
            val existing = if (idx >= 0) s.lorebooks[idx] else null
            val built = buildLorebookFromJsrEntries(requested, entriesJson, existing)
            val nextBooks = s.lorebooks.toMutableList()
            if (idx >= 0) {
                nextBooks[idx] = built
            } else {
                nextBooks.add(built)
            }
            s.copy(lorebooks = nextBooks)
        }
        return true
    }

    /**
     * JSR `deleteWorldbook(name)` / `deleteLorebook(name)` 宿主落点：
     * 从 `Settings.lorebooks` 删除同名世界书并清理助手与会话的 `lorebookIds` 引用。
     */
    fun deleteLorebookByName(bookName: String): Boolean {
        val requested = bookName.trim()
        if (requested.isEmpty()) return false
        val currentSettings = settingsStore.settingsFlow.value
        val existingIdx = findLorebookIndexByName(currentSettings.lorebooks, requested)
        if (existingIdx < 0) return false
        val removedId = currentSettings.lorebooks[existingIdx].id
        patchSettings { s ->
            val idx = findLorebookIndexByName(s.lorebooks, requested)
            if (idx < 0) return@patchSettings s
            val targetId = s.lorebooks[idx].id
            val nextBooks = s.lorebooks.filterIndexed { i, _ -> i != idx }
            val nextAssistants = s.assistants.map { a ->
                if (targetId in a.lorebookIds) a.copy(lorebookIds = a.lorebookIds - targetId) else a
            }
            s.copy(lorebooks = nextBooks, assistants = nextAssistants)
        }
        val conv = conversation.value
        if (removedId in conv.lorebookIds) {
            updateConversation(conv.copy(lorebookIds = conv.lorebookIds - removedId))
            saveConversationAsync()
        }
        return true
    }

    /**
     * JSR `setChatLorebook` / `rebindCharWorldbooks` / `rebindGlobalWorldbooks` 宿主落点。
     */
    fun rebindLorebooks(scope: String, namesJson: String): Boolean {
        val names = runCatching {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(namesJson) as? JsonArray ?: return@runCatching emptyList()
            arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim()?.takeIf { n -> n.isNotEmpty() } }
        }.getOrDefault(emptyList())
        val normScope = scope.trim().lowercase()
        when (normScope) {
            "global" -> {
                patchSettings { s ->
                    val nextBooks = s.lorebooks.map { book ->
                        val shouldEnable = names.any { lorebookNameMatches(book.name, it) }
                        if (book.enabled != shouldEnable) book.copy(enabled = shouldEnable) else book
                    }
                    s.copy(lorebooks = nextBooks)
                }
                return true
            }
            "char", "character" -> {
                val convAssistantId = conversation.value.assistantId
                patchSettings { s ->
                    val matchedIds = s.lorebooks
                        .filter { b -> names.any { lorebookNameMatches(b.name, it) } }
                        .map { it.id }
                        .toSet()
                    val primaryName = names.firstOrNull()
                    val nextAssistants = s.assistants.map { a ->
                        if (a.id == convAssistantId) {
                            val updatedTav = a.tavernData?.let { tav ->
                                tav.copy(extensionsRaw = updateWorldNameInExtensionsRaw(tav.extensionsRaw, primaryName))
                            }
                            a.copy(lorebookIds = matchedIds, tavernData = updatedTav)
                        } else a
                    }
                    s.copy(assistants = nextAssistants)
                }
                return true
            }
            "chat" -> {
                val s = settingsStore.settingsFlow.value
                val matchedIds = s.lorebooks
                    .filter { b -> names.any { lorebookNameMatches(b.name, it) } }
                    .map { it.id }
                    .toSet()
                val conv = conversation.value
                updateConversation(conv.copy(lorebookIds = matchedIds))
                saveConversationAsync()
                return true
            }
            else -> return false
        }
    }

    fun toggleMessageFavorite(node: MessageNode) {
        viewModelScope.launch {
            val currentlyFavorited = favoriteRepository.isNodeFavorited(_conversationId, node.id)
            if (currentlyFavorited) {
                favoriteRepository.removeNodeFavorite(_conversationId, node.id)
            } else {
                favoriteRepository.addNodeFavorite(
                    NodeFavoriteTarget(
                        conversationId = _conversationId,
                        conversationTitle = conversation.value.title,
                        nodeId = node.id,
                        node = node
                    )
                )
            }

            chatService.updateConversationState(_conversationId) { currentConversation ->
                currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.map { existingNode ->
                        if (existingNode.id == node.id) {
                            existingNode.copy(isFavorite = !currentlyFavorited)
                        } else {
                            existingNode
                        }
                    }
                )
            }
        }
    }

    /** 对齐 ST /hide：切换消息隐藏标记（作用于整条消息的全部 swipe 变体，写回并持久化）。 */
    fun toggleMessageHidden(node: MessageNode) {
        val hide = node.messages.getOrNull(node.selectIndex)?.isHidden != true
        chatService.updateConversationState(_conversationId) { currentConversation ->
            currentConversation.copy(
                messageNodes = currentConversation.messageNodes.map { existingNode ->
                    if (existingNode.id != node.id) {
                        existingNode
                    } else {
                        existingNode.copy(
                            messages = existingNode.messages.map { message ->
                                if (hide) message.withHidden() else message.withoutHidden()
                            }
                        )
                    }
                }
            )
        }
        saveConversationAsync()
    }

    fun updateWorkspaceCwd(cwd: String?) {
        viewModelScope.launch {
            chatService.updateWorkspaceCwd(conversation.value.id, cwd)
        }
    }

    fun updateConversationSystemPrompt(prompt: String?) {
        viewModelScope.launch {
            chatService.updateConversationSystemPrompt(conversation.value.id, prompt)
        }
    }

    fun updateDraftFolder(folderId: Uuid?) {
        viewModelScope.launch {
            chatService.updateDraftConversationFolder(conversation.value.id, folderId)
        }
    }
}


/**
 * [v244] 世界书写入诊断：把 `upsertLorebookEntries` 载荷里要写/要删的条目名与 uid 摘出来打进日志。
 *
 * 为什么需要：真机「世界书里没有多出我的设定」有两种可能 —— 写没落盘，或写到了另一本书/另一个条目名。
 * 只打 changed=true 分不清这两种；把条目名打出来，一次复现就能判定，不必再猜。
 */
private fun describeUpsertPayload(payloadJson: String): String = runCatching {
    val obj = kotlinx.serialization.json.Json.parseToJsonElement(payloadJson) as? kotlinx.serialization.json.JsonObject
        ?: return@runCatching "parse-failed"
    val upsert = obj["upsert"] as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
    val del = obj["delete"] as? kotlinx.serialization.json.JsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
    val names = upsert.mapNotNull { el ->
        val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val nm = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
        val uid = (o["uid"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: -1
        val contentLen = ((o["content"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "").length
        "name='$nm' uid=$uid contentLen=$contentLen"
    }
    val uids = del.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }
    "upsert=${names.size}(${names.joinToString("; ")}) delete=$uids replaceAll=${(obj["replaceAll"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true}"
}.getOrDefault("describe-failed")
