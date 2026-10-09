package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager

/**
 * [v228 S2] 「设置变更 -> 应发哪些 ST 事件」的纯函数（可单测）。
 *
 * ## 为什么要有它
 * v227 N1 只在 `SettingsStore.update()`（全量写入口）里做了差分门控并发射 `SETTINGS_UPDATED`，
 * 而 v227 D4 新增的**窄写回** `patchKey()`（`PreferencesStore.kt`，消息样式页 4 个滑块走它）
 * **直接漏了口子**：值改了、事件不发 → 扩展（ST-Prompt-Template `ui.ts:176` / JSR
 * `use_check_enablement_popup.ts:17`）拿不到 `SETTINGS_UPDATED`，面板数据永远是旧的。
 *
 * ## 判据（必须唯一、且可单测）
 * - `previous == next`：什么都没变 -> **不发**（防止无脑刷屏让扩展重渲染）；
 * - 变了：
 *   - 一定发 `settings_updated`（ST `events.js:30`，真源 `script.js:8110` 无载荷）；
 *   - 若这 10 个**世界书设置**字段变了，再补一条 `worldinfo_settings_updated`
 *     （ST `events.js:41`；订阅方 ST-PT `PromptManager.js:835`），判据见 [worldInfoSettingsChanged]。
 *
 * ⚠️ 这里只判「设置」；世界书**条目**变化对应 `WORLDINFO_ENTRIES_LOADED`（v228 S5，挂在扫描链路）。
 */
internal fun settingsEventsFor(previous: Settings, next: Settings): List<String> {
    if (previous == next) return emptyList()
    val events = mutableListOf(TavernRuntimeManager.EVENT_SETTINGS_UPDATED)
    if (worldInfoSettingsChanged(previous, next)) {
        events += TavernRuntimeManager.EVENT_WORLDINFO_SETTINGS_UPDATED
    }
    return events
}
