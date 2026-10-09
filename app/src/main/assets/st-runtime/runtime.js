/**
 * rikkaST 酒馆运行时（ST / JSR / MVU 宿主）
 * ============================================
 * 设计来源（不造轮子）：
 *  - MVU Standalone (uuuiiiiooo/mvu-sillytavern-extension) 的 shim + blob iframe 模板
 *  - TauriTavern "资源端点原生化" 哲学：不解释扩展代码，只提供资源与 API 表面
 *  - SillyTavern 1.18 原版 events.js / eventemitter.js（事件总线）
 *
 * 数据流：
 *  Kotlin (TavernVariableStore) ← @JavascriptInterface → 本页 state 工作副本
 *  本页 → blob iframe（MVU bundle）通过 window.__mvu_shims__ / SillyTavern 代理
 *  Kotlin → 本页事件：window.__rikkaEmit(name, payloadJson)
 */
import { eventSource, event_types } from './vendor/st/events.js';

// ---------------------------------------------------------------- shims 装载
import * as shimConstants from './shims/constants.js';
import * as shimEvent from './shims/event-shims.js';
import * as shimVariable from './shims/variable-shims.js';
import * as shimChat from './shims/chat-shims.js';
import * as shimLorebook from './shims/lorebook-shims.js';
import * as shimButton from './shims/button-shims.js';
import * as shimUtil from './shims/util-shims.js';
import * as shimInject from './shims/inject-shims.js';
import * as ejsRender from './shims/ejs-render.js';
import * as mathRender from './shims/math-render.js';
import { deepClone } from './shims/clone-util.js';
import { normalizeStCharacter } from './character-shape.js';
// [v214] ST ctx.powerUserSettings（recon §2.9：ST-PT persona 域）——零依赖模块，直接复用 st-compat 导出。
import { power_user as stCompatPowerUser } from './st-compat/power-user.js';
// [v236 R1] ST `#chat` 楼层 DOM 桩（updateMessageBlock / addCopyToCodeBlocks / appendMediaToMessage）
import * as chatDom from './st-compat/chat-dom.js';

// ---------------------------------------------------------------- ESM 库（zod / yaml）
import * as zodModule from './vendor/lib/zod.esm.js';
import * as yamlModule from './vendor/lib/yaml.esm.js';

const LOG_PREFIX = '[RikkaTavern]';

// ============================================================================
// [v229 A1] /api/backends/chat-completions/{status,generate} 兼容路由（JS 侧）
//
// 为什么必须在 JS 侧接：Android WebView 的 shouldInterceptRequest **拿不到 POST body**，
// 而 JSR 的 generate() 走 POST + 请求体（responseGenerator.ts:343/:438）。所以：
//   1) 这里拦 fetch（install 在主模块最早期，早于任何第三方扩展/JSR 装载）；
//   2) 经 RikkaBridge 把请求体交给 Kotlin 宿主生成管线；
//   3) Kotlin 逐块经 __rikkaApiChunk 回推，这里用 ReadableStream 还原成真实 fetch 响应。
// 兼容 JSR 的两条路径：status 只探模型；generate 支持 SSE（含 [DONE]）与 stream:false。
// ============================================================================
(function installChatCompletionsFetchPatch() {
    try {
        if (window.__rikkaApiFetchPatched) return;
        window.__rikkaApiFetchPatched = true;
        const STATUS_PATH = '/api/backends/chat-completions/status';
        const GENERATE_PATH = '/api/backends/chat-completions/generate';
        const originalFetch = window.fetch ? window.fetch.bind(window) : null;

        // ---- 请求体读取（fetch(url, {body}) / fetch(new Request(url, {body})) 两种写法）----
        const readBody = async (url, options) => {
            try {
                if (options && typeof options.body === 'string') return options.body;
                if (url && typeof url === 'object' && typeof url.clone === 'function') {
                    return await url.clone().text();
                }
            } catch (_e) { /* noop */ }
            return '';
        };
        const urlOf = (url) => {
            try {
                if (typeof url === 'string') return url;
                if (url && typeof url.url === 'string') return url.url;
            } catch (_e) { /* noop */ }
            return '';
        };
        const pathOf = (u) => {
            try {
                const i = String(u).indexOf('/api/');
                return i >= 0 ? String(u).slice(i) : String(u);
            } catch (_e) { return ''; }
        };

        window.__rikkaApiChunk = function (reqId, payload) {
            try {
                const q = window.__rikkaApiPending && window.__rikkaApiPending[reqId];
                if (q) q.push(payload);
            } catch (_e) { /* noop */ }
        };
        window.__rikkaApiFinish = function (reqId) {
            try {
                const s = window.__rikkaApiStreams && window.__rikkaApiStreams[reqId];
                if (!s) return;
                delete window.__rikkaApiStreams[reqId];
                if (typeof s.close === 'function') s.close();
            } catch (_e) { /* noop */ }
        };

        const jsonResponse = (payload, status) => new Response(payload, {
            status: status || 200,
            headers: { 'Content-Type': 'application/json' },
        });

        const handleGenerate = async (url, options) => {
            const body = await readBody(url, options);
            const reqId = 'a1-' + Date.now() + '-' + Math.random().toString(36).slice(2);
            window.__rikkaApiPending = window.__rikkaApiPending || {};
            window.__rikkaApiStreams = window.__rikkaApiStreams || {};
            window.__rikkaApiPending[reqId] = [];
            const queue = window.__rikkaApiPending[reqId];
            let wake = null;
            const pump = () => { if (wake) { const w = wake; wake = null; w(); } };
            window.__rikkaApiStreams[reqId] = {
                close: function () { delete window.__rikkaApiPending[reqId]; pump(); },
            };
            const encoder = new TextEncoder();
            const stream = new ReadableStream({
                start: function (controller) {
                    (async function () {
                        try {
                            callBridge('chatCompletionStart', reqId, body);
                            while (true) {
                                if (!queue.length) {
                                    if (!window.__rikkaApiPending[reqId]) { controller.close(); return; }
                                    await new Promise(function (res) { wake = res; });
                                    continue;
                                }
                                const payload = queue.shift();
                                controller.enqueue(encoder.encode('data: ' + payload + '\\n\\n'));
                            }
                        } catch (err) {
                            try { controller.error(err); } catch (_e) { /* noop */ }
                        }
                    })();
                },
            });
            return new Response(stream, {
                status: 200,
                headers: { 'Content-Type': 'text/event-stream' },
            });
        };

        const handleStatus = async () => {
            try {
                const res = callBridge('chatCompletionStatus', '');
                if (typeof res === 'string' && res.length) return jsonResponse(res, 200);
            } catch (e) {
                callBridge('log', 'warn', '[a1] chatCompletionStatus bridge failed: ' + String(e && e.message || e));
            }
            return jsonResponse('{"object":"list","data":[],"model":""}', 200);
        };

        window.fetch = function (url, options) {
            try {
                const p = pathOf(urlOf(url));
                if (p.indexOf(STATUS_PATH) === 0) return handleStatus();
                if (p.indexOf(GENERATE_PATH) === 0) return handleGenerate(url, options);
            } catch (e) {
                callBridge('log', 'warn', '[a1] fetch patch error: ' + String(e && e.message || e));
            }
            return originalFetch ? originalFetch(url, options) : Promise.reject(new Error('fetch unavailable'));
        };
        console.log(LOG_PREFIX + ' [a1] chat-completions fetch patch installed');
    } catch (__a1e) {
        try { callBridge('log', 'error', '[a1] fetch patch install failed: ' + String(__a1e && __a1e.message || __a1e)); } catch (_e) { /* noop */ }
    }
})();
// [v207] 主页面也要有全局错误闸门。以前 error / unhandledrejection 只在 panel 模式与 script iframe 里转发，
// main 模式是静默的 —— 「扩展抛错但不影响加载」这类问题完全看不见（B 项被误判两版就是这么来的：
// `#send_form` 缺失导致的 MutationObserver 报错，只在用户看得见的 toast 里露过一次）。
(function installGlobalErrorForwarder() {
    try {
        if (window.__rikkaErrFwd) return;
        window.__rikkaErrFwd = true;
        window.addEventListener('error', (e) => {
            try {
                const msg = (e && (e.message || (e.error && e.error.stack))) || e;
                callBridge('log', 'error', `[global] error: ${String(msg)}\n  at ${String((e && e.filename) || '')}:${String((e && e.lineno) || '')}`);
            } catch (_e) { /* noop */ }
        }, true);
        window.addEventListener('unhandledrejection', (e) => {
            try {
                const r = e && e.reason;
                callBridge('log', 'error', `[global] unhandledrejection: ${String((r && (r.stack || r.message)) || r)}`);
            } catch (_e) { /* noop */ }
        });
    } catch (_e) { /* noop */ }
})();

// ---------------------------------------------------------------- 桥接（JS → Kotlin）
const bridge = (typeof window.RikkaBridge !== 'undefined') ? window.RikkaBridge : null;

function callBridge(name, ...args) {
    if (!bridge || typeof bridge[name] !== 'function') return undefined;
    try {
        return bridge[name](...args);
    } catch (e) {
        safeWarn(`bridge.${name} failed`, e);
        return undefined;
    }
}

function safeWarn(...args) {
    try { console.warn(LOG_PREFIX, ...args); } catch (_e) { /* noop */ }
}

// ---------------------------------------------------------------- 控制台转发（供调试面板/日志）
(function patchConsole() {
    const MAX = 600;
    // [T5.1] Error 用 JSON.stringify 会得到 "{}" —— 真机日志里成排的 [error] {} 就是这么来的，
    // 唯一有价值的报错信息（message/stack）被丢掉。这里与脚本 iframe 的 send()（见本文件
    // "console → 宿主日志（JSR log.js 语义）" 一段）保持一致：Error / 带 stack 的对象优先取 stack。
    const cut = (v) => {
        let s;
        try {
            if (typeof v === 'string') s = v;
            else if (v instanceof Error) s = v.stack || (v.name + ': ' + v.message);
            else if (v && typeof v === 'object' && typeof v.stack === 'string') {
                s = (v.name ? v.name + ': ' : '') + (v.message || '') + '\n' + v.stack;
            } else s = JSON.stringify(v);
        } catch (_e) { s = String(v); }
        if (s === undefined) s = String(v);
        return s.length > MAX ? s.slice(0, MAX) + '…' : s;
    };
    for (const level of ['log', 'info', 'warn', 'error']) {
        const orig = console[level] ? console[level].bind(console) : () => {};
        console[level] = (...args) => {
            orig(...args);
            try { callBridge('log', level, args.map(cut).join(' ')); } catch (_e) { /* noop */ }
        };
    }
})();

// ---------------------------------------------------------------- 状态（工作副本）
const state = {
    chat: [],              // [{ name, is_user, is_system, mes, swipe_id, variables: [ {} ] }]
    chatMetadata: { variables: {} },
    extensionSettings: { variables: { global: {} } }, // 设置根（variables.global 落点）
    thirdPartySettings: {},                            // ST extension_settings 语义（按扩展名分键）
    characters: [],
    characterId: 0,
    chatId: '',
    // [v208] ST `world_names`（世界书名列表）。此前恒为空 → ST-PT worldinfo.ts:660
    //   `results.filter(e => e && world_names.includes(e))` 把所有书过滤光（WI 功能静默全灭），
    //   JSR setLorebookSettings 对非空书单 throw Error。数据源 = 宿主 getWorldNamesJson。
    worldNames: [],
    // [v208] ST `world_info` 对象（ST 65：{ globalSelect: string[], charLore: [...] }）。
    worldInfo: { globalSelect: [], charLore: [] },
    // [v208] ST `selected_world_info`：全局启用的世界书（charLore 附加书不在此列）。
    selectedWorldInfo: [],
    name1: 'User',
    name2: 'AI',
    modelName: '',
};

const hasOwn = (o, k) => Object.prototype.hasOwnProperty.call(o, k);

// 聊天内容指纹（diff 出被 JS 侧修改过的楼层；JSR setChatMessages / MVU saveChat 写路径）
let chatFingerprintParts = [];

function pullChat() {
    let raw = null;
    try { raw = callBridge('getChatJson'); } catch (_e) { /* noop */ }
    let chat = [];
    try { chat = raw ? JSON.parse(raw) : []; } catch (_e) { chat = []; }
    if (!Array.isArray(chat)) chat = [];

    let mv = null;
    try { mv = JSON.parse(callBridge('getMessageVariablesJson') || '[]'); } catch (_e) { mv = null; }

    chat.forEach((m, i) => {
        if (!m || typeof m !== 'object') return;
        let vs = Array.isArray(mv) ? mv[i] : null;
        if (!Array.isArray(vs)) vs = Array.isArray(m.variables) ? m.variables : [];
        if (vs.length === 0) vs = [{}];
        m.variables = vs;
        if (typeof m.swipe_id !== 'number') m.swipe_id = 0;
        // ⚠️ swipes 必须是【字符串】数组：MVU 切换聊天后重新初始化会对 swipe 内容调用 matchAll。
        // 此前用 {} 填充导致 "n.matchAll is not a function"（红色报错真凶），严禁回退为对象占位。
        const mesText = String(m.mes == null ? '' : m.mes);
        if (!Array.isArray(m.swipes) || m.swipes.length === 0) {
            m.swipes = new Array(Math.max(1, vs.length)).fill(mesText);
        } else {
            m.swipes = m.swipes.map((s) => (typeof s === 'string' ? s : mesText));
        }
    });
    state.chat = chat;
    rebuildChatFingerprint();
}

function pullMetadata() {
    let obj = null;
    try { obj = JSON.parse(callBridge('getChatMetadataJson') || '{}'); } catch (_e) { obj = null; }
    if (!obj || typeof obj !== 'object') obj = {};
    if (!obj.variables || typeof obj.variables !== 'object' || Array.isArray(obj.variables)) obj.variables = {};
    state.chatMetadata = obj;
}

function pullSettings() {
    let obj = null;
    try { obj = JSON.parse(callBridge('getSettingsJson') || '{}'); } catch (_e) { obj = null; }
    if (!obj || typeof obj !== 'object') obj = {};
    if (!obj.variables || typeof obj.variables !== 'object') obj.variables = {};
    if (!obj.variables.global || typeof obj.variables.global !== 'object') obj.variables.global = {};
    state.extensionSettings = obj;
}

// 第三方扩展设置（extension_settings 语义：按扩展名分键）。补充 ST 内置键的安全默认，
// 避免扩展直接访问 extension_settings.note / .regex / .disabledExtensions 时崩溃。
let thirdPartySettingsDirty = false;

/**
 * [v215] ST 契约形状保证：`extension_settings.variables.global` 必须存在。
 * 真机取证：ST-Prompt-Template `src/function/variables.ts:54` 直接读
 * `extension_settings.variables.global`（不做空值保护）—— 我们的
 * `ctx.extensionSettings` 默认 `{}` 时就是 `TypeError: Cannot read properties of undefined (reading 'global')`
 * （真机日志 18:30/19:27 成片出现，栈顶 = ST-PT dist/index.js）。
 * 同时这也是 JSR `type:'global'` 变量的存储位置（variable-shims.js:49-51）。
 */
function ensureStStateShapes() {
    try {
        if (!state.thirdPartySettings || typeof state.thirdPartySettings !== 'object' || Array.isArray(state.thirdPartySettings)) {
            state.thirdPartySettings = {};
        }
        const es = state.thirdPartySettings;
        if (!es.variables || typeof es.variables !== 'object' || Array.isArray(es.variables)) es.variables = { global: {} };
        if (!es.variables.global || typeof es.variables.global !== 'object' || Array.isArray(es.variables.global)) es.variables.global = {};
        if (!state.chatMetadata || typeof state.chatMetadata !== 'object' || Array.isArray(state.chatMetadata)) state.chatMetadata = { variables: {} };
        if (!state.chatMetadata.variables || typeof state.chatMetadata.variables !== 'object' || Array.isArray(state.chatMetadata.variables)) {
            state.chatMetadata.variables = {};
        }
    } catch (_e) { /* noop */ }
}

function pullThirdPartySettings(force) {
    if (!force && thirdPartySettingsDirty) return;
    let obj = null;
    try { obj = JSON.parse(callBridge('getThirdPartySettingsJson') || '{}'); } catch (_e) { obj = null; }
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) obj = {};
    if (!Array.isArray(obj.disabledExtensions)) obj.disabledExtensions = [];
    if (!Array.isArray(obj.regex)) obj.regex = [];
    if (!Array.isArray(obj.regex_presets)) obj.regex_presets = [];
    if (!Array.isArray(obj.character_allowed_regex)) obj.character_allowed_regex = [];
    if (!obj.preset_allowed_regex || typeof obj.preset_allowed_regex !== 'object' || Array.isArray(obj.preset_allowed_regex)) obj.preset_allowed_regex = {}
    // [v215] 世界书之外的 ST 契约形状：全局变量存放在 extension_settings.variables.global
    if (!obj.variables || typeof obj.variables !== 'object' || Array.isArray(obj.variables)) obj.variables = { global: {} }
    if (!obj.variables.global || typeof obj.variables.global !== 'object' || Array.isArray(obj.variables.global)) obj.variables.global = {};
    if (!obj.note || typeof obj.note !== 'object' || Array.isArray(obj.note)) {
        obj.note = {
            default: '',
            defaultPosition: 1,
            defaultDepth: 4,
            defaultInterval: 1,
            defaultRole: 0,
            chara: [],
            allowWIScan: false,
        };
    }
    state.thirdPartySettings = obj;
}

// 角色信息（ST `characters[i]` / v1CharData 语义）
//   ⚠️ v204 事故：这里曾经只塞 `{ name, data: { extensions: { world } } }`，
//   于是 ST-Prompt-Template 的 `getCharacterDefine()` 在 `char.mes_example.trim()` 处炸掉，
//   真机日志却写成 `Error processing world info: TypeError: ... reading 'trim'`（世界书背了黑锅）。
//   形态补齐统一交给 character-shape.js（补全 ST 的全部字段），宿主 Kotlin 侧同步输出完整形态：
//   少一个键，扩展就会在某个 .trim()/.replace()/for..of 处炸掉。
function pullCharacter() {
    let obj = null;
    try { obj = JSON.parse(callBridge('getCharacterJson') || '{}'); } catch (_e) { obj = null; }
    if (!obj || typeof obj !== 'object') obj = {};
    const char = normalizeStCharacter(obj);

    if (char.name) state.name2 = char.name;
    state.characters = char.name ? [char] : [];
    state.characterId = 0;

    // [v208] 世界书名列表 + 全局启用书（P0-3：此前 world_names/selected_world_info 恒为空）
    try {
        const raw = callBridge('getWorldNamesJson');
        const parsed = raw ? JSON.parse(raw) : null;
        if (parsed && typeof parsed === 'object') {
            state.worldNames = Array.isArray(parsed.world_names) ? parsed.world_names : [];
            state.selectedWorldInfo = Array.isArray(parsed.selected) ? parsed.selected : [];
            state.worldInfo = {
                globalSelect: state.selectedWorldInfo.slice(),
                charLore: Array.isArray(parsed.charLore) ? parsed.charLore : [],
            };
            // 角色绑定的主世界书（data.extensions.world）也算「可用书」，兜底并入列表
            const w = char.data && char.data.extensions && char.data.extensions.world;
            if (typeof w === 'string' && w) {
                if (!state.worldNames.includes(w)) state.worldNames.push(w);
                if (!state.selectedWorldInfo.includes(w)) state.selectedWorldInfo.push(w);
                if (!state.worldInfo.globalSelect.includes(w)) state.worldInfo.globalSelect.push(w);
            }
        }
    } catch (_e) { /* noop */ }
}

function refreshAll() {
    pullChat();
    pullMetadata();
    pullSettings();
    pullThirdPartySettings(false);
    pullCharacter();
    const cid = callBridge('getActiveConversationId');
    if (typeof cid === 'string') state.chatId = cid;
    // [v215] 刷新后保证 ST 契约形状（ST-PT / JSR 全局变量读写依赖）
    ensureStStateShapes();
    // 通知 st-compat shim 重新快照（live binding）
    try { window.dispatchEvent(new Event('rikka-st-refresh')); } catch (_e) { /* noop */ }
}

