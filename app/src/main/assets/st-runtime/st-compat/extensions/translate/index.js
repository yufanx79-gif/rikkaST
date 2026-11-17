/**
 * rikkaST st-compat: extensions/translate/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/translate/index.js`（804 行）。
 *
 * ## ST 原版怎么工作
 * `translate(text, lang, provider)` 按 `provider` 分支到 8 个后端：
 * libre / google / lingva / deepl / deeplx / oneringtranslator / bing / yandex，
 * 全部 POST 到酒馆后端的 `/api/translate/*`。长文本用 `chunkedTranslate` 按
 * 5000 / 1500 / 1000 字符切片逐段翻译再拼回。
 *
 * ## 我们的实现
 * 用户明确不做 provider 请求 API（`/api/backends/*`），所以我们**不**复刻 8 个后端，
 * 改为把翻译落到宿主已有的 `TranslationHandler`（`Settings.translateModeId` +
 * `translatePrompt`），与「翻译消息」按钮、TranslatorPage 共用同一个模型和用量记账。
 *
 * 保留了 ST 的两个**行为契约**（第三方扩展依赖它们）：
 *  1. 空文本直接返回 `''`，不发请求；
 *  2. `lang` 为空时回退到 `extension_settings.translate.target_language`。
 * 另外保留了「图片链接不参与翻译」的切分逻辑 —— 这是 ST 原版 456-470 行的真实行为，
 * 丢了它会把 `![img](url)` 的 URL 也送去翻译，返回乱码链接。
 *
 * DIVERGENCE：
 *  - `provider` 参数被接受但忽略（我们只有一个后端）。传任意值不报错，
 *    与 ST 的 `default: console.error + return text` 相比更宽容，但返回值形态一致。
 *  - ST 失败时返回 `undefined`；我们返回原文，避免 `undefined` 传染给调用方。
 */

export const autoModeOptions = {
    NONE: 'none',
    RESPONSES: 'responses',
    INPUT: 'inputs',
    BOTH: 'both',
};

/** ST 原版的 provider 名（保留给 UI 下拉框读取，不代表我们实现了它们）。 */
export const translationProviders = [
    'libre', 'google', 'lingva', 'deepl', 'deeplx', 'oneringtranslator', 'bing', 'yandex',
];

/** 取桥；不存在时返回 null（面板模式 / 单元测试环境）。 */
function bridge() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        return (b && typeof b.translateText === 'function') ? b : null;
    } catch (_e) {
        return null;
    }
}

/** 取 `extension_settings.translate`（ST 真源，st-compat/extensions.js 提供）。 */
function translateSettings() {
    try {
        const es = (typeof globalThis !== 'undefined') ? globalThis.extension_settings : null;
        if (es && typeof es === 'object') {
            if (!es.translate || typeof es.translate !== 'object') es.translate = {};
            return es.translate;
        }
    } catch (_e) { /* noop */ }
    return {};
}

/**
 * 把文本按「图片链接」切开，只翻译纯文本段。
 * 对齐 ST `index.js:456-470`：
 *   chunks = text.split(/!\[.*?]\([^)]*\)/)
 *   翻译每段，再把原始图片链接按原顺序插回。
 *
 * @param {string} text
 * @param {(s: string) => Promise<string>} fn
 * @returns {Promise<string>}
 */
async function translateSkippingImages(text, fn) {
    const re = /!\[.*?]\([^)]*\)/g;
    const chunks = text.split(re);
    const links = [...text.matchAll(re)];
    let out = '';
    for (let i = 0; i < chunks.length; i++) {
        out += await fn(chunks[i]);
        if (i < links.length) out += links[i][0];
    }
    return out;
}

/**
 * 翻译文本（ST 公开导出）。
 *
 * @param {string} text 待翻译文本
 * @param {string} [lang] 目标语言码；空则取 extension_settings.translate.target_language
 * @param {string} [provider] ST 兼容参数，我们忽略（只有一个后端）
 * @returns {Promise<string>} 译文；无桥/失败时返回原文
 */
export async function translate(text, lang, provider = null) {
    void provider; // ST 兼容参数，见文件头 DIVERGENCE
    if (typeof text !== 'string' || text === '') return '';

    const settings = translateSettings();
    let target = lang;
    if (!target) target = settings.target_language || '';
    if (!target) target = 'zh-CN';

    const b = bridge();
    if (!b) {
        // 无桥（面板模式）：返回原文而不是 null，保证调用方拿到字符串。
        return text;
    }

    try {
        const result = await translateSkippingImages(text, async (chunk) => {
            if (!chunk) return chunk;
            // 纯空白 / 纯标点不浪费一次请求
            if (!/\S/.test(chunk)) return chunk;
            try {
                const r = b.translateText(chunk, target);
                return (typeof r === 'string' && r.length > 0) ? r : chunk;
            } catch (_e) {
                return chunk;
            }
        });
        return result || text;
    } catch (_e) {
        return text;
    }
}

/**
 * ST 扩展入口。原版在这里建设置面板 DOM；我们由宿主的翻译设置页承担。
 * 只做一件事：确保 `extension_settings.translate` 存在且字段齐全，
 * 否则 `translate()` 读 `target_language` 会 undefined。
 */
export async function init() {
    const settings = translateSettings();
    const defaults = {
        provider: 'oneringtranslator',
        target_language: 'zh-CN',
        internal_language: 'en',
        auto_mode: autoModeOptions.NONE,
        keep_original_text: false,
        show_original_text: false,
        show_translated_text: true,
    };
    for (const [k, v] of Object.entries(defaults)) {
        if (settings[k] === undefined) settings[k] = v;
    }
    return true;
}
