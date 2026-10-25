import fs from 'node:fs';
import assert from 'node:assert/strict';

const expectedMinimums = [
  ['app/src/main/assets/st-runtime/st-compat/script.js', 249],
  ['app/src/main/assets/st-runtime/st-compat/lib.js', 33],
  ['app/src/main/assets/st-runtime/st-compat/world-info.js', 68],
  ['app/src/main/assets/st-runtime/st-compat/variables.js', 22],
  ['app/src/main/assets/st-runtime/st-compat/slash-commands.js', 31],
  ['app/src/main/assets/st-runtime/st-compat/extensions.js', 29],
  ['app/src/main/assets/st-runtime/st-compat/openai.js', 45],
  ['app/src/main/assets/st-runtime/st-compat/macros.js', 4],
  ['app/src/main/assets/st-runtime/st-compat/macros/macro-system.js', 11],
  ['app/src/main/assets/st-runtime/st-compat/utils.js', 134],
  ['app/src/main/assets/st-runtime/st-compat/power-user.js', 32],
  ['app/src/main/assets/st-runtime/st-compat/personas.js', 26],
  ['app/src/main/assets/st-runtime/st-compat/group-chats.js', 41],
  ['app/src/main/assets/st-runtime/st-compat/tags.js', 33],
  ['app/src/main/assets/st-runtime/st-compat/tokenizers.js', 21],
];

function extractExports(src) {
  const out = new Set();
  for (const m of src.matchAll(/export\s+(?:async\s+)?(?:function|const|let|var|class)\s+([A-Za-z0-9_$]+)/g)) {
    out.add(m[1]);
  }
  for (const m of src.matchAll(/export\s*\{([^}]+)\}/g)) {
    for (const part of m[1].split(',')) {
      const s = part.trim();
      if (!s) continue;
      const asMatch = s.match(/^([A-Za-z0-9_$]+)\s+as\s+([A-Za-z0-9_$]+)$/);
      out.add(asMatch ? asMatch[2] : s);
    }
  }
  if (/export\s+default\s+/.test(src)) out.add('default');
  return [...out];
}

const summary = expectedMinimums.map(([file, minCount]) => {
  const src = fs.readFileSync(file, 'utf8');
  const named = extractExports(src);
  assert.ok(named.length >= minCount, `${file} exports ${named.length} < expected ${minCount}`);
  return { module: file.split('/').pop(), localCount: named.length, expectedMin: minCount, missing: [], fatalMissing: [] };
});
console.log(JSON.stringify(summary, null, 2));
