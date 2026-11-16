/**
 * rikkaST st-compat: logit-bias.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/logit-bias.js`（614 行）。
 *
 * 真源里的三块能力：
 *  1. `getLogitBiasListResult(biasPreset, tokenizerType, getBiasObject)` —— **纯计算**，
 *     把「文本 / {原文} / [token id 数组]」三种写法解析成 token 列表（logit-bias.js:104-140）。
 *  2. `displayLogitBias` / `createNewLogitBiasEntry` / `removeLogitBias` —— DOM 列表渲染。
 *  3. `BIAS_CACHE` —— token 缓存。
 *
 * ## 我们实现哪部分
 * 第 1 块**完整移植**（纯函数，脚本/扩展真的会用；不依赖 DOM）。
 * 第 2 块是 ST 设置页的 jQuery 列表 UI —— 我们用 Compose 渲染，
 * 这里保留同名函数为 no-op（调用不抛错），见 DIVERGENCE。
 *
 * DIVERGENCE：ST 用 `$(containerSelector).find('.logit_bias_list')` 操作 DOM 列表；
 * 我们没有 ST 设置页 DOM。宿主侧的 logit bias 编辑由 Compose UI 承担。
 */

import { getTextTokens } from './tokenizers.js';
import { uuidv4 } from './utils.js';

/** ST logit-bias.js:8 —— token 缓存（key = 文本 + tokenizer）。 */
export const BIAS_CACHE = new Map();

/**
 * 把 logit bias 预设解析成 `[{value, tokens}]` 列表。
 *
 * 三种写法（逐字对齐 ST logit-bias.js:104-140）：
 *  - `{文本}` → 取花括号内的原文，按 tokenizer 切 token
 *  - `[1,2,3]` → JSON 数组，直接当 token id（每项必须是整数）
 *  - `文本`   → **前面补一个空格**再切 token（ST 的行为，别改）
 *
 * @param {Array<{text?: string, value?: number}>} biasPreset
 * @param {string} tokenizerType 例如 'cl100k_base'
 * @param {(value: number, tokens: number[]) => any} getBiasObject
 * @returns {any[]}
 */
export function getLogitBiasListResult(biasPreset, tokenizerType, getBiasObject) {
    const result = [];
    if (!Array.isArray(biasPreset)) return result;
    const makeBias = typeof getBiasObject === 'function'
        ? getBiasObject
        : (value, tokens) => ({ value, tokens });

    for (const entry of biasPreset) {
        if (!entry || typeof entry.text !== 'string' || entry.text.length === 0) continue;
        const text = entry.text.trim();
        if (text.length === 0) continue;

        if (text.startsWith('{') && text.endsWith('}')) {
            // 原文模式：去掉花括号后按 tokenizer 切
            const tokens = getTextTokens(tokenizerType, text.slice(1, -1));
            result.push(makeBias(entry.value, tokens));
        } else if (text.startsWith('[') && text.endsWith(']')) {
            // 原始 token id 模式（JSON 序列化的整数数组）
            try {
                const tokens = JSON.parse(text);
                if (Array.isArray(tokens) && tokens.every((t) => Number.isInteger(t))) {
                    result.push(makeBias(entry.value, tokens));
                } else {
                    throw new Error('Not an array of integers');
                }
            } catch (err) {
                console.log(`Failed to parse logit bias token list: ${text}`, err);
            }
        } else {
            // 普通文本：ST 会**补一个前导空格**再切 token
            const biasText = ` ${text}`;
            const tokens = getTextTokens(tokenizerType, biasText);
            result.push(makeBias(entry.value, tokens));
        }
    }
    return result;
}

/**
 * ST `createNewLogitBiasEntry` 的**数据部分**：往数组里追加一条空条目。
 * ST 还会同步渲染 DOM 列表，我们不需要（Compose 侧自行观察数组变化）。
 *
 * @param {object[]} logitBias
 * @returns {{id: string, text: string, value: number}}
 */
export function createNewLogitBiasEntry(logitBias, containerSelector) {
    const entry = { id: uuidv4(), text: '', value: 0 };
    if (Array.isArray(logitBias)) logitBias.push(entry);
    // ST 语义（logit-bias.js:60-66）：新条目要同步渲染到 DOM 列表，
    // 并清掉该容器的 token 缓存（否则新条目的 token 会命中旧缓存）。
    if (containerSelector) {
        try { BIAS_CACHE.delete(containerSelector); } catch (_e) { /* noop */ }
    }
    return entry;
}

/**
 * ST `displayLogitBias(logitBias, containerSelector)`：把列表渲染到 DOM。
 *
 * 我们没有 ST 设置页 DOM → no-op。但必须存在且不抛错（扩展会无条件调用）。
 * 同时按 ST 语义清掉该容器的 token 缓存（真源 :55 `BIAS_CACHE.delete(containerSelector)`）。
 */
export function displayLogitBias(logitBias, containerSelector) {
    try {
        if (containerSelector) BIAS_CACHE.delete(containerSelector);
    } catch (_e) { /* noop */ }
    // no-op by design（见文件头 DIVERGENCE）
}
