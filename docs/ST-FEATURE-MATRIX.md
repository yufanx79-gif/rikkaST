> **来源与验证状态**
>
> 本文由一次独立的只读审计产出（覆盖 Kotlin 源码、`app/src/main/assets/st-runtime/**`、README/DIVERGENCE）。
> 产出后由 Lead 抽查了 10 条高风险断言 —— V1 角色卡能否导入、PNG chunk 类型支持、`group_only_greetings` 是否生效、
> STscript 控制流是否存在、`vectorized` 是否硬编码、缺失宏清单、正则 `WORLD_INFO` 有无调用点、
> 以及「知识库其实没删」这条反直觉结论 —— **抽查全部属实**（含迁移链 `20_21` 建表 / `25_26` 删表 / `27_28` 重建的原始 SQL 佐证）。
>
> **已知局限**：本次审计未编译、未运行、未逐行读 JS 运行时（156 KB `runtime.js` + 560 KB `mvu/bundle.js`），
> 因此「未实现」类结论存在**低估风险** —— 可能漏掉未被关键词命中的隐式实现。所有「完整实现」结论均已给出 `路径:行号`。
>
> **与 `docs/ST-COMPATIBILITY.md` 的分工**：那份讲**插件接入层**（第三方扩展能不能跑、边界在哪）；
> 这份讲**官方功能逐项实现状态**（角色卡 / 世界书 / 预设 / 宏 / STscript / 正则 / 变量 / 多媒体 …）。
# rikkaST「SillyTavern 官方功能 → 本分支实现状态」对照矩阵

审计对象：`D:\rikkaST`（只读，未修改任何文件、未执行构建）
审计方式：全部结论回到代码取证；每条给出 `文件:行号`（路径相对 `D:\rikkaST`）
状态口径：**完整实现 / 基本实现（有已知缺口） / 部分实现 / 仅数据兼容（能导入但不生效） / 未实现**

---

## 0. 一句话结论

对用户「除了酒馆生图和 TTS，剩下的功能我到底有没有全部实现？」的回答是：**没有全部实现，但核心的「角色卡 / 世界书 / 宏 / 斜杠命令 / 人设 / 导演备注 / 群聊 / 正则 / 变量 / 第三方扩展宿主」这条主线做得相当深**（部分模块甚至是 ST 源码的逐语义移植），
而**缺口集中在三块**（v240 已收窄）：① STscript 语言层 —— 闭包 `{: :}` / `/run` / `/abort` / `/delay` / `/switch` / `{{arg::key}}` 已补齐，仍缺 QR 集合与 `/let` `/var`；② 提示词预设体系（只导入 Chat-Completion 的 prompts，无导出、无 Text-Completion/Kobold/NovelAI、无 squash）；③ ST 内置扩展 —— expressions（表情立绘）已原生重写（llm 分类 + 立绘），vectors / caption / summarize 仍未移植；④ 若干「字段能存能编辑能导出、但引擎不读」的仅数据兼容项（`group_only_greetings`、`automation_id`、`vectorized`、`display_index/position`、`WORLD_INFO` 正则位）。

---

## 1. 角色卡

| 子项 | 状态 | 证据(路径:行号) | 备注/缺口 |
|---|---|---|---|
| V2 导入 | 完整实现 | `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantImporter.kt:200,208-261` | `spec=chara_card_v2` 分派 |
| V3 导入 | 完整实现 | `AssistantImporter.kt:201,265-316` | 含 nickname / assets / source / creation_date / creator_notes_multilingual |
| **V1 导入** | **未实现（真差距，已复核）** | `AssistantImporter.kt:196-197`（`json["spec"] ?: error(...)`） | 无 `spec` 字段的 V1 卡直接报错。**官方能收**：`src/endpoints/characters.js:504-508` 的 `readFromV2()` 在 `char.data` 缺失时只 `console.warn` 后原样返回，扁平 V1 字段照常可用 |
| PNG tEXt chunk | 完整实现 | `app/src/main/java/me/rerere/rikkahub/utils/ImageUtils.kt:305-359` | 识别 `chara` / `ccv3`，`ccv3` 优先 |
| PNG **iTXt** chunk | 未实现，**但与官方一致，不算差距** | rikkaST 全仓 grep `iTXt` = 0 命中；官方 `src/character-card-parser.js:6,17` 用 `png-chunk-text`，**同样只处理 `tEXt`**；rikkaST 还额外做了 `ccv3` 优先（`ImageUtils.kt:326,339`） | 与官方行为相同 |
| PNG zTXt chunk | 部分实现 | `ImageUtils.kt:319,349` | 类型被识别但注释明确「压缩的，跳过」，未做 inflate |
| JSON 导入 | 完整实现 | `AssistantImporter.kt:186-193` | |
| PNG 导出 | 完整实现 | `app/src/main/java/me/rerere/rikkahub/utils/CardExporter.kt:23-65` | 双 chunk（`chara`=V2、`ccv3`=V3），与官方一致 |
| JSON 导出 | 完整实现 | `CardExporter.kt:70-236` | 字段名对齐官方 `chara_card_v3` |
| 字段完整度（20+） | 完整实现 | `app/src/main/java/me/rerere/rikkahub/data/model/TavernCard.kt:11-45` | spec/specVersion/name/description/personality/scenario/firstMessage/alternateGreetings/mesExample/systemPrompt/creator/creatorNotes/characterVersion/tags/postHistoryInstructions/extensions/extensionsRaw/assets/groupOnlyGreetings/nickname/creatorNotesMultilingual/source/creationDate/modificationDate/depthPrompt(+depth/role)/embeddedBook/embeddedRegexScripts |
| `character_book` 内嵌世界书 | 完整实现 | 解析 `TavernCard.kt:42,60-118`、`AssistantImporter.kt:368-...`；**生效**：`AssistantImporter.kt:721-736`（转 Lorebook）、`AssistantImporter.kt:874-875`、运行时 `data/st/runtime/TavernScripts.kt:394-443`、`data/ai/transformers/PromptInjectionTransformer.kt`（经 RegexInjection） | 能真正参与激活扫描；**[v241] 启停字段按 ST 1.18 规范读 `enabled`**（官方写侧 `characters.js convertWorldInfoToCharacterBook` 写 `enabled`、读侧 `world-info.js convertCharacterBook` 读 `enabled` → `disable = !enabled`；兼容旧 `disable`），导出改为 **`entries` 数组 + 显式 `id` + `enabled`**、`extensions.role` 写数字（ST 兼容，回归见 `RealCardImportAuditTest`） |
| `extensions` 保真 / 往返 | 完整实现 | `TavernCard.kt:28`（extensionsRaw）；导出 `CardExporter.kt:118-125,130-136` | 原始 JSON 原样带回 |
| 深度提示 depth_prompt | 完整实现 | 解析 `TavernCard.kt:38-40`、`AssistantImporter.kt:240-242,295-297,332-351`；注入 `AssistantImporter.kt:854-865`；`match_character_depth_prompt` 命中 `PromptInjectionTransformer.kt:318`；宏 `{{charDepthPrompt}}` `data/st/macro/MacroDefinitions.kt:457` | 真注入，不是仅存储 |
| `alternate_greetings` | 完整实现 | 导入 `AssistantImporter.kt:230,278`；选择 UI `ui/pages/assistant/detail/AssistantDetailPage.kt:284`、`ui/pages/chat/ChatPage.kt:321,333`、`ui/pages/chat/GroupChatPage.kt:301`；宏 `{{charFirstMessage::N}}` `data/st/macro/MacroEngine.kt:890` | |
| `group_only_greetings` | **仅数据兼容** | 导入 `AssistantImporter.kt:289`；编辑 `ui/pages/assistant/detail/TavernCharacterCard.kt:284-287`；导出 `CardExporter.kt:90`；**群聊开场无引用**（`GroupChatPage.kt` 只用 `alternateGreetings`） | 存/改/导出都在，开局不生效 |
| PHI `post_history_instructions` | 完整实现 | 导入 `AssistantImporter.kt:237,285`；注入 `AssistantImporter.kt:841-850`；宏 `{{charInstruction}}`/`{{jailbreak}}` `MacroDefinitions.kt:450`、`data/ai/transformers/PlaceholderTransformer.kt:218,240` | |
| 创作者元数据 | 完整实现 | `TavernCard.kt:22-24,33-36`；导出 `CardExporter.kt:84-117` | creator/creator_notes/character_version/creation_date/modification_date/source/creator_notes_multilingual |
| 卡内脚本（tavern_helper.scripts） | 完整实现 | `data/st/runtime/TavernScripts.kt:30,74,118-122,196-229`；`TavernRuntimeManager.kt:208,799`；执行于 JS 运行时 `app/src/main/assets/st-runtime/runtime.js:1877-1899`；UI `ui/components/ai/StTavernSections.kt:198,389-446` | 真跑（WebView） |
| 卡内正则（extensions.regex_scripts） | 完整实现 | `data/st/regex/RegexScriptSources.kt:22-42`；调用 `data/ai/transformers/StRegexInputTransformer.kt:32`、`ui/components/message/ChatMessage.kt:322` | |
| 多角色卡库 | 完整实现 | 上游 RikkaHub 助手列表 + `TavernCharacterCard.kt`（1942 行详情编辑页） | 上游能力 |
| 角色卡可视化编辑页 | 完整实现 | `TavernCharacterCard.kt` 全文；内嵌世界书管理 `:295-308` | |
| `fav` / `talkativeness` | 基本实现 | `AssistantImporter.kt:213-219`（并入 extensions）、`data/model/Assistant.kt:82` | fav 仅存；talkativeness 群聊选择器真用（`GroupSpeakerSelector.kt:68-74`） |
| `world`（卡绑定世界书名） | 基本实现 | `data/st/runtime/TavernScripts.kt:339,431,740,778` | 按名字解析已存在的外置世界书 |

