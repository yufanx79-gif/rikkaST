package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E9-E12 回归测试：JS 上报的内层纵向滚动区如何驱动原生手势判定。
 *
 * 判定契约（[InnerScrollZones.canScrollAt]）：
 * - null  = 触点不在任何内层滚动区 -> 调用方回退到整页探测；
 * - true  = 该滑动方向上内层还能继续滚 -> 手势留给面板；
 * - false = 落点在内层滚动区但该方向已到边界 -> 交还外层列表。
 */
class InnerScrollZonesTest {

    /** 单个滚动区：left, top, right, bottom, canUp, canDown（CSS px，视口坐标）。 */
    private fun zones(vw: Float, vararg rects: FloatArray) =
        InnerScrollZones(vw, rects.flatMap { it.toList() }.toFloatArray())

    private val roster = floatArrayOf(0f, 0f, 336f, 320f, 0f, 1f)

    @Test
    fun emptyZonesAlwaysFallBackToPageProbe() {
        // 没有任何内层滚动区：必须返回 null，否则整页面板会失去竖滑能力。
        assertNull(InnerScrollZones.EMPTY.canScrollAt(10f, 10f, fingerDown = true))
        assertNull(InnerScrollZones.EMPTY.canScrollAt(10f, 10f, fingerDown = false))
    }

    @Test
    fun hitInsideZoneUsesDirectionFlag() {
        val z = zones(336f, roster)
        // canUp=0 已到顶 -> 手指下滑（看内容上方）交给外层
        assertEquals(false, z.canScrollAt(100f, 100f, fingerDown = true))
        // canDown=1 还能往下滚 -> 手指上滑留在面板
        assertEquals(true, z.canScrollAt(100f, 100f, fingerDown = false))
    }

    @Test
    fun missOutsideZoneReturnsNullEvenWhenZonesExist() {
        val z = zones(336f, roster)
        // 横向越界
        assertNull(z.canScrollAt(400f, 100f, fingerDown = true))
        // 纵向越界（下方按钮区）
        assertNull(z.canScrollAt(100f, 400f, fingerDown = false))
    }

    @Test
    fun innermostScrollableAncestorWins() {
        // 外层已到顶不可滚(canUp=0)，内层还能滚(canUp=1)：落点同时命中两者，必须判为可滚。
        val outer = floatArrayOf(0f, 0f, 336f, 400f, 0f, 1f)
        val inner = floatArrayOf(20f, 20f, 316f, 300f, 1f, 1f)
        val z = zones(336f, outer, inner)
        assertEquals(true, z.canScrollAt(100f, 100f, fingerDown = true))
        // 落在内层之外、外层之内：只能看外层，已到顶 -> false
        assertEquals(false, z.canScrollAt(100f, 380f, fingerDown = true))
    }

    @Test
    fun viewportWidthIsPreservedForNativeUnitConversion() {
        // Native 侧用 viewportCssWidth 把触摸的 px 换成 CSS px；0 表示未知，调用方回退到 density。
        assertEquals(336f, zones(336f, roster).viewportCssWidth, 0.01f)
        assertEquals(0f, InnerScrollZones.EMPTY.viewportCssWidth, 0.01f)
    }
}
