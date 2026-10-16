package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v183：C4 渲染判定放宽的回归测试。
 *
 * 真实样本来源：魔法少女卡内嵌正则替换串（staging/card_panels/）：
 * - r1「游玩指南」：``` 围栏 + 完整 HTML，无 <script>（v182 判定失败 → 显示为代码块）
 * - r0「角色创建表单」：```html 围栏 + <script> + <button>/<input>
 * - r10「快速选项模块」：```html 围栏 + <script>（按钮由 JS 动态生成）
 */
class MessageHtmlDetectionTest {

    @Test
    fun fencedHtmlDocumentWithoutScriptIsInteractive() {
        val guide = """
            ```
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><style>.mg-guide-outer { color: red; }</style></head>
            <body><div class="mg-guide-outer"><div class="wrapper">游玩指南内容</div></div></body>
            </html>
            ```
        """.trimIndent()
        assertTrue(looksLikeInteractiveHtml(guide))
    }

    @Test
    fun fencedFormWithScriptIsInteractive() {
        val form = """
            ```html
            <!DOCTYPE html>
            <html lang="zh-CN"><head><meta charset="UTF-8"></head>
            <body>
            <div class="form"><form id="creator-form"><input type="text" name="name"><button type="button" id="generate-btn">签订契约并开始</button></form></div>
            <script>document.addEventListener('DOMContentLoaded', () => { console.log('ready'); });</script>
            </body></html>
            ```
        """.trimIndent()
        assertTrue(looksLikeInteractiveHtml(form))
    }

    @Test
    fun stylePlusBlockContainerIsInteractive() {
        val t = "<style>.a{color:#fff}</style><div class=\"panel\"><span>状态</span></div>"
        assertTrue(looksLikeInteractiveHtml(t))
    }

    @Test
    fun onclickOnlyFragmentIsInteractive() {
        val t = "<div style=\"padding:8px\"><button onclick=\"startGame()\">开始</button></div>"
        assertTrue(looksLikeInteractiveHtml(t))
    }

    @Test
    fun widgetWithStructureIsInteractive() {
        val t = "<div class=\"wrap\"><input type=\"text\"><button>发送</button></div>"
        assertTrue(looksLikeInteractiveHtml(t))
    }

    @Test
    fun plainMarkdownIsNotInteractive() {
        val t = "这是一段普通的 Markdown 文本，提到了组件和 div 的概念，但没有任何 HTML 结构标签，也不会触发内嵌渲染。"
        assertFalse(looksLikeInteractiveHtml(t))
    }

    @Test
    fun escapedScriptIsNotInteractive() {
        val t = "被转义的 &lt;script&gt;alert(1)&lt;/script&gt; 不应触发内嵌渲染，保持普通 Markdown 路径。"
        assertFalse(looksLikeInteractiveHtml(t))
    }

    @Test
    fun shortTextIsNotInteractive() {
        assertFalse(looksLikeInteractiveHtml("<div>x</div>"))
    }

    @Test
    fun cssBackgroundAndFlexGridFragmentsAreInteractiveWhileXmlFenceIsNot() {
        val bgCard = """<div style="background-image: url('https://example.com/bg.png'); display: flex; padding: 12px;"><span>状态栏</span></div>"""
        assertTrue(looksLikeInteractiveHtml(bgCard))

        val svgPanel = """<div class="hud"><svg width="100" height="40"><rect width="100" height="40"/></svg></div>"""
        assertTrue(looksLikeInteractiveHtml(svgPanel))

        val xmlCodeFence = """
            ```xml
            <div class="not-html-render">
                <span style="color:red">This is an XML code snippet</span>
            </div>
            ```
        """.trimIndent()
        assertFalse(looksLikeInteractiveHtml(xmlCodeFence))
    }

    @Test
    fun panelMountStateUsesInPlaceRemountForScriptlessUpdatesAndReloadsForScripts() {
        val state = PanelMountState()
        val html1 = "<style>.card{color:blue}</style><div class=\"card\">Turn 1</div>"
        val html2 = "<style>.card{color:blue}</style><div class=\"card\">Turn 2</div>"
        val htmlWithScript = "<div class=\"card\">Turn 3</div><script>console.log(3)</script>"

        assertEquals(PanelUpdateAction.RELOAD_DOCUMENT, state.decideUpdateAction(html1, 1))
        assertEquals(1, state.reloadCount)
        assertEquals(0, state.inPlaceRemountCount)

        // 同一内容且同一重载代次 → NO_OP
        assertEquals(PanelUpdateAction.NO_OP, state.decideUpdateAction(html1, 1))

        // 纯 HTML/CSS 更新（无 <script>）→ 原地重挂载，不触发整页 reload
        assertEquals(PanelUpdateAction.REMOUINT_IN_PLACE, state.decideUpdateAction(html2, 1))
        assertEquals(1, state.reloadCount)
        assertEquals(1, state.inPlaceRemountCount)

        // 含 <script> 的更新 → 整页 reload 以隔离全局污染并重新触发 DOMContentLoaded
        assertEquals(PanelUpdateAction.RELOAD_DOCUMENT, state.decideUpdateAction(htmlWithScript, 1))
        assertEquals(2, state.reloadCount)
    }

    @Test
    fun tavernRuntimeMimeOfHandlesCaseQueryFragmentsAndMediaExtensions() {
        assertEquals("image/jpeg", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("charx_assets/id/Avatar.JPG?v=1#top"))
        assertEquals("image/webp", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("sprites/joy.WEBP"))
        assertEquals("image/avif", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("bg.avif"))
        assertEquals("font/woff2", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("fonts/custom.WOFF2?ver=2.0"))
        assertEquals("font/ttf", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("fonts/pixel.ttf"))
        assertEquals("audio/mpeg", me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.mimeOf("voice/line01.MP3"))
    }
}
