package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.InstructNamesBehavior
import me.rerere.rikkahub.data.model.InstructTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instruct 序列格式化（Advanced Formatting → Instruct Mode）语义测试。
 *
 * 金标准来源：SillyTavern 1.18.0 `public/scripts/instruct-mode.js`
 * - `formatInstructModeChat`：prefix + separator + name + mes + suffix（wrap 时补 '\n'）；
 * - `formatInstructModeStoryString`：story prefix + separator + content + suffix（suffix 不补 '\n'）；
 * - `skip_examples`：带示例注解的消息不做序列化；
 * - `activation_regex`：空恒激活、非法正则停用（保守）。
 */
class InstructModeTransformerTest {

    private val noopSubstitute: (String) -> String = { it }

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun texts(messages: List<UIMessage>): List<String> = messages.map(::textOf)

    private fun baseTemplate(): InstructTemplate = InstructTemplate(
        enabled = true,
        systemSequence = "### System:",
        inputSequence = "### User:",
        outputSequence = "### Assistant:",
    )

    // ==================== 激活正则（activation_regex） ====================

    @Test
    fun `blank activation regex always matches`() {
        assertTrue(isInstructActivationMatch("", "gpt-4"))
        assertTrue(isInstructActivationMatch("   ", "gpt-4"))
    }

    @Test
    fun `activation regex matches case-insensitively`() {
        assertTrue(isInstructActivationMatch("gpt-4", "OpenAI/GPT-4-Turbo"))
        assertTrue(isInstructActivationMatch("^claude", "claude-3-opus"))
    }

    @Test
    fun `activation regex mismatch blocks formatting`() {
        assertFalse(isInstructActivationMatch("claude", "gpt-4o"))
    }

    @Test
    fun `invalid activation regex disables formatting conservatively`() {
        assertFalse(isInstructActivationMatch("[unclosed", "gpt-4"))
    }

    // ==================== 总开关 ====================

    @Test
    fun `disabled template returns messages untouched`() {
        val messages = listOf(UIMessage.system("story"), UIMessage.user("hi"))
        val result = applyInstructFormatting(
            messages = messages,
            template = InstructTemplate(enabled = false),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals(messages, result)
    }

    // ==================== 故事串（story string） ====================

    @Test
    fun `first system message is wrapped by story prefix and suffix`() {
        val template = baseTemplate().copy(
            storyStringPrefix = "<story>",
            storyStringSuffix = "</story>",
        )
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.system("STORY"), UIMessage.user("hi")),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("<story>\nSTORY</story>", texts(result)[0])
    }

