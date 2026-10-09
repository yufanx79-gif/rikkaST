# rikkaST — 把酒馆装进手机

[**简体中文**](README.md) | [**English**](README_EN.md)

> 基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 的深度定制分支：一个原生 Android LLM 聊天客户端，
> 在此之上**内建了一套 SillyTavern 兼容层** —— 角色卡、世界书、宏、斜杠命令、人设、群聊，
> 以及一个能真正跑起酒馆第三方插件的运行时。
>
> 逐文件差异与上游合并工作流见 [DIVERGENCE.md](DIVERGENCE.md)；
> 酒馆生态兼容覆盖度的完整报告见 [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md)。

---

## 目录

- [为什么会有这个项目](#为什么会有这个项目)
- [三分钟上手](#三分钟上手)
- [功能总览（按体验组织）](#功能总览按体验组织)
- [酒馆兼容覆盖度：诚实版](#酒馆兼容覆盖度诚实版)
- [与上游 RikkaHub 的关系](#与上游-rikkahub-的关系)
- [构建与安装](#构建与安装)
- [已知限制](#已知限制)
- [致谢](#致谢)
- [许可证](#许可证)

---

## 为什么会有这个项目

酒馆（SillyTavern）是网页应用，它的整个生态 —— 角色卡、世界书、预设、正则、插件 —— 都围绕浏览器构建。
手机上有两种用法，体验都不好：要么用浏览器跑酒馆本体（耗电、滚动卡、后台被杀），要么用客户端但只能吃到「角色卡导入」这一小块。

**rikkaST 想做的是第三件事：把酒馆的「语义」原生化，同时把酒馆的「插件」留在原处。**

- **语义原生化**：角色卡、世界书、宏、预设这些不依赖浏览器的部分，用 Kotlin 重写成原生实现 —— 快、省电、字段无损、可可视化编辑。
- **插件留在原处**：酒馆的 JS 插件生态太大，不可能逐个重写。所以内置一个 WebView 运行时，仿真出酒馆的前端 API（`SillyTavern.getContext()`、`eventSource`、`TavernHelper`、`#extensions_settings` DOM……），让**真正的酒馆插件原样跑起来**。

一句话：**上游是基础聊天客户端，这是一个给 AI 角色扮演准备的完整工具箱。**

---

## 三分钟上手

1. **装 APK**（`dist/` 目录，或自己构建 —— 见 [构建与安装](#构建与安装)）。
2. **加一个提供商**：设置 → 提供商 → 任意 OpenAI / Anthropic / Google 兼容端点（也支持自定义 Base URL）。
3. **导入一张角色卡**：助手页 → 导入 → 选 PNG 卡或 JSON 卡。字段全部保留，导入后可以直接进详情页改。
4. **开始聊**。想更进一步：导入一本世界书、装一个酒馆插件、开一条群聊。

不需要账号，不需要联网注册。

---

## 功能总览（按体验组织）

> 规模参照（逐文件实测行数）：角色卡导入 `AssistantImporter.kt` 902 行 · 世界书引擎 `PromptInjectionTransformer.kt` 994 行 · 世界书编辑页 `PromptPage.kt` 2717 行 · 角色卡编辑页 `TavernCharacterCard.kt` 1942 行 · 导出器 `CardExporter.kt` 316 行 · 宏引擎 `MacroEngine.kt` 866 行 · 斜杠命令 `SlashCommands.kt` 630 行 · 群聊页 `GroupChatPage.kt` 1558 行 · 插件运行时 `TavernRuntimeManager.kt` 1178 行。

### 🎴 角色卡：导入之后你还能改

上游只解析 6 个字段（name / first_mes / system_prompt / description / personality / scenario）并拼成一段系统提示，其余全丢，而且没有导出、没有编辑页。

这里：

- **25 个顶层字段全部结构化保留**：示例对话、备选开场白、作者备注（含多语言）、历史后指令（PHI）、角色版本、标签、昵称、素材、仅群聊开场白、创建 / 修改时间、内嵌世界书、内嵌正则、深度提示（含深度与角色）。
- **`extensionsRaw` 无损往返**：扩展字段的原始 JSON 原样存、原样导出，导入再导出不丢东西。
- **官方 Chat Completion 注入结构**：主提示、角色卡字段独立消息、`<START>` 分块解析成真正的 user/assistant 示例消息、PHI 放历史末尾、深度提示按配置的深度与角色注入。
- **PNG / JSON 导出**（`CardExporter.kt`），字段名对齐官方规范。
- **可视化编辑页**：25 个字段 + 内嵌世界书管理 + 导出按钮，一张卡一个页面搞定。
- **真实卡验证**：测试资源里有一张真实 V3 卡（47 条世界书 + 11 条正则 + 3 个酒馆助手脚本）作为回归样本。

### 📚 世界书：让角色真的「记得住」

上游是 5 字段模型 + 关键词包含匹配。这里逐条对齐官方 `world-info.js` 语义，**条目字段 44 个**：

| 能力 | 说明 |
|---|---|
| 主 / 副关键词四档逻辑 | `and_any` / `and_all` / `not_any` / `not_all` |
| 匹配方式 | 整词、正则、大小写敏感 |
| 条目级扫描深度 | 覆盖全局默认 |
| 常驻激活 | 不靠关键词，永远在上下文里 |
| 跨书分组选胜 | 同组只激活一条：粘性优先 → 关键词评分 → `group_override` → 加权随机 |
| 触发概率 | 0–100，可开关 |
| 粘性 / 冷却 | 激活后保留 N 轮 / 触发后冷却 N 轮 |
| 延迟激活 | 延迟到第 N 轮才可激活 |
| 递归扫描 | 已激活内容进递归缓冲继续扫；`exclude` / `prevent` 控制；`delay_until_recursion` 支持数字层级 |
| token 预算 | 全局预算百分比 × 上下文长度，溢出即停（可弹提醒），条目可豁免 |
| 匹配角色卡字段 | 人设 / 描述 / 性格 / 深度提示 / 场景 / 作者备注 ×6 |
| 展示排序 / 生成过滤 | `display_index` / `display_position` / `triggers` |

引擎是一个**官方 `checkWorldInfo` 状态机**：INITIAL → RECURSION / MIN_ACTIVATIONS / 层级开放循环，含预算、溢出、粘性、冷却的完整生命周期；序列化用官方枚举名，和酒馆互通。

编辑页有全局设置面板（扫描深度、预算、最少激活数、最大递归轮数、插入策略、溢出提醒、组评分）+ 条目编辑器 + 拖拽排序 + 外置 / 内嵌世界书双向同步。

### 🧩 提示词可编程：宏引擎 2.0

上游是 6 个占位符的字符串替换。这里提示词变成了程序（`MacroEngine.kt`）：

- **变量**：`/setvar` `/getvar` `/incvar` … 管理对话变量，宏里用 `{{getvar::key}}` 或 `.key` 简写 —— 一张卡可以随剧情状态切换说法。
- **条件**：`{{if}} / {{else}} / !`、比较运算符、`&&` / `||`，支持嵌套。
- **随机与时间**：`{{pick::A|B|C}}`（同一轮内稳定，可 `/reroll-pick` 重掷）、`{{roll::1d20}}`、`{{random}}`、`{{time}}`、`{{trim}}`、`{{comment}}`。
- **对话感知**：`{{lastUserMessage}}`、`{{lastCharMessage}}`、`{{idleDuration}}`、`{{charFirstMessage::N}}`、`{{original}}`。
- **EJS 模板渲染**：发送前对提示词 / 世界书 / 角色卡里的 `<% %>` 模板求值（ST-Prompt-Template 语义），渲染上下文里可用 `getvar` / `setvar` 等变量 API。
- 未知宏原样保留，不破坏模板。

### ⌨️ 斜杠命令：输入框就是控制台

输入框直接输入即执行，`/help` 列出全部命令与说明；无参数命令点击直接跑，带参数的自动填入输入框补完。**33 个内置命令**：

- **角色扮演**：`/impersonate`（AI 以你的视角拟话）、`/continue`、`/sendas`、`/sys`、`/send`
- **操控生成**：`/trigger`（不新增消息直接触发回复）、`/sysgen`（让 AI 写旁白）、`/gen`
- **角色卡**：`/char-update`、`/char-duplicate`、`/rename-char`
- **变量与随机**：`/listvar` `/setvar` `/getvar` `/addvar` `/incvar` `/decvar` `/flushvar` `/reroll-pick`
- **消息与分支**：`/hide` `/unhide` `/swipe`、`/checkpoint-create` `/checkpoint-go` `/checkpoint-exit` `/checkpoint-parent` `/checkpoint-get` `/checkpoint-list`、`/branch-create`
- **其它**：`/persona`（官方 `/persona-set` 别名）、`/js`（在内置运行时里执行 JS）、`/tavern`

另有一个 **STscript 执行器**（`StSlashParser` + `StSlashExecutor`，对齐官方 slash-commands 语法核心子集），支撑管道 `|`、命名参数、`/pass` `/return` `/echo` 等，供插件里的 `triggerSlash()` 与快速回复自动执行共用同一条通道。

### 🎭 人设 · 导演备注 · 群聊

三个上游完全没有的系统，语义都对齐官方：

- **人设（Persona）**：官方五档注入位置（IN_PROMPT / TOP / BOTTOM / AT_DEPTH / NONE）、按角色绑定、独立 SYSTEM 消息注入、禁用即不注入。
- **导演备注（Author's Note）**：官方间隔语义（1 = 每次，N = 每 N 条用户消息）、注入深度、注入角色、总开关。
- **群聊**：多角色共同对话，每个成员独立提示词 / 人设 / 模型；四种选人策略（自然 / 列表 / 带权重随机 / 手动）；自动接话（轮数、延迟可配，用户发言即打断）；实时发言人状态。

### 🔌 酒馆扩展生态：装真正的酒馆插件

这是本项目最有意思的一块。不是「支持某几个插件」，而是**搭了一个能跑酒馆插件的运行时**：

- **安装**：扩展中心 → 第三方扩展 → 给一个 zip，或一个 http(s) 直链（GitHub / GitLab 仓库、`manifest.json` 直链都认，自动试 `main` / `master` 分支，源码型扩展会递归抓取相对 import）。
- **资源端点**：扩展文件按真实路径伺服为 `/scripts/extensions/third-party/<folder>/<path>`，**在 WebView 里像真酒馆一样被加载**，而不是在 runtime 里解释代码。
- **ST 核心模块仿真**：36 个 shim 覆盖扩展会 `import` 的 ST 内部模块（`script.js`、`extensions.js`、`world-info.js`、`preset-manager.js`、`PromptManager.js`、`power-user.js`、`slash-commands/*`、`tokenizers.js`、`openai.js`、`group-chats.js`、`reasoning.js`…）。
- **前端 API 仿真**：`SillyTavern.getContext()`、`extension_settings`、`extension_prompts`、`eventSource` + `event_types`，以及 **`TavernHelper` 55 个 API 键**（事件 / 变量 / 消息 / 世界书 / 按钮 / 工具）。
- **DOM 仿真**：`#extensions_settings`、`#extensions_settings2`、`#extensionsMenu`、`#send_form`、`#tavern_helper`、隐藏的 `#chat` 楼层桩 —— 扩展的设置界面能真的挂上去、楼层节点能真的读写。
- **事件总线**：82 个官方事件常量全表登记，约 30 个有真实发射点（生成管线的 `GENERATE_*`、`CHAT_COMPLETION_PROMPT_READY`、`GENERATION_AFTER_COMMANDS`、消息生命周期全套、预设族、世界书族、流式 token 等）。**注意：登记 ≠ 发射**，其余约 50 个依赖它们做初始化的插件会静默不工作。
- **宿主端点**：实现了酒馆的 `/api/backends/chat-completions/{status,generate}` —— 这条让酒馆助手（JS-Slash-Runner）的 `generate()` / `generateRaw()` 能真的调起 App 的生成管线（SSE 与 `stream:false` 双路径）。
- **离线友好**：卡脚本里裸 `import` 的 jsDelivr CDN 会被本地化到内置 `vendor/`，命中就离线加载，没命中才回退真实网络。
- **能力闸门**：`/version` 返回刻意选定的 `pkgVersion`，用来打开插件的特性分支。

**✅ 已实测兼容的插件**：

| 插件 | 兼容范围 |
|---|---|
| **酒馆助手（JS-Slash-Runner）** | 脚本 iframe、脚本按钮、事件、变量、世界书、`triggerSlash`、`generate()` / `generateRaw()` 全链路 |
| **提示词模板（ST-Prompt-Template）** | EJS 模板渲染、`getCharacterDefine()`、PromptManager、变量 scope 语义 |

另有内置 **MVU 变量框架**（MagVarUpdate）支持。
其余插件的可运行性取决于它是否需要酒馆的服务端 API —— 完整分析与缺口清单见 **[docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md)**。

### 🧠 MVU 变量框架

内置 MVU（MagVarUpdate，MIT）运行时 bundle，卡内脚本可直接用；宿主侧解析 AI 输出里的变量更新块并落到对话变量，配套面板 UI 挂在本地运行时里。`#tavern_helper` 容器里会按 MVU 的 `unique_check` 约定注入脚本身份节点，避免多实现冲突。

### ⚙️ 正则脚本 · 快捷回复 · 预设导入

- **正则脚本**：全局 / 作用域 / 预设 / 角色卡四个来源；`find_regex`、`replace_string`、放置位（用户输入 / AI 输出 / 斜杠命令 / 世界书 / 推理）、深度、宏替换。内嵌 `extensions/regex/engine.js` 兼容层。
- **快捷回复**：数据模型对齐酒馆 QR，可设「点击直接执行」—— 斜杠开头走 STscript，纯文本直接发送。
- **预设与世界书导入**：直接吃酒馆预设文件与备份存档（`StSettingsImport` / `StArchiveImporter`），并会把预设里内嵌的「酒馆助手」脚本一并提取出来。
- **卡内脚本三源合并**：角色卡脚本（`extensions.tavern_helper.scripts`）+ 全局脚本 + 预设脚本，各自独立启停。

### 🛠 技能与工具

**技能**：上游只有「模型主动调用 `use_skill` 才加载」。这里加了关键词**自动触发**、公共技能目录 `/Rikkahub/skills`（文件管理器直接丢进去就认）、GitHub 一键安装、整仓库批量下载、更新检测（记录仓库源与整目录哈希）、安装源识别与技能注册表。

**工具**：上游有时间 / 剪贴板 / 日历 / JavaScript / 屏幕时间 / TTS / 提问 / 记忆 / 搜索 / 技能 / 工作区。这里新增文件操作、Shell、任务、计算器、数据库查询、Python 引擎、网页抓取，并提供 **Python / JS 双桥接**（AI 可读写对话、助手设置、群聊，运行 Python / JS 引擎）与系统提示组装器。本地工具共 17 个可选项。

**工作区**：带终端的沙箱目录，Agent 可以在里面跑命令、编辑文件。

### 🧠 知识库：把资料喂给角色（RAG）

不只是聊天 —— 你可以把一个文件夹、几份文档、甚至过去的聊天记录变成角色的「长期记忆」：

- **导入方式**：文件 / 整个文件夹 / 聊天记录 / 纯文本便签；按「知识源」管理，可绑定到指定助手，也可全局共享。PDF、EPUB、DOCX、PPTX 由内置 `document` 模块解析。
- **双级分块**：父块 1024 token / 子块 256 token（带重叠）。只给子块做向量，命中后回溯父块 —— 返回的上下文更完整，而不是孤零零一句话。
- **混合检索**：SQLite **FTS5** 全文检索与**向量检索**并行执行，再用 **RRF（倒数排名融合）** 合并排序；查询侧还会做多路改写提高召回。
- **注入预算**：结果按 token 预算裁剪后注入生成链，不会把上下文挤爆。
- **向量化**：用你配置的 embedding 模型，支持自动向量化、进度显示与向量 LRU 缓存；不配 embedding 也能纯靠 FTS5 工作。

### 🖼 图像生成 · 🔊 语音朗读 · 🎙 语音输入

- **文生图**：独立页面 + 原生 Provider（OpenAI `/images/generations`、Claude），也可作为内置工具让模型自己调用。
- **TTS**：11 个 Provider —— ElevenLabs、FishAudio、Gemini、Groq、MiMo、MiniMax、OpenAI、Qwen、Step、System、xAI。支持朗读、自动播放、分块合成。
- **ASR**：5 个 Provider —— DashScope、MiMo、OpenAI Realtime、Step、Volcengine。

> ⚠️ 这三项是**原生功能**，不是酒馆扩展。酒馆的 Image Generation / TTS 插件因为需要未实现的 `/api/sd`、`/api/tts` 端点，装进来不会工作。详见兼容报告 §6。

### ⚡ 稳定性

- **后台生成保活**：前台服务 + 异步启动 + 600ms 防抖 + 失败兜底，切后台不打断生成。
- **运行时日志**：插件运行时的日志持久化到 `Android/data/<pkg>/files/tavern-runtime.log`，排查插件问题第一现场。
- **上游冗余已清理**：GitHub 工具、sleep 工具、日志调试页已移除（见 [DIVERGENCE.md](DIVERGENCE.md) §5）。

---

## 酒馆兼容覆盖度：诚实版

| 层 | 覆盖度 | 说明 |
|---|---|---|
| **数据层**（角色卡 / 世界书 / 预设 / 正则 / 变量 / 聊天记录） | ✅ 基本完整 | 字段无损往返，导入再导出不丢东西 |
| **引擎层**（世界书扫描 / 宏 / STscript / 正则 / MVU 解析 / EJS） | ✅ 基本完整 | 对齐官方语义，有单测覆盖 |
| **插件运行时层** | 🟡 能跑，有边界 | 前端 API + DOM + 事件 + 2 个宿主端点；服务端 API 是最大缺口 |

**主要缺口**（逐条证据见 [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md) §5）：

> 另外两份细化文档：
> [docs/ST-COMPATIBILITY.md](docs/ST-COMPATIBILITY.md) —— **插件接入层**怎么做、边界在哪、已适配哪些插件；
> [docs/ST-FEATURE-MATRIX.md](docs/ST-FEATURE-MATRIX.md) —— **官方功能逐项对照**：角色卡 / 世界书 / 预设 / 宏 / STscript / 正则 / 变量 / 多媒体，每一条都有 `路径:行号`。

1. 酒馆服务端 API 只实现了 2 个端点（`/api/backends/chat-completions/{status,generate}`），其余一律 404。
2. 酒馆官方内置扩展零实现（只有 `regex/engine.js` 一个 shim）：vectors / expressions / caption / summarize / tts / sd / gallery / assets 全缺。
3. 82 个事件常量里约 30 个有真实发射点，其余约 50 个只登记不发射。
4. 3 个 iframe 事件（`GENERATION_REQUESTED` 与两个推理 token 事件）已登记未接线。
5. 没有扩展商店 / 在线索引；`manifest.requires` 只解析不安装。

---

## 与上游 RikkaHub 的关系

- **直接上游**：`github.com/rikkahub/rikkahub`（RikkaHub），AGPL-3.0。
- **保留**：Material You 主题、多提供商、流式输出、对话分支与重新生成、消息编辑 / 删除 / 翻译、全文搜索（jieba）、收藏、图像生成、TTS / ASR、MCP、工作区沙箱、备份（S3 / WebDAV / 提醒）、Web 服务端、聊天导出 —— 全部照常可用。
- **移除**：GitHub 工具、sleep 工具、日志调试页（上游没有对应功能，见 [DIVERGENCE.md](DIVERGENCE.md) §5）。
- **差异地图**：哪些上游文件被改、哪些是本分支独有、合并时的冲突怎么办 —— 全部逐条记录在 [DIVERGENCE.md](DIVERGENCE.md)。上游更新可随时 `git fetch upstream && git merge upstream/master` 合入。

---

## 构建与安装

完整说明见 [BUILDING.md](BUILDING.md)。最简路径：

```bash
# 环境：JDK 17+、Android SDK、NDK（Chaquopy 需要）
# Release 签名与 Chaquopy 需要 local.properties（不进 git）
./gradlew :app:assembleRelease

# 只跑单元测试
./gradlew :app:testDebugUnitTest
```

产物在 `app/build/outputs/apk/`。预编译 APK 见 `dist/`。

> 改了 `app/src/main/assets/st-runtime/*.js` 之后建议先做一次 ES module 语法自检，再出包。

---

## 已知限制

- **插件兼容不是 100%**：见上方覆盖度表与兼容报告。判断标准很明确 —— 插件碰不碰酒馆的服务端 API。
- **真机验收未完全覆盖**：第三方扩展装载链路、部分事件发射、群聊策略、ST 备份导入等场景目前只有单测 + 代码路径确认，缺真机回归记录。
- **签名**：换过密钥，与更早的构建不能直接覆盖安装（同包名下需要先卸载）。
- **平台**：预编译 APK 目前只含 `arm64-v8a`。

---

## 致谢

- [**RikkaHub**](https://github.com/rikkahub/rikkahub)：本项目的上游。
- [**SillyTavern**](https://github.com/SillyTavern/SillyTavern)：兼容目标与事件契约参考。本项目的酒馆兼容层是对其数据格式与扩展契约的**兼容实现**。
- [**Kelivo**](https://github.com/Chevey339/kelivo)（Flutter / AGPL-3.0）：**UI 与交互设计参考**。这个项目非常优秀，界面也很漂亮，本项目的视觉与交互方向大量参照了它；没有逐行复制 Kelivo 的 Dart 代码。
- 其余第三方组件、字体与许可证全文见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

---

## 许可证

本项目以 **GNU Affero General Public License v3.0（AGPL-3.0）** 发布，全文见 [LICENSE](LICENSE)。

选择 AGPL 不是随意的：上游 [RikkaHub](https://github.com/rikkahub/rikkahub) 是 AGPL-3.0，
[SillyTavern](https://github.com/SillyTavern/SillyTavern) 是 AGPL-3.0，
[MuPDF](https://github.com/ArtifexSoftware/mupdf)（PDF 解析依赖）也是 AGPL-3.0 —— **四者同许可，天然兼容**。

> 如果你要分发本项目的二进制，请一并提供 `LICENSE` 与 `THIRD-PARTY-NOTICES.md`。

### 商标

「SillyTavern」是其各自所有者的商标。本项目**兼容**其数据格式与扩展契约，
但**不隶属于、也不受其背书**。与 Artifex Software、Khan Academy、OpenAI、Anthropic、Google 等亦无隶属关系。

---

如果这个分支对你有用，请点个 ⭐ Star 支持一下 ✨

## 隐私与网络

- **无埋点、无崩溃上报**：不包含任何 Analytics / Crashlytics 类 SDK，不上传聊天内容、角色卡与设备信息。
- 会访问的第三方域名、触发条件与敏感权限用途，全部列在 [docs/PRIVACY-NETWORK.md](docs/PRIVACY-NETWORK.md)。
