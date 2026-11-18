package me.rerere.rikkahub.data.st.preset

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal const val SPRESET_JS_TIMEOUT_MS = 5_000L
internal const val SPRESET_JS_MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
internal const val SPRESET_JS_MAX_STACK_BYTES = 256L * 1024

internal val spresetJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * QuickJS 公共初始化。
 *
 * 关键点：QuickJS 默认没有 console，而真实 SPreset 的 OutputPreprocessing 脚本会调用
 * `console.log(buffer)`。这里统一注入 no-op console，避免脚本必然 ReferenceError。
 */
internal suspend fun QuickJs.installSpresetConsole() {
    evaluate<Unit>(
        """
        (() => {
          const c = globalThis.console || (globalThis.console = {});
          for (const level of ['log', 'info', 'warn', 'error', 'debug', 'trace']) {
            if (typeof c[level] !== 'function') c[level] = function () {};
          }
          void 0;
        })()
        """.trimIndent()
    )
}

/**
 * 把一段 JS 表达式（通常是 `({args}) => {...}` 箭头函数）安装到 globalThis。
 * 脚本通过 JsonPrimitive 序列化后作为字符串字面量注入，绝不直接拼接。
 */
internal suspend fun QuickJs.installSpresetFunction(name: String, script: String) {
    val nameLiteral = JsonPrimitive(name).toString()
    val scriptLiteral = JsonPrimitive(script).toString()
    evaluate<Unit>(
        """
        (() => {
          const fn = (0, eval)($scriptLiteral);
          if (typeof fn !== 'function') {
            throw new Error('SPreset script did not evaluate to a function');
          }
          globalThis[$nameLiteral] = fn;
          void 0;
        })()
        """.trimIndent()
    )
}

/**
 * 把脚本返回值统一转成 `String?` 的 JS 片段。
 *
 * 契约与旧实现一致：字符串原样、对象/数组/数字 `JSON.stringify`、null/undefined → null。
 * 调用方需保证 `value` 已经在作用域里。
 */
private const val RESULT_TO_STRING_BODY = """
    if (value === undefined || value === null) return null;
    if (typeof value === 'string') return value;
    return JSON.stringify(value);
"""

/**
 * 调用已安装函数，参数必须是 JSON 字符串，内部 JSON.parse 后传入。
 * 返回值：字符串原样返回；对象/数组/数字 JSON.stringify；null/undefined 返回 null。
 *
 * **会等待 async 脚本返回的 Promise 结算。**
 */
internal suspend fun QuickJs.callSpresetFunction(name: String, argsJson: String): String? {
    val nameLiteral = JsonPrimitive(name).toString()
    val argsLiteral = JsonPrimitive(argsJson).toString()
    return evaluate<String?>(
        """
        await (async () => {
          const args = JSON.parse($argsLiteral);
          const fn = globalThis[$nameLiteral];
          if (typeof fn !== 'function') {
            throw new Error('SPreset function is not installed');
          }
          const value = await fn(args);
          $RESULT_TO_STRING_BODY
        })()
        """.trimIndent()
    )
}

/**
 * 调用已安装函数并把单个字符串作为参数传入。
 * ChatSquash 的 squashed_post_script 形态是 `content => {...}`，必须传字符串而不是对象。
 *
 * **会等待 async 脚本返回的 Promise 结算。**
 */
internal suspend fun QuickJs.callSpresetFunctionWithStringArg(
    name: String,
    argument: String,
): String? {
    val nameLiteral = JsonPrimitive(name).toString()
    val argumentLiteral = JsonPrimitive(argument).toString()
    return evaluate<String?>(
        """
        await (async () => {
          const fn = globalThis[$nameLiteral];
          if (typeof fn !== 'function') {
            throw new Error('SPreset function is not installed');
          }
          const value = await fn($argumentLiteral);
          $RESULT_TO_STRING_BODY
        })()
        """.trimIndent()
    )
}

