package me.rerere.rikkahub.data.st.script

/**
 * [今晚 P2] 斜杠命令的**运行时覆写**持有者。
 *
 * ## 为什么需要单独一个对象
 * 下面三条 ST 命令都是「运行时改当前生成行为」，而不是改持久化设置：
 *
 * | 命令 | 语义 | 上游落点 |
 * |---|---|---|
 * | `/start-reply-with <text>` | 下次生成以该文本开头（assistant prefill） | `power-user.js` → `oai_settings.assistant_prefill` |
 * | `/stop-strings <a,b>` | 本次生成的停止序列 | `oai_settings.custom_stopping_strings` |
 * | `/prompt-post-processing <on\|off>` | 预设后处理开关 | `oai_settings.prompt_post_processing` |
 *
 * 我们此前这三条只出现在 `StSlashExecutor` 的**派发名单**里，
 * `ChatSlashHost.executeExtendedCommand` 没有实现分支 → 返回 null →
 * 落到 `runFallbackExtendedCommand` 的 `else -> unnamed` → **把输入原样回显**。
 * 不报错、不崩溃、什么也没做 —— 这是最恶劣的一类「静默假成功」。
 *
 * ## 为什么用 @Volatile 而不是塞进 Settings
 * 1. ST 里这些也是运行时状态（改完立即影响下一次生成，不必落盘）
 * 2. 塞进 Settings 会触发一次 DataStore 写入 + 全 UI 重组，而 `/stop-strings`
 *    在脚本里可能被调用几十次
 * 3. 与既有做法一致：`PromptInjectionTransformer.vectorThreshold` 就是 @Volatile 字段
 *
 * ## 生命周期
 * - 进程内有效，重启清空（与 ST 刷新页面后回到设置值的行为一致）
 * - `/start-reply-with` 与 `/stop-strings` 用 `clear()` 显式复位
 */
object StSlashRuntimeOverrides {

    /** `/start-reply-with`：下一次生成的 assistant prefill；null = 用预设值。 */
    @Volatile
    var assistantPrefill: String? = null

    /**
     * `/stop-strings`：本次生效的停止序列；null = 用预设值。
     * 空 list 也代表「显式清空」（与 null 区分）。
     */
    @Volatile
    var stopStrings: List<String>? = null

    /** `/prompt-post-processing`：预设后处理开关；null = 用预设值。 */
    @Volatile
    var promptPostProcessing: Boolean? = null

    fun reset() {
        assistantPrefill = null
        stopStrings = null
        promptPostProcessing = null
    }

    /** 供日志/诊断用的一句话快照。 */
    fun snapshot(): String = buildString {
        append("prefill=").append(assistantPrefill ?: "-")
        append(" stop=").append(stopStrings?.joinToString(",") ?: "-")
        append(" postProc=").append(promptPostProcessing?.toString() ?: "-")
    }
}
