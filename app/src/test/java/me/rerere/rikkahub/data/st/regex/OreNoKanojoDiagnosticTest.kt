package me.rerere.rikkahub.data.st.regex

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.ui.components.message.detectUnmatchedCardWrapperBrackets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色卡 `〔` / `〕` 包装符未匹配诊断与三源正则去重回归测试。
 */
class OreNoKanojoDiagnosticTest {

    @Test
    fun detectsUnmatchedWrapperWhenCardDescriptionUsesLenticularBracketsAndNoRegexMatchesThem() {
        val assistant = Assistant(
            name = "早川",
            tavernData = TavernCharacterData(
                name = "早川",
                description = "输出模板：〔正文段落〕",
            ),
        )
        val scripts = listOf(
            RegexScript(id = "1", scriptName = "status", findRegex = "</base>[\\s\\S]*?</base>", replaceString = "<div></div>")
        )
        assertTrue(detectUnmatchedCardWrapperBrackets(assistant, scripts))

        val scriptsWithCleanup = scripts + RegexScript(
            id = "2",
            scriptName = "strip-brackets",
            findRegex = "[〔〕]",
            replaceString = "",
        )
        assertFalse(detectUnmatchedCardWrapperBrackets(assistant, scriptsWithCleanup))
    }

    @Test
    fun mergeRegexScriptsPreservesDistinctSourcesWithCollidingIds() {
        val global = listOf(RegexScript(id = "1", scriptName = "global-rule", findRegex = "A", replaceString = "B"))
        val preset = listOf(RegexScript(id = "1", scriptName = "preset-rule", findRegex = "C", replaceString = "D"))
        val card = listOf(RegexScript(id = "1", scriptName = "card-rule", findRegex = "E", replaceString = "F"))

        val merged = mergeRegexScripts(global = global, card = card, preset = preset)
        assertTrue(merged.size == 3)
        assertTrue(merged.any { it.scriptName == "global-rule" })
        assertTrue(merged.any { it.scriptName == "preset-rule" })
        assertTrue(merged.any { it.scriptName == "card-rule" })
    }
}