/**
 * 执行一段 JS **表达式**，等待其顶层 Promise 结算后按统一契约转成 `String?`。
 *
 * 用于工具 action / 动态工具定义这类需要 `await` 用户脚本的场合。
 *
 * ## 为什么必须显式 `await`
 *
 * quickjs-kt 1.0.15 的 `evaluate` **不会自动等待脚本最后返回的 Promise**。
 * 实测（见 `SpresetOutputPromiseTest`）：
 *
 * | JS 表达式 | 裸 `evaluate<String?>` |
 * |---|---|
 * | `'hello'` | `hello` |
 * | `Promise.resolve('x')` | `Promise { <state>: "fulfilled" }` |
 * | `(async () => 'x')()` | `Promise { <state>: "fulfilled" }` |
 *
 * 只要 Promise 在 JNI 取结果前已经 settle，桥层就把 **Promise 对象本身**
 * 交给 `TypeConverter`，转 String 时调用 `toString()` 得到
 * `Promise { <state>: "fulfilled" }`。
 *
 * 线上故障现场（2026-11-18 18:30，Meowssiah 2.0.11）：
 * ```
 * [event] stream_token_received "Promise { <state>: \"fulfilled\" }"
 * ```
 * 模型思考正常、正文只有一个假 token —— 就是 `OutputPreprocessing` 的
 * `callSpresetFunction` 把 Promise 字符串化后当成唯一一段正文发了出去。
 *
 * ## 可行性与代价
 *
 * `evaluate` 以 **module 语义** 编译脚本，因此支持顶层 `await`
 * （`search/CustomJsSearchService` 一直依赖 `JSON.stringify(await ...)` 且测试通过）。
 * 我们只需把调用包进 `async` IIFE 并 `await` 一次，无需在 Kotlin 侧泵微任务。
 *
 * 注意：若脚本返回一个永不 settle 的 Promise，这里会一直挂到
 * [withSpresetQuickJs] 的外层超时；这是期望行为（与浏览器一致）。
 */
internal suspend fun QuickJs.evaluateSpresetScriptValue(script: String): String? {
    val scriptLiteral = JsonPrimitive(script).toString()
    return evaluate<String?>(
        """
        await (async () => {
          const value = await (0, eval)($scriptLiteral);
          $RESULT_TO_STRING_BODY
        })()
        """.trimIndent()
    )
}

/** 单次执行用的 QuickJS 作用域；quickJs() 会在 finally 中 close()。 */
internal suspend fun <T> withSpresetQuickJs(
    timeoutMillis: Long = SPRESET_JS_TIMEOUT_MS,
    block: suspend QuickJs.() -> T?,
): T? {
    return withTimeoutOrNull(timeoutMillis + 1_500L) {
        quickJs(Dispatchers.Default) {
            memoryLimit = SPRESET_JS_MEMORY_LIMIT_BYTES
            maxStackSize = SPRESET_JS_MAX_STACK_BYTES
            evaluationTimeoutMillis = timeoutMillis
            installSpresetConsole()
            block()
        }
    }
}

/**
 * 所有公开入口统一使用：脚本异常一律降级，协程取消继续向上传播。
 */
internal suspend fun <T> presetRunCatching(default: T, block: suspend () -> T): T {
    return runCatching { block() }.getOrElse { error ->
        if (error is CancellationException) throw error
        default
    }
}

internal fun parseJsonElementOrNull(text: String?): JsonElement? {
    if (text.isNullOrBlank()) return null
    return runCatching { spresetJson.parseToJsonElement(text) }.getOrNull()
}

internal fun parseJsonObjectOrEmpty(text: String?): JsonObject {
    return parseJsonElementOrNull(text)?.let {
        runCatching { it.jsonObject }.getOrNull()
    } ?: JsonObject(emptyMap())
}

/** 流式/toolCalls 场景：优先解析为 JSON 对象，失败则保留原始字符串。 */
internal fun parseArgumentsElement(arguments: String): JsonElement {
    if (arguments.isBlank()) return JsonObject(emptyMap())
    return parseJsonElementOrNull(arguments) ?: JsonPrimitive(arguments)
}