---

## 2. 世界书 / World Info

引擎：`app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt`（994 行）
数据模型：`data/model/Assistant.kt:436-488`（RegexInjection）+ `data/model/TavernCard.kt:68-118`（TavernBookEntry）

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| 主关键词匹配 | 完整实现 | `PromptInjectionTransformer.kt:432-470`（扫描循环）、`keyMatches` 相关 | |
| 副关键词 + 四档 selective logic | 完整实现 | `Assistant.kt:494-505`（`and_any`/`and_all`/`or_any`/`not_any`/`not_all`，官方枚举名序列化） | `or_any` 是本地遗留扩展 |
| `key_regex` / `match_whole_words` / `case_sensitive` | 完整实现 | `Assistant.kt:452-454`；导入 `data/export/ExportSerializer.kt:347-349` | |
| 条目级 `scan_depth` | 完整实现 | `PromptInjectionTransformer.kt:438-440`（条目优先，否则全局+skew） | |
| `constant` 常驻 | 完整实现 | `PromptInjectionTransformer.kt:432` | |
| 分组 + 权重 + override + 组评分 | 完整实现 | `PromptInjectionTransformer.kt:582-665`（粘性优先 → 评分 → group_override → 加权随机）；字段 `Assistant.kt:459-461,475` | 与官方 world-info.js 逐语义对齐（代码注释标注官方行号） |
| `probability` / `use_probability` | 完整实现 | `PromptInjectionTransformer.kt:483-486`（sticky 免掷） | |
| `sticky` / `cooldown` / `delay` | 完整实现 | `:407`(delay)、`:410`(cooldown)、`:694-734`（tick + 到期自动转冷却）；按会话隔离 `:40-44` | 内存态，重启丢失（官方存 chat_metadata） |
| 递归扫描 + exclude/prevent | 完整实现 | `:428-430`、`:507-525` | |
| `delay_until_recursion`（true/数字层级） | 完整实现 | `:373-378`（层级表）、`:412-425`、`:539` | |
| token 预算 / 溢出 / `ignore_budget` | 完整实现 | `:380-383`（budget = 预算% × maxContext，cap 封顶）、`:471-492`（溢出后 ignore_budget 仍可插） | |
| `match_*` ×6 | 完整实现 | `:314-319`；字段 `Assistant.kt:481-486`；导入 `AssistantImporter.kt:529-534` | 六项全在 |
| 注入位置 / depth / role | 完整实现 | `InjectionPosition` `Assistant.kt:361-400`（含 before/after char、ANTop/ANBottom、atDepth、EMTop/EMBottom、outlet）；注入 `:917-946` | |
| `triggers`（生成类型过滤） | 完整实现 | `:403-404`；字段 `Assistant.kt:480` | 注意 `Assistant.kt:480` 注释写「本App暂不执行」，与 `:403-404` 实际执行**矛盾**（注释过期） |
| 全局设置项（深度/预算/最少激活/递归/插入策略/溢出提醒/组评分） | 完整实现 | `data/datastore/PreferencesStore.kt:161-170,292-301`；UI `ui/pages/extensions/PromptPage.kt` | 10 项齐全 |
| 条目编辑器 | 完整实现 | `PromptPage.kt:2000-2100` 区域 + `TavernCharacterCard.kt:1475-1910` | |
| 拖拽排序 | 完整实现 | `PromptPage.kt`（reorderable，README 亦述） | |
| 外置世界书 ↔ 内嵌书双向同步 | 完整实现 | `ui/pages/extensions/PromptVM.kt`、`ui/pages/assistant/detail/AssistantDetailVM.kt:200,247-250`、`ChatVM.kt:502-510` | |
| 官方序列化兼容 | 完整实现 | 导入 `ExportSerializer.kt:324-385`；导出（进角色卡）`CardExporter.kt:137-232` | |
| **世界书导出为 ST 世界书 JSON** | **未实现** | `ExportSerializer.kt:281-286`（LorebookSerializer.export 只产本应用 `ExportData`）；全仓 grep `convertWorldInfoToCharacterBook` 仅注释 | README「与酒馆导入导出互通」只对**导入**与**卡内嵌**成立 |
| **向量化 / RAG 激活 `vectorized`** | **未实现** | `TavernScripts.kt:625`（硬编码 `put("vectorized", false)`）；`assets/st-runtime/st-compat/world-info.js:230` 默认 false | 无 embedding 激活路径 |
| **`@@` 装饰器** | **完整实现（v240 W2）** | `data/model/WorldInfoDecorators.kt`（`parseDecorators` 逐字移植 + 派生字段 `worldInfoDecorators` / `contentWithoutDecorators`，不改存储）；扫描判定 `PromptInjectionTransformer.kt`（`@@activate` → 强制激活、`@@dont_activate` → 跳过，均先于关键词判定）；`WorldInfoDecoratorTest` 9 条用例 | ✅ 官方 2 个全部对齐（`@@@` 转义细节亦逐字复刻）；剥壳只作用扫描副本，导入/导出无损（`WorldInfoDecoratorLosslessTest`：47 条真实卡往返 + `@@` 行原样保留） |
| `automation_id` | **仅数据兼容** | `Assistant.kt:477` 注释「本App暂不执行，仅保留」；`strings.xml:1612`「Data only, not executed yet」 | 编辑/导出有，不执行 |
| `display_index` / `display_position` | **仅数据兼容（App 内）** | 字段 `Assistant.kt:478-479`；编辑器 `PromptPage.kt:2036-2048`；导出 `CardExporter.kt:213-216`；仅 JS 侧导出 `TavernScripts.kt:640` | App 内条目展示顺序未读取该字段 |

---

## 3. 提示词管理与预设

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| ST **Chat Completion** 预设导入 | 基本实现 | `data/export/ExportSerializer.kt:144-236`（`prompts` + `prompt_order` → 多条 `ModeInjection`）；DTO `:239-272` | 只取 `prompts[]`；marker 条目跳过；`injection_position`/`injection_depth`/`role` 映射 |
| prompt manager 条目启停 / 顺序 | 基本实现 | `ExportSerializer.kt:169-205`（优先 `character_id=100001` 全局组，缺失回退 `prompts` 原序，启停只认 `prompt_order.enabled`） | 顺序/启停语义对齐官方 |
| ST 预设**导出** | **未实现** | `ExportSerializer.kt:83-88`（ModeInjectionSerializer.export → 本应用 `ExportData`） | 无 ST 格式回写 |
| ST **Text Completion / Kobold / NovelAI** 预设 | **未实现** | `ExportSerializer.kt:159-160`（`prompts` 为空即 `return null`） | 无 `preset_settings` / `instruct` 段解析 |
| 主提示（main prompt） | 完整实现 | `data/ai/GenerationHandler.kt:156-172`（官方拆分路径）；`Assistant.kt:755` 注释 | |
| 后置提示 / PHI | 完整实现 | `AssistantImporter.kt:841-850` | |
| 系统提示 | 完整实现 | `Assistant.kt:28`；UI `ui/pages/assistant/detail/AssistantPromptPage.kt:177` | |
| instruct 模板 | 完整实现（手动编辑） | 模型 `data/model/InstructTemplate.kt:24-58`（19 字段）；UI `AssistantFormattingPage.kt:236-395`；应用 `data/ai/transformers/InstructModeTransformer.kt`；宏 `MacroDefinitions.kt:625-661` | `stop_sequence` / `user_alignment_message` / `last_system_sequence` 明确未实现（`InstructTemplate.kt:20`） |
| ST instruct 预设导入 | **未实现** | 全仓无 instruct preset 解析 | 只能手填 |
| context template | 完整实现 | `Assistant.kt:38`；UI `AssistantFormattingPage.kt:130`；`GenerationHandler.kt:618-625` | 注意 `GenerationHandler.kt:156` 注释写「contextTemplate 无 UI 入口」，与 `AssistantFormattingPage.kt:130` **矛盾**（注释过期） |
| **squash system messages** | **未实现** | Kotlin 侧 0 命中；仅 JS shim 空壳 `assets/st-runtime/st-compat/openai.js:35,129`（`squashSystemMessages() { return this; }`） | 不合并 system 消息 |
| tokenizer 选择 | **完整实现（v240 W5）** | Kotlin 原生 tiktoken BPE：`data/st/tokenizer/TikToken.kt`（cl100k / o200k 官方 pat_str + 词表单次加载 + chunk 缓存）；设置项 `Settings.tokenizerMode`（UI：世界书设置 → 分词器）；调用点 `PromptInjectionTransformer.kt`（预算 / maxContext / 递归 token） | ✅ 18 个中英混合样例与官方 tiktoken 数值逐个一致；off / 词表缺失自动回退启发式 |

---

