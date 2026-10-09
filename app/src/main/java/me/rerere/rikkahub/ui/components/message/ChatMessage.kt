package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Bookmark01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.getStRegexed
import me.rerere.rikkahub.data.st.regex.mergeRegexScripts
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import me.rerere.rikkahub.data.datastore.splitAssistantSegments
import me.rerere.rikkahub.ui.components.frosted.MessageBubbleContainer
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.toMessageTimeString
import me.rerere.rikkahub.utils.urlDecode
import kotlinx.datetime.toJavaLocalDateTime
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onImpersonate: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDeleteSwipe: (() -> Unit)? = null,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleHidden: (() -> Unit)? = null,
    onCreateCheckpoint: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
) {
    val message = node.messages[node.selectIndex]
    val settings = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            // ST /hide：隐藏消息淡化显示（仍保留在会话/UI 中，仅从提示词排除）
            .alpha(if (message.isHidden) 0.55f else 1f),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!message.parts.isEmptyUIMessage()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                ChatMessageAssistantAvatar(
                    message = message,
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                ChatMessageUserAvatar(
                    message = message,
                    avatar = settings.userAvatar,
                    nickname = settings.userNickname,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ProvideTextStyle(textStyle) {
            MessagePartsBlock(
                messageKey = node.id.toString(),
                assistant = assistant,
                role = message.role,
                parts = message.parts,
                annotations = message.annotations,
                loading = loading,
                model = model,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
            )

            message.translation?.let { translation ->
                CollapsibleTranslationText(
                    content = translation,
                    onClickCitation = {}
                )
            }
        }

        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }

        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    onImpersonate = onImpersonate,
                    node = node,
                    onUpdate = onUpdate,
                    isLastMessage = lastMessage,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
        )

        if (settings.showDateTimeInMessage && !message.parts.isEmptyUIMessage()) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(message = message)
        }

    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onDeleteSwipe = onDeleteSwipe,
            onShare = onShare,
            onFork = onFork,
            onCreateCheckpoint = onCreateCheckpoint,
            model = model,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onToggleHidden = onToggleHidden,
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
    messageKey: String,
) {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val stRegexScripts = remember(settings.regexScripts, assistant) { mergeRegexScripts(settings.regexScripts, assistant) }
    val partsState by rememberUpdatedState(parts)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    groupedParts.fastForEach { block ->
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    val isReasoningOnlyBlock = block.steps.fastAll { it is ThinkingStep.ReasoningStep }
                    ChainOfThought(
                        modifier = Modifier.animateContentSize(),
                        steps = block.steps,
                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                        cardColors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        // [v222 R4/R6] 只改「气泡容器层」：样式三选一（默认/毛玻璃/纯色）+ 磨砂玻璃 +
                        // 助手气泡贴合内容 + 分段多气泡。
                        // 红线：不碰 StTextContent / MessageHtmlBlock 内部渲染、尺寸换算与滚动行为
                        // （1 CSS px == 1 dp、--rikka-fg、PanelScrollBridge 全部原样）。
                        // [v233] 助手级消息样式覆盖：assistant.messageStyleOverride 非 null 时覆盖分块字段
                        val msgStyle = me.rerere.rikkahub.data.datastore.resolveMessageStyle(
                            settings.messageStyle,
                            assistant?.messageStyleOverride,
                        )
                        // [v227 D5] 容器分支判据统一走 MessageBubbleContainer.bubbleContainerKind，
                        // 本文件不再自行算 useStyledBubble（两处判据曾不一致 -> 真机缺陷）。
                        // R6-②：分段显示为多个气泡。只在「数据 -> 气泡」边界拆，
                        // 且**面板消息（HTML）永不参与拆分**（用与 StTextContent 同一个判据）。
                        val segments = if (
                            role == MessageRole.ASSISTANT &&
                            msgStyle.splitSegmentsAsBubbles &&
                            !looksLikeInteractiveHtml(part.text)
                        ) {
                            splitAssistantSegments(part.text)
                        } else {
                            listOf(part.text)
                        }
                        // [v226 D1] 分段渲染：N 段必须**垂直排列**。
                        // 旧实现把 N 段直接铺进 SelectionContainer —— SelectionContainer 内部是
                        // 「所有子级叠放在同一位置」的布局（它只为单条文本设计），于是多段互相叠画
                        // （真机：后段压前段，「我是2」盖在「我是1」上）。这里显式用 Column 串起来。
                        // 单段（用户消息 / 未开分段开关）仍走原路径，零行为变化。
                        val emitSegments: @Composable () -> Unit = {
                            segments.forEachIndexed { segIndex, segText ->
                                val segKey = if (segIndex == 0) messageKey else messageKey + "#" + segIndex
                                key(segKey) {
                                    // [v226 D2] 判定面板 HTML 必须用「渲染后」的文本：
                                    // StTextContent 是按 regex 替换**之后**的文本决定走 WebView 面板还是
                                    // Markdown，旧实现却用原始 part.text 判定 wrapContent，两者不一致 ——
                                    // 当显示正则把预设开场白替换成 HTML 面板时，气泡仍走收缩路径，而面板是
                                    // AndroidView（intrinsics 恒为 0）→ width(IntrinsicSize.Max) 把气泡压成
                                    // 0 宽，面板又是「整高渲染」→ 真机表现为「很细很长的一条竖线」。
                                    val segRendered = rememberStRegexedText(
                                        segText,
                                        stRegexScripts,
                                        assistant,
                                        if (role == MessageRole.USER) AssistantAffectScope.USER else AssistantAffectScope.ASSISTANT,
                                        if (role == MessageRole.USER) RegexPlacement.USER_INPUT else RegexPlacement.AI_OUTPUT,
                                    )
                                    val segIsPanelHtml = remember(segRendered) {
                                        looksLikeInteractiveHtml(segRendered)
                                    }
                                    // [v229b 主人反馈] 有时多出一条「空白的小气泡」：显示正则把某段内容清空，
                                    // 或分段切出了纯空白段 —— 渲染后为 blank 的段仍然画了带背景的气泡。
                                    // 修复 = 渲染后为 blank 的段（多段时）不再包气泡壳，只渲染空内容
                                    //（MarkdownBlock 对空文本高度为 0，视觉上等于该段不存在）；
                                    // 单段全空保持原样，避免整楼被正则清空后楼层彻底消失。
                                    val segIsEmpty = segRendered.isBlank() && segments.size > 1
                                    // [v229 P0-1] 渲染/面板气泡恢复「正常气泡模式」：
                                    //   满宽 + **零测量 + 零宽度反馈**。主人已拍板放弃「渲染气泡贴合内容」
                                    //   （v229 开场提示词 §1.2 A / §35.1）：面板消息走 AndroidView（intrinsics 恒为 0），
                                    //   任何「测量 -> Modifier.width -> 再测量」都是闭环反馈，结构上不可能稳定。
                                    val segBubble: @Composable () -> Unit = {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            StTextContent(
                                                messageKey = segKey,
                                                content = segRendered,
                                                loading = loading,
                                                onClickCitation = handleClickCitation,
                                            )
                                        }
                                    }
                                    // [v227 D5] 气泡容器唯一化：聊天页与消息样式预览区共用
                                    // MessageBubbleContainer（判据 bubbleContainerKind 同源），
                                    // 消除「预览与真实气泡不一致」的结构性来源。
                                    // [v229 P0-1] 面板气泡宽度不再由任何测量结果决定 -> 恒定满宽（红线 3）。
                                    val bubbleModifier = Modifier
                                    if (!segIsEmpty) MessageBubbleContainer(
                                        setting = msgStyle,
                                        isUser = role == MessageRole.USER,
                                        bubbleOpacity = settings.displaySetting.bubbleOpacity,
                                        showAssistantBubble = settings.displaySetting.showAssistantBubble,
                                        modifier = bubbleModifier.animateContentSize(),
                                        // 面板消息（WebView）不实现 intrinsics，禁止走收缩路径
                                        wrapContent = msgStyle.assistantBubbleWrapContent && !segIsPanelHtml,
                                        onClick = if (role == MessageRole.USER) {
                                            { onUserMessageClick?.invoke() }
                                        } else {
                                            null
                                        },
                                        bubbleContent = segBubble,
                                        bareContent = {
                                            StTextContent(
                                                messageKey = segKey,
                                                content = segRendered,
                                                loading = loading,
                                                onClickCitation = handleClickCitation,
                                                modifier = Modifier
                                                    .animateContentSize()
                                            )
                                        },
                                    )
                                } // key(segKey)
                            } // forEachIndexed
                        } // emitSegments
                        val textContent = @Composable {
                            if (segments.size > 1) {
                                // 每段一个独立气泡，段间距 8dp（v226 P1 建议形态 A）。
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    emitSegments()
                                }
                            } else {
                                emitSegments()
                            }
                        }

                        // 流式生成期间不启用 SelectionContainer（原注释保留，见 git 历史）。
                        if (loading) {
                            textContent()
                        } else {
                            SelectionContainer {
                                textContent()
                            }
                        }
                    }

                    is UIMessagePart.Video -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(HugeIcons.Video01, null)
                            }
                        }
                    }

                    is UIMessagePart.Audio -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.MusicNote03,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (part.mime) {
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.docx),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        "application/pdf" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.pdf),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        else -> {
                                            Icon(
                                                imageVector = HugeIcons.File02,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = part.fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            var expand by remember { mutableStateOf(false) }
            if (expand) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.extendColors.gray8.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    color = contentColor.copy(alpha = 0.2f),
                                    size = Size(width = 10f, height = size.height),
                                )
                            }
                            .padding(start = 16.dp)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }

                                is UIMessageAnnotation.CharacterCardData -> Unit
                                is UIMessageAnnotation.ExampleMessage -> Unit
                                is UIMessageAnnotation.Hidden -> {
                                    Text(
                                        text = stringResource(R.string.st_message_hidden_marker),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }

                                is UIMessageAnnotation.StCheckpointLink -> {
                                    val navController = LocalNavController.current
                                    TextButton(
                                        onClick = {
                                            runCatching { Uuid.parse(annotation.conversationId) }
                                                .getOrNull()
                                                ?.let { checkpointId ->
                                                    navigateToChatPage(navController, chatId = checkpointId)
                                                }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = HugeIcons.Bookmark01,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.st_message_checkpoint_marker,
                                                annotation.name,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    expand = !expand
                }
            ) {
                Text(stringResource(R.string.citations_count, annotations.size))
            }
        }
    }
}
