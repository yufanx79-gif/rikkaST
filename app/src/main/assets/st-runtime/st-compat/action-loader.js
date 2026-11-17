/**
 * rikkaST st-compat: action-loader.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/action-loader.js`（约 470 行）。
 *
 * ## ST 原版怎么工作
 * 生成过程中显示「正在生成…」的遮罩层 + toast（带停止按钮），
 * 用 `ActionLoaderHandle` 管理生命周期：
 *   - `loader.show({ blocking, message, onStop })` → handle
 *   - `handle.stop()` → 调 onStop（默认 stopGeneration）后销毁
 *   - `handle.hide()` → 调 onHide 后销毁
 *   - `loader.hide()` → 隐藏全部
 *   - `loader.isBlocking()` → 是否有阻塞遮罩
 *
 * ## 我们的实现
 * 宿主是 Compose，没有 `#loader` DOM 遮罩。但**handle 的生命周期契约必须真实现**：
 * 第三方扩展会 `const h = loader.show(...); ... h.hide();`，
 * 如果 `loader` 是空对象，`loader.show` 就是 undefined → TypeError。
 *
 * 所以这里保留完整语义：
 *  - handle 有 id / slug / isActive / isBlocking，状态机与 ST 一致
 *  - `stop()` 先调 onStop 再销毁；`hide()` 先调 onHide 再销毁
 *  - 已销毁的 handle 再调用是 no-op（ST 也是这个行为）
 *  - `ActionLoaderHandle.EMPTY` 是已销毁的哨兵
 *  - `loader.isBlocking()` 根据活跃 handle 真实计算
 *
 * DIVERGENCE：不建 DOM 遮罩，改为把状态同步给宿主（`RikkaBridge.setActionLoaderState`），
 * 由 Compose 决定怎么展示。宿主没有该桥时静默降级（不抛错）。
 */

export class ActionLoaderHandle {
    /** 已销毁的哨兵 handle（ST `ActionLoaderHandle.EMPTY`）。 */
    static get EMPTY() {
        return new ActionLoaderHandle({ predisposed: true });
    }

    #id = '';
    #slug = null;
    #blocking = true;
    #disposed = false;
    #onStop = null;
    #onHide = null;

    constructor({
        blocking = true,
        toastMode = ActionLoaderToastMode.STOPPABLE,
        slug = null,
        message = 'Generating...',
        title = '',
        stopTooltip = 'Stop',
        overlayContent = null,
        onStop = null,
        onHide = null,
        predisposed = false,
    } = {}) {
        void toastMode; void message; void title; void stopTooltip; void overlayContent;
        if (predisposed) {
            this.#disposed = true;
            return;
        }
        this.#id = generateLoaderId();
        this.#slug = slug;
        this.#blocking = blocking;
        this.#onStop = onStop;
        this.#onHide = onHide;
        activeHandles.add(this);
        syncLoaderState();
    }

    get id() { return this.#id; }
    get slug() { return this.#slug; }
    get isActive() { return !this.#disposed; }
    get isBlocking() { return this.#blocking; }

    /** 触发停止：先 onStop，再销毁（不调 onHide）。 */
    async stop() {
        if (this.#disposed) return;
        if (this.#onStop) {
            try { await this.#onStop(); } catch (e) { console.error('Error executing onStop handler', e); }
        } else {
            // ST 默认调 stopGeneration()；宿主没有该全局时静默
            try {
                if (typeof globalThis.stopGeneration === 'function') globalThis.stopGeneration();
            } catch (_e) { /* noop */ }
        }
        await this.#dispose();
    }

    /** 隐藏：先 onHide，再销毁。 */
    async hide() {
        if (this.#disposed) return;
        if (this.#onHide) {
            try { await this.#onHide(); } catch (e) { console.error('Error executing onHide handler', e); }
        }
        await this.#dispose();
    }

    async #dispose() {
        if (this.#disposed) return;
        this.#disposed = true;
        activeHandles.delete(this);
        syncLoaderState();
    }
}

/** 活跃 handle 集合（ST `activeHandles`）。 */
const activeHandles = new Set();

/** 自增 id 生成器（ST `generateLoaderId`）。 */
let loaderIdCounter = 0;
function generateLoaderId() {
    loaderIdCounter += 1;
    return `action-loader-${Date.now().toString(36)}-${loaderIdCounter}`;
}

/** 把「是否有阻塞 loader」同步给宿主（有桥才同步）。 */
function syncLoaderState() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        if (b && typeof b.setActionLoaderState === 'function') {
            b.setActionLoaderState(activeHandles.size, hasBlockingLoaders());
        }
    } catch (_e) { /* noop */ }
}

/** 是否有活跃的阻塞 handle（ST `hasBlockingLoaders`）。 */
function hasBlockingLoaders() {
    for (const h of activeHandles) {
        if (h.isActive && h.isBlocking) return true;
    }
    return false;
}

/** 遮罩是否正在显示（我们无 DOM 遮罩，等价于有阻塞 handle）。 */
export function isOverlayDisplayed() {
    return hasBlockingLoaders();
}

export const ActionLoaderToastMode = {
    /** No toast is displayed */
    NONE: 'none',
    /** Toast is displayed without stop button (non-interactable) */
    STATIC: 'static',
    /** Toast is displayed with stop button (default) */
    STOPPABLE: 'stoppable',
};

/**
 * 创建并显示一个 action loader（ST `showActionLoader`）。
 * @param {object} [options]
 * @returns {ActionLoaderHandle}
 */
export function showActionLoader(options = {}) {
    return new ActionLoaderHandle(options);
}

/**
 * 隐藏指定 handle，或隐藏全部（ST `hideActionLoader`）。
 * @param {ActionLoaderHandle|null} [handle=null]
 * @returns {Promise<boolean>} 是否真的隐藏了至少一个
 */
export async function hideActionLoader(handle = null) {
    if (handle instanceof ActionLoaderHandle) {
        if (handle.isActive) {
            await handle.hide();
            return true;
        }
        return false;
    }
    const all = getActiveLoaderHandles();
    if (all.length === 0) return false;
    for (const h of all) {
        await h.hide();
    }
    return true;
}

/** 取全部活跃 handle（ST `getActiveLoaderHandles`）。 */
export function getActiveLoaderHandles() {
    return Array.from(activeHandles);
}

/** 按 id 取 handle（ST `getLoaderHandleById`）。 */
export function getLoaderHandleById(id) {
    for (const h of activeHandles) {
        if (h.id === id) return h;
    }
    return undefined;
}

/**
 * 自定义遮罩内容（ST `createDefaultLoaderOverlay`）。
 * 我们无 DOM 遮罩，返回 null 表示「交给宿主渲染」。
 * @returns {null}
 */
export function createDefaultLoaderOverlay() {
    return null;
}

/**
 * 统一入口对象（ST `export const loader`）。
 *
 * ⚠️ 这个对象必须是**真实现**：第三方扩展普遍写
 * `const h = loader.show({...}); ... h.hide();`
 * 导出空对象时 `loader.show` 是 undefined，调用直接 TypeError。
 */
export const loader = {
    show: showActionLoader,
    hide: hideActionLoader,
    active: getActiveLoaderHandles,
    get: getLoaderHandleById,
    isBlocking: isOverlayDisplayed,
    ToastMode: ActionLoaderToastMode,
};
