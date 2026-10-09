package me.rerere.rikkahub.ui.components.frosted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v226 D3 / v227 D3-b] 顶栏遮罩几何 + scrim ramp 回归测试。
 *
 * 钉死两条不变量：
 * 1. **绝对坐标**（v226 根因）：渐变 `startY/endY` 必须是 DrawScope 绝对坐标。写成
 *    `0f..fadeHeightPx` 会让整块渐变矩形落在渐变定义域之外 -> `TileMode.Clamp` 取末色标
 *    `alpha = 0` -> 渐变段全透明（v222~v225 两轮真机「看不到顶栏遮罩」的真实根因）。
 * 2. **无实色段 / 顶部半透明**（v227 D3-b）：渐变必须从 `y = 0` 起，且顶部 scrim 不透明度 < 1。
 *    任何恒定的不透明带都会让主人看到「一条灰带 + 硬边」（v226 真机实测：y=0..190 std=0.0，
 *    y≈200 硬边跳到壁纸本色；而主人要的参照图是顶栏区壁纸可见、连续过渡）。
 */
class ChatTopFadeGeometryTest {

    @Test
    fun scrimGradientStartsAtTheVeryTop() {
        // 真机实测：1dp = 2.8125px，topInset ≈ 88dp = 247.5px，fadeHeight = 88dp = 247.5px
        val g = chatTopFadeGeometry(topInsetPx = 247.5f, fadeHeightPx = 247.5f)
        assertEquals(0f, g.gradientStartY, 0.001f)
        assertEquals(495.0f, g.gradientEndY, 0.001f)
        assertEquals(247.5f, g.fadeHeightPx, 0.001f)
    }

    @Test
    fun gradientBandIsAnchoredInAbsoluteDrawScopeCoordinates() {
        val g = chatTopFadeGeometry(topInsetPx = 100f, fadeHeightPx = 40f)
        // 旧实现把渐变矩形画在 y=solid..solid+fade，却把 startY 写成 0f（坐标空间错位）—— 回归防线
        assertEquals(140f, g.gradientEndY, 0.001f)
        assertTrue("gradient must be a non-empty band", g.gradientStartY < g.gradientEndY)
    }

    @Test
    fun gradientBandAlwaysReachesTopInsetPlusFadeHeight() {
        // 任意 topInset 下，下边界都必须是 topInset + fadeHeight（顶栏区整体被 scrim 覆盖）
        assertEquals(347.5f, chatTopFadeGeometry(247.5f, 100f).gradientEndY, 0.001f)
        assertEquals(100f, chatTopFadeGeometry(0f, 100f).gradientEndY, 0.001f)
    }

    @Test
    fun zeroFadeHeightStillCoversTheTopInset() {
        // v227 语义变化：fadeHeight = 0 不再是「空渐变」，而是「scrim 只覆盖顶栏区、不留尾巴」
        val g = chatTopFadeGeometry(topInsetPx = 247.5f, fadeHeightPx = 0f)
        assertEquals(0f, g.gradientStartY, 0.001f)
        assertEquals(247.5f, g.gradientEndY, 0.001f)
    }

    @Test
    fun rampIsMonotonicAndEndsFullyTransparent() {
        val stops = chatTopFadeRampStops()
        assertEquals(CHAT_TOP_FADE_SCRIM_ALPHA, stops.first().second, 0.0001f)
        assertEquals(0f, stops.last().second, 0.0001f)
        stops.zipWithNext { a, b ->
            assertTrue("alpha must not increase: ${a.second} -> ${b.second}", b.second <= a.second)
            assertTrue("t must increase", b.first > a.first)
        }
    }

    @Test
    fun rampTopAlphaIsStrictlyBelowOneSoWallpaperShowsThrough() {
        // v227 D3-b 的核心断言：顶部**不允许**是全不透明 —— 否则就是 v226 那条「不透明实色带」。
        assertTrue(CHAT_TOP_FADE_SCRIM_ALPHA < 1f)
        val maxAlpha = chatTopFadeRampStops().maxOf { it.second }
        assertTrue("max scrim alpha must stay < 1f, was $maxAlpha", maxAlpha < 1f)
        assertNotEquals(1f, maxAlpha, 0.0001f)
    }

    @Test
    fun rampSpansTheWholeUnitInterval() {
        val stops = chatTopFadeRampStops(stops = 8)
        assertEquals(8, stops.size)
        assertEquals(0f, stops.first().first, 0.0001f)
        assertEquals(1f, stops.last().first, 0.0001f)
    }

