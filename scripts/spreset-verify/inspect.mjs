import {
  loadPreset,
  executeToolBinding,
  formParametersToJsonSchema,
  createSpresetHarness,
  evalFunctionExpression,
} from './lib/spreset.mjs';

const preset = loadPreset();
const { spreset } = preset;
const harness = createSpresetHarness(preset);

const line = (title) => console.log(`\n===== ${title} =====`);
const json = (value) => console.log(JSON.stringify(value, null, 2));

line('预设来源');
console.log(preset.presetPath);

line('SPreset 顶层键');
for (const [key, value] of Object.entries(spreset)) {
  const type = Array.isArray(value) ? 'array' : value === null ? 'null' : typeof value;
  const detail = typeof value === 'string'
    ? `length=${value.length}`
    : value && typeof value === 'object'
      ? `keys=${Object.keys(value).length}`
      : String(value);
  console.log(`${key}: ${type} ${detail}`);
}

line('ToolBindings');
for (const [id, binding] of Object.entries(spreset.ToolBindings)) {
  const { tool, actionResult } = await executeToolBinding(binding);
  json({
    id,
    enabled: binding.enabled,
    valid: binding.valid,
    uuid: binding.uuid,
    resolvedName: binding.resolvedName,
    editorMode: binding.editorMode,
    stealth: binding.form?.stealth,
    form: {
      name: binding.form?.name,
      displayName: binding.form?.displayName,
      description: binding.form?.description,
      parameters: binding.form?.parameters,
      actionBody: binding.form?.actionBody,
    },
    codeLength: binding.code?.length,
    executedTool: {
      name: tool.name,
      description: tool.description,
      parameters: tool.parameters,
      stealth: tool.stealth,
      actionResult,
    },
    jsonSchemaFromForm: formParametersToJsonSchema(binding),
  });
}

line('MessageInjections');
json(spreset.MessageInjections);

line('OutputPreprocessing');
json({
  enabled: spreset.OutputPreprocessing.enabled,
  consumeToolCalls: spreset.OutputPreprocessing.consumeToolCalls,
  toolCallFormatter: {
    enabled: spreset.OutputPreprocessing.toolCallFormatter.enabled,
    script: spreset.OutputPreprocessing.toolCallFormatter.script,
  },
  script: spreset.OutputPreprocessing.script,
});

line('FixedPresetName / ForcedPostProcessing / FinalRequestProcessing');
json({
  FixedPresetName: spreset.FixedPresetName,
  ForcedPostProcessing: spreset.ForcedPostProcessing,
  FinalRequestProcessing: {
    enabled: spreset.FinalRequestProcessing.enabled,
    script: spreset.FinalRequestProcessing.script,
  },
});

line('toolCallFormatter 实际输出');
const calls = [
  { name: 'game_content', arguments: { content: '你好' } },
  { name: 'soliumbra_think', arguments: { thinking: '想一下' } },
];
console.log('calls both:', JSON.stringify(calls));
console.log('formatted:', JSON.stringify(await harness.formatToolCalls(calls)));
console.log('calls game only:', JSON.stringify(await harness.formatToolCalls([calls[0]])));
console.log('calls think only:', JSON.stringify(await harness.formatToolCalls([calls[1]])));
console.log('calls with arguments as JSON string:', JSON.stringify(await harness.formatToolCalls([
  { name: 'game_content', arguments: '{"content":"你好"}' },
])));

line('outputPreprocessing 实际输出');
json(await harness.processOutput({
  buffer: '你好\n<think_nya~>\n想一下\n</think_nya~>',
  final: true,
  state: { seed: 1 },
}));

line('FinalRequestProcessing 前');
const request = {
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
};
const before = structuredClone(request);
json(before);

line('FinalRequestProcessing 后');
const after = await harness.processFinalRequest(request);
json(after);
console.log('messages:', after.messages.length, 'roles:', after.messages.map((m) => m.role).join(','));
console.log('tools:', after.tools.map((tool) => tool.function?.name).join(','));
console.log('context.description:', JSON.stringify(after.tools.at(-1)?.function?.description));

line('ChatSquash.squashed_post_script 实际输出');
const postFn = evalFunctionExpression(spreset.ChatSquash.squashed_post_script, {
  label: 'post-script',
}).fn;
const postSamples = {
  basic: '|用户：小明|\n|小猫之神|猫说\n|用户|玩家说\n|delete|旧内容|/delete|\n尾巴',
  separatorMarkers: 'A<|sep|>B<|merge|>C|游戏剧情|D',
  summaries: '<summary>S1</summary>\nX\n<summary>S2</summary>\nX\n<summary>S3</summary>\nX\n<summary>S4</summary>\nX\n<summary>S5</summary>',
};
for (const [name, content] of Object.entries(postSamples)) {
  console.log(`\n-- ${name} --`);
  console.log('in :', JSON.stringify(content));
  console.log('out:', JSON.stringify(postFn(content)));
}
