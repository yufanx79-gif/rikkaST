package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [buildCheckpointName] 单测（对齐 ST `getUniqueName` + `buildCheckpointName`）。
 *
 * 覆盖：自动生成 / 既有标题去重 / 旧后缀剥离 / 指定名字优先 / 空白回退 / 默认基准。
 */
class CheckpointNameTest {

    @Test
    fun autoGeneratesFirstCandidate() {
        assertEquals("Chat - Checkpoint #1", buildCheckpointName("Chat", null, emptyList(), "Chat"))
    }

    @Test
    fun deduplicatesAgainstExistingTitles() {
        val existing = listOf("Chat - Checkpoint #1", "Chat - Checkpoint #2")
        assertEquals("Chat - Checkpoint #3", buildCheckpointName("Chat", null, existing, "Chat"))
    }

    @Test
    fun stripsOldCheckpointSuffixBeforeNumbering() {
        assertEquals(
            "Chat - Checkpoint #1",
            buildCheckpointName("Chat - Checkpoint #5", null, emptyList(), "Chat"),
        )
    }

    @Test
    fun requestedNameWinsWithoutDeduplication() {
        assertEquals("My CP", buildCheckpointName("Chat", "My CP", listOf("My CP"), "Chat"))
    }

    @Test
    fun blankRequestedFallsBackToAuto() {
        assertEquals("Chat - Checkpoint #1", buildCheckpointName("Chat", "   ", emptyList(), "Chat"))
    }

    @Test
    fun blankSourceUsesDefaultBase() {
        assertEquals("Chat - Checkpoint #1", buildCheckpointName("", null, emptyList(), "Chat"))
    }

    @Test
    fun multiDigitSuffixIsStripped() {
        assertEquals(
            "Chat - Checkpoint #1",
            buildCheckpointName("Chat - Checkpoint #12", null, emptyList(), "Chat"),
        )
    }
}
