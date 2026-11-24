<div align="center">

<img src="docs/icon.png" alt="rikkaST Icon" width="128" height="128" />

# rikkaST

**安卓上的原生 AI 聊天客户端 · 兼为 SillyTavern 1.19.0 兼容端**

*独立包名共存 · 角色卡/世界书/预设按酒馆语义导入 · 第三方扩展与双引擎伴生服务 · 长对话 HTML 面板池*

<p>
  <b>简体中文</b> ·
  <a href="README_EN.md">English</a> ·
  <a href="README_JA.md">日本語</a>
</p>

<p>
  <a href="https://github.com/yufanx79-gif/rikkaST/releases"><img src="https://img.shields.io/github/v/release/yufanx79-gif/rikkaST?style=flat-square&label=Release&color=111111" alt="Release" /></a>
  <a href="https://github.com/yufanx79-gif/rikkaST/stargazers"><img src="https://img.shields.io/github/stars/yufanx79-gif/rikkaST?style=flat-square&color=f59e0b" alt="Stars" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0-2563eb?style=flat-square" alt="License" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square" alt="Android" />
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-555555?style=flat-square" alt="ABI" />
</p>

<p>
  <a href="https://github.com/yufanx79-gif/rikkaST/releases"><b>下载最新 APK</b></a> ·
  <a href="https://github.com/yufanx79-gif/rikkaST/issues">反馈问题</a> ·
  <a href="THIRD-PARTY-NOTICES.md">第三方组件声明</a> ·
  <a href="docs/PRIVACY-NETWORK.md">隐私与网络说明</a>
</p>

</div>

---

**rikkaST** 是基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 构建的独立开源分支，使用 Kotlin + Jetpack Compose 原生实现。

- **只想找个好用的安卓 AI 客户端**：填上 API Key 就能聊——多模型协议、流式输出、语音对话、联网搜索、主动记忆、知识库、MCP / Skills、内置 Python 3.12 与 PRoot Linux 工作区全部保留，酒馆模块默认不打扰日常使用。
- **想在手机上原生玩 SillyTavern**：角色卡、世界书、预设与 SPreset、正则、宏、斜杠命令、HTML 交互面板与第三方扩展都在客户端内原生跑通，无需在 Termux 里开网页忍受移动端排版与后台杀进程。

> [!TIP]
> rikkaST 使用独立包名 `me.rerere.rikkahub.st`，可与官方 RikkaHub 同机安装、互不覆盖，同时支持直接导入 RikkaHub 备份包（不含 SSH 远程工作区）与 SillyTavern 备份压缩包。

<!-- 建议在此处补充 3–4 张真机截图（聊天与 HTML 面板 / 角色卡与世界书 / 扩展管理 / 主题界面） -->

---

## ✨ 核心特性一览

| 方向 | 说明 |
| :--- | :--- |
| 🍺 **酒馆深度兼容** | 锁定 SillyTavern 1.19.0 上游 205 个公共模块；角色卡、世界书、预设、正则、宏、斜杠命令与快捷回复全链路打通 |
| 🧩 **第三方扩展宿主** | 支持本地 ZIP / Git URL 安装扩展，原生暴露 `/scripts/extensions/third-party/` 端点，内置 MVU 与离线前端依赖库 |
| ⚙️ **双引擎伴生服务** | 本地 `127.0.0.1:18181` 零配置内置 JVM 伴生服务开箱即用，安装 PRoot Node.js v20.18.1 后无缝切换完整后端守护进程 |
| 🖼️ **长对话 HTML 面板池** | 五类内容自动分级、活跃 WebView 池 LRU 淘汰、历史楼层快照冻结与点击唤醒、内层手势协调与崩溃自动重建 |
| 🤖 **多模型与多协议** | 原生支持 OpenAI（Chat Completions / Responses）、Claude、Gemini（含 Vertex AI / Interactions）、Grok、ChatGPT 登录与多 Key 轮询 |
| 🧠 **记忆与知识库** | 基于文件的记忆系统、主动记忆提醒（可自定义提醒提示词）、后台定时记忆整合与本地知识库（PDF / DOCX / XLSX） |
| 🛠️ **Agent 与本地算力** | 支持 MCP 与 Skills 技能系统，内置 Python 3.12（Chaquopy）、PRoot Linux 终端、JS 沙箱与 19+ 联网搜索引擎 |
| 🔊 **语音、群聊与表现力** | 多角色群聊发言调度、角色表情立绘（Expressions）、酒馆音频桥、语音对话模式（多厂商 ASR / TTS）与媒体生成 |
| 🖥️ **多端协作与同步** | 内置局域网 Web UI（支持 NSD 自动发现），支持 WebDAV / S3（AWS SigV4）云同步与 Chatbox / CherryStudio 导入 |
| 🛡️ **隐私与工程质量** | 移除 Firebase / Crashlytics 遥测，主动拦截 SPreset `LogUpload` 外发；仓库内置 **175 个单元测试文件**保障核心回归 |

