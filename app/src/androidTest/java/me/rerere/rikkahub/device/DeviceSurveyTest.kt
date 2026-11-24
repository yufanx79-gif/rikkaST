package me.rerere.rikkahub.device

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import me.rerere.rikkahub.ui.components.message.shouldRenderAsHtmlPanel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 综合真机取证：一次测完「之后要修/要做」所需的事实。
 */
@RunWith(AndroidJUnit4::class)
class DeviceSurveyTest {

    private val lines = mutableListOf<String>()
    private fun rec(k: String, v: Any?) { lines.add(k + " = " + v) }

    @Test
    fun surveyEverything() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. 面板 HTML 识别
        val htmlSamples = linkedMapOf(
            "details-summary" to "<details><summary>状态</summary><div class=\"s\">HP 100</div></details>",
            "options" to "<options><div class=\"o\">next</div></options>",
            "script" to "<div id=\"p\"></div><script>window.x=1;</script>",
            "plain-markdown" to "**hi**\n\nnormal reply text here.",
            "think-tag" to "<think_nya>think</think_nya>body",
            "classed-div" to "<div class=\"post-card\"><span>hi</span></div>",
            "bg-only" to "<background>bg.png</background>",
        )
        for ((name, html) in htmlSamples) {
            val d = runCatching { shouldRenderAsHtmlPanel(html) }.getOrElse { "ERR" }
            rec("detect/" + name, d)
        }

        // 2. 正则引擎常量
        rec("regex/MIN_CATASTROPHIC_INPUT_LENGTH", RegexConstants.min)
        rec("regex/BOUNDED_LOOKBEHIND_MAX", RegexConstants.lookbehind)

        // 3. WebView 环境
        rec("webview/pkg", android.webkit.WebView.getCurrentWebViewPackage()?.packageName)
        rec("webview/ver", android.webkit.WebView.getCurrentWebViewPackage()?.versionName)

        // 4. 运行时资产
        for (a in listOf("st-runtime/panel.html", "st-runtime/runtime.js", "st-runtime/index.html")) {
            val size = runCatching { ctx.assets.open(a).use { it.available() } }.getOrElse { -1 }
            rec("asset/" + a, size)
        }

        // 5. 内存（方案 C 预算依据）
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        rec("mem/totalMB", mi.totalMem / 1024 / 1024)
        rec("mem/availMB", mi.availMem / 1024 / 1024)
        rec("mem/lowMemory", mi.lowMemory)
        rec("mem/isLowRam", am.isLowRamDevice)
        rec("mem/largeHeapMB", am.largeMemoryClass)

                SurveySink.write("survey.txt", lines)
        assertTrue("[SURVEY-OK] " + lines.size + " entries written", true)
    }
}

object RegexConstants {
    val min: Any = runCatching {
        val f = RegexScriptEngine::class.java.getDeclaredField("MIN_CATASTROPHIC_INPUT_LENGTH")
        f.isAccessible = true
        f.get(null)
    }.getOrElse { "n/a" }
    val lookbehind: Any = runCatching {
        val f = RegexScriptEngine::class.java.getDeclaredField("BOUNDED_LOOKBEHIND_MAX")
        f.isAccessible = true
        f.get(null)
    }.getOrElse { "n/a" }
}