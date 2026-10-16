/**
 * rikkaST st-compat: personas.js
 * ============================================================
 * 对齐 SillyTavern `public/scripts/personas.js`，桥接宿主 Persona 与Slash 命令。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

export let user_avatar = 'user-default.png';

export function getUserAvatar() {
    const ctx = st();
    return (ctx && ctx.userAvatar) || user_avatar || 'user-default.png';
}

export function getUserAvatars() {
    const ctx = st();
    if (ctx && Array.isArray(ctx.personas)) {
        return ctx.personas.map((p) => (p && (p.avatar || p.name)) || '').filter(Boolean);
    }
    return [getUserAvatar()];
}

export async function setUserAvatar(avatarOrName) {
    if (!avatarOrName) return;
    user_avatar = String(avatarOrName);
    try {
        const ctx = st();
        if (ctx && typeof ctx.executeSlashCommandsWithOptions === 'function') {
            await ctx.executeSlashCommandsWithOptions(`/persona ${avatarOrName}`);
        }
    } catch (_e) { /* noop */ }
}

export async function getUserPersona() {
    const ctx = st();
    if (ctx && ctx.extensionSettings && ctx.extensionSettings.persona_description) {
        return String(ctx.extensionSettings.persona_description);
    }
    return '';
}
