/**
 * MVU Standalone — 工具函数 Shim
 */

import { eventEmit, eventOn } from './event-shims.js';

// 伪装版本号（>= 4.0.14 以启用所有 MVU 功能）
const SPOOFED_VERSION = '4.7.7';

export function getTavernHelperVersion() {
    return SPOOFED_VERSION;
}

export function getTavernVersion() {
    try {
        return window.SillyTavern.getContext().getVersion?.() ?? '1.13.4';
    } catch {
        return '1.13.4';
    }
}

// 注意: 保留原始拼写错误 "substitude"
export function substitudeMacros(text) {
    try {
        const ctx = window.SillyTavern.getContext();
        return (ctx.substituteParams || ctx.substituteParamsExtended || (t => t))(text);
    } catch {
        return text;
    }
}

export function getLastMessageId() {
    try {
        const chat = window.SillyTavern.getContext().chat;
        return chat.length - 1;
    } catch {
        return -1;
    }
}

// 批次十三：面板 WebView 也要知道「我是哪条消息」。
// Kotlin 侧（MessageHtmlBlock）在挂 HTML 前把对应 MessageNode.id 写进
// window.__rikkaPanelMessageKey；runtime.js 把它换算成 chat 数组下标后存到
// window.__rikkaPanelMessageKeyIndex。这里返回该下标，而不是写死的 -1。
// msg_id=-1 在 JSR 里的含义是「最后一条非系统消息」，对单条消息的面板没意义。
export function getCurrentMessageId() {
    try {
        const k = window.__rikkaPanelMessageKey;
        if (k) {
            const chat = (window.SillyTavern.getContext().chat) || [];
            for (let i = 0; i < chat.length; i++) {
                if (chat[i] && String(chat[i].id) === String(k)) {
                    window.__rikkaPanelMessageKeyIndex = i;
                    return i;
                }
            }
            if (typeof window.__rikkaPanelMessageKeyIndex === 'number') {
                return window.__rikkaPanelMessageKeyIndex;
            }
        }
    } catch (_e) { /* noop */ }
    return getLastMessageId();
}

export function getScriptId() {
    return 'mvu-standalone';
}

export function getScriptName() {
    return 'MVU变量框架';
}

export function getScriptInfo() {
    return 'MVU Standalone Extension';
}

export function replaceScriptInfo(_info) {
    // no-op
}

export function getIframeName() {
    return 'TH-script--MVU变量框架--mvu-standalone';
}

export function reloadIframe() {
    // 触发 MVU iframe 重新加载
    console.warn('[MVU-Standalone] reloadIframe called — not supported in standalone mode');
}

export function errorCatched(fn) {
    return function (...args) {
        try {
            return fn.apply(this, args);
        } catch (e) {
            console.error('[MVU-Standalone] Error:', e);
            if (window.toastr) {
                toastr.error(e.message, '[MVU] 错误');
            }
        }
    };
}

// Global 共享机制
const _globalValues = new Map();
const _globalWaiters = new Map();

export function initializeGlobal(name, value) {
    _globalValues.set(name, value);
    // 设置到 iframe 自身
    window[name] = value;
    // 设置到 parent（SillyTavern 主窗口）
    try {
        window.parent[name] = value;
    } catch { /* cross-origin fallback */ }

    eventEmit(`global_${name}_initialized`);

    // 解决等待者
    const waiters = _globalWaiters.get(name);
    if (waiters) {
        for (const resolve of waiters) resolve(value);
        _globalWaiters.delete(name);
    }
}

export function waitGlobalInitialized(name) {
    // 检查 iframe 自身
    if (window[name] !== undefined) return Promise.resolve(window[name]);
    // 检查 parent
    try {
        if (window.parent[name] !== undefined) return Promise.resolve(window.parent[name]);
    } catch { /* cross-origin */ }
    // 检查已注册
    if (_globalValues.has(name)) return Promise.resolve(_globalValues.get(name));

    return new Promise(resolve => {
        if (!_globalWaiters.has(name)) _globalWaiters.set(name, []);
        _globalWaiters.get(name).push(resolve);

        // 也监听事件
        eventOn(`global_${name}_initialized`, () => {
            resolve(window[name] ?? window.parent?.[name]);
        });
    });
}

export function registerMacroLike(_name, _fn) {
    // MVU 不使用这个，但提供空实现避免报错
    console.debug('[MVU-Standalone] registerMacroLike called (no-op)');
}

/**
 * JSR `triggerSlash`：在宿主执行一行 STscript，返回管道结果（Promise<string>）。
 * 底层为 RikkaBridge.triggerSlash 同步桥的 Promise 包装；宿主未接线时返回 ''。
 */
export function triggerSlash(command) {
    return Promise.resolve().then(() => {
        try {
            const fn = window.RikkaBridge && window.RikkaBridge.triggerSlash;
            if (typeof fn !== 'function') return '';
            const result = fn(String(command ?? ''));
            return result === undefined || result === null ? '' : String(result);
        } catch (e) {
            console.warn('[RikkaTavern] triggerSlash failed:', e);
            return '';
        }
    });
}
