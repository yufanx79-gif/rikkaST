// 复刻 verify_th_mocks 的关键部分
const noop = () => {};
const chain = new Proxy(function(){}, { get: (t,p) => p === Symbol.toPrimitive ? () => '' : chain, apply: () => chain });
globalThis.$ = chain; globalThis.jQuery = chain; globalThis.window = globalThis;
globalThis.document = { createElement: () => ({ style:{}, setAttribute:noop, appendChild:noop, classList:{add:noop,remove:noop} }), querySelector: () => null, querySelectorAll: () => [], addEventListener: noop, body:{appendChild:noop}, head:{appendChild:noop}, getElementById: () => null };
globalThis.localStorage = { getItem: () => null, setItem: noop, removeItem: noop };
globalThis.toastr = { info:noop, warning:noop, error:noop, success:noop };
globalThis.addEventListener = noop; globalThis.removeEventListener = noop;
globalThis.matchMedia = () => ({ matches:false, addEventListener:noop, removeEventListener:noop });
globalThis.getComputedStyle = () => ({ getPropertyValue: () => '' });
globalThis.requestAnimationFrame = (cb) => setTimeout(cb, 0);
globalThis.cancelAnimationFrame = noop;
globalThis.MutationObserver = class { observe(){} disconnect(){} };
globalThis.ResizeObserver = class { observe(){} disconnect(){} };
globalThis.IntersectionObserver = class { observe(){} disconnect(){} };
globalThis.HTMLElement = class {};
globalThis.customElements = { define: noop, get: () => undefined };
// Pinia mock：**必须单例**。真实 Pinia 的 defineStore 返回的 hook 每次调用都返回同一个 store 实例，
// 否则 `useGlobalSettingsStore()` 在两个地方各建一份，状态互不可见（script_trees 永远是 undefined）。
globalThis.__piniaStores = new Map();
const __piniaStores = globalThis.__piniaStores;
globalThis.defineStore = (id, setup) => {
  const fn = (...a) => {
    if (__piniaStores.has(id)) return __piniaStores.get(id);
    if (typeof setup !== 'function') { __piniaStores.set(id, setup); return setup; }
    const raw = setup(...a) || {};
    const u = {};
    for (const k of Object.keys(raw)) {
      const v = raw[k];
      u[k] = (v && typeof v === 'object' && 'value' in v && Object.keys(v).length === 1) ? v.value : v;
    }
    __piniaStores.set(id, u);
    return u;
  };
  fn.$id = id;
  return fn;
};
globalThis.ref = (v) => ({ value: v });
// Pinia/Vue 的 computed 支持两种形式：函数 或 {get, set}
globalThis.computed = (f) => {
  if (typeof f === 'function') return { get value() { return f(); } };
  if (f && typeof f === 'object' && typeof f.get === 'function') {
    return {
      get value() { return f.get(); },
      set value(v) { if (typeof f.set === 'function') f.set(v); },
    };
  }
  return { value: f };
};
globalThis.reactive = (o) => o; globalThis.watch = () => () => {}; globalThis.watchEffect = () => () => {};
globalThis.onMounted = noop; globalThis.onUnmounted = noop; globalThis.nextTick = (f) => Promise.resolve().then(f);
globalThis.defineComponent = (c) => c; globalThis.h = () => ({}); globalThis.toRaw = (o) => o;
globalThis.markRaw = (o) => o; globalThis.readonly = (o) => o; globalThis.shallowRef = (v) => ({ value: v });
globalThis.isRef = (o) => !!(o && typeof o === 'object' && 'value' in o);
globalThis.toRefs = (o) => {
  if (!o || typeof o !== 'object') return {};
  const out = {};
  for (const k of Object.keys(o)) out[k] = { value: o[k] };
  return out;
};
globalThis.toRef = (o, k) => ({ get value() { return o?.[k]; }, set value(v) { if (o) o[k] = v; } });
globalThis.triggerRef = () => {};
globalThis.shallowReactive = (o) => o;
globalThis.isReactive = () => false;
globalThis.provide = () => {};
globalThis.inject = () => undefined;
globalThis.getCurrentInstance = () => null;
globalThis.defineAsyncComponent = (f) => f;
globalThis.defineProps = () => ({});
globalThis.defineEmits = () => (() => {});
globalThis.defineExpose = () => {};
globalThis.withDefaults = (c) => c;
globalThis.useSlots = () => ({});
globalThis.useAttrs = () => ({});
globalThis.useId = () => 'id';
globalThis.mergeProps = (...a) => Object.assign({}, ...a);
globalThis.resolveComponent = (c) => c;
globalThis.resolveDirective = (d) => d;
globalThis.createApp = () => ({ use: () => {}, mount: () => {}, unmount: () => {}, config: { globalProperties: {} } });
globalThis.createVNode = () => ({});
globalThis.openBlock = () => ({});
globalThis.createBlock = () => ({});
globalThis.createElementBlock = () => ({});
globalThis.createElementVNode = () => ({});
globalThis.createTextVNode = () => ({});
globalThis.createCommentVNode = () => ({});
globalThis.toDisplayString = (v) => String(v ?? '');
globalThis.defineModel = () => ({ value: undefined });
globalThis.defineSlots = () => ({});
globalThis.defineOptions = () => {};
globalThis.useCssModule = () => ({});
globalThis.useTemplateRef = () => ({ value: null });
globalThis.useMounted = () => ({ value: false });
globalThis.useSupported = () => ({ value: false });
globalThis.useElementSize = () => ({ width: { value: 0 }, height: { value: 0 } });
globalThis.useResizeObserver = () => {};
globalThis.useEventListener = () => () => {};
globalThis.useIntervalFn = (f) => { const c = () => {}; return { pause: c, resume: c, isActive: { value: false } }; };
globalThis.useTimeoutFn = (f) => { const c = () => {}; return { start: c, stop: c, isPending: { value: false } }; };
globalThis.useLocalStorage = (k, v) => ({ value: v });
globalThis.useSessionStorage = (k, v) => ({ value: v });
globalThis.useStorage = (k, v) => ({ value: v });
globalThis.useDebounceFn = (f) => f;
globalThis.useThrottleFn = (f) => f;
globalThis.watchPausable = (src, cb) => { const stop = () => {}; stop.pause = () => {}; stop.resume = () => {}; return stop; };
globalThis.watchThrottled = globalThis.watchIgnorable;
globalThis.watchDeep = globalThis.watchIgnorable;
globalThis.watchImmediate = globalThis.watchIgnorable;
globalThis.watchOnce = globalThis.watchIgnorable;
globalThis.watchWithFilter = globalThis.watchIgnorable;
globalThis.refDebounced = (v) => ({ value: v });
globalThis.refThrottled = (v) => ({ value: v });
globalThis.computedAsync = (f) => ({ value: undefined });
globalThis.computedEager = (f) => ({ get value() { return f(); } });
globalThis.reactiveComputed = (f) => ({ get value() { return f(); } });
globalThis.reactiveOmit = (o) => o;
globalThis.reactivePick = (o) => o;
globalThis.useDark = () => ({ value: false });
globalThis.usePreferredDark = () => ({ value: false });
globalThis.useCssVar = () => ({ value: '' });
globalThis.useClipboard = () => ({ copy: async () => {}, text: { value: '' } });
globalThis.useFullscreen = () => ({ isFullscreen: { value: false }, enter: () => {}, exit: () => {}, toggle: () => {} });
globalThis.onClickOutside = () => {};
globalThis.onKeyStroke = () => {};
globalThis.useSortable = () => ({ option: () => {} });
globalThis.useEventBus = () => ({ on: () => {}, off: () => {}, emit: () => {} });
globalThis.useModal = () => ({ open: () => {}, close: () => {} });
globalThis.useVfm = () => ({});
globalThis.useI18n = () => ({ t: (k) => k, locale: { value: 'zh' } });
globalThis.useToast = () => ({ show: () => {} });
globalThis.createPinia = () => ({ install: () => {} });
globalThis.setActivePinia = () => {};
globalThis.acceptHMRUpdate = () => false;
globalThis.storeToRefs = (s) => s; globalThis.unref = (o) => (o && 'value' in o ? o.value : o);
globalThis.watchIgnorable = (s, cb) => { try { cb(s?.value ?? s, undefined); } catch(_e){} return () => {}; };
globalThis.watchDebounced = globalThis.watchIgnorable;
globalThis.klona = (o) => JSON.parse(JSON.stringify(o));
globalThis.extension_settings = {
  preset_settings: { name: 'in_use' }, disabledExtensions: [], tavern_helper: {}, TavernHelper: {} };
