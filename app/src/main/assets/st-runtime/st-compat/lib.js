/**
 * rikkaST st-compat: lib.js
 * ============================================================
 * ST 根级 `lib.js` 导出面（第三方扩展经 `../../../../lib.js` import）。
 * 已提供：jQuery（$）、lodash（_）、yaml（本地 vendor ESM）。
 */

import * as yamlModule from '/st-runtime/vendor/lib/yaml.esm.js';

export const $ = window.jQuery;
export const jQuery = window.jQuery;
export const _ = window._;
export const lodash = window._;

export const yaml = (yamlModule && yamlModule.default) ? yamlModule.default : yamlModule;

// ── [batch16] 补齐第三方扩展真正 import 的导出面 ─────────
// 依据：refdeps\st-memory-enhancement\services\appFuncManager.js:3
//   `import { DOMPurify, Bowser, slideToggle } from '/lib.js';`
// ESM 具名导出缺失 = 整条模块图解析失败 = 扩展整体不加载（真机日志：
//   `[third-party] 加载失败: st-memory-enhancement/index.js ... SyntaxError: The requested module '/lib.js' does not provide an export named 'Bowser'`）
// 与 ST 一致：lib.js 从 dompurify / bowser 再导出；两者已 vendored 到 vendor/lib（纯 ESM，本地离线）。
import * as dompurifyModule from '/st-runtime/vendor/lib/dompurify.esm.js';
import * as bowserModule from '/st-runtime/vendor/lib/bowser.esm.js';

/** DOMPurify 实例（ST lib.js 同样从 dompurify 再导出）。 */
export const DOMPurify = (dompurifyModule && dompurifyModule.default) ? dompurifyModule.default : dompurifyModule;

/** Bowser UA 解析器（ST lib.js 同样从 bowser 再导出）。 */
export const Bowser = (bowserModule && bowserModule.default) ? bowserModule.default : bowserModule;

/** ST lib.js 的 slideToggle(target, ...args)：滑动显隐，jQuery 在则用 jQuery。 */
export function slideToggle(target, ...args) {
    try {
        if (target && typeof target.slideToggle === 'function') return target.slideToggle(...args);
        const jq = window.jQuery;
        if (jq) {
            const $el = jq(target);
            if ($el && $el.length && typeof $el.slideToggle === 'function') return $el.slideToggle(...args);
        }
        const node = (target && target.nodeType === 1) ? target : (typeof target === 'string' ? document.querySelector(target) : null);
        if (node) {
            const hidden = (node.style.display === 'none' || (!node.style.display && node.offsetParent === null));
            node.style.display = hidden ? '' : 'none';
        }
        return target;
    } catch (err) {
        console.warn('[lib.js] slideToggle failed:', err);
        return target;
    }
}

// 批次十七：ST 官方 lib.js 有 default 导出（`import lib from '../../../../lib.js'` 的扩展要用）
export default { $, jQuery, _, lodash, yaml, DOMPurify, Bowser, slideToggle };
