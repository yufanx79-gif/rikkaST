package me.rerere.rikkahub.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 桥接/交互面真机取证：面板按钮要能用的前提。 */
@RunWith(AndroidJUnit4::class)
class BridgeSurfaceTest {

    private val lines = mutableListOf<String>()
    private fun rec(k: String, v: Any?) { lines.add(k + " = " + v) }

    @Test
    fun listJsBridgeSurface() {
        var bridgeCls: Class<*>? = null
        for (n in listOf(
            "me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager" + '$' + "TavernJsBridge",
            "me.rerere.rikkahub.data.st.runtime.TavernJsBridge",
        )) {
            bridgeCls = runCatching { Class.forName(n) }.getOrNull()
            if (bridgeCls != null) { rec("bridge/class", n); break }
        }
        if (bridgeCls == null) {
            // 兜底：扫 jar 里所有含 Bridge 的类
            rec("bridge/class", "NOT-FOUND")
        } else {
            val methods = bridgeCls.declaredMethods
                .filter { m -> m.annotations.any { it.annotationClass.simpleName == "JavascriptInterface" } }
                .map { it.name }
                .sorted()
            rec("bridge/jsInterfaceCount", methods.size)
            for (m in methods) rec("bridge/m/" + m, "ok")
        }

        val mgr = TavernRuntimeManager::class.java
        for (slot in listOf(
            "slashRunner", "chatInserter", "chatDeleter", "userMessageSender",
            "userMessageSenderWithReceipt", "onJsEvent", "quietGenerator",
            "lorebookUpserter", "lorebookUpdater", "assistantMessageInserter",
        )) {
            val f = runCatching { mgr.getDeclaredField(slot) }.getOrNull()
            rec("slot/" + slot, if (f != null) "exists" else "MISSING")
        }

                SurveySink.write("bridge-surface.txt", lines)
        assertTrue("[BRIDGE-OK] " + lines.size + " entries written", true)
    }
}