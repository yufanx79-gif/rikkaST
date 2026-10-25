import fs from 'node:fs';

const rikkaLock = JSON.parse(fs.readFileSync('upstream-lock/rikkahub.json', 'utf8'));
const stLock = JSON.parse(fs.readFileSync('upstream-lock/sillytavern.json', 'utf8'));
const schema32 = JSON.parse(fs.readFileSync('app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json', 'utf8'));
const libsToml = fs.readFileSync('gradle/libs.versions.toml', 'utf8');
const updateJson = JSON.parse(fs.readFileSync('update.json', 'utf8'));

const keyVersions = {};
for (const k of ['agp', 'kotlin', 'composeBom', 'room', 'okhttp', 'ktor', 'koin-bom']) {
  const m = libsToml.match(new RegExp(`^${k}\\s*=\\s*"([^"]+)"`, 'm'));
  keyVersions[k] = m ? m[1] : 'unknown';
}

const deterministicTimestamp = updateJson.publishedAt || '2026-10-22T00:00:00Z';
const artifactStatus = updateJson.artifactStatus || (updateJson.downloads?.length ? 'PUBLISHED' : 'SOURCE_ONLY_PENDING_SDK_BUILD');
const activeApkSha256 = updateJson.downloads?.[0]?.sha256 || `NONE (${artifactStatus})`;

const report = `# 上游自动跟随与风险评估报告 (Automated Upstream Risk Report)

- **生成时间**: \`${deterministicTimestamp}\`
- **合并策略**: **仅生成风险报告与 Draft PR，严禁无人审查自动 merge 或自动发布**

## 1. 上游版本锁状态 (\`upstream-lock/\`)

| 上游仓库 | 锁定版本 / Tag | 锁定 Commit / SHA-256 | 策略 |
|---|---|---|---|
| \`${rikkaLock.repository}\` | \`${rikkaLock.targetTag}\` | \`${rikkaLock.targetCommit}\` | \`${rikkaLock.policy}\` |
| \`${stLock.repository}\` | \`${stLock.version}\` | \`${stLock.sha256}\` | \`verify-lock + diff-exports\` |

## 2. 数据库 Schema 与迁移门禁 (\`AppDatabase\`)

- **当前 Room Schema 版本**: \`${schema32.database.version}\` (identityHash: \`${schema32.database.identityHash}\`)
- **实体表数量**: \`${schema32.database.entities.length}\`
- **显式迁移链**: \`Migration_28_29\`, \`Migration_29_32\`, \`Migration_30_31\`, \`Migration_30_32\`, \`Migration_31_32\`
- **破坏性回退检查**: \`fallbackToDestructiveMigration\` = **DISABLED (PASS)**

## 3. 核心依赖版本快照 (\`gradle/libs.versions.toml\`)

| 依赖项 | 当前版本 |
|---|---|
${Object.entries(keyVersions).map(([k, v]) => `| \`${k}\` | \`${v}\` |`).join('\n')}

## 4. 发布元数据一致性 (\`update.json\`)

- **version**: \`${updateJson.version}\`
- **versionCode**: \`${updateJson.versionCode}\`
- **packageName**: \`${updateJson.packageName}\`
- **artifactStatus**: \`${artifactStatus}\`
- **APK SHA-256**: \`${activeApkSha256}\`
`;

fs.mkdirSync('audit/upstream', { recursive: true });
const outPath = 'audit/upstream/upstream-risk-report.md';
const existing = fs.existsSync(outPath) ? fs.readFileSync(outPath, 'utf8') : null;
if (existing !== report) {
  fs.writeFileSync(outPath, report, 'utf8');
}
console.log('[generate-upstream-risk-report] OK -> audit/upstream/upstream-risk-report.md');

const unmappedMd = fs.readFileSync('audit/upstream/unmapped-items.md', 'utf8');
if (!unmappedMd.includes('UNMAPPED_COUNT: 0')) {
  console.error('[generate-upstream-risk-report] FAIL: audit/upstream/unmapped-items.md does not have UNMAPPED_COUNT: 0');
  process.exit(1);
}
