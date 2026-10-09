package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v222 R5/R6] 消息样式的**纯函数**回归。
 *
 * 设计原则（工程纪律）：颜色 / 不透明度 / 圆角 / 拆分的可测部分一律抽成纯函数 + 单测，
 * 不把验证押在截图或真机手测上。
 */
class MessageStyleSettingTest {

    // ---------- withAlpha ----------

    @Test
    fun `withAlpha replaces only the alpha channel`() {
        val rgb = 0x112233
        assertEquals(0x00112233, withAlpha(rgb, 0f))
        assertEquals(0x80112233.toInt(), withAlpha(rgb, 0.5f))
        assertEquals(0xFF112233.toInt(), withAlpha(rgb, 1f))
        // 带 alpha 的输入色：alpha 被覆盖，RGB 保留
        assertEquals(0xFF112233.toInt(), withAlpha(0x7F112233, 1f))
    }

    @Test
    fun `withAlpha clamps out of range`() {
        assertEquals(0x00112233, withAlpha(0x112233, -1f))
        assertEquals(0xFF112233.toInt(), withAlpha(0x112233, 2f))
    }

    // ---------- resolveBubbleStyle ----------

    @Test
    fun `frosted uses frostedOpacity and solid uses solidOpacity`() {
        val setting = MessageStyleSetting(
            light = BubbleStyleTheme(frostedOpacity = 0.25f, solidOpacity = 0.75f),
        )
        val frosted = resolveBubbleStyle(setting, MessageBubbleStyle.FROSTED, false, 0xFFFFFF, 0x000000)
        val solid = resolveBubbleStyle(setting, MessageBubbleStyle.SOLID, false, 0xFFFFFF, 0x000000)
        assertEquals(0x40FFFFFF, frosted.backgroundArgb)
        assertEquals(0xBFFFFFFF.toInt(), solid.backgroundArgb)
        // 边框不透明度同源
        assertNotEquals(frosted.backgroundArgb, solid.backgroundArgb)
    }

    @Test
    fun `override colors win over fallback`() {
        val setting = MessageStyleSetting(
            light = BubbleStyleTheme(
                backgroundArgb = 0x0000FF,
                borderArgb = 0x00FF00,
                textArgb = 0xFF0000,
            ),
        )
        val r = resolveBubbleStyle(setting, MessageBubbleStyle.SOLID, false, 0xFFFFFF, 0x888888)
        assertEquals(0xFF0000FF.toInt(), r.backgroundArgb)   // backgroundArgb=0x0000FF + solidOpacity 默认 1.0
        assertEquals(0xFF0000, r.textArgb)
        // borderOpacity 默认 0.14 → alpha ≈ 0x24
        assertEquals(0x2400FF00, r.borderArgb)
    }

    @Test
    fun `fallback colors used when override is null`() {
        val r = resolveBubbleStyle(
            MessageStyleSetting(), MessageBubbleStyle.FROSTED, false,
            fallbackBackgroundArgb = 0x123456, fallbackBorderArgb = 0x654321,
        )
        // frostedOpacity 默认 0.66 → alpha = round(0.66*255) = 168 = 0xA8
        assertEquals(0xA8123456.toInt(), r.backgroundArgb)
        assertNull(r.textArgb)
    }

    @Test
    fun `geometry passes through untouched`() {
        val setting = MessageStyleSetting(
            dark = BubbleStyleTheme(borderWidth = 2.5f, cornerRadius = 30f, solidOpacity = 1f),
        )
        val r = resolveBubbleStyle(setting, MessageBubbleStyle.SOLID, true, 0xFFFFFF, 0x000000)
        assertEquals(2.5f, r.borderWidthDp, 0.0001f)
        assertEquals(30f, r.cornerRadiusDp, 0.0001f)
    }

    // ---------- BlurStrength ----------

    @Test
    fun `blur strength has five levels and index zero means no blur`() {
        assertEquals(5, BlurStrength.SIGMAS_DP.size)
        assertEquals(0f, BlurStrength.sigmaDp(0), 0.0001f)
        assertTrue(BlurStrength.SIGMAS_DP.zipWithNext().all { (a, b) -> b > a })
        assertEquals(2, BlurStrength.DEFAULT_INDEX)
    }

