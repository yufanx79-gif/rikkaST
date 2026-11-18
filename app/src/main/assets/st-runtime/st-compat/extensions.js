/**
 * rikkaST st-compat: extensions.js
 * ============================================================
 * 模拟 SillyTavern 1.19.0 `public/scripts/extensions.js` 的完整导出面。
 * 第三方扩展最高频的 `getContext` / `extension_settings` / `renderExtensionTemplateAsync` / `ModuleWorkerWrapper` 均由此提供。
 */

import { SimpleMutex } from './util/SimpleMutex.js';

const rootWin = () => (typeof window !== 'undefined' ? window : globalThis);
const st = () => { const w = rootWin(); return (w && w.SillyTavern && w.SillyTavern.getContext ? w.SillyTavern.getContext() : {}); };
const host = () => (window.__rikkaSt || {});

export const UNSET_VALUE = undefined;
export const EMPTY_AUTHOR = '<No author>';
export { SimpleMutex, SimpleMutex as ModuleWorkerWrapper };

/**
 * 兼容冗余（超出 ST 1.19.0 的导出面）。
 *
 * ST 1.19.0 的 public/scripts/extensions.js 只导出
 *   { getContext, getApiUrl, SimpleMutex as ModuleWorkerWrapper }
 * —— 它内部 import 了 saveSettingsDebounced 但**没有再导出**。
 *
 * 但真实生态里有插件写：
 *   import * as ext from '.../scripts/extensions.js';
 *   const { saveSettingsDebounced } = ext;   // ← 上游为 undefined
 * 然后无保护地调用它，在 ST 里同样会崩（ZerxzLib 就是这样）。
 *
 * 我们多导出一个指向 script.js 同名函数的转发，插件能跑起来，
 * 且对按上游正确写法调用的插件没有任何影响。
 */
export function saveSettingsDebounced(...args) {
    const fn = rootWin()?.__rikkaStSaveSettingsDebounced;
    if (typeof fn === 'function') return fn(...args);
    const ctx = st();
    if (typeof ctx.saveSettingsDebounced === 'function') return ctx.saveSettingsDebounced(...args);
}

/** 全扩展共享的设置对象（live：runtime refreshAll 后自动同步）。 */
export let extension_settings = st().extensionSettings || {};
export let extensionNames = [];
export let modules = [];

try {
    if (typeof window !== 'undefined' && window.addEventListener) window.addEventListener('rikka-st-refresh', () => {
        try {
            const c = st();
            if (c.extensionSettings && typeof c.extensionSettings === 'object') {
                extension_settings = c.extensionSettings;
            }
        } catch (_e) { /* noop */ }
        try { __rikkaRefreshExtensionTypes(); } catch (_e) { /* noop */ }
    });
} catch (_e) { /* noop */ }

/**
 * ── [v202] 预置 `extension_settings.tavern_helper` 的完整默认形状 ──────────
 */
function __rikkaSeedTavernHelperSettings(settings) {
    try {
        if (!settings || typeof settings !== 'object') return;
        const s = settings.script && typeof settings.script === 'object' ? settings.script : (settings.script = {});
        const e = s.enabled && typeof s.enabled === 'object' ? s.enabled : (s.enabled = {});
        if (!Array.isArray(e.presets)) e.presets = [];
        if (!Array.isArray(e.characters)) e.characters = [];
        if (typeof e.global !== 'boolean') e.global = true;
        const pu = s.popuped && typeof s.popuped === 'object' ? s.popuped : (s.popuped = {});
        if (!Array.isArray(pu.presets)) pu.presets = [];
        if (!Array.isArray(pu.characters)) pu.characters = [];
        if (!Array.isArray(s.scripts)) s.scripts = [];
    } catch (_e) { /* noop */ }
}

export function __rikkaEnsureTavernHelperSeed() {
    try {
        __rikkaSeedTavernHelperSettings(extension_settings.tavern_helper);
    } catch (_e) { /* noop */ }
}

try { __rikkaEnsureTavernHelperSeed(); } catch (_e) { /* noop */ }

