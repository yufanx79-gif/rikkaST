package me.rerere.rikkahub.data.st.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.StreamChunk
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.st.extensions.extensionsBaseDir
import me.rerere.rikkahub.data.st.extensions.isSafeExtensionSegment
import me.rerere.rikkahub.data.st.extensions.resolveExtensionFile
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import org.json.JSONObject

/**
 * 酒馆 JS 运行时宿主（WebView + 资产链路 + 事件桥）。
 *
 * 架构：
 * - WebView 加载 `https://appassets.androidplatform.net/st-runtime/index.html`
 *   （自研资产拦截器提供 ES module 友好的 https 同源环境）
 * - `window.SillyTavern.getContext()` → RikkaBridge（@JavascriptInterface，同步）
 * - JS 侧：TavernHelper / 事件总线 / MVU iframe
 * - Kotlin → JS 事件：`fireEvent(name, payload)`（走 evaluateJavascript）
 */
/** `/js` 脚本执行结果（UI 可观察）。 */
data class JsResult(val ok: Boolean, val text: String, val seq: Long)
/** 脚本按钮（JSR 语义）：由运行时 JS 经 RikkaBridge.onScriptButtons 上报。 */
data class TavernScriptButton(
    val scriptId: String,
    val scriptName: String,
    val name: String,
    val event: String = "",
)

/** 第三方扩展或卡脚本通过 `registerFunctionTool` 注册的 LLM 函数工具描述。 */
data class JsFunctionToolSpec(
    val name: String,
    val displayName: String = name,
    val description: String = "",
    val parametersJson: String = "{}",
    val stealth: Boolean = false,
)

/** STscript `/popup`、`/input`、`/buttons` 交互弹窗请求（后台协程挂起等待 UI 响应）。 */
sealed class SlashModalRequest {
    abstract val deferred: CompletableDeferred<String>

    data class Popup(
        val text: String,
        val title: String?,
        val expectResult: Boolean,
        val okButton: String?,
        val cancelButton: String?,
        override val deferred: CompletableDeferred<String> = CompletableDeferred(),
    ) : SlashModalRequest()

    data class Input(
        val prompt: String,
        val title: String?,
        val defaultValue: String,
        override val deferred: CompletableDeferred<String> = CompletableDeferred(),
    ) : SlashModalRequest()

    data class Buttons(
        val prompt: String,
        val labels: List<String>,
        val multiple: Boolean,
        override val deferred: CompletableDeferred<String> = CompletableDeferred(),
    ) : SlashModalRequest()
}

object TavernRuntimeManager {
    private const val TAG = "TavernRuntime"
    private const val HOST = "appassets.androidplatform.net"
    const val BASE_URL = "https://$HOST/st-runtime/"

    // ==================== ST 事件名（逐行核对 vendor/st/events.js） ====================
    const val EVENT_MESSAGE_SWIPED = "message_swiped"          // events.js:7
    const val EVENT_MESSAGE_EDITED = "message_edited"          // events.js:10
    const val EVENT_MESSAGE_DELETED = "message_deleted"        // events.js:11
    const val EVENT_MESSAGE_UPDATED = "message_updated"        // events.js:12
    const val EVENT_GENERATION_STOPPED = "generation_stopped"  // events.js:24
    const val EVENT_GENERATION_ENDED = "generation_ended"      // events.js:25
    // [v222] 生成期事件（events.js:22 / 23）。GENERATION_AFTER_COMMANDS 逐字大写 —— ST 原名如此。
    const val EVENT_GENERATION_AFTER_COMMANDS = "GENERATION_AFTER_COMMANDS" // events.js:22
    const val EVENT_GENERATION_STARTED = "generation_started"               // events.js:23
    // [v227 N1] 预设·设置面事件（逐字对齐 ST events.js:30/37/38/92/93/94/95）。
    // 这是「适配所有卡 / 接得上扩展」的地基：ST-Prompt-Template 的 PromptManager.js:74 订阅
    // OAI_PRESET_CHANGED_BEFORE 做预设迁移，JSR 预设页订阅 PRESET_* 刷新列表。
    const val EVENT_SETTINGS_UPDATED = "settings_updated"                     // events.js:30
    const val EVENT_OAI_PRESET_CHANGED_BEFORE = "oai_preset_changed_before"   // events.js:37
    const val EVENT_OAI_PRESET_CHANGED_AFTER = "oai_preset_changed_after"     // events.js:38
    const val EVENT_PRESET_CHANGED = "preset_changed"                         // events.js:92
    const val EVENT_PRESET_DELETED = "preset_deleted"                         // events.js:93
    const val EVENT_PRESET_RENAMED = "preset_renamed"                         // events.js:94
    const val EVENT_PRESET_RENAMED_BEFORE = "preset_renamed_before"           // events.js:95
    // [v227 N1] 世界书设置变更（events.js:41；ST 真源在 world-info.js:5842/:6228 发，**无载荷**）。
    // 订阅方实证：ST-Prompt-Template PromptManager.js:835 -> renderDebounced()。
    const val EVENT_WORLDINFO_SETTINGS_UPDATED = "worldinfo_settings_updated" // events.js:41
    // [v234 I1/I2] STREAM_TOKEN_RECEIVED（events.js:74；script.js:3901 emit，载荷 = 累积全文）+
    //   MESSAGE_SWIPE_DELETED（events.js:16；script.js:9388 emit，载荷 = {messageId, swipeId, newSwipeId}；
    //   JSR event.d.ts:320 / src/swipe.ts:8 订阅实证）。
    const val EVENT_STREAM_TOKEN_RECEIVED = "stream_token_received"      // events.js:74
    const val EVENT_MESSAGE_SWIPE_DELETED = "message_swipe_deleted"     // events.js:16
    private const val MAX_LOGS = 400
    /** 持久化日志文件名（app 外部专属目录 /sdcard/Android/data/<pkg>/files/）。 */
    private const val LOG_FILE_NAME = "tavern-runtime.log"
    /** 持久化日志滚动阈值（超过后保留尾部约一半）。 */
    private const val LOG_FILE_MAX_BYTES = 256 * 1024L

    /** 需要本地化的 jsDelivr 镜像域名（卡脚本裸 import 的落点）。 */
    private val CDN_HOSTS = setOf(
        "cdn.jsdelivr.net",
        "testingcf.jsdelivr.net",
        "fastly.jsdelivr.net",
        "gcore.jsdelivr.net",
        "cdnjs.cloudflare.com",
        "unpkg.com",
        "esm.sh",
        "cdn.bootcdn.net",
        "s4.zstatic.net",
    )

    private val OFFLINE_FONT_AND_STYLE_HOSTS = setOf(
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "use.fontawesome.com",
        "kit.fontawesome.com",
        "at.alicdn.com",
        "ka-f.fontawesome.com",
    )

    // 延迟创建：JVM 单元测试（android.jar 桩）下 Looper.getMainLooper() 会抛 "not mocked"，
    // 静态初始化失败会导致整个类不可用；改为首次 post 时再创建。
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    val json = Json { ignoreUnknownKeys = true }
    private val logs = ConcurrentLinkedQueue<String>()
    /** 日志文件写入锁（append 可能由主线程 / JavaBridge 线程并发触发）。 */
    private val logFileLock = Any()
    private val logTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val _lastJsResult = MutableStateFlow<JsResult?>(null)
    /** 脚本按钮列表（跨脚本汇总，聊天页 UI 订阅渲染）。 */
    val scriptButtonsFlow = MutableStateFlow<List<TavernScriptButton>>(emptyList())
    /** EJS 渲染等待槽（seq → deferred；由 RikkaBridge.onEjsResult 回填）。 */
    val pendingEjs = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val ejsSeqCounter = AtomicLong(0)

    /** [v234 S2] MacrosParser JS 注册表快照（JS 'macros_updated' 推送；key = 宏名 → (type, value)）。 */
    val macrosRegistry = AtomicReference<Map<String, Pair<String, String>>>(emptyMap())

