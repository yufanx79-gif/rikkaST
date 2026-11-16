/**
 * rikkaST st-compat: bookmarks.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/bookmarks.js`（740 行）。
 *
 * 真源契约（bookmarks.js:452-472, 300-380）：
 *  - `branchChat(mesId, {swipeId})` —— 从指定楼层创建分支会话并打开
 *  - `createBranch(mesId, {swipeId})` —— 只创建分支文件，返回文件名
 *  - `createNewBookmark(mesId, {swipeId, title})` —— 书签（带标题的分支）
 *  - `updateBookmarkDisplay()` / `showBookmarksButtons()` / `initBookmarks()` —— UI
 *
 * ## 与 ST 的差异（DIVERGENCE，登记在此）
 * ST 的分支 = 在文件系统里写一个新的 `.jsonl` 聊天文件，再用 `openCharacterChat()` 打开。
 * 我们用的是 Room 数据库里的 `Conversation` 实体，所以：
 *  - `createBranch()` 经宿主桥 `branchConversation(mesId, swipeId, title)` 落一个新会话，
 *    返回新会话 id（当作 ST 的 fileName 用）；
 *  - `branchChat()` 再调 `openConversation(id)` 切过去（宿主侧导航）。
 * 返回值语义保持一致：成功返回字符串 id，失败返回 null。
 *
 * 为什么必须真实现：书签/分支是酒馆的**核心交互**，脚本和扩展会调
 * `branchChat`（如「在这里分叉」按钮）。返回 null 会让调用方以为失败了。
 */

const st = () => (typeof window !== 'undefined' && window.SillyTavern && window.SillyTavern.getContext
    ? window.SillyTavern.getContext() : {});
const bridge = () => (typeof window !== 'undefined' ? window.RikkaBridge : null);

function callBridge(name, ...args) {
    try {
        const b = bridge();
        if (!b || typeof b[name] !== 'function') return undefined;
        return b[name](...args);
    } catch (_e) {
        return undefined;
    }
}

function toast(level, msg) {
    try {
        if (typeof window !== 'undefined' && window.toastr && typeof window.toastr[level] === 'function') {
            window.toastr[level](msg);
        }
    } catch (_e) { /* noop */ }
}

/**
 * 创建分支会话（ST `createBranch` 语义：只创建、不切换）。
 *
 * @param {number} mesId 楼层号
 * @param {{swipeId?: number|null}} [options]
 * @returns {Promise<string|null>} 新会话 id（对应 ST 的 fileName）
 */
export async function createBranch(mesId, { swipeId = null } = {}) {
    const id = Number(mesId);
    if (!Number.isFinite(id)) {
        toast('warning', '创建分支失败：楼层号无效');
        return null;
    }
    const raw = callBridge('branchConversation', id, swipeId == null ? -1 : Number(swipeId), '');
    if (typeof raw !== 'string' || !raw) {
        // 宿主桥未接线（面板模式 / 老版本）时返回 null，与 ST「创建失败」语义一致
        return null;
    }
    let parsed = null;
    try { parsed = JSON.parse(raw); } catch (_e) { parsed = null; }
    if (parsed && typeof parsed === 'object') {
        return parsed.ok && parsed.id ? String(parsed.id) : null;
    }
    return raw;
}

/**
 * 从某楼层分叉并**切换过去**（ST `branchChat` 语义）。
 *
 * @param {number} mesId
 * @param {{swipeId?: number|null}} [options]
 * @returns {Promise<string|null>}
 */
export async function branchChat(mesId, { swipeId = null } = {}) {
    const newId = await createBranch(mesId, { swipeId });
    if (!newId) return null;
    // 切换会话：宿主侧负责导航（ST 是 openCharacterChat(fileName)）
    const ok = callBridge('openConversation', newId);
    if (ok === false) {
        toast('warning', '分支已创建，但切换失败');
    }
    return newId;
}

/**
 * 创建书签（带标题的分支）。
 *
 * ST 的书签本质就是「带 title 的分支 + 在书签列表里显示」。
 *
 * @param {number} mesId
 * @param {{swipeId?: number|null, title?: string}} [options]
 * @returns {Promise<string|null>}
 */
export async function createNewBookmark(mesId, { swipeId = null, title = '' } = {}) {
    const id = Number(mesId);
    if (!Number.isFinite(id)) return null;
    const raw = callBridge('branchConversation', id, swipeId == null ? -1 : Number(swipeId), String(title || ''));
    if (typeof raw !== 'string' || !raw) return null;
    let parsed = null;
    try { parsed = JSON.parse(raw); } catch (_e) { parsed = null; }
    if (parsed && typeof parsed === 'object') return parsed.ok && parsed.id ? String(parsed.id) : null;
    return raw;
}

/**
 * ST `updateBookmarkDisplay()`：刷新楼层上的书签按钮。
 *
 * 我们没有 ST 的 `#chat` DOM 楼层，宿主用 Compose 渲染消息操作栏，
 * 所以这里是 no-op —— 但**必须存在且不抛错**（脚本/扩展会无条件调用）。
 */
export function updateBookmarkDisplay() {
    // no-op by design（见文件头 DIVERGENCE）
}

/**
 * ST `showBookmarksButtons()`：显示书签栏。
 * 同上，宿主用 Compose 渲染，这里 no-op。
 */
export function showBookmarksButtons() {
    // no-op by design
}

/**
 * ST `initBookmarks()`：初始化（绑定 DOM 事件 + 注册斜杠命令）。
 *
 * 我们无需绑定 DOM，但 ST 的 `initBookmarks` 会注册 `/branch` `/bookmark` 斜杠命令，
 * 那些命令由 Kotlin 的 StSlashExecutor 提供，所以这里也是 no-op。
 */
export function initBookmarks() {
    // no-op by design
}

/**
 * ST `convertSoloToGroupChat()`：把单聊转成群聊。
 *
 * 宿主有 GroupChat 模型但没有「从现有会话转换」的桥，
 * 返回 null 表示未执行（与 ST 失败路径一致），不抛错。
 */
export async function convertSoloToGroupChat(..._args) {
    toast('info', '当前版本暂不支持把单聊转为群聊');
    return null;
}
