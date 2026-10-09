package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.runtime.InjectedPromptStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [v241] JSR `injectPrompts` 宿主编译链单测。
 *
 * 金标准：`refs/C-st-ext/N0VI028__JS-Slash-Runner/src/function/inject.ts`
 * - position='in_chat' → 按 depth 插入、按 role 发送；
 * - position='none' + should_scan → 只参与世界书扫描、发送前必须消失；
 * - once=true → 注入一次后从 store 消费；
 * - 注入消息不是真实聊天消息（isInjectedBlock = true）。
 */
class InjectedPromptsTest {

    @Before
    fun setUp() = InjectedPromptStore.clearAll()

    @After
    fun tearDown() = InjectedPromptStore.clearAll()

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    // ==================== store ====================

    @Test
    fun `store isolates conversations and supports uninject`() {
        InjectedPromptStore.inject("c1", listOf(prompt(id = "a")))
        InjectedPromptStore.inject("c2", listOf(prompt(id = "b")))
        assertEquals(listOf("a"), InjectedPromptStore.snapshot("c1").map { it.id })
        assertEquals(listOf("b"), InjectedPromptStore.snapshot("c2").map { it.id })

        InjectedPromptStore.uninject("c1", listOf("a"))
        assertTrue(InjectedPromptStore.snapshot("c1").isEmpty())
        assertEquals(listOf("b"), InjectedPromptStore.snapshot("c2").map { it.id })
        // 无会话作用域互不影响
        InjectedPromptStore.inject(null, listOf(prompt(id = "g")))
        assertEquals(listOf("g"), InjectedPromptStore.snapshot(null).map { it.id })
    }

    @Test
    fun `store overwrites same id like st set extension prompt`() {
        InjectedPromptStore.inject("c1", listOf(prompt(id = "a", content = "v1")))
        InjectedPromptStore.inject("c1", listOf(prompt(id = "a", content = "v2")))
        assertEquals(1, InjectedPromptStore.snapshot("c1").size)
        assertEquals("v2", InjectedPromptStore.snapshot("c1").single().content)
    }

    // ==================== 注入 ====================

    @Test
    fun `in_chat prompt is appended and sent with role`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "a", position = "in_chat", depth = 0, role = "system", content = "HINT")),
        )
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        assertEquals(2, out.size)
        assertEquals(MessageRole.SYSTEM, out.last().role)
        assertEquals("HINT", InjectedPromptCleanupTransformer.cleanupInjectedPrompts(out).last()
            .parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text })
        // 注入块必须被识别为「非真实聊天消息」
        assertTrue(out.last().isInjectedBlock())
    }

    @Test
    fun `in_chat depth inserts before the last messages`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "a", position = "in_chat", depth = 2, role = "user", content = "MID")),
        )
        val base = listOf(UIMessage.user("m1"), UIMessage.assistant("m2"), UIMessage.user("m3"))
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", base)
        assertEquals(4, out.size)
        assertEquals("MID", textOf(out[1]).removePrefix(JSR_INJECT_CHAT_MARKER).trim())
        assertEquals("m3", textOf(out[3]))
    }

    @Test
    fun `none prompt with should_scan joins scan text but is dropped before send`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "scan", position = "none", role = "system", content = "KEYWORD-FOR-SCAN", shouldScan = true)),
        )
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        // 宿主侧必须以「非 SYSTEM」承载，才能进入世界书扫描（扫描只看非 SYSTEM 消息）
        assertEquals(MessageRole.USER, out.last().role)
        assertTrue(textOf(out.last()).contains("KEYWORD-FOR-SCAN"))
        assertTrue(out.last().isInjectedBlock())
        // 发送前整条丢弃
        val cleaned = InjectedPromptCleanupTransformer.cleanupInjectedPrompts(out)
        assertEquals(1, cleaned.size)
        assertFalse(cleaned.any { textOf(it).contains("KEYWORD-FOR-SCAN") })
        // 扫描文本（PromptInjectionTransformer 的 context 构建口径）确实能看到关键词
        val scanText = out.filter { it.role != MessageRole.SYSTEM }.joinToString("\n") { textOf(it) }
        assertTrue(scanText.contains("KEYWORD-FOR-SCAN"))
    }

    @Test
    fun `none prompt without should_scan is a no-op`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "noop", position = "none", content = "IGNORED", shouldScan = false)),
        )
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        assertEquals(1, out.size)
    }

    @Test
    fun `cleanup strips marker but keeps in_chat content`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "a", position = "in_chat", depth = 0, role = "system", content = "LINE1\nLINE2")),
        )
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", emptyList())
        val cleaned = InjectedPromptCleanupTransformer.cleanupInjectedPrompts(out)
        assertEquals("LINE1\nLINE2", textOf(cleaned.single()))
        assertFalse(cleaned.single().isInjectedBlock())
    }

    // ==================== once ====================

    @Test
    fun `once prompt is consumed after one injection`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "once", position = "in_chat", content = "ONLY-ONCE", once = true)),
        )
        val first = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        assertEquals(2, first.size)
        assertTrue(InjectedPromptStore.snapshot("c1").isEmpty())
        val second = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        assertEquals(1, second.size)
    }

    @Test
    fun `persistent prompt stays across generations until uninjected`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "keep", position = "in_chat", content = "ALWAYS", once = false)),
        )
        repeat(3) {
            val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
            assertEquals(2, out.size)
        }
        InjectedPromptStore.uninject("c1", listOf("keep"))
        assertEquals(
            1,
            InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi"))).size,
        )
    }

    // ==================== 边界 ====================

    @Test
    fun `unknown conversation or empty content is ignored`() {
        InjectedPromptStore.inject(
            "c1",
            listOf(prompt(id = "blank", position = "in_chat", content = "   ")),
        )
        assertEquals(1, InjectedPromptsTransformer.applyInjectedPrompts("c2", listOf(UIMessage.user("hi"))).size)
        val out = InjectedPromptsTransformer.applyInjectedPrompts("c1", listOf(UIMessage.user("hi")))
        assertEquals(1, out.size)
        // 空内容 + once 也会被消费，避免残留
        assertTrue(InjectedPromptStore.snapshot("c1").isEmpty())
    }

    private fun prompt(
        id: String,
        position: String = "in_chat",
        depth: Int = 0,
        role: String = "system",
        content: String = "X",
        shouldScan: Boolean = false,
        once: Boolean = false,
    ) = InjectedPromptStore.InjectedPrompt(
        id = id,
        position = position,
        depth = depth,
        role = role,
        content = content,
        shouldScan = shouldScan,
        once = once,
    )
}
