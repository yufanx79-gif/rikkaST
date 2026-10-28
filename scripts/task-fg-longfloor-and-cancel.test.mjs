import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const REPO_ROOT = process.cwd();

function readRepoFile(relPath) {
  return fs.readFileSync(path.join(REPO_ROOT, relPath), 'utf8');
}

/**
 * Parses TavernChatJson.kt's NodeCacheKey declaration and getOrBuildNodeJson key construction
 * so the test exercises the actual production key fields declared in TavernChatJson.kt.
 */
function buildProductionCacheSimulatorFromKotlinSource(ktSource) {
  const keyBlockMatch = ktSource.match(/data class NodeCacheKey\(([\s\S]*?)\)/);
  assert.ok(keyBlockMatch, 'TavernChatJson.kt must declare data class NodeCacheKey');
  const keyFields = Array.from(keyBlockMatch[1].matchAll(/val\s+([A-Za-z0-9_]+)\s*:/g)).map((m) => m[1]);

  const map = new Map();
  return {
    keyFields,
    getOrBuildNodeJson({ index, node, current, speakerName, alternateGreetings }) {
      const currentText = current.text;
      const isUser = current.role === 'USER';
      const effectiveAltCount = index === 0 && !isUser && node.messages.length === 1 ? alternateGreetings.length : 0;
      const swipesList =
        effectiveAltCount > 0
          ? [currentText, ...alternateGreetings]
          : node.messages.length === 1
          ? [currentText]
          : node.messages.map((m) => m.text);
      const sendDate = String(current.createdAt ?? '2026-10-22T00:00:00Z');

      // Java 32-bit String.hashCode()
      const jvmHash = (s) => {
        let h = 0;
        for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
        return h;
      };

      const fieldValues = {
        nodeId: String(node.id),
        index,
        selectIndex: node.selectIndex,
        messagesSize: node.messages.length,
        currentMsgId: String(current.id),
        currentTextHash: jvmHash(currentText),
        currentTextLen: currentText.length,
        currentText,
        swipes: swipesList,
        sendDate,
        isUser,
        isHidden: Boolean(current.isHidden),
        speakerName,
        altGreetingCount: effectiveAltCount,
      };

      const keyObj = {};
      for (const f of keyFields) {
        keyObj[f] = fieldValues[f];
      }
      const serializedKey = JSON.stringify(keyObj);
      if (map.has(serializedKey)) {
        return { json: map.get(serializedKey), hit: true };
      }
      const built = JSON.stringify({
        message_id: index,
        name: speakerName,
        is_user: isUser,
        is_system: Boolean(current.isHidden),
        send_date: sendDate,
        mes: currentText,
        swipe_id: node.selectIndex,
        swipes: swipesList,
        extra: {},
      });
      map.set(serializedKey, built);
      return { json: built, hit: false };
    },
  };
}

