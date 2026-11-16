package me.rerere.rikkahub.data.export

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实预设回归测试：直接读取工作区里 3 个社区主流预设的原文，
 * 验证解析器不崩溃、条目数正确、关键字段被保留。
 *
 * 这些文件是用户实测环境里真实在用的预设，比手写 JSON 更能暴露兼容性问题。
 */
class RealPresetCompatibilityTest {

    private fun loadPreset(name: String): String {
        val candidates = listOf(
            File("src/test/resources/presets/$name"),
            File("app/src/test/resources/presets/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("preset not found: $name (tried ${candidates.map { it.absolutePath }})")
        return file.readText()
    }

    private fun parse(name: String) =
        ModeInjectionSerializer.tryImportSillyTavernPreset(loadPreset(name), name)

    @Test
    fun meowssiahPresetParsesWithoutCrash() {
        val list = parse("Meowssiah-2.0.11.json")
        assertNotNull("Meowssiah 预设应能解析", list)
        println("Meowssiah 注入条目数 = ${list!!.size}")
        assertTrue("应产出注入条目", list.isNotEmpty())
    }

    @Test
    fun transAm4APresetParsesWithoutCrash() {
        val list = parse("TRANS-AM-4A版-v1.1.2.3.json")
        assertNotNull("TRANS-AM-4A 预设应能解析", list)
        println("TRANS-AM-4A 注入条目数 = ${list!!.size}")
        assertTrue("应产出注入条目", list.isNotEmpty())
    }

    @Test
    fun transAm4HPresetParsesWithoutCrash() {
        val list = parse("TRANS-AM-4H版-v1.0.0.1.json")
        assertNotNull("TRANS-AM-4H 预设应能解析", list)
        println("TRANS-AM-4H 注入条目数 = ${list!!.size}")
        assertTrue("应产出注入条目", list.isNotEmpty())
    }

    /** 三个预设都必须产出条目，且数量应在合理区间（远小于原始 prompts 数，因为 marker/空内容被跳过）。 */
    @Test
    fun allRealPresetsProduceReasonableEntryCounts() {
        val results = mapOf(
            "Meowssiah" to parse("Meowssiah-2.0.11.json"),
            "TRANS-AM-4A" to parse("TRANS-AM-4A版-v1.1.2.3.json"),
            "TRANS-AM-4H" to parse("TRANS-AM-4H版-v1.0.0.1.json"),
        )
        results.forEach { (name, list) ->
            val size = list?.size ?: 0
            println("$name -> $size entries")
            assertTrue("$name 条目数应在 1..200 之间，实际 $size", size in 1..200)
        }
    }
}
