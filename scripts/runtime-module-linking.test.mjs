import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { scanStRuntimeGraph } from './esm-graph-validator.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..');
const ST_RUNTIME_ROOT = path.join(REPO_ROOT, 'app/src/main/assets/st-runtime');

test('Task 1.1a: shims/chat-shims.js links and imports cleanly in Node ESM without SyntaxError', async () => {
    const chatShimsUrl = pathToFileURL(path.join(ST_RUNTIME_ROOT, 'shims/chat-shims.js')).href;
    const mod = await import(chatShimsUrl);
    assert.equal(typeof mod.getChatMessages, 'function');
    assert.equal(typeof mod.setChatMessages, 'function');
    assert.equal(typeof mod.setChatMessage, 'function');
    assert.equal(typeof mod.createChatMessages, 'function');
    assert.equal(typeof mod.deleteChatMessages, 'function');
    assert.equal(typeof mod.rotateChatMessages, 'function');
});

test('Task 1.1b: setChatMessages emits tavern_events.MESSAGE_UPDATED ("message_updated") and MESSAGE_SWIPED ("message_swiped") matching ST/JSR contract', async () => {
    const constantsUrl = pathToFileURL(path.join(ST_RUNTIME_ROOT, 'shims/constants.js')).href;
    const chatShimsUrl = pathToFileURL(path.join(ST_RUNTIME_ROOT, 'shims/chat-shims.js')).href;
    const constantsMod = await import(constantsUrl);
    const chatShimsMod = await import(chatShimsUrl);

    assert.equal(constantsMod.tavern_events.MESSAGE_UPDATED, 'message_updated');
    assert.equal(constantsMod.tavern_events.MESSAGE_SWIPED, 'message_swiped');
    if (constantsMod.EVENT_NAMES) {
        assert.equal(constantsMod.EVENT_NAMES.MESSAGE_UPDATED, 'message_updated');
        assert.equal(constantsMod.EVENT_NAMES.MESSAGE_SWIPED, 'message_swiped');
    }

    const emitted = [];
    const mockChat = [
        { name: 'Alice', is_user: false, is_system: false, mes: 'Old text', swipes: ['Old text', 'Swipe 1'], swipe_id: 0, extra: {} }
    ];
    globalThis.window = {
        __rikkaSt: {
            getChatArray: () => mockChat,
            eventSource: {
                emit: (evt, payload) => emitted.push({ evt, payload }),
            },
        },
        RikkaBridge: {
            updateChatMessages: () => {},
        },
    };

    await chatShimsMod.setChatMessages([{ message_id: 0, message: 'New text', swipe_id: 1 }], { refresh: 'affected' });
    assert.deepEqual(emitted, [
        { evt: 'message_updated', payload: 0 },
        { evt: 'message_swiped', payload: 0 },
    ]);
});

test('Task 1.1c & 1.3: Full st-runtime ESM static dependency graph has 0 missing files and 0 missing named/default exports', () => {
    const result = scanStRuntimeGraph(ST_RUNTIME_ROOT);
    assert.ok(result.scannedFilesCount >= 120, `Expected >=120 ESM files scanned, got ${result.scannedFilesCount}`);
    assert.deepEqual(result.errors, [], `ESM graph errors found:\n${JSON.stringify(result.errors, null, 2)}`);
});
