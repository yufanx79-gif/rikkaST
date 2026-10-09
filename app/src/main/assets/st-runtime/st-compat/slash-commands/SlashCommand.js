/**
 * rikkaST st-compat: slash-commands/SlashCommand.js
 * ============================================================
 * 模拟 ST1.18 `SlashCommand` 类。注册的命令对象存入共享注册表
 * （window.__rikkaStSlashRegistry），供未来与宿主 triggerSlash 集成。
 */

const registry = (() => {
    try {
        if (!window.__rikkaStSlashRegistry) window.__rikkaStSlashRegistry = new Map();
        return window.__rikkaStSlashRegistry;
    } catch (_e) {
        return new Map();
    }
})();

export function __getRegistry() {
    return registry;
}

export class SlashCommand {
    constructor(name, callback, ...rest) {
        this.name = name;
        this.callback = callback || (() => '');
        this.extra = rest;
    }

    static fromProps(props) {
        const cmd = new SlashCommand(props && props.name, props && props.callback, props);
        if (props && typeof props === 'object') Object.assign(cmd, props);
        return cmd;
    }

    static namedArgument() {
        return null;
    }
}