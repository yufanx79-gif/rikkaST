package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.ABSOLUTE_MAX_TOOL_STEPS
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TAVERN_MAX_TOOL_STEPS
import me.rerere.rikkahub.data.model.effectiveMaxToolSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：提示词里不能再出现不存在的工具名。
 *
 * 真机事故（2026-11-14）：`<tool_selection>` 手写死了 11 个工具名，其中
 * `workspace_read` / `workspace_write` / `workspace_edit` / `web_search` / `memory_tool`
 * **全都不存在**。模型照着提示词去找，一个都找不到，只能退而求其次乱试，
 * 表现为「装了 31 个工具却只用 shell」。
 *
 * 修复方式是把提示词改成按真实 tools 列表生成。这里守住两件事：
 * 1. 真实工具名集合与记忆工具名一致；
 * 2. 步数上限有绝对天花板，不会被设成 256 这种值。
 */
class ToolNamePromptTest {

    @Test
    fun `memory tool names are the real ones`() {
        // 这 6 个是 MemoryTools.kt 里真实注册的名字
        val real = setOf(
            "memory_read",
            "memory_write",
            "memory_str_replace",
            "memory_append",
            "memory_delete",
            "memory_list",
        )
        assertEquals(real, me.rerere.rikkahub.data.ai.tools.MemoryToolNames.ALL)

        // 曾经写进提示词的那个名字必须不存在
        assertFalse(
            "memory_tool 是个不存在的工具名，提示词里不能再出现",
            "memory_tool" in me.rerere.rikkahub.data.ai.tools.MemoryToolNames.ALL,
        )
    }

    @Test
    fun `normal assistant step limit is capped`() {
        val assistant = Assistant(name = "t", totalStepsLimit = 256)
        assertEquals(ABSOLUTE_MAX_TOOL_STEPS, assistant.effectiveMaxToolSteps())
    }

    @Test
    fun `tavern card step limit stays tighter than normal`() {
        val tavern = Assistant(
            name = "t",
            totalStepsLimit = 256,
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(name = "t"),
        )
        assertEquals(TAVERN_MAX_TOOL_STEPS, tavern.effectiveMaxToolSteps())
        assertTrue(TAVERN_MAX_TOOL_STEPS < ABSOLUTE_MAX_TOOL_STEPS)
    }

    @Test
    fun `step limit never drops below one`() {
        assertEquals(1, Assistant(name = "t", totalStepsLimit = 0).effectiveMaxToolSteps())
        assertEquals(1, Assistant(name = "t", totalStepsLimit = -5).effectiveMaxToolSteps())
    }

    @Test
    fun `reasonable limits pass through unchanged`() {
        assertEquals(8, Assistant(name = "t", totalStepsLimit = 8).effectiveMaxToolSteps())
        assertEquals(16, Assistant(name = "t", totalStepsLimit = 16).effectiveMaxToolSteps())
    }
}
