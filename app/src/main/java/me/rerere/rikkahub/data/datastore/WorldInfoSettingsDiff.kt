package me.rerere.rikkahub.data.datastore

/**
 * [v227 N1] 世界书设置是否发生变化（纯函数，可单测）。
 *
 * ## 为什么需要它
 * ST 真源 `world-info.js:5842` / `:6228` 在**世界书设置**变化时发 `WORLDINFO_SETTINGS_UPDATED`
 * （事件名 `worldinfo_settings_updated`，`events.js:41`，**无载荷**）。
 * 订阅方真实存在：ST-Prompt-Template 的 `PromptManager.js:835`
 * `eventSource.on(event_types.WORLDINFO_SETTINGS_UPDATED, () => this.renderDebounced())`
 * —— 世界书设置一变就重渲染提示词面板。宿主不发这个事件，扩展的面板就永远是旧数据。
 *
 * ## 差分门控
 * `SettingsStore.update()` 会重写 ~73 个 key，任何一次设置写入都会走到这里；无脑发事件会让扩展
 * 在每次无关写入时重跑一遍重渲染。所以先比较这 10 个世界书设置字段，只有真的变了才发。
 *
 * ⚠️ 只比「设置」（ST 的 WORLDINFO_SETTINGS_UPDATED 语义），**不比 `lorebooks` 条目本身**
 * （条目变化对应的是 `WORLDINFO_ENTRIES_LOADED`，需要世界书加载链路的挂点，登记在 v228）。
 */
internal fun worldInfoSettingsChanged(previous: Settings, next: Settings): Boolean =
    previous.worldInfoBudget != next.worldInfoBudget ||
        previous.worldInfoBudgetCap != next.worldInfoBudgetCap ||
        previous.worldInfoMinActivations != next.worldInfoMinActivations ||
        previous.worldInfoMinActivationsDepthMax != next.worldInfoMinActivationsDepthMax ||
        previous.worldInfoRecursive != next.worldInfoRecursive ||
        previous.worldInfoMaxRecursionSteps != next.worldInfoMaxRecursionSteps ||
        previous.worldInfoDepth != next.worldInfoDepth ||
        previous.worldInfoCharacterStrategy != next.worldInfoCharacterStrategy ||
        previous.worldInfoOverflowAlert != next.worldInfoOverflowAlert ||
        previous.worldInfoUseGroupScoring != next.worldInfoUseGroupScoring