## 4. 生成控制

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| 流式 | 完整实现 | `Assistant.kt:33`（`streamOutput`）；上游链路 | |
| **stop strings** | **未实现** | 全仓无 stop 参数注入；`InstructTemplate.kt:20` 明确「未实现」 | 无用户可配停止串 |
| 采样参数 | 基本实现 | `Assistant.kt:29-30,44`（temperature / topP / maxTokens） | 无 top_k / freq_penalty / presence_penalty / seed |
| 上下文长度与消息裁剪 | 完整实现 | `Assistant.kt:31-32`（`contextMessageLimit`，阶梯截断）；`GenerationHandler.kt:607`（`limitedChat`） | |
| swipe / 重 roll | 完整实现 | `data/st/script/StSlashExecutor.kt:239-244`；宿主 `ui/pages/chat/ChatSlashHost.kt`（swipe 实现） | |
| branch | 完整实现 | `StSlashExecutor.kt:273-276`（`/branch-create`）；`ChatSlashHost.kt:451-467` | 上游消息节点分支树 |
| continue | 完整实现 | `StSlashExecutor.kt:181-184`；`service/ChatService.kt:912` | |
| impersonate | 完整实现 | `StSlashExecutor.kt:186-189`；`ChatService.kt:1007-1015` | |
| regenerate | 完整实现 | 上游能力（`ChatVM`/`ChatService`） | |
| edit / delete message | 完整实现 | 上游能力；`web/routes/ConversationRoutes.kt:303`（编辑成新分支） | |
| 消息隐藏 `/hide` `/unhide` | 完整实现 | `StSlashExecutor.kt:229-237`；`StSlashHost.hideMessages` `StSlashExecutor.kt:40` | |
| checkpoint（书签） | 完整实现 | `StSlashExecutor.kt:248-276`；`ChatService.kt:2086-2175`；`data/model/CheckpointLink.kt`；`data/db/migrations/Migration_26_27.kt` | 六个 `/checkpoint-*` 全在 |

---

## 5. 人设 Persona

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| 数据模型 | 完整实现 | `data/model/Persona.kt:8-19`（name/title/description/position/depth/role/avatar/enabled/lockedCharacterIds） | |
| 五档位置枚举 | 完整实现 | `Persona.kt:22-27`（IN_PROMPT / TOP_OF_CHAT / BOTTOM_OF_CHAT / AT_DEPTH / NONE） | |
| IN_PROMPT 注入 | 完整实现 | `GenerationHandler.kt:609-637`（插在 before_char 世界书之后、角色卡字段之前） | |
| AT_DEPTH 注入 | 完整实现 | `GenerationHandler.kt:639-652` | |
| TOP / BOTTOM 注入 | 完整实现 | `data/ai/transformers/AuthorsNoteTransformer.kt:19-20,39-43`（并入导演备注并跟随其节奏） | |
| NONE（不注入） | 完整实现 | `GenerationHandler.kt:654`（`else -> base`） | |
| role（system/user/assistant） | 完整实现 | `GenerationHandler.kt:646-650` | |
| depth | 完整实现 | `GenerationHandler.kt:640` | |
| 按角色绑定 / persona 锁 | 完整实现 | `lockedCharacterIds` 判定 `GenerationHandler.kt:610-611`、`AuthorsNoteTransformer.kt:16-17` | |
| `{{persona}}` 宏 | 完整实现 | `MacroDefinitions.kt:454`；`GenerationHandler.kt:625` | |
| 多 Persona + 切换 UI | 完整实现 | `data/datastore/PreferencesStore.kt:195-196,316-317,812-813`；UI `ui/pages/setting/PersonaPage.kt` | |

---

## 6. 作者备注 Author's Note

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| 数据模型（内容/位置/深度/角色/间隔/开关） | 完整实现 | `PreferencesStore.kt:814-819` | |
| 位置（Before Prompt / In Prompt / In Chat） | 完整实现 | `AuthorsNoteTransformer.kt:54-75` | |
| 官方间隔语义（1=每次 / N=用户消息倍数 / 0=关） | 完整实现 | `AuthorsNoteTransformer.kt:27-37` | 与官方 authors-note.js 一致 |
| 注入角色 | 完整实现 | `AuthorsNoteTransformer.kt:47-52` | |
| 深度 | 完整实现 | `AuthorsNoteTransformer.kt:64-74` | |
| 真注入 | 完整实现 | `AuthorsNoteTransformer.kt:54-75` | |
| UI + 预设模板 | 完整实现 | `ui/pages/setting/AuthorsNotePage.kt`（含多个内置模板文案 `:309,321,473,505,529`） | |
| `{{authorsNote}}` / `{{charAuthorsNote}}` / `{{defaultAuthorsNote}}` 宏 | **完整实现（v240 W1）** | `data/st/macro/MacroDefinitions.kt`（registerEnv 注册 3 个）+ `MacroEnv.AuthorNotes` 通道（`MacroTypes.kt`）+ `StMacroSupport.kt` 映射；`MacroEngineTest` 3 条用例 | `authorsNote`/`defaultAuthorsNote` ← `Settings.authorNote`；`charAuthorsNote` 无数据源恒空串（官方按角色卡绑定的语义差异已在代码注释登记） |

---

## 7. 群聊 Group Chat

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| 数据模型 | 完整实现 | `data/model/GroupChat.kt:10-39`（成员/策略/生成模式/禁言/权重/自响应/延迟/轮数/模型覆盖） | |
| 四种选人策略 | 完整实现 | `data/model/GroupSpeakerSelector.kt:9-13`（NATURAL/LIST/MANUAL/POOLED）+ `:48-135` | NATURAL 用 talkativeness 掷骰 + 名字分词匹配，对齐官方 |
| 自动接话（轮数/延迟/用户发言打断） | 完整实现 | `ui/pages/chat/GroupChatPage.kt:676-706,1245-1268`；`GroupChat.kt:20-21` | |
| 群内人设 / 成员独立模型 | 基本实现 | 成员各自是 `Assistant`（含 persona 绑定与 `chatModelId`）；群级模型 `GroupChat.kt:23` | 无「群专属 persona」独立概念 |
| 禁言成员 | 完整实现 | `GroupChatPage.kt:1057-1080`；`MacroEngine.kt:899`（`{{groupNotMuted}}`） | |
| 生成模式（SWAP/APPEND/APPEND_DISABLED） | 完整实现 | `GroupChat.kt:35-39` | |
| 群聊 UI | 完整实现 | `GroupChatPage.kt`（1167 行）、`GroupChatListPage.kt`（242 行） | |
| **group greeting** | **仅数据兼容** | `GroupChatPage.kt:301` 只用 `alternateGreetings`；`groupOnlyGreetings` 无群聊引用 | 见 §1 |
| **scenario override（群/成员级）** | **未实现** | 无实现证据 | |
| 群聊持久化 | 完整实现 | `GroupChat.kt:44-52`、`PreferencesStore.kt` 序列化 | |

---

## 8. 宏

引擎：`app/src/main/java/me/rerere/rikkahub/data/st/macro/`（`MacroEngine.kt` 866 行 CST walker 移植、`MacroDefinitions.kt` 890 行、`MacroVariables.kt`、`MacroHash.kt`、`StRandom.kt`、`MacroRegistry.kt`）

### 8.1 官方宏对照（依据官方 Macros 文档逐条比对代码注册表）

| 官方宏 | 状态 | 证据 |
|---|---|---|
| `{{user}}` `{{char}}` `{{group}}` `{{groupNotMuted}}` `{{charIfNotGroup}}` `{{notChar}}` | 完整实现 | `MacroDefinitions.kt:443-447` |
| `{{description}}` `{{personality}}` `{{scenario}}` `{{persona}}` | 完整实现 | `:451-454` |
| `{{charPrompt}}` `{{charInstruction}}` `{{charDepthPrompt}}` `{{charCreatorNotes}}` `{{charVersion}}` | 完整实现 | `:449-465` |
| `{{charFirstMessage}}`（含 `::N`） | 完整实现 | `:460-464`；`MacroEngine.kt:890` |
| `{{mesExamples}}` `{{mesExamplesRaw}}` | 完整实现 | `:455-456` |
| `{{original}}` | 完整实现 | `:470` |
| `{{input}}` | 部分实现 | `:85`（恒返回空串，注释说明无输入框访问） |
| `{{lastMessage}}` `{{lastMessageId}}` `{{lastUserMessage}}` `{{lastCharMessage}}` | 完整实现 | `:387-393` |
| `{{firstIncludedMessageId}}` `{{firstDisplayedMessageId}}` `{{lastSwipeId}}` `{{currentSwipeId}}` `{{allChatRange}}` | 完整实现 | `:395-411` |
| **`{{summary}}`** | **未实现** | 无注册 |
| `{{time}}`（含 UTC±n） `{{date}}` `{{weekday}}` `{{isotime}}` `{{isodate}}` `{{datetimeformat}}` `{{idleDuration}}` `{{timeDiff}}` | 完整实现 | `:213-254` |
| 变量族 `{{getvar}}` `{{setvar}}` `{{addvar}}` `{{incvar}}` `{{decvar}}` `{{hasvar}}` `{{deletevar}}` + global 全套 + `{{varexists}}` `{{flushvar}}` `{{globalvarexists}}` `{{flushglobalvar}}` | 完整实现 | `:530-578` |
| `{{setvarkey}}` `{{getvarkey}}` `{{setglobalvarkey}}` `{{getglobalvarkey}}` | 完整实现（超出官方文档表） | `:581-620` |
| `{{random}}` `{{pick}}` `{{roll}}` | 完整实现 | `:112-156`（pick 用 ARC4 bit-exact `StSeedrandom`） |
| `{{maxPrompt}}` `{{maxContextTokens}}` `{{maxResponseTokens}}` `{{model}}` `{{isMobile}}` `{{lastGenerationType}}` `{{hasExtension}}` | 完整实现 | `:87-97,467,472,510-513` |
| `{{systemPrompt}}` | 完整实现 | `:659-661` |
| **`{{defaultSystemPrompt}}`** | 无注册（**官方 1.18 也没有**） | rikkaST 仅在 `MacroDefinitions.kt:24` 注释里被提及；官方源码无注册点 → 不是对照项 |
| **`{{authorsNote}}` `{{charAuthorsNote}}` `{{defaultAuthorsNote}}`** | **完整实现（v240 W1）** | `MacroDefinitions.kt` registerEnv 注册；`MacroEngineTest`（W1 三条用例） |
| instruct 全系列（`instructUserPrefix` … `instructStop` `instructUserFiller` `instructSystemInstructionPrefix`） | 基本实现 | `:625-654`；`instructStop`/`userFiller` 等宿主未覆盖字段恒空串（`:630` 注释） |
| `{{chatSeparator}}` `{{chatStart}}` | 完整实现 | `:656-657` |
| **`{{reasoningPrefix}}` `{{reasoningSuffix}}` `{{reasoningSeparator}}`** | 无注册（**官方 1.18 也没有**） | 官方源码无注册点 → 不是对照项 |
| **`{{charPrefix}}` `{{charNegativePrefix}}`**（生图前缀） | **未实现**，但**属 stable-diffusion 扩展宏、非核心** | 官方由 `extensions/stable-diffusion/index.js:5989,5993` 注册 → 该扩展在 rikkaST 本来就跑不了 |
| `{{newline}}` `{{space}}` `{{noop}}` `{{trim}}` `{{reverse}}` `{{banned}}` `{{outlet}}` `{{//}}` | 完整实现 | `:56-109,159-166` |
| `{{if}}` `{{else}}` `{{/if}}`（scoped + inline + `!` 反转） | 完整实现 | `:76-82`；`MacroEngine.kt:169-207,852-866` |
| 宏 flag（`/` 闭合、`#` 保留空白） | 完整实现 | `MacroEngine.kt:182-195`（`MacroFlags.parse`） |
| 变量简写 `.var` / `$var` 及全部运算符（`=` `++` `--` `+=` `-=` `\|\|` `??` `\|\|=` `??=` `==` `!=` `>` `>=` `<` `<=`） | 完整实现 | `MacroEngine.kt:712-760`（`evalVariableExpr`，含 `-=` 数值校验告警 `:744`） |
| 大小写不敏感 / 空白宽容 / 嵌套 / 未知宏原样保留 | 完整实现 | `MacroRegistry.kt:15,31,37`（lowercase）；`MacroEngine.kt:26`（MAX_DEPTH=32）、`:580-586`（arity/类型校验失败保留原文） |
| 转义 `\{\{` `\}\}` | 完整实现 | `MacroEngine.kt:64,83`（`RE_UNESCAPE_BRACES`） |
| legacy `<USER>` `<BOT>` `<CHAR>` `<GROUP>` `<CHARIFNOTGROUP>` `{{time_UTC±n}}` | 完整实现 | `MacroEngine.kt:58-76` |

