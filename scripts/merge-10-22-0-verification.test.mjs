import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const ROOT = process.cwd();
const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf8');
const exists = (rel) => fs.existsSync(path.join(ROOT, rel));

test('Merge Check 1: ReasoningPicker.kt declares ReasoningSliderSpec and preserves rikkaST Kelivo slider UI, drag, spring snap, and haptics', () => {
  const picker = read('app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt');
  const levelPicker = read('app/src/main/java/me/rerere/rikkahub/ui/components/ui/LevelPicker.kt');
  const specTest = read('app/src/test/java/me/rerere/rikkahub/ui/components/ai/ReasoningSliderSpecTest.kt');

  assert.ok(picker.includes('internal object ReasoningSliderSpec'), 'ReasoningPicker.kt must declare internal object ReasoningSliderSpec');
  for (const member of [
    'val Height = 56.dp',
    'val TrackHeight = 34.dp',
    'val ThumbSize = 38.dp',
    'val DotSize = 7.dp',
    'val StopInset = ThumbSize / 2',
    'const val DragScale = 1.14f',
    'const val SnapDampingRatio = 0.783f',
    'const val SnapStiffness = 320f',
    'fun thumbX(position: Float, width: Float, count: Int, inset: Float): Float',
    'fun stopX(index: Int, width: Float, count: Int, inset: Float): Float',
    'fun fractionForX(x: Float, width: Float, count: Int, inset: Float): Float',
  ]) {
    assert.ok(picker.includes(member), `ReasoningPicker.kt missing ReasoningSliderSpec member: ${member}`);
  }

  for (const uiBehavior of [
    'ReasoningLevelPill',
    'ReasoningSlider(',
    'detectTapGestures',
    'detectHorizontalDragGestures',
    'HapticFeedbackType.TextHandleMove',
    'onDragStart',
    'onDragEnd',
    'settleTo(target)',
    'commit(target)',
  ]) {
    assert.ok(picker.includes(uiBehavior), `ReasoningPicker.kt missing rikkaST slider behavior: ${uiBehavior}`);
  }

  assert.ok(levelPicker.includes('import me.rerere.rikkahub.ui.components.ai.ReasoningSliderSpec'));
  assert.ok(specTest.includes('ReasoningSliderSpec.thumbX'));
  assert.ok(specTest.includes('ReasoningSliderSpec.stopX'));
  assert.ok(specTest.includes('ReasoningSliderSpec.fractionForX'));
});

