package me.rerere.rikkahub.data.st.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TavernExtensions 纯 JVM 单元测试：
 * - manifest 解析（全字段 / 缺省回退 / 非法 JSON）
 * - 路径安全（segment 校验 / 目录名净化 / 越界拒绝）
 * - zip 安装（根级 / 嵌套 prefix / zip-slip 防护 / .git & __MACOSX 跳过 / 更新语义）
 * - runtime 注入 JSON 构建（过滤规则与字段契约）
 * - URL → 目录名推导
 */
class TavernExtensionsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    private fun makeZip(name: String, entries: Map<String, String>): File {
        val f = File(tmp.root, name)
        ZipOutputStream(f.outputStream()).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return f
    }

    private fun info(
        folder: String,
        js: String = "index.js",
        css: String = "",
        hasManifest: Boolean = true,
        displayName: String = folder,
        version: String = "1.0",
    ): TavernExtensionInfo = TavernExtensionInfo(
        folder = folder,
        displayName = displayName,
        version = version,
        author = "",
        homePage = "",
        js = js,
        css = css,
        loadingOrder = 100,
        requires = emptyList(),
        hasManifest = hasManifest,
    )

    // ------------------------------------------------------------
    // parseManifest
    // ------------------------------------------------------------

    @Test
    fun parseManifestFull() {
        val parsed = parseManifest(
            "myext",
            """{"display_name":"My Ext","version":"2.0.1","author":"me","homePage":"https://x.example",
               "js":"index.js","css":"style.css","loading_order":5,"requires":["a","b"]}"""
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals("myext", parsed.folder)
        assertEquals("My Ext", parsed.displayName)
        assertEquals("2.0.1", parsed.version)
        assertEquals("me", parsed.author)
        assertEquals("https://x.example", parsed.homePage)
        assertEquals("index.js", parsed.js)
        assertEquals("style.css", parsed.css)
        assertEquals(5, parsed.loadingOrder)
        assertEquals(listOf("a", "b"), parsed.requires)
        assertTrue(parsed.hasManifest)
    }

    @Test
    fun parseManifestMissingNameFallsBackToFolder() {
        val parsed = parseManifest("fallback-name", """{"js":"i.js"}""")
        assertNotNull(parsed)
        assertEquals("fallback-name", parsed!!.displayName)
        assertEquals(100, parsed.loadingOrder)
        assertEquals("", parsed.css)
        assertEquals(emptyList<String>(), parsed.requires)
    }

    @Test
    fun parseManifestHomepageLowercaseFallback() {
        val parsed = parseManifest("x", """{"homepage":"https://lower.example"}""")
        assertEquals("https://lower.example", parsed!!.homePage)
    }

    @Test
    fun parseManifestInvalidJsonReturnsNull() {
        assertNull(parseManifest("x", "not a json"))
        assertNull(parseManifest("x", "[1,2,3]"))
        assertNull(parseManifest("x", ""))
        assertNull(parseManifest("x", "   "))
    }

    // ------------------------------------------------------------
    // path safety
    // ------------------------------------------------------------

    @Test
    fun safeSegmentsAccepted() {
        assertTrue(isSafeExtensionSegment("my-ext"))
        assertTrue(isSafeExtensionSegment("Ext 1.2"))
        assertTrue(isSafeExtensionSegment("日本語"))
    }

    @Test
    fun unsafeSegmentsRejected() {
        assertFalse(isSafeExtensionSegment(""))
        assertFalse(isSafeExtensionSegment("."))
        assertFalse(isSafeExtensionSegment(".."))
        assertFalse(isSafeExtensionSegment("a/b"))
        assertFalse(isSafeExtensionSegment("a\\b"))
        assertFalse(isSafeExtensionSegment("a:b"))
        assertFalse(isSafeExtensionSegment("a*b"))
        assertFalse(isSafeExtensionSegment("a?b"))
        assertFalse(isSafeExtensionSegment("a\"b"))
        assertFalse(isSafeExtensionSegment("a<b"))
        assertFalse(isSafeExtensionSegment("a>b"))
        assertFalse(isSafeExtensionSegment("a|b"))
        assertFalse(isSafeExtensionSegment("a\u0001b"))
    }

    @Test
    fun sanitizeFolderNameBehaviour() {
        assertEquals("my_ext", sanitizeFolderName("my/ext"))
        assertEquals("my_ext", sanitizeFolderName("my\\ext"))
        assertEquals("name", sanitizeFolderName("  name  "))
        assertEquals("a.b", sanitizeFolderName("a.b"))
        assertEquals("C__x", sanitizeFolderName("C:\\x"))
        assertNull(sanitizeFolderName("..."))
        assertNull(sanitizeFolderName("   "))
        assertNull(sanitizeFolderName(""))
    }

    @Test
    fun resolveExtensionFileExisting() {
        val base = tmp.newFolder("exts")
        File(base, "myext/sub").mkdirs()
        File(base, "myext/sub/a.js").writeText("1")
        val hit = resolveExtensionFile(base, "myext", "sub/a.js")
        assertNotNull(hit)
        assertEquals("a.js", hit!!.name)
    }

    @Test
    fun resolveExtensionFileRejectsTraversal() {
        val base = tmp.newFolder("exts2")
        File(base.parentFile, "secret.txt").writeText("s")
        assertNull(resolveExtensionFile(base, "..", "secret.txt"))
        assertNull(resolveExtensionFile(base, "myext", "../secret.txt"))
        assertNull(resolveExtensionFile(base, "myext", "sub/../../oops.txt"))
        assertNull(resolveExtensionFile(base, "myext", ""))
        assertNull(resolveExtensionFile(base, "myext", "missing.js"))
    }

    // ------------------------------------------------------------
    // runtime extensions json
    // ------------------------------------------------------------

    @Test
    fun runtimeExtensionsJsonFilters() {
        val list = listOf(
            info("a"),
            info("b", js = ""),          // 无 js → 排除
            info("c", hasManifest = false), // 无 manifest → 排除
            info("d"),                    // 被禁用 → 排除
        )
        val arr = Json.parseToJsonElement(
            buildRuntimeExtensionsJson(list, disabled = setOf("d"))
        ).jsonArray
        assertEquals(1, arr.size)
        val first = arr[0].jsonObject
        assertEquals("a", first["folder"]!!.jsonPrimitive.content)
        assertEquals("a", first["displayName"]!!.jsonPrimitive.content)
        assertEquals("1.0", first["version"]!!.jsonPrimitive.content)
        assertEquals("index.js", first["js"]!!.jsonPrimitive.content)
        assertEquals("", first["css"]!!.jsonPrimitive.content)
    }

    @Test
    fun runtimeExtensionsJsonEmpty() {
        assertEquals("[]", buildRuntimeExtensionsJson(emptyList(), emptySet()))
    }

    // ------------------------------------------------------------
    // zip install
    // ------------------------------------------------------------

    @Test
    fun installRootZipUsesZipFileName() {
        val dest = tmp.newFolder("dest1")
        val zip = makeZip(
            "myext.zip",
            mapOf(
                "manifest.json" to """{"display_name":"Root Ext","js":"index.js"}""",
                "index.js" to "console.log(1)",
                "style.css" to "body{}",
            )
        )
        val res = installFromZipFile(zip, dest)
        assertTrue(res.isSuccess)
        val out = res.getOrThrow()
        assertEquals("myext", out.folder)
        assertEquals("Root Ext", out.displayName)
        assertTrue(File(dest, "myext/index.js").isFile)
        assertTrue(File(dest, "myext/style.css").isFile)
        assertEquals("console.log(1)", File(dest, "myext/index.js").readText())
    }

    @Test
    fun installNestedZipUsesPrefixFolder() {
        val dest = tmp.newFolder("dest2")
        val zip = makeZip(
            "pack.zip",
            mapOf(
                "MyExt/manifest.json" to """{"js":"index.js"}""",
                "MyExt/index.js" to "x",
                "MyExt/sub/a.js" to "y",
                "Other/ignored.js" to "z",
            )
        )
        val res = installFromZipFile(zip, dest)
        assertTrue(res.isSuccess)
        assertEquals("MyExt", res.getOrThrow().folder)
        assertTrue(File(dest, "MyExt/index.js").isFile)
        assertTrue(File(dest, "MyExt/sub/a.js").isFile)
        assertFalse(File(dest, "Other").exists())
        assertFalse(File(dest, "MyExt/Other/ignored.js").exists())
    }

    @Test
    fun installSourceNameOverridesFolder() {
        val dest = tmp.newFolder("dest3")
        val zip = makeZip("p.zip", mapOf("m/manifest.json" to """{"js":"i.js"}""", "m/i.js" to "1"))
        val res = installFromZipFile(zip, dest, sourceName = "Renamed")
        assertTrue(res.isSuccess)
        assertEquals("Renamed", res.getOrThrow().folder)
        assertTrue(File(dest, "Renamed/i.js").isFile)
    }

    @Test
    fun installZipSlipSkipped() {
        val dest = tmp.newFolder("dest4")
        val zip = makeZip(
            "slip.zip",
            mapOf(
                "x/manifest.json" to """{"js":"index.js"}""",
                "x/index.js" to "ok",
                "x/../evil.txt" to "evil",
                "../evil2.txt" to "evil2",
            )
        )
        val res = installFromZipFile(zip, dest)
        assertTrue(res.isSuccess)
        assertFalse(File(dest, "evil.txt").exists())
        assertFalse(File(dest, "evil2.txt").exists())
        assertFalse(File(dest.parentFile, "evil.txt").exists())
        assertFalse(File(dest.parentFile, "evil2.txt").exists())
        assertTrue(File(dest, "x/index.js").isFile)
    }

    @Test
    fun installSkipsGitAndMacosx() {
        val dest = tmp.newFolder("dest5")
        val zip = makeZip(
            "g.zip",
            mapOf(
                "m/manifest.json" to """{"js":"i.js"}""",
                "m/i.js" to "1",
                "__MACOSX/m/manifest.json" to "junk",
                "m/.git/config" to "gitcfg",
                ".git/config" to "topgit",
            )
        )
        val res = installFromZipFile(zip, dest)
        assertTrue(res.isSuccess)
        assertEquals("m", res.getOrThrow().folder)
        assertFalse(File(dest, "m/.git").exists())
        assertFalse(File(dest, ".git").exists())
        assertTrue(File(dest, "m/i.js").isFile)
    }

    @Test
    fun installWithoutManifestFails() {
        val dest = tmp.newFolder("dest6")
        val zip = makeZip("bad.zip", mapOf("index.js" to "1"))
        assertTrue(installFromZipFile(zip, dest).isFailure)
    }

    @Test
    fun installReplacesExistingFolder() {
        val dest = tmp.newFolder("dest7")
        File(dest, "e").mkdirs()
        File(dest, "e/old.js").writeText("old")
        val zip = makeZip(
            "e.zip",
            mapOf("manifest.json" to """{"js":"index.js"}""", "index.js" to "new")
        )
        val res = installFromZipFile(zip, dest)
        assertTrue(res.isSuccess)
        assertFalse(File(dest, "e/old.js").exists())
        assertTrue(File(dest, "e/index.js").isFile)
        assertEquals("new", File(dest, "e/index.js").readText())
    }

    // ------------------------------------------------------------
    // deriveNameFromUrl
    // ------------------------------------------------------------

    @Test
    fun deriveNameFromUrlCases() {
        assertEquals("bar", deriveNameFromUrl("https://github.com/foo/bar"))
        assertEquals("bar", deriveNameFromUrl("https://github.com/foo/bar.git"))
        assertEquals("bar", deriveNameFromUrl("https://github.com/foo/bar/"))
        assertEquals("bar", deriveNameFromUrl("https://github.com/foo/bar/archive/refs/tags/v1.zip"))
        assertEquals("myext", deriveNameFromUrl("https://example.com/files/myext.zip"))
        assertEquals("myext", deriveNameFromUrl("https://example.com/files/myext.zip?token=1"))
        assertEquals("myext", deriveNameFromUrl("https://example.com/files/myext/"))
    }
}
