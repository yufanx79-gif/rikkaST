# 已知限制与风险说明（KNOWN_LIMITATIONS）

## 1. 本轮构建与测试验证边界
- **未在 AI 沙箱内执行 Gradle 构建**：遵照用户明确指令，当前环境未运行 `gradlew` 编译或 Android 单元测试。虽然全部 925 个 Kotlin 源码/构建脚本均通过了词法状态机、括号配平、导入与函数签名核对，且 50 个 JS 运行时文件全部通过 `node --check`，但最终字节码编译与运行时行为仍需用户在 Windows 本机执行 `.\gradlew.bat testDebugUnitTest assembleDebug` 验收。

## 2. SillyTavern 插件生态兼容性边界（不做“100% 任意插件兼容”虚假承诺）
1. **桌面端顶层 DOM 强耦合扩展（Partial Compatibility）**：
   - RikkaST 的主聊天界面采用 Android 原生 Jetpack Compose 渲染，而后台 `TavernRuntimeManager` 运行在独立 Headless WebView 中，楼层消息卡片运行在按需挂载的 `MessageHtmlBlock` WebView 中。
   - 因此，遵循 TavernHelper API、`SillyTavern.getContext()`、STscript、世界书、正则与楼层 HTML/JS 卡片规范的角色卡与脚本插件可正常工作；但直接通过 `window.parent.document.querySelector('#sheld')` 修改桌面端顶栏、侧边栏或输入框 DOM 的纯桌面 UI 插件，仅在后台桩节点上执行而不改变原生 Compose 布局。
2. **SillyTavern Node.js 服务端插件（Server Plugins — Unsupported）**：
   - 依赖 SillyTavern 桌面版 Node.js Express 后端（如本地文件系统自由读写、Python 子进程调用、自定义 `/api/plugins/*` 后端路由）的插件在 Android 端不适用。
3. **酒馆官方 TTS / 图像生成扩展 API（Excluded — Use Native RikkaHub Features）**：
   - 未承诺兼容酒馆 `/sd`、`/tts` 扩展后端路由；用户应使用 RikkaHub 原生提供的多供应商 TTS 与图像生成模型功能。

## 3. WebView 资源与多面板并发说明
- **楼层 WebView 并发数量**：聊天列表对处于可见区域的复杂 HTML/JS 消息块按需挂载 WebView，滑出屏幕时自动解绑释放。若单次对话中同时在首屏展示过多包含重型 Canvas/WebGL 循环的楼层卡片，低内存设备仍可能触发系统 WebView 渲染器回收（已内置 `onRenderProcessGone` 崩溃捕获与“点击重新加载面板”恢复按钮）。
