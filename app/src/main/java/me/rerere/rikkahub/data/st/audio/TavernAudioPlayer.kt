package me.rerere.rikkahub.data.st.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 酒馆 BGM / ambient 双通道播放器。
 *
 * 为什么用 ExoPlayer 而不是 SoundPool：
 *  - BGM 是长音频（几分钟到几十分钟），SoundPool 会把整个文件解码进内存，必然 OOM；
 *  - 需要循环、随机、进度、seek，ExoPlayer 原生支持；
 *  - 项目已有 media3 依赖（`app/build.gradle.kts:334`），零新增。
 *
 * 双通道实现：**两个独立的 ExoPlayer 实例**。
 * 单实例换 audioAttributes 无法做到"BGM 与环境音同时播放"，而酒馆语义要求并存。
 *
 * 线程模型：所有公开方法都可在任意线程调用，内部统一 post 到主线程执行
 * （ExoPlayer 要求在其创建线程访问）。
 */
class TavernAudioPlayer(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(TavernAudioState())
    val state: StateFlow<TavernAudioState> = _state.asStateFlow()

    /** 播放进度（毫秒），供 `getCurrentAudio().progress` 读取。不落盘。 */
    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private var bgmPlayer: ExoPlayer? = null
    private var ambientPlayer: ExoPlayer? = null

    private val released = AtomicBoolean(false)
    private var progressJob: kotlinx.coroutines.Job? = null

    private fun playerFor(type: String): ExoPlayer? =
        if (type.equals("ambient", ignoreCase = true)) ambientPlayer else bgmPlayer

    private fun ensurePlayer(type: String): ExoPlayer? {
        if (released.get()) return null
        val isAmbient = type.equals("ambient", ignoreCase = true)
        val existing = if (isAmbient) ambientPlayer else bgmPlayer
        if (existing != null) return existing
        return runCatching {
            val p = ExoPlayer.Builder(context).build().apply {
                // 环境音通常较短，音量略低以免盖住 BGM
                setVolume(if (isAmbient) 0.6f else 1.0f)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onTrackEnded(if (isAmbient) "ambient" else "bgm")
                        }
                    }
                })
            }
            if (isAmbient) ambientPlayer = p else bgmPlayer = p
            p
        }.getOrElse {
            Log.w(TAG, "ensurePlayer($type) failed", it)
            null
        }
    }

    /** 从持久化状态恢复（启动时调用一次）。 */
    fun restore(raw: String?) {
        val restored = TavernAudioState.decode(raw)
        _state.value = restored
        // 恢复时不自动播放：酒馆也是刷新页面后停在暂停态
        applyVolume("bgm")
        applyVolume("ambient")
    }

    /** 序列化当前状态用于持久化。 */
    fun snapshot(): String = TavernAudioState.encode(_state.value)

    // ------------------------------------------------------------------ 对外操作

    /** 对齐上游 `playAudio(type, audio)`：upsert 到列表 + 切到该曲 + 播放。 */
    fun play(type: String, title: String, url: String) {
        if (url.isBlank()) return
        scope.launch {
            val normalized = TavernAudioTrack(
                title = title.ifBlank { url.substringAfterLast('/').substringBeforeLast('.') },
                url = url,
            )
            val ch = _state.value.channel(type)
            val (nextCh, idx) = ch.upsert(normalized)
            _state.value = _state.value.withChannel(
                type,
                nextCh.copy(currentIndex = idx, settings = nextCh.settings.copy(enabled = true)),
            )
            applyVolume(type)
            loadAndPlay(type)
        }
    }

    fun pause(type: String) {
        scope.launch {
            playerFor(type)?.pause()
            val ch = _state.value.channel(type)
            _state.value = _state.value.withChannel(type, ch.copy(settings = ch.settings.copy(enabled = false)))
        }
    }

    fun resume(type: String) {
        scope.launch {
            val ch = _state.value.channel(type)
            _state.value = _state.value.withChannel(type, ch.copy(settings = ch.settings.copy(enabled = true)))
            applyVolume(type)
            loadAndPlay(type)
        }
    }

    fun replaceList(type: String, tracks: List<TavernAudioTrack>) {
        scope.launch {
            val ch = _state.value.channel(type)
            _state.value = _state.value.withChannel(
                type,
                ch.copy(playlist = tracks, currentIndex = if (tracks.isEmpty()) -1 else 0),
            )
            playerFor(type)?.stop()
        }
    }

    fun appendList(type: String, tracks: List<TavernAudioTrack>) {
        scope.launch {
            val ch = _state.value.channel(type)
            val merged = ch.playlist.toMutableList()
            for (t in tracks) {
                if (merged.none { it.title == t.title || it.url == t.url }) merged += t
            }
            _state.value = _state.value.withChannel(
                type,
                ch.copy(playlist = merged, currentIndex = if (ch.currentIndex < 0 && merged.isNotEmpty()) 0 else ch.currentIndex),
            )
        }
    }

    fun updateSettings(
        type: String,
        enabled: Boolean? = null,
        mode: String? = null,
        muted: Boolean? = null,
        volume: Int? = null,
    ) {
        scope.launch {
            val ch = _state.value.channel(type)
            val s = ch.settings.copy(
                enabled = enabled ?: ch.settings.enabled,
                mode = if (mode != null) TavernAudioMode.fromRaw(mode) else ch.settings.mode,
                muted = muted ?: ch.settings.muted,
                volume = (volume ?: ch.settings.volume).coerceIn(0, 100),
            )
            _state.value = _state.value.withChannel(type, ch.copy(settings = s))
            applyVolume(type)
            if (s.enabled) loadAndPlay(type) else playerFor(type)?.pause()
        }
    }

    fun seek(type: String, positionMs: Long) {
        scope.launch { playerFor(type)?.seekTo(positionMs.coerceAtLeast(0)) }
    }

    // ------------------------------------------------------------------ 内部

    private fun applyVolume(type: String) {
        val p = playerFor(type) ?: return
        val s = _state.value.channel(type).settings
        val v = if (s.muted) 0f else (s.volume.coerceIn(0, 100) / 100f)
        p.volume = if (type.equals("ambient", ignoreCase = true)) v * 0.6f else v
    }

    private fun loadAndPlay(type: String) {
        val ch = _state.value.channel(type)
        if (!ch.settings.enabled) return
        val track = ch.current ?: return
        val p = ensurePlayer(type) ?: return
        runCatching {
            val current = p.currentMediaItem?.localConfiguration?.uri?.toString()
            if (current != track.url) {
                p.setMediaItem(MediaItem.fromUri(track.url))
                p.prepare()
            }
            p.playWhenReady = true
            startProgressLoop()
        }.onFailure { Log.w(TAG, "loadAndPlay($type) failed", it) }
    }

    private fun onTrackEnded(type: String) {
        scope.launch {
            val ch = _state.value.channel(type)
            val nextIndex = when (ch.settings.mode) {
                TavernAudioMode.PLAY_ONE_AND_STOP -> {
                    val p = playerFor(type)
                    p?.pause()
                    return@launch
                }
                TavernAudioMode.REPEAT_ONE -> ch.currentIndex
                TavernAudioMode.REPEAT_ALL -> if (ch.playlist.isEmpty()) -1 else (ch.currentIndex + 1) % ch.playlist.size
                TavernAudioMode.SHUFFLE -> if (ch.playlist.isEmpty()) -1 else ch.playlist.indices.random()
            }
            if (nextIndex < 0) return@launch
            _state.value = _state.value.withChannel(type, ch.copy(currentIndex = nextIndex))
            loadAndPlay(type)
        }
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && !released.get()) {
                val p = bgmPlayer ?: ambientPlayer
                _progress.value = p?.currentPosition ?: 0L
                delay(250)
            }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        progressJob?.cancel()
        runCatching { bgmPlayer?.release() }
        runCatching { ambientPlayer?.release() }
        bgmPlayer = null
        ambientPlayer = null
    }

    private companion object {
        const val TAG = "TavernAudioPlayer"
    }
}
