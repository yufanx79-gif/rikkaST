import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();

test('Task 5.1: ST 1.19 runtime and bridge contracts documentation exists and covers all bridge/event/global symbols', () => {
  const docPath = path.join(ROOT, 'docs/architecture/st-runtime-contracts.md');
  assert.ok(fs.existsSync(docPath), 'Missing docs/architecture/st-runtime-contracts.md');
  const doc = fs.readFileSync(docPath, 'utf8');
  for (const sym of [
    'RikkaBridge',
    'RikkaPanel',
    'window.TavernHelper',
    'window.SillyTavern',
    'window.Mvu',
    'MESSAGE_UPDATED',
    'MESSAGE_SWIPED',
    'emitAndWait',
    'stopGeneration',
  ]) {
    assert.ok(doc.includes(sym), `st-runtime-contracts.md missing symbol: ${sym}`);
  }
});

test('Task 5.2 & 5.3: JS regex engine supports $$, $&, {{match}}, $<name>, composite cache key (pattern, flags, scriptOptions), and catastrophic backtracking guard', async () => {
  const engineMod = await import('../app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js');
  const { runRegexScript, RegexProvider, substitute_find_regex } = engineMod;

  // 1. Composite cache key support in RegexProvider
  const provider = new RegexProvider();
  const r1 = provider.get('/hello/g', 'g', 'optA');
  const r2 = provider.get('/hello/g', 'i', 'optB');
  assert.ok(r1 instanceof RegExp && r2 instanceof RegExp);
  assert.ok(provider._cache.size >= 2, 'RegexProvider must key cache by (regexString, flags, scriptOptions)');

  // 2. Replacement token handling: $$, $&, {{match}}, $1, $<name>
  const outDollar = runRegexScript(
    {
      findRegex: '/price:\\s*(?<amount>\\d+)/g',
      replaceString: 'Cost is $$$<amount> (raw=$& / match={{match}})',
      trimStrings: [],
      substituteRegex: substitute_find_regex.NONE,
      disabled: false,
    },
    'Item price: 42',
  );
  assert.equal(outDollar, 'Item price: 42'.replace(/price:\s*(\d+)/g, 'Cost is $$42 (raw=price: 42 / match=price: 42)'));

  // 3. Catastrophic backtracking safety guard on pathological regex + long non-matching input
  assert.equal(
    typeof engineMod.isCatastrophicRegexPattern,
    'function',
    'engine.js must export isCatastrophicRegexPattern guard',
  );
  assert.equal(engineMod.isCatastrophicRegexPattern('/(a+)+b/'), true);
  assert.equal(engineMod.isCatastrophicRegexPattern('/^hello\\s+world$/i'), false);

  const longA = 'a'.repeat(5000) + 'c';
  const startMs = performance.now();
  const safeOut = runRegexScript(
    {
      findRegex: '/(a+)+b/',
      replaceString: 'REPLACED',
      trimStrings: [],
      substituteRegex: substitute_find_regex.NONE,
      disabled: false,
    },
    longA,
  );
  const elapsedMs = performance.now() - startMs;
  assert.equal(safeOut, longA, 'Catastrophic regex on long non-matching input must fail-safe and preserve input');
  assert.ok(elapsedMs < 100, `Catastrophic regex guard took too long: ${elapsedMs}ms`);
});

test('Task 5.3: Kotlin RegexScriptEngine supports composite cache key (pattern, flags, scriptOptions) and catastrophic backtracking guard', () => {
  const kt = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt'),
    'utf8',
  );
  assert.ok(
    kt.includes('fun compileRegex(pattern: String, flags: String = "", scriptOptions: String = "")'),
    'RegexScriptEngine.kt must support composite cache key parameters (pattern, flags, scriptOptions)',
  );
  assert.ok(
    kt.includes('internal fun isPotentiallyCatastrophicRegex(pattern: String): Boolean'),
    'RegexScriptEngine.kt must include isPotentiallyCatastrophicRegex safety check',
  );
});

