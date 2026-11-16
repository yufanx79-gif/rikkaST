/**
 * rikkaST st-compat: browser-fixes.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export function applyBrowserFixes(..._args) { return null; }
/**
 * [v247 BUGFIX] ST 真源是**函数**：`const isFirefox = () => /firefox/i.test(navigator.userAgent)`
 * （browser-fixes.js:3，通过 `export { isFirefox }` 转导出）。
 * 我们导出成 `{}` → 脚本 `isFirefox()` 直接崩。
 * 宿主是 Android WebView（Chromium），恒返回 false。
 */
export function isFirefox() {
    try {
        const ua = (typeof navigator !== 'undefined' && navigator.userAgent) || '';
        return /firefox/i.test(ua);
    } catch (_e) { return false; }
}
