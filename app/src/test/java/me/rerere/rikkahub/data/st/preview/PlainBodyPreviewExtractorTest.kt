package me.rerere.rikkahub.data.st.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainBodyPreviewExtractorTest {

    @Test
    fun `剥离助手消息中的 HTML 面板、style、script 与标签只保留纯正文`() {
        val raw = """
            她轻轻推开木门，阳光洒在书架上。
            ```html
            <div class="status-panel"><style>.x{color:red}</style><script>alert(1)</script>HP: 100</div>
            ```
            <div class="card"><b>内心独白</b></div>&nbsp;“欢迎回来。”
        """.trimIndent()
        val extracted = PlainBodyPreviewExtractor.extract(raw)
        assertTrue(extracted.contains("她轻轻推开木门"))
        assertTrue(extracted.contains("欢迎回来"))
        assertFalse(extracted.contains("<div"))
        assertFalse(extracted.contains("<style"))
        assertFalse(extracted.contains("alert(1)"))
    }

    @Test
    fun `超长正文按 maxChars 截断并加省略号`() {
        val longText = "这是一段很长的剧情正文。".repeat(40)
        val extracted = PlainBodyPreviewExtractor.extract(longText, maxChars = 60)
        assertTrue(extracted.length <= 61)
        assertTrue(extracted.endsWith("…"))
    }
}
