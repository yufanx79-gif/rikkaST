# 静态验证与回归检查报告（VALIDATION_REPORT）

> **重要声明**：遵照用户明确指令（严禁在当前 AI 环境中运行 Gradle 或伪造编译结果），本轮验证全部为**非 Gradle 静态检查**。所有依赖 Gradle 的编译与测试任务均如实标记为 `NOT RUN — USER REQUESTED NO BUILD`。当前交付物状态为：**已完成静态审查，待本机编译验收**。

## 1. 输入基线 SHA-256 校验结果（Exit Code: 0）
- **命令**：`sha256sum /working_dir/.../user_uploaded/*.zip`
- **证据文件**：`docs/ai-work/EVIDENCE/input_sha256.txt`
- **结果**：
  - `rikkaST Gemini 自信交付.zip`：`562d064a27bd36ec09e8fe3c88f63a88d9fd8e78d664de290bf6f04191b1eec5`（**MATCH** `BASELINE_AND_SCOPE_MANIFEST_CN.md`）
  - `SillyTavern-1.18.0.zip`：`9ff16405d8df12679049f238292cb313e6c59676bc268fb639e1db1e8d74fdc2`（**MATCH** `BASELINE_AND_SCOPE_MANIFEST_CN.md`）
  - `RikkaST_Complete_AI_Engineering_Package_2026-10-15.zip`：`16317e0375c0f48c16ff4d06e3559cfeb5b2af18c5ed3e0129339f695177c520`

## 2. P0 编译错误历史回归门禁逐项核验（Exit Code: 0）

### 2.1 `STATIC_BASELINE_SPOT_CHECK_CN.md` 12 项当前阻塞点
| 编号 | 文件路径 | 问题简述 | 修改前状态 | 最终状态 | 验证证据与符号定位 |
|---|---|---|---|---|---|
| `KOT-01` | `.../transformers/PromptInjectionTransformer.kt` | 用包名 `.run {}` 调用扩展函数 `matchesAnyKey` | `PRESENT` | **`FIXED`** | 顶部新增 `import me.rerere.rikkahub.data.model.matchesAnyKey`，直接调用 `entry.matchesAnyKey(entry.excludeKeys, context)` |
| `KOT-02` | `.../data/export/ExportSerializer.kt` | `SelectiveLogic` 未导入且 `when` 缺少 `OR_ANY` 分支 | `PRESENT` | **`FIXED`** | 新增 `import me.rerere.rikkahub.data.model.SelectiveLogic` 并补齐 `SelectiveLogic.OR_ANY -> 0` |
| `KOT-03` | `.../service/ChatService.kt` | 可空 `convUuid: Uuid?` 传给非空 `Uuid` 参数 | `PRESENT` | **`FIXED`** | 修正为 `backgroundTextGenerationParams(model, convUuid ?: Uuid.random())` |
| `KOT-04` | `.../ui/pages/chat/ChatList.kt` | `ChatMessageItem` 重复传入 `lastMessage` 命名参数 | `PRESENT` | **`FIXED`** | 删除第 386 行重复的 `lastMessage = index == lastMessageIndex` |
| `KOT-05` | `.../ui/pages/chat/ChatSlashHost.kt` | 两处 `?: if (...) null else ... .orEmpty()` 优先级错误 | `PRESENT` | **`FIXED`** | 用括号包裹整个 Elvis 表达式 `(TavernVariableStore.macroGet(...) ?: if (...) null else ...).orEmpty()` |
| `KOT-06` | `.../data/model/WorldInfoDecorators.kt` | 顶层函数与扩展函数 `applyWorldInfoDecoratorOverrides` JVM 签名冲突 | `PRESENT` | **`FIXED`** | 为顶层函数添加 `@JvmName("applyWorldInfoDecoratorOverridesTopLevel")` |
| `KOT-07` | `.../transformers/PromptInjectionTransformer.kt` | `collectInjections` 缺少 `delayOverrideEntries` 形参 | `PRESENT` | **`FIXED`** | 在 `collectInjections` 增加 `delayOverrideEntries: Map<Uuid, Int> = emptyMap()` 并从 `transformMessages` 透传 |
| `KOT-08` | `.../data/st/script/StSlashExecutor.kt` | 访问不存在的 `StClosure.rawText` 属性 | `PRESENT` | **`FIXED`** | 改为访问 `closure.raw` 与 `parsed.raw`（保留 JSON 字段名 `"rawText"`） |
| `KOT-09` | `.../data/st/script/StSlashExecutor.kt` | 两处调用不存在的 `invokeClosure(...)` | `PRESENT` | **`FIXED`** | 改为调用 `runClosure(closure = ..., provided = ..., host = host, parentPipe = pipe, scope = scope, label = ...)` |
| `KOT-10` | `.../ui/pages/chat/ChatPage.kt` | `LaunchedEffect` 内引用未声明的 `toaster` 与 `openConversation` | `PRESENT` | **`FIXED`** | 在 `ChatPage` 顶层声明 `val toaster = LocalToaster.current` 与 `val openConversation: (Uuid) -> Unit` |
| `KOT-11` | `.../assistant/detail/AssistantImporter.kt` | `withContext(Dispatchers.IO)` lambda 内使用非局部 `return` | `PRESENT` | **`FIXED`** | 改为 `return withContext(Dispatchers.IO) { when (mime) { ... } }` 统一由分支表达式返回 `Pair<Assistant, List<Lorebook>>` |
| `KOT-12` | `.../ui/pages/chat/ChatSlashHost.kt` | 使用 `UIMessage.user`/`assistant` 但缺少 `import` | `PRESENT` | **`FIXED`** | 顶部补齐 `import me.rerere.ai.ui.UIMessage` |

