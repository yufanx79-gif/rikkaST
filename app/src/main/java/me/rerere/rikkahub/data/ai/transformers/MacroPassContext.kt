/*
 * 性能优化说明（本轮 P3）
 * ============================================================
 * 真机日志（用户提供，2026-11-18）：
 *   [transformer] slow #4 PlaceholderTransformer 748ms msgs=8
 *
 * 根因：`transform()` 对**每条消息的每个 Text part** 都调用一次
 * `replacePlaceholders()`，而后者内部每次都执行：
 *   1. `parseActiveStPresetConfig(ctx.settings.tavernPresets, ...)`
 *      → `ExportSerializer.DefaultJson.parseToJsonElement(tavernPresetsJson)`
 *      → 解析**整个预设 JSON**（Meowssiah 2.0.11 = 543 KB）
 *   2. `enabledPresetGroups(...)` 遍历
 *   3. `PlaceholderCtx` 构造
 *
 * 同一次请求里这些结果完全相同，却重复了 N 次（N = 消息数 × text part 数）。
 * 8 条消息 → 8 次 543KB JSON 解析 ≈ 748ms，与实测完全吻合。
 *
 * 修复：把「与消息内容无关」的前置计算提到 `transform()` 里做一次，
 * 通过 `MacroPassContext` 传给每个 part。
 *
 * 正确性：`parseActiveStPresetConfig` 的输入是 `ctx.settings.tavernPresets`
 * 与 `enabledPresetGroups(ctx.assistant, ctx.settings, ...)`，二者在同一次
 * `transform()` 内不会变化；`PlaceholderCtx` 里的 `messages` 也固定为本次
 * 入参。因此复用是安全的。
 */
package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.datastore.SettingsStore

/**
 * 一次 `transform()` 内可复用的宏替换前置结果。
 *
 * 这些字段只依赖 `TransformerContext` 与入参 `messages`，
 * 不依赖被替换的那一段文本，所以可以在消息循环外只算一次。
 */
internal class MacroPassContext(
    val placeholderCtx: PlaceholderCtx,
    /** SPreset `MacroNest` 的嵌套展开轮数（已 coerce 到 1..5）。 */
    val nestPasses: Int,
)
