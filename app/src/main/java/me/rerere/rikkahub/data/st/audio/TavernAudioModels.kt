package me.rerere.rikkahub.data.st.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 酒馆（SillyTavern）BGM / ambient 双通道音频状态。
 *
 * 契约来源：Tavern Helper `src/function/audio.ts` 的 `AudioSettings` / `CurrentAudio`。
 * 语义：
 *  - 两个通道（`bgm` 背景音乐 / `ambient` 环境音效）**互相独立**，可同时播放；
 *  - `mode` 四选一：单曲循环 / 全部循环 / 随机 / 播一首就停；
 *  - `volume` 0..100，`muted` 独立于 `enabled`（静音但保留播放进度）。
 *
 * 为什么单独建模型而不塞进 Settings：
 * 音频状态变更极频繁（进度每 250ms 一次），走 DataStore 会造成写放大。
 * 这里只持久化「列表 + 设置」，播放进度属于瞬时状态，不落盘。
 */
@Serializable
data class TavernAudioTrack(
    val title: String = "",
    val url: String = "",
)

/** 播放模式，字符串值与上游 `AudioMode` 逐字对齐。 */
@Serializable
enum class TavernAudioMode {
    @kotlinx.serialization.SerialName("repeat_one")
    REPEAT_ONE,

    @kotlinx.serialization.SerialName("repeat_all")
    REPEAT_ALL,

    @kotlinx.serialization.SerialName("shuffle")
    SHUFFLE,

    @kotlinx.serialization.SerialName("play_one_and_stop")
    PLAY_ONE_AND_STOP,
    ;

    companion object {
        fun fromRaw(raw: String?): TavernAudioMode = when (raw?.lowercase()) {
            "repeat_one" -> REPEAT_ONE
            "repeat_all" -> REPEAT_ALL
            "shuffle" -> SHUFFLE
            "play_one_and_stop" -> PLAY_ONE_AND_STOP
            else -> REPEAT_ALL
        }
    }

    fun toRaw(): String = when (this) {
        REPEAT_ONE -> "repeat_one"
        REPEAT_ALL -> "repeat_all"
        SHUFFLE -> "shuffle"
        PLAY_ONE_AND_STOP -> "play_one_and_stop"
    }
}

@Serializable
data class TavernAudioSettings(
    val enabled: Boolean = true,
    val mode: TavernAudioMode = TavernAudioMode.REPEAT_ALL,
    val muted: Boolean = false,
    val volume: Int = 50,
)

/** 单通道状态（列表 + 设置 + 当前曲目索引）。 */
@Serializable
data class TavernAudioChannel(
    val playlist: List<TavernAudioTrack> = emptyList(),
    val settings: TavernAudioSettings = TavernAudioSettings(),
    val currentIndex: Int = -1,
) {
    val current: TavernAudioTrack?
        get() = playlist.getOrNull(currentIndex)

    /**
     * 按 URL 或标题去重插入。
     *
     * 对齐上游 `playAudio`：同名或同 URL 视为同一首，**就地更新**而不是追加，
     * 否则同一首歌反复 play 会让列表无限膨胀（实测某些卡片每楼都调 playAudio）。
     */
    fun upsert(track: TavernAudioTrack): Pair<TavernAudioChannel, Int> {
        val idx = playlist.indexOfFirst { it.title == track.title || it.url == track.url }
        return if (idx >= 0) {
            copy(playlist = playlist.toMutableList().also { it[idx] = track }) to idx
        } else {
            copy(playlist = playlist + track) to playlist.size
        }
    }
}

/** 完整音频状态（双通道）。 */
@Serializable
data class TavernAudioState(
    val bgm: TavernAudioChannel = TavernAudioChannel(
        settings = TavernAudioSettings(mode = TavernAudioMode.REPEAT_ALL),
    ),
    val ambient: TavernAudioChannel = TavernAudioChannel(
        settings = TavernAudioSettings(mode = TavernAudioMode.PLAY_ONE_AND_STOP),
    ),
) {
    fun channel(type: String): TavernAudioChannel =
        if (type.equals("ambient", ignoreCase = true)) ambient else bgm

    fun withChannel(type: String, next: TavernAudioChannel): TavernAudioState =
        if (type.equals("ambient", ignoreCase = true)) copy(ambient = next) else copy(bgm = next)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): TavernAudioState = runCatching {
            if (raw.isNullOrBlank()) TavernAudioState() else json.decodeFromString(serializer(), raw)
        }.getOrElse { TavernAudioState() }

        fun encode(state: TavernAudioState): String = runCatching {
            json.encodeToString(serializer(), state)
        }.getOrElse { "{}" }
    }
}
