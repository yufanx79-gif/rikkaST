/**
 * rikkaST st-compat: extensions/regex/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/regex/index.js`。
 *
 * 真源 `index.js:17` 只有一句：`export { getRegexScripts };`（从 engine.js 转导出）。
 *
 * ⚠️ [v247 BUGFIX] 我们此前导出成 `export const getRegexScripts = {};`
 * —— 空对象。任何脚本调 `getRegexScripts()` 都会崩 "is not a function"。
 * 这个 API 是正则扩展的核心读入口（第三方扩展会用它枚举当前生效的正则）。
 *
 * 实现委托给 `st-compat/regex-engine.js` 的 `getScriptsByType` 语义：
 * 按 SCRIPT_TYPES（GLOBAL / SCOPED / PRESET）三源合并，
 * 数据源与 `getRegexedString` 完全一致（宿主 `__rikkahub_regex_scripts__`）。
 */

import { SCRIPT_TYPES } from '../../regex-engine.js';

/**
 * 取当前生效的全部正则脚本（三源合并，语义对齐 ST engine.js:98-100）。
 *
 * @param {{allowedOnly?: boolean}} [options]
 * @returns {Array<object>} ST `RegexScript[]` 形状
 */
export function getRegexScripts(options = { allowedOnly: true }) {
    try {
        const raw = (typeof globalThis !== 'undefined') ? globalThis.__rikkahub_regex_scripts__ : null;
        let list = [];
        if (Array.isArray(raw)) list = raw;
        else if (typeof raw === 'string' && raw) {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) list = parsed;
        }
        // allowedOnly：过滤掉 disabled 的（ST 的 getScriptsByType 也会按允许状态过滤）
        if (options && options.allowedOnly) {
            return list.filter((s) => s && !s.disabled);
        }
        return list;
    } catch (_e) {
        return [];
    }
}

/**
 * ST `getScriptsByType(scriptType, options)` —— 按来源类型取正则。
 *
 * 我们的数据源是**合并后**的单一列表（宿主已按 全局→预设→卡片 顺序合并并去重），
 * 所以这里只能按 `__presetGroup` 标记粗分：
 *  - GLOBAL (0)：没有 __presetGroup 标记的
 *  - PRESET (2)：有 __presetGroup 标记的
 *  - SCOPED (1)：宿主合并列表里没有单独的卡片标记，返回空（卡片正则已在合并列表里）
 *
 * @param {number} scriptType SCRIPT_TYPES 之一
 * @param {{allowedOnly?: boolean}} [options]
 * @returns {Array<object>}
 */
export function getScriptsByType(scriptType, options = { allowedOnly: true }) {
    const all = getRegexScripts({ allowedOnly: false });
    let picked;
    switch (Number(scriptType)) {
        case SCRIPT_TYPES.GLOBAL: picked = all.filter((s) => !s || !s.__presetGroup); break;
        case SCRIPT_TYPES.PRESET: picked = all.filter((s) => s && s.__presetGroup); break;
        case SCRIPT_TYPES.SCOPED: picked = []; break;
        default: picked = [];
    }
    if (options && options.allowedOnly) return picked.filter((s) => s && !s.disabled);
    return picked;
}

/**
 * ST 正则扩展入口：绑定设置页 UI + 注册事件。
 * 我们的正则设置页由 Compose 承担（RegexScriptsPage.kt），这里只做自检。
 */
export async function init() {
    try {
        return typeof getRegexScripts === 'function';
    } catch (_e) {
        return false;
    }
}
