package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JavascriptToolTest {
    private suspend fun evaluate(code: String, timeoutMillis: Long = 2_000) =
        Json.parseToJsonElement(evaluateJavascript(code, timeoutMillis)).jsonObject

    @Test(timeout = 20_000)
    fun `preserves completion values and logs`() = runBlocking {
        assertEquals("3", evaluate("1 + 2")["result"]?.jsonPrimitive?.content)
        assertEquals("10", evaluate("const x = 5; x * 2")["result"]?.jsonPrimitive?.content)
        assertEquals("{\"value\":42}", evaluate("({value: 42})")["result"]?.jsonPrimitive?.content)
        assertEquals("[1,2]", evaluate("[1, 2]")["result"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, evaluate("undefined")["result"])
        val output = evaluate("console.log('你好', 42); console.warn('注意'); '😀'")
        assertEquals("[LOG] 你好 42\n[WARN] 注意", output["logs"]?.jsonPrimitive?.content)
        assertEquals("😀", output["result"]?.jsonPrimitive?.content)
    }

    /**
     * 回归：用户代码返回 Promise 时必须 await 后再序列化。
     *
     * 修复前 `JSON.stringify(Promise)` 会得到 `"{}"`，用户看到的是一对空花括号，
     * 与 SPreset 那个 `Promise { <state>: "fulfilled" }` 是同一类 bug。
     */
    @Test(timeout = 20_000)
    fun `awaits async results before serializing`() = runBlocking {
        assertEquals(
            "{\"value\":42}",
            evaluate("(async () => ({value: 42}))()")["result"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "async-text",
            evaluate("(async () => { await null; return 'async-text'; })()")["result"]?.jsonPrimitive?.content,
        )
        // 日志在 await 前后都要保留
        val out = evaluate("console.log('before'); (async () => { await null; console.log('after'); return 'done'; })()")
        assertEquals("done", out["result"]?.jsonPrimitive?.content)
        assertEquals("[LOG] before\n[LOG] after", out["logs"]?.jsonPrimitive?.content)
    }

    @Test(timeout = 20_000)
    fun `interrupts infinite loops including during serialization`() = runBlocking {
        for (code in listOf(
            "while (true) {}",
            "({toJSON() { while (true) {} }})",
            "while (true) { console.log('spam'); }",
        )) {
            assertTrue(evaluate(code, 100)["error"]!!.jsonPrimitive.content.contains("timed out"))
            assertEquals("2", evaluate("1 + 1")["result"]?.jsonPrimitive?.content)
        }
    }

    @Test(timeout = 20_000)
    fun `bounds logs and output`() = runBlocking {
        val logs = evaluate("for (let i = 0; i < 10000; i++) console.log('1234567890'); 1")
        assertTrue(logs["logs"]!!.jsonPrimitive.content.length < 66_000)
        assertTrue(logs["logs"]!!.jsonPrimitive.content.endsWith("[Logs truncated]"))
        assertTrue(evaluate("'x'.repeat(2 * 1024 * 1024)").containsKey("error"))
    }

    @Test(timeout = 20_000)
    fun `memory and stack exhaustion return errors`() = runBlocking {
        assertTrue(evaluate("new ArrayBuffer(128 * 1024 * 1024)").containsKey("error"))
        assertTrue(evaluate("function recurse() { return recurse(); } recurse();").containsKey("error"))
        assertTrue(evaluate("throw new Error('failure')").containsKey("error"))
        assertEquals("3", evaluate("1 + 2")["result"]?.jsonPrimitive?.content)
    }

    @Test(timeout = 20_000)
    fun `cancellation stops native execution`() = runBlocking {
        val execution = async(Dispatchers.Default) {
            evaluateJavascript("while (true) {}", timeoutMillis = 60_000)
        }
        delay(250)
        execution.cancel()
        withTimeout(2_000) { execution.join() }
        assertTrue(execution.isCancelled)
    }
}
