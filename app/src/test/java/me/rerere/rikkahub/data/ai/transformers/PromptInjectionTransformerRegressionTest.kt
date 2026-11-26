package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * 回归守卫：自定义 12-Marker 顺序（unified prompt order）下的锚点行为。
 *
 * 说明：真正的槽位排序由 GenerationLoop 的 UnifiedPromptOrderEngine 完成；
 * transformMessages 只负责把相对区 / AT_DEPTH / Author's Note 锚定到
 * main 与聊天记录区的边界上。因此这里断言的是「顺序锚点」而不是
 * 「main 与 chatHistory 互换后的整体重排」。
 */
class PromptInjectionTransformerRegressionTest {

    @Test
    fun `custom marker order keeps AT_DEPTH and AuthorNote anchored to chat tail`() {
        val depthId = Uuid.random()
        val depthInj = PromptInjection.ModeInjection(
            id = depthId,
            name = "Depth0",
            enabled = true,
            priority = 10,
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 0,
            role = MessageRole.SYSTEM,
            content = "DEPTH_ZERO_CONTENT",
        )
        val assistant = Assistant(modeInjectionIds = setOf(depthId))
        val messages = listOf(
            UIMessage.user("USER_TURN_1"),
            UIMessage.assistant("ASSISTANT_TURN_1"),
            UIMessage.user("USER_TURN_2"),
            UIMessage.system("MAIN_SYSTEM_PROMPT"),
        )

        val result = transformMessages(
            messages = messages,
            assistant = assistant,
            modeInjections = listOf(depthInj),
            lorebooks = emptyList(),
            orderedMarkers = listOf("chatHistory", "main"),
        )

        val texts = result.map { it.toText() }
        val user2Idx = texts.indexOfFirst { (it as String).contains("USER_TURN_2") }
        val depth0Idx = texts.indexOfFirst { (it as String).contains("DEPTH_ZERO_CONTENT") }
        val mainIdx = texts.indexOfFirst { (it as String).contains("MAIN_SYSTEM_PROMPT") }

        assertTrue("Expected chat/depth/main present: $texts", user2Idx >= 0 && depth0Idx >= 0 && mainIdx >= 0)
        // chatHistory 被拖到 main 之前时，AT_DEPTH depth=0 仍必须锚定在「聊天区最后一条」之后，
        // 而不是整条消息列表的末尾（否则会把注入甩到 main 后面）。
        assertTrue("AT_DEPTH depth=0 should appear after USER_TURN_2: $texts", user2Idx < depth0Idx)
    }

    @Test
    fun `custom marker order anchors system prompt injections around real main message`() {
        val beforeId = Uuid.random()
        val afterId = Uuid.random()
        val beforeInj = PromptInjection.ModeInjection(
            id = beforeId,
            name = "BeforeMain",
            enabled = true,
            priority = 10,
            position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            role = MessageRole.SYSTEM,
            content = "BEFORE_MAIN_MARKER",
        )
        val afterInj = PromptInjection.ModeInjection(
            id = afterId,
            name = "AfterMain",
            enabled = true,
            priority = 10,
            position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            role = MessageRole.SYSTEM,
            content = "AFTER_MAIN_MARKER",
        )
        val assistant = Assistant(modeInjectionIds = setOf(beforeId, afterId))
        val cardMsg = UIMessage.system("CHAR_DESC").copy(
            annotations = listOf(UIMessageAnnotation.CharacterCardData),
        )
        val mainMsg = UIMessage.system("REAL_MAIN_PROMPT")
        val userMsg = UIMessage.user("HELLO")

        val result = transformMessages(
            messages = listOf(cardMsg, mainMsg, userMsg),
            assistant = assistant,
            modeInjections = listOf(beforeInj, afterInj),
            lorebooks = emptyList(),
            orderedMarkers = listOf("charDescription", "main", "chatHistory"),
        )

        val texts = result.map { it.toText() }
        val cardIdx = texts.indexOfFirst { (it as String).contains("CHAR_DESC") }
        val beforeIdx = texts.indexOfFirst { (it as String).contains("BEFORE_MAIN_MARKER") }
        val mainIdx = texts.indexOfFirst { (it as String).contains("REAL_MAIN_PROMPT") }
        val afterIdx = texts.indexOfFirst { (it as String).contains("AFTER_MAIN_MARKER") }

        // 关键回归点：BEFORE/AFTER_SYSTEM_PROMPT 必须锚定在真正的 main 上，
        // 而不是被角色卡（同样带 SYSTEM 注解）骗走（旧实现会锚到 CHAR_DESC 上）。
        assertTrue("BEFORE_MAIN_MARKER should share/merge with REAL_MAIN_PROMPT: $texts", beforeIdx == mainIdx)
        assertTrue("AFTER_MAIN_MARKER should share/merge with REAL_MAIN_PROMPT: $texts", afterIdx == mainIdx)
        assertTrue("Card should stay before main: $texts", cardIdx in 0 until mainIdx)
    }

    @Test
    fun `mixed preset and standalone injections sort without TimSort exception`() {
        val mixed = (0 until 40).map { i ->
            PromptInjection.ModeInjection(
                id = Uuid.random(),
                name = "Item$i",
                enabled = true,
                priority = (i * 7) % 13,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                role = MessageRole.SYSTEM,
                content = "C$i",
                presetGroup = if (i % 2 == 0) "PresetA" else null,
            )
        }
        val assistant = Assistant(modeInjectionIds = mixed.map { it.id }.toSet())
        val result = transformMessages(
            messages = listOf(UIMessage.system("MAIN"), UIMessage.user("HI")),
            assistant = assistant,
            modeInjections = mixed,
            lorebooks = emptyList(),
        )
        assertTrue(result.size >= 2)
        // 预设条目（桶 0，priority 升序）与独立条目（桶 1）都会进入结果，且不抛 TimSort 异常
        val combined = result.joinToString("\n") { it.toText() }
        assertTrue(combined.contains("C0"))
        assertTrue(combined.contains("C1"))
    }
}
