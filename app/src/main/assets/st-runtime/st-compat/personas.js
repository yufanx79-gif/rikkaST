/**
 * rikkaST st-compat: personas.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/personas.js` 的导出面（P0：最小实现）。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

export let user_avatar = '';

export function getUserAvatar() {
    return user_avatar || '';
}

export function getUserAvatars() {
    return [];
}

export async function setUserAvatar() {}

export async function getUserPersona() {
    return '';
}