# 上游消息结构预览与自由拖拽调序面板 — 实施计划

**Goal:** 在 rikkaST 中落地「上游消息结构预览与自由拖拽调序面板」，打通底层 12 个内置 Marker 与相对区真自由重排、预设双存储完整同步、只读 Dry-Run 预览组装与 4/5 屏拖拽调序 UI。
**Spec:** `docs/superpowers/specs/2026-11-25-upstream-prompt-preview-panel-design.md`

## Tasks
- [x] Task 1: 实现 `PromptPreviewModels.kt`（`PromptSlotOrigin`、`PromptPreviewBlock`、`ActiveProcessorBadge`、`PromptPreviewSnapshot`、`PlainBodyPreviewExtractor`）
- [x] Task 2: 实现 `UnifiedPromptOrderEngine.kt` 并接入 `GenerationLoop.kt` 实现全部 12 个内置 Marker 真自由重排（零卡死纯函数）
- [x] Task 3: 实现只读无副作用 `PromptPreviewAssembler.kt`（8 类处理器检测 + 折叠/展开聊天记录与 `depth=0` 待发标尺）
- [x] Task 4: 实现完整双写 `PromptOrderSyncWriter.kt`（`dirtyKeys` 保护 `depth > 30`、`Settings.promptInjections` + `Settings.tavernPresets` JSON 同步）
- [x] Task 5: 实现 `PromptStructurePreviewDialog.kt` 并挂载至 `ChatInput.kt`（思考强度旁）与 `ChatPage.kt`（含底部取消/保存按钮）
- [x] Task 6: 编写 4 套单元测试与回归守卫测试，执行独立对抗式 Code Review 清零全部问题，并打包最终无误 zip 与完整变更清单
