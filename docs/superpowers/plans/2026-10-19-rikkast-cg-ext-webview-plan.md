# rikkaST CG 渲染修复、五扩展设置页直达适配、WebView 全视觉保活与编译同步实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task after user approval. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 彻底解决内嵌 CG 偶现不显示/固定显示同一张图片的问题、修复第三方扩展设置页误渲染聊天小说正文且无法打开子面板/测试台的问题、同步全部 10 项编译修复，并用“WebView 全视觉保活 + 静默期定时器节流 + 无感位图缓存”替代破坏观感的“唤醒面板”休眠卡片。

**Architecture:**
1. **CG 渲染与多图精准映射链路**：在 `ThinkTagTransformer` / `ChatMessageCot` 中解除原生 `Reasoning` 与正文 `<thinking>` 标签的互斥跳过逻辑，确保双轨输出模型（如 Gemini Thinking）的正文思维链被彻底剥离；在 `runtime.js` 与 `MessageHtmlBlock.kt` 中修复 `varsWait` 异步重挂载覆盖旧 HTML、`<img referrerpolicy="no-referrer">` 首帧注入、以及外部图床与本地 `charx_assets` 回退优先级，彻底杜绝多张 CG 固定显示第一张或某一张的问题。
2. **第三方扩展独立设置宿主（Extension Settings Host）**：在 `extension-settings.html` 与 `st-compat/chat-dom.js` 中彻底隔离并隐藏 `#chat` 聊天 DOM；在 `TavernRuntimeManager.kt` 与 `runtime.js` 中打通目录别名匹配与 `settings.html` 确定性挂载管线；对齐 `TavernExtensionSettingsPage.kt` 与 `runtime.js` 的动作指令协议，为 `ST-Prompt-Template` 提供可交互的「EJS 模板实时测试台」子视图，并确保 5 个扩展点进去即直接呈现完整设置面板。
3. **WebView 全视觉保活渲染策略**：移除 `MessageHtmlBlock.kt` 中的 `PanelHibernatedPlaceholderCard`（“历史交互面板已自动休眠 / 唤醒面板”），依托 Compose `LazyColumn` 原生视口生命周期保持屏幕内所有 WebView 100% 实时视觉渲染；在面板完成首屏渲染与变量注入后自动静默后台轮询定时器（冻结无意义 CPU 空转而非卸载页面），并配合渲染进程崩溃后的同尺寸无感自愈重绘，兼顾满血视觉体验与内存稳定性。

**Tech Stack:** Kotlin (Jetpack Compose, Coroutines, Android WebView, WebViewAssetLoader), JavaScript (ES Modules, jQuery / SillyTavern 1.19.0 Compat Runtime, EJS).

---

## 零、Node.js 运行时诊断日志审查结论（截图 2）

你上传的第 2 张截图（`共享 Node.js 运行时诊断`）检查结果**完全正常，可以直接通过**：
- **状态**：`RUNNING_HEALTHY`（监听 `127.0.0.1:18181`，`/health` 健康检查通过，崩溃恢复次数 `0`）。
- **服务模式**：内置零配置极速伴生引擎（`v20.18.1-compat / jvm-builtin-companion`），已成功挂载 `littlewhitebox-server`（`LittleWhiteBox Companion Server v3.0.0`）。
- **结论**：Node 伴生服务链路已经在真机上稳定运行，无需额外折腾 Rootfs 即可支撑扩展后端 API 请求。

---

## 一、问题根因深度排查报告

### 1. 问题一：嵌入助手消息的 CG 偶现无法显示 & 有时固定显示第一张/某一张图片

