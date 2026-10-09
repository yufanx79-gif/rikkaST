package me.rerere.rikkahub.data.knowledge

import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PdfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * C2 · PDF/EPUB 导入：文档解析层纯 JVM 单测。
 *
 * EpubParser / DocxParser 纯 JVM 可跑（自研 ZipFile + XmlPullParser）；样本用 ZipOutputStream 现场生成。
 * MuPDF(PdfParser) 依赖 native .so，JVM 不可跑，见 ImportRoutingContractTest 与 notes/c2-pdf-epub.md。
 * android.jar 的 XmlPullParserFactory 是 Stub!，本模块 test 源集里的 XmlPullParserFactory.java 是测试替身。
 */
class DocumentImportParserTest {

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun epubEntries(): Map<String, String> = mapOf(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""",
        "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>C2 Test Book</dc:title></metadata>
  <manifest>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>""",
        "OEBPS/chapter1.xhtml" to """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>C2</title></head><body><h1>Chapter One</h1><p>Hello EPUB world</p></body></html>"""
    )

    @Test
    fun `epub parse extracts spine body text with markdown heading`() {
        val file = File.createTempFile("c2-epub-", ".epub")
        try {
            writeZip(file, epubEntries())
            val text = EpubParser.parse(file)
            assertFalse("EPUB 解析不应报错: $text", text.startsWith("Error parsing EPUB file"))
            assertTrue("应抽出正文段落: $text", text.contains("Hello EPUB world"))
            assertTrue("应保留 H1 markdown 前缀: $text", text.contains("# Chapter One"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `epub parse without container returns explicit error`() {
        val file = File.createTempFile("c2-epub-nocontainer-", ".epub")
        try {
            writeZip(file, mapOf("OEBPS/chapter1.xhtml" to "<html><body><p>lonely</p></body></html>"))
            assertEquals("Unable to find OPF file in EPUB", EpubParser.parse(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `epub parse of non-zip bytes does not crash`() {
        val file = File.createTempFile("c2-epub-bad-", ".epub")
        try {
            file.writeText("this is definitely not a zip archive")
            val text = EpubParser.parse(file)
            assertTrue("非法 EPUB 应降级为错误字符串而非抛异常: $text", text.startsWith("Error parsing EPUB file"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `docx parse extracts paragraph and heading text`() {
        val file = File.createTempFile("c2-docx-", ".docx")
        try {
            writeZip(file, mapOf("word/document.xml" to """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Hello DOCX world</w:t></w:r></w:p><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Section</w:t></w:r></w:p></w:body></w:document>"""))
            val text = DocxParser.parse(file)
            assertTrue("应抽出 DOCX 段落: $text", text.contains("Hello DOCX world"))
            assertTrue("应保留 Heading1: $text", text.contains("# Section"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `import pipeline chunks extracted epub text`() {
        val file = File.createTempFile("c2-epub-chunk-", ".epub")
        try {
            writeZip(file, epubEntries())
            val text = EpubParser.parse(file)
            val result = DocumentChunker().chunkDocument(text, chunkSize = 1024, overlap = 200)
            assertTrue("导入文本应被切成 chunk", result.chunks.isNotEmpty())
            assertTrue("chunk 应覆盖正文内容", result.chunks.any { it.text.contains("Hello EPUB world") })
        } finally {
            file.delete()
        }
    }

    @Test
    fun `pdf parser needs native mupdf which is absent on plain jvm`() {
        val file = File.createTempFile("c2-pdf-", ".pdf")
        try {
            file.writeBytes("%PDF-1.4\n%fake\n".toByteArray(Charsets.US_ASCII))
            val outcome = runCatching { PdfParser.parserPdf(file) }
            assertTrue("JVM 上 MuPDF native .so 应不可用（真机验证项）: $outcome", outcome.isFailure)
        } finally {
            file.delete()
        }
    }
}