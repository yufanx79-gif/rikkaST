# SPreset core 自测

对应实现目录：

`app/src/main/java/me/rerere/rikkahub/data/st/preset/`

## 文件

| 文件 | 作用 |
| --- | --- |
| `StSpresetConfig.kt` | 全字段数据模型 + camelCase/snake_case 别名 + form.parameters → JSON Schema |
| `SpresetParse.kt` | 纯 JSON 解析、`Settings.tavernPresets` 激活组 / FixedPresetName 回退 |
| `SpresetJsRuntime.kt` | QuickJS 公共初始化（console stub / 函数安装 / 参数注入 / 5s 超时） |
| `SpresetCompat.kt` | 公共 API：parse / ToolBindings / OutputPreprocessing / FinalRequestProcessing |
| `SpresetChatSquashEngine.kt` | ChatSquash conditional 严格三条路径 + 旧版 role 合并 |
| `SpresetMessageInjectionEngine.kt` | MessageInjections → assistant 消息注入 |
| `SpresetStreamEngine.kt` | OutputPreprocessing 流式持久 runtime、前缀差量、toolCallFormatter 注入 |

## Node 自测

```powershell
node scripts/spreset-core/verify.mjs "E:\Localsend download\【小猫之神·喵赛亚】1.5.json"
node scripts/spreset-core/verify-wrappers.mjs "E:\Localsend download\【小猫之神·喵赛亚】1.5.json"
```

`verify.mjs` 验证真实预设结构、ToolBindings code、OutputPreprocessing 脚本、
toolCallFormatter、FinalRequestProcessing、ChatSquash post-script，以及
conditional ChatSquash → FinalRequestProcessing 端到端（含 lastUser 之后的 AFTER_DIALOG system）。

`verify-wrappers.mjs` 与 Kotlin 侧 QuickJS 字符串模板保持一致，验证：

- `JsonPrimitive(String)` 等价的 JSON 字符串字面量注入；
- `(0, eval)(script)` 安装箭头函数；
- `await fn(args)` / `JSON.stringify` 返回；
- `new Function(code)()` 与 `new Function('args', actionBody)`；
- formatter 的 `arguments` 是 JSON.parse 后的对象；
- squashed_post_script 的字符串参数调用。

## Host Kotlin 自测

`HostParseTest.kt` 不依赖 Android/QuickJS（post-script 临时关闭），验证：

- 真实预设六键解析：2 个 ToolBindings（1 enabled game_content）、OutputPreprocessing
  enabled/consumeToolCalls、FinalRequestProcessing enabled、FixedPresetName 空、
  ForcedPostProcessing enabled/mode 空、2 个 MessageInjections 全 disabled；
- `parseActivePreset(enabledGroups=emptySet())` 按 FixedPresetName 回退；
- conditional ChatSquash 严格 `[system, assistant, user]`，且 lastUser 之后的
  AFTER_DIALOG system 不被丢弃；
- MessageInjections 的 reasoning/toolCall/toolResult 展开；
- `createDynamicTools` 用 form.parameters 生成 `InputSchema.Obj`；
- `processStream` 在 OutputPreprocessing 禁用时逐 chunk 透传；
- snake_case/camelCase 兼容。

当前环境用 Kotlin 2.4.21 embeddable compiler 编译通过（`MAIN_EXIT=0`），
HostParseTest 编译运行通过（`RUN_EXIT=0`）。没有跑 Gradle assemble。

## 已知边界

- `separate_chat_history` / `re_split` 已解析但暂未在 ChatSquash 中实现独立的
  chatHistory 分段语义（真实小猫之神预设为 false）。
- `OutputPreprocessing` 遇到脚本持续抛错时会从出错 chunk 起退化为原文透传；
  已发出的前缀文本无法回滚。
- `FinalRequestProcessing` 未知工具按任务要求生成 no-op Tool；其 execute 返回空 part。
- QuickJS 原生行为最终仍需设备/模拟器验证；host 侧已验证编译、纯逻辑和 JS 包装模板。
