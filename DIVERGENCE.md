# DIVERGENCE — rikkaST 与上游 RikkaHub 的差异地图

> 用途：合并上游（`git fetch upstream && git merge upstream/master`）时的冲突处理手册。
> 维护：每次合入上游后更新本节“状态”；每次改动核心文件后更新对应条目。

## 0. 当前状态

| 项 | 值 |
|---|---|
| 本地分支 | `main` |
| 上游 | `upstream/master`（github.com/rikkahub/rikkahub） |
| 最近合入 | `aac6e9638`（2026-08-13，含 AI 模块重构 / 搜索模式 / 构建重构） |
| 领先提交数 | 约 1780+ |
| 上游文件被本地修改 | 约 100 个（含重命名） |
| 本地独有文件 | 约 50 个 |

## 1. 合并工作流（推荐）

1. **频繁合**：上游每次更新就 `git fetch upstream`，需要时 `git merge upstream/master`。不要攒几百个提交再合。
2. 2026-08-13 合入注意事项：AI 流式接口已改为 `StreamChunkHandler` / `handleTextGenerationResult`（旧 `handleMessageChunk`/`MessageChunk` 已删除）；搜索改为 `SearchMode`（OFF/LOCAL/BUILT_IN）；keep 规则迁移到 `app/src/main/keepRules/rikkahub.keep`（本地 Chaquopy/Compose 规则已并入，不要再改 `app/proguard-rules.pro`）；应用版本与上游一致（2.4.6/173）；更新检查已切换到本仓库 `update.json`（`raw.githubusercontent.com/yufanx79-gif/rikkaST/main/update.json`），不再指向官方服务器。以后发版：改 `update.json`（版本号+下载链接）+ 发布 GitHub Release，用户端即可收到更新。。
2. 冲突按本文件分类处理：
   - 本地**独有文件**（第 3 节）→ 永不冲突，忽略。
   - **核心生成链**（第 2 节 A 组）→ 人工逐块审，两边语义都要保留。
   - **酒馆/工具/群聊**（本地方向，上游没有）→ 冲突时以上游文件为基底，把本地功能重新叠上去。
   - **可回退项**（第 5 节）→ 尽量保持上游原样。
3. 合并后本地检查（不本地编译）：`git diff --check`；推送后靠 CI 验证。
4. 若上游新增了本地没有的功能，且不与本地冲突 → 直接收下，并补进本文件。

## 2. 上游文件被本地修改（按合并风险分组）

### A. 核心生成链（最高风险，冲突需人工审）

| 文件 | 改动量 | 本地改了什么 | 合并建议 |
|---|---|---|---|
| `service/ChatService.kt` | +509/-55 | 工具构建、前台服务、群聊生成、斜杠注入、发送链路；v240 W3 加 `classifyExpression`（表情分类 best-effort） | 上游更新先合，再叠本地逻辑 |
| `data/ai/GenerationHandler.kt` | +638/-185 | 系统提示组装、transform 链、预构建 system | 同上 |
| `data/ai/transformers/PromptInjectionTransformer.kt` | +540/-11 | 世界书官方对齐（选择性逻辑/分组/递归/粘性/预算）；v240 W2 加 `@@` 装饰器（扫描副本剥壳）；v240 W5 预算/maxContext 改用真 tokenizer | 本地逻辑已对照酒馆官方，合时保留 |
| `data/ai/transformers/PlaceholderTransformer.kt` | +358/-6 | 宏引擎 2.0 接入、`{{original}}` 等修正 | 保留本地 |
| `data/ai/transformers/Transformer.kt` | +36/-2 | TransformerContext 扩展字段 | 保留 |
| `data/model/Assistant.kt` | +382/-22 | 工具/技能/群聊/酒馆/宏/知识库 字段 | 合时保留本地字段 |
| `data/model/Conversation.kt` | +3 | 小改 | 低风险 |
| `data/datastore/PreferencesStore.kt` | +105/-1 | 本地设置（群聊/酒馆/工具/压缩等）；v240 加 `expression_enabled` / `expression_classifier` / `expression_fallback_label` / `tokenizer_mode` | 保留本地设置项 |
| `data/ai/GenerationPrompts.kt` | +33 | 本地提示词 | 保留 |
| `ai/ui/Message.kt` | +184 | 消息模型扩展（UIMessagePart/Annotation 合并进此文件） | 上游也改此文件时容易冲突 |
| `ai/registry/ModelRegistry.kt` | ±20 | 本地模型注册 | 低风险 |

