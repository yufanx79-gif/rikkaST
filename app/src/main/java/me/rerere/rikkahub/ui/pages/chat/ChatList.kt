package me.rerere.rikkahub.ui.pages.chat

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDownDouble
import me.rerere.hugeicons.stroke.ArrowUpDouble
import me.rerere.hugeicons.stroke.CursorPointer01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.withConversation
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.ui.ErrorCardsDisplay
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.components.ListSelectableItem
import me.rerere.ui.components.Tooltip
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

private const val TAG = "ChatList"
private const val LoadingIndicatorKey = "LoadingIndicator"
private const val ScrollBottomKey = "ScrollBottomKey"

@Composable
fun ChatList(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String? = null,
    previewMode: Boolean,
    settings: Settings,
    hazeState: HazeState,
    errors: List<ChatError> = emptyList(),
    onDismissError: (Uuid) -> Unit = {},
    onClearAllErrors: () -> Unit = {},
    onRegenerate: (UIMessage) -> Unit = {},
    onImpersonate: ((UIMessage) -> Unit)? = null,
    onEdit: (UIMessage) -> Unit = {},
    onForkMessage: (UIMessage) -> Unit = {},
    onCreateCheckpointMessage: ((UIMessage) -> Unit)? = null,
    onDelete: (UIMessage) -> Unit = {},
    onDeleteSwipe: ((MessageNode) -> Unit)? = null,
    onUpdateMessage: (MessageNode) -> Unit = {},
    onToggleHidden: ((MessageNode) -> Unit)? = null,
    onClickSuggestion: (String) -> Unit = {},
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onJumpToMessage: (Int) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
) {
    AnimatedContent(
        targetState = previewMode,
        label = "ChatListMode",
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.8f) togetherWith fadeOut() + scaleOut(targetScale = 0.8f))
        }
    ) { target ->
        if (target) {
            ChatListPreview(
                innerPadding = innerPadding,
                conversation = conversation,
                settings = settings,
                hazeState = hazeState,
                onJumpToMessage = onJumpToMessage,
                animatedVisibilityScope = this@AnimatedContent,
            )
        } else {
            ChatListNormal(
                innerPadding = innerPadding,
                conversation = conversation,
                state = state,
                loading = loading,
                processingStatus = processingStatus,
                settings = settings,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = onRegenerate,
                onImpersonate = onImpersonate,
                onEdit = onEdit,
                onForkMessage = onForkMessage,
                onCreateCheckpointMessage = onCreateCheckpointMessage,
                onDelete = onDelete,
                onDeleteSwipe = onDeleteSwipe,
                onUpdateMessage = onUpdateMessage,
                onToggleHidden = onToggleHidden,
                onClickSuggestion = onClickSuggestion,
                onTranslate = onTranslate,
                onClearTranslation = onClearTranslation,
                animatedVisibilityScope = this@AnimatedContent,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onToggleFavorite = onToggleFavorite,
                onConversationSystemPromptChange = onConversationSystemPromptChange,
            )
        }
    }
}

