/**
 * rikkaST st-compat: slash-commands/SlashCommandCommonEnumsProvider.js
 * ============================================================
 * 模拟 ST1.18 `commonEnumProviders` / `enumIcons`。
 *
 * ★ v201 关键修复（酒馆助手「点开黑屏」真正根因）
 * JSR(酒馆助手) 4.11.x 在 `initSlashCommands()` 里调用
 *     commonEnumProviders.boolean('trueFalse')()      // 注意：工厂调用后还要再 `()` 一次
 * 而旧 shim 只导出了 characters/groups/personas 三个 provider（且直接返回数组），
 * 于是 `commonEnumProviders.boolean` 是 undefined → `nn.boolean is not a function`
 * → 异常发生在 `$(function(){ … initSlashCommands(); app.mount($app[0]); })`
 *   这个 jQuery ready 回调里，**`app.mount()` 根本没被执行** → 设置页只剩深色背景 = 黑屏。
 * （真机日志实证：`jQuery.Deferred exception: nn.boolean is not a function`）
 *
 * ST 的契约：每个 provider 是「返回 enum 提供函数的工厂」，即
 *     typeof commonEnumProviders.boolean('trueFalse') === 'function'
 *     commonEnumProviders.boolean('trueFalse')() === SlashCommandEnumValue[]
 * 每个 provider 都返回一个新数组（不是同一个数组引用），避免共享可变状态。
 */

import { SlashCommandEnumValue } from './SlashCommandEnumValue.js';

function makeEnumValue(value, description, icon) {
    const v = new SlashCommandEnumValue(value, description);
    if (icon !== undefined) v.icon = icon;
    return v;
}

/** ST 内部：把 ['trueFalse', …] 这类标签串归一化成真正的 enum 值对。 */
function normalizePair(label, fallbackA, fallbackB) {
    const s = String(label == null ? '' : label);
    const lower = s.toLowerCase();

    if (lower.includes('truefalse') || /^(boolean|bool)/.test(lower)) {
        return ['true', 'false'];
    }
    if (lower.includes('yesno') || lower.includes('yes/no')) {
        return ['yes', 'no'];
    }
    if (lower.includes('onoff') || lower.includes('on/off')) {
        return ['on', 'off'];
    }
    if (lower === '10' || lower === '0/1' || lower === 'zeroone' || lower === 'zero/one') {
        return ['1', '0'];
    }
    // 'a|b' / 'a,b' / 'a/b' 形态
    const parts = s.split(/[|,\/]/).map((t) => t.trim()).filter(Boolean);
    if (parts.length >= 2) return [parts[0], parts[1]];
    if (s) return [s, s];
    return [fallbackA, fallbackB];
}

function pairProvider(label, fallback, icon) {
    return () => {
        const pair = normalizePair(label, fallback[0], fallback[1]);
        return [
            makeEnumValue(pair[0], pair[0], icon),
            makeEnumValue(pair[1], pair[1], icon),
        ];
    };
}

function listProvider(values, icon) {
    return () => values.map((v) => makeEnumValue(v, v, icon));
}

