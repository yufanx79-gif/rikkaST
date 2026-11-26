# Chat Scroll & Streaming Performance Fix Implementation Plan

> **For agentic workers:** Executing inline in this session following TDD guard tests and verification-before-completion.

**Goal:** Eliminate historical message scroll jank, streaming IPC storms, and Main-thread blocking in `rikkaST`'s chat page while preserving 100% of WebView full-keepalive interactivity and complete SillyTavern regex/HTML panel rendering.

**Architecture:** Seven surgical, orthogonal fixes outside the `StRegexedText` v251 state machine: (1) eliminate unconditional full-screen `CompositingStrategy.Offscreen` and scroll-state recomposition in `ChatList.kt`; (2) remove Main-thread synchronous `captureFromWebView` from `AndroidView.onRelease`, isolate global Flow subscriptions, fix the 120ms streaming preview debounce bug, and memoize `shouldRenderAsHtmlPanel` in `MessageHtmlBlock.kt`; (3) forward `isHistoricalFloor` in `ChatMessage.kt` `bareContent`; (4) apply quote coloring natively via `SpanStyle` in `Markdown.kt` with an AST `LruCache` and move `Jsoup.parse` off Main thread in `MarkdownNew.kt`; (5) expand `StRegexTextCache` to 512 and cache ReDoS prechecks in `RegexScriptEngine.kt`; (6) guard `stopGenerationForeground()` with a running flag in `ChatService.kt`; (7) dispatch `persistSettings` onto `Dispatchers.IO` in `PreferencesStore.kt`.

**Tech Stack:** Kotlin 2.4, Jetpack Compose, Android WebView, Jsoup, IntelliJ Markdown, JUnit 4.

## Global Constraints
- **Zero regression to WebView keepalive & interactivity**: Never destroy/evict active WebViews prematurely; keep `renderReady` snapshot capture intact.
- **Zero regression to v251 / P0 HTML panel defense**: Do NOT alter `StRegexedText.kt:782-825` sync/async return branches or `StTextContent`'s `loading && panelCandidate` guard.
- **Zero regression to existing guard tests**: Keep `key = { index, item -> item.id }` verbatim in `ChatList.kt` to satisfy `ComposePerformanceGuardTest`.

## Tasks
- [x] **Task 0 (TDD Guard):** Create `ChatScrollPerformanceRegressionGuardTest.kt` locking all 7 performance invariants + v251/P0 HTML panel invariants.
- [ ] **Task 1 (`ChatList.kt`):** Conditional `topFadingEdge` (no offscreen FBO on solid background), `contentType` on `itemsIndexed`, lazy `selectedMessages`, isolated `ChatScrollStateObserverAndJumper`.
- [ ] **Task 2 (`MessageHtmlBlock.kt` & `ChatMessage.kt`):** Remove sync `captureFromWebView` in `onRelease`, isolate `activeKeysFlow`/`queueSnapshot` per-key, fix `remember(messageKey)` streaming debounce, memoize `shouldRenderAsHtmlPanel`, forward `isHistoricalFloor` in `bareContent`.
- [ ] **Task 3 (`Markdown.kt` & `MarkdownNew.kt`):** Native `LocalMarkdownQuoteColor` `SpanStyle` (skipping `CODE_SPAN`/`INLINE_MATH`), `LruCache` for `parseMarkdown`, precompiled `HTML_OR_IMG_PROTECT_REGEX`, background `Jsoup.parse` in `MarkdownNew.kt`.
- [ ] **Task 4 (`StRegexedText.kt` & `RegexScriptEngine.kt`):** Increase `StRegexTextCache.MAX_ENTRIES` to 512; cache ReDoS pattern static checks.
- [ ] **Task 5 (`ChatService.kt` & `PreferencesStore.kt`):** Guard `stopGenerationForeground()` against redundant `startService` IPC; move `persistSettings` to `Dispatchers.IO`.
- [ ] **Task 6 (Verification):** Verify all guard assertions against modified source, check Kotlin syntax/imports/braces, generate unified `.patch` and present deliverable.
