package me.rerere.rikkahub.data.st.expressions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表情立绘标签与 LLM 分类提示词（Character Expressions 的语义核心）。
 *
 * 移植自 SillyTavern 1.18.0 `public/scripts/extensions/expressions/index.js`：
 * - [ALL] ← `DEFAULT_EXPRESSIONS`（index.js:44-75）：GoEmotions 28 标签全集，顺序逐字一致；
 * - [DEFAULT_FALLBACK] ← `DEFAULT_FALLBACK_EXPRESSION`（index.js:42，官方兜底 = `joy`）；
 * - [DEFAULT_LLM_PROMPT] ← `DEFAULT_LLM_PROMPT`（index.js:43），`{{labels}}` 按 `getLlmPrompt`
 *   展开成 `"joy", "anger", ...`（带引号、逗号+空格连接）；
 * - [OPTION_NONE] / [OPTION_EMOJI] ← `OPTION_NO_FALLBACK` / `OPTION_EMOJI_FALLBACK`（index.js:76-77）；
 * - [parseResponse] ← `parseLlmResponse`（index.js:961-995）的等价实现；
 * - [labelFromFileName] ← `validateExpressionSpriteName`（index.js:1832-1836）的命名模式
 *   `^label(?:[-.].*?)?$`（即 `joy` / `joy-1` / `joy.alt` 都归属 joy）。
 *
 * 说明：Fuse.js 模糊搜索在本地以「精确 → 包含」两级匹配近似（官方最后一级也是 contains），
 * 对 LLM 单标签输出的命中率等价；未命中返回 null，由调用方回退 [DEFAULT_FALLBACK]。
 */
object ExpressionLabels {

    /** 官方 `DEFAULT_EXPRESSIONS`：28 个标签（GoEmotions 全集），顺序与官方一致 */
    val ALL: List<String> = listOf(
        "admiration",
        "amusement",
        "anger",
        "annoyance",
        "approval",
        "caring",
        "confusion",
        "curiosity",
        "desire",
        "disappointment",
        "disapproval",
        "disgust",
        "embarrassment",
        "excitement",
        "fear",
        "gratitude",
        "grief",
        "joy",
        "love",
        "nervousness",
        "optimism",
        "pride",
        "realization",
        "relief",
        "remorse",
        "sadness",
        "surprise",
        "neutral",
    )

    /** 官方 `DEFAULT_FALLBACK_EXPRESSION` */
    const val DEFAULT_FALLBACK: String = "joy"

    /** 官方 `OPTION_NO_FALLBACK`：兜底 = 不显示任何立绘 */
    const val OPTION_NONE: String = "#none"

    /** 官方 `OPTION_EMOJI_FALLBACK`：兜底 = emoji（rikkaST 暂无 emoji 立绘资源，仅保留语义） */
    const val OPTION_EMOJI: String = "#emoji"

    /** 官方 `DEFAULT_LLM_PROMPT`（index.js:43，逐字） */
    const val DEFAULT_LLM_PROMPT: String =
        "Ignore previous instructions. Classify the emotion of the last message. " +
            "Output just one word, e.g. \"joy\" or \"anger\". " +
            "Choose only one of the following labels: {{labels}}"

    /** 官方 `getLlmPrompt`：`{{labels}}` → `"joy", "anger", ...` */
    fun buildPrompt(labels: List<String>): String =
        DEFAULT_LLM_PROMPT.replace("{{labels}}", labels.joinToString(", ") { "\"$it\"" })

    /**
     * 官方 `sampleClassifyText` 的 llm 分支（index.js:919-947）：
     * LLM 模式下只做去星号/引号 + trim（不做 500 字符采样）。
     */
    fun sampleText(text: String): String = text.replace("*", "").replace("\"", "").trim()

