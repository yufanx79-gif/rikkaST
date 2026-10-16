# Changelog — rikkaST 2.5.6 (AI Reviewed Source Candidate)

> **交付状态说明**：本版本已在隔离工作区内完成全部源码修复、单测补充与非 Gradle 静态审查（Kotlin 词法/语法扫描、符号与签名核对、Node.js `--check`、JSON/XML 校验），**待 Windows 本机编译验收**。

## Fixed (P0 Compile Blockers & Static Regression Gate)
- **[KOT-01]** `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`：显式导入 `me.rerere.rikkahub.data.model.matchesAnyKey`，移除错误的包名 `.run {}` 调用方式。
- **[KOT-02]** `app/src/main/java/me/rerere/rikkahub/data/export/ExportSerializer.kt`：补齐 `SelectiveLogic` 导入，并在 `SillyTavernLorebookSerializer` 的 `when (entry.selectiveLogic)` 中补充 `SelectiveLogic.OR_ANY -> 0` 分支以满足 Kotlin 穷尽性检查。
- **[KOT-03]** `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`：修复 `backgroundTextGenerationParams(model, convUuid ?: Uuid.random())` 中可空 `Uuid?` 传入非空 `Uuid` 参数的类型不匹配问题。
- **[KOT-04]** `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt`：移除 `ChatMessageItem` 调用处重复的 `lastMessage = index == lastMessageIndex` 命名参数。
- **[KOT-05]** `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatSlashHost.kt`：为 `SLASH_GETVAR_REGEX` 与 `SLASH_GETGLOBALVAR_REGEX` 替换 lambda 中的 `?: if (...) null else ...` 添加显式括号后再调用 `.orEmpty()`，消除 Elvis 与 `if` 优先级导致的 `String?` 返回类型不匹配。
- **[KOT-06]** `app/src/main/java/me/rerere/rikkahub/data/model/WorldInfoDecorators.kt`：为顶层重载函数添加 `@JvmName("applyWorldInfoDecoratorOverridesTopLevel")`，消除与同名扩展函数 `PromptInjection.RegexInjection.applyWorldInfoDecoratorOverrides` 的 JVM 字节码签名冲突。
- **[KOT-07]** `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`：在 `collectInjections` 函数签名中补齐 `delayOverrideEntries: Map<Uuid, Int> = emptyMap()` 形参，并从 `transformMessages` 正确透传。
- **[KOT-08]** `app/src/main/java/me/rerere/rikkahub/data/st/script/StSlashExecutor.kt`：将 `/closure-serialize` 与 `/closure-deserialize` 中访问的不存在属性 `.rawText` 修正为 `StClosure.raw`（同时保留序列化 JSON 键名 `"rawText"`）。
- **[KOT-09]** `app/src/main/java/me/rerere/rikkahub/data/st/script/StSlashExecutor.kt`：将 `/tools-invoke` 与 `/loader-wrap` 中不存在的 `invokeClosure(...)` 调用修正为实际存在的 `runClosure(closure = ..., provided = ..., host = host, parentPipe = pipe, scope = scope, label = ...)`。
- **[KOT-10]** `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`：在 `ChatPage` Composable 顶层声明 `val toaster = LocalToaster.current` 与 `val openConversation: (Uuid) -> Unit`，解决 `LaunchedEffect` 中 Quick Reply 自动触发块引用未定义符号的作用域问题。
- **[KOT-11]** `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt`：重构 `parseCardFromUri`，使 `withContext(Dispatchers.IO)` 各 `when (mime)` 分支统一返回 `Pair<Assistant, List<Lorebook>>`，消除 lambda 内禁止的非局部 `return`。
- **[KOT-12]** `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatSlashHost.kt`：补齐缺失的 `import me.rerere.ai.ui.UIMessage`。

## Added (New Capabilities & End-to-End Wiring)
- **[DF-01] `.charx` 内嵌资产持久化与 `embeded://` 协议重写**（`AssistantImporter.kt`、`TavernRuntimeManager.kt`、`StArchiveImporter.kt`）：
  - 导入 `.charx` 时将 `assets/` 下的图标、立绘与贴图资源保存至 `filesDir/charx_assets/<assistantId>/`（无 `Context` 单测环境下自动转为 `data:image/...;base64,...`），并自动将角色卡 `firstMessage`、`alternateGreetings`、`groupOnlyGreetings`、`description`、`systemPrompt`、`presetMessages`、世界书条目与内嵌正则中的 `embeded://`、`embedded://`、`ccdefault:` 引用重写为 `https://st.rikka.local/charx_assets/<assistantId>/...`；
  - `StArchiveImporter` 在导入酒馆全量备份 ZIP 时保留 `.charx` 与 `.byaf` 文件后缀，确保归档内 V3 角色卡走正确的解析分支。
- **[DF-02] SillyTavern 预设采样器、停止词、`logit_bias` 与 `prompt_post_processing` 全链路接入**（`ExportSerializer.kt`、`GenerationLoop.kt`）：
  - `StActivePresetConfig` 新增解析 `temperature`、`topP`、`topK`、`minP`、`topA`、`frequencyPenalty`、`presencePenalty`、`repetitionPenalty`、`maxTokens`、`seed`、`wrapInQuotes`；
  - `GenerationLoop` 在构造 `TextGenerationParams` 时自动应用预设的 `wrapInQuotes`（为用户消息自动包裹引号）、`startReplyWith` / `assistantPrefill`、`applyPromptPostProcessing`（支持 `merge`、`merge_tools`、`semi`、`strict`、`single` 五种官方消息角色合并/降级策略）以及 `buildPresetMergedCustomBodies`（透传 `stop`、`logit_bias`、惩罚项与高级采样参数）。
