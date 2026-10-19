import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { spawn } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const WORK_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const importFile = (p) => import(pathToFileURL(p).href);
const ASSETS_RUNTIME = path.join(WORK_ROOT, 'app/src/main/assets/st-runtime');
const ALL_NEEDS_DIR = process.env.ALL_NEEDS_DIR || '/tmp/rikka_work/all_needs';

// Provide minimal browser globals for st-compat and real extension modules
globalThis.window = globalThis;
globalThis.location = new URL('https://appassets.androidplatform.net/st-runtime/index.html');
globalThis.DOMException = globalThis.DOMException || class DOMException extends Error {
    constructor(msg, name) { super(msg); this.name = name || 'DOMException'; }
};

test('Clue 1: Version Gate Consistency (1.19.0 across runtime.js, script.js, TavernRuntimeManager.kt) & MacroRegistry + createGenerationParameters', async () => {
    const runtimeTxt = fs.readFileSync(path.join(ASSETS_RUNTIME, 'runtime.js'), 'utf8');
    const scriptTxt = fs.readFileSync(path.join(ASSETS_RUNTIME, 'st-compat/script.js'), 'utf8');
    const ktTxt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'), 'utf8');

    assert.match(runtimeTxt, /const TAVERN_ST_COMPAT_VERSION = '1\.19\.0';/);
    assert.match(runtimeTxt, /version: '1\.19\.0',/);
    assert.match(scriptTxt, /export const CLIENT_VERSION = '1\.19\.0';/);
    assert.match(ktTxt, /const val TAVERN_ST_COMPAT_VERSION = "1\.19\.0"/);
    assert.ok(!ktTxt.includes('fun getVersion(): String = "1.18.0"'));

    const macroRegMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/macros/engine/MacroRegistry.js'));
    assert.ok(macroRegMod.MacroRegistry);
    assert.ok(macroRegMod.MacroCategory);
    assert.ok(macroRegMod.MacroValueType);

    macroRegMod.MacroRegistry.registerMacro('test_st119_macro', {
        category: macroRegMod.MacroCategory.UTILITY,
        description: 'Test macro for JSR Reference.vue',
        handler: () => 'ok',
    });
    const def = macroRegMod.MacroRegistry.getMacro('test_st119_macro');
    assert.equal(def.name, 'test_st119_macro');
    assert.ok(macroRegMod.MacroRegistry.getAllMacros().some(m => m.name === 'test_st119_macro'));

    const openaiMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/openai.js'));
    assert.equal(openaiMod.POLLINATIONS_ENDPOINT, 'https://gen.pollinations.ai/v1');
    assert.equal(typeof openaiMod.createGenerationParameters, 'function');
    assert.equal(typeof openaiMod.settingsToUpdate, 'object');
    assert.deepEqual(openaiMod.settingsToUpdate.temperature, [1.0, 'temp_openai']);

    const genParams = await openaiMod.createGenerationParameters(
        { temp_openai: 0.65, stream_openai: true },
        'claude-3-7-sonnet',
        'normal',
        [{ role: 'user', content: 'hi' }],
        { tools: [{ type: 'function', function: { name: 't1' } }] }
    );
    assert.equal(genParams.generate_data.type, 'normal');
    assert.equal(genParams.generate_data.temperature, 0.65);
    assert.equal(genParams.generate_data.model, 'claude-3-7-sonnet');
    assert.equal(genParams.stream, true);
    assert.equal(genParams.generate_data.tools.length, 1);
});

test('Clue 3: st-compat/openai.js getStreamingReply is synchronous and extracts OpenAI/Claude/Gemini deltas without [object Promise]', async () => {
    const openaiMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/openai.js'));
    assert.notEqual(openaiMod.getStreamingReply.constructor.name, 'AsyncFunction');

    const chunk1 = openaiMod.getStreamingReply({
        choices: [{ delta: { content: 'Hello, ', reasoning_content: 'thinking...' } }],
    }, { reasoning: '' }, { source: 'openai' });
    assert.equal(typeof chunk1, 'string');
    assert.equal(chunk1, 'Hello, ');
    assert.ok(!chunk1.includes('[object Promise]'));

    const chunk2 = openaiMod.getStreamingReply({
        choices: [{ delta: { content: 'SillyTavern 1.19.0!' } }],
    }, { reasoning: '' }, { source: 'openai' });
    assert.equal(chunk1 + chunk2, 'Hello, SillyTavern 1.19.0!');
});

test('Clue 5: EventEmitter.prototype.emitAndWait awaits async listeners sequentially & vendor/st/events.js shares singleton with st-compat/events.js', async () => {
    const { EventEmitter } = await importFile(path.join(ASSETS_RUNTIME, 'vendor/st/eventemitter.js'));
    const vendorEvents = await importFile(path.join(ASSETS_RUNTIME, 'vendor/st/events.js'));
    const compatEvents = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/events.js'));
    assert.strictEqual(vendorEvents.eventSource, compatEvents.eventSource);

    const bus = new EventEmitter();
    const trace = [];

    bus.on('prompt_hook', async (data) => {
        await new Promise(r => setTimeout(r, 25));
        data.messages.push({ role: 'system', content: 'from-async-listener-1' });
        trace.push('listener-1');
    });
    bus.on('prompt_hook', async (data) => {
        await new Promise(r => setTimeout(r, 10));
        data.messages = data.messages.map(m => ({ ...m, content: m.content + ' [rewritten-by-2]' }));
        trace.push('listener-2');
    });

    const payload = { messages: [{ role: 'user', content: 'initial' }] };
    await bus.emitAndWait('prompt_hook', payload);

    assert.deepEqual(trace, ['listener-1', 'listener-2']);
    assert.equal(payload.messages.length, 2);
    assert.equal(payload.messages[0].content, 'initial [rewritten-by-2]');
    assert.equal(payload.messages[1].content, 'from-async-listener-1 [rewritten-by-2]');
});

