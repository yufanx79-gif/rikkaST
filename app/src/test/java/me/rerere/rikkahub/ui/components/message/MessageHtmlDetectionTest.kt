package me.rerere.rikkahub.ui.components.message

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
}
