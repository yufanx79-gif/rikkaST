/**
 * rikkaST st-compat: variables.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/variables.js` 的局部（chat）与全局（global）变量读写。
 * 注意：setLocalVariable / setGlobalVariable 必须在原变量表上增量合并，严禁整表覆写抹除既有键！
 */

function getCtx() {
    try {
        return (window.SillyTavern && typeof window.SillyTavern.getContext === 'function')
            ? window.SillyTavern.getContext()
            : null;
    } catch (_e) {
        return null;
    }
}

function getLocalMap() {
    const ctx = getCtx();
    if (ctx && ctx.chatMetadata && typeof ctx.chatMetadata === 'object') {
        if (!ctx.chatMetadata.variables || typeof ctx.chatMetadata.variables !== 'object' || Array.isArray(ctx.chatMetadata.variables)) {
            ctx.chatMetadata.variables = {};
        }
        return ctx.chatMetadata.variables;
    }
    return null;
}

function getGlobalMap() {
    const ctx = getCtx();
    if (ctx && ctx.extensionSettings && typeof ctx.extensionSettings === 'object') {
        if (!ctx.extensionSettings.variables || typeof ctx.extensionSettings.variables !== 'object') {
            ctx.extensionSettings.variables = { global: {} };
        }
        if (!ctx.extensionSettings.variables.global || typeof ctx.extensionSettings.variables.global !== 'object') {
            ctx.extensionSettings.variables.global = {};
        }
        return ctx.extensionSettings.variables.global;
    }
    return null;
}

function saveLocal() {
    const ctx = getCtx();
    if (!ctx) return;
    if (typeof ctx.saveMetadataDebounced === 'function') ctx.saveMetadataDebounced();
    else if (typeof ctx.saveMetadata === 'function') ctx.saveMetadata();
}

function saveGlobal() {
    const ctx = getCtx();
    if (!ctx) return;
    if (typeof ctx.saveSettingsDebounced === 'function') ctx.saveSettingsDebounced();
}

export function getLocalVariable(key) {
    try {
        const m = getLocalMap();
        if (m) return m[String(key)];
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.get === 'function') {
            return ctx.variables.local.get(String(key));
        }
        const raw = (window.RikkaBridge && typeof window.RikkaBridge.getLocalVariableJson === 'function')
            ? window.RikkaBridge.getLocalVariableJson()
            : '{}';
        const obj = JSON.parse(raw || '{}');
        return (obj && typeof obj === 'object') ? obj[String(key)] : undefined;
    } catch (_e) {
        return undefined;
    }
}

export function setLocalVariable(key, value) {
    try {
        const m = getLocalMap();
        if (m) {
            m[String(key)] = value;
            saveLocal();
            return value;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.set === 'function') {
            ctx.variables.local.set(String(key), value);
        }
    } catch (_e) { /* noop */ }
    return value;
}

export function deleteLocalVariable(key) {
    try {
        const m = getLocalMap();
        if (m) {
            delete m[String(key)];
            saveLocal();
            return;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.del === 'function') {
            ctx.variables.local.del(String(key));
        }
    } catch (_e) { /* noop */ }
}

export function existsLocalVariable(key) {
    try {
        const m = getLocalMap();
        if (m) return Object.prototype.hasOwnProperty.call(m, String(key));
        return getLocalVariable(key) !== undefined;
    } catch (_e) {
        return false;
    }
}

export function addLocalVariable(key, value) {
    try {
        const cur = getLocalVariable(key);
        const nCur = Number(cur);
        const nVal = Number(value);
        const next = (!Number.isNaN(nCur) && !Number.isNaN(nVal) && cur !== undefined && cur !== '')
            ? (nCur + nVal)
            : (cur === undefined ? value : String(cur) + String(value));
        setLocalVariable(key, next);
        return next;
    } catch (_e) {
        return undefined;
    }
}

export function incrementLocalVariable(key) {
    const next = Number(getLocalVariable(key) || 0) + 1;
    setLocalVariable(key, next);
    return next;
}

export function decrementLocalVariable(key) {
    const next = Number(getLocalVariable(key) || 0) - 1;
    setLocalVariable(key, next);
    return next;
}

export function getGlobalVariable(key) {
    try {
        const m = getGlobalMap();
        if (m) return m[String(key)];
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.get === 'function') {
            return ctx.variables.global.get(String(key));
        }
        const raw = (window.RikkaBridge && typeof window.RikkaBridge.getGlobalVariableJson === 'function')
            ? window.RikkaBridge.getGlobalVariableJson()
            : '{}';
        const obj = JSON.parse(raw || '{}');
        return (obj && typeof obj === 'object') ? obj[String(key)] : undefined;
    } catch (_e) {
        return undefined;
    }
}

export function setGlobalVariable(key, value) {
    try {
        const m = getGlobalMap();
        if (m) {
            m[String(key)] = value;
            saveGlobal();
            return value;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.set === 'function') {
            ctx.variables.global.set(String(key), value);
        }
    } catch (_e) { /* noop */ }
    return value;
}

export function deleteGlobalVariable(key) {
    try {
        const m = getGlobalMap();
        if (m) {
            delete m[String(key)];
            saveGlobal();
            return;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.del === 'function') {
            ctx.variables.global.del(String(key));
        }
    } catch (_e) { /* noop */ }
}

export function existsGlobalVariable(key) {
    try {
        const m = getGlobalMap();
        if (m) return Object.prototype.hasOwnProperty.call(m, String(key));
        return getGlobalVariable(key) !== undefined;
    } catch (_e) {
        return false;
    }
}

export function addGlobalVariable(key, value) {
    try {
        const cur = getGlobalVariable(key);
        const nCur = Number(cur);
        const nVal = Number(value);
        const next = (!Number.isNaN(nCur) && !Number.isNaN(nVal) && cur !== undefined && cur !== '')
            ? (nCur + nVal)
            : (cur === undefined ? value : String(cur) + String(value));
        setGlobalVariable(key, next);
        return next;
    } catch (_e) {
        return undefined;
    }
}

export function incrementGlobalVariable(key) {
    const next = Number(getGlobalVariable(key) || 0) + 1;
    setGlobalVariable(key, next);
    return next;
}

export function decrementGlobalVariable(key) {
    const next = Number(getGlobalVariable(key) || 0) - 1;
    setGlobalVariable(key, next);
    return next;
}