// ---------------------------------------------------------------- 写回（JS → Kotlin，去抖）
let tChat = null, tMeta = null, tGlobal = null;

function pushMessageVars() {
    try {
        callBridge('replaceMessageVariables', JSON.stringify(state.chat.map(m => (m && Array.isArray(m.variables)) ? m.variables : [{}])));
    } catch (e) { safeWarn('pushMessageVars failed', e); }
}

function pushChatVars() {
    try { callBridge('replaceChatVariables', JSON.stringify(state.chatMetadata.variables || {})); }
    catch (e) { safeWarn('pushChatVars failed', e); }
}

function pushGlobalVars() {
    try { callBridge('replaceGlobalVariables', JSON.stringify((state.extensionSettings.variables || {}).global || {})); }
    catch (e) { safeWarn('pushGlobalVars failed', e); }
}

function pushThirdPartySettingsNow() {
    try {
        callBridge('saveThirdPartySettings', JSON.stringify(state.thirdPartySettings || {}));
        thirdPartySettingsDirty = false;
    } catch (e) { safeWarn('pushThirdPartySettings failed', e); }
}

function saveChat() {
    clearTimeout(tChat);
    tChat = setTimeout(() => { pushMessageVars(); pushChatContent(); }, 800);
}
function rebuildChatFingerprint() {
    chatFingerprintParts = state.chat.map(chatFingerprintPart);
}
function chatFingerprintPart(m) {
    return JSON.stringify([
        m && m.mes != null ? String(m.mes) : '',
        m && Array.isArray(m.swipes) ? m.swipes : [],
        m && typeof m.swipe_id === 'number' ? m.swipe_id : 0,
        !!(m && m.is_system),
        m && m.name != null ? String(m.name) : '',
    ]);
}
/**
 * 把 JS 侧对聊天记录内容的修改（swipes / swipe_id / mes / is_system / name）回写宿主。
 * 仅推送与上次快照相比有变化的楼层；无变化时静默返回。
 */
function pushChatContent() {
    try {
        const parts = state.chat.map(chatFingerprintPart);
        const updates = [];
        for (let i = 0; i < parts.length; i++) {
            if (parts[i] !== chatFingerprintParts[i]) {
                const m = state.chat[i] || {};
                updates.push({
                    index: i,
                    mes: m.mes != null ? String(m.mes) : '',
                    swipes: (Array.isArray(m.swipes) ? m.swipes : []).map((s) => (typeof s === 'string' ? s : '')),
                    swipe_id: typeof m.swipe_id === 'number' ? m.swipe_id : 0,
                    is_system: !!m.is_system,
                    name: m.name != null ? String(m.name) : '',
                });
            }
        }
        chatFingerprintParts = parts;
        if (updates.length === 0) return;
        callBridge('updateChatMessages', JSON.stringify(updates));
    } catch (e) {
        safeWarn('pushChatContent failed', e);
    }
}
function saveMetadataDebounced() { clearTimeout(tMeta); tMeta = setTimeout(pushChatVars, 800); }
function saveSettingsDebounced() {
    thirdPartySettingsDirty = true;
    clearTimeout(tGlobal);
    tGlobal = setTimeout(() => { pushGlobalVars(); pushThirdPartySettingsNow(); }, 800);
}
function flushAll() {
    clearTimeout(tChat); clearTimeout(tMeta); clearTimeout(tGlobal);
    pushMessageVars(); pushChatContent(); pushChatVars(); pushGlobalVars(); pushThirdPartySettingsNow();
}

// ---------------------------------------------------------------- ST 常量（对齐 popup.js）
const POPUP_TYPE = { TEXT: 1, CONFIRM: 2, INPUT: 3, DISPLAY: 4, CROP: 5 };
const POPUP_RESULT = {
    AFFIRMATIVE: 1, NEGATIVE: 0, CANCELLED: null,
    CUSTOM1: 1001, CUSTOM2: 1002, CUSTOM3: 1003, CUSTOM4: 1004, CUSTOM5: 1005,
    CUSTOM6: 1006, CUSTOM7: 1007, CUSTOM8: 1008, CUSTOM9: 1009,
};

const toolManagerStub = {
    isToolCallingSupported: () => false,
    canPerformToolCalls: () => false,
    registerFunctionTool: () => {},
    unregisterFunctionTool: () => {},
};

const chatCompletionSettingsStub = {
    chat_completion_source: 'custom',
    openai_model: 'unknown',
    reverse_proxy: '',
    temperature: 1,
    max_tokens: 4096,
};

async function callGenericPopupStub(content, type, defaultResult /*, options */) {
    try {
        callBridge('log', 'info', `[popup] type=${type} content=${String(content).slice(0, 200)}`);
    } catch (_e) { /* noop */ }
    // 无界面宿主：直接返回调用方给定的默认结果（不阻塞自动化流程）
    return defaultResult;
}

// ---------------------------------------------------------------- [v214] ST 兼容成员面的实现体
// st-memory-enhancement 走 `ctx.macros.registry.registerMacro`（新版）与 `ctx.registerMacro`（旧版）双路径；
// 这里给一个真实注册表（可查询、可展开），避免 TypeError。
const stMacroRegistry = new Map();
const stMacrosFacade = {
    macros: stMacroRegistry,
    registry: {
        registerMacro: (name, value) => { try { stMacroRegistry.set(String(name), value); } catch (_e) { /* noop */ } },
        unregisterMacro: (name) => { try { stMacroRegistry.delete(String(name)); } catch (_e) { /* noop */ } },
        getMacro: (name) => stMacroRegistry.get(String(name)) ?? null,
        hasMacro: (name) => stMacroRegistry.has(String(name)),
        clear: () => { try { stMacroRegistry.clear(); } catch (_e) { /* noop */ } },
    },
    registerMacro: (name, value) => { try { stMacroRegistry.set(String(name), value); } catch (_e) { /* noop */ } },
    unregisterMacro: (name) => { try { stMacroRegistry.delete(String(name)); } catch (_e) { /* noop */ } },
    getMacro: (name) => stMacroRegistry.get(String(name)) ?? null,
};

/** ST ctx.substituteParams：先展开已注册宏，未命中保持原样（宿主宏由生成管线处理）。 */
function substituteParamsWithMacros(text) {
    let out = String(text == null ? '' : text);
    if (stMacroRegistry.size === 0) return out;
    try {
        out = out.replace(/\{\{\s*([\w.-]+)\s*\}\}/g, (m, name) => {
            if (!stMacroRegistry.has(name)) return m;
            try {
                const v = stMacroRegistry.get(name);
                return typeof v === 'function' ? String(v()) : String(v);
            } catch (_e) { return m; }
        });
    } catch (_e) { /* noop */ }
    return out;
}

/** ST ctx.variables.local / global（st-context.js:288-303），底层复用 variable-shims 存储布局。 */
function makeStVariableScope(type) {
    const readAll = () => {
        try { return shimVariable.getVariables({ type }) || {}; } catch (_e) { return {}; }
    };
    const write = (name, value) => {
        try { shimVariable.replaceVariables({ [String(name)]: value }, { type }); } catch (_e) { /* noop */ }
    };
    return {
        get: (name) => { const v = readAll(); return name == null ? v : v[name]; },
        set: (name, value) => write(name, value),
        del: (name) => { try { shimVariable.deleteVariable(String(name), { type }); } catch (_e) { /* noop */ } },
        add: (name, value) => { const v = Number(readAll()[name] || 0) + Number(value || 0); write(name, v); return v; },
        inc: (name) => { const v = Number(readAll()[name] || 0) + 1; write(name, v); return v; },
        dec: (name) => { const v = Number(readAll()[name] || 0) - 1; write(name, v); return v; },
        has: (name) => Object.prototype.hasOwnProperty.call(readAll(), String(name)),
    };
}
const stVariablesFacade = { local: makeStVariableScope('chat'), global: makeStVariableScope('global') };

/**
 * [v231] ST ctx.swipe（st-context.js:277-287）真桥（此前为安全桩，长尾遗留项）。
 *
 * 复用宿主已有的 `/swipe` 斜杠命令实现（StSlashExecutor.kt:239 -> ChatSlashHost.kt:326，
 * 含「左=切换上一变体 / 右=切换下一变体 / 右越界=重新生成」全套 ST 语义），零宿主改动：
 *   - left/right() 经 RikkaBridge.triggerSlash("/swipe direction=..|await=..") 走真桥；
 *   - to() 在 JS 侧直接改最后楼层 swipe_id -> pushChatContent() 走既有写回链持久化，
 *     并广播 message_swiped（对齐 ST ctx.swipe.to 的本地切换语义）；
 *   - show/hide/refresh 是纯 DOM 面板操作（宿主消息 UI 自绘，无 DOM 面板可操作），保持 no-op；
 *   - isAllowed 对齐 ST：最后一条 AI 消息才允许 swipe；
 *   - state() 返回当前楼层真实 swipe_id（从 state.chat 取，不再恒 0）。
 */
const stSwipeFacade = {
    left: async (options) => {
        try {
            const awaitFlag = options && typeof options === 'object' && options.await ? 'true' : 'false';
            const r = callBridge('triggerSlash', '/swipe direction=left await=' + awaitFlag);
            return !/error|not ready|无法|失败/i.test(String(r == null ? '' : r));
        } catch (_e) { return false; }
    },
    right: async (options) => {
        try {
            const awaitFlag = options && typeof options === 'object' && options.await ? 'true' : 'false';
            const r = callBridge('triggerSlash', '/swipe direction=right await=' + awaitFlag);
            return !/error|not ready|无法|失败/i.test(String(r == null ? '' : r));
        } catch (_e) { return false; }
    },
    to: async (swipeId) => {
        try {
            const id = Number(swipeId);
            if (!Number.isFinite(id)) return false;
            const n = state.chat ? state.chat.length : 0;
            const m = n > 0 ? state.chat[n - 1] : null;
            if (!m || !Array.isArray(m.swipes) || id < 0 || id >= m.swipes.length) return false;
            m.swipe_id = id;
            pushChatContent();
            try { eventSource.emit('message_swiped', n - 1); } catch (_e) { /* noop */ }
            return true;
        } catch (_e) { return false; }
    },
    show: () => {},
    hide: () => {},
    refresh: () => {},
    isAllowed: () => {
        try {
            const n = state.chat ? state.chat.length : 0;
            const m = n > 0 ? state.chat[n - 1] : null;
            return !!(m && !m.is_user && !m.is_system);
        } catch (_e) { return false; }
    },
    state: () => {
        try {
            const n = state.chat ? state.chat.length : 0;
            const m = n > 0 ? state.chat[n - 1] : null;
            return { swipeId: m && typeof m.swipe_id === 'number' ? m.swipe_id : 0 };
        } catch (_e) { return { swipeId: 0 }; }
    },
};

/** ST ctx.messageFormatter（st-context.js:275）：最小实现（Markdown 渲染由宿主/面板负责）。 */
const stMessageFormatterFacade = {
    format: (text) => String(text == null ? '' : text),
    sanitize: (text) => String(text == null ? '' : text),
    generate: (text) => String(text == null ? '' : text),
};

const ST_SYMBOL_IGNORE = Symbol('ST.ignore');
const ST_SYMBOL_UNSET = Symbol('ST.unset');

/** [v214] 千纱卡编辑消息后的写回链：改 state.chat → 桥持久化 → 广播事件。 */
function stUpdateMessageBlock(messageId, message, options) {
    try {
        const idx = Number(messageId);
        let resolved = message;
        if (Number.isFinite(idx) && state.chat && state.chat[idx]) {
            const cur = state.chat[idx];
            if (message && typeof message === 'object') {
                for (const k of Object.keys(message)) {
                    if (k === 'variables') continue;
                    cur[k] = message[k];
                }
            }
            resolved = cur;
            try { callBridge('updateChatMessages', JSON.stringify(state.chat)); } catch (_e) { /* noop */ }
            try { eventSource.emit('message_updated', { message_id: idx, message: cur }); } catch (_e) { /* noop */ }
        }
        // [v236 R1] 同步 `#chat` 楼层 DOM（卡内脚本 / ST-PT 读写楼层节点）
        chatDom.updateMessageBlock(Number.isFinite(idx) ? idx : messageId, resolved, options);
    } catch (e) { safeWarn('updateMessageBlock failed', e); }
}

// [v235 R2] 装载离线 KaTeX（index.html / panel.html 的 <script> 已先加载 katex.min.js）
try { mathRender.setKatex(window.katex); } catch (_e) { /* noop */ }

/**
 * [v231] ST ctx.messageFormatting（script.js:1800 真源契约）：
 *   messageFormatting(mes, ch_name, isSystem, isUser, messageId, sanitizerOverrides={}, isReasoning=false)
 * 语义 = Markdown -> HTML 全流程（此前为「原文返回」的桩，ST-PT 消息内渲染降级裸文本）。
 * 实现：复用 third-party-globals.js 的 showdown 兼容层（真库或轻量桩，同名 makeHtml API）
 * 做 Markdown -> HTML；宏替换已由 substitudeMacros 链路在上游完成，此处不重复；
 * isReasoning=true 时包 reasoning 折叠块（对齐 ST 的 reasoning 视觉语义）。
 */
function stMessageFormatting(text, ch_name, isSystem, isUser, messageId, sanitizerOverrides, isReasoning) {
    const raw = String(text == null ? "" : text);
    let html = raw;
    try {
        // [v235 R2] 数学离线渲染：先占位（保护围栏/行内代码里的分隔符），Markdown 之后再回填 KaTeX HTML
        const pre = mathRender.stashMath(raw);
        let body = pre.text;
        if (typeof window.showdown !== "undefined" && window.showdown && typeof window.showdown.Converter === "function") {
            if (!stMessageFormatting._converter) {
                try { stMessageFormatting._converter = new window.showdown.Converter({ simpleLineBreaks: false, ghCodeBlocks: true }); }
                catch (_e) { stMessageFormatting._converter = new window.showdown.Converter(); }
            }
            body = stMessageFormatting._converter.makeHtml(body);
        }
        html = pre.restore(body);
    } catch (_e) { html = raw; }
    if (isReasoning) {
        html = '<div class="reasoning_block"><details open><summary>思考过程</summary>' + html + '</details></div>';
    }
    return html;
}

// ---------------------------------------------------------------- ST 上下文（ctx）
// [v228 S1] ST 兼容版本口径（**唯一真源**）：`/version` 端点、`SillyTavern.getContext().getVersion()`、
// `TavernHelper.getTavernVersion()` 三处必须是同一个值 —— v227 之前它们分别是 1.0.0（404 兜底）/1.13.4/1.13.4。
// 取值依据见 notes/recon-version-gate-20261008.md §4：1.13.5 = 打开 JSR 的 G1/G2/G3 三条闸门，
// 同时**不开** 1.15.0（宿主 createGenerationParameters 还是桩、MacroRegistry 模块缺失）。
// Kotlin 侧同一个值定义在 TavernRuntimeManager.TAVERN_ST_COMPAT_VERSION（回归测试会比对两处一致）。
const TAVERN_ST_COMPAT_VERSION = '1.13.5';

const stCtx = {
    get chat() { return state.chat; },
    get chatMetadata() { ensureStStateShapes(); return state.chatMetadata; },
    get extensionSettings() { ensureStStateShapes(); return state.thirdPartySettings; },
    get characters() { return state.characters; },
    get characterId() { return state.characterId; },
    // [v208] ST world_names / world_info / selected_world_info（st-compat/world-info.js 的 refresh 从这里取）
    get world_names() { return state.worldNames; },
    get world_info() { return state.worldInfo; },
    get selected_world_info() { return state.selectedWorldInfo; },
    get name1() { return state.name1; },
    get name2() { return state.name2; },
    get chatId() { return state.chatId; },
    get eventSource() { return eventSource; },
    // [v228 S1] ST /version 口径：JSR 读 fetch('/version').pkgVersion；shims/util-shims.js 的
    // getTavernVersion() 读这里。两处必须一致（v227 是 1.0.0 vs 1.13.4 的漂移）。
    getVersion: () => TAVERN_ST_COMPAT_VERSION,
    get eventTypes() { return event_types; },
    get event_types() { return event_types; },
    get chatCompletionSettings() { return chatCompletionSettingsStub; },
    POPUP_TYPE, POPUP_RESULT,
    ToolManager: toolManagerStub,
    saveChat,
    saveMetadataDebounced,
    saveMetadata: saveMetadataDebounced,
    saveSettingsDebounced,
    reloadCurrentChat: () => { refreshAll(); return Promise.resolve(); },
    loadWorldInfo: async (name) => {
        try {
            const raw = callBridge('getLorebookJson', String(name == null ? '' : name));
            const parsed = raw ? JSON.parse(raw) : {};
            return (parsed && typeof parsed === 'object') ? parsed : {};
        } catch (_e) {
            return {};
        }
    },
    substituteParams: (text) => substituteParamsWithMacros(text),
    substituteParamsExtended: (text) => substituteParamsWithMacros(text),
    getRequestHeaders: () => ({}),
    getCurrentChatId: () => state.chatId,
    getCurrentLocale: () => 'zh-cn',
    getChatCompletionModel: () => state.modelName || 'unknown',
    getCharacterCardFields: () => ({ name: state.name2 || '', description: '', personality: '', scenario: '', first_mes: '', mes_example: '' }),
    callGenericPopup: callGenericPopupStub,
    registerFunctionTool: () => {},
    unregisterFunctionTool: () => {},
    registerMacro: stMacrosFacade.registerMacro,
    unregisterMacro: stMacrosFacade.unregisterMacro,
    extensionPrompts: {},
    generate: async () => '',
    generateRaw: async () => '',
    generateQuietPrompt: async () => '',
    generateRawData: async () => ({}),
    sendStreamingRequest: async () => {},
    sendGenerationRequest: async () => {},
    stopGeneration: () => {},
    clearChat: () => {},
    printMessages: () => {},
    // ---- [v214] ST getContext() 成员面补齐（st-context.js:129-311；按真实消费方优先级）----
    get groups() { return []; },
    get groupId() { return null; },
    get onlineStatus() { return ''; },
    get maxContext() { return 32768; },
    get mainApi() { return 'openai'; },
    get textCompletionSettings() { return {}; },
    get tags() { return []; },
    get tagMap() { return {}; },
    get menuType() { return 0; },
    get accountStorage() { return {}; },
    get streamingProcessor() { return {}; },
    get loader() { return {}; },
    get createCharacterData() { return {}; },
    get symbols() { return { ignore: ST_SYMBOL_IGNORE, unset: ST_SYMBOL_UNSET }; },
    get constants() { return { unset: ST_SYMBOL_UNSET }; },
    get macros() { return stMacrosFacade; },
    get messageFormatter() { return stMessageFormatterFacade; },
    get swipe() { return stSwipeFacade; },
    get variables() { return stVariablesFacade; },
    get CONNECT_API_MAP() { return {}; },
    // ST st-context.js:229 powerUserSettings（ST-PT persona 域：persona_description / persona_description_position）
    get powerUserSettings() { return stCompatPowerUser; },
    // ST st-context.js:214-217 i18n 面
    t: (text) => String(text == null ? '' : text),
    translate: (text) => String(text == null ? '' : text),
    addLocaleData: () => {},
    setExtensionPrompt: (key, value, position, depth) => {
        try { stCtx.extensionPrompts[String(key)] = { value: value, position: position, depth: depth }; } catch (_e) { /* noop */ }
    },
    updateChatMetadata: (patch) => {
        try {
            if (patch && typeof patch === 'object') Object.assign(state.chatMetadata, patch);
            saveMetadataDebounced();
        } catch (_e) { /* noop */ }
    },
    // ST 真源 extensions.js:2070：`writeExtensionField(characterId, key, value)`。
    // 宿主端卡数据在 Kotlin 侧，这里做“可读回、可持久化”的最小落点（thirdPartySettings 命名空间）。
    writeExtensionField: (characterId, key, value) => {
        try {
            const bucket = state.thirdPartySettings['__extension_fields__'] || (state.thirdPartySettings['__extension_fields__'] = {});
            const cid = String(characterId == null ? '' : characterId);
            if (!bucket[cid] || typeof bucket[cid] !== 'object') bucket[cid] = {};
            bucket[cid][String(key == null ? '' : key)] = value;
            pushThirdPartySettingsNow();
        } catch (_e) { /* noop */ }
    },
    // ST 真源 extensions.js:2152：`writeExtensionFieldBulk(avatars, key, value, {filterPath})`
    writeExtensionFieldBulk: (avatars, key, value) => {
        try {
            const list = Array.isArray(avatars) ? avatars : (avatars == null ? [] : [avatars]);
            list.forEach((cid) => stCtx.writeExtensionField(cid, key, value));
        } catch (_e) { /* noop */ }
    },
    getCharacters: () => state.characters,
    getOneCharacter: (id) => {
        try { return (Array.isArray(state.characters) && state.characters[Number(id)]) || null; } catch (_e) { return null; }
    },
    getCharacterSource: () => 'character',
    getThumbnailUrl: () => '',
    selectCharacterById: async () => {},
    openCharacterChat: async () => {},
    openGroupChat: async () => {},
    renameChat: async () => {},
    unshallowCharacter: async () => {},
    unshallowGroupMembers: async () => {},
    addOneMessage: () => {},
    deleteLastMessage: async () => {},
    deleteMessage: async () => {},
    saveReply: async () => {},
    sendSystemMessage: () => {},
    activateSendButtons: () => {},
    deactivateSendButtons: () => {},
    showLoader: () => {},
    hideLoader: () => {},
    callPopup: callGenericPopupStub,
    registerHelper: () => {},
    registerDebugFunction: () => {},
    isToolCallingSupported: () => false,
    canPerformToolCalls: () => false,
    shouldSendOnEnter: () => false,
    isMobile: () => true,
    timestampToMoment: () => ({ format: () => '' }),
    uuidv4: () => {
        try {
            if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
        } catch (_e) { /* noop */ }
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            const v = c === 'x' ? r : ((r & 0x3) | 0x8);
            return v.toString(16);
        });
    },
    humanizedDateTime: () => new Date().toLocaleString('zh-CN', { hour12: false }),
    importFromExternalUrl: async () => null,
    importTags: async () => {},
    updateMessageBlock: stUpdateMessageBlock,
    messageFormatting: stMessageFormatting,
    // [v236 R1] ST script.js:2479 / :2216 真执行版（真插 DOM 节点，不再 no-op）
    addCopyToCodeBlocks: chatDom.addCopyToCodeBlocks,
    appendMediaToMessage: chatDom.appendMediaToMessage,
    renderMessageFloors: chatDom.renderFloors,
    ensureMessageMediaIsArray: (m) => m,
    getMediaDisplay: () => 'none',
    getMediaIndex: () => -1,
    scrollChatToBottom: () => {},
    scrollOnMediaLoad: () => {},
    getTextGenServer: () => '',
    extractMessageFromData: (data) => (data && typeof data === 'object' ? String(data.mes || data.content || '') : ''),
    getExtensionManifest: () => null,
    openThirdPartyExtensionMenu: () => {},
    renderExtensionTemplate: (extensionName, templateId, templateData) => renderExtensionTemplateAsync(extensionName, templateId, templateData),
    renderExtensionTemplateAsync: (extensionName, templateId, templateData) => renderExtensionTemplateAsync(extensionName, templateId, templateData),
    updateReasoningUI: () => {},
    parseReasoningFromString: (text) => String(text == null ? '' : text),
    getReasoningTemplateByName: () => null,
    getTokenizerModel: () => '',
    getTextTokens: () => 0,
    getTokenCount: () => 0,
    getTokenCountAsync: async () => 0,
};

