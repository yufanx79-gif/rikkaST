package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 模型把工具调用写成了正文时的保守兜底。
 *
 * 有些模型（典型是 DeepSeek 系）在工具描述和它自己的预期不一致时，不走 `tool_calls`，
 * 而是在正文里输出 `<function=name>{...}</function>` 或 `{"name": "...", "arguments": {...}}`。
 * 这段文本会原样进聊天记录、工具永远不执行，用户看到的正是"把调用函数直接输出"。
 *
 * 这里的解析刻意保守：
 * - 只在整条消息**没有**任何工具调用、且只有一段正文时尝试；
 * - 工具名必须真的在本次请求的工具列表里；
 * - 参数必须是合法 JSON 对象；
 * - 文本长度有上限。
 *
 * 任何一条不满足就原样返回，绝不改写正常叙事。
 */
internal object ToolTextFallback {
    /** 正文超过这个长度就放弃匹配：正常叙事很少整段只有一个调用。 */
    private const val MAX_TEXT_LENGTH = 2000

    // 注意：花括号必须转义成 \{ \}。Android 用的是 ICU 正则，裸 { } 会抛
    // PatternSyntaxException（"Unexpected internal error"），桌面 JVM 却照常能跑 ——
    // MacrosMacroSentinelTest.noBareBracesInAnyKotlinRegexLiteral 就是守这条的。
    private val functionTagRegex = Regex(
        """^\s*<function\s*=\s*([A-Za-z0-9_.\-]+)\s*>\s*(\{[\s\S]*\})\s*</function>\s*$""",
    )

    private val toolCallBlockRegex = Regex(
        """^\s*```(?:json|tool_call)?\s*(\{[\s\S]*\})\s*```\s*$""",
    )

    /**
     * 消息里是否有可提升为工具调用的正文。
     *
     * @param availableToolNames 本次请求真正声明给模型的工具名
     * @return 识别出的 (工具名, 参数 JSON 文本)；没有命中时返回 null
     */
    fun extractToolCall(
        message: UIMessage,
        availableToolNames: Set<String>,
        json: Json,
    ): Pair<String, String>? {
        if (message.role != MessageRole.ASSISTANT) return null
        if (message.getTools().isNotEmpty()) return null
        val text = message.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
            .trim()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) return null

        functionTagRegex.matchEntire(text)?.let { match ->
            val name = match.groupValues[1]
            val args = match.groupValues[2]
            return if (name in availableToolNames && isJsonObject(args, json)) name to args else null
        }

        // 形如 {"name":"memory_append","arguments":{...}} 或
        // {"tool":"memory_append","input":"{\"path\":...}"}
        val candidate = toolCallBlockRegex.matchEntire(text)?.groupValues?.get(1) ?: text
        val obj = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
            ?: return null
        val name = obj.stringField("name") ?: obj.stringField("tool") ?: obj.stringField("tool_name")
            ?: return null
        if (name !in availableToolNames) return null

        val argsElement = obj["arguments"] ?: obj["input"] ?: obj["args"] ?: return null
        val args = when (argsElement) {
            is JsonObject -> argsElement.toString()
            else -> argsElement.toString().let { raw ->
                // 有的模型把参数再序列化成字符串
                val inner = runCatching { json.parseToJsonElement(raw) }.getOrNull()
                if (inner is JsonObject) inner.toString() else return null
            }
        }
        return name to args
    }

    private fun JsonObject.stringField(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content

    private fun isJsonObject(raw: String, json: Json): Boolean =
        runCatching { json.parseToJsonElement(raw) is JsonObject }.getOrDefault(false)
}