**自研扩展宏（官方没有）**：`{{setvarkey}}` / `{{getvarkey}}` / `{{setglobalvarkey}}` / `{{getglobalvarkey}}`（`MacroDefinitions.kt:581-620`）——这是本分支超出官方宏表的部分。

**判定（已用官方 1.18.0 源码复核并修正）**：

官方核心宏共 **73 个** = 宏注册表 **70 个**（`public/scripts/macros*` 里 `registerMacro(...)` 去重计数）+ `public/scripts/authors-note.js:606-614` 注册的 **3 个**（`authorsNote` / `charAuthorsNote` / `defaultAuthorsNote`，该模块被核心 `world-info.js:6` import，属核心而非扩展）。

rikkaST 侧实测：`MacroDefinitions.kt` 注册 + 别名去重后 **102 个**（v240 W1 新增 3 个），其中**官方 73 个核心宏全部覆盖（73/73）**，另有 29 个自有宏/别名。**核心宏已无缺口**。

> ⚠️ 本节早先写的「缺 10 个」**是错的**，已在官方源码中逐条核对：
> - `{{summary}}` 由 **memory 扩展**注册（`extensions/memory/index.js:1127`）—— 不是核心宏；
> - `{{charPrefix}}` / `{{charNegativePrefix}}` 由 **stable-diffusion 扩展**注册（`extensions/stable-diffusion/index.js:5989,5993`）—— 不是核心宏；
> - `{{defaultSystemPrompt}}`、`{{reasoningPrefix}}` / `{{reasoningSuffix}}` / `{{reasoningSeparator}}` 在官方 1.18 源码中**根本不存在**注册点。
>
> 另：**README「宏引擎 2.0」的自述基本属实，且实际是 ST 1.18 宏引擎的移植而非自创**（README 没说这一点，属「代码有、README 没写」）。

---

## 9. 斜杠命令 / STscript

解析器：`data/st/script/StSlashParser.kt`（172 行）· 执行器：`data/st/script/StSlashExecutor.kt`（546 行，v240 W4 补闭包/`/run`/`/abort`/`/delay`/`/switch`）· 闭包语法：`data/st/script/StClosure.kt`（228 行）· UI 命令表：`ui/components/ai/SlashCommands.kt`（595 行，33 条）· 宿主：`ui/pages/chat/ChatSlashHost.kt`

### 9.1 实际实现的命令（执行器 + UI 直连合计）

| 命令 | 证据 |
|---|---|
| `/pass` `/return` `/echo` | `StSlashExecutor.kt:126-131` |
| `/setvar` `/getvar` `/addvar` `/incvar` `/decvar` `/flushvar` `/listvar` | `StSlashExecutor.kt:134-148` |
| `/send` `/sendas` `/sys` `/sysgen` | `StSlashExecutor.kt:151-176` |
| `/trigger` `/continue` `/impersonate` `/gen` | `StSlashExecutor.kt:179-206` |
| `/persona` `/persona-set` `/rename-char` | `StSlashExecutor.kt:209-221` |
| `/js` `/tavern` | `StSlashExecutor.kt:224-226` |
| `/hide` `/unhide` `/swipe` | `StSlashExecutor.kt:229-244` |
| `/checkpoint-create/-go/-exit/-parent/-get/-list` `/branch-create` | `StSlashExecutor.kt:248-276` |
| `/run` `/call` `/exec`（闭包字面量 / 变量里的闭包 / 命名参数 `{{arg::key}}`） | `StSlashExecutor.kt` + `StClosure.kt`（v240 W4） |
| `/abort` `/delay` `/wait` `/sleep` `/switch`（闭包分派扩展） | `StSlashExecutor.kt`（v240 W4） |
| `/expression-fallback`（表情立绘兜底标签） | `StSlashExecutor.kt` + `ChatSlashHost.kt`（v240 W3） |
| `/help` `/char-update` `/char-duplicate` `/reroll-pick` | UI 侧：`SlashCommands.kt:132,228,245,315`；`ui/components/ai/ChatInput.kt:1557-1600` |

**合计 38 条**（README 声称「21 个内置命令」→ **README 低估**，实际更多；v240 新增 `/run` `/abort` `/delay` `/switch` `/expression-fallback` 5 条基础命令）。

### 9.2 解析器能力

| 能力 | 状态 | 证据 |
|---|---|---|
| `/` 前缀 + 命令名小写化 | 完整实现 | `StSlashParser.kt:95-99` |
| 命名参数 `key=value` / `key="quoted"` | 完整实现 | `StSlashParser.kt:113-135` |
| 引号感知的 `\|` 切分（含 `{{}}` `()` `[]` 内不切） | 完整实现 | `StSlashParser.kt:35-87` |
| `\|\|` 禁用管道注入 | 完整实现 | `StSlashParser.kt:74-78` |
| 管道 `\|` 串联 + `{{pipe}}` | 完整实现 | `StSlashExecutor.kt:98-116` |
| 首段不注入 / 空参数自动接管道 | 完整实现 | `StSlashExecutor.kt:295-303` |
| 转义引号 | 完整实现 | `StSlashParser.kt:48-54,150-171` |
| **闭包 `{: ... :}` / 命名闭包参数** | **完整实现（v240 W4）** | `data/st/script/StClosure.kt`（`{: :}` 解析、头部 `key=value` 命名参数声明、`{{arg::key}}` 替换、嵌套 `|` 保护）；`StSlashExecutor.runClosure`；`StClosureStore`（按会话作用域存闭包）；`StClosureTest` 26 条用例 |
| **多行脚本 / 注释 `//` `/# #/`** | **未实现** | 解析器按单行 `\|` 切分 |
| **`/run`** | **完整实现（v240 W4）** | `StSlashExecutor.kt` `runClosureCommand`（别名 `/call` `/exec`）；支持闭包字面量、`/setvar fn={: ... :}` 存入的闭包、递归调用（深度上限 32 防栈溢出）；命名参数 `{{arg::key}}`（未提供展开空串，对齐官方 `arg::*` 通配默认） |
| **`/if` `/while` `/times` `/break`** | **官方 1.18 没有这 4 个命令（已源码核实）** | `slash-commands.js` 无注册；酒馆真正的控制流 = 闭包 + `/run` + `{{if}}` 宏（rikkaST 两者均已有） |
| **`/abort`** | **完整实现（v240 W4）** | `StSlashExecutor.kt`：停止整个批次（闭包内外一致），reason 记录；`StClosureTest` 2 条用例 |
| **`/let` `/var`** | **未实现（官方 1.18 亦无此命令）** | v240 已核实官方 `slash-commands.js` 无注册；不在本批范围 |
| **`/delay` `/wait` `/sleep`** | **完整实现（v240 W4）** | `StSlashExecutor.kt` `delayBestEffort`：非 UI 线程真实 `Thread.sleep`；UI 线程跳过（防 ANR，差异登记 DIVERGENCE §W4）；单次上限 30s |
| **`/qr` / QR 集合** | **未实现** | 无分支；全仓 grep `"/qr"` 0 命中 |
| **文本处理族**（`/len` `/substr` `/split` `/join` `/replace` `/regex` `/trim` `/upper` `/lower` `/sort` `/fuzzy` `/test` `/math` `/round`） | **未实现** | 无分支 |
| **世界书族**（`/world` `/getchatbook` `/findentry` `/wi-*`） | **未实现** | 无分支 |
| **`/inject` `/flushinject` `/messages`** | **未实现** | 无分支 |
| **`/api` `/model` `/preset` `/context` `/instruct` `/sysprompt` `/stop` `/seed`** | **未实现** | 无分支（`/abort` 见上：v240 W4 已实现） |
| **角色管理族**（`/char-create` `/char-get` `/char-delete` `/char-import` `/char-export` `/char-list` `/char-find`） | **未实现**（仅 `/char-update` `/char-duplicate` `/rename-char`） | `SlashCommands.kt:228,245,252` |
| **`/persona-create` `/persona-list` `/persona-get` `/persona-delete` `/persona-sync`** | **未实现**（仅 `/persona-set`） | `StSlashExecutor.kt:209-211` |
| **群聊族** `/group-*` | **未实现** | 无分支 |
| **媒体族** `/imagine` `/sd` `/tts` `/caption` `/speak` `/expression` `/emote` | **未实现** | 无分支 |
| **扩展族** `/extension` `/extensions` `/regex` | **未实现** | 无分支 |
| **`/popup` `/input` `/pick` `/buttons` `/sing` `/bg` `/themes`** | **未实现** | 无分支 |

