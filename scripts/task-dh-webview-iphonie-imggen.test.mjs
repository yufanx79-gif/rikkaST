import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const REPO_ROOT = process.cwd();
const EXPLICIT_ALL_NEEDS_DIR = (process.env.ALL_NEEDS_DIR || '').trim();
const DEFAULT_ALL_NEEDS_CANDIDATES = process.env.RIKKA_FORCE_NO_DEFAULT_FIXTURE === '1'
  ? []
  : [
      path.join(REPO_ROOT, 'testdata/all-needs'),
      '/tmp/c_4ce2e51aba38f935/extract_all_needs',
      '/tmp/rikkast_work/extracted_all_needs',
      '/tmp/rikka_work/all_needs',
    ];
const ALL_NEEDS_DIR = EXPLICIT_ALL_NEEDS_DIR || DEFAULT_ALL_NEEDS_CANDIDATES.find((d) => fs.existsSync(d)) || '';

function readRepoFile(relPath) {
  return fs.readFileSync(path.join(REPO_ROOT, relPath), 'utf8');
}

test('Task D: ST-iPhonie floating panel open is idempotent across recompositions and preserves .floating class to prevent 180ms/500ms sheet-closing loop', () => {
  const runtimeJs = readRepoFile('app/src/main/assets/st-runtime/runtime.js');
  const floatingOverlayKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/ui/components/chat/TavernFloatingWindowOverlay.kt');
  const runtimeMgrKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt');

  assert.ok(
    !runtimeJs.includes("dlg.classList.remove('floating');"),
    'openRealStIphoniePanel must not remove .floating class, which triggers ST-iPhonie panelResized() close/reopen loop'
  );

  assert.ok(
    runtimeJs.includes('rikka-iphonie-fullscreen-override') &&
      runtimeJs.includes("if (!forceReopen && existingDlg && (existingDlg.open || existingDlg.hasAttribute('open'))"),
    'openRealStIphoniePanel must guard against re-clicking #sttts-open when #sttts-panel is already open'
  );

  assert.ok(
    runtimeJs.includes("window.__rikkaCurrentFloatingTarget !== 'iphonie'"),
    'settings_updated must not disable/re-enable ST-iPhonie while iphonie floating window is active'
  );

  assert.ok(
    !floatingOverlayKt.includes('update = { container ->\n                        containerHolder[0] = container\n                        TavernRuntimeManager.attachHostWebViewToContainer(container, selectedTarget)\n                    }'),
    'TavernFloatingWindowModal AndroidView.update must not unconditionally re-trigger attachHostWebViewToContainer on every recomposition'
  );
  assert.ok(
    runtimeMgrKt.includes('lastAttachedFloatingTarget'),
    'TavernRuntimeManager.attachHostWebViewToContainer must track lastAttachedFloatingTarget to avoid redundant JS re-open calls'
  );
});

