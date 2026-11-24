/**
 * Tavern Runtime — 脚本按钮 Shim（per-script + 宿主上报）
 *
 * 对齐 JSR 语义：每个脚本（iframe）拥有独立的按钮集与按钮事件名；
 * 注册/变更时通过 window.RikkaBridge.onScriptButtons 上报 Kotlin 宿主，
 * 由聊天界面统一渲染按钮；点击经 __rikkaButtonHub.fire 派发回脚本事件。
 */

import { eventEmit } from './event-shims.js';
import { deepClone } from './clone-util.js';

/**
 * [v250 根治] 单一按钮存储：声明式 + 运行时两路来源合并进同一个 store。
 *
 * 旧实现有两套互不相通的存储：
 *   1. 声明式（脚本 JSON 的 `button:{enabled,buttons:[...]}`）—— runtime.js 的
 *      `syncDeclaredScriptButtons()` 现算数组后**直接** `RikkaBridge.onScriptButtons()`，
 *      从不写入本 store；
 *   2. 运行时（脚本调 `appendInexistentScriptButtons()` 等）—— 写本 store，
 *      `_notify()` 用 `allEnabledButtons()`（只含 store 内条目）整包上报。
 *
 * 于是任何脚本在运行期注册一次按钮，就把声明式按钮**整包替换**掉。
 * 真机日志（2026-11-24 18:18:23→18:18:24）：
 *   n=3 [4A:🛡️ 防路由状态, 4A:⚙️ 切换防路由模式, 双马尾:⚙ 双马尾插件]
 *   n=1 [双马尾:⚙ 双马尾插件]        ← 前两个被顶掉
 *
 * 现在两路都写进本 store，`_notify()` 是唯一上报出口，天然是并集：
 *   - `setDeclared(scriptId, name, buttons)`：整组替换该脚本的声明式按钮（脚本重载/禁用时会被 prune）；
 *   - `append/replace/update`：运行时按钮（JSR 语义不变）；
 *   - `allEnabledButtons()`：声明式在前、运行时在后，按 name 去重。
 */

/** scriptId -> { scriptName, declared: [{name, visible}], runtime: [{name, visible}], events: Map<name, eventName> } */
const _store = new Map();

function _ensure(scriptId, scriptName) {
    let entry = _store.get(scriptId);
    if (!entry) {
        entry = { scriptName: String(scriptName || scriptId), declared: [], runtime: [], events: new Map() };
        _store.set(scriptId, entry);
    } else if (scriptName) {
        entry.scriptName = String(scriptName);
    }
    return entry;
}

function _isEmpty(entry) {
    return entry.declared.length === 0 && entry.runtime.length === 0;
}

import { getStringHash } from '../st-compat/utils.js';

/**
 * [v247 BUGFIX] 按钮事件名必须与 Tavern Helper 上游契约**逐字一致**。
 *
 * 上游真源（`src/function/script.ts` + `src/store/script_iframe_runtimes.ts`）：
 * ```
 * function getButtonId(script_id, button_name) {
 *     return `${script_id}_${getStringHash(button_name)}`;   // rikkast-entry.js:11127-11129
 * }
 * function _getButtonEvent(button_name) {
 *     return getButtonId(String(_getScriptId.call(this)), button_name);  // :18930-18932
 * }
 * function _eventOnButton(event_type, listener) {
 *     _eventOn.call(this, _getButtonEvent.call(this, event_type), listener);  // :19029-19031
 * }
 * ```
 * 即脚本调 `eventOnButton('打开面板', fn)` 时，它监听的事件名是
 * `<scriptId>_<hash('打开面板')>`（`getStringHash` = ST `public/scripts/utils.js:522` 的 cyrb53 变体）。
 *
 * 我们此前用的是自造格式 `rikka_button_<base36>` —— 和上游**永远不匹配**，
 * 于是「按钮能显示、点了没反应」：宿主 `fireButton()` emit 的事件名，
 * 脚本根本没在监听。这就是 KEMINI / TRANS-AM 控制台点击无反应的根因。
 *
 * 修法：直接复用 st-compat 里那份与 ST 逐字对齐的 `getStringHash`，拼出上游同名事件。
 */
function _eventNameOf(scriptId, entry, name) {
    if (!entry.events.has(name)) {
        entry.events.set(name, `${String(scriptId)}_${getStringHash(String(name))}`);
    }
    return entry.events.get(name);
}