// ---------------------------------------------------------------- SillyTavern 全局（顶层静态成员，MVU bundle 大量使用）
const SillyTavern = {
    name: 'SillyTavern',
    version: '1.18.0',
    libs: {},
    getContext: () => stCtx,
    callGenericPopup: callGenericPopupStub,
    saveChat,
    saveSettingsDebounced,
    saveMetadataDebounced,
    getCurrentChatId: () => state.chatId,
    getRequestHeaders: () => ({}),
    getCurrentLocale: () => 'zh-cn',
    getChatCompletionModel: () => state.modelName || 'unknown',
    getCharacterCardFields: () => stCtx.getCharacterCardFields(),
    registerFunctionTool: () => {},
    unregisterFunctionTool: () => {},
    registerMacro: () => {},
    unregisterMacro: () => {},
    loadWorldInfo: async (name) => {
        try {
            const raw = callBridge('getLorebookJson', String(name == null ? '' : name));
            const parsed = raw ? JSON.parse(raw) : {};
            return (parsed && typeof parsed === 'object') ? parsed : {};
        } catch (_e) {
            return {};
        }
    },
};
Object.defineProperties(SillyTavern, {
    chat: { get: () => state.chat, configurable: true },
    chatMetadata: { get: () => { ensureStStateShapes(); return state.chatMetadata; }, configurable: true },
    characters: { get: () => state.characters, configurable: true },
    characterId: { get: () => state.characterId, configurable: true },
    extensionSettings: { get: () => { ensureStStateShapes(); return state.thirdPartySettings; }, configurable: true },
    chatCompletionSettings: { get: () => chatCompletionSettingsStub, configurable: true },
    name1: { get: () => state.name1, configurable: true },
    name2: { get: () => state.name2, configurable: true },
    POPUP_TYPE: { value: POPUP_TYPE },
    POPUP_RESULT: { value: POPUP_RESULT },
    ToolManager: { value: toolManagerStub },
});
window.SillyTavern = SillyTavern;

// [v214] 卡脚本第二分支 `window.top.context` / `window.context`（千纱卡 a() 函数）：
// WebView 里 window.top === window.self，挂在 window 上即同时满足 top 访问。
try {
    Object.defineProperty(window, 'context', {
        configurable: true,
        get: () => ({
            chat: state.chat,
            chatMetadata: state.chatMetadata,
            characters: state.characters,
            characterId: state.characterId,
            name1: state.name1,
            name2: state.name2,
            chatId: state.chatId,
            eventSource: eventSource,
            event_types: event_types,
            extensionSettings: state.thirdPartySettings,
        }),
    });
} catch (_e) { /* noop */ }

// [v214] 千纱卡 window.top 直挂面（卡 JSON 全卡 21 处）：
// saveChat / updateMessageBlock / messageFormatting 等。已存在的同名全局不覆盖。
(function installWindowStCompatMounts() {
    const mounts = {
        saveChat: saveChat,
        saveMetadataDebounced: saveMetadataDebounced,
        saveSettingsDebounced: saveSettingsDebounced,
        updateMessageBlock: stCtx.updateMessageBlock,
        messageFormatting: stCtx.messageFormatting,
        addCopyToCodeBlocks: stCtx.addCopyToCodeBlocks,
        appendMediaToMessage: stCtx.appendMediaToMessage,
        substituteParams: stCtx.substituteParams,
        substituteParamsExtended: stCtx.substituteParamsExtended,
        getContext: () => stCtx,
    };
    for (const k of Object.keys(mounts)) {
        try { if (!(k in window)) window[k] = mounts[k]; } catch (_e) { /* noop */ }
    }
})();

// [v236 R1] 楼层 DOM 桩接线：渲染器 = stMessageFormatting；chat_changed / chat_id_changed → 重建楼层。
try {
    chatDom.setFormatter(stMessageFormatting);
    chatDom.setChatProvider(() => (Array.isArray(state.chat) ? state.chat : []));
    chatDom.attachChatListeners(eventSource);
} catch (_e) { /* noop */ }

// ---------------------------------------------------------------- TavernHelper 全局（JSR API 兼容层）
const TavernHelper = {
    // 常量
    tavern_events: shimConstants.tavern_events,
    iframe_events: shimConstants.iframe_events,
    // 事件系统
    eventOn: shimEvent.eventOn,
    eventOnce: shimEvent.eventOnce,
    eventEmit: shimEvent.eventEmit,
    eventEmitAndWait: shimEvent.eventEmitAndWait,
    eventRemoveListener: shimEvent.eventRemoveListener,
    eventMakeLast: shimEvent.eventMakeLast,
    eventMakeFirst: shimEvent.eventMakeFirst,
    eventClearEvent: shimEvent.eventClearEvent,
    eventClearListener: shimEvent.eventClearListener,
    eventClearAll: shimEvent.eventClearAll,
    // 变量系统
    getVariables: shimVariable.getVariables,
    getAllVariables: shimVariable.getAllVariables,
    replaceVariables: shimVariable.replaceVariables,
    updateVariablesWith: shimVariable.updateVariablesWith,
    deleteVariable: shimVariable.deleteVariable,
    insertOrAssignVariables: shimVariable.insertOrAssignVariables,
    insertVariables: shimVariable.insertVariables,
    // 聊天消息
    getChatMessages: shimChat.getChatMessages,
    setChatMessages: shimChat.setChatMessages,
    setChatMessage: shimChat.setChatMessage,
    // 世界书
    getLorebookEntries: shimLorebook.getLorebookEntries,
    getCurrentCharPrimaryLorebook: shimLorebook.getCurrentCharPrimaryLorebook,
    getLorebookSettings: shimLorebook.getLorebookSettings,
    setLorebookSettings: shimLorebook.setLorebookSettings,
    getCharWorldbookNames: shimLorebook.getCharWorldbookNames,
    getCharLorebooks: shimLorebook.getCharLorebooks,
    updateWorldbookWith: shimLorebook.updateWorldbookWith,
    // [v214] JSR 4.x 世界书 API（千纱卡开关 UI 依赖；裸全局循环会自动同步）
    getWorldbook: shimLorebook.getWorldbook,
    getWorldbookNames: shimLorebook.getWorldbookNames,
    getChatWorldbookName: shimLorebook.getChatWorldbookName,
    getGlobalWorldbookNames: shimLorebook.getGlobalWorldbookNames,
    // 按钮
    getScriptButtons: shimButton.getScriptButtons,
    replaceScriptButtons: shimButton.replaceScriptButtons,
    updateScriptButtonsWith: shimButton.updateScriptButtonsWith,
    appendInexistentScriptButtons: shimButton.appendInexistentScriptButtons,
    getButtonEvent: shimButton.getButtonEvent,
    // 工具
    getTavernHelperVersion: shimUtil.getTavernHelperVersion,
    getTavernVersion: shimUtil.getTavernVersion,
    triggerSlash: shimUtil.triggerSlash,
    substitudeMacros: shimUtil.substitudeMacros,
    getLastMessageId: shimUtil.getLastMessageId,
    getCurrentMessageId: shimUtil.getCurrentMessageId,
    getScriptId: shimUtil.getScriptId,
    getScriptName: shimUtil.getScriptName,
    getScriptInfo: shimUtil.getScriptInfo,
    replaceScriptInfo: shimUtil.replaceScriptInfo,
    getIframeName: shimUtil.getIframeName,
    reloadIframe: shimUtil.reloadIframe,
    errorCatched: shimUtil.errorCatched,
    initializeGlobal: shimUtil.initializeGlobal,
    waitGlobalInitialized: shimUtil.waitGlobalInitialized,
    registerMacroLike: shimUtil.registerMacroLike,
    // [v241] JSR 注入提示词（酒馆助手）：injectPrompts / uninjectPrompts
    // 语义与差异见 shims/inject-shims.js 头部注释（filter 不支持，已在 DIVERGENCE §H.4 登记）
    injectPrompts: shimInject.injectPrompts,
    uninjectPrompts: shimInject.uninjectPrompts,
    _bind: {},
};
window.TavernHelper = TavernHelper;
window.__mvu_shims__ = TavernHelper;

// 便捷全局（对齐 JSR iframe 里这些函数是裸全局的行为）
for (const key of Object.keys(TavernHelper)) {
    if (!(key in window)) {
        try { window[key] = TavernHelper[key]; } catch (_e) { /* noop */ }
    }
}

// ---------------------------------------------------------------- [v221] 裸全局注入（P0 eventOn 复发收口）
// 真机根因（v221 取证，logs\v221\runtime-20261003.log L1 vs L268）：
//   首次挂载时父窗口 window.TavernHelper 还是模块内建那份（55 keys，顶层有 eventOn）→ 子窗口正常；
//   第三方扩展 JSR 加载后会把父窗口 window.TavernHelper 换成它自己的对象（157 keys，**顶层没有 eventOn**，
//   eventOn 只在 _bind._eventOn 里；见 JS-Slash-Runner/src/function/index.ts:221 + src/iframe/predefine.js:14-18）；
//   此后任何 iframe（force 重装 / 会话切换 / 模型回复后的重建）都拿不到裸全局 eventOn →
//   卡片报「引擎初始化遇到严重错误: eventOn is not defined」。
// 修法：注入时**顶层 key 与 _bind key 都装**，并把模块内建那份（永远有顶层 eventOn）作为兜底源。
const CORE_BARE_GLOBALS = [
    'eventOn', 'eventOnce', 'eventEmit', 'eventEmitAndWait', 'eventRemoveListener',
    'eventMakeLast', 'eventMakeFirst', 'eventClearEvent', 'eventClearListener', 'eventClearAll',
    'getScriptId', 'getScriptName', 'getIframeName',
    'getVariables', 'getAllVariables', 'replaceVariables', 'updateVariablesWith',
    'deleteVariable', 'insertOrAssignVariables', 'insertVariables',
    'triggerSlash', 'substitudeMacros', 'getLastMessageId', 'getCurrentMessageId',
];

// 把 JSR predefine 语义的裸全局装进一个子窗口（脚本 iframe / MVU iframe 共用）。
// sources 按优先级排列；**不覆盖**子窗口已有的同名属性（per-iframe 身份对象不能被打回父窗口身份）。
function installBareGlobals(w, sources) {
    if (!w) return;
    // (1) 顶层 key
    for (const s of sources) {
        if (!s || typeof s !== 'object') continue;
        let keys = [];
        try { keys = Object.keys(s); } catch (_e) { keys = []; }
        for (const k of keys) {
            try { if (!(k in w)) w[k] = s[k]; } catch (_e) { /* noop */ }
        }
    }
    // (2) JSR `_bind`：去掉前导下划线 + bind(子窗口)，只在缺失时装（predefine.js:14-18 同义）
    for (const s of sources) {
        let bind = null;
        try { bind = s && s._bind; } catch (_e) { bind = null; }
        if (!bind || typeof bind !== 'object') continue;
        let bkeys = [];
        try { bkeys = Object.keys(bind); } catch (_e) { bkeys = []; }
        for (const bk of bkeys) {
            const bare = String(bk).replace(/^_/, '');
            if (bare === bk) continue;
            try { if (typeof w[bare] !== 'function') w[bare] = bind[bk].bind(w); } catch (_e) { /* noop */ }
        }
    }
}

// 取「最丰富」的父窗口 TavernHelper（第三方扩展可能往上加了 JSR API 键），退回模块内建那份。
function richestTavernHelper() {
    let src = TavernHelper;
    try {
        const live = window.TavernHelper;
        if (live && typeof live === 'object' && Object.keys(live).length >= Object.keys(TavernHelper).length) src = live;
    } catch (_e) { /* noop */ }
    return src;
}

// [v228 S6] 事件 API 的裸全局清单（监听表必须**随 iframe 隔离**）。
// 为什么只隔离事件类：其余裸全局（变量/宏/按钮）都是无状态转发，共享闭包没有串扰问题；
// 而 eventOn/eventOnce/.../eventClearAll 带着「监听表」这个可变状态（shims/event-shims.js）。
const EVENT_API_BARE_GLOBALS = [
    'eventOn', 'eventOnce', 'eventEmit', 'eventEmitAndWait', 'eventRemoveListener',
    'eventMakeLast', 'eventMakeFirst', 'eventClearEvent', 'eventClearListener', 'eventClearAll',
];

/**
 * [v228 S6] 给一个子窗口装**它自己那份**事件 API（v218 起遗留的跨 iframe 串扰收口）。
 *
 * 真机后果（父 realm 共享闭包）：任一脚本 iframe 卸载都会跑 window.eventClearAll()
 * （buildScriptIframeHtml 的 pagehide 钩子）-> 把其它面板 iframe 的监听全清掉；
 * 同 listener 跨窗口复用时 stop() 也会误删别人的 wrapper。
 * 修法 = 每个子窗口一份独立实例（自己的监听表），底层仍共用同一根 eventSource（emit 语义不变）。
 *
 * @param w 子窗口（脚本 iframe / MVU iframe）
 * @param makeApi () => api（调用方传 shimEvent.createEventApi(true)；回归里注入假工厂）
 * @param sharedTH 父窗口那份共享 TavernHelper（同一个对象会污染父 realm -> 必须换成子窗口副本）
 */
function installPerIframeEventApi(w, makeApi, sharedTH) {
    if (!w || typeof makeApi !== 'function') return null;
    var api = null;
    try { api = w.__rikkaEventApi || null; } catch (_e) { api = null; }
    if (!api) {
        try { api = makeApi(); } catch (_e) { api = null; }
        if (!api) return null;
        try { w.__rikkaEventApi = api; } catch (_e) { /* noop */ }
    }
    for (var i = 0; i < EVENT_API_BARE_GLOBALS.length; i++) {
        var name = EVENT_API_BARE_GLOBALS[i];
        try { if (typeof api[name] === 'function') w[name] = api[name]; } catch (_e) { /* noop */ }
    }
    // TavernHelper 顶层：JSR 语义里子窗口的 TavernHelper 是「merge 出来的自己那份」，
    // 但宿主注入在 childKeys===0 时会把父窗口对象**直接赋给**子窗口（同一个引用）-> 先脱钩再装。
    try {
        var th = w.TavernHelper;
        if (!th || typeof th !== 'object') {
            // 子窗口没有 TavernHelper（pushShims 未命中时）-> 建一个本窗口私有空壳，
            // 至少保证 TavernHelper.eventOn 也走 per-iframe 表（与裸全局同源）。
            th = {};
            w.TavernHelper = th;
        }
        if (th && typeof th === 'object') {
            if (th === sharedTH) {
                var copy = {};
                for (var k in th) { try { copy[k] = th[k]; } catch (_e) { /* noop */ } }
                w.TavernHelper = copy;
                th = copy;
            }
            for (var j = 0; j < EVENT_API_BARE_GLOBALS.length; j++) {
                var nm = EVENT_API_BARE_GLOBALS[j];
                try { if (typeof api[nm] === 'function') th[nm] = api[nm]; } catch (_e) { /* noop */ }
            }
        }
    } catch (_e) { /* noop */ }
    try { if (typeof w.eventOn === 'function') w.__rikkaHostInjected = true; } catch (_e) { /* noop */ }
    return api;
}

// 往子窗口注入：最丰富源优先 + 模块内建补缺 + 核心事件 API 最后兜底（保证 eventOn 一定在）。
function pushRuntimeGlobals(w) {
    if (!w) return;
    const src = richestTavernHelper();
    installBareGlobals(w, [src, TavernHelper]);
    for (const name of CORE_BARE_GLOBALS) {
        try { if (typeof w[name] !== 'function' && typeof TavernHelper[name] === 'function') w[name] = TavernHelper[name]; } catch (_e) { /* noop */ }
    }
    try { if (!w.__mvu_shims__) w.__mvu_shims__ = src; } catch (_e) { /* noop */ }
    let childKeys = 0;
    try { childKeys = (w.TavernHelper && typeof w.TavernHelper === 'object') ? Object.keys(w.TavernHelper).length : 0; } catch (_e) { childKeys = 0; }
    if (childKeys === 0) { try { w.TavernHelper = src; } catch (_e) { /* noop */ } }
    // [v228 S6] 最后一步：把事件 API 换成**本子窗口私有**的实例（自己的监听表）。
    // 放在最后 -> 覆盖上面三条路（顶层 key / _bind 绑定 / CORE 兜底）装进来的父 realm 共享函数。
    try { installPerIframeEventApi(w, function () { return shimEvent.createEventApi(true); }, src); } catch (_e) { /* noop */ }
    try { if (typeof w.eventOn === 'function') w.__rikkaHostInjected = true; } catch (_e) { /* noop */ }
}

