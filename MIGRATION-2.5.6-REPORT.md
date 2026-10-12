# rikkaST 2.5.6 底座迁移报告

## 1. 结论

- 底座目标：官方 RikkaHub `2.5.6` tag，commit `447bb7e89710d31f1204d7a2973baa19fdbd5b28`。
- 迁移前本地 HEAD：`a94f2363f6e724a9125b6cdb25d1db072d044a88`（`main`，v2.4.6）。
- 迁移方式：以官方 `2.4.6` 为共同祖先，对 rikkaST 与官方 `2.5.6` 做 rename-aware 三方合并，再处理全部冲突、
  清理重命名/移动/删除、补数据库迁移。
- 本轮遵守用户约束：**没有编译 APK**。因此所有“编译通过”结论均未产生；本轮交付的是已整合源码树、
  静态结构检查、数据库迁移与完整差异记录。

## 2. 实际完成的源码动作

### 2.1 官方 2.5.6 变更吸收

- 生成链：`GenerationHandler.kt` → `GenerationLoop.kt`；本地生成、工具、Tavern 事件、Persona、DeepSeek 缓存逻辑
  已挂到官方新文件与新调用点。
- 翻译：官方新增 `TranslationHandler`，本地旧 `GenerationLoop.translateText` 已移除，调用方改用新 handler。
- 导出：`Export.kt` → `ConversationExport.kt`，按官方 rename 吸收。
- 会话/队列：官方 `ConversationSessionManager` / `ConversationSession` / `MessageQueue` 成为骨架；
  本地旧 `sessions` 平行实现已删除，前台服务、斜杠命令、Tavern 桥重新接到官方 session。
- 工具：官方 `ChatToolFactory` 成为主装配入口；本地自研 File/Shell/Python/Database/Calculator/WebFetch/Task
  工具作为 local extra tools 追加并去重。
- 模块：`:oauth`、`:videogen` 已接入 settings 与 app 依赖；官方 Gradle/AGP/Kotlin/Compose/Room/Navigation3
  版本按 2.5.6 catalog 吸收。
- 技能：官方 builtin skills / `builtin` 字段 / XML 安全提示吸收；本地外部 skills 目录与 `/Rikkahub/skills/` 保留。
- 安全/测试：官方 provider schema、工具输出截断、MCP 非法名校验、设置网络重试等进入代码。

### 2.2 本地自研功能保留

- 角色卡导入导出、世界书/提示注入、宏引擎、STscript/斜杠命令、JSR `injectPrompts`、Persona、导演备注、
  表情立绘、群聊、知识库、自研工具、技能目录、Web UI、Chaquopy/QuickJS 运行时均保留。
- 本地 `Settings` 大量字段（messageStyle、worldInfo*、regexScripts、expression*、tokenizerMode、
  authorNote*、personas、groupChats、macro* 等）保留，并补入官方 `networkSetting`。

### 2.3 数据库

- 本地版本 28 → 29。
- 新增 `Migration_28_29`：为 `workspaces` 增加官方 2.5.6 的 `shell_compatibility_mode`。
- 已发布迁移 `20_21`~`27_28` 保留；`AppDatabase.version = 29`；新增 `schemas/.../29.json`。
- `DataSourceModule`、`AppDatabaseFactory` 均注册完整迁移链。
- 真实 28 库升级测试未执行（本轮禁止 APK 构建），必须在下一轮补 Room migration test。

### 2.4 产品身份

- `applicationId = me.rerere.rikkahub.st` 保留。
- 独立签名配置、Chaquopy、arm64-v8a ABI、更新源保留。
- 未引入官方 Firebase/Crashlytics 云配置；未使用官方签名/更新 URL。
- `versionCode = 238`、`versionName = "2.5.6"`、`update.json` 已指向本仓库。

## 3. 证据与文件

- 合并工作树：`/home/xiaoqi/rikkaST-migration-audit/work/merge2/result`。
- 三方合并报告：`/home/xiaoqi/rikkaST-migration-audit/reports/candidate-merge-report.csv`。
- 官方 2.5.6 change map：`/home/xiaoqi/rikkaST-migration-audit/reports/upstream-2.5.6-change-map.md`。
- 源清单：`source-inventory-rikkast.jsonl`、`source-inventory-rikkahub-2.5.6.jsonl`。
- 逐文件阅读 ledger：`source-read-ledger-tracked.csv`；本轮未完成全量语义回执，`reading-coverage.md` 仍为未通过。
- 本报告文件：`MIGRATION-2.5.6-REPORT.md`。

## 4. 本轮静态检查

- 全仓冲突标记扫描：业务源码中 `<<<<<<<` / `>>>>>>>` 为 0（审计备份文件已清理）。
- Kotlin 文件括号/字符串/注释级结构扫描：核心合并文件（GenerationLoop、ChatService、RouteActivity、
  PreferencesStore、GoogleProvider 等）已修复到结构平衡。
- Gradle 版本目录别名引用扫描：所有 `libs.*` 引用均有 catalog 定义。
- 模块引用扫描：所有 `project(":...")` 均有 settings include。
- 未执行 Gradle 编译/测试/APK 构建。

## 6. 逐条复核与数据库模拟测试（本轮追加）

- 官方 2.4.6→2.5.6 的 464 条变更已逐条分类：
  - `ABSORBED_EXACT`: 354
  - `ABSORBED_RENAME_EXACT`: 5
  - `DELETED_ABSORBED`: 6
  - `MERGED_OR_DIVERGED` / `MERGED_OR_DIVERGED_RENAME`: 88
  - 剩余为有意差异、移动文件吸收或已说明的测试/文档差异
- 逐条明细：`/home/xiaoqi/rikkaST-migration-audit/reports/upstream-2.5.6-review.csv`
- 逐条说明：`/home/xiaoqi/rikkaST-migration-audit/reports/upstream-2.5.6-review.md`
- 数据库 28→29 SQL 级模拟测试已通过：11 张表、行数保留、`workspaces.shell_compatibility_mode` 默认 0。
  - 报告：`/home/xiaoqi/rikkaST-migration-audit/reports/db-migration-28-29-test.md`
  - Room schema 复核：`/home/xiaoqi/rikkaST-migration-audit/reports/room-schema-29-review.md`
- 已补入官方 2.5.6 测试：`McpToolSchemaTest`、`BuiltinSkillsTest`、`ChatUtilTest`；
  已重新启用本地被禁用的官方测试：`SkillsToolsTest`、`PromptInjectionTransformerTest`、
  `TimeReminderTransformerTest`、`SkillPathsTest`、`ProviderConfigureConvertToTest`。
- 技能 UI 已回移：SkillDetailPage/VM readOnly/builtin、SkillsPage 内置徽章/禁止删除、SkillsVM 二进制安全下载与限流提示。
- 仍剩余：真实 Room `MigrationTestHelper`、完整编译/测试/回归（下一轮）。

## 5. 下一轮必须完成

1. 在 Windows/Linux 构建环境执行 `:app:assembleDebug`、`test`、`lint`。
2. 运行 Room migration test：真实 28 库 → 29，校验用户数据保留。
3. 对酒馆核心功能做回归：角色卡导入导出、世界书、宏/Slash、Persona、导演备注、表情、群聊、知识库、自研工具。
4. 对官方 2.5.6 功能做回归：Provider、流式、会话队列、MCP/OAuth、技能、Navigation3/预测性返回。
5. 完成全量逐文件语义回执；未完成前不得宣称“全量源码已读完”。
6. 修复编译期暴露的 API/类型差异，并更新 `DIVERGENCE.md` 与阅读 ledger。