#### 1.1 为什么会偶现无法显示（只看到过一次，切后台再进又正常了）？
1. **双轨思维链模型（如截图 2 顶部的 `build-抗截断-gemini...`）导致 `<thinking>` 偶发漏入正文**：
   - 在 `ThinkTagTransformer.kt:43` 和 `ChatMessageCot.kt:51,81` 中，旧代码判断了 `if (message.hasPart<UIMessagePart.Reasoning>()) return message`。
   - 当使用 Gemini 等原生推理模型配合酒馆预设时，模型**既会返回原生 `UIMessagePart.Reasoning` 块，又会按预设要求在正文 `UIMessagePart.Text` 开头输出 `<thinking>...</thinking>`**。因为 `hasExplicitReasoning` 为 `true`，正文的 `<thinking>` 被跳过了剥离，直接挡在了 CG 标签前面，破坏了角色卡正则对 CG 块的匹配。
2. **流式输出刚结束瞬间与 `StRegexTextCache` / `varsWait` 的时序竞态**：
   - 流式输出结束瞬间，若变量宏或 MVU 状态尚未同步完成，`StRegexedText` 首次计算的 HTML 被缓存；当你切到后台再切回前台时，Compose 触发重绘并重新求值，此时变量和闭合标签已就绪，所以就恢复正常了。

#### 1.2 为什么显示的 CG 图片有时感觉固定变成第一张或某一张？
排查代码后发现了 **3 个叠加的精确 Bug**：
1. **`runtime.js` 中 `varsWait` 异步重挂载把后续更新的 HTML 覆盖回了“第一次”的旧 HTML（核心元凶）**：
   - 在 `app/src/main/assets/st-runtime/runtime.js:3843-3855` 中：
     ```javascript
     var lastPanelHtml = '';
     function scheduleVarsRefresh(html) {
       lastPanelHtml = String(html || '');
       if (varsReady || varsWaitTimer) return; // <-- BUG：如果定时器已在运行，后续逻辑直接 return，且定时器触发后固定重挂载最初的状态！
     ```
   - 更严重的是，在 `MessageHtmlBlock.kt:865` 中，`key(messageKey, localCrashCount)` **没有把 `contentHash` 放进 Compose 的 `key` 或 `update` 强刷条件中**，且 `PanelMountState.decideUpdateAction(html)` 对不含 `<script>` 的纯 CG 卡片返回 `REMOUNT_IN_PLACE`。当同一楼层存在多个 HTML 片段或切换分支/刷新图片时，WebView 复用了旧实例或被 `varsWaitTimer` 延迟回调覆盖回了第一张 CG 的 HTML！
2. **同一消息内多张 CG 共享全局变量 / DOM 选择器冲突**：
   - 许多角色卡的 CG 正则脚本使用 `document.querySelector('.cg-img')` 或全局变量（如 `window.currentCg`）来设置图片地址，或者在同一个 HTML 块里渲染「当前 CG + 完整图库列表（如 `▼ 查看完整图库`）」。
   - 当 `MessageHtmlBlock.kt` 的 `extractMessageHtmlBlocks` 将同一条消息里的多段 HTML 合并或拆分时，如果多个 CG 块被合并进同一个 WebView，`document.querySelector('img')` 永远只会命中**第一张 `<img>`**！
3. **`runtime.js` 图片加载失败回退链把外部图床 URL 覆盖丢失，且 `SpriteRepository` 模糊回退命中了固定立绘**：
   - 在 `runtime.js:3780-3832` 的 `window.addEventListener('error')` 中：
     - 首屏挂载 `root.innerHTML = normalizeHtml(html)` 时，**没有预先给 `<img` 标签注入 `referrerpolicy="no-referrer"`**，导致防盗链图床（如 GitGud / Catbox / Discord 等）第一次请求失败。
     - 第一次失败进入 `error` 回调时，代码把 `candidates[0]` 设为了本地查找 `https://appassets.androidplatform.net/charx_assets/lookup/${baseName}`，并直接覆盖了 `img.src = candidates[0]`！
     - 一旦角色卡使用的是**纯外部图床**（本地 `charx_assets` 里只有默认封面或第一张立绘），或者当 `/charx_assets/lookup/` 404 触发第二次 `error` 时，此时 `curSrc` 已经变成了 `appassets.androidplatform.net`，导致 `if (!curSrc.includes('appassets.androidplatform.net'))` 判断为 `false`，**彻底丢失了原始外部图床 URL**，再也无法重试 `no-referrer` 或 `.webp`/`.png`/`.jpg` 后缀！
     - 同时在 `SpriteRepository.kt:132-135` 的 `resolveAnySpriteOrAssetFile` 中，当精确匹配不到文件名时，若回退匹配过宽或命中同名默认前缀，就会返回固定的一张本地图片。