### B. 酒馆兼容（本地独有方向，上游没有对应功能）

| 文件 | 改动量 | 说明 |
|---|---|---|
| `ui/pages/assistant/detail/TavernCharacterCard.kt` | +1796 | 角色卡详情/内嵌世界书编辑器（本地新增） |
| `ui/pages/assistant/detail/AssistantImporter.kt` | +778/-183 | 角色卡导入解析（V2/V3、世界书、PHI、深度提示） |
| `ui/pages/extensions/PromptPage.kt` | +1169/-55 | 世界书/提示注入编辑页 |
| `data/model/TavernCard.kt` | +118 | 角色卡数据模型 |
| `utils/CardExporter.kt` | +317 | 角色卡导出（PNG/JSON） |
| `data/ai/transformers/AuthorsNoteTransformer.kt` / `ui/pages/setting/AuthorsNotePage.kt` | +84 / +413 | 导演备注（官方语义） |
| `data/model/Persona.kt` / `ui/pages/setting/PersonaPage.kt` | +28 / +680 | 人设 |
| `data/model/AuthorNotePosition.kt` / `GenerationType.kt` | +44 / +29 | 枚举 |
| `data/ai/transformers/MacroEngine.kt` | +879 | 宏引擎 2.0 |
| `ui/components/ai/SlashCommands.kt` / `MacroVarSlashOps.kt` | +257 / +92 | 斜杠命令 |
| `ui/pages/extensions/PromptVM.kt` | +77 | 世界书双向同步 |

### C. 群聊（本地独有）

`data/model/GroupChat.kt`(+52)、`GroupSpeakerSelector.kt`(+148)、`ui/pages/chat/GroupChatPage.kt`(+1167)、`GroupChatListPage.kt`(+242)

### D. 工具与 Agent（本地独有）

`data/ai/tools/LocalTools.kt`(+489，从 `tools/local/` 移动)、`FileTools.kt`(+430)、`TaskTools.kt`(+430)、`DatabaseQueryTool.kt`(+326)、`ShellTools.kt`(+81)、`PythonTools.kt`(+152)、`CalculatorTool.kt`(+127)、`WebFetchTool.kt`(+111)、`data/ai/python/PythonBridge.kt`(+235)、`JsBridge.kt`(+37)、`data/ai/prompts/SystemPromptAssembler.kt`(+134)、`data/ai/transformers/SkillAutoTriggerTransformer.kt`(+94)、`data/files/PluginManifest.kt`(+119)、`SkillRegistry.kt`(+43)、`SkillFrontmatterParser.kt`(+21)

上游的 `data/ai/tools/SkillsTools.kt` 本地改了 +64/-35（技能工具）；`data/files/SkillManager.kt` +78/-5（**已回退缓存改动**，仅剩外部存储/公共目录两个早期差异，见第 5 节）。

### E. 数据库与迁移

| 文件 | 说明 |
|---|---|
| `data/db/AppDatabase.kt` | 版本 28；本地实体增减（知识库实体于 27→28 恢复，见下）、DAO 增删 |
| `data/db/migrations/Migration_20_21.kt` ~ `27_28.kt` | 本地新增/修改；知识库表的三段历史：`20_21` 建表 → `25_26` 删除 → `27_28` 重建（当前版本 28，知识库功能可用）。**迁移链不可删**，否则升级崩溃 |
| `data/db/dao/MessageNodeDAO.kt` | +3，小改 |

### F. 路由 / DI / Web

`RouteActivity.kt` ±1501（本地页面入口最多，冲突高）、`RikkaHubApp.kt` +36/-49、`di/AppModule.kt`、`DataSourceModule.kt`、`RepositoryModule.kt`、`ViewModelModule.kt`。
Web 相关只有小改（`web/routes/ConversationRoutes.kt` +47/-25、`SettingsRoutes.kt` +15、`WebApiModule.kt` +12、`FolderRoutes.kt` +6、`WebServerManager.kt` +4）——**约定：尽量不动 web**。

### G. 其余 UI/工具

