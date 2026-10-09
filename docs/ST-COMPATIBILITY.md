# rikkaST × SillyTavern 生态兼容报告

> 本文回答一个问题：**这个项目到底实现了什么？酒馆生态里，除了「酒馆助手」和「提示词模板」，其他插件适配到什么程度？**
>
> 全部结论来自 `D:\rikkaST` 工作树的源码逐文件核对（不依赖 README 自述、不依赖构建结果）。每条结论附 `路径:行号`。

**证据强度标注**（下文每条都会带）：

| 标记 | 含义 |
|---|---|
| **A** | 有自动化测试或探针卡断言（最强） |
| **B** | 有真机日志 / 代码注释中的修复记录 |
| **C** | 代码存在且逻辑可读，但无测试、无真机验证 |
| **D** | 只有常量表 / 占位，未接线 |

---

## 0. 结论速览

| 层 | 内容 | 覆盖度 |
|---|---|---|
| **① 数据层** | 角色卡（V1/V2/V3、PNG/JSON）、世界书、预设、正则脚本、变量、聊天记录 | **基本完整**，字段无损往返 |
| **② 引擎层** | 世界书扫描状态机、宏引擎、STscript、正则引擎、MVU 变量解析、EJS 模板 | **基本完整**，对齐官方语义 |
| **③ 插件运行时层** | 在 App 内跑真正的 SillyTavern 前端扩展（JS） | **能跑，但有明确边界**——见 §2、§5 |

**一句话回答你的问题：**

> 我们不是「适配了几个插件」，而是搭了一个**能跑酒馆插件的运行时**（WebView + ST 前端 API 仿真 + 扩展安装器）。
> 目前**明确打通并点名适配过**的是 **JS-Slash-Runner（酒馆助手）**、**ST-Prompt-Template（提示词模板）**、**MVU 变量框架**三条线；
> 其他插件属于「结构上大概率能跑，但**没有实测记录**」。
> 真正的硬边界是：**酒馆的服务端 API（`/api/**`）只实现了 2 个端点**，其余一律 404 —— 这一条决定了哪些插件注定跑不起来。

---

## 1. 三层架构：先把「兼容」拆开看

```
┌─ ③ 插件运行时层 ──────────────────────────────────────────────┐
│  WebView (appassets.androidplatform.net)                      │
│  st-runtime/runtime.js  +  36 个 st-compat 模块  +  10 个 shim │
│  ← 第三方扩展 JS 在这里跑；TavernHelper / 事件 / 变量 / DOM 仿真 │
└───────────────────────────────────────────────────────────────┘
              ↑ 只通过 2 个宿主端点 + JS Bridge 回宿主
┌─ ② 引擎层（Kotlin 原生）──────────────────────────────────────┐
│  世界书扫描 PromptInjectionTransformer.kt (994 行)             │
│  宏引擎 MacroEngine.kt (866 行) / STscript StSlashExecutor     │
│  正则引擎 RegexScriptEngine.kt / MVU UpdateVariableParser      │
└───────────────────────────────────────────────────────────────┘
              ↑
┌─ ① 数据层（Kotlin 原生 + Room）───────────────────────────────┐
│  角色卡 TavernCard.kt（25 个顶层字段，无损 extensionsRaw）      │
│  世界书 44 字段条目 / 预设 / 正则 / 变量 / 群聊 / 人设          │
└───────────────────────────────────────────────────────────────┘
```

关键点：**①② 层是原生 Kotlin 实现的，不依赖 ③ 层**。所以即使插件跑不起来，角色卡、世界书、宏、群聊这些主线功能照常工作。

---

## 2. 插件接入层详解（重点）

### 2.1 第三方扩展的装载链路