---

### 2. 问题二：第三方扩展设置页显示小说正文 & 按钮点击无反应（截图 1）

#### 2.1 为什么点进「Prompt Template/提示词模板」设置页，下面黑框里显示的是小说聊天正文？
- 在 `app/src/main/assets/st-runtime/extension-settings.html:36` 中：
  `<div id="rikka-st-compat-dom">` 内部写了一个 `<div id="chat"></div>`，但**没有设置 `style="display:none"`**！
- 当扩展设置页启动并执行 `runtime.js:4154` 的 `refreshAll()` 时，它会调用 `chatDom.renderFloors(state.chat)`（`st-compat/chat-dom.js:126`）。
- 而在 `chat-dom.js:24` 中，只有当 `#chat` 不存在、由脚本动态创建时才会加 `el.style.display = 'none'`；因为 `extension-settings.html` 里已经存在 `<div id="chat"></div>`，它保持了默认的 `display: block`，直接把当前聊天记录（`九月下旬的午后，沿坡而建的校舍总能照进过于透亮的光……`）原封不动地渲染在了扩展设置页里！
- 更糟糕的是，`runtime.js:4201` 的 `normalizeExtensionSettingsDom()` 统计整个 ` settingsRoot` 的子元素数量时，把 `#chat` 里的几十个小说段落节点也算了进去（`totalDescendants > 12`），误以为扩展设置面板已经成功渲染，从而隐藏了状态提示！

#### 2.2 为什么扩展真正的设置面板没有显示出来，且点「展开全部子面板」「打开 EJS 模板测试台」没反应？
1. **目录别名过滤导致扩展未加载**：
   - `all-needs.zip` 解压出的扩展目录名带有版本号或分支后缀（如 `ST-Prompt-Template-1.16`、`JS-Slash-Runner-main`、`st-memory-enhancement-2.2.0`、`LittleWhiteBox-3.1.7`、`ST-iPhonie-main`），或者用户安装的 `v1.17.9` 目录名与 `folder` 参数存在大小写/后缀差异。
   - `runtime.js:2850` 在设置页模式下用 `String(e.folder || '') === settingsFolderForLoad` 做严格相等过滤，且当 `ChatPage` 退居后台转场时 `thirdPartyExtensionsSupplier` 可能瞬时返回空列表 `[]`。
2. **jQuery 异步 `$(async () => { await init(); })` 挂载时机滞后**：
   - `ST-Prompt-Template`、`JS-Slash-Runner`、`st-memory-enhancement` 等插件把 `renderExtensionTemplateAsync` 和 `$('#extensions_settings').append(...)` 放在 `jQuery(async () => { ... })` 回调里。
   - `runtime.js` 的 `loadThirdPartyExtensions()` 只 `await import(src)` 就立即执行了 `normalizeExtensionSettingsDom()`，此时插件的 `settings.html` 异步网络请求还没完成，面板尚未插入 DOM！
3. **原生按钮与 `runtime.js` 的 Action 名称不一致 + 缺少独立「EJS 模板测试台」子视图**：
   - `TavernExtensionSettingsPage.kt` 发送的动作名是：
     - `expand_all`、`open_stpt_modal`、`open_jsr_scripts`、`open_memory_table`、`open_xbx_panel`、`open_iphonie_app`
   - 而 `runtime.js:4330` (`window.__rikkaExtSettingsAction`) 只处理了：
     - `expand_all_drawers`、`open_iphonie`、`switch_memory_tab`、`reload_settings`！
   - 两边指令名完全对不上，所以点击「展开全部子面板」和「打开 EJS 模板测试台」毫无反应！而且 `ST-Prompt-Template` 原始 `settings.html` 只有开关和滑块，没有可视化的“模板测试台”二级界面，需要我们在设置宿主中为其内建可交互的 **「EJS 模板测试台」二级面板/弹窗**。

