package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 1 回归测试：带优先级的淘汰 + 内存分级 + 快照 key 扩展。
 *
 * 旧常量语义不变（见 activePanelWebViewPoolAndSchedulerEnforceDefaultBoundsAndCleanCancel），
 * 这里只验证新增能力。
 */
class PanelPoolPriorityTest {

    @Test
    fun evictDropsLowestPriorityFirstInsteadOfPureLru() {
        ActivePanelWebViewPool.resetForTest()

        ActivePanelWebViewPool.acquire("floor1", maxActive = 6)
        ActivePanelWebViewPool.acquire("floor2", maxActive = 6)
        ActivePanelWebViewPool.acquire("latest", maxActive = 6, priority = PanelPoolPriority.LATEST_FLOOR)
        ActivePanelWebViewPool.acquire("floor3", maxActive = 6, priority = PanelPoolPriority.VISIBLE)
        assertEquals(4, ActivePanelWebViewPool.activeCount())

        // 上限 3：两个 BACKGROUND 必须优先于 LATEST_FLOOR / VISIBLE 被淘汰
        ActivePanelWebViewPool.acquire("newcomer", maxActive = 3)

        val keys = ActivePanelWebViewPool.activeKeysFlow.value
        assertEquals(3, keys.size)
        assertFalse("background oldest (floor1) evicted first", "floor1" in keys)
        assertFalse("background (floor2) evicted second", "floor2" in keys)
        assertTrue("latest floor must survive", "latest" in keys)
        assertTrue("visible floor must survive", "floor3" in keys)
        assertTrue("newcomer must survive", "newcomer" in keys)
        ActivePanelWebViewPool.clearAll()
    }

    @Test
    fun normalCandidatesAreEvictedBeforePinnedOnes() {
        ActivePanelWebViewPool.resetForTest()
        ActivePanelWebViewPool.acquire("latest", maxActive = 6, priority = PanelPoolPriority.LATEST_FLOOR)
        ActivePanelWebViewPool.acquire("v1", maxActive = 6, priority = PanelPoolPriority.VISIBLE)
        ActivePanelWebViewPool.acquire("n1", maxActive = 6)
        ActivePanelWebViewPool.acquire("n2", maxActive = 6)

        ActivePanelWebViewPool.acquire("n3", maxActive = 3)
        val keys = ActivePanelWebViewPool.activeKeysFlow.value
        assertEquals(3, keys.size)
        assertTrue("latest must survive while normals exist", "latest" in keys)
        assertTrue("visible must survive while normals exist", "v1" in keys)
        assertTrue("newest normal survives", "n3" in keys)
        assertFalse("n1 evicted", "n1" in keys)
        assertFalse("n2 evicted", "n2" in keys)
        ActivePanelWebViewPool.clearAll()
    }

    @Test
    fun touchRefreshesRecencyWithinSamePriority() {
        ActivePanelWebViewPool.resetForTest()
        ActivePanelWebViewPool.acquire("a", maxActive = 6)
        ActivePanelWebViewPool.acquire("b", maxActive = 6)
        ActivePanelWebViewPool.touch("a")
        ActivePanelWebViewPool.acquire("c", maxActive = 2)
        val keys = ActivePanelWebViewPool.activeKeysFlow.value
        assertEquals(2, keys.size)
        assertTrue("touched key survives", "a" in keys)
        assertFalse("untouched older key evicted", "b" in keys)
        ActivePanelWebViewPool.clearAll()
    }

    @Test
    fun updatePriorityChangesEvictionOrder() {
        ActivePanelWebViewPool.resetForTest()
        ActivePanelWebViewPool.acquire("a", maxActive = 6)
        ActivePanelWebViewPool.acquire("b", maxActive = 6)
        ActivePanelWebViewPool.updatePriority("a", PanelPoolPriority.LATEST_FLOOR)
        ActivePanelWebViewPool.acquire("c", maxActive = 2)
        val keys = ActivePanelWebViewPool.activeKeysFlow.value
        assertEquals(2, keys.size)
        assertTrue("a upgraded to LATEST_FLOOR survives", "a" in keys)
        assertFalse("b background evicted", "b" in keys)
        ActivePanelWebViewPool.clearAll()
    }

