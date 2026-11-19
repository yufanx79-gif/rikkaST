# 酒馆插件生态实测审计（2026-11-18）

> 本文回答一个具体问题：**「兼容所有插件」这件事，目前做到了哪一步，缺口在哪，怎么继续。**
>
> 与 `ST-COMPATIBILITY.md` 的区别：那份是**代码结构**的静态描述，这份是**真实插件跑起来**的实测结果。
> 所有数字都可用 `dev-scripts/` 下的脚本复现（脚本 gitignored，命令见文末）。

---

## 0. 一句话结论

> 38 个真实第三方插件（1 个是酒馆助手本体）里 **37 个能在隔离沙箱中完整加载**（`37/38`），
> 剩余 1 个的失败原因是**插件自己的打包产物缺变量**，在任何 ST 环境下同样会崩，不是我们的缺口。
>
> 更重要的是：**「能加载」不等于「能用」**。本次审计建立了三层检查，
> 分别回答「名字在不在」「调用崩不崩」「实现是不是空的」。
>
> 而 L3 之后还有一层更隐蔽的：**「实现是真的，但你拿到的不是那个实例」**。
> 见 §4.3 —— 名字全对、语义全对，插件却一条事件都收不到。

---

## 1. 三层审计模型

只看一层会得出错误结论，本次实测每一层都踩过坑：

| 层 | 问题 | 方法 | 结果 |
|---|---|---|---|
| **L1 导出面（供给）** | ST 官方导出的符号我们抄全了吗 | `_st_export_inventory.mjs`：解析 ST 1.19.0 全部 `public/scripts/**/*.js` 的 export，与对应 shim 的运行时 `Object.keys()` 比对 | ST 导出 **1331** 个符号，缺 **4** 个；无 shim 的模块 **0** |
| **L2 导出面（需求）** | 插件真正 import 的符号我们有吗 | `_export_surface_audit.mjs`：扫所有插件的 `import { ... }`，解析出 258 个不同符号 | 缺失 **0** |
| **L3 语义面** | 有名字，但实现是不是空的 | `_runtime_ctx_test.mjs` + `_api_runtime_probe.mjs`：真加载 `runtime.js`，用 Proxy 记录插件实际访问的成员 | 见 §3 |

**关键教训（写下来避免重复踩）**：
- 静态正则审计 **不可靠**。试图用 `ctx\.(\w+)\(` 推断 API 调用时，误报率 > 90%
  （canvas 的 `ctx.beginPath`、three.js 的 `mesh.animate`、压缩代码里的局部变量全被算成 ST API）。
  **必须用 Proxy 在运行时记录**，才能得到零误报的依赖面。
- 「名字存在」不代表「能用」。`SlashCommandParser: { commands: {} }` 在 L1/L2 全绿，
  但插件一读 `.commands['echo'].callback` 就崩。**必须有 L3**。

---

## 2. 官方模块与内置扩展覆盖

### 2.1 ST 官方 14 个内置扩展

ST 1.19.0 `public/scripts/extensions/` 共 14 个内置扩展（不含 third-party）：

`assets` `attachments` `caption` `connection-manager` `expressions` `gallery` `memory`
`quick-reply` `regex` `stable-diffusion` `token-counter` `translate` `tts` `vectors`

文件层面 **63/63 = 100%** 有对应 shim，**导出符号 0 缺失**。

> ⚠️ 但要注意：这是「文件与导出面对齐」，不等于「行为等价」。
> 例如 `extensions/tts/index.js` 我们只有 340 行（ST 原版 1622 行），
> 采用 **DIVERGENCE 策略**：不复刻 30+ provider 子模块，而是把 ST 的公开导出
> （`getCharacters` / `sanitizeId` / `getPreviewString` / `registerTtsProvider` …）
> 做成真实实现，朗读本身交给宿主的 `TtsController`。这类文件里的 DIVERGENCE 注释要保留。

