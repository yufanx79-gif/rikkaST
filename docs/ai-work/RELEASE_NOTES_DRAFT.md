# rikkaST 2.5.6 更新说明（候选发布稿）

> **状态提示**：本源码包已完成全量静态代码审查与回归门禁核验，待本机编译生成 APK 后即可发布。

### ✨ 新增与酒馆生态兼容增强
- **`.charx` / `.byaf` 角色卡与内嵌贴图全链路支持**：
  - 导入 `.charx` 角色卡或酒馆整包备份 ZIP 时，自动提取内嵌图标、表情立绘与背景资源，并将开场白、角色描述、世界书与正则中的 `embeded://` / `embedded://` / `ccdefault:` 路径自动转换为可直接显示的本地资源地址。
- **SillyTavern 预设参数深度生效**：
  - 启用酒馆预设后，除了提示词顺序与正则脚本外，预设内的采样参数（Temperature、Top-P、Top-K、Min-P、Top-A、Frequency/Presence/Repetition Penalty、Max Tokens、Seed）、自定义停止词（Stopping Strings）、`logit_bias`、消息角色合并策略（`prompt_post_processing`：Merge / Semi-strict / Strict / Single）以及用户发言自动加引号（`wrap_in_quotes`）均已接入生成管线。
- **STscript 交互式弹窗与工具调用（Function Calling）**：
  - `/popup`、`/input`、`/buttons` 命令现在会在聊天界面弹出真实的交互对话框，支持脚本暂停等待您的输入或选项点击；
  - 支持前端插件通过 `SillyTavern.registerFunctionTool` 或 STscript `/tools-register` 注册自定义函数工具，并在 AI 生成过程中自动参与 Tool Calling 循环。
- **Quick Reply v2 自动触发与管理升级**：
  - 快速回复编辑弹窗新增 QR 集合名、`Automation ID` 以及“启动时触发 / 切会话触发 / 发送时触发 / AI 回复后触发 / 群聊发言前触发 / 隐藏按钮”开关；
  - 完整接通世界书条目激活后按 `automationId` 自动联动执行对应快速回复脚本。
- **世界书与聊天记录酒馆标准格式导出**：
  - 世界书卡片菜单新增“导出为酒馆 World Info (.json)”；
  - 聊天记录导出面板新增“SillyTavern (.jsonl)”标准格式导出，方便与桌面端酒馆双向迁移。

### 🎨 WebView 楼层卡片与前端体验优化
- **状态栏卡片识别与防闪烁更新**：
  - 放宽对现代 CSS 状态栏（含 `background-image`、`flex`/`grid` 布局、`<svg>` 仪表盘）的识别；
  - 对不含 `<script>` 的纯 HTML/CSS 状态栏更新采用原地 DOM 刷新，消除流式生成或变量更新时的整页重载白屏闪烁；带 `<script>` 的交互卡仍保持独立文档重载以确保脚本干净执行。
- **外链图片防盗链与本地字体/音频 MIME 补齐**：
  - 内嵌面板默认启用 `no-referrer` 策略，解决部分外部图床因 Referer 校验返回 403 裂图的问题；
  - 补齐 `.webp`、`.avif`、`.jpg`、`.gif`、`.woff`/`.ttf` 字体及 `.mp3`/`.wav`/`.ogg` 音频的本地虚拟域名 MIME 映射。

### 🛠️ 内部重构与稳定性修复（开发者关注）
- 修复此前源码基线中的 12 处 Kotlin 编译期阻塞点（扩展函数导入、`SelectiveLogic` 穷尽分支、可空 `Uuid` 传参、重复命名参数、Elvis 运算符优先级、`@JvmName` 签名冲突、`delayOverrideEntries` 形参透传、`StClosure.raw` 字段对齐、`runClosure` 调用修正、`ChatPage` 作用域提升、`AssistantImporter` 协程非局部返回及 `UIMessage` 导入）。
- 增补覆盖世界书 `delay` 覆盖、预设采样器后处理、STscript 闭包工具与模态对话框、WebView 状态机的 JUnit 单元测试。
