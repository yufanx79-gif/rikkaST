package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [parseMessageRange] 边界测试（移植自 ST `utils.stringToRange`）。
 *
 * 覆盖：单索引 / 闭区间 / 全范围 / 空白容忍 / 反向区间 / 越界 / 非法输入。
 * 该函数是 `/hide` `/unhide` 命令在输入框通道的索引解析核心。
 */
class ChatSlashHostTest {

    @Test
    fun singleIndex() {
        assertEquals(3 to 3, parseMessageRange("3", 10))
    }

    @Test
    fun inclusiveRange() {
        assertEquals(2 to 9, parseMessageRange("2-9", 10))
    }

    @Test
    fun fullSpan() {
        assertEquals(0 to 10, parseMessageRange("0-10", 10))
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        assertEquals(1 to 2, parseMessageRange("1 -2 ", 10))
    }

    @Test
    fun acceptsZeroIndex() {
        assertEquals(0 to 0, parseMessageRange("0", 10))
    }

    @Test
    fun acceptsIndexAtMax() {
        assertEquals(10 to 10, parseMessageRange("10", 10))
    }

    @Test
    fun rejectsReversedRange() {
        assertNull(parseMessageRange("5-2", 10))
    }

    @Test
    fun rejectsSingleIndexBeyondMax() {
        assertNull(parseMessageRange("11", 10))
    }

    @Test
    fun rejectsRangeBeyondMax() {
        assertNull(parseMessageRange("0-11", 10))
    }

    @Test
    fun rejectsNegativeInput() {
        assertNull(parseMessageRange("-1", 10))
    }

    @Test
    fun rejectsNonNumeric() {
        assertNull(parseMessageRange("abc", 10))
        assertNull(parseMessageRange("1-abc", 10))
        assertNull(parseMessageRange("", 10))
    }
}