function _normalize(buttons) {
    return (buttons || [])
        .filter(b => b && typeof b.name === 'string')
        .map(b => ({ name: b.name, visible: b.visible !== false }));
}

// ── 核心 API（带 scriptId 上下文）──

/** 该脚本当前对外的按钮列表（声明式优先，运行时补充；用于 getScriptButtons 语义）。 */
function _mergedButtons(entry) {
    const out = [];
    const seen = new Set();
    for (const btn of entry.declared) {
        if (seen.has(btn.name)) continue;
        seen.add(btn.name);
        out.push(btn);
    }
    for (const btn of entry.runtime) {
        if (seen.has(btn.name)) continue;
        seen.add(btn.name);
        out.push(btn);
    }
    return out;
}

export function appendButtons(scriptId, scriptName, buttons) {
    const entry = _ensure(scriptId, scriptName);
    for (const btn of _normalize(buttons)) {
        if (!entry.runtime.some(b => b.name === btn.name)) {
            entry.runtime.push(btn);
        }
    }
    _notify();
    return deepClone(_mergedButtons(entry));
}

export function replaceButtons(scriptId, scriptName, buttons) {
    const entry = _ensure(scriptId, scriptName);
    entry.runtime = _normalize(buttons);
    _notify();
    return deepClone(_mergedButtons(entry));
}

export function updateButtons(scriptId, scriptName, updater) {
    const entry = _ensure(scriptId, scriptName);
    let next;
    try {
        next = updater(deepClone(_mergedButtons(entry)));
    } catch (e) {
        next = _mergedButtons(entry);
    }
    // JSR 语义：update 作用在整个可见集上，因此结果写回运行时层，
    // 声明式层被显式替换（否则会与 upstream 的「整组替换」语义打架）。
    entry.declared = [];
    entry.runtime = _normalize(next);
    _notify();
    return deepClone(_mergedButtons(entry));
}

/**
 * [v250] 声明式按钮整组同步（来自脚本 JSON 的 `button.buttons`）。
 *
 * 由 runtime.js 的 `syncDeclaredScriptButtons()` 在每次脚本集合变化时调用：
 * - 传入该脚本当前的声明按钮数组 → 整组替换（重复调用幂等）；
 * - 传入空数组 → 清空该脚本的声明按钮（脚本被禁用/删除时不会留下僵尸按钮）。
 */
export function setDeclaredButtons(scriptId, scriptName, buttons) {
    const entry = _ensure(scriptId, scriptName);
    entry.declared = _normalize(buttons);
    if (_isEmpty(entry)) _store.delete(scriptId);
    _notify();
    return deepClone(_mergedButtons(entry));
}

/**
 * [v250] 按脚本集合整体裁剪：只保留仍启用的脚本，其余声明式/运行时按钮一并移除。
 *
 * 为什么必须做：脚本被禁用后，它的 iframe 会被拆掉，但 store 里的按钮记录会残留 ——
 * 旧实现下声明式按钮本来就不进 store（所以「禁用后按钮消失」是碰巧对的）；
 * 合并存储之后必须显式裁剪，否则会出现「脚本已停用，按钮还在」。
 */
export function retainScripts(scriptIds) {
    const keep = new Set((scriptIds || []).map(String));
    let changed = false;
    for (const id of Array.from(_store.keys())) {
        if (!keep.has(String(id))) {
            _store.delete(id);
            changed = true;
        }
    }
    // 显式禁用（disabled）的脚本也要清掉声明式按钮
    if (changed) _notify();
    return changed;
}

export function listButtons(scriptId) {
    const entry = _store.get(scriptId);
    return entry ? deepClone(_mergedButtons(entry)) : [];
}

export function buttonEvent(scriptId, name) {
    const entry = _ensure(scriptId, String(scriptId));
    return _eventNameOf(scriptId, entry, String(name));
}

export function fireButton(scriptId, name) {
    // [batch17] 点击链路埋点：主页面里到底认不认得这个 scriptId
    try {
        if (window.RikkaBridge && RikkaBridge.log) {
            RikkaBridge.log('debug', '[buttons] fire ' + scriptId + '::' + name +
                ' known=' + _store.has(scriptId) + ' owner=' + String(window.__rikkaOwnerTag || 'main'));
        }
    } catch (_e) { /* noop */ }
    const evt = buttonEvent(scriptId, name);
    try {
        eventEmit(evt);
        return true;
    } catch (e) {
        return false;
    }
}

