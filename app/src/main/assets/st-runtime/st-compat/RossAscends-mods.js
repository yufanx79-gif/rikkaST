/**
 * rikkaST st-compat: RossAscends-mods.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/RossAscends-mods.js` 的常用导出（P0）。
 */

/**
 * [v247 BUGFIX] ST 真源是 `export async function favsToHotswap()`（RossAscends-mods.js:305），
 * 作用是把收藏的角色卡渲染到右侧栏的快捷切换区。
 *
 * 我们此前导出成**数组 `[]`** —— 上游 `replaceCharacter()`（rikkast-entry.js:18543）
 * 和 `BulkEditOverlay`（:785）/ `group-chats`（:850）都会**直接调用**它：
 * ```
 * favsToHotswap();
 * ^ TypeError: favsToHotswap is not a function
 * ```
 * 后果：**任何角色卡修改都会崩**（updateCharacterWith / replaceCharacter 全部失效）。
 *
 * 宿主用 Compose 渲染角色列表，没有 ST 的 `#right-nav-panel .hotswap` DOM，
 * 所以这里是 no-op —— 但必须是**函数**且返回 Promise（调用方会 await）。
 */
export async function favsToHotswap() {
    // no-op by design（宿主用 Compose 渲染收藏列表）
}

export const isMobile = true;

export function shouldSendOnEnter() {
    return false;
}

export function getMessageTimeStamp(timestamp) {
    try {
        const d = timestamp ? new Date(timestamp) : new Date();
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (_e) {
        return '';
    }
}

// ---- [ST 1.19.0 Full Export Parity] ----
export async function RA_CountCharTokens() { return 0; }
export const autoFitSendTextAreaDebounced = () => {};
export function dragElement() {}
export function getParsedUA() { return typeof navigator !== 'undefined' ? navigator.userAgent : ''; }
export function humanizeGenTime(ms = 0) { return `${Math.max(0, Number(ms) || 0)}ms`; }
export function humanizedDateTime() { return new Date().toISOString().replace(/[:.]/g, '-'); }
export async function initMovingUI() {}
export function initRossMods() {}
