package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * M2 群聊成员排序纯逻辑（reorderMemberIds）单元测试 —— 对齐酒馆 reorderGroupMember 的 up/down。
 */
class GroupMemberReorderTest {

    private fun ids(vararg n: Int): List<Uuid> =
        n.map { Uuid.parse("00000000-0000-0000-0000-%012d".format(it)) }

    @Test
    fun `move up swaps with previous member`() {
        val list = ids(1, 2, 3, 4)
        assertEquals(ids(1, 3, 2, 4), reorderMemberIds(list, list[2], -1))
    }

    @Test
    fun `move down swaps with next member`() {
        val list = ids(1, 2, 3, 4)
        assertEquals(ids(1, 3, 2, 4), reorderMemberIds(list, list[1], 1))
    }

    @Test
    fun `move first down`() {
        val list = ids(1, 2, 3)
        assertEquals(ids(2, 1, 3), reorderMemberIds(list, list[0], 1))
    }

    @Test
    fun `move last up`() {
        val list = ids(1, 2, 3)
        assertEquals(ids(1, 3, 2), reorderMemberIds(list, list[2], -1))
    }

    @Test
    fun `move up at top is no-op`() {
        val list = ids(1, 2, 3)
        assertEquals(list, reorderMemberIds(list, list[0], -1))
    }

    @Test
    fun `move down at bottom is no-op`() {
        val list = ids(1, 2, 3)
        assertEquals(list, reorderMemberIds(list, list[2], 1))
    }

    @Test
    fun `unknown member is no-op`() {
        val list = ids(1, 2, 3)
        assertEquals(list, reorderMemberIds(list, Uuid.random(), 1))
    }

    @Test
    fun `single element list is no-op`() {
        val list = ids(1)
        assertEquals(list, reorderMemberIds(list, list[0], -1))
        assertEquals(list, reorderMemberIds(list, list[0], 1))
    }

    @Test
    fun `overshoot delta is no-op`() {
        val list = ids(1, 2, 3)
        assertEquals(list, reorderMemberIds(list, list[1], 5))
        assertEquals(list, reorderMemberIds(list, list[1], -5))
    }

    @Test
    fun `no-op returns same instance`() {
        val list = ids(1, 2, 3)
        assertSame(list, reorderMemberIds(list, list[0], -1))
    }

    @Test
    fun `d12 group greetings char macro replacement and multi speaker history order`() {
        // 1. groupOnlyGreetings 优先于普通 firstMessage / alternateGreetings
        val tavWithGroup = me.rerere.rikkahub.data.model.TavernCharacterData(
            firstMessage = "Normal First",
            alternateGreetings = listOf("Alt 1"),
            groupOnlyGreetings = listOf("Group Exclusive 1", "Group Exclusive 2"),
        )
        assertEquals(listOf("Group Exclusive 1", "Group Exclusive 2"), selectGroupGreetingPool(tavWithGroup))

        val tavWithoutGroup = me.rerere.rikkahub.data.model.TavernCharacterData(
            firstMessage = "Normal First",
            alternateGreetings = listOf("Alt 1"),
            groupOnlyGreetings = emptyList(),
        )
        assertEquals(listOf("Normal First", "Alt 1"), selectGroupGreetingPool(tavWithoutGroup))

        // 2. APPEND 模式下拼接成员角色卡字段前先将 {{char}} 替换为对应成员自己的名字
        val alice = me.rerere.rikkahub.data.model.Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000011"),
            name = "Alice",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(
                name = "Alice",
                description = "{{char}} is a mage.",
                personality = "{{char}} is calm.",
            ),
        )
        val bob = me.rerere.rikkahub.data.model.Assistant(
            id = Uuid.parse("00000000-0000-0000-0000-000000000022"),
            name = "Bob",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(
                name = "Bob",
                description = "{{char}} is a warrior.",
                personality = "{{char}} is brave.",
            ),
        )
        val gc = me.rerere.rikkahub.data.model.GroupChat(
            name = "Party",
            memberIds = listOf(alice.id, bob.id),
            generationMode = me.rerere.rikkahub.data.model.GroupGenerationMode.APPEND,
        )
        val mergedSpeaker = buildGenerationSpeaker(gc, listOf(alice, bob), alice)
        assertEquals("Alice is a mage.\nBob is a warrior.", mergedSpeaker.tavernData?.description)
        assertEquals("Alice is calm.\nBob is brave.", mergedSpeaker.tavernData?.personality)

        // 3. 同轮多角色依次发言时序保持：第 1 个角色拆出末尾用户消息作为 prompt，第 2 个角色保持原序且 prompt 为空
        val h1 = listOf(
            me.rerere.ai.ui.UIMessage.assistant("Alice: Welcome"),
            me.rerere.ai.ui.UIMessage.user("Hello everyone"),
        )
        val (hist1, prompt1) = prepareGroupGenerationHistoryAndPrompt(h1, "Hello everyone")
        assertEquals(1, hist1.size)
        assertEquals("Hello everyone", prompt1)

        val h2 = listOf(
            me.rerere.ai.ui.UIMessage.assistant("Alice: Welcome"),
            me.rerere.ai.ui.UIMessage.user("Hello everyone"),
            me.rerere.ai.ui.UIMessage.assistant("Alice: Hi there!"),
        )
        val (hist2, prompt2) = prepareGroupGenerationHistoryAndPrompt(h2, "Hello everyone")
        assertEquals(3, hist2.size)
        assertEquals("", prompt2)
        assertEquals(me.rerere.ai.core.MessageRole.USER, hist2[1].role)
        assertEquals(me.rerere.ai.core.MessageRole.ASSISTANT, hist2[2].role)
    }
}
