package me.rerere.rikkahub.data.st.preset

import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：SPreset 脚本返回 Promise 时，必须等它结算，不能把
 * `Promise { <state>: "fulfilled" }` 当成正文发出去。
 *
 * 线上故障现场（2026-11-18 18:30）：
 * ```
 * [event] stream_token_received "Promise { <state>: \"fulfilled\" }"
 * ```
 * 模型思考正常、正文只有一个假 token。
 */
class SpresetOutputPromiseTest {

    /** Meowssiah 2.0.11 `extensions.SPreset.OutputPreprocessing.script` 原文。 */
    private val meowssiahOutputScript = """
        ({ buffer, final, state = {} }) => {
          console.log(buffer)
          if (true) {
            return {
              output: buffer,
              hold: '',
              state: { ...state, accepted: true },
            };
          }

          return {
            output: buffer,
            hold: '',
            state: { ...state, rejected: true },
          };
        }
    """.trimIndent()

    /** Meowssiah 2.0.11 `toolCallFormatter.script` 的等价精简版（逻辑逐字保留）。 */
    private val meowssiahFormatterScript = """
        ({ calls, defaultText }) => {
          if (!Array.isArray(calls) || calls.length === 0) {
            return defaultText ?? '';
          }
          return calls.map(call => {
            let args = call?.arguments;
            if (typeof args === 'string') {
              try { args = JSON.parse(args); } catch { args = {}; }
            }
            if (!args || typeof args !== 'object') return '';
            return String(args.content ?? '');
          }).filter(Boolean).join('\n\n');
        }
    """.trimIndent()

    // ------------------------------------------------------------ L1 对照

    /**
     * 对照组：证明 quickjs-kt 1.0.15 的裸 `evaluate` **不会** await Promise。
     *
     * 这不是我们的 bug，而是上游库的行为。这个测试把行为钉死：一旦将来升级
     * quickjs-kt 修好了，它会失败并提醒我们简化 SpresetJsRuntime 的泵微任务逻辑。
     */
    @Test
    fun bareEvaluateDoesNotAwaitPromise() = runBlocking {
        val result = quickJs(Dispatchers.Default) {
            evaluationTimeoutMillis = SPRESET_JS_TIMEOUT_MS
            evaluate<String?>("(async () => 'x')()")
        }
        println("[L1 对照] bare evaluate => <$result>")
        assertTrue(
            "quickjs-kt 似乎已修复 Promise 等待，可简化泵微任务逻辑。实际 <$result>",
            result.orEmpty().contains("Promise {"),
        )
    }

    /** 我们的入口必须拿到 await 之后的值（与上面形成对照）。 */
    @Test
    fun ourEntryPointAwaitsPromise() = runBlocking {
        val result = quickJs(Dispatchers.Default) {
            memoryLimit = SPRESET_JS_MEMORY_LIMIT_BYTES
            maxStackSize = SPRESET_JS_MAX_STACK_BYTES
            evaluationTimeoutMillis = SPRESET_JS_TIMEOUT_MS
            installSpresetConsole()
            evaluateSpresetScriptValue(
                """
                (async () => {
                  const value = await (async () => ({ output: 'hello' }))();
                  return JSON.stringify(value);
                })()
                """.trimIndent()
            )
        }
        println("[L1] our evaluateSpresetScriptValue => <$result>")
        assertFalse(
            "我们的入口返回了 Promise 的字符串化结果：<$result>",
            result.orEmpty().contains("Promise {"),
        )
        assertEquals("""{"output":"hello"}""", result)
    }

    // ------------------------------------------------------------ L4 契约

    /** 用真实的 installSpresetFunction + callSpresetFunction 走一遍。 */
    @Test
    fun callSpresetFunctionAwaitsArrowFunctionResult() = runBlocking {
        val result = quickJs(Dispatchers.Default) {
            memoryLimit = SPRESET_JS_MEMORY_LIMIT_BYTES
            maxStackSize = SPRESET_JS_MAX_STACK_BYTES
            evaluationTimeoutMillis = SPRESET_JS_TIMEOUT_MS
            installSpresetConsole()
            installSpresetFunction("__t_output", meowssiahOutputScript)
            callSpresetFunction("__t_output", """{"buffer":"hello","final":false,"state":{}}""")
        }
        println("[L4] callSpresetFunction => <$result>")
        assertNotNull("callSpresetFunction 不应返回 null", result)
        assertFalse(
            "callSpresetFunction 返回了 Promise 的字符串化结果：<$result>",
            result!!.contains("Promise {"),
        )
        assertTrue("应包含 buffer 内容，实际 <$result>", result.contains("hello"))
    }