**判定（v240 更新）**：STscript 的**管道与参数层完整**；**语言层已补闭包 / `/run` / `/abort` / `/delay` / `/switch` / `{{arg::key}}` / 作用域隔离**（`StClosure.kt` + `StClosureStore`），仍缺 **QR 集合（`/qr`、QR 变量作用域）** 与 `/let` `/var`；文本处理族 / 世界书族 / 角色管理族等外围命令仍缺。

---

## 10. 正则脚本 Regex

模型：`data/st/regex/RegexScript.kt` · 引擎：`data/st/regex/RegexScriptEngine.kt`（452 行）· 来源：`RegexScriptSources.kt` · JS 侧：`assets/st-runtime/st-compat/extensions/regex/engine.js`

| 子项 | 状态 | 证据 |
|---|---|---|
| 数据模型全字段 | 完整实现 | `RegexScript.kt:15-37`（id/scriptName/findRegex/replaceString/trimStrings/placement/disabled/markdownOnly/promptOnly/runOnEdit/substituteRegex/minDepth/maxDepth） |
| 作用域：global / character（卡内嵌） | 完整实现 | `RegexScriptSources.kt:22-24`（`global + embeddedRegexScripts`）；卡内解析 `:32-42` |
| 作用域：preset 绑定 | 部分实现 | 仅 JS 侧 `assets/st-runtime/st-compat/extensions/regex/engine.js:117,142`（presetManager 读写）；Kotlin 生成链未接 |
| `findRegex` `/pattern/flags` 解析（g/i/m/s/u） | 完整实现 | `RegexScriptEngine.kt:101-117`；`isGlobalPattern` `:290-294` |
| 捕获组 `$1..$99` / `$<name>` / `{{match}}` | 完整实现 | `RegexScriptEngine.kt:296-297,355-364` |
| `trimStrings` | 完整实现 | `RegexScriptEngine.kt:344-353` |
| 替换串内宏替换 | 完整实现 | `RegexScriptEngine.kt:365-366`（末尾整体 `substitute`） |
| `substituteRegex` NONE/RAW/ESCAPED | 完整实现 | `RegexScriptEngine.kt:313-319`；`sanitizeRegexMacro` `:411-430` |
| `minDepth` / `maxDepth` | 完整实现 | `RegexScriptEngine.kt:395-400` |
| `markdownOnly` / `promptOnly` | 完整实现 | `RegexScriptEngine.kt:390-393` |
| `runOnEdit` | 完整实现 | `RegexScriptEngine.kt:394` |
| placement USER_INPUT | 完整实现 | 提示词侧 `StRegexInputTransformer.kt:56`；显示侧 `ChatMessage.kt:448` |
| placement AI_OUTPUT | 完整实现 | `StRegexInputTransformer.kt:57`；`ChatMessage.kt:448` |
| placement SLASH_COMMAND | 完整实现 | `ChatSlashHost`/`ChatService.kt:571,691` |
| placement REASONING | 完整实现 | `StRegexInputTransformer.kt:78`；显示 `ChatMessageReasonsing.kt:177` |
| **placement WORLD_INFO** | **未实现（UI 可选但无调用点）** | 常量 `RegexScript.kt:48`、UI 开关 `ui/pages/extensions/RegexScriptsPage.kt:396-397`，但全仓**无** `RegexPlacement.WORLD_INFO` 的引擎调用点 | 勾了不生效 |
| placement MD_DISPLAY | 仅数据兼容 | `RegexScript.kt:42`（官方已废弃，可接受） |
| 正则引擎 JS 语义兼容（`.` 不匹配 `\r`） | 完整实现（超出官方） | `RegexScriptEngine.kt:86-99`（`UNIX_LINES` 修正 CRLF 卡） |
| 不定长 lookbehind 降级 | 完整实现（超出官方） | `RegexScriptEngine.kt:119-248`（Java 正则限制的绕行） |
| UI（列表/编辑/导入导出/启停） | 完整实现 | `ui/pages/extensions/RegexScriptsPage.kt:93-217` |
| 从 ST 备份导入 `extension_settings.regex` | 完整实现 | `data/st/import/StArchiveImporter.kt:56-64`；`StSettingsImport.kt:45,157-169` |
| 流式过程中应用 | 完整实现 | `RegexOutputTransformer.kt`（1.5KB）+ 显示侧 `ui/components/message/StRegexedText.kt:28` |

**判定**：这是全项目完成度最高的模块之一。唯一实质缺口是 `WORLD_INFO` placement 无落点。

---

## 11. 快速回复 Quick Replies

| 子项 | 状态 | 证据 |
|---|---|---|
| 数据模型 | 部分实现 | `data/model/Assistant.kt:89-96`（`QuickMessage{id,title,content,autoExecute}`） |
| 自动执行（autoExecute） | 完整实现 | `ui/components/ai/ChatInput.kt:1637-1640`；`ChatPage.kt:811-834`（`/`开头走 STscript，纯文本直接发送） |
| 管理 UI | 完整实现 | `ui/pages/extensions/QuickMessagesPage.kt`、`QuickMessagesVM.kt` |
| 助手级启用绑定 | 完整实现 | `Assistant.kt:41`（`quickMessageIds`）；`ExtensionSelector.kt:157-167` |
| 从 ST `QuickReplies/*.json` 导入（`qrList`） | 完整实现 | `StArchiveImporter.kt:66-86` |
| **QR 集合（Quick Reply Sets）** | **未实现** | 无 set 概念，只有扁平列表 |
| **`/qr` 命令 / QR 变量作用域** | **未实现** | `StSlashExecutor.kt` 无分支 |
| **`contextMenu` 等 ST QR 高级结构** | **未实现** | `StArchiveImporter.kt:68` 注释「contextMenu 等嵌套结构忽略」 |

---

## 12. 变量

| 子项 | 状态 | 证据 |
|---|---|---|
| global 作用域 | 完整实现 | `data/st/runtime/TavernVariableStore.kt:47,102,125-129` |
| chat 作用域 | 完整实现 | `TavernVariableStore.kt:46,104-106,131-136` |
| message 作用域 | 基本实现 | `TavernVariableStore.kt:49-50,108-116`（按会话存一个 JSON blob，非按消息/ swipe 索引） |
| local（宏引擎内存） | 完整实现 | `data/st/macro/MacroVariables.kt:23-59` |
| `/setvar` 等命令 | 完整实现 | `StSlashExecutor.kt:134-148`；`ui/components/ai/MacroVarSlashOps.kt` |
| 宏变量族 | 完整实现 | `MacroDefinitions.kt:530-620` |
| 持久化（`vars.json`，去抖落盘） | 完整实现 | `TavernVariableStore.kt:52-53,244-283` |
| 旧宏变量迁移 | 完整实现 | `TavernVariableStore.kt:85-98` |
| JS 侧变量契约（JSR） | 完整实现 | `TavernRuntimeManager.kt:839-877`（getMessageVariables/replaceChatVariables/replaceGlobalVariables/getLocalVariableJson/getGlobalVariableJson） |
| JSR `injectPrompts` / `uninjectPrompts` | **完整实现（v241）** | shim `assets/st-runtime/shims/inject-shims.js`（逐字对齐 `refs/C-st-ext/N0VI028__JS-Slash-Runner/src/function/inject.ts`）；宿主桥 `TavernJsBridge.injectPrompts/uninjectPrompts`；运行态 `InjectedPromptStore`；生成链 `InjectedPromptsTransformer` + `InjectedPromptCleanupTransformer`；`InjectedPromptsTest` 10 条 | `in_chat` 按 depth/role 注入并发送；`none + should_scan` 只参与世界书扫描、发送前整条丢弃；`once` 一次性；`filter`（JS 函数）跨语言不支持（已登记 DIVERGENCE §H.4） |
| **MVU 兼容** | 完整实现 | `assets/st-runtime/mvu/bundle.js`（560KB，真 MVU Standalone 打包）；宿主 `TavernRuntimeManager.kt:254-262,590,708`；`data/st/runtime/UpdateVariableParser.kt`；`UpdateVariableOutputTransformer.kt` |
| `stat_data` | 完整实现 | `assets/st-runtime/runtime.js:2666-2668,2726-2730,2866`（读取/诊断 `stat_data`） |
| MVU iframe + shim 桥 | 完整实现 | `assets/st-runtime/runtime.js:2097-2490`（`MVU_BUNDLE_URL`、iframe、超时/重试/自检） |

