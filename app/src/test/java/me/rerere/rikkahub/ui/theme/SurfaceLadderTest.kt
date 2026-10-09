package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v217 / A2：Surface Ladder 的纯计算回归。
 *
 * 这些数字全部来自 Kelivo 1.3.0 的 lib/theme/surface_ladder.dart（只参考机制，未复制代码），
 * 详见 notes/spec-kelivo-port-20261002.md 第 3、4 节。谁把公式改坏，这里会红。
 */
class SurfaceLadderTest {

    private val seed = Color(0xFF6750A4)

    // ---------------- 四个原语 ----------------

    @Test
    fun shiftToneClampsIntoZeroToHundred() {
        assertEquals(100.0, surfaceTone(shiftTone(seed, 1000.0)), 0.5)
        assertEquals(0.0, surfaceTone(shiftTone(seed, -1000.0)), 0.5)
    }

    @Test
    fun shiftToneKeepsHueAndChroma() {
        val base = Hct.fromInt(seed.toArgb())
        val shifted = Hct.fromInt(shiftTone(seed, 7.0).toArgb())

        assertEquals(base.hue, shifted.hue, 1.0)
        assertEquals(base.chroma, shifted.chroma, 1.0)
        assertEquals(base.tone + 7.0, shifted.tone, 0.5)
    }

    @Test
    fun atToneForcesTheExactTone() {
        assertEquals(42.0, surfaceTone(atTone(seed, 42.0)), 0.5)
        // 越界 tone 要 clamp
        assertEquals(100.0, surfaceTone(atTone(seed, 999.0)), 0.5)
    }

    /**
     * 最容易实现错的一条：lerpTone 只在 tone 轴上插值，hue/chroma 取 from。
     * 如果被实现成颜色插值，这条会红。
     */
    @Test
    fun lerpToneInterpolatesToneOnlyAndKeepsFromHueChroma() {
        val from = Color(0xFF6750A4)
        val to = Color(0xFFFFD700) // 完全不同的色相（金）

        val mid = lerpTone(from, to, 0.5)
        val fromHct = Hct.fromInt(from.toArgb())
        val toHct = Hct.fromInt(to.toArgb())
        val midHct = Hct.fromInt(mid.toArgb())

        assertEquals("hue 必须来自 from", fromHct.hue, midHct.hue, 1.0)
        assertEquals("chroma 必须来自 from", fromHct.chroma, midHct.chroma, 1.5)
        assertEquals("tone 取中点", (fromHct.tone + toHct.tone) / 2, midHct.tone, 0.5)
    }

    // ---------------- 页面下沉 ----------------

    @Test
    fun lightPageSinksFourTones() {
        val surface = Color(0xFFFDF7FF)
        val page = applyPageSurface(surface, isDark = false)

        assertEquals(surfaceTone(surface) - 4.0, surfaceTone(page), 0.5)
    }

    @Test
    fun darkPageIsNeverSunk() {
        val surface = Color(0xFF141218)
        assertEquals(surface, applyPageSurface(surface, isDark = true))
    }

    @Test
    fun pureWhitePageSkipsTheSink() {
        assertEquals(Color.White, applyPageSurface(Color.White, isDark = false))
    }

    @Test
    fun nonLayeredPageIsNotSunk() {
        val surface = Color(0xFFFDF7FF)
        assertEquals(surface, applyPageSurface(surface, isDark = false, layered = false))
    }

    // ---------------- 阶梯 ----------------

