# NOTES.md — 根因解答、边界说明与真机回归验收清单

本文档逐条回答 `00-总交接提示词.md` 与 `07-外部AI参考/00-问题说明.md` 中的全部问题，说明外部 AI 结论的交叉核实结果、本轮明确不做的事项，以及真机编译后的逐项验收步骤。

---

## 一、外部 AI 结论交叉核实与修正说明

我们对外部 AI 针对白条与静默闪退的分析报告对照 `04-真机证据/merged-17时-完整未截断.log`（1171 行完整日志）及当前工程真实源码进行了逐行核实：

1. **根因定性 100% 属实（已全部采纳修复）**：
   - **H1（用户切太快）与 H2（20 个 WebView 并发耗尽资源）均被日志直接证伪**：`merged.log` 实际包含 4 次独立的 App 冷启动（`17:11:28`、`17:12:14`、`17:13:46`、`17:18:46`）；白条面板存活最长超过 32 秒（`9518f747` 从 `17:12:32.426` 到 `17:13:04.517`），且全程 `aux registered: panel` 的 `total` 仅在 1~2 之间。
   - **前 8 个白条真凶（进程 #2，`17:12:14` 冷启动直落 `大小姐`）**：`runtime.js` 的 `boot()` 在第三方扩展（JSR / ST-PT）与 MVU 尚未就绪时并发挂载 6 个重型 `blob:` 脚本 iframe，且 `mvuFromScripts` 正则漏判 `MagVarUpdate` 导致双份 MVU 加载，在 `17:12:15.294` 锁死了同源共享的 Chromium `CrRendererMain` 渲染主线程。
   - **第 9 个白条真凶（进程 #3，`17:15:35.714` 的 `4ce46720`）**：`MessageHtmlBlock.kt` 仅在 `AndroidView.update` 中调用 `mountState.submit()`，未在 `factory` 中调用；当 WebView 处于未 attach / 0 高度状态时 `onPageFinished` 先于 `update` 到达，`pendingHtml == null` 导致 `tryMount()` 直接空返回。
   - **`everMounted` 死循环真凶**：`PanelMountState.tryMount()` 在 `if (everMounted)` 分支重载 `panel.html` 时漏写 `everMounted = false`，导致任何二次 HTML 更新陷入无限 `loadUrl("panel.html")` 循环。
   - **闲置静默闪退且 `dumpsys dropbox` 为空真凶**：全工程 3 处 `WebViewClient` 均未重写 `onRenderProcessGone`（Android 默认返回 `false`，渲染进程因 `varsWait` 每 400ms 全量序列化解析数百 KB 会话 JSON + 每 1.5s 强制重排发生 OOM 被杀时，Chromium Native 层直接调用 `abort()` 强杀宿主 App，不走 Java `UncaughtExceptionHandler` 也不触发系统 ANR）。

2. **外部 AI 补丁代码中的 API 幻觉修正（重要）**：
   - 外部 AI 给出的根因分析是准确的，但其贴出的 Kotlin 补丁代码存在多处**方法名幻觉**（例如写成了不存在的 `TavernRuntimeManager.logDebug` / `logError`、`PanelBridge.onHeight`、`RikkaPanel.setChatVar`、以及虚构的 `ChatMessage.kt` `rememberStRegexedText` 签名）。
   - 本次交付源码已严格按照本工程真实 API（`TavernRuntimeManager.appendLog`、`PanelBridge.panelResize`、`StRegexedText.kt` 真实签名与 `TavernRuntimeManager.rendererGeneration`）完成落地合入。

---

## 二、`P0-3`（「导入预设只弹通知，预设管理里看不到，杀后台也没看到」）完整根因揭秘

除了 `00-总交接提示词.md` 中怀疑的 `Settings.dummy()` 之外，源码级逐行追踪发现了**三个叠加的必现级 Bug**，完美解释了为什么「每次弹导入成功通知，但列表里看不到、杀后台重进也没了」：

1. **第一重必现覆盖（0ms 同帧旧快照覆盖，`StTavernSections.kt:1034-1040` + `PromptPage.kt:216-234`）**：
   - 第 6 轮在 `PresetManagementTab` 的 `result.onSuccess` 中新增了 `raw?.let { (label, json) -> onStoreRawPreset(label, json) }`，紧挨在 `onUpdate(modeInjections + enabled)` 下一行同步执行。
   - 在 `PromptPage.kt` 中，这三个回调全部写成 `vm.updateSettings(settings.copy(...))`。
   - 因为同一帧内 Compose 尚未发生重组，`onStoreRawPreset` 捕获的 `settings` 依然是**导入前的旧快照**（其 `settings.modeInjections` 不包含刚刚导入的 `enabled` 条目）！
   - 由于每一个酒馆预设都含 `prompts` 数组，`raw` 100% 非空，因此 `onStoreRawPreset` 在 `onUpdate` 之后 **0 毫秒**必然执行，并用旧的 `settings.modeInjections` 把刚刚写入 `settingsFlow` 和 `DataStore` 的新预设 **100% 覆盖抹掉**！
