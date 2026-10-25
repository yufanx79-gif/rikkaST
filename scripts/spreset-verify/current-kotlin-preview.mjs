import { loadPreset, buildAppLikeMessages, createPostProcessor } from './lib/spreset.mjs';

const preset = loadPreset();
const cfg = preset.spreset.ChatSquash;
const { postProcess } = createPostProcessor(cfg, { userName: '小明', charName: '小猫之神', logs: [] });
const text = (m) => typeof m.content === 'string' ? m.content : (m.content ?? []).map((p) => typeof p === 'string' ? p : p.text ?? '').join('\n');
const prefix = (role) => role === 'system' ? cfg.prefix_system : role === 'user' ? cfg.user_prefix : cfg.char_prefix;
const suffix = (role) => role === 'system' ? cfg.suffix_system : role === 'user' ? cfg.user_suffix : cfg.char_suffix;
const sub = (s) => s.replaceAll('{{user}}', '小明').replaceAll('{{trim}}', '');

function kotlinConditional(messages) {
  const markerIndex = messages.findIndex((m) => text(m).includes(cfg.conditional_tag));
  if (markerIndex < 0) return { changed: false, reason: 'no marker' };
  const marker = messages[markerIndex];
  if (marker.role !== 'system') return { changed: false, reason: 'marker not system' };
  const lastUserIndex = messages.map((m, i) => ({ m, i })).filter(({ m }) => m.role === 'user' && text(m).trim()).at(-1)?.i ?? -1;
  if (lastUserIndex <= markerIndex) return { changed: false, reason: 'no last user' };
  const folded = [...messages.slice(0, markerIndex), ...messages.slice(markerIndex + 1, lastUserIndex)];
  const foldedText = folded.map((m) => sub(prefix(m.role)) + text(m) + sub(suffix(m.role))).join('\n');
  const postProcessed = postProcess(foldedText);
  let systemText = text(marker);
  systemText = systemText.replaceAll(cfg.conditional_tag, '').replaceAll(cfg.squashed_separator_string, '').trim();
  const output = [
    { ...marker, content: systemText },
    { role: 'assistant', content: postProcessed },
    messages[lastUserIndex],
  ];
  return { changed: true, foldedCount: folded.length, foldedTextLength: foldedText.length, output };
}

for (const [name, history] of [
  ['default history', undefined],
  ['first turn', [{ role: 'user', content: '|用户|你好呀' }]],
]) {
  const { messages } = buildAppLikeMessages(preset, { userName: '小明', charName: '小猫之神', history });
  const result = kotlinConditional(messages);
  console.log(`\n=== ${name} ===`);
  console.log('input roles:', messages.map((m) => m.role).join(','));
  console.log('changed:', result.changed, result.reason ?? '');
  console.log('foldedCount:', result.foldedCount, 'foldedTextLength:', result.foldedTextLength);
  if (result.output) {
    console.log('output roles:', result.output.map((m) => m.role).join(','));
    console.log('assistant length:', result.output[1].content.length);
    console.log('assistant start:', JSON.stringify(result.output[1].content.slice(0, 100)));
    console.log('assistant end:', JSON.stringify(result.output[1].content.slice(-100)));
  }
}
