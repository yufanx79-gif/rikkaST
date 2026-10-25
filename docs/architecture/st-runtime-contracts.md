# SillyTavern 1.19.0 Runtime & Bridge Contracts (`10-22-0`)

## 1. Host Bridge Methods (`window.RikkaBridge` & `window.RikkaPanel`)

| Bridge Interface | Method Signature | Purpose & Security Boundary | Implementation | Automated Verification |
|---|---|---|---|---|
| `RikkaBridge` | `stopGeneration()` | Aborts active Kotlin `ChatService` generation and JS `externalAbortController` / active fetch streams | `TavernRuntimeManager.kt` (`TavernJsBridge.stopGeneration`) | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `RikkaBridge` | `startChatCompletionStream(reqId, bodyJson)` | Starts SSE or non-streaming `/api/backends/chat-completions/generate` pipeline | `TavernRuntimeManager.kt` | `scripts/st-runtime-fixes.test.mjs` |
| `RikkaBridge` | `cancelChatCompletionStream(reqId)` | Cancels in-flight chat completion stream job | `TavernRuntimeManager.kt` | `scripts/st-runtime-fixes.test.mjs` |
| `RikkaBridge` | `chatCompletionStatus()` | Returns available model IDs and active model ID for `/api/backends/chat-completions/status` | `TavernRuntimeManager.kt` | `scripts/st-runtime-fixes.test.mjs` |
| `RikkaBridge` | `onRuntimeReady()` | Signals that `runtime.js` ES module graph and `window.TavernHelper` / `window.SillyTavern` have finished linking | `TavernRuntimeManager.kt` | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `RikkaBridge` | `updateFloatingWindows(json)` | Publishes detected floating windows (`ST-iPhonie`, `st-memory-enhancement`, `LittleWhiteBox`, card HUDs) | `TavernRuntimeManager.kt` | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `RikkaBridge` | `closeFloatingWindow(id, hideForChat)` | Closes active floating window overlay and optionally suppresses for current conversation | `TavernRuntimeManager.kt` | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `RikkaPanel` | `onPanelRuntimeReady()` | Signals that `panel.html` ES modules and `window.__rikkaPanelMount` are ready (distinct from `onPageFinished`) | `MessageHtmlBlock.kt` (`PanelBridge`) | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `RikkaPanel` | `panelResize(height)` | Reports measured DOM height in CSS px/dp to `MessageHtmlBlock` with GPU texture cap (`<= 4096px`) | `MessageHtmlBlock.kt` (`PanelBridge`) | `scripts/st-runtime-fixes.test.mjs` |
| `RikkaPanel` | `getPendingHtml()` / `getPendingKey()` | Pull-based retrieval of pending HTML and message key to avoid large JS string literal escaping issues | `MessageHtmlBlock.kt` (`PanelBridge`) | `scripts/phase5-9-integration.test.mjs` |
| `RikkaPanel` | `onPanelError(message)` | Surfaces JS/DOM mount errors to `PanelLoadingPlaceholderCard` | `MessageHtmlBlock.kt` (`PanelBridge`) | `scripts/phase5-9-integration.test.mjs` |

## 2. Event Bus & Lifecycle Events (`tavern_events` & `eventSource`)

| Event Constant | Event String | Contract & Listener Semantics | Verification |
|---|---|---|---|
| `tavern_events.MESSAGE_UPDATED` | `"message_updated"` | Emitted by `setChatMessages` when message content/variables change | `scripts/runtime-module-linking.test.mjs` |
| `tavern_events.MESSAGE_SWIPED` | `"message_swiped"` | Emitted by `setChatMessages` when swipe ID changes | `scripts/runtime-module-linking.test.mjs` |
| `tavern_events.CHAT_CHANGED` | `"chat_id_changed"` | Emitted when conversation switches; resets per-chat floating window state | `scripts/st-runtime-fixes.test.mjs` |
| `tavern_events.GENERATION_STARTED` | `"generation_started"` | Emitted before generation begins | `scripts/st-runtime-fixes.test.mjs` |
| `tavern_events.GENERATION_STOPPED` | `"generation_stopped"` | Emitted when `stopGeneration()` aborts active stream | `scripts/webview-stop-and-regex-Regression.test.mjs` |
| `tavern_events.GENERATION_ENDED` | `"generation_ended"` | Emitted upon normal completion of generation | `scripts/st-runtime-fixes.test.mjs` |
| `EventEmitter.prototype.emitAndWait` | Async sequential | Awaits async listeners in `hook_order` sequence across `vendor/st/events.js` and `st-compat/events.js` singleton | `scripts/st-runtime-fixes.test.mjs` |

## 3. Global Runtime Symbols (`runtime.js` & `panel.html`)

| Global Symbol | Provided Surface | Source Module | Verification |
|---|---|---|---|
| `window.SillyTavern` | `getContext()`, `VERSION = "1.19.0"`, `stopGeneration()`, `chat`, `characters`, `extensionSettings` | `app/src/main/assets/st-runtime/runtime.js` | `scripts/st-runtime-fixes.test.mjs` |
| `window.TavernHelper` | JSR / TavernHelper API (`getChatMessages`, `setChatMessages`, `getVariables`, `insertOrAssignVariables`, `substitudeMacros`, `generate`, `triggerSlash`) | `app/src/main/assets/st-runtime/runtime.js` + `shims/chat-shims.js` | `scripts/runtime-module-linking.test.mjs` |
| `window.Mvu` | MagVarUpdate (`getMvuData`, `setMvuData`, `replaceMvuData`, `parseMessage`, `UpdateData`, `events`) | `app/src/main/assets/st-runtime/runtime.js` | `scripts/phase5-9-integration.test.mjs` |
| `window.EJS` / `window.ejs` | Offline bundled EJS template engine for `ST-Prompt-Template` | `app/src/main/assets/st-runtime/vendor/` | `scripts/st-runtime-fixes.test.mjs` |
| `window.jQuery` / `window.$` / `window._` / `window.YAML` / `window.toastr` | Offline bundled jQuery, Lodash, YAML, Toastr shims for third-party ST extensions | `app/src/main/assets/st-runtime/lib.js` | `scripts/st-runtime-fixes.test.mjs` |
| `window.substitudeMacros` | Compatibility alias (preserving ST historical typo) delegating to `substituteParams` | `app/src/main/assets/st-runtime/shims/chat-shims.js` | `scripts/runtime-module-linking.test.mjs` |
