/**
 * rikkaST st-compat: system-messages.js
 * SillyTavern 1.19.0 full module & export parity shim.
 */
export const SAFETY_CHAT = [];
export function getSystemMessageByType(..._args) { return null; }
export async function initSystemMessages(..._args) { return null; }
export function sendSystemMessage(..._args) { return null; }
export const system_message_types = {
    HELP: 'help',
    WELCOME: 'welcome',
    EMPTY: 'empty',
    GENERIC: 'generic',
    NARRATOR: 'narrator',
    COMMENT: 'comment',
    SLASH_COMMANDS: 'slash_commands',
    FORMATTING: 'formatting',
    HOTKEYS: 'hotkeys',
    MACROS: 'macros',
    WELCOME_PROMPT: 'welcome_prompt',
    ASSISTANT_NOTE: 'assistant_note',
    ASSISTANT_MESSAGE: 'assistant_message',
};
export const system_messages = {};