    @Test
    fun memoryTierScalesCapByTotalRam() {
        assertEquals(16, ActivePanelWebViewPool.resolveMemoryTierMaxActive(12L * 1024 * 1024 * 1024))
        assertEquals(16, ActivePanelWebViewPool.resolveMemoryTierMaxActive(16L * 1024 * 1024 * 1024))
        assertEquals(8, ActivePanelWebViewPool.resolveMemoryTierMaxActive(9L * 1024 * 1024 * 1024))
        assertEquals(4, ActivePanelWebViewPool.resolveMemoryTierMaxActive(6L * 1024 * 1024 * 1024))
        assertEquals(4, ActivePanelWebViewPool.resolveMemoryTierMaxActive(0L))
    }

    @Test
    fun userOverrideWinsOverMemoryTierAndIsClamped() {
        // 用户手动覆盖优先，且夹在 1..16
        assertEquals(7, ActivePanelWebViewPool.resolveMemoryTierMaxActive(0L, userOverride = 7))
        assertEquals(16, ActivePanelWebViewPool.resolveMemoryTierMaxActive(0L, userOverride = 99))
        // <=0 表示「自动档」，不是夹到 1；总内存 0 时回退到保守档 4
        assertEquals(4, ActivePanelWebViewPool.resolveMemoryTierMaxActive(0L, userOverride = -5))
        assertEquals(4, ActivePanelWebViewPool.resolveMemoryTierMaxActive(0L, userOverride = 0))
    }

    @Test
    fun hibernatePoolNoLongerHardCappedAtTwoOnHighMemoryDevice() {
        // 缺陷修复回归：方案一（默认模式）在高内存机上不再被压到 2
        val highMem = 12L * 1024 * 1024 * 1024
        val cap = ActivePanelWebViewPool.resolveEffectiveMaxActive(
            totalMemBytes = highMem,
            perfEnabled = true,
            perfMode = me.rerere.rikkahub.data.datastore.WebViewPerfMode.HIBERNATE_POOL,
        )
        assertEquals(
            "12GB 设备在方案一下应拿到 16 而不是 2",
            16,
            cap,
        )
        // 低内存机仍收紧到 2，保住安全边际
        val lowMemCap = ActivePanelWebViewPool.resolveEffectiveMaxActive(
            totalMemBytes = 4L * 1024 * 1024 * 1024,
            perfEnabled = true,
            perfMode = me.rerere.rikkahub.data.datastore.WebViewPerfMode.HIBERNATE_POOL,
        )
        assertEquals(2, lowMemCap)
    }

    @Test
    fun snapshotCompositeKeyIncludesThemeWidthAndFontScale() {
        val k1 = PanelSnapshotStore.compositeKey("msg1", 123, isDarkTheme = true, widthPx = 1080, fontScale = 1.0f)
        val k2 = PanelSnapshotStore.compositeKey("msg1", 123, isDarkTheme = false, widthPx = 1080, fontScale = 1.0f)
        val k3 = PanelSnapshotStore.compositeKey("msg1", 123, isDarkTheme = true, widthPx = 720, fontScale = 1.0f)
        val k4 = PanelSnapshotStore.compositeKey("msg1", 123, isDarkTheme = true, widthPx = 1080, fontScale = 1.15f)
        assertTrue("theme must change snapshot key", k1 != null && k2 != null && k1 != k2)
        assertTrue("width must change snapshot key", k1 != k3)
        assertTrue("font scale must change snapshot key", k1 != k4)
        assertEquals(k1, PanelSnapshotStore.compositeKey("msg1", 123, isDarkTheme = true, widthPx = 1080, fontScale = 1.0f))
        assertNull(PanelSnapshotStore.compositeKey(null, 123, isDarkTheme = true, widthPx = 1080, fontScale = 1.0f))
    }

    @Test
    fun snapshotLegacyKeyStillResolvable() {
        // 旧格式 "msg#hash" 在未提供扩展维度时保持原样，磁盘上已有快照可继续命中
        assertEquals("msg1#123", PanelSnapshotStore.compositeKey("msg1", 123))
    }
}