test('Clue 6 & P0-2: MessageFormatter 1.19.0 3-stage pipeline, hook_order sorting, validation, frozen ctx, owner cleanup, and non-blocking Main-thread Compose contract', async () => {
    const { MessageFormatter, formatting_stage, hook_order } = await importFile(
        path.join(ASSETS_RUNTIME, 'st-compat/message-formatter.js')
    );
    MessageFormatter.clearAllHooks();

    assert.deepEqual(formatting_stage, {
        BEFORE_REGEX: 'beforeRegex',
        AFTER_REGEX: 'afterRegex',
        AFTER_MARKDOWN: 'afterMarkdown',
    });
    assert.deepEqual(hook_order, {
        EARLIEST: 0,
        EARLY: 10,
        NORMAL: 50,
        LATE: 90,
        LATEST: 100,
    });

    assert.throws(() => MessageFormatter.addHook('not-a-fn'), TypeError);
    assert.throws(() => MessageFormatter.addHook(async (m) => m), TypeError);
    assert.throws(() => MessageFormatter.addHook((m) => m, { stage: 'invalidStage' }), RangeError);

    let frozenCheck = false;
    let stageSeen = [];
    MessageFormatter.addHook((mes, ctx) => {
        frozenCheck = Object.isFrozen(ctx);
        stageSeen.push(ctx.stage);
        return mes + '[LATE]';
    }, { stage: formatting_stage.AFTER_MARKDOWN, order: hook_order.LATE, owner: 'ext-a' });

    MessageFormatter.addHook((mes, ctx) => {
        stageSeen.push(ctx.stage);
        return mes + '[EARLY]';
    }, { stage: formatting_stage.AFTER_MARKDOWN, order: hook_order.EARLY, owner: 'ext-a' });

    MessageFormatter.addHook((mes, ctx) => {
        stageSeen.push(ctx.stage);
        return '[BEFORE]' + mes;
    }, { stage: formatting_stage.BEFORE_REGEX, order: hook_order.NORMAL, owner: 'ext-b' });

    const formatted = MessageFormatter.format('body', 'Aqua', false, false, 3, {}, false);
    assert.equal(formatted, '[BEFORE]body[EARLY][LATE]');
    assert.equal(frozenCheck, true);
    assert.deepEqual(stageSeen, ['beforeRegex', 'afterMarkdown', 'afterMarkdown']);

    assert.equal(MessageFormatter.clearHooksByOwner('ext-a'), 2);
    assert.equal(MessageFormatter.format('body', 'Aqua', false, false, 3, {}, false), '[BEFORE]body');
    MessageFormatter.clearAllHooks();

    const stRegexKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/StRegexedText.kt'),
        'utf8'
    );
    const runtimeMgrKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'),
        'utf8'
    );
    assert.ok(stRegexKt.includes('TavernRuntimeManager.messageFormatterVersionFlow.collectAsState()'));
    assert.ok(stRegexKt.includes('TavernRuntimeManager.runMessageFormatterStageAsync('));
    assert.ok(stRegexKt.includes('computeStRegexedTextAsync('));
    assert.ok(!runtimeMgrKt.includes('if (Looper.myLooper() == Looper.getMainLooper()) return text'));
});

test('Clue 2 & Clue 7: Variable-length message rewriting (st-memory-enhancement splice + ST-Prompt-Template reassign + ST-iPhonie mes mutation) & Kotlin contract check', async () => {
    const { EventEmitter } = await importFile(path.join(ASSETS_RUNTIME, 'vendor/st/eventemitter.js'));
    const bus = new EventEmitter();

    let activeMessages = [
        { role: 'system', content: 'You are Aqua.', mes: 'You are Aqua.', _origContent: 'You are Aqua.', _origMes: 'You are Aqua.', is_user: false, is_system: true },
        { role: 'user', content: 'Hello!', mes: 'Hello!', _origContent: 'Hello!', _origMes: 'Hello!', is_user: true, is_system: false },
    ];

    const stIphonieInterceptor = async (chat, _contextSize, _abort, _type) => {
        chat[1] = { ...chat[1], mes: '[iPhone SMS] Hello!' };
    };
    await stIphonieInterceptor(activeMessages, 4096, () => {}, 'normal');

    for (let i = 0; i < activeMessages.length; i++) {
        const item = activeMessages[i];
        if (typeof item.mes === 'string' && item.mes !== item._origMes) {
            item.content = item.mes;
            item._origContent = item.content;
            item._origMes = item.mes;
        }
    }

    const eventData = { type: 'normal', dryRun: false };
    Object.defineProperty(eventData, 'messages', {
        enumerable: true,
        get() { return activeMessages; },
        set(v) { if (Array.isArray(v)) activeMessages = v; },
    });
    Object.defineProperty(eventData, 'chat', {
        enumerable: true,
        get() { return activeMessages; },
        set(v) { if (Array.isArray(v)) activeMessages = v; },
    });
    Object.defineProperty(eventData, 'prompt', {
        enumerable: true,
        get() { return activeMessages; },
        set(v) { if (Array.isArray(v)) activeMessages = v; },
    });

    bus.on('chat_completion_prompt_ready', async (data) => {
        data.chat.splice(1, 0, { role: 'system', content: '<table_memory>HP=100</table_memory>' });
    });

    bus.on('chat_completion_settings_ready', async (data) => {
        data.messages = [
            ...data.messages,
            { role: 'system', content: '[ST-PT Post-History Instruction]' },
        ];
    });

    await bus.emitAndWait('chat_completion_prompt_ready', eventData);
    await bus.emitAndWait('chat_completion_settings_ready', eventData);

    assert.equal(activeMessages.length, 4);
    assert.equal(activeMessages[0].content, 'You are Aqua.');
    assert.equal(activeMessages[1].content, '<table_memory>HP=100</table_memory>');
    assert.equal(activeMessages[2].content, '[iPhone SMS] Hello!');
    assert.equal(activeMessages[3].content, '[ST-PT Post-History Instruction]');

    const genLoopKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt'),
        'utf8'
    );
    assert.ok(!genLoopKt.includes('parsed.size == internalMessages.size'));
    assert.ok(!genLoopKt.includes('return@channelFlow'));
    assert.ok(genLoopKt.includes('hookOutcome.messages.isNotEmpty()'));
    assert.ok(genLoopKt.includes('hookOutcome.aborted'));
});

