package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ST 宿主约定守卫。
 *
 * 背景：真实第三方插件依赖 ST 宿主页（public/index.html）里的
 * 若干「非 JS 模块」资产——模板元素、全局库。这些东西我们在
 * assets/st-runtime/index.html 里自己重建，很容易在后续改动中丢。
 *
 * 每一条断言都对应一个**真实插件**的实测失败，不是推测：
 *  - generic_draggable_template → Extension-TopInfoBar
 *    "Cannot read properties of undefined (reading 'cloneNode')"
 *  - showdown 全局 → LALib
 *    "showdown is not defined"
 *  - DOMPurify 完整实例 → SillyTavern-EchoChamber
 *    "DOMPurify.sanitize is not a function"
 */
class StHostContractTest {

    private val runtimeDir = File("src/main/assets/st-runtime")

    private fun read(rel: String): String {
        val f = File(runtimeDir, rel)
        assertTrue("缺少资产: $rel", f.isFile)
        return f.readText()
    }

    @Test
    fun `host pages define the draggable template third-party extensions clone`() {
        for (page in listOf("index.html", "extension-settings.html")) {
            val html = read(page)
            assertTrue(
                "$page 缺少 ST 宿主模板 generic_draggable_template（TopInfoBar 会 clone 它）",
                html.contains("id=\"generic_draggable_template\""),
            )
            assertTrue(
                "$page 的 generic_draggable_template 里必须有 .draggable",
                html.contains("draggable\""),
            )
            assertTrue(
                "$page 的 generic_draggable_template 里必须有 .dragClose",
                html.contains("dragClose\""),
            )
        }
    }

    @Test
    fun `showdown real library ships and loads before the globals stub`() {
        val libF = File(runtimeDir, "vendor/lib/showdown.min.js")
        assertTrue("缺少 vendor/lib/showdown.min.js（真库）", libF.isFile)
        assertTrue("showdown.min.js 太小（${libF.length()}B），不像是真库", libF.length() > 30_000)

        val text = libF.readText()
        assertTrue("showdown.min.js 内容不含版本标识", text.contains("showdown"))

        for (page in listOf("index.html", "extension-settings.html")) {
            val html = read(page)
            val showdownAt = html.indexOf("vendor/lib/showdown.min.js")
            val globalsAt = html.indexOf("vendor/lib/third-party-globals.js")
            assertTrue("$page 没有加载 showdown.min.js", showdownAt >= 0)
            assertTrue("$page 没有加载 third-party-globals.js", globalsAt >= 0)
            assertTrue(
                "$page 中 showdown.min.js 必须在 third-party-globals.js 之前加载，否则桩会先生效（真库被跳过）",
                showdownAt < globalsAt,
            )
        }
    }

    @Test
    fun `globals stub never overwrites a real showdown`() {
        val stub = read("vendor/lib/third-party-globals.js")
        // 桩必须是「真库不存在时才生效」，否则真 showdown 会被降级成轻量实现
        assertTrue(
            "third-party-globals.js 的 showdown 段缺少 undefined 守卫",
            stub.contains("if (typeof window.showdown === \"undefined\")"),
        )
        assertFalse(
            "third-party-globals.js 会无条件覆盖 window.showdown",
            stub.contains("window.showdown = {") &&
                !stub.contains("if (typeof window.showdown === \"undefined\")"),
        )
    }

    @Test
    fun `extensions shim re-exports saveSettingsDebounced for third-party code`() {
        // 超出上游的兼容冗余：上游 extensions.js 不导出它，但生态里有插件这么用。
        // 见 ZerxzLib：import * as ext；ext.saveSettingsDebounced()。
        val shim = read("st-compat/extensions.js")
        assertTrue(
            "extensions.js 需要导出 saveSettingsDebounced 兼容冗余",
            shim.contains("export function saveSettingsDebounced"),
        )
    }

    @Test
    fun `vendor dompurify is a real build not a hand written stub`() {
        val f = File(runtimeDir, "vendor/lib/dompurify.esm.js")
        assertTrue("缺少 vendor/lib/dompurify.esm.js", f.isFile)
        assertTrue("dompurify.esm.js 太小（${f.length()}B），不像是真库", f.length() > 10_000)
        val text = f.readText()
        assertTrue("dompurify.esm.js 内容不含 DOMPurify", text.contains("DOMPurify"))
        assertTrue(
            "dompurify.esm.js 必须是 ESM default 导出",
            text.contains("export { purify as default }") || text.contains("export default"),
        )
    }
}
