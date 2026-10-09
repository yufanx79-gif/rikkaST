/**
 * rikkaST st-compat: slash-commands/slash-commands.js（根入口）
 * ============================================================
 * 模拟 SillyTavern `public/scripts/slash-commands.js` 的执行入口。
 * executeSlashCommandsWithOptions 直连宿主 RikkaBridge.triggerSlash（真执行）。
 *
 * [v213] 返回形态对齐 ST 契约：ST 的 executeSlashCommandsWithOptions 返回
 * SlashCommandExecutionResult 对象 { pipe, isError, errorMessage, isAbort, isIdle, hasBackpipe }，
 * 不是字符串。JSR function/slash.ts:3-8（triggerSlash）与 ST-PT EJS 的 execute() 都读 result.pipe / result.isError。
 * 旧实现直接返回字符串 → result.pipe === undefined。
 */

function toExecutionResult(rawValue) {
    const text = rawValue == null ? "" : String(rawValue);
    // 宿主桥的三类返回：真实管道值 / "(slash runner not ready)" / "(slash error: ...)"
    const isErr = text.startsWith("(slash error:") || text === "(slash runner not ready)";
    return {
        pipe: isErr ? "" : text,
        isError: isErr,
        errorMessage: isErr ? text : "",
        isAbort: false,
        isIdle: false,
        hasBackpipe: false,
    };
}

export async function executeSlashCommandsWithOptions(text, options) {
    try {
        if (window.RikkaBridge && typeof window.RikkaBridge.triggerSlash === "function") {
            return toExecutionResult(window.RikkaBridge.triggerSlash(String(text == null ? "" : text)));
        }
    } catch (_e) { /* noop */ }
    return toExecutionResult("(slash runner not ready)");
}

export async function executeSlashCommands(text) {
    const r = await executeSlashCommandsWithOptions(text);
    return r && typeof r === "object" ? r.pipe : r;
}

export async function executeSlashCommandsOnChatInput() {
    return "";
}
