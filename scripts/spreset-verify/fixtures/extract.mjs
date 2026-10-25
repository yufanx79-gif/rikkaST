import fs from 'node:fs';

const input = process.env.SPRESET_JSON ?? 'E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json';
const output = new URL('./spreset-cat-god.json', import.meta.url);
const root = JSON.parse(fs.readFileSync(input, 'utf8'));
const sp = root.extensions.SPreset;

const SIX = [
  'ToolBindings',
  'MessageInjections',
  'OutputPreprocessing',
  'FixedPresetName',
  'ForcedPostProcessing',
  'FinalRequestProcessing',
];
const spreset = {};
for (const key of SIX) spreset[key] = sp[key];
spreset.ChatSquash = sp.ChatSquash;

const fixture = {
  _note: 'Extracted from 【小猫之神·喵赛亚】1.5.json. Contains only data needed by spreset-verify tests.',
  temperature: root.temperature,
  new_chat_prompt: root.new_chat_prompt,
  prompts: (root.prompts ?? []).filter((p) => p.identifier !== 'SPresetSettings'),
  prompt_order: root.prompt_order,
  extensions: { SPreset: spreset },
};

fs.writeFileSync(output, JSON.stringify(fixture, null, 2) + '\n', 'utf8');
const size = fs.statSync(output).size;
console.log('wrote', output.pathname, 'bytes=', size);
console.log('prompts=', fixture.prompts.length, 'spreset keys=', Object.keys(spreset));
for (const key of SIX) console.log(key, 'present=', key in spreset);
console.log('ChatSquash keys=', Object.keys(spreset.ChatSquash).length);
