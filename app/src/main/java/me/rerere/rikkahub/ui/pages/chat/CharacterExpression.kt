package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.isInjectedBlock
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.st.expressions.ExpressionClassifier
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.st.expressions.ExpressionStore
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.expressions.SpriteRepository

/**
 * [v240 W3] Character Expressions（表情立绘）宿主。
 *
 * 职责：生成完成后对「最后一条助手消息」做一次表情分类，并把结果写进 [ExpressionStore]。
 *
 * 对齐 SillyTavern `extensions/expressions/index.js`：
 * - 分类只发生在生成完成之后（官方 llm 分支在流式期间节流 10s，rikkaST 只在完成点分类，
 *   天然不会每 token 调用；[ExpressionStore.shouldThrottleClassify] 额外防护连续重生成）；
 * - 结果按 messageId 缓存（官方 `lastMessage` 去重语义），同一条消息不重复调用模型；
 * - 该角色「没有立绘」→ 不调用分类、不显示（官方 filterAvailable 语义）；
 * - 分类失败/超时 → 回退兜底标签（官方 `DEFAULT_FALLBACK_EXPRESSION = joy`）；
 * - 分类结果必须是该角色实际拥有的标签，否则回退兜底标签；两者都不可用则不显示。
 */
@Composable
fun CharacterExpressionHost(vm: ChatVM, setting: Settings, conversation: Conversation) {
    val context = LocalContext.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)

    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { doneConversationId ->
            val s = updatedSetting
            val conv = currentConversation
            if (doneConversationId != conv.id) return@collect
            if (!s.expressionEnabled || s.expressionClassifier == ExpressionClassifier.NONE) return@collect

            val assistant = s.getAssistantById(conv.assistantId) ?: s.getCurrentAssistant()

            // 该角色无立绘 → 不调用、不显示（避免无意义的模型调用）
            val availableLabels = withContext(Dispatchers.IO) {
                SpriteRepository.listLabels(context, assistant.id)
            }
            if (availableLabels.isEmpty()) {
                TavernRuntimeManager.appendLog("info", "[expression] skip: no sprites for ${assistant.id}")
                return@collect
            }

            val message = conv.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT && !it.isInjectedBlock() }
                ?: return@collect
            val text = message.parts.filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }
                .trim()
            if (text.isEmpty()) return@collect

            val messageId = message.id.toString()
            val conversationKey = conv.id.toString()

            // 1. messageId 缓存命中 → 直接复用（官方同一条消息不重复分类）
            val cached = ExpressionStore.cachedLabel(messageId)
            val resolved = if (cached != null) {
                cached.takeIf { it in availableLabels } ?: return@collect
            } else {
                // 2. 节流：同一对话两次分类间隔 < 10s 时跳过本次（旧立绘保持显示）
                if (ExpressionStore.shouldThrottleClassify(conversationKey)) return@collect
                ExpressionStore.markClassified(conversationKey)

                // 分类异常/超时 → null；解析不出标签 → 回退兜底（官方 DEFAULT_FALLBACK_EXPRESSION）
                val classified = runCatching {
                    vm.classifyExpression(assistant, text, availableLabels)
                }.getOrNull()
                val label = ExpressionLabels.resolveDisplayLabel(
                    classified = classified,
                    fallback = s.expressionFallbackLabel,
                    availableLabels = availableLabels,
                ) ?: return@collect
                ExpressionStore.putLabel(messageId, label)
                label
            }

            ExpressionStore.select(conversationKey, messageId, resolved)
            TavernRuntimeManager.appendLog(
                "info",
                "[expression] select conversation=$conversationKey message=$messageId label=$resolved",
            )
        }
    }
}

/**
 * [v240 W3] 立绘叠加层：按 [ExpressionStore] 当前选择显示该助手的立绘。
 *
 * - 默认关闭（[Settings.expressionEnabled] = false，设置页打开前不渲染任何东西）；
 * - 表情切换用 [Crossfade] 淡入淡出（对齐官方 CSS 过渡语义）；
 * - 无指针输入修饰符：不拦截聊天列表的点击/滚动。
 */
@Composable
fun CharacterExpressionOverlay(
    setting: Settings,
    conversation: Conversation,
    modifier: Modifier = Modifier,
) {
    if (!setting.expressionEnabled) return

    val selections by ExpressionStore.selections.collectAsStateWithLifecycle()
    val selection = selections[conversation.id.toString()] ?: return
    val assistant = setting.getAssistantById(conversation.assistantId) ?: return
    val context = LocalContext.current
    val file = remember(selection.label, assistant.id) {
        SpriteRepository.resolveFile(context, assistant.id, selection.label)
    } ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 6.dp, end = 4.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Crossfade(
            targetState = file,
            animationSpec = tween(durationMillis = 260),
            label = "character-expression",
        ) { target ->
            AsyncImage(
                model = target,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight(0.58f)
                    .fillMaxWidth(0.55f),
            )
        }
    }
}
