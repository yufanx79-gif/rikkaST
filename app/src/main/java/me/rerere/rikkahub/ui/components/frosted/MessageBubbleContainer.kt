package me.rerere.rikkahub.ui.components.frosted

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import me.rerere.rikkahub.data.datastore.MessageStyleSetting

/**
 * [v227 D5] 气泡容器三态（纯枚举，可单测）。
 *
 * ## 为什么要有这个文件
 * 「消息样式」页的**预览区**此前直接调 [FrostedBubbleSurface]，而聊天页（ChatMessage.kt）是
 * 「样式三选一 == DEFAULT 时走旧 M3 Surface + displaySetting.bubbleOpacity，否则走磨砂容器」的两分支。
 * 两者不一致 -> 预览与真实气泡观感不同（真机反馈：「预览框里只有用户消息和助手消息，没有气泡框」）。
 *
 * 现在把这条分支**唯一化**到 [MessageBubbleContainer]，聊天页与预览区共用同一份实参渲染，
 * 从结构上保证「预览 == 聊天页」（主人 P3 建议：1:1 复刻）。
 *
 * ## 三态
 * - [STYLED]：`style != DEFAULT` -> [FrostedBubbleSurface]（毛玻璃 / 纯色，含可调圆角 / 底色 / 边框 / 文字色）。
 * - [LEGACY_SURFACE]：`style == DEFAULT` -> v221 旧路径（固定 16dp 圆角 + `displaySetting.bubbleOpacity`）。
 * - [BARE]：助手气泡关闭（`showAssistantBubble == false`）-> 完全不画容器，纯文本。
 *
 * 红线（与 v222/v225/v226 一致）：本组件**只画容器**，不碰内容渲染 / 尺寸换算 / 滚动行为；
 * [STYLED] 分支原样委托给 [FrostedBubbleSurface]，不新增任何 clip / 滚动容器。
 */
internal enum class BubbleContainerKind {
    STYLED,
    LEGACY_SURFACE,
    BARE,
}

/**
 * [v227 D5] 纯函数：决定某个角色 / 当前样式下用哪种气泡容器。
 *
 * 判据与 ChatMessage.kt 的 `useStyledBubble` / `showAssistantBubble` 完全同源（v226 D2 的教训：
 * 两处判据不一致 -> 真机缺陷）。**任何新调用方都必须走这个函数**，不许再手写 if。
 */
internal fun bubbleContainerKind(
    style: MessageBubbleStyle,
    isUser: Boolean,
    showAssistantBubble: Boolean,
): BubbleContainerKind = when {
    style != MessageBubbleStyle.DEFAULT -> BubbleContainerKind.STYLED
    isUser -> BubbleContainerKind.LEGACY_SURFACE
    showAssistantBubble -> BubbleContainerKind.LEGACY_SURFACE
    else -> BubbleContainerKind.BARE
}

/**
 * [v227 D5 复审修复] 「贴合内容」只作用于**助手**气泡 —— 与 v226 行为严格等价。
 *
 * v226 的调用点对**用户**气泡从不传 `wrapContent`（默认 false）；v227 把 user/assistant 合并进同一个
 * 容器后，调用点统一传了 `wrapContent`，会让用户气泡在「助手气泡贴合内容」开关打开时也去收缩宽度
 * （**行为回归**，由只读对抗式复审 `notes/review-v227b-20261007.md` 发现）。
 * 这里把不变量收敛到容器内部：**非助手一律不贴合**，调用方传什么都不例外。
 */
internal fun bubbleWrapContent(requested: Boolean, isUser: Boolean): Boolean = requested && !isUser

/** DEFAULT 样式下旧路径的圆角（v221 起固定 16dp，不属于可调参数）。 */
private val LegacyBubbleCorner = 16.dp

/**
 * [v227 D5] 聊天页 / 消息样式预览区**共用**的气泡容器。
 *
 * @param setting 消息样式（全局唯一）。
 * @param isUser 用户气泡（true）还是助手气泡（false）。
 * @param bubbleOpacity `displaySetting.bubbleOpacity`，只作用于 [BubbleContainerKind.LEGACY_SURFACE]。
 * @param showAssistantBubble `displaySetting.showAssistantBubble`，只作用于助手 + DEFAULT。
 * @param wrapContent 助手气泡贴合内容（仅 [STYLED] 分支生效）。
 * @param onClick 用户气泡点击（旧路径下会切换成可点击 Surface 重载，与 v221 行为一致）。
 * @param bareContent [BARE] 时的内容（聊天页需要它**不带气泡 padding**，故单独给出；
 *   null = 直接用 [bubbleContent]）。
 * @param bubbleContent 气泡内的内容（调用方负责 padding）。**故意放最后一个参数**，这样
 *   `MessageBubbleContainer(...) { ... }` 的尾随 lambda 绑定的就是气泡内容（不会误绑到 bareContent）。
 */
@Composable
fun MessageBubbleContainer(
    setting: MessageStyleSetting,
    isUser: Boolean,
    bubbleOpacity: Float,
    showAssistantBubble: Boolean,
    modifier: Modifier = Modifier,
    wrapContent: Boolean = false,
    onClick: (() -> Unit)? = null,
    bareContent: (@Composable () -> Unit)? = null,
    bubbleContent: @Composable () -> Unit,
) {
    when (bubbleContainerKind(setting.style, isUser, showAssistantBubble)) {
        BubbleContainerKind.STYLED -> {
            FrostedBubbleSurface(
                setting = setting,
                isUser = isUser,
                style = setting.style,
                modifier = modifier,
                // [v227 D5 复审修复] 只让助手气泡走贴合路径（bubbleWrapContent 的不变量）
                wrapContent = bubbleWrapContent(wrapContent, isUser),
                onClick = onClick,
            ) {
                bubbleContent()
            }
        }

        BubbleContainerKind.LEGACY_SURFACE -> {
            val color = (
                if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
                ).copy(alpha = bubbleOpacity)
            val shape = RoundedCornerShape(LegacyBubbleCorner)
            if (isUser) {
                Surface(
                    modifier = modifier,
                    shape = shape,
                    color = color,
                    onClick = { onClick?.invoke() },
                ) {
                    bubbleContent()
                }
            } else {
                Surface(
                    modifier = modifier,
                    shape = shape,
                    color = color,
                ) {
                    bubbleContent()
                }
            }
        }

        BubbleContainerKind.BARE -> (bareContent ?: bubbleContent)()
    }
}
