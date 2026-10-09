package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.st.macro.MacroDefinitions
import me.rerere.rikkahub.data.st.macro.MacroEngine
import me.rerere.rikkahub.data.st.macro.MacroEnv
import me.rerere.rikkahub.data.st.macro.MacroRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 世界书 outlet（官方 position=7 + extensions.outlet_name）语义测试。
 *
 * 金标准来源：SillyTavern 1.18.0
 * - world-info.js `setExtensionPrompt(extension_prompts.CUSTOM_WI_OUTLET(key), value.join('\n'), NONE, 0)`：
 *   outlet 条目激活后不注入任何位置，按 outlet_name 汇聚（同名 "\n" 连接）；
 * - macros.js `getOutletPrompt(key)`：`{{outlet::key}}` 读取注入槽，未激活时为空串。
 */
class WorldInfoOutletTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerMacros() {
            MacroRegistry.clear()
            MacroDefinitions.registerAll()
        }
    }

    private fun textOf(messages: List<UIMessage>): String =
        messages.joinToString("\n") { m ->
            m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        }

    private fun outletEntry(
        name: String,
        outlet: String,
        content: String,
        priority: Int = 1,
    ): PromptInjection.RegexInjection = PromptInjection.RegexInjection(
        name = name,
        enabled = true,
        priority = priority,
        position = InjectionPosition.OUTLET,
        content = content,
        constantActive = true,
        ignoreBudget = true, // 测试聚焦 outlet 汇聚语义，避开短消息预算估算干扰
        outletName = outlet,
    )

    // ==================== 扫描 → 汇聚发布 ====================

    @Test
    fun `outlet entries publish to sink joined by newline in priority order`() {
        val bookId = Uuid.random()
        val book = Lorebook(
            id = bookId,
            name = "book",
            enabled = true,
            entries = listOf(
                outletEntry("o1", "states", "OUT-A", priority = 1),
                outletEntry("o2", "states", "OUT-B", priority = 2),
                PromptInjection.RegexInjection(
                    name = "normal",
                    enabled = true,
                    priority = 1,
                    position = InjectionPosition.AFTER_CHARACTER,
                    content = "INJ-C",
                    constantActive = true,
                    ignoreBudget = true,
                ),
            ),
        )
        val assistant = Assistant(name = "char", lorebookIds = setOf(bookId))
        val publishes = mutableListOf<Map<String, String>>()

        val result = transformMessages(
            messages = listOf(UIMessage.user("hello")),
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
            outletSink = { publishes.add(it) },
        )

        // outlet 汇聚：同名 "\n" 连接，priority 升序
        assertEquals(mapOf("states" to "OUT-A\nOUT-B"), publishes.last())

        // outlet 内容不注入任何位置；普通条目照常注入
        val joined = textOf(result)
        assertTrue("普通条目应注入", joined.contains("INJ-C"))
        assertFalse("outlet 内容不得注入", joined.contains("OUT-A"))
        assertFalse("outlet 内容不得注入", joined.contains("OUT-B"))
    }

    @Test
    fun `no activated outlets publishes empty map to clear stale state`() {
        val publishes = mutableListOf<Map<String, String>>()
        transformMessages(
            messages = listOf(UIMessage.user("hi")),
            assistant = Assistant(name = "char"),
            modeInjections = emptyList(),
            lorebooks = emptyList(),
            outletSink = { publishes.add(it) },
        )
        assertEquals(1, publishes.size)
        assertTrue(publishes.last().isEmpty())
    }

    @Test
    fun `outlet entry without outlet name is ignored`() {
        val bookId = Uuid.random()
        val entry = outletEntry("broken", outlet = "", content = "NO-NAME")
        val book = Lorebook(id = bookId, name = "b", enabled = true, entries = listOf(entry))
        val assistant = Assistant(name = "char", lorebookIds = setOf(bookId))
        val publishes = mutableListOf<Map<String, String>>()

        transformMessages(
            messages = listOf(UIMessage.user("x")),
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
            outletSink = { publishes.add(it) },
        )
        assertTrue(publishes.last().isEmpty())
    }

    // ==================== 运行时存储 ====================

    @Test
    fun `store isolates conversations and clears on empty publish`() {
        WorldInfoOutlets.publish("c1", mapOf("k" to "v1"))
        WorldInfoOutlets.publish("c2", mapOf("k" to "v2"))
        assertEquals("v1", WorldInfoOutlets.get("c1", "k"))
        assertEquals("v2", WorldInfoOutlets.get("c2", "k"))

        // key trim 兜底（酒馆宽松读取）
        assertEquals("v1", WorldInfoOutlets.get("c1", " k "))

        // 空发布清空该会话
        WorldInfoOutlets.publish("c1", emptyMap())
        assertEquals("", WorldInfoOutlets.get("c1", "k"))
        // 其他会话不受影响
        assertEquals("v2", WorldInfoOutlets.get("c2", "k"))
    }

    // ==================== {{outlet::name}} 宏 ====================

    @Test
    fun `outlet macro reads env outlets`() {
        val env = MacroEnv(outlets = mapOf("states" to "VV"))
        assertEquals("VV", MacroEngine.evaluate("{{outlet::states}}", env))
        assertEquals("VV", MacroEngine.evaluate("{{outlet:: states }}", env))
    }

    @Test
    fun `outlet macro returns empty for missing key`() {
        val env = MacroEnv(outlets = mapOf("states" to "VV"))
        assertEquals("", MacroEngine.evaluate("{{outlet::missing}}", env))
        assertEquals("", MacroEngine.evaluate("{{outlet::missing}}", MacroEnv()))
    }
}