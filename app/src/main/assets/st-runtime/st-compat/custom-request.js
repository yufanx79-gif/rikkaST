/**
 * rikkaST st-compat: custom-request.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/custom-request.js`（P0 stub）。
 * 扩展经此发起自定义 API 调用暂不支持（宿主 AI 管道另行接管）。
 */

export class ChatCompletionService {
    static async sendRequest() { return null; }
    static async processRequest() { return null; }
}