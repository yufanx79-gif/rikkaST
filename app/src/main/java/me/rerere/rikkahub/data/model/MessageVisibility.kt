package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation

/**
 * ST /hide（hideChatMessageRange）支持：消息级隐藏标记。
 *
 * 对齐 ST 语义：隐藏（`is_system = true`）的消息不进入提示词（Prompt），
 * 但仍保留在会话与 UI 中（淡化显示），可用 /unhide 或消息菜单恢复。
 * 标记以 [UIMessageAnnotation.Hidden] 注解形式随消息序列化持久化（每个 swipe 变体独立）。
 */

/** 消息是否已隐藏（从提示词中排除）。 */
val UIMessage.isHidden: Boolean
    get() = annotations.any { it is UIMessageAnnotation.Hidden }

/** 打上隐藏标记（幂等）。 */
fun UIMessage.withHidden(): UIMessage =
    if (isHidden) this else copy(annotations = annotations + UIMessageAnnotation.Hidden)

/** 移除隐藏标记（幂等）。 */
fun UIMessage.withoutHidden(): UIMessage =
    if (!isHidden) this else copy(annotations = annotations.filterNot { it is UIMessageAnnotation.Hidden })
