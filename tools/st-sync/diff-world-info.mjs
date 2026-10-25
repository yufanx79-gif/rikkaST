import fs from 'node:fs';
import assert from 'node:assert/strict';

const wi = fs.readFileSync('app/src/main/assets/st-runtime/st-compat/world-info.js', 'utf8');
const required42Fields = [
  'key', 'keysecondary', 'comment', 'content', 'constant', 'vectorized',
  'selective', 'selectiveLogic', 'addMemo', 'order', 'position', 'disable',
  'ignoreBudget', 'excludeRecursion', 'preventRecursion',
  'matchPersonaDescription', 'matchCharacterDescription', 'matchCharacterPersonality',
  'matchCharacterDepthPrompt', 'matchScenario', 'matchCreatorNotes',
  'delayUntilRecursion', 'probability', 'useProbability', 'depth', 'outletName',
  'group', 'groupOverride', 'groupWeight', 'scanDepth', 'caseSensitive',
  'matchWholeWords', 'useGroupScoring', 'automationId', 'role',
  'sticky', 'cooldown', 'delay',
  'characterFilterNames', 'characterFilterTags', 'characterFilterExclude', 'triggers',
];
assert.equal(required42Fields.length, 42);
for (const field of required42Fields) {
  assert.ok(wi.includes(field), `world-info.js missing field: ${field}`);
}
console.log('[diff-world-info] OK:', { verifiedFields: required42Fields.length });
