/**
 * rikkaST st-compat: slash-commands/SlashCommandParser.js
 * ============================================================
 * 模拟 ST1.18 `SlashCommandParser`：addCommandObject 收集命令至共享注册表，
 * 并暴露 `window.__rikkaExecuteJsSlashCommand` 供宿主 StSlashExecutor 回退调用。
 */

import { __getRegistry } from './SlashCommand.js';

export const PARSER_FLAG = {
    STRICT_ESCAPING: 1,
    REPLACE_GETVAR: 2,
};

export const SlashCommandParser = {
    commands: {},

    addCommandObject(cmd) {
        try {
            if (cmd && cmd.name) {
                const name = String(cmd.name);
                __getRegistry().set(name, cmd);
                __getRegistry().set(name.toLowerCase(), cmd);
                this.commands[name] = cmd;
                if (Array.isArray(cmd.aliases)) {
                    for (const alias of cmd.aliases) {
                        if (alias) {
                            __getRegistry().set(String(alias), cmd);
                            __getRegistry().set(String(alias).toLowerCase(), cmd);
                        }
                    }
                }
            }
        } catch (_e) { /* noop */ }
        return cmd;
    },

    addCommand(name, callback, aliases = []) {
        const cmd = { name, callback: callback || (() => ''), aliases };
        try {
            __getRegistry().set(String(name), cmd);
            __getRegistry().set(String(name).toLowerCase(), cmd);
            this.commands[String(name)] = cmd;
            if (Array.isArray(aliases)) {
                for (const alias of aliases) {
                    if (alias) {
                        __getRegistry().set(String(alias), cmd);
                        __getRegistry().set(String(alias).toLowerCase(), cmd);
                    }
                }
            }
        } catch (_e) { /* noop */ }
        return cmd;
    },

    removeCommand(name) {
        try {
            __getRegistry().delete(String(name));
            __getRegistry().delete(String(name).toLowerCase());
            delete this.commands[String(name)];
        } catch (_e) { /* noop */ }
    },

    getCommand(name) {
        try {
            return __getRegistry().get(String(name)) || __getRegistry().get(String(name).toLowerCase()) || null;
        } catch (_e) {
            return null;
        }
    },

    async parse() { return ''; },
    async executeCommand(name, namedArgs = {}, unnamedArgs = '') {
        const cmd = this.getCommand(name);
        if (!cmd || typeof cmd.callback !== 'function') return null;
        const r = await cmd.callback(namedArgs, unnamedArgs);
        return r == null ? '' : String(r);
    },
    addHelpCommand() {},
};

try {
    if (typeof window !== 'undefined') {
        window.__rikkaExecuteJsSlashCommand = async function (name, namedArgsJson, unnamedArgs, pipe, seq) {
            const cmd = SlashCommandParser.getCommand(name);
            if (!cmd || typeof cmd.callback !== 'function') {
                if (seq != null && window.RikkaBridge && typeof window.RikkaBridge.onSlashResult === 'function') {
                    try { window.RikkaBridge.onSlashResult(seq, JSON.stringify({ found: false, value: '' })); } catch (_e) { /* noop */ }
                }
                return null;
            }
            let named = {};
            try { named = (typeof namedArgsJson === 'string' && namedArgsJson) ? JSON.parse(namedArgsJson) : (namedArgsJson && typeof namedArgsJson === 'object' ? namedArgsJson : {}); } catch (_e) { named = {}; }
            if (pipe !== undefined && named._pipe === undefined) named._pipe = String(pipe);
            try {
                const r = await cmd.callback(named, String(unnamedArgs == null ? '' : unnamedArgs));
                const val = r == null ? '' : String(r);
                if (seq != null && window.RikkaBridge && typeof window.RikkaBridge.onSlashResult === 'function') {
                    try { window.RikkaBridge.onSlashResult(seq, JSON.stringify({ found: true, value: val })); } catch (_e) { /* noop */ }
                }
                return val;
            } catch (err) {
                const errVal = `(js slash error: ${(err && err.message) || err})`;
                if (seq != null && window.RikkaBridge && typeof window.RikkaBridge.onSlashResult === 'function') {
                    try { window.RikkaBridge.onSlashResult(seq, JSON.stringify({ found: true, value: errVal })); } catch (_e) { /* noop */ }
                }
                return errVal;
            }
        };
    }
} catch (_e) { /* noop */ }
