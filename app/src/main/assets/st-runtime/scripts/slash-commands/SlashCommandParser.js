/**
 * rikkaST 桥接层（side-effect）：`../scripts/slash-commands/SlashCommandParser.js` → `../../../st-compat/slash-commands/SlashCommandParser.js`。
 * 产物里这些是 `import "..."` 形式，只为触发模块副作用（注册斜杠命令等），
 * 不取任何导出。同样只做 re-export，不写逻辑。
 */
export * from '../../st-compat/slash-commands/SlashCommandParser.js';