test('Task H & Security (F-03): HTTP proxy bridge blocks SSRF (loopback, RFC1918, link-local, metadata, IPv6, userinfo, redirect-to-private), strips cross-origin auth headers on redirect, caps oversized responses, and preserves legitimate external image/API requests', async () => {
  const runtimeJs = readRepoFile('app/src/main/assets/st-runtime/runtime.js');
  const runtimeMgrKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt');

  // 1. Verify Kotlin TavernRuntimeManager.kt enforces manual redirect checking (instanceFollowRedirects = false),
  // IP/DNS validation (isBlockedProxyInetAddress / validateProxyTargetUrl), cross-origin auth header stripping, and response byte cap
  assert.ok(
    runtimeMgrKt.includes('instanceFollowRedirects = false') &&
      runtimeMgrKt.includes('isBlockedProxyInetAddress') &&
      runtimeMgrKt.includes('validateProxyTargetUrl') &&
      runtimeMgrKt.includes('MAX_PROXY_RESPONSE_BYTES') &&
      runtimeMgrKt.includes('SENSITIVE_REDIRECT_HEADERS'),
    'TavernRuntimeManager.kt must disable automatic redirects, validate resolved InetAddress against loopback/private/link-local/metadata/ULA ranges, strip sensitive headers on cross-origin redirects, and cap response bytes'
  );

  // 2. Extract and execute the actual JS validateProxyTargetUrl from runtime.js
  const startMarker = '/* __RIKKA_PROXY_SECURITY_START__ */';
  const endMarker = '/* __RIKKA_PROXY_SECURITY_END__ */';
  const sIdx = runtimeJs.indexOf(startMarker);
  const eIdx = runtimeJs.indexOf(endMarker);
  assert.ok(sIdx >= 0 && eIdx > sIdx, 'runtime.js must contain __RIKKA_PROXY_SECURITY_START__..END block');
  const securitySlice = runtimeJs.slice(sIdx, eIdx);
  const sandbox = { URL, parseInt, Number, String, Array, Set, RegExp, Object };
  vm.createContext(sandbox);
  vm.runInContext(securitySlice, sandbox);
  assert.equal(typeof sandbox.validateProxyTargetUrl, 'function', 'validateProxyTargetUrl must be defined in runtime.js');

  // Blocked targets: loopback, localhost aliases, RFC1918, link-local/cloud metadata, IPv6 loopback/link-local/ULA/v4-mapped, non-standard IP encodings, credentials in URL, bad schemes
  const blockedUrls = [
    'http://127.0.0.1:8080/api/host/context',
    'http://127.0.1.1/secret',
    'http://localhost:3000/health',
    'https://sub.localhost/admin',
    'http://0.0.0.0:8080/',
    'http://10.0.0.1/internal',
    'http://172.16.0.1/router',
    'http://172.31.255.255/admin',
    'http://192.168.1.1/setup',
    'http://169.254.169.254/latest/meta-data/iam/security-credentials/',
    'http://100.64.0.1/cgnat',
    'http://[::1]:8080/api',
    'http://[0:0:0:0:0:0:0:1]/',
    'http://[::]/',
    'http://[fe80::1]/link-local',
    'http://[fc00::1]/ula',
    'http://[fd12:3456:789a::1]/ula',
    'http://[::ffff:127.0.0.1]:8080/mapped-loopback',
    'http://[::ffff:10.0.0.1]/mapped-private',
    'http://[::ffff:169.254.169.254]/mapped-metadata',
    'http://2130706433/decimal-loopback',
    'http://0177.0.0.1/octal-loopback',
    'http://0x7f.0.0.1/hex-loopback',
    'http://127.1/short-loopback',
    'http://metadata.google.internal/computeMetadata/v1/',
    'http://service.local/api',
    'https://user:secretpass@api.example.com/v1/images',
    'file:///etc/passwd',
    'javascript:alert(1)',
    'data:text/plain;base64,SGVsbG8=',
  ];
  for (const bad of blockedUrls) {
    const check = sandbox.validateProxyTargetUrl(bad);
    assert.equal(check.ok, false, `Expected proxy security validator to REJECT ${bad}, but got ok=true`);
  }

  // Allowed targets: legitimate public HTTPS/HTTP endpoints
  const allowedUrls = [
    'https://api.openai.com/v1/images/generations',
    'https://relay.example.com:8443/v1/models?foo=bar',
    'https://image.novelai.net/ai/generate-image',
    'http://172.32.0.1/public-ip-outside-rfc1918',
    'http://11.0.0.1/public-ip',
  ];
  for (const good of allowedUrls) {
    const check = sandbox.validateProxyTargetUrl(good);
    assert.equal(check.ok, true, `Expected proxy security validator to ALLOW ${good}, but got reason=${check.reason}`);
  }

  // 3. Desktop-only /api/sd/comfy/* must return explicit 501 UNSUPPORTED_DESKTOP_COMFY_EXTENSION (not SSRF to 127.0.0.1:8188)
  assert.ok(
    runtimeJs.includes('UNSUPPORTED_DESKTOP_COMFY_EXTENSION') &&
      !runtimeJs.includes("http://127.0.0.1:8188"),
    'handleComfyProxyFetch must return explicit 501 UNSUPPORTED_DESKTOP_COMFY_EXTENSION rather than forwarding to 127.0.0.1:8188'
  );
});

