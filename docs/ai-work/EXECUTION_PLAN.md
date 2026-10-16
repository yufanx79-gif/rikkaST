# rikkaST 2.5.6 源码修复与交付执行计划（已完成静态审查）

## 1. 基线校验与执行约束
- **唯一源码基线**：`rikkaST Gemini 自信交付.zip`
  - SHA-256：`562d064a27bd36ec09e8fe3c88f63a88d9fd8e78d664de290bf6f04191b1eec5`（与 `BASELINE_AND_SCOPE_MANIFEST_CN.md` 严格一致）
- **官方兼容真源**：`SillyTavern-1.18.0.zip`
  - SHA-256：`9ff16405d8df12679049f238292cb313e6c59676bc268fb639e676bc268fb639e1db1e8d74fdc2`（注：完整 SHA-256 见 `EVIDENCE/input_sha256.txt`：`9ff16405d8df12679049f238292cb313e6c59676bc268fb639e1db1e8d74fdc2`）
- **工程资料包**：`RikkaST_Complete_AI_Engineering_Package_2026-10-15.zip`
  - SHA-256：`16317e0375c0f48c16ff4d06e3559cfeb5b2af18c5ed3e0129339f695177c520`
- **不编译约束（NO BUILD）**：严格遵照用户指令，未执行任何 `gradlew`、`assemble*`、`compile*Kotlin`、`testDebugUnitTest` 或 `lint` 任务；所有验证均通过非 Gradle 静态分析（Kotlin 词法/语法状态机扫描、符号/签名核对、Node.js `--check`、JSON/XML 解析器校验）完成。当前状态为**已完成静态审查，待本机编译验收**。

## 2. 分片实施里程碑（Slices A ~ F）
- **Slice A（P0 编译错误历史回归门禁）**：逐项定位并修复 `STATIC_BASELINE_SPOT_CHECK_CN.md` 中的 12 个 Kotlin 阻塞点（`KOT-01` ~ `KOT-12`），并复核 Round 1 / Round 2 历史回归项（`B1`、`C1` ~ `C5`）。
- **Slice B（数据流闭环）**：
  - `DF-01`：修复 `StArchiveImporter` 导入 `.charx` / `.byaf` 时的后缀保留问题，并在 `AssistantImporter.parseCharxBytes` 与 `TavernRuntimeManager.interceptAsset` 中打通 `embeded://` / `embedded://` / `ccdefault:` 资产落盘与 URL/DataURI 重写。
  - `DF-02`：在 `ExportSerializer.kt` 与 `GenerationLoop.kt` 中补齐 SillyTavern 预设采样器与控制项（`temperature`、`topP`、`topK`、`minP`、`topA`、`frequencyPenalty`、`presencePenalty`、`repetitionPenalty`、`maxTokens`、`seed`、`wrapInQuotes`、`startReplyWith`、`promptPostProcessing`、`customStoppingStrings`、`logitBias`）的解析与生成请求消费。
  - `DF-03`：在 `PromptPage.kt`（世界书卡片菜单）与 `ConversationExport.kt`（聊天导出弹窗）中接通官方 SillyTavern World Info JSON 导出与 `.jsonl` 聊天记录导出。
  - `DF-04`：在 `ChatPage.kt` 与 `QuickMessagesPage.kt` 中补齐 Quick Reply v2 全生命周期自动触发（`executeOnStartup`、`executeOnChatChange`、`executeOnUser`、`executeOnAi`、`automationId`）与 UI 编辑弹窗开关。
- **Slice C（STscript / Slash 宿主真实接线）**：
  - `ST-01`：为 `/popup`、`/input`、`/buttons` 接入 `SlashModalRequest` + Compose `AlertDialog` 交互式协程挂起桥，同时保留无 UI/后台脚本场景下的非阻塞默认值回退。
  - `ST-02`：在 `ChatSlashHost.kt` 中将 `/api-url`、`/proxy`、`/persona-lock`、`/persona-sync`、`/qr-contextadd`、`/qr-contextdel`、`/qr-contextclear`、`/extension-exists`、`/extension-state`、`/extension-enable`、`/extension-disable`、`/extension-toggle`、`/bg`、`/theme`、`/context`、`/forcesave` 接通到真实的 `Settings` 与 `TavernVariableStore` 持久化状态。
- **Slice D（TavernHelper / Runtime API 与 Function Tool 桥接）**：
  - `RT-01`：在 `runtime.js`、`TavernRuntimeManager.kt` 与 `ChatToolFactory.kt` 中实现真实的 `registerFunctionTool` / `unregisterFunctionTool` 注册表与回调桥，将 JS 插件注册的 Function Tools 及 STscript `/tools-register` 注册的闭包工具统一注入原生 LLM 工具调用循环（`GenerationLoop`）。
- **Slice E（WebView 稳定性、MIME 与渲染器状态机）**：
  - `WV-01`：在 `TavernRuntimeManager.mimeOf` 中增加大小写归一、URL query/fragment 剥离与 `.jpg`/`.webp`/`.gif`/`.avif`/`.woff`/`.ttf`/`.mp3`/`.wav`/`.ogg` 支持；在 `panel.html` 中添加 `<meta name="referrer" content="no-referrer">`；在 `MessageHtmlBlock.kt` 中增强 `looksLikeInteractiveHtml`（支持 CSS `background-image`、`flex`/`grid`、`svg`/`canvas`/`iframe`，排除非 HTML 语言代码围栏）并引入 `PanelMountState`（纯 HTML/CSS 更新走原地 `mountCard()` 重挂载，仅在包含 `<script>` 时执行整页 reload）。
- **Slice F（单元测试补充、静态检查与交付打包）**：
  - 在 `app/src/test/` 下新增覆盖上述修复点的 JUnit 测试代码（标记为 `NOT RUN — USER REQUESTED NO BUILD`），运行全量静态检查并打包 `rikkaST-2.5.6-ai-reviewed-source.zip`。