---

## 🚀 快速开始

1. 前往 [Releases](https://github.com/yufanx79-gif/rikkaST/releases) 下载最新的 `arm64-v8a` 安装包（要求 Android 8.0 / API 26 及以上）。
2. 打开应用，在 **设置 → 服务商** 中配置模型 API 与默认模型，即可直接开始对话。
3. 迁移已有酒馆或 RikkaHub 数据：
   - **导入角色卡**：在助手页一键导入 `.png` / `.json` / `.charx` / `.byaf`（按扩展名优先识别，避免系统文件管理器 MIME 误判）。
   - **导入酒馆整包**：直接导入 SillyTavern 数据目录或备份 ZIP，自动识别 `characters/`、`worlds/`、`OpenAI Settings/`、`QuickReplies/` 与 `settings.json` 中的全局正则。
   - **导入 RikkaHub 备份**：支持直接恢复 RikkaHub 备份包（SSH 远程工作区除外），也可从 Chatbox / CherryStudio 导入配置。

---

## 🍺 SillyTavern 兼容（对齐上游 1.19.0）

rikkaST 内置 `st-runtime` 运行时并对齐 SillyTavern 1.19.0 核心语义，尽量还原桌面端酒馆的真实行为，而不是简单拼接系统提示词。

| 资产 / 机制 | 源码已实现的支持情况 |
| :--- | :--- |
| **角色卡** | 支持 `.png` / `.json` / `.charx` / `.byaf` 导入与编辑，完整保留角色设定、内嵌世界书与扩展字段 |
| **世界书（Lorebook）** | 常量/关键词触发、递归扫描、粘性（Sticky）/ 冷却（Cooldown）/ 延迟（Delay）、装饰器（`@@activate` / `@@dont_activate` 等）、Outlet 出口注入与本地语义向量触发 |
| **预设与 SPreset** | 支持 Chat Completion 预设与 SPreset 扩展（`ChatSquash`、`RegexBinding`、`MacroNest` 等），QuickJS 安全降级执行，显式拦截 `LogUpload` 第三方调试上传 |
| **宏与正则脚本** | 独立宏引擎（变量、随机、时间、状态、嵌套宏）；多来源正则脚本（全局 / 角色卡 / 预设）自动去重与分阶段处理 |
| **斜杠命令与快捷回复** | 完整斜杠命令解析与执行器（支持闭包与变量作用域），兼容 Quick Replies 导入与执行 |
| **上下文与表现力** | Author's Note、Instruct Mode、Persona、群聊多角色发言调度、TikToken 本地精准计数（`cl100k_base` / `o200k_base`）、角色表情立绘与酒馆音频桥 |

<details>
<summary><b>📖 展开查看：第三方扩展、双引擎伴生服务与 HTML 面板池实现细节</b></summary>

### 第三方扩展宿主

- **标准资源路由**：支持从本地 ZIP（带 64 MB 包体与 256 MB 解压上限防 zip 炸弹校验及路径穿越过滤）或 Git URL 安装扩展，通过资产拦截器把 `/scripts/extensions/third-party/<folder>/` 以同源 HTTPS 方式直接暴露给 WebView 运行时。
- **离线基础库**：内置 MVU bundle，以及 EJS、KaTeX、Mermaid、jQuery、Lodash、Showdown、DOMPurify、Zod、YAML 等常用依赖的本地拦截与离线资源，减少弱网环境加载失败。

### 双引擎本地伴生服务（`127.0.0.1:18181`）

针对需要服务端配合的扩展（如 `littlewhitebox-server`），rikkaST 采用双引擎接力方案：

1. **零配置内置 JVM 伴生服务（`BuiltinNodeCompanionServer`）**：应用启动即用，无需额外下载，原生承接 `/health`、上下文隔离桥接、`/api/backends/chat-completions/{status,generate}` 与插件路由。
2. **PRoot Linux + Node.js v20.18.1 守护进程（`NodeRuntimeManager`）**：用户按需安装共享 Linux Rootfs 与 Node.js 后，自动接管 `127.0.0.1:18181` 运行 `server-bootstrap.cjs`，提供 Express 风格路由加载服务端插件。

服务强制仅绑定 `127.0.0.1` 回环地址、启用随机 Token 鉴权（Bearer / X-CSRF-Token）、限制单次请求体上限并自动脱敏日志中的 API Key 与 Token。

### 长对话 HTML / JS 面板引擎（`MessageHtmlBlock.kt`）

为避免带状态栏、交互卡片或动效的角色卡在手机长对话中掉帧、抢手势或引发 WebView 崩溃，渲染管线内置了完整调度机制：

- **五类面板自动分级**：静态判定 `STATIC`（静态美化）、`ANIMATED`（动态展示）、`LIGHT`（轻交互）、`SEND`（宿主发送桥）与 `BIG`（复杂内层滚动/大面板）。
- **活跃池与优先级淘汰**：严格限制同时存活的面板 WebView 数量（默认 2–3 个，可调硬上限 16 个），按楼层优先级执行 LRU 淘汰与销毁。
- **快照冻结与点击唤醒**：历史楼层捕获快照后释放 WebView，点击时按需重新挂载。
- **手势与防崩保护**：JS 侧上报内层纵向滚动区坐标与外层列表协调滚动；超长面板自动降级软件层避免纹理溢出，渲染进程崩溃后支持自动重建（上限 5 次防死循环）。

</details>

---

## 🤖 原生 AI 客户端能力

除了酒馆兼容层，rikkaST 完整保留并持续增强了原生多模型客户端体验：

- **多供应商与协议**：OpenAI（Chat Completions & Responses API）、Anthropic Claude、Google Gemini（含 Vertex AI / Interactions API）、xAI Grok、ChatGPT 账号登录；支持多 API Key 轮询与配额查询。
- **记忆与知识库**：基于文件的记忆管理、主动记忆提醒（支持自定义提醒词）、后台定时记忆整合；本地知识库支持 PDF（MuPDF / PDFBox）、DOCX、XLSX 等文档解析。
- **Agent 工具箱与本地环境**：
  - 支持 MCP（Model Context Protocol）与 Skills 技能系统。
  - 内置 **Python 3.12**（Chaquopy，预装 `requests`、`beautifulsoup4`、`pypdf`、`openpyxl`、`markdownify`、`tabulate` 等）。
  - 内置 **PRoot Linux 工作区与 PTY 终端**，以及文件读写、JavaScript 沙箱、计算器、日历、剪贴板、图表展示等本地工具。
  - 集成 19+ 联网搜索与抓取引擎（Bing、Brave、Tavily、Exa、Perplexity、Firecrawl、Jina、SearXNG、Serper、Bocha、Metaso、Zhipu 等）。
- **语音、媒体与多端同步**：
  - 语音模式支持多家 ASR（OpenAI Realtime、DashScope、Volcengine、Step、MiMo）与 TTS（ElevenLabs、Fish Audio、OpenAI、Gemini、MiniMax、Qwen、Volcengine、Groq、xAI、系统 TTS 等）。
  - 支持对话分支与上下文检查点（Checkpoint）、消息队列、图片与视频生成。
  - 内置 **局域网 Web UI**（Ktor + NSD 自动发现），支持 **WebDAV** 与 **S3（AWS SigV4）** 云同步。
- **外观与多语言**：内置 Minimal、Claude、Ocean、Sakura、Spring、Autumn、Black 等主题预设与自定义 JSON 主题、毛玻璃消息气泡；支持简体中文、繁体中文、English、日本語、한국어、Русский、العربية。

---

## 🛡️ 隐私与工程质量

- **无内置遥测**：构建配置已移除 Firebase 与 Crashlytics 插件，不向官方或第三方上报崩溃与行为日志。
- **预设与扩展防外发**：导入 SPreset 时显式跳过 `LogUpload` 遥测上传；扩展压缩包强制体积与路径安全校验；本地伴生服务仅监听 `127.0.0.1` 并做日志凭据脱敏。
- **175 个单元测试文件**：覆盖真实角色卡/预设导入回归、世界书装饰器与向量回退链、宏/正则/斜杠命令契约、事件总线与宿主桥接、HTML 面板池优先级、Compose 性能守卫、数据库迁移与备份路径修复。

---

## 🛠️ 开发与构建

> [!NOTE]
> 构建需要本机安装 **JDK 21**、**Android SDK**（`compileSdk 37` / `minorApiLevel 2`，`minSdk 26`）与 **Python 3.12**（供 Chaquopy 打包）。当前工具链版本为 Gradle 9.6.0、AGP 9.4.0、Kotlin 2.4.21。

<details>
<summary><b>📦 展开查看：多模块目录结构与技术栈</b></summary>

| 模块 / 目录 | 说明 |
| :--- | :--- |
| `:app` | 主应用、Jetpack Compose UI、Room / DataStore 持久化、酒馆兼容管线（`data/st/`、`data/ai/transformers/`）、HTML 面板池与局域网 Web 服务 |
| `app/src/main/assets/st-runtime` | SillyTavern 1.19.0 兼容 JS 运行时、Shims、MVU bundle、离线依赖库与 `node-host` 插件引导脚本 |
| `:ai` | 多模型 Provider 抽象、SSE 流式解码、Agent Loop、Key 轮询与配额查询 |
| `:workspace` | PRoot Linux Rootfs 安装与补丁、PTY 终端原生层（C++）、共享 Node.js 守护进程管理 |
| `:search` / `:speech` / `:document` | 联网搜索、ASR/TTS 语音引擎与 PDF/Office 本地文档解析 |
| `:mediagen` / `:videogen` / `:oauth` | 图像/视频生成与账号授权模块 |
| `:highlight` / `:material3` / `:ui` / `:common` / `:web` | 代码高亮、Material 3 组件封装、通用基础库与 Web 静态资源 |

核心技术栈：Kotlin · Jetpack Compose · Material 3 Expressive · Navigation 3 · Koin · Room · DataStore · OkHttp · Ktor · Coil 3 · Chaquopy · QuickJS

</details>

**本地构建命令**：

```bash
git clone https://github.com/yufanx79-gif/rikkaST.git
cd rikkaST
cp local.properties.example local.properties
# 在 local.properties 中填写 sdk.dir 与 chaquopy.python（Release 签名配置按需填写，切勿提交）

# 运行单元测试
./gradlew :app:testDebugUnitTest --console=plain

# 构建 Debug APK
./gradlew :app:assembleDebug --console=plain

# 构建 Release APK（需配置签名）
./gradlew :app:assembleRelease --console=plain
```

Windows 环境将 `./gradlew` 替换为 `gradlew.bat` 即可。

---

## 🙏 致谢与开源协议

| 项目 | 关系 | 许可证 |
| :--- | :--- | :--- |
| [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) | 上游项目，提供原生 Android 多模型客户端架构与 UI 基础 | AGPL-3.0 |
| [SillyTavern/SillyTavern](https://github.com/SillyTavern/SillyTavern) | 酒馆生态兼容目标（对齐 1.19.0 核心语义与公共模块） | AGPL-3.0 |
| MVU / Chaquopy / PRoot / Termux 等开源组件 | 运行时、沙箱与工具链生态支持，完整声明见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) | 见声明文件 |

本项目基于 **[GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE)** 开源。

---

## ⭐ Star History

如果 rikkaST 对你有帮助，欢迎点一颗 Star 支持一下 ⭐

<a href="https://www.star-history.com/#yufanx79-gif/rikkaST&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=yufanx79-gif/rikkaST&type=Date" />
  </picture>
</a>
