package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.ai.prompts.buildProactiveMemoryReminder
import me.rerere.rikkahub.data.ai.prompts.shouldInjectProactiveMemory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主动记忆提醒：默认文案必须包含"没有就不写"和"不要重复写"这两条约束，
 * 否则模型会被提示成每轮都硬写一条。
 */
class ProactiveMemoryPromptTest {

    @Test
    fun `default prompt tells the model to do nothing when there is nothing`() {
        val prompt = buildProactiveMemoryReminder("")
        assertTrue(prompt.contains("若没有，什么都不要做"))
        assertTrue(prompt.contains("已经记过的不要再写一遍"))
        assertTrue(prompt.startsWith("<proactive_memory_reminder>"))
        assertTrue(prompt.endsWith("</proactive_memory_reminder>"))
    }

    @Test
    fun `default prompt does not forbid the user's own sensitive facts`() {
        val prompt = buildProactiveMemoryReminder("")
        // 产品允许用户自由聊天：不能把用户的私密话题当成"禁止记录"的敏感清单
        assertTrue(!prompt.contains("自伤"))
        assertTrue(!prompt.contains("性经历"))
        assertTrue(!prompt.contains("犯罪记录"))
    }

    @Test
    fun `custom prompt replaces the default`() {
        val prompt = buildProactiveMemoryReminder("  自定义内容  ")
        assertTrue(prompt == "自定义内容")
    }

    @Test
    fun `blank custom prompt falls back to default`() {
        assertTrue(buildProactiveMemoryReminder("   ").contains("<proactive_memory_reminder>"))
    }

    // ===== 隔 N 轮注入 =====

    @Test
    fun `injects on every Nth user turn`() {
        val args = { turn: Int ->
            shouldInjectProactiveMemory(
                enableMemory = true,
                enableProactiveMemory = true,
                hasTools = true,
                userTurnCount = turn,
                interval = 5,
            )
        }
        assertTrue(args(5))
        assertTrue(args(10))
        assertTrue(args(15))
        assertFalse(args(1))
        assertFalse(args(4))
        assertFalse(args(6))
    }

    @Test
    fun `interval one injects every turn`() {
        (1..3).forEach { turn ->
            assertTrue(
                shouldInjectProactiveMemory(true, true, true, turn, interval = 1)
            )
        }
    }

    @Test
    fun `requires memory and tools`() {
        assertFalse(shouldInjectProactiveMemory(false, true, true, 5, 5))
        assertFalse(shouldInjectProactiveMemory(true, false, true, 5, 5))
        // 没有工具时模型写不了记忆，注入只是浪费 token
        assertFalse(shouldInjectProactiveMemory(true, true, false, 5, 5))
    }

    @Test
    fun `no user turns means no injection`() {
        assertFalse(shouldInjectProactiveMemory(true, true, true, 0, 5))
        assertFalse(shouldInjectProactiveMemory(true, true, true, -1, 5))
    }

    @Test
    fun `interval is clamped into 1 to 20`() {
        // 0 / 负数收敛成 1：每轮都注入，而不是永远不注入或除零
        assertTrue(shouldInjectProactiveMemory(true, true, true, 3, 0))
        assertTrue(shouldInjectProactiveMemory(true, true, true, 3, -5))
        // 超过 20 收敛成 20
        assertFalse(shouldInjectProactiveMemory(true, true, true, 19, 100))
        assertTrue(shouldInjectProactiveMemory(true, true, true, 20, 100))
    }
}