    /** JS 侧通过 SlashCommandParser.addCommandObject / addCommand 注册的扩展斜杠命令名集合（小写）。 */
    private val jsSlashCommandNames: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** 标记当前线程或当前同步桥调用栈是否正在阻塞等待 WebView JavaBridge 返回，防止向同一 WebView 发起重入 evaluateJavascript 造成 V8 死锁。 */
    private val inJavaBridgeCall = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }
    private val activeJavaBridgeBlockingCalls = java.util.concurrent.atomic.AtomicInteger(0)

    private val filterEvalSeqCounter = AtomicLong(0)
    private val pendingFilterEvals = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    /** 判断当前是否处于 WebView @JavascriptInterface 同步调用栈内（含内部启动的 runBlocking 子协程）。 */
    fun isInJavaBridgeCall(): Boolean = (inJavaBridgeCall.get() == true) || activeJavaBridgeBlockingCalls.get() > 0

    /** 判断指定命令名是否已在 JS 侧注册（O(1) 无锁查询）。 */
    fun hasJsSlashCommand(name: String): Boolean = name.trim().lowercase() in jsSlashCommandNames

    /** 供测试或宿主同步更新已注册的 JS 斜杠命令名集合。 */
    fun updateRegisteredJsSlashCommands(names: Collection<String>) {
        jsSlashCommandNames.clear()
        names.forEach { n ->
            val trimmed = n.trim().lowercase()
            if (trimmed.isNotEmpty()) jsSlashCommandNames.add(trimmed)
        }
    }

    /** JS 侧通过 `registerFunctionTool` 注册的 LLM 函数工具规格列表。 */
    private val jsFunctionToolsRef = AtomicReference<List<JsFunctionToolSpec>>(emptyList())
    val pendingFunctionTools = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val functionToolSeqCounter = AtomicLong(0)

    fun updateRegisteredJsFunctionTools(specs: List<JsFunctionToolSpec>) {
        jsFunctionToolsRef.set(specs.filter { it.name.isNotBlank() })
    }

    fun getRegisteredJsFunctionTools(): List<JsFunctionToolSpec> = jsFunctionToolsRef.get()

    fun listJsFunctionTools(): List<JsFunctionToolSpec> = jsFunctionToolsRef.get()

    fun registerJsFunctionTool(spec: JsFunctionToolSpec): Boolean {
        val cleanName = spec.name.trim()
        if (cleanName.isEmpty()) return false
        val current = jsFunctionToolsRef.get().filterNot { it.name.equals(cleanName, ignoreCase = true) }
        jsFunctionToolsRef.set(current + spec.copy(name = cleanName))
        return true
    }

    fun unregisterJsFunctionTool(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        val before = jsFunctionToolsRef.get()
        val after = before.filterNot { it.name.equals(cleanName, ignoreCase = true) }
        jsFunctionToolsRef.set(after)
        return after.size < before.size
    }

    fun hasJsFunctionTool(name: String): Boolean =
        jsFunctionToolsRef.get().any { it.name.equals(name.trim(), ignoreCase = true) }

    /** 进程内 `executeOnStartup` Quick Reply 是否已触发过（避免每次切换会话重复跑 startup 钩子）。 */
    private val startupQrExecuted = java.util.concurrent.atomic.AtomicBoolean(false)
    fun markStartupQrExecuted(): Boolean = startupQrExecuted.compareAndSet(false, true)

    /** STscript `/popup`、`/input`、`/buttons` 模态弹窗状态流与 UI 挂载计数。 */
    val pendingSlashModal = MutableStateFlow<SlashModalRequest?>(null)
    @Volatile var isSlashModalUiAttached: Boolean = false

    fun awaitSlashModal(request: SlashModalRequest, timeoutMs: Long = 120_000L): String? {
        if (!isSlashModalUiAttached) return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        pendingSlashModal.value = request
        return try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(timeoutMs) { request.deferred.await() }
            }
        } finally {
            if (pendingSlashModal.value === request) {
                pendingSlashModal.value = null
            }
        }
    }

    /** [v234 S2] 函数宏批量执行等待槽（seq → deferred）。 */
    val pendingMacros = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val macrosSeqCounter = AtomicLong(0)

    /** [v211] 生成管线钩子（CHAT_COMPLETION_SETTINGS_READY）的挂起回调表。 */
    val pendingGenHooks = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val genHookSeqCounter = AtomicLong(0)

    /** [ST 1.19.0] MessageFormatter 三阶段钩子状态与挂起回调表。 */
    @Volatile var hasMessageFormatterHooks: Boolean = false
        private set
    private val messageFormatterVersionCounter = AtomicLong(0)
    private val _messageFormatterVersionFlow = MutableStateFlow(0L)
    val messageFormatterVersionFlow: StateFlow<Long> get() = _messageFormatterVersionFlow
    val messageFormatterVersion: Long get() = messageFormatterVersionCounter.get()
    val pendingFormatterStages = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val formatterStageSeqCounter = AtomicLong(0)

    /** Optional test/host hook for evaluating MessageFormatter stages without an Android WebView instance. */
    @Volatile
    var customMessageFormatterEvaluator: (suspend (stage: String, text: String, characterName: String, isUser: Boolean) -> String?)? = null

    fun updateMessageFormatterHooksState(active: Boolean) {
        hasMessageFormatterHooks = active
        val next = messageFormatterVersionCounter.incrementAndGet()
        _messageFormatterVersionFlow.value = next
    }

    suspend fun runMessageFormatterStageAsync(
        stage: String,
        text: String,
        characterName: String = "",
        isUser: Boolean = false,
        timeoutMs: Long = 1_500L,
    ): String {
        if (!hasMessageFormatterHooks) return text
        if (isInJavaBridgeCall()) return text
        customMessageFormatterEvaluator?.let { evaluator ->
            return runCatching {
                withTimeoutOrNull(timeoutMs) {
                    evaluator(stage, text, characterName, isUser)
                } ?: text
            }.getOrDefault(text)
        }
        if (!pageReady) return text
        val wv = webView ?: return text
        val seq = formatterStageSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pendingFormatterStages[seq] = deferred
        val ctxJson = JSONObject().apply {
            put("characterName", characterName)
            put("ch_name", characterName)
            put("isSystem", false)
            put("isUser", isUser)
            put("messageId", -1)
            put("isReasoning", false)
        }.toString()
        mainHandler.post {
            runCatching {
                wv.evaluateJavascript(
                    "window.__rikkaRunMessageFormatterStage && window.__rikkaRunMessageFormatterStage($seq, ${JSONObject.quote(stage)}, ${JSONObject.quote(text)}, ${JSONObject.quote(ctxJson)});",
                    null,
                )
            }.onFailure {
                pendingFormatterStages.remove(seq)?.complete(text)
            }
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: text
        } catch (_: Exception) {
            text
        } finally {
            pendingFormatterStages.remove(seq)
        }
    }

    fun runMessageFormatterStageBlocking(
        stage: String,
        text: String,
        characterName: String = "",
        isUser: Boolean = false,
        timeoutMs: Long = 1_500L,
    ): String {
        if (!hasMessageFormatterHooks) return text
        val isMain = runCatching { Looper.myLooper() == Looper.getMainLooper() && Looper.getMainLooper() != null }.getOrDefault(false)
        if (isMain && customMessageFormatterEvaluator == null) return text
        return try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                runMessageFormatterStageAsync(stage, text, characterName, isUser, timeoutMs)
            }
        } catch (_: Exception) {
            text
        }
    }

    // ==================== [Bug B] 7 阶段发送诊断记录 ====================
    enum class SendDiagnosticStage(val stepNumber: Int, val label: String) {
        STAGE_1_SEND_CLICKED(1, "用户点击发送"),
        STAGE_2_HANDLER_ENTERED(2, "进入发送处理函数"),
        STAGE_3_USER_MSG_PERSISTED(3, "用户消息写入会话状态与持久化层"),
        STAGE_4_SCROLL_TRIGGERED(4, "触发列表滚动"),
        STAGE_5_GENERATION_STARTED(5, "启动助手生成任务"),
        STAGE_6_MODEL_REQUEST_SENT(6, "发起模型请求"),
        STAGE_7_GENERATION_FINISHED(7, "生成完成/失败/取消"),
    }

    data class SendDiagnosticRecord(
        val seq: Long,
        val stage: SendDiagnosticStage,
        val conversationId: String,
        val detail: String,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val sendDiagnosticSeq = AtomicLong(0)
    private val sendDiagnosticRecords = ConcurrentLinkedQueue<SendDiagnosticRecord>()
    private const val MAX_SEND_DIAG_RECORDS = 200

    @Volatile
    var lastSendCriticalWindowStartMs: Long = 0L
        private set

    val isSendOrHookInFlight: Boolean
        get() {
            if (pendingGenHooks.isNotEmpty()) return true
            val started = lastSendCriticalWindowStartMs
            if (started <= 0L) return false
            return (System.currentTimeMillis() - started) in 0L..4_000L
        }

    fun recordSendDiagnostic(
        stage: SendDiagnosticStage,
        conversationId: String,
        detail: String,
    ): SendDiagnosticRecord {
        when (stage) {
            SendDiagnosticStage.STAGE_1_SEND_CLICKED,
            SendDiagnosticStage.STAGE_2_HANDLER_ENTERED,
            SendDiagnosticStage.STAGE_3_USER_MSG_PERSISTED,
            SendDiagnosticStage.STAGE_5_GENERATION_STARTED -> {
                lastSendCriticalWindowStartMs = System.currentTimeMillis()
            }
            SendDiagnosticStage.STAGE_6_MODEL_REQUEST_SENT,
            SendDiagnosticStage.STAGE_7_GENERATION_FINISHED -> {
                lastSendCriticalWindowStartMs = 0L
            }
            else -> {}
        }
        val record = SendDiagnosticRecord(
            seq = sendDiagnosticSeq.incrementAndGet(),
            stage = stage,
            conversationId = conversationId,
            detail = detail,
        )
        sendDiagnosticRecords.offer(record)
        while (sendDiagnosticRecords.size > MAX_SEND_DIAG_RECORDS) {
            sendDiagnosticRecords.poll()
        }
        appendLog(
            "info",
            "[send-diag][#${record.seq}][S${stage.stepNumber}:${stage.name}] conv=$conversationId $detail",
        )
        return record
    }

    fun getSendDiagnosticRecords(conversationId: String? = null): List<SendDiagnosticRecord> {
        val all = sendDiagnosticRecords.toList()
        return if (conversationId == null) all else all.filter { it.conversationId == conversationId }
    }

    fun clearSendDiagnosticRecords() {
        sendDiagnosticRecords.clear()
    }
    private var jsResultSeq = 0L

    /** [v229 A1] 运行时自持协程作用域（跑 `/generate` 的流式生成；不阻塞 WebView 线程）。 */
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ==================== [v214] 正则诊断接线 ====================

    /** 正则诊断钩子是否已接线（幂等）。 */
    @Volatile private var regexDiagnosticsWired = false

    init {
        wireRegexDiagnostics()
    }

    /**
     * [v214] 把 RegexScriptEngine 的编译失败 / 不定长 lookbehind 降级诊断接到 [appendLog]
     * （→ 真机 tavern-runtime.log）。对象初始化即接线，早于 ChatVM / 生成 / 渲染管线。
     */
    private fun wireRegexDiagnostics() {
        if (regexDiagnosticsWired) return
        regexDiagnosticsWired = true
        RegexScriptEngine.onCompileError = { msg -> appendLog("warn", msg) }
        RegexScriptEngine.onCompileDegraded = { msg -> appendLog("warn", msg) }
    }

    @Volatile private var webView: WebView? = null

    // [batch17] 除主运行时页面外，还可能有若干「消息面板」WebView（每个面板一个）。
    // 脚本按钮可能注册在任意一个页面里，而 eval() 只打主页面 —— 这就是「按钮点了没反应」的原因。
    private val auxWebViews = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebView, String>())

    /**
     * [P0-1 / P0-2] 主运行时渲染进程代数：
     * - 采用 1500ms 合并窗口（coalesce window），避免同一次共享渲染进程崩溃引发多个 WebView
     *   连续回调时反复递增代数导致级联重建风暴；
     * - 单个面板的局部崩溃走 [onPanelRendererGone]，仅重建失效面板自身，不强制销毁其他健康面板。
     */
    private val _rendererGeneration = MutableStateFlow(0)
    val rendererGeneration: StateFlow<Int> get() = _rendererGeneration
    private val rendererBumpLock = Any()
    @Volatile private var lastRendererBumpAtMs: Long = 0L
    private const val RENDERER_BUMP_COALESCE_MS = 1500L

    fun bumpRendererGeneration(nowMs: Long = System.currentTimeMillis()): Int = synchronized(rendererBumpLock) {
        if (lastRendererBumpAtMs > 0L && (nowMs - lastRendererBumpAtMs) in 0 until RENDERER_BUMP_COALESCE_MS) {
            appendLog("debug", "[webview] coalesced duplicate bumpRendererGeneration within ${nowMs - lastRendererBumpAtMs}ms (gen=${_rendererGeneration.value})")
            return _rendererGeneration.value
        }
        lastRendererBumpAtMs = nowMs
        val next = _rendererGeneration.value + 1
        _rendererGeneration.value = next
        return next
    }

    /**
     * 面板 WebView 发生 `onRenderProcessGone` 时的协调入口：
     * 1) 立即注销失效面板辅助视图；
     * 2) 探测主运行时 WebView 是否仍然存活（若共享同一渲染进程但主 WebView 回调尚未到达或已失效，则触发主运行时自愈）；
     * 3) 不无条件递增全局代数，防止其他未崩溃面板丢失输入与 DOM 状态。
     */
    fun onPanelRendererGone(
        panelView: WebView,
        messageKey: String?,
        didCrash: Boolean,
        priority: Int,
    ) {
        unregisterAuxWebView(panelView)
        appendLog(
            "error",
            "[webview] renderer gone (panel key=$messageKey) didCrash=$didCrash priority=$priority",
        )
        mainHandler.post {
            val hostWv = webView ?: return@post
            runCatching {
                hostWv.evaluateJavascript("1") { res ->
                    if (res == null) {
                        appendLog("warn", "[webview] host runtime unresponsive after panel renderer gone -> recovering host")
                        val gen = bumpRendererGeneration()
                        if (webView === hostWv) {
                            webView = null
                            pageReady = false
                            isMvuActive = false
                        }
                        runCatching {
                            hostWv.stopLoading()
                            hostWv.destroy()
                        }
                        appContext?.let { ctx ->
                            runCatching { createWebView(ctx) }
                                .onFailure { appendLog("error", "[webview] host recovery failed (gen=$gen): ${it.message}") }
                        }
                    }
                }
            }
        }
    }

    /**
     * 在提示词组装前异步求值当前会话所有带动态 JS `filter` 的注入提示词（带超时保护，绝不永久阻塞生成管线）。
     */
    suspend fun evaluateActiveInjectFilters(conversationId: String?, timeoutMs: Long = 1200L) {
        if (conversationId.isNullOrBlank()) return
        if (isInJavaBridgeCall()) return
        val rawList = InjectedPromptStore.rawSnapshot(conversationId)
        if (rawList.none { it.hasFilter }) return
        val wv = webView ?: return
        if (!pageReady) return
        val seq = filterEvalSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<Unit>()
        pendingFilterEvals[seq] = deferred
        mainHandler.post {
            val currentWv = webView
            if (currentWv == null || !pageReady) {
                pendingFilterEvals.remove(seq)?.complete(Unit)
                return@post
            }
            runCatching {
                currentWv.evaluateJavascript(
                    "(async function(){try{if(window.__rikkaEvaluateAllInjectFilters){await window.__rikkaEvaluateAllInjectFilters($seq);}else if(window.RikkaBridge&&window.RikkaBridge.onInjectFiltersEvaluated){window.RikkaBridge.onInjectFiltersEvaluated($seq);}}catch(e){if(window.RikkaBridge&&window.RikkaBridge.onInjectFiltersEvaluated){window.RikkaBridge.onInjectFiltersEvaluated($seq);}}})()",
                    null,
                )
            }.onFailure {
                pendingFilterEvals.remove(seq)?.complete(Unit)
            }
        }
        try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pendingFilterEvals.remove(seq)
        }
    }

    @Volatile private var exitInfoLogged = false
    @Volatile var lastAbnormalExitSummary: String? = null
        private set

    /** [P0-2] Android 11+ 启动时自取证：读取上一次进程退出的内核级原因（Native abort / LMKD / OOM / ANR 及 PSS/RSS）写入日志与崩溃报告目录。 */
    private fun logLastProcessExitInfo(context: Context) {
        if (exitInfoLogged) return
        exitInfoLogged = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val exits = am?.getHistoricalProcessExitReasons(context.packageName, 0, 3).orEmpty()
                for ((idx, info) in exits.withIndex()) {
                    val summary = "[exit-info] prev_exit[$idx]: reason=${info.reason} status=${info.status} " +
                        "importance=${info.importance} pss=${info.pss}kB rss=${info.rss}kB " +
                        "ts=${info.timestamp} desc=${info.description}"
                    appendLog("warn", summary)
                    if (idx == 0 && info.reason in setOf(
                            android.app.ApplicationExitInfo.REASON_CRASH,
                            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                            android.app.ApplicationExitInfo.REASON_ANR,
                            android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                            android.app.ApplicationExitInfo.REASON_SIGNALED,
                        )
                    ) {
                        lastAbnormalExitSummary = summary
                        runCatching {
                            val crashDir = java.io.File(context.filesDir, "crash_logs").apply { mkdirs() }
                            val reportFile = java.io.File(crashDir, "native_exit_${info.timestamp}.log")
                            if (!reportFile.exists()) {
                                val traceText = runCatching {
                                    info.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(32_000) }
                                }.getOrNull().orEmpty()
                                reportFile.writeText(
                                    buildString {
                                        appendLine("=== RikkaST Process Exit Diagnostic Report ===")
                                        appendLine(summary)
                                        if (traceText.isNotBlank()) {
                                            appendLine("--- Trace ---")
                                            appendLine(traceText)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }.onFailure { t ->
                appendLog("warn", "[exit-info] query failed: ${t.message}")
            }
        }
    }

    fun registerAuxWebView(wv: WebView, tag: String) {
        auxWebViews[wv] = tag
        appendLog("debug", "[webview] aux registered: $tag (total=${auxWebViews.size})")
    }

    fun unregisterAuxWebView(wv: WebView) {
        val removed = auxWebViews.remove(wv)
        if (removed != null) {
            appendLog("debug", "[webview] aux unregistered: $removed (total=${auxWebViews.size})")
        }
    }

    /** 在主运行时 + 所有辅助 WebView 里执行同一段 JS（按钮派发用）。 */
    fun broadcast(script: String) {
        var n = 0
        for (wv in auxWebViews.keys.toList()) {
            n++
            try { wv.post { runCatching { wv.evaluateJavascript(script, null) } } } catch (_: Throwable) { }
        }
        appendLog("debug", "[buttons] broadcast to main + $n aux webview(s)")
        eval(script)
    }
    @Volatile private var appContext: Context? = null
    @Volatile private var pageReady = false

    /** 当前焦点会话（由聊天页面/生成流程更新）。 */
    @Volatile var activeConversationId: String? = null
        private set

    /** 当前焦点会话所属的助手 ID（用于立绘/CG 资源解析与 Node 伴生服务会话隔离）。 */
    @Volatile var activeAssistantId: String? = null
        get() = field ?: activeAssistant?.id?.toString()
        private set

    /** 由 UI 层注入：当前焦点会话的 Assistant 实例提供器。 */
    @Volatile var activeAssistantSupplier: (() -> Assistant?)? = null

    val activeAssistant: Assistant?
        get() = runCatching { activeAssistantSupplier?.invoke() }.getOrNull()

    fun setActiveAssistantId(assistantId: String?) {
        activeAssistantId = assistantId?.takeIf { it.isNotBlank() }
    }

    /** [R9] 开发者模式：开启后 [fireEvent] 额外把事件写入 tavern-runtime.log。 */
    @Volatile var developerMode: Boolean = false
        private set

    /** [R9] 由 ChatService 在发射事件前同步当前 Settings.developerMode。 */
    fun setDeveloperMode(enabled: Boolean) {
        developerMode = enabled
    }

    /** 由 UI 层注入：构建当前会话的消息数组 JSON（JSR `getChatMessages` 语义）。 */
    @Volatile var chatJsonSupplier: (() -> String)? = null

    /** 由 UI 层注入：JS 侧聊天记录内容变更回写（入参为楼层更新 JSON 数组，由 UI 解析并持久化）。 */
    @Volatile var chatUpdater: ((String) -> Unit)? = null

    /** 由 UI 层注入：当前助手卡内 `tavern_helper` 原始 JSON（JSR 卡脚本语义）。 */
    @Volatile var tavernScriptsSupplier: (() -> String)? = null

    /** 由 UI 层注入：卡内嵌世界书读取（入参世界书名，返回 ST 风格 world info JSON）。 */
    @Volatile var tavernLorebookSupplier: ((String) -> String)? = null

    /** 由 UI 层注入：当前角色卡最小信息 JSON（name / world）。 */
    @Volatile var tavernCharacterSupplier: (() -> String)? = null

    /** [v208] 由 UI 层注入：可用世界书名列表 JSON（world_names / selected / charLore）。 */
    @Volatile var worldNamesSupplier: (() -> String)? = null

    /** 由 UI 层注入：已安装第三方扩展装载列表 JSON（文件夹/入口文件；供 runtime 注入）。 */
    @Volatile var thirdPartyExtensionsSupplier: (() -> String)? = null

    /** 由 UI 层注入：第三方扩展设置（extension_settings 非 variables 部分）JSON。 */
    @Volatile var thirdPartySettingsSupplier: (() -> String)? = null

    /** 由 UI 层注入：第三方扩展设置保存回调（JS `saveSettingsDebounced` 时回写）。 */
    @Volatile var thirdPartySettingsSaver: ((String) -> Unit)? = null

    /** 由 UI 层注入：把用户脚本送进运行时的通道（保留）。 */
    @Volatile var onJsEvent: ((name: String, payloadJson: String) -> Unit)? = null

    /** 由 UI 层注入：STscript 执行入口（JS `triggerSlash` / 快速回复共用；返回管道值）。 */
    @Volatile var slashRunner: ((String) -> String)? = null

    /** 由 UI 层注入：静默生成直连通道（prompt, raw, systemPrompt -> 生成文本），不受管道符 `|` 切分干扰。 */
    @Volatile var quietGenerator: ((String, Boolean, String) -> String)? = null

    /**
     * [v229 A1] 由 ChatService 注入：跑一次「JSR 生成的回复」。
     * 入参 = JSR `/generate` 的请求体（OpenAI 形状），返回宿主 Provider 归一化后的流。
     * 未接线时返回 emptyFlow（调用方回 JSON 错误体，而不是 500 空响应）。
     */
    @Volatile var textGenerationHook: ((String) -> Flow<StreamChunk>)? = null

    /** [v229 A1] 由 ChatService 注入：`/status` 要回报的模型名列表（JSR `getModelList`）。 */
    @Volatile var generationModelIdsSupplier: (() -> List<String>)? = null

    /** [v229 A1] 由 ChatService 注入：当前默认模型名（请求体没带 model 时的回退）。 */
    @Volatile var currentModelIdSupplier: (() -> String?)? = null

    /** [v214] 由 UI 层注入：世界书条目写回（bookName, entriesJson → 是否受理）。 */
    @Volatile var lorebookUpdater: ((String, String) -> Boolean)? = null

    /** [v214] 由 UI 层注入：面板表单发送用户消息（text → 是否受理）。 */
    @Volatile var userMessageSender: ((String) -> Boolean)? = null

    /**
     * [v243 BUGFIX A2] 由 UI 层注入：JS `createChatMessages` 的宿主落点
     * （jsonText = [{role,name,message,data}] → 是否受理）。
     *
     * ⚠️ 必须**同步**完成会话状态更新（见 ChatVM.insertRuntimeChatMessages 的注释）：
     * 卡脚本的典型序列是 `await createChatMessages(...); await triggerSlash('/trigger')`，
     * 而 ChatService 每个入口都会 cancel 上一个 job —— 排队的异步插入会被 /trigger 直接取消。
     */
    @Volatile var chatInserter: ((String) -> Boolean)? = null

    /** JS `deleteChatMessages` 的宿主落点（idsJson = [0, 1, ...] → 是否受理）。 */
    @Volatile var chatDeleter: ((String) -> Boolean)? = null

    /**
     * [v243 BUGFIX A3] 由 UI 层注入：JSR 世界书条目写入
     * （bookName, payloadJson = {upsert:[...],delete:[uid],replaceAll:bool} → 是否受理）。
     */
    @Volatile var lorebookUpserter: ((String, String) -> Boolean)? = null

    /** JSR `createOrReplaceWorldbook` / `createWorldbook` 整书创建或替换回调（bookName, entriesJson, replaceIfExists）。 */
    @Volatile var lorebookCreator: ((String, String, Boolean) -> Boolean)? = null

    /** JSR `deleteWorldbook` 整书删除回调（bookName）。 */
    @Volatile var lorebookDeleter: ((String) -> Boolean)? = null

    /** JSR `setChatLorebook` / `rebindCharWorldbooks` / `rebindGlobalWorldbooks` 绑定回调（scope, namesJson）。 */
    @Volatile var lorebookRebinder: ((String, String) -> Boolean)? = null

    /** JS 自定义斜杠命令异步等待槽（seq → deferred）。 */
    val pendingSlash = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val slashSeqCounter = AtomicLong(0)

    /** MVU bundle 是否已就绪（由 JS 侧 mvu_ready / mvu_error 事件维护）。 */
    @Volatile var isMvuActive: Boolean = false
        private set

    // ==================== [10-19-21] 通用悬浮窗状态 & 双通道 TTS 隔离 ====================
    data class FloatingWindowSummary(
        val hasIphonie: Boolean = false,
        val hasMemory: Boolean = false,
        val hasLittleWhiteBox: Boolean = false,
        val hasCardFloating: Boolean = false,
        val cardFloatingCount: Int = 0,
    ) {
        val hasAny: Boolean
            get() = hasIphonie || hasMemory || hasLittleWhiteBox || hasCardFloating
    }

    data class StIphonieTtsState(
        val phase: String = "idle",
        val playing: Boolean = false,
        val message: String = "等待播放",
    )

    private val _floatingWindowsFlow = MutableStateFlow(FloatingWindowSummary())
    val floatingWindowsFlow: StateFlow<FloatingWindowSummary> get() = _floatingWindowsFlow

    private val _requestedFloatingTargetFlow = MutableStateFlow<String?>(null)
    val requestedFloatingTargetFlow: StateFlow<String?> get() = _requestedFloatingTargetFlow

    private val _stIphonieTtsStateFlow = MutableStateFlow(StIphonieTtsState())
    val stIphonieTtsStateFlow: StateFlow<StIphonieTtsState> get() = _stIphonieTtsStateFlow

    /** 由上游系统原生 TTS (LocalTTSState) 注册的停止回调：当 ST-iPhonie 开始播音时自动停止原生 TTS，防止双通道冲突。 */
    @Volatile
    var nativeTtsStopper: (() -> Unit)? = null

    val isStIphonieSpeaking: Boolean
        get() = _stIphonieTtsStateFlow.value.playing || _stIphonieTtsStateFlow.value.phase == "playing"

    fun requestOpenFloatingWindow(target: String = "iphonie") {
        _requestedFloatingTargetFlow.value = target
    }

    fun dismissFloatingWindow() {
        _requestedFloatingTargetFlow.value = null
        eval("window.__rikkaCloseFloatingStage && window.__rikkaCloseFloatingStage();")
    }

    fun switchFloatingWindowTarget(target: String) {
        _requestedFloatingTargetFlow.value = target
        val escaped = JSONObject.quote(target)
        eval("window.__rikkaOpenFloatingTarget && window.__rikkaOpenFloatingTarget($escaped);")
    }

    /** 停止 ST-iPhonie 角色语音（当上游系统原生 TTS 启动或用户点击停止时调用）。 */
    fun stopStIphonieTts(reason: String = "manual") {
        appendLog("debug", "[tts-isolation] stopping ST-iPhonie TTS (reason=$reason)")
        _stIphonieTtsStateFlow.value = _stIphonieTtsStateFlow.value.copy(phase = "idle", playing = false)
        broadcast("window.__rikkaStopIphonieTts && window.__rikkaStopIphonieTts();")
    }

    /** 切换 ST-iPhonie 暂停/继续播放。 */
    fun toggleStIphonieTts() {
        nativeTtsStopper?.let { stopper -> mainHandler.post { runCatching { stopper() } } }
        broadcast("window.__rikkaToggleIphonieTts && window.__rikkaToggleIphonieTts();")
    }

    /** 使用 ST-iPhonie 角色语音通道朗读指定楼层（与系统原生 TTS 通道独立区分）。 */
    fun playFloorWithStIphonie(floorIndex: Int) {
        nativeTtsStopper?.let { stopper -> mainHandler.post { runCatching { stopper() } } }
        eval("window.__rikkaPlayFloorIphonieTts && window.__rikkaPlayFloorIphonieTts($floorIndex);")
    }

    /**
     * 将常驻主运行时 WebView 挂载到可视悬浮窗容器（零二次白屏、零 IndexedDB 锁冲突、100% 保持小手机与卡脚本实时状态）。
     */
    fun attachHostWebViewToContainer(container: android.view.ViewGroup, target: String) {
        ensureStarted(container.context)
        val wv = webView ?: return
        runCatching {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.setBackgroundColor(android.graphics.Color.parseColor("#121318"))
            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
            container.addView(
                wv,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            val escaped = JSONObject.quote(target)
            wv.evaluateJavascript(
                "window.__rikkaOpenFloatingTarget && window.__rikkaOpenFloatingTarget($escaped);",
                null,
            )
        }.onFailure { e ->
            appendLog("error", "[floating] attachHostWebViewToContainer failed: ${e.message}")
        }
    }

    /**
     * 从可视悬浮窗容器安全摘除常驻主运行时 WebView（不销毁实例，继续在后台保持小手机语音与脚本运行）。
     */
    fun detachHostWebViewFromContainer(container: android.view.ViewGroup) {
        val wv = webView ?: return
        runCatching {
            wv.evaluateJavascript("window.__rikkaCloseFloatingStage && window.__rikkaCloseFloatingStage();", null)
            wv.clearFocus()
            wv.isFocusable = false
            wv.isFocusableInTouchMode = false
            if (wv.parent === container) {
                container.removeView(wv)
            }
        }
    }

    /** 处理 JS 侧生命周期事件（由桥回调触发）。 */
    fun handleJsLifecycleEvent(name: String, payloadJson: String = "{}") {
        when (name) {
            "mvu_ready" -> isMvuActive = true
            "mvu_error" -> isMvuActive = false
            "iphonie_tts_state" -> runCatching {
                val obj = JSONObject(payloadJson)
                val phase = obj.optString("phase", "idle")
                val playing = obj.optBoolean("playing", false)
                val msg = obj.optString("message", "")
                _stIphonieTtsStateFlow.value = StIphonieTtsState(
                    phase = phase,
                    playing = playing,
                    message = msg,
                )
                if (playing) {
                    nativeTtsStopper?.let { stopper ->
                        mainHandler.post { runCatching { stopper() } }
                    }
                }
            }
            "floating_windows_changed" -> runCatching {
                val obj = JSONObject(payloadJson)
                _floatingWindowsFlow.value = FloatingWindowSummary(
                    hasIphonie = obj.optBoolean("hasIphonie", false),
                    hasMemory = obj.optBoolean("hasMemory", false),
                    hasLittleWhiteBox = obj.optBoolean("hasLittleWhiteBox", false),
                    hasCardFloating = obj.optBoolean("hasCardFloating", false),
                    cardFloatingCount = obj.optInt("cardFloatingCount", 0),
                )
            }
            "request_open_floating_window" -> runCatching {
                val obj = JSONObject(payloadJson)
                val target = obj.optString("target", "iphonie").ifBlank { "iphonie" }
                requestOpenFloatingWindow(target)
            }
            // [v218.1] 面板渲染完成 → 主运行时补发 ST 的 RENDERED 事件。
            "panel_rendered" -> runCatching {
                val obj = JSONObject(payloadJson)
                val id = obj.optInt("messageId", -1)
                if (id >= 0) {
                    val evt = if (obj.optBoolean("isUser")) "user_message_rendered" else "character_message_rendered"
                    fireEvent(evt, "{\"args\":[$id]}")
                }
            }
        }
    }

    /** 发布 `/js` 执行结果到 UI 观察流（由桥回调触发）。 */
    fun publishJsResult(payloadJson: String) {
        runCatching {
            val obj = JSONObject(payloadJson)
            val ok = obj.optBoolean("ok")
            val raw = if (ok) obj.optString("value") else obj.optString("error")
            val text = if (raw.length > 400) raw.take(400) + "..." else raw
            _lastJsResult.value = JsResult(ok, text.ifBlank { "(empty)" }, ++jsResultSeq)
        }
    }

    val isPageReady: Boolean get() = pageReady
    val isStarted: Boolean get() = webView != null

    /** 最近一次 `/js` 执行结果（供 UI 展示）。 */
    val lastJsResult: StateFlow<JsResult?> get() = _lastJsResult

    // ==================== 生命周期 ====================

    /** 只设置资产拦截器上下文，不创建主运行时 WebView（扩展设置等辅助 WebView 用）。 */
    fun ensureContext(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        logLastProcessExitInfo(appCtx)
    }

    /**
     * 启动主运行时 WebView。
     *
     * ⚠️⚠️ [第6轮 P0-1 · 白条根因] `appContext` 必须**同步**赋值，绝不能放进 `mainHandler.post`。
     *
     * 真机铁证（2026-10-14 14:48:42.638 ~ 14:48:43.199）：
     *   · `MessageHtmlBlock` 的面板 WebView 在 `factory` 里先调 `ensureStarted(context)`，
     *     紧接着**同一帧内**就 `loadUrl(BASE_URL + "panel.html")`；
     *   · 旧实现里 `appContext` 是在 `post` 出去的 `createWebView()` 内部才赋值的，
     *     于是这段窗口里 `interceptAsset()` 走到 `openAsset()` 的
     *     `val ctx = appContext ?: return null` 直接返回 null；
     *   · `shouldInterceptRequest` 返回 null ⇒ WebView 真的去访问
     *     `appassets.androidplatform.net` ⇒ 加载失败（空白错误页）；
     *   · 页面里没有 runtime.js ⇒ `window.__rikkaPanelMount` 不存在
     *     ⇒ HTML 被暂存进 `__rikkaPanelPendingHtml` 后**永远挂不上**；
     *   · 面板于是停在 `MessageHtmlBlock.minHeightDp = 48`
     *     ⇒ 用户看到的「**白色的条**」（同卡的「主角图不显示 / 可修改处点不动」是同一个原因）。
     *   · 「用久了就好」= 主运行时 WebView 起来之后 / 面板被 LazyColumn 销毁重建时，
     *     `appContext` 已经有值了。
     *
     * 同步赋值后，`ensureStarted()` 一返回资产拦截器就可用（`createWebView` 内重复赋值无害）。
     */
    fun ensureStarted(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        logLastProcessExitInfo(appCtx)
        if (webView != null) return
        mainHandler.post {
            if (webView != null) return@post
            runCatching { createWebView(appCtx) }
                .onFailure { appendLog("error", "WebView 初始化失败: ${it.message}") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context) {
        appContext = context
        val wv = WebView(context)
        // [P1-2] 离屏主运行时 WebView 永不需要输入焦点，禁止其抢夺软键盘焦点
        wv.isFocusable = false
        wv.isFocusableInTouchMode = false
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.blockNetworkImage = false
        wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return interceptAsset(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                appendLog("info", "运行时页面就绪: $url")
                fireEvent("app_ready", "{}")
                // 若是渲染器崩溃后的自愈重建，补发一次 active_conversation_changed 恢复当前会话脚本与 MVU
                val activeConv = activeConversationId
                if (_rendererGeneration.value > 0 && !activeConv.isNullOrBlank()) {
                    fireEvent(
                        "active_conversation_changed",
                        buildJsonObject { put("conversation_id", activeConv) }.toString(),
                    )
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail?,
            ): Boolean {
                val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    detail?.didCrash() == true
                } else false
                val priority = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    detail?.rendererPriorityAtExit() ?: -1
                } else -1
                val gen = bumpRendererGeneration()
                appendLog(
                    "error",
                    "[webview] renderer gone (host) didCrash=$didCrash priority=$priority gen=$gen -> rebuilding runtime WebView",
                )
                mainHandler.post {
                    if (webView === view) {
                        webView = null
                        pageReady = false
                        isMvuActive = false
                    }
                    runCatching {
                        view.stopLoading()
                        view.destroy()
                    }
                    val ctx = appContext
                    if (ctx != null && webView == null) {
                        runCatching { createWebView(ctx) }
                            .onFailure { appendLog("error", "[webview] host rebuild failed: ${it.message}") }
                    }
                }
                // 必须返回 true，阻止 Chromium 调 abort() 连带强杀整个 App 进程
                return true
            }
        }
        wv.addJavascriptInterface(TavernJsBridge(), "RikkaBridge")
        webView = wv
        wv.loadUrl("${BASE_URL}index.html")
    }

    /** 资产拦截：/st-runtime/... → assets/st-runtime/...（含 ES module MIME）；角色立绘/CG资产多级回退；CDN 请求 → 本地 vendor。 */
    internal fun interceptAsset(uri: Uri): WebResourceResponse? {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "embeded" || scheme == "embedded" || scheme == "ccdefault") {
            val ctx = appContext ?: return notFound()
            val rawTarget = uri.toString()
                .removePrefix("embeded://")
                .removePrefix("embedded://")
                .removePrefix("ccdefault:")
            val resolved = resolveLocalCharacterAssetFile(ctx, rawTarget)
            return if (resolved != null) openFileResponse(resolved) ?: notFound() else notFound()
        }
        if (uri.host != HOST && uri.host != "st.rikka.local") return interceptCdn(uri)
        val path = uri.path ?: return notFound()
        // 0) 第三方扩展资源（真实文件端点）：
        if (path.startsWith("/scripts/extensions/third-party/")) return openThirdPartyAsset(path)
        if (path.startsWith("/st-runtime/scripts/extensions/third-party/")) {
            return openThirdPartyAsset(path.removePrefix("/st-runtime"))
        }
        if (path.startsWith("/third-party/")) {
            return openThirdPartyAsset("/scripts/extensions$path")
        }
        // 0.15) [ST 1.19.0 / LittleWhiteBox / ST-iPhonie] 用户文件与图片存储端点：/user/files/* 与 /user/images/*
        if (path.startsWith("/user/files/") || path.startsWith("/user/images/")) {
            val ctx = appContext ?: return notFound()
            val subRel = path.removePrefix("/user/")
            val allowedRoot = runCatching { File(ctx.filesDir, "tavern-user").canonicalFile }.getOrNull()
            val targetFile = runCatching { File(File(ctx.filesDir, "tavern-user"), subRel).canonicalFile }.getOrNull()
            if (allowedRoot != null && targetFile != null &&
                targetFile.path.startsWith(allowedRoot.path + File.separator) &&
                targetFile.isFile
            ) {
                return openFileResponse(targetFile) ?: notFound()
            }
            resolveLocalCharacterAssetFile(ctx, subRel)?.let { return openFileResponse(it) ?: notFound() }
            return notFound()
        }
        // 0.2) 本地角色资产（.charx 解压资产、表情立绘、/characters/、/api/sprites/ 与 /charx_assets/lookup/）：
        if (path.startsWith("/charx_assets/") ||
            path.startsWith("/sprites/") ||
            path.startsWith("/characters/") ||
            path.startsWith("/api/sprites/")
        ) {
            val ctx = appContext ?: return notFound()
            val resolved = resolveLocalCharacterAssetFile(ctx, path)
            if (resolved != null) {
                return openFileResponse(resolved) ?: notFound()
            }
            return notFound()
        }
        // 0.5) [v228 S1] ST `/version` 端点
        if (path == "/version") return versionResponse()
        // 1) ST 核心模块兼容层
        routeStCompat(path)?.let { return it }
        // 2) /npm/ 前缀 → st-runtime/vendor/npm/
        val assetPath = when {
            path.startsWith("/npm/") -> "st-runtime/vendor$path"
            else -> path.trimStart('/')
        }
        openAsset(assetPath)?.let { return it }
        // 3) 若 panel.html 内相对路径图片（如 <img src="织乃_zcw_2.png"> 或 <img src="sprites/xxx.webp">）
        //    解析到了 /st-runtime/<rel> 且非内置静态资源，则自动回退到角色立绘/charx_assets 查找！
        val ctx = appContext
        if (ctx != null) {
            val candidateRel = path.removePrefix("/st-runtime/").trimStart('/')
            if (candidateRel.isNotEmpty() && !candidateRel.endsWith(".js") && !candidateRel.endsWith(".html")) {
                resolveLocalCharacterAssetFile(ctx, candidateRel)?.let {
                    return openFileResponse(it) ?: notFound()
                }
            }
        }
        return notFound()
    }

    /**
     * 统一解析角色卡立绘 / CG / CharX 资产文件（供 WebView `interceptAsset` 与原生 `ZoomableAsyncImage` 共同调用）：
     * 1. 先检查 `filesDir/charx_assets`、`filesDir/sprites`、`filesDir/tavern-user` 下的精确路径；
     * 2. 若精确路径不存在（例如卡片正则写了 `.png` 但实际资源为 `.webp`/`.jpg`，或按 `asset.name` 如 `织乃_zcw_2` 引用），
     *    调用 [me.rerere.rikkahub.data.st.expressions.SpriteRepository.resolveAnySpriteOrAssetFile] 按文件名/stem 跨扩展名检索；
     * 3. 若磁盘上仍未找到（例如用户在旧版本已导入该角色卡，当时 `data.assets` 未落盘），
     *    直接检查当前活跃助手内存中的 `tavernData.assets` 并即时解码落盘后返回！
     */
    internal fun resolveLocalCharacterAssetFile(ctx: Context, rawPath: String): File? {
        val clean = runCatching { Uri.decode(rawPath) }.getOrDefault(rawPath)
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .removePrefix("embeded://")
            .removePrefix("embedded://")
            .removePrefix("ccdefault:")
            .trimStart('/')
        if (clean.isBlank()) return null

        val topDir = clean.substringBefore('/')
        if (topDir == "charx_assets" || topDir == "sprites") {
            val subAfterTop = clean.substringAfter('/', "")
            if (!subAfterTop.startsWith("lookup/")) {
                val allowedRoot = runCatching { File(ctx.filesDir, topDir).canonicalFile }.getOrNull()
                val exactFile = runCatching { File(ctx.filesDir, clean).canonicalFile }.getOrNull()
                if (allowedRoot != null && exactFile != null &&
                    exactFile.path.startsWith(allowedRoot.path + File.separator) &&
                    exactFile.isFile
                ) {
                    return exactFile
                }
            }
        }

        val explicitAssistantId = if (clean.startsWith("charx_assets/") && !clean.startsWith("charx_assets/lookup/")) {
            clean.removePrefix("charx_assets/").substringBefore('/').let { runCatching { kotlin.uuid.Uuid.parse(it) }.getOrNull() }
        } else null
        val queryTarget = when {
            clean.startsWith("charx_assets/lookup/") -> clean.removePrefix("charx_assets/lookup/")
            clean.startsWith("charx_assets/") -> clean.removePrefix("charx_assets/").substringAfter('/', clean.substringAfterLast('/'))
            clean.startsWith("sprites/") -> clean.removePrefix("sprites/").substringAfter('/', clean.substringAfterLast('/'))
            clean.startsWith("characters/") -> clean.removePrefix("characters/").substringAfter('/', clean.substringAfterLast('/'))
            clean.startsWith("api/sprites/") -> clean.removePrefix("api/sprites/").substringAfter('/', clean.substringAfterLast('/'))
            else -> clean
        }

        val activeAst = activeAssistant
        me.rerere.rikkahub.data.st.expressions.SpriteRepository.resolveAnySpriteOrAssetFile(
            context = ctx,
            assistantId = explicitAssistantId ?: activeAst?.id,
            rawNameOrPath = queryTarget,
        )?.let { return it }

        // 惰性从当前助手内存 tavernData.assets 解码落盘（兼容旧版本已导入未落盘的角色卡）
        if (activeAst != null) {
            val assets = activeAst.tavernData?.assets.orEmpty()
            if (assets.isNotEmpty()) {
                val targetBase = queryTarget.substringAfterLast('/')
                val targetStem = targetBase.substringBeforeLast('.').ifBlank { targetBase }
                val safeTargetStem = me.rerere.rikkahub.data.st.expressions.SpriteRepository.sanitizeAssetStem(targetStem)
                val matchedAsset = assets.firstOrNull { a ->
                    val aBase = a.name.substringAfterLast('/')
                    val aStem = aBase.substringBeforeLast('.').ifBlank { aBase }
                    val aSafe = me.rerere.rikkahub.data.st.expressions.SpriteRepository.sanitizeAssetStem(aStem)
                    a.name.equals(targetBase, ignoreCase = true) ||
                        aStem.equals(targetStem, ignoreCase = true) ||
                        aSafe.equals(safeTargetStem, ignoreCase = true)
                }
                if (matchedAsset != null) {
                    val decoded = me.rerere.rikkahub.data.st.expressions.SpriteRepository.decodeDataUri(matchedAsset.uri)
                    if (decoded != null) {
                        val ext = (decoded.second ?: matchedAsset.ext.ifBlank { "png" }).lowercase()
                        val outDir = me.rerere.rikkahub.data.st.expressions.SpriteRepository.charxAssetDir(ctx, activeAst.id)
                        val outFile = File(outDir, "${safeTargetStem.ifBlank { "sprite" }}.$ext")
                        runCatching { outFile.writeBytes(decoded.first) }
                        if (outFile.isFile) return outFile
                    }
                }
            }
        }
        return null
    }

    /**
     * 供 Coil (`ZoomableAsyncImage`) 解析本地角色立绘/CG 资产或 `data:image/...` URI。
     */
    fun resolveLocalAssetForCoil(context: Context, rawUrlOrPath: String): Any? {
        val m = rawUrlOrPath.trim()
        if (m.isEmpty()) return null
        if (m.startsWith("embeded://", ignoreCase = true) ||
            m.startsWith("embedded://", ignoreCase = true) ||
            m.startsWith("ccdefault:", ignoreCase = true)
        ) {
            return resolveLocalCharacterAssetFile(context, m)
        }
        val localPrefix = listOf(
            "https://appassets.androidplatform.net/",
            "https://st.rikka.local/",
            "http://appassets.androidplatform.net/",
            "http://st.rikka.local/",
        ).firstOrNull { m.startsWith(it, ignoreCase = true) }
        if (localPrefix != null) {
            val rel = m.substring(localPrefix.length)
            return resolveLocalCharacterAssetFile(context, rel)
        }
        if (m.startsWith("/charx_assets/") ||
            m.startsWith("/sprites/") ||
            m.startsWith("/characters/") ||
            m.startsWith("/api/sprites/") ||
            m.startsWith("/user/images/")
        ) {
            return resolveLocalCharacterAssetFile(context, m)
        }
        if (!m.contains("://") && !m.startsWith("data:", ignoreCase = true) && !m.startsWith("content:", ignoreCase = true) && !m.startsWith("file:", ignoreCase = true)) {
            resolveLocalCharacterAssetFile(context, m)?.let { return it }
        }
        return null
    }

    /** 打开本地资产（命中返回 200 响应，未命中返回 null）。 */
    private fun openAsset(assetPath: String): WebResourceResponse? {
        val ctx = appContext ?: return null
        return try {
            val stream = ctx.assets.open(assetPath)
            WebResourceResponse(
                mimeOf(assetPath),
                null,
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache",
                ),
                stream,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 第三方扩展文件伺服：/scripts/extensions/third-party/<folder>/<path> → 真实文件。
     * 路径安全规则对齐 TauriTavern（拒 `..` / 分隔符 / 控制字符；canonical 前缀双保险）。
     */
    private fun openThirdPartyAsset(path: String): WebResourceResponse? {
        val ctx = appContext ?: return notFound()
        val rest = path.removePrefix("/scripts/extensions/third-party/")
        val segs = rest.split('/').filter { it.isNotEmpty() }
        if (segs.size < 2) return notFound()
        val folder = segs.first()
        if (!isSafeExtensionSegment(folder)) return notFound()
        val rel = segs.drop(1).joinToString("/")
        val file = resolveExtensionFile(extensionsBaseDir(ctx), folder, rel) ?: return notFound()
        return openFileResponse(file) ?: notFound()
    }

    /** 打开文件系统文件（命中返回 200 响应，未命中返回 null）。 */
    private fun openFileResponse(file: File): WebResourceResponse? {
        return try {
            val stream = FileInputStream(file)
            WebResourceResponse(
                mimeOf(file.name),
                null,
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache",
                ),
                stream,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** ST 核心模块兼容 shim 路由（返回 null 表示该路径不属于兼容层，放行后续资产链路）。 */
    private fun routeStCompat(path: String): WebResourceResponse? {
        val shimPath = when {
            path == "/script.js" -> "st-runtime/st-compat/script.js"
            path == "/lib.js" -> "st-runtime/st-compat/lib.js"
            path == "/scripts/events.js" -> "st-runtime/vendor/st/events.js"
            path == "/scripts/eventemitter.js" -> "st-runtime/vendor/st/eventemitter.js"
            path.startsWith("/scripts/") -> {
                val rel = path.removePrefix("/scripts/")
                if (!isPlausibleStModulePath(rel)) return null
                // 根级文件（/script.js、/lib.js）不经 /scripts/ 暴露（对齐 ST 的真实布局，避免模块双实例）；
                // 其余未命中 st-compat 资源时由 openAsset 自然 404。
                if (rel == "script.js" || rel == "lib.js") return notFound()
                "st-runtime/st-compat/$rel"
            }
            else -> return null
        }
        return openAsset(shimPath) ?: run {
            appendLog("error", "[compat] 缺少 st-compat shim: $path → $shimPath（第三方扩展会整模块加载失败）")
            notFound()
        }
    }

    /** 兼容层路径形态校验：`xxx.js` 或 `a/b.js`（段字符白名单，防目录穿越）。 */
    private fun isPlausibleStModulePath(rel: String): Boolean {
        if (!rel.endsWith(".js")) return false
        val segs = rel.split('/')
        if (segs.isEmpty()) return false
        return segs.all { seg ->
            seg.isNotEmpty() && seg != "." && seg != ".." && seg.none { c ->
                !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.')
            }
        }
    }

    /**
     * jsDelivr CDN 本地化（卡脚本里的裸 `import 'https://cdn.jsdelivr.net/...'`）：
     * - /gh/<user>/<repo>@<ver>/<path> → vendor/gh/...
     * - /npm/<pkg>@<ver>/<path>        → vendor/npm/...（未命中时尝试去版本回退）
     * 未命中本地资产时返回 null 放行真实网络（在线可用，离线降级为加载失败）。
     */
    private fun interceptCdn(uri: Uri): WebResourceResponse? {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path.orEmpty()
        val lowerPath = path.lowercase(Locale.ROOT)

        // 0) 拦截 st-memory-enhancement 版本检查等外网阻塞探针，0ms 离线返回
        if (host == "api.muyoo.com.cn") {
            return WebResourceResponse(
                "application/json",
                "utf-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                "{\"success\":true,\"isLatest\":true}".byteInputStream(Charsets.UTF_8),
            )
        }

        // 1) 拦截无 VPN 下导致 WebView 卡死 15~30s 的 Google Fonts / FontAwesome 等外网字体与样式表
        if (host in OFFLINE_FONT_AND_STYLE_HOSTS) {
            return emptyOfflineStyleOrFontResponse(lowerPath)
        }

        // 2) CDN 本地化优先匹配 vendor/
        if (host in CDN_HOSTS) {
            if (path.isNotEmpty()) {
                openAsset("st-runtime/vendor$path")?.let { return it }
                deversionNpm(path)?.let { de ->
                    openAsset("st-runtime/vendor$de")?.let { return it }
                }
                // 针对 cdnjs / bootcdn / unpkg / jsdelivr 常见库文件名映射到内置 vendor/lib/
                val fileName = lowerPath.substringAfterLast('/')
                when {
                    fileName.startsWith("jquery") && fileName.endsWith(".js") ->
                        openAsset("st-runtime/vendor/lib/jquery-3.5.1.min.js")?.let { return it }
                    fileName.startsWith("lodash") && fileName.endsWith(".js") ->
                        openAsset("st-runtime/vendor/lib/lodash.min.js")?.let { return it }
                    fileName.startsWith("toastr") && fileName.endsWith(".js") ->
                        openAsset("st-runtime/vendor/lib/toastr.min.js")?.let { return it }
                    fileName.startsWith("select2") && fileName.endsWith(".js") ->
                        openAsset("st-runtime/vendor/lib/select2.min.js")?.let { return it }
                    fileName.startsWith("select2") && fileName.endsWith(".css") ->
                        openAsset("st-runtime/vendor/lib/select2.min.css")?.let { return it }
                    fileName.startsWith("katex") && fileName.endsWith(".js") ->
                        openAsset("st-runtime/vendor/katex/katex.min.js")?.let { return it }
                    fileName.startsWith("katex") && fileName.endsWith(".css") ->
                        openAsset("st-runtime/vendor/katex/katex.min.css")?.let { return it }
                    (fileName.contains("font-awesome") || fileName.contains("fontawesome") || fileName.endsWith(".css") || fileName.endsWith(".woff2") || fileName.endsWith(".woff") || fileName.endsWith(".ttf")) ->
                        return emptyOfflineStyleOrFontResponse(lowerPath)
                }
            }
        }
        return null
    }

    private fun emptyOfflineStyleOrFontResponse(lowerPath: String): WebResourceResponse {
        val isFont = lowerPath.endsWith(".woff2") || lowerPath.endsWith(".woff") || lowerPath.endsWith(".ttf") || lowerPath.endsWith(".otf")
        val mime = if (isFont) "font/woff2" else "text/css"
        val bytes = if (isFont) ByteArray(0) else "/* offline css shim */".toByteArray(Charsets.UTF_8)
        return WebResourceResponse(
            mime,
            "utf-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "max-age=86400",
            ),
            bytes.inputStream(),
        )
    }

    /** /npm/name@1.2.3/rest → /npm/name/rest（兼容 scoped 包名 @scope/name@1.2.3）。 */
    private fun deversionNpm(path: String): String? {
        if (!path.startsWith("/npm/")) return null
        val m = Regex("^/npm/((?:@[^/]+/)?[^/@]+)@[^/]+(.*)$").find(path) ?: return null
        return "/npm/${m.groupValues[1]}${m.groupValues[2]}"
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)

    /**
     * [v228 S1] ST `/version` 响应体（canonical 形状，对齐 ST `src/util.js:164-165` + `src/server-main.js:272-275`）。
     *
     * JSR 只读 `pkgVersion`；TauriTavern 读 `tauriVersion || pkgVersion` → 给全 canonical 字段更稳。
     * 判据（为什么是 1.13.5，而不是 1.15.0/1.16.0）：`notes/recon-version-gate-20261008.md` §4 ——
     *   · 1.13.5 打开 G1/G2/G3（swipe 清理 / macro 走 GENERATE_AFTER_DATA / preset 删除重命名同步）；
     *   · 1.15.0 会把生成参数构造切到宿主**桩**实现（payload 缩水）+ 缺 MacroRegistry 模块（宏文档 404）；
     *   · 1.16.0 仅 TauriTavern 有意义（rikkaST 无 `__TAURITAVERN__`，零收益）。
     * ⚠️ 与 JS 侧 runtime.js 的 `TAVERN_ST_COMPAT_VERSION` 必须逐字一致（回归测试会比对）。
     */
    private fun versionResponse(): WebResourceResponse {
        val body = org.json.JSONObject().apply {
            put("agent", "SillyTavern:$TAVERN_ST_COMPAT_VERSION:rikkaST")
            put("pkgVersion", TAVERN_ST_COMPAT_VERSION)
            put("gitRevision", org.json.JSONObject.NULL)
            put("gitBranch", org.json.JSONObject.NULL)
            put("commitDate", org.json.JSONObject.NULL)
            put("isLatest", true)
        }.toString()
        return WebResourceResponse(
            "application/json",
            "utf-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache",
            ),
            body.byteInputStream(Charsets.UTF_8),
        )
    }

    internal fun mimeOf(path: String): String {
        val clean = path.substringBefore('?').substringBefore('#').trim().lowercase(Locale.ROOT)
        return when {
            clean.endsWith(".html") || clean.endsWith(".htm") -> "text/html"
            clean.endsWith(".js") || clean.endsWith(".mjs") || clean.endsWith("+esm") -> "application/javascript"
            clean.endsWith(".css") -> "text/css"
            clean.endsWith(".json") || clean.endsWith(".map") -> "application/json"
            clean.endsWith(".wasm") -> "application/wasm"
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".gif") -> "image/gif"
            clean.endsWith(".svg") -> "image/svg+xml"
            clean.endsWith(".avif") -> "image/avif"
            clean.endsWith(".bmp") -> "image/bmp"
            clean.endsWith(".ico") -> "image/x-icon"
            clean.endsWith(".woff2") -> "font/woff2"
            clean.endsWith(".woff") -> "font/woff"
            clean.endsWith(".ttf") -> "font/ttf"
            clean.endsWith(".otf") -> "font/otf"
            clean.endsWith(".mp3") -> "audio/mpeg"
            clean.endsWith(".wav") -> "audio/wav"
            clean.endsWith(".ogg") -> "audio/ogg"
            clean.endsWith(".mp4") -> "video/mp4"
            clean.endsWith(".webm") -> "video/webm"
            clean.endsWith(".xml") -> "application/xml"
            clean.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * [v228 S1] `/version` 上报的 ST 兼容版本号（**能力闸门口径，不是 rikkaST 自身版本**）。
     * 改动它 = 一次性切换所有下游扩展的能力分支，必须同步改 `assets/st-runtime/runtime.js` 的
     * `TAVERN_ST_COMPAT_VERSION`，并更新 `notes/recon-version-gate-20261008.md` §4 的判据。
     */
    const val TAVERN_ST_COMPAT_VERSION = "1.19.0"

    // ==================== 执行 / 事件 ====================

    fun eval(script: String, callback: ((String) -> Unit)? = null) {
        mainHandler.post {
            val wv = webView ?: return@post
            if (callback != null) {
                wv.evaluateJavascript(script) { value -> callback(value ?: "null") }
            } else {
                wv.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * [v222] 会话级宿主事件出口：给够不到 `ChatService.fireTavernEvent`（private）的调用点复用，
     * 语义完全一致 —— **焦点会话守卫 + developerMode 写前同步**（懒同步，保证非本 helper 发的事件也写 [event] 日志）。
     *
     * @param conversationId null = 不做守卫（如 app 级事件）
     * @param developerMode 非 null 时先同步到 [setDeveloperMode]，决定是否写日志
     */
    fun fireConversationEvent(
        conversationId: String?,
        name: String,
        payloadJson: String = "{}",
        developerMode: Boolean? = null,
    ) {
        if (developerMode != null) setDeveloperMode(developerMode)
        val active = activeConversationId
        if (conversationId != null && active != null && conversationId != active) return
        fireEvent(name, payloadJson)
    }

    /** Kotlin → JS 事件。 */
    fun fireEvent(name: String, payloadJson: String = "{}", onResult: ((String) -> Unit)? = null) {
        if (developerMode) appendLog("debug", "[event] $name $payloadJson")
        val script = "window.__rikkaEmit && window.__rikkaEmit(${JSONObject.quote(name)}, $payloadJson);"
        if (onResult != null) eval(script, onResult) else eval(script)
    }

    /** 在运行时（WebView）中执行一段 JS 脚本（/js 命令）。结果经 emitEvent("js_result") 异步回传。 */
    fun runScript(code: String) {
        eval("window.__rikkaRunScript && window.__rikkaRunScript(${JSONObject.quote(code)});")
    }

    /** 手动重载 MVU（v182 C2：错误 toast / 命令入口调用），复用 JS 侧 __rikkaMvuReload。 */
    fun reloadMvu() {
        eval("window.__rikkaMvuReload && window.__rikkaMvuReload();")
    }

    /** 脚本按钮点击派发（UI → JS）：经 __rikkaButtonHub.fire 触发脚本侧按钮事件。 */
    fun fireScriptButton(button: TavernScriptButton) {
        val sid = JSONObject.quote(button.scriptId)
        val name = JSONObject.quote(button.name)
        broadcast("window.__rikkaButtonHub && window.__rikkaButtonHub.fire && window.__rikkaButtonHub.fire($sid, $name);")
    }

    /**
     * EJS 批量渲染（发送前，ST-Prompt-Template 语义）：
     * 挂起等待运行时 JS 回调（RikkaBridge.onEjsResult）；超时/未就绪返回 null（调用方保留原文）。
     */
    /**
     * [v234 S2] 函数宏批量执行（Kotlin → JS）：requestsJson = [{id, name, nonce}, ...]，
     * JS 侧调 MacrosParser 函数宏并经 onMacrosResult 回传 [{id, value}, ...]；超时/未就绪返回 null。
     */
    suspend fun runMacrosBatch(requestsJson: String, timeoutMs: Long = 10_000): List<Pair<Int, String>>? {
        if (isInJavaBridgeCall()) return null
        val wv = webView ?: return null
        val seq = macrosSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pendingMacros[seq] = deferred
        mainHandler.post {
            wv.evaluateJavascript(
                "window.__rikkaEvalMacrosBatch && window.__rikkaEvalMacrosBatch($seq, ${JSONObject.quote(requestsJson)});",
                null,
            )
        }
        return try {
            val raw = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (raw == null) {
                appendLog("warn", "[macros] batch timeout after ${timeoutMs}ms")
                null
            } else {
                runCatching {
                    val arr = org.json.JSONArray(raw)
                    (0 until arr.length()).map { i ->
                        val o = arr.optJSONObject(i) ?: org.json.JSONObject()
                        Pair(o.optInt("id", -1), o.optString("value", ""))
                    }
                }.getOrElse { e ->
                    appendLog("error", "[macros] decode failed: ${e.message}")
                    null
                }
            }
        } finally {
            pendingMacros.remove(seq)
        }
    }

    suspend fun renderEjsBatch(texts: List<String>, timeoutMs: Long = 10_000): List<String>? {
        if (texts.isEmpty()) return texts
        if (isInJavaBridgeCall()) return null
        val wv = webView ?: return null
        val seq = ejsSeqCounter.incrementAndGet()
        val textsArray = org.json.JSONArray()
        texts.forEach { textsArray.put(it) }
        val deferred = CompletableDeferred<String>()
        pendingEjs[seq] = deferred
        mainHandler.post {
            wv.evaluateJavascript(
                "window.__rikkaEvalEjsBatch && window.__rikkaEvalEjsBatch($seq, ${JSONObject.quote(textsArray.toString())});",
                null,
            )
        }
        return try {
            val raw = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (raw == null) {
                appendLog("warn", "[ejs] render timeout after ${timeoutMs}ms")
                null
            } else {
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { e ->
                    appendLog("error", "[ejs] decode failed: ${e.message}")
                    null
                }
            }
        } finally {
            pendingEjs.remove(seq)
        }
    }

    /**
     * [v211] 生成前钩子（ST CHAT_COMPLETION_SETTINGS_READY 语义）：
     * 把最终 messages 交给运行时 WebView，让扩展（ST-PT 的 EJS 模板 / MVU 的 <UpdateVariable> 剥离）
     * 就地改写，返回改写后的 JSON 数组；超时 / WebView 未就绪 / 无监听器返回 null（调用方保留原文，零开销直通）。
     *
     * JS 侧契约：window.__rikkaRunGenerationHooks(seq, messagesJson) -> JSON 字符串 | null
     * 回调：RikkaBridge.onGenerationHooksResult(seq, json)
     */
    suspend fun runGenerationHooks(messagesJson: String, timeoutMs: Long = 15_000): String? {
        if (isInJavaBridgeCall()) return null
        val wv = webView ?: return null
        if (!pageReady) return null
        val seq = genHookSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pendingGenHooks[seq] = deferred
        mainHandler.post {
            wv.evaluateJavascript(
                "window.__rikkaRunGenerationHooks && window.__rikkaRunGenerationHooks($seq, ${JSONObject.quote(messagesJson)});",
                null,
            )
        }
        return try {
            val raw = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (raw == null) {
                appendLog("warn", "[gen-hooks] timeout after ${timeoutMs}ms (seq=$seq)")
                null
            } else {
                runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }
                    .getOrNull()
                    ?.takeIf { it != "null" && it.isNotBlank() }
            }
        } finally {
            pendingGenHooks.remove(seq)
        }
    }

    /** 查询 MVU 状态（JSON 文本回调）。 */
    fun queryMvuStatus(callback: (String) -> Unit) {
        eval("window.__rikkaMvuStatus ? JSON.stringify(window.__rikkaMvuStatus()) : '{}'") { raw ->
            val text = runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }.getOrDefault(raw)
            callback(text)
        }
    }

    fun setActiveConversation(conversationId: String?) {
        activeConversationId = conversationId
        fireEvent(
            "active_conversation_changed",
            buildJsonObject { put("conversation_id", conversationId ?: "") }.toString(),
        )
    }

    /** 重新扫描并通知主运行时与辅助 WebView 重载第三方扩展。 */
    fun reloadThirdPartyExtensions() {
        broadcast("window.__rikkaReloadThirdPartyExtensions && window.__rikkaReloadThirdPartyExtensions();")
        fireEvent(EVENT_SETTINGS_UPDATED, "{}")
    }

    // ==================== [v214] JS 写回桥（世界书 / 面板表单发送） ====================

    /** [v214] JS `RikkaBridge.updateLorebookEntries`：整本书条目 disable 补丁 → 宿主持久化。 */
    fun dispatchLorebookCreate(bookName: String, entriesJson: String, replaceIfExists: Boolean): Boolean {
        val creator = lorebookCreator ?: run {
            appendLog("warn", "[lorebook] createOrReplaceLorebook: creator not wired (name='$bookName')")
            return false
        }
        val ok = runCatching { creator(bookName, entriesJson, replaceIfExists) }.getOrDefault(false)
        appendLog("info", "[lorebook] createOrReplaceLorebook name='$bookName' replace=$replaceIfExists ok=$ok")
        return ok
    }

    fun dispatchLorebookDelete(bookName: String): Boolean {
        val deleter = lorebookDeleter ?: run {
            appendLog("warn", "[lorebook] deleteLorebook: deleter not wired (name='$bookName')")
            return false
        }
        val ok = runCatching { deleter(bookName) }.getOrDefault(false)
        appendLog("info", "[lorebook] deleteLorebook name='$bookName' ok=$ok")
        return ok
    }

    fun dispatchLorebookRebind(scope: String, namesJson: String): Boolean {
        val rebinder = lorebookRebinder ?: run {
            appendLog("warn", "[lorebook] rebindLorebooks: rebinder not wired (scope='$scope')")
            return false
        }
        val ok = runCatching { rebinder(scope, namesJson) }.getOrDefault(false)
        appendLog("info", "[lorebook] rebindLorebooks scope='$scope' names=$namesJson ok=$ok")
        return ok
    }

    /**
     * 调用 WebView `window.__rikkaExecuteJsSlashCommand` 执行通过 `SlashCommandParser.addCommandObject` 注册的 JS 扩展斜杠命令。
     * @return 若 JS 侧已注册该命令则返回执行结果字符串；若未注册或 WebView 未就绪则返回 `null`。
     */
    suspend fun executeJsSlashCommand(
        name: String,
        namedArgs: Map<String, String>,
        unnamedArgs: String,
        pipe: String = "",
        timeoutMs: Long = 5_000L,
    ): String? {
        if (isInJavaBridgeCall()) return null
        if (name.trim().lowercase() !in jsSlashCommandNames) return null
        val wv = webView ?: return null
        if (!pageReady) return null
        val seq = slashSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pendingSlash[seq] = deferred
        val namedObj = JSONObject()
        namedArgs.forEach { (k, v) -> namedObj.put(k, v) }
        val namedJsonQuoted = JSONObject.quote(namedObj.toString())
        val nameQuoted = JSONObject.quote(name)
        val unnamedQuoted = JSONObject.quote(unnamedArgs)
        val pipeQuoted = JSONObject.quote(pipe)
        mainHandler.post {
            runCatching {
                wv.evaluateJavascript(
                    "if (window.__rikkaExecuteJsSlashCommand) { window.__rikkaExecuteJsSlashCommand($nameQuoted, $namedJsonQuoted, $unnamedQuoted, $pipeQuoted, $seq); } else if (window.RikkaBridge && window.RikkaBridge.onSlashResult) { window.RikkaBridge.onSlashResult($seq, '{\"found\":false}'); }",
                    null,
                )
            }.onFailure {
                pendingSlash.remove(seq)?.complete("{\"found\":false}")
            }
        }
        return try {
            val raw = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
            val obj = JSONObject(raw)
            if (obj.optBoolean("found", false)) {
                obj.optString("value", "")
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            pendingSlash.remove(seq)
        }
    }

    /**
     * 调用 WebView `window.__rikkaInvokeJsFunctionTool` 执行通过 `registerFunctionTool` 注册的 JS 扩展函数工具。
     */
    suspend fun executeJsFunctionTool(
        name: String,
        argsJson: String,
        timeoutMs: Long = 15_000L,
    ): String? {
        if (isInJavaBridgeCall()) return null
        if (!hasJsFunctionTool(name)) return null
        val wv = webView ?: return null
        if (!pageReady) return null
        val seq = functionToolSeqCounter.incrementAndGet()
        val deferred = CompletableDeferred<String>()
        pendingFunctionTools[seq] = deferred
        val nameQuoted = JSONObject.quote(name)
        val argsQuoted = JSONObject.quote(argsJson.ifBlank { "{}" })
        mainHandler.post {
            runCatching {
                wv.evaluateJavascript(
                    "if (window.__rikkaInvokeJsFunctionTool) { window.__rikkaInvokeJsFunctionTool($nameQuoted, $argsQuoted, $seq); } else if (window.RikkaBridge && window.RikkaBridge.onFunctionToolResult) { window.RikkaBridge.onFunctionToolResult($seq, '{\"ok\":false,\"error\":\"bridge missing\"}'); }",
                    null,
                )
            }.onFailure {
                pendingFunctionTools.remove(seq)?.complete("{\"ok\":false,\"error\":\"eval failed\"}")
            }
        }
        return try {
            val raw = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
            val obj = JSONObject(raw)
            if (obj.optBoolean("ok", false)) {
                obj.optString("value", "")
            } else {
                "Error: ${obj.optString("error", "tool execution failed")}"
            }
        } catch (_: Exception) {
            null
        } finally {
            pendingFunctionTools.remove(seq)
        }
    }

    fun dispatchLorebookUpdate(bookName: String, entriesJson: String): Boolean {
        val updater = lorebookUpdater ?: run {
            appendLog("warn", "[lorebook] updateLorebookEntries: updater not wired (name='$bookName')")
            return false
        }
        val ok = runCatching { updater(bookName, entriesJson) }.getOrDefault(false)
        appendLog("info", "[lorebook] updateLorebookEntries name='$bookName' patches=${countLorebookPatches(entriesJson)} ok=$ok")
        return ok
    }

    /** 统计世界书补丁条目数（[{uid,disable},...]）。 */
    private fun countLorebookPatches(json: String): Int =
        if (json.isBlank()) 0 else Regex("\"uid\"").findAll(json).count()

    /**
     * [v214] JS `RikkaBridge.sendUserMessage`：面板表单提交 → 宿主按普通用户消息发送。
     * 调度到主线程执行，避免在 JavaBridge 线程触碰会话状态；返回是否受理（未接线 false）。
     */
    fun dispatchUserMessage(text: String): Boolean {
        val sender = userMessageSender ?: return false
        if (text.isBlank()) return false
        mainHandler.post { runCatching { sender(text) } }
        return true
    }

    // ==================== 日志 ====================

    fun appendLog(level: String, message: String) {
        val ts = synchronized(logTimeFormat) { logTimeFormat.format(Date()) }
        val line = "[$ts][$level] $message"
        logs.offer(line)
        while (logs.size > MAX_LOGS) logs.poll()
        runCatching { android.util.Log.d(TAG, line) }
        persistLogAsync(line)
    }

    /** 异步追加写入持久化日志文件（在 Dispatchers.IO 后台执行，杜绝阻塞主线程造成 UI 卡顿）。 */
    private fun persistLogAsync(line: String) {
        if (appContext == null) return
        runtimeScope.launch(Dispatchers.IO) {
            persistLogSync(line)
        }
    }

    private fun persistLogSync(line: String) {
        val ctx = appContext ?: return
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val file = File(dir, LOG_FILE_NAME)
            synchronized(logFileLock) {
                if (file.exists() && file.length() > LOG_FILE_MAX_BYTES) {
                    val tail = file.readText().takeLast((LOG_FILE_MAX_BYTES / 2).toInt())
                    file.writeText(tail.substringAfter('\n', tail))
                }
                file.appendText(line + "\n")
            }
        }
    }

    fun logs(): List<String> = logs.toList()

    fun clearLogs() = logs.clear()

    // ==================== JS 桥 ====================

    /**
     * JS → Kotlin 同步桥。方法均在 JavaBridge 线程调用（不阻塞主线程），
     * 对内存态变量存储进行同步读写。
     */
    @Keep
    class TavernJsBridge {
        @JavascriptInterface
        fun getVersion(): String = TAVERN_ST_COMPAT_VERSION

        @JavascriptInterface
        fun getActiveConversationId(): String = activeConversationId ?: ""

        /** JSR `getChatMessages` 的底层数据：消息数组 [ {name, is_user, is_system, mes, variables, swipe_id} ]。 */
        @JavascriptInterface
        fun getChatJson(): String = chatJsonSupplier?.invoke() ?: "[]"

        /** 当前助手卡内 tavern_helper（scripts / variables），JSR 卡脚本语义。 */
        @JavascriptInterface
        fun getTavernScriptsJson(): String = tavernScriptsSupplier?.invoke() ?: "{}"

        /** 卡内嵌世界书读取（JSR `loadWorldInfo` 语义）。 */
        @JavascriptInterface
        fun getLorebookJson(name: String): String = tavernLorebookSupplier?.invoke(name) ?: "{}"

        /** 当前角色卡最小信息（JSR `characters[i].data.extensions.world` 语义）。 */
        @JavascriptInterface
        fun getCharacterJson(): String = tavernCharacterSupplier?.invoke() ?: "{}"

        /** [v208] 可用世界书名列表（ST world_names / selected_world_info / world_info）。 */
        @JavascriptInterface
        fun getWorldNamesJson(): String = worldNamesSupplier?.invoke() ?: "{}"

        /** ST `chat_metadata`（含 variables）。 */
        @JavascriptInterface
        fun getChatMetadataJson(): String {
            val id = activeConversationId ?: return "{}"
            return buildJsonObject {
                put("variables", TavernVariableStore.getChat(id))
            }.toString()
        }

        /** ST `extension_settings`（含 variables.global）。 */
        @JavascriptInterface
        fun getSettingsJson(): String {
            return buildJsonObject {
                put(
                    "variables",
                    buildJsonObject {
                        put("global", TavernVariableStore.getGlobal())
                    },
                )
            }.toString()
        }

        /** 消息级变量（JSR `chat[i].variables[swipe]`）：数组套数组，索引对齐消息数组。 */
        @JavascriptInterface
        fun getMessageVariablesJson(): String {
            val id = activeConversationId ?: return "[]"
            return TavernVariableStore.getMessageVars(id).toString()
        }

        @JavascriptInterface
        fun replaceMessageVariables(data: String): Boolean {
            val id = activeConversationId ?: return false
            return runCatching {
                TavernVariableStore.replaceMessageVars(id, json.parseToJsonElement(data))
                true
            }.getOrDefault(false)
        }

        /** JSR `replaceVariables(..., {type:'chat'})` 的持久化落点。 */
        @JavascriptInterface
        fun replaceChatVariables(data: String): Boolean {
            val id = activeConversationId ?: return false
            return runCatching {
                val obj = json.decodeFromString(JsonObject.serializer(), data)
                TavernVariableStore.replaceChat(id, obj)
                true
            }.getOrDefault(false)
        }

        /** JSR `replaceVariables(..., {type:'global'})` 的持久化落点。 */
        @JavascriptInterface
        fun replaceGlobalVariables(data: String): Boolean {
            return runCatching {
                val obj = json.decodeFromString(JsonObject.serializer(), data)
                TavernVariableStore.replaceGlobal(obj)
                true
            }.getOrDefault(false)
        }

        /**
         * JSR `triggerSlash` 语义：在宿主执行一行 STscript，返回管道值。
         * 同步桥（JS 侧自行包装为 Promise）；宿主未接线时返回提示文本。
         */
        @JavascriptInterface
        fun triggerSlash(script: String): String {
            // [v244 BUGFIX D2] 旧实现用 `slashRunner?.invoke(script) ?: "(slash runner not ready)"`，
            // slashRunner 为空时**静默**返回一个字符串。而 JS 侧 st-compat/slash-commands.js 的
            // toExecutionResult 把 "(slash runner not ready)" 判成 isError=true →
            // 第三方扩展（JSR）的 triggerSlash 会 `throw Error(...)` → 卡片 `await triggerSlash('/trigger')`
            // 整条开局链路断掉，日志里却一条都不打。这里必须留痕，否则真机无法定位。
            val runner = slashRunner
            if (runner == null) {
                appendLog(
                    "error",
                    // [v245] 措辞订正：ChatPage 的 onDispose 已经**不再**置空 slashRunner（见 ChatPage.kt 的 E1），
                    // 所以这里的可能原因只剩「本次进程里还没有任何 ChatPage 绑定过」。
                    "[slash] triggerSlash: slashRunner 未接线（本进程内还没有 ChatPage 绑定过 / 从未接线）script='" +
                        script.take(80) + "'",
                )
                return "(slash runner not ready)"
            }
            val prevInBridge = inJavaBridgeCall.get() == true
            inJavaBridgeCall.set(true)
            activeJavaBridgeBlockingCalls.incrementAndGet()
            return try {
                runner.invoke(script)
            } catch (e: Exception) {
                appendLog("error", "[slash] triggerSlash failed: ${e.message}")
                "(slash error: ${e.message})"
            } finally {
                activeJavaBridgeBlockingCalls.decrementAndGet()
                inJavaBridgeCall.set(prevInBridge)
            }
        }

        /**
         * 静默生成专用桥：直接传递 prompt、raw（是否不含聊天历史）与可选 systemPrompt，
         * 避免通过 `triggerSlash('/gen ' + prompt)` 拼接导致 prompt 含 `|` 时被误切分为多段命令。
         */
        @JavascriptInterface
        fun generateQuiet(prompt: String, raw: Boolean, systemPrompt: String): String {
            if (prompt.isBlank()) return ""
            val qg = quietGenerator
            if (qg != null) {
                val prevInBridge = inJavaBridgeCall.get() == true
                inJavaBridgeCall.set(true)
                activeJavaBridgeBlockingCalls.incrementAndGet()
                return try {
                    runCatching { qg.invoke(prompt, raw, systemPrompt) }.getOrDefault("")
                } finally {
                    activeJavaBridgeBlockingCalls.decrementAndGet()
                    inJavaBridgeCall.set(prevInBridge)
                }
            }
            val escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"")
            val cmd = if (raw) {
                val sysPart = if (systemPrompt.isNotBlank()) {
                    val escapedSys = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"")
                    "system=\"$escapedSys\" "
                } else ""
                "/genraw ${sysPart}\"$escapedPrompt\""
            } else {
                "/gen \"$escapedPrompt\""
            }
            return triggerSlash(cmd)
        }

        /** 读取 `character` / `preset` / `script` 等独立作用域变量 JSON。 */
        @JavascriptInterface
        fun getScopedVariablesJson(scopeKey: String): String {
            if (scopeKey.isBlank()) return "{}"
            return TavernVariableStore.getScoped(scopeKey).toString()
        }

        /** 写入 `character` / `preset` / `script` 等独立作用域变量 JSON。 */
        @JavascriptInterface
        fun replaceScopedVariables(scopeKey: String, data: String): Boolean {
            if (scopeKey.isBlank()) return false
            return runCatching {
                val obj = json.decodeFromString(JsonObject.serializer(), data)
                TavernVariableStore.replaceScoped(scopeKey, obj)
                true
            }.getOrDefault(false)
        }

        /** JSR `createOrReplaceWorldbook(name, entries)` 宿主桥。 */
        @JavascriptInterface
        fun createOrReplaceLorebook(bookName: String, entriesJson: String): Boolean =
            dispatchLorebookCreate(bookName, entriesJson, replaceIfExists = true)

        /** JSR `createWorldbook(name, entries)` / `createLorebook(name, entries)` 宿主桥。 */
        @JavascriptInterface
        fun createLorebook(bookName: String, entriesJson: String): Boolean =
            dispatchLorebookCreate(bookName, entriesJson, replaceIfExists = false)

        /** JSR `deleteWorldbook(name)` / `deleteLorebook(name)` 宿主桥。 */
        @JavascriptInterface
        fun deleteLorebook(bookName: String): Boolean =
            dispatchLorebookDelete(bookName)

        /** JSR `setChatLorebook` / `rebindCharWorldbooks` / `rebindGlobalWorldbooks` 宿主桥。 */
        @JavascriptInterface
        fun rebindLorebooks(scope: String, namesJson: String): Boolean =
            dispatchLorebookRebind(scope, namesJson)

        /** JS `SlashCommandParser` 自定义命令执行结果回传（JS → Kotlin）。 */
        @JavascriptInterface
        fun onSlashResult(seq: Long, jsonRes: String) {
            try {
                pendingSlash.remove(seq)?.complete(jsonRes)
            } catch (e: Exception) {
                appendLog("error", "[slash-js] onSlashResult failed: ${e.message}")
            }
        }

        /** JS `registerFunctionTool` 函数工具执行结果回传（JS → Kotlin）。 */
        @JavascriptInterface
        fun onFunctionToolResult(seq: Long, jsonRes: String) {
            try {
                pendingFunctionTools.remove(seq)?.complete(jsonRes)
            } catch (e: Exception) {
                appendLog("error", "[tools-js] onFunctionToolResult failed: ${e.message}")
            }
        }

        /**
         * [v241] JSR `injectPrompts(prompts, { once })`：登记注入提示词（会话作用域）。
         *
         * 载荷 = JSR `InjectionPrompt[]` 的 JSON；字段：id / position('in_chat'|'none') / depth /
         * role / content / should_scan / once。解析失败返回 false（不把异常抛进 JS）。
         */
        @JavascriptInterface
        fun injectPrompts(jsonText: String): Boolean {
            val conversationId = activeConversationId ?: return false
            return runCatching {
                val arr = json.parseToJsonElement(jsonText) as? JsonArray ?: return false
                val prompts = arr.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (id.isEmpty()) return@mapNotNull null
                    InjectedPromptStore.InjectedPrompt(
                        id = id,
                        position = obj["position"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()
                            ?.takeIf { it == "none" } ?: "in_chat",
                        depth = obj["depth"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()
                            ?.coerceAtLeast(0) ?: 0,
                        role = obj["role"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "system",
                        content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        shouldScan = obj["shouldScan"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?.equals("true", ignoreCase = true) == true,
                        once = obj["once"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?.equals("true", ignoreCase = true) == true,
                        hasFilter = obj["hasFilter"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?.equals("true", ignoreCase = true) == true,
                        filterPassed = obj["filterPassed"]?.jsonPrimitive?.contentOrNull?.trim()
                            ?.let { !it.equals("false", ignoreCase = true) } ?: true,
                    )
                }
                InjectedPromptStore.inject(conversationId, prompts)
                appendLog("info", "[jsr] injectPrompts n=${prompts.size}")
                true
            }.getOrDefault(false)
        }

        /**
         * JSR `injectPrompts` 动态 `filter` 回调求值结果同步：`{"id1": true, "id2": false}`。
         */
        @JavascriptInterface
        fun updateInjectFilterResults(resultsJson: String): Boolean {
            val conversationId = activeConversationId ?: return false
            return runCatching {
                val obj = json.parseToJsonElement(resultsJson) as? JsonObject ?: return false
                val map = mutableMapOf<String, Boolean>()
                obj.forEach { (k, v) ->
                    val b = (v as? JsonPrimitive)?.contentOrNull?.trim()?.equals("true", ignoreCase = true) == true
                    map[k] = b
                }
                InjectedPromptStore.updateFilterResults(conversationId, map)
                true
            }.getOrDefault(false)
        }

        /** [v241] JSR `uninjectPrompts(ids)`：按 id 移除注入（载荷 = id 字符串数组 JSON）。 */
        @JavascriptInterface
        fun uninjectPrompts(idsJson: String): Boolean {
            val conversationId = activeConversationId ?: return false
            return runCatching {
                val arr = json.parseToJsonElement(idsJson) as? JsonArray ?: return false
                val ids = arr.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { id -> id.isNotEmpty() }
                }
                InjectedPromptStore.uninject(conversationId, ids)
                true
            }.getOrDefault(false)
        }

        @JavascriptInterface
        fun saveChat() {
            // 变量写入已自带去抖持久化；此处为 JSR 语义兼容。
        }

        /**
         * JSR `setChatMessages` / MVU `saveChat` 写路径：
         * JS 侧聊天记录内容变更（swipes / swipe_id / mes / is_system / name）回写宿主。
         */
        @JavascriptInterface
        fun updateChatMessages(jsonText: String) {
            try {
                chatUpdater?.invoke(jsonText)
            } catch (e: Exception) {
                appendLog("error", "[chat] updateChatMessages failed: ${e.message}")
            }
        }

        /** [v214] JSR/面板世界书写回：entriesJson = [{uid,disable},...] 整本书补丁。 */
        @JavascriptInterface
        fun updateLorebookEntries(bookName: String, entriesJson: String): Boolean =
            dispatchLorebookUpdate(bookName, entriesJson)

        /** [v214] 面板表单发送：把文本作为普通用户消息发送。 */
        @JavascriptInterface
        fun sendUserMessage(text: String): Boolean = dispatchUserMessage(text)

        /**
         * [v243 BUGFIX A2] JS `createChatMessages` 的宿主桥：把楼层**插入**当前会话（不触发生成）。
         *
         * 真机取证（tavern-runtime.log 2026-10-13 10:00:59）：
         *   [error] [百合花期] opening submit failed ReferenceError: createChatMessages is not defined
         * 卡脚本 `createChatMessages([{role:'user',name,message}])` 是 JSR 裸全局 API；
         * rikkaST 从来没提供过它 —— 这一步抛异常后整条开局链路（含随后的 /trigger）中断。
         */
        @JavascriptInterface
        fun insertChatMessages(jsonText: String): Boolean {
            val inserter = chatInserter ?: run {
                appendLog("warn", "[chat] insertChatMessages: inserter not wired")
                return false
            }
            return try {
                inserter(jsonText)
            } catch (e: Exception) {
                appendLog("error", "[chat] insertChatMessages failed: ${e.message}")
                false
            }
        }

        /** JSR `deleteChatMessages(messageIds)` 的宿主桥：按 0 基楼层下标数组同步删除。 */
        @JavascriptInterface
        fun deleteChatMessages(idsJson: String): Boolean {
            val deleter = chatDeleter ?: run {
                appendLog("warn", "[chat] deleteChatMessages: deleter not wired")
                return false
            }
            return try {
                deleter(idsJson)
            } catch (e: Exception) {
                appendLog("error", "[chat] deleteChatMessages failed: ${e.message}")
                false
            }
        }

        /**
         * [v243 BUGFIX A3] JS `createWorldbookEntries` 等 JSR 世界书写入 API 的宿主桥。
         *
         * 真机取证（tavern-runtime.log 2026-10-13 10:00:59）：
         *   toast "用户设定未能写入世界书：当前酒馆助手不支持世界书接口"
         * 该卡的能力探测要求 `createWorldbookEntries` 存在 —— 此前缺失 → 探测直接失败。
         */
        @JavascriptInterface
        fun upsertLorebookEntries(bookName: String, payloadJson: String): Boolean {
            val upserter = lorebookUpserter ?: run {
                appendLog("warn", "[lorebook] upsertLorebookEntries: upserter not wired (name='$bookName')")
                return false
            }
            val ok = try {
                upserter(bookName, payloadJson)
            } catch (e: Exception) {
                appendLog("error", "[lorebook] upsertLorebookEntries failed: ${e.message}")
                false
            }
            appendLog("info", "[lorebook] upsertLorebookEntries name='$bookName' ok=$ok")
            return ok
        }

        @JavascriptInterface
        fun saveMetadataDebounced() = saveChat()

        @JavascriptInterface
        fun saveSettingsDebounced() {
            TavernVariableStore.flushNow()
        }

                /** 第三方扩展装载列表（runtime 启动时注入 script/link 用；supplier 未挂载时直接扫描磁盘目录兜底）。 */
        @JavascriptInterface
        fun getThirdPartyExtensionsJson(): String {
            thirdPartyExtensionsSupplier?.invoke()?.takeIf { it.isNotBlank() && it != "[]" }?.let { return it }
            val ctx = appContext ?: return thirdPartyExtensionsSupplier?.invoke() ?: "[]"
            return runCatching {
                val list = me.rerere.rikkahub.data.st.extensions.listExtensions(ctx)
                me.rerere.rikkahub.data.st.extensions.buildRuntimeExtensionsJson(list, emptySet())
            }.getOrDefault("[]")
        }

        /** 专供扩展设置页（extension-settings.html?ext=...）使用：直接从磁盘扫描全部已安装扩展，不受启停过滤或页面切换竞态影响。 */
        @JavascriptInterface
        fun getAllThirdPartyExtensionsJson(): String {
            val ctx = appContext ?: return getThirdPartyExtensionsJson()
            return runCatching {
                val list = me.rerere.rikkahub.data.st.extensions.listExtensions(ctx)
                me.rerere.rikkahub.data.st.extensions.buildRuntimeExtensionsJson(list, emptySet())
            }.getOrDefault(getThirdPartyExtensionsJson())
        }

        /** 第三方扩展设置（extension_settings 非 variables 部分）。 */
        @JavascriptInterface
        fun getThirdPartySettingsJson(): String = thirdPartySettingsSupplier?.invoke() ?: "{}"

        /** 第三方扩展设置回写（JS 侧 saveSettingsDebounced 调用时携带全量 JSON）。 */
        @JavascriptInterface
        fun saveThirdPartySettings(jsonText: String) {
            try {
                thirdPartySettingsSaver?.invoke(jsonText)
            } catch (e: Exception) {
                appendLog("error", "[ext] saveThirdPartySettings failed: ${e.message}")
            }
        }

        @JavascriptInterface
        fun log(level: String, message: String) {
            appendLog(level, message)
        }

        /** JS 侧事件上抛（script_ready / variable_updated / mvu_ready…）。 */
        @JavascriptInterface
        fun emitEvent(name: String, payloadJson: String) {
            if (name == "macros_updated") {
                try {
                    val obj = org.json.JSONObject(payloadJson)
                    val out = LinkedHashMap<String, Pair<String, String>>()
                    for (key in obj.keys()) {
                        val e = obj.optJSONObject(key) ?: continue
                        out[key] = Pair(e.optString("type"), e.optString("value"))
                    }
                    macrosRegistry.set(out)
                } catch (e: Exception) {
                    appendLog("error", "[macros] snapshot parse failed: ${e.message}")
                }
            } else if (name == "js_slash_commands_updated") {
                try {
                    val arr = org.json.JSONArray(payloadJson)
                    val names = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val cmdName = arr.optString(i, "").trim().lowercase()
                        if (cmdName.isNotEmpty()) names.add(cmdName)
                    }
                    updateRegisteredJsSlashCommands(names)
                } catch (e: Exception) {
                    appendLog("error", "[slash] js_slash_commands_updated parse failed: ${e.message}")
                }
            } else if (name == "js_function_tools_updated") {
                try {
                    val arr = org.json.JSONArray(payloadJson)
                    val specs = mutableListOf<JsFunctionToolSpec>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val toolName = o.optString("name", "").trim()
                        if (toolName.isEmpty()) continue
                        specs += JsFunctionToolSpec(
                            name = toolName,
                            displayName = o.optString("displayName", toolName).ifBlank { toolName },
                            description = o.optString("description", ""),
                            parametersJson = o.optJSONObject("parameters")?.toString()
                                ?: o.optString("parameters", "{}").ifBlank { "{}" },
                            stealth = o.optBoolean("stealth", false),
                        )
                    }
                    updateRegisteredJsFunctionTools(specs)
                } catch (e: Exception) {
                    appendLog("error", "[tools] js_function_tools_updated parse failed: ${e.message}")
                }
            }
            appendLog("js-event", "$name $payloadJson")
            handleJsLifecycleEvent(name, payloadJson)
            if (name == "js_result") publishJsResult(payloadJson)
            onJsEvent?.invoke(name, payloadJson)
        }

        /** 轻量变量宏（getvar/getglobalvar/setvar）——第一版在 JS 侧做，Kotlin 提供数据。 */
        @JavascriptInterface
        fun getLocalVariableJson(): String {
            val id = activeConversationId ?: return "{}"
            return TavernVariableStore.getChat(id).toString()
        }

        @JavascriptInterface
        fun getGlobalVariableJson(): String {
            return TavernVariableStore.getGlobal().toString()
        }
        /** 脚本按钮上报（JSR 语义）：JS 侧按钮集/可见性变更时推送。 */
        @JavascriptInterface
        fun onScriptButtons(json: String) {
            try {
                val arr = org.json.JSONArray(json)
                val list = ArrayList<TavernScriptButton>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    if (name.isEmpty()) continue
                    list.add(
                        TavernScriptButton(
                            scriptId = obj.optString("scriptId", ""),
                            scriptName = obj.optString("scriptName", ""),
                            name = name,
                            event = obj.optString("event", ""),
                        )
                    )
                }
                scriptButtonsFlow.value = list
                appendLog("debug", "[buttons] onScriptButtons ok: n=${list.size} [${list.joinToString(",") { it.scriptId + ":" + it.name }}]")
            } catch (e: Exception) {
                appendLog("error", "[buttons] onScriptButtons parse failed: ${e.message}")
            }
        }

        /** EJS 批量渲染结果回传（JS → Kotlin；完成对应 seq 的挂起等待）。 */
        @JavascriptInterface
        fun onEjsResult(seq: Long, jsonRes: String) {
            try {
                pendingEjs.remove(seq)?.complete(jsonRes)
            } catch (e: Exception) {
                appendLog("error", "[ejs] onEjsResult failed: ${e.message}")
            }
        }

        /** [v234 S2] 函数宏批量执行结果回传（JS → Kotlin）。 */
        @JavascriptInterface
        fun onMacrosResult(seq: Long, jsonRes: String) {
            try {
                pendingMacros.remove(seq)?.complete(jsonRes)
            } catch (e: Exception) {
                appendLog("error", "[macros] onMacrosResult failed: ${e.message}")
            }
        }

        /** [v211] 生成管线钩子结果回传（JS → Kotlin；完成对应 seq 的挂起等待）。 */
        @JavascriptInterface
        fun onGenerationHooksResult(seq: Long, jsonRes: String) {
            try {
                pendingGenHooks.remove(seq)?.complete(jsonRes)
            } catch (e: Exception) {
                appendLog("error", "[gen-hooks] onGenerationHooksResult failed: ${e.message}")
            }
        }

        /** 动态注入提示词异步 filter 求值完成回调（JS → Kotlin）。 */
        @JavascriptInterface
        fun onInjectFiltersEvaluated(seq: Long) {
            try {
                pendingFilterEvals.remove(seq)?.complete(Unit)
            } catch (_: Exception) {
            }
        }

        // ==================== [v229 A1] JSR 生成兼容桥 ====================

        /** 同步：可用模型列表 + 当前模型（JSR `getModelList`）。 */
        @JavascriptInterface
        fun chatCompletionStatus(): String = TavernRuntimeManager.chatCompletionStatus()

        /** 起一条流（JS 侧立即返回；结果经 `__rikkaApiChunk` / `__rikkaApiFinish` 回推）。 */
        @JavascriptInterface
        fun chatCompletionStart(reqId: String?, bodyJson: String) {
            TavernRuntimeManager.startChatCompletionStream(reqId, bodyJson)
        }

        /** SSE 流结束（JS 侧 `finally` 也调一次，保证 GC）。 */
        @JavascriptInterface
        fun chatCompletionCancel(reqId: String?) {
            TavernRuntimeManager.appendLog("debug", "[a1] client cancelled reqId=$reqId")
        }

        /** 通道自检（真机取证用）。 */
        @JavascriptInterface
        fun chatCompletionProbe(): String = TavernRuntimeManager.chatCompletionError("probe")

        // ==================== [ST 1.19.0] MessageFormatter & 用户文件/图片 & 共享 Node.js 桥 ====================

        @JavascriptInterface
        fun onMessageFormatterHooksChanged(active: Boolean) {
            updateMessageFormatterHooksState(active)
        }

        @JavascriptInterface
        fun onMessageFormatterStageResult(seq: Long, resultText: String) {
            try {
                pendingFormatterStages.remove(seq)?.complete(resultText)
            } catch (_: Exception) {
            }
        }

        @JavascriptInterface
        fun writeUserFileBase64(name: String, base64Data: String): String {
            val ctx = appContext ?: return ""
            val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: return ""
            return runCatching {
                val dir = File(ctx.filesDir, "tavern-user/files").apply { mkdirs() }
                val file = File(dir, safeName)
                val cleanB64 = base64Data.substringAfter("base64,", base64Data)
                val bytes = java.util.Base64.getMimeDecoder().decode(cleanB64)
                file.writeBytes(bytes)
                "/user/files/$safeName"
            }.getOrDefault("")
        }

        @JavascriptInterface
        fun readUserFileJson(name: String): String {
            val ctx = appContext ?: return JSONObject().put("exists", false).toString()
            val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").takeIf { it.isNotEmpty() && it != "." && it != ".." }
                ?: return JSONObject().put("exists", false).toString()
            val file = File(ctx.filesDir, "tavern-user/files/$safeName")
            if (!file.isFile) return JSONObject().put("exists", false).toString()
            return runCatching {
                val b64 = java.util.Base64.getEncoder().encodeToString(file.readBytes())
                JSONObject().apply {
                    put("exists", true)
                    put("name", safeName)
                    put("mime", mimeOf(safeName))
                    put("base64", b64)
                }.toString()
            }.getOrElse { JSONObject().put("exists", false).toString() }
        }

        @JavascriptInterface
        fun deleteUserFile(name: String): Boolean {
            val ctx = appContext ?: return false
            val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: return false
            val file = File(ctx.filesDir, "tavern-user/files/$safeName")
            return runCatching { file.delete() }.getOrDefault(false)
        }

        @JavascriptInterface
        fun writeUserImageBase64(folder: String, filename: String, base64Data: String): String {
            val ctx = appContext ?: return ""
            val safeFolder = folder.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").ifBlank { "default" }
            val safeFile = filename.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: return ""
            return runCatching {
                val dir = File(ctx.filesDir, "tavern-user/images/$safeFolder").apply { mkdirs() }
                val file = File(dir, safeFile)
                val cleanB64 = base64Data.substringAfter("base64,", base64Data)
                val bytes = java.util.Base64.getMimeDecoder().decode(cleanB64)
                file.writeBytes(bytes)
                "/user/images/$safeFolder/$safeFile"
            }.getOrDefault("")
        }

        @JavascriptInterface
        fun listUserImagesJson(folder: String): String {
            val ctx = appContext ?: return "[]"
            val safeFolder = folder.trim().replace(Regex("[^a-zA-Z0-9._\u4e00-\u9fa5-]"), "_").ifBlank { "default" }
            val dir = File(ctx.filesDir, "tavern-user/images/$safeFolder")
            if (!dir.isDirectory) return "[]"
            val arr = org.json.JSONArray()
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { f ->
                arr.put("/user/images/$safeFolder/${f.name}")
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun deleteUserImage(relPath: String): Boolean {
            val ctx = appContext ?: return false
            val allowedRoot = runCatching { File(ctx.filesDir, "tavern-user/images").canonicalFile }.getOrNull() ?: return false
            val target = runCatching { File(allowedRoot, relPath.removePrefix("/")).canonicalFile }.getOrNull() ?: return false
            if (!target.path.startsWith(allowedRoot.path + File.separator)) return false
            return runCatching { target.delete() }.getOrDefault(false)
        }

        @JavascriptInterface
        fun nodePluginHttpRequestAsync(
            reqId: String,
            method: String,
            pathWithQuery: String,
            headersJson: String,
            body: String,
        ) {
            TavernNodeServiceManager.dispatchPluginHttpRequestAsync(
                context = appContext,
                reqId = reqId,
                method = method,
                pathWithQuery = pathWithQuery,
                headersJson = headersJson,
                body = body,
                assistantId = activeAssistantId,
                conversationId = activeConversationId,
            ) { respJson ->
                eval(
                    "window.__rikkaNodeHttpCallback && window.__rikkaNodeHttpCallback(" +
                        JSONObject.quote(reqId) + "," +
                        JSONObject.quote(respJson) + ");",
                )
            }
        }

        @JavascriptInterface
        fun nodePluginHttpAbort(reqId: String) {
            TavernNodeServiceManager.abortPluginHttpRequest(reqId)
        }
    }

    // ==================== [v229 A1] /api/backends/chat-completions/{status,generate} ====================

    /**
     * `/status`：JS 侧同步调用（JSR `getModelList` 只探可用模型，见 index.ts:46-64）。
     * 未接线时返回空 `data` + 明确 error 字段（JSR 会拿到 []，不会 404）。
     */
    fun chatCompletionStatus(): String {
        val hook = textGenerationHook
        if (hook == null) {
            appendLog("warn", "[a1] /status 未接线（textGenerationHook == null）")
            return TavernChatCompletionsApi.statusResponse(emptyList(), null)
        }
        val ids = generationModelIdsSupplier?.invoke().orEmpty()
        val current = currentModelIdSupplier?.invoke()
        appendLog("debug", "[a1] /status models=${ids.size} current=${current ?: "-"}")
        return TavernChatCompletionsApi.statusResponse(ids, current)
    }

    /**
     * `/generate`：JS 侧 fire-and-forget 起流，宿主逐块 `eval` 回推。
     * **SSE 与 stream:false 两种都要能用**（JSR 流式走 responseGenerator.ts:343，非流式走 :438）。
     */
    fun startChatCompletionStream(reqId: String?, bodyJson: String) {
        val id = reqId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val req = TavernChatCompletionsApi.parseGenerateRequest(bodyJson)
        if (req == null) {
            appendLog("warn", "[a1] /generate 请求体非法（缺 messages / 非 JSON）")
            emitChatSse(id, TavernChatCompletionsApi.errorBody("invalid request body: messages is required"))
            finishChatCompletion(id, ok = false)
            return
        }
        val hook = textGenerationHook
        if (hook == null) {
            appendLog("error", "[a1] /generate 未接线：宿主生成管线不可用")
            emitChatSse(id, TavernChatCompletionsApi.errorBody("host generation pipeline is not wired"))
            finishChatCompletion(id, ok = false)
            return
        }

        val model = req.modelId.ifBlank { currentModelIdSupplier?.invoke().orEmpty() }
        val streamId = "chatcmpl-" + id
        val isStreaming = req.stream
        appendLog(
            "debug",
            "[a1] /generate start id=$id stream=$isStreaming model=${model.ifBlank { "-" }} msgs=${req.messages.size}",
        )

        runtimeScope.launch {
            val buffer = StringBuilder()
            var failed = false
            try {
                hook(bodyJson)
                    .catch { error ->
                        failed = true
                        appendLog("error", "[a1] /generate stream failed: ${error.message}")
                        emitChatSse(id, TavernChatCompletionsApi.errorBody(error.message ?: "generation failed", "api_error"))
                        emptyFlow<StreamChunk>()
                    }
                    .collect { chunk ->
                        when (chunk) {
                            is StreamChunk.TextDelta -> {
                                buffer.append(chunk.text)
                                if (isStreaming) {
                                    emitChatSse(
                                        id,
                                        TavernChatCompletionsApi.streamChunk(streamId, model, chunk.text),
                                    )
                                }
                            }
                            is StreamChunk.ReasoningDelta -> {
                                if (isStreaming) {
                                    emitChatSse(
                                        id,
                                        TavernChatCompletionsApi.streamChunk(
                                            streamId,
                                            model,
                                            null,
                                            finishReason = null,
                                        ),
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
            } catch (t: Throwable) {
                failed = true
                appendLog("error", "[a1] /generate crashed: ${t.message}")
                emitChatSse(id, TavernChatCompletionsApi.errorBody(t.message ?: "generation crashed", "api_error"))
            }
            if (!isStreaming && !failed) {
                emitChatSse(id, TavernChatCompletionsApi.fullCompletion(streamId, model, buffer.toString()))
            }
            finishChatCompletion(id, ok = !failed)
            appendLog("debug", "[a1] /generate done id=$id chars=${buffer.length} ok=${!failed}")
        }
    }

    /** 逐块回推（`data:` 载荷），走 `__rikkaApiChunk`。 */
    private fun emitChatSse(reqId: String, payload: String) {
        eval(
            "window.__rikkaApiChunk && window.__rikkaApiChunk(" +
                JSONObject.quote(reqId) + "," +
                JSONObject.quote(payload) + ");",
        )
    }

    /** 收尾。SSE 只发 `[DONE]`；非 SSE 只关闭流（整包已在上一步发出）。 */
    private fun finishChatCompletion(reqId: String, ok: Boolean) {
        eval(
            "window.__rikkaApiFinish && window.__rikkaApiFinish(" +
                JSONObject.quote(reqId) + "," +
                JSONObject.quote(if (ok) "DONE" else "ERR") + ");",
        )
    }

    /** JSON 错误体响应（给 JSR 的任务体路径用；HTTP 层由 runtime.js 保证 2xx + body）。 */
    fun chatCompletionError(message: String): String =
        TavernChatCompletionsApi.errorBody(message)

}