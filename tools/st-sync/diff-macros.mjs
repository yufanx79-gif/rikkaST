import fs from 'node:fs';
import assert from 'node:assert/strict';

const kt = fs.readFileSync('app/src/main/java/me/rerere/rikkahub/data/st/macro/MacroDefinitions.kt', 'utf8');
const js = fs.readFileSync('app/src/main/assets/st-runtime/st-compat/macros/engine/MacroRegistry.js', 'utf8');

const ktPrimary = [...kt.matchAll(/reg\(\s*"([^"]+)"/g)].map((m) => m[1]);
const jsPrimary = [...js.matchAll(/\{\s*name:\s*'([^']+)'/g)].map((m) => m[1]);

assert.ok(ktPrimary.length >= 75, `Kotlin primary macros < 75: ${ktPrimary.length}`);
assert.ok(jsPrimary.length >= 75, `JS MacroRegistry primary macros < 75: ${jsPrimary.length}`);
console.log('[diff-macros] OK:', { ktPrimaryCount: ktPrimary.length, jsPrimaryCount: jsPrimary.length });
