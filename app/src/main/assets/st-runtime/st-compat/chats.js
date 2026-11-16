// rikkaST st-compat: scripts/chats.js
export function addDOMPurifyHooks() {}
export async function appendFileContent() { return ''; }
export function decodeStyleTags(text) { return String(text ?? ''); }
export function encodeStyleTags(text) { return String(text ?? ''); }
export async function deleteAttachment() {}
export async function deleteFileFromServer() {}
export async function deleteMediaFromServer() {}
/**
 * 格式化作者备注（ST chats.js:684 语义：按角色卡的样式偏好决定是否解码 `<custom-style>`）。
 *
 * ST 的真实现依赖 `StylesPreference(avatarId)` + DOMPurify，做两件事：
 *  1. 若该卡**没有**偏好样式 → 剥掉 `#creator_notes_spoiler` 前缀的样式标签
 *  2. 否则保留（作者想让备注带样式）
 *
 * 我们没有 DOMPurify 与 StylesPreference 存储，但**签名必须完整**：
 * 少了 avatarId，调用方传进来会被丢掉，行为退化成「所有卡都一样」。
 *
 * @param {string} text 作者备注原文
 * @param {string} [avatarId] 角色卡头像 id（决定用哪张卡的样式偏好）
 */
export function formatCreatorNotes(text, avatarId) {
    const raw = String(text == null ? '' : text);
    if (!raw) return '';
    // 样式偏好存储：与 ST 同位置（extension_settings 里按 avatar 分键）
    let keepStyles = false;
    try {
        const ctx = globalThis.SillyTavern?.getContext?.() || {};
        const prefs = (ctx.extensionSettings && ctx.extensionSettings.styles_preference) || {};
        const key = avatarId == null ? '' : String(avatarId);
        keepStyles = key !== '' && prefs[key] === true;
    } catch (_e) { /* noop */ }
    if (keepStyles) return raw;
    // 默认：剥掉 custom-style 标签，保留其文本内容（对齐 ST 的 sanitize 行为）
    return raw.replace(/<custom-style\b[^>]*>/gi, '').replace(/<\/custom-style>/gi, '');
}
export function getCurrentEntityId() {
    const ctx = globalThis.SillyTavern?.getContext?.() || {};
    return ctx.groupId || ctx.characterId || '';
}
export function getDataBankAttachments() { return []; }
export function getDataBankAttachmentsForSource() { return []; }
export async function getFileAttachment() { return ''; }
export function hasPendingFileAttachment() { return false; }
export async function hideChatMessage() {}
export async function hideChatMessageRange() {}
export async function unhideChatMessage() {}
export function initChatUtilities() {}
export function isExternalMediaAllowed() { return true; }
export function populateFileAttachment() {}
export function preserveNeutralChat() {}
export function restoreNeutralChat() {}
export function registerFileConverter() {}
export async function uploadFileAttachment() {}
export async function uploadFileAttachmentToServer() { return ''; }
