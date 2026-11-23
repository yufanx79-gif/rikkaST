package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v249] 无 embedding 模型时的**词袋回退链路**验证。
 *
 * 真机现象：未配置 embedding 时 scorer=null → 走 computeVectorSimilarity（词袋余弦），
 * 相似度低于 0.35 阈值 → 条目不激活。这不等于链路坏了，本测试把"回退链路本身能工作"
 * 与"相似度够不够"两件事分开证明。
 */
class VectorFallbackChainTest {

    private fun vectorEntry(name: String, content: String, keywords: List<String>) =
        PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = name,
            content = content,
            keywords = keywords,
            vectorized = true,
        )

    private fun run(entries: List<PromptInjection.RegexInjection>, query: String, threshold: Double, max: Int): List<String> {
        val book = Lorebook(name = "回退测试书", entries = entries)
        val old = PromptInjectionTransformer.vectorThreshold
        PromptInjectionTransformer.vectorThreshold = threshold
        try {
            val result = collectInjections(
                messages = listOf(UIMessage.user(query)),
                assistant = Assistant(lorebookIds = setOf(book.id)),
                modeInjections = emptyList(),
                lorebooks = listOf(book),
                semanticScorer = null, // 关键：模拟"没有 embedding 模型"
                vectorMaxEntries = max,
            )
            return result.filterIsInstance<PromptInjection.RegexInjection>()
                .map { it.name }
                .filter { it.startsWith("VEC_") }
        } finally {
            PromptInjectionTransformer.vectorThreshold = old
        }
    }

    /**
     * 词袋回退 + 低阈值：词袋链路必须能把条目激活出来。
     * 这条证明"回退路径本身是通的"，真机上不激活只是相似度不够，不是代码坏。
     */
    @Test
    fun bagOfWordsFallbackActivatesWhenSimilarityPassesThreshold() {
        val entries = listOf(
            vectorEntry("VEC_星辰", "星辰大海 星际航行 宇宙 飞船", listOf("星辰大海")),
            vectorEntry("VEC_深海", "深海潜水 鲸鱼 海洋", listOf("深海")),
        )
        // 阈值降到 0.05：让词袋相似度必然通过
        val activated = run(entries, "星辰大海和星际航行的关系", threshold = 0.05, max = 5)
        assertTrue(
            "词袋回退链路必须能激活与 query 有共同词元的条目，实际=$activated",
            activated.contains("VEC_星辰"),
        )
    }

    /**
     * 词袋回退 + 正常阈值：不相关条目不应被激活（证明阈值真的在起作用）。
     */
    @Test
    fun bagOfWordsFallbackRejectsUnrelatedEntryAtDefaultThreshold() {
        val entries = listOf(
            vectorEntry("VEC_无关", "量子色动力学 胶子 强相互作用", listOf("胶子")),
        )
        val activated = run(entries, "今天天气不错", threshold = 0.35, max = 5)
        assertTrue(
            "完全无共同词元的条目不应激活，实际=$activated",
            activated.isEmpty(),
        )
    }

    /**
     * 回退路径也必须遵守 vectorMaxEntries 上限（缺口 3 在无 embedding 时同样生效）。
     */
    @Test
    fun fallbackPathAlsoRespectsMaxEntries() {
        // 关键词故意用永不命中的串，强制走向量分支（否则会被 isTriggered 命中而绕过向量上限）
        val entries = (1..5).map { i ->
            vectorEntry("VEC_$i", "共同词元 alpha beta gamma 编号$i", listOf("zz_never_$i"))
        }
        val activated = run(entries, "共同 alpha beta gamma", threshold = 0.01, max = 2)
        assertEquals(
            "无 embedding 时也必须只激活 vectorMaxEntries 条，实际=$activated",
            2,
            activated.size,
        )
    }

    /**
     * 反向语义：关键词命中的条目**不受** vectorMaxEntries 限制。
     * 这是正确行为（向量上限只管语义召回）。
     */
    @Test
    fun keywordHitIsNotLimitedByVectorMaxEntries() {
        val entries = (1..5).map { i ->
            vectorEntry("VEC_$i", "内容$i", listOf("共同触发词"))
        }
        val activated = run(entries, "共同触发词", threshold = 0.99, max = 1)
        assertEquals(
            "关键词命中的 5 条都应激活，不受向量上限约束",
            5, activated.size,
        )
    }
}
