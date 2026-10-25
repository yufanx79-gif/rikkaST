# rikkaST 10-22-16 Independent Second-Pass Review & Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline execution in this harness; subagent dispatch tool `invoke_agent` is not exposed in the active tool declarations) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Independently audit `10-22-16.zip`, fix all 8 confirmed code/test/metadata defects (`F-01` through `F-08`) with strict RED → GREEN TDD verification, reconcile all reports and manifests truthfully, and package `10-22-16-reviewed.zip`.

**Architecture:** Keep `10-22-16.zip` and its initial clean extraction immutable. Work in `/tmp/c_4ce2e51aba38f935/worktree_10_22_16`. For every confirmed defect, write or run a failing behavioral test (RED), apply the minimal root-cause fix in production code, verify GREEN on the targeted test and full suite, and commit each task cleanly.

**Tech Stack:** Kotlin, Jetpack Compose, Room (`v29..v32`), DataStore Preferences (`v1..v5`), Android WebView / JavaScript ESM (`st-runtime`), Node.js test runner (`node:test`), Python 3 (`sqlite3`).

**Spec:** `/working_dir/c_4ce2e51aba38f935/work/extract_review_plan/REVIEW_PLAN.md` and `ACCEPTANCE_MATRIX.csv`

## Global Constraints

- Do not promise "zero bugs." Practical criterion: zero unresolved Critical/High findings, all host-executable tests pass from a clean extraction, and all environment-blocked gates (`Gradle`, `adb install -r`, APK signing comparison) are plainly marked `BLOCKED` / `NOT RUN`.
- Preserve `applicationId = "me.rerere.rikkahub.st"`, `versionName = "2.5.9"`, `versionCode = 241`, Room schema `32`.
- Never advertise the historical `2.5.7` APK in active `update.json` `downloads` when `artifactStatus` is `SOURCE_ONLY_PENDING_SDK_BUILD`.
- Never include `.superpowers/` transient progress files in the archive or tracked inventory.
- Never leak API keys, tokens, or credentials in logs or reports.

## Review Focus

1. Clean-extraction Node suite (`node --test scripts/*.test.mjs`) must pass 100% without relying on excluded `.superpowers/` files or mutating tracked files with wall-clock timestamps.
2. `UpdateChecker.kt`, `UpdateCard.kt`, and `update.json` must never offer a stale `2.5.7` binary as `2.5.9` (`versionCode = 241`).
3. `TavernChatJsonCache.NodeCacheKey` must invalidate whenever any non-selected swipe, alternate greeting text, `send_date`, role, or message part changes, with zero 32-bit hash-collision staleness.
4. `runtime.js` `handleCorsProxyFetch` and `TavernRuntimeManager.kt` `dispatchProxyHttpRequestAsync` must block loopback, RFC1918 private, link-local, metadata (`169.254.169.254`), IPv6 loopback/ULA/mapped-IPv4, userinfo URLs, cross-origin sensitive header leaks on redirects, redirect-to-private SSRF, and oversized responses.
5. Both Kotlin (`RegexScriptEngine.kt`) and JS (`engine.js`, `utils.js`) regex engines must protect against catastrophic backtracking at any perilous input length (including < 4096 chars and nested/alternation patterns) with killable/bounded execution, and implement the full parity surface.

---

### Task 1: Fix Upstream Inventory / `.superpowers` Exclusion Contradiction & Deterministic Risk Report (`F-01`, `F-08a`)

**Files:**
- Modify: `audit/10-22-0/upstream-path-inventory.csv`
- Modify: `audit/upstream/changed-files.csv`
- Modify: `audit/10-22-0/base-only-inventory.csv`
- Modify: `tools/upstream/verify-upstream-inventory.mjs`
- Modify: `tools/upstream/generate-upstream-risk-report.mjs`
- Modify: `scripts/merge-10-22-0-verification.test.mjs`

- [ ] **Step 1: Verify RED failure on `node --test scripts/merge-10-22-0-verification.test.mjs`**
- [ ] **Step 2: Remove `.superpowers/sdd/02_MASTER_PLAN/progress.md` from inventory CSVs, add explicit `.superpowers/` exclusion assertion in `verify-upstream-inventory.mjs`, and make `generate-upstream-risk-report.mjs` deterministic**
- [ ] **Step 3: Run `node --test scripts/merge-10-22-0-verification.test.mjs` and verify GREEN**
- [ ] **Step 4: Commit Task 1**

---

### Task 2: Stop Advertising Stale 2.5.7 APK in `update.json`, `UpdateChecker.kt`, and `UpdateCard.kt` (`F-02`)

**Files:**
- Modify: `update.json`
- Modify: `app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/ui/UpdateCard.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/utils/UpdateCheckerValidationTest.kt`
- Modify: `scripts/st119-contract-and-release-meta.test.mjs`

- [ ] **Step 1: Write failing test in `scripts/st119-contract-and-release-meta.test.mjs` and `UpdateCheckerValidationTest.kt` asserting `SOURCE_ONLY_PENDING_SDK_BUILD` cannot expose active `downloads` or allow downloading a mismatched/historical APK**
- [ ] **Step 2: Run `node --test scripts/st119-contract-and-release-meta.test.mjs` and verify RED**
- [ ] **Step 3: Move historical 2.5.7 metadata to `historicalArtifacts` in `update.json` (`downloads: []`), add `artifactStatus`/`status`/`targetVersionCode` validation & `hasAvailableDownloads` to `UpdateChecker.kt`, and pass `info` + guard empty/source-only downloads in `UpdateCard.kt`**
- [ ] **Step 4: Run `node --test scripts/st119-contract-and-release-meta.test.mjs` and verify GREEN**
- [ ] **Step 5: Commit Task 2**

