# 上游 RikkaHub 3.1.0 与 SillyTavern 1.19.0 兼容性及追踪审计报告 (UPSTREAM-COMPATIBILITY.md)

## 1. 上游锁定证据 (`upstream-lock/`)

本仓库通过 `upstream-lock/rikkahub.json` 与 `upstream-lock/sillytavern.json` 固定已知上游基准，并由 `node tools/st-sync/verify-lock.mjs`、`node tools/upstream/check-tags.mjs` 和 `node tools/upstream/verify-upstream-inventory.mjs` 执行自动化门禁校验：

| 上游项目 | 锁定文件 | 固定版本 / Tag | 固定 Commit / 规模 | 校验脚本与结果 |
| --- | --- | --- | --- | --- |
| **RikkaHub** | `upstream-lock/rikkahub.json` | `3.1.0` (`versionCode: 310`) | `eeef60b22ba24383daf51f3c6c2e5b75bbab6bba` (1,588 files) | `node tools/upstream/check-tags.mjs` -> **PASS** |
| **SillyTavern** | `upstream-lock/sillytavern.json` | `1.19.0` | `205` 个公开 ESM 模块 (`app/src/main/assets/st-runtime/st-compat/`) | `node tools/st-sync/verify-lock.mjs` -> **PASS** |

> **诚实边界说明**：当前工作环境仅包含用户提供的两个候选交付包（`10-22-16-reviewed.zip` 与 `10-22-16-reviewed (1).zip`），不包含独立的原始上游 RikkaHub 3.1.0 Git 仓库完整克隆。因此对上游 3.1.0 的核验严格基于包内已有的锁定清单（`audit/10-22-0/upstream-path-inventory.csv`、`audit/upstream/changed-files.csv`、`app/schemas/.../32.json`）以及对本仓真实源码路由、状态、存储、调用链的直接检查，不声称在本次会话中重新逐文件对比了外部原始上游仓库。

---

## 2. 上游追踪资产与自动化门禁核查

我们对包内的全部上游追踪脚本、清单与门禁工具进行了实跑检验与修复：
1. **`tools/upstream/verify-upstream-inventory.mjs`**：
   - 实跑结果：`[verify-upstream-inventory] PASS: total=2773, upstream=1588, stOnly=1185, unresolved=0, missingOnDisk=0, excluded=3, statusCounts={"UPSTREAM_TEST_OR_TOOL":354,"ADAPT":320,"ST_ONLY":1185,"INTENTIONALLY_EXCLUDED":3,"IDENTICAL":843,"PORT/UNCHANGED":64,"REIMPLEMENT_EQUIVALENT":2,"GENERATED":2}`
   - 验证了全部 1,588 个上游路径（除 3 个显式排除的 CI/自动构建工作流外）与 1,185 个 `ST_ONLY` 路径在磁盘上 100% 真实存在，且 `.superpowers/` 等瞬态目录绝不污染追踪 CSV。
2. **`tools/upstream/generate-upstream-risk-report.mjs`**：
   - 修复为确定性输出（使用 `update.json` 的 `publishedAt` 且仅在内容变化时写入 `audit/upstream/upstream-risk-report.md`），避免每次运行因系统时钟变化产生脏 diff；同时强制校验 `audit/upstream/unmapped-items.md` 中 `UNMAPPED_COUNT: 0` 与 `MISSING_USER_FACING_COUNT: 0`。
3. **`tools/upstream/check-db-schema.mjs`**：
   - 实跑结果：`[check-db-schema] OK: version=32, tables= 18`，且验证 `DatabaseModule.kt` 未启用破坏性回退 `fallbackToDestructiveMigration`。

---

## 3. RikkaHub 3.1.0 核心功能融合核验（路由、状态、存储与错误处理）

经逐文件核查源码调用链（并通过 `scripts/upstream-310-fusion.test.mjs` 与 `scripts/task-abc-upgrade-migration-provider.test.mjs` 自动验证）：

