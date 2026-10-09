package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * S3：世界书 AT_DEPTH 注入语义 vs ST 逐字段对照测试。
 *
 * 金标准来源（SillyTavern 1.19.0）：
 * - world-info.js:5203-5246 `[...allActivatedEntries.values()].sort(sortFn)`（sortFn = order 降序）
 *   + `entries.unshift(content)` → 同 (depth, role) 分组内最终为 order 升序；内容 '\n' 连接；
 * - openai.js:810-871 populationInjectionPrompts：
 *   depth 0 = 最后一条消息之后；depth d = 倒数第 d 条消息之前；
 *   同 depth 多角色按固定顺序 [system, user, assistant] 生成多条消息后整体 splice；
 * - script.js:8916-8935 setExtensionPrompt：depth 语义「0 表示上下文最后一条消息」；
 * - DEFAULT_DEPTH = 4（world-info.js:96）。
 */
class PromptInjectionDepthTest {

    private fun textOf(messages: List<UIMessage>): List<String> =
        messages.map { m -> m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text } }

    private fun roleOf(messages: List<UIMessage>): List<MessageRole> = messages.map { it.role }

    private fun depthEntry(
        name: String,
        content: String,
        depth: Int,
        role: MessageRole,
        priority: Int = 1,
    ): PromptInjection.RegexInjection = PromptInjection.RegexInjection(
        name = name,
        enabled = true,
        priority = priority,
        position = InjectionPosition.AT_DEPTH,
        content = content,
        injectDepth = depth,
        role = role,
        constantActive = true,
        ignoreBudget = true,
    )

    private fun run(
        entries: List<PromptInjection.RegexInjection>,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val bookId = Uuid.random()
        val book = Lorebook(id = bookId, name = "b", enabled = true, entries = entries)
        val assistant = Assistant(name = "char", lorebookIds = setOf(bookId))
        return transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
        )
    }

    private fun baseChat(): List<UIMessage> = listOf(
        UIMessage.user("U1"),
        UIMessage.assistant("A1"),
        UIMessage.user("U2"),
    )

    @Test
    fun `depth zero lands after last message`() {
        val result = run(listOf(depthEntry("d0", "D0", 0, MessageRole.SYSTEM)), baseChat())
        assertEquals(listOf("U1", "A1", "U2", "D0"), textOf(result))
    }

    @Test
    fun `depth one lands before last message`() {
        val result = run(listOf(depthEntry("d1", "D1", 1, MessageRole.SYSTEM)), baseChat())
        assertEquals(listOf("U1", "A1", "D1", "U2"), textOf(result))
    }

    @Test
    fun `depth two lands before second to last message`() {
        val result = run(listOf(depthEntry("d2", "D2", 2, MessageRole.SYSTEM)), baseChat())
        assertEquals(listOf("U1", "D2", "A1", "U2"), textOf(result))
    }

    @Test
    fun `multiple depths keep st placement`() {
        val result = run(
            listOf(
                depthEntry("d0", "D0", 0, MessageRole.SYSTEM),
                depthEntry("d1", "D1", 1, MessageRole.SYSTEM),
                depthEntry("d2", "D2", 2, MessageRole.SYSTEM),
            ),
            baseChat(),
        )
        assertEquals(listOf("U1", "D2", "A1", "D1", "U2", "D0"), textOf(result))
    }

    @Test
    fun `depth beyond chat length clamps to front`() {
        val result = run(listOf(depthEntry("far", "FAR", 99, MessageRole.SYSTEM)), baseChat())
        assertEquals(listOf("FAR", "U1", "A1", "U2"), textOf(result))
    }

    @Test
    fun `same depth multiple roles follow system user assistant order`() {
        // 输入故意乱序（assistant, system, user），输出必须固定为 system, user, assistant
        val result = run(
            listOf(
                depthEntry("a", "RA", 1, MessageRole.ASSISTANT),
                depthEntry("s", "RS", 1, MessageRole.SYSTEM),
                depthEntry("u", "RU", 1, MessageRole.USER),
            ),
            baseChat(),
        )
        assertEquals(listOf("U1", "A1", "RS", "RU", "RA", "U2"), textOf(result))
        assertEquals(
            listOf(
                MessageRole.USER, MessageRole.ASSISTANT,
                MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT,
                MessageRole.USER,
            ),
            roleOf(result),
        )
    }

    @Test
    fun `same depth same role merges by newline in priority ascending order`() {
        val result = run(
            listOf(
                depthEntry("p2", "P2", 1, MessageRole.SYSTEM, priority = 2),
                depthEntry("p1", "P1", 1, MessageRole.SYSTEM, priority = 1),
            ),
            baseChat(),
        )
        assertEquals(listOf("U1", "A1", "P1\nP2", "U2"), textOf(result))
    }
}
