import fs from 'node:fs';
import vm from 'node:vm';

export const DEFAULT_PRESET_PATH = 'E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json';
export const FALLBACK_FIXTURE_PATH = new URL('../fixtures/spreset-cat-god.json', import.meta.url);

export function resolvePresetPath(explicitPath) {
  const candidate = explicitPath ?? process.env.SPRESET_JSON ?? DEFAULT_PRESET_PATH;
  if (fs.existsSync(candidate)) return candidate;
  if (fs.existsSync(FALLBACK_FIXTURE_PATH)) return FALLBACK_FIXTURE_PATH;
  throw new Error(`SPreset JSON not found: ${candidate} (and fixture missing)`);
}

export function loadPreset(explicitPath) {
  const presetPath = resolvePresetPath(explicitPath);
  const root = JSON.parse(fs.readFileSync(presetPath, 'utf8'));
  const spreset = root.extensions?.SPreset;
  if (!spreset) throw new Error(`${presetPath}: missing extensions.SPreset`);
  return { root, spreset, presetPath };
}

function createSandbox() {
  const logs = [];
  const context = vm.createContext({
    console: {
      log: (...args) => logs.push(['log', args.map(safeString).join(' ')]),
      info: (...args) => logs.push(['info', args.map(safeString).join(' ')]),
      warn: (...args) => logs.push(['warn', args.map(safeString).join(' ')]),
      error: (...args) => logs.push(['error', args.map(safeString).join(' ')]),
      debug: (...args) => logs.push(['debug', args.map(safeString).join(' ')]),
    },
  });
  return { context, logs };
}

