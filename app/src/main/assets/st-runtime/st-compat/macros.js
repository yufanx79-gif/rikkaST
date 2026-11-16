/**
 * rikkaST st-compat: macros.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/macros.js` 的最小导出面（P0）。
 * JSR 的真实调用是 `MacrosParser.registerMacro(...)` 静态方法；
 * 之前 shim 只给了实例方法，导致 registerMacros() 抛 TypeError，
 * 后续 `app.mount()` 永远不执行，酒馆助手设置页黑屏。
 *
 * [v234 S2] MacrosParser JS→Kotlin 双向同步：
 * - registerMacro / unregisterMacro 变更后把全量注册表快照推给宿主（emitToHost 'macros_updated'）；
 * - 字符串宏由宿主提示词组装（StMacroSupport dynamicMacros）直接展开；
 * - 函数宏（typeof value === 'function'，ST 用 nonce 调用后 sanitize 返回值）由宿主
 *   MacrosMacroPass 挂起回 JS `__rikkaEvalMacrosBatch` 执行（对齐 ST scripts/macros.js
 *   #registerMacroInNewEngine 的 handler 包装修改：nonce = 宿主生成的 uuid）。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

/** 快照推送去抖（多宏连续注册时合并为一次推送） */
let pushTimer = null;
function pushMacrosSnapshot() {
    if (pushTimer) clearTimeout(pushTimer);
    pushTimer = setTimeout(() => {
        pushTimer = null;
        try {
            const out = {};
            for (const [k, v] of MacrosParser.macros.entries()) {
                out[k] = { type: typeof v === 'function' ? 'function' : 'string', value: typeof v === 'function' ? null : String(v) };
            }
            emitToHostSafely(out);
        } catch (_e) { /* noop */ }
    }, 60);
}

function emitToHostSafely(snapshot) {
    try { callHostBridge('emitEvent', 'macros_updated', JSON.stringify(snapshot)); } catch (_e) { /* noop */ }
}

/** 与 runtime.js 的 callBridge 同语义（macros.js 被 runtime 一并装载，callBridge 在全局） */
function callHostBridge(name) {
    const args = Array.prototype.slice.call(arguments, 1);
    try {
        const bridge = window.RikkaBridge;
        if (bridge && typeof bridge[name] === 'function') {
            return bridge[name].apply(bridge, args);
        }
    } catch (_e) { /* noop */ }
}

export class MacrosParser {
    /** 静态注册表，对齐 ST 的 MacrosParser.registerMacro / getMacro 语义。 */
    static macros = new Map();

    static registerMacro(name, value) {
        try {
            MacrosParser.macros.set(String(name), value);
            if (typeof window !== 'undefined' && window.RikkaBridge) pushMacrosSnapshot();
        } catch (_e) { /* noop */ }
        return value;
    }

    static unregisterMacro(name) {
        try {
            MacrosParser.macros.delete(String(name));
            if (typeof window !== 'undefined' && window.RikkaBridge) pushMacrosSnapshot();
        } catch (_e) { /* noop */ }
    }

    static getMacro(name) {
        return MacrosParser.macros.get(String(name)) ?? null;
    }

    // 兼容个别扩展使用实例写法的老代码。
    constructor() {
        this.macros = MacrosParser.macros;
    }

    registerMacro(name, value) {
        return MacrosParser.registerMacro(name, value);
    }

    unregisterMacro(name) {
        MacrosParser.unregisterMacro(name);
    }

    getMacro(name) {
        return MacrosParser.getMacro(name);
    }
}

/**
 * 最后一条消息的索引。
 *
 * [v246] 补齐 `filter` 参数：ST 1.19 的 `getLastMessageId({filter})` 会从末尾往前找
 * 第一个满足 filter 的消息（Tavern Helper 的 `formatAsDisplayedMessage` 用它定位
 * `last_user` / `last_char` 楼层）。旧实现忽略参数直接返回末位索引，
 * 导致「按角色/按用户定位楼层」永远命中同一条 —— 显示格式化结果错位。
 *
 * 同时补齐 `exclude_swipe_in_propress`（ST 原拼写，保留以对齐）：
 * swipe 进行中的消息不算「最后一条」，否则格式化会读到半成品 swipe。
 *
 * @param {{exclude_swipe_in_propress?: boolean, filter?: ((m:any)=>boolean)|null}} [opts]
 * @returns {number|null} 找不到时返回 null（与 ST 一致，不是 -1）
 */
export function getLastMessageId({ exclude_swipe_in_propress = true, filter = null } = {}) {
    try {
        const c = st().chat || [];
        if (!Array.isArray(c) || c.length === 0) return null;
        for (let i = c.length - 1; i >= 0; i--) {
            const m = c[i];
            if (exclude_swipe_in_propress && m && m.swipes && m.swipe_id >= m.swipes.length) continue;
            if (!filter || filter(m)) return i;
        }
        return null;
    } catch (_e) {
        return null;
    }
}

export function initMacros() {}

export function evaluateMacros(text, env = {}) {
    const s = String(text ?? '');
    try {
        if (typeof window !== 'undefined' && window.RikkaBridge && typeof window.RikkaBridge.substituteParams === 'function') {
            return String(window.RikkaBridge.substituteParams(s, JSON.stringify(env || {})) ?? s);
        }
        const c = st();
        if (c && typeof c.substituteParams === 'function') {
            return String(c.substituteParams(s) ?? s);
        }
    } catch (_e) { /* noop */ }
    return s;
}
