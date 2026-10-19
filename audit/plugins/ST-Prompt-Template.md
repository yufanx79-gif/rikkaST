# 插件专项审计：ST-Prompt-Template (v1.16.0)

- **目录**：`ST-Prompt-Template-1.16/` | **版本**：`1.16.0` | **入口**：`dist/index.js`
- **关键链路**：监听 `CHAT_COMPLETION_SETTINGS_READY` 与 `CHAT_COMPLETION_PROMPT_READY`，通过异步函数 `handleChatCompletionReady` 调用 `await processGenerateAfter(data.messages)` 改写、过滤或插入提示词消息，并使用 `eventSource.makeLast` 保证在其他插件后运行。
- **根因缺陷与修复**：
  1. `vendor/st/eventemitter.js` 的 `emitAndWait` 未 `await` 异步监听器，且 `runtime.js` `__rikkaRunGenerationHooks` 未 `await` → 改为异步顺序 `await`。
  2. `GenerationLoop.kt:848` 强制要求改写前后消息数相等 → 放开变长消息数组写回。
