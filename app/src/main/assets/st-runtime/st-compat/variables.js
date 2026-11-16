/**
 * rikkaST st-compat: variables.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/variables.js` 的局部（chat）与全局（global）变量读写及全部 19+ 导出面。
 * 注意：setLocalVariable / setGlobalVariable 在原变量表上增量合并，严禁整表覆写抹除既有键！
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

function computeVariableAdd(cur, value) {
    if (Array.isArray(cur)) {
        const nextArr = cur.slice();
        nextArr.push(value);
        return nextArr;
    }
    if (typeof cur === 'string' && cur.trim().startsWith('[') && cur.trim().endsWith(']')) {
        try {
            const parsed = JSON.parse(cur);
            if (Array.isArray(parsed)) {
                parsed.push(value);
                return JSON.stringify(parsed);
            }
        } catch (_e) { /* fallthrough */ }
    }
    if (cur === undefined || cur === null) {
        const nVal = Number(value);
        return (!Number.isNaN(nVal) && String(value).trim() !== '') ? nVal : value;
    }
    const nCur = Number(cur);
    const nVal = Number(value);
    if (!Number.isNaN(nCur) && !Number.isNaN(nVal) && String(cur).trim() !== '' && String(value).trim() !== '') {
        return nCur + nVal;
    }
    const base = (cur === 0 || cur === '0' || cur === '') ? '' : String(cur);
    return base + String(value ?? '');
}

export function getLocalVariable(key, args = {}) {
    try {
        const m = getLocalMap();
        let val;
        if (m) {
            val = m[String(key)];
        } else {
            const ctx = getCtx();
            if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.get === 'function') {
                val = ctx.variables.local.get(String(key));
            } else {
                const raw = (window.RikkaBridge && typeof window.RikkaBridge.getLocalVariableJson === 'function')
                    ? window.RikkaBridge.getLocalVariableJson()
                    : '{}';
                const obj = JSON.parse(raw || '{}');
                val = (obj && typeof obj === 'object') ? obj[String(key)] : undefined;
            }
        }
        if (args && args.index !== undefined && args.index !== null && val !== undefined) {
            try {
                const parsed = typeof val === 'string' ? JSON.parse(val) : val;
                if (parsed && typeof parsed === 'object') {
                    const sub = parsed[args.index];
                    return typeof sub === 'object' && sub !== null ? JSON.stringify(sub) : sub;
                }
            } catch (_e) { return undefined; }
        }
        return val;
    } catch (_e) {
        return undefined;
    }
}

export function setLocalVariable(key, value, args = {}) {
    try {
        let finalValue = value;
        if (args && args.index !== undefined && args.index !== null) {
            const cur = getLocalVariable(key);
            let container = {};
            try { container = typeof cur === 'string' && cur ? JSON.parse(cur) : (cur && typeof cur === 'object' ? cur : {}); } catch (_e) { container = {}; }
            container[args.index] = value;
            finalValue = typeof cur === 'string' ? JSON.stringify(container) : container;
        }
        const m = getLocalMap();
        if (m) {
            m[String(key)] = finalValue;
            saveLocal();
            return finalValue;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.set === 'function') {
            ctx.variables.local.set(String(key), finalValue);
        }
        return finalValue;
    } catch (_e) {
        return value;
    }
}

export function deleteLocalVariable(key) {
    try {
        const m = getLocalMap();
        if (m) {
            delete m[String(key)];
            saveLocal();
            return '';
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.local && typeof ctx.variables.local.del === 'function') {
            ctx.variables.local.del(String(key));
        }
    } catch (_e) { /* noop */ }
    return '';
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
        const next = computeVariableAdd(cur, value);
        setLocalVariable(key, next);
        return next;
    } catch (_e) {
        return undefined;
    }
}

export function incrementLocalVariable(key) {
    return addLocalVariable(key, 1);
}

export function decrementLocalVariable(key) {
    return addLocalVariable(key, -1);
}

