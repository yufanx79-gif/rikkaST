/**
 * rikkaST st-compat: extensions/quick-reply/index.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
/**
 * [v247 BUGFIX] ST 从 utils.js 转导出 `debounceAsync`（quick-reply/index.js:12-14），
 * 它是**函数**。我们导出成 `{}` → 调用即崩。
 * 实现与 ST utils.js 的 debounceAsync 同语义：延迟执行 + 保留最后一次调用的 Promise。
 */
export function debounceAsync(fn, delay = 0) {
    let timer = null;
    let pending = [];
    const wrapped = (...args) => new Promise((resolve, reject) => {
        pending.push({ resolve, reject });
        if (timer) clearTimeout(timer);
        timer = setTimeout(async () => {
            const batch = pending;
            pending = [];
            timer = null;
            try {
                const r = await fn(...args);
                for (const b of batch) b.resolve(r);
            } catch (e) {
                for (const b of batch) b.reject(e);
            }
        }, delay);
    });
    wrapped.cancel = () => { if (timer) clearTimeout(timer); timer = null; pending = []; };
    return wrapped;
}
/**
 * [v247 BUGFIX] ST 真源（extensions/quick-reply/index.js:18-20）是**函数**：
 *   export const debug = (...msg) => _VERBOSE ? console.debug('[QR2]', ...msg) : null;
 * 我们导出成了空对象 `{}` → 脚本 `log('...')` 直接崩。
 */
const _QR_VERBOSE = false;
export const debug = (...msg) => { if (_QR_VERBOSE) try { console.debug('[QR2]', ...msg); } catch (_e) {} return null; };
export async function init(..._args) { return null; }
export const log = (...msg) => { if (_QR_VERBOSE) try { console.log('[QR2]', ...msg); } catch (_e) {} return null; };
export let quickReplyApi = {};
export const warn = (...msg) => { if (_QR_VERBOSE) try { console.warn('[QR2]', ...msg); } catch (_e) {} return null; };