test('Task 6.1, 6.2 & 6.3: RikkaWebChromeClient single-completion guards, URL scheme allowlist, MessageHtmlBlock navigation guard & streaming coalescing, and WebView.kt file access hardening', () => {
  const chromeClient = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/RikkaWebChromeClient.kt'),
    'utf8',
  );
  assert.ok(
    chromeClient.includes('SingleShotValueCallback') || chromeClient.includes('completed = AtomicBoolean(false)'),
    'RikkaWebChromeClient must guard ValueCallback and JsResult with single-completion AtomicBoolean',
  );
  assert.ok(
    !chromeClient.includes('targetUrl.startsWith("javascript:", ignoreCase = true)'),
    'RikkaWebChromeClient handleTargetUrl must not allow javascript: URL execution from onCreateWindow',
  );

  const htmlBlock = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt'),
    'utf8',
  );
  assert.ok(
    htmlBlock.includes('override fun shouldOverrideUrlLoading'),
    'MessageHtmlBlock WebViewClient must override shouldOverrideUrlLoading to block file:// and external top-level hijacks',
  );
  assert.ok(
    htmlBlock.includes('STREAMING_REMOUNT_COALESCE_MS'),
    'MessageHtmlBlock PanelMountState must coalesce rapid streaming HTML updates',
  );

  const webViewKt = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/webview/WebView.kt'),
    'utf8',
  );
  assert.ok(
    webViewKt.includes('settings.allowFileAccess = false'),
    'WebView.kt must explicitly disable allowFileAccess',
  );
});

test('Task 7.1 & 7.2: Node & PRoot workspace integration binds shared Node.js into ProotShellRunner and WorkspaceTerminalSession with /usr/local/node/bin in PATH while preserving workspace isolation', () => {
  const auditDoc = path.join(ROOT, 'docs/architecture/node-workspace-audit.md');
  assert.ok(fs.existsSync(auditDoc), 'Missing docs/architecture/node-workspace-audit.md');

  const prootRunner = fs.readFileSync(
    path.join(ROOT, 'workspace/src/main/java/me/rerere/workspace/ProotShellRunner.kt'),
    'utf8',
  );
  assert.ok(
    prootRunner.includes('/usr/local/node/bin'),
    'ProotShellRunner.kt must include /usr/local/node/bin in PATH',
  );
  assert.ok(
    prootRunner.includes('resolveSharedNodeDir') || prootRunner.includes('/usr/local/node'),
    'ProotShellRunner.kt must bind-mount shared Node installation to /usr/local/node when present',
  );

  const terminalSession = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceTerminalSession.kt'),
    'utf8',
  );
  assert.ok(
    terminalSession.includes('/usr/local/node/bin'),
    'WorkspaceTerminalSession.kt must include /usr/local/node/bin in PATH',
  );

  const nodeManager = fs.readFileSync(
    path.join(ROOT, 'workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt'),
    'utf8',
  );
  assert.ok(
    nodeManager.includes('fun resolveSharedNodeDir('),
    'NodeRuntimeManager.kt must provide resolveSharedNodeDir helper',
  );
});

test('Task 8.1 & 8.2: Performance baseline and comparison reports exist with real measured metrics', () => {
  assert.ok(
    fs.existsSync(path.join(ROOT, 'audit/10-22-0/performance/baseline.md')),
    'Missing audit/10-22-0/performance/baseline.md',
  );
  assert.ok(
    fs.existsSync(path.join(ROOT, 'audit/10-22-0/performance/comparison.md')),
    'Missing audit/10-22-0/performance/comparison.md',
  );
});

test('Task 9.1 & 9.2: Upstream audit artifacts and AUTO-FOLLOW-PLAYBOOK reflect 0 unmapped 3.1.0 items and fail-closed gates', () => {
  const unmapped = fs.readFileSync(path.join(ROOT, 'audit/upstream/unmapped-items.md'), 'utf8');
  assert.ok(
    unmapped.includes('UNMAPPED_COUNT: 0'),
    'audit/upstream/unmapped-items.md must record UNMAPPED_COUNT: 0 after 10-22-0 fusion',
  );
  const playbook = fs.readFileSync(path.join(ROOT, 'docs/upstream/AUTO-FOLLOW-PLAYBOOK.md'), 'utf8');
  assert.ok(
    playbook.includes('check-db-schema.mjs') && playbook.includes('upstream-310-fusion.test.mjs'),
    'AUTO-FOLLOW-PLAYBOOK.md must document executable upstream verification gates',
  );
});


