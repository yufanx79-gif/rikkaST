import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const REPO_ROOT = process.cwd();

function readRepoFile(relPath) {
  return fs.readFileSync(path.join(REPO_ROOT, relPath), 'utf8');
}

/**
 * Runs a regex evaluation in a separate killable Node child process with a strict timeout
 * so a catastrophic regex hang is caught as a deterministic test failure without hanging CI.
 */
function runAdversarialInChildProcess(findRegex, input, timeoutMs = 1500) {
  const enginePath = path.join(REPO_ROOT, 'app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js');
  const code = `
    import(${JSON.stringify('file://' + enginePath)}).then(({ runRegexScript }) => {
      const t0 = Date.now();
      const out = runRegexScript({ findRegex: ${JSON.stringify(findRegex)}, replaceString: 'X', disabled: false, trimStrings: [] }, ${JSON.stringify(input)});
      console.log(JSON.stringify({ ok: true, elapsedMs: Date.now() - t0, unchanged: out === ${JSON.stringify(input)} }));
      process.exit(0);
    });
  `;
  const res = spawnSync(process.execPath, ['--input-type=module', '-e', code], {
    cwd: REPO_ROOT,
    timeout: timeoutMs,
    killSignal: 'SIGKILL',
    encoding: 'utf8',
  });
  if (res.error && res.error.code === 'ETIMEDOUT') {
    return { timedOut: true, status: res.status };
  }
  if (res.status !== 0) {
    return { timedOut: false, status: res.status, stderr: res.stderr };
  }
  const lines = (res.stdout || '').trim().split('\n');
  const parsed = JSON.parse(lines[lines.length - 1]);
  return { timedOut: false, status: 0, ...parsed };
}

