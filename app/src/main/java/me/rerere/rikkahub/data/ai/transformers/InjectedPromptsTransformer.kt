package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.runtime.InjectedPromptStore

/** scan-only 注入消息的内部前缀：参与世界书扫描，发送前整条丢弃 */
internal const val JSR_INJECT_SCAN_MARKER = "[Rikka JSR Inject Scan]"

/** in_chat 注入消息的内部前缀：发送前只剥掉前缀行，正文保留 */
internal const val JSR_INJECT_CHAT_MARKER = "[Rikka JSR Inject]"

/**
 * [v241] JSR `injectPrompts` 注入器（宿主侧）。
 *
 * 语义对齐 `N0VI028/JS-Slash-Runner src/function/inject.ts`：
 * - `position = 'in_chat'`：按 `depth` 插入聊天（0 = 最末尾），以 `role` 发送给模型；
 * - `position = 'none'`：**不发送**；`should_scan = true` 时内容参与世界书关键词扫描
 *   （官方 setExtensionPrompt 的 scan 标记），由 [InjectedPromptCleanupTransformer] 在发送前整条丢弃；
 * - `once = true`：注入进提示词后即从 [InjectedPromptStore] 消费（官方在 GENERATION_ENDED/STOPPED 移除，
 *   对「只影响下一次生成」的实际效果等价；差异登记 DIVERGENCE §H.4）；
 * - 每次生成都会重新读取 store，因此脚本可以在生成前动态注入/撤销。
 *
 * 两条注入消息都带内部前缀，被 [UIMessage.isInjectedBlock] 识别为「非真实聊天消息」：
 * 宏/占位符（`{{lastMessage}}` 等）、导演备注计数、输入正则都会跳过它们。
 */
object InjectedPromptsTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyInjectedPrompts(ctx.conversationId?.toString(), messages)

    /** 纯函数核心（无 Android 依赖，JVM 单测直接调用） */
    internal fun applyInjectedPrompts(
        conversationId: String?,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (conversationId == null) return messages
        val prompts = InjectedPromptStore.snapshot(conversationId)
        if (prompts.isEmpty()) return messages

        var result = messages
        val consumed = mutableListOf<String>()
        for (prompt in prompts.sortedBy { it.id }) {
            val content = prompt.content.trim()
            if (content.isEmpty()) {
                consumed += prompt.id
                continue
            }
            val scanOnly = prompt.position == "none"
            if (scanOnly && !prompt.shouldScan) {
                // 不发送也不扫描 → 无效果（对齐 ST position=-1 且 scan=false）
                if (prompt.once) consumed += prompt.id
                continue
            }
            // scan-only 必须进入世界书扫描文本；扫描只取非 SYSTEM 消息（见 PromptInjectionTransformer），
            // 因此用 USER 角色承载，再由清理器整条丢弃。
            val role = if (scanOnly) {
                MessageRole.USER
            } else {
                when (prompt.role.lowercase()) {
                    "user" -> MessageRole.USER
                    "assistant" -> MessageRole.ASSISTANT
                    else -> MessageRole.SYSTEM
                }
            }
            val marker = if (scanOnly) JSR_INJECT_SCAN_MARKER else JSR_INJECT_CHAT_MARKER
            val message = UIMessage(
                role = role,
                parts = listOf(UIMessagePart.Text("$marker\n$content")),
            )
            result = if (scanOnly || prompt.depth <= 0) {
                result + message
            } else {
                val index = (result.size - prompt.depth).coerceIn(0, result.size)
                result.take(index) + message + result.drop(index)
            }
            if (prompt.once) consumed += prompt.id
        }
        if (consumed.isNotEmpty()) {
            InjectedPromptStore.consumeOnce(conversationId, consumed)
        }
        return result
    }
}

/**
 * [v241] JSR 注入的发送前清理器（应放在内置输入链末尾，早于 Template/Knowledge/Instruct 等包装器）：
 * - scan-only 注入 → 整条丢弃（只服务世界书扫描）；
 * - in_chat 注入 → 剥掉内部前缀行，正文保留（对齐 ST 扩展提示词的纯文本语义）。
 */
object InjectedPromptCleanupTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = cleanupInjectedPrompts(messages)

    /** 纯函数核心（无 Android 依赖，JVM 单测直接调用） */
    internal fun cleanupInjectedPrompts(messages: List<UIMessage>): List<UIMessage> = messages.mapNotNull { message ->
        val text = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
        when {
            text.startsWith(JSR_INJECT_SCAN_MARKER) -> null

            text.startsWith(JSR_INJECT_CHAT_MARKER) -> {
                val stripped = text.removePrefix(JSR_INJECT_CHAT_MARKER).removePrefix("\n")
                if (stripped.isBlank()) {
                    null
                } else {
                    message.copy(
                        parts = message.parts.map { part ->
                            if (part is UIMessagePart.Text && part.text.startsWith(JSR_INJECT_CHAT_MARKER)) {
                                part.copy(text = stripped)
                            } else {
                                part
                            }
                        },
                    )
                }
            }

            else -> message
        }
    }
}
