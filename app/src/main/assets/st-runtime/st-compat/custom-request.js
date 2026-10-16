/**
 * rikkaST st-compat: custom-request.js
 * ============================================================
 * 对齐 SillyTavern `public/scripts/custom-request.js`，通过宿主 Chat Completions 桥接发送请求。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

export class ChatCompletionService {
    static async sendRequest(payload = {}) {
        return ChatCompletionService.processRequest(payload);
    }

    static async processRequest(payload = {}) {
        try {
            if (typeof window !== 'undefined' && typeof window.fetch === 'function') {
                try {
                    const resp = await window.fetch('/api/backends/chat-completions/generate', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload || {}),
                    });
                    if (resp && resp.ok) {
                        return await resp.json();
                    }
                } catch (_fetchErr) {
                    // Fallback to ctx.generateRaw below
                }
            }
            const ctx = st();
            if (ctx && typeof ctx.generateRaw === 'function') {
                const messages = Array.isArray(payload.messages) ? payload.messages : [];
                const prompt = messages.map((m) => (m && m.content) || '').join('\n');
                const text = await ctx.generateRaw({ prompt });
                return { choices: [{ message: { role: 'assistant', content: String(text || '') } }] };
            }
        } catch (_e) { /* noop */ }
        return { choices: [{ message: { role: 'assistant', content: '' } }] };
    }
}
