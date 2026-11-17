/**
 * rikkaST st-compat: extensions/tts/index.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/extensions/tts/index.js`（1622 行）。
 *
 * ## ST 原版怎么工作
 * 这是「多角色语音」扩展：把聊天里每个角色映射到不同的 TTS 语音（voiceMap），
 * 逐段朗读助手消息，支持多语音（正文 / "引号台词" / *斜体动作* 分别用不同音色）。
 * 真正的合成走 30+ 个 provider 子模块（elevenlabs / azure / openai / edge ...），
 * 全部 POST 到酒馆后端或第三方 API。
 *
 * ## 我们的实现
 * 宿主有自己的完整 TTS 栈（`speech` 模块：12 个 provider + TtsController + 流式播放），
 * 入口在「设置 → 语音」。所以本模块**不**复刻 provider 子模块，而是把 ST 的
 * **公开导出**做成真实实现：
 *
 *  - `getCharacters(unrestricted)` —— 真读当前会话的角色名（含群聊成员），
 *    多语音开启时展开成 ST 的三种分段类型。这是 voiceMap UI 的数据源。
 *  - `sanitizeId(input)` —— 纯函数，逐字符对齐 ST 实现（第三方扩展依赖它生成 DOM id）。
 *  - `getPreviewString(lang)` —— 34 种语言的 TTS 预览句，与 ST 完全一致。
 *  - `registerTtsProvider(name, provider)` —— 维护 provider 注册表，重复注册按 ST 抛错。
 *  - `initVoiceMap()` —— 从 `extension_settings.tts.voiceMap` 解析出映射表。
 *  - `saveTtsProviderSettings()` —— 持久化到 `extension_settings.tts`（宿主负责落盘）。
 *  - `init()` —— 补齐 `extension_settings.tts` 默认字段。
 *
 * DIVERGENCE：ST 的 `init()` 建 DOM 面板（`#tts_container`）；我们由宿主的语音设置页承担。
 * 朗读本身也不走本模块，走宿主的 `TtsController`（由「朗读」按钮触发）。
 */

export const MODULE_NAME = 'tts';

/** ST `index.js:50` 的两个保留标记。 */
const DEFAULT_VOICE_MARKER = '[Default Voice]';
const DISABLED_VOICE_MARKER = 'disabled';

/** ST `index.js:106-140` 的 provider 注册表（第三方扩展 `registerTtsProvider` 往这里写）。 */
const ttsProviders = {};

/** voiceMap：{ charName: voiceId }（ST `index.js:47`）。 */
let voiceMap = {};

/** voiceMapEntries：未展开的原始条目（ST `index.js:46`）。 */
let voiceMapEntries = [];

/** 取宿主上下文（st-compat 的 getContext 由 extensions.js 挂到全局）。 */
function ctx() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        if (g && typeof g.SillyTavern?.getContext === 'function') return g.SillyTavern.getContext();
        if (g && typeof g.getContext === 'function') return g.getContext();
    } catch (_e) { /* noop */ }
    return {};
}

/** 取 `extension_settings.tts`（不存在则建）。 */
function ttsSettings() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        const es = g ? g.extension_settings : null;
        if (es && typeof es === 'object') {
            if (!es.tts || typeof es.tts !== 'object') es.tts = {};
            return es.tts;
        }
    } catch (_e) { /* noop */ }
    return {};
}

/** 只保留首次出现的元素（ST `onlyUnique`）。 */
function onlyUnique(value, index, array) {
    return array.indexOf(value) === index;
}

/**
 * 取 TTS 预览句（ST `index.js:58-101`，34 种语言逐字对齐）。
 * @param {string} lang BCP-47 语言码
 * @returns {string}
 */
