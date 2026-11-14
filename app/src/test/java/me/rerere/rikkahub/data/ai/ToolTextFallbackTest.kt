package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 模型把工具调用写成正文时的保守兜底。
 *
 * 关键在于"不误伤"：正常叙事、未知工具名、非法参数都必须原样返回 null。
 */
class ToolTextFallbackTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val tools = setOf("memory_append", "memory_read")

    private fun assistantText(text: String) =
        UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(text)))

    @Test
    fun `function tag form is promoted`() {
        val result = ToolTextFallback.extractToolCall(
            assistantText("""<function=memory_append>{"path":"/profile.md","content":"喜欢猫"}</function>"""),
            tools,
            json,
        )
        assertEquals("memory_append", result?.first)
        assertEquals("""{"path":"/profile.md","content":"喜欢猫"}""", result?.second)
    }

    @Test
    fun `json object form is promoted`() {
        val result = ToolTextFallback.extractToolCall(
            assistantText("""{"name":"memory_append","arguments":{"path":"/profile.md","content":"x"}}"""),
            tools,
            json,
        )
        assertEquals("memory_append", result?.first)
    }

    @Test
    fun `fenced json form is promoted`() {
        val result = ToolTextFallback.extractToolCall(
            assistantText("""```json
{"name":"memory_read","arguments":{"path":["/profile.md"]}}
```"""),
            tools,
            json,
        )
        assertEquals("memory_read", result?.first)
    }

    @Test
    fun `unknown tool name is ignored`() {
        assertNull(
            ToolTextFallback.extractToolCall(
                assistantText("""<function=delete_everything>{"path":"/"}</function>"""),
                tools,
                json,
            )
        )
    }

    @Test
    fun `normal narration is untouched`() {
        assertNull(
            ToolTextFallback.extractToolCall(
                assistantText("我记住了你喜欢猫，以后会注意。"),
                tools,
                json,
            )
        )
    }

    @Test
    fun `narration containing json but no tool name is untouched`() {
        assertNull(
            ToolTextFallback.extractToolCall(
                assistantText("""这是配置：{"path":"/a","content":"b"}，你看对不对？"""),
                tools,
                json,
            )
        )
    }

    @Test
    fun `invalid arguments are ignored`() {
        assertNull(
            ToolTextFallback.extractToolCall(
                assistantText("""<function=memory_append>{not json}</function>"""),
                tools,
                json,
            )
        )
    }

    @Test
    fun `message that already has a real tool call is not touched`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("""<function=memory_append>{"path":"/a"}</function>"""),
                UIMessagePart.Tool(toolCallId = "c1", toolName = "memory_append", input = "{}"),
            ),
        )
        assertNull(ToolTextFallback.extractToolCall(message, tools, json))
    }

    @Test
    fun `user message is never promoted`() {
        val message = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("""<function=memory_append>{"path":"/a"}</function>""")),
        )
        assertNull(ToolTextFallback.extractToolCall(message, tools, json))
    }

    @Test
    fun `overlong text is ignored`() {
        val long = "x".repeat(3000) + """<function=memory_append>{"path":"/a"}</function>"""
        assertNull(ToolTextFallback.extractToolCall(assistantText(long), tools, json))
    }
}
