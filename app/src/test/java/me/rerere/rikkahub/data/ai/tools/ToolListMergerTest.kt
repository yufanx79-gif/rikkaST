package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：`Tool names must be unique.`
 *
 * 真机事故（2026-11-14 12:08/12:11，DeepSeek）：`tools-built(count=31)`，但其中 6 个是
 * 重复的 `memory_*` —— ChatToolFactory 注入一次，GenerationLoop 又补一次 —— Provider 直接
 * 拒单，用户每次发送都 400 失败。
 */
class ToolListMergerTest {

    private fun tool(name: String) = Tool(
        name = name,
        description = "",
        execute = { listOf<UIMessagePart>(UIMessagePart.Text("ok")) },
    )

    private fun memoryTools() = MemoryToolNames.ALL.map { tool(it) }

    @Test
    fun `already present memory tools are not added twice`() {
        // 主聊天路径的真实形态：ChatToolFactory 已经把记忆工具放进来了
        val existing = memoryTools() + tool("search_web") + tool("ask_user")
        val merged = mergeToolsWithMemory(existing, includeMemory = true) { memoryTools() }

        assertEquals("不该再补一份记忆工具", existing.size, merged.size)
        assertEquals("工具名必须唯一", merged.size, merged.map { it.name }.distinct().size)
    }

    @Test
    fun `memory tools are added when caller did not provide them`() {
        // 群聊路径：手写工具清单，不含记忆工具
        val existing = listOf(tool("search_web"), tool("ask_user"))
        val merged = mergeToolsWithMemory(existing, includeMemory = true) { memoryTools() }

        assertEquals(existing.size + MemoryToolNames.ALL.size, merged.size)
        assertTrue(merged.map { it.name }.containsAll(MemoryToolNames.ALL))
    }

    @Test
    fun `no memory tools when memory is disabled`() {
        val existing = listOf(tool("search_web"))
        val merged = mergeToolsWithMemory(existing, includeMemory = false) { memoryTools() }

        assertEquals(listOf("search_web"), merged.map { it.name })
    }

    @Test
    fun `partial memory tool set still counts as present`() {
        // 只要有一个记忆工具在，就认为调用方已经负责过记忆工具，避免半套重复
        val existing = listOf(tool(MemoryToolNames.READ), tool("search_web"))
        val merged = mergeToolsWithMemory(existing, includeMemory = true) { memoryTools() }

        assertEquals(listOf(MemoryToolNames.READ, "search_web"), merged.map { it.name })
    }

    @Test
    fun `duplicate names across plugin sources keep the first one`() {
        // 酒馆 JS 工具 / MCP 工具可能与内置工具重名，先注册的赢
        val builtin = tool("search_web")
        val fromPlugin = tool("search_web")
        val merged = dedupeToolNames(listOf(builtin, tool("ask_user"), fromPlugin))

        assertEquals(listOf("search_web", "ask_user"), merged.map { it.name })
        assertSame("应保留先出现的那个实例", builtin, merged.first())
    }

    @Test
    fun `duplicate detection is case insensitive`() {
        // MCP 服务器名会拼进工具名，只差大小写也算重名
        val merged = dedupeToolNames(listOf(tool("Read_File"), tool("read_file")))

        assertEquals(listOf("Read_File"), merged.map { it.name })
    }

    @Test
    fun `duplicateToolNames reports what was dropped`() {
        val dropped = duplicateToolNames(
            listOf(tool("a"), tool("b"), tool("a"), tool("A"), tool("b")),
        )

        assertEquals(listOf("a", "A", "b"), dropped)
    }

    @Test
    fun `empty and single tool lists are returned untouched`() {
        assertTrue(dedupeToolNames(emptyList()).isEmpty())
        val one = listOf(tool("only"))
        assertSame("单元素不必复制", one, dedupeToolNames(one))
        assertTrue(duplicateToolNames(one).isEmpty())
    }
}
