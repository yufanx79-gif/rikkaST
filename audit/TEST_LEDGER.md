# 自动化测试执行与真实五扩展集成验证台账（TEST_LEDGER.md）

## 一、可执行测试套件总览（当前 Linux / Node.js 验证环境）

| 序号 | 测试套件 / 命令 | 覆盖范围 | 执行状态 | 结果摘要 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `node --test scripts/st119-migration-and-5plugins.test.mjs` | ST 1.19.0 核心能力 + **5 个真实扩展模块加载与执行** + Node 守护进程正反向安全测试 + Bug A/B/C 回归校验 | **已执行 (PASS)** | **15 tests, 15 passed, 0 failed** (耗时 ~3.88s) |
| 2 | `bash scripts/verify-node-runtime-on-device.sh` | Layer 1 (Node/npm 二进制) + Layer 2 (`127.0.0.1` 守护进程与健康检查) + Layer 3 (`littlewhitebox-server` 22 路由、401/403 防伪造拦截、多助手/多会话隔离) | **已执行 (PASS)** | **All Layers Passed** (`PID` 启动、`/health`、`401`、`403`、`/probe`、会话隔离全部通过) |
| 3 | 全量 5 扩展 ES Module 导入图与具名导出静态链接扫描 | 扫描 `all-needs.zip` 中全部 5 个真实扩展对 `/script.js`、`/lib.js`、`/scripts/**` 的所有静态/动态导入与具名导出 | **已执行 (PASS)** | **Missing files: 0, Missing exports: 0** |
| 4 | 全仓修改文件 JS 语法检查 (`node --check`) 与 Kotlin 词法级括号/标签静态检查 | 全部新增与修改的 `.js` / `.cjs` / `.mjs` / `.kt` 源码文件 | **已执行 (PASS)** | **JS syntax errors: 0, Kotlin balance/label errors: 0** |
| 5 | `./gradlew :workspace:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` | Android Gradle 编译与 JUnit 单测（含 `NodeRuntimeManagerTest`、`TavernExtensionsTest`、`MacroEngineTest`、`TavernScriptsTest`） | **待目标环境执行** | 当前容器无 Android SDK / 离线 Gradle 缓存且禁外网；已提供完整源码、单测与复测命令 |

---

## 二、15 项核心与真实扩展集成测试明细（`scripts/st119-migration-and-5plugins.test.mjs`）

