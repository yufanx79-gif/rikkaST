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
    )

    private val mainHandler = Handler(Looper.getMainLooper())
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

    /** [v234 S2] 函数宏批量执行等待槽（seq → deferred）。 */
    val pendingMacros = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val macrosSeqCounter = AtomicLong(0)

    /** [v211] 生成管线钩子（CHAT_COMPLETION_SETTINGS_READY）的挂起回调表。 */
    val pendingGenHooks = ConcurrentHashMap<Long, CompletableDeferred<String>>()
    private val genHookSeqCounter = AtomicLong(0)
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

    fun registerAuxWebView(wv: WebView, tag: String) {
        auxWebViews[wv] = tag
        appendLog("debug", "[webview] aux registered: $tag (total=${auxWebViews.size})")
    }

    fun unregisterAuxWebView(wv: WebView) {
        auxWebViews.remove(wv)
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

    /** MVU bundle 是否已就绪（由 JS 侧 mvu_ready / mvu_error 事件维护）。 */
    @Volatile var isMvuActive: Boolean = false
        private set

    /** 处理 JS 侧生命周期事件（由桥回调触发）。 */
    fun handleJsLifecycleEvent(name: String, payloadJson: String = "{}") {
        when (name) {
            "mvu_ready" -> isMvuActive = true
            "mvu_error" -> isMvuActive = false
            // [v218.1] 面板渲染完成 → 主运行时补发 ST 的 RENDERED 事件。
            // 依据 notes/recon-ext-compat-20261001.md §2.3：扩展侧（记忆增强表格 index.js:1072、
            // ST-PT handler.ts:986/964、JSR macro_like.ts:127-128）全靠 USER/CHARACTER_MESSAGE_RENDERED
            // 刷新视图，此前宿主一条都不发。面板 WebView 与主运行时是两个 realm（eventSource 各一份），
            // 所以必须经桥转发到主运行时发射。
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
        appContext = context.applicationContext
    }

    fun ensureStarted(context: Context) {
        if (webView != null) return
        mainHandler.post {
            if (webView != null) return@post
            runCatching { createWebView(context.applicationContext) }
                .onFailure { appendLog("error", "WebView 初始化失败: ${it.message}") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context) {
        appContext = context
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.blockNetworkImage = false
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
            }
        }
        wv.addJavascriptInterface(TavernJsBridge(), "RikkaBridge")
        webView = wv
        wv.loadUrl("${BASE_URL}index.html")
    }

    /** 资产拦截：/st-runtime/... → assets/st-runtime/...（含 ES module MIME）；CDN 请求 → 本地 vendor。 */
    internal fun interceptAsset(uri: Uri): WebResourceResponse? {
        if (uri.host != HOST) return interceptCdn(uri)
        val path = uri.path ?: return notFound()
        // 0) 第三方扩展资源（真实文件端点）：
        //    /scripts/extensions/third-party/<folder>/<path> → filesDir/tavern-extensions/third-party/...
        if (path.startsWith("/scripts/extensions/third-party/")) return openThirdPartyAsset(path)
        //    扩展内相对路径拼接（extensionFolderPath = "scripts/extensions/..."）：
        //    /st-runtime/scripts/extensions/third-party/... → 剥前缀后同上
        if (path.startsWith("/st-runtime/scripts/extensions/third-party/")) {
            return openThirdPartyAsset(path.removePrefix("/st-runtime"))
        }
        // runtime.js 的 renderTemplateAsync 生成的是 /third-party/<folder>/<tpl>.html；
        // 为兼容这个 ST 常见相对路径，映射到真实的 /scripts/extensions/third-party/**。
        if (path.startsWith("/third-party/")) {
            return openThirdPartyAsset("/scripts/extensions$path")
        }
        // 0.5) [v228 S1] ST `/version` 端点（**能力闸门**，不是 rikkaST 自身版本）。
        //    JSR `src/util/tavern.ts:24-27`：await fetch('/version').then(r => r.json()).then(d => d.pkgVersion)
        //      .catch(() => '1.0.0')  —— 全库所有 compare(version, ...) 闸门的唯一数据源（dist/index.js:74）。
        //    以前这里落到 notFound()（404 text/plain）→ .json() 抛错 → 全部闸门按 1.0.0 走旧分支。
        //    取值判据见 notes/recon-version-gate-20261008.md §4。
        if (path == "/version") return versionResponse()
        // 1) ST 核心模块兼容层（第三方扩展 import 的 ../../../script.js / extensions.js / lib.js …）：
        //    /script.js、/lib.js、/scripts/<name>.js → st-runtime/st-compat/ 下的 shim 模块
        routeStCompat(path)?.let { return it }
        // 2) /npm/ 前缀 → st-runtime/vendor/npm/（MVU bundle 及其依赖的本地端点）
        val assetPath = when {
            path.startsWith("/npm/") -> "st-runtime/vendor$path"
            else -> path.trimStart('/')
        }
        return openAsset(assetPath) ?: notFound()
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
            seg.isNotEmpty() && seg.none { c ->
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
        val host = uri.host ?: return null
        if (host !in CDN_HOSTS) return null
        val path = uri.path ?: return null
        openAsset("st-runtime/vendor$path")?.let { return it }
        deversionNpm(path)?.let { de ->
            openAsset("st-runtime/vendor$de")?.let { return it }
        }
        return null
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

    private fun mimeOf(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith("+esm") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".json") || path.endsWith(".map") -> "application/json"
        path.endsWith(".wasm") -> "application/wasm"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".woff2") -> "font/woff2"
        else -> "application/octet-stream"
    }

    /**
     * [v228 S1] `/version` 上报的 ST 兼容版本号（**能力闸门口径，不是 rikkaST 自身版本**）。
     * 改动它 = 一次性切换所有下游扩展的能力分支，必须同步改 `assets/st-runtime/runtime.js` 的
     * `TAVERN_ST_COMPAT_VERSION`，并更新 `notes/recon-version-gate-20261008.md` §4 的判据。
     */
    const val TAVERN_ST_COMPAT_VERSION = "1.13.5"

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

    // ==================== [v214] JS 写回桥（世界书 / 面板表单发送） ====================

    /** [v214] JS `RikkaBridge.updateLorebookEntries`：整本书条目 disable 补丁 → 宿主持久化。 */
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
        android.util.Log.d(TAG, line)
        persistLog(line)
    }

    /** 追加写入持久化日志文件（滚动上限 [LOG_FILE_MAX_BYTES]，失败静默）。 */
    private fun persistLog(line: String) {
        val ctx = appContext ?: return
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val file = File(dir, LOG_FILE_NAME)
            synchronized(logFileLock) {
                if (file.length() > LOG_FILE_MAX_BYTES) {
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
    class TavernJsBridge {
        @JavascriptInterface
        fun getVersion(): String = "1.18.0"

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
            return try {
                slashRunner?.invoke(script) ?: "(slash runner not ready)"
            } catch (e: Exception) {
                appendLog("error", "[slash] triggerSlash failed: ${e.message}")
                "(slash error: ${e.message})"
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
                    )
                }
                InjectedPromptStore.inject(conversationId, prompts)
                appendLog("info", "[jsr] injectPrompts n=${prompts.size}")
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

        @JavascriptInterface
        fun saveMetadataDebounced() = saveChat()

        @JavascriptInterface
        fun saveSettingsDebounced() {
            TavernVariableStore.flushNow()
        }

        /** 第三方扩展装载列表（runtime 启动时注入 script/link 用）。 */
        @JavascriptInterface
        fun getThirdPartyExtensionsJson(): String = thirdPartyExtensionsSupplier?.invoke() ?: "[]"

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