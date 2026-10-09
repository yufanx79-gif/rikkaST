package me.rerere.rikkahub.ui.components.frosted

import me.rerere.rikkahub.data.datastore.MessageBubbleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v227 D5] 气泡容器判据回归测试。
 *
 * 判据必须**唯一**：聊天页（ChatMessage.kt）与消息样式预览区共用 [bubbleContainerKind]。
 * 旧实现两处各自写 if（聊天页 `useStyledBubble` / 预览区无条件 FrostedBubbleSurface）—— 这就是
 * 主人 v226 真机反馈「预览框里只有用户消息和助手消息，没有气泡框」的结构性来源。
 */
class MessageBubbleContainerKindTest {

    private fun kind(style: MessageBubbleStyle, isUser: Boolean, showAssistantBubble: Boolean = true) =
        bubbleContainerKind(style, isUser, showAssistantBubble)

    @Test
    fun frostedStyleUsesFrostedSurfaceForBothRoles() {
        assertEquals(BubbleContainerKind.STYLED, kind(MessageBubbleStyle.FROSTED, isUser = true))
        assertEquals(BubbleContainerKind.STYLED, kind(MessageBubbleStyle.FROSTED, isUser = false))
    }

    @Test
    fun solidStyleUsesFrostedSurfaceForBothRoles() {
        assertEquals(BubbleContainerKind.STYLED, kind(MessageBubbleStyle.SOLID, isUser = true))
        assertEquals(BubbleContainerKind.STYLED, kind(MessageBubbleStyle.SOLID, isUser = false))
    }

    @Test
    fun defaultStyleUserBubbleUsesLegacySurface() {
        assertEquals(BubbleContainerKind.LEGACY_SURFACE, kind(MessageBubbleStyle.DEFAULT, isUser = true))
    }

    @Test
    fun defaultStyleAssistantBubbleUsesLegacySurfaceWhenSwitchIsOn() {
        assertEquals(
            BubbleContainerKind.LEGACY_SURFACE,
            kind(MessageBubbleStyle.DEFAULT, isUser = false, showAssistantBubble = true),
        )
    }

    @Test
    fun defaultStyleAssistantBubbleIsBareWhenSwitchIsOff() {
        assertEquals(
            BubbleContainerKind.BARE,
            kind(MessageBubbleStyle.DEFAULT, isUser = false, showAssistantBubble = false),
        )
    }

    @Test
    fun userBubbleIsNeverBareEvenWhenAssistantSwitchIsOff() {
        // 回归防线：`showAssistantBubble` 只作用于助手气泡，不许把用户气泡也变成裸文本。
        assertEquals(
            BubbleContainerKind.LEGACY_SURFACE,
            kind(MessageBubbleStyle.DEFAULT, isUser = true, showAssistantBubble = false),
        )
    }

    @Test
    fun styledBubbleWinsOverTheLegacyAssistantSwitch() {
        // 回归防线（v222 R4 语义）：一旦选了毛玻璃 / 纯色，助手气泡**必须**画出来，
        // 不受 v221 旧开关 `showAssistantBubble`（默认 false）影响。
        assertEquals(
            BubbleContainerKind.STYLED,
            kind(MessageBubbleStyle.FROSTED, isUser = false, showAssistantBubble = false),
        )
        assertEquals(
            BubbleContainerKind.STYLED,
            kind(MessageBubbleStyle.SOLID, isUser = false, showAssistantBubble = false),
        )
    }

    @Test
    fun wrapContentOnlyAppliesToAssistantBubbles() {
        // [v227 D5 复审修复] v226 对用户气泡从不传 wrapContent（默认 false）——用户气泡永远不贴合，
        // 否则「助手气泡贴合内容」开关打开时用户气泡会跟着收缩宽度（行为回归）。
        assertFalse(bubbleWrapContent(requested = true, isUser = true))
        assertTrue(bubbleWrapContent(requested = true, isUser = false))
        assertFalse(bubbleWrapContent(requested = false, isUser = false))
        assertFalse(bubbleWrapContent(requested = false, isUser = true))
    }

    @Test
    fun onlyDefaultStyleCanBeBare() {
        // 穷举：BARE 只可能出现在 DEFAULT + 助手 + 开关关闭 这一种组合。
        MessageBubbleStyle.entries.forEach { style ->
            listOf(true, false).forEach { isUser ->
                listOf(true, false).forEach { show ->
                    val k = kind(style, isUser, show)
                    if (k == BubbleContainerKind.BARE) {
                        assertEquals(MessageBubbleStyle.DEFAULT, style)
                        assertEquals(false, isUser)
                        assertEquals(false, show)
                    }
                }
            }
        }
    }
}
