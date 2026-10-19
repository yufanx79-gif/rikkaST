package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "TTS"

/**
 * 剥离 ST-iPhonie 等酒馆扩展专用多媒体/协议标签（`<tts>`、`<pic>`、`<bgm>`、`<sfx>`、`<phone>`），
 * 确保上游原生 TTS 通道只朗读角色实际可见的文本，不把 `<tts>角色|情绪|英文台词</tts>` 原文读出来。
 */
internal val ST_IPHONIE_PROTOCOL_TAGS_RE = Regex(
    """<(tts|pic|bgm|sfx|phone)\b[^>]*>[\s\S]*?</\1>""",
    RegexOption.IGNORE_CASE,
)

internal fun stripStIphonieProtocolTagsForNativeTts(raw: String): String {
    if (!raw.contains('<')) return raw
    return raw.replace(ST_IPHONIE_PROTOCOL_TAGS_RE, "").trim()
}

/**
 * Composable function to remember and manage custom TTS state.
 * Uses user-configured TTS providers instead of system TTS.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    // Remember the CustomTtsState instance across recompositions
    val ttsState = remember {
        CustomTtsStateImpl(
            context = context.applicationContext,
            settingsStore = settingsStore
        )
    }

    // Update the provider when settings change
    DisposableEffect(
        settings.selectedTTSProviderId,
        settings.ttsProviders,
        settings.defaultTTSPlaybackSpeed,
    ) {
        ttsState.updateProvider(settings.getSelectedTTSProvider())
        ttsState.setSpeed(settings.defaultTTSPlaybackSpeed)
        onDispose { }
    }

    // 注册双通道音频仲裁回调：当小手机（ST-iPhonie）开始播放语音时，自动停止原生 TTS 通道；
    // 反之当原生 TTS 开始朗读时，也会自动停止小手机语音，确保两个通道彻底隔离、绝不抢占冲突。
    DisposableEffect(ttsState) {
        TavernRuntimeManager.nativeTtsStopper = { ttsState.stop() }
        onDispose {
            if (TavernRuntimeManager.nativeTtsStopper != null) {
                TavernRuntimeManager.nativeTtsStopper = null
            }
            ttsState.cleanup()
        }
    }

    return ttsState
}

/**
 * Interface defining the public API of our custom TTS state holder.
 */
interface CustomTtsState {
    /** Flow indicating if the TTS provider is available and ready. */
    val isAvailable: StateFlow<Boolean>

    /** Flow indicating if the TTS is currently speaking. */
    val isSpeaking: StateFlow<Boolean>

    /** Flow holding any error message. */
    val error: StateFlow<String?>

    /** Flow indicating current chunk being processed (index) */
    val currentChunk: StateFlow<Int>

    /** Flow indicating total chunks in queue */
    val totalChunks: StateFlow<Int>

    /** Unified playback state (status, position, duration, speed, etc.) */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Speaks the given text using the selected TTS provider.
     * Long texts will be automatically chunked and queued.
     */
    fun speak(text: String, flushCalled: Boolean = true)

    /** Stops the current speech and clears the queue. */
    fun stop()

    /** Pauses the current playback. */
    fun pause()

    /** Resumes the paused playback. */
    fun resume()

    /** Skips to the next chunk in the queue. */
    fun skipNext()

    /** Fast forward current playback by [ms]. */
    fun fastForward(ms: Long = 5_000)

    /** Set playback [speed]. */
    fun setSpeed(speed: Float)

    /** Cleanup resources. */
    fun cleanup()
}

/**
 * Internal implementation of CustomTtsState.
 */
private class CustomTtsStateImpl(
    private val context: Context,
    private val settingsStore: SettingsStore
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()
    private val controller by lazy { TtsController(context, ttsManager) }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    override val isAvailable: StateFlow<Boolean> get() = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> get() = controller.isSpeaking
    override val error: StateFlow<String?> get() = controller.error
    override val currentChunk: StateFlow<Int> get() = controller.currentChunk
    override val totalChunks: StateFlow<Int> get() = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> get() = controller.playbackState

    fun updateProvider(provider: TTSProviderSetting?) {
        controller.setProvider(provider)
    }

    override fun speak(text: String, flushCalled: Boolean) {
        // Channel A (Native TTS) 启动前先通知 Channel B (ST-iPhonie) 停止播放，防止双声道叠加冲突
        TavernRuntimeManager.stopStIphonieTts("native_tts_speak")
        val cleanedProtocol = stripStIphonieProtocolTagsForNativeTts(text)
        val processed = cleanedProtocol.stripMarkdown()
        if (processed.isNotBlank()) {
            controller.speak(processed, flushCalled)
        }
    }

    override fun stop() {
        controller.stop()
    }

    override fun pause() {
        controller.pause()
        Log.d("CustomTtsState", "TTS paused")
    }

    override fun resume() {
        controller.resume()
        Log.d("CustomTtsState", "TTS resumed")
    }

    override fun skipNext() {
        controller.skipNext()
    }

    override fun fastForward(ms: Long) {
        controller.fastForward(ms)
    }

    override fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
    }

    override fun cleanup() {
        controller.dispose()
        currentJob = null
    }
}
