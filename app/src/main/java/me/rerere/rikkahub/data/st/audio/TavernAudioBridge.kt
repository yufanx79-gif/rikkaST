package me.rerere.rikkahub.data.st.audio

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * 音频指令总线：JS 侧 `TavernHelper.playAudio(...)` → 宿主 ExoPlayer。
 *
 * 设计要点：
 *  - **单一入口** `dispatch(op, payloadJson)`，内部按 op 分发。
 *    为什么不用 N 个 @JavascriptInterface 方法：Android 的 JS 桥按「方法名+实参个数」
 *    解析，方法一多极易出现 argc 不匹配（历史事故：getMessageVariablesJson 传了 1 个实参
 *    而 Kotlin 侧是 0 形参，静默失败且无日志）。单一入口 + JSON payload 只有一种调用形态。
 *  - 每个 op 都 try/catch 并留痕，绝不把异常抛回 JS（否则卡片脚本整段崩）。
 *  - 播放器按需创建，`release()` 在会话结束时调用。
 */
object TavernAudioBridge {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val players = ConcurrentHashMap<String, TavernAudioPlayer>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun playerFor(scopeKey: String): TavernAudioPlayer? {
        val ctx = appContext ?: return null
        return players.getOrPut(scopeKey) { TavernAudioPlayer(ctx) }
    }

    /** 供 runtime 恢复上次的播放列表/设置。 */
    fun restore(scopeKey: String, raw: String?) {
        runCatching { playerFor(scopeKey)?.restore(raw) }
    }

    fun snapshot(scopeKey: String): String =
        runCatching { playerFor(scopeKey)?.snapshot() ?: "{}" }.getOrElse { "{}" }

    /**
     * 执行一条音频指令。
     *
     * @param op 操作名：play / pause / resume / list / replaceList / appendList / settings / seek
     * @param payloadJson JSON 对象，字段随 op 而定
     * @return 执行结果 JSON；失败返回 `{"ok":false,"error":"..."}`
     */
    fun dispatch(scopeKey: String, op: String, payloadJson: String): String {
        return runCatching {
            val payload = runCatching {
                json.parseToJsonElement(payloadJson) as? JsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            val player = playerFor(scopeKey)
                ?: return@runCatching errorJson("audio player not initialized (context missing)")

            val type = payload.str("type") ?: "bgm"

            when (op.lowercase()) {
                "play" -> {
                    val url = payload.str("url").orEmpty()
                    if (url.isBlank()) return@runCatching errorJson("url is required")
                    player.play(type, payload.str("title").orEmpty(), url)
                    okJson()
                }

                "pause" -> {
                    player.pause(type)
                    okJson()
                }

                "resume" -> {
                    player.resume(type)
                    okJson()
                }

                "list" -> {
                    val ch = player.state.value.channel(type)
                    buildJsonObject {
                        put("ok", JsonPrimitive(true))
                        put("playlist", tracksToJson(ch.playlist))
                        put("currentIndex", JsonPrimitive(ch.currentIndex))
                    }.toString()
                }

                "replaceList" -> {
                    player.replaceList(type, payload.tracks())
                    okJson()
                }

                "appendList" -> {
                    player.appendList(type, payload.tracks())
                    okJson()
                }

                "settings" -> {
                    player.updateSettings(
                        type = type,
                        enabled = payload.bool("enabled"),
                        mode = payload.str("mode"),
                        muted = payload.bool("muted"),
                        volume = payload.int("volume"),
                    )
                    okJson()
                }

                "seek" -> {
                    player.seek(type, payload.long("position") ?: 0L)
                    okJson()
                }

                else -> errorJson("unknown audio op: $op")
            }
        }.getOrElse { e ->
            errorJson(e.message ?: e::class.simpleName ?: "unknown error")
        }
    }

    /** 供 `getAudioSettings` / `getCurrentAudio` 读快照。 */
    fun stateJson(scopeKey: String, type: String): String {
        return runCatching {
            val ch = playerFor(scopeKey)?.state?.value?.channel(type) ?: TavernAudioChannel()
            buildJsonObject {
                put("playlist", tracksToJson(ch.playlist))
                put("currentIndex", JsonPrimitive(ch.currentIndex))
                put("current", ch.current?.let {
                    buildJsonObject {
                        put("title", JsonPrimitive(it.title))
                        put("url", JsonPrimitive(it.url))
                    }
                } ?: JsonObject(emptyMap()))
                put(
                    "settings",
                    buildJsonObject {
                        put("enabled", JsonPrimitive(ch.settings.enabled))
                        put("mode", JsonPrimitive(ch.settings.mode.toRaw()))
                        put("muted", JsonPrimitive(ch.settings.muted))
                        put("volume", JsonPrimitive(ch.settings.volume))
                    },
                )
            }.toString()
        }.getOrElse { "{}" }
    }

    fun release(scopeKey: String) {
        players.remove(scopeKey)?.release()
    }

    fun releaseAll() {
        players.values.forEach { runCatching { it.release() } }
        players.clear()
    }

    // ---------------------------------------------------------------- helpers

    private fun tracksToJson(tracks: List<TavernAudioTrack>): JsonArray = buildJsonArray {
        tracks.forEach { t ->
            add(
                buildJsonObject {
                    put("title", JsonPrimitive(t.title))
                    put("url", JsonPrimitive(t.url))
                },
            )
        }
    }

    private fun JsonObject.tracks(): List<TavernAudioTrack> {
        val arr = this["tracks"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("url").orEmpty()
            if (url.isBlank()) return@mapNotNull null
            TavernAudioTrack(
                title = o.str("title").orEmpty().ifBlank {
                    url.substringAfterLast('/').substringBeforeLast('.')
                },
                url = url,
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun okJson(): String = buildJsonObject { put("ok", JsonPrimitive(true)) }.toString()

    private fun errorJson(msg: String): String = buildJsonObject {
        put("ok", JsonPrimitive(false))
        put("error", JsonPrimitive(msg))
    }.toString()
}
