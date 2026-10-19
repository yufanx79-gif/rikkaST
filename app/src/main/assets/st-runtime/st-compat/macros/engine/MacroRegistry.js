/**
 * rikkaST st-compat: macros/engine/MacroRegistry.js
 * ============================================================
 * 对齐 SillyTavern 1.15.0 ~ 1.19.0 `public/scripts/macros/engine/MacroRegistry.js`。
 * 供 JS-Slash-Runner >= 1.15.0 分支（Reference.vue:137）与 LittleWhiteBox 宏引用读取。
 */

import { MacrosParser } from '../../macros.js';
import { getLocalVariable, setLocalVariable, getGlobalVariable, setGlobalVariable } from '../../variables.js';

export const MacroCategory = {
    CHAT: 'chat',
    CHARACTER: 'character',
    ENVIRONMENT: 'environment',
    STATE: 'state',
    TIME: 'time',
    UTILITY: 'utility',
    VARIABLE: 'variable',
    RANDOM: 'random',
    NAMES: 'names',
    PROMPTS: 'prompts',
    INSTRUCT: 'instruct',
    CUSTOM: 'custom',
    MISC: 'misc',
    UNCATEGORIZED: 'uncategorized',
};

export const MacroValueType = {
    STRING: 'string',
    NUMBER: 'number',
    INTEGER: 'integer',
    BOOLEAN: 'boolean',
    ANY: 'any',
};

const registryMap = new Map();

function seedBuiltinMacros() {
    const builtins = [
        { name: 'user', category: MacroCategory.ENVIRONMENT, description: 'Current user name.', unnamedArgs: [] },
        { name: 'char', category: MacroCategory.CHARACTER, description: 'Current character name.', unnamedArgs: [] },
        { name: 'setvar', category: MacroCategory.VARIABLE, description: 'Sets a local chat variable.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'value', type: MacroValueType.STRING }] },
        { name: 'getvar', category: MacroCategory.VARIABLE, description: 'Gets a local chat variable.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }] },
        { name: 'setvarkey', aliases: [{ alias: 'setvarindex' }], category: MacroCategory.VARIABLE, description: 'Sets a value at a specific key or index in a local object or array.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'key', type: [MacroValueType.STRING, MacroValueType.NUMBER] }, { name: 'value', type: [MacroValueType.STRING, MacroValueType.NUMBER] }] },
        { name: 'getvarkey', aliases: [{ alias: 'getvarindex' }], category: MacroCategory.VARIABLE, description: 'Gets a value at a specific key or index in a local object or array.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'key', type: [MacroValueType.STRING, MacroValueType.NUMBER] }] },
        { name: 'setglobalvar', category: MacroCategory.VARIABLE, description: 'Sets a global variable.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'value', type: MacroValueType.STRING }] },
        { name: 'getglobalvar', category: MacroCategory.VARIABLE, description: 'Gets a global variable.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }] },
        { name: 'setglobalvarkey', aliases: [{ alias: 'setglobalvarindex' }], category: MacroCategory.VARIABLE, description: 'Sets a value at a specific key or index in a global object or array.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'key', type: [MacroValueType.STRING, MacroValueType.NUMBER] }, { name: 'value', type: [MacroValueType.STRING, MacroValueType.NUMBER] }] },
        { name: 'getglobalvarkey', aliases: [{ alias: 'getglobalvarindex' }], category: MacroCategory.VARIABLE, description: 'Gets a value at a specific key or index in a global object or array.', unnamedArgs: [{ name: 'name', type: MacroValueType.STRING }, { name: 'key', type: [MacroValueType.STRING, MacroValueType.NUMBER] }] },
        { name: '//', aliases: [{ alias: 'comment', visible: false }], category: MacroCategory.UTILITY, description: 'Comment macro (supports scoped {{//}}...{{///}}).', unnamedArgs: [{ name: 'comment', type: MacroValueType.STRING }] },
    ];
    for (const def of builtins) {
        registryMap.set(def.name, {
            name: def.name,
            aliases: def.aliases || [],
            category: def.category || MacroCategory.UTILITY,
            unnamedArgs: def.unnamedArgs || [],
            list: Boolean(def.list),
            description: def.description || '',
            returns: def.returns || '',
            exampleUsage: def.exampleUsage || [],
            handler: def.handler || (() => ''),
        });
    }
}
seedBuiltinMacros();

export const MacroRegistry = {
    registerMacro(name, def = {}) {
        const entry = {
            name: String(name),
            aliases: Array.isArray(def.aliases) ? def.aliases : [],
            category: def.category || MacroCategory.CUSTOM,
            unnamedArgs: Array.isArray(def.unnamedArgs) ? def.unnamedArgs : [],
            list: Boolean(def.list),
            description: String(def.description || ''),
            returns: String(def.returns || ''),
            exampleUsage: Array.isArray(def.exampleUsage) ? def.exampleUsage : [],
            handler: typeof def.handler === 'function' ? def.handler : (() => String(def.value ?? '')),
        };
        registryMap.set(entry.name, entry);
        try { MacrosParser.registerMacro(entry.name, entry.handler); } catch (_e) { /* noop */ }
        return entry;
    },
    registerMacroAlias(alias, target, { visible = true } = {}) {
        const targetEntry = registryMap.get(String(target));
        if (targetEntry) {
            targetEntry.aliases = targetEntry.aliases || [];
            targetEntry.aliases.push({ alias: String(alias), visible: Boolean(visible) });
            registryMap.set(String(alias), targetEntry);
        }
        return targetEntry || null;
    },
    getPrimaryMacro(name) {
        return registryMap.get(String(name)) || null;
    },
    unregisterMacro(name) {
        registryMap.delete(String(name));
        try { MacrosParser.unregisterMacro(String(name)); } catch (_e) { /* noop */ }
    },
    getMacro(name) {
        return registryMap.get(String(name)) || null;
    },
    hasMacro(name) {
        return registryMap.has(String(name));
    },
    getAllMacros({ excludeAliases = false } = {}) {
        const out = Array.from(registryMap.values());
        for (const [k, v] of MacrosParser.macros.entries()) {
            if (!registryMap.has(k)) {
                out.push({
                    name: k,
                    aliases: [],
                    category: MacroCategory.CUSTOM,
                    unnamedArgs: [],
                    list: false,
                    description: '',
                    returns: '',
                    exampleUsage: [],
                    handler: typeof v === 'function' ? v : () => String(v ?? ''),
                });
            }
        }
        void excludeAliases;
        return out;
    },
};

export default MacroRegistry;
