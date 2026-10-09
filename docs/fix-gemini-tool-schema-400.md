# 修复报告：开启「JavaScript 引擎」工具后 Gemini / Vertex 接口 400（`items: missing field`）

| 项 | 值 |
| --- | --- |
| 仓库 | `D:\rikkaST`（rikkaST 2.4.6，`versionCode = 237`） |
| 分支 / HEAD | `main` @ `439f030` |
| 修复日期 | 2026-10-08 |
| 触发条件 | 助手开启本地工具 **「JavaScript 引擎」** + provider 使用 **Gemini 协议**（Google / Vertex / 任何 Gemini 兼容中转，例如 Antigravity） |
| 严重程度 | 阻塞级：请求在发出阶段就被 400 拒绝，该助手完全无法对话 |

---

## 一、现象

开启「JavaScript 引擎」开关后，任何一次生成都会立刻返回 400：

```json
{"error": {"code": 400, "message": "Antigravity API Error (已重试 0 次): API Error 400: {\n  \"error\": {\n    \"code\": 400,\n    \"message\": \"* GenerateContentRequest.tools[0].function_declarations[4].parameters.properties[args].items: missing field.\",\n    \"status\": \"INVALID_ARGUMENT\"\n  }\n}\n"}}
```

关键特征：

- **关掉开关就恢复正常**，说明问题只跟这个工具声明有关；
- **只有 Gemini 系 provider 报错**，同一个助手换 OpenAI / Claude 协议（哪怕工具开关照开）不报错；
- 报错发生在**请求阶段**，JS 代码一行都没执行 —— 日志里的 QuickJS / MVU / `[RikkaTavern]` 输出都是无关噪声。

---

## 二、根因

### 2.1 把报错路径逐段翻译

```
GenerateContentRequest
  .tools[0]
  .function_declarations[4]          ← 本次请求工具列表里的第 5 个工具
  .parameters
  .properties[args]                  ← 该工具有一个叫 args 的参数
  .items: missing field              ← args 的 type 是 array，但没有给 items 子 schema
```

Gemini / Vertex 对 function calling 的 schema 是**强校验**的：凡 `type: "array"` 的参数，**必须**带 `items`，且 `items` 里要有 `type`。缺了就直接 400，一个字段不合规会拖死整个请求（不像 OpenAI / Claude 那样宽容）。

### 2.2 定位到具体代码

全仓扫描 `put("type", "array")`，共 4 处，只有 1 处没有配套 `items`：

| 位置 | 参数 | items |
| --- | --- | --- |
| `app/.../data/ai/tools/LocalTools.kt`（`eval_javascript`） | `args` | ❌ **缺失** |
| `app/.../data/ai/tools/local/AskUserTool.kt:22` | `questions` | ✅ |
| `app/.../data/ai/tools/local/AskUserTool.kt:36` | `options` | ✅ |
| `app/.../data/ai/tools/TaskTools.kt:362` | `todos` | ✅ |

同时全仓只有 `eval_javascript` 这一个工具的参数名叫 `args`，与报错路径 `properties[args]` 完全吻合。

**出问题的原始代码**（修复前）：

```kotlin
put("args", buildJsonObject {
    put("type", "array")                                   // ← 声明为数组
    put("description", "JSON array of arguments for function call")
})                                                         // ← 缺少 items
```

### 2.3 为什么「只有开这个开关才炸」

工具列表在 `app/.../service/ChatService.kt:1247-1258` 拼装，`javascriptTool` 只有在 `assistant.localTools` 里包含 `LocalToolOption.JavascriptEngine` 时才会被加入（`LocalTools.getTools()`）。不开开关 → 列表里没有这个工具 → 没有 `properties.args` → 不报错。

### 2.4 为什么索引正好是 `[4]`

拼装顺序是：workspace 工具（正好 4 个：`workspace_read_file` / `workspace_write_file` / `workspace_edit_file` / `workspace_shell`）→ 本地工具（JS 引擎排在第一个）→ ……

所以 `eval_javascript` 落在 `function_declarations[4]`，与报错索引完全对上，反向印证了结论。

### 2.5 为什么只有 Gemini 系报错

schema 是原样序列化发出去的。`GoogleProvider.kt` 在发请求前只做「**删**字段」（`const` / `exclusiveMaximum` / `exclusiveMinimum` / `format` / `additionalProperties` / `enum`），**从不做补字段或规范化**，所以没有任何兜底。