export function getPreviewString(lang) {
    const previewStrings = {
        'en-US': 'The quick brown fox jumps over the lazy dog',
        'en-GB': 'Sphinx of black quartz, judge my vow',
        'fr-FR': 'Portez ce vieux whisky au juge blond qui fume',
        'de-DE': 'Victor jagt zwölf Boxkämpfer quer über den großen Sylter Deich',
        'it-IT': 'Pranzo d\'acqua fa volti sghembi',
        'es-ES': 'Quiere la boca exhausta vid, kiwi, piña y fugaz jamón',
        'es-MX': 'Fabio me exige, sin tapujos, que añada cerveza al whisky',
        'ru-RU': 'В чащах юга жил бы цитрус? Да, но фальшивый экземпляр!',
        'pt-BR': 'Vejo xá gritando que fez show sem playback.',
        'pt-PR': 'Todo pajé vulgar faz boquinha sexy com kiwi.',
        'uk-UA': 'Фабрикуймо гідність, лящім їжею, ґав хапаймо, з\'єднавці чаш!',
        'pl-PL': 'Pchnąć w tę łódź jeża lub ośm skrzyń fig',
        'cs-CZ': 'Příliš žluťoučký kůň úpěl ďábelské ódy',
        'sk-SK': 'Vyhŕňme si rukávy a vyprážajme čínske ryžové cestoviny',
        'hu-HU': 'Árvíztűrő tükörfúrógép',
        'tr-TR': 'Pijamalı hasta yağız şoföre çabucak güvendi',
        'nl-NL': 'De waard heeft een kalfje en een pinkje opgegeten',
        'sv-SE': 'Yxskaftbud, ge vårbygd, zinkqvarn',
        'da-DK': 'Quizdeltagerne spiste jordbær med fløde, mens cirkusklovnen Walther spillede på xylofon',
        'ja-JP': 'いろはにほへと　ちりぬるを　わかよたれそ　つねならむ　うゐのおくやま　けふこえて　あさきゆめみし　ゑひもせす',
        'ko-KR': '가나다라마바사아자차카타파하',
        'zh-CN': '我能吞下玻璃而不伤身体',
        'ro-RO': 'Muzicologă în bej vând whisky și tequila, preț fix',
        'bg-BG': 'Щъркелите се разпръснаха по цялото небе',
        'el-GR': 'Ταχίστη αλώπηξ βαφής ψημένη γη, δρασκελίζει υπέρ νωθρού κυνός',
        'fi-FI': 'Voi veljet, miksi juuri teille myin nämä vehkeet?',
        'he-IL': 'הקצינים צעקו: "כל הכבוד לצבא הצבאות!"',
        'id-ID': 'Jangkrik itu memang enak, apalagi kalau digoreng',
        'ms-MY': 'Muzik penyanyi wanita itu menggambarkan kehidupan yang penuh dengan duka nestapa',
        'th-TH': 'เป็นไงบ้างครับ ผมชอบกินข้าวผัดกระเพราหมูกรอบ',
        'vi-VN': 'Cô bé quàng khăn đỏ đang ngồi trên bãi cỏ xanh',
        'ar-SA': 'أَبْجَدِيَّة عَرَبِيَّة',
        'hi-IN': 'श्वेता ने श्वेता के श्वेते हाथों में श्वेता का श्वेता चावल पकड़ा',
    };
    const fallbackPreview = 'Neque porro quisquam est qui dolorem ipsum quia dolor sit amet';
    return previewStrings[lang] ?? fallbackPreview;
}

/**
 * 注册一个 TTS provider（ST `index.js:104-123`）。
 * 第三方扩展（如 GPT-SoVITS）靠这个把自己的 provider 挂进下拉框。
 *
 * @param {string} name provider 名
 * @param {Function} provider provider 类
 * @returns {boolean} 注册成功
 * @throws {Error} 名字/类非法或重复注册 —— 与 ST 一致
 */
export function registerTtsProvider(name, provider) {
    if (!name || typeof name !== 'string') {
        throw new Error(`TTS provider name ${name} is not a valid string.`);
    }
    if (!provider || typeof provider !== 'function') {
        throw new Error(`TTS provider ${name} is not a valid provider class.`);
    }
    if (ttsProviders[name]) {
        throw new Error(`TTS provider ${name} is already registered.`);
    }
    ttsProviders[name] = provider;
    return true;
}

/** 已注册的 provider 名列表（给 UI 读；ST 没有此导出，我们补的只读视图）。 */
export function getRegisteredTtsProviders() {
    return Object.keys(ttsProviders);
}

/**
 * 取当前聊天里的角色名（ST `index.js:1296-1341`）。
 *
 * 单聊：`[Default Voice, user, char]`
 * 群聊：`[Default Voice, user, ...群成员]`
 * 多语音开启：每个非保留角色再展开成三种分段类型
 *   `<名> ("Quotes")` / `<名> (*Text inside asterisks*)` / `<名> (Other text)`
 *
 * @param {boolean} [unrestricted] true = 返回所有角色卡（不限当前会话）
 * @returns {string[]}
 */
export function getCharacters(unrestricted) {
    const c = ctx();

    if (unrestricted) {
        const chars = Array.isArray(c.characters) ? c.characters : [];
        const names = chars.map((ch) => (ch && ch.name) || '').filter((n) => n);
        names.unshift(DEFAULT_VOICE_MARKER);
        return names.filter(onlyUnique);
    }

    let characters = [];
    const isGroup = c.groupId !== null && c.groupId !== undefined;

    if (!isGroup) {
        characters.push(DEFAULT_VOICE_MARKER);
        if (c.name1) characters.push(c.name1);
        if (c.name2) characters.push(c.name2);
    } else {
        characters.push(DEFAULT_VOICE_MARKER);
        if (c.name1) characters.push(c.name1);
        const groups = Array.isArray(c.groups) ? c.groups : [];
        const group = groups.find((g) => g && c.groupId == g.id);
        const chars = Array.isArray(c.characters) ? c.characters : [];
        const members = (group && Array.isArray(group.members)) ? group.members : [];
        for (const member of members) {
            const character = chars.find((ch) => ch && ch.avatar === member);
            if (character && character.name) characters.push(character.name);
        }
    }
    characters = characters.filter(onlyUnique);

    const s = ttsSettings();
    if (s.multi_voice_enabled) {
        const expanded = [];
        for (const ch of characters) {
            if (ch === DEFAULT_VOICE_MARKER || ch === 'SillyTavern System') {
                expanded.push(ch);
            } else {
                expanded.push(`${ch} ("Quotes")`);
                expanded.push(`${ch} (*Text inside asterisks*)`);
                expanded.push(`${ch} (Other text)`);
            }
        }
        return expanded;
    }

    return characters;
}