export function getGlobalVariable(key, args = {}) {
    try {
        const m = getGlobalMap();
        let val;
        if (m) {
            val = m[String(key)];
        } else {
            const ctx = getCtx();
            if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.get === 'function') {
                val = ctx.variables.global.get(String(key));
            } else {
                const raw = (window.RikkaBridge && typeof window.RikkaBridge.getGlobalVariableJson === 'function')
                    ? window.RikkaBridge.getGlobalVariableJson()
                    : '{}';
                const obj = JSON.parse(raw || '{}');
                val = (obj && typeof obj === 'object') ? obj[String(key)] : undefined;
            }
        }
        if (args && args.index !== undefined && args.index !== null && val !== undefined) {
            try {
                const parsed = typeof val === 'string' ? JSON.parse(val) : val;
                if (parsed && typeof parsed === 'object') {
                    const sub = parsed[args.index];
                    return typeof sub === 'object' && sub !== null ? JSON.stringify(sub) : sub;
                }
            } catch (_e) { return undefined; }
        }
        return val;
    } catch (_e) {
        return undefined;
    }
}

export function setGlobalVariable(key, value, args = {}) {
    try {
        let finalValue = value;
        if (args && args.index !== undefined && args.index !== null) {
            const cur = getGlobalVariable(key);
            let container = {};
            try { container = typeof cur === 'string' && cur ? JSON.parse(cur) : (cur && typeof cur === 'object' ? cur : {}); } catch (_e) { container = {}; }
            container[args.index] = value;
            finalValue = typeof cur === 'string' ? JSON.stringify(container) : container;
        }
        const m = getGlobalMap();
        if (m) {
            m[String(key)] = finalValue;
            saveGlobal();
            return finalValue;
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.set === 'function') {
            ctx.variables.global.set(String(key), finalValue);
        }
        return finalValue;
    } catch (_e) {
        return value;
    }
}

export function deleteGlobalVariable(key) {
    try {
        const m = getGlobalMap();
        if (m) {
            delete m[String(key)];
            saveGlobal();
            return '';
        }
        const ctx = getCtx();
        if (ctx && ctx.variables && ctx.variables.global && typeof ctx.variables.global.del === 'function') {
            ctx.variables.global.del(String(key));
        }
    } catch (_e) { /* noop */ }
    return '';
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
        const next = computeVariableAdd(cur, value);
        setGlobalVariable(key, next);
        return next;
    } catch (_e) {
        return undefined;
    }
}

export function incrementGlobalVariable(key) {
    return addGlobalVariable(key, 1);
}

export function decrementGlobalVariable(key) {
    return addGlobalVariable(key, -1);
}

export function resolveVariable(name, scope = null) {
    const k = String(name ?? '').trim();
    if (!k) return '';
    if (scope && typeof scope === 'object' && typeof scope.getVariable === 'function') {
        const sv = scope.getVariable(k);
        if (sv !== undefined) return sv;
    }
    if (existsLocalVariable(k)) return getLocalVariable(k);
    if (existsGlobalVariable(k)) return getGlobalVariable(k);
    return name;
}

export function parseBooleanOperands(args = {}) {
    const a = resolveVariable(args.a ?? args.left ?? '');
    const b = resolveVariable(args.b ?? args.right ?? '');
    const rule = String(args.rule ?? args.op ?? 'eq').trim().toLowerCase();
    return { a, b, rule };
}

/**
 * 布尔比较（ST variables.js:494-560 语义）。
 *
 * ST 签名是 **位置参数** `evalBoolean(rule, a, b)`，我们之前写成
 * `evalBoolean(expressionOrArgs, scope)`（对象形式）—— 两套调用约定不兼容：
 * 脚本按 ST 写 `evalBoolean('gt', 5, 3)` 会拿到错误结果。
 *
 * 这里同时支持两种调用形态（ST 位置参数优先，对象形式保留向后兼容）：
 *   evalBoolean('gt', 5, 3)              → true
 *   evalBoolean(undefined, 'yes')        → true（真值判定）
 *   evalBoolean('not', 'no')             → true
 *   evalBoolean({ rule: 'gt', a: 5, b: 3 }) → true（旧对象形式）
 *
 * @param {string|object|undefined} rule 比较规则，或旧形式的对象
 * @param {*} a 左操作数
 * @param {*} b 右操作数
 * @returns {boolean}
 */