---

### 3. 问题四：如何不暂停/不休眠外部 WebView，同时保证流畅度与不崩渲染进程？

- **痛点**：之前为了防多 WebView 崩溃，引入了 `ActivePanelWebViewPool`（最多保留 4 个活跃 WebView，其余替换成 `历史交互面板已自动休眠（防多 WebView 卡顿） [唤醒面板]` 的灰底占位卡片）。但这导致上下滑动看剧场/状态栏/CG 时，历史楼层全变成了冷冰冰的文字卡片，严重破坏沉浸感。
- **全新非休眠方案（全视觉保活 + 定时器智能静默 + 渲染自愈）**：
  1. **彻底移除 `PanelHibernatedPlaceholderCard`（“唤醒面板”）**：任何楼层的 HTML 面板、状态栏、立绘 CG 永远直接渲染真实视觉画面，绝不降级成占位文字卡。
  2. **依托 Compose `LazyColumn` 自然视口裁剪 + WebView 内部轮询定时器自动静默（Timer Quiescence）**：
     - 之前多 WebView 卡顿/崩溃的真正原因，不是因为显示了静态/轻交互的 HTML 画面，而是因为每个 WebView 内部的 `runtime.js` 都在无限运行 `1500ms` 的 `setInterval(report, 1500)` 高度探测、未清理的 `varsWaitTimer`、以及角色卡脚本里的后台轮询！
     - 改造后：当某个楼层的 WebView 完成挂载且高度稳定（例如挂载 2.5 秒后且高度连续 2 次无变化），`runtime.js` 自动停止该面板的后台轮询定时器（转为仅在收到用户点击 `click`/`input` 或宿主显式 `updateVars` 事件时按需触发一次更新）。这样画面 **100% 保持实时渲染与可点击交互**，但静止时的后台 CPU/IPC 消耗降为 **0**！
  3. **离屏位图快照无缝兜底（Zero-Jank Bitmap Snapshot Cache）**：
     - 当某个 WebView 已完成渲染时，在内存中缓存一份 1:1 像素级渲染快照（`Bitmap`）。万一极端情况下系统底层回收了渲染进程或正在高频快速滑动，UI 直接显示 1:1 真实渲染画面（肉眼与活体 WebView 完全一致），并在触碰瞬间无缝切回活体交互，彻底告别“唤醒面板”占位卡。

---

## 二、分步实施计划（Implementation Plan）

