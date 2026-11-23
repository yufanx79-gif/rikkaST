package me.rerere.rikkahub.ui.pages.assistant.detail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 character_book 导入时 vectorized 是否被采集（真机日志中该字段缺失）。 */
class EmbeddedBookVectorizedTest {

    @Test
    fun embeddedEntryKeepsVectorizedFlag() {
        val json = """
            {
              "entries": [
                {
                  "id": 1,
                  "keys": ["k1"],
                  "comment": "向量条目",
                  "content": "内容",
                  "constant": false,
                  "selective": true,
                  "vectorized": true,
                  "extensions": { "vectorized": true, "sticky": 0 }
                }
              ]
            }
        """.trimIndent()

        val book = parseEmbeddedBook(Json.parseToJsonElement(json).jsonObject)
        checkNotNull(book) { "book 应解析成功" }
        assertTrue(
            "character_book 条目顶层 vectorized=true 必须在导入后保留",
            book.entries[0].vectorized,
        )
    }

    @Test
    fun embeddedEntryKeepsVectorizedFromExtensionsOnly() {
        val json = """
            {
              "entries": [
                {
                  "id": 2,
                  "keys": ["k2"],
                  "comment": "仅 extensions 带标记",
                  "content": "内容",
                  "constant": false,
                  "extensions": { "vectorized": true }
                }
              ]
            }
        """.trimIndent()

        val book = parseEmbeddedBook(Json.parseToJsonElement(json).jsonObject)
        checkNotNull(book)
        assertTrue(
            "extensions.vectorized=true 必须在导入后保留",
            book.entries[0].vectorized,
        )
    }
}
