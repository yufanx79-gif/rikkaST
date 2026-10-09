package me.rerere.rikkahub.data.st.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRegexParseTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun parsesStandardStCardRegexScripts() {
        val json = """{"talkativeness":"0.5","regex_scripts":[
            {"id":"abc","scriptName":"Hide status","findRegex":"/\\[.*?\\]/g","replaceString":"","placement":[2],"disabled":true,"runOnEdit":true},
            {"id":"","scriptName":"trim","findRegex":"X","replaceString":"Y","placement":[1,2],"markdownOnly":true}
        ]}"""
        val result = parseCardRegexScripts(obj(json))
        assertEquals(2, result.size)
        val first = result[0]
        assertEquals("abc", first.id)
        assertEquals("Hide status", first.scriptName)
        assertEquals("/\\[.*?\\]/g", first.findRegex)
        assertEquals(listOf(2), first.placement)
        assertTrue(first.disabled)
        assertTrue(first.runOnEdit)
        val second = result[1]
        assertTrue(second.id.isNotBlank())
        assertTrue(second.markdownOnly)
        assertEquals(listOf(1, 2), second.placement)
    }

    @Test
    fun missingRegexScriptsYieldsEmpty() {
        assertTrue(parseCardRegexScripts(obj("""{"foo":"bar"}""")).isEmpty())
        assertTrue(parseCardRegexScripts(null).isEmpty())
    }

    @Test
    fun malformedYieldsEmpty() {
        assertTrue(parseCardRegexScripts(obj("""{"regex_scripts":"not-an-array"}""")).isEmpty())
    }

    @Test
    fun scriptsWithoutFindRegexAreDropped() {
        val json = """{"regex_scripts":[{"id":"a","findRegex":""},{"id":"b","findRegex":"ok","replaceString":""}]}"""
        val result = parseCardRegexScripts(obj(json))
        assertEquals(1, result.size)
        assertEquals("b", result[0].id)
    }

    @Test
    fun mergeWithNullAssistantReturnsGlobal() {
        val global = listOf(RegexScript(id = "1", scriptName = "g", findRegex = "a", replaceString = "b"))
        assertEquals(global, mergeRegexScripts(global, null))
    }

    // ==================== [v241] 字段类型漂移容错（真实卡兼容） ====================

    @Test
    fun toleratesLegacyScalarTypes() {
        // 旧版酒馆 / 第三方工具：数字 id、标量 placement、字符串 trimStrings、布尔 substituteRegex、snake_case
        val json = """{"regex_scripts":[{
            "id": 7,
            "script_name": "Legacy",
            "find_regex": "A",
            "replace_string": "B",
            "trim_strings": "trim-me",
            "placement": 2,
            "disabled": 1,
            "markdown_only": true,
            "substitute_regex": true,
            "min_depth": 1,
            "max_depth": 5
        }]}"""
        val result = parseCardRegexScripts(obj(json))
        assertEquals(1, result.size)
        val script = result.single()
        assertEquals("7", script.id)
        assertEquals("Legacy", script.scriptName)
        assertEquals("A", script.findRegex)
        assertEquals("B", script.replaceString)
        assertEquals(listOf("trim-me"), script.trimStrings)
        assertEquals(listOf(2), script.placement)
        assertTrue(script.disabled)
        assertTrue(script.markdownOnly)
        assertEquals(1, script.substituteRegex)
        assertEquals(1, script.minDepth)
        assertEquals(5, script.maxDepth)
    }

    @Test
    fun toleratesNumericPlacementArrayAndEnabledFlag() {
        val json = """{"regex_scripts":[
            {"id":"a","findRegex":"X","placement":["1","2"],"enabled":false},
            {"id":"b","findRegex":"Y","placement":[2]}
        ]}"""
        val result = parseCardRegexScripts(obj(json))
        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result[0].placement)
        assertTrue(result[0].disabled)
        assertEquals(listOf(2), result[1].placement)
        assertTrue(!result[1].disabled)
    }

    @Test
    fun malformedScriptOnlyDropsItself() {
        val json = """{"regex_scripts":[{"id":"ok","findRegex":"A"}, "not-an-object", {"id":"ok2","findRegex":"B"}]}"""
        val result = parseCardRegexScripts(obj(json))
        assertEquals(2, result.size)
    }
}