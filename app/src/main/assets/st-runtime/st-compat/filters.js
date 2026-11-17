/**
 * rikkaST st-compat: filters.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/filters.js`（约 470 行）。
 *
 * ## ST 原版怎么工作
 * `FilterHelper` 是角色卡 / 世界书 / 人设 / 群组列表的**过滤引擎**：
 *  - 7 种过滤类型（搜索 / 标签 / 目录 / 收藏 / 群组 / 世界书搜索 / 人设搜索）
 *  - 每种类型有一个 filter 函数，`applyFilters()` 按顺序 reduce 一遍
 *  - 三态过滤（SELECTED 只保留 / EXCLUDED 排除 / UNDEFINED 不过滤）
 *  - 模糊搜索的分数会缓存起来供排序用
 *
 * ## 为什么必须真实现
 * `group-chats.js` / `personas.js` / `world-info.js` 都在模块顶层
 * `export const xxxFilter = new FilterHelper(...)`。如果 `FilterHelper`
 * 是空壳，这些 filter 对象就没有任何方法 —— 调用 `applyFilters()` 直接 TypeError。
 *
 * ## 与 ST 的差异
 *  - ST 的模糊搜索调 `power-user.js` 的 `fuzzySearchCharacters` 等，那些函数依赖
 *    全局角色表 / 世界书表。我们改成「对传入 data 做字段匹配」的自包含实现，
 *    语义一致（子序列匹配 + 分数），但不依赖外部状态。
 *  - ST 的 `tag_map` 从 `tags.js` 读；我们优先读全局 `tag_map`，缺失时返回 false。
 */

export const FILTER_TYPES = {
    SEARCH: 'search',
    TAG: 'tag',
    FOLDER: 'folder',
    FAV: 'fav',
    GROUP: 'group',
    WORLD_INFO_SEARCH: 'world_info_search',
    PERSONA_SEARCH: 'persona_search',
};

export const FILTER_STATES = {
    SELECTED: { key: 'SELECTED', class: 'selected' },
    EXCLUDED: { key: 'EXCLUDED', class: 'excluded' },
    UNDEFINED: { key: 'UNDEFINED', class: 'undefined' },
};

/** 默认过滤状态（必须定义在 FILTER_STATES 之后，否则 TDZ）。 */
export const DEFAULT_FILTER_STATE = FILTER_STATES.UNDEFINED.key;

export const fuzzySearchCategories = Object.freeze({
    characters: 'characters',
    worldInfo: 'worldInfo',
    personas: 'personas',
    tags: 'tags',
    groups: 'groups',
});

/**
 * 三态比较：接受 state key 字符串或 state 对象。
 * @param {object|string} a
 * @param {object|string} b
 * @returns {boolean}
 */
export function isFilterState(a, b) {
    const states = Object.keys(FILTER_STATES);
    const aKey = typeof a == 'string' && states.includes(a) ? a : states.find(key => FILTER_STATES[key] === a);
    const bKey = typeof b == 'string' && states.includes(b) ? b : states.find(key => FILTER_STATES[key] === b);
    return aKey === bKey;
}

/** 取全局 tag_map（tags.js 导出；不存在时返回空对象）。 */
function tagMap() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        return (g && g.tag_map && typeof g.tag_map === 'object') ? g.tag_map : {};
    } catch (_e) {
        return {};
    }
}

/** 取 power_user.fuzzy_search 开关。 */
function fuzzyEnabled() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        return !!(g && g.power_user && g.power_user.fuzzy_search);
    } catch (_e) {
        return false;
    }
}

/**
 * 自包含的模糊匹配：返回 [0,1] 分数，0 = 不匹配。
 *
 * 规则（与 ST 的 fuzzy 语义一致）：
 *  - 完整子串包含 → 高分（越长占比越高）
 *  - 否则按子序列匹配（字符顺序出现即可）→ 分数按跳跃次数衰减
 *
 * @param {string} needle
 * @param {string} haystack
 * @returns {number}
 */
function fuzzyScore(needle, haystack) {
    const n = String(needle ?? '').toLowerCase();
    const h = String(haystack ?? '').toLowerCase();
    if (!n) return 0;
    if (!h) return 0;
    if (h.includes(n)) {
        return 1 - (h.length - n.length) / Math.max(h.length, 1) * 0.5;
    }
    let i = 0;
    let gaps = 0;
    let last = -1;
    for (let j = 0; j < h.length && i < n.length; j++) {
        if (h[j] === n[i]) {
            if (last >= 0) gaps += j - last - 1;
            last = j;
            i++;
        }
    }
    if (i < n.length) return 0;
    const penalty = gaps / Math.max(h.length, 1);
    return Math.max(0.01, (n.length / Math.max(h.length, 1)) * (1 - penalty));
}

