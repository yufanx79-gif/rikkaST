# rikkaST 迁移 SillyTavern 1.19.0 架构与十条基线线索核查报告

## 一、Workspace / PRoot 子系统对比（RikkaHub `2.5.6` vs `rikkaST`）

经对 `/tmp/st_audit/input/rikkahub-2.5.6/rikkahub-2.5.6/workspace` 与 `/tmp/st_audit/input/rikkast/rikkaST-main/workspace` 逐文件 diff 核查：
1. **源码一致性**：两者的 Kotlin 源码（`ProotShellRunner.kt`、`RootfsInstaller.kt`、`RootfsPatcher.kt`、`Workspace.kt`、`WorkspaceFileSystem.kt`、`WorkspaceManager.kt`、`WorkspaceShellRunner.kt`）完全一致。
2. **构建差异**：`rikkaST` 在 `workspace/build.gradle.kts` 中注释了 `externalNativeBuild` NDK 现场编译，改为直接在 `workspace/src/main/jniLibs/arm64-v8a/` 下内置预编译好的 `libtermux.so` 与 `libworkspace.so`，并在 `app` 的 `nativeLibraryDir` 中查找 `libproot_exec.so` 与 `libproot_loader.so`。
3. **一次性命令执行器的局限**：
   - `ProotShellRunner.execute()` 默认附加 `--kill-on-exit` 且调用 `process.readResult(timeoutMillis)`（默认 30 秒超时后 `destroyForcibly()`）。
   - 因此，**不能使用 `ProotShellRunner.execute()` 直接运行常驻 Node.js 服务**；必须新增专门的常驻进程管理器 `NodeRuntimeManager`，在后台协程作用域中持有 `Process` 实例、采集并脱敏滚动日志、维护 PID 与健康检查、并在异常退出时按退避策略自动恢复。

## 二、十条关键代码线索逐条核查结论

| 线索编号 | 线索内容 | 核查结论 | 源码位置证据 | 处理措施 |
|---|---|---|---|---|
| 1 | `messageFormatter` 只暴露空壳 `format/sanitize/generate`，缺少 `addHook`/`runStage`/`stage` | **确认属实** | `runtime.js:869-884` 仅有 3 个属性；`st-compat/script.js:187` 的 `messageFormatting` 仅返回 `String(mes)`；`StRegexedText.kt` 完全未调用 JS 侧 `messageFormatter` | 新增 `st-compat/message-formatter.js` 完整实现 1.19.0 三阶段管线，接入 `runtime.js` 与 `StRegexedText.kt` |
| 2 | `TavernExtensions.kt` 未解析 `generate_interceptor` 与 `hooks`，`runtime.js` 未调用生命周期钩子与生成拦截器 | **确认属实** | `TavernExtensions.kt:49-130` 仅解析基础字段；`runtime.js:2483-2534` `loadThirdPartyExtensions` 仅执行 `import(src)` | 在 `TavernExtensions.kt` 与 `runtime.js` 完整解析并调度 `hooks.{enable,activate,disable,delete}` 与 `generate_interceptor` |
| 3 | `eventSource.emitAndWait` 未等待异步监听器，`__rikkaRunGenerationHooks` 未 `await` | **确认属实** | `vendor/st/eventemitter.js:158-169` 未 `await`；`runtime.js:1759-1774` 未 `await` | 将 `emitAndWait` 改为异步顺序 `await` 监听器，并在 `__rikkaRunGenerationHooks` 中 `await` |
| 4 | `GenerationLoop.kt` 要求 `parsed.size == internalMessages.size`，导致插件插入/删除消息被丢弃 | **确认属实（P0 阻断缺陷）** | `GenerationLoop.kt:848` `if (parsed != null && parsed.size == internalMessages.size)` | 移除等长死锁限制，支持变长消息数组写回、角色校验、长度上限与 `aborted` 标志 |
| 5 | `runtime.js` 的 `/api/backends/chat-completions/generate` 始终返回 SSE 流，导致 `stream: false` 调用方 `response.json()` 崩溃 | **确认属实（P0 阻断缺陷）** | `runtime.js:104-141` `handleGenerate` 硬编码 `Content-Type: text/event-stream` 与 `data: ` 前缀 | 解析请求体 `stream` 字段：`stream === false` 时聚合返回 `application/json`，否则返回 SSE |
| 6 | `stCtx.getExtensionManifest` 返回 `null`；`world-info.js` 写接口为空操作 | **确认属实** | `runtime.js:1191` `getExtensionManifest: function () { return null; }`；`st-compat/world-info.js:122-129` | 绑定 `stCompatExtensions.getExtensionManifest`，并将 `world-info.js` 写接口桥接到 `RikkaBridge` |
| 7 | `LittleWhiteBox` 依赖 `/user/files/*`、`/api/files/{upload,delete}` 及 `littlewhitebox-server` | **确认属实** | `LittleWhiteBox-3.1.7/core/storage-file.js`、`ST-iPhonie-main/index.js:110`、`server-plugin/littlewhitebox-server/index.js` | 在 `TavernRuntimeManager.kt` + `runtime.js` 实现用户文件 API，并在共享 Node.js 服务中挂载 `littlewhitebox-server` |
| 8 | `ST-iPhonie` 强依赖 `messageFormatter.addHook`、`generate_interceptor`、`hooks` 及 `1.19.0` 版本 | **确认属实** | `ST-iPhonie-main/manifest.json`、`capability-contract.json`、`index.js:420-436` | 补齐全部能力契约并桥接可见消息渲染 |
| 9 | 版本号分裂（`TAVERN_ST_COMPAT_VERSION = '1.13.5'` vs `SillyTavern.version = '1.18.0'`） | **确认属实** | `runtime.js:939`、`TavernRuntimeManager.kt:942`、`st-compat/script.js:132` | 补齐 `st-compat/openai.js` 1.15+ / 1.19.0 导出函数后，统一全部版本号为 `'1.19.0'` |
| 10 | `MacroDefinitions.kt` 缺少 `setvarkey`/`getvarkey`/`setglobalvarkey`/`getglobalvarkey` 与 `{{///}}` | **确认属实** | `MacroDefinitions.kt`、`MacroEngine.kt` | 在 Kotlin 宏引擎与 JS 兼容层同步实现结构化变量深路径宏与闭合注释宏 |
