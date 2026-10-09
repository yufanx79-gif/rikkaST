/**
 * rikkaST st-compat: world-info.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/world-info.js` 的导出面。
 * 读路径（loadWorldInfo）转发到宿主（卡内嵌世界书）；写路径 P0 为 no-op。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

// ---------------------------------------------------------------- 数据（live）
// [v208] ST 契约（world-info.js:65-68）：
//   world_info 是【对象】{ globalSelect: string[], charLore: [...] }，不是数组；
//   world_names / selected_world_info 是 string[]。
// 此前三者恒为空/形态错 → ST-PT worldinfo.ts:660 把所有书过滤光（WI 功能静默全灭）、
//   JSR lorebook.ts:88 读 world_info_settings.world_info.globalSelect 直接 TypeError。
export let world_info = { globalSelect: [], charLore: [] };
export let world_names = [];
export let selected_world_info = [];

function __refreshWorldInfoData() {
    const c = st();
    // selected_world_info：优先取 ctx 的 selected_world_info，其次 world_info.globalSelect
    if (Array.isArray(c.selected_world_info) && c.selected_world_info.length) {
        selected_world_info = c.selected_world_info;
    } else if (c.world_info && Array.isArray(c.world_info.globalSelect)) {
        selected_world_info = c.world_info.globalSelect;
    }
    if (c.world_info && typeof c.world_info === 'object' && !Array.isArray(c.world_info)) {
        world_info = c.world_info;
        if (!Array.isArray(world_info.globalSelect)) world_info.globalSelect = selected_world_info.slice();
        if (!Array.isArray(world_info.charLore)) world_info.charLore = [];
    }
    if (Array.isArray(c.world_names)) world_names = c.world_names;
}

try {
    window.addEventListener('rikka-st-refresh', () => { try { __refreshWorldInfoData(); } catch (_e) { /* noop */ } });
    __refreshWorldInfoData();
} catch (_e) { /* noop */ }

// ---------------------------------------------------------------- 常量（对齐 ST1.18）
// [v208] 对齐 ST 真源（world-info.js:33-38）：{AND_ANY:0, NOT_ALL:1, NOT_ANY:2, AND_ALL:3}。
// 此前 1/2/3 互换 → ST-PT worldinfo.ts:563-567 的 switch 把 NOT_ALL 走进 AND_ALL 分支，激活条件相反。
// 与宿主 TavernCard.kt:77 的官方注释（0=AND_ANY 1=NOT_ALL 2=NOT_ANY 3=AND_ALL）也终于一致。
export const world_info_logic = { AND_ANY: 0, NOT_ALL: 1, NOT_ANY: 2, AND_ALL: 3 };
export const world_info_position = {
    before: 0,
    after: 1,
    ANTop: 2,
    ANBottom: 3,
    atDepth: 4,
    EMTop: 5,
    EMBottom: 6,
    // [v208] ST world-info.js:855-864 有 outlet:7（JSR replay.ts:101 的 outlet 分桶靠它）
    outlet: 7,
};

// [v208] ST world-info.js:27-31（此前缺失）
export const world_info_insertion_strategy = { evenly: 0, character_first: 1, global_first: 2 };

// [v208] ST world-info.js:98（此前缺失）
export const MAX_SCAN_DEPTH = 1000;
export const wi_anchor_position = { before: 0, after: 1 };
export const DEFAULT_DEPTH = 4;
export const DEFAULT_WEIGHT = 100;
export const METADATA_KEY = 'world_info';

// ---------------------------------------------------------------- 设置（静态近似）
export const world_info_include_names = true;
export const world_info_max_recursion_steps = 0;
export const world_info_case_sensitive = false;
export const world_info_match_whole_words = false;
export const world_info_use_group_scoring = false;

export function getWorldInfoSettings() {
    // [v208] 对齐 ST getWorldInfoSettings（world-info.js:795-812，14 键）：
    //  ① 补 world_info（对象本体，含 globalSelect/charLore）与 world_info_budget_cap —— 缺它们
    //     JSR lorebook.ts:88 `(settings.world_info as {globalSelect}).globalSelect` 直接 TypeError（P0-2）；
    //  ② world_info_depth 用 ST 全局默认 2（ST 69），不是 entry 的 DEFAULT_DEPTH(4)
    //     （JSR lorebook.ts:90 拿它当 scan_depth，此前一直是 4，P1-5）。
    return {
        world_info,
        world_info_budget_cap: 0,
        world_info_depth: 2,
        world_info_budget: 25,
        world_info_recursive: false,
        world_info_min_activations: 0,
        world_info_min_activations_depth_max: 0,
        world_info_overflow_alert: false,
        // 其余键名与 ST 一致：budget/recursive/include_names/case_sensitive/match_whole_words/
        //   character_strategy/use_group_scoring/max_recursion_steps（见下）
        world_info_case_sensitive: false,
        world_info_match_whole_words: false,
        world_info_include_names: true,
        world_info_use_group_scoring: false,
        world_info_character_strategy: 1,
        world_info_max_recursion_steps: 0,
    };
}

