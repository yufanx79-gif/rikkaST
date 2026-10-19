# 插件专项审计：ST-iPhonie (v0.8.15)

- **目录**：`ST-iPhonie-main/` | **版本**：`0.8.15` | **入口**：`index.js`
- **关键链路**：
  1. `manifest.json` 声明 `minimum_client_version: "1.19.0"`、`generate_interceptor: "stIphonieInterceptor"`、`hooks: { enable: "enable", disable: "disable", delete: "dispose" }`。
  2. 在 `ctx.messageFormatter.addHook` 注册 `BEFORE_REGEX`、`AFTER_REGEX`、`AFTER_MARKDOWN` 三阶段钩子，转换 `<tts>`、`<phone>`、`<pic>` 标签。
  3. 使用 `/user/files/*` 与 `/api/files/{upload,delete}` 存储手机应用数据与音频缓存。
- **根因缺陷与修复**：
  1. 实现 1.19.0 `MessageFormatter` 并桥接到 `StRegexedText.kt` 可见消息渲染。
  2. 在 `TavernExtensions.kt` 与 `runtime.js` 支持 `generate_interceptor` 与 `hooks` 生命周期。
  3. 实现 `/user/files/*` 与 `/api/files/{upload,delete}`。