    /** async 脚本（真正返回 Promise 的那种）也必须被 await。 */
    @Test
    fun callSpresetFunctionAwaitsAsyncScript() = runBlocking {
        val asyncScript = """
            async ({ buffer }) => {
              await null;
              return { output: buffer + '!', hold: '', state: {} };
            }
        """.trimIndent()
        val result = quickJs(Dispatchers.Default) {
            memoryLimit = SPRESET_JS_MEMORY_LIMIT_BYTES
            maxStackSize = SPRESET_JS_MAX_STACK_BYTES
            evaluationTimeoutMillis = SPRESET_JS_TIMEOUT_MS
            installSpresetConsole()
            installSpresetFunction("__t_async", asyncScript)
            callSpresetFunction("__t_async", """{"buffer":"abc"}""")
        }
        println("[L4] async script => <$result>")
        assertFalse("async 脚本返回了 Promise 字符串：<$result>", result.orEmpty().contains("Promise {"))
        assertTrue("应包含 abc!，实际 <$result>", result.orEmpty().contains("abc!"))
    }

    // ------------------------------------------------------------ L5 活性

    /** 走 SpresetCompat.processOutput 真实入口：buffer 变了，output 必须跟着变。 */
    @Test
    fun processOutputReflectsBufferChanges() = runBlocking {
        val cfg = StSpresetOutputPreprocessing(
            enabled = true,
            script = meowssiahOutputScript,
            consumeToolCalls = true,
            toolCallFormatter = StSpresetOutputToolCallFormatter(
                enabled = true,
                script = meowssiahFormatterScript,
            ),
        )

        val first = SpresetCompat.processOutput(
            p = cfg,
            buffer = "第一段",
            final = false,
            stateJson = "{}",
        )
        val second = SpresetCompat.processOutput(
            p = cfg,
            buffer = "第一段第二段",
            final = false,
            stateJson = "{}",
        )

        println("[L5] processOutput#1 => ${first?.output}")
        println("[L5] processOutput#2 => ${second?.output}")

        assertNotNull("processOutput 不应返回 null（脚本本身没有抛错）", first)
        assertEquals("第一段", first!!.output)
        assertEquals("第一段第二段", second!!.output)
    }

    /** 整条流：真实 TextDelta 必须逐段透出，而不是被压成一个假 token。 */
    @Test
    fun streamEngineEmitsRealTextNotPromiseString() = runBlocking {
        val config = StSpresetConfig(
            outputPreprocessing = StSpresetOutputPreprocessing(
                enabled = true,
                script = meowssiahOutputScript,
                consumeToolCalls = true,
                toolCallFormatter = StSpresetOutputToolCallFormatter(
                    enabled = true,
                    script = meowssiahFormatterScript,
                ),
            ),
        )

        val input = flowOf(
            StreamChunk.TextStart("t1"),
            StreamChunk.TextDelta("t1", "你好"),
            StreamChunk.TextDelta("t1", "，世界"),
            StreamChunk.TextEnd("t1"),
            StreamChunk.Finish("stop"),
        )

        val out = SpresetCompat.processStream(input, config).toList()
        val visibleText = out.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text }
        println("[L5] stream visible text => <$visibleText>")
        println("[L5] stream chunks => ${out.map { it::class.simpleName }}")

