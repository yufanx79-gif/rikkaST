import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test('Room SQLite migration verification: 28->29->32, 29->32, 30->32, and 31->32 match Schema 32.json and preserve/backfill all rows without destructive fallback', () => {
  const pyFile = path.join(__dirname, 'verify_room_migration_sqlite.py');
  const out = execFileSync('python3', [pyFile], { encoding: 'utf8' });
  assert.ok(out.includes('SQLITE_MIGRATION_VERIFIED_OK'));
});
