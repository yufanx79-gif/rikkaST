import { Stopwatch, delay, ensureImageFormatSupported, getBase64Async, getCharaFilename, getImageSizeFromDataURL, getStringHash, isDataURL, uuidv4 } from "../scripts/utils.js";
import { t } from "../scripts/i18n.js";
import { MAX_INJECTION_DEPTH, activateSendButtons, addOneMessage, baseChatReplace, characters, chat, chat_metadata, cleanUpMessage, clearChat, countOccurrences, deactivateSendButtons, default_avatar, default_user_avatar, deleteCharacter, eventSource, event_types, extension_prompt_roles, extension_prompt_types, extension_prompts, getBiasStrings, getCharacterCardFields, getCharacters, getCurrentChatId, getExtensionPromptRoleByName, getMaxContextSize, getOneCharacter, getPastCharacterChats, getRequestHeaders, getThumbnailUrl, isOdd, is_send_press, messageFormatting, name1, name2, printCharacters, printMessages, reloadCurrentChat, reloadMarkdownProcessor, saveCharacterDebounced, saveChatConditional, saveMetadata, saveSettings, saveSettingsDebounced, scrollChatToBottom, selectCharacterById, setExtensionPrompt, setGenerationProgress, setUserName, showSwipeButtons, stopGeneration, substituteParams, substituteParamsExtended, system_avatar, system_message_types, this_chid, unshallowCharacter, user_avatar } from "../script.js";
import { DEFAULT_DEPTH, DEFAULT_WEIGHT, METADATA_KEY, convertCharacterBook, createNewWorldInfo, deleteWorldInfo, getWorldInfoPrompt, getWorldInfoSettings, loadWorldInfo, newWorldInfoEntryTemplate, parseRegexFromString, saveWorldInfo, selected_world_info, setWorldInfoButtonClass, wi_anchor_position, world_info, world_info_include_names, world_info_logic, world_info_position, world_names } from "../scripts/world-info.js";
import { getRegexedString, regex_placement } from "../scripts/extensions/regex/engine.js";
import { getPresetManager } from "../scripts/preset-manager.js";
import { extensionTypes, extension_settings, getContext, saveMetadataDebounced } from "../scripts/extensions.js";
import { ChatCompletion, Message, MessageCollection, getChatCompletionModel, getStreamingReply, isImageInliningSupported, oai_settings, prepareOpenAIMessages, promptManager, proxies, sendOpenAIRequest, setOpenAIMessageExamples, setOpenAIMessages, setupChatCompletionPromptManager, tryParseStreamingError } from "../scripts/openai.js";
import { getLastMessageId } from "../scripts/macros.js";
import { favsToHotswap } from "../scripts/RossAscends-mods.js";
import { flushEphemeralStoppingStrings, persona_description_positions, power_user } from "../scripts/power-user.js";
import { isAdmin } from "../scripts/user.js";
import { NOTE_MODULE_NAME, metadata_keys, shouldWIAddPrompt } from "../scripts/authors-note.js";
import { Prompt, PromptCollection } from "../scripts/PromptManager.js";
import { extractReasoningFromData } from "../scripts/reasoning.js";
import { getEventSourceStream } from "../scripts/sse-stream.js";
import { getUserAvatar, getUserAvatars, setUserAvatar, user_avatar as user_avatar$1 } from "../scripts/personas.js";
import { executeSlashCommandsWithOptions } from "../scripts/slash-commands.js";
import "../scripts/slash-commands/SlashCommand.js";
import "../scripts/slash-commands/SlashCommandArgument.js";
import "../scripts/slash-commands/SlashCommandCommonEnumsProvider.js";
import "../scripts/slash-commands/SlashCommandEnumValue.js";
import "../scripts/slash-commands/SlashCommandParser.js";
// \0rolldown/runtime.js
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __commonJSMin = (cb, mod) => () => (mod || (cb((mod = { exports: {} }).exports, mod, mod.exports), cb = null), mod.exports);
var __exportAll = (all, no_symbols) => {
	let target = {};
	for (var name in all) __defProp(target, name, {
		get: all[name],
		enumerable: true
	});
	if (!no_symbols) __defProp(target, Symbol.toStringTag, { value: "Module" });
	return target;
};
var __copyProps = (to, from, except, desc) => {
	if (from && typeof from === "object" || typeof from === "function") for (var keys = __getOwnPropNames(from), i = 0, n = keys.length, key; i < n; i++) {
		key = keys[i];
		if (!__hasOwnProp.call(to, key) && key !== except) __defProp(to, key, {
			get: ((k) => from[k]).bind(null, key),
			enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable
		});
	}
	return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(isNodeMode || !mod || !mod.__esModule || !__hasOwnProp.call(mod, "default") ? __defProp(target, "default", {
	value: mod,
	enumerable: true
}) : target, mod));
// node_modules/.pnpm/@vue+shared@3.5.45/node_modules/@vue/shared/dist/shared.esm-bundler.js
/**
* @vue/shared v3.5.45
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
// @__NO_SIDE_EFFECTS__
function makeMap(str) {
	const map = /* @__PURE__ */ Object.create(null);
	for (const key of str.split(",")) map[key] = 1;
	return (val) => val in map;
}
var EMPTY_OBJ = {};
var EMPTY_ARR = [];
var NOOP = () => {};
var isOn = (key) => key.charCodeAt(0) === 111 && key.charCodeAt(1) === 110 && (key.charCodeAt(2) > 122 || key.charCodeAt(2) < 97);
var isModelListener = (key) => key.startsWith("onUpdate:");
var extend$1 = Object.assign;
var hasOwnProperty$1 = Object.prototype.hasOwnProperty;
var hasOwn = (val, key) => hasOwnProperty$1.call(val, key);
var isArray = Array.isArray;
var isMap$1 = (val) => toTypeString(val) === "[object Map]";
var isSet = (val) => toTypeString(val) === "[object Set]";
var isFunction = (val) => typeof val === "function";
var isString = (val) => typeof val === "string";
var isSymbol = (val) => typeof val === "symbol";
var isObject$2 = (val) => val !== null && typeof val === "object";
var isPromise$1 = (val) => {
	return (isObject$2(val) || isFunction(val)) && isFunction(val.then) && isFunction(val.catch);
};
var objectToString = Object.prototype.toString;
var toTypeString = (value) => objectToString.call(value);
var toRawType = (value) => {
	return toTypeString(value).slice(8, -1);
};
var isPlainObject$1 = (val) => toTypeString(val) === "[object Object]";
var isIntegerKey = (key) => isString(key) && key !== "NaN" && key[0] !== "-" && "" + parseInt(key, 10) === key;
var cacheStringFunction$1 = (fn) => {
	const cache = /* @__PURE__ */ Object.create(null);
	return ((str) => {
		return cache[str] || (cache[str] = fn(str));
	});
};
var camelizeRE$1 = /-\w/g;
var camelize$1 = /* @__PURE__ */ cacheStringFunction$1((str) => {
	return str.replace(camelizeRE$1, (c) => c.slice(1).toUpperCase());
});
var hyphenateRE$1 = /\B([A-Z])/g;
var hyphenate$1 = /* @__PURE__ */ cacheStringFunction$1((str) => str.replace(hyphenateRE$1, "-$1").toLowerCase());
var capitalize = /* @__PURE__ */ cacheStringFunction$1((str) => {
	return str.charAt(0).toUpperCase() + str.slice(1);
});
var toHandlerKey = /* @__PURE__ */ cacheStringFunction$1((str) => {
	return str ? `on${capitalize(str)}` : ``;
});
var hasChanged = (value, oldValue) => !Object.is(value, oldValue);
var def = (obj, key, value, writable = false) => {
	Object.defineProperty(obj, key, {
		configurable: true,
		enumerable: false,
		writable,
		value
	});
};
var toNumber = (val) => {
	const n = isString(val) ? Number(val) : NaN;
	return isNaN(n) ? val : n;
};
var _globalThis;
var getGlobalThis = () => {
	return _globalThis || (_globalThis = typeof globalThis !== "undefined" ? globalThis : typeof self !== "undefined" ? self : typeof window !== "undefined" ? window : typeof global !== "undefined" ? global : {});
};
function normalizeStyle(value) {
	if (isArray(value)) {
		const res = {};
		for (let i = 0; i < value.length; i++) {
			const item = value[i];
			const normalized = isString(item) ? parseStringStyle(item) : normalizeStyle(item);
			if (normalized) for (const key in normalized) res[key] = normalized[key];
		}
		return res;
	} else if (isString(value) || isObject$2(value)) return value;
}
var listDelimiterRE = /;(?![^(]*\))/g;
var propertyDelimiterRE = /:([^]+)/;
var styleCommentRE = /"(?:[^"\\]|\\[^])*"|'(?:[^'\\]|\\[^])*'|\\[^]|\/\*[^]*?\*\//g;
function parseStringStyle(cssText) {
	const ret = {};
	cssText.replace(styleCommentRE, (match) => match.startsWith("/*") ? "" : match).split(listDelimiterRE).forEach((item) => {
		if (item) {
			const tmp = item.split(propertyDelimiterRE);
			tmp.length > 1 && (ret[tmp[0].trim()] = tmp[1].trim());
		}
	});
	return ret;
}
function normalizeClass(value) {
	let res = "";
	if (isString(value)) res = value;
	else if (isArray(value)) for (let i = 0; i < value.length; i++) {
		const normalized = normalizeClass(value[i]);
		if (normalized) res += normalized + " ";
	}
	else if (isObject$2(value)) {
		for (const name in value) if (value[name]) res += name + " ";
	}
	return res.trim();
}
function normalizeProps(props) {
	if (!props) return null;
	let { class: klass, style } = props;
	if (klass && !isString(klass)) props.class = normalizeClass(klass);
	if (style) props.style = normalizeStyle(style);
	return props;
}
var isRef$1 = (val) => {
	return !!(val && val["__v_isRef"] === true);
};
var toDisplayString = (val) => {
	return isString(val) ? val : val == null ? "" : isArray(val) || isObject$2(val) && (val.toString === objectToString || !isFunction(val.toString)) ? isRef$1(val) ? toDisplayString(val.value) : JSON.stringify(val, replacer, 2) : String(val);
};
var replacer = (_key, val) => {
	if (isRef$1(val)) return replacer(_key, val.value);
	else if (isMap$1(val)) return { [`Map(${val.size})`]: [...val.entries()].reduce((entries, [key, val2], i) => {
		entries[stringifySymbol(key, i) + " =>"] = val2;
		return entries;
	}, {}) };
	else if (isSet(val)) return { [`Set(${val.size})`]: [...val.values()].map((v) => stringifySymbol(v)) };
	else if (isSymbol(val)) return stringifySymbol(val);
	else if (isObject$2(val) && !isArray(val) && !isPlainObject$1(val)) return String(val);
	return val;
};
var stringifySymbol = (v, i = "") => {
	var _a;
	return isSymbol(v) ? `Symbol(${(_a = v.description) != null ? _a : i})` : v;
};
/**
* @vue/reactivity v3.5.45
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
var activeSub;
var pausedQueueEffects = /* @__PURE__ */ new WeakSet();
var ReactiveEffect = class {
	constructor(fn) {
		this.fn = fn;
		/**
		* @internal
		*/
		this.deps = void 0;
		/**
		* @internal
		*/
		this.depsTail = void 0;
		/**
		* @internal
		*/
		this.flags = 5;
		/**
		* @internal
		*/
		this.next = void 0;
		/**
		* @internal
		*/
		this.cleanup = void 0;
		this.scheduler = void 0;
	}
	pause() {
		this.flags |= 64;
	}
	resume() {
		if (this.flags & 64) {
			this.flags &= -65;
			if (pausedQueueEffects.has(this)) {
				pausedQueueEffects.delete(this);
				this.trigger();
			}
		}
	}
	/**
	* @internal
	*/
	notify() {
		if (this.flags & 2 && !(this.flags & 32)) return;
		if (!(this.flags & 8)) batch(this);
	}
	run() {
		if (!(this.flags & 1)) return this.fn();
		this.flags |= 2;
		cleanupEffect(this);
		prepareDeps(this);
		const prevEffect = activeSub;
		const prevShouldTrack = shouldTrack;
		activeSub = this;
		shouldTrack = true;
		try {
			return this.fn();
		} finally {
			cleanupDeps(this);
			activeSub = prevEffect;
			shouldTrack = prevShouldTrack;
			this.flags &= -3;
		}
	}
	stop() {
		if (this.flags & 1) {
			for (let link = this.deps; link; link = link.nextDep) removeSub(link);
			this.deps = this.depsTail = void 0;
			cleanupEffect(this);
			this.onStop && this.onStop();
			this.flags &= -2;
		}
	}
	trigger() {
		if (this.flags & 64) pausedQueueEffects.add(this);
		else if (this.scheduler) this.scheduler();
		else this.runIfDirty();
	}
	/**
	* @internal
	*/
	runIfDirty() {
		if (isDirty(this)) this.run();
	}
	get dirty() {
		return isDirty(this);
	}
};
var batchDepth = 0;
var batchedSub;
var batchedComputed;
function batch(sub, isComputed = false) {
	sub.flags |= 8;
	if (isComputed) {
		sub.next = batchedComputed;
		batchedComputed = sub;
		return;
	}
	sub.next = batchedSub;
	batchedSub = sub;
}
function startBatch() {
	batchDepth++;
}
function endBatch() {
	if (--batchDepth > 0) return;
	if (batchedComputed) {
		let e = batchedComputed;
		batchedComputed = void 0;
		while (e) {
			const next = e.next;
			e.next = void 0;
			e.flags &= -9;
			e = next;
		}
	}
	let error;
	while (batchedSub) {
		let e = batchedSub;
		batchedSub = void 0;
		while (e) {
			const next = e.next;
			e.next = void 0;
			e.flags &= -9;
			if (e.flags & 1) try {
				e.trigger();
			} catch (err) {
				if (!error) error = err;
			}
			e = next;
		}
	}
	if (error) throw error;
}
function prepareDeps(sub) {
	for (let link = sub.deps; link; link = link.nextDep) {
		link.version = -1;
		link.prevActiveLink = link.dep.activeLink;
		link.dep.activeLink = link;
	}
}
function cleanupDeps(sub) {
	let head;
	let tail = sub.depsTail;
	let link = tail;
	while (link) {
		const prev = link.prevDep;
		if (link.version === -1) {
			if (link === tail) tail = prev;
			removeSub(link);
			removeDep(link);
		} else head = link;
		link.dep.activeLink = link.prevActiveLink;
		link.prevActiveLink = void 0;
		link = prev;
	}
	sub.deps = head;
	sub.depsTail = tail;
}
function isDirty(sub) {
	for (let link = sub.deps; link; link = link.nextDep) if (link.dep.version !== link.version || link.dep.computed && (refreshComputed(link.dep.computed) || link.dep.version !== link.version)) return true;
	if (sub._dirty) return true;
	return false;
}
function refreshComputed(computed) {
	if (computed.flags & 4 && !(computed.flags & 16)) return;
	computed.flags &= -17;
	if (computed.globalVersion === globalVersion) return;
	computed.globalVersion = globalVersion;
	if (!computed.isSSR && computed.flags & 128 && (!computed.deps && !computed._dirty || !isDirty(computed))) return;
	computed.flags |= 2;
	const dep = computed.dep;
	const prevSub = activeSub;
	const prevShouldTrack = shouldTrack;
	activeSub = computed;
	shouldTrack = true;
	try {
		prepareDeps(computed);
		const value = computed.fn(computed._value);
		if (dep.version === 0 || hasChanged(value, computed._value)) {
			computed.flags |= 128;
			computed._value = value;
			dep.version++;
		}
	} catch (err) {
		dep.version++;
		throw err;
	} finally {
		activeSub = prevSub;
		shouldTrack = prevShouldTrack;
		cleanupDeps(computed);
		computed.flags &= -3;
	}
}
function removeSub(link, soft = false) {
	const { dep, prevSub, nextSub } = link;
	if (prevSub) {
		prevSub.nextSub = nextSub;
		link.prevSub = void 0;
	}
	if (nextSub) {
		nextSub.prevSub = prevSub;
		link.nextSub = void 0;
	}
	if (dep.subs === link) {
		dep.subs = prevSub;
		if (!prevSub && dep.computed) {
			dep.computed.flags &= -5;
			for (let l = dep.computed.deps; l; l = l.nextDep) removeSub(l, true);
		}
	}
	if (!soft && !--dep.sc && dep.map) dep.map.delete(dep.key);
}
function removeDep(link) {
	const { prevDep, nextDep } = link;
	if (prevDep) {
		prevDep.nextDep = nextDep;
		link.prevDep = void 0;
	}
	if (nextDep) {
		nextDep.prevDep = prevDep;
		link.nextDep = void 0;
	}
}
var shouldTrack = true;
var trackStack = [];
function pauseTracking() {
	trackStack.push(shouldTrack);
	shouldTrack = false;
}
function resetTracking() {
	const last = trackStack.pop();
	shouldTrack = last === void 0 ? true : last;
}
function cleanupEffect(e) {
	const { cleanup } = e;
	e.cleanup = void 0;
	if (cleanup) {
		const prevSub = activeSub;
		activeSub = void 0;
		try {
			cleanup();
		} finally {
			activeSub = prevSub;
		}
	}
}
var globalVersion = 0;
var Link = class {
	constructor(sub, dep) {
		this.sub = sub;
		this.dep = dep;
		this.version = dep.version;
		this.nextDep = this.prevDep = this.nextSub = this.prevSub = this.prevActiveLink = void 0;
	}
};
var Dep = class {
	constructor(computed) {
		this.computed = computed;
		this.version = 0;
		/**
		* Link between this dep and the current active effect
		*/
		this.activeLink = void 0;
		/**
		* Doubly linked list representing the subscribing effects (tail)
		*/
		this.subs = void 0;
		/**
		* For object property deps cleanup
		*/
		this.map = void 0;
		this.key = void 0;
		/**
		* Subscriber counter
		*/
		this.sc = 0;
		/**
		* @internal
		*/
		this.__v_skip = true;
	}
	track(debugInfo) {
		if (!activeSub || !shouldTrack || activeSub === this.computed) return;
		let link = this.activeLink;
		if (link === void 0 || link.sub !== activeSub) {
			link = this.activeLink = new Link(activeSub, this);
			if (!activeSub.deps) activeSub.deps = activeSub.depsTail = link;
			else {
				link.prevDep = activeSub.depsTail;
				activeSub.depsTail.nextDep = link;
				activeSub.depsTail = link;
			}
			addSub(link);
		} else if (link.version === -1) {
			link.version = this.version;
			if (link.nextDep) {
				const next = link.nextDep;
				next.prevDep = link.prevDep;
				if (link.prevDep) link.prevDep.nextDep = next;
				link.prevDep = activeSub.depsTail;
				link.nextDep = void 0;
				activeSub.depsTail.nextDep = link;
				activeSub.depsTail = link;
				if (activeSub.deps === link) activeSub.deps = next;
			}
		}
		return link;
	}
	trigger(debugInfo) {
		this.version++;
		globalVersion++;
		this.notify(debugInfo);
	}
	notify(debugInfo) {
		startBatch();
		try {
			for (let link = this.subs; link; link = link.prevSub) if (link.sub.notify()) link.sub.dep.notify();
		} finally {
			endBatch();
		}
	}
};
function addSub(link) {
	link.dep.sc++;
	if (link.sub.flags & 4) {
		const computed = link.dep.computed;
		if (computed && !link.dep.subs) {
			computed.flags |= 20;
			for (let l = computed.deps; l; l = l.nextDep) addSub(l);
		}
		const currentTail = link.dep.subs;
		if (currentTail !== link) {
			link.prevSub = currentTail;
			if (currentTail) currentTail.nextSub = link;
		}
		link.dep.subs = link;
	}
}
var targetMap = /* @__PURE__ */ new WeakMap();
var ITERATE_KEY = /* @__PURE__ */ Symbol("");
var MAP_KEY_ITERATE_KEY = /* @__PURE__ */ Symbol("");
var ARRAY_ITERATE_KEY = /* @__PURE__ */ Symbol("");
function track(target, type, key) {
	if (shouldTrack && activeSub) {
		let depsMap = targetMap.get(target);
		if (!depsMap) targetMap.set(target, depsMap = /* @__PURE__ */ new Map());
		let dep = depsMap.get(key);
		if (!dep) {
			depsMap.set(key, dep = new Dep());
			dep.map = depsMap;
			dep.key = key;
		}
		dep.track();
	}
}
function trigger(target, type, key, newValue, oldValue, oldTarget) {
	const depsMap = targetMap.get(target);
	if (!depsMap) {
		globalVersion++;
		return;
	}
	const run = (dep) => {
		if (dep) dep.trigger();
	};
	startBatch();
	if (type === "clear") depsMap.forEach(run);
	else {
		const targetIsArray = isArray(target);
		const isArrayIndex = targetIsArray && isIntegerKey(key);
		if (targetIsArray && key === "length") {
			const newLength = Number(newValue);
			depsMap.forEach((dep, key2) => {
				if (key2 === "length" || key2 === ARRAY_ITERATE_KEY || !isSymbol(key2) && key2 >= newLength) run(dep);
			});
		} else {
			if (key !== void 0 || depsMap.has(void 0)) run(depsMap.get(key));
			if (isArrayIndex) run(depsMap.get(ARRAY_ITERATE_KEY));
			switch (type) {
				case "add":
					if (!targetIsArray) {
						run(depsMap.get(ITERATE_KEY));
						if (isMap$1(target)) run(depsMap.get(MAP_KEY_ITERATE_KEY));
					} else if (isArrayIndex) run(depsMap.get("length"));
					break;
				case "delete":
					if (!targetIsArray) {
						run(depsMap.get(ITERATE_KEY));
						if (isMap$1(target)) run(depsMap.get(MAP_KEY_ITERATE_KEY));
					}
					break;
				case "set": if (isMap$1(target)) run(depsMap.get(ITERATE_KEY));
			}
		}
	}
	endBatch();
}
function getDepFromReactive(object, key) {
	const depMap = targetMap.get(object);
	return depMap && depMap.get(key);
}
function reactiveReadArray(array) {
	const raw = /* @__PURE__ */ toRaw(array);
	if (raw === array) return raw;
	track(raw, "iterate", ARRAY_ITERATE_KEY);
	if (/* @__PURE__ */ isShallow(array)) return raw;
	if (!/* @__PURE__ */ isReadonly(array)) return raw.map(toReactive);
	return /* @__PURE__ */ isReactive(array) ? raw.map((item) => toReadonly(toReactive(item))) : raw.map(toReadonly);
}
function shallowReadArray(arr) {
	track(arr = /* @__PURE__ */ toRaw(arr), "iterate", ARRAY_ITERATE_KEY);
	return arr;
}
function toWrapped(target, item) {
	if (/* @__PURE__ */ isReadonly(target)) return /* @__PURE__ */ isReactive(target) ? toReadonly(toReactive(item)) : toReadonly(item);
	return toReactive(item);
}
var arrayInstrumentations = {
	__proto__: null,
	[Symbol.iterator]() {
		return iterator(this, Symbol.iterator, (item) => toWrapped(this, item));
	},
	concat(...args) {
		return reactiveReadArray(this).concat(...args.map((x) => isArray(x) ? reactiveReadArray(x) : x));
	},
	entries() {
		return iterator(this, "entries", (value) => {
			value[1] = toWrapped(this, value[1]);
			return value;
		});
	},
	every(fn, thisArg) {
		return apply(this, "every", fn, thisArg, void 0, arguments);
	},
	filter(fn, thisArg) {
		return apply(this, "filter", fn, thisArg, (v) => v.map((item) => toWrapped(this, item)), arguments);
	},
	find(fn, thisArg) {
		return apply(this, "find", fn, thisArg, (item) => toWrapped(this, item), arguments);
	},
	findIndex(fn, thisArg) {
		return apply(this, "findIndex", fn, thisArg, void 0, arguments);
	},
	findLast(fn, thisArg) {
		return apply(this, "findLast", fn, thisArg, (item) => toWrapped(this, item), arguments);
	},
	findLastIndex(fn, thisArg) {
		return apply(this, "findLastIndex", fn, thisArg, void 0, arguments);
	},
	forEach(fn, thisArg) {
		return apply(this, "forEach", fn, thisArg, void 0, arguments);
	},
	includes(...args) {
		return searchProxy(this, "includes", args);
	},
	indexOf(...args) {
		return searchProxy(this, "indexOf", args);
	},
	join(separator) {
		return reactiveReadArray(this).join(separator);
	},
	lastIndexOf(...args) {
		return searchProxy(this, "lastIndexOf", args);
	},
	map(fn, thisArg) {
		return apply(this, "map", fn, thisArg, void 0, arguments);
	},
	pop() {
		return noTracking(this, "pop");
	},
	push(...args) {
		return noTracking(this, "push", args);
	},
	reduce(fn, ...args) {
		return reduce(this, "reduce", fn, args);
	},
	reduceRight(fn, ...args) {
		return reduce(this, "reduceRight", fn, args);
	},
	shift() {
		return noTracking(this, "shift");
	},
	some(fn, thisArg) {
		return apply(this, "some", fn, thisArg, void 0, arguments);
	},
	splice(...args) {
		return noTracking(this, "splice", args);
	},
	toReversed() {
		return reactiveReadArray(this).toReversed();
	},
	toSorted(comparer) {
		return reactiveReadArray(this).toSorted(comparer);
	},
	toSpliced(...args) {
		return reactiveReadArray(this).toSpliced(...args);
	},
	unshift(...args) {
		return noTracking(this, "unshift", args);
	},
	values() {
		return iterator(this, "values", (item) => toWrapped(this, item));
	}
};
function iterator(self, method, wrapValue) {
	const arr = shallowReadArray(self);
	const methodFn = arr[method];
	const iter = methodFn.call(arr);
	if (arr !== self && !/* @__PURE__ */ isShallow(self)) {
		if (iter[Symbol.toStringTag] === "Array Iterator") {
			const wrapped = Object.create(Object.getPrototypeOf(iter));
			wrapped.next = () => wrapResult(iter.next(), wrapValue);
			if (methodFn !== arrayProto[method]) Object.defineProperties(wrapped, {
				return: { get() {
					const fn = iter.return;
					return typeof fn === "function" ? fn.bind(iter) : fn;
				} },
				throw: { get() {
					const fn = iter.throw;
					return typeof fn === "function" ? fn.bind(iter) : fn;
				} }
			});
			return wrapped;
		}
		iter._next = iter.next;
		iter.next = () => wrapResult(iter._next(), wrapValue);
	}
	return iter;
}
function wrapResult(result, wrapValue) {
	if (!result.done) result.value = wrapValue(result.value);
	return result;
}
var arrayProto = Array.prototype;
function apply(self, method, fn, thisArg, wrappedRetFn, args) {
	const arr = shallowReadArray(self);
	const needsWrap = arr !== self && !/* @__PURE__ */ isShallow(self);
	const methodFn = arr[method];
	if (methodFn !== arrayProto[method]) {
		const result2 = methodFn.apply(self, args);
		return needsWrap ? toReactive(result2) : result2;
	}
	let wrappedFn = fn;
	if (arr !== self) {
		if (needsWrap) wrappedFn = function(item, index) {
			return fn.call(this, toWrapped(self, item), index, self);
		};
		else if (fn.length > 2) wrappedFn = function(item, index) {
			return fn.call(this, item, index, self);
		};
	}
	const result = methodFn.call(arr, wrappedFn, thisArg);
	return needsWrap && wrappedRetFn ? wrappedRetFn(result) : result;
}
function reduce(self, method, fn, args) {
	const arr = shallowReadArray(self);
	const needsWrap = arr !== self && !/* @__PURE__ */ isShallow(self);
	let wrappedFn = fn;
	let wrapInitialAccumulator = false;
	if (arr !== self) {
		if (needsWrap) {
			wrapInitialAccumulator = args.length === 0;
			wrappedFn = function(acc, item, index) {
				if (wrapInitialAccumulator) {
					wrapInitialAccumulator = false;
					acc = toWrapped(self, acc);
				}
				return fn.call(this, acc, toWrapped(self, item), index, self);
			};
		} else if (fn.length > 3) wrappedFn = function(acc, item, index) {
			return fn.call(this, acc, item, index, self);
		};
	}
	const result = arr[method](wrappedFn, ...args);
	return wrapInitialAccumulator ? toWrapped(self, result) : result;
}
function searchProxy(self, method, args) {
	const arr = /* @__PURE__ */ toRaw(self);
	track(arr, "iterate", ARRAY_ITERATE_KEY);
	const res = arr[method](...args);
	if ((res === -1 || res === false) && /* @__PURE__ */ isProxy(args[0])) {
		args[0] = /* @__PURE__ */ toRaw(args[0]);
		return arr[method](...args);
	}
	return res;
}
function noTracking(self, method, args = []) {
	pauseTracking();
	startBatch();
	const res = (/* @__PURE__ */ toRaw(self))[method].apply(self, args);
	endBatch();
	resetTracking();
	return res;
}
var isNonTrackableKeys = /* @__PURE__ */ makeMap(`__proto__,__v_isRef,__isVue`);
var builtInSymbols = /* @__PURE__ */ new Set(/* @__PURE__ */ Object.getOwnPropertyNames(Symbol).filter((key) => key !== "arguments" && key !== "caller").map((key) => Symbol[key]).filter(isSymbol));
function hasOwnProperty(key) {
	if (!isSymbol(key)) key = String(key);
	const obj = /* @__PURE__ */ toRaw(this);
	track(obj, "has", key);
	return obj.hasOwnProperty(key);
}
var BaseReactiveHandler = class {
	constructor(_isReadonly = false, _isShallow = false) {
		this._isReadonly = _isReadonly;
		this._isShallow = _isShallow;
	}
	get(target, key, receiver) {
		if (key === "__v_skip") return target["__v_skip"];
		const isReadonly2 = this._isReadonly, isShallow2 = this._isShallow;
		if (key === "__v_isReactive") return !isReadonly2;
		else if (key === "__v_isReadonly") return isReadonly2;
		else if (key === "__v_isShallow") return isShallow2;
		else if (key === "__v_raw") {
			if (receiver === (isReadonly2 ? isShallow2 ? shallowReadonlyMap : readonlyMap : isShallow2 ? shallowReactiveMap : reactiveMap).get(target) || Object.getPrototypeOf(target) === Object.getPrototypeOf(receiver)) return target;
			return;
		}
		const targetIsArray = isArray(target);
		if (!isReadonly2) {
			let fn;
			if (targetIsArray && (fn = arrayInstrumentations[key])) return fn;
			if (key === "hasOwnProperty") return hasOwnProperty;
		}
		const res = Reflect.get(target, key, /* @__PURE__ */ isRef(target) ? target : receiver);
		if (isSymbol(key) ? builtInSymbols.has(key) : isNonTrackableKeys(key)) return res;
		if (!isReadonly2) track(target, "get", key);
		if (isShallow2) return res;
		if (/* @__PURE__ */ isRef(res)) {
			const value = targetIsArray && isIntegerKey(key) ? res : res.value;
			return isReadonly2 && isObject$2(value) ? /* @__PURE__ */ readonly$2(value) : value;
		}
		if (isObject$2(res)) return isReadonly2 ? /* @__PURE__ */ readonly$2(res) : /* @__PURE__ */ reactive(res);
		return res;
	}
};
var MutableReactiveHandler = class extends BaseReactiveHandler {
	constructor(isShallow2 = false) {
		super(false, isShallow2);
	}
	set(target, key, value, receiver) {
		let oldValue = target[key];
		const isArrayWithIntegerKey = isArray(target) && isIntegerKey(key);
		if (!this._isShallow) {
			const isOldValueReadonly = /* @__PURE__ */ isReadonly(oldValue);
			if (!/* @__PURE__ */ isShallow(value) && !/* @__PURE__ */ isReadonly(value)) {
				oldValue = /* @__PURE__ */ toRaw(oldValue);
				value = /* @__PURE__ */ toRaw(value);
			}
			if (!isArrayWithIntegerKey && /* @__PURE__ */ isRef(oldValue) && !/* @__PURE__ */ isRef(value)) {
				if (isOldValueReadonly) return true;
				else {
					oldValue.value = value;
					return true;
				}
			}
		}
		const hadKey = isArrayWithIntegerKey ? Number(key) < target.length : hasOwn(target, key);
		const result = Reflect.set(target, key, value, /* @__PURE__ */ isRef(target) ? target : receiver);
		if (target === /* @__PURE__ */ toRaw(receiver) && result) {
			if (!hadKey) trigger(target, "add", key, value);
			else if (hasChanged(value, oldValue)) trigger(target, "set", key, value, oldValue);
		}
		return result;
	}
	deleteProperty(target, key) {
		const hadKey = hasOwn(target, key);
		const oldValue = target[key];
		const result = Reflect.deleteProperty(target, key);
		if (result && hadKey) trigger(target, "delete", key, void 0, oldValue);
		return result;
	}
	has(target, key) {
		const result = Reflect.has(target, key);
		if (!isSymbol(key) || !builtInSymbols.has(key)) track(target, "has", key);
		return result;
	}
	ownKeys(target) {
		track(target, "iterate", isArray(target) ? "length" : ITERATE_KEY);
		return Reflect.ownKeys(target);
	}
};
var ReadonlyReactiveHandler = class extends BaseReactiveHandler {
	constructor(isShallow2 = false) {
		super(true, isShallow2);
	}
	set(target, key) {
		return true;
	}
	deleteProperty(target, key) {
		return true;
	}
};
var mutableHandlers = /* @__PURE__ */ new MutableReactiveHandler();
var readonlyHandlers = /* @__PURE__ */ new ReadonlyReactiveHandler();
var toShallow = (value) => value;
var getProto = (v) => Reflect.getPrototypeOf(v);
function createIterableMethod(method, isReadonly2, isShallow2) {
	return function(...args) {
		const target = this["__v_raw"];
		const rawTarget = /* @__PURE__ */ toRaw(target);
		const targetIsMap = isMap$1(rawTarget);
		const isPair = method === "entries" || method === Symbol.iterator && targetIsMap;
		const isKeyOnly = method === "keys" && targetIsMap;
		const innerIterator = target[method](...args);
		const wrap = isShallow2 ? toShallow : isReadonly2 ? toReadonly : toReactive;
		!isReadonly2 && track(rawTarget, "iterate", isKeyOnly ? MAP_KEY_ITERATE_KEY : ITERATE_KEY);
		return extend$1(Object.create(innerIterator), { next() {
			const { value, done } = innerIterator.next();
			return done ? {
				value,
				done
			} : {
				value: isPair ? [wrap(value[0]), wrap(value[1])] : wrap(value),
				done
			};
		} });
	};
}
function createReadonlyMethod(type) {
	return function(...args) {
		return type === "delete" ? false : type === "clear" ? void 0 : this;
	};
}
function createInstrumentations(readonly, shallow) {
	const instrumentations = {
		get(key) {
			const target = this["__v_raw"];
			const rawTarget = /* @__PURE__ */ toRaw(target);
			const rawKey = /* @__PURE__ */ toRaw(key);
			if (!readonly) {
				if (hasChanged(key, rawKey)) track(rawTarget, "get", key);
				track(rawTarget, "get", rawKey);
			}
			const { has } = getProto(rawTarget);
			const wrap = shallow ? toShallow : readonly ? toReadonly : toReactive;
			if (has.call(rawTarget, key)) return wrap(target.get(key));
			else if (has.call(rawTarget, rawKey)) return wrap(target.get(rawKey));
			else if (target !== rawTarget) target.get(key);
		},
		get size() {
			const target = this["__v_raw"];
			!readonly && track(/* @__PURE__ */ toRaw(target), "iterate", ITERATE_KEY);
			return target.size;
		},
		has(key) {
			const target = this["__v_raw"];
			const rawTarget = /* @__PURE__ */ toRaw(target);
			const rawKey = /* @__PURE__ */ toRaw(key);
			if (!readonly) {
				if (hasChanged(key, rawKey)) track(rawTarget, "has", key);
				track(rawTarget, "has", rawKey);
			}
			return key === rawKey ? target.has(key) : target.has(key) || target.has(rawKey);
		},
		forEach(callback, thisArg) {
			const observed = this;
			const target = observed["__v_raw"];
			const rawTarget = /* @__PURE__ */ toRaw(target);
			const wrap = shallow ? toShallow : readonly ? toReadonly : toReactive;
			!readonly && track(rawTarget, "iterate", ITERATE_KEY);
			return target.forEach((value, key) => {
				return callback.call(thisArg, wrap(value), wrap(key), observed);
			});
		}
	};
	extend$1(instrumentations, readonly ? {
		add: createReadonlyMethod("add"),
		set: createReadonlyMethod("set"),
		delete: createReadonlyMethod("delete"),
		clear: createReadonlyMethod("clear")
	} : {
		add(value) {
			const target = /* @__PURE__ */ toRaw(this);
			const proto = getProto(target);
			const rawValue = /* @__PURE__ */ toRaw(value);
			const valueToAdd = !shallow && !/* @__PURE__ */ isShallow(value) && !/* @__PURE__ */ isReadonly(value) ? rawValue : value;
			if (!(proto.has.call(target, valueToAdd) || hasChanged(value, valueToAdd) && proto.has.call(target, value) || hasChanged(rawValue, valueToAdd) && proto.has.call(target, rawValue))) {
				target.add(valueToAdd);
				trigger(target, "add", valueToAdd, valueToAdd);
			}
			return this;
		},
		set(key, value) {
			if (!shallow && !/* @__PURE__ */ isShallow(value) && !/* @__PURE__ */ isReadonly(value)) value = /* @__PURE__ */ toRaw(value);
			const target = /* @__PURE__ */ toRaw(this);
			const { has, get } = getProto(target);
			let hadKey = has.call(target, key);
			if (!hadKey) {
				key = /* @__PURE__ */ toRaw(key);
				hadKey = has.call(target, key);
			}
			const oldValue = get.call(target, key);
			target.set(key, value);
			if (!hadKey) trigger(target, "add", key, value);
			else if (hasChanged(value, oldValue)) trigger(target, "set", key, value, oldValue);
			return this;
		},
		delete(key) {
			const target = /* @__PURE__ */ toRaw(this);
			const { has, get } = getProto(target);
			let hadKey = has.call(target, key);
			if (!hadKey) {
				key = /* @__PURE__ */ toRaw(key);
				hadKey = has.call(target, key);
			}
			const oldValue = get ? get.call(target, key) : void 0;
			const result = target.delete(key);
			if (hadKey) trigger(target, "delete", key, void 0, oldValue);
			return result;
		},
		clear() {
			const target = /* @__PURE__ */ toRaw(this);
			const hadItems = target.size !== 0;
			const oldTarget = void 0;
			const result = target.clear();
			if (hadItems) trigger(target, "clear", void 0, void 0, oldTarget);
			return result;
		}
	});
	[
		"keys",
		"values",
		"entries",
		Symbol.iterator
	].forEach((method) => {
		instrumentations[method] = createIterableMethod(method, readonly, shallow);
	});
	return instrumentations;
}
function createInstrumentationGetter(isReadonly2, shallow) {
	const instrumentations = createInstrumentations(isReadonly2, shallow);
	return (target, key, receiver) => {
		if (key === "__v_isReactive") return !isReadonly2;
		else if (key === "__v_isReadonly") return isReadonly2;
		else if (key === "__v_isShallow") return shallow;
		else if (key === "__v_raw") return target;
		return Reflect.get(hasOwn(instrumentations, key) && key in target ? instrumentations : target, key, receiver);
	};
}
var mutableCollectionHandlers = { get: /* @__PURE__ */ createInstrumentationGetter(false, false) };
var readonlyCollectionHandlers = { get: /* @__PURE__ */ createInstrumentationGetter(true, false) };
var reactiveMap = /* @__PURE__ */ new WeakMap();
var shallowReactiveMap = /* @__PURE__ */ new WeakMap();
var readonlyMap = /* @__PURE__ */ new WeakMap();
var shallowReadonlyMap = /* @__PURE__ */ new WeakMap();
function targetTypeMap(rawType) {
	switch (rawType) {
		case "Object":
		case "Array": return 1;
		case "Map":
		case "Set":
		case "WeakMap":
		case "WeakSet": return 2;
		default: return 0;
	}
}
// @__NO_SIDE_EFFECTS__
function reactive(target) {
	if (/* @__PURE__ */ isReadonly(target)) return target;
	return createReactiveObject(target, false, mutableHandlers, mutableCollectionHandlers, reactiveMap);
}
// @__NO_SIDE_EFFECTS__
function readonly$2(target) {
	return createReactiveObject(target, true, readonlyHandlers, readonlyCollectionHandlers, readonlyMap);
}
function createReactiveObject(target, isReadonly2, baseHandlers, collectionHandlers, proxyMap) {
	if (!isObject$2(target)) return target;
	if (target["__v_raw"] && !(isReadonly2 && target["__v_isReactive"])) return target;
	if (target["__v_skip"] || !Object.isExtensible(target)) return target;
	const existingProxy = proxyMap.get(target);
	if (existingProxy) return existingProxy;
	const targetType = targetTypeMap(toRawType(target));
	if (targetType === 0) return target;
	const proxy = new Proxy(target, targetType === 2 ? collectionHandlers : baseHandlers);
	proxyMap.set(target, proxy);
	return proxy;
}
// @__NO_SIDE_EFFECTS__
function isReactive(value) {
	if (/* @__PURE__ */ isReadonly(value)) return /* @__PURE__ */ isReactive(value["__v_raw"]);
	return !!(value && value["__v_isReactive"]);
}
// @__NO_SIDE_EFFECTS__
function isReadonly(value) {
	return !!(value && value["__v_isReadonly"]);
}
// @__NO_SIDE_EFFECTS__
function isShallow(value) {
	return !!(value && value["__v_isShallow"]);
}
// @__NO_SIDE_EFFECTS__
function isProxy(value) {
	return value ? !!value["__v_raw"] : false;
}
// @__NO_SIDE_EFFECTS__
function toRaw(observed) {
	const raw = observed && observed["__v_raw"];
	return raw ? /* @__PURE__ */ toRaw(raw) : observed;
}
function markRaw(value) {
	if (!hasOwn(value, "__v_skip") && Object.isExtensible(value)) def(value, "__v_skip", true);
	return value;
}
var toReactive = (value) => isObject$2(value) ? /* @__PURE__ */ reactive(value) : value;
var toReadonly = (value) => isObject$2(value) ? /* @__PURE__ */ readonly$2(value) : value;
// @__NO_SIDE_EFFECTS__
function isRef(r) {
	return r ? r["__v_isRef"] === true : false;
}
// @__NO_SIDE_EFFECTS__
function ref$1(value) {
	return createRef(value, false);
}
// @__NO_SIDE_EFFECTS__
function shallowRef(value) {
	return createRef(value, true);
}
function createRef(rawValue, shallow) {
	if (/* @__PURE__ */ isRef(rawValue)) return rawValue;
	return new RefImpl(rawValue, shallow);
}
var RefImpl = class {
	constructor(value, isShallow2) {
		this.dep = new Dep();
		this["__v_isRef"] = true;
		this["__v_isShallow"] = false;
		this._rawValue = isShallow2 ? value : /* @__PURE__ */ toRaw(value);
		this._value = isShallow2 ? value : toReactive(value);
		this["__v_isShallow"] = isShallow2;
	}
	get value() {
		this.dep.track();
		return this._value;
	}
	set value(newValue) {
		const oldValue = this._rawValue;
		const useDirectValue = this["__v_isShallow"] || /* @__PURE__ */ isShallow(newValue) || /* @__PURE__ */ isReadonly(newValue);
		newValue = useDirectValue ? newValue : /* @__PURE__ */ toRaw(newValue);
		if (hasChanged(newValue, oldValue)) {
			this._rawValue = newValue;
			this._value = useDirectValue ? newValue : toReactive(newValue);
			this.dep.trigger();
		}
	}
};
function unref$1(ref2) {
	return /* @__PURE__ */ isRef(ref2) ? ref2.value : ref2;
}
function toValue(source) {
	return isFunction(source) ? source() : unref$1(source);
}
var shallowUnwrapHandlers = {
	get: (target, key, receiver) => key === "__v_raw" ? target : unref$1(Reflect.get(target, key, receiver)),
	set: (target, key, value, receiver) => {
		const oldValue = target[key];
		if (/* @__PURE__ */ isRef(oldValue) && !/* @__PURE__ */ isRef(value)) {
			oldValue.value = value;
			return true;
		} else return Reflect.set(target, key, value, receiver);
	}
};
function proxyRefs(objectWithRefs) {
	return /* @__PURE__ */ isReactive(objectWithRefs) ? objectWithRefs : new Proxy(objectWithRefs, shallowUnwrapHandlers);
}
var CustomRefImpl = class {
	constructor(factory) {
		this["__v_isRef"] = true;
		this._value = void 0;
		const dep = this.dep = new Dep();
		const { get, set } = factory(dep.track.bind(dep), dep.trigger.bind(dep));
		this._get = get;
		this._set = set;
	}
	get value() {
		return this._value = this._get();
	}
	set value(newVal) {
		this._set(newVal);
	}
};
function customRef(factory) {
	return new CustomRefImpl(factory);
}
var ObjectRefImpl = class {
	constructor(_object, key, _defaultValue) {
		this._object = _object;
		this._defaultValue = _defaultValue;
		this["__v_isRef"] = true;
		this._value = void 0;
		this._key = isSymbol(key) ? key : String(key);
		this._raw = /* @__PURE__ */ toRaw(_object);
		let shallow = true;
		let obj = _object;
		if (!isArray(_object) || isSymbol(this._key) || !isIntegerKey(this._key)) do
			shallow = !/* @__PURE__ */ isProxy(obj) || /* @__PURE__ */ isShallow(obj);
		while (shallow && (obj = obj["__v_raw"]));
		this._shallow = shallow;
	}
	get value() {
		let val = this._object[this._key];
		if (this._shallow) val = unref$1(val);
		return this._value = val === void 0 ? this._defaultValue : val;
	}
	set value(newVal) {
		if (this._shallow && /* @__PURE__ */ isRef(this._raw[this._key])) {
			const nestedRef = this._object[this._key];
			if (/* @__PURE__ */ isRef(nestedRef)) {
				nestedRef.value = newVal;
				return;
			}
		}
		this._object[this._key] = newVal;
	}
	get dep() {
		return getDepFromReactive(this._raw, this._key);
	}
};
var GetterRefImpl = class {
	constructor(_getter) {
		this._getter = _getter;
		this["__v_isRef"] = true;
		this["__v_isReadonly"] = true;
		this._value = void 0;
	}
	get value() {
		return this._value = this._getter();
	}
};
// @__NO_SIDE_EFFECTS__
function toRef$1(source, key, defaultValue) {
	if (/* @__PURE__ */ isRef(source)) return source;
	else if (isFunction(source)) return new GetterRefImpl(source);
	else if (isObject$2(source) && arguments.length > 1) return propertyToRef(source, key, defaultValue);
	else return /* @__PURE__ */ ref$1(source);
}
function propertyToRef(source, key, defaultValue) {
	return new ObjectRefImpl(source, key, defaultValue);
}
var ComputedRefImpl = class {
	constructor(fn, setter, isSSR) {
		this.fn = fn;
		this.setter = setter;
		/**
		* @internal
		*/
		this._value = void 0;
		/**
		* @internal
		*/
		this.dep = new Dep(this);
		/**
		* @internal
		*/
		this.__v_isRef = true;
		/**
		* @internal
		*/
		this.deps = void 0;
		/**
		* @internal
		*/
		this.depsTail = void 0;
		/**
		* @internal
		*/
		this.flags = 16;
		/**
		* @internal
		*/
		this.globalVersion = globalVersion - 1;
		/**
		* @internal
		*/
		this.next = void 0;
		this.effect = this;
		this["__v_isReadonly"] = !setter;
		this.isSSR = isSSR;
	}
	/**
	* @internal
	*/
	notify() {
		this.flags |= 16;
		if (!(this.flags & 8) && activeSub !== this) {
			batch(this, true);
			return true;
		}
	}
	get value() {
		const link = this.dep.track();
		refreshComputed(this);
		if (link) link.version = this.dep.version;
		return this._value;
	}
	set value(newValue) {
		if (this.setter) this.setter(newValue);
	}
};
// @__NO_SIDE_EFFECTS__
function computed$1(getterOrOptions, debugOptions, isSSR = false) {
	let getter;
	let setter;
	if (isFunction(getterOrOptions)) getter = getterOrOptions;
	else {
		getter = getterOrOptions.get;
		setter = getterOrOptions.set;
	}
	return new ComputedRefImpl(getter, setter, isSSR);
}
var INITIAL_WATCHER_VALUE = {};
var cleanupMap = /* @__PURE__ */ new WeakMap();
var activeWatcher = void 0;
function onWatcherCleanup(cleanupFn, failSilently = false, owner = activeWatcher) {
	if (owner) {
		let cleanups = cleanupMap.get(owner);
		if (!cleanups) cleanupMap.set(owner, cleanups = []);
		cleanups.push(cleanupFn);
	}
}
function watch$1(source, cb, options = EMPTY_OBJ) {
	const { immediate, deep, once, scheduler, augmentJob, call } = options;
	const reactiveGetter = (source2) => {
		if (deep) return source2;
		if (/* @__PURE__ */ isShallow(source2) || deep === false || deep === 0) return traverse(source2, 1);
		return traverse(source2);
	};
	let effect;
	let getter;
	let cleanup;
	let boundCleanup;
	let forceTrigger = false;
	let isMultiSource = false;
	if (/* @__PURE__ */ isRef(source)) {
		getter = () => source.value;
		forceTrigger = /* @__PURE__ */ isShallow(source);
	} else if (/* @__PURE__ */ isReactive(source)) {
		getter = () => reactiveGetter(source);
		forceTrigger = true;
	} else if (isArray(source)) {
		isMultiSource = true;
		forceTrigger = source.some((s) => /* @__PURE__ */ isReactive(s) || /* @__PURE__ */ isShallow(s));
		getter = () => source.map((s) => {
			if (/* @__PURE__ */ isRef(s)) return s.value;
			else if (/* @__PURE__ */ isReactive(s)) return reactiveGetter(s);
			else if (isFunction(s)) return call ? call(s, 2) : s();
		});
	} else if (isFunction(source)) {
		if (cb) getter = call ? () => call(source, 2) : source;
		else getter = () => {
			if (cleanup) {
				pauseTracking();
				try {
					cleanup();
				} finally {
					resetTracking();
				}
			}
			const currentEffect = activeWatcher;
			activeWatcher = effect;
			try {
				return call ? call(source, 3, [boundCleanup]) : source(boundCleanup);
			} finally {
				activeWatcher = currentEffect;
			}
		};
	} else getter = NOOP;
	if (cb && deep) {
		const baseGetter = getter;
		const depth = deep === true ? Infinity : deep;
		getter = () => traverse(baseGetter(), depth);
	}
	const watchHandle = () => {
		effect.stop();
	};
	if (once && cb) {
		const _cb = cb;
		cb = (...args) => {
			try {
				return _cb(...args);
			} finally {
				watchHandle();
			}
		};
	}
	let oldValue = isMultiSource ? new Array(source.length).fill(INITIAL_WATCHER_VALUE) : INITIAL_WATCHER_VALUE;
	const job = (immediateFirstRun) => {
		if (!(effect.flags & 1) || !effect.dirty && !immediateFirstRun) return;
		if (cb) {
			const newValue = effect.run();
			if (immediateFirstRun || deep || forceTrigger || (isMultiSource ? newValue.some((v, i) => hasChanged(v, oldValue[i])) : hasChanged(newValue, oldValue))) {
				const currentWatcher = activeWatcher;
				pauseTracking();
				try {
					if (cleanup) cleanup();
					activeWatcher = effect;
					const args = [
						newValue,
						oldValue === INITIAL_WATCHER_VALUE ? void 0 : isMultiSource && oldValue[0] === INITIAL_WATCHER_VALUE ? [] : oldValue,
						boundCleanup
					];
					oldValue = newValue;
					call ? call(cb, 3, args) : cb(...args);
				} finally {
					activeWatcher = currentWatcher;
					resetTracking();
				}
			}
		} else effect.run();
	};
	if (augmentJob) augmentJob(job);
	effect = new ReactiveEffect(getter);
	effect.scheduler = scheduler ? () => scheduler(job, false) : job;
	boundCleanup = (fn) => onWatcherCleanup(fn, false, effect);
	cleanup = effect.onStop = () => {
		const cleanups = cleanupMap.get(effect);
		if (cleanups) {
			if (call) call(cleanups, 4);
			else for (const cleanup2 of cleanups) cleanup2();
			cleanupMap.delete(effect);
		}
	};
	if (cb) {
		if (immediate) job(true);
		else oldValue = effect.run();
	} else if (scheduler) scheduler(job.bind(null, true), true);
	else effect.run();
	watchHandle.pause = effect.pause.bind(effect);
	watchHandle.resume = effect.resume.bind(effect);
	watchHandle.stop = watchHandle;
	return watchHandle;
}
function traverse(value, depth = Infinity, seen) {
	if (depth <= 0 || !isObject$2(value) || value["__v_skip"]) return value;
	seen = seen || /* @__PURE__ */ new Map();
	if ((seen.get(value) || 0) >= depth) return value;
	seen.set(value, depth);
	depth--;
	if (/* @__PURE__ */ isRef(value)) traverse(value.value, depth, seen);
	else if (isArray(value)) for (let i = 0; i < value.length; i++) traverse(value[i], depth, seen);
	else if (isSet(value) || isMap$1(value)) value.forEach((v) => {
		traverse(v, depth, seen);
	});
	else if (isPlainObject$1(value)) {
		for (const key in value) traverse(value[key], depth, seen);
		for (const key of Object.getOwnPropertySymbols(value)) if (Object.prototype.propertyIsEnumerable.call(value, key)) traverse(value[key], depth, seen);
	}
	return value;
}
// node_modules/.pnpm/@vue+runtime-core@3.5.45/node_modules/@vue/runtime-core/dist/runtime-core.esm-bundler.js
/**
* @vue/runtime-core v3.5.45
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
function callWithErrorHandling(fn, instance, type, args) {
	try {
		return args ? fn(...args) : fn();
	} catch (err) {
		handleError(err, instance, type);
	}
}
function callWithAsyncErrorHandling(fn, instance, type, args) {
	if (isFunction(fn)) {
		const res = callWithErrorHandling(fn, instance, type, args);
		if (res && isPromise$1(res)) res.catch((err) => {
			handleError(err, instance, type, void 0, true);
		});
		return res;
	}
	if (isArray(fn)) {
		const values = [];
		for (let i = 0; i < fn.length; i++) values.push(callWithAsyncErrorHandling(fn[i], instance, type, args));
		return values;
	}
}
function handleError(err, instance, type, throwInDev = true, isAsyncError = false) {
	const contextVNode = instance ? instance.vnode : null;
	const { errorHandler, throwUnhandledErrorInProduction } = instance && instance.appContext.config || EMPTY_OBJ;
	if (instance) {
		let cur = instance.parent;
		const exposedInstance = instance.proxy;
		const errorInfo = `https://vuejs.org/error-reference/#runtime-${type}`;
		while (cur) {
			const errorCapturedHooks = cur.ec;
			if (errorCapturedHooks && !cur.isUnmounted && (!isAsyncError || cur.scope.active)) {
				for (let i = 0; i < errorCapturedHooks.length; i++) if (errorCapturedHooks[i](err, exposedInstance, errorInfo) === false) return;
			}
			cur = cur.parent;
		}
		if (errorHandler) {
			pauseTracking();
			callWithErrorHandling(errorHandler, null, 10, [
				err,
				exposedInstance,
				errorInfo
			]);
			resetTracking();
			return;
		}
	}
	logError(err, type, contextVNode, throwInDev, throwUnhandledErrorInProduction);
}
function logError(err, type, contextVNode, throwInDev = true, throwInProd = false) {
	if (throwInProd) throw err;
	else console.error(err);
}
var queue = [];
var flushIndex = -1;
var pendingPostFlushCbs = [];
var activePostFlushCbs = null;
var postFlushIndex = 0;
var resolvedPromise = /* @__PURE__ */ Promise.resolve();
var currentFlushPromise = null;
function nextTick(fn) {
	const p = currentFlushPromise || resolvedPromise;
	return fn ? p.then(this ? fn.bind(this) : fn) : p;
}
function findInsertionIndex(id) {
	let start = flushIndex + 1;
	let end = queue.length;
	while (start < end) {
		const middle = start + end >>> 1;
		const middleJob = queue[middle];
		const middleJobId = getId(middleJob);
		if (middleJobId < id || middleJobId === id && middleJob.flags & 2) start = middle + 1;
		else end = middle;
	}
	return start;
}
function queueJob(job) {
	if (!(job.flags & 1)) {
		const jobId = getId(job);
		const lastJob = queue[queue.length - 1];
		if (!lastJob || !(job.flags & 2) && jobId >= getId(lastJob)) queue.push(job);
		else queue.splice(findInsertionIndex(jobId), 0, job);
		job.flags |= 1;
		queueFlush();
	}
}
function queueFlush() {
	if (!currentFlushPromise) currentFlushPromise = resolvedPromise.then(flushJobs);
}
function queuePostFlushCb(cb) {
	if (!isArray(cb)) {
		if (activePostFlushCbs && cb.id === -1) activePostFlushCbs.splice(postFlushIndex + 1, 0, cb);
		else if (!(cb.flags & 1)) {
			pendingPostFlushCbs.push(cb);
			cb.flags |= 1;
		}
	} else for (let i = 0; i < cb.length; i++) pendingPostFlushCbs.push(cb[i]);
	queueFlush();
}
function flushPostFlushCbs(seen, skip, minUid = 0) {
	if (pendingPostFlushCbs.length) {
		let cbs = pendingPostFlushCbs;
		let kept = 0;
		if (skip) {
			cbs = [];
			for (let i = 0; i < pendingPostFlushCbs.length; i++) {
				const cb = pendingPostFlushCbs[i];
				if (cb.i !== void 0 ? !cb.i || cb.i.uid < minUid : skip.has(cb)) pendingPostFlushCbs[kept++] = cb;
				else cbs.push(cb);
			}
		}
		const deduped = [...new Set(cbs)].sort((a, b) => getId(a) - getId(b));
		pendingPostFlushCbs.length = kept;
		if (activePostFlushCbs) {
			for (let i = 0; i < deduped.length; i++) activePostFlushCbs.push(deduped[i]);
			return;
		}
		activePostFlushCbs = deduped;
		for (postFlushIndex = 0; postFlushIndex < activePostFlushCbs.length; postFlushIndex++) {
			const cb = activePostFlushCbs[postFlushIndex];
			if (cb.flags & 4) cb.flags &= -2;
			if (!(cb.flags & 8)) cb();
			cb.flags &= -2;
		}
		activePostFlushCbs = null;
		postFlushIndex = 0;
	}
}
var getId = (job) => job.id == null ? job.flags & 2 ? -1 : Infinity : job.id;
function flushJobs(seen) {
	try {
		for (flushIndex = 0; flushIndex < queue.length; flushIndex++) {
			const job = queue[flushIndex];
			if (job && !(job.flags & 8)) {
				if (job.flags & 4) job.flags &= -2;
				callWithErrorHandling(job, job.i, job.i ? 15 : 14);
				if (!(job.flags & 4)) job.flags &= -2;
			}
		}
	} finally {
		for (; flushIndex < queue.length; flushIndex++) {
			const job = queue[flushIndex];
			if (job) job.flags &= -2;
		}
		flushIndex = -1;
		queue.length = 0;
		flushPostFlushCbs(seen);
		currentFlushPromise = null;
		if (queue.length || pendingPostFlushCbs.length) flushJobs(seen);
	}
}
var currentRenderingInstance = null;
var currentScopeId = null;
function setCurrentRenderingInstance(instance) {
	const prev = currentRenderingInstance;
	currentRenderingInstance = instance;
	currentScopeId = instance && instance.type.__scopeId || null;
	return prev;
}
function withCtx(fn, ctx = currentRenderingInstance, isNonScopedSlot) {
	if (!ctx) return fn;
	if (fn._n) return fn;
	const renderFnWithContext = (...args) => {
		if (renderFnWithContext._d) setBlockTracking(-1);
		const prevInstance = setCurrentRenderingInstance(ctx);
		const prevStackSize = blockStack.length;
		let res;
		try {
			res = fn(...args);
		} finally {
			for (let i = blockStack.length; i > prevStackSize; i--) closeBlock();
			setCurrentRenderingInstance(prevInstance);
			if (renderFnWithContext._d) setBlockTracking(1);
		}
		return res;
	};
	renderFnWithContext._n = true;
	renderFnWithContext._c = true;
	renderFnWithContext._d = true;
	return renderFnWithContext;
}
function withDirectives(vnode, directives) {
	if (currentRenderingInstance === null) return vnode;
	const instance = getComponentPublicInstance(currentRenderingInstance);
	const bindings = vnode.dirs || (vnode.dirs = []);
	for (let i = 0; i < directives.length; i++) {
		let [dir, value, arg, modifiers = EMPTY_OBJ] = directives[i];
		if (dir) {
			if (isFunction(dir)) dir = {
				mounted: dir,
				updated: dir
			};
			if (dir.deep) traverse(value);
			bindings.push({
				dir,
				instance,
				value,
				oldValue: void 0,
				arg,
				modifiers
			});
		}
	}
	return vnode;
}
function inject(key, defaultValue, treatDefaultAsFactory = false) {
	const instance = getCurrentInstance();
	if (instance || currentApp) {
		let provides = currentApp ? currentApp._context.provides : instance ? instance.parent == null || instance.ce ? instance.vnode.appContext && instance.vnode.appContext.provides : instance.parent.provides : void 0;
		if (provides && key in provides) return provides[key];
		else if (arguments.length > 1) return treatDefaultAsFactory && isFunction(defaultValue) ? defaultValue.call(instance && instance.proxy) : defaultValue;
	}
}
var ssrContextKey = /* @__PURE__ */ Symbol.for("v-scx");
var useSSRContext = () => {
	{
		const ctx = inject(ssrContextKey);
		if (!ctx) {}
		return ctx;
	}
};
function watchSyncEffect(effect, options) {
	return doWatch(effect, null, { flush: "sync" });
}
function watch$2(source, cb, options) {
	return doWatch(source, cb, options);
}
function doWatch(source, cb, options = EMPTY_OBJ) {
	const { immediate, deep, flush, once } = options;
	const baseWatchOptions = extend$1({}, options);
	const runsImmediately = cb && immediate || !cb && flush !== "post";
	let ssrCleanup;
	if (isInSSRComponentSetup) {
		if (flush === "sync") {
			const ctx = useSSRContext();
			ssrCleanup = ctx.__watcherHandles || (ctx.__watcherHandles = []);
		} else if (!runsImmediately) {
			const watchStopHandle = () => {};
			watchStopHandle.stop = NOOP;
			watchStopHandle.resume = NOOP;
			watchStopHandle.pause = NOOP;
			return watchStopHandle;
		}
	}
	const instance = currentInstance;
	baseWatchOptions.call = (fn, type, args) => callWithAsyncErrorHandling(fn, instance, type, args);
	let isPre = false;
	if (flush === "post") baseWatchOptions.scheduler = (job) => {
		job.i = instance;
		queuePostRenderEffect(job, instance && instance.suspense);
	};
	else if (flush !== "sync") {
		isPre = true;
		baseWatchOptions.scheduler = (job, isFirstRun) => {
			if (isFirstRun) job();
			else queueJob(job);
		};
	}
	baseWatchOptions.augmentJob = (job) => {
		if (cb) job.flags |= 4;
		if (isPre) {
			job.flags |= 2;
			if (instance) {
				job.id = instance.uid;
				job.i = instance;
			}
		}
	};
	const watchHandle = watch$1(source, cb, baseWatchOptions);
	if (isInSSRComponentSetup) {
		if (ssrCleanup) ssrCleanup.push(watchHandle);
		else if (runsImmediately) watchHandle();
	}
	return watchHandle;
}
var pendingMounts = /* @__PURE__ */ new WeakMap();
var TeleportEndKey = /* @__PURE__ */ Symbol("_vte");
var isTeleport = (type) => type.__isTeleport;
var isTeleportDisabled = (props) => props && (props.disabled || props.disabled === "");
var isTeleportDeferred = (props) => props && (props.defer || props.defer === "");
var isTargetSVG = (target) => typeof SVGElement !== "undefined" && target instanceof SVGElement;
var isTargetMathML = (target) => typeof MathMLElement === "function" && target instanceof MathMLElement;
var resolveTarget = (props, select) => {
	const targetSelector = props && props.to;
	if (isString(targetSelector)) {
		if (!select) return null;
		else return select(targetSelector);
	} else return targetSelector;
};
var TeleportImpl = {
	name: "Teleport",
	__isTeleport: true,
	process(n1, n2, container, anchor, parentComponent, parentSuspense, namespace, slotScopeIds, optimized, internals) {
		const { mc: mountChildren, pc: patchChildren, pbc: patchBlockChildren, o: { insert, querySelector, createText, createComment, parentNode } } = internals;
		const disabled = isTeleportDisabled(n2.props);
		let { dynamicChildren } = n2;
		const mount = (vnode, container2, anchor2) => {
			if (vnode.shapeFlag & 16) mountChildren(vnode.children, container2, anchor2, parentComponent, parentSuspense, namespace, slotScopeIds, optimized);
		};
		const mountToTarget = (vnode = n2) => {
			const disabled2 = isTeleportDisabled(vnode.props);
			const target = vnode.target = resolveTarget(vnode.props, querySelector);
			const targetAnchor = prepareAnchor(target, vnode, createText, insert);
			if (target) {
				if (namespace !== "svg" && isTargetSVG(target)) namespace = "svg";
				else if (namespace !== "mathml" && isTargetMathML(target)) namespace = "mathml";
				if (parentComponent && parentComponent.isCE) (parentComponent.ce._teleportTargets || (parentComponent.ce._teleportTargets = /* @__PURE__ */ new Set())).add(target);
				if (!disabled2) {
					mount(vnode, target, targetAnchor);
					updateCssVars(vnode, false);
				}
			}
		};
		const queuePendingMount = (vnode) => {
			const mountJob = () => {
				if (pendingMounts.get(vnode) !== mountJob) return;
				pendingMounts.delete(vnode);
				if (isTeleportDisabled(vnode.props)) {
					const mountContainer = parentNode(vnode.el) || container;
					mount(vnode, mountContainer, vnode.anchor);
					updateCssVars(vnode, true);
				}
				mountToTarget(vnode);
			};
			pendingMounts.set(vnode, mountJob);
			queuePostRenderEffect(mountJob, parentSuspense);
		};
		if (n1 == null) {
			const placeholder = n2.el = createText("");
			const mainAnchor = n2.anchor = createText("");
			insert(placeholder, container, anchor);
			insert(mainAnchor, container, anchor);
			if (isTeleportDeferred(n2.props) || parentSuspense && parentSuspense.pendingBranch) {
				queuePendingMount(n2);
				return;
			}
			if (disabled) {
				mount(n2, container, mainAnchor);
				updateCssVars(n2, true);
			}
			mountToTarget();
		} else {
			n2.el = n1.el;
			const mainAnchor = n2.anchor = n1.anchor;
			const pendingMount = pendingMounts.get(n1);
			if (pendingMount) {
				pendingMount.flags |= 8;
				pendingMounts.delete(n1);
				queuePendingMount(n2);
				return;
			}
			n2.targetStart = n1.targetStart;
			const target = n2.target = n1.target;
			const targetAnchor = n2.targetAnchor = n1.targetAnchor;
			const wasDisabled = isTeleportDisabled(n1.props);
			const currentContainer = wasDisabled ? container : target;
			const currentAnchor = wasDisabled ? mainAnchor : targetAnchor;
			if (namespace === "svg" || isTargetSVG(target)) namespace = "svg";
			else if (namespace === "mathml" || isTargetMathML(target)) namespace = "mathml";
			if (dynamicChildren) {
				patchBlockChildren(n1.dynamicChildren, dynamicChildren, currentContainer, parentComponent, parentSuspense, namespace, slotScopeIds);
				traverseStaticChildren(n1, n2, true);
			} else if (!optimized) patchChildren(n1, n2, currentContainer, currentAnchor, parentComponent, parentSuspense, namespace, slotScopeIds, false);
			if (disabled) {
				if (!wasDisabled) moveTeleport(n2, container, mainAnchor, internals, 1);
				else if (n2.props && n1.props && n2.props.to !== n1.props.to) n2.props.to = n1.props.to;
			} else if ((n2.props && n2.props.to) !== (n1.props && n1.props.to)) {
				const nextTarget = resolveTarget(n2.props, querySelector);
				if (nextTarget) {
					n2.target = nextTarget;
					moveTeleport(n2, nextTarget, null, internals, 0);
				}
			} else if (wasDisabled) moveTeleport(n2, target, targetAnchor, internals, 1);
			updateCssVars(n2, disabled);
		}
	},
	remove(vnode, parentComponent, parentSuspense, { um: unmount, o: { remove: hostRemove } }, doRemove) {
		const { shapeFlag, children, anchor, targetStart, targetAnchor, target, props } = vnode;
		const disabled = isTeleportDisabled(props);
		const shouldRemove = doRemove || !disabled;
		const pendingMount = pendingMounts.get(vnode);
		if (pendingMount) {
			pendingMount.flags |= 8;
			pendingMounts.delete(vnode);
		}
		if (target) {
			hostRemove(targetStart);
			hostRemove(targetAnchor);
		}
		doRemove && hostRemove(anchor);
		if (!pendingMount && (disabled || target) && shapeFlag & 16) for (let i = 0; i < children.length; i++) {
			const child = children[i];
			unmount(child, parentComponent, parentSuspense, shouldRemove, !!child.dynamicChildren);
		}
	},
	move: moveTeleport,
	hydrate: hydrateTeleport
};
function moveTeleport(vnode, container, parentAnchor, { o: { insert }, m: move }, moveType = 2) {
	if (moveType === 0) {
		if (vnode.targetStart) insert(vnode.targetStart, container, parentAnchor);
		insert(vnode.targetAnchor, container, parentAnchor);
		parentAnchor = vnode.targetAnchor;
	}
	const { el, anchor, shapeFlag, children, props } = vnode;
	const isReorder = moveType === 2;
	if (isReorder) insert(el, container, parentAnchor);
	if (!pendingMounts.has(vnode) && (!isReorder || isTeleportDisabled(props))) {
		if (shapeFlag & 16) for (let i = 0; i < children.length; i++) move(children[i], container, parentAnchor, 2);
	}
	if (isReorder) insert(anchor, container, parentAnchor);
}
function hydrateTeleport(node, vnode, parentComponent, parentSuspense, slotScopeIds, optimized, { o: { nextSibling, parentNode, querySelector, insert, createText } }, hydrateChildren) {
	function hydrateAnchor(target2, targetNode) {
		let targetAnchor = targetNode;
		while (targetAnchor) {
			if (targetAnchor && targetAnchor.nodeType === 8) {
				if (targetAnchor.data === "teleport start anchor") vnode.targetStart = targetAnchor;
				else if (targetAnchor.data === "teleport anchor") {
					vnode.targetAnchor = targetAnchor;
					target2._lpa = vnode.targetAnchor && nextSibling(vnode.targetAnchor);
					break;
				}
			}
			targetAnchor = nextSibling(targetAnchor);
		}
	}
	function hydrateDisabledTeleport(node2, vnode2) {
		vnode2.anchor = hydrateChildren(nextSibling(node2), vnode2, parentNode(node2), parentComponent, parentSuspense, slotScopeIds, optimized);
	}
	const target = vnode.target = resolveTarget(vnode.props, querySelector);
	const disabled = isTeleportDisabled(vnode.props);
	if (target) {
		const targetNode = target._lpa || target.firstChild;
		if (vnode.shapeFlag & 16) {
			if (disabled) {
				hydrateDisabledTeleport(node, vnode);
				hydrateAnchor(target, targetNode);
				if (!vnode.targetAnchor) prepareAnchor(target, vnode, createText, insert, parentNode(node) === target ? node : null);
			} else {
				vnode.anchor = nextSibling(node);
				hydrateAnchor(target, targetNode);
				if (!vnode.targetAnchor) prepareAnchor(target, vnode, createText, insert);
				hydrateChildren(targetNode && nextSibling(targetNode), vnode, target, parentComponent, parentSuspense, slotScopeIds, optimized);
			}
		}
		updateCssVars(vnode, disabled);
	} else if (disabled) {
		if (vnode.shapeFlag & 16) {
			hydrateDisabledTeleport(node, vnode);
			vnode.targetStart = node;
			vnode.targetAnchor = nextSibling(node);
		}
	}
	return vnode.anchor && nextSibling(vnode.anchor);
}
var Teleport = TeleportImpl;
function updateCssVars(vnode, isDisabled) {
	const ctx = vnode.ctx;
	if (ctx && ctx.ut) {
		let node, anchor;
		if (isDisabled) {
			node = vnode.el;
			anchor = vnode.anchor;
		} else {
			node = vnode.targetStart;
			anchor = vnode.targetAnchor;
		}
		while (node && node !== anchor) {
			if (node.nodeType === 1) node.setAttribute("data-v-owner", ctx.uid);
			node = node.nextSibling;
		}
		ctx.ut();
	}
}
function prepareAnchor(target, vnode, createText, insert, anchor = null) {
	const targetStart = vnode.targetStart = createText("");
	const targetAnchor = vnode.targetAnchor = createText("");
	targetStart[TeleportEndKey] = targetAnchor;
	if (target) {
		insert(targetStart, target, anchor);
		insert(targetAnchor, target, anchor);
	}
	return targetAnchor;
}
var leaveCbKey = /* @__PURE__ */ Symbol("_leaveCb");
var enterCbKey = /* @__PURE__ */ Symbol("_enterCb");
function useTransitionState() {
	const state = {
		isMounted: false,
		isLeaving: false,
		isUnmounting: false,
		leavingVNodes: /* @__PURE__ */ new Map()
	};
	onMounted$1(() => {
		state.isMounted = true;
	});
	onBeforeUnmount$1(() => {
		state.isUnmounting = true;
	});
	return state;
}
var TransitionHookValidator = [Function, Array];
var BaseTransitionPropsValidators = {
	mode: String,
	appear: Boolean,
	persisted: Boolean,
	onBeforeEnter: TransitionHookValidator,
	onEnter: TransitionHookValidator,
	onAfterEnter: TransitionHookValidator,
	onEnterCancelled: TransitionHookValidator,
	onBeforeLeave: TransitionHookValidator,
	onLeave: TransitionHookValidator,
	onAfterLeave: TransitionHookValidator,
	onLeaveCancelled: TransitionHookValidator,
	onBeforeAppear: TransitionHookValidator,
	onAppear: TransitionHookValidator,
	onAfterAppear: TransitionHookValidator,
	onAppearCancelled: TransitionHookValidator
};
var BaseTransitionImpl = {
	name: `BaseTransition`,
	props: BaseTransitionPropsValidators,
	setup(props, { slots }) {
		const instance = getCurrentInstance();
		const state = useTransitionState();
		return () => {
			const children = slots.default && getTransitionRawChildren(slots.default(), true);
			const child = children && children.length ? findNonCommentChild(children) : instance.subTree ? createCommentVNode() : void 0;
			if (!child) return;
			const rawProps = /* @__PURE__ */ toRaw(props);
			const { mode } = rawProps;
			if (state.isLeaving) return emptyPlaceholder(child);
			const innerChild = getInnerChild$1(child);
			if (!innerChild) return emptyPlaceholder(child);
			const enterHooks = resolveTransitionHooks(innerChild, rawProps, state, instance);
			if (innerChild.type !== Comment) setTransitionHooks(innerChild, enterHooks);
			let oldInnerChild = instance.subTree && getInnerChild$1(instance.subTree);
			if (oldInnerChild && oldInnerChild.type !== Comment && !isSameVNodeType(oldInnerChild, innerChild) && getTransitionRoot(instance.subTree)) {
				let leavingHooks = resolveTransitionHooks(oldInnerChild, rawProps, state, instance);
				setTransitionHooks(oldInnerChild, leavingHooks);
				if (mode === "out-in" && innerChild.type !== Comment) {
					state.isLeaving = true;
					leavingHooks.afterLeave = () => {
						state.isLeaving = false;
						if (!(instance.job.flags & 8)) {
							if (instance.effect.flags & 2) {
								const job = () => {
									if (!(instance.job.flags & 8)) instance.update();
								};
								job.i = instance;
								queueJob(job);
							} else instance.update();
						}
						delete leavingHooks.afterLeave;
						oldInnerChild = void 0;
					};
					return emptyPlaceholder(child);
				} else if (mode === "in-out" && innerChild.type !== Comment) leavingHooks.delayLeave = (el, earlyRemove, delayedLeave) => {
					const leavingVNodesCache = getLeavingNodesForType(state, oldInnerChild);
					leavingVNodesCache[String(oldInnerChild.key)] = oldInnerChild;
					el[leaveCbKey] = () => {
						earlyRemove();
						el[leaveCbKey] = void 0;
						delete enterHooks.delayedLeave;
						oldInnerChild = void 0;
					};
					enterHooks.delayedLeave = () => {
						delayedLeave();
						delete enterHooks.delayedLeave;
						oldInnerChild = void 0;
					};
				};
				else oldInnerChild = void 0;
			} else if (oldInnerChild) oldInnerChild = void 0;
			return child;
		};
	}
};
function findNonCommentChild(children) {
	let child = children[0];
	if (children.length > 1) {
		for (const c of children) if (c.type !== Comment) {
			child = c;
			break;
		}
	}
	return child;
}
var BaseTransition = BaseTransitionImpl;
function getLeavingNodesForType(state, vnode) {
	const { leavingVNodes } = state;
	let leavingVNodesCache = leavingVNodes.get(vnode.type);
	if (!leavingVNodesCache) {
		leavingVNodesCache = /* @__PURE__ */ Object.create(null);
		leavingVNodes.set(vnode.type, leavingVNodesCache);
	}
	return leavingVNodesCache;
}
function resolveTransitionHooks(vnode, props, state, instance) {
	const { appear, mode, persisted = false, onBeforeEnter, onEnter, onAfterEnter, onEnterCancelled, onBeforeLeave, onLeave, onAfterLeave, onLeaveCancelled, onBeforeAppear, onAppear, onAfterAppear, onAppearCancelled } = props;
	const key = String(vnode.key);
	const leavingVNodesCache = getLeavingNodesForType(state, vnode);
	const callHook = (hook, args) => {
		hook && callWithAsyncErrorHandling(hook, instance, 9, args);
	};
	const callAsyncHook = (hook, args) => {
		const done = args[1];
		callHook(hook, args);
		if (isArray(hook)) {
			if (hook.every((hook2) => hook2.length <= 1)) done();
		} else if (hook.length <= 1) done();
	};
	const hooks = {
		mode,
		persisted,
		beforeEnter(el) {
			let hook = onBeforeEnter;
			if (!state.isMounted) {
				if (appear) hook = onBeforeAppear || onBeforeEnter;
				else return;
			}
			if (el[leaveCbKey]) el[leaveCbKey](true);
			const leavingVNode = leavingVNodesCache[key];
			if (leavingVNode && isSameVNodeType(vnode, leavingVNode)) {
				let leavingCb = leavingVNode.el[leaveCbKey];
				if (!leavingCb) {
					const leavingRoot = getTransitionRoot(leavingVNode);
					leavingCb = leavingRoot && leavingRoot.el[leaveCbKey];
				}
				if (leavingCb) leavingCb();
			}
			callHook(hook, [el]);
		},
		enter(el) {
			if (leavingVNodesCache[key] === vnode) return;
			let hook = onEnter;
			let afterHook = onAfterEnter;
			let cancelHook = onEnterCancelled;
			if (!state.isMounted) {
				if (appear) {
					hook = onAppear || onEnter;
					afterHook = onAfterAppear || onAfterEnter;
					cancelHook = onAppearCancelled || onEnterCancelled;
				} else return;
			}
			let called = false;
			el[enterCbKey] = (cancelled) => {
				if (called) return;
				called = true;
				if (cancelled) callHook(cancelHook, [el]);
				else callHook(afterHook, [el]);
				if (hooks.delayedLeave) hooks.delayedLeave();
				el[enterCbKey] = void 0;
			};
			const done = el[enterCbKey].bind(null, false);
			if (hook) callAsyncHook(hook, [el, done]);
			else done();
		},
		leave(el, remove) {
			const key2 = String(vnode.key);
			if (el[enterCbKey]) el[enterCbKey](true);
			if (state.isUnmounting) return remove();
			callHook(onBeforeLeave, [el]);
			let called = false;
			el[leaveCbKey] = (cancelled) => {
				if (called) return;
				called = true;
				remove();
				if (cancelled) callHook(onLeaveCancelled, [el]);
				else callHook(onAfterLeave, [el]);
				el[leaveCbKey] = void 0;
				if (leavingVNodesCache[key2] === vnode) delete leavingVNodesCache[key2];
			};
			const done = el[leaveCbKey].bind(null, false);
			leavingVNodesCache[key2] = vnode;
			if (onLeave) callAsyncHook(onLeave, [el, done]);
			else done();
		},
		clone(vnode2) {
			const cloned = resolveTransitionHooks(vnode2, props, state, instance);
			cloned.afterLeave = () => {
				if (hooks.afterLeave) hooks.afterLeave();
			};
			cloned.delayLeave = (el, earlyRemove, delayedLeave) => {
				if (hooks.delayLeave) hooks.delayLeave(el, earlyRemove, delayedLeave);
				else delayedLeave();
			};
			cloned.delayedLeave = () => {
				if (hooks.delayedLeave) hooks.delayedLeave();
			};
			return cloned;
		}
	};
	return hooks;
}
function emptyPlaceholder(vnode) {
	if (isKeepAlive(vnode)) {
		vnode = cloneVNode(vnode);
		vnode.children = null;
		return vnode;
	}
}
function getInnerChild$1(vnode) {
	if (!isKeepAlive(vnode)) {
		if (isTeleport(vnode.type) && vnode.children) return findNonCommentChild(vnode.children);
		return vnode;
	}
	if (vnode.component) return vnode.component.subTree;
	const { shapeFlag, children } = vnode;
	if (children) {
		if (shapeFlag & 16) return children[0];
		if (shapeFlag & 32 && isFunction(children.default)) return children.default();
	}
}
function setTransitionHooks(vnode, hooks) {
	if (vnode.shapeFlag & 6 && vnode.component) {
		vnode.transition = hooks;
		const subTree = vnode.component.subTree;
		setTransitionHooks(isTeleport(subTree.type) ? getInnerChild$1(subTree) || subTree : subTree, hooks);
	} else if (vnode.shapeFlag & 128) {
		const suspense = vnode.suspense;
		if (suspense) {
			const branch = suspense.isHydrating ? suspense.pendingBranch : suspense.activeBranch;
			if (branch) setTransitionHooks(branch, hooks.clone(branch));
		} else {
			vnode.ssContent.transition = hooks.clone(vnode.ssContent);
			vnode.ssFallback.transition = hooks.clone(vnode.ssFallback);
		}
	} else {
		vnode.transition = hooks;
		if (vnode.type === Fragment && isArray(vnode.children)) {
			const child = filterSingleRoot(vnode.children, false);
			if (child) setTransitionHooks(child, hooks);
		}
	}
}
function getTransitionRawChildren(children, keepComment = false, parentKey) {
	let ret = [];
	let keyedFragmentCount = 0;
	for (let i = 0; i < children.length; i++) {
		let child = children[i];
		const key = parentKey == null ? child.key : String(parentKey) + String(child.key != null ? child.key : i);
		if (child.type === Fragment) {
			if (child.patchFlag & 128) keyedFragmentCount++;
			ret = ret.concat(getTransitionRawChildren(child.children, keepComment, key));
		} else if (keepComment || child.type !== Comment) ret.push(key != null ? cloneVNode(child, { key }) : child);
	}
	if (keyedFragmentCount > 1) for (let i = 0; i < ret.length; i++) ret[i].patchFlag = -2;
	return ret;
}
function getTransitionRoot(vnode) {
	if (vnode.component) return getTransitionRoot(vnode.component.subTree);
	if (vnode.type === Fragment) {
		const child = isArray(vnode.children) && filterSingleRoot(vnode.children, false);
		return child ? getTransitionRoot(child) : void 0;
	}
	if (vnode.type !== Comment) return vnode;
}
// @__NO_SIDE_EFFECTS__
function defineComponent(options, extraOptions) {
	return isFunction(options) ? /* @__PURE__ */ (() => extend$1({ name: options.name }, extraOptions, { setup: options }))() : options;
}
getGlobalThis().requestIdleCallback;
getGlobalThis().cancelIdleCallback;
var isAsyncWrapper = (i) => !!i.type.__asyncLoader;
var isKeepAlive = (vnode) => vnode.type.__isKeepAlive;
function injectHook(type, hook, target = currentInstance, prepend = false) {
	if (target) {
		const hooks = target[type] || (target[type] = []);
		const wrappedHook = hook.__weh || (hook.__weh = (...args) => {
			pauseTracking();
			const reset = setCurrentInstance(target);
			const res = callWithAsyncErrorHandling(hook, target, type, args);
			reset();
			resetTracking();
			return res;
		});
		if (prepend) hooks.unshift(wrappedHook);
		else hooks.push(wrappedHook);
		return wrappedHook;
	}
}
var createHook = (lifecycle) => (hook, target = currentInstance) => {
	if (!isInSSRComponentSetup || lifecycle === "sp") injectHook(lifecycle, (...args) => hook(...args), target);
};
var onMounted$1 = createHook("m");
var onBeforeUnmount$1 = createHook("bum");
var onUnmounted$1 = createHook("um");
var NULL_DYNAMIC_COMPONENT = /* @__PURE__ */ Symbol.for("v-ndc");
function renderList(source, renderItem, cache, index) {
	let ret;
	const cached = cache && cache[index];
	const sourceIsArray = isArray(source);
	if (sourceIsArray || isString(source)) {
		const sourceIsReactiveArray = sourceIsArray && /* @__PURE__ */ isReactive(source);
		let needsWrap = false;
		let isReadonlySource = false;
		if (sourceIsReactiveArray) {
			needsWrap = !/* @__PURE__ */ isShallow(source);
			isReadonlySource = /* @__PURE__ */ isReadonly(source);
			source = shallowReadArray(source);
		}
		ret = new Array(source.length);
		for (let i = 0, l = source.length; i < l; i++) ret[i] = renderItem(needsWrap ? isReadonlySource ? toReadonly(toReactive(source[i])) : toReactive(source[i]) : source[i], i, void 0, cached && cached[i]);
	} else if (typeof source === "number") {
		if (!Number.isInteger(source) || source < 0) ret = [];
		else {
			ret = new Array(source);
			for (let i = 0; i < source; i++) ret[i] = renderItem(i + 1, i, void 0, cached && cached[i]);
		}
	} else if (isObject$2(source)) {
		if (source[Symbol.iterator]) ret = Array.from(source, (item, i) => renderItem(item, i, void 0, cached && cached[i]));
		else {
			const keys = Object.keys(source);
			ret = new Array(keys.length);
			for (let i = 0, l = keys.length; i < l; i++) {
				const key = keys[i];
				ret[i] = renderItem(source[key], key, i, cached && cached[i]);
			}
		}
	} else ret = [];
	if (cache) cache[index] = ret;
	return ret;
}
function renderSlot(slots, name, props, fallback, noSlotted, branchKey) {
	if (props == null) props = {};
	if (currentRenderingInstance.ce || currentRenderingInstance.parent && isAsyncWrapper(currentRenderingInstance.parent) && currentRenderingInstance.parent.ce) {
		const slotProps = branchKey != null && props.key == null ? extend$1({}, props, { key: branchKey }) : props;
		const hasProps = Object.keys(slotProps).length > 0;
		if (name !== "default") slotProps.name = name;
		return openBlock(), createBlock(Fragment, null, [createVNode("slot", slotProps, fallback && fallback())], hasProps ? -2 : 64);
	}
	let slot = slots[name];
	if (slot && slot._c) slot._d = false;
	const prevStackSize = blockStack.length;
	openBlock();
	let rendered;
	try {
		const validSlotContent = slot && ensureValidVNode(slot(props));
		const slotKey = props.key || branchKey || validSlotContent && validSlotContent.key;
		rendered = createBlock(Fragment, { key: (slotKey && !isSymbol(slotKey) ? slotKey : `_${name}`) + (!validSlotContent && fallback ? "_fb" : "") }, validSlotContent || (fallback ? fallback() : []), validSlotContent && slots._ === 1 ? 64 : -2);
	} catch (err) {
		for (let i = blockStack.length; i > prevStackSize; i--) closeBlock();
		throw err;
	} finally {
		if (slot && slot._c) slot._d = true;
	}
	if (!noSlotted && rendered.scopeId) rendered.slotScopeIds = [rendered.scopeId + "-s"];
	return rendered;
}
function ensureValidVNode(vnodes) {
	return vnodes.some((child) => {
		if (!isVNode(child)) return true;
		if (child.type === Comment) return false;
		if (child.type === Fragment && !ensureValidVNode(child.children)) return false;
		return true;
	}) ? vnodes : null;
}
function toHandlers(obj, preserveCaseIfNecessary) {
	const ret = {};
	for (const key in obj) ret[preserveCaseIfNecessary && /[A-Z]/.test(key) ? `on:${key}` : toHandlerKey(key)] = obj[key];
	return ret;
}
var getPublicInstance = (i) => {
	if (!i) return null;
	if (isStatefulComponent(i)) return getComponentPublicInstance(i);
	return getPublicInstance(i.parent);
};
var publicPropertiesMap = /* @__PURE__ */ extend$1(/* @__PURE__ */ Object.create(null), {
	$: (i) => i,
	$el: (i) => i.vnode.el,
	$data: (i) => i.data,
	$props: (i) => i.props,
	$attrs: (i) => i.attrs,
	$slots: (i) => i.slots,
	$refs: (i) => i.refs,
	$parent: (i) => getPublicInstance(i.parent),
	$root: (i) => getPublicInstance(i.root),
	$host: (i) => i.ce,
	$emit: (i) => i.emit,
	$options: (i) => i.type,
	$forceUpdate: (i) => i.f || (i.f = () => {
		queueJob(i.update);
	}),
	$nextTick: (i) => i.n || (i.n = nextTick.bind(i.proxy)),
	$watch: (i) => NOOP
});
function useAttrs() {
	return getContext$1("useAttrs").attrs;
}
function getContext$1(calledFunctionName) {
	const i = getCurrentInstance();
	return i.setupContext || (i.setupContext = createSetupContext(i));
}
function normalizePropsOrEmits(props) {
	return isArray(props) ? props.reduce((normalized, p) => (normalized[p] = null, normalized), {}) : props;
}
function mergeModels(a, b) {
	if (!a || !b) return a || b;
	if (isArray(a) && isArray(b)) return a.concat(b);
	return extend$1({}, normalizePropsOrEmits(a), normalizePropsOrEmits(b));
}
var currentApp = null;
function useModel(props, name, options = EMPTY_OBJ) {
	const i = getCurrentInstance();
	const camelizedName = camelize$1(name);
	const hyphenatedName = hyphenate$1(name);
	const modifiers = getModelModifiers(props, camelizedName);
	const res = customRef((track, trigger) => {
		let localValue;
		let prevSetValue = EMPTY_OBJ;
		let prevEmittedValue;
		watchSyncEffect(() => {
			const propValue = props[camelizedName];
			if (hasChanged(localValue, propValue)) {
				localValue = propValue;
				trigger();
			}
		});
		return {
			get() {
				track();
				return options.get ? options.get(localValue) : localValue;
			},
			set(value) {
				const emittedValue = options.set ? options.set(value) : value;
				if (!hasChanged(emittedValue, localValue) && !(prevSetValue !== EMPTY_OBJ && hasChanged(value, prevSetValue))) return;
				const rawProps = i.vnode.props;
				const hasVModel = !!(rawProps && (name in rawProps || camelizedName in rawProps || hyphenatedName in rawProps) && (`onUpdate:${name}` in rawProps || `onUpdate:${camelizedName}` in rawProps || `onUpdate:${hyphenatedName}` in rawProps));
				if (!hasVModel) {
					localValue = value;
					trigger();
				}
				i.emit(`update:${name}`, emittedValue);
				if (hasChanged(value, prevSetValue) && (hasChanged(value, emittedValue) && !hasChanged(emittedValue, prevEmittedValue) || hasVModel && prevSetValue !== EMPTY_OBJ && !hasChanged(emittedValue, localValue))) trigger();
				prevSetValue = value;
				prevEmittedValue = emittedValue;
			}
		};
	});
	res[Symbol.iterator] = () => {
		let i2 = 0;
		return { next() {
			if (i2 < 2) return {
				value: i2++ ? modifiers || EMPTY_OBJ : res,
				done: false
			};
			else return { done: true };
		} };
	};
	return res;
}
var getModelModifiers = (props, modelName) => {
	return modelName === "modelValue" || modelName === "model-value" ? props.modelModifiers : props[`${modelName}Modifiers`] || props[`${camelize$1(modelName)}Modifiers`] || props[`${hyphenate$1(modelName)}Modifiers`];
};
function filterSingleRoot(children, recurse = true) {
	let singleRoot;
	for (let i = 0; i < children.length; i++) {
		const child = children[i];
		if (isVNode(child)) {
			if (child.type !== Comment || child.children === "v-if") {
				if (singleRoot) return;
				else singleRoot = child;
			}
		} else return;
	}
	return singleRoot;
}
var internalObjectProto = {};
var isInternalObject = (obj) => Object.getPrototypeOf(obj) === internalObjectProto;
var queuePostRenderEffect = queueEffectWithSuspense;
function traverseStaticChildren(n1, n2, shallow = false) {
	const ch1 = n1.children;
	const ch2 = n2.children;
	if (isArray(ch1) && isArray(ch2)) for (let i = 0; i < ch1.length; i++) {
		const c1 = ch1[i];
		let c2 = ch2[i];
		if (c2.shapeFlag & 1 && !c2.dynamicChildren) {
			if (c2.patchFlag <= 0 || c2.patchFlag === 32) {
				c2 = ch2[i] = cloneIfMounted(ch2[i]);
				c2.el = c1.el;
			}
			if (!shallow && c2.patchFlag !== -2) traverseStaticChildren(c1, c2);
		}
		if (c2.type === Text) {
			if (c2.patchFlag === -1) c2 = ch2[i] = cloneIfMounted(c2);
			c2.el = c1.el;
		}
		if (c2.type === Comment && !c2.el) c2.el = c1.el;
	}
}
var isSuspense = (type) => type.__isSuspense;
function queueEffectWithSuspense(fn, suspense) {
	if (suspense && suspense.pendingBranch) {
		if (isArray(fn)) suspense.effects.push(...fn);
		else suspense.effects.push(fn);
	} else queuePostFlushCb(fn);
}
var Fragment = /* @__PURE__ */ Symbol.for("v-fgt");
var Text = /* @__PURE__ */ Symbol.for("v-txt");
var Comment = /* @__PURE__ */ Symbol.for("v-cmt");
var blockStack = [];
var currentBlock = null;
function openBlock(disableTracking = false) {
	blockStack.push(currentBlock = disableTracking ? null : []);
}
function closeBlock() {
	blockStack.pop();
	currentBlock = blockStack[blockStack.length - 1] || null;
}
var isBlockTreeEnabled = 1;
function setBlockTracking(value, inVOnce = false) {
	isBlockTreeEnabled += value;
	if (value < 0 && currentBlock && inVOnce) currentBlock.hasOnce = true;
}
function setupBlock(vnode) {
	vnode.dynamicChildren = isBlockTreeEnabled > 0 ? currentBlock || EMPTY_ARR : null;
	closeBlock();
	if (isBlockTreeEnabled > 0 && currentBlock) currentBlock.push(vnode);
	return vnode;
}
function createElementBlock(type, props, children, patchFlag, dynamicProps, shapeFlag) {
	return setupBlock(createBaseVNode(type, props, children, patchFlag, dynamicProps, shapeFlag, true));
}
function createBlock(type, props, children, patchFlag, dynamicProps) {
	return setupBlock(createVNode(type, props, children, patchFlag, dynamicProps, true));
}
function isVNode(value) {
	return value ? value.__v_isVNode === true : false;
}
function isSameVNodeType(n1, n2) {
	return n1.type === n2.type && n1.key === n2.key;
}
var normalizeKey = ({ key }) => key != null ? key : null;
var normalizeRef = ({ ref, ref_key, ref_for }) => {
	if (typeof ref === "number") ref = "" + ref;
	return ref != null ? isString(ref) || /* @__PURE__ */ isRef(ref) || isFunction(ref) ? {
		i: currentRenderingInstance,
		r: ref,
		k: ref_key,
		f: !!ref_for
	} : ref : null;
};
function createBaseVNode(type, props = null, children = null, patchFlag = 0, dynamicProps = null, shapeFlag = type === Fragment ? 0 : 1, isBlockNode = false, needFullChildrenNormalization = false) {
	const vnode = {
		__v_isVNode: true,
		__v_skip: true,
		type,
		props,
		key: props && normalizeKey(props),
		ref: props && normalizeRef(props),
		scopeId: currentScopeId,
		slotScopeIds: null,
		children,
		component: null,
		suspense: null,
		ssContent: null,
		ssFallback: null,
		dirs: null,
		transition: null,
		el: null,
		anchor: null,
		target: null,
		targetStart: null,
		targetAnchor: null,
		staticCount: 0,
		shapeFlag,
		patchFlag,
		dynamicProps,
		dynamicChildren: null,
		appContext: null,
		ctx: currentRenderingInstance
	};
	if (needFullChildrenNormalization) {
		normalizeChildren(vnode, children);
		if (shapeFlag & 128) type.normalize(vnode);
	} else if (children) vnode.shapeFlag |= isString(children) ? 8 : 16;
	if (isBlockTreeEnabled > 0 && !isBlockNode && currentBlock && (vnode.patchFlag > 0 || shapeFlag & 6) && vnode.patchFlag !== 32) currentBlock.push(vnode);
	return vnode;
}
var createVNode = _createVNode;
function _createVNode(type, props = null, children = null, patchFlag = 0, dynamicProps = null, isBlockNode = false) {
	if (!type || type === NULL_DYNAMIC_COMPONENT) type = Comment;
	if (isVNode(type)) {
		const cloned = cloneVNode(type, props, true);
		if (children) normalizeChildren(cloned, children);
		if (isBlockTreeEnabled > 0 && !isBlockNode && currentBlock) {
			if (cloned.shapeFlag & 6) currentBlock[currentBlock.indexOf(type)] = cloned;
			else currentBlock.push(cloned);
		}
		cloned.patchFlag = -2;
		return cloned;
	}
	if (isClassComponent(type)) type = type.__vccOpts;
	if (props) {
		props = guardReactiveProps(props);
		let { class: klass, style } = props;
		if (klass && !isString(klass)) props.class = normalizeClass(klass);
		if (isObject$2(style)) {
			if (/* @__PURE__ */ isProxy(style) && !isArray(style)) style = extend$1({}, style);
			props.style = normalizeStyle(style);
		}
	}
	const shapeFlag = isString(type) ? 1 : isSuspense(type) ? 128 : isTeleport(type) ? 64 : isObject$2(type) ? 4 : isFunction(type) ? 2 : 0;
	return createBaseVNode(type, props, children, patchFlag, dynamicProps, shapeFlag, isBlockNode, true);
}
function guardReactiveProps(props) {
	if (!props) return null;
	if (/* @__PURE__ */ isProxy(props)) return extend$1({}, props);
	return isInternalObject(props) ? extend$1({}, props[rawAttrsKey] || props) : props;
}
function cloneVNode(vnode, extraProps, mergeRef = false, cloneTransition = false) {
	const { props, ref, patchFlag, children, transition } = vnode;
	const mergedProps = extraProps ? mergeProps(props || {}, extraProps) : props;
	const cloned = {
		__v_isVNode: true,
		__v_skip: true,
		type: vnode.type,
		props: mergedProps,
		key: mergedProps && normalizeKey(mergedProps),
		ref: extraProps && extraProps.ref ? mergeRef && ref ? isArray(ref) ? ref.concat(normalizeRef(extraProps)) : [ref, normalizeRef(extraProps)] : normalizeRef(extraProps) : ref,
		scopeId: vnode.scopeId,
		slotScopeIds: vnode.slotScopeIds,
		children,
		target: vnode.target,
		targetStart: vnode.targetStart,
		targetAnchor: vnode.targetAnchor,
		staticCount: vnode.staticCount,
		shapeFlag: vnode.shapeFlag,
		patchFlag: extraProps && vnode.type !== Fragment ? patchFlag === -1 ? 16 : patchFlag | 16 : patchFlag,
		dynamicProps: vnode.dynamicProps,
		dynamicChildren: vnode.dynamicChildren,
		appContext: vnode.appContext,
		dirs: vnode.dirs,
		transition,
		component: vnode.component,
		suspense: vnode.suspense,
		ssContent: vnode.ssContent && cloneVNode(vnode.ssContent),
		ssFallback: vnode.ssFallback && cloneVNode(vnode.ssFallback),
		placeholder: vnode.placeholder,
		el: vnode.el,
		anchor: vnode.anchor,
		ctx: vnode.ctx,
		ce: vnode.ce,
		cacheIndex: vnode.cacheIndex
	};
	if (transition && cloneTransition) setTransitionHooks(cloned, transition.clone(cloned));
	return cloned;
}
function createTextVNode(text = " ", flag = 0) {
	return createVNode(Text, null, text, flag);
}
function createCommentVNode(text = "", asBlock = false) {
	return asBlock ? (openBlock(), createBlock(Comment, null, text)) : createVNode(Comment, null, text);
}
function cloneIfMounted(child) {
	return child.el === null && child.patchFlag !== -1 || child.memo ? child : cloneVNode(child);
}
function normalizeChildren(vnode, children) {
	let type = 0;
	const { shapeFlag } = vnode;
	if (children == null) children = null;
	else if (isArray(children)) type = 16;
	else if (typeof children === "object") {
		if (shapeFlag & 65) {
			const slot = children.default;
			if (slot) {
				slot._c && (slot._d = false);
				normalizeChildren(vnode, slot());
				slot._c && (slot._d = true);
			}
			return;
		} else {
			type = 32;
			const slotFlag = children._;
			if (!slotFlag && !isInternalObject(children)) children._ctx = currentRenderingInstance;
			else if (slotFlag === 3 && currentRenderingInstance) {
				if (currentRenderingInstance.slots._ === 1) children._ = 1;
				else {
					children._ = 2;
					vnode.patchFlag |= 1024;
				}
			}
		}
	} else if (isFunction(children)) {
		if (shapeFlag & 65) {
			normalizeChildren(vnode, { default: children });
			return;
		}
		children = {
			default: children,
			_ctx: currentRenderingInstance
		};
		type = 32;
	} else {
		children = String(children);
		if (shapeFlag & 64) {
			type = 16;
			children = [createTextVNode(children)];
		} else type = 8;
	}
	vnode.children = children;
	vnode.shapeFlag |= type;
}
function mergeProps(...args) {
	const ret = {};
	for (let i = 0; i < args.length; i++) {
		const toMerge = args[i];
		for (const key in toMerge) if (key === "class") {
			if (ret.class !== toMerge.class) ret.class = normalizeClass([ret.class, toMerge.class]);
		} else if (key === "style") ret.style = normalizeStyle([ret.style, toMerge.style]);
		else if (isOn(key)) {
			const existing = ret[key];
			const incoming = toMerge[key];
			if (incoming && existing !== incoming && !(isArray(existing) && existing.includes(incoming))) ret[key] = existing ? [].concat(existing, incoming) : incoming;
			else if (incoming == null && existing == null && !isModelListener(key)) ret[key] = incoming;
		} else if (key !== "") ret[key] = toMerge[key];
	}
	return ret;
}
var currentInstance = null;
var getCurrentInstance = () => currentInstance || currentRenderingInstance;
var internalSetCurrentInstance;
{
	const g = getGlobalThis();
	const registerGlobalSetter = (key, setter) => {
		let setters;
		if (!(setters = g[key])) setters = g[key] = [];
		setters.push(setter);
		return (v) => {
			if (setters.length > 1) setters.forEach((set) => set(v));
			else setters[0](v);
		};
	};
	internalSetCurrentInstance = registerGlobalSetter(`__VUE_INSTANCE_SETTERS__`, (v) => currentInstance = v);
	registerGlobalSetter(`__VUE_SSR_SETTERS__`, (v) => isInSSRComponentSetup = v);
}
var setCurrentInstance = (instance) => {
	const prev = currentInstance;
	internalSetCurrentInstance(instance);
	instance.scope.on();
	return () => {
		instance.scope.off();
		internalSetCurrentInstance(prev);
	};
};
function isStatefulComponent(instance) {
	return instance.vnode.shapeFlag & 4;
}
var isInSSRComponentSetup = false;
var rawAttrsKey = /* @__PURE__ */ Symbol();
var attrsProxyHandlers = { get(target, key) {
	track(target, "get", "");
	return key === rawAttrsKey ? target : target[key];
} };
function createSetupContext(instance) {
	const expose = (exposed) => {
		instance.exposed = exposed || {};
	};
	return {
		attrs: new Proxy(instance.attrs, attrsProxyHandlers),
		slots: instance.slots,
		emit: instance.emit,
		expose
	};
}
function getComponentPublicInstance(instance) {
	if (instance.exposed) return instance.exposeProxy || (instance.exposeProxy = new Proxy(proxyRefs(markRaw(instance.exposed)), {
		get(target, key) {
			if (key in target) return target[key];
			else if (key in publicPropertiesMap) return publicPropertiesMap[key](instance);
		},
		has(target, key) {
			return key in target || key in publicPropertiesMap;
		}
	}));
	else return instance.proxy;
}
function isClassComponent(value) {
	return isFunction(value) && "__vccOpts" in value;
}
var computed$2 = (getterOrOptions, debugOptions) => {
	return /* @__PURE__ */ computed$1(getterOrOptions, debugOptions, isInSSRComponentSetup);
};
function h(type, propsOrChildren, children) {
	try {
		setBlockTracking(-1);
		const l = arguments.length;
		if (l === 2) {
			if (isObject$2(propsOrChildren) && !isArray(propsOrChildren)) {
				if (isVNode(propsOrChildren)) return createVNode(type, null, [propsOrChildren]);
				return createVNode(type, propsOrChildren);
			} else return createVNode(type, null, propsOrChildren);
		} else {
			if (l > 3) children = Array.prototype.slice.call(arguments, 2);
			else if (l === 3 && isVNode(children)) children = [children];
			return createVNode(type, propsOrChildren, children);
		}
	} finally {
		setBlockTracking(1);
	}
}
/**
* @vue/runtime-dom v3.5.45
* (c) 2018-present Yuxi (Evan) You and Vue contributors
* @license MIT
**/
if (typeof window !== "undefined" && window.trustedTypes);
var TRANSITION = "transition";
var ANIMATION = "animation";
var vtcKey = /* @__PURE__ */ Symbol("_vtc");
var DOMTransitionPropsValidators = {
	name: String,
	type: String,
	css: {
		type: Boolean,
		default: true
	},
	duration: [
		String,
		Number,
		Object
	],
	enterFromClass: String,
	enterActiveClass: String,
	enterToClass: String,
	appearFromClass: String,
	appearActiveClass: String,
	appearToClass: String,
	leaveFromClass: String,
	leaveActiveClass: String,
	leaveToClass: String
};
var TransitionPropsValidators = /* @__PURE__ */ extend$1({}, BaseTransitionPropsValidators, DOMTransitionPropsValidators);
var decorate$1 = (t) => {
	t.displayName = "Transition";
	t.props = TransitionPropsValidators;
	return t;
};
var Transition = /* @__PURE__ */ decorate$1((props, { slots }) => h(BaseTransition, resolveTransitionProps(props), slots));
var callHook = (hook, args = []) => {
	if (isArray(hook)) hook.forEach((h2) => h2(...args));
	else if (hook) hook(...args);
};
var hasExplicitCallback = (hook) => {
	return hook ? isArray(hook) ? hook.some((h2) => h2.length > 1) : hook.length > 1 : false;
};
function resolveTransitionProps(rawProps) {
	const baseProps = {};
	for (const key in rawProps) if (!(key in DOMTransitionPropsValidators)) baseProps[key] = rawProps[key];
	if (rawProps.css === false) return baseProps;
	const { name = "v", type, duration, enterFromClass = `${name}-enter-from`, enterActiveClass = `${name}-enter-active`, enterToClass = `${name}-enter-to`, appearFromClass = enterFromClass, appearActiveClass = enterActiveClass, appearToClass = enterToClass, leaveFromClass = `${name}-leave-from`, leaveActiveClass = `${name}-leave-active`, leaveToClass = `${name}-leave-to` } = rawProps;
	const durations = normalizeDuration(duration);
	const enterDuration = durations && durations[0];
	const leaveDuration = durations && durations[1];
	const { onBeforeEnter, onEnter, onEnterCancelled, onLeave, onLeaveCancelled, onBeforeAppear = onBeforeEnter, onAppear = onEnter, onAppearCancelled = onEnterCancelled } = baseProps;
	const finishEnter = (el, isAppear, done, isCancelled) => {
		el._enterCancelled = isCancelled;
		removeTransitionClass(el, isAppear ? appearToClass : enterToClass);
		removeTransitionClass(el, isAppear ? appearActiveClass : enterActiveClass);
		done && done();
	};
	const finishLeave = (el, done) => {
		el._isLeaving = false;
		removeTransitionClass(el, leaveFromClass);
		removeTransitionClass(el, leaveToClass);
		removeTransitionClass(el, leaveActiveClass);
		done && done();
	};
	const makeEnterHook = (isAppear) => {
		return (el, done) => {
			const hook = isAppear ? onAppear : onEnter;
			const resolve = () => finishEnter(el, isAppear, done);
			callHook(hook, [el, resolve]);
			nextFrame(() => {
				removeTransitionClass(el, isAppear ? appearFromClass : enterFromClass);
				addTransitionClass(el, isAppear ? appearToClass : enterToClass);
				if (!hasExplicitCallback(hook)) whenTransitionEnds(el, type, enterDuration, resolve);
			});
		};
	};
	return extend$1(baseProps, {
		onBeforeEnter(el) {
			callHook(onBeforeEnter, [el]);
			addTransitionClass(el, enterFromClass);
			addTransitionClass(el, enterActiveClass);
		},
		onBeforeAppear(el) {
			callHook(onBeforeAppear, [el]);
			addTransitionClass(el, appearFromClass);
			addTransitionClass(el, appearActiveClass);
		},
		onEnter: makeEnterHook(false),
		onAppear: makeEnterHook(true),
		onLeave(el, done) {
			el._isLeaving = true;
			const resolve = () => finishLeave(el, done);
			addTransitionClass(el, leaveFromClass);
			if (!el._enterCancelled) {
				forceReflow(el);
				addTransitionClass(el, leaveActiveClass);
			} else {
				addTransitionClass(el, leaveActiveClass);
				forceReflow(el);
			}
			nextFrame(() => {
				if (!el._isLeaving) return;
				removeTransitionClass(el, leaveFromClass);
				addTransitionClass(el, leaveToClass);
				if (!hasExplicitCallback(onLeave)) whenTransitionEnds(el, type, leaveDuration, resolve);
			});
			callHook(onLeave, [el, resolve]);
		},
		onEnterCancelled(el) {
			finishEnter(el, false, void 0, true);
			callHook(onEnterCancelled, [el]);
		},
		onAppearCancelled(el) {
			finishEnter(el, true, void 0, true);
			callHook(onAppearCancelled, [el]);
		},
		onLeaveCancelled(el) {
			finishLeave(el);
			callHook(onLeaveCancelled, [el]);
		}
	});
}
function normalizeDuration(duration) {
	if (duration == null) return null;
	else if (isObject$2(duration)) return [NumberOf(duration.enter), NumberOf(duration.leave)];
	else {
		const n = NumberOf(duration);
		return [n, n];
	}
}
function NumberOf(val) {
	return toNumber(val);
}
function addTransitionClass(el, cls) {
	cls.split(/\s+/).forEach((c) => c && el.classList.add(c));
	(el[vtcKey] || (el[vtcKey] = /* @__PURE__ */ new Set())).add(cls);
}
function removeTransitionClass(el, cls) {
	cls.split(/\s+/).forEach((c) => c && el.classList.remove(c));
	const _vtc = el[vtcKey];
	if (_vtc) {
		_vtc.delete(cls);
		if (!_vtc.size) el[vtcKey] = void 0;
	}
}
function nextFrame(cb) {
	requestAnimationFrame(() => {
		requestAnimationFrame(cb);
	});
}
var endId = 0;
function whenTransitionEnds(el, expectedType, explicitTimeout, resolve) {
	const id = el._endId = ++endId;
	const resolveIfNotStale = () => {
		if (id === el._endId) resolve();
	};
	if (explicitTimeout != null) return setTimeout(resolveIfNotStale, explicitTimeout);
	const { type, timeout, propCount } = getTransitionInfo(el, expectedType);
	if (!type) return resolve();
	const endEvent = type + "end";
	let ended = 0;
	const end = () => {
		el.removeEventListener(endEvent, onEnd);
		resolveIfNotStale();
	};
	const onEnd = (e) => {
		if (e.target === el && ++ended >= propCount) end();
	};
	setTimeout(() => {
		if (ended < propCount) end();
	}, timeout + 1);
	el.addEventListener(endEvent, onEnd);
}
function getTransitionInfo(el, expectedType) {
	const styles = window.getComputedStyle(el);
	const getStyleProperties = (key) => (styles[key] || "").split(", ");
	const transitionDelays = getStyleProperties(`${TRANSITION}Delay`);
	const transitionDurations = getStyleProperties(`${TRANSITION}Duration`);
	const transitionTimeout = getTimeout(transitionDelays, transitionDurations);
	const animationDelays = getStyleProperties(`${ANIMATION}Delay`);
	const animationDurations = getStyleProperties(`${ANIMATION}Duration`);
	const animationTimeout = getTimeout(animationDelays, animationDurations);
	let type = null;
	let timeout = 0;
	let propCount = 0;
	if (expectedType === TRANSITION) {
		if (transitionTimeout > 0) {
			type = TRANSITION;
			timeout = transitionTimeout;
			propCount = transitionDurations.length;
		}
	} else if (expectedType === ANIMATION) {
		if (animationTimeout > 0) {
			type = ANIMATION;
			timeout = animationTimeout;
			propCount = animationDurations.length;
		}
	} else {
		timeout = Math.max(transitionTimeout, animationTimeout);
		type = timeout > 0 ? transitionTimeout > animationTimeout ? TRANSITION : ANIMATION : null;
		propCount = type ? type === TRANSITION ? transitionDurations.length : animationDurations.length : 0;
	}
	const hasTransform = type === TRANSITION && /\b(?:transform|all)(?:,|$)/.test(getStyleProperties(`${TRANSITION}Property`).toString());
	return {
		type,
		timeout,
		propCount,
		hasTransform
	};
}
function getTimeout(delays, durations) {
	while (delays.length < durations.length) delays = delays.concat(delays);
	return Math.max(...durations.map((d, i) => toMs(d) + toMs(delays[i])));
}
function toMs(s) {
	if (s === "auto") return 0;
	return Number(s.slice(0, -1).replace(",", ".")) * 1e3;
}
function forceReflow(el) {
	return (el ? el.ownerDocument : document).body.offsetHeight;
}
var vShowOriginalDisplay = /* @__PURE__ */ Symbol("_vod");
var vShowHidden = /* @__PURE__ */ Symbol("_vsh");
var vShow = {
	name: "show",
	beforeMount(el, { value }, { transition }) {
		el[vShowOriginalDisplay] = el.style.display === "none" ? "" : el.style.display;
		if (transition && value) transition.beforeEnter(el);
		else setDisplay(el, value);
	},
	mounted(el, { value }, { transition }) {
		if (transition && value) transition.enter(el);
	},
	updated(el, { value, oldValue }, { transition }) {
		if (!value === !oldValue) return;
		if (transition) {
			if (value) {
				transition.beforeEnter(el);
				setDisplay(el, true);
				transition.enter(el);
			} else transition.leave(el, () => {
				setDisplay(el, false);
			});
		} else setDisplay(el, value);
	},
	beforeUnmount(el, { value }) {
		setDisplay(el, value);
	}
};
function setDisplay(el, value) {
	el.style.display = value ? el[vShowOriginalDisplay] : "none";
	el[vShowHidden] = !value;
}
var systemModifiers = [
	"ctrl",
	"shift",
	"alt",
	"meta"
];
var modifierGuards = {
	stop: (e) => e.stopPropagation(),
	prevent: (e) => e.preventDefault(),
	self: (e) => e.target !== e.currentTarget,
	ctrl: (e) => !e.ctrlKey,
	shift: (e) => !e.shiftKey,
	alt: (e) => !e.altKey,
	meta: (e) => !e.metaKey,
	left: (e) => "button" in e && e.button !== 0,
	middle: (e) => "button" in e && e.button !== 1,
	right: (e) => "button" in e && e.button !== 2,
	exact: (e, modifiers) => systemModifiers.some((m) => e[`${m}Key`] && !modifiers.includes(m))
};
var withModifiers = (fn, modifiers) => {
	if (!fn) return fn;
	const cache = fn._withMods || (fn._withMods = {});
	const cacheKey = modifiers.join(".");
	return cache[cacheKey] || (cache[cacheKey] = ((event, ...args) => {
		for (let i = 0; i < modifiers.length; i++) {
			const guard = modifierGuards[modifiers[i]];
			if (guard && guard(event, modifiers)) return;
		}
		return fn(event, ...args);
	}));
};
var keyNames = {
	esc: "escape",
	space: " ",
	up: "arrow-up",
	left: "arrow-left",
	right: "arrow-right",
	down: "arrow-down",
	delete: "backspace"
};
var withKeys = (fn, modifiers) => {
	const cache = fn._withKeys || (fn._withKeys = {});
	const cacheKey = modifiers.join(".");
	return cache[cacheKey] || (cache[cacheKey] = ((event) => {
		if (!("key" in event)) return;
		const eventKey = hyphenate$1(event.key);
		if (modifiers.some((k) => k === eventKey || keyNames[k] === eventKey)) return fn(event);
	}));
};
var isClient = typeof window !== "undefined" && typeof document !== "undefined";
typeof WorkerGlobalScope !== "undefined" && globalThis instanceof WorkerGlobalScope;
var notNullish = (val) => val != null;
var toString$1 = Object.prototype.toString;
var isObject$1 = (val) => toString$1.call(val) === "[object Object]";
function toArray(value) {
	return Array.isArray(value) ? value : [value];
}
function getLifeCycleTarget(target) {
	return target || getCurrentInstance();
}
/**
* Call onUnmounted() if it's inside a component lifecycle, if not, do nothing
*
* @param fn
* @param target
*/
function tryOnUnmounted(fn, target) {
	if (getLifeCycleTarget(target)) onUnmounted$1(fn, target);
}
/**
* Shorthand for watching value with {immediate: true}
*
* @see https://vueuse.org/watchImmediate
*/
function watchImmediate$1(source, cb, options) {
	return watch$2(source, cb, {
		...options,
		immediate: true
	});
}
// node_modules/.pnpm/@vueuse+core@14.4.0_vue@3.5.45_typescript@6.0.3_/node_modules/@vueuse/core/dist/index.js
var defaultWindow = isClient ? window : void 0;
isClient && window.document;
isClient && window.navigator;
isClient && window.location;
/**
* Get the dom element of a ref of element or Vue component instance
*
* @param elRef
*/
function unrefElement(elRef) {
	var _$el;
	const plain = toValue(elRef);
	return (_$el = plain === null || plain === void 0 ? void 0 : plain.$el) !== null && _$el !== void 0 ? _$el : plain;
}
function useEventListener$1(...args) {
	const register = (el, event, listener, options) => {
		el.addEventListener(event, listener, options);
		return () => el.removeEventListener(event, listener, options);
	};
	const firstParamTargets = computed$2(() => {
		const test = toArray(toValue(args[0])).filter((e) => e != null);
		return test.every((e) => typeof e !== "string") ? test : void 0;
	});
	return watchImmediate$1(() => {
		var _firstParamTargets$va, _firstParamTargets$va2;
		return [
			(_firstParamTargets$va = (_firstParamTargets$va2 = firstParamTargets.value) === null || _firstParamTargets$va2 === void 0 ? void 0 : _firstParamTargets$va2.map((e) => unrefElement(e))) !== null && _firstParamTargets$va !== void 0 ? _firstParamTargets$va : [defaultWindow].filter((e) => e != null),
			toArray(toValue(firstParamTargets.value ? args[1] : args[0])),
			toArray(unref$1(firstParamTargets.value ? args[2] : args[1])),
			toValue(firstParamTargets.value ? args[3] : args[2])
		];
	}, ([raw_targets, raw_events, raw_listeners, raw_options], _, onCleanup) => {
		if (!(raw_targets === null || raw_targets === void 0 ? void 0 : raw_targets.length) || !(raw_events === null || raw_events === void 0 ? void 0 : raw_events.length) || !(raw_listeners === null || raw_listeners === void 0 ? void 0 : raw_listeners.length)) return;
		const optionsClone = isObject$1(raw_options) ? { ...raw_options } : raw_options;
		const cleanups = raw_targets.flatMap((el) => raw_events.flatMap((event) => raw_listeners.map((listener) => register(el, event, listener, optionsClone))));
		onCleanup(() => {
			cleanups.forEach((fn) => fn());
		});
	}, { flush: "post" });
}
// node_modules/.pnpm/tabbable@6.5.1/node_modules/tabbable/dist/index.esm.js
/*!
* tabbable 6.5.1
* @license MIT, https://github.com/focus-trap/tabbable/blob/master/LICENSE
*/
var candidateSelectors = [
	"input:not([inert]):not([inert] *)",
	"select:not([inert]):not([inert] *)",
	"textarea:not([inert]):not([inert] *)",
	"a[href]:not([inert]):not([inert] *)",
	"area[href]:not([inert]):not([inert] *)",
	"button:not([inert]):not([inert] *)",
	"[tabindex]:not(slot):not([inert]):not([inert] *)",
	"audio[controls]:not([inert]):not([inert] *)",
	"video[controls]:not([inert]):not([inert] *)",
	"[contenteditable]:not([contenteditable=\"false\"]):not([inert]):not([inert] *)",
	"details>summary:first-of-type:not([inert]):not([inert] *)",
	"details:not([inert]):not([inert] *)"
];
var candidateSelector = /* #__PURE__ */ candidateSelectors.join(",");
var NoElement = typeof Element === "undefined";
var matches = NoElement ? function() {} : Element.prototype.matches || Element.prototype.msMatchesSelector || Element.prototype.webkitMatchesSelector;
var getRootNode = !NoElement && Element.prototype.getRootNode ? function(element) {
	return element == null || element.getRootNode == null ? void 0 : element.getRootNode();
} : function(element) {
	return element == null ? void 0 : element.ownerDocument;
};
/**
* Determines if a node is inert or in an inert ancestor.
* @param {Node} [node]
* @param {boolean} [lookUp] If true and `node` is not inert, looks up at ancestors to
*  see if any of them are inert. If false, only `node` itself is considered.
* @returns {boolean} True if inert itself or by way of being in an inert ancestor.
*  False if `node` is falsy.
*/
var _isInert = function isInert(node, lookUp) {
	if (lookUp === void 0) lookUp = true;
	var inertAtt = node == null || node.getAttribute == null ? void 0 : node.getAttribute("inert");
	return inertAtt === "" || inertAtt === "true" || lookUp && node && (typeof node.closest === "function" ? node.closest("[inert]") : _isInert(node.parentNode));
};
/**
* Determines if a node's content is editable.
* @param {Element} [node]
* @returns True if it's content-editable; false if it's not or `node` is falsy.
*/
var isContentEditable = function isContentEditable(node) {
	var _node$getAttribute;
	var attValue = node == null || node.getAttribute == null || (_node$getAttribute = node.getAttribute("contenteditable")) == null ? void 0 : _node$getAttribute.toLowerCase();
	return attValue === "" || attValue === "true" || attValue === "plaintext-only";
};
/**
* @param {Element} el container to check in
* @param {boolean} includeContainer add container to check
* @param {(node: Element) => boolean} filter filter candidates
* @returns {Element[]}
*/
var getCandidates = function getCandidates(el, includeContainer, filter) {
	if (_isInert(el)) return [];
	var candidates = Array.prototype.slice.apply(el.querySelectorAll(candidateSelector));
	if (includeContainer && matches.call(el, candidateSelector)) candidates.unshift(el);
	candidates = candidates.filter(filter);
	return candidates;
};
/**
* @callback GetShadowRoot
* @param {Element} element to check for shadow root
* @returns {ShadowRoot|boolean} ShadowRoot if available or boolean indicating if a shadowRoot is attached but not available.
*/
/**
* @callback ShadowRootFilter
* @param {Element} shadowHostNode the element which contains shadow content
* @returns {boolean} true if a shadow root could potentially contain valid candidates.
*/
/**
* @typedef {Object} CandidateScope
* @property {Element} scopeParent contains inner candidates
* @property {Element[]} candidates list of candidates found in the scope parent
*/
/**
* @typedef {Object} IterativeOptions
* @property {GetShadowRoot|boolean} getShadowRoot true if shadow support is enabled; falsy if not;
*  if a function, implies shadow support is enabled and either returns the shadow root of an element
*  or a boolean stating if it has an undisclosed shadow root
* @property {(node: Element) => boolean} filter filter candidates
* @property {boolean} flatten if true then result will flatten any CandidateScope into the returned list
* @property {ShadowRootFilter} shadowRootFilter filter shadow roots;
*/
/**
* @param {Element[]} elements list of element containers to match candidates from
* @param {boolean} includeContainer add container list to check
* @param {IterativeOptions} options
* @returns {Array.<Element|CandidateScope>}
*/
var _getCandidatesIteratively = function getCandidatesIteratively(elements, includeContainer, options) {
	var candidates = [];
	var elementsToCheck = Array.from(elements);
	while (elementsToCheck.length) {
		var element = elementsToCheck.shift();
		if (_isInert(element, false)) continue;
		if (element.tagName === "SLOT") {
			var assigned = element.assignedElements();
			var nestedCandidates = _getCandidatesIteratively(assigned.length ? assigned : element.children, true, options);
			if (options.flatten) candidates.push.apply(candidates, nestedCandidates);
			else candidates.push({
				scopeParent: element,
				candidates: nestedCandidates
			});
		} else {
			if (matches.call(element, candidateSelector) && options.filter(element) && (includeContainer || !elements.includes(element))) candidates.push(element);
			var shadowRoot = element.shadowRoot || typeof options.getShadowRoot === "function" && options.getShadowRoot(element);
			var validShadowRoot = !_isInert(shadowRoot, false) && (!options.shadowRootFilter || options.shadowRootFilter(element));
			if (shadowRoot && validShadowRoot) {
				var _nestedCandidates = _getCandidatesIteratively(shadowRoot === true ? element.children : shadowRoot.children, true, options);
				if (options.flatten) candidates.push.apply(candidates, _nestedCandidates);
				else candidates.push({
					scopeParent: element,
					candidates: _nestedCandidates
				});
			} else elementsToCheck.unshift.apply(elementsToCheck, element.children);
		}
	}
	return candidates;
};
/**
* @private
* Determines if the node has an explicitly specified `tabindex` attribute.
* @param {HTMLElement} node
* @returns {boolean} True if so; false if not.
*/
var hasTabIndex = function hasTabIndex(node) {
	return !isNaN(parseInt(node.getAttribute("tabindex"), 10));
};
/**
* Determine the tab index of a given node.
* @param {HTMLElement} node
* @returns {number} Tab order (negative, 0, or positive number).
* @throws {Error} If `node` is falsy.
*/
var getTabIndex = function getTabIndex(node) {
	if (!node) throw new Error("No node provided");
	if (node.tabIndex < 0) {
		if ((/^(AUDIO|VIDEO|DETAILS)$/.test(node.tagName) || isContentEditable(node)) && !hasTabIndex(node)) return 0;
	}
	return node.tabIndex;
};
/**
* Determine the tab index of a given node __for sort order purposes__.
* @param {HTMLElement} node
* @param {boolean} [isScope] True for a custom element with shadow root or slot that, by default,
*  has tabIndex -1, but needs to be sorted by document order in order for its content to be
*  inserted into the correct sort position.
* @returns {number} Tab order (negative, 0, or positive number).
*/
var getSortOrderTabIndex = function getSortOrderTabIndex(node, isScope) {
	var tabIndex = getTabIndex(node);
	if (tabIndex < 0 && isScope && !hasTabIndex(node)) return 0;
	return tabIndex;
};
var sortOrderedTabbables = function sortOrderedTabbables(a, b) {
	return a.tabIndex === b.tabIndex ? a.documentOrder - b.documentOrder : a.tabIndex - b.tabIndex;
};
var isInput = function isInput(node) {
	return node.tagName === "INPUT";
};
var isHiddenInput = function isHiddenInput(node) {
	return isInput(node) && node.type === "hidden";
};
var isDetailsWithSummary = function isDetailsWithSummary(node) {
	return node.tagName === "DETAILS" && Array.prototype.slice.apply(node.children).some(function(child) {
		return child.tagName === "SUMMARY";
	});
};
var getCheckedRadio = function getCheckedRadio(nodes, form) {
	for (var i = 0; i < nodes.length; i++) if (nodes[i].checked && nodes[i].form === form) return nodes[i];
};
var isTabbableRadio = function isTabbableRadio(node) {
	if (!node.name) return true;
	var radioScope = node.form || getRootNode(node);
	var queryRadios = function queryRadios(name) {
		return radioScope.querySelectorAll("input[type=\"radio\"][name=\"" + name + "\"]");
	};
	var radioSet;
	if (typeof window !== "undefined" && typeof window.CSS !== "undefined" && typeof window.CSS.escape === "function") radioSet = queryRadios(window.CSS.escape(node.name));
	else try {
		radioSet = queryRadios(node.name);
	} catch (err) {
		console.error("Looks like you have a radio button with a name attribute containing invalid CSS selector characters and need the CSS.escape polyfill: %s", err.message);
		return false;
	}
	var checked = getCheckedRadio(radioSet, node.form);
	return !checked || checked === node;
};
var isRadio = function isRadio(node) {
	return isInput(node) && node.type === "radio";
};
var isNonTabbableRadio = function isNonTabbableRadio(node) {
	return isRadio(node) && !isTabbableRadio(node);
};
var isNodeAttached = function isNodeAttached(node) {
	var _nodeRoot;
	var nodeRoot = node && getRootNode(node);
	var nodeRootHost = (_nodeRoot = nodeRoot) == null ? void 0 : _nodeRoot.host;
	var attached = false;
	if (nodeRoot && nodeRoot !== node) {
		var _nodeRootHost, _node$ownerDocument;
		attached = !!((_nodeRootHost = nodeRootHost) != null && (_nodeRootHost = _nodeRootHost.ownerDocument) != null && _nodeRootHost.contains(nodeRootHost) || node != null && (_node$ownerDocument = node.ownerDocument) != null && _node$ownerDocument.contains(node));
		while (!attached && nodeRootHost) {
			var _nodeRoot2, _nodeRootHost2;
			nodeRoot = getRootNode(nodeRootHost);
			nodeRootHost = (_nodeRoot2 = nodeRoot) == null ? void 0 : _nodeRoot2.host;
			attached = !!((_nodeRootHost2 = nodeRootHost) != null && (_nodeRootHost2 = _nodeRootHost2.ownerDocument) != null && _nodeRootHost2.contains(nodeRootHost));
		}
	}
	return attached;
};
var isZeroArea = function isZeroArea(node) {
	var _node$getBoundingClie = node.getBoundingClientRect(), width = _node$getBoundingClie.width, height = _node$getBoundingClie.height;
	return width === 0 && height === 0;
};
var isHidden = function isHidden(node, _ref) {
	var displayCheck = _ref.displayCheck, getShadowRoot = _ref.getShadowRoot;
	if (displayCheck === "full-native") {
		if ("checkVisibility" in node) return !node.checkVisibility({
			checkOpacity: false,
			opacityProperty: false,
			contentVisibilityAuto: true,
			visibilityProperty: true,
			checkVisibilityCSS: true
		});
	}
	var visibility = getComputedStyle(node).visibility;
	if (visibility === "hidden" || visibility === "collapse") return true;
	var nodeUnderDetails = matches.call(node, "details>summary:first-of-type") ? node.parentElement : node;
	if (matches.call(nodeUnderDetails, "details:not([open]) *")) return true;
	if (!displayCheck || displayCheck === "full" || displayCheck === "full-native" || displayCheck === "legacy-full") {
		if (typeof getShadowRoot === "function") {
			var originalNode = node;
			while (node) {
				var parentElement = node.parentElement;
				var rootNode = getRootNode(node);
				if (parentElement && !parentElement.shadowRoot && getShadowRoot(parentElement) === true) return isZeroArea(node);
				else if (node.assignedSlot) node = node.assignedSlot;
				else if (!parentElement && rootNode !== node.ownerDocument) node = rootNode.host;
				else node = parentElement;
			}
			node = originalNode;
		}
		if (isNodeAttached(node)) return !node.getClientRects().length;
		if (displayCheck !== "legacy-full") return true;
	} else if (displayCheck === "non-zero-area") return isZeroArea(node);
	return false;
};
var isDisabledFromFieldset = function isDisabledFromFieldset(node) {
	if (/^(INPUT|BUTTON|SELECT|TEXTAREA)$/.test(node.tagName)) {
		var parentNode = node.parentElement;
		while (parentNode) {
			if (parentNode.tagName === "FIELDSET" && parentNode.disabled) {
				for (var i = 0; i < parentNode.children.length; i++) {
					var child = parentNode.children.item(i);
					if (child.tagName === "LEGEND") return matches.call(parentNode, "fieldset[disabled] *") ? true : !child.contains(node);
				}
				return true;
			}
			parentNode = parentNode.parentElement;
		}
	}
	return false;
};
var isNodeMatchingSelectorFocusable = function isNodeMatchingSelectorFocusable(options, node) {
	if (node.disabled || isHiddenInput(node) || isHidden(node, options) || isDetailsWithSummary(node) || isDisabledFromFieldset(node)) return false;
	return true;
};
var isNodeMatchingSelectorTabbable = function isNodeMatchingSelectorTabbable(options, node) {
	if (isNonTabbableRadio(node) || getTabIndex(node) < 0 || !isNodeMatchingSelectorFocusable(options, node)) return false;
	return true;
};
var isShadowRootTabbable = function isShadowRootTabbable(shadowHostNode) {
	var tabIndex = parseInt(shadowHostNode.getAttribute("tabindex"), 10);
	if (isNaN(tabIndex) || tabIndex >= 0) return true;
	return false;
};
/**
* @param {Array.<Element|CandidateScope>} candidates
* @returns Element[]
*/
var _sortByOrder = function sortByOrder(candidates) {
	var regularTabbables = [];
	var orderedTabbables = [];
	candidates.forEach(function(item, i) {
		var isScope = !!item.scopeParent;
		var element = isScope ? item.scopeParent : item;
		var candidateTabindex = getSortOrderTabIndex(element, isScope);
		var elements = isScope ? _sortByOrder(item.candidates) : element;
		if (candidateTabindex === 0) isScope ? regularTabbables.push.apply(regularTabbables, elements) : regularTabbables.push(element);
		else orderedTabbables.push({
			documentOrder: i,
			tabIndex: candidateTabindex,
			item,
			isScope,
			content: elements
		});
	});
	return orderedTabbables.sort(sortOrderedTabbables).reduce(function(acc, sortable) {
		sortable.isScope ? acc.push.apply(acc, sortable.content) : acc.push(sortable.content);
		return acc;
	}, []).concat(regularTabbables);
};
var tabbable = function tabbable(container, options) {
	options = options || {};
	var candidates;
	if (options.getShadowRoot) candidates = _getCandidatesIteratively([container], options.includeContainer, {
		filter: isNodeMatchingSelectorTabbable.bind(null, options),
		flatten: false,
		getShadowRoot: options.getShadowRoot,
		shadowRootFilter: isShadowRootTabbable
	});
	else candidates = getCandidates(container, options.includeContainer, isNodeMatchingSelectorTabbable.bind(null, options));
	return _sortByOrder(candidates);
};
var focusable = function focusable(container, options) {
	options = options || {};
	var candidates;
	if (options.getShadowRoot) candidates = _getCandidatesIteratively([container], options.includeContainer, {
		filter: isNodeMatchingSelectorFocusable.bind(null, options),
		flatten: true,
		getShadowRoot: options.getShadowRoot
	});
	else candidates = getCandidates(container, options.includeContainer, isNodeMatchingSelectorFocusable.bind(null, options));
	return candidates;
};
var isTabbable = function isTabbable(node, options) {
	options = options || {};
	if (!node) throw new Error("No node provided");
	if (matches.call(node, candidateSelector) === false) return false;
	return isNodeMatchingSelectorTabbable(options, node);
};
var focusableCandidateSelector = /* #__PURE__ */ candidateSelectors.concat("iframe:not([inert]):not([inert] *)").join(",");
var isFocusable = function isFocusable(node, options) {
	options = options || {};
	if (!node) throw new Error("No node provided");
	if (matches.call(node, focusableCandidateSelector) === false) return false;
	return isNodeMatchingSelectorFocusable(options, node);
};
// node_modules/.pnpm/focus-trap@7.6.5/node_modules/focus-trap/dist/focus-trap.esm.js
/*!
* focus-trap 7.6.5
* @license MIT, https://github.com/focus-trap/focus-trap/blob/master/LICENSE
*/
function _arrayLikeToArray(r, a) {
	(null == a || a > r.length) && (a = r.length);
	for (var e = 0, n = Array(a); e < a; e++) n[e] = r[e];
	return n;
}
function _arrayWithoutHoles(r) {
	if (Array.isArray(r)) return _arrayLikeToArray(r);
}
function _defineProperty(e, r, t) {
	return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, {
		value: t,
		enumerable: true,
		configurable: true,
		writable: true
	}) : e[r] = t, e;
}
function _iterableToArray(r) {
	if ("undefined" != typeof Symbol && null != r[Symbol.iterator] || null != r["@@iterator"]) return Array.from(r);
}
function _nonIterableSpread() {
	throw new TypeError("Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.");
}
function ownKeys(e, r) {
	var t = Object.keys(e);
	if (Object.getOwnPropertySymbols) {
		var o = Object.getOwnPropertySymbols(e);
		r && (o = o.filter(function(r) {
			return Object.getOwnPropertyDescriptor(e, r).enumerable;
		})), t.push.apply(t, o);
	}
	return t;
}
function _objectSpread2(e) {
	for (var r = 1; r < arguments.length; r++) {
		var t = null != arguments[r] ? arguments[r] : {};
		r % 2 ? ownKeys(Object(t), true).forEach(function(r) {
			_defineProperty(e, r, t[r]);
		}) : Object.getOwnPropertyDescriptors ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(t)) : ownKeys(Object(t)).forEach(function(r) {
			Object.defineProperty(e, r, Object.getOwnPropertyDescriptor(t, r));
		});
	}
	return e;
}
function _toConsumableArray(r) {
	return _arrayWithoutHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableSpread();
}
function _toPrimitive(t, r) {
	if ("object" != typeof t || !t) return t;
	var e = t[Symbol.toPrimitive];
	if (void 0 !== e) {
		var i = e.call(t, r);
		if ("object" != typeof i) return i;
		throw new TypeError("@@toPrimitive must return a primitive value.");
	}
	return ("string" === r ? String : Number)(t);
}
function _toPropertyKey(t) {
	var i = _toPrimitive(t, "string");
	return "symbol" == typeof i ? i : i + "";
}
function _unsupportedIterableToArray(r, a) {
	if (r) {
		if ("string" == typeof r) return _arrayLikeToArray(r, a);
		var t = {}.toString.call(r).slice(8, -1);
		return "Object" === t && r.constructor && (t = r.constructor.name), "Map" === t || "Set" === t ? Array.from(r) : "Arguments" === t || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(t) ? _arrayLikeToArray(r, a) : void 0;
	}
}
var activeFocusTraps = {
	activateTrap: function activateTrap(trapStack, trap) {
		if (trapStack.length > 0) {
			var activeTrap = trapStack[trapStack.length - 1];
			if (activeTrap !== trap) activeTrap._setPausedState(true);
		}
		var trapIndex = trapStack.indexOf(trap);
		if (trapIndex === -1) trapStack.push(trap);
		else {
			trapStack.splice(trapIndex, 1);
			trapStack.push(trap);
		}
	},
	deactivateTrap: function deactivateTrap(trapStack, trap) {
		var trapIndex = trapStack.indexOf(trap);
		if (trapIndex !== -1) trapStack.splice(trapIndex, 1);
		if (trapStack.length > 0 && !trapStack[trapStack.length - 1]._isManuallyPaused()) trapStack[trapStack.length - 1]._setPausedState(false);
	}
};
var isSelectableInput = function isSelectableInput(node) {
	return node.tagName && node.tagName.toLowerCase() === "input" && typeof node.select === "function";
};
var isEscapeEvent = function isEscapeEvent(e) {
	return (e === null || e === void 0 ? void 0 : e.key) === "Escape" || (e === null || e === void 0 ? void 0 : e.key) === "Esc" || (e === null || e === void 0 ? void 0 : e.keyCode) === 27;
};
var isTabEvent = function isTabEvent(e) {
	return (e === null || e === void 0 ? void 0 : e.key) === "Tab" || (e === null || e === void 0 ? void 0 : e.keyCode) === 9;
};
var isKeyForward = function isKeyForward(e) {
	return isTabEvent(e) && !e.shiftKey;
};
var isKeyBackward = function isKeyBackward(e) {
	return isTabEvent(e) && e.shiftKey;
};
var delay$1 = function delay(fn) {
	return setTimeout(fn, 0);
};
/**
* Get an option's value when it could be a plain value, or a handler that provides
*  the value.
* @param {*} value Option's value to check.
* @param {...*} [params] Any parameters to pass to the handler, if `value` is a function.
* @returns {*} The `value`, or the handler's returned value.
*/
var valueOrHandler = function valueOrHandler(value) {
	for (var _len = arguments.length, params = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) params[_key - 1] = arguments[_key];
	return typeof value === "function" ? value.apply(void 0, params) : value;
};
var getActualTarget = function getActualTarget(event) {
	return event.target.shadowRoot && typeof event.composedPath === "function" ? event.composedPath()[0] : event.target;
};
var internalTrapStack = [];
var createFocusTrap = function createFocusTrap(elements, userOptions) {
	var doc = (userOptions === null || userOptions === void 0 ? void 0 : userOptions.document) || document;
	var trapStack = (userOptions === null || userOptions === void 0 ? void 0 : userOptions.trapStack) || internalTrapStack;
	var config = _objectSpread2({
		returnFocusOnDeactivate: true,
		escapeDeactivates: true,
		delayInitialFocus: true,
		isKeyForward,
		isKeyBackward
	}, userOptions);
	var state = {
		containers: [],
		containerGroups: [],
		tabbableGroups: [],
		nodeFocusedBeforeActivation: null,
		mostRecentlyFocusedNode: null,
		active: false,
		paused: false,
		manuallyPaused: false,
		delayInitialFocusTimer: void 0,
		recentNavEvent: void 0
	};
	var trap;
	/**
	* Gets a configuration option value.
	* @param {Object|undefined} configOverrideOptions If true, and option is defined in this set,
	*  value will be taken from this object. Otherwise, value will be taken from base configuration.
	* @param {string} optionName Name of the option whose value is sought.
	* @param {string|undefined} [configOptionName] Name of option to use __instead of__ `optionName`
	*  IIF `configOverrideOptions` is not defined. Otherwise, `optionName` is used.
	*/
	var getOption = function getOption(configOverrideOptions, optionName, configOptionName) {
		return configOverrideOptions && configOverrideOptions[optionName] !== void 0 ? configOverrideOptions[optionName] : config[configOptionName || optionName];
	};
	/**
	* Finds the index of the container that contains the element.
	* @param {HTMLElement} element
	* @param {Event} [event] If available, and `element` isn't directly found in any container,
	*  the event's composed path is used to see if includes any known trap containers in the
	*  case where the element is inside a Shadow DOM.
	* @returns {number} Index of the container in either `state.containers` or
	*  `state.containerGroups` (the order/length of these lists are the same); -1
	*  if the element isn't found.
	*/
	var findContainerIndex = function findContainerIndex(element, event) {
		var composedPath = typeof (event === null || event === void 0 ? void 0 : event.composedPath) === "function" ? event.composedPath() : void 0;
		return state.containerGroups.findIndex(function(_ref) {
			var container = _ref.container, tabbableNodes = _ref.tabbableNodes;
			return container.contains(element) || (composedPath === null || composedPath === void 0 ? void 0 : composedPath.includes(container)) || tabbableNodes.find(function(node) {
				return node === element;
			});
		});
	};
	/**
	* Gets the node for the given option, which is expected to be an option that
	*  can be either a DOM node, a string that is a selector to get a node, `false`
	*  (if a node is explicitly NOT given), or a function that returns any of these
	*  values.
	* @param {string} optionName
	* @param {Object} options
	* @param {boolean} [options.hasFallback] True if the option could be a selector string
	*  and the option allows for a fallback scenario in the case where the selector is
	*  valid but does not match a node (i.e. the queried node doesn't exist in the DOM).
	* @param {Array} [options.params] Params to pass to the option if it's a function.
	* @returns {undefined | null | false | HTMLElement | SVGElement} Returns
	*  `undefined` if the option is not specified; `null` if the option didn't resolve
	*  to a node but `options.hasFallback=true`, `false` if the option resolved to `false`
	*  (node explicitly not given); otherwise, the resolved DOM node.
	* @throws {Error} If the option is set, not `false`, and is not, or does not
	*  resolve to a node, unless the option is a selector string and `options.hasFallback=true`.
	*/
	var getNodeForOption = function getNodeForOption(optionName) {
		var _ref2 = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {}, _ref2$hasFallback = _ref2.hasFallback, hasFallback = _ref2$hasFallback === void 0 ? false : _ref2$hasFallback, _ref2$params = _ref2.params, params = _ref2$params === void 0 ? [] : _ref2$params;
		var optionValue = config[optionName];
		if (typeof optionValue === "function") optionValue = optionValue.apply(void 0, _toConsumableArray(params));
		if (optionValue === true) optionValue = void 0;
		if (!optionValue) {
			if (optionValue === void 0 || optionValue === false) return optionValue;
			throw new Error("`".concat(optionName, "` was specified but was not a node, or did not return a node"));
		}
		var node = optionValue;
		if (typeof optionValue === "string") {
			try {
				node = doc.querySelector(optionValue);
			} catch (err) {
				throw new Error("`".concat(optionName, "` appears to be an invalid selector; error=\"").concat(err.message, "\""));
			}
			if (!node) {
				if (!hasFallback) throw new Error("`".concat(optionName, "` as selector refers to no known node"));
			}
		}
		return node;
	};
	var getInitialFocusNode = function getInitialFocusNode() {
		var node = getNodeForOption("initialFocus", { hasFallback: true });
		if (node === false) return false;
		if (node === void 0 || node && !isFocusable(node, config.tabbableOptions)) {
			if (findContainerIndex(doc.activeElement) >= 0) node = doc.activeElement;
			else {
				var firstTabbableGroup = state.tabbableGroups[0];
				node = firstTabbableGroup && firstTabbableGroup.firstTabbableNode || getNodeForOption("fallbackFocus");
			}
		} else if (node === null) node = getNodeForOption("fallbackFocus");
		if (!node) throw new Error("Your focus-trap needs to have at least one focusable element");
		return node;
	};
	var updateTabbableNodes = function updateTabbableNodes() {
		state.containerGroups = state.containers.map(function(container) {
			var tabbableNodes = tabbable(container, config.tabbableOptions);
			var focusableNodes = focusable(container, config.tabbableOptions);
			var firstTabbableNode = tabbableNodes.length > 0 ? tabbableNodes[0] : void 0;
			var lastTabbableNode = tabbableNodes.length > 0 ? tabbableNodes[tabbableNodes.length - 1] : void 0;
			var firstDomTabbableNode = focusableNodes.find(function(node) {
				return isTabbable(node);
			});
			var lastDomTabbableNode = focusableNodes.slice().reverse().find(function(node) {
				return isTabbable(node);
			});
			return {
				container,
				tabbableNodes,
				focusableNodes,
				/** True if at least one node with positive `tabindex` was found in this container. */
				posTabIndexesFound: !!tabbableNodes.find(function(node) {
					return getTabIndex(node) > 0;
				}),
				/** First tabbable node in container, __tabindex__ order; `undefined` if none. */
				firstTabbableNode,
				/** Last tabbable node in container, __tabindex__ order; `undefined` if none. */
				lastTabbableNode,
				/** First tabbable node in container, __DOM__ order; `undefined` if none. */
				firstDomTabbableNode,
				/** Last tabbable node in container, __DOM__ order; `undefined` if none. */
				lastDomTabbableNode,
				/**
				* Finds the __tabbable__ node that follows the given node in the specified direction,
				*  in this container, if any.
				* @param {HTMLElement} node
				* @param {boolean} [forward] True if going in forward tab order; false if going
				*  in reverse.
				* @returns {HTMLElement|undefined} The next tabbable node, if any.
				*/
				nextTabbableNode: function nextTabbableNode(node) {
					var forward = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
					var nodeIdx = tabbableNodes.indexOf(node);
					if (nodeIdx < 0) {
						if (forward) return focusableNodes.slice(focusableNodes.indexOf(node) + 1).find(function(el) {
							return isTabbable(el);
						});
						return focusableNodes.slice(0, focusableNodes.indexOf(node)).reverse().find(function(el) {
							return isTabbable(el);
						});
					}
					return tabbableNodes[nodeIdx + (forward ? 1 : -1)];
				}
			};
		});
		state.tabbableGroups = state.containerGroups.filter(function(group) {
			return group.tabbableNodes.length > 0;
		});
		if (state.tabbableGroups.length <= 0 && !getNodeForOption("fallbackFocus")) throw new Error("Your focus-trap must have at least one container with at least one tabbable node in it at all times");
		if (state.containerGroups.find(function(g) {
			return g.posTabIndexesFound;
		}) && state.containerGroups.length > 1) throw new Error("At least one node with a positive tabindex was found in one of your focus-trap's multiple containers. Positive tabindexes are only supported in single-container focus-traps.");
	};
	/**
	* Gets the current activeElement. If it's a web-component and has open shadow-root
	* it will recursively search inside shadow roots for the "true" activeElement.
	*
	* @param {Document | ShadowRoot} el
	*
	* @returns {HTMLElement} The element that currently has the focus
	**/
	var _getActiveElement = function getActiveElement(el) {
		var activeElement = el.activeElement;
		if (!activeElement) return;
		if (activeElement.shadowRoot && activeElement.shadowRoot.activeElement !== null) return _getActiveElement(activeElement.shadowRoot);
		return activeElement;
	};
	var _tryFocus = function tryFocus(node) {
		if (node === false) return;
		if (node === _getActiveElement(document)) return;
		if (!node || !node.focus) {
			_tryFocus(getInitialFocusNode());
			return;
		}
		node.focus({ preventScroll: !!config.preventScroll });
		state.mostRecentlyFocusedNode = node;
		if (isSelectableInput(node)) node.select();
	};
	var getReturnFocusNode = function getReturnFocusNode(previousActiveElement) {
		var node = getNodeForOption("setReturnFocus", { params: [previousActiveElement] });
		return node ? node : node === false ? false : previousActiveElement;
	};
	/**
	* Finds the next node (in either direction) where focus should move according to a
	*  keyboard focus-in event.
	* @param {Object} params
	* @param {Node} [params.target] Known target __from which__ to navigate, if any.
	* @param {KeyboardEvent|FocusEvent} [params.event] Event to use if `target` isn't known (event
	*  will be used to determine the `target`). Ignored if `target` is specified.
	* @param {boolean} [params.isBackward] True if focus should move backward.
	* @returns {Node|undefined} The next node, or `undefined` if a next node couldn't be
	*  determined given the current state of the trap.
	*/
	var findNextNavNode = function findNextNavNode(_ref3) {
		var target = _ref3.target, event = _ref3.event, _ref3$isBackward = _ref3.isBackward, isBackward = _ref3$isBackward === void 0 ? false : _ref3$isBackward;
		target = target || getActualTarget(event);
		updateTabbableNodes();
		var destinationNode = null;
		if (state.tabbableGroups.length > 0) {
			var containerIndex = findContainerIndex(target, event);
			var containerGroup = containerIndex >= 0 ? state.containerGroups[containerIndex] : void 0;
			if (containerIndex < 0) {
				if (isBackward) destinationNode = state.tabbableGroups[state.tabbableGroups.length - 1].lastTabbableNode;
				else destinationNode = state.tabbableGroups[0].firstTabbableNode;
			} else if (isBackward) {
				var startOfGroupIndex = state.tabbableGroups.findIndex(function(_ref4) {
					var firstTabbableNode = _ref4.firstTabbableNode;
					return target === firstTabbableNode;
				});
				if (startOfGroupIndex < 0 && (containerGroup.container === target || isFocusable(target, config.tabbableOptions) && !isTabbable(target, config.tabbableOptions) && !containerGroup.nextTabbableNode(target, false))) startOfGroupIndex = containerIndex;
				if (startOfGroupIndex >= 0) {
					var destinationGroupIndex = startOfGroupIndex === 0 ? state.tabbableGroups.length - 1 : startOfGroupIndex - 1;
					var destinationGroup = state.tabbableGroups[destinationGroupIndex];
					destinationNode = getTabIndex(target) >= 0 ? destinationGroup.lastTabbableNode : destinationGroup.lastDomTabbableNode;
				} else if (!isTabEvent(event)) destinationNode = containerGroup.nextTabbableNode(target, false);
			} else {
				var lastOfGroupIndex = state.tabbableGroups.findIndex(function(_ref5) {
					var lastTabbableNode = _ref5.lastTabbableNode;
					return target === lastTabbableNode;
				});
				if (lastOfGroupIndex < 0 && (containerGroup.container === target || isFocusable(target, config.tabbableOptions) && !isTabbable(target, config.tabbableOptions) && !containerGroup.nextTabbableNode(target))) lastOfGroupIndex = containerIndex;
				if (lastOfGroupIndex >= 0) {
					var _destinationGroupIndex = lastOfGroupIndex === state.tabbableGroups.length - 1 ? 0 : lastOfGroupIndex + 1;
					var _destinationGroup = state.tabbableGroups[_destinationGroupIndex];
					destinationNode = getTabIndex(target) >= 0 ? _destinationGroup.firstTabbableNode : _destinationGroup.firstDomTabbableNode;
				} else if (!isTabEvent(event)) destinationNode = containerGroup.nextTabbableNode(target);
			}
		} else destinationNode = getNodeForOption("fallbackFocus");
		return destinationNode;
	};
	var checkPointerDown = function checkPointerDown(e) {
		if (findContainerIndex(getActualTarget(e), e) >= 0) return;
		if (valueOrHandler(config.clickOutsideDeactivates, e)) {
			trap.deactivate({ returnFocus: config.returnFocusOnDeactivate });
			return;
		}
		if (valueOrHandler(config.allowOutsideClick, e)) return;
		e.preventDefault();
	};
	var checkFocusIn = function checkFocusIn(event) {
		var target = getActualTarget(event);
		var targetContained = findContainerIndex(target, event) >= 0;
		if (targetContained || target instanceof Document) {
			if (targetContained) state.mostRecentlyFocusedNode = target;
		} else {
			event.stopImmediatePropagation();
			var nextNode;
			var navAcrossContainers = true;
			if (state.mostRecentlyFocusedNode) {
				if (getTabIndex(state.mostRecentlyFocusedNode) > 0) {
					var mruContainerIdx = findContainerIndex(state.mostRecentlyFocusedNode);
					var tabbableNodes = state.containerGroups[mruContainerIdx].tabbableNodes;
					if (tabbableNodes.length > 0) {
						var mruTabIdx = tabbableNodes.findIndex(function(node) {
							return node === state.mostRecentlyFocusedNode;
						});
						if (mruTabIdx >= 0) {
							if (config.isKeyForward(state.recentNavEvent)) {
								if (mruTabIdx + 1 < tabbableNodes.length) {
									nextNode = tabbableNodes[mruTabIdx + 1];
									navAcrossContainers = false;
								}
							} else if (mruTabIdx - 1 >= 0) {
								nextNode = tabbableNodes[mruTabIdx - 1];
								navAcrossContainers = false;
							}
						}
					}
				} else if (!state.containerGroups.some(function(g) {
					return g.tabbableNodes.some(function(n) {
						return getTabIndex(n) > 0;
					});
				})) navAcrossContainers = false;
			} else navAcrossContainers = false;
			if (navAcrossContainers) nextNode = findNextNavNode({
				target: state.mostRecentlyFocusedNode,
				isBackward: config.isKeyBackward(state.recentNavEvent)
			});
			if (nextNode) _tryFocus(nextNode);
			else _tryFocus(state.mostRecentlyFocusedNode || getInitialFocusNode());
		}
		state.recentNavEvent = void 0;
	};
	var checkKeyNav = function checkKeyNav(event) {
		var isBackward = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
		state.recentNavEvent = event;
		var destinationNode = findNextNavNode({
			event,
			isBackward
		});
		if (destinationNode) {
			if (isTabEvent(event)) event.preventDefault();
			_tryFocus(destinationNode);
		}
	};
	var checkTabKey = function checkTabKey(event) {
		if (config.isKeyForward(event) || config.isKeyBackward(event)) checkKeyNav(event, config.isKeyBackward(event));
	};
	var checkEscapeKey = function checkEscapeKey(event) {
		if (isEscapeEvent(event) && valueOrHandler(config.escapeDeactivates, event) !== false) {
			event.preventDefault();
			trap.deactivate();
		}
	};
	var checkClick = function checkClick(e) {
		if (findContainerIndex(getActualTarget(e), e) >= 0) return;
		if (valueOrHandler(config.clickOutsideDeactivates, e)) return;
		if (valueOrHandler(config.allowOutsideClick, e)) return;
		e.preventDefault();
		e.stopImmediatePropagation();
	};
	var addListeners = function addListeners() {
		if (!state.active) return;
		activeFocusTraps.activateTrap(trapStack, trap);
		state.delayInitialFocusTimer = config.delayInitialFocus ? delay$1(function() {
			_tryFocus(getInitialFocusNode());
		}) : _tryFocus(getInitialFocusNode());
		doc.addEventListener("focusin", checkFocusIn, true);
		doc.addEventListener("mousedown", checkPointerDown, {
			capture: true,
			passive: false
		});
		doc.addEventListener("touchstart", checkPointerDown, {
			capture: true,
			passive: false
		});
		doc.addEventListener("click", checkClick, {
			capture: true,
			passive: false
		});
		doc.addEventListener("keydown", checkTabKey, {
			capture: true,
			passive: false
		});
		doc.addEventListener("keydown", checkEscapeKey);
		return trap;
	};
	var removeListeners = function removeListeners() {
		if (!state.active) return;
		doc.removeEventListener("focusin", checkFocusIn, true);
		doc.removeEventListener("mousedown", checkPointerDown, true);
		doc.removeEventListener("touchstart", checkPointerDown, true);
		doc.removeEventListener("click", checkClick, true);
		doc.removeEventListener("keydown", checkTabKey, true);
		doc.removeEventListener("keydown", checkEscapeKey);
		return trap;
	};
	var mutationObserver = typeof window !== "undefined" && "MutationObserver" in window ? new MutationObserver(function checkDomRemoval(mutations) {
		if (mutations.some(function(mutation) {
			return Array.from(mutation.removedNodes).some(function(node) {
				return node === state.mostRecentlyFocusedNode;
			});
		})) _tryFocus(getInitialFocusNode());
	}) : void 0;
	var updateObservedNodes = function updateObservedNodes() {
		if (!mutationObserver) return;
		mutationObserver.disconnect();
		if (state.active && !state.paused) state.containers.map(function(container) {
			mutationObserver.observe(container, {
				subtree: true,
				childList: true
			});
		});
	};
	trap = {
		get active() {
			return state.active;
		},
		get paused() {
			return state.paused;
		},
		activate: function activate(activateOptions) {
			if (state.active) return this;
			var onActivate = getOption(activateOptions, "onActivate");
			var onPostActivate = getOption(activateOptions, "onPostActivate");
			var checkCanFocusTrap = getOption(activateOptions, "checkCanFocusTrap");
			if (!checkCanFocusTrap) updateTabbableNodes();
			state.active = true;
			state.paused = false;
			state.nodeFocusedBeforeActivation = _getActiveElement(doc);
			onActivate === null || onActivate === void 0 || onActivate();
			var finishActivation = function finishActivation() {
				if (checkCanFocusTrap) updateTabbableNodes();
				addListeners();
				updateObservedNodes();
				onPostActivate === null || onPostActivate === void 0 || onPostActivate();
			};
			if (checkCanFocusTrap) {
				checkCanFocusTrap(state.containers.concat()).then(finishActivation, finishActivation);
				return this;
			}
			finishActivation();
			return this;
		},
		deactivate: function deactivate(deactivateOptions) {
			if (!state.active) return this;
			var options = _objectSpread2({
				onDeactivate: config.onDeactivate,
				onPostDeactivate: config.onPostDeactivate,
				checkCanReturnFocus: config.checkCanReturnFocus
			}, deactivateOptions);
			clearTimeout(state.delayInitialFocusTimer);
			state.delayInitialFocusTimer = void 0;
			removeListeners();
			state.active = false;
			state.paused = false;
			updateObservedNodes();
			activeFocusTraps.deactivateTrap(trapStack, trap);
			var onDeactivate = getOption(options, "onDeactivate");
			var onPostDeactivate = getOption(options, "onPostDeactivate");
			var checkCanReturnFocus = getOption(options, "checkCanReturnFocus");
			var returnFocus = getOption(options, "returnFocus", "returnFocusOnDeactivate");
			onDeactivate === null || onDeactivate === void 0 || onDeactivate();
			var finishDeactivation = function finishDeactivation() {
				delay$1(function() {
					if (returnFocus) _tryFocus(getReturnFocusNode(state.nodeFocusedBeforeActivation));
					onPostDeactivate === null || onPostDeactivate === void 0 || onPostDeactivate();
				});
			};
			if (returnFocus && checkCanReturnFocus) {
				checkCanReturnFocus(getReturnFocusNode(state.nodeFocusedBeforeActivation)).then(finishDeactivation, finishDeactivation);
				return this;
			}
			finishDeactivation();
			return this;
		},
		pause: function pause(pauseOptions) {
			if (!state.active) return this;
			state.manuallyPaused = true;
			return this._setPausedState(true, pauseOptions);
		},
		unpause: function unpause(unpauseOptions) {
			if (!state.active) return this;
			state.manuallyPaused = false;
			if (trapStack[trapStack.length - 1] !== this) return this;
			return this._setPausedState(false, unpauseOptions);
		},
		updateContainerElements: function updateContainerElements(containerElements) {
			state.containers = [].concat(containerElements).filter(Boolean).map(function(element) {
				return typeof element === "string" ? doc.querySelector(element) : element;
			});
			if (state.active) updateTabbableNodes();
			updateObservedNodes();
			return this;
		}
	};
	Object.defineProperties(trap, {
		_isManuallyPaused: { value: function value() {
			return state.manuallyPaused;
		} },
		_setPausedState: { value: function value(paused, options) {
			if (state.paused === paused) return this;
			state.paused = paused;
			if (paused) {
				var onPause = getOption(options, "onPause");
				var onPostPause = getOption(options, "onPostPause");
				onPause === null || onPause === void 0 || onPause();
				removeListeners();
				updateObservedNodes();
				onPostPause === null || onPostPause === void 0 || onPostPause();
			} else {
				var onUnpause = getOption(options, "onUnpause");
				var onPostUnpause = getOption(options, "onPostUnpause");
				onUnpause === null || onUnpause === void 0 || onUnpause();
				updateTabbableNodes();
				addListeners();
				updateObservedNodes();
				onPostUnpause === null || onPostUnpause === void 0 || onPostUnpause();
			}
			return this;
		} }
	});
	trap.updateContainerElements(elements);
	return trap;
};
// node_modules/.pnpm/@vueuse+integrations@14.4.0_ecaa237c85c2ef42a6a56c9f046d1484/node_modules/@vueuse/integrations/dist/useFocusTrap.js
/**
* Reactive focus-trap
*
* @see https://vueuse.org/useFocusTrap
*/
function useFocusTrap(target, options = {}) {
	let trap;
	const { immediate, ...focusTrapOptions } = options;
	const hasFocus = /* @__PURE__ */ shallowRef(false);
	const isPaused = /* @__PURE__ */ shallowRef(false);
	const activate = (opts) => trap && trap.activate(opts);
	const deactivate = (opts) => trap && trap.deactivate(opts);
	const pause = () => {
		if (trap) {
			trap.pause();
			isPaused.value = true;
		}
	};
	const unpause = () => {
		if (trap) {
			trap.unpause();
			isPaused.value = false;
		}
	};
	watch$2(computed$2(() => {
		return toArray(toValue(target)).map((el) => {
			const _el = toValue(el);
			return typeof _el === "string" ? _el : unrefElement(_el);
		}).filter(notNullish);
	}), (els) => {
		if (!els.length) return;
		if (!trap) {
			trap = createFocusTrap(els, {
				...focusTrapOptions,
				onActivate(params) {
					hasFocus.value = true;
					if (options.onActivate) options.onActivate(params);
				},
				onDeactivate(params) {
					hasFocus.value = false;
					if (options.onDeactivate) options.onDeactivate(params);
				}
			});
			if (immediate) activate();
		} else {
			const isActive = trap === null || trap === void 0 ? void 0 : trap.active;
			trap === null || trap === void 0 || trap.updateContainerElements(els);
			if (!isActive && immediate) activate();
		}
	}, { flush: "post" });
	return {
		hasFocus,
		isPaused,
		activate,
		deactivate,
		pause,
		unpause
	};
}
// node_modules/.pnpm/vue-final-modal@4.5.6_@vueu_0a6f72f093ebe4ef95b78694c8e75fc5/node_modules/vue-final-modal/dist/index.es.mjs
var uo = (e) => (...o) => {
	e && (e?.(...o), e = null);
};
var q = () => {};
function oe(e, o, l) {
	return e > l ? l : e < o ? o : e;
}
var we = (e) => typeof e == "string";
function ve(e, o) {
	var s;
	const l = ((s = $$1(e, o)) == null ? void 0 : s[0]) || o;
	e.push(l);
}
function $$1(e, o) {
	const l = e.indexOf(o);
	if (l !== -1) return e.splice(l, 1);
}
function Fe(e) {
	return Object.entries(e);
}
var co = {
	/**
	* @description Set `null | false` to disable teleport.
	* @default `'body'`
	* @example
	* ```js
	* teleportTo: '#modals'
	* ```
	*/
	teleportTo: {
		type: [
			String,
			null,
			Boolean,
			Object
		],
		default: "body"
	},
	/**
	* @description An uniq name for the open/close a modal via vfm.open/vfm.close APIs.
	* @default `undefined`
	* @example Symbol: `Symbol('MyModal')`
	* @example String: `'AUniqString'`
	* @example Number: `300`
	*/
	modalId: {
		type: [
			String,
			Number,
			Symbol
		],
		default: void 0
	},
	/**
	* @description Display the modal or not.
	* @default `undefined`
	* @example
	* ```js
	* const showModal = ref(false)
	* v-model="showModal"
	* ```
	*/
	modelValue: {
		type: Boolean,
		default: void 0
	},
	/**
	* @description Render the modal via `if` or `show`.
	* @default `'if'`
	* @example
	* ```js
	* displayDirective: 'if'
	* ```
	* @example
	* ```js
	* displayDirective: 'show'
	* ```
	*/
	displayDirective: {
		type: String,
		default: "if",
		validator: (e) => [
			"if",
			"show",
			"visible"
		].includes(e)
	},
	/**
	* @description Hide the overlay or not.
	* @default `undefined`
	* @example
	* ```js
	* hideOverlay="true"
	* ```
	*/
	hideOverlay: {
		type: Boolean,
		default: void 0
	},
	/**
	* @description Customize the overlay behavior.
	*/
	overlayBehavior: {
		type: String,
		default: "auto",
		validator: (e) => ["auto", "persist"].includes(e)
	},
	/**
	* @description Customize the overlay transition.
	* @default `undefined`
	*/
	overlayTransition: {
		type: [String, Object],
		default: void 0
	},
	/**
	* @description Customize the content transition.
	* @default `undefined`
	*/
	contentTransition: {
		type: [String, Object],
		default: void 0
	},
	/**
	* @description Bind class to vfm__overlay.
	* @default `undefined`
	*/
	overlayClass: {
		type: void 0,
		default: void 0
	},
	/**
	* @description Bind class to vfm__content.
	* @default `undefined`
	*/
	contentClass: {
		type: void 0,
		default: void 0
	},
	/**
	* @description Bind style to vfm__overlay.
	* @default `undefined`
	*/
	overlayStyle: {
		type: [
			String,
			Object,
			Array
		],
		default: void 0
	},
	/**
	* @description Bind style to vfm__content.
	* @default `undefined`
	*/
	contentStyle: {
		type: [
			String,
			Object,
			Array
		],
		default: void 0
	},
	/**
	* @description Is it allow to close the modal by clicking the overlay.
	* @default `true`
	*/
	clickToClose: {
		type: Boolean,
		default: !0
	},
	/**
	* @description Is it allow to close the modal by keypress `esc`.
	* @default `true`
	*/
	escToClose: {
		type: Boolean,
		default: !0
	},
	/**
	* @description Is it allow to click outside of the vfm__content when the modal is opened
	* @default `'non-interactive'`
	*/
	background: {
		type: String,
		default: "non-interactive",
		validator: (e) => ["interactive", "non-interactive"].includes(e)
	},
	/**
	* @description
	* * Use `{ disabled: true }` to disable the focusTrap.
	* * Checkout the createOptions type here https://github.com/focus-trap/focus-trap for more.
	* @default `{ allowOutsideClick: true }`
	*/
	focusTrap: {
		type: [Boolean, Object],
		default: () => ({ allowOutsideClick: !0 })
	},
	/**
	* @description Lock body scroll or not when the modal is opened.
	* @default `true`
	*/
	lockScroll: {
		type: Boolean,
		default: !0
	},
	/**
	* @description Creates a padding-right when scroll is locked to prevent the page from jumping
	* @default `true`
	*/
	reserveScrollBarGap: {
		type: Boolean,
		default: !0
	},
	/**
	* @description Define how to increase the zIndex when there are nested modals
	* @default `({ index }) => 1000 + 2 * index`
	*/
	zIndexFn: {
		type: Function,
		default: ({ index: e }) => 1e3 + 2 * e
	},
	/**
	* @description The direction of swiping to close the modal
	* @default `none`
	* @example
	* Set swipeToClose="none" to disable swiping to close
	* ```js
	* swipeToClose="none"
	* ```
	*/
	swipeToClose: {
		type: String,
		default: "none",
		validator: (e) => [
			"none",
			"up",
			"right",
			"down",
			"left"
		].includes(e)
	},
	/**
	* @description Threshold for swipe to close
	* @default `0`
	*/
	threshold: {
		type: Number,
		default: 0
	},
	/**
	* @description If set `:showSwipeBanner="true"`, only allow clicking `swipe-banner` slot to swipe to close
	* @default `undefined`
	* @example
	* ```js
	* swipeToClose="right"
	* :showSwipeBanner="true"
	* ```
	* ```html
	* <VueFinalModal
	*   ...
	*   swipeToClose="right"
	*   :showSwipeBanner="true"
	* >
	*   <template #swipe-banner>
	*     <div style="position: absolute; height: 100%; top: 0; left: 0; width: 10px;" />
	*   </template>
	*   ...modal content
	* </VueFinalModal>
	* ```
	*/
	showSwipeBanner: {
		type: Boolean,
		default: void 0
	},
	/**
	* @description When set `:preventNavigationGestures="true"`, there will be two invisible bars for prevent navigation gestures including swiping back/forward on mobile webkit. For example: Safari mobile.
	* @default `undefined`
	* @example
	* Set preventNavigationGestures="true" to prevent Safari navigation gestures including swiping back/forward.
	* ```js
	* :preventNavigationGestures="true"
	* ```
	*/
	preventNavigationGestures: {
		type: Boolean,
		default: void 0
	}
};
function Oe(e = !1) {
	const o = /* @__PURE__ */ ref$1(e), l = /* @__PURE__ */ ref$1(o.value ? 0 : void 0);
	return [
		o,
		l,
		{
			beforeEnter() {
				l.value = 1;
			},
			afterEnter() {
				l.value = 0;
			},
			beforeLeave() {
				l.value = 3;
			},
			afterLeave() {
				l.value = 2;
			}
		}
	];
}
function fo(e, o) {
	const { modelValueLocal: l, onEntering: s, onEnter: u, onLeaving: c, onLeave: a } = o, n = /* @__PURE__ */ ref$1(l.value), [t, r, m] = Oe(n.value), [f, M, S] = Oe(n.value), V = computed$2(() => typeof e.contentTransition == "string" ? {
		name: e.contentTransition,
		appear: !0
	} : {
		appear: !0,
		...e.contentTransition
	}), O = computed$2(() => typeof e.overlayTransition == "string" ? {
		name: e.overlayTransition,
		appear: !0
	} : {
		appear: !0,
		...e.overlayTransition
	});
	watch$2(computed$2(() => (e.hideOverlay || M.value === 2) && r.value === 2), (k) => {
		k && (n.value = !1);
	}), watch$2(r, (k) => {
		if (k === 1) {
			if (!n.value) return;
			s?.();
		} else if (k === 0) {
			if (!n.value) return;
			u?.();
		} else k === 3 ? c?.() : k === 2 && a?.();
	});
	async function w() {
		n.value = !0, await nextTick(), t.value = !0, f.value = !0;
	}
	function D() {
		t.value = !1, f.value = !1;
	}
	return {
		visible: n,
		contentVisible: t,
		contentListeners: m,
		contentTransition: V,
		overlayVisible: f,
		overlayListeners: S,
		overlayTransition: O,
		enterTransition: w,
		leaveTransition: D
	};
}
function vo(e, o, l) {
	const { vfmRootEl: s, vfmContentEl: u, visible: c, modelValueLocal: a } = l, n = /* @__PURE__ */ ref$1();
	function t() {
		c.value && e.escToClose && (a.value = !1);
	}
	function r(f) {
		n.value = f == null ? void 0 : f.target;
	}
	function m() {
		var f;
		n.value === s.value && (e.clickToClose ? a.value = !1 : ((f = u.value) == null || f.focus(), o("clickOutside")));
	}
	return {
		onEsc: t,
		onMouseupRoot: m,
		onMousedown: r
	};
}
function po(e, o, l) {
	let s = !1;
	const { open: u, close: c } = l, a = /* @__PURE__ */ ref$1(!1), n = {
		get value() {
			return a.value;
		},
		set value(r) {
			t(r);
		}
	};
	function t(r) {
		(r ? u() : c()) ? (a.value = r, r !== e.modelValue && o("update:modelValue", r)) : (s = !0, o("update:modelValue", !r), nextTick(() => {
			s = !1;
		}));
	}
	return watch$2(() => e.modelValue, (r) => {
		s || (n.value = !!r);
	}), { modelValueLocal: n };
}
function yo(e, o) {
	if (e.focusTrap === !1) return {
		focus() {},
		blur() {}
	};
	const { focusEl: l } = o, { hasFocus: s, activate: u, deactivate: c } = useFocusTrap(l, e.focusTrap);
	function a() {
		requestAnimationFrame(() => {
			u();
		});
	}
	function n() {
		s.value && c();
	}
	return {
		focus: a,
		blur: n
	};
}
var be = !1;
if (typeof window < "u") {
	const e = { get passive() {
		be = !0;
	} };
	window.addEventListener("testPassive", null, e), window.removeEventListener("testPassive", null, e);
}
var He = typeof window < "u" && window.navigator && window.navigator.platform && (/iP(ad|hone|od)/.test(window.navigator.platform) || window.navigator.platform === "MacIntel" && window.navigator.maxTouchPoints > 1);
var j = [];
var le = !1;
var ne = 0;
var je = -1;
var W;
var X;
var ho = (e) => {
	if (!e || e.nodeType !== Node.ELEMENT_NODE) return !1;
	const o = window.getComputedStyle(e);
	return ["auto", "scroll"].includes(o.overflowY) && e.scrollHeight > e.clientHeight;
};
var mo = (e, o) => !(e.scrollTop === 0 && o < 0 || e.scrollTop + e.clientHeight + o >= e.scrollHeight && o > 0);
var wo = (e) => {
	const o = [];
	for (; e;) {
		if (o.push(e), e.classList.contains("vfm")) return o;
		e = e.parentElement;
	}
	return o;
};
var bo = (e, o) => {
	let l = !1;
	return wo(e).forEach((u) => {
		ho(u) && mo(u, o) && (l = !0);
	}), l;
};
var Ne = (e) => j.some(() => bo(e, -ne));
var se = (e) => {
	const o = e || window.event;
	return Ne(o.target) || o.touches.length > 1 ? !0 : (o.preventDefault && o.preventDefault(), !1);
};
var To = (e) => {
	if (X === void 0) {
		const o = !!e && e.reserveScrollBarGap === !0, l = window.innerWidth - document.documentElement.clientWidth;
		if (o && l > 0) {
			const s = parseInt(getComputedStyle(document.body).getPropertyValue("padding-right"), 10);
			X = document.body.style.paddingRight, document.body.style.paddingRight = `${s + l}px`;
		}
	}
	W === void 0 && (W = document.body.style.overflow, document.body.style.overflow = "hidden");
};
var So = () => {
	X !== void 0 && (document.body.style.paddingRight = X, X = void 0), W !== void 0 && (document.body.style.overflow = W, W = void 0);
};
var Mo = (e) => e ? e.scrollHeight - e.scrollTop <= e.clientHeight : !1;
var go = (e, o) => (ne = e.targetTouches[0].clientY - je, Ne(e.target) ? !1 : o && o.scrollTop === 0 && ne > 0 || Mo(o) && ne < 0 ? se(e) : (e.stopPropagation(), !0));
var Co = (e, o) => {
	if (!e) {
		console.error("disableBodyScroll unsuccessful - targetElement must be provided when calling disableBodyScroll on IOS devices.");
		return;
	}
	if (j.some((s) => s.targetElement === e)) return;
	const l = {
		targetElement: e,
		options: o || {}
	};
	j = [...j, l], He ? (e.ontouchstart = (s) => {
		s.targetTouches.length === 1 && (je = s.targetTouches[0].clientY);
	}, e.ontouchmove = (s) => {
		s.targetTouches.length === 1 && go(s, e);
	}, le || (document.addEventListener("touchmove", se, be ? { passive: !1 } : void 0), le = !0)) : To(o);
};
var ko = (e) => {
	if (!e) {
		console.error("enableBodyScroll unsuccessful - targetElement must be provided when calling enableBodyScroll on IOS devices.");
		return;
	}
	j = j.filter((o) => o.targetElement !== e), He ? (e.ontouchstart = null, e.ontouchmove = null, le && j.length === 0 && (document.removeEventListener("touchmove", se, be ? { passive: !1 } : void 0), le = !1)) : j.length || So();
};
function Vo(e, o) {
	const { lockScrollEl: l, modelValueLocal: s } = o;
	let u;
	watch$2(l, (n) => {
		n && (u = n);
	}, { immediate: !0 }), watch$2(() => e.lockScroll, (n) => {
		n ? a() : c();
	}), onBeforeUnmount$1(() => {
		c();
	});
	function c() {
		u && ko(u);
	}
	function a() {
		s.value && e.lockScroll && u && Co(u, {
			reserveScrollBarGap: e.reserveScrollBarGap,
			allowTouchMove: (n) => {
				for (; n && n !== document.body;) {
					if (n.getAttribute("vfm-scroll-lock-ignore") !== null) return !0;
					n = n.parentElement;
				}
				return !1;
			}
		});
	}
	return {
		enableBodyScroll: c,
		disableBodyScroll: a
	};
}
function Eo(e) {
	const o = /* @__PURE__ */ ref$1();
	function l(u) {
		var c;
		o.value = (c = e.zIndexFn) == null ? void 0 : c.call(e, { index: u <= -1 ? 0 : u });
	}
	function s() {
		o.value = void 0;
	}
	return {
		zIndex: o,
		refreshZIndex: l,
		resetZIndex: s
	};
}
var pe = {
	beforeMount(e, { value: o }, { transition: l }) {
		e._vov = e.style.visibility === "hidden" ? "" : e.style.visibility, l && o ? l.beforeEnter(e) : G(e, o);
	},
	mounted(e, { value: o }, { transition: l }) {
		l && o && l.enter(e);
	},
	updated(e, { value: o, oldValue: l }, { transition: s }) {
		!o != !l && (s ? o ? (s.beforeEnter(e), G(e, !0), s.enter(e)) : s.leave(e, () => {
			G(e, !1);
		}) : G(e, o));
	},
	beforeUnmount(e, { value: o }) {
		G(e, o);
	}
};
function G(e, o) {
	e.style.visibility = o ? e._vov : "hidden";
}
var De = (e) => {
	if (e instanceof MouseEvent) {
		const { clientX: o, clientY: l } = e;
		return {
			x: o,
			y: l
		};
	} else {
		const { clientX: o, clientY: l } = e.targetTouches[0];
		return {
			x: o,
			y: l
		};
	}
};
function Bo(e) {
	if (!e) return !1;
	let o = !1;
	return e.addEventListener("x", q, { get passive() {
		return o = !0, !1;
	} }), e.removeEventListener("x", q), o;
}
function Oo(e, { threshold: o = 0, onSwipeStart: l, onSwipe: s, onSwipeEnd: u, passive: c = !0 }) {
	const a = /* @__PURE__ */ reactive({
		x: 0,
		y: 0
	}), n = /* @__PURE__ */ reactive({
		x: 0,
		y: 0
	}), t = computed$2(() => a.x - n.x), r = computed$2(() => a.y - n.y), { max: m, abs: f } = Math, M = computed$2(() => m(f(t.value), f(r.value)) >= o), S = /* @__PURE__ */ ref$1(!1), V = computed$2(() => M.value ? f(t.value) > f(r.value) ? t.value > 0 ? "left" : "right" : r.value > 0 ? "up" : "down" : "none"), O = (p, h) => {
		a.x = p, a.y = h;
	}, E = (p, h) => {
		n.x = p, n.y = h;
	};
	let w, D;
	function k(p) {
		w.capture && !w.passive && p.preventDefault();
		const { x: h, y: R } = De(p);
		O(h, R), E(h, R), l?.(p), D = [
			useEventListener$1(e, "mousemove", P, w),
			useEventListener$1(e, "touchmove", P, w),
			useEventListener$1(e, "mouseup", i, w),
			useEventListener$1(e, "touchend", i, w),
			useEventListener$1(e, "touchcancel", i, w)
		];
	}
	function P(p) {
		const { x: h, y: R } = De(p);
		E(h, R), !S.value && M.value && (S.value = !0), S.value && s?.(p);
	}
	function i(p) {
		S.value && u?.(p, V.value), S.value = !1, D.forEach((h) => h());
	}
	let b = [];
	return onMounted$1(() => {
		const p = Bo(window == null ? void 0 : window.document);
		c ? w = p ? { passive: !0 } : { capture: !1 } : w = p ? {
			passive: !1,
			capture: !0
		} : { capture: !0 }, b = [useEventListener$1(e, "mousedown", k, w), useEventListener$1(e, "touchstart", k, w)];
	}), {
		isSwiping: S,
		direction: V,
		coordsStart: a,
		coordsEnd: n,
		lengthX: t,
		lengthY: r,
		stop: () => {
			b.forEach((p) => p()), D.forEach((p) => p());
		}
	};
}
function Do(e, o) {
	const { vfmContentEl: l, modelValueLocal: s } = o, u = .1, c = 300, a = /* @__PURE__ */ ref$1(), n = computed$2(() => {
		if (!(e.swipeToClose === void 0 || e.swipeToClose === "none")) return e.showSwipeBanner ? a.value : l.value;
	}), t = /* @__PURE__ */ ref$1(0), r = /* @__PURE__ */ ref$1(!0);
	let m = q, f = !0, M, S = !1;
	const { lengthX: V, lengthY: O, direction: E, isSwiping: w } = Oo(n, {
		threshold: e.threshold,
		onSwipeStart(i) {
			m = useEventListener$1(document, "selectionchange", () => {
				var b;
				r.value = (b = window.getSelection()) == null ? void 0 : b.isCollapsed;
			}), M = (/* @__PURE__ */ new Date()).getTime(), S = P(i == null ? void 0 : i.target);
		},
		onSwipe() {
			var i, b, L, p;
			if (S && r.value && E.value === e.swipeToClose) {
				if (E.value === "up") {
					const h = oe(Math.abs(O.value || 0), 0, ((i = n.value) == null ? void 0 : i.offsetHeight) || 0) - (e.threshold || 0);
					t.value = h;
				} else if (E.value === "down") {
					const h = oe(Math.abs(O.value || 0), 0, ((b = n.value) == null ? void 0 : b.offsetHeight) || 0) - (e.threshold || 0);
					t.value = -h;
				} else if (E.value === "right") {
					const h = oe(Math.abs(V.value || 0), 0, ((L = n.value) == null ? void 0 : L.offsetWidth) || 0) - (e.threshold || 0);
					t.value = -h;
				} else if (E.value === "left") {
					const h = oe(Math.abs(V.value || 0), 0, ((p = n.value) == null ? void 0 : p.offsetWidth) || 0) - (e.threshold || 0);
					t.value = h;
				}
			}
		},
		onSwipeEnd(i, b) {
			if (m(), !r.value) {
				r.value = !0;
				return;
			}
			const L = (/* @__PURE__ */ new Date()).getTime(), p = b === e.swipeToClose, h = (() => {
				var J, Q;
				if (b === "up" || b === "down") return Math.abs((O == null ? void 0 : O.value) || 0) > u * (((J = n.value) == null ? void 0 : J.offsetHeight) || 0);
				if (b === "left" || b === "right") return Math.abs((V == null ? void 0 : V.value) || 0) > u * (((Q = n.value) == null ? void 0 : Q.offsetWidth) || 0);
			})(), R = L - M <= c;
			if (f && S && p && (h || R)) {
				s.value = !1;
				return;
			}
			t.value = 0;
		}
	}), D = computed$2(() => {
		if (e.swipeToClose === "none") return;
		const i = (() => {
			switch (e.swipeToClose) {
				case "up":
				case "down": return "translateY";
				case "left":
				case "right": return "translateX";
			}
		})();
		return {
			class: { "vfm-bounce-back": !w.value },
			style: { transform: `${i}(${-t.value}px)` }
		};
	});
	watch$2(() => r.value, (i) => {
		i || (t.value = 0);
	}), watch$2(() => s.value, (i) => {
		i && (t.value = 0);
	}), watch$2(() => t.value, (i, b) => {
		switch (e.swipeToClose) {
			case "down":
			case "right":
				f = i < b;
				break;
			case "up":
			case "left": f = i > b;
		}
	});
	function k(i) {
		e.preventNavigationGestures && i.preventDefault();
	}
	function P(i) {
		const b = i == null ? void 0 : i.tagName;
		if (!b || ["INPUT", "TEXTAREA"].includes(b)) return !1;
		const L = (() => {
			switch (e.swipeToClose) {
				case "up": return (i == null ? void 0 : i.scrollTop) + (i == null ? void 0 : i.clientHeight) === (i == null ? void 0 : i.scrollHeight);
				case "left": return (i == null ? void 0 : i.scrollLeft) + (i == null ? void 0 : i.clientWidth) === (i == null ? void 0 : i.scrollWidth);
				case "down": return (i == null ? void 0 : i.scrollTop) === 0;
				case "right": return (i == null ? void 0 : i.scrollLeft) === 0;
				default: return !1;
			}
		})();
		return i === n.value ? L : L && P(i == null ? void 0 : i.parentElement);
	}
	return {
		vfmContentEl: l,
		swipeBannerEl: a,
		bindSwipe: D,
		onTouchStartSwipeBanner: k
	};
}
var Ye = Symbol("vfm");
var H;
var Po = {
	install: q,
	modals: [],
	openedModals: [],
	openedModalOverlays: [],
	dynamicModals: [],
	modalsContainers: /* @__PURE__ */ ref$1([]),
	get: () => {},
	toggle: () => {},
	open: () => {},
	close: () => {},
	closeAll: () => Promise.allSettled([])
};
var Ao = () => getCurrentInstance() && inject(Ye, Po) || H;
function Z(e) {
	var o;
	return (o = e == null ? void 0 : e.exposed) == null ? void 0 : o.modalExposed;
}
var Ro = /* @__PURE__ */ defineComponent({
	.../* @__PURE__ */ defineComponent({ inheritAttrs: !1 }),
	__name: "VueFinalModal",
	props: co,
	emits: [
		"update:modelValue",
		"beforeOpen",
		"opened",
		"beforeClose",
		"closed",
		"clickOutside"
	],
	setup(e, { expose: o, emit: l }) {
		const s = e, u = l, c = useAttrs(), a = getCurrentInstance(), { modals: n, openedModals: t, openedModalOverlays: r } = K(), m = /* @__PURE__ */ ref$1(), f = /* @__PURE__ */ ref$1(), { focus: M, blur: S } = yo(s, { focusEl: m }), { zIndex: V, refreshZIndex: O, resetZIndex: E } = Eo(s), { modelValueLocal: w } = po(s, u, {
			open: We,
			close: Xe
		}), { enableBodyScroll: D, disableBodyScroll: k } = Vo(s, {
			lockScrollEl: m,
			modelValueLocal: w
		});
		let P = q;
		const { visible: i, contentVisible: b, contentListeners: L, contentTransition: p, overlayVisible: h, overlayListeners: R, overlayTransition: J, enterTransition: Q, leaveTransition: xe } = fo(s, {
			modelValueLocal: w,
			onEntering() {
				nextTick(() => {
					k(), M();
				});
			},
			onEnter() {
				u("opened"), P("opened");
			},
			onLeave() {
				$$1(t, a), E(), D(), u("closed"), P("closed");
			}
		}), { onEsc: ze, onMouseupRoot: Ge, onMousedown: Te } = vo(s, u, {
			vfmRootEl: m,
			vfmContentEl: f,
			visible: i,
			modelValueLocal: w
		}), { swipeBannerEl: $e, bindSwipe: Ue, onTouchStartSwipeBanner: Se } = Do(s, {
			vfmContentEl: f,
			modelValueLocal: w
		}), Me = computed$2(() => a ? t.indexOf(a) : -1);
		watch$2([() => s.zIndexFn, Me], () => {
			i.value && O(Me.value);
		}), onMounted$1(() => {
			ve(n, a);
		}), s.modelValue && (w.value = !0);
		function We() {
			let d = !1;
			return u("beforeOpen", { stop: () => d = !0 }), d ? !1 : (ve(t, a), ve(r, a), ae(), Q(), !0);
		}
		function Xe() {
			let d = !1;
			return u("beforeClose", { stop: () => d = !0 }), d ? !1 : ($$1(r, a), ae(), S(), xe(), !0);
		}
		function Ze() {
			w.value = !1;
		}
		onBeforeUnmount$1(() => {
			D(), $$1(n, a), $$1(t, a), S(), ae();
		});
		async function ae() {
			await nextTick();
			const d = r.filter((y) => {
				var A;
				const T = Z(y);
				return (T == null ? void 0 : T.value.overlayBehavior.value) === "auto" && !((A = T == null ? void 0 : T.value.hideOverlay) != null && A.value);
			});
			d.forEach((y, T) => {
				const A = Z(y);
				A != null && A.value && (A.value.overlayVisible.value = T === d.length - 1);
			});
		}
		const Ke = /* @__PURE__ */ toRef$1(() => s.modalId), ge = /* @__PURE__ */ toRef$1(() => s.hideOverlay), qe = /* @__PURE__ */ toRef$1(() => s.overlayBehavior);
		return o({ modalExposed: computed$2(() => ({
			modalId: Ke,
			hideOverlay: ge,
			overlayBehavior: qe,
			overlayVisible: h,
			toggle(d) {
				return new Promise((y) => {
					P = uo((A) => y(A));
					const T = typeof d == "boolean" ? d : !w.value;
					w.value = T;
				});
			}
		})) }), (d, y) => (openBlock(), createBlock(Teleport, {
			to: d.teleportTo ? d.teleportTo : void 0,
			disabled: !d.teleportTo
		}, [d.displayDirective !== "if" || unref$1(i) ? withDirectives((openBlock(), createElementBlock("div", mergeProps({ key: 0 }, unref$1(c), {
			ref_key: "vfmRootEl",
			ref: m,
			class: ["vfm vfm--fixed vfm--inset", { "vfm--prevent-none": d.background === "interactive" }],
			style: { zIndex: unref$1(V) },
			role: "dialog",
			"aria-modal": "true",
			onKeydown: y[7] || (y[7] = withKeys(() => unref$1(ze)(), ["esc"])),
			onMouseup: y[8] || (y[8] = withModifiers(() => unref$1(Ge)(), ["self"])),
			onMousedown: y[9] || (y[9] = withModifiers((T) => unref$1(Te)(T), ["self"]))
		}), [ge.value ? createCommentVNode("", !0) : (openBlock(), createBlock(Transition, mergeProps({ key: 0 }, unref$1(J), toHandlers(unref$1(R))), {
			default: withCtx(() => [d.displayDirective !== "if" || unref$1(h) ? withDirectives((openBlock(), createElementBlock("div", {
				key: 0,
				class: normalizeClass(["vfm__overlay vfm--overlay vfm--absolute vfm--inset vfm--prevent-none", d.overlayClass]),
				style: normalizeStyle(d.overlayStyle),
				"aria-hidden": "true"
			}, null, 6)), [[vShow, d.displayDirective !== "show" || unref$1(h)], [unref$1(pe), d.displayDirective !== "visible" || unref$1(h)]]) : createCommentVNode("", !0)]),
			_: 1
		}, 16)), createVNode(Transition, mergeProps(unref$1(p), toHandlers(unref$1(L))), {
			default: withCtx(() => [d.displayDirective !== "if" || unref$1(b) ? withDirectives((openBlock(), createElementBlock("div", mergeProps({
				key: 0,
				ref_key: "vfmContentEl",
				ref: f,
				class: ["vfm__content vfm--outline-none", [d.contentClass, { "vfm--prevent-auto": d.background === "interactive" }]],
				style: d.contentStyle,
				tabindex: "0"
			}, unref$1(Ue), { onMousedown: y[6] || (y[6] = () => unref$1(Te)()) }), [renderSlot(d.$slots, "default", normalizeProps(guardReactiveProps({ close: Ze }))), d.showSwipeBanner ? (openBlock(), createElementBlock("div", {
				key: 0,
				ref_key: "swipeBannerEl",
				ref: $e,
				class: "vfm-swipe-banner-container",
				onTouchstart: y[2] || (y[2] = (T) => unref$1(Se)(T))
			}, [renderSlot(d.$slots, "swipe-banner", {}, () => [createBaseVNode("div", {
				class: "vfm-swipe-banner-back",
				onTouchstart: y[0] || (y[0] = (T) => d.swipeToClose === "left" && T.preventDefault())
			}, null, 32), createBaseVNode("div", {
				class: "vfm-swipe-banner-forward",
				onTouchstart: y[1] || (y[1] = (T) => d.swipeToClose === "right" && T.preventDefault())
			}, null, 32)])], 544)) : !d.showSwipeBanner && d.preventNavigationGestures ? (openBlock(), createElementBlock("div", {
				key: 1,
				class: "vfm-swipe-banner-container",
				onTouchstart: y[5] || (y[5] = (T) => unref$1(Se)(T))
			}, [createBaseVNode("div", {
				class: "vfm-swipe-banner-back",
				onTouchstart: y[3] || (y[3] = (T) => d.swipeToClose === "left" && T.preventDefault())
			}, null, 32), createBaseVNode("div", {
				class: "vfm-swipe-banner-forward",
				onTouchstart: y[4] || (y[4] = (T) => d.swipeToClose === "right" && T.preventDefault())
			}, null, 32)], 32)) : createCommentVNode("", !0)], 16)), [[vShow, d.displayDirective !== "show" || unref$1(b)], [unref$1(pe), d.displayDirective !== "visible" || unref$1(b)]]) : createCommentVNode("", !0)]),
			_: 3
		}, 16)], 16)), [[vShow, d.displayDirective !== "show" || unref$1(i)], [unref$1(pe), d.displayDirective !== "visible" || unref$1(i)]]) : createCommentVNode("", !0)], 8, ["to", "disabled"]));
	}
});
function K() {
	const e = Ao();
	if (!e) throw new Error(`[Vue Final Modal]: getActiveVfm was called with no active Vfm. Did you forget to install vfm?
	const vfm = createVfm()
	app.use(vfm)
This will fail in production.`);
	return e;
}
function Le(e, o = Ro) {
	const { component: l, slots: s, ...u } = e, c = typeof s > "u" ? {} : Object.fromEntries(Fe(s).map(([a, n]) => we(n) ? [a, n] : re(n) ? [a, {
		...n,
		component: markRaw(n.component)
	}] : [a, markRaw(n)]));
	return {
		...u,
		component: markRaw(l || o),
		slots: c
	};
}
function $o(e) {
	const o = /* @__PURE__ */ reactive({
		id: Symbol("useModal"),
		modelValue: !!(e != null && e.defaultModelValue),
		resolveOpened: () => {},
		resolveClosed: () => {},
		attrs: {},
		...Le(e)
	});
	tryOnUnmounted(() => {
		o != null && o.keepAlive || n();
	}), o.modelValue === !0 && nextTick(() => {
		K()?.dynamicModals.push(o);
	});
	async function l() {
		let t;
		return await nextTick(), t = K(), o.modelValue ? Promise.resolve("[Vue Final Modal] modal is already opened.") : (n(), o.modelValue = !0, t.dynamicModals.push(o), new Promise((r) => {
			o.resolveOpened = () => r("opened");
		}));
	}
	function s() {
		return o.modelValue ? (o.modelValue = !1, new Promise((t) => {
			o.resolveClosed = () => t("closed");
		})) : Promise.resolve("[Vue Final Modal] modal is already closed.");
	}
	function u(t) {
		const { slots: r, ...m } = Le(t, o.component);
		t.defaultModelValue !== void 0 && (o.defaultModelValue = t.defaultModelValue), (t == null ? void 0 : t.keepAlive) !== void 0 && (o.keepAlive = t == null ? void 0 : t.keepAlive), c(o, m), r && Fe(r).forEach(([f, M]) => {
			const S = o.slots[f];
			we(S) ? o.slots[f] = M : re(S) && re(M) ? c(S, M) : o.slots[f] = M;
		});
	}
	function c(t, r) {
		r.component && (t.component = r.component), r.attrs && a(t.attrs, r.attrs);
	}
	function a(t, r) {
		return Object.entries(r).forEach(([m, f]) => {
			t[m] = f;
		}), t;
	}
	function n() {
		const t = K(), r = t.dynamicModals.indexOf(o);
		r !== -1 && t.dynamicModals.splice(r, 1);
	}
	return {
		options: o,
		open: l,
		close: s,
		patchOptions: u,
		destroy: n
	};
}
function re(e) {
	return typeof e == "object" && e !== null ? "component" in e : !1;
}
({ .../* @__PURE__ */ defineComponent({ inheritAttrs: !1 }) });
// src/panel/component/Popup.vue?vue&type=script&setup=true&lang.ts
var _hoisted_1$1 = { class: "h-full max-h-[80vh] overflow-y-auto" };
var _hoisted_2$1 = {
	key: 0,
	class: "my-0.5 flex items-center justify-center gap-[20px]"
};
var _hoisted_3$1 = ["onClick"];
// src/panel/component/Popup.vue
var Popup_default = /* @__PURE__ */ defineComponent({
	__name: "Popup",
	props: /*@__PURE__*/ mergeModels({
		buttons: { default: () => [{ name: "取消" }] },
		width: { default: "normal" },
		focusTrap: { type: Boolean }
	}, {
		"modelValue": { type: Boolean },
		"modelModifiers": {}
	}),
	emits: /*@__PURE__*/ mergeModels([
		"update:modelValue",
		"opened",
		"beforeClose",
		"closed"
	], ["update:modelValue"]),
	setup(__props, { emit: __emit }) {
		const visible = useModel(__props, "modelValue");
		const emit = __emit;
		function close() {
			visible.value = false;
		}
		return (_ctx, _cache) => {
			return openBlock(), createBlock(unref$1(Ro), {
				modelValue: visible.value,
				"onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => visible.value = $event),
				class: "TH-popup TH-custom-tailwind absolute! flex h-full w-full items-center justify-center",
				"teleport-to": "body",
				"z-index-fn": () => 1e4,
				"hide-overlay": true,
				"focus-trap": __props.focusTrap,
				onClosed: _cache[1] || (_cache[1] = ($event) => emit("closed"))
			}, {
				default: withCtx(() => [createBaseVNode("div", {
					ref: "dialog_ref",
					class: normalizeClass(["popup box-border justify-between px-1.5! focus:outline-none", {
						"w-[500px]": __props.width === "normal",
						"h-dvh w-dvw!": __props.width === "full",
						"w-fit!": __props.width === "fit"
					}]),
					role: "dialog",
					"aria-modal": "true",
					style: normalizeStyle([
						"normal",
						"full",
						"fit"
					].includes(__props.width) ? "" : __props.width === "wide" ? "min-width: 50vw !important" : `width: ${__props.width} !important;`)
				}, [createBaseVNode("div", _hoisted_1$1, [renderSlot(_ctx.$slots, "default")]), __props.buttons.length ? (openBlock(), createElementBlock("div", _hoisted_2$1, [(openBlock(true), createElementBlock(Fragment, null, renderList(__props.buttons, (action) => {
					return openBlock(), createElementBlock("button", {
						key: action.name,
						class: normalizeClass(["menu_button interactable w-[unset]!", [action.class, { "popup-button-ok": action.shouldEmphasize }]]),
						onClick: ($event) => action.onClick ? action.onClick(close) : close()
					}, toDisplayString(action.name), 11, _hoisted_3$1);
				}), 128))])) : createCommentVNode("", true)], 6)]),
				_: 3
			}, 8, ["modelValue", "focus-trap"]);
		};
	}
});
// src/panel/script/ExportBy.vue?vue&type=script&setup=true&lang.ts
var _hoisted_1 = { class: "flex w-full max-w-[92vw] flex-col gap-1 p-1.5 text-left" };
var _hoisted_2 = { class: "max-h-[45vh] overflow-x-hidden overflow-y-auto pr-1" };
var _hoisted_3 = { class: "mb-0.5 font-bold break-all" };
var _hoisted_4 = { class: "flex flex-row flex-wrap gap-4" };
var _hoisted_5 = {
	key: 0,
	class: "flex items-center gap-0.5 opacity-90"
};
var _hoisted_6 = { class: "leading-none" };
var _hoisted_7 = {
	key: 1,
	class: "flex items-center gap-0.5 opacity-90"
};
var _hoisted_8 = { class: "leading-none" };
var _hoisted_9 = { class: "th-text-sm opacity-80" };
// src/panel/script/ExportBy.vue
var ExportBy_default = /* @__PURE__ */ defineComponent({
	__name: "ExportBy",
	props: {
		targetName: {},
		items: {}
	},
	setup(__props) {
		return (_ctx, _cache) => {
			return openBlock(), createBlock(Popup_default, { buttons: [{ name: _ctx.t`关闭` }] }, {
				default: withCtx(() => [createBaseVNode("div", _hoisted_1, [
					createBaseVNode("div", null, toDisplayString(_ctx.t`本次${__props.targetName}导出中，以下绑定脚本会连同这些内容一起导出：`), 1),
					createBaseVNode("div", _hoisted_2, [(openBlock(true), createElementBlock(Fragment, null, renderList(__props.items, (item) => {
						return openBlock(), createElementBlock("div", {
							key: item.script_name,
							class: "mb-1 border border-(--SmartThemeBorderColor) p-1"
						}, [createBaseVNode("div", _hoisted_3, toDisplayString(item.script_name), 1), createBaseVNode("div", _hoisted_4, [item.has_data ? (openBlock(), createElementBlock("span", _hoisted_5, [createBaseVNode("span", { class: normalizeClass(item.exported_data ? "fa-solid fa-check text-green-500" : "fa-solid fa-xmark text-red-500") }, null, 2), createBaseVNode("span", _hoisted_6, toDisplayString(_ctx.t`变量`), 1)])) : createCommentVNode("", true), item.has_button ? (openBlock(), createElementBlock("span", _hoisted_7, [createBaseVNode("span", { class: normalizeClass(item.exported_button ? "fa-solid fa-check text-green-500" : "fa-solid fa-xmark text-red-500") }, null, 2), createBaseVNode("span", _hoisted_8, toDisplayString(_ctx.t`按钮`), 1)])) : createCommentVNode("", true)])]);
					}), 128))]),
					createBaseVNode("div", _hoisted_9, toDisplayString(_ctx.t`如需调整这些内容的导出行为, 可在对应脚本编辑器的“导出选项”中修改`), 1)
				])]),
				_: 1
			}, 8, ["buttons"]);
		};
	}
});
// src/util/color.ts
var getSmartThemeQuoteColor = () => {
	return getComputedStyle(document.documentElement).getPropertyValue("--SmartThemeQuoteColor").trim();
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/core.js
var _a$1;
/* @__NO_SIDE_EFFECTS__ */
function $constructor(name, initializer, params) {
	function init(inst, def) {
		if (!inst._zod) Object.defineProperty(inst, "_zod", {
			value: {
				def,
				constr: _,
				traits: /* @__PURE__ */ new Set()
			},
			enumerable: false
		});
		if (inst._zod.traits.has(name)) return;
		inst._zod.traits.add(name);
		initializer(inst, def);
		const proto = _.prototype;
		const keys = Object.keys(proto);
		for (let i = 0; i < keys.length; i++) {
			const k = keys[i];
			if (!(k in inst)) inst[k] = proto[k].bind(inst);
		}
	}
	const Parent = params?.Parent ?? Object;
	class Definition extends Parent {}
	Object.defineProperty(Definition, "name", { value: name });
	function _(def) {
		var _a;
		const inst = params?.Parent ? new Definition() : this;
		init(inst, def);
		(_a = inst._zod).deferred ?? (_a.deferred = []);
		for (const fn of inst._zod.deferred) fn();
		return inst;
	}
	Object.defineProperty(_, "init", { value: init });
	Object.defineProperty(_, Symbol.hasInstance, { value: (inst) => {
		if (params?.Parent && inst instanceof params.Parent) return true;
		return inst?._zod?.traits?.has(name);
	} });
	Object.defineProperty(_, "name", { value: name });
	return _;
}
var $ZodAsyncError = class extends Error {
	constructor() {
		super(`Encountered Promise during synchronous parse. Use .parseAsync() instead.`);
	}
};
var $ZodEncodeError = class extends Error {
	constructor(name) {
		super(`Encountered unidirectional transform during encode: ${name}`);
		this.name = "ZodEncodeError";
	}
};
(_a$1 = globalThis).__zod_globalConfig ?? (_a$1.__zod_globalConfig = {});
var globalConfig = globalThis.__zod_globalConfig;
function config(newConfig) {
	if (newConfig) Object.assign(globalConfig, newConfig);
	return globalConfig;
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/util.js
function getEnumValues(entries) {
	const numericValues = Object.values(entries).filter((v) => typeof v === "number");
	return Object.entries(entries).filter(([k, _]) => numericValues.indexOf(+k) === -1).map(([_, v]) => v);
}
function jsonStringifyReplacer(_, value) {
	if (typeof value === "bigint") return value.toString();
	return value;
}
function cached(getter) {
	return { get value() {
		{
			const value = getter();
			Object.defineProperty(this, "value", { value });
			return value;
		}
	} };
}
function nullish(input) {
	return input === null || input === void 0;
}
function cleanRegex(source) {
	const start = source.startsWith("^") ? 1 : 0;
	const end = source.endsWith("$") ? source.length - 1 : source.length;
	return source.slice(start, end);
}
function floatSafeRemainder(val, step) {
	const ratio = val / step;
	const roundedRatio = Math.round(ratio);
	const tolerance = Number.EPSILON * Math.max(Math.abs(ratio), 1);
	if (Math.abs(ratio - roundedRatio) < tolerance) return 0;
	return ratio - roundedRatio;
}
var EVALUATING = /* @__PURE__*/ Symbol("evaluating");
function defineLazy(object, key, getter) {
	let value = void 0;
	Object.defineProperty(object, key, {
		get() {
			if (value === EVALUATING) return;
			if (value === void 0) {
				value = EVALUATING;
				value = getter();
			}
			return value;
		},
		set(v) {
			Object.defineProperty(object, key, { value: v });
		},
		configurable: true
	});
}
function assignProp(target, prop, value) {
	Object.defineProperty(target, prop, {
		value,
		writable: true,
		enumerable: true,
		configurable: true
	});
}
function mergeDefs(...defs) {
	const mergedDescriptors = {};
	for (const def of defs) {
		const descriptors = Object.getOwnPropertyDescriptors(def);
		Object.assign(mergedDescriptors, descriptors);
	}
	return Object.defineProperties({}, mergedDescriptors);
}
function esc(str) {
	return JSON.stringify(str);
}
function slugify(input) {
	return input.toLowerCase().trim().replace(/[^\w\s-]/g, "").replace(/[\s_-]+/g, "-").replace(/^-+|-+$/g, "");
}
var captureStackTrace = "captureStackTrace" in Error ? Error.captureStackTrace : (..._args) => {};
function isObject(data) {
	return typeof data === "object" && data !== null && !Array.isArray(data);
}
var allowsEval = /* @__PURE__*/ cached(() => {
	if (globalConfig.jitless) return false;
	if (typeof navigator !== "undefined" && navigator?.userAgent?.includes("Cloudflare")) return false;
	try {
		new Function("");
		return true;
	} catch (_) {
		return false;
	}
});
function isPlainObject(o) {
	if (isObject(o) === false) return false;
	const ctor = o.constructor;
	if (ctor === void 0) return true;
	if (typeof ctor !== "function") return true;
	const prot = ctor.prototype;
	if (isObject(prot) === false) return false;
	if (Object.prototype.hasOwnProperty.call(prot, "isPrototypeOf") === false) return false;
	return true;
}
function shallowClone(o) {
	if (isPlainObject(o)) return { ...o };
	if (Array.isArray(o)) return [...o];
	if (o instanceof Map) return new Map(o);
	if (o instanceof Set) return new Set(o);
	return o;
}
var propertyKeyTypes = /* @__PURE__*/ new Set([
	"string",
	"number",
	"symbol"
]);
function escapeRegex(str) {
	return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
function clone(inst, def, params) {
	const cl = new inst._zod.constr(def ?? inst._zod.def);
	if (!def || params?.parent) cl._zod.parent = inst;
	return cl;
}
function normalizeParams(_params) {
	const params = _params;
	if (!params) return {};
	if (typeof params === "string") return { error: () => params };
	if (params?.message !== void 0) {
		if (params?.error !== void 0) throw new Error("Cannot specify both `message` and `error` params");
		params.error = params.message;
	}
	delete params.message;
	if (typeof params.error === "string") return {
		...params,
		error: () => params.error
	};
	return params;
}
function optionalKeys(shape) {
	return Object.keys(shape).filter((k) => {
		return shape[k]._zod.optin === "optional" && shape[k]._zod.optout === "optional";
	});
}
var NUMBER_FORMAT_RANGES = {
	safeint: [Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER],
	int32: [-2147483648, 2147483647],
	uint32: [0, 4294967295],
	float32: [-34028234663852886e22, 34028234663852886e22],
	float64: [-Number.MAX_VALUE, Number.MAX_VALUE]
};
function pick(schema, mask) {
	const currDef = schema._zod.def;
	const checks = currDef.checks;
	if (checks && checks.length > 0) throw new Error(".pick() cannot be used on object schemas containing refinements");
	return clone(schema, mergeDefs(schema._zod.def, {
		get shape() {
			const newShape = {};
			for (const key in mask) {
				if (!(key in currDef.shape)) throw new Error(`Unrecognized key: "${key}"`);
				if (!mask[key]) continue;
				newShape[key] = currDef.shape[key];
			}
			assignProp(this, "shape", newShape);
			return newShape;
		},
		checks: []
	}));
}
function omit(schema, mask) {
	const currDef = schema._zod.def;
	const checks = currDef.checks;
	if (checks && checks.length > 0) throw new Error(".omit() cannot be used on object schemas containing refinements");
	return clone(schema, mergeDefs(schema._zod.def, {
		get shape() {
			const newShape = { ...schema._zod.def.shape };
			for (const key in mask) {
				if (!(key in currDef.shape)) throw new Error(`Unrecognized key: "${key}"`);
				if (!mask[key]) continue;
				delete newShape[key];
			}
			assignProp(this, "shape", newShape);
			return newShape;
		},
		checks: []
	}));
}
function extend(schema, shape) {
	if (!isPlainObject(shape)) throw new Error("Invalid input to extend: expected a plain object");
	const checks = schema._zod.def.checks;
	if (checks && checks.length > 0) {
		const existingShape = schema._zod.def.shape;
		for (const key in shape) if (Object.getOwnPropertyDescriptor(existingShape, key) !== void 0) throw new Error("Cannot overwrite keys on object schemas containing refinements. Use `.safeExtend()` instead.");
	}
	return clone(schema, mergeDefs(schema._zod.def, { get shape() {
		const _shape = {
			...schema._zod.def.shape,
			...shape
		};
		assignProp(this, "shape", _shape);
		return _shape;
	} }));
}
function safeExtend(schema, shape) {
	if (!isPlainObject(shape)) throw new Error("Invalid input to safeExtend: expected a plain object");
	return clone(schema, mergeDefs(schema._zod.def, { get shape() {
		const _shape = {
			...schema._zod.def.shape,
			...shape
		};
		assignProp(this, "shape", _shape);
		return _shape;
	} }));
}
function merge$1(a, b) {
	if (a._zod.def.checks?.length) throw new Error(".merge() cannot be used on object schemas containing refinements. Use .safeExtend() instead.");
	return clone(a, mergeDefs(a._zod.def, {
		get shape() {
			const _shape = {
				...a._zod.def.shape,
				...b._zod.def.shape
			};
			assignProp(this, "shape", _shape);
			return _shape;
		},
		get catchall() {
			return b._zod.def.catchall;
		},
		checks: b._zod.def.checks ?? []
	}));
}
function partial(Class, schema, mask) {
	const checks = schema._zod.def.checks;
	if (checks && checks.length > 0) throw new Error(".partial() cannot be used on object schemas containing refinements");
	return clone(schema, mergeDefs(schema._zod.def, {
		get shape() {
			const oldShape = schema._zod.def.shape;
			const shape = { ...oldShape };
			if (mask) for (const key in mask) {
				if (!(key in oldShape)) throw new Error(`Unrecognized key: "${key}"`);
				if (!mask[key]) continue;
				shape[key] = Class ? new Class({
					type: "optional",
					innerType: oldShape[key]
				}) : oldShape[key];
			}
			else for (const key in oldShape) shape[key] = Class ? new Class({
				type: "optional",
				innerType: oldShape[key]
			}) : oldShape[key];
			assignProp(this, "shape", shape);
			return shape;
		},
		checks: []
	}));
}
function required(Class, schema, mask) {
	return clone(schema, mergeDefs(schema._zod.def, { get shape() {
		const oldShape = schema._zod.def.shape;
		const shape = { ...oldShape };
		if (mask) for (const key in mask) {
			if (!(key in shape)) throw new Error(`Unrecognized key: "${key}"`);
			if (!mask[key]) continue;
			shape[key] = new Class({
				type: "nonoptional",
				innerType: oldShape[key]
			});
		}
		else for (const key in oldShape) shape[key] = new Class({
			type: "nonoptional",
			innerType: oldShape[key]
		});
		assignProp(this, "shape", shape);
		return shape;
	} }));
}
function aborted(x, startIndex = 0) {
	if (x.aborted === true) return true;
	for (let i = startIndex; i < x.issues.length; i++) if (x.issues[i]?.continue !== true) return true;
	return false;
}
function explicitlyAborted(x, startIndex = 0) {
	if (x.aborted === true) return true;
	for (let i = startIndex; i < x.issues.length; i++) if (x.issues[i]?.continue === false) return true;
	return false;
}
function prefixIssues(path, issues) {
	return issues.map((iss) => {
		var _a;
		(_a = iss).path ?? (_a.path = []);
		iss.path.unshift(path);
		return iss;
	});
}
function unwrapMessage(message) {
	return typeof message === "string" ? message : message?.message;
}
function finalizeIssue(iss, ctx, config) {
	const message = iss.message ? iss.message : unwrapMessage(iss.inst?._zod.def?.error?.(iss)) ?? unwrapMessage(ctx?.error?.(iss)) ?? unwrapMessage(config.customError?.(iss)) ?? unwrapMessage(config.localeError?.(iss)) ?? "Invalid input";
	const { inst: _inst, continue: _continue, input: _input, ...rest } = iss;
	rest.path ?? (rest.path = []);
	rest.message = message;
	if (ctx?.reportInput) rest.input = _input;
	return rest;
}
function getLengthableOrigin(input) {
	if (Array.isArray(input)) return "array";
	if (typeof input === "string") return "string";
	return "unknown";
}
function issue(...args) {
	const [iss, input, inst] = args;
	if (typeof iss === "string") return {
		message: iss,
		code: "custom",
		input,
		inst
	};
	return { ...iss };
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/errors.js
var initializer$1 = (inst, def) => {
	inst.name = "$ZodError";
	Object.defineProperty(inst, "_zod", {
		value: inst._zod,
		enumerable: false
	});
	Object.defineProperty(inst, "issues", {
		value: def,
		enumerable: false
	});
	inst.message = JSON.stringify(def, jsonStringifyReplacer, 2);
	Object.defineProperty(inst, "toString", {
		value: () => inst.message,
		enumerable: false
	});
};
var $ZodError = /* @__PURE__ */ $constructor("$ZodError", initializer$1);
var $ZodRealError = /* @__PURE__ */ $constructor("$ZodError", initializer$1, { Parent: Error });
function flattenError(error, mapper = (issue) => issue.message) {
	const fieldErrors = {};
	const formErrors = [];
	for (const sub of error.issues) if (sub.path.length > 0) {
		fieldErrors[sub.path[0]] = fieldErrors[sub.path[0]] || [];
		fieldErrors[sub.path[0]].push(mapper(sub));
	} else formErrors.push(mapper(sub));
	return {
		formErrors,
		fieldErrors
	};
}
function formatError(error, mapper = (issue) => issue.message) {
	const fieldErrors = { _errors: [] };
	const processError = (error, path = []) => {
		for (const issue of error.issues) if (issue.code === "invalid_union" && issue.errors.length) issue.errors.map((issues) => processError({ issues }, [...path, ...issue.path]));
		else if (issue.code === "invalid_key") processError({ issues: issue.issues }, [...path, ...issue.path]);
		else if (issue.code === "invalid_element") processError({ issues: issue.issues }, [...path, ...issue.path]);
		else {
			const fullpath = [...path, ...issue.path];
			if (fullpath.length === 0) fieldErrors._errors.push(mapper(issue));
			else {
				let curr = fieldErrors;
				let i = 0;
				while (i < fullpath.length) {
					const el = fullpath[i];
					if (!(i === fullpath.length - 1)) curr[el] = curr[el] || { _errors: [] };
					else {
						curr[el] = curr[el] || { _errors: [] };
						curr[el]._errors.push(mapper(issue));
					}
					curr = curr[el];
					i++;
				}
			}
		}
	};
	processError(error);
	return fieldErrors;
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/parse.js
var _parse = (_Err) => (schema, value, _ctx, _params) => {
	const ctx = _ctx ? {
		..._ctx,
		async: false
	} : { async: false };
	const result = schema._zod.run({
		value,
		issues: []
	}, ctx);
	if (result instanceof Promise) throw new $ZodAsyncError();
	if (result.issues.length) {
		const e = new ((_params?.Err) ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
		captureStackTrace(e, _params?.callee);
		throw e;
	}
	return result.value;
};
var _parseAsync = (_Err) => async (schema, value, _ctx, params) => {
	const ctx = _ctx ? {
		..._ctx,
		async: true
	} : { async: true };
	let result = schema._zod.run({
		value,
		issues: []
	}, ctx);
	if (result instanceof Promise) result = await result;
	if (result.issues.length) {
		const e = new ((params?.Err) ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
		captureStackTrace(e, params?.callee);
		throw e;
	}
	return result.value;
};
var _safeParse = (_Err) => (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		async: false
	} : { async: false };
	const result = schema._zod.run({
		value,
		issues: []
	}, ctx);
	if (result instanceof Promise) throw new $ZodAsyncError();
	return result.issues.length ? {
		success: false,
		error: new (_Err ?? $ZodError)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
	} : {
		success: true,
		data: result.value
	};
};
var safeParse$1 = /* @__PURE__*/ _safeParse($ZodRealError);
var _safeParseAsync = (_Err) => async (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		async: true
	} : { async: true };
	let result = schema._zod.run({
		value,
		issues: []
	}, ctx);
	if (result instanceof Promise) result = await result;
	return result.issues.length ? {
		success: false,
		error: new _Err(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
	} : {
		success: true,
		data: result.value
	};
};
var safeParseAsync$1 = /* @__PURE__*/ _safeParseAsync($ZodRealError);
var _encode = (_Err) => (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		direction: "backward"
	} : { direction: "backward" };
	return _parse(_Err)(schema, value, ctx);
};
var _decode = (_Err) => (schema, value, _ctx) => {
	return _parse(_Err)(schema, value, _ctx);
};
var _encodeAsync = (_Err) => async (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		direction: "backward"
	} : { direction: "backward" };
	return _parseAsync(_Err)(schema, value, ctx);
};
var _decodeAsync = (_Err) => async (schema, value, _ctx) => {
	return _parseAsync(_Err)(schema, value, _ctx);
};
var _safeEncode = (_Err) => (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		direction: "backward"
	} : { direction: "backward" };
	return _safeParse(_Err)(schema, value, ctx);
};
var _safeDecode = (_Err) => (schema, value, _ctx) => {
	return _safeParse(_Err)(schema, value, _ctx);
};
var _safeEncodeAsync = (_Err) => async (schema, value, _ctx) => {
	const ctx = _ctx ? {
		..._ctx,
		direction: "backward"
	} : { direction: "backward" };
	return _safeParseAsync(_Err)(schema, value, ctx);
};
var _safeDecodeAsync = (_Err) => async (schema, value, _ctx) => {
	return _safeParseAsync(_Err)(schema, value, _ctx);
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/regexes.js
/**
* @deprecated CUID v1 is deprecated by its authors due to information leakage
* (timestamps embedded in the id). Use {@link cuid2} instead.
* See https://github.com/paralleldrive/cuid.
*/
var cuid = /^[cC][0-9a-z]{6,}$/;
var cuid2 = /^[0-9a-z]+$/;
var ulid = /^[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}$/;
var xid = /^[0-9a-vA-V]{20}$/;
var ksuid = /^[A-Za-z0-9]{27}$/;
var nanoid = /^[a-zA-Z0-9_-]{21}$/;
/** ISO 8601-1 duration regex. Does not support the 8601-2 extensions like negative durations or fractional/negative components. */
var duration$1 = /^P(?:(\d+W)|(?!.*W)(?=\d|T\d)(\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+([.,]\d+)?S)?)?)$/;
/** A regex for any UUID-like identifier: 8-4-4-4-12 hex pattern */
var guid = /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;
/** Returns a regex for validating an RFC 9562/4122 UUID.
*
* @param version Optionally specify a version 1-8. If no version is specified, all versions are supported. */
var uuid = (version) => {
	if (!version) return /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$/;
	return new RegExp(`^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-${version}[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$`);
};
/** Practical email validation */
var email = /^(?!\.)(?!.*\.\.)([A-Za-z0-9_'+\-\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\-]*\.)+[A-Za-z]{2,}$/;
var _emoji$1 = `^(\\p{Extended_Pictographic}|\\p{Emoji_Component})+$`;
function emoji() {
	return new RegExp(_emoji$1, "u");
}
var ipv4 = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/;
var ipv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:))$/;
var cidrv4 = /^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/([0-9]|[1-2][0-9]|3[0-2])$/;
var cidrv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|::|([0-9a-fA-F]{1,4})?::([0-9a-fA-F]{1,4}:?){0,6})\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/;
var base64 = /^$|^(?:[0-9a-zA-Z+/]{4})*(?:(?:[0-9a-zA-Z+/]{2}==)|(?:[0-9a-zA-Z+/]{3}=))?$/;
var base64url = /^[A-Za-z0-9_-]*$/;
var httpProtocol = /^https?$/;
var e164 = /^\+[1-9]\d{6,14}$/;
var dateSource = `(?:(?:\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-(?:(?:0[13578]|1[02])-(?:0[1-9]|[12]\\d|3[01])|(?:0[469]|11)-(?:0[1-9]|[12]\\d|30)|(?:02)-(?:0[1-9]|1\\d|2[0-8])))`;
var date$1 = /*@__PURE__*/ new RegExp(`^${dateSource}$`);
function timeSource(args) {
	const hhmm = `(?:[01]\\d|2[0-3]):[0-5]\\d`;
	return typeof args.precision === "number" ? args.precision === -1 ? `${hhmm}` : args.precision === 0 ? `${hhmm}:[0-5]\\d` : `${hhmm}:[0-5]\\d\\.\\d{${args.precision}}` : `${hhmm}(?::[0-5]\\d(?:\\.\\d+)?)?`;
}
function time$1(args) {
	return new RegExp(`^${timeSource(args)}$`);
}
function datetime$1(args) {
	const time = timeSource({ precision: args.precision });
	const opts = ["Z"];
	if (args.local) opts.push("");
	if (args.offset) opts.push(`([+-](?:[01]\\d|2[0-3]):[0-5]\\d)`);
	const timeRegex = `${time}(?:${opts.join("|")})`;
	return new RegExp(`^${dateSource}T(?:${timeRegex})$`);
}
var string$3 = (params) => {
	const regex = params ? `[\\s\\S]{${params?.minimum ?? 0},${params?.maximum ?? ""}}` : `[\\s\\S]*`;
	return new RegExp(`^${regex}$`);
};
var integer = /^-?\d+$/;
var number$1 = /^-?\d+(?:\.\d+)?$/;
var boolean$1 = /^(?:true|false)$/i;
var _null$2 = /^null$/i;
var lowercase = /^[^A-Z]*$/;
var uppercase = /^[^a-z]*$/;
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/checks.js
var $ZodCheck = /*@__PURE__*/ $constructor("$ZodCheck", (inst, def) => {
	var _a;
	inst._zod ?? (inst._zod = {});
	inst._zod.def = def;
	(_a = inst._zod).onattach ?? (_a.onattach = []);
});
var numericOriginMap = {
	number: "number",
	bigint: "bigint",
	object: "date"
};
var $ZodCheckLessThan = /*@__PURE__*/ $constructor("$ZodCheckLessThan", (inst, def) => {
	$ZodCheck.init(inst, def);
	const origin = numericOriginMap[typeof def.value];
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		const curr = (def.inclusive ? bag.maximum : bag.exclusiveMaximum) ?? Number.POSITIVE_INFINITY;
		if (def.value < curr) {
			if (def.inclusive) bag.maximum = def.value;
			else bag.exclusiveMaximum = def.value;
		}
	});
	inst._zod.check = (payload) => {
		if (def.inclusive ? payload.value <= def.value : payload.value < def.value) return;
		payload.issues.push({
			origin,
			code: "too_big",
			maximum: typeof def.value === "object" ? def.value.getTime() : def.value,
			input: payload.value,
			inclusive: def.inclusive,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckGreaterThan = /*@__PURE__*/ $constructor("$ZodCheckGreaterThan", (inst, def) => {
	$ZodCheck.init(inst, def);
	const origin = numericOriginMap[typeof def.value];
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		const curr = (def.inclusive ? bag.minimum : bag.exclusiveMinimum) ?? Number.NEGATIVE_INFINITY;
		if (def.value > curr) {
			if (def.inclusive) bag.minimum = def.value;
			else bag.exclusiveMinimum = def.value;
		}
	});
	inst._zod.check = (payload) => {
		if (def.inclusive ? payload.value >= def.value : payload.value > def.value) return;
		payload.issues.push({
			origin,
			code: "too_small",
			minimum: typeof def.value === "object" ? def.value.getTime() : def.value,
			input: payload.value,
			inclusive: def.inclusive,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckMultipleOf = /*@__PURE__*/ $constructor("$ZodCheckMultipleOf", (inst, def) => {
	$ZodCheck.init(inst, def);
	inst._zod.onattach.push((inst) => {
		var _a;
		(_a = inst._zod.bag).multipleOf ?? (_a.multipleOf = def.value);
	});
	inst._zod.check = (payload) => {
		if (typeof payload.value !== typeof def.value) throw new Error("Cannot mix number and bigint in multiple_of check.");
		if (typeof payload.value === "bigint" ? payload.value % def.value === BigInt(0) : floatSafeRemainder(payload.value, def.value) === 0) return;
		payload.issues.push({
			origin: typeof payload.value,
			code: "not_multiple_of",
			divisor: def.value,
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckNumberFormat = /*@__PURE__*/ $constructor("$ZodCheckNumberFormat", (inst, def) => {
	$ZodCheck.init(inst, def);
	def.format = def.format || "float64";
	const isInt = def.format?.includes("int");
	const origin = isInt ? "int" : "number";
	const [minimum, maximum] = NUMBER_FORMAT_RANGES[def.format];
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.format = def.format;
		bag.minimum = minimum;
		bag.maximum = maximum;
		if (isInt) bag.pattern = integer;
	});
	inst._zod.check = (payload) => {
		const input = payload.value;
		if (isInt) {
			if (!Number.isInteger(input)) {
				payload.issues.push({
					expected: origin,
					format: def.format,
					code: "invalid_type",
					continue: false,
					input,
					inst
				});
				return;
			}
			if (!Number.isSafeInteger(input)) {
				if (input > 0) payload.issues.push({
					input,
					code: "too_big",
					maximum: Number.MAX_SAFE_INTEGER,
					note: "Integers must be within the safe integer range.",
					inst,
					origin,
					inclusive: true,
					continue: !def.abort
				});
				else payload.issues.push({
					input,
					code: "too_small",
					minimum: Number.MIN_SAFE_INTEGER,
					note: "Integers must be within the safe integer range.",
					inst,
					origin,
					inclusive: true,
					continue: !def.abort
				});
				return;
			}
		}
		if (input < minimum) payload.issues.push({
			origin: "number",
			input,
			code: "too_small",
			minimum,
			inclusive: true,
			inst,
			continue: !def.abort
		});
		if (input > maximum) payload.issues.push({
			origin: "number",
			input,
			code: "too_big",
			maximum,
			inclusive: true,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckMaxLength = /*@__PURE__*/ $constructor("$ZodCheckMaxLength", (inst, def) => {
	var _a;
	$ZodCheck.init(inst, def);
	(_a = inst._zod.def).when ?? (_a.when = (payload) => {
		const val = payload.value;
		return !nullish(val) && val.length !== void 0;
	});
	inst._zod.onattach.push((inst) => {
		const curr = inst._zod.bag.maximum ?? Number.POSITIVE_INFINITY;
		if (def.maximum < curr) inst._zod.bag.maximum = def.maximum;
	});
	inst._zod.check = (payload) => {
		const input = payload.value;
		if (input.length <= def.maximum) return;
		const origin = getLengthableOrigin(input);
		payload.issues.push({
			origin,
			code: "too_big",
			maximum: def.maximum,
			inclusive: true,
			input,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckMinLength = /*@__PURE__*/ $constructor("$ZodCheckMinLength", (inst, def) => {
	var _a;
	$ZodCheck.init(inst, def);
	(_a = inst._zod.def).when ?? (_a.when = (payload) => {
		const val = payload.value;
		return !nullish(val) && val.length !== void 0;
	});
	inst._zod.onattach.push((inst) => {
		const curr = inst._zod.bag.minimum ?? Number.NEGATIVE_INFINITY;
		if (def.minimum > curr) inst._zod.bag.minimum = def.minimum;
	});
	inst._zod.check = (payload) => {
		const input = payload.value;
		if (input.length >= def.minimum) return;
		const origin = getLengthableOrigin(input);
		payload.issues.push({
			origin,
			code: "too_small",
			minimum: def.minimum,
			inclusive: true,
			input,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckLengthEquals = /*@__PURE__*/ $constructor("$ZodCheckLengthEquals", (inst, def) => {
	var _a;
	$ZodCheck.init(inst, def);
	(_a = inst._zod.def).when ?? (_a.when = (payload) => {
		const val = payload.value;
		return !nullish(val) && val.length !== void 0;
	});
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.minimum = def.length;
		bag.maximum = def.length;
		bag.length = def.length;
	});
	inst._zod.check = (payload) => {
		const input = payload.value;
		const length = input.length;
		if (length === def.length) return;
		const origin = getLengthableOrigin(input);
		const tooBig = length > def.length;
		payload.issues.push({
			origin,
			...tooBig ? {
				code: "too_big",
				maximum: def.length
			} : {
				code: "too_small",
				minimum: def.length
			},
			inclusive: true,
			exact: true,
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckStringFormat = /*@__PURE__*/ $constructor("$ZodCheckStringFormat", (inst, def) => {
	var _a, _b;
	$ZodCheck.init(inst, def);
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.format = def.format;
		if (def.pattern) {
			bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
			bag.patterns.add(def.pattern);
		}
	});
	if (def.pattern) (_a = inst._zod).check ?? (_a.check = (payload) => {
		def.pattern.lastIndex = 0;
		if (def.pattern.test(payload.value)) return;
		payload.issues.push({
			origin: "string",
			code: "invalid_format",
			format: def.format,
			input: payload.value,
			...def.pattern ? { pattern: def.pattern.toString() } : {},
			inst,
			continue: !def.abort
		});
	});
	else (_b = inst._zod).check ?? (_b.check = () => {});
});
var $ZodCheckRegex = /*@__PURE__*/ $constructor("$ZodCheckRegex", (inst, def) => {
	$ZodCheckStringFormat.init(inst, def);
	inst._zod.check = (payload) => {
		def.pattern.lastIndex = 0;
		if (def.pattern.test(payload.value)) return;
		payload.issues.push({
			origin: "string",
			code: "invalid_format",
			format: "regex",
			input: payload.value,
			pattern: def.pattern.toString(),
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckLowerCase = /*@__PURE__*/ $constructor("$ZodCheckLowerCase", (inst, def) => {
	def.pattern ?? (def.pattern = lowercase);
	$ZodCheckStringFormat.init(inst, def);
});
var $ZodCheckUpperCase = /*@__PURE__*/ $constructor("$ZodCheckUpperCase", (inst, def) => {
	def.pattern ?? (def.pattern = uppercase);
	$ZodCheckStringFormat.init(inst, def);
});
var $ZodCheckIncludes = /*@__PURE__*/ $constructor("$ZodCheckIncludes", (inst, def) => {
	$ZodCheck.init(inst, def);
	const escapedRegex = escapeRegex(def.includes);
	const pattern = new RegExp(typeof def.position === "number" ? `^.{${def.position}}${escapedRegex}` : escapedRegex);
	def.pattern = pattern;
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
		bag.patterns.add(pattern);
	});
	inst._zod.check = (payload) => {
		if (payload.value.includes(def.includes, def.position)) return;
		payload.issues.push({
			origin: "string",
			code: "invalid_format",
			format: "includes",
			includes: def.includes,
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckStartsWith = /*@__PURE__*/ $constructor("$ZodCheckStartsWith", (inst, def) => {
	$ZodCheck.init(inst, def);
	const pattern = new RegExp(`^${escapeRegex(def.prefix)}.*`);
	def.pattern ?? (def.pattern = pattern);
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
		bag.patterns.add(pattern);
	});
	inst._zod.check = (payload) => {
		if (payload.value.startsWith(def.prefix)) return;
		payload.issues.push({
			origin: "string",
			code: "invalid_format",
			format: "starts_with",
			prefix: def.prefix,
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckEndsWith = /*@__PURE__*/ $constructor("$ZodCheckEndsWith", (inst, def) => {
	$ZodCheck.init(inst, def);
	const pattern = new RegExp(`.*${escapeRegex(def.suffix)}$`);
	def.pattern ?? (def.pattern = pattern);
	inst._zod.onattach.push((inst) => {
		const bag = inst._zod.bag;
		bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
		bag.patterns.add(pattern);
	});
	inst._zod.check = (payload) => {
		if (payload.value.endsWith(def.suffix)) return;
		payload.issues.push({
			origin: "string",
			code: "invalid_format",
			format: "ends_with",
			suffix: def.suffix,
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodCheckOverwrite = /*@__PURE__*/ $constructor("$ZodCheckOverwrite", (inst, def) => {
	$ZodCheck.init(inst, def);
	inst._zod.check = (payload) => {
		payload.value = def.tx(payload.value);
	};
});
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/doc.js
var Doc = class {
	constructor(args = []) {
		this.content = [];
		this.indent = 0;
		if (this) this.args = args;
	}
	indented(fn) {
		this.indent += 1;
		fn(this);
		this.indent -= 1;
	}
	write(arg) {
		if (typeof arg === "function") {
			arg(this, { execution: "sync" });
			arg(this, { execution: "async" });
			return;
		}
		const lines = arg.split("\n").filter((x) => x);
		const minIndent = Math.min(...lines.map((x) => x.length - x.trimStart().length));
		const dedented = lines.map((x) => x.slice(minIndent)).map((x) => " ".repeat(this.indent * 2) + x);
		for (const line of dedented) this.content.push(line);
	}
	compile() {
		const F = Function;
		const args = this?.args;
		const lines = [...(this?.content ?? [``]).map((x) => `  ${x}`)];
		return new F(...args, lines.join("\n"));
	}
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/versions.js
var version$2 = {
	major: 4,
	minor: 4,
	patch: 3
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/schemas.js
var $ZodType = /*@__PURE__*/ $constructor("$ZodType", (inst, def) => {
	var _a;
	inst ?? (inst = {});
	inst._zod.def = def;
	inst._zod.bag = inst._zod.bag || {};
	inst._zod.version = version$2;
	const checks = [...inst._zod.def.checks ?? []];
	if (inst._zod.traits.has("$ZodCheck")) checks.unshift(inst);
	for (const ch of checks) for (const fn of ch._zod.onattach) fn(inst);
	if (checks.length === 0) {
		(_a = inst._zod).deferred ?? (_a.deferred = []);
		inst._zod.deferred?.push(() => {
			inst._zod.run = inst._zod.parse;
		});
	} else {
		const runChecks = (payload, checks, ctx) => {
			let isAborted = aborted(payload);
			let asyncResult;
			for (const ch of checks) {
				if (ch._zod.def.when) {
					if (explicitlyAborted(payload)) continue;
					if (!ch._zod.def.when(payload)) continue;
				} else if (isAborted) continue;
				const currLen = payload.issues.length;
				const _ = ch._zod.check(payload);
				if (_ instanceof Promise && ctx?.async === false) throw new $ZodAsyncError();
				if (asyncResult || _ instanceof Promise) asyncResult = (asyncResult ?? Promise.resolve()).then(async () => {
					await _;
					if (payload.issues.length === currLen) return;
					if (!isAborted) isAborted = aborted(payload, currLen);
				});
				else {
					if (payload.issues.length === currLen) continue;
					if (!isAborted) isAborted = aborted(payload, currLen);
				}
			}
			if (asyncResult) return asyncResult.then(() => {
				return payload;
			});
			return payload;
		};
		const handleCanaryResult = (canary, payload, ctx) => {
			if (aborted(canary)) {
				canary.aborted = true;
				return canary;
			}
			const checkResult = runChecks(payload, checks, ctx);
			if (checkResult instanceof Promise) {
				if (ctx.async === false) throw new $ZodAsyncError();
				return checkResult.then((checkResult) => inst._zod.parse(checkResult, ctx));
			}
			return inst._zod.parse(checkResult, ctx);
		};
		inst._zod.run = (payload, ctx) => {
			if (ctx.skipChecks) return inst._zod.parse(payload, ctx);
			if (ctx.direction === "backward") {
				const canary = inst._zod.parse({
					value: payload.value,
					issues: []
				}, {
					...ctx,
					skipChecks: true
				});
				if (canary instanceof Promise) return canary.then((canary) => {
					return handleCanaryResult(canary, payload, ctx);
				});
				return handleCanaryResult(canary, payload, ctx);
			}
			const result = inst._zod.parse(payload, ctx);
			if (result instanceof Promise) {
				if (ctx.async === false) throw new $ZodAsyncError();
				return result.then((result) => runChecks(result, checks, ctx));
			}
			return runChecks(result, checks, ctx);
		};
	}
	defineLazy(inst, "~standard", () => ({
		validate: (value) => {
			try {
				const r = safeParse$1(inst, value);
				return r.success ? { value: r.data } : { issues: r.error?.issues };
			} catch (_) {
				return safeParseAsync$1(inst, value).then((r) => r.success ? { value: r.data } : { issues: r.error?.issues });
			}
		},
		vendor: "zod",
		version: 1
	}));
});
var $ZodString = /*@__PURE__*/ $constructor("$ZodString", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.pattern = [...inst?._zod.bag?.patterns ?? []].pop() ?? string$3(inst._zod.bag);
	inst._zod.parse = (payload, _) => {
		if (def.coerce) try {
			payload.value = String(payload.value);
		} catch (_) {}
		if (typeof payload.value === "string") return payload;
		payload.issues.push({
			expected: "string",
			code: "invalid_type",
			input: payload.value,
			inst
		});
		return payload;
	};
});
var $ZodStringFormat = /*@__PURE__*/ $constructor("$ZodStringFormat", (inst, def) => {
	$ZodCheckStringFormat.init(inst, def);
	$ZodString.init(inst, def);
});
var $ZodGUID = /*@__PURE__*/ $constructor("$ZodGUID", (inst, def) => {
	def.pattern ?? (def.pattern = guid);
	$ZodStringFormat.init(inst, def);
});
var $ZodUUID = /*@__PURE__*/ $constructor("$ZodUUID", (inst, def) => {
	if (def.version) {
		const v = {
			v1: 1,
			v2: 2,
			v3: 3,
			v4: 4,
			v5: 5,
			v6: 6,
			v7: 7,
			v8: 8
		}[def.version];
		if (v === void 0) throw new Error(`Invalid UUID version: "${def.version}"`);
		def.pattern ?? (def.pattern = uuid(v));
	} else def.pattern ?? (def.pattern = uuid());
	$ZodStringFormat.init(inst, def);
});
var $ZodEmail = /*@__PURE__*/ $constructor("$ZodEmail", (inst, def) => {
	def.pattern ?? (def.pattern = email);
	$ZodStringFormat.init(inst, def);
});
var $ZodURL = /*@__PURE__*/ $constructor("$ZodURL", (inst, def) => {
	$ZodStringFormat.init(inst, def);
	inst._zod.check = (payload) => {
		try {
			const trimmed = payload.value.trim();
			if (!def.normalize && def.protocol?.source === httpProtocol.source) {
				if (!/^https?:\/\//i.test(trimmed)) {
					payload.issues.push({
						code: "invalid_format",
						format: "url",
						note: "Invalid URL format",
						input: payload.value,
						inst,
						continue: !def.abort
					});
					return;
				}
			}
			const url = new URL(trimmed);
			if (def.hostname) {
				def.hostname.lastIndex = 0;
				if (!def.hostname.test(url.hostname)) payload.issues.push({
					code: "invalid_format",
					format: "url",
					note: "Invalid hostname",
					pattern: def.hostname.source,
					input: payload.value,
					inst,
					continue: !def.abort
				});
			}
			if (def.protocol) {
				def.protocol.lastIndex = 0;
				if (!def.protocol.test(url.protocol.endsWith(":") ? url.protocol.slice(0, -1) : url.protocol)) payload.issues.push({
					code: "invalid_format",
					format: "url",
					note: "Invalid protocol",
					pattern: def.protocol.source,
					input: payload.value,
					inst,
					continue: !def.abort
				});
			}
			if (def.normalize) payload.value = url.href;
			else payload.value = trimmed;
			return;
		} catch (_) {
			payload.issues.push({
				code: "invalid_format",
				format: "url",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		}
	};
});
var $ZodEmoji = /*@__PURE__*/ $constructor("$ZodEmoji", (inst, def) => {
	def.pattern ?? (def.pattern = emoji());
	$ZodStringFormat.init(inst, def);
});
var $ZodNanoID = /*@__PURE__*/ $constructor("$ZodNanoID", (inst, def) => {
	def.pattern ?? (def.pattern = nanoid);
	$ZodStringFormat.init(inst, def);
});
/**
* @deprecated CUID v1 is deprecated by its authors due to information leakage
* (timestamps embedded in the id). Use {@link $ZodCUID2} instead.
* See https://github.com/paralleldrive/cuid.
*/
var $ZodCUID = /*@__PURE__*/ $constructor("$ZodCUID", (inst, def) => {
	def.pattern ?? (def.pattern = cuid);
	$ZodStringFormat.init(inst, def);
});
var $ZodCUID2 = /*@__PURE__*/ $constructor("$ZodCUID2", (inst, def) => {
	def.pattern ?? (def.pattern = cuid2);
	$ZodStringFormat.init(inst, def);
});
var $ZodULID = /*@__PURE__*/ $constructor("$ZodULID", (inst, def) => {
	def.pattern ?? (def.pattern = ulid);
	$ZodStringFormat.init(inst, def);
});
var $ZodXID = /*@__PURE__*/ $constructor("$ZodXID", (inst, def) => {
	def.pattern ?? (def.pattern = xid);
	$ZodStringFormat.init(inst, def);
});
var $ZodKSUID = /*@__PURE__*/ $constructor("$ZodKSUID", (inst, def) => {
	def.pattern ?? (def.pattern = ksuid);
	$ZodStringFormat.init(inst, def);
});
var $ZodISODateTime = /*@__PURE__*/ $constructor("$ZodISODateTime", (inst, def) => {
	def.pattern ?? (def.pattern = datetime$1(def));
	$ZodStringFormat.init(inst, def);
});
var $ZodISODate = /*@__PURE__*/ $constructor("$ZodISODate", (inst, def) => {
	def.pattern ?? (def.pattern = date$1);
	$ZodStringFormat.init(inst, def);
});
var $ZodISOTime = /*@__PURE__*/ $constructor("$ZodISOTime", (inst, def) => {
	def.pattern ?? (def.pattern = time$1(def));
	$ZodStringFormat.init(inst, def);
});
var $ZodISODuration = /*@__PURE__*/ $constructor("$ZodISODuration", (inst, def) => {
	def.pattern ?? (def.pattern = duration$1);
	$ZodStringFormat.init(inst, def);
});
var $ZodIPv4 = /*@__PURE__*/ $constructor("$ZodIPv4", (inst, def) => {
	def.pattern ?? (def.pattern = ipv4);
	$ZodStringFormat.init(inst, def);
	inst._zod.bag.format = `ipv4`;
});
var $ZodIPv6 = /*@__PURE__*/ $constructor("$ZodIPv6", (inst, def) => {
	def.pattern ?? (def.pattern = ipv6);
	$ZodStringFormat.init(inst, def);
	inst._zod.bag.format = `ipv6`;
	inst._zod.check = (payload) => {
		try {
			new URL(`http://[${payload.value}]`);
		} catch {
			payload.issues.push({
				code: "invalid_format",
				format: "ipv6",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		}
	};
});
var $ZodCIDRv4 = /*@__PURE__*/ $constructor("$ZodCIDRv4", (inst, def) => {
	def.pattern ?? (def.pattern = cidrv4);
	$ZodStringFormat.init(inst, def);
});
var $ZodCIDRv6 = /*@__PURE__*/ $constructor("$ZodCIDRv6", (inst, def) => {
	def.pattern ?? (def.pattern = cidrv6);
	$ZodStringFormat.init(inst, def);
	inst._zod.check = (payload) => {
		const parts = payload.value.split("/");
		try {
			if (parts.length !== 2) throw new Error();
			const [address, prefix] = parts;
			if (!prefix) throw new Error();
			const prefixNum = Number(prefix);
			if (`${prefixNum}` !== prefix) throw new Error();
			if (prefixNum < 0 || prefixNum > 128) throw new Error();
			new URL(`http://[${address}]`);
		} catch {
			payload.issues.push({
				code: "invalid_format",
				format: "cidrv6",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		}
	};
});
function isValidBase64(data) {
	if (data === "") return true;
	if (/\s/.test(data)) return false;
	if (data.length % 4 !== 0) return false;
	try {
		atob(data);
		return true;
	} catch {
		return false;
	}
}
var $ZodBase64 = /*@__PURE__*/ $constructor("$ZodBase64", (inst, def) => {
	def.pattern ?? (def.pattern = base64);
	$ZodStringFormat.init(inst, def);
	inst._zod.bag.contentEncoding = "base64";
	inst._zod.check = (payload) => {
		if (isValidBase64(payload.value)) return;
		payload.issues.push({
			code: "invalid_format",
			format: "base64",
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
function isValidBase64URL(data) {
	if (!base64url.test(data)) return false;
	const base64 = data.replace(/[-_]/g, (c) => c === "-" ? "+" : "/");
	return isValidBase64(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
}
var $ZodBase64URL = /*@__PURE__*/ $constructor("$ZodBase64URL", (inst, def) => {
	def.pattern ?? (def.pattern = base64url);
	$ZodStringFormat.init(inst, def);
	inst._zod.bag.contentEncoding = "base64url";
	inst._zod.check = (payload) => {
		if (isValidBase64URL(payload.value)) return;
		payload.issues.push({
			code: "invalid_format",
			format: "base64url",
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodE164 = /*@__PURE__*/ $constructor("$ZodE164", (inst, def) => {
	def.pattern ?? (def.pattern = e164);
	$ZodStringFormat.init(inst, def);
});
function isValidJWT(token, algorithm = null) {
	try {
		const tokensParts = token.split(".");
		if (tokensParts.length !== 3) return false;
		const [header] = tokensParts;
		if (!header) return false;
		const parsedHeader = JSON.parse(atob(header));
		if ("typ" in parsedHeader && parsedHeader?.typ !== "JWT") return false;
		if (!parsedHeader.alg) return false;
		if (algorithm && (!("alg" in parsedHeader) || parsedHeader.alg !== algorithm)) return false;
		return true;
	} catch {
		return false;
	}
}
var $ZodJWT = /*@__PURE__*/ $constructor("$ZodJWT", (inst, def) => {
	$ZodStringFormat.init(inst, def);
	inst._zod.check = (payload) => {
		if (isValidJWT(payload.value, def.alg)) return;
		payload.issues.push({
			code: "invalid_format",
			format: "jwt",
			input: payload.value,
			inst,
			continue: !def.abort
		});
	};
});
var $ZodNumber = /*@__PURE__*/ $constructor("$ZodNumber", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.pattern = inst._zod.bag.pattern ?? number$1;
	inst._zod.parse = (payload, _ctx) => {
		if (def.coerce) try {
			payload.value = Number(payload.value);
		} catch (_) {}
		const input = payload.value;
		if (typeof input === "number" && !Number.isNaN(input) && Number.isFinite(input)) return payload;
		const received = typeof input === "number" ? Number.isNaN(input) ? "NaN" : !Number.isFinite(input) ? "Infinity" : void 0 : void 0;
		payload.issues.push({
			expected: "number",
			code: "invalid_type",
			input,
			inst,
			...received ? { received } : {}
		});
		return payload;
	};
});
var $ZodNumberFormat = /*@__PURE__*/ $constructor("$ZodNumberFormat", (inst, def) => {
	$ZodCheckNumberFormat.init(inst, def);
	$ZodNumber.init(inst, def);
});
var $ZodBoolean = /*@__PURE__*/ $constructor("$ZodBoolean", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.pattern = boolean$1;
	inst._zod.parse = (payload, _ctx) => {
		if (def.coerce) try {
			payload.value = Boolean(payload.value);
		} catch (_) {}
		const input = payload.value;
		if (typeof input === "boolean") return payload;
		payload.issues.push({
			expected: "boolean",
			code: "invalid_type",
			input,
			inst
		});
		return payload;
	};
});
var $ZodNull = /*@__PURE__*/ $constructor("$ZodNull", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.pattern = _null$2;
	inst._zod.values = /* @__PURE__ */ new Set([null]);
	inst._zod.parse = (payload, _ctx) => {
		const input = payload.value;
		if (input === null) return payload;
		payload.issues.push({
			expected: "null",
			code: "invalid_type",
			input,
			inst
		});
		return payload;
	};
});
var $ZodAny = /*@__PURE__*/ $constructor("$ZodAny", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload) => payload;
});
var $ZodUnknown = /*@__PURE__*/ $constructor("$ZodUnknown", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload) => payload;
});
var $ZodNever = /*@__PURE__*/ $constructor("$ZodNever", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload, _ctx) => {
		payload.issues.push({
			expected: "never",
			code: "invalid_type",
			input: payload.value,
			inst
		});
		return payload;
	};
});
function handleArrayResult(result, final, index) {
	if (result.issues.length) final.issues.push(...prefixIssues(index, result.issues));
	final.value[index] = result.value;
}
var $ZodArray = /*@__PURE__*/ $constructor("$ZodArray", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload, ctx) => {
		const input = payload.value;
		if (!Array.isArray(input)) {
			payload.issues.push({
				expected: "array",
				code: "invalid_type",
				input,
				inst
			});
			return payload;
		}
		payload.value = Array(input.length);
		const proms = [];
		for (let i = 0; i < input.length; i++) {
			const item = input[i];
			const result = def.element._zod.run({
				value: item,
				issues: []
			}, ctx);
			if (result instanceof Promise) proms.push(result.then((result) => handleArrayResult(result, payload, i)));
			else handleArrayResult(result, payload, i);
		}
		if (proms.length) return Promise.all(proms).then(() => payload);
		return payload;
	};
});
function handlePropertyResult(result, final, key, input, isOptionalIn, isOptionalOut) {
	const isPresent = key in input;
	if (result.issues.length) {
		if (isOptionalIn && isOptionalOut && !isPresent) return;
		final.issues.push(...prefixIssues(key, result.issues));
	}
	if (!isPresent && !isOptionalIn) {
		if (!result.issues.length) final.issues.push({
			code: "invalid_type",
			expected: "nonoptional",
			input: void 0,
			path: [key]
		});
		return;
	}
	if (result.value === void 0) {
		if (isPresent) final.value[key] = void 0;
	} else final.value[key] = result.value;
}
function normalizeDef(def) {
	const keys = Object.keys(def.shape);
	for (const k of keys) if (!def.shape?.[k]?._zod?.traits?.has("$ZodType")) throw new Error(`Invalid element at key "${k}": expected a Zod schema`);
	const okeys = optionalKeys(def.shape);
	return {
		...def,
		keys,
		keySet: new Set(keys),
		numKeys: keys.length,
		optionalKeys: new Set(okeys)
	};
}
function handleCatchall(proms, input, payload, ctx, def, inst) {
	const unrecognized = [];
	const keySet = def.keySet;
	const _catchall = def.catchall._zod;
	const t = _catchall.def.type;
	const isOptionalIn = _catchall.optin === "optional";
	const isOptionalOut = _catchall.optout === "optional";
	for (const key in input) {
		if (key === "__proto__") continue;
		if (keySet.has(key)) continue;
		if (t === "never") {
			unrecognized.push(key);
			continue;
		}
		const r = _catchall.run({
			value: input[key],
			issues: []
		}, ctx);
		if (r instanceof Promise) proms.push(r.then((r) => handlePropertyResult(r, payload, key, input, isOptionalIn, isOptionalOut)));
		else handlePropertyResult(r, payload, key, input, isOptionalIn, isOptionalOut);
	}
	if (unrecognized.length) payload.issues.push({
		code: "unrecognized_keys",
		keys: unrecognized,
		input,
		inst
	});
	if (!proms.length) return payload;
	return Promise.all(proms).then(() => {
		return payload;
	});
}
var $ZodObject = /*@__PURE__*/ $constructor("$ZodObject", (inst, def) => {
	$ZodType.init(inst, def);
	if (!Object.getOwnPropertyDescriptor(def, "shape")?.get) {
		const sh = def.shape;
		Object.defineProperty(def, "shape", { get: () => {
			const newSh = { ...sh };
			Object.defineProperty(def, "shape", { value: newSh });
			return newSh;
		} });
	}
	const _normalized = cached(() => normalizeDef(def));
	defineLazy(inst._zod, "propValues", () => {
		const shape = def.shape;
		const propValues = {};
		for (const key in shape) {
			const field = shape[key]._zod;
			if (field.values) {
				propValues[key] ?? (propValues[key] = /* @__PURE__ */ new Set());
				for (const v of field.values) propValues[key].add(v);
			}
		}
		return propValues;
	});
	const isObject$3 = isObject;
	const catchall = def.catchall;
	let value;
	inst._zod.parse = (payload, ctx) => {
		value ?? (value = _normalized.value);
		const input = payload.value;
		if (!isObject$3(input)) {
			payload.issues.push({
				expected: "object",
				code: "invalid_type",
				input,
				inst
			});
			return payload;
		}
		payload.value = {};
		const proms = [];
		const shape = value.shape;
		for (const key of value.keys) {
			const el = shape[key];
			const isOptionalIn = el._zod.optin === "optional";
			const isOptionalOut = el._zod.optout === "optional";
			const r = el._zod.run({
				value: input[key],
				issues: []
			}, ctx);
			if (r instanceof Promise) proms.push(r.then((r) => handlePropertyResult(r, payload, key, input, isOptionalIn, isOptionalOut)));
			else handlePropertyResult(r, payload, key, input, isOptionalIn, isOptionalOut);
		}
		if (!catchall) return proms.length ? Promise.all(proms).then(() => payload) : payload;
		return handleCatchall(proms, input, payload, ctx, _normalized.value, inst);
	};
});
var $ZodObjectJIT = /*@__PURE__*/ $constructor("$ZodObjectJIT", (inst, def) => {
	$ZodObject.init(inst, def);
	const superParse = inst._zod.parse;
	const _normalized = cached(() => normalizeDef(def));
	const generateFastpass = (shape) => {
		const doc = new Doc([
			"shape",
			"payload",
			"ctx"
		]);
		const normalized = _normalized.value;
		const parseStr = (key) => {
			const k = esc(key);
			return `shape[${k}]._zod.run({ value: input[${k}], issues: [] }, ctx)`;
		};
		doc.write(`const input = payload.value;`);
		const ids = Object.create(null);
		let counter = 0;
		for (const key of normalized.keys) ids[key] = `key_${counter++}`;
		doc.write(`const newResult = {};`);
		for (const key of normalized.keys) {
			const id = ids[key];
			const k = esc(key);
			const schema = shape[key];
			const isOptionalIn = schema?._zod?.optin === "optional";
			const isOptionalOut = schema?._zod?.optout === "optional";
			doc.write(`const ${id} = ${parseStr(key)};`);
			if (isOptionalIn && isOptionalOut) doc.write(`
        if (${id}.issues.length) {
          if (${k} in input) {
            payload.issues = payload.issues.concat(${id}.issues.map(iss => ({
              ...iss,
              path: iss.path ? [${k}, ...iss.path] : [${k}]
            })));
          }
        }
        
        if (${id}.value === undefined) {
          if (${k} in input) {
            newResult[${k}] = undefined;
          }
        } else {
          newResult[${k}] = ${id}.value;
        }
        
      `);
			else if (!isOptionalIn) doc.write(`
        const ${id}_present = ${k} in input;
        if (${id}.issues.length) {
          payload.issues = payload.issues.concat(${id}.issues.map(iss => ({
            ...iss,
            path: iss.path ? [${k}, ...iss.path] : [${k}]
          })));
        }
        if (!${id}_present && !${id}.issues.length) {
          payload.issues.push({
            code: "invalid_type",
            expected: "nonoptional",
            input: undefined,
            path: [${k}]
          });
        }

        if (${id}_present) {
          if (${id}.value === undefined) {
            newResult[${k}] = undefined;
          } else {
            newResult[${k}] = ${id}.value;
          }
        }

      `);
			else doc.write(`
        if (${id}.issues.length) {
          payload.issues = payload.issues.concat(${id}.issues.map(iss => ({
            ...iss,
            path: iss.path ? [${k}, ...iss.path] : [${k}]
          })));
        }
        
        if (${id}.value === undefined) {
          if (${k} in input) {
            newResult[${k}] = undefined;
          }
        } else {
          newResult[${k}] = ${id}.value;
        }
        
      `);
		}
		doc.write(`payload.value = newResult;`);
		doc.write(`return payload;`);
		const fn = doc.compile();
		return (payload, ctx) => fn(shape, payload, ctx);
	};
	let fastpass;
	const isObject$4 = isObject;
	const jit = !globalConfig.jitless;
	const fastEnabled = jit && allowsEval.value;
	const catchall = def.catchall;
	let value;
	inst._zod.parse = (payload, ctx) => {
		value ?? (value = _normalized.value);
		const input = payload.value;
		if (!isObject$4(input)) {
			payload.issues.push({
				expected: "object",
				code: "invalid_type",
				input,
				inst
			});
			return payload;
		}
		if (jit && fastEnabled && ctx?.async === false && ctx.jitless !== true) {
			if (!fastpass) fastpass = generateFastpass(def.shape);
			payload = fastpass(payload, ctx);
			if (!catchall) return payload;
			return handleCatchall([], input, payload, ctx, value, inst);
		}
		return superParse(payload, ctx);
	};
});
function handleUnionResults(results, final, inst, ctx) {
	for (const result of results) if (result.issues.length === 0) {
		final.value = result.value;
		return final;
	}
	const nonaborted = results.filter((r) => !aborted(r));
	if (nonaborted.length === 1) {
		final.value = nonaborted[0].value;
		return nonaborted[0];
	}
	final.issues.push({
		code: "invalid_union",
		input: final.value,
		inst,
		errors: results.map((result) => result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
	});
	return final;
}
var $ZodUnion = /*@__PURE__*/ $constructor("$ZodUnion", (inst, def) => {
	$ZodType.init(inst, def);
	defineLazy(inst._zod, "optin", () => def.options.some((o) => o._zod.optin === "optional") ? "optional" : void 0);
	defineLazy(inst._zod, "optout", () => def.options.some((o) => o._zod.optout === "optional") ? "optional" : void 0);
	defineLazy(inst._zod, "values", () => {
		if (def.options.every((o) => o._zod.values)) return new Set(def.options.flatMap((option) => Array.from(option._zod.values)));
	});
	defineLazy(inst._zod, "pattern", () => {
		if (def.options.every((o) => o._zod.pattern)) {
			const patterns = def.options.map((o) => o._zod.pattern);
			return new RegExp(`^(${patterns.map((p) => cleanRegex(p.source)).join("|")})$`);
		}
	});
	const first = def.options.length === 1 ? def.options[0]._zod.run : null;
	inst._zod.parse = (payload, ctx) => {
		if (first) return first(payload, ctx);
		let async = false;
		const results = [];
		for (const option of def.options) {
			const result = option._zod.run({
				value: payload.value,
				issues: []
			}, ctx);
			if (result instanceof Promise) {
				results.push(result);
				async = true;
			} else {
				if (result.issues.length === 0) return result;
				results.push(result);
			}
		}
		if (!async) return handleUnionResults(results, payload, inst, ctx);
		return Promise.all(results).then((results) => {
			return handleUnionResults(results, payload, inst, ctx);
		});
	};
});
var $ZodDiscriminatedUnion = /*@__PURE__*/ $constructor("$ZodDiscriminatedUnion", (inst, def) => {
	def.inclusive = false;
	$ZodUnion.init(inst, def);
	const _super = inst._zod.parse;
	defineLazy(inst._zod, "propValues", () => {
		const propValues = {};
		for (const option of def.options) {
			const pv = option._zod.propValues;
			if (!pv || Object.keys(pv).length === 0) throw new Error(`Invalid discriminated union option at index "${def.options.indexOf(option)}"`);
			for (const [k, v] of Object.entries(pv)) {
				if (!propValues[k]) propValues[k] = /* @__PURE__ */ new Set();
				for (const val of v) propValues[k].add(val);
			}
		}
		return propValues;
	});
	const disc = cached(() => {
		const opts = def.options;
		const map = /* @__PURE__ */ new Map();
		for (const o of opts) {
			const values = o._zod.propValues?.[def.discriminator];
			if (!values || values.size === 0) throw new Error(`Invalid discriminated union option at index "${def.options.indexOf(o)}"`);
			for (const v of values) {
				if (map.has(v)) throw new Error(`Duplicate discriminator value "${String(v)}"`);
				map.set(v, o);
			}
		}
		return map;
	});
	inst._zod.parse = (payload, ctx) => {
		const input = payload.value;
		if (!isObject(input)) {
			payload.issues.push({
				code: "invalid_type",
				expected: "object",
				input,
				inst
			});
			return payload;
		}
		const opt = disc.value.get(input?.[def.discriminator]);
		if (opt) return opt._zod.run(payload, ctx);
		if (def.unionFallback || ctx.direction === "backward") return _super(payload, ctx);
		payload.issues.push({
			code: "invalid_union",
			errors: [],
			note: "No matching discriminator",
			discriminator: def.discriminator,
			options: Array.from(disc.value.keys()),
			input,
			path: [def.discriminator],
			inst
		});
		return payload;
	};
});
var $ZodIntersection = /*@__PURE__*/ $constructor("$ZodIntersection", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload, ctx) => {
		const input = payload.value;
		const left = def.left._zod.run({
			value: input,
			issues: []
		}, ctx);
		const right = def.right._zod.run({
			value: input,
			issues: []
		}, ctx);
		if (left instanceof Promise || right instanceof Promise) return Promise.all([left, right]).then(([left, right]) => {
			return handleIntersectionResults(payload, left, right);
		});
		return handleIntersectionResults(payload, left, right);
	};
});
function mergeValues(a, b) {
	if (a === b) return {
		valid: true,
		data: a
	};
	if (a instanceof Date && b instanceof Date && +a === +b) return {
		valid: true,
		data: a
	};
	if (isPlainObject(a) && isPlainObject(b)) {
		const bKeys = Object.keys(b);
		const sharedKeys = Object.keys(a).filter((key) => bKeys.indexOf(key) !== -1);
		const newObj = {
			...a,
			...b
		};
		for (const key of sharedKeys) {
			const sharedValue = mergeValues(a[key], b[key]);
			if (!sharedValue.valid) return {
				valid: false,
				mergeErrorPath: [key, ...sharedValue.mergeErrorPath]
			};
			newObj[key] = sharedValue.data;
		}
		return {
			valid: true,
			data: newObj
		};
	}
	if (Array.isArray(a) && Array.isArray(b)) {
		if (a.length !== b.length) return {
			valid: false,
			mergeErrorPath: []
		};
		const newArray = [];
		for (let index = 0; index < a.length; index++) {
			const itemA = a[index];
			const itemB = b[index];
			const sharedValue = mergeValues(itemA, itemB);
			if (!sharedValue.valid) return {
				valid: false,
				mergeErrorPath: [index, ...sharedValue.mergeErrorPath]
			};
			newArray.push(sharedValue.data);
		}
		return {
			valid: true,
			data: newArray
		};
	}
	return {
		valid: false,
		mergeErrorPath: []
	};
}
function handleIntersectionResults(result, left, right) {
	const unrecKeys = /* @__PURE__ */ new Map();
	let unrecIssue;
	for (const iss of left.issues) if (iss.code === "unrecognized_keys") {
		unrecIssue ?? (unrecIssue = iss);
		for (const k of iss.keys) {
			if (!unrecKeys.has(k)) unrecKeys.set(k, {});
			unrecKeys.get(k).l = true;
		}
	} else result.issues.push(iss);
	for (const iss of right.issues) if (iss.code === "unrecognized_keys") for (const k of iss.keys) {
		if (!unrecKeys.has(k)) unrecKeys.set(k, {});
		unrecKeys.get(k).r = true;
	}
	else result.issues.push(iss);
	const bothKeys = [...unrecKeys].filter(([, f]) => f.l && f.r).map(([k]) => k);
	if (bothKeys.length && unrecIssue) result.issues.push({
		...unrecIssue,
		keys: bothKeys
	});
	if (aborted(result)) return result;
	const merged = mergeValues(left.value, right.value);
	if (!merged.valid) throw new Error(`Unmergable intersection. Error path: ${JSON.stringify(merged.mergeErrorPath)}`);
	result.value = merged.data;
	return result;
}
var $ZodRecord = /*@__PURE__*/ $constructor("$ZodRecord", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.parse = (payload, ctx) => {
		const input = payload.value;
		if (!isPlainObject(input)) {
			payload.issues.push({
				expected: "record",
				code: "invalid_type",
				input,
				inst
			});
			return payload;
		}
		const proms = [];
		const values = def.keyType._zod.values;
		if (values) {
			payload.value = {};
			const recordKeys = /* @__PURE__ */ new Set();
			for (const key of values) if (typeof key === "string" || typeof key === "number" || typeof key === "symbol") {
				recordKeys.add(typeof key === "number" ? key.toString() : key);
				const keyResult = def.keyType._zod.run({
					value: key,
					issues: []
				}, ctx);
				if (keyResult instanceof Promise) throw new Error("Async schemas not supported in object keys currently");
				if (keyResult.issues.length) {
					payload.issues.push({
						code: "invalid_key",
						origin: "record",
						issues: keyResult.issues.map((iss) => finalizeIssue(iss, ctx, config())),
						input: key,
						path: [key],
						inst
					});
					continue;
				}
				const outKey = keyResult.value;
				const result = def.valueType._zod.run({
					value: input[key],
					issues: []
				}, ctx);
				if (result instanceof Promise) proms.push(result.then((result) => {
					if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
					payload.value[outKey] = result.value;
				}));
				else {
					if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
					payload.value[outKey] = result.value;
				}
			}
			let unrecognized;
			for (const key in input) if (!recordKeys.has(key)) {
				unrecognized = unrecognized ?? [];
				unrecognized.push(key);
			}
			if (unrecognized && unrecognized.length > 0) payload.issues.push({
				code: "unrecognized_keys",
				input,
				inst,
				keys: unrecognized
			});
		} else {
			payload.value = {};
			for (const key of Reflect.ownKeys(input)) {
				if (key === "__proto__") continue;
				if (!Object.prototype.propertyIsEnumerable.call(input, key)) continue;
				let keyResult = def.keyType._zod.run({
					value: key,
					issues: []
				}, ctx);
				if (keyResult instanceof Promise) throw new Error("Async schemas not supported in object keys currently");
				if (typeof key === "string" && number$1.test(key) && keyResult.issues.length) {
					const retryResult = def.keyType._zod.run({
						value: Number(key),
						issues: []
					}, ctx);
					if (retryResult instanceof Promise) throw new Error("Async schemas not supported in object keys currently");
					if (retryResult.issues.length === 0) keyResult = retryResult;
				}
				if (keyResult.issues.length) {
					if (def.mode === "loose") payload.value[key] = input[key];
					else payload.issues.push({
						code: "invalid_key",
						origin: "record",
						issues: keyResult.issues.map((iss) => finalizeIssue(iss, ctx, config())),
						input: key,
						path: [key],
						inst
					});
					continue;
				}
				const result = def.valueType._zod.run({
					value: input[key],
					issues: []
				}, ctx);
				if (result instanceof Promise) proms.push(result.then((result) => {
					if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
					payload.value[keyResult.value] = result.value;
				}));
				else {
					if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
					payload.value[keyResult.value] = result.value;
				}
			}
		}
		if (proms.length) return Promise.all(proms).then(() => payload);
		return payload;
	};
});
var $ZodEnum = /*@__PURE__*/ $constructor("$ZodEnum", (inst, def) => {
	$ZodType.init(inst, def);
	const values = getEnumValues(def.entries);
	const valuesSet = new Set(values);
	inst._zod.values = valuesSet;
	inst._zod.pattern = new RegExp(`^(${values.filter((k) => propertyKeyTypes.has(typeof k)).map((o) => typeof o === "string" ? escapeRegex(o) : o.toString()).join("|")})$`);
	inst._zod.parse = (payload, _ctx) => {
		const input = payload.value;
		if (valuesSet.has(input)) return payload;
		payload.issues.push({
			code: "invalid_value",
			values,
			input,
			inst
		});
		return payload;
	};
});
var $ZodLiteral = /*@__PURE__*/ $constructor("$ZodLiteral", (inst, def) => {
	$ZodType.init(inst, def);
	if (def.values.length === 0) throw new Error("Cannot create literal schema with no valid values");
	const values = new Set(def.values);
	inst._zod.values = values;
	inst._zod.pattern = new RegExp(`^(${def.values.map((o) => typeof o === "string" ? escapeRegex(o) : o ? escapeRegex(o.toString()) : String(o)).join("|")})$`);
	inst._zod.parse = (payload, _ctx) => {
		const input = payload.value;
		if (values.has(input)) return payload;
		payload.issues.push({
			code: "invalid_value",
			values: def.values,
			input,
			inst
		});
		return payload;
	};
});
var $ZodTransform = /*@__PURE__*/ $constructor("$ZodTransform", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.optin = "optional";
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") throw new $ZodEncodeError(inst.constructor.name);
		const _out = def.transform(payload.value, payload);
		if (ctx.async) return (_out instanceof Promise ? _out : Promise.resolve(_out)).then((output) => {
			payload.value = output;
			payload.fallback = true;
			return payload;
		});
		if (_out instanceof Promise) throw new $ZodAsyncError();
		payload.value = _out;
		payload.fallback = true;
		return payload;
	};
});
function handleOptionalResult(result, input) {
	if (input === void 0 && (result.issues.length || result.fallback)) return {
		issues: [],
		value: void 0
	};
	return result;
}
var $ZodOptional = /*@__PURE__*/ $constructor("$ZodOptional", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.optin = "optional";
	inst._zod.optout = "optional";
	defineLazy(inst._zod, "values", () => {
		return def.innerType._zod.values ? /* @__PURE__ */ new Set([...def.innerType._zod.values, void 0]) : void 0;
	});
	defineLazy(inst._zod, "pattern", () => {
		const pattern = def.innerType._zod.pattern;
		return pattern ? new RegExp(`^(${cleanRegex(pattern.source)})?$`) : void 0;
	});
	inst._zod.parse = (payload, ctx) => {
		if (def.innerType._zod.optin === "optional") {
			const input = payload.value;
			const result = def.innerType._zod.run(payload, ctx);
			if (result instanceof Promise) return result.then((r) => handleOptionalResult(r, input));
			return handleOptionalResult(result, input);
		}
		if (payload.value === void 0) return payload;
		return def.innerType._zod.run(payload, ctx);
	};
});
var $ZodExactOptional = /*@__PURE__*/ $constructor("$ZodExactOptional", (inst, def) => {
	$ZodOptional.init(inst, def);
	defineLazy(inst._zod, "values", () => def.innerType._zod.values);
	defineLazy(inst._zod, "pattern", () => def.innerType._zod.pattern);
	inst._zod.parse = (payload, ctx) => {
		return def.innerType._zod.run(payload, ctx);
	};
});
var $ZodNullable = /*@__PURE__*/ $constructor("$ZodNullable", (inst, def) => {
	$ZodType.init(inst, def);
	defineLazy(inst._zod, "optin", () => def.innerType._zod.optin);
	defineLazy(inst._zod, "optout", () => def.innerType._zod.optout);
	defineLazy(inst._zod, "pattern", () => {
		const pattern = def.innerType._zod.pattern;
		return pattern ? new RegExp(`^(${cleanRegex(pattern.source)}|null)$`) : void 0;
	});
	defineLazy(inst._zod, "values", () => {
		return def.innerType._zod.values ? /* @__PURE__ */ new Set([...def.innerType._zod.values, null]) : void 0;
	});
	inst._zod.parse = (payload, ctx) => {
		if (payload.value === null) return payload;
		return def.innerType._zod.run(payload, ctx);
	};
});
var $ZodDefault = /*@__PURE__*/ $constructor("$ZodDefault", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.optin = "optional";
	defineLazy(inst._zod, "values", () => def.innerType._zod.values);
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") return def.innerType._zod.run(payload, ctx);
		if (payload.value === void 0) {
			payload.value = def.defaultValue;
			/**
			* $ZodDefault returns the default value immediately in forward direction.
			* It doesn't pass the default value into the validator ("prefault"). There's no reason to pass the default value through validation. The validity of the default is enforced by TypeScript statically. Otherwise, it's the responsibility of the user to ensure the default is valid. In the case of pipes with divergent in/out types, you can specify the default on the `in` schema of your ZodPipe to set a "prefault" for the pipe.   */
			return payload;
		}
		const result = def.innerType._zod.run(payload, ctx);
		if (result instanceof Promise) return result.then((result) => handleDefaultResult(result, def));
		return handleDefaultResult(result, def);
	};
});
function handleDefaultResult(payload, def) {
	if (payload.value === void 0) payload.value = def.defaultValue;
	return payload;
}
var $ZodPrefault = /*@__PURE__*/ $constructor("$ZodPrefault", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.optin = "optional";
	defineLazy(inst._zod, "values", () => def.innerType._zod.values);
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") return def.innerType._zod.run(payload, ctx);
		if (payload.value === void 0) payload.value = def.defaultValue;
		return def.innerType._zod.run(payload, ctx);
	};
});
var $ZodNonOptional = /*@__PURE__*/ $constructor("$ZodNonOptional", (inst, def) => {
	$ZodType.init(inst, def);
	defineLazy(inst._zod, "values", () => {
		const v = def.innerType._zod.values;
		return v ? new Set([...v].filter((x) => x !== void 0)) : void 0;
	});
	inst._zod.parse = (payload, ctx) => {
		const result = def.innerType._zod.run(payload, ctx);
		if (result instanceof Promise) return result.then((result) => handleNonOptionalResult(result, inst));
		return handleNonOptionalResult(result, inst);
	};
});
function handleNonOptionalResult(payload, inst) {
	if (!payload.issues.length && payload.value === void 0) payload.issues.push({
		code: "invalid_type",
		expected: "nonoptional",
		input: payload.value,
		inst
	});
	return payload;
}
var $ZodCatch = /*@__PURE__*/ $constructor("$ZodCatch", (inst, def) => {
	$ZodType.init(inst, def);
	inst._zod.optin = "optional";
	defineLazy(inst._zod, "optout", () => def.innerType._zod.optout);
	defineLazy(inst._zod, "values", () => def.innerType._zod.values);
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") return def.innerType._zod.run(payload, ctx);
		const result = def.innerType._zod.run(payload, ctx);
		if (result instanceof Promise) return result.then((result) => {
			payload.value = result.value;
			if (result.issues.length) {
				payload.value = def.catchValue({
					...payload,
					error: { issues: result.issues.map((iss) => finalizeIssue(iss, ctx, config())) },
					input: payload.value
				});
				payload.issues = [];
				payload.fallback = true;
			}
			return payload;
		});
		payload.value = result.value;
		if (result.issues.length) {
			payload.value = def.catchValue({
				...payload,
				error: { issues: result.issues.map((iss) => finalizeIssue(iss, ctx, config())) },
				input: payload.value
			});
			payload.issues = [];
			payload.fallback = true;
		}
		return payload;
	};
});
var $ZodPipe = /*@__PURE__*/ $constructor("$ZodPipe", (inst, def) => {
	$ZodType.init(inst, def);
	defineLazy(inst._zod, "values", () => def.in._zod.values);
	defineLazy(inst._zod, "optin", () => def.in._zod.optin);
	defineLazy(inst._zod, "optout", () => def.out._zod.optout);
	defineLazy(inst._zod, "propValues", () => def.in._zod.propValues);
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") {
			const right = def.out._zod.run(payload, ctx);
			if (right instanceof Promise) return right.then((right) => handlePipeResult(right, def.in, ctx));
			return handlePipeResult(right, def.in, ctx);
		}
		const left = def.in._zod.run(payload, ctx);
		if (left instanceof Promise) return left.then((left) => handlePipeResult(left, def.out, ctx));
		return handlePipeResult(left, def.out, ctx);
	};
});
function handlePipeResult(left, next, ctx) {
	if (left.issues.length) {
		left.aborted = true;
		return left;
	}
	return next._zod.run({
		value: left.value,
		issues: left.issues,
		fallback: left.fallback
	}, ctx);
}
var $ZodReadonly = /*@__PURE__*/ $constructor("$ZodReadonly", (inst, def) => {
	$ZodType.init(inst, def);
	defineLazy(inst._zod, "propValues", () => def.innerType._zod.propValues);
	defineLazy(inst._zod, "values", () => def.innerType._zod.values);
	defineLazy(inst._zod, "optin", () => def.innerType?._zod?.optin);
	defineLazy(inst._zod, "optout", () => def.innerType?._zod?.optout);
	inst._zod.parse = (payload, ctx) => {
		if (ctx.direction === "backward") return def.innerType._zod.run(payload, ctx);
		const result = def.innerType._zod.run(payload, ctx);
		if (result instanceof Promise) return result.then(handleReadonlyResult);
		return handleReadonlyResult(result);
	};
});
function handleReadonlyResult(payload) {
	payload.value = Object.freeze(payload.value);
	return payload;
}
var $ZodCustom = /*@__PURE__*/ $constructor("$ZodCustom", (inst, def) => {
	$ZodCheck.init(inst, def);
	$ZodType.init(inst, def);
	inst._zod.parse = (payload, _) => {
		return payload;
	};
	inst._zod.check = (payload) => {
		const input = payload.value;
		const r = def.fn(input);
		if (r instanceof Promise) return r.then((r) => handleRefineResult(r, payload, input, inst));
		handleRefineResult(r, payload, input, inst);
	};
});
function handleRefineResult(result, payload, input, inst) {
	if (!result) {
		const _iss = {
			code: "custom",
			input,
			inst,
			path: [...inst._zod.def.path ?? []],
			continue: !inst._zod.def.abort
		};
		if (inst._zod.def.params) _iss.params = inst._zod.def.params;
		payload.issues.push(issue(_iss));
	}
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/registries.js
var _a;
var $ZodRegistry = class {
	constructor() {
		this._map = /* @__PURE__ */ new WeakMap();
		this._idmap = /* @__PURE__ */ new Map();
	}
	add(schema, ..._meta) {
		const meta = _meta[0];
		this._map.set(schema, meta);
		if (meta && typeof meta === "object" && "id" in meta) this._idmap.set(meta.id, schema);
		return this;
	}
	clear() {
		this._map = /* @__PURE__ */ new WeakMap();
		this._idmap = /* @__PURE__ */ new Map();
		return this;
	}
	remove(schema) {
		const meta = this._map.get(schema);
		if (meta && typeof meta === "object" && "id" in meta) this._idmap.delete(meta.id);
		this._map.delete(schema);
		return this;
	}
	get(schema) {
		const p = schema._zod.parent;
		if (p) {
			const pm = { ...this.get(p) ?? {} };
			delete pm.id;
			const f = {
				...pm,
				...this._map.get(schema)
			};
			return Object.keys(f).length ? f : void 0;
		}
		return this._map.get(schema);
	}
	has(schema) {
		return this._map.has(schema);
	}
};
function registry() {
	return new $ZodRegistry();
}
(_a = globalThis).__zod_globalRegistry ?? (_a.__zod_globalRegistry = registry());
var globalRegistry = globalThis.__zod_globalRegistry;
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/api.js
// @__NO_SIDE_EFFECTS__
function _string(Class, params) {
	return new Class({
		type: "string",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _coercedString(Class, params) {
	return new Class({
		type: "string",
		coerce: true,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _email(Class, params) {
	return new Class({
		type: "string",
		format: "email",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _guid(Class, params) {
	return new Class({
		type: "string",
		format: "guid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _uuid(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _uuidv4(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v4",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _uuidv6(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v6",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _uuidv7(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v7",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _url(Class, params) {
	return new Class({
		type: "string",
		format: "url",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _emoji(Class, params) {
	return new Class({
		type: "string",
		format: "emoji",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _nanoid(Class, params) {
	return new Class({
		type: "string",
		format: "nanoid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
/**
* @deprecated CUID v1 is deprecated by its authors due to information leakage
* (timestamps embedded in the id). Use {@link _cuid2} instead.
* See https://github.com/paralleldrive/cuid.
*/
// @__NO_SIDE_EFFECTS__
function _cuid(Class, params) {
	return new Class({
		type: "string",
		format: "cuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _cuid2(Class, params) {
	return new Class({
		type: "string",
		format: "cuid2",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _ulid(Class, params) {
	return new Class({
		type: "string",
		format: "ulid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _xid(Class, params) {
	return new Class({
		type: "string",
		format: "xid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _ksuid(Class, params) {
	return new Class({
		type: "string",
		format: "ksuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _ipv4(Class, params) {
	return new Class({
		type: "string",
		format: "ipv4",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _ipv6(Class, params) {
	return new Class({
		type: "string",
		format: "ipv6",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _cidrv4(Class, params) {
	return new Class({
		type: "string",
		format: "cidrv4",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _cidrv6(Class, params) {
	return new Class({
		type: "string",
		format: "cidrv6",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _base64(Class, params) {
	return new Class({
		type: "string",
		format: "base64",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _base64url(Class, params) {
	return new Class({
		type: "string",
		format: "base64url",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _e164(Class, params) {
	return new Class({
		type: "string",
		format: "e164",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _jwt(Class, params) {
	return new Class({
		type: "string",
		format: "jwt",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _isoDateTime(Class, params) {
	return new Class({
		type: "string",
		format: "datetime",
		check: "string_format",
		offset: false,
		local: false,
		precision: null,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _isoDate(Class, params) {
	return new Class({
		type: "string",
		format: "date",
		check: "string_format",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _isoTime(Class, params) {
	return new Class({
		type: "string",
		format: "time",
		check: "string_format",
		precision: null,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _isoDuration(Class, params) {
	return new Class({
		type: "string",
		format: "duration",
		check: "string_format",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _number(Class, params) {
	return new Class({
		type: "number",
		checks: [],
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _int(Class, params) {
	return new Class({
		type: "number",
		check: "number_format",
		abort: false,
		format: "safeint",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _boolean(Class, params) {
	return new Class({
		type: "boolean",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _null$1(Class, params) {
	return new Class({
		type: "null",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _any(Class) {
	return new Class({ type: "any" });
}
// @__NO_SIDE_EFFECTS__
function _unknown(Class) {
	return new Class({ type: "unknown" });
}
// @__NO_SIDE_EFFECTS__
function _never(Class, params) {
	return new Class({
		type: "never",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _lt(value, params) {
	return new $ZodCheckLessThan({
		check: "less_than",
		...normalizeParams(params),
		value,
		inclusive: false
	});
}
// @__NO_SIDE_EFFECTS__
function _lte(value, params) {
	return new $ZodCheckLessThan({
		check: "less_than",
		...normalizeParams(params),
		value,
		inclusive: true
	});
}
// @__NO_SIDE_EFFECTS__
function _gt(value, params) {
	return new $ZodCheckGreaterThan({
		check: "greater_than",
		...normalizeParams(params),
		value,
		inclusive: false
	});
}
// @__NO_SIDE_EFFECTS__
function _gte(value, params) {
	return new $ZodCheckGreaterThan({
		check: "greater_than",
		...normalizeParams(params),
		value,
		inclusive: true
	});
}
// @__NO_SIDE_EFFECTS__
function _multipleOf(value, params) {
	return new $ZodCheckMultipleOf({
		check: "multiple_of",
		...normalizeParams(params),
		value
	});
}
// @__NO_SIDE_EFFECTS__
function _maxLength(maximum, params) {
	return new $ZodCheckMaxLength({
		check: "max_length",
		...normalizeParams(params),
		maximum
	});
}
// @__NO_SIDE_EFFECTS__
function _minLength(minimum, params) {
	return new $ZodCheckMinLength({
		check: "min_length",
		...normalizeParams(params),
		minimum
	});
}
// @__NO_SIDE_EFFECTS__
function _length(length, params) {
	return new $ZodCheckLengthEquals({
		check: "length_equals",
		...normalizeParams(params),
		length
	});
}
// @__NO_SIDE_EFFECTS__
function _regex(pattern, params) {
	return new $ZodCheckRegex({
		check: "string_format",
		format: "regex",
		...normalizeParams(params),
		pattern
	});
}
// @__NO_SIDE_EFFECTS__
function _lowercase(params) {
	return new $ZodCheckLowerCase({
		check: "string_format",
		format: "lowercase",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _uppercase(params) {
	return new $ZodCheckUpperCase({
		check: "string_format",
		format: "uppercase",
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _includes(includes, params) {
	return new $ZodCheckIncludes({
		check: "string_format",
		format: "includes",
		...normalizeParams(params),
		includes
	});
}
// @__NO_SIDE_EFFECTS__
function _startsWith(prefix, params) {
	return new $ZodCheckStartsWith({
		check: "string_format",
		format: "starts_with",
		...normalizeParams(params),
		prefix
	});
}
// @__NO_SIDE_EFFECTS__
function _endsWith(suffix, params) {
	return new $ZodCheckEndsWith({
		check: "string_format",
		format: "ends_with",
		...normalizeParams(params),
		suffix
	});
}
// @__NO_SIDE_EFFECTS__
function _overwrite(tx) {
	return new $ZodCheckOverwrite({
		check: "overwrite",
		tx
	});
}
// @__NO_SIDE_EFFECTS__
function _normalize(form) {
	return /* @__PURE__ */ _overwrite((input) => input.normalize(form));
}
// @__NO_SIDE_EFFECTS__
function _trim() {
	return /* @__PURE__ */ _overwrite((input) => input.trim());
}
// @__NO_SIDE_EFFECTS__
function _toLowerCase() {
	return /* @__PURE__ */ _overwrite((input) => input.toLowerCase());
}
// @__NO_SIDE_EFFECTS__
function _toUpperCase() {
	return /* @__PURE__ */ _overwrite((input) => input.toUpperCase());
}
// @__NO_SIDE_EFFECTS__
function _slugify() {
	return /* @__PURE__ */ _overwrite((input) => slugify(input));
}
// @__NO_SIDE_EFFECTS__
function _array(Class, element, params) {
	return new Class({
		type: "array",
		element,
		...normalizeParams(params)
	});
}
// @__NO_SIDE_EFFECTS__
function _refine(Class, fn, _params) {
	return new Class({
		type: "custom",
		check: "custom",
		fn,
		...normalizeParams(_params)
	});
}
// @__NO_SIDE_EFFECTS__
function _superRefine(fn, params) {
	const ch = /* @__PURE__ */ _check((payload) => {
		payload.addIssue = (issue$2) => {
			if (typeof issue$2 === "string") payload.issues.push(issue(issue$2, payload.value, ch._zod.def));
			else {
				const _issue = issue$2;
				if (_issue.fatal) _issue.continue = false;
				_issue.code ?? (_issue.code = "custom");
				_issue.input ?? (_issue.input = payload.value);
				_issue.inst ?? (_issue.inst = ch);
				_issue.continue ?? (_issue.continue = !ch._zod.def.abort);
				payload.issues.push(issue(_issue));
			}
		};
		return fn(payload.value, payload);
	}, params);
	return ch;
}
// @__NO_SIDE_EFFECTS__
function _check(fn, params) {
	const ch = new $ZodCheck({
		check: "custom",
		...normalizeParams(params)
	});
	ch._zod.check = fn;
	return ch;
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/to-json-schema.js
function initializeContext(params) {
	let target = params?.target ?? "draft-2020-12";
	if (target === "draft-4") target = "draft-04";
	if (target === "draft-7") target = "draft-07";
	return {
		processors: params.processors ?? {},
		metadataRegistry: params?.metadata ?? globalRegistry,
		target,
		unrepresentable: params?.unrepresentable ?? "throw",
		override: params?.override ?? (() => {}),
		io: params?.io ?? "output",
		counter: 0,
		seen: /* @__PURE__ */ new Map(),
		cycles: params?.cycles ?? "ref",
		reused: params?.reused ?? "inline",
		external: params?.external ?? void 0
	};
}
function process(schema, ctx, _params = {
	path: [],
	schemaPath: []
}) {
	var _a;
	const def = schema._zod.def;
	const seen = ctx.seen.get(schema);
	if (seen) {
		seen.count++;
		if (_params.schemaPath.includes(schema)) seen.cycle = _params.path;
		return seen.schema;
	}
	const result = {
		schema: {},
		count: 1,
		cycle: void 0,
		path: _params.path
	};
	ctx.seen.set(schema, result);
	const overrideSchema = schema._zod.toJSONSchema?.();
	if (overrideSchema) result.schema = overrideSchema;
	else {
		const params = {
			..._params,
			schemaPath: [..._params.schemaPath, schema],
			path: _params.path
		};
		if (schema._zod.processJSONSchema) schema._zod.processJSONSchema(ctx, result.schema, params);
		else {
			const _json = result.schema;
			const processor = ctx.processors[def.type];
			if (!processor) throw new Error(`[toJSONSchema]: Non-representable type encountered: ${def.type}`);
			processor(schema, ctx, _json, params);
		}
		const parent = schema._zod.parent;
		if (parent) {
			if (!result.ref) result.ref = parent;
			process(parent, ctx, params);
			ctx.seen.get(parent).isParent = true;
		}
	}
	const meta = ctx.metadataRegistry.get(schema);
	if (meta) Object.assign(result.schema, meta);
	if (ctx.io === "input" && isTransforming(schema)) {
		delete result.schema.examples;
		delete result.schema.default;
	}
	if (ctx.io === "input" && "_prefault" in result.schema) (_a = result.schema).default ?? (_a.default = result.schema._prefault);
	delete result.schema._prefault;
	return ctx.seen.get(schema).schema;
}
function extractDefs(ctx, schema) {
	const root = ctx.seen.get(schema);
	if (!root) throw new Error("Unprocessed schema. This is a bug in Zod.");
	const idToSchema = /* @__PURE__ */ new Map();
	for (const entry of ctx.seen.entries()) {
		const id = ctx.metadataRegistry.get(entry[0])?.id;
		if (id) {
			const existing = idToSchema.get(id);
			if (existing && existing !== entry[0]) throw new Error(`Duplicate schema id "${id}" detected during JSON Schema conversion. Two different schemas cannot share the same id when converted together.`);
			idToSchema.set(id, entry[0]);
		}
	}
	const makeURI = (entry) => {
		const defsSegment = ctx.target === "draft-2020-12" ? "$defs" : "definitions";
		if (ctx.external) {
			const externalId = ctx.external.registry.get(entry[0])?.id;
			const uriGenerator = ctx.external.uri ?? ((id) => id);
			if (externalId) return { ref: uriGenerator(externalId) };
			const id = entry[1].defId ?? entry[1].schema.id ?? `schema${ctx.counter++}`;
			entry[1].defId = id;
			return {
				defId: id,
				ref: `${uriGenerator("__shared")}#/${defsSegment}/${id}`
			};
		}
		if (entry[1] === root) return { ref: "#" };
		const defUriPrefix = `#/${defsSegment}/`;
		const defId = entry[1].schema.id ?? `__schema${ctx.counter++}`;
		return {
			defId,
			ref: defUriPrefix + defId
		};
	};
	const extractToDef = (entry) => {
		if (entry[1].schema.$ref) return;
		const seen = entry[1];
		const { ref, defId } = makeURI(entry);
		seen.def = { ...seen.schema };
		if (defId) seen.defId = defId;
		const schema = seen.schema;
		for (const key in schema) delete schema[key];
		schema.$ref = ref;
	};
	if (ctx.cycles === "throw") for (const entry of ctx.seen.entries()) {
		const seen = entry[1];
		if (seen.cycle) throw new Error(`Cycle detected: #/${seen.cycle?.join("/")}/<root>

Set the \`cycles\` parameter to \`"ref"\` to resolve cyclical schemas with defs.`);
	}
	for (const entry of ctx.seen.entries()) {
		const seen = entry[1];
		if (schema === entry[0]) {
			extractToDef(entry);
			continue;
		}
		if (ctx.external) {
			const ext = ctx.external.registry.get(entry[0])?.id;
			if (schema !== entry[0] && ext) {
				extractToDef(entry);
				continue;
			}
		}
		if (ctx.metadataRegistry.get(entry[0])?.id) {
			extractToDef(entry);
			continue;
		}
		if (seen.cycle) {
			extractToDef(entry);
			continue;
		}
		if (seen.count > 1) {
			if (ctx.reused === "ref") {
				extractToDef(entry);
				continue;
			}
		}
	}
}
function finalize(ctx, schema) {
	const root = ctx.seen.get(schema);
	if (!root) throw new Error("Unprocessed schema. This is a bug in Zod.");
	const flattenRef = (zodSchema) => {
		const seen = ctx.seen.get(zodSchema);
		if (seen.ref === null) return;
		const schema = seen.def ?? seen.schema;
		const _cached = { ...schema };
		const ref = seen.ref;
		seen.ref = null;
		if (ref) {
			flattenRef(ref);
			const refSeen = ctx.seen.get(ref);
			const refSchema = refSeen.schema;
			if (refSchema.$ref && (ctx.target === "draft-07" || ctx.target === "draft-04" || ctx.target === "openapi-3.0")) {
				schema.allOf = schema.allOf ?? [];
				schema.allOf.push(refSchema);
			} else Object.assign(schema, refSchema);
			Object.assign(schema, _cached);
			if (zodSchema._zod.parent === ref) for (const key in schema) {
				if (key === "$ref" || key === "allOf") continue;
				if (!(key in _cached)) delete schema[key];
			}
			if (refSchema.$ref && refSeen.def) for (const key in schema) {
				if (key === "$ref" || key === "allOf") continue;
				if (key in refSeen.def && JSON.stringify(schema[key]) === JSON.stringify(refSeen.def[key])) delete schema[key];
			}
		}
		const parent = zodSchema._zod.parent;
		if (parent && parent !== ref) {
			flattenRef(parent);
			const parentSeen = ctx.seen.get(parent);
			if (parentSeen?.schema.$ref) {
				schema.$ref = parentSeen.schema.$ref;
				if (parentSeen.def) for (const key in schema) {
					if (key === "$ref" || key === "allOf") continue;
					if (key in parentSeen.def && JSON.stringify(schema[key]) === JSON.stringify(parentSeen.def[key])) delete schema[key];
				}
			}
		}
		ctx.override({
			zodSchema,
			jsonSchema: schema,
			path: seen.path ?? []
		});
	};
	for (const entry of [...ctx.seen.entries()].reverse()) flattenRef(entry[0]);
	const result = {};
	if (ctx.target === "draft-2020-12") result.$schema = "https://json-schema.org/draft/2020-12/schema";
	else if (ctx.target === "draft-07") result.$schema = "http://json-schema.org/draft-07/schema#";
	else if (ctx.target === "draft-04") result.$schema = "http://json-schema.org/draft-04/schema#";
	else if (ctx.target === "openapi-3.0") {}
	if (ctx.external?.uri) {
		const id = ctx.external.registry.get(schema)?.id;
		if (!id) throw new Error("Schema is missing an `id` property");
		result.$id = ctx.external.uri(id);
	}
	Object.assign(result, root.def ?? root.schema);
	const rootMetaId = ctx.metadataRegistry.get(schema)?.id;
	if (rootMetaId !== void 0 && result.id === rootMetaId) delete result.id;
	const defs = ctx.external?.defs ?? {};
	for (const entry of ctx.seen.entries()) {
		const seen = entry[1];
		if (seen.def && seen.defId) {
			if (seen.def.id === seen.defId) delete seen.def.id;
			defs[seen.defId] = seen.def;
		}
	}
	if (ctx.external) {} else if (Object.keys(defs).length > 0) {
		if (ctx.target === "draft-2020-12") result.$defs = defs;
		else result.definitions = defs;
	}
	try {
		const finalized = JSON.parse(JSON.stringify(result));
		Object.defineProperty(finalized, "~standard", {
			value: {
				...schema["~standard"],
				jsonSchema: {
					input: createStandardJSONSchemaMethod(schema, "input", ctx.processors),
					output: createStandardJSONSchemaMethod(schema, "output", ctx.processors)
				}
			},
			enumerable: false,
			writable: false
		});
		return finalized;
	} catch (_err) {
		throw new Error("Error converting schema to JSON.");
	}
}
function isTransforming(_schema, _ctx) {
	const ctx = _ctx ?? { seen: /* @__PURE__ */ new Set() };
	if (ctx.seen.has(_schema)) return false;
	ctx.seen.add(_schema);
	const def = _schema._zod.def;
	if (def.type === "transform") return true;
	if (def.type === "array") return isTransforming(def.element, ctx);
	if (def.type === "set") return isTransforming(def.valueType, ctx);
	if (def.type === "lazy") return isTransforming(def.getter(), ctx);
	if (def.type === "promise" || def.type === "optional" || def.type === "nonoptional" || def.type === "nullable" || def.type === "readonly" || def.type === "default" || def.type === "prefault") return isTransforming(def.innerType, ctx);
	if (def.type === "intersection") return isTransforming(def.left, ctx) || isTransforming(def.right, ctx);
	if (def.type === "record" || def.type === "map") return isTransforming(def.keyType, ctx) || isTransforming(def.valueType, ctx);
	if (def.type === "pipe") {
		if (_schema._zod.traits.has("$ZodCodec")) return true;
		return isTransforming(def.in, ctx) || isTransforming(def.out, ctx);
	}
	if (def.type === "object") {
		for (const key in def.shape) if (isTransforming(def.shape[key], ctx)) return true;
		return false;
	}
	if (def.type === "union") {
		for (const option of def.options) if (isTransforming(option, ctx)) return true;
		return false;
	}
	if (def.type === "tuple") {
		for (const item of def.items) if (isTransforming(item, ctx)) return true;
		if (def.rest && isTransforming(def.rest, ctx)) return true;
		return false;
	}
	return false;
}
/**
* Creates a toJSONSchema method for a schema instance.
* This encapsulates the logic of initializing context, processing, extracting defs, and finalizing.
*/
var createToJSONSchemaMethod = (schema, processors = {}) => (params) => {
	const ctx = initializeContext({
		...params,
		processors
	});
	process(schema, ctx);
	extractDefs(ctx, schema);
	return finalize(ctx, schema);
};
var createStandardJSONSchemaMethod = (schema, io, processors = {}) => (params) => {
	const { libraryOptions, target } = params ?? {};
	const ctx = initializeContext({
		...libraryOptions ?? {},
		target,
		io,
		processors
	});
	process(schema, ctx);
	extractDefs(ctx, schema);
	return finalize(ctx, schema);
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/core/json-schema-processors.js
var formatMap = {
	guid: "uuid",
	url: "uri",
	datetime: "date-time",
	json_string: "json-string",
	regex: ""
};
var stringProcessor = (schema, ctx, _json, _params) => {
	const json = _json;
	json.type = "string";
	const { minimum, maximum, format, patterns, contentEncoding } = schema._zod.bag;
	if (typeof minimum === "number") json.minLength = minimum;
	if (typeof maximum === "number") json.maxLength = maximum;
	if (format) {
		json.format = formatMap[format] ?? format;
		if (json.format === "") delete json.format;
		if (format === "time") delete json.format;
	}
	if (contentEncoding) json.contentEncoding = contentEncoding;
	if (patterns && patterns.size > 0) {
		const regexes = [...patterns];
		if (regexes.length === 1) json.pattern = regexes[0].source;
		else if (regexes.length > 1) json.allOf = [...regexes.map((regex) => ({
			...ctx.target === "draft-07" || ctx.target === "draft-04" || ctx.target === "openapi-3.0" ? { type: "string" } : {},
			pattern: regex.source
		}))];
	}
};
var numberProcessor = (schema, ctx, _json, _params) => {
	const json = _json;
	const { minimum, maximum, format, multipleOf, exclusiveMaximum, exclusiveMinimum } = schema._zod.bag;
	if (typeof format === "string" && format.includes("int")) json.type = "integer";
	else json.type = "number";
	const exMin = typeof exclusiveMinimum === "number" && exclusiveMinimum >= (minimum ?? Number.NEGATIVE_INFINITY);
	const exMax = typeof exclusiveMaximum === "number" && exclusiveMaximum <= (maximum ?? Number.POSITIVE_INFINITY);
	const legacy = ctx.target === "draft-04" || ctx.target === "openapi-3.0";
	if (exMin) {
		if (legacy) {
			json.minimum = exclusiveMinimum;
			json.exclusiveMinimum = true;
		} else json.exclusiveMinimum = exclusiveMinimum;
	} else if (typeof minimum === "number") json.minimum = minimum;
	if (exMax) {
		if (legacy) {
			json.maximum = exclusiveMaximum;
			json.exclusiveMaximum = true;
		} else json.exclusiveMaximum = exclusiveMaximum;
	} else if (typeof maximum === "number") json.maximum = maximum;
	if (typeof multipleOf === "number") json.multipleOf = multipleOf;
};
var booleanProcessor = (_schema, _ctx, json, _params) => {
	json.type = "boolean";
};
var nullProcessor = (_schema, ctx, json, _params) => {
	if (ctx.target === "openapi-3.0") {
		json.type = "string";
		json.nullable = true;
		json.enum = [null];
	} else json.type = "null";
};
var neverProcessor = (_schema, _ctx, json, _params) => {
	json.not = {};
};
var enumProcessor = (schema, _ctx, json, _params) => {
	const def = schema._zod.def;
	const values = getEnumValues(def.entries);
	if (values.every((v) => typeof v === "number")) json.type = "number";
	if (values.every((v) => typeof v === "string")) json.type = "string";
	json.enum = values;
};
var literalProcessor = (schema, ctx, json, _params) => {
	const def = schema._zod.def;
	const vals = [];
	for (const val of def.values) if (val === void 0) {
		if (ctx.unrepresentable === "throw") throw new Error("Literal `undefined` cannot be represented in JSON Schema");
	} else if (typeof val === "bigint") {
		if (ctx.unrepresentable === "throw") throw new Error("BigInt literals cannot be represented in JSON Schema");
		else vals.push(Number(val));
	} else vals.push(val);
	if (vals.length === 0) {} else if (vals.length === 1) {
		const val = vals[0];
		json.type = val === null ? "null" : typeof val;
		if (ctx.target === "draft-04" || ctx.target === "openapi-3.0") json.enum = [val];
		else json.const = val;
	} else {
		if (vals.every((v) => typeof v === "number")) json.type = "number";
		if (vals.every((v) => typeof v === "string")) json.type = "string";
		if (vals.every((v) => typeof v === "boolean")) json.type = "boolean";
		if (vals.every((v) => v === null)) json.type = "null";
		json.enum = vals;
	}
};
var customProcessor = (_schema, ctx, _json, _params) => {
	if (ctx.unrepresentable === "throw") throw new Error("Custom types cannot be represented in JSON Schema");
};
var transformProcessor = (_schema, ctx, _json, _params) => {
	if (ctx.unrepresentable === "throw") throw new Error("Transforms cannot be represented in JSON Schema");
};
var arrayProcessor = (schema, ctx, _json, params) => {
	const json = _json;
	const def = schema._zod.def;
	const { minimum, maximum } = schema._zod.bag;
	if (typeof minimum === "number") json.minItems = minimum;
	if (typeof maximum === "number") json.maxItems = maximum;
	json.type = "array";
	json.items = process(def.element, ctx, {
		...params,
		path: [...params.path, "items"]
	});
};
var objectProcessor = (schema, ctx, _json, params) => {
	const json = _json;
	const def = schema._zod.def;
	json.type = "object";
	json.properties = {};
	const shape = def.shape;
	for (const key in shape) json.properties[key] = process(shape[key], ctx, {
		...params,
		path: [
			...params.path,
			"properties",
			key
		]
	});
	const allKeys = new Set(Object.keys(shape));
	const requiredKeys = new Set([...allKeys].filter((key) => {
		const v = def.shape[key]._zod;
		if (ctx.io === "input") return v.optin === void 0;
		else return v.optout === void 0;
	}));
	if (requiredKeys.size > 0) json.required = Array.from(requiredKeys);
	if (def.catchall?._zod.def.type === "never") json.additionalProperties = false;
	else if (!def.catchall) {
		if (ctx.io === "output") json.additionalProperties = false;
	} else if (def.catchall) json.additionalProperties = process(def.catchall, ctx, {
		...params,
		path: [...params.path, "additionalProperties"]
	});
};
var unionProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	const isExclusive = def.inclusive === false;
	const options = def.options.map((x, i) => process(x, ctx, {
		...params,
		path: [
			...params.path,
			isExclusive ? "oneOf" : "anyOf",
			i
		]
	}));
	if (isExclusive) json.oneOf = options;
	else json.anyOf = options;
};
var intersectionProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	const a = process(def.left, ctx, {
		...params,
		path: [
			...params.path,
			"allOf",
			0
		]
	});
	const b = process(def.right, ctx, {
		...params,
		path: [
			...params.path,
			"allOf",
			1
		]
	});
	const isSimpleIntersection = (val) => "allOf" in val && Object.keys(val).length === 1;
	json.allOf = [...isSimpleIntersection(a) ? a.allOf : [a], ...isSimpleIntersection(b) ? b.allOf : [b]];
};
var recordProcessor = (schema, ctx, _json, params) => {
	const json = _json;
	const def = schema._zod.def;
	json.type = "object";
	const keyType = def.keyType;
	const patterns = keyType._zod.bag?.patterns;
	if (def.mode === "loose" && patterns && patterns.size > 0) {
		const valueSchema = process(def.valueType, ctx, {
			...params,
			path: [
				...params.path,
				"patternProperties",
				"*"
			]
		});
		json.patternProperties = {};
		for (const pattern of patterns) json.patternProperties[pattern.source] = valueSchema;
	} else {
		if (ctx.target === "draft-07" || ctx.target === "draft-2020-12") json.propertyNames = process(def.keyType, ctx, {
			...params,
			path: [...params.path, "propertyNames"]
		});
		json.additionalProperties = process(def.valueType, ctx, {
			...params,
			path: [...params.path, "additionalProperties"]
		});
	}
	const keyValues = keyType._zod.values;
	if (keyValues) {
		const validKeyValues = [...keyValues].filter((v) => typeof v === "string" || typeof v === "number");
		if (validKeyValues.length > 0) json.required = validKeyValues;
	}
};
var nullableProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	const inner = process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	if (ctx.target === "openapi-3.0") {
		seen.ref = def.innerType;
		json.nullable = true;
	} else json.anyOf = [inner, { type: "null" }];
};
var nonoptionalProcessor = (schema, ctx, _json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
};
var defaultProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
	json.default = JSON.parse(JSON.stringify(def.defaultValue));
};
var prefaultProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
	if (ctx.io === "input") json._prefault = JSON.parse(JSON.stringify(def.defaultValue));
};
var catchProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
	let catchValue;
	try {
		catchValue = def.catchValue(void 0);
	} catch {
		throw new Error("Dynamic catch values are not supported in JSON Schema");
	}
	json.default = catchValue;
};
var pipeProcessor = (schema, ctx, _json, params) => {
	const def = schema._zod.def;
	const inIsTransform = def.in._zod.traits.has("$ZodTransform");
	const innerType = ctx.io === "input" ? inIsTransform ? def.out : def.in : def.out;
	process(innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = innerType;
};
var readonlyProcessor = (schema, ctx, json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
	json.readOnly = true;
};
var optionalProcessor = (schema, ctx, _json, params) => {
	const def = schema._zod.def;
	process(def.innerType, ctx, params);
	const seen = ctx.seen.get(schema);
	seen.ref = def.innerType;
};
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/classic/iso.js
var ZodISODateTime = /*@__PURE__*/ $constructor("ZodISODateTime", (inst, def) => {
	$ZodISODateTime.init(inst, def);
	ZodStringFormat.init(inst, def);
});
function datetime(params) {
	return /* @__PURE__ */ _isoDateTime(ZodISODateTime, params);
}
var ZodISODate = /*@__PURE__*/ $constructor("ZodISODate", (inst, def) => {
	$ZodISODate.init(inst, def);
	ZodStringFormat.init(inst, def);
});
function date(params) {
	return /* @__PURE__ */ _isoDate(ZodISODate, params);
}
var ZodISOTime = /*@__PURE__*/ $constructor("ZodISOTime", (inst, def) => {
	$ZodISOTime.init(inst, def);
	ZodStringFormat.init(inst, def);
});
function time(params) {
	return /* @__PURE__ */ _isoTime(ZodISOTime, params);
}
var ZodISODuration = /*@__PURE__*/ $constructor("ZodISODuration", (inst, def) => {
	$ZodISODuration.init(inst, def);
	ZodStringFormat.init(inst, def);
});
function duration(params) {
	return /* @__PURE__ */ _isoDuration(ZodISODuration, params);
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/classic/errors.js
var initializer = (inst, issues) => {
	$ZodError.init(inst, issues);
	inst.name = "ZodError";
	Object.defineProperties(inst, {
		format: { value: (mapper) => formatError(inst, mapper) },
		flatten: { value: (mapper) => flattenError(inst, mapper) },
		addIssue: { value: (issue) => {
			inst.issues.push(issue);
			inst.message = JSON.stringify(inst.issues, jsonStringifyReplacer, 2);
		} },
		addIssues: { value: (issues) => {
			inst.issues.push(...issues);
			inst.message = JSON.stringify(inst.issues, jsonStringifyReplacer, 2);
		} },
		isEmpty: { get() {
			return inst.issues.length === 0;
		} }
	});
};
var ZodError = /*@__PURE__*/ $constructor("ZodError", initializer);
var ZodRealError = /*@__PURE__*/ $constructor("ZodError", initializer, { Parent: Error });
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/classic/parse.js
var parse$1 = /* @__PURE__ */ _parse(ZodRealError);
var parseAsync = /* @__PURE__ */ _parseAsync(ZodRealError);
var safeParse = /* @__PURE__ */ _safeParse(ZodRealError);
var safeParseAsync = /* @__PURE__ */ _safeParseAsync(ZodRealError);
var encode = /* @__PURE__ */ _encode(ZodRealError);
var decode = /* @__PURE__ */ _decode(ZodRealError);
var encodeAsync = /* @__PURE__ */ _encodeAsync(ZodRealError);
var decodeAsync = /* @__PURE__ */ _decodeAsync(ZodRealError);
var safeEncode = /* @__PURE__ */ _safeEncode(ZodRealError);
var safeDecode = /* @__PURE__ */ _safeDecode(ZodRealError);
var safeEncodeAsync = /* @__PURE__ */ _safeEncodeAsync(ZodRealError);
var safeDecodeAsync = /* @__PURE__ */ _safeDecodeAsync(ZodRealError);
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/classic/schemas.js
var _installedGroups = /* @__PURE__ */ new WeakMap();
function _installLazyMethods(inst, group, methods) {
	const proto = Object.getPrototypeOf(inst);
	let installed = _installedGroups.get(proto);
	if (!installed) {
		installed = /* @__PURE__ */ new Set();
		_installedGroups.set(proto, installed);
	}
	if (installed.has(group)) return;
	installed.add(group);
	for (const key in methods) {
		const fn = methods[key];
		Object.defineProperty(proto, key, {
			configurable: true,
			enumerable: false,
			get() {
				const bound = fn.bind(this);
				Object.defineProperty(this, key, {
					configurable: true,
					writable: true,
					enumerable: true,
					value: bound
				});
				return bound;
			},
			set(v) {
				Object.defineProperty(this, key, {
					configurable: true,
					writable: true,
					enumerable: true,
					value: v
				});
			}
		});
	}
}
var ZodType = /*@__PURE__*/ $constructor("ZodType", (inst, def) => {
	$ZodType.init(inst, def);
	Object.assign(inst["~standard"], { jsonSchema: {
		input: createStandardJSONSchemaMethod(inst, "input"),
		output: createStandardJSONSchemaMethod(inst, "output")
	} });
	inst.toJSONSchema = createToJSONSchemaMethod(inst, {});
	inst.def = def;
	inst.type = def.type;
	Object.defineProperty(inst, "_def", { value: def });
	inst.parse = (data, params) => parse$1(inst, data, params, { callee: inst.parse });
	inst.safeParse = (data, params) => safeParse(inst, data, params);
	inst.parseAsync = async (data, params) => parseAsync(inst, data, params, { callee: inst.parseAsync });
	inst.safeParseAsync = async (data, params) => safeParseAsync(inst, data, params);
	inst.spa = inst.safeParseAsync;
	inst.encode = (data, params) => encode(inst, data, params);
	inst.decode = (data, params) => decode(inst, data, params);
	inst.encodeAsync = async (data, params) => encodeAsync(inst, data, params);
	inst.decodeAsync = async (data, params) => decodeAsync(inst, data, params);
	inst.safeEncode = (data, params) => safeEncode(inst, data, params);
	inst.safeDecode = (data, params) => safeDecode(inst, data, params);
	inst.safeEncodeAsync = async (data, params) => safeEncodeAsync(inst, data, params);
	inst.safeDecodeAsync = async (data, params) => safeDecodeAsync(inst, data, params);
	_installLazyMethods(inst, "ZodType", {
		check(...chks) {
			const def = this.def;
			return this.clone(mergeDefs(def, { checks: [...def.checks ?? [], ...chks.map((ch) => typeof ch === "function" ? { _zod: {
				check: ch,
				def: { check: "custom" },
				onattach: []
			} } : ch)] }), { parent: true });
		},
		with(...chks) {
			return this.check(...chks);
		},
		clone(def, params) {
			return clone(this, def, params);
		},
		brand() {
			return this;
		},
		register(reg, meta) {
			reg.add(this, meta);
			return this;
		},
		refine(check, params) {
			return this.check(refine(check, params));
		},
		superRefine(refinement, params) {
			return this.check(superRefine(refinement, params));
		},
		overwrite(fn) {
			return this.check(/* @__PURE__ */ _overwrite(fn));
		},
		optional() {
			return optional$1(this);
		},
		exactOptional() {
			return exactOptional(this);
		},
		nullable() {
			return nullable(this);
		},
		nullish() {
			return optional$1(nullable(this));
		},
		nonoptional(params) {
			return nonoptional(this, params);
		},
		array() {
			return array(this);
		},
		or(arg) {
			return union([this, arg]);
		},
		and(arg) {
			return intersection(this, arg);
		},
		transform(tx) {
			return pipe(this, transform(tx));
		},
		default(d) {
			return _default(this, d);
		},
		prefault(d) {
			return prefault(this, d);
		},
		catch(params) {
			return _catch(this, params);
		},
		pipe(target) {
			return pipe(this, target);
		},
		readonly() {
			return readonly$1(this);
		},
		describe(description) {
			const cl = this.clone();
			globalRegistry.add(cl, { description });
			return cl;
		},
		meta(...args) {
			if (args.length === 0) return globalRegistry.get(this);
			const cl = this.clone();
			globalRegistry.add(cl, args[0]);
			return cl;
		},
		isOptional() {
			return this.safeParse(void 0).success;
		},
		isNullable() {
			return this.safeParse(null).success;
		},
		apply(fn) {
			return fn(this);
		}
	});
	Object.defineProperty(inst, "description", {
		get() {
			return globalRegistry.get(inst)?.description;
		},
		configurable: true
	});
	return inst;
});
/** @internal */
var _ZodString = /*@__PURE__*/ $constructor("_ZodString", (inst, def) => {
	$ZodString.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => stringProcessor(inst, ctx, json, params);
	const bag = inst._zod.bag;
	inst.format = bag.format ?? null;
	inst.minLength = bag.minimum ?? null;
	inst.maxLength = bag.maximum ?? null;
	_installLazyMethods(inst, "_ZodString", {
		regex(...args) {
			return this.check(/* @__PURE__ */ _regex(...args));
		},
		includes(...args) {
			return this.check(/* @__PURE__ */ _includes(...args));
		},
		startsWith(...args) {
			return this.check(/* @__PURE__ */ _startsWith(...args));
		},
		endsWith(...args) {
			return this.check(/* @__PURE__ */ _endsWith(...args));
		},
		min(...args) {
			return this.check(/* @__PURE__ */ _minLength(...args));
		},
		max(...args) {
			return this.check(/* @__PURE__ */ _maxLength(...args));
		},
		length(...args) {
			return this.check(/* @__PURE__ */ _length(...args));
		},
		nonempty(...args) {
			return this.check(/* @__PURE__ */ _minLength(1, ...args));
		},
		lowercase(params) {
			return this.check(/* @__PURE__ */ _lowercase(params));
		},
		uppercase(params) {
			return this.check(/* @__PURE__ */ _uppercase(params));
		},
		trim() {
			return this.check(/* @__PURE__ */ _trim());
		},
		normalize(...args) {
			return this.check(/* @__PURE__ */ _normalize(...args));
		},
		toLowerCase() {
			return this.check(/* @__PURE__ */ _toLowerCase());
		},
		toUpperCase() {
			return this.check(/* @__PURE__ */ _toUpperCase());
		},
		slugify() {
			return this.check(/* @__PURE__ */ _slugify());
		}
	});
});
var ZodString = /*@__PURE__*/ $constructor("ZodString", (inst, def) => {
	$ZodString.init(inst, def);
	_ZodString.init(inst, def);
	inst.email = (params) => inst.check(/* @__PURE__ */ _email(ZodEmail, params));
	inst.url = (params) => inst.check(/* @__PURE__ */ _url(ZodURL, params));
	inst.jwt = (params) => inst.check(/* @__PURE__ */ _jwt(ZodJWT, params));
	inst.emoji = (params) => inst.check(/* @__PURE__ */ _emoji(ZodEmoji, params));
	inst.guid = (params) => inst.check(/* @__PURE__ */ _guid(ZodGUID, params));
	inst.uuid = (params) => inst.check(/* @__PURE__ */ _uuid(ZodUUID, params));
	inst.uuidv4 = (params) => inst.check(/* @__PURE__ */ _uuidv4(ZodUUID, params));
	inst.uuidv6 = (params) => inst.check(/* @__PURE__ */ _uuidv6(ZodUUID, params));
	inst.uuidv7 = (params) => inst.check(/* @__PURE__ */ _uuidv7(ZodUUID, params));
	inst.nanoid = (params) => inst.check(/* @__PURE__ */ _nanoid(ZodNanoID, params));
	inst.guid = (params) => inst.check(/* @__PURE__ */ _guid(ZodGUID, params));
	inst.cuid = (params) => inst.check(/* @__PURE__ */ _cuid(ZodCUID, params));
	inst.cuid2 = (params) => inst.check(/* @__PURE__ */ _cuid2(ZodCUID2, params));
	inst.ulid = (params) => inst.check(/* @__PURE__ */ _ulid(ZodULID, params));
	inst.base64 = (params) => inst.check(/* @__PURE__ */ _base64(ZodBase64, params));
	inst.base64url = (params) => inst.check(/* @__PURE__ */ _base64url(ZodBase64URL, params));
	inst.xid = (params) => inst.check(/* @__PURE__ */ _xid(ZodXID, params));
	inst.ksuid = (params) => inst.check(/* @__PURE__ */ _ksuid(ZodKSUID, params));
	inst.ipv4 = (params) => inst.check(/* @__PURE__ */ _ipv4(ZodIPv4, params));
	inst.ipv6 = (params) => inst.check(/* @__PURE__ */ _ipv6(ZodIPv6, params));
	inst.cidrv4 = (params) => inst.check(/* @__PURE__ */ _cidrv4(ZodCIDRv4, params));
	inst.cidrv6 = (params) => inst.check(/* @__PURE__ */ _cidrv6(ZodCIDRv6, params));
	inst.e164 = (params) => inst.check(/* @__PURE__ */ _e164(ZodE164, params));
	inst.datetime = (params) => inst.check(datetime(params));
	inst.date = (params) => inst.check(date(params));
	inst.time = (params) => inst.check(time(params));
	inst.duration = (params) => inst.check(duration(params));
});
function string$2(params) {
	return /* @__PURE__ */ _string(ZodString, params);
}
var ZodStringFormat = /*@__PURE__*/ $constructor("ZodStringFormat", (inst, def) => {
	$ZodStringFormat.init(inst, def);
	_ZodString.init(inst, def);
});
var ZodEmail = /*@__PURE__*/ $constructor("ZodEmail", (inst, def) => {
	$ZodEmail.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodGUID = /*@__PURE__*/ $constructor("ZodGUID", (inst, def) => {
	$ZodGUID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodUUID = /*@__PURE__*/ $constructor("ZodUUID", (inst, def) => {
	$ZodUUID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodURL = /*@__PURE__*/ $constructor("ZodURL", (inst, def) => {
	$ZodURL.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodEmoji = /*@__PURE__*/ $constructor("ZodEmoji", (inst, def) => {
	$ZodEmoji.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodNanoID = /*@__PURE__*/ $constructor("ZodNanoID", (inst, def) => {
	$ZodNanoID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
/**
* @deprecated CUID v1 is deprecated by its authors due to information leakage
* (timestamps embedded in the id). Use {@link ZodCUID2} instead.
* See https://github.com/paralleldrive/cuid.
*/
var ZodCUID = /*@__PURE__*/ $constructor("ZodCUID", (inst, def) => {
	$ZodCUID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodCUID2 = /*@__PURE__*/ $constructor("ZodCUID2", (inst, def) => {
	$ZodCUID2.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodULID = /*@__PURE__*/ $constructor("ZodULID", (inst, def) => {
	$ZodULID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodXID = /*@__PURE__*/ $constructor("ZodXID", (inst, def) => {
	$ZodXID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodKSUID = /*@__PURE__*/ $constructor("ZodKSUID", (inst, def) => {
	$ZodKSUID.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodIPv4 = /*@__PURE__*/ $constructor("ZodIPv4", (inst, def) => {
	$ZodIPv4.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodIPv6 = /*@__PURE__*/ $constructor("ZodIPv6", (inst, def) => {
	$ZodIPv6.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodCIDRv4 = /*@__PURE__*/ $constructor("ZodCIDRv4", (inst, def) => {
	$ZodCIDRv4.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodCIDRv6 = /*@__PURE__*/ $constructor("ZodCIDRv6", (inst, def) => {
	$ZodCIDRv6.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodBase64 = /*@__PURE__*/ $constructor("ZodBase64", (inst, def) => {
	$ZodBase64.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodBase64URL = /*@__PURE__*/ $constructor("ZodBase64URL", (inst, def) => {
	$ZodBase64URL.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodE164 = /*@__PURE__*/ $constructor("ZodE164", (inst, def) => {
	$ZodE164.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodJWT = /*@__PURE__*/ $constructor("ZodJWT", (inst, def) => {
	$ZodJWT.init(inst, def);
	ZodStringFormat.init(inst, def);
});
var ZodNumber = /*@__PURE__*/ $constructor("ZodNumber", (inst, def) => {
	$ZodNumber.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => numberProcessor(inst, ctx, json, params);
	_installLazyMethods(inst, "ZodNumber", {
		gt(value, params) {
			return this.check(/* @__PURE__ */ _gt(value, params));
		},
		gte(value, params) {
			return this.check(/* @__PURE__ */ _gte(value, params));
		},
		min(value, params) {
			return this.check(/* @__PURE__ */ _gte(value, params));
		},
		lt(value, params) {
			return this.check(/* @__PURE__ */ _lt(value, params));
		},
		lte(value, params) {
			return this.check(/* @__PURE__ */ _lte(value, params));
		},
		max(value, params) {
			return this.check(/* @__PURE__ */ _lte(value, params));
		},
		int(params) {
			return this.check(int$2(params));
		},
		safe(params) {
			return this.check(int$2(params));
		},
		positive(params) {
			return this.check(/* @__PURE__ */ _gt(0, params));
		},
		nonnegative(params) {
			return this.check(/* @__PURE__ */ _gte(0, params));
		},
		negative(params) {
			return this.check(/* @__PURE__ */ _lt(0, params));
		},
		nonpositive(params) {
			return this.check(/* @__PURE__ */ _lte(0, params));
		},
		multipleOf(value, params) {
			return this.check(/* @__PURE__ */ _multipleOf(value, params));
		},
		step(value, params) {
			return this.check(/* @__PURE__ */ _multipleOf(value, params));
		},
		finite() {
			return this;
		}
	});
	const bag = inst._zod.bag;
	inst.minValue = Math.max(bag.minimum ?? Number.NEGATIVE_INFINITY, bag.exclusiveMinimum ?? Number.NEGATIVE_INFINITY) ?? null;
	inst.maxValue = Math.min(bag.maximum ?? Number.POSITIVE_INFINITY, bag.exclusiveMaximum ?? Number.POSITIVE_INFINITY) ?? null;
	inst.isInt = (bag.format ?? "").includes("int") || Number.isSafeInteger(bag.multipleOf ?? .5);
	inst.isFinite = true;
	inst.format = bag.format ?? null;
});
function number(params) {
	return /* @__PURE__ */ _number(ZodNumber, params);
}
var ZodNumberFormat = /*@__PURE__*/ $constructor("ZodNumberFormat", (inst, def) => {
	$ZodNumberFormat.init(inst, def);
	ZodNumber.init(inst, def);
});
function int$2(params) {
	return /* @__PURE__ */ _int(ZodNumberFormat, params);
}
var ZodBoolean = /*@__PURE__*/ $constructor("ZodBoolean", (inst, def) => {
	$ZodBoolean.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => booleanProcessor(inst, ctx, json, params);
});
function boolean(params) {
	return /* @__PURE__ */ _boolean(ZodBoolean, params);
}
var ZodNull = /*@__PURE__*/ $constructor("ZodNull", (inst, def) => {
	$ZodNull.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => nullProcessor(inst, ctx, json, params);
});
function _null(params) {
	return /* @__PURE__ */ _null$1(ZodNull, params);
}
var ZodAny = /*@__PURE__*/ $constructor("ZodAny", (inst, def) => {
	$ZodAny.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => void 0;
});
function any() {
	return /* @__PURE__ */ _any(ZodAny);
}
var ZodUnknown = /*@__PURE__*/ $constructor("ZodUnknown", (inst, def) => {
	$ZodUnknown.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => void 0;
});
function unknown() {
	return /* @__PURE__ */ _unknown(ZodUnknown);
}
var ZodNever = /*@__PURE__*/ $constructor("ZodNever", (inst, def) => {
	$ZodNever.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => neverProcessor(inst, ctx, json, params);
});
function never(params) {
	return /* @__PURE__ */ _never(ZodNever, params);
}
var ZodArray = /*@__PURE__*/ $constructor("ZodArray", (inst, def) => {
	$ZodArray.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => arrayProcessor(inst, ctx, json, params);
	inst.element = def.element;
	_installLazyMethods(inst, "ZodArray", {
		min(n, params) {
			return this.check(/* @__PURE__ */ _minLength(n, params));
		},
		nonempty(params) {
			return this.check(/* @__PURE__ */ _minLength(1, params));
		},
		max(n, params) {
			return this.check(/* @__PURE__ */ _maxLength(n, params));
		},
		length(n, params) {
			return this.check(/* @__PURE__ */ _length(n, params));
		},
		unwrap() {
			return this.element;
		}
	});
});
function array(element, params) {
	return /* @__PURE__ */ _array(ZodArray, element, params);
}
var ZodObject = /*@__PURE__*/ $constructor("ZodObject", (inst, def) => {
	$ZodObjectJIT.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => objectProcessor(inst, ctx, json, params);
	defineLazy(inst, "shape", () => {
		return def.shape;
	});
	_installLazyMethods(inst, "ZodObject", {
		keyof() {
			return _enum(Object.keys(this._zod.def.shape));
		},
		catchall(catchall) {
			return this.clone({
				...this._zod.def,
				catchall
			});
		},
		passthrough() {
			return this.clone({
				...this._zod.def,
				catchall: unknown()
			});
		},
		loose() {
			return this.clone({
				...this._zod.def,
				catchall: unknown()
			});
		},
		strict() {
			return this.clone({
				...this._zod.def,
				catchall: never()
			});
		},
		strip() {
			return this.clone({
				...this._zod.def,
				catchall: void 0
			});
		},
		extend(incoming) {
			return extend(this, incoming);
		},
		safeExtend(incoming) {
			return safeExtend(this, incoming);
		},
		merge(other) {
			return merge$1(this, other);
		},
		pick(mask) {
			return pick(this, mask);
		},
		omit(mask) {
			return omit(this, mask);
		},
		partial(...args) {
			return partial(ZodOptional, this, args[0]);
		},
		required(...args) {
			return required(ZodNonOptional, this, args[0]);
		}
	});
});
function object(shape, params) {
	return new ZodObject({
		type: "object",
		shape: shape ?? {},
		...normalizeParams(params)
	});
}
var ZodUnion = /*@__PURE__*/ $constructor("ZodUnion", (inst, def) => {
	$ZodUnion.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => unionProcessor(inst, ctx, json, params);
	inst.options = def.options;
});
function union(options, params) {
	return new ZodUnion({
		type: "union",
		options,
		...normalizeParams(params)
	});
}
var ZodDiscriminatedUnion = /*@__PURE__*/ $constructor("ZodDiscriminatedUnion", (inst, def) => {
	ZodUnion.init(inst, def);
	$ZodDiscriminatedUnion.init(inst, def);
});
function discriminatedUnion(discriminator, options, params) {
	return new ZodDiscriminatedUnion({
		type: "union",
		options,
		discriminator,
		...normalizeParams(params)
	});
}
var ZodIntersection = /*@__PURE__*/ $constructor("ZodIntersection", (inst, def) => {
	$ZodIntersection.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => intersectionProcessor(inst, ctx, json, params);
});
function intersection(left, right) {
	return new ZodIntersection({
		type: "intersection",
		left,
		right
	});
}
var ZodRecord = /*@__PURE__*/ $constructor("ZodRecord", (inst, def) => {
	$ZodRecord.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => recordProcessor(inst, ctx, json, params);
	inst.keyType = def.keyType;
	inst.valueType = def.valueType;
});
function record(keyType, valueType, params) {
	if (!valueType || !valueType._zod) return new ZodRecord({
		type: "record",
		keyType: string$2(),
		valueType: keyType,
		...normalizeParams(valueType)
	});
	return new ZodRecord({
		type: "record",
		keyType,
		valueType,
		...normalizeParams(params)
	});
}
var ZodEnum = /*@__PURE__*/ $constructor("ZodEnum", (inst, def) => {
	$ZodEnum.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => enumProcessor(inst, ctx, json, params);
	inst.enum = def.entries;
	inst.options = Object.values(def.entries);
	const keys = new Set(Object.keys(def.entries));
	inst.extract = (values, params) => {
		const newEntries = {};
		for (const value of values) if (keys.has(value)) newEntries[value] = def.entries[value];
		else throw new Error(`Key ${value} not found in enum`);
		return new ZodEnum({
			...def,
			checks: [],
			...normalizeParams(params),
			entries: newEntries
		});
	};
	inst.exclude = (values, params) => {
		const newEntries = { ...def.entries };
		for (const value of values) if (keys.has(value)) delete newEntries[value];
		else throw new Error(`Key ${value} not found in enum`);
		return new ZodEnum({
			...def,
			checks: [],
			...normalizeParams(params),
			entries: newEntries
		});
	};
});
function _enum(values, params) {
	return new ZodEnum({
		type: "enum",
		entries: Array.isArray(values) ? Object.fromEntries(values.map((v) => [v, v])) : values,
		...normalizeParams(params)
	});
}
var ZodLiteral = /*@__PURE__*/ $constructor("ZodLiteral", (inst, def) => {
	$ZodLiteral.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => literalProcessor(inst, ctx, json, params);
	inst.values = new Set(def.values);
	Object.defineProperty(inst, "value", { get() {
		if (def.values.length > 1) throw new Error("This schema contains multiple valid literal values. Use `.values` instead.");
		return def.values[0];
	} });
});
function literal(value, params) {
	return new ZodLiteral({
		type: "literal",
		values: Array.isArray(value) ? value : [value],
		...normalizeParams(params)
	});
}
var ZodTransform = /*@__PURE__*/ $constructor("ZodTransform", (inst, def) => {
	$ZodTransform.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => transformProcessor(inst, ctx, json, params);
	inst._zod.parse = (payload, _ctx) => {
		if (_ctx.direction === "backward") throw new $ZodEncodeError(inst.constructor.name);
		payload.addIssue = (issue$1) => {
			if (typeof issue$1 === "string") payload.issues.push(issue(issue$1, payload.value, def));
			else {
				const _issue = issue$1;
				if (_issue.fatal) _issue.continue = false;
				_issue.code ?? (_issue.code = "custom");
				_issue.input ?? (_issue.input = payload.value);
				_issue.inst ?? (_issue.inst = inst);
				payload.issues.push(issue(_issue));
			}
		};
		const output = def.transform(payload.value, payload);
		if (output instanceof Promise) return output.then((output) => {
			payload.value = output;
			payload.fallback = true;
			return payload;
		});
		payload.value = output;
		payload.fallback = true;
		return payload;
	};
});
function transform(fn) {
	return new ZodTransform({
		type: "transform",
		transform: fn
	});
}
var ZodOptional = /*@__PURE__*/ $constructor("ZodOptional", (inst, def) => {
	$ZodOptional.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => optionalProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function optional$1(innerType) {
	return new ZodOptional({
		type: "optional",
		innerType
	});
}
var ZodExactOptional = /*@__PURE__*/ $constructor("ZodExactOptional", (inst, def) => {
	$ZodExactOptional.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => optionalProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function exactOptional(innerType) {
	return new ZodExactOptional({
		type: "optional",
		innerType
	});
}
var ZodNullable = /*@__PURE__*/ $constructor("ZodNullable", (inst, def) => {
	$ZodNullable.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => nullableProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function nullable(innerType) {
	return new ZodNullable({
		type: "nullable",
		innerType
	});
}
var ZodDefault = /*@__PURE__*/ $constructor("ZodDefault", (inst, def) => {
	$ZodDefault.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => defaultProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
	inst.removeDefault = inst.unwrap;
});
function _default(innerType, defaultValue) {
	return new ZodDefault({
		type: "default",
		innerType,
		get defaultValue() {
			return typeof defaultValue === "function" ? defaultValue() : shallowClone(defaultValue);
		}
	});
}
var ZodPrefault = /*@__PURE__*/ $constructor("ZodPrefault", (inst, def) => {
	$ZodPrefault.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => prefaultProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function prefault(innerType, defaultValue) {
	return new ZodPrefault({
		type: "prefault",
		innerType,
		get defaultValue() {
			return typeof defaultValue === "function" ? defaultValue() : shallowClone(defaultValue);
		}
	});
}
var ZodNonOptional = /*@__PURE__*/ $constructor("ZodNonOptional", (inst, def) => {
	$ZodNonOptional.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => nonoptionalProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function nonoptional(innerType, params) {
	return new ZodNonOptional({
		type: "nonoptional",
		innerType,
		...normalizeParams(params)
	});
}
var ZodCatch = /*@__PURE__*/ $constructor("ZodCatch", (inst, def) => {
	$ZodCatch.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => catchProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
	inst.removeCatch = inst.unwrap;
});
function _catch(innerType, catchValue) {
	return new ZodCatch({
		type: "catch",
		innerType,
		catchValue: typeof catchValue === "function" ? catchValue : () => catchValue
	});
}
var ZodPipe = /*@__PURE__*/ $constructor("ZodPipe", (inst, def) => {
	$ZodPipe.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => pipeProcessor(inst, ctx, json, params);
	inst.in = def.in;
	inst.out = def.out;
});
function pipe(in_, out) {
	return new ZodPipe({
		type: "pipe",
		in: in_,
		out
	});
}
var ZodReadonly = /*@__PURE__*/ $constructor("ZodReadonly", (inst, def) => {
	$ZodReadonly.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => readonlyProcessor(inst, ctx, json, params);
	inst.unwrap = () => inst._zod.def.innerType;
});
function readonly$1(innerType) {
	return new ZodReadonly({
		type: "readonly",
		innerType
	});
}
var ZodCustom = /*@__PURE__*/ $constructor("ZodCustom", (inst, def) => {
	$ZodCustom.init(inst, def);
	ZodType.init(inst, def);
	inst._zod.processJSONSchema = (ctx, json, params) => customProcessor(inst, ctx, json, params);
});
function refine(fn, _params = {}) {
	return /* @__PURE__ */ _refine(ZodCustom, fn, _params);
}
function superRefine(fn, params) {
	return /* @__PURE__ */ _superRefine(fn, params);
}
// node_modules/.pnpm/zod@4.4.3/node_modules/zod/v4/classic/coerce.js
function string$1(params) {
	return /* @__PURE__ */ _coercedString(ZodString, params);
}
// src/type/scripts.ts
var ScriptButton = object({
	name: string$1(),
	visible: boolean()
});
var ScriptExportWith = object({
	data: boolean().prefault(true).catch(true),
	button: boolean().prefault(true).catch(true)
}).prefault({});
var Script = object({
	type: literal("script").default("script"),
	enabled: boolean().default(false),
	name: string$1().default(""),
	id: string$1().default(() => uuidv4()),
	content: string$1().default(""),
	info: string$1().default(""),
	button: object({
		enabled: boolean().default(true),
		buttons: array(ScriptButton).default([])
	}).prefault({}),
	data: record(string$2(), any()).default({}).catch({}),
	export_with: ScriptExportWith
});
var ScriptFolder$1 = object({
	type: literal("folder").default("folder"),
	enabled: boolean().default(false),
	name: string$1().default(""),
	id: string$1().default(() => uuidv4()),
	icon: string$2().default("fa-solid fa-folder"),
	color: string$2().default(getSmartThemeQuoteColor),
	scripts: array(Script).default([])
});
var ScriptTree$1 = discriminatedUnion("type", [Script, ScriptFolder$1]);
function isScript(script_tree) {
	return script_tree.type === "script";
}
function flattenScriptTree(script_tree) {
	return script_tree.type === "folder" ? script_tree.scripts : [script_tree];
}
// src/panel/script/export_by.ts
function collectExportSummaryItems(original_scripts, exported_scripts) {
	const exported_script_map = new Map(exported_scripts.flatMap(flattenScriptTree).map((script) => [script.id, script]));
	return original_scripts.flatMap(flattenScriptTree).flatMap((script) => {
		const has_data = !_.isEmpty(script.data);
		const has_button = script.button.buttons.length > 0;
		if (!has_data && !has_button) return [];
		const exported_script = exported_script_map.get(script.id);
		return [{
			script_name: script.name,
			has_data,
			has_button,
			exported_data: has_data && !_.isEmpty(exported_script?.data),
			exported_button: has_button && (exported_script?.button.buttons.length ?? 0) > 0
		}];
	});
}
function showExportSummaryToast(target_name, items) {
	if (items.length === 0) return;
	toastr.info(`${t`本次${target_name}导出包含 ${items.length} 个带变量或按钮的酒馆助手脚本`}<br><u>${t`点此查看详情`}</u>`, t`酒馆助手`, {
		showDuration: 1e3,
		escapeHtml: false,
		onclick: () => {
			$o({
				component: ExportBy_default,
				attrs: {
					targetName: target_name,
					items
				}
			}).open();
		}
	});
}
// src/type/settings.ts
var setting_field = "tavern_helper";
var AudioMode = _enum([
	"repeat_one",
	"repeat_all",
	"shuffle",
	"play_one_and_stop"
]);
var CollapseCodeBlock = _enum([
	"none",
	"frontend_only",
	"all"
]);
var GlobalSettings$1 = object({
	audio: object({
		enabled: boolean().default(true),
		bgm: object({
			enabled: boolean().default(true),
			mode: AudioMode.default("repeat_all"),
			muted: boolean().default(false),
			volume: number().default(50)
		}).prefault({}),
		ambient: object({
			enabled: boolean().default(true),
			mode: AudioMode.default("play_one_and_stop"),
			muted: boolean().default(false),
			volume: number().default(50)
		}).prefault({})
	}).prefault({}),
	listener: object({
		enabled: boolean().default(false),
		enable_echo: boolean().default(true),
		url: string$2().default("http://localhost:6621").catch("http://localhost:6621"),
		duration: number().default(1e3).catch(1e3)
	}).prefault({}),
	macro: object({ enabled: boolean().default(true) }).prefault({}),
	optimize: object({
		disable_incompatible_option: boolean().default(true),
		better_message_to_load: boolean().default(true),
		better_character_update: boolean().default(true),
		better_character_export: boolean().default(true),
		better_character_deletion: boolean().default(true),
		force_recommended_worldbook_global_settings: boolean().default(true),
		save_preset_when_saving_preset_entries: boolean().default(true),
		maximize_preset_context_length: boolean().default(true)
	}).prefault({}),
	render: object({
		enabled: boolean().default(true),
		collapse_code_block: CollapseCodeBlock.default("frontend_only").catch("frontend_only"),
		allow_streaming: boolean().default(false),
		use_blob_url: boolean().default(false),
		use_cleanup_protector: boolean().default(false),
		optimize_hljs: boolean().default(true),
		depth: number().default(0).catch(0),
		depth_ignore_hidden: boolean().default(false)
	}).prefault({}),
	script: object({
		enabled: object({
			global: boolean().default(true),
			presets: array(string$1()).default([]),
			characters: array(string$1()).default([])
		}).prefault({}),
		popuped: object({
			presets: array(string$1()).default([]),
			characters: array(string$1()).default([])
		}).prefault({}),
		scripts: array(ScriptTree$1).default([])
	}).prefault({}),
	$impl: object({ 已经提醒过查看提示词模板问号: boolean().default(false) }).prefault({})
}).prefault({});
var ChatSettings = object({
	bgm: array(object({
		url: string$2(),
		title: string$1()
	})).default([]),
	ambient: array(object({
		url: string$2(),
		title: string$1()
	})).default([])
}).prefault({});
var PresetSettings = object({
	scripts: array(ScriptTree$1).default([]).catch([]),
	variables: record(string$2(), any()).default({}).catch({})
}).prefault({});
var CharacterSettings$1 = object({
	scripts: array(ScriptTree$1).default([]).catch([]),
	variables: record(string$2(), any()).default({}).catch({})
}).prefault({});
// src/type/backward.ts
var ScriptData = object({
	enabled: boolean().default(false).catch(false),
	name: string$2().default("").catch(""),
	id: string$2().default(() => uuidv4()).catch(() => uuidv4()),
	content: string$2().default("").catch(""),
	info: string$2().default("").catch(""),
	buttons: array(object({
		name: string$2().default("").catch(""),
		visible: boolean().default(true).catch(true)
	}).prefault({})).default([]).catch([]),
	data: record(string$2(), any()).default({}).catch({})
}).prefault({}).transform((item) => {
	return Script.parse({
		type: "script",
		enabled: item.enabled,
		name: item.name,
		id: item.id,
		content: item.content,
		info: item.info,
		button: {
			enabled: true,
			buttons: item.buttons
		},
		data: item.data,
		export_with: {
			data: true,
			button: true
		}
	});
});
var ScriptTree = union([discriminatedUnion("type", [object({
	type: literal("script").default("script").catch("script"),
	value: ScriptData
}).transform((item) => item.value), object({
	type: literal("folder").default("folder").catch("folder"),
	id: string$2().default(() => uuidv4()).catch(() => uuidv4()),
	name: string$2().default("").catch(""),
	icon: string$2().default("fa-solid fa-folder").catch("fa-solid fa-folder"),
	color: string$2().default(getSmartThemeQuoteColor).catch(getSmartThemeQuoteColor),
	value: array(ScriptData).default([]).catch([])
}).transform((folder) => {
	return ScriptFolder$1.parse({
		type: "folder",
		enabled: true,
		name: folder.name,
		id: folder.id,
		icon: folder.icon,
		color: folder.color,
		scripts: folder.value
	});
})]), ScriptData]);
var GlobalSettings = object({
	macro: object({ replace: boolean().default(true).catch(true) }).prefault({}),
	render: object({
		render_enabled: boolean().default(true).catch(true),
		render_depth: number().default(0).catch(0),
		render_blob_url: boolean().default(false).catch(false)
	}).prefault({}),
	script: object({
		global_script_enabled: boolean().default(true).catch(true),
		scriptsRepository: array(ScriptTree).default([]).catch([]),
		characters_with_scripts: array(union([string$2(), _null()])).default([]).catch([]).transform((characters) => characters.filter((character) => character !== null))
	}).prefault({})
}).prefault({}).transform((settings) => {
	return GlobalSettings$1.parse({
		macro: { enabled: settings.macro.replace },
		render: {
			enabled: settings.render.render_enabled,
			collapse_code_block: "frontend_only",
			allow_streaming: false,
			use_blob_url: settings.render.render_blob_url,
			use_cleanup_protector: false,
			optimize_hljs: true,
			depth: settings.render.render_depth,
			depth_ignore_hidden: false
		},
		script: {
			enabled: {
				global: settings.script.global_script_enabled,
				presets: [],
				characters: settings.script.characters_with_scripts
			},
			popuped: {
				presets: [],
				characters: []
			},
			scripts: settings.script.scriptsRepository
		}
	});
});
var CharacterSettings = object({
	scripts: array(ScriptTree).default([]).catch([]),
	variables: record(string$2(), any()).default({}).catch({})
}).prefault({}).transform((settings) => {
	return CharacterSettings$1.parse({
		scripts: settings.scripts,
		variables: settings.variables
	});
});
// src/util/compatibility.ts
async function updateWorldInfoList() {
	const result = await fetch("/api/settings/get", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({})
	});
	if (result.ok) {
		const data = await result.json();
		world_names.length = 0;
		world_names.push(...data.world_names?.length ? data.world_names : []);
		$("#world_info").find("option[value!=\"\"]").remove();
		$("#world_editor_select").find("option[value!=\"\"]").remove();
		world_names.forEach((item, i) => {
			$("#world_info").append(`<option value='${i}'${selected_world_info.includes(item) ? " selected" : ""}>${item}</option>`);
			$("#world_editor_select").append(`<option value='${i}'>${item}</option>`);
		});
	}
}
function fromCharacterBook(character_book) {
	const result = { entries: {} };
	character_book.entries.forEach((entry, index) => {
		if (entry.id === void 0) entry.id = index;
		result.entries[entry.id] = {
			...newWorldInfoEntryTemplate,
			uid: entry.id,
			key: entry.keys,
			keysecondary: entry.secondary_keys || [],
			comment: entry.comment || "",
			content: entry.content,
			constant: entry.constant || false,
			selective: entry.selective || false,
			order: entry.insertion_order,
			position: entry.extensions?.position ?? (entry.position === "before_char" ? world_info_position.before : world_info_position.after),
			excludeRecursion: entry.extensions?.exclude_recursion ?? false,
			preventRecursion: entry.extensions?.prevent_recursion ?? false,
			delayUntilRecursion: entry.extensions?.delay_until_recursion ?? false,
			disable: !entry.enabled,
			addMemo: !!entry.comment,
			displayIndex: entry.extensions?.display_index ?? index,
			probability: entry.extensions?.probability ?? 100,
			useProbability: entry.extensions?.useProbability ?? true,
			depth: entry.extensions?.depth ?? DEFAULT_DEPTH,
			selectiveLogic: entry.extensions?.selectiveLogic ?? world_info_logic.AND_ANY,
			group: entry.extensions?.group ?? "",
			groupOverride: entry.extensions?.group_override ?? false,
			groupWeight: entry.extensions?.group_weight ?? DEFAULT_WEIGHT,
			scanDepth: entry.extensions?.scan_depth ?? null,
			caseSensitive: entry.extensions?.case_sensitive ?? null,
			matchWholeWords: entry.extensions?.match_whole_words ?? null,
			useGroupScoring: entry.extensions?.use_group_scoring ?? null,
			automationId: entry.extensions?.automation_id ?? "",
			role: entry.extensions?.role ?? extension_prompt_roles.SYSTEM,
			vectorized: entry.extensions?.vectorized ?? false,
			sticky: entry.extensions?.sticky ?? null,
			cooldown: entry.extensions?.cooldown ?? null,
			delay: entry.extensions?.delay ?? null,
			extensions: entry.extensions ?? {}
		};
	});
	return result;
}
/**
* Reloads the editor with the specified world info file
* @param file The file to load in the editor
* @param loadIfNotSelected Indicates whether to load the file even if it's not currently selected
*/
function reloadEditor(file, load_if_not_selected = false) {
	const current_index = Number($("#world_editor_select").val());
	const selected_index = world_names.indexOf(file);
	if (selected_index !== -1 && (load_if_not_selected || current_index === selected_index)) $("#world_editor_select").val(selected_index).trigger("change");
}
async function copyText(text) {
	if (navigator.clipboard) {
		navigator.clipboard.writeText(text);
		return;
	}
	const parent = document.querySelector("dialog[open]:last-of-type") ?? document.body;
	const textArea = document.createElement("textarea");
	textArea.value = text;
	parent.appendChild(textArea);
	textArea.focus();
	textArea.select();
	document.execCommand("copy");
	parent.removeChild(textArea);
}
var reloadEditorDebounced = _.debounce(reloadEditor, 1e3);
var settingsToUpdate = {
	max_context_unlocked: {
		selector: "#oai_max_context_unlocked",
		oai_setting: "max_context_unlocked",
		type: "checkbox"
	},
	openai_max_context: {
		selector: "#openai_max_context",
		oai_setting: "openai_max_context",
		type: "input"
	},
	openai_max_tokens: {
		selector: "#openai_max_tokens",
		oai_setting: "openai_max_tokens",
		type: "input"
	},
	n: {
		selector: "#n_openai",
		oai_setting: "n",
		type: "input"
	},
	stream_openai: {
		selector: "#stream_toggle",
		oai_setting: "stream_openai",
		type: "checkbox"
	},
	temperature: {
		selector: "#temp_openai",
		oai_setting: "temp_openai",
		type: "input"
	},
	frequency_penalty: {
		selector: "#freq_pen_openai",
		oai_setting: "freq_pen_openai",
		type: "input"
	},
	presence_penalty: {
		selector: "#pres_pen_openai",
		oai_setting: "pres_pen_openai",
		type: "input"
	},
	top_p: {
		selector: "#top_p_openai",
		oai_setting: "top_p_openai",
		type: "input"
	},
	repetition_penalty: {
		selector: "#repetition_penalty_openai",
		oai_setting: "repetition_penalty_openai",
		type: "input"
	},
	min_p: {
		selector: "#min_p_openai",
		oai_setting: "min_p_openai",
		type: "input"
	},
	top_k: {
		selector: "#top_k_openai",
		oai_setting: "top_k_openai",
		type: "input"
	},
	top_a: {
		selector: "#top_a_openai",
		oai_setting: "top_a_openai",
		type: "input"
	},
	seed: {
		selector: "#seed_openai",
		oai_setting: "seed",
		type: "input"
	},
	squash_system_messages: {
		selector: "#squash_system_messages",
		oai_setting: "squash_system_messages",
		type: "checkbox"
	},
	reasoning_effort: {
		selector: "#openai_reasoning_effort",
		oai_setting: "reasoning_effort",
		type: "input"
	},
	show_thoughts: {
		selector: "#openai_show_thoughts",
		oai_setting: "show_thoughts",
		type: "checkbox"
	},
	request_images: {
		selector: "#openai_request_images",
		oai_setting: "request_images",
		type: "checkbox"
	},
	function_calling: {
		selector: "#openai_function_calling",
		oai_setting: "function_calling",
		type: "checkbox"
	},
	enable_web_search: {
		selector: "#openai_enable_web_search",
		oai_setting: "enable_web_search",
		type: "checkbox"
	},
	image_inlining: {
		selector: "#openai_image_inlining",
		oai_setting: "image_inlining",
		type: "checkbox"
	},
	inline_image_quality: {
		selector: "#openai_inline_image_quality",
		oai_setting: "inline_image_quality",
		type: "input"
	},
	video_inlining: {
		selector: "#openai_video_inlining",
		oai_setting: "video_inlining",
		type: "checkbox"
	},
	names_behavior: {
		selector: "#names_behavior",
		oai_setting: "names_behavior",
		type: "input"
	},
	wrap_in_quotes: {
		selector: "#wrap_in_quotes",
		oai_setting: "wrap_in_quotes",
		type: "checkbox"
	},
	prompts: {
		selector: "#prompts",
		oai_setting: "prompts",
		type: "none"
	},
	prompt_order: {
		selector: "#prompt_order",
		oai_setting: "prompt_order",
		type: "none"
	},
	extensions: {
		selector: "#extensions",
		oai_setting: "extensions",
		type: "none"
	}
};
// src/util/is_frontend.ts
function isFrontend(content) {
	return [
		"html>",
		"<head>",
		"<body"
	].some((tag) => content.includes(tag));
}
// node_modules/.pnpm/klona@2.0.6/node_modules/klona/dist/index.mjs
var import_src = (/* @__PURE__ */ __commonJSMin(((exports, module) => {
	function isUndefined(value) {
		return value === void 0;
	}
	function isNull(value) {
		return value === null;
	}
	function isBoolean(value) {
		return typeof value === "boolean";
	}
	function isObject(value) {
		return value === Object(value);
	}
	function isArray(value) {
		return Array.isArray(value);
	}
	function isDate(value) {
		return value instanceof Date;
	}
	function isBlob(value, isReactNative) {
		return isReactNative ? isObject(value) && !isUndefined(value.uri) : isObject(value) && typeof value.size === "number" && typeof value.type === "string" && typeof value.slice === "function";
	}
	function isFile(value, isReactNative) {
		return isBlob(value, isReactNative) && typeof value.name === "string" && (isObject(value.lastModifiedDate) || typeof value.lastModified === "number");
	}
	function initCfg(value) {
		return isUndefined(value) ? false : value;
	}
	function serialize(obj, cfg, fd, pre) {
		cfg = cfg || {};
		fd = fd || new FormData();
		cfg.indices = initCfg(cfg.indices);
		cfg.nullsAsUndefineds = initCfg(cfg.nullsAsUndefineds);
		cfg.booleansAsIntegers = initCfg(cfg.booleansAsIntegers);
		cfg.allowEmptyArrays = initCfg(cfg.allowEmptyArrays);
		cfg.noAttributesWithArrayNotation = initCfg(cfg.noAttributesWithArrayNotation);
		cfg.noFilesWithArrayNotation = initCfg(cfg.noFilesWithArrayNotation);
		cfg.dotsForObjectNotation = initCfg(cfg.dotsForObjectNotation);
		const isReactNative = typeof fd.getParts === "function";
		if (isUndefined(obj)) return fd;
		else if (isNull(obj)) {
			if (!cfg.nullsAsUndefineds) fd.append(pre, "");
		} else if (isBoolean(obj)) {
			if (cfg.booleansAsIntegers) fd.append(pre, obj ? 1 : 0);
			else fd.append(pre, obj);
		} else if (isArray(obj)) {
			if (obj.length) obj.forEach((value, index) => {
				let key = pre + "[" + (cfg.indices ? index : "") + "]";
				if (cfg.noAttributesWithArrayNotation || cfg.noFilesWithArrayNotation && isFile(value, isReactNative)) key = pre;
				serialize(value, cfg, fd, key);
			});
			else if (cfg.allowEmptyArrays) fd.append(cfg.noAttributesWithArrayNotation ? pre : pre + "[]", "");
		} else if (isDate(obj)) fd.append(pre, obj.toISOString());
		else if (isObject(obj) && !isBlob(obj, isReactNative)) Object.keys(obj).forEach((prop) => {
			const value = obj[prop];
			if (isArray(value)) while (prop.length > 2 && prop.lastIndexOf("[]") === prop.length - 2) prop = prop.substring(0, prop.length - 2);
			const key = pre ? cfg.dotsForObjectNotation ? pre + "." + prop : pre + "[" + prop + "]" : prop;
			serialize(value, cfg, fd, key);
		});
		else fd.append(pre, obj);
		return fd;
	}
	module.exports = { serialize };
})))();
function klona(x) {
	if (typeof x !== "object") return x;
	var k, tmp, str = Object.prototype.toString.call(x);
	if (str === "[object Object]") {
		if (x.constructor !== Object && typeof x.constructor === "function") {
			tmp = new x.constructor();
			for (k in x) if (x.hasOwnProperty(k) && tmp[k] !== x[k]) tmp[k] = klona(x[k]);
		} else {
			tmp = {};
			for (k in x) if (k === "__proto__") Object.defineProperty(tmp, k, {
				value: klona(x[k]),
				configurable: true,
				enumerable: true,
				writable: true
			});
			else tmp[k] = klona(x[k]);
		}
		return tmp;
	}
	if (str === "[object Array]") {
		k = x.length;
		for (tmp = Array(k); k--;) tmp[k] = klona(x[k]);
		return tmp;
	}
	if (str === "[object Set]") {
		tmp = /* @__PURE__ */ new Set();
		x.forEach(function(val) {
			tmp.add(klona(val));
		});
		return tmp;
	}
	if (str === "[object Map]") {
		tmp = /* @__PURE__ */ new Map();
		x.forEach(function(val, key) {
			tmp.set(klona(key), klona(val));
		});
		return tmp;
	}
	if (str === "[object Date]") return /* @__PURE__ */ new Date(+x);
	if (str === "[object RegExp]") {
		tmp = new RegExp(x.source, x.flags);
		tmp.lastIndex = x.lastIndex;
		return tmp;
	}
	if (str === "[object DataView]") return new x.constructor(klona(x.buffer));
	if (str === "[object ArrayBuffer]") return x.slice(0);
	if (str.slice(-6) === "Array]") return new x.constructor(x);
	return x;
}
// src/util/tavern.ts
var version$1 = await fetch("/version").then((res) => res.json()).then((data) => data.pkgVersion).catch(() => "1.0.0");
var APP_READY_EVENTS = [
	event_types.APP_READY,
	"chatLoaded",
	event_types.SETTINGS_UPDATED
];
var preset_manager = getPresetManager("openai");
function getCompletionPresetByName(name) {
	const { presets, preset_names } = preset_manager.getPresetList();
	let preset;
	if (Array.isArray(preset_names)) {
		if (preset_names.includes(name)) preset = presets[preset_names.indexOf(name)];
	} else if (preset_names[name] !== void 0) preset = presets[preset_names[name]];
	if (preset === void 0) console.error(`Preset ${name} not found`);
	return preset;
}
function highlight_code(element) {
	const $node = $(element);
	if ($node.hasClass("hljs") || isFrontend($node.text())) return;
	hljs.highlightElement(element);
	$node.append($(`<i class="fa-solid fa-copy code-copy interactable" title="Copy code"></i>`).on("click", function(e) {
		e.stopPropagation();
	}).on("pointerup", async function() {
		navigator.clipboard.writeText($(element).text());
		toastr.info(t`已复制!`, "", { timeOut: 2e3 });
	}));
}
var saveChatConditionalDebounced = _.debounce(saveChatConditional, 1e3);
async function reloadChatWithoutEvents() {
	const chat_id = getCurrentChatId();
	if (chat_id === void 0) return false;
	await saveChatConditional();
	if (getCurrentChatId() !== chat_id) return false;
	if (characters.at(this_chid)) {
		await clearChat();
		await printMessages();
		return true;
	}
	return false;
}
function invokeMessageRenders() {
	$("#chat > .mes").each((_index, element) => {
		eventSource.emit($(element).attr("is_user") ? event_types.USER_MESSAGE_RENDERED : event_types.CHARACTER_MESSAGE_RENDERED, $(element).attr("mesid"));
	});
}
async function reloadAndRenderChatWithoutEvents() {
	if (await reloadChatWithoutEvents()) invokeMessageRenders();
}
async function getImageTokenCost(data_url, quality) {
	const TOKENS_PER_IMAGE = 85;
	if (quality === "low") return TOKENS_PER_IMAGE;
	const size = await getImageSizeFromDataURL(data_url);
	if (quality === "auto" && size.width <= 512 && size.height <= 512) return TOKENS_PER_IMAGE;
	const scale = 2048 / Math.min(size.width, size.height);
	const scaledWidth = Math.round(size.width * scale);
	const scaledHeight = Math.round(size.height * scale);
	const finalScale = 768 / Math.min(scaledWidth, scaledHeight);
	const finalWidth = Math.round(scaledWidth * finalScale);
	const finalHeight = Math.round(scaledHeight * finalScale);
	return Math.ceil(finalWidth / 512) * Math.ceil(finalHeight / 512) * 170 + 85;
}
async function getVideoTokenCost(_data_url) {
	return 1e3;
}
function renderMarkdown(markdown) {
	return reloadMarkdownProcessor().makeHtml(markdown);
}
function getFirstMessage() {
	const first_messag = characters[Number(this_chid)]?.first_mes || "";
	const alternate_greetings = characters[Number(this_chid)]?.data?.alternate_greetings;
	const message = {
		name: name2,
		is_user: false,
		is_system: false,
		send_date: (/* @__PURE__ */ new Date()).toISOString(),
		mes: getRegexedString(first_messag, regex_placement.AI_OUTPUT),
		extra: {}
	};
	if (Array.isArray(alternate_greetings) && alternate_greetings.length > 0) {
		const swipes = [message.mes, ...alternate_greetings.map((greeting) => getRegexedString(greeting, regex_placement.AI_OUTPUT))];
		if (!message.mes) {
			swipes.shift();
			message.mes = swipes[0];
		}
		_.set(message, "swipe_id", 0);
		_.set(message, "swipes", swipes);
		_.set(message, "swipe_info", swipes.map((_) => ({
			send_date: message.send_date,
			gen_started: void 0,
			gen_finished: void 0,
			extra: {}
		})));
	}
	return message;
}
async function writeExtensionField(id, field, value, affect_memory = true) {
	let character = characters[Number(id)];
	if (!character) return;
	await unshallowCharacter(String(id));
	character = characters[Number(id)];
	if (_.isEqual(_.get(character.data.extensions, field), value)) return;
	if (!affect_memory) character = klona(character);
	if (value === void 0) _.unset(character.data.extensions, field);
	else _.set(character.data.extensions, field, value);
	if (character.json_data) {
		const json_data = JSON.parse(character.json_data);
		if (value === void 0) _.unset(json_data.data.extensions, field);
		else _.set(json_data.data.extensions, field, value);
		character.json_data = JSON.stringify(json_data);
		if (Number(id) === Number(this_chid)) $("#character_json_data").val(character.json_data);
	}
	const payload = {
		ch_name: character.name,
		avatar_url: character.avatar,
		character_version: character.data.character_version,
		creator: character.data.creator,
		creator_notes: character.data.creator_notes,
		description: character.data.description,
		first_mes: character.data.first_mes,
		alternate_greetings: character.data.alternate_greetings,
		world: character.data.extensions.world,
		extensions: JSON.stringify(character.data.extensions),
		chat: character.chat,
		create_date: character.create_date,
		personality: character.data.personality,
		scenario: character.data.scenario,
		mes_example: character.data.mes_example,
		talkativeness: character.data.extensions?.talkativeness,
		fav: character.data.extensions?.fav,
		tags: character.data.tags
	};
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	const response = await fetch("/api/characters/edit", {
		method: "POST",
		headers,
		body: (0, import_src.serialize)(payload),
		cache: "no-cache"
	});
	if (!response.ok) console.error("Failed to save extension field", await response.text());
}
// src/store/settings/character.ts
function getSettings$3(id) {
	const character = characters.at(id);
	if (character === void 0) return CharacterSettings$1.parse({});
	const backward_scripts = _.get(character, `data.extensions.TavernHelper_scripts`);
	const backward_variables = _.get(character, `data.extensions.TavernHelper_characterScriptVariables`);
	if (backward_scripts !== void 0 || backward_variables !== void 0) {
		if (!_.has(character, `data.extensions.tavern_helper`)) {
			const parsed = CharacterSettings.safeParse({
				scripts: backward_scripts ?? [],
				variables: backward_variables ?? {}
			});
			if (parsed.success) saveSettings$1(id, characters[id]?.avatar, parsed.data);
			else toastr.warning(parsed.error.message, t`[酒馆助手]迁移旧数据失败, 将使用空数据`);
		}
		writeExtensionField(id, "TavernHelper_scripts", void 0);
		writeExtensionField(id, "TavernHelper_characterScriptVariables", void 0);
	}
	const settings = _.get(character, `data.extensions.${setting_field}`);
	const parsed = CharacterSettings$1.safeParse(settings !== void 0 ? _.isArray(settings) ? Object.fromEntries(settings) : settings : {});
	if (!parsed.success) {
		toastr.warning(parsed.error.message, t`[酒馆助手]读取角色卡数据失败, 将使用空数据`);
		return CharacterSettings$1.parse({});
	}
	return CharacterSettings$1.parse(parsed.data);
}
async function saveSettings$1(id, avatar, settings, affect_memory = true) {
	if (avatar === characters[id]?.avatar) await writeExtensionField(id, setting_field, settings, affect_memory);
}
var useCharacterSettingsStore = defineStore("character_setttings", () => {
	const id = ref(this_chid);
	const name = ref(characters?.[this_chid]?.name);
	const avatar = ref(characters?.[this_chid]?.avatar);
	eventSource.makeFirst(event_types.CHAT_CHANGED, () => {
		const new_avatar = characters?.[this_chid]?.avatar;
		if (avatar.value !== new_avatar) {
			id.value = this_chid;
			name.value = characters?.[this_chid]?.name;
			avatar.value = new_avatar;
		}
	});
	const settings = ref(getSettings$3(id.value));
	watch([id, avatar], ([new_id]) => {
		ignoreUpdates(() => {
			settings.value = getSettings$3(new_id);
		});
	});
	$("#character_replace_file").on("click", () => {
		eventSource.once(event_types.CHAT_CHANGED, () => {
			ignoreUpdates(async () => {
				const current_id = id.value;
				settings.value = getSettings$3(current_id);
				if ($("#world_button").hasClass("world_set")) {
					const book = characters[Number(current_id)]?.data?.character_book;
					if (book) {
						const book_name = book.name || `${characters[Number(current_id)]?.avatar?.replace(".png", "")}'s Lorebook`;
						await saveWorldInfo(book_name, fromCharacterBook(book), true);
						await updateWorldInfoList();
						$("#character_world").val(book_name).trigger("change");
					}
				}
			});
		});
	});
	$("#export_button").on("click", async () => {
		const book_name = $("#character_world").val();
		if (book_name) await saveWorldInfo(book_name, await loadWorldInfo(book_name), true);
	});
	{
		let scripts_summary = [];
		const originalFetch = window.fetch;
		window.fetch = async function(...args) {
			const [url] = args;
			const response = await originalFetch.apply(this, args);
			if (typeof url === "string" && url === "/api/characters/export") {
				eventSource.emit("character_export_ready", { scripts_summary });
				scripts_summary = [];
			}
			return response;
		};
		$("#export_button").on("click", async () => {
			if (id.value !== void 0 && avatar.value !== void 0) {
				const cleared_settings = klona(settings.value);
				cleared_settings.scripts.flatMap(flattenScriptTree).forEach((script) => {
					if (!script.export_with.data) script.data = {};
					if (!script.export_with.button) script.button.buttons = [];
				});
				scripts_summary = collectExportSummaryItems(settings.value.scripts, cleared_settings.scripts);
				await saveSettings$1(id.value, avatar.value, cleared_settings, false);
				const timeout_id = setTimeout(async () => {
					if (id.value !== void 0 && avatar.value !== void 0) {
						await saveSettings$1(id.value, avatar.value, klona(settings.value), false);
						scripts_summary = [];
					}
				}, 1e4);
				eventSource.once("character_export_ready", () => {
					clearTimeout(timeout_id);
				});
			}
		});
		eventSource.on("character_export_ready", async ({ scripts_summary }) => {
			if (id.value !== void 0 && avatar.value !== void 0) {
				await saveSettings$1(id.value, avatar.value, klona(settings.value), false);
				showExportSummaryToast(t`角色卡`, scripts_summary);
			}
		});
	}
	const { ignoreUpdates } = watchIgnorable(settings, async (new_settings) => {
		if (id.value !== void 0 && avatar.value !== void 0) await saveSettings$1(id.value, avatar.value, klona(new_settings));
	}, { deep: true });
	const forceReload = () => {
		ignoreUpdates(() => {
			if (id.value !== void 0 && avatar.value !== void 0) settings.value = getSettings$3(id.value);
		});
	};
	return {
		id: readonly(id),
		name: readonly(name),
		avatar: readonly(avatar),
		settings,
		forceReload
	};
});
// src/store/settings/chat.ts
function getSettings$2() {
	const settings = _.get(chat_metadata, setting_field, {});
	const parsed = ChatSettings.safeParse(settings);
	if (!parsed.success) {
		toastr.warning(parsed.error.message, t`[酒馆助手]读取聊天数据失败, 将使用空数据`);
		return ChatSettings.parse({});
	}
	return ChatSettings.parse(parsed.data);
}
var useChatSettingsStore = defineStore("chat_settings", () => {
	const id = ref(getCurrentChatId());
	eventSource.makeFirst(event_types.CHAT_CHANGED, (new_chat_id) => {
		if (id.value !== new_chat_id) id.value = new_chat_id;
	});
	const settings = ref(getSettings$2());
	watch(id, () => {
		ignoreUpdates(() => {
			settings.value = getSettings$2();
		});
	});
	const { ignoreUpdates } = watchIgnorable(settings, (new_settings) => {
		if (id.value !== void 0) {
			_.set(chat_metadata, setting_field, klona(new_settings));
			saveMetadataDebounced();
		}
	}, { deep: true });
	return {
		id: readonly(id),
		settings
	};
});
// src/store/settings/global.ts
function getSettings$1() {
	const backward_settings = _.get(extension_settings, "TavernHelper");
	if (backward_settings !== void 0 && !_.has(extension_settings, "tavern_helper")) {
		const parsed = GlobalSettings.safeParse(backward_settings);
		if (parsed.success) {
			_.set(extension_settings, setting_field, parsed.data);
			saveSettingsDebounced();
		} else toastr.warning(parsed.error.message, t`[酒馆助手]迁移旧数据失败, 将使用空数据`);
	}
	const settings = _.get(extension_settings, setting_field, {});
	const parsed = GlobalSettings$1.safeParse(settings);
	if (!parsed.success) {
		toastr.warning(parsed.error.message, t`[酒馆助手]读取全局数据失败, 将使用空数据`);
		return GlobalSettings$1.parse({});
	}
	return GlobalSettings$1.parse(parsed.data);
}
var useGlobalSettingsStore = defineStore("global_settings", () => {
	const app_ready = ref(false);
	APP_READY_EVENTS.forEach((event) => eventSource.once(event, () => {
		app_ready.value = true;
	}));
	const settings = ref(getSettings$1());
	if (settings.value.script.enabled.characters.length > 0 && !settings.value.script.enabled.characters[0].endsWith(".png")) settings.value.script.enabled.characters = settings.value.script.enabled.characters.map((item) => item.endsWith(".png") ? item : item + ".png");
	if (settings.value.script.popuped.characters.length > 0 && !settings.value.script.popuped.characters[0].endsWith(".png")) settings.value.script.popuped.characters = settings.value.script.popuped.characters.map((item) => item.endsWith(".png") ? item : item + ".png");
	watch(settings, (new_settings) => {
		_.set(extension_settings, setting_field, klona(new_settings));
		saveSettingsDebounced();
	}, { deep: true });
	return {
		app_ready,
		settings
	};
});
// src/store/settings/preset.ts
function getSettings(id) {
	const settings = _.get(preset_manager.getPresetList().presets[Number(id)], `extensions.${setting_field}`, {});
	const parsed = PresetSettings.safeParse(settings);
	if (!parsed.success) {
		toastr.warning(parsed.error.message, t`[酒馆助手]读取预设数据失败, 将使用空数据`);
		return PresetSettings.parse({});
	}
	return PresetSettings.parse(parsed.data);
}
function saveSettingsToMemoryDebounced(id, name, settings) {
	if (id === preset_manager.getSelectedPreset() && name === preset_manager.getSelectedPresetName()) {
		_.set(oai_settings, `extensions.${setting_field}`, settings);
		saveSettingsDebounced();
	}
}
async function saveSettingsToFile(_id, name, settings) {
	const preset_list = preset_manager.getPresetList();
	const index = _.get(preset_list.preset_names, name, -1);
	if (index === -1) return;
	const preset = preset_list.presets[index];
	_.set(preset, `extensions.${setting_field}`, settings);
	await preset_manager.savePreset(name, preset, { skipUpdate: true });
}
var saveSettingsToFileDebounced = _.debounce(saveSettingsToFile, 1e3);
var usePresetSettingsStore = defineStore("preset_settings", () => {
	const id = ref(preset_manager.getSelectedPreset());
	const name = ref(preset_manager.getSelectedPresetName());
	eventSource.makeFirst(event_types.OAI_PRESET_CHANGED_AFTER, () => {
		const new_id = String(preset_manager.getSelectedPreset());
		const new_name = String(preset_manager.getSelectedPresetName());
		if (name.value !== new_name) {
			id.value = new_id;
			name.value = new_name;
		}
	});
	const settings = ref(getSettings(id.value));
	watch([id, name], ([new_id]) => {
		ignoreUpdates(() => {
			settings.value = getSettings(new_id);
		});
	});
	eventSource.on(event_types.OAI_PRESET_EXPORT_READY, (preset) => {
		const script_trees = _.get(preset, `extensions.${setting_field}.scripts`, []);
		const original_script_trees = klona(script_trees);
		script_trees.flatMap(flattenScriptTree).forEach((script) => {
			if (!script.export_with.data) script.data = {};
			if (!script.export_with.button) script.button.buttons = [];
		});
		showExportSummaryToast(t`预设`, collectExportSummaryItems(original_script_trees, script_trees));
	});
	const { ignoreUpdates } = watchIgnorable(settings, (new_settings) => {
		saveSettingsToMemoryDebounced(id.value, name.value, klona(new_settings));
		saveSettingsToFileDebounced(id.value, name.value, klona(new_settings));
	}, { deep: true });
	return {
		id: readonly(id),
		name: readonly(name),
		settings
	};
});
// src/store/audio.ts
function createAudioStore(type) {
	return defineStore(`${type}_audio`, () => {
		const global_settings = useGlobalSettingsStore();
		const src = ref("");
		const playing = ref(false);
		const progress = ref(0);
		const settings = computed({
			get: () => global_settings.settings.audio[type],
			set: (value) => {
				global_settings.settings.audio[type] = value;
			}
		});
		const { enabled, mode, muted, volume } = toRefs(settings.value);
		watchDebounced(volume, (new_volume) => {
			if (new_volume > 0 && muted.value) muted.value = false;
			else if (new_volume === 0 && !muted.value) muted.value = true;
		}, { debounce: 100 });
		const chat_settings = useChatSettingsStore();
		const playlist = ref(chat_settings.settings[type]);
		watch(() => chat_settings.id, () => {
			src.value = "";
			playing.value = false;
			progress.value = 0;
			playlist.value = chat_settings.settings[type];
		});
		watch(playlist, () => {
			chat_settings.settings[type] = playlist.value;
		});
		return {
			src,
			playing,
			progress,
			enabled,
			mode,
			muted,
			volume,
			playlist
		};
	});
}
var useBgmAudioStore = createAudioStore("bgm");
var useAmbientAudioStore = createAudioStore("ambient");
// src/function/audio.ts
function get_store_by_type(type) {
	switch (type) {
		case "bgm": return useBgmAudioStore();
		case "ambient": return useAmbientAudioStore();
	}
}
function handle_url_to_title(url) {
	return url.split("/").at(-1)?.split(".").at(0) || url;
}
function playAudio(type, audio) {
	const store = get_store_by_type(type);
	audio.title = audio.title || handle_url_to_title(audio.url);
	const existing = store.playlist.find((item) => item.title === audio.title || item.url === audio.url);
	if (!existing) store.playlist.push(audio);
	else {
		existing.title = audio.title;
		existing.url = audio.url;
	}
	store.src = audio.url;
	store.progress = 0;
	store.playing = false;
	store.playing = true;
}
function pauseAudio(type) {
	const store = get_store_by_type(type);
	store.playing = false;
}
function getAudioList(type) {
	return klona(get_store_by_type(type).playlist);
}
function replaceAudioList(type, audio_list) {
	const store = get_store_by_type(type);
	store.playlist = audio_list.map((item) => ({
		title: item.title || handle_url_to_title(item.url),
		url: item.url
	}));
}
function appendAudioList(type, audio_list) {
	get_store_by_type(type).playlist.push(...audio_list.map((item) => ({
		title: item.title || handle_url_to_title(item.url),
		url: item.url
	})));
}
function getAudioSettings(type) {
	const store = get_store_by_type(type);
	return klona(_.pick(store, [
		"enabled",
		"mode",
		"muted",
		"volume"
	]));
}
/**
* 获取当前播放的音频信息: 链接、标题、是否在播、进度
* @param type 背景音乐 ('bgm') 或音效 ('ambient')
* @returns 当前音频信息; 未选中曲目时 `src`/`title` 为空字符串
*/
function getCurrentAudio(type) {
	const store = get_store_by_type(type);
	const current = store.playlist.find((item) => item.url === store.src);
	return {
		src: store.src,
		title: current?.title ?? "",
		playing: store.playing,
		progress: store.progress
	};
}
function setAudioSettings(type, settings) {
	const store = get_store_by_type(type);
	if (settings.enabled !== void 0) store.enabled = settings.enabled;
	if (settings.mode !== void 0) store.mode = settings.mode;
	if (settings.muted !== void 0) store.muted = settings.muted;
	if (settings.volume !== void 0) store.volume = _.clamp(settings.volume, 0, 100);
}
// src/function/builtin.ts
var builtin = {
	addOneMessage,
	copyText,
	duringGenerating: () => is_send_press,
	getImageTokenCost,
	getVideoTokenCost,
	parseRegexFromString,
	promptManager,
	reloadAndRenderChatWithoutEvents,
	reloadChatWithoutEvents,
	reloadEditor,
	reloadEditorDebounced,
	renderMarkdown,
	renderPromptManager: promptManager.render,
	renderPromptManagerDebounced: promptManager.renderDebounced,
	saveSettings,
	uuidv4
};
// src/function/raw_character.ts
/**
* 判断聊天摘要是否包含可读取的文件名
*/
function hasChatFileName(data) {
	return typeof data?.file_name === "string" && data.file_name.length > 0;
}
/**
* 按文件名倒序整理聊天摘要列表
*/
function getSortedChatList(data) {
	return Object.values(data ?? {}).filter(hasChatFileName).sort((a, b) => a.file_name.localeCompare(b.file_name)).reverse();
}
/**
* 从聊天摘要解析角色名，保留旧文件名前缀兼容
*/
function getChatCharacterName(chat) {
	return chat.ch_name ?? chat.character_name ?? chat.file_name.split(" - ")[0];
}
/**
* 从聊天摘要解析头像目录，优先使用摘要携带的真实角色头像
*/
function getChatAvatarUrl(chat, ch_name) {
	return chat.avatar_url || RawCharacter.find({ name: ch_name })?.avatar || "";
}
/**
* 构造聊天详情读取请求
*/
function buildChatRequest(chat, isGroupChat) {
	if (isGroupChat) return {
		endpoint: "/api/chats/group/get",
		body: JSON.stringify({ id: chat.file_name })
	};
	const ch_name = getChatCharacterName(chat);
	return {
		endpoint: "/api/chats/get",
		body: JSON.stringify({
			ch_name,
			file_name: chat.file_name.replace(".jsonl", ""),
			avatar_url: getChatAvatarUrl(chat, ch_name)
		})
	};
}
/**
* 为聊天摘要补充详情读取所需的角色定位字段。
*/
function attachCharacterToChats(chats, character) {
	return chats.map((chat) => ({
		...chat,
		ch_name: character.getCardData().name,
		avatar_url: character.getAvatarId()
	}));
}
var RawCharacter = class {
	character_data;
	constructor(character_data) {
		this.character_data = character_data;
	}
	static find({ name }) {
		const index = this.findIndex(name);
		if (index !== -1) return characters[index];
		return null;
	}
	static findIndex(name) {
		if (name === "current") return this_chid === void 0 ? -1 : Number(this_chid);
		const lowered_name = name.toLowerCase();
		return characters.findIndex((character) => character.name.toLowerCase() === lowered_name || character.avatar.toLowerCase() === lowered_name);
	}
	static async getChatsFromFiles(data, isGroupChat) {
		const chat_dict = {};
		const chat_promise = getSortedChatList(data).map(async (chat) => {
			const request = buildChatRequest(chat, isGroupChat);
			const chatResponse = await fetch(request.endpoint, {
				method: "POST",
				headers: getRequestHeaders(),
				body: request.body,
				cache: "no-cache"
			});
			if (!chatResponse.ok) return;
			const currentChat = await chatResponse.json();
			if (!Array.isArray(currentChat)) return;
			if (!isGroupChat) currentChat.shift();
			chat_dict[chat.file_name] = currentChat;
		});
		await Promise.all(chat_promise);
		return chat_dict;
	}
	getCardData() {
		return this.character_data;
	}
	getAvatarId() {
		return this.character_data.avatar || "";
	}
	getRegexScripts() {
		return this.character_data.data?.extensions?.regex_scripts || [];
	}
	getCharacterBook() {
		return this.character_data.data?.character_book || null;
	}
	getWorldName() {
		return this.character_data.data?.extensions?.world || "";
	}
};
function getCharData(name) {
	try {
		name = !name ? "current" : name;
		const characterData = RawCharacter.find({ name });
		if (!characterData) return null;
		return new RawCharacter(characterData).getCardData();
	} catch (err) {
		const error = err;
		throw Error(`获取${name ? ` '${name}' ` : "未知"}角色卡数据失败: ${error.message}`);
	}
}
function getCharAvatarPath(name) {
	name = !name ? "current" : name;
	const characterData = RawCharacter.find({ name });
	if (!characterData) return null;
	const avatarId = new RawCharacter(characterData).getAvatarId();
	const thumbnailPath = getThumbnailUrl("avatar", avatarId);
	return "/characters/" + thumbnailPath.substring(thumbnailPath.lastIndexOf("=") + 1);
}
async function getChatHistoryBrief(name) {
	name = !name ? "current" : name;
	const character_data = RawCharacter.find({ name });
	if (!character_data) return null;
	const character = new RawCharacter(character_data);
	const index = RawCharacter.findIndex(character.getAvatarId());
	if (index === -1) return null;
	return attachCharacterToChats(await getPastCharacterChats(index), character);
}
async function getChatHistoryDetail(data, isGroupChat = false) {
	return await RawCharacter.getChatsFromFiles(data, isGroupChat);
}
// src/util/message.ts
function inMessageRange(message_id) {
	return _.inRange(message_id, 0, chat.length);
}
/**
* 将向后索引转换为 message_id
* - -1 表示最新楼层
* - -2 表示倒数第二楼层
*/
function fromBackwardMessageId(backward_message_id) {
	return chat.length + backward_message_id;
}
/**
* 将向前索引或向后索引转换为 message_id
* - 向前索引: 正常的 message_id
* - 向后索引: -1 表示最新楼层, -2 表示倒数第二楼层
*/
function normalizeMessageId(forward_or_backward_message_id) {
	return forward_or_backward_message_id < 0 ? fromBackwardMessageId(forward_or_backward_message_id) : forward_or_backward_message_id;
}
function inUnnormalizedMessageRange(forward_or_backward_message_id) {
	return _.inRange(forward_or_backward_message_id, -chat.length, chat.length);
}
// src/iframe/adjust_iframe_height.js?raw
var adjust_iframe_height_default = "(function () {\n  let scheduled = false;\n\n  function measureAndPost() {\n    scheduled = false;\n    try {\n      const doc = window.document;\n      const body = doc.body;\n      const html = doc.documentElement;\n      if (!body || !html) {\n        return;\n      }\n\n      let height = 0;\n      height = body.scrollHeight;\n\n      if (!Number.isFinite(height) || height <= 0) {\n        return;\n      }\n\n      frameElement.style.height = `${height}px`;\n    } catch {\n      //\n    }\n  }\n  const throttledMeasureAndPost = _.throttle(measureAndPost, 500);\n\n  function postIframeHeight() {\n    if (scheduled) {\n      return;\n    }\n    scheduled = true;\n\n    if (typeof window.requestAnimationFrame === 'function') {\n      window.requestAnimationFrame(measureAndPost);\n    } else {\n      throttledMeasureAndPost();\n    }\n  }\n\n  function observeHeightChange() {\n    const body = document.body;\n    if (!body) {\n      return;\n    }\n\n    const resize_observer = new ResizeObserver(entries => {\n      postIframeHeight();\n    });\n    resize_observer.observe(body);\n  }\n\n  $(() => {\n    postIframeHeight();\n    observeHeightChange();\n  });\n})();\n";
// src/iframe/adjust_viewport.js?raw
var adjust_viewport_default = "$('html').css('--TH-viewport-height', `${window.parent.innerHeight}px`);\nwindow.addEventListener('message', function (event) {\n  if (event.data?.type === 'TH_UPDATE_VIEWPORT_HEIGHT') {\n    $('html').css('--TH-viewport-height', `${window.parent.innerHeight}px`);\n  }\n});\n";
// src/iframe/cleanup_protector.js?raw
var cleanup_protector_default = "// 优先获取原生的主窗口 jQuery 对象，确保在 parent 劫持之前获取，因此必为原生实例\nconst parentJQuery = window.parent.$;\n// 在 parent 被 Proxy 劫持之前保存原始引用，供 pagehide 清理时直接操作，避免经过 Proxy 的 set 拦截\nconst originalParent = window.parent;\n\nconst recordedWrites = new Map();\nlet iframeObserver;\nlet customJQuery;\nlet parentDocumentProxy;\n\n// 高性能的树遍历标记辅助函数，相比 querySelectorAll('*') 更省内存且快\nfunction markElementTree(el, id) {\n  if (!el || el.nodeType !== 1) return;\n  el.setAttribute('data-th-iframe-id', id);\n  let child = el.firstElementChild;\n  while (child) {\n    markElementTree(child, id);\n    child = child.nextElementSibling;\n  }\n}\n\n// 判断是否是 HTML 字符串的简易函数（优化了首尾空格和简单 HTML 标签检测）\nfunction isHtmlString(str) {\n  if (typeof str !== 'string') return false;\n  const trimmed = str.trim();\n  return trimmed.startsWith('<') && trimmed.endsWith('>') && trimmed.length >= 3;\n}\n\n// 转换参数中的 HTML 字符串为被标记的 jQuery 实例的通用函数\nfunction convertHtmlArguments(args, methodName) {\n  const converted = [];\n  for (let i = 0; i < args.length; i++) {\n    let arg = args[i];\n    if (isHtmlString(arg) && parentJQuery) {\n      try {\n        const jq = parentJQuery(arg);\n        jq.each(function () {\n          markElementTree(this, iframeId);\n        });\n        converted.push(jq);\n      } catch (e) {\n        converted.push(arg); // 容错，如果 parentJQuery 解析非法选择器报错，回退到原字符串参数\n      }\n    } else {\n      converted.push(arg);\n    }\n  }\n  return converted;\n}\n\n// 1. 实现 parentDocumentProxy 以拦截主窗口 document.createElement\ntry {\n  if (window.parent && window.parent.document) {\n    parentDocumentProxy = new Proxy(window.parent.document, {\n      get(target, prop) {\n        if (prop === 'createElement') {\n          return function (tagName, options) {\n            const el = target.createElement(tagName, options);\n            if (el && el.nodeType === 1) {\n              el.setAttribute('data-th-iframe-id', iframeId);\n            }\n            return el;\n          };\n        }\n        if (prop === 'createElementNS') {\n          return function (namespaceURI, qualifiedName, options) {\n            const el = target.createElementNS(namespaceURI, qualifiedName, options);\n            if (el && el.nodeType === 1) {\n              el.setAttribute('data-th-iframe-id', iframeId);\n            }\n            return el;\n          };\n        }\n        const val = Reflect.get(target, prop);\n        if (typeof val === 'function') {\n          const desc = Object.getOwnPropertyDescriptor(target, prop);\n          if (desc && !desc.writable && !desc.configurable) {\n            return val;\n          }\n          return val.bind(target);\n        }\n        return val;\n      },\n    });\n  }\n} catch (e) {\n  console.warn('[TavernHelper] Failed to setup parent document proxy:', e);\n}\n\n// 2. 劫持 window.parent 和 window.top 并指向包装版本\nlet parentProxy;\ntry {\n  parentProxy = new Proxy(window.parent, {\n    get(target, prop) {\n      if (['parent', 'window', 'self', 'top', 'globalThis'].includes(prop)) {\n        return parentProxy;\n      }\n      if (prop === '$' || prop === 'jQuery') {\n        return customJQuery || Reflect.get(target, prop);\n      }\n      if (prop === 'document') {\n        return parentDocumentProxy || Reflect.get(target, prop);\n      }\n      const val = Reflect.get(target, prop);\n      if (typeof val === 'function') {\n        const desc = Object.getOwnPropertyDescriptor(target, prop);\n        if (desc && !desc.writable && !desc.configurable) {\n          return val;\n        }\n        return val.bind(target);\n      }\n      return val;\n    },\n    set(target, prop, value) {\n      if (!recordedWrites.has(prop)) {\n        const exists = prop in target;\n        recordedWrites.set(prop, {\n          exists,\n          originalValue: exists ? target[prop] : undefined,\n        });\n      }\n      return Reflect.set(target, prop, value);\n    },\n    deleteProperty(target, prop) {\n      if (!recordedWrites.has(prop)) {\n        const exists = prop in target;\n        recordedWrites.set(prop, {\n          exists,\n          originalValue: exists ? target[prop] : undefined,\n        });\n      }\n      return Reflect.deleteProperty(target, prop);\n    },\n    has(target, prop) {\n      return Reflect.has(target, prop);\n    },\n  });\n\n  Object.defineProperty(window, 'parent', {\n    get: () => parentProxy,\n    set: () => {},\n    configurable: true,\n    enumerable: true,\n  });\n} catch (e) {\n  console.warn('[TavernHelper] Failed to setup window.parent proxy:', e);\n}\n\n// window.top 在部分浏览器/Electron 环境中受安全限制，无法被重定义，单独处理以免干扰上面的 parent 代理\ntry {\n  if (parentProxy) {\n    const topDescriptor = Object.getOwnPropertyDescriptor(window, 'top');\n    if (!topDescriptor || topDescriptor.configurable) {\n      Object.defineProperty(window, 'top', {\n        get: () => parentProxy,\n        set: () => {},\n        configurable: true,\n        enumerable: true,\n      });\n    }\n  }\n} catch (e) {\n  // window.top 在此环境中无法劫持（非致命，window.parent 的代理仍然正常工作）\n}\n\n// 3. 劫持 createElement、createElementNS 以及 cloneNode (当前 iframe)\ntry {\n  const originalCreateElement = window.document.createElement;\n  window.document.createElement = function (tagName, options) {\n    const el = originalCreateElement.call(this, tagName, options);\n    if (el && el.nodeType === 1) {\n      el.setAttribute('data-th-iframe-id', iframeId);\n    }\n    return el;\n  };\n\n  const originalCreateElementNS = window.document.createElementNS;\n  window.document.createElementNS = function (namespaceURI, qualifiedName, options) {\n    const el = originalCreateElementNS.call(this, namespaceURI, qualifiedName, options);\n    if (el && el.nodeType === 1) {\n      el.setAttribute('data-th-iframe-id', iframeId);\n    }\n    return el;\n  };\n\n  // 拦截 cloneNode，确保被复制的已追踪元素也能被继承追踪（深度克隆包含子节点）\n  const originalCloneNode = Node.prototype.cloneNode;\n  Node.prototype.cloneNode = function (deep) {\n    const cloned = originalCloneNode.call(this, deep);\n    try {\n      if (this.nodeType === 1 && this.closest(`[data-th-iframe-id=\"${CSS.escape(iframeId)}\"]`)) {\n        if (deep) {\n          markElementTree(cloned, iframeId);\n        } else {\n          cloned.setAttribute('data-th-iframe-id', iframeId);\n        }\n      }\n    } catch (e) {}\n    return cloned;\n  };\n} catch (e) {\n  console.warn('[TavernHelper] Failed to patch document.createElement & cloneNode:', e);\n}\n\n// 4. 监听 iframe 自身 document 变化捕获 innerHTML 元素，并应用过滤避免 O(N^2) 扫描\ntry {\n  iframeObserver = new MutationObserver(mutations => {\n    for (let i = 0; i < mutations.length; i++) {\n      const mutation = mutations[i];\n      for (let j = 0; j < mutation.addedNodes.length; j++) {\n        const node = mutation.addedNodes[j];\n        if (node.nodeType === 1) {\n          // Node.ELEMENT_NODE\n          if (node.closest(`[data-th-iframe-id=\"${CSS.escape(iframeId)}\"]`)) {\n            continue; // 如果该节点或其祖先刚刚已经被打上过当前 iframe 的标记，跳过以避免级联重复扫描\n          }\n          markElementTree(node, iframeId);\n        }\n      }\n    }\n  });\n  iframeObserver.observe(window.document, { childList: true, subtree: true });\n} catch (e) {\n  console.warn('[TavernHelper] Failed to init iframe MutationObserver:', e);\n}\n\n// 5. 包装 window.$ 和 window.jQuery，自定义原型链以拦截链式调用中的 DOM 插入方法\nconst localJQuery = window.$;\nconst isLocalJQueryValid = localJQuery && localJQuery !== parentJQuery;\n\nif (parentJQuery) {\n  customJQuery = function (selector, context) {\n    let result;\n    try {\n      result = parentJQuery(selector, context);\n    } catch (e) {\n      throw e; // 如果是无效选择器报错，保留 jQuery 原生异常抛出行为\n    }\n    Object.setPrototypeOf(result, customJQuery.fn);\n\n    // 如果 selector 是创建 HTML 元素的字符串，说明返回的是全新创建的元素，深度打标\n    if (isHtmlString(selector)) {\n      result.each(function () {\n        markElementTree(this, iframeId);\n      });\n    }\n    return result;\n  };\n\n  // 继承静态方法\n  Object.setPrototypeOf(customJQuery, parentJQuery);\n\n  // 创建定制原型\n  customJQuery.fn = customJQuery.prototype = Object.create(parentJQuery.fn);\n  customJQuery.fn.constructor = customJQuery;\n\n  // 重写可能由 HTML 字符串插入新节点的方法\n  const methodsToPatch = ['append', 'prepend', 'before', 'after', 'html'];\n  methodsToPatch.forEach(method => {\n    customJQuery.fn[method] = function (...args) {\n      const convertedArgs = convertHtmlArguments(args, method);\n      const originalMethod = parentJQuery.fn[method];\n      const result = originalMethod.apply(this, convertedArgs);\n      if (result && result.jquery) {\n        Object.setPrototypeOf(result, customJQuery.fn);\n      }\n      return result;\n    };\n  });\n\n  if (!isLocalJQueryValid) {\n    window.$ = window.jQuery = customJQuery;\n  }\n}\n\n$(window).on('pagehide', () => {\n  // 清理挂载在父窗口的全局变量\n  // 使用 originalParent（Proxy 劫持前保存的引用），避免 set 操作再次被 Proxy 记录到 recordedWrites\n  recordedWrites.forEach((state, prop) => {\n    try {\n      if (state.exists) {\n        originalParent[prop] = state.originalValue;\n      } else {\n        delete originalParent[prop];\n      }\n    } catch (e) {\n      console.warn(`[TavernHelper] Failed to restore/delete parent property: ${String(prop)}`, e);\n    }\n  });\n\n  // 清理父窗口中的残留 DOM 元素\n  if (iframeId) {\n    try {\n      if (window.parent && window.parent.document) {\n        const escapedId = CSS.escape(iframeId);\n        const allTagged = [...originalParent.document.querySelectorAll(`[data-th-iframe-id=\"${escapedId}\"]`)];\n\n        // 只处理最顶层的标记元素（已嵌套在将被删除元素内的就跳过，避免重复卸载和误伤）\n        const topLevelElements = allTagged.filter(\n          el => !el.parentElement?.closest(`[data-th-iframe-id=\"${escapedId}\"]`),\n        );\n\n        topLevelElements.forEach(el => {\n          el.remove();\n        });\n      }\n    } catch (e) {\n      console.warn('[TavernHelper] Failed to cleanup iframe elements:', e);\n    }\n  }\n\n  // 断开 observer\n  if (iframeObserver) {\n    iframeObserver.disconnect();\n  }\n});\n";
// src/iframe/parent_jquery.js?raw
var parent_jquery_default = "window.$ = window.parent.$;\nwindow.jQuery = window.parent.jQuery;\n";
// src/iframe/predefine.js?raw
var predefine_default = "window._ = window.parent._;\nconst iframeId = window.frameElement?.id || window.name;\nif (iframeId) {\n  // Cache the iframe id in case frameElement disappears (e.g., Firefox removing srcdoc iframes on navigation)\n  // and also put it on window.name so it survives DOM removal.\n  window.__TH_IFRAME_ID = iframeId;\n  if (!window.name) {\n    window.name = iframeId;\n  }\n}\nlet result = _(window);\nresult = result.merge(_.pick(window.parent, ['EjsTemplate', 'TavernHelper', 'YAML', 'showdown', 'toastr', 'z']));\nresult = result.merge(_.omit(_.get(window.parent, 'TavernHelper'), '_bind'));\nresult = result.merge(\n  ...Object.entries(_.get(window.parent, 'TavernHelper')._bind).map(([key, value]) => ({\n    [key.replace('_', '')]: value.bind(window),\n  })),\n);\nresult.value();\n\n// pinia 4.0.0+ 必须设置这个, 考虑到影响了非常多脚本/前端, 直接在这里统一设置\n_.set(window, '__VUE_PROD_DEVTOOLS__', true);  // 不使用 vue devtools 时不会有性能开销, 默认启用即可\n_.set(window, '__VUE_OPTIONS_API__', true);\n_.set(window, '__VUE_PROD_HYDRATION_MISMATCH_DETAILS__', false);\n\nObject.defineProperty(window, 'SillyTavern', {\n  get: () => {\n    const SillyTavern = _.get(window.parent, 'SillyTavern');\n    const getContext = () => {\n      return { ...SillyTavern.getContext(), writeExtensionField: _th_impl.writeExtensionField };\n    };\n    return { ...getContext(), getContext };\n  },\n});\n\n// 其实应该用 waitGlobalInitialized 来等待 Mvu 初始化完毕, 这里设置 window.Mvu 只是为了兼容性\nif (_.has(window.parent, 'Mvu')) {\n  Object.defineProperty(window, 'Mvu', {\n    get: () => _.get(window.parent, 'Mvu'),\n    // Mvu 脚本自己还会 `_.set()` 自己的变量, 所以这里设置一个空 set\n    set: () => {},\n    configurable: true,\n  });\n}\n\n$(window).on('pagehide', () => {\n  eventClearAll();\n});\n";
// src/iframe/script_url.ts
function createObjectURLFromScript(code) {
	return URL.createObjectURL(new Blob([code], { type: "application/javascript" }));
}
createObjectURLFromScript(adjust_iframe_height_default);
createObjectURLFromScript(adjust_viewport_default);
createObjectURLFromScript(cleanup_protector_default);
createObjectURLFromScript(parent_jquery_default);
createObjectURLFromScript(predefine_default);
// node_modules/.pnpm/is-promise@4.0.0/node_modules/is-promise/index.mjs
function isPromise(obj) {
	return !!obj && (typeof obj === "object" || typeof obj === "function") && typeof obj.then === "function";
}
// src/function/util.ts
function _reloadIframe() {
	this.location.reload();
}
function substitudeMacros(text) {
	return substituteParamsExtended(text);
}
function getLastMessageId$1() {
	return Number(substitudeMacros("{{lastMessageId}}"));
}
function errorCatched(fn) {
	const onError = (error) => {
		toastr.error(`<pre style="white-space: pre-wrap">${error.stack ? error instanceof ZodError ? [error.message, error.stack].join("\n") : error.stack : error.message}</pre>`, error.name, {
			escapeHtml: false,
			toastClass: "toastr w-fit! min-w-[300px]"
		});
		throw error;
	};
	return (...args) => {
		try {
			const result = fn(...args);
			if (isPromise(result)) return result.then(void 0, (error) => {
				onError(error);
			});
			return result;
		} catch (error) {
			return onError(error);
		}
	};
}
function _errorCatched(fn) {
	const onError = (error) => {
		const iframe_name = _getIframeName.call(this);
		const message = error.stack ? error instanceof ZodError ? [error.message, error.stack].join("\n") : error.stack : error.message;
		toastr.error(`<pre style="white-space: pre-wrap">${message}</pre>`, `[${iframe_name}] ${error.name}`, {
			escapeHtml: false,
			toastClass: "toastr w-fit! min-w-[300px]"
		});
		this._th_impl._log(iframe_name, "error", message);
		throw error;
	};
	return (...args) => {
		try {
			const result = fn(...args);
			if (isPromise(result)) return result.then(void 0, (error) => {
				onError(error);
			});
			return result;
		} catch (error) {
			return onError(error);
		}
	};
}
function _getIframeName() {
	const frameElement = this.frameElement;
	const cachedId = this.__TH_IFRAME_ID || this.name;
	if (frameElement?.id) {
		this.__TH_IFRAME_ID = frameElement.id;
		if (!this.name) this.name = frameElement.id;
		return frameElement.id;
	}
	if (cachedId) {
		if (!this.name) this.name = cachedId;
		return cachedId;
	}
	throw new TypeError("frameElement is null while resolving iframe id");
}
function _getScriptId() {
	const iframe_name = _getIframeName.call(this);
	if (!iframe_name.startsWith("TH-script--")) throw new Error("你只能在脚本 iframe 内获取 getScriptId!");
	return iframe_name.replace(/TH-script--.+--/, "");
}
function _getCurrentMessageId() {
	return getMessageId(_getIframeName.call(this));
}
function getMessageId(iframe_name) {
	const match = iframe_name.match(/^TH-message--(\d+)--\d+(_\d+)?$/);
	if (!match) throw Error(`获取 ${iframe_name} 所在楼层 id 时出错: 不要对全局脚本 iframe 调用 getMessageId!`);
	return parseInt(match[1].toString());
}
// src/panel/render/message_recovery.ts
var REQUEST_RENDER_MESSAGE_EVENT = "TH_REQUEST_RENDER_MESSAGE";
// src/store/iframe_runtimes/message.ts
function render$mes($mes, reload_memo) {
	return _($mes.toArray()).map((div) => {
		return {
			message_id: Number($(div).attr("mesid")),
			reload_memo,
			elements: $(div).find("pre").filter((_index, pre) => isFrontend($(pre).text())).filter((_index, pre) => {
				if (useGlobalSettingsStore().settings.render.allow_streaming) return $(pre).closest(".mes_text, .TH-streaming").length === 0;
				return true;
			}).map((_index, pre) => {
				const $pre = $(pre);
				const $possible_div = $pre.parent("div.TH-render");
				if ($possible_div.length > 0) return $possible_div[0];
				$pre.wrap("<div class=\"TH-render\">");
				return $pre.parent("div.TH-render")[0];
			}).toArray()
		};
	}).filter(({ elements }) => elements.length > 0).value();
}
function renderMessages(ids, reload_memo) {
	return render$mes($("#chat > .mes").filter((_index, div) => _.includes(ids, Number($(div).attr("mesid")))), reload_memo);
}
function calcToRender(depth, ignore_hidden) {
	const min_showed_message_id = Number($("#chat > .mes").first().attr("mesid"));
	if (!ignore_hidden) return _.range(depth === 0 ? min_showed_message_id : Math.max(min_showed_message_id, chat.length - depth), chat.length);
	return _(_.range(min_showed_message_id, chat.length)).filter((message_id) => !chat[message_id]?.is_system).takeRight(depth === 0 ? chat.length : depth).value();
}
function auditRuntimes(runtimes, depth, ignore_hidden) {
	const rendered = _.map(runtimes, (runtime) => runtime.message_id);
	const to_render = calcToRender(depth, ignore_hidden);
	return _.concat(_.filter(runtimes, (runtime) => _.includes(to_render, runtime.message_id)), renderMessages(_.difference(to_render, rendered), uuidv4()));
}
function rerenderAll(depth, ignore_hidden) {
	return renderMessages(calcToRender(depth, ignore_hidden), uuidv4());
}
defineStore("message_iframe_runtimes", () => {
	const global_settings = useGlobalSettingsStore();
	const runtimes = ref([]);
	watch(() => [
		global_settings.settings.render.enabled,
		global_settings.settings.render.allow_streaming,
		global_settings.settings.render.depth,
		global_settings.settings.render.depth_ignore_hidden
	], ([new_enabled, new_allow_streaming, new_depth, new_ignore_hidden]) => {
		if (new_enabled) {
			if (new_allow_streaming) runtimes.value = [];
			runtimes.value = auditRuntimes(runtimes.value, new_depth, new_ignore_hidden);
		} else runtimes.value = [];
	}, { immediate: true });
	if (global_settings.settings.render.enabled && $("#chat > .welcomePanel").length > 0) runtimes.value = rerenderAll(global_settings.settings.render.depth, global_settings.settings.render.depth_ignore_hidden);
	else setTimeout(() => {
		if (global_settings.settings.render.enabled && $("#chat > .welcomePanel").length > 0) runtimes.value = rerenderAll(global_settings.settings.render.depth, global_settings.settings.render.depth_ignore_hidden);
	}, 3e3);
	eventSource.on("chatLoaded", () => {
		if (global_settings.settings.render.enabled) runtimes.value = rerenderAll(global_settings.settings.render.depth, global_settings.settings.render.depth_ignore_hidden);
	});
	[
		event_types.CHARACTER_MESSAGE_RENDERED,
		event_types.USER_MESSAGE_RENDERED,
		event_types.MESSAGE_UPDATED,
		event_types.MESSAGE_SWIPED,
		REQUEST_RENDER_MESSAGE_EVENT
	].forEach((event) => {
		eventSource.on(event, (message_id) => {
			if (global_settings.settings.render.enabled) {
				const numbered_message_id = Number(message_id);
				runtimes.value = auditRuntimes(_.reject(runtimes.value, (runtime) => runtime.message_id === numbered_message_id), global_settings.settings.render.depth, global_settings.settings.render.depth_ignore_hidden);
			}
		});
	});
	[event_types.MESSAGE_DELETED, event_types.MORE_MESSAGES_LOADED].forEach((event) => {
		eventSource.on(event, () => {
			if (global_settings.settings.render.enabled) runtimes.value = auditRuntimes(runtimes.value, global_settings.settings.render.depth, global_settings.settings.render.depth_ignore_hidden);
		});
	});
	const reloadAll = () => {
		const reload_memo = uuidv4();
		runtimes.value = runtimes.value.map((runtime) => ({
			...runtime,
			reload_memo
		}));
	};
	return {
		runtimes,
		reloadAll
	};
});
// src/store/scripts.ts
function createScriptsStore(type) {
	return defineStore(`${type}_scripts`, () => {
		let enabled;
		let script_trees;
		let source;
		switch (type) {
			case "global": {
				const store = useGlobalSettingsStore();
				enabled = computed({
					get: () => store.settings.script.enabled.global,
					set: (value) => {
						store.settings.script.enabled.global = value;
					}
				});
				script_trees = computed({
					get: () => store.settings.script.scripts,
					set: (value) => {
						store.settings.script.scripts = value;
					}
				});
				source = ref("global");
				break;
			}
			case "preset": {
				const global_store = useGlobalSettingsStore();
				const preset_store = usePresetSettingsStore();
				enabled = computed({
					get: () => preset_store.name !== void 0 && global_store.settings.script.enabled.presets.includes(preset_store.name),
					set: (value) => {
						if (preset_store.name === void 0) return;
						if (value) global_store.settings.script.enabled.presets.push(preset_store.name);
						else _.pull(global_store.settings.script.enabled.presets, preset_store.name);
					}
				});
				script_trees = computed({
					get: () => preset_store.settings.scripts,
					set: (value) => {
						preset_store.settings.scripts = value;
					}
				});
				source = computed(() => preset_store.name);
				break;
			}
			case "character": {
				const global_store = useGlobalSettingsStore();
				const character_store = useCharacterSettingsStore();
				enabled = computed({
					get: () => character_store.avatar !== void 0 && global_store.settings.script.enabled.characters.includes(character_store.avatar),
					set: (value) => {
						if (character_store.avatar === void 0) return;
						if (value) global_store.settings.script.enabled.characters.push(character_store.avatar);
						else _.pull(global_store.settings.script.enabled.characters, character_store.avatar);
					}
				});
				script_trees = computed({
					get: () => character_store.settings.scripts,
					set: (value) => {
						character_store.settings.scripts = value;
					}
				});
				source = computed(() => character_store.avatar ?? "unknown");
				break;
			}
		}
		const enabled_scripts = computed(() => {
			return enabled.value ? _(script_trees.value).filter((item) => item.enabled).flatMap((item) => {
				return isScript(item) ? item : item.scripts.filter((script) => script.enabled);
			}).value() : [];
		});
		return {
			enabled,
			script_trees,
			source,
			enabled_scripts
		};
	});
}
var useGlobalScriptsStore = createScriptsStore("global");
var useCharacterScriptsStore = createScriptsStore("character");
var usePresetScriptsStore = createScriptsStore("preset");
function getScriptsStoreByType(target) {
	switch (target) {
		case "global": return useGlobalScriptsStore();
		case "character": return useCharacterScriptsStore();
		case "preset": return usePresetScriptsStore();
	}
}
// src/store/iframe_runtimes/script.ts
function getButtonId(script_id, button_name) {
	return `${script_id}_${getStringHash(button_name)}`;
}
var useScriptIframeRuntimesStore = defineStore("script_iframe_runtimes", () => {
	const global_settings = useGlobalSettingsStore();
	const global_scripts = useGlobalScriptsStore();
	const preset_scripts = usePresetScriptsStore();
	const character_scripts = useCharacterScriptsStore();
	const reload_memos = ref({});
	const reload = (id) => {
		_.set(reload_memos.value, id, uuidv4());
	};
	const reloadAll = () => {
		const reload_memo = uuidv4();
		reload_memos.value = Object.fromEntries(runtimes.value.map((runtime) => [runtime.id, reload_memo]));
	};
	const enabled_scripts_with_source = computed(() => {
		return global_settings.app_ready ? _([
			global_scripts,
			preset_scripts,
			character_scripts
		]).flatMap((store) => store.enabled_scripts.map((script) => ({
			source: store.source,
			script
		}))).value() : [];
	});
	const runtimes = computed(() => {
		return _(enabled_scripts_with_source.value).map((item) => ({
			id: item.script.id,
			name: item.script.name,
			source: unref(item.source),
			content: item.script.content,
			reload_memo: _.get(reload_memos.value, item.script.id, "")
		})).sortBy((script) => script.id).value();
	});
	const button_map = computed(() => {
		return _(enabled_scripts_with_source.value).filter((item) => item.script.button.enabled && item.script.button.buttons.some((button) => button.visible)).map((item) => [item.script.id, item.script.button.buttons.filter((button) => button.visible).map((button) => ({
			button_id: getButtonId(item.script.id, button.name),
			button_name: button.name
		}))]).fromPairs().value();
	});
	const get = (script_id) => {
		return enabled_scripts_with_source.value.find((item) => item.script.id === script_id)?.script;
	};
	return {
		reload_memos: readonly(reload_memos),
		enabled_scripts: enabled_scripts_with_source,
		runtimes,
		button_map,
		get,
		reload,
		reloadAll
	};
});
// src/store/variable_schemas.ts
var useVariableSchemasStore = defineStore("variable_schemas", () => {
	const global = ref();
	const preset = ref();
	const preset_settings = usePresetSettingsStore();
	watch(() => preset_settings.name, () => {
		preset.value = void 0;
	});
	const character = ref();
	const chat = ref();
	const message = ref();
	const character_settings = useCharacterSettingsStore();
	watch(() => character_settings.avatar, () => {
		character.value = void 0;
		chat.value = void 0;
		message.value = void 0;
	});
	return {
		global,
		preset,
		character,
		chat,
		message
	};
});
// src/function/variables.ts
function registerVariableSchema(schema, { type }) {
	const store = useVariableSchemasStore();
	switch (type) {
		case "global":
			store.global = schema;
			break;
		case "preset":
			store.preset = schema;
			break;
		case "character":
			store.character = schema;
			break;
		case "chat":
			store.chat = schema;
			break;
		case "message": store.message = schema;
	}
}
function get_variables_without_clone(option) {
	switch (option.type) {
		case "message": {
			const normalized_message_id = option.message_id === void 0 || option.message_id === "latest" ? -1 : option.message_id;
			if (!_.inRange(normalized_message_id, -chat.length, chat.length)) throw Error(`提供的消息楼层号 '${option.message_id}' 超出了范围 [${-chat.length}, ${chat.length})`);
			let chat_message;
			if (option.message_id === void 0 || option.message_id === "latest") chat_message = chat.filter((chat_message) => !chat_message.is_system).at(normalized_message_id);
			else chat_message = chat.at(normalized_message_id);
			return chat_message?.variables?.[chat_message?.swipe_id ?? 0] ?? {};
		}
		case "chat": return _.get(chat_metadata, "variables", {});
		case "character": return useCharacterSettingsStore().settings.variables;
		case "preset": return usePresetSettingsStore().settings.variables;
		case "global": return extension_settings.variables.global;
		case "script":
			if (!option.script_id) throw Error("获取变量失败, 未指定 script_id");
			return useScriptIframeRuntimesStore().get(option.script_id)?.data ?? {};
		case "extension": return _.get(extension_settings, option.extension_id, {});
	}
}
function getVariables(option = { type: "chat" }) {
	return klona(get_variables_without_clone(option));
}
function _getVariables(option = { type: "chat" }) {
	return option.type === "script" ? getVariables({
		type: "script",
		script_id: _getScriptId.call(this)
	}) : getVariables(option);
}
function _getAllVariables() {
	const is_message_iframe = _getIframeName.call(this).startsWith("TH-message");
	let result = _({});
	result = result.assign(get_variables_without_clone({ type: "global" }), get_variables_without_clone({ type: "character" }));
	if (!is_message_iframe) result = result.assign(get_variables_without_clone({
		type: "script",
		script_id: _getScriptId.call(this)
	}));
	result = result.assign(get_variables_without_clone({ type: "chat" }));
	if (is_message_iframe) result = result.assign(...chat.slice(0, _getCurrentMessageId.call(this) + 1).map((chat_message) => chat_message?.variables?.[chat_message?.swipe_id ?? 0]));
	return klona(result.value());
}
function replaceVariables(variables, option = { type: "chat" }) {
	switch (option.type) {
		case "message": {
			option.message_id = option.message_id === void 0 || option.message_id === "latest" ? -1 : option.message_id;
			if (!_.inRange(option.message_id, -chat.length, chat.length)) throw Error(`提供的消息楼层号 '${option.message_id}' 超出了范围 (${-chat.length}, ${chat.length})`);
			const chat_message = chat.at(option.message_id);
			if (!_.has(chat_message, "variables")) _.set(chat_message, "variables", _.times(chat_message.swipes?.length ?? 1, _.constant({})));
			if (_.isPlainObject(_.get(chat_message, "variables"))) _.set(chat_message, "variables", _.range(0, chat_message.swipes?.length ?? 1).map((i) => chat_message.variables[i] ?? {}));
			_.set(chat_message, ["variables", _.get(chat_message, "swipe_id", 0)], variables);
			saveChatConditionalDebounced();
			break;
		}
		case "chat":
			_.set(chat_metadata, "variables", variables);
			saveMetadataDebounced();
			break;
		case "character": {
			const store = useCharacterSettingsStore();
			if (store.avatar === void 0) throw new Error("当前没有打开角色卡，保存角色卡变量失败");
			toRef(store.settings, "variables").value = variables;
			break;
		}
		case "preset": {
			const store = usePresetSettingsStore();
			toRef(store.settings, "variables").value = variables;
			break;
		}
		case "global":
			_.set(extension_settings.variables, "global", variables);
			saveSettingsDebounced();
			break;
		case "script": {
			if (!option.script_id) throw Error("保存变量失败, 未指定 script_id");
			const script = useScriptIframeRuntimesStore().get(option.script_id);
			if (!script) return;
			script.data = variables;
			break;
		}
		case "extension":
			_.set(extension_settings, option.extension_id, variables);
			saveSettingsDebounced();
	}
}
function _replaceVariables(variables, option = { type: "chat" }) {
	return option.type === "script" ? replaceVariables(variables, {
		type: "script",
		script_id: _getScriptId.call(this)
	}) : replaceVariables(variables, option);
}
function updateVariablesWith(updater, option = { type: "chat" }) {
	const result = updater(getVariables(option));
	if (isPromise(result)) return result.then((result) => {
		replaceVariables(result, option);
		return result;
	});
	replaceVariables(result, option);
	return result;
}
function _updateVariablesWith(updater, option = { type: "chat" }) {
	return option.type === "script" ? updateVariablesWith(updater, {
		type: "script",
		script_id: _getScriptId.call(this)
	}) : updateVariablesWith(updater, option);
}
function insertOrAssignVariables(variables, option = { type: "chat" }) {
	return updateVariablesWith((old_variables) => _.mergeWith(old_variables, variables, (_lhs, rhs) => _.isArray(rhs) ? rhs : void 0), option);
}
function _insertOrAssignVariables(variables, option = { type: "chat" }) {
	return option.type === "script" ? insertOrAssignVariables(variables, {
		type: "script",
		script_id: _getScriptId.call(this)
	}) : insertOrAssignVariables(variables, option);
}
function insertVariables(variables, option = { type: "chat" }) {
	return updateVariablesWith((old_variables) => _.mergeWith({}, variables, old_variables, (_lhs, rhs) => _.isArray(rhs) ? rhs : void 0), option);
}
function _insertVariables(variables, option = { type: "chat" }) {
	return option.type === "script" ? insertVariables(variables, {
		type: "script",
		script_id: _getScriptId.call(this)
	}) : insertVariables(variables, option);
}
function deleteVariable(variable_path, option = { type: "chat" }) {
	let delete_occurred = false;
	return {
		variables: updateVariablesWith((old_variables) => {
			delete_occurred = _.unset(old_variables, variable_path);
			return old_variables;
		}, option),
		delete_occurred
	};
}
function _deleteVariable(variable_path, option = { type: "chat" }) {
	return option.type === "script" ? deleteVariable(variable_path, {
		type: "script",
		script_id: _getScriptId.call(this)
	}) : deleteVariable(variable_path, option);
}
// node_modules/.pnpm/lodash-omitdeep@1.11.0_lodash@4.18.1/node_modules/lodash-omitdeep/dist/esm/index.mjs
var import_lodash = /* @__PURE__ */ __toESM((/* @__PURE__ */ __commonJSMin(((exports, module) => {
	module.exports = _;
})))(), 1);
/**
* The opposite of `_.pickBy`; this method creates an object composed of the
* own and inherited enumerable properties of `object` that `predicate`
* doesn't return truthy for.
*
* @category Function
* @param object The source object.
* @param [predicate] The function invoked per property.
* @returns Returns the new object.
* @example
*
* const object = { 'a': 1, 'b': null, 'c': { 'a': 1, 'b': null } };
*
* omitByDeep(object, _.isNil);
* // => { 'a': 1, 'c': { 'a': 1 } }
*/
var omitDeepBy = (object, cb) => {
	function omitByDeepByOnOwnProps(object) {
		if (!Array.isArray(object) && !import_lodash.default.isPlainObject(object)) return object;
		if (Array.isArray(object)) return object.map((element) => omitDeepBy(element, cb));
		const temp = {};
		for (const [key, value] of Object.entries(object)) temp[key] = omitDeepBy(value, cb);
		return import_lodash.default.omitBy(temp, cb);
	}
	return omitByDeepByOnOwnProps(object);
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/identity.js
var ALIAS = Symbol.for("yaml.alias");
var DOC = Symbol.for("yaml.document");
var MAP = Symbol.for("yaml.map");
var PAIR = Symbol.for("yaml.pair");
var SCALAR$1 = Symbol.for("yaml.scalar");
var SEQ = Symbol.for("yaml.seq");
var NODE_TYPE = Symbol.for("yaml.node.type");
var isAlias = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === ALIAS;
var isDocument = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === DOC;
var isMap = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === MAP;
var isPair = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === PAIR;
var isScalar$1 = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === SCALAR$1;
var isSeq = (node) => !!node && typeof node === "object" && node[NODE_TYPE] === SEQ;
function isCollection$1(node) {
	if (node && typeof node === "object") switch (node[NODE_TYPE]) {
		case MAP:
		case SEQ: return true;
	}
	return false;
}
function isNode(node) {
	if (node && typeof node === "object") switch (node[NODE_TYPE]) {
		case ALIAS:
		case MAP:
		case SCALAR$1:
		case SEQ: return true;
	}
	return false;
}
var hasAnchor = (node) => (isScalar$1(node) || isCollection$1(node)) && !!node.anchor;
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/visit.js
var BREAK$1 = Symbol("break visit");
var SKIP$1 = Symbol("skip children");
var REMOVE$1 = Symbol("remove node");
/**
* Apply a visitor to an AST node or document.
*
* Walks through the tree (depth-first) starting from `node`, calling a
* `visitor` function with three arguments:
*   - `key`: For sequence values and map `Pair`, the node's index in the
*     collection. Within a `Pair`, `'key'` or `'value'`, correspondingly.
*     `null` for the root node.
*   - `node`: The current node.
*   - `path`: The ancestry of the current node.
*
* The return value of the visitor may be used to control the traversal:
*   - `undefined` (default): Do nothing and continue
*   - `visit.SKIP`: Do not visit the children of this node, continue with next
*     sibling
*   - `visit.BREAK`: Terminate traversal completely
*   - `visit.REMOVE`: Remove the current node, then continue with the next one
*   - `Node`: Replace the current node, then continue by visiting it
*   - `number`: While iterating the items of a sequence or map, set the index
*     of the next step. This is useful especially if the index of the current
*     node has changed.
*
* If `visitor` is a single function, it will be called with all values
* encountered in the tree, including e.g. `null` values. Alternatively,
* separate visitor functions may be defined for each `Map`, `Pair`, `Seq`,
* `Alias` and `Scalar` node. To define the same visitor function for more than
* one node type, use the `Collection` (map and seq), `Value` (map, seq & scalar)
* and `Node` (alias, map, seq & scalar) targets. Of all these, only the most
* specific defined one will be used for each node.
*/
function visit$1(node, visitor) {
	const visitor_ = initVisitor(visitor);
	if (isDocument(node)) {
		if (visit_(null, node.contents, visitor_, Object.freeze([node])) === REMOVE$1) node.contents = null;
	} else visit_(null, node, visitor_, Object.freeze([]));
}
/** Terminate visit traversal completely */
visit$1.BREAK = BREAK$1;
/** Do not visit the children of the current node */
visit$1.SKIP = SKIP$1;
/** Remove the current node */
visit$1.REMOVE = REMOVE$1;
function visit_(key, node, visitor, path) {
	const ctrl = callVisitor(key, node, visitor, path);
	if (isNode(ctrl) || isPair(ctrl)) {
		replaceNode(key, path, ctrl);
		return visit_(key, ctrl, visitor, path);
	}
	if (typeof ctrl !== "symbol") {
		if (isCollection$1(node)) {
			path = Object.freeze(path.concat(node));
			for (let i = 0; i < node.items.length; ++i) {
				const ci = visit_(i, node.items[i], visitor, path);
				if (typeof ci === "number") i = ci - 1;
				else if (ci === BREAK$1) return BREAK$1;
				else if (ci === REMOVE$1) {
					node.items.splice(i, 1);
					i -= 1;
				}
			}
		} else if (isPair(node)) {
			path = Object.freeze(path.concat(node));
			const ck = visit_("key", node.key, visitor, path);
			if (ck === BREAK$1) return BREAK$1;
			else if (ck === REMOVE$1) node.key = null;
			const cv = visit_("value", node.value, visitor, path);
			if (cv === BREAK$1) return BREAK$1;
			else if (cv === REMOVE$1) node.value = null;
		}
	}
	return ctrl;
}
/**
* Apply an async visitor to an AST node or document.
*
* Walks through the tree (depth-first) starting from `node`, calling a
* `visitor` function with three arguments:
*   - `key`: For sequence values and map `Pair`, the node's index in the
*     collection. Within a `Pair`, `'key'` or `'value'`, correspondingly.
*     `null` for the root node.
*   - `node`: The current node.
*   - `path`: The ancestry of the current node.
*
* The return value of the visitor may be used to control the traversal:
*   - `Promise`: Must resolve to one of the following values
*   - `undefined` (default): Do nothing and continue
*   - `visit.SKIP`: Do not visit the children of this node, continue with next
*     sibling
*   - `visit.BREAK`: Terminate traversal completely
*   - `visit.REMOVE`: Remove the current node, then continue with the next one
*   - `Node`: Replace the current node, then continue by visiting it
*   - `number`: While iterating the items of a sequence or map, set the index
*     of the next step. This is useful especially if the index of the current
*     node has changed.
*
* If `visitor` is a single function, it will be called with all values
* encountered in the tree, including e.g. `null` values. Alternatively,
* separate visitor functions may be defined for each `Map`, `Pair`, `Seq`,
* `Alias` and `Scalar` node. To define the same visitor function for more than
* one node type, use the `Collection` (map and seq), `Value` (map, seq & scalar)
* and `Node` (alias, map, seq & scalar) targets. Of all these, only the most
* specific defined one will be used for each node.
*/
async function visitAsync(node, visitor) {
	const visitor_ = initVisitor(visitor);
	if (isDocument(node)) {
		if (await visitAsync_(null, node.contents, visitor_, Object.freeze([node])) === REMOVE$1) node.contents = null;
	} else await visitAsync_(null, node, visitor_, Object.freeze([]));
}
/** Terminate visit traversal completely */
visitAsync.BREAK = BREAK$1;
/** Do not visit the children of the current node */
visitAsync.SKIP = SKIP$1;
/** Remove the current node */
visitAsync.REMOVE = REMOVE$1;
async function visitAsync_(key, node, visitor, path) {
	const ctrl = await callVisitor(key, node, visitor, path);
	if (isNode(ctrl) || isPair(ctrl)) {
		replaceNode(key, path, ctrl);
		return visitAsync_(key, ctrl, visitor, path);
	}
	if (typeof ctrl !== "symbol") {
		if (isCollection$1(node)) {
			path = Object.freeze(path.concat(node));
			for (let i = 0; i < node.items.length; ++i) {
				const ci = await visitAsync_(i, node.items[i], visitor, path);
				if (typeof ci === "number") i = ci - 1;
				else if (ci === BREAK$1) return BREAK$1;
				else if (ci === REMOVE$1) {
					node.items.splice(i, 1);
					i -= 1;
				}
			}
		} else if (isPair(node)) {
			path = Object.freeze(path.concat(node));
			const ck = await visitAsync_("key", node.key, visitor, path);
			if (ck === BREAK$1) return BREAK$1;
			else if (ck === REMOVE$1) node.key = null;
			const cv = await visitAsync_("value", node.value, visitor, path);
			if (cv === BREAK$1) return BREAK$1;
			else if (cv === REMOVE$1) node.value = null;
		}
	}
	return ctrl;
}
function initVisitor(visitor) {
	if (typeof visitor === "object" && (visitor.Collection || visitor.Node || visitor.Value)) return Object.assign({
		Alias: visitor.Node,
		Map: visitor.Node,
		Scalar: visitor.Node,
		Seq: visitor.Node
	}, visitor.Value && {
		Map: visitor.Value,
		Scalar: visitor.Value,
		Seq: visitor.Value
	}, visitor.Collection && {
		Map: visitor.Collection,
		Seq: visitor.Collection
	}, visitor);
	return visitor;
}
function callVisitor(key, node, visitor, path) {
	if (typeof visitor === "function") return visitor(key, node, path);
	if (isMap(node)) return visitor.Map?.(key, node, path);
	if (isSeq(node)) return visitor.Seq?.(key, node, path);
	if (isPair(node)) return visitor.Pair?.(key, node, path);
	if (isScalar$1(node)) return visitor.Scalar?.(key, node, path);
	if (isAlias(node)) return visitor.Alias?.(key, node, path);
}
function replaceNode(key, path, node) {
	const parent = path[path.length - 1];
	if (isCollection$1(parent)) parent.items[key] = node;
	else if (isPair(parent)) {
		if (key === "key") parent.key = node;
		else parent.value = node;
	} else if (isDocument(parent)) parent.contents = node;
	else {
		const pt = isAlias(parent) ? "alias" : "scalar";
		throw new Error(`Cannot replace node with ${pt} parent`);
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/doc/directives.js
var escapeChars = {
	"!": "%21",
	",": "%2C",
	"[": "%5B",
	"]": "%5D",
	"{": "%7B",
	"}": "%7D"
};
var escapeTagName = (tn) => tn.replace(/[!,[\]{}]/g, (ch) => escapeChars[ch]);
var Directives = class Directives {
	constructor(yaml, tags) {
		/**
		* The directives-end/doc-start marker `---`. If `null`, a marker may still be
		* included in the document's stringified representation.
		*/
		this.docStart = null;
		/** The doc-end marker `...`.  */
		this.docEnd = false;
		this.yaml = Object.assign({}, Directives.defaultYaml, yaml);
		this.tags = Object.assign({}, Directives.defaultTags, tags);
	}
	clone() {
		const copy = new Directives(this.yaml, this.tags);
		copy.docStart = this.docStart;
		return copy;
	}
	/**
	* During parsing, get a Directives instance for the current document and
	* update the stream state according to the current version's spec.
	*/
	atDocument() {
		const res = new Directives(this.yaml, this.tags);
		switch (this.yaml.version) {
			case "1.1":
				this.atNextDocument = true;
				break;
			case "1.2":
				this.atNextDocument = false;
				this.yaml = {
					explicit: Directives.defaultYaml.explicit,
					version: "1.2"
				};
				this.tags = Object.assign({}, Directives.defaultTags);
		}
		return res;
	}
	/**
	* @param onError - May be called even if the action was successful
	* @returns `true` on success
	*/
	add(line, onError) {
		if (this.atNextDocument) {
			this.yaml = {
				explicit: Directives.defaultYaml.explicit,
				version: "1.1"
			};
			this.tags = Object.assign({}, Directives.defaultTags);
			this.atNextDocument = false;
		}
		const parts = line.trim().split(/[ \t]+/);
		const name = parts.shift();
		switch (name) {
			case "%TAG": {
				if (parts.length !== 2) {
					onError(0, "%TAG directive should contain exactly two parts");
					if (parts.length < 2) return false;
				}
				const [handle, prefix] = parts;
				this.tags[handle] = prefix;
				return true;
			}
			case "%YAML": {
				this.yaml.explicit = true;
				if (parts.length !== 1) {
					onError(0, "%YAML directive should contain exactly one part");
					return false;
				}
				const [version] = parts;
				if (version === "1.1" || version === "1.2") {
					this.yaml.version = version;
					return true;
				} else {
					const isValid = /^\d+\.\d+$/.test(version);
					onError(6, `Unsupported YAML version ${version}`, isValid);
					return false;
				}
			}
			default:
				onError(0, `Unknown directive ${name}`, true);
				return false;
		}
	}
	/**
	* Resolves a tag, matching handles to those defined in %TAG directives.
	*
	* @returns Resolved tag, which may also be the non-specific tag `'!'` or a
	*   `'!local'` tag, or `null` if unresolvable.
	*/
	tagName(source, onError) {
		if (source === "!") return "!";
		if (source[0] !== "!") {
			onError(`Not a valid tag: ${source}`);
			return null;
		}
		if (source[1] === "<") {
			const verbatim = source.slice(2, -1);
			if (verbatim === "!" || verbatim === "!!") {
				onError(`Verbatim tags aren't resolved, so ${source} is invalid.`);
				return null;
			}
			if (source[source.length - 1] !== ">") onError("Verbatim tags must end with a >");
			return verbatim;
		}
		const [, handle, suffix] = source.match(/^(.*!)([^!]*)$/s);
		if (!suffix) onError(`The ${source} tag has no suffix`);
		const prefix = this.tags[handle];
		if (prefix) try {
			return prefix + decodeURIComponent(suffix);
		} catch (error) {
			onError(String(error));
			return null;
		}
		if (handle === "!") return source;
		onError(`Could not resolve tag: ${source}`);
		return null;
	}
	/**
	* Given a fully resolved tag, returns its printable string form,
	* taking into account current tag prefixes and defaults.
	*/
	tagString(tag) {
		for (const [handle, prefix] of Object.entries(this.tags)) if (tag.startsWith(prefix)) return handle + escapeTagName(tag.substring(prefix.length));
		return tag[0] === "!" ? tag : `!<${tag}>`;
	}
	toString(doc) {
		const lines = this.yaml.explicit ? [`%YAML ${this.yaml.version || "1.2"}`] : [];
		const tagEntries = Object.entries(this.tags);
		let tagNames;
		if (doc && tagEntries.length > 0 && isNode(doc.contents)) {
			const tags = {};
			visit$1(doc.contents, (_key, node) => {
				if (isNode(node) && node.tag) tags[node.tag] = true;
			});
			tagNames = Object.keys(tags);
		} else tagNames = [];
		for (const [handle, prefix] of tagEntries) {
			if (handle === "!!" && prefix === "tag:yaml.org,2002:") continue;
			if (!doc || tagNames.some((tn) => tn.startsWith(prefix))) lines.push(`%TAG ${handle} ${prefix}`);
		}
		return lines.join("\n");
	}
};
Directives.defaultYaml = {
	explicit: false,
	version: "1.2"
};
Directives.defaultTags = { "!!": "tag:yaml.org,2002:" };
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/doc/anchors.js
/**
* Verify that the input string is a valid anchor.
*
* Will throw on errors.
*/
function anchorIsValid(anchor) {
	if (/[\x00-\x19\s,[\]{}]/.test(anchor)) {
		const msg = `Anchor must not contain whitespace or control characters: ${JSON.stringify(anchor)}`;
		throw new Error(msg);
	}
	return true;
}
function anchorNames(root) {
	const anchors = /* @__PURE__ */ new Set();
	visit$1(root, { Value(_key, node) {
		if (node.anchor) anchors.add(node.anchor);
	} });
	return anchors;
}
/** Find a new anchor name with the given `prefix` and a one-indexed suffix. */
function findNewAnchor(prefix, exclude) {
	for (let i = 1;; ++i) {
		const name = `${prefix}${i}`;
		if (!exclude.has(name)) return name;
	}
}
function createNodeAnchors(doc, prefix) {
	const aliasObjects = [];
	const sourceObjects = /* @__PURE__ */ new Map();
	let prevAnchors = null;
	return {
		onAnchor: (source) => {
			aliasObjects.push(source);
			prevAnchors ?? (prevAnchors = anchorNames(doc));
			const anchor = findNewAnchor(prefix, prevAnchors);
			prevAnchors.add(anchor);
			return anchor;
		},
		/**
		* With circular references, the source node is only resolved after all
		* of its child nodes are. This is why anchors are set only after all of
		* the nodes have been created.
		*/
		setAnchors: () => {
			for (const source of aliasObjects) {
				const ref = sourceObjects.get(source);
				if (typeof ref === "object" && ref.anchor && (isScalar$1(ref.node) || isCollection$1(ref.node))) ref.node.anchor = ref.anchor;
				else {
					const error = /* @__PURE__ */ new Error("Failed to resolve repeated object (this should not happen)");
					error.source = source;
					throw error;
				}
			}
		},
		sourceObjects
	};
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/doc/applyReviver.js
/**
* Applies the JSON.parse reviver algorithm as defined in the ECMA-262 spec,
* in section 24.5.1.1 "Runtime Semantics: InternalizeJSONProperty" of the
* 2021 edition: https://tc39.es/ecma262/#sec-json.parse
*
* Includes extensions for handling Map and Set objects.
*/
function applyReviver(reviver, obj, key, val) {
	if (val && typeof val === "object") {
		if (Array.isArray(val)) for (let i = 0, len = val.length; i < len; ++i) {
			const v0 = val[i];
			const v1 = applyReviver(reviver, val, String(i), v0);
			if (v1 === void 0) delete val[i];
			else if (v1 !== v0) val[i] = v1;
		}
		else if (val instanceof Map) for (const k of Array.from(val.keys())) {
			const v0 = val.get(k);
			const v1 = applyReviver(reviver, val, k, v0);
			if (v1 === void 0) val.delete(k);
			else if (v1 !== v0) val.set(k, v1);
		}
		else if (val instanceof Set) for (const v0 of Array.from(val)) {
			const v1 = applyReviver(reviver, val, v0, v0);
			if (v1 === void 0) val.delete(v0);
			else if (v1 !== v0) {
				val.delete(v0);
				val.add(v1);
			}
		}
		else for (const [k, v0] of Object.entries(val)) {
			const v1 = applyReviver(reviver, val, k, v0);
			if (v1 === void 0) delete val[k];
			else if (v1 !== v0) val[k] = v1;
		}
	}
	return reviver.call(obj, key, val);
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/toJS.js
/**
* Recursively convert any node or its contents to native JavaScript
*
* @param value - The input value
* @param arg - If `value` defines a `toJSON()` method, use this
*   as its first argument
* @param ctx - Conversion context, originally set in Document#toJS(). If
*   `{ keep: true }` is not set, output should be suitable for JSON
*   stringification.
*/
function toJS(value, arg, ctx) {
	if (Array.isArray(value)) return value.map((v, i) => toJS(v, String(i), ctx));
	if (value && typeof value.toJSON === "function") {
		if (!ctx || !hasAnchor(value)) return value.toJSON(arg, ctx);
		const data = {
			aliasCount: 0,
			count: 1,
			res: void 0
		};
		ctx.anchors.set(value, data);
		ctx.onCreate = (res) => {
			data.res = res;
			delete ctx.onCreate;
		};
		const res = value.toJSON(arg, ctx);
		if (ctx.onCreate) ctx.onCreate(res);
		return res;
	}
	if (typeof value === "bigint" && !ctx?.keep) return Number(value);
	return value;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/Node.js
var NodeBase = class {
	constructor(type) {
		Object.defineProperty(this, NODE_TYPE, { value: type });
	}
	/** Create a copy of this node.  */
	clone() {
		const copy = Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this));
		if (this.range) copy.range = this.range.slice();
		return copy;
	}
	/** A plain JavaScript representation of this node. */
	toJS(doc, { mapAsMap, maxAliasCount, onAnchor, reviver } = {}) {
		if (!isDocument(doc)) throw new TypeError("A document argument is required");
		const ctx = {
			anchors: /* @__PURE__ */ new Map(),
			doc,
			keep: true,
			mapAsMap: mapAsMap === true,
			mapKeyWarned: false,
			maxAliasCount: typeof maxAliasCount === "number" ? maxAliasCount : 100
		};
		const res = toJS(this, "", ctx);
		if (typeof onAnchor === "function") for (const { count, res } of ctx.anchors.values()) onAnchor(res, count);
		return typeof reviver === "function" ? applyReviver(reviver, { "": res }, "", res) : res;
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/Alias.js
var Alias = class extends NodeBase {
	constructor(source) {
		super(ALIAS);
		this.source = source;
		Object.defineProperty(this, "tag", { set() {
			throw new Error("Alias nodes cannot have tags");
		} });
	}
	/**
	* Resolve the value of this alias within `doc`, finding the last
	* instance of the `source` anchor before this node.
	*/
	resolve(doc, ctx) {
		if (ctx?.maxAliasCount === 0) throw new ReferenceError("Alias resolution is disabled");
		let nodes;
		if (ctx?.aliasResolveCache) nodes = ctx.aliasResolveCache;
		else {
			nodes = [];
			visit$1(doc, { Node: (_key, node) => {
				if (isAlias(node) || hasAnchor(node)) nodes.push(node);
			} });
			if (ctx) ctx.aliasResolveCache = nodes;
		}
		let found = void 0;
		for (const node of nodes) {
			if (node === this) break;
			if (node.anchor === this.source) found = node;
		}
		if (found && ctx) {
			const { anchors, doc, maxAliasCount } = ctx;
			let data = anchors.get(found);
			if (!data) {
				toJS(found, null, ctx);
				data = anchors.get(found);
			}
			/* istanbul ignore if */
			if (data?.res === void 0) throw new ReferenceError("This should not happen: Alias anchor was not resolved?");
			if (maxAliasCount >= 0) {
				data.count += 1;
				if (data.aliasCount === 0) data.aliasCount = getAliasCount(doc, found, anchors);
				if (data.count * data.aliasCount > maxAliasCount) throw new ReferenceError("Excessive alias count indicates a resource exhaustion attack");
			}
		}
		return found;
	}
	toJSON(_arg, ctx) {
		if (!ctx) return { source: this.source };
		const source = this.resolve(ctx.doc, ctx);
		if (!source) {
			const msg = `Unresolved alias (the anchor must be set before the alias): ${this.source}`;
			throw new ReferenceError(msg);
		}
		return ctx.anchors.get(source).res;
	}
	toString(ctx, _onComment, _onChompKeep) {
		const src = `*${this.source}`;
		if (ctx) {
			anchorIsValid(this.source);
			if (ctx.options.verifyAliasOrder && !ctx.anchors.has(this.source)) {
				const msg = `Unresolved alias (the anchor must be set before the alias): ${this.source}`;
				throw new Error(msg);
			}
			if (ctx.implicitKey) return `${src} `;
		}
		return src;
	}
};
function getAliasCount(doc, node, anchors) {
	if (isAlias(node)) {
		const source = node.resolve(doc);
		const anchor = anchors && source && anchors.get(source);
		return anchor ? anchor.count * anchor.aliasCount : 0;
	} else if (isCollection$1(node)) {
		let count = 0;
		for (const item of node.items) {
			const c = getAliasCount(doc, item, anchors);
			if (c > count) count = c;
		}
		return count;
	} else if (isPair(node)) {
		const kc = getAliasCount(doc, node.key, anchors);
		const vc = getAliasCount(doc, node.value, anchors);
		return Math.max(kc, vc);
	}
	return 1;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/Scalar.js
var isScalarValue = (value) => !value || typeof value !== "function" && typeof value !== "object";
var Scalar = class extends NodeBase {
	constructor(value) {
		super(SCALAR$1);
		this.value = value;
	}
	toJSON(arg, ctx) {
		return ctx?.keep ? this.value : toJS(this.value, arg, ctx);
	}
	toString() {
		return String(this.value);
	}
};
Scalar.BLOCK_FOLDED = "BLOCK_FOLDED";
Scalar.BLOCK_LITERAL = "BLOCK_LITERAL";
Scalar.PLAIN = "PLAIN";
Scalar.QUOTE_DOUBLE = "QUOTE_DOUBLE";
Scalar.QUOTE_SINGLE = "QUOTE_SINGLE";
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/doc/createNode.js
var defaultTagPrefix = "tag:yaml.org,2002:";
function findTagObject(value, tagName, tags) {
	if (tagName) {
		const match = tags.filter((t) => t.tag === tagName);
		const tagObj = match.find((t) => !t.format) ?? match[0];
		if (!tagObj) throw new Error(`Tag ${tagName} not found`);
		return tagObj;
	}
	return tags.find((t) => t.identify?.(value) && !t.format);
}
function createNode(value, tagName, ctx) {
	if (isDocument(value)) value = value.contents;
	if (isNode(value)) return value;
	if (isPair(value)) {
		const map = ctx.schema[MAP].createNode?.(ctx.schema, null, ctx);
		map.items.push(value);
		return map;
	}
	if (value instanceof String || value instanceof Number || value instanceof Boolean || typeof BigInt !== "undefined" && value instanceof BigInt) value = value.valueOf();
	const { aliasDuplicateObjects, onAnchor, onTagObj, schema, sourceObjects } = ctx;
	let ref = void 0;
	if (aliasDuplicateObjects && value && typeof value === "object") {
		ref = sourceObjects.get(value);
		if (ref) {
			ref.anchor ?? (ref.anchor = onAnchor(value));
			return new Alias(ref.anchor);
		} else {
			ref = {
				anchor: null,
				node: null
			};
			sourceObjects.set(value, ref);
		}
	}
	if (tagName?.startsWith("!!")) tagName = defaultTagPrefix + tagName.slice(2);
	let tagObj = findTagObject(value, tagName, schema.tags);
	if (!tagObj) {
		if (value && typeof value.toJSON === "function") value = value.toJSON();
		if (!value || typeof value !== "object") {
			const node = new Scalar(value);
			if (ref) ref.node = node;
			return node;
		}
		tagObj = value instanceof Map ? schema[MAP] : Symbol.iterator in Object(value) ? schema[SEQ] : schema[MAP];
	}
	if (onTagObj) {
		onTagObj(tagObj);
		delete ctx.onTagObj;
	}
	const node = tagObj?.createNode ? tagObj.createNode(ctx.schema, value, ctx) : typeof tagObj?.nodeClass?.from === "function" ? tagObj.nodeClass.from(ctx.schema, value, ctx) : new Scalar(value);
	if (tagName) node.tag = tagName;
	else if (!tagObj.default) node.tag = tagObj.tag;
	if (ref) ref.node = node;
	return node;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/Collection.js
function collectionFromPath(schema, path, value) {
	let v = value;
	for (let i = path.length - 1; i >= 0; --i) {
		const k = path[i];
		if (typeof k === "number" && Number.isInteger(k) && k >= 0) {
			const a = [];
			a[k] = v;
			v = a;
		} else v = /* @__PURE__ */ new Map([[k, v]]);
	}
	return createNode(v, void 0, {
		aliasDuplicateObjects: false,
		keepUndefined: false,
		onAnchor: () => {
			throw new Error("This should not happen, please report a bug.");
		},
		schema,
		sourceObjects: /* @__PURE__ */ new Map()
	});
}
var isEmptyPath = (path) => path == null || typeof path === "object" && !!path[Symbol.iterator]().next().done;
var Collection = class extends NodeBase {
	constructor(type, schema) {
		super(type);
		Object.defineProperty(this, "schema", {
			value: schema,
			configurable: true,
			enumerable: false,
			writable: true
		});
	}
	/**
	* Create a copy of this collection.
	*
	* @param schema - If defined, overwrites the original's schema
	*/
	clone(schema) {
		const copy = Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this));
		if (schema) copy.schema = schema;
		copy.items = copy.items.map((it) => isNode(it) || isPair(it) ? it.clone(schema) : it);
		if (this.range) copy.range = this.range.slice();
		return copy;
	}
	/**
	* Adds a value to the collection. For `!!map` and `!!omap` the value must
	* be a Pair instance or a `{ key, value }` object, which may not have a key
	* that already exists in the map.
	*/
	addIn(path, value) {
		if (isEmptyPath(path)) this.add(value);
		else {
			const [key, ...rest] = path;
			const node = this.get(key, true);
			if (isCollection$1(node)) node.addIn(rest, value);
			else if (node === void 0 && this.schema) this.set(key, collectionFromPath(this.schema, rest, value));
			else throw new Error(`Expected YAML collection at ${key}. Remaining path: ${rest}`);
		}
	}
	/**
	* Removes a value from the collection.
	* @returns `true` if the item was found and removed.
	*/
	deleteIn(path) {
		const [key, ...rest] = path;
		if (rest.length === 0) return this.delete(key);
		const node = this.get(key, true);
		if (isCollection$1(node)) return node.deleteIn(rest);
		else throw new Error(`Expected YAML collection at ${key}. Remaining path: ${rest}`);
	}
	/**
	* Returns item at `key`, or `undefined` if not found. By default unwraps
	* scalar values from their surrounding node; to disable set `keepScalar` to
	* `true` (collections are always returned intact).
	*/
	getIn(path, keepScalar) {
		const [key, ...rest] = path;
		const node = this.get(key, true);
		if (rest.length === 0) return !keepScalar && isScalar$1(node) ? node.value : node;
		else return isCollection$1(node) ? node.getIn(rest, keepScalar) : void 0;
	}
	hasAllNullValues(allowScalar) {
		return this.items.every((node) => {
			if (!isPair(node)) return false;
			const n = node.value;
			return n == null || allowScalar && isScalar$1(n) && n.value == null && !n.commentBefore && !n.comment && !n.tag;
		});
	}
	/**
	* Checks if the collection includes a value with the key `key`.
	*/
	hasIn(path) {
		const [key, ...rest] = path;
		if (rest.length === 0) return this.has(key);
		const node = this.get(key, true);
		return isCollection$1(node) ? node.hasIn(rest) : false;
	}
	/**
	* Sets a value in this collection. For `!!set`, `value` needs to be a
	* boolean to add/remove the item from the set.
	*/
	setIn(path, value) {
		const [key, ...rest] = path;
		if (rest.length === 0) this.set(key, value);
		else {
			const node = this.get(key, true);
			if (isCollection$1(node)) node.setIn(rest, value);
			else if (node === void 0 && this.schema) this.set(key, collectionFromPath(this.schema, rest, value));
			else throw new Error(`Expected YAML collection at ${key}. Remaining path: ${rest}`);
		}
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyComment.js
/**
* Stringifies a comment.
*
* Empty comment lines are left empty,
* lines consisting of a single space are replaced by `#`,
* and all other lines are prefixed with a `#`.
*/
var stringifyComment = (str) => str.replace(/^(?!$)(?: $)?/gm, "#");
function indentComment(comment, indent) {
	if (/^\n+$/.test(comment)) return comment.substring(1);
	return indent ? comment.replace(/^(?! *$)/gm, indent) : comment;
}
var lineComment = (str, indent, comment) => str.endsWith("\n") ? indentComment(comment, indent) : comment.includes("\n") ? "\n" + indentComment(comment, indent) : (str.endsWith(" ") ? "" : " ") + comment;
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/foldFlowLines.js
var FOLD_FLOW = "flow";
var FOLD_BLOCK = "block";
var FOLD_QUOTED = "quoted";
/**
* Tries to keep input at up to `lineWidth` characters, splitting only on spaces
* not followed by newlines or spaces unless `mode` is `'quoted'`. Lines are
* terminated with `\n` and started with `indent`.
*/
function foldFlowLines(text, indent, mode = "flow", { indentAtStart, lineWidth = 80, minContentWidth = 20, onFold, onOverflow } = {}) {
	if (!lineWidth || lineWidth < 0) return text;
	if (lineWidth < minContentWidth) minContentWidth = 0;
	const endStep = Math.max(1 + minContentWidth, 1 + lineWidth - indent.length);
	if (text.length <= endStep) return text;
	const folds = [];
	const escapedFolds = {};
	let end = lineWidth - indent.length;
	if (typeof indentAtStart === "number") {
		if (indentAtStart > lineWidth - Math.max(2, minContentWidth)) folds.push(0);
		else end = lineWidth - indentAtStart;
	}
	let split = void 0;
	let prev = void 0;
	let overflow = false;
	let i = -1;
	let escStart = -1;
	let escEnd = -1;
	if (mode === "block") {
		i = consumeMoreIndentedLines(text, i, indent.length);
		if (i !== -1) end = i + endStep;
	}
	for (let ch; ch = text[i += 1];) {
		if (mode === "quoted" && ch === "\\") {
			escStart = i;
			switch (text[i + 1]) {
				case "x":
					i += 3;
					break;
				case "u":
					i += 5;
					break;
				case "U":
					i += 9;
					break;
				default: i += 1;
			}
			escEnd = i;
		}
		if (ch === "\n") {
			if (mode === "block") i = consumeMoreIndentedLines(text, i, indent.length);
			end = i + indent.length + endStep;
			split = void 0;
		} else {
			if (ch === " " && prev && prev !== " " && prev !== "\n" && prev !== "	") {
				const next = text[i + 1];
				if (next && next !== " " && next !== "\n" && next !== "	") split = i;
			}
			if (i >= end) {
				if (split) {
					folds.push(split);
					end = split + endStep;
					split = void 0;
				} else if (mode === "quoted") {
					while (prev === " " || prev === "	") {
						prev = ch;
						ch = text[i += 1];
						overflow = true;
					}
					const j = i > escEnd + 1 ? i - 2 : escStart - 1;
					if (escapedFolds[j]) return text;
					folds.push(j);
					escapedFolds[j] = true;
					end = j + endStep;
					split = void 0;
				} else overflow = true;
			}
		}
		prev = ch;
	}
	if (overflow && onOverflow) onOverflow();
	if (folds.length === 0) return text;
	if (onFold) onFold();
	let res = text.slice(0, folds[0]);
	for (let i = 0; i < folds.length; ++i) {
		const fold = folds[i];
		const end = folds[i + 1] || text.length;
		if (fold === 0) res = `\n${indent}${text.slice(0, end)}`;
		else {
			if (mode === "quoted" && escapedFolds[fold]) res += `${text[fold]}\\`;
			res += `\n${indent}${text.slice(fold + 1, end)}`;
		}
	}
	return res;
}
/**
* Presumes `i + 1` is at the start of a line
* @returns index of last newline in more-indented block
*/
function consumeMoreIndentedLines(text, i, indent) {
	let end = i;
	let start = i + 1;
	let ch = text[start];
	while (ch === " " || ch === "	") if (i < start + indent) ch = text[++i];
	else {
		do
			ch = text[++i];
		while (ch && ch !== "\n");
		end = i;
		start = i + 1;
		ch = text[start];
	}
	return end;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyString.js
var getFoldOptions = (ctx, isBlock) => ({
	indentAtStart: isBlock ? ctx.indent.length : ctx.indentAtStart,
	lineWidth: ctx.options.lineWidth,
	minContentWidth: ctx.options.minContentWidth
});
var containsDocumentMarker = (str) => /^(%|---|\.\.\.)/m.test(str);
function lineLengthOverLimit(str, lineWidth, indentLength) {
	if (!lineWidth || lineWidth < 0) return false;
	const limit = lineWidth - indentLength;
	const strLen = str.length;
	if (strLen <= limit) return false;
	for (let i = 0, start = 0; i < strLen; ++i) if (str[i] === "\n") {
		if (i - start > limit) return true;
		start = i + 1;
		if (strLen - start <= limit) return false;
	}
	return true;
}
function doubleQuotedString(value, ctx) {
	const json = JSON.stringify(value);
	if (ctx.options.doubleQuotedAsJSON) return json;
	const { implicitKey } = ctx;
	const minMultiLineLength = ctx.options.doubleQuotedMinMultiLineLength;
	const indent = ctx.indent || (containsDocumentMarker(value) ? "  " : "");
	let str = "";
	let start = 0;
	for (let i = 0, ch = json[i]; ch; ch = json[++i]) {
		if (ch === " " && json[i + 1] === "\\" && json[i + 2] === "n") {
			str += json.slice(start, i) + "\\ ";
			i += 1;
			start = i;
			ch = "\\";
		}
		if (ch === "\\") switch (json[i + 1]) {
			case "u":
				{
					str += json.slice(start, i);
					const code = json.substr(i + 2, 4);
					switch (code) {
						case "0000":
							str += "\\0";
							break;
						case "0007":
							str += "\\a";
							break;
						case "000b":
							str += "\\v";
							break;
						case "001b":
							str += "\\e";
							break;
						case "0085":
							str += "\\N";
							break;
						case "00a0":
							str += "\\_";
							break;
						case "2028":
							str += "\\L";
							break;
						case "2029":
							str += "\\P";
							break;
						default: if (code.substr(0, 2) === "00") str += "\\x" + code.substr(2);
						else str += json.substr(i, 6);
					}
					i += 5;
					start = i + 1;
				}
				break;
			case "n":
				if (implicitKey || json[i + 2] === "\"" || json.length < minMultiLineLength) i += 1;
				else {
					str += json.slice(start, i) + "\n\n";
					while (json[i + 2] === "\\" && json[i + 3] === "n" && json[i + 4] !== "\"") {
						str += "\n";
						i += 2;
					}
					str += indent;
					if (json[i + 2] === " ") str += "\\";
					i += 1;
					start = i + 1;
				}
				break;
			default: i += 1;
		}
	}
	str = start ? str + json.slice(start) : json;
	return implicitKey ? str : foldFlowLines(str, indent, FOLD_QUOTED, getFoldOptions(ctx, false));
}
function singleQuotedString(value, ctx) {
	if (ctx.options.singleQuote === false || ctx.implicitKey && value.includes("\n") || /[ \t]\n|\n[ \t]/.test(value)) return doubleQuotedString(value, ctx);
	const indent = ctx.indent || (containsDocumentMarker(value) ? "  " : "");
	const res = "'" + value.replace(/'/g, "''").replace(/\n+/g, `$&\n${indent}`) + "'";
	return ctx.implicitKey ? res : foldFlowLines(res, indent, FOLD_FLOW, getFoldOptions(ctx, false));
}
function quotedString(value, ctx) {
	const { singleQuote } = ctx.options;
	let qs;
	if (singleQuote === false) qs = doubleQuotedString;
	else {
		const hasDouble = value.includes("\"");
		const hasSingle = value.includes("'");
		if (hasDouble && !hasSingle) qs = singleQuotedString;
		else if (hasSingle && !hasDouble) qs = doubleQuotedString;
		else qs = singleQuote ? singleQuotedString : doubleQuotedString;
	}
	return qs(value, ctx);
}
var blockEndNewlines;
try {
	blockEndNewlines = /* @__PURE__ */ new RegExp("(^|(?<!\n))\n+(?!\n|$)", "g");
} catch {
	blockEndNewlines = /\n+(?!\n|$)/g;
}
function blockString({ comment, type, value }, ctx, onComment, onChompKeep) {
	const { blockQuote, commentString, lineWidth } = ctx.options;
	if (!blockQuote || /\n[\t ]+$/.test(value)) return quotedString(value, ctx);
	const indent = ctx.indent || (ctx.forceBlockIndent || containsDocumentMarker(value) ? "  " : "");
	const literal = blockQuote === "literal" ? true : blockQuote === "folded" || type === Scalar.BLOCK_FOLDED ? false : type === Scalar.BLOCK_LITERAL ? true : !lineLengthOverLimit(value, lineWidth, indent.length);
	if (!value) return literal ? "|\n" : ">\n";
	let chomp;
	let endStart;
	for (endStart = value.length; endStart > 0; --endStart) {
		const ch = value[endStart - 1];
		if (ch !== "\n" && ch !== "	" && ch !== " ") break;
	}
	let end = value.substring(endStart);
	const endNlPos = end.indexOf("\n");
	if (endNlPos === -1) chomp = "-";
	else if (value === end || endNlPos !== end.length - 1) {
		chomp = "+";
		if (onChompKeep) onChompKeep();
	} else chomp = "";
	if (end) {
		value = value.slice(0, -end.length);
		if (end[end.length - 1] === "\n") end = end.slice(0, -1);
		end = end.replace(blockEndNewlines, `$&${indent}`);
	}
	let startWithSpace = false;
	let startEnd;
	let startNlPos = -1;
	for (startEnd = 0; startEnd < value.length; ++startEnd) {
		const ch = value[startEnd];
		if (ch === " ") startWithSpace = true;
		else if (ch === "\n") startNlPos = startEnd;
		else break;
	}
	let start = value.substring(0, startNlPos < startEnd ? startNlPos + 1 : startEnd);
	if (start) {
		value = value.substring(start.length);
		start = start.replace(/\n+/g, `$&${indent}`);
	}
	let header = (startWithSpace ? indent ? "2" : "1" : "") + chomp;
	if (comment) {
		header += " " + commentString(comment.replace(/ ?[\r\n]+/g, " "));
		if (onComment) onComment();
	}
	if (!literal) {
		const foldedValue = value.replace(/\n+/g, "\n$&").replace(/(?:^|\n)([\t ].*)(?:([\n\t ]*)\n(?![\n\t ]))?/g, "$1$2").replace(/\n+/g, `$&${indent}`);
		let literalFallback = false;
		const foldOptions = getFoldOptions(ctx, true);
		if (blockQuote !== "folded" && type !== Scalar.BLOCK_FOLDED) foldOptions.onOverflow = () => {
			literalFallback = true;
		};
		const body = foldFlowLines(`${start}${foldedValue}${end}`, indent, FOLD_BLOCK, foldOptions);
		if (!literalFallback) return `>${header}\n${indent}${body}`;
	}
	value = value.replace(/\n+/g, `$&${indent}`);
	return `|${header}\n${indent}${start}${value}${end}`;
}
function plainString(item, ctx, onComment, onChompKeep) {
	const { type, value } = item;
	const { actualString, implicitKey, indent, indentStep, inFlow } = ctx;
	if (implicitKey && value.includes("\n") || inFlow && /[[\]{},]/.test(value)) return quotedString(value, ctx);
	if (/^[\n\t ,[\]{}#&*!|>'"%@`]|^[?-]$|^[?-][ \t]|[\n:][ \t]|[ \t]\n|[\n\t ]#|[\n\t :]$/.test(value)) return implicitKey || inFlow || !value.includes("\n") ? quotedString(value, ctx) : blockString(item, ctx, onComment, onChompKeep);
	if (!implicitKey && !inFlow && type !== Scalar.PLAIN && value.includes("\n")) return blockString(item, ctx, onComment, onChompKeep);
	if (containsDocumentMarker(value)) {
		if (indent === "") {
			ctx.forceBlockIndent = true;
			return blockString(item, ctx, onComment, onChompKeep);
		} else if (implicitKey && indent === indentStep) return quotedString(value, ctx);
	}
	const str = value.replace(/\n+/g, `$&\n${indent}`);
	if (actualString) {
		const test = (tag) => tag.default && tag.tag !== "tag:yaml.org,2002:str" && tag.test?.test(str);
		const { compat, tags } = ctx.doc.schema;
		if (tags.some(test) || compat?.some(test)) return quotedString(value, ctx);
	}
	return implicitKey ? str : foldFlowLines(str, indent, FOLD_FLOW, getFoldOptions(ctx, false));
}
function stringifyString(item, ctx, onComment, onChompKeep) {
	const { implicitKey, inFlow } = ctx;
	const ss = typeof item.value === "string" ? item : Object.assign({}, item, { value: String(item.value) });
	let { type } = item;
	if (type !== Scalar.QUOTE_DOUBLE) {
		if (/[\x00-\x08\x0b-\x1f\x7f-\x9f\u{D800}-\u{DFFF}]/u.test(ss.value)) type = Scalar.QUOTE_DOUBLE;
	}
	const _stringify = (_type) => {
		switch (_type) {
			case Scalar.BLOCK_FOLDED:
			case Scalar.BLOCK_LITERAL: return implicitKey || inFlow ? quotedString(ss.value, ctx) : blockString(ss, ctx, onComment, onChompKeep);
			case Scalar.QUOTE_DOUBLE: return doubleQuotedString(ss.value, ctx);
			case Scalar.QUOTE_SINGLE: return singleQuotedString(ss.value, ctx);
			case Scalar.PLAIN: return plainString(ss, ctx, onComment, onChompKeep);
			default: return null;
		}
	};
	let res = _stringify(type);
	if (res === null) {
		const { defaultKeyType, defaultStringType } = ctx.options;
		const t = implicitKey && defaultKeyType || defaultStringType;
		res = _stringify(t);
		if (res === null) throw new Error(`Unsupported default string type ${t}`);
	}
	return res;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringify.js
function createStringifyContext(doc, options) {
	const opt = Object.assign({
		blockQuote: true,
		commentString: stringifyComment,
		defaultKeyType: null,
		defaultStringType: "PLAIN",
		directives: null,
		doubleQuotedAsJSON: false,
		doubleQuotedMinMultiLineLength: 40,
		falseStr: "false",
		flowCollectionPadding: true,
		indentSeq: true,
		lineWidth: 80,
		minContentWidth: 20,
		nullStr: "null",
		simpleKeys: false,
		singleQuote: null,
		trailingComma: false,
		trueStr: "true",
		verifyAliasOrder: true
	}, doc.schema.toStringOptions, options);
	let inFlow;
	switch (opt.collectionStyle) {
		case "block":
			inFlow = false;
			break;
		case "flow":
			inFlow = true;
			break;
		default: inFlow = null;
	}
	return {
		anchors: /* @__PURE__ */ new Set(),
		doc,
		flowCollectionPadding: opt.flowCollectionPadding ? " " : "",
		indent: "",
		indentStep: typeof opt.indent === "number" ? " ".repeat(opt.indent) : "  ",
		inFlow,
		options: opt
	};
}
function getTagObject(tags, item) {
	if (item.tag) {
		const match = tags.filter((t) => t.tag === item.tag);
		if (match.length > 0) return match.find((t) => t.format === item.format) ?? match[0];
	}
	let tagObj = void 0;
	let obj;
	if (isScalar$1(item)) {
		obj = item.value;
		let match = tags.filter((t) => t.identify?.(obj));
		if (match.length > 1) {
			const testMatch = match.filter((t) => t.test);
			if (testMatch.length > 0) match = testMatch;
		}
		tagObj = match.find((t) => t.format === item.format) ?? match.find((t) => !t.format);
	} else {
		obj = item;
		tagObj = tags.find((t) => t.nodeClass && obj instanceof t.nodeClass);
	}
	if (!tagObj) {
		const name = obj?.constructor?.name ?? (obj === null ? "null" : typeof obj);
		throw new Error(`Tag not resolved for ${name} value`);
	}
	return tagObj;
}
function stringifyProps(node, tagObj, { anchors, doc }) {
	if (!doc.directives) return "";
	const props = [];
	const anchor = (isScalar$1(node) || isCollection$1(node)) && node.anchor;
	if (anchor && anchorIsValid(anchor)) {
		anchors.add(anchor);
		props.push(`&${anchor}`);
	}
	const tag = node.tag ?? (tagObj.default ? null : tagObj.tag);
	if (tag) props.push(doc.directives.tagString(tag));
	return props.join(" ");
}
function stringify$2(item, ctx, onComment, onChompKeep) {
	if (isPair(item)) return item.toString(ctx, onComment, onChompKeep);
	if (isAlias(item)) {
		if (ctx.doc.directives) return item.toString(ctx);
		if (ctx.resolvedAliases?.has(item)) throw new TypeError(`Cannot stringify circular structure without alias nodes`);
		else {
			if (ctx.resolvedAliases) ctx.resolvedAliases.add(item);
			else ctx.resolvedAliases = /* @__PURE__ */ new Set([item]);
			item = item.resolve(ctx.doc);
		}
	}
	let tagObj = void 0;
	const node = isNode(item) ? item : ctx.doc.createNode(item, { onTagObj: (o) => tagObj = o });
	tagObj ?? (tagObj = getTagObject(ctx.doc.schema.tags, node));
	const props = stringifyProps(node, tagObj, ctx);
	if (props.length > 0) ctx.indentAtStart = (ctx.indentAtStart ?? 0) + props.length + 1;
	const str = typeof tagObj.stringify === "function" ? tagObj.stringify(node, ctx, onComment, onChompKeep) : isScalar$1(node) ? stringifyString(node, ctx, onComment, onChompKeep) : node.toString(ctx, onComment, onChompKeep);
	if (!props) return str;
	return isScalar$1(node) || str[0] === "{" || str[0] === "[" ? `${props} ${str}` : `${props}\n${ctx.indent}${str}`;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyPair.js
function stringifyPair({ key, value }, ctx, onComment, onChompKeep) {
	const { allNullValues, doc, indent, indentStep, options: { commentString, indentSeq, simpleKeys } } = ctx;
	let keyComment = isNode(key) && key.comment || null;
	if (simpleKeys) {
		if (keyComment) throw new Error("With simple keys, key nodes cannot have comments");
		if (isCollection$1(key) || !isNode(key) && typeof key === "object") throw new Error("With simple keys, collection cannot be used as a key value");
	}
	let explicitKey = !simpleKeys && (!key || keyComment && value == null && !ctx.inFlow || isCollection$1(key) || (isScalar$1(key) ? key.type === Scalar.BLOCK_FOLDED || key.type === Scalar.BLOCK_LITERAL : typeof key === "object"));
	ctx = Object.assign({}, ctx, {
		allNullValues: false,
		implicitKey: !explicitKey && (simpleKeys || !allNullValues),
		indent: indent + indentStep
	});
	let keyCommentDone = false;
	let chompKeep = false;
	let str = stringify$2(key, ctx, () => keyCommentDone = true, () => chompKeep = true);
	if (!explicitKey && !ctx.inFlow && str.length > 1024) {
		if (simpleKeys) throw new Error("With simple keys, single line scalar must not span more than 1024 characters");
		explicitKey = true;
	}
	if (ctx.inFlow) {
		if (allNullValues || value == null) {
			if (keyCommentDone && onComment) onComment();
			return str === "" ? "?" : explicitKey ? `? ${str}` : str;
		}
	} else if (allNullValues && !simpleKeys || value == null && explicitKey) {
		str = `? ${str}`;
		if (keyComment && !keyCommentDone) str += lineComment(str, ctx.indent, commentString(keyComment));
		else if (chompKeep && onChompKeep) onChompKeep();
		return str;
	}
	if (keyCommentDone) keyComment = null;
	if (explicitKey) {
		if (keyComment) str += lineComment(str, ctx.indent, commentString(keyComment));
		str = `? ${str}\n${indent}:`;
	} else {
		str = `${str}:`;
		if (keyComment) str += lineComment(str, ctx.indent, commentString(keyComment));
	}
	let vsb, vcb, valueComment;
	if (isNode(value)) {
		vsb = !!value.spaceBefore;
		vcb = value.commentBefore;
		valueComment = value.comment;
	} else {
		vsb = false;
		vcb = null;
		valueComment = null;
		if (value && typeof value === "object") value = doc.createNode(value);
	}
	ctx.implicitKey = false;
	if (!explicitKey && !keyComment && isScalar$1(value)) ctx.indentAtStart = str.length + 1;
	chompKeep = false;
	if (!indentSeq && indentStep.length >= 2 && !ctx.inFlow && !explicitKey && isSeq(value) && !value.flow && !value.tag && !value.anchor) ctx.indent = ctx.indent.substring(2);
	let valueCommentDone = false;
	const valueStr = stringify$2(value, ctx, () => valueCommentDone = true, () => chompKeep = true);
	let ws = " ";
	if (keyComment || vsb || vcb) {
		ws = vsb ? "\n" : "";
		if (vcb) {
			const cs = commentString(vcb);
			ws += `\n${indentComment(cs, ctx.indent)}`;
		}
		if (valueStr === "" && !ctx.inFlow) {
			if (ws === "\n" && valueComment) ws = "\n\n";
		} else ws += `\n${ctx.indent}`;
	} else if (!explicitKey && isCollection$1(value)) {
		const vs0 = valueStr[0];
		const nl0 = valueStr.indexOf("\n");
		const hasNewline = nl0 !== -1;
		const flow = ctx.inFlow ?? value.flow ?? value.items.length === 0;
		if (hasNewline || !flow) {
			let hasPropsLine = false;
			if (hasNewline && (vs0 === "&" || vs0 === "!")) {
				let sp0 = valueStr.indexOf(" ");
				if (vs0 === "&" && sp0 !== -1 && sp0 < nl0 && valueStr[sp0 + 1] === "!") sp0 = valueStr.indexOf(" ", sp0 + 1);
				if (sp0 === -1 || nl0 < sp0) hasPropsLine = true;
			}
			if (!hasPropsLine) ws = `\n${ctx.indent}`;
		}
	} else if (valueStr === "" || valueStr[0] === "\n") ws = "";
	str += ws + valueStr;
	if (ctx.inFlow) {
		if (valueCommentDone && onComment) onComment();
	} else if (valueComment && !valueCommentDone) str += lineComment(str, ctx.indent, commentString(valueComment));
	else if (chompKeep && onChompKeep) onChompKeep();
	return str;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/log.js
function warn(logLevel, warning) {
	if (logLevel === "debug" || logLevel === "warn") console.warn(warning);
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/merge.js
var MERGE_KEY = "<<";
var merge = {
	identify: (value) => value === MERGE_KEY || typeof value === "symbol" && value.description === MERGE_KEY,
	default: "key",
	tag: "tag:yaml.org,2002:merge",
	test: /^<<$/,
	resolve: () => Object.assign(new Scalar(Symbol(MERGE_KEY)), { addToJSMap: addMergeToJSMap }),
	stringify: () => MERGE_KEY
};
var isMergeKey = (ctx, key) => (merge.identify(key) || isScalar$1(key) && (!key.type || key.type === Scalar.PLAIN) && merge.identify(key.value)) && ctx?.doc.schema.tags.some((tag) => tag.tag === merge.tag && tag.default);
function addMergeToJSMap(ctx, map, value) {
	const source = resolveAliasValue(ctx, value);
	if (isSeq(source)) for (const it of source.items) mergeValue(ctx, map, it);
	else if (Array.isArray(source)) for (const it of source) mergeValue(ctx, map, it);
	else mergeValue(ctx, map, source);
}
function mergeValue(ctx, map, value) {
	const source = resolveAliasValue(ctx, value);
	if (!isMap(source)) throw new Error("Merge sources must be maps or map aliases");
	const srcMap = source.toJSON(null, ctx, Map);
	for (const [key, value] of srcMap) if (map instanceof Map) {
		if (!map.has(key)) map.set(key, value);
	} else if (map instanceof Set) map.add(key);
	else if (!Object.prototype.hasOwnProperty.call(map, key)) Object.defineProperty(map, key, {
		value,
		writable: true,
		enumerable: true,
		configurable: true
	});
	return map;
}
function resolveAliasValue(ctx, value) {
	return ctx && isAlias(value) ? value.resolve(ctx.doc, ctx) : value;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/addPairToJSMap.js
function addPairToJSMap(ctx, map, { key, value }) {
	if (isNode(key) && key.addToJSMap) key.addToJSMap(ctx, map, value);
	else if (isMergeKey(ctx, key)) addMergeToJSMap(ctx, map, value);
	else {
		const jsKey = toJS(key, "", ctx);
		if (map instanceof Map) map.set(jsKey, toJS(value, jsKey, ctx));
		else if (map instanceof Set) map.add(jsKey);
		else {
			const stringKey = stringifyKey(key, jsKey, ctx);
			const jsValue = toJS(value, stringKey, ctx);
			if (stringKey in map) Object.defineProperty(map, stringKey, {
				value: jsValue,
				writable: true,
				enumerable: true,
				configurable: true
			});
			else map[stringKey] = jsValue;
		}
	}
	return map;
}
function stringifyKey(key, jsKey, ctx) {
	if (jsKey === null) return "";
	if (typeof jsKey !== "object") return String(jsKey);
	if (isNode(key) && ctx?.doc) {
		const strCtx = createStringifyContext(ctx.doc, {});
		strCtx.anchors = /* @__PURE__ */ new Set();
		for (const node of ctx.anchors.keys()) strCtx.anchors.add(node.anchor);
		strCtx.inFlow = true;
		strCtx.inStringifyKey = true;
		const strKey = key.toString(strCtx);
		if (!ctx.mapKeyWarned) {
			let jsonStr = JSON.stringify(strKey);
			if (jsonStr.length > 40) jsonStr = jsonStr.substring(0, 36) + "...\"";
			warn(ctx.doc.options.logLevel, `Keys with collection values will be stringified due to JS Object restrictions: ${jsonStr}. Set mapAsMap: true to use object keys.`);
			ctx.mapKeyWarned = true;
		}
		return strKey;
	}
	return JSON.stringify(jsKey);
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/Pair.js
function createPair(key, value, ctx) {
	return new Pair(createNode(key, void 0, ctx), createNode(value, void 0, ctx));
}
var Pair = class Pair {
	constructor(key, value = null) {
		Object.defineProperty(this, NODE_TYPE, { value: PAIR });
		this.key = key;
		this.value = value;
	}
	clone(schema) {
		let { key, value } = this;
		if (isNode(key)) key = key.clone(schema);
		if (isNode(value)) value = value.clone(schema);
		return new Pair(key, value);
	}
	toJSON(_, ctx) {
		return addPairToJSMap(ctx, ctx?.mapAsMap ? /* @__PURE__ */ new Map() : {}, this);
	}
	toString(ctx, onComment, onChompKeep) {
		return ctx?.doc ? stringifyPair(this, ctx, onComment, onChompKeep) : JSON.stringify(this);
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyCollection.js
function stringifyCollection(collection, ctx, options) {
	return (ctx.inFlow ?? collection.flow ? stringifyFlowCollection : stringifyBlockCollection)(collection, ctx, options);
}
function stringifyBlockCollection({ comment, items }, ctx, { blockItemPrefix, flowChars, itemIndent, onChompKeep, onComment }) {
	const { indent, options: { commentString } } = ctx;
	const itemCtx = Object.assign({}, ctx, {
		indent: itemIndent,
		type: null
	});
	let chompKeep = false;
	const lines = [];
	for (let i = 0; i < items.length; ++i) {
		const item = items[i];
		let comment = null;
		if (isNode(item)) {
			if (!chompKeep && item.spaceBefore) lines.push("");
			addCommentBefore(ctx, lines, item.commentBefore, chompKeep);
			if (item.comment) comment = item.comment;
		} else if (isPair(item)) {
			const ik = isNode(item.key) ? item.key : null;
			if (ik) {
				if (!chompKeep && ik.spaceBefore) lines.push("");
				addCommentBefore(ctx, lines, ik.commentBefore, chompKeep);
			}
		}
		chompKeep = false;
		let str = stringify$2(item, itemCtx, () => comment = null, () => chompKeep = true);
		if (comment) str += lineComment(str, itemIndent, commentString(comment));
		if (chompKeep && comment) chompKeep = false;
		lines.push(blockItemPrefix + str);
	}
	let str;
	if (lines.length === 0) str = flowChars.start + flowChars.end;
	else {
		str = lines[0];
		for (let i = 1; i < lines.length; ++i) {
			const line = lines[i];
			str += line ? `\n${indent}${line}` : "\n";
		}
	}
	if (comment) {
		str += "\n" + indentComment(commentString(comment), indent);
		if (onComment) onComment();
	} else if (chompKeep && onChompKeep) onChompKeep();
	return str;
}
function stringifyFlowCollection({ items }, ctx, { flowChars, itemIndent }) {
	const { indent, indentStep, flowCollectionPadding: fcPadding, options: { commentString } } = ctx;
	itemIndent += indentStep;
	const itemCtx = Object.assign({}, ctx, {
		indent: itemIndent,
		inFlow: true,
		type: null
	});
	let reqNewline = false;
	let linesAtValue = 0;
	const lines = [];
	for (let i = 0; i < items.length; ++i) {
		const item = items[i];
		let comment = null;
		if (isNode(item)) {
			if (item.spaceBefore) lines.push("");
			addCommentBefore(ctx, lines, item.commentBefore, false);
			if (item.comment) comment = item.comment;
		} else if (isPair(item)) {
			const ik = isNode(item.key) ? item.key : null;
			if (ik) {
				if (ik.spaceBefore) lines.push("");
				addCommentBefore(ctx, lines, ik.commentBefore, false);
				if (ik.comment) reqNewline = true;
			}
			const iv = isNode(item.value) ? item.value : null;
			if (iv) {
				if (iv.comment) comment = iv.comment;
				if (iv.commentBefore) reqNewline = true;
			} else if (item.value == null && ik?.comment) comment = ik.comment;
		}
		if (comment) reqNewline = true;
		let str = stringify$2(item, itemCtx, () => comment = null);
		reqNewline || (reqNewline = lines.length > linesAtValue || str.includes("\n"));
		if (i < items.length - 1) str += ",";
		else if (ctx.options.trailingComma) {
			if (ctx.options.lineWidth > 0) reqNewline || (reqNewline = lines.reduce((sum, line) => sum + line.length + 2, 2) + (str.length + 2) > ctx.options.lineWidth);
			if (reqNewline) str += ",";
		}
		if (comment) str += lineComment(str, itemIndent, commentString(comment));
		lines.push(str);
		linesAtValue = lines.length;
	}
	const { start, end } = flowChars;
	if (lines.length === 0) return start + end;
	else {
		if (!reqNewline) {
			const len = lines.reduce((sum, line) => sum + line.length + 2, 2);
			reqNewline = ctx.options.lineWidth > 0 && len > ctx.options.lineWidth;
		}
		if (reqNewline) {
			let str = start;
			for (const line of lines) str += line ? `\n${indentStep}${indent}${line}` : "\n";
			return `${str}\n${indent}${end}`;
		} else return `${start}${fcPadding}${lines.join(" ")}${fcPadding}${end}`;
	}
}
function addCommentBefore({ indent, options: { commentString } }, lines, comment, chompKeep) {
	if (comment && chompKeep) comment = comment.replace(/^\n+/, "");
	if (comment) {
		const ic = indentComment(commentString(comment), indent);
		lines.push(ic.trimStart());
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/YAMLMap.js
function findPair(items, key) {
	const k = isScalar$1(key) ? key.value : key;
	for (const it of items) if (isPair(it)) {
		if (it.key === key || it.key === k) return it;
		if (isScalar$1(it.key) && it.key.value === k) return it;
	}
}
var YAMLMap = class extends Collection {
	static get tagName() {
		return "tag:yaml.org,2002:map";
	}
	constructor(schema) {
		super(MAP, schema);
		this.items = [];
	}
	/**
	* A generic collection parsing method that can be extended
	* to other node classes that inherit from YAMLMap
	*/
	static from(schema, obj, ctx) {
		const { keepUndefined, replacer } = ctx;
		const map = new this(schema);
		const add = (key, value) => {
			if (typeof replacer === "function") value = replacer.call(obj, key, value);
			else if (Array.isArray(replacer) && !replacer.includes(key)) return;
			if (value !== void 0 || keepUndefined) map.items.push(createPair(key, value, ctx));
		};
		if (obj instanceof Map) for (const [key, value] of obj) add(key, value);
		else if (obj && typeof obj === "object") for (const key of Object.keys(obj)) add(key, obj[key]);
		if (typeof schema.sortMapEntries === "function") map.items.sort(schema.sortMapEntries);
		return map;
	}
	/**
	* Adds a value to the collection.
	*
	* @param overwrite - If not set `true`, using a key that is already in the
	*   collection will throw. Otherwise, overwrites the previous value.
	*/
	add(pair, overwrite) {
		let _pair;
		if (isPair(pair)) _pair = pair;
		else if (!pair || typeof pair !== "object" || !("key" in pair)) _pair = new Pair(pair, pair?.value);
		else _pair = new Pair(pair.key, pair.value);
		const prev = findPair(this.items, _pair.key);
		const sortEntries = this.schema?.sortMapEntries;
		if (prev) {
			if (!overwrite) throw new Error(`Key ${_pair.key} already set`);
			if (isScalar$1(prev.value) && isScalarValue(_pair.value)) prev.value.value = _pair.value;
			else prev.value = _pair.value;
		} else if (sortEntries) {
			const i = this.items.findIndex((item) => sortEntries(_pair, item) < 0);
			if (i === -1) this.items.push(_pair);
			else this.items.splice(i, 0, _pair);
		} else this.items.push(_pair);
	}
	delete(key) {
		const it = findPair(this.items, key);
		if (!it) return false;
		return this.items.splice(this.items.indexOf(it), 1).length > 0;
	}
	get(key, keepScalar) {
		const node = findPair(this.items, key)?.value;
		return (!keepScalar && isScalar$1(node) ? node.value : node) ?? void 0;
	}
	has(key) {
		return !!findPair(this.items, key);
	}
	set(key, value) {
		this.add(new Pair(key, value), true);
	}
	/**
	* @param ctx - Conversion context, originally set in Document#toJS()
	* @param {Class} Type - If set, forces the returned collection type
	* @returns Instance of Type, Map, or Object
	*/
	toJSON(_, ctx, Type) {
		const map = Type ? new Type() : ctx?.mapAsMap ? /* @__PURE__ */ new Map() : {};
		if (ctx?.onCreate) ctx.onCreate(map);
		for (const item of this.items) addPairToJSMap(ctx, map, item);
		return map;
	}
	toString(ctx, onComment, onChompKeep) {
		if (!ctx) return JSON.stringify(this);
		for (const item of this.items) if (!isPair(item)) throw new Error(`Map items must all be pairs; found ${JSON.stringify(item)} instead`);
		if (!ctx.allNullValues && this.hasAllNullValues(false)) ctx = Object.assign({}, ctx, { allNullValues: true });
		return stringifyCollection(this, ctx, {
			blockItemPrefix: "",
			flowChars: {
				start: "{",
				end: "}"
			},
			itemIndent: ctx.indent || "",
			onChompKeep,
			onComment
		});
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/common/map.js
var map = {
	collection: "map",
	default: true,
	nodeClass: YAMLMap,
	tag: "tag:yaml.org,2002:map",
	resolve(map, onError) {
		if (!isMap(map)) onError("Expected a mapping for this tag");
		return map;
	},
	createNode: (schema, obj, ctx) => YAMLMap.from(schema, obj, ctx)
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/nodes/YAMLSeq.js
var YAMLSeq = class extends Collection {
	static get tagName() {
		return "tag:yaml.org,2002:seq";
	}
	constructor(schema) {
		super(SEQ, schema);
		this.items = [];
	}
	add(value) {
		this.items.push(value);
	}
	/**
	* Removes a value from the collection.
	*
	* `key` must contain a representation of an integer for this to succeed.
	* It may be wrapped in a `Scalar`.
	*
	* @returns `true` if the item was found and removed.
	*/
	delete(key) {
		const idx = asItemIndex(key);
		if (typeof idx !== "number") return false;
		return this.items.splice(idx, 1).length > 0;
	}
	get(key, keepScalar) {
		const idx = asItemIndex(key);
		if (typeof idx !== "number") return void 0;
		const it = this.items[idx];
		return !keepScalar && isScalar$1(it) ? it.value : it;
	}
	/**
	* Checks if the collection includes a value with the key `key`.
	*
	* `key` must contain a representation of an integer for this to succeed.
	* It may be wrapped in a `Scalar`.
	*/
	has(key) {
		const idx = asItemIndex(key);
		return typeof idx === "number" && idx < this.items.length;
	}
	/**
	* Sets a value in this collection. For `!!set`, `value` needs to be a
	* boolean to add/remove the item from the set.
	*
	* If `key` does not contain a representation of an integer, this will throw.
	* It may be wrapped in a `Scalar`.
	*/
	set(key, value) {
		const idx = asItemIndex(key);
		if (typeof idx !== "number") throw new Error(`Expected a valid index, not ${key}.`);
		const prev = this.items[idx];
		if (isScalar$1(prev) && isScalarValue(value)) prev.value = value;
		else this.items[idx] = value;
	}
	toJSON(_, ctx) {
		const seq = [];
		if (ctx?.onCreate) ctx.onCreate(seq);
		let i = 0;
		for (const item of this.items) seq.push(toJS(item, String(i++), ctx));
		return seq;
	}
	toString(ctx, onComment, onChompKeep) {
		if (!ctx) return JSON.stringify(this);
		return stringifyCollection(this, ctx, {
			blockItemPrefix: "- ",
			flowChars: {
				start: "[",
				end: "]"
			},
			itemIndent: (ctx.indent || "") + "  ",
			onChompKeep,
			onComment
		});
	}
	static from(schema, obj, ctx) {
		const { replacer } = ctx;
		const seq = new this(schema);
		if (obj && Symbol.iterator in Object(obj)) {
			let i = 0;
			for (let it of obj) {
				if (typeof replacer === "function") {
					const key = obj instanceof Set ? it : String(i++);
					it = replacer.call(obj, key, it);
				}
				seq.items.push(createNode(it, void 0, ctx));
			}
		}
		return seq;
	}
};
function asItemIndex(key) {
	let idx = isScalar$1(key) ? key.value : key;
	if (idx && typeof idx === "string") idx = Number(idx);
	return typeof idx === "number" && Number.isInteger(idx) && idx >= 0 ? idx : null;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/common/seq.js
var seq = {
	collection: "seq",
	default: true,
	nodeClass: YAMLSeq,
	tag: "tag:yaml.org,2002:seq",
	resolve(seq, onError) {
		if (!isSeq(seq)) onError("Expected a sequence for this tag");
		return seq;
	},
	createNode: (schema, obj, ctx) => YAMLSeq.from(schema, obj, ctx)
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/common/string.js
var string = {
	identify: (value) => typeof value === "string",
	default: true,
	tag: "tag:yaml.org,2002:str",
	resolve: (str) => str,
	stringify(item, ctx, onComment, onChompKeep) {
		ctx = Object.assign({ actualString: true }, ctx);
		return stringifyString(item, ctx, onComment, onChompKeep);
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/common/null.js
var nullTag = {
	identify: (value) => value == null,
	createNode: () => new Scalar(null),
	default: true,
	tag: "tag:yaml.org,2002:null",
	test: /^(?:~|[Nn]ull|NULL)?$/,
	resolve: () => new Scalar(null),
	stringify: ({ source }, ctx) => typeof source === "string" && nullTag.test.test(source) ? source : ctx.options.nullStr
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/core/bool.js
var boolTag = {
	identify: (value) => typeof value === "boolean",
	default: true,
	tag: "tag:yaml.org,2002:bool",
	test: /^(?:[Tt]rue|TRUE|[Ff]alse|FALSE)$/,
	resolve: (str) => new Scalar(str[0] === "t" || str[0] === "T"),
	stringify({ source, value }, ctx) {
		if (source && boolTag.test.test(source)) {
			if (value === (source[0] === "t" || source[0] === "T")) return source;
		}
		return value ? ctx.options.trueStr : ctx.options.falseStr;
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyNumber.js
function stringifyNumber({ format, minFractionDigits, tag, value }) {
	if (typeof value === "bigint") return String(value);
	const num = typeof value === "number" ? value : Number(value);
	if (!isFinite(num)) return isNaN(num) ? ".nan" : num < 0 ? "-.inf" : ".inf";
	let n = Object.is(value, -0) ? "-0" : JSON.stringify(value);
	if (!format && minFractionDigits && (!tag || tag === "tag:yaml.org,2002:float") && /^-?\d/.test(n) && !n.includes("e")) {
		let i = n.indexOf(".");
		if (i < 0) {
			i = n.length;
			n += ".";
		}
		let d = minFractionDigits - (n.length - i - 1);
		while (d-- > 0) n += "0";
	}
	return n;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/core/float.js
var floatNaN$1 = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	test: /^(?:[-+]?\.(?:inf|Inf|INF)|\.nan|\.NaN|\.NAN)$/,
	resolve: (str) => str.slice(-3).toLowerCase() === "nan" ? NaN : str[0] === "-" ? Number.NEGATIVE_INFINITY : Number.POSITIVE_INFINITY,
	stringify: stringifyNumber
};
var floatExp$1 = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	format: "EXP",
	test: /^[-+]?(?:\.[0-9]+|[0-9]+(?:\.[0-9]*)?)[eE][-+]?[0-9]+$/,
	resolve: (str) => parseFloat(str),
	stringify(node) {
		const num = Number(node.value);
		return isFinite(num) ? num.toExponential() : stringifyNumber(node);
	}
};
var float$1 = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	test: /^[-+]?(?:\.[0-9]+|[0-9]+\.[0-9]*)$/,
	resolve(str) {
		const node = new Scalar(parseFloat(str));
		const dot = str.indexOf(".");
		if (dot !== -1 && str[str.length - 1] === "0") node.minFractionDigits = str.length - dot - 1;
		return node;
	},
	stringify: stringifyNumber
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/core/int.js
var intIdentify$2 = (value) => typeof value === "bigint" || Number.isInteger(value);
var intResolve$1 = (str, offset, radix, { intAsBigInt }) => intAsBigInt ? BigInt(str) : parseInt(str.substring(offset), radix);
function intStringify$1(node, radix, prefix) {
	const { value } = node;
	if (intIdentify$2(value) && value >= 0) return prefix + value.toString(radix);
	return stringifyNumber(node);
}
var intOct$1 = {
	identify: (value) => intIdentify$2(value) && value >= 0,
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "OCT",
	test: /^0o[0-7]+$/,
	resolve: (str, _onError, opt) => intResolve$1(str, 2, 8, opt),
	stringify: (node) => intStringify$1(node, 8, "0o")
};
var int$1 = {
	identify: intIdentify$2,
	default: true,
	tag: "tag:yaml.org,2002:int",
	test: /^[-+]?[0-9]+$/,
	resolve: (str, _onError, opt) => intResolve$1(str, 0, 10, opt),
	stringify: stringifyNumber
};
var intHex$1 = {
	identify: (value) => intIdentify$2(value) && value >= 0,
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "HEX",
	test: /^0x[0-9a-fA-F]+$/,
	resolve: (str, _onError, opt) => intResolve$1(str, 2, 16, opt),
	stringify: (node) => intStringify$1(node, 16, "0x")
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/core/schema.js
var schema$2 = [
	map,
	seq,
	string,
	nullTag,
	boolTag,
	intOct$1,
	int$1,
	intHex$1,
	floatNaN$1,
	floatExp$1,
	float$1
];
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/json/schema.js
function intIdentify$1(value) {
	return typeof value === "bigint" || Number.isInteger(value);
}
var stringifyJSON = ({ value }) => JSON.stringify(value);
var jsonScalars = [
	{
		identify: (value) => typeof value === "string",
		default: true,
		tag: "tag:yaml.org,2002:str",
		resolve: (str) => str,
		stringify: stringifyJSON
	},
	{
		identify: (value) => value == null,
		createNode: () => new Scalar(null),
		default: true,
		tag: "tag:yaml.org,2002:null",
		test: /^null$/,
		resolve: () => null,
		stringify: stringifyJSON
	},
	{
		identify: (value) => typeof value === "boolean",
		default: true,
		tag: "tag:yaml.org,2002:bool",
		test: /^true$|^false$/,
		resolve: (str) => str === "true",
		stringify: stringifyJSON
	},
	{
		identify: intIdentify$1,
		default: true,
		tag: "tag:yaml.org,2002:int",
		test: /^-?(?:0|[1-9][0-9]*)$/,
		resolve: (str, _onError, { intAsBigInt }) => intAsBigInt ? BigInt(str) : parseInt(str, 10),
		stringify: ({ value }) => intIdentify$1(value) ? value.toString() : JSON.stringify(value)
	},
	{
		identify: (value) => typeof value === "number",
		default: true,
		tag: "tag:yaml.org,2002:float",
		test: /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]*)?(?:[eE][-+]?[0-9]+)?$/,
		resolve: (str) => parseFloat(str),
		stringify: stringifyJSON
	}
];
var schema$1 = [map, seq].concat(jsonScalars, {
	default: true,
	tag: "",
	test: /^/,
	resolve(str, onError) {
		onError(`Unresolved plain scalar ${JSON.stringify(str)}`);
		return str;
	}
});
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/binary.js
var binary = {
	identify: (value) => value instanceof Uint8Array,
	default: false,
	tag: "tag:yaml.org,2002:binary",
	/**
	* Returns a Buffer in node and an Uint8Array in browsers
	*
	* To use the resulting buffer as an image, you'll want to do something like:
	*
	*   const blob = new Blob([buffer], { type: 'image/jpeg' })
	*   document.querySelector('#photo').src = URL.createObjectURL(blob)
	*/
	resolve(src, onError) {
		if (typeof atob === "function") {
			const str = atob(src.replace(/[\n\r]/g, ""));
			const buffer = new Uint8Array(str.length);
			for (let i = 0; i < str.length; ++i) buffer[i] = str.charCodeAt(i);
			return buffer;
		} else {
			onError("This environment does not support reading binary tags; either Buffer or atob is required");
			return src;
		}
	},
	stringify({ comment, type, value }, ctx, onComment, onChompKeep) {
		if (!value) return "";
		const buf = value;
		let str;
		if (typeof btoa === "function") {
			let s = "";
			for (let i = 0; i < buf.length; ++i) s += String.fromCharCode(buf[i]);
			str = btoa(s);
		} else throw new Error("This environment does not support writing binary tags; either Buffer or btoa is required");
		type ?? (type = Scalar.BLOCK_LITERAL);
		if (type !== Scalar.QUOTE_DOUBLE) {
			const lineWidth = Math.max(ctx.options.lineWidth - ctx.indent.length, ctx.options.minContentWidth);
			const n = Math.ceil(str.length / lineWidth);
			const lines = new Array(n);
			for (let i = 0, o = 0; i < n; ++i, o += lineWidth) lines[i] = str.substr(o, lineWidth);
			str = lines.join(type === Scalar.BLOCK_LITERAL ? "\n" : " ");
		}
		return stringifyString({
			comment,
			type,
			value: str
		}, ctx, onComment, onChompKeep);
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/pairs.js
function resolvePairs(seq, onError) {
	if (isSeq(seq)) for (let i = 0; i < seq.items.length; ++i) {
		let item = seq.items[i];
		if (isPair(item)) continue;
		else if (isMap(item)) {
			if (item.items.length > 1) onError("Each pair must have its own sequence indicator");
			const pair = item.items[0] || new Pair(new Scalar(null));
			if (item.commentBefore) pair.key.commentBefore = pair.key.commentBefore ? `${item.commentBefore}\n${pair.key.commentBefore}` : item.commentBefore;
			if (item.comment) {
				const cn = pair.value ?? pair.key;
				cn.comment = cn.comment ? `${item.comment}\n${cn.comment}` : item.comment;
			}
			item = pair;
		}
		seq.items[i] = isPair(item) ? item : new Pair(item);
	}
	else onError("Expected a sequence for this tag");
	return seq;
}
function createPairs(schema, iterable, ctx) {
	const { replacer } = ctx;
	const pairs = new YAMLSeq(schema);
	pairs.tag = "tag:yaml.org,2002:pairs";
	let i = 0;
	if (iterable && Symbol.iterator in Object(iterable)) for (let it of iterable) {
		if (typeof replacer === "function") it = replacer.call(iterable, String(i++), it);
		let key, value;
		if (Array.isArray(it)) {
			if (it.length === 2) {
				key = it[0];
				value = it[1];
			} else throw new TypeError(`Expected [key, value] tuple: ${it}`);
		} else if (it && it instanceof Object) {
			const keys = Object.keys(it);
			if (keys.length === 1) {
				key = keys[0];
				value = it[key];
			} else throw new TypeError(`Expected tuple with one key, not ${keys.length} keys`);
		} else key = it;
		pairs.items.push(createPair(key, value, ctx));
	}
	return pairs;
}
var pairs = {
	collection: "seq",
	default: false,
	tag: "tag:yaml.org,2002:pairs",
	resolve: resolvePairs,
	createNode: createPairs
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/omap.js
var YAMLOMap = class YAMLOMap extends YAMLSeq {
	constructor() {
		super();
		this.add = YAMLMap.prototype.add.bind(this);
		this.delete = YAMLMap.prototype.delete.bind(this);
		this.get = YAMLMap.prototype.get.bind(this);
		this.has = YAMLMap.prototype.has.bind(this);
		this.set = YAMLMap.prototype.set.bind(this);
		this.tag = YAMLOMap.tag;
	}
	/**
	* If `ctx` is given, the return type is actually `Map<unknown, unknown>`,
	* but TypeScript won't allow widening the signature of a child method.
	*/
	toJSON(_, ctx) {
		if (!ctx) return super.toJSON(_);
		const map = /* @__PURE__ */ new Map();
		if (ctx?.onCreate) ctx.onCreate(map);
		for (const pair of this.items) {
			let key, value;
			if (isPair(pair)) {
				key = toJS(pair.key, "", ctx);
				value = toJS(pair.value, key, ctx);
			} else key = toJS(pair, "", ctx);
			if (map.has(key)) throw new Error("Ordered maps must not include duplicate keys");
			map.set(key, value);
		}
		return map;
	}
	static from(schema, iterable, ctx) {
		const pairs = createPairs(schema, iterable, ctx);
		const omap = new this();
		omap.items = pairs.items;
		return omap;
	}
};
YAMLOMap.tag = "tag:yaml.org,2002:omap";
var omap = {
	collection: "seq",
	identify: (value) => value instanceof Map,
	nodeClass: YAMLOMap,
	default: false,
	tag: "tag:yaml.org,2002:omap",
	resolve(seq, onError) {
		const pairs = resolvePairs(seq, onError);
		const seenKeys = [];
		for (const { key } of pairs.items) if (isScalar$1(key)) {
			if (seenKeys.includes(key.value)) onError(`Ordered maps must not include duplicate keys: ${key.value}`);
			else seenKeys.push(key.value);
		}
		return Object.assign(new YAMLOMap(), pairs);
	},
	createNode: (schema, iterable, ctx) => YAMLOMap.from(schema, iterable, ctx)
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/bool.js
function boolStringify({ value, source }, ctx) {
	if (source && (value ? trueTag : falseTag).test.test(source)) return source;
	return value ? ctx.options.trueStr : ctx.options.falseStr;
}
var trueTag = {
	identify: (value) => value === true,
	default: true,
	tag: "tag:yaml.org,2002:bool",
	test: /^(?:Y|y|[Yy]es|YES|[Tt]rue|TRUE|[Oo]n|ON)$/,
	resolve: () => new Scalar(true),
	stringify: boolStringify
};
var falseTag = {
	identify: (value) => value === false,
	default: true,
	tag: "tag:yaml.org,2002:bool",
	test: /^(?:N|n|[Nn]o|NO|[Ff]alse|FALSE|[Oo]ff|OFF)$/,
	resolve: () => new Scalar(false),
	stringify: boolStringify
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/float.js
var floatNaN = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	test: /^(?:[-+]?\.(?:inf|Inf|INF)|\.nan|\.NaN|\.NAN)$/,
	resolve: (str) => str.slice(-3).toLowerCase() === "nan" ? NaN : str[0] === "-" ? Number.NEGATIVE_INFINITY : Number.POSITIVE_INFINITY,
	stringify: stringifyNumber
};
var floatExp = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	format: "EXP",
	test: /^[-+]?(?:[0-9][0-9_]*)?(?:\.[0-9_]*)?[eE][-+]?[0-9]+$/,
	resolve: (str) => parseFloat(str.replace(/_/g, "")),
	stringify(node) {
		const num = Number(node.value);
		return isFinite(num) ? num.toExponential() : stringifyNumber(node);
	}
};
var float = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	test: /^[-+]?(?:[0-9][0-9_]*)?\.[0-9_]*$/,
	resolve(str) {
		const node = new Scalar(parseFloat(str.replace(/_/g, "")));
		const dot = str.indexOf(".");
		if (dot !== -1) {
			const f = str.substring(dot + 1).replace(/_/g, "");
			if (f[f.length - 1] === "0") node.minFractionDigits = f.length;
		}
		return node;
	},
	stringify: stringifyNumber
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/int.js
var intIdentify = (value) => typeof value === "bigint" || Number.isInteger(value);
function intResolve(str, offset, radix, { intAsBigInt }) {
	const sign = str[0];
	if (sign === "-" || sign === "+") offset += 1;
	str = str.substring(offset).replace(/_/g, "");
	if (intAsBigInt) {
		switch (radix) {
			case 2:
				str = `0b${str}`;
				break;
			case 8:
				str = `0o${str}`;
				break;
			case 16: str = `0x${str}`;
		}
		const n = BigInt(str);
		return sign === "-" ? BigInt(-1) * n : n;
	}
	const n = parseInt(str, radix);
	return sign === "-" ? -1 * n : n;
}
function intStringify(node, radix, prefix) {
	const { value } = node;
	if (intIdentify(value)) {
		const str = value.toString(radix);
		return value < 0 ? "-" + prefix + str.substr(1) : prefix + str;
	}
	return stringifyNumber(node);
}
var intBin = {
	identify: intIdentify,
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "BIN",
	test: /^[-+]?0b[0-1_]+$/,
	resolve: (str, _onError, opt) => intResolve(str, 2, 2, opt),
	stringify: (node) => intStringify(node, 2, "0b")
};
var intOct = {
	identify: intIdentify,
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "OCT",
	test: /^[-+]?0[0-7_]+$/,
	resolve: (str, _onError, opt) => intResolve(str, 1, 8, opt),
	stringify: (node) => intStringify(node, 8, "0")
};
var int = {
	identify: intIdentify,
	default: true,
	tag: "tag:yaml.org,2002:int",
	test: /^[-+]?[0-9][0-9_]*$/,
	resolve: (str, _onError, opt) => intResolve(str, 0, 10, opt),
	stringify: stringifyNumber
};
var intHex = {
	identify: intIdentify,
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "HEX",
	test: /^[-+]?0x[0-9a-fA-F_]+$/,
	resolve: (str, _onError, opt) => intResolve(str, 2, 16, opt),
	stringify: (node) => intStringify(node, 16, "0x")
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/set.js
var YAMLSet = class YAMLSet extends YAMLMap {
	constructor(schema) {
		super(schema);
		this.tag = YAMLSet.tag;
	}
	add(key) {
		let pair;
		if (isPair(key)) pair = key;
		else if (key && typeof key === "object" && "key" in key && "value" in key && key.value === null) pair = new Pair(key.key, null);
		else pair = new Pair(key, null);
		if (!findPair(this.items, pair.key)) this.items.push(pair);
	}
	/**
	* If `keepPair` is `true`, returns the Pair matching `key`.
	* Otherwise, returns the value of that Pair's key.
	*/
	get(key, keepPair) {
		const pair = findPair(this.items, key);
		return !keepPair && isPair(pair) ? isScalar$1(pair.key) ? pair.key.value : pair.key : pair;
	}
	set(key, value) {
		if (typeof value !== "boolean") throw new Error(`Expected boolean value for set(key, value) in a YAML set, not ${typeof value}`);
		const prev = findPair(this.items, key);
		if (prev && !value) this.items.splice(this.items.indexOf(prev), 1);
		else if (!prev && value) this.items.push(new Pair(key));
	}
	toJSON(_, ctx) {
		return super.toJSON(_, ctx, Set);
	}
	toString(ctx, onComment, onChompKeep) {
		if (!ctx) return JSON.stringify(this);
		if (this.hasAllNullValues(true)) return super.toString(Object.assign({}, ctx, { allNullValues: true }), onComment, onChompKeep);
		else throw new Error("Set items must all have null values");
	}
	static from(schema, iterable, ctx) {
		const { replacer } = ctx;
		const set = new this(schema);
		if (iterable && Symbol.iterator in Object(iterable)) for (let value of iterable) {
			if (typeof replacer === "function") value = replacer.call(iterable, value, value);
			set.items.push(createPair(value, null, ctx));
		}
		return set;
	}
};
YAMLSet.tag = "tag:yaml.org,2002:set";
var set = {
	collection: "map",
	identify: (value) => value instanceof Set,
	nodeClass: YAMLSet,
	default: false,
	tag: "tag:yaml.org,2002:set",
	createNode: (schema, iterable, ctx) => YAMLSet.from(schema, iterable, ctx),
	resolve(map, onError) {
		if (isMap(map)) {
			if (map.hasAllNullValues(true)) return Object.assign(new YAMLSet(), map);
			else onError("Set items must all have null values");
		} else onError("Expected a mapping for this tag");
		return map;
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/timestamp.js
/** Internal types handle bigint as number, because TS can't figure it out. */
function parseSexagesimal(str, asBigInt) {
	const sign = str[0];
	const parts = sign === "-" || sign === "+" ? str.substring(1) : str;
	const num = (n) => asBigInt ? BigInt(n) : Number(n);
	const res = parts.replace(/_/g, "").split(":").reduce((res, p) => res * num(60) + num(p), num(0));
	return sign === "-" ? num(-1) * res : res;
}
/**
* hhhh:mm:ss.sss
*
* Internal types handle bigint as number, because TS can't figure it out.
*/
function stringifySexagesimal(node) {
	let { value } = node;
	let num = (n) => n;
	if (typeof value === "bigint") num = (n) => BigInt(n);
	else if (isNaN(value) || !isFinite(value)) return stringifyNumber(node);
	let sign = "";
	if (value < 0) {
		sign = "-";
		value *= num(-1);
	}
	const _60 = num(60);
	const parts = [value % _60];
	if (value < 60) parts.unshift(0);
	else {
		value = (value - parts[0]) / _60;
		parts.unshift(value % _60);
		if (value >= 60) {
			value = (value - parts[0]) / _60;
			parts.unshift(value);
		}
	}
	return sign + parts.map((n) => String(n).padStart(2, "0")).join(":").replace(/000000\d*$/, "");
}
var intTime = {
	identify: (value) => typeof value === "bigint" || Number.isInteger(value),
	default: true,
	tag: "tag:yaml.org,2002:int",
	format: "TIME",
	test: /^[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+$/,
	resolve: (str, _onError, { intAsBigInt }) => parseSexagesimal(str, intAsBigInt),
	stringify: stringifySexagesimal
};
var floatTime = {
	identify: (value) => typeof value === "number",
	default: true,
	tag: "tag:yaml.org,2002:float",
	format: "TIME",
	test: /^[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\.[0-9_]*$/,
	resolve: (str) => parseSexagesimal(str, false),
	stringify: stringifySexagesimal
};
var timestamp = {
	identify: (value) => value instanceof Date,
	default: true,
	tag: "tag:yaml.org,2002:timestamp",
	test: RegExp("^([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})(?:(?:t|T|[ \\t]+)([0-9]{1,2}):([0-9]{1,2}):([0-9]{1,2}(\\.[0-9]+)?)(?:[ \\t]*(Z|[-+][012]?[0-9](?::[0-9]{2})?))?)?$"),
	resolve(str) {
		const match = str.match(timestamp.test);
		if (!match) throw new Error("!!timestamp expects a date, starting with yyyy-mm-dd");
		const [, year, month, day, hour, minute, second] = match.map(Number);
		const millisec = match[7] ? Number((match[7] + "00").substr(1, 3)) : 0;
		let date = Date.UTC(year, month - 1, day, hour || 0, minute || 0, second || 0, millisec);
		const tz = match[8];
		if (tz && tz !== "Z") {
			let d = parseSexagesimal(tz, false);
			if (Math.abs(d) < 30) d *= 60;
			date -= 6e4 * d;
		}
		return new Date(date);
	},
	stringify: ({ value }) => value?.toISOString().replace(/(T00:00:00)?\.000Z$/, "") ?? ""
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/yaml-1.1/schema.js
var schema = [
	map,
	seq,
	string,
	nullTag,
	trueTag,
	falseTag,
	intBin,
	intOct,
	int,
	intHex,
	floatNaN,
	floatExp,
	float,
	binary,
	merge,
	omap,
	pairs,
	set,
	intTime,
	floatTime,
	timestamp
];
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/tags.js
var schemas = /* @__PURE__ */ new Map([
	["core", schema$2],
	["failsafe", [
		map,
		seq,
		string
	]],
	["json", schema$1],
	["yaml11", schema],
	["yaml-1.1", schema]
]);
var tagsByName = {
	binary,
	bool: boolTag,
	float: float$1,
	floatExp: floatExp$1,
	floatNaN: floatNaN$1,
	floatTime,
	int: int$1,
	intHex: intHex$1,
	intOct: intOct$1,
	intTime,
	map,
	merge,
	null: nullTag,
	omap,
	pairs,
	seq,
	set,
	timestamp
};
var coreKnownTags = {
	"tag:yaml.org,2002:binary": binary,
	"tag:yaml.org,2002:merge": merge,
	"tag:yaml.org,2002:omap": omap,
	"tag:yaml.org,2002:pairs": pairs,
	"tag:yaml.org,2002:set": set,
	"tag:yaml.org,2002:timestamp": timestamp
};
function getTags(customTags, schemaName, addMergeTag) {
	const schemaTags = schemas.get(schemaName);
	if (schemaTags && !customTags) return addMergeTag && !schemaTags.includes(merge) ? schemaTags.concat(merge) : schemaTags.slice();
	let tags = schemaTags;
	if (!tags) {
		if (Array.isArray(customTags)) tags = [];
		else {
			const keys = Array.from(schemas.keys()).filter((key) => key !== "yaml11").map((key) => JSON.stringify(key)).join(", ");
			throw new Error(`Unknown schema "${schemaName}"; use one of ${keys} or define customTags array`);
		}
	}
	if (Array.isArray(customTags)) for (const tag of customTags) tags = tags.concat(tag);
	else if (typeof customTags === "function") tags = customTags(tags.slice());
	if (addMergeTag) tags = tags.concat(merge);
	return tags.reduce((tags, tag) => {
		const tagObj = typeof tag === "string" ? tagsByName[tag] : tag;
		if (!tagObj) {
			const tagName = JSON.stringify(tag);
			const keys = Object.keys(tagsByName).map((key) => JSON.stringify(key)).join(", ");
			throw new Error(`Unknown custom tag ${tagName}; use one of ${keys}`);
		}
		if (!tags.includes(tagObj)) tags.push(tagObj);
		return tags;
	}, []);
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/schema/Schema.js
var sortMapEntriesByKey = (a, b) => a.key < b.key ? -1 : a.key > b.key ? 1 : 0;
var Schema = class Schema {
	constructor({ compat, customTags, merge, resolveKnownTags, schema, sortMapEntries, toStringDefaults }) {
		this.compat = Array.isArray(compat) ? getTags(compat, "compat") : compat ? getTags(null, compat) : null;
		this.name = typeof schema === "string" && schema || "core";
		this.knownTags = resolveKnownTags ? coreKnownTags : {};
		this.tags = getTags(customTags, this.name, merge);
		this.toStringOptions = toStringDefaults ?? null;
		Object.defineProperty(this, MAP, { value: map });
		Object.defineProperty(this, SCALAR$1, { value: string });
		Object.defineProperty(this, SEQ, { value: seq });
		this.sortMapEntries = typeof sortMapEntries === "function" ? sortMapEntries : sortMapEntries === true ? sortMapEntriesByKey : null;
	}
	clone() {
		const copy = Object.create(Schema.prototype, Object.getOwnPropertyDescriptors(this));
		copy.tags = this.tags.slice();
		return copy;
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/stringify/stringifyDocument.js
function stringifyDocument(doc, options) {
	const lines = [];
	let hasDirectives = options.directives === true;
	if (options.directives !== false && doc.directives) {
		const dir = doc.directives.toString(doc);
		if (dir) {
			lines.push(dir);
			hasDirectives = true;
		} else if (doc.directives.docStart) hasDirectives = true;
	}
	if (hasDirectives) lines.push("---");
	const ctx = createStringifyContext(doc, options);
	const { commentString } = ctx.options;
	if (doc.commentBefore) {
		if (lines.length !== 1) lines.unshift("");
		const cs = commentString(doc.commentBefore);
		lines.unshift(indentComment(cs, ""));
	}
	let chompKeep = false;
	let contentComment = null;
	if (doc.contents) {
		if (isNode(doc.contents)) {
			if (doc.contents.spaceBefore && hasDirectives) lines.push("");
			if (doc.contents.commentBefore) {
				const cs = commentString(doc.contents.commentBefore);
				lines.push(indentComment(cs, ""));
			}
			ctx.forceBlockIndent = !!doc.comment;
			contentComment = doc.contents.comment;
		}
		const onChompKeep = contentComment ? void 0 : () => chompKeep = true;
		let body = stringify$2(doc.contents, ctx, () => contentComment = null, onChompKeep);
		if (contentComment) body += lineComment(body, "", commentString(contentComment));
		if ((body[0] === "|" || body[0] === ">") && lines[lines.length - 1] === "---") lines[lines.length - 1] = `--- ${body}`;
		else lines.push(body);
	} else lines.push(stringify$2(doc.contents, ctx));
	if (doc.directives?.docEnd) {
		if (doc.comment) {
			const cs = commentString(doc.comment);
			if (cs.includes("\n")) {
				lines.push("...");
				lines.push(indentComment(cs, ""));
			} else lines.push(`... ${cs}`);
		} else lines.push("...");
	} else {
		let dc = doc.comment;
		if (dc && chompKeep) dc = dc.replace(/^\n+/, "");
		if (dc) {
			if ((!chompKeep || contentComment) && lines[lines.length - 1] !== "") lines.push("");
			lines.push(indentComment(commentString(dc), ""));
		}
	}
	return lines.join("\n") + "\n";
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/doc/Document.js
var Document$1 = class Document$1 {
	constructor(value, replacer, options) {
		/** A comment before this Document */
		this.commentBefore = null;
		/** A comment immediately after this Document */
		this.comment = null;
		/** Errors encountered during parsing. */
		this.errors = [];
		/** Warnings encountered during parsing. */
		this.warnings = [];
		Object.defineProperty(this, NODE_TYPE, { value: DOC });
		let _replacer = null;
		if (typeof replacer === "function" || Array.isArray(replacer)) _replacer = replacer;
		else if (options === void 0 && replacer) {
			options = replacer;
			replacer = void 0;
		}
		const opt = Object.assign({
			intAsBigInt: false,
			keepSourceTokens: false,
			logLevel: "warn",
			prettyErrors: true,
			strict: true,
			stringKeys: false,
			uniqueKeys: true,
			version: "1.2"
		}, options);
		this.options = opt;
		let { version } = opt;
		if (options?._directives) {
			this.directives = options._directives.atDocument();
			if (this.directives.yaml.explicit) version = this.directives.yaml.version;
		} else this.directives = new Directives({ version });
		this.setSchema(version, options);
		this.contents = value === void 0 ? null : this.createNode(value, _replacer, options);
	}
	/**
	* Create a deep copy of this Document and its contents.
	*
	* Custom Node values that inherit from `Object` still refer to their original instances.
	*/
	clone() {
		const copy = Object.create(Document$1.prototype, { [NODE_TYPE]: { value: DOC } });
		copy.commentBefore = this.commentBefore;
		copy.comment = this.comment;
		copy.errors = this.errors.slice();
		copy.warnings = this.warnings.slice();
		copy.options = Object.assign({}, this.options);
		if (this.directives) copy.directives = this.directives.clone();
		copy.schema = this.schema.clone();
		copy.contents = isNode(this.contents) ? this.contents.clone(copy.schema) : this.contents;
		if (this.range) copy.range = this.range.slice();
		return copy;
	}
	/** Adds a value to the document. */
	add(value) {
		if (assertCollection(this.contents)) this.contents.add(value);
	}
	/** Adds a value to the document. */
	addIn(path, value) {
		if (assertCollection(this.contents)) this.contents.addIn(path, value);
	}
	/**
	* Create a new `Alias` node, ensuring that the target `node` has the required anchor.
	*
	* If `node` already has an anchor, `name` is ignored.
	* Otherwise, the `node.anchor` value will be set to `name`,
	* or if an anchor with that name is already present in the document,
	* `name` will be used as a prefix for a new unique anchor.
	* If `name` is undefined, the generated anchor will use 'a' as a prefix.
	*/
	createAlias(node, name) {
		if (!node.anchor) {
			const prev = anchorNames(this);
			node.anchor = !name || prev.has(name) ? findNewAnchor(name || "a", prev) : name;
		}
		return new Alias(node.anchor);
	}
	createNode(value, replacer, options) {
		let _replacer = void 0;
		if (typeof replacer === "function") {
			value = replacer.call({ "": value }, "", value);
			_replacer = replacer;
		} else if (Array.isArray(replacer)) {
			const keyToStr = (v) => typeof v === "number" || v instanceof String || v instanceof Number;
			const asStr = replacer.filter(keyToStr).map(String);
			if (asStr.length > 0) replacer = replacer.concat(asStr);
			_replacer = replacer;
		} else if (options === void 0 && replacer) {
			options = replacer;
			replacer = void 0;
		}
		const { aliasDuplicateObjects, anchorPrefix, flow, keepUndefined, onTagObj, tag } = options ?? {};
		const { onAnchor, setAnchors, sourceObjects } = createNodeAnchors(this, anchorPrefix || "a");
		const ctx = {
			aliasDuplicateObjects: aliasDuplicateObjects ?? true,
			keepUndefined: keepUndefined ?? false,
			onAnchor,
			onTagObj,
			replacer: _replacer,
			schema: this.schema,
			sourceObjects
		};
		const node = createNode(value, tag, ctx);
		if (flow && isCollection$1(node)) node.flow = true;
		setAnchors();
		return node;
	}
	/**
	* Convert a key and a value into a `Pair` using the current schema,
	* recursively wrapping all values as `Scalar` or `Collection` nodes.
	*/
	createPair(key, value, options = {}) {
		return new Pair(this.createNode(key, null, options), this.createNode(value, null, options));
	}
	/**
	* Removes a value from the document.
	* @returns `true` if the item was found and removed.
	*/
	delete(key) {
		return assertCollection(this.contents) ? this.contents.delete(key) : false;
	}
	/**
	* Removes a value from the document.
	* @returns `true` if the item was found and removed.
	*/
	deleteIn(path) {
		if (isEmptyPath(path)) {
			if (this.contents == null) return false;
			this.contents = null;
			return true;
		}
		return assertCollection(this.contents) ? this.contents.deleteIn(path) : false;
	}
	/**
	* Returns item at `key`, or `undefined` if not found. By default unwraps
	* scalar values from their surrounding node; to disable set `keepScalar` to
	* `true` (collections are always returned intact).
	*/
	get(key, keepScalar) {
		return isCollection$1(this.contents) ? this.contents.get(key, keepScalar) : void 0;
	}
	/**
	* Returns item at `path`, or `undefined` if not found. By default unwraps
	* scalar values from their surrounding node; to disable set `keepScalar` to
	* `true` (collections are always returned intact).
	*/
	getIn(path, keepScalar) {
		if (isEmptyPath(path)) return !keepScalar && isScalar$1(this.contents) ? this.contents.value : this.contents;
		return isCollection$1(this.contents) ? this.contents.getIn(path, keepScalar) : void 0;
	}
	/**
	* Checks if the document includes a value with the key `key`.
	*/
	has(key) {
		return isCollection$1(this.contents) ? this.contents.has(key) : false;
	}
	/**
	* Checks if the document includes a value at `path`.
	*/
	hasIn(path) {
		if (isEmptyPath(path)) return this.contents !== void 0;
		return isCollection$1(this.contents) ? this.contents.hasIn(path) : false;
	}
	/**
	* Sets a value in this document. For `!!set`, `value` needs to be a
	* boolean to add/remove the item from the set.
	*/
	set(key, value) {
		if (this.contents == null) this.contents = collectionFromPath(this.schema, [key], value);
		else if (assertCollection(this.contents)) this.contents.set(key, value);
	}
	/**
	* Sets a value in this document. For `!!set`, `value` needs to be a
	* boolean to add/remove the item from the set.
	*/
	setIn(path, value) {
		if (isEmptyPath(path)) this.contents = value;
		else if (this.contents == null) this.contents = collectionFromPath(this.schema, Array.from(path), value);
		else if (assertCollection(this.contents)) this.contents.setIn(path, value);
	}
	/**
	* Change the YAML version and schema used by the document.
	* A `null` version disables support for directives, explicit tags, anchors, and aliases.
	* It also requires the `schema` option to be given as a `Schema` instance value.
	*
	* Overrides all previously set schema options.
	*/
	setSchema(version, options = {}) {
		if (typeof version === "number") version = String(version);
		let opt;
		switch (version) {
			case "1.1":
				if (this.directives) this.directives.yaml.version = "1.1";
				else this.directives = new Directives({ version: "1.1" });
				opt = {
					resolveKnownTags: false,
					schema: "yaml-1.1"
				};
				break;
			case "1.2":
			case "next":
				if (this.directives) this.directives.yaml.version = version;
				else this.directives = new Directives({ version });
				opt = {
					resolveKnownTags: true,
					schema: "core"
				};
				break;
			case null:
				if (this.directives) delete this.directives;
				opt = null;
				break;
			default: {
				const sv = JSON.stringify(version);
				throw new Error(`Expected '1.1', '1.2' or null as first argument, but found: ${sv}`);
			}
		}
		if (options.schema instanceof Object) this.schema = options.schema;
		else if (opt) this.schema = new Schema(Object.assign(opt, options));
		else throw new Error(`With a null YAML version, the { schema: Schema } option is required`);
	}
	toJS({ json, jsonArg, mapAsMap, maxAliasCount, onAnchor, reviver } = {}) {
		const ctx = {
			anchors: /* @__PURE__ */ new Map(),
			doc: this,
			keep: !json,
			mapAsMap: mapAsMap === true,
			mapKeyWarned: false,
			maxAliasCount: typeof maxAliasCount === "number" ? maxAliasCount : 100
		};
		const res = toJS(this.contents, jsonArg ?? "", ctx);
		if (typeof onAnchor === "function") for (const { count, res } of ctx.anchors.values()) onAnchor(res, count);
		return typeof reviver === "function" ? applyReviver(reviver, { "": res }, "", res) : res;
	}
	/**
	* A JSON representation of the document `contents`.
	*
	* @param jsonArg Used by `JSON.stringify` to indicate the array index or
	*   property name.
	*/
	toJSON(jsonArg, onAnchor) {
		return this.toJS({
			json: true,
			jsonArg,
			mapAsMap: false,
			onAnchor
		});
	}
	/** A YAML representation of the document. */
	toString(options = {}) {
		if (this.errors.length > 0) throw new Error("Document with errors cannot be stringified");
		if ("indent" in options && (!Number.isInteger(options.indent) || Number(options.indent) <= 0)) {
			const s = JSON.stringify(options.indent);
			throw new Error(`"indent" option must be a positive integer, not ${s}`);
		}
		return stringifyDocument(this, options);
	}
};
function assertCollection(contents) {
	if (isCollection$1(contents)) return true;
	throw new Error("Expected a YAML collection as document contents");
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/errors.js
var YAMLError = class extends Error {
	constructor(name, pos, code, message) {
		super();
		this.name = name;
		this.code = code;
		this.message = message;
		this.pos = pos;
	}
};
var YAMLParseError = class extends YAMLError {
	constructor(pos, code, message) {
		super("YAMLParseError", pos, code, message);
	}
};
var YAMLWarning = class extends YAMLError {
	constructor(pos, code, message) {
		super("YAMLWarning", pos, code, message);
	}
};
var prettifyError = (src, lc) => (error) => {
	if (error.pos[0] === -1) return;
	error.linePos = error.pos.map((pos) => lc.linePos(pos));
	const { line, col } = error.linePos[0];
	error.message += ` at line ${line}, column ${col}`;
	let ci = col - 1;
	let lineStr = src.substring(lc.lineStarts[line - 1], lc.lineStarts[line]).replace(/[\n\r]+$/, "");
	if (ci >= 60 && lineStr.length > 80) {
		const trimStart = Math.min(ci - 39, lineStr.length - 79);
		lineStr = "…" + lineStr.substring(trimStart);
		ci -= trimStart - 1;
	}
	if (lineStr.length > 80) lineStr = lineStr.substring(0, 79) + "…";
	if (line > 1 && /^ *$/.test(lineStr.substring(0, ci))) {
		let prev = src.substring(lc.lineStarts[line - 2], lc.lineStarts[line - 1]);
		if (prev.length > 80) prev = prev.substring(0, 79) + "…\n";
		lineStr = prev + lineStr;
	}
	if (/[^ ]/.test(lineStr)) {
		let count = 1;
		const end = error.linePos[1];
		if (end?.line === line && end.col > col) count = Math.max(1, Math.min(end.col - col, 80 - ci));
		const pointer = " ".repeat(ci) + "^".repeat(count);
		error.message += `:\n\n${lineStr}\n${pointer}\n`;
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-props.js
function resolveProps(tokens, { flow, indicator, next, offset, onError, parentIndent, startOnNewline }) {
	let spaceBefore = false;
	let atNewline = startOnNewline;
	let hasSpace = startOnNewline;
	let comment = "";
	let commentSep = "";
	let hasNewline = false;
	let reqSpace = false;
	let tab = null;
	let anchor = null;
	let tag = null;
	let newlineAfterProp = null;
	let comma = null;
	let found = null;
	let start = null;
	for (const token of tokens) {
		if (reqSpace) {
			if (token.type !== "space" && token.type !== "newline" && token.type !== "comma") onError(token.offset, "MISSING_CHAR", "Tags and anchors must be separated from the next token by white space");
			reqSpace = false;
		}
		if (tab) {
			if (atNewline && token.type !== "comment" && token.type !== "newline") onError(tab, "TAB_AS_INDENT", "Tabs are not allowed as indentation");
			tab = null;
		}
		switch (token.type) {
			case "space":
				if (!flow && (indicator !== "doc-start" || next?.type !== "flow-collection") && token.source.includes("	")) tab = token;
				hasSpace = true;
				break;
			case "comment": {
				if (!hasSpace) onError(token, "MISSING_CHAR", "Comments must be separated from other tokens by white space characters");
				const cb = token.source.substring(1) || " ";
				if (!comment) comment = cb;
				else comment += commentSep + cb;
				commentSep = "";
				atNewline = false;
				break;
			}
			case "newline":
				if (atNewline) {
					if (comment) comment += token.source;
					else if (!found || indicator !== "seq-item-ind") spaceBefore = true;
				} else commentSep += token.source;
				atNewline = true;
				hasNewline = true;
				if (anchor || tag) newlineAfterProp = token;
				hasSpace = true;
				break;
			case "anchor":
				if (anchor) onError(token, "MULTIPLE_ANCHORS", "A node can have at most one anchor");
				if (token.source.endsWith(":")) onError(token.offset + token.source.length - 1, "BAD_ALIAS", "Anchor ending in : is ambiguous", true);
				anchor = token;
				start ?? (start = token.offset);
				atNewline = false;
				hasSpace = false;
				reqSpace = true;
				break;
			case "tag":
				if (tag) onError(token, "MULTIPLE_TAGS", "A node can have at most one tag");
				tag = token;
				start ?? (start = token.offset);
				atNewline = false;
				hasSpace = false;
				reqSpace = true;
				break;
			case indicator:
				if (anchor || tag) onError(token, "BAD_PROP_ORDER", `Anchors and tags must be after the ${token.source} indicator`);
				if (found) onError(token, "UNEXPECTED_TOKEN", `Unexpected ${token.source} in ${flow ?? "collection"}`);
				found = token;
				atNewline = indicator === "seq-item-ind" || indicator === "explicit-key-ind";
				hasSpace = false;
				break;
			case "comma": if (flow) {
				if (comma) onError(token, "UNEXPECTED_TOKEN", `Unexpected , in ${flow}`);
				comma = token;
				atNewline = false;
				hasSpace = false;
				break;
			}
			default:
				onError(token, "UNEXPECTED_TOKEN", `Unexpected ${token.type} token`);
				atNewline = false;
				hasSpace = false;
		}
	}
	const last = tokens[tokens.length - 1];
	const end = last ? last.offset + last.source.length : offset;
	if (reqSpace && next && next.type !== "space" && next.type !== "newline" && next.type !== "comma" && (next.type !== "scalar" || next.source !== "")) onError(next.offset, "MISSING_CHAR", "Tags and anchors must be separated from the next token by white space");
	if (tab && (atNewline && tab.indent <= parentIndent || next?.type === "block-map" || next?.type === "block-seq")) onError(tab, "TAB_AS_INDENT", "Tabs are not allowed as indentation");
	return {
		comma,
		found,
		spaceBefore,
		comment,
		hasNewline,
		anchor,
		tag,
		newlineAfterProp,
		end,
		start: start ?? end
	};
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/util-contains-newline.js
function containsNewline(key) {
	if (!key) return null;
	switch (key.type) {
		case "alias":
		case "scalar":
		case "double-quoted-scalar":
		case "single-quoted-scalar":
			if (key.source.includes("\n")) return true;
			if (key.end) {
				for (const st of key.end) if (st.type === "newline") return true;
			}
			return false;
		case "flow-collection":
			for (const it of key.items) {
				for (const st of it.start) if (st.type === "newline") return true;
				if (it.sep) {
					for (const st of it.sep) if (st.type === "newline") return true;
				}
				if (containsNewline(it.key) || containsNewline(it.value)) return true;
			}
			return false;
		default: return true;
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/util-flow-indent-check.js
function flowIndentCheck(indent, fc, onError) {
	if (fc?.type === "flow-collection") {
		const end = fc.end[0];
		if (end.indent === indent && (end.source === "]" || end.source === "}") && containsNewline(fc)) onError(end, "BAD_INDENT", "Flow end indicator should be more indented than parent", true);
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/util-map-includes.js
function mapIncludes(ctx, items, search) {
	const { uniqueKeys } = ctx.options;
	if (uniqueKeys === false) return false;
	const isEqual = typeof uniqueKeys === "function" ? uniqueKeys : (a, b) => a === b || isScalar$1(a) && isScalar$1(b) && a.value === b.value;
	return items.some((pair) => isEqual(pair.key, search));
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-block-map.js
var startColMsg = "All mapping items must start at the same column";
function resolveBlockMap({ composeNode, composeEmptyNode }, ctx, bm, onError, tag) {
	const map = new ((tag?.nodeClass) ?? YAMLMap)(ctx.schema);
	if (ctx.atRoot) ctx.atRoot = false;
	let offset = bm.offset;
	let commentEnd = null;
	for (const collItem of bm.items) {
		const { start, key, sep, value } = collItem;
		const keyProps = resolveProps(start, {
			indicator: "explicit-key-ind",
			next: key ?? sep?.[0],
			offset,
			onError,
			parentIndent: bm.indent,
			startOnNewline: true
		});
		const implicitKey = !keyProps.found;
		if (implicitKey) {
			if (key) {
				if (key.type === "block-seq") onError(offset, "BLOCK_AS_IMPLICIT_KEY", "A block sequence may not be used as an implicit map key");
				else if ("indent" in key && key.indent !== bm.indent) onError(offset, "BAD_INDENT", startColMsg);
			}
			if (!keyProps.anchor && !keyProps.tag && !sep) {
				commentEnd = keyProps.end;
				if (keyProps.comment) {
					if (map.comment) map.comment += "\n" + keyProps.comment;
					else map.comment = keyProps.comment;
				}
				continue;
			}
			if (keyProps.newlineAfterProp || containsNewline(key)) onError(key ?? start[start.length - 1], "MULTILINE_IMPLICIT_KEY", "Implicit keys need to be on a single line");
		} else if (keyProps.found?.indent !== bm.indent) onError(offset, "BAD_INDENT", startColMsg);
		ctx.atKey = true;
		const keyStart = keyProps.end;
		const keyNode = key ? composeNode(ctx, key, keyProps, onError) : composeEmptyNode(ctx, keyStart, start, null, keyProps, onError);
		if (ctx.schema.compat) flowIndentCheck(bm.indent, key, onError);
		ctx.atKey = false;
		if (mapIncludes(ctx, map.items, keyNode)) onError(keyStart, "DUPLICATE_KEY", "Map keys must be unique");
		const valueProps = resolveProps(sep ?? [], {
			indicator: "map-value-ind",
			next: value,
			offset: keyNode.range[2],
			onError,
			parentIndent: bm.indent,
			startOnNewline: !key || key.type === "block-scalar"
		});
		offset = valueProps.end;
		if (valueProps.found) {
			if (implicitKey) {
				if (value?.type === "block-map" && !valueProps.hasNewline) onError(offset, "BLOCK_AS_IMPLICIT_KEY", "Nested mappings are not allowed in compact mappings");
				if (ctx.options.strict && keyProps.start < valueProps.found.offset - 1024) onError(keyNode.range, "KEY_OVER_1024_CHARS", "The : indicator must be at most 1024 chars after the start of an implicit block mapping key");
			}
			const valueNode = value ? composeNode(ctx, value, valueProps, onError) : composeEmptyNode(ctx, offset, sep, null, valueProps, onError);
			if (ctx.schema.compat) flowIndentCheck(bm.indent, value, onError);
			offset = valueNode.range[2];
			const pair = new Pair(keyNode, valueNode);
			if (ctx.options.keepSourceTokens) pair.srcToken = collItem;
			map.items.push(pair);
		} else {
			if (implicitKey) onError(keyNode.range, "MISSING_CHAR", "Implicit map keys need to be followed by map values");
			if (valueProps.comment) {
				if (keyNode.comment) keyNode.comment += "\n" + valueProps.comment;
				else keyNode.comment = valueProps.comment;
			}
			const pair = new Pair(keyNode);
			if (ctx.options.keepSourceTokens) pair.srcToken = collItem;
			map.items.push(pair);
		}
	}
	if (commentEnd && commentEnd < offset) onError(commentEnd, "IMPOSSIBLE", "Map comment with trailing content");
	map.range = [
		bm.offset,
		offset,
		commentEnd ?? offset
	];
	return map;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-block-seq.js
function resolveBlockSeq({ composeNode, composeEmptyNode }, ctx, bs, onError, tag) {
	const seq = new ((tag?.nodeClass) ?? YAMLSeq)(ctx.schema);
	if (ctx.atRoot) ctx.atRoot = false;
	if (ctx.atKey) ctx.atKey = false;
	let offset = bs.offset;
	let commentEnd = null;
	for (const { start, value } of bs.items) {
		const props = resolveProps(start, {
			indicator: "seq-item-ind",
			next: value,
			offset,
			onError,
			parentIndent: bs.indent,
			startOnNewline: true
		});
		if (!props.found) {
			if (props.anchor || props.tag || value) {
				if (value?.type === "block-seq") onError(props.end, "BAD_INDENT", "All sequence items must start at the same column");
				else onError(offset, "MISSING_CHAR", "Sequence item without - indicator");
			} else {
				commentEnd = props.end;
				if (props.comment) seq.comment = props.comment;
				continue;
			}
		}
		const node = value ? composeNode(ctx, value, props, onError) : composeEmptyNode(ctx, props.end, start, null, props, onError);
		if (ctx.schema.compat) flowIndentCheck(bs.indent, value, onError);
		offset = node.range[2];
		seq.items.push(node);
	}
	seq.range = [
		bs.offset,
		offset,
		commentEnd ?? offset
	];
	return seq;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-end.js
function resolveEnd(end, offset, reqSpace, onError) {
	let comment = "";
	if (end) {
		let hasSpace = false;
		let sep = "";
		for (const token of end) {
			const { source, type } = token;
			switch (type) {
				case "space":
					hasSpace = true;
					break;
				case "comment": {
					if (reqSpace && !hasSpace) onError(token, "MISSING_CHAR", "Comments must be separated from other tokens by white space characters");
					const cb = source.substring(1) || " ";
					if (!comment) comment = cb;
					else comment += sep + cb;
					sep = "";
					break;
				}
				case "newline":
					if (comment) sep += source;
					hasSpace = true;
					break;
				default: onError(token, "UNEXPECTED_TOKEN", `Unexpected ${type} at node end`);
			}
			offset += source.length;
		}
	}
	return {
		comment,
		offset
	};
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-flow-collection.js
var blockMsg = "Block collections are not allowed within flow collections";
var isBlock = (token) => token && (token.type === "block-map" || token.type === "block-seq");
function resolveFlowCollection({ composeNode, composeEmptyNode }, ctx, fc, onError, tag) {
	const isMap = fc.start.source === "{";
	const fcName = isMap ? "flow map" : "flow sequence";
	const coll = new ((tag?.nodeClass) ?? (isMap ? YAMLMap : YAMLSeq))(ctx.schema);
	coll.flow = true;
	const atRoot = ctx.atRoot;
	if (atRoot) ctx.atRoot = false;
	if (ctx.atKey) ctx.atKey = false;
	let offset = fc.offset + fc.start.source.length;
	for (let i = 0; i < fc.items.length; ++i) {
		const collItem = fc.items[i];
		const { start, key, sep, value } = collItem;
		const props = resolveProps(start, {
			flow: fcName,
			indicator: "explicit-key-ind",
			next: key ?? sep?.[0],
			offset,
			onError,
			parentIndent: fc.indent,
			startOnNewline: false
		});
		if (!props.found) {
			if (!props.anchor && !props.tag && !sep && !value) {
				if (i === 0 && props.comma) onError(props.comma, "UNEXPECTED_TOKEN", `Unexpected , in ${fcName}`);
				else if (i < fc.items.length - 1) onError(props.start, "UNEXPECTED_TOKEN", `Unexpected empty item in ${fcName}`);
				if (props.comment) {
					if (coll.comment) coll.comment += "\n" + props.comment;
					else coll.comment = props.comment;
				}
				offset = props.end;
				continue;
			}
			if (!isMap && ctx.options.strict && containsNewline(key)) onError(key, "MULTILINE_IMPLICIT_KEY", "Implicit keys of flow sequence pairs need to be on a single line");
		}
		if (i === 0) {
			if (props.comma) onError(props.comma, "UNEXPECTED_TOKEN", `Unexpected , in ${fcName}`);
		} else {
			if (!props.comma) onError(props.start, "MISSING_CHAR", `Missing , between ${fcName} items`);
			if (props.comment) {
				let prevItemComment = "";
				loop: for (const st of start) switch (st.type) {
					case "comma":
					case "space": break;
					case "comment":
						prevItemComment = st.source.substring(1);
						break loop;
					default: break loop;
				}
				if (prevItemComment) {
					let prev = coll.items[coll.items.length - 1];
					if (isPair(prev)) prev = prev.value ?? prev.key;
					if (prev.comment) prev.comment += "\n" + prevItemComment;
					else prev.comment = prevItemComment;
					props.comment = props.comment.substring(prevItemComment.length + 1);
				}
			}
		}
		if (!isMap && !sep && !props.found) {
			const valueNode = value ? composeNode(ctx, value, props, onError) : composeEmptyNode(ctx, props.end, sep, null, props, onError);
			coll.items.push(valueNode);
			offset = valueNode.range[2];
			if (isBlock(value)) onError(valueNode.range, "BLOCK_IN_FLOW", blockMsg);
		} else {
			ctx.atKey = true;
			const keyStart = props.end;
			const keyNode = key ? composeNode(ctx, key, props, onError) : composeEmptyNode(ctx, keyStart, start, null, props, onError);
			if (isBlock(key)) onError(keyNode.range, "BLOCK_IN_FLOW", blockMsg);
			ctx.atKey = false;
			const valueProps = resolveProps(sep ?? [], {
				flow: fcName,
				indicator: "map-value-ind",
				next: value,
				offset: keyNode.range[2],
				onError,
				parentIndent: fc.indent,
				startOnNewline: false
			});
			if (valueProps.found) {
				if (!isMap && !props.found && ctx.options.strict) {
					if (sep) for (const st of sep) {
						if (st === valueProps.found) break;
						if (st.type === "newline") {
							onError(st, "MULTILINE_IMPLICIT_KEY", "Implicit keys of flow sequence pairs need to be on a single line");
							break;
						}
					}
					if (props.start < valueProps.found.offset - 1024) onError(valueProps.found, "KEY_OVER_1024_CHARS", "The : indicator must be at most 1024 chars after the start of an implicit flow sequence key");
				}
			} else if (value) {
				if ("source" in value && value.source?.[0] === ":") onError(value, "MISSING_CHAR", `Missing space after : in ${fcName}`);
				else onError(valueProps.start, "MISSING_CHAR", `Missing , or : between ${fcName} items`);
			}
			const valueNode = value ? composeNode(ctx, value, valueProps, onError) : valueProps.found ? composeEmptyNode(ctx, valueProps.end, sep, null, valueProps, onError) : null;
			if (valueNode) {
				if (isBlock(value)) onError(valueNode.range, "BLOCK_IN_FLOW", blockMsg);
			} else if (valueProps.comment) {
				if (keyNode.comment) keyNode.comment += "\n" + valueProps.comment;
				else keyNode.comment = valueProps.comment;
			}
			const pair = new Pair(keyNode, valueNode);
			if (ctx.options.keepSourceTokens) pair.srcToken = collItem;
			if (isMap) {
				const map = coll;
				if (mapIncludes(ctx, map.items, keyNode)) onError(keyStart, "DUPLICATE_KEY", "Map keys must be unique");
				map.items.push(pair);
			} else {
				const map = new YAMLMap(ctx.schema);
				map.flow = true;
				map.items.push(pair);
				const endRange = (valueNode ?? keyNode).range;
				map.range = [
					keyNode.range[0],
					endRange[1],
					endRange[2]
				];
				coll.items.push(map);
			}
			offset = valueNode ? valueNode.range[2] : valueProps.end;
		}
	}
	const expectedEnd = isMap ? "}" : "]";
	const [ce, ...ee] = fc.end;
	let cePos = offset;
	if (ce?.source === expectedEnd) cePos = ce.offset + ce.source.length;
	else {
		const name = fcName[0].toUpperCase() + fcName.substring(1);
		const msg = atRoot ? `${name} must end with a ${expectedEnd}` : `${name} in block collection must be sufficiently indented and end with a ${expectedEnd}`;
		onError(offset, atRoot ? "MISSING_CHAR" : "BAD_INDENT", msg);
		if (ce && ce.source.length !== 1) ee.unshift(ce);
	}
	if (ee.length > 0) {
		const end = resolveEnd(ee, cePos, ctx.options.strict, onError);
		if (end.comment) {
			if (coll.comment) coll.comment += "\n" + end.comment;
			else coll.comment = end.comment;
		}
		coll.range = [
			fc.offset,
			cePos,
			end.offset
		];
	} else coll.range = [
		fc.offset,
		cePos,
		cePos
	];
	return coll;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/compose-collection.js
function resolveCollection(CN, ctx, token, onError, tagName, tag) {
	const coll = token.type === "block-map" ? resolveBlockMap(CN, ctx, token, onError, tag) : token.type === "block-seq" ? resolveBlockSeq(CN, ctx, token, onError, tag) : resolveFlowCollection(CN, ctx, token, onError, tag);
	const Coll = coll.constructor;
	if (tagName === "!" || tagName === Coll.tagName) {
		coll.tag = Coll.tagName;
		return coll;
	}
	if (tagName) coll.tag = tagName;
	return coll;
}
function composeCollection(CN, ctx, token, props, onError) {
	const tagToken = props.tag;
	const tagName = !tagToken ? null : ctx.directives.tagName(tagToken.source, (msg) => onError(tagToken, "TAG_RESOLVE_FAILED", msg));
	if (token.type === "block-seq") {
		const { anchor, newlineAfterProp: nl } = props;
		const lastProp = anchor && tagToken ? anchor.offset > tagToken.offset ? anchor : tagToken : anchor ?? tagToken;
		if (lastProp && (!nl || nl.offset < lastProp.offset)) onError(lastProp, "MISSING_CHAR", "Missing newline after block sequence props");
	}
	const expType = token.type === "block-map" ? "map" : token.type === "block-seq" ? "seq" : token.start.source === "{" ? "map" : "seq";
	if (!tagToken || !tagName || tagName === "!" || tagName === YAMLMap.tagName && expType === "map" || tagName === YAMLSeq.tagName && expType === "seq") return resolveCollection(CN, ctx, token, onError, tagName);
	let tag = ctx.schema.tags.find((t) => t.tag === tagName && t.collection === expType);
	if (!tag) {
		const kt = ctx.schema.knownTags[tagName];
		if (kt?.collection === expType) {
			ctx.schema.tags.push(Object.assign({}, kt, { default: false }));
			tag = kt;
		} else {
			if (kt) onError(tagToken, "BAD_COLLECTION_TYPE", `${kt.tag} used for ${expType} collection, but expects ${kt.collection ?? "scalar"}`, true);
			else onError(tagToken, "TAG_RESOLVE_FAILED", `Unresolved tag: ${tagName}`, true);
			return resolveCollection(CN, ctx, token, onError, tagName);
		}
	}
	const coll = resolveCollection(CN, ctx, token, onError, tagName, tag);
	const res = tag.resolve?.(coll, (msg) => onError(tagToken, "TAG_RESOLVE_FAILED", msg), ctx.options) ?? coll;
	const node = isNode(res) ? res : new Scalar(res);
	node.range = coll.range;
	node.tag = tagName;
	if (tag?.format) node.format = tag.format;
	return node;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-block-scalar.js
function resolveBlockScalar(ctx, scalar, onError) {
	const start = scalar.offset;
	const header = parseBlockScalarHeader(scalar, ctx.options.strict, onError);
	if (!header) return {
		value: "",
		type: null,
		comment: "",
		range: [
			start,
			start,
			start
		]
	};
	const type = header.mode === ">" ? Scalar.BLOCK_FOLDED : Scalar.BLOCK_LITERAL;
	const lines = scalar.source ? splitLines(scalar.source) : [];
	let chompStart = lines.length;
	for (let i = lines.length - 1; i >= 0; --i) {
		const content = lines[i][1];
		if (content === "" || content === "\r") chompStart = i;
		else break;
	}
	if (chompStart === 0) {
		const value = header.chomp === "+" && lines.length > 0 ? "\n".repeat(Math.max(1, lines.length - 1)) : "";
		let end = start + header.length;
		if (scalar.source) end += scalar.source.length;
		return {
			value,
			type,
			comment: header.comment,
			range: [
				start,
				end,
				end
			]
		};
	}
	let trimIndent = scalar.indent + header.indent;
	let offset = scalar.offset + header.length;
	let contentStart = 0;
	for (let i = 0; i < chompStart; ++i) {
		const [indent, content] = lines[i];
		if (content === "" || content === "\r") {
			if (header.indent === 0 && indent.length > trimIndent) trimIndent = indent.length;
		} else {
			if (indent.length < trimIndent) onError(offset + indent.length, "MISSING_CHAR", "Block scalars with more-indented leading empty lines must use an explicit indentation indicator");
			if (header.indent === 0) trimIndent = indent.length;
			contentStart = i;
			if (trimIndent === 0 && !ctx.atRoot) onError(offset, "BAD_INDENT", "Block scalar values in collections must be indented");
			break;
		}
		offset += indent.length + content.length + 1;
	}
	for (let i = lines.length - 1; i >= chompStart; --i) if (lines[i][0].length > trimIndent) chompStart = i + 1;
	let value = "";
	let sep = "";
	let prevMoreIndented = false;
	for (let i = 0; i < contentStart; ++i) value += lines[i][0].slice(trimIndent) + "\n";
	for (let i = contentStart; i < chompStart; ++i) {
		let [indent, content] = lines[i];
		offset += indent.length + content.length + 1;
		const crlf = content[content.length - 1] === "\r";
		if (crlf) content = content.slice(0, -1);
		/* istanbul ignore if already caught in lexer */
		if (content && indent.length < trimIndent) {
			const message = `Block scalar lines must not be less indented than their ${header.indent ? "explicit indentation indicator" : "first line"}`;
			onError(offset - content.length - (crlf ? 2 : 1), "BAD_INDENT", message);
			indent = "";
		}
		if (type === Scalar.BLOCK_LITERAL) {
			value += sep + indent.slice(trimIndent) + content;
			sep = "\n";
		} else if (indent.length > trimIndent || content[0] === "	") {
			if (sep === " ") sep = "\n";
			else if (!prevMoreIndented && sep === "\n") sep = "\n\n";
			value += sep + indent.slice(trimIndent) + content;
			sep = "\n";
			prevMoreIndented = true;
		} else if (content === "") {
			if (sep === "\n") value += "\n";
			else sep = "\n";
		} else {
			value += sep + content;
			sep = " ";
			prevMoreIndented = false;
		}
	}
	switch (header.chomp) {
		case "-": break;
		case "+":
			for (let i = chompStart; i < lines.length; ++i) value += "\n" + lines[i][0].slice(trimIndent);
			if (value[value.length - 1] !== "\n") value += "\n";
			break;
		default: value += "\n";
	}
	const end = start + header.length + scalar.source.length;
	return {
		value,
		type,
		comment: header.comment,
		range: [
			start,
			end,
			end
		]
	};
}
function parseBlockScalarHeader({ offset, props }, strict, onError) {
	/* istanbul ignore if should not happen */
	if (props[0].type !== "block-scalar-header") {
		onError(props[0], "IMPOSSIBLE", "Block scalar header not found");
		return null;
	}
	const { source } = props[0];
	const mode = source[0];
	let indent = 0;
	let chomp = "";
	let error = -1;
	for (let i = 1; i < source.length; ++i) {
		const ch = source[i];
		if (!chomp && (ch === "-" || ch === "+")) chomp = ch;
		else {
			const n = Number(ch);
			if (!indent && n) indent = n;
			else if (error === -1) error = offset + i;
		}
	}
	if (error !== -1) onError(error, "UNEXPECTED_TOKEN", `Block scalar header includes extra characters: ${source}`);
	let hasSpace = false;
	let comment = "";
	let length = source.length;
	for (let i = 1; i < props.length; ++i) {
		const token = props[i];
		switch (token.type) {
			case "space": hasSpace = true;
			case "newline":
				length += token.source.length;
				break;
			case "comment":
				if (strict && !hasSpace) onError(token, "MISSING_CHAR", "Comments must be separated from other tokens by white space characters");
				length += token.source.length;
				comment = token.source.substring(1);
				break;
			case "error":
				onError(token, "UNEXPECTED_TOKEN", token.message);
				length += token.source.length;
				break;
			/* istanbul ignore next should not happen */
			default: {
				onError(token, "UNEXPECTED_TOKEN", `Unexpected token in block scalar header: ${token.type}`);
				const ts = token.source;
				if (ts && typeof ts === "string") length += ts.length;
			}
		}
	}
	return {
		mode,
		indent,
		chomp,
		comment,
		length
	};
}
/** @returns Array of lines split up as `[indent, content]` */
function splitLines(source) {
	const split = source.split(/\n( *)/);
	const first = split[0];
	const m = first.match(/^( *)/);
	const lines = [m?.[1] ? [m[1], first.slice(m[1].length)] : ["", first]];
	for (let i = 1; i < split.length; i += 2) lines.push([split[i], split[i + 1]]);
	return lines;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/resolve-flow-scalar.js
function resolveFlowScalar(scalar, strict, onError) {
	const { offset, type, source, end } = scalar;
	let _type;
	let value;
	const _onError = (rel, code, msg) => onError(offset + rel, code, msg);
	switch (type) {
		case "scalar":
			_type = Scalar.PLAIN;
			value = plainValue(source, _onError);
			break;
		case "single-quoted-scalar":
			_type = Scalar.QUOTE_SINGLE;
			value = singleQuotedValue(source, _onError);
			break;
		case "double-quoted-scalar":
			_type = Scalar.QUOTE_DOUBLE;
			value = doubleQuotedValue(source, _onError);
			break;
		/* istanbul ignore next should not happen */
		default:
			onError(scalar, "UNEXPECTED_TOKEN", `Expected a flow scalar value, but found: ${type}`);
			return {
				value: "",
				type: null,
				comment: "",
				range: [
					offset,
					offset + source.length,
					offset + source.length
				]
			};
	}
	const valueEnd = offset + source.length;
	const re = resolveEnd(end, valueEnd, strict, onError);
	return {
		value,
		type: _type,
		comment: re.comment,
		range: [
			offset,
			valueEnd,
			re.offset
		]
	};
}
function plainValue(source, onError) {
	let badChar = "";
	switch (source[0]) {
		/* istanbul ignore next should not happen */
		case "	":
			badChar = "a tab character";
			break;
		case ",":
			badChar = "flow indicator character ,";
			break;
		case "%":
			badChar = "directive indicator character %";
			break;
		case "|":
		case ">":
			badChar = `block scalar indicator ${source[0]}`;
			break;
		case "@":
		case "`": badChar = `reserved character ${source[0]}`;
	}
	if (badChar) onError(0, "BAD_SCALAR_START", `Plain value cannot start with ${badChar}`);
	return unfoldLines(source);
}
function singleQuotedValue(source, onError) {
	if (source[source.length - 1] !== "'" || source.length === 1) onError(source.length, "MISSING_CHAR", "Missing closing 'quote");
	return unfoldLines(source.slice(1, -1)).replace(/''/g, "'");
}
function unfoldLines(source) {
	const line = /(.*?)\r?\n/sy;
	let match = line.exec(source);
	if (!match) return source;
	/**
	* The negative lookbehinds in these RegExps are to
	* prevent causing a polynomial search time in certain cases.
	*
	* The try-catch is for Safari < 16.4 and other old browsers:
	* https://caniuse.com/js-regexp-lookbehind
	*/
	let trimEnd, trimBoth;
	try {
		trimEnd = /* @__PURE__ */ new RegExp("(?<![ 	])[ 	]+$");
		trimBoth = /* @__PURE__ */ new RegExp("^[ 	]+|(?<![ 	])[ 	]+$", "g");
	} catch {
		trimEnd = /[ \t]+$/;
		trimBoth = /^[ \t]+|[ \t]+$/g;
	}
	let res = match[1].replace(trimEnd, "");
	let sep = " ";
	let pos = line.lastIndex;
	while (match = line.exec(source)) {
		const lm = match[1].replace(trimBoth, "");
		if (lm === "") {
			if (sep === "\n") res += sep;
			else sep = "\n";
		} else {
			res += sep + lm;
			sep = " ";
		}
		pos = line.lastIndex;
	}
	const last = /[ \t]*(.*)/sy;
	last.lastIndex = pos;
	match = last.exec(source);
	return res + sep + (match?.[1] ?? "");
}
function doubleQuotedValue(source, onError) {
	let res = "";
	for (let i = 1; i < source.length - 1; ++i) {
		const ch = source[i];
		if (ch === "\r" && source[i + 1] === "\n") continue;
		if (ch === "\n") {
			const { fold, offset } = foldNewline(source, i);
			res += fold;
			i = offset;
		} else if (ch === "\\") {
			let next = source[++i];
			const cc = escapeCodes[next];
			if (cc) res += cc;
			else if (next === "\n") {
				next = source[i + 1];
				while (next === " " || next === "	") next = source[++i + 1];
			} else if (next === "\r" && source[i + 1] === "\n") {
				next = source[++i + 1];
				while (next === " " || next === "	") next = source[++i + 1];
			} else if (next === "x" || next === "u" || next === "U") {
				const length = next === "x" ? 2 : next === "u" ? 4 : 8;
				res += parseCharCode(source, i + 1, length, onError);
				i += length;
			} else {
				const raw = source.substr(i - 1, 2);
				onError(i - 1, "BAD_DQ_ESCAPE", `Invalid escape sequence ${raw}`);
				res += raw;
			}
		} else if (ch === " " || ch === "	") {
			const wsStart = i;
			let next = source[i + 1];
			while (next === " " || next === "	") next = source[++i + 1];
			if (next !== "\n" && !(next === "\r" && source[i + 2] === "\n")) res += i > wsStart ? source.slice(wsStart, i + 1) : ch;
		} else res += ch;
	}
	if (source[source.length - 1] !== "\"" || source.length === 1) onError(source.length, "MISSING_CHAR", "Missing closing \"quote");
	return res;
}
/**
* Fold a single newline into a space, multiple newlines to N - 1 newlines.
* Presumes `source[offset] === '\n'`
*/
function foldNewline(source, offset) {
	let fold = "";
	let ch = source[offset + 1];
	while (ch === " " || ch === "	" || ch === "\n" || ch === "\r") {
		if (ch === "\r" && source[offset + 2] !== "\n") break;
		if (ch === "\n") fold += "\n";
		offset += 1;
		ch = source[offset + 1];
	}
	if (!fold) fold = " ";
	return {
		fold,
		offset
	};
}
var escapeCodes = {
	"0": "\0",
	a: "\x07",
	b: "\b",
	e: "\x1B",
	f: "\f",
	n: "\n",
	r: "\r",
	t: "	",
	v: "\v",
	N: "",
	_: "\xA0",
	L: "\u2028",
	P: "\u2029",
	" ": " ",
	"\"": "\"",
	"/": "/",
	"\\": "\\",
	"	": "	"
};
function parseCharCode(source, offset, length, onError) {
	const cc = source.substr(offset, length);
	const code = cc.length === length && /^[0-9a-fA-F]+$/.test(cc) ? parseInt(cc, 16) : NaN;
	try {
		return String.fromCodePoint(code);
	} catch {
		const raw = source.substr(offset - 2, length + 2);
		onError(offset - 2, "BAD_DQ_ESCAPE", `Invalid escape sequence ${raw}`);
		return raw;
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/compose-scalar.js
function composeScalar(ctx, token, tagToken, onError) {
	const { value, type, comment, range } = token.type === "block-scalar" ? resolveBlockScalar(ctx, token, onError) : resolveFlowScalar(token, ctx.options.strict, onError);
	const tagName = tagToken ? ctx.directives.tagName(tagToken.source, (msg) => onError(tagToken, "TAG_RESOLVE_FAILED", msg)) : null;
	let tag;
	if (ctx.options.stringKeys && ctx.atKey) tag = ctx.schema[SCALAR$1];
	else if (tagName) tag = findScalarTagByName(ctx.schema, value, tagName, tagToken, onError);
	else if (token.type === "scalar") tag = findScalarTagByTest(ctx, value, token, onError);
	else tag = ctx.schema[SCALAR$1];
	let scalar;
	try {
		const res = tag.resolve(value, (msg) => onError(tagToken ?? token, "TAG_RESOLVE_FAILED", msg), ctx.options);
		scalar = isScalar$1(res) ? res : new Scalar(res);
	} catch (error) {
		const msg = error instanceof Error ? error.message : String(error);
		onError(tagToken ?? token, "TAG_RESOLVE_FAILED", msg);
		scalar = new Scalar(value);
	}
	scalar.range = range;
	scalar.source = value;
	if (type) scalar.type = type;
	if (tagName) scalar.tag = tagName;
	if (tag.format) scalar.format = tag.format;
	if (comment) scalar.comment = comment;
	return scalar;
}
function findScalarTagByName(schema, value, tagName, tagToken, onError) {
	if (tagName === "!") return schema[SCALAR$1];
	const matchWithTest = [];
	for (const tag of schema.tags) if (!tag.collection && tag.tag === tagName) {
		if (tag.default && tag.test) matchWithTest.push(tag);
		else return tag;
	}
	for (const tag of matchWithTest) if (tag.test?.test(value)) return tag;
	const kt = schema.knownTags[tagName];
	if (kt && !kt.collection) {
		schema.tags.push(Object.assign({}, kt, {
			default: false,
			test: void 0
		}));
		return kt;
	}
	onError(tagToken, "TAG_RESOLVE_FAILED", `Unresolved tag: ${tagName}`, tagName !== "tag:yaml.org,2002:str");
	return schema[SCALAR$1];
}
function findScalarTagByTest({ atKey, directives, schema }, value, token, onError) {
	const tag = schema.tags.find((tag) => (tag.default === true || atKey && tag.default === "key") && tag.test?.test(value)) || schema[SCALAR$1];
	if (schema.compat) {
		const compat = schema.compat.find((tag) => tag.default && tag.test?.test(value)) ?? schema[SCALAR$1];
		if (tag.tag !== compat.tag) onError(token, "TAG_RESOLVE_FAILED", `Value may be parsed as either ${directives.tagString(tag.tag)} or ${directives.tagString(compat.tag)}`, true);
	}
	return tag;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/util-empty-scalar-position.js
function emptyScalarPosition(offset, before, pos) {
	if (before) {
		pos ?? (pos = before.length);
		for (let i = pos - 1; i >= 0; --i) {
			let st = before[i];
			switch (st.type) {
				case "space":
				case "comment":
				case "newline":
					offset -= st.source.length;
					continue;
			}
			st = before[++i];
			while (st?.type === "space") {
				offset += st.source.length;
				st = before[++i];
			}
			break;
		}
	}
	return offset;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/compose-node.js
var CN = {
	composeNode,
	composeEmptyNode
};
function composeNode(ctx, token, props, onError) {
	const atKey = ctx.atKey;
	const { spaceBefore, comment, anchor, tag } = props;
	let node;
	let isSrcToken = true;
	switch (token.type) {
		case "alias":
			node = composeAlias(ctx, token, onError);
			if (anchor || tag) onError(token, "ALIAS_PROPS", "An alias node must not specify any properties");
			break;
		case "scalar":
		case "single-quoted-scalar":
		case "double-quoted-scalar":
		case "block-scalar":
			node = composeScalar(ctx, token, tag, onError);
			if (anchor) node.anchor = anchor.source.substring(1);
			break;
		case "block-map":
		case "block-seq":
		case "flow-collection":
			try {
				node = composeCollection(CN, ctx, token, props, onError);
				if (anchor) node.anchor = anchor.source.substring(1);
			} catch (error) {
				onError(token, "RESOURCE_EXHAUSTION", error instanceof Error ? error.message : String(error));
			}
			break;
		default:
			onError(token, "UNEXPECTED_TOKEN", token.type === "error" ? token.message : `Unsupported token (type: ${token.type})`);
			isSrcToken = false;
	}
	node ?? (node = composeEmptyNode(ctx, token.offset, void 0, null, props, onError));
	if (anchor && node.anchor === "") onError(anchor, "BAD_ALIAS", "Anchor cannot be an empty string");
	if (atKey && ctx.options.stringKeys && (!isScalar$1(node) || typeof node.value !== "string" || node.tag && node.tag !== "tag:yaml.org,2002:str")) onError(tag ?? token, "NON_STRING_KEY", "With stringKeys, all keys must be strings");
	if (spaceBefore) node.spaceBefore = true;
	if (comment) {
		if (token.type === "scalar" && token.source === "") node.comment = comment;
		else node.commentBefore = comment;
	}
	if (ctx.options.keepSourceTokens && isSrcToken) node.srcToken = token;
	return node;
}
function composeEmptyNode(ctx, offset, before, pos, { spaceBefore, comment, anchor, tag, end }, onError) {
	const node = composeScalar(ctx, {
		type: "scalar",
		offset: emptyScalarPosition(offset, before, pos),
		indent: -1,
		source: ""
	}, tag, onError);
	if (anchor) {
		node.anchor = anchor.source.substring(1);
		if (node.anchor === "") onError(anchor, "BAD_ALIAS", "Anchor cannot be an empty string");
	}
	if (spaceBefore) node.spaceBefore = true;
	if (comment) {
		node.comment = comment;
		node.range[2] = end;
	}
	return node;
}
function composeAlias({ options }, { offset, source, end }, onError) {
	const alias = new Alias(source.substring(1));
	if (alias.source === "") onError(offset, "BAD_ALIAS", "Alias cannot be an empty string");
	if (alias.source.endsWith(":")) onError(offset + source.length - 1, "BAD_ALIAS", "Alias ending in : is ambiguous", true);
	const valueEnd = offset + source.length;
	const re = resolveEnd(end, valueEnd, options.strict, onError);
	alias.range = [
		offset,
		valueEnd,
		re.offset
	];
	if (re.comment) alias.comment = re.comment;
	return alias;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/compose-doc.js
function composeDoc(options, directives, { offset, start, value, end }, onError) {
	const doc = new Document$1(void 0, Object.assign({ _directives: directives }, options));
	const ctx = {
		atKey: false,
		atRoot: true,
		directives: doc.directives,
		options: doc.options,
		schema: doc.schema
	};
	const props = resolveProps(start, {
		indicator: "doc-start",
		next: value ?? end?.[0],
		offset,
		onError,
		parentIndent: 0,
		startOnNewline: true
	});
	if (props.found) {
		doc.directives.docStart = true;
		if (value && (value.type === "block-map" || value.type === "block-seq") && !props.hasNewline) onError(props.end, "MISSING_CHAR", "Block collection cannot start on same line with directives-end marker");
	}
	doc.contents = value ? composeNode(ctx, value, props, onError) : composeEmptyNode(ctx, props.end, start, null, props, onError);
	const contentEnd = doc.contents.range[2];
	const re = resolveEnd(end, contentEnd, false, onError);
	if (re.comment) doc.comment = re.comment;
	doc.range = [
		offset,
		contentEnd,
		re.offset
	];
	return doc;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/compose/composer.js
function getErrorPos(src) {
	if (typeof src === "number") return [src, src + 1];
	if (Array.isArray(src)) return src.length === 2 ? src : [src[0], src[1]];
	const { offset, source } = src;
	return [offset, offset + (typeof source === "string" ? source.length : 1)];
}
function parsePrelude(prelude) {
	let comment = "";
	let atComment = false;
	let afterEmptyLine = false;
	for (let i = 0; i < prelude.length; ++i) {
		const source = prelude[i];
		switch (source[0]) {
			case "#":
				comment += (comment === "" ? "" : afterEmptyLine ? "\n\n" : "\n") + (source.substring(1) || " ");
				atComment = true;
				afterEmptyLine = false;
				break;
			case "%":
				if (prelude[i + 1]?.[0] !== "#") i += 1;
				atComment = false;
				break;
			default:
				if (!atComment) afterEmptyLine = true;
				atComment = false;
		}
	}
	return {
		comment,
		afterEmptyLine
	};
}
/**
* Compose a stream of CST nodes into a stream of YAML Documents.
*
* ```ts
* import { Composer, Parser } from 'yaml'
*
* const src: string = ...
* const tokens = new Parser().parse(src)
* const docs = new Composer().compose(tokens)
* ```
*/
var Composer = class {
	constructor(options = {}) {
		this.doc = null;
		this.atDirectives = false;
		this.prelude = [];
		this.errors = [];
		this.warnings = [];
		this.onError = (source, code, message, warning) => {
			const pos = getErrorPos(source);
			if (warning) this.warnings.push(new YAMLWarning(pos, code, message));
			else this.errors.push(new YAMLParseError(pos, code, message));
		};
		this.directives = new Directives({ version: options.version || "1.2" });
		this.options = options;
	}
	decorate(doc, afterDoc) {
		const { comment, afterEmptyLine } = parsePrelude(this.prelude);
		if (comment) {
			const dc = doc.contents;
			if (afterDoc) doc.comment = doc.comment ? `${doc.comment}\n${comment}` : comment;
			else if (afterEmptyLine || doc.directives.docStart || !dc) doc.commentBefore = comment;
			else if (isCollection$1(dc) && !dc.flow && dc.items.length > 0) {
				let it = dc.items[0];
				if (isPair(it)) it = it.key;
				const cb = it.commentBefore;
				it.commentBefore = cb ? `${comment}\n${cb}` : comment;
			} else {
				const cb = dc.commentBefore;
				dc.commentBefore = cb ? `${comment}\n${cb}` : comment;
			}
		}
		if (afterDoc) {
			for (let i = 0; i < this.errors.length; ++i) doc.errors.push(this.errors[i]);
			for (let i = 0; i < this.warnings.length; ++i) doc.warnings.push(this.warnings[i]);
		} else {
			doc.errors = this.errors;
			doc.warnings = this.warnings;
		}
		this.prelude = [];
		this.errors = [];
		this.warnings = [];
	}
	/**
	* Current stream status information.
	*
	* Mostly useful at the end of input for an empty stream.
	*/
	streamInfo() {
		return {
			comment: parsePrelude(this.prelude).comment,
			directives: this.directives,
			errors: this.errors,
			warnings: this.warnings
		};
	}
	/**
	* Compose tokens into documents.
	*
	* @param forceDoc - If the stream contains no document, still emit a final document including any comments and directives that would be applied to a subsequent document.
	* @param endOffset - Should be set if `forceDoc` is also set, to set the document range end and to indicate errors correctly.
	*/
	*compose(tokens, forceDoc = false, endOffset = -1) {
		for (const token of tokens) yield* this.next(token);
		yield* this.end(forceDoc, endOffset);
	}
	/** Advance the composer by one CST token. */
	*next(token) {
		switch (token.type) {
			case "directive":
				this.directives.add(token.source, (offset, message, warning) => {
					const pos = getErrorPos(token);
					pos[0] += offset;
					this.onError(pos, "BAD_DIRECTIVE", message, warning);
				});
				this.prelude.push(token.source);
				this.atDirectives = true;
				break;
			case "document": {
				const doc = composeDoc(this.options, this.directives, token, this.onError);
				if (this.atDirectives && !doc.directives.docStart) this.onError(token, "MISSING_CHAR", "Missing directives-end/doc-start indicator line");
				this.decorate(doc, false);
				if (this.doc) yield this.doc;
				this.doc = doc;
				this.atDirectives = false;
				break;
			}
			case "byte-order-mark":
			case "space": break;
			case "comment":
			case "newline":
				this.prelude.push(token.source);
				break;
			case "error": {
				const msg = token.source ? `${token.message}: ${JSON.stringify(token.source)}` : token.message;
				const error = new YAMLParseError(getErrorPos(token), "UNEXPECTED_TOKEN", msg);
				if (this.atDirectives || !this.doc) this.errors.push(error);
				else this.doc.errors.push(error);
				break;
			}
			case "doc-end": {
				if (!this.doc) {
					this.errors.push(new YAMLParseError(getErrorPos(token), "UNEXPECTED_TOKEN", "Unexpected doc-end without preceding document"));
					break;
				}
				this.doc.directives.docEnd = true;
				const end = resolveEnd(token.end, token.offset + token.source.length, this.doc.options.strict, this.onError);
				this.decorate(this.doc, true);
				if (end.comment) {
					const dc = this.doc.comment;
					this.doc.comment = dc ? `${dc}\n${end.comment}` : end.comment;
				}
				this.doc.range[2] = end.offset;
				break;
			}
			default: this.errors.push(new YAMLParseError(getErrorPos(token), "UNEXPECTED_TOKEN", `Unsupported token ${token.type}`));
		}
	}
	/**
	* Call at end of input to yield any remaining document.
	*
	* @param forceDoc - If the stream contains no document, still emit a final document including any comments and directives that would be applied to a subsequent document.
	* @param endOffset - Should be set if `forceDoc` is also set, to set the document range end and to indicate errors correctly.
	*/
	*end(forceDoc = false, endOffset = -1) {
		if (this.doc) {
			this.decorate(this.doc, true);
			yield this.doc;
			this.doc = null;
		} else if (forceDoc) {
			const doc = new Document$1(void 0, Object.assign({ _directives: this.directives }, this.options));
			if (this.atDirectives) this.onError(endOffset, "MISSING_CHAR", "Missing directives-end indicator line");
			doc.range = [
				0,
				endOffset,
				endOffset
			];
			this.decorate(doc, false);
			yield doc;
		}
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/cst-scalar.js
function resolveAsScalar(token, strict = true, onError) {
	if (token) {
		const _onError = (pos, code, message) => {
			const offset = typeof pos === "number" ? pos : Array.isArray(pos) ? pos[0] : pos.offset;
			if (onError) onError(offset, code, message);
			else throw new YAMLParseError([offset, offset + 1], code, message);
		};
		switch (token.type) {
			case "scalar":
			case "single-quoted-scalar":
			case "double-quoted-scalar": return resolveFlowScalar(token, strict, _onError);
			case "block-scalar": return resolveBlockScalar({ options: { strict } }, token, _onError);
		}
	}
	return null;
}
/**
* Create a new scalar token with `value`
*
* Values that represent an actual string but may be parsed as a different type should use a `type` other than `'PLAIN'`,
* as this function does not support any schema operations and won't check for such conflicts.
*
* @param value The string representation of the value, which will have its content properly indented.
* @param context.end Comments and whitespace after the end of the value, or after the block scalar header. If undefined, a newline will be added.
* @param context.implicitKey Being within an implicit key may affect the resolved type of the token's value.
* @param context.indent The indent level of the token.
* @param context.inFlow Is this scalar within a flow collection? This may affect the resolved type of the token's value.
* @param context.offset The offset position of the token.
* @param context.type The preferred type of the scalar token. If undefined, the previous type of the `token` will be used, defaulting to `'PLAIN'`.
*/
function createScalarToken(value, context) {
	const { implicitKey = false, indent, inFlow = false, offset = -1, type = "PLAIN" } = context;
	const source = stringifyString({
		type,
		value
	}, {
		implicitKey,
		indent: indent > 0 ? " ".repeat(indent) : "",
		inFlow,
		options: {
			blockQuote: true,
			lineWidth: -1
		}
	});
	const end = context.end ?? [{
		type: "newline",
		offset: -1,
		indent,
		source: "\n"
	}];
	switch (source[0]) {
		case "|":
		case ">": {
			const he = source.indexOf("\n");
			const head = source.substring(0, he);
			const body = source.substring(he + 1) + "\n";
			const props = [{
				type: "block-scalar-header",
				offset,
				indent,
				source: head
			}];
			if (!addEndtoBlockProps(props, end)) props.push({
				type: "newline",
				offset: -1,
				indent,
				source: "\n"
			});
			return {
				type: "block-scalar",
				offset,
				indent,
				props,
				source: body
			};
		}
		case "\"": return {
			type: "double-quoted-scalar",
			offset,
			indent,
			source,
			end
		};
		case "'": return {
			type: "single-quoted-scalar",
			offset,
			indent,
			source,
			end
		};
		default: return {
			type: "scalar",
			offset,
			indent,
			source,
			end
		};
	}
}
/**
* Set the value of `token` to the given string `value`, overwriting any previous contents and type that it may have.
*
* Best efforts are made to retain any comments previously associated with the `token`,
* though all contents within a collection's `items` will be overwritten.
*
* Values that represent an actual string but may be parsed as a different type should use a `type` other than `'PLAIN'`,
* as this function does not support any schema operations and won't check for such conflicts.
*
* @param token Any token. If it does not include an `indent` value, the value will be stringified as if it were an implicit key.
* @param value The string representation of the value, which will have its content properly indented.
* @param context.afterKey In most cases, values after a key should have an additional level of indentation.
* @param context.implicitKey Being within an implicit key may affect the resolved type of the token's value.
* @param context.inFlow Being within a flow collection may affect the resolved type of the token's value.
* @param context.type The preferred type of the scalar token. If undefined, the previous type of the `token` will be used, defaulting to `'PLAIN'`.
*/
function setScalarValue(token, value, context = {}) {
	let { afterKey = false, implicitKey = false, inFlow = false, type } = context;
	let indent = "indent" in token ? token.indent : null;
	if (afterKey && typeof indent === "number") indent += 2;
	if (!type) switch (token.type) {
		case "single-quoted-scalar":
			type = "QUOTE_SINGLE";
			break;
		case "double-quoted-scalar":
			type = "QUOTE_DOUBLE";
			break;
		case "block-scalar": {
			const header = token.props[0];
			if (header.type !== "block-scalar-header") throw new Error("Invalid block scalar header");
			type = header.source[0] === ">" ? "BLOCK_FOLDED" : "BLOCK_LITERAL";
			break;
		}
		default: type = "PLAIN";
	}
	const source = stringifyString({
		type,
		value
	}, {
		implicitKey: implicitKey || indent === null,
		indent: indent !== null && indent > 0 ? " ".repeat(indent) : "",
		inFlow,
		options: {
			blockQuote: true,
			lineWidth: -1
		}
	});
	switch (source[0]) {
		case "|":
		case ">":
			setBlockScalarValue(token, source);
			break;
		case "\"":
			setFlowScalarValue(token, source, "double-quoted-scalar");
			break;
		case "'":
			setFlowScalarValue(token, source, "single-quoted-scalar");
			break;
		default: setFlowScalarValue(token, source, "scalar");
	}
}
function setBlockScalarValue(token, source) {
	const he = source.indexOf("\n");
	const head = source.substring(0, he);
	const body = source.substring(he + 1) + "\n";
	if (token.type === "block-scalar") {
		const header = token.props[0];
		if (header.type !== "block-scalar-header") throw new Error("Invalid block scalar header");
		header.source = head;
		token.source = body;
	} else {
		const { offset } = token;
		const indent = "indent" in token ? token.indent : -1;
		const props = [{
			type: "block-scalar-header",
			offset,
			indent,
			source: head
		}];
		if (!addEndtoBlockProps(props, "end" in token ? token.end : void 0)) props.push({
			type: "newline",
			offset: -1,
			indent,
			source: "\n"
		});
		for (const key of Object.keys(token)) if (key !== "type" && key !== "offset") delete token[key];
		Object.assign(token, {
			type: "block-scalar",
			indent,
			props,
			source: body
		});
	}
}
/** @returns `true` if last token is a newline */
function addEndtoBlockProps(props, end) {
	if (end) for (const st of end) switch (st.type) {
		case "space":
		case "comment":
			props.push(st);
			break;
		case "newline":
			props.push(st);
			return true;
	}
	return false;
}
function setFlowScalarValue(token, source, type) {
	switch (token.type) {
		case "scalar":
		case "double-quoted-scalar":
		case "single-quoted-scalar":
			token.type = type;
			token.source = source;
			break;
		case "block-scalar": {
			const end = token.props.slice(1);
			let oa = source.length;
			if (token.props[0].type === "block-scalar-header") oa -= token.props[0].source.length;
			for (const tok of end) tok.offset += oa;
			delete token.props;
			Object.assign(token, {
				type,
				source,
				end
			});
			break;
		}
		case "block-map":
		case "block-seq": {
			const nl = {
				type: "newline",
				offset: token.offset + source.length,
				indent: token.indent,
				source: "\n"
			};
			delete token.items;
			Object.assign(token, {
				type,
				source,
				end: [nl]
			});
			break;
		}
		default: {
			const indent = "indent" in token ? token.indent : -1;
			const end = "end" in token && Array.isArray(token.end) ? token.end.filter((st) => st.type === "space" || st.type === "comment" || st.type === "newline") : [];
			for (const key of Object.keys(token)) if (key !== "type" && key !== "offset") delete token[key];
			Object.assign(token, {
				type,
				indent,
				source,
				end
			});
		}
	}
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/cst-stringify.js
/**
* Stringify a CST document, token, or collection item
*
* Fair warning: This applies no validation whatsoever, and
* simply concatenates the sources in their logical order.
*/
var stringify$1 = (cst) => "type" in cst ? stringifyToken(cst) : stringifyItem(cst);
function stringifyToken(token) {
	switch (token.type) {
		case "block-scalar": {
			let res = "";
			for (const tok of token.props) res += stringifyToken(tok);
			return res + token.source;
		}
		case "block-map":
		case "block-seq": {
			let res = "";
			for (const item of token.items) res += stringifyItem(item);
			return res;
		}
		case "flow-collection": {
			let res = token.start.source;
			for (const item of token.items) res += stringifyItem(item);
			for (const st of token.end) res += st.source;
			return res;
		}
		case "document": {
			let res = stringifyItem(token);
			if (token.end) for (const st of token.end) res += st.source;
			return res;
		}
		default: {
			let res = token.source;
			if ("end" in token && token.end) for (const st of token.end) res += st.source;
			return res;
		}
	}
}
function stringifyItem({ start, key, sep, value }) {
	let res = "";
	for (const st of start) res += st.source;
	if (key) res += stringifyToken(key);
	if (sep) for (const st of sep) res += st.source;
	if (value) res += stringifyToken(value);
	return res;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/cst-visit.js
var BREAK = Symbol("break visit");
var SKIP = Symbol("skip children");
var REMOVE = Symbol("remove item");
/**
* Apply a visitor to a CST document or item.
*
* Walks through the tree (depth-first) starting from the root, calling a
* `visitor` function with two arguments when entering each item:
*   - `item`: The current item, which included the following members:
*     - `start: SourceToken[]` – Source tokens before the key or value,
*       possibly including its anchor or tag.
*     - `key?: Token | null` – Set for pair values. May then be `null`, if
*       the key before the `:` separator is empty.
*     - `sep?: SourceToken[]` – Source tokens between the key and the value,
*       which should include the `:` map value indicator if `value` is set.
*     - `value?: Token` – The value of a sequence item, or of a map pair.
*   - `path`: The steps from the root to the current node, as an array of
*     `['key' | 'value', number]` tuples.
*
* The return value of the visitor may be used to control the traversal:
*   - `undefined` (default): Do nothing and continue
*   - `visit.SKIP`: Do not visit the children of this token, continue with
*      next sibling
*   - `visit.BREAK`: Terminate traversal completely
*   - `visit.REMOVE`: Remove the current item, then continue with the next one
*   - `number`: Set the index of the next step. This is useful especially if
*     the index of the current token has changed.
*   - `function`: Define the next visitor for this item. After the original
*     visitor is called on item entry, next visitors are called after handling
*     a non-empty `key` and when exiting the item.
*/
function visit(cst, visitor) {
	if ("type" in cst && cst.type === "document") cst = {
		start: cst.start,
		value: cst.value
	};
	_visit(Object.freeze([]), cst, visitor);
}
/** Terminate visit traversal completely */
visit.BREAK = BREAK;
/** Do not visit the children of the current item */
visit.SKIP = SKIP;
/** Remove the current item */
visit.REMOVE = REMOVE;
/** Find the item at `path` from `cst` as the root */
visit.itemAtPath = (cst, path) => {
	let item = cst;
	for (const [field, index] of path) {
		const tok = item?.[field];
		if (tok && "items" in tok) item = tok.items[index];
		else return void 0;
	}
	return item;
};
/**
* Get the immediate parent collection of the item at `path` from `cst` as the root.
*
* Throws an error if the collection is not found, which should never happen if the item itself exists.
*/
visit.parentCollection = (cst, path) => {
	const parent = visit.itemAtPath(cst, path.slice(0, -1));
	const field = path[path.length - 1][0];
	const coll = parent?.[field];
	if (coll && "items" in coll) return coll;
	throw new Error("Parent collection not found");
};
function _visit(path, item, visitor) {
	let ctrl = visitor(item, path);
	if (typeof ctrl === "symbol") return ctrl;
	for (const field of ["key", "value"]) {
		const token = item[field];
		if (token && "items" in token) {
			for (let i = 0; i < token.items.length; ++i) {
				const ci = _visit(Object.freeze(path.concat([[field, i]])), token.items[i], visitor);
				if (typeof ci === "number") i = ci - 1;
				else if (ci === BREAK) return BREAK;
				else if (ci === REMOVE) {
					token.items.splice(i, 1);
					i -= 1;
				}
			}
			if (typeof ctrl === "function" && field === "key") ctrl = ctrl(item, path);
		}
	}
	return typeof ctrl === "function" ? ctrl(item, path) : ctrl;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/cst.js
var cst_exports = /* @__PURE__ */ __exportAll({
	BOM: () => "﻿",
	DOCUMENT: () => "",
	FLOW_END: () => "",
	SCALAR: () => "",
	createScalarToken: () => createScalarToken,
	isCollection: () => isCollection,
	isScalar: () => isScalar,
	prettyToken: () => prettyToken,
	resolveAsScalar: () => resolveAsScalar,
	setScalarValue: () => setScalarValue,
	stringify: () => stringify$1,
	tokenType: () => tokenType,
	visit: () => visit
});
/** @returns `true` if `token` is a flow or block collection */
var isCollection = (token) => !!token && "items" in token;
/** @returns `true` if `token` is a flow or block scalar; not an alias */
var isScalar = (token) => !!token && (token.type === "scalar" || token.type === "single-quoted-scalar" || token.type === "double-quoted-scalar" || token.type === "block-scalar");
/* istanbul ignore next */
/** Get a printable representation of a lexer token */
function prettyToken(token) {
	switch (token) {
		case "﻿": return "<BOM>";
		case "": return "<DOC>";
		case "": return "<FLOW_END>";
		case "": return "<SCALAR>";
		default: return JSON.stringify(token);
	}
}
/** Identify the type of a lexer token. May return `null` for unknown tokens. */
function tokenType(source) {
	switch (source) {
		case "﻿": return "byte-order-mark";
		case "": return "doc-mode";
		case "": return "flow-error-end";
		case "": return "scalar";
		case "---": return "doc-start";
		case "...": return "doc-end";
		case "":
		case "\n":
		case "\r\n": return "newline";
		case "-": return "seq-item-ind";
		case "?": return "explicit-key-ind";
		case ":": return "map-value-ind";
		case "{": return "flow-map-start";
		case "}": return "flow-map-end";
		case "[": return "flow-seq-start";
		case "]": return "flow-seq-end";
		case ",": return "comma";
	}
	switch (source[0]) {
		case " ":
		case "	": return "space";
		case "#": return "comment";
		case "%": return "directive-line";
		case "*": return "alias";
		case "&": return "anchor";
		case "!": return "tag";
		case "'": return "single-quoted-scalar";
		case "\"": return "double-quoted-scalar";
		case "|":
		case ">": return "block-scalar-header";
	}
	return null;
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/lexer.js
function isEmpty(ch) {
	switch (ch) {
		case void 0:
		case " ":
		case "\n":
		case "\r":
		case "	": return true;
		default: return false;
	}
}
var hexDigits = /* @__PURE__ */ new Set("0123456789ABCDEFabcdef");
var tagChars = /* @__PURE__ */ new Set("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-#;/?:@&=+$_.!~*'()");
var flowIndicatorChars = /* @__PURE__ */ new Set(",[]{}");
var invalidAnchorChars = /* @__PURE__ */ new Set(" ,[]{}\n\r	");
var isNotAnchorChar = (ch) => !ch || invalidAnchorChars.has(ch);
/**
* Splits an input string into lexical tokens, i.e. smaller strings that are
* easily identifiable by `tokens.tokenType()`.
*
* Lexing starts always in a "stream" context. Incomplete input may be buffered
* until a complete token can be emitted.
*
* In addition to slices of the original input, the following control characters
* may also be emitted:
*
* - `\x02` (Start of Text): A document starts with the next token
* - `\x18` (Cancel): Unexpected end of flow-mode (indicates an error)
* - `\x1f` (Unit Separator): Next token is a scalar value
* - `\u{FEFF}` (Byte order mark): Emitted separately outside documents
*/
var Lexer = class {
	constructor() {
		/**
		* Flag indicating whether the end of the current buffer marks the end of
		* all input
		*/
		this.atEnd = false;
		/**
		* Explicit indent set in block scalar header, as an offset from the current
		* minimum indent, so e.g. set to 1 from a header `|2+`. Set to -1 if not
		* explicitly set.
		*/
		this.blockScalarIndent = -1;
		/**
		* Block scalars that include a + (keep) chomping indicator in their header
		* include trailing empty lines, which are otherwise excluded from the
		* scalar's contents.
		*/
		this.blockScalarKeep = false;
		/** Current input */
		this.buffer = "";
		/**
		* Flag noting whether the map value indicator : can immediately follow this
		* node within a flow context.
		*/
		this.flowKey = false;
		/** Count of surrounding flow collection levels. */
		this.flowLevel = 0;
		/**
		* Minimum level of indentation required for next lines to be parsed as a
		* part of the current scalar value.
		*/
		this.indentNext = 0;
		/** Indentation level of the current line. */
		this.indentValue = 0;
		/** Position of the next \n character. */
		this.lineEndPos = null;
		/** Stores the state of the lexer if reaching the end of incpomplete input */
		this.next = null;
		/** A pointer to `buffer`; the current position of the lexer. */
		this.pos = 0;
	}
	/**
	* Generate YAML tokens from the `source` string. If `incomplete`,
	* a part of the last line may be left as a buffer for the next call.
	*
	* @returns A generator of lexical tokens
	*/
	*lex(source, incomplete = false) {
		if (source) {
			if (typeof source !== "string") throw TypeError("source is not a string");
			this.buffer = this.buffer ? this.buffer + source : source;
			this.lineEndPos = null;
		}
		this.atEnd = !incomplete;
		let next = this.next ?? "stream";
		while (next && (incomplete || this.hasChars(1))) next = yield* this.parseNext(next);
	}
	atLineEnd() {
		let i = this.pos;
		let ch = this.buffer[i];
		while (ch === " " || ch === "	") ch = this.buffer[++i];
		if (!ch || ch === "#" || ch === "\n") return true;
		if (ch === "\r") return this.buffer[i + 1] === "\n";
		return false;
	}
	charAt(n) {
		return this.buffer[this.pos + n];
	}
	continueScalar(offset) {
		let ch = this.buffer[offset];
		if (this.indentNext > 0) {
			let indent = 0;
			while (ch === " ") ch = this.buffer[++indent + offset];
			if (ch === "\r") {
				const next = this.buffer[indent + offset + 1];
				if (next === "\n" || !next && !this.atEnd) return offset + indent + 1;
			}
			return ch === "\n" || indent >= this.indentNext || !ch && !this.atEnd ? offset + indent : -1;
		}
		if (ch === "-" || ch === ".") {
			const dt = this.buffer.substr(offset, 3);
			if ((dt === "---" || dt === "...") && isEmpty(this.buffer[offset + 3])) return -1;
		}
		return offset;
	}
	getLine() {
		let end = this.lineEndPos;
		if (typeof end !== "number" || end !== -1 && end < this.pos) {
			end = this.buffer.indexOf("\n", this.pos);
			this.lineEndPos = end;
		}
		if (end === -1) return this.atEnd ? this.buffer.substring(this.pos) : null;
		if (this.buffer[end - 1] === "\r") end -= 1;
		return this.buffer.substring(this.pos, end);
	}
	hasChars(n) {
		return this.pos + n <= this.buffer.length;
	}
	setNext(state) {
		this.buffer = this.buffer.substring(this.pos);
		this.pos = 0;
		this.lineEndPos = null;
		this.next = state;
		return null;
	}
	peek(n) {
		return this.buffer.substr(this.pos, n);
	}
	*parseNext(next) {
		switch (next) {
			case "stream": return yield* this.parseStream();
			case "line-start": return yield* this.parseLineStart();
			case "block-start": return yield* this.parseBlockStart();
			case "doc": return yield* this.parseDocument();
			case "flow": return yield* this.parseFlowCollection();
			case "quoted-scalar": return yield* this.parseQuotedScalar();
			case "block-scalar": return yield* this.parseBlockScalar();
			case "plain-scalar": return yield* this.parsePlainScalar();
		}
	}
	*parseStream() {
		let line = this.getLine();
		if (line === null) return this.setNext("stream");
		if (line[0] === "﻿") {
			yield* this.pushCount(1);
			line = line.substring(1);
		}
		if (line[0] === "%") {
			let dirEnd = line.length;
			let cs = line.indexOf("#");
			while (cs !== -1) {
				const ch = line[cs - 1];
				if (ch === " " || ch === "	") {
					dirEnd = cs - 1;
					break;
				} else cs = line.indexOf("#", cs + 1);
			}
			while (true) {
				const ch = line[dirEnd - 1];
				if (ch === " " || ch === "	") dirEnd -= 1;
				else break;
			}
			const n = (yield* this.pushCount(dirEnd)) + (yield* this.pushSpaces(true));
			yield* this.pushCount(line.length - n);
			this.pushNewline();
			return "stream";
		}
		if (this.atLineEnd()) {
			const sp = yield* this.pushSpaces(true);
			yield* this.pushCount(line.length - sp);
			yield* this.pushNewline();
			return "stream";
		}
		yield "";
		return yield* this.parseLineStart();
	}
	*parseLineStart() {
		const ch = this.charAt(0);
		if (!ch && !this.atEnd) return this.setNext("line-start");
		if (ch === "-" || ch === ".") {
			if (!this.atEnd && !this.hasChars(4)) return this.setNext("line-start");
			const s = this.peek(3);
			if ((s === "---" || s === "...") && isEmpty(this.charAt(3))) {
				yield* this.pushCount(3);
				this.indentValue = 0;
				this.indentNext = 0;
				return s === "---" ? "doc" : "stream";
			}
		}
		this.indentValue = yield* this.pushSpaces(false);
		if (this.indentNext > this.indentValue && !isEmpty(this.charAt(1))) this.indentNext = this.indentValue;
		return yield* this.parseBlockStart();
	}
	*parseBlockStart() {
		const [ch0, ch1] = this.peek(2);
		if (!ch1 && !this.atEnd) return this.setNext("block-start");
		if ((ch0 === "-" || ch0 === "?" || ch0 === ":") && isEmpty(ch1)) {
			const n = (yield* this.pushCount(1)) + (yield* this.pushSpaces(true));
			this.indentNext = this.indentValue + 1;
			this.indentValue += n;
			return "block-start";
		}
		return "doc";
	}
	*parseDocument() {
		yield* this.pushSpaces(true);
		const line = this.getLine();
		if (line === null) return this.setNext("doc");
		let n = yield* this.pushIndicators();
		switch (line[n]) {
			case "#": yield* this.pushCount(line.length - n);
			case void 0:
				yield* this.pushNewline();
				return yield* this.parseLineStart();
			case "{":
			case "[":
				yield* this.pushCount(1);
				this.flowKey = false;
				this.flowLevel = 1;
				return "flow";
			case "}":
			case "]":
				yield* this.pushCount(1);
				return "doc";
			case "*":
				yield* this.pushUntil(isNotAnchorChar);
				return "doc";
			case "\"":
			case "'": return yield* this.parseQuotedScalar();
			case "|":
			case ">":
				n += yield* this.parseBlockScalarHeader();
				n += yield* this.pushSpaces(true);
				yield* this.pushCount(line.length - n);
				yield* this.pushNewline();
				return yield* this.parseBlockScalar();
			default: return yield* this.parsePlainScalar();
		}
	}
	*parseFlowCollection() {
		let nl, sp;
		let indent = -1;
		do {
			nl = yield* this.pushNewline();
			if (nl > 0) {
				sp = yield* this.pushSpaces(false);
				this.indentValue = indent = sp;
			} else sp = 0;
			sp += yield* this.pushSpaces(true);
		} while (nl + sp > 0);
		const line = this.getLine();
		if (line === null) return this.setNext("flow");
		if (indent !== -1 && indent < this.indentNext && line[0] !== "#" || indent === 0 && (line.startsWith("---") || line.startsWith("...")) && isEmpty(line[3])) {
			if (!(indent === this.indentNext - 1 && this.flowLevel === 1 && (line[0] === "]" || line[0] === "}"))) {
				this.flowLevel = 0;
				yield "";
				return yield* this.parseLineStart();
			}
		}
		let n = 0;
		while (line[n] === ",") {
			n += yield* this.pushCount(1);
			n += yield* this.pushSpaces(true);
			this.flowKey = false;
		}
		n += yield* this.pushIndicators();
		switch (line[n]) {
			case void 0: return "flow";
			case "#":
				yield* this.pushCount(line.length - n);
				return "flow";
			case "{":
			case "[":
				yield* this.pushCount(1);
				this.flowKey = false;
				this.flowLevel += 1;
				return "flow";
			case "}":
			case "]":
				yield* this.pushCount(1);
				this.flowKey = true;
				this.flowLevel -= 1;
				return this.flowLevel ? "flow" : "doc";
			case "*":
				yield* this.pushUntil(isNotAnchorChar);
				return "flow";
			case "\"":
			case "'":
				this.flowKey = true;
				return yield* this.parseQuotedScalar();
			case ":": {
				const next = this.charAt(1);
				if (this.flowKey || isEmpty(next) || next === ",") {
					this.flowKey = false;
					yield* this.pushCount(1);
					yield* this.pushSpaces(true);
					return "flow";
				}
			}
			default:
				this.flowKey = false;
				return yield* this.parsePlainScalar();
		}
	}
	*parseQuotedScalar() {
		const quote = this.charAt(0);
		let end = this.buffer.indexOf(quote, this.pos + 1);
		if (quote === "'") while (end !== -1 && this.buffer[end + 1] === "'") end = this.buffer.indexOf("'", end + 2);
		else while (end !== -1) {
			let n = 0;
			while (this.buffer[end - 1 - n] === "\\") n += 1;
			if (n % 2 === 0) break;
			end = this.buffer.indexOf("\"", end + 1);
		}
		const qb = this.buffer.substring(0, end);
		let nl = qb.indexOf("\n", this.pos);
		if (nl !== -1) {
			while (nl !== -1) {
				const cs = this.continueScalar(nl + 1);
				if (cs === -1) break;
				nl = qb.indexOf("\n", cs);
			}
			if (nl !== -1) end = nl - (qb[nl - 1] === "\r" ? 2 : 1);
		}
		if (end === -1) {
			if (!this.atEnd) return this.setNext("quoted-scalar");
			end = this.buffer.length;
		}
		yield* this.pushToIndex(end + 1, false);
		return this.flowLevel ? "flow" : "doc";
	}
	*parseBlockScalarHeader() {
		this.blockScalarIndent = -1;
		this.blockScalarKeep = false;
		let i = this.pos;
		while (true) {
			const ch = this.buffer[++i];
			if (ch === "+") this.blockScalarKeep = true;
			else if (ch > "0" && ch <= "9") this.blockScalarIndent = Number(ch) - 1;
			else if (ch !== "-") break;
		}
		return yield* this.pushUntil((ch) => isEmpty(ch) || ch === "#");
	}
	*parseBlockScalar() {
		let nl = this.pos - 1;
		let indent = 0;
		let ch;
		loop: for (let i = this.pos; ch = this.buffer[i]; ++i) switch (ch) {
			case " ":
				indent += 1;
				break;
			case "\n":
				nl = i;
				indent = 0;
				break;
			case "\r": {
				const next = this.buffer[i + 1];
				if (!next && !this.atEnd) return this.setNext("block-scalar");
				if (next === "\n") break;
			}
			default: break loop;
		}
		if (!ch && !this.atEnd) return this.setNext("block-scalar");
		if (indent >= this.indentNext) {
			if (this.blockScalarIndent === -1) this.indentNext = indent;
			else this.indentNext = this.blockScalarIndent + (this.indentNext === 0 ? 1 : this.indentNext);
			do {
				const cs = this.continueScalar(nl + 1);
				if (cs === -1) break;
				nl = this.buffer.indexOf("\n", cs);
			} while (nl !== -1);
			if (nl === -1) {
				if (!this.atEnd) return this.setNext("block-scalar");
				nl = this.buffer.length;
			}
		}
		let i = nl + 1;
		ch = this.buffer[i];
		while (ch === " ") ch = this.buffer[++i];
		if (ch === "	") {
			while (ch === "	" || ch === " " || ch === "\r" || ch === "\n") ch = this.buffer[++i];
			nl = i - 1;
		} else if (!this.blockScalarKeep) do {
			let i = nl - 1;
			let ch = this.buffer[i];
			if (ch === "\r") ch = this.buffer[--i];
			const lastChar = i;
			while (ch === " ") ch = this.buffer[--i];
			if (ch === "\n" && i >= this.pos && i + 1 + indent > lastChar) nl = i;
			else break;
		} while (true);
		yield "";
		yield* this.pushToIndex(nl + 1, true);
		return yield* this.parseLineStart();
	}
	*parsePlainScalar() {
		const inFlow = this.flowLevel > 0;
		let end = this.pos - 1;
		let i = this.pos - 1;
		let ch;
		while (ch = this.buffer[++i]) if (ch === ":") {
			const next = this.buffer[i + 1];
			if (isEmpty(next) || inFlow && flowIndicatorChars.has(next)) break;
			end = i;
		} else if (isEmpty(ch)) {
			let next = this.buffer[i + 1];
			if (ch === "\r") {
				if (next === "\n") {
					i += 1;
					ch = "\n";
					next = this.buffer[i + 1];
				} else end = i;
			}
			if (next === "#" || inFlow && flowIndicatorChars.has(next)) break;
			if (ch === "\n") {
				const cs = this.continueScalar(i + 1);
				if (cs === -1) break;
				i = Math.max(i, cs - 2);
			}
		} else {
			if (inFlow && flowIndicatorChars.has(ch)) break;
			end = i;
		}
		if (!ch && !this.atEnd) return this.setNext("plain-scalar");
		yield "";
		yield* this.pushToIndex(end + 1, true);
		return inFlow ? "flow" : "doc";
	}
	*pushCount(n) {
		if (n > 0) {
			yield this.buffer.substr(this.pos, n);
			this.pos += n;
			return n;
		}
		return 0;
	}
	*pushToIndex(i, allowEmpty) {
		const s = this.buffer.slice(this.pos, i);
		if (s) {
			yield s;
			this.pos += s.length;
			return s.length;
		} else if (allowEmpty) yield "";
		return 0;
	}
	*pushIndicators() {
		let n = 0;
		loop: while (true) {
			switch (this.charAt(0)) {
				case "!":
					n += yield* this.pushTag();
					n += yield* this.pushSpaces(true);
					continue loop;
				case "&":
					n += yield* this.pushUntil(isNotAnchorChar);
					n += yield* this.pushSpaces(true);
					continue loop;
				case "-":
				case "?":
				case ":": {
					const inFlow = this.flowLevel > 0;
					const ch1 = this.charAt(1);
					if (isEmpty(ch1) || inFlow && flowIndicatorChars.has(ch1)) {
						if (!inFlow) this.indentNext = this.indentValue + 1;
						else if (this.flowKey) this.flowKey = false;
						n += yield* this.pushCount(1);
						n += yield* this.pushSpaces(true);
						continue loop;
					}
				}
			}
			break loop;
		}
		return n;
	}
	*pushTag() {
		if (this.charAt(1) === "<") {
			let i = this.pos + 2;
			let ch = this.buffer[i];
			while (!isEmpty(ch) && ch !== ">") ch = this.buffer[++i];
			return yield* this.pushToIndex(ch === ">" ? i + 1 : i, false);
		} else {
			let i = this.pos + 1;
			let ch = this.buffer[i];
			while (ch) if (tagChars.has(ch)) ch = this.buffer[++i];
			else if (ch === "%" && hexDigits.has(this.buffer[i + 1]) && hexDigits.has(this.buffer[i + 2])) ch = this.buffer[i += 3];
			else break;
			return yield* this.pushToIndex(i, false);
		}
	}
	*pushNewline() {
		const ch = this.buffer[this.pos];
		if (ch === "\n") return yield* this.pushCount(1);
		else if (ch === "\r" && this.charAt(1) === "\n") return yield* this.pushCount(2);
		else return 0;
	}
	*pushSpaces(allowTabs) {
		let i = this.pos - 1;
		let ch;
		do
			ch = this.buffer[++i];
		while (ch === " " || allowTabs && ch === "	");
		const n = i - this.pos;
		if (n > 0) {
			yield this.buffer.substr(this.pos, n);
			this.pos = i;
		}
		return n;
	}
	*pushUntil(test) {
		let i = this.pos;
		let ch = this.buffer[i];
		while (!test(ch)) ch = this.buffer[++i];
		return yield* this.pushToIndex(i, false);
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/line-counter.js
/**
* Tracks newlines during parsing in order to provide an efficient API for
* determining the one-indexed `{ line, col }` position for any offset
* within the input.
*/
var LineCounter = class {
	constructor() {
		this.lineStarts = [];
		/**
		* Should be called in ascending order. Otherwise, call
		* `lineCounter.lineStarts.sort()` before calling `linePos()`.
		*/
		this.addNewLine = (offset) => this.lineStarts.push(offset);
		/**
		* Performs a binary search and returns the 1-indexed { line, col }
		* position of `offset`. If `line === 0`, `addNewLine` has never been
		* called or `offset` is before the first known newline.
		*/
		this.linePos = (offset) => {
			let low = 0;
			let high = this.lineStarts.length;
			while (low < high) {
				const mid = low + high >> 1;
				if (this.lineStarts[mid] < offset) low = mid + 1;
				else high = mid;
			}
			if (this.lineStarts[low] === offset) return {
				line: low + 1,
				col: 1
			};
			if (low === 0) return {
				line: 0,
				col: offset
			};
			const start = this.lineStarts[low - 1];
			return {
				line: low,
				col: offset - start + 1
			};
		};
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/parse/parser.js
function includesToken(list, type) {
	for (let i = 0; i < list.length; ++i) if (list[i].type === type) return true;
	return false;
}
function findNonEmptyIndex(list) {
	for (let i = 0; i < list.length; ++i) switch (list[i].type) {
		case "space":
		case "comment":
		case "newline": break;
		default: return i;
	}
	return -1;
}
function isFlowToken(token) {
	switch (token?.type) {
		case "alias":
		case "scalar":
		case "single-quoted-scalar":
		case "double-quoted-scalar":
		case "flow-collection": return true;
		default: return false;
	}
}
function getPrevProps(parent) {
	switch (parent.type) {
		case "document": return parent.start;
		case "block-map": {
			const it = parent.items[parent.items.length - 1];
			return it.sep ?? it.start;
		}
		case "block-seq": return parent.items[parent.items.length - 1].start;
		/* istanbul ignore next should not happen */
		default: return [];
	}
}
/** Note: May modify input array */
function getFirstKeyStartProps(prev) {
	if (prev.length === 0) return [];
	let i = prev.length;
	loop: while (--i >= 0) switch (prev[i].type) {
		case "doc-start":
		case "explicit-key-ind":
		case "map-value-ind":
		case "seq-item-ind":
		case "newline": break loop;
	}
	while (prev[++i]?.type === "space");
	return prev.splice(i, prev.length);
}
function arrayPushArray(target, source) {
	if (source.length < 1e5) Array.prototype.push.apply(target, source);
	else for (let i = 0; i < source.length; ++i) target.push(source[i]);
}
function fixFlowSeqItems(fc) {
	if (fc.start.type === "flow-seq-start") {
		for (const it of fc.items) if (it.sep && !it.value && !includesToken(it.start, "explicit-key-ind") && !includesToken(it.sep, "map-value-ind")) {
			if (it.key) it.value = it.key;
			delete it.key;
			if (isFlowToken(it.value)) {
				if (it.value.end) arrayPushArray(it.value.end, it.sep);
				else it.value.end = it.sep;
			} else arrayPushArray(it.start, it.sep);
			delete it.sep;
		}
	}
}
/**
* A YAML concrete syntax tree (CST) parser
*
* ```ts
* const src: string = ...
* for (const token of new Parser().parse(src)) {
*   // token: Token
* }
* ```
*
* To use the parser with a user-provided lexer:
*
* ```ts
* function* parse(source: string, lexer: Lexer) {
*   const parser = new Parser()
*   for (const lexeme of lexer.lex(source))
*     yield* parser.next(lexeme)
*   yield* parser.end()
* }
*
* const src: string = ...
* const lexer = new Lexer()
* for (const token of parse(src, lexer)) {
*   // token: Token
* }
* ```
*/
var Parser = class {
	/**
	* @param onNewLine - If defined, called separately with the start position of
	*   each new line (in `parse()`, including the start of input).
	*/
	constructor(onNewLine) {
		/** If true, space and sequence indicators count as indentation */
		this.atNewLine = true;
		/** If true, next token is a scalar value */
		this.atScalar = false;
		/** Current indentation level */
		this.indent = 0;
		/** Current offset since the start of parsing */
		this.offset = 0;
		/** On the same line with a block map key */
		this.onKeyLine = false;
		/** Top indicates the node that's currently being built */
		this.stack = [];
		/** The source of the current token, set in parse() */
		this.source = "";
		/** The type of the current token, set in parse() */
		this.type = "";
		this.lexer = new Lexer();
		this.onNewLine = onNewLine;
	}
	/**
	* Parse `source` as a YAML stream.
	* If `incomplete`, a part of the last line may be left as a buffer for the next call.
	*
	* Errors are not thrown, but yielded as `{ type: 'error', message }` tokens.
	*
	* @returns A generator of tokens representing each directive, document, and other structure.
	*/
	*parse(source, incomplete = false) {
		if (this.onNewLine && this.offset === 0) this.onNewLine(0);
		for (const lexeme of this.lexer.lex(source, incomplete)) yield* this.next(lexeme);
		if (!incomplete) yield* this.end();
	}
	/**
	* Advance the parser by the `source` of one lexical token.
	*/
	*next(source) {
		this.source = source;
		if (this.atScalar) {
			this.atScalar = false;
			yield* this.step();
			this.offset += source.length;
			return;
		}
		const type = tokenType(source);
		if (!type) {
			const message = `Not a YAML token: ${source}`;
			yield* this.pop({
				type: "error",
				offset: this.offset,
				message,
				source
			});
			this.offset += source.length;
		} else if (type === "scalar") {
			this.atNewLine = false;
			this.atScalar = true;
			this.type = "scalar";
		} else {
			this.type = type;
			yield* this.step();
			switch (type) {
				case "newline":
					this.atNewLine = true;
					this.indent = 0;
					if (this.onNewLine) this.onNewLine(this.offset + source.length);
					break;
				case "space":
					if (this.atNewLine && source[0] === " ") this.indent += source.length;
					break;
				case "explicit-key-ind":
				case "map-value-ind":
				case "seq-item-ind":
					if (this.atNewLine) this.indent += source.length;
					break;
				case "doc-mode":
				case "flow-error-end": return;
				default: this.atNewLine = false;
			}
			this.offset += source.length;
		}
	}
	/** Call at end of input to push out any remaining constructions */
	*end() {
		while (this.stack.length > 0) yield* this.pop();
	}
	get sourceToken() {
		return {
			type: this.type,
			offset: this.offset,
			indent: this.indent,
			source: this.source
		};
	}
	*step() {
		const top = this.peek(1);
		if (this.type === "doc-end" && top?.type !== "doc-end") {
			while (this.stack.length > 0) yield* this.pop();
			this.stack.push({
				type: "doc-end",
				offset: this.offset,
				source: this.source
			});
			return;
		}
		if (!top) return yield* this.stream();
		switch (top.type) {
			case "document": return yield* this.document(top);
			case "alias":
			case "scalar":
			case "single-quoted-scalar":
			case "double-quoted-scalar": return yield* this.scalar(top);
			case "block-scalar": return yield* this.blockScalar(top);
			case "block-map": return yield* this.blockMap(top);
			case "block-seq": return yield* this.blockSequence(top);
			case "flow-collection": return yield* this.flowCollection(top);
			case "doc-end": return yield* this.documentEnd(top);
		}
		/* istanbul ignore next should not happen */
		yield* this.pop();
	}
	peek(n) {
		return this.stack[this.stack.length - n];
	}
	*pop(error) {
		const token = error ?? this.stack.pop();
		/* istanbul ignore if should not happen */
		if (!token) yield {
			type: "error",
			offset: this.offset,
			source: "",
			message: "Tried to pop an empty stack"
		};
		else if (this.stack.length === 0) yield token;
		else {
			const top = this.peek(1);
			if (token.type === "block-scalar") token.indent = "indent" in top ? top.indent : 0;
			else if (token.type === "flow-collection" && top.type === "document") token.indent = 0;
			if (token.type === "flow-collection") fixFlowSeqItems(token);
			switch (top.type) {
				case "document":
					top.value = token;
					break;
				case "block-scalar":
					top.props.push(token);
					break;
				case "block-map": {
					const it = top.items[top.items.length - 1];
					if (it.value) {
						top.items.push({
							start: [],
							key: token,
							sep: []
						});
						this.onKeyLine = true;
						return;
					} else if (it.sep) it.value = token;
					else {
						Object.assign(it, {
							key: token,
							sep: []
						});
						this.onKeyLine = !it.explicitKey;
						return;
					}
					break;
				}
				case "block-seq": {
					const it = top.items[top.items.length - 1];
					if (it.value) top.items.push({
						start: [],
						value: token
					});
					else it.value = token;
					break;
				}
				case "flow-collection": {
					const it = top.items[top.items.length - 1];
					if (!it || it.value) top.items.push({
						start: [],
						key: token,
						sep: []
					});
					else if (it.sep) it.value = token;
					else Object.assign(it, {
						key: token,
						sep: []
					});
					return;
				}
				/* istanbul ignore next should not happen */
				default:
					yield* this.pop();
					yield* this.pop(token);
			}
			if ((top.type === "document" || top.type === "block-map" || top.type === "block-seq") && (token.type === "block-map" || token.type === "block-seq")) {
				const last = token.items[token.items.length - 1];
				if (last && !last.sep && !last.value && last.start.length > 0 && findNonEmptyIndex(last.start) === -1 && (token.indent === 0 || last.start.every((st) => st.type !== "comment" || st.indent < token.indent))) {
					if (top.type === "document") top.end = last.start;
					else top.items.push({ start: last.start });
					token.items.splice(-1, 1);
				}
			}
		}
	}
	*stream() {
		switch (this.type) {
			case "directive-line":
				yield {
					type: "directive",
					offset: this.offset,
					source: this.source
				};
				return;
			case "byte-order-mark":
			case "space":
			case "comment":
			case "newline":
				yield this.sourceToken;
				return;
			case "doc-mode":
			case "doc-start": {
				const doc = {
					type: "document",
					offset: this.offset,
					start: []
				};
				if (this.type === "doc-start") doc.start.push(this.sourceToken);
				this.stack.push(doc);
				return;
			}
		}
		yield {
			type: "error",
			offset: this.offset,
			message: `Unexpected ${this.type} token in YAML stream`,
			source: this.source
		};
	}
	*document(doc) {
		if (doc.value) return yield* this.lineEnd(doc);
		switch (this.type) {
			case "doc-start":
				if (findNonEmptyIndex(doc.start) !== -1) {
					yield* this.pop();
					yield* this.step();
				} else doc.start.push(this.sourceToken);
				return;
			case "anchor":
			case "tag":
			case "space":
			case "comment":
			case "newline":
				doc.start.push(this.sourceToken);
				return;
		}
		const bv = this.startBlockValue(doc);
		if (bv) this.stack.push(bv);
		else yield {
			type: "error",
			offset: this.offset,
			message: `Unexpected ${this.type} token in YAML document`,
			source: this.source
		};
	}
	*scalar(scalar) {
		if (this.type === "map-value-ind") {
			const start = getFirstKeyStartProps(getPrevProps(this.peek(2)));
			let sep;
			if (scalar.end) {
				sep = scalar.end;
				sep.push(this.sourceToken);
				delete scalar.end;
			} else sep = [this.sourceToken];
			const map = {
				type: "block-map",
				offset: scalar.offset,
				indent: scalar.indent,
				items: [{
					start,
					key: scalar,
					sep
				}]
			};
			this.onKeyLine = true;
			this.stack[this.stack.length - 1] = map;
		} else yield* this.lineEnd(scalar);
	}
	*blockScalar(scalar) {
		switch (this.type) {
			case "space":
			case "comment":
			case "newline":
				scalar.props.push(this.sourceToken);
				return;
			case "scalar":
				scalar.source = this.source;
				this.atNewLine = true;
				this.indent = 0;
				if (this.onNewLine) {
					let nl = this.source.indexOf("\n") + 1;
					while (nl !== 0) {
						this.onNewLine(this.offset + nl);
						nl = this.source.indexOf("\n", nl) + 1;
					}
				}
				yield* this.pop();
				break;
			/* istanbul ignore next should not happen */
			default:
				yield* this.pop();
				yield* this.step();
		}
	}
	*blockMap(map) {
		const it = map.items[map.items.length - 1];
		switch (this.type) {
			case "newline":
				this.onKeyLine = false;
				if (it.value) {
					const end = "end" in it.value ? it.value.end : void 0;
					if ((Array.isArray(end) ? end[end.length - 1] : void 0)?.type === "comment") end?.push(this.sourceToken);
					else map.items.push({ start: [this.sourceToken] });
				} else if (it.sep) it.sep.push(this.sourceToken);
				else it.start.push(this.sourceToken);
				return;
			case "space":
			case "comment":
				if (it.value) map.items.push({ start: [this.sourceToken] });
				else if (it.sep) it.sep.push(this.sourceToken);
				else {
					if (this.atIndentedComment(it.start, map.indent)) {
						const end = map.items[map.items.length - 2]?.value?.end;
						if (Array.isArray(end)) {
							arrayPushArray(end, it.start);
							end.push(this.sourceToken);
							map.items.pop();
							return;
						}
					}
					it.start.push(this.sourceToken);
				}
				return;
		}
		if (this.indent >= map.indent) {
			const atMapIndent = !this.onKeyLine && this.indent === map.indent;
			const atNextItem = atMapIndent && (it.sep || it.explicitKey) && this.type !== "seq-item-ind";
			let start = [];
			if (atNextItem && it.sep && !it.value) {
				const nl = [];
				for (let i = 0; i < it.sep.length; ++i) {
					const st = it.sep[i];
					switch (st.type) {
						case "newline":
							nl.push(i);
							break;
						case "space": break;
						case "comment":
							if (st.indent > map.indent) nl.length = 0;
							break;
						default: nl.length = 0;
					}
				}
				if (nl.length >= 2) start = it.sep.splice(nl[1]);
			}
			switch (this.type) {
				case "anchor":
				case "tag":
					if (atNextItem || it.value) {
						start.push(this.sourceToken);
						map.items.push({ start });
						this.onKeyLine = true;
					} else if (it.sep) it.sep.push(this.sourceToken);
					else it.start.push(this.sourceToken);
					return;
				case "explicit-key-ind":
					if (!it.sep && !it.explicitKey) {
						it.start.push(this.sourceToken);
						it.explicitKey = true;
					} else if (atNextItem || it.value) {
						start.push(this.sourceToken);
						map.items.push({
							start,
							explicitKey: true
						});
					} else this.stack.push({
						type: "block-map",
						offset: this.offset,
						indent: this.indent,
						items: [{
							start: [this.sourceToken],
							explicitKey: true
						}]
					});
					this.onKeyLine = true;
					return;
				case "map-value-ind":
					if (it.explicitKey) {
						if (!it.sep) {
							if (includesToken(it.start, "newline")) Object.assign(it, {
								key: null,
								sep: [this.sourceToken]
							});
							else {
								const start = getFirstKeyStartProps(it.start);
								this.stack.push({
									type: "block-map",
									offset: this.offset,
									indent: this.indent,
									items: [{
										start,
										key: null,
										sep: [this.sourceToken]
									}]
								});
							}
						} else if (it.value) map.items.push({
							start: [],
							key: null,
							sep: [this.sourceToken]
						});
						else if (includesToken(it.sep, "map-value-ind")) this.stack.push({
							type: "block-map",
							offset: this.offset,
							indent: this.indent,
							items: [{
								start,
								key: null,
								sep: [this.sourceToken]
							}]
						});
						else if (isFlowToken(it.key) && !includesToken(it.sep, "newline")) {
							const start = getFirstKeyStartProps(it.start);
							const key = it.key;
							const sep = it.sep;
							sep.push(this.sourceToken);
							delete it.key;
							delete it.sep;
							this.stack.push({
								type: "block-map",
								offset: this.offset,
								indent: this.indent,
								items: [{
									start,
									key,
									sep
								}]
							});
						} else if (start.length > 0) it.sep = it.sep.concat(start, this.sourceToken);
						else it.sep.push(this.sourceToken);
					} else if (!it.sep) Object.assign(it, {
						key: null,
						sep: [this.sourceToken]
					});
					else if (it.value || atNextItem) map.items.push({
						start,
						key: null,
						sep: [this.sourceToken]
					});
					else if (includesToken(it.sep, "map-value-ind")) this.stack.push({
						type: "block-map",
						offset: this.offset,
						indent: this.indent,
						items: [{
							start: [],
							key: null,
							sep: [this.sourceToken]
						}]
					});
					else it.sep.push(this.sourceToken);
					this.onKeyLine = true;
					return;
				case "alias":
				case "scalar":
				case "single-quoted-scalar":
				case "double-quoted-scalar": {
					const fs = this.flowScalar(this.type);
					if (atNextItem || it.value) {
						map.items.push({
							start,
							key: fs,
							sep: []
						});
						this.onKeyLine = true;
					} else if (it.sep) this.stack.push(fs);
					else {
						Object.assign(it, {
							key: fs,
							sep: []
						});
						this.onKeyLine = true;
					}
					return;
				}
				default: {
					const bv = this.startBlockValue(map);
					if (bv) {
						if (bv.type === "block-seq") {
							if (!it.explicitKey && it.sep && !includesToken(it.sep, "newline")) {
								yield* this.pop({
									type: "error",
									offset: this.offset,
									message: "Unexpected block-seq-ind on same line with key",
									source: this.source
								});
								return;
							}
						} else if (atMapIndent) map.items.push({ start });
						this.stack.push(bv);
						return;
					}
				}
			}
		}
		yield* this.pop();
		yield* this.step();
	}
	*blockSequence(seq) {
		const it = seq.items[seq.items.length - 1];
		switch (this.type) {
			case "newline":
				if (it.value) {
					const end = "end" in it.value ? it.value.end : void 0;
					if ((Array.isArray(end) ? end[end.length - 1] : void 0)?.type === "comment") end?.push(this.sourceToken);
					else seq.items.push({ start: [this.sourceToken] });
				} else it.start.push(this.sourceToken);
				return;
			case "space":
			case "comment":
				if (it.value) seq.items.push({ start: [this.sourceToken] });
				else {
					if (this.atIndentedComment(it.start, seq.indent)) {
						const end = seq.items[seq.items.length - 2]?.value?.end;
						if (Array.isArray(end)) {
							arrayPushArray(end, it.start);
							end.push(this.sourceToken);
							seq.items.pop();
							return;
						}
					}
					it.start.push(this.sourceToken);
				}
				return;
			case "anchor":
			case "tag":
				if (it.value || this.indent <= seq.indent) break;
				it.start.push(this.sourceToken);
				return;
			case "seq-item-ind":
				if (this.indent !== seq.indent) break;
				if (it.value || includesToken(it.start, "seq-item-ind")) seq.items.push({ start: [this.sourceToken] });
				else it.start.push(this.sourceToken);
				return;
		}
		if (this.indent > seq.indent) {
			const bv = this.startBlockValue(seq);
			if (bv) {
				this.stack.push(bv);
				return;
			}
		}
		yield* this.pop();
		yield* this.step();
	}
	*flowCollection(fc) {
		const it = fc.items[fc.items.length - 1];
		if (this.type === "flow-error-end") {
			let top;
			do {
				yield* this.pop();
				top = this.peek(1);
			} while (top?.type === "flow-collection");
		} else if (fc.end.length === 0) {
			switch (this.type) {
				case "comma":
				case "explicit-key-ind":
					if (!it || it.sep) fc.items.push({ start: [this.sourceToken] });
					else it.start.push(this.sourceToken);
					return;
				case "map-value-ind":
					if (!it || it.value) fc.items.push({
						start: [],
						key: null,
						sep: [this.sourceToken]
					});
					else if (it.sep) it.sep.push(this.sourceToken);
					else Object.assign(it, {
						key: null,
						sep: [this.sourceToken]
					});
					return;
				case "space":
				case "comment":
				case "newline":
				case "anchor":
				case "tag":
					if (!it || it.value) fc.items.push({ start: [this.sourceToken] });
					else if (it.sep) it.sep.push(this.sourceToken);
					else it.start.push(this.sourceToken);
					return;
				case "alias":
				case "scalar":
				case "single-quoted-scalar":
				case "double-quoted-scalar": {
					const fs = this.flowScalar(this.type);
					if (!it || it.value) fc.items.push({
						start: [],
						key: fs,
						sep: []
					});
					else if (it.sep) this.stack.push(fs);
					else Object.assign(it, {
						key: fs,
						sep: []
					});
					return;
				}
				case "flow-map-end":
				case "flow-seq-end":
					fc.end.push(this.sourceToken);
					return;
			}
			const bv = this.startBlockValue(fc);
			/* istanbul ignore else should not happen */
			if (bv) this.stack.push(bv);
			else {
				yield* this.pop();
				yield* this.step();
			}
		} else {
			const parent = this.peek(2);
			if (parent.type === "block-map" && (this.type === "map-value-ind" && parent.indent === fc.indent || this.type === "newline" && !parent.items[parent.items.length - 1].sep)) {
				yield* this.pop();
				yield* this.step();
			} else if (this.type === "map-value-ind" && parent.type !== "flow-collection") {
				const start = getFirstKeyStartProps(getPrevProps(parent));
				fixFlowSeqItems(fc);
				const sep = fc.end.splice(1, fc.end.length);
				sep.push(this.sourceToken);
				const map = {
					type: "block-map",
					offset: fc.offset,
					indent: fc.indent,
					items: [{
						start,
						key: fc,
						sep
					}]
				};
				this.onKeyLine = true;
				this.stack[this.stack.length - 1] = map;
			} else yield* this.lineEnd(fc);
		}
	}
	flowScalar(type) {
		if (this.onNewLine) {
			let nl = this.source.indexOf("\n") + 1;
			while (nl !== 0) {
				this.onNewLine(this.offset + nl);
				nl = this.source.indexOf("\n", nl) + 1;
			}
		}
		return {
			type,
			offset: this.offset,
			indent: this.indent,
			source: this.source
		};
	}
	startBlockValue(parent) {
		switch (this.type) {
			case "alias":
			case "scalar":
			case "single-quoted-scalar":
			case "double-quoted-scalar": return this.flowScalar(this.type);
			case "block-scalar-header": return {
				type: "block-scalar",
				offset: this.offset,
				indent: this.indent,
				props: [this.sourceToken],
				source: ""
			};
			case "flow-map-start":
			case "flow-seq-start": return {
				type: "flow-collection",
				offset: this.offset,
				indent: this.indent,
				start: this.sourceToken,
				items: [],
				end: []
			};
			case "seq-item-ind": return {
				type: "block-seq",
				offset: this.offset,
				indent: this.indent,
				items: [{ start: [this.sourceToken] }]
			};
			case "explicit-key-ind": {
				this.onKeyLine = true;
				const start = getFirstKeyStartProps(getPrevProps(parent));
				start.push(this.sourceToken);
				return {
					type: "block-map",
					offset: this.offset,
					indent: this.indent,
					items: [{
						start,
						explicitKey: true
					}]
				};
			}
			case "map-value-ind": {
				this.onKeyLine = true;
				const start = getFirstKeyStartProps(getPrevProps(parent));
				return {
					type: "block-map",
					offset: this.offset,
					indent: this.indent,
					items: [{
						start,
						key: null,
						sep: [this.sourceToken]
					}]
				};
			}
		}
		return null;
	}
	atIndentedComment(start, indent) {
		if (this.type !== "comment") return false;
		if (this.indent <= indent) return false;
		return start.every((st) => st.type === "newline" || st.type === "space");
	}
	*documentEnd(docEnd) {
		if (this.type !== "doc-mode") {
			if (docEnd.end) docEnd.end.push(this.sourceToken);
			else docEnd.end = [this.sourceToken];
			if (this.type === "newline") yield* this.pop();
		}
	}
	*lineEnd(token) {
		switch (this.type) {
			case "comma":
			case "doc-start":
			case "doc-end":
			case "flow-seq-end":
			case "flow-map-end":
			case "map-value-ind":
				yield* this.pop();
				yield* this.step();
				break;
			case "newline": this.onKeyLine = false;
			default:
				if (token.end) token.end.push(this.sourceToken);
				else token.end = [this.sourceToken];
				if (this.type === "newline") yield* this.pop();
		}
	}
};
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/dist/public-api.js
function parseOptions(options) {
	const prettyErrors = options.prettyErrors !== false;
	return {
		lineCounter: options.lineCounter || prettyErrors && new LineCounter() || null,
		prettyErrors
	};
}
/**
* Parse the input as a stream of YAML documents.
*
* Documents should be separated from each other by `...` or `---` marker lines.
*
* @returns If an empty `docs` array is returned, it will be of type
*   EmptyStream and contain additional stream information. In
*   TypeScript, you should use `'empty' in docs` as a type guard for it.
*/
function parseAllDocuments(source, options = {}) {
	const { lineCounter, prettyErrors } = parseOptions(options);
	const parser = new Parser(lineCounter?.addNewLine);
	const composer = new Composer(options);
	const docs = Array.from(composer.compose(parser.parse(source)));
	if (prettyErrors && lineCounter) for (const doc of docs) {
		doc.errors.forEach(prettifyError(source, lineCounter));
		doc.warnings.forEach(prettifyError(source, lineCounter));
	}
	if (docs.length > 0) return docs;
	return Object.assign([], { empty: true }, composer.streamInfo());
}
/** Parse an input string into a single YAML.Document */
function parseDocument(source, options = {}) {
	const { lineCounter, prettyErrors } = parseOptions(options);
	const parser = new Parser(lineCounter?.addNewLine);
	const composer = new Composer(options);
	let doc = null;
	for (const _doc of composer.compose(parser.parse(source), true, source.length)) if (!doc) doc = _doc;
	else if (doc.options.logLevel !== "silent") {
		doc.errors.push(new YAMLParseError(_doc.range.slice(0, 2), "MULTIPLE_DOCS", "Source contains multiple documents; please use YAML.parseAllDocuments()"));
		break;
	}
	if (prettyErrors && lineCounter) {
		doc.errors.forEach(prettifyError(source, lineCounter));
		doc.warnings.forEach(prettifyError(source, lineCounter));
	}
	return doc;
}
function parse(src, reviver, options) {
	let _reviver = void 0;
	if (typeof reviver === "function") _reviver = reviver;
	else if (options === void 0 && reviver && typeof reviver === "object") options = reviver;
	const doc = parseDocument(src, options);
	if (!doc) return null;
	doc.warnings.forEach((warning) => warn(doc.options.logLevel, warning));
	if (doc.errors.length > 0) {
		if (doc.options.logLevel !== "silent") throw doc.errors[0];
		else doc.errors = [];
	}
	return doc.toJS(Object.assign({ reviver: _reviver }, options));
}
function stringify(value, replacer, options) {
	let _replacer = null;
	if (typeof replacer === "function" || Array.isArray(replacer)) _replacer = replacer;
	else if (options === void 0 && replacer) options = replacer;
	if (typeof options === "string") options = options.length;
	if (typeof options === "number") {
		const indent = Math.round(options);
		options = indent < 1 ? void 0 : indent > 8 ? { indent: 8 } : { indent };
	}
	if (value === void 0) {
		const { keepUndefined } = options ?? replacer ?? {};
		if (!keepUndefined) return void 0;
	}
	if (isDocument(value) && !_replacer) return value.toString(options);
	return new Document$1(value, _replacer, options).toString(options);
}
// node_modules/.pnpm/yaml@2.9.1/node_modules/yaml/browser/index.js
var browser_default = /* @__PURE__ */ __exportAll({
	Alias: () => Alias,
	CST: () => cst_exports,
	Composer: () => Composer,
	Document: () => Document$1,
	Lexer: () => Lexer,
	LineCounter: () => LineCounter,
	Pair: () => Pair,
	Parser: () => Parser,
	Scalar: () => Scalar,
	Schema: () => Schema,
	YAMLError: () => YAMLError,
	YAMLMap: () => YAMLMap,
	YAMLParseError: () => YAMLParseError,
	YAMLSeq: () => YAMLSeq,
	YAMLWarning: () => YAMLWarning,
	isAlias: () => isAlias,
	isCollection: () => isCollection$1,
	isDocument: () => isDocument,
	isMap: () => isMap,
	isNode: () => isNode,
	isPair: () => isPair,
	isScalar: () => isScalar$1,
	isSeq: () => isSeq,
	parse: () => parse,
	parseAllDocuments: () => parseAllDocuments,
	parseDocument: () => parseDocument,
	stringify: () => stringify,
	visit: () => visit$1,
	visitAsync: () => visitAsync
});
// src/function/macro_like.ts
function getVariableOption(context, type) {
	return type !== "message" ? { type } : {
		type,
		message_id: context.message_id ?? chat.findLastIndex((message) => _.isObject(message.variables?.[message.swipe_id ?? 0]))
	};
}
function getWithout$(variables, path) {
	return omitDeepBy(_.get(variables, _.unescape(path), null), (_, key) => key.startsWith("$"));
}
var format_variable_regex = /^(.*)\{\{format_(message|chat|character|preset|global)_variable::(.*?)\}\}/im;
function applyFormatVariable(context, _substring, prefix, type, path) {
	const match = prefix.match(format_variable_regex);
	if (match) prefix = applyFormatVariable(context, "", match[1], match[2], match[3]) + prefix.slice(match[0].length);
	const value = getWithout$(get_variables_without_clone(getVariableOption(context, type)), path);
	return prefix + (typeof value === "string" ? value : browser_default.stringify(value, { blockQuote: "literal" }).trimEnd()).replaceAll("\n", "\n" + " ".repeat(prefix.length));
}
var format_message_variable_quoted_regex = /^(.*)\{\{format_(message|chat|character|preset|global)_variable_quoted::(.*?)\}\}/im;
function applyFormatMessageVariableQuoted(context, _substring, prefix, type, path) {
	const match = prefix.match(format_message_variable_quoted_regex);
	if (match) prefix = applyFormatMessageVariableQuoted(context, "", match[1], match[2], match[3]) + prefix.slice(match[0].length);
	const value = getWithout$(get_variables_without_clone(getVariableOption(context, type)), path);
	return prefix + (typeof value === "string" ? value : browser_default.stringify(value, {
		blockQuote: "literal",
		defaultKeyType: "PLAIN",
		defaultStringType: "QUOTE_DOUBLE"
	}).trimEnd()).replaceAll("\n", "\n" + " ".repeat(prefix.length));
}
var macros = [
	{
		regex: /\{\{get_(message|chat|character|preset|global)_variable::(.*?)\}\}/gi,
		replace: (context, _substring, type, path) => {
			const value = getWithout$(get_variables_without_clone(getVariableOption(context, type)), path);
			return typeof value === "string" ? value : JSON.stringify(value);
		}
	},
	{
		regex: /^(.*)\{\{format_(message|chat|character|preset|global)_variable_quoted::(.*?)\}\}/gim,
		replace: applyFormatMessageVariableQuoted
	},
	{
		regex: /^(.*)\{\{format_(message|chat|character|preset|global)_variable::(.*?)\}\}/gim,
		replace: applyFormatVariable
	}
];
function registerMacroLike(regex, replace) {
	if (!macros.some((macro) => macro.regex.source === regex.source)) macros.push({
		regex,
		replace
	});
	return { unregister: () => unregisterMacroLike(regex) };
}
function _registerMacroLike(regex, replace) {
	const { unregister } = registerMacroLike(regex, replace);
	$(this).on("pagehide", unregister);
	return { unregister };
}
function unregisterMacroLike(regex) {
	const index = macros.findIndex((macro) => macro.regex.source === regex.source);
	if (index !== -1) macros.splice(index, 1);
}
// node_modules/.pnpm/compare-versions@6.1.1/node_modules/compare-versions/lib/esm/utils.js
var semver = /^[v^~<>=]*?(\d+)(?:\.([x*]|\d+)(?:\.([x*]|\d+)(?:\.([x*]|\d+))?(?:-([\da-z\-]+(?:\.[\da-z\-]+)*))?(?:\+[\da-z\-]+(?:\.[\da-z\-]+)*)?)?)?$/i;
var validateAndParse = (version) => {
	if (typeof version !== "string") throw new TypeError("Invalid argument expected string");
	const match = version.match(semver);
	if (!match) throw new Error(`Invalid argument not valid semver ('${version}' received)`);
	match.shift();
	return match;
};
var isWildcard = (s) => s === "*" || s === "x" || s === "X";
var tryParse = (v) => {
	const n = parseInt(v, 10);
	return isNaN(n) ? v : n;
};
var forceType = (a, b) => typeof a !== typeof b ? [String(a), String(b)] : [a, b];
var compareStrings = (a, b) => {
	if (isWildcard(a) || isWildcard(b)) return 0;
	const [ap, bp] = forceType(tryParse(a), tryParse(b));
	if (ap > bp) return 1;
	if (ap < bp) return -1;
	return 0;
};
var compareSegments = (a, b) => {
	for (let i = 0; i < Math.max(a.length, b.length); i++) {
		const r = compareStrings(a[i] || "0", b[i] || "0");
		if (r !== 0) return r;
	}
	return 0;
};
// node_modules/.pnpm/compare-versions@6.1.1/node_modules/compare-versions/lib/esm/compareVersions.js
/**
* Compare [semver](https://semver.org/) version strings to find greater, equal or lesser.
* This library supports the full semver specification, including comparing versions with different number of digits like `1.0.0`, `1.0`, `1`, and pre-release versions like `1.0.0-alpha`.
* @param v1 - First version to compare
* @param v2 - Second version to compare
* @returns Numeric value compatible with the [Array.sort(fn) interface](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/sort#Parameters).
*/
var compareVersions = (v1, v2) => {
	const n1 = validateAndParse(v1);
	const n2 = validateAndParse(v2);
	const p1 = n1.pop();
	const p2 = n2.pop();
	const r = compareSegments(n1, n2);
	if (r !== 0) return r;
	if (p1 && p2) return compareSegments(p1.split("."), p2.split("."));
	else if (p1 || p2) return p1 ? -1 : 1;
	return 0;
};
// node_modules/.pnpm/compare-versions@6.1.1/node_modules/compare-versions/lib/esm/compare.js
/**
* Compare [semver](https://semver.org/) version strings using the specified operator.
*
* @param v1 First version to compare
* @param v2 Second version to compare
* @param operator Allowed arithmetic operator to use
* @returns `true` if the comparison between the firstVersion and the secondVersion satisfies the operator, `false` otherwise.
*
* @example
* ```
* compare('10.1.8', '10.0.4', '>'); // return true
* compare('10.0.1', '10.0.1', '='); // return true
* compare('10.1.1', '10.2.2', '<'); // return true
* compare('10.1.1', '10.2.2', '<='); // return true
* compare('10.1.1', '10.2.2', '>='); // return false
* ```
*/
var compare = (v1, v2, operator) => {
	assertValidOperator(operator);
	const res = compareVersions(v1, v2);
	return operatorResMap[operator].includes(res);
};
var operatorResMap = {
	">": [1],
	">=": [0, 1],
	"=": [0],
	"<=": [-1, 0],
	"<": [-1],
	"!=": [-1, 1]
};
var allowedOperators = Object.keys(operatorResMap);
var assertValidOperator = (op) => {
	if (typeof op !== "string") throw new TypeError(`Invalid operator type, expected string but got ${typeof op}`);
	if (allowedOperators.indexOf(op) === -1) throw new Error(`Invalid operator, expected one of ${allowedOperators.join("|")}`);
};
// \0vite/preload-helper.js
var scriptRel = "modulepreload";
var assetsURL = function(dep) {
	return "/" + dep;
};
var seen = {};
var isCssPreloadUrl = function isCssPreloadUrl(url) {
	return url.pathname.endsWith(".css");
};
var preloadOnce = function preloadOnce(seen, href, preload) {
	if (href in seen) return seen[href];
	const promise = preload();
	if (!promise) {
		seen[href] = void 0;
		return;
	}
	const preloadPromise = promise.then(() => {
		seen[href] = void 0;
	}, (err) => {
		seen[href] = void 0;
		throw err;
	});
	seen[href] = preloadPromise;
	return preloadPromise;
};
var __vitePreload = function preload(baseModule, deps, importerUrl) {
	let promise = Promise.resolve();
	if (deps && deps.length > 0) {
		let preloadedHrefs;
		const cspNonceMeta = document.querySelector("meta[property=csp-nonce]");
		const cspNonce = cspNonceMeta?.nonce || cspNonceMeta?.getAttribute("nonce");
		function allSettled(promises) {
			return Promise.all(promises.map((p) => Promise.resolve(p).then((value) => ({
				status: "fulfilled",
				value
			}), (reason) => ({
				status: "rejected",
				reason
			}))));
		}
		function importMetaResolve(specifier) {
			if (import.meta.resolve) return new URL(import.meta.resolve(specifier));
			return new URL(
				specifier,
				/** #__KEEP__ */
				import.meta.url
			);
		}
		promise = allSettled(deps.map((depString) => {
			depString = assetsURL(depString, importerUrl);
			const dep = importMetaResolve(depString);
			const isCss = isCssPreloadUrl(dep);
			return preloadOnce(seen, dep.href, () => {
				if (preloadedHrefs === void 0) {
					preloadedHrefs = {
						all: /* @__PURE__ */ new Set(),
						styles: /* @__PURE__ */ new Set()
					};
					const links = document.getElementsByTagName("link");
					for (let i = links.length - 1; i >= 0; i--) {
						const link = links[i];
						preloadedHrefs.all.add(link.href);
						if (link.rel === "stylesheet") preloadedHrefs.styles.add(link.href);
					}
				}
				if ((isCss ? preloadedHrefs.styles : preloadedHrefs.all).has(dep.href)) return;
				const link = document.createElement("link");
				link.rel = isCss ? "stylesheet" : scriptRel;
				if (!isCss) link.as = "script";
				link.crossOrigin = "";
				link.href = dep.href;
				if (cspNonce) link.setAttribute("nonce", cspNonce);
				document.head.appendChild(link);
				if (isCss) return new Promise((res, rej) => {
					const handleSettle = (e) => {
						link.removeEventListener("load", handleSettle);
						link.removeEventListener("error", handleSettle);
						if (e.type === "error") rej(/* @__PURE__ */ new Error(`Unable to preload CSS for ${dep}`));
						else res(e);
					};
					link.addEventListener("load", handleSettle);
					link.addEventListener("error", handleSettle);
				});
			});
		}).filter((p) => p !== void 0));
	}
	function handlePreloadError(err) {
		const e = new Event("vite:preloadError", { cancelable: true });
		e.payload = err;
		window.dispatchEvent(e);
		if (!e.defaultPrevented) throw err;
	}
	return promise.then((res) => {
		for (const item of res || []) {
			if (item.status !== "rejected") continue;
			handlePreloadError(item.reason);
		}
		return baseModule().catch(handlePreloadError);
	});
};
// src/tauritavern_chat_surface.ts
function getChatSurfaceApi() {
	return window.__TAURITAVERN__?.api?.chatSurface;
}
var usesManagedChatSurface = (() => {
	if (compare(version$1, "1.16.0", "<")) return false;
	const query = getChatSurfaceApi()?.isManagedOwnershipRequired;
	if (query === void 0) return false;
	if (typeof query !== "function") throw new Error("TauriTavern ChatSurface ownership query is unavailable");
	const managed = query();
	if (typeof managed !== "boolean") throw new TypeError("TauriTavern ChatSurface ownership query must return a boolean");
	return managed;
})();
async function refreshManagedChatSurface() {
	if (!usesManagedChatSurface) return;
	try {
		const { redisplayChat } = await __vitePreload(async () => {
			const { redisplayChat } = await import("../script.js");
			return { redisplayChat };
		}, []);
		await redisplayChat({
			startIndex: 0,
			fade: false
		});
	} catch (error) {
		throw error;
	}
}
// src/function/displayed_message.ts
function formatAsDisplayedMessage(text, { message_id = "last" } = {}) {
	if (typeof message_id !== "number" && ![
		"last",
		"last_user",
		"last_char"
	].includes(message_id)) throw Error(`提供的 message_id 无效, 请提供 'last', 'last_user', 'last_char' 或楼层消息号 (-1 表示倒数第一楼), 你提供的是: ${message_id}`);
	const last_message_id = getLastMessageId();
	if (last_message_id === null) throw Error(`未找到任何消息楼层`);
	switch (message_id) {
		case "last":
			message_id = last_message_id;
			break;
		case "last_user": {
			const last_user_message_id = getLastMessageId({ filter: (m) => m.is_user && !m.is_system });
			if (last_user_message_id === null) throw Error(`未找到任何 user 消息楼层, 你提供的是: ${message_id}`);
			message_id = last_user_message_id;
			break;
		}
		case "last_char": {
			const last_char_message_id = getLastMessageId({ filter: (m) => !m.is_user && !m.is_system });
			if (last_char_message_id === null) throw Error(`未找到任何 char 消息楼层, 你提供的是: ${message_id}`);
			message_id = last_char_message_id;
			break;
		}
	}
	const normalized_message_id = normalizeMessageId(message_id);
	if (!inMessageRange(normalized_message_id)) throw Error(`提供的 message_id 不在 [${-last_message_id - 1}, ${last_message_id}] 内, 你提供的是: ${message_id}`);
	const chat_message = chat[normalized_message_id];
	const result = messageFormatting(text, chat_message.name, chat_message.is_system, chat_message.is_user, normalized_message_id);
	const $div = $("<div>").append(result);
	$div.find("pre code").each((_index, element) => {
		const $node = $(element);
		if ($node.hasClass("hljs") || isFrontend($node.text())) return;
		hljs.highlightElement(element);
	});
	return $div.html();
}
function retrieveDisplayedMessage(message_id) {
	return $(`#chat > .mes[mesid = "${message_id}"]`, window.parent.document).find(`div.mes_text`);
}
async function refreshOneMessage(message_id, $mes) {
	if ($mes && $mes.length === 0) return;
	if (usesManagedChatSurface) {
		await refreshManagedChatSurface();
		return;
	}
	$mes = $mes ?? $(`#chat > .mes[mesid = "${message_id}"]`);
	if (!$mes) return;
	const chat_message = chat[message_id];
	$mes.attr({
		mesid: message_id,
		swipeid: chat_message.swipe_id ?? 0,
		ch_name: chat_message.name,
		is_user: chat_message.is_user,
		is_system: !!chat_message.is_system,
		force_avatar: !!chat_message.force_avatar,
		type: chat_message.extra?.type ?? ""
	});
	$mes.find(".avatar img").attr("src", chat_message.force_avatar ? chat_message.force_avatar : chat_message.is_user ? getThumbnailUrl("persona", user_avatar) : this_chid === void 0 ? system_avatar : characters[Number(this_chid)].avatar !== "none" ? getThumbnailUrl("avatar", characters[Number(this_chid)].avatar) : default_avatar);
	$mes.find(".ch_name .name_text").text(chat_message.name);
	$mes.find(".mesIDDisplay").text(`#${message_id}`);
	if (chat_message.extra?.token_count) $mes.find(".tokenCounterDisplay").text(`${chat_message.extra.token_count}t`);
	if (chat_message.swipes) {
		$mes.find(".swipes-counter").text(`${chat_message.swipe_id + 1}\u200b/\u200b${chat_message.swipes.length}`);
		if (message_id === chat.length - 1) showSwipeButtons();
	}
	$mes.find(".mes_text").empty().append(messageFormatting(chat_message.mes, chat_message.name, chat_message.is_system, chat_message.is_user, message_id));
	$mes.find("pre code").each((_index, element) => {
		highlight_code(element);
		if ($(element).is("[data-highlighted=\"yes\"]")) $(element).css("position", "relative");
	});
	await eventSource.emit(chat_message.is_user ? event_types.USER_MESSAGE_RENDERED : event_types.CHARACTER_MESSAGE_RENDERED, message_id);
}
// src/function/tavern_regex.ts
function formatAsTavernRegexedString(text, source, destination, { depth, character_name } = {}) {
	let result = getRegexedString(text, {
		user_input: regex_placement.USER_INPUT,
		ai_output: regex_placement.AI_OUTPUT,
		slash_command: regex_placement.SLASH_COMMAND,
		world_info: regex_placement.WORLD_INFO,
		reasoning: regex_placement.REASONING
	}[source], {
		characterOverride: character_name,
		isMarkdown: destination === "display",
		isPrompt: destination === "prompt",
		depth
	});
	result = substituteParams(result, void 0, character_name, void 0, void 0);
	macros.forEach((macro) => {
		result = result.replace(macro.regex, (substring, ...args) => macro.replace({
			role: {
				user_input: "user",
				ai_output: "assistant",
				slash_command: "system",
				world_info: "system",
				reasoning: "system"
			}[source],
			message_id: depth !== void 0 ? chat.length - depth - 1 : void 0
		}, substring, ...args));
	});
	return result;
}
function get_tavern_regexes_without_clone(option) {
	let data;
	switch (option.type) {
		case "global":
			data = extension_settings.regex ?? [];
			break;
		case "character": {
			const id = RawCharacter.findIndex(option.name ?? "current");
			data = characters.at(id)?.data?.extensions?.regex_scripts ?? [];
			break;
		}
		case "preset":
			option.name ??= "in_use";
			data = (option.name === "in_use" ? oai_settings : getCompletionPresetByName(option.name))?.extensions?.regex_scripts ?? [];
	}
	return data.map(to_tavern_regex);
}
function to_tavern_regex(regex_script_data) {
	return {
		id: regex_script_data.id,
		script_name: regex_script_data.scriptName,
		enabled: !regex_script_data.disabled,
		find_regex: regex_script_data.findRegex,
		trim_strings: regex_script_data.trimStrings || [],
		replace_string: regex_script_data.replaceString,
		source: {
			user_input: regex_script_data.placement.includes(regex_placement.USER_INPUT),
			ai_output: regex_script_data.placement.includes(regex_placement.AI_OUTPUT),
			slash_command: regex_script_data.placement.includes(regex_placement.SLASH_COMMAND),
			world_info: regex_script_data.placement.includes(regex_placement.WORLD_INFO),
			reasoning: regex_script_data.placement.includes(regex_placement.REASONING)
		},
		destination: {
			display: regex_script_data.markdownOnly,
			prompt: regex_script_data.promptOnly
		},
		run_on_edit: regex_script_data.runOnEdit,
		min_depth: typeof regex_script_data.minDepth === "number" ? regex_script_data.minDepth : null,
		max_depth: typeof regex_script_data.maxDepth === "number" ? regex_script_data.maxDepth : null
	};
}
function from_tavern_regex(tavern_regex) {
	return {
		id: tavern_regex.id,
		scriptName: tavern_regex.script_name,
		disabled: !tavern_regex.enabled,
		runOnEdit: tavern_regex.run_on_edit,
		findRegex: tavern_regex.find_regex,
		trimStrings: tavern_regex.trim_strings || [],
		replaceString: tavern_regex.replace_string,
		placement: [
			...tavern_regex.source.user_input ? [regex_placement.USER_INPUT] : [],
			...tavern_regex.source.ai_output ? [regex_placement.AI_OUTPUT] : [],
			...tavern_regex.source.slash_command ? [regex_placement.SLASH_COMMAND] : [],
			...tavern_regex.source.world_info ? [regex_placement.WORLD_INFO] : [],
			...tavern_regex.source.reasoning ? [regex_placement.REASONING] : []
		],
		substituteRegex: 0,
		minDepth: tavern_regex.min_depth,
		maxDepth: tavern_regex.max_depth,
		markdownOnly: tavern_regex.destination.display,
		promptOnly: tavern_regex.destination.prompt
	};
}
function isCharacterTavernRegexesEnabled() {
	return (extension_settings?.character_allowed_regex)?.includes(characters.at(Number(this_chid))?.avatar ?? "");
}
function getTavernRegexes(option) {
	if (option?.type === void 0) {
		option ??= { type: "global" };
		const { scope = "all", enable_state = "all" } = option;
		if (![
			"all",
			"enabled",
			"disabled"
		].includes(enable_state)) throw Error(`提供的 enable_state 无效, 请提供 'all', 'enabled' 或 'disabled', 你提供的是: ${enable_state}`);
		if (![
			"all",
			"global",
			"character"
		].includes(scope)) throw Error(`提供的 scope 无效, 请提供 'all', 'global' 或 'character', 你提供的是: ${scope}`);
		let regexes = [];
		if (scope === "all" || scope === "global") regexes = [...regexes, ...get_tavern_regexes_without_clone({ type: "global" }).map((regex) => ({
			...regex,
			scope: "global"
		}))];
		if (scope === "all" || scope === "character") regexes = [...regexes, ...get_tavern_regexes_without_clone({ type: "character" }).map((regex) => ({
			...regex,
			scope: "character"
		}))];
		if (enable_state !== "all") regexes = regexes.filter((regex) => regex.enabled === (enable_state === "enabled"));
		return klona(regexes);
	}
	return klona(get_tavern_regexes_without_clone(option));
}
async function render_tavern_regexes() {
	await saveSettings();
	await Promise.all($("#chat > .mes").map((_index, element) => {
		return refreshOneMessage(Number($(element).attr("mesid")));
	}));
	await eventSource.emit(event_types.CHAT_CHANGED, getCurrentChatId());
}
var render_tavern_regexes_debounced = _.debounce(render_tavern_regexes, 1e3);
async function replaceTavernRegexes(regexes, option) {
	regexes.filter((regex) => regex.script_name == "").forEach((regex) => {
		regex.script_name = `未命名-${regex.id}`;
	});
	if (option?.type === void 0) {
		option ??= { type: "global" };
		const { scope = "all" } = option;
		if (![
			"all",
			"global",
			"character"
		].includes(scope)) throw Error(`提供的 scope 无效, 请提供 'all', 'global' 或 'character', 你提供的是: ${scope}`);
		const [global_regexes, character_regexes] = _.partition(regexes, (regex) => regex.scope === "global").map((paritioned) => paritioned.map(from_tavern_regex));
		const character = characters.at(this_chid);
		if (scope === "all" || scope === "global") extension_settings.regex = global_regexes;
		if (scope === "all" || scope === "character") {
			if (!character) return;
			await writeExtensionField(this_chid, "regex_scripts", character_regexes);
		}
		return render_tavern_regexes_debounced();
	}
	const converted = regexes.map(from_tavern_regex);
	switch (option.type) {
		case "global":
			extension_settings.regex = converted;
			break;
		case "preset":
			option.name ??= "in_use";
			if (option.name !== "in_use" && !preset_manager.getAllPresets().includes(option.name)) return;
			if (option.name === "in_use") {
				_.set(oai_settings, "extensions.regex_scripts", converted);
				saveSettingsDebounced();
			} else {
				const data = getCompletionPresetByName(option.name);
				_.set(data, "extensions.regex_scripts", converted);
				await preset_manager.savePreset(option.name, data, { skipUpdate: true });
			}
			break;
		case "character": {
			const id = RawCharacter.findIndex(option.name ?? "current");
			if (id === -1) {
				const errorMsg = `未能找到角色 '${option.name ?? "current"}'`;
				toastr.error(errorMsg, "角色不存在");
				throw Error(errorMsg);
			}
			if (!characters.at(id)) return;
			await writeExtensionField(String(id), "regex_scripts", converted);
			break;
		}
	}
	return render_tavern_regexes_debounced();
}
async function updateTavernRegexesWith(updater, option) {
	let regexes = getTavernRegexes(option);
	regexes = await updater(regexes);
	await replaceTavernRegexes(regexes, option);
	return regexes;
}
// src/function/lorebook.ts
async function editCurrentCharacter() {
	$("#rm_info_avatar").html("");
	const form_data = new FormData($("#form_create").get(0));
	const raw_file = form_data.get("avatar");
	if (raw_file instanceof File) {
		const converted_file = await ensureImageFormatSupported(raw_file);
		form_data.set("avatar", converted_file);
	}
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	form_data.delete("alternate_greetings");
	const chid = $(".open_alternate_greetings").data("chid");
	if (chid && Array.isArray(characters[chid]?.data?.alternate_greetings)) for (const value of characters[chid].data.alternate_greetings) form_data.append("alternate_greetings", value);
	if (!(await fetch("/api/characters/edit", {
		method: "POST",
		headers,
		body: form_data,
		cache: "no-cache"
	})).ok) return false;
	await getOneCharacter(form_data.get("avatar_url"));
	$("#add_avatar_button").replaceWith($("#add_avatar_button").val("").clone(true));
	$("#create_button").attr("value", "Save");
	return true;
}
function toLorebookSettings(world_info_settings) {
	return {
		selected_global_lorebooks: world_info_settings.world_info.globalSelect,
		scan_depth: world_info_settings.world_info_depth,
		context_percentage: world_info_settings.world_info_budget,
		budget_cap: world_info_settings.world_info_budget_cap,
		min_activations: world_info_settings.world_info_min_activations,
		max_depth: world_info_settings.world_info_min_activations_depth_max,
		max_recursion_steps: world_info_settings.world_info_max_recursion_steps,
		insertion_strategy: {
			0: "evenly",
			1: "character_first",
			2: "global_first"
		}[world_info_settings.world_info_character_strategy],
		include_names: world_info_settings.world_info_include_names,
		recursive: world_info_settings.world_info_recursive,
		case_sensitive: world_info_settings.world_info_case_sensitive,
		match_whole_words: world_info_settings.world_info_match_whole_words,
		use_group_scoring: world_info_settings.world_info_use_group_scoring,
		overflow_alert: world_info_settings.world_info_overflow_alert
	};
}
function assignPartialLorebookSettings(settings) {
	let $inputs = $();
	let $changes = $();
	const for_eachs = {
		selected_global_lorebooks: (value) => {
			$("#world_info").find("option[value!=\"\"]").remove();
			world_names.forEach((item, i) => $("#world_info").append(`<option value='${i}'${value.includes(item) ? " selected" : ""}>${item}</option>`));
			selected_world_info.length = 0;
			selected_world_info.push(...value);
			saveSettings();
		},
		scan_depth: (value) => {
			$inputs = $inputs.add($("#world_info_depth").val(value));
		},
		context_percentage: (value) => {
			$inputs = $inputs.add($("#world_info_budget").val(value));
		},
		budget_cap: (value) => {
			$inputs = $inputs.add($("#world_info_budget_cap").val(value));
		},
		min_activations: (value) => {
			$inputs = $inputs.add($("#world_info_min_activations").val(value));
		},
		max_depth: (value) => {
			$inputs = $inputs.add($("#world_info_min_activations_depth_max").val(value));
		},
		max_recursion_steps: (value) => {
			$inputs = $inputs.add($("#world_info_max_recursion_steps").val(value));
		},
		insertion_strategy: (value) => {
			const converted_value = {
				evenly: 0,
				character_first: 1,
				global_first: 2
			}[value];
			$(`#world_info_character_strategy option[value='${converted_value}']`).prop("selected", true);
			$changes = $changes.add($("#world_info_character_strategy").val(converted_value));
		},
		include_names: (value) => {
			$inputs = $inputs.add($("#world_info_include_names").prop("checked", value));
		},
		recursive: (value) => {
			$inputs = $inputs.add($("#world_info_recursive").prop("checked", value));
		},
		case_sensitive: (value) => {
			$inputs = $inputs.add($("#world_info_case_sensitive").prop("checked", value));
		},
		match_whole_words: (value) => {
			$inputs = $inputs.add($("#world_info_match_whole_words").prop("checked", value));
		},
		use_group_scoring: (value) => {
			$changes = $changes.add($("#world_info_use_group_scoring").prop("checked", value));
		},
		overflow_alert: (value) => {
			$changes = $changes.add($("#world_info_overflow_alert").prop("checked", value));
		}
	};
	Object.entries(settings).filter(([_, value]) => value !== void 0).forEach(([field, value]) => {
		for_eachs[field]?.(value);
	});
	$inputs.trigger("input");
	$changes.trigger("change");
}
function getLorebookSettings() {
	return klona(toLorebookSettings(getWorldInfoSettings()));
}
function setLorebookSettings(settings) {
	if (settings.selected_global_lorebooks) {
		const inexisting_lorebooks = settings.selected_global_lorebooks.filter((lorebook) => !world_names.includes(lorebook));
		if (inexisting_lorebooks.length > 0) throw Error(`尝试修改要全局启用的世界书, 但未找到以下世界书: ${JSON.stringify(inexisting_lorebooks)}`);
	}
	const original_settings = getLorebookSettings();
	settings = _.omitBy(settings, (value, key) => value === original_settings[key]);
	assignPartialLorebookSettings(settings);
}
function getLorebooks() {
	return klona(world_names);
}
async function deleteLorebook(lorebook) {
	return deleteWorldInfo(lorebook);
}
async function createLorebook(lorebook) {
	return createNewWorldInfo(lorebook, { interactive: false });
}
function getCharLorebooks({ name = "current" } = {}) {
	const character = RawCharacter.find({ name });
	const books = {
		primary: null,
		additional: []
	};
	if (!character) {
		if (name === "current") return books;
		throw Error(`未找到${`名为 '${name}' `}的角色卡`);
	}
	if (character.data?.extensions?.world) books.primary = character.data?.extensions?.world || null;
	const filename = character.avatar.replace(/\.[^/.]+$/, "");
	const extra_charlore = world_info.charLore?.find((e) => e.name === filename);
	if (extra_charlore && Array.isArray(extra_charlore.extraBooks)) books.additional = extra_charlore.extraBooks;
	return klona(books);
}
function getCurrentCharPrimaryLorebook() {
	return getCharLorebooks().primary;
}
async function setCurrentCharLorebooks(lorebooks) {
	const filename = getCharaFilename(this_chid);
	if (!filename) throw Error(`未打开任何角色卡`);
	const inexisting_lorebooks = _(_.concat(lorebooks.primary ? [lorebooks.primary] : [], lorebooks.additional)).reject(_.isNull).reject((lorebook_name) => getLorebooks().some((value) => value === lorebook_name)).value();
	if (inexisting_lorebooks.length > 0) throw Error(`尝试修改 '${filename}' 绑定的世界书, 但未找到以下世界书: ${inexisting_lorebooks}`);
	if (lorebooks.primary !== void 0) {
		const previous_primary = String($("#character_world").val());
		$("#character_world").val(lorebooks.primary || "");
		$(".character_world_info_selector").find("option:selected").val(lorebooks.primary ? world_names.indexOf(lorebooks.primary) : "");
		if (previous_primary && !lorebooks.primary) {
			const data = JSON.parse(String($("#character_json_data").val()));
			if (data?.data?.character_book) data.data.character_book = void 0;
			$("#character_json_data").val(JSON.stringify(data));
		}
		if (!await editCurrentCharacter()) throw Error(`尝试为 '${filename}' 绑定主要世界书, 但在访问酒馆后端时出错`);
		setWorldInfoButtonClass(void 0, !!lorebooks.primary);
	}
	if (lorebooks.additional !== void 0) {
		const char_lore = world_info.charLore ?? [];
		const existing_char_index = char_lore.findIndex((entry) => entry.name === filename);
		if (existing_char_index === -1) char_lore.push({
			name: filename,
			extraBooks: lorebooks.additional
		});
		else if (lorebooks.additional.length === 0) char_lore.splice(existing_char_index, 1);
		else char_lore[existing_char_index].extraBooks = lorebooks.additional;
		Object.assign(world_info, { charLore: char_lore });
	}
	saveCharacterDebounced();
	saveSettingsDebounced();
}
function getChatLorebook() {
	if (!getCurrentChatId()) throw Error(`未打开任何聊天, 不可获取聊天世界书`);
	const existing_lorebook = _.get(chat_metadata, METADATA_KEY, "");
	if (world_names.includes(existing_lorebook)) return existing_lorebook;
	_.unset(chat_metadata, METADATA_KEY);
	return null;
}
async function setChatLorebook(lorebook) {
	if (lorebook === null) {
		_.unset(chat_metadata, METADATA_KEY);
		$(".chat_lorebook_button").removeClass("world_set");
	} else {
		if (!world_names.includes(lorebook)) throw new Error(`尝试为角色卡绑定聊天世界书, 但该世界书 '${lorebook}' 不存在`);
		_.set(chat_metadata, METADATA_KEY, lorebook);
		$(".chat_lorebook_button").addClass("world_set");
	}
	await saveMetadata();
}
async function getOrCreateChatLorebook(lorebook) {
	const existing_lorebook = await getChatLorebook();
	if (existing_lorebook !== null) return existing_lorebook;
	const new_lorebook = (() => {
		if (lorebook) {
			if (world_names.includes(lorebook)) throw new Error(`尝试创建聊天世界书, 但该名称 '${lorebook}' 已存在`);
			return lorebook;
		}
		return `Chat Book ${getCurrentChatId()}`.replace(/[^a-z0-9]/gi, "_").replace(/_{2,}/g, "_").substring(0, 64);
	})();
	await createNewWorldInfo(new_lorebook);
	await setChatLorebook(new_lorebook);
	return new_lorebook;
}
// src/function/worldbook.ts
function getWorldbookNames() {
	return klona(world_names);
}
function getGlobalWorldbookNames() {
	return klona(getWorldInfoSettings().world_info.globalSelect);
}
async function rebindGlobalWorldbooks(worldbook_names) {
	const $world_info = $("#world_info");
	$world_info.find("option[value!=\"\"]").remove();
	$world_info.append(world_names.map((item, i) => {
		const should_select = worldbook_names.includes(item);
		return new Option(item, String(i), should_select, should_select);
	}));
	selected_world_info.length = 0;
	selected_world_info.push(...worldbook_names);
	_.set(getWorldInfoSettings().world_info, "globalSelect", selected_world_info);
	saveSettingsDebounced();
}
function getCharWorldbookNames(character_name_or_id) {
	return getCharLorebooks({ name: character_name_or_id });
}
async function rebindCharWorldbooks(character_name_or_id, char_worldbooks) {
	if (character_name_or_id !== "current") throw Error(`目前不支持对非当前角色卡调用 bindCharWorldbooks`);
	if (!RawCharacter.find({ name: character_name_or_id })) throw Error(`角色卡 '${character_name_or_id}' 不存在`);
	return setCurrentCharLorebooks(char_worldbooks);
}
function getChatWorldbookName(chat_name) {
	if (chat_name !== "current") throw Error(`目前不支持对非当前聊天调用 getChatWorldbookName`);
	return getChatLorebook();
}
async function rebindChatWorldbook(chat_name, worldbook_name) {
	if (chat_name !== "current") throw Error(`目前不支持对非当前聊天调用 getChatWorldbookName`);
	await setChatLorebook(worldbook_name);
}
async function getOrCreateChatWorldbook(chat_name, worldbook_name) {
	if (chat_name !== "current") throw Error(`目前不支持对非当前聊天调用 getChatWorldbookName`);
	return await getOrCreateChatLorebook(worldbook_name);
}
var _default_implicit_keys = {
	addMemo: true,
	matchPersonaDescription: false,
	matchCharacterDescription: false,
	matchCharacterPersonality: false,
	matchCharacterDepthPrompt: false,
	matchScenario: false,
	matchCreatorNotes: false,
	group: "",
	groupOverride: false,
	groupWeight: 100,
	caseSensitive: null,
	matchWholeWords: null,
	useGroupScoring: null,
	automationId: "",
	ignoreBudget: false,
	outletName: "",
	triggers: [],
	characterFilter: {
		isExclude: false,
		names: [],
		tags: []
	}
};
function toWorldbookEntry(entry) {
	let result = _({}).set("uid", entry.uid).set("name", entry.comment).set("enabled", !entry.disable).set("strategy.type", entry.constant ? "constant" : entry.vectorized ? "vectorized" : "selective").set("strategy.keys", entry.key.map((value) => parseRegexFromString(value) ?? value)).set("strategy.keys_secondary", {
		logic: {
			0: "and_any",
			1: "not_all",
			2: "not_any",
			3: "and_all"
		}[entry.selectiveLogic],
		keys: entry.keysecondary.map((value) => parseRegexFromString(value) ?? value)
	}).set("strategy.scan_depth", entry.scanDepth ?? "same_as_global").set("position.type", {
		0: "before_character_definition",
		1: "after_character_definition",
		5: "before_example_messages",
		6: "after_example_messages",
		2: "before_author_note",
		3: "after_author_note",
		4: "at_depth",
		7: "outlet"
	}[entry.position]).set("position.role", {
		0: "system",
		1: "user",
		2: "assistant"
	}[entry.role ?? 0]).set("position.depth", entry.depth).set("position.order", entry.order).set("content", entry.content).set("probability", entry.useProbability ? entry.probability : 100).set("recursion.prevent_incoming", entry.excludeRecursion).set("recursion.prevent_outgoing", entry.preventRecursion).set("recursion.delay_until", typeof entry.delayUntilRecursion === "number" && entry.delayUntilRecursion > 0 ? entry.delayUntilRecursion : null).set("effect.sticky", typeof entry.sticky === "number" && entry.sticky > 0 ? entry.sticky : null).set("effect.cooldown", typeof entry.cooldown === "number" && entry.cooldown > 0 ? entry.cooldown : null).set("effect.delay", typeof entry.delay === "number" && entry.delay > 0 ? entry.delay : null);
	if (entry.extra) result = result.set("extra", entry.extra);
	result = result.merge(_.pick(entry, Object.keys(_default_implicit_keys)));
	return result.value();
}
function fromWorldbookEntry(entry, display_index) {
	let result = _({}).set("uid", entry.uid).set("displayIndex", display_index).set("comment", entry.name ?? "").set("disable", !(entry.enabled ?? true)).set("constant", entry?.strategy?.type ? entry?.strategy?.type === "constant" : true).set("selective", entry?.strategy?.type === "selective").set("key", entry?.strategy?.keys?.map(_.toString) ?? []).set("selectiveLogic", {
		and_any: 0,
		not_all: 1,
		not_any: 2,
		and_all: 3
	}[entry?.strategy?.keys_secondary?.logic ?? "and_any"]).set("keysecondary", entry?.strategy?.keys_secondary?.keys?.map(_.toString) ?? []).set("scanDepth", entry?.strategy?.scan_depth === "same_as_global" ? null : entry?.strategy?.scan_depth ?? null).set("vectorized", entry?.strategy?.type === "vectorized").set("position", {
		before_character_definition: 0,
		after_character_definition: 1,
		before_example_messages: 5,
		after_example_messages: 6,
		before_author_note: 2,
		after_author_note: 3,
		at_depth: 4,
		outlet: 7
	}[entry?.position?.type ?? "at_depth"]).set("role", {
		system: 0,
		user: 1,
		assistant: 2
	}[entry?.position?.role ?? "system"]).set("depth", entry?.position?.depth ?? 4).set("order", entry?.position?.order ?? 100).set("content", entry.content ?? "").set("useProbability", true).set("probability", entry.probability ?? 100).set("excludeRecursion", entry.recursion?.prevent_incoming ?? false).set("preventRecursion", entry.recursion?.prevent_outgoing ?? false).set("delayUntilRecursion", entry.recursion?.delay_until ?? false).set("sticky", entry.effect?.sticky ?? null).set("cooldown", entry.effect?.cooldown ?? null).set("delay", entry.effect?.delay ?? null);
	if (entry.extra) result = result.set("extra", entry.extra);
	result = result.merge(_default_implicit_keys).merge(_.pick(entry, Object.keys(_default_implicit_keys)));
	return result.value();
}
function handleWorldbookEntriesCollision(entries) {
	const MAX_UID = 1e6;
	const uid_set = /* @__PURE__ */ new Set();
	const handle_uid_collision = (index) => {
		if (index === void 0) index = _.random(0, 999999);
		let i = 1;
		while (true) {
			if (!uid_set.has(index)) {
				uid_set.add(index);
				return index;
			}
			index = (index + i * i) % MAX_UID;
			++i;
		}
	};
	return entries.map((entry) => ({
		...entry,
		uid: handle_uid_collision(entry.uid)
	}));
}
async function getWorldbook(worldbook_name) {
	if (!getWorldbookNames().includes(worldbook_name)) throw Error(`未能找到世界书 '${worldbook_name}'`);
	const original_worldbook_entries = await loadWorldInfo(worldbook_name).then((data) => data ?? {});
	return klona(_(original_worldbook_entries.entries).values().sortBy("displayIndex").map(toWorldbookEntry).value());
}
async function createWorldbook(worldbook_name, worldbook = []) {
	if (getWorldbookNames().includes(worldbook_name)) return false;
	return await createOrReplaceWorldbook(worldbook_name, worldbook);
}
async function createOrReplaceWorldbook(worldbook_name, worldbook = [], { render = "debounced" } = {}) {
	const is_existing = getWorldbookNames().includes(worldbook_name);
	if (!getWorldbookNames().includes(worldbook_name)) {
		if (!await createNewWorldInfo(worldbook_name, { interactive: false })) return false;
	}
	if (is_existing || worldbook.length > 0) {
		await saveWorldInfo(worldbook_name, { entries: _.merge({}, ..._(handleWorldbookEntriesCollision(worldbook)).map(fromWorldbookEntry).map((entry) => ({ [entry.uid]: entry })).value()) });
		switch (render) {
			case "debounced":
				reloadEditorDebounced(worldbook_name);
				break;
			case "immediate": reloadEditor(worldbook_name);
		}
	}
	return !is_existing;
}
async function deleteWorldbook(worldbook_name) {
	return await deleteWorldInfo(worldbook_name);
}
async function replaceWorldbook(worldbook_name, worldbook, options) {
	if (!getWorldbookNames().includes(worldbook_name)) throw Error(`未能找到世界书 '${worldbook_name}'`);
	await createOrReplaceWorldbook(worldbook_name, worldbook, options);
}
async function updateWorldbookWith(worldbook_name, updater, options) {
	await replaceWorldbook(worldbook_name, await updater(await getWorldbook(worldbook_name)), options);
	return await getWorldbook(worldbook_name);
}
async function createWorldbookEntries(worldbook_name, new_entries, options) {
	let slice_start;
	const worldbook = await updateWorldbookWith(worldbook_name, (data) => {
		slice_start = data.length;
		return [...data, ...new_entries];
	}, options);
	return {
		worldbook,
		new_entries: worldbook.slice(slice_start)
	};
}
async function deleteWorldbookEntries(worldbook_name, predicate, options) {
	let deleted_entries = [];
	return {
		worldbook: await updateWorldbookWith(worldbook_name, (data) => {
			deleted_entries = _.remove(data, predicate);
			return data;
		}, options),
		deleted_entries
	};
}
// node_modules/.pnpm/is-blob@3.0.0/node_modules/is-blob/index.js
var { toString } = Object.prototype;
function isBlob(value) {
	if (typeof Blob === "undefined") return false;
	return value instanceof Blob || toString.call(value) === "[object Blob]";
}
// src/function/character.ts
function getCharacterNames() {
	return characters.map((character) => character.name);
}
function getCharacterIds() {
	return characters.map((character) => character.avatar);
}
function getCurrentCharacterName() {
	return name2 === "" ? null : name2;
}
function getCurrentCharacterId() {
	return RawCharacter.find({ name: "current" })?.avatar ?? null;
}
function toCharacter(character) {
	const data = character.data;
	const first_messages = [character.first_mes ?? data.first_mes, ...data.alternate_greetings];
	let extensions = klona(data.extensions);
	if (_.has(extensions, "regex_scripts")) _.set(extensions, "regex_scripts", _.get(extensions, "regex_scripts", []).map(to_tavern_regex));
	if (_.has(extensions, "tavern_helper")) {
		const tavern_helper = _.get(extensions, "tavern_helper", {});
		_.set(extensions, "tavern_helper", Array.isArray(tavern_helper) ? Object.fromEntries(tavern_helper) : tavern_helper);
	}
	extensions = _.omit(extensions, [
		"TavernHelper_scripts",
		"TavernHelper_characterScriptVariables",
		"fav",
		"talkativeness",
		"world",
		"depth_prompt",
		"pygmalion_id",
		"github_repo",
		"source_url",
		"chub",
		"risuai",
		"sd_character_prompt"
	]);
	return {
		avatar: character.avatar ?? `${data.name}.png`,
		version: data.character_version ?? "",
		creator: data.creator ?? "",
		creator_notes: character.creatorcomment ?? data.creator_notes ?? "",
		description: character.description ?? data.description ?? "",
		first_messages,
		worldbook: getCharWorldbookNames(character.avatar ?? character.name).primary,
		extensions
	};
}
function fromCharacterToPayload(character_name_or_id, new_data, old_data) {
	let world = old_data?.data?.extensions?.world;
	if (new_data.worldbook !== void 0) world = new_data.worldbook || void 0;
	const extensions = klona({
		...old_data?.data?.extensions,
		...new_data.extensions
	});
	if (new_data.extensions?.regex_scripts !== void 0) _.set(extensions, "regex_scripts", new_data.extensions.regex_scripts.map(from_tavern_regex));
	const is_id = character_name_or_id.endsWith(".png");
	const display_name = old_data?.name ?? (is_id ? character_name_or_id.replace(".png", "") : character_name_or_id);
	const unique_name = old_data?.avatar ?? (is_id ? character_name_or_id : character_name_or_id + ".png");
	return {
		ch_name: display_name,
		avatar_url: unique_name,
		avatar: isBlob(new_data.avatar) ? new File([new_data.avatar], unique_name) : void 0,
		character_version: new_data.version ?? old_data?.data.character_version,
		creator: new_data.creator ?? old_data?.data.creator,
		creator_notes: new_data.creator_notes ?? old_data?.data.creator_notes,
		description: new_data.description ?? old_data?.data.description,
		first_mes: (new_data.first_messages?.[0] ?? old_data?.data.first_mes) || "",
		alternate_greetings: (new_data.first_messages?.slice(1) ?? old_data?.data.alternate_greetings) || [],
		world,
		extensions: JSON.stringify(extensions),
		chat: old_data?.chat,
		create_date: old_data?.create_date,
		personality: old_data?.data?.personality,
		scenario: old_data?.data?.scenario,
		mes_example: old_data?.data?.mes_example,
		talkativeness: old_data?.data?.extensions?.talkativeness,
		fav: old_data?.data?.extensions?.fav,
		tags: old_data?.data?.tags
	};
}
async function createCharacter(character_name_or_id, character = {}) {
	if (character_name_or_id === "current" || RawCharacter.findIndex(character_name_or_id) !== -1) return false;
	const payload = fromCharacterToPayload(character_name_or_id, character);
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	const response = await fetch("/api/characters/create", {
		method: "POST",
		headers,
		body: (0, import_src.serialize)(payload),
		cache: "no-cache"
	});
	if (!response.ok) throw new Error(`创建角色卡 '${character_name_or_id}' 失败: (${response.status}) ${await response.text()}`);
	await getCharacters();
	return true;
}
async function createOrReplaceCharacter(character_name_or_id, character = {}, options = {}) {
	if (RawCharacter.findIndex(character_name_or_id) !== -1) {
		await replaceCharacter(character_name_or_id, character, options);
		return false;
	} else {
		await createCharacter(character_name_or_id, character);
		return true;
	}
}
async function deleteCharacter$1(character_name_or_id, option = {}) {
	const character = RawCharacter.find({ name: character_name_or_id });
	if (!character) return false;
	await deleteCharacter(character.avatar, { deleteChats: option.delete_chats ?? true });
	return true;
}
async function getCharacter(character_name_or_id) {
	const index = RawCharacter.findIndex(character_name_or_id);
	if (index === -1) throw Error(`角色卡 '${character_name_or_id}' 不存在`);
	await unshallowCharacter(String(index));
	return klona(toCharacter(characters[index]));
}
async function render_character(character_name, character_id, character, is_current) {
	if (isBlob(character.avatar)) {
		const avatar_url = getThumbnailUrl("avatar", character_id);
		await fetch(avatar_url, {
			method: "GET",
			cache: "reload"
		});
		$("#add_avatar_button").replaceWith($("#add_avatar_button").val("").clone(true));
		const $mes_image = $(`.mes[ch_name=${character_name}]`).find("img");
		const $avatar_load_preview = $("#avatar_load_preview");
		const default_avatar = "img/ai4.png";
		$avatar_load_preview.attr("src", default_avatar);
		$mes_image.attr("src", default_avatar);
		await delay(1);
		$mes_image.attr("src", avatar_url);
		$avatar_load_preview.attr("src", avatar_url);
	}
	favsToHotswap();
	const message = getFirstMessage();
	const should_regenerate_message = message.mes && !chat_metadata.tainted && (chat.length === 0 || chat.length === 1 && !chat[0].is_user && !chat[0].is_system);
	if (is_current && should_regenerate_message) {
		chat.splice(0, chat.length, message);
		const message_id = chat.length - 1;
		await eventSource.emit(event_types.MESSAGE_RECEIVED, message_id, "first_message");
		await clearChat();
		await printMessages();
		await eventSource.emit(event_types.CHARACTER_MESSAGE_RENDERED, message_id, "first_message");
		await saveChatConditional();
	}
	if (is_current) await selectCharacterById(RawCharacter.findIndex(character_id));
	await printCharacters(true);
}
var renderCharacterDebounced = _.debounce(render_character, 1e3);
async function replaceCharacter(character_name_or_id, character, { render = "debounced" } = {}) {
	const index = RawCharacter.findIndex(character_name_or_id);
	if (index === -1) throw Error(`角色卡 '${character_name_or_id}' 不存在`);
	const target = characters[index];
	const payload = fromCharacterToPayload(character_name_or_id, character, target);
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	const response = await fetch("/api/characters/edit", {
		method: "POST",
		headers,
		body: (0, import_src.serialize)(payload),
		cache: "no-cache"
	});
	if (!response.ok) throw new Error(`修改角色卡 '${character_name_or_id}' 失败: (${response.status}) ${await response.text()}`);
	const store = useCharacterSettingsStore();
	const is_current = target.avatar === store.avatar;
	await getOneCharacter(target.avatar);
	if (is_current) {
		if (character.extensions?.tavern_helper !== void 0) store.forceReload();
	}
	switch (render) {
		case "debounced":
			renderCharacterDebounced(target.name, target.avatar, character, is_current);
			break;
		case "immediate": await render_character(target.name, target.avatar, character, is_current);
	}
}
async function updateCharacterWith(character_name_or_id, updater) {
	const character = await updater(await getCharacter(character_name_or_id));
	await replaceCharacter(character_name_or_id, character);
	return character;
}
// src/panel/optimize/better_message_to_load/index.ts
async function auditChatMessages() {
	if (power_user.chat_truncation > 0) {
		const mesid = $("#chat > .mes").first().attr("mesid");
		const last_showed = mesid ? Number(mesid) : void 0;
		if (last_showed === void 0 || chat.length - power_user.chat_truncation < last_showed) {
			const should_show = _.range(Math.max(0, chat.length - power_user.chat_truncation), last_showed ?? chat.length);
			for (const message_id of should_show) {
				const message = chat[message_id];
				addOneMessage(message, {
					insertBefore: last_showed,
					forceId: message_id,
					scroll: false
				});
				await eventSource.emit(message.is_user ? event_types.USER_MESSAGE_RENDERED : event_types.CHARACTER_MESSAGE_RENDERED, message_id);
			}
			scrollChatToBottom({ waitForFrame: true });
		}
	}
}
// src/function/chat_message.ts
function string_to_range(input, min, max) {
	let start, end;
	const clamp = (value) => _.clamp(value < 0 ? max + value + 1 : value, min, max);
	if (input.match(/^(-?\d+)$/)) start = end = clamp(Number(input));
	else {
		const match = input.match(/^(-?\d+)-(-?\d+)$/);
		if (!match) return null;
		[start, end] = _.sortBy([match[1], match[2]].map(Number).map(clamp));
	}
	if (isNaN(start) || isNaN(end)) return null;
	return {
		start,
		end
	};
}
function getChatMessages(range, { role = "all", hide_state = "all", include_swipes = false } = {}) {
	const range_number = string_to_range(substituteParamsExtended(range.toString()), 0, chat.length - 1);
	if (!range_number) return [];
	const { start, end } = range_number;
	const get_role = (chat_message) => {
		if (chat_message.extra?.type === system_message_types.NARRATOR) {
			if (chat_message.is_user) return "unknown";
			return "system";
		}
		if (chat_message.is_user) return "user";
		return "assistant";
	};
	const process_message = (message_id) => {
		const message = chat[message_id];
		if (!message) return null;
		const message_role = get_role(message);
		if (role !== "all" && message_role !== role) return null;
		if (hide_state !== "all" && hide_state === "hidden" !== message.is_system) return null;
		const swipe_id = message?.swipe_id ?? 0;
		let swipes = message?.swipes ?? [message.mes];
		let swipes_data = message?.variables ?? [{}];
		let swipes_info = message?.swipe_info ?? [message?.extra ?? {}];
		const swipe_length = swipes.length;
		swipes = _.range(0, swipe_length).map((i) => swipes[i] ?? "");
		swipes_data = _.range(0, swipe_length).map((i) => swipes_data[i] ?? {});
		swipes_info = _.range(0, swipe_length).map((i) => swipes_info[i] ?? {});
		const extra = swipes_info[swipe_id];
		const data = swipes_data[swipe_id];
		if (include_swipes) return {
			message_id,
			name: message.name,
			role: message_role,
			is_hidden: message.is_system,
			swipe_id,
			swipes,
			swipes_data,
			swipes_info
		};
		return {
			message_id,
			name: message.name,
			role: message_role,
			is_hidden: message.is_system,
			message: message.mes ?? "",
			data,
			extra,
			swipe_id,
			swipes,
			swipes_data
		};
	};
	return klona(_.range(start, end + 1).map((i) => process_message(i)).filter((chat_message) => chat_message !== null));
}
async function refreshMessages(refresh, affected_action) {
	if (usesManagedChatSurface) {
		if (refresh === "all") await saveChatConditional();
		else saveChatConditionalDebounced();
		if (refresh !== "none") await refreshManagedChatSurface();
		return;
	}
	if (refresh === "all") {
		await saveChatConditional();
		await reloadCurrentChat();
	} else {
		saveChatConditionalDebounced();
		if (refresh === "affected") {
			await affected_action();
			const $mes = $("chat > .mes");
			$mes.removeClass("last_mes");
			$mes.last().addClass("last_mes");
		}
	}
}
function assertManagedStructureMutationAllowed(refresh) {
	if (!usesManagedChatSurface) return;
	if (refresh === "none") throw new Error("Managed ChatSurface structure mutations require refresh=\"affected\" or refresh=\"all\"");
	if (document.querySelector("#chat > .mes .edit_textarea, #chat > .mes .reasoning_edit_textarea")) throw new Error("Managed ChatSurface structure mutations are unavailable while a message editor is open");
}
async function setChatMessages(chat_messages, { refresh = "affected" } = {}) {
	const convert_and_merge_messages = (data) => {
		return _(data).map((chat_message) => ({
			...chat_message,
			message_id: normalizeMessageId(chat_message.message_id)
		})).sortBy("message_id").groupBy("message_id").map((messages) => {
			return messages.reduce((result, current) => ({
				...result,
				...current
			}), {});
		}).value();
	};
	const is_chat_message = (chat_message) => {
		return _.has(chat_message, "message") || _.has(chat_message, "data");
	};
	const modify = async (chat_message) => {
		const data = chat[chat_message.message_id];
		if (data === void 0) return;
		if (_.isPlainObject(data?.variables)) _.set(data, "variables", _.range(0, data.swipes?.length ?? 1).map((i) => data.variables[i] ?? {}));
		if (chat_message?.name !== void 0) _.set(data, "name", chat_message.name);
		if (chat_message?.role !== void 0) {
			_.set(data, "is_user", chat_message.role === "user");
			if (chat_message.role === "system") _.set(data, "extra.type", system_message_types.NARRATOR);
			else _.unset(data, "extra.type");
		}
		if (chat_message?.is_hidden !== void 0) _.set(data, "is_system", chat_message.is_hidden);
		if (is_chat_message(chat_message)) {
			if (chat_message?.message !== void 0) {
				_.set(data, "mes", chat_message.message);
				if (data?.swipes !== void 0) _.set(data, ["swipes", data.swipe_id], chat_message.message);
			}
			if (chat_message?.data !== void 0) {
				if (data?.variables === void 0) _.set(data, "variables", _.times(data.swipes?.length ?? 1, _.constant({})));
				_.set(data, ["variables", data.swipe_id ?? 0], chat_message.data);
			}
			if (chat_message?.extra !== void 0) {
				if (data?.swipes_info === void 0) _.set(data, "swipe_info", _.times(data.swipes?.length ?? 1, _.constant({})));
				_.set(data, "extra", chat_message?.extra);
				_.set(data, ["swipe_info", data.swipe_id ?? 0], chat_message?.extra);
			}
		} else if (chat_message?.swipe_id !== void 0 || chat_message?.swipes !== void 0 || chat_message?.swipes_data !== void 0 || chat_message?.swipes_info !== void 0) {
			const max_length = _.max([
				chat_message.swipes?.length,
				chat_message.swipes_data?.length,
				chat_message.swipes_info?.length
			]) ?? data.swipes?.length ?? 1;
			_.set(chat_message, "swipe_id", _.clamp(chat_message.swipe_id ?? data.swipe_id ?? 0, 0, max_length - 1));
			_.set(chat_message, "swipes", chat_message.swipes ?? data.swipes ?? [data.mes]);
			_.set(chat_message, "swipes_data", chat_message.swipes_data ?? data.variables ?? [{}]);
			_.set(chat_message, "swipes_info", chat_message.swipes_info ?? data.swipe_info ?? [{}]);
			chat_message.swipes = _.range(0, max_length).map((i) => chat_message.swipes?.[i] ?? "");
			chat_message.swipes_data = _.range(0, max_length).map((i) => chat_message.swipes_data?.[i] ?? {});
			chat_message.swipes_info = _.range(0, max_length).map((i) => chat_message.swipes_info?.[i] ?? {});
			_.set(data, "swipes", chat_message.swipes);
			_.set(data, "variables", chat_message.swipes_data);
			_.set(data, "swipe_info", chat_message.swipes_info);
			_.set(data, "swipe_id", chat_message.swipe_id);
			_.set(data, "mes", chat_message.swipes[chat_message.swipe_id]);
			_.set(data, "extra", chat_message.swipes_info[chat_message.swipe_id]);
		}
	};
	chat_messages = convert_and_merge_messages(chat_messages);
	await Promise.all(chat_messages.map(modify));
	await refreshMessages(refresh, async () => {
		await Promise.all(chat_messages.map((message) => refreshOneMessage(message.message_id)));
	});
}
async function createChatMessages(chat_messages, { insert_at, insert_before = "end", refresh = "affected" } = {}) {
	assertManagedStructureMutationAllowed(refresh);
	insert_before = insert_at ?? insert_before;
	insert_before = insert_before === "end" ? chat.length : _.clamp(insert_before, -chat.length, chat.length);
	const is_at_end = insert_before === chat.length;
	const convert = async (chat_message) => {
		let result = _({});
		if (chat_message?.name !== void 0) result = result.set("name", chat_message.name);
		else if (chat_message.role === "system") result = result.set("name", "system");
		else if (chat_message.role === "user") result = result.set("name", name1);
		else result = result.set("name", name2);
		result = result.set("is_user", chat_message.role === "user");
		if (chat_message.role === "system") result = result.set("extra.type", system_message_types.NARRATOR);
		result = result.set("is_system", chat_message.is_hidden ?? false);
		result = result.set("mes", chat_message.message);
		if (chat_message.data) result = result.set(["variables", 0], chat_message.data);
		if (chat_message.extra) result = result.set("extra", chat_message.extra);
		return result.value();
	};
	const converted = await Promise.all(chat_messages.map(convert));
	console.info(converted);
	chat.splice(insert_before, 0, ...converted);
	if (usesManagedChatSurface) {
		await refreshMessages(refresh, async () => {});
		await Promise.all(converted.map((message, index) => eventSource.emit(message.is_user ? event_types.MESSAGE_SENT : event_types.MESSAGE_RECEIVED, insert_before + index, "extension")));
		return;
	}
	await refreshMessages(refresh, async () => {
		await Promise.all(converted.map(async (message, index) => {
			const message_id = insert_before - converted.length + index + 1;
			addOneMessage(message, is_at_end ? void 0 : {
				insertBefore: insert_before,
				forceId: message_id,
				scroll: false
			});
			await eventSource.emit(message.is_user ? event_types.MESSAGE_SENT : event_types.MESSAGE_RECEIVED, message_id, "extension");
			await eventSource.emit(message.is_user ? event_types.USER_MESSAGE_RENDERED : event_types.CHARACTER_MESSAGE_RENDERED, message_id);
		}));
		if (!is_at_end) {
			const dirty_messages = _.range(insert_before, chat.length - converted.length);
			await Promise.all(dirty_messages.map((message_id) => refreshOneMessage(message_id + converted.length, $(`#chat > .mes[mesid="${message_id}"]`).last())));
		}
	});
}
async function deleteChatMessages(message_ids, { refresh = "affected" } = {}) {
	message_ids = _(message_ids).filter(inUnnormalizedMessageRange).map((id) => normalizeMessageId(id)).sort().sortedUniq().value();
	if (message_ids.length === 0) return;
	assertManagedStructureMutationAllowed(refresh);
	_.pullAt(chat, message_ids);
	await refreshMessages(refresh, async () => {
		const min_affected = Math.max(Number($(`#chat > .mes`).first().attr("mesid")), _.min(message_ids));
		const deleted_in_affected = message_ids.filter((message_id) => message_id >= min_affected);
		const before_after = _(_.range(min_affected, chat.length + message_ids.length)).map((message_id) => [message_id, message_id - deleted_in_affected.reduce((sum, deleted_id) => sum + (deleted_id < message_id ? 1 : 0), 0)]).value();
		await Promise.all(before_after.map(([before, after]) => {
			const $mes = $(`#chat > .mes[mesid="${before}"]`);
			if (before === after) $mes.remove();
			else refreshOneMessage(after, $mes);
		}));
		await auditChatMessages();
	});
}
async function rotateChatMessages(begin, middle, end, { refresh = "affected" } = {}) {
	begin = _.clamp(normalizeMessageId(begin), 0, chat.length);
	end = _.clamp(normalizeMessageId(end), 0, chat.length);
	middle = _.clamp(normalizeMessageId(middle), begin, end);
	assertManagedStructureMutationAllowed(refresh);
	const right_part = chat.splice(middle, end - middle);
	chat.splice(begin, 0, ...right_part);
	await refreshMessages(refresh, async () => {
		await Promise.all(_.range(Math.max(Number($(`#chat > .mes`).first().attr("mesid")), _.min([
			begin,
			middle,
			end
		])), chat.length).map((message_id) => refreshOneMessage(message_id)));
	});
}
/** @deprecated 请使用 `setChatMessages` 代替 */
async function setChatMessage(field_values, message_id, { swipe_id = "current", refresh = "display_and_render_current" } = {}) {
	field_values = typeof field_values === "string" ? { message: field_values } : field_values;
	if (typeof swipe_id !== "number" && swipe_id !== "current") throw Error(`提供的 swipe_id 无效, 请提供 'current' 或序号, 你提供的是: ${swipe_id} `);
	if (![
		"none",
		"display_current",
		"display_and_render_current",
		"all"
	].includes(refresh)) throw Error(`提供的 refresh 无效, 请提供 'none', 'display_current', 'display_and_render_current' 或 'all', 你提供的是: ${refresh} `);
	const chat_message = chat.at(message_id);
	if (!chat_message) return;
	const add_swipes_if_required = () => {
		if (swipe_id === "current") return false;
		if (swipe_id == 0 || chat_message.swipes && swipe_id < chat_message.swipes.length) return true;
		if (!chat_message.swipes) {
			chat_message.swipe_id = 0;
			chat_message.swipes = [chat_message.mes];
			chat_message.variables = [{}];
		}
		for (let i = chat_message.swipes.length; i <= swipe_id; ++i) {
			chat_message.swipes.push("");
			chat_message.variables.push({});
		}
		return true;
	};
	const swipe_id_previous_index = chat_message.swipe_id ?? 0;
	const swipe_id_to_set_index = swipe_id == "current" ? swipe_id_previous_index : swipe_id;
	const swipe_id_to_use_index = refresh != "none" ? swipe_id_to_set_index : swipe_id_previous_index;
	const message = field_values.message ?? (chat_message.swipes ? chat_message.swipes[swipe_id_to_set_index] : void 0) ?? chat_message.mes;
	const update_chat_message = () => {
		const message_demacroed = substituteParamsExtended(message);
		if (field_values.data) {
			if (!chat_message.variables) chat_message.variables = [];
			chat_message.variables[swipe_id_to_set_index] = field_values.data;
		}
		if (chat_message.swipes) {
			chat_message.swipes[swipe_id_to_set_index] = message_demacroed;
			chat_message.swipe_id = swipe_id_to_use_index;
		}
		if (swipe_id_to_use_index === swipe_id_to_set_index) chat_message.mes = message_demacroed;
	};
	const update_partial_html = async (should_update_swipe) => {
		const mes_html = $(`#chat > .mes[mesid = "${message_id}"]`);
		if (!mes_html) return;
		if (should_update_swipe && chat_message.swipes) {
			mes_html.find(".swipes-counter").text(`${swipe_id_to_use_index + 1}\u200b/\u200b${chat_message.swipes.length}`);
			if (message_id === chat.length - 1) showSwipeButtons();
		}
		if (refresh != "none") {
			mes_html.find(".mes_text").empty().append(messageFormatting(message, chat_message.name, chat_message.is_system, chat_message.is_user, message_id));
			if (refresh === "display_and_render_current") await eventSource.emit(chat_message.is_user ? event_types.USER_MESSAGE_RENDERED : event_types.CHARACTER_MESSAGE_RENDERED, message_id);
		}
	};
	const should_update_swipe = add_swipes_if_required();
	update_chat_message();
	await saveChatConditional();
	if (usesManagedChatSurface) {
		if (refresh !== "none") await refreshManagedChatSurface();
		return;
	}
	if (refresh == "all") await reloadCurrentChat();
	else await update_partial_html(should_update_swipe);
}
// src/function/script.ts
function getScriptTrees(option) {
	return klona(getScriptsStoreByType(option.type).script_trees);
}
function replaceScriptTrees(script_trees, option) {
	const parsed = script_trees.map((tree) => ScriptTree$1.parse(tree));
	const store = getScriptsStoreByType(option.type);
	store.script_trees = parsed;
}
function updateScriptTreesWith(updater, option) {
	const result = updater(getScriptTrees(option));
	if (isPromise(result)) return result.then((result) => {
		replaceScriptTrees(result, option);
		return getScriptTrees(option);
	});
	replaceScriptTrees(result, option);
	return getScriptTrees(option);
}
function _getButtonEvent(button_name) {
	return getButtonId(String(_getScriptId.call(this)), button_name);
}
function _getScriptButtons() {
	const script = useScriptIframeRuntimesStore().get(_getScriptId.call(this));
	if (!script) return [];
	return klona(script.button.buttons);
}
function getAllEnabledScriptButtons() {
	return klona(useScriptIframeRuntimesStore().button_map);
}
function _replaceScriptButtons(param1, param2) {
	const script = useScriptIframeRuntimesStore().get(_getScriptId.call(this));
	if (!script) return;
	const new_buttons = typeof param1 === "string" ? param2 : param1;
	if (!_.isEqual(script.button.buttons, new_buttons)) script.button.buttons = new_buttons;
}
function _updateScriptButtonsWith(updater) {
	let result = updater(_getScriptButtons.call(this));
	if (isPromise(result)) result = result.then((result) => {
		_replaceScriptButtons.call(this, result);
		return result;
	});
	else _replaceScriptButtons.call(this, result);
	return result;
}
function _appendInexistentScriptButtons(param1, param2) {
	const new_buttons = typeof param1 === "string" ? param2 : param1;
	return _updateScriptButtonsWith.call(this, (buttons) => {
		const inexistent_buttons = new_buttons.filter((button) => !buttons.some((b) => b.name === button.name));
		return [...buttons, ...inexistent_buttons];
	});
}
function _getScriptName() {
	const script = useScriptIframeRuntimesStore().get(_getScriptId.call(this));
	if (!script) return "";
	return script.name;
}
function _getScriptInfo() {
	const script = useScriptIframeRuntimesStore().get(_getScriptId.call(this));
	if (!script) return "";
	return script.info;
}
function _replaceScriptInfo(info) {
	const script = useScriptIframeRuntimesStore().get(_getScriptId.call(this));
	if (!script) return;
	script.info = info;
}
// src/util/algorithm.ts
function getOrSet(map, key, defaulter) {
	const existing_value = map.get(key);
	if (existing_value) return existing_value;
	const default_value = defaulter();
	map.set(key, default_value);
	return default_value;
}
// src/function/event.ts
var iframe_event_listener_wrapper_map = /* @__PURE__ */ new Map();
function get_event_listener_wrapper_map() {
	return getOrSet(iframe_event_listener_wrapper_map, _getIframeName.call(this), () => /* @__PURE__ */ new Map());
}
function get_listener_wrapper_map(event_type) {
	return getOrSet(get_event_listener_wrapper_map.call(this), event_type, () => /* @__PURE__ */ new Map());
}
function register_listener_wrapper(event_type, listener, options = {}) {
	return getOrSet(get_listener_wrapper_map.call(this, event_type), listener, () => {
		const wrapper = (...args) => {
			if (!get_listener_wrapper_map.call(this, event_type)?.has(listener)) {
				eventSource.removeListener(event_type, wrapper);
				return;
			}
			if ([
				tavern_events.MESSAGE_SWIPED,
				tavern_events.MESSAGE_SENT,
				tavern_events.MESSAGE_RECEIVED,
				tavern_events.MESSAGE_EDITED,
				tavern_events.MESSAGE_UPDATED,
				tavern_events.USER_MESSAGE_RENDERED,
				tavern_events.CHARACTER_MESSAGE_RENDERED
			].some((event) => event === event_type)) {
				args[0] = parseInt(args[0]);
				if (isNaN(args[0])) return;
			}
			const result = listener(...args);
			if (options.once) _eventRemoveListener.call(this, event_type, listener);
			return result;
		};
		return wrapper;
	});
}
function make_event_on_return(event_type, listener) {
	return { stop: () => _eventRemoveListener.call(this, event_type, listener) };
}
function _eventOn(event_type, listener) {
	const wrapped = register_listener_wrapper.call(this, event_type, listener);
	eventSource.on(event_type, wrapped);
	return make_event_on_return.call(this, event_type, wrapped);
}
/** @deprecated */
function _eventOnButton(event_type, listener) {
	_eventOn.call(this, _getButtonEvent.call(this, event_type), listener);
}
function _eventMakeLast(event_type, listener) {
	const wrapped = register_listener_wrapper.call(this, event_type, listener);
	eventSource.makeLast(event_type, wrapped);
	return make_event_on_return.call(this, event_type, wrapped);
}
function _eventMakeFirst(event_type, listener) {
	const wrapped = register_listener_wrapper.call(this, event_type, listener);
	eventSource.makeFirst(event_type, wrapped);
	return make_event_on_return.call(this, event_type, wrapped);
}
function _eventOnce(event_type, listener) {
	const wrapped = register_listener_wrapper.call(this, event_type, listener, { once: true });
	eventSource.once(event_type, wrapped);
	return make_event_on_return.call(this, event_type, wrapped);
}
async function _eventEmit(event_type, ...data) {
	await eventSource.emit(event_type, ...data);
}
function _eventEmitAndWait(event_type, ...data) {
	eventSource.emitAndWait(event_type, ...data);
}
function _eventRemoveListener(event_type, listener) {
	const listener_wrapper_map = get_listener_wrapper_map.call(this, event_type);
	if (listener_wrapper_map) {
		const wrapper = listener_wrapper_map.get(listener);
		if (wrapper) {
			listener_wrapper_map.delete(listener);
			eventSource.removeListener(event_type, wrapper);
		}
	}
}
function _eventClearEvent(event_type) {
	get_listener_wrapper_map.call(this, event_type)?.forEach((_wrapper, listener) => {
		_eventRemoveListener.call(this, event_type, listener);
	});
}
function _eventClearListener(listener) {
	get_event_listener_wrapper_map.call(this).forEach((_listeners, event_type) => {
		_eventRemoveListener.call(this, event_type, listener);
	});
}
function _eventClearAll() {
	get_event_listener_wrapper_map.call(this).forEach((listeners, event_type) => {
		listeners.forEach((_wrapper, listener) => {
			_eventRemoveListener.call(this, event_type, listener);
		});
	});
	iframe_event_listener_wrapper_map.delete(_getIframeName.call(this));
}
var iframe_events = {
	MESSAGE_IFRAME_RENDER_STARTED: "message_iframe_render_started",
	MESSAGE_IFRAME_RENDER_ENDED: "message_iframe_render_ended",
	GENERATION_REQUESTED: "js_generation_requested",
	GENERATION_STARTED: "js_generation_started",
	STREAM_TOKEN_RECEIVED_FULLY: "js_stream_token_received_fully",
	STREAM_TOKEN_RECEIVED_INCREMENTALLY: "js_stream_token_received_incrementally",
	REASONING_TOKEN_RECEIVED_FULLY: "js_reasoning_token_received_fully",
	REASONING_TOKEN_RECEIVED_INCREMENTALLY: "js_reasoning_token_received_incrementally",
	GENERATION_ENDED: "js_generation_ended"
};
var tavern_events = {
	APP_READY: "app_ready",
	EXTRAS_CONNECTED: "extras_connected",
	MESSAGE_SWIPED: "message_swiped",
	MESSAGE_SENT: "message_sent",
	MESSAGE_RECEIVED: "message_received",
	MESSAGE_EDITED: "message_edited",
	MESSAGE_DELETED: "message_deleted",
	MESSAGE_UPDATED: "message_updated",
	MESSAGE_FILE_EMBEDDED: "message_file_embedded",
	MESSAGE_REASONING_EDITED: "message_reasoning_edited",
	MESSAGE_REASONING_DELETED: "message_reasoning_deleted",
	MESSAGE_SWIPE_DELETED: "message_swipe_deleted",
	MORE_MESSAGES_LOADED: "more_messages_loaded",
	IMPERSONATE_READY: "impersonate_ready",
	CHAT_CHANGED: "chat_id_changed",
	GENERATION_AFTER_COMMANDS: "GENERATION_AFTER_COMMANDS",
	GENERATION_STARTED: "generation_started",
	GENERATION_STOPPED: "generation_stopped",
	GENERATION_ENDED: "generation_ended",
	SD_PROMPT_PROCESSING: "sd_prompt_processing",
	EXTENSIONS_FIRST_LOAD: "extensions_first_load",
	EXTENSION_SETTINGS_LOADED: "extension_settings_loaded",
	SETTINGS_LOADED: "settings_loaded",
	SETTINGS_UPDATED: "settings_updated",
	MOVABLE_PANELS_RESET: "movable_panels_reset",
	SETTINGS_LOADED_BEFORE: "settings_loaded_before",
	SETTINGS_LOADED_AFTER: "settings_loaded_after",
	CHATCOMPLETION_SOURCE_CHANGED: "chatcompletion_source_changed",
	CHATCOMPLETION_MODEL_CHANGED: "chatcompletion_model_changed",
	OAI_PRESET_CHANGED_BEFORE: "oai_preset_changed_before",
	OAI_PRESET_CHANGED_AFTER: "oai_preset_changed_after",
	OAI_PRESET_EXPORT_READY: "oai_preset_export_ready",
	OAI_PRESET_IMPORT_READY: "oai_preset_import_ready",
	WORLDINFO_SETTINGS_UPDATED: "worldinfo_settings_updated",
	WORLDINFO_UPDATED: "worldinfo_updated",
	CHARACTER_EDITOR_OPENED: "character_editor_opened",
	CHARACTER_EDITED: "character_edited",
	CHARACTER_PAGE_LOADED: "character_page_loaded",
	USER_MESSAGE_RENDERED: "user_message_rendered",
	CHARACTER_MESSAGE_RENDERED: "character_message_rendered",
	FORCE_SET_BACKGROUND: "force_set_background",
	CHAT_DELETED: "chat_deleted",
	CHAT_CREATED: "chat_created",
	GENERATE_BEFORE_COMBINE_PROMPTS: "generate_before_combine_prompts",
	GENERATE_AFTER_COMBINE_PROMPTS: "generate_after_combine_prompts",
	GENERATE_AFTER_DATA: "generate_after_data",
	WORLD_INFO_ACTIVATED: "world_info_activated",
	TEXT_COMPLETION_SETTINGS_READY: "text_completion_settings_ready",
	CHAT_COMPLETION_SETTINGS_READY: "chat_completion_settings_ready",
	CHAT_COMPLETION_PROMPT_READY: "chat_completion_prompt_ready",
	CHARACTER_FIRST_MESSAGE_SELECTED: "character_first_message_selected",
	CHARACTER_DELETED: "characterDeleted",
	CHARACTER_DUPLICATED: "character_duplicated",
	CHARACTER_RENAMED: "character_renamed",
	CHARACTER_RENAMED_IN_PAST_CHAT: "character_renamed_in_past_chat",
	SMOOTH_STREAM_TOKEN_RECEIVED: "stream_token_received",
	STREAM_TOKEN_RECEIVED: "stream_token_received",
	STREAM_REASONING_DONE: "stream_reasoning_done",
	FILE_ATTACHMENT_DELETED: "file_attachment_deleted",
	WORLDINFO_FORCE_ACTIVATE: "worldinfo_force_activate",
	OPEN_CHARACTER_LIBRARY: "open_character_library",
	ONLINE_STATUS_CHANGED: "online_status_changed",
	IMAGE_SWIPED: "image_swiped",
	CONNECTION_PROFILE_LOADED: "connection_profile_loaded",
	CONNECTION_PROFILE_CREATED: "connection_profile_created",
	CONNECTION_PROFILE_DELETED: "connection_profile_deleted",
	CONNECTION_PROFILE_UPDATED: "connection_profile_updated",
	TOOL_CALLS_PERFORMED: "tool_calls_performed",
	TOOL_CALLS_RENDERED: "tool_calls_rendered",
	CHARACTER_MANAGEMENT_DROPDOWN: "charManagementDropdown",
	SECRET_WRITTEN: "secret_written",
	SECRET_DELETED: "secret_deleted",
	SECRET_ROTATED: "secret_rotated",
	SECRET_EDITED: "secret_edited",
	PRESET_CHANGED: "preset_changed",
	PRESET_DELETED: "preset_deleted",
	PRESET_RENAMED: "preset_renamed",
	PRESET_RENAMED_BEFORE: "preset_renamed_before",
	MAIN_API_CHANGED: "main_api_changed",
	WORLDINFO_ENTRIES_LOADED: "worldinfo_entries_loaded",
	WORLDINFO_SCAN_DONE: "worldinfo_scan_done",
	MEDIA_ATTACHMENT_DELETED: "media_attachment_deleted"
};
// src/function/extension.ts
var isAdmin$1 = isAdmin;
function getTavernHelperExtensionId() {
	return "JS-Slash-Runner";
}
function getExtensionType(extension_id) {
	const result = Object.keys(extensionTypes).find((result) => result.endsWith(extension_id));
	return result ? extensionTypes[result] : null;
}
async function getExtensionInstallationInfo(extension_id) {
	const type = getExtensionType(extension_id);
	if (!type) return null;
	const response = await fetch("/api/extensions/version", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			extensionName: extension_id,
			global: type === "global"
		})
	});
	return _.mapKeys(await response.json(), (_value, key) => _.snakeCase(key));
}
function isInstalledExtension(extension_id) {
	return getExtensionType(extension_id) !== null;
}
async function installExtension(url, type) {
	if (!isAdmin$1() && type === "global") return Response.json({ message: "只有管理员才能安装全局扩展" }, { status: 403 });
	return await fetch("/api/extensions/install", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			url,
			global: type === "global"
		})
	});
}
async function uninstallExtension(extension_id) {
	const type = getExtensionType(extension_id);
	if (!type) return Response.json({ message: "扩展不存在" }, { status: 404 });
	if (!isAdmin$1() && type === "global") return Response.json({ message: "只有管理员才能卸载全局扩展" }, { status: 403 });
	return await fetch("/api/extensions/delete", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			extensionName: extension_id,
			global: type === "global"
		})
	});
}
async function reinstallExtension(extension_id) {
	const type = getExtensionType(extension_id);
	if (!type) return Response.json({ message: "扩展不存在" }, { status: 404 });
	if (!isAdmin$1() && type === "global") return Response.json({ message: "只有管理员才能重新安装全局扩展" }, { status: 403 });
	const status = await getExtensionInstallationInfo(extension_id);
	if (status.is_up_to_date) return Response.json({ message: "扩展已是最新版本" }, { status: 200 });
	const response = await uninstallExtension(extension_id);
	if (!response.ok) return response;
	return installExtension(status.remote_url, type);
}
async function updateExtension(extension_id) {
	const type = getExtensionType(extension_id);
	if (!type) return Response.json({ message: "扩展不存在" }, { status: 404 });
	if (!isAdmin$1() && type === "global") return Response.json({ message: "只有管理员才能更新全局扩展" }, { status: 403 });
	return await fetch("/api/extensions/update", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			extensionName: extension_id,
			global: type === "global"
		})
	});
}
// src/function/file.ts
async function getTavernFile(path) {
	return fetch(path, { cache: "no-store" });
}
async function readResponseError(response, fallback) {
	return await response.text() || `${fallback}（HTTP ${response.status}）`;
}
async function uploadTavernFile(filename, base64) {
	const response = await fetch("/api/files/upload", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			name: filename,
			data: base64
		})
	});
	if (!response.ok) throw new Error(await readResponseError(response, "文件上传失败"));
	const payload = await response.json();
	if (!payload.path) throw new Error("文件上传响应缺少路径");
	return payload.path.replace(/^(\/)?users\/files\//, "");
}
async function deleteTavernFile(filename) {
	const response = await fetch("/api/files/delete", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({ path: `/users/files/${filename}` })
	});
	if (response.status === 404) return false;
	if (!response.ok) throw new Error(await readResponseError(response, "文件删除失败"));
	return true;
}
// src/function/generate/utils.ts
/**
* 将文件转换为base64
* @param img 文件或图片url
* @returns base64字符串
*/
async function convertFileToBase64(img) {
	const isDataUrl = typeof img === "string" && isDataURL(img);
	let processedImg;
	if (!isDataUrl) try {
		if (typeof img === "string") {
			const response = await fetch(img, {
				method: "GET",
				cache: "force-cache"
			});
			if (!response.ok) throw new Error("Failed to fetch image");
			const blob = await response.blob();
			processedImg = await getBase64Async(blob);
		} else processedImg = await getBase64Async(img);
	} catch (err) {
		return;
	}
	else processedImg = img;
	return processedImg;
}
/**
* 从响应数据中提取消息内容
* @param data 响应数据
* @returns 提取的消息字符串
*/
function extractMessageFromData(data) {
	if (typeof data === "string") return data;
	return data?.choices?.[0]?.message?.content ?? data?.choices?.[0]?.text ?? data?.text ?? data?.message?.content?.[0]?.text ?? data?.message?.tool_plan ?? "";
}
/**
* 处理对话示例格式
* @param examplesStr 对话示例字符串
* @returns 处理后的对话示例数组
*/
function parseMesExamples(examplesStr) {
	if (examplesStr.length === 0 || examplesStr === "<START>") return [];
	if (!examplesStr.startsWith("<START>")) examplesStr = "<START>\n" + examplesStr.trim();
	const blockHeading = "<START>\n";
	return examplesStr.split(/<START>/gi).slice(1).map((block) => `${blockHeading}${block.trim()}\n`);
}
/**
* 用户输入先正则处理
* @param user_input 用户输入
* @returns 处理后的用户输入
*/
function processUserInput(user_input) {
	if (user_input === "") user_input = oai_settings.send_if_empty.trim();
	return getRegexedString(user_input, regex_placement.USER_INPUT, {
		isPrompt: true,
		depth: 0
	});
}
/**
* 获取提示词角色类型
* @param role 角色数字
* @returns 角色字符串
*/
function getPromptRole(role) {
	switch (role) {
		case extension_prompt_roles.SYSTEM: return "system";
		case extension_prompt_roles.USER: return "user";
		case extension_prompt_roles.ASSISTANT: return "assistant";
		default: return "system";
	}
}
/**
* 检查提示词是否被过滤
* @param promptId 提示词ID
* @param config 配置对象
* @returns 是否被过滤
*/
function isPromptFiltered(promptId, config) {
	if (!config.overrides) return false;
	if (promptId === "with_depth_entries") return config.overrides.with_depth_entries === false;
	if (promptId === "chat_history") {
		const prompts = config.overrides.chat_history;
		return prompts !== void 0 && prompts.length === 0;
	}
	const override = config.overrides[promptId];
	return override !== void 0 && override === "";
}
/**
* 添加临时用户消息
* @param userContent 用户内容
*/
function addTemporaryUserMessage(userContent) {
	setExtensionPrompt("TEMP_USER_MESSAGE", userContent, extension_prompt_types.IN_PROMPT, 0, true, 1);
}
/**
* 移除临时用户消息
*/
function removeTemporaryUserMessage() {
	setExtensionPrompt("TEMP_USER_MESSAGE", "", extension_prompt_types.IN_PROMPT, 0, true, 1);
}
/**
* 解除生成阻塞状态
*/
function unblockGeneration() {
	activateSendButtons();
	showSwipeButtons();
	setGenerationProgress(0);
	flushEphemeralStoppingStrings();
}
/**
* 清理注入提示词
* @param prefixes 前缀数组
*/
async function clearInjectionPrompts(prefixes) {
	const prompts = getContext().extensionPrompts;
	Object.keys(prompts).filter((key) => prefixes.some((prefix) => key.startsWith(prefix))).forEach((key) => delete prompts[key]);
	saveChatConditionalDebounced();
}
/**
* 直接处理图片数组，转换为prompt格式
* @param processedUserInput 处理后的用户输入
* @param image 图片数组参数
* @returns 包含文本和图片内容的数组格式
*/
async function processImageArrayDirectly(processedUserInput, image) {
	const quality = oai_settings.inline_image_quality || "low";
	const validImageContents = (await Promise.all(image.map(async (img) => {
		try {
			const processedImg = await convertFileToBase64(img);
			if (!processedImg) {
				console.warn("[TavernHelper][Generate:图片数组处理] 图片处理失败，跳过该图片");
				return null;
			}
			return {
				type: "image_url",
				image_url: {
					url: processedImg,
					detail: quality
				}
			};
		} catch (imgError) {
			console.warn("[TavernHelper][Generate:图片数组处理] 图片处理失败，跳过该图片");
			return null;
		}
	}))).filter((content) => content !== null);
	return [{
		type: "text",
		text: processedUserInput
	}, ...validImageContents];
}
/**
* 设置图片数组处理逻辑（用于事件监听方式）
* @param processedUserInput 处理后的用户输入
* @param image 图片数组参数
* @returns 包含带标识符的用户输入和Promise解析器的对象
*/
function setupImageArrayProcessing(processedUserInput, image) {
	const imageMarker = `__IMG_ARRAY_MARKER_`;
	const userInputWithMarker = processedUserInput + imageMarker;
	let resolveImageProcessing;
	let rejectImageProcessing;
	const imageProcessingPromise = new Promise((resolve, reject) => {
		resolveImageProcessing = resolve;
		rejectImageProcessing = reject;
	});
	let timeoutId = null;
	let isHandlerRegistered = true;
	const imageArrayHandler = async (eventData) => {
		try {
			timeoutId = setTimeout(() => {
				console.warn("[TavernHelper][Generate:图片数组处理] 图片处理超时");
				rejectImageProcessing(/* @__PURE__ */ new Error("图片处理超时"));
			}, 3e4);
			for (let i = eventData.chat.length - 1; i >= 0; i--) {
				const message = eventData.chat[i];
				const contentStr = typeof message.content === "string" ? message.content : "";
				if (message.role === "user" && contentStr.includes(imageMarker)) try {
					const quality = oai_settings.inline_image_quality || "low";
					const validImageContents = (await Promise.all(image.map(async (img) => {
						try {
							const processedImg = await convertFileToBase64(img);
							if (!processedImg) {
								console.warn("[TavernHelper][Generate:图片数组处理] 图片处理失败，跳过该图片");
								return null;
							}
							return {
								type: "image_url",
								image_url: {
									url: processedImg,
									detail: quality
								}
							};
						} catch (imgError) {
							console.warn("[TavernHelper][Generate:图片数组处理] 单个图片处理失败:", imgError);
							return null;
						}
					}))).filter((content) => content !== null);
					message.content = [{
						type: "text",
						text: contentStr.replace(imageMarker, "")
					}, ...validImageContents];
					if (timeoutId) {
						clearTimeout(timeoutId);
						timeoutId = null;
					}
					resolveImageProcessing();
					return;
				} catch (error) {
					if (timeoutId) {
						clearTimeout(timeoutId);
						timeoutId = null;
					}
					console.error("[TavernHelper][Generate:图片数组处理] 处理图片时出错:", error);
					rejectImageProcessing(error);
					return;
				}
			}
			console.warn("[TavernHelper][Generate:图片数组处理] 未找到包含图片标记的用户消息");
			resolveImageProcessing();
		} catch (error) {
			console.error("[TavernHelper][Generate:图片数组处理] imageArrayHandler 异常:", error);
			rejectImageProcessing(error);
		}
	};
	eventSource.once("chat_completion_prompt_ready", imageArrayHandler);
	const cleanup = () => {
		if (timeoutId) {
			clearTimeout(timeoutId);
			timeoutId = null;
		}
		if (isHandlerRegistered) try {
			eventSource.removeListener("chat_completion_prompt_ready", imageArrayHandler);
			isHandlerRegistered = false;
		} catch (error) {
			console.warn("[TavernHelper][Generate:图片数组处理] 清理事件监听器时出错:", error);
		}
	};
	return {
		userInputWithMarker,
		imageProcessingPromise,
		resolveImageProcessing,
		rejectImageProcessing,
		cleanup
	};
}
function normalizeBaseURL(api_url) {
	api_url = api_url.trim().replace(/\/+$/, "");
	return api_url;
}
// src/function/inject.ts
function injectPrompts(prompts, { once = false } = {}) {
	prompts.forEach((prompt) => setExtensionPrompt(prompt.id ?? uuidv4(), prompt.content ?? "", prompt.position === "none" ? -1 : 1, prompt.depth ?? 0, prompt.should_scan ?? false, {
		system: 0,
		user: 1,
		assistant: 2
	}[prompt.role ?? "system"], prompt.filter ?? null));
	let deleted = false;
	const uninject = () => {
		if (deleted) return;
		uninjectPrompts(prompts.map((p) => p.id));
		deleted = true;
	};
	if (once) {
		eventSource.once(iframe_events.GENERATION_ENDED, uninject);
		eventSource.once(tavern_events.GENERATION_ENDED, uninject);
		eventSource.once(tavern_events.GENERATION_STOPPED, uninject);
	}
	$(window).on("pagehide", uninject);
	return { uninject };
}
function uninjectPrompts(ids) {
	ids.forEach((k) => {
		_.unset(extension_prompts, k);
	});
}
// src/function/generate/dataProcessor.ts
/**
* 准备并覆盖数据的核心函数
* @param config 配置参数
* @param processedUserInput 处理后的用户输入
* @returns 包含角色信息、聊天上下文和世界信息的数据对象
*/
async function prepareAndOverrideData(config, processedUserInput) {
	const getOverrideContent = (identifier) => {
		if (!config.overrides) return void 0;
		const value = config.overrides[identifier];
		if (typeof value === "boolean") return void 0;
		return value;
	};
	if (!isPromptFiltered("chat_history", config)) handleCharDepthPrompt();
	if (!isPromptFiltered("chat_history", config) && !isPromptFiltered("author_note", config)) setAuthorNotePrompt(config);
	if (!isPromptFiltered("chat_history", config) && !isPromptFiltered("persona_description", config)) setPersonaDescriptionExtensionPrompt();
	const character = characters.at(this_chid);
	const charDepthPrompt = baseChatReplace(character?.data?.extensions?.depth_prompt?.prompt?.trim(), name1, name2);
	const creatorNotes = baseChatReplace(character?.data?.creator_notes?.trim(), name1, name2);
	const { description: rawDescription, personality: rawPersonality, persona: rawPersona, scenario: rawScenario, mesExamples: rawMesExamples, system, jailbreak } = getCharacterCardFields();
	const description = isPromptFiltered("char_description", config) ? "" : getOverrideContent("char_description") ?? rawDescription;
	const personality = isPromptFiltered("char_personality", config) ? "" : getOverrideContent("char_personality") ?? rawPersonality;
	const persona = isPromptFiltered("persona_description", config) ? "" : getOverrideContent("persona_description") ?? rawPersona;
	const scenario = isPromptFiltered("scenario", config) ? "" : getOverrideContent("scenario") ?? rawScenario;
	const mesExamplesArray = parseMesExamples(isPromptFiltered("dialogue_examples", config) ? "" : getOverrideContent("dialogue_examples") ?? rawMesExamples);
	const oaiMessageExamples = setOpenAIMessageExamples(mesExamplesArray);
	const { promptBias } = getBiasStrings(processedUserInput, "normal");
	if (config.inject) await handleInjectedPrompts(config);
	let oaiMessages;
	if (config.overrides?.chat_history) oaiMessages = [...config.overrides.chat_history].reverse();
	else {
		oaiMessages = setOpenAIMessages(await processChatHistory(chat));
		if (config.max_chat_history !== void 0) oaiMessages = oaiMessages.slice(0, config.max_chat_history);
	}
	addTemporaryUserMessage(processedUserInput);
	const worldInfo = await processWorldInfo(oaiMessages, config, {
		description: rawDescription,
		personality: rawPersonality,
		persona: rawPersona,
		scenario: rawScenario,
		charDepthPrompt,
		creatorNotes
	});
	removeTemporaryUserMessage();
	if (!isPromptFiltered("dialogue_examples", config)) await processMessageExamples(mesExamplesArray, worldInfo.worldInfoExamples);
	return {
		characterInfo: {
			description,
			personality,
			persona,
			scenario,
			system,
			jailbreak
		},
		chatContext: {
			oaiMessages,
			oaiMessageExamples,
			promptBias
		},
		worldInfo
	};
}
/**
* 处理角色卡中的深度提示词
*/
function handleCharDepthPrompt() {
	const character = characters.at(this_chid);
	const depthPromptText = baseChatReplace(character?.data?.extensions?.depth_prompt?.prompt?.trim(), name1, name2) || "";
	const depthPromptDepth = character?.data?.extensions?.depth_prompt?.depth ?? 4;
	const depthPromptRole = getExtensionPromptRoleByName(character?.data?.extensions?.depth_prompt?.role ?? "system");
	setExtensionPrompt("DEPTH_PROMPT", depthPromptText, extension_prompt_types.IN_CHAT, depthPromptDepth, extension_settings.note.allowWIScan, depthPromptRole);
}
/**
* 处理作者注释
*/
function setAuthorNotePrompt(config) {
	const prompt = config?.overrides?.author_note ?? $("#extension_floating_prompt").val();
	setExtensionPrompt(NOTE_MODULE_NAME, prompt, chat_metadata[metadata_keys.position], chat_metadata[metadata_keys.depth], extension_settings.note.allowWIScan, chat_metadata[metadata_keys.role]);
}
/**
* 用户角色描述提示词设置为提示词管理器之外的选项的情况
*/
function setPersonaDescriptionExtensionPrompt() {
	const description = power_user.persona_description;
	const INJECT_TAG = "PERSONA_DESCRIPTION";
	setExtensionPrompt(INJECT_TAG, "", extension_prompt_types.IN_PROMPT, 0);
	if (!description || power_user.persona_description_position === persona_description_positions.NONE) return;
	if ([persona_description_positions.BOTTOM_AN, persona_description_positions.TOP_AN].includes(power_user.persona_description_position) && shouldWIAddPrompt) {
		const originalAN = _.get(extension_prompts, NOTE_MODULE_NAME);
		const ANWithDesc = power_user.persona_description_position === persona_description_positions.TOP_AN ? `${description}\n${originalAN}` : `${originalAN}\n${description}`;
		setExtensionPrompt(NOTE_MODULE_NAME, ANWithDesc, chat_metadata[metadata_keys.position], chat_metadata[metadata_keys.depth], extension_settings.note.allowWIScan, chat_metadata[metadata_keys.role]);
	}
	if (power_user.persona_description_position === persona_description_positions.AT_DEPTH) setExtensionPrompt(INJECT_TAG, description, extension_prompt_types.IN_CHAT, power_user.persona_description_depth, true, power_user.persona_description_role);
}
/**
* 处理注入的提示词
*/
async function handleInjectedPrompts(promptConfig) {
	if (!promptConfig || !Array.isArray(promptConfig.inject) || !promptConfig.use_preset) return;
	injectPrompts(promptConfig.inject.map((prompt) => ({
		id: `TH-CustomInjects-${uuidv4()}`,
		...prompt
	})), { once: true });
}
/**
* 处理聊天记录
*/
async function processChatHistory(chatHistory) {
	const coreChat = chatHistory.filter((x) => !x.is_system);
	return await Promise.all(coreChat.map(async (chatItem, index) => {
		const message = chatItem.mes;
		const regexType = chatItem.is_user ? regex_placement.USER_INPUT : regex_placement.AI_OUTPUT;
		const regexedMessage = getRegexedString(message, regexType, {
			isPrompt: true,
			depth: coreChat.length - index - 1
		});
		return {
			...chatItem,
			mes: regexedMessage,
			index
		};
	}));
}
/**
* 处理世界书
*/
async function processWorldInfo(oaiMessages, config, characterInfo) {
	const chatForWI = oaiMessages.filter((x) => x.role !== "system").map((x) => {
		const name = x.role === "user" ? name1 : name2;
		return world_info_include_names ? `${name}: ${x.content}` : x.content;
	}).reverse();
	const this_max_context = getMaxContextSize();
	const globalScanData = {
		personaDescription: config.overrides?.persona_description ?? characterInfo.persona,
		characterDescription: config.overrides?.char_description ?? characterInfo.description,
		characterPersonality: config.overrides?.char_personality ?? characterInfo.personality,
		characterDepthPrompt: characterInfo.charDepthPrompt,
		scenario: config.overrides?.scenario ?? characterInfo.scenario,
		creatorNotes: characterInfo.creatorNotes
	};
	const { worldInfoString, worldInfoBefore, worldInfoAfter, worldInfoExamples, worldInfoDepth } = await getWorldInfoPrompt(chatForWI, this_max_context, false, globalScanData);
	await clearInjectionPrompts(["customDepthWI"]);
	if (!isPromptFiltered("with_depth_entries", config)) processWorldInfoDepth(worldInfoDepth);
	return {
		worldInfoString,
		worldInfoBefore: isPromptFiltered("world_info_before", config) ? null : config.overrides?.world_info_before !== void 0 ? config.overrides.world_info_before : worldInfoBefore,
		worldInfoAfter: isPromptFiltered("world_info_after", config) ? null : config.overrides?.world_info_after !== void 0 ? config.overrides.world_info_after : worldInfoAfter,
		worldInfoExamples,
		worldInfoDepth: !isPromptFiltered("with_depth_entries", config) ? worldInfoDepth : []
	};
}
/**
* 处理世界信息深度部分
*/
function processWorldInfoDepth(worldInfoDepth) {
	if (Array.isArray(worldInfoDepth)) worldInfoDepth.forEach((entry) => {
		const joinedEntries = entry.entries.join("\n");
		setExtensionPrompt(`customDepthWI-${entry.depth}-${entry.role}`, joinedEntries, extension_prompt_types.IN_CHAT, entry.depth, false, entry.role);
	});
}
/**
* 处理世界书中示例前后
*/
async function processMessageExamples(mesExamplesArray, worldInfoExamples) {
	for (const example of worldInfoExamples) {
		if (!example.content.length) continue;
		const cleanedExample = parseMesExamples(baseChatReplace(example.content, name1, name2));
		if (example.position === wi_anchor_position.before) mesExamplesArray.unshift(...cleanedExample);
		else mesExamplesArray.push(...cleanedExample);
	}
	return mesExamplesArray;
}
// src/function/generate/generate.ts
var dryRun = false;
/**
* 使用预设路径处理生成请求
* @param baseData 基础数据
* @param processedUserInput 处理后的用户输入
* @param config 配置参数
* @returns 生成数据
*/
async function handlePresetPath(baseData, processedUserInput, config) {
	let originalScenario = null;
	const character = characters.at(this_chid);
	try {
		const scenarioOverride = config?.overrides?.scenario;
		if (scenarioOverride && character) {
			originalScenario = character.scenario || null;
			character.scenario = scenarioOverride;
		}
		const userMessageTemp = {
			role: "user",
			content: processedUserInput,
			image: config.image
		};
		if (config.image) {
			if (Array.isArray(config.image)) delete userMessageTemp.image;
			else userMessageTemp.image = await convertFileToBase64(config.image);
		}
		baseData.chatContext.oaiMessages.unshift(userMessageTemp);
		const messageData = {
			name2,
			charDescription: baseData.characterInfo.description,
			charPersonality: baseData.characterInfo.personality,
			Scenario: baseData.characterInfo.scenario,
			worldInfoBefore: baseData.worldInfo.worldInfoBefore,
			worldInfoAfter: baseData.worldInfo.worldInfoAfter,
			extensionPrompts: getContext().extensionPrompts,
			bias: baseData.chatContext.promptBias,
			type: "normal",
			quietPrompt: "",
			quietImage: null,
			cyclePrompt: "",
			systemPromptOverride: baseData.characterInfo.system,
			jailbreakPromptOverride: baseData.characterInfo.jailbreak,
			personaDescription: baseData.characterInfo.persona,
			messages: baseData.chatContext.oaiMessages,
			messageExamples: baseData.chatContext.oaiMessageExamples
		};
		const [prompt] = await prepareOpenAIMessages(messageData, dryRun);
		return { prompt };
	} finally {
		if (originalScenario !== null && character) character.scenario = originalScenario;
	}
}
// src/function/generate/types.ts
/**
* 角色类型（复制自@sillytavern/script以避免依赖）
*/
var extension_prompt_roles$1 = {
	SYSTEM: 0,
	USER: 1,
	ASSISTANT: 2
};
/**
* 仅在显式要求返回 reasoning，或存在非空 tool_calls 时返回普通详情对象。
* 否则保持原始字符串返回；对象只包含实际存在的元数据字段。
*/
function createGenerateResult(content, details = {}, shouldReturnReasoning = false) {
	const metadata = {
		...details.reasoning ? { reasoning: details.reasoning } : {},
		...details.reasoning_signature ? { reasoning_signature: details.reasoning_signature } : {}
	};
	const toolCalls = details.tool_calls?.length ? { tool_calls: details.tool_calls } : {};
	if (!shouldReturnReasoning && !("tool_calls" in toolCalls)) return content;
	return {
		content,
		...metadata,
		...toolCalls
	};
}
/**
* 默认内置提示词顺序
*/
var placeholder_prompt_default_order = [
	"world_info_before",
	"persona_description",
	"char_description",
	"char_personality",
	"scenario",
	"world_info_after",
	"dialogue_examples",
	"chat_history",
	"user_input"
];
extension_prompt_roles$1.SYSTEM, extension_prompt_roles$1.USER, extension_prompt_roles$1.ASSISTANT;
/**
* 默认提示词顺序
*/
var default_order = [
	"world_info_before",
	"persona_description",
	"char_description",
	"char_personality",
	"scenario",
	"world_info_after",
	"dialogue_examples",
	"chat_history",
	"user_input"
];
/**
* 角色名称行为常量
*/
var character_names_behavior = {
	NONE: -1,
	DEFAULT: 0,
	COMPLETION: 1,
	CONTENT: 2
};
// src/function/generate/generateRaw.ts
/**
* @fileoverview 原始生成路径处理模块 - 不使用预设的生成逻辑
* 包含所有不使用预设(use_preset=false)时的提示词处理和聊天完成逻辑
*/
/**
* 将系统提示词转换为集合格式
* 处理内置提示词、自定义注入和对话示例，转换为PromptCollection和MessageCollection格式
* @param baseData 包含角色信息和世界书信息的基础数据
* @param promptConfig 提示词配置参数，包含order等设置
* @returns Promise<{systemPrompts: PromptCollection, dialogue_examples: MessageCollection}> 返回系统提示词和对话示例的集合
*/
async function convertSystemPromptsToCollection(baseData, promptConfig) {
	const promptCollection = new PromptCollection();
	const examplesCollection = new MessageCollection("dialogue_examples");
	const orderArray = promptConfig.order || placeholder_prompt_default_order;
	const builtinPromptContents = {
		world_info_before: baseData.worldInfo.worldInfoBefore,
		persona_description: power_user.persona_description && power_user.persona_description_position === persona_description_positions.IN_PROMPT ? baseData.characterInfo.persona : null,
		char_description: baseData.characterInfo.description,
		char_personality: baseData.characterInfo.personality,
		scenario: baseData.characterInfo.scenario,
		world_info_after: baseData.worldInfo.worldInfoAfter
	};
	for (const [index, item] of orderArray.entries()) if (typeof item === "string") {
		const content = builtinPromptContents[item];
		if (content) promptCollection.add(new Prompt({
			identifier: item,
			role: "system",
			content,
			system_prompt: true
		}));
	} else if (typeof item === "object" && item.role && item.content) {
		const identifier = `custom_prompt_${index}`;
		promptCollection.add(new Prompt({
			identifier,
			role: item.role,
			content: item.content,
			system_prompt: item.role === "system"
		}));
	}
	if (baseData.chatContext.oaiMessageExamples.length > 0) for (const dialogue of [...baseData.chatContext.oaiMessageExamples]) {
		const dialogueIndex = baseData.chatContext.oaiMessageExamples.indexOf(dialogue);
		const chatMessages = [];
		for (let promptIndex = 0; promptIndex < dialogue.length; promptIndex++) {
			const prompt = dialogue[promptIndex];
			const role = "system";
			const content = prompt.content || "";
			const identifier = `dialogue_examples ${dialogueIndex}-${promptIndex}`;
			const chatMessage = await Message.createAsync(role, content, identifier);
			await chatMessage.setName(prompt.name);
			chatMessages.push(chatMessage);
		}
		for (const message of chatMessages) examplesCollection.add(message);
	}
	return {
		systemPrompts: promptCollection,
		dialogue_examples: examplesCollection
	};
}
/**
* 处理聊天记录并注入提示词
* 根据order配置处理聊天历史和用户输入，并注入各种深度提示词
* @param baseData 包含聊天上下文的基础数据
* @param promptConfig 提示词配置参数
* @param chatCompletion ChatCompletion对象，用于管理token预算和消息集合
* @param processedUserInput 经过处理的用户输入文本
* @param processedImageArray 可选的处理后图片数组，用于多图片支持
* @returns Promise<void> 无返回值，直接修改chatCompletion对象
*/
async function processChatHistoryAndInject(baseData, promptConfig, chatCompletion, processedUserInput, processedImageArray) {
	const orderArray = promptConfig.order || default_order;
	const chatHistoryIndex = orderArray.findIndex((item) => typeof item === "string" && item.toLowerCase() === "chat_history");
	const userInputIndex = orderArray.findIndex((item) => typeof item === "string" && item.toLowerCase() === "user_input");
	const hasUserInput = userInputIndex !== -1;
	const hasChatHistory = chatHistoryIndex !== -1;
	const isChatHistoryFiltered = isPromptFiltered("chat_history", promptConfig);
	let userMessage;
	if (processedImageArray && hasUserInput) userMessage = await Message.createAsync("user", processedImageArray, "user_input");
	else {
		userMessage = await Message.createAsync("user", processedUserInput, "user_input");
		if (promptConfig.image && hasUserInput) {
			if (!Array.isArray(promptConfig.image)) {
				const img = await convertFileToBase64(promptConfig.image);
				if (img) await userMessage.addImage(img);
			}
		}
	}
	if (isChatHistoryFiltered || !hasChatHistory) {
		const insertIndex = hasUserInput ? userInputIndex : orderArray.length;
		chatCompletion.add(new MessageCollection("user_input", userMessage), insertIndex);
		return;
	}
	const chatCollection = new MessageCollection("chat_history");
	const newChat = oai_settings.new_chat_prompt;
	const newChatMessage = await Message.createAsync("system", substituteParams(newChat), "newMainChat");
	chatCompletion.reserveBudget(newChatMessage);
	chatCollection.add(newChatMessage);
	const lastChatPrompt = baseData.chatContext.oaiMessages[baseData.chatContext.oaiMessages.length - 1];
	const emptyMessage = await Message.createAsync("user", oai_settings.send_if_empty, "emptyUserMessageReplacement");
	if (lastChatPrompt && lastChatPrompt.role === "assistant" && oai_settings.send_if_empty && chatCompletion.canAfford(emptyMessage)) chatCollection.add(emptyMessage);
	if (!hasUserInput) {
		let userPrompt;
		if (processedImageArray) userPrompt = {
			role: "user",
			content: processedImageArray,
			identifier: "user_input"
		};
		else userPrompt = {
			role: "user",
			content: processedUserInput,
			identifier: "user_input",
			image: promptConfig.image && !Array.isArray(promptConfig.image) ? await convertFileToBase64(promptConfig.image) : void 0
		};
		baseData.chatContext.oaiMessages.unshift(userPrompt);
	}
	const messages = (await populationInjectionPrompts(baseData, baseData.chatContext.oaiMessages, promptConfig.inject, promptConfig)).reverse();
	const imageInlining = isImageInliningSupported();
	const chatPool = [...messages];
	for (const chatPrompt of chatPool) {
		const prompt = new Prompt(chatPrompt);
		prompt.identifier = `chat_history-${messages.length - chatPool.indexOf(chatPrompt)}`;
		prompt.content = substituteParams(prompt.content);
		const chatMessage = await Message.fromPromptAsync(prompt);
		const promptManager = setupChatCompletionPromptManager(oai_settings);
		if (promptManager) {
			if (promptManager.serviceSettings.names_behavior === character_names_behavior.COMPLETION && prompt.name) {
				const messageName = promptManager.isValidName(prompt.name) ? prompt.name : promptManager.sanitizeName(prompt.name);
				await chatMessage.setName(messageName);
			}
		}
		if (imageInlining && chatPrompt.image) await chatMessage.addImage(chatPrompt.image);
		if (chatCompletion.canAfford(chatMessage)) chatCollection.add(chatMessage);
		else break;
	}
	chatCompletion.freeBudget(newChatMessage);
	if (hasUserInput) {
		chatCompletion.add(chatCollection, chatHistoryIndex);
		chatCompletion.add(new MessageCollection("user_input", userMessage), userInputIndex);
	} else chatCompletion.add(chatCollection, chatHistoryIndex);
}
/**
* 处理注入提示词
* 按深度注入各种提示词，包括作者注释、用户描述、世界书深度条目和自定义注入
* @param baseData 包含世界书信息的基础数据
* @param messages 原始消息数组
* @param customInjects 自定义注入提示词数组
* @param config 配置参数，用于过滤检查
* @returns Promise<RolePrompt[]> 处理后的消息数组，包含所有注入的提示词
*/
async function populationInjectionPrompts(baseData, messages, customInjects = [], config) {
	const processedMessages = [...messages];
	let totalInsertedMessages = 0;
	const injectionPrompts = [];
	const authorsNote = _.get(extension_prompts, "2_floating_prompt", {});
	if (authorsNote && authorsNote.value) injectionPrompts.push({
		role: getPromptRole(authorsNote.role),
		content: authorsNote.value,
		identifier: "authorsNote",
		injection_depth: authorsNote.depth,
		injected: true
	});
	if (power_user.persona_description && power_user.persona_description_position === persona_description_positions.AT_DEPTH) injectionPrompts.push({
		role: "system",
		content: power_user.persona_description,
		identifier: "persona_description",
		injection_depth: power_user.persona_description_depth,
		injected: true
	});
	if (!isPromptFiltered("char_depth_prompt", config)) {
		const wiDepthPrompt = baseData.worldInfo.worldInfoDepth;
		if (wiDepthPrompt) for (const entry of wiDepthPrompt) injectionPrompts.push({
			role: getPromptRole(entry.role),
			content: entry.entries.join("\n"),
			injection_depth: entry.depth,
			injected: true
		});
	}
	if (Array.isArray(customInjects)) for (const inject of customInjects) injectionPrompts.push({
		identifier: `INJECTION-${inject.role}-${inject.depth}`,
		role: inject.role,
		content: inject.content,
		injection_depth: inject.depth || 0,
		injected: true
	});
	const knownExtensionPrompts = [
		"1_memory",
		"2_floating_prompt",
		"3_vectors",
		"4_vectors_data_bank",
		"chromadb",
		"PERSONA_DESCRIPTION",
		"QUIET_PROMPT",
		"DEPTH_PROMPT"
	];
	for (const key in extension_prompts) if (Object.hasOwn(extension_prompts, key)) {
		const prompt = extension_prompts[key];
		if (knownExtensionPrompts.includes(key)) continue;
		if (!prompt.value) continue;
		if (![extension_prompt_types.BEFORE_PROMPT, extension_prompt_types.IN_PROMPT].includes(prompt.position)) continue;
		if (typeof prompt.filter === "function" && !await prompt.filter()) continue;
		injectionPrompts.push({
			identifier: key.replace(/\W/g, "_"),
			role: getPromptRole(prompt.role),
			content: prompt.value,
			injection_depth: prompt.depth || 0,
			injected: true
		});
	}
	const roleTypes = {
		system: extension_prompt_roles$1.SYSTEM,
		user: extension_prompt_roles$1.USER,
		assistant: extension_prompt_roles$1.ASSISTANT
	};
	for (let i = 0; i <= MAX_INJECTION_DEPTH; i++) {
		const depthPrompts = injectionPrompts.filter((prompt) => prompt.injection_depth === i && prompt.content);
		const roles = [
			"system",
			"user",
			"assistant"
		];
		const roleMessages = [];
		const separator = "\n";
		for (const role of roles) {
			const jointPrompt = [depthPrompts.filter((prompt) => prompt.role === role).map((x) => x.content).join(separator), await filteredGetExtensionPrompt(extension_prompt_types.IN_CHAT, i, separator, roleTypes[role], false)].filter((x) => x).map((x) => x.trim()).join(separator);
			if (jointPrompt && jointPrompt.length) roleMessages.push({
				role,
				content: jointPrompt,
				injected: true
			});
		}
		if (roleMessages.length) {
			const injectIdx = i + totalInsertedMessages;
			processedMessages.splice(injectIdx, 0, ...roleMessages);
			totalInsertedMessages += roleMessages.length;
		}
	}
	return processedMessages;
}
async function filteredGetExtensionPrompt(position = extension_prompt_types.IN_PROMPT, depth = void 0, separator = "\n", role = void 0, wrap = true) {
	const filterByFunction = async (prompt) => {
		if (typeof prompt.filter === "function" && !await prompt.filter()) return false;
		return true;
	};
	const promptPromises = Object.keys(extension_prompts).sort().filter((x) => x !== "2_floating_prompt" && !/customDepthWI-\d+-\d+/.test(x) && !/TH-CustomInjects-.+/.test(x)).map((x) => extension_prompts[x]).filter((x) => x.position == position && x.value).filter((x) => depth === void 0 || x.depth === void 0 || x.depth === depth).filter((x) => role === void 0 || x.role === void 0 || x.role === role).filter(filterByFunction);
	let values = (await Promise.all(promptPromises)).map((x) => x.value.trim()).join(separator);
	if (wrap && values.length && !values.startsWith(separator)) values = separator + values;
	if (wrap && values.length && !values.endsWith(separator)) values = values + separator;
	if (values.length) values = substituteParams(values);
	return values;
}
/**
* 处理原始生成路径（不使用预设）
* 构建ChatCompletion对象，按照指定order处理各种提示词，管理token预算
* @param baseData 包含角色信息、聊天上下文和世界书信息的基础数据
* @param config 配置参数，包含图片、覆盖设置、注入等选项
* @param processedUserInput 经过处理的用户输入文本
* @returns Promise<{prompt: any}> 包含最终prompt的生成数据对象
*/
async function handleCustomPath(baseData, config, processedUserInput) {
	const chatCompletion = new ChatCompletion();
	chatCompletion.setTokenBudget(oai_settings.openai_max_context, oai_settings.openai_max_tokens);
	chatCompletion.reserveBudget(3);
	const orderArray = config.order || default_order;
	const positionMap = orderArray.reduce((acc, item, index) => {
		if (typeof item === "string") acc[item.toLowerCase()] = index;
		else if (typeof item === "object") acc[`custom_prompt_${index}`] = index;
		return acc;
	}, {});
	const { systemPrompts, dialogue_examples } = await convertSystemPromptsToCollection(baseData, config);
	const addToChatCompletionInOrder = async (source, index) => {
		if (typeof source === "object") {
			const collection = new MessageCollection(`custom_prompt_${index}`);
			const message = await Message.createAsync(source.role, source.content, `custom_prompt_${index}`);
			collection.add(message);
			chatCompletion.add(collection, index);
		} else if (systemPrompts.has(source)) {
			const prompt = systemPrompts.get(source);
			const collection = new MessageCollection(source);
			const message = await Message.fromPromptAsync(prompt);
			collection.add(message);
			chatCompletion.add(collection, positionMap[source]);
		}
	};
	for (const [index, item] of orderArray.entries()) if (typeof item === "string") {
		if (!isPromptFiltered(item, config)) await addToChatCompletionInOrder(item, index);
	} else if (typeof item === "object" && item.role && item.content) await addToChatCompletionInOrder(item, index);
	const dialogue_examplesIndex = orderArray.findIndex((item) => typeof item === "string" && item.toLowerCase() === "dialogue_examples");
	if (dialogue_examplesIndex !== -1 && !isPromptFiltered("dialogue_examples", config)) chatCompletion.add(dialogue_examples, dialogue_examplesIndex);
	const userInputMessage = await Message.createAsync("user", processedUserInput, "user_input");
	chatCompletion.reserveBudget(userInputMessage);
	await processChatHistoryAndInject(baseData, config, chatCompletion, processedUserInput, config.processedImageArray);
	chatCompletion.freeBudget(userInputMessage);
	if (oai_settings.squash_system_messages) await chatCompletion.squashSystemMessages();
	const prompt = chatCompletion.getChat();
	eventSource.emit(event_types.CHAT_COMPLETION_PROMPT_READY, {
		chat: prompt,
		dryRun: false
	});
	return { prompt };
}
// src/function/generate/inputProcessor.ts
/**
* 处理用户输入的第一步
* 包括宏替换、正则处理等预处理操作
* @param user_input 原始用户输入
* @returns 处理后的用户输入
*/
function processInitialUserInput(user_input = "") {
	return processUserInput(substituteParams(user_input)) || "";
}
/**
* 完整的用户输入和图片处理
* 包括用户输入预处理和图片数组处理逻辑
* @param user_input 用户输入文本
* @param use_preset 是否使用预设
* @param image 图片参数，可以是单个图片(File|string)或图片数组(File|string)[]
* @returns 处理结果，包含处理后的用户输入和图片处理相关数据
*/
async function processUserInputWithImages(user_input = "", use_preset = true, image = void 0) {
	let processedUserInput = processInitialUserInput(user_input);
	let imageProcessingSetup = void 0;
	let processedImageArray = void 0;
	if (Array.isArray(image) && image.length > 0) {
		if (use_preset) {
			imageProcessingSetup = setupImageArrayProcessing(processedUserInput, image);
			processedUserInput = imageProcessingSetup.userInputWithMarker;
		} else processedImageArray = await processImageArrayDirectly(processedUserInput, image);
	}
	return {
		processedUserInput,
		imageProcessingSetup,
		processedImageArray
	};
}
var manifest_default = {
	display_name: "酒馆助手",
	loading_order: 100,
	requires: [],
	optional: [],
	js: "dist/index.js",
	css: "dist/index.css",
	hooks: { "activate": "activateTauriTavernChatSurface" },
	author: "KAKAA",
	version: "4.12.1",
	homePage: "https://github.com/N0VI028/JS-Slash-Runner",
	auto_update: true,
	minimum_client_version: "1.13.0",
	i18n: {
		"en": "i18n/en.json",
		"en-us": "i18n/en.json",
		"en-gb": "i18n/en.json",
		"en-ca": "i18n/en.json",
		"en-au": "i18n/en.json"
	}
};
// src/function/version.ts
function getTavernHelperVersion() {
	return manifest_default.version;
}
async function updateTavernHelper() {
	return updateExtension(getTavernHelperExtensionId()).then((res) => res.ok);
}
function getTavernVersion() {
	return version$1;
}
// src/function/generate/createGenerationParametersCompat.ts
/**
* getChatCompletionModel 兼容调用
* 1.14.0: getChatCompletionModel(source: string)
* 1.15.0+: getChatCompletionModel(settings: object)
*/
function getChatCompletionModelCompat(getChatCompletionModel, settings) {
	try {
		if (compare(getTavernVersion(), "1.15.0", ">=")) return getChatCompletionModel(settings);
	} catch {}
	return getChatCompletionModel(settings.chat_completion_source);
}
/**
* 动态导入 createGenerationParameters 函数
* 版本 >= 1.15.0 时从 SillyTavern 导入，否则使用 fallback 实现
*/
async function createGenerationParameters(settings, model, type, messages, options = {}) {
	const version = getTavernVersion();
	try {
		if (compare(version, "1.15.0", ">=")) {
			const { createGenerationParameters: nativeFunc } = await __vitePreload(async () => {
				const { createGenerationParameters: nativeFunc } = await import("../scripts/openai.js");
				return { createGenerationParameters: nativeFunc };
			}, []);
			const result = await nativeFunc(settings, model, type, messages, options);
			if (options.tools?.length) {
				result.generate_data.tools = options.tools;
				result.generate_data.tool_choice = options.tool_choice ?? "auto";
			}
			return result;
		}
	} catch (error) {
		console.warn("Failed to import native createGenerationParameters, using fallback:", error);
	}
	return createGenerationParametersFallback(settings, model, type, messages, options);
}
/**
* Fallback 实现（基于 SillyTavern 1.14.0 的 sendOpenAIRequest, 只复制了主流渠道）
* 用于向后兼容 < 1.15.0 版本
*/
async function createGenerationParametersFallback(settings, model, type, messages, { jsonSchema = null, tools, tool_choice } = {}) {
	const { chat_completion_sources, getChatCompletionModel } = await __vitePreload(async () => {
		const { chat_completion_sources, getChatCompletionModel } = await import("../scripts/openai.js");
		return {
			chat_completion_sources,
			getChatCompletionModel
		};
	}, []);
	const { getCustomStoppingStrings } = await __vitePreload(async () => {
		const { getCustomStoppingStrings } = await import("../scripts/power-user.js");
		return { getCustomStoppingStrings };
	}, []);
	const { name1, name2, substituteParams } = await __vitePreload(async () => {
		const { name1, name2, substituteParams } = await import("../script.js");
		return {
			name1,
			name2,
			substituteParams
		};
	}, []);
	const { getGroupNames } = await __vitePreload(async () => {
		const { getGroupNames } = await import("../scripts/group-chats.js");
		return { getGroupNames };
	}, []);
	const openai_max_stop_strings = await __vitePreload(() => import("../scripts/openai.js").then((m) => m.openai_max_stop_strings), []) ?? 4;
	if (!Array.isArray(messages)) throw new Error("messages must be an array");
	messages = messages.filter((msg) => msg && typeof msg === "object");
	const isClaude = settings.chat_completion_source === chat_completion_sources.CLAUDE;
	const isOpenRouter = settings.chat_completion_source === chat_completion_sources.OPENROUTER;
	const isGoogle = settings.chat_completion_source === chat_completion_sources.MAKERSUITE;
	const isVertexAI = settings.chat_completion_source === chat_completion_sources.VERTEXAI;
	const isOAI = settings.chat_completion_source === chat_completion_sources.OPENAI;
	const isCustom = settings.chat_completion_source === chat_completion_sources.CUSTOM;
	const isDeepSeek = settings.chat_completion_source === chat_completion_sources.DEEPSEEK;
	const isXAI = settings.chat_completion_source === chat_completion_sources.XAI;
	const isAzureOpenAI = settings.chat_completion_source === chat_completion_sources.AZURE_OPENAI;
	const isQuiet = type === "quiet";
	const isImpersonate = type === "impersonate";
	const isContinue = type === "continue";
	let currentModel = model;
	try {
		currentModel = getChatCompletionModelCompat(getChatCompletionModel, settings);
	} catch {
		currentModel = model;
	}
	const stream = settings.stream_openai && !isQuiet && !((isOAI || isAzureOpenAI) && ["o1-2024-12-17", "o1"].includes(currentModel));
	const canMultiSwipe = settings.n > 1 && !isContinue && !isImpersonate && !isQuiet && (isOAI || isAzureOpenAI || isCustom || isXAI || settings.chat_completion_source === chat_completion_sources.AIMLAPI || settings.chat_completion_source === chat_completion_sources.MOONSHOT);
	const generate_data = {
		type,
		messages,
		model,
		temperature: Number(settings.temp_openai),
		frequency_penalty: Number(settings.freq_pen_openai),
		presence_penalty: Number(settings.pres_pen_openai),
		top_p: Number(settings.top_p_openai),
		max_tokens: settings.openai_max_tokens,
		stream,
		stop: getCustomStoppingStrings(openai_max_stop_strings),
		chat_completion_source: settings.chat_completion_source,
		n: canMultiSwipe ? settings.n : void 0,
		user_name: name1,
		char_name: name2,
		group_names: getGroupNames(),
		custom_prompt_post_processing: settings.custom_prompt_post_processing
	};
	const supportedSources = [
		chat_completion_sources.CLAUDE,
		chat_completion_sources.OPENAI,
		chat_completion_sources.MISTRALAI,
		chat_completion_sources.MAKERSUITE,
		chat_completion_sources.DEEPSEEK,
		chat_completion_sources.XAI
	];
	if (settings.reverse_proxy && supportedSources.includes(settings.chat_completion_source)) {
		generate_data.reverse_proxy = settings.reverse_proxy;
		generate_data.proxy_password = settings.proxy_password;
	}
	if (isAzureOpenAI) {
		generate_data.azure_base_url = settings.azure_base_url;
		generate_data.azure_deployment_name = settings.azure_deployment_name;
		generate_data.azure_api_version = settings.azure_api_version;
	}
	if (isClaude) {
		generate_data["top_k"] = Number(settings.top_k_openai);
		generate_data["claude_use_sysprompt"] = settings.claude_use_sysprompt;
		generate_data["stop"] = getCustomStoppingStrings();
		if (!isQuiet && !(isContinue && settings.continue_prefill)) generate_data["assistant_prefill"] = isImpersonate ? substituteParams(settings.assistant_impersonation) : substituteParams(settings.assistant_prefill);
	}
	if (isOpenRouter) {
		generate_data["top_k"] = Number(settings.top_k_openai);
		generate_data["min_p"] = Number(settings.min_p_openai);
		generate_data["repetition_penalty"] = Number(settings.repetition_penalty_openai);
		generate_data["top_a"] = Number(settings.top_a_openai);
		generate_data["use_fallback"] = settings.openrouter_use_fallback;
		generate_data["provider"] = settings.openrouter_providers;
		generate_data["allow_fallbacks"] = settings.openrouter_allow_fallbacks;
		generate_data["middleout"] = settings.openrouter_middleout;
	}
	if (isGoogle || isVertexAI) {
		const stopStringsLimit = 5;
		generate_data["top_k"] = Number(settings.top_k_openai);
		generate_data["stop"] = getCustomStoppingStrings(stopStringsLimit).slice(0, stopStringsLimit).filter((x) => x.length >= 1 && x.length <= 16);
		generate_data["use_makersuite_sysprompt"] = settings.use_makersuite_sysprompt;
		if (isVertexAI) {
			generate_data["vertexai_auth_mode"] = settings.vertexai_auth_mode;
			generate_data["vertexai_region"] = settings.vertexai_region;
			generate_data["vertexai_express_project_id"] = settings.vertexai_express_project_id;
		}
	}
	if (isCustom) {
		generate_data["custom_url"] = settings.custom_url;
		generate_data["custom_include_body"] = settings.custom_include_body;
		generate_data["custom_exclude_body"] = settings.custom_exclude_body;
		generate_data["custom_include_headers"] = settings.custom_include_headers;
	}
	if (isDeepSeek) generate_data.top_p = generate_data.top_p || Number.EPSILON;
	if (isXAI) {
		if (model.includes("grok-3-mini")) {
			delete generate_data.presence_penalty;
			delete generate_data.frequency_penalty;
			delete generate_data.stop;
		}
		if (model.includes("grok-4") || model.includes("grok-code")) {
			delete generate_data.presence_penalty;
			delete generate_data.frequency_penalty;
			if (!model.includes("grok-4-fast-non-reasoning")) delete generate_data.stop;
		}
	}
	if (!Array.isArray(generate_data.stop) || !generate_data.stop.length) delete generate_data.stop;
	if (jsonSchema) generate_data.json_schema = jsonSchema;
	if (tools?.length) {
		generate_data.tools = tools;
		generate_data.tool_choice = tool_choice ?? "auto";
	}
	return {
		generate_data,
		stream,
		canMultiSwipe
	};
}
// src/function/generate/toolCallCompat.ts
var CLAUDE_INPUT_DELTA_KEY = "__input_json_delta";
var SUPPORTED_TOOL_CALL_SOURCES = [
	"openai",
	"claude",
	"openrouter",
	"makersuite",
	"vertexai",
	"azure_openai",
	"custom",
	"deepseek",
	"xai"
];
function getToolCallResponseFamily(source) {
	switch (source) {
		case "claude": return "claude";
		case "makersuite":
		case "vertexai": return "makersuite";
		case "openai":
		case "openrouter":
		case "azure_openai":
		case "custom":
		case "deepseek":
		case "xai": return "openai";
		default: return;
	}
}
function resolveToolCallSource(input) {
	if (input.customSource) return input.customSource;
	if (input.hasCustomApiUrl) return "openai";
	return input.defaultSource;
}
function isSupportedToolCallSource(source) {
	return typeof source === "string" && SUPPORTED_TOOL_CALL_SOURCES.includes(source);
}
function hasToolCallRequestOptions(input) {
	return Boolean((input.toolOptions?.tools?.length ?? 0) > 0 || input.jsonSchema);
}
function findForcedFunctionName(toolChoice) {
	if (!toolChoice || typeof toolChoice === "string") return;
	return toolChoice.function?.name;
}
function normalizeRequestToolOptionsForSource(source, toolOptions) {
	if (!toolOptions?.tools?.length) return {
		toolOptions,
		warnings: []
	};
	const warnings = [];
	const tools = toolOptions.tools;
	const toolChoice = toolOptions.tool_choice;
	const forcedFunctionName = findForcedFunctionName(toolChoice);
	if (source === "claude") {
		if (toolChoice === "required") return {
			toolOptions: {
				tools,
				tool_choice: "any"
			},
			warnings
		};
		if (forcedFunctionName) {
			const forcedTools = tools.filter((tool) => tool.function?.name === forcedFunctionName);
			if (forcedTools.length > 0) {
				warnings.push(`Claude 不兼容 OpenAI 风格的指定函数 tool_choice，已改为仅暴露目标工具 ${forcedFunctionName} 并强制 tool use。`);
				return {
					toolOptions: {
						tools: forcedTools,
						tool_choice: "any"
					},
					warnings
				};
			}
			warnings.push(`Claude 指定工具 ${forcedFunctionName} 未在 tools 中找到，已回退为 auto。`);
			return {
				toolOptions: {
					tools,
					tool_choice: "auto"
				},
				warnings
			};
		}
		return {
			toolOptions: {
				tools,
				tool_choice: toolChoice
			},
			warnings
		};
	}
	if (source === "makersuite" || source === "vertexai") {
		if (toolChoice === "none") {
			warnings.push(`${source} 当前不发送 tool_choice=none，已移除工具定义。`);
			return {
				toolOptions: void 0,
				warnings
			};
		}
		if (forcedFunctionName) {
			const forcedTools = tools.filter((tool) => tool.function?.name === forcedFunctionName);
			if (forcedTools.length > 0) {
				warnings.push(`${source} 不兼容 OpenAI 风格的指定函数 tool_choice，已改为仅暴露目标工具 ${forcedFunctionName}。`);
				return {
					toolOptions: { tools: forcedTools },
					warnings
				};
			}
			warnings.push(`${source} 指定工具 ${forcedFunctionName} 未在 tools 中找到，已忽略 tool_choice。`);
			return {
				toolOptions: { tools },
				warnings
			};
		}
		if (toolChoice === "required") warnings.push(`${source} 当前不支持严格 required tool_choice，已保留 tools 并忽略该选项。`);
		return {
			toolOptions: { tools },
			warnings
		};
	}
	return {
		toolOptions,
		warnings
	};
}
function getIgnoredToolCallWarningMessage(source) {
	return `当前源 ${source ?? "unknown"} 不支持 tools/json_schema，已忽略这些参数并继续请求`;
}
function normalizeArguments(value) {
	if (typeof value === "string") return value;
	return JSON.stringify(value ?? {});
}
function normalizeToolCall(input) {
	const thoughtSignature = input.thought_signature ?? input.thoughtSignature ?? input.signature;
	return {
		id: input.id ?? uuidv4(),
		type: "function",
		function: {
			name: input.name ?? "",
			arguments: normalizeArguments(input.arguments ?? input.input ?? input.args)
		},
		...thoughtSignature ? { thought_signature: thoughtSignature } : {}
	};
}
function mergeToolCallDelta(target, delta) {
	for (const key in delta) {
		if (!Object.prototype.hasOwnProperty.call(delta, key)) continue;
		if (key === "__proto__" || key === "constructor") continue;
		const deltaValue = delta[key];
		const targetValue = target[key];
		if (deltaValue === null || deltaValue === void 0) {
			target[key] = deltaValue;
			continue;
		}
		if (typeof deltaValue === "string") {
			target[key] = typeof targetValue === "string" ? targetValue + deltaValue : deltaValue;
			continue;
		}
		if (typeof deltaValue === "object" && !Array.isArray(deltaValue)) {
			if (typeof targetValue !== "object" || targetValue === null || Array.isArray(targetValue)) target[key] = {};
			mergeToolCallDelta(target[key], deltaValue);
			continue;
		}
		target[key] = deltaValue;
	}
}
function ensureToolCallAt(toolCalls, index) {
	if (!toolCalls[index]) toolCalls[index] = {};
	return toolCalls[index];
}
function extractToolCallsForSupportedSources(data, source) {
	if (!data || typeof data !== "object") return null;
	const family = getToolCallResponseFamily(source);
	if (family === "openai") {
		const toolCalls = data?.choices?.[0]?.message?.tool_calls;
		if (!Array.isArray(toolCalls) || toolCalls.length === 0) return null;
		const toolSignatures = {};
		if (source === "openrouter") {
			const reasoningDetails = data?.choices?.[0]?.message?.reasoning_details;
			if (Array.isArray(reasoningDetails)) {
				for (const detail of reasoningDetails) if (detail?.type === "reasoning.encrypted" && detail?.data && typeof detail?.id === "string" && /^(tool_|call_)/.test(detail.id)) toolSignatures[detail.id] = detail.data;
			}
		}
		return toolCalls.map((toolCall) => normalizeToolCall({
			id: toolCall?.id,
			name: toolCall?.function?.name,
			arguments: toolCall?.function?.arguments,
			thoughtSignature: toolCall?.thoughtSignature ?? toolSignatures[toolCall?.id ?? ""],
			thought_signature: toolCall?.thought_signature,
			signature: toolCall?.signature
		}));
	}
	if (family === "claude") {
		const blocks = data?.content ?? data?.message?.content;
		if (!Array.isArray(blocks)) return null;
		const toolUses = blocks.filter((block) => block?.type === "tool_use" && block?.name);
		if (toolUses.length === 0) return null;
		return toolUses.map((toolUse) => normalizeToolCall({
			id: toolUse?.id,
			name: toolUse?.name,
			input: toolUse?.input,
			thoughtSignature: toolUse?.thoughtSignature,
			thought_signature: toolUse?.thought_signature,
			signature: toolUse?.signature
		}));
	}
	if (family !== "makersuite") return null;
	const parts = data?.candidates?.[0]?.content?.parts ?? data?.responseContent?.parts;
	if (!Array.isArray(parts)) return null;
	const functionCalls = parts.filter((part) => part?.functionCall);
	if (functionCalls.length === 0) return null;
	return functionCalls.map((part) => normalizeToolCall({
		name: part.functionCall?.name,
		args: part.functionCall?.args,
		thoughtSignature: part?.thoughtSignature ?? part?.functionCall?.thoughtSignature,
		thought_signature: part?.thought_signature ?? part?.functionCall?.thought_signature,
		signature: part?.signature ?? part?.functionCall?.signature
	}));
}
function extractReasoningSignatureForSupportedSources(data, source) {
	if (!data || typeof data !== "object") return null;
	const family = getToolCallResponseFamily(source);
	if (source === "openrouter") {
		const reasoningDetails = data?.choices?.[0]?.message?.reasoning_details;
		if (Array.isArray(reasoningDetails)) {
			for (const detail of reasoningDetails) if (detail?.type === "reasoning.encrypted" && detail?.data) {
				const id = typeof detail?.id === "string" ? detail.id : "";
				if (!/^(tool_|call_)/.test(id)) return detail.data;
			}
		}
	}
	if (family === "claude") {
		const blocks = data?.content ?? data?.message?.content;
		if (!Array.isArray(blocks)) return null;
		for (const block of blocks) if (block?.type === "thinking" && typeof block?.signature === "string" && block.signature) return block.signature;
		return null;
	}
	if (family !== "makersuite") return null;
	const parts = data?.candidates?.[0]?.content?.parts ?? data?.responseContent?.parts;
	if (!Array.isArray(parts)) return null;
	for (const part of parts) if (typeof part?.text === "string" && typeof part?.thoughtSignature === "string" && part.thoughtSignature) return part.thoughtSignature;
	return null;
}
function accumulateToolCallDeltasForSupportedSources(toolCalls, parsed, source) {
	const family = getToolCallResponseFamily(source);
	if (family === "openai") {
		const choices = parsed?.choices;
		if (!Array.isArray(choices)) return;
		for (const choice of choices) {
			const deltas = choice?.delta?.tool_calls;
			if (!Array.isArray(deltas)) continue;
			for (const delta of deltas) mergeToolCallDelta(ensureToolCallAt(toolCalls, typeof delta?.index === "number" ? delta.index : 0), delta);
		}
		return;
	}
	if (family === "claude") {
		if (parsed?.content_block?.type === "tool_use") {
			mergeToolCallDelta(ensureToolCallAt(toolCalls, typeof parsed?.index === "number" ? parsed.index : 0), parsed.content_block);
			return;
		}
		if (parsed?.delta?.type === "input_json_delta") {
			const target = ensureToolCallAt(toolCalls, typeof parsed?.index === "number" ? parsed.index : 0);
			target[CLAUDE_INPUT_DELTA_KEY] = (target[CLAUDE_INPUT_DELTA_KEY] ?? "") + (parsed?.delta?.partial_json ?? "");
			return;
		}
		if (parsed?.type === "content_block_stop") {
			const target = toolCalls[typeof parsed?.index === "number" ? parsed.index : 0];
			const deltaText = target?.[CLAUDE_INPUT_DELTA_KEY];
			if (!target || !deltaText) return;
			try {
				delete target[CLAUDE_INPUT_DELTA_KEY];
				mergeToolCallDelta(target, { input: JSON.parse(deltaText) });
			} catch {}
		}
		return;
	}
	if (family !== "makersuite") return;
	const candidates = parsed?.candidates;
	if (!Array.isArray(candidates)) return;
	for (const candidate of candidates) {
		const parts = candidate?.content?.parts;
		if (!Array.isArray(parts)) continue;
		for (let index = 0; index < parts.length; index++) {
			const part = parts[index];
			if (!part?.functionCall) continue;
			const target = ensureToolCallAt(toolCalls, index);
			mergeToolCallDelta(target, part.functionCall);
			if (typeof part?.thoughtSignature === "string" && part.thoughtSignature) target.thoughtSignature = part.thoughtSignature;
			if (typeof part?.thought_signature === "string" && part.thought_signature) target.thought_signature = part.thought_signature;
			if (typeof part?.signature === "string" && part.signature) target.signature = part.signature;
		}
	}
}
function normalizeAccumulatedToolCalls(toolCalls) {
	return toolCalls.filter(Boolean).map((toolCall) => normalizeToolCall({
		id: toolCall?.id,
		name: toolCall?.function?.name ?? toolCall?.name,
		arguments: toolCall?.function?.arguments,
		input: toolCall?.input,
		args: toolCall?.args,
		thoughtSignature: toolCall?.thoughtSignature,
		thought_signature: toolCall?.thought_signature,
		signature: toolCall?.signature
	}));
}
// src/function/generate/responseGenerator.ts
/**
* 用 YAML 形式覆盖自定义请求头中的 Authorization，保留其他已有头
* @param customIncludeHeaders 自定义请求头 YAML 字符串
* @param key 要覆盖写入的 API Key
* @returns 更新后的 YAML 字符串
*/
function overrideCustomAuthorizationHeader(customIncludeHeaders, key) {
	const parsed = typeof customIncludeHeaders === "string" && customIncludeHeaders.trim() ? browser_default.parse(customIncludeHeaders) : void 0;
	const headers = parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
	headers.Authorization = `Bearer ${key}`;
	return browser_default.stringify(headers).trimEnd();
}
var StreamingProcessor = class {
	generator;
	stoppingStrings;
	result;
	reasoning;
	toolCalls;
	reasoningSignature;
	toolSignatures;
	isStopped;
	isFinished;
	abortController;
	messageBuffer;
	lastEmittedReasoningLength = 0;
	generationId;
	constructor(generationId, abortController) {
		this.result = "";
		this.reasoning = "";
		this.toolCalls = [];
		this.reasoningSignature = "";
		this.toolSignatures = {};
		this.messageBuffer = "";
		this.isStopped = false;
		this.isFinished = false;
		this.generator = this.nullStreamingGeneration;
		this.abortController = abortController;
		this.generationId = generationId;
	}
	/**
	* 处理流式增量并派发事件
	* @param data 流式数据，包含文本、结束标识与思维链内容
	*/
	onProgressStreaming(data) {
		const newText = data.text.slice(this.messageBuffer.length);
		this.messageBuffer = data.text;
		let processedText = cleanUpMessage(newText, false, false, !data.isFinal, this.stoppingStrings);
		for (const char of [
			"*",
			"\"",
			"```"
		]) if (!data.isFinal && isOdd(countOccurrences(processedText, char))) {
			const separator = char.length > 1 ? "\n" : "";
			processedText = processedText.trimEnd() + separator + char;
		}
		eventSource.emit("js_stream_token_received_fully", data.text, this.generationId);
		eventSource.emit("js_stream_token_received_incrementally", processedText, this.generationId);
		eventSource.emit("js_reasoning_token_received_fully", data.reasoning, this.generationId);
		eventSource.emit("js_reasoning_token_received_incrementally", data.reasoning.slice(this.lastEmittedReasoningLength), this.generationId);
		this.lastEmittedReasoningLength = data.reasoning.length;
		if (data.isFinal) {
			const message = cleanUpMessage(data.text, false, false, false, this.stoppingStrings);
			const before_end_payload = { message };
			if (data.reasoning) before_end_payload.reasoning = data.reasoning;
			eventSource.emit("js_generation_before_end", before_end_payload, this.generationId);
			eventSource.emit("js_generation_ended", message, this.generationId, data.reasoning || void 0);
			data.text = message;
		}
	}
	onErrorStreaming() {
		if (this.abortController) this.abortController.abort();
		this.isStopped = true;
		saveChatConditionalDebounced();
	}
	async *nullStreamingGeneration() {
		throw Error("Generation function for streaming is not hooked up");
	}
	async generate() {
		try {
			const sw = new Stopwatch(1e3 / power_user.streaming_fps);
			for await (const { text, toolCalls, state } of this.generator()) {
				if (this.isStopped) {
					this.messageBuffer = "";
					return this.result;
				}
				this.result = text;
				if (toolCalls) this.toolCalls = toolCalls;
				if (state) {
					if (typeof state.reasoning === "string") this.reasoning = state.reasoning;
					if (typeof state.signature === "string" && state.signature) this.reasoningSignature = state.signature;
					if (state.toolSignatures && typeof state.toolSignatures === "object") Object.assign(this.toolSignatures, state.toolSignatures);
				}
				await sw.tick(() => this.onProgressStreaming({
					text: this.result,
					isFinal: false,
					reasoning: this.reasoning
				}));
			}
			if (!this.isStopped) this.onProgressStreaming({
				text: this.result,
				isFinal: true,
				reasoning: this.reasoning
			});
			else this.messageBuffer = "";
		} catch (err) {
			if (!this.isFinished) {
				this.onErrorStreaming();
				throw Error(`Generate method error: ${err}`);
			}
			this.messageBuffer = "";
			return this.result;
		}
		this.isFinished = true;
		return this.result;
	}
};
function resolveProxyPreset(customApi) {
	if (!customApi.proxy_preset) return customApi;
	const preset = proxies.find((p) => p.name === customApi.proxy_preset?.trim());
	if (!preset) {
		console.warn(`代理预设 '${customApi.proxy_preset}' 未找到，将回退到 ${customApi.apiurl ? "custom_api.apiurl" : "当前 ST 源"}`);
		return customApi;
	}
	return {
		...customApi,
		apiurl: preset.url,
		key: preset.password ?? ""
	};
}
function applyCustomApiOverrides(generateData, customApi) {
	if (customApi.apiurl) {
		const normalizedApiUrl = normalizeBaseURL(customApi.apiurl);
		generateData.reverse_proxy = normalizedApiUrl;
		generateData.proxy_password = customApi.key || "";
		if (generateData.chat_completion_source === "custom") {
			generateData.custom_url = normalizedApiUrl;
			if (customApi.key) generateData.custom_include_headers = overrideCustomAuthorizationHeader(generateData.custom_include_headers, customApi.key);
		}
	}
	if (customApi.model) generateData.model = customApi.model;
	const setParam = (param, default_value = null) => {
		const input = customApi[param] ?? "same_as_preset";
		if (input === default_value || input === "unset") delete generateData[param];
		else if (input !== "same_as_preset") generateData[param] = input;
	};
	setParam("max_tokens");
	setParam("temperature", 1);
	setParam("frequency_penalty", 0);
	setParam("presence_penalty", 0);
	setParam("top_p", 1);
	setParam("top_k", 0);
	if (customApi.source === "custom") {
		if (customApi.custom_include_body) generateData.custom_include_body = browser_default.stringify(customApi.custom_include_body);
		if (customApi.custom_exclude_body) generateData.custom_exclude_body = browser_default.stringify(customApi.custom_exclude_body);
		if (customApi.custom_include_headers) generateData.custom_include_headers = browser_default.stringify(customApi.custom_include_headers);
	}
}
function resolveEffectiveToolCallOptions(customApi, toolOptions, jsonSchema) {
	const source = resolveToolCallSource({
		customSource: customApi?.source,
		hasCustomApiUrl: Boolean(customApi?.apiurl),
		defaultSource: oai_settings.chat_completion_source
	});
	const supportedSource = isSupportedToolCallSource(source) ? source : void 0;
	const hasToolCallOptions = hasToolCallRequestOptions({
		toolOptions,
		jsonSchema
	});
	if (!supportedSource && hasToolCallOptions) toastr.warning(getIgnoredToolCallWarningMessage(source), "Tool Calling", { preventDuplicates: true });
	const normalizedToolOptions = supportedSource ? normalizeRequestToolOptionsForSource(supportedSource, toolOptions) : {
		toolOptions: void 0,
		warnings: []
	};
	for (const warning of normalizedToolOptions.warnings) toastr.warning(warning, "Tool Calling", { preventDuplicates: true });
	return {
		source,
		supportedSource,
		effectiveToolOptions: supportedSource ? normalizedToolOptions.toolOptions : void 0,
		effectiveJsonSchema: supportedSource ? jsonSchema : void 0
	};
}
async function* sendChatCompletionRequestStreaming(messages, signal, customApi, toolOptions, jsonSchema) {
	const source = customApi ? resolveToolCallSource({
		customSource: customApi.source,
		hasCustomApiUrl: Boolean(customApi.apiurl),
		defaultSource: oai_settings.chat_completion_source
	}) ?? "openai" : oai_settings.chat_completion_source;
	const toolCallSource = isSupportedToolCallSource(source) ? source : void 0;
	const settings = {
		...oai_settings,
		chat_completion_source: source,
		stream_openai: true
	};
	const { generate_data, canMultiSwipe } = await createGenerationParameters(settings, getChatCompletionModelCompat(getChatCompletionModel, settings), "normal", messages, {
		tools: toolOptions?.tools,
		tool_choice: toolOptions?.tool_choice,
		jsonSchema
	});
	if (customApi) applyCustomApiOverrides(generate_data, customApi);
	generate_data.include_reasoning = true;
	await eventSource.emit(event_types.CHAT_COMPLETION_SETTINGS_READY, generate_data);
	const response = await fetch("/api/backends/chat-completions/generate", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify(generate_data),
		signal
	});
	if (!response.ok) {
		const responseText = await response.text();
		tryParseStreamingError(response, responseText);
		throw new Error(`HTTP ${response.status}: ${responseText}`);
	}
	if (!response.body) throw new Error("Response body is null");
	const eventStream = getEventSourceStream();
	response.body.pipeThrough(eventStream);
	const reader = eventStream.readable.getReader();
	let text = "";
	const toolCalls = [];
	const state = {
		reasoning: "",
		images: [],
		signature: "",
		toolSignatures: {}
	};
	try {
		while (true) {
			const { done, value } = await reader.read();
			if (done) break;
			const rawData = value.data;
			if (rawData === "[DONE]") break;
			tryParseStreamingError(response, rawData);
			let parsed;
			try {
				parsed = JSON.parse(rawData);
			} catch {
				continue;
			}
			if (canMultiSwipe && Array.isArray(parsed?.choices) && parsed?.choices[0]?.index > 0) continue;
			const chunk = getStreamingReply(parsed, state, {
				chatCompletionSource: source,
				overrideShowThoughts: true
			});
			if (chunk) text += chunk;
			if (toolCallSource) accumulateToolCallDeltasForSupportedSources(toolCalls, parsed, toolCallSource);
			yield {
				text,
				toolCalls,
				state
			};
		}
	} finally {
		reader.releaseLock();
	}
}
async function sendCustomApiRequestNonStreaming(messages, signal, customApi, toolOptions, jsonSchema) {
	const source = resolveToolCallSource({
		customSource: customApi.source,
		hasCustomApiUrl: Boolean(customApi.apiurl),
		defaultSource: oai_settings.chat_completion_source
	}) ?? "openai";
	const settings = {
		...oai_settings,
		chat_completion_source: source,
		stream_openai: false
	};
	const { generate_data } = await createGenerationParameters(settings, getChatCompletionModelCompat(getChatCompletionModel, settings), "normal", messages, {
		tools: toolOptions?.tools,
		tool_choice: toolOptions?.tool_choice,
		jsonSchema
	});
	applyCustomApiOverrides(generate_data, customApi);
	generate_data.include_reasoning = true;
	await eventSource.emit(event_types.CHAT_COMPLETION_SETTINGS_READY, generate_data);
	const response = await fetch("/api/backends/chat-completions/generate", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify(generate_data),
		signal
	});
	if (!response.ok) {
		const responseText = await response.text();
		tryParseStreamingError(response, responseText);
		throw new Error(`HTTP ${response.status}: ${responseText}`);
	}
	const data = await response.json();
	if (data.error) throw new Error(data.error.message || "API error");
	return data;
}
async function handleResponse(response, generationId, hasTools, reasoningSource, toolCallSource, shouldReturnReasoning = false) {
	if (!response) throw Error("未得到响应");
	if (response.error) {
		if (response?.response) toastr.error(response.response, t`API 错误`, { preventDuplicates: true });
		throw Error(response?.response);
	}
	const content = extractMessageFromData(response);
	const reasoning = extractReasoningFromData(response, {
		mainApi: "openai",
		chatCompletionSource: reasoningSource,
		ignoreShowThoughts: true
	});
	const reasoningSignature = toolCallSource ? extractReasoningSignatureForSupportedSources(response, toolCallSource) : null;
	const toolCalls = hasTools && toolCallSource ? extractToolCallsForSupportedSources(response, toolCallSource) : null;
	const before_end_payload = { message: content };
	if (reasoning) before_end_payload.reasoning = reasoning;
	eventSource.emit("js_generation_before_end", before_end_payload, generationId);
	eventSource.emit("js_generation_ended", content, generationId, reasoning || void 0);
	return createGenerateResult(content, {
		...reasoning ? { reasoning } : {},
		...reasoningSignature ? { reasoning_signature: reasoningSignature } : {},
		...toolCalls ? { tool_calls: toolCalls } : {}
	}, shouldReturnReasoning);
}
/**
* 把流式生成器的累积结果包装成对外返回值（custom 与 preset 两条流式路径共用）
*/
function wrapStreamingResult(streamingProcessor, hasTools, shouldReturnReasoning) {
	const toolCalls = hasTools && streamingProcessor.toolCalls.length > 0 ? normalizeAccumulatedToolCalls(Array.isArray(streamingProcessor.toolCalls[0]) ? streamingProcessor.toolCalls[0] : streamingProcessor.toolCalls).map((toolCall) => ({
		...toolCall,
		...toolCall.thought_signature ? {} : { thought_signature: streamingProcessor.toolSignatures[toolCall.id] }
	})) : void 0;
	return createGenerateResult(streamingProcessor.result, {
		...streamingProcessor.reasoning ? { reasoning: streamingProcessor.reasoning } : {},
		...streamingProcessor.reasoningSignature ? { reasoning_signature: streamingProcessor.reasoningSignature } : {},
		...toolCalls ? { tool_calls: toolCalls } : {}
	}, shouldReturnReasoning);
}
async function generateResponse(generateData, useStream = false, generationId = void 0, imageProcessingSetup = void 0, abortController, customApi, toolOptions, jsonSchema, shouldReturnReasoning = false) {
	let result = "";
	const { source, supportedSource, effectiveToolOptions, effectiveJsonSchema } = resolveEffectiveToolCallOptions(customApi, toolOptions, jsonSchema);
	const hasTools = !!effectiveToolOptions?.tools?.length;
	try {
		if (imageProcessingSetup) try {
			await imageProcessingSetup.imageProcessingPromise;
		} catch (imageError) {
			throw new Error(`图片处理失败: ${imageError?.message || "未知错误"}`);
		}
		if (generationId === void 0 || generationId === "") generationId = uuidv4();
		eventSource.emit("js_generation_started", generationId);
		if (customApi) {
			customApi = resolveProxyPreset(customApi);
			const validCustomApi = customApi;
			if (useStream) {
				const streamingProcessor = new StreamingProcessor(generationId, abortController);
				streamingProcessor.generator = () => sendChatCompletionRequestStreaming(generateData.prompt, abortController.signal, validCustomApi, effectiveToolOptions, effectiveJsonSchema);
				await streamingProcessor.generate();
				result = wrapStreamingResult(streamingProcessor, hasTools, shouldReturnReasoning);
			} else result = await handleResponse(await sendCustomApiRequestNonStreaming(generateData.prompt, abortController.signal, validCustomApi, effectiveToolOptions, effectiveJsonSchema), generationId, hasTools, source, supportedSource, shouldReturnReasoning);
		} else {
			const optionsInjector = (data) => {
				data.include_reasoning = true;
				if (hasTools) {
					data.tools = effectiveToolOptions.tools;
					data.tool_choice = effectiveToolOptions.tool_choice ?? "auto";
				}
				if (effectiveJsonSchema) data.json_schema = effectiveJsonSchema;
			};
			eventSource.once(event_types.CHAT_COMPLETION_SETTINGS_READY, optionsInjector);
			try {
				if (useStream) {
					const streamingProcessor = new StreamingProcessor(generationId, abortController);
					streamingProcessor.generator = () => sendChatCompletionRequestStreaming(generateData.prompt, abortController.signal, void 0, effectiveToolOptions, effectiveJsonSchema);
					await streamingProcessor.generate();
					result = wrapStreamingResult(streamingProcessor, hasTools, shouldReturnReasoning);
				} else {
					oai_settings.stream_openai = false;
					result = await handleResponse(await sendOpenAIRequest("normal", generateData.prompt, abortController.signal), generationId, hasTools, source, supportedSource, shouldReturnReasoning);
				}
			} finally {
				eventSource.removeListener(event_types.CHAT_COMPLETION_SETTINGS_READY, optionsInjector);
				oai_settings.stream_openai = $("#stream_toggle").is(":checked");
			}
		}
	} catch (error) {
		if (imageProcessingSetup) imageProcessingSetup.rejectImageProcessing(error);
		throw error;
	} finally {
		await clearInjectionPrompts(["INJECTION"]);
	}
	return result;
}
// src/function/preset.ts
function isPresetNormalPrompt(prompt) {
	return !isPresetSystemPrompt(prompt) && !isPresetPlaceholderPrompt(prompt);
}
function isPresetSystemPrompt(prompt) {
	return [
		"main",
		"nsfw",
		"jailbreak",
		"enhanceDefinitions"
	].includes(prompt.id);
}
function isPresetPlaceholderPrompt(prompt) {
	return [
		"worldInfoBefore",
		"personaDescription",
		"charDescription",
		"charPersonality",
		"scenario",
		"worldInfoAfter",
		"dialogueExamples",
		"chatHistory"
	].includes(prompt.id);
}
var default_preset = {
	settings: {
		max_context: 2e6,
		max_completion_tokens: 300,
		reply_count: 1,
		should_stream: false,
		temperature: 1,
		frequency_penalty: 0,
		presence_penalty: 0,
		repetition_penalty: 1,
		top_p: 1,
		min_p: 0,
		top_k: 0,
		top_a: 0,
		seed: -1,
		squash_system_messages: false,
		reasoning_effort: "auto",
		request_thoughts: false,
		request_images: false,
		enable_function_calling: false,
		enable_web_search: false,
		allow_sending_images: "disabled",
		allow_sending_videos: false,
		character_name_prefix: "none",
		wrap_user_messages_in_quotes: false
	},
	prompts: [
		{
			id: "worldInfoBefore",
			name: "World Info (before) - 角色定义之前",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "personaDescription",
			name: "Persona Description - 玩家描述",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "charDescription",
			name: "Char Description - 角色描述",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "charPersonality",
			name: "Char Personality - 角色性格",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "scenario",
			name: "Scenario - 情景",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "worldInfoAfter",
			name: "World Info (after) - 角色定义之后",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "dialogueExamples",
			name: "Chat Examples - 对话示例",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		},
		{
			id: "chatHistory",
			name: "Chat History - 聊天记录",
			enabled: true,
			position: { type: "relative" },
			role: "system"
		}
	],
	prompts_unused: [],
	extensions: { tavern_helper: {
		scripts: [],
		variables: {}
	} }
};
var in_use_map = {
	temp_openai: "temperature",
	freq_pen_openai: "frequency_penalty",
	pres_pen_openai: "presence_penalty",
	top_p_openai: "top_p",
	repetition_penalty_openai: "repetition_penalty",
	min_p_openai: "min_p",
	top_k_openai: "top_k",
	top_a_openai: "top_a"
};
function toPresetPrompt(prompt, prompt_order) {
	const is_normal_prompt = prompt.system_prompt === false && (prompt.marker === void 0 || prompt.marker === false);
	const is_system_prompt = prompt.system_prompt === true && (prompt.marker === void 0 || prompt.marker === false);
	const is_placeholder_prompt = prompt.marker === true;
	let result = _({}).set("id", prompt.identifier ?? uuidv4()).set("name", prompt.name ?? "unnamed").set("enabled", prompt_order.find((order) => order.identifier === prompt.identifier)?.enabled ?? prompt.enabled ?? true);
	if (is_normal_prompt || is_placeholder_prompt) {
		result = result.set("position.type", {
			0: "relative",
			1: "in_chat"
		}[prompt.injection_position ?? 0]);
		if (prompt.injection_position === 1) {
			result = result.set("position.depth", prompt.injection_depth ?? 4);
			result = result.set("position.order", prompt.injection_order ?? 100);
		}
	}
	result = result.set("role", prompt.role ?? "system");
	if (is_normal_prompt || is_system_prompt) result = result.set("content", prompt.content ?? "");
	if (prompt.extra) result = result.set("extra", prompt.extra);
	return result.value();
}
function fromPresetPrompt(prompt) {
	const is_normal_prompt = isPresetNormalPrompt(prompt);
	const is_system_prompt = isPresetSystemPrompt(prompt);
	const is_placeholder_prompt = isPresetPlaceholderPrompt(prompt);
	let result = _({}).set("identifier", prompt.id).set("name", prompt.name).set("enabled", prompt.enabled);
	if ((is_normal_prompt || is_placeholder_prompt) && !["dialogueExamples", "chatHistory"].includes(prompt.id)) result = result.set("injection_position", (prompt.position?.type ?? "relative") === "relative" ? 0 : 1).set("injection_depth", prompt.position?.depth ?? 4).set("injection_order", prompt.position?.order ?? 100);
	result = result.set("role", prompt.role);
	if (is_normal_prompt || is_system_prompt) result = result.set("content", prompt.content);
	result = result.set("system_prompt", is_system_prompt || is_placeholder_prompt).set("marker", is_placeholder_prompt);
	if (prompt.extra) result = result.set("extra", prompt.extra);
	result = result.set("forbid_overrides", false);
	return result.value();
}
function toPreset(preset, { in_use }) {
	const prompt_order = preset.prompt_order.find((order) => order.character_id === 100001)?.order ?? [];
	const prompts_all = preset.prompts.map((prompt) => toPresetPrompt(prompt, prompt_order));
	const prompt_order_identifiers = prompt_order.map((order) => order.identifier);
	const [prompts_used, prompts_unused] = _.partition(prompts_all, (prompt) => prompt_order_identifiers.includes(prompt.id));
	const prompts = prompt_order_identifiers.map((identifier) => prompts_used.find((prompt) => prompt.id === identifier));
	const extensions = klona(preset.extensions);
	_.set(extensions, "regex_scripts", (extensions?.regex_scripts ?? []).map(to_tavern_regex));
	return {
		settings: {
			max_context: Number(preset.openai_max_context),
			max_completion_tokens: Number(preset.openai_max_tokens),
			reply_count: Number(preset.n),
			should_stream: Boolean(preset.stream_openai),
			temperature: Number(in_use ? preset.temp_openai : preset.temperature),
			frequency_penalty: Number(in_use ? preset.freq_pen_openai : preset.frequency_penalty),
			presence_penalty: Number(in_use ? preset.pres_pen_openai : preset.presence_penalty),
			top_p: Number(in_use ? preset.top_p_openai : preset.top_p),
			repetition_penalty: Number(in_use ? preset.repetition_penalty_openai : preset.repetition_penalty),
			min_p: Number(in_use ? preset.min_p_openai : preset.min_p),
			top_k: Number(in_use ? preset.top_k_openai : preset.top_k),
			top_a: Number(in_use ? preset.top_a_openai : preset.top_a),
			seed: Number(preset.seed),
			squash_system_messages: Boolean(preset.squash_system_messages),
			reasoning_effort: String(preset.reasoning_effort),
			request_thoughts: Boolean(preset.show_thoughts),
			request_images: Boolean(preset.request_images),
			enable_function_calling: Boolean(preset.function_calling),
			enable_web_search: Boolean(preset.enable_web_search),
			allow_sending_images: Boolean(preset.image_inlining) === false ? "disabled" : String(preset.inline_image_quality),
			allow_sending_videos: Boolean(preset.video_inlining),
			character_name_prefix: {
				[-1]: "none",
				[0]: "default",
				[2]: "content",
				[1]: "completion"
			}[Number(preset.names_behavior)],
			wrap_user_messages_in_quotes: Boolean(preset.wrap_in_quotes)
		},
		prompts,
		prompts_unused,
		extensions
	};
}
function fromPreset(preset) {
	const id_set = /* @__PURE__ */ new Set();
	const handle_id_collision = (id, is_normal_prompt) => {
		if (!id_set.has(id)) {
			id_set.add(id);
			return id;
		}
		if (!is_normal_prompt) throw Error(`修改的预设中存在重复的系统/占位提示词 '${id}'`);
		const new_id = uuidv4();
		id_set.add(new_id);
		return new_id;
	};
	const make_uncollision_prompts = (prompts) => {
		return prompts.map((prompt) => {
			const new_id = handle_id_collision(prompt.id, isPresetNormalPrompt(prompt));
			return {
				...prompt,
				id: new_id
			};
		});
	};
	preset.prompts = make_uncollision_prompts(preset.prompts);
	preset.prompts_unused = make_uncollision_prompts(preset.prompts_unused);
	const prompt_used = preset.prompts.map((prompt) => fromPresetPrompt(prompt));
	const prompt_unused = preset.prompts_unused.map((prompt) => fromPresetPrompt(prompt));
	const extensions = klona(preset.extensions);
	if (_.has(extensions, "regex_scripts[0].source")) extensions.regex_scripts = extensions.regex_scripts.map(from_tavern_regex);
	return {
		max_context_unlocked: true,
		openai_max_context: preset.settings.max_context,
		openai_max_tokens: preset.settings.max_completion_tokens,
		n: preset.settings.reply_count,
		stream_openai: preset.settings.should_stream,
		temp_openai: preset.settings.temperature,
		freq_pen_openai: preset.settings.frequency_penalty,
		pres_pen_openai: preset.settings.presence_penalty,
		top_p_openai: preset.settings.top_p,
		repetition_penalty_openai: preset.settings.repetition_penalty,
		min_p_openai: preset.settings.min_p,
		top_k_openai: preset.settings.top_k,
		top_a_openai: preset.settings.top_a,
		temperature: preset.settings.temperature,
		frequency_penalty: preset.settings.frequency_penalty,
		presence_penalty: preset.settings.presence_penalty,
		top_p: preset.settings.top_p,
		repetition_penalty: preset.settings.repetition_penalty,
		min_p: preset.settings.min_p,
		top_k: preset.settings.top_k,
		top_a: preset.settings.top_a,
		seed: preset.settings.seed,
		squash_system_messages: preset.settings.squash_system_messages,
		reasoning_effort: preset.settings.reasoning_effort,
		show_thoughts: preset.settings.request_thoughts,
		request_images: preset.settings.request_images,
		function_calling: preset.settings.enable_function_calling,
		enable_web_search: preset.settings.enable_web_search,
		image_inlining: preset.settings.allow_sending_images !== "disabled",
		inline_image_quality: preset.settings.allow_sending_images === "disabled" ? "auto" : preset.settings.allow_sending_images,
		video_inlining: preset.settings.allow_sending_videos,
		names_behavior: {
			none: -1,
			default: 0,
			content: 2,
			completion: 1
		}[preset.settings.character_name_prefix],
		wrap_in_quotes: preset.settings.wrap_user_messages_in_quotes,
		prompts: [...prompt_used, ...prompt_unused],
		prompt_order: [{
			character_id: 100001,
			order: prompt_used.map((prompt) => ({
				identifier: prompt.identifier,
				enabled: prompt.enabled ?? true
			}))
		}],
		extensions
	};
}
function getPresetNames() {
	return klona(["in_use", ...preset_manager.getAllPresets()]);
}
function getLoadedPresetName() {
	return preset_manager.getSelectedPresetName();
}
function loadPreset(preset_name) {
	const preset_value = preset_manager.findPreset(preset_name);
	if (!preset_value) return false;
	preset_manager.selectPreset(preset_value);
	return true;
}
function getPreset(preset_name) {
	const original_preset = preset_name === "in_use" ? oai_settings : getCompletionPresetByName(preset_name);
	if (!original_preset) throw Error(`预设 '${preset_name}' 不存在`);
	return klona(toPreset(original_preset, { in_use: preset_name === "in_use" }));
}
async function createPreset(preset_name, preset = default_preset) {
	if (getPresetNames().includes(preset_name)) return false;
	await createOrReplacePreset(preset_name, preset);
	return true;
}
function updateOriginalPresetData(data, updates, { in_use, render }) {
	let lodash_data = _(data);
	Object.entries(settingsToUpdate).forEach(([key, { oai_setting }]) => {
		lodash_data = lodash_data.set(in_use ? oai_setting : _.get(in_use_map, oai_setting, oai_setting), updates[key]);
	});
	lodash_data.value();
	if (!in_use) return;
	saveSettingsDebounced();
	if (render === "none") return;
	const checkboxes = $();
	const inputs = $();
	Object.entries(settingsToUpdate).forEach(([key, { selector, type }]) => {
		switch (type) {
			case "checkbox":
				$(selector).prop("checked", updates[key]);
				checkboxes.add(selector);
				break;
			case "input":
				$(selector).val(updates[key]);
				inputs.add(selector);
		}
	});
	$(checkboxes).trigger("input", { source: "preset" });
	$(inputs).trigger("input", { source: "preset" });
	if (render === "debounced") promptManager.renderDebounced();
	else promptManager.render(false);
}
async function createOrReplacePreset(preset_name, preset = default_preset, { render = "debounced" } = {}) {
	const original_preset = fromPreset(preset);
	const is_existing = getPresetNames().includes(preset_name);
	if (!is_existing) {
		const { presets, preset_names } = preset_manager.getPresetList();
		presets.push(original_preset);
		preset_names[preset_name] = presets.length - 1;
		preset_manager.select.append($("<option></option>", {
			value: presets.length - 1,
			text: preset_name,
			selected: false
		}));
	} else updateOriginalPresetData(preset_name === "in_use" ? oai_settings : getCompletionPresetByName(preset_name), original_preset, {
		in_use: preset_name === "in_use",
		render
	});
	if (preset_name !== "in_use") await preset_manager.savePreset(preset_name, getCompletionPresetByName(preset_name), { skipUpdate: true });
	return !is_existing;
}
async function deletePreset(preset_name) {
	return Boolean(await preset_manager.deletePreset(preset_name));
}
async function renamePreset(preset_name, new_name) {
	if (!getPresetNames().includes(preset_name)) return false;
	await createPreset(new_name, getPreset(preset_name));
	await deletePreset(preset_name);
	return true;
}
async function replacePreset(preset_name, preset, options = {}) {
	if (!getPresetNames().includes(preset_name)) throw Error(`预设 '${preset_name}' 不存在`);
	await createOrReplacePreset(preset_name, preset, options);
}
async function updatePresetWith(preset_name, updater, options = {}) {
	if (!getPresetNames().includes(preset_name)) throw Error(`预设 '${preset_name}' 不存在`);
	const preset = await updater(getPreset(preset_name));
	await replacePreset(preset_name, preset, options);
	return preset;
}
async function setPreset(preset_name, preset, options = {}) {
	return await updatePresetWith(preset_name, (old_preset) => {
		return {
			settings: _.defaultsDeep(preset.settings, old_preset.settings),
			prompts: preset.prompts ?? old_preset.prompts,
			prompts_unused: preset.prompts_unused ?? old_preset.prompts_unused,
			extensions: _.defaultsDeep(preset.extensions, old_preset.extensions)
		};
	}, options);
}
// src/function/generate/index.ts
var generationControllers = /* @__PURE__ */ new Map();
var stopButtonBoundGenerationIds = /* @__PURE__ */ new Set();
function getProxyPresetNames() {
	return proxies.map((proxy) => proxy.name);
}
async function getModelList(custom_api) {
	const url = normalizeBaseURL(custom_api?.apiurl);
	const json = await (await fetch("/api/backends/chat-completions/status", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({
			reverse_proxy: url,
			proxy_password: custom_api.key ?? "",
			chat_completion_source: "openai"
		}),
		cache: "no-cache"
	})).json();
	return _(json?.data ?? []).map((model) => String(model?.id ?? model?.name ?? "").trim()).filter(Boolean).sort().sortedUniq().value();
}
/**
* 中断指定的生成请求
* @param id 生成ID
*/
function stopGenerationById(id) {
	const entry = generationControllers.get(id);
	if (!entry) return false;
	entry.abortController.abort(`生成 ID '${id}' 已停止`);
	generationControllers.delete(id);
	if (entry.bindToStopButton) {
		stopButtonBoundGenerationIds.delete(id);
		if (stopButtonBoundGenerationIds.size === 0) unblockGeneration();
	}
	eventSource.emit(event_types.GENERATION_STOPPED, id);
	return true;
}
/**
* 中断所有TH-generate的生成任务
*/
function stopAllGeneration() {
	try {
		const hadStopButtonBoundGeneration = stopButtonBoundGenerationIds.size > 0;
		for (const [id, entry] of generationControllers.entries()) {
			entry.abortController.abort(`生成 ID '${id}' 已停止`);
			eventSource.emit(event_types.GENERATION_STOPPED, id);
		}
		generationControllers.clear();
		stopButtonBoundGenerationIds.clear();
		if (hadStopButtonBoundGeneration) unblockGeneration();
		return true;
	} catch (error) {
		console.error(`[TavernHelper][Generate:停止] 中断所有生成任务时出错: ${error}`);
		return false;
	}
}
/**
* 清理图片处理相关的监听器和Promise
*/
function cleanupImageProcessing(imageProcessingSetup) {
	if (imageProcessingSetup) try {
		imageProcessingSetup.cleanup();
		imageProcessingSetup.rejectImageProcessing(/* @__PURE__ */ new Error("Generation stopped"));
	} catch (error) {
		console.warn(`[TavernHelper][Generate:停止] 清理图片处理时出错: ${error}`);
	}
}
/**
* 从Overrides转换为detail.OverrideConfig
* @param overrides 覆盖配置
* @returns detail.OverrideConfig
*/
function fromOverrides(overrides) {
	return {
		world_info_before: overrides.world_info_before,
		persona_description: overrides.persona_description,
		char_description: overrides.char_description,
		char_personality: overrides.char_personality,
		scenario: overrides.scenario,
		world_info_after: overrides.world_info_after,
		dialogue_examples: overrides.dialogue_examples,
		with_depth_entries: overrides.chat_history?.with_depth_entries,
		author_note: overrides.chat_history?.author_note,
		chat_history: overrides.chat_history?.prompts
	};
}
/**
* 从GenerateConfig转换为detail.GenerateParams
* @param config 生成配置
* @returns detail.GenerateParams
*/
function fromGenerateConfig(config) {
	return {
		generation_id: config.generation_id,
		user_input: config.user_input,
		use_preset: true,
		image: config.image,
		stream: config.should_stream ?? false,
		bindToStopButton: !(config.should_silence ?? false),
		overrides: config.overrides !== void 0 ? fromOverrides(config.overrides) : void 0,
		inject: config.injects,
		max_chat_history: typeof config.max_chat_history === "number" ? config.max_chat_history : void 0,
		custom_api: config.custom_api,
		tools: config.tools,
		tool_choice: config.tool_choice,
		json_schema: config.json_schema,
		should_return_reasoning: config.should_return_reasoning
	};
}
function toOrderedPrompt(prompt) {
	if (isPresetPlaceholderPrompt(prompt)) return _.snakeCase(prompt.id);
	return {
		role: prompt.role,
		content: substitudeMacros(prompt.content)
	};
}
function toInChatPrompt(prompt) {
	prompt.content = substitudeMacros(prompt.content);
	return {
		position: "in_chat",
		depth: prompt.position.depth,
		role: prompt.role,
		content: substitudeMacros(prompt.content)
	};
}
function convertGenerateWithCustomPreset(config) {
	const preset = getPreset(config.preset_name ?? "in_use");
	const prompts = preset.prompts.filter((prompt) => prompt.enabled);
	const [in_chat, ordered] = _.partition(prompts, (prompt) => prompt.position?.type === "in_chat");
	const ordered_prompts = ordered.map(toOrderedPrompt).filter((prompt) => typeof prompt === "string" || prompt.content.trim() !== "");
	const injects = _.concat(config.injects ?? [], _(in_chat).sortBy(["position.depth", "position.order"]).map(toInChatPrompt).filter((prompt) => prompt.content.trim() !== "").value());
	const custom_api = { ...config.custom_api };
	const setValidly = (param, value, min = null, max = null) => {
		if (typeof value !== "number") return;
		if (min !== null) value = Math.max(min, value);
		if (max !== null) value = Math.min(max, value);
		_.set(custom_api, param, value);
	};
	setValidly("max_tokens", preset.settings.max_completion_tokens);
	setValidly("temperature", preset.settings.temperature, 0, 2);
	setValidly("frequency_penalty", preset.settings.frequency_penalty, -2, 2);
	setValidly("presence_penalty", preset.settings.presence_penalty, -2, 2);
	setValidly("top_p", preset.settings.top_p, 0, 1);
	setValidly("top_k", preset.settings.top_k, 0, 100);
	return {
		...config,
		ordered_prompts,
		injects,
		custom_api
	};
}
/**
* 从GenerateRawConfig转换为detail.GenerateParams
* @param config 原始生成配置
* @returns detail.GenerateParams
*/
function fromGenerateRawConfig(config) {
	return {
		generation_id: config.generation_id,
		user_input: config.user_input,
		use_preset: false,
		image: config.image,
		stream: config.should_stream ?? false,
		bindToStopButton: !(config.should_silence ?? false),
		max_chat_history: typeof config.max_chat_history === "number" ? config.max_chat_history : void 0,
		overrides: config.overrides ? fromOverrides(config.overrides) : void 0,
		inject: config.injects,
		order: config.ordered_prompts,
		custom_api: config.custom_api,
		tools: config.tools,
		tool_choice: config.tool_choice,
		json_schema: config.json_schema,
		should_return_reasoning: config.should_return_reasoning
	};
}
/**
* 生成AI响应的核心函数
* @param config 生成配置参数
* @param config.user_input 用户输入文本
* @param config.use_preset 是否使用预设
* @param config.image 图片参数，可以是单个图片(File|string)或图片数组(File|string)[]
* @param config.overrides 覆盖配置
* @param config.max_chat_history 最大聊天历史数量
* @param config.inject 注入的提示词
* @param config.order 提示词顺序
* @param config.stream 是否启用流式传输
* @param config.bindToStopButton 是否绑定到酒馆停止按钮；默认为 true
* @returns 响应文本；按需为包含 reasoning 等元数据的详情对象
*/
async function iframeGenerate({ generation_id, user_input = "", use_preset = true, image = void 0, overrides = void 0, max_chat_history = void 0, inject = [], order = void 0, stream = false, bindToStopButton = true, custom_api = void 0, tools = void 0, tool_choice = void 0, json_schema = void 0, should_return_reasoning = false } = {}) {
	const generationId = generation_id;
	if (generationControllers.has(generationId)) throw new Error(`ID为 '${generationId}' 的请求正在进行中，无法启动用同一 ID 的生成任务`);
	const abortController = new AbortController();
	const shouldBindToStopButton = typeof bindToStopButton === "boolean" ? bindToStopButton : true;
	generationControllers.set(generationId, {
		abortController,
		bindToStopButton: shouldBindToStopButton
	});
	if (shouldBindToStopButton) {
		const shouldDeactivateSendButtons = stopButtonBoundGenerationIds.size === 0;
		stopButtonBoundGenerationIds.add(generationId);
		if (shouldDeactivateSendButtons) deactivateSendButtons();
	}
	let imageProcessingSetup = void 0;
	try {
		const inputResult = await processUserInputWithImages(user_input, use_preset, image);
		const { processedUserInput, processedImageArray } = inputResult;
		imageProcessingSetup = inputResult.imageProcessingSetup;
		await eventSource.emit(event_types.GENERATION_AFTER_COMMANDS, "normal", {}, false);
		const baseData = await prepareAndOverrideData({
			use_preset,
			overrides,
			max_chat_history,
			inject,
			order
		}, processedUserInput);
		const generate_data = use_preset ? await handlePresetPath(baseData, processedUserInput, {
			image,
			overrides,
			max_chat_history,
			inject,
			order
		}) : await handleCustomPath(baseData, {
			image,
			overrides,
			max_chat_history,
			inject,
			order,
			processedImageArray
		}, processedUserInput);
		await eventSource.emit(event_types.GENERATE_AFTER_DATA, generate_data, false);
		const toolOptions = tools?.length ? {
			tools,
			tool_choice
		} : void 0;
		return await generateResponse(generate_data, stream, generationId, imageProcessingSetup, abortController, custom_api, toolOptions, json_schema, should_return_reasoning);
	} catch (error) {
		if (imageProcessingSetup) imageProcessingSetup.rejectImageProcessing(error);
		throw error;
	} finally {
		cleanupImageProcessing(imageProcessingSetup);
		generationControllers.delete(generationId);
		if (shouldBindToStopButton) {
			stopButtonBoundGenerationIds.delete(generationId);
			if (stopButtonBoundGenerationIds.size === 0) unblockGeneration();
		}
	}
}
async function generate(config) {
	config.generation_id = config.generation_id || uuidv4();
	await eventSource.emit("js_generation_requested", config.generation_id, "generate", config);
	if (config.preset_name && config.preset_name !== "in_use") return await iframeGenerate(fromGenerateRawConfig(convertGenerateWithCustomPreset(config)));
	return await iframeGenerate(fromGenerateConfig(config));
}
async function generateRaw(config) {
	config.generation_id = config.generation_id || uuidv4();
	await eventSource.emit("js_generation_requested", config.generation_id, "generateRaw", config);
	return await iframeGenerate(fromGenerateRawConfig(config));
}
/**
* 点击停止按钮时的逻辑
*/
$(document).off("click.tavernhelper_generate", "#mes_stop").on("click.tavernhelper_generate", "#mes_stop", function() {
	stopGeneration();
	if (stopButtonBoundGenerationIds.size === 0) return;
	const idsToAbort = Array.from(stopButtonBoundGenerationIds.values());
	for (const id of idsToAbort) {
		const entry = generationControllers.get(id);
		if (!entry) {
			stopButtonBoundGenerationIds.delete(id);
			continue;
		}
		entry.abortController.abort("点击停止按钮");
		generationControllers.delete(id);
		stopButtonBoundGenerationIds.delete(id);
		eventSource.emit(event_types.GENERATION_STOPPED, id);
	}
	if (stopButtonBoundGenerationIds.size === 0) unblockGeneration();
});
// node_modules/.pnpm/async-wait-until@2.0.31/node_modules/async-wait-until/dist/index.esm.js
function e(e, t, o, n) {
	return new (o || (o = Promise))((function(r, i) {
		function u(e) {
			try {
				s(n.next(e));
			} catch (e) {
				i(e);
			}
		}
		function c(e) {
			try {
				s(n.throw(e));
			} catch (e) {
				i(e);
			}
		}
		function s(e) {
			var t;
			e.done ? r(e.value) : (t = e.value, t instanceof o ? t : new o((function(e) {
				e(t);
			}))).then(u, c);
		}
		s((n = n.apply(e, t || [])).next());
	}));
}
var t$1 = class t$1 extends Error {
	constructor(e) {
		super(null != e ? `Timed out after waiting for ${e} ms` : "Timed out"), Object.setPrototypeOf(this, t$1.prototype);
	}
};
var r = Number.POSITIVE_INFINITY;
var i = (o, n, i) => {
	var u, c;
	const s = null !== (u = "number" == typeof n ? n : null == n ? void 0 : n.timeout) && void 0 !== u ? u : 5e3, l = null !== (c = "number" == typeof n ? i : null == n ? void 0 : n.intervalBetweenAttempts) && void 0 !== c ? c : 50;
	let a, f;
	return Promise.race([...s !== r ? [new Promise(((e, o) => {
		a = setTimeout((() => {
			o(new t$1(s));
		}), s);
	}))] : [], new Promise(((t, n) => {
		const r = () => e(void 0, void 0, void 0, (function* () {
			try {
				const e = yield o();
				if (e) return void t(e);
				f = setTimeout(r, l);
			} catch (e) {
				n(e);
			}
		}));
		r();
	}))]).finally((() => {
		a && clearTimeout(a), f && clearTimeout(f);
	}));
};
// src/function/global.ts
function hasMvuData(chat_message) {
	return _.has(chat_message?.variables?.[chat_message.swipe_id ?? 0], "stat_data");
}
async function waitMvu() {
	try {
		await i(() => hasMvuData(chat[0]) || _.takeRight(chat, 10).some(hasMvuData));
	} catch (error) {}
}
function initializeGlobal(global, value) {
	_.set(window, global, value);
	eventSource.emit(`global_${global}_initialized`);
}
function _initializeGlobal(global, value) {
	_.set(window, global, value);
	_eventEmit.call(this, `global_${global}_initialized`);
}
async function waitGlobalInitialized(global) {
	if (_.has(window, global)) return;
	return new Promise((resolve) => {
		eventSource.once(`global_${global}_initialized`, () => {
			resolve();
		});
	});
}
async function _waitGlobalInitialized(global) {
	if (_.has(window, global)) {
		Object.defineProperty(this, global, {
			get: () => _.get(window, global),
			configurable: true
		});
		if (global === "Mvu") await waitMvu();
		return;
	}
	return new Promise((resolve) => {
		_eventOnce.call(this, `global_${global}_initialized`, async () => {
			Object.defineProperty(this, global, {
				get: () => _.get(window, global),
				configurable: true
			});
			if (global === "Mvu") await waitMvu();
			resolve();
		});
	});
}
// src/function/import_raw.ts
async function importRawCharacter(name, content) {
	name = name.replace(/\.(?:png|json)$/, "");
	const avatar = name + ".png";
	const old_worldbook_name = RawCharacter.find({ name })?.data?.extensions?.world;
	const file = new File([content], name, { type: "image/png" });
	const form_data = new FormData();
	form_data.append("avatar", file);
	form_data.append("file_type", "png");
	form_data.append("preserved_name", file.name);
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	return fetch("/api/characters/import", {
		method: "POST",
		headers,
		body: form_data,
		cache: "no-cache"
	}).then(async (result) => {
		$("#character_search_bar").val("").trigger("input");
		const store = useCharacterSettingsStore();
		const is_current = store.avatar === avatar;
		await getCharacters();
		await getOneCharacter(avatar);
		await render_character(name, avatar, await getCharacter(name), is_current);
		if (old_worldbook_name) {
			const worldbook = characters.find((character) => character.avatar === avatar)?.data?.character_book;
			if (world_names.includes(old_worldbook_name) && worldbook) {
				await saveWorldInfo(worldbook.name, convertCharacterBook(worldbook), true);
				reloadEditor(worldbook.name);
			}
		}
		if (is_current) store.forceReload();
		return result;
	});
}
async function importRawChat(name, content) {
	if (this_chid === void 0) throw Error("导入聊天文件失败, 请先选择一张角色卡");
	const form_data = new FormData();
	form_data.append("avatar", new File([content], name + ".jsonl", { type: "application/json" }));
	form_data.append("file_type", "jsonl");
	form_data.append("avatar_url", characters[this_chid].avatar);
	form_data.append("character_name", characters[this_chid].name);
	form_data.append("user_name", name1);
	const headers = getRequestHeaders();
	_.unset(headers, "Content-Type");
	return fetch(`/api/chats/import`, {
		method: "POST",
		headers,
		body: form_data,
		cache: "no-cache"
	});
}
async function importRawPreset(name, content) {
	try {
		await preset_manager.savePreset(name, JSON.parse(content));
		return true;
	} catch (error) {
		return false;
	}
}
async function importRawWorldbook(name, content) {
	try {
		await saveWorldInfo(name, _.pick(JSON.parse(content), "entries"), true);
	} catch (error) {
		return false;
	}
	reloadEditor(name);
	return true;
}
function importRawTavernRegex(name, content) {
	const json = JSON.parse(content);
	if (!_.has(json, "findRegex")) return false;
	_.set(json, "id", uuidv4());
	_.set(json, "scriptName", name);
	extension_settings.regex.push(json);
	render_tavern_regexes_debounced();
	return true;
}
// src/function/lorebook_entry.ts
var default_original_lorebook_entry = {
	key: [],
	keysecondary: [],
	comment: "",
	content: "",
	constant: false,
	vectorized: false,
	selective: true,
	selectiveLogic: 0,
	addMemo: true,
	order: 100,
	position: 0,
	disable: false,
	excludeRecursion: false,
	preventRecursion: false,
	matchPersonaDescription: false,
	matchCharacterDescription: false,
	matchCharacterPersonality: false,
	matchCharacterDepthPrompt: false,
	matchScenario: false,
	matchCreatorNotes: false,
	delayUntilRecursion: 0,
	probability: 100,
	useProbability: true,
	depth: 4,
	group: "",
	groupOverride: false,
	groupWeight: 100,
	scanDepth: null,
	caseSensitive: null,
	matchWholeWords: null,
	useGroupScoring: null,
	automationId: "",
	role: 0,
	sticky: null,
	cooldown: null,
	delay: null
};
function toLorebookEntry(entry) {
	return {
		uid: entry.uid,
		display_index: entry.displayIndex,
		comment: entry.comment,
		enabled: !entry.disable,
		type: entry.constant ? "constant" : entry.vectorized ? "vectorized" : "selective",
		position: {
			0: "before_character_definition",
			1: "after_character_definition",
			5: "before_example_messages",
			6: "after_example_messages",
			2: "before_author_note",
			3: "after_author_note"
		}[entry.position] ?? (entry.role === 1 ? "at_depth_as_user" : entry.role === 2 ? "at_depth_as_assistant" : "at_depth_as_system"),
		depth: entry.position === 4 ? entry.depth : null,
		order: entry.order,
		probability: entry.probability,
		key: entry.key,
		keys: entry.key,
		logic: {
			0: "and_any",
			1: "not_all",
			2: "not_any",
			3: "and_all"
		}[entry.selectiveLogic],
		filter: entry.keysecondary,
		filters: entry.keysecondary,
		scan_depth: entry.scanDepth ?? "same_as_global",
		case_sensitive: entry.caseSensitive ?? "same_as_global",
		match_whole_words: entry.matchWholeWords ?? "same_as_global",
		use_group_scoring: entry.useGroupScoring ?? "same_as_global",
		automation_id: entry.automationId || null,
		exclude_recursion: entry.excludeRecursion,
		prevent_recursion: entry.preventRecursion,
		delay_until_recursion: entry.delayUntilRecursion,
		content: entry.content,
		group: entry.group,
		group_prioritized: entry.groupOverride,
		group_weight: entry.groupWeight,
		sticky: entry.sticky || null,
		cooldown: entry.cooldown || null,
		delay: entry.delay || null
	};
}
async function getLorebookEntries(lorebook, { filter = "none" } = {}) {
	if (!world_names.includes(lorebook)) throw Error(`未能找到世界书 '${lorebook}'`);
	const data = await loadWorldInfo(lorebook);
	let entries = _(data.entries).values().map(toLorebookEntry).value();
	if (filter !== "none") entries = entries.filter((entry) => Object.entries(filter).every(([field, expected_value]) => {
		const entry_value = entry[field];
		if (Array.isArray(entry_value)) return expected_value.every((value) => entry_value.includes(value));
		if (typeof entry_value === "string") return entry_value.includes(expected_value);
		return entry_value === expected_value;
	}));
	return klona(entries);
}
function fromPartialLorebookEntry(entry) {
	const transformers = {
		uid: (value) => ({ uid: value }),
		display_index: (value) => ({ displayIndex: value }),
		comment: (value) => ({ comment: value }),
		enabled: (value) => ({ disable: !value }),
		type: (value) => ({
			constant: value === "constant",
			vectorized: value === "vectorized"
		}),
		position: (value) => ({
			position: {
				before_character_definition: 0,
				after_character_definition: 1,
				before_example_messages: 5,
				after_example_messages: 6,
				before_author_note: 2,
				after_author_note: 3,
				at_depth_as_system: 4,
				at_depth_as_user: 4,
				at_depth_as_assistant: 4
			}[value],
			role: _.get({
				at_depth_as_system: 0,
				at_depth_as_user: 1,
				at_depth_as_assistant: 2
			}, value, null)
		}),
		depth: (value) => ({ depth: value === null ? 4 : value }),
		order: (value) => ({ order: value }),
		probability: (value) => ({ probability: value }),
		keys: (value) => ({ key: value }),
		logic: (value) => ({ selectiveLogic: {
			and_any: 0,
			not_all: 1,
			not_any: 2,
			and_all: 3
		}[value] }),
		filters: (value) => ({ keysecondary: value }),
		scan_depth: (value) => ({ scanDepth: value === "same_as_global" ? null : value }),
		case_sensitive: (value) => ({ caseSensitive: value === "same_as_global" ? null : value }),
		match_whole_words: (value) => ({ matchWholeWords: value === "same_as_global" ? null : value }),
		use_group_scoring: (value) => ({ useGroupScoring: value === "same_as_global" ? null : value }),
		automation_id: (value) => ({ automationId: value === null ? "" : value }),
		exclude_recursion: (value) => ({ excludeRecursion: value }),
		prevent_recursion: (value) => ({ preventRecursion: value }),
		delay_until_recursion: (value) => ({ delayUntilRecursion: value }),
		content: (value) => ({ content: value }),
		group: (value) => ({ group: value }),
		group_prioritized: (value) => ({ groupOverride: value }),
		group_weight: (value) => ({ groupWeight: value }),
		sticky: (value) => ({ sticky: value === null ? 0 : value }),
		cooldown: (value) => ({ cooldown: value === null ? 0 : value }),
		delay: (value) => ({ delay: value === null ? 0 : value })
	};
	return _.merge({}, default_original_lorebook_entry, ...Object.entries(entry).filter(([_, value]) => value !== void 0).map(([key, value]) => transformers[key]?.(value)));
}
var MAX_UID = 1e6;
function handleLorebookEntriesCollision(entries) {
	const uid_set = /* @__PURE__ */ new Set();
	const handle_uid_collision = (index) => {
		if (index === void 0) index = _.random(0, 999999);
		let i = 1;
		while (true) {
			if (!uid_set.has(index)) {
				uid_set.add(index);
				return index;
			}
			index = (index + i * i) % MAX_UID;
			++i;
		}
	};
	let max_display_index = _.max(entries.map((entry) => entry.display_index ?? -1)) ?? -1;
	return entries.map((entry) => ({
		...entry,
		uid: handle_uid_collision(entry.uid),
		display_index: entry.display_index ?? ++max_display_index
	}));
}
async function replaceLorebookEntries(lorebook, entries) {
	if (!world_names.includes(lorebook)) throw Error(`未能找到世界书 '${lorebook}'`);
	const data = { entries: _.merge({}, ...handleLorebookEntriesCollision(entries).map(fromPartialLorebookEntry).map((entry) => ({ [entry.uid]: entry }))) };
	await saveWorldInfo(lorebook, data);
	reloadEditorDebounced(lorebook);
}
async function updateLorebookEntriesWith(lorebook, updater) {
	await replaceLorebookEntries(lorebook, await updater(await getLorebookEntries(lorebook)));
	return getLorebookEntries(lorebook);
}
async function setLorebookEntries(lorebook, entries) {
	return await updateLorebookEntriesWith(lorebook, (data) => {
		for (const entry_to_set of entries) {
			const data_entry = data.find((entry) => entry.uid === entry_to_set.uid);
			if (data_entry) _.merge(data_entry, entry_to_set);
		}
		return data;
	});
}
async function createLorebookEntries(lorebook, entries) {
	const new_uids = [];
	return {
		entries: await updateLorebookEntriesWith(lorebook, (data) => {
			const uid_set = new Set(data.map((entry) => entry.uid));
			const get_free_uid = () => {
				for (let i = 0; i < MAX_UID; ++i) if (!uid_set.has(i)) {
					uid_set.add(i);
					new_uids.push(i);
					return i;
				}
				throw Error(`无法找到可用的世界书条目 uid`);
			};
			entries.forEach((entry) => entry.uid = get_free_uid());
			return [...data, ...entries];
		}),
		new_uids
	};
}
async function deleteLorebookEntries(lorebook, uids) {
	let deleted = false;
	return {
		entries: await updateLorebookEntriesWith(lorebook, (data) => {
			deleted = _.remove(data, (entry) => uids.includes(entry.uid)).length > 0;
			return data;
		}),
		delete_occurred: deleted
	};
}
/** @deprecated 请使用 `createLorebookEntries` 代替 */
async function createLorebookEntry(lorebook, field_values) {
	return (await createLorebookEntries(lorebook, [field_values])).new_uids[0];
}
/** @deprecated 请使用 `deleteLorebookEntries` 代替 */
async function deleteLorebookEntry(lorebook, uid) {
	return (await deleteLorebookEntries(lorebook, [uid])).delete_occurred;
}
// src/function/persona.ts
var DEFAULT_DEPTH$1 = 2;
var DEFAULT_ROLE = 0;
/**
* 获取 persona 的名称列表
*/
function getPersonaNames() {
	return Object.values(power_user.personas).map(String);
}
/**
* 获取 persona 的头像 id 列表
*/
function getPersonaIds() {
	return Object.keys(power_user.personas);
}
/**
* 获取当前 persona 的名称
*/
function getCurrentPersonaName() {
	return power_user.personas[user_avatar$1] ?? null;
}
/**
* 获取当前 persona 的头像 id
*/
function getCurrentPersonaId() {
	return user_avatar$1 || null;
}
/**
* 根据名称查找 persona 头像 id
*/
function findPersonaIdsByName(name) {
	const lowered_name = name.toLowerCase();
	return Object.entries(power_user.personas).filter(([, persona_name]) => String(persona_name).toLowerCase() === lowered_name).map(([avatar_id]) => avatar_id);
}
/**
* 根据名称或头像 id 查找唯一 persona
*/
function findPersonaId(persona_id) {
	if (!persona_id || persona_id === "current") return user_avatar$1 || null;
	if (power_user.personas[persona_id] !== void 0) return persona_id;
	const matches = findPersonaIdsByName(persona_id);
	return matches.length === 1 ? matches[0] : null;
}
/**
* 生成新 persona 使用的头像 id
*/
function makePersonaAvatarId(persona_name) {
	const safe_name = persona_name.replace(/[^a-zA-Z0-9]/g, "") || "persona";
	return `${Date.now()}-${safe_name}.png`;
}
/**
* 获取 persona 描述对象的默认值
*/
function getDefaultDescriptor() {
	return {
		description: "",
		position: persona_description_positions.IN_PROMPT,
		depth: DEFAULT_DEPTH$1,
		role: DEFAULT_ROLE,
		lorebook: "",
		connections: [],
		title: ""
	};
}
/**
* 把酒馆 persona 数据转换为接口数据
*/
function toPersona(avatar_id) {
	const descriptor = {
		...getDefaultDescriptor(),
		...power_user.persona_descriptions[avatar_id]
	};
	return {
		avatar_id,
		avatar: avatar_id,
		name: String(power_user.personas[avatar_id] ?? ""),
		title: descriptor.title,
		description: descriptor.description,
		position: descriptor.position,
		depth: descriptor.depth,
		role: descriptor.role,
		lorebook: descriptor.lorebook,
		connections: klona(descriptor.connections),
		is_default: power_user.default_persona === avatar_id
	};
}
/**
* 根据局部更新合成 persona 描述对象
*/
function toPersonaDescriptor(persona, old_data) {
	const old_descriptor = {
		...getDefaultDescriptor(),
		...old_data
	};
	return {
		description: persona.description ?? old_descriptor.description,
		position: persona.position ?? old_descriptor.position,
		depth: persona.depth ?? old_descriptor.depth,
		role: persona.role ?? old_descriptor.role,
		lorebook: persona.lorebook ?? old_descriptor.lorebook,
		connections: persona.connections ?? old_descriptor.connections,
		title: persona.title ?? old_descriptor.title
	};
}
/**
* 获取可上传头像的二进制文件
*/
async function getAvatarFile(avatar, avatar_id) {
	if (isBlob(avatar)) return new File([avatar], avatar_id);
	const source = avatar ? getUserAvatar(avatar) : default_user_avatar;
	const blob = await fetch(source).then((response) => response.blob());
	return new File([blob], avatar_id, { type: blob.type || "image/png" });
}
/**
* 获取后端现有 persona 头像 id 列表
*/
async function getExistingAvatarIds() {
	const avatars = await getUserAvatars(false);
	if (!Array.isArray(avatars)) throw new Error("获取 persona 头像列表失败");
	return avatars;
}
/**
* 上传或补齐 persona 头像文件
*/
async function ensurePersonaAvatar(avatar_id, avatar) {
	if (!avatar || avatar === avatar_id) {
		if ((await getExistingAvatarIds()).includes(avatar_id)) return;
	}
	const form_data = new FormData();
	form_data.append("avatar", await getAvatarFile(avatar, avatar_id));
	form_data.append("overwrite_name", avatar_id);
	const response = await fetch("/api/avatars/upload", {
		method: "POST",
		headers: getRequestHeaders({ omitContentType: true }),
		cache: "no-cache",
		body: form_data
	});
	if (!response.ok) throw new Error(`上传 persona 头像 '${avatar_id}' 失败: (${response.status}) ${await response.text()}`);
}
/**
* 同步当前 persona 的前端状态
*/
function syncCurrentPersona(avatar_id, descriptor) {
	if (avatar_id !== user_avatar$1) return;
	power_user.persona_description = descriptor.description;
	power_user.persona_description_position = descriptor.position;
	power_user.persona_description_depth = descriptor.depth;
	power_user.persona_description_role = descriptor.role;
	power_user.persona_description_lorebook = descriptor.lorebook;
	setUserName(power_user.personas[avatar_id], { toastPersonaNameChange: false });
}
/**
* 写入 persona 的名称和描述数据
*/
function writePersona(avatar_id, persona, old_data) {
	const descriptor = toPersonaDescriptor(persona, old_data);
	power_user.personas[avatar_id] = persona.name ?? power_user.personas[avatar_id] ?? avatar_id;
	power_user.persona_descriptions[avatar_id] = descriptor;
	if (persona.is_default !== void 0) {
		if (persona.is_default) power_user.default_persona = avatar_id;
		else if (power_user.default_persona === avatar_id) power_user.default_persona = null;
	}
	syncCurrentPersona(avatar_id, descriptor);
	saveSettingsDebounced();
}
/**
* 删除 persona 的设置数据
*/
async function removePersonaData(avatar_id) {
	delete power_user.personas[avatar_id];
	delete power_user.persona_descriptions[avatar_id];
	if (power_user.default_persona === avatar_id) power_user.default_persona = null;
	if (chat_metadata["persona"] === avatar_id) {
		delete chat_metadata["persona"];
		await saveMetadata();
	}
	saveSettingsDebounced();
}
/**
* 当前 persona 被删除后选择下一个可用 persona
*/
async function selectNextPersonaIfNeeded(avatar_id) {
	if (avatar_id !== user_avatar$1) return;
	const next_avatar = Object.keys(power_user.personas)[0];
	if (next_avatar) await setUserAvatar(next_avatar, {
		toastPersonaNameChange: false,
		navigateToCurrent: true
	});
}
/**
* 渲染 persona 管理列表
*/
async function renderPersona(avatar_id, { render = "debounced" } = {}) {
	switch (render) {
		case "debounced":
			renderPersonaDebounced(avatar_id);
			break;
		case "immediate": await getUserAvatars(true, avatar_id);
	}
}
var renderPersonaDebounced = _.debounce((avatar_id) => void getUserAvatars(true, avatar_id), 1e3);
/**
* 获取 persona 的头像路径
*/
function getPersonaAvatarPath(persona_id = "current") {
	const avatar_id = findPersonaId(persona_id);
	return avatar_id ? `./User Avatars/${avatar_id}` : null;
}
/**
* 获取 persona 的内容
*/
function getPersona(persona_id) {
	const avatar_id = findPersonaId(persona_id);
	if (!avatar_id) throw Error(`persona '${persona_id}' 不存在或名称不唯一`);
	return klona(toPersona(avatar_id));
}
/**
* 新建 persona
*/
async function createPersona(persona_name, persona = {}, options = {}) {
	const avatar_id = persona.avatar_id ?? makePersonaAvatarId(persona_name);
	if (persona_name === "current" || power_user.personas[avatar_id] !== void 0 || findPersonaIdsByName(persona_name).length > 0) return false;
	await ensurePersonaAvatar(avatar_id, persona.avatar);
	writePersona(avatar_id, {
		...persona,
		name: persona_name
	});
	await renderPersona(avatar_id, options);
	return true;
}
/**
* 创建或替换 persona
*/
async function createOrReplacePersona(persona_name, persona = {}, options = {}) {
	const avatar_id = findPersonaId(persona_name);
	if (!avatar_id) {
		if (findPersonaIdsByName(persona_name).length > 1) throw Error(`persona '${persona_name}' 不存在或名称不唯一`);
		return await createPersona(persona_name, persona, options);
	}
	await replacePersona(avatar_id, persona, options);
	return false;
}
/**
* 删除 persona
*/
async function deletePersona(persona_id) {
	const avatar_id = findPersonaId(persona_id);
	if (!avatar_id) return false;
	const response = await fetch("/api/avatars/delete", {
		method: "POST",
		headers: getRequestHeaders(),
		body: JSON.stringify({ avatar: avatar_id })
	});
	if (!response.ok) throw new Error(`删除 persona '${persona_id}' 失败: (${response.status}) ${await response.text()}`);
	await removePersonaData(avatar_id);
	await getUserAvatars(true);
	await selectNextPersonaIfNeeded(avatar_id);
	return true;
}
/**
* 完全替换 persona 的内容
*/
async function replacePersona(persona_id, persona, options = {}) {
	const avatar_id = findPersonaId(persona_id);
	if (!avatar_id) throw Error(`persona '${persona_id}' 不存在或名称不唯一`);
	await ensurePersonaAvatar(avatar_id, persona.avatar);
	writePersona(avatar_id, persona, power_user.persona_descriptions[avatar_id]);
	await renderPersona(avatar_id, options);
}
/**
* 用 updater 函数更新 persona
*/
async function updatePersonaWith(persona_id, updater, options = {}) {
	const persona = await updater(getPersona(persona_id));
	await replacePersona(persona_id, persona, options);
	return persona;
}
// src/function/slash.ts
async function triggerSlash(command) {
	const result = await executeSlashCommandsWithOptions(command);
	if (result.isError) throw Error(`运行 Slash 命令 '${command}' 时出错: ${result.errorMessage}`);
	return result.pipe;
}
// src/slash_command/audio.ts
/** @deprecated 酒馆助手脚本已自带按钮，不再需要 /STScript */
function audioEnable(_args) {
	return "";
}
/** @deprecated 酒馆助手脚本已自带按钮，不再需要 /STScript */
function audioPlay(args) {
	if (!["bgm", "ambient"].includes(args.type)) {
		console.warn("WARN: Invalid arguments for /audioplaypause command");
		return "";
	}
	const type = args.type.toLowerCase();
	const play = Boolean(args.play ?? "true");
	get_store_by_type(type).playing = play;
	return "";
}
/** @deprecated 酒馆助手脚本已自带按钮，不再需要 /STScript */
function audioMode(args) {
	if (!["bgm", "ambient"].includes(args.type) || ![
		"repeat",
		"random",
		"single",
		"stop"
	].includes(args.mode)) {
		console.warn("WARN: Invalid arguments for /audiomode command");
		return "";
	}
	const type = args.type.toLowerCase();
	const mode = args.mode.toLowerCase();
	get_store_by_type(type).mode = {
		single: "repeat_one",
		repeat: "repeat_all",
		random: "shuffle",
		stop: "play_one_and_stop"
	}[mode];
	return "";
}
/** @deprecated 酒馆助手脚本已自带按钮，不再需要 /STScript */
function audioImport(args, url) {
	if (!["bgm", "ambient"].includes(args.type)) {
		console.warn("WARN: Invalid arguments for /audioplaypause command");
		return "";
	}
	const type = args.type.toLowerCase();
	const play = Boolean(args.play ?? "true");
	const store = get_store_by_type(type);
	const urls = _(url).split(",").map((url) => url.trim()).filter((url) => url !== "").uniq().filter((url) => !store.playlist.some((item) => item.url === url)).map((url) => ({
		url,
		title: handle_url_to_title(url)
	})).value();
	if (urls.length === 0) {
		console.warn("WARN: Invalid or empty URLs provided for /audioimport command");
		return "";
	}
	store.playlist.push(...urls);
	if (play) {
		store.src = urls[0].url;
		store.progress = 0;
		store.playing = play;
	}
	return "";
}
/** @deprecated 酒馆助手脚本已自带按钮，不再需要 /STScript */
function audioSelect(args, url) {
	if (!url) {
		console.warn("WARN: Missing URL for /audioselect command");
		return "";
	}
	const store = get_store_by_type(args.type.toLowerCase());
	if (!store.playlist.some((item) => item.url === url)) store.playlist.push({
		url,
		title: handle_url_to_title(url)
	});
	store.src = url;
	store.progress = 0;
	store.playing = true;
	return "";
}
// src/store/iframe_logs.ts
var useIframeLogsStore = defineStore("iframe_logs", () => {
	const iframe_logs = ref(/* @__PURE__ */ new Map());
	const init = (iframe_id) => {
		iframe_logs.value.set(iframe_id, []);
	};
	const log = (iframe_id, level, ...args) => {
		if (!iframe_logs.value.has(iframe_id)) iframe_logs.value.set(iframe_id, []);
		iframe_logs.value.get(iframe_id)?.push({
			level: level === "log" ? "info" : level,
			message: args.map(String).join(""),
			timestamp: Date.now()
		});
	};
	const clear = (iframe_id) => {
		iframe_logs.value.delete(iframe_id);
	};
	return {
		iframe_logs,
		init,
		log,
		clear
	};
});
// src/function/index.ts
function getTavernHelper() {
	return {
		_th_impl: {
			_init: useIframeLogsStore().init,
			_log: useIframeLogsStore().log,
			_clearLog: useIframeLogsStore().clear,
			writeExtensionField
		},
		_bind: {
			_eventOn,
			_eventOnButton,
			_eventMakeLast,
			_eventMakeFirst,
			_eventOnce,
			_eventEmit,
			_eventEmitAndWait,
			_eventRemoveListener,
			_eventClearEvent,
			_eventClearListener,
			_eventClearAll,
			_initializeGlobal,
			_waitGlobalInitialized,
			_registerMacroLike,
			_getButtonEvent,
			_getScriptButtons,
			_replaceScriptButtons,
			_updateScriptButtonsWith,
			_appendInexistentScriptButtons,
			_getScriptName,
			_getScriptInfo,
			_replaceScriptInfo,
			_getVariables,
			_getAllVariables,
			_replaceVariables,
			_updateVariablesWith,
			_insertOrAssignVariables,
			_insertVariables,
			_deleteVariable,
			_reloadIframe,
			_errorCatched,
			_getIframeName,
			_getScriptId,
			_getCurrentMessageId
		},
		audioEnable,
		audioImport,
		audioMode,
		audioPlay,
		audioSelect,
		playAudio,
		pauseAudio,
		getAudioList,
		replaceAudioList,
		appendAudioList,
		getAudioSettings,
		setAudioSettings,
		getCurrentAudio,
		builtin,
		getCharacterNames,
		getCharacterIds,
		getCurrentCharacterName,
		getCurrentCharacterId,
		createCharacter,
		createOrReplaceCharacter,
		deleteCharacter: deleteCharacter$1,
		getCharacter,
		replaceCharacter,
		updateCharacterWith,
		getChatMessages,
		setChatMessages,
		setChatMessage,
		createChatMessages,
		deleteChatMessages,
		rotateChatMessages,
		formatAsDisplayedMessage,
		retrieveDisplayedMessage,
		refreshOneMessage,
		tavern_events,
		iframe_events,
		isAdmin: isAdmin$1,
		getTavernHelperExtensionId,
		getExtensionType,
		getExtensionStatus: getExtensionInstallationInfo,
		isInstalledExtension,
		installExtension,
		uninstallExtension,
		reinstallExtension,
		updateExtension,
		getTavernFile,
		uploadTavernFile,
		deleteTavernFile,
		importRawCharacter,
		importRawPreset,
		importRawChat,
		importRawWorldbook,
		importRawTavernRegex,
		injectPrompts,
		uninjectPrompts,
		builtin_prompt_default_order: placeholder_prompt_default_order,
		getModelList,
		getProxyPresetNames,
		generate,
		generateRaw,
		stopGenerationById,
		stopAllGeneration,
		initializeGlobal,
		waitGlobalInitialized,
		getLorebookEntries,
		replaceLorebookEntries,
		updateLorebookEntriesWith,
		setLorebookEntries,
		createLorebookEntries,
		createLorebookEntry,
		deleteLorebookEntries,
		deleteLorebookEntry,
		getLorebookSettings,
		setLorebookSettings,
		getCharLorebooks,
		setCurrentCharLorebooks,
		getLorebooks,
		deleteLorebook,
		createLorebook,
		getCurrentCharPrimaryLorebook,
		getChatLorebook,
		setChatLorebook,
		getOrCreateChatLorebook,
		isPresetNormalPrompt,
		isPresetSystemPrompt,
		isPresetPlaceholderPrompt,
		default_preset,
		getPresetNames,
		getLoadedPresetName,
		loadPreset,
		createPreset,
		createOrReplacePreset,
		deletePreset,
		renamePreset,
		getPreset,
		replacePreset,
		updatePresetWith,
		setPreset,
		getPersonaNames,
		getPersonaIds,
		getCurrentPersonaName,
		getCurrentPersonaId,
		getPersonaAvatarPath,
		createPersona,
		createOrReplacePersona,
		deletePersona,
		getPersona,
		replacePersona,
		updatePersonaWith,
		RawCharacter,
		getCharData,
		getCharAvatarPath,
		getChatHistoryBrief,
		getChatHistoryDetail,
		registerMacroLike,
		unregisterMacroLike,
		getAllEnabledScriptButtons,
		getScriptTrees,
		replaceScriptTrees,
		updateScriptTreesWith,
		triggerSlash,
		triggerSlashWithResult: triggerSlash,
		formatAsTavernRegexedString,
		isCharacterTavernRegexesEnabled,
		getTavernRegexes,
		replaceTavernRegexes,
		updateTavernRegexesWith,
		substitudeMacros,
		getLastMessageId: getLastMessageId$1,
		errorCatched,
		getMessageId,
		registerVariableSchema,
		getVariables,
		replaceVariables,
		updateVariablesWith,
		insertOrAssignVariables,
		deleteVariable,
		insertVariables,
		getTavernHelperVersion,
		getFrontendVersion: getTavernHelperVersion,
		updateTavernHelper,
		updateFrontendVersion: updateTavernHelper,
		getTavernVersion,
		getWorldbookNames,
		getGlobalWorldbookNames,
		rebindGlobalWorldbooks,
		getCharWorldbookNames,
		rebindCharWorldbooks,
		getChatWorldbookName,
		rebindChatWorldbook,
		getOrCreateChatWorldbook,
		createWorldbook,
		createOrReplaceWorldbook,
		deleteWorldbook,
		getWorldbook,
		replaceWorldbook,
		updateWorldbookWith,
		createWorldbookEntries,
		deleteWorldbookEntries
	};
}
function initTavernHelperObject() {
	globalThis.TavernHelper = getTavernHelper();
}
// src/rikkast-entry.ts
/**
* rikkaST 精简入口：只导出 Tavern Helper 的 API 层，不含 Vue 面板与 DOM 挂载。
*
* 为什么需要单独入口：
* 上游 `src/index.ts` 把三件事混在一起 —— ①API 初始化 ②Vue 面板挂载 ③jQuery DOM 操作。
* 在 Android WebView 里我们只需要 ①：
*   - ②③ 依赖 `$('#extensions_settings')` 这类 ST DOM，我们根本没有，会直接崩。
*   - 而且 Vue 面板是给桌面端用的，移动端要自己的 Compose UI。
*
* 本入口只 import `@/function/index`，vite 会把 Vue/Panel 相关模块整棵摇掉。
*
* 注意：上游 `getTavernHelper()` 是**私有函数**（src/function/index.ts:211 无 export），
* 唯一公开的初始化入口是 `initTavernHelperObject()`，它把 API 挂到 `globalThis.TavernHelper`。
* 所以我们走它，再从 globalThis 取回来。
*/
/**
* rikkaST 侧统一初始化入口。
*
* 行为：
*  1. 调用上游 `initTavernHelperObject()` 装配 API（内部是纯对象构造，不碰 DOM）；
*  2. 把 API 对象逐个挂到目标 scope，供脚本以裸全局方式调用（对齐上游行为）；
*  3. 返回 API 对象，便于宿主显式引用。
*
* 不做任何 DOM 操作，可在无 jQuery 环境调用。
*/
function installTavernHelper(target) {
	initTavernHelperObject();
	const g = typeof globalThis !== "undefined" ? globalThis : null;
	const api = g?.TavernHelper ?? null;
	const scope = target ?? g;
	if (scope && api) {
		scope.TavernHelper = api;
		for (const key of Object.keys(api)) if (!(key in scope)) try {
			scope[key] = api[key];
		} catch (_e) {}
	}
	return api;
}
export { initTavernHelperObject, installTavernHelper };