### Task 1: 同步全部 10 项编译修复与会话身份接线

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/st/TavernRuntimeManager.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/chat/StRegexedText.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` (或 `QuickMessage` 定义所在文件)
- Modify: `app/src/main/java/me/rerere/rikkahub/st/TavernExtensions.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/st/TavernNodeServiceManager.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt`

- [ ] **Step 1: 逐项核对并合入你列出的 10 项编译修复**
  1. `TavernRuntimeManager.kt`：清理全部 6 处 Kotlin 字符串/正则中的非法转义 `\-`（改为 `-` 或合规范转义）。
  2. `TavernRuntimeManager.kt`：补齐 `var activeAssistantSupplier: () -> Assistant?` 与 `val activeAssistant: Assistant? get() = activeAssistantSupplier()`，并在 `ChatPage.kt` 中注入当前激活的 `Assistant`。
  3. `TavernRuntimeManager.kt`：补齐 `fun reloadThirdPartyExtensions()` 方法，支持刷新已安装第三方扩展并通知 WebView 重载。
  4. `StRegexedText.kt`：在 `TavernVarMacroStoreAdapter` 中补齐 `getAtKey(scope, name, keyPath)` 与 `setAtKey(scope, name, keyPath, value)` 实现。
  5. `QuickMessage` 模型：补齐 `val preventAutoExecute: Boolean = true` 字段及序列化默认值。
  6. `TavernExtensionInfo`（`TavernExtensions.kt`）：补齐 `val requiresNode: Boolean` 派生属性（识别 `hasNodeServer` 或 `littlewhitebox` 等需伴生服务的扩展）。
  7. `TavernNodeServiceManager.kt`：补齐 `NodeServiceStatus` 数据类与 `statusFlow: StateFlow<NodeServiceStatus>`，在 `ensureStarted`、`stop`、`diagnose`、健康检查与异常恢复全链路同步更新状态流。
  8. `ChatPage.kt`：将 `val chatListState = rememberLazyListState()` 声明前移至所有引用点之前。
  9. `ChatPage.kt` / `ChatVM.kt`：补齐 `import me.rerere.rikkahub.data.model.findModelById` 导入。
  10. `TavernRuntimeManager.kt` / `TavernNodeServiceManager.kt` / `ChatPage.kt`：完整接通 `activeAssistantId` 会话身份（从 `activeAssistant?.id` / `ChatVM` 注入到 `TavernRuntimeManager` 与 `TavernNodeServiceManager`）。

- [ ] **Step 2: 静态扫描验证**
  运行脚本扫描全仓 `.kt` 文件的非法正则转义、未解析符号与括号平衡，确保 0 编译隐患。

---

### Task 2: 修复内嵌助手消息 CG 偶现不显示 & 固定显示第一张/某一张图片

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformer.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/chat/ChatMessageCot.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/chat/MessageHtmlBlock.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/chat/StRegexedText.kt`
- Modify: `app/src/main/assets/st-runtime/runtime.js`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/repository/SpriteRepository.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/st/TavernRuntimeManager.kt`

- [ ] **Step 1: 修复双轨输出模型 `<thinking>` 偶发漏入正文导致 CG 正则失效**
  - 在 `ThinkTagTransformer.kt` 与 `ChatMessageCot.kt` 中，即使消息已经包含原生 `UIMessagePart.Reasoning`，仍然扫描并剥离 `UIMessagePart.Text` 中的 `<think>` / `<thinking>` / `<thought>` 块（将其合并入 `Reasoning` 部分或从正文中移除），并在流式输出期间对未闭合的 `<thinking>` 前缀进行实时隐藏，防止 `<thinking>` 污染 CG 正则输入。

- [ ] **Step 2: 修复 `runtime.js` 中 `varsWait` 异步重挂载覆盖最新 CG HTML 的 Bug**
  - 在 `runtime.js` (`installPanelMode`) 中：
    - 确保每次调用 `__rikkaPanelMount(html, reason)` 时始终更新 `lastPanelHtml = String(html || '')`；
    - 当 `varsWaitTimer` 触发重挂载时，对比当前已挂载的 HTML 与最新 `lastPanelHtml`，且**绝不用旧快照覆盖已经更新过的 DOM**；
    - 在 `MessageHtmlBlock.kt` 中，当纯 HTML 块（如不同 CG 节点）的 `contentHash` 发生变化时，强制触发 `__rikkaPanelMount(html, 'content-changed')` 刷新 DOM，防止 WebView 停留在第一张图片的旧 DOM 上。

- [ ] **Step 3: 隔离同一消息内的多张独立 CG 块，防止 DOM/选择器互相覆盖**
  - 检查 `MessageHtmlBlock.kt` 中的 `extractMessageHtmlBlocks` 与 `splitMixedMessagePieces`：当一条消息内存在多个独立的 CG 渲染块时，保持它们各自独立的 `subKey`（`"$messageKey#part-$pieceIdx-html-$blockIdx-${blockHtml.hashCode()}"`），避免多张 CG 混入同一个 DOM 后被 `document.querySelector('img')` 永远锁死在第一张图。

- [ ] **Step 4: 修复外部图床 `referrerpolicy="no-referrer"` 首帧注入与本地/远程图片解析回退顺序**
  - 在 `runtime.js` 的 `normalizeHtml(raw)` 中：在 HTML 写入 `innerHTML` **之前**，自动为所有没有 `referrerpolicy` 属性的 `<img ...>` 标签注入 `referrerpolicy="no-referrer"` 和 `data-rikka-orig-src` 原始地址属性，让防盗链外部图床（GitGud / Catbox 等）在**第一次请求时就成功加载**。
  - 在 `runtime.js` 的 `<img onerror>` 回退链中：
    - 始终从 `img.dataset.rikkaOrigSrc` 读取原始 URL（绝不因中间尝试了 `appassets.androidplatform.net` 而丢失原始外部 URL）；
    - 如果原始 URL 是 `http(s)://` 外部图床，**优先重试外部图床的扩展名变体（`.webp` / `.png` / `.jpg`）与无缓存请求**，仅在外部图床确认不可达时才回退查询本地 `/charx_assets/lookup/${baseName}`；
  - 在 `SpriteRepository.kt` (`resolveAnySpriteOrAssetFile`) 与 `TavernRuntimeManager.kt` (`resolveLocalCharacterAssetFile`) 中：
    - 严格匹配请求的精确文件名（如 `织乃_zcw_2` 必须只匹配 `织乃_zcw_2.*`），**严禁在找不到特定编号 CG 时回退返回角色卡的第一张默认立绘（如 `织乃_1` 或 `default`）**，从而彻底杜绝“找不到某张图或回退时全都变成第一张图”的现象！

