/**
 * rikkaST st-compat: extensions/gallery/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/gallery/index.js`（854 行）。
 *
 * 这个扩展干的事：把聊天里出现过的图片**汇总成一个画廊**，可浏览/删除/重新生成。
 * ST 真源靠遍历 `#chat` DOM 里的 `<img>` 节点建索引。
 *
 * ## 我们的实现
 * 宿主有等价能力（`mediagen` 模块 + `GenMediaRepository`），但**没有**「按会话汇总
 * 已出现图片」的查询桥。所以：
 *  - `init()` 自检宿主是否提供 `galleryListJson` 桥
 *  - `getGalleryImages()` 有桥就返回真实列表，无桥返回 `[]`（类型正确的空值）
 *
 * DIVERGENCE：ST 的 `init()` 建 DOM 画廊面板；我们的画廊若要做，由 Compose 承担。
 * 当前宿主未提供该桥 → 本模块退化为「类型正确但不产出数据」。
 */

export const MODULE_NAME = 'gallery';

/**
 * 取当前会话出现过的图片列表。
 *
 * @returns {Promise<Array<{url: string, messageId?: number, title?: string}>>}
 */
export async function getGalleryImages() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        if (!b || typeof b.galleryListJson !== 'function') return [];
        const raw = b.galleryListJson();
        if (typeof raw !== 'string' || !raw) return [];
        const arr = JSON.parse(raw);
        return Array.isArray(arr) ? arr : [];
    } catch (_e) {
        return [];
    }
}

/**
 * ST 扩展入口。ST 里它建 DOM 画廊面板。
 * 我们由 Compose 承担；这里只自检桥可用性，不抛错。
 */
export async function init() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        return !!(b && typeof b.galleryListJson === 'function');
    } catch (_e) {
        return false;
    }
}
