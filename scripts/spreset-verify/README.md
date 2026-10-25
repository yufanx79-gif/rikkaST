# SPreset 六字段独立语义验证报告

- 验证对象：`E:\Localsend download\【小猫之神·喵赛亚】1.5.json`
- 项目：`D:\10-22-final-rikkast-10-22-final`
- 验证方式：Node `vm` 直接执行预设里的实际 JS 脚本；另用 `D:\_work\SPreset-source\inject.js`（starowo/SPreset 公开版 1.0.0）对照 `squashPrompts` / `postProcess` 语义。
- 写入范围：只写 `scripts/spreset-verify/`，未改 app 源码，未运行 Gradle。

## 1. 测试命令与结果

```powershell
cd D:\10-22-final-rikkast-10-22-final
$env:SPRESET_JSON='E:\Localsend download\【小猫之神·喵赛亚】1.5.json'
node --test scripts/spreset-verify/semantics.test.mjs
```

实际结果：**50 tests / 50 pass / 0 fail**。

同时验证了自包含 fixture（不依赖 E: 盘输入文件）：

```powershell
$env:SPRESET_JSON='D:\10-22-final-rikkast-10-22-final\scripts\spreset-verify\fixtures\spreset-cat-god.json'
node --test scripts/spreset-verify/semantics.test.mjs
```

结果同样 **50/50 pass**。fixture 是用真实文件抽取的六字段 + `ChatSquash` + `prompts` / `prompt_order`。

其他可运行演示：

```powershell
node scripts/spreset-verify/inspect.mjs             # 打印结构 + 脚本实际输出
node scripts/spreset-verify/simulate.mjs             # 旧版 squash vs 条件块合并 + FinalRequest
node scripts/spreset-verify/current-kotlin-preview.mjs # 当前 Kotlin 条件合并逻辑的 JS 等价预览
```

## 2. 六个私有键的结构摘要

`extensions.SPreset` 顶层键：`ChatSquash`、`RegexBinding`、`MacroNest`、`ToolBindings`、`MessageInjections`、`OutputPreprocessing`、`FixedPresetName`、`ForcedPostProcessing`、`FinalRequestProcessing`。

### ToolBindings（2 个）

| key / uuid | enabled | form.name | parameters | actionBody |
|---|---:|---|---|---|
| `fbb8a1bc-...` / `2bd707ba-d098-4e6e-ad78-34e6d6477cbc` | **false** | `soliumbra_think` | `thinking: string, required` | `return JSON.stringify({ ok: true });` |
| `3df19ba7-...` / `da14679f-7aad-4cd1-b66d-9790a51f0e70` | **true** | `game_content` | `content: string, required` | `return JSON.stringify({ ok: true });` |

两者 `valid=true`、`editorMode="form"`、`stealth=false`、`form.displayName=""`，同时存在 `code`（完整 `return {name,description,parameters,action,stealth};`）与 `actionBody`。

直接执行 `code` 的结果：

- 返回对象包含 `name`、`description`、`parameters`（标准 JSON Schema 对象）、`action`、`stealth`。
- `game_content` 的 parameters：
  ```json
  {"type":"object","properties":{"content":{"type":"string"}},"required":["content"]}
  ```
- `soliumbra_think` 的 parameters：
  ```json
  {"type":"object","properties":{"thinking":{"type":"string","description":"思考内容"}},"required":["thinking"]}
  ```
- 调用 `tool.action({})` 都返回字符串 `{"ok":true}`。

**重要**：`soliumbra_think` 的 `enabled=false`，正常生成时**不应注册为动态工具**；但下面 formatter 本身仍能处理该工具的历史/幻觉调用。

### MessageInjections（2 个）

