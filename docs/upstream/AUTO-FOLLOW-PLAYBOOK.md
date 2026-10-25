# Upstream Auto-Follow & Verification Playbook (`10-22-0`)

## 1. Pinned Upstream Locks (`upstream-lock/`)

1. **RikkaHub Upstream (`upstream-lock/rikkahub.json`)**:
   - Repository: `https://github.com/rikkahub/rikkahub`
   - Pinned Tag: `3.1.0`
   - Pinned Commit: `eeef60b22ba24383daf51f3c6c2e5b75bbab6bba`
   - Archive SHA-256: `ac64a2ec712588704ed63e193ab760141aa85d8a819b2a0b05b4efadf41a8c67`
2. **SillyTavern Upstream (`upstream-lock/sillytavern.json`)**:
   - Repository: `https://github.com/SillyTavern/SillyTavern`
   - Pinned Version: `1.19.0`
   - Archive SHA-256: `19f34009d55d4e2dd394ce9d6dd6e5d1b0e5b9e5d57df8b5aa325a44c18969be`

## 2. Fail-Closed Automated Verification Gates

Every upstream sync or PR must execute and pass the following gates in order:

```bash
# 1. Verify pinned upstream lockfiles & DB schema v32 invariants
node tools/st-sync/verify-lock.mjs
node tools/upstream/check-tags.mjs
node tools/upstream/check-db-schema.mjs
node tools/upstream/verify-upstream-inventory.mjs

# 2. Verify SillyTavern 1.19.0 export/macro/world-info/slash-command parity
node tools/st-sync/diff-exports.mjs
node tools/st-sync/diff-macros.mjs
node tools/st-sync/diff-world-info.mjs
node tools/st-sync/diff-slash-commands.mjs

# 3. Verify static ESM module dependency graph (0 missing files / exports)
node scripts/esm-graph-validator.mjs

# 4. Run full automated regression & fusion test suites
node --test scripts/room-migration-v29-v32.test.mjs
node --test scripts/runtime-module-linking.test.mjs
node --test scripts/st-runtime-fixes.test.mjs
node --test scripts/upstream-310-fusion.test.mjs
node --test scripts/webview-stop-and-regex-Regression.test.mjs
node --test scripts/phase5-9-integration.test.mjs

# 5. Generate updated upstream risk report (fails closed if UNMAPPED_COUNT > 0)
node tools/upstream/generate-upstream-risk-report.mjs
```

## 3. Non-Negotiable Merge & Release Policy

- **No Unverified Auto-Merge / Auto-Release**: `.github/workflows/upstream-watch.yml` only produces the risk report artifact and never auto-merges or publishes releases.
- **Zero Silent Unmapped Drift**: `tools/upstream/generate-upstream-risk-report.mjs` verifies that `audit/upstream/unmapped-items.md` contains `UNMAPPED_COUNT: 0` and exits non-zero if any upstream file or feature remains unmapped.
