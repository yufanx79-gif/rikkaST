package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * v220：思考强度滑杆几何的纯计算回归。
 *
 * 这些数字全部来自 Kelivo 1.3.0 的 `_EffortSlider` / `_SliderPainter`
 * （lib/features/chat/widgets/reasoning_budget_sheet.dart:471-685；只参考机制与数值，未复制代码），
 * 量测复核见 notes/recon-reasoning-slider-ref-20261003.md。
 *
 * 谁把停靠点几何、内缩约定或吸附弹簧改坏，这里会红。
 */
class ReasoningSliderSpecTest {

    /** 与实现一致的档位数（ReasoningLevel.entries.size）。 */
    private val count = 6

    /** 参考实现里的像素宽度（任意值，几何应按比例成立）。 */
    private val width = 1080f

    private val inset = ReasoningSliderSpec.StopInset.value

    // ---------------- 内缩约定 ----------------

    @Test
    fun stopInsetIsHalfThumb() {
        // Kelivo 约定：停靠点内缩 = thumb 半径。
        assertEquals(19f, inset, 1e-4f)
        assertEquals(ReasoningSliderSpec.ThumbSize.value / 2f, inset, 1e-4f)
    }

    @Test
    fun extremesAreFlushWithTrackEnds() {
        // 首档 thumb 左缘贴轨道左端、末档 thumb 右缘贴轨道右端 ——
        // 极端档位不该露出轨道残边（Kelivo 注释里的 original intent）。
        assertEquals(inset, ReasoningSliderSpec.thumbX(0f, width, count, inset), 1e-3f)
        assertEquals(
            width - inset,
            ReasoningSliderSpec.thumbX((count - 1).toFloat(), width, count, inset),
            1e-3f,
        )
    }

    @Test
    fun stopsAreEvenlySpaced() {
        val xs = (0 until count).map { ReasoningSliderSpec.stopX(it, width, count, inset) }
        val gaps = xs.zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(gaps.first(), it, 1e-3f) }
        assertTrue("间距应为正", gaps.first() > 0f)
        // 首末档之间恰好是 (count-1) 个等距间隔
        assertEquals(
            xs.last() - xs.first(),
            gaps.first() * (count - 1),
            1e-3f,
        )
    }

    // ---------------- 像素 ↔ 档位 ----------------

    @Test
    fun fractionForXInvertsStopX() {
        for (index in 0 until count) {
            val x = ReasoningSliderSpec.stopX(index, width, count, inset)
            assertEquals(
                "第 $index 档应能反解回自己",
                index.toFloat(),
                ReasoningSliderSpec.fractionForX(x, width, count, inset),
                1e-3f,
            )
        }
    }

    @Test
    fun fractionForXIsHalfwayBetweenNeighbouringStops() {
        val a = ReasoningSliderSpec.stopX(2, width, count, inset)
        val b = ReasoningSliderSpec.stopX(3, width, count, inset)
        assertEquals(2.5f, ReasoningSliderSpec.fractionForX((a + b) / 2f, width, count, inset), 1e-3f)
    }

    @Test
    fun fractionForXClampsOutsideTrack() {
        assertEquals(0f, ReasoningSliderSpec.fractionForX(-500f, width, count, inset), 1e-4f)
        assertEquals(0f, ReasoningSliderSpec.fractionForX(0f, width, count, inset), 1e-4f)
        assertEquals(
            (count - 1).toFloat(),
            ReasoningSliderSpec.fractionForX(width, width, count, inset),
            1e-4f,
        )
        assertEquals(
            (count - 1).toFloat(),
            ReasoningSliderSpec.fractionForX(width + 500f, width, count, inset),
            1e-4f,
        )
    }

    @Test
    fun thumbXClampsPositionIntoRange() {
        assertEquals(
            ReasoningSliderSpec.thumbX(0f, width, count, inset),
            ReasoningSliderSpec.thumbX(-3f, width, count, inset),
            1e-3f,
        )
        assertEquals(
            ReasoningSliderSpec.thumbX((count - 1).toFloat(), width, count, inset),
            ReasoningSliderSpec.thumbX(99f, width, count, inset),
            1e-3f,
        )
    }

    // ---------------- 退化输入 ----------------

    @Test
    fun degenerateInputsDoNotThrow() {
        // 单档 / 零档 / 零宽 / 宽度小于两倍内缩：都不该抛异常，且结果落在 [inset, ...] 内。
        listOf(
            ReasoningSliderSpec.thumbX(0f, 0f, 1, inset),
            ReasoningSliderSpec.thumbX(3f, width, 1, inset),
            ReasoningSliderSpec.thumbX(3f, width, 0, inset),
            ReasoningSliderSpec.thumbX(3f, inset, count, inset),
            ReasoningSliderSpec.fractionForX(10f, 0f, count, inset),
            ReasoningSliderSpec.fractionForX(10f, width, 1, inset),
            ReasoningSliderSpec.fractionForX(10f, width, 0, inset),
        ).forEach { assertTrue("结果应为有限值：$it", it.isFinite()) }

        assertEquals(inset, ReasoningSliderSpec.thumbX(0f, 0f, 1, inset), 1e-4f)
        assertEquals(0f, ReasoningSliderSpec.fractionForX(10f, 0f, count, inset), 1e-4f)
    }

    // ---------------- 手感常量 ----------------

    @Test
    fun snapSpringMatchesKelivoSimulation() {
        // Kelivo: SpringSimulation(mass = 1, stiffness = 320, damping = 28)
        val stiffness = 320.0
        val damping = 28.0
        assertEquals(stiffness, ReasoningSliderSpec.SnapStiffness.toDouble(), 1e-3)
        assertEquals(
            damping / (2.0 * sqrt(stiffness)),
            ReasoningSliderSpec.SnapDampingRatio.toDouble(),
            5e-4,
        )
        // 略带过冲（ζ < 1），但不能太晃（ζ > 0.5）
        assertTrue(ReasoningSliderSpec.SnapDampingRatio in 0.5f..1f)
    }

    @Test
    fun dragScaleMatchesKelivo() {
        assertEquals(1.14f, ReasoningSliderSpec.DragScale, 1e-4f)
    }
}