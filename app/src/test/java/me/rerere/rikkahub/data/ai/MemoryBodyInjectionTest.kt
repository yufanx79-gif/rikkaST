package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.ai.prompts.buildMemoryPrompt
import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆正文注入。
 *
 * 真机回归（2026-11-14 测试 B）：新对话里模型只拿到 `<memory_listing>`（路径+描述），
 * 看不到正文，于是被问「用户的宠物叫什么」时回答「不知道豆豆」——
 * 而豆豆就写在 `/keep-me.md` 里。
 *
 * 这里锁死：短文件必须注入正文，超预算的文件才退回清单。
 */
class MemoryBodyInjectionTest {

    private fun file(path: String, body: String, updatedAt: Long = 1_000L) = MemoryFile(
        path = path,
        content = "---\nname: ${path.trim('/').removeSuffix(".md")}\ndescription: test\n---\n\n$body",
        updatedAt = updatedAt,
    )

    @Test
    fun `short memory file body is injected so the model knows it without calling memory_read`() {
        val prompt = buildMemoryPrompt(
            listOf(file("/keep-me.md", "- [stated] 用户的宠物叫豆豆，今年三岁。"))
        )
        // 清单仍在
        assertTrue(prompt.contains("<memory_listing>"))
        assertTrue(prompt.contains("/keep-me.md"))
        // 关键：正文必须出现
        assertTrue("正文必须注入，否则模型答不出豆豆", prompt.contains("豆豆"))
        assertTrue(prompt.contains("<memory_files>"))
        assertTrue(prompt.contains("<file path=\"/keep-me.md\""))
    }

    @Test
    fun `large memory file is not inlined to avoid blowing the context`() {
        val huge = "- [stated] " + "x".repeat(20_000)
        val prompt = buildMemoryPrompt(listOf(file("/huge.md", huge)))
        // 清单保留
        assertTrue(prompt.contains("/huge.md"))
        // 正文不注入
        assertFalse(prompt.contains("<memory_files>"))
        assertFalse(prompt.contains("x".repeat(20_000)))
        assertTrue(prompt.contains("call memory_read"))
    }

    @Test
    fun `budget is spent on the most recently updated files first`() {
        val old = file("/old.md", "- [stated] 旧内容标记 OLD-MARK", updatedAt = 1_000L)
        val fresh = file("/fresh.md", "- [stated] 新内容标记 NEW-MARK", updatedAt = 9_000L)
        val prompt = buildMemoryPrompt(listOf(old, fresh))
        assertTrue(prompt.contains("NEW-MARK"))
    }

    @Test
    fun `pinned profile and preferences are still rendered`() {
        val prompt = buildMemoryPrompt(
            listOf(
                MemoryFile(
                    path = MemoryFile.PROFILE_PATH,
                    content = "---\nname: profile\n---\n\n- [stated] 用户是程序员",
                    updatedAt = 1L,
                ),
                file("/topics/pets.md", "- [stated] 喜欢猫"),
            )
        )
        assertTrue(prompt.contains("<profile"))
        assertTrue(prompt.contains("用户是程序员"))
        assertTrue(prompt.contains("<preferences"))
        assertTrue(prompt.contains("喜欢猫"))
    }
}
