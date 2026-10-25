import fs from 'node:fs';
const lock = JSON.parse(fs.readFileSync('upstream-lock/rikkahub.json', 'utf8'));
console.log('[check-tags] Pinned upstream RikkaHub tag:', lock.targetTag, lock.targetCommit);
