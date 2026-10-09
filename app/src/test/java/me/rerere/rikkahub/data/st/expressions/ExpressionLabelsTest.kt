package me.rerere.rikkahub.data.st.expressions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v240 W3] 表情立绘标签/提示词/解析单测。
 *
 * 金标准：SillyTavern 1.18.0 `public/scripts/extensions/expressions/index.js`
 * - DEFAULT_EXPRESSIONS（28 标签，index.js:44-75）
 * - DEFAULT_FALLBACK_EXPRESSION = joy（:42）
 * - DEFAULT_LLM_PROMPT（:43）+ getLlmPrompt 的 `{{labels}}` 展开（:949-953）
 * - parseLlmResponse（:961-995）
 * - validateExpressionSpriteName 的 `^label(?:[-.].*?)?$`（:1832-1836）
 */
class ExpressionLabelsTest {

    @Test
    fun `all 28 labels match official order`() {
        assertEquals(28, ExpressionLabels.ALL.size)
        assertEquals(
            listOf(
                "admiration", "amusement", "anger", "annoyance", "approval", "caring",
                "confusion", "curiosity", "desire", "disappointment", "disapproval", "disgust",
                "embarrassment", "excitement", "fear", "gratitude", "grief", "joy", "love",
                "nervousness", "optimism", "pride", "realization", "relief", "remorse",
                "sadness", "surprise", "neutral",
            ),
            ExpressionLabels.ALL,
        )
        assertEquals("joy", ExpressionLabels.DEFAULT_FALLBACK)
        assertEquals("#none", ExpressionLabels.OPTION_NONE)
        assertEquals("#emoji", ExpressionLabels.OPTION_EMOJI)
    }

    @Test
    fun `build prompt matches official template`() {
        val prompt = ExpressionLabels.buildPrompt(listOf("joy", "anger"))
        assertEquals(
            "Ignore previous instructions. Classify the emotion of the last message. " +
                "Output just one word, e.g. \"joy\" or \"anger\". " +
                "Choose only one of the following labels: \"joy\", \"anger\"",
            prompt,
        )
        assertTrue(prompt.startsWith("Ignore previous instructions."))
        // 单引号标签展开：逗号+空格连接（官方 getLlmPrompt）
        assertTrue(ExpressionLabels.buildPrompt(ExpressionLabels.ALL).contains("\"joy\", \"love\""))
    }

    @Test
    fun `parse json emotion response`() {
        assertEquals("joy", ExpressionLabels.parseResponse("{\"emotion\":\"joy\"}"))
        assertEquals("joy", ExpressionLabels.parseResponse("{\"emotion\": \"Joy\"}"))
        assertEquals("anger", ExpressionLabels.parseResponse("{\"emotion\":\"anger\",\"confidence\":0.9}"))
        // 标签不在可用集合 → null（官方 JSON 分支要求 labels.includes）
        assertNull(ExpressionLabels.parseResponse("{\"emotion\":\"sleepy\"}"))
    }

    @Test
    fun `parse plain text response`() {
        assertEquals("joy", ExpressionLabels.parseResponse("joy"))
        assertEquals("joy", ExpressionLabels.parseResponse("  Joy.  "))
        assertEquals("joy", ExpressionLabels.parseResponse("\"joy\""))
        assertEquals("anger", ExpressionLabels.parseResponse("The emotion of the last message is anger."))
        // 推理块剥离（官方 removeReasoningFromString 语义）
        assertEquals("sadness", ExpressionLabels.parseResponse("<thinking>hmm, sad</thinking> sadness"))
        assertEquals("love", ExpressionLabels.parseResponse("```\nlove\n```"))
    }

    @Test
    fun `parse respects available labels only`() {
        assertEquals("joy", ExpressionLabels.parseResponse("joy", listOf("joy", "anger")))
        // 模型输出了集合外的标签 → 不命中（由调用方回退兜底）
        assertNull(ExpressionLabels.parseResponse("love", listOf("joy", "anger")))
        // 空响应 / 无法解析
        assertNull(ExpressionLabels.parseResponse(null))
        assertNull(ExpressionLabels.parseResponse(""))
        assertNull(ExpressionLabels.parseResponse("I cannot classify this."))
        assertNull(ExpressionLabels.parseResponse("joy", emptyList()))
    }

    @Test
    fun `sample text strips quotes and asterisks like st`() {
        assertEquals("Hello world", ExpressionLabels.sampleText("  *Hello* \"world\"  "))
        assertEquals("动作描写", ExpressionLabels.sampleText("*动作描写*"))
    }

    @Test
    fun `label from file name follows official naming pattern`() {
        assertEquals("joy", ExpressionLabels.labelFromFileName("joy.png"))
        assertEquals("joy", ExpressionLabels.labelFromFileName("JOY.JPG"))
        assertEquals("joy", ExpressionLabels.labelFromFileName("joy-1.png"))
        assertEquals("joy", ExpressionLabels.labelFromFileName("joy.alt.png"))
        assertEquals("neutral", ExpressionLabels.labelFromFileName("neutral.webp"))
        assertNull(ExpressionLabels.labelFromFileName("smile.png"))
        assertNull(ExpressionLabels.labelFromFileName("joy_2.png"))
        assertNull(ExpressionLabels.labelFromFileName(".png"))
        // 无扩展名也按 base name 匹配（目录扫描已先过滤扩展名）
        assertEquals("anger", ExpressionLabels.labelFromFileName("anger"))
    }

    @Test
    fun `resolve display label uses classified then fallback`() {
        val available = listOf("joy", "anger", "neutral")
        assertEquals("anger", ExpressionLabels.resolveDisplayLabel("anger", "joy", available))
        // 分类为 null（异常/超时）→ 兜底
        assertEquals("joy", ExpressionLabels.resolveDisplayLabel(null, "joy", available))
        // 分类为集合外标签 → 兜底
        assertEquals("neutral", ExpressionLabels.resolveDisplayLabel("love", "neutral", available))
        // 兜底也没有 → 不显示
        assertNull(ExpressionLabels.resolveDisplayLabel(null, "love", available))
        assertNull(ExpressionLabels.resolveDisplayLabel("love", "joy", emptyList()))
        // 官方特殊兜底 #none / #emoji → 不显示
        assertNull(ExpressionLabels.resolveDisplayLabel(null, ExpressionLabels.OPTION_NONE, available))
        assertNull(ExpressionLabels.resolveDisplayLabel(null, ExpressionLabels.OPTION_EMOJI, available))
    }

    @Test
    fun `decode data uri handles embedded sprite assets`() {
        // 1x1 PNG 的最小 base64（官方 V3 assets 的内嵌图片格式）
        val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val decoded = SpriteRepository.decodeDataUri("data:image/png;base64,$png")
        assertTrue(decoded != null)
        assertEquals("png", decoded!!.second)
        assertTrue(decoded.first.isNotEmpty())
        assertEquals("jpg", SpriteRepository.decodeDataUri("data:image/jpeg;base64,$png")!!.second)
        // 非 data URI / 非 base64 / 坏 payload → null（不崩）
        assertNull(SpriteRepository.decodeDataUri("https://example.com/joy.png"))
        assertNull(SpriteRepository.decodeDataUri("data:image/png,rawbytes"))
        assertNull(SpriteRepository.decodeDataUri("data:image/png;base64,@@@not-base64@@@"))
    }
}
