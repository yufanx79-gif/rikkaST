# 插件专项审计：st-memory-enhancement (v2.2.0)

- **目录**：`st-memory-enhancement-2.2.0/` | **版本**：`2.2.0` (`package.json`: `1.0.21`) | **入口**：`index.js`
- **关键链路**：在 `CHAT_COMPLETION_PROMPT_READY` 事件中向 `eventData.chat` 使用 `splice` 按深度 (`deep`) 插入 `<tableEdit>` 记忆表格消息；通过 `scripts/world-info.js` 同步世界书。
- **根因缺陷与修复**：
  1. 插入记忆表格后消息数组长度 `+1`，被 `GenerationLoop.kt:848` 的 `parsed.size == internalMessages.size` 拦截丢弃 → 放开 `GenerationLoop.kt` 变长消息数组写回。
  2. 将 `st-compat/world-info.js` 的写接口桥接到 `RikkaBridge` 世界书持久化方法。
