# DIVERGENCE — rikkaST 与官方 RikkaHub 2.5.6 的差异地图

> 本次迁移目标：**以官方 RikkaHub `2.5.6` tag（commit `447bb7e89710d31f1204d7a2973baa19fdbd5b28`）为底座**，在官方新架构上重新叠加 rikkaST 的酒馆与自研功能层。
> 本文取代旧版“基于 Rikka Plus / 合并 upstream master”的合并规则。旧规则不再适用于本分支。

## 0. 基线

| 项 | 值 |
|---|---|
| 官方底座 | `rikkahub/rikkahub` tag `2.5.6`，commit `447bb7e89710d31f1204d7a2973baa19fdbd5b28` |
| 本地迁移前 HEAD | `a94f2363f6e724a9125b6cdb25d1db072d044a88`（`main`，v2.4.6 + README 提交） |
| 迁移方式 | 官方 2.5.6 为基底；以 `2.4.6` 为共同祖先做 rename-aware 三方合并，再逐冲突人工合并 |
| 独立产品身份 | `applicationId = me.rerere.rikkahub.st`，namespace `me.rerere.rikkahub`，继续与官方 App 共存 |
| 数据库 | 本地版本链 28 → **29**；新增 `Migration_28_29`，绝不回退到官方 25 |
| 版本号 | `versionCode = 238`（必须大于已发布 237），`versionName = "2.5.6"` |

## 1. 吸收的上游 2.5.6 架构变化

- `GenerationHandler.kt` → `GenerationLoop.kt`：本地生成逻辑已重新挂到官方新命名/新结构上；本地新增的
  `statusTrackedTools`、工具去重/递归保护、并行工具执行、DeepSeek 尾部注入、Persona 注入、
  `withMessageNames`、Tavern `STREAM_TOKEN_RECEIVED` 事件保留在新调用点。
- 官方新 `TranslationHandler` 接管翻译；本地 `GenerationLoop.translateText` 已移除，ChatService/TranslatorVM
  改用 `translationHandler`。
- `Export.kt` → `ConversationExport.kt`：按官方 rename 吸收。
- 官方 `ChatToolFactory` 成为工具装配入口；rikkaST 自研 File/Shell/Python/Database/Calculator/WebFetch/Task
  工具作为 local extra tools 在 `ChatService` 中追加并去重。
- 官方 `ConversationSessionManager` / `ConversationSession` / `MessageQueue` 成为会话与队列骨架；
  本地旧的 `sessions`/`getOrCreateSession` 平行实现已移除，前台服务/Tavern 事件/斜杠命令重新接到官方 session。
- 官方 `:oauth`、`:videogen` 模块已接入 `settings.gradle.kts` 与 app 依赖。
- 官方 AI SDK、Provider、消息模型、Navigation3、Room 2.8.5、Compose BOM 2026.09.00、AGP 9.4.0、
  Kotlin 2.4.20 等版本变化按官方 2.5.6 catalog 吸收。
- 官方技能系统新增 builtin skills / `SkillMetadata.builtin` / XML 安全提示；本地技能目录与公共目录规则保留，
  `SkillManager` 同时扫描本地 skills、`/Rikkahub/skills/` 与 builtin skills。

## 2. rikkaST 有意保留的差异（不是未迁移）

| 差异 | 原因 |
|---|---|
| `applicationId = me.rerere.rikkahub.st`、独立签名/更新源/品牌 | 独立产品，与官方共存 |
| Chaquopy/Python、QuickJS、Termux、Web UI、ABI arm64-v8a | rikkaST 运行时能力 |
| `GenerationForegroundService` 与官方 `ChatGenerationForegroundService` 并存 | 本地前台保活路径 + 官方 session 路径均保留，避免丢功能 |
| 本地 QuickJS 引擎（`wang.harlon.quickjs:wrapper-android:3.2.3`） | 本地 JS 工具依赖持久化 context；未切到官方 `dokar3`，作为明确的有意差异记录 |
| Firebase/Crashlytics 未接入 | 本地产品无 `google-services.json`，不误用官方云服务配置；官方相关代码在 app 层未启用 |
| 本地 skills 根目录 `context.getExternalFilesDir(null)/skills` + `/Rikkahub/skills` | 保持已安装用户技能可见/可迁移，不改为官方 `filesDir/skills` |
| 世界书、宏、Slash、Persona、导演备注、表情立绘、群聊、知识库、自研工具 | 本项目核心功能，完整保留 |

## 3. 数据库与数据安全

- 本地数据库版本 28 是已发布用户的实际版本；官方 2.5.6 的 25 不是同一条迁移链。
- 新增 `Migration_28_29`：仅补官方 2.5.6 引入的 `workspaces.shell_compatibility_mode INTEGER NOT NULL DEFAULT 0`。
- 已发布迁移 `20_21` ~ `27_28` 全部保留，未删除、未改写、未使用 destructive fallback。
- `AppDatabase.version = 29`，`app/schemas/.../29.json` 已生成；`DataSourceModule` 与 `AppDatabaseFactory`
  均已注册 `Migration_28_29`。
- 真实旧库升级测试尚未在本轮执行（首次对话禁止 APK 构建），需在下一轮用 Room migration test 从真实 28 库验证。

## 4. 尚未完成/未验证项（必须诚实记录）

- 未执行 APK 构建（遵守用户“第一次对话不要进行编译 APK”的约束），因此未获得编译级验证。
- 未完成全量 3,267 个文本版本的逐文件语义回执；`rikkaST-migration-audit/reports/reading-coverage.md`
  仍为未通过状态。本轮完成的是 rename-aware 三方合并、冲突消解、结构清理和关键文件静态审查。
- 官方 2.4.6→2.5.6 的 464 条变更未逐条人工复核；合并树已按 rename-aware 方式吸收，但需要下一轮用编译和测试确认。
- 旧技能页已删除，保留本地 flat 页面；官方 2.5.6 技能页的 readOnly/内置技能 UI 改动尚未完全回移到 flat 页面。
- 官方 Firebase/Crashlytics、部分新测试 fixture、`.agents` 文档的处理策略已在报告中说明。
- 更新 JSON 已改为 2.5.6，但 Release/APK 未发布、未推送、未签名。

## 5. 后续合并规则（替换旧规则）

1. 上游目标始终固定为官方 tag；不得用 master 替代目标版本。
2. 官方文件是骨架；本地差异通过扩展点（Transformer、Tool、Repository、Session 钩子、UI 入口）接入。
3. 双方都改的文件：先保留官方结构，再按最小补丁接回本地语义；禁止整份旧文件覆盖。
4. 本地独有文件必须可追溯到入口、DI、路由、数据、测试；不能只拷 class 不注册。
5. 数据库只允许在当前本地版本向前追加迁移；禁止回退版本、改写已发布迁移、destructive fallback。
6. 每次变更后更新本文件，并保留上游版本、commit、证据路径。
