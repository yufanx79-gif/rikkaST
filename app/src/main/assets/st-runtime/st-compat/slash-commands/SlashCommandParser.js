/**
 * rikkaST st-compat: slash-commands/SlashCommandParser.js
 * ============================================================
 * 模拟 ST1.18 `SlashCommandParser`：addCommandObject 收集命令至共享注册表。
 */

import { __getRegistry } from './SlashCommand.js';

export const SlashCommandParser = {
    commands: {},

    addCommandObject(cmd) {
        try {
            if (cmd && cmd.name) {
                __getRegistry().set(String(cmd.name), cmd);
                this.commands[String(cmd.name)] = cmd;
            }
        } catch (_e) { /* noop */ }
        return cmd;
    },

    addCommand(name, callback) {
        const cmd = { name, callback: callback || (() => '') };
        try { __getRegistry().set(String(name), cmd); } catch (_e) { /* noop */ }
        return cmd;
    },

    removeCommand(name) {
        try {
            __getRegistry().delete(String(name));
            delete this.commands[String(name)];
        } catch (_e) { /* noop */ }
    },

    getCommand(name) {
        try {
            return __getRegistry().get(String(name)) || null;
        } catch (_e) {
            return null;
        }
    },

    async parse() { return ''; },
    async executeCommand() { return ''; },
    addHelpCommand() {},
};