    @Test
    fun `story string is left untouched when no story sequences configured`() {
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.system("STORY")),
            template = baseTemplate(),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("STORY", texts(result)[0])
    }

    // ==================== 聊天消息序列 ====================

    @Test
    fun `user message is wrapped with input sequence and trailing newline`() {
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = baseTemplate(),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("### User:\nhi\n", texts(result)[0])
    }

    @Test
    fun `assistant message is wrapped with output sequence`() {
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.assistant("yo")),
            template = baseTemplate(),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("### Assistant:\nyo\n", texts(result)[0])
    }

    @Test
    fun `system message uses system sequence unless same as user`() {
        val messages = listOf(UIMessage.system("STORY"), UIMessage.system("SYS2"), UIMessage.user("hi"))
        val normal = applyInstructFormatting(messages, baseTemplate(), "u", "c", noopSubstitute)
        assertEquals("### System:\nSYS2\n", texts(normal)[1])

        val sameAsUser = applyInstructFormatting(
            messages,
            baseTemplate().copy(systemSameAsUser = true),
            "u",
            "c",
            noopSubstitute,
        )
        assertEquals("### User:\nSYS2\n", texts(sameAsUser)[1])
    }

    // ==================== first / last 变体 ====================

    @Test
    fun `first and last input variants take precedence`() {
        val template = baseTemplate().copy(
            firstInputSequence = "[FIRST]",
            lastInputSequence = "[LAST]",
        )
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("u1"), UIMessage.user("u2")),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("[FIRST]\nu1\n", texts(result)[0])
        assertEquals("[LAST]\nu2\n", texts(result)[1])
    }

    @Test
    fun `single user message prefers first variant`() {
        val template = baseTemplate().copy(
            firstInputSequence = "[FIRST]",
            lastInputSequence = "[LAST]",
        )
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("only")),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("[FIRST]\nonly\n", texts(result)[0])
    }

    @Test
    fun `empty first and last variants fall back to default sequences`() {
        val template = baseTemplate().copy(
            firstOutputSequence = "",
            lastOutputSequence = "[LAST-OUT]",
        )
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.assistant("a1"), UIMessage.assistant("a2")),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("### Assistant:\na1\n", texts(result)[0])
        assertEquals("[LAST-OUT]\na2\n", texts(result)[1])
    }

    // ==================== wrap / suffix ====================

    @Test
    fun `wrap disabled concatenates sequence directly`() {
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = baseTemplate().copy(wrap = false),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("### User:hi", texts(result)[0])
    }

    @Test
    fun `custom suffix is appended verbatim and replaces the auto newline`() {
        val template = baseTemplate().copy(
            inputSequence = "<start>",
            inputSuffix = "<end>",
        )
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("<start>\nhi<end>", texts(result)[0])
    }

    // ==================== 宏（macro / {{name}}） ====================

    @Test
    fun `macros are expanded inside sequences when enabled`() {
        val template = baseTemplate().copy(inputSequence = "<{{char}}>")
        val substitute: (String) -> String = { it.replace("{{char}}", "Alice") }
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = template,
            userName = "u",
            charName = "Alice",
            substitute = substitute,
        )
        assertEquals("<Alice>\nhi\n", texts(result)[0])
    }

    @Test
    fun `name macro resolves to the speaking character`() {
        val template = baseTemplate().copy(
            inputSequence = "[{{name}}]",
            outputSequence = "[{{name}}]",
        )
        val result = applyInstructFormatting(
            messages = listOf(
                UIMessage.user("hi"),
                UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("yo")), name = "Rin"),
                UIMessage.assistant("yo2"),
            ),
            template = template,
            userName = "Bob",
            charName = "Chara",
            substitute = noopSubstitute,
        )
        assertEquals("[Bob]\nhi\n", texts(result)[0])
        assertEquals("[Rin]\nyo\n", texts(result)[1])
        assertEquals("[Chara]\nyo2\n", texts(result)[2])
    }

    @Test
    fun `macro expansion is skipped when disabled`() {
        val template = baseTemplate().copy(inputSequence = "{{char}}:", macro = false)
        val substitute: (String) -> String = { it.replace("{{char}}", "Alice") }
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = template,
            userName = "u",
            charName = "Alice",
            substitute = substitute,
        )
        assertEquals("{{char}}:\nhi\n", texts(result)[0])
    }

    // ==================== names_behavior ====================

    @Test
    fun `names always prepends speaker names`() {
        val template = baseTemplate().copy(namesBehavior = InstructNamesBehavior.ALWAYS)
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi"), UIMessage.assistant("yo")),
            template = template,
            userName = "Bob",
            charName = "Alice",
            substitute = noopSubstitute,
        )
        assertEquals("### User:\nBob: hi\n", texts(result)[0])
        assertEquals("### Assistant:\nAlice: yo\n", texts(result)[1])
    }

    @Test
    fun `names force only uses explicit message names`() {
        val template = baseTemplate().copy(namesBehavior = InstructNamesBehavior.FORCE)
        val result = applyInstructFormatting(
            messages = listOf(
                UIMessage.user("hi"),
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("yo")), name = "Rin"),
            ),
            template = template,
            userName = "Bob",
            charName = "Alice",
            substitute = noopSubstitute,
        )
        assertEquals("### User:\nhi\n", texts(result)[0])
        assertEquals("### User:\nRin: yo\n", texts(result)[1])
    }

    @Test
    fun `names none never prepends names`() {
        val template = baseTemplate().copy(namesBehavior = InstructNamesBehavior.NONE)
        val result = applyInstructFormatting(
            messages = listOf(UIMessage.user("hi")),
            template = template,
            userName = "Bob",
            charName = "Alice",
            substitute = noopSubstitute,
        )
        assertEquals("### User:\nhi\n", texts(result)[0])
    }

    // ==================== skip_examples / 非文本消息 ====================

    @Test
    fun `example messages skip wrapping when skipExamples enabled`() {
        val template = baseTemplate().copy(skipExamples = true)
        val result = applyInstructFormatting(
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("ex")),
                    annotations = listOf(UIMessageAnnotation.ExampleMessage),
                ),
                UIMessage.user("real"),
            ),
            template = template,
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("ex", texts(result)[0])
        assertEquals("### User:\nreal\n", texts(result)[1])
    }

    @Test
    fun `example messages are wrapped when skipExamples disabled`() {
        val result = applyInstructFormatting(
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("ex")),
                    annotations = listOf(UIMessageAnnotation.ExampleMessage),
                ),
            ),
            template = baseTemplate(),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals("### User:\nex\n", texts(result)[0])
    }

    @Test
    fun `message without text part is passed through`() {
        val imageMessage = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Image("https://example.com/a.png")),
        )
        val result = applyInstructFormatting(
            messages = listOf(imageMessage),
            template = baseTemplate(),
            userName = "u",
            charName = "c",
            substitute = noopSubstitute,
        )
        assertEquals(imageMessage, result[0])
        assertTrue(result[0].parts[0] is UIMessagePart.Image)
    }
}