1. **Test 1 (`Clue 1`)**：验证 `runtime.js`、`st-compat/script.js`、`TavernRuntimeManager.kt`（含 `TavernJsBridge.getVersion()`）版本门禁统一为 `1.19.0`；验证 `MacroRegistry` 宏注册/查询与 `openai.js` `createGenerationParameters`、`settingsToUpdate`。
2. **Test 2 (`Clue 3`)**：验证 `st-compat/openai.js` 的 `getStreamingReply` 为同步函数，正确提取 OpenAI / Claude / Gemini 流式分片且不产生 `[object Promise]`。
3. **Test 3 (`Clue 5`)**：验证 `EventEmitter.prototype.emitAndWait` 按注册顺序串行 `await` 异步监听器，且 `vendor/st/events.js` 与 `st-compat/events.js` 共享同一全局 `eventSource` 单例。
4. **Test 4 (`Clue 6 & P0-2`)**：验证 `MessageFormatter` 1.19.0 三阶段管线（`BEFORE_REGEX`、`AFTER_REGEX`、`AFTER_MARKDOWN`）、优先级排序、参数校验、冻结上下文、按 owner 清理钩子，并验证 `StRegexedText.kt` 订阅 `messageFormatterVersionFlow` 且通过 `computeStRegexedTextAsync` 非阻塞执行。
5. **Test 5 (`Clue 2 & Clue 7`)**：验证多扩展可变长度消息数组改写（`ST-iPhonie` 替换 `.mes`、`st-memory-enhancement` 插入表格消息、`ST-Prompt-Template` 重赋 `data.messages`），并验证 `GenerationLoop.kt` 已移除旧的长度相等限制与非法 `return@channelFlow`。
6. **Test 6 (`Clue 4 & Clue 9`)**：验证 `extractMessageFromData`、`util/AccountStorage.js`、`custom-request.js` (`ChatCompletionService.presetToGeneratePayload`)、`world-info.js` (`originalWIDataKeyMap`、`onWorldInfoChange`、`updateWorldInfoSettings`)、`utils.js` (`getFileText`、`getSortableDelay`、`extractTextFromOffice`)、`PromptManager.js` (`INJECTION_POSITION`) 与 `group-chats.js` (`is_group_generating`)。
7. **Test 7 (`Clue 8`)**：验证 `MacroEngine.kt` 与 `MacroDefinitions.kt` 支持 ST 1.19.0 `{{//}}...{{///}}` 多行作用域注释宏与 `delayArgResolution = true`。
8. **Test 8 (`Real Extension 1/5: ST-Prompt-Template-1.16`)**：校验真实 `manifest.json` (`v1.16.3.0`)、`dist/index.js`、`dist/ejs.workers.js`，并通过 `vendor/lib/ejs.umd.js` + `shims/ejs-render.js` 真实编译执行 `<% if (getvar("hp") > 50) { %>...<% setvar("hp", 100); } %>` 模板。
9. **Test 9 (`Real Extension 2/5: JS-Slash-Runner-main`)**：校验真实 `manifest.json` (`v4.11.3`, `hooks.activate: "activateTauriTavernChatSurface"`)、`dist/index.js` 产物完整性与 `util-shims.js` 接口。
10. **Test 10 (`Real Extension 3/5: st-memory-enhancement-2.2.0`)**：加载并执行真实扩展源码 `core/table/utils.js` (`getColumnLetter`) 与 `utils/stringUtil.js` (`replaceUserTag`、`safeParse`)。
11. **Test 11 (`Real Extension 4/5: LittleWhiteBox-3.1.7`)**：加载并执行真实扩展源码 `core/constants.js` 与 `shared/common/generate-interceptor.js`，注册多个不同优先级 (`order: 90` vs `order: 10`) 的拦截器并通过 `globalThis.xiaobaixGenerateInterceptor` 验证按序异步调度与注销清理。
12. **Test 12 (`Real Extension 5/5: ST-iPhonie-main`)**：加载并执行真实扩展源码 `core/outgoing.js` (`outgoingChat`, `withoutVoice`) 与 `core/protocol.js` (`parseDialogue`, `DEFAULT_FORMAT`)，验证历史消息剥离 `<tts>` 标签而最新一条语音回复在 `keepLatest: true` 下完整保留示例标签。
13. **Test 13 (`P0-1`)**：验证 `NodeRuntimeManager.kt` 中 `SHA256_LINUX_ARM64` 与 `SHA256_LINUX_X64` 与 Node.js 官方 `v20.18.1` `SHASUMS256.txt` 完全一致。
14. **Test 14 (`Clue 10 & P0-3`)**：真实拉起 `server-bootstrap.cjs` + `littlewhitebox-server` 子进程，验证：
    - 未携带 Bearer Token 请求返回 HTTP `401`；
    - 伪造 `X-Rikka-Profile-Handle` 请求返回 HTTP `403 (Rejected spoofed X-Rikka-Profile-Handle)`；
    - `POST /api/host/context` Body 伪造 `assistantId` / `conversationId` 返回 HTTP `403 (session_identity_mismatch)`；
    - 不同 `(assistantId, conversationId)` 会话间的变量与状态严格隔离；
    - `/api/backends/chat-completions/{status,generate}` 非流式 JSON 与流式 SSE 正常响应；
    - `/api/plugins/littlewhitebox-server/v1/jobs` 与 `/v1/cancel` 正常运作。
15. **Test 15 (`Production Bugs A, B, C`)**：验证 `NavContext.kt`、`RouteActivity.kt`、`ImeAutoScroller.kt`、`TavernRuntimeManager.kt`（7 阶段 `SendDiagnosticStage`）与 `ChatService.kt` 修复到位。