---

### Task 3: 深度修复第三方扩展设置页（截图 1：消除小说正文、直达设置面板、打通子面板与测试台）

**Files:**
- Modify: `app/src/main/assets/st-runtime/extension-settings.html`
- Modify: `app/src/main/assets/st-runtime/extension-settings.css`
- Modify: `app/src/main/assets/st-runtime/st-compat/chat-dom.js`
- Modify: `app/src/main/assets/st-runtime/runtime.js`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/TavernExtensionSettingsPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/st/TavernRuntimeManager.kt`

- [ ] **Step 1: 彻底隐藏并隔离扩展设置页中的 `#chat` 聊天正文（解决截图 1 下方出现小说文本的问题）**
  - 在 `extension-settings.html` 中为 `<div id="rikka-st-compat-dom">` 和 `<div id="chat">` 加上强制内联样式 `style="display:none !important; visibility:hidden !important; height:0 !important; overflow:hidden !important;"`。
  - 在 `extension-settings.css` 中增加 `#rikka-st-compat-dom, #chat { display: none !important; }`。
  - 在 `runtime.js` 的 `refreshAll()` 与 `st-compat/chat-dom.js` 的 `renderFloors()` 中：当检测到 `window.__RIKKA_EXT_SETTINGS_MODE__ === true` 时，跳过向 `#chat` 渲染可见楼层卡片，且 `normalizeExtensionSettingsDom()` 仅统计 `#extensions_settings` 与 `#extensions_settings2` 内的有效插件设置节点（彻底排除 `#rikka-st-compat-dom`）。

