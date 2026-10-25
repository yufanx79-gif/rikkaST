import {
  loadPreset,
  buildAppLikeMessages,
  oldSquashPrompts,
  createPostProcessor,
  proposedConditionalBlockMerge,
  createSpresetHarness,
  formParametersToJsonSchema,
} from './lib/spreset.mjs';

const preset = loadPreset();
const { spreset } = preset;
const chatSquash = spreset.ChatSquash;
const userName = '小明';
const charName = '小猫之神';

function summarizeMessages(label, messages) {
  console.log(`\n=== ${label} (${messages.length}) ===`);
  messages.forEach((m, i) => {
    const text = typeof m.content === 'string' ? m.content : JSON.stringify(m.content);
    console.log(`${i}: role=${m.role} len=${text.length} start=${JSON.stringify(text.slice(0, 80))} end=${JSON.stringify(text.slice(-80))}`);
  });
}

const { messages: appMessages, meta } = buildAppLikeMessages(preset, { userName, charName });
console.log('app-like meta:', JSON.stringify(meta));
console.log('first system contains marker:', String(appMessages[0].content).includes('<run_tool_replacement>'));
console.log('first system contains merge tag:', String(appMessages[0].content).includes('<|merge|>'));
console.log('first system contains sep:', String(appMessages[0].content).includes('<|sep|>'));
summarizeMessages('app-like messages BEFORE', appMessages);

const postLogs = [];
const { postProcess, compileError } = createPostProcessor(chatSquash, { userName, charName, logs: postLogs });
if (compileError) console.log('POST COMPILE ERROR', compileError.message);

const oldOutput = oldSquashPrompts(appMessages, chatSquash, { userName, charName, postProcess });
summarizeMessages('old SPreset 1.0 squashPrompts output', oldOutput);

const oldReq = { messages: oldOutput, tools: [] };
const harness = createSpresetHarness(preset);
try {
  const result = await harness.processFinalRequest(structuredClone(oldReq));
  console.log('\nold squash + FinalRequest: OK, messages=', result.messages.length);
} catch (e) {
  console.log('\nold squash + FinalRequest: THREW:', e.message);
}

const proposed = proposedConditionalBlockMerge(appMessages, chatSquash, { userName, charName, postProcess });
console.log('\nproposed changed=', proposed.changed, 'reason=', proposed.reason, 'blockRoles=', proposed.blockRoles.join(','));
console.log('rawMergedLength=', proposed.rawMergedLength, 'assistantLength=', proposed.assistantLength);
summarizeMessages('proposed conditional block merge output', proposed.messages);
if (proposed.changed) {
  const content = proposed.messages[1].content;
  console.log('\nPOST SCRIPT OUTPUT start:', JSON.stringify(content.slice(0, 300)));
  console.log('POST SCRIPT OUTPUT end:', JSON.stringify(content.slice(-300)));
  console.log('contains cat marker:', content.includes('|小猫之神|'));
  console.log('contains user marker:', content.includes('|用户|'));
  console.log('contains user prefix:', content.includes('|小明|'));
  console.log('contains char prefix:', content.includes('|游戏剧情|'));
  console.log('contains soliumbra role:', content.includes('"role": "soliumbra"'));
  console.log('contains username role:', content.includes('"role": "小明"'));
  console.log('contains default user role:', content.includes('"role": "用户"'));
  console.log('contains merge tag:', content.includes('<|merge|>'));
  console.log('contains sep tag:', content.includes('<|sep|>'));

  const request = {
    messages: structuredClone(proposed.messages),
    tools: [{
      type: 'function',
      function: {
        name: 'game_content',
        description: 'x',
        parameters: { type: 'object', properties: {} },
      },
    }],
  };
  console.log('\nrequest before FinalRequest:', JSON.stringify({
    messages: request.messages.map((m) => ({ role: m.role, len: String(m.content).length })),
    tools: request.tools.map((t) => t.function.name),
  }));
  try {
    const result = await harness.processFinalRequest(request);
    console.log('proposed + FinalRequest: OK');
    console.log('messages after:', JSON.stringify(result.messages.map((m) => ({ role: m.role, len: String(m.content).length, start: String(m.content).slice(0, 80), end: String(m.content).slice(-80) })), null, 2));
    console.log('tools after:', JSON.stringify(result.tools.map((t) => ({ name: t.function?.name, descriptionLen: t.function?.description?.length, descriptionStart: t.function?.description?.slice(0, 120), descriptionEnd: t.function?.description?.slice(-120), parameters: t.function?.parameters })), null, 2));
  } catch (e) {
    console.log('proposed + FinalRequest: THREW:', e.message);
  }
}

// First-turn edge case: no prior assistant, AFTER_DIALOG injections land after the last user.
const firstTurn = buildAppLikeMessages(preset, {
  userName,
  charName,
  history: [{ role: 'user', content: '|用户|你好呀' }],
});
summarizeMessages('first-turn app-like messages', firstTurn.messages);
const firstProposed = proposedConditionalBlockMerge(firstTurn.messages, chatSquash, { userName, charName, postProcess });
summarizeMessages('first-turn proposed output', firstProposed.messages);
const firstRequest = { messages: structuredClone(firstProposed.messages), tools: [] };
try {
  const result = await harness.processFinalRequest(firstRequest);
  console.log('first-turn proposed + FinalRequest: OK, messages=', result.messages.length);
} catch (e) {
  console.log('first-turn proposed + FinalRequest: THREW:', e.message);
}

// Show form->schema conversion for enabled tool.
for (const [id, b] of Object.entries(spreset.ToolBindings ?? {})) {
  console.log('\nToolBinding', b.resolvedName, 'enabled=', b.enabled);
  console.log(JSON.stringify(formParametersToJsonSchema(b), null, 2));
}
