# GitHub 提交与推送指南（GITHUB_PUSH_GUIDE）

> **安全纪律说明**：AI 工作流严格遵守“不配置远端、不触碰 GitHub、不自动 push/merge/release”的约束。请在您完成 Windows 本机编译与冒烟验证后，按以下步骤将代码推送到您的 GitHub 仓库。

## 1. 变更概况
- **修改源码与测试文件数**：24 个核心源码与测试文件（另有 `docs/ai-work/` 与根目录交付文档）
- **核心改动范围**：
  - `app/src/main/java/me/rerere/rikkahub/data/ai/`：`GenerationLoop.kt`、`ChatToolFactory.kt`、`PromptInjectionTransformer.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/export/`：`ExportSerializer.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/model/`：`WorldInfoDecorators.kt`
  - `app/src/main/java/me/rerere/rikkahub/data/st/`：`TavernRuntimeManager.kt`、`StSlashExecutor.kt`、`StArchiveImporter.kt`
  - `app/src/main/java/me/rerere/rikkahub/service/`：`ChatService.kt`
  - `app/src/main/java/me/rerere/rikkahub/ui/`：`MessageHtmlBlock.kt`、`AssistantImporter.kt`、`ChatList.kt`、`ChatPage.kt`、`ChatSlashHost.kt`、`ConversationExport.kt`、`PromptPage.kt`、`QuickMessagesPage.kt`、`QuickMessagesVM.kt`
  - `app/src/main/assets/st-runtime/`：`runtime.js`、`panel.html`
  - `app/src/test/java/me/rerere/rikkahub/`：4 个测试类新增契约与回归单测

## 2. Push 前本机自查清单（Pre-Push Checklist）
1. **确认本机 Gradle 编译与单测通过**（命令详见 `BUILD_HANDOFF.md`）：
   - `.\gradlew.bat compileDebugKotlin compileDebugUnitTestKotlin`
   - `.\gradlew.bat testDebugUnitTest`
   - `.\gradlew.bat assembleDebug`
2. **检查敏感文件未被暂存（Working Tree Hygiene）**：
   ```powershell
   git status --ignored
   ```
   确认以下文件**不在**待提交列表中：
   - `local.properties`
   - `keystore.properties`
   - `*.jks` / `*.keystore`
   - `.gradle/` 或 `app/build/`
   - 任何测试用真实角色卡隐私数据或 API Key

## 3. 建议 Git Commit 标题与正文

### 方案 A：单次汇总提交（Squash / Single Commit）
```text
fix(st-compat): resolve 12 Kotlin compile blockers and complete ST 1.18.0 dataflow, slash modal, function tool, and WebView pipelines

- Fix all 12 P0 static compile blockers (KOT-01..KOT-12) across PromptInjectionTransformer, ExportSerializer, ChatService, ChatList, ChatSlashHost, WorldInfoDecorators, StSlashExecutor, ChatPage, and AssistantImporter
- Preserve .charx/.byaf extensions in StArchiveImporter and rewrite embeded:// asset URIs to local /charx_assets/ endpoints or data URIs
- Parse and apply SillyTavern preset sampler parameters, stopping strings, logit_bias, wrap_in_quotes, start_reply_with, and prompt_post_processing in GenerationLoop
- Wire SillyTavern World Info (.json) export in PromptPage and SillyTavern chat (.jsonl) export in ConversationExport
- Implement interactive Compose AlertDialogs for /popup, /input, and /buttons via SlashModalRequest with headless fallback
- Implement real host mutations for /api-url, /proxy, /persona-lock, /persona-sync, /qr-context*, /extension-*, /bg, /theme, /context, and /forcesave
- Wire SillyTavern.registerFunctionTool and STscript /tools-register into native ChatToolFactory and GenerationLoop
- Expand WebView asset MIME mappings, add no-referrer meta to panel.html, improve looksLikeInteractiveHtml, and add PanelMountState in-place remount for scriptless HTML updates
- Add unit tests covering delayOverrideEntries, .charx URI rewriting, preset post-processing/customBodies, slash tools/modals, and PanelMountState
```

### 方案 B：按模块分步提交（推荐用于清晰 Code Review）
1. **Commit 1 (P0 Compile Gate)**：
   `fix(core): resolve 12 Kotlin compile blockers (KOT-01..KOT-12)`
2. **Commit 2 (Data Flows & Export)**：
   `feat(st-dataflow): wire charx asset rewriting, preset sampler/post-processing, WI/JSONL export, and QR v2 triggers`
3. **Commit 3 (STscript, Slash & Function Tools)**：
   `feat(st-runtime): add interactive slash modals, host state commands, and registerFunctionTool LLM bridge`
4. **Commit 4 (WebView & Unit Tests)**：
   `improve(webview,test): add scriptless in-place remount state machine, expand asset MIMEs, and add unit tests`
