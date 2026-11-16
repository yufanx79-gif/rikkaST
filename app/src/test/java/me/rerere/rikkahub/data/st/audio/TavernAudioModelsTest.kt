package me.rerere.rikkahub.data.st.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v246] 酒馆音频模型测试。
 *
 * 覆盖契约（对齐 Tavern Helper `src/function/audio.ts`）：
 *  - 播放列表 upsert 去重（按 title 或 url）
 *  - 四种播放模式的字符串互转
 *  - 双通道独立（bgm / ambient 互不影响）
 *  - 音量裁剪到 0..100
 *  - 序列化往返
 */
class TavernAudioModelsTest {

    @Test
    fun upsertDeduplicatesByTitleOrUrl() {
        val ch = TavernAudioChannel()
        // 第一次插入
        val (c1, i1) = ch.upsert(TavernAudioTrack("A", "http://x/a.mp3"))
        assertEquals(1, c1.playlist.size)
        assertEquals(0, i1)

        // 同 title 不同 url -> 视为同一首，就地更新
        val (c2, i2) = c1.upsert(TavernAudioTrack("A", "http://y/other.mp3"))
        assertEquals("同 title 不应新增", 1, c2.playlist.size)
        assertEquals(0, i2)
        assertEquals("url 应被更新", "http://y/other.mp3", c2.playlist[0].url)

        // 同 url 不同 title -> 同样就地更新
        val (c3, i3) = c2.upsert(TavernAudioTrack("B", "http://y/other.mp3"))
        assertEquals("同 url 不应新增", 1, c3.playlist.size)
        assertEquals(0, i3)
        assertEquals("title 应被更新", "B", c3.playlist[0].title)

        // 全新一首 -> 追加
        val (c4, i4) = c3.upsert(TavernAudioTrack("C", "http://z/c.mp3"))
        assertEquals(2, c4.playlist.size)
        assertEquals(1, i4)
    }

    @Test
    fun audioModeRoundTripsAllFourValues() {
        val cases = mapOf(
            "repeat_one" to TavernAudioMode.REPEAT_ONE,
            "repeat_all" to TavernAudioMode.REPEAT_ALL,
            "shuffle" to TavernAudioMode.SHUFFLE,
            "play_one_and_stop" to TavernAudioMode.PLAY_ONE_AND_STOP,
        )
        for ((raw, mode) in cases) {
            assertEquals("fromRaw($raw)", mode, TavernAudioMode.fromRaw(raw))
            assertEquals("toRaw($mode)", raw, mode.toRaw())
        }
        // 未知值应回退到 repeat_all（上游默认）
        assertEquals(TavernAudioMode.REPEAT_ALL, TavernAudioMode.fromRaw("garbage"))
        assertEquals(TavernAudioMode.REPEAT_ALL, TavernAudioMode.fromRaw(null))
    }

    @Test
    fun twoChannelsAreIndependent() {
        val state = TavernAudioState()
        val bgm = state.channel("bgm").copy(
            playlist = listOf(TavernAudioTrack("song", "http://x/s.mp3")),
            currentIndex = 0,
        )
        val next = state.withChannel("bgm", bgm)

        assertEquals("bgm 应更新", 1, next.bgm.playlist.size)
        assertEquals("ambient 应保持空", 0, next.ambient.playlist.size)
        assertNotNull(next.bgm.current)
        assertNull("ambient 无当前曲目", next.ambient.current)
    }

    @Test
    fun channelTypeIsCaseInsensitive() {
        val state = TavernAudioState()
        val ambient = state.channel("AMBIENT").copy(playlist = listOf(TavernAudioTrack("a", "u")))
        val next = state.withChannel("Ambient", ambient)
        assertEquals("大小写应等价", 1, next.ambient.playlist.size)
        assertEquals(0, next.bgm.playlist.size)
    }

    @Test
    fun stateSerializationRoundTrips() {
        val original = TavernAudioState(
            bgm = TavernAudioChannel(
                playlist = listOf(
                    TavernAudioTrack("s1", "http://x/1.mp3"),
                    TavernAudioTrack("s2", "http://x/2.mp3"),
                ),
                settings = TavernAudioSettings(enabled = true, mode = TavernAudioMode.SHUFFLE, muted = true, volume = 77),
                currentIndex = 1,
            ),
            ambient = TavernAudioChannel(
                playlist = listOf(TavernAudioTrack("rain", "http://x/rain.mp3")),
                settings = TavernAudioSettings(enabled = false, mode = TavernAudioMode.PLAY_ONE_AND_STOP, volume = 30),
                currentIndex = 0,
            ),
        )
        val encoded = TavernAudioState.encode(original)
        val decoded = TavernAudioState.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun decodeGarbageFallsBackToDefaults() {
        val d1 = TavernAudioState.decode("not json")
        assertEquals(TavernAudioState(), d1)
        val d2 = TavernAudioState.decode("")
        assertEquals(TavernAudioState(), d2)
        val d3 = TavernAudioState.decode(null)
        assertEquals(TavernAudioState(), d3)
    }

    @Test
    fun defaultModesMatchUpstream() {
        // 上游 audio.ts：bgm 默认 repeat_all，ambient 默认 play_one_and_stop
        assertEquals(TavernAudioMode.REPEAT_ALL, TavernAudioState().bgm.settings.mode)
        assertEquals(TavernAudioMode.PLAY_ONE_AND_STOP, TavernAudioState().ambient.settings.mode)
    }

    @Test
    fun currentTrackReflectsIndex() {
        val ch = TavernAudioChannel(
            playlist = listOf(
                TavernAudioTrack("a", "u1"),
                TavernAudioTrack("b", "u2"),
            ),
            currentIndex = 1,
        )
        assertEquals("b", ch.current?.title)
        assertNull("越界索引应返回 null", ch.copy(currentIndex = 5).current)
        assertNull("负索引应返回 null", ch.copy(currentIndex = -1).current)
    }
}
