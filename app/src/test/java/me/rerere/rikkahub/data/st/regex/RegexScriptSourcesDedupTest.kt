package me.rerere.rikkahub.data.st.regex

import me.rerere.rikkahub.data.export.ModeInjectionSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v2] 预设正则「快照 + 预设副本」重复执行的回归。
 *
 * 真机背景：同一条预设正则先在 5963 chars 输入上跑、又在 16467 chars（第一遍折叠后的产物）上重跑，
 * 导致前端综合面板把输出膨胀到 125 KB、raw-data 模板出现 3 份。
 * 根因是 mergeRegexScripts 同时收下了 Settings.regexScripts 里的预设快照与 tavernPresets 里的同一批脚本。
 */
class RegexScriptSourcesDedupTest {

    private fun s(id: String, find: String = "/$id/g", group: String? = null) = RegexScript(
        id = id, scriptName = id, findRegex = find, replaceString = "",
        trimStrings = emptyList(), placement = listOf(RegexPlacement.AI_OUTPUT),
        disabled = false, markdownOnly = true, promptOnly = false, runOnEdit = true,
        substituteRegex = 0, minDepth = null, maxDepth = null,
    ).copy(sourcePresetGroup = group)

    @Test
    fun snapshotInGlobalSuppressesPresetCopies() {
        val snapshot = listOf(s("a", group = "G"), s("b", group = "G"))
        val fromPreset = listOf(s("a", group = "G"), s("b", group = "G"))
        val merged = mergeRegexScripts(
            global = snapshot, card = null, preset = fromPreset, presetByGroup = mapOf("G" to fromPreset),
        )
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun presetWithoutSnapshotStillApplies() {
        val merged = mergeRegexScripts(
            global = listOf(s("u")), card = null,
            preset = listOf(s("a", group = "G")), presetByGroup = mapOf("G" to listOf(s("a", group = "G"))),
        )
        assertEquals(listOf("u", "a"), merged.map { it.id })
    }

    @Test
    fun userScriptsAreNeverDropped() {
        val merged = mergeRegexScripts(
            global = listOf(s("u"), s("a", group = "G")), card = null,
            preset = listOf(s("a", group = "G")), presetByGroup = mapOf("G" to listOf(s("a", group = "G"))),
        )
        assertEquals(listOf("u", "a"), merged.map { it.id })
    }

    @Test
    fun onlyGroupsWithSnapshotAreSuppressed() {
        val g1 = listOf(s("a", group = "G1"))
        val g2 = listOf(s("b", group = "G2"))
        val merged = mergeRegexScripts(
            global = g1, card = null, preset = g1 + g2, presetByGroup = mapOf("G1" to g1, "G2" to g2),
        )
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun legacyFlatPresetListHonorsSnapshotGroups() {
        val merged = mergeRegexScripts(
            global = listOf(s("a", group = "G")), card = null,
            preset = listOf(s("a", group = "G"), s("h", group = "H")),
        )
        assertEquals(listOf("a", "h"), merged.map { it.id })
    }

    @Test
    fun orderIsGlobalThenPresetThenCard() {
        val merged = mergeRegexScripts(
            global = listOf(s("u")), card = listOf(s("c")),
            preset = listOf(s("p", group = "G")), presetByGroup = mapOf("G" to listOf(s("p", group = "G"))),
        )
        assertEquals(listOf("u", "p", "c"), merged.map { it.id })
    }

    // ---- inspectPresetRegexScripts：两个来源数组 ----

    private val ONE = """{"findRegex":"/a/g","replaceString":"x","placement":[2],"scriptName":"A"}"""
    private val TWO = """{"findRegex":"/b/g","replaceString":"y","placement":[2],"scriptName":"B"}"""

    @Test
    fun sameScriptsWithoutIdsInBothSourcesAreNotDoubled() {
        val json = """{"extensions":{"regex_scripts":[$ONE,$TWO],"SPreset":{"RegexBinding":{"regexes":[$ONE,$TWO]}}}}"""
        val (_, scripts) = ModeInjectionSerializer.inspectPresetRegexScripts(json, "G")
        assertEquals(2, scripts.size)
    }

    @Test
    fun sameIdsInBothSourcesAreNotDoubled() {
        val a = """{"id":"id-a","findRegex":"/a/g","replaceString":"x","placement":[2]}"""
        val json = """{"extensions":{"regex_scripts":[$a],"SPreset":{"RegexBinding":{"regexes":[$a]}}}}"""
        val (_, scripts) = ModeInjectionSerializer.inspectPresetRegexScripts(json, "G")
        assertEquals(1, scripts.size)
    }

    @Test
    fun differentScriptsAreKept() {
        val json = """{"extensions":{"regex_scripts":[$ONE],"SPreset":{"RegexBinding":{"regexes":[$TWO]}}}}"""
        val (_, scripts) = ModeInjectionSerializer.inspectPresetRegexScripts(json, "G")
        assertEquals(2, scripts.size)
    }
}
