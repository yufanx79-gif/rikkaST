package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 恢复官方备份后「头像 / 聊天背景 / 附件全部空白」的回归防护。
 *
 * 根因：备份里的文件引用是**绝对路径**，含导出时那个安装的包名
 * （`me.rerere.rikkahub`）。本应用是独立包名 `me.rerere.rikkahub.st`，
 * 数据目录不同，于是所有 `file:///data/user/0/me.rerere.rikkahub/files/...`
 * 都指向不存在的文件。文件本身在备份里是齐的，只是路径写错。
 *
 * 修复必须同时满足两点，否则会造成新的问题：
 *  1. 该改的必须改（否则图片依旧不显示）；
 *  2. 不该改的绝不能改（外部路径、已正确的路径、文件不存在的路径）。
 */
class BackupPathHealerTest {

    private val pkg = "me.rerere.rikkahub.st"
    private val filesDir = "/data/user/0/me.rerere.rikkahub.st/files"

    /** 假定本机存在这些文件（相对 filesDir 的路径）。 */
    private fun exists(vararg present: String): (String) -> Boolean {
        val set = present.toSet()
        return { relative -> relative in set }
    }

    private fun rebase(path: String, exists: (String) -> Boolean): String? =
        BackupPathHealer.rebase(path, pkg, filesDir, exists)

    // ── 必须修复的情况 ────────────────────────────────────────────

    @Test
    fun `官方包名的头像路径改写成本机包名`() {
        val actual = rebase(
            "file:///data/user/0/me.rerere.rikkahub/files/upload/095981d4.jpg",
            exists("upload/095981d4.jpg"),
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub.st/files/upload/095981d4.jpg",
            actual,
        )
    }

    @Test
    fun `聊天背景路径同样被改写`() {
        val actual = rebase(
            "file:///data/user/0/me.rerere.rikkahub/files/upload/70d77dbd.png",
            exists("upload/70d77dbd.png"),
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub.st/files/upload/70d77dbd.png",
            actual,
        )
    }

    @Test
    fun `没有 file 前缀的裸绝对路径也能改写`() {
        val actual = rebase(
            "/data/user/0/me.rerere.rikkahub/files/upload/a.jpg",
            exists("upload/a.jpg"),
        )
        assertEquals("/data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg", actual)
    }

    @Test
    fun `多用户目录 user 10 也支持`() {
        val actual = rebase(
            "file:///data/user/10/me.rerere.rikkahub/files/upload/a.jpg",
            exists("upload/a.jpg"),
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg",
            actual,
        )
    }

    @Test
    fun `子目录里的文件也能改写`() {
        val actual = rebase(
            "file:///data/user/0/me.rerere.rikkahub/files/skills/my-skill/SKILL.md",
            exists("skills/my-skill/SKILL.md"),
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub.st/files/skills/my-skill/SKILL.md",
            actual,
        )
    }

    // ── 绝不能改的情况（安全边界）────────────────────────────────

    @Test
    fun `已经是本机包名时不改（幂等）`() {
        assertNull(
            rebase(
                "file:///data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg",
                exists("upload/a.jpg"),
            )
        )
    }

    @Test
    fun `目标文件不存在时不改 —— 避免改成同样无效的路径`() {
        // 用户可能只恢复了设置没恢复文件；此时保持原样更好排查。
        assertNull(
            rebase(
                "file:///data/user/0/me.rerere.rikkahub/files/upload/missing.jpg",
                exists(),
            )
        )
    }

    @Test
    fun `外部存储路径不改`() {
        assertNull(rebase("/sdcard/Download/pic.jpg", exists("upload/pic.jpg")))
    }

    @Test
    fun `content URI 不改`() {
        assertNull(rebase("content://media/external/images/1", exists("upload/pic.jpg")))
    }

    @Test
    fun `网络 URL 不改`() {
        assertNull(rebase("https://example.com/a.jpg", exists("upload/pic.jpg")))
    }

    @Test
    fun `别的应用的数据目录不改`() {
        // 不是 /files/ 约定，不动。
        assertNull(
            rebase(
                "file:///data/user/0/com.other.app/databases/x.db",
                exists("databases/x.db"),
            )
        )
    }

