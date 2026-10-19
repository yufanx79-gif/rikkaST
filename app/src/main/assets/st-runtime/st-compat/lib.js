/**
 * rikkaST st-compat: lib.js
 * ============================================================
 * ST 1.19.0 根级 `lib.js` 导出面（第三方扩展经 `../../../../lib.js` 或 `/lib.js` import）。
 * 已提供：jQuery（$）、lodash（_）、yaml、DOMPurify、Bowser、slideToggle 以及 ST 1.19.0 其余库导出。
 */

import * as yamlModule from '/st-runtime/vendor/lib/yaml.esm.js';
import * as dompurifyModule from '/st-runtime/vendor/lib/dompurify.esm.js';
import * as bowserModule from '/st-runtime/vendor/lib/bowser.esm.js';

const g = typeof globalThis !== 'undefined' ? globalThis : (typeof window !== 'undefined' ? window : {});

export const $ = g.jQuery || g.$;
export const jQuery = g.jQuery || g.$;
export const _ = g._;
export const lodash = g._;

export const yaml = (yamlModule && yamlModule.default) ? yamlModule.default : yamlModule;
export const DOMPurify = (dompurifyModule && dompurifyModule.default) ? dompurifyModule.default : dompurifyModule;
export const Bowser = (bowserModule && bowserModule.default) ? bowserModule.default : bowserModule;

export const hljs = g.hljs || {
    highlight: (code) => ({ value: String(code ?? '') }),
    highlightElement: () => {},
    highlightAll: () => {},
    getLanguage: () => null,
    listLanguages: () => [],
};

export const showdown = g.showdown || {
    Converter: class Converter {
        makeHtml(text) { return String(text ?? ''); }
    },
    extension: () => {},
};

export const moment = g.moment || function momentShim(input) {
    const d = input ? new Date(input) : new Date();
    return {
        toDate: () => d,
        valueOf: () => d.getTime(),
        toISOString: () => d.toISOString(),
        format: () => d.toISOString(),
        fromNow: () => '',
        isValid: () => !Number.isNaN(d.getTime()),
    };
};

export const Handlebars = g.Handlebars || {
    compile: (tpl) => (data = {}) => {
        let html = String(tpl ?? '');
        if (data && typeof data === 'object') {
            html = html.replace(/\{\{\{\s*([\w.$]+)\s*\}\}\}/g, (m, k) => (k in data ? String(data[k]) : m));
            html = html.replace(/\{\{\s*([\w.$]+)\s*\}\}/g, (m, k) => (k in data ? String(data[k]) : m));
        }
        return html;
    },
    registerHelper: () => {},
    SafeString: class SafeString {
        constructor(str) { this.string = String(str ?? ''); }
        toString() { return this.string; }
    },
};

export const localforage = g.localforage || {
    getItem: async (k) => {
        try {
            const raw = g.localStorage ? g.localStorage.getItem(String(k)) : null;
            return raw ? JSON.parse(raw) : null;
        } catch (_e) { return null; }
    },
    setItem: async (k, v) => {
        try {
            if (g.localStorage) g.localStorage.setItem(String(k), JSON.stringify(v));
        } catch (_e) { /* noop */ }
        return v;
    },
    removeItem: async (k) => {
        try {
            if (g.localStorage) g.localStorage.removeItem(String(k));
        } catch (_e) { /* noop */ }
    },
    clear: async () => {},
    keys: async () => [],
    createInstance: function () { return this; },
};

export const sha256 = g.sha256 || ((str) => String(str ?? ''));
export const morphdom = g.morphdom || ((fromNode, toNode) => {
    try {
        if (fromNode && typeof toNode === 'string') fromNode.innerHTML = toNode;
    } catch (_e) { /* noop */ }
    return fromNode;
});
export const Fuse = g.Fuse || class Fuse {
    constructor(list = []) { this._list = Array.isArray(list) ? list : []; }
    search(q) { return this._list.map((item, refIndex) => ({ item, refIndex })); }
};
export const Popper = g.Popper || {
    createPopper: () => ({ update: () => {}, destroy: () => {}, forceUpdate: () => {} }),
};
export const seedrandom = g.seedrandom || (() => Math.random);
export const droll = g.droll || {
    roll: () => ({ total: 1, rolls: [1] }),
    validate: () => true,
};

export function initLibraryShims() {}

/** ST lib.js 的 slideToggle(target, ...args)：滑动显隐，jQuery 在则用 jQuery。 */
export function slideToggle(target, ...args) {
    try {
        if (target && typeof target.slideToggle === 'function') return target.slideToggle(...args);
        const jq = g.jQuery || g.$;
        if (jq) {
            const $el = jq(target);
            if ($el && $el.length && typeof $el.slideToggle === 'function') return $el.slideToggle(...args);
        }
        const node = (target && target.nodeType === 1) ? target : (typeof target === 'string' && typeof document !== 'undefined' ? document.querySelector(target) : null);
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

export default {
    $,
    jQuery,
    _,
    lodash,
    yaml,
    DOMPurify,
    Bowser,
    hljs,
    showdown,
    moment,
    Handlebars,
    localforage,
    sha256,
    morphdom,
    Fuse,
    Popper,
    seedrandom,
    droll,
    initLibraryShims,
    slideToggle,
};
