package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager

/**
 * EJS 提示词模板渲染（ST-Prompt-Template 语义）：
 * 发送前对消息文本中 `<% %>` 模板调用运行时 JS 渲染（异步桥，超时/未就绪透明跳过）。
 * - 开关：settings.tavernEjsRendering（默认开）；
 * - EJS 内 setvar/incvar 等写入经 shim 变量系统持久化（与 MVU 同链路）；
 * - 渲染失败保留原文，绝不破坏 prompt。
 */
object EjsInputTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.settings.tavernEjsRendering) return messages

        // 收集含 `<%` 的文本 part（记录坐标 [messageIndex, partIndex]）
        val targets = mutableListOf<IntArray>()
        messages.forEachIndexed { mi, msg ->
            msg.parts.forEachIndexed { pi, part ->
                if (part is UIMessagePart.Text && part.text.contains("<%")) {
                    targets.add(intArrayOf(mi, pi))
                }
            }
        }
        if (targets.isEmpty()) return messages

        val texts = targets.map { (mi, pi) -> (messages[mi].parts[pi] as UIMessagePart.Text).text }
        val rendered = TavernRuntimeManager.renderEjsBatch(texts) ?: return messages
        if (rendered.size != texts.size) return messages

        // 组装替换映射：messageIndex → (partIndex → newText)
        val repl = HashMap<Int, HashMap<Int, String>>()
        targets.forEachIndexed { i, coord ->
            val mi = coord[0]
            val pi = coord[1]
            if (rendered[i] != texts[i]) {
                repl.getOrPut(mi) { HashMap() }[pi] = rendered[i]
            }
        }
        if (repl.isEmpty()) return messages

        return messages.mapIndexed { mi, msg ->
            val partMap = repl[mi] ?: return@mapIndexed msg
            msg.copy(
                parts = msg.parts.mapIndexed { pi, part ->
                    val t = partMap[pi]
                    if (t != null && part is UIMessagePart.Text) part.copy(text = t) else part
                }
            )
        }
    }
}