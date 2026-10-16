/**
 * MVU Standalone — 聊天消息 Shim
 * 包装 SillyTavern 的 chat 数组
 */

import { deepClone } from './clone-util.js';

function getCtx() {
    return window.SillyTavern.getContext();
}

function stringToRange(input, min, max) {
    let start, end;
    const clamp = v => _.clamp(v < 0 ? max + v + 1 : v, min, max);

    if (input.match(/^(-?\d+)$/)) {
        start = end = clamp(Number(input));
    } else {
        const match = input.match(/^(-?\d+)-(-?\d+)$/);
        if (!match) return null;
        [start, end] = _.sortBy([match[1], match[2]].map(Number).map(clamp));
    }
    if (isNaN(start) || isNaN(end)) return null;
    return { start, end };
}

function getRole(chatMessage) {
    const isNarrator = chatMessage.extra?.type === 'narrator';
    if (isNarrator) {
        return chatMessage.is_user ? 'unknown' : 'system';
    }
    return chatMessage.is_user ? 'user' : 'assistant';
}

export function getChatMessages(range, { role = 'all', hide_state = 'all', include_swipes = false } = {}) {
    const ctx = getCtx();
    const chat = ctx.chat;
    if (!chat || chat.length === 0) return [];

    // 宏替换 range 中的 {{lastMessageId}} / {{lastUserMessageId}} / {{lastCharMessageId}} / {{firstMessageId}} 等
    let rangeStr = String(range == null ? '' : range);
    if (ctx.substituteParams) {
        rangeStr = ctx.substituteParams(rangeStr);
    }
    if (rangeStr.includes('{{')) {
        rangeStr = rangeStr
            .replace(/\{\{\s*lastMessageId\s*\}\}/gi, () => String(Math.max(0, chat.length - 1)))
            .replace(/\{\{\s*firstMessageId\s*\}\}/gi, '0')
            .replace(/\{\{\s*lastUserMessageId\s*\}\}/gi, () => {
                for (let i = chat.length - 1; i >= 0; i--) {
                    if (chat[i] && chat[i].is_user) return String(i);
                }
                return '-1';
            })
            .replace(/\{\{\s*lastCharMessageId\s*\}\}/gi, () => {
                for (let i = chat.length - 1; i >= 0; i--) {
                    if (chat[i] && !chat[i].is_user && !chat[i].is_system) return String(i);
                }
                return '-1';
            });
    }
    const rangeDemacroed = rangeStr;
    const rangeNumber = stringToRange(rangeDemacroed, 0, chat.length - 1);
    if (!rangeNumber) return [];

    const { start, end } = rangeNumber;

    const processMessage = (messageId) => {
        const message = chat[messageId];
        if (!message) return null;

        const messageRole = getRole(message);
        if (role !== 'all' && messageRole !== role) return null;
        if (hide_state !== 'all' && (hide_state === 'hidden') !== message.is_system) return null;

        const swipeId = message?.swipe_id ?? 0;
        let swipes = message?.swipes ?? [message.mes];
        let swipesData = message?.variables ?? [{}];
        let swipesInfo = message?.swipe_info ?? [message?.extra ?? {}];
        const swipeLength = swipes.length;
        // swipes 必须是字符串数组（MVU 等会对条目调用 matchAll）；非字符串条目回退到正文。
        swipes = _.range(0, swipeLength).map(i => {
            const v = swipes[i];
            if (typeof v === 'string') return v;
            if (v == null) return '';
            return String(message?.mes ?? '');
        });
        swipesData = _.range(0, swipeLength).map(i => swipesData[i] ?? {});
        swipesInfo = _.range(0, swipeLength).map(i => swipesInfo[i] ?? {});

        const extra = swipesInfo[swipeId];
        const data = swipesData[swipeId];

        if (include_swipes) {
            return {
                message_id: messageId,
                name: message.name,
                role: messageRole,
                is_hidden: message.is_system,
                swipe_id: swipeId,
                swipes,
                swipes_data: swipesData,
                swipes_info: swipesInfo,
            };
        }
        return {
            message_id: messageId,
            name: message.name,
            role: messageRole,
            is_hidden: message.is_system,
            message: message.mes ?? '',
            data,
            extra,
            // for compatibility
            swipe_id: swipeId,
            swipes,
            swipes_data: swipesData,
        };
    };

    const chatMessages = _.range(start, end + 1)
        .map(i => processMessage(i))
        .filter(m => m !== null);

    return deepClone(chatMessages);
}