// [v221] 注入守护（H1）：iframe 重建 / 导航会换掉子窗口 global，一次性 4 次注入不够 →
// 短轮询补注，直到裸 eventOn 就位或超时（2.5s，与 host-verify 同一时刻）。
function guardChildGlobals(w) {
    let ticks = 0;
    const timer = setInterval(() => {
        ticks++;
        let ok = false;
        try { ok = !!(w && typeof w.eventOn === 'function'); } catch (_e) { ok = false; }
        if (ok) { clearInterval(timer); return; }
        pushRuntimeGlobals(w);
        if (ticks >= 25) clearInterval(timer);
    }, 100);
    return timer;
}

// 全局库（供 iframe 拷贝 / MVU 使用）
// 与 JSR 语义逐字对齐：globalThis.z = import * as zod（命名空间本体）。
// 命名空间同时具备 z.object / z.string（卡脚本直接用法）与 z.z.ZodObject（mvu_zod.js 用法），
// 绝不能只赋值 zodModule.z —— 否则 mvu_zod.js 的 r.z.ZodObject 会炸。
window.z = zodModule;
window.YAML = (yamlModule && yamlModule.default) ? yamlModule.default : yamlModule;
window.klona = window.klona || ((obj) => {
    if (typeof structuredClone === 'function') {
        try { return structuredClone(obj); } catch (_e) { /* fallthrough */ }
    }
    return deepClone(obj);
});

// ---------------------------------------------------------------- 事件桥（Kotlin → JS）
// ---------------------------------------------------------------- ST 模板渲染 API（批次十七）
// ST 的第三方扩展普遍这么用：
//   $('#extensions_settings').append(await renderExtensionTemplateAsync('third-party/<folder>', 'settings'));
// 路径规则：/<folder>/<templateId>.html，由宿主资产路由伺服（第三方扩展目录已可直读）。
// 模板里的 {{key}} 用传进来的 data 做最简替换（ST 用 Handlebars；扩展的 settings 模板绝大多数是纯 HTML）。
async function renderTemplateAsync(path, name, data) {
    const rel = String(path == null ? '' : path).replace(/^\/+/, '');
    const tpl = String(name == null ? '' : name).replace(/^\/+/, '');
    // [v235 I5] 归一化重复斜杠：扩展常传 '/third-party/<folder>/' 这类带尾斜杠的 path，
    // 旧实现会拼出 // 从而在资产路由上 404（模板静默变空串）。
    const url = ('/' + rel + '/' + tpl + '.html').replace(/\/{2,}/g, '/');
    let html = '';
    try {
        const res = await fetch(url, { cache: 'no-store' });
        if (!res.ok) throw new Error('http=' + res.status);
        html = await res.text();
    } catch (e) {
        try { console.warn(`${LOG_PREFIX} renderTemplateAsync failed: ${url}`, e); } catch (_e) { /* noop */ }
        return '';
    }
    // [v207] 去掉模板首部的注释与空白 —— 对齐真 ST（templates.js 会过 DOMPurify.sanitize，注释被丢掉）。
    // 真机证据：记忆增强表格的 `manager.html` 整份就是一行 `<!-- manager.html -->`，
    // jQuery $(html) 于是造出 #comment(nodeType=8)，扩展 `$(await getTemplate('manager')).get(0)`
    // 拿到的就是注释节点 → `initializedTableView.querySelector is not a function`
    // （报告 notes/recon-B-dom.md；ST 侧同样的模板不会炸，差的就是这一步）。
    html = html.replace(/^(?:\s|<!--[\s\S]*?-->)+/, '');
    if (data && typeof data === 'object') {
        html = html.replace(/\{\{\{\s*([\w.$]+)\s*\}\}\}/g, (m, k) => (k in data ? String(data[k]) : m));
        html = html.replace(/\{\{\s*([\w.$]+)\s*\}\}/g, (m, k) => (k in data ? String(data[k]) : m));
    }
    return html;
}
async function renderExtensionTemplateAsync(extensionName, templateId, templateData, _sanitize) {
    const raw = String(extensionName == null ? '' : extensionName);
    const folder = raw.startsWith('third-party/') ? raw : `third-party/${raw}`;
    return renderTemplateAsync(folder, templateId, templateData);
}
try { window.renderTemplateAsync = renderTemplateAsync; } catch (_e) { /* noop */ }
try { window.renderExtensionTemplateAsync = renderExtensionTemplateAsync; } catch (_e) { /* noop */ }

// ============================================================
// [v211] 生成管线钩子（ST CHAT_COMPLETION_SETTINGS_READY / GENERATE_AFTER_DATA）
// ============================================================
// ST 契约（openai.js:1616-1623）：prompt 组装完成后
//   const chat = chatCompletion.getChat();            // [{role, content, ...}]
//   const eventData = { chat, dryRun };
//   await eventSource.emit(CHAT_COMPLETION_PROMPT_READY, eventData);   // 扩展就地改 eventData.chat
//   return [chat, ...];                               // ← 改写后的结果继续用
// 而 ST-PT handler.ts:979-980 实际监听的是【GENERATE_AFTER_DATA（prompt 路径）】与
//   【CHAT_COMPLETION_SETTINGS_READY（chat completion 路径，data.messages = await processGenerateAfter(data.messages)）】
// MVU bundle 里 5 处 CHAT_COMPLETION_SETTINGS_READY（含 eventMakeLast —— 要求排在最后拿最终结果）。
// 所以这里只需实现 CHAT_COMPLETION_SETTINGS_READY，ST-PT 与 MVU 就都能参与生成。
//
// Kotlin 调用：TavernRuntimeManager.runGenerationHooks(messagesJson)
// 返回：JSON 字符串（改写后的 messages 数组）或 null（无监听器/超时 → 调用方保留原文，零开销直通）。
window.__rikkaRunGenerationHooks = async function __rikkaRunGenerationHooks(seq, messagesJson) {
    try {
        const messages = JSON.parse(messagesJson);
        if (!Array.isArray(messages)) return null;
        // ST-PT handleChatCompletionReady（handler.ts:201-210）：
        //   data.messages = await processGenerateAfter(data.messages, data.type ?? generateType)
        // 我们无法知道扩展内部是否异步，因此发一个【载荷对象】，扩展把改写结果写回 data.messages；
        // eventSource.emitAndWait 会等全部同步 handler 跑完（我方 eventSource 同步派发）。
        // [v213] chat 与 messages 指向同一数组引用：SETTINGS_READY 消费方（ST-PT/MVU）读写 data.messages，
        // PROMPT_READY 消费方（st-memory onChatCompletionPromptReady，index.js:625）读写 data.chat —— 同引用保证两边改写互相可见。
        // [v228 S1 前置] JSR >= 1.13.5 的 macro_like.ts:15-25 在 GENERATE_AFTER_DATA 分支里读
        //   `for (const message of event_data.prompt)` 并改写 message.content；缺 prompt 会抛 TypeError
        //   被宿主 try/catch 吞掉 -> 宏替换静默退化（notes/recon-version-gate-20261008.md §2 G2）。
        //   prompt 与 messages / chat 指向**同一数组引用**，所以监听者改写 content 会直接作用于本次生成。
        const eventData = { messages, prompt: messages, chat: messages, type: '', chatId: state.chatId, dryRun: false };
        // [v222] ST GENERATION_AFTER_COMMANDS（vendor/st/events.js:22）：
        //   ST-PT 的 handleGenerateBefore / handleFilterInstall 靠它跑提示词模板与过滤器安装，
        //   **必须在 prompt 组装之前完成** —— 所以并入这条可等待桥（Kotlin 侧 suspend + 15s）。
        //   必须用 await eventSource.emit(...)：vendor/st/eventemitter.js 的 emitAndWait 是同步派发、
        //   **不等异步监听器**，而 ST-PT 的 handler 是 async；用 emitAndWait 会与组装竞态。
        //   也不许走 window.__rikkaEmit（fire-and-forget）。严禁在注释里使用反引号（外层模板字符串红线）。
        try {
            // [v225 T7] 真机可见性：本事件在 JS 侧发射（不走 Kotlin fireEvent），不会自动写 [event] 日志；
            // 补一行 RikkaBridge.log，使 developerMode 下真机日志能确认它在 prompt 组装之前出现。
            // payload 与 ST 语义对齐：args = [type, options, dryRun]。
            try { callBridge("log", "info", "[event] GENERATION_AFTER_COMMANDS " + JSON.stringify({ args: ["normal", {}, false] })); } catch (_eLog) { /* noop */ }
            await eventSource.emit(event_types.GENERATION_AFTER_COMMANDS, eventData);
        } catch (eAfter) {
            console.error('[RikkaTavern] GENERATION_AFTER_COMMANDS handler failed:', (eAfter && eAfter.stack) || eAfter);
        }
        // [v228 S4] ST GENERATE_BEFORE_COMBINE_PROMPTS（vendor/st/events.js:56；真源 script.js:5234
        //   "await eventSource.emit(event_types.GENERATE_BEFORE_COMBINE_PROMPTS, data)"）。
        //   语义：提示词层合并**之前**把组装输入交给监听者，监听者可就地改写（ST 的 data.messages 即最终 messages）。
        //   我方等价物 = eventData（messages / chat 同引用，改写立刻生效）；载荷键名与 ST 同名。
        //   必须 await：ST-PT 的 handler 是 async，而 emitAndWait 不等异步监听器（会与组装竞态）。
        try {
            try { callBridge("log", "info", "[event] GENERATE_BEFORE_COMBINE_PROMPTS " + JSON.stringify({ dryRun: false })); } catch (_eLog) { /* noop */ }
            await eventSource.emit(event_types.GENERATE_BEFORE_COMBINE_PROMPTS, eventData);
        } catch (eBeforeCombine) {
            console.error('[RikkaTavern] GENERATE_BEFORE_COMBINE_PROMPTS handler failed:', (eBeforeCombine && eBeforeCombine.stack) || eBeforeCombine);
        }
        // [v227 N1] GENERATE_AFTER_DATA（ST events.js:58；真源 script.js:5318
        // "await emit(GENERATE_AFTER_DATA, generate_data, dryRun)"，dryRun 恒 false）。
        // 订阅方（本机参考库实证）：JSR src/panel/render/macro_like.ts:104、
        // ST-Prompt-Template src/modules/handler.ts:979（handleGenerateAfter）。
        // payload 复用同一个 eventData（messages/chat/type/chatId/dryRun），第二参 dryRun=false。
        try {
            // [v225 T7] 真机可见性：JS 侧发射不走 Kotlin fireEvent，补一行日志才能在 developerMode 下确认。
            try { callBridge("log", "info", "[event] GENERATE_AFTER_DATA " + JSON.stringify({ dryRun: false })); } catch (_eLog) { /* noop */ }
            await eventSource.emit(event_types.GENERATE_AFTER_DATA, eventData, false);
        } catch (eAfterData) {
            console.error('[RikkaTavern] GENERATE_AFTER_DATA handler failed:', (eAfterData && eAfterData.stack) || eAfterData);
        }
        try {
            if (typeof eventSource.emitAndWait === 'function') {
                eventSource.emitAndWait(event_types.CHAT_COMPLETION_SETTINGS_READY, eventData);
            } else {
                eventSource.emit(event_types.CHAT_COMPLETION_SETTINGS_READY, eventData);
            }
        } catch (e) {
            console.error('[RikkaTavern] generation hook handler failed:', (e && e.stack) || e);
        }
        // [v213] PROMPT_READY：st-memory 的「AI 读表」注入通路（ST openai.js:1619 契约时机，载荷 {chat, dryRun}）。
        // SETTINGS_READY 之后发出，与 ST 顺序一致（先 settings 后 prompt —— ST-PT 在 settings 里做 format，st-memory 在 prompt 里注入表格数据）。
        try {
            if (typeof eventSource.emitAndWait === "function") {
                eventSource.emitAndWait(event_types.CHAT_COMPLETION_PROMPT_READY, eventData);
            } else {
                eventSource.emit(event_types.CHAT_COMPLETION_PROMPT_READY, eventData);
            }
        } catch (e2) {
            console.error("[RikkaTavern] prompt-ready hook handler failed:", (e2 && e2.stack) || e2);
        }
        // [v228 S4] ST GENERATE_AFTER_COMBINE_PROMPTS（vendor/st/events.js:57；真源 script.js:5242-5244
        //   "const eventData = { prompt: finalPrompt, dryRun }; await emit(AFTER, eventData); finalPrompt = eventData.prompt;"）。
        //   rikkaST 没有「扁平化 prompt 字符串」，组合结果就是 messages 数组：
        //     prompt   = 组合结果的串行化（监听者可把它改成新的 JSON 数组 -> 生效）；
        //     messages = 同一份活动引用（与 ST 的 data 同构：改它，后续生成就用改后的值）。
        try {
            const afterData = {
                prompt: JSON.stringify(eventData.messages),
                messages: eventData.messages,
                chat: eventData.chat,
                type: eventData.type || '',
                chatId: eventData.chatId,
                dryRun: false,
            };
            try { callBridge("log", "info", "[event] GENERATE_AFTER_COMBINE_PROMPTS " + JSON.stringify({ dryRun: false })); } catch (_eLog) { /* noop */ }
            await eventSource.emit(event_types.GENERATE_AFTER_COMBINE_PROMPTS, afterData);
            // ST 真源 script.js:5244 的语义是 `finalPrompt = eventData.prompt` —— **prompt 优先**。
            // 所以先看 prompt 有没有被监听者改掉；没改再看 messages 是否被换成了新数组。
            if (typeof afterData.prompt === 'string' && Array.isArray(eventData.messages) &&
                afterData.prompt !== JSON.stringify(eventData.messages)) {
                try {
                    const reparsed = JSON.parse(afterData.prompt);
                    if (Array.isArray(reparsed) && reparsed.length > 0) eventData.messages = reparsed;
                } catch (_eParse) { /* 非 JSON 数组的 prompt 无法表达 -> 忽略（不猜） */ }
            } else if (Array.isArray(afterData.messages)) {
                eventData.messages = afterData.messages;
            }
        } catch (eAfterCombine) {
            console.error('[RikkaTavern] GENERATE_AFTER_COMBINE_PROMPTS handler failed:', (eAfterCombine && eAfterCombine.stack) || eAfterCombine);
        }
        // [v213] 收口：messages / chat 任一被改写都生效（同引用下二者等价，这里双兜底）。
        const out = Array.isArray(eventData.messages) ? eventData.messages : (Array.isArray(eventData.chat) ? eventData.chat : messages);
        const json = JSON.stringify(out);
        // [v211] 通过 bridge 把改写结果回传给 Kotlin（对应 runGenerationHooks 的挂起等待）
        try {
            if (window.RikkaBridge && typeof window.RikkaBridge.onGenerationHooksResult === 'function') {
                window.RikkaBridge.onGenerationHooksResult(seq, JSON.stringify(json));
            }
        } catch (_e) { /* noop */ }
        return json;
    } catch (e) {
        console.error('[RikkaTavern] __rikkaRunGenerationHooks failed:', (e && e.stack) || e);
        return null;
    }
};

window.__rikkaEmit = function __rikkaEmit(name, payload) {
    try {
        if (['message_received', 'message_sent', 'message_updated', 'user_message_rendered',
             'character_message_rendered', 'message_swiped', 'active_conversation_changed'].includes(name)) {
            refreshAll();
        }
        if (name === 'active_conversation_changed') {
            // [v214] 会话切换：**强制**重装卡脚本（force=true）。
            // 真机取证：会话数据（assistantId）可能晚于事件到达 → 签名比对误判「没变化」而跳过
            // → 新卡的 MVU/脚本永不装载（面板 vars 恒空）。
            // 随后按需重启 MVU（等旧脚本 iframe 的 pagehide 清理完 parent.Mvu 再判定）。
            try {
                Promise.resolve().then(async () => {
                    try {
                        await loadTavernScripts(true);
                    } catch (e) { safeWarn('loadTavernScripts(force) failed', e); }
                    setTimeout(() => {
                        try { if (!window.Mvu) loadMvu(); } catch (_e) { /* noop */ }
                    }, 700);
                    setTimeout(notifyChatReady, 300);
                });
            } catch (_e) { /* noop */ }
        }
        if (name === 'third_party_extensions_changed') {
            // 第三方扩展安装/启停/删除：重装扩展资源（已装载 URL 不重复执行）
            try {
                Promise.resolve().then(() => loadThirdPartyExtensions(true));
            } catch (_e) { /* noop */ }
        }
        let data = payload;
        if (typeof payload === 'string') {
            try { data = JSON.parse(payload); } catch (_e) { data = payload; }
        }
        if (data && typeof data === 'object' && Array.isArray(data.args)) {
            return eventSource.emit(name, ...data.args);
        }
        // [batch17] 空对象过去会被吞成「无参 emit」，导致监听方拿到 undefined 而崩（MVU / ST-Prompt-Template 的 chat_completion_prompt_ready 监听器就是这样炸的）。载荷一律照传。
        if (data && typeof data === 'object' && Object.keys(data).length === 0) return eventSource.emit(name, data);
        return eventSource.emit(name, data);
    } catch (e) {
        safeWarn('__rikkaEmit failed', name, e);
    }
};

// JS 侧事件上抛（script_ready / js_result / mvu_ready …）
function emitToHost(name, payload) {
    try { callBridge('emitEvent', name, JSON.stringify(payload ?? {})); } catch (_e) { /* noop */ }
}

// JS → ST 事件（让 JS-Slash-Runner 风格脚本的 eventEmit 同时能到达宿主可见的日志）
eventSource.on(event_types.MESSAGE_RECEIVED, (id) => {
    try { callBridge('log', 'info', `[event] message_received id=${id}`); } catch (_e) { /* noop */ }
});

// ---------------------------------------------------------------- toastr 转发（离屏 WebView 中弹窗不可见 → 桥接日志）
(function patchToastr() {
    const t = window.toastr;
    if (!t || t.__rikkaPatched) return;
    for (const level of ['info', 'success', 'warning', 'error']) {
        const orig = t[level];
        if (typeof orig !== 'function') continue;
        t[level] = function (...args) {
            try { emitToHost('toast', { level, args: args.map((a) => String(a).slice(0, 300)) }); } catch (_e) { /* noop */ }
            return orig.apply(t, args);
        };
    }
    t.__rikkaPatched = true;
})();

// 供脚本 iframe 的 console/toastr 桥接（log.js 语义）
window.__rikkaLog = function (level, message) {
    try { callBridge('log', String(level || 'log'), String(message || '')); } catch (_e) { /* noop */ }
};

// [v218] iframe → 宿主的 postMessage 通道。
// 背景：TH-script iframe 里的 console 桥走 `window.parent.__rikkaLog`（跨窗口属性读），
// 真机上这条通道**从来没在日志里出现过**（MVU iframe 的 postMessage 通道却一直正常），
// 于是「predefine 抛错」这类问题完全静默 —— eventOn P0 查不下去就是这个盲区。
window.addEventListener('message', (ev) => {
    try {
        const d = ev && ev.data;
        if (!d || typeof d !== 'object') return;
        if (d.type === 'th-iframe-log') {
            callBridge('log', String(d.level || 'debug'), String(d.text || ''));
            return;
        }
        if (d.type === 'th-script-diag') {
            callBridge('log', 'debug', '[TH-script] diag ' + JSON.stringify(d.diag || {}));
        }
    } catch (_e) { /* noop */ }
});

// ---------------------------------------------------------------- 脚本执行入口（/js）
window.__rikkaRunScript = async function __rikkaRunScript(code) {
    refreshAll();
    let result, error = null;
    try {
        try {
            const exprFn = new Function('"use strict"; return (' + code + '\n);');
            result = await exprFn();
        } catch (_e1) {
            const stmtFn = new Function('"use strict"; return (async () => {\n' + code + '\n})();');
            result = await stmtFn();
        }
    } catch (e) {
        error = e;
    }
    let payload;
    if (error) {
        payload = { ok: false, error: String((error && error.stack) || error) };
    } else {
        let v;
        try { v = JSON.stringify(result); if (v === undefined) v = 'undefined'; }
        catch (_e) { v = String(result); }
        payload = { ok: true, value: v };
    }
    emitToHost('js_result', payload);
    return payload;
};

