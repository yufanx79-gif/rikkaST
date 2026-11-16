/**
 * rikkaST st-compat: extensions/quick-reply/index.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export const debounceAsync = {};
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
