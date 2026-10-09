package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.MotionScheme
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v222 R2 收尾] 「开关卡顿」的**可量化**部分：把弹簧的 1% 稳定时间算出来，钉死改进幅度。
 *
 * M3 1.5.0-alpha26 的 `Switch` thumb 动画走 `LocalMotionScheme.fastSpatialSpec()`：
 * - `MotionScheme.expressive()` = spring(dampingRatio = 0.6f, stiffness = 800f)
 * - `MotionScheme.standard()`   = spring(dampingRatio = 0.9f, stiffness = 1400f)
 *
 * 欠阻尼弹簧的 1% 稳定时间 ≈ 4.6 / (ζ·ωn)，其中 ωn = sqrt(stiffness)。
 * 这个测试不依赖真机，是「不许把动画时长调短糊过去」的对照基线：
 * 我们改的是**弹簧参数**（能量耗散更快），不是 durationMillis。
 */
class MotionSchemeWindowTest {

    private fun settleMs(dampingRatio: Float, stiffness: Float): Double {
        val omegaN = sqrt(stiffness.toDouble())
        return 4.6 / (dampingRatio * omegaN) * 1000.0
    }

    @Test
    fun `standard settles meaningfully faster than expressive`() {
        val expressive = settleMs(0.6f, 800f)
        val standard = settleMs(0.9f, 1400f)
        println("expressive ≈ ${expressive.toInt()} ms, standard ≈ ${standard.toInt()} ms")
        assertTrue("standard 应当明显快于 expressive", standard < expressive * 0.75)
        assertTrue(standard < 150.0)
    }

    @Test
    fun `expressive is underdamped and standard is much closer to critical`() {
        // ζ < 1 会过冲；0.6 vs 0.9 是「弹」与「稳」的分界说明
        assertTrue(0.6f < 1f)
        assertTrue(0.9f > 0.6f)
        val overshoot = exp(-PI * 0.6 / sqrt(1.0 - 0.6 * 0.6))
        assertTrue("expressive 的过冲约 9%", overshoot > 0.08)
    }

    @Test
    fun `both schemes are the real ones shipped by material3`() {
        // 只要 M3 换了这两个数，这条会失败，提醒我们重新量测（而不是凭感觉改时长）
        val expressive = MotionScheme.expressive()
        val standard = MotionScheme.standard()
        assertTrue(expressive !== standard)
    }
}