    /**
     * 亮色阶梯的真实顺序（按 tone 降序）：
     *   Lowest(100) > High(=card) > Container > Low > page > Highest(=surfaceFill)
     *
     * 注意这**不是** M3 的 Low/Container/High 顺序，也不是 spec-kelivo-port 初稿写的
     * 「Lowest > Low > Container > High」——那句写反了（已在 2026-10-02 更正）。
     * 亮色下 Low/Container 是在 page↔card 之间 lerpTone 插值，而 High 直接 = card，
     * 所以 High 比 Container / Low 都亮。以 surface_ladder.dart:121-133 为准。
     */
    @Test
    fun lightLadderOrderAndCardFloatsAbovePage() {
        val page = applyPageSurface(Color(0xFFFDF7FF), isDark = false)
        val ladder = surfaceLadderFromScheme(
            surface = page,
            onSurface = Color(0xFF1D1B20),
            outlineVariant = Color(0xFFCAC4D0),
            isDark = false,
        )

        val pageTone = surfaceTone(ladder.page)
        val lowest = surfaceTone(ladder.surfaceContainerLowest)
        val high = surfaceTone(ladder.surfaceContainerHigh)
        val container = surfaceTone(ladder.surfaceContainer)
        val low = surfaceTone(ladder.surfaceContainerLow)
        val highest = surfaceTone(ladder.surfaceContainerHighest)

        assertEquals("High 必须等于 card", surfaceTone(ladder.card), high, 0.01)
        assertEquals("Highest 必须等于 surfaceFill", surfaceTone(ladder.surfaceFill), highest, 0.01)

        assertTrue("亮色 card 必须比 page 亮（卡片浮在页面上），实际 card=$high page=$pageTone", high > pageTone)
        assertTrue("亮色 surfaceFill 必须比 page 暗，实际 fill=$highest page=$pageTone", highest < pageTone)

        assertTrue("Lowest 必须是最亮的一档，实际 $lowest", lowest > high)
        assertTrue("High(=card) 必须比 Container 亮，实际 high=$high container=$container", high > container)
        assertTrue("Container 必须比 Low 亮（同一条 lerp 上 t 更大），实际 $container vs $low", container > low)
        assertTrue("Low 必须比 page 亮，实际 low=$low page=$pageTone", low > pageTone)
        assertTrue("Highest(=surfaceFill) 必须是最暗的一档，实际 $highest", highest < low)
    }

    @Test
    fun lightCardFallsBackDownwardWhenThereIsNoHeadroom() {
        // page 已经到 tone 99，向上没有空间 -> card 必须向下取，不能和 page 同色
        val page = atTone(seed, 99.0)
        val ladder = surfaceLadderFromScheme(
            surface = page,
            onSurface = Color(0xFF1D1B20),
            outlineVariant = Color(0xFFCAC4D0),
            isDark = false,
        )

        assertTrue("无 headroom 时 card 必须比 page 暗，实际 card=${surfaceTone(ladder.card)} page=${surfaceTone(ladder.page)}",
            surfaceTone(ladder.card) < surfaceTone(ladder.page))
    }

    @Test
    fun darkLadderIsFiveConsecutiveShifts() {
        val page = Color(0xFF141218)
        val ladder = surfaceLadderFromScheme(
            surface = page,
            onSurface = Color(0xFFE6E0E9),
            outlineVariant = Color(0xFF49454F),
            isDark = true,
        )

        val t = surfaceTone(page)
        assertEquals(t - 3.0, surfaceTone(ladder.surfaceContainerLowest), 0.5)
        assertEquals(t + 4.0, surfaceTone(ladder.surfaceContainerLow), 0.5)
        assertEquals(t + 7.0, surfaceTone(ladder.surfaceContainer), 0.5)
        assertEquals(t + 11.0, surfaceTone(ladder.surfaceContainerHigh), 0.5)
        assertEquals(t + 14.0, surfaceTone(ladder.surfaceContainerHighest), 0.5)
        assertEquals("暗色 card == surfaceContainerHigh", t + 11.0, surfaceTone(ladder.card), 0.5)
    }

    /**
     * 换一个完全不同的种子色（青），card 必须保住页面的色相与彩度。
     *
     * 容差说明：`Hct.from(hue, chroma, tone)` 在目标 tone 下若超出 sRGB 色域，会由
     * HctSolver 做「色域回落」（降彩度、可能轻微挪色相），这是预期行为（spec 第 3.4 节第 5 条）。
     * 所以这里只锁「彩度不掉、色相不跑」这两个真正影响主题一致性的性质。
     */
    @Test
    fun ladderKeepsTheThemeIdentityOfThePage() {
        val page = applyPageSurface(Color(0xFF00696D), isDark = false)
        val ladder = surfaceLadderFromScheme(
            surface = page,
            onSurface = Color(0xFF1D1B20),
            outlineVariant = Color(0xFFCAC4D0),
            isDark = false,
        )
        val pageHct = Hct.fromInt(page.toArgb())
        val cardHct = Hct.fromInt(ladder.card.toArgb())

        assertTrue(
            "card 的彩度不能掉成灰（page=${pageHct.chroma} card=${cardHct.chroma}）",
            cardHct.chroma >= pageHct.chroma * 0.7,
        )
        assertTrue(
            "card 的色相不能跑（page=${pageHct.hue} card=${cardHct.hue}）",
            hueDistance(pageHct.hue, cardHct.hue) < 15.0,
        )
    }

    /** 色相是环形的，差值要取最短弧。 */
    private fun hueDistance(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b) % 360.0
        return kotlin.math.min(d, 360.0 - d)
    }
}
