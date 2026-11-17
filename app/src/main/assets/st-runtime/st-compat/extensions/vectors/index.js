/**
 * rikkaST st-compat: extensions/vectors/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/vectors/index.js`（2358 行）。
 *
 * ## ST 原版怎么工作
 * 这个扩展做「语义检索」：
 *  1. `synchronizeChat()` 把聊天消息切片、算 hash、插入向量库；
 *  2. `rearrangeChat()` 在生成前按语义相似度召回相关历史消息 / 文件 / 世界书条目，
 *     注入成 `EXTENSION_PROMPT_TAG` 的提示词；
 *  3. `activateWorldInfo()` 对 `vectorized` 的世界书条目做语义激活；
 *  4. 所有向量操作 POST 到 `/api/vector/*`（list / insert / delete / query / query-multi）。
 *
 * ## 我们的实现
 * 宿主**已经**有完整的 embedding 能力：
 *  - `KnowledgeBaseService` 有 `generateEmbedding` + 余弦检索 + LRU 缓存；
 *  - `PromptInjectionTransformer` 本轮接上了真 embedding 的世界书向量激活。
 *
 * 所以本模块把 ST 的 5 个向量 API 落到宿主桥 `vectorOp(op, payloadJson)` 上，
 * 由 Kotlin 侧用**同一个 embedding 模型**（`Settings.embeddingModelId`）执行，
 * 集合数据存进宿主沙箱（`st-user-files/vectors/<collectionId>.json`），
 * 不依赖酒馆后端。
 *
 * ## 保留的行为契约
 *  - `getSavedHashes` 返回 number[]（ST 的 hash 是 cyrb53 变体，我们用同一个函数）
 *  - `queryCollection` 返回 `{hashes, metadata}`
 *  - `queryMultipleCollections` 返回 `{[collectionId]: {hashes, metadata}}`
 *  - 空 collection / 空文本返回空结果而不是抛错（ST 也是这个行为）
 *
 * DIVERGENCE：ST 支持 30+ 个 embedding 源（OpenAI/Ollama/WebLLM/Cohere…）；
 * 我们只用宿主配置的那一个（OpenAI 兼容 / Google / Anthropic 的 embedding 端点）。
 * 用户明确不做 `/api/backends/*`，所以 `settings.source` 等字段接受但忽略。
 */

export const MODULE_NAME = 'vectors';
export const EXTENSION_PROMPT_TAG = '3_vectors';
export const EXTENSION_PROMPT_TAG_DB = '4_vectors_data_bank';

/** 取桥；不存在时返回 null。 */
function bridge() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        return (b && typeof b.vectorOp === 'function') ? b : null;
    } catch (_e) {
        return null;
    }
}

/**
 * 统一的向量操作入口。
 * @param {string} op list | insert | delete | query | query-multi | purge
 * @param {object} payload
 * @returns {any} 解析后的结果；失败返回 null
 */
function vectorCall(op, payload) {
    const b = bridge();
    if (!b) return null;
    try {
        const raw = b.vectorOp(op, JSON.stringify(payload || {}));
        if (typeof raw !== 'string' || !raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && parsed.ok === false) return null;
        return parsed && Object.prototype.hasOwnProperty.call(parsed, 'data') ? parsed.data : parsed;
    } catch (_e) {
        return null;
    }
}

/**
 * ST `index.js:556 getStringHash`（cyrb53 变体）。
 * 与酒馆 `utils.js:522` 的 `getStringHash` 完全一致 —— 集合里的 hash
 * 必须和 ST 算出来的一样，否则导入的旧索引对不上。
 *
 * @param {string} str
 * @param {number} [seed]
 * @returns {number}
 */
