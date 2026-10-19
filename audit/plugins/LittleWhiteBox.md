# 插件专项审计：LittleWhiteBox / 小白X (v3.1.7) + littlewhitebox-server (v1.2.1)

- **目录**：`LittleWhiteBox-3.1.7/` | **版本**：`3.1.7` | **入口**：`index.js` | **服务端插件**：`server-plugin/littlewhitebox-server` (`1.2.1`)
- **关键链路**：
  1. `core/storage-file.js` 通过 `GET /user/files/<name>`、`POST /api/files/upload`、`POST /api/files/delete` 持久化大文件。
  2. 前端调用 `/api/plugins/littlewhitebox-server/*` 执行 NovelAI / SD-WebUI / ComfyUI 异步生图任务、Draw Runs（Scene Planner）、取消控制、回环探测与 Exa 搜索。
  3. `draw-runs/loopback-host-client.js` 向同一监听 socket 回环调用 `/api/backends/chat-completions/generate` 与 `/api/backends/chat-completions/status`。
- **根因缺陷与修复**：
  1. 在 `TavernRuntimeManager.kt` 与 `runtime.js` 实现 `/user/files/*` 与 `/api/files/{upload,delete}`。
  2. 在共享 Node.js 守护服务 `node-host/server-bootstrap.cjs` 中挂载 `littlewhitebox-server`，注入按助手/会话隔离的 `req.user.profile.handle`，并在同一 loopback 服务提供 `/api/backends/chat-completions/{generate,status}` 回环桥接。
