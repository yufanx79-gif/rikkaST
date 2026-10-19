# SillyTavern 1.18.0 → 1.19.0 子系统级精确差异分析 (subsystem-diff)

基于官方 `SillyTavern-1.18.0`（988 文件）与 `SillyTavern-1.19.0`（1010 文件）源码树逐文件比对（共 80 个修改文件、22 个新增文件、0 个删除文件，完整清单见 `audit/upstream/changed-files.csv`），按子系统归纳如下：

## 1. 消息格式化管线 (`MessageFormatter`)
- **新增文件**：`public/scripts/message-formatter.js`
- **关联改动**：`public/script.js`
- **核心变更**：
  - 新增 `MessageFormatter` 类及全局实例 `messageFormatter`，定义三阶段格式化管线：
    - `BEFORE_REGEX = 'before_regex'`
    - `AFTER_REGEX = 'after_regex'`
    - `AFTER_MARKDOWN = 'after_markdown'`
  - 定义优先级枚举 `hook_order`：`EARLIEST (-Infinity)`、`EARLY (-100)`、`NORMAL (0)`、`LATE (100)`、`LATEST (Infinity)`。
  - 提供 `addHook(stage, callback, { order, owner })`、`removeHook(callback)`、`clearHooksByOwner(owner)`、`runStage(stage, content, context)`、`format(content, { converter, regex, context })`。
  - 在 `SillyTavern.getContext()` 中暴露 `messageFormatter` 实例（`ST-iPhonie` 强依赖此 API）。

## 2. 第三方扩展生命周期与拦截器 (`extensions.js` / `script.js`)
- **关联文件**：`public/scripts/extensions.js`, `public/script.js`
- **核心变更**：
  - `manifest.json` 支持 `hooks: { activate, enable, disable, delete }`、`generate_interceptor`、`minimum_client_version`、`optional`。
  - 扩展启用/加载时调用模块导出的 `enable()` / `activate()`，禁用时调用 `disable()`，卸载/删除时调用 `delete()`。
  - 生成前按 `loading_order` 顺序调用已启用扩展声明的全局函数 `window[manifest.generate_interceptor](chat, contextSize, abort, type)`。
  - `SillyTavern.getContext()` 暴露 `getExtensionManifest(name)`。

## 3. 宏系统与变量键/索引操作 (`macros/*`, `variables.js`)
- **关联文件**：
  - 新增 `public/scripts/macros/definitions/comment-closing-macros.js`（支持 `{{///}}` 块注释闭合宏）
  - 修改 `public/scripts/macros/definitions/variable-macros.js`、`public/scripts/variables.js`、`public/scripts/macros/engine/MacroLexer.js`、`public/scripts/macros/engine/MacroCstWalker.js`、`public/scripts/macros/engine/MacroEnv.styles.js`
- **核心变更**：
  - `variable-macros.js` 新增结构化 JSON/数组路径读写宏：`setvarkey`（别名 `setvarindex`）、`getvarkey`（别名 `getvarindex`）、`setglobalvarkey`（别名 `setglobalvarindex`）、`getglobalvarkey`（别名 `getglobalvarindex`），使用 lodash 风格深路径（如 `a.b[0].c`）读写聊天局部或全局变量。
  - `MacroLexer.js` 修复引号或非命令上下文中竖线 `|` 字符的词法切分问题。
  - `MacroCstWalker.js` 支持 `{{///}}` 闭合注释宏，将开闭标记之间的原文本整体作为注释忽略。

## 4. OpenAI / Chat Completions 请求构建与模型供应商 (`openai.js` / `src/endpoints/backends/chat-completions.js`)
- **关联文件**：`public/scripts/openai.js`, `src/endpoints/backends/chat-completions.js`, `src/endpoints/openai.js`
- **核心变更**：
  - `openai.js` 更新 `getChatCompletionModel()`、新增 `POLLINATIONS_ENDPOINT`、支持 `xai` / `pollinations` / `moonshot` / `xiaomi` / `zai` 等模型配置更新。
  - `createGenerationParameters`、`sendOpenAIRequest`、`getStreamingReply`、`tryParseStreamingError`、`getPresetApplicationPromise` 供前端扩展（如 `JS-Slash-Runner`）直接 import 调用。
  - `script.js` 中 `deleteMessage(id, swipeId, confirm, { deleteToolCalls })` 新增级联删除关联 Tool Calls 的选项。

## 5. 世界书与预设管理 (`world-info.js`, `preset-manager.js`)
- **关联文件**：`public/scripts/world-info.js`, `public/scripts/preset-manager.js`
- **核心变更**：
  - `world-info.js` 改进条目排序、绑定与持久化导出函数（`saveWorldInfo`、`createNewWorldInfo`、`createWorldInfoEntry`、`deleteWorldInfoEntry`、`deleteWorldInfo`）。
  - `preset-manager.js` 改进预设切换与 `getPresetApplicationPromise` 同步等待。

## 6. 桌面/服务端专属变更（Android 宿主不直接照搬部分）
- **关联文件**：`src/server-CopyFile.js`, `src/endpoints/assets.js`, `src/endpoints/classify.js`, `src/endpoints/speech.js`, `public/lib.js`, `public/css/*`, `public/locales/*`
- **说明**：官方桌面版 Node.js 后端路由、HuggingFace transformers 本地分类器等属于桌面服务端架构；在 `rikkaST` 中由 Kotlin 原生层或共享 PRoot Node.js 运行时按需桥接（详见 `porting-decisions.md` 与 `unmapped-items.md`）。
