/**
 * rikkaST st-compat: instruct-mode.js
 * ============================================================
 * 对齐 SillyTavern 1.19.0 `public/scripts/instruct-mode.js`（871 行）。
 *
 * 核心是 **Instruct 序列格式化**：把一条消息按模板包装成
 * `<sequence><name>: <message><suffix>` 的形状再发给模型。
 *
 * 数据源：`power_user.instruct`（ST）—— 我们映射到宿主 `Assistant.instructTemplate`
 * （字段名对齐，见 InstructTemplate.kt:24-49）。宿主经 ctx.instructTemplate 下发。
 *
 * ## 实现范围
 * - `formatInstructModePrompt`  **完整移植**（ST :593-657，纯字符串处理）
 * - `formatInstructModeSystemPrompt` / `formatInstructModeStoryString` /
 *   `formatInstructModeExamples`  **完整移植**（同文件）
 * - `getInstructMacros`  完整移植（宏表）
 * - `getInstructStoppingSequences`  完整移植
 * - `loadInstructMode` / `selectContextPreset` / `selectInstructPreset` /
 *   `autoSelectInstructPreset`  → 宿主由 Compose 设置页承担，这里 no-op
 *
 * DIVERGENCE：ST 从 `power_user.instruct` 读；我们优先读 `ctx.instructTemplate`（宿主真源），
 * 读不到再退回 `power_user.instruct`（兼容脚本自己改 power_user 的场景）。
 */

const st = () => (typeof window !== 'undefined' && window.SillyTavern && window.SillyTavern.getContext
    ? window.SillyTavern.getContext() : {});

/** ST `names_behavior_types`（instruct-mode.js 顶部）。 */
export const names_behavior_types = { NONE: 0, FORCE: 1, ALWAYS: 2, NEVER: 3 };

/**
 * 取当前 Instruct 模板（宿主真源优先）。
 * 字段名同时兼容 ST 的 snake_case 与宿主的 camelCase。
 */
function getInstruct() {
    let raw = null;
    try {
        const ctx = st();
        if (ctx && ctx.instructTemplate && typeof ctx.instructTemplate === 'object') raw = ctx.instructTemplate;
    } catch (_e) { /* noop */ }
    if (!raw) {
        try {
            const pu = globalThis.power_user;
            if (pu && pu.instruct && typeof pu.instruct === 'object') raw = pu.instruct;
        } catch (_e) { /* noop */ }
    }
    const o = raw || {};
    const pick = (snake, camel, dflt) => {
        if (o[snake] !== undefined) return o[snake];
        if (o[camel] !== undefined) return o[camel];
        return dflt;
    };
    return {
        enabled: pick('enabled', 'enabled', false),
        story_string_prefix: pick('story_string_prefix', 'storyStringPrefix', ''),
        story_string_suffix: pick('story_string_suffix', 'storyStringSuffix', ''),
        system_sequence: pick('system_sequence', 'systemSequence', ''),
        system_suffix: pick('system_suffix', 'systemSuffix', ''),
        last_system_sequence: pick('last_system_sequence', 'lastSystemSequence', ''),
        input_sequence: pick('input_sequence', 'inputSequence', ''),
        input_suffix: pick('input_suffix', 'inputSuffix', ''),
        first_input_sequence: pick('first_input_sequence', 'firstInputSequence', ''),
        last_input_sequence: pick('last_input_sequence', 'lastInputSequence', ''),
        output_sequence: pick('output_sequence', 'outputSequence', ''),
        output_suffix: pick('output_suffix', 'outputSuffix', ''),
        first_output_sequence: pick('first_output_sequence', 'firstOutputSequence', ''),
        last_output_sequence: pick('last_output_sequence', 'lastOutputSequence', ''),
        wrap: pick('wrap', 'wrap', true),
        macro: pick('macro', 'macro', true),
        names_behavior: pick('names_behavior', 'namesBehavior', 0),
        stop_sequence: pick('stop_sequence', 'stopSequence', ''),
    };
}

function substitute(t) {
    try {
        const ctx = st();
        if (ctx && typeof ctx.substituteParams === 'function') return String(ctx.substituteParams(t) ?? t);
    } catch (_e) { /* noop */ }
    return String(t ?? '');
}

