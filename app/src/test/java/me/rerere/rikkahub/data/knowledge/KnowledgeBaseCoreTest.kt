package me.rerere.rikkahub.data.knowledge

import me.rerere.rikkahub.data.db.entity.KnowledgeChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识库核心纯逻辑测试（P3 复产）：
 * - KnowledgeChunkEntity.floatsToBytes / bytesToFloats：embedding 向量序列化回环；
 * - DocumentChunker：空输入、短文本、确定性递归拆分、重叠合并、段落优先、overlap 守卫。
 *
 * 所有断言基于纯 JVM 逻辑，不依赖 Android 运行时。
 */
class KnowledgeBaseCoreTest {

    // ========== 向量序列化 ==========

    @Test
    fun `embedding bytes roundtrip preserves values`() {
        val values = listOf(0f, 1f, -1.5f, 3.1415f, -0.001f, 1e6f)
        val bytes = KnowledgeChunkEntity.floatsToBytes(values)
        assertEquals("每 float 占 4 字节", values.size * 4, bytes.size)
        val restored = KnowledgeChunkEntity.bytesToFloats(bytes)
        assertEquals(values, restored)
    }

    @Test
    fun `embedding empty roundtrip is empty`() {
        val bytes = KnowledgeChunkEntity.floatsToBytes(emptyList())
        assertEquals(0, bytes.size)
        assertTrue(KnowledgeChunkEntity.bytesToFloats(ByteArray(0)).isEmpty())
    }

    // ========== 分块引擎 ==========

    @Test
    fun `blank input yields no chunks`() {
        val chunker = DocumentChunker()
        val result = chunker.chunkDocument("   \n  ")
        assertTrue(result.chunks.isEmpty())
        assertEquals(0, result.sentenceCount)
    }

    @Test
    fun `short text stays as single chunk`() {
        val chunker = DocumentChunker()
        val result = chunker.chunkDocument("短文本。")
        assertEquals(1, result.chunks.size)
        assertEquals("短文本。", result.chunks[0].text)
        assertEquals(0, result.chunks[0].sentenceStart)
        assertEquals(0, result.chunks[0].sentenceEnd)
    }

    @Test
    fun `deterministic split without overlap`() {
        val chunker = DocumentChunker()
        // 6 句 × 3 token（5 字 × 0.75 取整），chunkSize=10 → 前 3 句一块、后 3 句一块
        val text = "AAAA。BBBB。CCCC。DDDD。EEEE。FFFF。"
        val result = chunker.chunkDocument(text, chunkSize = 10, overlap = 0)
        assertEquals(2, result.chunks.size)
        assertEquals("AAAA。BBBB。CCCC。", result.chunks[0].text)
        assertEquals("DDDD。EEEE。FFFF。", result.chunks[1].text)
        assertEquals(0, result.chunks[0].sentenceStart)
        assertEquals(2, result.chunks[0].sentenceEnd)
        assertEquals(3, result.chunks[1].sentenceStart)
        assertEquals(5, result.chunks[1].sentenceEnd)
        // 无重叠时全部内容拼接还原
        assertEquals(text, result.chunks.joinToString("") { it.text })
    }

    @Test
    fun `overlap merges back previous sentence`() {
        val chunker = DocumentChunker()
        val text = "AAAA。BBBB。CCCC。DDDD。EEEE。FFFF。"
        val result = chunker.chunkDocument(text, chunkSize = 10, overlap = 3)
        assertEquals(3, result.chunks.size)
        assertEquals("AAAA。BBBB。CCCC。", result.chunks[0].text)
        assertEquals("CCCC。DDDD。EEEE。", result.chunks[1].text)
        assertEquals("EEEE。FFFF。", result.chunks[2].text)
        // 相邻块确实共享重叠句
        assertTrue(result.chunks[0].text.contains("CCCC。"))
        assertTrue(result.chunks[1].text.contains("CCCC。"))
        assertTrue(result.chunks[1].text.contains("EEEE。"))
        assertTrue(result.chunks[2].text.contains("EEEE。"))
        // 注：sentenceStart/End 是"合并序列累计编号"（重叠句会重复计数），非原始句索引
        assertEquals(3, result.chunks[1].sentenceStart)
        assertEquals(5, result.chunks[1].sentenceEnd)
        assertEquals(6, result.chunks[2].sentenceStart)
        assertEquals(7, result.chunks[2].sentenceEnd)
    }

    @Test
    fun `paragraph separator has priority`() {
        val chunker = DocumentChunker()
        val text = "段落一。\n\n\n段落二。"
        val result = chunker.chunkDocument(text, chunkSize = 5, overlap = 0)
        assertEquals(2, result.chunks.size)
        assertEquals("段落一。", result.chunks[0].text)
        assertEquals("段落二。", result.chunks[1].text)
    }

    @Test
    fun `overlap guard avoids infinite loop when overlap exceeds chunk size`() {
        val chunker = DocumentChunker()
        val text = "AAAA。BBBB。CCCC。DDDD。EEEE。FFFF。"
        val result = chunker.chunkDocument(text, chunkSize = 10, overlap = 10)
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.chunks.all { it.text.isNotBlank() })
    }

    @Test
    fun `semantic chunking delegates to same strategy`() {
        val chunker = DocumentChunker()
        val text = "AAAA。BBBB。CCCC。DDDD。EEEE。FFFF。"
        val a = chunker.chunkDocument(text, chunkSize = 10, overlap = 0)
        val b = chunker.chunkDocumentSemantic(text, chunkSize = 10, overlap = 0)
        assertEquals(a.chunks.map { it.text }, b.chunks.map { it.text })
        assertEquals(a.sentenceCount, b.sentenceCount)
    }
}