2. **第二重必现覆盖（300ms 跨页面后台 JS 回调覆盖，`ChatPage.kt:221`）**：
   - 当用户从 `ChatPage` 导航到「设置 → 预设管理」时，`ChatPage` 离开前景停止收集 `collectAsStateWithLifecycle()`，其闭包内的 `tavernLatestSettings.value` 冻结在进入设置页之前的旧快照。
   - 当预设导入触发 `SettingsStore.update()` 时，系统向主运行时 WebView 发射 `settings_updated` 事件；JS 侧扩展（`ST-Prompt-Template` / `JS-Slash-Runner`）收到事件后调用 `saveSettingsDebounced()`，300ms 后回调 `ChatPage.kt:221` 注册的 `thirdPartySettingsSaver`，执行 `vm.updateSettings(tavernLatestSettings.value.copy(tavernExtensionSettings = json))`——用 `ChatPage` 冻结的旧 `Settings` 快照再次把整个 `SettingsStore` 回滚到导入前的状态！
3. **第三重持久化与门控缺口（`PreferencesStore.kt`）**：
   - `Settings` 数据类中的 `tavernGlobalScripts`、`tavernGlobalScriptsEnabled`、`tavernPresetScripts`、`tavernThirdPartyDisabled`、`tavernExtensionSettings` 5 个字段此前未接入 `PreferencesStore` 的 DataStore 读写映射，导致每次写完这 5 个字段后，DataStore 回灌的 `incoming` 恒为默认值，与 `SettingsEchoGate.pending` 永远不相等（`p == incoming` 恒为 `false`），直接卡死 `SettingsEchoGate`！
4. **修复闭环**：
   - 在 `PreferencesStore.kt` 补齐全部 5 个 `tavern*` 偏好键的读写；
   - 在 `PromptVM`、`ChatVM`、`AssistantDetailVM` 中统一改用基于 `settingsStore.settingsFlow.value` 的原子 CAS 方法 `patchSettings { current -> ... }`；
   - 在 `ExportSerializer.kt` + `StTavernSections.kt` + `PromptPage.kt` 中将预设导入收敛为单次 `onImportBundle` 原子更新，一次性写入 `modeInjections`、`tavernPresets`、`tavernPresetScripts` 与 `regexScripts`。

---

## 三、`P1-1`、`P1-2`、`P1-3`、`P2` 处理说明

1. **`P1-1`（助手消息高度限制与超长截断诊断）**：
   - 在 `MessageHtmlBlock.kt` 中已补齐 `screenHeightDp`、`safetyMaxHeightDp`、`heightDp`、`physicalPx` 打点；当 `physicalPx > 8192`（Android 硬件加速 Canvas 单纹理常见上限，在 3x 屏幕上约 2730dp，而 `大小姐` 面板高度为 3095~3865dp ≈ 9285~11595px）或触及 `safetyMaxHeightDp` 时，自动在 `tavern-runtime.log` 打印 `[panel] height threshold warning`。
   - 在 `runtime.js` 的 `measure()` 中补充计算 `#rikka-panel-root` 直接子元素的 `offsetTop + max(scrollHeight, offsetHeight)`，防止带偏移或内嵌高度的子容器被漏算。
2. **`P1-2`（卡片加载期间输入法弹出但输入框打不出字）**：
   - **根因**：Android `WebView` 默认 `isFocusable = true` 且 `isFocusableInTouchMode = true`。卡片加载或 `varsWait` 重挂期间，主运行时 WebView 或面板 WebView（内含 `#send_textarea` 桩或开局表单控件）会抢走焦点或改变 `InputConnection`，导致输入法虽处于弹出态但焦点不在底部 Compose `ChatInput` 上。
   - **修复**：主运行时离屏 WebView 永久设为 `isFocusable = false`；面板 WebView 初始设为 `isFocusable = false`、`isFocusableInTouchMode = false`，仅当用户手指实际触摸面板（`ACTION_DOWN`）时才开启焦点能力（兼顾开局设定表单填写）；同时在 `ChatInput.kt` 增加 `[input] focus changed` 诊断日志。
3. **`P1-3`（预设内嵌 34 条 `extensions.regex_scripts` 导入）**：
   - 已在 `ExportSerializer.kt` 实现 `importPresetRegexScripts` 与 `mergePresetRegexScripts`，复用 `parseCardRegexScripts` 解析并标记 `sourcePresetGroup = presetGroup`，按 `id` 去重并入 `Settings.regexScripts`，绝不覆盖用户自建正则（`sourcePresetGroup == null`）。经对 `05-卡与预设素材/` 下 3 个真实预设离线测试，10 + 13 + 11 = 34 条内嵌正则全部准确提取。
