# SillyTavern 1.18.0 兼容性矩阵（COMPATIBILITY_MATRIX）

> **口径说明**：本矩阵基于本轮核实的 `rikkaST` 真实调用链与 `SillyTavern-1.18.0` 官方源码比对生成，严格区分 **`SUPPORTED`（完整端到端闭环）**、**`PARTIAL`（核心路径可用但存在宿主/移动端边界差异）**、**`STUB_OR_FALLBACK`（仅安全降级/返回默认值）** 与 **`UNSUPPORTED`（架构不适用或明确排除）**。

## 1. 数据资产导入与导出（Assets, Cards, Presets, Lorebooks, Chats）
| 能力子项 | 状态 | 真实调用链与源码证据 | 备注 / 边界说明 |
|---|---|---|---|
| Character Card V2 / V3 PNG & JSON 导入 | `SUPPORTED` | `AssistantImporter.kt` (`parseCardFromUri`, `parseCardJson`) | 支持 `chara`/`ccv3` 块、Alternate Greetings、`group_only_greetings`、`character_book`、内嵌 `regex_scripts` 与 TavernHelper 脚本 |
| `.charx` (CCv3 ZIP) 导入与 `embeded://` 资产映射 | `SUPPORTED` | `AssistantImporter.kt` (`parseCharxBytes`) + `TavernRuntimeManager.kt` (`interceptAsset`) + `StArchiveImporter.kt` | 本轮补齐 `filesDir/charx_assets/<id>/` 落盘、`https://st.rikka.local/charx_assets/...` 路由与开场白/描述/世界书/正则中的 `embeded://` 重写 |
| `.byaf` (Backyard AI Archive) 导入 | `SUPPORTED` | `AssistantImporter.kt` (`parseByafBytes`) + `StArchiveImporter.kt` | 支持 `character.json`、头像资源与 `loreItems` 转换 |
| SillyTavern Chat Completion 预设导入与消费 | `SUPPORTED` | `ExportSerializer.kt` (`ModeInjectionSerializer`, `parseActiveStPresetConfig`) + `GenerationLoop.kt` | 支持 `prompts` + `prompt_order`、`wi_format`、`scenario_format`、`personality_format`、`new_chat_prompt`、`assistant_prefill`、`squash_system_messages`、`wrap_in_quotes`、`prompt_post_processing`（`merge`/`semi`/`strict`/`single`）及采样器/停止词/`logit_bias` 透传 |
| World Info (Lorebook) 导入与扫描激活 | `SUPPORTED` | `ExportSerializer.kt` (`LorebookSerializer`) + `PromptInjectionTransformer.kt` + `WorldInfoDecorators.kt` | 支持常驻、主副关键词（`AND_ANY`/`NOT_ALL`/`NOT_ANY`/`AND_ALL`/`OR_ANY`）、递归扫描、预算裁剪、Inclusion Group 权重/评分、Sticky/Cooldown/Delay 时效、Character Filter、词袋余弦向量激活与 `@@` 装饰器 |
| World Info 导出为官方 SillyTavern JSON | `SUPPORTED` | `ExportSerializer.kt` (`SillyTavernLorebookSerializer`) + `PromptPage.kt` | 本轮在世界书卡片菜单接通导出入口，支持无损往返再导入 |
| 聊天记录 `.jsonl` 导入与导出 | `SUPPORTED` | `TavernRuntimeManager.kt` (`importConversationFromStJsonl`, `exportConversationToStJsonl`) + `ConversationExport.kt` | 本轮在会话导出面板新增 `.jsonl` 导出入口，保留 `swipes`、`swipe_id` 与 `variables` |

## 2. 宏、变量与正则引擎（Macros, Variables & Regex Scripts）
| 能力子项 | 状态 | 真实调用链与源码证据 | 备注 / 边界说明 |
|---|---|---|---|
| 聊天变量 / 全局变量 / 消息级变量 | `SUPPORTED` | `TavernVariableStore.kt` + `MacroEngine.kt` + `ChatSlashHost.kt` | 支持 `{{getvar::}}`、`{{setvar::}}`、`{{addvar::}}`、`{{incvar::}}`、`{{decvar::}}`、`{{getglobalvar::}}`、`{{setglobalvar::}}` 及 JSON 路径读写 |
| 60+ 官方常用宏（时间、随机数、骰子、系统状态） | `SUPPORTED` | `MacroEngine.kt` + `PromptInjectionTransformer.kt` | 支持 `{{char}}`、`{{user}}`、`{{random}}`、`{{pick}}`、`{{roll}}`、`{{time}}`、`{{date}}`、`{{idle_duration}}`、`{{lastMessage}}`、`{{lastUserMessage}}`、`{{lastCharMessage}}`、`{{outlet::}}` 等 |
| 正则脚本（Regex Scripts）6 大 Placement 与分阶段替换 | `SUPPORTED` | `RegexScriptEngine.kt` + `PromptInjectionTransformer.kt` + `GenerationLoop.kt` + `ChatList.kt` | 支持 `USER_INPUT`、`AI_OUTPUT`、`SLASH_COMMAND`、`WORLD_INFO`、`REASONING`、`MD_DISPLAY`、`runOnEdit`、`minDepth`/`maxDepth`、`trimStrings` 与宏替换模式 |

