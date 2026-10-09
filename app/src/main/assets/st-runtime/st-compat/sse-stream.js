/**
 * rikkaST st-compat: sse-stream.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/sse-stream.js`（P0：空流）。
 * 宿主 AI 管道自行处理流式响应；此层仅供扩展 import 不炸。
 */

export async function* getEventSourceStream() {
    // P0：不产出任何事件。
}