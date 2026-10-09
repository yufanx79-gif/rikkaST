/**
 * rikkaST st-compat: constants.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/constants.js` 的常量（值对齐 ST1.18 源码）。
 */

export const debounce_timeout = {
    quick: 100,
    short: 200,
    standard: 300,
    relaxed: 1000,
    extended: 5000,
};

export const inject_ids = {
    STORY_STRING: '__STORY_STRING__',
    QUIET_PROMPT: 'QUIET_PROMPT',
    DEPTH_PROMPT: 'DEPTH_PROMPT',
    DEPTH_PROMPT_INDEX: (index) => `DEPTH_PROMPT_${index}`,
    CUSTOM_WI_DEPTH: 'customDepthWI',
    CUSTOM_WI_DEPTH_ROLE: (depth, role) => `customDepthWI_${depth}_${role}`,
    CUSTOM_WI_OUTLET: (key) => `customWIOutlet_${key}`,
};