| 环节 | 实现 | 证据 |
|---|---|---|
| 安装目录 | `filesDir/tavern-extensions/third-party/<folder>/` | `data/st/extensions/TavernExtensions.kt:40` |
| 安装方式 | ① SAF 选 zip ② http(s) 直链（含 GitHub/GitLab 仓库与 `manifest.json` 直链、自动试 `main`/`master` 分支、递归抓取相对 import） | `TavernExtensions.kt:313-315,579`；UI `ui/components/ai/ThirdPartyExtensionsSection.kt:99-114` |
| manifest 契约 | ST 1.18：`display_name`/`version`/`author`/`homePage`/`js`/`css`/`loading_order`/`requires` | `TavernExtensions.kt:96-130` |
| 资源端点 | `/scripts/extensions/third-party/<folder>/<path>` → 真实文件；另兼容 `/third-party/<folder>/<tpl>.html` 与 `/st-runtime/scripts/...` 两种相对路径 | `runtime/TavernRuntimeManager.kt:347-357,400-410` |
| 路径安全 | 拒 `..`/`.`/分隔符/控制字符/Windows 保留字符 + canonical 前缀双保险（对齐 TauriTavern 规则） | `TavernExtensions.kt:137-140`；`TavernRuntimeManager.kt:398` |
| 启停状态 | 全局 `Settings.tavernThirdPartyDisabled`（对齐 ST：第三方扩展启用是全局的，不绑角色卡） | `ui/pages/extensions/TavernExtensionsPage.kt:33` |
| 热重载 | 变更后 `window.__rikkaReloadThirdPartyExtensions()` | `ThirdPartyExtensionsSection.kt:86-90` |
| 防 zip 炸弹 | 单包 64 MB / 解压总量 256 MB 上限 | `TavernExtensions.kt:43-46` |

**注意**：没有「扩展商店 / 在线索引」。安装靠你给 URL 或选 zip。

### 2.2 ST 核心模块 shim（36 个）

第三方扩展 `import` 的 ST 内部模块，通过 `routeStCompat()` 映射到本地 shim（`TavernRuntimeManager.kt:433-453`）。

`/script.js`、`/lib.js`、`/scripts/events.js`、`/scripts/eventemitter.js` 是特例，其余 `/scripts/<rel>.js` 走 `st-runtime/st-compat/<rel>`。

实际存在的 36 个文件（`app/src/main/assets/st-runtime/st-compat/`）：

```
sse-stream.js      char-data.js     custom-request.js  authors-note.js
user.js            st-context.js    RossAscends-mods.js personas.js
constants.js       PromptManager.js variables.js       i18n.js
extensions.js      slash-commands.js popup.js           f-localStorage.js
group-chats.js     macros.js        power-user.js      utils.js
preset-manager.js  openai.js        script.js          reasoning.js
tokenizers.js      world-info.js    chat-dom.js        lib.js
templates.js       tags.js
extensions/regex/engine.js
slash-commands/SlashCommand.js
slash-commands/SlashCommandParser.js
slash-commands/SlashCommandArgument.js
slash-commands/SlashCommandEnumValue.js
slash-commands/SlashCommandCommonEnumsProvider.js
```

**没命中的路径会 404，并写一条 error 日志**（`TavernRuntimeManager.kt:449-451`）——这是判断「某插件为什么不工作」的第一现场。

对照：ST 官方自己的内置扩展（`/scripts/extensions/vectors/`、`expressions/`、`caption/`、`summarize/`、`tts/`…）**一个都没有实现**，只有 `extensions/regex/engine.js` 一个。

### 2.3 前端 API 兼容面

