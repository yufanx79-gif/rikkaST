/**
 * rikkaST st-compat: variables.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/variables.js` 的局部变量读写。
 * 数据源：宿主 runtime 的会话变量（chat 作用域），经 RikkaBridge 同步。
 */

const host = () => (window.__rikkaSt || {});

export function getLocalVariable(key) {
    try {
        const h = host();
        if (h.variables && typeof h.variables.getLocalVariable === 'function') {
            return h.variables.getLocalVariable(key);
        }
        const raw = (window.RikkaBridge && typeof window.RikkaBridge.getLocalVariableJson === 'function')
            ? window.RikkaBridge.getLocalVariableJson()
            : '{}';
        const obj = JSON.parse(raw || '{}');
        return (obj && typeof obj === 'object') ? obj[String(key)] : undefined;
    } catch (_e) {
        return undefined;
    }
}

export function setLocalVariable(key, value) {
    try {
        const h = host();
        if (h.variables && typeof h.variables.setLocalVariable === 'function') {
            h.variables.setLocalVariable(String(key), value);
        }
    } catch (_e) { /* noop */ }
}