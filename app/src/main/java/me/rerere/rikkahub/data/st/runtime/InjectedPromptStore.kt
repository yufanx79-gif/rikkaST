package me.rerere.rikkahub.data.st.runtime

import java.util.concurrent.ConcurrentHashMap

/**
 * [v241] JSR `injectPrompts` / `uninjectPrompts` 的宿主运行态存储。
 *
 * 移植自 `D:\rikkaST-refs\C-st-ext\N0VI028__JS-Slash-Runner\src\function\inject.ts`
 * 与 `@types/function/inject.d.ts`：
 * - `injectPrompts(prompts, { once })`：把提示词注册进当前聊天（会话）作用域；
 * - `uninjectPrompts(ids)`：按 id 移除；
 * - `once: true` → 只在「下一次生成」有效（官方在 GENERATION_ENDED / GENERATION_STOPPED 时移除；
 *   rikkaST 在注入进提示词的那个瞬间消费，语义等价于「一次性」；差异见 DIVERGENCE §H.4）。
 *
 * 作用域：按会话 id 隔离（官方 extension_prompts 是按聊天文件的），进程内存、不落盘。
 * 注意：本存储只保存「宿主生成链需要的字段」；JSR 的 `filter`（JS 函数）无法跨语言传递，
 * 由 shim 侧在登记时忽略（官方 SDK 里本卡片的 filter 恒为 `() => true`，影响为零）。
 */
object InjectedPromptStore {

    /** 单条注入提示词（字段名对齐 JSR InjectionPrompt） */
    data class InjectedPrompt(
        val id: String,
        /** `in_chat`（插入聊天并发送）或 `none`（不发送，仅按 [shouldScan] 参与世界书扫描） */
        val position: String,
        /** in_chat 时的注入深度（0 = 聊天最末尾） */
        val depth: Int,
        /** system / user / assistant */
        val role: String,
        val content: String,
        /** 是否加入世界书扫描文本 */
        val shouldScan: Boolean,
        /** 是否只生效一次 */
        val once: Boolean,
    )

    private val byConversation = ConcurrentHashMap<String, ConcurrentHashMap<String, InjectedPrompt>>()

    private fun scope(conversationId: String?): ConcurrentHashMap<String, InjectedPrompt> =
        byConversation.computeIfAbsent(conversationId ?: GLOBAL_SCOPE) { ConcurrentHashMap() }

    /** 无会话时的保留作用域（与宏变量一致的口径） */
    const val GLOBAL_SCOPE = "__st_injection_global__"

    /** 登记/覆盖一批注入（同 id 覆盖，对齐 ST setExtensionPrompt 的同 key 覆盖语义） */
    fun inject(conversationId: String?, prompts: List<InjectedPrompt>) {
        if (prompts.isEmpty()) return
        val map = scope(conversationId)
        prompts.forEach { map[it.id] = it }
    }

    /** 按 id 移除 */
    fun uninject(conversationId: String?, ids: List<String>) {
        if (ids.isEmpty()) return
        val map = scope(conversationId)
        ids.forEach { map.remove(it) }
    }

    /** 当前注入快照（按登记顺序不保证；排序交给消费方） */
    fun snapshot(conversationId: String?): List<InjectedPrompt> =
        scope(conversationId).values.toList()

    /** 消费 once 注入（注入进提示词后调用） */
    fun consumeOnce(conversationId: String?, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val map = scope(conversationId)
        ids.forEach { map.remove(it) }
    }

    /** 清空某会话（切换会话/测试用） */
    fun clear(conversationId: String?) {
        scope(conversationId).clear()
    }

    /** 测试用：清空全部 */
    fun clearAll() {
        byConversation.clear()
    }
}