---

## 13. 扩展与插件

| 子项 | 状态 | 证据 |
|---|---|---|
| 第三方 ST 扩展安装（zip / URL / manifest 树） | 完整实现 | `data/st/extensions/TavernExtensions.kt:223-364`（zip）、`:318-363`（URL）、`:582-616`（manifest 递归下载） |
| 扩展加载目录与安全校验 | 完整实现 | `TavernExtensions.kt:40,137-183`（`isSafeExtensionSegment`/canonical path） |
| 扩展运行时（WebView + ST DOM/ESM 契约） | 完整实现 | `TavernRuntimeManager.kt:313-472`（asset 拦截、`/scripts/extensions/third-party/` 映射、CDN 反版本化）；`assets/st-runtime/runtime.js`（156KB） |
`assets/st-runtime/shims/` 11 个（event/chat/variable/lorebook/ejs/button/util/math/constants/inject） |
| 扩展设置页（真实 WebView 宿主） | 完整实现 | `ui/pages/extensions/TavernExtensionSettingsPage.kt:52-70`；`TavernExtensionsPage.kt` |
| 扩展启停 | 完整实现 | `TavernExtensions.kt:186-210`（`buildRuntimeExtensionsJson`）；`Settings.tavernThirdPartyDisabled` |
| 扩展设置持久化 | 基本实现 | `StSettingsImport.kt:353-355` 自述「`tavernExtensionSettings` / `tavernThirdPartyDisabled` 在 PreferencesStore.update 里没有对应 key，重启会丢」 | **已知缺口（代码自己承认）** |
| **ST 内置扩展：expressions（表情/立绘/sprites）** | **完整实现（v240 W3，原生重写）** | `data/st/expressions/`（28 标签 + 官方 llm 提示词 + 解析/兜底 + 按 messageId 缓存 + 10s 节流）、`ui/pages/chat/CharacterExpression.kt`（Compose 叠加层 + Crossfade）、`ui/pages/setting/SettingPreferencesExpressionPage.kt`（设置 + 立绘管理）、`SpriteRepository`（filesDir/sprites/<assistantId>/）、V3 assets 内嵌立绘导入、`/expression-fallback` | ✅ llm 分类（无需服务端）；local/BERT/extras/webllm 与 visual novel 群聊并排未做 |
| **ST 内置扩展：vectors（向量化）** | **未实现** | 无 `st-compat/extensions/vectors*` |
| **ST 内置扩展：caption（图像识别）** | **未实现（有等价替代）** | 无 ST caption；RikkaHub 自有 `data/ai/transformers/OcrTransformer.kt:28-119` + `data/ai/prompts/OcrPrompt.kt` |
| **ST 内置扩展：summarize** | **未实现** | 无 `st-compat/extensions/summarize*`；`{{summary}}` 宏亦缺 |
| **ST 内置扩展：translate** | **未实现（有等价替代）** | 无 ST 扩展；RikkaHub 自有 `GenerationHandler.kt:776-844` + `ui/pages/translator/` |
| **ST 内置扩展：TTS / image-gen** | **未实现为 ST 扩展（有等价替代）** | RikkaHub 自有 TTS provider（`ui/pages/setting/components/TTSProviderConfigure.kt`，含 ElevenLabs `:783-813`）与生图页 `ui/pages/imggen/` |
| 内置扩展注册面（`assets/st-runtime/st-compat/extensions/`） | 仅 1 个 | 目录下只有 `regex/engine.js` |

**判定（v240 更新）**：**「跑第三方扩展的宿主」做得非常好**，ST 自带扩展中 **expressions 已原生重写**（llm 分类 + 立绘，v240 W3）；vectors / caption / summarize 仍未移植。用户从酒馆带来的 `extension_settings` 里 expressions 配置现在能对应到原生能力（其余仍只透传不生效）。

---

## 14. 多媒体

| 子项 | 状态 | 证据 | 备注 |
|---|---|---|---|
| 图像生成 | 完整实现（**非 ST 扩展**） | `ui/pages/imggen/ImgGenVM.kt:136-238`、`ImgGenPage.kt`；模型 `Settings.imageGenerationModelId` `PreferencesStore.kt:232,761` | 走 provider 的 `generateImage`，**无 SD WebUI / ComfyUI / DALL-E 专用适配** |
| ST Image Generation 扩展 / `/sd` `/imagine` | 未实现 | 无 ST 扩展；斜杠命令无 | 用户已排除此项 |
| TTS | 完整实现（**非 ST 扩展**） | `TTSProviderSetting`（含 `ElevenLabs` `TTSProviderConfigure.kt:783-813`）；`TTSAutoPlay` `ChatPage.kt:553` | 无 Edge-TTS / GPT-SoVITS / XTTS 专用 provider（`grep` 0 命中） |
| ST TTS 扩展 / `/tts` `/speak` | 未实现 | 无 ST 扩展；斜杠命令无 | 用户已排除此项 |
| 语音输入 STT | 完整实现（上游） | `speech/` 模块（独立 Gradle module，含 androidTest） | 上游能力 |
| 图像识别 caption / vision | 完整实现（自有实现） | `OcrTransformer.kt:28-119`（含 `ocr_cache.json` 缓存）；`OcrPrompt.kt`；设置 `PreferencesStore.kt:113-114,769-770` | 非 ST caption 扩展 |
| 表情立绘 / sprites（ST expressions） | **完整实现（v240 W3，原生重写）** | `data/st/expressions/` + `ui/pages/chat/CharacterExpression.kt` + 设置页；28 标签 / llm 分类 / 兜底 joy / 按 messageId 缓存 / 10s 节流 | 见 §13 |

---

## 15. 其他

| 子项 | 状态 | 证据 | 备注/缺口 |
|---|---|---|---|
| tokenizer（cl100k / o200k 已内置？） | **Kotlin 侧完整实现（v240 W5）** | 词表 `assets/st-runtime/vendor/data/{cl100k_base,o200k_base}.tiktoken`；Kotlin 原生 BPE `data/st/tokenizer/TikToken.kt`（官方 pat_str + 堆优化 byte_pair_merge + 单次加载 + chunk 缓存）；JS 侧 `st-compat/tokenizers.js` 仍在 | ✅ 18 样例与官方 tiktoken 一致；UI 可选 cl100k / o200k / off |
| chat 导入导出（ST **jsonl**） | **未实现** | 导出仅 Markdown/PNG：`ui/pages/chat/Export.kt:137-143,241-246,389`；导入仅 Chatbox/CherryStudio：`data/sync/importer/`（`ChatboxImporter.kt`、`CherryStudioProviderImporter.kt`） | 无 ST jsonl 读写 |
| 数据备份 / 迁移 | 完整实现 | S3 `data/sync/s3/`、WebDAV `data/sync/webdav/`；`ui/pages/backup/tabs/ImportExportTab.kt`；DB 迁移链 `data/db/migrations/Migration_20_21 … 27_28` | |
| ST 备份 ZIP 全量导入 | 完整实现 | `data/st/import/StArchiveImporter.kt:35-227`（characters / worlds / OpenAI Settings / QuickReplies / settings.json 五类分发） | |
| ST settings.json 导入 | 基本实现 | `data/st/import/StSettingsImport.kt:120-278`（power_user / oai_settings / extension_settings / world_info_settings；未映射字段如实登记 `unmappedPaths`） | 覆盖面窄但**诚实登记缺口** |
| ST settings.json 导出 | 部分实现 | `StSettingsImport.kt:281-343` | 只回写 4 段中已映射的字段 |
| 多角色卡库（character library） | 完整实现 | 上游助手列表页 + `TavernCharacterCard.kt` 详情编辑 | |
| 主题 / UI 主题导入 | 完整实现（上游） | `ui/pages/setting/SettingThemePage.kt:93,172,485-515`（导入主题） | 非 ST 主题格式 |
| 翻译 | 完整实现（自有） | `GenerationHandler.kt:776-844`；`ChatService.kt:1926-1959`；`ui/pages/translator/`；消息内联翻译 `ui/components/message/ChatMessageTranslation.kt` | 非 ST translate 扩展 |
| RAG / 向量记忆 | 完整实现（自有，**与 README 矛盾**） | `data/knowledge/KnowledgeBaseService.kt`（混合 FTS5 + embedding，`rrfMerge` `:881-887`）；`KnowledgeBaseTransformer.kt`；`ChatService.kt:1241,1695`；UI `ui/pages/knowledge/KnowledgeBasePage.kt`；路由 `RouteActivity.kt:602` | README/DIVERGENCE 声称「知识库整套已删」，**实际存在且已接线** |
| Web 搜索 | 完整实现（上游） | `Assistant.kt:57`（`enableWebSearch`）；`ChatService.kt:159-160,1167-1251`；`SearchMode`（OFF/LOCAL/BUILT_IN） | |
| 定时消息 | 完整实现（自有） | `ContextInjectorTransformer.kt:27-38`（cron 消息 `[Scheduled]` 注入）；任务工具 `data/ai/tools/TaskTools.kt` | 非 ST 能力（ST 无原生定时消息） |
| 聊天分支树 | 完整实现 | 上游消息节点模型 + `branch-create` `ChatSlashHost.kt:451-467`；`web/routes/ConversationRoutes.kt:338` | |
| ST Checkpoints（书签） | 完整实现 | 见 §4 | |
| ST 消息隐藏 | 完整实现 | 见 §4 | |

---

## 16. 本分支**上游 RikkaHub 自带**、SillyTavern 没有的能力（简要）