/**
 * 把一条消息按 Instruct 模板包装（ST instruct-mode.js:593-657 逐行移植）。
 *
 * @param {string} name 说话者名字
 * @param {boolean} isImpersonate 是否用户扮演
 * @param {string} promptBias 提示偏置（会追加到序列后）
 * @param {string} name1 用户名字（宏用）
 * @param {string} name2 角色名字（宏用）
 * @param {boolean} isQuiet 是否静默提示
 * @param {boolean} isQuietToLoud 静默提示是否走「出声」通道
 * @param {object|null} customInstruct 自定义模板（null = 用当前）
 * @returns {string}
 */
export function formatInstructModePrompt(name, isImpersonate, promptBias, name1, name2, isQuiet, isQuietToLoud, customInstruct = null) {
    const instruct = Object.assign({}, getInstruct(), customInstruct || {});
    const nb = Number(instruct.names_behavior);
    const selected_group = (() => { try { return st().groupId || null; } catch (_e) { return null; } })();
    const includeNames = !!name && (nb === names_behavior_types.ALWAYS || (!!selected_group && nb === names_behavior_types.FORCE)) && !(isQuiet && !isQuietToLoud);

    function getSequence() {
        if (isImpersonate) return instruct.last_input_sequence || instruct.input_sequence;
        if (isQuiet && !isQuietToLoud) return instruct.last_system_sequence || instruct.output_sequence;
        if (isQuiet && isQuietToLoud) return instruct.last_output_sequence || instruct.output_sequence;
        return instruct.last_output_sequence || instruct.output_sequence;
    }

    let sequence = getSequence() || '';
    let nameFiller = '';

    // Mistral 格式的特例：last_output_sequence 与 output_sequence 只差尾部空格
    if (
        includeNames &&
        instruct.last_output_sequence &&
        instruct.output_sequence &&
        sequence === instruct.last_output_sequence &&
        /\s$/.test(instruct.output_sequence) &&
        !/\s$/.test(instruct.last_output_sequence)
    ) {
        nameFiller = instruct.output_sequence.slice(-1);
    }

    if (instruct.macro) {
        sequence = substitute(sequence);
        sequence = sequence.replace(/{{name}}/gi, name || 'System');
    }

    const separator = instruct.wrap ? '\n' : '';
    let text = includeNames
        ? (separator + sequence + separator + nameFiller + `${name}:`)
        : (separator + sequence);

    // 静默提示本身已带换行
    if (isQuiet && separator) text = text.slice(separator.length);

    if (!isImpersonate && promptBias) {
        text += (includeNames ? promptBias : (separator + String(promptBias).trimStart()));
    }

    return (instruct.wrap ? text.trimEnd() : text) + (includeNames ? '' : separator);
}

/**
 * 包装系统提示（ST instruct-mode.js:707 附近）。
 * @param {string} systemPrompt
 * @param {boolean} [isQuiet]
 * @returns {string}
 */
export function formatInstructModeSystemPrompt(systemPrompt, isQuiet) {
    const instruct = getInstruct();
    let seq = instruct.system_sequence || '';
    if (instruct.macro) seq = substitute(seq);
    const separator = instruct.wrap ? '\n' : '';
    let text = separator + seq + String(systemPrompt ?? '');
    if (isQuiet && separator) text = text.slice(separator.length);
    const suffix = instruct.system_suffix || '';
    if (suffix) text += instruct.macro ? substitute(suffix) : suffix;
    return instruct.wrap ? text.trimEnd() : text;
}

/**
 * 包装故事串（ST instruct-mode.js:730 附近）。
 * @param {string} storyString
 * @returns {string}
 */
export function formatInstructModeStoryString(storyString) {
    const instruct = getInstruct();
    const pre = instruct.story_string_prefix || '';
    const suf = instruct.story_string_suffix || '';
    return (instruct.macro ? substitute(pre) : pre) + String(storyString ?? '') + (instruct.macro ? substitute(suf) : suf);
}

/**
 * 包装对话示例（ST instruct-mode.js:760 附近）。
 * @param {string} example
 * @param {string} [name1]
 * @param {string} [name2]
 * @returns {string}
 */
export function formatInstructModeExamples(example, name1, name2) {
    const instruct = getInstruct();
    const out = [];
    for (const block of String(example ?? '').split(/<START>/gi)) {
        const trimmed = block.trim();
        if (!trimmed) continue;
        out.push(formatInstructModePrompt('', false, '', name1, name2, false, false));
        out.push(trimmed);
    }
    return out.join('');
}

/**
 * ST Instruct 宏表（instruct-mode.js:668 附近）。
 * @param {object} [env]
 * @returns {Array<{key: string, value: string, enabled: boolean}>}
 */
