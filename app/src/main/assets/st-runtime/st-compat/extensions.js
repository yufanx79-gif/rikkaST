/**
 * rikkaST st-compat: extensions.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/extensions.js` 的导出面。
 * 第三方扩展最高频的 `getContext` / `extension_settings` 均由此提供。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});
const host = () => (window.__rikkaSt || {});

/** 全扩展共享的设置对象（live：runtime refreshAll 后自动同步）。 */
export let extension_settings = st().extensionSettings || {};
try {
    window.addEventListener('rikka-st-refresh', () => {
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
 * 真机实证：JSR 4.11.2 的 `store/settings/global.ts` 在 store 初始化时直接访问
 *     settings.value.script.popuped.characters[0]
 * （它的 zod schema 里 popuped 用的是 `.prefault({})`）。
 * 真机日志：`TypeError: Cannot read properties of undefined (reading '0')`
 *   at dF(...) / useGlobalSettingsStore 的 setup 里。
 * 我们不去改 JSR 的代码，而是**在扩展加载前把这份数据预置成完整形状**：
 * zod 的 default/prefault 对「已存在的完整对象」是幂等 no-op，从此不再残留 undefined。
 * 注意：只补 `script` 子树，其它字段保持 undefined，让 JSR 自己的 default 生效。
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

/**
 * [v243 BUGFIX C2] 扩展类型表。
 *
 * JSR 的 `extensionTypes` 形状是 `{ 'third-party/<folder>': 'local', ... }`
 * （见 JS-Slash-Runner/src/function/extension.ts:26-29 `Object.keys(extensionTypes).find(k => k.endsWith(id))`），
 * 而 rikkaST 原来只导出 `{ SYSTEM, LOCAL, GLOBAL }` —— 卡脚本按 JSR 形状查会永远 miss。
 * 这里做成**混合表**：保留原有三个常量键（向后兼容），同时按 JSR 形状补 `third-party/<folder>`
 * 条目，并在 `rikka-st-refresh`（宿主同步扩展清单后广播）时重新取数。
 */
export const extensionTypes = { SYSTEM: 'system', LOCAL: 'local', GLOBAL: 'global' };

function __rikkaRefreshExtensionTypes() {
    try {
        const list = host().extensions || [];
        for (const e of list) {
            if (e && e.folder) extensionTypes['third-party/' + String(e.folder)] = 'local';
        }
    } catch (_e) { /* noop */ }
}
try { __rikkaRefreshExtensionTypes(); } catch (_e) { /* noop */ }

export function getContext() {
    return st();
}

/** 写扩展字段的便捷函数（对齐 ST：extension_settings[name][field] = value）。 */
export function writeExtensionField(extensionName, fieldName, value) {
    try {
        if (!extension_settings[extensionName]) extension_settings[extensionName] = {};
        extension_settings[extensionName][fieldName] = value;
    } catch (_e) { /* noop */ }
}

/**
 * 扩展模板渲染。
 * v197 之前这里返回空串，导致 ST-Prompt-Template / 记忆增强表格 append 空字符串 → 设置页黑屏。
 * 真正实现由 runtime.js 挂到 window.renderExtensionTemplateAsync，这里只做 live proxy。
 */
export function renderExtensionTemplateAsync(extensionName, templateId, templateData = {}, sanitize = true) {
    try {
        const f = window.renderExtensionTemplateAsync;
        if (typeof f === 'function') return f(extensionName, templateId, templateData, sanitize);
    } catch (_e) { /* noop */ }
    return Promise.resolve('');
}

export function renderTemplateAsync(path, name, data) {
    try {
        const f = window.renderTemplateAsync;
        if (typeof f === 'function') return f(path, name, data);
    } catch (_e) { /* noop */ }
    return Promise.resolve('');
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
 * [v243 BUGFIX C1] 扩展清单查询。
 *
 * 旧实现只比 `folder` / `name` 两个字段。宿主 buildRuntimeExtensionsJson() 下发的正式字段是
 * `displayName`（UI 显示名，如「酒馆助手」「提示词模板」），卡脚本用显示名查就会 miss ——
 * 表现就是「明明装了插件，卡却报未检测到」。
 * 现在对 folder / name / displayName 三个字段做「去空格 + 不区分大小写」的精确匹配。
 */
export function getExtensionManifest(extensionName) {
    try {
        const list = host().extensions || [];
        const want = String(extensionName == null ? '' : extensionName).trim().toLowerCase();
        if (!want) return null;
        const norm = (v) => String(v == null ? '' : v).trim().toLowerCase();
        return list.find((e) => e && (
            norm(e.folder) === want || norm(e.name) === want || norm(e.displayName) === want
        )) || null;
    } catch (_e) { return null; }
}
