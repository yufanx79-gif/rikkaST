/**
 * rikkaST st-compat: script.js
 * ============================================================
 * 模拟 SillyTavern `public/script.js` 的导出面（第三方扩展 import 契约）。
 *
 * 设计（对齐 TauriTavern "资源端点 + API 表面" 哲学）：
 * - 数据类导出（chat / characters / name1 / ...）使用 `export let` + live binding：
 *   runtime 每次 refreshAll() 后广播 'rikka-st-refresh'，本模块重新快照 → 引用方始终读到最新值；
 * - 函数类导出为"转发器"：调用宿主 getContext() 上的同名实现，缺失时降级为安全默认值；
 * - 纯 DOM / 后端行为（消息节点重绘、生图等）在 P0 为 no-op（不抛错），P1 再逐步接线。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});
const host = () => (window.__rikkaSt || {});

// ---------------------------------------------------------------- live 会话数据
export let chat = [];
export let characters = [];
export let name1 = 'User';
export let name2 = 'AI';
export let this_chid = 0;
export let chat_metadata = {};
export let extension_prompts = {};
export let extension_settings = {};
export let user_avatar = '';
export let online_status = 'no_connection';
export let is_send_press = false;
export let isChatSaving = false;
export let main_api = 'openai';
export let settings = {};
export let amount_gen = 512;
export let max_context = 32768;
export let selected_group = null;
export let groups = [];

function __refresh() {
    try {
        const c = st();
        if (Array.isArray(c.chat)) chat = c.chat;
        if (Array.isArray(c.characters)) characters = c.characters;
        if (typeof c.name1 === 'string' && c.name1) name1 = c.name1;
        if (typeof c.name2 === 'string' && c.name2) name2 = c.name2;
        this_chid = (typeof c.characterId === 'number') ? c.characterId : 0;
        if (c.chatMetadata && typeof c.chatMetadata === 'object') chat_metadata = c.chatMetadata;
        if (c.extensionSettings && typeof c.extensionSettings === 'object') extension_settings = c.extensionSettings;
    } catch (_e) { /* noop */ }
}
__refresh();
try {
    window.addEventListener('rikka-st-refresh', () => { try { __refresh(); } catch (_e) { /* noop */ } });
} catch (_e) { /* noop */ }

// ---------------------------------------------------------------- 事件总线（与 runtime 同实例）
const ES = st().eventSource || host().eventSource;
export const eventSource = ES || {
    on() {}, once() {}, off() {}, emit() {}, emitAndWait() {},
    addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {},
};
export const event_types = st().eventTypes || st().event_types || {};

// ---------------------------------------------------------------- 常量（对齐 ST1.18 popup/script）
export const extension_prompt_types = { IN_PROMPT: 0, IN_CHAT: 1, BEFORE_PROMPT: 2 };
export const extension_prompt_roles = { SYSTEM: 0, USER: 1, ASSISTANT: 2 };
export const MAX_INJECTION_DEPTH = 10000;
export const depth_prompt_depth_default = 4;
export const depth_prompt_role_default = 'system';
export const default_avatar = 'img/ai4.png';
export const system_avatar = 'img/five.png';
export const comment_avatar = 'img/quill.png';
export const default_user_avatar = 'img/user-default.png';
export const systemUserName = 'SillyTavern System';
export const neutralCharacterName = 'Assistant';
export const system_message_types = { ASSISTANT: 'assistant', USER: 'user', SYSTEM: 'system' };

// ---------------------------------------------------------------- 函数转发器
function fwd(path, fallback) {
    return (...args) => {
        try {
            const c = st();
            const target = String(path).split('.').reduce((o, k) => (o == null ? o : o[k]), c);
            if (typeof target === 'function') return target.apply(c, args);
        } catch (_e) { /* noop */ }
        return typeof fallback === 'function' ? fallback(...args) : fallback;
    };
}

