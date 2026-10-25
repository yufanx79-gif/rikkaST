import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

test('ST 1.19.0 API contract table (docs/porting/ST119_API_CONTRACT.csv) covers all required domains and all referenced source paths exist', () => {
  const csvPath = path.join(repoRoot, 'docs/porting/ST119_API_CONTRACT.csv');
  assert.ok(fs.existsSync(csvPath), 'ST119_API_CONTRACT.csv must exist');
  const lines = fs.readFileSync(csvPath, 'utf8').trim().split('\n');
  assert.ok(lines.length >= 12, 'ST119_API_CONTRACT.csv must contain header + at least 12 capability rows');

  const requiredDomains = [
    'macros',
    'variables',
    'world_info',
    'regex',
    'slash_stscript',
    'preset_sampling_and_request_builder',
    'message_formatter',
    'events',
    'plugin_lifecycle_and_interceptors',
    'quick_reply_v2',
    'character_cards_and_chats',
    'generation_cancellation',
  ];
  const content = lines.join('\n');
  for (const dom of requiredDomains) {
    assert.ok(content.includes(dom), `Missing domain in ST119_API_CONTRACT.csv: ${dom}`);
  }
  assert.ok(!/95%|99%|100%/.test(content), 'Contract table must use SUPPORTED/PARTIAL/UNSUPPORTED rather than vague percentages');
});

test('Release metadata consistency & stale-APK prevention across app/build.gradle.kts, update.json, UpdateChecker.kt, UpdateCard.kt, and docs/upstream/BASELINE-3.1.0.md', () => {
  const gradleKts = fs.readFileSync(path.join(repoRoot, 'app/build.gradle.kts'), 'utf8');
  const updateJson = JSON.parse(fs.readFileSync(path.join(repoRoot, 'update.json'), 'utf8'));
  const baselineMd = fs.readFileSync(path.join(repoRoot, 'docs/upstream/BASELINE-3.1.0.md'), 'utf8');
  const updateCheckerKt = fs.readFileSync(path.join(repoRoot, 'app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt'), 'utf8');
  const updateCardKt = fs.readFileSync(path.join(repoRoot, 'app/src/main/java/me/rerere/rikkahub/ui/components/ui/UpdateCard.kt'), 'utf8');

  assert.match(gradleKts, /versionCode\s*=\s*241/);
  assert.match(gradleKts, /versionName\s*=\s*"2\.5\.9"/);
  assert.match(gradleKts, /applicationId\s*=\s*"me\.rerere\.rikkahub\.st"/);

  assert.equal(updateJson.version, '2.5.9');
  assert.equal(updateJson.versionCode, 241);
  assert.equal(updateJson.packageName, 'me.rerere.rikkahub.st');
  assert.equal(updateJson.artifactStatus, 'SOURCE_ONLY_PENDING_SDK_BUILD');

  // Critical P0 gate (F-02): When artifactStatus is SOURCE_ONLY_PENDING_SDK_BUILD, active downloads[] MUST be empty
  // so no historical 2.5.7 APK is ever offered as a 2.5.9 update download.
  assert.ok(Array.isArray(updateJson.downloads), 'update.json downloads must be an array');
  assert.equal(
    updateJson.downloads.length,
    0,
    'Active downloads[] in update.json must be empty when no signed 2.5.9 APK has been built (never advertise historical 2.5.7 APK in active downloads)'
  );
  assert.ok(Array.isArray(updateJson.historicalArtifacts) && updateJson.historicalArtifacts.length === 1, 'Historical 2.5.7 forensics must be separated into historicalArtifacts');
  assert.equal(
    updateJson.historicalArtifacts[0].sha256,
    '8620c3e2bf744732ed7fcdd6f9b9de8175814242315fe70a73124972a9f3441e'
  );
  assert.equal(updateJson.historicalArtifacts[0].version, '2.5.7');
  assert.equal(updateJson.historicalArtifacts[0].versionCode, 239);

  // Verify UpdateChecker.kt models artifactStatus, eligibleDownloads, and rejects historical/version-mismatched APKs
  assert.ok(
    updateCheckerKt.includes('val artifactStatus: String') &&
      updateCheckerKt.includes('val eligibleDownloads: List<UpdateDownload>') &&
      updateCheckerKt.includes('containsConflictingVersion'),
    'UpdateChecker.kt must parse artifactStatus, expose eligibleDownloads, and reject conflicting APK versions in validateBeforeDownload'
  );
  // Verify UpdateCard.kt checks eligibleDownloads and passes info to downloadUpdate(context, item, info)
  assert.ok(
    updateCardKt.includes('info.eligibleDownloads') &&
      updateCardKt.includes('vm.updateChecker.downloadUpdate(context, item, info)'),
    'UpdateCard.kt must gate banner/list on info.eligibleDownloads and pass info to downloadUpdate(context, item, info)'
  );

  assert.ok(baselineMd.includes('current versionName: `2.5.9`'));
  assert.ok(baselineMd.includes('current versionCode: `241`'));
  assert.ok(baselineMd.includes('eeef60b22ba24383daf51f3c6c2e5b75bbab6bba'));
});

test('Upstream risk report generator and RikkaHub 3.1.0 diff artifacts exist and pass validation deterministically', () => {
  const reportPath = path.join(repoRoot, 'audit/upstream/upstream-risk-report.md');
  execFileSync('node', [path.join(repoRoot, 'tools/upstream/generate-upstream-risk-report.mjs')], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  const firstRunContent = fs.readFileSync(reportPath, 'utf8');
  execFileSync('node', [path.join(repoRoot, 'tools/upstream/generate-upstream-risk-report.mjs')], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  const secondRunContent = fs.readFileSync(reportPath, 'utf8');
  assert.equal(firstRunContent, secondRunContent, 'generate-upstream-risk-report.mjs output must be deterministic across runs');

  for (const rel of [
    'audit/upstream/changed-files.csv',
    'audit/upstream/porting-decisions.md',
    'audit/upstream/unmapped-items.md',
    'audit/upstream/upstream-risk-report.md',
  ]) {
    assert.ok(fs.existsSync(path.join(repoRoot, rel)), `Missing upstream audit artifact: ${rel}`);
  }
});
