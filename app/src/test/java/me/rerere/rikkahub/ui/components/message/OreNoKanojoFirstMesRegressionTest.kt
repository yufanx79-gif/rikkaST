package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.RegexScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 针对 `36cf5ad94fc2908e.png`（俺の彼女）开场白 `</base>`、`</mes>`、`</sh>` 模板替换、
 * `<div class="post-card">` HTML 分段与 `〔` / `〕` 包装符清理的端到端回归测试。
 */
class OreNoKanojoFirstMesRegressionTest {

    @Test
    fun firstMesBaseMesAndShTemplatesRenderIntoHtmlPanelsWithoutLeakingPostCardToMarkdown() {
        val scripts = listOf(
            RegexScript(
                id = "r-base",
                scriptName = "状态栏替换",
                findRegex = """</base>\s*日期[：:]\s*(.*?)\n\s*时间[：:]\s*(.*?)\n([\s\S]*?)</base>""",
                replaceString = """<style>.status-box{color:#fff;background:#222}</style><div class="status-box"><span>$1 $2</span><div>$3</div></div>""",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
            RegexScript(
                id = "r-mes",
                scriptName = "手机消息替换",
                findRegex = """</mes>\s*([\s\S]*?)</mes>""",
                replaceString = """<style>.mes-box{border:1px solid #444}</style><div class="mes-box">$1</div>""",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
            RegexScript(
                id = "r-sh",
                scriptName = "动态卡片替换",
                findRegex = """</sh>\s*标题[：:]\s*(.*?)\n\s*内容[：:]\s*([\s\S]*?)</sh>""",
                replaceString = """<style>.post-card{padding:12px;border-radius:10px}</style><div class="post-card"><div class="post-title">$1</div><div class="post-content">$2</div></div>""",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
        )

        val rawFirstMes = """
            </base>
            日期：10月20日
            时间：19:30
            地点：便利店
            </base>
            </mes>
            早川：今晚下雨了，记得带伞。
            </mes>
            </sh>
            标题：雨夜随记
            内容：窗外的霓虹灯倒映在水洼里。
            </sh>

            她推开玻璃门，把湿漉漉的雨伞收拢在伞桶里，朝{{user}}微微一笑。
        """.trimIndent()

        val assistant = Assistant(
            name = "早川",
            tavernData = TavernCharacterData(
                name = "早川",
                description = "回复格式示例：〔正文内容〕",
            ),
        )

        val rendered = computePureKotlinRegexedText(
            rawInputText = rawFirstMes,
            scripts = scripts,
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            placement = RegexPlacement.AI_OUTPUT,
            userName = "阿明",
        )

        assertTrue(rendered.contains("朝阿明微微一笑"))
        val pieces = splitMixedMessagePieces(rendered)
        val htmlPanels = pieces.filterIsInstance<MixedContentPiece.HtmlPanel>()
        val markdownBodies = pieces.filterIsInstance<MixedContentPiece.MarkdownBody>()

        assertEquals(3, htmlPanels.size)
        assertTrue(htmlPanels[2].html.contains(".post-card"))
        assertTrue(htmlPanels[2].html.contains("class=\"post-card\""))
        assertEquals(1, markdownBodies.size)
        assertFalse(markdownBodies[0].text.contains("<div class=\"post-card\">"))
    }

    @Test
    fun unmatchedWrapperBracketsAreCleanedVisuallyWithoutBreakingInlineBrackets() {
        val assistant = Assistant(
            name = "早川",
            tavernData = TavernCharacterData(
                name = "早川",
                description = "请用 〔 与 〕 包裹你的回复。",
            ),
        )
        val rawReply = "〔她轻轻叹了口气，递过来一罐热咖啡。〕"
        val rendered = computePureKotlinRegexedText(
            rawInputText = rawReply,
            scripts = emptyList(),
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            placement = RegexPlacement.AI_OUTPUT,
        )
        assertEquals("她轻轻叹了口气，递过来一罐热咖啡。", rendered)
    }

    @Test
    fun unhandledDrawTagPromptIsConvertedIntoCompactDrawCard() {
        val rawReply = """
            她站在便利店明亮的灯下。
            [出图]
            画幅 | 竖
            场景 | sfw, 1girl, 1boy, indoor, Convenience_Store, night
            [/出图]
            随后转身看向货架。
        """.trimIndent()
        val rendered = computePureKotlinRegexedText(
            rawInputText = rawReply,
            scripts = emptyList(),
            assistant = null,
            scope = AssistantAffectScope.ASSISTANT,
            placement = RegexPlacement.AI_OUTPUT,
        )
        assertFalse(rendered.contains("[出图]"))
        assertFalse(rendered.contains("[/出图]"))
        assertTrue(rendered.contains("rikka-draw-card"))
    }

    @Test
    fun realOreNoKanojoCcv3FixtureWithCrlfRendersPhoneAndPostCardPanelsWithoutCodeBlockDegeneration() {
        val rawCrlfFirstMes = "</base>\r\n" +
            "日期：*{{isodate}}*\r\n" +
            "星期：*{{weekday}}*\r\n" +
            "时间：*{{isotime}} *\r\n" +
            "电量：100%\r\n" +
            "信号：1\r\n" +
            "ID：爱吃羽衣甘蓝\r\n" +
            "状态：🔘\r\n\r\n" +
            "</mes>\r\n" +
            "ID：爱吃羽衣甘蓝\r\n" +
            "消息正文：こんにちは！初めまして、あなたに会えて嬉しいです\r\n" +
            "中文翻译：你好呀！初次见面，很高兴认识你\r\n" +
            "心情：🙂\r\n" +
            "时间：*{{isotime}} *\r\n" +
            "</mes>\r\n" +
            "</base>\r\n\r\n" +
            "</sh>\r\n" +
            "ID：爱吃羽衣甘蓝\r\n" +
            "动态正文：無\r\n" +
            "中文翻译：无\r\n" +
            "时间：无*{{isotime}} *\r\n" +
            "点赞数量：无\r\n" +
            "相关评论：无\r\n" +
            "</sh>\r\n\r\n" +
            "*手机振动*\r\n" +
            "我收到了消息，是社交软件的好友通过。"

        val scripts = listOf(
            RegexScript(
                id = "base-real",
                scriptName = "基础1",
                findRegex = "</base>\\s*日期[：:]\\s*(.*?)\\n\\s*星期[：:]\\s*(.*?)\\n\\s*时间[：:]\\s*(.*?)\\n\\s*电量[：:]\\s*(.*?)\\n\\s*信号[：:]\\s*(.*?)\\n\\s*ID[：:]\\s*(.*?)\\n\\s*状态[：:]\\s*(.*?)\\n([\\s\\S]*?)</base>",
                replaceString = "<style>.phone-pro{display:block}</style>\n<div class=\"phone-pro\">\n    <div class=\"chat-area\">\n        <div class=\"raw-messages\">$8</div>\n    </div>\n</div>",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
            RegexScript(
                id = "sh-real",
                scriptName = "动态分享1",
                findRegex = "</sh>\\s*ID[：:]\\s*(.*?)\\n\\s*动态正文[：:]\\s*(.*?)\\n\\s*中文翻译[：:]\\s*(.*?)\\n\\s*时间[：:]\\s*(.*?)\\n\\s*点赞数量[：:]\\s*(.*?)\\n\\s*相关评论[：:]\\s*([\\s\\S]*?)\\n\\s*</sh>",
                replaceString = "<style>.post-card{padding:12px}</style>\n<div class=\"post-card\">\n    <!-- 头部 -->\n    <div class=\"post-header\">$1</div>\n    <div class=\"post-content\">$2</div>\n</div>",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
            RegexScript(
                id = "mes-real",
                scriptName = "messages1",
                findRegex = "</mes>\\s*ID[：:]\\s*(.*?)\\n\\s*消息正文[：:]\\s*([\\s\\S]*?)\\n\\s*(?=中文翻译[：:])中文翻译[：:]\\s*([\\s\\S]*?)\\n\\s*(?=心情[：:])心情[：:]\\s*(.*?)\\n\\s*(?=时间[：:])时间[：:]\\s*(.*?)\\n\\s*</mes>",
                replaceString = "<style>.message-bubble{padding:10px}</style><div class=\"message-bubble\">$1: $2 ($3)</div>",
                placement = listOf(RegexPlacement.USER_INPUT, RegexPlacement.AI_OUTPUT),
                markdownOnly = true,
            ),
        )

        val rendered = computePureKotlinRegexedText(
            rawInputText = rawCrlfFirstMes,
            scripts = scripts,
            assistant = null,
            scope = AssistantAffectScope.ASSISTANT,
            placement = RegexPlacement.AI_OUTPUT,
        )

        val pieces = splitMixedMessagePieces(rendered)
        val htmlPanels = pieces.filterIsInstance<MixedContentPiece.HtmlPanel>()
        val mdBodies = pieces.filterIsInstance<MixedContentPiece.MarkdownBody>()

        assertEquals(2, htmlPanels.size)
        assertTrue(htmlPanels[0].html.contains("phone-pro"))
        assertTrue(htmlPanels[0].html.contains("message-bubble"))
        assertTrue(htmlPanels[1].html.contains("post-card"))
        assertEquals(1, mdBodies.size)
        assertFalse(mdBodies[0].text.contains("post-card"))
        assertFalse(mdBodies[0].text.contains("post-header"))
    }

    @Test
    fun multilineWrapperBracketsAroundHtmlBlocksAreCleanedWhileInlineProseBracketsRemainIntact() {
        val assistant = Assistant(
            name = "早川",
            tavernData = TavernCharacterData(
                name = "早川",
                description = "回复模板：
〔
</base>...
〕
或
〖</txt>...
</txt>〗",
            ),
        )
        val rawReply = """
            〔
            <style>.post-card{padding:8px}</style>
            <div class="post-card"><div class="post-title">朋友圈动态</div></div>
            〕
            她翻开〔世界史〕笔记本，指着「〖重点〗北海道开拓史」那一行微笑。
        """.trimIndent()

        val rendered = computePureKotlinRegexedText(
            rawInputText = rawReply,
            scripts = emptyList(),
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            placement = RegexPlacement.AI_OUTPUT,
        )

        assertFalse(rendered.lines().any { it.trim() == "〔" || it.trim() == "〕" })
        assertTrue(rendered.contains("她翻开〔世界史〕笔记本"))
        assertTrue(rendered.contains("「〖重点〗北海道开拓史」"))
    }
}
