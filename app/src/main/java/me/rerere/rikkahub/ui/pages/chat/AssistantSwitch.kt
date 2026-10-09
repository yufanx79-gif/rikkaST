package me.rerere.rikkahub.ui.pages.chat

import kotlin.uuid.Uuid

/**
 * 批次十一 T1（bug C）：切换助手时决定「紧接着要打开哪个会话」。
 *
 * - 打开新会话（`create_new_conversation_on_start = true`，默认）：给一个随机新 id，
 *   由 ChatService 用「当前设置里的助手」建出带预设消息的新会话；
 * - 否则复用该助手最近一次的会话；该助手还没有任何会话时同样随机新建。
 *
 * 抽成纯函数是为了可单测：这条规则原先散落在 ChatDrawer 的 lambda 里且与导航耦合，
 * 出过「助手切了、会话/渲染没切」的静默故障（见 notes/batch11_plan_20260929.md）。
 */
internal fun resolveTargetConversationId(
    createNewConversation: Boolean,
    latestConversationId: Uuid?,
    fallbackNewId: Uuid,
): Uuid = if (createNewConversation || latestConversationId == null) fallbackNewId else latestConversationId