export const getRequestHeaders = fwd('getRequestHeaders', () => ({ 'Content-Type': 'application/json' }));
export const getCurrentChatId = fwd('getCurrentChatId', () => String(st().chatId || ''));
export const substituteParams = fwd('substituteParams', (t) => t);
export const substituteParamsExtended = fwd('substituteParamsExtended', (t) => t);
export const getCharacterCardFields = fwd('getCharacterCardFields', () => ({
    name: '', description: '', personality: '', scenario: '', first_mes: '', mes_example: '',
}));
export const reloadCurrentChat = fwd('reloadCurrentChat', () => Promise.resolve());
export const reloadMarkdownProcessor = fwd('reloadMarkdownProcessor', () => {});
export const saveSettingsDebounced = fwd('saveSettingsDebounced', () => {});
export const saveSettings = saveSettingsDebounced;
export const saveMetadataDebounced = fwd('saveMetadataDebounced', () => {});
export const saveMetadata = saveMetadataDebounced;
export const saveChat = fwd('saveChat', () => {});
export const saveChatConditional = () => {
    try {
        const f = st().saveChat;
        if (typeof f === 'function') return Promise.resolve(f());
    } catch (_e) { /* noop */ }
    return Promise.resolve();
};
export const cancelDebouncedChatSave = () => {};
export const generate = fwd('generate', () => Promise.resolve(''));
export const generateRaw = fwd('generateRaw', () => Promise.resolve(''));
export const Generate = generate;
export const stopGeneration = fwd('stopGeneration', () => {});
export const clearChat = fwd('clearChat', () => {});

// ---------------------------------------------------------------- 扩展提示（P0 本地记录）
export function setExtensionPrompt(key, value, position, depth, scan, role) {
    try {
        extension_prompts[String(key || '')] = { value, position, depth, scan, role };
    } catch (_e) { /* noop */ }
}
export function getExtensionPromptByName(name) {
    const p = extension_prompts[name];
    return p ? p.value : '';
}
export function getExtensionPromptRoleByName(name) {
    const p = extension_prompts[name];
    return p ? p.role : extension_prompt_roles.SYSTEM;
}

// ---------------------------------------------------------------- 角色 / 消息读取
export const getCharacters = () => Promise.resolve(characters || []);
export function getOneCharacter(avatar) {
    const list = characters || [];
    return list.find((c) => c && (c.avatar === avatar || c.name === avatar)) || list[0] || {};
}
export const getThumbnailUrl = () => '';
export const getPastCharacterChats = () => Promise.resolve([]);
export const getUserAvatar = () => user_avatar || '';
export const getMaxContextSize = () => max_context;
export const isGenerating = () => !!is_send_press;
export const isOdd = (n) => (Number(n) % 2) !== 0;
export function countOccurrences(str, needle) {
    try {
        if (!needle) return 0;
        return String(str).split(String(needle)).length - 1;
    } catch (_e) { return 0; }
}

// ---------------------------------------------------------------- 显示 / DOM 类（P0 no-op）
export function messageFormatting(mes) { return String(mes == null ? '' : mes); }
export const updateMessageBlock = fwd('updateMessageBlock', () => {});
export const addOneMessage = () => {};
export const printMessages = () => {};
export const printCharacters = () => {};
export const scrollChatToBottom = () => {};
export const deleteLastMessage = () => {};
export const syncMesToSwipe = () => {};
export const activateSendButtons = () => {};
export const deactivateSendButtons = () => {};
export const showSwipeButtons = () => {};
export const setGenerationProgress = () => {};
export const setUserName = () => {};
export const select_selected_character = () => {};
export const selectCharacterById = () => Promise.resolve();
export const deleteCharacter = () => Promise.resolve();
export const unshallowCharacter = () => Promise.resolve();
export const saveCharacterDebounced = () => {};
export const parseMesExamples = (text) => text;
export const baseChatReplace = (text) => text;
export const cleanUpMessage = (mes) => String(mes == null ? '' : mes);
export const getBiasStrings = () => ({});
export const appendMediaToMessage = fwd('appendMediaToMessage', () => {});
export const addCopyToCodeBlocks = fwd('addCopyToCodeBlocks', () => {});
export const nai_settings = {};
export const GenerateOptions = {};

// ---------------------------------------------------------------- 兼容测试钩子（部分第三方扩展/工具链 import）
export function __setChatMetadata(meta) {
    try {
        chat_metadata = (meta && typeof meta === 'object') ? meta : {};
    } catch (_e) { /* noop */ }
}

// ── [batch16] getSlideToggleOptions ──────────────────────────────
// 依据：refdeps\st-memory-enhancement\core\manager.js:328 —— `getSlideToggleOptions: APP.getSlideToggleOptions`
//   （APP 来自 `import * as APP from '/script.js'`，缺名字会让该 import 整条失败）
// ST 语义：reduce_motion 时动画时长 0；否则默认。
export function getSlideToggleOptions() {
    try {
        const pu = (window.__rikkaSt && window.__rikkaSt.powerUser) || null;
        if (pu && pu.reduce_motion) return { duration: 0 };
    } catch (err) { /* noop */ }
    return {};
}
