/**
 * rikkaST st-compat: BulkEditOverlay.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/BulkEditOverlay.js`（891 行）。
 *
 * 这个模块干的事：**角色卡列表的多选批量编辑**。
 * ST 真源（BulkEditOverlay.js:356-390）：
 *  - `BulkEditOverlayState` = `{ browse: 0, select: 1 }` 两个模式常量
 *  - `BulkEditOverlay` 是**单例**（:382 `Object.freeze`），维护
 *    `#selectedCharacters: number[]`、长按判定、状态切换回调、批量打标签
 *  - `CharacterContextMenu` = 角色卡右键菜单
 *
 * ## 我们的实现
 * 选择状态（`selectedCharacters` / `state` / 长按）是**纯数据**，完整移植；
 * DOM 操作（给 `.character_select` 加 `character_selected` class）在宿主侧
 * 由 Compose 的选中态承担，所以这里不碰 DOM。
 *
 * 这样第三方扩展/脚本读 `new BulkEditOverlay().selectedCharacters`
 * 能拿到真实选择集，而不是空数组。
 *
 * DIVERGENCE：ST 的 `containerId` / `characterClass` 等 DOM 常量保留（有人会读），
 * 但不再据此查询 DOM。
 */

/** ST BulkEditOverlay.js:356-367 —— 两个浏览/选择模式。 */
export class BulkEditOverlayState {
    static browse = 0;
    static select = 1;
}

/** 单例（ST :382 `bulkEditOverlayInstance`）。 */
let bulkEditOverlayInstance = null;

/** ST BulkEditOverlay.js:378-390 —— DOM 常量（保留供读取）。 */
class BulkEditOverlayBase {
    static containerId = 'rm_print_characters_block';
    static contextMenuId = 'character_context_menu';
    static characterClass = 'character_select';
    static groupClass = 'group_select';
    static bogusFolderClass = 'bogus_folder_select';
    static selectModeClass = 'group_overlay_mode_select';
    static selectedClass = 'character_selected';
    static legacySelectedClass = 'bulk_select_checkbox';
    static bulkSelectedCountId = 'bulkSelectedCount';
    static longPressDelay = 2500;

    #state = BulkEditOverlayState.browse;
    #longPress = false;
    #stateChangeCallbacks = [];
    #selectedCharacters = [];

    /** ST :402 —— 记住最近一次 toggle 的角色与方向。 */
    lastSelected = { characterId: undefined, select: undefined };

    get state() { return this.#state; }
    set state(value) { this.#state = Number(value); }

    get isLongPress() { return this.#longPress; }
    set isLongPress(v) { this.#longPress = !!v; }

    get stateChangeCallbacks() { return this.#stateChangeCallbacks; }

    /** @returns {number[]} 已选角色 id 列表（ST :458） */
    get selectedCharacters() { return this.#selectedCharacters; }

    constructor() {
        // ⚠️ [v247 BUGFIX] ST 真源确实 `Object.freeze(this)`（BulkEditOverlay.js:472），
        // 但那是**类字段**实现：freeze 后私有字段 #selectedCharacters / lastSelected 仍可改
        // （私有字段走内部槽，不受 freeze 影响）。我们这里是普通属性，freeze 后
        // `lastSelected = {...}` 直接抛 "Cannot assign to read only property"。
        // 所以这里**不 freeze**：单例语义靠下面的 if 保证，可变状态靠属性可写保证。
        if (bulkEditOverlayInstance instanceof BulkEditOverlayBase) return bulkEditOverlayInstance;
        bulkEditOverlayInstance = this;
    }

    /**
     * 切换单个角色的选中态（ST :693-720 的数据部分）。
     *
     * @param {number} characterId 角色 id（ST 从 DOM 的 data-chid 读，我们直接收数字）
     * @param {{markState?: boolean}} [options]
     * @returns {boolean} 切换后是否选中
     */
    toggleSingleCharacter(characterId, { markState = true } = {}) {
        const id = Number(characterId);
        if (!Number.isFinite(id)) return false;
        const select = !this.#selectedCharacters.includes(id);
        if (select) this.#selectedCharacters.push(id);
        else this.#selectedCharacters = this.#selectedCharacters.filter((x) => x !== id);
        if (markState) {
            this.lastSelected.characterId = id;
            this.lastSelected.select = select;
        }
        return select;
    }

    /** 清空选择集。 */
    clearSelection() {
        this.#selectedCharacters = [];
        this.lastSelected = { characterId: undefined, select: undefined };
    }

    /** ST :720 `updateSelectedCount()` —— 更新「已选 N 个」显示。Compose 侧自行观察，这里 no-op。 */
    updateSelectedCount() {
        // no-op by design
    }
}

export class BulkEditOverlay extends BulkEditOverlayBase {}

/**
 * ST `CharacterContextMenu`：角色卡右键菜单。
 * 宿主用 Compose 的 DropdownMenu 承担，这里只保留构造契约（可读不可用）。
 */
export class CharacterContextMenu {
    constructor(...args) {
        this._args = args;
        this.target = null;
    }
    /** ST :840 附近 `show()` —— 宿主无 DOM 菜单 → no-op。 */
    show() { /* no-op by design */ }
    /** ST :860 附近 `hide()` —— no-op。 */
    hide() { /* no-op by design */ }
}
