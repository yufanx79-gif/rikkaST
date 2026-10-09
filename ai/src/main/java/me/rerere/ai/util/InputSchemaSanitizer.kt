package me.rerere.ai.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 规范化 function calling 的 JSON Schema，让它能被对 schema 强校验的 provider 接受。
 *
 * 目前只做一件事：**给缺 `items` 的数组补上 `items`**。
 *
 * 为什么需要：Gemini / Vertex 对工具 schema 是强校验的，`{"type":"array"}` 缺 `items`
 * 会直接让整个请求 400，错误形如
 * `GenerateContentRequest.tools[0].function_declarations[N].parameters.properties[x].items: missing field.`
 * 工具 schema 来自本地工具代码和第三方 MCP server，都不保证合规，所以在这里统一兜底。
 *
 * 该函数幂等，可以安全地在每次请求前调用。
 */
fun JsonElement.normalizeInputSchema(): JsonElement = when (this) {
    is JsonObject -> {
        val normalized = JsonObject(mapValues { (_, value) -> value.normalizeInputSchema() })
        if (normalized.isArrayType() && normalized[ITEMS].hasNoType()) {
            JsonObject(normalized + (ITEMS to JsonObject(mapOf(TYPE to JsonPrimitive("string")))))
        } else {
            normalized
        }
    }

    is JsonArray -> JsonArray(map { it.normalizeInputSchema() })

    else -> this
}

private const val TYPE = "type"
private const val ITEMS = "items"

private fun JsonObject.isArrayType(): Boolean =
    (this[TYPE] as? JsonPrimitive)?.contentOrNull?.equals("array", ignoreCase = true) == true

/** `items` 缺失，或者虽然存在但没有 `type`（例如空对象 `{}`）——两者都会被 Gemini 拒绝。 */
private fun JsonElement?.hasNoType(): Boolean =
    this !is JsonObject || this[TYPE] !is JsonPrimitive