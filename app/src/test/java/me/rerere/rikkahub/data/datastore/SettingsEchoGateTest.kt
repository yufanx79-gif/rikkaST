package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v229 A4/P0-2] 写回门控纯函数单测。
 *
 * 背景：v227/v228 两轮「滑块松手后自己滑 / 开关弹回」都栽在写回层回声上（UI 层补丁无效）。
 * 这里锁定「本地权威值 + 落盘确认」的四条语义，防回归。
 */
class SettingsEchoGateTest {

    private fun settings(marker: Int): Settings =
        Settings.dummy().copy(chatModelId = kotlin.uuid.Uuid.parse("00000000-0000-0000-0000-%012d".format(marker)))

    @Test
    fun `idle gate accepts every upstream emission`() {
        val gate = SettingsEchoGate()
        assertTrue(gate.accept(settings(1)))
        assertTrue(gate.accept(settings(2)))
        assertEquals(0L, gate.droppedCount())
    }

    @Test
    fun `stale echo arriving after a local write is dropped`() {
        val gate = SettingsEchoGate()
        val local = settings(10)
        gate.mark(local)
        // 更早那笔写的旧快照晚到 -> 必须丢弃
        assertFalse(gate.accept(settings(9)))
        assertEquals(1L, gate.droppedCount())
        assertTrue(gate.hasPending())
    }

    @Test
    fun `confirmation of the local write clears pending and is accepted`() {
        val gate = SettingsEchoGate()
        val local = settings(10)
        gate.mark(local)
        assertTrue(gate.accept(local))
        assertFalse(gate.hasPending())
        // 确认之后的任何发射都放行（回到 idle 语义）
        assertTrue(gate.accept(settings(11)))
    }

    @Test
    fun `rapid consecutive writes only let the newest confirmation through`() {
        val gate = SettingsEchoGate()
        val v1 = settings(1)
        val v2 = settings(2)
        val v3 = settings(3)
        gate.mark(v1)
        gate.mark(v2)
        gate.mark(v3)
        // 前两笔的旧回声全部丢弃
        assertFalse(gate.accept(v1))
        assertFalse(gate.accept(v2))
        assertEquals(2L, gate.droppedCount())
        // 最新一笔的确认放行
        assertTrue(gate.accept(v3))
        assertFalse(gate.hasPending())
        assertEquals(2L, gate.droppedCount())
    }

    @Test
    fun `gate never wedges permanently even if a write is never confirmed`() {
        val gate = SettingsEchoGate()
        gate.mark(settings(1))
        assertFalse(gate.accept(settings(99)))
        // 下一笔本地写覆盖权威值 -> 立刻恢复可用
        val v2 = settings(2)
        gate.mark(v2)
        assertTrue(gate.accept(v2))
        assertTrue(gate.accept(settings(3)))
    }
}
