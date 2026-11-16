/**
 * rikkaST st-compat: char-data.js
 * ============================================================
 * 模拟 SillyTavern `public/scripts/char-data.js` 的最小导出（P0）。
 * v1CharData 为宽容构造函数（可 new 也可直接调用）。
 */

export function v1CharData(name) {
    if (!(this instanceof v1CharData)) {
        return { name: name || '' };
    }
    this.name = name || '';
}
/**
 * [v246] 正则脚本数据结构。
 *
 * 为什么补：Tavern Helper `src/function/tavern_regex.ts` 从
 * `@sillytavern/scripts/char-data` import `RegexScriptData` 做类型收窄。
 * 对照结果里这是 char-data 唯一缺失的符号。
 *
 * 与真源一致：只是数据载体，无行为。字段名用 ST 的 snake_case + camelCase 双写，
 * 因为预设 JSON 里两种写法都存在（实测 TRANS-AM 用 findRegex/scriptName）。
 */
export class RegexScriptData {
    constructor(data = {}) {
        this.id = data.id ?? '';
        this.scriptName = data.scriptName ?? data.script_name ?? '';
        this.findRegex = data.findRegex ?? data.find_regex ?? '';
        this.replaceString = data.replaceString ?? data.replace_string ?? '';
        this.trimStrings = data.trimStrings ?? data.trim_strings ?? [];
        this.placement = data.placement ?? [];
        this.disabled = data.disabled ?? false;
        this.markdownOnly = data.markdownOnly ?? data.markdown_only ?? false;
        this.promptOnly = data.promptOnly ?? data.prompt_only ?? false;
        this.runOnEdit = data.runOnEdit ?? data.run_on_edit ?? false;
        this.substituteRegex = data.substituteRegex ?? data.substitute_regex ?? 0;
        this.minDepth = data.minDepth ?? data.min_depth ?? null;
        this.maxDepth = data.maxDepth ?? data.max_depth ?? null;
    }
}