```json
{
  "91918c0a-bfc6-4101-9796-5421aab6fae8": {
    "enabled": false, "editorMode": "form", "reasoning": "", "signature": "",
    "toolCalls": [], "toolResults": []
  },
  "6e9b2959-4ea3-4d02-88b0-d15e3e6e14ef": {
    "enabled": false, "editorMode": "form", "reasoning": "", "signature": "",
    "toolCalls": [], "toolResults": []
  }
}
```

两个都 disabled，且没有 reasoning / toolCalls / toolResults 内容。注意它们的 key 同时出现在 `prompts[]` / `prompt_order` 中；对应 prompt 的注入由 prompt_order 控制，不要因为这里 `enabled=false` 就把已由 prompt_order 引入的内容再重复注入。

### OutputPreprocessing

```json
{
  "enabled": true,
  "consumeToolCalls": true,
  "toolCallFormatter": { "enabled": true, "script": "({ calls }) => ..." },
  "script": "({ buffer, final, state = {} }) => { console.log(buffer); ... }"
}
```

实际执行 `script`（`final` 为 true/false 都一样）：

```json
{
  "output": "<原 buffer>",
  "hold": "",
  "state": { "seed": 1, "accepted": true }
}
```

脚本内部调用 `console.log(buffer)`；QuickJS 必须注入 console shim，否则脚本抛错。

### FixedPresetName / ForcedPostProcessing / FinalRequestProcessing

```json
{
  "FixedPresetName": "",
  "ForcedPostProcessing": { "enabled": true, "mode": "" },
  "FinalRequestProcessing": { "enabled": true, "script": "({ request }) => {...}" }
}
```

- `FixedPresetName` 为空：有 enabledGroups 时正常走启用组，**不要**用空字符串回退。
- `ForcedPostProcessing.enabled=true` 但 `mode=""`：应视作**无强制模式**，不能把用户原有 post-processing 覆盖成空串。
- `FinalRequestProcessing.enabled=true`，脚本长度 1670 字符。

### ChatSquash（本预设实际使用）

```json
{
  "enabled": true,
  "conditional_enabled": true,
  "conditional_tag": "<|merge|>",
  "separate_chat_history": false,
  "parse_clewd": false,
  "user_role_system": false,
  "role": "assistant",
  "user_prefix": "|{{user}}|\n",
  "user_suffix": "",
  "char_prefix": "|游戏剧情|\n",
  "char_suffix": "",
  "prefix_system": "",
  "suffix_system": "",
  "enable_squashed_separator": true,
  "squashed_separator_regex": false,
  "squashed_separator_string": "<|sep|>",
  "squashed_post_script_enable": true,
  "re_split": false
}
```

`stop_string` 为 JSON 字符串 `["<end>", "<ctrl", "<thought>"]`，但 `enable_stop_string=false`。

## 3. toolCallFormatter 实测

脚本：

```js
({ calls }) => calls.map(call =>
  call.name === 'soliumbra_think'
    ? `<think_nya~>\n${call.arguments.thinking}\n</think_nya~>`
    : call.arguments.content
).join('\n');
```

实测：

| 输入 calls | 输出 |
|---|---|
| `game_content{content:'你好'}` + `soliumbra_think{thinking:'想一下'}`（按此顺序） | `"你好\n<think_nya~>\n想一下\n</think_nya~>"` |
| 仅 `game_content{content:'你好'}` | `"你好"` |
| 仅 `soliumbra_think{thinking:'想一下'}` | `"<think_nya~>\n想一下\n</think_nya~>"` |
| `calls: []` | `""` |
| `arguments` 是 JSON 字符串 `'{"content":"你好"}'`（未解析） | `""` |

结论/坑：

- 传给 formatter 的 `calls[].arguments` **必须是已经 `JSON.parse` 后的对象**；传字符串会静默得到空串。
- 输出顺序完全跟 calls 顺序；多个 `game_content` 用 `\n` 连接。
- 未知工具会取 `call.arguments.content`；若该字段不存在，`join` 中的 `undefined` 会变成空片段（可能留下尾部 `\n`）。
- `soliumbra_think` 虽然 disabled，formatter 仍应能处理它（历史记录、模型幻觉、旧对话）；不要先按 binding.enabled 过滤 calls。

