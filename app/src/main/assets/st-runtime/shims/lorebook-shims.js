/**
 * MVU Standalone — 世界书 Shim
 * 包装 SillyTavern 的 world info API
 */

import { deepClone } from './clone-util.js';

function getCtx() {
    return window.SillyTavern.getContext();
}

/** 宿主桥（RikkaBridge）调用；不存在/异常时返回 undefined。 */
function callBridge(name, ...args) {
    try {
        const b = window.RikkaBridge;
        if (!b || typeof b[name] !== 'function') return undefined;
        return b[name](...args);
    } catch (_e) {
        return undefined;
    }
}

// [v214] 世界书开关的内存 overlay：bookName → Map<uid, disable>
// 用途：卡（如千纱-DS鲸鱼版）经 updateWorldbookWith 切换 enabled 后，宿主持久化是异步的；
// 这里保证同一会话内后续 getWorldbook / getLorebookEntries 立即读到新状态（重启后由宿主存储兜底）。
const wbDisableOverlay = new Map();

function overlayBookEntries(name, entries) {
    const ov = wbDisableOverlay.get(String(name));
    if (!ov || ov.size === 0) return entries;
    return entries.map((e) => (ov.has(Number(e.uid)) ? { ...e, enabled: !ov.get(Number(e.uid)) } : e));
}

// position 枚举映射 (ST number → JSR string)
const POSITION_MAP = {
    0: 'before_character_definition',
    1: 'after_character_definition',
    2: 'before_authors_note',
    3: 'after_authors_note',
    4: 'at_depth',
    5: 'before_example_messages',
    6: 'after_example_messages',
    7: 'outlet',
};

const POSITION_REVERSE_MAP = Object.fromEntries(
    Object.entries(POSITION_MAP).map(([k, v]) => [v, Number(k)])
);

// selectiveLogic 枚举映射
const LOGIC_MAP = {
    0: 'and_any',
    1: 'not_all',
    2: 'not_any',
    3: 'and_all',
};

// role 枚举映射
const ROLE_MAP = {
    0: 'system',
    1: 'user',
    2: 'assistant',
};

/**
 * ST 扁平条目 → JSR 结构化条目
 */
function toLorebookEntry(uid, entry) {
    const strategyType = entry.constant ? 'constant'
        : entry.vectorized ? 'vectorized'
        : 'selective';

    return {
        uid: uid,
        name: entry.comment ?? '',
        content: entry.content ?? '',
        enabled: !entry.disable,
        strategy: {
            type: strategyType,
            keys: (entry.key ?? []).map(k => {
                if (k.startsWith('/') && k.endsWith('/')) {
                    try { return new RegExp(k.slice(1, -1)); } catch { return k; }
                }
                return k;
            }),
            keys_secondary: {
                logic: LOGIC_MAP[entry.selectiveLogic] ?? 'and_any',
                keys: (entry.keysecondary ?? []).map(k => {
                    if (k.startsWith('/') && k.endsWith('/')) {
                        try { return new RegExp(k.slice(1, -1)); } catch { return k; }
                    }
                    return k;
                }),
            },
            scan_depth: entry.scanDepth === null ? 'same_as_global' : entry.scanDepth,
        },
        position: {
            type: POSITION_MAP[entry.position] ?? 'before_character_definition',
            role: ROLE_MAP[entry.role] ?? 'system',
            depth: entry.depth ?? 4,
            order: entry.order ?? 100,
        },
        probability: entry.probability ?? 100,
        recursion: {
            prevent_incoming: entry.excludeRecursion ?? false,
            prevent_outgoing: entry.preventRecursion ?? false,
            delay_until: entry.delayUntilRecursion ?? 0,
        },
        effect: {
            sticky: entry.sticky ?? null,
            cooldown: entry.cooldown ?? null,
            delay: entry.delay ?? null,
        },
    };
}

export async function getLorebookEntries(lorebook, option = {}) {
    const ctx = getCtx();

    try {
        // 使用 SillyTavern 的 loadWorldInfo
        const data = await ctx.loadWorldInfo(lorebook);
        if (!data || !data.entries) return [];

        let entries = Object.entries(data.entries).map(([uid, entry]) =>
            toLorebookEntry(Number(uid), entry)
        );

        // 过滤
        if (option.filter && option.filter !== 'none') {
            entries = entries.filter(entry => {
                for (const [key, value] of Object.entries(option.filter)) {
                    if (entry[key] !== value) return false;
                }
                return true;
            });
        }

        return deepClone(overlayBookEntries(lorebook, entries));
    } catch (error) {
        console.error('[MVU-Standalone] getLorebookEntries failed:', error);
        return [];
    }
}

export function getCurrentCharPrimaryLorebook() {
    const ctx = getCtx();
    try {
        const charId = ctx.characterId;
        if (charId === undefined || charId === null) return null;
        const char = ctx.characters?.[charId];
        if (!char) return null;
        return char.data?.extensions?.world || null;
    } catch {
        return null;
    }
}

