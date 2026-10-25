#!/usr/bin/env node
/**
 * SPreset 真实预设自测（Node，无 Android 依赖）。
 *
 * 用法：
 *   node scripts/spreset-core/verify.mjs
 *   node scripts/spreset-core/verify.mjs "E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json"
 */
import fs from 'node:fs';
import assert from 'node:assert/strict';

const defaultPresetPath = 'E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json';
const presetPath = process.argv[2] || defaultPresetPath;

const raw = fs.readFileSync(presetPath, 'utf8').replace(/^\uFEFF/, '');
const root = JSON.parse(raw);
const sp = root?.extensions?.SPreset;
assert.ok(sp, 'extensions.SPreset 不存在');

function evalScript(script) {
  // 真实脚本多数是 `({ ... }) => {...}` 或 `content => {...}`，末尾可能带分号。
  // eval 表达式语句本身会返回函数值，不需要额外包裹括号。
  return eval(script);
}

function line(message) {
  process.stdout.write(`✓ ${message}\n`);
}

// ---------- 1. 六个扩展键与字段结构 ----------
const expectedKeys = [
  'ChatSquash',
  'RegexBinding',
  'MacroNest',
  'ToolBindings',
  'MessageInjections',
  'OutputPreprocessing',
  'FixedPresetName',
  'ForcedPostProcessing',
  'FinalRequestProcessing',
];
for (const key of expectedKeys) {
  assert.ok(Object.prototype.hasOwnProperty.call(sp, key), `缺少 SPreset.${key}`);
}
line(`SPreset 九个已知键齐全：${expectedKeys.join(', ')}`);

const toolEntries = Object.entries(sp.ToolBindings ?? {});
assert.equal(toolEntries.length, 2, 'ToolBindings 应有 2 个');
const enabledTools = toolEntries.filter(([, value]) => value?.enabled === true);
assert.equal(enabledTools.length, 1, '应只有 1 个 enabled ToolBinding');
const gameTool = enabledTools[0][1];
assert.equal(gameTool.form?.name, 'game_content', 'enabled 工具应为 game_content');
assert.equal(gameTool.resolvedName, 'game_content');
assert.deepEqual(gameTool.form?.parameters?.map((item) => item.name), ['content']);
line('ToolBindings：2 个，仅 game_content enabled，form.parameters 可用');

const op = sp.OutputPreprocessing;
assert.equal(op?.enabled, true, 'OutputPreprocessing.enabled 应为 true');
assert.equal(op?.consumeToolCalls, true, 'OutputPreprocessing.consumeToolCalls 应为 true');
assert.equal(op?.toolCallFormatter?.enabled, true, 'toolCallFormatter.enabled 应为 true');
line('OutputPreprocessing：enabled=true，consumeToolCalls=true，formatter enabled');

assert.equal(sp.FinalRequestProcessing?.enabled, true, 'FinalRequestProcessing.enabled 应为 true');
assert.equal(sp.FixedPresetName, '', 'FixedPresetName 应为空');
assert.equal(sp.ForcedPostProcessing?.enabled, true, 'ForcedPostProcessing.enabled 应为 true');
assert.equal(sp.ForcedPostProcessing?.mode, '', 'ForcedPostProcessing.mode 应为空');
line('FinalRequestProcessing enabled；FixedPresetName 空；ForcedPostProcessing enabled/mode 空');

const injections = Object.entries(sp.MessageInjections ?? {});
assert.equal(injections.length, 2, 'MessageInjections 应有 2 个');
assert.ok(injections.every(([, value]) => value?.enabled === false), 'MessageInjections 应全部 disabled');
line('MessageInjections：2 个，全部 disabled');

assert.equal(sp.ChatSquash?.conditional_enabled, true, 'ChatSquash.conditional_enabled 应为 true');
assert.equal(sp.ChatSquash?.conditional_tag, '<|merge|>');
assert.equal(sp.ChatSquash?.squashed_separator_string, '<|sep|>');
assert.equal(sp.ChatSquash?.squashed_post_script_enable, true);
line('ChatSquash：conditional_enabled/conditional_tag/separator/post-script 均为真实值');

// ---------- 2. ToolBindings code 可执行 ----------
for (const [id, binding] of toolEntries) {
  assert.equal(typeof binding.code, 'string');
  const tool = new Function(binding.code)();
  assert.equal(tool.name, binding.form?.name, `${id} code 返回 name 与 form.name 不一致`);
  assert.equal(typeof tool.action, 'function', `${id} action 不是函数`);
}
const gameToolObject = new Function(gameTool.code)();
const actionResult = await gameToolObject.action({ content: '你好' });
assert.equal(actionResult, JSON.stringify({ ok: true }));
line('ToolBindings code：new Function(code)() 可解析，game_content.action 返回 {"ok":true}');

// ---------- 3. OutputPreprocessing.toolCallFormatter ----------
const originalLog = console.log;
console.log = () => {};
try {
  const formatter = evalScript(op.toolCallFormatter.script);
  const formatted = await formatter({
    calls: [
      { name: 'game_content', arguments: { content: '你好' } },
      { name: 'soliumbra_think', arguments: { thinking: '想一下' } },
    ],
  });
  assert.match(formatted, /你好/, 'formatter 输出应包含 game_content.content');
  assert.match(formatted, /think_nya~/, 'formatter 输出应包含 think_nya 标签');
  assert.match(formatted, /想一下/, 'formatter 输出应包含 thinking');
  line(`toolCallFormatter：${JSON.stringify(formatted)}`);

  // ---------- 4. OutputPreprocessing.script ----------
  const outputFn = evalScript(op.script);
  const processed = await outputFn({
    buffer: 'hello',
    final: false,
    state: {},
  });
  assert.equal(processed.output, 'hello');
  assert.equal(processed.hold, '');
  assert.equal(processed.state.accepted, true);
  line('OutputPreprocessing.script：console.log 可执行，返回 output/hold/state');
} finally {
  console.log = originalLog;
}

