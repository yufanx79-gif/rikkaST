/**
 * rikkaST st-compat: macros/engine/MacroEngine.js
 */
const preProcessors = [];
const postProcessors = [];

export const MacroEngine = {
    addPreProcessor(handler, { priority = 100, source = 'unknown' } = {}) {
        if (typeof handler !== 'function') return;
        preProcessors.push({ handler, priority: Number(priority) || 100, source });
        preProcessors.sort((a, b) => a.priority - b.priority);
    },
    removePreProcessor(handler) {
        const idx = preProcessors.findIndex((p) => p.handler === handler);
        if (idx >= 0) preProcessors.splice(idx, 1);
    },
    addPostProcessor(handler, { priority = 100, source = 'unknown' } = {}) {
        if (typeof handler !== 'function') return;
        postProcessors.push({ handler, priority: Number(priority) || 100, source });
        postProcessors.sort((a, b) => a.priority - b.priority);
    },
    removePostProcessor(handler) {
        const idx = postProcessors.findIndex((p) => p.handler === handler);
        if (idx >= 0) postProcessors.splice(idx, 1);
    },
    evaluate(text, env = {}) {
        let out = String(text ?? '');
        for (const p of preProcessors) {
            try { out = String(p.handler(out, env) ?? out); } catch (_e) { /* noop */ }
        }
        try {
            const st = (typeof window !== 'undefined' && window.SillyTavern && window.SillyTavern.getContext)
                ? window.SillyTavern.getContext()
                : null;
            if (st && typeof st.substituteParams === 'function') {
                out = st.substituteParams(out);
            }
        } catch (_e) { /* noop */ }
        for (const p of postProcessors) {
            try { out = String(p.handler(out, env) ?? out); } catch (_e) { /* noop */ }
        }
        return out;
    },
};
export default MacroEngine;