test('Task H: ST-iPhonie nested menus, sheets, and image generation work with /proxy/, native RikkaHub bridge, and full error/cancel/aspect-ratio coverage', async (t) => {
  const runtimeJs = readRepoFile('app/src/main/assets/st-runtime/runtime.js');
  const runtimeMgrKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt');
  const imgGenVmKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/ui/pages/imggen/ImgGenVM.kt');

  assert.ok(
    runtimeJs.includes("p.indexOf('/proxy/') === 0") &&
      runtimeJs.includes('handleCorsProxyFetch') &&
      runtimeJs.includes('handleNativeImageGeneration') &&
      runtimeJs.includes("p.indexOf('/api/sd/comfy/') === 0"),
    'runtime.js fetch patch must intercept /proxy/*, native image generation, and /api/sd/comfy/*'
  );

  assert.ok(
    runtimeMgrKt.includes('fun proxyHttpRequestAsync(') &&
      runtimeMgrKt.includes('fun proxyHttpAbort(') &&
      runtimeMgrKt.includes('fun generateImageFromJsAsync(') &&
      runtimeMgrKt.includes('fun cancelImageGenerationFromJs(') &&
      runtimeMgrKt.includes('fun getImageGenerationStatusJson('),
    'TavernRuntimeManager.TavernJsBridge must expose proxyHttpRequestAsync, proxyHttpAbort, generateImageFromJsAsync, cancelImageGenerationFromJs, and getImageGenerationStatusJson'
  );

  assert.ok(
    imgGenVmKt.includes('internal fun classifyImageGenerationError(') &&
      imgGenVmKt.includes('internal fun normalizeImageGenSize('),
    'ImgGenVM must provide classifyImageGenerationError and normalizeImageGenSize'
  );

  if (!ALL_NEEDS_DIR) {
    t.skip(
      '[SKIP] Optional external plugin fixture ST-iPhonie-main/core/image-engines.js not present in source archive. ' +
        'Condition: requires unpacked ST-iPhonie-main fixture directory. ' +
        'Run command when fixture is provided: ALL_NEEDS_DIR=/path/to/all_needs node --test scripts/task-dh-webview-iphonie-imggen.test.mjs'
    );
    return;
  }
  const imgEnginesPath = path.join(ALL_NEEDS_DIR, 'ST-iPhonie-main/core/image-engines.js');
  assert.ok(fs.existsSync(imgEnginesPath), `Expected ST-iPhonie image-engines.js at ${imgEnginesPath} (ALL_NEEDS_DIR=${ALL_NEEDS_DIR})`);
  const imgEngines = await import(`file://${imgEnginesPath}`);
  const PNG_1X1_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

  for (const [orientation, expectedSize] of [
    ['portrait', '1024x1536'],
    ['landscape', '1536x1024'],
    ['square', '1024x1024'],
  ]) {
    const size = imgEngines.gptSize('gpt-image-1', orientation);
    assert.equal(size, expectedSize);
    let capturedBody = null;
    const blob = await imgEngines.gptGenerate({
      fetch: async (_url, init) => {
        capturedBody = JSON.parse(init.body);
        return new Response(JSON.stringify({ data: [{ b64_json: PNG_1X1_B64 }] }), { status: 200 });
      },
      settings: { url: 'https://relay.example.com/v1', model: 'gpt-image-1', quality: 'high', orientation },
      key: 'sk-test-key',
      prompt: '1girl, rainy day',
      size,
    });
    assert.equal(capturedBody.size, expectedSize);
    assert.equal(blob.type, 'image/png');
    assert.ok(blob.size > 0);
  }

  let proxyCalled = false;
  const urlBlob = await imgEngines.gptGenerate({
    fetch: async (url) => {
      if (String(url).endsWith('/images/generations')) {
        return new Response(JSON.stringify({ data: [{ url: 'https://cdn.example.com/pic.png?sig=1' }] }), { status: 200 });
      }
      if (String(url).startsWith('https://cdn.example.com/')) {
        throw new TypeError('Failed to fetch (CORS)');
      }
      if (String(url).startsWith('/proxy/')) {
        proxyCalled = true;
        const bytes = Buffer.from(PNG_1X1_B64, 'base64');
        return new Response(bytes, { status: 200, headers: { 'Content-Type': 'image/png' } });
      }
      throw new Error('Unexpected URL: ' + url);
    },
    settings: { url: 'https://relay.example.com/v1', model: 'gpt-image-1', quality: 'auto', orientation: 'portrait' },
    key: 'sk-test-key',
    prompt: 'test url fallback',
    size: '1024x1536',
  });
  assert.equal(proxyCalled, true, 'gptPicture must fall back to /proxy/ when direct CDN fetch fails');
  assert.equal(urlBlob.type, 'image/png');
});
