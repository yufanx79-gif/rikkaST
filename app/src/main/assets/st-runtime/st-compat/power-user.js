/**
 * rikkaST st-compat: power-user.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/power-user.js` 的导出面（P0 核心对象 + 常用枚举）。
 * `power_user` 为宽容对象：扩展读未知字段得到 undefined（与 ST 行为一致）。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

/** Persona 描述插入位置枚举（对齐 ST 语义；数值以 ST1.18 为参照）。 */
const persona_description_positions_enum = {
    IN_PROMPT: 0,
    TOP_AN: 2,
    AFTER_CHAR: 1, // ST personas.js:91（deprecated 别名，v213 对齐）
    IN_CHAT: 1, // 旧别名保留（部分扩展可能已按此名读取）
    BOTTOM_AN: 3,
    AT_DEPTH: 4,
    NONE: 9,
};

export const persona_description_positions = persona_description_positions_enum;

/** Power User 设置（P0 只读默认值；后续可接宿主设置）。 */
export const power_user = {
    persona_description: '',
    persona_description_position: persona_description_positions_enum.IN_PROMPT,
    persona_description_depth: 2,
    persona_description_role: 0,
    personas: {},
    default_persona: null,
    show_avatar_char_list: true,
    active_character: '',
    active_group: '',
    pinnedGroups: [],
    favorite_characters: [],
    quick_actions: [],
    custom_stopping_strings: '',
    custom_stopping_strings_macro: true,
    always_force_name2: false,
    reasoning: {
        name: 'Think XML',
        auto_parse: false,
        add_to_prompts: false,
        auto_expand: false,
        show_hidden: false,
        prefix: '<think>',
        suffix: '</think>',
        separator: '\n',
        max_additions: 1,
    },
    trim_spaces: true,
    world_info_depth: 2,
    context_size_responsive: false,
    smooth_streaming: false,
    smooth_streaming_speed: 15,
    media_display: 'list',
    message_wide: false,
    message_side: 'right',
    compact_input_area: true,
    auto_connect: true,
};

export const flushEphemeralStoppingStrings = () => {};

// ── [batch16] 三个缺失的导出 ──────────────────────────────
// 依据：refdeps\st-memory-enhancement\services\appFuncManager.js:6
//   `import { power_user, applyPowerUserSettings, getContextSettings, loadPowerUserSettings } from "/scripts/power-user.js";`
// 真实 ST 语义见 refdeps\sillytavern\scripts_power-user.js（:1554 / :1475 / :1925）。
try { if (!power_user.context) power_user.context = {}; } catch (err) { /* noop */ }

/** ST power-user.js:1554：把 settings.power_user 合并进 power_user。 */
export async function loadPowerUserSettings(settings = {}, data = null) {
    try {
        const fromSettings = settings && settings.power_user;
        if (fromSettings && typeof fromSettings === 'object') Object.assign(power_user, fromSettings);
        const fromData = data && data.power_user;
        if (fromData && typeof fromData === 'object') Object.assign(power_user, fromData);
    } catch (err) {
        console.warn('[power-user.js] loadPowerUserSettings failed:', err);
    }
    return power_user;
}

/** ST power-user.js:1475：应用外观设置。宿主没有 ST 核心 DOM，这里只做能做的两件事（不抛错）。 */
export function applyPowerUserSettings() {
    try {
        if (power_user.reduce_motion) document.body.classList.add('reduce-motion');
        if (power_user.avatar_style !== undefined) document.body.dataset.avatarStyle = String(power_user.avatar_style);
    } catch (err) {
        console.warn('[power-user.js] applyPowerUserSettings failed:', err);
    }
}

/** ST power-user.js:1925：把「全局或上下文」设置摊平成对象。 */
export function getContextSettings() {
    const compiled = {};
    try {
        const ctx = power_user.context || {};
        for (const key of Object.keys(ctx)) compiled[key] = ctx[key];
        for (const key of ['max_context', 'amount_gen', 'max_context_unlocked', 'instruct', 'context', 'sysprompt', 'reasoning']) {
            if (power_user[key] !== undefined && compiled[key] === undefined) compiled[key] = power_user[key];
        }
    } catch (err) {
        console.warn('[power-user.js] getContextSettings failed:', err);
    }
    return compiled;
}

// ── [v213] getCustomStoppingStrings（ST power-user.js:3078）──────────
// JSR createGenerationParametersCompat.ts:72 动态 import 具名读取；
// 旧版无此导出 → undefined（动态 import 解构缺名不抛 SyntaxError，但调用时 TypeError）。
export function getCustomStoppingStrings(limit) {
    try {
        const raw = power_user.custom_stopping_strings;
        if (!raw) return [];
        let arr = [];
        try { arr = JSON.parse(raw); } catch (_e) { arr = String(raw).split(String.fromCharCode(10)).map((s) => s.trim()).filter(Boolean); }
        if (!Array.isArray(arr)) return [];
        const out = arr.filter((s) => typeof s === "string" && s.length > 0);
        return typeof limit === "number" && limit > 0 ? out.slice(0, limit) : out;
    } catch (_e) { return []; }
}
