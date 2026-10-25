import fs from 'node:fs';
import assert from 'node:assert/strict';

const rikka = JSON.parse(fs.readFileSync('upstream-lock/rikkahub.json', 'utf8'));
const st = JSON.parse(fs.readFileSync('upstream-lock/sillytavern.json', 'utf8'));
const runtimeUpstream = JSON.parse(fs.readFileSync('app/src/main/assets/st-runtime/UPSTREAM.json', 'utf8'));

assert.equal(rikka.baselineCommit, '447bb7e89710d31f1204d7a2973baa19fdbd5b28');
assert.equal(rikka.targetTag, '3.1.0');
assert.equal(rikka.targetCommit, 'eeef60b22ba24383daf51f3c6c2e5b75bbab6bba');
assert.equal(st.version, '1.19.0');
assert.equal(st.sha256, '19F34009D55D4E2DD394CE9D6DD6E5D1B0E5B9E5D57DF8B5AA325A44C18969BE');
assert.equal(runtimeUpstream.version, st.version);
assert.equal(runtimeUpstream.sha256, st.sha256);
assert.ok(Array.isArray(st.modules) && st.modules.length >= 10);
console.log('[verify-lock] OK:', { rikkahub: rikka.targetTag, sillytavern: st.version, publicModules: runtimeUpstream.publicModuleCount });