// ---------- 5. FinalRequestProcessing ----------
const marker = '<run_tool_replacement>';
const finalFn = evalScript(sp.FinalRequestProcessing.script);
const finalRequest = {
  messages: [
    { role: 'system', content: `prefix\n${marker}\nsuffix` },
    { role: 'assistant', content: '|用户|hi\n|小猫之神|hello' },
    { role: 'user', content: '继续' },
  ],
  tools: [
    {
      type: 'function',
      function: {
        name: 'game_content',
        description: 'MANDATORY',
        parameters: { type: 'object', properties: {} },
      },
    },
  ],
};
const finalResult = await finalFn({ request: JSON.parse(JSON.stringify(finalRequest)) });
assert.equal(finalResult.messages.length, 2, 'FinalRequestProcessing 应把消息缩为 2 条');
assert.deepEqual(finalResult.messages.map((message) => message.role), ['system', 'user']);
assert.ok(!finalResult.messages[0].content.includes(marker), 'system 中 marker 应被移除');
assert.equal(finalResult.tools.at(-1)?.function?.name, 'context', 'tools 末尾应出现 context');
assert.equal(
  finalResult.tools.at(-1)?.function?.description,
  '|用户|hi\n|小猫之神|hello',
  'context.description 应来自 assistant 原文',
);
assert.equal(
  finalResult.tools.filter((tool) => tool?.function?.name === 'context').length,
  1,
  'context 只能出现一次',
);
line('FinalRequestProcessing：3→2 条消息，marker 清除，tools 末尾 context');

// ---------- 6. ChatSquash post script ----------
const postScript = evalScript(sp.ChatSquash.squashed_post_script);
const postSample = '|用户：小明|\n|小猫之神|喵~\n|用户|你好\n<summary>总结</summary>';
const postOutput = await postScript(postSample);
assert.match(postOutput, /"role": "soliumbra"/, 'post script 应生成 soliumbra 边界');
assert.match(postOutput, /"role": "小明"/, 'post script 应生成用户名边界');
line('ChatSquash.squashed_post_script：可 eval，用户名/soliumbra 边界生成');

// ---------- 7. 条件折叠 + FinalRequestProcessing 端到端 ----------
const squash = sp.ChatSquash;
const substitute = (value) =>
  String(value)
    .replace(/\{\{user}}/gi, '小明')
    .replace(/\{\{char}}/gi, '小猫之神')
    .replace(/\{\{trim}}/gi, '');
const prefixFor = (role) =>
  role === 'system' ? squash.prefix_system : role === 'user' ? squash.user_prefix : squash.char_prefix;
const suffixFor = (role) =>
  role === 'system' ? squash.suffix_system : role === 'user' ? squash.user_suffix : squash.char_suffix;
const clean = (text) =>
  String(text)
    .replaceAll(squash.conditional_tag, '')
    .replaceAll(squash.squashed_separator_string, '');

const markerMessage = {
  role: 'system',
  content: '{{trim}}<|sep|><|merge|><run_tool_replacement>',
};
const lastUser = { role: 'user', content: '{{user}}：继续' };
// 故意在 lastUser 之后再放一条 AFTER_DIALOG system，验证不会被丢弃。
const allMessages = [
  { role: 'system', content: '<初始角色卡>' },
  markerMessage,
  { role: 'assistant', content: '|小猫之神|喵~' },
  lastUser,
  { role: 'system', content: 'AFTER_DIALOG：历史后置指令' },
];
const folded = allMessages
  .filter((message) => message !== markerMessage && message !== lastUser)
  .map((message) => `${substitute(prefixFor(message.role))}${clean(message.content)}${substitute(suffixFor(message.role))}`)
  .join('\n');
assert.match(folded, /AFTER_DIALOG：历史后置指令/, 'lastUser 之后的 system 必须折叠进 assistant');
const finalAssistant = await postScript(folded);
assert.match(finalAssistant, /AFTER_DIALOG：历史后置指令/, 'post script 后仍应保留 AFTER_DIALOG');
const strictThree = [
  { role: 'system', content: clean(markerMessage.content) },
  { role: 'assistant', content: finalAssistant },
  lastUser,
];
assert.equal(strictThree.length, 3);
assert.deepEqual(strictThree.map((message) => message.role), ['system', 'assistant', 'user']);
const e2e = await finalFn({
  request: {
    messages: strictThree,
    tools: [{ type: 'function', function: { name: 'game_content', description: 'x', parameters: { type: 'object', properties: {} } } }],
  },
});
assert.equal(e2e.messages.length, 2);
assert.equal(e2e.tools.at(-1)?.function?.name, 'context');
assert.match(e2e.tools.at(-1)?.function?.description ?? '', /AFTER_DIALOG/);
line('条件折叠端到端：严格 [system, assistant, user] → FinalRequestProcessing 命中且 AFTER_DIALOG 保留');

console.log('\n全部 SPreset Node 自测通过。');
