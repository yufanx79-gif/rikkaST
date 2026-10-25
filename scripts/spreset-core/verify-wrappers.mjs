#!/usr/bin/env node
/**
 * 校验 Kotlin 侧 QuickJS 包装模板（与 SpresetJsRuntime.kt / SpresetCompat.kt 中的
 * 字符串模板保持一致），确保 JSON 字符串注入、await Promise、new Function(code)()
 * 与 call.arguments 解析对象等语义在 Node 下也能跑通。
 *
 * 用法：node scripts/spreset-core/verify-wrappers.mjs [preset.json]
 */
import fs from 'node:fs';
import assert from 'node:assert/strict';

const presetPath = process.argv[2] || 'E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json';
const root = JSON.parse(fs.readFileSync(presetPath, 'utf8').replace(/^\uFEFF/, ''));
const sp = root.extensions.SPreset;

// 与 Kotlin JsonPrimitive(String).toString() 等价的字符串字面量编码。
const literal = (value) => JSON.stringify(value);
const report = (message) => process.stdout.write(message + '\n');

function installConsole() {
  const c = globalThis.console || (globalThis.console = {});
  for (const level of ['log', 'info', 'warn', 'error', 'debug', 'trace']) {
    c[level] = function () {};
  }
}

function evalJs(code) {
  return eval(code);
}

async function installFunction(name, script) {
  const nameLiteral = literal(name);
  const scriptLiteral = literal(script);
  evalJs(`
    (() => {
      const fn = (0, eval)(${scriptLiteral});
      if (typeof fn !== 'function') {
        throw new Error('SPreset script did not evaluate to a function');
      }
      globalThis[${nameLiteral}] = fn;
      void 0;
    })()
  `);
}

async function callFunction(name, argsJson) {
  const nameLiteral = literal(name);
  const argsLiteral = literal(argsJson);
  return evalJs(`
    (async () => {
      const args = JSON.parse(${argsLiteral});
      const fn = globalThis[${nameLiteral}];
      if (typeof fn !== 'function') {
        throw new Error('SPreset function is not installed');
      }
      const value = await fn(args);
      if (value === undefined || value === null) return null;
      if (typeof value === 'string') return value;
      return JSON.stringify(value);
    })()
  `);
}

async function callFunctionWithStringArg(name, argument) {
  const nameLiteral = literal(name);
  const argumentLiteral = literal(argument);
  return evalJs(`
    (async () => {
      const fn = globalThis[${nameLiteral}];
      if (typeof fn !== 'function') {
        throw new Error('SPreset function is not installed');
      }
      const value = await fn(${argumentLiteral});
      if (value === undefined || value === null) return null;
      if (typeof value === 'string') return value;
      return JSON.stringify(value);
    })()
  `);
}

function toolActionScript(binding, argsJson) {
  const argsLiteral = literal(argsJson);
  const resultHandling = `
    if (value === undefined || value === null) return null;
    if (typeof value === 'string') return value;
    return JSON.stringify(value);
  `;
  if (binding.code) {
    return `
      (async () => {
        const args = JSON.parse(${argsLiteral});
        const tool = new Function(${literal(binding.code)})();
        if (!tool || typeof tool.action !== 'function') {
          throw new Error('SPreset tool action is not a function');
        }
        const value = await tool.action(args);
        ${resultHandling}
      })()
    `;
  }
  return `
    (async () => {
      const args = JSON.parse(${argsLiteral});
      const fn = new Function('args', ${literal(binding.actionBody)});
      const value = await fn(args);
      ${resultHandling}
    })()
  `;
}

function resolveDefinitionScript(binding) {
  return `
    (() => {
      const tool = new Function(${literal(binding.code)})();
      if (!tool || typeof tool !== 'object') {
        throw new Error('SPreset tool code did not return an object');
      }
      return JSON.stringify({
        name: tool.name ?? null,
        description: tool.description ?? null,
        parameters: tool.parameters ?? null
      });
    })()
  `;
}

installConsole();

// 1) OutputPreprocessing 安装 + args 对象调用
await installFunction('__out', sp.OutputPreprocessing.script);
const outputRaw = await callFunction('__out', JSON.stringify({
  buffer: 'hello',
  final: false,
  state: {},
}));
const output = JSON.parse(outputRaw);
assert.equal(output.output, 'hello');
assert.equal(output.state.accepted, true);
report('✓ installFunction/callFunction：OutputPreprocessing 对象参数与 Promise 返回');

// 2) toolCallFormatter：call.arguments 必须是对象
await installFunction('__fmt', sp.OutputPreprocessing.toolCallFormatter.script);
const callsJson = JSON.stringify({
  calls: [
    { name: 'game_content', arguments: { content: '你好' } },
    { name: 'soliumbra_think', arguments: { thinking: '想一下' } },
  ],
});
const formatted = await callFunction('__fmt', callsJson);
assert.match(formatted, /你好/);
assert.match(formatted, /think_nya~/);
assert.match(formatted, /想一下/);
report('✓ callFunction：toolCallFormatter 收到解析后的 arguments 对象');

// 3) FinalRequestProcessing
await installFunction('__final', sp.FinalRequestProcessing.script);
const request = {
  messages: [
    { role: 'system', content: 'prefix\n<run_tool_replacement>' },
    { role: 'assistant', content: '|小猫之神|hello' },
    { role: 'user', content: '继续' },
  ],
  tools: [{ type: 'function', function: { name: 'game_content', description: 'x', parameters: { type: 'object', properties: {} } } }],
};
const finalRaw = await callFunction('__final', JSON.stringify({ request }));
const finalResult = JSON.parse(finalRaw);
assert.equal(finalResult.messages.length, 2);
assert.equal(finalResult.tools.at(-1).function.name, 'context');
report('✓ callFunction：FinalRequestProcessing 3→2 条并追加 context');

// 4) Tool action：new Function(code)() + await action(args)
const enabledTool = Object.values(sp.ToolBindings).find((item) => item.enabled);
const actionRaw = await evalJs(toolActionScript(enabledTool, JSON.stringify({ content: '你好' })));
assert.equal(actionRaw, JSON.stringify({ ok: true }));
report('✓ executeToolAction 模板：new Function(code)() 与 action(args) 返回 JSON');

// 5) 缺失 code 时 new Function('args', actionBody)
const fallbackRaw = await evalJs(toolActionScript({ code: '', actionBody: 'return JSON.stringify({ args });' }, JSON.stringify({ a: 1 })));
assert.deepEqual(JSON.parse(fallbackRaw), { args: { a: 1 } });
report('✓ executeToolAction fallback：new Function(args, actionBody)');

// 6) resolveToolDefinition 模板
const definition = JSON.parse(await evalJs(resolveDefinitionScript(enabledTool)));
assert.equal(definition.name, 'game_content');
assert.deepEqual(definition.parameters.required, ['content']);
report('✓ resolveToolDefinition 模板：code 解析 name/description/parameters');

// 7) ChatSquash squashed_post_script：字符串参数版本
await installFunction('__post', sp.ChatSquash.squashed_post_script);
const postText = await callFunctionWithStringArg('__post', '|用户：小明|\n|小猫之神|喵~\n|用户|你好');
assert.match(postText, /"role": "soliumbra"/);
assert.match(postText, /"role": "小明"/);
report('✓ callFunctionWithStringArg：squashed_post_script 字符串参数');

report('\n全部 QuickJS 包装模板 Node 自测通过。');