- [ ] **Step 2: 确保进入插件设置页 100% 立即加载并展开该插件的真实设置面板**
  - **修复目录别名与扩展列表兜底**：
    - 在 `TavernRuntimeManager.kt` 的 `TavernJsBridge.getThirdPartyExtensionsJson()` 中，若内存缓存为空则直接调用 `TavernExtensions.listInstalledExtensions(context)` 实时扫描磁盘，保证设置页 WebView 永远能拿到完整的已安装扩展列表；
    - 在 `runtime.js` 的 `loadThirdPartyExtensions()` 中，使用 `normalizeExtensionFolderAlias(e.folder) === normalizeExtensionFolderAlias(settingsFolderForLoad)` 匹配目标插件（使 `ST-Prompt-Template`、`ST-Prompt-Template-1.16`、`ST-Prompt-Template-1.17.9` 互通）。
  - **等待 jQuery Ready 异步初始化完成 + 确定性模板挂载兜底（Fallback Direct Mount）**：
    - 在 `loadThirdPartyExtensions()` 完成 `await import(src)` 后，自动 flushing jQuery ready 队列并等待异步 `renderExtensionTemplateAsync` 完成；
    - 增加**五大扩展确定性设置面板兜底挂载器（`ensureTargetExtensionSettingsMounted`）**：如果插件脚本因任何原因在 600ms 内未把设置 DOM 挂入 `#extensions_settings`，自动根据插件类型直接拉取并渲染其原生 `settings.html` / 模板并双向绑定 `extension_settings`：
      1. **`ST-Prompt-Template`（提示词模板）**：挂载其 `settings.html`（`#prompt_template_settings`，含启用开关、预加载世界书、正则优先、生成提示词模板、渲染消息模板、自动包裹代码块、美化引擎等全部设置项），双向绑定 `extension_settings.EjsTemplate` 并实时同步到宿主！
      2. **`JS-Slash-Runner`（酒馆助手）**：挂载酒馆助手设置面板（启用开关、沙盒模式、性能模式、日志级别、内置脚本库列表、导入/新建脚本与变量检查器），双向绑定 `extension_settings.tavern_helper`。
      3. **`st-memory-enhancement`（记忆增强表格）**：挂载 `assets/templates/index.html` 与 `appHeaderTableDrawer.html`（合并展示「插件设置」与「记忆表格工作台」，无需额外点击抽屉即可直接查看与编辑表格和设置）。
      4. **`LittleWhiteBox`（小白盒）**：挂载其 `settings.html`（`#littlewhitebox_settings`），默认展开「渲染交互（Story Outline / AI 手机 / 沉浸式剧场 / 角色周报 / 墙纸）」与「循环任务 / 数据互动 / 辅助工具 / 预设思维链」全部四级子模块卡片。
      5. **`ST-iPhonie`（小手机）**：直接挂载并打开 `ui/index.html` 小手机设置与测试主界面（含语音协议配置、倍速、缓存清理及测试拨号）。

- [ ] **Step 3: 全面打通顶部按钮与二级子面板 /「EJS 模板测试台」**
  - 在 `runtime.js` (`window.__rikkaExtSettingsAction`) 中完整支持并对齐 `TavernExtensionSettingsPage.kt` 的所有动作指令：
    - `expand_all` / `expand_all_drawers`：一键展开当前扩展的所有折叠子面板（`.inline-drawer-content`、`details`、`.xbx-subpanel`、`.drawer-content`），并平滑滚动到子面板区域。
    - `open_stpt_modal`（**打开 EJS 模板测试台**）：在页面内打开专门定制的 **「EJS 模板实时测试与变量调试台」二级视图/模态面板**：
      - 支持输入任意 EJS 模板代码（预置常用示例：读取/修改变量 `getvar`/`setvar`、条件分支 `<% if (...) %>`、角色/用户信息 `<%= char %>`）；
      - 实时展示当前会话的局部变量（Local Vars）与全局变量（Global Vars）JSON 快照，支持一键添加测试变量；
      - 点击「立即运行测试」直接调用 `ST-Prompt-Template` 的真实 EJS 引擎（`evalTemplate` / `renderEjsTemplate`）输出渲染结果与耗时！
    - `open_jsr_scripts`（**管理脚本库**）：切换到酒馆助手的「脚本库管理与控制台」子面板，支持查看/启停脚本、导入脚本 JSON 及运行测试 Slash/JS 指令。
    - `open_memory_table`（**打开记忆表格**）：切换到 `st-memory-enhancement` 的表格视图（`#table_drawer_content`），支持直接查看和编辑当前会话的记忆表格行与列。
    - `open_xbx_panel`（**展开功能模块**）：展开小白盒全部四大分类子面板并高亮模块导航栏。
    - `open_iphonie_app`（**打开小手机面板**）：全屏展开 `ST-iPhonie` 手机界面并加载 `ui/index.html`。
  - 同时优化 `TavernExtensionSettingsPage.kt` 顶部原生控制栏：当已在插件设置页内时，让原生操作栏更紧凑，把核心空间留给下方的插件原生设置面板与测试台子界面。

