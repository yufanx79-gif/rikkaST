package me.rerere.rikkahub.data.st.expressions

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [v240 W3] 表情立绘运行态单测：messageId 缓存 / 10s 节流 / 当前选择。
 *
 * 金标准：官方 `STREAMING_UPDATE_INTERVAL = 10000`（expressions/index.js:41）
 * 与「同一条消息不重复分类」的 lastMessage 去重语义。
 */
class ExpressionStoreTest {

    @Before
    fun setUp() = ExpressionStore.resetForTest()

    @After
    fun tearDown() = ExpressionStore.resetForTest()

    @Test
    fun `message id cache stores and returns labels`() {
        assertNull(ExpressionStore.cachedLabel("m1"))
        ExpressionStore.putLabel("m1", "joy")
        assertEquals("joy", ExpressionStore.cachedLabel("m1"))
        // 同一条消息重复分类时直接命中缓存（官方 lastMessage 去重）
        assertEquals("joy", ExpressionStore.cachedLabel("m1"))
        assertNull(ExpressionStore.cachedLabel("m2"))
    }

    @Test
    fun `message id cache is bounded`() {
        assertTrue(ExpressionStore.MAX_CACHE_ENTRIES > 0)
        repeat(ExpressionStore.MAX_CACHE_ENTRIES + 8) { index ->
            ExpressionStore.putLabel("m$index", "joy")
        }
        // 超限后整体清空重建：早期条目被丢弃，最近条目仍在
        assertNull(ExpressionStore.cachedLabel("m0"))
        assertEquals(
            "joy",
            ExpressionStore.cachedLabel("m${ExpressionStore.MAX_CACHE_ENTRIES + 7}"),
        )
    }

    @Test
    fun `classify throttle uses official 10s interval`() {
        assertEquals(10_000L, ExpressionStore.CLASSIFY_INTERVAL_MS)
        // 首次无记录 → 不节流
        assertFalse(ExpressionStore.shouldThrottleClassify("c1", nowMillis = 1_000))
        ExpressionStore.markClassified("c1", nowMillis = 1_000)
        // 4s 后 → 仍在 10s 窗口内，节流
        assertTrue(ExpressionStore.shouldThrottleClassify("c1", nowMillis = 5_000))
        // 恰好 10s → 放行（< 才节流）
        assertFalse(ExpressionStore.shouldThrottleClassify("c1", nowMillis = 11_000))
        // 不同对话互不影响
        assertFalse(ExpressionStore.shouldThrottleClassify("c2", nowMillis = 5_000))
    }

    @Test
    fun `selection is per conversation and deduplicates`() {
        ExpressionStore.select("c1", "m1", "joy")
        assertEquals(
            ExpressionStore.Selection("m1", "joy"),
            ExpressionStore.selections.value["c1"],
        )
        ExpressionStore.select("c2", "m9", "anger")
        assertEquals(
            ExpressionStore.Selection("m9", "anger"),
            ExpressionStore.selections.value["c2"],
        )
        // 同一选择重复写入不改变内容
        val before = ExpressionStore.selections.value
        ExpressionStore.select("c1", "m1", "joy")
        assertEquals(before, ExpressionStore.selections.value)
        // 切换标签
        ExpressionStore.select("c1", "m1", "sadness")
        assertEquals("sadness", ExpressionStore.selections.value["c1"]?.label)
    }

    @Test
    fun `clear conversation removes only that selection`() {
        ExpressionStore.select("c1", "m1", "joy")
        ExpressionStore.select("c2", "m2", "anger")
        ExpressionStore.clearConversation("c1")
        assertNull(ExpressionStore.selections.value["c1"])
        assertEquals("anger", ExpressionStore.selections.value["c2"]?.label)
        // messageId 缓存不受清除选择影响（同一消息回到前台可复用）
        ExpressionStore.putLabel("m1", "joy")
        ExpressionStore.clearConversation("c1")
        assertEquals("joy", ExpressionStore.cachedLabel("m1"))
    }

    @Test
    fun `classification failure resolves to fallback without crashing`() {
        // 模拟 ChatService.classifyExpression 抛异常/超时被宿主 runCatching 吞掉
        val classified = runCatching<String?> { error("timeout") }.getOrNull()
        assertNull(classified)
        val label = ExpressionLabels.resolveDisplayLabel(
            classified = classified,
            fallback = ExpressionLabels.DEFAULT_FALLBACK,
            availableLabels = listOf("joy", "anger"),
        )
        assertEquals("joy", label)
        // 宿主路径：缓存最终解析结果（异常不得留下脏缓存）
        ExpressionStore.putLabel("m1", label!!)
        assertEquals("joy", ExpressionStore.cachedLabel("m1"))
    }
}
