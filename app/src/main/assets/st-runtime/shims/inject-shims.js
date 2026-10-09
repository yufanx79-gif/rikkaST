/**
 * rikkaST shim: inject-shims.js  [v241]
 * ============================================================
 * JSR（酒馆助手）`injectPrompts` / `uninjectPrompts` —— 注入提示词。
 *
 * 语义来源（逐条对齐）：
 *   D:\rikkaST-refs\C-st-ext\N0VI028__JS-Slash-Runner\src\function\inject.ts
 *   D:\rikkaST-refs\C-st-ext\N0VI028__JS-Slash-Runner\@types\function\inject.d.ts
 *
 * - `injectPrompts(prompts, { once })` → `{ uninject() }`
 * - `uninjectPrompts(ids)`
 * - 作用域 = 当前聊天文件（rikkaST：当前会话；宿主 InjectedPromptStore 按 conversationId 隔离）
 * - `position: 'in_chat'` 插入聊天并发送；`'none'` 不发送，仅 `should_scan` 参与世界书扫描
 * - `once: true` 只影响下一次生成
 *
 * 宿主桥：`window.RikkaBridge.injectPrompts(json)` / `uninjectPrompts(json)`。
 *
 * 差异（已登记 DIVERGENCE §H.4）：官方 `filter` 是 JS 函数、在生成时求值，跨 Kotlin 桥无法传递；
 * 本 shim 忽略 filter（本批真实卡的 filter 恒为 `() => true`，无实际影响）。
 */

/** 宿主桥（不存在时静默降级：只在页面内存里记账，不抛异常） */
function hostBridge() {
    try {
        return (window.RikkaBridge && typeof window.RikkaBridge.injectPrompts === 'function')
            ? window.RikkaBridge
            : null;
    } catch (_e) {
        return null;
    }
}

/** 本页已登记的注入（调试/诊断用；权威数据在宿主 InjectedPromptStore） */
const activeInjections = new Map();

/** JSR InjectionPrompt → 宿主载荷（角色/位置/深度容错对齐 JSR 默认值） */
function normalizePrompt(raw, once) {
    if (!raw || typeof raw !== 'object') return null;
    const id = String(raw.id == null ? '' : raw.id).trim();
    if (!id) return null;
    const depthRaw = Number(raw.depth);
    return {
        id,
        position: raw.position === 'none' ? 'none' : 'in_chat',
        depth: Number.isFinite(depthRaw) && depthRaw > 0 ? Math.floor(depthRaw) : 0,
        role: (raw.role === 'user' || raw.role === 'assistant') ? raw.role : 'system',
        content: String(raw.content == null ? '' : raw.content),
        shouldScan: !!raw.should_scan,
        once: !!once,
    };
}

/**
 * 注入提示词（对齐 JSR inject.ts）。
 * @param {Array<object>} prompts InjectionPrompt 数组
 * @param {{once?: boolean}} [options]
 * @returns {{uninject: () => void}}
 */
export function injectPrompts(prompts, options) {
    const once = !!(options && options.once);
    const list = Array.isArray(prompts) ? prompts : [prompts];
    const normalized = list.map((p) => normalizePrompt(p, once)).filter(Boolean);
    normalized.forEach((p) => activeInjections.set(p.id, p));
    try {
        const bridge = hostBridge();
        if (bridge) bridge.injectPrompts(JSON.stringify(normalized));
    } catch (e) {
        console.warn('[injectPrompts] host bridge failed:', e);
    }
    let deleted = false;
    const uninject = () => {
        if (deleted) return;
        deleted = true;
        uninjectPrompts(normalized.map((p) => p.id));
    };
    return { uninject };
}

/** 移除注入（对齐 JSR uninject.ts：按 id 从 extension_prompts 删除） */
export function uninjectPrompts(ids) {
    const list = (Array.isArray(ids) ? ids : [ids])
        .map((id) => String(id == null ? '' : id).trim())
        .filter(Boolean);
    list.forEach((id) => activeInjections.delete(id));
    try {
        const bridge = hostBridge();
        if (bridge) bridge.uninjectPrompts(JSON.stringify(list));
    } catch (e) {
        console.warn('[uninjectPrompts] host bridge failed:', e);
    }
}