// 顶部渐隐：消息滚到标题栏下面时逐渐透明，从状态栏一直淡出到标题栏下沿之外，保证标题可读。
// 让内容自己变透明而不是盖一层背景色，背景图和动态渐变背景才不会被盖住。
private fun Modifier.topFadingEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fadeHeight = height.toPx()
        val brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.Black.copy(alpha = 0.15f),
            1f to Color.Black,
            startY = 0f,
            endY = fadeHeight,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, size = Size(size.width, fadeHeight), blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun ChatListNormal(
    innerPadding: PaddingValues,
    conversation: Conversation,
    state: LazyListState,
    loading: Boolean,
    processingStatus: String? = null,
    settings: Settings,
    hazeState: HazeState,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onImpersonate: ((UIMessage) -> Unit)?,
    onEdit: (UIMessage) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onCreateCheckpointMessage: ((UIMessage) -> Unit)?,
    onDelete: (UIMessage) -> Unit,
    onDeleteSwipe: ((MessageNode) -> Unit)?,
    onUpdateMessage: (MessageNode) -> Unit,
    onToggleHidden: ((MessageNode) -> Unit)?,
    onClickSuggestion: (String) -> Unit,
    onTranslate: ((UIMessage, java.util.Locale) -> Unit)?,
    onClearTranslation: (UIMessage) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
    onConversationSystemPromptChange: ((String?) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val loadingState by rememberUpdatedState(loading)
    var isRecentScroll by remember { mutableStateOf(false) }
    val conversationUpdated by rememberUpdatedState(conversation)
    val density = LocalDensity.current
    val activity = LocalContext.current as? me.rerere.rikkahub.RouteActivity

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Boolean = { isVolumeUp ->
            if (settings.displaySetting.enableVolumeKeyScroll) {
                // 列表视口包含标题栏和输入区遮住的部分，翻页时只按可见区域计算
                val coveredPx = with(density) {
                    (innerPadding.calculateTopPadding() + 32.dp + innerPadding.calculateBottomPadding()).toPx()
                }
                val scrollAmount = (state.layoutInfo.viewportSize.height - coveredPx) *
                    settings.displaySetting.volumeKeyScrollRatio
                scope.launch { state.scrollBy(if (isVolumeUp) -scrollAmount else scrollAmount) }
                true
            } else false
        }
        activity?.volumeKeyListeners?.add(listener)
        onDispose {
            activity?.volumeKeyListeners?.remove(listener)
        }
    }

    /**
     * 判断列表当前是否「已经贴到底部」。
     *
     * [P0 修复] 旧实现只判断 `lastPos <= inputPos - 8`，在**内容不足一屏**时
     * （例如只有开场白、或只有一两条消息）恒为 true。于是每次
     * `visibleItemsInfo` 变化都会触发 `requestScrollToItem(lastIndex)`，
     * 而该 API 会把目标 item 对齐到视口**顶部** —— 用户看到的就是
     * 「输入消息点发送，列表跳到最上面」。
     *
     * 正确语义（对齐聊天类 App 的通行做法）：
     *  - 内容不足一屏：整个列表本来就全部可见，**不需要任何滚动**；
     *  - 内容超过一屏：只有当最后一项的底边已经进入视口底部附近时，
     *    才认为「用户正停在底部」，此时新消息才应自动跟随。
     */
    fun List<LazyListItemInfo>.isAtBottom(): Boolean {
        // [P0 修复 · 2026-11-19] 这里原来用 `lastOrNull()` —— 那是
        // **视口内最后一个可见项**，不是列表的最后一项！
        //
        // 后果（用户实测反馈）：共 10 层楼，用户停在第 5 层，
        // visibleItemsInfo.lastOrNull() 是第 6 层，它的底边当然在视口内 →
        // isAtBottom() 恒为 true → 只要 loadingState 为真就每帧把他拽到底部。
        // 用户观感就是「我在第 2 层发消息，它自己跳到第 1 层 / 开场白」。
        //
        // 正确语义：必须是**列表真正的最后一项**（index == totalItemsCount - 1）
        // 落在视口内，才算「贴底」。
        val layout = state.layoutInfo
        val total = layout.totalItemsCount
        if (total <= 0) return false
        // 没有任何可滚动空间 = 内容不足一屏，全部可见，不需要滚动
        if (!state.canScrollForward && !state.canScrollBackward) return true
        // 只有「真正的最后一项」出现在视口里，才可能贴底。
        val trueLast = firstOrNull { it.index == total - 1 } ?: return false
        val inputBarHeight = with(density) { innerPadding.calculateBottomPadding().toPx() }
        val lastPos = trueLast.offset + trueLast.size
        val inputPos = (layout.viewportEndOffset - inputBarHeight.roundToInt())
        // 容差放宽到 24px：流式输出时最后一项高度每帧都在涨，
        // 8px 容差会让它在「刚好差一点」时反复判定 false→true→false，形成抖动。
        return lastPos <= inputPos - 24
    }

    /**
     * [P0 新增 · 2026-11-19] 「用户是否停在底部附近」—— 用于**自动跟随**判定。
     *
     * 与 [isAtBottom] 的区别：
     *  - [isAtBottom]：最后一项**贴到**底部（严格），用于「要不要显示回到底部按钮」；
     *  - [isCloseToBottom]：最后一项**在视口内可见**（宽松），用于「新消息来了要不要跟随」。
     *
     * 为什么要宽松：流式输出时最后一项高度每帧都在涨，严格的「贴底」判定会在
     * 阈值附近反复 true/false 抖动，导致列表一抽一抽。只要用户还能看到最后一项，
     * 就认为他在跟随，滚动是符合预期的。
     */
    fun LazyListState.isCloseToBottom(): Boolean {
        val info = layoutInfo
        val total = info.totalItemsCount
        if (total <= 0) return true
        // 内容不足一屏：本来全部可见
        if (!canScrollForward && !canScrollBackward) return true
        // 列表真正的最后一项出现在可见项里 → 用户在底部附近
        return info.visibleItemsInfo.any { it.index == total - 1 }
    }

    /**
     * 内容不足一屏时无需滚动；否则把最后一项滚到「底部对齐」。
     *
     * 直接用 `requestScrollToItem` 有一个陷阱：它把目标对齐到**顶部**。
     * 对聊天列表来说这会让最后一条消息跳到屏幕最上方，观感就是「跳顶」。
     * 这里用 `scrollToItem(index, scrollOffset)` 让目标贴底：
     * offset = 视口高度（正向偏移 = 目标顶部在视口底部之外，即贴底）。
     */
    suspend fun LazyListState.scrollToBottomSafely(lastIndex: Int) =
        scrollToBottomAligned(lastIndex)

    // 聊天选择
    val selectedItems = remember { mutableStateListOf<Uuid>() }
    var selecting by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // 自动跟随键盘滚动
    ImeLazyListAutoScroller(lazyListState = state)

    val assistant = remember(
        settings.assistants,
        conversation.assistantId,
        conversation.config,
        conversation.modeInjectionIds,
        conversation.lorebookIds,
    ) {
        // 工作区等配置在会话开始后以会话上固定的为准
        settings.getAssistantById(conversation.assistantId)?.withConversation(conversation)
    }
    val modelById = remember(settings.providers) {
        settings.providers
            .flatMap { it.models }
            .associateBy { it.id }
    }
    val lastMessageIndex = conversation.messageNodes.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        // 自动滚动到底部（流式输出跟随）
        //
        // [P0 修复 · 2026-11-19] 原实现是：
        //   LaunchedEffect(state) { snapshotFlow { visibleItemsInfo }.collect { ... 滚到底 ... } }
        // 三个问题叠加，正是用户说的「我在第 2 层发消息，它自己跳到第 1 层 / 开场白」：
        //
        // 1. `snapshotFlow { visibleItemsInfo }` 在**滚动过程中每一帧**都发射。
        //    而滚到底本身又会改变 visibleItemsInfo → 再次发射 → 再滚 —— 回弹抖动。
        // 2. 判断用的是 `visibleItemsInfo.isAtBottom()`，
        //    旧 isAtBottom 拿的是「视口内最后可见项」而非列表最后一项（已单独修）。
        // 3. 只要 `loadingState` 为真（流式输出期间一直为真），任何一帧判定成立就强制滚到底，
        //    用户根本无法在生成过程中向上翻看历史 —— 一翻就被拽回去。
        //
        // 新语义（对齐微信/Telegram 的通行做法）：
        //  - 只在**消息条数增加**时考虑跟随，不再订阅滚动帧；
        //  - 跟随的前提是「用户本来就停在底部」（用户手动上滑则不跟）；
        //  - 用户手动上滑后设置 sticky 标志，本次生成期间不再打扰，直到他滚回底部。
        if (settings.displaySetting.enableAutoScroll) {
            // 只在「条数变化」时求值，避免滚动帧驱动
            LaunchedEffect(conversationUpdated.messageNodes.size) {
                if (!loadingState) return@LaunchedEffect
                // 用户手动上滑过 → 本次不打扰
                if (!state.isCloseToBottom()) return@LaunchedEffect
                state.scrollToBottomSafely(conversationUpdated.messageNodes.lastIndex.coerceAtLeast(0))
            }
        }

        // 判断最近是否滚动，并同步至面板加载调度器（阶段 6.2：快速滑动期间延后挂载 WebView）
        DisposableEffect(Unit) {
            onDispose {
                me.rerere.rikkahub.ui.components.message.TavernPanelLoadScheduler.setListScrolling(false)
            }
        }
        LaunchedEffect(state.isScrollInProgress) {
            me.rerere.rikkahub.ui.components.message.TavernPanelLoadScheduler.setListScrolling(state.isScrollInProgress)
            if (state.isScrollInProgress) {
                isRecentScroll = true
                delay(1500)
                isRecentScroll = false
            } else {
                delay(1500)
                isRecentScroll = false
            }
        }

        ChatFontProvider(displaySetting = settings.displaySetting) {
            LazyColumn(
                state = state,
                // 顶部留白放进 contentPadding，让消息可以滚动到透明标题栏下面
                contentPadding = PaddingValues(16.dp) + PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 32.dp + innerPadding.calculateBottomPadding(),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .topFadingEdge(innerPadding.calculateTopPadding() + 24.dp)
                    .hazeSource(state = hazeState),
            ) {
            itemsIndexed(
                items = conversation.messageNodes,
                key = { index, item -> item.id },
            ) { index, node ->
                Column {
                    ListSelectableItem(
                        key = node.id,
                        onSelectChange = {
                            if (!selectedItems.contains(node.id)) {
                                selectedItems.add(node.id)
                            } else {
                                selectedItems.remove(node.id)
                            }
                        },
                        selectedKeys = selectedItems,
                        enabled = selecting,
                    ) {
                        ChatMessage(
                            node = node,
                            model = node.currentMessage.modelId?.let(modelById::get)
                                ?: node.currentMessage.snapshotModel(),
                            assistant = assistant,
                            loading = loading && index == lastMessageIndex,
                            onRegenerate = {
                                onRegenerate(node.currentMessage)
                            },
                            onImpersonate = onImpersonate?.let { callback -> { callback(node.currentMessage) } },
                            onEdit = {
                                onEdit(node.currentMessage)
                            },
                            onFork = {
                                onForkMessage(node.currentMessage)
                            },
                            onDelete = {
                                onDelete(node.currentMessage)
                            },
                            onDeleteSwipe = onDeleteSwipe?.let { callback -> { callback(node) } },
                            onCreateCheckpoint = onCreateCheckpointMessage?.let { callback -> { callback(node.currentMessage) } },
                            onToggleHidden = onToggleHidden?.let { callback -> { callback(node) } },
                            onShare = {
                                selecting = true
                                selectedItems.clear()
                                val currentNodes = conversationUpdated.messageNodes
                                val idx = currentNodes.indexOfFirst { it.id == node.id }
                                if (idx >= 0) {
                                    selectedItems.addAll(currentNodes.subList(0, idx + 1).map { it.id })
                                }
                            },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            isFavorite = node.isFavorite,
                            onToggleFavorite = {
                                onToggleFavorite?.invoke(node)
                            },
                            onTranslate = onTranslate,
                            onClearTranslation = onClearTranslation,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                            lastMessage = index == lastMessageIndex,
                            messageDepth = (lastMessageIndex - index).coerceAtLeast(0),
                            messageIndex = index,
                        )
                    }
                }
            }

            if (!loading && assistant?.allowConversationSystemPrompt == true && onConversationSystemPromptChange != null) {
                item(key = "ConversationSystemPrompt") {
                    ConversationSystemPromptButton(
                        customSystemPrompt = conversation.customSystemPrompt,
                        onSystemPromptChange = onConversationSystemPromptChange,
                    )
                }
            }

            if (loading) {
                item(LoadingIndicatorKey) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                        AnimatedVisibility(
                            visible = processingStatus != null,
                        ) {
                            Text(
                                text = processingStatus ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 为了能正确滚动到这
            item(ScrollBottomKey) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 错误消息卡片
            ErrorCardsDisplay(
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )

            // 完成选择
            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(48).dp),
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it * 2 },
                ),
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                ) {
                    Tooltip(
                        tooltip = {
                            Text("Clear selection")
                        }
                    ) {
                        IconButton(
                            onClick = {
                                selecting = false
                                selectedItems.clear()
                            }
                        ) {
                            Icon(HugeIcons.Cancel01, null)
                        }
                    }
                    Tooltip(
                        tooltip = {
                            Text("Select all")
                        }
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.addAll(conversation.messageNodes.map { it.id })
                                }
                            }
                        ) {
                            Icon(HugeIcons.CursorPointer01, null)
                        }
                    }
                    Tooltip(
                        tooltip = {
                            Text("Confirm")
                        }
                    ) {
                        FilledIconButton(
                            onClick = {
                                selecting = false
                                val messages = conversation.messageNodes.filter { it.id in selectedItems }
                                if (messages.isNotEmpty()) {
                                    showExportSheet = true
                                }
                            }
                        ) {
                            Icon(HugeIcons.Tick01, null)
                        }
                    }
                }
            }

            // 导出对话框
            ChatExportSheet(
                visible = showExportSheet,
                onDismissRequest = {
                    showExportSheet = false
                    selectedItems.clear()
                },
                conversation = conversation,
                selectedMessages = conversation.messageNodes.filter { it.id in selectedItems }
                    .map { it.currentMessage }
            )

            val captureProgress = LocalScrollCaptureInProgress.current

            // 消息快速跳转
            MessageJumper(
                show = isRecentScroll && !state.isScrollInProgress && settings.displaySetting.showMessageJumper && !captureProgress,
                onLeft = settings.displaySetting.messageJumperOnLeft,
                scope = scope,
                state = state
            )

            // Suggestion
            if (conversation.chatSuggestions.isNotEmpty() && !captureProgress) {
                ChatSuggestionsRow(
                    conversation = conversation,
                    onClickSuggestion = onClickSuggestion,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * 提取包含搜索词的文本片段，确保匹配词在开头可见
 */
private fun extractMatchingSnippet(
    text: String,
    query: String
): String {
    if (query.isBlank()) {
        return text
    }

    val matchIndex = text.indexOf(query, ignoreCase = true)
    if (matchIndex == -1) {
        return text
    }

    // 直接从匹配词开始显示，确保匹配词在最前面
    val snippet = text.substring(matchIndex)

    // 只在前面有内容时添加省略号
    return if (matchIndex > 0) {
        "...$snippet"
    } else {
        snippet
    }
}

private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var startIndex = 0
        var index = text.indexOf(query, startIndex, ignoreCase = true)

        while (index >= 0) {
            // 添加高亮前的文本
            append(text.substring(startIndex, index))

            // 添加高亮文本
            withStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = Color.Black
                )
            ) {
                append(text.substring(index, index + query.length))
            }

            startIndex = index + query.length
            index = text.indexOf(query, startIndex, ignoreCase = true)
        }

        // 添加剩余文本
        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }
}

