/**
 * rikkaST st-compat: cfg-scale.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export const cfgType = {
    chat: 0,
    chara: 1,
    global: 2,
};
export function getCfgPrompt(..._args) { return null; }
export function getGuidanceScale(..._args) { return null; }
export function initCfg(..._args) { return null; }
export const metadataKeys = {
    guidance_scale: 'cfg_guidance_scale',
    negative_prompt: 'cfg_negative_prompt',
    positive_prompt: 'cfg_positive_prompt',
    prompt_combine: 'cfg_prompt_combine',
    groupchat_individual_chars: 'cfg_groupchat_individual_chars',
    prompt_insertion_depth: 'cfg_prompt_insertion_depth',
    prompt_separator: 'cfg_prompt_separator',
};
