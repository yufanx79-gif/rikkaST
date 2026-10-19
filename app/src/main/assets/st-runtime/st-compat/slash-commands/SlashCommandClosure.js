// rikkaST st-compat: scripts/slash-commands/SlashCommandClosure.js
export class SlashCommandClosure {
    constructor(scope) {
        this.scope = scope || null;
        this.argumentList = [];
        this.executor = [];
        this.rawText = '';
    }
    async execute() {
        return { pipe: '' };
    }
}
export default SlashCommandClosure;