| API | 状态 | 证据 |
|---|---|---|
| `window.SillyTavern.getContext()` | 由运行时 `stCtx` 提供，注释称对齐「150+ 键契约」 | `st-compat/st-context.js:5` |
| `extension_settings` | 预置 `extension_settings.tavern_helper` 完整默认形状 | `st-compat/extensions.js:25-56` |
| `extension_prompts` | `setExtensionPrompt` / `getExtensionPromptByName` / `getExtensionPromptRoleByName` 本地记录 | `st-compat/script.js:116-128` |
| `eventSource` + `event_types` | 与 runtime 同实例；含 `on/once/off/emit/emitAndWait` 等全套 | `st-compat/script.js:53-59` |
| `script.js` 导出面 | `chat`/`characters`/`name1`/`name2`/`this_chid`/`chat_metadata`/`main_api`… 用 `export let` + live binding，`rikka-st-refresh` 事件后重新快照 | `st-compat/script.js:6-51` |
| `TavernHelper`（JSR API） | **55 个键**（与 JSR 真源对齐，含 `_bind`） | `st-runtime/runtime.js:981-1045` |
| 裸全局注入 | 每个 iframe 内把 TavernHelper 的键铺成裸全局（对齐 JSR iframe 语义），并做 per-iframe 隔离防跨窗口污染 | `runtime.js:1050-1054,1129-1182` |
| `#tavern_helper` DOM | 每个容器内注入脚本身份 `div[data-script-id]`（MVU 的 `unique_check` 依赖） | `runtime.js:2010-2050` |

`TavernHelper` 55 键的构成：

- **事件**（11）：`tavern_events` `iframe_events` `eventOn` `eventOnce` `eventEmit` `eventEmitAndWait` `eventRemoveListener` `eventMakeLast` `eventMakeFirst` `eventClearEvent` `eventClearListener` `eventClearAll`
- **变量**（7）：`getVariables` `getAllVariables` `replaceVariables` `updateVariablesWith` `deleteVariable` `insertOrAssignVariables` `insertVariables`
- **消息**（3）：`getChatMessages` `setChatMessages` `setChatMessage`
- **世界书**（11）：`getLorebookEntries` `getCurrentCharPrimaryLorebook` `getLorebookSettings` `setLorebookSettings` `getCharWorldbookNames` `getCharLorebooks` `updateWorldbookWith` `getWorldbook` `getWorldbookNames` `getChatWorldbookName` `getGlobalWorldbookNames`
- **按钮**（5）：`getScriptButtons` `replaceScriptButtons` `updateScriptButtonsWith` `appendInexistentScriptButtons` `getButtonEvent`
- **工具/杂项**（17）：`getTavernHelperVersion` `getTavernVersion` `triggerSlash` `substitudeMacros` `getLastMessageId` `getCurrentMessageId` `getScriptId` `getScriptName` `getScriptInfo` `replaceScriptInfo` `getIframeName` `reloadIframe` `errorCatched` `initializeGlobal` `waitGlobalInitialized` `registerMacroLike` `_bind`

### 2.4 事件总线：82 个常量，约 30 个真的会发

常量表是**完整的**（对齐 ST/JSR 真源）：

- `tavern_events`：**82 个** — `shims/constants.js:6-89`
- `iframe_events`：**9 个** — `shims/constants.js:99-109`

但「登记了常量」≠「宿主会发」。**实际有发射点的 ST 事件约 30 个**：