function normalizeFolderAlias(raw) {
    return String(raw == null ? '' : raw)
        .trim()
        .replace(/^third-party\//i, '')
        .replace(/-(?:main|master|dev|release|v?\d+(?:\.\d+)*(?:-[a-z0-9._-]+)?)$/i, '')
        .toLowerCase();
}

/**
 * [v243 BUGFIX C2] 扩展类型表。
 * 同时提供保留常量键与 `third-party/<folder>` 映射（含去版本后缀别名）。
 */
export let extensionTypes = { SYSTEM: 'system', LOCAL: 'local', GLOBAL: 'global' };

function __rikkaRefreshExtensionTypes() {
    try {
        const list = host().extensions || [];
        const names = [];
        for (const e of list) {
            if (e && e.folder) {
                const f = String(e.folder);
                const fullName = 'third-party/' + f;
                extensionTypes[fullName] = 'local';
                names.push(fullName);
                const alias = normalizeFolderAlias(f);
                if (alias && alias !== f.toLowerCase()) {
                    extensionTypes['third-party/' + alias] = 'local';
                }
            }
        }
        extensionNames = names;
    } catch (_e) { /* noop */ }
}
try { __rikkaRefreshExtensionTypes(); } catch (_e) { /* noop */ }

export function getContext() {
    return st();
}

export function getApiUrl() {
    return (extension_settings && extension_settings.apiUrl) || 'http://127.0.0.1:5100';
}

export async function doExtrasFetch(url, options = {}) {
    return fetch(url, options);
}

/** 写扩展字段的便捷函数（对齐 ST：extension_settings[name][field] = value）。 */
/**
 * 写角色卡的扩展字段。
 *
 * [v246 修正] 旧实现语义完全错了：
 *   旧：(extensionName, fieldName, value) → 写 `extension_settings[extensionName][fieldName]`
 *   真：(characterId, key, value)          → 写 `characters[characterId].data.extensions[key]`
 *
 * 证据：ST 1.19.0 `public/scripts/extensions.js:2070`
 *   `export async function writeExtensionField(characterId, key, value)`
 *   `const character = context.characters[characterId];`
 *   `const extensionPath = \`data.extensions.${key}\`;`
 *
 * 影响：Tavern Helper 的 `src/function/extension.ts` 与 `src/util/tavern.ts`
 * 都用它给角色卡写扩展数据（如卡片的自定义状态）。旧实现把这些数据
 * 写进了 `extension_settings`，角色卡永远读不到 —— 表现为「卡片存的状态下次没了」。
 *
 * @param {number|string} characterId 角色在 characters[] 里的下标
 * @param {string} key 扩展字段名
 * @param {any} value 值；传 `UNSET_VALUE` 表示删除
 */
export async function writeExtensionField(characterId, key, value) {
    try {
        const c = st();
        const chars = c.characters || [];
        const character = chars[Number(characterId)];
        if (!character) {
            // 与 ST 一致：只 warn 不抛错
            try { console.warn('writeExtensionField: character not found', characterId); } catch (_e) { /* noop */ }
            return;
        }
        if (!character.data) character.data = {};
        if (!character.data.extensions) character.data.extensions = {};
        if (value === UNSET_VALUE) {
            delete character.data.extensions[key];
        } else {
            character.data.extensions[key] = value;
        }
        try { saveMetadataDebounced(); } catch (_e) { /* noop */ }
    } catch (_e) { /* noop */ }
}


export function writeExtensionFieldBulk(extensionName, fields = {}) {
    try {
        if (!extension_settings[extensionName]) extension_settings[extensionName] = {};
        if (fields && typeof fields === 'object') {
            Object.assign(extension_settings[extensionName], fields);
        }
        saveMetadataDebounced();
    } catch (_e) { /* noop */ }
}

export function loadExtensionSettings(_settings = {}) {
    try {
        __rikkaRefreshExtensionTypes();
    } catch (_e) { /* noop */ }
}

export function isOfficialExtension(_name) {
    return false;
}

export function cancelDebouncedMetadataSave() {}

export async function runGenerationInterceptors(_chat, _contextSize, _abort, _type) {
    return false;
}

/**
 * 扩展模板渲染（异步与同步兼容）。
 * 真正实现由 runtime.js 挂到 window.renderExtensionTemplateAsync / window.renderTemplateAsync。
 */
export function renderExtensionTemplateAsync(extensionName, templateId, templateData = {}, sanitize = true, localize = true) {
    try {
        const f = window.renderExtensionTemplateAsync;
        if (typeof f === 'function') return f(extensionName, templateId, templateData, sanitize, localize);
    } catch (_e) { /* noop */ }
    return Promise.resolve('');
}

export function renderExtensionTemplate(extensionName, templateId, templateData = {}, sanitize = true, localize = true) {
    try {
        const f = window.renderExtensionTemplate;
        if (typeof f === 'function') return f(extensionName, templateId, templateData, sanitize, localize);
    } catch (_e) { /* noop */ }
    return '';
}

export function renderTemplateAsync(pathOrTemplateId, nameOrData, data) {
    try {
        const f = window.renderTemplateAsync;
        if (typeof f === 'function') return f(pathOrTemplateId, nameOrData, data);
    } catch (_e) { /* noop */ }
    return Promise.resolve('');
}

export function renderTemplate(pathOrTemplateId, nameOrData, data) {
    try {
        const f = window.renderTemplate;
        if (typeof f === 'function') return f(pathOrTemplateId, nameOrData, data);
    } catch (_e) { /* noop */ }
    return '';
}

export function saveMetadataDebounced() {
    try {
        const f = st().saveMetadataDebounced;
        if (typeof f === 'function') f();
    } catch (_e) { /* noop */ }
}

// ---------------------------------------------------------------- 兼容测试钩子（部分扩展/工具链 import）
export function __setExtensionSettings(obj) {
    try {
        Object.assign(extension_settings, obj || {});
    } catch (_e) { /* noop */ }
}

export const __setReplayContext = () => {};

export function openThirdPartyExtensionMenu() {}

/**
 * [v243 BUGFIX C1 + ST 1.19.0 别名支持] 扩展清单查询。
 */
export function getExtensionManifest(extensionName) {
    try {
        const c = st();
        if (c && typeof c.getExtensionManifest === 'function') {
            const m = c.getExtensionManifest(extensionName);
            if (m) return m;
        }
        const list = host().extensions || [];
        const want = String(extensionName == null ? '' : extensionName).trim().toLowerCase();
        if (!want) return null;
        const wantAlias = normalizeFolderAlias(want);
        const norm = (v) => String(v == null ? '' : v).trim().toLowerCase();
        return list.find((e) => e && (
            norm(e.folder) === want ||
            norm(e.name) === want ||
            norm(e.displayName) === want ||
            (wantAlias && normalizeFolderAlias(e.folder) === wantAlias)
        )) || null;
    } catch (_e) { return null; }
}

// ---- [ST 1.19.0 Full Export Parity] ----
export async function deleteExtension(..._args) { return null; }
export async function disableExtension(..._args) { return null; }
export function doDailyExtensionUpdatesCheck(..._args) { return null; }
export async function enableExtension(..._args) { return null; }
export function findExtension(..._args) { return null; }
export function getAuthorFromUrl(..._args) { return null; }
export async function initExtensions(..._args) { return null; }
export async function installExtension(..._args) { return null; }