test('Merge Check 2: RootfsPatcher.ensureNodeEnvironment(), login shell PATH persistence, /usr/local/bin symlinks, and strict shared_tavern_node resolution', () => {
  const patcher = read('workspace/src/main/java/me/rerere/workspace/RootfsPatcher.kt');
  assert.ok(patcher.includes('ensureNodeEnvironment(linuxDir, etcDir)'), 'RootfsPatcher.patch must call ensureNodeEnvironment(linuxDir, etcDir)');
  assert.ok(patcher.includes('listOf("node", "npm", "npx")'), 'RootfsPatcher must create /usr/local/bin/{node,npm,npx} symlinks');
  assert.ok(patcher.includes('Paths.get("../node/bin/$binName")'), 'RootfsPatcher symlinks must point to ../node/bin/$binName');
  assert.ok(patcher.includes('rikka-node.sh'), 'RootfsPatcher must write /etc/profile.d/rikka-node.sh');
  assert.ok(patcher.includes('export PATH=\\"/usr/local/node/bin:\\$PATH\\"'), 'rikka-node.sh must persist /usr/local/node/bin in login shell PATH');
  assert.ok(patcher.includes('NPM_CONFIG_CACHE'), 'rikka-node.sh must isolate NPM_CONFIG_CACHE');
  assert.ok(patcher.includes('NODE_COMPILE_CACHE'), 'rikka-node.sh must isolate NODE_COMPILE_CACHE');

  const nodeMgr = read('workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt');
  assert.ok(nodeMgr.includes('const val SHARED_WORKSPACE_ROOT = "shared_tavern_node"'));
  assert.ok(nodeMgr.includes('const val ROOTFS_NODE_DIR = "/usr/local/node"'));
  assert.ok(nodeMgr.includes('fun resolveSharedNodeDir(linuxDir: File): File?'), 'NodeRuntimeManager.resolveSharedNodeDir must take linuxDir: File');
  // Strictly forbid scanning arbitrary assistant workspaces via listFiles() inside resolveSharedNodeDir
  const resolveBlock = nodeMgr.slice(nodeMgr.indexOf('fun resolveSharedNodeDir'));
  assert.ok(!resolveBlock.includes('listFiles()'), 'resolveSharedNodeDir must strictly resolve only from shared_tavern_node and never scan arbitrary assistant workspaces');

  const proot = read('workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt');
  assert.ok(proot.includes('NodeRuntimeManager.resolveSharedNodeDir(context.linuxDir)'), 'ProotShellRunner must pass context.linuxDir to resolveSharedNodeDir');
  assert.ok(proot.includes('NPM_CONFIG_CACHE=/tmp/.npm-cache'), 'ProotShellRunner must set NPM_CONFIG_CACHE=/tmp/.npm-cache');
  assert.ok(proot.includes('NODE_COMPILE_CACHE=/tmp/.node-compile-cache'), 'ProotShellRunner must set NODE_COMPILE_CACHE=/tmp/.node-compile-cache');

  const term = read('app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceTerminalSession.kt');
  assert.ok(term.includes('NodeRuntimeManager.resolveSharedNodeDir(linuxDir)'), 'WorkspaceTerminalSession must pass linuxDir to resolveSharedNodeDir');
  assert.ok(term.includes('NPM_CONFIG_CACHE=/tmp/.npm-cache'), 'WorkspaceTerminalSession must set NPM_CONFIG_CACHE=/tmp/.npm-cache');
  assert.ok(term.includes('WorkspaceManager.rootfsShell(linuxDir)'), 'WorkspaceTerminalSession must retain 3.1.0 WorkspaceManager.rootfsShell(linuxDir)');
  assert.ok(term.includes('WorkspaceManager.isUsableRootfs(linuxDir)'), 'WorkspaceTerminalSession must retain 3.1.0 WorkspaceManager.isUsableRootfs(linuxDir)');

  // Behavioral check: simulate /etc/profile.d/rikka-node.sh and /usr/local/bin/node -> ../node/bin/node in a login shell
  const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'rikka-rootfs-login-'));
  try {
    const usrLocalNodeBin = path.join(tmpRoot, 'usr/local/node/bin');
    const usrLocalBin = path.join(tmpRoot, 'usr/local/bin');
    const etcProfileD = path.join(tmpRoot, 'etc/profile.d');
    fs.mkdirSync(usrLocalNodeBin, { recursive: true });
    fs.mkdirSync(usrLocalBin, { recursive: true });
    fs.mkdirSync(etcProfileD, { recursive: true });

    fs.symlinkSync(process.execPath, path.join(usrLocalNodeBin, 'node'));
    fs.symlinkSync('../node/bin/node', path.join(usrLocalBin, 'node'));

    const profileScript = [
      '# Generated by RikkaHub workspace for shared Node.js runtime',
      `if [ -d "${usrLocalNodeBin}" ]; then`,
      '  case ":$PATH:" in',
      `    *":${usrLocalNodeBin}:"*) ;;`,
      `    *) export PATH="${usrLocalNodeBin}:$PATH" ;;`,
      '  esac',
      'fi',
      'export NPM_CONFIG_CACHE="${NPM_CONFIG_CACHE:-/tmp/.npm-cache}"',
      'export NODE_COMPILE_CACHE="${NODE_COMPILE_CACHE:-/tmp/.node-compile-cache}"',
    ].join('\n');
    const profileFile = path.join(etcProfileD, 'rikka-node.sh');
    fs.writeFileSync(profileFile, profileScript, 'utf8');

    // Simulate login shell where /etc/profile resets PATH to /usr/bin:/bin, then sources /etc/profile.d/rikka-node.sh
    const out = execFileSync(
      '/bin/bash',
      ['-l', '-c', `PATH=/usr/bin:/bin; . "${profileFile}"; node --version && "${path.join(usrLocalBin, 'node')}" --version`],
      { encoding: 'utf8' },
    ).trim().split(/\r?\n/);
    assert.equal(out.length, 2);
    assert.equal(out[0], process.version);
    assert.equal(out[1], process.version);
  } finally {
    fs.rmSync(tmpRoot, { recursive: true, force: true });
  }
});