/** 汇总全部脚本的可见按钮（供宿主渲染）：声明式在前、运行时在后，按脚本+名字去重。 */
export function allEnabledButtons() {
    const out = [];
    _store.forEach((entry, scriptId) => {
        for (const btn of _mergedButtons(entry)) {
            if (btn.visible === false) continue;
            out.push({
                scriptId: String(scriptId),
                scriptName: entry.scriptName,
                name: btn.name,
                event: _eventNameOf(scriptId, entry, btn.name),
            });
        }
    });
    return out;
}

function _notify() {
    try {
        const payload = JSON.stringify(allEnabledButtons());
        if (window.RikkaBridge && typeof window.RikkaBridge.onScriptButtons === 'function') {
            window.RikkaBridge.onScriptButtons(payload);
        }
    } catch (e) { /* noop */ }
    try { _renderLegacy(); } catch (e) { /* noop */ }
}

/**
 * [v250] 声明式按钮批量同步（runtime.js 每次脚本集合变化时调用一次）。
 *
 * @param {Array<{scriptId:string, scriptName:string, buttons:Array<{name:string, visible?:boolean}>}>} list
 *        本次「启用中」脚本的全部声明按钮；不在 list 里的脚本，其声明按钮会被清空。
 * @param {string[]} keepScriptIds 当前仍启用的脚本 id（用于裁剪僵尸条目）。
 * @returns {Array} 合并后的全量按钮（可直接上报宿主）
 */
export function syncDeclared(list, keepScriptIds) {
    // 1) 先清空所有脚本的声明层（只保留运行时层），避免脚本被禁用后残留
    for (const entry of _store.values()) entry.declared = [];
    // 2) 写入本次声明
    for (const item of list || []) {
        if (!item) continue;
        const id = String(item.scriptId == null ? '' : item.scriptId);
        if (!id) continue;
        const entry = _ensure(id, item.scriptName);
        entry.declared = _normalize(item.buttons);
    }
    // 3) 裁剪：脚本已被删除/禁用的，连同运行时层一起移除
    const keep = new Set((keepScriptIds || []).map(String));
    for (const id of Array.from(_store.keys())) {
        const sid = String(id);
        if (sid === '__global__') continue; // 全局兜底按钮不受脚本集合约束
        if (!keep.has(sid)) _store.delete(id);
    }
    _notify();
    return allEnabledButtons();
}

// ── 兼容导出（无 scriptId 上下文时的全局兜底；脚本 iframe 内由 predefine 覆盖为 per-script 版本）──

export function getScriptButtons() {
    return allEnabledButtons();
}

export function replaceScriptButtons(buttons) {
    return replaceButtons('__global__', '__global__', buttons);
}

export function updateScriptButtonsWith(updater) {
    return updateButtons('__global__', '__global__', updater);
}

export function appendInexistentScriptButtons(buttons) {
    return appendButtons('__global__', '__global__', buttons);
}

export function getButtonEvent(buttonName) {
    return buttonEvent('__global__', buttonName);
}

// ── 遗留容器渲染（若 runtime 页面存在 #mvu-standalone-buttons 则同步显示）──

function _renderLegacy() {
    const container = document.getElementById('mvu-standalone-buttons');
    if (!container) return;
    container.innerHTML = '';
    for (const item of allEnabledButtons()) {
        const el = document.createElement('div');
        el.className = 'menu_button';
        el.textContent = item.name;
        el.addEventListener('click', () => fireButton(item.scriptId, item.name));
        container.appendChild(el);
    }
}

// ── 宿主 Hub（挂在 window 上，脚本 iframe 经 parent 访问）──

export const buttonHub = {
    append: appendButtons,
    replace: replaceButtons,
    update: updateButtons,
    list: listButtons,
    event: buttonEvent,
    fire: fireButton,
    all: allEnabledButtons,
    notify: _notify,
    // [v250] 声明式按钮统一入 store（runtime.js syncDeclaredScriptButtons 调用）
    setDeclared: setDeclaredButtons,
    retain: retainScripts,
    syncDeclared,
};

try { window.__rikkaButtonHub = buttonHub; } catch (e) { /* noop */ }

