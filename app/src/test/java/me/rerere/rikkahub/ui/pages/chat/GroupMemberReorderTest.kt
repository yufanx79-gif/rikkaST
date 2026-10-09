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
}
