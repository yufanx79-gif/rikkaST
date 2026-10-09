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

    // 宏替换 range 中的 {{lastMessageId}} 等
    const rangeDemacroed = ctx.substituteParams ? ctx.substituteParams(range.toString()) : range.toString();
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