---

### Task 3: Fix `TavernChatJsonCache.NodeCacheKey` Stale Swipe / Alternate Greeting / Field Invalidation (`F-04`)

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernChatJson.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/service/CancelAndLongFloorTest.kt`
- Modify: `scripts/task-fg-longfloor-and-cancel.test.mjs`

- [ ] **Step 1: Write failing behavioral test in `scripts/task-fg-longfloor-and-cancel.test.mjs` and `CancelAndLongFloorTest.kt` for mutating a non-selected swipe, mutating an alternate greeting, mutating `send_date`/`isUser`, and hash-collision resistance**
- [ ] **Step 2: Run `node --test scripts/task-fg-longfloor-and-cancel.test.mjs` and verify RED**
- [ ] **Step 3: Update `TavernChatJsonCache.NodeCacheKey` in `TavernChatJson.kt` to include full `swipesList`, `currentText`, `sendDate`, and `isUser` so every serialized field participates in exact equality and hash fingerprinting**
- [ ] **Step 4: Run `node --test scripts/task-fg-longfloor-and-cancel.test.mjs` and verify GREEN**
- [ ] **Step 5: Commit Task 3**

---

### Task 4: Harden WebView CORS Proxy Against SSRF, Redirect-to-Private, Header Leaks & Unbounded Responses (`F-03`)

**Files:**
- Modify: `app/src/main/assets/st-runtime/runtime.js`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt`
- Modify: `scripts/task-dh-webview-iphonie-imggen.test.mjs`

- [ ] **Step 1: Write failing security tests in `scripts/task-dh-webview-iphonie-imggen.test.mjs` testing IPv4/IPv6 loopback, RFC1918 private ranges, link-local/metadata (`169.254.169.254`), `localhost` aliases, URL userinfo, redirect-to-private SSRF, cross-origin `Authorization` stripping on redirects, and response byte cap**
- [ ] **Step 2: Run `node --test scripts/task-dh-webview-iphonie-imggen.test.mjs` and verify RED**
- [ ] **Step 3: Implement `isForbiddenProxyTargetUrl` / `validateProxyTargetAndResolve` in `runtime.js` and `TavernRuntimeManager.kt` with manual redirect validation (`instanceFollowRedirects = false`, max 5 hops, cross-origin auth header stripping, DNS/IP private-range rejection, and 15 MB response cap) and return HTTP 501 for desktop-only `/api/sd/comfy/*` unless explicitly configured**
- [ ] **Step 4: Run `node --test scripts/task-dh-webview-iphonie-imggen.test.mjs` and verify GREEN**
- [ ] **Step 5: Commit Task 4**

---

### Task 5: ReDoS Protection at Any Input Length & Full Kotlin/JS Regex Parity (`F-05`)

**Files:**
- Modify: `app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js`
- Modify: `app/src/main/assets/st-runtime/st-compat/utils.js`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngineTest.kt`
- Modify: `scripts/task-e-regex-parity.test.mjs`

- [ ] **Step 1: Write failing tests in `scripts/task-e-regex-parity.test.mjs` (running adversarial patterns in a killable worker/child process with hard timeout) for short pathological inputs (`"a".repeat(32) + "c"`), nested groups `((a+))+b`, overlapping alternation `(a|aa)+b`, `utils.js` `replaceCompatString` / `extractInlineRegexFlags`, and `RegexScriptEngine.kt` bounded step/time `CharSequence` guard**
- [ ] **Step 2: Run `node --test scripts/task-e-regex-parity.test.mjs` and verify RED**
- [ ] **Step 3: Implement structural ReDoS detector + input-length-aware guard in `engine.js`, `utils.js`, and `RegexScriptEngine.kt`, plus `BoundedRegexCharSequence` step/time budget and `stripUnmatchedBoundaryWrappers` / inline flag extraction in `RegexScriptEngine.kt`**
- [ ] **Step 4: Run `node --test scripts/task-e-regex-parity.test.mjs` and verify GREEN**
- [ ] **Step 5: Commit Task 5**

---

### Task 6: DataStore Corrupt Provider Backup, Cancellation Epoch Fencing, `WebViewPerfMode` Differentiation & Room SQL Parity (`F-06`, `F-07`, `F-08b`)

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/data/datastore/ProviderResilienceAndMigrationTest.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt`
- Modify: `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/Migration_29_32_Test.kt`
- Modify: `scripts/verify_room_migration_sqlite.py`
- Modify: `scripts/task-abc-upgrade-migration-provider.test.mjs`
- Modify: `scripts/task-fg-longfloor-and-cancel.test.mjs`

- [ ] **Step 1: Write failing tests in `scripts/task-abc-upgrade-migration-provider.test.mjs` and `scripts/task-fg-longfloor-and-cancel.test.mjs`**
- [ ] **Step 2: Run targeted tests and verify RED**
- [ ] **Step 3: Implement `PROVIDERS_CORRUPT_BACKUP` & `sanitizeProviderLogSummary` in `PreferencesStore.kt`, `CancellationException` rethrow & `generationEpoch` fencing in `ConversationSession.kt`/`ChatService.kt`, default safety pool & distinct `WebViewPerfMode` behavior in `MessageHtmlBlock.kt`, and production SQL extraction/FK/FTS verification in `verify_room_migration_sqlite.py` + `Migration_29_32_Test.kt`**
- [ ] **Step 4: Run targeted and full test suites and verify GREEN**
- [ ] **Step 5: Commit Task 6**