/** 从任意实体上取一个可搜索的显示名。 */
function entityName(entity) {
    if (entity == null) return '';
    if (typeof entity === 'string') return entity;
    const item = entity.item ?? entity;
    return String(item.name ?? item.key ?? item.title ?? item.comment ?? item.avatar ?? '');
}

export class FilterHelper {
    /**
     * @param {Function} [onDataChanged] 过滤条件变化时的回调
     */
    constructor(onDataChanged) {
        this.onDataChanged = typeof onDataChanged === 'function' ? onDataChanged : () => {};

        /** @type {Map<string, Map<string|number, number>>} */
        this.scoreCache = new Map();

        /** @type {Object.<string,{resultMap:Map<string,any>}>} */
        this.fuzzySearchCaches = {
            [fuzzySearchCategories.characters]: { resultMap: new Map() },
            [fuzzySearchCategories.worldInfo]: { resultMap: new Map() },
            [fuzzySearchCategories.personas]: { resultMap: new Map() },
            [fuzzySearchCategories.tags]: { resultMap: new Map() },
            [fuzzySearchCategories.groups]: { resultMap: new Map() },
        };

        /** @type {Object.<string,any>} */
        this.filterData = {
            [FILTER_TYPES.SEARCH]: '',
            [FILTER_TYPES.FAV]: false,
            [FILTER_TYPES.GROUP]: false,
            [FILTER_TYPES.FOLDER]: false,
            [FILTER_TYPES.TAG]: { excluded: [], selected: [] },
            [FILTER_TYPES.WORLD_INFO_SEARCH]: '',
            [FILTER_TYPES.PERSONA_SEARCH]: '',
        };

        this.filterFunctions = {
            [FILTER_TYPES.SEARCH]: this.searchFilter.bind(this),
            [FILTER_TYPES.FAV]: this.favFilter.bind(this),
            [FILTER_TYPES.GROUP]: this.groupFilter.bind(this),
            [FILTER_TYPES.FOLDER]: this.folderFilter.bind(this),
            [FILTER_TYPES.TAG]: this.tagFilter.bind(this),
            [FILTER_TYPES.WORLD_INFO_SEARCH]: this.wiSearchFilter.bind(this),
            [FILTER_TYPES.PERSONA_SEARCH]: this.personaSearchFilter.bind(this),
        };
    }

    /** 是否有任何生效的过滤条件。 */
    hasAnyFilter() {
        function checkRecursive(obj) {
            if (typeof obj === 'string' && obj.length > 0 && obj !== 'UNDEFINED') return true;
            if (typeof obj === 'boolean' && obj) return true;
            if (Array.isArray(obj) && obj.length > 0) return true;
            if (typeof obj === 'object' && obj !== null) {
                for (const key in obj) {
                    if (checkRecursive(obj[key])) return true;
                }
            }
            return false;
        }
        return checkRecursive(this.filterData);
    }

    /** 世界书模糊搜索过滤。 */
    wiSearchFilter(data) {
        const term = this.filterData[FILTER_TYPES.WORLD_INFO_SEARCH];
        if (!term) return data;
        const scored = data
            .map(entity => {
                const uid = entity?.item?.uid ?? entity?.uid;
                const score = fuzzyScore(term, entityName(entity) + ' ' + String(entity?.item?.content ?? entity?.content ?? ''));
                return { entity, uid, score };
            })
            .filter(x => x.score > 0);
        this.cacheScores(FILTER_TYPES.WORLD_INFO_SEARCH, new Map(scored.map(x => [x.uid, x.score])));
        return scored.map(x => x.entity);
    }

    /** 人设模糊搜索过滤。 */
    personaSearchFilter(data) {
        const term = this.filterData[FILTER_TYPES.PERSONA_SEARCH];
        if (!term) return data;
        const scored = data
            .map(name => ({ name, score: fuzzyScore(term, name) }))
            .filter(x => x.score > 0);
        this.cacheScores(FILTER_TYPES.PERSONA_SEARCH, new Map(scored.map(x => [x.name, x.score])));
        return scored.map(x => x.name);
    }

    /** 实体是否带某标签。 */
    isElementTagged(entity, tagId) {
        const isCharacter = entity?.type === 'character';
        const lookupValue = isCharacter ? entity.item?.avatar : String(entity?.id);
        const map = tagMap();
        const arr = map[lookupValue];
        return Array.isArray(arr) && arr.includes(tagId);
    }

