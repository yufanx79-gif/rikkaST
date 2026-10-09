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
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    // 网络搜索(每个助手独立)
    val enableWebSearch = settings.map {
        it.getCurrentAssistant().enableWebSearch
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 当前模型
    val currentChatModel = settings.map { settings ->
        settings.getCurrentChatModel()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(newSettings: Settings): Job {
        return viewModelScope.launch {
            val oldSettings = settings.value
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
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
    fun handleMessageSend(content: List<UIMessagePart>,answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        chatService.sendMessage(_conversationId, content, answer)
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
            chatService.deleteMessage(_conversationId, message)
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
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launch
            // 文件夹是助手内分组，切换助手后原文件夹在新助手下不可见，需清空归属避免会话丢失
            val updatedConversation = conversationFull.copy(
                assistantId = targetAssistantId,
                folderId = null,
            )
            if (conversation.id == _conversationId) {
                chatService.saveConversation(_conversationId, updatedConversation)
                settingsStore.updateAssistant(targetAssistantId)
            } else {
                conversationRepo.updateConversation(updatedConversation)
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
     * 在视图模型作用域内应用楼层内容变更（swipes / swipe_id / mes / is_system / name）并持久化。
     */
    fun applyRuntimeChatUpdates(jsonText: String) {
        viewModelScope.launch {
            val current = conversation.value
            val updated = applyTavernChatUpdates(current, jsonText) ?: return@launch
            updateConversation(updated)
        }
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
        val currentSettings = settings.value
        val requested = bookName.trim()
        if (requested.isEmpty()) return false

        // ① 卡内嵌书
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
                    updateSettings(
                        currentSettings.copy(
                            assistants = currentSettings.assistants.map {
                                if (it.id == assistant.id) newAssistant else it
                            },
                        ),
                    )
                }
                TavernRuntimeManager.appendLog(
                    "info",
                    "[lorebook] apply embedded name='$name' requested='$requested' changed=${updatedBook != null}",
                )
                return true
            }
        }

        // ② 全局世界书库（[v216] 精确优先 → 双侧 trim 兜底）
        val index = findLorebookIndexByName(currentSettings.lorebooks, bookName)
        if (index >= 0) {
            val target = currentSettings.lorebooks[index]
            val updatedBook = applyLorebookDisablePatch(target, entriesJson)
            if (updatedBook != null) {
                val newList = currentSettings.lorebooks.toMutableList().also { it[index] = updatedBook }
                updateSettings(currentSettings.copy(lorebooks = newList))
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

}