test('Clue 4 & Clue 9: extractMessageFromData + st-compat modules (AccountStorage, custom-request, world-info, utils, PromptManager, group-chats)', async () => {
    const scriptMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/script.js'));
    const completionObj = {
        id: 'chatcmpl-test',
        object: 'chat.completion',
        choices: [{ index: 0, message: { role: 'assistant', content: 'Non-streaming reply ok!' }, finish_reason: 'stop' }],
    };
    assert.equal(scriptMod.extractMessageFromData(completionObj, 'chat_completion'), 'Non-streaming reply ok!');
    assert.equal(typeof scriptMod.setCharacterId, 'function');
    assert.equal(typeof scriptMod.setCharacterName, 'function');
    assert.equal(typeof scriptMod.setExternalAbortController, 'function');
    assert.equal(typeof scriptMod.setSendButtonState, 'function');
    assert.equal(typeof scriptMod.callPopup, 'function');
    assert.equal(typeof scriptMod.processCommands, 'function');

    const { accountStorage } = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/util/AccountStorage.js'));
    accountStorage.setItem('lwb_task_check', 'true');
    assert.equal(accountStorage.getItem('lwb_task_check'), 'true');
    accountStorage.removeItem('lwb_task_check');
    assert.equal(accountStorage.getItem('lwb_task_check'), null);

    const { ChatCompletionService } = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/custom-request.js'));
    const payload = await ChatCompletionService.presetToGeneratePayload(
        { temperature: 0.8, openai_max_tokens: 512 },
        {},
        { messages: [{ role: 'user', content: 'hello' }], model: 'gpt-4o', stream: true }
    );
    assert.equal(payload.model, 'gpt-4o');
    assert.equal(payload.temperature, 0.8);
    assert.equal(payload.max_tokens, 512);
    assert.equal(payload.stream, true);

    const wiMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/world-info.js'));
    assert.equal(typeof wiMod.originalWIDataKeyMap, 'object');
    assert.equal(wiMod.originalWIDataKeyMap.key, 'keys');
    assert.equal(typeof wiMod.onWorldInfoChange, 'function');
    assert.equal(typeof wiMod.updateWorldInfoSettings, 'function');
    await wiMod.onWorldInfoChange({ state: true }, 'TestWorldBook');
    assert.ok(wiMod.selected_world_info.includes('TestWorldBook'));

    const utilsMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/utils.js'));
    assert.equal(typeof utilsMod.getFileText, 'function');
    assert.equal(typeof utilsMod.getSortableDelay, 'function');
    assert.equal(typeof utilsMod.extractTextFromOffice, 'function');
    assert.equal(await utilsMod.getFileText('raw text'), 'raw text');

    const pmMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/PromptManager.js'));
    assert.deepEqual(pmMod.INJECTION_POSITION, { RELATIVE: 0, ABSOLUTE: 1 });

    const gcMod = await importFile(path.join(ASSETS_RUNTIME, 'st-compat/group-chats.js'));
    assert.equal(gcMod.is_group_generating, false);
});

test('Clue 8: MacroEngine.kt supports {{//}}...{{///}} scoped comments with delayArgResolution', () => {
    const macroEngineKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/macro/MacroEngine.kt'),
        'utf8'
    );
    const macroDefsKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/macro/MacroDefinitions.kt'),
        'utf8'
    );
    assert.ok(macroEngineKt.includes('val isClosingComment = !flags.closingBlock && args.firstOrNull()?.text?.startsWith("/") == true'));
    assert.ok(macroDefsKt.includes('delayArgResolution = true'));
});

test('Real Extension Integration (1/5): ST-Prompt-Template-1.16 manifest & real EJS template engine evaluation', async (t) => {
    const ejsUmdCode = fs.readFileSync(path.join(ASSETS_RUNTIME, 'vendor/lib/ejs.umd.js'), 'utf8');
    vm.runInThisContext(ejsUmdCode);
    assert.equal(typeof globalThis.ejs?.compile, 'function');

    const ejsMod = await importFile(path.join(ASSETS_RUNTIME, 'shims/ejs-render.js'));
    assert.equal(typeof ejsMod.renderEjsText, 'function');
    assert.equal(ejsMod.isEjsAvailable(), true);
    assert.equal(ejsMod.containsEjs('Hello <%= user %>'), true);

    const vars = { hp: 85, hero: 'Aqua' };
    const env = ejsMod.createEjsApi({
        user: 'Kazuma',
        char: 'Aqua',
        getvar: (k) => vars[k],
        setvar: (k, v) => { vars[k] = v; return ''; },
    });
    const rendered = await ejsMod.renderEjsText(
        '<% if (getvar("hp") > 50) { %><%= char %> greets <%= user %> (HP=<%= getvar("hp") %>)<% setvar("hp", 100); %><% } %>',
        env
    );
    assert.equal(rendered.trim(), 'Aqua greets Kazuma (HP=85)');
    assert.equal(vars.hp, 100);

    const stPtDir = path.join(ALL_NEEDS_DIR, 'ST-Prompt-Template-1.16');
    if (!fs.existsSync(stPtDir)) {
        t.skip('Unpacked ST-Prompt-Template-1.16 not present in ALL_NEEDS_DIR');
        return;
    }
    const manifest = JSON.parse(fs.readFileSync(path.join(stPtDir, 'manifest.json'), 'utf8'));
    assert.equal(manifest.display_name, 'Prompt Template/提示词模板');
    assert.equal(manifest.version, '1.16.3.0');
    assert.ok(fs.existsSync(path.join(stPtDir, manifest.js)));
    assert.ok(fs.existsSync(path.join(stPtDir, 'dist/ejs.workers.js')));
});

test('Real Extension Integration (2/5): JS-Slash-Runner-main manifest, bundle integrity, and TavernHelper runtime shims', async (t) => {
    const utilShims = await importFile(path.join(ASSETS_RUNTIME, 'shims/util-shims.js'));
    assert.equal(typeof utilShims.getTavernHelperVersion, 'function');
    assert.equal(typeof utilShims.getTavernVersion, 'function');

    const jsrDir = path.join(ALL_NEEDS_DIR, 'JS-Slash-Runner-main');
    if (!fs.existsSync(jsrDir)) {
        t.skip('Unpacked JS-Slash-Runner-main not present in ALL_NEEDS_DIR');
        return;
    }
    const manifest = JSON.parse(fs.readFileSync(path.join(jsrDir, 'manifest.json'), 'utf8'));
    assert.equal(manifest.display_name, '酒馆助手');
    assert.equal(manifest.version, '4.11.3');
    assert.equal(manifest.hooks?.activate, 'activateTauriTavernChatSurface');
    assert.ok(fs.existsSync(path.join(jsrDir, manifest.js)));
    const distBundle = fs.readFileSync(path.join(jsrDir, manifest.js), 'utf8');
    assert.ok(distBundle.includes('TavernHelper'));
    assert.ok(distBundle.includes('MacroRegistry'));
});