export const commonEnumProviders = {
    /** @see ST scripts/slash-commands/SlashCommandCommonEnumsProvider.js */
    boolean: (label) => pairProvider(label, ['true', 'false'], 'check'),

    number: () => listProvider([], 'hashtag'),
    string: () => listProvider([], 'font'),
    enum: () => listProvider([], 'list'),

    // 这些依赖宿主数据（角色 / 群组 / 人格 / 世界书），
    // 由 runtime 在拿到真实数据后经 `__rikkaSetCommonEnumSource` 注入。
    /** ST: `characters(mode='all') => () => EnumValue[]`（工厂，不是直接数组）。 */
    characters: (mode = 'all') => () => {
        const src = getCommonEnumSource();
        const out = [];
        if (mode === 'all' || mode === 'character') {
            (Array.isArray(src.characters) ? src.characters : []).forEach((c) => {
                out.push(makeEnumValue(c && c.name ? c.name : String(c), null, '👤'));
            });
        }
        if (mode === 'all' || mode === 'group') {
            (Array.isArray(src.groups) ? src.groups : []).forEach((g) => {
                out.push(makeEnumValue(g && g.name ? g.name : String(g), null, '🧑‍🤝‍🧑'));
            });
        }
        return out;
    },
    groups: () => {
        const src = getCommonEnumSource().groups || [];
        return Array.isArray(src) ? src.slice() : [];
    },
    /** ST: `personas({allowPersonaKey}) => () => EnumValue[]`（工厂）。 */
    personas: ({ allowPersonaKey = false } = {}) => () => {
        const src = getCommonEnumSource();
        const list = Array.isArray(src.personas) ? src.personas : [];
        return list.map((p) => {
            const name = (p && typeof p === 'object') ? (p.name || p.key || '') : String(p);
            return makeEnumValue(String(name), null, '🧙‍♂️');
        });
    },
    worldInfos: () => {
        const src = getCommonEnumSource().worldInfos || [];
        return Array.isArray(src) ? src.slice() : [];
    },

    // ---- 以下按 ST 1.19.0 SlashCommandCommonEnumsProvider.js 补齐 ----
    // 实测 Tracker 插件直接调 commonEnumProviders.messages(...)，缺失即崩。

    /** 所有变量名；可按 global / local / scope / all 过滤（ST:variables）。 */
    variables: (...type) => (_executor, scope) => {
        const types = type.flat();
        const isAll = types.includes('all');
        const src = getCommonEnumSource();
        const out = [];
        if (isAll || types.includes('scope')) {
            const scopeNames = (scope && Array.isArray(scope.allVariableNames)) ? scope.allVariableNames : [];
            scopeNames.forEach((name) => out.push(makeEnumValue(name, null, 'S')));
        }
        if (isAll || types.includes('local')) {
            Object.keys((src.localVariables && typeof src.localVariables === 'object') ? src.localVariables : {})
                .forEach((name) => out.push(makeEnumValue(name, null, 'L')));
        }
        if (isAll || types.includes('global')) {
            Object.keys((src.globalVariables && typeof src.globalVariables === 'object') ? src.globalVariables : {})
                .forEach((name) => out.push(makeEnumValue(name, null, 'G')));
        }
        return out.filter((item, idx, list) => idx === list.findIndex((it) => it.value === item.value));
    },

    /** 数字 + 变量名（ST:numbersAndVariables）。 */
    numbersAndVariables: (executor, scope) => {
        const out = commonEnumProviders.variables('all')(executor, scope);
        const anyVar = makeEnumValue('any variable name', null, '𝑥');
        const anyNum = makeEnumValue('any number', null, '#');
        return [...out, anyVar, anyNum];
    },

    /** 指定群的成员索引（ST:groupMembers）。 */
    groupMembers: (groupId = undefined) => () => {
        const src = getCommonEnumSource();
        const members = (src.groupMembers && typeof src.groupMembers === 'object')
            ? (src.groupMembers[String(groupId)] || src.groupMembers['__default__'] || [])
            : [];
        return (Array.isArray(members) ? members : [])
            .map((m, i) => makeEnumValue(String(i), m && m.name ? m.name : '', '👤'));
    },

    /** 全部标签（ST:tags）。 */
    tags: (mode = 'all') => () => {
        const src = getCommonEnumSource();
        const tags = Array.isArray(src.tags) ? src.tags : [];
        return tags.map((t) => makeEnumValue(t && t.name ? t.name : String(t), null, '🏷️'));
    },

    /** 指定角色的标签（ST:tagsForChar）。 */
    tagsForChar: (mode = 'all') => () => {
        const src = getCommonEnumSource();
        const tags = Array.isArray(src.tags) ? src.tags : [];
        return tags.map((t) => makeEnumValue(t && t.name ? t.name : String(t), null, '🏷️'));
    },

    /**
     * 当前聊天的消息 id（ST:messages）。
     *
     * Tracker 插件依赖这个 provider；ST 签名是
     *   messages({ allowIdAfter, allowVars }) => (executor, scope) => EnumValue[]
     */
    messages: ({ allowIdAfter = false, allowVars = false } = {}) => (executor, scope) => {
        const src = getCommonEnumSource();
        const chat = Array.isArray(src.chat) ? src.chat : [];
        const nameFilter = (executor && Array.isArray(executor.namedArgumentList))
            ? String((executor.namedArgumentList.find((it) => it && it.name === 'name') || {}).value || '')
            : '';
        const out = [];
        chat.forEach((message, index) => {
            const who = message && message.name != null ? String(message.name) : '';
            const text = message && message.mes != null ? String(message.mes) : '';
            const desc = `${who}: ${text}`;
            if (nameFilter && !desc.startsWith(`${nameFilter}:`)) return;
            const icon = (message && message.is_user) ? '👤'
                : (message && message.is_system) ? '⚙️' : '🤖';
            out.push(makeEnumValue(String(index), desc, icon));
        });
        if (allowIdAfter) {
            out.push(makeEnumValue(String(chat.length), '>> After Last Message >>', '➕'));
        }
        if (allowVars) {
            out.push(...commonEnumProviders.variables('all')(executor, scope));
        }
        return out;
    },

    /** 消息附带的媒体（ST:messageMedia）。 */
    messageMedia: () => (executor) => {
        const src = getCommonEnumSource();
        const chat = Array.isArray(src.chat) ? src.chat : [];
        const idArg = (executor && Array.isArray(executor.namedArgumentList))
            ? (executor.namedArgumentList.find((it) => it && (it.name === 'mesId' || it.name === 'id')) || {}).value
            : undefined;
        const messageId = Number(idArg);
        if (Number.isNaN(messageId) || messageId < 0 || messageId >= chat.length) return [];
        const message = chat[messageId];
        const media = message && message.extra && message.extra.media;
        if (!Array.isArray(media)) return [];
        return media.map((m, i) => makeEnumValue(String(i),
            (m && m.title) || (message.extra && message.extra.title) || '[Untitled]', '🖼️'));
    },

    /** 当前聊天里出现过的角色名（ST:messageNames）。 */
    messageNames: () => {
        const src = getCommonEnumSource();
        const chat = Array.isArray(src.chat) ? src.chat : [];
        const seen = new Set();
        const out = [];
        for (const message of chat) {
            const name = message && message.name != null ? String(message.name) : '';
            if (!name || seen.has(name)) continue;
            seen.add(name);
            out.push(makeEnumValue(name, null, (message && message.is_user) ? '👤' : '🤖'));
        }
        return out;
    },

    /** 所有世界书名（ST:worlds）。 */
    worlds: () => {
        const src = getCommonEnumSource();
        const names = Array.isArray(src.worldNames) ? src.worldNames : [];
        return names.map((n) => makeEnumValue(String(n), null, '🌐'));
    },

    /** 当前聊天的注入项（ST:injects）。 */
    injects: () => {
        const src = getCommonEnumSource();
        const injects = (src.scriptInjects && typeof src.scriptInjects === 'object') ? src.scriptInjects : {};
        return Object.entries(injects).map(([id, inject]) => {
            const pos = inject && inject.position != null ? String(inject.position) : 'unknown';
            const depth = inject && inject.depth != null ? inject.depth : 0;
            const scan = inject && inject.scan != null ? inject.scan : false;
            const value = inject && inject.value != null ? String(inject.value) : '';
            return makeEnumValue(id, `[Inject](${pos}, depth: ${depth}, scan: ${scan}) ${value}`, '💉');
        });
    },

    /** STscript 类型（ST:types）。 */
    types: () => ['string', 'number', 'boolean', 'array', 'object', 'null', 'undefined']
        .map((t) => makeEnumValue(t, null, '◊')),

    /** 消息角色（ST:messageRoles）。 */
    messageRoles: () => [
        makeEnumValue('user', null, '👤'),
        makeEnumValue('assistant', null, '🤖'),
        makeEnumValue('system', null, '⚙️'),
    ],

    /** 背景（ST:backgrounds）。 */
    backgrounds: () => {
        try {
            const nodes = document.querySelectorAll('.bg_example');
            return Array.from(nodes || [])
                .map((n) => makeEnumValue(n.getAttribute ? n.getAttribute('bgfile') : ''))
                .filter((v) => v.value && v.value.length);
        } catch (_e) {
            return [];
        }
    },

    /** 连接配置（ST:connectionProfiles）。 */
    connectionProfiles: ({ includeNone = false } = {}) => () => {
        const src = getCommonEnumSource();
        const profiles = Array.isArray(src.connectionProfiles) ? src.connectionProfiles : [];
        const out = [];
        if (includeNone) out.push(makeEnumValue('<None>'));
        profiles.forEach((p) => out.push(makeEnumValue(p && p.name ? p.name : String(p), null, '🖥️')));
        return out;
    },
};

