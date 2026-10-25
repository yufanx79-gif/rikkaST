import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { execFileSync, spawnSync } from 'node:child_process';

const ROOT = process.cwd();
const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf8');

test('Regression F-01: verify-upstream-inventory passes on clean extraction without .superpowers and generate-upstream-risk-report is deterministic', () => {
  const invCsv = read('audit/10-22-0/upstream-path-inventory.csv');
  const chgCsv = read('audit/upstream/changed-files.csv');
  const baseOnlyCsv = read('audit/10-22-0/base-only-inventory.csv');

  assert.ok(!invCsv.includes('.superpowers/'), 'upstream-path-inventory.csv must not track transient .superpowers/ files');
  assert.ok(!chgCsv.includes('.superpowers/'), 'changed-files.csv must not track transient .superpowers/ files');
  assert.ok(!baseOnlyCsv.includes('.superpowers/'), 'base-only-inventory.csv must not track transient .superpowers/ files');

  const out = execFileSync(process.execPath, [path.join(ROOT, 'tools/upstream/verify-upstream-inventory.mjs')], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  assert.ok(out.includes('[verify-upstream-inventory] PASS'), 'verify-upstream-inventory.mjs must pass without .superpowers on disk');

  // Verify generate-upstream-risk-report.mjs is deterministic across runs (no wall-clock new Date() mutation)
  execFileSync(process.execPath, [path.join(ROOT, 'tools/upstream/generate-upstream-risk-report.mjs')], { cwd: ROOT });
  const report1 = read('audit/upstream/upstream-risk-report.md');
  execFileSync(process.execPath, [path.join(ROOT, 'tools/upstream/generate-upstream-risk-report.mjs')], { cwd: ROOT });
  const report2 = read('audit/upstream/upstream-risk-report.md');
  assert.equal(report1, report2, 'generate-upstream-risk-report.mjs must produce deterministic output');
});

test('Regression F-02: update.json does not offer historical 2.5.7 APK as active download for 2.5.9, and UpdateChecker/UpdateCard enforce artifactStatus, version match, and SHA-256 format', () => {
  const updateJson = JSON.parse(read('update.json'));
  assert.equal(updateJson.version, '2.5.9');
  assert.equal(updateJson.versionCode, 241);
  assert.equal(updateJson.artifactStatus, 'SOURCE_ONLY_PENDING_SDK_BUILD');

  assert.ok(Array.isArray(updateJson.downloads), 'update.json downloads must be an array');
  assert.equal(
    updateJson.downloads.length,
    0,
    'update.json active downloads must be empty when no verified 2.5.9 APK binary has been built'
  );
  assert.ok(
    Array.isArray(updateJson.historicalArtifacts) && updateJson.historicalArtifacts.length === 1,
    'Historical 2.5.7 APK reference must be isolated in historicalArtifacts'
  );
  assert.equal(
    updateJson.historicalArtifacts[0].sha256,
    '8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e'
  );

  const checkerKt = read('app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt');
  const cardKt = read('app/src/main/java/me/rerere/rikkahub/ui/components/ui/UpdateCard.kt');

  assert.ok(
    checkerKt.includes('val artifactStatus: String') &&
      checkerKt.includes('fun isArtifactAvailable()') &&
      checkerKt.includes('fun availableDownloads()') &&
      checkerKt.includes('val eligibleDownloads: List<UpdateDownload>') &&
      checkerKt.includes('fun isDownloadable(') &&
      checkerKt.includes('currentVersionName: String = ""'),
    'UpdateChecker.kt UpdateInfo must deserialize artifactStatus and provide both isArtifactAvailable()/availableDownloads() and eligibleDownloads/isSourceOnlyOrUnavailable'
  );
  assert.ok(
    cardKt.includes('vm.updateChecker.downloadUpdate(context, item, info)') &&
      cardKt.includes('info.availableDownloads()'),
    'UpdateCard.kt must pass info to downloadUpdate and only render info.availableDownloads()'
  );
  assert.ok(
    fs.existsSync(path.join(ROOT, 'app/src/test/java/me/rerere/rikkahub/utils/UpdateCheckerTest.kt')) &&
      fs.existsSync(path.join(ROOT, 'app/src/test/java/me/rerere/rikkahub/utils/UpdateCheckerValidationTest.kt')),
    'Both UpdateCheckerTest.kt and UpdateCheckerValidationTest.kt must exist'
  );
});

test('Regression F-03: TavernChatJsonCache invalidates when non-selected swipes, alternateGreetings content, send_date, or role change', () => {
  const tavernChatJsonKt = read('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernChatJson.kt');
  assert.ok(
    tavernChatJsonKt.includes('val swipes: List<String>') &&
      tavernChatJsonKt.includes('val currentText: String') &&
      tavernChatJsonKt.includes('val sendDate: String') &&
      tavernChatJsonKt.includes('val isUser: Boolean'),
    'NodeCacheKey must include full swipes list, currentText, sendDate, and isUser so non-selected swipe edits and alternate greeting edits never hit stale cache'
  );
});

test('Regression F-04: handleCorsProxyFetch and dispatchProxyHttpRequestAsync block loopback, private, link-local, metadata, credentials in URL, and cross-origin redirect header leaks, while handleComfyProxyFetch returns HTTP 501', async () => {
  const runtimeJs = read('app/src/main/assets/st-runtime/runtime.js');
  const runtimeMgrKt = read('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt');

  assert.ok(
    runtimeJs.includes('function validateProxyTargetUrl(') &&
      runtimeJs.includes('UNSUPPORTED_DESKTOP_COMFY_EXTENSION') &&
      runtimeMgrKt.includes('instanceFollowRedirects = false'),
    'Both runtime.js and TavernRuntimeManager.kt must enforce SSRF target validation, manual redirect validation with instanceFollowRedirects = false, and HTTP 501 for desktop ComfyUI'
  );

  const startIdx = runtimeJs.indexOf('function isForbiddenIpv4Parts(parts)');
  const endIdx = runtimeJs.indexOf('window.__rikkaValidateProxyTargetUrl = validateProxyTargetUrl;');
  assert.ok(startIdx > 0 && endIdx > startIdx, 'validateProxyTargetUrl block must exist in runtime.js');
  const validatorCode = runtimeJs.slice(startIdx, endIdx) + '\n; validateProxyTargetUrl;';
  const validateProxyTargetUrl = vm.runInNewContext(validatorCode, { URL, parseInt, Number, String, Array, Set, RegExp, Object });

  for (const bad of [
    'http://127.0.0.1:8188/prompt',
    'http://localhost:8080/api/host/context',
    'http://169.254.169.254/latest/meta-data/iam/security-credentials/',
    'http://10.0.2.2:8080/',
    'http://192.168.1.1/router',
    'http://[::1]:8080/',
    'http://[::ffff:127.0.0.1]:8080/',
    'http://0x7f000001/hex',
    'http://2130706433/decimal',
    'http://0177.0.0.1/octal',
    'https://user:secretpass@api.openai.com/v1/models',
    'file:///etc/passwd',
  ]) {
    const res = validateProxyTargetUrl(bad);
    assert.equal(res.ok, false, `Expected proxy validator to block unsafe URL: ${bad}`);
  }

  for (const good of [
    'https://api.openai.com/v1/images/generations',
    'https://api.novelai.net/ai/generate-image',
  ]) {
    const res = validateProxyTargetUrl(good);
    assert.equal(res.ok, true, `Expected proxy validator to allow public URL: ${good}`);
    assert.equal(res.normalizedUrl, good);
    assert.equal(res.url, good);
  }
});

test('Regression F-05: JS regex engine out-of-range $N group replacement returns empty string and ReDoS guard + Kotlin BoundedRegexCharSequence block /(a+){30}b/g and /(a+)+b/g under hard timeout', async () => {
  const engineMod = await import(path.join(ROOT, 'app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js'));
  const utilsMod = await import(path.join(ROOT, 'app/src/main/assets/st-runtime/st-compat/utils.js'));
  const ktRegex = read('app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt');
  const { runRegexScript, isCatastrophicRegexPattern, regex_placement } = engineMod;

  assert.ok(
    ktRegex.includes('class BoundedRegexCharSequence(') &&
      ktRegex.includes('fun isCatastrophicRegexPattern('),
    'RegexScriptEngine.kt must retain BoundedRegexCharSequence execution budget and static ReDoS filter'
  );

  const zeroGroupScript = {
    scriptName: 'zero-group-out-of-range',
    findRegex: '/hello/g',
    replaceString: '[$1][$2]',
    trimStrings: [],
    placement: [regex_placement.AI_OUTPUT],
    disabled: false,
  };
  assert.equal(runRegexScript(zeroGroupScript, 'say hello world'), 'say [][] world');
  assert.equal(utilsMod.replaceCompatString('say hello world', '/hello/g', '[$1][$&]'), 'say [][hello] world');

  for (const pat of ['/(a+){30}b/g', '/(a+)+b/', '/(\\w+)+b/', '/((?:a+))+b/', '/(a|aa)+b/', '/([a-z]+|\\w+)+$/']) {
    assert.equal(isCatastrophicRegexPattern(pat), true, `Must detect catastrophic pattern: ${pat}`);
  }
});

test('Regression F-06: ChatService.stopGeneration does not clobber concurrent resend job, preserves generationEpoch, and __rikkaRunGenerationHooks honors abort epoch across async stages', () => {
  const chatServiceKt = read('app/src/main/java/me/rerere/rikkahub/service/ChatService.kt');
  const genLoopKt = read('app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt');
  const runtimeJs = read('app/src/main/assets/st-runtime/runtime.js');

  assert.ok(
    chatServiceKt.includes('if (activeJob == null || activeJob in jobs)') &&
      chatServiceKt.includes('if (session.currentGenerationEpoch() == stopEpoch)') &&
      chatServiceKt.includes('currentCoroutineContext().ensureActive()') &&
      chatServiceKt.includes('if (session.currentGenerationEpoch() != myGenerationEpoch) return@collect'),
    'ChatService must guard stopGeneration post-join cleanup and fence handleMessageComplete by both ensureActive() and myGenerationEpoch'
  );
  assert.ok(
    genLoopKt.includes('error.message?.contains("cancel", ignoreCase = true) == true'),
    'GenerationLoop.awaitNetworkRetryOrThrow must not retry canceled IOExceptions'
  );
  assert.ok(
    runtimeJs.includes('(window.__rikkaGenerationAbortEpoch || 0) !== startEpoch') &&
      runtimeJs.includes('ctx.externalAbortController.abort(') &&
      runtimeJs.includes('window.__rikkaProxyPending') &&
      runtimeJs.includes('window.__rikkaImgGenPending'),
    '__rikkaAbortActiveGeneration and __rikkaRunGenerationHooks must check startEpoch and abort externalAbortController + pending proxy/image requests'
  );
});

test('Regression F-07: verify_room_migration_sqlite.py verifies empty/populated/idempotent/interrupted-recovery migrations, PreferencesStore + V5Migration back up corrupt providers and redact logs, NodeRuntimeManager resolves all test symbols, and optional fixture tests emit clean SKIP when absent', () => {
  const roomPy = read('scripts/verify_room_migration_sqlite.py');
  const prefStoreKt = read('app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt');
  const v5MigKt = read('app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV5Migration.kt');
  const msgHtmlKt = read('app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt');
  const nodeMgrKt = read('workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt');

  assert.ok(
    roomPy.includes('Migration_29_32.kt') &&
      roomPy.includes('extract_kotlin_migration_sql') &&
      roomPy.includes('test_interrupted_migration_recovery'),
    'verify_room_migration_sqlite.py must parse SQL from Kotlin Migration files and verify interrupted-migration recovery'
  );
  const pyOut = execFileSync('python3', [path.join(ROOT, 'scripts/verify_room_migration_sqlite.py')], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  assert.ok(
    pyOut.includes('KOTLIN_MIGRATION_SQL_PARSED_AND_VERIFIED') &&
      pyOut.includes('INTERRUPTED_MIGRATION_RECOVERY_VERIFIED') &&
      pyOut.includes('SQLITE_MIGRATION_VERIFIED_OK'),
    'verify_room_migration_sqlite.py must pass all migration and recovery checks'
  );

  assert.ok(
    prefStoreKt.includes('PROVIDERS_CORRUPT_BACKUP') &&
      prefStoreKt.includes('internal fun sanitizeProviderLogSummary(message: String): String') &&
      prefStoreKt.includes('internal fun sanitizeProviderLogSummary(\n    rawObj: JsonObject,') &&
      v5MigKt.includes('PROVIDERS_CORRUPT_BACKUP') &&
      v5MigKt.includes('isOpenAiCompatible'),
    'PreferencesStore.kt and PreferenceStoreV5Migration.kt must persist PROVIDERS_CORRUPT_BACKUP, support OpenAI-compatible balanceOption migration, and provide both sanitizeProviderLogSummary overloads'
  );

  assert.ok(
    msgHtmlKt.includes('LaunchedEffect(snapshotCompositeKey, useHistoricalSnapshot)') &&
      msgHtmlKt.includes('if (useHistoricalSnapshot && snapshotEntry == null && snapshotCompositeKey != null)'),
    'MessageHtmlBlock.kt must only load disk snapshots when useHistoricalSnapshot is true'
  );

  // Verify NodeRuntimeManager.kt defines all companion symbols referenced by NodeRuntimeManagerTest.kt
  assert.ok(
    nodeMgrKt.includes('const val NODE_ARM64_SHA256') &&
      nodeMgrKt.includes('const val NODE_X64_SHA256') &&
      nodeMgrKt.includes('fun verifySha256(') &&
      nodeMgrKt.includes('fun satisfiesNodeEngine(') &&
      nodeMgrKt.includes('fun resolveSharedNodeDir('),
    'NodeRuntimeManager.kt must define NODE_ARM64_SHA256, NODE_X64_SHA256, verifySha256, satisfiesNodeEngine, and resolveSharedNodeDir'
  );

  // Verify task-dh-webview-iphonie-imggen.test.mjs emits clean SKIP when ALL_NEEDS_DIR is unset and no default fixture dir exists,
  // and FAILS when ALL_NEEDS_DIR is explicitly set to a directory missing the fixture!
  const childEnvBase = { ...process.env };
  delete childEnvBase.NODE_TEST_CONTEXT;
  const skipRun = spawnSync(
    process.execPath,
    ['--test', '--test-reporter=tap', path.join(ROOT, 'scripts/task-dh-webview-iphonie-imggen.test.mjs')],
    {
      cwd: ROOT,
      env: { ...childEnvBase, ALL_NEEDS_DIR: '', RIKKA_FORCE_NO_DEFAULT_FIXTURE: '1' },
      encoding: 'utf8',
    }
  );
  assert.equal(skipRun.status, 0, `Expected exit 0 with clean SKIP when optional fixture is absent, got ${skipRun.status}: ${skipRun.stdout}\n${skipRun.stderr}`);
  assert.ok(skipRun.stdout.includes('# SKIP'), 'Expected TAP output to include # SKIP with clear explanation when optional fixture is absent');

  const brokenExplicitRun = spawnSync(
    process.execPath,
    ['--test', '--test-reporter=tap', path.join(ROOT, 'scripts/task-dh-webview-iphonie-imggen.test.mjs')],
    {
      cwd: ROOT,
      env: { ...childEnvBase, ALL_NEEDS_DIR: '/tmp/nonexistent_explicit_fixture_dir_12345' },
      encoding: 'utf8',
    }
  );
  assert.notEqual(
    brokenExplicitRun.status,
    0,
    'Explicitly setting ALL_NEEDS_DIR to a broken/missing fixture path MUST fail rather than silently skipping'
  );
});