test('Real Extension Integration (3/5): st-memory-enhancement-2.2.0 real module execution (data/pluginSetting.js & utils/stringUtil.js)', async (t) => {
    const memDir = path.join(ALL_NEEDS_DIR, 'st-memory-enhancement-2.2.0');
    if (!fs.existsSync(memDir)) {
        t.skip('Unpacked st-memory-enhancement-2.2.0 not present in ALL_NEEDS_DIR');
        return;
    }
    const manifest = JSON.parse(fs.readFileSync(path.join(memDir, 'manifest.json'), 'utf8'));
    assert.equal(manifest.version, '2.2.0');
    assert.ok(fs.existsSync(path.join(memDir, manifest.js)));

    // Import real ESM source from st-memory-enhancement-2.2.0 (core/table/utils.js & utils/stringUtil.js)
    const tableUtilsSrc = fs.readFileSync(path.join(memDir, 'core/table/utils.js'), 'utf8');
    const tableUtilsMod = await import(`data:text/javascript;base64,${Buffer.from(tableUtilsSrc).toString('base64')}`);
    assert.equal(tableUtilsMod.getColumnLetter(0), 'A');
    assert.equal(tableUtilsMod.getColumnLetter(25), 'Z');
    assert.equal(tableUtilsMod.getColumnLetter(26), 'AA');

    const stringUtilSrc = fs.readFileSync(path.join(memDir, 'utils/stringUtil.js'), 'utf8')
        .replace(/import\s+\{[^}]*\}\s+from\s+['"][^'"]+['"];?/g, 'const USER = { getContext: () => ({ name1: "Kazuma" }) };');
    const stringUtilMod = await import(`data:text/javascript;base64,${Buffer.from(stringUtilSrc).toString('base64')}`);
    assert.equal(stringUtilMod.replaceUserTag('Hello <user>!'), 'Hello Kazuma!');
    assert.deepEqual(stringUtilMod.safeParse('[{"hp":100}]'), [[{ hp: 100 }]]);
});

test('Real Extension Integration (4/5): LittleWhiteBox-3.1.7 real module execution (generate-interceptor.js & core/constants.js)', async (t) => {
    const lwbDir = path.join(ALL_NEEDS_DIR, 'LittleWhiteBox-3.1.7');
    if (!fs.existsSync(lwbDir)) {
        t.skip('Unpacked LittleWhiteBox-3.1.7 not present in ALL_NEEDS_DIR');
        return;
    }
    const manifest = JSON.parse(fs.readFileSync(path.join(lwbDir, 'manifest.json'), 'utf8'));
    assert.equal(manifest.display_name, 'LittleWhiteBox');
    assert.equal(manifest.version, '3.1.7');

    const constMod = await importFile(path.join(lwbDir, 'core/constants.js'));
    assert.equal(constMod.EXT_ID, 'LittleWhiteBox');

    const interceptorMod = await importFile(path.join(lwbDir, 'shared/common/generate-interceptor.js'));
    assert.equal(typeof interceptorMod.registerGenerateInterceptor, 'function');
    assert.equal(typeof interceptorMod.unregisterGenerateInterceptor, 'function');
    assert.equal(typeof interceptorMod.observeGenerateInterceptors, 'function');

    const calls = [];
    interceptorMod.registerGenerateInterceptor('test-late', async (chat) => {
        calls.push('late');
        chat.push({ role: 'system', mes: 'late' });
    }, 90);
    interceptorMod.registerGenerateInterceptor('test-early', async (chat) => {
        calls.push('early');
        chat.push({ role: 'system', mes: 'early' });
    }, 10);

    try {
        assert.equal(typeof globalThis.xiaobaixGenerateInterceptor, 'function');
        const sampleChat = [{ role: 'user', mes: 'hello' }];
        await globalThis.xiaobaixGenerateInterceptor(sampleChat, 4096, () => {}, 'normal');
        assert.deepEqual(calls, ['early', 'late']);
        assert.equal(sampleChat.length, 3);
    } finally {
        interceptorMod.unregisterGenerateInterceptor('test-late');
        interceptorMod.unregisterGenerateInterceptor('test-early');
    }
});

test('Real Extension Integration (5/5): ST-iPhonie-main real module execution (core/outgoing.js & core/protocol.js)', async (t) => {
    const iphonieDir = path.join(ALL_NEEDS_DIR, 'ST-iPhonie-main');
    if (!fs.existsSync(iphonieDir)) {
        t.skip('Unpacked ST-iPhonie-main not present in ALL_NEEDS_DIR');
        return;
    }
    const manifest = JSON.parse(fs.readFileSync(path.join(iphonieDir, 'manifest.json'), 'utf8'));
    assert.equal(manifest.display_name, 'ST-iPhonie');
    assert.equal(manifest.version, '0.8.15');
    assert.equal(manifest.minimum_client_version, '1.13.2');
    assert.equal(manifest.generate_interceptor, 'stIphonieInterceptor');

    const outgoingMod = await importFile(path.join(iphonieDir, 'core/outgoing.js'));
    const protocolMod = await importFile(path.join(iphonieDir, 'core/protocol.js'));
    assert.equal(typeof outgoingMod.outgoingChat, 'function');
    assert.equal(typeof outgoingMod.withoutVoice, 'function');
    assert.equal(typeof protocolMod.parseDialogue, 'function');

    const originalChat = [
        {
            is_user: false,
            is_system: false,
            mes: 'Older reply “早上好！”<tts>Aqua|happy|Good morning!</tts>',
        },
        {
            is_user: false,
            is_system: false,
            mes: 'Latest reply “保留示例！”<tts>Aqua|excited|Keep this voice tag as example!</tts>',
        },
    ];
    const clonedChat = structuredClone(originalChat);
    outgoingMod.outgoingChat(clonedChat, {
        pictures: true,
        voice: true,
        keepLatest: true,
        formats: [protocolMod.DEFAULT_FORMAT],
    });

    assert.ok(!clonedChat[0].mes.includes('<tts>'));
    assert.ok(clonedChat[0].mes.includes('“早上好！”'));
    assert.ok(clonedChat[1].mes.includes('<tts>Aqua|excited|Keep this voice tag as example!</tts>'));
});

