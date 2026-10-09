package me.rerere.rikkahub.data.st.import

import me.rerere.rikkahub.data.export.LorebookSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ST 全量导入纯逻辑测试：
 * - 条目分类（目录段匹配 / macOS 垃圾跳过 / 其他忽略）；
 * - settings.json 全局正则提取（extension_settings.regex）；
 * - QuickReplies 解析（qrList → QuickMessage）；
 * - 世界书字符串导入（酒馆格式）。
 */
class StArchiveImportCoreTest {

    // ========== 条目分类 ==========

    @Test
    fun `classify entry kinds by path segments`() {
        assertEquals(StArchiveEntryKind.CHARACTER, classifyStArchiveEntry("characters/Alice.png"))
        assertEquals(StArchiveEntryKind.CHARACTER, classifyStArchiveEntry("characters/bob.json"))
        assertEquals(StArchiveEntryKind.WORLD, classifyStArchiveEntry("data/default-user/worlds/lore.json"))
        assertEquals(StArchiveEntryKind.PRESET, classifyStArchiveEntry("default-user/OpenAI Settings/my preset.json"))
        assertEquals(StArchiveEntryKind.QUICK_REPLIES, classifyStArchiveEntry("QuickReplies/qr.json"))
        assertEquals(StArchiveEntryKind.SETTINGS, classifyStArchiveEntry("data/default-user/settings.json"))
        assertEquals(StArchiveEntryKind.SETTINGS, classifyStArchiveEntry("settings.json"))
    }

    @Test
    fun `classify skips mac junk and unknown files`() {
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("__MACOSX/characters/A.png"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("characters/._Alice.png"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("worlds/notes.txt"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("chats/chat.jsonl"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("characters/"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("backgrounds/bg.png"))
    }

    @Test
    fun `classify respects file extensions per directory`() {
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("characters/card.webp"))
        assertEquals(StArchiveEntryKind.OTHER, classifyStArchiveEntry("worlds/cover.png"))
        assertEquals(StArchiveEntryKind.CHARACTER, classifyStArchiveEntry("SillyTavern-1.12/data/default-user/characters/x.PNG"))
    }

    // ========== settings.json 正则 ==========

    @Test
    fun `parse settings regex from extension_settings`() {
        val json = """
            {
              "theme": "dark",
              "extension_settings": {
                "regex": [
                  {
                    "id": "r1",
                    "scriptName": "clean",
                    "findRegex": "\\s+",
                    "replaceString": " ",
                    "placement": [1, 2],
                    "disabled": false,
                    "markdownOnly": true
                  },
                  {
                    "id": "r2",
                    "scriptName": "second",
                    "findRegex": "a",
                    "replaceString": "b",
                    "placement": [2]
                  }
                ]
              }
            }
        """.trimIndent()
        val scripts = parseStSettingsRegex(json)
        assertEquals(2, scripts.size)
        assertEquals("clean", scripts[0].scriptName)
        assertEquals(listOf(1, 2), scripts[0].placement)
        assertTrue(scripts[0].markdownOnly)
        assertEquals("r2", scripts[1].id)
    }

    @Test
    fun `parse settings regex returns empty when missing or invalid`() {
        assertTrue(parseStSettingsRegex("""{"theme":"x"}""").isEmpty())
        assertTrue(parseStSettingsRegex("""{"extension_settings":{}}""").isEmpty())
        assertTrue(parseStSettingsRegex("not json at all").isEmpty())
    }

    // ========== QuickReplies ==========

    @Test
    fun `parse quick replies maps label and value`() {
        val json = """
            {
              "version": 2,
              "qrList": [
                {"id": "1", "label": "greet", "value": "/send hello"},
                {"id": "2", "label": "note", "value": "world info edit"},
                {"id": "3", "label": "", "value": ""}
              ]
            }
        """.trimIndent()
        val messages = parseStQuickReplies(json)
        assertEquals(2, messages.size)
        assertEquals("greet", messages[0].title)
        assertEquals("/send hello", messages[0].content)
        assertEquals("note", messages[1].title)
        assertFalse(messages[0].autoExecute)
    }

    @Test
    fun `parse quick replies tolerates junk`() {
        assertTrue(parseStQuickReplies("[]").isEmpty())
        assertTrue(parseStQuickReplies("""{"qrList":[]}""").isEmpty())
        assertTrue(parseStQuickReplies("garbage").isEmpty())
    }

    // ========== 世界书字符串导入 ==========

    @Test
    fun `lorebook string import handles sillytavern format`() {
        val json = """
            {
              "entries": {
                "0": {
                  "key": ["alpha"],
                  "keysecondary": [],
                  "content": "hello",
                  "comment": "note",
                  "constant": true,
                  "order": 10,
                  "position": 0,
                  "disable": false
                }
              }
            }
        """.trimIndent()
        val book = LorebookSerializer.importFromString(json, "mybook")
        assertEquals("mybook", book?.name)
        assertEquals(1, book?.entries?.size)
    }

    @Test
    fun `lorebook string import returns null for junk`() {
        assertNull(LorebookSerializer.importFromString("not json at all", "x"))
    }
}