    @Test
    fun `blur strength clamps out of range index`() {
        assertEquals(0, BlurStrength.clampIndex(-3))
        assertEquals(4, BlurStrength.clampIndex(99))
        assertEquals(BlurStrength.SIGMAS_DP.last(), BlurStrength.sigmaDp(99), 0.0001f)
    }

    @Test
    fun `blur strength labels are stable`() {
        assertEquals(listOf("关", "弱", "中", "强", "极强"), (0..4).map { BlurStrength.labelIndex(it) })
        assertEquals("极强", BlurStrength.labelIndex(42))
    }

    // ---------- themeFor / backgroundOpacityFor ----------

    @Test
    fun `themeFor picks the light and dark slot`() {
        val light = BubbleStyleTheme(cornerRadius = 4f)
        val dark = BubbleStyleTheme(cornerRadius = 8f)
        val s = MessageStyleSetting(light = light, dark = dark)
        assertEquals(light, s.themeFor(false))
        assertEquals(dark, s.themeFor(true))
    }

    @Test
    fun `backgroundOpacityFor follows the active style`() {
        val s = MessageStyleSetting(
            light = BubbleStyleTheme(frostedOpacity = 0.4f, solidOpacity = 0.9f),
        )
        assertEquals(0.4f, s.backgroundOpacityFor(MessageBubbleStyle.FROSTED, false), 0.0001f)
        assertEquals(0.9f, s.backgroundOpacityFor(MessageBubbleStyle.SOLID, false), 0.0001f)
        assertEquals(0.9f, s.backgroundOpacityFor(MessageBubbleStyle.DEFAULT, false), 0.0001f)
    }

    // ---------- splitAssistantSegments（R6-②） ----------

    @Test
    fun `single paragraph is not split`() {
        assertEquals(listOf("hello"), splitAssistantSegments("hello"))
        assertEquals(listOf("a\nb"), splitAssistantSegments("a\nb"))
    }

    @Test
    fun `blank line splits into separate bubbles`() {
        assertEquals(listOf("one", "two"), splitAssistantSegments("one\n\ntwo"))
        assertEquals(listOf("one", "two", "three"), splitAssistantSegments("one\n\ntwo\n\nthree"))
    }

    @Test
    fun `consecutive blank lines still produce exactly one boundary`() {
        assertEquals(listOf("one", "two"), splitAssistantSegments("one\n\n\n\ntwo"))
    }

    @Test
    fun `blank lines containing only spaces count as a boundary`() {
        assertEquals(listOf("one", "two"), splitAssistantSegments("one\n   \n\t\ntwo"))
    }

    @Test
    fun `leading and trailing blank lines are dropped`() {
        assertEquals(listOf("one"), splitAssistantSegments("\n\none\n\n"))
    }

    @Test
    fun `empty text stays a single empty segment`() {
        assertEquals(listOf(""), splitAssistantSegments(""))
    }

    @Test
    fun `carriage returns are treated as normal characters`() {
        // 只按 '\n' 判空行；'\r' 会让该行非空（保守：宁可少拆，不可把 CRLF 正文拆坏）
        val out = splitAssistantSegments("one\r\n\r\ntwo")
        assertTrue(out.size >= 1)
        assertTrue(out.joinToString("\n").contains("one"))
        assertTrue(out.joinToString("\n").contains("two"))
    }

    @Test
    fun `blank line inside a code fence must not split`() {
        // 真机事故防护：代码块里的空行不是分段边界（Kelivo 六重保护里的第一条）
        val text = "before\n\n" + "```kotlin\nval a = 1\n\nval b = 2\n```" + "\n\nafter"
        val out = splitAssistantSegments(text)
        assertEquals(3, out.size)
        assertTrue(out[1].contains("val a = 1"))
        assertTrue(out[1].contains("val b = 2"))
        assertTrue(out[1].contains("```"))
    }

    @Test
    fun `blank line inside a display math block must not split`() {
        val text = "intro\n\n$$\nx = 1\n\ny = 2\n$$\n\noutro"
        val out = splitAssistantSegments(text)
        assertEquals(3, out.size)
        assertTrue(out[1].contains("x = 1"))
        assertTrue(out[1].contains("y = 2"))
    }

    @Test
    fun `tilde fence also protects blank lines`() {
        val text = "a\n\n~~~\nline1\n\nline2\n~~~\n\nb"
        assertEquals(3, splitAssistantSegments(text).size)
    }

    // ---------- 序列化（设置持久化必须无损） ----------

