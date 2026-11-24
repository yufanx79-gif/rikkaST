package me.rerere.rikkahub.ui.components.message

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 方案 C 成败前提实验：WebView 跨 parent 迁移后，JS 运行时状态是否存活。
 * 四条断言 = 四个硬约束：DOM 存活 / window 状态存活(C2) / 心跳继续(C2,C3) / 脚本不重跑(C6)。
 *
 * 所有观测值都写进失败消息，保证无论成败都能从 report 里读到真实数字。
 */
@RunWith(AndroidJUnit4::class)
class WebViewReparentSurvivalTest {

    private val instr get() = InstrumentationRegistry.getInstrumentation()

    private fun onMain(block: () -> Unit) = instr.runOnMainSync(block)

    private fun evalJs(wv: WebView, js: String): String? {
        val result = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        onMain { wv.evaluateJavascript(js) { v -> result.set(v); latch.countDown() } }
        return if (latch.await(10, TimeUnit.SECONDS)) result.get() else null
    }

    private fun asInt(raw: String?): Int = raw.orEmpty().trim().trim('"').toIntOrNull() ?: -1

    private fun panelHtml(): String = listOf(
        "<!DOCTYPE html><html><body><div id=\"p\">panel-content</div>",
        "<script>",
        "window.__exec=(window.__exec||0)+1;",
        "window.__state=\"alive\";",
        "window.__ticks=0;",
        "setInterval(function(){window.__ticks++;},50);",
        "</script></body></html>",
    ).joinToString("\n")

    @Test
    fun webViewSurvivesReparentAndKeepsJsState() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wvRef = AtomicReference<WebView?>(null)
        val parentBRef = AtomicReference<android.widget.FrameLayout?>(null)

        onMain {
            val a = android.widget.FrameLayout(ctx)
            val b = android.widget.FrameLayout(ctx)
            val wv = WebView(ctx)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            a.addView(wv)
            parentBRef.set(b)
            wvRef.set(wv)
            wv.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/", panelHtml(), "text/html", "UTF-8", null,
            )
        }

        // 等脚本执行
        val deadline = System.currentTimeMillis() + 25_000
        var exec = -1
        var waited = 0L
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            exec = asInt(runCatching { evalJs(wvRef.get()!!, "String(window.__exec||0)") }.getOrNull())
            if (exec >= 1) break
            Thread.sleep(150)
        }
        waited = System.currentTimeMillis() - t0
        val reachedReady = exec >= 1

        val beforeState = evalJs(wvRef.get()!!, "String(window.__state||\"\")")
        val beforeExec = asInt(evalJs(wvRef.get()!!, "String(window.__exec||0)"))
        val beforeTicks = asInt(evalJs(wvRef.get()!!, "String(window.__ticks||0)"))

        onMain {
            val wv = wvRef.get()!!
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            parentBRef.get()!!.addView(wv)
        }

        Thread.sleep(1000)

        val afterState = evalJs(wvRef.get()!!, "String(window.__state||\"\")")
        val afterExec = asInt(evalJs(wvRef.get()!!, "String(window.__exec||0)"))
        val afterTicks = asInt(evalJs(wvRef.get()!!, "String(window.__ticks||0)"))
        val afterDom = evalJs(wvRef.get()!!, "String(document.body.innerText||\"\")")
        val attached = runCatching { wvRef.get()!!.parent != null }.getOrDefault(false)

        val report = "ready=" + reachedReady + " waitMs=" + waited +
            " | beforeState=" + beforeState + " beforeExec=" + beforeExec + " beforeTicks=" + beforeTicks +
            " | afterState=" + afterState + " afterExec=" + afterExec + " afterTicks=" + afterTicks +
            " afterDom=" + afterDom + " attached=" + attached

        runCatching {
            onMain {
                wvRef.get()?.let {
                    (it.parent as? android.view.ViewGroup)?.removeView(it)
                    it.destroy()
                }
            }
        }

        // 先断言「实验真的跑起来了」，否则后面的通过毫无意义
        assertTrue("[EXP-NOT-READY] 脚本未在超时内执行, " + report, reachedReady)
        assertTrue("[EXP-C2-DOM] DOM 内容丢失, " + report, afterDom.orEmpty().contains("panel-content"))
        assertTrue("[EXP-C2-STATE] window 状态丢失, " + report, afterState.orEmpty().contains("alive"))
        assertEquals("[EXP-C6-EXEC] 脚本被重跑, " + report, 1, afterExec)
        assertTrue("[EXP-C2-TICKS] 心跳停止, " + report, afterTicks > beforeTicks)
    }
}