---

## 三、修复内容

三件事一起做：**根治 + provider 层兜底 + 回归测试**。

### 3.1 根治：给 `args` 补上 `items`

`LocalTools.kt` 里 `eval_javascript` 的 `args` 参数补上 `items`：

```kotlin
put("args", buildJsonObject {
    put("type", "array")
    // Gemini / Vertex 拒绝缺 items 的数组：parameters.properties[args].items: missing field.
    put("items", buildJsonObject { put("type", "string") })
    put("description", "JSON array of arguments for function call")
})
```

### 3.2 兜底：新增 provider 层的 schema 规范化（新增文件）

新增 `ai/src/main/java/me/rerere/ai/util/InputSchemaSanitizer.kt`：

```kotlin
fun JsonElement.normalizeInputSchema(): JsonElement
```

行为：**递归**遍历 schema，凡是 `type == "array"` 但 `items` 缺失、或 `items` 存在却没有 `type`（例如空对象 `{}`）的节点，一律补成 `{"items": {"type": "string"}}`。函数幂等，可以每次请求都调用。

为什么需要它：工具 schema 不只来自本项目代码，还来自**第三方 MCP server**（`McpSessionRegistry.kt:514-515` 把服务端返回的 `properties` 原样透传）。任何一个 MCP 工具声明了「数组但没有 items」，在 Gemini 下都会 100% 复现同样的 400。有了这层兜底，这类问题以后不会再打穿到接口层。

接入点：`GoogleProvider.kt` 序列化工具参数时调用（**只接在 Google 这一条链路上**，因为只有它做致命强校验，避免影响 OpenAI / Claude 的既有行为）：

```kotlin
element = json.encodeToJsonElement(tool.parameters())
    // Gemini / Vertex 拒绝缺 items 的数组 schema（400: missing field），统一兜底
    .normalizeInputSchema()
    .removeElements(
        listOf("const", "exclusiveMaximum", "exclusiveMinimum", "format", "additionalProperties", "enum")
    )
```

### 3.3 可测性：把 JS 工具 schema 抽成顶层函数

原来 schema 内联在 `LocalTools`（需要 Android `Context` 才能构造）里，JVM 单测拿不到。现抽成顶层函数 `internal fun javascriptToolSchema(): InputSchema`（文件末尾），工具侧改为：

```kotlin
parameters = { javascriptToolSchema() },
```

行为完全不变，只是变得可被单测直接校验。

### 3.4 回归测试

- `ai/src/test/java/me/rerere/ai/util/InputSchemaSanitizerTest.kt`：6 个用例覆盖规范化函数（补 `items`、保留已有 `items`、修复无 `type` 的 `items`、递归、幂等、不动非数组）。
- `app/src/test/java/me/rerere/rikkahub/data/ai/tools/JavascriptToolSchemaTest.kt`：
  - 断言 `eval_javascript` 的 `args` 确实带 `items`（本次 bug 的直接回归守卫）；
  - 用一个递归遍历器扫描所有**不依赖 Context** 的本地工具 schema（`eval_javascript` / `ask_user` / `createTaskTools()`），断言「任何 `type: array` 都必须有 `items`」，防止以后新增工具再犯同样的错。

---

## 四、改动文件清单

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `ai/src/main/java/me/rerere/ai/util/InputSchemaSanitizer.kt` | 新增 | `normalizeInputSchema()` 规范化工具 schema |
| `ai/src/test/java/me/rerere/ai/util/InputSchemaSanitizerTest.kt` | 新增 | 规范化函数的单测 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/JavascriptToolSchemaTest.kt` | 新增 | 工具 schema 回归测试 |
| `ai/src/main/java/me/rerere/ai/provider/providers/google/GoogleProvider.kt` | 修改 | +1 import、+1 处 `.normalizeInputSchema()` 调用 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt` | 修改 | `args` 补 `items`；schema 抽成顶层函数 `javascriptToolSchema()` |

净改动约 44 行新增 / 33 行删除，**不涉及**任何 UI、数据库、迁移、协议字段变更。

---

## 五、验证

### 5.1 单元测试

```powershell
cd D:\rikkaST
.\gradlew.bat --offline :ai:testDebugUnitTest :app:testDebugUnitTest `
  -Pchaquopy.python=C:/Users/<you>/AppData/Roaming/uv/python/cpython-3.12.13-windows-x86_64-none/python.exe