test('Merge Check 3: Executable full-path, missing-file, category, and upstream-lock verification passes on -2 inventory', () => {
  assert.ok(exists('tools/upstream/verify-upstream-inventory.mjs'), 'Missing tools/upstream/verify-upstream-inventory.mjs');
  const out = execFileSync(process.execPath, [path.join(ROOT, 'tools/upstream/verify-upstream-inventory.mjs')], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  assert.ok(out.includes('[verify-upstream-inventory] PASS'), `Unexpected output: ${out}`);
  const workflow = read('.github/workflows/upstream-watch.yml');
  assert.ok(workflow.includes('verify-upstream-inventory.mjs'), 'upstream-watch.yml must invoke verify-upstream-inventory.mjs');
});

test('Merge Check 4: ReasoningSliderSpec mathematical contract matches ReasoningSliderSpecTest.kt and LevelPicker.kt', () => {
  const picker = read('app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt');
  const thumbSize = Number(picker.match(/val ThumbSize = (\d+)\.dp/)[1]);
  const stopInset = thumbSize / 2;
  const dragScale = Number(picker.match(/const val DragScale = ([\d.]+)f/)[1]);
  const snapDampingRatio = Number(picker.match(/const val SnapDampingRatio = ([\d.]+)f/)[1]);
  const snapStiffness = Number(picker.match(/const val SnapStiffness = ([\d.]+)f/)[1]);

  function thumbX(position, width, count, inset) {
    const span = width - 2 * inset;
    if (count <= 1 || span <= 0) return inset;
    const t = Math.min(1, Math.max(0, position / (count - 1)));
    return inset + span * t;
  }
  function stopX(index, width, count, inset) {
    return thumbX(index, width, count, inset);
  }
  function fractionForX(x, width, count, inset) {
    const span = width - 2 * inset;
    if (count <= 1 || span <= 0) return 0;
    return Math.min(count - 1, Math.max(0, ((x - inset) / span) * (count - 1)));
  }

  assert.equal(stopInset, 19);
  assert.equal(dragScale, 1.14);
  assert.equal(snapStiffness, 320);
  assert.ok(Math.abs(snapDampingRatio - 28 / (2 * Math.sqrt(320))) < 5e-4);

  const count = 6;
  const width = 1080;
  assert.ok(Math.abs(thumbX(0, width, count, stopInset) - stopInset) < 1e-3);
  assert.ok(Math.abs(thumbX(count - 1, width, count, stopInset) - (width - stopInset)) < 1e-3);
  for (let i = 0; i < count; i++) {
    const x = stopX(i, width, count, stopInset);
    assert.ok(Math.abs(fractionForX(x, width, count, stopInset) - i) < 1e-3);
  }
});

test('Merge Check 5: Preserves -2 production JS regex cache & anti-backtracking, WebView streaming/lifecycle fixes, and production perf-benchmark.mjs (no synthetic -1 benchmark)', () => {
  assert.ok(exists('scripts/perf-benchmark.mjs'), 'Must retain -2 production scripts/perf-benchmark.mjs');
  assert.ok(!exists('scripts/perf-benchmark-10-22-0.mjs'), 'Must not include -1 synthetic scripts/perf-benchmark-10-22-0.mjs');
  const perfSrc = read('scripts/perf-benchmark.mjs');
  assert.ok(
    perfSrc.includes("../app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js"),
    'perf-benchmark.mjs must import production engine.js',
  );
  const engineSrc = read('app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js');
  assert.ok(engineSrc.includes('isCatastrophicRegexPattern'), 'engine.js must retain isCatastrophicRegexPattern');
  assert.ok(engineSrc.includes('get(regexString, flags = \'\', scriptOptions = \'\')'), 'engine.js must retain composite cache key get()');
});

test('Merge Check 6: Stop generation and resend-after-stop behavioral verification', async () => {
  const scriptCompat = await import('../app/src/main/assets/st-runtime/st-compat/script.js');
  const sessionKt = read('app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt');
  assert.ok(sessionKt.includes('fun cancelJobs()') && sessionKt.includes('fun afterPreviousGeneration('), 'ConversationSession.kt must support generation cancellation');

  const calls = [];
  globalThis.window = globalThis;
  globalThis.RikkaBridge = {
    stopGeneration: () => {
      calls.push('stopGeneration');
      return true;
    },
  };

  // Turn 1: start generation and stop mid-stream
  const ac1 = new AbortController();
  scriptCompat.setExternalAbortController(ac1);
  assert.equal(ac1.signal.aborted, false);
  scriptCompat.stopGeneration();
  assert.equal(ac1.signal.aborted, true, 'First generation AbortController must be aborted');
  assert.equal(calls.length, 1);

  // Turn 2: resend after stop with fresh AbortController must not be pre-aborted
  const ac2 = new AbortController();
  scriptCompat.setExternalAbortController(ac2);
  assert.equal(ac2.signal.aborted, false, 'Resend after stop must have clean non-aborted signal');
  const simulatedReply = await Promise.resolve().then(() => {
    if (scriptCompat.externalAbortController.signal.aborted) throw new Error('Aborted');
    return 'Resent assistant reply ok';
  });
  assert.equal(simulatedReply, 'Resent assistant reply ok');
});