`ui/components/ai/ChatInput.kt`(+455)、`ui/pages/chat/ChatPage.kt`(+312/-79)、`ChatDrawer.kt`(+173/-189)、`ChatDrawerVM.kt`、`ChatVM.kt`(+54)、`ui/components/message/ChatMessageTools.kt`(+774)、`ChatMessage.kt`、`ChatMessageActions.kt`、`AssistantDetailPage.kt`(+519)、`AssistantDetailVM.kt`、`AssistantLocalToolPage.kt`(+169/-7)、`AssistantBasicPage.kt`、`AssistantVM.kt`、`AssistantPage.kt`、`SettingPage.kt`(+183)、`SettingPreferencesUIPage.kt`(+326)、`ui/pages/chat/Export.kt`、`utils/ImageUtils.kt`、`ContextUtil.kt`、`CrashHandler.kt`、`service/ChatNotificationManager.kt`、`ui/components/richtext/Markdown.kt`、`MarkdownNew.kt`、`FilesPicker.kt`、`ChatList.kt`、`data/repository/ConversationRepository.kt`(+32/-50)、`FolderRepository.kt`、`data/export/ExportSerializer.kt`(+85/-8)

### H. [v240] 本批新增：W1–W5（导演备注宏 / @@ 装饰器 / 表情立绘 / 闭包 / 真 tokenizer）

#### H.1 修改的上游文件（合并建议）

| 文件 | 本批改动 | 合并建议 |
|---|---|---|
| `data/ai/transformers/PromptInjectionTransformer.kt` | W2 `@@` 装饰器（扫描副本剥壳，判定先于关键词）；W5 `countTokens(text, tokenizerMode)` 替换启发式调用点 | 保留本地 |
| `service/ChatService.kt` | W3 `classifyExpression`（独立一次性分类，20s 超时，异常返回 null） | 保留本地 |
| `data/datastore/PreferencesStore.kt` | W3/W5 四个新设置项（key：`expression_enabled` / `expression_classifier` / `expression_fallback_label` / `tokenizer_mode`） | 保留本地 |
| `RikkaHubApp.kt` | W5 `TikToken.attach { assets.open(it) }`（懒加载，失败回退启发式） | 保留本地 |
| `ui/pages/chat/ChatPage.kt` | W3 挂载 `CharacterExpressionHost` / `CharacterExpressionOverlay` | 低风险 |
| `ui/pages/chat/ChatSlashHost.kt` | W3 `/expression-fallback` 宿主实现 | 保留本地 |
| `ui/pages/chat/ChatVM.kt` | W3 `classifyExpression` 透传 | 低风险 |
| `ui/pages/assistant/detail/AssistantImporter.kt` | W3 V3 卡 `assets` 内嵌立绘落盘（best-effort） | 保留本地 |
| `data/st/import/StArchiveImporter.kt` | W3 归档导入同步内嵌立绘 | 保留本地 |
| `ui/pages/extensions/PromptPage.kt` | W5 世界书设置新增分词器选择 | 保留本地（B 组文件） |
| `ui/pages/setting/SettingPreferencesPage.kt` / `RouteActivity.kt` | W3 新设置页入口 + 路由 | 保留本地 |
| `data/st/script/StSlashExecutor.kt` | W4 闭包运行时 + `/run(/call/exec)` `/abort` `/delay` `/switch` | 保留本地 |

#### H.2 本地新增文件（同步补第 3 节）

- `data/model/WorldInfoDecorators.kt`（W2）
- `data/st/expressions/`：`ExpressionLabels.kt` / `ExpressionStore.kt` / `ExpressionClassifier.kt` / `SpriteRepository.kt`（W3）
- `ui/pages/chat/CharacterExpression.kt`、`ui/pages/setting/SettingPreferencesExpressionPage.kt`（W3 UI）
- `data/st/script/StClosure.kt`（W4 闭包语法 + 闭包表）
- `data/st/tokenizer/TikToken.kt`（W5 真 tokenizer + `TokenizerMode`）
- 测试：`WorldInfoDecoratorTest` / `WorldInfoDecoratorLosslessTest` / `ExpressionLabelsTest` / `ExpressionStoreTest` / `StClosureTest` / `TikTokenTest`；`MacroEngineTest` 加 W1 三条

#### H.3 本批语义差异 / PORT NOTE（与官方的有意差异，逐条登记）

