/**
 * rikkaST st-compat: macros/engine/MacroCstWalker.js
 */
export const MacroCstWalker = {
    walk(_cst, env) {
        return String((env && env.content) ?? '');
    },
};
export default MacroCstWalker;
