# 10-22-0 Architecture Integration Decisions (`docs/architecture/10-22-0-integration-decisions.md`)

## 1. Boundary Between RikkaHub 3.1.0 Native Core, ST 1.19 Runtime, and Node Host

1. **Native Compose & Kotlin Domain Core (Authoritative for App State)**:
   - Jetpack Compose (`MaterialExpressiveTheme`, `CardGroup`, `ConnectedChoiceButtons`, `PagerTabs`) is the sole first-party UI surface.
   - `ConversationSession` (managed by `ChatService` and `ConversationSessionManager`) is the single source of truth for in-memory active conversation state. All mutations go through `session.update { ... }` followed by `flush()`, while preserving rikkaST's synchronous `/trigger` and `insertMessage` hooks.
   - `ConversationConfig` snapshots per-conversation settings (`chatModel`, `reasoningLevel`, `search`, `mcpServers`, `workspace`, `skills`) upon persistence; `Settings.getAssistantOf(conversation)` and `Settings.getChatModelOf(conversation)` resolve effective settings.
2. **Execution Runtime Boundaries (Android WebView JS vs QuickJS vs Node.js)**:
   - **Android WebView (`app/src/main/assets/st-runtime/**`)**: Executes SillyTavern 1.19.0 browser-side extensions, slash commands, EJS/Prompt-Template scripts, ST-iPhonie UI, and HTML message panels (`MessageHtmlBlock`). Isolated behind `TavernRuntimeManager` and `PanelBridge` with explicit `onPageFinished` vs `onPanelRuntimeReady` milestones.
   - **QuickJS (`io.github.dokar3:quickjs-kt` / `com.whl.quickjs`)**: Executes lightweight host-side `JavascriptTool` (`data/ai/tools/local/JavascriptTool.kt`) for native AI tool calls without WebView overhead.
   - **Node.js Companion & Virtual Shell (`NodeRuntimeManager`, `TavernNodeServiceManager`, `ProotShellRunner`)**:
     - `TavernNodeServiceManager` runs the loopback-only (`127.0.0.1`) authenticated Node companion server (`node-host/server-bootstrap.cjs`) for server-side ST plugins (`littlewhitebox-server`).
     - `ProotShellRunner` mounts and exposes the managed Node.js runtime (`NodeRuntimeManager`) into `/usr/local/bin/node` (and `PATH`) inside the per-workspace PRoot virtual Linux environment so `node --version` and JS script execution work natively in assistant/user workspace shells without sharing writable assistant directories.
3. **Provider Registry, OAuth Account Credentials & Quota Architecture**:
   - Adopt RikkaHub 3.1.0's `ProviderSetting` hierarchy (`ProviderSetting.OpenAI`, `ProviderSetting.Google`, `ProviderSetting.Claude` extending `ProviderSetting.ApiKey`, and `ProviderSetting.ChatGPT`, `ProviderSetting.Grok` extending `ProviderSetting.Account`).
   - Backward-compatible `BalanceOption` support is preserved alongside `QuotaQuery` and `ProviderQuota` (`QuotaQueryRunner`) so neither existing rikkaST settings nor 3.1.0 callers break.
   - `ProviderManager(client, context, accountCredentials)` registers all 5 provider families (`openai`, `google`, `claude`, `chatgpt`, `grok`).
4. **Datastore Settings Modularization**:
   - Adopt 3.1.0's modularized `data/datastore/` files (`Settings.kt`, `SettingsFields.kt`, `SettingsExt.kt`, `SettingsNormalization.kt`, `DefaultSettings.kt`, `PreferencesStore.kt`, `PreferenceStoreV5Migration.kt`) while embedding all rikkaST-specific fields (`developerMode`, `webviewPerfMode`, `macroGlobalVariables`, `macroChatVariables`, `persona*`, `authorsNote*`, `messageStyle*`, `tavern*`) into `Settings.kt` and `SettingsFields.kt` with full default values and JSON migration support.
5. **Room Database Schema v32**:
   - Keep `AppDatabase.version = 32`, which includes all 7 new RikkaHub 3.1.0 v31 tables (`memory_file`, `memory_consolidation`, `media_creation_session`, `media_creation_node`, `media_creation_record`, `translation_history`, `usage_record`) plus `ConversationEntity.config` AND all rikkaST tables/columns (`ConversationEntity.st_parent_conversation_id`, `MemoryEntity`, `knowledge_sources`, `knowledge_chunks`, `knowledge_source_assistants`).
   - Provide explicit non-destructive migrations (`Migration_28_29`, `Migration_29_32`, `Migration_30_31`, `Migration_30_32`, `Migration_31_32`) with `fallbackToDestructiveMigration` strictly forbidden.
