package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import java.io.File
import kotlin.math.sqrt

/**
 * [v248] ST 向量库的宿主实现（`extensions/vectors/index.js` 的后端落点）。
 *
 * ## 为什么需要它
 * ST 的 vectors 扩展所有操作都走 `/api/vector/` 系列，由酒馆后端调 embedding 服务。
 * 用户明确不做 provider 请求 API，所以那些端点在 RikkaHub 里根本不存在 ——
 * 扩展就算被加载，`getSavedHashes()` 也只会拿到 404。
 *
 * 这里用宿主**已有**的 embedding 能力（`ProviderManager.generateEmbedding`）
 * 实现同样 6 个操作，数据落在沙箱 `st-user-files/vectors/<collectionId>.json`。
 *
 * ## 数据结构
 * 每个集合一个 JSON 文件：
 * ```json
 * {"items":[{"hash":123,"text":"...","index":0,"embedding":[0.1,...]}]}
 * ```
 * 字段与 ST 的 `{hash, text, index}` 对齐，`embedding` 是我们额外存的（ST 存在后端）。
 *
 * ## 硬约束
 * - 所有方法都是 **suspend**：调用方（JavaBridge）必须放到 IO 协程，禁止 runBlocking
 * - 集合名做路径穿越防护（只允许 `[A-Za-z0-9_-]`）
 * - embedding 失败时**不**让整个操作失败：条目照常存，只是没有向量（查询时跳过）
 */
object TavernVectorStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** 集合名白名单：ST 的 collectionId 形如 `world_123456` / `<hash>_<hash>`。 */
    private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,120}$")

    private fun vectorsDir(): File? {
        val root = TavernFileBridge.rootDirectory() ?: return null
        return File(root, "vectors").also { if (!it.exists()) it.mkdirs() }
    }

    private fun collectionFile(collectionId: String): File? {
        if (!SAFE_ID.matches(collectionId)) return null
        return vectorsDir()?.let { File(it, "$collectionId.json") }
    }

    // ---------------- 序列化 ----------------

    private data class Item(
        val hash: Long,
        val text: String,
        val index: Int,
        val embedding: List<Float>?,
    )

    private fun load(file: File): MutableList<Item> {
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            root["items"]?.jsonArray?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val hash = o["hash"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: return@mapNotNull null
                val text = o["text"]?.jsonPrimitive?.contentOrNull ?: ""
                val index = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                val emb = (o["embedding"] as? JsonArray)?.mapNotNull {
                    it.jsonPrimitive.doubleOrNull?.toFloat()
                }?.takeIf { it.isNotEmpty() }
                Item(hash, text, index, emb)
            }?.toMutableList() ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    private fun save(file: File, items: List<Item>) {
        val obj = buildJsonObject {
            putJsonArray("items") {
                for (it in items) {
                    add(buildJsonObject {
                        put("hash", it.hash)
                        put("text", it.text)
                        put("index", it.index)
                        if (it.embedding != null) {
                            putJsonArray("embedding") { it.embedding.forEach { v -> add(v) } }
                        }
                    })
                }
            }
        }
        file.writeText(obj.toString())
    }

    // ---------------- embedding ----------------

    private suspend fun embed(texts: List<String>): List<List<Float>?> {
        if (texts.isEmpty()) return emptyList()
        return try {
            val settingsStore = org.koin.java.KoinJavaComponent.get<SettingsStore>(
                SettingsStore::class.java
            )
            val settings = settingsStore.awaitLoaded()
            val model = settings.findModelById(settings.embeddingModelId)
                ?: settings.findModelById(settings.chatModelId)
                ?: return texts.map { null }
            val provider = model.findProvider(settings.providers) ?: return texts.map { null }
            val manager = org.koin.java.KoinJavaComponent.get<ProviderManager>(
                ProviderManager::class.java
            )
            val impl = manager.getProviderByType(provider)
            val out = arrayOfNulls<List<Float>>(texts.size)
            // 分批：部分后端限制 batch size
            texts.withIndex().chunked(32).forEach { chunk ->
                val resp = impl.generateEmbedding(
                    provider,
                    EmbeddingGenerationParams(model = model, input = chunk.map { it.value }),
                )
                resp.embeddings.forEachIndexed { i, vec ->
                    val slot = chunk.getOrNull(i)?.index ?: return@forEachIndexed
                    out[slot] = vec
                }
            }
            out.toList()
        } catch (_e: Exception) {
            // 没配 embedding 模型 / 网络失败：条目照存，查询时跳过
            texts.map { null }
        }
    }

    private fun cosine(a: List<Float>, b: List<Float>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom <= 1e-12) 0.0 else dot / denom
    }

    // ---------------- 操作 ----------------

    /** 入口：op + payload → `{"ok":true,"data":...}`。 */
    suspend fun execute(op: String, payloadJson: String): String {
        val payload = runCatching {
            json.parseToJsonElement(payloadJson.ifBlank { "{}" }).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }

        fun str(key: String): String =
            payload[key]?.jsonPrimitive?.contentOrNull.orEmpty()

        fun int(key: String, def: Int): Int =
            payload[key]?.jsonPrimitive?.intOrNull ?: def

        return when (op.lowercase()) {
            "list" -> {
                val file = collectionFile(str("collectionId"))
                    ?: return err("invalid collectionId")
                val items = load(file)
                ok(buildJsonArray { items.forEach { add(JsonPrimitive(it.hash)) } })
            }

            "insert" -> {
                val cid = str("collectionId")
                val file = collectionFile(cid) ?: return err("invalid collectionId")
                val arr = payload["items"]?.jsonArray ?: return err("missing items")
                val incoming = arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val hash = o["hash"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: return@mapNotNull null
                    Item(
                        hash = hash,
                        text = o["text"]?.jsonPrimitive?.contentOrNull ?: "",
                        index = o["index"]?.jsonPrimitive?.intOrNull ?: 0,
                        embedding = null,
                    )
                }
                if (incoming.isEmpty()) return err("no valid items")

                val embeddings = embed(incoming.map { it.text })
                val withEmb = incoming.mapIndexed { i, it -> it.copy(embedding = embeddings.getOrNull(i)) }

                val existing = load(file)
                val byHash = existing.associateBy { it.hash }.toMutableMap()
                withEmb.forEach { byHash[it.hash] = it }
                save(file, byHash.values.sortedBy { it.hash })
                ok(JsonPrimitive(byHash.size))
            }

            "delete" -> {
                val file = collectionFile(str("collectionId"))
                    ?: return err("invalid collectionId")
                val hashes = payload["hashes"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.toLongOrNull() }
                    ?.toSet()
                    ?: emptySet()
                if (hashes.isEmpty()) return err("missing hashes")
                val items = load(file)
                val kept = items.filter { it.hash !in hashes }
                save(file, kept)
                ok(JsonPrimitive(items.size - kept.size))
            }

            "purge" -> {
                val file = collectionFile(str("collectionId"))
                    ?: return err("invalid collectionId")
                val n = if (file.exists()) { load(file).size.also { file.delete() } } else 0
                ok(JsonPrimitive(n))
            }

            "query" -> {
                val file = collectionFile(str("collectionId"))
                    ?: return err("invalid collectionId")
                val text = str("searchText")
                val topK = int("topK", 5).coerceIn(1, 100)
                val threshold = payload["threshold"]?.jsonPrimitive?.doubleOrNull
                queryOne(file, text, topK, threshold)
            }

            "query-multi" -> {
                val ids = payload["collectionIds"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val text = str("searchText")
                val topK = int("topK", 5).coerceIn(1, 100)
                val threshold = payload["threshold"]?.jsonPrimitive?.doubleOrNull
                val out = buildJsonObject {
                    for (id in ids) {
                        val file = collectionFile(id) ?: continue
                        put(id, queryOneData(file, text, topK, threshold))
                    }
                }
                ok(out)
            }

            else -> err("unknown vector op: $op")
        }
    }

    private suspend fun queryOne(file: File, text: String, topK: Int, threshold: Double?): String {
        return ok(queryOneData(file, text, topK, threshold))
    }

    private suspend fun queryOneData(
        file: File,
        text: String,
        topK: Int,
        threshold: Double?,
    ): JsonObject {
        val items = load(file).filter { it.embedding != null }
        if (items.isEmpty() || text.isBlank()) {
            return buildJsonObject {
                putJsonArray("hashes") {}
                putJsonArray("metadata") {}
            }
        }
        val qv = embed(listOf(text)).firstOrNull()
            ?: return buildJsonObject {
                putJsonArray("hashes") {}
                putJsonArray("metadata") {}
            }
        val cut = threshold ?: 0.0
        val scored = items
            .map { it to cosine(qv, it.embedding!!) }
            .filter { it.second >= cut }
            .sortedByDescending { it.second }
            .take(topK)

        return buildJsonObject {
            putJsonArray("hashes") { scored.forEach { add(JsonPrimitive(it.first.hash)) } }
            putJsonArray("metadata") {
                scored.forEach { (item, score) ->
                    add(buildJsonObject {
                        put("hash", item.hash)
                        put("text", item.text)
                        put("index", item.index)
                        put("score", score)
                    })
                }
            }
        }
    }

    private fun ok(data: kotlinx.serialization.json.JsonElement): String =
        buildJsonObject {
            put("ok", true)
            put("data", data)
        }.toString()

    private fun err(msg: String): String =
        buildJsonObject {
            put("ok", false)
            put("error", msg)
        }.toString()
}
