package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.getStRegexed

/**
 * 显示通道正则文本（带重组缓存）：避免滚动/动画等重组时重复执行全部正则脚本。
 *
 * 对应 ST 显示侧 getRegexedString 的净行为；仅缓存结果，不修改消息源。
 * 之前的实现直接在 Composable 参数求值处执行正则，重组（滚动、输入、流式输出每帧）
 * 都会对每条消息全量重跑所有脚本——脚本较多（如角色卡内置 20+ 条）时会拖死 UI。
 */
@Composable
internal fun rememberStRegexedText(
    text: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
): String = remember(text, scripts, assistant) {
    text
        .replaceRegexes(assistant = assistant, scope = scope, visual = true)
        .getStRegexed(scripts, placement, isMarkdown = true)
}