export function getInstructMacros(env) {
    const i = getInstruct();
    const enabled = !!i.enabled;
    void env;
    return [
        { key: 'instructStoryStringPrefix', value: i.story_string_prefix, enabled },
        { key: 'instructStoryStringSuffix', value: i.story_string_suffix, enabled },
        { key: 'instructInput|instructUserPrefix', value: i.input_sequence, enabled },
        { key: 'instructUserSuffix', value: i.input_suffix, enabled },
        { key: 'instructFirstInput|instructFirstUserPrefix', value: i.first_input_sequence, enabled },
        { key: 'instructFirstUserSuffix', value: i.input_suffix, enabled },
        { key: 'instructLastInput|instructLastUserPrefix', value: i.last_input_sequence, enabled },
        { key: 'instructLastUserSuffix', value: i.input_suffix, enabled },
        { key: 'instructOutput|instructAssistantPrefix', value: i.output_sequence, enabled },
        { key: 'instructAssistantSuffix', value: i.output_suffix, enabled },
        { key: 'instructFirstOutput|instructFirstAssistantPrefix', value: i.first_output_sequence, enabled },
        { key: 'instructFirstAssistantSuffix', value: i.output_suffix, enabled },
        { key: 'instructLastOutput|instructLastAssistantPrefix', value: i.last_output_sequence, enabled },
        { key: 'instructLastAssistantSuffix', value: i.output_suffix, enabled },
        { key: 'instructSystem|instructSystemPrefix', value: i.system_sequence, enabled },
        { key: 'instructSystemSuffix', value: i.system_suffix, enabled },
    ];
}

/**
 * ST Instruct 停止序列（instruct-mode.js:820 附近）。
 * @returns {string[]}
 */
export function getInstructStoppingSequences() {
    const i = getInstruct();
    const out = [];
    const push = (s) => { const t = String(s ?? '').trim(); if (t && !out.includes(t)) out.push(t); };
    push(i.input_sequence); push(i.last_input_sequence);
    push(i.system_sequence); push(i.last_system_sequence);
    if (i.stop_sequence) String(i.stop_sequence).split('\n').forEach(push);
    return out;
}

// ---------------------------------------------------------------- 宿主承担的部分（no-op）
/** 设置页由 Compose 承担。 */
export async function loadInstructMode() { /* no-op by design */ }
/** 上下文模板选择由 Compose 承担。 */
export function selectContextPreset() { /* no-op by design */ }
/** Instruct 预设选择由 Compose 承担。 */
export function selectInstructPreset() { /* no-op by design */ }
/** 自动匹配预设由 Compose 承担。 */
export function autoSelectInstructPreset() { /* no-op by design */ }


// ============================================================================
// [今晚 P4] 补齐 ST 1.19.0 `public/scripts/instruct-mode.js` 的 4 个缺失导出。
//
// `_st_export_inventory.mjs` 报的「ST 导出 1331，缺 4」就是这 4 个：
//   force_output_sequence      ← ST :369
//   formatInstructModeChat     ← ST :387
//   instruct_presets           ← ST :15
//   updateBindModelTemplatesState ← ST :162
//
// 实测 38 个真实插件里**无人 import 这 4 个符号**
// （`grep -rl "formatInstructModeChat|updateBindModelTemplatesState|force_output_sequence|instruct_presets"`
//   扫整个插件语料 → 0 命中），它们只被 ST 自己的 script.js / custom-request.js / power-user.js 用。
// 所以优先级低，但属于供给面缺口 —— 补上让 `import` 不报错，形状与上游一致。
// ============================================================================

/** ST instruct-mode.js:369 —— 强制使用首/尾输出序列。 */
export const force_output_sequence = {
    FIRST: 1,
    LAST: 2,
};

/**
 * ST instruct-mode.js:15 —— instruct 预设列表。
 *
 * 上游是 `export let instruct_presets = []`，由 `loadInstructMode()` 从
 * `instruct-presets.json` 填充（:113 `instruct_presets = data.instruct`）。
 *
 * 我们是宿主承担预设管理（见文件头 DIVERGENCE），所以这里是**只读空数组**。
 * 用 `let` 而非 `const` 保持与上游一致的活绑定语义（将来若要填充无需改导出）。
 */
export let instruct_presets = [];

/** 供宿主/兼容层在加载预设后回填（对齐上游 :113 的赋值点）。 */
export function __setInstructPresets(list) {
    instruct_presets = Array.isArray(list) ? list : [];
}

