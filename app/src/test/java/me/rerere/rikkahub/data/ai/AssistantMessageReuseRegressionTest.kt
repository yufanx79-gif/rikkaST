package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 「两条回复粘成一条」回归测试 —— 直接覆盖生产函数 [resolveResponseBaseMessages]。
 *
 * 真实事故（2026-11-24 21:40，conv 7d1ae95f）：一条 assistant 消息里塞了 3 段完整回复，
 * `usage.completionTokens` 只有 1782 却装着 8103 字正文；`<think_nya>` 出现 3 次、
 * `<game>` 开标签 4 个而闭标签只有 3 个。标签配对被打破后，面板解析器切不出完整块，
 * 整片面板从界面上消失。
 *
 * 根因：GenerationLoop 把「会话末尾是 assistant」当成可以复用的容器，于是新回复被追加进
 * 上一条已经写完的消息。多步工具续跑确实需要复用本轮自己刚产出的那条消息，但全新生成的
 * 第 0 步不该复用任何历史消息。
 */
class AssistantMessageReuseRegressionTest {

    private fun assistant(text: String): UIMessage =
        UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text(text)))

    private fun user(text: String): UIMessage =
        UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(text)))

    @Test
    fun `末尾是写完的 assistant 时新生成必须另起一条而不是追加`() {
        val history = listOf(
            assistant("开场白"),
            user("早安"),
            assistant("第一段完整回复"),
        )

        val base = resolveResponseBaseMessages(
            messages = history,
            forceNewAssistantMessage = true,
            modelId = null,
        )

        assertEquals("应当新开一条助手消息", history.size + 1, base.size)
        // 历史消息必须原样保留，绝不被吞并
        assertEquals("历史消息不能被改写", history, base.dropLast(1))
        assertEquals(MessageRole.ASSISTANT, base.last().role)
        assertTrue("新占位消息应为空", base.last().parts.isEmpty())
        // 关键：id 必须与上一条不同，否则会被 upsert 成同一条消息（即「粘在一起」）
        assertNotEquals(
            "新占位消息不能复用上一条的 id，否则会覆盖而不是新建",
            history.last().id,
            base.last().id,
        )
    }

    @Test
    fun `允许复用时 continue 才能续写同一楼层`() {
        val history = listOf(
            user("早安"),
            assistant("第一段完整回复"),
        )

        val base = resolveResponseBaseMessages(
            messages = history,
            forceNewAssistantMessage = false,
            modelId = null,
        )

        // /continue 语义：接在同一楼层，不新增消息
        assertEquals("续写不应新增消息", history.size, base.size)
        assertEquals("续写必须命中同一条消息", history.last().id, base.last().id)
    }

    @Test
    fun `末尾不是 assistant 时正常新开一条`() {
        val history = listOf(
            assistant("开场白"),
            user("你好"),
        )

        val base = resolveResponseBaseMessages(
            messages = history,
            forceNewAssistantMessage = false,
            modelId = null,
        )

        assertEquals(history.size + 1, base.size)
        assertEquals(MessageRole.ASSISTANT, base.last().role)
    }

    @Test
    fun `空会话也必须能开出第一条消息`() {
        val base = resolveResponseBaseMessages(
            messages = emptyList(),
            forceNewAssistantMessage = true,
            modelId = null,
        )

        assertEquals(1, base.size)
        assertEquals(MessageRole.ASSISTANT, base.last().role)
    }

    @Test
    fun `多步工具续跑第 0 步不复用而后续步复用`() {
        val history = listOf(user("查一下文件"), assistant("我先看看"))

        // 第 0 步：必须新开
        val step0 = resolveResponseBaseMessages(history, forceNewAssistantMessage = true, modelId = null)
        assertEquals(history.size + 1, step0.size)
        val ownedId = step0.last().id

        // 第 1 步：复用本轮刚创建的那条（forceNewAssistantMessage = false）
        val step1 = resolveResponseBaseMessages(step0, forceNewAssistantMessage = false, modelId = null)
        assertEquals("后续步不应再新开消息", step0.size, step1.size)
        assertEquals("后续步必须复用本轮自己的消息", ownedId, step1.last().id)
    }

    @Test
    fun `粘连会让标签配对失衡并导致面板消失`() {
        // 两段独立回复被拼进同一条消息后，<game> 开闭标签数量失衡 —— 面板消失的直接原因
        val first = "<game>第一段</game><options><option>A</option></options>"
        val second = "<game>第二段</game>"
        val merged = first + "\n" + second

        assertEquals("拼接后出现两个开标签", 2, Regex("<game>").findAll(merged).count())
        assertEquals("闭标签数量对不上，面板解析失败", 2, Regex("</game>").findAll(merged).count())
        // 单段回复的标签必须自平衡（面板可正常解析）
        assertEquals(1, Regex("<game>").findAll(first).count())
        assertEquals(1, Regex("</game>").findAll(first).count())
    }
}
