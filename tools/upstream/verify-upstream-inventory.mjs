import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = process.cwd();
const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');

const EXCLUDED_ARCHIVE_PREFIXES = ['.git/', '.gradle/', 'build/', 'node_modules/', '.superpowers/'];
const isExcludedTransientPath = (relPath) =>
  typeof relPath === 'string' && EXCLUDED_ARCHIVE_PREFIXES.some((prefix) => relPath === prefix.slice(0, -1) || relPath.startsWith(prefix));

function walkRepoFiles(dir, prefix = '', set = new Set()) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      if (
        entry.name === '.git' ||
        entry.name === '.gradle' ||
        entry.name === 'node_modules' ||
        entry.name === 'build' ||
        entry.name === '.superpowers'
      ) {
        continue;
      }
      walkRepoFiles(path.join(dir, entry.name), rel, set);
    } else {
      set.add(rel);
    }
  }
  return set;
}

const existingFiles = walkRepoFiles(root);
const exists = (rel) => existingFiles.has(rel) || fs.existsSync(path.join(root, rel));

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      out.push(cur);
      cur = '';
    } else {
      cur += ch;
    }
  }
  out.push(cur);
  return out;
}

// 1. Verify upstream lock files
const rikkaLock = JSON.parse(read('upstream-lock/rikkahub.json'));
const stLock = JSON.parse(read('upstream-lock/sillytavern.json'));
assert.equal(rikkaLock.targetTag, '3.1.0', 'rikkahub.json targetTag must be 3.1.0');
assert.equal(rikkaLock.targetCommit, 'eeef60b22ba24383daf51f3c6c2e5b75bbab6bba', 'rikkahub.json targetCommit mismatch');
assert.equal(stLock.version, '1.19.0', 'sillytavern.json version must be 1.19.0');

// 2. Verify audit/10-22-0/upstream-path-inventory.csv and audit/upstream/changed-files.csv
const invLines = read('audit/10-22-0/upstream-path-inventory.csv').trim().split(/\r?\n/);
const chgLines = read('audit/upstream/changed-files.csv').trim().split(/\r?\n/);
assert.equal(invLines.length, 2774, `Expected 2773 data rows + 1 header in upstream-path-inventory.csv, got ${invLines.length}`);
assert.equal(chgLines.length, invLines.length, 'changed-files.csv row count must match upstream-path-inventory.csv');

const header = parseCsvLine(invLines[0]);
const idxUpstreamPath = header.indexOf('upstream_path');
const idxBasePath = header.indexOf('nearest_base_path');
const idxSubsystem = header.indexOf('subsystem');
const idxStatus = header.indexOf('status');
const idxReviewState = header.indexOf('review_state');

const validStatuses = new Set([
  'IDENTICAL',
  'ADAPT',
  'PORT/UNCHANGED',
  'REIMPLEMENT_EQUIVALENT',
  'GENERATED',
  'UPSTREAM_TEST_OR_TOOL',
  'INTENTIONALLY_EXCLUDED',
  'ST_ONLY',
]);

let unresolvedCount = 0;
let missingOnDiskCount = 0;
let excludedCount = 0;
let stOnlyCount = 0;
let upstreamCount = 0;
let transientLeakCount = 0;
const statusCounts = {};

for (let i = 1; i < invLines.length; i++) {
  const cols = parseCsvLine(invLines[i]);
  const upstreamPath = cols[idxUpstreamPath];
  const basePath = cols[idxBasePath];
  const subsystem = cols[idxSubsystem];
  const status = cols[idxStatus];
  const reviewState = cols[idxReviewState];

  assert.ok(subsystem && subsystem.length > 0, `Row ${i} missing subsystem category`);
  assert.ok(validStatuses.has(status), `Row ${i} has invalid status category: ${status}`);
  statusCounts[status] = (statusCounts[status] || 0) + 1;

  if (isExcludedTransientPath(basePath) || isExcludedTransientPath(upstreamPath)) {
    transientLeakCount++;
    console.error(`[verify-upstream-inventory] Excluded transient path must not appear in tracked inventory: ${basePath || upstreamPath}`);
  }

  if (status === 'UNRESOLVED' || reviewState !== 'AUDITED') {
    unresolvedCount++;
  }
  if (status === 'INTENTIONALLY_EXCLUDED') {
    excludedCount++;
    upstreamCount++;
    continue;
  }
  if (status === 'ST_ONLY') {
    stOnlyCount++;
    if (!exists(basePath)) {
      missingOnDiskCount++;
      console.error(`[verify-upstream-inventory] Missing ST_ONLY file on disk: ${basePath}`);
    }
  } else {
    upstreamCount++;
    const targetPath = (status === 'REIMPLEMENT_EQUIVALENT' && basePath && !exists(upstreamPath))
      ? basePath.split(' ')[0]
      : upstreamPath;
    if (!exists(targetPath)) {
      missingOnDiskCount++;
      console.error(`[verify-upstream-inventory] Missing upstream file on disk: ${targetPath} (status=${status})`);
    }
  }
}

assert.equal(transientLeakCount, 0, `Found ${transientLeakCount} excluded transient paths (.superpowers/.git/build) in inventory`);
assert.equal(unresolvedCount, 0, `Found ${unresolvedCount} UNRESOLVED/unaudited rows`);
assert.equal(missingOnDiskCount, 0, `Found ${missingOnDiskCount} non-excluded files missing on disk`);
assert.equal(excludedCount, 3, `Expected 3 INTENTIONALLY_EXCLUDED files, found ${excludedCount}`);
assert.equal(upstreamCount, 1588, `Expected 1588 upstream paths, found ${upstreamCount}`);
assert.equal(stOnlyCount, 1185, `Expected 1185 ST_ONLY paths, found ${stOnlyCount}`);

// 3. Verify audit/10-22-0/base-only-inventory.csv
assert.ok(exists('audit/10-22-0/base-only-inventory.csv'), 'Missing audit/10-22-0/base-only-inventory.csv');
const baseOnlyLines = read('audit/10-22-0/base-only-inventory.csv').trim().split(/\r?\n/);
assert.equal(baseOnlyLines.length - 1, stOnlyCount, 'base-only-inventory.csv count must equal ST_ONLY count (1185)');
for (let i = 1; i < baseOnlyLines.length; i++) {
  const bCols = parseCsvLine(baseOnlyLines[i]);
  assert.ok(!isExcludedTransientPath(bCols[0]), `base-only-inventory.csv row ${i} contains excluded transient path: ${bCols[0]}`);
}

// 4. Verify audit/upstream/unmapped-items.md has 0 unmapped and 0 missing items
const unmappedMd = read('audit/upstream/unmapped-items.md');
assert.ok(unmappedMd.includes('UNMAPPED_COUNT: 0'), 'unmapped-items.md must record UNMAPPED_COUNT: 0');
assert.ok(unmappedMd.includes('MISSING_USER_FACING_COUNT: 0'), 'unmapped-items.md must record MISSING_USER_FACING_COUNT: 0');

console.log(
  `[verify-upstream-inventory] PASS: total=${invLines.length - 1}, upstream=${upstreamCount}, stOnly=${stOnlyCount}, unresolved=0, missingOnDisk=0, excluded=${excludedCount}, statusCounts=${JSON.stringify(statusCounts)}`
);
