package me.rerere.rikkahub.data.export

import org.junit.Test
import java.io.File

/**
 * 诊断测试：把真实预设走完整导入流程，打印所有会显示给用户的警告/错误。
 *
 * 目的：复现用户反馈的「导入小猫之神预设会弹黄色报错框」，定位到具体字段。
 */
class PresetImportDiagnosticsTest {

    private fun load(name: String): String {
        val candidates = listOf(
            File("src/test/resources/presets/$name"),
            File("app/src/test/resources/presets/$name"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun diagnoseAllThreePresets() {
        val names = listOf(
            "Meowssiah-2.0.11.json",
            "TRANS-AM-4A版-v1.1.2.3.json",
            "TRANS-AM-4H版-v1.0.0.1.json",
        )
        for (name in names) {
            println("=".repeat(72))
            println("PRESET: $name")
            val json = load(name)
            val result = ModeInjectionSerializer.importPresetBundleFromString(json, name)
            result.fold(
                onSuccess = { b ->
                    println("  OK  presetGroup      = ${b.presetGroup}")
                    println("      injections       = ${b.injections.size}")
                    println("      presetScripts    = ${b.presetScripts.size}")
                    println("      regexScripts     = ${b.regexScripts.size}")
                    println("      rawPresetJson    = ${if (b.rawPresetJson != null) "${b.rawPresetJson.length} chars" else "null"}")
                    println("      regexFieldState  = ${b.regexFieldState}")
                    println("      scriptsFieldState= ${b.scriptsFieldState}")
                    println("      unsupportedExt   = ${b.unsupportedExtensions}")
                    println("      warnings         = ${b.warnings}")
                    if (b.unsupportedExtensions.isNotEmpty()) {
                        println("  >>> 用户会看到黄色警告: ${b.unsupportedExtensions}")
                    }
                    if (b.warnings.isNotEmpty()) {
                        println("  >>> 用户会看到黄色警告: ${b.warnings}")
                    }
                },
                onFailure = { e ->
                    println("  FAILED: ${e::class.simpleName}: ${e.message}")
                    println("  >>> 用户会看到红色错误")
                },
            )
        }
    }

    /** 单独列出 SPreset 里我们没声明的键。 */
    @Test
    fun diagnoseUnsupportedSpresetKeys() {
        val supported = me.rerere.rikkahub.data.st.preset.SpresetCompat.supportedKeys()
        println("supportedKeys = $supported")
        for (name in listOf("Meowssiah-2.0.11.json", "TRANS-AM-4A版-v1.1.2.3.json")) {
            val json = load(name)
            val ext = me.rerere.rikkahub.data.export.ModeInjectionSerializer
                .detectUnsupportedPresetExtensionsFromJson(json)
            println("$name -> unsupported=$ext")
        }
    }
}
