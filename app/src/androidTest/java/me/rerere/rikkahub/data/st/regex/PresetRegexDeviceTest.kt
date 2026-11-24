package me.rerere.rikkahub.data.st.regex

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 用 Meowssiah-2.0.11 预设里真实的 6 条正则，在真机上逐条跑引擎。
 * 判定三态：PASS / SKIPPED(被 ReDoS 守卫拦截=静默失效) / NO-MATCH / ERROR / BUDGET。
 */
@RunWith(AndroidJUnit4::class)
class PresetRegexDeviceTest {

    private fun g(body: String): String = "/" + body + "/g"

    private fun presetPatterns(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        list.add(Pair("tag-wrap", g("<(?<tag>(?!\\/?(?:think_nya|report_nya)~?>)[^<>]+)>(?=(?:(?!<\\/?(?:think_nya|report_nya)~>)[\\s\\S])*<\\/(?:think_nya|report_nya)~?>)")))
        list.add(Pair("think-collect", g("<(?:think_nya|report_nya)~?>([\\s\\S]*?)<\\/(?:think_nya|report_nya)~?>")))
        list.add(Pair("background", g("<background>([\\s\\S]*?)<\\/background>")))
        list.add(Pair("thought-os", g("<(thought|os)>\\s?([\\s\\S]*?)\\s?<\\/[\\S\\s]*?>")))
        list.add(Pair("think-collect-2", g("<(?:think_nya|report_nya)~?>([\\s\\S]*?)<\\/(?:think_nya|report_nya)~?>")))
        list.add(
            Pair(
                "options-summary-comment",
                "/" + "(?i)" +
                    "((?:<options\\b[^>]*>[\\s\\S]*?<\\/options>|(?<!<details\\b[^>]*>\\s*)<summary\\b[^>]*>[\\s\\S]*?<\\/summary>|<comment\\b[^>]*>[\\s\\S]*?<\\/comment>)" +
                    "(?:\\s*(?:<options\\b[^>]*>[\\s\\S]*?<\\/options>|(?<!<details\\b[^>]*>\\s*)<summary\\b[^>]*>[\\s\\S]*?<\\/summary>|<comment\\b[^>]*>[\\s\\S]*?<\\/comment>))*)",
            ),
        )
        return list
    }

    private fun sampleOutput(padTo: Int): String {
        val core = listOf(
            "<details><summary>状态面板</summary>",
            "<div class=\"stat\">HP 100 / MP 50</div>",
            "</details>",
            "<options>",
            "<div class=\"opt\">下一步</div>",
            "</options>",
            "<think_nya>思考内容</think_nya>",
            "<background>bg.png</background>",
        ).joinToString("\n")
        if (core.length >= padTo) return core
        return core + "\n" + "填充文本".repeat((padTo - core.length) / 4 + 1)
    }

    private fun classify(pattern: String, input: String, name: String): String {
        return try {
            val script = RegexScript(
                id = name,
                scriptName = name,
                findRegex = pattern,
                replaceString = "[R:" + name + "]",
                placement = listOf(RegexPlacement.AI_OUTPUT),
            )
            var degraded: String? = null
            RegexScriptEngine.onCompileDegraded = { msg -> degraded = msg }
            val out = RegexScriptEngine.runRegexScript(script, input)
            RegexScriptEngine.onCompileDegraded = null
            when {
                degraded != null -> "SKIPPED  " + degraded
                out == input -> "NO-MATCH"
                out.contains("[R:") -> "PASS"
                else -> "UNKNOWN out=" + out.take(60)
            }
        } catch (e: Throwable) {
            "ERROR " + e.javaClass.simpleName + ": " + (e.message ?: "").take(80)
        } finally {
            RegexScriptEngine.onCompileDegraded = null
        }
    }

    @Test
    fun everyPresetRegexIsClassified() {
        val lines = mutableListOf<String>()
        for (len in listOf(800, 5713)) {
            val input = sampleOutput(len)
            lines.add("=== inputLen=" + input.length + " ===")
            for (p in presetPatterns()) {
                lines.add("  [" + p.first + "] " + classify(p.second, input, p.first))
            }
        }
                me.rerere.rikkahub.device.SurveySink.write("preset-regex.txt", lines)
        assertTrue("[PRESET-REGEX-OK] " + lines.size + " entries written", true)
    }
}