export function getLorebookSettings() {
    try {
        // 读取世界书全局设置
        // SillyTavern 在 world-info.js 中维护全局设置变量
        // 通过 jQuery 读取 DOM 元素的值（与 JSR 兼容）
        return deepClone({
            selected_global_lorebooks: [],
            scan_depth: Number($('#world_info_depth').val()) || 2,
            context_percentage: Number($('#world_info_budget').val()) || 25,
            budget_cap: Number($('#world_info_budget_cap').val()) || 0,
            min_activations: Number($('#world_info_min_activations').val()) || 0,
            max_depth: Number($('#world_info_max_depth').val()) || 0,
            max_recursion_steps: Number($('#world_info_max_recursion_steps').val()) || 0,
            include_names: $('#world_info_include_names').is(':checked'),
            recursive: $('#world_info_recursive').is(':checked'),
            case_sensitive: $('#world_info_case_sensitive').is(':checked'),
            match_whole_words: $('#world_info_match_whole_words').is(':checked'),
            use_group_scoring: $('#world_info_use_group_scoring').is(':checked'),
            overflow_alert: $('#world_info_overflow_alert').is(':checked'),
        });
    } catch (e) {
        console.warn('[MVU-Standalone] getLorebookSettings fallback:', e);
        return {
            scan_depth: 2,
            context_percentage: 25,
            budget_cap: 0,
            min_activations: 0,
            max_depth: 0,
            max_recursion_steps: 0,
            include_names: false,
            recursive: true,
            case_sensitive: false,
            match_whole_words: false,
            use_group_scoring: false,
            overflow_alert: false,
        };
    }
}

export function setLorebookSettings(settings) {
    // 设置世界书全局参数 (通过 jQuery 操作 DOM)
    if (settings.scan_depth !== undefined) {
        $('#world_info_depth').val(settings.scan_depth).trigger('input');
    }
    if (settings.context_percentage !== undefined) {
        $('#world_info_budget').val(settings.context_percentage).trigger('input');
    }
    if (settings.budget_cap !== undefined) {
        $('#world_info_budget_cap').val(settings.budget_cap).trigger('input');
    }
    if (settings.recursive !== undefined) {
        $('#world_info_recursive').prop('checked', settings.recursive).trigger('change');
    }
    if (settings.case_sensitive !== undefined) {
        $('#world_info_case_sensitive').prop('checked', settings.case_sensitive).trigger('change');
    }
}

// ---------------------------------------------------------------- JSR 4.x 世界书兼容 API（MVU 初始化依赖）
/**
 * 角色卡绑定的世界书名。语义对齐 JS-Slash-Runner：
 * 返回 { primary: string|null, additional: string[] }；
 * 'current'（或无角色）返回空结构；显式指定名字未找到则抛错（与 JSR 行为一致）。
 * 注：rikkaST 的角色卡/角色世界书存储尚未接入，additional 暂为空。
 */
export function getCharLorebooks({ name = 'current' } = {}) {
    const books = { primary: null, additional: [] };
    let characters = [];
    let charId = null;
    try {
        const ctx = getCtx();
        if (Array.isArray(ctx && ctx.characters)) characters = ctx.characters;
        if (ctx && ctx.characterId !== undefined && ctx.characterId !== null) charId = ctx.characterId;
    } catch (_e) { /* 无上下文 -> 空结构 */ }
    let character = null;
    if (name === 'current') {
        if (charId !== null && characters[charId]) character = characters[charId];
    } else {
        character = characters.find((c) => c && c.name === name) || null;
    }
    if (!character) {
        if (name === 'current') return books;
        throw Error("未找到名为 '" + name + "' 的角色卡");
    }
    const world = character && character.data && character.data.extensions ? character.data.extensions.world : null;
    if (typeof world === 'string' && world) books.primary = world;
    return books;
}

/** JSR 4.x 名称（内部委托 getCharLorebooks）。 */
export function getCharWorldbookNames(character_name) {
    return getCharLorebooks({ name: character_name || 'current' });
}

// ---------------------------------------------------------------- JSR 4.x 世界书 API（v214）
// 契约真源：JS-Slash-Runner 4.11.2 src/function/worldbook.ts（只对照行为重写，不拄贝代码）。
// 消费方：千纱-DS鲸鱼版「开局可视化」里的世界书开关 UI（卡 JSON:128960-129310 / 137318-137420）。

/** JSR 4.x `getWorldbookNames()` → string[]（全部世界书名）。 */
export function getWorldbookNames() {
    try {
        const ctx = getCtx();
        const names = Array.isArray(ctx && ctx.world_names) ? ctx.world_names : [];
        return deepClone(names.filter((n) => typeof n === 'string' && n));
    } catch (_e) {
        return [];
    }
}

