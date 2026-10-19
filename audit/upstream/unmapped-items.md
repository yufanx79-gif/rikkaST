# SillyTavern 1.18.0 → 1.19.0 未直接映射/不适用项说明 (unmapped-items)

以下上游 `1.18.0 → 1.19.0` 变更经源码核对后，判定在 Android 原生宿主架构（`rikkaST`）中属于桌面 Web UI 样式、多语言静态翻译包或桌面专用后端服务，无需直接照搬：

1. **桌面端全量 CSS 与 DOM 布局调整**（`public/css/st-tailwind.css`, `public/css/style.css`, `public/index.html`）：
   - **依据**：`rikkaST` 主聊天列表、顶栏、底栏与设置页采用 Kotlin + Jetpack Compose 原生渲染（`ChatPage.kt`, `ChatMessage.kt`, `TavernExtensionsPage.kt`），仅在扩展设置页与 HTML 消息块中使用 WebView 壳层。
   - **处理**：保留 `rikkaST` Compose UI，仅在 `runtime.html` / `runtime.js` 隐藏 DOM 锚点中补齐扩展挂载所需的容器节点。
2. **多国语言静态词典增量**（`public/locales/*.json` 共 20+ 语言文件）：
   - **依据**：`rikkaST` 使用 Android 资源文件 (`res/values*/strings.xml`) 管理原生界面多语言，`st-compat/i18n.js` 已提供 `t()` / `translate()` 透传与词典注入能力。
3. **桌面服务端 HuggingFace Transformers / 本地 TTS 路由**（`src/endpoints/classify.js`, `src/endpoints/speech.js`, `src/vectors/makers/transformers.js`）：
   - **依据**：`rikkaST` 已在原生模块 `speech/`（含 ElevenLabs、FishAudio、MiMo、Step、Volcengine 等）和 `ai/` 中实现 Android 原生语音与向量能力，不运行整套桌面 SillyTavern Express 后端。
4. **桌面服务端备份文件复制脚本**（`src/server-CopyFile.js`）：
   - **依据**：`rikkaST` 使用 `BackupManager.kt`、Room 数据库及 WebDAV/S3 同步机制管理备份，不适用桌面文件目录拷贝脚本。