// ---------------------------------------------------------------- 读 / 写
export async function loadWorldInfo(name) {
    // [v208] P0-1：对齐 ST loadWorldInfo（world-info.js:2036-2058）—— 未命中返回 null/undefined（falsy）。
    // 此前返回 {}（真值）：ST-PT worldinfo.ts:216-222 先 `if (!lorebook) return []` 再
    // `Object.values(lorebook.entries)`，把 {} 当有效书 → 对 undefined.entries 取 values → TypeError。
    if (!name) return null;
    let r = null;
    try {
        const f = st().loadWorldInfo;
        if (typeof f === 'function') r = await f(name);
    } catch (_e) { r = null; }
    // 宿主未命中已返回 JSON null；再兜一层：非对象 / 无 entries 都算「无此书」
    if (!r || typeof r !== 'object' || !r.entries || typeof r.entries !== 'object') return null;
    return r;
}

export async function saveWorldInfo() {}
export async function updateWorldInfoList() {}
export async function deleteWorldInfo() {}
export async function createNewWorldInfo(name) { return { name: name || '', entries: {} }; }
export function createWorldInfoEntry() { return {}; }
export function deleteWorldInfoEntry() {}
export function setWIOriginalDataValue() {}
export function reloadEditor() {}
export function setWorldInfoButtonClass() {}

export function convertCharacterBook(book) {
    // [v208] P2-3：对齐 ST convertCharacterBook（world-info.js:5617-5674）——
    // entries 转成 uid→entry 对象，每条套用 newWorldInfoEntryTemplate 默认值，并带 originalData。
    // 此前原样透传卡规格字段（keys/secondary_keys），JSR import_raw.ts:46 的角色书导入整体无效。
    try {
        if (!book || typeof book !== 'object') return { entries: {}, originalData: book };
        const list = Array.isArray(book.entries) ? book.entries : Object.values(book.entries || {});
        const entries = {};
        list.forEach((entry) => {
            if (!entry || typeof entry !== 'object') return;
            const uid = Number(entry.id != null ? entry.id : entry.uid);
            const base = JSON.parse(JSON.stringify(newWorldInfoEntryTemplate));
            base.uid = uid;
            base.key = Array.isArray(entry.keys) ? entry.keys.slice() : (Array.isArray(entry.key) ? entry.key.slice() : []);
            base.keysecondary = Array.isArray(entry.secondary_keys) ? entry.secondary_keys.slice()
                : (Array.isArray(entry.keysecondary) ? entry.keysecondary.slice() : []);
            base.comment = String(entry.comment == null ? '' : entry.comment);
            base.content = String(entry.content == null ? '' : entry.content);
            base.constant = entry.constant === true;
            base.selective = entry.selective !== false;
            base.selectiveLogic = Number(entry.selective_logic != null ? entry.selective_logic : entry.selectiveLogic) || 0;
            base.addMemo = entry.addMemo === true || (typeof entry.comment === 'string' && entry.comment.length > 0);
            base.order = Number(entry.insertion_order != null ? entry.insertion_order : entry.order) || 0;
            base.position = Number(entry.position != null ? entry.position : 0) || 0;
            base.disable = entry.enabled === false;
            base.excludeRecursion = entry.exclude_recursion === true;
            base.preventRecursion = entry.prevent_recursion === true;
            base.delayUntilRecursion = Number(entry.extensions?.delay_until_recursion ?? entry.delay_until_recursion) || 0;
            base.probability = Number(entry.extensions?.probability ?? entry.probability) || 100;
            base.useProbability = entry.extensions?.use_probability !== false;
            base.depth = Number(entry.extensions?.depth ?? entry.depth) || DEFAULT_DEPTH;
            base.group = String(entry.group ?? '');
            base.groupOverride = entry.group_override === true;
            base.groupWeight = Number(entry.extensions?.group_weight ?? entry.group_weight) || DEFAULT_WEIGHT;
            base.scanDepth = entry.extensions?.scan_depth != null ? Number(entry.extensions.scan_depth) : null;
            base.caseSensitive = entry.extensions?.case_sensitive != null ? entry.extensions.case_sensitive === true : null;
            base.matchWholeWords = entry.extensions?.match_whole_words != null ? entry.extensions.match_whole_words === true : null;
            base.useGroupScoring = entry.extensions?.use_group_scoring != null ? entry.extensions.use_group_scoring === true : null;
            base.automationId = String(entry.extensions?.automation_id ?? entry.automation_id ?? '');
            base.role = roleToStNumber(entry.extensions?.role ?? entry.role);
            base.outletName = String(entry.extensions?.outlet_name ?? entry.outlet_name ?? '');
            base.triggers = Array.isArray(entry.triggers) ? entry.triggers.slice() : [];
            base.world = String(book.name == null ? '' : book.name);
            entries[String(uid)] = base;
        });
        return { entries, originalData: book };
    } catch (_e) {
        return { entries: {}, originalData: book };
    }
}