// ---------------------------------------------------------------- EJS 模板渲染（ST-Prompt-Template 语义）
/** 构建 EJS 渲染上下文（ST-Prompt-Template prepareContext 子集映射）。 */
function buildEjsContext() {
    const pickLast = (pred) => {
        try {
            for (let i = state.chat.length - 1; i >= 0; i--) {
                const m = state.chat[i];
                if (m && pred(m)) return m;
            }
        } catch (_e) { /* noop */ }
        return null;
    };
    return ejsRender.createEjsApi({
        userName: state.name1,
        charName: state.name2,
        assistantName: state.name2,
        chatId: state.chatId,
        characterId: state.characterId,
        get lastMessageId() { return Math.max(0, state.chat.length - 1); },
        get lastUserMessage() {
            const m = pickLast(x => x.is_user);
            return m ? String(m.mes || '') : '';
        },
        get lastCharMessage() {
            const m = pickLast(x => !x.is_user && !x.is_system);
            return m ? String(m.mes || '') : '';
        },
    });
}

/**
 * EJS 批量渲染入口（Kotlin → JS）：
 * textsJson 为待渲染文本数组；逐条渲染（仅含 `<%` 的才处理），
 * 完成后经 RikkaBridge.onEjsResult(seq, json) 回传（杜绝轮询）。
 */
window.__rikkaEvalEjsBatch = async function __rikkaEvalEjsBatch(seq, textsJson) {
    let texts = [];
    try { texts = JSON.parse(textsJson); } catch (_e) { texts = []; }
    if (!Array.isArray(texts)) texts = [];
    refreshAll();
    const out = [];
    const data = buildEjsContext();
    for (const t of texts) {
        const src = (t === null || t === undefined) ? '' : String(t);
        if (!ejsRender.containsEjs(src)) { out.push(src); continue; }
        try {
            out.push(await ejsRender.renderEjsText(src, data));
        } catch (e) {
            const brief = String((e && e.message) || e).slice(0, 300);
            safeWarn(`[ejs] render failed: ${brief}`);
            try { callBridge('log', 'error', `[ejs] render failed: ${brief}`); } catch (_e2) { /* noop */ }
            out.push(src); // 失败保留原文，不破坏 prompt
        }
    }
    try { callBridge('onEjsResult', seq, JSON.stringify(out)); } catch (_e) { /* noop */ }
    return out;
};

// ---------------------------------------------------------------- [v234 S2] 函数宏批量执行（Kotlin → JS）
// Kotlin 的 MacrosMacroPass 把文本里的 {{fnmacro}} 哨兵替换请求发回 JS：
// __rikkaEvalMacrosBatch(seq, requestsJson)，requestsJson = [{id, name, nonce}, ...]
// 逐项调用 MacrosParser 注册的函数宏（ST 语义 fn(nonce) → sanitizeMacroValue），
// 完成后经 RikkaBridge.onMacrosResult(seq, json) 回传 [{id, value}, ...]。
window.__rikkaEvalMacrosBatch = async function __rikkaEvalMacrosBatch(seq, requestsJson) {
    const results = [];
    let requests = [];
    try { requests = JSON.parse(requestsJson); } catch (_e) { requests = []; }
    if (!Array.isArray(requests)) requests = [];
    try {
        const parserCtor = (window.SillyTavern && window.SillyTavern.getContext
            && window.SillyTavern.getContext().MacrosParser) || null;
        const mod = (typeof MacrosParserModule !== 'undefined') ? MacrosParserModule : null;
        for (const req of requests) {
            let value = '';
            try {
                let fn = null;
                if (parserCtor && parserCtor.macros instanceof Map) {
                    fn = parserCtor.macros.get(req.name);
                }
                if (typeof fn !== 'function' && mod && mod.MacrosParser && mod.MacrosParser.macros instanceof Map) {
                    fn = mod.MacrosParser.macros.get(req.name);
                }
                if (typeof fn === 'function') {
                    const raw = fn(req.nonce);
                    value = (raw == null) ? '' : (typeof raw === 'object' ? JSON.stringify(raw) : String(raw));
                }
            } catch (e) {
                try { callBridge('log', 'warn', '[macros] function macro ' + req.name + ' threw: ' + String(e && e.message || e)); } catch (_e2) { /* noop */ }
                value = '';
            }
            results.push({ id: req.id, value: value });
        }
    } catch (_e) { /* noop */ }
    try { callBridge('onMacrosResult', seq, JSON.stringify(results)); } catch (_e) { /* noop */ }
    return results;
};

// ---------------------------------------------------------------- 卡脚本装载（TH-script iframe，JSR 兼容）
const scriptIframes = new Map();      // scriptId → HTMLIFrameElement
let lastScriptsSignature = '';
let mvuFromScripts = false;           // 卡脚本自带 MVU 加载器（MVU-offline）时置 true

function scriptEnabled(v) {
    if (v === undefined || v === null) return true;
    if (typeof v === 'boolean') return v;
    if (typeof v === 'number') return v !== 0;
    if (typeof v === 'string') return v.toLowerCase() === 'true' || v === '1';
    return true;
}