### 2.2 getContext() 语义面

`getContext()` 是插件最常用的入口（比具名 import 更常用）。
ST 1.19.0 `scripts/st-context.js` 返回对象有 **146 个顶层键**。

| 指标 | 数值 |
|---|---|
| ST 顶层键 | 146 |
| 我方覆盖 | **146（100%）** |
| 我方额外（rikkaST 私有） | 17（`sendMessage` / `triggerSlashAsync` / `renderMessageFloors` …） |

---

## 3. 真实插件的运行时依赖面（本次实测数据）

用 Proxy 记录 38 个插件实际访问的 `getContext()` 成员，按被依赖次数排序（前 20）：

| 次数 | 成员 | 次数 | 成员 |
|---|---|---|---|
| 37 | `streamingProcessor` | 27 | `eventSource` |
| 37 | `userAvatar` | 27 | `extensionSettings` |
| 37 | `world_info` | 25 | `eventTypes` |
| 37 | `world_names` | 24 | `chat` |
| 37 | `chatMetadata` | 24 | `characters` |
| 37 | `characterId` | 23 | `name1` / `name2` |
| 13 | `POPUP_RESULT` | 10 | `groups` |
| 13 | `POPUP_TYPE` | 10 | `selected_group` |
| | | 10 | `is_group_generating` |

**这张表就是兼容性优先级**。前 12 项被 23+ 个插件依赖，任何一项缺失都会造成大面积失败。

---

## 4. 本轮发现并修复的真实缺口

全部有真实插件报错作为证据，不是推测：

### 4.1 宿主页资产缺失（3 项）

| 缺口 | 插件报错 | 修法 |
|---|---|---|
| **showdown 真库** | LALib: `showdown is not defined` | 从上游 ST 1.19.0 的 `node_modules` 拷入 v2.1.0 真库（MIT），加载顺序在桩之前 |
| **`generic_draggable_template`** | TopInfoBar: `reading 'cloneNode'` | 按 `public/index.html:6434` 原文补模板 + `NULL_SELECTOR` |
| **`extensions.js` 少导出** | ZerxzLib: `reading 'get'` | 补 `saveSettingsDebounced` 兼容转发（上游不导出，但生态里有插件这么用） |

### 4.2 `SlashCommandParser.commands` 是空对象（最隐蔽）

**症状**：SillyTavern-Roadway 加载失败 `Cannot read properties of undefined (reading 'commands')`

**根因**：我们写的是 `SlashCommandParser: { commands: {} }` —— 名字在、内容是空。
插件（Roadway）每次打印提示都调
`getContext().SlashCommandParser.commands['echo'].callback(...)`。

**ST 契约**（`SlashCommandParser.js`）：
- `:44` `static commands = {}`
- `:95` 写入命令名，`:99` **每个别名也写入**
- `:105` `get commands()` 直接返回该静态表

**修法**：接上 101 条真实命令表，两个来源合并：
- 宿主内置命令（`echo`/`toast`/`send`/`sys`/`pass`…）→ `callback` 走 `stCtx.triggerSlashAsync`，
  由 Kotlin `StSlashExecutor` 真正执行
- 扩展 `addCommandObject`/`addCommand` 注册的命令 → 从 `window.__rikkaStSlashRegistry` 同步

**效果**：Roadway 从「加载失败」→「加载成功」，访问 68 个真实 context 成员。

---

### 4.3 `script.js` 导出的 `eventSource` / `event_types` 是死的（身份分裂）

**这是本轮最隐蔽的一个缺口**，前三层审计全绿也发现不了。

**症状**：43 个插件文件从 `script.js` 导入 `eventSource` / `event_types`，
它们的事件订阅**全部静默失效**。不报错、不崩溃，就是永远不触发。

**根因**（ES 模块求值顺序）：

```js
// runtime.js:29
import * as stCompatScript from './st-compat/script.js';
// runtime.js:2472
window.SillyTavern = SillyTavern;      // ← 在 29 行之后才执行
```