    /** 标签过滤（AND 逻辑 + 排除）。 */
    tagFilter(data) {
        const { selected = [], excluded = [] } = this.filterData[FILTER_TYPES.TAG] ?? {};
        if (!selected.length && !excluded.length) return data;

        const getIsTagged = (entity) => {
            const isTag = entity?.type === 'tag';
            const tagFlags = selected.map(tagId => this.isElementTagged(entity, tagId));
            const trueFlags = tagFlags.filter(x => x);
            const isTagged = tagFlags.length === trueFlags.length;

            const excludedTagFlags = excluded.map(tagId => this.isElementTagged(entity, tagId));
            const isExcluded = excludedTagFlags.includes(true);

            if (isTag) return true;
            if (isExcluded) return false;
            if (selected.length > 0 && !isTagged) return false;
            return true;
        };

        return data.filter(entity => getIsTagged(entity));
    }

    /** 收藏过滤。 */
    favFilter(data) {
        const state = this.filterData[FILTER_TYPES.FAV];
        const isFav = entity => {
            const item = entity?.item ?? {};
            return !!(item.fav || item.fav == 'true');
        };
        return this.filterDataByState(data, state, isFav, { includeFolders: true });
    }

    /** 群组过滤。 */
    groupFilter(data) {
        const state = this.filterData[FILTER_TYPES.GROUP];
        const isGroup = entity => entity?.type === 'group';
        return this.filterDataByState(data, state, isGroup, { includeFolders: true });
    }

    /** 目录过滤。 */
    folderFilter(data) {
        const state = this.filterData[FILTER_TYPES.FOLDER];
        const isFolder = entity => entity?.type === 'tag';
        return this.filterDataByState(data, state, isFolder);
    }

    /** 三态过滤核心。 */
    filterDataByState(data, state, filterFunc, { includeFolders = false } = {}) {
        if (isFilterState(state, FILTER_STATES.SELECTED)) {
            return data.filter(entity => filterFunc(entity) || (includeFolders && entity?.type == 'tag'));
        }
        if (isFilterState(state, FILTER_STATES.EXCLUDED)) {
            return data.filter(entity => !filterFunc(entity) || (includeFolders && entity?.type == 'tag'));
        }
        return data;
    }

    /** 通用搜索过滤。 */
    searchFilter(data) {
        if (!this.filterData[FILTER_TYPES.SEARCH]) return data;
        const searchValue = this.filterData[FILTER_TYPES.SEARCH];
        const useFuzzy = fuzzyEnabled();

        const scored = data
            .map((entity, refIndex) => {
                const name = entityName(entity);
                const score = useFuzzy
                    ? fuzzyScore(searchValue, name)
                    : (name.toLowerCase().includes(String(searchValue).toLowerCase()) ? 1 : 0);
                return { entity, refIndex, score };
            })
            .filter(x => x.score > 0)
            .sort((a, b) => b.score - a.score);

        if (useFuzzy) {
            this.cacheScores(FILTER_TYPES.SEARCH, new Map(scored.map(x => [`character.${x.refIndex}`, x.score])));
        }
        return scored.map(x => x.entity);
    }

    /** 设置某类过滤条件；值真变了才回调。 */
    setFilterData(filterType, data, suppressDataChanged = false) {
        const oldData = this.filterData[filterType];
        this.filterData[filterType] = data;
        if (JSON.stringify(oldData) !== JSON.stringify(data) && !suppressDataChanged) {
            this.onDataChanged();
        }
    }

    /** 读某类过滤条件。 */
    getFilterData(filterType) {
        return this.filterData[filterType];
    }

    /** 应用全部过滤。 */
    applyFilters(data, { clearScoreCache = true, tempOverrides = {}, clearFuzzySearchCaches = true } = {}) {
        if (clearScoreCache) this.clearScoreCache();
        if (clearFuzzySearchCaches) this.clearFuzzySearchCaches();

        const originalStates = {};
        for (const key in tempOverrides) {
            originalStates[key] = this.filterData[key];
            this.filterData[key] = tempOverrides[key];
        }

        try {
            return Object.values(this.filterFunctions).reduce((acc, fn) => fn(acc), data);
        } finally {
            // 无论成功失败都要还原临时覆盖，否则下一次过滤会串味
            for (const key in originalStates) {
                this.filterData[key] = originalStates[key];
            }
        }
    }

    /** 缓存搜索分数。 */
    cacheScores(type, results) {
        const typeScores = this.scoreCache.get(type) || new Map();
        for (const [uid, score] of results) {
            typeScores.set(uid, score);
        }
        this.scoreCache.set(type, typeScores);
    }

    /** 读缓存分数。 */
    getScore(type, uid) {
        return this.scoreCache.get(type)?.get(uid) ?? undefined;
    }

    /** 清分数缓存（不传 type = 全清）。 */
    clearScoreCache(type) {
        if (type) {
            this.scoreCache.set(type, new Map());
        } else {
            this.scoreCache = new Map();
        }
    }

    /** 清模糊搜索缓存。 */
    clearFuzzySearchCaches() {
        for (const cache of Object.values(this.fuzzySearchCaches)) {
            cache.resultMap.clear();
        }
    }
}