function buildScriptIframeHtml(script) {
    const parentOrigin = window.location.origin;
    const scriptName = String(script.name == null ? '' : script.name);
    const scriptId = String(script.id == null ? '' : script.id);
    const iframeName = `TH-script--${scriptName}--${scriptId}`;
    const rawContent = String(script.content == null ? '' : script.content);
    // 防止 HTML 解析被 </script> 截断（模块内 <\/script> 与 </script> 等价）
    const content = rawContent.replace(/<\/script>/gi, '<\\/script>');
    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<base href="${parentOrigin}/st-runtime/">
<script>
// [v218] 诊断 + console/error 闸门必须在 predefine **之前**安装。
// 旧版把 console 桥放在 body（predefine 之后）：predefine 一旦抛错就完全静默，
// 真机「eventOn is not defined」查了三版查不下去就是这个盲区。
(function () {
    window.__rikkaDiag = function (tag, extra) {
        var d = { tag: String(tag) };
        try { d.eventOn = typeof window.eventOn; } catch (e) { d.eventOn = 'ERR'; }
        try { d.thKeys = window.TavernHelper ? Object.keys(window.TavernHelper).length : -1; } catch (e) { d.thKeys = 'ERR'; }
        try { d.toastr = typeof window.toastr; } catch (e) { d.toastr = 'ERR'; }
        try { d.mvu = typeof window.Mvu; } catch (e) { d.mvu = 'ERR'; }
        try { d.dollar = typeof window.$; } catch (e) { d.dollar = 'ERR'; }
        try { d.name = String(window.name || ''); } catch (e) { d.name = 'ERR'; }
        try { d.isTop = (window.parent === window); } catch (e) { d.isTop = 'ERR'; }
        try { d.parentHasLog = !!(window.parent && window.parent.__rikkaLog); } catch (e) { d.parentHasLog = 'ERR:' + (e && e.name); }
        try { d.parentThKeys = (window.parent && window.parent.TavernHelper) ? Object.keys(window.parent.TavernHelper).length : -1; } catch (e) { d.parentThKeys = 'ERR:' + (e && e.name); }
        try { d.parentErr = window.__rikkaParentErr || null; } catch (e) { /* noop */ }
        try { d.hostInjected = !!window.__rikkaHostInjected; } catch (e) { /* noop */ }
        if (extra) { for (var k in extra) { try { d[k] = extra[k]; } catch (e) { /* noop */ } } }
        try { window.parent.postMessage({ type: 'th-script-diag', diag: d }, '*'); } catch (e) { /* noop */ }
        try { if (window.parent && window.parent.__rikkaLog) window.parent.__rikkaLog('debug', '[TH-script] diag ' + JSON.stringify(d)); } catch (e) { /* noop */ }
        return d;
    };
    if (!window.__rikkaConsolePatched) {
        window.__rikkaConsolePatched = true;
        var send = function (level, args) {
            try {
                var text = Array.prototype.map.call(args, function (a) {
                    try {
                        if (typeof a === 'string') return a;
                        if (a instanceof Error) return a.stack || (a.name + ': ' + a.message);
                        if (a && typeof a === 'object' && typeof a.stack === 'string') {
                            return (a.name ? a.name + ': ' : '') + (a.message || '') + '\\n' + a.stack;
                        }
                        var s = JSON.stringify(a);
                        return s === undefined ? String(a) : s;
                    } catch (e) { return String(a); }
                }).join(' ');
                try { window.parent.postMessage({ type: 'th-iframe-log', level: String(level), text: String(text).slice(0, 900) }, '*'); } catch (e) { /* noop */ }
                try { if (window.parent && window.parent.__rikkaLog) window.parent.__rikkaLog(level, text); } catch (e) { /* noop */ }
            } catch (e) { /* noop */ }
        };
        ['log', 'debug', 'info', 'warn', 'error'].forEach(function (level) {
            var orig = console[level] ? console[level].bind(console) : function () {};
            console[level] = function () {
                var args = Array.prototype.slice.call(arguments);
                send(level, args);
                orig.apply(null, args);
            };
        });
    }
    if (!window.__rikkaErrGate) {
        window.__rikkaErrGate = true;
        window.addEventListener('error', function (e) {
            try {
                var m = '[TH-script] error: ' + String((e && (e.message || (e.error && e.error.stack))) || e);
                try { window.parent.postMessage({ type: 'th-iframe-log', level: 'error', text: m }, '*'); } catch (_e) { /* noop */ }
                try { if (window.parent && window.parent.__rikkaLog) window.parent.__rikkaLog('error', m); } catch (_e) { /* noop */ }
            } catch (_e) { /* noop */ }
        }, true);
    }
})();
<\/script>
<script>
(function () {
    var p = null;
    try { p = window.parent; } catch (e) { window.__rikkaParentErr = String((e && e.name) || e); }
    try {
        // 1) 全局库引用（纯对象/函数，跨 realm 安全；jQuery/lodash 由下方标签自加载）
        window.z = p.z;
        window.YAML = p.YAML;
        window.klona = p.klona;
        window.Vue = p.Vue;
        window.VueRouter = p.VueRouter;
        window.toastr = p.toastr; // 复用宿主 toastr（已桥接日志；离屏 WebView 中弹窗不可见）
        // 2) TavernHelper + 裸全局函数（JSR predefine 语义）
        // [v218] 三个来源按优先级取，且**不覆盖已注入的非空集合**：
        //   (a) 父窗口的 TavernHelper（同源时可读）
        //   (b) 宿主在 iframe 装载瞬间直接写进本窗口的 TavernHelper（跨域也能到，见 mountScriptIframe 的 pushShims）
        //   (c) 本窗口已有的非空 TavernHelper
        var parentTH = null;
        try { parentTH = (p && p.TavernHelper) ? p.TavernHelper : null; } catch (e) { parentTH = null; }
        var parentTHKeys = 0;
        try { parentTHKeys = parentTH ? Object.keys(parentTH).length : 0; } catch (e) { parentTHKeys = 0; }
        var existingTH = (window.TavernHelper && typeof window.TavernHelper === 'object') ? window.TavernHelper : null;
        var existingKeys = 0;
        try { existingKeys = existingTH ? Object.keys(existingTH).length : 0; } catch (e) { existingKeys = 0; }
        var TH = parentTHKeys > 0 ? parentTH : (existingKeys > 0 ? existingTH : (parentTH || existingTH || {}));
        window.TavernHelper = {};
        for (var key in TH) { try { window.TavernHelper[key] = TH[key]; } catch (e) { /* noop */ } }
        window.TavernHelper._bind = window.TavernHelper._bind || {};
        for (var key2 in TH) {
            if (key2 === '_bind') continue;
            try { window[key2] = TH[key2]; } catch (e) { /* noop */ }
        }
        // [v221] JSR predefine 语义补全：_bind 的每个 key 去掉前导下划线后挂成裸全局，并 bind(本 iframe window)。
        // 真机根因：JSR 会把父窗口 window.TavernHelper 换成它自己的对象（顶层**没有** eventOn，
        // 只有 _bind._eventOn）→ 旧代码只遍历顶层 key，裸全局 eventOn 就丢了
        // （真机 diag：thKeys=165/parentThKeys=157 但 eventOn=undefined）。
        var __bindSrc = (window.TavernHelper && window.TavernHelper._bind) || (TH && TH._bind) || null;
        if (__bindSrc) {
            for (var bkey in __bindSrc) {
                var bare = String(bkey).replace(/^_/, '');
                if (bare === bkey) continue;
                try { if (typeof window[bare] !== 'function') window[bare] = __bindSrc[bkey].bind(window); } catch (e) { /* noop */ }
            }
        }
        window.__rikkaPredefineInfo = {
            parentTHKeys: parentTHKeys,
            existingKeys: existingKeys,
            usedKeys: Object.keys(window.TavernHelper).length,
        };
        // 2.5) 脚本按钮 / 脚本身份：per-iframe 包装（对齐 JSR 语义）
        var sid = ${JSON.stringify(scriptId)};
        var sname = ${JSON.stringify(scriptName)};
        function wrapButton(fnName) {
            return function () {
                var args = Array.prototype.slice.call(arguments);
                var hub = p.__rikkaButtonHub;
                if (!hub || typeof hub[fnName] !== 'function') return undefined;
                return hub[fnName].apply(null, [sid, sname].concat(args));
            };
        }
        window.appendInexistentScriptButtons = wrapButton('append');
        window.replaceScriptButtons = wrapButton('replace');
        window.updateScriptButtonsWith = wrapButton('update');
        window.getScriptButtons = function () {
            return (p.__rikkaButtonHub && p.__rikkaButtonHub.list) ? p.__rikkaButtonHub.list(sid) : [];
        };
        window.getButtonEvent = function (name) {
            return (p.__rikkaButtonHub && p.__rikkaButtonHub.event) ? p.__rikkaButtonHub.event(sid, String(name)) : String(name);
        };
        // 脚本身份 API（覆盖主窗口硬编码实现）
        window.getScriptId = function () { return sid; };
        window.getScriptName = function () { return sname; };
        window.getIframeName = function () { return window.name; };
        if (window.TavernHelper) {
            window.TavernHelper.appendInexistentScriptButtons = window.appendInexistentScriptButtons;
            window.TavernHelper.replaceScriptButtons = window.replaceScriptButtons;
            window.TavernHelper.updateScriptButtonsWith = window.updateScriptButtonsWith;
            window.TavernHelper.getScriptButtons = window.getScriptButtons;
            window.TavernHelper.getButtonEvent = window.getButtonEvent;
            window.TavernHelper.getScriptId = window.getScriptId;
            window.TavernHelper.getScriptName = window.getScriptName;
            window.TavernHelper.getIframeName = window.getIframeName;
        }
        // 3) SillyTavern 代理
        Object.defineProperty(window, 'SillyTavern', {
            get: function () { return p.SillyTavern; },
            configurable: true
        });
        // 4) Mvu 代理（MVU-offline / 卡 MVU 脚本挂到 parent）
        Object.defineProperty(window, 'Mvu', {
            get: function () { return p.Mvu; },
            set: function (v) { p.Mvu = v; try { p.__rikkaMvuOwner = window.name; } catch (e) { /* noop */ } },
            configurable: true
        });
        // 5) vue / pinia 标志（JSR predefine.js 同款）
        window.__VUE_PROD_DEVTOOLS__ = true;
        window.__VUE_OPTIONS_API__ = true;
        window.__VUE_PROD_HYDRATION_MISMATCH_DETAILS__ = false;
        // 6) iframe 名称（JSR getScriptId 解析语义）
        window.name = ${JSON.stringify(iframeName)};
        window.__TH_IFRAME_ID = window.name;
        console.log('[TH-script] predefine ok: ' + window.name);
        if (typeof window.__rikkaDiag === 'function') window.__rikkaDiag('predefine-ok', window.__rikkaPredefineInfo);
    } catch (err) {
        window.__rikkaPredefineErr = String((err && (err.stack || err.message)) || err);
        if (typeof window.__rikkaDiag === 'function') window.__rikkaDiag('predefine-failed', { err: window.__rikkaPredefineErr });
        console.error('[TH-script] predefine failed', err);
    }
})();
<\/script>
<script src="${parentOrigin}/st-runtime/vendor/lib/jquery-3.5.1.min.js"><\/script>
<script src="${parentOrigin}/st-runtime/vendor/lib/lodash.min.js"><\/script>
</head>
<body>
<script>
(function () {
    // [v218] 错误转发与 console 桥已提前到 <head>（predefine 之前）安装，此处不再重复。
    // iframe 卸载清理（重建脚本时防止监听器 / Mvu 泄漏）
    $(window).on('pagehide', function () {
        try {
            if (window.parent && window.parent.__rikkaMvuOwner === window.name) {
                delete window.parent.Mvu;
                delete window.parent.__rikkaMvuOwner;
            }
        } catch (e) { /* noop */ }
        try { if (window.eventClearAll) window.eventClearAll(); } catch (e) { /* noop */ }
    });
})();
<\/script>
<script type="module">
// [v218] 卡片脚本执行前最后一道自检：eventOn 缺失时立刻上报（而不是等卡自己抛「is not defined」）
try { if (typeof window.__rikkaDiag === 'function') window.__rikkaDiag('module-start'); } catch (_e) { /* noop */ }
${content}
<\/script>
</body>
</html>`;
}

function mountScriptIframe(script) {
    const id = String(script.id == null || script.id === '' ? (script.name == null ? 'script' : script.name) : script.id);
    if (scriptIframes.has(id)) {
        try { scriptIframes.get(id).remove(); } catch (_e) { /* noop */ }
        scriptIframes.delete(id);
    }
    const html = buildScriptIframeHtml(script);
    const blob = new Blob([html], { type: 'text/html' });
    const blobUrl = URL.createObjectURL(blob);
    const iframe = document.createElement('iframe');
    iframe.src = blobUrl;
    iframe.id = `TH-script--${id}`;
    iframe.style.cssText = 'display:none;width:0;height:0;border:none;position:absolute;pointer-events:none;';
    document.body.appendChild(iframe);
    iframe.addEventListener('load', () => { try { URL.revokeObjectURL(blobUrl); } catch (_e) { /* noop */ } });
    // [v218] 宿主侧兜底注入：**不依赖子窗口去读 window.parent**。
    // 真机 P0「eventOn is not defined」的现场是「首次装载正常、force 重装后必现」，
    // 说明子窗口 head predefine 在某些时机会拿不到父窗口的 TavernHelper；而父→子的直接赋值
    // 在任何时候都成立（同源导航复用同一个 Window 对象，文档还没解析就能写）。
    // [v218.1]/[v221] 宿主侧兜底注入：**不依赖子窗口去读 window.parent**。
    // v221 真机根因：父窗口 TavernHelper 被 JSR 换成它那份（顶层无 eventOn）→
    // pushRuntimeGlobals 现在同时装顶层 key 与 _bind key，并用模块内建那份兜底。
    const pushShims = () => pushRuntimeGlobals(iframe.contentWindow);
    pushShims();
    iframe.addEventListener('load', pushShims);
    [50, 250, 800].forEach((ms) => setTimeout(pushShims, ms));
    guardChildGlobals(iframe.contentWindow);
    setTimeout(() => {
        try {
            const w = iframe.contentWindow;
            if (w && typeof w.eventOn === 'function') return;
            let diag = 'n/a';
            try { diag = JSON.stringify(w && w.__rikkaDiag ? w.__rikkaDiag('host-verify') : { eventOn: typeof (w && w.eventOn) }); } catch (_e) { diag = 'diag-failed'; }
            callBridge('log', 'error', `[TH-script] host-verify FAILED: ${iframe.id} eventOn missing; ${diag}`);
        } catch (_e) { /* noop */ }
    }, 2500);

    scriptIframes.set(id, iframe);
    console.log(`${LOG_PREFIX} script iframe mounted: ${iframe.id} (${script.name || ''})`);
}

async function loadTavernScripts(force) {
    let raw = '';
    try { raw = callBridge('getTavernScriptsJson') || '{}'; } catch (_e) { raw = '{}'; }
    let helper = null;
    try { helper = (JSON.parse(raw) || {}).tavern_helper || null; } catch (_e) { helper = null; }
    const list = (helper && Array.isArray(helper.scripts)) ? helper.scripts : [];
    const scripts = list.filter((s) => s && s.type === 'script' && scriptEnabled(s.enabled));
    const signature = scripts.map((s) => `${String(s.id)}:${String(s.name)}`).join('|');
    const convTag = String(state.chatId == null ? '' : state.chatId);
    if (!force && signature === lastScriptsSignature) {
        try { callBridge('log', 'debug', `[scripts] skip (same signature, conv=${convTag}, n=${scripts.length})`); } catch (_e) { /* noop */ }
        return false;
    }
    // 集合变化：全部重建（pagehide 会自清理监听与 Mvu）
    for (const [id, frame] of scriptIframes) {
        try { frame.remove(); } catch (_e) { /* noop */ }
        scriptIframes.delete(id);
    }
    lastScriptsSignature = signature;
    mvuFromScripts = scripts.some((s) => /mvu_bundle_full|MVU-offline/i.test(String(s.content || '')));
    for (const s of scripts) mountScriptIframe(s);
    try { callBridge('log', 'info', `[scripts] loaded ${scripts.length}${scripts.length ? ': ' + scripts.map((s) => s.name).join(', ') : ''}, mvu_from_scripts=${mvuFromScripts} (conv=${convTag}, force=${!!force})`); } catch (_e) { /* noop */ }
    emitToHost('scripts_loaded', { count: scripts.length, mvu_from_scripts: mvuFromScripts });
    return true;
}

// ---------------------------------------------------------------- 第三方扩展（ST third-party loader）
// ST 语义：扩展以 ES module 注入（<script type="module" src="/scripts/extensions/third-party/<folder>/<js>">），
// CSS 以 <link> 注入；模块内的相对 import（../../../../script.js 等）经 URL 归一化后，
// 由宿主 shouldInterceptRequest 路由到 st-compat shim（/script.js、/scripts/<name>.js）。
const thirdPartyStyleEls = [];
const thirdPartyScriptEls = [];
const thirdPartyLoadedUrls = new Set();
let lastThirdPartySignature = null;

function thirdPartyAssetUrl(folder, rel) {
    const segs = String(rel).split('/').map((s) => encodeURIComponent(s));
    return `/scripts/extensions/third-party/${encodeURIComponent(folder)}/${segs.join('/')}`;
}

function injectThirdPartyStyles(list) {
    for (const ext of list) {
        const css = String(ext.css || '');
        if (!css) continue;
        const el = document.createElement('link');
        el.rel = 'stylesheet';
        el.type = 'text/css';
        el.href = thirdPartyAssetUrl(ext.folder, css);
        el.setAttribute('data-rikka-third-party', ext.folder);
        document.head.appendChild(el);
        thirdPartyStyleEls.push(el);
    }
}

function injectThirdPartyModule(ext, js) {
    const src = thirdPartyAssetUrl(ext.folder, js);
    // 模块注册表按 URL 缓存：同一 URL 不会二次执行（防重复注册事件监听）
    if (thirdPartyLoadedUrls.has(src)) return Promise.resolve();
    thirdPartyLoadedUrls.add(src);
    // 用动态 import() 而不是 <script type="module" src>：
    // script 元素的 error 事件拿不到任何原因（连 HTTP 状态都没有），
    // 动态 import 会抛出真正的 Error（含失败的具体子模块），再补一次 fetch 拿状态码。
    return Promise.resolve()
        .then(() => import(src))
        .then(() => {
            try { callBridge('log', 'info', `[third-party] loaded ${ext.folder}/${js}`); } catch (_e) { /* noop */ }
        })
        .catch(async (err) => {
            let detail = 'http=?';
            try {
                const res = await fetch(src, { cache: 'no-store' });
                detail = `http=${res.status}`;
            } catch (_e2) { detail = 'http=ERR'; }
            const msg = (err && (err.stack || (err.name + ': ' + err.message))) || String(err);
            try {
                callBridge('log', 'error', `[third-party] 加载失败: ${ext.folder}/${js} ${detail} url=${src} → ${msg}`);
            } catch (_e3) { /* noop */ }
        });
}

async function loadThirdPartyExtensions(force) {
    let raw = '[]';
    try { raw = callBridge('getThirdPartyExtensionsJson') || '[]'; } catch (_e) { raw = '[]'; }
    let list = [];
    try { const parsed = JSON.parse(raw); if (Array.isArray(parsed)) list = parsed; } catch (_e) { list = []; }
    // [v197] 扩展设置页：URL 参数 ext=<folder> 时只加载被点击的那个扩展。
    let settingsFolderForLoad = '';
    try { settingsFolderForLoad = new URLSearchParams(location.search).get('ext') || ''; } catch (_e) { settingsFolderForLoad = ''; }
    if (settingsFolderForLoad) {
        const beforeFilter = list.length;
        list = list.filter((e) => String(e.folder || '') === settingsFolderForLoad);
        try {
            if (list.length === 0) {
                callBridge('log', 'error', `[third-party] 设置页目标扩展不存在或已停用: ${settingsFolderForLoad} (active=${beforeFilter})`);
            } else {
                callBridge('log', 'info', `[third-party] 设置页过滤 ext=${settingsFolderForLoad} matched=${list.length}/${beforeFilter}`);
            }
        } catch (_e) { /* noop */ }
    }
    const signature = list.map((e) => `${String(e.folder)}:${String(e.js)}:${String(e.css || '')}`).join('|');
    if (!force && signature === lastThirdPartySignature) return;
    lastThirdPartySignature = signature;

    // 拆卸旧资源（重装语义：先移除旧标签，再按新列表注入）
    for (const el of thirdPartyStyleEls.splice(0)) { try { el.remove(); } catch (_e) { /* noop */ } }
    for (const el of thirdPartyScriptEls.splice(0)) { try { el.remove(); } catch (_e) { /* noop */ } }

    // 供 getExtensionManifest 等扩展 API 查询
    try {
        window.__rikkaSt = window.__rikkaSt || {};
        window.__rikkaSt.extensions = list;
    } catch (_e) { /* noop */ }

    if (list.length === 0) return;

    injectThirdPartyStyles(list);
    for (const ext of list) {
        const js = String(ext.js || '');
        if (!js || !ext.folder) continue;
        await injectThirdPartyModule(ext, js);
    }

    // 全部模块装载完成后：广播刷新（shim live bindings）+ 上报
    try { window.dispatchEvent(new Event('rikka-st-refresh')); } catch (_e) { /* noop */ }
    try {
        callBridge('log', 'info', `[third-party] loaded ${list.length}: ${list.map((e) => e.displayName || e.folder).join(', ')}`);
    } catch (_e) { /* noop */ }
    emitToHost('third_party_extensions_loaded', { count: list.length, folders: list.map((e) => e.folder) });
}

// Kotlin 侧触发（安装/启停/删除扩展后）：eval("__rikkaReloadThirdPartyExtensions()")
window.__rikkaReloadThirdPartyExtensions = () => loadThirdPartyExtensions(true);

// ---------------------------------------------------------------- #tavern_helper DOM（MVU unique_check 依赖）
// ST/JSR 语义：`#tavern_helper` 是「脚本清单容器」；MVU 的 unique_check 用
//   `$('#tavern_helper').find('div[data-script-id]')` 判断「谁是被选中的 MVU 实现」：
//   取「注册过的 id 里、在 DOM 中排最后的那一个」，命中自己才 should_enable=true，才会发布 window.Mvu。
//
// ⚠️ v206 真机根因（诊断埋点实测 diag={"helperLen":1,"idsViaParentJq":[]}）：
//   JSR 也在它的 jQuery ready 回调里 `$('<div id="tavern_helper">').appendTo('#extensions_settings')`
//   （见 dist: `$(...{let e=$('<div id="tavern_helper">').appendTo('#extensions_settings');$9.mount(e[0])}`）。
//   而 `getElementById` / `$('#id')` 只返回【文档序第一个】——`#extensions_settings` 在 body 前部、
//   我方容器在 body 末尾 → MVU 查到的永远是 JSR 那个「里面没有 div[data-script-id]」的容器 →
//   getPreferredScriptId()=undefined ≠ 'mvu-standalone' → should_enable=false →
//   **window.Mvu 永不发布** → 真机 30s 后 `MVU init timeout`（红字），卡内脚本同时报
//   「未检测到MVU框架，核心引擎无法启动！」。
// 修法：**每一个** #tavern_helper 容器里都要有我方脚本身份 div（不碰 JSR 容器的可见性/内容）。
function ensureTavernHelperDom(scriptIds) {
    let holders = Array.from(document.querySelectorAll('#tavern_helper'));
    if (holders.length === 0) {
        const created = document.createElement('div');
        created.id = 'tavern_helper';
        created.style.display = 'none';
        document.body.appendChild(created);
        holders = [created];
    }
    for (const holder of holders) {
        for (const sid of scriptIds) {
            if (!holder.querySelector(`div[data-script-id="${sid}"]`)) {
                const d = document.createElement('div');
                d.setAttribute('data-script-id', sid);
                holder.appendChild(d);
            }
        }
    }
    return holders.length;
}

// v206：JSR 的容器可能在任何时刻被创建/被 Vue 重渲染清空 → 短期内轮询补齐（比全文档 MutationObserver 便宜）
function keepTavernHelperDom(scriptIds, ms = 20000) {
    const deadline = Date.now() + ms;
    const t = setInterval(() => {
        if (Date.now() > deadline) { clearInterval(t); return; }
        try { ensureTavernHelperDom(scriptIds); } catch (_e) { /* noop */ }
    }, 500);
    return t;
}

// v206：把「首选 MVU 实现」重新广播一次，逼 MVU bundle 内的 listenPreferenceState 回调重算 should_enable。
// 背景：bundle 的 should_enable 只在 listenPreferenceState 回调里赋值一次（= 取 DOM 里排最后的那个实现），
// 那个时刻如果 DOM 还不正确，它就永远停在 false。事件总线是同一个（我方 shims 的 eventEmit/eventOn）。
// [v218.1] 从脚本 iframe 里「认领」卡片自带 MVU。
// 背景（v218 真机 diag 实证）：魔法少女卡的 MVU-offline bundle 在**脚本 iframe 自己的 window** 上
// 建立 Mvu —— 我们的 predefine 把 `Mvu` 定义成 proxy（set → p.Mvu），但 bundle 若用
// Object.defineProperty 重建 'Mvu' 就会绕过 setter，父窗口永远看不到；宿主只能 6s 后回退内置 MVU
// （面板「vars wait timeout」那一族就是这么来的）。
// 父→子读是同源的，直接把子窗口那份取过来即可（不依赖子窗口的 setter）。
function adoptScriptIframeMvu() {
    const pick = (w) => {
        if (!w) return null;
        let cand = null;
        try { cand = w.Mvu; } catch (_e) { return null; }
        if (!cand) return null;
        if (typeof cand !== 'object' && typeof cand !== 'function') return null;
        return cand;
    };
    // [v235 I4] 认领时登记 owner：卸载清理只允许「自己发布的」Mvu 被删，
    // 否则旧 iframe 的 pagehide 会把新卡刚认领的 Mvu 一起删掉（面板 vars 恒空）。
    const claim = (w, cand) => {
        try { window.__rikkaMvuOwner = (w && w.name) || ''; } catch (_e) { /* noop */ }
        return cand;
    };
    try {
        for (const [, frame] of scriptIframes) {
            const w = frame && frame.contentWindow;
            const got = pick(w);
            if (got) return claim(w, got);
        }
    } catch (_e) { /* noop */ }
    try { const w = mvuIframe && mvuIframe.contentWindow; return claim(w, pick(w)); } catch (_e) { return null; }
}

function forceMvuPreference(scriptId = 'mvu-standalone', label = 'MVU变量框架') {
    try {
        shimEvent.eventEmit(`th_unique_check.${label}`, scriptId);
        console.log(`${LOG_PREFIX} re-emit ${`th_unique_check.${label}`} = ${scriptId}`);
    } catch (_e) { /* noop */ }
}

// ---------------------------------------------------------------- MVU iframe（blob 宿主）
const MVU_BUNDLE_URL = 'https://appassets.androidplatform.net/st-runtime/mvu/bundle.js';
let mvuIframe = null;
let mvuReady = false;
let mvuPollTimer = null;
let mvuAutoRetries = 0;

function buildMvuIframeHtml() {
    const shimNames = Object.keys(window.__mvu_shims__ || {});
    const shimCopyCode = shimNames
        .map((name) => `window[${JSON.stringify(name)}] = p.__mvu_shims__[${JSON.stringify(name)}];`)
        .join('\n            ');
    const parentOrigin = window.location.origin;
    return `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>MVU Runtime</title>
<script>
(function () {
    var p = window.parent;
    var parentOrigin = ${JSON.stringify(parentOrigin)};
    window.parentOrigin = parentOrigin;
    // ── [v205] 诊断：iframe 内的 console / 未捕获错误一律转发到宿主日志。
    //    此前这一层是 0 可见性（真机日志里一条 MVU-Iframe 都没有），
    //    所以「MVU init timeout」永远查不下去。
    function __rikkaFwd(level, text) {
        try {
            window.parent.postMessage({ type: 'mvu-iframe-log', level: level, text: String(text == null ? '' : text).slice(0, 900) }, '*');
        } catch (e) { /* noop */ }
    }
    ['log', 'warn', 'error'].forEach(function (lv) {
        var orig = console[lv] ? console[lv].bind(console) : function () {};
        console[lv] = function () {
            var args = Array.prototype.slice.call(arguments);
            try { orig.apply(null, args); } catch (e) { /* noop */ }
            try {
                __rikkaFwd(lv, args.map(function (a) { return String(a && a.message ? a.message : a); }).join(' '));
            } catch (e) { /* noop */ }
        };
    });
    window.addEventListener('error', function (e) {
        __rikkaFwd('error', 'window.onerror: ' + (e && e.message) + ' @' + (e && e.filename) + ':' + (e && e.lineno));
    });
    window.addEventListener('unhandledrejection', function (e) {
        var r = e && e.reason;
        __rikkaFwd('error', 'unhandledrejection: ' + ((r && (r.stack || r.message)) || r));
    });
    // ── [v205] 脚本身份显式声明（与 script iframe 同款）：不再依赖「从 parent 复制过来的闭包」
    window.getScriptId = function () { return 'mvu-standalone'; };
    window.getScriptName = function () { return 'MVU变量框架'; };
    window.getIframeName = function () { return window.name; };
    // ── [v205] 自检：宿主侧超时/复现时可随时调用，一次性上报所有关键中间量
    window.__rikkaMvuSelfCheck = function () {
        var out = {};
        try { out.iframeName = window.name; } catch (e) { out.iframeName = 'ERR ' + e.message; }
        try { out.scriptId = (typeof getScriptId === 'function') ? getScriptId() : 'NO-FN'; } catch (e) { out.scriptId = 'ERR ' + e.message; }
        try { out.isSelfOverride = String(getScriptId) !== String(p.__mvu_shims__ && p.__mvu_shims__.getScriptId); } catch (e) { out.isSelfOverride = 'ERR ' + e.message; }
        try { out.jq = typeof $; out.lodash = typeof _; } catch (e) { /* noop */ }
        try { out.helperLen = $('#tavern_helper').length; } catch (e) { out.helperLen = 'ERR ' + e.message; }
        try {
            out.idsViaParentJq = $('#tavern_helper').find('div[data-script-id]').toArray()
                .map(function (el) { return String($(el).attr('data-script-id')); });
        } catch (e) { out.idsViaParentJq = 'ERR ' + e.message; }
        try { out.hasMvuNow = !!window.Mvu; } catch (e) { out.hasMvuNow = 'ERR ' + e.message; }
        try { out.parentMvu = !!(window.parent && window.parent.Mvu); } catch (e) { out.parentMvu = 'ERR ' + e.message; }
        try {
            var st = _.get(window.parent, 'th_unique_check.MVU变量框架');
            out.registered = st ? Array.from(st) : null;
        } catch (e) { out.registered = 'ERR ' + e.message; }
        try {
            out.preferred = (function () {
                var st2 = _.get(window.parent, 'th_unique_check.MVU变量框架', new Set());
                return _($('#tavern_helper').find('div[data-script-id]').toArray())
                    .map(function (el) { return String($(el).attr('data-script-id')); })
                    .filter(function (id) { return st2.has(id); })
                    .last();
            })();
        } catch (e) { out.preferred = 'ERR ' + e.message; }
        return JSON.stringify(out);
    };
    try {
        // 1) shim API 复制
        ${shimCopyCode}
        // 2) 全局库复制
        window.$ = window.jQuery = p.$;
        window._ = p._;
        window.toastr = p.toastr;
        window.Vue = p.Vue;
        window.VueRouter = p.VueRouter;
        if (p.z) window.z = p.z;
        if (p.YAML) window.YAML = p.YAML;
        window.klona = p.klona || function klona(obj) {
            if (typeof structuredClone === 'function') {
                try { return structuredClone(obj); } catch (e) {}
            }
            if (p._ && p._.cloneDeep) return p._.cloneDeep(obj);
            return JSON.parse(JSON.stringify(obj));
        };
        // 3) TavernHelper 对象（MVU bundle 通过 globalThis.TavernHelper 访问 API）
        window.TavernHelper = {};
        var shimObj = p.__mvu_shims__ || {};
        for (var key in shimObj) {
            if (Object.prototype.hasOwnProperty.call(shimObj, key)) {
                window.TavernHelper[key] = shimObj[key];
            }
        }
        window.TavernHelper._bind = window.TavernHelper._bind || {};
        // [v221] 同款补全：_bind 去下划线挂裸全局（对齐 JSR predefine.js:14-18）
        try {
            var __mvubind = window.TavernHelper._bind;
            for (var __mvubk in __mvubind) {
                var __mvubare = String(__mvubk).replace(/^_/, '');
                if (__mvubare === __mvubk) continue;
                if (typeof window[__mvubare] !== 'function') window[__mvubare] = __mvubind[__mvubk].bind(window);
            }
        } catch (__mvue) { /* noop */ }
        // 4) fetch 修复（blob iframe 中根相对路径）
        var originalFetch = window.fetch;
        window.fetch = function (url, options) {
            try {
                if (typeof url === 'string' && url.startsWith('/')) {
                    url = parentOrigin + url;
                }
            } catch (e) {}
            return originalFetch.call(window, url, options);
        };
        // 5) SillyTavern 代理（ctx + 顶层静态成员合并）
        var __stCtx = null, __stProxy = null;
        Object.defineProperty(window, 'SillyTavern', {
            get: function () {
                var ST = p.SillyTavern;
                if (!ST) return undefined;
                var ctx = ST.getContext();
                if (!__stProxy || __stCtx !== ctx) {
                    __stCtx = ctx;
                    __stProxy = new Proxy(ctx, {
                        get: function (target, prop) {
                            if (prop in target) return target[prop];
                            var v = ST[prop];
                            return (typeof v === 'function') ? v.bind(ST) : v;
                        },
                        has: function (target, prop) {
                            return (prop in target) || (prop in ST);
                        }
                    });
                }
                return __stProxy;
            },
            configurable: true
        });
        // 6) Mvu 代理（MVU 设置后暴露到 parent）
        Object.defineProperty(window, 'Mvu', {
            get: function () { return p.Mvu; },
            set: function (v) { p.Mvu = v; try { p.__rikkaMvuOwner = window.name; } catch (e) { /* noop */ } },
            configurable: true
        });
        // 7) iframe 名称（对齐 JS-Slash-Runner 的 script iframe 命名 → getScriptId 解析）
        window.name = 'TH-script--MVU变量框架--mvu-standalone';
        window.addEventListener('pagehide', function () {
            try { if (p.__rikkaMvuOwner === window.name) { delete p.Mvu; delete p.__rikkaMvuOwner; } } catch (e) {}
        });
        console.log('[MVU-Iframe] shim globals injected');
    } catch (err) {
        console.error('[MVU-Iframe] bootstrap failed', err);
    }
})();
<\/script>
<script src="${parentOrigin}/st-runtime/vendor/lib/vue.runtime.global.prod.js"><\/script>
<script src="${parentOrigin}/st-runtime/vendor/lib/vue-router.global.prod.js"><\/script>
</head>
<body>
<script type="module">
try {
    try {
        const zod_module = await import(${JSON.stringify(parentOrigin + '/st-runtime/vendor/lib/zod.esm.js')});
        window.z = zod_module; // 命名空间本体（含 .z 与全部顶层成员，JSR 同款语义）
    } catch (e) { console.warn('[MVU-Iframe] zod load failed:', e.message); }
    try {
        const YAML_module = await import(${JSON.stringify(parentOrigin + '/st-runtime/vendor/lib/yaml.esm.js')});
        window.YAML = YAML_module.default || YAML_module;
    } catch (e) { console.warn('[MVU-Iframe] yaml load failed:', e.message); }
    await import(${JSON.stringify(MVU_BUNDLE_URL)});
    console.log('[MVU-Iframe] MVU bundle loaded');
    window.parent.postMessage({ type: 'mvu-standalone-loaded' }, '*');
} catch (err) {
    console.error('[MVU-Iframe] bundle load failed:', err);
    window.parent.postMessage({ type: 'mvu-standalone-error', error: String((err && err.message) || err) }, '*');
}
<\/script>
</body>
</html>`;
}

function createMvuIframe() {
    if (mvuIframe) {
        try { mvuIframe.remove(); } catch (_e) { /* noop */ }
        mvuIframe = null;
    }
    const html = buildMvuIframeHtml();
    const blob = new Blob([html], { type: 'text/html' });
    const blobUrl = URL.createObjectURL(blob);
    const iframe = document.createElement('iframe');
    iframe.src = blobUrl;
    iframe.id = 'mvu-runtime-iframe';
    iframe.style.cssText = 'display:none;width:0;height:0;border:none;position:absolute;pointer-events:none;';
    document.body.appendChild(iframe);
    iframe.addEventListener('load', () => {
        try { URL.revokeObjectURL(blobUrl); } catch (_e) { /* noop */ }
        console.log(`${LOG_PREFIX} MVU iframe loaded`);
    });
    // [v218] 同款宿主侧兜底注入：MVU bundle 顶层也裸调 eventOn(...)，不能只靠 iframe 内 self-copy。
    const pushMvuShims = () => pushRuntimeGlobals(iframe.contentWindow);
    pushMvuShims();
    iframe.addEventListener('load', pushMvuShims);
    [50, 250, 800].forEach((ms) => setTimeout(pushMvuShims, ms));
    guardChildGlobals(iframe.contentWindow);

    mvuIframe = iframe;
    return iframe;
}

// MVU/脚本语义：会话就绪通知（chat_changed / chat_id_changed）。
// 注意：chat_completion_settings_ready 是「生成前由宿主填充 messages」的语义，
// 不能在 chat ready 时无参 emit；旧的无参 emit 会让 ST-Prompt-Template 的 handler 取 data.messages 直接崩。
function notifyChatReady() {
    // [batch17] ST 里 CHAT_CHANGED 的常量值是 'chat_id_changed'（见 shims/constants.js:21），
    // 以前这里只发 'chat_changed' 且载荷只有 chatId（可能还是 undefined）→ 两边的名字都对不上。
    // 现在两个名字都发，并带上 { chatId, messages }，监听方拿到的至少是个对象。
    const chatChangedPayload = { chatId: String(state.chatId == null ? '' : state.chatId), messages: Array.isArray(state.chat) ? state.chat : [] };
    try { eventSource.emit('chat_changed', chatChangedPayload); } catch (_e) { /* noop */ }
    try { eventSource.emit('chat_id_changed', chatChangedPayload); } catch (_e) { /* noop */ }
}

function waitForMvu(timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
        if (window.Mvu) { resolve(window.Mvu); return; }
        const started = Date.now();
        let earlyError = null;
        // 移植自 mvu-sillytavern-extension：监听 MVU iframe 的加载/错误消息，
        // 让「bundle 加载失败」立即可见（而不是静默等超时）。
        const onMessage = (ev) => {
            const d = ev && ev.data;
            if (!d || typeof d !== 'object') return;
            if (d.type === 'mvu-iframe-log') {
                // [v205] MVU iframe 的 console/未捕获错误 → 宿主日志（此前完全不可见）
                try { callBridge('log', String(d.level || 'debug'), `[MVU-iframe] ${String(d.text || '')}`); } catch (_e) { /* noop */ }
                return;
            }
            if (d.type === 'mvu-standalone-loaded') {
                console.log(`${LOG_PREFIX} MVU iframe reported loaded, waiting for window.Mvu ...`);
                try { ensureTavernHelperDom(['mvu-standalone']); } catch (_e) { /* noop */ }
                forceMvuPreference();
                // [v205] 快速自检：bundle 加载后 3s 若仍无 window.Mvu，立刻上报中间量（不等 30s 超时）
                setTimeout(() => {
                    if (window.Mvu) return;
                    try {
                        const diag = mvuIframe && mvuIframe.contentWindow && mvuIframe.contentWindow.__rikkaMvuSelfCheck
                            ? mvuIframe.contentWindow.__rikkaMvuSelfCheck() : 'no-selfcheck';
                        console.warn(`${LOG_PREFIX} MVU selfcheck (no window.Mvu 3s after bundle loaded): ${diag}`);
                    } catch (e) { console.warn(`${LOG_PREFIX} MVU selfcheck failed: ${(e && e.message) || e}`); }
                }, 3000);
            } else if (d.type === 'mvu-standalone-error') {
                earlyError = new Error(String(d.error || 'mvu-standalone-error'));
            }
        };
        window.addEventListener('message', onMessage);
        const cleanup = () => {
            window.removeEventListener('message', onMessage);
            if (mvuPollTimer) { clearInterval(mvuPollTimer); mvuPollTimer = null; }
        };
        mvuPollTimer = setInterval(() => {
            if (window.Mvu) {
                cleanup();
                mvuReady = true;
                resolve(window.Mvu);
                return;
            }
            // [v218.1] 卡片自带 MVU 的采纳路径（见 adoptScriptIframeMvu 注释）。
            // 只在父窗口还看不到 Mvu 时才走，避免抢掉正常路径。
            try {
                const adopted = adoptScriptIframeMvu();
                if (adopted) {
                    window.Mvu = adopted;
                    cleanup();
                    mvuReady = true;
                    console.log(`${LOG_PREFIX} MVU adopted from script iframe (version=${String(adopted && adopted.version || '')})`);
                    emitToHost('mvu_ready', { source: 'script-adopted', version: String(adopted && adopted.version || '') });
                    resolve(window.Mvu);
                    return;
                }
            } catch (_e) { /* noop */ }
            if (earlyError) {
                cleanup();
                reject(earlyError);
                return;
            }
            if (Date.now() - started > timeoutMs) {
                cleanup();
                // [v205] 超时不再只有一句话：把 iframe 自检结果一起带出来
                let diag = 'n/a';
                try {
                    diag = (mvuIframe && mvuIframe.contentWindow && mvuIframe.contentWindow.__rikkaMvuSelfCheck)
                        ? mvuIframe.contentWindow.__rikkaMvuSelfCheck() : 'no-selfcheck';
                } catch (e) { diag = 'ERR ' + ((e && e.message) || e); }
                reject(new Error(`MVU init timeout after ${timeoutMs}ms (window.Mvu not found; iframe=${mvuIframe ? 'created' : 'none'}; diag=${diag})`));
            }
        }, 250);
    });
}

