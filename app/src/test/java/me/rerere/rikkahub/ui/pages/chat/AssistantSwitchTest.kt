package me.rerere.rikkahub.ui.pages.chat

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批次十一 T1（bug C）回归测试：切助手时「要打开哪个会话」的判定规则。
 * 这条规则原本埋在 ChatDrawer 的导航 lambda 里，坏掉时表现为「助手切了、对话还在旧的」。
 */
class AssistantSwitchTest {
    private val existing = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val fresh = Uuid.parse("22222222-2222-2222-2222-222222222222")

    @Test
    fun opensNewConversationWhenPreferenceEnabled() {
        assertEquals(fresh, resolveTargetConversationId(true, existing, fresh))
        assertEquals(fresh, resolveTargetConversationId(true, null, fresh))
    }

    @Test
    fun reusesLatestConversationWhenPreferenceDisabled() {
        assertEquals(existing, resolveTargetConversationId(false, existing, fresh))
    }

    @Test
    fun fallsBackToNewConversationWhenAssistantHasNoConversation() {
        assertEquals(fresh, resolveTargetConversationId(false, null, fresh))
    }
}