    @Test
    fun `serialization round trip preserves every field`() {
        val original = MessageStyleSetting(
            style = MessageBubbleStyle.FROSTED,
            assistantBubbleWrapContent = true,
            splitSegmentsAsBubbles = true,
            blurStrength = 4,
            blurStyle = BlurStyle.FROSTED_GLASS,
            light = BubbleStyleTheme(
                backgroundArgb = 0x123456,
                frostedOpacity = 0.5f,
                solidOpacity = 0.8f,
                borderArgb = 0xABCDEF,
                borderOpacity = 0.2f,
                borderWidth = 1.5f,
                textArgb = 0x010203,
                cornerRadius = 22f,
            ),
            dark = BubbleStyleTheme(cornerRadius = 6f),
        )
        val json = Json.encodeToString(MessageStyleSetting.serializer(), original)
        val restored = Json.decodeFromString(MessageStyleSetting.serializer(), json)
        assertEquals(original, restored)
    }

    @Test
    fun `decoding a legacy payload without messageStyle keys falls back to defaults`() {
        // 老用户存档里没有这些新键 → 必须读得出来（R1-(4)「不允许出现读不出旧设置的情况」）
        val restored = Json.decodeFromString(MessageStyleSetting.serializer(), "{}")
        assertEquals(MessageStyleSetting(), restored)
        assertEquals(BlurStyle.TRANSPARENT, restored.blurStyle)
        assertEquals(BlurStrength.DEFAULT_INDEX, restored.blurStrength)
    }

    // ---------- [v225 F2] DataStore 键往返（消息样式曾经完全不落盘） ----------

    @Test
    fun messageStyleSurvivesTheDatastoreKeyRoundTrip() {
        val original = MessageStyleSetting(
            style = MessageBubbleStyle.FROSTED,
            assistantBubbleWrapContent = true,
            splitSegmentsAsBubbles = true,
            blurStrength = 3,
            blurStyle = BlurStyle.FROSTED_GLASS,
            light = BubbleStyleTheme(cornerRadius = 28f, frostedOpacity = 0.55f),
            dark = BubbleStyleTheme(backgroundArgb = 0x112233),
        )
        // 模拟 update(): Settings -> DataStore 字符串 -> settingsFlowRaw 解码
        val restored = decodeMessageStyle(encodeMessageStyle(original))
        assertEquals(original, restored)
    }

    @Test
    fun missingDatastoreKeyFallsBackToDefaults() {
        assertEquals(MessageStyleSetting(), decodeMessageStyle(null))
    }

    @Test
    fun datastoreKeyNameIsStable() {
        // 改名会让所有老用户的存档读不回来 -> 用单测钉死
        assertEquals("message_style", MESSAGE_STYLE_KEY_NAME)
    }

    // ---------- [v225 P4] 分段保护补齐（details / 缩进续行，Kelivo 第 2、4 条） ----------

    @Test
    fun detailsBlockProtectsBlankLines() {
        val text = "before\n\n<details>\n<summary>more</summary>\n\nbody line\n</details>\n\nafter"
        assertEquals(
            listOf(
                "before",
                "<details>\n<summary>more</summary>\n\nbody line\n</details>",
                "after",
            ),
            splitAssistantSegments(text),
        )
    }

    @Test
    fun unclosedDetailsKeepsRestInOneSegment() {
        val text = "intro\n\n<details>\n<summary>s</summary>\n\nbody\n\nstill body"
        assertEquals(
            listOf("intro", "<details>\n<summary>s</summary>\n\nbody\n\nstill body"),
            splitAssistantSegments(text),
        )
    }

    @Test
    fun indentedContinuationProtectsBlankLines() {
        val text = "para\n\n    val a = 1\n\n    val b = 2\n\nafter"
        assertEquals(
            listOf("para\n\n    val a = 1\n\n    val b = 2", "after"),
            splitAssistantSegments(text),
        )
    }

    @Test
    fun detailsLiteralInsideCodeFenceDoesNotEscape() {
        // 三反引号用 Char(96) 拼出来（源码里不写字面量）
        val fence = Char(96).toString().repeat(3)
        val text = "before\n\n" + fence + "\n<details>\n\nfenced body\n" + fence + "\n\nafter"
        assertEquals(
            listOf("before", fence + "\n<details>\n\nfenced body\n" + fence, "after"),
            splitAssistantSegments(text),
        )
    }