let mvuLoading = false;
let mvuReloadQueued = false;
async function loadMvu() {
    if (mvuLoading) {
        // [v235 I4] 旧实现直接 return：会话切换时若上一次装载仍在飞，新卡这一路会被彻底丢掉。
        // 改为记一个补跑标记，等 in-flight 结束后再跑一次。
        mvuReloadQueued = true;
        console.log(`${LOG_PREFIX} loadMvu queued (already in flight)`);
        return;
    }
    mvuLoading = true;
    try {
    mvuReady = false;
    if (window.Mvu) {
        mvuReady = true;
        console.log(`${LOG_PREFIX} MVU already available (provided by card scripts)`);
        emitToHost('mvu_ready', { source: 'script', version: String(window.Mvu?.version || '') });
        setTimeout(() => notifyChatReady(), 150);
        return;
    }
    if (mvuFromScripts) {
        console.log(`${LOG_PREFIX} waiting MVU from card scripts ...`);
        // [v218.1] 先试一次认领：卡片 bundle 可能已经建好，只是没走我们的 setter
        try {
            const pre = adoptScriptIframeMvu();
            if (pre) {
                window.Mvu = pre;
                mvuReady = true;
                console.log(`${LOG_PREFIX} MVU adopted from script iframe (early, version=${String(pre.version || '')})`);
                emitToHost('mvu_ready', { source: 'script-adopted', version: String(pre.version || '') });
                setTimeout(() => notifyChatReady(), 150);
                return;
            }
        } catch (_e) { /* noop */ }
        try {
            await waitForMvu(6000);
            mvuReady = true;
            console.log(`${LOG_PREFIX} MVU ready (card scripts)`);
            emitToHost('mvu_ready', { source: 'script', version: String(window.Mvu?.version || '') });
        setTimeout(() => notifyChatReady(), 150);
            return;
        } catch (e) {
            console.warn(`${LOG_PREFIX} card-script MVU not ready (${(e && e.message) || e}), fallback to bundled MVU`);
        }
    }
    console.log(`${LOG_PREFIX} loading bundled MVU bundle ...`);
    // v206：身份 div 必须在 bundle 初始化 store 之前就位（它只在那一次读 DOM 决定 should_enable）
    try { ensureTavernHelperDom(['mvu-standalone']); } catch (_e) { /* noop */ }
    createMvuIframe();
    // v206：再加一道保险——把首选实现广播几次（覆盖 store 晚建 / 容器被重建的情况）
    for (const d of [600, 1500, 3000, 6000, 12000]) setTimeout(() => forceMvuPreference(), d);
    try {
        await waitForMvu();
        mvuReady = true;
        console.log(`${LOG_PREFIX} MVU ready (bundled)`);
        emitToHost('mvu_ready', { source: 'bundle', version: String(window.Mvu?.version || '') });
        setTimeout(() => notifyChatReady(), 150);
    } catch (e) {
        console.warn(`${LOG_PREFIX} bundled MVU not ready:`, e);
        emitToHost('mvu_error', {
            error: String((e && e.message) || e || 'unknown'),
            hasIframe: !!mvuIframe,
            hasMvu: !!window.Mvu,
        });
        // 瞬态失败自愈：自动重试一次（再失败则等待用户手动 __rikkaMvuReload）
        if (mvuAutoRetries < 1) {
            mvuAutoRetries += 1;
            console.log(`${LOG_PREFIX} scheduling MVU auto-retry #${mvuAutoRetries} in 8s ...`);
            setTimeout(() => { if (!window.Mvu) loadMvu(); }, 4000);
        }
    }
    } finally {
        mvuLoading = false;
        if (mvuReloadQueued) {
            mvuReloadQueued = false;
            if (!window.Mvu) setTimeout(() => { try { loadMvu(); } catch (_e) { /* noop */ } }, 300);
        }
    }
}

window.__rikkaMvuReload = () => { mvuAutoRetries = 0; return loadMvu(); };
window.__rikkaMvuStatus = () => ({ ready: mvuReady, hasIframe: !!mvuIframe, hasMvu: !!window.Mvu });

// ---------------------------------------------------------------- Panel 模式（C4：消息内嵌渲染宿主）
/**
 * [v214] 面板模式补 ST 聊天 DOM 桩：#send_textarea / #send_but / #send_form。
 * 卡内表单（如魔法少女 RS[0]）写 `window.parent.document.querySelector('#send_textarea')`，
 * 面板 WebView 里 window.parent === window；补桩后点 #send_but 即经 RikkaBridge.sendUserMessage
 * 交宿主以 user 身份入栈（此前必走剪贴板兜底 + throw 'SillyTavern UI not found.'）。
 */
function ensurePanelChatDomStubs() {
    try {
        const host = document.body || document.documentElement;
        if (!host) return;
        if (!document.getElementById('send_textarea')) {
            const ta = document.createElement('textarea');
            ta.id = 'send_textarea';
            ta.setAttribute('rows', '1');
            ta.style.cssText = 'position:absolute;left:-9999px;top:0;width:1px;height:1px;opacity:0;pointer-events:none;';
            host.appendChild(ta);
        }
        if (!document.getElementById('send_form')) {
            const form = document.createElement('div');
            form.id = 'send_form';
            form.style.cssText = 'display:none;';
            host.appendChild(form);
        }
        if (!document.getElementById('send_but')) {
            const btn = document.createElement('button');
            btn.id = 'send_but';
            btn.type = 'button';
            btn.style.cssText = 'display:none;';
            btn.addEventListener('click', () => {
                try {
                    const ta = document.getElementById('send_textarea');
                    const text = ta && typeof ta.value === 'string' ? ta.value : '';
                    if (!text.trim()) return;
                    const ok = callBridge('sendUserMessage', text);
                    if (ta) ta.value = '';
                    try { if (window.RikkaPanel && RikkaPanel.log) RikkaPanel.log('debug', '[panel] send_but → host sendUserMessage ok=' + (ok === true) + ' len=' + text.length); } catch (_e) { /* noop */ }
                } catch (e) { safeWarn('panel send_but failed', e); }
            });
            host.appendChild(btn);
        }
    } catch (_e) { /* noop */ }
}