let __commonEnumSource = {};
function getCommonEnumSource() {
    return __commonEnumSource && typeof __commonEnumSource === 'object' ? __commonEnumSource : {};
}

/** 供 runtime 注入真实角色/群组/人格列表（可选）。 */
export function __rikkaSetCommonEnumSource(source) {
    __commonEnumSource = source && typeof source === 'object' ? source : {};
}

export const enumIcons = {
    meta: 'diamond',
    enum: 'list',
    variable: 'square-root-variable',
    closure: 'code',
    subcommand: 'terminal',
    file: 'file',
    check: 'check',
    hashtag: 'hashtag',
    font: 'font',
    list: 'list',
    world: 'globe',
    character: 'user',
    group: 'users',
    persona: 'user-pen',
};
export const commonEnumMatchProviders = {
    /**
     * Provides autocomplete matching for folder-like enum values.
     * Matches if the input starts with the check or vice versa (case-insensitive).
     * @param {string} input - The input string to match against
     * @param {string} check - The check string to match with
     * @param {object} [options={}] - Options
     * @param {boolean} [options.trueOnEmpty=true] - Whether to return true when input is empty
     * @returns {boolean} - True if the strings match according to the folder matching rules
     */
    folderEnum: (input, check, { trueOnEmpty = true } = {}) => {
        if (!check) return false;
        if (!input) return trueOnEmpty;
        const inputLower = input.toLowerCase();
        const checkLower = check.toLowerCase();
        return inputLower.startsWith(checkLower) || checkLower.startsWith(inputLower);
    },
};