    @Test
    fun rampIsSmoothInTheMiddleSoNoVisibleBandEdge() {
        // 平滑性：中段相邻档的 alpha 落差必须小于首档落差（否则会出现肉眼可见的「带边」）。
        val stops = chatTopFadeRampStops()
        val firstStep = stops[0].second - stops[1].second
        val midStep = stops[stops.size / 2].second - stops[stops.size / 2 + 1].second
        assertTrue("mid step $midStep should be <= first step $firstStep", midStep <= firstStep)
    }

    @Test
    fun rampClampsOutOfRangeTopAlpha() {
        assertEquals(1f, chatTopFadeRampStops(topAlpha = 2.5f).first().second, 0.0001f)
        assertEquals(0f, chatTopFadeRampStops(topAlpha = -1f).first().second, 0.0001f)
    }

    // ==================== [v229 P1-4] 两版顶栏参数 ====================

    /** 业主描述版：上/中段背景可见（alpha 恒 0），下段从下往上渐浓，末档 = bottomAlpha 且 < 1。 */
    @Test
    fun bottomAnchoredProfileKeepsBackgroundVisibleThenFadesUp() {
        val stops = chatTopFadeProfileStops(ChatTopFadeStyle.BOTTOM_ANCHORED, bottomAlpha = 0.42f)
        assertTrue(stops.size >= 2)
        assertEquals(0f, stops.first().second, 0.0001f)
        // [v229b] 两端都必须归零（顶栏上缘 + 渐变带下缘均无硬边）
        assertEquals(0f, stops.last().second, 0.0001f)
        // 前 30% 区间必须恒 0（背景完整可见）
        stops.forEach { (t, alpha) ->
            if (t <= CHAT_TOP_FADE_BG_VISIBLE_FRACTION) {
                assertEquals("t=$t 应为背景可见区间", 0f, alpha, 0.0001f)
            }
        }
        // 必须有可见的遮盖（否则 = v227「同色叠加不可见」）且峰值 < 1（不能出现实色带）
        assertTrue("必须有可见的遮盖", stops.any { it.second > 0.1f })
        assertTrue("峰值必须 < 1", stops.maxOf { it.second } < 1f)
    }

    /** Kelivo 实测版：上浓下淡，首档 = topAlpha、末档恒 0、整体单调不增；且顶部必须 < 1（不能是实色带）。 */
    @Test
    fun kelivoTopDownProfileFadesDownAndNeverHasAContinuousOpaqueBand() {
        val stops = chatTopFadeProfileStops(ChatTopFadeStyle.KELIVO_TOP_DOWN, topAlpha = 0.74f)
        assertEquals(0.74f, stops.first().second, 0.0001f)
        assertEquals(0f, stops.last().second, 0.0001f)
        assertTrue("顶部 alpha 必须 < 1（=1 即 v226 的不透明实色带）", stops.first().second < 1f)
        stops.zipWithNext { a, b -> assertTrue("alpha 不应回升: $a -> $b", b.second <= a.second) }
    }

    /** 两版都必须有非平凡过渡（不能全是 0 或全是同值）：否则真机看不到任何渐变。 */
    @Test
    fun bothProfilesHaveANonTrivialTransition() {
        ChatTopFadeStyle.entries.forEach { style ->
            val alphas = chatTopFadeProfileStops(style).map { it.second }
            assertTrue("$style 的 alpha 不应恒定", alphas.distinct().size > 1)
            assertTrue("$style 必须存在 alpha = 0 的档（背景可见）", alphas.any { it == 0f })
        }
    }

    /** 上/中段比例/浓度越界时必须被夹住（防御性下限/上限），不能抛异常。 */
    @Test
    fun bottomAnchoredProfileClampsOutOfRangeInputs() {
        val tooBig = chatTopFadeProfileStops(
            ChatTopFadeStyle.BOTTOM_ANCHORED,
            bottomAlpha = 9f,
            bgVisibleFraction = 5f,
        )
        assertTrue(tooBig.last().second < 1f)
        assertTrue(tooBig.first().second == 0f)
        val negative = chatTopFadeProfileStops(ChatTopFadeStyle.BOTTOM_ANCHORED, bottomAlpha = -3f)
        assertTrue(negative.all { it.second == 0f })
    }

    /**
     * 红线（业主 2026-10-04 明确纠正）：「屏幕分 6 等份」只是**打比方**。
     * 这里锁死两版高度都是**有限绝对 dp 常量**，与任何屏幕尺寸无关。
     */
    @Test
    fun bothPresetHeightsAreAbsoluteConstantsNotScreenRatios() {
        listOf(CHAT_TOP_FADE_DP_TOP_DOWN, CHAT_TOP_FADE_DP_BOTTOM_ANCHORED).forEach { d ->
            assertTrue("高度必须是正数", d.value > 0f)
            assertTrue("高度不该超过半屏（说明写成了比例）", d.value < 400f)
        }
    }
}
