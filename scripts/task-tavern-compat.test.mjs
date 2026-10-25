import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const DEFAULT_PRESET = 'E:/Localsend download/【小猫之神·喵赛亚】1.5.json';
const PRESET_PATH = process.env.CAT_GOD_PRESET || DEFAULT_PRESET;

function readJsonIfPresent(file) {
  if (!fs.existsSync(file)) return null;
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

/**
 * 与 `ModeInjectionSerializer.tryImportSillyTavernPreset` 的关键投影一致：
 * 优先 character_id=100001 的 prompt_order；marker/空内容跳过；去重后按顺序生成注入。
 */
function projectPresetInjections(root) {
  const prompts = Array.isArray(root.prompts) ? root.prompts : [];
  const map = new Map();
  for (let i = 0; i < prompts.length; i++) {
    const p = prompts[i] || {};
    const key = typeof p.identifier === 'string' && p.identifier ? p.identifier : `#${i}`;
    if (!map.has(key)) map.set(key, p);
  }
  const groups = Array.isArray(root.prompt_order) ? root.prompt_order : [];
  const orderGroup = groups.find((g) => g && g.character_id === 100001)
    || groups.find((g) => g && Array.isArray(g.order) && g.order.length > 0);
  const order = orderGroup && Array.isArray(orderGroup.order) ? orderGroup.order : [];
  const injections = [];
  const seen = new Set();
  for (const slot of order) {
    const key = slot && slot.identifier;
    if (!key || !map.has(key) || seen.has(key)) continue;
    seen.add(key);
    const prompt = map.get(key);
    if (prompt.marker) continue;
    const content = typeof prompt.content === 'string' ? prompt.content.trim() : '';
    if (!content) continue;
    injections.push({
      key,
      name: prompt.name,
      enabled: typeof slot.enabled === 'boolean' ? slot.enabled
        : (typeof prompt.enabled === 'boolean' ? prompt.enabled : true),
      position: prompt.injection_position,
    });
  }
  return injections;
}

function inspectPresetScripts(root) {
  const ext = root.extensions;
  if (!ext || typeof ext !== 'object' || Array.isArray(ext)) return { state: 'CORRUPTED', scripts: [] };
  const helper = ext.tavern_helper;
  if (helper === undefined || helper === null) return { state: 'ABSENT', scripts: [] };
  if (typeof helper !== 'object' || Array.isArray(helper)) return { state: 'CORRUPTED', scripts: [] };
  const scripts = helper.scripts;
  if (scripts === undefined || scripts === null) return { state: 'ABSENT', scripts: [] };
  if (!Array.isArray(scripts) || scripts.some((s) => typeof s !== 'object' || s === null || Array.isArray(s))) {
    return { state: 'CORRUPTED', scripts: [] };
  }
  return { state: 'PRESENT_VALID', scripts };
}

function inspectPresetRegexes(root) {
  const ext = root.extensions;
  const raw = Array.isArray(ext?.regex_scripts) ? ext.regex_scripts : null;
  const spreset = ext?.SPreset?.RegexBinding;
  const sp = Array.isArray(spreset?.regexes) ? spreset.regexes
    : (Array.isArray(spreset?.scripts) ? spreset.scripts : null);
  const combined = [...(raw || []), ...(sp || [])];
  const valid = combined.every((r) => r && typeof r === 'object' && typeof r.findRegex === 'string' && r.findRegex.trim());
  return { rawCount: raw?.length ?? 0, spresetCount: sp?.length ?? 0, combinedCount: combined.length, valid };
}

test('小猫之神 preset projects deterministically through preset import rules', { skip: !fs.existsSync(PRESET_PATH) && `fixture missing: ${PRESET_PATH}` }, () => {
  const root = readJsonIfPresent(PRESET_PATH);
  assert.ok(root);
  assert.equal(root.spec, undefined, '这是聊天补全预设，不是角色卡：角色卡导入会报缺少 spec，应走预设导入');
  assert.ok(Array.isArray(root.prompts));

  const injections = projectPresetInjections(root);
  assert.equal(injections.length, 45);
  assert.equal(injections.filter((i) => i.enabled).length, 28);
  assert.equal(injections.filter((i) => i.position === 0).length, 45);

  const scripts = inspectPresetScripts(root);
  assert.equal(scripts.state, 'PRESENT_VALID');
  assert.equal(scripts.scripts.length, 1);
  assert.equal(scripts.scripts[0].name, 'SPreset');

  const regex = inspectPresetRegexes(root);
  assert.equal(regex.rawCount, 14);
  assert.equal(regex.spresetCount, 14);
  assert.equal(regex.combinedCount, 28);
  assert.equal(regex.valid, true);

  const spresetKeys = Object.keys(root.extensions?.SPreset || {});
  const unsupported = spresetKeys
    .filter((k) => !['ChatSquash', 'RegexBinding', 'MacroNest', 'version', 'enabled'].includes(k))
    .sort();
  assert.deepEqual(unsupported, [
    'FinalRequestProcessing',
    'FixedPresetName',
    'ForcedPostProcessing',
    'MessageInjections',
    'OutputPreprocessing',
    'ToolBindings',
  ]);

});

test('card-script compatibility layer covers legacy JSR storage and UI setter path', () => {
  const compat = fs.readFileSync(path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernScriptCompat.kt'), 'utf8');
  const scripts = fs.readFileSync(path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernScripts.kt'), 'utf8');
  const ui = fs.readFileSync(path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/ai/StTavernSections.kt'), 'utf8');

  assert.match(compat, /TavernHelper_scripts/);
  assert.match(compat, /TavernHelper_characterScriptVariables/);
  assert.match(compat, /fun cardExtensions\(/);
  assert.match(compat, /obj\["value"\] as\? JsonObject/);
  assert.match(compat, /put\("type", JsonPrimitive\("script"\)\)/);
  assert.match(scripts, /fun setCardScriptEnabled\(assistant: Assistant\?/);
  assert.match(scripts, /TavernScriptCompat\.cardExtensions\(assistant\)/);
  assert.match(ui, /setCardScriptEnabled\(assistant, brief\.id, checked\)/);
});