| 能力 | 证据 |
|---|---|
| MCP（Model Context Protocol，含多 transport） | `data/ai/mcp/`（含 `transport/`）；`Assistant.kt:47`（`mcpServers`）；`TavernRuntimeManager` 无关 |
| 技能系统（SKILL.md + `use_skill` + 自动触发 + GitHub 安装/批量/更新检测/注册表） | `data/files/SkillManager.kt`、`SkillRegistry.kt`、`PluginManifest.kt`、`SkillFrontmatterParser.kt`；`data/ai/transformers/SkillAutoTriggerTransformer.kt`；`ui/pages/extensions/SkillsPage.kt`(43KB)、`SkillDetailPage.kt`(33.5KB)；`sample-skills/` |
| 工作区沙箱（workspace） | `workspace/` 模块；`Assistant.kt:58`（`workspaceId`）；`WorkspaceReminderTransformer.kt` |
| Python / JS 双引擎桥接 | `data/ai/python/PythonBridge.kt`、`JsBridge.kt`；`data/ai/tools/PythonTools.kt`；`app/src/main/python/`（含 `calculator.py`）；`Assistant.kt:79`（`jsTimeout`） |
| 工具集扩展（文件/Shell/任务/计算器/数据库查询/网页抓取） | `data/ai/tools/{FileTools,ShellTools,TaskTools,CalculatorTool,DatabaseQueryTool,WebFetchTool,LocalTools}.kt`；`Assistant.kt:48-56` |
| 系统提示组装器 | `data/ai/prompts/SystemPromptAssembler.kt:11-129` |
| Web 服务端（内置 HTTP API） | `web/` 模块；`web/routes/{ConversationRoutes,SettingsRoutes,FolderRoutes}.kt`、`WebApiModule.kt`、`WebServerManager.kt` |
| 多提供商（OpenAI/Anthropic/Google/Mistral/… + 任意兼容端点） | `data/provider/`、`data/datastore/DefaultProviders.kt`；`assets/st-runtime/vendor/npm/@earendil-works/pi-ai@0.85.1/providers/`（30+ 厂商模型表） |
| 桌面端 / TUI / 高亮 / 搜索 / 文档 / 语音独立模块 | `locale-tui/`、`highlight/`、`search/`、`document/`、`speech/`、`ai/`、`common/`、`material3/` |
| 记忆系统（自动提取 + 近期对话引用） | `Assistant.kt:34-36,85-86` |
| 自动压缩对话历史 | `Assistant.kt:84`（`enableAutoCompact`） |
| 前台服务保活生成 | `service/GenerationForegroundService.kt` |
| EJS 模板渲染 | `data/ai/transformers/EjsInputTransformer.kt`、`TemplateTransformer.kt`；`assets/st-runtime/shims/ejs-render.js` |
| JS 侧 `chatCompletion` 兼容端点（扩展可反向调用宿主模型） | `data/st/runtime/TavernChatCompletionsApi.kt`；`TavernRuntimeManager.kt:1039-1175` |

---

# README 与代码不符之处

> 以下均为**回到代码后**发现的偏差。

| # | README/DIVERGENCE 自述 | 代码事实 | 证据 |
|---|---|---|---|
| 1 | DIVERGENCE §5 / README「知识库整套已删（不要再加回）」 | **知识库（RAG）完整存在且已接入生成链** | `data/knowledge/KnowledgeBaseService.kt`、`data/ai/transformers/KnowledgeBaseTransformer.kt`、`ChatService.kt:1241,1695`、`RouteActivity.kt:602`、`Migration_27_28.kt`（**重建**知识库表） |
| 2 | README「21 个内置命令」 | 实际 **38 条**（v240 W4 新增 `/run` `/abort` `/delay` `/switch` `/expression-fallback`；此前 33 条） | `StSlashExecutor.kt`、`StClosure.kt`、`SlashCommands.kt` |
| 3 | README「世界书…官方序列化兼容…与酒馆导入导出互通」 | **导出侧不成立**：Lorebook 导出只产本应用格式 | `ExportSerializer.kt:281-286` |
| 4 | README 未提，代码实为 ST 1.18 宏引擎移植（含 flag/scoped/变量简写/ARC4 pick） | 代码质量与覆盖面远超「自研宏引擎 2.0」的自述 | `MacroEngine.kt:64,182-195,712-760`；`MacroDefinitions.kt:20-21` 自注「移植自 SillyTavern 1.18.0」 |
| 5 | README「角色卡 22 字段无损」 | 字段数属实（实际顶层 25 个），但 **V1 卡（无 `spec`）无法导入** —— 官方可以（`characters.js:504-508`）。PNG iTXt 一条**不成立**：官方也只读 `tEXt` | `AssistantImporter.kt:196-197`；`ImageUtils.kt:319-350` |
| 6 | README「深度兼容…字段无损导入导出」 | `group_only_greetings` 存/改/导出齐全但**群聊开局不生效** | `GroupChatPage.kt:301` 只用 `alternateGreetings` |
| 7 | README 未提 | 正则 `WORLD_INFO` placement 在 UI 可勾选但**无引擎调用点** | `RegexScriptsPage.kt:396-397` vs 全仓无调用 |
| 8 | `MacroDefinitions.kt:23-25` 注释「instruct-macros.js 全系列…暂缓（待后续批次实现）」 | instruct 宏**已实现**（v234 S1），注释过期；但 `{{defaultSystemPrompt}}` 确实没实现 | `:625-661` vs `:24` |
| 9 | `Assistant.kt:480` 注释「triggers…本App暂不执行」 | **实际执行** | `PromptInjectionTransformer.kt:403-404` |
| 10 | `GenerationHandler.kt:156` 注释「contextTemplate 无 UI 入口」 | **有 UI 入口** | `AssistantFormattingPage.kt:130,143-153` |
| 11 | `StSettingsImport.kt:353-355` 自述「扩展设置重启会丢」 | 属**代码自认缺口**，非 README 偏差，但需向用户交代 | 同左 |
| 12 | README 未提 | ST 内置扩展中 **expressions 已原生重写（v240 W3）**；vectors/caption/summarize 仍未移植；`assets/st-runtime/st-compat/extensions/` 只有 `regex/` | `data/st/expressions/`、`ui/pages/chat/CharacterExpression.kt` |
| 13 | README 未提 | 斜杠命令语言层 **v240 W4 已补闭包 / `/run` / `/abort` / `/delay` / `/switch`**；仍缺 QR 集合与 `/let` `/var` | `StClosure.kt`、`StSlashExecutor.kt` |
| 14 | README「上游全部功能原样保留」 | 上游 `data/ai/tools/local/`、`ui/pages/extensions/skills/` 已移动；知识库实为**保留**（与 §5 矛盾） | DIVERGENCE §4 vs 实际 |

---

# 真正没做的功能 Top 清单（按对酒馆用户的实际影响排序）

| 排名 | 缺口 | 影响 | 证据 |
|---|---|---|---|
| 1 | **STscript 语言层仍缺 QR 集合与 `/let` `/var`**（v240 W4 已补闭包 `{: :}` / `/run` / `/abort` / `/delay` / `/switch` / `{{arg::key}}`；官方 1.18 本就没有 `/if` `/while` `/times` `/break`） | 基础子程序 / 闭包脚本已可跑；复杂 QR 集合脚本仍退化 | `StClosure.kt`、`StSlashExecutor.kt`；官方 `slash-commands.js` |
| 2 | **ST 内置扩展仅 expressions 已原生重写（v240 W3）**：vectors（向量化）、caption、summarize 仍零移植 | 依赖 vectors/caption/summarize 的卡仍无效；表情立绘卡已可用（llm 分类） | `data/st/expressions/`；`assets/st-runtime/st-compat/extensions/` 仅 `regex/` |
| 3 | **Quick Replies 无 QR 集合、无 `/qr`、无 QR 变量作用域、忽略 `contextMenu`** | 酒馆用户导入的 QR 只有扁平 label/value 能用，复杂 QR 全部退化 | `Assistant.kt:89-96`；`StArchiveImporter.kt:66-86` |
| 4 | **世界书 `vectorized`（向量化激活）未实现** | 大世界书在酒馆里靠向量召回，这里只能靠关键词，长书召回率显著下降 | `TavernScripts.kt:625`（硬编码 false） |
| 5 | ~~世界书 `@@` 装饰器缺 2 个~~ **v240 W2 已补齐**（`@@activate` / `@@dont_activate`，含 `@@@` 转义） | 已对齐官方；该项从缺口清单移除 | `data/model/WorldInfoDecorators.kt`；`WorldInfoDecoratorTest` / `WorldInfoDecoratorLosslessTest` |
| 6 | **提示词预设体系不完整**：无 ST 预设导出、无 Text-Completion/Kobold/NovelAI、无 instruct 预设导入、无 squash system messages | 从酒馆迁移的老用户**无法把自己的预设带回酒馆**，TC 系预设完全不可用 | `ExportSerializer.kt:83-88,159-160`；squash 0 命中 |
| 7 | **chat 无 ST jsonl 导入导出** | 无法与酒馆互迁聊天记录 | `ui/pages/chat/Export.kt:137-143` |
| 8 | **正则 `WORLD_INFO` placement 勾了不生效** | 想用正则清洗世界书注入内容的用户静默失效（无报错） | `RegexScriptsPage.kt:396` vs 无调用点 |
| 9 | **`group_only_greetings` 群聊不生效** | 群聊专用开场白永远用不上 | `GroupChatPage.kt:301` |
| 10 | **V1 角色卡无法导入**（`zTXt` 不 inflate 同官方，不算差距） | 无 `spec` 字段的老卡/扁平卡直接导入失败 | `AssistantImporter.kt:196-197`；官方对照 `characters.js:504-508` |
| 11 | **stop strings 不可配** | 无法像酒馆那样用停止串约束输出 | 0 命中；`InstructTemplate.kt:20` |
| 12 | **扩展设置 / 启停状态重启丢失**（代码自认） | 每次重启要重配第三方扩展开关 | `StSettingsImport.kt:353-355` |
| 13 | **核心宏缺 3 个**：`{{authorsNote}}` / `{{charAuthorsNote}}` / `{{defaultAuthorsNote}}`（官方 73 个核心宏，rikkaST 覆盖 70） | 用这三个宏的预设会留下未展开的 `{{...}}` | 官方 `authors-note.js:606-614`；`MacroDefinitions.kt` 无注册 |
| 14 | **`automation_id` / `display_index` / `display_position` 仅数据兼容** | 依赖自动化 ID 的世界书不触发；条目展示顺序不遵循酒馆设置 | `Assistant.kt:477-479` |
| 15 | **无 tokenizer 选择，Kotlin 侧 token 计数为启发式** | 世界书预算 / 上下文裁剪与酒馆实际 token 数有偏差 | `PromptInjectionTransformer.kt:675-688` |
| 16 | **无 SD WebUI / ComfyUI / DALL-E 专用生图适配；无 Edge-TTS / GPT-SoVITS / XTTS** | （用户已排除此项，仅登记） | `ImgGenVM.kt`；`TTSProviderConfigure.kt` |
| 17 | **无 `/api` `/model` `/preset` `/world` `/inject` `/regex` 等运维型命令** | 无法在聊天框里切模型/切预设/查世界书 | `StSlashExecutor.kt` 无分支 |
| 18 | **群聊无 scenario override、无群专属 persona** | 群聊场景设定只能写死在成员卡里 | 无实现证据 |

