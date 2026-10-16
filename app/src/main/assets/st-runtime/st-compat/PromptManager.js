/**
 * rikkaST st-compat: PromptManager.js
 * ============================================================
 * 对齐 SillyTavern `public/scripts/PromptManager.js` 的 Prompt / PromptCollection 与激活预设读写。
 */

export class Prompt {
    constructor(collection, identifier, ...rest) {
        this.collection = collection;
        this.identifier = identifier;
        this.role = 'system';
        this.content = '';
        this.enabled = true;
        this.injection_position = 0;
        this.injection_depth = 4;
        this.injection_order = 100;
        this.extra = rest;
    }

    static from(props) {
        const p = new Prompt(props && props.collection, props && props.identifier, props);
        if (props && typeof props === 'object') Object.assign(p, props);
        return p;
    }
}

export class PromptCollection {
    constructor() {
        this.collection = [];
        this.overriddenPrompts = [];
    }

    add(prompt) {
        try {
            const p = prompt instanceof Prompt ? prompt : Prompt.from(prompt);
            this.collection.push(p);
            return p;
        } catch (_e) {
            return prompt;
        }
    }

    get(id) {
        try {
            return this.collection.find((p) => p && (p.identifier === id || p.id === id)) || null;
        } catch (_e) {
            return null;
        }
    }

    has(id) {
        return !!this.get(id);
    }

    index(id) {
        return this.collection.findIndex((p) => p && (p.identifier === id || p.id === id));
    }

    override(prompt) {
        if (!prompt) return;
        const id = prompt.identifier || prompt.id;
        const existingIdx = this.index(id);
        if (existingIdx >= 0) {
            this.collection[existingIdx] = Object.assign(this.collection[existingIdx], prompt);
        } else {
            this.add(prompt);
        }
        if (id && !this.overriddenPrompts.includes(id)) {
            this.overriddenPrompts.push(id);
        }
    }

    render() {
        try {
            if (window.RikkaBridge && typeof window.RikkaBridge.emitEvent === 'function') {
                window.RikkaBridge.emitEvent('prompt_manager_rendered', JSON.stringify(this.collection));
            }
        } catch (_e) { /* noop */ }
    }
}

export const promptManager = new PromptCollection();