/**
 * ST instruct-mode.js:387 —— 把一条消息按 Instruct 模板包装。
 *
 * 与 [formatInstructModePrompt] 的区别：那个包装「待生成的提示」，
 * 这个包装「已有的历史消息」（用于 text-completion 的对话拼接）。
 *
 * 语义逐条对齐上游 :
 *   - getPrefix：按 isUser / isNarrator / forceOutputSequence 选序列
 *   - getSuffix：同理选后缀
 *   - includeNames：names_behavior === ALWAYS，或 FORCE 且（群聊/forceAvatar）且不是自己
 *
 * @param {string} name 消息作者名
 * @param {string} mes 消息正文
 * @param {boolean} isUser 是否用户消息
 * @param {boolean} isNarrator 是否旁白
 * @param {string} forceAvatar 强制头像（非自己时用于 FORCE 判定）
 * @param {string} name1 用户名
 * @param {string} name2 角色名
 * @param {boolean|number} forceOutputSequence 强制首/尾序列
 * @param {object|null} customInstruct 自定义 instruct 设置
 * @returns {string}
 */
export function formatInstructModeChat(name, mes, isUser, isNarrator, forceAvatar, name1, name2, forceOutputSequence, customInstruct = null) {
    const instruct = Object.assign({}, getInstruct(), customInstruct || {});
    const nb = Number(instruct.names_behavior);

    let includeNames = isNarrator ? false : nb === names_behavior_types.ALWAYS;
    if (!isNarrator && nb === names_behavior_types.FORCE) {
        let groupId = null;
        try { groupId = st().groupId || null; } catch (_e) { groupId = null; }
        if ((groupId && name !== name1) || (forceAvatar && name !== name1)) includeNames = true;
    }

    function getPrefix() {
        if (isNarrator) {
            return instruct.system_same_as_user ? instruct.input_sequence : instruct.system_sequence;
        }
        if (isUser) {
            if (forceOutputSequence === force_output_sequence.FIRST) {
                return instruct.first_input_sequence || instruct.input_sequence;
            }
            if (forceOutputSequence === force_output_sequence.LAST) {
                return instruct.last_input_sequence || instruct.input_sequence;
            }
            return instruct.input_sequence;
        }
        if (forceOutputSequence === force_output_sequence.FIRST) {
            return instruct.first_output_sequence || instruct.output_sequence;
        }
        if (forceOutputSequence === force_output_sequence.LAST) {
            return instruct.last_output_sequence || instruct.output_sequence;
        }
        return instruct.output_sequence;
    }

    function getSuffix() {
        if (isNarrator) {
            return instruct.system_same_as_user ? instruct.input_suffix : instruct.system_suffix;
        }
        return isUser ? instruct.input_suffix : instruct.output_suffix;
    }

    const prefix = String(getPrefix() || '');
    const suffix = String(getSuffix() || '');
    const body = String(mes == null ? '' : mes);

    if (isNarrator) return prefix + body + suffix;
    if (includeNames) {
        const displayName = isUser ? (name1 || name) : (name2 || name);
        return prefix + displayName + ': ' + body + suffix;
    }
    return prefix + body + suffix;
}

/**
 * ST instruct-mode.js:162 —— 根据当前模型/模板回填「绑定模型模板」勾选状态。
 *
 * 上游读 `power_user.model_templates_mappings[online_status]` 与
 * `power_user.context.preset` / `power_user.instruct.preset` 比对，然后改
 * `#bind_model_templates` 的 checked。
 *
 * 我们是宿主设置页承担（Compose），没有这个 checkbox，所以做成**安全的 no-op**：
 * 不 throw、不改 DOM、返回当前比对结果。这样 `power-user.js` 那类调用方
 * import 得到函数、调用不崩，且拿到一个布尔结果。
 *
 * @returns {boolean} 当前是否匹配
 */
export function updateBindModelTemplatesState() {
    try {
        const pu = (typeof globalThis !== 'undefined' ? globalThis.power_user : null) || null;
        if (!pu) return false;
        const mappings = pu.model_templates_mappings || {};
        const bind = mappings[pu.online_status] ?? mappings[pu.chat_template_hash];
        if (!bind) return false;
        const contextMatch = pu.context && pu.context.preset === bind.context;
        const instructMatch = !pu.instruct || !pu.instruct.enabled || pu.instruct.preset === bind.instruct;
        return Boolean(contextMatch && instructMatch);
    } catch (_e) {
        return false;
    }
}