1. **W1 导演备注宏**：官方 `{{authorsNote}}` 读 `chat_metadata[prompt]`（按对话）、`{{charAuthorsNote}}` 读按角色卡绑定的备注、`{{defaultAuthorsNote}}` 读全局默认模板；rikkaST 只有全局 `Settings.authorNote`，因此 `authorsNote` / `defaultAuthorsNote` 同源、`charAuthorsNote` 恒空串（见 `MacroEnv.AuthorNotes` 注释）。
2. **W2 `@@` 装饰器**：官方在 `getSortedEntries` 把剥壳结果写进条目副本；rikkaST 不改存储结构 —— 装饰器是派生字段、剥离只作用扫描副本 → 导入/导出无损（`WorldInfoDecoratorLosslessTest`：47 条真实卡往返 + `@@` 行原样保留）。`parseDecorators` 的 `@@@` 转义、未知装饰器行丢弃、全 `@@` 行时 content 保持原样等边角行为逐字复刻官方。
3. **W3 表情立绘**：**原生重写**，不移植官方 `index.js`（依赖浏览器 DOM + classify 服务端）。只保留官方 5 种分类方式的 `llm` / `none`；未做：visual novel（群聊并排）、`#emoji` 立绘资源、`/expression` 强制设置、`/expression-folder-override`、local(BERT)/extras/webllm。分类只在生成完成后触发（+官方 10s 节流 `STREAMING_UPDATE_INTERVAL`）、按 messageId 缓存、该角色无立绘不调用、失败回退兜底标签（默认 joy）。立绘存储：`filesDir/sprites/<assistantId>/<label>.(png|jpg|jpeg|webp|gif)`。
4. **W4 控制流**：
   - `/switch` 是**本地扩展**（官方 1.18 没有该命令，已源码核实）：值匹配 → 执行对应闭包分支，`default=` 兜底。
   - `/delay` 在 UI 线程跳过等待（防 ANR），非 UI 线程真实 `Thread.sleep`；单次上限 30s。
   - 闭包表 `StClosureStore` 为进程内存、按 `host.chatKey` 分作用域（官方 `SlashCommandScope` 同样是会话内存，不落盘）。
   - 闭包递归深度上限 **32**（防栈溢出；官方无显式上限，靠 abortController）。
   - `{{arg::key}}`：进入闭包体前做一次文本替换；未提供的 key 展开为空串（对齐官方 `arg::*` 通配默认），支持 `{{arg::*}}`。
   - 官方 1.18 **没有** `/if` `/while` `/times` `/break` `/let` `/var`（已源码核实），本批不实现、不假实现。
#### H.4 [v241] 真实卡兼容修复（导入 / 导出 / JSR 注入）

**触发**：用主人桌面 `Desktop/角色卡/` 的真实成品卡（校园恋爱综漫1.7 / 星羽学院 / 千纱-DS鲸鱼版 / 偏航的东京日常v0.1）做导入审计，发现 3 类真实兼容缺陷，全部修复并加回归测试：

| # | 缺陷（v241 前） | 影响 | 修复 |
|---|---|---|---|
| 1 | `character_book.entries[].enabled` 被忽略（只读 `disable`） | ST 1.18 规范写/读 `enabled`；真实卡 422/499 条本应禁用却被全部启用 → 世界书注入量与 token 预算严重不符 | `AssistantImporter.parseEntriesArray/Map`：`enabled` 优先 → `disable` → 默认启用；`RealCardV3ImportTest` 新增 3 条断言 |
| 2 | 导出 `character_book.entries` 是「以 id 为键的对象」、不写 `enabled`、`extensions.role` 写字符串 | 导出的卡在酒馆里 `convertCharacterBook` 用 `.forEach` 读数组 → 失败；`enabled` 缺失 → 条目全禁用 | `CardExporter`：`entries` 改数组 + 显式 `id` + 写 `enabled`；`extensions.role` 写数字（0/1/2）；`RealCardImportAuditTest` 钉死形状 |
| 3 | `regex_scripts` 用严格 kotlinx 序列化，字段类型漂移时整条丢 | `id` 数字 / `placement` 标量 / `trimStrings` 字符串 / 布尔 0-1 的真实卡正则会静默丢失 | `parseCardRegexScripts` 改宽容解析（数字 id、标量或数组 placement、字符串 trimStrings、0/1 布尔、camel/snake 双写法）；「坏字段忽略、脚本保留」，仅无 `findRegex`/非对象才丢 |

**JSR `injectPrompts` 移植（v241）**：

