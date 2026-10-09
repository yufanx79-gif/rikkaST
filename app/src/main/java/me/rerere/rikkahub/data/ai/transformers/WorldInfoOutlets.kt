package me.rerere.rikkahub.data.ai.transformers

/**
 * 世界书 outlet（官方 position=7 + extensions.outlet_name）运行时存储。
 *
 * 对齐 SillyTavern 语义（world-info.js setExtensionPrompt(CUSTOM_WI_OUTLET(...)) + macros.js getOutletPrompt）：
 * - 世界书扫描时，被激活的 outlet 条目内容按 outlet_name 汇聚（多个同名条目以 "\n" 连接），
 *   写入运行时存储；outlet 内容自身不注入到提示词的任何位置；
 * - 仅当 `{{outlet::name}}` 宏被求值时，才读取对应 name 的内容展开（未激活/未配置时为空串）；
 * - 快照按会话隔离，避免并发会话互相串数据。
 *
 * 生命周期：每次生成流程由 [PromptInjectionTransformer] 扫描后整体覆写（含写空 = 清除旧值），
 * 随后宏展开（PlaceholderTransformer / StRegexInputTransformer）读取本次快照。
 */
object WorldInfoOutlets {

    private const val FALLBACK_KEY = "__no_conversation__"

    private val lock = Any()
    private val byConversation = HashMap<String, Map<String, String>>()

    /**
     * 发布某会话本次扫描得到的 outlet 集合（整体覆盖旧值）。
     * outlets 为空表示本次扫描没有激活任何 outlet 条目 → 清除旧值，避免陈旧数据被宏读到。
     */
    fun publish(conversationId: String?, outlets: Map<String, String>) {
        val key = conversationId ?: FALLBACK_KEY
        synchronized(lock) {
            if (outlets.isEmpty()) {
                byConversation.remove(key)
            } else {
                byConversation[key] = LinkedHashMap(outlets)
            }
        }
    }

    /** 读取某会话当前 outlet 快照（无数据时返回空表）。 */
    fun snapshot(conversationId: String?): Map<String, String> = synchronized(lock) {
        byConversation[conversationId ?: FALLBACK_KEY] ?: emptyMap()
    }

    /** 读取单个 outlet（ST getOutletPrompt 语义：不存在时为空串）。key 先原文、失败再 trim 兜底。 */
    fun get(conversationId: String?, key: String): String {
        val snap = snapshot(conversationId)
        snap[key]?.let { return it }
        val trimmed = key.trim()
        return if (trimmed != key) snap[trimmed] ?: "" else ""
    }
}