被 import 的模块**先跑完模块体**。所以 `script.js` 求值时 `window.SillyTavern` 还是
`undefined` → `st()` 返回 `{}` → 旧代码 `const ES = st().eventSource || host().eventSource`
拿到 `undefined` → **手写 fallback 被永久固化成导出值**。

实测数据（修复前）：

| 符号 | 实测 | 真值 |
|---|---|---|
| `script.js` 的 `eventSource` | 手写 fallback（`events` 为 `undefined`，`emit`/`on` 全是空函数） | 真 EventEmitter 单例 |
| `script.js` 的 `event_types` | **0 个键** | **104 个键** |

**为什么前三层查不出来**：符号存在（L1/L2 绿）、`typeof` 是 `function`（L3 绿）、
`makeFirst`/`makeLast`/`once` 名字都在。问题在于**你拿到的那个对象是另一个**。

**修法**：对齐上游 `public/script.js:278` 的写法，直接转发真单例，不在模块求值期取值：

```js
export { eventSource, event_types } from '../vendor/st/events.js';
```

`vendor/st/events.js` 用 `globalThis.__rikkaSharedEventSource` 保证多路径同一实例，
因此转发后与 `getContext().eventSource` 是**同一个对象**。

**验证**（`_eventemitter_contract.mjs`，真加载 `runtime.js`，22 项断言全过）：

- 身份：`script.js` / `st-compat/events.js` / `getContext().eventSource` 三者 `===`
- `makeFirst` 排到最前、`makeLast` 排到最后，且重复调用不产生重复监听（Roadway 依赖）
- `once` 只触发一次（LALib / DeepLore / ExtensionManager / TopInfoBar 依赖）
- `removeListener` 精确摘除（27 处使用，最常用的清理手段）
- `autoFireAfterEmit`：`APP_READY` emit 之后**晚订阅**的监听器立刻收到最后一次参数
- `emit` 串行 await async 监听器（宿主生成链依赖顺序确定）
- 单个监听器抛错不影响后续监听器

**顺带修正一个认知**：`vendor/st/eventemitter.js` 是上游 `public/lib/eventemitter.js`
的**逐字副本**，只改了 `emitAndWait`（上游不 await，我们改成真 await）。
`makeFirst` / `makeLast` / `once` / `removeListener` / `events` / `autoFireAfterEmit`
**从来就不缺**。上一轮把它列为「TOP 优先级补实现」是误判 —— 缺的是**证明**，不是代码。

**同时删除我们自己编的 API**：旧 fallback 里写了 `clearEvent` / `clearListener` /
`clearAll` / `waitForEvent` / `emitAndWaitAsync`，声称是「eventemitter3 扩展」。
实测：上游 ST **没有**这些方法，38 个插件里**也没有任何真实调用**。
它们是凭空捏造的接口，已随 fallback 一并删除。

---

## 5. 测试台保真度修正（这些曾让我误判）

审计工具本身也会骗人。本轮修掉的 4 个「假失败 / 假通过」：

| 问题 | 表现 | 修正 |
|---|---|---|
| 相对 import 基准错误 | LALib 报缺 `extensions.js`（其实有） | 基准改为**发起 import 的文件**（`context.parentURL`），不是插件入口 |
| DOMPurify 退化成工厂 | EchoChamber: `DOMPurify.sanitize is not a function` | DOMPurify 3.x 需要 `window.document.nodeType===9` 且 `window.Element` 才是完整实例 |
| `<select>.options` 缺失 | WorldInfo-Recommender: `reading 'length'`（jQuery 内部） | mock 节点补 `options` |
| `<template>.content` 缺失 | TopInfoBar: `reading 'cloneNode'` | mock 的 template 元素补 `content`（DocumentFragment） |

