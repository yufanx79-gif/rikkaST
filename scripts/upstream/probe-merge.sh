#!/usr/bin/env bash
set -euo pipefail
mkdir -p artifacts/upstream-report
if git rev-parse --verify upstream/3.1.0 >/dev/null 2>&1; then
  BASE=$(git merge-base HEAD upstream/3.1.0 || true)
  if [ -n "$BASE" ]; then
    git merge-tree "$BASE" HEAD upstream/3.1.0 > artifacts/upstream-report/merge-tree.txt || true
  fi
fi
echo "[probe-merge] OK"
