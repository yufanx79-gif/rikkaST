/**
 * rikkaST st-compat: logprobs.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/logprobs.js`（615 行）。
 *
 * 真源两块：
 *  1. **数据层**（:509-552）：`saveLogprobsForActiveMessage(logprobs, continueFrom)`
 *     把最近一次生成的 token 概率存进 `state.messageLogprobs`（Map，按消息哈希索引），
 *     并裁剪到 `MAX_MESSAGE_LOGPROBS = 100` 条。哈希只取 `{name, mid, text}`，
 *     **不含 swipeId**（真源注释：删 swipe 会让后续 swipe id 全变，不稳定）。
 *  2. **渲染层**：把每个 token 按概率染色（`TINTS = 4` 档）+ 悬浮显示候选。
 *
 * ## 我们实现哪部分
 * 第 1 块**完整移植**（纯数据，扩展/脚本会读）。
 * 第 2 块是 ST 消息 DOM 染色，我们用 Compose 渲染消息 → no-op（见 DIVERGENCE）。
 */

import * as ScriptNS from './script.js';
// ⚠️ [v247 BUGFIX] 不能 `import { chat }` —— st-compat/script.js 的 `chat` 是
// `export let`（live binding），在 ES module 里解构导入后拿到的是**快照**，
// 后续 runtime 刷新 `chat = [...]` 时本模块仍指向旧数组（甚至初始空数组）。
// 必须每次通过命名空间读 `ScriptNS.chat` 才能拿到最新值。
const getChat = () => (Array.isArray(ScriptNS.chat) ? ScriptNS.chat : []);
import { getStringHash } from './utils.js';

/** ST logprobs.js:16 */
const MAX_MESSAGE_LOGPROBS = 100;
/** ST logprobs.js:15 */
export const TINTS = 4;

/** ST logprobs.js:490 附近的模块级状态。 */
const state = {
    /** @type {Map<number, MessageLogprobData>} */
    messageLogprobs: new Map(),
};

/**
 * 消息哈希（ST logprobs.js:543-552 逐字对齐）。
 *
 * ⚠️ 真源注释强调：**不要**把 swipeId 算进哈希 —— 删掉一个 swipe 会让后续
 * 所有 swipe 的 id 改变，哈希就全失效了。只取 {name, mid, text}。
 */
function getMessageHash(message) {
    const c = getChat();
    const hashParams = {
        name: message && message.name,
        mid: c.indexOf(message),
        text: message && message.mes,
    };
    return getStringHash(JSON.stringify(hashParams));
}

/**
 * 保存当前楼层的 logprobs（ST logprobs.js:509-541 语义）。
 *
 * @param {Array<{token: string, topLogprobs: Array<[string, number]>}>|null} logprobs
 * @param {string|null} [continueFrom] 「继续」时的前缀
 */
export function saveLogprobsForActiveMessage(logprobs, continueFrom) {
    // 非流式 API 可能返回 null
    if (!logprobs) return;
    const c = getChat();
    if (c.length === 0) return;
    const msgId = c.length - 1;
    const message = c[msgId];
    if (!message) return;

    const data = {
        created: Date.now(),
        api: 'openai',
        messageId: msgId,
        swipeId: message.swipe_id,
        messageLogprobs: logprobs,
        continueFrom: continueFrom ?? null,
        hash: getMessageHash(message),
    };
    state.messageLogprobs.set(data.hash, data);

    // 裁剪：只保留最近 MAX_MESSAGE_LOGPROBS 条（真源 :534-540）
    const old = Array.from(state.messageLogprobs.values())
        .sort((a, b) => b.created - a.created)
        .slice(MAX_MESSAGE_LOGPROBS);
    for (const d of old) state.messageLogprobs.delete(d.hash);
}

/**
 * 取某条消息的 logprobs（ST 内部 `getActiveMessageLogprobData` 的公开版）。
 *
 * @param {number} [messageId] 不传 = 最后一条
 * @returns {object|null}
 */
export function getMessageLogprobs(messageId) {
    const c = getChat();
    if (c.length === 0) return null;
    const idx = Number.isFinite(Number(messageId)) ? Number(messageId) : c.length - 1;
    const message = c[idx];
    if (!message) return null;
    const hash = getMessageHash(message);
    return state.messageLogprobs.get(hash) ?? null;
}

/** 清空缓存（宿主切会话时调用）。 */
export function clearLogprobsCache() {
    state.messageLogprobs.clear();
}

/**
 * ST `initLogprobs()`：初始化 DOM 染色 + 事件监听。
 *
 * 我们的消息由 Compose 渲染，没有 ST 的 `#chat .mes_text` DOM 节点可染色，
 * 因此这里是 no-op。但**必须存在且不抛错**（`script.js` 启动时会无条件调用）。
 */
export function initLogprobs() {
    // no-op by design（见文件头 DIVERGENCE）
}