test('Task F (F-04): TavernChatJsonCache.NodeCacheKey invalidates on non-selected swipe edit, alternateGreeting edit, sendDate/isUser change, and resists 32-bit hash collisions', () => {
  const tavernChatJsonKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernChatJson.kt');
  const sim = buildProductionCacheSimulatorFromKotlinSource(tavernChatJsonKt);

  // 1. Editing a NON-SELECTED swipe (selectIndex=0 unchanged, swipe[1] text edited) MUST invalidate cache and emit updated swipes[]
  const nodeV1 = {
    id: 'node-0',
    selectIndex: 0,
    messages: [
      { id: 'msg-0a', role: 'ASSISTANT', text: 'Selected greeting v1', isHidden: false, createdAt: '2026-10-22T00:00:00Z' },
      { id: 'msg-0b', role: 'ASSISTANT', text: 'Alternate swipe BEFORE edit', isHidden: false, createdAt: '2026-10-22T00:01:00Z' },
    ],
  };
  const res1 = sim.getOrBuildNodeJson({
    index: 0,
    node: nodeV1,
    current: nodeV1.messages[0],
    speakerName: 'Aqua',
    alternateGreetings: [],
  });
  assert.equal(res1.hit, false);
  assert.deepEqual(JSON.parse(res1.json).swipes, ['Selected greeting v1', 'Alternate swipe BEFORE edit']);

  // Repeat identical call -> cache hit
  const res1Hit = sim.getOrBuildNodeJson({
    index: 0,
    node: nodeV1,
    current: nodeV1.messages[0],
    speakerName: 'Aqua',
    alternateGreetings: [],
  });
  assert.equal(res1Hit.hit, true);

  // Mutate non-selected swipe (messages[1]) while messages[0] and selectIndex=0 remain untouched
  const nodeV2 = {
    ...nodeV1,
    messages: [
      nodeV1.messages[0],
      { ...nodeV1.messages[1], text: 'Alternate swipe AFTER edit' },
    ],
  };
  const res2 = sim.getOrBuildNodeJson({
    index: 0,
    node: nodeV2,
    current: nodeV2.messages[0],
    speakerName: 'Aqua',
    alternateGreetings: [],
  });
  assert.equal(res2.hit, false, 'Editing a non-selected swipe must miss cache');
  assert.deepEqual(
    JSON.parse(res2.json).swipes,
    ['Selected greeting v1', 'Alternate swipe AFTER edit'],
    'Cached JSON must not return stale non-selected swipe text'
  );

  // 2. Editing an alternateGreeting text without changing alternateGreetings.length MUST invalidate cache
  const singleMsgNode = {
    id: 'node-greeting',
    selectIndex: 0,
    messages: [
      { id: 'msg-g0', role: 'ASSISTANT', text: 'First greeting', isHidden: false, createdAt: '2026-10-22T00:00:00Z' },
    ],
  };
  sim.getOrBuildNodeJson({
    index: 0,
    node: singleMsgNode,
    current: singleMsgNode.messages[0],
    speakerName: 'Aqua',
    alternateGreetings: ['Alt 1 original', 'Alt 2 original'],
  });
  const altEditedRes = sim.getOrBuildNodeJson({
    index: 0,
    node: singleMsgNode,
    current: singleMsgNode.messages[0],
    speakerName: 'Aqua',
    alternateGreetings: ['Alt 1 original', 'Alt 2 EDITED'],
  });
  assert.equal(altEditedRes.hit, false, 'Editing alternateGreeting text with same count must miss cache');
  assert.deepEqual(JSON.parse(altEditedRes.json).swipes, ['First greeting', 'Alt 1 original', 'Alt 2 EDITED']);

  // 3. 32-bit JVM String.hashCode() + length collision ("Aa" and "BB" both have hashCode=2112 and length=2) MUST NOT return stale mes
  const collisionNodeA = {
    id: 'node-col',
    selectIndex: 0,
    messages: [{ id: 'm-col', role: 'USER', text: 'Aa', isHidden: false, createdAt: '2026-10-22T00:00:00Z' }],
  };
  const collisionNodeB = {
    id: 'node-col',
    selectIndex: 0,
    messages: [{ id: 'm-col', role: 'USER', text: 'BB', isHidden: false, createdAt: '2026-10-22T00:00:00Z' }],
  };
  sim.getOrBuildNodeJson({
    index: 1,
    node: collisionNodeA,
    current: collisionNodeA.messages[0],
    speakerName: 'User',
    alternateGreetings: [],
  });
  const colResB = sim.getOrBuildNodeJson({
    index: 1,
    node: collisionNodeB,
    current: collisionNodeB.messages[0],
    speakerName: 'User',
    alternateGreetings: [],
  });
  assert.equal(colResB.hit, false, '32-bit hashCode collision ("Aa" vs "BB") must not cause false cache hit');
  assert.equal(JSON.parse(colResB.json).mes, 'BB');

  // 4. send_date (createdAt) mutation MUST invalidate cache
  const dateUpdatedNode = {
    ...collisionNodeB,
    messages: [{ ...collisionNodeB.messages[0], createdAt: '2026-10-22T05:00:00Z' }],
  };
  const dateRes = sim.getOrBuildNodeJson({
    index: 1,
    node: dateUpdatedNode,
    current: dateUpdatedNode.messages[0],
    speakerName: 'User',
    alternateGreetings: [],
  });
  assert.equal(dateRes.hit, false, 'Changing createdAt (send_date) must invalidate cache');
  assert.equal(JSON.parse(dateRes.json).send_date, '2026-10-22T05:00:00Z');
});

