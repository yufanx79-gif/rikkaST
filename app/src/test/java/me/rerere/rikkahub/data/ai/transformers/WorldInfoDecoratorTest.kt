package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_ACTIVATE
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_DONT_ACTIVATE
import me.rerere.rikkahub.data.model.contentWithoutDecorators
import me.rerere.rikkahub.data.model.parseWorldInfoDecorators
import me.rerere.rikkahub.data.model.worldInfoDecorators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * W2 验收：世界书 `@@` 装饰器（`@@activate` / `@@dont_activate`）。
 *
 * 金标准：SillyTavern 1.18.0
 * - `world-info.js:100` KNOWN_DECORATORS（官方只有这两个）；
 * - `world-info.js:4538-4582` parseDecorators（含 `@@@` 转义的逐字行为）；
 * - `world-info.js:4763-4770` 扫描判定顺序：`@@activate` → 激活并 continue；
 *   `@@dont_activate` → 跳过；两者都在关键词匹配之前、constant/sticky 之前。
 */
class WorldInfoDecoratorTest {

    private fun textOf(messages: List<UIMessage>): String =
        messages.joinToString("\n") { m ->
            m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        }

    private fun decoratorEntry(
        name: String,
        content: String,
        keywords: List<String> = emptyList(),
        constant: Boolean = false,
    ): PromptInjection.RegexInjection = PromptInjection.RegexInjection(
        name = name,
        enabled = true,
        priority = 1,
        position = InjectionPosition.AFTER_CHARACTER,
        content = content,
        keywords = keywords,
        constantActive = constant,
        ignoreBudget = true, // 聚焦装饰器语义，避开短消息预算估算干扰
    )

    private fun scan(
        entries: List<PromptInjection.RegexInjection>,
        userText: String = "hello",
        recursive: Boolean = false,
    ): String {
        val book = Lorebook(id = Uuid.random(), name = "book", enabled = true, entries = entries)
        val assistant = Assistant(name = "char", lorebookIds = setOf(book.id))
        return textOf(
            transformMessages(
                messages = listOf(UIMessage.user(userText)),
                assistant = assistant,
                modeInjections = emptyList(),
                lorebooks = listOf(book),
                worldInfoRecursive = recursive,
                worldInfoMaxRecursionSteps = if (recursive) 2 else 0,
            )
        )
    }

    // ==================== parseDecorators 逐字对齐 ====================

