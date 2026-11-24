package me.rerere.rikkahub.device

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** 取证结果落盘到 app external files，测试本身 PASS（避免污染全量测试结果）。 */
object SurveySink {
    fun write(name: String, lines: List<String>) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, name)
        f.writeText(lines.joinToString("\n"))
    }
}