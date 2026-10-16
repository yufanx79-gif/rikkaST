/**
 * rikkaST st-compat: tags.js
 * ============================================================
 * 对齐 SillyTavern `public/scripts/tags.js`，从当前角色卡与上下文构建标签映射。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

export const tag_map = {};
export const tags = [];

export function getTagKeyForEntity(entityId) {
    if (entityId != null && String(entityId) !== '') return String(entityId);
    const ctx = st();
    return String((ctx && (ctx.characterId ?? ctx.name2)) || 'default');
}

export function getTagMap() {
    try {
        const ctx = st();
        if (ctx && Array.isArray(ctx.characters)) {
            ctx.characters.forEach((ch, idx) => {
                if (ch && Array.isArray(ch.tags)) {
                    const key = ch.avatar || ch.name || String(idx);
                    tag_map[key] = ch.tags.slice();
                }
            });
        }
    } catch (_e) { /* noop */ }
    return tag_map;
}

export function getTagsList(entityId) {
    const map = getTagMap();
    const key = getTagKeyForEntity(entityId);
    return Array.isArray(map[key]) ? map[key] : [];
}