export function getStringHash(str, seed = 0) {
    if (typeof str !== 'string') str = String(str ?? '');
    let h1 = 0xdeadbeef ^ seed;
    let h2 = 0x41c6ce57 ^ seed;
    for (let i = 0; i < str.length; i++) {
        const ch = str.charCodeAt(i);
        h1 = Math.imul(h1 ^ ch, 2654435761);
        h2 = Math.imul(h2 ^ ch, 1597334677);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
    h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
    return 4294967296 * (2097151 & h2) + (h1 >>> 0);
}

/**
 * 取集合里已存的所有 hash（ST `getSavedHashes`）。
 * @param {string} collectionId
 * @returns {Promise<number[]>}
 */
export async function getSavedHashes(collectionId) {
    const data = vectorCall('list', { collectionId });
    return Array.isArray(data) ? data : [];
}

/**
 * 插入向量条目（ST `insertVectorItems`）。
 * @param {string} collectionId
 * @param {{hash:number, text:string, index?:number}[]} items
 * @returns {Promise<boolean>}
 */
export async function insertVectorItems(collectionId, items) {
    if (!collectionId || !Array.isArray(items) || items.length === 0) return false;
    return vectorCall('insert', { collectionId, items }) !== null;
}

/**
 * 删除指定 hash（ST `deleteVectorItems`）。
 * @param {string} collectionId
 * @param {number[]} hashes
 * @returns {Promise<boolean>}
 */
export async function deleteVectorItems(collectionId, hashes) {
    if (!collectionId || !Array.isArray(hashes) || hashes.length === 0) return false;
    return vectorCall('delete', { collectionId, hashes }) !== null;
}

/**
 * 查询单个集合（ST `queryCollection`）。
 * @param {string} collectionId
 * @param {string} searchText
 * @param {number} topK
 * @returns {Promise<{hashes:number[], metadata:object[]}>}
 */
export async function queryCollection(collectionId, searchText, topK) {
    const empty = { hashes: [], metadata: [] };
    if (!collectionId || !searchText) return empty;
    const data = vectorCall('query', {
        collectionId,
        searchText,
        topK: Number(topK) || 5,
    });
    if (!data || typeof data !== 'object') return empty;
    return {
        hashes: Array.isArray(data.hashes) ? data.hashes : [],
        metadata: Array.isArray(data.metadata) ? data.metadata : [],
    };
}

/**
 * 跨集合查询（ST `queryMultipleCollections`）。
 * @param {string[]} collectionIds
 * @param {string} searchText
 * @param {number} topK
 * @param {number} [threshold]
 * @returns {Promise<Record<string,{hashes:number[],metadata:object[]}>>}
 */
export async function queryMultipleCollections(collectionIds, searchText, topK, threshold) {
    const out = {};
    if (!Array.isArray(collectionIds) || collectionIds.length === 0 || !searchText) return out;
    const data = vectorCall('query-multi', {
        collectionIds,
        searchText,
        topK: Number(topK) || 5,
        threshold: (typeof threshold === 'number') ? threshold : undefined,
    });
    if (!data || typeof data !== 'object') return out;
    for (const id of collectionIds) {
        const one = data[id];
        out[id] = {
            hashes: Array.isArray(one?.hashes) ? one.hashes : [],
            metadata: Array.isArray(one?.metadata) ? one.metadata : [],
        };
    }
    return out;
}

/**
 * 清空一个集合（ST `purgeVectorIndex`）。
 * @param {string} collectionId
 * @returns {Promise<boolean>}
 */
export async function purgeVectorIndex(collectionId) {
    if (!collectionId) return false;
    return vectorCall('purge', { collectionId }) !== null;
}

/**
 * 世界书语义激活（ST `index.js:1623 activateWorldInfo`）。
 *
 * ST 原版：遍历所有启用的世界书条目 → 按 world 分组 → 同步集合 →
 * 多集合查询 → 命中的条目通过 `WORLDINFO_FORCE_ACTIVATE` 事件强制激活。
 *
 * 我们：世界书的语义激活**已经在 Kotlin 侧完成**
 * （`PromptInjectionTransformer` 里 `entry.vectorized` 分支，本轮接上了真 embedding）。
 * 这里保留函数是为了 ST 的调用点不炸，并返回真实统计（而不是假装成功）。
 *
 * @param {Array} chat
 * @returns {Promise<{activated:number, reason:string}>}
 */
export async function activateWorldInfo(chat) {
    void chat;
    const b = bridge();
    if (!b) return { activated: 0, reason: 'bridge_unavailable' };
    return { activated: 0, reason: 'handled_by_host' };
}

/**
 * ST 扩展入口。原版建设置面板 DOM；我们由宿主「设置 → 知识库 / 模型」承担。
 * 这里只保证 `extension_settings.vectors` 存在且字段齐全。
 * @returns {Promise<boolean>}
 */
export async function init() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        const es = g ? g.extension_settings : null;
        if (es && typeof es === 'object') {
            if (!es.vectors || typeof es.vectors !== 'object') es.vectors = {};
            const defaults = {
                source: 'transformers',
                enabled_chats: false,
                enabled_files: false,
                enabled_world_info: false,
                enabled_for_all: false,
                max_entries: 5,
                score_threshold: 0.25,
                chunk_size: 400,
                overlap_percent: 0,
                retrieve_context_percent: 100,
                force_chunk_delimiter: false,
            };
            for (const [k, v] of Object.entries(defaults)) {
                if (es.vectors[k] === undefined) es.vectors[k] = v;
            }
        }
    } catch (_e) { /* noop */ }
    return true;
}
