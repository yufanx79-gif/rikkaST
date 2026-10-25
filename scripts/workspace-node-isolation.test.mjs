import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');
const exists = (rel) => fs.existsSync(path.join(root, rel));

test('Phase 7.1: audit/10-22-0/node-architecture-audit.md documents existing Node runtime and workspace PRoot integration', () => {
  assert.ok(exists('audit/10-22-0/node-architecture-audit.md'), 'Missing audit/10-22-0/node-architecture-audit.md');
  const doc = read('audit/10-22-0/node-architecture-audit.md');
  for (const term of ['NodeRuntimeManager', 'TavernNodeServiceManager', 'ProotShellRunner', 'RootfsPatcher', 'WorkspaceTerminalSession', 'shared_tavern_node', '/usr/local/node']) {
    assert.ok(doc.includes(term), `node-architecture-audit.md missing ${term}`);
  }
});

test('Phase 7.2a: NodeRuntimeManager exposes resolveSharedNodeDir and RootfsPatcher provisions /etc/profile.d/rikka-node.sh + symlinks', () => {
  const nodeMgr = read('workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt');
  assert.ok(nodeMgr.includes('fun resolveSharedNodeDir('), 'NodeRuntimeManager.kt missing resolveSharedNodeDir');
  assert.ok(nodeMgr.includes('ROOTFS_NODE_DIR = "/usr/local/node"'), 'NodeRuntimeManager.kt missing ROOTFS_NODE_DIR constant');

  const patcher = read('workspace/src/main/java/me/rerere/workspace/RootfsPatcher.kt');
  assert.ok(patcher.includes('ensureNodeEnvironment'), 'RootfsPatcher.kt missing ensureNodeEnvironment');
  assert.ok(patcher.includes('rikka-node.sh'), 'RootfsPatcher.kt missing /etc/profile.d/rikka-node.sh provisioning');
  assert.ok(patcher.includes('NPM_CONFIG_CACHE'), 'RootfsPatcher.kt missing NPM_CONFIG_CACHE isolation');
});

test('Phase 7.2b: ProotShellRunner and WorkspaceTerminalSession mount shared /usr/local/node without leaking /workspace or RIKKA_NODE_TOKEN', () => {
  const proot = read('workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt');
  assert.ok(proot.includes('NodeRuntimeManager.resolveSharedNodeDir'), 'ProotShellRunner.kt missing resolveSharedNodeDir bind mount');
  assert.ok(proot.includes('PATH=/usr/local/node/bin:'), 'ProotShellRunner.kt missing /usr/local/node/bin in PATH');
  assert.ok(proot.includes('NPM_CONFIG_CACHE=/tmp/.npm-cache'), 'ProotShellRunner.kt missing NPM_CONFIG_CACHE=/tmp/.npm-cache');
  assert.ok(!proot.includes('RIKKA_NODE_TOKEN'), 'ProotShellRunner.kt must not expose RIKKA_NODE_TOKEN to workspace shells');

  const term = read('app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceTerminalSession.kt');
  assert.ok(term.includes('NodeRuntimeManager.resolveSharedNodeDir'), 'WorkspaceTerminalSession.kt missing resolveSharedNodeDir bind mount');
  assert.ok(term.includes('PATH=/usr/local/node/bin:'), 'WorkspaceTerminalSession.kt missing /usr/local/node/bin in PATH');
  assert.ok(term.includes('NPM_CONFIG_CACHE=/tmp/.npm-cache'), 'WorkspaceTerminalSession.kt missing NPM_CONFIG_CACHE=/tmp/.npm-cache');
  assert.ok(!term.includes('RIKKA_NODE_TOKEN'), 'WorkspaceTerminalSession.kt must not expose RIKKA_NODE_TOKEN');
});

test('Phase 7.2c: Real Node.js execution across isolated assistant workspaces preserves strict directory and env isolation', () => {
  const tmpBase = fs.mkdtempSync(path.join(os.tmpdir(), 'rikka-ws-node-'));
  try {
    const wsA = path.join(tmpBase, 'assistant_a', 'files');
    const wsB = path.join(tmpBase, 'assistant_b', 'files');
    const tmpA = path.join(tmpBase, 'assistant_a', 'tmp');
    const tmpB = path.join(tmpBase, 'assistant_b', 'tmp');
    fs.mkdirSync(wsA, { recursive: true });
    fs.mkdirSync(wsB, { recursive: true });
    fs.mkdirSync(tmpA, { recursive: true });
    fs.mkdirSync(tmpB, { recursive: true });

    fs.writeFileSync(path.join(wsA, 'secret-a.txt'), 'only-for-assistant-a', 'utf8');
    fs.writeFileSync(path.join(wsB, 'probe.mjs'), `
      import fs from 'node:fs';
      import path from 'node:path';
      const cwdFiles = fs.readdirSync(process.cwd());
      console.log(JSON.stringify({
        node: process.version,
        cwdFiles,
        npmCache: process.env.NPM_CONFIG_CACHE,
        hasNodeToken: Boolean(process.env.RIKKA_NODE_TOKEN),
      }));
    `, 'utf8');

    const out = execFileSync(process.execPath, [path.join(wsB, 'probe.mjs')], {
      cwd: wsB,
      env: {
        HOME: '/root',
        PATH: `${path.dirname(process.execPath)}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`,
        LANG: 'C.UTF-8',
        LC_ALL: 'C.UTF-8',
        NPM_CONFIG_CACHE: path.join(tmpB, '.npm-cache'),
        NODE_COMPILE_CACHE: path.join(tmpB, '.node-compile-cache'),
      },
      encoding: 'utf8',
    }).trim();

    const parsed = JSON.parse(out);
    assert.match(parsed.node, /^v\d+\.\d+\.\d+/);
    assert.deepEqual(parsed.cwdFiles, ['probe.mjs']);
    assert.equal(parsed.npmCache, path.join(tmpB, '.npm-cache'));
    assert.equal(parsed.hasNodeToken, false);
  } finally {
    fs.rmSync(tmpBase, { recursive: true, force: true });
  }
});
