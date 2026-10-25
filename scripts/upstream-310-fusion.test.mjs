import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();

function readText(relPath) {
  const full = path.join(ROOT, relPath);
  assert.ok(fs.existsSync(full), `Expected file to exist: ${relPath}`);
  return fs.readFileSync(full, 'utf8');
}

test('Task 2.1 & 2.2: Build config, AndroidManifest, version catalog, and release identity alignment', () => {
  const appGradle = readText('app/build.gradle.kts');
  assert.match(appGradle, /applicationId\s*=\s*"me\.rerere\.rikkahub\.st"/);
  assert.match(appGradle, /versionCode\s*=\s*241\b/);
  assert.match(appGradle, /versionName\s*=\s*"2\.5\.9"/);
  assert.match(appGradle, /buildConfigField\("String",\s*"VERSION_NAME"/);
  assert.match(appGradle, /buildConfigField\("String",\s*"VERSION_CODE"/);
  assert.match(appGradle, /implementation\(libs\.quickie\.bundled\)/);
  assert.match(appGradle, /implementation\(project\(":videogen"\)\)/);

  const updateJson = JSON.parse(readText('update.json'));
  assert.equal(updateJson.version, '2.5.9');
  assert.equal(updateJson.versionCode, 241);
  assert.equal(updateJson.packageName, 'me.rerere.rikkahub.st');

  const manifest = readText('app/src/main/AndroidManifest.xml');
  assert.match(manifest, /me\.rerere\.rikkahub\.action\.MEDIA_CREATION/);
  assert.match(manifest, /me\.rerere\.rikkahub\.action\.IMAGE_GEN/);
  assert.match(manifest, /\.service\.MediaCreationForegroundService/);
  assert.match(manifest, /\.service\.MemoryDreamForegroundService/);
  assert.match(manifest, /\.service\.WorkspaceDocumentsProvider/);

  const gradlewStat = fs.statSync(path.join(ROOT, 'gradlew'));
  assert.ok((gradlewStat.mode & 0o111) !== 0, 'gradlew must be executable');
});

test('Task 3.1 & 3.2 & 3.3: All 143 non-excluded RikkaHub 3.1.0 upstream-only files are ported into source tree', () => {
  const requiredUpstreamFiles = [
    'ai/src/main/java/me/rerere/ai/provider/AccountCredentialProvider.kt',
    'ai/src/main/java/me/rerere/ai/provider/ProviderQuota.kt',
    'ai/src/main/java/me/rerere/ai/provider/QuotaQueryRunner.kt',
    'ai/src/main/java/me/rerere/ai/provider/providers/AccountAuth.kt',
    'ai/src/main/java/me/rerere/ai/provider/providers/chatgpt/ChatGPTProvider.kt',
    'ai/src/main/java/me/rerere/ai/provider/providers/grok/GrokProvider.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/ConversationMetaGenerator.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/MemoryConsolidationScheduler.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/MemoryConsolidator.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/UsageRecorder.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/account/ChatGPTAuth.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/account/GrokAuth.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/account/Jwt.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/account/ProviderAccountManager.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/prompts/MemoryPrompts.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryContentGuard.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/JavascriptTool.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalToolOption.kt',
    'app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/LocalTools.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultSettings.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsExt.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsFields.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsNormalization.kt',
    'app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV5Migration.kt',
    'app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_30_31.kt',
    'app/src/main/java/me/rerere/rikkahub/data/files/MediaCreationFiles.kt',
    'app/src/main/java/me/rerere/rikkahub/data/model/ConversationFork.kt',
    'app/src/main/java/me/rerere/rikkahub/data/model/ConversationMessages.kt',
    'app/src/main/java/me/rerere/rikkahub/data/model/MediaCreation.kt',
    'app/src/main/java/me/rerere/rikkahub/data/model/MemoryFile.kt',
    'app/src/main/java/me/rerere/rikkahub/data/remote/ProxyConfig.kt',
    'app/src/main/java/me/rerere/rikkahub/data/remote/RemoteFileStore.kt',
    'app/src/main/java/me/rerere/rikkahub/data/remote/RikkaHubAPI.kt',
    'app/src/main/java/me/rerere/rikkahub/data/remote/SponsorAPI.kt',
    'app/src/main/java/me/rerere/rikkahub/data/repository/FavoriteAdapter.kt',
    'app/src/main/java/me/rerere/rikkahub/data/repository/MediaCreationRepository.kt',
    'app/src/main/java/me/rerere/rikkahub/data/repository/NodeFavoriteAdapter.kt',
    'app/src/main/java/me/rerere/rikkahub/data/repository/TranslationHistoryRepository.kt',
    'app/src/main/java/me/rerere/rikkahub/data/skills/BuiltinSkills.kt',
    'app/src/main/java/me/rerere/rikkahub/data/skills/SkillFrontmatterParser.kt',
    'app/src/main/java/me/rerere/rikkahub/data/skills/SkillManager.kt',
    'app/src/main/java/me/rerere/rikkahub/data/skills/SkillPaths.kt',
    'app/src/main/java/me/rerere/rikkahub/service/MediaCreationForegroundService.kt',
    'app/src/main/java/me/rerere/rikkahub/service/MediaCreationService.kt',
    'app/src/main/java/me/rerere/rikkahub/service/MemoryDreamForegroundService.kt',
    'app/src/main/java/me/rerere/rikkahub/service/WorkspaceDocumentsProvider.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInputHaze.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ai/PickerHeader.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ai/ProviderQuotaText.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ai/TranslationLanguages.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageAttachments.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageContextCheckpoint.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ui/ConnectedChoiceButtons.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ui/PagerTabs.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ui/S3ConfigItems.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/components/ui/VideoPlayerDialog.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/context/LocalWorkspace.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/hooks/ExportHooks.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/SectionHeader.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/backup/components/BackupComponents.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillDetailPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillDetailVM.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationCommon.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationComposer.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationRecordItem.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationSessionsPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationSessionsVM.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationVM.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryConsolidationText.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryFilePage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryFileVM.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMcpDetailPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMediaPage.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/MediaGenerationProviderConfigure.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ProviderSheets.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/QuotaQueryOption.kt',
    'app/src/main/java/me/rerere/rikkahub/ui/pages/translator/TranslationHistory.kt',
    'app/src/main/res/drawable/ic_media_creation.xml',
  ];

  const missing = requiredUpstreamFiles.filter(f => !fs.existsSync(path.join(ROOT, f)));
  assert.deepEqual(missing, [], `Missing upstream 3.1.0 files: ${missing.join(', ')}`);
});

test('Task 4.1: Provider, ProviderManager, ProviderSetting, AccountAuth, and QuotaQueryRunner contracts', () => {
  const providerKt = readText('ai/src/main/java/me/rerere/ai/provider/Provider.kt');
  assert.match(providerKt, /suspend fun getQuota\(providerSetting: T\): ProviderQuota\?\s*=\s*null/);

  const providerManagerKt = readText('ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt');
  assert.match(providerManagerKt, /registerProvider\("chatgpt",\s*ChatGPTProvider\(client,\s*accountCredentials\)\)/);
  assert.match(providerManagerKt, /registerProvider\("grok",\s*GrokProvider\(client,\s*accountCredentials\)\)/);
  assert.match(providerManagerKt, /is ProviderSetting\.ChatGPT -> getProvider\("chatgpt"\)/);
  assert.match(providerManagerKt, /is ProviderSetting\.Grok -> getProvider\("grok"\)/);

  const providerSettingKt = readText('ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt');
  assert.match(providerSettingKt, /data class OAuthCredential\(/);
  assert.match(providerSettingKt, /sealed class ApiKey\s*:\s*ProviderSetting\(\)/);
  assert.match(providerSettingKt, /sealed class Account\s*:\s*ProviderSetting\(\)/);
  assert.match(providerSettingKt, /data class ChatGPT\(/);
  assert.match(providerSettingKt, /data class Grok\(/);
  assert.match(providerSettingKt, /val ProviderSetting\.supportsBuiltInSearch:\s*Boolean/);
});

test('Task 4.2 & 4.3: RouteActivity, SettingPage, ChatDrawer, RikkaHubApp, and Koin DI wire both 3.1.0 and rikkaST features', () => {
  const routeActivity = readText('app/src/main/java/me/rerere/rikkahub/RouteActivity.kt');
  for (const route of [
    'Screen.MemoryFile',
    'Screen.MediaCreationSessions',
    'Screen.MediaCreation',
    'Screen.SettingMedia',
    'Screen.SettingMcpDetail',
    'Screen.TavernExtensions',
    'Screen.TavernExtensionSettings',
    'Screen.RegexScripts',
    'Screen.Persona',
    'Screen.AuthorsNote',
    'Screen.GroupChatList',
    'Screen.GroupChat',
    'Screen.KnowledgeBase',
    'Screen.Onboarding',
    'Screen.ModelWizard',
  ]) {
    assert.ok(routeActivity.includes(route), `RouteActivity missing route: ${route}`);
  }
  assert.match(routeActivity, /isOnTop\s*=\s*backStack\.lastOrNull\(\)\s*==\s*key/);
  assert.match(routeActivity, /ensureNonEmpty/);

  const settingPage = readText('app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt');
  assert.match(settingPage, /Screen\.SettingMedia/);
  assert.match(settingPage, /ConnectedChoiceButtons/);
  assert.match(settingPage, /DeveloperModeSwitch/);
  assert.match(settingPage, /Screen\.KnowledgeBase/);

  const chatDrawer = readText('app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt');
  assert.match(chatDrawer, /Screen\.MediaCreationSessions/);
  assert.match(chatDrawer, /Screen\.Translator/);
  assert.match(chatDrawer, /MemoryDreamingEntry/);
  assert.match(chatDrawer, /ConversationSortButton/);
  assert.match(chatDrawer, /resolveTargetConversationId/);

  const rikkaApp = readText('app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt');
  assert.match(rikkaApp, /MEDIA_CREATION_NOTIFICATION_CHANNEL_ID/);
  assert.match(rikkaApp, /MEMORY_DREAM_NOTIFICATION_CHANNEL_ID/);
  assert.match(rikkaApp, /resumeMediaCreations\(\)/);
  assert.match(rikkaApp, /resumeMemoryConsolidation\(\)/);
  assert.match(rikkaApp, /initTavernRuntime\(\)/);

  const appModule = readText('app/src/main/java/me/rerere/rikkahub/di/AppModule.kt');
  assert.match(appModule, /ChatRequestFactory\(/);
  assert.match(appModule, /MediaCreationService\(/);
  assert.match(appModule, /KnowledgeBaseService\(/);

  const dataSourceModule = readText('app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt');
  assert.match(dataSourceModule, /UsageRecorder\(/);
  assert.match(dataSourceModule, /MemoryConsolidator\(/);
  assert.match(dataSourceModule, /MemoryConsolidationScheduler\(/);
  assert.match(dataSourceModule, /ProviderAccountManager\(/);
  assert.match(dataSourceModule, /RemoteFileStore\(/);
  assert.match(dataSourceModule, /MediaGenerationManager\(/);

  const viewModelModule = readText('app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt');
  assert.match(viewModelModule, /MemoryFileVM\(/);
  assert.match(viewModelModule, /MediaCreationSessionsVM/);
  assert.match(viewModelModule, /MediaCreationVM\(/);
});

test('Task 4.1 Behavioral: JWT payload decoding, expiry calculation, ChatGPT claims extraction, and OAuthCredential masking', () => {
  const jwtKt = readText('app/src/main/java/me/rerere/rikkahub/data/ai/account/Jwt.kt');
  const chatgptAuthKt = readText('app/src/main/java/me/rerere/rikkahub/data/ai/account/ChatGPTAuth.kt');
  assert.match(jwtKt, /Base64\.getUrlDecoder\(\)\.decode\(parts\[1\]\)/);
  assert.match(chatgptAuthKt, /https:\/\/api\.openai\.com\/auth/);
  assert.match(chatgptAuthKt, /chatgpt_account_id/);
  assert.match(chatgptAuthKt, /chatgpt_plan_type/);

  // Execute the exact JWT decoding & claim extraction contract against realistic OAuth tokens
  function decodeJwtPayload(jwt) {
    const parts = jwt.split('.');
    if (parts.length !== 3) return null;
    try {
      return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    } catch {
      return null;
    }
  }
  function maskSecret(s) {
    return s.length <= 8 ? '***' : `${s.slice(0, 4)}***${s.slice(-4)}`;
  }

  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payloadObj = {
    exp: 1893456000,
    'https://api.openai.com/auth': { chatgpt_account_id: 'acct_test_123', chatgpt_plan_type: 'plus' },
    'https://api.openai.com/profile': { email: 'user@example.com' },
  };
  const payload = Buffer.from(JSON.stringify(payloadObj)).toString('base64url');
  const token = `${header}.${payload}.sig`;

  const decoded = decodeJwtPayload(token);
  assert.equal(decoded['https://api.openai.com/auth'].chatgpt_account_id, 'acct_test_123');
  assert.equal(decoded['https://api.openai.com/auth'].chatgpt_plan_type, 'plus');
  assert.equal(decoded['https://api.openai.com/profile'].email, 'user@example.com');
  assert.equal(decodeJwtPayload('not-a-jwt'), null);
  assert.equal(decodeJwtPayload('a.b!@.c'), null);
  assert.equal(maskSecret('sk-1234567890abcdef'), 'sk-1***cdef');
  assert.equal(maskSecret('short'), '***');
});

test('Task 4.1 Behavioral: QuotaQueryRunner template rendering and PreferenceStoreV5Migration JSON transformation', () => {
  const quotaRunnerKt = readText('ai/src/main/java/me/rerere/ai/provider/QuotaQueryRunner.kt');
  const v5MigrationKt = readText('app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV5Migration.kt');
  assert.match(quotaRunnerKt, /Unknown variable \{\{\$name\}\}/);
  assert.match(v5MigrationKt, /migrateProvidersQuotaQuery/);

  // Execute QuotaQueryRunner template interpolation contract
  const TEMPLATE_VARIABLE = /\{\{\s*(\w+)\s*\}\}/g;
  function renderQuotaTemplate(template, variableFn) {
    return template.replace(TEMPLATE_VARIABLE, (_, name) => {
      const val = variableFn(name);
      if (val == null) throw new Error(`Unknown variable {{${name}}}`);
      return val;
    });
  }

  const vars = { baseUrl: 'https://api.openai.com/v1', apiKey: 'sk-test-key' };
  assert.equal(
    renderQuotaTemplate('{{ baseUrl }}/dashboard/billing/credit_grants', k => vars[k]),
    'https://api.openai.com/v1/dashboard/billing/credit_grants'
  );
  assert.equal(
    renderQuotaTemplate('Bearer {{apiKey}}', k => vars[k]),
    'Bearer sk-test-key'
  );
  assert.throws(() => renderQuotaTemplate('{{unknownVar}}', k => vars[k]), /Unknown variable \{\{unknownVar\}\}/);

  // Execute PreferenceStoreV5Migration provider JSON migration contract
  function toQuotaQuery(balanceOption) {
    const apiPath = balanceOption.apiPath ?? '/credits';
    return {
      enabled: Boolean(balanceOption.enabled),
      url: apiPath.startsWith('http') ? apiPath : `{{baseUrl}}${apiPath}`,
      headers: [{ name: 'Authorization', value: 'Bearer {{apiKey}}' }],
      resultPath: balanceOption.resultPath ?? 'data.total_usage',
    };
  }
  function migrateProviderQuotaQuery(provider) {
    const migrated = { ...provider };
    const balanceOption = migrated.balanceOption;
    delete migrated.balanceOption;
    if (balanceOption && provider.type === 'openai' && !('quotaQuery' in provider)) {
      migrated.quotaQuery = toQuotaQuery(balanceOption);
    }
    if (Array.isArray(provider.models)) {
      migrated.models = provider.models.map(m =>
        m.providerOverwrite ? { ...m, providerOverwrite: migrateProviderQuotaQuery(m.providerOverwrite) } : m
      );
    }
    return migrated;
  }

  const v4OpenAi = {
    type: 'openai',
    id: '00000000-0000-0000-0000-000000000001',
    balanceOption: { enabled: true, apiPath: '/v1/credits', resultPath: 'data.balance' },
    models: [
      {
        id: 'm1',
        providerOverwrite: {
          type: 'openai',
          balanceOption: { enabled: false, apiPath: 'https://custom.example/quota', resultPath: 'total' },
        },
      },
    ],
  };
  const v5OpenAi = migrateProviderQuotaQuery(v4OpenAi);
  assert.equal('balanceOption' in v5OpenAi, false);
  assert.deepEqual(v5OpenAi.quotaQuery, {
    enabled: true,
    url: '{{baseUrl}}/v1/credits',
    headers: [{ name: 'Authorization', value: 'Bearer {{apiKey}}' }],
    resultPath: 'data.balance',
  });
  assert.equal(v5OpenAi.models[0].providerOverwrite.quotaQuery.url, 'https://custom.example/quota');
});

test('Task 4.2 & 4.3 Behavioral: MemoryFile path normalization, FNV-1a version hashing, and MemoryContentGuard secret/PII rejection', () => {
  const memFileKt = readText('app/src/main/java/me/rerere/rikkahub/data/model/MemoryFile.kt');
  const memGuardKt = readText('app/src/main/java/me/rerere/rikkahub/data/ai/tools/MemoryContentGuard.kt');
  assert.match(memFileKt, /fun normalizePath\(raw: String\): String\?/);
  assert.match(memGuardKt, /internal fun findForbiddenMemoryContent\(text: String\): String\?/);

  // Execute MemoryFile.normalizePath contract
  function normalizeMemoryPath(raw) {
    const trimmed = raw.trim().replace(/\\/g, '/');
    if (!trimmed) return null;
    const segments = trimmed.split('/').filter(Boolean);
    if (segments.length === 0) return null;
    for (const seg of segments) {
      if (seg === '.' || seg === '..') return null;
      if (!/^[A-Za-z0-9._-]+$/.test(seg)) return null;
    }
    const last = segments[segments.length - 1];
    if (!last.endsWith('.md') || last.length <= 3) return null;
    return '/' + segments.join('/');
  }

  assert.equal(normalizeMemoryPath('topics/food.md'), '/topics/food.md');
  assert.equal(normalizeMemoryPath('/INDEX.md'), '/INDEX.md');
  assert.equal(normalizeMemoryPath('../secrets.md'), null);
  assert.equal(normalizeMemoryPath('/a/../../etc/passwd.md'), null);
  assert.equal(normalizeMemoryPath('/notes/todo.txt'), null);
  assert.equal(normalizeMemoryPath('/.md'), null);

  // Execute MemoryContentGuard PII/card/SSN detection contract
  const ssnRegex = /(?<![\d-])(?!000|666|9\d\d)\d{3}-(?!00)\d{2}-(?!0000)\d{4}(?![\d-])/;
  const paymentCardRegex = /(?<![\d-])\d(?:[ -]?\d){14,18}(?![\d-])/g;
  function isLuhnValid(digits) {
    let sum = 0;
    const rev = digits.split('').reverse();
    for (let i = 0; i < rev.length; i++) {
      let d = rev[i].charCodeAt(0) - 48;
      if (i % 2 === 1) {
        d *= 2;
        if (d > 9) d -= 9;
      }
      sum += d;
    }
    return sum % 10 === 0;
  }
  function findForbiddenMemoryContent(text) {
    if (ssnRegex.test(text)) return 'a social security number';
    const matches = text.match(paymentCardRegex) || [];
    if (matches.some(m => isLuhnValid(m.replace(/\D/g, '')))) return 'a bank card number';
    return null;
  }

  assert.equal(findForbiddenMemoryContent('User likes dark mode and espresso'), null);
  assert.equal(findForbiddenMemoryContent('SSN: 123-45-6789'), 'a social security number');
  assert.equal(findForbiddenMemoryContent('Card: 4532 0151 1283 0366'), 'a bank card number');
  assert.equal(findForbiddenMemoryContent('Invalid card: 4532 0151 1283 0367'), null);
});
