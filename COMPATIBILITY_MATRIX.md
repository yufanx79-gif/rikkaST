# rikkaST 兼容性矩阵（SillyTavern 1.19.0 & RikkaHub 3.1.0 基座）

| 模块 / 子系统 | 兼容状态 | 说明与实现细节 |
| :--- | :--- | :--- |
| **角色卡导入 (`.png` / `.json` / `.charx` / `.byaf`)** | ✅ 完整支持 | 支持 V1 / V2 / CCv3、CharX ZIP 归档、Backyard `.byaf`；自动落盘 `data.assets`、内嵌立绘与角色头像别名 `/characters/<id>.png`、`/thumbnail`。 |
| **世界书引擎 (`World Info` 1.19.0)** | ✅ 完整支持 | 支持递归扫描、延迟递归级数、粘性/冷却/延迟、概率触发、包含组与组评分、角色过滤、`@D` 深度注入、`Outlet` 宏出口及 JS `getWorldInfoPrompt` / `checkWorldInfo` / `getWorldInfoSettings` 桥接。 |
| **正则脚本引擎 (`Regex` 1.19.0)** | ✅ 完整支持 | 支持全局 / 预设 / 角色卡三源按作用域分桶去重、`/pattern/flags`、不定长 lookbehind 自动有界降级、`{{match}}`、命名捕获组、`trimStrings`、`minDepth/maxDepth` 及思维链防穿透保护。 |
| **宏引擎 (`MacroEngine` 2.0 + ST 1.19)** | ✅ 完整支持 | 完整对齐 ST 1.19 `MacroLexer` / `MacroParser` / `MacroCstWalker` / `MacroRegistry` 75+ 核心宏，并打通 JS `substituteParams` → Kotlin `MacroEngine` 同步桥。 |
| **变量系统 (`variables.js` + `TavernVariableStore`)** | ✅ 完整支持 | 支持 `global` / `preset` / `character` / `chat` / `message` 五层作用域，导出 ST 1.19 全部 19 项变量函数与斜杠命令，支持 JSON 数组追加、数值累加与字符串拼接。 |
| **STscript 斜杠命令系统** | ✅ 完整支持 | 支持闭包、管道、作用域变量、控制流（`/if` `/while` `/times` `/break` `/return`）、消息增删改、检查点与分支、世界书读写、弹窗交互及明确的不支持命令白名单提示。 |
| **五大主流酒馆插件** | ✅ 完整支持 | 内置兼容 `JS-Slash-Runner` (JSR)、`ST-Prompt-Template` (EJS)、`MVU` (MagVarUpdate)、`st-memory-enhancement`（记忆表格）、`LittleWhiteBox`（小白X）与 `ST-iPhonie`（小手机）。 |
| **内嵌 HTML 面板与悬浮窗系统** | ✅ 完整支持 | 支持多段 `<style>` 精确配对切分、静态 HTML 卡片识别、面板下方手动「重载面板」按钮、常驻右侧小手机/角色卡悬浮窗、文件选择与 JS 弹窗桥接。 |
