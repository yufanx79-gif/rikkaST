package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v229 A1] `/api/backends/chat-completions/{status,generate}` 兼容路由的行为级单测。
 *
 * 为什么必须有：v228 之前宿主对 `/api/` 零命中 -> 一律 404 -> JSR 的
 * `TavernHelper.generateRaw()` / `generate()` 全废。这里锁定「路由 + 请求体解析 +
 * SSE 分块顺序 + 错误体」四件事，防回归。
 *
 * 真源（只读参考）：
 *  - `JS-Slash-Runner/src/function/generate/index.ts:46`（status）
 *  - `JS-Slash-Runner/src/function/generate/responseGenerator.ts:343`（stream）/ `:438`（non-stream）
 */
class TavernChatCompletionsApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---------------------------------------------------------------- 路由

    @Test
    fun `route matches the two JSR endpoints exactly`() {
        assertEquals(
            TavernChatCompletionsApi.Endpoint.STATUS,
            TavernChatCompletionsApi.routeOf("/api/backends/chat-completions/status"),
        )
        assertEquals(
            TavernChatCompletionsApi.Endpoint.GENERATE,
            TavernChatCompletionsApi.routeOf("/api/backends/chat-completions/generate"),
        )
    }

    @Test
    fun `route ignores unrelated asset paths so the old chain is untouched`() {
        assertNull(TavernChatCompletionsApi.routeOf("/version"))
        assertNull(TavernChatCompletionsApi.routeOf("/st-runtime/runtime.js"))
        assertNull(TavernChatCompletionsApi.routeOf("/api/backends/chat-completions"))
        assertNull(TavernChatCompletionsApi.routeOf(null))
    }

    @Test
    fun `endpoint constants stay byte-identical to what JSR fetches`() {
        assertEquals("/api/backends/chat-completions/status", TavernChatCompletionsApi.STATUS)
        assertEquals("/api/backends/chat-completions/generate", TavernChatCompletionsApi.GENERATE)
    }

    // ---------------------------------------------------------------- /generate 请求解析

    @Test
    fun `parseGenerateRequest reads model stream and sampling params`() {
        val body = """{"type":"normal","messages":[{"role":"user","content":"hi"}],"model":"gpt-4o","temperature":0.7,"top_p":0.95,"max_tokens":256,"stream":true}"""
        val req = TavernChatCompletionsApi.parseGenerateRequest(body)
        assertNotNull(req)
        req!!
        assertEquals("gpt-4o", req.modelId)
        assertTrue(req.stream)
        assertEquals(0.7, req.temperature!!, 1e-9)
        assertEquals(0.95, req.topP!!, 1e-9)
        assertEquals(256, req.maxTokens)
        assertEquals(1, req.messages.size)
        assertEquals("user", req.messages[0].role)
        assertEquals("hi", req.messages[0].content)
    }

    @Test
    fun `parseGenerateRequest defaults stream to false when absent`() {
        val req = TavernChatCompletionsApi.parseGenerateRequest(
            """{"messages":[{"role":"assistant","content":"yo"}]}"""
        )
        assertNotNull(req)
        assertEquals(false, req!!.stream)
        assertEquals("", req.modelId)
    }

    @Test
    fun `parseGenerateRequest rejects malformed or message-less bodies`() {
        assertNull(TavernChatCompletionsApi.parseGenerateRequest(null))
        assertNull(TavernChatCompletionsApi.parseGenerateRequest(""))
        assertNull(TavernChatCompletionsApi.parseGenerateRequest("not json"))
        assertNull(TavernChatCompletionsApi.parseGenerateRequest("{}"))
        assertNull(TavernChatCompletionsApi.parseGenerateRequest("""{"messages":[]}"""))
    }

    @Test
    fun `parseGenerateRequest flattens multimodal content parts`() {
        val body = """{"messages":[{"role":"user","content":[{"type":"text","text":"part one"},{"type":"text","text":"part two"}]}]}"""
        val req = TavernChatCompletionsApi.parseGenerateRequest(body)
        assertNotNull(req)
        assertEquals("part one\npart two", req!!.messages[0].content)
    }

    // ---------------------------------------------------------------- model / role

    @Test
    fun `pickModel prefers exact then case-insensitive then first`() {
        val models = listOf("gpt-4o", "claude-3-5-sonnet")
        assertEquals("claude-3-5-sonnet", TavernChatCompletionsApi.pickModel("claude-3-5-sonnet", models))
        assertEquals("claude-3-5-sonnet", TavernChatCompletionsApi.pickModel("CLAUDE-3-5-SONNET", models))
        assertEquals("gpt-4o", TavernChatCompletionsApi.pickModel("unknown-model", models))
        assertEquals("gpt-4o", TavernChatCompletionsApi.pickModel("", models))
        assertNull(TavernChatCompletionsApi.pickModel("gpt-4o", emptyList()))
    }

    @Test
    fun `normalizeRole maps unknown roles to user so providers never throw`() {
        assertEquals("system", TavernChatCompletionsApi.normalizeRole("system"))
        assertEquals("assistant", TavernChatCompletionsApi.normalizeRole("ASSISTANT"))
        assertEquals("user", TavernChatCompletionsApi.normalizeRole("tool"))
        assertEquals("user", TavernChatCompletionsApi.normalizeRole("wat"))
    }

    // ---------------------------------------------------------------- /status 响应

    @Test
    fun `statusResponse exposes data list that JSR getModelList reads`() {
        val body = TavernChatCompletionsApi.statusResponse(
            models = listOf("gpt-4o", "gpt-4o-mini"),
            currentModel = "gpt-4o",
        )
        val root = json.parseToJsonElement(body).jsonObject
        val ids = root["data"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), ids)
        assertEquals("gpt-4o", root["model"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------- /generate 响应

    @Test
    fun `streamChunk is the OpenAI delta shape getStreamingReply understands`() {
        val line = TavernChatCompletionsApi.streamChunk(
            id = "chatcmpl-1",
            model = "gpt-4o",
            deltaText = "Hello",
        )
        val root = json.parseToJsonElement(line).jsonObject
        assertEquals("chat.completion.chunk", root["object"]!!.jsonPrimitive.content)
        assertEquals("gpt-4o", root["model"]!!.jsonPrimitive.content)
        val choice = root["choices"]!!.jsonArray[0].jsonObject
        assertEquals("Hello", choice["delta"]!!.jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `streamChunk can carry only finish_reason without content`() {
        val line = TavernChatCompletionsApi.streamChunk(
            id = "chatcmpl-2",
            model = "gpt-4o",
            deltaText = null,
            finishReason = "stop",
        )
        val choice = json.parseToJsonElement(line).jsonObject["choices"]!!.jsonArray[0].jsonObject
        assertEquals("stop", choice["finish_reason"]!!.jsonPrimitive.content)
        assertTrue(choice["delta"]!!.jsonObject.isEmpty())
    }

    @Test
    fun `fullCompletion is a non-streaming chat_completion body`() {
        val body = TavernChatCompletionsApi.fullCompletion("chatcmpl-3", "gpt-4o", "world")
        val root = json.parseToJsonElement(body).jsonObject
        assertEquals("chat.completion", root["object"]!!.jsonPrimitive.content)
        val msg = root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        assertEquals("assistant", msg["role"]!!.jsonPrimitive.content)
        assertEquals("world", msg["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sse framing keeps the data prefix and double newline JSR parses`() {
        val chunk = TavernChatCompletionsApi.streamChunk("id", "m", "x")
        val sse = TavernChatCompletionsApi.sseData(chunk)
        assertTrue(sse.startsWith("data: "))
        assertTrue(sse.endsWith("\n\n"))
        // 一行 SSE = 一行 data:；分块内部绝不能出现裸换行（否则 JSR 的 event stream 解析会断）
        assertEquals(1, sse.trim().split("\n").size)
        assertEquals("data: [DONE]\n\n", TavernChatCompletionsApi.SSE_DONE)
    }

    @Test
    fun `errorBody always returns a JSON error object（never an empty 500）`() {
        val body = TavernChatCompletionsApi.errorBody("boom")
        val err = json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject
        assertEquals("boom", err["message"]!!.jsonPrimitive.content)
        assertEquals("invalid_request_error", err["type"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------- SSE 分块顺序（端到端线序）

    @Test
    fun `stream sequence is delta then finish then DONE`() {
        val model = "gpt-4o"
        val out = StringBuilder()
        out.append(TavernChatCompletionsApi.sseData(TavernChatCompletionsApi.streamChunk("c", model, "Hel")))
        out.append(TavernChatCompletionsApi.sseData(TavernChatCompletionsApi.streamChunk("c", model, "lo")))
        out.append(
            TavernChatCompletionsApi.sseData(
                TavernChatCompletionsApi.streamChunk("c", model, null, finishReason = "stop"),
            )
        )
        out.append(TavernChatCompletionsApi.SSE_DONE)
        val frames = out.toString().split("\n\n").filter { it.isNotBlank() }
        assertEquals(4, frames.size)
        assertTrue(frames[0].contains("Hel"))
        assertTrue(frames[1].contains("lo"))
        assertTrue(frames[2].contains("stop"))
        assertEquals("data: [DONE]", frames[3])
    }
}