## 4. FinalRequestProcessing 实测

### 输入（任务给定形状）

```json
{
  "messages": [
    {"role":"system","content":"system prefix\n<run_tool_replacement>\nsystem suffix"},
    {"role":"assistant","content":"角色资料"},
    {"role":"user","content":"开始"}
  ],
  "tools": [
    {"type":"function","function":{"name":"game_content","description":"x","parameters":{"type":"object","properties":{}}}}
  ]
}
```

### 输出（实际行为）

```json
{
  "messages": [
    {"role":"system","content":"system prefix\n\nsystem suffix"},
    {"role":"user","content":"开始"}
  ],
  "tools": [
    {"type":"function","function":{"name":"game_content","description":"x","parameters":{"type":"object","properties":{}}}},
    {"type":"function","function":{"name":"context","description":"角色资料","parameters":{"type":"object","properties":{}}}}
  ]
}
```

结论：

- `messages.length` 变成 **2**：system（去掉 marker）+ user；assistant 被删除。
- **tools 不是“仅保留 context”**：原有非 context 工具全部保留，`context` 追加到最后；已有 `context` 会先被过滤掉再重新追加。
- `context.function.description` = assistant 的 content 文本；`context.function.parameters` = `{"type":"object","properties":{}}`（无 required）。
- 没有 marker：原样返回 request（不抛错）。
- marker 存在，但 messages 不是严格 3 条、或角色不是 `system → assistant → user`：**throw** `预期消息为三条：system → assistant → user`。
- marker 只在 user/assistant 中、system 没有：原样返回，不处理。
- array content：`toText` 用 `\n` 拼接；字符串 part 原样，对象 part 取 `part.text ?? ''`。marker 会从 system 的字符串 part 或 `{text}` part 中删除。
- `request.tools` 缺失时，只追加 `context`。
- 脚本会原地修改传入的 `request` 并返回同一个对象；调用方若要重试，先 clone。

### Kotlin 侧建议

- 传给脚本的 `request` 至少需要 `messages` 与 `tools`；本预设脚本不读 temperature 等字段。建议仍传完整 OpenAI-ish request，避免未来脚本需要其他字段。
- messages 必须能序列化成 OpenAI 形状：`role` + `content` string/array。RikkaHub 的 `UIMessagePart.Image` 等非文本 part 在 `toText` 中会变成空串（且多出一个 `\n`），不要期望图片进入 context description。
- tools 必须是 `{type:"function", function:{name,description,parameters}}`。脚本返回的 `context` 需要反解析成 Tool；未知工具（这里是 `context`）应生成 **no-op Tool**，否则下一轮 provider 可能因为 tools 中出现未注册的 name 而拒绝请求。
- `context` 没有参数、没有执行语义；no-op execute 返回空 parts 或固定字符串均可，但必须存在。
- 调用前最好先检查 `system` 文本含 `<run_tool_replacement>` 且数量/角色正确；不满足时直接跳过，不要调用后依赖 catch（脚本内部没有 try/catch）。
- context description 在本预设中约 **3782 字符**（见下）；注意某些 provider 对 tool description 长度有限制，必要时记录/截断诊断（但不要截断实际语义）。

## 5. OutputPreprocessing + formatter 的组合语义

建议顺序（也是本预设需要的行为）：

1. 取最后一条 assistant 的文本 buffer；
2. 若 `consumeToolCalls=true` 且 tool parts 尚未格式化：
   - 用 `toolCallFormatter` 把 tool calls 转成文本（`formatted`）；
   - 将 `buffer` 与 `formatted` 组合成最终 buffer。两者都存在时用 `\n` 分隔，避免直接粘连；