test('P0-1: NodeRuntimeManager.kt official Node.js v20.18.1 SHA-256 constants and engine version range check', () => {
    const nodeMgrKt = fs.readFileSync(
        path.join(WORK_ROOT, 'workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt'),
        'utf8'
    );
    assert.ok(
        nodeMgrKt.includes('const val SHA256_LINUX_ARM64 = "44d1ffc5905c005ace4515ca6f8c090c4c7cfce3a9a67df0dba35c727590b8f6"'),
        'ARM64 SHA-256 must match official Node.js v20.18.1 SHASUMS256.txt'
    );
    assert.ok(
        nodeMgrKt.includes('const val SHA256_LINUX_X64 = "c6fa75c841cbffac851678a472f2a5bd612fff8308ef39236190e1f8dbb0e567"'),
        'x64 SHA-256 must match official Node.js v20.18.1 SHASUMS256.txt'
    );
    assert.ok(
        nodeMgrKt.includes('return (major > 20) || (major == 20 && minor >= 11) || (major == 18 && minor >= 20)'),
        'Engine check must accept Node >=18.20 and >=20.11.0'
    );
});

test('Clue 10 & P0-3: Shared Node.js server-bootstrap.cjs + littlewhitebox-server end-to-end integration & adversarial identity/session isolation checks', async () => {
    const port = 18481;
    const token = 'integration-token-999';
    const bootstrapPath = path.join(ASSETS_RUNTIME, 'node-host/server-bootstrap.cjs');

    const nodeServiceKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernNodeServiceManager.kt'),
        'utf8'
    );
    assert.ok(nodeServiceKt.includes('buildSanitizedRequestHeaders'));
    assert.ok(nodeServiceKt.includes('lower.startsWith("x-rikka-")'));

    const proc = spawn('node', [bootstrapPath], {
        env: { ...process.env, RIKKA_NODE_PORT: String(port), RIKKA_NODE_TOKEN: token },
        stdio: ['ignore', 'pipe', 'pipe'],
    });

    try {
        await new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error('Startup timeout')), 5000);
            proc.stdout.on('data', (chunk) => {
                if (chunk.toString().includes(`Listening on http://127.0.0.1:${port}`)) {
                    clearTimeout(timer);
                    resolve();
                }
            });
            proc.on('exit', (code) => reject(new Error(`Exited early: ${code}`)));
        });

        const base = `http://127.0.0.1:${port}`;
        const authHeaders = {
            Authorization: `Bearer ${token}`,
            'X-CSRF-Token': token,
            'Content-Type': 'application/json',
        };

        // 1. Negative security test: Missing Bearer token -> 401 Unauthorized
        const unauthRes = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/jobs`);
        assert.equal(unauthRes.status, 401);

        // 2. Negative security test: Spoofed X-Rikka-Profile-Handle mismatching assistant/conversation -> 403 Forbidden
        const spoofedHandleRes = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/jobs`, {
            headers: {
                ...authHeaders,
                'X-Rikka-Assistant-Id': 'assist-1',
                'X-Rikka-Conversation-Id': 'conv-1',
                'X-Rikka-Profile-Handle': 'rikka_assist-2_conv-2',
            },
        });
        assert.equal(spoofedHandleRes.status, 403);
        const spoofedBody = await spoofedHandleRes.json();
        assert.match(spoofedBody.error, /Rejected spoofed X-Rikka-Profile-Handle/);

        // 3. Negative security test: POST /api/host/context attempting to override assistantId/conversationId/profileHandle in body -> 403 Forbidden
        const ctxSpoofHTTP = await fetch(`${base}/api/host/context`, {
            method: 'POST',
            headers: {
                ...authHeaders,
                'X-Rikka-Assistant-Id': 'assist-safe',
                'X-Rikka-Conversation-Id': 'conv-safe',
            },
            body: JSON.stringify({
                assistantId: 'evil-assistant',
                conversationId: 'evil-conv',
                profileHandle: 'evil_handle',
                variables: { hacked: true },
            }),
        });
        assert.equal(ctxSpoofHTTP.status, 403);
        const ctxSpoofBody = await ctxSpoofHTTP.json();
        assert.equal(ctxSpoofBody.code, 'session_identity_mismatch');

        // 3b. Valid session-scoped POST /api/host/context stores state strictly in assist-safe_conv-safe bucket
        const ctxValidPost = await fetch(`${base}/api/host/context`, {
            method: 'POST',
            headers: {
                ...authHeaders,
                'X-Rikka-Assistant-Id': 'assist-safe',
                'X-Rikka-Conversation-Id': 'conv-safe',
            },
            body: JSON.stringify({
                variables: { hp: 100, hero: 'Aqua' },
                metadata: { world: 'Axel' },
            }),
        }).then(r => r.json());
        assert.equal(ctxValidPost.ok, true);
        assert.equal(ctxValidPost.profileHandle, 'assist-safe_conv-safe');
        assert.equal(ctxValidPost.variables.hp, 100);

        const ctxGetRes = await fetch(`${base}/api/host/context`, {
            headers: {
                ...authHeaders,
                'X-Rikka-Assistant-Id': 'assist-safe',
                'X-Rikka-Conversation-Id': 'conv-safe',
            },
        }).then(r => r.json());
        assert.equal(ctxGetRes.assistantId, 'assist-safe');
        assert.equal(ctxGetRes.conversationId, 'conv-safe');
        assert.equal(ctxGetRes.profileHandle, 'assist-safe_conv-safe');
        assert.equal(ctxGetRes.variables.hero, 'Aqua');

        // Verify another session does NOT see assist-safe's variables
        const ctxOtherRes = await fetch(`${base}/api/host/context`, {
            headers: {
                ...authHeaders,
                'X-Rikka-Assistant-Id': 'assist-other',
                'X-Rikka-Conversation-Id': 'conv-other',
            },
        }).then(r => r.json());
        assert.equal(ctxOtherRes.assistantId, 'assist-other');
        assert.equal(ctxOtherRes.variables.hero, undefined);

        // 4. Verify /api/backends/chat-completions/status and /generate (both stream:false and stream:true)
        const ccStatus = await fetch(`${base}/api/backends/chat-completions/status`, { headers: authHeaders }).then(r => r.json());
        assert.equal(ccStatus.object, 'list');

        const ccNonStream = await fetch(`${base}/api/backends/chat-completions/generate`, {
            method: 'POST',
            headers: authHeaders,
            body: JSON.stringify({
                model: 'rikka-host-model',
                stream: false,
                mockReply: 'Hello from non-stream!',
                messages: [{ role: 'user', content: 'ping' }],
            }),
        }).then(r => r.json());
        assert.equal(ccNonStream.object, 'chat.completion');
        assert.equal(ccNonStream.choices[0].message.content, 'Hello from non-stream!');

        const ccStreamRes = await fetch(`${base}/api/backends/chat-completions/generate`, {
            method: 'POST',
            headers: authHeaders,
            body: JSON.stringify({
                model: 'rikka-host-model',
                stream: true,
                mockReply: 'Hello from SSE!',
                messages: [{ role: 'user', content: 'ping' }],
            }),
        });
        assert.equal(ccStreamRes.headers.get('content-type'), 'text/event-stream');
        const sseText = await ccStreamRes.text();
        assert.ok(sseText.includes('Hello from SSE!'));
        assert.ok(sseText.includes('data: [DONE]\n\n'));

        // 5. Verify LittleWhiteBox Server /v1/jobs and /v1/cancel with canonical profile headers
        const sessionHeadersA = {
            ...authHeaders,
            'X-Rikka-Assistant-Id': 'assist-1',
            'X-Rikka-Conversation-Id': 'conv-1',
            'X-Rikka-Profile-Handle': 'assist-1_conv-1',
        };
        const jobsEmptyA = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/jobs`, {
            headers: sessionHeadersA,
        }).then(r => r.json());
        assert.equal(jobsEmptyA.ok, true);
        assert.deepEqual(jobsEmptyA.jobs, []);

        const cancelRes = await fetch(`${base}/api/plugins/littlewhitebox-server/v1/cancel`, {
            method: 'POST',
            headers: sessionHeadersA,
            body: JSON.stringify({
                owner: 'assist-1_conv-1',
                jobIds: ['job-test-1'],
                runIds: ['run-test-1'],
            }),
        }).then(r => r.json());
        assert.equal(cancelRes.ok, true);
    } finally {
        proc.kill('SIGTERM');
    }
});

test('Production Bugs A, B, C: NavDisplay backstack safety, 7-stage SendDiagnosticRecord, IME scroll contention fix, and ChatService non-blocking send pipeline', () => {
    const navContextKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/context/NavContext.kt'),
        'utf8'
    );
    const routeActivityKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/RouteActivity.kt'),
        'utf8'
    );
    assert.ok(navContextKt.includes('Snapshot.withMutableSnapshot'));
    assert.ok(navContextKt.includes('fun ensureNonEmpty(fallback: NavKey? = null): NavKey'));
    assert.ok(navContextKt.includes('if (backStack.size > 1)'));
    assert.ok(routeActivityKt.includes('navController.ensureNonEmpty(startScreen)'));
    assert.ok(routeActivityKt.includes('onBack = { navController.popBackStack() }'));

    const imeScrollerKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/hooks/ImeAutoScroller.kt'),
        'utf8'
    );
    assert.ok(imeScrollerKt.includes('val delta = keyboardHeight - previousHeight'));
    assert.ok(imeScrollerKt.includes('if (keyboardHeight > 0 && delta > 0 && !lazyListState.isScrollInProgress && lazyListState.canScrollForward)'));

    const runtimeMgrKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'),
        'utf8'
    );
    for (const stage of [
        'STAGE_1_SEND_CLICKED',
        'STAGE_2_HANDLER_ENTERED',
        'STAGE_3_USER_MSG_PERSISTED',
        'STAGE_4_SCROLL_TRIGGERED',
        'STAGE_5_GENERATION_STARTED',
        'STAGE_6_MODEL_REQUEST_SENT',
        'STAGE_7_GENERATION_FINISHED',
    ]) {
        assert.ok(runtimeMgrKt.includes(stage), `Missing diagnostic stage ${stage}`);
    }

    const chatServiceKt = fs.readFileSync(
        path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/service/ChatService.kt'),
        'utf8'
    );
    assert.ok(chatServiceKt.includes('session.messageQueue.resume()'));
    assert.ok(chatServiceKt.includes('isExecutingSendTurn'));
    assert.ok(chatServiceKt.includes('checkFiles = false'));
});

test('Test 16: Real-Device APK 8 Issues, Quick Reply JSON Import/Export, BuiltinNodeCompanionServer & Hybrid Extension Settings', async () => {
  const chatServiceKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/service/ChatService.kt'), 'utf8');
  assert.ok(chatServiceKt.includes('session.isExecutingSendTurn = answer'), 'ChatService must set isExecutingSendTurn synchronously before launching generation job');
  assert.ok(chatServiceKt.includes('updateConversation(conversationId, newConversation, checkFiles = false)'), 'insertMessage (/send) must update conversation state synchronously without overwriting session.setJob');

  const stRegexedTextKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/StRegexedText.kt'), 'utf8');
  assert.ok(stRegexedTextKt.includes('fun scriptsUseDepthFilter'), 'StRegexTextCache must normalize depth when no active script uses depth filtering');
  assert.ok(stRegexedTextKt.includes('lastRenderedHolder'), 'rememberStRegexedTextWithStatus must preserve lastRenderedHolder to prevent WebView mass destruction on send');

  const msgHtmlKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt'), 'utf8');
  assert.ok(msgHtmlKt.includes('object ActivePanelWebViewPool'), 'MessageHtmlBlock must enforce ActivePanelWebViewPool LRU cap');
  assert.ok(msgHtmlKt.includes('MAX_ACTIVE_PANEL_WEBVIEWS = 3'), 'ActivePanelWebViewPool must cap active WebViews at 3');
  assert.ok(msgHtmlKt.includes('TavernRuntimeManager.isSendOrHookInFlight'), 'TavernPanelLoadScheduler must yield during send or generation hooks');

  const nodeMgrKt = fs.readFileSync(path.join(WORK_ROOT, 'workspace/src/main/java/me/rerere/workspace/NodeRuntimeManager.kt'), 'utf8');
  assert.ok(nodeMgrKt.includes('ensureRootfsInstalled('), 'NodeRuntimeManager must auto-provision Ubuntu rootfs when missing');
  assert.ok(nodeMgrKt.includes('preservedNodeDir'), 'ensureRootfsInstalled must preserve already-extracted /usr/local/node');

  const nodeSvcKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernNodeServiceManager.kt'), 'utf8');
  assert.ok(nodeSvcKt.includes('BuiltinNodeCompanionServer'), 'TavernNodeServiceManager must provide zero-config BuiltinNodeCompanionServer on 127.0.0.1:18181');

  const thirdPartyUiKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/ai/ThirdPartyExtensionsSection.kt'), 'utf8');
  assert.ok(thirdPartyUiKt.includes('fun NodeSetupTutorialDialog'), 'ThirdPartyExtensionsSection must include NodeSetupTutorialDialog');
  assert.ok(thirdPartyUiKt.includes('installAllFromZipUri'), 'ThirdPartyExtensionsSection must support multi-extension bundle ZIP import');

  const qmVmKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/QuickMessagesVM.kt'), 'utf8');
  assert.ok(qmVmKt.includes('fun importQuickMessagesFromJson'), 'QuickMessagesVM must support Quick Reply JSON import');
  assert.ok(qmVmKt.includes('fun exportQuickMessagesToJson'), 'QuickMessagesVM must support Quick Reply JSON export');

  const runtimeJs = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/runtime.js'), 'utf8');
  assert.ok(runtimeJs.includes('function rebindHostTavernHelperShims()'), 'runtime.js must rebind host RikkaBridge TavernHelper shims after JSR loads');
});

test('Test 17: Inline Character Sprite (立绘/CG) Screenshot Bug Fix & 3 End-to-End Audits Verification', async () => {
  const thinkKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ThinkTagTransformer.kt'), 'utf8');
  assert.ok(thinkKt.includes('<(think|thinking|thought)'), 'ThinkTagTransformer must extract <think>, <thinking>, and <thought> blocks');

  const cotKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageCot.kt'), 'utf8');
  assert.ok(cotKt.includes('THINKING_TAG_REGEX.find(part.text)'), 'ChatMessageCot.groupMessageParts must retroactively extract <thinking> blocks from historical messages');

  const regexEngineKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt'), 'utf8');
  assert.ok(regexEngineKt.includes('THINK_BLOCK_SHIELD_REGEX'), 'RegexScriptEngine must shield thinking blocks from non-thinking regexes');
  assert.ok(regexEngineKt.includes('val pairRegex = Regex'), 'RegexScriptEngine must re-anchor runaway cross-line backtick matches like `织乃_zcw_2`');

  const spriteRepoKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/expressions/SpriteRepository.kt'), 'utf8');
  assert.ok(spriteRepoKt.includes('fun resolveAnySpriteOrAssetFile'), 'SpriteRepository must resolve custom Chinese/action sprite names across extensions');
  assert.ok(spriteRepoKt.includes('writeSpriteByExactStem'), 'SpriteRepository.syncFromCardAssets must persist custom non-GoEmotions sprites');

  const trmKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'), 'utf8');
  assert.ok(trmKt.includes('fun resolveLocalCharacterAssetFile'), 'TavernRuntimeManager must provide unified local character asset & lazy tavernData.assets resolution');
  assert.ok(trmKt.includes('fun resolveLocalAssetForCoil'), 'TavernRuntimeManager must expose resolveLocalAssetForCoil for ZoomableAsyncImage');

  const pitKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PromptInjectionTransformer.kt'), 'utf8');
  assert.ok(pitKt.includes('enabledPromptInjectionIds'), 'PromptInjectionTransformer.transform must activate enabled global promptInjections');
  assert.ok(pitKt.includes('enabledGlobalLorebookIds'), 'PromptInjectionTransformer.transform must activate enabled global lorebooks');
});

test('Test 18: ST 1.19.0 macros/engine/* exports, ModuleWorkerWrapper, and Extension Settings UI isolation', async () => {
  const { pathToFileURL } = await import('node:url');
  const stCompatDir = path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/st-compat');
  const macroEngineMod = await import(pathToFileURL(path.join(stCompatDir, 'macros/engine/MacroEngine.js')).href);
  const macroFlagsMod = await import(pathToFileURL(path.join(stCompatDir, 'macros/engine/MacroFlags.js')).href);
  const macroLexerMod = await import(pathToFileURL(path.join(stCompatDir, 'macros/engine/MacroLexer.js')).href);
  const macroParserMod = await import(pathToFileURL(path.join(stCompatDir, 'macros/engine/MacroParser.js')).href);
  const slashParserMod = await import(pathToFileURL(path.join(stCompatDir, 'slash-commands/SlashCommandParser.js')).href);
  const simpleMutexMod = await import(pathToFileURL(path.join(stCompatDir, 'util/SimpleMutex.js')).href);

  assert.equal(typeof macroEngineMod.MacroEngine.evaluate, 'function');
  assert.equal(typeof macroFlagsMod.MacroFlags, 'object');
  assert.equal(typeof macroLexerMod.MacroLexer.tokenize, 'function');
  assert.equal(typeof macroParserMod.MacroParser.parseDocument, 'function');
  assert.equal(typeof slashParserMod.PARSER_FLAG, 'object');
  assert.equal(typeof simpleMutexMod.SimpleMutex, 'function');

  const extHtml = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/extension-settings.html'), 'utf8');
  const extCss = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/extension-settings.css'), 'utf8');
  const runtimeJs = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/runtime.js'), 'utf8');
  assert.ok(extHtml.includes('id="rikka-hidden-st-stubs"'), 'extension-settings.html must isolate #chat and #form_sheld');
  assert.ok(extCss.includes('#chat,') && extCss.includes('display: none !important;'), 'extension-settings.css must hide #chat');
  assert.ok(runtimeJs.includes('ensureExtensionSettingsUiMounted'), 'runtime.js must deterministically mount extension settings UI');
  assert.ok(runtimeJs.includes("action === 'open_stpt_modal'") && runtimeJs.includes("action === 'open_iphonie_app'"), 'runtime.js must wire all native chip actions');
});

test('Test 19: Problem 4 WebViewPerfMode (3 schemes + confirmation dialog + assistant edit lock) & Task 6 functional wiring', async () => {
  const prefKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt'), 'utf8');
  const cardKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/WebViewPerfReductionCard.kt'), 'utf8');
  const htmlBlockKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt'), 'utf8');
  const chatActionsKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt'), 'utf8');
  const slashHostKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatSlashHost.kt'), 'utf8');
  const tavernScriptsKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernScripts.kt'), 'utf8');

  assert.ok(prefKt.includes('enum class WebViewPerfMode'), 'PreferencesStore.kt must define WebViewPerfMode');
  assert.ok(prefKt.includes('HIBERNATE_POOL') && prefKt.includes('LIVE_QUIESCENT') && prefKt.includes('HISTORICAL_SNAPSHOT_IMAGE'));
  assert.ok(prefKt.includes('val enableWebViewPerfReduction: Boolean = false'));

  assert.ok(cardKt.includes('如果选择这个方案，你就不能修改助手的消息。'), 'Must show exact confirmation prompt for Scheme 3');
  assert.ok(cardKt.includes('Text("好的")') && cardKt.includes('Text("取消")'), 'Must provide 好的 and 取消 actions');

  assert.ok(htmlBlockKt.includes('object PanelSnapshotStore'), 'MessageHtmlBlock.kt must include PanelSnapshotStore for historical floor images');
  assert.ok(chatActionsKt.includes('isAssistantEditLocked'), 'ChatMessageActions.kt must lock assistant message editing under Scheme 3');
  assert.ok(slashHostKt.includes('qm.preventAutoExecute'), 'selectAutoTriggeredQuickReplies must honor preventAutoExecute');
  assert.ok(tavernScriptsKt.includes('!book.isCharacterBook || book.id in boundIds'), 'buildWorldNamesJson must isolate character lorebooks');
});

test('Test 20: Universal Floating Windows (ST-iPhonie + Card Floating), 2nd-Open IDB Cleanup, Interactive Extension Settings, Fast Frame-1 Regex & Offline CDN, Uniform Aspect-Ratio Snapshot & Dual-Channel TTS Isolation', async () => {
  const runtimeJs = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/runtime.js'), 'utf8');
  const powerUserJs = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/st-compat/power-user.js'), 'utf8');
  const extCss = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/assets/st-runtime/extension-settings.css'), 'utf8');
  const extPageKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/TavernExtensionSettingsPage.kt'), 'utf8');
  const floatOverlayKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/chat/TavernFloatingWindowOverlay.kt'), 'utf8');
  const trmKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt'), 'utf8');
  const stRegexKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/StRegexedText.kt'), 'utf8');
  const htmlBlockKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt'), 'utf8');
  const ttsKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/hooks/TTS.kt'), 'utf8');
  const ttsAutoKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/chat/TTSAutoPlay.kt'), 'utf8');
  const chatActionsKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt'), 'utf8');
  const chatPageKt = fs.readFileSync(path.join(WORK_ROOT, 'app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt'), 'utf8');

  // 1. Task 1: 2nd+ open white screen fix (IndexedDB handle tracking & cleanup + full-screen #sttts-panel + universal floating overlay)
  assert.ok(runtimeJs.includes('window.__rikkaOpenedIdbDatabases'), 'runtime.js must track opened IDBDatabase instances');
  assert.ok(runtimeJs.includes('window.__rikkaCleanupExtSettings'), 'runtime.js must provide __rikkaCleanupExtSettings to close IDB handles and call disable()');
  assert.ok(extPageKt.includes('window.__rikkaCleanupExtSettings()'), 'TavernExtensionSettingsPage must invoke __rikkaCleanupExtSettings before destroying WebView');
  assert.ok(extCss.includes('#sttts-panel') && extCss.includes('inset: 0 !important'), 'extension-settings.css must force #sttts-panel full-screen with no blurry offset');
  assert.ok(floatOverlayKt.includes('fun BoxScope.TavernFloatingWindowOverlay'), 'TavernFloatingWindowOverlay must provide resident right-edge floating dock');
  assert.ok(floatOverlayKt.includes('card_floating'), 'TavernFloatingWindowModal must support character card custom floating windows');
  assert.ok(chatPageKt.includes('TavernFloatingWindowOverlay'), 'ChatPage must mount TavernFloatingWindowOverlay');
  assert.ok(trmKt.includes('fun attachHostWebViewToContainer') && trmKt.includes('fun detachHostWebViewFromContainer'));

  // 2. Task 1b: Dual-channel TTS isolation
  assert.ok(ttsKt.includes('TavernRuntimeManager.nativeTtsStopper') && ttsKt.includes('TavernRuntimeManager.stopStIphonieTts("native_tts_speak")'));
  assert.ok(ttsKt.includes('stripStIphonieProtocolTagsForNativeTts'));
  assert.ok(ttsAutoKt.includes('!TavernRuntimeManager.isStIphonieSpeaking'));
  assert.ok(chatActionsKt.includes('TavernRuntimeManager.playFloorWithStIphonie'));

  // 3. Task 2: Direct interactive settings for st-memory-enhancement, ST-iPhonie, LittleWhiteBox, ST-Prompt-Template & JS-Slash-Runner
  assert.ok(runtimeJs.includes('function mountMemoryEnhancementQuickSettings()'), 'runtime.js must mount interactive st-memory-enhancement settings card');
  assert.ok(runtimeJs.includes('function mountStIphonieQuickSettings()'), 'runtime.js must mount interactive ST-iPhonie settings card bound to stTtsDialogue');
  assert.ok(runtimeJs.includes('function mountLittleWhiteBoxQuickSettings()'), 'runtime.js must mount interactive LittleWhiteBox settings card');
  assert.ok(runtimeJs.includes('easeInOutCubic'), 'runtime.js must polyfill jQuery.easing.easeInOutCubic for st-memory-enhancement');
  assert.ok(powerUserJs.includes('syncPowerUserFromExtensionSettings') && powerUserJs.includes('muyoo_dataTable'));
  assert.ok(runtimeJs.includes('function deepMergeInPlace(target, source)'), 'pullThirdPartySettings must merge in-place to preserve object references');

  // 4. Task 3: Frame-1 synchronous Kotlin regex & 0ms offline interception for Google Fonts / FontAwesome / api.muyoo.com.cn
  assert.ok(stRegexKt.includes('fun shouldInvokeWebViewMessageFormatter') && stRegexKt.includes('fun computePureKotlinRegexedText'));
  assert.ok(runtimeJs.includes('muyoo') && runtimeJs.includes('googleapis'));
  assert.ok(trmKt.includes('api.muyoo.com.cn') && trmKt.includes('fonts.googleapis.com'));

  // 5. Task 4: Uniform-scale snapshot capture (.aspectRatio) & DetachedPanelWebViewCache
  assert.ok(htmlBlockKt.includes('val uniformScale = minOf(1f, maxDimW / w.toFloat(), maxDimH / h.toFloat())'));
  assert.ok(htmlBlockKt.includes('.aspectRatio(safeAspectRatio)'));
  assert.ok(htmlBlockKt.includes('object DetachedPanelWebViewCache'));
});