| 事件 | 发射方 | 证据 |
|---|---|---|
| `app_ready` | Kotlin | `TavernRuntimeManager.kt:333` |
| `message_sent` / `message_received` | Kotlin | `service/ChatService.kt:511,1334` |
| `message_edited` / `message_updated` | Kotlin | `ChatService.kt:2030-2031,2210,2227` |
| `message_swiped` | Kotlin + JS | `ChatService.kt:2209,2226`；`runtime.js:613` |
| `message_deleted` / `message_swipe_deleted` | Kotlin | `ChatService.kt:2251,2299` |
| `generation_started` / `generation_ended` / `generation_stopped` | Kotlin | `data/ai/GenerationHandler.kt:104`；`ChatService.kt:1344,2385` |
| `stream_token_received` | Kotlin | `GenerationHandler.kt:740` |
| `settings_updated` / `worldinfo_settings_updated` | Kotlin | `data/datastore/SettingsEventDiff.kt:25-27` |
| `worldinfo_entries_loaded` / `worldinfo_scan_done` | Kotlin | `PromptInjectionTransformer.kt:347,549` |
| `user_message_rendered` / `character_message_rendered` | Kotlin | `TavernRuntimeManager.kt:272` |
| `GENERATION_AFTER_COMMANDS` | JS | `runtime.js:1299` |
| `GENERATE_BEFORE_COMBINE_PROMPTS` | JS | `runtime.js:1310` |
| `GENERATE_AFTER_DATA` | JS | `runtime.js:1322` |
| `GENERATE_AFTER_COMBINE_PROMPTS` | JS | `runtime.js:1361` |
| `CHAT_COMPLETION_SETTINGS_READY` / `CHAT_COMPLETION_PROMPT_READY` | JS | `runtime.js:1328-1341` |
| `chat_id_changed` / `chat_changed` | JS | `runtime.js:2326-2327` |
| `oai_preset_changed_before/after`、`preset_changed/deleted/renamed/renamed_before` | JS | `st-compat/preset-manager.js:92-141` |

**只登记、宿主不发射的**（约 50 个）——依赖它们做初始化的插件会「静默不工作」：

`EXTENSIONS_FIRST_LOAD`、`EXTENSION_SETTINGS_LOADED`、`SETTINGS_LOADED`、`SETTINGS_LOADED_BEFORE/AFTER`、`WORLD_INFO_ACTIVATED`、`WORLDINFO_UPDATED`、`CHAT_CREATED`、`CHAT_DELETED`、`CHARACTER_EDITED`、`CHARACTER_PAGE_LOADED`、`CHARACTER_DELETED`、`CHARACTER_DUPLICATED`、`CHARACTER_RENAMED`、`MORE_MESSAGES_LOADED`、`IMPERSONATE_READY`、`IMAGE_SWIPED`、`SD_PROMPT_PROCESSING`、`TOOL_CALLS_PERFORMED`、`TOOL_CALLS_RENDERED`、`SECRET_*`、`CONNECTION_PROFILE_*`、`ONLINE_STATUS_CHANGED`、`FORCE_SET_BACKGROUND`、`MOVABLE_PANELS_RESET`、`TEXT_COMPLETION_SETTINGS_READY`、`MAIN_API_CHANGED`、`CHARACTER_FIRST_MESSAGE_SELECTED`、`MESSAGE_FILE_EMBEDDED`、`MESSAGE_REASONING_*`、`STREAM_REASONING_DONE`、`FILE_ATTACHMENT_DELETED`、`MEDIA_ATTACHMENT_DELETED` 等。

`iframe_events` 里 **3 个已登记但未接线**（代码注释自己承认，列入 v228 缺口）：
`GENERATION_REQUESTED`、`REASONING_TOKEN_RECEIVED_FULLY`、`REASONING_TOKEN_RECEIVED_INCREMENTALLY` — `shims/constants.js:91-98`

### 2.5 宿主端点：只有 2 个 ⚠️

这是**最重要的边界**。

| 端点 | 状态 | 证据 |
|---|---|---|
| `POST /api/backends/chat-completions/status` | ✅ 实现（探测可用模型） | `runtime/TavernChatCompletionsApi.kt`；`runtime.js:53-54,140-148` |
| `POST /api/backends/chat-completions/generate` | ✅ 实现（SSE + `stream:false` 双路径，逐块回推） | `runtime.js:101-138`；`TavernRuntimeManager.kt:1058-1154` |
| `/api/sd/*`（生图）、`/api/tts/*`（语音）、`/api/vector/*`（向量）、`/api/characters/*`、`/api/worldinfo/*`、`/api/settings/*`、`/api/secrets/*`、`/api/extensions/*`、`/api/files/*`、`/api/quick-replies/*`、`/api/tokenizers/*`、`/api/backends/text-completions/*` | ❌ **未实现 → 404** | 全仓库 grep 无命中；`interceptAsset` 未命中即 `notFound()`（`TavernRuntimeManager.kt:372,491-492`） |

