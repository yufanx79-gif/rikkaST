/**
 * rikkaST st-compat: filters.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export const FILTER_STATES = {
    SELECTED: { key: 'SELECTED', class: 'selected' },
    EXCLUDED: { key: 'EXCLUDED', class: 'excluded' },
    UNDEFINED: { key: 'UNDEFINED', class: 'undefined' },
};
export const DEFAULT_FILTER_STATE = FILTER_STATES.UNDEFINED.key;
export const FILTER_TYPES = {
    SEARCH: 'search',
    TAG: 'tag',
    FOLDER: 'folder',
    FAV: 'fav',
    GROUP: 'group',
    WORLD_INFO_SEARCH: 'world_info_search',
    PERSONA_SEARCH: 'persona_search',
};
export class FilterHelper { constructor(...args) { this._args = args; } }
export const fuzzySearchCategories = Object.freeze({
    characters: 'characters',
    worldInfo: 'worldInfo',
    personas: 'personas',
    tags: 'tags',
    groups: 'groups',
});
export function isFilterState(a, b) {
    const states = Object.keys(FILTER_STATES);

    const aKey = typeof a == 'string' && states.includes(a) ? a : states.find(key => FILTER_STATES[key] === a);
    const bKey = typeof b == 'string' && states.includes(b) ? b : states.find(key => FILTER_STATES[key] === b);

    return aKey === bKey;
}