**最严重的一次**：`_api_runtime_probe.mjs` 早期用手写 `realCtx`，
它「看起来什么都有」，因此**验证不出 `runtime.js` 里的真实缺口** —— Roadway 的
`SlashCommandParser` 问题就是被它掩盖的。改为**真加载 `runtime.js`** 后才暴露。

---

## 6. 剩余失败（1 个，已定性为插件自身缺陷）

修复 §4.3 之后，`ExtensionManager`（`reading 'includes'`）与
`PromptInspector`（`Events not found.`）**都已通过** —— 它们的失败根因就是拿到了死事件总线。

| 插件 | 报错 | 定性 |
|---|---|---|
| `SillyTavern-Extension-ZerxzLib` | `Cannot read properties of undefined (reading 'get')` | **插件打包产物自身缺陷**：`dist/zerxzLib.js` 里 `bn.get()` 被调用 12 次，但 `bn` **从未定义**（`grep 'bn='` 命中 0 次）。这是它自己的状态管理对象，与 ST API 无关，在真酒馆里同样会崩 |

> 判定依据：`bn` 是 bundle 内部压缩符号，不是任何 ST 导出。所以这不是「我们少给了什么」，
> 而是「它自己少打包了什么」。继续在测试台上追这个只会浪费时间。

---

## 7. 怎么复现

```bash
cd dev-scripts

# L1：ST 官方导出面 vs 我们的 shim
node _st_export_inventory.mjs

# L2：插件实际 import 的符号
node _export_surface_audit.mjs "<插件父目录>"

# L3-a：getContext() 顶层键覆盖
node _getcontext_audit.mjs

# L3-b：真加载 runtime.js，检查关键成员
node _runtime_ctx_test.mjs

# L3-c：单个插件的真实 API 依赖（Proxy 记录）
node _api_runtime_probe.mjs "<插件目录>" 1500

# 全量插件加载
node plugin_compat_lab.mjs "<插件父目录>"
```

---

## 8. 下一步优先级（按依赖次数排）

**已完成**（本轮）：
- ~~`eventSource` 的 `autoFireAfterEmit` / `makeFirst` / `events`~~ —— 实现本就在，已补 22 项行为断言，
  并修掉 `script.js` 的身份分裂（§4.3）
- ~~剩余 3 个失败插件定性~~ —— 2 个随 §4.3 自愈，1 个是插件自身缺陷

**待办**（按依赖次数降序）：

1. **`streamingProcessor` / `userAvatar` / `world_info` / `world_names`** —— 各被 37 个插件访问，
   名字都在，但**尚未用 L3 逐一验证语义**（即 §4.3 那类「名字在、对象不对/实现是空」的问题可能还有）
2. **`ConnectionManagerRequestService`** —— 3 个插件依赖，目前是 `{ handleDropdown: () => {}, sendRequest: async () => ({}) }` 空壳
3. **官方扩展的行为等价性**：14 个扩展文件都在，但 TTS 这类只有 340/1622 行，
   需要逐个确认 DIVERGENCE 是否影响插件调用
4. **把 L3 语义断言推广到全部高频成员** —— 本轮证明这是唯一能抓住 §4.3 类问题的手段

### 方法论沉淀：三层之后还有第四层

本轮新增的教训值得单独记一笔。前三层分别查「名字在不在」「调用崩不崩」「实现空不空」，
但 `script.js` 的 `eventSource` 三者全绿 —— 名字在、能调用、实现是真的 EventEmitter，
**只是那不是运行时用的那个实例**。

> **第四层：身份（identity）**。凡是从多条路径暴露同一资源的模块
> （事件总线、单例服务、共享状态），都必须断言 `a === b`，
> 而不只是断言 `typeof a.emit === 'function'`。

判据：如果一个符号在 ST 里是**单例**（`eventSource`、`extension_settings`、
`chat_metadata`、`characters`…），就要验证所有导出路径指向同一引用。

---

## 9. 附：8 个单测失败的根因（不是插件兼容问题，但必须记下来）