为什么必须走 JS 侧拦 `fetch`：Android WebView 的 `shouldInterceptRequest` **拿不到 POST body**，而 JSR 的 `generate()` 是 POST + 请求体 —— 见 `runtime.js:42-47` 的说明。

**推论**：只要一个插件要读写 ST 的服务器能力（向量检索、生图、TTS、角色库、密钥、扩展自安装），它就会拿到 404。

### 2.6 兼容 DOM 容器

宿主伪造了一批隐藏容器，因为「jQuery 对空集合 `append` 是静默 no-op」→ 表现为「点了没反应」（`index.html:22-38`）：

`#extensions_settings`、`#extensions_settings2`、`#extensionsMenu`、`#extensions_menu`、`#extensions-settings-button`、`#translation_container`、`#extensions_details`、`#mvu-standalone-buttons`、`#send_form`（**故意用 `div` 不用 `form`**，防止扩展调 `.submit()` 导致 WebView 导航刷新）、`#tavern_helper`（运行时动态创建）、`#chat`（隐藏楼层桩）。

### 2.7 第三方库注入

对齐 ST 1.18 的加载顺序（`index.html`）：jQuery 3.5.1 → lodash → toastr → EJS → Vue 3 + Vue Router → KaTeX 0.16.45 → highlight.js 11.9 → third-party-globals → select2 4.1.0。

`vendor/` 下还有：DOMPurify、zod、mathjs、showdown、yaml、json5、jsonrepair、seedrandom、partial-json、klona、compare-versions、bowser、fast-sha256、decimal.js、complex.js、fraction.js、typed-function、tiny-emitter、p-retry、retry、@stablelib/base64、standardwebhooks、@babel/runtime、OpenAI SDK、Anthropic SDK、Google GenAI(×2)、pi-ai、tiktoken 词表（`cl100k_base` / `o200k_base`）。

`vendor/st/` 下是 ST 官方的 `events.js` / `eventemitter.js`（**真源文件**，不是重写）。

### 2.8 离线化

- **jsDelivr CDN 本地化**：`/gh/<user>/<repo>@<ver>/<path>` 与 `/npm/<pkg>@<ver>/<path>` → 本地 `vendor/`，未命中回退真实网络（`TavernRuntimeManager.kt:467-489`）
- `/npm/` 前缀 → `st-runtime/vendor/npm/`
- `/version` 端点返回 `pkgVersion = 1.13.5`（**能力闸门**：JSR 全库的 `compare(version, …)` 都读这个值；取值理由是「恰好打开 G1/G2/G3 三个特性」）— `TavernRuntimeManager.kt:358-363,494-499`

---

## 3. 点名适配过的扩展（有代码证据）

| 扩展 | 角色 | 证据 | 强度 |
|---|---|---|---|
| **JS-Slash-Runner（酒馆助手）** | 主目标。脚本 iframe、按钮、事件、变量、世界书、`triggerSlash`、`generate/generateRaw` 全链路 | `runtime.js` 大量 JSR 真源行号引用；`TavernChatCompletionsApi.kt`（为 JSR `generateRaw` 专门实现的兼容层）；`data/st/runtime/TavernScripts.kt:26-30` | **A/B** |
| **ST-Prompt-Template（提示词模板）** | EJS 模板渲染、`getCharacterDefine()`、`PromptManager`、变量 scope 语义 | `shims/ejs-render.js:1-38`；`data/ai/transformers/EjsInputTransformer.kt`；`st-compat/preset-manager.js:86`；`PromptInjectionTransformer` 注释；开关 `PreferencesStore.kt:810` | **A/B** |
| **MVU（MagVarUpdate / mvu-standalone）** | 变量框架 + 面板 UI | `st-runtime/mvu/bundle.js`(573 KB，MIT)；`data/st/runtime/UpdateVariableParser.kt`；`runtime.js:2195-2350`（MVU iframe 专门装配）；`#tavern_helper` DOM 为 MVU `unique_check` 定制 | **A/B** |
| **st-memory-enhancement（记忆增强）** | 被当作「源码型扩展」的兼容基准：`lib.js` 的 `Bowser` 导出、`power-user.js`、`getSlideToggleOptions`、select2 依赖 | `st-compat/lib.js:18-21`；`st-compat/power-user.js:66-68`；`st-compat/script.js:185`；`index.html:44-46` | **B** |
| **ST-Prompt-Template / 记忆增强 的设置页渲染** | `renderExtensionTemplateAsync` 返回空串导致设置页黑屏 → 已修 | `st-compat/extensions.js:74`；`runtime.js:1220` | **B** |

