package me.rerere.rikkahub.ui.components.message

import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.transformers.ANYWHERE_THINKING_BLOCK_REGEX
import me.rerere.rikkahub.data.ai.transformers.THINKING_TAG_REGEX
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.time.Clock

internal const val CHART_DISPLAY_TOOL_NAME = "chart_display"

/**
 * 思考步骤类型，用于分组 Reasoning、客户端 Tool 和 ServerTool
 */
sealed interface ThinkingStep {
    data class ReasoningStep(
        val reasoning: UIMessagePart.Reasoning,
    ) : ThinkingStep

    data class ToolStep(
        val tool: UIMessagePart.Tool,
    ) : ThinkingStep

    data class ServerToolStep(
        val tool: UIMessagePart.ServerTool,
    ) : ThinkingStep
}

/**
 * 消息部分块类型，用于保持渲染顺序
 */
sealed interface MessagePartBlock {
    data class ThinkingBlock(val steps: List<ThinkingStep>) : MessagePartBlock
    data class ContentBlock(val part: UIMessagePart, val index: Int) : MessagePartBlock

    /** 成功执行的 chart_display 工具调用, 在正文中以图表卡片展示 */
    data class ChartBlock(val tool: UIMessagePart.Tool, val index: Int) : MessagePartBlock
}

/**
 * 将 parts 分组成 ThinkingBlock 和 ContentBlock
 * 连续的 Reasoning、客户端 Tool 和 ServerTool 会被分组到一个 ThinkingBlock 中
 * 成功执行的 chart_display 原地替换为 ChartBlock (会切断所在的 ThinkingBlock);
 * 生成中或失败的调用仍作为普通 ToolStep 展示。
 *
 * 同时兼容历史已入库消息中未拆分出的开头 `<think>` / `<thinking>` / `<thought>` 块：
 * 自动将其拆入 `ThinkingStep.ReasoningStep`，防止思维链文本混入正文触发立绘/CG 显示正则误匹配。
 */
fun List<UIMessagePart>.groupMessageParts(): List<MessagePartBlock> {
    val result = mutableListOf<MessagePartBlock>()
    var currentThinkingSteps = mutableListOf<ThinkingStep>()
    val hasExplicitReasoning = this.any { it is UIMessagePart.Reasoning }

    fun flushThinkingSteps() {
        if (currentThinkingSteps.isNotEmpty()) {
            result.add(MessagePartBlock.ThinkingBlock(currentThinkingSteps.toList()))
            currentThinkingSteps = mutableListOf()
        }
    }

    this.fastForEachIndexed { index, part ->
        when (part) {
            is UIMessagePart.Reasoning -> {
                currentThinkingSteps.add(ThinkingStep.ReasoningStep(part))
            }

            is UIMessagePart.Tool -> {
                if (part.isSuccessfulChartDisplay()) {
                    flushThinkingSteps()
                    result.add(MessagePartBlock.ChartBlock(part, index))
                } else {
                    currentThinkingSteps.add(ThinkingStep.ToolStep(part))
                }
            }

            is UIMessagePart.ServerTool -> {
                currentThinkingSteps.add(ThinkingStep.ServerToolStep(part))
            }

            is UIMessagePart.Text -> {
                val match = if (part.text.isNotBlank() &&
                    (part.text.contains("<think", ignoreCase = true) || part.text.contains("<thought", ignoreCase = true))
                ) {
                    THINKING_TAG_REGEX.find(part.text) ?: ANYWHERE_THINKING_BLOCK_REGEX.find(part.text)
                } else null
                if (match != null) {
                    val openTag = match.groupValues[1].lowercase()
                    val hasClosingTag = match.value.endsWith("</$openTag>", ignoreCase = true)
                    val now = Clock.System.now()
                    val extractedReasoning = match.groupValues[2].trim()
                    if (extractedReasoning.isNotEmpty()) {
                        val alreadyPresent = currentThinkingSteps.any {
                            it is ThinkingStep.ReasoningStep && it.reasoning.reasoning.contains(extractedReasoning)
                        }
                        if (!alreadyPresent) {
                            currentThinkingSteps.add(
                                ThinkingStep.ReasoningStep(
                                    UIMessagePart.Reasoning(
                                        reasoning = extractedReasoning,
                                        createdAt = now,
                                        finishedAt = if (hasClosingTag) now else null,
                                    )
                                )
                            )
                        }
                    }
                    flushThinkingSteps()
                    val remainingText = ANYWHERE_THINKING_BLOCK_REGEX.replace(part.text, "").trimStart('\r', '\n')
                    if (remainingText.isNotEmpty() || !hasClosingTag) {
                        result.add(MessagePartBlock.ContentBlock(part.copy(text = remainingText), index))
                    }
                } else {
                    flushThinkingSteps()
                    result.add(MessagePartBlock.ContentBlock(part, index))
                }
            }

            else -> {
                flushThinkingSteps()
                result.add(MessagePartBlock.ContentBlock(part, index))
            }
        }
    }
    flushThinkingSteps()
    return result
}

private fun UIMessagePart.Tool.isSuccessfulChartDisplay(): Boolean {
    if (toolName != CHART_DISPLAY_TOOL_NAME || !isExecuted) return false
    val outputText = output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
    val result = runCatching { JsonInstant.parseToJsonElement(outputText) }.getOrNull() as? JsonObject
    return (result?.get("success") as? JsonPrimitive)?.booleanOrNull == true
}
