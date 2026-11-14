package me.rerere.rikkahub.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆写的确定性去重：只挡"规范化后完全相同"的行，
 * 语义近似留给后台整理的合并指令，避免误删不同事实。
 */
class MemoryDedupeTest {

    private val file = listOf(
        "---",
        "name: profile",
        "---",
        "",
        "- [stated] 喜欢猫",
        "- [stated] 住在深圳",
    ).joinToString("\n")

    @Test
    fun `exact line is detected as duplicate`() {
        assertTrue(containsEquivalentLine(file, "- [stated] 喜欢猫"))
        assertTrue(containsEquivalentLine(file, "喜欢猫"))
    }

    @Test
    fun `punctuation and whitespace differences are duplicates`() {
        assertTrue(containsEquivalentLine(file, "- [stated] 喜欢猫。"))
        assertTrue(containsEquivalentLine(file, "- [stated]   喜欢猫"))
        assertTrue(containsEquivalentLine(file, "- [stated] 喜欢猫！"))
    }

    @Test
    fun `different facts are not duplicates`() {
        assertFalse(containsEquivalentLine(file, "- [stated] 喜欢狗"))
        assertFalse(containsEquivalentLine(file, "- [stated] 住在广州"))
    }

    @Test
    fun `related but different statements stay separate`() {
        assertFalse(containsEquivalentLine(file, "- [stated] 喜欢猫和狗"))
        assertFalse(containsEquivalentLine(file, "- [stated] 不喜欢猫"))
    }

    @Test
    fun `full width characters normalize to half width`() {
        assertTrue(containsEquivalentLine("- [stated] Rikka", "- [stated] Ｒｉｋｋａ"))
    }

    @Test
    fun `blank candidate never matches`() {
        assertFalse(containsEquivalentLine(file, ""))
        assertFalse(containsEquivalentLine(file, "   "))
    }

    @Test
    fun `normalization keeps distinct words apart`() {
        assertEquals(
            normalizeMemoryLine("- [stated] 喜欢猫"),
            normalizeMemoryLine("喜欢猫。"),
        )
        assertTrue(normalizeMemoryLine("- [stated] 喜欢猫") != normalizeMemoryLine("喜欢狗"))
    }
}