function installPanelMode() {
    const root = document.getElementById('rikka-panel-root') || document.body;
    ensurePanelChatDomStubs();
    // 高度上报（v183 强化：多基准测量 + 变化去重 + 面板日志）
    let lastReportedHeight = -1;
    const measure = () => {
        const de = document.documentElement, b = document.body;
        const parts = [
            de ? de.scrollHeight : 0,
            b ? b.scrollHeight : 0,
            root.scrollHeight,
            Math.ceil(root.getBoundingClientRect().height || 0),
        ].filter((v) => typeof v === 'number' && isFinite(v) && v >= 0);
        return parts.length ? Math.max.apply(null, parts) : 0;
    };
    // [v229 P0-1] 面板宽度测量/上报**整条链路已删除**（主人已拍板：渲染/面板气泡恢复「正常气泡模式」）。
    //   v228 的实现是「测量 -> 约束 -> 再测量」的闭环：ResizeObserver 量到的是**被约束后**的宽度，
    //   回写又让它更窄，结构上不可能稳定 -> 真机「宽->细->宽」自激振荡（宽->细->突然变宽->再缩）。
    //   这里连 measureWidth/lastReportedWidth 都不再保留，杜绝任何形式的宽度反馈回归。
    const report = () => {
        // [v229 P0-1] 只上报高度。宽度不允许参与任何布局（高度上报保留）。
        try {
            const h = measure() | 0;
            if (h === lastReportedHeight) return;
            lastReportedHeight = h;
            if (window.RikkaPanel && typeof RikkaPanel.panelResize === 'function') RikkaPanel.panelResize(h);
            if (window.RikkaPanel && typeof RikkaPanel.log === 'function') RikkaPanel.log('debug', '[panel] height=' + h);
        } catch (_e) { /* noop */ }
    };
    window.__rikkaPanelReport = report;
    const runScripts = async (container) => {
        // 消息挂载时页面早已 loaded：r0 类「DOMContentLoaded 后初始化」的脚本原生监听
        // 不会再触发，导致表单点不动。这里做补偿式补丁：已加载状态下注册的
        // DOMContentLoaded/load 监听直接异步补执行一次。
        const readyPatch = (t) => {
            if (!t || t.__rikkaReadyPatched) return;
            try { t.__rikkaReadyPatched = true; } catch (_e) { /* noop */ }
            const origAdd = t.addEventListener;
            t.addEventListener = function (type, listener, opts) {
                if ((type === 'DOMContentLoaded' || type === 'load') && typeof listener === 'function' && document.readyState !== 'loading') {
                    setTimeout(() => {
                        try { listener.call(t, new Event(type)); } catch (e) { safeWarn('deferred ' + type + ' handler failed', e); }
                    }, 0);
                    return;
                }
                return origAdd.call(t, type, listener, opts);
            };
        };
        try { readyPatch(document); readyPatch(window); } catch (_e) { /* noop */ }
        const scripts = Array.from(container.querySelectorAll('script'));
        for (const s of scripts) {
            const ns = document.createElement('script');
            for (const attr of Array.from(s.attributes)) ns.setAttribute(attr.name, attr.value);
            if (s.src) {
                await new Promise((res) => {
                    ns.onload = res;
                    ns.onerror = res;
                    setTimeout(res, 8000);
                    s.replaceWith(ns);
                });
            } else {
                ns.text = s.textContent || '';
                s.replaceWith(ns);
            }
        }
    };
    const stripFences = (s) => String(s == null ? '' : s)
        .replace(/^\s*```[a-zA-Z0-9_-]*\s*\r?\n/, '')
        .replace(/\r?\n```\s*$/, '')
        .replace(/^\s*```[a-zA-Z0-9_-]*\s*$/gm, '');
    const normalizeHtml = (html) => {
        const unfenced = stripFences(html);
        if (/<!doctype|<html[\s>]/i.test(unfenced)) {
            try {
                const doc = new DOMParser().parseFromString(unfenced, 'text/html');
                const headBits = Array.from(doc.head.querySelectorAll('style,link[rel="stylesheet"]')).map((n) => n.outerHTML).join('\n');
                return headBits + '\n' + doc.body.innerHTML;
            } catch (_e) { /* fallthrough */ }
        }
        return unfenced;
    };
    // [DIAG B] 资源加载失败捕获（capture 阶段，target 就是元素本身）：
    // 面板 HTML 常引用 cdnjs font-awesome / Google Fonts，离线或被拦时图标字体静默变空白，
    // 这正是「面板渲染有问题」的一类肉眼可见症状，必须在日志里留痕。
    const badLoads = [];
    window.addEventListener('error', (ev) => {
        try {
            const t2 = ev && ev.target;
            if (t2 && t2 !== window && t2.tagName && (t2.tagName === 'LINK' || t2.tagName === 'SCRIPT' || t2.tagName === 'IMG')) {
                const url = String(t2.href || t2.src || '');
                let host = '';
                try { host = new URL(url).host; } catch (_e) { host = url.slice(0, 60); }
                const tag = String(t2.tagName) + ':' + host + url.split('/').pop();
                if (badLoads.indexOf(tag) < 0) badLoads.push(tag);
            }
        } catch (_e) { /* noop */ }
    }, true);
    // [DIAG B] 挂载后体检：MVU 是否可见、getMvuData 能否取到变量、脚本/样式数量、最终高度。
    // 排查「面板渲染异常」的关键事实，全部写进 tavern-runtime.log（[panel] 前缀）。
    const probe = (tag) => {
        try {
            if (!window.RikkaPanel || typeof RikkaPanel.log !== 'function') return;
    // [批次十三] 面板是「某条消息」的渲染宿主：本条消息 id 由 Kotlin 侧注入
    // （MessageHtmlBlock 在挂 HTML 前 evaluateJavascript 写 window.__rikkaPanelMessageKey）。
    // 用它把 chat 数组里对应的那条消息找出来；先前写死的 message_id: -1 永远只指向最后一条消息。
    function panelSelfId() {
        try {
            const key = window.__rikkaPanelMessageKey;
            if (key) {
                const chat = (window.SillyTavern.getContext().chat) || [];
                for (let i = 0; i < chat.length; i++) {
                    if (chat[i] && String(chat[i].id) === String(key)) {
                        window.__rikkaPanelMessageKeyIndex = i;
                        return i;
                    }
                }
                if (typeof window.__rikkaPanelMessageKeyIndex === 'number') return window.__rikkaPanelMessageKeyIndex;
            }
        } catch (_e) { /* noop */ }
        return -1;
    }


            const nScripts = root.querySelectorAll('script').length;
            const nLinks = root.querySelectorAll('link[rel="stylesheet"]').length;
            let vars = 'no-Mvu';
            if (window.Mvu && typeof window.Mvu.getMvuData === 'function') {
                try {
                    const v = window.Mvu.getMvuData({ type: 'message', message_id: panelSelfId() });
                    if (v === null || v === undefined) { vars = 'null'; }
                    else {
                        const ks = Object.keys(v);
                        vars = 'keys=[' + ks.slice(0, 8).join(',') + (ks.length > 8 ? ',+more' : '') + '] hasStat=' + !!(v && v.stat_data);
                        if (v && v.stat_data && typeof v.stat_data === 'object') {
                            vars += ' stat=[' + Object.keys(v.stat_data).slice(0, 10).join(',') + ']';
                        }
                    }
                } catch (e2) { vars = 'ERR ' + String((e2 && e2.message) || e2); }
            }
            RikkaPanel.log('debug', '[panel] probe(' + tag + ') scripts=' + nScripts + ' cssLinks=' + nLinks +
                ' mvu=' + !!window.Mvu + ' vars=' + vars + ' height=' + measure() +
                (badLoads.length ? ' failedLoads=[' + badLoads.join('|') + ']' : ''));
        } catch (_e) { /* noop */ }
    };
    // [T4] 面板变量就绪后重渲染（批次十二）。
    // 现场：真机日志 probe(mount+1500) 报 vars=keys=[]，而 MVU 变量要到 ~6 秒后才就绪 ——
    // 面板脚本（状态栏类）在挂载那一刻读到的是空变量，之后也没有任何人再通知它，于是面板一直是空壳。
    // 面板模式下的 Mvu 只是 stub（见本文件末尾的 Mvu stub），Mvu.events.* 从来没有被派发过。
    // 处理：挂载后短时轮询变量（400ms × 最多 20 次 ≈ 8 秒），一旦非空就
    //   ① 派发 Mvu 事件（mag_variable_initialized / mag_variable_update_ended）—— 照顾监听式卡脚本；
    //   ② 只重挂一次（整棵面板重新执行）—— 照顾一次性渲染的卡脚本。
    const hasMvuVars = (v) => !!v && typeof v === 'object' && (Object.keys(v).length > 0 || !!v.stat_data);
    const readMvuVars = () => {
        try {
            if (window.Mvu && typeof window.Mvu.getMvuData === 'function') {
                return window.Mvu.getMvuData({ type: 'message', message_id: panelSelfId() });
            }
        } catch (_e) { /* noop */ }
        return null;
    };
    let varsReady = false;
    let varsWaitTimer = null;
    let lastPanelHtml = null;
    const varsWait = (html) => {
        lastPanelHtml = html;
        if (varsReady || varsWaitTimer) return;
        // 挂载时变量就已经就绪 → 卡脚本已经拿到数据，不必等待、不必重挂
        if (hasMvuVars(readMvuVars())) { varsReady = true; return; }
        let ticks = 0;
        try { if (window.RikkaPanel && RikkaPanel.log) RikkaPanel.log('debug', '[panel] vars wait: start'); } catch (_e) { /* noop */ }
        varsWaitTimer = setInterval(() => {
            ticks++;
            const v = readMvuVars();
            if (hasMvuVars(v)) {
                clearInterval(varsWaitTimer); varsWaitTimer = null;
                varsReady = true;
                try {
                    if (window.RikkaPanel && RikkaPanel.log) {
                        RikkaPanel.log('debug', '[panel] vars ready after ' + ticks + ' ticks keys=[' +
                            Object.keys(v).slice(0, 8).join(',') + '] → remount');
                    }
                } catch (_e) { /* noop */ }
                try { window.dispatchEvent(new CustomEvent('mag_variable_initialized', { detail: v })); } catch (_e) { /* noop */ }
                try { window.dispatchEvent(new CustomEvent('mag_variable_update_ended', { detail: v })); } catch (_e) { /* noop */ }
                // 只重挂一次：整棵面板重新执行，让「一次性渲染」的卡脚本拿到变量
                try { if (lastPanelHtml && window.__rikkaPanelMount) window.__rikkaPanelMount(lastPanelHtml, 'remount'); } catch (_e) { /* noop */ }
                return;
            }
            if (ticks >= 20) {
                clearInterval(varsWaitTimer); varsWaitTimer = null;
                try {
                    if (window.RikkaPanel && RikkaPanel.log) {
                        let mvuState = 'n/a';
                        try { mvuState = window.__rikkaMvuStatus ? JSON.stringify(window.__rikkaMvuStatus()) : 'no-status'; } catch (_e2) { mvuState = 'ERR'; }
                        let selfId = -1;
                        try { selfId = (typeof panelSelfId === 'function') ? panelSelfId() : -1; } catch (_e3) { selfId = -1; }
                        RikkaPanel.log('debug', '[panel] vars wait: timeout (no vars) mvuStatus=' + mvuState + ' selfId=' + selfId);
                    }
                } catch (_e) { /* noop */ }
            }
        }, 400);
    };
        // [v218.1] 面板渲染完成后补发 ST 的 RENDERED 事件（见 __rikkaPanelMount 里的调用点）
        function emitRenderedEvent(tag) {
            try {
                const mid = panelSelfId();
                if (mid < 0) return;
                const chat = (window.SillyTavern && window.SillyTavern.getContext && window.SillyTavern.getContext().chat) || [];
                const msg = chat[mid];
                const isUser = !!(msg && msg.is_user);
                // 面板与主运行时是两个 realm（eventSource 各一份）→ 必须经 Kotlin 桥转到主运行时发射
                if (window.RikkaBridge && typeof RikkaBridge.emitEvent === 'function') {
                    RikkaBridge.emitEvent('panel_rendered', JSON.stringify({ messageId: mid, isUser: isUser }));
                }
                try { RikkaPanel.log('debug', '[panel] rendered id=' + mid + ' isUser=' + isUser + ' (' + tag + ')'); } catch (_e2) { /* noop */ }
            } catch (_e) { /* noop */ }
        }

    window.__rikkaPanelMount = (html, tag) => {
        try {
            if (!window.__rikkaPanelHtmlDumps) window.__rikkaPanelHtmlDumps = [];
            const dus = window.__rikkaPanelHtmlDumps;
            if (dus.length < 3) {
                const head = String(html).replace(/\s+/g, ' ').slice(0, 500);
                if (dus.indexOf(head) < 0) { dus.push(head); RikkaPanel.log('debug', '[panel] html head: ' + head); }
            }
        } catch (_e) { /* noop */ }
        const t = tag || 'mount';
        try {
            root.innerHTML = normalizeHtml(html);
            Promise.resolve()
                .then(() => runScripts(root))
                .then(() => {
                    report();
                    setTimeout(report, 120);
                    setTimeout(report, 600);
                    probe(t);
                    setTimeout(() => probe(t + '+1500'), 1500);
                    // [v218.1] recon-ext-compat §2.3：RENDERED 类事件由面板渲染完成后自发。
                    // 扩展侧（记忆增强表格 index.js:1072、ST-PT handler.ts:986/964、JSR macro_like.ts:127-128）
                    // 全靠 USER/CHARACTER_MESSAGE_RENDERED 刷新视图；此前宿主一条都不发 → 表格视图不刷新。
                    // 载荷对齐 ST：单个 message_id。
                    emitRenderedEvent(t);
                    varsWait(html);
                })
                .catch((e) => {
                    console.warn(`${LOG_PREFIX} panel scripts failed:`, e);
                    try { if (window.RikkaPanel) RikkaPanel.onPanelError(String((e && e.message) || e)); } catch (_e2) { /* noop */ }
                });
        } catch (e) {
            console.error(`${LOG_PREFIX} panel mount failed:`, e);
            try { if (window.RikkaPanel) RikkaPanel.onPanelError(String((e && e.message) || e)); } catch (_e2) { /* noop */ }
        }
    };
    // [BUGFIX B] 面板内脚本报错必须带位置与栈，否则只剩一句无信息量的 message
    const describe = (ev, kind) => {
        try {
            const msg = String((ev && (ev.message || (ev.error && ev.error.message) || ev.reason || ev.error)) || 'unknown');
            const stack = (ev && ev.error && ev.error.stack) || (ev && ev.reason && ev.reason.stack) || '';
            const where = ev && ev.filename ? ' @ ' + String(ev.filename).split('/').pop() + ':' + ev.lineno + ':' + ev.colno : '';
            const tail = stack ? ' | ' + String(stack).split('\n').slice(0, 3).join(' <- ') : '';
            return '[' + kind + '] ' + msg + where + tail;
        } catch (_e) { return '[' + kind + '] unprintable'; }
    };
    window.addEventListener('error', (ev) => {
        try { if (window.RikkaPanel && RikkaPanel.onPanelError) RikkaPanel.onPanelError(describe(ev, 'panel-error')); } catch (_e) { /* noop */ }
    });
    window.addEventListener('unhandledrejection', (ev) => {
        try { if (window.RikkaPanel && RikkaPanel.onPanelError) RikkaPanel.onPanelError(describe(ev, 'panel-rejection')); } catch (_e) { /* noop */ }
    });
    if (window.ResizeObserver) {
        try { new ResizeObserver(report).observe(document.documentElement); } catch (_e) { /* noop */ }
        try { new ResizeObserver(report).observe(document.body); } catch (_e) { /* noop */ }
        try { new ResizeObserver(report).observe(root); } catch (_e) { /* noop */ }
    }
    if (window.MutationObserver) {
        try {
            let scheduled = false;
            const mo = new MutationObserver(() => {
                if (scheduled) return;
                scheduled = true;
                requestAnimationFrame(() => { scheduled = false; report(); });
            });
            mo.observe(root, { childList: true, subtree: true, attributes: true, characterData: true });
        } catch (_e) { /* noop */ }
    }
    try { if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => report()).catch(() => {}); } catch (_e) { /* noop */ }
    window.addEventListener('load', () => { report(); setTimeout(report, 250); });
    setInterval(report, 1500);
    setTimeout(report, 100);
    // Mvu stub：与 MagVarUpdate bundle 同形（getMvuData → getVariables / events 常量表）。
    // panel 模式不加载 MVU 引擎，但面板脚本（魔法少女状态栏等）会直接引用 Mvu.*
    if (!window.Mvu) {
        const mvuEvents = {
            VARIABLE_INITIALIZED: 'mag_variable_initialized',
            VARIABLE_UPDATE_STARTED: 'mag_variable_update_started',
            COMMAND_PARSED: 'mag_command_parsed',
            VARIABLE_UPDATE_ENDED: 'mag_variable_update_ended',
            BEFORE_MESSAGE_UPDATE: 'mag_before_message_update',
            SINGLE_VARIABLE_UPDATED: 'mag_variable_updated',
        };
        window.Mvu = {
            events: mvuEvents,
            getMvuData: (option) => TavernHelper.getVariables(option),
            replaceMvuData: (data, option) => TavernHelper.replaceVariables(data, option),
            getCurrentMvuData: () => TavernHelper.getVariables({ type: 'message', message_id: TavernHelper.getCurrentMessageId() }),
            replaceCurrentMvuData: async (data) => TavernHelper.replaceVariables(data, { type: 'message', message_id: TavernHelper.getCurrentMessageId() }),
            parseMessage: async (_message, data) => data,
            reloadInitVar: async () => {},
        };
        console.log(`${LOG_PREFIX} PANEL mode: Mvu stub installed (→ TavernHelper)`);
    }
    // 处理早于 runtime 就绪的挂载请求：Kotlin 侧在 __rikkaPanelMount 未定义时会暂存 HTML，
    // 这里在全部环境（含 Mvu stub）就绪后补挂，避免“首次挂载静默丢失”。
    if (typeof window.__rikkaPanelPendingHtml === 'string' && window.__rikkaPanelPendingHtml) {
        const pendingHtml = window.__rikkaPanelPendingHtml;
        window.__rikkaPanelPendingHtml = null;
        try { window.__rikkaPanelMount(pendingHtml); } catch (_e) { /* noop */ }
    }
        // [批次十三·诊断] 卡脚本在面板里到底向变量系统要了什么、又拿到了什么。
    // 只记前 24 次，避免刷爆日志（tavern-runtime.log 上限 256KB）。
    (function wrapPanelVarApi() {
        try {
            if (window.__rikkaPanelVarLog) return;
            window.__rikkaPanelVarLog = { n: 0 };
            const CAP = 24;
            const note = (label, opt, res) => {
                const st = window.__rikkaPanelVarLog;
                if (!st || st.n >= CAP) return;
                st.n++;
                let keys = '?', stat = '';
                try { keys = (res && typeof res === 'object') ? Object.keys(res).slice(0, 10).join(',') : String(res); } catch (_e) { /* noop */ }
                try { stat = (res && res.stat_data && typeof res.stat_data === 'object') ? ' stat=[' + Object.keys(res.stat_data).slice(0, 10).join(',') + ']' : ''; } catch (_e) { /* noop */ }
                try { RikkaPanel.log('debug', '[panel] varreq ' + label + ' ' + JSON.stringify(opt) + ' key=' + String(window.__rikkaPanelMessageKey) + ' selfId=' + panelSelfId() + ' → keys=[' + keys + ']' + stat); } catch (_e) { /* noop */ }
            };
            if (window.Mvu) {
                ['getMvuData', 'getCurrentMvuData'].forEach((fn) => {
                    const orig = window.Mvu[fn];
                    if (typeof orig !== 'function') return;
                    window.Mvu[fn] = function (opt) {
                        const res = orig.apply(this, arguments);
                        try { note('Mvu.' + fn, opt, res); } catch (_e) { /* noop */ }
                        return res;
                    };
                });
            }
            const th = (typeof TavernHelper !== 'undefined') ? TavernHelper : window.TavernHelper;
            if (th && typeof th.getVariables === 'function' && !th.__rikkaWrapped) {
                const origTh = th.getVariables;
                th.getVariables = function (opt) {
                    const res = origTh.apply(this, arguments);
                    try { note('TavernHelper.getVariables', opt, res); } catch (_e) { /* noop */ }
                    return res;
                };
                th.__rikkaWrapped = true;
            }
            RikkaPanel.log('debug', '[panel] var-API wrapped (Mvu=' + !!window.Mvu + ' TavernHelper=' + !!th + ')');
        } catch (e) {
            try { RikkaPanel.log('debug', '[panel] var-API wrap failed: ' + e); } catch (_e) { /* noop */ }
        }
    })();
    console.log(`${LOG_PREFIX} PANEL mode ready (mount API: __rikkaPanelMount)`);
}

// ---------------------------------------------------------------- 启动
(async function boot() {
    // [v197] 扩展设置页模式：只装载第三方扩展自身的设置 UI，不加载卡脚本 / MVU。
    if (window.__RIKKA_EXT_SETTINGS_MODE__) {
        console.log(`${LOG_PREFIX} booting in EXT SETTINGS mode ...`);
        // [v197 hotfix] 设置页可见诊断状态：即使扩展注入失败也不至于纯黑。
        try {
            const statusEl = document.getElementById('rikka-ext-settings-status');
            if (statusEl) {
                statusEl.style.display = 'block';
                statusEl.textContent = `正在加载扩展设置：${new URLSearchParams(location.search).get('ext') || '(未指定扩展)'} …`;
            }
        } catch (_e) { /* noop */ }

        if (typeof $ === 'undefined') safeWarn('jQuery missing! shims may break.');
        refreshAll();
        emitToHost('ext_settings_ready', { version: '1.0.0' });
        const t0 = Date.now();
        try {
            await loadThirdPartyExtensions(false);
        } catch (e) {
            const msg = (e && (e.stack || e.message)) || String(e);
            try { callBridge("log", "error", `[ext-settings] load failed: ${msg}`); } catch (_e) { /* noop */ }
            const statusEl = document.getElementById("rikka-ext-settings-status");
            if (statusEl) {
                statusEl.setAttribute("data-error", "true");
                statusEl.textContent = `扩展加载失败：${msg}`;
            }
        }
        console.log(`${LOG_PREFIX} ext settings phase done in ${Date.now() - t0}ms`);
        try {
            const extFolder = new URLSearchParams(location.search).get('ext') || '';
            emitToHost('ext_settings_loaded', { folder: extFolder });
        } catch (_e) { /* noop */ }
        // [v197 hotfix] 加载完成后更新状态：有 UI 则收起提示，没有 UI 则明确告诉用户。
        try {
            const statusEl = document.getElementById('rikka-ext-settings-status');
            const uiCount = (id) => {
                const el = document.getElementById(id);
                return el && el.children ? el.children.length : 0;
            };
            const hasUi = (
                uiCount('extensions_settings') +
                uiCount('extensions_settings2') +
                uiCount('translation_container') +
                uiCount('extensionsMenu') +
                uiCount('extensions_menu') +
                uiCount('extensions-settings-button')
            ) > 0;
            if (statusEl) {
                if (hasUi) {
                    statusEl.textContent = '扩展设置已加载';
                    setTimeout(() => { statusEl.style.display = 'none'; }, 500);
                } else {
                    statusEl.textContent = '扩展已加载，但没有检测到设置界面。若该扩展只提供运行时功能，这是正常的；否则请把日志发给 AI。';
                }
            }
        } catch (_e) { /* noop */ }
        setTimeout(() => {
            try {
                const statusEl = document.getElementById('rikka-ext-settings-status');
                const uiCount = (id) => {
                    const el = document.getElementById(id);
                    return el && el.children ? el.children.length : 0;
                };
                const hasUi = (
                    uiCount('extensions_settings') +
                    uiCount('extensions_settings2') +
                    uiCount('translation_container') +
                    uiCount('extensionsMenu') +
                    uiCount('extensions_menu') +
                    uiCount('extensions-settings-button')
                ) > 0;
                if (statusEl && hasUi) statusEl.style.display = 'none';
            } catch (_e) { /* noop */ }
        }, 1500);
        return;
    }
    // Panel 模式（C4）：消息内嵌渲染宿主 —— 不加载卡脚本 / MVU
    if (window.__RIKKA_PANEL_MODE__) {
        console.log(`${LOG_PREFIX} booting in PANEL mode ...`);
        if (typeof $ === 'undefined') safeWarn('jQuery missing! shims may break.');
        refreshAll();
        emitToHost('panel_ready', { version: '1.1.0' });
        installPanelMode();
        return;
    }
    console.log(`${LOG_PREFIX} booting ...`);
    // DOM / 全局就绪
    if (typeof $ === 'undefined') {
        safeWarn('jQuery missing! shims may break.');
    }
    ensureTavernHelperDom(['mvu-standalone']);
    // v206：JSR 会在 ready 回调里另建一个同名容器（文档序在我方之前）→ 短期轮询把身份 div 补进去
    keepTavernHelperDom(['mvu-standalone'], 30000);
    refreshAll();
    emitToHost('script_ready', { version: '1.1.0' });
    console.log(`${LOG_PREFIX} shims installed: ${Object.keys(TavernHelper).length} keys`);
    // 先装载卡脚本（可能自带 MVU 加载器），再决定 MVU 来源
    try { await loadTavernScripts(); } catch (_e) { /* noop */ }
    console.log(`${LOG_PREFIX} card scripts mounted: mvuFromScripts=${mvuFromScripts}, window.Mvu=${!!window.Mvu}`);
    // [BUGFIX B] MVU 装载不再排在第三方扩展装载之后。
    // 真机证据：injectThirdPartyModule 单模块最长等 15s（setTimeout(done, 15000)），3 个扩展
    // 最坏要 45s 才返回，而卡内 MVU 引擎脚本只重试 10×2s＝20s 就放弃并报「未检测到MVU框架」，
    // 变量永不初始化 → 状态栏/监控面板拿不到数据只能渲染空壳。MVU 必须尽早、与扩展并行启动。
    setTimeout(() => { loadMvu(); }, 1200);
    // 第三方扩展（ST third-party）：与卡脚本独立，装载完成后广播 rikka-st-refresh
    const extT0 = Date.now();
    try { await loadThirdPartyExtensions(false); } catch (_e) { /* noop */ }
    console.log(`${LOG_PREFIX} third-party extension phase done in ${Date.now() - extT0}ms (window.Mvu=${!!window.Mvu})`);
    // 少数扩展自带 MVU：晚到时补一次 ready 广播，避免宿主一直等 mvu_ready
    if (window.Mvu && !mvuReady) {
        mvuReady = true;
        console.log(`${LOG_PREFIX} MVU became available from third-party extension`);
        emitToHost('mvu_ready', { source: 'extension', version: String(window.Mvu && window.Mvu.version || '') });
        setTimeout(() => notifyChatReady(), 150);
    }
})();

// 页面卸载前冲刷变量
window.addEventListener('pagehide', () => { try { flushAll(); } catch (_e) { /* noop */ } });
