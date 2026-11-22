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

    @Test
    fun classifyPanelStaticDistinguishesAllFivePanelClasses() {
        PanelClassificationCache.resetForTest()

        // 1. STATIC：纯样式卡片，无脚本、无事件、无动画、无交互元素
        val staticCard = """
            <style>.narration{border:1px solid #444;padding:12px;border-radius:8px;}</style>
            <div class="narration"><p>雨声敲打着便利店的玻璃窗。</p></div>
        """.trimIndent()
        assertEquals(PanelClass.STATIC, classifyPanelStatic(staticCard))
        assertEquals("雨声敲打着便利店的玻璃窗。", extractStaticPanelPlainText(staticCard))

        // 2. ANIMATED：含 CSS 动画/粒子，无交互元素
        val animatedCard = """
            <style>@keyframes float{0%{opacity:.4}100%{opacity:1}}.particle{animation:float 2s infinite}</style>
            <div class="scene"><span class="particle">✦</span></div>
        """.trimIndent()
        assertEquals(PanelClass.ANIMATED, classifyPanelStatic(animatedCard))

        // 3. LIGHT：展开收起 / details 轻交互，不发消息
        val lightDetails = """
            <details class="thought"><summary>思考过程与好感度</summary><div>好感度 +3</div></details>
        """.trimIndent()
        assertEquals(PanelClass.LIGHT, classifyPanelStatic(lightDetails))

        // 4. SEND：调用宿主桥发消息
        val sendButtons = """
            <div class="actions"><button onclick="TavernHelper.triggerSlash('/send 继续剧情')">继续剧情</button></div>
        """.trimIndent()
        assertEquals(PanelClass.SEND, classifyPanelStatic(sendButtons))

        // 5. BIG：角色卡大面板（内部滚动容器 + 多角色切换）
        val bigPanel = """
            <style>.roster{max-height:320px;overflow-y:auto;display:flex;}</style>
            <div class="roster"><button onclick="selectChar(1)">角色A</button><button onclick="selectChar(2)">角色B</button></div>
        """.trimIndent()
        assertEquals(PanelClass.BIG, classifyPanelStatic(bigPanel))

        // 运行时探测缓存与升级
        assertEquals(PanelClass.STATIC, PanelClassificationCache.getOrClassify(staticCard))
        PanelClassificationCache.recordRuntimeClass(staticCard.hashCode(), PanelClass.LIGHT)
        assertEquals(PanelClass.LIGHT, PanelClassificationCache.getCached(staticCard.hashCode()))
        PanelClassificationCache.resetForTest()
    }

    @Test
    fun priorityPoolProtectsLatestFloorAndRespectsMemoryTiersAndSnapshotKey() {
        ActivePanelWebViewPool.resetForTest()

        // 内存分级校验（初值：<=6GB -> 4, 8GB -> 8, >=12GB -> 16；手动覆盖上限 16）
        assertEquals(4, ActivePanelWebViewPool.resolveMemoryTierMaxActive(6L * 1024L * 1024L * 1024L))
        assertEquals(8, ActivePanelWebViewPool.resolveMemoryTierMaxActive(8L * 1024L * 1024L * 1024L))
        assertEquals(16, ActivePanelWebViewPool.resolveMemoryTierMaxActive(12L * 1024L * 1024L * 1024L))
        assertEquals(16, ActivePanelWebViewPool.resolveMemoryTierMaxActive(6L * 1024L * 1024L * 1024L, userOverride = 24))
        assertEquals(10, ActivePanelWebViewPool.resolveMemoryTierMaxActive(6L * 1024L * 1024L * 1024L, userOverride = 10))

        // 优先级淘汰：最新楼（LATEST_FLOOR）和正在交互（INTERACTING）绝不先于 BACKGROUND 被淘汰
        ActivePanelWebViewPool.acquire("latest", maxActive = 2, priority = PanelPoolPriority.LATEST_FLOOR)
        ActivePanelWebViewPool.acquire("bg1", maxActive = 2, priority = PanelPoolPriority.BACKGROUND)
        ActivePanelWebViewPool.acquire("bg2", maxActive = 2, priority = PanelPoolPriority.BACKGROUND)
        assertTrue("latest" in ActivePanelWebViewPool.activeKeysFlow.value)
        assertFalse("bg1" in ActivePanelWebViewPool.activeKeysFlow.value)
        assertTrue("bg2" in ActivePanelWebViewPool.activeKeysFlow.value)
        assertEquals(1L, ActivePanelWebViewPool.stats().evictionCount)

        // 快照 Key 随主题 / 宽度 / 字号变化而失效
        val kLight = PanelSnapshotStore.compositeKey("m1", 123, isDarkTheme = false, widthPx = 1080, fontScale = 1.0f)
        val kDark = PanelSnapshotStore.compositeKey("m1", 123, isDarkTheme = true, widthPx = 1080, fontScale = 1.0f)
        val kWide = PanelSnapshotStore.compositeKey("m1", 123, isDarkTheme = false, widthPx = 1440, fontScale = 1.0f)
        val kBigFont = PanelSnapshotStore.compositeKey("m1", 123, isDarkTheme = false, widthPx = 1080, fontScale = 1.25f)
        assertTrue(kLight != kDark)
        assertTrue(kLight != kWide)
        assertTrue(kLight != kBigFont)

        ActivePanelWebViewPool.resetForTest()
    }
}
