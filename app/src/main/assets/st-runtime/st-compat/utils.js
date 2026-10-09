/**
 * rikkaST st-compat: utils.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/utils.js` 的导出面（常用工具函数真实现）。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

// ---------------------------------------------------------------- 基础工具
export function debounce(fn, wait = 300) {
    let timer = null;
    const wrapped = (...args) => {
        if (timer) clearTimeout(timer);
        timer = setTimeout(() => { timer = null; try { fn(...args); } catch (_e) { /* noop */ } }, wait);
    };
    wrapped.cancel = () => { if (timer) { clearTimeout(timer); timer = null; } };
    wrapped.flush = (...args) => { if (timer) { clearTimeout(timer); timer = null; } try { fn(...args); } catch (_e) { /* noop */ } };
    return wrapped;
}

export function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, Math.max(0, Number(ms) || 0)));
}

export function uuidv4() {
    try {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
    } catch (_e) { /* noop */ }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

/** 与 ST 语义一致的字符串哈希（cyrb53 变体）。 */
export function getStringHash(str, seed = 0) {
    let h1 = 0xdeadbeef ^ seed;
    let h2 = 0x41c6ce57 ^ seed;
    const s = String(str == null ? '' : str);
    for (let i = 0; i < s.length; i++) {
        const ch = s.charCodeAt(i);
        h1 = Math.imul(h1 ^ ch, 2654435761);
        h2 = Math.imul(h2 ^ ch, 1597334677);
    }
    h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
    h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
    return 4294967296 * (2097151 & h2) + (h1 >>> 0);
}

export function isDataURL(value) {
    return /^data:/i.test(String(value == null ? '' : value));
}

export function getSanitizedFilename(filename) {
    return String(filename == null ? '' : filename).replace(/[\/\\:*?"<>|\u0000-\u001f]/g, '_').trim() || 'file';
}

export function getImageSizeFromDataURL(dataUrl) {
    return new Promise((resolve) => {
        try {
            const img = new Image();
            img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight });
            img.onerror = () => resolve({ width: 0, height: 0 });
            img.src = dataUrl;
        } catch (_e) {
            resolve({ width: 0, height: 0 });
        }
    });
}

export async function getBase64Async(file) {
    try {
        return await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsDataURL(file);
        });
    } catch (_e) {
        return '';
    }
}

export function ensureImageFormatSupported(file) {
    // P0：不做格式转换，原样返回（ST 在浏览器端做 PNG 转换）。
    return file;
}

// ---------------------------------------------------------------- 文件 / 剪贴板
/** P0：宿主文件系统下载未接线 → 返回空（调用方通常忽略返回值）。 */
export async function saveBase64AsFile(base64Data, characterName, filename, extension, ...rest) {
    return '';
}

export async function download(data, filename) {
    try {
        const blob = (data instanceof Blob) ? data : new Blob([data]);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = String(filename || 'download');
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (_e) { /* noop */ }
}

export async function copyText(text) {
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(String(text == null ? '' : text));
            return true;
        }
    } catch (_e) { /* noop */ }
    return false;
}

// ---------------------------------------------------------------- 角色
export function getCharaFilename(chid) {
    try {
        const c = st();
        const list = c.characters || [];
        const index = (typeof chid === 'number') ? chid : (typeof c.characterId === 'number' ? c.characterId : 0);
        const ch = list[index];
        if (!ch) return '';
        const name = ch.avatar || ch.name || '';
        return String(name).replace(/\.[^.]+$/, '');
    } catch (_e) {
        return '';
    }
}

export function findChar(avatar, data) {
    try {
        const list = (data && Array.isArray(data)) ? data : (st().characters || []);
        return list.find((c) => c && (c.avatar === avatar || c.name === avatar)) || null;
    } catch (_e) {
        return null;
    }
}

// ---------------------------------------------------------------- 其他
/** 解析 `/pattern/flags` 形式的正则字符串（对齐 ST utils.regexFromString）。 */
export function regexFromString(input) {
    try {
        // Parse input
        const m = String(input).match(/(\/?)(.+)\1([a-z]*)/i);

        // Invalid flags
        if (m[3] && !/^(?!.*?(.).*?\1)[gmixXsuUAJ]+$/.test(m[3])) {
            return RegExp(input);
        }

        // Create the regular expression
        return new RegExp(m[2], m[3]);
    } catch (_e) {
        return undefined;
    }
}

export class Stopwatch {
    constructor() { this.startTime = performance.now(); }
    get elapsed() { return performance.now() - this.startTime; }
    reset() { this.startTime = performance.now(); }
}

export function showFontAwesomePicker() {}
