package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 匹配助手消息开头的思维链块（支持 `<think>`、`<thinking>`、`<thought>`，大小写不敏感，允许前导空白、HTML 注释或轻量状态标签）。
 * 解决酒馆预设/角色卡输出 `<thinking>...</thinking>`（包括 Gemini 等同时具有原生 Reasoning 分片的双轨输出模型）
 * 未拆入 Reasoning 通道、进而污染正文显示正则（如立绘/CG 正则跨行误匹配）的问题。
 */
internal val THINKING_TAG_REGEX = Regex(
    "\\A(?:\\s|<!--[\\s\\S]*?-->)*<(think|thinking|thought)\\b[^>]*>([\\s\\S]*?)(</\\1>|$)",
    RegexOption.IGNORE_CASE,
)

internal val ANYWHERE_THINKING_BLOCK_REGEX = Regex(
    "<(think|thinking|thought)\\b[^>]*>([\\s\\S]*?)(?:</\\1>|\\z)",
    RegexOption.IGNORE_CASE,
)

// 部分供应商或酒馆预设直接在正文返回 <think> / <thinking> / <thought>，由该 transformer 提取为 Reasoning part
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = false,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = true,
        )
    }
}

internal fun List<UIMessage>.transformThinkTags(
    now: Instant,
    generationFinished: Boolean,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) {
        return@map message
    }

    val updatedParts = mutableListOf<UIMessagePart>()
    var changed = false

    for (part in message.parts) {
        when (part) {
            is UIMessagePart.Reasoning -> {
                if (generationFinished && part.finishedAt == null) {
                    updatedParts.add(part.copy(finishedAt = now))
                    changed = true
                } else {
                    updatedParts.add(part)
                }
            }
            is UIMessagePart.Text -> {
                if (part.text.isNotBlank() &&
                    (part.text.contains("<think", ignoreCase = true) || part.text.contains("<thought", ignoreCase = true))
                ) {
                    val match = THINKING_TAG_REGEX.find(part.text) ?: ANYWHERE_THINKING_BLOCK_REGEX.find(part.text)
                    if (match != null) {
                        val openTag = match.groupValues[1].lowercase()
                        val fullMatch = match.value
                        val hasClosingTag = fullMatch.endsWith("</$openTag>", ignoreCase = true)
                        val extracted = match.groupValues[2].trim()
                        if (extracted.isNotEmpty()) {
                            val existingReasoningIdx = updatedParts.indexOfLast { it is UIMessagePart.Reasoning }
                            if (existingReasoningIdx >= 0) {
                                val prev = updatedParts[existingReasoningIdx] as UIMessagePart.Reasoning
                                val mergedText = if (prev.reasoning.contains(extracted)) {
                                    prev.reasoning
                                } else if (prev.reasoning.isBlank()) {
                                    extracted
                                } else {
                                    prev.reasoning.trimEnd() + "\n\n" + extracted
                                }
                                updatedParts[existingReasoningIdx] = prev.copy(
                                    reasoning = mergedText,
                                    finishedAt = if (generationFinished || hasClosingTag) (prev.finishedAt ?: now) else prev.finishedAt,
                                )
                            } else {
                                updatedParts.add(
                                    UIMessagePart.Reasoning(
                                        reasoning = extracted,
                                        createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                        finishedAt = if (generationFinished || hasClosingTag) now else null,
                                    )
                                )
                            }
                        }
                        val stripped = ANYWHERE_THINKING_BLOCK_REGEX.replace(part.text, "").trimStart('\r', '\n')
                        updatedParts.add(part.copy(text = stripped))
                        changed = true
                    } else {
                        updatedParts.add(part)
                    }
                } else {
                    updatedParts.add(part)
                }
            }
            else -> updatedParts.add(part)
        }
    }

    if (changed) message.copy(parts = updatedParts) else message
}