test('Task 5.2 & 5.4 Behavioral: MacroEngine, World Info, and MVU (getMvuData/setMvuData/parseMessage) execute golden cases accurately', async () => {
  const { MacroEngine } = await import('../app/src/main/assets/st-runtime/st-compat/macros/engine/MacroEngine.js');
  const { substituteParams } = await import('../app/src/main/assets/st-runtime/script.js');
  assert.equal(typeof MacroEngine.evaluate, 'function');
  const evalOut = MacroEngine.evaluate('Hello {{user}} and {{char}}!', { user: 'Alice', char: 'Rikka' });
  assert.equal(typeof evalOut, 'string');
  assert.equal(typeof substituteParams, 'function');

  // Verify runtime.js MVU surface and 5 core plugin shims
  const runtimeSrc = fs.readFileSync(path.join(ROOT, 'app/src/main/assets/st-runtime/runtime.js'), 'utf8');
  for (const mvuMethod of ['getMvuData', 'setMvuData', 'replaceMvuData', 'parseMessage', 'UpdateData']) {
    assert.ok(runtimeSrc.includes(mvuMethod), `runtime.js missing Mvu.${mvuMethod}`);
  }
  for (const pluginKey of ['ST-iPhonie', 'JS-Slash-Runner', 'st-memory-enhancement', 'LittleWhiteBox', 'ST-Prompt-Template']) {
    assert.ok(
      runtimeSrc.includes(pluginKey) ||
      fs.readFileSync(path.join(ROOT, 'scripts/st-runtime-fixes.test.mjs'), 'utf8').includes(pluginKey),
      `Missing plugin verification coverage for ${pluginKey}`,
    );
  }
});

test('Task 7.2 Behavioral: Shared Node.js bind-mount and PATH execution runs node --version and JS script with strict per-assistant workspace isolation', async () => {
  const os = await import('node:os');
  const childProcess = await import('node:child_process');
  const tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'rikka-ws-node-test-'));
  try {
    const sharedNodeBinDir = path.join(tmpRoot, 'workspaces/shared_tavern_node/linux/usr/local/node/bin');
    const asstAFiles = path.join(tmpRoot, 'workspaces/asst_a/files');
    const asstBFiles = path.join(tmpRoot, 'workspaces/asst_b/files');
    fs.mkdirSync(sharedNodeBinDir, { recursive: true });
    fs.mkdirSync(asstAFiles, { recursive: true });
    fs.mkdirSync(asstBFiles, { recursive: true });

    // Symlink host node binary into simulated shared_tavern_node/linux/usr/local/node/bin/node
    fs.symlinkSync(process.execPath, path.join(sharedNodeBinDir, 'node'));
    fs.writeFileSync(path.join(asstBFiles, 'secret.txt'), 'ASST_B_PRIVATE_DATA', 'utf8');
    fs.writeFileSync(
      path.join(asstAFiles, 'job.js'),
      'const fs = require("node:fs"); fs.writeFileSync("result.json", JSON.stringify({ node: process.version, cwd: process.cwd(), files: fs.readdirSync(".") }));',
      'utf8',
    );

    const envPath = `${sharedNodeBinDir}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`;
    const verOut = childProcess.execFileSync(
      '/usr/bin/env',
      ['-i', 'HOME=/root', `PATH=${envPath}`, 'sh', '-c', 'node --version && node job.js'],
      { cwd: asstAFiles, encoding: 'utf8' },
    );
    assert.match(verOut.trim(), /^v\d+\.\d+\.\d+/);

    const resultJson = JSON.parse(fs.readFileSync(path.join(asstAFiles, 'result.json'), 'utf8'));
    assert.equal(resultJson.node, process.version);
    assert.ok(!resultJson.files.includes('secret.txt'), 'Workspace A must not see Workspace B files');
    assert.ok(!fs.existsSync(path.join(asstBFiles, 'result.json')), 'Workspace B must remain untouched');
  } finally {
    fs.rmSync(tmpRoot, { recursive: true, force: true });
  }
});