| 3.1.0 功能域 | 关键源码文件 | 路由 / UI / 存储 / 错误处理核验状态 |
| --- | --- | --- |
| **Provider / Auth / ChatGPT OAuth & 额度查询 (`QuotaQuery`)** | `ProviderSetting.kt`, `ProviderManager.kt`, `AccountAuth.kt`, `ChatGptOAuthService.kt`, `QuotaQueryRunner.kt`, `PreferenceStoreV5Migration.kt`, `PreferencesStore.kt` | **已核实真实接入**：支持 OpenAI OAuth 账号登录、JWT 载荷解析与脱敏掩码、`QuotaQueryRunner` 模板化额度查询；`PreferenceStoreV5Migration` 将旧版 `balanceOption`（含 `openai`、`openai-compatible`、`custom` 及隐式 OpenAI 记录）平滑迁移为 `quotaQuery`；`decodeProvidersResilient` 遇到单条损坏提供商时自动跳过、将原始 JSON 备份至 `PROVIDERS_CORRUPT_BACKUP` 并通过 `sanitizeProviderLogSummary` 脱敏日志。 |
| **结构化记忆文件 (`memory_file` / `memory_consolidation`)** | `MemoryFileDAO.kt`, `MemoryRepository.kt`, `MemoryContentGuard.kt`, `MemoryPage.kt`, `Migration_29_32.kt` | **已核实真实接入**：`RouteActivity.kt` 与 `SettingPage.kt` 挂载 `MemoryPage`；`Migration_29_32.kt` 将旧 `MemoryEntity` 迁移至 `memory_file` (`profile.md`) 并保留 `MemoryEntity` 兼容酒馆记忆；`MemoryContentGuard` 拦截私钥/API Key/PII 写入。 |
| **多媒体生成工作室 (`media_creation_*` / `mediagen` / `videogen`)** | `MediaCreationDao.kt`, `MediaStudioPage.kt`, `ImgGenPage.kt`, `ImgGenVM.kt` | **已核实真实接入**：Room Schema v32 建立 `media_creation_session` / `node` / `record` 三张表及级联外键；`ChatDrawer.kt` 与 `RouteActivity.kt` 挂载入口；`ImgGenVM.kt` 提供 `classifyImageGenerationError` 与 `normalizeImageGenSize` 并与酒馆生图桥互通。 |
| **翻译历史 (`translation_history`) 与统计 (`usage_record`)** | `TranslationHistoryDao.kt`, `UsageRecordDao.kt`, `UsageStatsSheet.kt`, `Migration_29_32.kt` | **已核实真实接入**：Room Schema v32 建立 `translation_history` 与 `usage_record`；`Migration_29_32.kt` 从历史 `message_node.messages` JSON（含多 swipe 分支）回填 token 统计。 |
| **Linux / PRoot / Node.js 工作区 (`workspace`)** | `WorkspaceManager.kt`, `ProotShellRunner.kt`, `RootfsPatcher.kt`, `NodeRuntimeManager.kt`, `TavernNodeServiceManager.kt` | **已核实真实接入**：专用共享目录 `workspaces/shared_tavern_node/linux/usr/local/node`（`resolveSharedNodeDir` 严禁从任意助手工作区选取）；`RootfsPatcher` 写入 `/etc/profile.d/rikka-node.sh` 保持登录 Shell `PATH` 含 `/usr/local/node/bin` 并创建 `/usr/local/bin/{node,npm,npx}` 软链；提供内置 JVM 伴生服务 `BuiltinNodeCompanionServer` 与 PRoot Node 守护进程双引擎。 |
| **思考强度滑杆 (`ReasoningSliderSpec`)** | `ReasoningPicker.kt`, `LevelPicker.kt`, `ReasoningSliderSpecTest.kt` | **已核实真实接入**：完整保留 rikkaST Kelivo 风格滑杆、拖拽、弹簧吸附与触觉反馈，同时兼容 3.1.0 `ReasoningEffort` 档位映射。 |

