package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.UpdateVariableParser

/**
 * 输出变压器：MVU `<UpdateVariable>` 协议闭环。
 *
 * - `onGenerationFinish`：解析全部 assistant 消息中的块 → 应用 JSON Patch 到
 *   会话变量树（TavernVariableStore，chat 作用域）→ 从文本中剥离成功块；
 *   解析失败/不完整的块原样保留（不吞内容）。
 * - `visualTransform`：流式期间只剥离**最后一条**消息中的**完整**块（性能 + 防闪现），
 *   变量更新一律等生成结束才应用（与 MVU/ST 语义一致）。
 */
object UpdateVariableOutputTransformer : OutputMessageTransformer {
    private val completeBlockRegex = Regex(
        """<UpdateVariable>[\s\S]*?</UpdateVariable>""",
        RegexOption.IGNORE_CASE,
    )

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val conversationId = ctx.conversationId?.toString() ?: return messages
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            var changed = false
            val newParts = message.parts.map { part ->
                if (part !is UIMessagePart.Text) return@map part
                if (!part.text.contains("UpdateVariable", ignoreCase = true)) return@map part
                val result = UpdateVariableParser.apply(
                    part.text,
                    TavernVariableStore.getChat(conversationId),
                )
                if (!result.applied) return@map part
                // MVU 运行时激活时由 MVU 自身负责变量应用（消息作用域），避免双重应用
                if (!TavernRuntimeManager.isMvuActive) {
                    TavernVariableStore.replaceChat(conversationId, result.variables)
                }
                changed = true
                part.copy(text = result.text)
            }
            if (changed) message.copy(parts = newParts) else message
        }
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (messages.isEmpty()) return messages
        val lastIndex = messages.lastIndex
        return messages.mapIndexed { index, message ->
            if (index != lastIndex) return@mapIndexed message
            if (message.role != MessageRole.ASSISTANT) return@mapIndexed message
            var changed = false
            val newParts = message.parts.map { part ->
                if (part !is UIMessagePart.Text) return@map part
                if (!part.text.contains("UpdateVariable", ignoreCase = true)) return@map part
                val stripped = completeBlockRegex.replace(part.text) { "" }
                    .replace(Regex("\\n{3,}"), "\n\n")
                if (stripped == part.text) return@map part
                changed = true
                part.copy(text = stripped)
            }
            if (changed) message.copy(parts = newParts) else message
        }
    }
}