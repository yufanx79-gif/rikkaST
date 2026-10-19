# SillyTavern 1.19.0 迁移与五扩展适配变更日志（CHANGELOG.md）

## 2026-10-18 — SillyTavern 1.19.0 兼容层升级、五扩展适配与共享 Node.js 运行时实现

### 1. SillyTavern 1.19.0 运行时与兼容层（`app/src/main/assets/st-runtime/`）
- **`vendor/st/eventemitter.js`**：将 `EventEmitter.prototype.emitAndWait` 升级为 `async function`，严格按监听顺序 `await` 异步监听器。
- **`st-compat/message-formatter.js`（新增）**：实现官方 SillyTavern `1.19.0` `MessageFormatter` 单例、`formatting_stage`（`beforeRegex` / `afterRegex` / `afterMarkdown`）与 `hook_order`（`EARLIEST=0` 至 `LATEST=100`），支持同步钩子校验、冻结上下文注入、按 `owner` 清理钩子与跨桥状态同步。
- **`st-compat/macros/engine/MacroRegistry.js` & `st-compat/macros/macro-system.js`（新增）**：实现 `1.19.0` 宏注册表 API，满足 `JS-Slash-Runner` 在版本 `>= 1.15.0` 下的动态模块导入。
- **`st-compat/kai-settings.js`（新增）**：提供 `kai_settings` 导出，消除 `st-memory-enhancement` 顶层静态导入失败。
- **`st-compat/openai.js`**：升级至 `1.19.0`，新增 `POLLINATIONS_ENDPOINT`、`createGenerationParameters`，并将 `getStreamingReply` 修复为同步函数。
- **`st-compat/world-info.js`**：接入 `saveWorldInfo`、`deleteWorldInfo`、`createNewWorldInfo`、`createWorldInfoEntry`、`deleteWorldInfoEntry` 到 `RikkaBridge` 世界书持久化接口。
- **`st-compat/script.js` & `runtime.js`**：
  - 统一 `TAVERN_ST_COMPAT_VERSION = '1.19.0'`、`CLIENT_VERSION = '1.19.0'`、`SillyTavern.version = '1.19.0'`；
  - 修复 `installChatCompletionsFetchPatch` 中 `/api/backends/chat-completions/generate` 在 `stream: false` 下返回 SSE 帧及 `stream: true` 下双反斜杠换行的问题；
  - 新增 `/api/files/{upload,delete}`、`/user/files/*`、`/api/images/{upload,list,delete}` 与 `/api/plugins/*` 的 `fetch` 路由支持；
  - 升级 `__rikkaRunGenerationHooks`：支持 `generate_interceptor`（含 `abort()` 与 `.mes` 同步）、`eventData.{messages,chat,prompt}` 同引用读写、以及官方事件触发顺序（`CHAT_COMPLETION_PROMPT_READY` 先于 `CHAT_COMPLETION_SETTINGS_READY`）；
  - 升级 `loadThirdPartyExtensions`：支持 `minimum_client_version`、`requires`、`hooks.{activate,enable,disable,delete}` 与 `getExtensionManifest`。

### 2. Android / Kotlin 宿主与生成管线（`app/`）
- **`MacroEngine.kt` & `MacroDefinitions.kt`**：支持 `1.19.0` `{{//}}...{{///}}` 多行作用域注释宏（首参以 `/` 开头判定为闭合块，且启用 `delayArgResolution = true` 跳过注释内部宏求值）。
- **`TavernExtensions.kt` & `ThirdPartyExtensionsSection.kt`**：扩展 `TavernExtensionInfo`、`parseManifest` 与 `buildRuntimeExtensionsJson`，解析并下发 `optional`、`minimum_client_version`、`generate_interceptor` 与 `hooks`；在停用/删除扩展时触发 JS 生命周期钩子。
- **`TavernScripts.kt` & `GenerationLoop.kt`**：新增 `StGenerationHookResult` 与 `parseStGenerationHookResult`，移除 `GenerationLoop.kt` 中 `parsed.size == internalMessages.size` 的等长限制，支持可变长消息数组改写与拦截器 `abort` 终止生成。
- **`StRegexedText.kt` & `MessageHtmlBlock.kt`**：将 `MessageFormatter` 三阶段钩子接入 Compose 可见消息渲染链路，并在 `looksLikeInteractiveHtml` 中支持 `data-sttts-`/`sttts-` 与音视频标签识别。
- **`TavernRuntimeManager.kt`**：升级 `TAVERN_ST_COMPAT_VERSION = "1.19.0"`，新增 `MessageFormatter` 阶段执行桥、`/user/files/*` 与 `/user/images/*` 文件读写与 WebView 资产拦截服务，以及 `nodePluginHttpRequestAsync` / `nodePluginHttpAbort` 桥接方法。

### 3. 共享 Linux Workspace Node.js 运行时与服务管理（`workspace/` & `app/`）
- **`workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt`（新增）**：实现官方预编译 Node.js LTS（默认 `v20.18.1`）下载、SHA-256 校验、解压、`node --version` / `npm --version` 执行自检、常驻 PRoot 守护进程启动（不带一次性 `--kill-on-exit`）、停止、`/health` 探针、崩溃指数退避自动重启与敏感日志脱敏。
- **`app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernNodeServiceManager.kt`（新增）**：管理应用级共享 Node.js 服务、资产同步、`Bearer` / `X-CSRF-Token` 鉴权转发与会话隔离头（`X-Rikka-Assistant-Id`、`X-Rikka-Conversation-Id`）。
- **`app/src/main/assets/st-runtime/node-host/server-bootstrap.cjs` & `plugins/littlewhitebox-server/`（新增）**：实现仅绑定 `127.0.0.1` 的共享 Node.js 宿主服务，内置挂载 `littlewhitebox-server` 全部 22 条路由、回环 Chat Completion 转发与多会话隔离上下文存储。
- **`ThirdPartyExtensionsSection.kt`**：新增「共享 Node.js 服务端运行时 (PRoot / 127.0.0.1)」管理与诊断日志卡片。
- **`scripts/verify-node-runtime-on-device.sh` & `scripts/st119-migration-and-5plugins.test.mjs`（新增）**：提供三层可复现验收诊断脚本与自动化回归测试套件。