globalThis.power_user = { persona_description: '', persona_description_position: 0, personas: {}, default_persona: null };
globalThis.oai_settings = {}; globalThis.chat_metadata = {};
globalThis.characters = []; globalThis.this_chid = undefined; globalThis.chat = [];
globalThis.event_types = new Proxy({}, { get: (t,p) => String(p) });
globalThis.eventSource = { on:noop, once:noop, off:noop, emit:noop, emitAndWait:()=>Promise.resolve(), makeFirst:noop, makeLast:noop, clearEvent:noop, clearListener:noop, clearAll:noop, addListener:noop, removeListener:noop };
const zodMod = await import('file:///C:/Users/13239/Desktop/rikkaST-工作区/12-TavernHelper-源码/node_modules/zod/index.js');
globalThis.z = zodMod.z ?? zodMod.default ?? zodMod;
const lodashMod = await import('./lodash_mock.mjs');
globalThis._ = lodashMod.default;


const th = await import('../tavern-helper/rikkast-entry.js');
const api = th.installTavernHelper();

function group(title, tests) {
  console.log(`\n=== ${title} ===`);
  let ok = 0;
  for (const [name, fn] of tests) {
    try {
      const r = fn();
      const preview = (r && typeof r.then === 'function') ? '<Promise>' : JSON.stringify(r);
      console.log(`  ✓ ${name.padEnd(32)} -> ${String(preview).slice(0,55)}`);
      ok++;
    } catch (e) {
      console.log(`  ✗ ${name.padEnd(32)} -> ${e.constructor.name}: ${String(e.message).slice(0,60)}`);
      if (process.env.ST_DEBUG) console.log('    stack:', String(e.stack||'').split('\n').slice(1,4).join(' | '));
    }
  }
  console.log(`  ${ok}/${tests.length}`);
  return ok;
}

