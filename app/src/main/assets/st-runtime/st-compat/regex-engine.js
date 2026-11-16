/**
 * rikkaST st-compat: regex-engine.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/extensions/regex/engine.js`。
 *
 * 为什么需要：Tavern Helper 的 `src/function/tavern_regex.ts` 与
 * `src/function/displayed_message.ts` 从 `@sillytavern/scripts/extensions/regex/engine`
 * import `getRegexedString` 与 `regex_placement`，我们此前没有这个模块
 * （对照结果：26 个依赖模块里唯一整体缺失的一个）。
 *
 * 契约来源（ST 1.19.0 真源 engine.js:281-470）：
 *  - `regex_placement` 是数值枚举，脚本按位比较
 *  - `getRegexedString(rawString, placement, opts)` 遍历所有「允许的」正则脚本，
 *    按 markdownOnly / promptOnly / isEdit / depth 过滤后依次 apply
 *
 * 本实现与真源的差异（登记在 DIVERGENCE 里）：
 *  - `extension_settings.disabledExtensions` 不参与判断：我们没有扩展禁用概念，
 *    正则总开关由宿主 `Settings.regexScripts` 的启用状态表达
 *  - `RegexProvider.instance.get()` 用本地缓存替代（真源有 LRU 正则编译缓存，
 *    这里用 Map 缓存编译结果，语义等价）
 *  - `substituteParamsExtended` 的第三个参数（sanitizeRegexMacro）不支持，
 *    退化为普通宏替换 —— 该参数只影响极少数用 `{{macro}}` 拼正则的场景
 */

import { substituteParams, substituteParamsExtended } from './script.js';

/**
 * 正则作用位置。数值与 ST 真源逐字对齐（engine.js:281-292）。
 * @readonly
 * @enum {number}
 */
export const regex_placement = {
    /** @deprecated MD Display is deprecated. Do not use. */
    MD_DISPLAY: 0,
    USER_INPUT: 1,
    AI_OUTPUT: 2,
    SLASH_COMMAND: 3,
    // 4 - sendAs (legacy)
    WORLD_INFO: 5,
    REASONING: 6,
};

/** 与 ST `substitute_find_regex` 枚举对齐（engine.js 顶部）。 */
export const substitute_find_regex = {
    NONE: 0,
    RAW: 1,
    ESCAPED: 2,
};

/** 脚本类型（决定优先级）。数值与 ST 真源一致。 */
export const SCRIPT_TYPES = {
    GLOBAL: 0,
    PRESET: 2,
    SCOPED: 1,
};

/**
 * 正则编译缓存。
 * 真源用 `RegexProvider.instance.get()`（带 LRU + 失效），这里用普通 Map。
 * 正则是纯函数式输入，同一字符串编译结果稳定，不需要 LRU 淘汰。
 */
const regexCache = new Map();

function compileRegex(pattern) {
    if (typeof pattern !== 'string' || pattern === '') return null;
    if (regexCache.has(pattern)) return regexCache.get(pattern);
    // ST 的 `regexFromString` 支持 `/pattern/flags` 形式，这里同样处理
    let re = null;
    const m = pattern.match(/^\/([\s\S]*)\/([gimsuy]*)$/);
    try {
        re = m ? new RegExp(m[1], m[2]) : new RegExp(pattern, 'g');
    } catch (_e) {
        re = null;
    }
    regexCache.set(pattern, re);
    return re;
}

/** 供宿主在正则脚本变更时清缓存。 */
export function clearRegexCache() {
    regexCache.clear();
}

/**
 * 取当前所有「允许的」正则脚本。
 *
 * 数据来源：宿主通过 `window.__rikkahub_regex_scripts__` 注入（Kotlin 侧
 * `TavernRuntimeManager` 的 regexScriptsSupplier）。未注入时返回空数组，
 * 保证 `getRegexedString` 退化为恒等函数而不是抛错。
 */
function getRegexScripts() {
    try {
        const raw = globalThis.__rikkahub_regex_scripts__;
        if (Array.isArray(raw)) return raw;
        if (typeof raw === 'string' && raw) {
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : [];
        }
    } catch (_e) {
        /* noop */
    }
    return [];
}

/**
 * 对字符串跑正则管线。语义对齐 ST engine.js:334-388。
 *
 * @param {string} rawString 输入
 * @param {number} placement regex_placement 之一
 * @param {{characterOverride?: string, isMarkdown?: boolean, isPrompt?: boolean, isEdit?: boolean, depth?: number}} [opts]
 * @returns {string}
 */
