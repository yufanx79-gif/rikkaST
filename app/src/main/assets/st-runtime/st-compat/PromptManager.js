/**
 * rikkaST st-compat: PromptManager.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/PromptManager.js` 的类签名（P0）。
 */

export class Prompt {
    constructor(collection, identifier, ...rest) {
        this.collection = collection;
        this.identifier = identifier;
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
    }

    add(prompt) {
        try { this.collection.push(prompt); } catch (_e) { /* noop */ }
        return prompt;
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

    override() {}

    render() {}
}