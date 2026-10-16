# 关键架构与工程决策记录（DECISIONS）

## DEC-01：保持 RikkaHub 2.5.6 原生底座与身份不变
- **背景**：部分早期兼容尝试曾直接覆盖原生设置或破坏原生工具链。
- **决策**：保留 `me.rerere.rikkahub` 包名、Room 数据库迁移（1 -> 12）、原生 Provider/MCP/Skills/Memory/Search/TTS 体系，所有 SillyTavern 兼容能力均作为增量层或可选回退层接入。

## DEC-02：SillyTavern 预设参数采用“助手显式设置优先、预设兜底、CustomBody 合入”策略
- **背景**：用户既可能在助手详情页手动调整 `temperature` / `topP` / `maxTokens`，也可能直接启用酒馆预设期望采样参数、停止词和 `logit_bias` 生效。
- **决策**：在 `GenerationLoop.kt` 中：
  - `temperature`、`topP`、`maxTokens`：优先使用 `assistant` 显式配置，为空时回退到 `activeStPreset.temperature` / `topP` / `maxTokens`；
  - `customStoppingStrings`、`logitBias`、`frequencyPenalty`、`presencePenalty`、`repetitionPenalty`、`topK`、`minP`、`topA`、`seed`：通过 `buildPresetMergedCustomBodies` 注入 `CustomBody`（若用户已在助手 `customBodies` 中显式配置同名键，则以用户显式配置为准，不覆盖）。

## DEC-03：`/popup`、`/input`、`/buttons` 模态交互与后台脚本非阻塞兼容
- **背景**：在聊天页面前台执行 STscript 时，用户需要真实的弹窗确认、文本输入和按钮选择；但在单元测试或无 UI 后台场景下，阻塞等待会导致挂起。
- **决策**：通过 `TavernRuntimeManager.isSlashModalUiAttached` 状态位与 `withTimeoutOrNull(120_000L)` 控制：当 `ChatPage` 处于活跃挂载状态且非 UI 线程调用时，弹出 Compose `AlertDialog` 并挂起等待用户选择；当无 UI 挂载或在单元测试中使用默认 `StSlashHost` 时，自动回退默认值，兼顾真实交互与无头测试。

## DEC-04：`MessageHtmlBlock` 采用 `PanelMountState` 区分无脚本原地更新与带脚本整页重载
- **背景**：Previously, every HTML change triggered a full `panel.html` reload (`reloadGeneration++`), causing visible flicker on streaming or variable-driven status bars that only use HTML+CSS. Conversely, cards with `<script>` tags rely on fresh `window`/`DOMContentLoaded` execution.
- **决策**：引入可单测的纯状态机 `PanelMountState`：首次加载或包含 `<script` 的更新返回 `RELOAD_DOCUMENT`（整页重载，隔离全局 JS 状态并触发 `DOMContentLoaded`）；不含 `<script` 的后续 HTML/CSS 更新返回 `REMOUINT_IN_PLACE`（直接调用 `window.__rikkaPanel.mountCard()` 原地更新 DOM 并重新测量高度）。
