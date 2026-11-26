package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.rikkahub.data.st.preview.ActiveProcessorBadge
import me.rerere.rikkahub.data.st.preview.PromptPreviewBlock
import me.rerere.rikkahub.data.st.preview.PromptPreviewSnapshot
import me.rerere.rikkahub.data.st.preview.PromptSlotOrigin
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.theme.AppSpacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 输入框下方「思考强度」旁边的入口按钮，样式与 ReasoningButton 保持一致。
 */
@Composable
fun PromptPreviewButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleSurface(
        checked = false,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Box(
                modifier = Modifier.size(AppSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Layers01,
                    contentDescription = "上游消息结构预览",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 约 4/5 屏幕高度的深底浅色卡片面板：
 * - 顶部：已启用消息处理器徽章（正则/酒馆脚本/EJS/MVU/SPreset/第三方插件）
 * - 中间：可拖拽调序的上游消息结构块，右侧三横杠长按拖拽，拖入聊天记录自动展开近期纯正文消息
 * - 底部：「取消」「保存」按钮，点保存立即持久生效
 */
@Composable
fun PromptStructurePreviewDialog(
    snapshot: PromptPreviewSnapshot,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (draftBlocks: List<PromptPreviewBlock>, dirtyKeys: Set<String>) -> Unit,
) {
    var draftBlocks by remember(snapshot) { mutableStateOf(snapshot.blocks) }
    var dirtyKeys by remember(snapshot) { mutableStateOf(emptySet<String>()) }
    var isChatExpanded by remember(snapshot) { mutableStateOf(false) }
    var showProcessorDetails by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    fun expandChatHistoryInPlace() {
        if (isChatExpanded) return
        val headerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
        val footerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
        if (headerIdx < 0 || footerIdx <= headerIdx) return
        val existingEmbedded = draftBlocks.subList(headerIdx + 1, footerIdx)
        val mergedInside = interleaveEmbeddedDepthWithMessages(
            embeddedDepthBlocks = existingEmbedded,
            chatMessages = snapshot.chatMessagesForExpansion,
        )
        val updatedHeader = draftBlocks[headerIdx].let { h ->
            val origin = h.origin as PromptSlotOrigin.ChatRegionHeader
            h.copy(
                origin = origin.copy(expanded = true),
                title = "聊天记录区（已展开近期 ${(snapshot.chatMessagesForExpansion.size - 1).coerceAtLeast(0)} 条正文 + 待发标尺，点击收起）",
            )
        }
        draftBlocks = buildList {
            addAll(draftBlocks.subList(0, headerIdx))
            add(updatedHeader)
            addAll(mergedInside)
            addAll(draftBlocks.subList(footerIdx, draftBlocks.size))
        }
        isChatExpanded = true
    }

    fun collapseChatHistoryInPlace() {
        if (!isChatExpanded) return
        val headerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
        val footerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
        if (headerIdx < 0 || footerIdx <= headerIdx) return
        // 收起前先为所有在展开态中被拖动过的内嵌条目回写当前精确 depth，防止收起后丢失精调位置
        val keptEmbedded = buildList {
            for (idx in (headerIdx + 1) until footerIdx) {
                val blk = draftBlocks[idx]
                if (blk.origin is PromptSlotOrigin.ChatMessageAnchor) continue
                val updatedDepth = if (blk.key in dirtyKeys) {
                    me.rerere.rikkahub.data.st.preview.PromptOrderSyncWriter.computeDepthFromExpandedChat(draftBlocks, idx)
                } else {
                    blk.injectDepth
                }
                add(blk.copy(injectDepth = updatedDepth, isInsideChatHistory = true))
            }
        }
        val updatedHeader = draftBlocks[headerIdx].let { h ->
            val origin = h.origin as PromptSlotOrigin.ChatRegionHeader
            h.copy(
                origin = origin.copy(expanded = false, embeddedDepthCount = keptEmbedded.size),
                title = "聊天记录区（共 ${origin.totalMessageCount} 条，点击展开/收起近期正文）",
            )
        }
        draftBlocks = buildList {
            addAll(draftBlocks.subList(0, headerIdx))
            add(updatedHeader)
            addAll(keptEmbedded)
            addAll(draftBlocks.subList(footerIdx, draftBlocks.size))
        }
        isChatExpanded = false
    }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (isSaving) return@rememberReorderableLazyListState
        val outcome = applyPreviewDragMove(
            currentBlocks = draftBlocks,
            dirtyKeys = dirtyKeys,
            isChatExpanded = isChatExpanded,
            chatMessagesForExpansion = snapshot.chatMessagesForExpansion,
            fromIndex = from.index,
            toIndex = to.index,
        ) ?: return@rememberReorderableLazyListState
        draftBlocks = outcome.blocks
        dirtyKeys = outcome.dirtyKeys
        isChatExpanded = outcome.isChatExpanded
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (!isSaving) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.80f)
                    .clickable(
                        enabled = true,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "上游消息结构预览",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = snapshot.activePresetGroup?.let { "当前预设：$it" } ?: "当前未启用 ST 预设",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onDismiss, enabled = !isSaving) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = "关闭",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // 顶部已启用消息处理器徽章栏
                    ActiveProcessorsBar(
                        processors = snapshot.activeProcessors,
                        expanded = showProcessorDetails,
                        onToggleExpand = { showProcessorDetails = !showProcessorDetails },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 中间结构化列表（仅右侧三横杠可长按拖拽）
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(draftBlocks, key = { it.key }) { block ->
                            ReorderableItem(state = reorderState, key = block.key) { isDragging ->
                                when (val origin = block.origin) {
                                    is PromptSlotOrigin.ChatRegionHeader -> {
                                        ChatRegionHeaderRow(
                                            block = block,
                                            expanded = isChatExpanded,
                                            onClick = {
                                                if (!isSaving) {
                                                    if (isChatExpanded) collapseChatHistoryInPlace()
                                                    else expandChatHistoryInPlace()
                                                }
                                            },
                                        )
                                    }
                                    is PromptSlotOrigin.ChatRegionFooter -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "─── 聊天记录区结束 ───",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                    else -> {
                                        val handleMod = if (block.draggable) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                },
                                                onDragStopped = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                },
                                            )
                                        } else {
                                            Modifier
                                        }
                                        PromptPreviewRowCard(
                                            block = block,
                                            isDragging = isDragging,
                                            dragHandleModifier = handleMod,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 底部「取消」「保存」按钮栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (dirtyKeys.isNotEmpty()) {
                                "已修改 ${dirtyKeys.size} 项 · 保存将同步更新共享配置"
                            } else {
                                "长按右侧三横杠调整发送顺序与深度"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isSaving,
                        ) {
                            Text("取消")
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onSave(draftBlocks, dirtyKeys) },
                            enabled = dirtyKeys.isNotEmpty() && !isSaving,
                        ) {
                            Text(if (isSaving) "保存中…" else "保存")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveProcessorsBar(
    processors: List<ActiveProcessorBadge>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (processors.isEmpty()) {
                    "已启用消息处理器：无（纯原始发送）"
                } else {
                    "已启用消息处理器 (${processors.size}) · 发送时可能被以下链路修改"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (processors.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (processors.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                processors.forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (badge.isBlackBoxJsHook) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = buildString {
                                append(badge.category).append("·").append(badge.name)
                                if (badge.isBlackBoxJsHook) append(" ⚡")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (badge.isBlackBoxJsHook) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                // [BUGFIX] 展开区此前既没有高度上限也没有滚动容器：
                // 处理器一多（30 条时约 1400dp）会把外层 Column 撑爆，
                // 把下面 weight(1f) 的结构列表挤成 0 高，而它自己又不可滑动 ——
                // 表现就是「展开后整块卡住、怎么划都不动」。
                // 现在给它一个可滚动的高度区间：折叠时不会占满，展开时内部自己滚。
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        processors.forEach { badge ->
                            Text(
                                text = "• [${badge.category}] ${badge.name}：${badge.detail.ifBlank { "已启用" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRegionHeaderRow(
    block: PromptPreviewBlock,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (block.plainBodyPreview.isNotBlank()) {
                    Text(
                        text = block.plainBodyPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PromptPreviewRowCard(
    block: PromptPreviewBlock,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
) {
    val isChatAnchor = block.origin is PromptSlotOrigin.ChatMessageAnchor
    val cardColor = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        isChatAnchor -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        tonalElevation = if (isDragging) 6.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (block.inactiveReason != null) 0.62f else 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = block.categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (block.inactiveReason != null) {
                    Text(
                        text = block.inactiveReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (block.plainBodyPreview.isNotBlank()) {
                    Text(
                        text = block.plainBodyPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isChatAnchor) 4 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (block.draggable) {
                Box(
                    modifier = dragHandleModifier
                        .size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HugeIcons.Menu03,
                        contentDescription = "长按拖动调整顺序",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 将已有的内嵌 AT_DEPTH 条目按其 injectDepth 精准插回展开后的近期聊天消息序列中。
 */
internal fun interleaveEmbeddedDepthWithMessages(
    embeddedDepthBlocks: List<PromptPreviewBlock>,
    chatMessages: List<PromptPreviewBlock>,
): List<PromptPreviewBlock> {
    if (embeddedDepthBlocks.isEmpty()) return chatMessages
    val n = chatMessages.size
    // 按截断后的插入槽位分组（0..n），同深度按 system -> user -> assistant 角色序与 priority 升序排列
    fun roleRank(role: me.rerere.ai.core.MessageRole): Int = when (role) {
        me.rerere.ai.core.MessageRole.SYSTEM -> 0
        me.rerere.ai.core.MessageRole.USER -> 1
        me.rerere.ai.core.MessageRole.ASSISTANT -> 2
        else -> 3
    }
    val bySlot = HashMap<Int, MutableList<PromptPreviewBlock>>()
    val sortedEntries = embeddedDepthBlocks.sortedWith(
        compareByDescending<PromptPreviewBlock> { it.injectDepth ?: 0 }
            .thenBy { roleRank(it.role) }
            .thenBy { it.priority ?: 0 }
    )
    for (entry in sortedEntries) {
        val depth = (entry.injectDepth ?: 0).coerceAtLeast(0)
        val slot = (n - depth).coerceIn(0, n)
        bySlot.getOrPut(slot) { mutableListOf() }.add(entry)
    }
    return buildList(n + embeddedDepthBlocks.size) {
        for (i in 0..n) {
            bySlot[i]?.let { addAll(it) }
            if (i < n) add(chatMessages[i])
        }
    }
}

internal data class PreviewDragMoveOutcome(
    val blocks: List<PromptPreviewBlock>,
    val dirtyKeys: Set<String>,
    val isChatExpanded: Boolean,
)

/**
 * 预览列表拖拽状态转移纯函数（供 UI 与 JVM 单元测试共用）：
 * 1. 仅允许 `draggable == true` 的条目移动；
 * 2. 禁止将内置 `PresetMarker` 拖入 `ChatRegionHeader..ChatRegionFooter` 聊天记录区内部，
 *    避免 UI 位置与真实发送语义错位；
 * 3. 将外部可深度注入条目拖入折叠态聊天记录区时，自动原位展开近期纯正文消息标尺（设计文档 §6.2）。
 */
internal fun applyPreviewDragMove(
    currentBlocks: List<PromptPreviewBlock>,
    dirtyKeys: Set<String>,
    isChatExpanded: Boolean,
    chatMessagesForExpansion: List<PromptPreviewBlock>,
    fromIndex: Int,
    toIndex: Int,
): PreviewDragMoveOutcome? {
    val moved = currentBlocks.getOrNull(fromIndex) ?: return null
    if (!moved.draggable) return null
    val boundedTo = toIndex.coerceIn(0, currentBlocks.lastIndex)
    if (fromIndex == boundedTo) return null

    val nextList = currentBlocks.toMutableList().apply {
        add(boundedTo, removeAt(fromIndex))
    }
    val nextHeaderIdx = nextList.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
    val nextFooterIdx = nextList.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
    val landedInsideChat = nextHeaderIdx >= 0 && nextFooterIdx > nextHeaderIdx &&
        boundedTo in (nextHeaderIdx + 1) until nextFooterIdx

    // PresetMarker（main/charDescription/charPersonality/scenario/dialogueExamples/jailbreak）
    // 绝不滞留在聊天记录区内部：按拖动方向自动跨过整个 ChatRegionHeader..ChatRegionFooter，
    // 既杜绝 UI 与发包语义错位，又支持单手把 main/jailbreak 从聊天区上方顺畅拖到下方（或反向）。
    if (landedInsideChat && moved.origin is PromptSlotOrigin.PresetMarker) {
        val withoutMoved = currentBlocks.toMutableList().apply { removeAt(fromIndex) }
        val cleanHeaderIdx = withoutMoved.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
        val cleanFooterIdx = withoutMoved.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
        val leapTarget = if (fromIndex < boundedTo) {
            (cleanFooterIdx + 1).coerceIn(0, withoutMoved.size)
        } else {
            cleanHeaderIdx.coerceIn(0, withoutMoved.size)
        }
        withoutMoved.add(leapTarget, moved)
        return PreviewDragMoveOutcome(
            blocks = withoutMoved,
            dirtyKeys = dirtyKeys + moved.key,
            isChatExpanded = isChatExpanded,
        )
    }

    val nextDirty = dirtyKeys + moved.key
    if (landedInsideChat && !isChatExpanded && chatMessagesForExpansion.isNotEmpty()) {
        val existingEmbedded = nextList.subList(nextHeaderIdx + 1, nextFooterIdx)
        val mergedInside = interleaveEmbeddedDepthWithMessages(
            embeddedDepthBlocks = existingEmbedded,
            chatMessages = chatMessagesForExpansion,
        )
        val updatedHeader = nextList[nextHeaderIdx].let { h ->
            val origin = h.origin as PromptSlotOrigin.ChatRegionHeader
            h.copy(
                origin = origin.copy(expanded = true, embeddedDepthCount = existingEmbedded.size),
                title = "聊天记录区（已展开近期 ${(chatMessagesForExpansion.size - 1).coerceAtLeast(0)} 条正文 + 待发标尺，点击收起）",
            )
        }
        val expandedList = buildList {
            addAll(nextList.subList(0, nextHeaderIdx))
            add(updatedHeader)
            addAll(mergedInside)
            addAll(nextList.subList(nextFooterIdx, nextList.size))
        }
        return PreviewDragMoveOutcome(
            blocks = expandedList,
            dirtyKeys = nextDirty,
            isChatExpanded = true,
        )
    }

    return PreviewDragMoveOutcome(
        blocks = nextList,
        dirtyKeys = nextDirty,
        isChatExpanded = isChatExpanded,
    )
}
