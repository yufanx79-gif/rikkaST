# rikkaST 角色卡与插件兼容性分级标识（Compatibility Badges）

为帮助角色卡作者与玩家快速判断角色卡在移动端 `rikkaST` 的运行表现，我们定义了以下四级兼容性标识：

| 徽章标识 | 等级定义 | 适用范围与特征 |
| :--- | :--- | :--- |
| **🟢 ST-Core Ready** | 核心完全兼容 | V1 / V2 / CCv3 (`.png` / `.json` / `.charx` / `.byaf`) 角色卡、内嵌世界书（含递归/粘性/冷却/包含组/Outlet）、SillyTavern 1.19 正则引擎、MacroEngine 2.0（75+ 宏）与 STscript 斜杠命令。 |
| **🔵 ST-WebPanel Ready** | 交互式状态栏 & 面板兼容 | 含多段 `<style>` + `<div>` 状态栏、静态卡片（如 `post-card`）、EJS 提示词模板（`ST-Prompt-Template`）、`MVU` 状态树及预设消息按钮跳转（`setChatMessages` / `swipe_id`）。 |
| **🟣 ST-Floating Ready** | 小手机与角色卡悬浮窗兼容 | `ST-iPhonie`（小手机双通道 TTS / 语音切换 / 头像与昵称修改 / `[出图]` 指令捕获）、`st-memory-enhancement`（记忆表格）、`LittleWhiteBox`（小白X）与角色卡自带 `TH-script` 悬浮窗。 |
| **🟡 Host-Bridged / Optional** | 需外部服务或受控降级 | 依赖外部绘图后端（SD WebUI / ComfyUI / NovelAI）或外部 TTS 推理端的特性；未配置外部绘图的卡片会自动将 `[出图]` 转为折叠式提示词卡片，不污染正文。 |
