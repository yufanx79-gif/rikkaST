package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import me.rerere.rikkahub.data.st.regex.mergeRegexScripts
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * ST 正则脚本 · 提示词通道（对齐 SillyTavern `getMessage` 的 `getRegexedString` 调用）。
 *
 * 在消息组装为提示词之前应用 `settings.regexScripts`：
 * - USER 消息 → placement = USER_INPUT
 * - ASSISTANT 消息 → placement = AI_OUTPUT
 * - Reasoning 片段 → placement = REASONING
 * - 统一 isPrompt = true，depth = 距离最新消息的距离（0 = 最新一条）
 *
 * 不修改消息源（PORT NOTE 1）：只作用于本次发送给模型的副本；
 * 显示通道由 ChatMessage / ChatMessageReasoning 在渲染时叠加，两条通道净效果与 ST 等价。
 *
 * 替换串中的宏（{{user}} / {{char}} / 变量等）走高保真宏引擎，与占位符管线保持一致。
 */
object StRegexInputTransformer : InputMessageTransformer, KoinComponent {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val scripts = mergeRegexScripts(ctx.settings.regexScripts, ctx.assistant)
        if (scripts.isEmpty()) return messages

        val settingsStore = get<SettingsStore>()
        val vars = PlaceholderTransformer.createVars(settingsStore, ctx.settings)
        val placeholderCtx = PlaceholderCtx(
            context = ctx.context,
            settingsStore = settingsStore,
            settings = ctx.settings,
            model = ctx.model,
            assistant = ctx.assistant,
            messages = messages,
            conversationId = ctx.conversationId,
            generationType = ctx.generationType,
            swipeMeta = ctx.swipeMeta,
        )
        val substitute: (String) -> String = { text ->
            StMacroSupport.substitute(text, placeholderCtx, vars, DefaultPlaceholderProvider)
        }

        val result = messages.mapIndexed { index, message ->
            // 注入块（作者注释/人设/世界书等）不参与正则，避免二次处理注入内容
            if (message.isInjectedBlock()) return@mapIndexed message
            val placement = when (message.role) {
                MessageRole.USER -> RegexPlacement.USER_INPUT
                MessageRole.ASSISTANT -> RegexPlacement.AI_OUTPUT
                else -> null
            } ?: return@mapIndexed message
            val depth = messages.size - 1 - index
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> part.copy(
                            text = RegexScriptEngine.getRegexedString(
                                rawString = part.text,
                                scripts = scripts,
                                placement = placement,
                                isPrompt = true,
                                depth = depth,
                                substitute = substitute,
                            )
                        )
                        is UIMessagePart.Reasoning -> part.copy(
                            reasoning = RegexScriptEngine.getRegexedString(
                                rawString = part.reasoning,
                                scripts = scripts,
                                placement = RegexPlacement.REASONING,
                                isPrompt = true,
                                depth = depth,
                                substitute = substitute,
                            )
                        )
                        else -> part
                    }
                }
            )
        }
        vars.flush()
        return result
    }
}
