# 输入归档清点与证据边界 (INPUT_MANIFEST)

## 1. 五个输入源码归档清单与校验信息

| 序号 | 逻辑名称 | 实际文件路径 | 字节大小 | SHA-256 | 顶层目录与版本核定 | 许可证 |
|---|---|---|---:|---|---|---|
| 1 | RikkaHub `2.5.6` | `user_uploaded_files/ttl=63d/user_uploaded/rikkahub-2.5.6.zip` | 47,007,603 (45M) | `76eacc19336828d9ab9d2555dc502b4d7e9af4ed8d6923f420e3468796cba220` | `rikkahub-2.5.6/`（上游基线） | GPL-3.0 (`LICENSE`) |
| 2 | `rikkaST main` | `user_uploaded_files/ttl=63d/user_uploaded/rikkaST-main.zip` | 57,572,271 (55M) | `f2e434c93ee93c966e2acef59c6a1d75a50fd7cd12269079b9b649c523efe10b` | `rikkaST-main/`（修改目标工作树基线） | GPL-3.0 (`LICENSE`) |
| 3 | SillyTavern `1.18.0` | `user_uploaded_files/ttl=63d/user_uploaded/SillyTavern-1.18.0 (1).zip` | 38,546,204 (37M) | `9ff16405d8df12679049f238292cb313e6c59676bc268fb639e1db1e8d74fdc2` | `SillyTavern-1.18.0/` (`package.json` `"version": "1.18.0"`) | AGPL-3.0 (`LICENSE`) |
| 4 | SillyTavern `1.19.0` | `user_uploaded_files/ttl=63d/user_uploaded/SillyTavern-1.19.0.zip` | 38,619,144 (37M) | `19f34009d55d4e2dd394ce9d6dd6e5d1b0e5b9e5d57df8b5aa325a44c18969be` | `SillyTavern-1.19.0/` (`package.json` `"version": "1.19.0"`) | AGPL-3.0 (`LICENSE`) |
| 5 | 五扩展合集 `all-needs.zip` | `user_uploaded_files/ttl=63d/user_uploaded/all-needs.zip` | 61,510,598 (59M) | `c4169d83cbbfd582daf49de7b973bf2bb5b965fad714ef8a4ebf918f0372a665` | 含 5 个扩展 ZIP（见下表） | 各扩展独立许可证 |
| 6 | 迁移计划文档 | `user_uploaded_files/ttl=63d/user_uploaded/RikkaST_ST119_Plugin_Migration_Plan_5Inputs.md` | 70,582 (69K) | `b968b5050b7eb9fa9e0a4ce2ce36f2c0fb0bcf7a9bebacffe74fda909422c168` | Markdown 规格说明 | - |

## 2. `all-needs.zip` 内部五个扩展子包明细

| 扩展名称 | 内部归档文件名 | 解压顶层目录 | `manifest.json` 声明版本 | 入口脚本 (`js`) | 关键特性 |
|---|---|---|---|---|---|
| `ST-Prompt-Template` | `ST-Prompt-Template-1.16.zip` | `ST-Prompt-Template-1.16/` | `1.16.0` | `dist/index.js` | EJS 模板求值、变量注入、`CHAT_COMPLETION_SETTINGS_READY` / `CHAT_COMPLETION_PROMPT_READY` 异步钩子 |
| `JS-Slash-Runner` (Tavern Helper) | `JS-Slash-Runner-main.zip` | `JS-Slash-Runner-main/` | `4.11.3` | `dist/index.js` | iframe 沙箱、酒馆事件/变量/世界书桥接、`/api/backends/chat-completions/generate` 流式与非流式调用 |
| `st-memory-enhancement` | `st-memory-enhancement-2.2.0.zip` | `st-memory-enhancement-2.2.0/` | `2.2.0` (`package.json`: `1.0.21`) | `index.js` | 记忆表格管理、`CHAT_COMPLETION_PROMPT_READY` 消息数组插入、世界书导入导出 |
| `LittleWhiteBox` (小白X) | `LittleWhiteBox-3.1.7.zip` | `LittleWhiteBox-3.1.7/` | `3.1.7` | `index.js` | 多模块任务/生图/剧场/变量面板、`/user/files/*` 与 `/api/files/{upload,delete}`、配套服务端插件 `server-plugin/littlewhitebox-server` (`1.2.1`) |
| `ST-iPhonie` | `ST-iPhonie-main.zip` | `ST-iPhonie-main/` | `0.8.15` | `index.js` | `messageFormatter` 三阶段钩子、`generate_interceptor: stIphonieInterceptor`、`hooks: {enable, disable, delete}`、`minimum_client_version: 1.19.0` |

## 3. Node.js 版本核定与证据边界

1. **上游 SillyTavern `1.18.0` 与 `1.19.0` 的 `package.json` 核定**：
   - `SillyTavern-1.18.0/package.json` 与 `SillyTavern-1.19.0/package.json` 均声明 `"engines": { "node": ">=18.0.0", "npm": ">=8.0.0" }`。
   - `LittleWhiteBox-3.1.7/server-plugin/littlewhitebox-server` 使用 `node:stream/promises`、`node:http`、`node:https`、`node:crypto`、`Readable.toWeb`、`fetch`、`Headers`、`Response`、`AbortController` 等 Node 18+ 标准内置 API（自带 `tests/agent-core-node18-compatibility.js`）。
   - 为满足长期支持与兼容性要求，共享 Linux Workspace 运行时采用官方预编译 **Node.js LTS `v20.18.1`**（兼容 `>=18.0.0` 且满足 Node 20 LTS 要求），针对 `linux-arm64` 与 `linux-x64` 提供官方 SHA-256 校验表，并在当前沙盒（`Node.js v18.20.4`）完成全部 Node 侧自动化验证。
2. **源码归档保护**：
   - 原始 5 个 ZIP 包保留在 `user_uploaded_files/ttl=63d/user_uploaded/` 未作任何修改。
   - 只读解压树位于 `/tmp/st_audit/input/`；所有代码改动均在独立工作副本 `/tmp/st_audit/work/rikkaST-main/` 中完成。
