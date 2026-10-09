package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeepSeek 缓存优化：工具描述稳定性测试
 *
 * 校验点：
 * 1. stableDate=true 时 memory_tool 描述不含动态日期（跨请求字节稳定，不破坏前缀缓存）
 * 2. 默认（stableDate=false）保持旧行为（含 "Today is ..." 日期）
 * 3. Settings.deepseekCacheOptimization 默认开启
 */
class ToolDescriptionStabilityTest {

    private fun memoryToolDescription(stableDate: Boolean): String =
        buildMemoryTools(
            json = Json,
            onCreation = { content -> AssistantMemory(id = 1, content = content) },
            onUpdate = { id, content -> AssistantMemory(id = id, content = content) },
            onDelete = {},
            stableDate = stableDate,
        ).first().description

    @Test
    fun stableDateStripsDynamicDateFromMemoryToolDescription() {
        val desc = memoryToolDescription(stableDate = true)
        assertTrue("应指向末尾的上下文提醒", desc.contains("context reminder"))
        assertFalse("不应包含动态日期文本", desc.contains("Today is "))
    }

    @Test
    fun defaultKeepsLegacyDynamicDate() {
        val desc = memoryToolDescription(stableDate = false)
        assertTrue("默认保留旧行为（含日期）", desc.contains("Today is "))
    }

    @Test
    fun settingsDefaultEnablesDeepseekCacheOptimization() {
        assertTrue(Settings().deepseekCacheOptimization)
    }
}