@Composable
private fun ChatListPreview(
    innerPadding: PaddingValues,
    conversation: Conversation,
    settings: Settings,
    hazeState: HazeState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onJumpToMessage: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // 过滤消息，同时保留原始 index 避免后续 O(n) indexOf 查找
    val filteredMessages = remember(conversation.messageNodes, searchQuery) {
        if (searchQuery.isBlank()) {
            conversation.messageNodes.mapIndexed { index, node -> index to node }
        } else {
            conversation.messageNodes.mapIndexed { index, node -> index to node }
                .filter { (_, node) -> node.currentMessage.toText().contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .padding(top = innerPadding.calculateTopPadding())
            .fillMaxSize()
            .hazeSource(state = hazeState),
    ) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.history_page_search)) },
            leadingIcon = {
                Icon(
                    imageVector = HugeIcons.Search01,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "Clear",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            maxLines = 1,
        )

        // 消息预览
        LazyColumn(
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(
                items = filteredMessages,
                key = { index, item -> item.second.id },
            ) { _, (originalIndex, node) ->
                val message = node.currentMessage
                val isUser = message.role == me.rerere.ai.core.MessageRole.USER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!isUser) Modifier.padding(end = 24.dp) else Modifier
                        ),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onJumpToMessage(originalIndex)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
                            val highlightedText = remember(searchQuery, message) {
                                val fullText = message.toText().trim().ifBlank { "[...]" }
                                val messageText = extractMatchingSnippet(
                                    text = fullText,
                                    query = searchQuery
                                )
                                buildHighlightedText(
                                    text = messageText,
                                    query = searchQuery,
                                    highlightColor = highlightColor
                                )
                            }
                            Text(
                                text = highlightedText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    conversation: Conversation,
    onClickSuggestion: (String) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(conversation.chatSuggestions) { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        onClickSuggestion(suggestion)
                    }
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BoxScope.MessageJumper(
    show: Boolean,
    onLeft: Boolean,
    scope: CoroutineScope,
    state: LazyListState
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = slideInHorizontally(
            initialOffsetX = { if (onLeft) -it * 2 else it * 2 },
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it * 2 else it * 2 },
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(0)
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowUpDouble,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(
                            (state.firstVisibleItemIndex - 1).fastCoerceAtLeast(
                                0
                            )
                        )
                    }
                },
                shape = CircleShape,
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowUp01,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(state.firstVisibleItemIndex + 1)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f)
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
            Surface(
                onClick = {
                    scope.launch {
                        state.scrollToItem(state.layoutInfo.totalItemsCount - 1)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    4.dp
                ).copy(alpha = 0.65f),
            ) {
                Icon(
                    imageVector = HugeIcons.ArrowDownDouble,
                    contentDescription = stringResource(R.string.chat_page_scroll_to_bottom),
                    modifier = Modifier
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * 把列表滚到「最后一项贴底」的位置。
 *
 * ## 为什么不用 `requestScrollToItem(lastIndex)`
 *
 * `LazyListState.requestScrollToItem(index)` 的语义是把目标 item 对齐到视口**顶部**。
 * 对聊天列表来说，这会让最后一条消息跳到屏幕最上方 —— 用户观感就是
 * 「发送后列表跳到顶」。
 *
 * 正确做法：`scrollToItem(index, scrollOffset)`，`scrollOffset` 为正表示
 * 目标 item 的顶部位于视口起点**之下**这么多像素；给一个视口高度即可让
 * 目标贴底。
 *
 * ## 内容不足一屏时不滚动
 *
 * 此时 `canScrollForward` 与 `canScrollBackward` 都为 false，本来就没有
 * 可滚动空间。强行 scrollToItem 会把内容顶到屏幕上方，形成「只有开场白时
 * 跳到最上面」的现象。
 */
suspend fun LazyListState.scrollToBottomAligned(lastIndex: Int) {
    if (lastIndex < 0) return
    val layout = layoutInfo
    if (layout.totalItemsCount <= 0) return
    // 内容不足一屏（没有任何可滚动空间）→ 保持不动。
    // 这一步必须有：否则 scrollToItem 会把本来全部可见的内容顶到屏幕上方，
    // 形成「只有开场白时跳到最上面」。
    if (!canScrollForward && !canScrollBackward) return
    // [P0 修复 · 2026-11-19] 原来传 `scrollToItem(lastIndex, viewportHeight)`。
    //
    // 那个 offset 的语义是「目标项顶边相对视口顶边**再往下**偏移多少像素」，
    // 传一个视口高度等于要求「最后一项的顶边放到视口底部之外」。
    // List 会 clamp 到最大滚动量，结果取决于最后一项有多高：
    //   - 最后一项很高（≥ 视口）→ clamp 后碰巧贴底，看起来是对的；
    //   - 最后一项很矮（只有一两行）→ clamp 后滚到 maxScroll，此时**视口顶部
    //     会出现一大堆历史消息**，用户观感就是「跳到上面去了」。
    // 这正是用户反馈的「在第二层发消息，却跳到第一层 / 开场白」。
    //
    // 正确做法：`scrollToItem(lastIndex)`（不带 offset）就是「让这一项尽量贴底」——
    // 它内部会 clamp 到 maxScroll，效果等价于「滚到最底部」，且与最后一项高度无关。
    // 这也正是微信 / Telegram 的行为：发消息后停在最底部，不跳到别处。
    // [确定性写法] 完全不用 scrollOffset 参数 —— 它的含义是
    // 「第一项顶部滚出视口多少像素」，正负语义反直觉，本文件此前正是因为
    // 传了 `viewport`（= 让最后一项滚出视口一整屏）才跳位置的。
    //
    // 分两步，每步都只用语义明确的值：
    // 1. scrollToItem(lastIndex)：把最后一项带进视口。
    //    列表会 clamp 到最大滚动量，所以短消息时这一步就已经到底了。
    // 2. 若最后一项比视口高（长开场白 / 流式输出中的长回复），第 1 步只会
    //    让它顶部对齐视口顶部，用户看到的是这条消息的开头而不是结尾。
    //    此时 `canScrollForward` 仍为 true，继续 scrollBy 一个视口高度，
    //    直到不能继续向前滚 = 真正到底。
    runCatching {
        scrollToItem(lastIndex)
        val viewportPx = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        if (viewportPx > 0f) {
            // guard 防死循环：正常最多 2-3 次就到底
            var guard = 0
            while (canScrollForward && guard < 8) {
                scrollBy(viewportPx)
                guard++
            }
        }
    }.onFailure {
        runCatching { scrollToItem(lastIndex) }
    }
}