test('Task F & Domain B (F-06b): MessageHtmlBlock bounds active WebViews by default, evicts beyond pool limit across all modes, gates disk snapshot load on useHistoricalSnapshot, releases on conversation switch, cancels pending ready probes, and flushes coalesced streaming updates', () => {
  const messageHtmlKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt');
  const runtimeMgrKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt');

  // 1. Default safety cap (6) is active even when enableWebViewPerfReduction is false
  assert.ok(
    messageHtmlKt.includes('val useBoundedWebViewPool = true') &&
      messageHtmlKt.includes('DEFAULT_SAFE_MAX_ACTIVE_WEBVIEWS = 6') &&
      messageHtmlKt.includes('fun acquire(key: String, maxActive: Int = MAX_ACTIVE_PANEL_WEBVIEWS)'),
    'ActivePanelWebViewPool must enforce a safe default cap (6) even when perf reduction is off and accept mode-specific maxActive'
  );

  // 2. Evicted keys (!in activePoolKeys) must hibernate/release across all modes when pool cap is exceeded
  //
  // 契约更新说明（v2.5.10-public-20261027+）：
  // 旧断言要求表达式里保留 `useHibernatePool || (useBoundedWebViewPool && isHistoricalFloor)`
  // 的历史楼层限定。但在该版本中「活跃池之外一律休眠」是有意为之的改动
  // （见 MessageHtmlBlock.kt 的注释：确保"活跃 WebView 数量上限"是真实上限，
  //  不再只对历史楼层生效）。因此这里改为断言**语义**：
  //   ① isHibernated 由「useBoundedWebViewPool && slotGranted && key !in activePoolKeys」决定；
  //   ② onRelease 在被淘汰时走 evictAndDestroy + release（而不是 parkContainer 保活）。
  assert.ok(
    /val isHibernated = useBoundedWebViewPool && slotGranted && effectiveQueueKey !in activePoolKeys/.test(messageHtmlKt) &&
      messageHtmlKt.includes('if (useBoundedWebViewPool && effectiveQueueKey !in activePoolKeys)') &&
      messageHtmlKt.includes('DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)'),
    'MessageHtmlBlock must hibernate every panel outside the bounded active pool and release evicted WebViews in onRelease'
  );

  // 3. Disk snapshot loading is gated on useHistoricalSnapshot, and snapshot capture releases historical pool slot
  assert.ok(
    messageHtmlKt.includes('LaunchedEffect(snapshotCompositeKey, useHistoricalSnapshot)') &&
      messageHtmlKt.includes('if (useHistoricalSnapshot && snapshotEntry == null && snapshotCompositeKey != null)') &&
      messageHtmlKt.includes('ActivePanelWebViewPool.release(effectiveQueueKey)'),
    'MessageHtmlBlock must gate disk snapshot loading on useHistoricalSnapshot and release historical WebView slot after capture'
  );

  // 4. TavernPanelLoadScheduler.cancel cancels gate, PanelMountState cancels pending ready probe & flushes coalesced streaming updates, and switching conversation clears old panels
  assert.ok(
    messageHtmlKt.includes('removed.gate.cancel()') &&
      messageHtmlKt.includes('fun scheduleReadyProbe(') &&
      messageHtmlKt.includes('fun cancelReadyProbe()') &&
      messageHtmlKt.includes('fun flushPendingRemount()') &&
      runtimeMgrKt.includes('ActivePanelWebViewPool.clearAll()') &&
      runtimeMgrKt.includes('DetachedPanelWebViewCache.clearAll()'),
    'Scheduler cancel must cancel gate, PanelMountState must manage ready-probe & streaming flush callbacks, and setActiveConversation must clear old conversation panels'
  );
});

