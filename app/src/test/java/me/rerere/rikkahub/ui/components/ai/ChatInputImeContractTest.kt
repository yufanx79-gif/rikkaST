package me.rerere.rikkahub.ui.components.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v225 F9/P5] 键盘弹出态输入栏的几何契约。
 *
 * 主人 2026-10-04 拍板（P5 = 4dp）：输入框在弹出输入法后必须 ① 四角全圆、② 与输入法之间留一条可见的缝。
 * v224 的缺陷：键盘一弹出就把下方两角置 0（方角）+ bottom padding 归零（贴脸）。
 * 这里钉死两个数字，防止以后又被改回 0。
 */
class ChatInputImeContractTest {

    @Test
    fun imeBottomPaddingIsFourDp() {
        assertEquals(4, CHAT_INPUT_IME_BOTTOM_PADDING_DP)
    }

    @Test
    fun idleBottomPaddingStaysEightDp() {
        assertEquals(8, CHAT_INPUT_IDLE_BOTTOM_PADDING_DP)
    }

    @Test
    fun imeBottomPaddingNeverCollapsesToZero() {
        // v224 的「贴脸」根因就是 0dp
        assertTrue(CHAT_INPUT_IME_BOTTOM_PADDING_DP > 0)
        assertTrue(CHAT_INPUT_IDLE_BOTTOM_PADDING_DP > CHAT_INPUT_IME_BOTTOM_PADDING_DP)
    }
}
