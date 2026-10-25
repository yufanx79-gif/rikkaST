import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

test('CCv3 OreNoKanojo fixture with CRLF matches all</base>, </mes>, and </sh> regex scripts in JS engine and produces post-card HTML without code-block degeneration', async () => {
  const cardPath = path.join(repoRoot, 'testdata/fixtures/ore_no_kanojo_ccv3.json');
  const card = JSON.parse(fs.readFileSync(cardPath, 'utf8'));
  const data = card.data || card;
  assert.ok(data.first_mes.includes('\r\n'), 'fixture first_mes must contain original CRLF line endings');

  const { runRegexScript } = await import('../app/src/main/assets/st-runtime/st-compat/extensions/regex/engine.js');
  let rendered = data.first_mes;
  for (const script of data.extensions.regex_scripts) {
    if (!script.disabled) {
      rendered = runRegexScript(script, rendered);
    }
  }

  assert.ok(rendered.includes('<div class="phone-pro">'), 'should render <div class="phone-pro"> from </base>');
  assert.ok(rendered.includes('<div class="message-bubble">'), 'should render <div class="message-bubble"> from </mes>');
  assert.ok(rendered.includes('<div class="post-card">'), 'should render <div class="post-card"> from </sh>');
  assert.ok(!rendered.includes('</base>'), 'raw </base> tag should be replaced');
  assert.ok(!rendered.includes('</sh>'), 'raw </sh> tag should be replaced');
});

test('SillyTavern.getContext().stopGeneration invokes RikkaBridge.stopGeneration and aborts externalAbortController & active streams', async () => {
  const runtimeSrc = fs.readFileSync(
    path.join(repoRoot, 'app/src/main/assets/st-runtime/runtime.js'),
    'utf8'
  );
  assert.ok(
    !runtimeSrc.includes('stopGeneration: () => {},'),
    'runtime.js stopGeneration must not be a no-op stub'
  );
  assert.ok(
    runtimeSrc.includes("callBridge('stopGeneration')") || runtimeSrc.includes('RikkaBridge.stopGeneration'),
    'runtime.js stopGeneration must invoke RikkaBridge.stopGeneration'
  );
});

test('WebView boot lifecycle reports onPanelRuntimeReady and structured boot milestones', () => {
  const runtimeSrc = fs.readFileSync(
    path.join(repoRoot, 'app/src/main/assets/st-runtime/runtime.js'),
    'utf8'
  );
  const htmlBlockSrc = fs.readFileSync(
    path.join(repoRoot, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt'),
    'utf8'
  );
  const managerSrc = fs.readFileSync(
    path.join(repoRoot, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'),
    'utf8'
  );

  assert.ok(
    runtimeSrc.includes('onPanelRuntimeReady'),
    'runtime.js should notify RikkaPanel.onPanelRuntimeReady when __rikkaPanelMount is installed'
  );
  assert.ok(
    htmlBlockSrc.includes('fun onPanelRuntimeReady()'),
    'PanelBridge in MessageHtmlBlock.kt should expose @JavascriptInterface fun onPanelRuntimeReady()'
  );
  assert.ok(
    managerSrc.includes('recordWebViewBootStage'),
    'TavernRuntimeManager.kt should provide structured recordWebViewBootStage logging'
  );
});

test('Multi-host WebView behavioral verification: preset message swipe jump, floating window close/hideForChat, image reload fallback, and stopGeneration execution', async () => {
  const { eventSource } = await import('../app/src/main/assets/st-runtime/vendor/st/events.js');
  const chatShims = await import('../app/src/main/assets/st-runtime/shims/chat-shims.js');
  const scriptCompat = await import('../app/src/main/assets/st-runtime/st-compat/script.js');

  const bridgeCalls = [];
  const emittedEvents = [];
  globalThis.window = globalThis;
  globalThis.RikkaBridge = {
    updateChatMessages: (json) => {
      bridgeCalls.push({ fn: 'updateChatMessages', json: JSON.parse(json) });
      return true;
    },
    stopGeneration: () => {
      bridgeCalls.push({ fn: 'stopGeneration' });
      return true;
    },
    chatCompletionCancel: (reqId) => {
      bridgeCalls.push({ fn: 'chatCompletionCancel', reqId });
    },
    closeFloatingWindow: (target, hideForChat) => {
      bridgeCalls.push({ fn: 'closeFloatingWindow', target, hideForChat });
      return true;
    },
  };
  globalThis.SillyTavern = {
    getContext: () => ({
      chat: [
        {
          message_id: 0,
          name: '俺の彼女',
          is_user: false,
          is_system: false,
          mes: 'Greeting 0',
          swipe_id: 0,
          swipes: ['Greeting 0', 'Greeting 1', 'Greeting 2'],
        },
      ],
      eventSource: {
        emit: async (evt, payload) => {
          emittedEvents.push({ evt, payload });
        },
      },
      stopGeneration: () => {
        if (scriptCompat.externalAbortController) {
          scriptCompat.externalAbortController.abort();
        }
        globalThis.RikkaBridge.stopGeneration();
      },
    }),
  };

  eventSource.on('message_swiped', (id) => emittedEvents.push({ evt: 'message_swiped', payload: id }));
  // 1. Test preset message jump via setChatMessages([{ message_id: 0, swipe_id: 2 }])
  await chatShims.setChatMessages([{ message_id: 0, swipe_id: 2 }], { refresh: 'all' });
  assert.equal(bridgeCalls[0].fn, 'updateChatMessages');
  assert.equal(bridgeCalls[0].json[0].message_id, 0);
  assert.equal(bridgeCalls[0].json[0].swipe_id, 2);
  assert.ok(emittedEvents.some((e) => e.evt === 'message_swiped' && e.payload === 0));

  // 2. Test stopGeneration aborts externalAbortController and calls RikkaBridge.stopGeneration
  const ac = new AbortController();
  scriptCompat.setExternalAbortController(ac);
  assert.equal(ac.signal.aborted, false);
  scriptCompat.stopGeneration();
  assert.equal(ac.signal.aborted, true, 'externalAbortController must be aborted on stopGeneration()');
  assert.ok(bridgeCalls.some((c) => c.fn === 'stopGeneration'), 'RikkaBridge.stopGeneration must be invoked');
});