function safeString(value) {
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/**
 * The preset stores arrow functions as strings, usually ending with `;`.
 * `(src;)` is invalid, so strip terminal semicolons before wrapping.
 */
export function evalFunctionExpression(source, { label = 'spreset-script', timeout = 5000 } = {}) {
  if (typeof source !== 'string' || !source.trim()) throw new Error(`${label}: empty script`);
  const { context, logs } = createSandbox();
  const expression = source.replace(/;[\s;]*$/, '');
  const fn = vm.runInContext(`(${expression})`, context, {
    filename: `${label}.js`,
    timeout,
  });
  if (typeof fn !== 'function') throw new Error(`${label}: script did not evaluate to a function`);
  return { fn, logs };
}

export async function callFunctionExpression(source, arg, options) {
  const { fn, logs } = evalFunctionExpression(source, options);
  const result = await fn(arg);
  return { result, logs };
}

/** ToolBindings.code is a function body: `new Function(code)()` semantics. */
export function evalBindingFactory(code, { label = 'tool-binding', timeout = 5000 } = {}) {
  if (typeof code !== 'string' || !code.trim()) throw new Error(`${label}: empty code`);
  const { context, logs } = createSandbox();
  const factory = vm.runInContext(`(function () {\n${code}\n})`, context, {
    filename: `${label}.js`,
    timeout,
  });
  if (typeof factory !== 'function') throw new Error(`${label}: code did not evaluate to a function`);
  return { factory, logs };
}

export async function executeToolBinding(binding) {
  const { factory, logs } = evalBindingFactory(binding.code, { label: `tool-${binding.resolvedName ?? 'binding'}` });
  const tool = await factory();
  const actionResult = await tool.action({ __probe: true });
  return { tool, actionResult, logs };
}

/** Convert SPreset form.parameters array into OpenAI JSON Schema. */
export function formParametersToJsonSchema(binding) {
  const form = binding?.form ?? {};
  const params = Array.isArray(form.parameters) ? form.parameters : [];
  const properties = {};
  const required = [];
  for (const p of params) {
    if (!p || typeof p.name !== 'string' || !p.name) continue;
    const schema = {};
    if (typeof p.type === 'string' && p.type) schema.type = p.type;
    if (typeof p.description === 'string' && p.description) schema.description = p.description;
    if (Array.isArray(p.enum) && p.enum.length) schema.enum = p.enum;
    properties[p.name] = schema;
    if (p.required === true) required.push(p.name);
  }
  return {
    type: 'object',
    properties,
    ...(required.length ? { required } : {}),
  };
}

export function substituteParams(text, { userName = 'User', charName = 'Character' } = {}) {
  if (typeof text !== 'string') return text;
  return text
    .replace(/\{\{\s*trim\s*\}\}/gi, '')
    .replace(/\{\{\s*user\s*\}\}/gi, userName)
    .replace(/\{\{\s*char\s*\}\}/gi, charName);
}

/** Faithful port of SPreset 1.0.0 postProcess()'s parse_clewd branch. */
export function hyperPmtProcess(content) {
  const hyperRegex = (input, order) => {
    const regexPattern =
      '<regex(?: +order *= *' +
      order +
      ')' +
      (order === 2 ? '?' : '') +
      '> *"(/?)(.*)\\1(.*?)" *: *"(.*?)" *</regex>';
    const matches = input.match(new RegExp(regexPattern, 'gm'));
    if (matches) {
      for (const match of matches) {
        try {
          const reg = /<regex(?: +order *= *\d)?> *"(\/?)(.*)\1(.*?)" *: *"(.*?)" *<\/regex>/.exec(match);
          if (!reg) continue;
          const replacePattern = new RegExp(reg[2], reg[3]);
          const replacement = JSON.parse('"' + reg[4].replace(/\\?"/g, '\\"') + '"');
          input = input.replace(replacePattern, replacement);
        } catch {
          // Preset ignores malformed regex directives.
        }
      }
    }
    return input;
  };

  let output = content;
  output = hyperRegex(output, 1);
  output = hyperRegex(output, 2);
  output = hyperRegex(output, 3);
  output = output
    .replace(/<regex( +order *= *\d)?>.*?<\/regex>/gm, '')
    .replace(/\r\n|\r/gm, '\n')
    .replace(/\s*<\|curtail\|>\s*/g, '\n')
    .replace(/\s*<\|join\|>\s*/g, '')
    .replace(/\s*<\|space\|>\s*/g, ' ')
    .replace(/<\|(\\.*?)\|>/g, (match, p1) => {
      try {
        return JSON.parse('"' + p1 + '"');
      } catch {
        return match;
      }
    });
  return output
    .replace(/\s*<\|.*?\|>\s*/g, '\n\n')
    .trim()
    .replace(/^.+:/, '\n\n$&')
    .replace(/(?<=\n)\n(?=\n)/g, '');
}

export function createPostProcessor(chatSquash, { userName = 'User', charName = 'Character', logs = [] } = {}) {
  let postFn = null;
  let compileError = null;
  if (chatSquash?.squashed_post_script_enable && chatSquash?.squashed_post_script) {
    try {
      postFn = evalFunctionExpression(chatSquash.squashed_post_script, {
        label: 'squashed-post-script',
      }).fn;
    } catch (error) {
      compileError = error;
    }
  }
  return {
    compileError,
    postProcess(content) {
      let output = String(content ?? '');
      if (chatSquash?.parse_clewd) output = hyperPmtProcess(output);
      if (postFn) {
        const backup = output;
        try {
          const next = postFn(output);
          output = typeof next === 'string' ? next : String(next ?? '');
        } catch (error) {
          logs.push(['warn', `post script failed: ${error?.message ?? error}`]);
          output = backup;
        }
      }
      return output;
    },
  };
}

function rolePrefix(chatSquash, role, names) {
  switch (role) {
    case 'system':
      return substituteParams(chatSquash.prefix_system ?? '', names);
    case 'user':
      return substituteParams(chatSquash.user_prefix ?? '', names);
    case 'assistant':
      return substituteParams(chatSquash.char_prefix ?? '', names);
    default:
      return '';
  }
}

function roleSuffix(chatSquash, role, names) {
  switch (role) {
    case 'system':
      return substituteParams(chatSquash.suffix_system ?? '', names);
    case 'user':
      return substituteParams(chatSquash.user_suffix ?? '', names);
    case 'assistant':
      return substituteParams(chatSquash.char_suffix ?? '', names);
    default:
      return '';
  }
}

/**
 * Faithful port of starowo/SPreset 1.0.0 `squashPrompts` (inject.js lines 1025-1136).
 * The caller must supply postProcess (old code runs it only on merged chunks).
 */
export function oldSquashPrompts(prompts, chatSquash, { userName = 'User', charName = 'Character', postProcess = (s) => s } = {}) {
  const names = { userName, charName };
  let squashRole = chatSquash.role;
  if (chatSquash.role === 'follow') squashRole = prompts[0]?.role ?? 'assistant';

  const newPrompts = prompts.map((p) => ({ ...p }));
  const output = [];
  let lastRole = '';
  let mergedContent = '';

  const appendSuffix = (role) => {
    mergedContent += roleSuffix(chatSquash, role, names);
  };
  const appendPrefix = (role) => {
    mergedContent += rolePrefix(chatSquash, role, names);
  };
  const flush = () => {
    if (!mergedContent) return;
    appendSuffix(lastRole);
    output.push({ role: squashRole, content: postProcess(mergedContent) });
    mergedContent = '';
  };

  for (const prompt of newPrompts) {
    if (!prompt.content) continue;
    if (chatSquash.user_role_system && prompt.role === 'system') prompt.role = 'user';

    let separate = false;
    if (chatSquash.enable_squashed_separator && chatSquash.squashed_separator_string) {
      if (chatSquash.squashed_separator_regex) {
        const regex = new RegExp(chatSquash.squashed_separator_string);
        if (regex.test(prompt.content)) {
          separate = true;
          prompt.content = prompt.content.replace(regex, '');
        }
      } else if (prompt.content.includes(chatSquash.squashed_separator_string)) {
        prompt.content = prompt.content.replace(chatSquash.squashed_separator_string, '');
        separate = true;
      }
    }
    if (!separate && chatSquash.parse_clewd) {
      const marker = '<|no-trans|>';
      if (prompt.content.includes(marker)) {
        separate = true;
        prompt.content = prompt.content.replace(marker, '');
      }
    }

    if (separate) {
      if (mergedContent) {
        flush();
      }
      if (chatSquash.role === 'follow') squashRole = prompt.role;
      lastRole = '';
      output.push(prompt);
      continue;
    }

    if (prompt.role !== lastRole) {
      if (lastRole) appendSuffix(lastRole);
      appendPrefix(prompt.role);
    } else {
      mergedContent += '\n';
    }
    mergedContent += prompt.content;
    lastRole = prompt.role;
  }
  if (mergedContent) flush();

  return output;
}

function promptRoleOf(prompt) {
  const role = prompt?.role;
  return role === 'user' || role === 'assistant' || role === 'system' ? role : 'system';
}

/** Build a representative app-shaped message list from the real prompt_order. */
export function buildAppLikeMessages(preset, {
  userName = '小明',
  charName = '小猫之神',
  baseSystem = '你是小猫之神·喵赛亚。',
  history,
} = {}) {
  const { root, spreset } = preset;
  const orderGroups = (root.prompt_order ?? []).filter((g) => Array.isArray(g.order) && g.order.length);
  const orderGroup = orderGroups.find((g) => g.character_id === 100001) ?? orderGroups[0];
  const order = orderGroup?.order ?? [];
  const promptById = new Map();
  for (const p of root.prompts ?? []) {
    if (p?.identifier && !promptById.has(p.identifier)) promptById.set(p.identifier, p);
  }
  const chatHistoryIdx = order.findIndex((e) => e.identifier === 'chatHistory');

  const beforeSystem = [];
  const afterDialog = [];
  order.forEach((entry, idx) => {
    const prompt = promptById.get(entry.identifier);
    if (!prompt || entry.enabled === false || prompt.marker) return;
    if (!prompt.content || !String(prompt.content).trim()) return;
    if (promptRoleOf(prompt) !== 'system') return;
    if (chatHistoryIdx < 0 || idx < chatHistoryIdx) beforeSystem.push(prompt.content);
    else afterDialog.push(prompt.content);
  });

  const afterSystemText = beforeSystem
    .map((s) => substituteParams(String(s), { userName, charName }))
    .join('\n');
  const systemText = [baseSystem, afterSystemText].filter(Boolean).join('\n');
  const afterDialogText = afterDialog
    .map((s) => substituteParams(String(s), { userName, charName }))
    .join('\n');

  const defaultHistory = [
    { role: 'user', content: '|用户|喵喵喵——伟大又可爱的小猫之神，请聆听您的信徒最虔诚的呼唤——' },
    { role: 'assistant', content: '|小猫之神|喵——哈~ 干什么嘛，刚睡醒就找我……是哪位信徒在召唤我喵？' },
    { role: 'user', content: '|用户|诶，诶诶？是真的小猫之神吗！' },
    { role: 'assistant', content: '|小猫之神|当然是货真价实的神喵……你这家伙居然敢质疑本喵！' },
    { role: 'user', content: '|用户|好耶，那开始游戏吧！' },
  ];
  const historyMessages = (history ?? defaultHistory).map((m) => ({ ...m }));
  const messages = [{ role: 'system', content: systemText }, ...historyMessages];

  if (afterDialogText) {
    const lastAssistantIdx = messages.map((m) => m.role).lastIndexOf('assistant');
    const insertAt = lastAssistantIdx >= 0 ? lastAssistantIdx + 1 : messages.length;
    messages.splice(insertAt, 0, { role: 'system', content: afterDialogText });
  }

  return {
    messages,
    meta: {
      promptCount: beforeSystem.length + afterDialog.length,
      beforeSystemPromptCount: beforeSystem.length,
      afterDialogPromptCount: afterDialog.length,
      afterDialogTextLength: afterDialogText.length,
      systemTextLength: systemText.length,
    },
  };
}

function messageContentToString(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    return content.map((part) => typeof part === 'string' ? part : part?.text ?? '').join('\n');
  }
  return String(content ?? '');
}

function messagesContain(messages, needle) {
  return messages.some((m) => messageContentToString(m.content).includes(needle));
}

/**
 * Proposed fix for the newer conditional_tag feature:
 * - keep the first system message (which must carry <run_tool_replacement>),
 * - strip <|sep|>/<|merge|> there,
 * - merge every other non-last-user message into ONE assistant block using the same
 *   prefix/suffix/role-transition rules as squashPrompts,
 * - run squashed_post_script on that block,
 * - place the last user message back at the end.
 */
export function proposedConditionalBlockMerge(messages, chatSquash, {
  userName = 'User',
  charName = 'Character',
  postProcess = (s) => s,
  requireConditionalTag = true,
} = {}) {
  const tag = chatSquash.conditional_tag ?? '';
  const source = messages.map((m) => ({ ...m }));
  if (requireConditionalTag && chatSquash.conditional_enabled && tag && !messagesContain(source, tag)) {
    return { changed: false, reason: 'conditional-tag-missing', messages: source, blockRoles: [] };
  }

  const lastUserIdx = source.map((m) => m.role).lastIndexOf('user');
  if (lastUserIdx <= 0 || source[0]?.role !== 'system') {
    return { changed: false, reason: 'shape-not-supported', messages: source, blockRoles: [] };
  }

  const names = { userName, charName };
  const system = { ...source[0] };
  let systemText = messageContentToString(system.content);
  if (chatSquash.enable_squashed_separator && chatSquash.squashed_separator_string) {
    systemText = systemText.split(chatSquash.squashed_separator_string).join('');
  }
  if (tag) systemText = systemText.split(tag).join('');
  system.content = systemText;

  const block = source.filter((_, idx) => idx !== 0 && idx !== lastUserIdx);
  let mergedContent = '';
  let lastRole = '';
  for (const prompt of block) {
    let role = promptRoleOf(prompt);
    if (chatSquash.user_role_system && role === 'system') role = 'user';
    const content = messageContentToString(prompt.content);
    if (!content) continue;
    if (role !== lastRole) {
      if (lastRole) mergedContent += roleSuffix(chatSquash, lastRole, names);
      mergedContent += rolePrefix(chatSquash, role, names);
    } else if (mergedContent) {
      mergedContent += '\n';
    }
    mergedContent += content;
    lastRole = role;
  }
  if (mergedContent) mergedContent += roleSuffix(chatSquash, lastRole, names);

  const assistantContent = postProcess(mergedContent);
  const finalRole = chatSquash.role === 'follow' ? 'assistant' : (chatSquash.role || 'assistant');
  const output = [
    system,
    { role: finalRole, content: assistantContent },
    { ...source[lastUserIdx] },
  ];
  return {
    changed: true,
    reason: 'ok',
    messages: output,
    blockRoles: block.map((m) => promptRoleOf(m)),
    rawMergedLength: mergedContent.length,
    assistantLength: assistantContent.length,
  };
}

export function createSpresetHarness(preset) {
  const { spreset } = preset;
  const finalFn = evalFunctionExpression(spreset.FinalRequestProcessing?.script ?? '', {
    label: 'final-request-processing',
  }).fn;
  const formatterFn = evalFunctionExpression(spreset.OutputPreprocessing?.toolCallFormatter?.script ?? '', {
    label: 'tool-call-formatter',
  }).fn;
  const outputFn = evalFunctionExpression(spreset.OutputPreprocessing?.script ?? '', {
    label: 'output-preprocessing',
  }).fn;
  return {
    async formatToolCalls(calls) {
      return formatterFn({ calls });
    },
    async processOutput({ buffer, final = false, state = {} }) {
      return outputFn({ buffer, final, state });
    },
    async processFinalRequest(request) {
      return finalFn({ request });
    },
  };
}
