package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v248] 世界书向量激活的 L5 活性测试。
 *
 * 这一层要回答的不是「函数存在吗」，而是**「换个输入，输出真的会变吗」**。
 * 上一轮的教训：桥存在、符号存在、甚至单测全绿，功能依然可能完全没接线。
 *
 * 用 `collectInjections`（真正的扫描函数）而不是看某个字段被赋值，
 * 因为它就是生成链路里决定「哪些世界书条目被注入」的地方。
 */
class SemanticVectorWorldInfoTest {

    private fun entry(
        content: String,
        keywords: List<String> = emptyList(),
        vectorized: Boolean = true,
    ) = PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = "Entry",
        enabled = true,
        position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
        content = content,
        keywords = keywords,
        vectorized = vectorized,
        // 关掉概率，避免随机导致 flaky
        useProbability = false,
    )

    private fun book(vararg entries: PromptInjection.RegexInjection) = Lorebook(
        id = Uuid.random(),
        name = "TestBook",
        enabled = true,
        entries = entries.toList(),
    )

    private fun assistant(book: Lorebook) = Assistant(
        id = Uuid.random(),
        name = "Bot",
        lorebookIds = setOf(book.id),
    )

    private fun chat(text: String) = listOf(
        UIMessage(role = MessageRole.USER, parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(text)))
    )

    /**
     * 正向活性：同一段对话、同一条目。
     * 默认（词袋）不该命中；注入「永远相似」的 scorer 后必须命中。
     * 如果 scorer 被忽略，两次结果会一模一样 —— 这个断言就会失败。
     */
    @Test
    fun `semantic scorer actually changes activation outcome`() {
        val b = book(entry(content = "红富士苹果很甜", keywords = listOf("苹果")))
        val a = assistant(b)
        val msgs = chat("今天天气不错")

        val baseline = collectInjections(
            messages = msgs,
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
        )

        val semantic = collectInjections(
            messages = msgs,
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
            semanticScorer = { _q, _d -> 0.99 },
        )

        val baselineHas = baseline.any { it.content.contains("红富士苹果很甜") }
        val semanticHas = semantic.any { it.content.contains("红富士苹果很甜") }

        assertTrue(
            "词袋模式下「今天天气不错」不该激活「苹果」条目（baselineHas=$baselineHas）",
            !baselineHas,
        )
        assertTrue(
            "注入语义 scorer 后必须激活，否则说明 scorer 根本没参与判定",
            semanticHas,
        )
    }

    /** 反向活性：scorer 恒返回 0 时必须**不**激活 —— 防「调用了但返回值被忽略」。 */
    @Test
    fun `zero similarity prevents activation`() {
        val b = book(entry(content = "红富士苹果很甜", keywords = listOf("苹果")))
        val a = assistant(b)

        val result = collectInjections(
            messages = chat("今天天气不错"),
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
            semanticScorer = { _q, _d -> 0.0 },
        )

        assertTrue(
            "scorer 返回 0 时不该激活任何条目",
            result.none { it.content.contains("红富士苹果很甜") },
        )
    }

    /** scorer 只对 vectorized=true 的条目生效。 */
    @Test
    fun `non vectorized entry ignores scorer`() {
        val b = book(entry(content = "普通条目内容", keywords = listOf("不存在的词"), vectorized = false))
        val a = assistant(b)

        val result = collectInjections(
            messages = chat("完全无关的对话"),
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
            semanticScorer = { _q, _d -> 0.99 },
        )

        assertTrue(
            "vectorized=false 的条目不该被语义 scorer 激活",
            result.none { it.content.contains("普通条目内容") },
        )
    }

    /** 不传 scorer 时，关键词命中路径必须与旧版完全一致。 */
    @Test
    fun `keyword activation still works without scorer`() {
        val b = book(entry(content = "Excalibur is the legendary sword", keywords = listOf("excalibur")))
        val a = assistant(b)

        val result = collectInjections(
            messages = chat("Arthur drew Excalibur from the stone"),
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
        )

        assertTrue(
            "关键词命中必须照旧工作",
            result.any { it.content.contains("legendary sword") },
        )
    }

    /** 高阈值时低相似度不该激活（阈值真的参与比较）。 */
    @Test
    fun `threshold is respected`() {
        val b = book(entry(content = "红富士苹果很甜", keywords = listOf("苹果")))
        val a = assistant(b)

        // 默认阈值 0.35；给 0.10 的相似度不该激活
        val low = collectInjections(
            messages = chat("今天天气不错"),
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
            semanticScorer = { _q, _d -> 0.10 },
        )
        assertTrue("0.10 < 阈值 0.35，不该激活", low.none { it.content.contains("红富士苹果很甜") })

        // 给 0.90 必须激活
        val high = collectInjections(
            messages = chat("今天天气不错"),
            assistant = a,
            modeInjections = emptyList(),
            lorebooks = listOf(b),
            semanticScorer = { _q, _d -> 0.90 },
        )
        assertTrue("0.90 > 阈值 0.35，必须激活", high.any { it.content.contains("红富士苹果很甜") })
    }
}