/** [v208] ST extension_prompt_roles（0/1/2）—— convertCharacterBook 用。 */
function roleToStNumber(role) {
    const r = String(role == null ? '' : role).toLowerCase();
    if (r === 'user' || r === '1') return 1;
    if (r === 'assistant' || r === '2') return 2;
    return 0;
}

export function parseRegexFromString(str) {
    // [v208] P2-2：对齐 ST parseRegexFromString（world-info.js:2901-2926）—— 只认 /pattern/flags 字面量。
    // 此前对任意字符串 new RegExp → 含 . + ? 等元字符的普通关键词被误当正则
    // （ST-PT worldinfo.ts:505-508 的语义是「先试正则，不是正则格式就走普通字符串匹配」）。
    if (typeof str !== 'string') return null;
    const m = str.match(/^\/(.+)\/([gimsuy]*)$/s);
    if (!m) return null;
    try {
        return new RegExp(m[1], m[2]);
    } catch (_e) {
        return null;
    }
}

// ---------------------------------------------------------------- 扫描 / 注入（P0 宿主不在 JS 侧处理）
export async function getWorldInfoPrompt() {
    // [v208] P2-4：对齐 ST getWorldInfoPrompt（world-info.js:892-914）的 8 键形态
    // （JSR dataProcessor.ts:315-317 目前只解构 5 个，补 anBefore/anAfter/outletEntries 防将来解构炸）。
    return {
        worldInfoString: '', worldInfoBefore: '', worldInfoAfter: '',
        worldInfoExamples: [], worldInfoDepth: [],
        anBefore: '', anAfter: '', outletEntries: [],
    };
}

export async function checkWorldInfo() {
    return { found: false, worldInfoString: '', worldInfoBefore: '', worldInfoAfter: '' };
}

export async function charUpdatePrimaryWorld() {}

// ── [batch16] newWorldInfoEntryTemplate ──────────────────────────
// 依据：JS-Slash-Runner 的 dist bundle 具名 import；缺失时真机日志为
//   `SyntaxError: The requested module '../../../../../scripts/world-info.js' does not provide an export named 'newWorldInfoEntryTemplate'`
// 语义对齐 ST：新建世界书条目的默认模板（uid 归 0，由调用方回填）。
// [v208] P1-7：对齐 ST newWorldInfoEntryDefinition（world-info.js:4082-4125，去掉 excludeFromTemplate
// 的 characterFilterNames/Tags/Exclude 后共 39 键）。此前少 9 键、多 uid/displayIndex、
// role/delayUntilRecursion 默认值错 —— JSR compatibility.ts:46 以它为底模铺字段，缺的键就是 undefined。
export const newWorldInfoEntryTemplate = {
    key: [],
    keysecondary: [],
    comment: '',
    content: '',
    constant: false,
    vectorized: false,
    selective: true,
    selectiveLogic: world_info_logic.AND_ANY,
    addMemo: false,
    order: 100,
    position: world_info_position.before,
    disable: false,
    excludeRecursion: false,
    preventRecursion: false,
    delayUntilRecursion: false,
    probability: 100,
    useProbability: true,
    depth: DEFAULT_DEPTH,
    ignoreBudget: false,
    outletName: '',
    group: '',
    groupOverride: false,
    groupWeight: DEFAULT_WEIGHT,
    scanDepth: null,
    caseSensitive: null,
    matchWholeWords: null,
    useGroupScoring: null,
    automationId: '',
    matchPersonaDescription: false,
    matchCharacterDescription: false,
    matchCharacterPersonality: false,
    matchCharacterDepthPrompt: false,
    matchScenario: false,
    matchCreatorNotes: false,
    // ST 模板里 delayUntilRecursion 默认是数字 0（4104）、role 是 0（4117）；sticky/cooldown/delay 是 null
    delayUntilRecursion: 0,
    role: 0,
    sticky: null,
    cooldown: null,
    delay: null,
    triggers: [],
};
