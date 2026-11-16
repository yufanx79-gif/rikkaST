// 最小 lodash mock：只实现 Tavern Helper 用到的函数
export const _ = {
  pick: (o, ks) => Object.fromEntries(ks.filter(k => k in (o||{})).map(k => [k, o[k]])),
  omit: (o, ks) => Object.fromEntries(Object.entries(o||{}).filter(([k]) => !ks.includes(k))),
  clamp: (v, lo, hi) => Math.min(Math.max(v, lo), hi),
  defaultsDeep: (a, b) => ({ ...(b||{}), ...(a||{}) }),
  merge: (a, b) => ({ ...(a||{}), ...(b||{}) }),
  isEqual: (a, b) => JSON.stringify(a) === JSON.stringify(b),
  cloneDeep: (o) => { try { return JSON.parse(JSON.stringify(o)); } catch { return o; } },
  get: (o, p, d) => { try { const r = String(p).split('.').reduce((x,k)=>x?.[k], o); return r === undefined ? d : r; } catch { return d; } },
  set: (o, p, v) => { const ks = String(p).split('.'); let c = o; for (let i=0;i<ks.length-1;i++) { if (!c[ks[i]]) c[ks[i]] = {}; c = c[ks[i]]; } c[ks[ks.length-1]] = v; return o; },
  has: (o, p) => { try { return String(p).split('.').reduce((x,k)=>x?.[k], o) !== undefined; } catch { return false; } },
  uniq: (a) => [...new Set(a||[])],
  uniqBy: (a, f) => { const s = new Set(); return (a||[]).filter(x => { const k = typeof f==='function'?f(x):x[f]; if (s.has(k)) return false; s.add(k); return true; }); },
  sortBy: (a, f) => [...(a||[])].sort((x,y) => { const fx = typeof f==='function'?f(x):x[f]; const fy = typeof f==='function'?f(y):y[f]; return fx<fy?-1:fx>fy?1:0; }),
  map: (a, f) => (a||[]).map(f),
  filter: (a, f) => (a||[]).filter(f),
  find: (a, f) => (a||[]).find(f),
  findIndex: (a, f) => (a||[]).findIndex(f),
  forEach: (a, f) => (a||[]).forEach(f),
  some: (a, f) => (a||[]).some(f),
  every: (a, f) => (a||[]).every(f),
  includes: (a, v) => (a||[]).includes(v),
  concat: (...a) => a.flat(),
  flatten: (a) => (a||[]).flat(),
  flattenDeep: (a) => (a||[]).flat(Infinity),
  difference: (a, b) => (a||[]).filter(x => !(b||[]).includes(x)),
  takeRight: (a, n=1) => (a||[]).slice(-n),
  take: (a, n=1) => (a||[]).slice(0, n),
  first: (a) => (a||[])[0],
  last: (a) => (a||[])[(a||[]).length-1],
  chunk: (a, n) => { const r=[]; for(let i=0;i<(a||[]).length;i+=n) r.push(a.slice(i,i+n)); return r; },
  debounce: (f) => { const g = (...a) => f(...a); g.cancel = () => {}; g.flush = () => {}; return g; },
  throttle: (f) => f,
  isEmpty: (v) => !v || (typeof v === 'object' && Object.keys(v).length === 0),
  isArray: Array.isArray,
  isObject: (v) => v && typeof v === 'object',
  isPlainObject: (v) => v && typeof v === 'object' && !Array.isArray(v),
  isString: (v) => typeof v === 'string',
  isNumber: (v) => typeof v === 'number',
  isFunction: (v) => typeof v === 'function',
  isUndefined: (v) => v === undefined,
  isNull: (v) => v === null,
  isNil: (v) => v == null,
  isBoolean: (v) => typeof v === 'boolean',
  escapeRegExp: (s) => String(s ?? '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&'),
  trim: (s) => String(s ?? '').trim(),
  toNumber: (v) => Number(v) || 0,
  toString: (v) => String(v ?? ''),
  keys: (o) => Object.keys(o||{}),
  values: (o) => Object.values(o||{}),
  entries: (o) => Object.entries(o||{}),
  fromPairs: (a) => Object.fromEntries(a||[]),
  keyBy: (a, f) => Object.fromEntries((a||[]).map(x => [typeof f==='function'?f(x):x[f], x])),
  groupBy: (a, f) => (a||[]).reduce((acc, x) => { const k = typeof f==='function'?f(x):x[f]; (acc[k] ||= []).push(x); return acc; }, {}),
  sum: (a) => (a||[]).reduce((s,x)=>s+x,0),
  sumBy: (a, f) => (a||[]).reduce((s,x)=>s+(typeof f==='function'?f(x):x[f]||0),0),
  min: (a) => Math.min(...(a||[])),
  max: (a) => Math.max(...(a||[])),
  range: (n) => Array.from({length:n},(_,i)=>i),
  times: (n, f) => Array.from({length:n},(_,i)=>f(i)),
  noop: () => {},
  identity: (x) => x,
  constant: (x) => () => x,
  camelCase: (s) => String(s??'').replace(/[-_\s]+(.)?/g, (_,c)=>c?c.toUpperCase():''),
  kebabCase: (s) => String(s??'').replace(/([a-z])([A-Z])/g,'$1-$2').toLowerCase(),
  snakeCase: (s) => String(s??'').replace(/([a-z])([A-Z])/g,'$1_$2').toLowerCase(),
  upperFirst: (s) => String(s??'').charAt(0).toUpperCase() + String(s??'').slice(1),
  lowerFirst: (s) => String(s??'').charAt(0).toLowerCase() + String(s??'').slice(1),
  capitalize: (s) => { const t=String(s??''); return t.charAt(0).toUpperCase()+t.slice(1).toLowerCase(); },
  startsWith: (s, p) => String(s??'').startsWith(p),
  endsWith: (s, p) => String(s??'').endsWith(p),
  replace: (s, a, b) => String(s??'').replace(a, b),
  split: (s, sep) => String(s??'').split(sep),
  join: (a, sep) => (a||[]).join(sep),
  truncate: (s, o={}) => { const t=String(s??''); const len=o.length||30; return t.length>len ? t.slice(0,len)+(o.omission??'...') : t; },
  padStart: (s, n, c=' ') => String(s??'').padStart(n,c),
  padEnd: (s, n, c=' ') => String(s??'').padEnd(n,c),
  repeat: (s, n) => String(s??'').repeat(n),
  sample: (a) => (a||[])[Math.floor(Math.random()*(a||[]).length)],
  shuffle: (a) => [...(a||[])].sort(() => Math.random()-0.5),
  random: (lo=0, hi=1) => lo + Math.random()*(hi-lo),
  now: () => Date.now(),
  uniqueId: (p='') => p + (++_._id),
  _id: 0,
  memoize: (f) => { const c = new Map(); return (...a) => { const k = JSON.stringify(a); if (!c.has(k)) c.set(k, f(...a)); return c.get(k); }; },
  once: (f) => { let done=false, r; return (...a) => { if (!done) { done=true; r=f(...a); } return r; }; },
  before: (n, f) => { let c=0, r; return (...a) => { if (++c <= n) r=f(...a); return r; }; },
  after: (n, f) => { let c=0; return (...a) => { if (++c >= n) return f(...a); }; },
  negate: (f) => (...a) => !f(...a),
  wrap: (v, f) => f(v),
  castArray: (v) => Array.isArray(v) ? v : [v],
  compact: (a) => (a||[]).filter(Boolean),
  fill: (a, v, s=0, e=(a||[]).length) => { const r=[...(a||[])]; for(let i=s;i<e;i++) r[i]=v; return r; },
  without: (a, ...vs) => (a||[]).filter(x => !vs.includes(x)),
  zip: (...arrs) => { const n = Math.max(...arrs.map(a=>a.length)); return Array.from({length:n},(_,i)=>arrs.map(a=>a[i])); },
  at: (o, p) => { try { return String(p).split('.').reduce((x,k)=>x?.[k], o); } catch { return undefined; } },
  toPath: (p) => String(p).split('.'),
  result: (o, p, d) => { try { const r = String(p).split('.').reduce((x,k)=>x?.[k], o); return typeof r === 'function' ? r.call(o) : (r ?? d); } catch { return d; } },
  invoke: (o, p, ...a) => { try { return o[p](...a); } catch { return undefined; } },
  partial: (f, ...pre) => (...a) => f(...pre, ...a),
  curry: (f) => f,
  flow: (...fs) => (...a) => fs.reduce((r, f) => f(r), ...a),
  defaults: (o, ...ds) => Object.assign({}, ...ds.reverse(), o),
  extend: (o, ...s) => Object.assign(o, ...s),
  assign: (o, ...s) => Object.assign(o, ...s),
  invert: (o) => Object.fromEntries(Object.entries(o||{}).map(([k,v])=>[v,k])),
  mapValues: (o, f) => Object.fromEntries(Object.entries(o||{}).map(([k,v])=>[k,f(v)])),
  mapKeys: (o, f) => Object.fromEntries(Object.entries(o||{}).map(([k,v])=>[f(v,k),v])),
  pickBy: (o, f) => Object.fromEntries(Object.entries(o||{}).filter(([k,v])=>f(v,k))),
  omitBy: (o, f) => Object.fromEntries(Object.entries(o||{}).filter(([k,v])=>!f(v,k))),
  forOwn: (o, f) => { for (const k of Object.keys(o||{})) f(o[k], k, o); },
  forIn: (o, f) => { for (const k in (o||{})) f(o[k], k, o); },
  size: (v) => Array.isArray(v) || typeof v === 'string' ? v.length : Object.keys(v||{}).length,
  head: (a) => (a||[])[0],
  tail: (a) => (a||[]).slice(1),
  initial: (a) => (a||[]).slice(0,-1),
  lastIndexOf: (a, v) => (a||[]).lastIndexOf(v),
  indexOf: (a, v) => (a||[]).indexOf(v),
  pull: (a, ...vs) => { for (const v of vs) { let i; while ((i=a.indexOf(v))>=0) a.splice(i,1); } return a; },
  remove: (a, f) => { const r=[]; for(let i=a.length-1;i>=0;i--) if(f(a[i])) r.unshift(...a.splice(i,1)); return r; },
  sortedUniq: (a) => [...new Set(a||[])],
  orderBy: (a, ks, os) => [...(a||[])].sort((x,y) => { for (let i=0;i<ks.length;i++) { const k=ks[i], o=os?.[i]==='desc'?-1:1; if (x[k]<y[k]) return -1*o; if (x[k]>y[k]) return 1*o; } return 0; }),
  isMatch: (o, s) => Object.entries(s||{}).every(([k,v]) => o?.[k] === v),
  matches: (s) => (o) => _.isMatch(o, s),
  property: (p) => (o) => _.get(o, p),
  propertyOf: (o) => (p) => _.get(o, p),
  iteratee: (f) => typeof f === 'function' ? f : (o) => _.get(o, f),
};
// lodash 的 `_(value)` 链式包装：TH 的 predefine 用 `_(window).merge(...).value()`
export function lodashWrapper(value) {
  // 通用链式包装：任何 lodash 方法名都能调用，自动区分「写操作返回 this」与「读操作返回新包装」。
  // 写操作（改变 _value）：merge/assign/set/push/pop/shift/unshift/splice/sort/reverse/fill/pull/remove
  const MUTATORS = new Set(['merge', 'assign', 'set', 'push', 'pop', 'shift', 'unshift', 'splice', 'sort', 'reverse', 'fill', 'pull', 'remove', 'extend', 'defaults', 'update', 'unset']);
  const TARGET = {};
  const handler = {
    get(target, prop) {
      if (prop === '_value') return value;
      if (prop === 'value' || prop === 'valueOf') return () => value;
      if (prop === 'toJSON') return () => value;
      if (prop === Symbol.toPrimitive) return () => String(value);
      if (prop === 'then') return undefined;   // 不要被当成 thenable
      if (typeof prop === 'symbol') return undefined;

      const fn = _[prop];
      if (typeof fn !== 'function') {
        // 不是 lodash 方法：若 value 上有同名方法（原生数组方法 flatMap/find/some 等），
        // 包装成返回新 wrapper 的形式，保证链式不中断。
        const nativeFn = value?.[prop];
        if (typeof nativeFn === 'function') {
          return (...args) => {
            let result;
            try { result = nativeFn.apply(value, args); } catch (_e) { result = undefined; }
            if (result && typeof result === 'object') return lodashWrapper(result);
            return result;
          };
        }
        // 纯属性读取
        return value?.[prop];
      }
      return (...args) => {
        if (MUTATORS.has(prop)) {
          try { fn(value, ...args); } catch (_e) { /* noop */ }
          return proxy;
        }
        let result;
        try { result = fn(value, ...args); } catch (_e) { result = undefined; }
        // 结果是数组/对象 -> 继续可链；原始值 -> 直接返回
        if (result && typeof result === 'object') return lodashWrapper(result);
        return result;
      };
    },
  };
  const proxy = new Proxy(TARGET, handler);
  return proxy;
}
export const _chain = Object.assign(lodashWrapper, _);
export default Object.assign(lodashWrapper, _);
