/**
 * rikkaST st-compat: extensions/caption/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/caption/index.js`（824 行）。
 *
 * 这个扩展干的事：**给图片生成文字描述（caption）**，然后把描述注入到聊天里。
 * ST 真源的三条路径（caption/index.js）：
 *  - `local`    → 本地多模态模型
 *  - `extras`   → ST-Extras 后端
 *  - `multimodal` / `pollinations` → 走 oai_settings 的视觉模型
 *
 * ## 我们的实现
 * 宿主已有等价能力：`OcrTransformer.kt`（`Settings.ocrModelId` + `ocrPrompt`）
 * 走 OpenAI/Google/Anthropic 三家的多模态通道。所以这里：
 *  - `init()` 只做「确认宿主 OCR 可用」，不绑 DOM（我们是 Compose UI）
 *  - `captionImage(base64OrUrl, prompt)` 经宿主桥 `captionImage` 真出描述
 *  - 保留 ST 的 `MODULE_NAME` / `PROMPT_DEFAULT` / `TEMPLATE_DEFAULT` 常量
 *
 * DIVERGENCE：ST 的 `init()` 会往设置页塞一大坨 jQuery 表单；
 * 我们的设置页由 Compose 渲染（SettingModelPage 里已有 OCR 模型/提示词配置）。
 */

export const MODULE_NAME = 'caption';

/** ST caption/index.js:29 —— 默认提问。 */
export const PROMPT_DEFAULT = "What's in this image?";

/** ST caption/index.js:30 —— 默认注入模板。 */
export const TEMPLATE_DEFAULT = '[{{user}} sends {{char}} a picture that contains: {{caption}}]';

/**
 * 给图片生成描述。
 *
 * @param {string} image 图片 URL / data URL / base64
 * @param {string} [prompt] 提问（默认 PROMPT_DEFAULT）
 * @returns {Promise<string|null>} 描述文本；失败返回 null（与 ST 一致）
 */
export async function captionImage(image, prompt = PROMPT_DEFAULT) {
    const src = String(image == null ? '' : image).trim();
    if (!src) return null;
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        if (!b || typeof b.captionImage !== 'function') return null;
        const out = b.captionImage(src, String(prompt || PROMPT_DEFAULT));
        if (typeof out === 'string' && out.trim()) return out.trim();
    } catch (_e) { /* 宿主不可用 → null */ }
    return null;
}

/**
 * ST 扩展入口。ST 里它负责：注入设置页、绑定事件、注册斜杠命令。
 *
 * 我们：
 *  - 设置页由 Compose 承担（SettingModelPage 的 OCR 模型 + 提示词）
 *  - `/caption` 斜杠命令由 Kotlin StSlashExecutor 承担
 * 所以这里只做「宿主能力自检」，不抛错。
 */
export async function init() {
    try {
        const b = (typeof window !== 'undefined') ? window.RikkaBridge : null;
        const available = !!(b && typeof b.captionImage === 'function');
        if (!available && typeof console !== 'undefined') {
            console.debug('[caption] host captionImage bridge not available; /caption will be a no-op');
        }
        return available;
    } catch (_e) {
        return false;
    }
}
