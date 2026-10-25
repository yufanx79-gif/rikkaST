import fs from 'node:fs';
import assert from 'node:assert/strict';

const lock = JSON.parse(fs.readFileSync('upstream-lock/sillytavern.json', 'utf8'));
const runtimeUpstream = JSON.parse(fs.readFileSync('app/src/main/assets/st-runtime/UPSTREAM.json', 'utf8'));
assert.equal(lock.version, runtimeUpstream.version);
assert.equal(lock.sha256, runtimeUpstream.sha256);
console.log('[sync-st] Verified lock & runtime UPSTREAM.json parity:', lock.version);
