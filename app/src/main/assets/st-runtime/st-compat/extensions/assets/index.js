/**
 * rikkaST st-compat: extensions/assets/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/assets/index.js`（599 行）。
 *
 * 这个扩展干的事：**角色卡 V3 assets**（卡内嵌的图片/音频/视频资源）的
 * 上传、列表、删除、下载。ST 真源走 `/api/assets/*` 后端。
 *
 * ## 我们的实现
 * 宿主有沙箱文件桥 `TavernFileBridge`（`filesDir/st-user-files/`），
 * 正好对应 ST 的 assets 存储语义。所以：
 *  - `listAssets()` / `uploadAsset()` / `deleteAsset()` / `getAssetUrl()`
 *    全部经 `tavernFileOp` 桥落到沙箱（与 ST 的 /api/assets 语义等价）
 *  - 路径前缀 `assets/` 隔离，避免和普通文件混在一起
 *
 * DIVERGENCE：ST 用 HTTP 多部分上传；我们用 base64 经 JS 桥。
 */

export const MODULE_NAME = 'assets';

const PREFIX = 'assets/';

function callFileOp(op, path, base64) {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        if (!b || typeof b.tavernFileOp !== 'function') return null;
        const raw = b.tavernFileOp(op, PREFIX + String(path || ''), String(base64 || ''));
        if (typeof raw !== 'string' || !raw) return null;
        return JSON.parse(raw);
    } catch (_e) {
        return null;
    }
}

/**
 * 列出卡内嵌资源。
 * @returns {Promise<Array<{name: string, size?: number}>>}
 */
export async function listAssets() {
    const r = callFileOp('list', '', '');
    return (r && r.ok && Array.isArray(r.entries)) ? r.entries : [];
}

/**
 * 上传一个资源（base64）。
 * @param {string} name 文件名
 * @param {string} base64 内容
 * @returns {Promise<boolean>}
 */
export async function uploadAsset(name, base64) {
    const r = callFileOp('write', name, base64);
    return !!(r && r.ok);
}

/**
 * 删除一个资源。
 * @param {string} name
 * @returns {Promise<boolean>}
 */
export async function deleteAsset(name) {
    const r = callFileOp('delete', name, '');
    return !!(r && r.ok);
}

/**
 * 读一个资源（返回 base64）。
 * @param {string} name
 * @returns {Promise<string|null>}
 */
export async function getAssetBase64(name) {
    const r = callFileOp('read', name, '');
    return (r && r.ok && typeof r.base64 === 'string') ? r.base64 : null;
}

/**
 * ST 扩展入口：绑定设置页 UI。
 * 我们由 Compose 承担；这里只自检文件桥可用性。
 */
export async function init() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        return !!(b && typeof b.tavernFileOp === 'function');
    } catch (_e) {
        return false;
    }
}
