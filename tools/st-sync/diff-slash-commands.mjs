import fs from 'node:fs';
import assert from 'node:assert/strict';

const kt = fs.readFileSync('app/src/main/java/me/rerere/rikkahub/data/st/script/StSlashExecutor.kt', 'utf8');
assert.ok(kt.includes('UNSUPPORTED_MEDIA_COMMANDS'), 'Missing UNSUPPORTED_MEDIA_COMMANDS allowlist');
for (const cmd of ['speak', 'imagine', 'imagine-comfy-workflow', 'imagine-source', 'imagine-style']) {
  assert.ok(kt.includes(`"${cmd}"`), `Missing explicit unsupported command: ${cmd}`);
}
console.log('[diff-slash-commands] OK');
