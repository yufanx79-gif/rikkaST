package me.rerere.rikkahub.ui.pages.assistant.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 角色卡文件类型判定：扩展名优先、MIME 兜底。
 *
 * 覆盖用户反馈的「多选时 PNG/JSON 被文件管理器标错 MIME 后判成不支持」场景。
 */
class CardFileKindTest {

    @Test
    fun `known extensions win over mime`() {
        assertEquals(CardFileKind.PNG, resolveCardFileKind("alice.png", "image/png"))
        assertEquals(CardFileKind.JSON, resolveCardFileKind("alice.json", "application/json"))
        assertEquals(CardFileKind.CHARX, resolveCardFileKind("alice.charx", "application/x-charx"))
        assertEquals(CardFileKind.BYAF, resolveCardFileKind("alice.byaf", "application/x-byaf"))
    }

    @Test
    fun `extension survives wrong mime from file manager`() {
        assertEquals(CardFileKind.JSON, resolveCardFileKind("alice.json", "text/plain"))
        assertEquals(CardFileKind.JSON, resolveCardFileKind("alice.json", "application/octet-stream"))
        assertEquals(CardFileKind.CHARX, resolveCardFileKind("alice.charx", "application/octet-stream"))
        assertEquals(CardFileKind.BYAF, resolveCardFileKind("alice.byaf", "application/octet-stream"))
        assertEquals(CardFileKind.PNG, resolveCardFileKind("alice.png", "application/octet-stream"))
    }

    @Test
    fun `uppercase extensions are recognized`() {
        assertEquals(CardFileKind.PNG, resolveCardFileKind("ALICE.PNG", null))
        assertEquals(CardFileKind.JSON, resolveCardFileKind("Alice.Json", null))
        assertEquals(CardFileKind.CHARX, resolveCardFileKind("A.CHARX", null))
        assertEquals(CardFileKind.BYAF, resolveCardFileKind("a.BYAF", null))
    }

    @Test
    fun `mime used only when extension is unknown`() {
        assertEquals(CardFileKind.PNG, resolveCardFileKind("no-extension", "image/png"))
        assertEquals(CardFileKind.JSON, resolveCardFileKind("no-extension", "application/json"))
        assertEquals(CardFileKind.CHARX, resolveCardFileKind("no-extension", "application/zip"))
        assertEquals(CardFileKind.BYAF, resolveCardFileKind("no-extension", "application/x-byaf"))
    }

    @Test
    fun `mime parameters are stripped`() {
        assertEquals(CardFileKind.JSON, resolveCardFileKind(null, "application/json; charset=utf-8"))
    }

    @Test
    fun `unsupported inputs are rejected`() {
        assertEquals(CardFileKind.UNSUPPORTED, resolveCardFileKind("alice.webp", "image/webp"))
        assertEquals(CardFileKind.UNSUPPORTED, resolveCardFileKind("notes.txt", "text/plain"))
        assertEquals(CardFileKind.UNSUPPORTED, resolveCardFileKind(null, null))
        assertEquals(CardFileKind.UNSUPPORTED, resolveCardFileKind("", ""))
        // 只有点没有扩展名
        assertEquals(CardFileKind.UNSUPPORTED, resolveCardFileKind("archive.", "application/octet-stream"))
    }
}