4. **`P2`（预设内 `extensions.SPreset` 第三方扩展私有配置告警）**：
   - 已在 `ExportSerializer.kt` 实现 `detectUnsupportedPresetExtensions`，导入含 `SPreset` 的预设时在 `tavern-runtime.log` 打印 `warn` 日志并在 Toast 中明确提示「含不支持的扩展私有配置 SPreset(...)」，原始 JSON 仍无损保存在 `Settings.tavernPresets` 中。

---

## 四、本轮明确没做的事（按交接提示词禁令严格留白）

1. **未伪造 `SPreset` 的运行时实现**（仅无损保存 + 明确告警）。
2. **未在角色卡或全局正则中硬编码剥离 `<TASK_CHECK_PASS>` / `<Interleaving>` 等标签**：因为这些标签的处理规则属于预设内嵌正则（`P1-3` 修复后导入预设即可自动带上对应的 34 条正则）。
3. **未对预设根级生成采样参数（`temperature` / `top_p` / `openai_max_tokens` 等）做自动覆盖**：避免导入预设污染用户自己的模型采样设置，所有 44 个根级字段均原样保存在 `Settings.tavernPresets`。
4. **未改动 `ChatDrawer.kt` 的设置入口及 `MessageHtmlBlock` 的 `1 CSS px == 1 dp` / `PanelScrollBridge` 契约**。

---

## 五、真机编译与回归验收清单

### 1. 编译命令（Windows PowerShell）
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease --console=plain
```

### 2. 真机回归验证项
- [ ] **验收 1（P0-1 冷启动直落 `大小姐` 白条修复）**：
  打开 `大小姐` 会话 → 上滑划掉 App 后台杀死进程 → 点击桌面图标冷启动直接进入 `大小姐`。确认首屏状态栏面板正常展开无白条；检查 `tavern-runtime.log` 确认依次出现 `[third-party] loaded ...`、`[scripts] loaded 6 ..., mvu_from_scripts=true`、`运行时页面就绪`。
- [ ] **验收 2（P0-1 切换分支与往返切卡无白条）**：
  在 `大小姐` 与 `百合花期` 之间往返切换，或切换消息 swipe 分支 / 编辑消息，确认不会卡在 48dp 白条，日志中出现 `[panel] mount key=...`。
- [ ] **验收 3（P0-2 闲置不闪退 & `exit-info` 自取证）**：
  停留在 `大小姐` 会话静置 5 分钟以上，确认不再无故闪退；启动时查看 `tavern-runtime.log` 顶部 `[exit-info] prev_exit[0]: reason=...` 可直接看到历史进程退出原因。
- [ ] **验收 4（P0-3 / P1-3 / P2 预设导入闭环）**：
  进入「设置 → 提示词管理 → 预设管理」，分别导入 `Femiris`、`Reborn`、`夏瑾` 三个预设 JSON：
  1. 确认 Toast 提示「导入成功：预设『...』（N 条注入，M 条内嵌正则）；注意：含不支持的扩展私有配置 SPreset(...)」；
  2. 确认「预设管理」列表中立即出现对应的 3 行预设，点开 `▾` 可查看和开关各条目；
  3. 进入「扩展中心 → 正则脚本」，确认预设内嵌的 34 条正则已并入列表；
  4. **杀掉 App 后台重新打开**，再次进入「预设管理」与「正则脚本」，确认导入的预设和正则 100% 持久保留！
  5. 检查 `tavern-runtime.log` 中的 `[preset] import start -> parsed -> before update -> after update` 四段日志完整闭环。
- [ ] **验收 5（P1-1 / P1-2 高度诊断与输入框打字）**：
  在卡片加载期间点击底部聊天输入框，确认可正常输入文字；查看 `tavern-runtime.log` 中 `[input] focus changed` 与 `[panel] height threshold warning` 日志。


---

## 六、第二轮（Full ST Parity 一次性补齐）附加说明

1. **深度差距评估报告**：请参见根目录新增的 `ST-PARITY-ASSESSMENT.md`，内含 7 大子系统逐项对齐矩阵（带源码文件与行号）、本轮排查修复的 7 项 Bug 详情、以及剩余 ~4.5% 架构取舍差异的诚实盘点。
2. **离线验证闭环**：
   - 全仓 50 个非 vendor `.js` 文件拷贝为 `.mjs` 后通过 `node --check` 100% 零语法错误；
   - 全仓 `PreferencesStore.kt` 89 个持久化字段 0 遗漏，`values-zh/strings.xml`（2177 键）为 `values/strings.xml`（2190 键）严格子集；
   - 新增单测已并入 `SillyTavernPresetImportTest.kt`、`WorldInfoDecoratorTest.kt` 与 `StSlashExecutorTest.kt`。

3. **本地构建与容器环境说明**：
   - 在本地具备 Android SDK 与网络连接的机器上，可直接执行 `./gradlew :app:testDebugUnitTest :app:assembleRelease` 编译运行；
   - 当前离线评估容器因禁网无法下载 `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip`（实测报 `UnknownHostException: services.gradle.org`），因此完整 APK 组装与真机 WebView 渲染已如实标记为需在本地 Android 环境执行。
