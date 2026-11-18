package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 世界书 + 预设「真的组装进请求」的端到端验证。
 *
 * 用户的核心疑问是「世界书那些链路有没有真的运行下来、有没有真的把输出消息
 * 通过世界书和预设组装发出去」。只看 `internalMessages=8` 这种计数无法回答，
 * 所以这里直接断言**最终消息内容**里出现了什么。
 *
 * 覆盖 5 个层次：
 *   L1 关键词命中 → 条目被激活
 *   L2 激活内容真的出现在最终 messages 文本里
 *   L3 未命中的条目**不**出现（防止「全都注入」的假通过）
 *   L4 预设注入（ModeInjection）与世界书**同时**出现在同一份请求里，且顺序合理
 *   L5 活性：改变输入消息 → 命中的世界书条目随之改变
 */
class WorldInfoEndToEndPipelineTest {

    private fun textOf(messages: List<UIMessage>): String =
        messages.joinToString("\n") { m ->
            m.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        }

    private fun loreEntry(
        name: String,
        keywords: List<String>,
        content: String,
        position: InjectionPosition = InjectionPosition.BEFORE_CHARACTER,
        constant: Boolean = false,
    ) = PromptInjection.RegexInjection(
        id = Uuid.random(),
        name = name,
        enabled = true,
        keywords = keywords,
        content = content,
        position = position,
        constantActive = constant,
    )

    private fun lorebook(vararg entries: PromptInjection.RegexInjection) = Lorebook(
        id = Uuid.random(),
        name = "Test Lorebook",
        entries = entries.toList(),
    )