let total = 0, passed = 0;
function run(title, tests) { total += tests.length; passed += group(title, tests); }

run('C 组 音频', [
  ['playAudio', () => api.playAudio('bgm', {url:'http://x/a.mp3'})],
  ['pauseAudio', () => api.pauseAudio('bgm')],
  ['getAudioList', () => api.getAudioList('bgm')],
  ['getAudioSettings', () => api.getAudioSettings('bgm')],
  ['getCurrentAudio', () => api.getCurrentAudio('bgm')],
  ['setAudioSettings', () => api.setAudioSettings('bgm', {volume:80})],
  ['replaceAudioList', () => api.replaceAudioList('bgm', [{url:'http://x/b.mp3'}])],
  ['appendAudioList', () => api.appendAudioList('bgm', [{url:'http://x/c.mp3'}])],
]);
run('D 组 正则', [
  ['getTavernRegexes', () => api.getTavernRegexes()],
  ['isCharacterTavernRegexesEnabled', () => api.isCharacterTavernRegexesEnabled()],
]);
run('G 组 消息渲染', [
  ['formatAsTavernRegexedString', () => api.formatAsTavernRegexedString('hello', 'ai_output')],
]);
run('H 组 生成控制', [
  // getModelList 会走 fetch，Node 里没有 ST 后端 -> 只验证不抛同步异常
  ['getModelList(type)', () => typeof api.getModelList],
  ['stopAllGeneration', () => api.stopAllGeneration()],
]);
run('I 组 杂项', [
  ['getProxyPresetNames', () => api.getProxyPresetNames()],
  ['getChatHistoryBrief', () => api.getChatHistoryBrief()],
]);
run('A 组 预设', [
  ['getPresetNames', () => api.getPresetNames()],
  ['getLoadedPresetName', () => api.getLoadedPresetName()],
  ['isPresetNormalPrompt', () => api.isPresetNormalPrompt({marker:false, system_prompt:false})],
  ['isPresetSystemPrompt', () => api.isPresetSystemPrompt({system_prompt:true})],
  ['isPresetPlaceholderPrompt', () => api.isPresetPlaceholderPrompt({marker:true})],
]);
// 诊断：确认 store 里的 settings.script 结构
try {
  const gs = globalThis.__piniaStores ? globalThis.__piniaStores.get('global_settings') : null;
  console.log('\n=== 诊断 global_settings ===');
  console.log('  store 存在:', !!gs);
  if (gs) {
    console.log('  settings keys:', Object.keys(gs.settings || {}));
    console.log('  script:', JSON.stringify(gs.settings?.script)?.slice(0,150));
  }
} catch (e) { console.log('diag err:', e.message); }

run('F 组 脚本树/按钮', [
  // getScriptTrees 必须传 {type}，不传会读 undefined.type（上游契约）
  ['getScriptTrees(global)', () => api.getScriptTrees({type:'global'})],
  ['getScriptTrees(character)', () => api.getScriptTrees({type:'character'})],
  ['getScriptTrees(preset)', () => api.getScriptTrees({type:'preset'})],
  ['replaceScriptTrees', () => api.replaceScriptTrees([], {type:'global'})],
]);

console.log(`\n========== 总计 ${passed}/${total} ==========`);