export function evalBoolean(rule, a, b) {
    // 旧对象形式：evalBoolean({rule, a, b})
    if (rule && typeof rule === 'object' && !Array.isArray(rule)) {
        const o = rule;
        return evalBoolean(o.rule, o.a, o.b);
    }
    // 旧双参形式：evalBoolean(expressionString, scope) —— 把表达式交给斜杠求值器
    if (typeof rule === 'string' && a !== undefined && b === undefined && /[<>=!]/.test(rule)) {
        try {
            const m = rule.match(/^\s*(.+?)\s*(>=|<=|!=|==|>|<)\s*(.+?)\s*$/);
            if (m) return evalBoolean(
                { '>': 'gt', '<': 'lt', '>=': 'gte', '<=': 'lte', '!=': 'neq', '==': 'eq' }[m[2]],
                m[1], m[3],
            );
        } catch (_e) { /* fallthrough */ }
    }

    const isTrue = (v) => {
        const t = String(v ?? '').trim().toLowerCase();
        return t === 'true' || t === 'yes' || t === '1' || t === 'on';
    };
    const isFalse = (v) => {
        const t = String(v ?? '').trim().toLowerCase();
        return t === 'false' || t === 'no' || t === '0' || t === 'off' || t === '';
    };

    // ST :495-512 —— 只给一个操作数时做真值判定
    if (a === undefined) {
        throw new Error('Left operand is not provided');
    }
    if (b === undefined) {
        if (rule === undefined || rule === null || rule === '' || rule === 'not') {
            const wantTruthy = rule !== 'not';
            if (isTrue(a)) return wantTruthy;
            if (isFalse(a)) return !wantTruthy;
            return wantTruthy ? !!a : !a;
        }
        throw new Error(`Unknown boolean comparison rule for truthy check. If right operand is not provided, the rule must not provided or be 'not'. Provided: ${rule}`);
    }

    // ST :515 —— 没给规则时默认 eq
    const r = (rule === undefined || rule === null || rule === '') ? 'eq' : String(rule);
    const na = Number(a), nb = Number(b);
    const bothNum = typeof a === 'number' && typeof b === 'number';

    if (bothNum) {
        switch (r) {
            case 'gt': return na > nb;
            case 'gte': return na >= nb;
            case 'lt': return na < nb;
            case 'lte': return na <= nb;
            case 'eq': case '==': return na === nb;
            case 'neq': case '!=': return na !== nb;
            default: break;
        }
    }
    const sa = String(a), sb = String(b);
    switch (r) {
        case 'eq': case '==': return sa === sb;
        case 'neq': case '!=': return sa !== sb;
        case 'gt': return sa > sb;
        case 'gte': return sa >= sb;
        case 'lt': return sa < sb;
        case 'lte': return sa <= sb;
        case 'in': return sb.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean).includes(sa);
        case 'nin': return !sb.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean).includes(sa);
        case 'not': return !isTrue(a);
        default:
            throw new Error(`Unknown boolean comparison rule: ${rule}`);
    }
}

export function listVariables({ scope = 'local' } = {}) {
    if (scope === 'global') {
        return Object.assign({}, getGlobalMap() || {});
    }
    return Object.assign({}, getLocalMap() || {});
}

export function flushVariables({ scope = 'local' } = {}) {
    if (scope === 'global') {
        const m = getGlobalMap();
        if (m) {
            for (const k of Object.keys(m)) delete m[k];
            saveGlobal();
        }
        return;
    }
    const m = getLocalMap();
    if (m) {
        for (const k of Object.keys(m)) delete m[k];
        saveLocal();
    }
}

export function getVariableMacros() {
    return {
        getvar: getLocalVariable,
        setvar: setLocalVariable,
        addvar: addLocalVariable,
        incvar: incrementLocalVariable,
        decvar: decrementLocalVariable,
        getglobalvar: getGlobalVariable,
        setglobalvar: setGlobalVariable,
        addglobalvar: addGlobalVariable,
        incglobalvar: incrementGlobalVariable,
        decglobalvar: decrementGlobalVariable,
    };
}

export function registerVariableCommands() {}

export const localVariable = {
    get: getLocalVariable,
    set: setLocalVariable,
    add: addLocalVariable,
    del: deleteLocalVariable,
    exists: existsLocalVariable,
    inc: incrementLocalVariable,
    dec: decrementLocalVariable,
};