---

## 4. SillyTavern 1.19.0 兼容层与 5 大插件核验

1. **版本门禁一致性**：`runtime.js`、`st-compat/script.js` 与 `TavernRuntimeManager.kt` 统一锁定 `1.19.0`。
2. **205 个 `st-compat` ESM 模块**：通过 `node scripts/esm-graph-validator.mjs` 全图静态链接校验（0 缺失文件、0 缺失导出），涵盖 `MacroRegistry`、`MacroEngine`（支持 `{{//}}...{{///}}` 作用域注释与 `delayArgResolution`）、`MessageFormatter` 3 阶段管线、`PromptManager`、`WorldInfo`、`AccountStorage`、正则引擎（`engine.js` + `RegexScriptEngine.kt`）与 MVU。
3. **5 大核心插件实跑验证**（挂载外部 `ALL_NEEDS_DIR` 时 20/20 PASS）：
   - `ST-Prompt-Template-1.16`（v1.16.3.0）：EJS 模板求值与 `GENERATE_AFTER_COMBINE_PROMPTS` 消息重写。
   - `JS-Slash-Runner-main`（酒馆助手 v4.11.3）：`TavernHelper`、`MacroRegistry` 与事件同步。
   - `st-memory-enhancement-2.2.0`：表格 `getColumnLetter`、`replaceUserTag`、`easeInOutCubic` 补齐与快速设置挂载。
   - `LittleWhiteBox-3.1.7`：`registerGenerateInterceptor` 优先级排序拦截与伴生服务端 `/api/plugins/littlewhitebox-server/*` 路由。
   - `ST-iPhonie-main`（v0.8.15）：悬浮窗幂等打开（保留 `.floating` 类防闪烁关闭循环）、双通道 TTS 隔离、`/proxy/` SSRF 防护与多画幅生图。

---

## 5. 尚未核实项与未来上游更新门禁流程

### 尚未核实项（需外部环境支持）
1. **Android 编译与设备端 UI/WebView/PRoot 实测**：受限于当前离线无 SDK 容器，Gradle 编译（`testDebugUnitTest` / `assembleDebug`）、Room `MigrationTestHelper` 仪器测试、以及 Android 真机 WebView 渲染帧率与 PRoot SELinux 行为尚未在真机执行。
2. **原始上游 3.1.0 Git 仓库逐文件重扫**：当前仅基于包内已有锁文件与清单核验；在接入包含原始上游 Git 远端的环境后，应运行 `scripts/upstream/check-upstream-commit.ps1` 与 `scripts/upstream/probe-merge.sh` 进行远端树哈希复核。

### 未来每次上游更新的强制门禁流程
1. **先生成差异清单**：更新 `upstream-lock/rikkahub.json` 或 `upstream-lock/sillytavern.json` 前，必须先在独立分支运行差异清单生成脚本，更新 `audit/10-22-0/upstream-path-inventory.csv` 与 `audit/upstream/changed-files.csv`。
2. **执行自动化清单与 Schema 门禁**：
   - `node tools/upstream/verify-upstream-inventory.mjs`（确保 `unresolved=0`、`missingOnDisk=0`）
   - `node tools/upstream/check-db-schema.mjs`
   - `node tools/st-sync/verify-lock.mjs`
   - `node tools/upstream/generate-upstream-risk-report.mjs`
3. **执行全量行为回归与对抗测试**：
   - `python3 scripts/verify_room_migration_sqlite.py`
   - `node --test scripts/*.test.mjs`（含正则 ReDoS 子进程超时测试、WebView 池上限测试、取消与重发世代隔离测试）
   - `./gradlew testDebugUnitTest assembleDebug`
4. **未全部通过前严禁合并或发布 `update.json`**。