export async function setChatMessages(chatMessages, { refresh = 'affected' } = {}) {
    const ctx = getCtx();
    const chat = ctx.chat;

    // group by message_id, merge duplicates
    const grouped = _(chatMessages)
        .sortBy('message_id')
        .groupBy('message_id')
        .map(msgs => msgs.reduce((acc, cur) => ({ ...acc, ...cur }), {}))
        .value();

    for (const chatMessage of grouped) {
        const msgId = chatMessage.message_id;
        const data = chat[msgId];
        if (!data) continue;

        // 兼容: plain object variables → array
        if (_.isPlainObject(data?.variables)) {
            _.set(data, 'variables',
                _.range(0, data.swipes?.length ?? 1).map(i => data.variables[i] ?? {}));
        }

        if (chatMessage.name !== undefined) {
            _.set(data, 'name', chatMessage.name);
        }
        if (chatMessage.role !== undefined) {
            _.set(data, 'is_user', chatMessage.role === 'user');
            if (chatMessage.role === 'system') {
                _.set(data, 'extra.type', 'narrator');
            } else {
                _.unset(data, 'extra.type');
            }
        }
        if (chatMessage.is_hidden !== undefined) {
            _.set(data, 'is_system', chatMessage.is_hidden);
        }

        // ChatMessage style (message + data)
        if (chatMessage.message !== undefined || chatMessage.data !== undefined) {
            if (chatMessage.message !== undefined) {
                _.set(data, 'mes', chatMessage.message);
                if (data.swipes) {
                    _.set(data, ['swipes', data.swipe_id], chatMessage.message);
                }
            }
            if (chatMessage.data !== undefined) {
                if (!data.variables) {
                    _.set(data, 'variables', _.times(data.swipes?.length ?? 1, _.constant({})));
                }
                _.set(data, ['variables', data.swipe_id ?? 0], chatMessage.data);
            }
            if (chatMessage.extra !== undefined) {
                _.set(data, 'extra', chatMessage.extra);
                if (data.swipe_info) {
                    _.set(data, ['swipe_info', data.swipe_id ?? 0], chatMessage.extra);
                }
            }
        }
        // ChatMessageSwiped style
        else if (chatMessage.swipes !== undefined || chatMessage.swipes_data !== undefined) {
            const maxLen = _.max([
                chatMessage.swipes?.length,
                chatMessage.swipes_data?.length,
                chatMessage.swipes_info?.length
            ]) ?? data.swipes?.length ?? 1;

            const swipeId = _.clamp(chatMessage.swipe_id ?? data.swipe_id ?? 0, 0, maxLen - 1);
            const swipes = _.range(0, maxLen).map(i => (chatMessage.swipes ?? data.swipes ?? [data.mes])[i] ?? '');
            const swipesData = _.range(0, maxLen).map(i => (chatMessage.swipes_data ?? data.variables ?? [{}])[i] ?? {});
            const swipesInfo = _.range(0, maxLen).map(i => (chatMessage.swipes_info ?? data.swipe_info ?? [{}])[i] ?? {});

            _.set(data, 'swipes', swipes);
            _.set(data, 'variables', swipesData);
            _.set(data, 'swipe_info', swipesInfo);
            _.set(data, 'swipe_id', swipeId);
            _.set(data, 'mes', swipes[swipeId]);
            _.set(data, 'extra', swipesInfo[swipeId]);
        }
    }

    // Save
    if (refresh === 'all') {
        if (ctx.saveChat) await ctx.saveChat();
        if (ctx.reloadCurrentChat) await ctx.reloadCurrentChat();
    } else {
        _debouncedSaveChat();
    }
}

let _timer = null;
function _debouncedSaveChat() {
    if (_timer) clearTimeout(_timer);
    _timer = setTimeout(() => {
        _timer = null;
        const freshCtx = getCtx();
        if (freshCtx.saveChat) freshCtx.saveChat();
    }, 1000);
}

// 兼容旧版 setChatMessage（deprecated）
export async function setChatMessage(fieldValues, messageId, option = {}) {
    const entry = { message_id: messageId };
    if (fieldValues.message !== undefined) entry.message = fieldValues.message;
    if (fieldValues.data !== undefined) entry.data = fieldValues.data;
    await setChatMessages([entry], { refresh: option.refresh ?? 'none' });
}

// ---------------------------------------------------------------- 宿主写回桥（RikkaBridge）
/**
 * 同步调用宿主桥；不存在/异常返回 undefined。
 * 依次尝试本窗口 → 父窗口（卡脚本可能跑在 blob: 子 iframe 里，那里的桥不一定可用）。
 * ⚠️ 必须是 b[name](...args)：摘下来调用会丢 this，Android 侧直接抛 non-injected object。
 */
function callBridge(name, ...args) {
    const bridges = [];
    try { if (window.RikkaBridge) bridges.push(window.RikkaBridge); } catch (_e) { /* noop */ }
    try {
        const p = window.parent;
        if (p && p !== window && p.RikkaBridge) bridges.push(p.RikkaBridge);
    } catch (_e) { /* noop */ }
    for (const b of bridges) {
        try {
            if (typeof b[name] !== 'function') continue;
            return b[name](...args);
        } catch (_e) { /* 试下一个 */ }
    }
    return undefined;
}

