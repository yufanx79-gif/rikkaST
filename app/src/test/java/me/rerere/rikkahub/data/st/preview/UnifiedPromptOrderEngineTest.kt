package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPromptOrderEngineTest {

    @Test
    fun `空 orderedIdentifiers 退回默认 12 marker 顺序`() {
        val slots = mapOf(
            "chatHistory" to listOf(UIMessage.user("hi")),
            "main" to listOf(UIMessage.system("sys")),
            "charDescription" to listOf(UIMessage.system("char")),
            "dialogueExamples" to listOf(UIMessage.system("ex")),
        )
        val out = UnifiedPromptOrderEngine.assembleSlotsInOrder(
            orderedIdentifiers = emptyList(),
            disabledIdentifiers = emptySet(),
            slotMessages = slots,
        )
        assertEquals(listOf("sys", "char", "ex", "hi"), out.map { it.toText() })
    }

    @Test
    fun `自定义 12 marker 任意顺序严格按 prompt_order 生效`() {
        val slots = mapOf(
            "main" to listOf(UIMessage.system("main")),
            "charDescription" to listOf(UIMessage.system("char")),
            "dialogueExamples" to listOf(UIMessage.system("ex")),
            "chatHistory" to listOf(UIMessage.user("chat")),
            "jailbreak" to listOf(UIMessage.system("jb")),
        )
        val customOrder = listOf("chatHistory", "dialogueExamples", "jailbreak", "charDescription", "main")
        assertTrue(UnifiedPromptOrderEngine.hasCustomMarkerOrder(customOrder))
        val out = UnifiedPromptOrderEngine.assembleSlotsInOrder(
            orderedIdentifiers = customOrder,
            disabledIdentifiers = emptySet(),
            slotMessages = slots,
        )
        assertEquals(listOf("chat", "ex", "jb", "char", "main"), out.map { it.toText() })
    }

    @Test
    fun `重复 identifier 与缺失 chatHistory 不死循环不重复不丢消息`() {
        val slots = mapOf(
            "main" to listOf(UIMessage.system("main")),
            "chatHistory" to listOf(UIMessage.user("chat")),
        )
        val corrupted = listOf("main", "main", "unknown_slot", "main")
        val out = UnifiedPromptOrderEngine.assembleSlotsInOrder(
            orderedIdentifiers = corrupted,
            disabledIdentifiers = emptySet(),
            slotMessages = slots,
        )
        assertEquals(listOf("main", "chat"), out.map { it.toText() })
    }

    @Test
    fun `禁用 marker 被正确跳过且默认顺序检测无误报`() {
        assertFalse(UnifiedPromptOrderEngine.hasCustomMarkerOrder(ST_BUILTIN_MARKERS))
        val slots = mapOf(
            "main" to listOf(UIMessage.system("main")),
            "dialogueExamples" to listOf(UIMessage.system("ex")),
            "chatHistory" to listOf(UIMessage.user("chat")),
        )
        val out = UnifiedPromptOrderEngine.assembleSlotsInOrder(
            orderedIdentifiers = listOf("main", "dialogueExamples", "chatHistory"),
            disabledIdentifiers = setOf("dialogueExamples"),
            slotMessages = slots,
        )
        assertEquals(listOf("main", "chat"), out.map { it.toText() })
    }
}