### 2.2 `COMPILE_FAILURE_REGRESSION_CHECKLIST_CN.md` 早期历史问题复核
| 编号 | 检查项 | 状态 | 证据说明 |
|---|---|---|---|
| `B1` | `Assistant.regularExpressions` 错误字段名 | `NOT PRESENT` | 全仓扫描无 `.regularExpressions`，`ChatViewModel.kt` / `ChatSlashHost.kt` 均使用 `assistant.regexes` |
| `C1` | Kotlin 单行字符串字面量内被真实换行打断（`String.replace("\n", ...)` 断行） | `NOT PRESENT` | 状态机词法扫描全部 `925` 个 `.kt`/`.kts` 文件，单行字符串跨行错误数为 `0` |
| `C2` | `StClosure.kt` 对 `{| ... |}` 管道块语法及 `findClosureSpan` / `isClosureLiteral` 的支持 | `NOT PRESENT` | `StClosure.kt` 已完整支持 `{: :}` 与 `{| |}` 成对匹配与深度计数 |
| `C3` | `StClosure(emptyList(), ...)` 构造器位置参数错位 | `NOT PRESENT` | 全仓仅在 `StClosure.kt` 内使用具名参数构造 `StClosure(...)` |
| `C4` | `/times` 的 `{{timesIndex}}` 与 `{{pipe}}` 语义对齐 | `NOT PRESENT` | `StSlashExecutor.kt` 与 `StSlashExecutorTest.kt` 语义一致 |
| `C5` | 测试源码引用不存在的生产 API | `NOT PRESENT` | 静态核对 `WorldInfoDecoratorTest.kt`、`SillyTavernPresetImportTest.kt`、`StSlashExecutorTest.kt`、`MessageHtmlDetectionTest.kt` 中引用的全部函数与属性，均在 `app/src/main` 中真实存在 |

## 3. 本轮实际运行的非 Gradle 静态检查汇总
| 检查项目 | 实际执行命令 | 扫描范围 | 退出码 | 结果摘要 | 证据路径 |
|---|---|---|---|---|---|
| Kotlin 词法/括号/字符串跨行状态机扫描 | `python3 run_static_verification.py` | 全仓 `925` 个 `.kt`/`.kts` 文件 | `0` | `0` 处词法/跨行字符串/大括号失衡错误 | `docs/ai-work/EVIDENCE/static_verification_results.json` |
| Node.js ES Module 语法检查 | `node --check <file.js>`（含临时 `type: module`） | `app/src/main/assets/st-runtime/` 下全部 `50` 个非 vendor `.js` 文件 | `0` | `50/50` 文件通过语法检查，`0` 错误 | `docs/ai-work/EVIDENCE/static_verification_results.json` |
| JSON 格式合法性校验 | `python3 -c "json.load(...)"` | `app/src/` 下全部 `11` 个 `.json` 文件 | `0` | `11/11` 文件合法，`0` 错误 | `docs/ai-work/EVIDENCE/static_verification_results.json` |
| XML Android 资源与 Manifest 校验 | `python3 -c "ET.parse(...)"` | `app/src/` 下全部 `31` 个 `.xml` 文件 | `0` | `31/31` 文件合法，`0` 错误 | `docs/ai-work/EVIDENCE/static_verification_results.json` |

## 4. 未执行项目清单（NOT RUN — USER REQUESTED NO BUILD）
以下项目因需要 Android SDK / Gradle 编译环境或 Android 真机运行时，按用户明确要求未在当前环境中执行，留待用户 Windows 本机验收：
- `.\gradlew.bat compileDebugKotlin` / `compileReleaseKotlin` — **`NOT RUN — USER REQUESTED NO BUILD`**
- `.\gradlew.bat compileDebugUnitTestKotlin` — **`NOT RUN — USER REQUESTED NO BUILD`**
- `.\gradlew.bat testDebugUnitTest`（含本轮新增的 `StSlashExecutorTest`、`SillyTavernPresetImportTest`、`WorldInfoDecoratorTest`、`MessageHtmlDetectionTest` 用例） — **`NOT RUN — USER REQUESTED NO BUILD`**
- `.\gradlew.bat lintDebug` — **`NOT RUN — USER REQUESTED NO BUILD`**
- `.\gradlew.bat assembleDebug` / `assembleRelease` — **`NOT RUN — USER REQUESTED NO BUILD`**
- Android 真机 WebView 渲染、楼层交互卡点击、立绘切换与前台服务保活冒烟测试 — **`NOT RUN — REQUIRES ANDROID DEVICE`**
