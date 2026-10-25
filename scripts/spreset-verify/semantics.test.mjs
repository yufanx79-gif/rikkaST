import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

import {
  loadPreset,
  resolvePresetPath,
  evalFunctionExpression,
  callFunctionExpression,
  executeToolBinding,
  formParametersToJsonSchema,
  oldSquashPrompts,
  buildAppLikeMessages,
  createPostProcessor,
  proposedConditionalBlockMerge,
  createSpresetHarness,
} from './lib/spreset.mjs';

const plain = (value) => JSON.parse(JSON.stringify(value));

const preset = loadPreset();
const { spreset } = preset;
const chatSquash = spreset.ChatSquash;

test('preset source', () => {
  console.log('SPRESET_JSON =', preset.presetPath);
  assert.ok(fs.existsSync(preset.presetPath));
});

test('six private SPreset keys and their structure', async (t) => {
  const sixKeys = [
    'ToolBindings',
    'MessageInjections',
    'OutputPreprocessing',
    'FixedPresetName',
    'ForcedPostProcessing',
    'FinalRequestProcessing',
  ];
  for (const key of sixKeys) {
    assert.ok(Object.hasOwn(spreset, key), `missing ${key}`);
  }

  await t.test('ToolBindings: 2 entries, only game_content enabled', () => {
    const bindings = Object.values(spreset.ToolBindings);
    assert.equal(bindings.length, 2);
    const byName = new Map(bindings.map((b) => [b.resolvedName, b]));

    const game = byName.get('game_content');
    assert.ok(game);
    assert.equal(game.enabled, true);
    assert.equal(game.valid, true);
    assert.equal(game.editorMode, 'form');
    assert.equal(game.form.name, 'game_content');
    assert.equal(game.form.displayName, '');
    assert.equal(game.form.description, 'MANDATORY, You MUST use this tool to reply.');
    assert.deepEqual(game.form.parameters, [
      { name: 'content', type: 'string', description: '', required: true },
    ]);
    assert.equal(game.form.stealth, false);
    assert.equal(game.uuid, 'da14679f-7aad-4cd1-b66d-9790a51f0e70');

    const think = byName.get('soliumbra_think');
    assert.ok(think);
    assert.equal(think.enabled, false);
    assert.equal(think.valid, true);
    assert.equal(think.form.name, 'soliumbra_think');
    assert.equal(think.form.description, 'Mandatory. 一定要使用这个工具进行思考。');
    assert.deepEqual(think.form.parameters, [
      { name: 'thinking', type: 'string', description: '思考内容', required: true },
    ]);
    assert.equal(think.form.stealth, false);
    assert.equal(think.uuid, '2bd707ba-d098-4e6e-ad78-34e6d6477cbc');
  });

  await t.test('MessageInjections: 2 entries, both disabled and empty', () => {
    const items = Object.values(spreset.MessageInjections);
    assert.equal(items.length, 2);
    for (const item of items) {
      assert.equal(item.enabled, false);
      assert.equal(item.editorMode, 'form');
      assert.equal(item.reasoning, '');
      assert.equal(item.signature, '');
      assert.deepEqual(item.toolCalls, []);
      assert.deepEqual(item.toolResults, []);
    }
  });

  await t.test('OutputPreprocessing', () => {
    const p = spreset.OutputPreprocessing;
    assert.equal(p.enabled, true);
    assert.equal(p.consumeToolCalls, true);
    assert.equal(p.toolCallFormatter.enabled, true);
    assert.equal(typeof p.script, 'string');
    assert.match(p.script, /return \{\s*output: buffer/);
    assert.match(p.toolCallFormatter.script, /soliumbra_think/);
    assert.match(p.toolCallFormatter.script, /think_nya~>/);
  });

  await t.test('FixedPresetName / ForcedPostProcessing / FinalRequestProcessing', () => {
    assert.equal(spreset.FixedPresetName, '');
    assert.deepEqual(spreset.ForcedPostProcessing, { enabled: true, mode: '' });
    assert.equal(spreset.FinalRequestProcessing.enabled, true);
    assert.match(spreset.FinalRequestProcessing.script, /<run_tool_replacement>/);
    assert.match(spreset.FinalRequestProcessing.script, /name: 'context'/);
  });

  await t.test('ChatSquash configuration used by the integration path', () => {
    assert.equal(chatSquash.enabled, true);
    assert.equal(chatSquash.conditional_enabled, true);
    assert.equal(chatSquash.conditional_tag, '<|merge|>');
    assert.equal(chatSquash.separate_chat_history, false);
    assert.equal(chatSquash.parse_clewd, false);
    assert.equal(chatSquash.user_role_system, false);
    assert.equal(chatSquash.role, 'assistant');
    assert.equal(chatSquash.user_prefix, '|{{user}}|\n');
    assert.equal(chatSquash.char_prefix, '|游戏剧情|\n');
    assert.equal(chatSquash.prefix_system, '');
    assert.equal(chatSquash.suffix_system, '');
    assert.equal(chatSquash.enable_squashed_separator, true);
    assert.equal(chatSquash.squashed_separator_regex, false);
    assert.equal(chatSquash.squashed_separator_string, '<|sep|>');
    assert.equal(chatSquash.squashed_post_script_enable, true);
    assert.equal(chatSquash.re_split, false);
  });
});

test('ToolBindings.code executes and form parameters convert to JSON Schema', async (t) => {
  const expectedSchemas = {
    game_content: {
      type: 'object',
      properties: { content: { type: 'string' } },
      required: ['content'],
    },
    soliumbra_think: {
      type: 'object',
      properties: { thinking: { type: 'string', description: '思考内容' } },
      required: ['thinking'],
    },
  };

  for (const [id, binding] of Object.entries(spreset.ToolBindings)) {
    await t.test(binding.resolvedName, async () => {
      const { tool, actionResult, logs } = await executeToolBinding(binding);
      assert.equal(tool.name, binding.resolvedName);
      assert.equal(tool.description, binding.form.description);
      assert.equal(tool.stealth, false);
      assert.deepEqual(plain(tool.parameters), expectedSchemas[binding.resolvedName]);
      assert.equal(actionResult, '{"ok":true}');
      assert.deepEqual(logs, []);
      assert.deepEqual(formParametersToJsonSchema(binding), expectedSchemas[binding.resolvedName]);
      assert.ok(binding.code.length > 0);
      assert.ok(binding.form.actionBody.length > 0);
      assert.equal(binding.editorMode, 'form');
    });
  }

  await t.test('only enabled binding should become a dynamic tool', () => {
    const enabled = Object.values(spreset.ToolBindings)
      .filter((b) => b.enabled)
      .map((b) => b.form.name);
    assert.deepEqual(enabled, ['game_content']);
  });
});

test('OutputPreprocessing.toolCallFormatter.script', async (t) => {
  const script = spreset.OutputPreprocessing.toolCallFormatter.script;
  const both = [
    { name: 'game_content', arguments: { content: '你好' } },
    { name: 'soliumbra_think', arguments: { thinking: '想一下' } },
  ];

  await t.test('formatter handles both actual call shapes', async () => {
    const { result } = await callFunctionExpression(script, { calls: both }, { label: 'formatter' });
    assert.equal(result, '你好\n<think_nya~>\n想一下\n</think_nya~>');
  });

  await t.test('formatter handles enabled-only call', async () => {
    const { result } = await callFunctionExpression(script, {
      calls: [{ name: 'game_content', arguments: { content: '你好' } }],
    });
    assert.equal(result, '你好');
  });

  await t.test('formatter handles think-only legacy call', async () => {
    const { result } = await callFunctionExpression(script, {
      calls: [{ name: 'soliumbra_think', arguments: { thinking: '想一下' } }],
    });
    assert.equal(result, '<think_nya~>\n想一下\n</think_nya~>');
  });

  await t.test('formatter handles empty calls', async () => {
    const { result } = await callFunctionExpression(script, { calls: [] });
    assert.equal(result, '');
  });

  await t.test('arguments MUST already be parsed objects', async () => {
    const { result } = await callFunctionExpression(script, {
      calls: [{ name: 'game_content', arguments: '{"content":"你好"}' }],
    });
    assert.equal(result, '');
  });

  await t.test('multiple game_content calls are joined in call order', async () => {
    const { result } = await callFunctionExpression(script, {
      calls: [
        { name: 'game_content', arguments: { content: '第一段' } },
        { name: 'game_content', arguments: { content: '第二段' } },
      ],
    });
    assert.equal(result, '第一段\n第二段');
  });
});

test('OutputPreprocessing.script', async (t) => {
  const script = spreset.OutputPreprocessing.script;
  await t.test('passes buffer through and merges state', async () => {
    for (const final of [false, true]) {
      const { result, logs } = await callFunctionExpression(script, {
        buffer: `buffer-${final}`,
        final,
        state: { seed: 1 },
      });
      assert.deepEqual(plain(result), {
        output: `buffer-${final}`,
        hold: '',
        state: { seed: 1, accepted: true },
      });
      assert.deepEqual(logs, [['log', `buffer-${final}`]]);
    }
  });

  await t.test('the preset script requires a console shim', () => {
    const expression = script.replace(/;[\s;]*$/, '');
    const context = vm.createContext({ console: undefined });
    const fn = vm.runInContext(`(${expression})`, context);
    assert.throws(() => fn({ buffer: 'x', final: true, state: {} }));
  });
});

test('FinalRequestProcessing.script', async (t) => {
  const script = spreset.FinalRequestProcessing.script;
  const run = async (request) => {
    const { result } = await callFunctionExpression(script, { request }, { label: 'final-request' });
    return result;
  };
  const baseRequest = () => ({
    messages: [
      { role: 'system', content: 'system prefix\n<run_tool_replacement>\nsystem suffix' },
      { role: 'assistant', content: '角色资料' },
      { role: 'user', content: '开始' },
    ],
    tools: [
      {
        type: 'function',
        function: {
          name: 'game_content',
          description: 'x',
          parameters: { type: 'object', properties: {} },
        },
      },
    ],
  });

  await t.test('marker + exact 3 roles -> 2 messages and context tool (not only context)', async () => {
    const request = baseRequest();
    const result = await run(request);
    assert.equal(result, request, 'script mutates and returns the same request object');

    assert.equal(result.messages.length, 2);
    assert.deepEqual(plain(result.messages.map((m) => m.role)), ['system', 'user']);
    assert.equal(result.messages[0].content, 'system prefix\n\nsystem suffix');
    assert.ok(!result.messages[0].content.includes('<run_tool_replacement>'));
    assert.deepEqual(plain(result.messages[1]), { role: 'user', content: '开始' });

    assert.deepEqual(plain(result.tools.map((tool) => tool.function.name)), ['game_content', 'context']);
    const context = result.tools.at(-1);
    assert.deepEqual(plain(context), {
      type: 'function',
      function: {
        name: 'context',
        description: '角色资料',
        parameters: { type: 'object', properties: {} },
      },
    });
  });

  await t.test('existing context tool is filtered and replaced at the end', async () => {
    const request = baseRequest();
    request.tools.push({
      type: 'function',
      function: { name: 'context', description: 'old', parameters: { type: 'object', properties: {} } },
    });
    const result = await run(request);
    assert.deepEqual(plain(result.tools.map((tool) => tool.function.name)), ['game_content', 'context']);
    assert.equal(result.tools.at(-1).function.description, '角色资料');
  });

  await t.test('no marker -> request unchanged', async () => {
    const request = baseRequest();
    request.messages[0].content = 'no marker';
    const result = await run(request);
    assert.deepEqual(result, request);
  });

  await t.test('marker in user only -> request unchanged', async () => {
    const request = baseRequest();
    request.messages[0].content = 'no marker';
    request.messages[2].content = 'user <run_tool_replacement>';
    const result = await run(request);
    assert.deepEqual(result, request);
  });

  await t.test('marker + wrong message count -> throws', async () => {
    const request = baseRequest();
    request.messages.push({ role: 'assistant', content: 'extra' });
    await assert.rejects(() => run(request), /预期消息为三条/);
  });

  await t.test('marker + wrong roles -> throws', async () => {
    const request = baseRequest();
    request.messages[1].role = 'user';
    await assert.rejects(() => run(request), /预期消息为三条/);
  });

  await t.test('array content is detected, marker-stripped, and flattened for description', async () => {
    const request = {
      messages: [
        { role: 'system', content: [{ type: 'text', text: 'AAA<run_tool_replacement>' }, 'BBB'] },
        { role: 'assistant', content: [{ type: 'text', text: '角色' }, { type: 'text', text: '资料' }] },
        { role: 'user', content: [{ type: 'text', text: '开始' }] },
      ],
      tools: [],
    };
    const result = await run(request);
    assert.equal(result.messages.length, 2);
    assert.deepEqual(plain(result.messages[0].content), [{ type: 'text', text: 'AAA' }, 'BBB']);
    assert.equal(result.tools.length, 1);
    assert.equal(result.tools[0].function.name, 'context');
    assert.equal(result.tools[0].function.description, '角色\n资料');
  });

  await t.test('missing tools -> only context is appended', async () => {
    const request = {
      messages: [
        { role: 'system', content: '<run_tool_replacement>' },
        { role: 'assistant', content: '资料' },
        { role: 'user', content: '开始' },
      ],
    };
    const result = await run(request);
    assert.deepEqual(plain(result.tools.map((tool) => tool.function.name)), ['context']);
    assert.equal(result.messages[0].content, '');
  });
});

test('ChatSquash.squashed_post_script markers and output shape', async (t) => {
  const fn = evalFunctionExpression(chatSquash.squashed_post_script, { label: 'post-script' }).fn;

  await t.test('returns a string; empty and marker-free input unchanged', () => {
    assert.equal(fn(''), '');
    assert.equal(fn('纯文本，没有任何 marker'), '纯文本，没有任何 marker');
  });

  await t.test('|用户：name| selects role name; |小猫之神|/|用户| become JSON fragments; delete blocks removed', () => {
    const output = fn('|用户：爱丽丝|\n|小猫之神|猫A|用户|人B|delete|gone|/delete|尾巴');
    assert.equal(
      output,
      '\n"}, {"role": "soliumbra", "content": "\n猫A\n"}, {"role": "爱丽丝", "content": "\n人B尾巴',
    );
    assert.ok(!output.includes('|小猫之神|'));
    assert.ok(!output.includes('|用户|'));
    assert.ok(!output.includes('|delete|'));
    assert.ok(output.includes('"role": "soliumbra"'));
    assert.ok(output.includes('"role": "爱丽丝"'));
  });

  await t.test('without |用户：name| marker the default role is 用户', () => {
    const output = fn('|小猫之神|猫A|用户|人B');
    assert.equal(output, '\n"}, {"role": "soliumbra", "content": "\n猫A\n"}, {"role": "用户", "content": "\n人B');
  });

  await t.test('cat -> user transition does NOT emit <end> (prefix variable is unused)', () => {
    const output = fn('|小猫之神|猫A|用户|人B');
    assert.ok(!output.includes('<end>'));
  });

  await t.test('does not understand <|sep|>, <|merge|> or |游戏剧情|', () => {
    const input = 'A<|sep|>B<|merge|>C|游戏剧情|D';
    assert.equal(fn(input), input);
  });

  await t.test('processes only the first N-3 standalone <summary> tags', () => {
    const five = '<summary>S1</summary>\nX\n<summary>S2</summary>\nX\n<summary>S3</summary>\nX\n<summary>S4</summary>\nX\n<summary>S5</summary>';
    const out = fn(five);
    assert.equal(
      out,
      '<summary>\n---第1段剧情总结---S1</summary>\nX\n<summary>\n---第2段剧情总结---S2</summary>\nX\n<summary>S3</summary>\nX\n<summary>S4</summary>\nX\n<summary>S5</summary>',
    );
    assert.equal(fn('<summary>S1</summary>\nX\n<summary>S2</summary>\nX\n<summary>S3</summary>'), '<summary>S1</summary>\nX\n<summary>S2</summary>\nX\n<summary>S3</summary>');
  });

  await t.test('only <details><summary> is excluded, but a standalone summary immediately after </details> is wrongly excluded too', () => {
    assert.equal(
      fn('<details><summary>D</summary>x</details>\n<details><summary>D2</summary>y</details>'),
      '<details><summary>D</summary>x</details>\n<details><summary>D2</summary>y</details>',
    );
    const out = fn('<details><summary>D</summary>x</details>\n<summary>S1</summary>\n<summary>S2</summary>\n<summary>S3</summary>\n<summary>S4</summary>\n<summary>S5</summary>');
    assert.ok(!out.includes('---第1段剧情总结---S1'));
    assert.ok(out.includes('---第1段剧情总结---S2'));
  });
});

test('SPreset 1.0.0 squashPrompts role/prefix/suffix/separator semantics', async (t) => {
  const cfg = {
    role: 'assistant',
    user_role_system: false,
    enable_squashed_separator: true,
    squashed_separator_regex: false,
    squashed_separator_string: '<|sep|>',
    parse_clewd: false,
    user_prefix: '|{{user}}|\n',
    user_suffix: '[/U]',
    char_prefix: '|CHAR|\n',
    char_suffix: '[/A]',
    prefix_system: '[SYS]',
    suffix_system: '[/SYS]',
  };
  const prompts = [
    { role: 'system', content: 'SYS<|sep|>' },
    { role: 'user', content: 'U1' },
    { role: 'user', content: 'U2' },
    { role: 'assistant', content: 'A1' },
    { role: 'user', content: 'U3' },
  ];

  await t.test('role=assistant forces every merged chunk to assistant; separator-hit prompt stays untouched role', () => {
    const output = oldSquashPrompts(prompts, cfg, { userName: '小明', charName: '猫', postProcess: (s) => s });
    assert.deepEqual(output, [
      { role: 'system', content: 'SYS' },
      {
        role: 'assistant',
        content: '|小明|\nU1\nU2[/U]|CHAR|\nA1[/A]|小明|\nU3[/U]',
      },
    ]);
  });

  await t.test('role=follow takes the first prompt role for a chunk (and separator prompt role after a flush)', () => {
    const output = oldSquashPrompts(prompts, { ...cfg, role: 'follow' }, {
      userName: '小明',
      charName: '猫',
      postProcess: (s) => s,
    });
    assert.deepEqual(output, [
      { role: 'system', content: 'SYS' },
      { role: 'system', content: '|小明|\nU1\nU2[/U]|CHAR|\nA1[/A]|小明|\nU3[/U]' },
    ]);
  });

  await t.test('disabled separator means no flush; whole input is one assistant message', () => {
    const output = oldSquashPrompts(prompts, { ...cfg, enable_squashed_separator: false }, {
      userName: '小明',
      charName: '猫',
      postProcess: (s) => s,
    });
    assert.deepEqual(output, [
      {
        role: 'assistant',
        content: '[SYS]SYS<|sep|>[/SYS]|小明|\nU1\nU2[/U]|CHAR|\nA1[/A]|小明|\nU3[/U]',
      },
    ]);
  });

  await t.test('user_role_system converts system prompts to user role before prefixing', () => {
    const output = oldSquashPrompts(
      [{ role: 'system', content: 'S' }, { role: 'user', content: 'U' }],
      { ...cfg, user_role_system: true, enable_squashed_separator: false },
      { userName: '小明', charName: '猫', postProcess: (s) => s },
    );
    assert.deepEqual(output, [
      { role: 'assistant', content: '|小明|\nS\nU[/U]' },
    ]);
  });
});

test('app-shaped prompt assembly and two ChatSquash strategies', async (t) => {
  const userName = '小明';
  const charName = '小猫之神';
  const built = buildAppLikeMessages(preset, { userName, charName });
  const postLogs = [];
  const { postProcess, compileError } = createPostProcessor(chatSquash, {
    userName,
    charName,
    logs: postLogs,
  });
  assert.equal(compileError, null);

  await t.test('buildAppLikeMessages produces first system with marker and after-dialog system before last user', () => {
    const first = built.messages[0];
    assert.equal(first.role, 'system');
    assert.ok(String(first.content).includes('<run_tool_replacement>'));
    assert.ok(String(first.content).includes('<|merge|>'));
    assert.ok(String(first.content).includes('<|sep|>'));
    assert.deepEqual(
      built.messages.map((m) => m.role),
      ['system', 'user', 'assistant', 'user', 'assistant', 'system', 'user'],
    );
    assert.equal(built.meta.beforeSystemPromptCount, 8);
    assert.equal(built.meta.afterDialogPromptCount, 20);
  });

  await t.test('old SPreset 1.0 squashPrompts yields 2 messages; FinalRequestProcessing throws', async () => {
    const squashed = oldSquashPrompts(built.messages, chatSquash, { userName, charName, postProcess });
    assert.deepEqual(squashed.map((m) => m.role), ['system', 'assistant']);
    assert.ok(String(squashed[0].content).includes('<|merge|>'));
    assert.ok(String(squashed[1].content).startsWith('|小明|\n'));

    const harness = createSpresetHarness(preset);
    await assert.rejects(
      () => harness.processFinalRequest({ messages: squashed, tools: [] }),
      /预期消息为三条/,
    );
  });

  await t.test('proposed conditional block merge yields system/assistant/user and post-script output is JSON-fragment transcript', () => {
    const proposed = proposedConditionalBlockMerge(built.messages, chatSquash, {
      userName,
      charName,
      postProcess,
    });
    assert.equal(proposed.changed, true);
    assert.equal(proposed.reason, 'ok');
    assert.deepEqual(proposed.blockRoles, ['user', 'assistant', 'user', 'assistant', 'system']);
    assert.deepEqual(proposed.messages.map((m) => m.role), ['system', 'assistant', 'user']);

    const [system, assistant, user] = proposed.messages;
    assert.ok(!String(system.content).includes('<|sep|>'));
    assert.ok(!String(system.content).includes('<|merge|>'));
    assert.ok(String(system.content).includes('<run_tool_replacement>'));

    assert.ok(assistant.content.length > 3000);
    assert.ok(assistant.content.startsWith('|小明|\n"}, {"role": "用户", "content": "\n'));
    assert.ok(assistant.content.includes('"role": "soliumbra"'));
    assert.ok(assistant.content.endsWith('"role": "soliumbra", "content": "好喵！现在要继续游戏~ 调用工具喵~'));
    assert.ok(!assistant.content.includes('|小猫之神|'));
    assert.ok(!assistant.content.includes('|用户|'));
    assert.ok(!assistant.content.includes('<|sep|>'));
    assert.ok(!assistant.content.includes('<|merge|>'));

    // Prefix/suffix fields are applied before the post script; the post script does not understand them.
    assert.ok(assistant.content.includes('|小明|'));
    assert.ok(assistant.content.includes('|游戏剧情|'));
    // No |用户：小明| marker remains in the merged block, so post script falls back to role "用户".
    assert.ok(assistant.content.includes('"role": "用户"'));
    assert.ok(!assistant.content.includes('"role": "小明"'));
    assert.equal(user.role, 'user');
  });

  await t.test('proposed merge + FinalRequestProcessing succeeds and context description is the full transcript', async () => {
    const proposed = proposedConditionalBlockMerge(built.messages, chatSquash, {
      userName,
      charName,
      postProcess,
    });
    const request = {
      messages: proposed.messages,
      tools: [
        {
          type: 'function',
          function: {
            name: 'game_content',
            description: 'x',
            parameters: { type: 'object', properties: {} },
          },
        },
      ],
    };
    const harness = createSpresetHarness(preset);
    const result = await harness.processFinalRequest(request);
    assert.deepEqual(plain(result.messages.map((m) => m.role)), ['system', 'user']);
    assert.equal(result.messages[1].content, '|用户|好耶，那开始游戏吧！');
    assert.ok(!String(result.messages[0].content).includes('<run_tool_replacement>'));
    assert.deepEqual(plain(result.tools.map((tool) => tool.function.name)), ['game_content', 'context']);

    const context = result.tools.at(-1).function;
    assert.deepEqual(plain(context.parameters), { type: 'object', properties: {} });
    assert.equal(context.description, proposed.messages[1].content);
    assert.ok(context.description.startsWith('|小明|\n"}, {"role": "用户", "content": "\n'));
  });

  await t.test('first turn: AFTER_DIALOG lands after the last user; robust merge must move trailing systems into block', async () => {
    const firstTurn = buildAppLikeMessages(preset, {
      userName,
      charName,
      history: [{ role: 'user', content: '|用户|你好呀' }],
    });
    assert.deepEqual(firstTurn.messages.map((m) => m.role), ['system', 'user', 'system']);

    const proposed = proposedConditionalBlockMerge(firstTurn.messages, chatSquash, {
      userName,
      charName,
      postProcess,
    });
    assert.equal(proposed.changed, true);
    assert.deepEqual(proposed.blockRoles, ['system']);
    assert.deepEqual(proposed.messages.map((m) => m.role), ['system', 'assistant', 'user']);
    assert.ok(proposed.messages[1].content.startsWith('\n"}, {"role": "soliumbra", "content": "\n'));

    const harness = createSpresetHarness(preset);
    const result = await harness.processFinalRequest({ messages: proposed.messages, tools: [] });
    assert.deepEqual(plain(result.messages.map((m) => m.role)), ['system', 'user']);
    assert.equal(result.tools.length, 1);
    assert.equal(result.tools[0].function.name, 'context');
  });

  await t.test('conditional tag is required when conditional_enabled=true', () => {
    const messages = built.messages.map((m) => ({
      ...m,
      content: String(m.content).split('<|merge|>').join(''),
    }));
    const proposed = proposedConditionalBlockMerge(messages, chatSquash, {
      userName,
      charName,
      postProcess,
    });
    assert.equal(proposed.changed, false);
    assert.equal(proposed.reason, 'conditional-tag-missing');
  });
});
