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

export const extensionTypes = { SYSTEM: 'system', LOCAL: 'local', GLOBAL: 'global' };

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

export function getExtensionManifest(extensionName) {
    try {
        const list = host().extensions || [];
        return list.find((e) => e && (e.folder === extensionName || e.name === extensionName)) || null;
    } catch (_e) { return null; }
}