/** JSR 4.x `getGlobalWorldbookNames()` → string[]（全局启用中的世界书，= ST world_info.globalSelect）。 */
export function getGlobalWorldbookNames() {
    try {
        const ctx = getCtx();
        const sel = (ctx && ctx.world_info && Array.isArray(ctx.world_info.globalSelect))
            ? ctx.world_info.globalSelect
            : ((ctx && Array.isArray(ctx.selected_world_info)) ? ctx.selected_world_info : []);
        return deepClone(sel.filter((n) => typeof n === 'string' && n));
    } catch (_e) {
        return [];
    }
}

/**
 * JSR 4.x `getChatWorldbookName(chat_name)` → string | null。
 * 参数只接受 'current'（否则 throw，对齐 JSR worldbook.ts:65-70）；
 * 无绑定时返回 null（非错误）。实现读 chat_metadata.world_info 并校验在 world_names 中。
 */
export function getChatWorldbookName(chat_name) {
    if (String(chat_name) !== 'current') {
        throw Error("目前不支持对非当前聊天调用 getChatWorldbookName");
    }
    try {
        const ctx = getCtx();
        const bound = ctx && ctx.chatMetadata ? ctx.chatMetadata.world_info : null;
        if (typeof bound === 'string' && bound) {
            const names = Array.isArray(ctx.world_names) ? ctx.world_names : [];
            if (names.includes(bound)) return bound;
        }
    } catch (_e) { /* noop */ }
    return null;
}

/**
 * JSR 4.x `getWorldbook(name)` → Promise<WorldbookEntry[]>（纯数组，按 displayIndex）。
 * 未命中 throw（对齐 JSR worldbook.ts:356-364）——但只要名字来自三个 getter，就能命中（卡内嵌书 + 全局库）。
 *
 * [v216] 原样优先：ST 语义「书名即身份」，带尾随空格的书名合法；先按原始名取，未命中再用 trim() 兜底重试一次
 * （对齐 ST world-info.js 原样传名，避免我方 shim 在传递层 trim 把脏名规范化成另一个名字）。
 */
export async function getWorldbook(worldbook_name) {
    const raw = String(worldbook_name == null ? '' : worldbook_name);
    const name = raw.trim();
    if (!name) throw Error("未能找到世界书 ''");
    let data = null;
    // 原始名优先；名字本身干净（raw === name）时只试一次，避免无谓的重复读取
    const candidates = raw === name ? [name] : [raw, name];
    for (const candidate of candidates) {
        try {
            const ctx = getCtx();
            data = await ctx.loadWorldInfo(candidate);
        } catch (_e) {
            data = null;
        }
        if (data && typeof data === 'object' && data.entries && typeof data.entries === 'object') break;
        data = null;
    }
    if (!data) {
        throw Error("未能找到世界书 '" + name + "'");
    }
    const entries = Object.entries(data.entries).map(([uid, entry]) =>
        toLorebookEntry(Number(uid), entry || {})
    );
    return deepClone(overlayBookEntries(name, entries));
}

/**
 * JSR 4.x `updateWorldbookWith(name, updater, options)` → Promise<WorldbookEntry[]>。
 * 语义：读全量数组 → updater（收数组、返数组）→ 全量替换落盘（JSR worldbook.ts:432-439）。
 * 落盘通路：先写内存 overlay（本会话立即生效），再过 RikkaBridge.updateLorebookEntries 交宿主持久化。
 *
 * [v216] 原样传名（宿主侧同样「原样优先 → trim 兜底」）；overlay 仍以 trim 后的 name 作稳定 key。
 */
export async function updateWorldbookWith(worldbook_name, updater, options) {
    const raw = String(worldbook_name == null ? '' : worldbook_name);
    const name = raw.trim();
    if (typeof updater !== 'function') {
        throw Error("updateWorldbookWith: updater 必须是函数");
    }
    const current = await getWorldbook(raw);
    const updated = await updater(deepClone(current));
    if (!Array.isArray(updated)) return current;

    const ov = wbDisableOverlay.get(name) || new Map();
    const patch = [];
    for (const e of updated) {
        if (!e || typeof e !== 'object') continue;
        const uid = Number(e.uid);
        if (!Number.isFinite(uid)) continue;
        const disable = !(e.enabled !== false); // enabled 缺省视为 true
        ov.set(uid, disable);
        patch.push({ uid: uid, disable: disable });
    }
    wbDisableOverlay.set(name, ov);

    try {
        const ok = callBridge('updateLorebookEntries', raw, JSON.stringify(patch));
        try {
            console.log('[MVU-Standalone] updateWorldbookWith persisted:', name, 'entries=' + patch.length, 'ok=' + (ok === true), 'render=' + String((options && options.render) || ''));
        } catch (_e) { /* noop */ }
    } catch (e) {
        console.warn('[MVU-Standalone] updateWorldbookWith bridge failed:', e);
    }
    return getWorldbook(raw);
}
