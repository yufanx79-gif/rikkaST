package me.rerere.rikkahub.data.ai.tools

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.repository.FakeMemoryDAO
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek 前缀缓存：发给模型的工具定义必须跨请求字节稳定。
 *
 * 历史：记忆工具描述里曾经拼 `Today is <日期>`，日期一变 system 前缀就变，整段 prompt 缓存全失效。
 * 记忆工具重写后（`buildMemoryTools(repository, assistantId)`）动态日期被整体移除，
 * `json` / `stableDate` / `onCreation` 这些参数也一并消失。
 *
 * 这个测试守住回归：工具描述只由常量构成，同一份输入连调两次必须逐字节相同。
 */
class ToolDescriptionStabilityTest {

    private val tools = buildMemoryTools(
        repository = MemoryRepository(FakeMemoryDAO()),
        assistantId = "assistant",
    )

    @Test
    fun `memory tool descriptions are byte-stable across builds`() {
        // 隔一小段时间再建一次，模拟"跨请求"：任何随当前时间变化的内容都会暴露
        val first = tools.map { it.name to it.description }
        Thread.sleep(5)
        val second = buildMemoryTools(
            repository = MemoryRepository(FakeMemoryDAO()),
            assistantId = "assistant",
        ).map { it.name to it.description }

        assertEquals("工具描述必须跨请求逐字节稳定", first, second)
    }

    @Test
    fun `memory tool descriptions contain no dynamic date`() {
        val dateText = java.time.LocalDate.now().toString() // yyyy-MM-dd
        tools.forEach { tool ->
            assertTrue(
                "${tool.name} 的描述不应含动态日期：${tool.description.take(80)}",
                !tool.description.contains("Today is ") && !tool.description.contains(dateText),
            )
        }
    }

    @Test
    fun `all memory tools are described`() {
        assertEquals(MemoryToolNames.ALL, tools.map { it.name }.toSet())
        tools.forEach { assertTrue("${it.name} 缺少描述", it.description.isNotBlank()) }
    }

    @Test
    fun `settingsDefaultEnablesDeepseekCacheOptimization`() {
        assertTrue(Settings().deepseekCacheOptimization)
    }
}