## 3. STscript / Slash 命令引擎（Slash Commands）
| 命令分类 | 状态 | 真实调用链与源码证据 | 备注 / 边界说明 |
|---|---|---|---|
| 语言流控与闭包（`/let`, `/var`, `/if`, `/else`, `/while`, `/times`, `/break`, `/return`, `{: :}`, `{| |}`） | `SUPPORTED` | `StSlashParser.kt` + `StClosure.kt` + `StSlashExecutor.kt` | 支持局部作用域、闭包参数默认值、管道注入阻断（`||`）、`/closure-serialize` 与 `/closure-deserialize` |
| 算术、字符串、数组与 JSON 操作（`/add`..`/abs`, `/sin`..`/sqrt`, `/len`, `/split`, `/join`, `/sort`, `/fuzzy`） | `SUPPORTED` | `StSlashExecutor.kt` | 纯函数管线完整实现，包含除零与负数开方防 `NaN` 保护 |
| 模态交互弹窗（`/popup`, `/input`, `/buttons`） | `SUPPORTED` | `StSlashExecutor.kt` + `ChatSlashHost.kt` + `TavernRuntimeManager.kt` + `ChatPage.kt` | 本轮接入真实 Compose `AlertDialog` 协程挂起交互；无 UI 时自动回退默认值 |
| 消息读写、隐藏、Swipe、生成与旁白（`/send`, `/sendas`, `/sys`, `/messages`, `/hide`, `/unhide`, `/swipe`, `/trigger`, `/continue`, `/impersonate`, `/gen`, `/genraw`, `/sysgen`） | `SUPPORTED` | `ChatSlashHost.kt` + `ChatVM.kt` | 接入真实会话消息树与静默生成通道 |
| 分支与检查点（`/checkpoint-*`, `/branch-create`） | `SUPPORTED` | `ChatSlashHost.kt` + `ChatVM.kt` | 基于 RikkaHub 消息树节点与衍生会话实现 |
| API / 人设 / 扩展 / 外观宿主命令（`/api-url`, `/proxy`, `/persona-lock`, `/persona-sync`, `/qr-context*`, `/extension-*`, `/bg`, `/theme`, `/context`, `/forcesave`） | `SUPPORTED` | `ChatSlashHost.kt` | 本轮从空返回升级为真实读写 `Settings` 与 `TavernVariableStore` |
| 桌面端专属 UI 布局命令（`/movingui`, `/vn`, `/bubble`, `/flat`, `/panels`, `/resetpanels`, `/css-var`） | `STUB_OR_FALLBACK` | `StSlashExecutor.kt` | Android 原生 Compose 布局无对应的桌面端 CSS 布局切换器，安全返回空串不中断脚本管道 |

## 4. TavernHelper / SillyTavern 前端 Runtime API 与插件生态
| 接口 / 插件类别 | 状态 | 真实调用链与源码证据 | 备注 / 边界说明 |
|---|---|---|---|
| `SillyTavern.getContext()` & 事件总线（`eventSource.on` / `emit` / `makeLast` / `makeFirst`） | `SUPPORTED` | `runtime.js` + `TavernRuntimeManager.kt` | 支持 `MESSAGE_SENT`、`MESSAGE_RECEIVED`、`GENERATION_STARTED`、`GENERATION_ENDED`、`CHAT_CHANGED`、`WORLD_INFO_ACTIVATED`、`SETTINGS_UPDATED` 等核心生命周期事件 |
| TavernHelper 变量、宏、世界书、预设、楼层消息 CRUD API | `SUPPORTED` | `runtime.js` + `TavernRuntimeManager.kt` | 支持 `getVariables`/`replaceVariables`/`insertOrAssignVariables`、`getWorldInfo`/`updateWorldInfo`、`getChatMessages`/`setChatMessages`、`generate`/`generateRaw` |
| `registerFunctionTool` / `unregisterFunctionTool` & `/tools-register` | `SUPPORTED` | `runtime.js` + `TavernRuntimeManager.kt` + `ChatToolFactory.kt` + `StSlashExecutor.kt` | 本轮打通 JS 与 STscript 动态工具注册表并注入原生 `GenerationLoop` 工具调用链 |
| 楼层内嵌 HTML / MVU 状态栏卡片与 `<script>` 面板 | `SUPPORTED` | `MessageHtmlBlock.kt` + `panel.html` + `TavernRuntimeManager.kt` | 支持高度自适应、渲染进程崩溃恢复、无脚本原地重挂载（防闪烁）与带脚本整页隔离重载 |
| 直接操作 SillyTavern 桌面端顶层 DOM（如 `#sheld`、`#send_but`、`#top-settings-holder`）的第三方 UI 扩展 | `PARTIAL` | `runtime.js` + `index.html`（提供常用容器桩节点） | `index.html` 提供了常用选择器挂载桩，但依赖完整桌面端 DOM 结构的重度 UI 改造插件无法在原生 Compose 界面上直接呈现 |
| SillyTavern 后端 Node.js 插件（Server Plugins / Express 路由扩展） | `UNSUPPORTED` | 架构边界 | Android 端无内置 Node.js 服务端进程，需后端文件/进程操作的插件不适用 |
| SillyTavern 官方 TTS / SD WebUI / ComfyUI 扩展路由（`/api/tts/*`, `/api/sd/*`） | `UNSUPPORTED`（由原生能力替代） | 明确排除酒馆扩展 API 承诺 | 请直接使用 RikkaHub 原生内置的语音朗读（TTSProviders）与生图模型能力 |