        assertFalse(
            "正文里出现了 Promise 的字符串化结果：<$visibleText>",
            visibleText.contains("Promise {"),
        )
        assertEquals("你好，世界", visibleText)
    }

    /** 工具调用必须被 formatter 转成正文，而不是原样漏出 JSON。 */
    @Test
    fun streamEngineFormatsToolCallsIntoText() = runBlocking {
        val config = StSpresetConfig(
            outputPreprocessing = StSpresetOutputPreprocessing(
                enabled = true,
                script = meowssiahOutputScript,
                consumeToolCalls = true,
                toolCallFormatter = StSpresetOutputToolCallFormatter(
                    enabled = true,
                    script = meowssiahFormatterScript,
                ),
            ),
        )

        val input = flowOf(
            StreamChunk.ToolCallStart("c1", "soliumbra_reply"),
            StreamChunk.ToolCallDelta("c1", inputDelta = """{"content":"正文在这里"}"""),
            StreamChunk.ToolCallEnd("c1"),
            StreamChunk.Finish("tool_calls"),
        )

        val out = SpresetCompat.processStream(input, config).toList()
        val visibleText = out.filterIsInstance<StreamChunk.TextDelta>().joinToString("") { it.text }
        println("[L5] tool-formatted text => <$visibleText>")

        assertFalse(
            "正文里出现了 Promise 的字符串化结果：<$visibleText>",
            visibleText.contains("Promise {"),
        )
        assertEquals("正文在这里", visibleText)
    }

    // ------------------------------------------------------------ L6 真实脚本

    /** 真实 Meowssiah 预设文件里的 OutputPreprocessing 脚本也必须跑通。 */
    @Test
    fun realMeowssiahPresetOutputScriptWorks() = runBlocking {
        val file = listOf(
            java.io.File("src/test/resources/presets/Meowssiah-2.0.11.json"),
            java.io.File("app/src/test/resources/presets/Meowssiah-2.0.11.json"),
        ).firstOrNull { it.exists() } ?: error("Meowssiah preset not found")

        val root = spresetJson.parseToJsonElement(file.readText()) as JsonObject
        val spreset = (root["extensions"] as? JsonObject)?.get("SPreset") as? JsonObject
        assertNotNull("真实预设里应有 extensions.SPreset", spreset)

        val outputNode = spreset!!["OutputPreprocessing"] as? JsonObject
        assertNotNull("真实预设里应有 OutputPreprocessing", outputNode)

        val script = (outputNode!!["script"] as JsonPrimitive).content
        val enabled = (outputNode["enabled"] as JsonPrimitive).content.toBoolean()
        assertTrue("真实预设的 OutputPreprocessing 应启用", enabled)

        val processed = SpresetCompat.processOutput(
            p = StSpresetOutputPreprocessing(enabled = true, script = script),
            buffer = "真实预设测试文本",
            final = false,
            stateJson = "{}",
        )
        println("[L6] real preset processOutput => ${processed?.output}")
        assertNotNull("真实预设脚本应能执行", processed)
        assertFalse(
            "真实预设脚本返回了 Promise 字符串：<${processed!!.output}>",
            processed.output.contains("Promise {"),
        )
        assertEquals("真实预设测试文本", processed.output)
    }

    /** 真实 Meowssiah 的 toolCallFormatter 也必须能执行（它同样是异步形态）。 */
    @Test
    fun realMeowssiahFormatterScriptWorks() = runBlocking {
        val file = listOf(
            java.io.File("src/test/resources/presets/Meowssiah-2.0.11.json"),
            java.io.File("app/src/test/resources/presets/Meowssiah-2.0.11.json"),
        ).firstOrNull { it.exists() } ?: error("Meowssiah preset not found")

        val root = spresetJson.parseToJsonElement(file.readText()) as JsonObject
        val spreset = (root["extensions"] as? JsonObject)?.get("SPreset") as JsonObject
        val outputNode = spreset["OutputPreprocessing"] as JsonObject
        val formatter = outputNode["toolCallFormatter"] as JsonObject
        val script = (formatter["script"] as JsonPrimitive).content

        val callsJson = """
            [{"id":"c1","name":"soliumbra_reply","arguments":{"content":"猫猫正文"}}]
        """.trimIndent()
        val formatted = SpresetCompat.formatToolCalls(
            p = StSpresetOutputPreprocessing(
                enabled = true,
                script = (outputNode["script"] as JsonPrimitive).content,
                consumeToolCalls = true,
                toolCallFormatter = StSpresetOutputToolCallFormatter(enabled = true, script = script),
            ),
            callsJson = callsJson,
        )
        println("[L6] real formatter => <$formatted>")
        assertNotNull("真实 formatter 应能执行", formatted)
        assertFalse("formatter 返回了 Promise 字符串：<$formatted>", formatted!!.contains("Promise {"))
        assertEquals("猫猫正文", formatted)
    }
}
