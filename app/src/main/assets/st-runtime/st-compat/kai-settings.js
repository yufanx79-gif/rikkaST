/**
 * rikkaST st-compat: kai-settings.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/kai-settings.js` 的导出面。
 */

export const kai_settings = {
    temp: 1.0,
    rep_pen: 1.1,
    rep_pen_range: 1024,
    top_p: 0.9,
    top_a: 0.0,
    top_k: 0,
    typical: 1.0,
    tfs: 1.0,
    streaming_kobold: false,
};

export function getKoboldSettings() {
    return kai_settings;
}

export function formatKoboldUrl(url) {
    return String(url || '').trim().replace(/\/+$/, '');
}
