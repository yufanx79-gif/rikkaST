import fs from 'node:fs';
import assert from 'node:assert/strict';

const appDb = fs.readFileSync('app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt', 'utf8');
const factory = fs.readFileSync('app/src/main/java/me/rerere/rikkahub/data/db/AppDatabaseFactory.kt', 'utf8');
const convEntity = fs.readFileSync('app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt', 'utf8');
const schema32 = JSON.parse(fs.readFileSync('app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json', 'utf8'));

assert.ok(appDb.includes('version = 32'), 'AppDatabase version must be 32');
assert.ok(!factory.includes('fallbackToDestructiveMigration(true)'), 'Destructive migration is forbidden');
assert.ok(factory.includes('Migration_29_32'), 'AppDatabaseFactory must register Migration_29_32');
assert.ok(convEntity.includes('st_parent_conversation_id') && convEntity.includes('config'), 'ConversationEntity must retain both st_parent_conversation_id and config');

const tables = new Set(schema32.database.entities.map((e) => e.tableName));
for (const required of ['ConversationEntity', 'MemoryEntity', 'memory_file', 'memory_consolidation', 'knowledge_sources', 'media_creation_session', 'translation_history', 'usage_record']) {
  assert.ok(tables.has(required), `Schema 32 missing table: ${required}`);
}
console.log('[check-db-schema] OK: version=32, tables=', tables.size);