    @Test
    fun `files 之外的子目录不改`() {
        assertNull(
            rebase(
                "file:///data/user/0/me.rerere.rikkahub/cache/tmp.jpg",
                exists("cache/tmp.jpg"),
            )
        )
    }

    // ── 文本（消息 JSON）改写 ─────────────────────────────────────

    @Test
    fun `消息 JSON 里的路径被替换`() {
        val json = """[{"type":"image","url":"file:///data/user/0/me.rerere.rikkahub/files/upload/a.jpg"}]"""
        val actual = BackupPathHealer.rewriteText(
            json, pkg, filesDir, exists("upload/a.jpg"),
        )
        assertEquals(
            """[{"type":"image","url":"file:///data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg"}]""",
            actual,
        )
    }

    @Test
    fun `消息 JSON 里文件不存在的路径保持原样`() {
        val json = """{"url":"file:///data/user/0/me.rerere.rikkahub/files/upload/missing.jpg"}"""
        val actual = BackupPathHealer.rewriteText(json, pkg, filesDir, exists())
        assertEquals(json, actual)
    }

    @Test
    fun `一条文本里多个路径全部替换`() {
        val text = "a=file:///data/user/0/me.rerere.rikkahub/files/upload/a.jpg " +
            "b=file:///data/user/0/me.rerere.rikkahub/files/upload/b.png"
        val actual = BackupPathHealer.rewriteText(
            text, pkg, filesDir, exists("upload/a.jpg", "upload/b.png"),
        )
        assertEquals(
            "a=file:///data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg " +
                "b=file:///data/user/0/me.rerere.rikkahub.st/files/upload/b.png",
            actual,
        )
    }

    @Test
    fun `不含路径的文本原样返回`() {
        val text = """{"text":"今天天气不错"}"""
        assertEquals(text, BackupPathHealer.rewriteText(text, pkg, filesDir, exists()))
    }

    @Test
    fun `路径后面的 JSON 结构不被吞掉`() {
        // 正则必须停在引号处，不能把 `","role":"user"}` 一起吃进来。
        val text = """{"url":"file:///data/user/0/me.rerere.rikkahub/files/upload/a.jpg","role":"user"}"""
        val actual = BackupPathHealer.rewriteText(
            text, pkg, filesDir, exists("upload/a.jpg"),
        )
        assertEquals(
            """{"url":"file:///data/user/0/me.rerere.rikkahub.st/files/upload/a.jpg","role":"user"}""",
            actual,
        )
    }

    @Test
    fun `重复执行结果不变（幂等）`() {
        val original = "file:///data/user/0/me.rerere.rikkahub/files/upload/a.jpg"
        val once = BackupPathHealer.rewriteText(original, pkg, filesDir, exists("upload/a.jpg"))
        val twice = BackupPathHealer.rewriteText(once, pkg, filesDir, exists("upload/a.jpg"))
        assertEquals(once, twice)
    }

    // ── 恢复时「文件还在暂存区」的场景 ──────────────────────────

    @Test
    fun `恢复时用暂存区做存在性检查 —— 文件尚未落到 filesDir 也要能改写`() {
        // 这是恢复流程的真实情况：附件解包在 payload/files，
        // 尚未 publish 到 filesDir。若用 filesDir 判断会全部跳过。
        val staged = "upload/095981d4.jpg"
        val actual = BackupPathHealer.rewriteText(
            text = "file:///data/user/0/me.rerere.rikkahub/files/upload/095981d4.jpg",
            currentPackage = pkg,
            filesDirAbsolute = filesDir,
            exists = { it == staged },
        )
        assertEquals(
            "file:///data/user/0/me.rerere.rikkahub.st/files/upload/095981d4.jpg",
            actual,
        )
    }

    @Test
    fun `暂存区里也没有的文件不改写`() {
        val text = "file:///data/user/0/me.rerere.rikkahub/files/upload/nope.jpg"
        assertEquals(
            text,
            BackupPathHealer.rewriteText(text, pkg, filesDir) { false },
        )
    }
}
