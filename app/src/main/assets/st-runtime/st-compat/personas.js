/**
 * rikkaST st-compat: personas.js
 * ============================================================
 * 对齐 SillyTavern `public/scripts/personas.js`，桥接宿主 Persona 与Slash 命令。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

export let user_avatar = 'user-default.png';

/**
 * [v247 BUGFIX] `user_avatar` 必须跟随宿主当前人设，否则 JSR 的人设 API 全部错位。
 *
 * 依据（ST 真源）：`user_avatar` 是 `public/scripts/personas.js` 的模块级 let，
 * ST 在 `initUserAvatar()` / `setUserAvatar()` 里更新它。Tavern Helper 的
 * `src/function/persona.ts:72-80` 直接读这个 live binding：
 *   getCurrentPersonaId()   -> user_avatar || null
 *   getCurrentPersonaName() -> power_user.personas[user_avatar] ?? null
 *   findPersonaId('current')-> user_avatar
 *
 * 我们此前从不更新它 → 恒为 `'user-default.png'` → 上面三个 API 全部返回错误值
 * （`getCurrentPersonaId()` 返回一个根本不存在的头像 id，
 *   `getPersona('current')` 抛「persona 'current' 不存在」）。
 *
 * 修法：宿主经 `ctx.userAvatar` 下发（runtime.js 的 pullPersonas() 写入），
 * 这里在模块初始化 + 每次 `rikka-st-refresh` 时同步。**只新增同步逻辑**，
 * 不改变原有导出语义（`setUserAvatar()` 仍然可以主动覆盖）。
 */
function __rikkaSyncUserAvatar() {
    try {
        const ctx = st();
        const next = ctx && typeof ctx.userAvatar === 'string' ? ctx.userAvatar : '';
        if (next) user_avatar = next;
    } catch (_e) { /* noop */ }
}
__rikkaSyncUserAvatar();
try {
    if (typeof window !== 'undefined' && window.addEventListener) {
        window.addEventListener('rikka-st-refresh', __rikkaSyncUserAvatar);
    }
} catch (_e) { /* noop */ }

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

// ---- [ST 1.19.0 Full Export Parity] ----
export async function askForPersonaSelection(..._args) { return null; }
export async function autoSelectPersona(..._args) { return null; }
export function buildPersonaAvatarList(..._args) { return null; }
export async function convertCharacterToPersona(..._args) { return null; }
export async function createPersona(..._args) { return null; }
export function getConnectedPersonas(..._args) { return null; }
export function getCurrentConnectionObj(..._args) { return null; }
export function getOrCreatePersonaDescriptor(..._args) { return null; }
export async function initPersona(..._args) { return null; }
export async function initPersonas(..._args) { return null; }
export function initUserAvatar(..._args) { return null; }
export function isPersonaConnectionLocked(..._args) { return null; }
export function isPersonaLocked(..._args) { return null; }
export function isPersonaPanelOpen(..._args) { return null; }
export const persona_description_positions = { IN_PROMPT: 0, AFTER_CHAR: 1, TOP_AN: 2, BOTTOM_AN: 3, AT_DEPTH: 4, NONE: 9 };
export const personasFilter = {};
export async function retriggerFirstMessageOnEmptyChat(..._args) { return null; }
export function setPersonaDescription(..._args) { return null; }
export async function setPersonaLockState(..._args) { return null; }
export async function showCharConnections(..._args) { return null; }
export async function togglePersonaLock(..._args) { return null; }
export function updatePersonaConnectionsAvatarList(..._args) { return null; }