- 来源：`refs/C-st-ext/N0VI028__JS-Slash-Runner/src/function/inject.ts` + `@types/function/inject.d.ts`；
- 新增：`shims/inject-shims.js`（ES module，`node --check` 通过）、`TavernJsBridge.injectPrompts/uninjectPrompts`、`InjectedPromptStore`（按会话隔离内存态）、`InjectedPromptsTransformer` + `InjectedPromptCleanupTransformer`（输入链首尾）；
- 已对齐：`position: in_chat | none`、`depth`、`role`、`should_scan`、`once`、`{uninject}` 返回值、`uninjectPrompts(ids)`；
- 有意差异（逐条登记）：
  1. `filter`（JS 函数）跨 Kotlin 桥无法传递 → 忽略（本批真实卡的 filter 恒为 `() => true`，影响为零）；
  2. `once` 在「注入进提示词」瞬间消费（官方在 GENERATION_ENDED/STOPPED 移除；对「只影响下一次生成」等价）；
  3. `position='none'` 的扫描承载消息在宿主侧用 USER 角色（世界书扫描只取非 SYSTEM 消息），发送前由 cleanup 整条丢弃；
  4. 未提供 `getPromptsInjected`（JSR 公开类型里也没有该签名）。

## 3. 本地独有文件（永不参与上游冲突）

约 50 个新文件，包括：
- 酒馆：`data/ai/transformers/ContextInjectorTransformer.kt`、`ui/components/ai/SlashCommands.kt`、`MacroVarSlashOps.kt`、`utils/CardExporter.kt` 等
- 群聊：`GroupChat.kt`、`GroupChatPage.kt`、`GroupChatListPage.kt`、`GroupSpeakerSelector.kt`
- 工具：`FileTools.kt`、`TaskTools.kt`、`DatabaseQueryTool.kt`、`ShellTools.kt`、`PythonTools.kt`、`CalculatorTool.kt`、`WebFetchTool.kt`、`PythonBridge.kt`、`JsBridge.kt`、`SystemPromptAssembler.kt`、`SkillAutoTriggerTransformer.kt`
- 宏/斜杠：`MacroEngine.kt`
- 服务：`service/GenerationForegroundService.kt`

合并时这些文件直接保留本地版本即可。

## 4. 上游文件被本地删除/移动

| 文件 | 处理 |
|---|---|
| `data/ai/tools/local/JavascriptTool.kt` / `LocalToolOption.kt` / `LocalTools.kt` | 移动到 `data/ai/tools/`（功能保留） |
| `ui/pages/extensions/skills/SkillsPage.kt` / `SkillsVM.kt` / `SkillDetailPage.kt` / `SkillDetailVM.kt` | 移动到 `ui/pages/extensions/`（本地重写） |
| `ai/ui/UIMessagePart.kt` / `UIMessageAnnotation.kt` | 合并进 `ai/ui/Message.kt` |
| `ui/pages/setting/SettingMcpPage.kt` | 本地移除（MCP 设置并入别处） |
| `data/ai/tools/GitHubTool.kt`、`SleepTool.kt` | 已按“上游没有”删除，**不要再加回** |

## 5. 已对齐 / 已回退项（保持）

- GitHub 工具及其 UI：已删（上游没有）
- sleep 工具：已删（上游没有）
- **知识库（RAG）：曾被删，已于 v27→28 恢复，当前可用**。历史：`Migration_20_21` 建表 → `Migration_25_26` 删除 → `Migration_27_28` 重建；对应代码 `data/knowledge/KnowledgeBaseService.kt`、`data/ai/transformers/KnowledgeBaseTransformer.kt`（已挂在 `ChatService` 生成链上）、`ui/pages/knowledge/KnowledgeBasePage.kt`。**上游合并时不要按「已删除」处理**。
- SkillManager：已回退本轮缓存改动；**剩余两个早期差异**（技能目录用外部存储 + `/Rikkahub/skills` 公共目录）是技能安装依赖，默认保留
- MCP：工具名与校验对齐上游
- 文件工具：已去掉 skill 目录拼接
- 日志调试（DeveloperPage/AILogging）：已删（上游没有）

## 6. 冲突处理速查

1. `ChatService.kt` / `GenerationHandler.kt`：上游改动先收，本地功能块（工具构建、transform 链）重新叠上去。
2. `RouteActivity.kt`：各页面 entry 是追加式，冲突通常可两边都留。
3. `Assistant.kt` / `PreferencesStore.kt`：字段是追加式，上游删字段时检查本地是否在用。
4. 数据库：上游加 migration 时，注意本地版本号（当前 28）与迁移链；不要在本地重写已发布的迁移。
5. 合完：`git diff --check` + 推送 + CI；**不在本地编译**。