3. 用 `{buffer, final:true, state}` 调 `OutputPreprocessing.script`；
4. 若返回 `output` 字段，用其替换 assistant 的文本；
5. 若 `consumeToolCalls=true`，移除 Tool parts（已被格式化吸收）；
6. `hold` 非空表示尾部暂不展示；本预设 `hold=""`，无保留；
7. `state` 应在流式分片间持久化；本预设只扩展 `accepted:true`。

本预设脚本是 identity（`output=buffer`），但 `console.log` 和 `state` 仍必须执行。Kotlin 若只在“原本已有 Text part”时才调用 output script，会漏掉纯 tool-call 消息；若先跑 output script、再格式化 tool calls，则 formatter 生成的文本不会经过 output script。对本预设（identity）不会改变可见文本，但属于通用语义不完整。

## 6. ChatSquash.squashed_post_script 实测

脚本是 `content => {...}`，必须直接传字符串，不是 `{content}`。

### 已处理的 marker

- `|用户：名字|`：提取名字作为后续 user role 名，并从正文删除。
- `|小猫之神|` → `\n"}, {"role": "soliumbra", "content": "\n`。
- `|用户|` → `\n"}, {"role": "<用户名或 用户>", "content": "\n`。
- `|delete|...|/delete|`：整段删除（含内容）。
- 独立 `<summary>`：只处理“不是紧跟 `<details>`”的标签，且只处理前面 `N-3` 个（最后 3 个不处理），在每个 `<summary>` 后插入 `\n---第N段剧情总结---`。
- 返回类型始终是 **string**。

例子：

```text
输入: |用户：爱丽丝|\n|小猫之神|猫A|用户|人B|delete|gone|/delete|尾巴
输出: \n"}, {"role": "soliumbra", "content": "\n猫A\n"}, {"role": "爱丽丝", "content": "\n人B尾巴
```

### 不处理的 marker / 已知 bug

- 不处理 `<|sep|>`、`<|merge|>`、`|游戏剧情|`、`|小明|`，这些原样保留。
- `|小猫之神|` 后接 `|用户|` 时本应插入 `<end>`，但脚本里的 `const prefix = ...` 算了却**没有使用**；实测不会出现 `<end>`。
- 没有 `|用户：名字|` 时默认 role 是 `用户`，不是实际用户名。
- 紧跟 `</details>\n` 的独立 `<summary>` 会被误判为 details 内部（只回看 10 个字符的 `details>` 检查），从而少处理一个。保留这个预设 bug，不要“修正”成不同输出。
- 少于 4 个独立 `<summary>` 时不会插入任何剧情总结标记。
- `|delete|` 正则匹配 `[\s\S]*?`，跨行删除；不支持嵌套。

## 7. ChatSquash role / prefix / suffix / separator 的权威语义

依据 starowo/SPreset 公开版 1.0.0 `inject.js:1025-1136`（`squashPrompts`）和 `1138-1209`（`postProcess`）：

- `squashRole` 初始为 `settings.role`；`role="follow"` 时初始为 `prompts[0].role`。
- 遍历每个 prompt：
  - 若 `enable_squashed_separator` 且内容包含 separator（字符串或 regex）：
    - 删除该 prompt 中**一个** separator；
    - 先 flush 当前合并块为 `{role: squashRole, content: postProcess(merged)}`；
    - 然后把这个 separator 命中的原 prompt **原样（保持原 role）** push 到输出；
    - `role="follow"` 时把 `squashRole` 重置为这个 prompt 的 role。
  - 否则：
    - role 变化时，给上一段追加 `suffix_<oldRole>`，给新段加 `prefix_<newRole>`；
    - role 相同则只加 `\n`；
    - 追加 content；
    - 最后 flush 时追加 `suffix_<lastRole>`，始终以 `squashRole` 输出。
