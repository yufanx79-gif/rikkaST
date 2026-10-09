/**
 * rikkaST st-compat: openai.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/openai.js` 的导出面（P0：宽容 stub 为主）。
 * 真实模型调用由宿主 ai 管道负责，本层只保证第三方扩展 import / 读写不炸。
 */

const st = () => (window.SillyTavern && window.SillyTavern.getContext ? window.SillyTavern.getContext() : {});

/** 当前 Chat Completion 源（对齐 ST 语义的最小集）。 */
export const chat_completion_sources = {
    OPENAI: 'openai',
    CLAUDE: 'claude',
    WINDOWAI: 'windowai',
    OPENROUTER: 'openrouter',
    AI21: 'ai21',
    MAKERSUITE: 'makersuite',
    VERTEXAI: 'vertexai',
    GEMINI: 'gemini',
    DEEPSEEK: 'deepseek',
    XAI: 'xai',
    MISTRALAI: 'mistralai',
    CUSTOM: 'custom',
    COHERE: 'cohere',
    PERPLEXITY: 'perplexity',
    GROQ: 'groq',
};

/** 宽容对象：扩展读写未知字段不炸（对齐 ST 的全局可变语义）。 */
export const oai_settings = {
    prompts: [],
    prompt_order: [],
    stream_openai: false,
    chat_completion_source: 'custom',
    squash_system_messages: false,
    send_if_empty: '',
    impersonation_prompt: '[Write the next reply only as {{char}}]',
    continue_nsfw: false,
    continue_postfix: '',
    continue_max_length: 60,
    max_context_unlocked: false,
    wand_button: true,
};
export const openai_settings = [];
export const openai_setting_names = {};

export function getChatCompletionModel() {
    try {
        const f = st().getChatCompletionModel;
        if (typeof f === 'function') return f();
    } catch (_e) { /* noop */ }
    return 'unknown';
}

// ---------------------------------------------------------------- PromptManager（最小鸭子类型）
function createPromptManagerStub() {
    const prompts = [];
    return {
        prompts,
        addPrompt(prompt) { try { prompts.push(prompt); } catch (_e) { /* noop */ } return prompt; },
        removePrompt(prompt) { const i = prompts.indexOf(prompt); if (i >= 0) prompts.splice(i, 1); },
        overridePrompt() {},
        getPromptCollection() { return { collection: prompts.slice(), get: () => null, has: () => false }; },
        getPromptById(id) { return prompts.find((p) => p && p.id === id) || null; },
        render() {},
        tokenHandler: null,
        serviceSettings: {},
    };
}

export const promptManager = createPromptManagerStub();

// ---------------------------------------------------------------- 请求 / 流（v213：对齐 ST 契约形态）
// [v213] ST openai.js:1542 起 prepareOpenAIMessages 返回 [chat, canMultiSwipe] 数组；
// JSR generate.ts:70 是 const [prompt] = await prepareOpenAIMessages(...) 数组解构 ——
// 旧实现返回 args 对象 → 解构 TypeError（object is not iterable）。
export const openai_max_stop_strings = 4; // ST openai.js:144（JSR createGenerationParametersCompat.ts:75 具名读取）

export async function prepareOpenAIMessages(args) {
    // 宿主侧没有真实的 ST prompt 组装器；这里把传入数据整理成 [{role, content}] 后按契约返回数组。
    // 真正的组装由宿主 GenerationHandler 负责，本层保证 JSR 脚本的 [prompt] 解构可用。
    try {
        if (args && Array.isArray(args.messages)) {
            const chat = args.messages
                .filter((m) => m && typeof m === "object")
                .map((m) => ({ role: m.role || "system", content: m.content == null ? "" : String(m.content) }));
            return [chat, false];
        }
    } catch (_e) { /* noop */ }
    return [[], false];
}
export async function getStreamingReply() { return ""; }
export async function sendOpenAIRequest() {}
export async function createGenerationParameters(settings, model, type, messages, options) {
    // ST 1.15+ 的导出面（JSR createGenerationParameters.ts:39 动态 import 读取）。
    // 形状对齐 JSR 消费：{ generate_data, stream?, canMultiSwipe? }
    void settings; void model; void type; void messages; void options;
    return { generate_data: { messages: Array.isArray(messages) ? messages : [], model: String(model || "") }, stream: false, canMultiSwipe: false };
}
export const isImageInliningSupported = () => false;
export const proxies = [];
export function parseExampleIntoIndividual(text) { return [String(text == null ? '' : text)]; }
export function setOpenAIMessages() {}
export function setOpenAIMessageExamples() {}
export function setupChatCompletionPromptManager() {}
export function tryParseStreamingError() {}

// ---------------------------------------------------------------- 类型（v213：对齐 JSR generateRaw 的鸭子方法面）
// [v213] JSR generateRaw.ts 消费面（源码行号见 notes/recon-ext-compat-20261001.md §2.2）：
//   Message.createAsync(role, content, identifier) → setName() 可链、
//   Message.fromPromptAsync(prompt)、
//   ChatCompletion.add(collection, index)/reserveBudget(m)/freeBudget(m)/
//   squashSystemMessages()/getChat()（返回 [{role, content}]）。
export class ChatCompletion {
    constructor(...rest) {
        this.extra = rest;
        this._collections = []; // MessageCollection[]
    }
    add(collection, index) {
        try {
            if (index == null || typeof index !== "number" || index >= this._collections.length) this._collections.push(collection);
            else this._collections.splice(index, 0, collection);
        } catch (_e) { /* noop */ }
        return this;
    }
    reserveBudget() { return this; }
    freeBudget() { return this; }
    setTokenBudget() { return this; }
    async squashSystemMessages() { return this; }
    getChat() {
        const out = [];
        try {
            for (const col of this._collections) {
                const list = col && Array.isArray(col.messages) ? col.messages : (Array.isArray(col) ? col : [col]);
                for (const m of list) {
                    if (m && typeof m === "object" && m.content != null) out.push({ role: m.role || "system", content: String(m.content) });
                }
            }
        } catch (_e) { /* noop */ }
        return out;
    }
}

export class Message {
    constructor(role, content, ...rest) {
        this.role = role;
        this.content = content;
        this.identifier = (rest && rest[0]) || null;
        this.extra = rest;
        this.name = null;
    }
    static async createAsync(role, content, identifier) {
        return new Message(role, content, identifier);
    }
    static async fromPromptAsync(prompt) {
        const m = new Message((prompt && prompt.role) || "system", (prompt && prompt.content) || "", (prompt && (prompt.identifier || prompt.id)) || null);
        if (prompt && prompt.name) m.name = prompt.name;
        return m;
    }
    async setName(name) { this.name = name == null ? null : String(name); return this; }
    async setTokenCount() { return this; }
}

export class MessageCollection {
    constructor(identifierOrMessages = [], maybeMessages = null) {
        // ST 契约：new MessageCollection(identifier, [messages])；兼容旧调用 new MessageCollection([...])
        if (typeof identifierOrMessages === "string") {
            this.identifier = identifierOrMessages;
            this.messages = Array.isArray(maybeMessages) ? maybeMessages : [];
        } else {
            this.identifier = null;
            this.messages = Array.isArray(identifierOrMessages) ? identifierOrMessages : [];
        }
    }
    add(message) { try { this.messages.push(message); } catch (_e) { /* noop */ } return this; }
}
