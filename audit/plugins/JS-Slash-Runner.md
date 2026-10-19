# 插件专项审计：JS-Slash-Runner / Tavern Helper (v4.11.3)

- **目录**：`JS-Slash-Runner-main/` | **版本**：`4.11.3` | **入口**：`dist/index.js`
- **关键链路**：读取 `/version` 与 `SillyTavern.version`；在 `>= 1.15.0` 时从 `@sillytavern/scripts/openai` 导入 `createGenerationParameters`、`sendOpenAIRequest`、`getStreamingReply`；向 `/api/backends/chat-completions/generate` 发送 `stream: true` 或 `stream: false` 请求。
- **根因缺陷与修复**：
  1. `runtime.js` `handleGenerate` 对 `stream: false` 仍返回 SSE `text/event-stream`，导致 `response.json()` 抛出 `SyntaxError` → 根据 `body.stream` 分支返回 `application/json` 或 `text/event-stream`。
  2. 补齐 `st-compat/openai.js` 1.19.0 导出函数并将版本号统一为 `1.19.0`。