跑全量单测时固定有 **8 个失败**（`SettingsStoreTest` 7 个 + `ProviderAccountManagerTest` 1 个）。
此前一直记作「已知 Windows 基线」，但那个说法只说明「不是本轮改动引入的」，
**不等于「不是我们的问题」**。本轮查清了。

### 9.1 根因：Windows 上 `File.renameTo()` 不能覆盖已存在文件

最小复现（不含任何项目代码）：

```java
Files.write(Paths.get("src.tmp"), "new".getBytes());
Files.write(Paths.get("dst.bin"), "original".getBytes());

new File("src.tmp").renameTo(new File("dst.bin"));          // → false（静默失败）
Files.move(src, dst, REPLACE_EXISTING);                      // → true
Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING);         // → true
```

实测输出：

| 方式 | 覆盖已存在文件 |
|---|---|
| `File.renameTo()` | ❌ **false** |
| `Files.move(REPLACE_EXISTING)` | ✅ |
| `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` | ✅ |

DataStore 的 `FileStorageConnection.writeScope` 用的正是 `File.renameTo()`：

```kotlin
// androidx.datastore.core.FileStorageConnection.kt:114
if (!scratchFile.renameTo(file)) {
    throw IOException("Unable to rename $scratchFile to $file. ...")
}
```

**所以：第一次写入成功（目标文件还不存在），第二次写入必然失败。**
这不是「多实例争抢」（报错文案这么猜，但实际不是），而是 Windows 的 API 语义。

### 9.2 为什么只在 Windows 上炸

POSIX 的 `rename(2)` 规定可以原子覆盖目标文件，Linux/macOS 上 `File.renameTo()`
走的就是它，所以 CI 和 Android 真机都正常。Windows 的 `MoveFile` 默认不覆盖，
需要 `MoveFileEx` + `MOVEFILE_REPLACE_EXISTING`（即 `Files.move(REPLACE_EXISTING)`）。

### 9.3 为什么这不是产品 bug

**App 跑在 Android 上（Linux 内核），走 POSIX rename，不受影响。**
这 8 个失败只在「Windows 上跑 JVM 单测」这一种场景出现，是**测试环境的平台差异**。

### 9.4 要不要修 / 怎么修（留给后续决策）

三个选项，各有代价：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. 不修，记录清楚** | 在测试文档/CI 配置里标注「Windows 上预期 8 失败」 | 最省事，但每次跑测试都要人工判断这 8 个是不是新的 |
| **B. 测试里换临时目录到内存盘** | 用 `ImDisk`/`tmpfs` 挂载点跑测试 | 依赖外部工具，CI 不可移植 |
| **C. 给 DataStore 换 okio 后端** | 升到 `datastore-core-okio`（用 `FileSystem.atomicMove`，Windows 上会走 `Files.move`） | 改生产依赖，需回归验证；`datastore-core-okio` 1.2.1 已在依赖图里 |
| **D. 测试里包一层 rename 兼容** | 自定义 `Storage` 实现，rename 失败时 fallback 到 `Files.move` | 只影响测试，但要在测试里复制 DataStore 内部逻辑 |

**倾向**：短期选 **A**（把结论写死在这里，避免下次又当成谜团重查一遍）；
若要根治，**C** 是唯一同时修好生产与测试的方案，但需要单独一轮验证。

### 9.5 当前判定

**与本轮插件兼容工作无关，不是回归。** 判定依据：
- 干净 HEAD 上同样 8 个失败（此前已验证）
- 最小复现不含任何项目代码
- 根因在 `androidx.datastore` 的上游实现 + Windows API 语义

---

## 附：本轮数据文件

审计结果落在 `build/`（gitignored，可随时重跑）：
`st-export-inventory.json`、`export-surface-audit.json`、`getcontext-audit.json`、
`api-probe-all.json`、`api-probe-<插件名>.json`、`plugin-compat-lab.json`
