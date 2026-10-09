/**
 * rikkaST st-compat: RossAscends-mods.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/RossAscends-mods.js` 的常用导出（P0）。
 */

export const favsToHotswap = [];

export const isMobile = true;

export function getMessageTimeStamp(timestamp) {
    try {
        const d = timestamp ? new Date(timestamp) : new Date();
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (_e) {
        return '';
    }
}