---

### Task 4: 移除外部 WebView 休眠策略，实现「全视觉保活 + 定时器智能静默」

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/components/chat/MessageHtmlBlock.kt`
- Modify: `app/src/main/assets/st-runtime/runtime.js`

- [ ] **Step 1: 移除 `PanelHibernatedPlaceholderCard`（“历史交互面板已自动休眠 / 唤醒面板”）**
  - 在 `MessageHtmlBlock.kt` 中：
    - 移除将超出 4 个以外的历史 WebView 强制设为 `isHibernated = true` 并替换为 `PanelHibernatedPlaceholderCard` 的逻辑；
    - 让 `LazyColumn` 可见视口内的所有消息面板（无论是状态栏、立绘 CG 还是交互剧场）**全部保持真实 WebView 视觉渲染**，滑动浏览时直接看到完整渲染效果，不再出现需要手点的“唤醒面板”占位卡。

- [ ] **Step 2: 在 `runtime.js` 中实现面板渲染完成后的「智能定时器静默（Timer Quiescence）」**
  - 在 `runtime.js` (`installPanelMode`) 中：
    - 面板首次挂载期间正常开启高频高度探测（120ms -> 350ms -> 900ms）与变量等待；
    - 一旦面板挂载满 3 秒且高度稳定，**自动清除常驻的 `setInterval(report, 1500)` 和 `varsWaitTimer`**，进入「视觉保活 + 零后台轮询」状态；
    - 仅当用户在面板内发生交互（`click`、`input`、`change`、`toggle`）或宿主主动推送 `updateVars` / `__rikkaPanelMount` 时，才按需唤醒一次高度测量与重算。
    - 这样既保证了**所有外部 WebView 100% 不暂停、画面与交互随时可用**，又消除了后台 WebView 持续空转导致的卡顿与 `CrRendererMain` 压力。

- [ ] **Step 3: 完善 WebView 硬件渲染与崩溃无感自愈**
  - 保留 `onRenderProcessGone` 拦截保护（万一系统极端内存不足回收了单个 WebView 渲染进程，自动在原位无感重建并重新挂载同一份 HTML，不闪退、不显示报错卡片）。

---

### Task 5: 自动化验证与打包交付

**Files:**
- Modify: `scripts/st119-migration-and-5plugins.test.mjs`
- Create: 新版完整工程源码压缩包

- [ ] **Step 1: 扩展并运行自动化回归测试套件**
  - 在 `scripts/st119-migration-and-5plugins.test.mjs` 中新增针对本次 4 大问题的专项测试：
    1. 验证双轨 `<thinking>` 剥离、CG `<img referrerpolicy="no-referrer">` 预注入与外部图床回退不覆盖原始 URL、多 CG 块独立挂载不锁死第一张图；
    2. 验证 `extension-settings.html` 中 `#chat` 被强制隐藏，5 个扩展在设置页模式下均能挂载各自的设置 DOM 且不会混入聊天小说文本；
    3. 验证 `window.__rikkaExtSettingsAction` 对 `expand_all`、`open_stpt_modal`（EJS 模板测试台）、`open_jsr_scripts`、`open_memory_table`、`open_xbx_panel`、`open_iphonie_app` 全部响应成功；
    4. 验证 10 项编译修复在 Kotlin 源码中的完整性；
    5. 验证 `MessageHtmlBlock.kt` 已移除休眠占位卡且 `runtime.js` 实现了面板定时器静默。
- [ ] **Step 2: 打包完整修复后的源码归档并通过 `show_file` 交付**

---

### 请确认是否通过该计划
以上计划已保存在工作区 `docs/superpowers/plans/2026-10-19-rikkast-cg-ext-webview-plan.md`。请看一下这个排查结论和改动方案是否符合你的预期？如果没问题，回复我一声，我立刻按计划逐项修改代码并打包发你！