test('Task E (F-05 & Domain A): Killable adversarial ReDoS tests below & above 4096 chars (including /(a+){30}b/g, /(a+){6}b/g, /(a{2,8}){30}b/g, /(\\w+)+b/g, /(x?a|a)+b/g), out-of-range $N group safety, and Kotlin BoundedRegexCharSequence parity', async () => {
  const engineMod = await import(path.join(REPO_ROOT, 'app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js'));
  const utilsMod = await import(path.join(REPO_ROOT, 'app/src/main/assets/st-runtime/st-compat/utils.js'));
  const { runRegexScript, regex_placement, RegexProvider, stripUnmatchedBoundaryWrappers, isCatastrophicRegexPattern } = engineMod;

  // 1. Verify utils.js & engine.js exports
  assert.equal(typeof utilsMod.replaceCompatString, 'function', 'utils.js must export replaceCompatString');
  assert.equal(typeof utilsMod.extractInlineRegexFlags, 'function', 'utils.js must export extractInlineRegexFlags');
  assert.equal(typeof utilsMod.isLikelyCatastrophicRegex, 'function', 'utils.js must export isLikelyCatastrophicRegex');
  assert.equal(typeof utilsMod.isCatastrophicRegexPattern, 'function', 'utils.js must export isCatastrophicRegexPattern');
  assert.equal(typeof stripUnmatchedBoundaryWrappers, 'function', 'engine.js must export stripUnmatchedBoundaryWrappers');

  // 2. Out-of-range numbered group tokens ($1/$2 on 0-group regex, $2 on 1-group regex) MUST resolve to '' (not offset or inputString)
  const zeroGroupScript = {
    scriptName: 'zero-group-out-of-range',
    findRegex: '/hello/g',
    replaceString: '[$1][$2]',
    trimStrings: [],
    placement: [regex_placement.AI_OUTPUT],
    disabled: false,
  };
  assert.equal(
    runRegexScript(zeroGroupScript, 'say hello world'),
    'say [][] world',
    'Out-of-range $1/$2 on 0-group regex must resolve to empty string, not match offset or input string'
  );
  assert.equal(
    utilsMod.replaceCompatString('say hello world', '/hello/g', '[$1][$&]'),
    'say [][hello] world',
    'utils.replaceCompatString must accept string pattern and resolve out-of-range $1 to empty string'
  );

  // 3. Static detector accuracy: must catch exact quantifier /(a+){30}b/, moderate quantifier /(a+){6}b/, bounded inner /(a{2,8}){30}b/, escaped /(\w+)+b/, and optional-prefix alternation /(x?a|a)+b/, while NOT false-positiving fixed UUID/date patterns
  for (const dangerous of [
    '/(a+){30}b/g',
    '/(a+){6}b/g',
    '/(a{2,8}){30}b/g',
    '/(a+)+b/g',
    '/(\\w+)+b/g',
    '/((a+))+b/g',
    '/(?:a|(?:a+))+b/g',
    '/(a|aa)+b/g',
    '/(x?a|a)+b/g',
    '/([a-z]+|[a-z0-9]+)+!/g',
  ]) {
    assert.equal(isCatastrophicRegexPattern(dangerous), true, `Expected isCatastrophicRegexPattern(${dangerous}) to be true`);
  }
  assert.equal(
    isCatastrophicRegexPattern('/^(\\d{4}-){3}\\d{4}$/'),
    false,
    'Fixed-length non-ambiguous group /^(\\d{4}-){3}\\d{4}$/ must not be flagged as catastrophic'
  );

  // 4. Killable child-process ReDoS tests on SHORT inputs (< 4096 chars, e.g. 34-38 chars) and LONG inputs (> 4096 chars)
  const adversarialCases = [
    { name: 'exact quantifier (a+){30}b <4096 chars', pattern: '/(a+){30}b/g', input: 'a'.repeat(58) + 'c' },
    { name: 'moderate exact quantifier (a+){6}b <4096 chars', pattern: '/(a+){6}b/g', input: 'a'.repeat(38) + 'c' },
    { name: 'bounded inner+outer (a{2,8}){30}b <4096 chars', pattern: '/(a{2,8}){30}b/g', input: 'a'.repeat(90) + 'c' },
    { name: 'escaped word quantifier (\\w+)+b <4096 chars', pattern: '/(\\w+)+b/g', input: 'a'.repeat(34) + '!' },
    { name: 'flat nested quantifier <4096 chars', pattern: '/(a+)+b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'deeply nested group ((a+))+b <4096 chars', pattern: '/((a+))+b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'nested non-capturing (?:a|(?:a+))+b <4096 chars', pattern: '/(?:a|(?:a+))+b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'overlapping alternation (a|aa)+b <4096 chars', pattern: '/(a|aa)+b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'optional-prefix alternation (x?a|a)+b <4096 chars', pattern: '/(x?a|a)+b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'overlapping class alternation ([a-z]+|[a-z0-9]+)+! <4096 chars', pattern: '/([a-z]+|[a-z0-9]+)+!/g', input: 'a'.repeat(34) + '?' },
    { name: 'large bounded quantifier (a+){1,50}b <4096 chars', pattern: '/(a+){1,50}b/g', input: 'a'.repeat(34) + 'c' },
    { name: 'long input >4096 chars', pattern: '/(a+)+b/g', input: 'a'.repeat(5000) + 'c' },
  ];

  for (const tc of adversarialCases) {
    const res = runAdversarialInChildProcess(tc.pattern, tc.input, 1500);
    assert.equal(res.timedOut, false, `Adversarial regex [${tc.name}] (${tc.pattern}) hung and timed out!`);
    assert.equal(res.status, 0, `Adversarial regex [${tc.name}] failed: ${res.stderr || ''}`);
    assert.equal(res.unchanged, true, `Adversarial regex [${tc.name}] must safely preserve input`);
    assert.ok(res.elapsedMs < 200, `Adversarial regex [${tc.name}] took ${res.elapsedMs}ms (>=200ms)`);
  }

  // 5. CRLF normalization + multiple <style> blocks + post-card static HTML preservation
  const multiStyleInput =
    '〔\r\n<style>.a{color:red;}</style>\r\n<style>.post-card{padding:8px;}</style>\r\n<div class="post-card">Hello World</div>\r\n〕';
  const wrapperScript = {
    scriptName: 'strip-wrappers',
    findRegex: '/^〔\\n([\\s\\S]*?)\\n〕$/m',
    replaceString: '$1',
    trimStrings: [],
    placement: [regex_placement.AI_OUTPUT],
    disabled: false,
  };
  const out1 = runRegexScript(wrapperScript, multiStyleInput);
  assert.ok(!out1.includes('\r'), 'CRLF must be normalized to LF');
  assert.ok(!out1.startsWith('〔') && !out1.endsWith('〕'), 'Wrapper brackets must be stripped after CRLF normalization');
  assert.equal((out1.match(/<style>/g) || []).length, 2, 'Both <style> blocks must be preserved');
  assert.ok(out1.includes('<div class="post-card">Hello World</div>'), 'post-card block must be preserved');

  // 6. Replacement tokens: $$, $&, {{match}}, $0, $1, $<named>, and trimStrings (via both runRegexScript and utils.replaceCompatString)
  const captureScript = {
    scriptName: 'captures-and-trim',
    findRegex: '/\\[tag:(?<kind>[a-z]+)\\](.*?)\\[\\/tag\\]/gi',
    replaceString: 'Price=$$10 | Full={{match}} | Amp=$& | Zero=$0 | Kind=$<kind> | Body=$1-$2',
    trimStrings: ['REMOVE_ME'],
    placement: [regex_placement.AI_OUTPUT],
    disabled: false,
  };
  const out2 = runRegexScript(captureScript, '[tag:info]Hello REMOVE_MEWorld[/tag]');
  assert.ok(out2.includes('Price=$10'), '$$ must produce literal $');
  assert.ok(out2.includes('Kind=info'), '$<kind> named capture must resolve');
  assert.ok(out2.includes('Body=info-Hello World'), '$1/$2 and trimStrings must remove REMOVE_ME');
  assert.ok(!out2.includes('REMOVE_ME'), 'trimStrings must be stripped from all group/match insertions');

  const directCompat = utilsMod.replaceCompatString(
    '[tag:info]Hello REMOVE_MEWorld[/tag]',
    /\[tag:(?<kind>[a-z]+)\](.*?)\[\/tag\]/gi,
    'Price=$$10 | Full={{match}} | Amp=$& | Zero=$0 | Kind=$<kind> | Body=$1-$2',
    ['REMOVE_ME']
  );
  assert.equal(directCompat, out2, 'utils.replaceCompatString must match engine.runRegexScript output');

  // 7. Inline PCRE flags (?i), (?s), (?m) in both un-delimited and slash-delimited findRegex
  for (const pat of ['(?is)<think>.*?</think>\\s*', '/(?is)<think>.*?</think>\\s*/g']) {
    const inlineFlagScript = {
      scriptName: 'inline-dotall-ignorecase',
      findRegex: pat,
      replaceString: '',
      trimStrings: [],
      placement: [regex_placement.AI_OUTPUT],
      disabled: false,
    };
    const out3 = runRegexScript(inlineFlagScript, '<THINK>\nsecret chain of thought\n</THINK>\nVisible reply');
    assert.equal(out3, 'Visible reply', `Engine must support leading inline (?is) flags in ${pat}`);
  }

  // 8. Explicit flags parameter in RegexProvider.get(pattern, flags, scriptOptions)
  const rxWithExplicitFlags = RegexProvider.instance.get('^hello$', 'im', 'opt1');
  assert.ok(rxWithExplicitFlags instanceof RegExp, 'RegexProvider.get must compile pattern with explicit flags');
  assert.equal(rxWithExplicitFlags.test('foo\nHELLO\nbar'), true, 'Explicit "im" flags must be honored');

  // 9. Unmatched boundary wrapper stripping vs matched inline pair preservation
  assert.equal(stripUnmatchedBoundaryWrappers('〔\nHello world'), 'Hello world');
  assert.equal(stripUnmatchedBoundaryWrappers('Hello world\n〕'), 'Hello world');
  assert.equal(stripUnmatchedBoundaryWrappers('她翻开〔世界史〕笔记本'), '她翻开〔世界史〕笔记本');
  assert.equal(stripUnmatchedBoundaryWrappers('〖Unmatched open'), 'Unmatched open');

  // 10. Verify Kotlin RegexScriptEngine.kt has BoundedRegexCharSequence step/time guard, PCRE inline flag extraction, and stripUnmatchedBoundaryWrappers
  const ktEngine = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt');
  assert.ok(
    ktEngine.includes('class BoundedRegexCharSequence') &&
      ktEngine.includes('MAX_REGEX_CHAR_ACCESS_STEPS = 250_000') &&
      ktEngine.includes('MAX_REGEX_EXECUTION_NANOS = 50_000_000L') &&
      ktEngine.includes('extractInlineRegexFlags') &&
      ktEngine.includes('fun stripUnmatchedBoundaryWrappers('),
    'Kotlin RegexScriptEngine.kt must include BoundedRegexCharSequence step/time guard, extractInlineRegexFlags, and stripUnmatchedBoundaryWrappers'
  );
});