test('Task G (F-07): ConversationSession, ChatService, GenerationLoop & runtime.js preserve generationEpoch isolation, rethrow CancellationException, prevent stopGeneration from clobbering concurrent resend, and abort JS hooks across stages', async () => {
  const convSessionKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/service/ConversationSession.kt');
  const chatServiceKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/service/ChatService.kt');
  const genLoopKt = readRepoFile('app/src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt');
  const runtimeJs = readRepoFile('app/src/main/assets/st-runtime/runtime.js');

  // 1. ConversationSession tracks atomic generationEpoch and only bumps epoch on setJob when job != null || cancelPrevious
  assert.ok(
    convSessionKt.includes('generationEpoch') &&
      convSessionKt.includes('fun currentGenerationEpoch(): Long') &&
      convSessionKt.includes('if (job != null || cancelPrevious) {'),
    'ConversationSession must track generationEpoch and avoid bumping epoch on post-join setJob(null, cancelPrevious = false)'
  );

  // 2. ChatService.handleMessageComplete checks both ensureActive() and myGenerationEpoch in .collect, and fences .onCompletion by myGenerationEpoch
  assert.ok(
    chatServiceKt.includes('val myGenerationEpoch = session.currentGenerationEpoch()') &&
      chatServiceKt.includes('currentCoroutineContext().ensureActive()') &&
      chatServiceKt.includes('if (session.currentGenerationEpoch() != myGenerationEpoch) return@collect') &&
      chatServiceKt.includes('if (session.currentGenerationEpoch() == myGenerationEpoch)'),
    'ChatService.handleMessageComplete must check ensureActive() and fence both .collect and .onCompletion by myGenerationEpoch'
  );

  // 3. ChatService.stopGeneration does not clobber a newly started resend job after waiting for cancelled jobs to join
  assert.ok(
    !chatServiceKt.includes('synchronized(session) {\n            session.isExecutingSendTurn = false\n            session.submittingMessage = null\n            session.setJob(null)\n        }'),
    'ChatService.stopGeneration must not unconditionally call session.setJob(null) after join()'
  );
  assert.ok(
    chatServiceKt.includes('if (activeJob == null || activeJob in jobs)') &&
      chatServiceKt.includes('session.setJob(null, cancelPrevious = false)'),
    'ChatService.stopGeneration must guard post-join cleanup with (activeJob == null || activeJob in jobs) and use setJob(null, cancelPrevious = false)'
  );

  // 4. quietGenerate and all generation entrypoints rethrow CancellationException
  assert.ok(
    /suspend fun quietGenerate[\s\S]*?onFailure\s*\{\s*if\s*\(it is CancellationException\)\s*throw it\s*\}/m.test(chatServiceKt),
    'ChatService.quietGenerate (suspend) must rethrow CancellationException instead of swallowing it in runCatching'
  );
  const cancelRethrowMatches = chatServiceKt.match(/if \((?:e|it) is CancellationException\)[\s\S]{0,300}?throw (?:e|it)/g) || [];
  assert.ok(
    cancelRethrowMatches.length >= 6,
    `ChatService.kt must rethrow CancellationException across all generation entrypoints (found ${cancelRethrowMatches.length})`
  );

  // 5. GenerationLoop.awaitNetworkRetryOrThrow converts canceled IOException to CancellationException and never retries it
  assert.ok(
    genLoopKt.includes('if (error is CancellationException) throw error') &&
      genLoopKt.includes('error.message?.contains("cancel", ignoreCase = true) == true') &&
      genLoopKt.includes('throw CancellationException("Network call canceled", error)'),
    'GenerationLoop.awaitNetworkRetryOrThrow must convert canceled IOExceptions into CancellationException rather than retrying'
  );

  // 6. runtime.js __rikkaAbortActiveGeneration and __rikkaRunGenerationHooks epoch checks
  assert.ok(
    runtimeJs.includes('(window.__rikkaGenerationAbortEpoch || 0) !== startEpoch') &&
      runtimeJs.includes('ctx.externalAbortController.abort(') &&
      runtimeJs.includes('window.__rikkaProxyPending') &&
      runtimeJs.includes('window.__rikkaImgGenPending'),
    'runtime.js must abort externalAbortController, pending proxy/imgGen requests, and check startEpoch across hook stages'
  );

  // 7. Execute actual __rikkaAbortActiveGeneration and __rikkaRunGenerationHooks from runtime.js in VM context
  const vmMod = await import('node:vm');
  const startIdx = runtimeJs.indexOf('window.__rikkaAbortActiveGeneration = function');
  const endIdx = runtimeJs.indexOf('window.__rikkaEmit = function');
  const hookSnippet = runtimeJs.slice(startIdx, endIdx);
  const stagesExecuted = [];
  let externalAbortedReason = null;
  let proxyAborted = false;
  let imgGenAborted = false;
  const sandbox = {
    window: {
      __rikkaGenerationAbortEpoch: 0,
      __rikkaApiStreams: { s1: { finished: false, status: 'OPEN', wakeCalled: false, wake() { this.wakeCalled = true; } } },
      __rikkaProxyPending: { p1: { resolve(val) { if (val && val.aborted) proxyAborted = true; } } },
      __rikkaImgGenPending: { i1: { resolve(val) { if (val && val.aborted) imgGenAborted = true; } } },
      SillyTavern: {
        getContext() {
          return {
            externalAbortController: {
              abort(r) { externalAbortedReason = r; },
            },
          };
        },
      },
      RikkaBridge: { onGenerationHooksResult() {} },
    },
    state: { chatId: 'conv-1' },
    event_types: {
      GENERATION_AFTER_COMMANDS: 'generation_after_commands',
      GENERATE_BEFORE_COMBINE_PROMPTS: 'generate_before_combine_prompts',
      GENERATE_AFTER_DATA: 'generate_after_data',
      CHAT_COMPLETION_PROMPT_READY: 'chat_completion_prompt_ready',
      CHAT_COMPLETION_SETTINGS_READY: 'chat_completion_settings_ready',
      GENERATE_AFTER_COMBINE_PROMPTS: 'generate_after_combine_prompts',
    },
    runGenerationInterceptors: async () => {
      stagesExecuted.push('interceptors');
      return { aborted: false };
    },
    callBridge: () => {},
    console,
    eventSource: {
      emit() {},
      async emitAndWait(evt) {
        stagesExecuted.push(evt);
        if (evt === 'generate_before_combine_prompts') {
          // Simulate user clicking Stop while GENERATE_BEFORE_COMBINE_PROMPTS async plugin hook is awaiting
          sandbox.window.__rikkaAbortActiveGeneration('user_stop');
        }
      },
    },
  };
  vmMod.runInNewContext(hookSnippet, sandbox);
  const hookResultJson = await sandbox.window.__rikkaRunGenerationHooks(1, JSON.stringify([{ role: 'user', content: 'Hello' }]));
  const parsedHookResult = JSON.parse(hookResultJson);
  assert.equal(parsedHookResult.aborted, true, 'Aborting during GENERATE_BEFORE_COMBINE_PROMPTS must mark hook result as aborted');
  assert.equal(externalAbortedReason, 'user_stop', 'externalAbortController must receive abort reason');
  assert.equal(proxyAborted, true, 'Pending proxy request must resolve with { aborted: true }');
  assert.equal(imgGenAborted, true, 'Pending image generation request must resolve with { aborted: true }');
  assert.deepEqual(
    stagesExecuted,
    ['interceptors', 'generation_after_commands', 'generate_before_combine_prompts'],
    'Subsequent hook stages after abort epoch change must be skipped'
  );
});
