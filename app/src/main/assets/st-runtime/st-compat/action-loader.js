/**
 * rikkaST st-compat: action-loader.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export class ActionLoaderHandle { constructor(...args) { this._args = args; } }
export const ActionLoaderToastMode = {
    /** No toast is displayed */
    NONE: 'none',
    /** Toast is displayed without stop button (non-interactable) */
    STATIC: 'static',
    /** Toast is displayed with stop button (default) */
    STOPPABLE: 'stoppable',
};
export function createDefaultLoaderOverlay(..._args) { return null; }
export function getActiveLoaderHandles(..._args) { return null; }
export function getLoaderHandleById(..._args) { return null; }
export async function hideActionLoader(..._args) { return null; }
export const loader = {};
export function showActionLoader(..._args) { return null; }
