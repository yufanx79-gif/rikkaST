# SillyTavern 1.19.0 与五扩展兼容性矩阵（COMPATIBILITY.md）

## 一、核心运行时与 SillyTavern 1.19.0 兼容状态

| 子系统 / API | SillyTavern 1.19.0 契约 | rikkaST 实现位置 | 状态 | 备注 |
|---|---|---|---|---|
| 客户端版本标识 (`/version`、`getVersion()`、`CLIENT_VERSION`) | `'1.19.0'` | `TavernRuntimeManager.kt`、`runtime.js`、`st-compat/script.js` | 已适配 | 打开 JSR `>= 1.15.0` 能力分支与扩展 `minimum_client_version` 校验 |
| `MessageFormatter` 三阶段管线 | `BEFORE_REGEX` (`beforeRegex`)、`AFTER_REGEX` (`afterRegex`)、`AFTER_MARKDOWN` (`afterMarkdown`) + `hook_order` | `st-compat/message-formatter.js`、`runtime.js`、`StRegexedText.kt`、`TavernRuntimeManager.kt` | 已适配 | 同时作用于 WebView 渲染（`messageFormatting`）与 Compose 可见消息渲染（`StRegexedText`） |
| 异步事件等待 (`eventSource.emitAndWait`) | 按序 `await` 异步监听器 | `vendor/st/eventemitter.js`、`runtime.js` | 已适配 | 消除异步 Prompt 钩子在请求发送前未跑完的竞态 |
| 生成前事件顺序与消息数组改写 | `generate_interceptor` → `CHAT_COMPLETION_PROMPT_READY` → `CHAT_COMPLETION_SETTINGS_READY` | `runtime.js`、`TavernScripts.kt`、`GenerationLoop.kt` | 已适配 | 移除 `parsed.size == internalMessages.size` 限制，支持增删消息与 `abort()` |
| Chat Completion 请求桥 (`stream: true` / `stream: false`) | 流式返回 SSE（含 `data: [DONE]\n\n`），非流式返回 JSON `chat.completion` | `runtime.js`、`st-compat/openai.js`、`TavernRuntimeManager.kt` | 已适配 | 修复 `getStreamingReply` 异步导致 `[object Promise]` 及 `stream: false` 返回 SSE 帧的缺陷 |
| 新版宏注册表与作用域注释宏 | `MacroRegistry` + `{{//}}...{{///}}` 多行注释 | `st-compat/macros/engine/MacroRegistry.js`、`MacroEngine.kt`、`MacroDefinitions.kt` | 已适配 | 对齐 `MacroCstWalker.js:194-206` 与 `MacroEngine.e2e.js` |
| 用户文件与图片持久化端点 | `/api/files/{upload,delete}`、`/user/files/*`、`/api/images/{upload,list,delete}`、`/user/images/*` | `runtime.js`、`TavernRuntimeManager.kt` | 已适配 | 落盘至 `filesDir/tavern-user/{files,images}/` 并支持 WebView 资产直读 |
| 第三方扩展生命周期与清单查询 | `minimum_client_version`、`requires`、`optional`、`generate_interceptor`、`hooks.{activate,enable,disable,delete}`、`getExtensionManifest` | `TavernExtensions.kt`、`ThirdPartyExtensionsSection.kt`、`runtime.js` | 已适配 | 停用/删除扩展时自动触发对应钩子并清理 `MessageFormatter` 钩子与拦截器 |
| 共享 Linux Workspace Node.js 运行时 | 预编译 Node.js LTS 安装/校验/自检、常驻 `127.0.0.1` 守护进程、`/api/plugins/*` 代理与多会话隔离 | `NodeRuntimeManager.kt`、`TavernNodeServiceManager.kt`、`server-bootstrap.cjs`、`ThirdPartyExtensionsSection.kt` | 代码已实现并在当前 Linux 沙盒通过实测；Android 真机 PRoot 待实测 | 默认挂载 `littlewhitebox-server` 全部 22 条路由 |

## 二、五个指定扩展适配状态

| 扩展名称（包内实际版本） | 关键依赖链路 | 适配状态 | 说明与边界 |
|---|---|---|---|
| `ST-Prompt-Template` (`1.16.0`) | EJS 模板、`CHAT_COMPLETION_SETTINGS_READY` 可变长消息改写、`world_names`/`loadWorldInfo`、`substituteParams`、`messageFormatting` | 已完整适配 | 移除了 Kotlin 侧消息等长限制，扩展插入/拆分消息可进入真实模型请求体 |
| `JS-Slash-Runner` (`4.11.3`) | `/version` (`1.19.0`)、`MacroRegistry.js`、`createGenerationParameters`、同步 `getStreamingReply`、`/api/backends/chat-completions/{status,generate}`（流式与非流式）、世界书与聊天消息读写桥 | 已完整适配 | 修复了 `>= 1.15.0` 版本闸门下的模块缺失、流式 `[object Promise]` 与非流式 JSON 解析失败三处根因 |
| `st-memory-enhancement` (`2.2.0`) | `kai-settings.js`、`CHAT_COMPLETION_PROMPT_READY`（在 `SETTINGS_READY` 之前触发并 `splice` 注入记忆表格）、`macros.registry` | 已完整适配 | 修正事件顺序并共享 `eventData.{chat,messages,prompt}` 引用，与 `ST-Prompt-Template` 协同工作不丢失改写 |
| `LittleWhiteBox` (`3.1.7` + `littlewhitebox-server` `3.0.0`) | `/api/files/{upload,delete}`、`/user/files/*`、`/api/images/upload`、`/api/plugins/littlewhitebox-server/*`（NovelAI / SD-WebUI / ComfyUI 异步任务、Draw Runs、Loopback Host Client、Exa Web）、TTS DOM 高亮 | 已完整适配（Android 真机 PRoot 待实测） | 前端文件存储与后端 22 条 Node.js 路由均已接通并在当前沙盒通过端到端 HTTP 实测 |
| `ST-iPhonie` (`0.8.15`) | `manifest.json` `generate_interceptor: "stIphonieInterceptor"`（改写 `chat[i].mes`）、`/api/files/upload`、`/user/files/*`（`tavernFiles.js`）、`/api/images/{upload,list,delete}` | 已完整适配 | 在 `__rikkaRunGenerationHooks` 中按 `loading_order` 执行拦截器并将 `.mes` 改写同步回 `.content` |
