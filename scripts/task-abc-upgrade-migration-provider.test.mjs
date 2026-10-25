import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const REPO_ROOT = process.cwd();

function readRepoFile(relPath) {
  return fs.readFileSync(path.join(REPO_ROOT, relPath), 'utf8');
}

test('Task A/B: Koin DI & Kotlin compile contracts have no broken constructor/import/method mismatches', () => {
  const appModule = readRepoFile('app/src/main/java/me/rerere/rikkahub/di/AppModule.kt');
  assert.ok(
    appModule.includes('EmojiUtils.loadEmoji(get())'),
    'AppModule must register EmojiData via EmojiUtils.loadEmoji(get())'
  );
  assert.ok(
    appModule.includes('single { TTSManager(get()) }'),
    'AppModule must construct TTSManager(get()) with 1 Context argument'
  );
  assert.ok(
    !appModule.includes('import me.rerere.speech.SpeechService'),
    'AppModule must not import non-existent me.rerere.speech.SpeechService'
  );
  assert.ok(
    appModule.includes('single(createdAtStart = true) { ChatNotificationManager(get(), get(), get(), get()) }'),
    'AppModule must register ChatNotificationManager(get(), get(), get(), get()) with createdAtStart = true'
  );
  assert.ok(
    appModule.includes('WorkspaceTerminalSessionManager(get(), get())'),
    'AppModule must construct WorkspaceTerminalSessionManager(get(), get())'
  );
  assert.ok(
    appModule.includes('mcpManager = get()') && !appModule.includes('settingsStore = get(),\n        )\n    }\n    single {\n        ChatRequestFactory'),
    'AppModule ChatToolFactory must pass mcpManager = get() matching constructor'
  );

  const assistantDetailVm = readRepoFile('app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt');
  assert.ok(
    assistantDetailVm.includes('memoryConsolidationScheduler: MemoryConsolidationScheduler') &&
      assistantDetailVm.includes('val memoryId: String') &&
      assistantDetailVm.includes('fun organizeMemory()'),
    'AssistantDetailVM must include MemoryConsolidationScheduler, memoryId, and organizeMemory()'
  );

  const settingVm = readRepoFile('app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingVM.kt');
  assert.ok(
    !settingVm.includes('}\n        .stateIn('),
    'SettingVM must not have a dangling .stateIn() call after updateSettings'
  );

  const compressPrompt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt');
  assert.ok(
    compressPrompt.includes('DEFAULT_COMPRESS_PROMPT'),
    'CompressPrompt.kt must define DEFAULT_COMPRESS_PROMPT'
  );

  const settingsKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt');
  const fastModelMatches = settingsKt.match(/val fastModelReasoningLevel:/g) || [];
  assert.equal(fastModelMatches.length, 1, 'Settings.kt must declare fastModelReasoningLevel exactly once');
  assert.ok(
    !settingsKt.includes('import me.rerere.rikkahub.data.model.RegexScript') &&
      !settingsKt.includes('import me.rerere.rikkahub.data.model.ExpressionClassifier'),
    'Settings.kt must import RegexScript and ExpressionClassifier from their actual packages'
  );

  const settingsFieldsKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsFields.kt');
  assert.ok(
    !settingsFieldsKt.includes('ENABLE_AUTO_TITLE'),
    'SettingsFields.kt must not reference non-existent ENABLE_AUTO_TITLE'
  );
});

test('Task B/C (F-06): PreferencesStore registers PreferenceStoreV5Migration, backs up corrupt provider JSON into PROVIDERS_CORRUPT_BACKUP without overwriting prior backup, and redacts secrets via sanitizeProviderLogSummary', () => {
  const prefStore = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt');

  assert.ok(
    /migrations\s*=\s*listOf\([\s\S]*?PreferenceStoreV5Migration\(\)[\s\S]*?\)/.test(prefStore),
    'createSettingsDataStore must include PreferenceStoreV5Migration() in migrations = listOf(...)'
  );

  assert.ok(
    prefStore.includes('val PROVIDERS_CORRUPT_BACKUP = stringPreferencesKey("providers_corrupt_backup")') &&
      prefStore.includes('internal fun sanitizeProviderLogSummary(') &&
      prefStore.includes('onCorruptBackup: ((String) -> Unit)? = null'),
    'PreferencesStore.kt must define PROVIDERS_CORRUPT_BACKUP, sanitizeProviderLogSummary, and onCorruptBackup callback in decodeProvidersResilient'
  );

  const junitTest = readRepoFile('app/src/test/java/me/rerere/rikkahub/data/datastore/ProviderResilienceAndMigrationTest.kt');
  assert.ok(
    junitTest.includes('decodeProvidersResilient_isolatesMalformedRecords_andPreservesValidProviders') &&
      junitTest.includes('sanitizeProviderLogSummary') &&
      junitTest.includes('corruptBackups'),
    'ProviderResilienceAndMigrationTest.kt must test malformed provider isolation, non-destructive corrupt backup, and secret redaction via sanitizeProviderLogSummary'
  );
});

test('Task B (F-08b): SQLite Room migration verifier validates actual Kotlin Migration SQL for 28->29->32, 29->32, 30->31->32, 30->32, and 31->32 plus foreign_key_check & FTS integrity', () => {
  const pyOut = execFileSync('python3', [path.join(REPO_ROOT, 'scripts/verify_room_migration_sqlite.py')], {
    encoding: 'utf8',
  });
  assert.ok(pyOut.includes('SQLITE_MIGRATION_VERIFIED_OK'), 'All SQLite Room migration paths must succeed');
  assert.ok(
    pyOut.includes('KOTLIN_MIGRATION_SQL_PARSED_AND_VERIFIED') &&
      pyOut.includes('PATHS_VERIFIED=28->29->32,29->32,30->31->32,30->32,31->32'),
    'verify_room_migration_sqlite.py must parse and execute SQL from actual Kotlin Migration files across all 5 paths and verify FK/FTS integrity'
  );
});
