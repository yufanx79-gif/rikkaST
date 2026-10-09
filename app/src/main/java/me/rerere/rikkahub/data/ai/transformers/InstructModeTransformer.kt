package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InstructNamesBehavior
import me.rerere.rikkahub.data.model.InstructTemplate
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Instruct 序列格式化 · 发送前消息通道（对齐 SillyTavern Advanced Formatting → Instruct Mode）。
 *
 * 与 ST 的差异说明：ST 的 instruct 序列主要用于文本补全提示词拼接；本应用为 Chat-Completion 架构，
 * 这里把同一套序列语义应用到消息数组（每条消息按角色包裹序列，故事串单独用 story prefix/suffix）。
 * 模板默认关闭（enabled=false），开启前对消息零影响。
 *
 * 宏展开复用高保真 ST 宏引擎（StMacroSupport），{{name}} 单独替换为消息说话人名。
 */
object InstructModeTransformer : InputMessageTransformer, KoinComponent {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val template = ctx.assistant.instructTemplate
        if (!template.enabled) return messages
        if (!isInstructActivationMatch(template.activationRegex, ctx.model.modelId)) return messages

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

        val userName = ctx.settings.displaySetting.userNickname.ifBlank { "user" }
        val charName = ctx.assistant.name.ifBlank { "assistant" }
        return applyInstructFormatting(
            messages = messages,
            template = template,
            userName = userName,
            charName = charName,
            substitute = substitute,
        )
    }
}

/**
 * 激活正则判定（对齐 ST `activation_regex`）：正则空 → 恒激活；正则非法 → 停用（保守）；
 * 否则要求匹配模型 ID。
 */
internal fun isInstructActivationMatch(activationRegex: String, modelId: String): Boolean {
    if (activationRegex.isBlank()) return true
    val regex = runCatching { Regex(activationRegex, RegexOption.IGNORE_CASE) }.getOrNull() ?: return false
    return regex.containsMatchIn(modelId)
}

/**
 * 纯函数核心（可单测）：把 instruct 序列应用到消息数组。
 *
 * 规则（对齐 ST `formatInstructModeChat` / `formatInstructModeStoryString` 的聊天化子集）：
 * - 首条 SYSTEM 消息 = 故事串 → storyStringPrefix + wrap分隔 + 内容 + storyStringSuffix；
 * - 其余 SYSTEM → system 对（systemSameAsUser 时改用 input 对）；
 * - USER → input 对；ASSISTANT → output 对；
 * - 首/末条 USER、ASSISTANT 消息优先 first/last 变体（为空回退默认序列）；
 * - wrap：前缀与内容间以 '\n' 连接，且无后缀时自动补 '\n'；
 * - 名字：ALWAYS 恒包含（用户→userName、助手→[UIMessage.name] ?: charName）；FORCE 仅当消息自带 name；
 * - skipExamples：带 ExampleMessage 注解的消息跳过；
 * - 消息无文本 part 时跳过包裹。
 */
internal fun applyInstructFormatting(
    messages: List<UIMessage>,
    template: InstructTemplate,
    userName: String,
    charName: String,
    substitute: (String) -> String,
): List<UIMessage> {
    if (!template.enabled) return messages

    val firstUserIdx = messages.indexOfFirst { it.role == MessageRole.USER }
    val lastUserIdx = messages.indexOfLast { it.role == MessageRole.USER }
    val firstAssistantIdx = messages.indexOfFirst { it.role == MessageRole.ASSISTANT }
    val lastAssistantIdx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
    val storyIdx = messages.indexOfFirst { it.role == MessageRole.SYSTEM }

    fun expand(raw: String, speaker: String?): String {
        if (raw.isEmpty()) return raw
        var out = raw
        if (template.macro) {
            out = substitute(out)
            out = out.replace(Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE), speaker ?: "System")
        }
        return out
    }

    val separator = if (template.wrap) "\n" else ""

    return messages.mapIndexed { index, message ->
        // 示例消息跳过（对齐 ST skip_examples）
        if (template.skipExamples &&
            message.annotations.any { it is UIMessageAnnotation.ExampleMessage }
        ) {
            return@mapIndexed message
        }

        val role = message.role
        // 说话人名：消息自带 name（多角色/群聊/示例）优先，否则按角色回退
        val speakerName = message.name?.takeIf { it.isNotBlank() }
            ?: when (role) {
                MessageRole.USER -> userName
                MessageRole.ASSISTANT -> charName
                else -> null
            }

        // 选择序列（故事串单独走 story prefix/suffix）
        val prefixRaw: String
        val suffixRaw: String
        if (index == storyIdx) {
            prefixRaw = template.storyStringPrefix
            suffixRaw = template.storyStringSuffix
        } else {
            when (role) {
                MessageRole.SYSTEM -> {
                    if (template.systemSameAsUser) {
                        prefixRaw = template.inputSequence
                        suffixRaw = template.inputSuffix
                    } else {
                        prefixRaw = template.systemSequence
                        suffixRaw = template.systemSuffix
                    }
                }

                MessageRole.USER -> {
                    prefixRaw = when {
                        index == firstUserIdx && template.firstInputSequence.isNotBlank() -> template.firstInputSequence
                        index == lastUserIdx && template.lastInputSequence.isNotBlank() -> template.lastInputSequence
                        else -> template.inputSequence
                    }
                    suffixRaw = template.inputSuffix
                }

                MessageRole.ASSISTANT -> {
                    prefixRaw = when {
                        index == firstAssistantIdx && template.firstOutputSequence.isNotBlank() -> template.firstOutputSequence
                        index == lastAssistantIdx && template.lastOutputSequence.isNotBlank() -> template.lastOutputSequence
                        else -> template.outputSequence
                    }
                    suffixRaw = template.outputSuffix
                }

                else -> {
                    prefixRaw = ""
                    suffixRaw = ""
                }
            }
        }

        var prefix = expand(prefixRaw, speakerName)
        var suffix = expand(suffixRaw, speakerName)
        // 对齐 ST：wrap 且无后缀时补换行（故事串不补——ST 只在聊天消息分支补）
        if (index != storyIdx && suffix.isEmpty() && template.wrap) {
            suffix = "\n"
        }

        val includeName = when (template.namesBehavior) {
            InstructNamesBehavior.NONE -> false
            InstructNamesBehavior.ALWAYS -> role == MessageRole.USER || role == MessageRole.ASSISTANT
            InstructNamesBehavior.FORCE -> (role == MessageRole.USER || role == MessageRole.ASSISTANT) &&
                !message.name.isNullOrBlank()
        }
        val namePrefix = if (includeName && speakerName != null) "$speakerName: " else ""

        val firstTextIdx = message.parts.indexOfFirst { it is UIMessagePart.Text }
        if (firstTextIdx == -1) return@mapIndexed message
        val lastTextIdx = message.parts.indexOfLast { it is UIMessagePart.Text }

        val begin = buildString {
            if (prefix.isNotEmpty()) {
                append(prefix)
                if (separator.isNotEmpty()) append(separator)
            }
            append(namePrefix)
        }
        val end = suffix
        if (begin.isEmpty() && end.isEmpty()) return@mapIndexed message

        val parts = message.parts.toMutableList()
        val firstPart = parts[firstTextIdx] as UIMessagePart.Text
        parts[firstTextIdx] = firstPart.copy(text = begin + firstPart.text)
        val lastPart = parts[lastTextIdx] as UIMessagePart.Text
        parts[lastTextIdx] = lastPart.copy(text = lastPart.text + end)

        message.copy(parts = parts)
    }
}