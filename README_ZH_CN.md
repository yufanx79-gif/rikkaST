# rikkaST — 原生 Android 酒馆 (SillyTavern 1.19) 深度兼容客户端

基于 **RikkaHub** 打造的原生 Android AI 角色扮演与酒馆（SillyTavern 1.19.0）全功能兼容客户端。

## ✨ 核心特性

1. **全格式角色卡一键导入与即刻开聊**：
   - 原生支持 `.png` (V2 / CCv3)、`.json`、`.charx` (ZIP 内嵌立绘/CG 资产包) 与 `.byaf` (Backyard AI)。
   - 自动提取并落盘内嵌世界书、正则脚本、表情立绘与角色头像别名（`/thumbnail`、`/characters/<id>.png`）。
2. **SillyTavern 1.19.0 完整引擎对齐**：
   - **世界书引擎**：支持递归激活、延迟递归、粘性/冷却/延迟、包含组与组评分、`@D` 深度注入与 `{{outlet::name}}` 宏出口。
   - **正则与排版管线**：支持三源（全局/预设/角色卡）分桶去重、多段 `<style>` 精确配对切分、静态 HTML 卡片（`post-card`）识别、未配对 `〔`/`〕` 装饰框清理及 `[出图]...[/出图]` 折叠生图卡。
   - **MacroEngine 2.0 & STscript**：内置 75+ 核心宏、JS `substituteParams` 双向同步桥、完整 STscript 管道/闭包/作用域变量与流控指令。
3. **五大酒馆生态扩展与常驻小手机悬浮窗**：
   - 深度适配 `JS-Slash-Runner` (JSR)、`ST-Prompt-Template` (EJS)、`MVU` (MagVarUpdate)、`st-memory-enhancement`（记忆表格）、`LittleWhiteBox`（小白X）与 `ST-iPhonie`（小手机）。
   - 右侧常驻悬浮球支持小手机双通道 TTS 隔离、自定义头像/壁纸文件上传、JS 弹窗确认、二级界面导航与「本聊天不再显示」。
4. **极速离线 WebView 与可靠生成控制**：
   - 核心运行时资产 100% 离线内置并启用强缓存与单扩展 5 秒超时保护，无 VPN / 飞行模式下秒开。
   - 助手消息面板下方自带「重载面板」按钮；停止生成按钮在输入框非空时依然常驻可见，支持 2.5 秒有界立即中断。

## 🚀 3 分钟快速上手
1. 打开应用，点击聊天栏上方的 **「极速配置」**（或进入「新手向导」），选择 DeepSeek / OpenRouter / 硅基流动 / Gemini 模板并填入 API Key，点击「保存并启用模型」。
2. 进入「助手」页，点击 **「一键导入角色卡 (.png / .json / .charx / .byaf)」** 选择角色卡文件，导入后自动激活并直接进入新对话。
3. 更多详情请参阅：
   - [兼容性矩阵 (COMPATIBILITY_MATRIX.md)](./COMPATIBILITY_MATRIX.md)
   - [兼容徽章规范 (docs/COMPATIBILITY_BADGES.md)](./docs/COMPATIBILITY_BADGES.md)
   - [已知问题与排查指南 (KNOWN_LIMITATIONS.md)](./KNOWN_LIMITATIONS.md)
   - [发版核查清单 (docs/releases/RELEASE_CHECKLIST.md)](./docs/releases/RELEASE_CHECKLIST.md)