    @Test
    fun crlfIsNormalizedInsideSegments() {
        assertEquals(listOf("one", "two"), splitAssistantSegments("one\r\n\r\ntwo"))
        val text = "before\r\n\r\n<details>\r\n\r\nbody\r\n</details>\r\n\r\nafter"
        assertEquals(
            listOf("before", "<details>\n\nbody\n</details>", "after"),
            splitAssistantSegments(text),
        )
    }

    // ---------- [v226 C3] merge pass：相邻列表块 / 孤立标题（Kelivo 第 5、6 条保护） ----------

    @Test
    fun adjacentListChunksMergeIntoOneBubble() {
        // 有序列表被空行分开时不能拆成两个气泡，否则每段都从 1 重新编号
        assertEquals(listOf("1. a\n\n2. b"), splitAssistantSegments("1. a\n\n2. b"))
        assertEquals(listOf("- a\n\n- b\n\n- c"), splitAssistantSegments("- a\n\n- b\n\n- c"))
    }

    @Test
    fun listMergeNeedsBothChunksToStartWithAListItem() {
        assertEquals(listOf("intro", "- a"), splitAssistantSegments("intro\n\n- a"))
        assertEquals(listOf("- a", "tail"), splitAssistantSegments("- a\n\ntail"))
    }

    @Test
    fun starPlusAndParenMarkersCountAsListItems() {
        assertEquals(listOf("* a\n\n+ b"), splitAssistantSegments("* a\n\n+ b"))
        assertEquals(listOf("1) a\n\n2) b"), splitAssistantSegments("1) a\n\n2) b"))
    }

    @Test
    fun bareHashIsNotAListItemAndStaysSplit() {
        // `#` 后没有空白 -> 既不是标题也不是列表项
        assertEquals(listOf("#a", "#b"), splitAssistantSegments("#a\n\n#b"))
    }

    @Test
    fun singleLineHeadingMergesWithTheFollowingChunk() {
        assertEquals(listOf("## 标题\n\n正文"), splitAssistantSegments("## 标题\n\n正文"))
        assertEquals(listOf("###### h\n\nbody"), splitAssistantSegments("###### h\n\nbody"))
    }

    @Test
    fun multiLineChunkStartingWithHeadingIsNotMerged() {
        assertEquals(listOf("## 标题\n正文", "尾段"), splitAssistantSegments("## 标题\n正文\n\n尾段"))
    }

    @Test
    fun sevenHashesIsNotAnAtxHeading() {
        assertEquals(listOf("####### x", "y"), splitAssistantSegments("####### x\n\ny"))
    }

    @Test
    fun headingMergeDoesNotChainIntoFollowingHeadings() {
        // 合并后不再是「单行标题」，不会继续吞并下一段
        assertEquals(listOf("## a\n\n## b", "c"), splitAssistantSegments("## a\n\n## b\n\nc"))
    }

    // ---------- [v226 C3] 侦察报告点出的未覆盖边界（流式半成品 / 前导空白） ----------

    @Test
    fun unclosedFenceProtectsEverythingAfterIt() {
        // 流式输出到一半（围栏还没收尾）→ 其后所有空行都不拆
        val text = "a\n\n" + "```" + "\ncode\n\nmore"
        assertEquals(listOf("a", "```" + "\ncode\n\nmore"), splitAssistantSegments(text))
    }

    @Test
    fun unclosedDisplayMathProtectsEverythingAfterIt() {
        val text = "a\n\n$$\nx = 1\n\nmore"
        assertEquals(listOf("a", "$$\nx = 1\n\nmore"), splitAssistantSegments(text))
    }

    @Test
    fun shorterClosingFenceDoesNotCloseALongerOpener() {
        // 收栏 run 必须 >= 开栏 run（CommonMark）：3 个反引号关不掉 4 个反引号的块
        val text = "a\n\n" + "````" + "\ncode\n\nmore\n" + "```" + "\n\nb"
        assertEquals(listOf("a", "````" + "\ncode\n\nmore\n" + "```" + "\n\nb"), splitAssistantSegments(text))
    }

    @Test
    fun listMergeAllowsLeadingWhitespace() {
        assertEquals(listOf("  - a\n\n  - b"), splitAssistantSegments("  - a\n\n  - b"))
    }

    @Test
    fun headingMergeAllowsLeadingWhitespace() {
        assertEquals(listOf("  # h\n\nbody"), splitAssistantSegments("  # h\n\nbody"))
    }
}
