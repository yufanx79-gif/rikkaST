package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.data.ai.tools.local.buildAskUserTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：Gemini / Vertex 强校验工具 schema，`type: "array"` 必须带 `items`，
 * 否则整个请求被 400 拒绝：
 *
 * `GenerateContentRequest.tools[0].function_declarations[N].parameters.properties[args].items: missing field.`
 *
 * 触发场景：开启「JavaScript 引擎」本地工具后，`eval_javascript` 的 `args` 参数是数组但没写 `items`。
 */
class JavascriptToolSchemaTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `eval_javascript declares items for its args array`() {
        val args = javascriptToolSchema().toJsonObject()["properties"]!!
            .jsonObject["args"]!!.jsonObject

        assertEquals("array", args["type"]!!.jsonPrimitive.content)
        assertNotNull("args 是数组，必须声明 items，否则 Gemini 会返回 400", args["items"])
        assertEquals("string", args["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `context free local tool schemas never declare an array without items`() {
        val schemas = buildList {
            add("eval_javascript" to javascriptToolSchema())
            add("ask_user" to buildAskUserTool().parameters()!!)
            createTaskTools().forEach { add(it.name to it.parameters()!!) }
        }

        val violations = mutableListOf<String>()
        schemas.forEach { (name, schema) ->
            collectArraysWithoutItems(schema.toJsonObject(), name, violations)
        }

        assertTrue("以下工具声明了没有 items 的数组（Gemini 会 400）: $violations", violations.isEmpty())
    }

    private fun InputSchema.toJsonObject(): JsonObject =
        json.encodeToJsonElement(this).jsonObject

    private fun collectArraysWithoutItems(
        element: JsonElement,
        path: String,
        out: MutableList<String>,
    ) {
        when (element) {
            is JsonObject -> {
                val type = (element["type"] as? JsonPrimitive)?.contentOrNull
                if (type?.lowercase() == "array") {
                    val items = element["items"]
                    if (items !is JsonObject || items["type"] !is JsonPrimitive) out += path
                }
                element.forEach { (key, value) ->
                    collectArraysWithoutItems(value, "$path.$key", out)
                }
            }

            is JsonArray -> element.forEachIndexed { index, value ->
                collectArraysWithoutItems(value, "$path[$index]", out)
            }

            else -> Unit
        }
    }
}