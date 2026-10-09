package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation

/**
 * ST Checkpoints（书签）：消息级"检查点会话"链接。
 *
 * 对齐 ST `extra.bookmark_link`：
 * - 源会话消息上指向其检查点副本会话（点击旗帜打开）；
 * - 检查点会话最后一条消息上指向父会话（/checkpoint-exit 返回）。
 *
 * 以 [UIMessageAnnotation.StCheckpointLink] 注解随消息序列化持久化（消息级，作用于全部 swipe 变体）。
 */

/** 消息链接的检查点（无链接时为 null）。 */
val UIMessage.checkpointLink: UIMessageAnnotation.StCheckpointLink?
    get() = annotations.filterIsInstance<UIMessageAnnotation.StCheckpointLink>().firstOrNull()

/** 替换消息上的检查点链接（对齐 ST「已存在则覆盖」语义；幂等）。 */
fun UIMessage.withCheckpointLink(conversationId: String, name: String): UIMessage =
    copy(
        annotations = annotations.filterNot { it is UIMessageAnnotation.StCheckpointLink } +
            UIMessageAnnotation.StCheckpointLink(conversationId = conversationId, name = name)
    )

/** 移除消息上的检查点链接（幂等）。 */
fun UIMessage.withoutCheckpointLink(): UIMessage =
    if (annotations.none { it is UIMessageAnnotation.StCheckpointLink }) this
    else copy(annotations = annotations.filterNot { it is UIMessageAnnotation.StCheckpointLink })

// ---- 会话级读取辅助（供 ChatService / UI 共用）----

/** 读取第 [nodeIndex]（null = 最后一条）条消息上的检查点链接。 */
fun Conversation.checkpointLinkAt(nodeIndex: Int?): UIMessageAnnotation.StCheckpointLink? {
    val index = nodeIndex ?: messageNodes.lastIndex
    if (index !in messageNodes.indices) return null
    return messageNodes[index].messages.firstNotNullOfOrNull { it.checkpointLink }
}

/** 会话内全部检查点链接（node 索引 → 链接）。 */
fun Conversation.checkpointLinks(): List<Pair<Int, UIMessageAnnotation.StCheckpointLink>> {
    return messageNodes.mapIndexedNotNull { index, node ->
        node.messages.firstNotNullOfOrNull { it.checkpointLink }?.let { index to it }
    }
}

/** 检查点会话的父链接（记录父会话 id 与标题；挂在该会话复制的最后一条消息上）。 */
fun Conversation.checkpointParentLink(): UIMessageAnnotation.StCheckpointLink? {
    val parentId = stParentConversationId ?: return null
    return messageNodes
        .asSequence()
        .flatMap { it.messages.asSequence() }
        .firstNotNullOfOrNull { msg ->
            msg.checkpointLink?.takeIf { it.conversationId == parentId.toString() }
        }
}