---

## 附：审计范围与方法

- 覆盖文件：`app/src/main/java/me/rerere/rikkahub/**`（重点 `data/st/**`、`data/ai/transformers/**`、`data/export/**`、`data/model/**`、`ui/pages/{extensions,chat,assistant,setting}/**`）、`app/src/main/assets/st-runtime/**`（含 `mvu/bundle.js`、`st-compat/**`、`shims/**`）、`README.md`、`DIVERGENCE.md`、`docs/**`。
- 官方口径来源：SillyTavern 官方文档 Macros 页（本次联网核对，用于宏清单逐条比对）；其余域按官方功能域名称比对。
- **未做**：未编译、未运行、未修改任何文件（仓库全程只读）。所有「未实现」结论均基于全仓 grep + 关键文件通读；未对 JS 运行时（156KB `runtime.js` + 560KB `mvu/bundle.js`）做逐行审计，因此 §13/§14 的「未实现」结论存在**低估风险**（可能有未被 grep 关键词命中的隐式实现）。


---

# 17. 官方 1.18.0 基准数字（源码实测）

> 基准来源：**SillyTavern 官方 1.18.0 完整源码包**（含 `public/scripts/extensions/` 全部内置扩展），
> 已解压至 `D:\rikkaST-refs\Z-st118-full\SillyTavern-1.18.0`。
> 下表数字全部由脚本从官方源码直接统计，非估算。

| 基准项 | 官方 1.18.0 | rikkaST | 差距 |
|---|---|---|---|
| 服务端路由文件（`src/endpoints/*.js`） | **44** | **2 个端点**（`/api/backends/chat-completions/{status,generate}`） | ❌ 最大缺口 |
| 核心斜杠命令（`addCommandObject` 调用数） | **101** | **38**（v240 W4 +`/run` `/abort` `/delay` `/switch` `/expression-fallback`） | 🟡 约 38%；闭包/子程序层已补齐，QR 集合仍缺 |
| 事件常量（`public/scripts/events.js`） | **104** | 82 登记 / 约 30 有发射点 | 🟡 缺 22 个常量，发射率约 29% |
| 核心宏 | **73**（注册表 70 + `authors-note.js` 3） | 覆盖 **73/73**（v240 W1 补齐 authors-note 3 个），另有 29 自有/别名 | ✅ 全齐 |
| 内置扩展（`public/scripts/extensions/`） | **14** | **2**（regex shim + expressions 原生重写，v240 W3） | ❌ 缺 12（原生重写 ≠ JS 扩展可在插件运行时跑） |
| 世界书 `@@` 装饰器 | **2**（`@@activate` / `@@dont_activate`） | 2（v240 W2 逐个对齐 parseDecorators 语义） | ✅ 全齐 |
| JSR `injectPrompts` / `uninjectPrompts`（酒馆助手 API，非 ST 核心） | 2 个签名（`@types/function/inject.d.ts`） | 2 个（v241；`filter` 不支持） | ✅ 对齐（差异见 DIVERGENCE §H.4） |
| 角色卡 `character_book.entries[].enabled` 启停字段 | 官方写 enabled / 读 enabled | v241 起读写都按 enabled（兼容 disable）+ 导出数组形态 | ✅ 对齐（v240 之前会误把禁用条目全放出来） |
| 表情立绘（expressions 扩展） | 28 标签 + local/extras/llm/webllm/none 五种分类 + 立绘目录 | 原生重写：28 标签 + llm/none + 立绘切换 + `/expression-fallback`（v240 W3） | 🟡 仅 LLM 分类（无 local BERT / extras / webllm / visual novel） |
| Kotlin 侧真 tokenizer | JS 侧真 BPE（cl100k/o200k 词表随包） | Kotlin 原生 tiktoken BPE + UI 选择（v240 W5） | ✅ 与官方数值逐样例一致 |
| PNG 读卡 chunk | 仅 `tEXt` | `tEXt` + `ccv3` 优先 | ✅ 不落后 |
| 第三方扩展落点 | `public/scripts/extensions/third-party/`（源码里是空目录，只有 `.gitkeep`） | `filesDir/tavern-extensions/third-party/` | ✅ 契约对齐 |

**事件常量缺的 22 个**（官方有、rikkaST 常量表无）：
`APP_INITIALIZED`、`CHARACTER_GROUP_OVERLAY_STATE_CHANGE_BEFORE/AFTER`、`CHAT_LOADED`、`CHAT_RENAMED`、`GROUP_CHAT_CREATED`、`GROUP_CHAT_DELETED`、`GROUP_MEMBER_DRAFTED`、`GROUP_UPDATED`、`GROUP_WRAPPER_STARTED`、`GROUP_WRAPPER_FINISHED`、`ITEMIZED_PROMPTS_LOADED/SAVED/DELETED`、`PERSONA_CHANGED/CREATED/DELETED/RENAMED/UPDATED`、`TTS_JOB_STARTED`、`TTS_JOB_COMPLETE`、`TTS_AUDIO_READY`

**rikkaST 常量表里多出的 7 个**（属 JSR / iframe 事件域，非 ST `events.js`）：
`GENERATION_REQUESTED`、`MESSAGE_IFRAME_RENDER_STARTED/ENDED`、`STREAM_TOKEN_RECEIVED_FULLY/INCREMENTALLY`、`REASONING_TOKEN_RECEIVED_FULLY/INCREMENTALLY`

> 说明：rikkaST 的常量表对齐的是 **JS-Slash-Runner 的 `event.ts`**，不是 ST 自己的 `events.js`，所以两侧并非严格子集关系。

---

# 18. 14 个官方内置扩展：能否在 rikkaST 跑（逐个查 `/api` 依赖）

判定方法：扫描每个扩展目录下所有 `.js` 里出现的 `/api/**` 字面量。**只要依赖非 chat-completions 的端点，在 rikkaST 里就会 404。**

| 官方内置扩展 | 依赖的服务端端点 | 能否在 rikkaST 跑 |
|---|---|---|
| `attachments` | 无（纯前端） | 🟡 结构上可以，未实测 |
| `connection-manager` | 无（纯前端） | 🟡 结构上可以，未实测 |
| `token-counter` | 无（纯前端） | 🟡 结构上可以，未实测 |
| `regex` | 无（纯前端） | ✅ rikkaST 已提供 `st-compat/extensions/regex/engine.js` shim |
| `assets` | `/api/assets/get|download|delete` | ❌ 404 |
| `caption` | `/api/backends/chat-completions/multimodal-models/*` | ❌ 404 |
| `expressions`（表情立绘） | `/api/classify`、`/api/classify/labels`、`/api/sprites/get|delete` | ❌ 404 |
| `gallery` | `/api/images/list`、`/api/images/folders` | ❌ 404 |
| `memory`（摘要记忆） | `/api/summarize` | ❌ 404（rikkaST 有自研知识库/EJS 路径，但不是同一套） |
| `quick-reply` | `/api/quick-replies/*`、`/api/settings/get` | ❌ 404（rikkaST 有自研扁平静态快捷回复） |
| `stable-diffusion` | `/api/image`、`/api/horde/*`、`/api/google/generate-image` | ❌ 404（rikkaST 有原生生图页，但不是同一套） |
| `translate` | `/api/translate/{google,bing,deepl,deeplx,libre,lingva}` | ❌ 404（rikkaST 有自研翻译） |
| `tts` | `/api/azure/*` 等 | ❌ 404（rikkaST 有 11 个原生 TTS provider） |
| `vectors`（向量检索） | `/api/openai/*/models/embedding` 等 | ❌ 404（rikkaST 有自研 FTS5+向量混合检索） |

**结论**：官方内置扩展里只有 **regex 是真正被 shim 过的**；3 个纯前端扩展结构上可行但没验；其余 10 个全部卡在未实现的服务端端点上。

> 注：`memory` / `translate` / `stable-diffusion` / `tts` / `vectors` 这几项 rikkaST **有功能等价的原生实现**，但**不是同一套代码路径** —— 酒馆里对应扩展的配置导入后不生效。