    /**
     * 分类结果 → 实际展示标签（纯函数，便于单测）。
     *
     * 规则（对齐官方 getExpressionLabel 的收尾逻辑）：
     * - 分类命中「该角色实际拥有的标签」→ 用它；
     * - 否则回退兜底标签（兜底也必须实际拥有，否则不显示）；
     * - 官方特殊兜底 `#none` / `#emoji`（rikkaST 暂无 emoji 立绘资源）→ 不显示。
     *
     * @param classified 模型分类结果（失败/解析不出时为 null）
     * @param fallback 兜底标签（[Settings.expressionFallbackLabel]）
     * @param availableLabels 该角色实际拥有的立绘标签
     * @return 应展示的标签；无任何可用时返回 null（不显示立绘）
     */
    fun resolveDisplayLabel(
        classified: String?,
        fallback: String,
        availableLabels: Collection<String>,
    ): String? {
        if (classified != null && classified in availableLabels) return classified
        if (fallback == OPTION_NONE || fallback == OPTION_EMOJI) return null
        return fallback.takeIf { it in availableLabels }
    }

    /** 是否为合法标签（大小写不敏感；官方 `labels.includes(response)` 为小写精确） */
    fun matchLabel(candidate: String): String? {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return null
        return ALL.firstOrNull { it.equals(trimmed, ignoreCase = true) }
    }

    /**
     * 官方 `validateExpressionSpriteName` 的文件名 → 标签推断：
     * `^label(?:[-.].*?)?$`。`joy.png` / `joy-1.png` / `joy.alt.png` 均归 `joy`。
     * @param fileName 文件名（可含扩展名）
     */
    fun labelFromFileName(fileName: String): String? {
        val base = fileName.substringBeforeLast('.').trim().lowercase()
        if (base.isEmpty()) return null
        val head = base.substringBefore('-').substringBefore('.').trim()
        return ALL.firstOrNull { it == head }
    }

    /**
     * 官方 `parseLlmResponse`（index.js:961-995）的等价实现：
     * 1. 先按 JSON `{"emotion": "..."}` 解析（官方结构化输出分支）；
     * 2. 去掉 `<thinking>` / `<reasoning>` / 代码围栏等包裹；
     * 3. 精确匹配（忽略大小写与首尾引号/标点）；
     * 4. 包含匹配（官方 fuse 失败后的 `includes` 兜底）；
     * 5. 仍未命中 → null（调用方回退 [DEFAULT_FALLBACK]）。
     */
    fun parseResponse(response: String?, labels: List<String> = ALL): String? {
        val raw = response?.trim().orEmpty()
        if (raw.isEmpty() || labels.isEmpty()) return null

        // 1) 官方结构化输出：{"emotion":"joy"}
        runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val emotion = obj["emotion"]?.jsonPrimitive?.content
            if (!emotion.isNullOrBlank()) {
                labels.firstOrNull { it.equals(emotion.trim(), ignoreCase = true) }?.let { return it }
            }
        }

        // 2) 去掉推理块/代码围栏（官方 removeReasoningFromString 的本地等价）
        val cleaned = raw
            .replace(Regex("(?is)<(think|thinking|reasoning|analysis)>.*?</\\1>"), " ")
            .replace(Regex("(?s)```[a-zA-Z0-9_-]*\\s*"), " ")
            .replace("```", " ")
            .trim()

        // 3) 精确匹配（容忍首尾引号/标点/空白）
        val normalized = cleaned.lowercase().trim()
            .trim('"', '\'', '.', ',', '!', '?', '*', '`', ':', ';', ' ')
        labels.firstOrNull { it == normalized }?.let { return it }

        // 4) 包含匹配（官方 fuse 之后的 includes 兜底；长标签优先，避免 "joy" 命中 "enjoy"）
        val lower = cleaned.lowercase()
        labels.sortedByDescending { it.length }.firstOrNull { label ->
            Regex("(?<![a-z])" + Regex.escape(label) + "(?![a-z])").containsMatchIn(lower)
        }?.let { return it }

        // 5) 取第一个词再试一次（模型偶尔带句号/中文标点）
        val firstWord = lower.split(Regex("[^a-z]+")).firstOrNull { it.isNotBlank() }
        return labels.firstOrNull { it == firstWord }
    }
}