export function getRegexedString(rawString, placement, { characterOverride, isMarkdown, isPrompt, isEdit, depth } = {}) {
    if (typeof rawString !== 'string') {
        console.warn('getRegexedString: rawString is not a string. Returning empty string.');
        return '';
    }
    let finalString = rawString;
    if (!rawString || placement === undefined) return finalString;

    // [v247 G 组] 优先走宿主 Kotlin 的 RegexScriptEngine —— 与消息气泡里**同一份**实现。
    //
    // 为什么：StRegexedText.kt:575 渲染消息时调的是 Kotlin `RegexScriptEngine.getRegexedString`，
    // 而这里是 JS 复刻版。两套实现只要有一处漂移，就会出现
    // 「脚本 formatAsTavernRegexedString 算出 A、界面上显示 B」这类无法排查的诡异问题。
    // 宿主桥可用时以宿主为准；不可用（Node 测试 / 面板模式）时退回本地实现。
    // 注意：`isEdit` 语义宿主桥暂不承载（脚本极少用），带 isEdit 时仍走本地实现。
    if (!isEdit) {
        try {
            const bridge = (typeof window !== 'undefined' && window.RikkaBridge) ? window.RikkaBridge : null;
            if (bridge && typeof bridge.regexFormatText === 'function') {
                const destination = isMarkdown ? 'display' : (isPrompt ? 'prompt' : 'none');
                const out = bridge.regexFormatText(
                    rawString,
                    Number(placement),
                    destination,
                    (typeof depth === 'number' && Number.isFinite(depth)) ? Number(depth) : -1,
                );
                if (typeof out === 'string') return out;
            }
        } catch (_e) { /* 宿主桥不可用 → 落回本地实现 */ }
    }

    const allRegex = getRegexScripts();
    for (const script of allRegex) {
        if (!script) continue;
        // 宿主已过滤 disabled，这里再兜一层
        if (script.disabled) continue;

        const applies =
            (script.markdownOnly && isMarkdown) ||
            (script.promptOnly && isPrompt) ||
            (!script.markdownOnly && !script.promptOnly && !isMarkdown && !isPrompt);
        if (!applies) continue;

        if (isEdit && !script.runOnEdit) continue;

        if (typeof depth === 'number') {
            if (typeof script.minDepth === 'number' && script.minDepth >= -1 && depth < script.minDepth) continue;
            if (typeof script.maxDepth === 'number' && script.maxDepth >= 0 && depth > script.maxDepth) continue;
        }

        const placements = Array.isArray(script.placement) ? script.placement : [];
        if (!placements.includes(placement)) continue;

        finalString = runRegexScript(script, finalString, { characterOverride });
    }
    return finalString;
}

/**
 * 单个正则脚本应用。语义对齐 ST engine.js:395-447。
 *
 * @param {object} regexScript
 * @param {string} rawString
 * @param {{characterOverride?: string}} [opts]
 * @returns {string}
 */
export function runRegexScript(regexScript, rawString, { characterOverride } = {}) {
    let newString = rawString;
    if (!regexScript || regexScript.disabled || !regexScript.findRegex || !rawString) return newString;

    let regexString = regexScript.findRegex;
    switch (Number(regexScript.substituteRegex)) {
        case substitute_find_regex.NONE:
            break;
        case substitute_find_regex.RAW:
            regexString = substituteParamsExtended(regexScript.findRegex);
            break;
        case substitute_find_regex.ESCAPED:
            // sanitizeRegexMacro 不支持，退化为普通宏替换（见文件头 DIVERGENCE）
            regexString = substituteParamsExtended(regexScript.findRegex);
            break;
        default:
            break;
    }

    const findRegex = compileRegex(regexString);
    if (!findRegex) return newString;

    newString = rawString.replace(findRegex, function (...args) {
        const match = args[0];
        let replaceString = String(regexScript.replaceString ?? '').replace(/{{match}}/gi, '$0');
        replaceString = replaceString.replaceAll(/\$(\d+)|\$<([^>]+)>/g, (_, num, groupName) => {
            let picked = '';
            if (num) {
                picked = args[Number(num)] ?? '';
            } else if (groupName) {
                const groups = args[args.length - 1];
                picked = groups && typeof groups === 'object' ? (groups[groupName] ?? '') : '';
            }
            if (!picked) return '';
            return filterString(String(picked), regexScript.trimStrings, { characterOverride });
        });
        return substituteParams(replaceString);
    });

    return newString;
}

/**
 * 按 trimStrings 过滤匹配内容。语义对齐 ST engine.js:455-470。
 * @param {string} rawString
 * @param {string[]} [trimStrings]
 * @param {{characterOverride?: string}} [opts]
 */
function filterString(rawString, trimStrings, { characterOverride } = {}) {
    let finalString = rawString;
    const list = Array.isArray(trimStrings) ? trimStrings : [];
    for (const trimString of list) {
        if (!trimString) continue;
        const subTrimString = substituteParams(trimString, { name2Override: characterOverride });
        if (!subTrimString) continue;
        finalString = finalString.replaceAll(subTrimString, '');
    }
    return finalString;
}