/**
 * JSR `createChatMessages(chat_messages, { insert_at, insert_before, refresh })`
 * —— 向聊天记录**插入**新楼层（不触发生成）。
 *
 * [v243 BUGFIX A2] 这是本次排查的核心缺口之一：rikkaST 的 TavernHelper 从来没有
 * `createChatMessages`，而 JSR 会把 TavernHelper 的每个顶层键都装成裸全局
 * （JS-Slash-Runner/src/iframe/predefine.js:14-21），所以卡脚本里
 * `createChatMessages([...])` 在酒馆助手（桌面）能跑，在 rikkaST 直接
 * `ReferenceError: createChatMessages is not defined`。
 *
 * 真机取证（tavern-runtime.log 2026-10-13 10:00:59 / 10:01:21 / 10:02:13，三次内容一致）：
 *   [js-event] toast {"level":"warning","args":["用户设定未能写入世界书：当前酒馆助手不支持世界书接口","开局提醒"]}
 *   [error] [百合花期] opening submit failed ReferenceError: createChatMessages is not defined
 *       at mt (<anonymous>:1:13192)
 * 该卡的 mt() 是：写世界书 → `await createChatMessages([{role:'user',name,message}])` → `await triggerSlash('/trigger')`。
 * createChatMessages 抛异常后整条开局链路中断 → 用户看到「开局没有提交成功」，
 * 也就是「渲染面板里填写的内容点了没反应 / 保存不了」。
 *
 * 兼容两种签名：
 *  - JSR 4.x：createChatMessages([{role,name,message,data}], options)
 *  - ST 原生：createChatMessages(messageId, messageText, isUser, mesObj, options)
 */
export async function createChatMessages(a, b, c, d, e) {
    let list = [];
    if (Array.isArray(a)) {
        list = a;
    } else if (typeof a === 'number' || a === null || a === undefined) {
        // ST 原生签名：以用户身份发送 / 覆写指定楼层
        list = [{ role: c ? 'user' : 'assistant', message: String(b == null ? '' : b) }];
        const targetId = Number(a);
        if (Number.isInteger(targetId) && targetId >= 0) list[0].__overwrite_id = targetId;
    } else if (a && typeof a === 'object') {
        list = [a];
    }

    const payload = [];
    const overwrites = [];
    for (const m of list) {
        if (!m || typeof m !== 'object') continue;
        const role = m.role === 'user' || m.role === 'assistant' || m.role === 'system' ? m.role : 'user';
        const item = {
            role: role,
            name: m.name == null ? '' : String(m.name),
            message: String(m.message == null ? '' : m.message),
            data: (m.data && typeof m.data === 'object') ? m.data : null,
        };
        if (Number.isInteger(m.__overwrite_id)) overwrites.push({ id: m.__overwrite_id, item: item });
        else payload.push(item);
    }

    // 1) 覆写已有楼层 → 走 setChatMessages（真正落库）
    for (const o of overwrites) {
        try { await setChatMessages([{ message_id: o.id, message: o.item.message }], { refresh: 'none' }); }
        catch (_e) { /* noop */ }
    }

    if (payload.length === 0) return overwrites.length ? undefined : [];

    // 2) 追加新楼层 → 宿主 insertChatMessages（同步插入，保证紧随其后的 /trigger 能看到新楼层）
    let ok = false;
    try { ok = callBridge('insertChatMessages', JSON.stringify(payload)) === true; } catch (_e) { ok = false; }
    if (!ok) {
        console.warn('[RikkaTavern] createChatMessages: 宿主插入通道不可用（未接线/桥不可用）', payload);
        return undefined;
    }

    // 3) 宿主已真正插入 → 刷新 JS 镜像，让 chat.length / 下标重新对齐
    try {
        const ctx = getCtx();
        if (ctx && typeof ctx.reloadCurrentChat === 'function') await ctx.reloadCurrentChat();
    } catch (_e) { /* noop */ }

    // JSR 返回 void；这里返回新楼层（带 message_id），方便卡脚本定位（JSR 卡通常回退到 getLastMessageId）
    try {
        const chat = getCtx().chat || [];
        const n = payload.length;
        return chat.slice(Math.max(0, chat.length - n));
    } catch (_e) {
        return undefined;
    }
}

/**
 * JSR `deleteChatMessages(message_ids, options)`。
 *
 * rikkaST 宿主目前只有「按已有下标更新字段」的通道（RikkaBridge.updateChatMessages），
 * 没有删除通道。这里**明确告警并返回 false**，不假装成功 —— 静默改 JS 镜像会让用户
 * 以为删掉了，重新进会话又回来（比报错更难排查）。
 */
export async function deleteChatMessages(messageIds, { refresh = 'all' } = {}) {
    const rawList = Array.isArray(messageIds) ? messageIds : [messageIds];
    const ids = rawList
        .map((id) => Number(id))
        .filter((n) => Number.isInteger(n) && n >= 0);
    if (ids.length === 0) return false;
    let ok = false;
    try {
        ok = callBridge('deleteChatMessages', JSON.stringify(ids)) === true;
    } catch (_e) {
        ok = false;
    }
    if (!ok) {
        console.warn('[RikkaTavern] deleteChatMessages: 宿主删除未命中或通道不可用', ids);
        return false;
    }
    try {
        const ctx = getCtx();
        if (refresh !== 'none' && ctx && typeof ctx.reloadCurrentChat === 'function') {
            await ctx.reloadCurrentChat();
        }
    } catch (_e) { /* noop */ }
    return true;
}