- **[DF-03] 世界书 SillyTavern JSON 导出与聊天记录 `.jsonl` 导出 UI 入口**（`PromptPage.kt`、`ConversationExport.kt`）：
  - 在世界书管理页（`LorebookCard` 更多菜单）新增“导出为酒馆 World Info (.json)”选项；
  - 在会话导出面板（`ChatExportSheet`）新增“SillyTavern (.jsonl)”导出格式，完整保留 Swipe 候选消息与消息级变量。
- **[DF-04] Quick Reply v2 全生命周期自动触发与完整编辑界面**（`ChatPage.kt`、`QuickMessagesPage.kt`、`QuickMessagesVM.kt`）：
  - 接通 `executeOnStartup`（每进程启动首次进入会话触发一次）、`executeOnChatChange`（切换会话触发）、`executeOnUser`（用户发送消息触发）与 `executeOnAi` / `automationId`（AI 回复完成及世界书条目激活联动触发）；
  - 升级 `EditQuickMessageDialog`，支持编辑 QR 集合名（`setName`）、`automationId` 以及 5 类自动触发开关与隐藏按钮开关。
- **[ST-01] STscript `/popup`、`/input`、`/buttons` 真实 Compose 模态对话框**（`StSlashExecutor.kt`、`ChatSlashHost.kt`、`TavernRuntimeManager.kt`、`ChatPage.kt`）：
  - 新增 `SlashModalRequest` 协程挂起桥，在聊天界面弹出原生 Material3 `AlertDialog` 供用户确认、输入文本或点击候选按钮，并将结果写回 STscript 管道。
- **[RT-01] `registerFunctionTool` / `unregisterFunctionTool` 与 STscript 工具动态注入 LLM 工具链**（`runtime.js`、`TavernRuntimeManager.kt`、`ChatToolFactory.kt`）：
  - 在 `runtime.js` 中实现真实的 `SillyTavern.registerFunctionTool` 与 `unregisterFunctionTool`，同步工具 JSON Schema 至 `TavernRuntimeManager`，并通过 `window.__rikkaInvokeFunctionTool` 支持同步/Promise 异步工具回调；
  - 在 `ChatToolFactory.createTools` 中将 STscript `/tools-register` 与 JS `registerFunctionTool` 注册的工具转换为原生 `Tool` 实例（名称前缀 `st_tool_` / `st_js_tool_`），由 `GenerationLoop` 自动调度。

## Changed & Compatibility Improvements
- **[ST-02] Slash 宿主命令真实化**（`ChatSlashHost.kt`）：
  - `/api-url` 与 `/proxy` 支持读取与持久化修改当前活跃 Provider 的 `baseUrl`；
  - `/persona-lock` 与 `/persona-sync` 支持按角色绑定/解绑并自动同步锁定的人设（`Persona.lockedCharacterIds`）；
  - `/qr-contextadd`、`/qr-contextdel`、`/qr-contextclear` 支持持久化修改 Quick Reply 的 `contextMenu` 标记；
  - `/extension-exists`、`/extension-state`、`/extension-enable`、`/extension-disable`、`/extension-toggle` 接通 `tavernThirdPartyDisabled` 持久化集合与已安装扩展扫描；
  - `/bg`、`/theme`、`/context`、`/forcesave` 接通角色背景、应用主题、上下文模板更新与变量立即落盘。
- **[WV-01] WebView 面板渲染与资源加载优化**（`TavernRuntimeManager.kt`、`panel.html`、`MessageHtmlBlock.kt`）：
  - `TavernRuntimeManager.mimeOf` 支持大小写无关后缀、自动剥离 `?query` 与 `#hash`，并新增 `.jpg`、`.webp`、`.gif`、`.avif`、`.woff`、`.ttf`、`.otf`、`.mp3`、`.wav`、`.ogg`、`.m4a`、`.webm`、`.mp4` 等 MIME 类型；
  - `panel.html` 增加 `<meta name="referrer" content="no-referrer">` 防止外链图床因 `https://st.rikka.local` Referer 返回 403；
  - `looksLikeInteractiveHtml` 支持识别带 CSS `background-image`、`display: flex/grid`、`<svg>`/`<canvas>`/`<iframe>` 的现代状态栏卡片，并过滤 ` ```xml ` 等非 HTML 代码块；
  - `MessageHtmlBlock` 引入 `PanelMountState`：无 `<script>` 的 HTML/CSS 更新走原地 `mountCard()` 重挂载，避免流式输出或变量刷新时整页重载闪烁。

## Regression & Native Capabilities Protection
- 原生 RikkaHub 2.5.6 数据库 Schema（Room v1 ~ v12）、Provider 配置、MCP 工具、内置 Skills、知识库（KnowledgeBase）、搜索（Exa/Tavily/Zhipu/SearXNG/Bing）、原生语音（TTS）与图片生成能力均保持完整无删减。

## Known Limitations
- 详见 `KNOWN_LIMITATIONS.md`：包含 Node.js 后端插件（Server Plugins）不适用说明、酒馆官方 TTS/SD WebUI 扩展未承诺兼容（请使用 RikkaHub 原生语音/生图配置）、以及跨 iframe 顶层 DOM 强耦合脚本的沙箱边界说明。
