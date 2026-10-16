package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.assembleMainPrompt
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_ACTIVATE
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_DONT_ACTIVATE
import me.rerere.rikkahub.data.model.contentWithoutDecorators
import me.rerere.rikkahub.data.model.parseWorldInfoDecorators
import me.rerere.rikkahub.data.model.worldInfoDecorators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * W2 验收：世界书 `@@` 装饰器（`@@activate` / `@@dont_activate`）。
 *
 * 金标准：SillyTavern 1.18.0
 * - `world-info.js:100` KNOWN_DECORATORS（官方只有这两个）；
 * - `world-info.js:4538-4582` parseDecorators（含 `@@@` 转义的逐字行为）；
 * - `world-info.js:4763-4770` 扫描判定顺序：`@@activate` → 激活并 continue；
 *   `@@dont_activate` → 跳过；两者都在关键词匹配之前、constant/sticky 之前。
 */
class WorldInfoDecoratorTest {

    private fun textOf(messages: List<UIMessage>): String =
        messages.joinToString("\n") { m ->
            m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        }

    private fun decoratorEntry(
        name: String,
        content: String,
        keywords: List<String> = emptyList(),
        constant: Boolean = false,
    ): PromptInjection.RegexInjection = PromptInjection.RegexInjection(
        name = name,
        enabled = true,
        priority = 1,
        position = InjectionPosition.AFTER_CHARACTER,
        content = content,
        keywords = keywords,
        constantActive = constant,
        ignoreBudget = true, // 聚焦装饰器语义，避开短消息预算估算干扰
    )

    private fun scan(
        entries: List<PromptInjection.RegexInjection>,
        userText: String = "hello",
        recursive: Boolean = false,
    ): String {
        val book = Lorebook(id = Uuid.random(), name = "book", enabled = true, entries = entries)
        val assistant = Assistant(name = "char", lorebookIds = setOf(book.id))
        return textOf(
            transformMessages(
                messages = listOf(UIMessage.user(userText)),
                assistant = assistant,
                modeInjections = emptyList(),
                lorebooks = listOf(book),
                worldInfoRecursive = recursive,
                worldInfoMaxRecursionSteps = if (recursive) 2 else 0,
            )
        )
    }

    // ==================== parseDecorators 逐字对齐 ====================