    @Test
    fun `parse decorators matches st happy path`() {
        // 非 @@ 开头：原样
        val plain = parseWorldInfoDecorators("plain content")
        assertEquals(emptyList<String>(), plain.decorators)
        assertEquals("plain content", plain.content)

        // 单个 @@activate：装饰器记录，正文剥壳
        val single = parseWorldInfoDecorators("@@activate\nBODY")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), single.decorators)
        assertEquals("BODY", single.content)

        // 两个装饰器依次记录，正文从第一行非 @@ 行开始
        val both = parseWorldInfoDecorators("@@activate\n@@dont_activate\nBODY\nLINE2")
        assertEquals(
            listOf(WORLD_INFO_DECORATOR_ACTIVATE, WORLD_INFO_DECORATOR_DONT_ACTIVATE),
            both.decorators,
        )
        assertEquals("BODY\nLINE2", both.content)

        // 正文中间出现的 @@ 不是装饰器（只扫描开头连续 @@ 行）
        val inline = parseWorldInfoDecorators("BODY\n@@activate")
        assertEquals(emptyList<String>(), inline.decorators)
        assertEquals("BODY\n@@activate", inline.content)
    }

    @Test
    fun `parse decorators full-at-line semantics match st`() {
        // 全部行都是 @@ 行（未触发结束分支）→ 官方 content 保持原样
        val allDecorators = parseWorldInfoDecorators("@@activate")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), allDecorators.decorators)
        assertEquals("@@activate", allDecorators.content)

        // 首行 @@@ 转义（未 fallback）→ 跳过；正文从下一非 @@ 行开始
        val escaped = parseWorldInfoDecorators("@@@activate\nBODY")
        assertEquals(emptyList<String>(), escaped.decorators)
        assertEquals("BODY", escaped.content)

        // 未知装饰器打开 fallback；之后的 @@@activate 按转义还原成 @@activate 记录
        val unknownThenEscape = parseWorldInfoDecorators("@@unknown\n@@@activate\nBODY")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), unknownThenEscape.decorators)
        assertEquals("BODY", unknownThenEscape.content)

        // 官方 isKnownDecorator 用 startsWith：@@activate_extra 会被记录，但 includes 判定不激活
        val suffix = parseWorldInfoDecorators("@@activate_extra\nBODY")
        assertEquals(listOf("@@activate_extra"), suffix.decorators)
        assertEquals("BODY", suffix.content)
    }

    @Test
    fun `derived fields do not touch stored content`() {
        val entry = decoratorEntry("e", "@@activate\nFORCED")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), entry.worldInfoDecorators)
        assertEquals("FORCED", entry.contentWithoutDecorators)
        // 派生读取不得改写存储内容
        assertEquals("@@activate\nFORCED", entry.content)
    }

    // ==================== 扫描语义 ====================

    @Test
    fun `activate decorator forces entry without keywords`() {
        val joined = scan(listOf(decoratorEntry("forced", "@@activate\nFORCED-CONTENT")))
        assertTrue("无关键词也应按 @@activate 注入：$joined", joined.contains("FORCED-CONTENT"))
        assertFalse("装饰器行不应进入提示词", joined.contains("@@activate"))
    }

    @Test
    fun `dont activate decorator suppresses entry with matching keyword`() {
        val joined = scan(
            listOf(decoratorEntry("blocked", "@@dont_activate\nBLOCKED-CONTENT", keywords = listOf("hello")))
        )
        assertFalse("命中关键词也必须被 @@dont_activate 压制：$joined", joined.contains("BLOCKED-CONTENT"))
    }

    @Test
    fun `decorator precedence activate wins over dont activate`() {
        // 官方判定顺序：@@activate 在前（先 continue），@@dont_activate 在后
        val joined = scan(
            listOf(decoratorEntry("both", "@@activate\n@@dont_activate\nBOTH-CONTENT"))
        )
        assertTrue("@@activate 优先：$joined", joined.contains("BOTH-CONTENT"))
    }

    @Test
    fun `decorator comparison is exact and case sensitive`() {
        // 官方 includes()/startsWith 都区分大小写：@@ACTIVATE 不是已知装饰器。
        // 官方 parseDecorators 对「未知 @@ 行」的处理是：置 fallback 标记、该行不计入装饰器，
        // 后续正文从第一个非 @@ 行开始 —— 即 @@ACTIVATE 行被丢掉、条目按普通关键词条目参与扫描。
        val entry = decoratorEntry("upper", "@@ACTIVATE\nUPPER-CONTENT", keywords = listOf("hello"))
        assertEquals(emptyList<String>(), entry.worldInfoDecorators)
        assertEquals("UPPER-CONTENT", entry.contentWithoutDecorators)
        val joined = scan(listOf(entry))
        assertTrue("未知装饰器行被剥离后，条目仍是普通关键词条目：$joined", joined.contains("UPPER-CONTENT"))
        assertFalse("@@ACTIVATE 行本身不应进入提示词：$joined", joined.contains("@@ACTIVATE"))
    }

    @Test
    fun `activate decorator respects disabled entries`() {
        val disabled = decoratorEntry("off", "@@activate\nDISABLED-CONTENT").copy(enabled = false)
        val joined = scan(listOf(disabled))
        assertFalse("enabled=false 的条目即使带 @@activate 也必须跳过：$joined", joined.contains("DISABLED-CONTENT"))
    }

    @Test
    fun `decorated content feeds recursion buffer stripped`() {
        // 递归缓冲使用剥壳后的正文（官方 entry.content 已被 parseDecorators 改写），
        // 因此子条目的关键词命中 "RECURSE-SEED" 而不是带装饰器的原文。
        val joined = scan(
            entries = listOf(
                decoratorEntry("seed", "@@activate\nRECURSE-SEED"),
                decoratorEntry("child", "CHILD-CONTENT", keywords = listOf("RECURSE-SEED")),
            ),
            recursive = true,
        )
        assertTrue("剥壳后的正文应参与递归扫描：$joined", joined.contains("CHILD-CONTENT"))
    }
}
