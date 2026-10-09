package me.rerere.rikkahub.data.st.expressions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * 表情立绘运行态：messageId 级分类缓存 + 每对话当前选中的表情。
 *
 * 对齐 SillyTavern `extensions/expressions/index.js`：
 * - 分类结果按「消息」缓存（官方 `lastMessage` / `lastCharacter` 去重：同一条消息不重复调用分类）；
 * - 流式期间节流（官方 `STREAMING_UPDATE_INTERVAL = 10000`，index.js:41）：
 *   同一对话两次分类调用间隔 < [CLASSIFY_INTERVAL_MS] 时跳过本次分类；
 * - rikkaST 只在「生成完成」时分类（见 ui 的 CharacterExpressionHost），
 *   因此不会每 token 触发；节流额外防护连续重生成/快速连发。
 *
 * 纯 JVM 实现（无 Android 依赖），节流用可注入时钟，便于单测。
 */
object ExpressionStore {

    /** 每个对话当前展示的立绘（messageId + label） */
    data class Selection(val messageId: String, val label: String)

    /** 官方 `STREAMING_UPDATE_INTERVAL`（毫秒） */
    const val CLASSIFY_INTERVAL_MS: Long = 10_000L

    /** 分类缓存条数上限（超出后整体清空重建，对齐官方 spriteCache 的粗粒度 LRU 语义） */
    const val MAX_CACHE_ENTRIES: Int = 512

    private val labelCache = ConcurrentHashMap<String, String>()
    private val lastClassifyAt = ConcurrentHashMap<String, Long>()
    private val _selections = MutableStateFlow<Map<String, Selection>>(emptyMap())

    /** conversationId → 当前立绘；供 Compose 直接 collect */
    val selections: StateFlow<Map<String, Selection>> = _selections.asStateFlow()

    // ==================== messageId 分类缓存 ====================

    /** 读取某条消息已缓存的分类结果（无缓存返回 null） */
    fun cachedLabel(messageId: String): String? = labelCache[messageId]

    /** 写入某条消息的分类结果 */
    fun putLabel(messageId: String, label: String) {
        if (labelCache.size >= MAX_CACHE_ENTRIES) labelCache.clear()
        labelCache[messageId] = label
    }

    /** 测试/回滚用：清空全部运行态 */
    fun resetForTest() {
        labelCache.clear()
        lastClassifyAt.clear()
        _selections.value = emptyMap()
    }

    // ==================== 分类节流（官方 10s 间隔） ====================

    /**
     * 是否应跳过本次分类（同一对话距上次分类 < [CLASSIFY_INTERVAL_MS]）。
     * @param nowMillis 可注入时钟（默认系统时间）
     */
    fun shouldThrottleClassify(
        conversationId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val last = lastClassifyAt[conversationId] ?: return false
        return nowMillis - last < CLASSIFY_INTERVAL_MS
    }

    /** 记录一次实际发生的分类调用时间 */
    fun markClassified(
        conversationId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        lastClassifyAt[conversationId] = nowMillis
    }

    // ==================== 当前立绘选择 ====================

    /** 选中某条消息的分类结果作为当前立绘（消息/标签未变时不产生新事件） */
    fun select(conversationId: String, messageId: String, label: String) {
        val selection = Selection(messageId, label)
        _selections.update { current ->
            if (current[conversationId] == selection) current else current + (conversationId to selection)
        }
    }

    /** 清掉某对话的展示态（不清理 messageId 缓存：同一消息重新进入时可直接复用） */
    fun clearConversation(conversationId: String) {
        _selections.update { it - conversationId }
    }
}