- 因此 `role="assistant"` 会把每个合并块都变成 assistant；只有 separator 命中的 prompt 保持原 role。**不能**用它直接得到 system/assistant/user 三条。
- `user_role_system=true` 会在合并前把 system prompt 的 role 改成 user，再按 user prefix/suffix 处理。
- `parse_clewd=true` 走 `HyperPmtProcess`（regex 指令、`<|curtail|>`、`<|join|>`、`<|space|>`、clewd 转义等），且 `<|no-trans|>` 会触发 flush。
- `separate_chat_history=true` 在 `squashPrompts` 外层处理：只对 `chatHistory` collection 调 squash，其余 prompt 原样保留；本预设为 false。
- `re_split` 不在公开 1.0.0 中，本预设为 false，没有可验证语义。

### 旧版算法在“小猫之神”上的实际模拟

按 app 语义构造：

- prompt_order 中 enabled 的 28 条系统注入：8 条在 `chatHistory` 前 → 合并进第一条 system；20 条在 `chatHistory` 后 → 作为 AFTER_DIALOG system 消息，插在最后一个 assistant 之后、最后一个 user 之前。
- 代表消息角色：
  ```text
  system, user, assistant, user, assistant, system, user
  ```
- 第一条 system 同时包含 `<|sep|>`、`<|merge|>`、`<run_tool_replacement>`。

跑旧版 `squashPrompts`：

- 输出 **2 条**：`[system, assistant]`
- separator 命中第一条 system，system 保留（只删 `<|sep|>`，`<|merge|>` 仍在）
- 其余所有消息被合成一个 assistant
- 再跑 FinalRequestProcessing：**throw** `预期消息为三条：system → assistant → user`

结论：**旧版 `squashPrompts` 单独无法兼容这个预设**，必须实现新的 conditional 路径。

## 8. 推荐的新 conditional 合并算法（可让 FinalRequest 成功）

基于预设脚本和实际消息形状，推荐：

1. 若 `conditional_enabled=true`，先检查消息中是否存在 `conditional_tag`（本预设 `<|merge|>`）；不存在则**不 squash**（不要退回旧版）。
2. 找到含 tag 的 system 消息（本预设是第一条 system；若不在第一条，需要把前面的消息一起折叠并保证最终 system 是承载 `<run_tool_replacement>` 的那条）。
3. 从该 system content 中删除所有 `<|merge|>` 和 `<|sep|>`，保留 `<run_tool_replacement>`；trim 可选。
4. 找到**最后一条真实 user 消息**（`role=user` 且文本非空）；它作为最终 user，原样保留。
5. 把其余所有非第一条 system 消息（包括 marker 之后、最后 user 之前，以及**最后 user 之后可能存在的 AFTER_DIALOG system 消息**）按原顺序折叠为一条 assistant 文本：
   - 每个消息按 effective role（`user_role_system` 可把 system 变 user）套 `prefix_* + content + suffix_*`；
   - 或者按旧版 role 变化规则加 prefix/suffix；本预设历史角色交替，两者输出几乎相同（实测 3782 vs 3785 字符，首尾一致）。
6. 对整段 assistant 文本执行 `squashed_post_script`（一次），得到最终 assistant content。
7. 输出严格三条：`[system, assistant, lastUser]`。
8. 再调用 `FinalRequestProcessing`，得到 2 条 messages + 原有工具 + `context`。

### 模拟结果（默认多轮历史）