/**
 * 生成合法的 DOM element id（ST `index.js:1343-1353`）。
 * 规则：encodeURIComponent 后只保留 [a-zA-Z0-9-_]，首字符非字母则加 `element_` 前缀。
 *
 * @param {string} input
 * @returns {string}
 */
export function sanitizeId(input) {
    let sanitized = encodeURIComponent(String(input ?? '')).replace(/[^a-zA-Z0-9-_]/g, '');
    if (!/^[a-zA-Z]/.test(sanitized)) {
        sanitized = 'element_' + sanitized;
    }
    return sanitized;
}

/**
 * 解析 voiceMap 字符串 → `{charName: voiceId}`（ST `index.js:1355-1370 parseVoiceMap`）。
 * 格式：`角色A:voice1,角色B:voice2`。缺任一侧的条目被丢弃。
 *
 * @param {string} voiceMapString
 * @returns {Record<string,string>}
 */
function parseVoiceMap(voiceMapString) {
    const parsed = {};
    if (!voiceMapString || typeof voiceMapString !== 'string') return parsed;
    for (const [charName, voiceId] of voiceMapString.split(',').map((s) => s.split(':'))) {
        if (charName && voiceId) {
            parsed[charName] = voiceId;
        }
    }
    return parsed;
}

/**
 * 把 voiceMap 对象序列化回 ST 的字符串格式（ST `index.js:1372-1380`）。
 * @param {Record<string,string>} map
 * @returns {string}
 */
function stringifyVoiceMap(map) {
    return Object.entries(map || {})
        .filter(([k, v]) => k && v)
        .map(([k, v]) => `${k}:${v}`)
        .join(',');
}

/**
 * 从 `extension_settings.tts.voiceMap` 初始化映射表（ST `index.js:1440-1530`）。
 * @param {boolean} [unrestricted]
 * @returns {Promise<boolean>}
 */
export async function initVoiceMap(unrestricted = false) {
    const s = ttsSettings();
    voiceMap = parseVoiceMap(s.voiceMap);
    const names = getCharacters(unrestricted);
    voiceMapEntries = names.map((name) => ({
        name,
        voiceId: voiceMap[name] ?? DEFAULT_VOICE_MARKER,
        disabled: voiceMap[name] === DISABLED_VOICE_MARKER,
    }));
    return true;
}

/** 取当前 voiceMap（给 UI / 脚本读）。 */
export function getVoiceMap() {
    return { ...voiceMap };
}

/** 取 voiceMapEntries（含展开后的分段类型）。 */
export function getVoiceMapEntries() {
    return voiceMapEntries.slice();
}

/**
 * 设置某角色的语音（写进 voiceMap 并同步到 extension_settings）。
 * @param {string} charName
 * @param {string} voiceId
 * @returns {boolean}
 */
export function setVoiceForCharacter(charName, voiceId) {
    if (!charName) return false;
    if (!voiceId || voiceId === DEFAULT_VOICE_MARKER) {
        delete voiceMap[charName];
    } else {
        voiceMap[charName] = voiceId;
    }
    const s = ttsSettings();
    s.voiceMap = stringifyVoiceMap(voiceMap);
    voiceMapEntries = voiceMapEntries.map((e) =>
        e.name === charName ? { ...e, voiceId: voiceId || DEFAULT_VOICE_MARKER, disabled: voiceId === DISABLED_VOICE_MARKER } : e
    );
    return true;
}

/**
 * 持久化 TTS provider 设置（ST `index.js:1081-1090`）。
 * ST 原版 `saveSettingsDebounced()` 把整个 extension_settings 写回后端；
 * 我们写回 `extension_settings.tts`，落盘由宿主负责。
 * @returns {boolean}
 */
export function saveTtsProviderSettings() {
    try {
        const g = (typeof globalThis !== 'undefined') ? globalThis : null;
        if (g && typeof g.saveSettingsDebounced === 'function') {
            g.saveSettingsDebounced();
            return true;
        }
    } catch (_e) { /* noop */ }
    return false;
}

/**
 * ST 扩展入口。原版建设置面板 DOM；我们由宿主「设置 → 语音」承担。
 * 这里只补齐 `extension_settings.tts` 的默认字段，避免 UI 读到 undefined。
 * @returns {Promise<boolean>}
 */
export async function init() {
    const s = ttsSettings();
    const defaults = {
        currentProvider: 'System',
        voiceMap: '',
        multi_voice_enabled: false,
        auto_generation: false,
        periodic_auto_generation: false,
        narrate_user_messages: false,
        narrate_by_paragraph: false,
        show_text: false,
        linked_text: false,
        keep_playing: false,
    };
    for (const [k, v] of Object.entries(defaults)) {
        if (s[k] === undefined) s[k] = v;
    }
    await initVoiceMap();
    return true;
}