    private fun userMessage(text: String) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
    )

    // 注意：世界书只有在 assistant.lorebookIds 里登记后才会参与激活。
    // 这是 ST 的绑定语义（角色卡/会话绑定的世界书），不是 bug。
    private fun assistant(
        lorebookIds: Set<Uuid> = emptySet(),
        modeInjectionIds: Set<Uuid> = emptySet(),
    ) = Assistant(
        id = Uuid.random(),
        name = "Tester",
        lorebookIds = lorebookIds,
        modeInjectionIds = modeInjectionIds,
    )

    private fun presetInjection(name: String, content: String) = PromptInjection.ModeInjection(
        id = Uuid.random(),
        name = name,
        enabled = true,
        content = content,
        role = MessageRole.SYSTEM,
        position = InjectionPosition.BEFORE_CHARACTER,
    )

    // ---------------------------------------------------------------- L1 + L2

    @Test
    fun keywordTriggeredLoreIsPresentInFinalMessages() {
        val book = lorebook(
            loreEntry(
                name = "龙族设定",
                keywords = listOf("巨龙", "dragon"),
                content = "龙族生活在北境雪山，以冰晶为食。",
            ),
        )
        val out = transformMessages(
            messages = listOf(userMessage("我看到一条巨龙飞过")),
            assistant = assistant(setOf(book.id)),
            modeInjections = emptyList(),
            lorebooks = listOf(book),
        )
        val text = textOf(out)
        println("[L2] final text = ${text.take(300)}")

        assertTrue("命中的世界书条目内容必须出现在最终请求里", text.contains("龙族生活在北境雪山"))
        assertTrue("最终消息数量应大于原始 1 条", out.size > 1)
    }

    // ---------------------------------------------------------------- L3

    @Test
    fun nonTriggeredLoreIsAbsent() {
        val book = lorebook(
            loreEntry(name = "精灵设定", keywords = listOf("精灵"), content = "精灵住在森林。"),
            loreEntry(name = "矮人设定", keywords = listOf("矮人"), content = "矮人住在山洞。"),
        )
        val out = transformMessages(
            messages = listOf(userMessage("我遇到了一个精灵")),
            assistant = assistant(setOf(book.id)),
            modeInjections = emptyList(),
            lorebooks = listOf(book),
        )
        val text = textOf(out)

        assertTrue("命中的精灵条目应出现", text.contains("精灵住在森林"))
        assertFalse("未命中的矮人条目不应出现", text.contains("矮人住在山洞"))
    }

    // ---------------------------------------------------------------- L4

    @Test
    fun presetInjectionAndLoreCoexistInSameRequest() {
        val book = lorebook(
            loreEntry(name = "魔法体系", keywords = listOf("法术"), content = "【世界书】法术消耗魔力。"),
        )
        val preset = presetInjection(name = "写作风格", content = "【预设】请用第二人称叙述。")

        val out = transformMessages(
            messages = listOf(userMessage("我施放了一个法术")),
            assistant = assistant(
                lorebookIds = setOf(book.id),
                modeInjectionIds = setOf(preset.id),
            ),
            modeInjections = listOf(preset),
            lorebooks = listOf(book),
        )
        val text = textOf(out)
        println("[L4] final text = ${text.take(400)}")

        assertTrue("预设注入必须出现", text.contains("【预设】请用第二人称叙述"))
        assertTrue("世界书条目必须同时出现", text.contains("【世界书】法术消耗魔力"))
    }

    // ---------------------------------------------------------------- L5 活性

    @Test
    fun changingInputChangesWhichLoreActivates() {
        val book = lorebook(
            loreEntry(name = "A", keywords = listOf("苹果"), content = "苹果是红色的。"),
            loreEntry(name = "B", keywords = listOf("香蕉"), content = "香蕉是黄色的。"),
        )

        val appleText = textOf(
            transformMessages(
                messages = listOf(userMessage("我想吃苹果")),
                assistant = assistant(setOf(book.id)),
                modeInjections = emptyList(),
                lorebooks = listOf(book),
            ),
        )
        val bananaText = textOf(
            transformMessages(
                messages = listOf(userMessage("我想吃香蕉")),
                assistant = assistant(setOf(book.id)),
                modeInjections = emptyList(),
                lorebooks = listOf(book),
            ),
        )

        println("[L5] apple -> ${appleText.take(200)}")
        println("[L5] banana -> ${bananaText.take(200)}")

        assertTrue("输入苹果应激活苹果条目", appleText.contains("苹果是红色的"))
        assertFalse("输入苹果不应激活香蕉条目", appleText.contains("香蕉是黄色的"))
        assertTrue("输入香蕉应激活香蕉条目", bananaText.contains("香蕉是黄色的"))
        assertFalse("输入香蕉不应激活苹果条目", bananaText.contains("苹果是红色的"))
    }

    // ---------------------------------------------------------------- 常驻条目

    @Test
    fun constantEntryAlwaysPresent() {
        val book = lorebook(
            loreEntry(
                name = "系统规则",
                keywords = emptyList(),
                content = "【常驻】始终遵守安全准则。",
                constant = true,
            ),
        )
        val out = transformMessages(
            messages = listOf(userMessage("随便说点什么")),
            assistant = assistant(setOf(book.id)),
            modeInjections = emptyList(),
            lorebooks = listOf(book),
        )
        assertTrue("常驻条目应无条件注入", textOf(out).contains("【常驻】始终遵守安全准则"))
    }

    // ---------------------------------------------------------------- 位置

    @Test
    fun beforeCharacterEntryGoesBeforeChatHistory() {
        val book = lorebook(
            loreEntry(
                name = "前置设定",
                keywords = listOf("测试"),
                content = "【前置】这条在世界书之前。",
                position = InjectionPosition.BEFORE_CHARACTER,
            ),
        )
        val out = transformMessages(
            messages = listOf(userMessage("测试一下")),
            assistant = assistant(setOf(book.id)),
            modeInjections = emptyList(),
            lorebooks = listOf(book),
        )
        val text = textOf(out)
        val loreIndex = text.indexOf("【前置】这条在世界书之前。")
        val userIndex = text.indexOf("测试一下")

        println("[position] lore@$loreIndex user@$userIndex")
        assertTrue("世界书条目应存在", loreIndex >= 0)
        assertTrue("用户消息应存在", userIndex >= 0)
        assertTrue("BEFORE_CHARACTER 条目应排在用户消息之前", loreIndex < userIndex)
    }

    // ---------------------------------------------------------------- 预算

    @Test
    fun budgetOverflowDropsEntriesInsteadOfInjectingEverything() {
        val entries = (1..40).map { i ->
            loreEntry(
                name = "条目$i",
                keywords = listOf("触发"),
                content = "内容$i：" + "填充".repeat(200),
            )
        }.toTypedArray()
        val bookForBudget = lorebook(*entries)
        val out = transformMessages(
            messages = listOf(userMessage("触发一下")),
            assistant = assistant(setOf(bookForBudget.id)),
            modeInjections = emptyList(),
            lorebooks = listOf(bookForBudget),
            worldInfoBudget = 25,
        )
        val text = textOf(out)
        val injected = (1..40).count { text.contains("内容$it：") }
        println("[budget] injected=$injected / 40")

        assertTrue("预算应限制注入数量（不应 40 条全注入）", injected in 1..39)
    }
}
