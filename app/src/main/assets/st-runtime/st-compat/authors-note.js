/**
 * rikkaST st-compat: authors-note.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/authors-note.js` 的最小导出面（P0）。
 */

export const NOTE_MODULE_NAME = 'authorsNote';

export const metadata_keys = [
    'note_prompt',
    'note_interval',
    'note_position',
    'note_depth',
    'note_role',
];

export function shouldWIAddPrompt() {
    return false;
}