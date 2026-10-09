/**
 * rikkaST st-compat: f-localStorage.js
 * ============================================================
 * ST `scripts/f-localStorage.js` 的导出面。ST 里这三个是对 localStorage 的
 * 带 JSON 序列化的读写封装：
 *   SaveLocal(name, value)        写入（原始值 / 对象 / 数组都行）
 *   LoadLocal(name, defaultValue) 读取；缺失返回 defaultValue
 *   LoadLocalBool(name)           读取布尔值；缺失或非法返回 false
 *
 * 为什么必须有这个文件：第三方扩展（如 st-memory-enhancement 的
 * services/appFuncManager.js）在模块顶层 `import { LoadLocal, SaveLocal,
 * LoadLocalBool } from '/scripts/f-localStorage.js'`。兼容层缺失该路径时
 * shouldInterceptRequest 返回 404 → 整条 ES module 图解析失败 → 扩展一声不响地
 * 完全加载不起来（日志只有一句「加载失败」，不给原因）。批次十五补上。
 */
function area() {
    try {
        if (typeof window !== 'undefined' && window.localStorage) return window.localStorage;
    } catch (_e) { /* localStorage 被禁用 */ }
    return null;
}

export function SaveLocal(name, value) {
    const a = area();
    if (!a) return;
    try { a.setItem(String(name), JSON.stringify(value === undefined ? null : value)); } catch (_e) { /* quota */ }
}

export function LoadLocal(name, defaultValue = null) {
    const a = area();
    if (!a) return defaultValue;
    let raw = null;
    try { raw = a.getItem(String(name)); } catch (_e) { return defaultValue; }
    if (raw === null || raw === undefined) return defaultValue;
    try {
        const parsed = JSON.parse(raw);
        return parsed === null ? defaultValue : parsed;
    } catch (_e) {
        return raw; // 兼容「不是 JSON、直接存的字符串」
    }
}

export function LoadLocalBool(name) {
    const a = area();
    if (!a) return false;
    let raw = null;
    try { raw = a.getItem(String(name)); } catch (_e) { return false; }
    if (raw === null || raw === undefined) return false;
    if (raw === 'true') return true;
    if (raw === 'false') return false;
    try { return JSON.parse(raw) === true; } catch (_e) { return false; }
}

// 同文件里常见的兄弟函数，一并提供，避免别的扩展再撞 404
export function SaveLocalBool(name, value) { SaveLocal(name, !!value); }

export function RemoveLocal(name) {
    const a = area();
    if (!a) return;
    try { a.removeItem(String(name)); } catch (_e) { /* noop */ }
}

export function ClearLocal() {
    const a = area();
    if (!a) return;
    const doomed = [];
    for (let i = 0; i < a.length; i++) {
        const k = a.key(i);
        if (k && k.indexOf('st-') === 0) doomed.push(k);
    }
    for (const k of doomed) { try { a.removeItem(k); } catch (_e) { /* noop */ } }
}

export default { SaveLocal, LoadLocal, LoadLocalBool, SaveLocalBool, RemoveLocal, ClearLocal };