```

### 5.2 接口层验证（最贴近现场）

1. 装新包，助手保持「JavaScript 引擎」**开启**，provider 选 Gemini 兼容端点；
2. 发任意一条消息；
3. 期望：不再出现 `parameters.properties[args].items: missing field`，且工具调用正常（可以让模型跑一段 `eval_javascript` 的 `action='eval'` 代码验证）。

也可以直接抓包看请求体：`tools[0].functionDeclarations[*].parameters.properties.args` 里应该出现 `"items":{"type":"string"}`。

### 5.3 构建产物

| 项 | 值 |
| --- | --- |
| 产物路径 | `D:\rikkaST\dist\rikkaST-2.4.6-jsitems-fix.apk` |
| 大小 | 58,616,037 B（约 55.9 MiB） |
| SHA-256 | `B485988B189E0BAD68A6E0CDF3CC4CF98653A41CCD852981FE45D9DD25FAC940` |
| MD5 | `A0DECE97B5D7C3D973871023E73807E6` |
| 包名 / 版本 | `me.rerere.rikkahub.st` / versionCode `237` / versionName `2.4.6` |
| ABI | 仅 `arm64-v8a`（`minSdk 26` / `targetSdk 37`） |
| 签名 | APK Signature Scheme v2，证书 `CN=rikkaST`（SHA-256 `b01fb58500aa307fbba70a0c0f242d949b131435e81b16acbd3e5b365ee45748`），与旧包 `rikkaST-2.4.6-public.apk` **同一把 key**，可直接覆盖安装 |
| 构建命令 | `.\gradlew.bat --offline --continue --console=plain -Pchaquopy.python=<py3.12> :ai:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease` |
| 构建结果 | `BUILD SUCCESSFUL in 11m`，`561 actionable tasks: 130 executed, 431 up-to-date` |

**测试结果：66 个测试类 / 744 个用例，failures=0 errors=0 skipped=0**（含本次新增 8 个用例）。

---

## 六、影响面与风险

- **对 Gemini 系**：修的是被 400 挡住的路径，属于纯修复；顺带让所有工具（含 MCP）的数组参数都合规。
- **对 OpenAI / Claude**：`LocalTools` 里 `args` 多了一个 `items: {type: string}` 声明。这只是把原本隐含的「参数是字符串数组」写明，不改变工具执行逻辑（`execute` 仍然把 `args` 当 JSON 解析，数组/对象都能吃）。兜底函数**没有**接在这两条链路上，行为不变。
- **对 JS 引擎本身**：`javascriptToolSchema()` 只是把同一段 schema 搬到顶层，语义零变化。
- **构建脚本遗留问题（本次未改）**：`app/build.gradle.kts` 用 `project.findProperty("chaquopy.python")` 读取 Python 路径，而 Gradle **不会**把 `local.properties` 注入 project properties，所以 `BUILDING.md` 里写的「在 local.properties 里配 chaquopy.python」实际不生效，必须用 `-Pchaquopy.python=...` 或 `CHAQUOPY_PYTHON` 环境变量。建议后续单独修。

---

## 七、后续建议（本次未做）

1. **把兜底函数接到 Claude / OpenAI 链路**：目前只接了 Google。如果希望三家行为一致（例如未来 OpenAI 开了 strict 模式），可在 `ClaudeProvider.kt:508`、`ChatCompletionsAPI.kt:417`、`ResponseAPI.kt:267` 同样加一行 `.normalizeInputSchema()`。
2. **在 provider 层做统一的 schema 消毒**：现在「删字段」逻辑（`removeElements`）和「补字段」逻辑（`normalizeInputSchema`）是两段散落的代码，可合并成一个 `sanitizeToolSchema(provider)`。
3. **扩大回归测试覆盖面**：把需要 `Context` 的本地工具（`file` / `python` / `shell` / `calendar` / `tts` / `clipboard` …）也纳入 schema 校验；或给 `LocalTools` 加一个不依赖 Context 的 schema 清单。
4. **修 `chaquopy.python` 读取问题**（见第六节）。

---

## 附：一句话结论

`eval_javascript` 工具的 `args` 参数声明成数组却没写 `items`，Gemini 系接口对工具 schema 强校验，直接把整个请求 400 掉；本次给它补上 `items`，并在 Google provider 序列化工具 schema 时统一做「数组必补 items」的兜底，同时补了回归测试。