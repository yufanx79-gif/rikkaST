package me.rerere.rikkahub.ui.components.message

import java.util.Locale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import org.json.JSONObject

/**
 * 检测消息文本是否需要「内嵌 HTML/JS 渲染」（v182 C4，v183 放宽）。
 *
 * v183 起兼容「JSR 消息 iframe 化」语义：卡正则替换串常以 ``` / ```html 围栏包裹整段
 * HTML（如魔法少女卡的开场白/游玩指南），其中可能没有 `<script>`（纯指南/纯表单），
 * 不能再用「必须含 <script>」作唯一判据。触发条件（任一）：
 * 1) 含 <script> 或内联事件属性（onclick 等）；
 * 2) 完整 HTML 文档（<!DOCTYPE html> / <html ...>，含围栏包裹）；
 * 3) <style> + 块级容器（面板/指南类替换串）；
 * 4) 交互控件（button/input/select/textarea/form）+ <div> 结构。
 * 普通 Markdown 消息不触发；被转义的 &lt;script&gt; 不触发。
 */
fun looksLikeInteractiveHtml(text: String): Boolean {
    if (text.length < 32) return false
    if (Regex("<script[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
    if (Regex("\\son[a-z]{3,}\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
    if (Regex("<(!doctype\\s+html|html[\\s>])", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
    if (
        Regex("<style[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
        Regex("<(div|span|section|details|main|article)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text)
    ) {
        return true
    }
    val hasWidget = Regex("<(button|input|select|textarea|form)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text)
    val hasStruct = Regex("<div[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
        Regex("</div>", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return hasWidget && hasStruct
}

/**
 * 消息内嵌 HTML/JS 面板渲染（千纱状态栏 / 文爱社区 / Vue 面板类）。
 *
 * 复用 st-runtime 资产环境：WebView 加载 `panel.html`（以 `__RIKKA_PANEL_MODE__` 启动 runtime.js），
 * runtime 只装载 shims（TavernHelper / 变量桥 / 事件），不加载卡脚本与 MVU；
 * 消息文本经 `window.__rikkaPanelMount(html)` 注入：innerHTML + 手动执行 `<script>`（外链串行）。
 *
 * - 高度自适应：面板内 ResizeObserver → RikkaPanel.panelResize(h) → Compose 高度状态
 * - 面板内错误 → RikkaPanel.onPanelError → tavern-runtime.log
 * - 仅用于消息渲染；流式生成期间由调用方回退 Markdown（避免高频重挂载）
 *
 * v19x 渲染滚动修复：
 * 1) 取消旧版 1.6 屏高度上限，改为「整高渲染」——对齐 JSR `adjust_iframe_height`
 *    （消息 iframe 高度 = body.scrollHeight，全量高度），面板内容完整撑进气泡、
 *    由外层聊天列表滚动查看，修复「界面很长但只有一部分且滑不动」；
 * 2) 增加 [PanelScrollBridge] 触摸桥：面板整高渲染时不需要内部滚动，当 WebView
 *    无法垂直滚动时吞掉其 requestDisallowInterceptTouchEvent 请求，让聊天列表
 *    正常接管垂直拖动（对齐 ST/JSR 里「在面板上滑动 = 滚动聊天」的体验）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MessageHtmlBlock(
    html: String,
    messageKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // ⚠️ WebView 默认（useWideViewPort=false）下 1 CSS px == 1 dp（device-independent pixel），
    // runtime.js 上报的高度就是 CSS px，可直接当作 dp 使用。
    // v182 误按物理像素做 px→toDp() 二次换算，显示高度被 density 缩小（~2.75x），
    // 表现为「内容被压缩成一小行」——严禁回退为二次换算。
    val minHeightDp = 48
    // 高度保险丝：25 屏（仅防御异常脚本导致的无限增长；正常面板永远到不了）。
    val safetyMaxHeightDp = (configuration.screenHeightDp * 25).coerceAtLeast(1200)
    var heightDp by remember { mutableIntStateOf(0) }
    val boundedHeightDp = heightDp.coerceIn(minHeightDp, safetyMaxHeightDp)
    val mountState = remember { PanelMountState() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    // 批次十一 T2（bug D）：面板默认前景色必须跟随 App 主题。
    // panel.html 里写死的 body color:#e6e6e6 是为暗色场景准备的，浅色主题下卡片 CSS 覆盖不到的
    // 文字（叙事正文、*手机振动* 之类）会变成白底白字。这里把 Compose 侧的 onSurface 注入成
    // CSS 变量 --rikka-fg，由 panel.html 的 body color 引用，与正文同色。
    // 组合期先把 onSurface 提到局部变量：remember 的 lambda 不是 @Composable，
    // 在其中读 MaterialTheme 会编译报错（@Composable invocations can only happen ...）。
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val foregroundHex = remember(onSurfaceColor) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(boundedHeightDp.dp),
        factory = { context ->
            // 双保险：确保宿主运行时已初始化（面板的数据桥依赖其 appContext 资产拦截器）
            TavernRuntimeManager.ensureStarted(context)
            val webView = WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.blockNetworkImage = false
                overScrollMode = View.OVER_SCROLL_NEVER
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        return TavernRuntimeManager.interceptAsset(request.url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        mountState.onPageReady()
                    }
                }
                addJavascriptInterface(
                    PanelBridge(
                        handler = handler,
                        onHeight = { h -> heightDp = h },
                    ),
                    "RikkaPanel",
                )
                addJavascriptInterface(TavernRuntimeManager.TavernJsBridge(), "RikkaBridge")
                mountState.webView = this
                TavernRuntimeManager.registerAuxWebView(this, "panel") // [batch17] 按钮派发要能找到这一页
                loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
            }
            // 触摸桥：面板整高渲染时无需内部滚动。
            // WebView 在触摸时惯常向上层调用 requestDisallowInterceptTouchEvent(true)
            //（这会永久锁定外层聊天列表的拖动）；当且仅当它真的无法垂直滚动时
            // 吞掉该请求，让聊天列表接管垂直滑动；点击/水平交互不受影响，
            // （极端情况下）真实可滚动的内部滚动也保持原行为。
            PanelScrollBridge(context).apply {
                verticalScrollProbe = {
                    webView.canScrollVertically(-1) || webView.canScrollVertically(1)
                }
                addView(
                    webView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { mountState.submit(html, foregroundHex, messageKey) },
        onRelease = { mountState.release() },
    )

}

/** 消息文本渲染：交互式 HTML → 内嵌 WebView；否则 → Markdown（保持原有行为）。 */
@Composable
internal fun StTextContent(
    content: String,
    loading: Boolean,
    onClickCitation: (String) -> Unit,
    messageKey: String? = null,
    modifier: Modifier = Modifier,
) {
    if (!loading && looksLikeInteractiveHtml(content)) {
        MessageHtmlBlock(
            html = content,
            messageKey = messageKey,
            modifier = modifier,
        )
    } else {
        MarkdownBlock(content = content, modifier = modifier, onClickCitation = onClickCitation)
    }
}

/**
 * 面板触摸桥（包在 WebView 外层）：
 * 面板为整高渲染，没有内部滚动需求；当 WebView 不具备垂直滚动能力时，
 * 吞掉它向父级发出的 requestDisallowInterceptTouchEvent(true)，
 * 让外层的聊天 LazyColumn 可以正常接管垂直拖动。
 */
private class PanelScrollBridge(context: Context) : FrameLayout(context) {
    /** 返回 WebView 当前是否具备垂直滚动能力；为 false 时吞掉 disallow-intercept 请求。 */
    var verticalScrollProbe: (() -> Boolean)? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && verticalScrollProbe?.invoke() == false) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}

/** 面板挂载状态（WebView 页面就绪后注入 HTML；HTML 变化时重挂载）。 */
private class PanelMountState {
    var webView: WebView? = null
    private var pageReady = false

    // T3（批次十二）：是否已经成功挂载过至少一次 HTML。
    // 用来区分「首挂」与「换内容」——后者必须整页重载，否则会残留上一张卡的运行时状态。
    private var everMounted = false
    private var mountedHtml: String? = null
    private var pendingHtml: String? = null
    private var pendingForeground: String? = null
    private var appliedForeground: String? = null
    private var pendingKey: String? = null
    private var appliedKey: String? = null

    fun onPageReady() {
        pageReady = true
        tryMount()
    }

    /** [foregroundHex] = App 主题正文色（#RRGGBB），经 CSS 变量 --rikka-fg 注入面板。 */
    fun submit(html: String, foregroundHex: String?, messageKey: String? = null) {
        pendingHtml = html
        pendingForeground = foregroundHex
        pendingKey = messageKey
        tryMount()
    }

    fun release() {
        webView?.let { wv ->
            webView = null
            TavernRuntimeManager.unregisterAuxWebView(wv)
            try {
                wv.loadUrl("about:blank")
                wv.destroy()
            } catch (_: Throwable) {
                // 视图释放阶段的 destroy 失败可安全忽略
            }
        }
    }

    private fun tryMount() {
        val wv = webView ?: return
        val html = pendingHtml ?: return
        if (!pageReady) return
        // T2 (batch11 / bug D)：先把主题正文色写进 CSS 变量再挂 HTML，保证卡片脚本执行时
        // 读到的 --rikka-fg 已经是当前主题色；注入失败/为空时不影响原有挂载逻辑。
        val fg = pendingForeground
        if (fg != null && fg != appliedForeground) {
            appliedForeground = fg
            wv.evaluateJavascript(
                "document.documentElement.style.setProperty('--rikka-fg','$fg');",
                null,
            )
        }
        // 批次十三：面板页面要知道「我是哪条消息」。把 id 注入页面（挂 HTML 之前），
        // runtime.js 的 getCurrentMessageId() 靠它把 chat[i] 找出来 → 本条消息的 MVU 变量。
        val key = pendingKey
        if (key != appliedKey) {
            appliedKey = key
            wv.evaluateJavascript(
                "window.__rikkaPanelMessageKey=" + JSONObject.quote(key ?: "") + ";",
                null,
            )
        }
        
        if (html == mountedHtml) return
        // T3（批次十二 / 渲染残留）：换 HTML 就是换一张卡。
        // 旧做法直接在旧页面上 root.innerHTML = …（runtime.js 的 __rikkaPanelMount），
        // 上一张卡注册在 window/document 上的监听器与 setInterval 不会被清掉 ——
        // 它会继续往新卡的 DOM 上写（状态栏互串、高度反复上报），这就是「渲染残留」。
        // 现在改成整页重载：新卡在全新页面上挂载，旧卡的一切运行时状态随旧页面一起消失。
        // 不重建 WebView（不闪、更便宜）；重载期间 pageReady=false，
        // 这期间的 submit 只更新 pendingHtml，等 onPageFinished 后挂最新的一份（天然去抖）。
        if (everMounted) {
            mountedHtml = null
            appliedForeground = null
            appliedKey = null
            pageReady = false
            try {
                wv.loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
            } catch (_: Throwable) {
                // 视图已在销毁流程里，忽略
            }
            return
        }
        everMounted = true
        mountedHtml = html
        val quoted = JSONObject.quote(html)
        wv.evaluateJavascript(
            "window.__rikkaPanelMount ? window.__rikkaPanelMount($quoted) : (window.__rikkaPanelPendingHtml = $quoted);",
            null,
        )
    }
}

/** 面板 → Kotlin 桥：**高度**上报与日志（v229 P0-1 起宽度上报已彻底移除）。 */
private class PanelBridge(
    private val handler: Handler,
    private val onHeight: (Int) -> Unit,
) {
    @JavascriptInterface
    fun panelResize(height: Int) {
        handler.post {
            TavernRuntimeManager.appendLog("debug", "[panel] resize h=$height")
            onHeight(height.coerceIn(0, 2_000_000))
        }
    }

    @JavascriptInterface
    fun log(level: String, message: String) {
        TavernRuntimeManager.appendLog(level, "[panel] $message")
    }

    @JavascriptInterface
    fun onPanelError(message: String) {
        TavernRuntimeManager.appendLog("error", "[panel] $message")
    }
}
