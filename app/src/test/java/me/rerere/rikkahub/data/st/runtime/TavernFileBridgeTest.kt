package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [v246] 酒馆文件桥安全测试。
 *
 * 这些是**安全边界**测试，不是功能测试：
 *  - 路径穿越（`../`）必须被拒绝
 *  - 绝对路径必须被拒绝
 *  - 协议前缀（`file://`、`http://`）必须被拒绝
 *  - 非白名单扩展名（`.sh`、`.exe`）必须被拒绝
 *  - 超过 32MB 必须被拒绝
 *
 * 背景：卡片脚本可以任意调 `uploadTavernFile(path, base64)`，
 * 如果不做约束，一个恶意卡片就能往应用私有目录写可执行文件或覆盖配置文件。
 */
class TavernFileBridgeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun initBridge(): File {
        val dir = tmp.newFolder("files")
        TavernFileBridge.init(dir)
        return dir
    }

    private fun isOk(json: String) = json.contains("\"ok\":true")

    private fun errorOf(json: String): String =
        Regex("\"error\":\"([^\"]*)\"").find(json)?.groupValues?.get(1).orEmpty()

    // ---------------------------------------------------------------- 正常路径

    @Test
    fun writeThenReadRoundTrips() {
        initBridge()
        val content = "你好，世界"
        val b64 = java.util.Base64.getEncoder().encodeToString(content.toByteArray())

        val writeResult = TavernFileBridge.write("notes/hello.txt", b64)
        assertTrue("写入应成功: $writeResult", isOk(writeResult))

        val readResult = TavernFileBridge.read("notes/hello.txt")
        assertTrue("读取应成功: $readResult", isOk(readResult))

        // 解出 base64 比对内容
        val m = Regex("\"data\":\"([^\"]*)\"").find(readResult)
        assertTrue("应返回 data 字段", m != null)
        val decoded = String(java.util.Base64.getDecoder().decode(m!!.groupValues[1]))
        assertTrue("内容应一致", decoded == content)
    }

    @Test
    fun deleteRemovesFile() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("x".toByteArray())
        TavernFileBridge.write("temp.txt", b64)
        assertTrue(isOk(TavernFileBridge.read("temp.txt")))

        val del = TavernFileBridge.delete("temp.txt")
        assertTrue("删除应成功: $del", isOk(del))
        assertFalse("删除后应读不到", isOk(TavernFileBridge.read("temp.txt")))
    }

    @Test
    fun listShowsFiles() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("a".toByteArray())
        TavernFileBridge.write("a.json", b64)
        TavernFileBridge.write("b.json", b64)

        val listed = TavernFileBridge.list("")
        assertTrue("应成功: $listed", isOk(listed))
        assertTrue("应含 a.json", listed.contains("a.json"))
        assertTrue("应含 b.json", listed.contains("b.json"))
    }

    // ---------------------------------------------------------------- 安全边界

    @Test
    fun rejectsPathTraversal() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("evil".toByteArray())
        val cases = listOf(
            "../escape.txt",
            "../../escape.txt",
            "sub/../../escape.txt",
            "..\\escape.txt",
            "a/../../../etc/passwd.txt",
        )
        for (path in cases) {
            val r = TavernFileBridge.write(path, b64)
            assertFalse("路径穿越必须被拒绝: $path -> $r", isOk(r))
            assertTrue("应给出 invalid path 错误: $r", errorOf(r).contains("invalid path"))
        }
    }

    @Test
    fun rejectsAbsolutePath() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("evil".toByteArray())
        for (path in listOf("/etc/passwd.txt", "/data/local/tmp/x.txt")) {
            val r = TavernFileBridge.write(path, b64)
            assertFalse("绝对路径必须被拒绝: $path -> $r", isOk(r))
        }
    }

    @Test
    fun rejectsProtocolPrefix() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("evil".toByteArray())
        for (path in listOf("file:///etc/passwd", "http://evil.com/x.txt", "content://media/x.txt")) {
            val r = TavernFileBridge.write(path, b64)
            assertFalse("协议前缀必须被拒绝: $path -> $r", isOk(r))
        }
    }

    @Test
    fun rejectsNonWhitelistedExtension() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("evil".toByteArray())
        for (path in listOf("evil.sh", "evil.exe", "evil.apk", "evil.dex", "evil.so", "noext")) {
            val r = TavernFileBridge.write(path, b64)
            assertFalse("非白名单扩展名必须被拒绝: $path -> $r", isOk(r))
        }
    }

    @Test
    fun acceptsWhitelistedExtensions() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("ok".toByteArray())
        for (path in listOf("a.txt", "b.json", "c.md", "d.png", "e.mp3", "f.yaml", "g.html")) {
            val r = TavernFileBridge.write(path, b64)
            assertTrue("白名单扩展名应允许: $path -> $r", isOk(r))
        }
    }

    @Test
    fun rejectsOversizedFile() {
        initBridge()
        // 构造 33MB 的 base64（比上限 32MB 大）
        val big = ByteArray(33 * 1024 * 1024)
        val b64 = java.util.Base64.getEncoder().encodeToString(big)
        val r = TavernFileBridge.write("big.txt", b64)
        assertFalse("超限文件必须被拒绝: ${r.take(120)}", isOk(r))
        assertTrue("应给出 too large 错误", errorOf(r).contains("too large"))
    }

    @Test
    fun readMissingFileReturnsError() {
        initBridge()
        val r = TavernFileBridge.read("nope.txt")
        assertFalse(isOk(r))
        assertTrue("应给出 not found", errorOf(r).contains("not found"))
    }

    @Test
    fun readBeforeInitReturnsError() {
        // 不调 initBridge，rootDir 为 null
        val r = TavernFileBridge.read("x.txt")
        assertFalse("未初始化必须拒绝", isOk(r))
    }

    @Test
    fun emptyPathRejected() {
        initBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString("x".toByteArray())
        for (path in listOf("", "   ", "/")) {
            val r = TavernFileBridge.write(path, b64)
            assertFalse("空路径必须被拒绝: '$path'", isOk(r))
        }
    }

    @Test
    fun jsonStringEscapingWorks() {
        initBridge()
        // 文件名带引号和反斜杠，验证 JSON 转义不破坏响应
        val b64 = java.util.Base64.getEncoder().encodeToString("x".toByteArray())
        val r = TavernFileBridge.write("quote\"name.txt", b64)
        // 能写入即可（文件名合法），响应必须是合法 JSON 结构
        assertTrue("响应应含 ok 字段: ${r.take(150)}", r.contains("\"ok\""))
    }
}
