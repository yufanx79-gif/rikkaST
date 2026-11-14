package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 工具调用签名：用于"本轮内完全相同的调用不执行第二次"。
 *
 * 只做键顺序/空白的归一，不做语义近似 —— 用户明确要求"再做一次"时不能误挡。
 */
class ToolCallDedupeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun tool(name: String, input: String) =
        UIMessagePart.Tool(toolCallId = "id", toolName = name, input = input)

    @Test
    fun `same tool and same args produce same signature`() {
        val a = toolCallSignature(tool("memory_append", """{"path":"/profile.md","content":"喜欢猫"}"""), json)
        val b = toolCallSignature(tool("memory_append", """{"path":"/profile.md","content":"喜欢猫"}"""), json)
        assertEquals(a, b)
    }

    @Test
    fun `key order and whitespace are normalized`() {
        val a = toolCallSignature(tool("t", """{"a":1,"b":2}"""), json)
        val b = toolCallSignature(tool("t", """{ "b" : 2 , "a" : 1 }"""), json)
        assertEquals(a, b)
    }

    @Test
    fun `different args are different signatures`() {
        val a = toolCallSignature(tool("memory_append", """{"content":"喜欢猫"}"""), json)
        val b = toolCallSignature(tool("memory_append", """{"content":"喜欢狗"}"""), json)
        assertNotEquals(a, b)
    }

    @Test
    fun `different tool names are different signatures`() {
        val a = toolCallSignature(tool("memory_append", "{}"), json)
        val b = toolCallSignature(tool("memory_write", "{}"), json)
        assertNotEquals(a, b)
    }

    @Test
    fun `invalid json falls back to trimmed text`() {
        val a = toolCallSignature(tool("t", "  not-json  "), json)
        val b = toolCallSignature(tool("t", "not-json"), json)
        assertEquals(a, b)
    }

    @Test
    fun `nested key order is also normalized`() {
        val a = toolCallSignature(tool("t", """{"outer":{"x":1,"y":2},"list":[{"p":1,"q":2}]}"""), json)
        val b = toolCallSignature(tool("t", """{"list":[{"q":2,"p":1}],"outer":{"y":2,"x":1}}"""), json)
        assertEquals(a, b)
    }

    @Test
    fun `array order still matters`() {
        val a = toolCallSignature(tool("t", """{"path":["/a","/b"]}"""), json)
        val b = toolCallSignature(tool("t", """{"path":["/b","/a"]}"""), json)
        assertNotEquals(a, b)
    }

    @Test
    fun `blank input is treated as empty object`() {
        val a = toolCallSignature(tool("t", ""), json)
        val b = toolCallSignature(tool("t", "{}"), json)
        assertEquals(a, b)
    }
}
