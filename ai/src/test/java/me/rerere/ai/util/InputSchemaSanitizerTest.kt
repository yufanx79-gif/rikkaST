package me.rerere.ai.util

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 回归测试：Gemini / Vertex 对工具 schema 是强校验的，数组类型必须带 `items`，
 * 否则整个请求会被 400 拒绝：
 *
 * `GenerateContentRequest.tools[0].function_declarations[N].parameters.properties[args].items: missing field.`
 */
class InputSchemaSanitizerTest {

    @Test
    fun `fills in items for an array property that has none`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("args", buildJsonObject {
                    put("type", "array")
                    put("description", "JSON array of arguments for function call")
                })
            })
        }

        val args = schema.normalizeInputSchema()
            .jsonObject["properties"]!!.jsonObject["args"]!!.jsonObject

        assertEquals("array", args["type"]!!.jsonPrimitive.content)
        assertEquals("string", args["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `keeps an existing items declaration untouched`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("questions", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "object") })
                })
            })
        }

        val questions = schema.normalizeInputSchema()
            .jsonObject["properties"]!!.jsonObject["questions"]!!.jsonObject

        assertEquals("object", questions["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `replaces an items object that carries no type`() {
        val schema = buildJsonObject {
            put("type", "array")
            put("items", buildJsonObject { put("description", "no type here") })
        }

        val items = schema.normalizeInputSchema().jsonObject["items"]!!.jsonObject

        assertEquals("string", items["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `normalizes nested schemas recursively`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("outer", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("inner", buildJsonObject { put("type", "array") })
                        })
                    })
                })
            })
        }

        val inner = schema.normalizeInputSchema()
            .jsonObject["properties"]!!.jsonObject["outer"]!!.jsonObject["items"]!!
            .jsonObject["properties"]!!.jsonObject["inner"]!!.jsonObject

        assertEquals("string", inner["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `is idempotent`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("args", buildJsonObject { put("type", "array") })
            })
        }

        val once = schema.normalizeInputSchema()
        val twice = once.normalizeInputSchema()

        assertEquals(once.toString(), twice.toString())
    }

    @Test
    fun `does not touch schemas that are not arrays`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("code", buildJsonObject { put("type", "string") })
            })
        }

        val code = schema.normalizeInputSchema()
            .jsonObject["properties"]!!.jsonObject["code"]!!.jsonObject

        assertFalse(code.containsKey("items"))
    }
}