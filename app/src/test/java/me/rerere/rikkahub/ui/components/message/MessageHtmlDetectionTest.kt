package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v183：C4 渲染判定放宽的回归测试 + P0 多段 `<style>`/容器分段回归测试。
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
        assertFalse(looksLikeHtmlFragment(t))
        assertFalse(shouldRenderAsHtmlPanel(t))
    }

    @Test
    fun escapedScriptIsNotInteractive() {
        val t = "被转义的 &lt;script&gt;alert(1)&lt;/script&gt; 不应触发内嵌渲染，保持普通 Markdown 路径。"
        assertFalse(looksLikeInteractiveHtml(t))
        assertFalse(looksLikeHtmlFragment(t))
    }

    @Test
    fun shortTextIsNotInteractive() {
        assertFalse(looksLikeInteractiveHtml("<div>x</div>"))
        assertFalse(looksLikeHtmlFragment("<div>x</div>"))
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
        assertFalse(looksLikeHtmlFragment(xmlCodeFence))
        assertFalse(shouldRenderAsHtmlPanel(xmlCodeFence))
    }

    @Test
    fun staticPostCardFragmentIsRecognizedAsHtmlPanel() {
        val postCard = """
            <div class="post-card">
                <div class="post-header"><span class="post-author">早川雫</span></div>
                <div class="post-body">雨天便利店的偶遇</div>
            </div>
        """.trimIndent()
        assertTrue(looksLikeHtmlFragment(postCard))
        assertTrue(shouldRenderAsHtmlPanel(postCard))
    }

    @Test
    fun groupTopLevelHtmlNodesDoesNotSwallowFollowingStyleIntoPrecedingPanel() {
        val content = """
            <style>.phone{color:#fff}</style>
            <div class="phone">小手机状态栏内容</div>
            <style>.post-card{background:#111;padding:12px}</style>
            <div class="post-card"><div class="title">动态卡片</div></div>

            这里是后续的小说正文段落。
        """.trimIndent()

        val pieces = splitMixedMessagePieces(content)
        val htmlPanels = pieces.filterIsInstance<MixedContentPiece.HtmlPanel>()
        val mdBodies = pieces.filterIsInstance<MixedContentPiece.MarkdownBody>()

        assertEquals(2, htmlPanels.size)
        assertTrue(htmlPanels[0].html.contains(".phone"))
        assertFalse(htmlPanels[0].html.contains(".post-card"))
        assertTrue(htmlPanels[1].html.contains(".post-card"))
        assertTrue(htmlPanels[1].html.contains("class=\"post-card\""))
        assertEquals(1, mdBodies.size)
        assertFalse(mdBodies[0].text.contains("post-card"))
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

    @Test
    fun activePanelWebViewPoolAndSchedulerEnforceDefaultBoundsAndCleanCancel() {
        ActivePanelWebViewPool.resetForTest()
        TavernPanelLoadScheduler.resetForTest()

        // Default safe cap (6) and mode-specific cap (3)
        for (i in 1..7) {
            ActivePanelWebViewPool.acquire("k$i", maxActive = ActivePanelWebViewPool.DEFAULT_SAFE_MAX_ACTIVE_WEBVIEWS)
        }
        assertEquals(6, ActivePanelWebViewPool.activeKeysFlow.value.size)
        assertFalse("Oldest key k1 must be evicted when 7th is acquired under cap 6", "k1" in ActivePanelWebViewPool.activeKeysFlow.value)
        assertTrue("Newest key k7 must be present", "k7" in ActivePanelWebViewPool.activeKeysFlow.value)

        // Paired release and conversation switch clearAll
        ActivePanelWebViewPool.release("k7")
        assertFalse("k7" in ActivePanelWebViewPool.activeKeysFlow.value)
        ActivePanelWebViewPool.clearAll()
        assertTrue(ActivePanelWebViewPool.activeKeysFlow.value.isEmpty())

        // Cancelled scheduler request cancels gate immediately
        val req1 = TavernPanelLoadScheduler.enqueue("p1", "Panel 1")
        val req2 = TavernPanelLoadScheduler.enqueue("p2", "Panel 2")
        TavernPanelLoadScheduler.cancel("p2")
        assertTrue("Cancelled queued request gate must be cancelled", req2.gate.isCancelled)
        TavernPanelLoadScheduler.cancel("p1")
        TavernPanelLoadScheduler.resetForTest()
    }
}