> **另外**：`D:\rikkaST-refs\` 是一个**调研语料库**（19 个分类目录、数百个克隆仓库：`C-st-ext`、`E-worldinfo-memory`、`F-cards-render`、`H-ext-popular`、`K-st-official2`、`L-st-ext2`、`R-interaction-prompt` 等）。
> **语料库里存在的仓库 ≠ 已适配的插件。** 只有上面表格里那些在**代码注释/测试里被点名引用**的，才算真正做过兼容工作。

---

## 4. 可运行性分档

| 档位 | 判据 | 代表 |
|---|---|---|
| ✅ **能跑（有适配证据）** | 只依赖 ST 核心模块 + DOM + 事件 + TavernHelper API，且代码里点过名 | JS-Slash-Runner、ST-Prompt-Template、MVU 系卡（如「魔法少女」探针卡）、st-memory-enhancement 的设置页 |
| 🟡 **可能能跑（结构支持，无实测）** | 同上，但没有任何针对性修复记录。绝大多数「UI 增强 / 排版 / 状态栏 / 按钮」类插件属于此档 | 各类状态栏、悬浮面板、纯前端小工具 |
| 🟠 **部分能跑** | 需要某几个未发射的事件，或需要少量 `/api` | 依赖 `EXTENSIONS_FIRST_LOAD` / `WORLD_INFO_ACTIVATED` 做初始化的插件 |
| ❌ **跑不了** | 需要未实现的宿主端点或 ST 内置扩展 | 向量/RAG（`/api/vector`）、生图（`/api/sd`）、TTS 扩展（`/api/tts`）、角色库、扩展自安装、ST 官方内置扩展 |

---

## 5. 结构性缺口清单

1. **ST 服务端 API 只有 2/12+ 类**——§2.5。这是第一缺口。
2. **ST 官方内置扩展零实现**——只有 `regex/engine.js`。`vectors` / `expressions` / `caption` / `summarize` / `tts` / `sd` / `gallery` / `assets` 全部缺失。
3. **约 50 个事件只登记不发射**——§2.4。
4. **3 个 iframe 事件未接线**——`GENERATION_REQUESTED` 与两个推理 token 事件（代码自认的 v228 缺口）。
5. **没有扩展商店 / 在线索引 / 依赖自动解析**（`manifest.requires` 只解析不安装）。
6. **没有 iframe 网络代理**：扩展里的第三方 `fetch` 直连真实网络（离线环境下会失败）。
7. **第三方扩展的 zip 安装要求**内含 `manifest.json`，裸 JS 目录结构不认。

---

## 6. 关于「生图」和「TTS」

这两项**在本 App 里是以原生功能提供的，不是靠酒馆扩展**：

| 能力 | 实现方式 | 证据 |
|---|---|---|
| 文生图 | 原生页面 + 原生 Provider（OpenAI `/images/generations`、Claude）；作为内置工具 `ImageGeneration` 可被模型调用 | `ui/pages/imggen/ImgGenPage.kt`、`ui/pages/imggen/ImgGenVM.kt`、`ai/.../openai/OpenAIProvider.kt:202-231`、`ai/.../Model.kt:56` |
| TTS | 原生 `speech` 模块，**11 个 Provider**：ElevenLabs / FishAudio / Gemini / Groq / MiMo / MiniMax / OpenAI / Qwen / Step / System / xAI | `speech/src/main/java/me/rerere/tts/provider/providers/` |
| ASR（语音输入） | 原生，**5 个 Provider**：DashScope / MiMo / OpenAI Realtime / Step / Volcengine | `speech/src/main/java/me/rerere/asr/providers/` |
| 酒馆的「生图扩展」「TTS 扩展」 | ❌ **跑不了**（需要 `/api/sd/*`、`/api/tts/*`，未实现） | §2.5 |

所以：**功能上有替代，路径上不兼容**。你在酒馆里用惯的 `Image Generation` / `TTS` 扩展装进来不会工作；但 App 自带的生图和朗读是完整可用的。

---

## 7. 怎么自己判断一个新插件能不能跑

1. **装上**：扩展中心 → 第三方扩展 → URL 或 zip。
2. **开日志**：`/sdcard/Android/data/me.rerere.rikkahub.st/files/tavern-runtime.log`
   （也可在 App 内查看，`TavernRuntimeManager` 会持久化，上限 400 行 / 256 KB 滚动）
3. **看三类信号**：
   - `[compat] 缺少 st-compat shim: <path>` → **模块缺失**，该扩展整条 import 链断掉（`TavernRuntimeManager.kt:450`）
   - `[global] unhandledrejection` / `TypeError` → 缺 DOM 或 API
   - HTTP 404（非上面两类）→ 大概率是打到了未实现的 `/api/*`
4. **看它依赖什么**：
   - 只用 `script.js` / `extensions.js` / `TavernHelper` / 事件 / DOM → 大概率能跑
   - 用了 `/api/` 下 chat-completions 以外的路径 → 跑不了
   - 依赖 ST 内置扩展目录（`/scripts/extensions/vectors/` 等）→ 跑不了

---

## 8. 测试与验证现状

**有自动化覆盖的部分**（`app/src/test/java/me/rerere/rikkahub/data/st/`，16 个测试类）：

`regex/`（3）、`script/`（2）、`runtime/`（5）、`import/`（3）、`macro/`（2）、`extensions/`（2）

外加 `app/src/test/resources/` 里的**真机探针卡**（用于在手机上跑断言、日志里输出 `PASS/FAIL`）：

- `card_render_probe.json`（渲染 / 消息生命周期 / 正则 / 世界书）
- `card_events_probe.json`（裸全局 eventOn/eventEmit、eventOnce、stop()、iframe 身份、parent 可达）
- `card_buttons_probe.json`（脚本按钮族 API）
- `magical_girl_card.json`（真实 V3 卡：47 条世界书 + 11 条正则 + 3 个酒馆助手脚本）

**没有自动化覆盖、只有代码路径确认的部分**：

- 第三方扩展装载链路（安装 → 伺服 → 执行）—— 无集成测试
- `TavernRuntimeManager` 的 WebView 侧行为 —— 只能靠真机日志
- §2.5 的 404 行为 —— 是设备无关的逻辑推断，未逐个实测

---

## 9. 一句话总结

> **数据层和引擎层：做完了，而且做得比「能用」更细。**
> **插件层：搭的是一个「酒馆前端运行时」，不是逐个插件打补丁 —— 已经打通酒馆助手、提示词模板、MVU 三条主线；其余插件的可运行性取决于它是否碰酒馆的服务端 API。**
> **最需要补的一块：把 `/api/**` 从 2 个端点扩出去（尤其 `/api/vector`、`/api/sd`、`/api/tts`），以及补齐那约 50 个未发射的事件。**