    @Test
    fun `parse decorators matches st happy path`() {
        // 非 @@ 开头：原样
        val plain = parseWorldInfoDecorators("plain content")
        assertEquals(emptyList<String>(), plain.decorators)
        assertEquals("plain content", plain.content)

        // 单个 @@activate：装饰器记录，正文剥壳
        val single = parseWorldInfoDecorators("@@activate\nBODY")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), single.decorators)
        assertEquals("BODY", single.content)

        // 两个装饰器依次记录，正文从第一行非 @@ 行开始
        val both = parseWorldInfoDecorators("@@activate\n@@dont_activate\nBODY\nLINE2")
        assertEquals(
            listOf(WORLD_INFO_DECORATOR_ACTIVATE, WORLD_INFO_DECORATOR_DONT_ACTIVATE),
            both.decorators,
        )
        assertEquals("BODY\nLINE2", both.content)

        // 正文中间出现的 @@ 不是装饰器（只扫描开头连续 @@ 行）
        val inline = parseWorldInfoDecorators("BODY\n@@activate")
        assertEquals(emptyList<String>(), inline.decorators)
        assertEquals("BODY\n@@activate", inline.content)
    }

    @Test
    fun `parse decorators full-at-line semantics match st`() {
        // 全部行都是 @@ 行（未触发结束分支）→ 官方 content 保持原样
        val allDecorators = parseWorldInfoDecorators("@@activate")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), allDecorators.decorators)
        assertEquals("@@activate", allDecorators.content)

        // 首行 @@@ 转义（未 fallback）→ 跳过；正文从下一非 @@ 行开始
        val escaped = parseWorldInfoDecorators("@@@activate\nBODY")
        assertEquals(emptyList<String>(), escaped.decorators)
        assertEquals("BODY", escaped.content)

        // 未知装饰器打开 fallback；之后的 @@@activate 按转义还原成 @@activate 记录
        val unknownThenEscape = parseWorldInfoDecorators("@@unknown\n@@@activate\nBODY")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), unknownThenEscape.decorators)
        assertEquals("BODY", unknownThenEscape.content)

        // 官方 isKnownDecorator 用 startsWith：@@activate_extra 会被记录，但 includes 判定不激活
        val suffix = parseWorldInfoDecorators("@@activate_extra\nBODY")
        assertEquals(listOf("@@activate_extra"), suffix.decorators)
        assertEquals("BODY", suffix.content)
    }

    @Test
    fun `derived fields do not touch stored content`() {
        val entry = decoratorEntry("e", "@@activate\nFORCED")
        assertEquals(listOf(WORLD_INFO_DECORATOR_ACTIVATE), entry.worldInfoDecorators)
        assertEquals("FORCED", entry.contentWithoutDecorators)
        // 派生读取不得改写存储内容
        assertEquals("@@activate\nFORCED", entry.content)
    }

    // ==================== 扫描语义 ====================

    @Test
    fun `activate decorator forces entry without keywords`() {
        val joined = scan(listOf(decoratorEntry("forced", "@@activate\nFORCED-CONTENT")))
        assertTrue("无关键词也应按 @@activate 注入：$joined", joined.contains("FORCED-CONTENT"))
        assertFalse("装饰器行不应进入提示词", joined.contains("@@activate"))
    }

    @Test
    fun `dont activate decorator suppresses entry with matching keyword`() {
        val joined = scan(
            listOf(decoratorEntry("blocked", "@@dont_activate\nBLOCKED-CONTENT", keywords = listOf("hello")))
        )
        assertFalse("命中关键词也必须被 @@dont_activate 压制：$joined", joined.contains("BLOCKED-CONTENT"))
    }

    @Test
    fun `decorator precedence activate wins over dont activate`() {
        // 官方判定顺序：@@activate 在前（先 continue），@@dont_activate 在后
        val joined = scan(
            listOf(decoratorEntry("both", "@@activate\n@@dont_activate\nBOTH-CONTENT"))
        )
        assertTrue("@@activate 优先：$joined", joined.contains("BOTH-CONTENT"))
    }

    @Test
    fun `decorator comparison is exact and case sensitive`() {
        // 官方 includes()/startsWith 都区分大小写：@@ACTIVATE 不是已知装饰器。
        // 官方 parseDecorators 对「未知 @@ 行」的处理是：置 fallback 标记、该行不计入装饰器，
        // 后续正文从第一个非 @@ 行开始 —— 即 @@ACTIVATE 行被丢掉、条目按普通关键词条目参与扫描。
        val entry = decoratorEntry("upper", "@@ACTIVATE\nUPPER-CONTENT", keywords = listOf("hello"))
        assertEquals(emptyList<String>(), entry.worldInfoDecorators)
        assertEquals("UPPER-CONTENT", entry.contentWithoutDecorators)
        val joined = scan(listOf(entry))
        assertTrue("未知装饰器行被剥离后，条目仍是普通关键词条目：$joined", joined.contains("UPPER-CONTENT"))
        assertFalse("@@ACTIVATE 行本身不应进入提示词：$joined", joined.contains("@@ACTIVATE"))
    }

    @Test
    fun `activate decorator respects disabled entries`() {
        val disabled = decoratorEntry("off", "@@activate\nDISABLED-CONTENT").copy(enabled = false)
        val joined = scan(listOf(disabled))
        assertFalse("enabled=false 的条目即使带 @@activate 也必须跳过：$joined", joined.contains("DISABLED-CONTENT"))
    }

    @Test
    fun `decorated content feeds recursion buffer stripped`() {
        // 递归缓冲使用剥壳后的正文（官方 entry.content 已被 parseDecorators 改写），
        // 因此子条目的关键词命中 "RECURSE-SEED" 而不是带装饰器的原文。
        val joined = scan(
            entries = listOf(
                decoratorEntry("seed", "@@activate\nRECURSE-SEED"),
                decoratorEntry("child", "CHILD-CONTENT", keywords = listOf("RECURSE-SEED")),
            ),
            recursive = true,
        )
        assertTrue("剥壳后的正文应参与递归扫描：$joined", joined.contains("CHILD-CONTENT"))
    }

    @Test
    fun `extended ccv3 decorators override depth role position and keys`() {
        val raw = "@@depth 2\n@@role user\n@@additional_keys sword, shield\n@@exclude_keys cursed\n@@ignore_on_max_context\nARMORY-BODY"
        val parsed = parseWorldInfoDecorators(raw)
        assertEquals("ARMORY-BODY", parsed.content)
        assertEquals(2, parsed.depthOverride)
        assertEquals("user", parsed.roleOverride)
        assertEquals(listOf("sword", "shield"), parsed.additionalKeys)
        assertEquals(listOf("cursed"), parsed.excludeKeys)
        assertTrue(parsed.ignoreOnMaxContext)

        val baseEntry = decoratorEntry("armory", raw, keywords = listOf(" knights "))
        val overridden = me.rerere.rikkahub.data.model.applyWorldInfoDecoratorOverrides(baseEntry)
        assertEquals(InjectionPosition.AT_DEPTH, overridden.position)
        assertEquals(2, overridden.injectDepth)
        assertEquals(me.rerere.ai.core.MessageRole.USER, overridden.role)
        assertTrue(overridden.keywords.containsAll(listOf("sword", "shield")))
    }

    @Test
    fun `exclude keys suppresses entry with secondary keys AND_ANY and constant active entries`() {
        val entryWithSecondary = decoratorEntry(
            name = "hero_forest",
            content = "@@exclude_keys: 剧透\nHERO-FOREST-BODY",
            keywords = listOf("勇者"),
        ).copy(
            selective = true,
            secondaryKeys = listOf("森林"),
            selectiveLogic = me.rerere.rikkahub.data.model.SelectiveLogic.AND_ANY,
        )

        assertFalse("排除词不应被当作正向副键触发", scan(listOf(entryWithSecondary), "勇者在剧透中").contains("HERO-FOREST-BODY"))
        assertFalse("命中主副键但同时命中排除词时绝不激活", scan(listOf(entryWithSecondary), "勇者在森林剧透").contains("HERO-FOREST-BODY"))
        assertTrue("命中主副键且未命中排除词时正常激活", scan(listOf(entryWithSecondary), "勇者在森林中").contains("HERO-FOREST-BODY"))

        val constantWithExclude = decoratorEntry(
            name = "const_entry",
            content = "@@exclude_keys: 禁用词\nCONST-BODY",
            constant = true,
        )
        assertFalse("常驻条目命中 @@exclude_keys 时不激活", scan(listOf(constantWithExclude), "这段包含禁用词").contains("CONST-BODY"))
        assertTrue("常驻条目未命中 @@exclude_keys 时激活", scan(listOf(constantWithExclude), "普通对话").contains("CONST-BODY"))
    }

    @Test
    fun `lorebook group batch settings preserves entry name content and keywords`() {
        val entries = listOf(
            decoratorEntry("条目1", "内容1", keywords = listOf("k1")).copy(group = "G1", secondaryKeys = listOf("s1")),
            decoratorEntry("条目2", "内容2", keywords = listOf("k2")).copy(group = "G1", secondaryKeys = listOf("s2")),
            decoratorEntry("条目3", "内容3", keywords = listOf("k3")).copy(group = "G1", secondaryKeys = listOf("s3")),
        )
        val template = entries[0].copy(
            name = "被污染的名称",
            content = "被污染的正文",
            keywords = listOf("bad"),
            secondaryKeys = listOf("bad2"),
            injectDepth = 2,
            groupWeight = 50,
        )
        val updated = me.rerere.rikkahub.ui.pages.extensions.applyLorebookGroupBatchSettings(entries, "G2", template)
        assertEquals(listOf("条目1", "条目2", "条目3"), updated.map { it.name })
        assertEquals(listOf("内容1", "内容2", "内容3"), updated.map { it.content })
        assertEquals(listOf(listOf("k1"), listOf("k2"), listOf("k3")), updated.map { it.keywords })
        assertEquals(listOf(listOf("s1"), listOf("s2"), listOf("s3")), updated.map { it.secondaryKeys })
        assertTrue(updated.all { it.group == "G2" && it.injectDepth == 2 && it.groupWeight == 50 })
    }


    @Test
    fun `task08 progressive plain preview and mixed message pieces and serial scheduler`() = kotlinx.coroutines.runBlocking {
        // 1. 正例 3：超长含 <style>/<script> 的开场白快速剥壳预览非空且不含 <style>
        val longRaw = "<style>.box { color: red; }</style><script>console.log(1)</script><!-- comment -->" +
            "这是一段超长的小说正文内容。".repeat(2000) +
            "<div class=\"box\">状态栏</div>"
        val preview = me.rerere.rikkahub.ui.components.message.extractQuickPlainPreview(longRaw)
        assertTrue(preview.isNotEmpty())
        assertFalse(preview.contains("<style>"))
        assertFalse(preview.contains("<script>"))
        assertTrue(preview.contains("这是一段超长的小说正文内容。"))

        // 2. 正例 1：混排消息切分为 [MarkdownBody, HtmlPanel(title="角色状态栏"), MarkdownBody]
        val mixedInput = "第一段五百字小说正文导语。\n\n" +
            "<style>.status-box { padding: 8px; }</style><div class=\"status-box\"><script>var a=1;</script>角色状态栏 stat_data</div>\n\n" +
            "后续三百字小说正文。"
        val pieces = me.rerere.rikkahub.ui.components.message.splitMixedMessagePieces(mixedInput)
        assertEquals(3, pieces.size)
        assertTrue(pieces[0] is me.rerere.rikkahub.ui.components.message.MixedContentPiece.MarkdownBody)
        assertTrue(pieces[1] is me.rerere.rikkahub.ui.components.message.MixedContentPiece.HtmlPanel)
        assertEquals("角色状态栏", (pieces[1] as me.rerere.rikkahub.ui.components.message.MixedContentPiece.HtmlPanel).panelTitle)
        assertTrue(pieces[2] is me.rerere.rikkahub.ui.components.message.MixedContentPiece.MarkdownBody)

        // 3. 正例 2：串行队列不并发（P1 未完成前 P2、P3 的 gate.isCompleted 恒为 false）
        val scheduler = me.rerere.rikkahub.ui.components.message.TavernPanelLoadScheduler
        scheduler.resetForTest()
        scheduler.hostReadyProvider = { true }
        scheduler.cooldownMsOverride = 10L
        val r1 = scheduler.enqueue("P1", "面板1")
        val r2 = scheduler.enqueue("P2", "面板2")
        val r3 = scheduler.enqueue("P3", "面板3")
        kotlinx.coroutines.delay(30L)
        assertTrue(r1.gate.isCompleted)
        assertFalse(r2.gate.isCompleted)
        assertFalse(r3.gate.isCompleted)
        scheduler.markDone("P1")
        kotlinx.coroutines.delay(40L)
        assertTrue(r2.gate.isCompleted)
        assertFalse(r3.gate.isCompleted)
        scheduler.markDone("P2")
        kotlinx.coroutines.delay(40L)
        assertTrue(r3.gate.isCompleted)
        scheduler.markDone("P3")
        scheduler.resetForTest()

        // 4. 正例 4：PanelMountState 响应式 mounted 状态
        val mountState = me.rerere.rikkahub.ui.components.message.PanelMountState()
        assertFalse(mountState.mounted)
        mountState.setInternalScrollEnabled(true)
        mountState.simulateMountForTest("<div>ok</div>")
        assertTrue(mountState.mounted)
    }


    @Test
    fun `task06 and task07 wiFormat text macro worldInfo regex ANTop ANBottom and disabledMarkers`() {
        // 1. formatWorldInfoContent 同时支持 {0} 与 {{text}}
        assertEquals("[WI: 龙之谷]", formatWorldInfoContent("[WI: {{text}}]", "龙之谷"))
        assertEquals("[WI: 龙之谷]", formatWorldInfoContent("[WI: {0}]", "龙之谷"))

        // 2. RegexPlacement.WORLD_INFO 改写激活的世界书内容，且 ANTop / ANBottom 按顺序围绕作者备注注入
        val topEntry = decoratorEntry("top", "@@position antop\nRAW_TOP_LORE", constant = true).copy(priority = 1)
        val bottomEntry = decoratorEntry("bottom", "@@position anbottom\nRAW_BOTTOM_LORE", constant = true).copy(priority = 2)
        val beforeCharEntry = decoratorEntry("before_c", "BEFORE_CHAR_LORE", constant = true).copy(
            position = InjectionPosition.BEFORE_CHARACTER,
            priority = 3,
        )
        val book = Lorebook(id = Uuid.random(), name = "book", enabled = true, entries = listOf(topEntry, bottomEntry, beforeCharEntry))
        val assistant = Assistant(name = "Alice", lorebookIds = setOf(book.id))
        val wiRegex = me.rerere.rikkahub.data.st.regex.RegexScript(
            id = "wi-rx",
            scriptName = "RewriteWI",
            findRegex = "RAW_TOP_LORE",
            replaceString = "REGEXED_TOP_{{char}}",
            placement = listOf(me.rerere.rikkahub.data.st.regex.RegexPlacement.WORLD_INFO),
            disabled = false,
            substituteRegex = 1,
        )
        val outMsgs = transformMessages(
            messages = listOf(UIMessage.user("hello")),
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
            regexScripts = listOf(wiRegex),
            wiFormat = "[WI: {{text}}]",
            authorNote = "CENTRAL_AUTHOR_NOTE",
            disabledMarkers = setOf("worldInfoBefore"),
        )
        val fullText = textOf(outMsgs)
        assertFalse("disabledMarkers 中禁用 worldInfoBefore 时不应注入 BEFORE_CHARACTER 条目", fullText.contains("BEFORE_CHAR_LORE"))
        assertTrue("WORLD_INFO 正则与 {{text}} 格式化应生效", fullText.contains("[WI: REGEXED_TOP_Alice]"))
        val topPos = fullText.indexOf("[WI: REGEXED_TOP_Alice]")
        val notePos = fullText.indexOf("CENTRAL_AUTHOR_NOTE")
        val bottomPos = fullText.indexOf("[WI: RAW_BOTTOM_LORE]")
        assertTrue("ANTop 必须在作者备注之前且 ANBottom 在作者备注之后", topPos in 0 until notePos && notePos < bottomPos)

        // 3. 显示侧正则 depth 过滤与宏展开（maxDepth=0 仅在 depth=0 最新消息生效，depth=1 历史消息不生效）
        val displayRx = me.rerere.rikkahub.data.st.regex.RegexScript(
            id = "disp-rx",
            scriptName = "Depth0Only",
            findRegex = "SECRET",
            replaceString = "REVEALED_FOR_{{user}}",
            placement = listOf(me.rerere.rikkahub.data.st.regex.RegexPlacement.AI_OUTPUT),
            markdownOnly = true,
            minDepth = 0,
            maxDepth = 0,
            substituteRegex = 1,
        )
        val renderedDepth0 = me.rerere.rikkahub.ui.components.message.computeStRegexedText(
            text = "SECRET",
            scripts = listOf(displayRx),
            assistant = assistant,
            scope = me.rerere.rikkahub.data.model.AssistantAffectScope.ASSISTANT,
            placement = me.rerere.rikkahub.data.st.regex.RegexPlacement.AI_OUTPUT,
            depth = 0,
            userName = "Bob",
        )
        val renderedDepth1 = me.rerere.rikkahub.ui.components.message.computeStRegexedText(
            text = "SECRET",
            scripts = listOf(displayRx),
            assistant = assistant,
            scope = me.rerere.rikkahub.data.model.AssistantAffectScope.ASSISTANT,
            placement = me.rerere.rikkahub.data.st.regex.RegexPlacement.AI_OUTPUT,
            depth = 1,
            userName = "Bob",
        )
        assertEquals("REVEALED_FOR_Bob", renderedDepth0)
        assertEquals("SECRET", renderedDepth1)
    }

    @Test
    fun `d3 d5 d6 d7 d8 d9 comprehensive regression tests`() {
        // D3: 超高 HTML 面板安全封顶与内部滚动开启
        val (normalDp, normalScroll) = me.rerere.rikkahub.ui.components.message.computePanelHeightConstraint(600, density = 3f)
        assertEquals(600, normalDp)
        assertFalse(normalScroll)
        val (tallDp, tallScroll) = me.rerere.rikkahub.ui.components.message.computePanelHeightConstraint(5000, density = 3f)
        assertTrue("超高面板必须封顶在 GPU 安全纹理高度内", tallDp <= 1600)
        assertTrue("封顶面板必须启用内部滚动", tallScroll)

        // D5: squashAdjacentSystemMessages 将连续无名称 SYSTEM 消息合并，不被 <system-reminder> 打断
        val sysList = listOf(
            UIMessage.system("Main Prompt"),
            UIMessage.system("Char Description"),
            UIMessage.system("[Start a new Chat]"),
            UIMessage.user("Hello"),
        )
        val squashed = me.rerere.rikkahub.data.ai.squashAdjacentSystemMessages(sysList, "\n\n")
        assertEquals(2, squashed.size)
        assertEquals("Main Prompt\n\nChar Description\n\n[Start a new Chat]", textOf(listOf(squashed[0])))

        // D6: forbid_overrides (main 与 jailbreak) 覆盖与 {{original}} 展开
        val cardWithSys = Assistant(
            name = "CardChar",
            systemPrompt = "CARD_SYS_PROMPT",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(
                name = "CardChar",
                systemPrompt = "CARD_SYS_PROMPT",
                postHistoryInstructions = "CARD_PHI_WITH_{{original}}",
            ),
        )
        assertEquals(
            "PRESET_MAIN",
            cardWithSys.assembleMainPrompt(forbidOverrides = true, presetMainPrompt = "PRESET_MAIN"),
        )
        assertEquals(
            "CARD_SYS_PROMPT",
            cardWithSys.assembleMainPrompt(forbidOverrides = false, presetMainPrompt = "PRESET_MAIN"),
        )

        // D7: 短对话下世界书百分比预算按模型上下文窗口（默认 16384）计算，不因当前消息短而丢弃普通条目
        val normalEntry = PromptInjection.RegexInjection(
            name = "normal_budget_entry",
            enabled = true,
            priority = 1,
            position = InjectionPosition.AFTER_CHARACTER,
            content = "这是一段超过三十个Token的正常世界书背景设定正文，在新开启的短对话中绝不能因为预算分母算错而被丢弃。".repeat(3),
            constantActive = true,
            ignoreBudget = false,
        )
        val budgetBook = Lorebook(id = Uuid.random(), name = "budgetBook", enabled = true, entries = listOf(normalEntry))
        val budgetAssistant = Assistant(name = "Alice", lorebookIds = setOf(budgetBook.id))
        val shortChatResult = textOf(
            transformMessages(
                messages = listOf(UIMessage.user("hi")),
                assistant = budgetAssistant,
                modeInjections = emptyList(),
                lorebooks = listOf(budgetBook),
                worldInfoBudget = 25,
            )
        )
        assertTrue("短对话下正常世界书条目必须注入成功", shortChatResult.contains("这是一段超过三十个Token的正常世界书背景设定正文"))

        // D8: 相对注入按相邻同 Role 合并（RLE），保留 SYSTEM -> USER -> ASSISTANT -> SYSTEM 交错顺序
        val id1 = Uuid.random()
        val id2 = Uuid.random()
        val id3 = Uuid.random()
        val id4 = Uuid.random()
        val interleaved = listOf(
            PromptInjection.ModeInjection(id = id1, name = "m1", enabled = true, priority = 0, position = InjectionPosition.TOP_OF_CHAT, role = me.rerere.ai.core.MessageRole.SYSTEM, content = "SYS_1", presetGroup = "P"),
            PromptInjection.ModeInjection(id = id2, name = "m2", enabled = true, priority = 1, position = InjectionPosition.TOP_OF_CHAT, role = me.rerere.ai.core.MessageRole.USER, content = "USR_1", presetGroup = "P"),
            PromptInjection.ModeInjection(id = id3, name = "m3", enabled = true, priority = 2, position = InjectionPosition.TOP_OF_CHAT, role = me.rerere.ai.core.MessageRole.ASSISTANT, content = "AST_1", presetGroup = "P"),
            PromptInjection.ModeInjection(id = id4, name = "m4", enabled = true, priority = 3, position = InjectionPosition.TOP_OF_CHAT, role = me.rerere.ai.core.MessageRole.SYSTEM, content = "SYS_2", presetGroup = "P"),
        )
        val interleavedAssistant = Assistant(name = "Alice", modeInjectionIds = setOf(id1, id2, id3, id4))
        val interleavedOut = transformMessages(
            messages = listOf(UIMessage.user("CHAT_START")),
            assistant = interleavedAssistant,
            modeInjections = interleaved,
            lorebooks = emptyList(),
        )
        assertEquals(
            listOf("SYS_1", "USR_1", "AST_1", "SYS_2", "CHAT_START"),
            interleavedOut.map { textOf(listOf(it)) },
        )

        // D9.1 & D9.2: 当本轮 authorNote 为空（未达间隔轮次）时 ANTop/ANBottom 不注入；且 WORLD_INFO 正则使用真实 userName
        val anTopEntry = decoratorEntry("top", "@@position antop\nTOP_LORE_FOR_{{user}}", constant = true)
        val anBook = Lorebook(id = Uuid.random(), name = "anBook", enabled = true, entries = listOf(anTopEntry))
        val suppressedOut = textOf(
            transformMessages(
                messages = listOf(UIMessage.user("hello")),
                assistant = Assistant(name = "Alice", lorebookIds = setOf(anBook.id)),
                modeInjections = emptyList(),
                lorebooks = listOf(anBook),
                authorNote = "",
            )
        )
        assertFalse("当本轮未激活 Author's Note 时，ANTop 不应单独注入", suppressedOut.contains("TOP_LORE_FOR_"))

        val userRx = me.rerere.rikkahub.data.st.regex.RegexScript(
            id = "wi-user-rx",
            scriptName = "UserMacroWI",
            findRegex = "TOP_LORE_FOR_\\{\\{user\\}\\}",
            replaceString = "TOP_LORE_FOR_{{user}}",
            placement = listOf(me.rerere.rikkahub.data.st.regex.RegexPlacement.WORLD_INFO),
            disabled = false,
            substituteRegex = 1,
        )
        val activeAnOut = textOf(
            transformMessages(
                messages = listOf(UIMessage.user("hello")),
                assistant = Assistant(name = "Alice", lorebookIds = setOf(anBook.id)),
                modeInjections = emptyList(),
                lorebooks = listOf(anBook),
                regexScripts = listOf(userRx),
                authorNote = "ACTIVE_NOTE",
                userName = "PersonaUser",
            )
        )
        assertTrue("WORLD_INFO 阶段应将 {{user}} 展开为传入的真实用户名 PersonaUser", activeAnOut.contains("TOP_LORE_FOR_PersonaUser"))
    }

    @Test
    fun `characterFilter vectorized charx byaf and jsonl roundtrip work`() {
        // 1. characterFilter 白名单与黑名单过滤
        val alice = Assistant(
            name = "Alice",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(name = "Alice", tags = listOf("elf", "mage")),
        )
        val bob = Assistant(
            name = "Bob",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(name = "Bob", tags = listOf("warrior")),
        )
        val entryOnlyAlice = PromptInjection.RegexInjection(
            name = "AliceSecret",
            content = "ALICE_ONLY_LORE",
            keywords = listOf("hello"),
            characterFilterNames = listOf("Alice"),
            characterFilterIsExclude = false,
            ignoreBudget = true,
        )
        val entryExcludeMage = PromptInjection.RegexInjection(
            name = "NoMage",
            content = "NO_MAGE_LORE",
            keywords = listOf("hello"),
            characterFilterTags = listOf("mage"),
            characterFilterIsExclude = true,
            ignoreBudget = true,
        )
        assertTrue(entryOnlyAlice.matchesCharacterFilter(alice))
        assertFalse(entryOnlyAlice.matchesCharacterFilter(bob))
        assertFalse(entryExcludeMage.matchesCharacterFilter(alice))
        assertTrue(entryExcludeMage.matchesCharacterFilter(bob))

        // 2. vectorized 向量语义相似度激活（关键词未直接命中，但语义词袋高重合）
        val vecBookId = Uuid.random()
        val vecEntry = PromptInjection.RegexInjection(
            name = "DragonVolcano",
            content = "VECTOR_DRAGON_LORE",
            keywords = listOf("ancient_wyrm_key"),
            secondaryKeys = listOf("crimson", "dragon", "volcano", "slumber"),
            vectorized = true,
            ignoreBudget = true,
        )
        val vecBook = Lorebook(id = vecBookId, name = "VecBook", enabled = true, entries = listOf(vecEntry))
        PromptInjectionTransformer.vectorWorldInfoEnabled = true
        PromptInjectionTransformer.vectorThreshold = 0.20
        val vecOut = transformMessages(
            messages = listOf(UIMessage.user("Tell me about the crimson dragon in the volcano")),
            assistant = alice.copy(lorebookIds = setOf(vecBookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(vecBook),
        )
        val vecJoined = vecOut.joinToString("\n") { m ->
            m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        }
        assertTrue("Vectorized entry should activate on high cosine similarity", vecJoined.contains("VECTOR_DRAGON_LORE"))

        // 3. .charx (CCv3 ZIP 角色卡) 与 .byaf (Backyard AI 归档) 解析验证
        val charxBuffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(charxBuffer).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("card.json"))
            zos.write("""{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"CharxHero","description":"From CharX","first_mes":"Hello CharX"}}""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("assets/emotion/joy.png"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }
        val (charxAssistant, _) = me.rerere.rikkahub.ui.pages.assistant.detail.parseCharxBytes(charxBuffer.toByteArray())
        assertEquals("CharxHero", charxAssistant.name)
        assertTrue(charxAssistant.tavernData?.assets.orEmpty().isNotEmpty())

        val byafBuffer = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(byafBuffer).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("character.json"))
            zos.write("""{"aiName":"ByafKnight","aiPersona":"A brave knight","firstMessage":"Greetings!","loreItems":[{"key":"sword","value":"Excalibur"}]}""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val (byafAssistant, byafBooks) = me.rerere.rikkahub.ui.pages.assistant.detail.parseByafBytes(byafBuffer.toByteArray())
        assertEquals("ByafKnight", byafAssistant.name)
        assertEquals(1, byafBooks.size)

        // 4. 官方 SillyTavern .jsonl 聊天记录导出与导入无损往返
        val conv = me.rerere.rikkahub.data.model.Conversation(
            id = Uuid.random(),
            assistantId = alice.id,
            title = "Alice Chat",
            messageNodes = listOf(
                me.rerere.rikkahub.data.model.MessageNode(
                    messages = listOf(UIMessage.user("Hello Alice")),
                ),
                me.rerere.rikkahub.data.model.MessageNode(
                    messages = listOf(
                        UIMessage.assistant("Swipe 1"),
                        UIMessage.assistant("Swipe 2"),
                    ),
                    selectIndex = 1,
                ),
            ),
        )
        val jsonl = me.rerere.rikkahub.data.st.runtime.exportConversationToStJsonl(conv, "Traveler", "Alice")
        val importedConv = me.rerere.rikkahub.data.st.runtime.importConversationFromStJsonl(jsonl, alice.id)
        assertEquals(2, importedConv.messageNodes.size)
        assertEquals(2, importedConv.messageNodes[1].messages.size)
        assertEquals(1, importedConv.messageNodes[1].selectIndex)
    }

    @Test
    fun `kot07 delayOverrideEntries and charx embeded asset uri rewriting work`() {
        // 1. 验证 KOT-07 delayOverrideEntries 在 transformMessages -> collectInjections 中生效
        val delayedEntry = decoratorEntry(
            name = "delayed_lore",
            content = "DELAYED_LORE_BODY",
            keywords = listOf("hello"),
        )
        val book = Lorebook(id = Uuid.random(), name = "delayBook", enabled = true, entries = listOf(delayedEntry))
        val assistant = Assistant(name = "Alice", lorebookIds = setOf(book.id))

        // 当 delayOverrideEntries 指定需要至少 3 条历史消息，而当前只有 1 条时，被抑制
        val suppressed = textOf(
            transformMessages(
                messages = listOf(UIMessage.user("hello")),
                assistant = assistant,
                modeInjections = emptyList(),
                lorebooks = listOf(book),
                delayOverrideEntries = mapOf(delayedEntry.id to 3),
            )
        )
        assertFalse("Turn count 1 < delayOverride 3 should suppress entry", suppressed.contains("DELAYED_LORE_BODY"))

        // 当历史消息数达到 3 条时正常激活
        val activated = textOf(
            transformMessages(
                messages = listOf(UIMessage.user("hello"), UIMessage.assistant("hi"), UIMessage.user("hello again")),
                assistant = assistant,
                modeInjections = emptyList(),
                lorebooks = listOf(book),
                delayOverrideEntries = mapOf(delayedEntry.id to 3),
            )
        )
        assertTrue("Turn count 3 >= delayOverride 3 should activate entry", activated.contains("DELAYED_LORE_BODY"))

        // 2. 验证 .charx 归档内 embeded:// 与 embedded:// 资源路径重写为可用 URL/DataURI
        val charxBuf = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(charxBuf).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("card.json"))
            zos.write(
                """
                {
                  "spec": "chara_card_v3",
                  "spec_version": "3.0",
                  "data": {
                    "name": "CharxSpriteHero",
                    "description": "Portrait: embeded://assets/icon/main.png",
                    "first_mes": "<img src=\"embeded://assets/emotion/smile.png\">",
                    "assets": [
                      {"type": "icon", "uri": "embeded://assets/icon/main.png", "name": "main", "ext": "png"},
                      {"type": "emotion", "uri": "embeded://assets/emotion/smile.png", "name": "smile", "ext": "png"}
                    ]
                  }
                }
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("assets/icon/main.png"))
            zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("assets/emotion/smile.png"))
            zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zos.closeEntry()
        }
        val (parsedAssistant, _) = me.rerere.rikkahub.ui.pages.assistant.detail.parseCharxBytes(charxBuf.toByteArray())
        val firstMes = parsedAssistant.tavernData?.firstMessage.orEmpty()
        assertFalse("embeded:// in firstMessage should be rewritten", firstMes.contains("embeded://"))
        assertTrue("Rewritten firstMessage should contain resolved data URI or localhost URL", firstMes.contains("data:image/png;base64,"))
    }
}
