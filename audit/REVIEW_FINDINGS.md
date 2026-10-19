# 独立对抗式代码审查记录（REVIEW_FINDINGS.md）

## 概述
本文件记录针对 `rikkaST`（`2.5.6-public` 线上问题 + SillyTavern `1.19.0` 迁移候选版本）开展的两轮深度代码审查（Round 1 实现者自审 + Round 2 独立对抗式审查）发现的全部真实缺陷、幻觉代码与安全漏洞，以及对应的根因修复与复测证据。

---

## 第一轮审查（Round 1：线上高优 Bug A/B/C 与候选迁移 P0 缺陷清零）

### Finding R1-01（线上 Bug A）：`NavDisplay backstack cannot be empty` 偶发主线程崩溃
- **严重度**：Critical (P0)
- **位置**：
  - `app/src/main/java/me/rerere/rikkahub/ui/context/NavContext.kt`
  - `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
- **根因分析**：
  1. `Navigator.clearAndNavigate` 先执行 `backStack.clear()` 再执行 `backStack.add(screen)`，在 `SnapshotStateList` 上产生长度瞬间为 `0` 的中间窗口，若此时主线程触发 Compose 重组，`NavDisplay` 立即抛出 `IllegalArgumentException: NavDisplay backstack cannot be empty`；
  2. `Navigator.navigate` 在 `popUpTo(..., inclusive = true)` 命中栈底时可能将 `backStack` 弹空；
  3. `RouteActivity.kt` 的 `NavDisplay(onBack = { backStack.removeLastOrNull() })` 绕过 `Navigator.popBackStack()` 直接移除栈顶，当栈中仅剩 1 个页面且系统预测返回或 WebView 返回竞争触发 `onBack` 时，直接把 `backStack` 弹空。
- **修复措施**：
  1. 所有 `Navigator` 写操作统一使用 `synchronized(backStack)` + `Snapshot.withMutableSnapshot` 原子包裹；
  2. `clearAndNavigate` 与 `navigate(popUpTo root inclusive)` 改为「先追加新页面、再移除旧页面」，保证任意时刻 `backStack.size >= 1`；
  3. `popBackStack()` 强制守护 `backStack.size > 1` 并返回 `Boolean`；新增 `ensureNonEmpty(fallback)` 并在 `RouteActivity.kt` 渲染 `NavDisplay` 前及 `onBack` 回调中强制调用。
- **复测状态**：已修复并通过回归校验。

### Finding R1-02（线上 Bug B & Bug C）：长消息中间位置点击发送明显卡顿、滚到底部但消息未发出且未触发生成
- **严重度**：Critical (P0)
- **位置**：
  - `app/src/main/java/me/rerere/rikkahub/ui/hooks/ImeAutoScroller.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatInput.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`
  - `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`
- **根因分析**：
  1. **IME 收起与列表滚动锁竞争**：`ImeLazyListAutoScroller` 在软键盘收起（`keyboardHeight == 0`）时计算负向 `diff` 并调用 `lazyListState.scrollBy(diff)`，抢占 `LazyListState` 的 `MutatorMutex`，打断或吞掉发送逻辑触发的 `scrollToBottom`；
  2. **历史遗留或前序异常状态阻塞发送队列**：
     - 若 `editingMessage` 残留了一个不在当前 `conversation.messageNodes` 中的旧节点 UUID，`ChatService.sendQueuedMessage` 在 `if (index == -1) return` 处静默丢弃本次发送，而 `ChatPage` 却先清空了输入框并滚到底部；
     - 若前序轮次异常导致 `session.messageQueue` 处于 `paused` 状态，或 `session.getJob()` 持有一个已完成但未置空的 `Job` 引用，或历史消息残留 `isPending == true`，`dispatchNextQueuedMessage` 直接 `return` 死锁；
     - 扩展或快速回复在 `message_sent` 阶段调用 `/trigger` 时，旧逻辑无条件取消当前正在执行的 `sendQueuedMessage` 协程（自杀式取消）；
  3. **主线程 I/O 与大对象序列化卡顿**：`saveConversation`、`ConversationRepository` 的 JSON 序列化 + FTS 全文索引更新、流式每 token 的 `checkFiles = true` 全树遍历、以及 `TavernRuntimeManager.appendLog` 磁盘追加均在主线程或未隔离调度器上执行。
- **修复措施**：
  1. 建立 **7 阶段发送诊断链路（`SendDiagnosticStage.STAGE_1_SEND_CLICKED` ~ `STAGE_7_GENERATION_FINISHED`）**，全链路记录时间戳、线程名、队列长度与失败原因；
  2. 修复 `ImeAutoScroller.kt`，仅在软键盘弹出（`keyboardHeight > 0 && delta > 0`）且未处于滚动中时正向补偿，软键盘收起时绝不反向抢占滚动锁；
  3. 修复 `ChatPage.kt` 与 `ChatInput.kt`：先快照输入内容再收起键盘，校验 `editingMessage` 是否存在于当前 `messageNodes`（无效则自动清理失效编辑态），仅在 `handleMessageSend` 成功入队后清空输入框；
  4. 修复 `ChatService.kt`：`sendMessage` 显式调用 `session.messageQueue.resume()`；`ConversationSession.getJob()` 自动清理非 active 的 Job；清理残留 `isPending` 工具调用；引入 `isExecutingSendTurn` 防止嵌套 `/trigger` 自取消；先同步更新内存 `session.state` 立即刷新 UI，再在 `Dispatchers.IO` 异步落盘；流式分片使用 `checkFiles = false`。
- **复测状态**：已修复并通过回归测试。

### Finding R1-03（P0-1）：`NodeRuntimeManager.kt` 中 Node.js v20.18.1 官方 SHA-256 校验值错误（AI 幻觉常量）
- **严重度**：Critical (P0)
- **位置**：`workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt`
- **根因分析**：候选迁移版本中硬编码的 `SHA256_LINUX_ARM64` (`e5f61d79...`) 与 `SHA256_LINUX_X64` (`f0f28b92...`) 并非 Node.js 官方 `v20.18.1` `SHASUMS256.txt` 的真实哈希，会导致真机首次下载官方 Node.js LTS 压缩包后必然触发 SHA-256 校验失败并删除安装包。
- **修复措施**：核对 Node.js 官方 `v20.18.1` 发布清单，修正为真实 SHA-256：
  - `node-v20.18.1-linux-arm64.tar.xz`：`44d1ffc5905c005ace4515ca6f8c090c4c7cfce3a9a67df0dba35c727590b8f6`
  - `node-v20.18.1-linux-x64.tar.xz`：`c6fa75c841cbffac851678a472f2a5bd612fff8308ef39236190e1f8dbb0e567`
  同时在 `isNodeVersionSupported` 中兼容 Node `>=18.20` 与 `>=20.11.0`。
- **复测状态**：已修复并在 `NodeRuntimeManagerTest.kt` 与 `st119-migration-and-5plugins.test.mjs` 中锁定断言。

### Finding R1-04（P0-2）：`StRegexedText.kt` 在主线程绕过 `MessageFormatter` 钩子且未订阅钩子版本状态
- **严重度**：High (P0)
- **位置**：
  - `app/src/main/java/me/rerere/rikkahub/ui/components/message/StRegexedText.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt`
- **根因分析**：`TavernRuntimeManager.runMessageFormatterStage` 在检测到 `Looper.myLooper() == Looper.getMainLooper()` 时直接返回原文本以避免死锁，而 `StRegexedText.kt` 在主线程同步调用该函数，导致 Compose 原生消息文本渲染路径上 `MessageFormatter` 钩子实际上被 100% 绕过。
- **修复措施**：新增 `messageFormatterVersionFlow: StateFlow<Long>` 与非阻塞挂起函数 `runMessageFormatterStageAsync`；`StRegexedText.kt` 订阅 `messageFormatterVersionFlow`，并在 `hasMessageFormatterHooks` 为真时统一走异步 `produceState` + `computeStRegexedTextAsync` 管线，既不阻塞主线程又完整执行 `BEFORE_REGEX` / `AFTER_REGEX` / `AFTER_MARKDOWN` 钩子。
- **复测状态**：已修复并通过契约测试。

### Finding R1-05（P0-3）：`TavernNodeServiceManager.kt` 与 `server-bootstrap.cjs` 会话身份可被扩展请求头或 Body 伪造
- **严重度**：High (P0 Security)
- **位置**：
  - `app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernNodeServiceManager.kt`
  - `app/src/main/assets/st-runtime/node-host/server-bootstrap.cjs`
- **根因分析**：转发客户端请求头时未过滤 `x-rikka-*` / `authorization` / `cookie`，且 `server-bootstrap.cjs` 优先信任客户端传入的 `X-Rikka-Profile-Handle` 或 Body 中的 `assistantId` / `conversationId`，存在跨会话读写污染风险。
- **修复措施**：
  1. `TavernNodeServiceManager.buildSanitizedRequestHeaders` 强制剥离所有客户端传入的 `authorization`、`x-csrf-token`、`host`、`cookie`、`set-cookie` 及 `x-rikka-*` 头，由宿主统一注入真实会话头；
  2. `server-bootstrap.cjs` 根据 `assistantId` 与 `conversationId` 计算唯一合法的 `canonicalHandle`，若请求携带不一致的 `X-Rikka-Profile-Handle` 或在 `/api/host/context` Query/Body 中尝试覆盖会话身份，立即返回 HTTP `403 Forbidden (session_identity_mismatch)`。
- **复测状态**：已修复并通过正反向对抗测试（401 / 403 / 跨会话读写隔离）。

---

## 第二轮审查（Round 2：独立对抗式深挖——编译断路幻觉、五扩展全量 ESM 静态链接与目录别名审计）

### Finding R2-01（编译断路幻觉）：`GenerationLoop.kt:863` 存在非法标签返回 `return@channelFlow`
- **严重度**：Critical (Compile Blocker / AI Hallucination)
- **位置**：`app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt:863`
- **根因分析**：候选迁移代码在 `private suspend fun generateInternal(...)` 内部处理 `hookOutcome.aborted` 时写出了 `return@channelFlow`，而 `generateInternal` 是一个普通 `suspend fun`（外层 `generate` 使用的也是 `flow { ... }` 而非 `channelFlow`），在 Kotlin 编译器下会直接触发 `Unresolved label @channelFlow` 编译失败。
- **修复措施**：替换为合法的 `return`，并在返回前补齐 `SendDiagnosticStage.STAGE_7_GENERATION_FINISHED` 诊断记录与 `generation_stopped` 事件发射。
- **复测状态**：已修复并通过全仓 Kotlin 词法级语法与标签静态扫描。

### Finding R2-02（真机扩展加载阻断）：`LittleWhiteBox-3.1.7` 与 `ST-iPhonie-main` 依赖的 2 个 `st-compat` 模块与 19 个具名导出缺失
- **严重度**：Critical (P0 Extension Load Blocker)
- **位置**：
  - `app/src/main/assets/st-runtime/st-compat/util/AccountStorage.js`（新增）
  - `app/src/main/assets/st-runtime/st-compat/events.js`（新增）
  - `app/src/main/assets/st-runtime/vendor/st/events.js`
  - `app/src/main/assets/st-runtime/st-compat/script.js`
  - `app/src/main/assets/st-runtime/st-compat/world-info.js`
  - `app/src/main/assets/st-runtime/st-compat/utils.js`
  - `app/src/main/assets/st-runtime/st-compat/RossAscends-mods.js`
  - `app/src/main/assets/st-runtime/st-compat/PromptManager.js`
  - `app/src/main/assets/st-runtime/st-compat/group-chats.js`
  - `app/src/main/assets/st-runtime/st-compat/openai.js`
  - `app/src/main/assets/st-runtime/st-compat/custom-request.js`
- **根因分析**：对 `all-needs.zip` 解压出的全部 5 个真实扩展执行全量 ES Module 导入图静态扫描后发现：
  1. `LittleWhiteBox-3.1.7/modules/scheduled-tasks/scheduled-tasks.js` 静态导入 `../../../../../util/AccountStorage.js`（对应 `/scripts/util/AccountStorage.js`），此前该文件不存在，会导致整个 `LittleWhiteBox` ES Module 图在浏览器链接阶段直接 404 失败；
  2. `LittleWhiteBox-3.1.7/modules/reply-progress/host.js` 导入 `../../../../../events.js`（`/scripts/events.js`），与 `runtime.js` 导入的 `/st-runtime/vendor/st/events.js` 在浏览器 ESM 缓存中因 URL 不同会产生两个独立的 `EventEmitter` 实例，导致事件无法互通；
  3. 5 个扩展静态导入的 19 个具名导出（`script.js` 的 `streamingProcessor` / `callPopup` / `processCommands` / `setCharacterId` / `setCharacterName` / `setExternalAbortController` / `setSendButtonState` / `doNavbarIconClick`；`world-info.js` 的 `originalWIDataKeyMap` / `onWorldInfoChange` / `updateWorldInfoSettings`；`utils.js` 的 `getFileText` / `getSortableDelay` / `extractTextFromOffice`；`RossAscends-mods.js` 的 `shouldSendOnEnter`；`PromptManager.js` 的 `INJECTION_POSITION`；`group-chats.js` 的 `is_group_generating`；`openai.js` 的 `settingsToUpdate`；`custom-request.js` 的 `ChatCompletionService.presetToGeneratePayload`）此前缺失，在原生 ESM 静态链接阶段会抛出 `SyntaxError: The requested module does not provide an export named ...`。
- **修复措施**：补齐全部 2 个模块文件与 19 个具名导出，并在 `vendor/st/events.js` 中使用 `globalThis.__rikkaSharedEventSource` 保证跨 URL 单例一致；同时清理 `world-info.js` 中重复的 `delayUntilRecursion` 字面量键。
- **复测状态**：全量 5 扩展 ESM 静态扫描达到 `Missing files: 0, Missing exports: 0`，并通过 Node 实测。

### Finding R2-03（目录别名不匹配）：GitHub 发行版/分支 ZIP 解压目录带版本号或 `-main` 后缀导致硬编码路径 404
- **严重度**：High
- **位置**：
  - `app/src/main/java/me/rerere/rikkahub/data/st/extensions/TavernExtensions.kt`
  - `app/src/main/assets/st-runtime/runtime.js`
- **根因分析**：用户从 GitHub 下载的扩展包（如 `ST-Prompt-Template-1.16.zip`、`JS-Slash-Runner-main.zip`）解压后目录名带有 `-1.16` 或 `-main` 后缀，但扩展源码内硬编码请求 `/scripts/extensions/third-party/ST-Prompt-Template/dist/ejs.workers.js` 或 `renderExtensionTemplateAsync('third-party/ST-Prompt-Template', 'settings')`，精确目录名匹配会返回 404。
- **修复措施**：在 `TavernExtensions.kt` 新增 `normalizeExtensionFolderAlias(folder)`，并在 `resolveExtensionFile` 与 `runtime.js` `getExtensionManifest` 中支持同别名目录自动回退（同时严格保留 `canonicalBase` 越界防护）。
- **复测状态**：已在 `TavernExtensionsTest.kt` 中增加专项测试并验证通过。

### Finding R2-04：`TavernJsBridge.getVersion()` 硬编码返回 `"1.18.0"`
- **严重度**：Medium
- **位置**：`app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt:1381`
- **根因分析**：`TAVERN_ST_COMPAT_VERSION` 已升至 `"1.19.0"`，但 `@JavascriptInterface fun getVersion()` 仍返回 `"1.18.0"`。
- **修复措施**：改为返回 `TAVERN_ST_COMPAT_VERSION` (`"1.19.0"`)。
- **复测状态**：已修复并通过 `st119-migration-and-5plugins.test.mjs` Clue 1 验证。