- 输入：7 条，角色 `system,user,assistant,user,assistant,system,user`
- 推荐合并输出：3 条，角色 `system,assistant,user`
- assistant 文本长度约 **3782**（旧版 transition 语义）/**3785**（当前 Kotlin 逐条 prefix/suffix 语义）
- 开头：
  ```text
  |小明|\n"}, {"role": "用户", "content": "\n喵喵喵——...
  ```
- 结尾：
  ```text
  ..."}, {"role": "soliumbra", "content": "好喵！现在要继续游戏~ 调用工具喵~
  ```
- 包含 `"role": "用户"` 和 `"role": "soliumbra"`；不包含 `|小猫之神|`、`|用户|`、`<|sep|>`、`<|merge|>`
- 仍保留 `|小明|` 和 `|游戏剧情|`——因为 post script 不认识它们（这是**预期**的预设输出，不是 Kotlin 的 bug）
- 没有 `|用户：小明|` 从块内出现，所以 role 是默认的 `"用户"`，不是 `"小明"`

再跑 FinalRequest：

- messages：2 条 `[system(去掉 marker), user]`
- tools：`game_content`、`context`
- context.description = 上面 3782/3785 字符的完整 assistant 文本
- 结果：**成功**，不再 throw

### 首轮边界情况（重要）

如果历史为空，app 的 AFTER_DIALOG 注入会落在最后一条 user **之后**：

```text
输入角色: system, user, system
```

当前 Kotlin `applyConditional`（SpresetChatSquashEngine.kt:57-67）只折叠 `markerIndex+1 .. lastUserIndex-1`，因此：

- foldedCount = 0
- assistant content = `""`
- 最终 `[system, 空 assistant, user]`
- FinalRequest context.description 为空 → 20 条 AFTER_DIALOG 提示全部丢失

**修复建议**：不要用 `lastUserIndex` 作为折叠右边界；应保留 `lastUser` 单独作为最后一条，把**其余所有消息**（包括 lastUser 之后的 system）按原顺序折叠进 assistant。按此修复后首轮 assistant 约 3517 字符，FinalRequest 成功。

## 9. 对 Kotlin 集成的明确建议

### 9.1 解析与告警

- `SpresetCompat.supportedKeys()` 必须返回：
  ```text
  ChatSquash, RegexBinding, MacroNest, ToolBindings, MessageInjections,
  OutputPreprocessing, FixedPresetName, ForcedPostProcessing, FinalRequestProcessing,
  version, enabled
  ```
  这样 `ExportSerializer.detectUnsupportedPresetExtensions` 的 `unsupportedKeys` 对真实文件为空，导入 toast 不再出现 `SPreset(...)`。
- `parseActivePreset`：`enabledGroups` 非空按启用组；为空才用非空 `FixedPresetName` 回退。本预设 `FixedPresetName=""`，所以没有启用组时返回空配置是合理的。
- `ForcedPostProcessing.mode=""` 必须当作“未配置”，不能覆盖用户已有 post-processing。
- ToolBindings / MessageInjections 同时支持 map（UUID key）和 array 形态；本预设是 map。
- `form.parameters` 是数组形态 `[{name,type,description,required:true}]`；不要直接当 JSON Schema 传，要转成 `{type:"object",properties,required}`。缺失 form 时才执行 code 解析。

### 9.2 动态工具

- 只为 `enabled=true` 的 binding 创建 Tool：本预设只有 `game_content`。
- `game_content` schema：
  ```json
  {"type":"object","properties":{"content":{"type":"string"}},"required":["content"]}
  ```
  description：`MANDATORY, You MUST use this tool to reply.`
- 工具名要做非法字符清理，并与 existingNames 冲突时跳过；本预设名字合法。
- `action`：优先执行 binding.code（`new Function(code)()`，注意 code 是函数体，不是表达式），调用返回对象的 `action(args)`；args 是 JSON.parse 后的对象；结果字符串直接返回，对象 JSON.stringify。code 缺失/失败时回退 `new Function('args', actionBody)`。
- 本预设 actionBody 返回 `{"ok":true}`，不需要额外业务逻辑。
- `stealth=false`，normal 工具即可。

### 9.3 formatter / output preprocessing

- formatter 调用参数必须是 `{ calls }`，且 `calls[].arguments` 是解析后的对象。
- 只在 `toolCallsAlreadyFormatted=false` 时格式化 Tool parts；格式化后移除 Tool parts。
- 纯 tool-call 的 assistant 消息也要执行 output preprocessing（当前 Kotlin 只对含 Text part 的消息执行，见 SpresetCompat.kt:268；identity 脚本下可见文本不受影响，但通用语义不完整）。
- 组合文本和格式化结果时避免直接拼接：两者都非空用 `\n`。
- QuickJS 必须安装 console shim（`console.log` 会被调用）；脚本异常必须捕获并保留原文。
- `OutputPreprocessing.script` 返回对象，要解析 `output`、`hold`、`state`，不要当字符串直接使用。
- 流式路径：`SpresetStreamEngine` 在 Finish 时先对纯文本 run final output，再把 formatter 文本单独发出；本预设 identity 没问题，但通用脚本会漏掉“formatter 文本的最终预处理”，且文本可能被处理两次。可接受风险/后续修复。

### 9.4 FinalRequestProcessing

- 输入至少 `{messages, tools}`；按 OpenAI 形状序列化。
- 输出要替换 messages 和 tools 两者；原有 tool 按 name 复用，未知工具（`context`）生成 no-op Tool。
- 必须在 squash 之后、真正发请求之前调用；调用前检查严格三条 system/assistant/user + system 含 marker，不满足则跳过（脚本内部会 throw）。
- 返回的 context.description 可能很长（本预设约 3.8KB），注意 provider 限制和日志脱敏。
- `context` tool 无参数、无实际执行；注册 no-op 即可。否则 provider 侧可能出现 “tool not found”。
- 不要尝试把 post script 输出解析成消息数组；它只是 context 的文本描述，里面的 `"role": "soliumbra"` 不是合法 RikkaHub role，绝不能当消息 role。

### 9.5 ChatSquash

- 旧版 `squashPrompts` 只能作为 legacy 路径；本预设必须走 conditional 路径。
- conditional 路径要在 `conditional_tag` 不存在时**跳过 squash**，不要 fallback 到 legacy（否则可能产出 2 条并让 FinalRequest throw）。
- 折叠时必须处理 lastUser 之后的 system 消息（首轮边界）。
- 只合并纯文本消息；工具/图片在 legacy 路径保留。conditional 路径若要折叠历史中的 tool/image，需明确策略；本预设历史是纯文本。
- `squashed_post_script` 必须直接传字符串；输出是 string，不要再 JSON.parse 成对象。
- 逐条 prefix/suffix 与旧版 role-transition prefix/suffix 在本预设上几乎等价；若追求 legacy 精确兼容，legacy 路径用 role-transition 规则。

### 9.6 MessageInjections / prompt_order 重复注入

- 本预设两个 `MessageInjections` 都 disabled 且内容为空；`applyMessageInjections` 不会生成额外消息。
- 但对应 `prompts[]` 中的 91918（“预填充”）在 prompt_order 里 **enabled=true**，会通过普通 prompt 注入进入 AFTER_DIALOG system 块（我们模拟的 afterDialog 末尾就是它的 `"}, {"role": "soliumbra"...`）。不要再按 MessageInjections 生成一份，否则会重复。
- 6e9（“user尾”）在 prompt_order 中 disabled，不出现。
- 如果未来 MessageInjections 有 reasoning/toolCalls 内容，应用“附加到已有 prompt 消息”而不是重新创建同内容消息。

### 9.7 当前实现复核（只读，未改源码）

发现的可改进点（按影响排序）：

1. **首轮 trailing system 被丢弃**：`SpresetChatSquashEngine.kt` 的 `applyConditional` 右边界是 `lastUserIndex`；当 AFTER_DIALOG system 落在 lastUser 之后时（首轮），这些系统提示被排除，assistant 为空，context description 为空。建议改为“最后 user 单独保留，其余所有消息折叠”。
2. **conditional 失败回退 legacy**：`apply()` 中 `conditional ?: applyLegacy(...)`；当 marker/tag 缺失或形状不满足时，legacy 可能产出 2 条 [system, assistant]，随后 FinalRequest throw。建议 conditional_enabled=true 时失败就返回原消息或跳过 squash，不 fallback legacy。
3. **output preprocessing 顺序**：`SpresetCompat.finalizeAssistantOutput` 先对 Text parts 跑脚本，再格式化 Tool parts；纯 Tool 消息不跑脚本；格式化文本未进入 output 脚本。本预设脚本 identity，用户可见文本不变；但通用 SPreset 兼容性应合并 buffer 后统一处理。
4. **流式 final 输出重复/绕过**：`SpresetStreamEngine` 在 Finish 先对 rawBuffer 跑 final output，再单独发 formatter 文本；本预设 identity 没问题，通用脚本可能双重处理或漏处理 formatter 文本。
5. **conditional 逐条 prefix/suffix** vs 旧版 role-transition：本预设角色交替，两种结果首尾与长度几乎一致；若之后出现连续同角色消息，输出会有差异（逐条会重复 prefix/suffix）。建议 legacy 路径严格按旧版 role-transition，conditional 路径如果按新语义逐条则写注释说明。
6. **MessageInjections 与 prompt_order 不要双注入**：当前实现 `applyMessageInjections` 只处理 enabled 项（本预设无），不会重复；保持这个行为即可。
7. `context` no-op Tool 已实现（SpresetCompat.kt `buildNoopTool`），符合验证结果。
8. `supportedKeys()` 已包含六个键，导入告警应消失。

## 10. 剩余风险 / 未证实项

- 公开 1.0.0 源码没有 `conditional_enabled`、`conditional_tag`、`ToolBindings`、`MessageInjections`、`OutputPreprocessing`、`FinalRequestProcessing`；这些键的语义来自本预设脚本、实际运行形状和合理推断，不是公开源码逐行核对。
- `re_split` 在公开版没有语义，本预设为 false；不要猜测实现。
- post script 输出故意保留 `|小明|` / `|游戏剧情|`，并生成 `"}, {"role": ...` 片段；它是给模型的文本上下文，不是合法 JSON，也不是 RikkaHub 消息数组。
- post script 中 `|用户：名字|` 的提取依赖该 marker 出现在被合并块内；本预设的 `|用户：{{user}}|` 在第一条 system 注入里，不在被折叠块中，因此实际 role 名默认是 `用户`。若希望输出真实用户名，需要在折叠块中包含该 marker，或在合并前替换；这是预设语义限制，不是 Kotlin 可单方面修复的。
- `context.description` 约 3.8KB，且包含大量引号/JSON 片段；部分 provider 可能限制 tool description 长度或拒绝含特殊字符的 description。
- 首轮没有历史时 AFTER_DIALOG 注入位置在 app 层就落在 user 之后；conditional 合并必须把它移入 assistant 块。否则丢失 20 条提示。建议给该场景加 regression test（本目录已用 fixture 模拟）。
- `FinalRequestProcessing` 强依赖最终严格 3 条消息；任何上游 transformer/工具消息/多 user 场景都可能破坏。建议在调用前显式校验并记录诊断。

## 11. 文件说明

- `semantics.test.mjs`：50 个 node:test 断言（结构、脚本执行、边界、旧版 squash、conditional 模拟、首轮）。
- `lib/spreset.mjs`：加载真实预设、vm 执行脚本、form→JSON Schema、QuickJS 语义辅助、旧版 `squashPrompts` 精确移植、conditional 合并建议实现。
- `inspect.mjs`：打印六字段结构、实际脚本输出、formatter/output/finalRequest 前后。
- `simulate.mjs`：app 形状消息 + 旧版 squash vs conditional 合并 + FinalRequest。
- `current-kotlin-preview.mjs`：当前 Kotlin conditional 算法的 JS 等价预览（用于复现首轮空 context 问题）。
- `fixtures/spreset-cat-god.json`：自包含 fixture；`fixtures/extract.mjs` 可重新生成。
