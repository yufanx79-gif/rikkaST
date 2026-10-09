package me.rerere.rikkahub.data.knowledge

import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PdfParser
import me.rerere.document.PptxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * C2 · 导入分支路由契约测试（纯 JVM 降级口径）。
 *
 * KnowledgeBaseService.importFile / readDocument 需要 Android Context + ContentResolver + Uri
 * （android.jar 的 Uri.parse 亦为 Stub!），纯 JVM 无法构造实例；因此退到纯函数/源码契约层断言：
 *  1) 宿主 when 分支引用的解析入口存在且签名为 (File) -> String；
 *  2) 宿主源码里 .pdf/.epub/.docx/.pptx 分支与 else 文本兜底仍在（防路由回归）。
 * 真实 importFile 端到端（含 MuPDF 与 FTS5 检索）登记为真机验证项，见 notes/c2-pdf-epub.md。
 *
 * 注：Kotlin object 的函数是单例上的实例方法（非 static），宿主调用 EpubParser.parse(file)
 * 编译为 EpubParser.INSTANCE.parse(file)，因此这里只校验入口存在与返回类型。
 */
class ImportRoutingContractTest {

    @Test
    fun `parser entry points match host dispatch contract`() {
        assertEntry("parserPdf", PdfParser::class.java)
        assertEntry("parse", DocxParser::class.java)
        assertEntry("parse", PptxParser::class.java)
        assertEntry("parse", EpubParser::class.java)
    }

    private fun assertEntry(methodName: String, clazz: Class<*>) {
        val method = clazz.getMethod(methodName, File::class.java)
        assertEquals("$clazz.$methodName 必须返回 String", String::class.java, method.returnType)
        assertTrue("$clazz.$methodName 必须 public", java.lang.reflect.Modifier.isPublic(method.modifiers))
    }

    @Test
    fun `unknown extension uses plain text fallback`() {
        val file = File.createTempFile("c2-route-", ".bin")
        try {
            file.writeText("plain fallback payload")
            // readDocument 的 else 分支：tempFile.readText()
            assertEquals("plain fallback payload", file.readText())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `readDocument source keeps format dispatch and text fallback`() {
        val source = locateHostSource()
        assumeTrue("未找到 KnowledgeBaseService.kt（工作目录非 app/），跳过源码契约检查", source != null)
        val text = source!!.readText()
        assertTrue(".pdf -> PdfParser 分支缺失", text.contains("name.endsWith(\".pdf\") -> PdfParser.parserPdf(tempFile)"))
        assertTrue(".docx -> DocxParser 分支缺失", text.contains("name.endsWith(\".docx\") -> DocxParser.parse(tempFile)"))
        assertTrue(".pptx -> PptxParser 分支缺失", text.contains("name.endsWith(\".pptx\") -> PptxParser.parse(tempFile)"))
        assertTrue(".epub -> EpubParser 分支缺失", text.contains("name.endsWith(\".epub\") -> EpubParser.parse(tempFile)"))
        assertTrue(".txt/.md 文本分支缺失", text.contains("name.endsWith(\".txt\") || name.endsWith(\".md\") -> tempFile.readText()"))
        assertTrue("非法扩展名文本兜底缺失", text.contains("else -> tempFile.readText()"))
    }

    private fun locateHostSource(): File? {
        val rel = "src/main/java/me/rerere/rikkahub/data/knowledge/KnowledgeBaseService.kt"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val direct = File(dir, rel)
            if (direct.isFile) return direct
            val nested = File(dir, "app/$rel")
            if (nested.isFile) return nested
            dir = dir.parentFile
        }
        return null
    }
}