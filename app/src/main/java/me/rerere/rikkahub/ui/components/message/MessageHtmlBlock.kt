package me.rerere.rikkahub.ui.components.message

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.produceState
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.datastore.WebViewPerfMode
import me.rerere.rikkahub.data.st.runtime.RikkaWebChromeClient
import me.rerere.rikkahub.data.st.runtime.TavernResourceRoute
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import org.json.JSONObject

private const val MAX_PANEL_CRASH_REBUILDS = 5
private const val GPU_SOFTWARE_LAYER_THRESHOLD_PX = 14_000
// 分块截图允许的物理高度上限（超过则放弃快照并保持保活）。
private const val MAX_TILE_CAPTURE_HEIGHT_PX = 32_768

/**
 * 混排消息分片：将小说 Markdown 正文与内嵌 HTML 交互面板解耦，
 * 使纯文本正文通过原生 `MarkdownBlock` 0 延迟秒出，仅在面板局部挂载 WebView。
 */
sealed interface MixedContentPiece {
    data class MarkdownBody(val text: String, val subIndex: Int) : MixedContentPiece
    data class HtmlPanel(
        val html: String,
        val panelTitle: String,
        val subIndex: Int,
    ) : MixedContentPiece
}

/**
 * 提取内嵌 HTML 面板的可读名称（用于在分步加载占位卡片中展示“正在加载「xxx」”）。
 */
fun extractPanelDisplayName(html: String, index: Int): String {
    Regex("""<!--\s*(?:panel|title|name)\s*[:：]\s*([^->]+?)\s*-->""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it.take(24) }

    Regex("""<(?:title|summary|h[1-4])[^>]*>([^<]{1,30})</(?:title|summary|h[1-4])>""", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it.replace(Regex("\\s+"), " ").take(24) }

    return when {
        html.contains("状态栏") || html.contains("stat_data") || html.contains("Mvu.getMvuData") -> "角色状态栏"
        html.contains("世界风云") || html.contains("新闻") || html.contains("world", ignoreCase = true) -> "世界状态面板"
        html.contains("好感度") || html.contains("羁绊") || html.contains("affection", ignoreCase = true) -> "好感与关系面板"
        Regex("<(form|input|select|textarea)\\b", RegexOption.IGNORE_CASE).containsMatchIn(html) -> "交互设定面板"
        Regex("<button\\b", RegexOption.IGNORE_CASE).containsMatchIn(html) -> "交互操作面板"
        else -> "预设渲染面板 #${index + 1}"
    }
}

private val FENCED_HTML_BLOCK_RE = Regex("""```(?:html|htm)?\s*\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)
private val TOP_HTML_CLUSTER_RE = Regex(
    """(?:<!--[\s\S]*?-->\s*)*(?:<(?:style|script)\b[^>]*>[\s\S]*?</(?:style|script)>\s*)+|(?:<(?:div|section|main|article|details|form)\b[^>]*>[\s\S]*?</(?:div|section|main|article|details|form)>\s*)+""",
    RegexOption.IGNORE_CASE,
)
private val FIRST_HTML_START_RE = Regex(
    """<(?:!doctype\s+html|html|style|script|div|section|main|article|details|form)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * 查找从 [startPos] 开始的同标签配对闭合区间（支持嵌套同名标签，如 `<div>...<div>...</div>...</div>`）。
 */
private fun findBalancedTagEnd(text: String, tagStart: Int, tagName: String): Int {
    val openRe = Regex("""<$tagName(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    val closeRe = Regex("""</$tagName\s*>""", RegexOption.IGNORE_CASE)
    var depth = 0
    var cursor = tagStart
    while (cursor < text.length) {
        val nextOpen = openRe.find(text, cursor)
        val nextClose = closeRe.find(text, cursor) ?: return -1
        if (nextOpen != null && nextOpen.range.first < nextClose.range.first) {
            // 自闭合标签不增加深度
            if (!nextOpen.value.endsWith("/>")) {
                depth++
            }
            cursor = nextOpen.range.last + 1
        } else {
            depth--
            cursor = nextClose.range.last + 1
            if (depth <= 0) return cursor
        }
    }
    return -1
}

/**
 * 扫描文本中的所有顶层 HTML 节点区间 `IntRange`（自动跳过非 HTML 代码围栏如 ```xml ... ```）。
 */
internal fun findTopLevelHtmlNodes(content: String): List<IntRange> {
    if (content.length < 16 || !content.contains('<')) return emptyList()
    val excludedFences = NON_HTML_CODE_FENCE_RE.findAll(content).map { it.range }.toList()
    fun isExcluded(pos: Int): Boolean = excludedFences.any { pos in it }

    val nodes = mutableListOf<IntRange>()
    for (m in FENCED_HTML_BLOCK_RE.findAll(content)) {
        if (isExcluded(m.range.first)) continue
        val inner = m.groupValues[1]
        if (shouldRenderAsHtmlPanel(inner)) {
            nodes += m.range
        }
    }

    val tagStartRe = Regex(
        """<!--[\s\S]*?-->|<(style|script|div|section|main|article|details|form|table|aside|header|footer|svg|canvas|iframe)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    var idx = 0
    while (idx < content.length) {
        if (isExcluded(idx)) {
            idx = excludedFences.first { idx in it }.last + 1
            continue
        }
        if (nodes.any { idx in it }) {
            idx = nodes.first { idx in it }.last + 1
            continue
        }
        val m = tagStartRe.find(content, idx) ?: break
        if (isExcluded(m.range.first) || nodes.any { m.range.first in it }) {
            idx = m.range.last + 1
            continue
        }
        val blockStart = m.range.first
        val blockEnd = if (m.value.startsWith("<!--")) {
            m.range.last + 1
        } else {
            val tag = m.groupValues[1].lowercase()
            val end = findBalancedTagEnd(content, m.range.first, tag)
            if (end > m.range.first) end else (m.range.last + 1)
        }
        if (blockEnd > blockStart) {
            nodes += blockStart until blockEnd
            idx = blockEnd
        } else {
            idx = m.range.last + 1
        }
    }
    return nodes.sortedBy { it.first }
}

private fun isStyleNode(content: String, range: IntRange): Boolean =
    content.substring(range).trimStart().startsWith("<style", ignoreCase = true)

private fun isScriptNode(content: String, range: IntRange): Boolean =
    content.substring(range).trimStart().startsWith("<script", ignoreCase = true)

private fun isCommentNode(content: String, range: IntRange): Boolean =
    content.substring(range).trimStart().startsWith("<!--")

private fun isContainerNode(content: String, range: IntRange): Boolean {
    val s = content.substring(range).trimStart()
    if (s.startsWith("```")) return true
    return !isStyleNode(content, range) && !isScriptNode(content, range) && !isCommentNode(content, range)
}

private fun hasLaterContainerInContinuousCluster(
    content: String,
    nodes: List<IntRange>,
    startIndex: Int,
): Boolean {
    for (i in startIndex until nodes.size) {
        val prevEnd = nodes[i - 1].last + 1
        val curStart = nodes[i].first
        if (curStart > prevEnd && content.substring(prevEnd, curStart).isNotBlank()) {
            return false
        }
        if (isContainerNode(content, nodes[i])) {
            return true
        }
    }
    return false
}

/**
 * 将顶层 HTML 节点按语义聚合为独立面板区间：
 * 1. 若两个节点之间存在非空白 Markdown 文本，断开为不同组；
 * 2. 若当前组已包含容器节点（`seenContainer == true`），且遇到新的 `<style>` 节点，同时后方紧跟另一个容器节点
 *    （如 `36cf5ad94fc2908e.png` 中的 `<style>...post-card...</style><div class="post-card">...</div>`），
 *    在 `<style>` 之前切分，防止 `<style>` 被前一个面板吞噬；
 * 3. 若当前组已包含容器节点且下一个节点也是独立容器节点（中间无 `<style>`），同样切分，保证多张独立卡片各自拥有独立面板。
 */
internal fun groupTopLevelHtmlNodes(
    content: String,
    nodes: List<IntRange>,
): List<IntRange> {
    if (nodes.isEmpty()) return emptyList()
    val groups = mutableListOf<IntRange>()
    var start = -1
    var end = -1
    var seenContainer = false

    fun flush() {
        if (start >= 0 && end > start) {
            groups += start until end
        }
        start = -1
        end = -1
        seenContainer = false
    }

    nodes.forEachIndexed { index, node ->
        if (end >= 0 && node.first > end && content.substring(end, node.first).isNotBlank()) {
            flush()
        }
        val isStyle = isStyleNode(content, node)
        val isContainer = isContainerNode(content, node)
        val hasLaterContainer = hasLaterContainerInContinuousCluster(content, nodes, index + 1)

        if (start < 0) {
            start = node.first
            end = node.last + 1
            seenContainer = isContainer
        } else if (isStyle && seenContainer && hasLaterContainer) {
            flush()
            start = node.first
            end = node.last + 1
            seenContainer = false
        } else if (isContainer && seenContainer) {
            flush()
            start = node.first
            end = node.last + 1
            seenContainer = true
        } else {
            end = node.last + 1
            if (isContainer) seenContainer = true
        }
    }
    flush()
    return groups
}

/**
 * 扫描文本中的所有顶层 HTML 复合块区间 `[start, endExclusive)`（合并相邻的 `<!-- -->`/`<style>`/`<script>`/`<div>` 等标签）。
 */
private fun findInteractiveHtmlSpans(content: String): List<Pair<Int, Int>> {
    val nodes = findTopLevelHtmlNodes(content)
    val groups = groupTopLevelHtmlNodes(content, nodes)
    return groups.mapNotNull { range ->
        val candidate = content.substring(range).trim()
        val unwrapped = FENCED_HTML_BLOCK_RE.matchEntire(candidate)?.groupValues?.getOrNull(1)?.trim() ?: candidate
        if (shouldRenderAsHtmlPanel(unwrapped)) {
            range.first to (range.last + 1)
        } else {
            null
        }
    }
}

/**
 * 将单条消息拆分为「Markdown 正文分片 + 局部 HTML 面板分片」。
 */
fun splitMixedMessagePieces(content: String): List<MixedContentPiece> {
    val trimmed = content.trim()
    if (!shouldRenderAsHtmlPanel(trimmed)) {
        return listOf(MixedContentPiece.MarkdownBody(content, 0))
    }
    if (
        trimmed.startsWith("<!doctype html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true)
    ) {
        return listOf(
            MixedContentPiece.HtmlPanel(
                html = content,
                panelTitle = extractPanelDisplayName(content, 0),
                subIndex = 0,
            )
        )
    }

    val spans = findInteractiveHtmlSpans(content)
    if (spans.isNotEmpty()) {
        val pieces = mutableListOf<MixedContentPiece>()
        var cursor = 0
        var panelIdx = 0
        for ((start, end) in spans) {
            if (start > cursor) {
                val md = content.substring(cursor, start).trim()
                if (md.isNotEmpty()) {
                    pieces += MixedContentPiece.MarkdownBody(md, pieces.size)
                }
            }
            val rawSlice = content.substring(start, end).trim()
            val unwrappedHtml = FENCED_HTML_BLOCK_RE.matchEntire(rawSlice)?.groupValues?.getOrNull(1)?.trim() ?: rawSlice
            pieces += MixedContentPiece.HtmlPanel(
                html = unwrappedHtml,
                panelTitle = extractPanelDisplayName(unwrappedHtml, panelIdx++),
                subIndex = pieces.size,
            )
            cursor = end
        }
        if (cursor < content.length) {
            val tailMd = content.substring(cursor).trim()
            if (tailMd.isNotEmpty()) {
                pieces += MixedContentPiece.MarkdownBody(tailMd, pieces.size)
            }
        }
        if (pieces.isNotEmpty()) return pieces
    }

    // 兜底：若首个 HTML 标签前有 >40 字符的纯文本导语，拆为前导 MarkdownBody + 后续 HtmlPanel
    val firstTag = FIRST_HTML_START_RE.find(content)
    if (firstTag != null && firstTag.range.first > 40) {
        val leadingMd = content.substring(0, firstTag.range.first).trim()
        val trailingHtml = content.substring(firstTag.range.first).trim()
        if (leadingMd.isNotEmpty() && shouldRenderAsHtmlPanel(trailingHtml)) {
            return listOf(
                MixedContentPiece.MarkdownBody(leadingMd, 0),
                MixedContentPiece.HtmlPanel(
                    html = trailingHtml,
                    panelTitle = extractPanelDisplayName(trailingHtml, 0),
                    subIndex = 1,
                ),
            )
        }
    }

    return listOf(
        MixedContentPiece.HtmlPanel(
            html = content,
            panelTitle = extractPanelDisplayName(content, 0),
            subIndex = 0,
        )
    )
}

/**
 * 面板分步加载阶段状态（驱动 `PanelLoadingPlaceholderCard` 显示明确进度文案）。
 */
sealed interface PanelLoadStage {
    data class Queued(val panelTitle: String, val position: Int, val totalQueued: Int) : PanelLoadStage
    data class WaitingHost(val panelTitle: String) : PanelLoadStage
    data class LoadingWebView(val panelTitle: String) : PanelLoadStage
    data class MountingDom(val panelTitle: String) : PanelLoadStage
    data class WaitingVars(val panelTitle: String) : PanelLoadStage
    data class ResourcesLoading(val panelTitle: String) : PanelLoadStage
    data object Ready : PanelLoadStage
}

/**
 * 全局 WebView 面板串行分步加载调度器：
 * - 主运行时优先屏障：等待 `TavernRuntimeManager.isPageReady`（最多 1500ms 超时自动放行）；
 * - 串行单令牌放行：同一时刻只允许 1 个面板实例化 WebView 并加载 `panel.html`；
 * - 单面板完成后留出 60ms 帧间冷却间隔，彻底避免多个 WebView 同帧哄抢 `CrRendererMain`。
 */
internal object TavernPanelLoadScheduler {
    const val HOST_READY_WAIT_MAX_MS = 800L
    /**
     * 单面板占用渲染槽位的超时。
     *
     * 语义：等待该面板「首次上报高度」(markDone)，之后立刻轮转给队列下一项。
     * 原值 2500ms 对重型角色卡够用，但一个卡死面板会把后续全部堵满 2.5 秒，
     * 连续滑过多个楼层时表现为"一直转圈"。
     *
     * 因此拆成两档：普通内容 1200ms 让位更积极；BIG（角色卡大面板）保留 2500ms 的容错。
     */
    const val SINGLE_PANEL_TIMEOUT_MS = 1200L
    const val SINGLE_PANEL_TIMEOUT_HEAVY_MS = 2500L
    const val INTER_PANEL_COOLDOWN_MS = 60L

    /**
     * 待加载队列上限。
     *
     * 背景：加载是严格串行的（同一时刻只允许 1 个面板），每面板最长占 2.5s 超时窗口。
     * 快速滑过 10 个楼层时若全部入队，最坏要排队 25 秒 —— 用户看到的就是「一直加载」。
     * 但排太久的楼层在真正轮到它时用户早已滑走，加载它纯属浪费。
     * 因此限制队列长度，超出的请求直接放行（不加载），由快照/占位卡片兜底。
     */
    // 队列上限：真机实测 3 太小 —— 正常滚动时可见楼层就可能同时 4~5 个，
    // 被丢弃的请求会一直停在"正在排队加载"的占位卡片上，观感比慢更差。
    // 放宽到 6，仍能在快速滑动时抑制无限排队。
    const val MAX_PENDING_QUEUE = 6
    const val SCROLL_SETTLE_DELAY_MS = 150L

    data class QueueRequest(
        val key: String,
        val title: String,
        /** 重型面板（如角色卡大面板）给更宽松的槽位超时。 */
        val heavy: Boolean = false,
        /** 是否因队列超长被丢弃（丢弃后应短暂等待再重排，而不是假装已获槽位）。 */
        @Volatile
        var dropped: Boolean = false,
        val gate: CompletableDeferred<Unit> = CompletableDeferred(),
        val done: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingQueue = ArrayDeque<QueueRequest>()
    private var activeRequest: QueueRequest? = null
    private var pumpJob: Job? = null

    private val _queueSnapshot = MutableStateFlow<List<String>>(emptyList())
    val queueSnapshot: StateFlow<List<String>> = _queueSnapshot.asStateFlow()

    @Volatile
    var hostReadyProvider: () -> Boolean = { TavernRuntimeManager.isPageReady }

    @Volatile
    var cooldownMsOverride: Long? = null

    @Volatile
    var scrollDeferEnabled: Boolean = true

    @Volatile
    private var lastScrollStoppedAtMs: Long = 0L

    private val _isListScrollingFlow = MutableStateFlow(false)
    val isListScrollingFlow: StateFlow<Boolean> = _isListScrollingFlow.asStateFlow()

    fun setListScrolling(scrolling: Boolean) {
        if (!scrolling && _isListScrollingFlow.value) {
            lastScrollStoppedAtMs = runCatching { android.os.SystemClock.uptimeMillis() }.getOrDefault(0L)
        }
        _isListScrollingFlow.value = scrolling
    }

    fun enqueue(
        key: String,
        title: String,
        prioritize: Boolean = false,
        heavy: Boolean = false,
    ): QueueRequest {
        val req: QueueRequest
        synchronized(lock) {
            val active = activeRequest
            if (active != null && active.key == key) {
                return active
            }
            val existingIdx = pendingQueue.indexOfFirst { it.key == key }
            if (existingIdx >= 0) {
                val existing = pendingQueue.removeAt(existingIdx)
                if (prioritize) {
                    pendingQueue.addFirst(existing)
                } else {
                    pendingQueue.addLast(existing)
                }
                publishSnapshotLocked()
                EnsurePumpLocked()
                return existing
            }
            req = QueueRequest(key = key, title = title, heavy = heavy)
            if (prioritize) {
                pendingQueue.addFirst(req)
            } else {
                pendingQueue.addLast(req)
            }
            // 队列超长时丢弃队尾（最老、最可能已被用户滑过）的请求。
            // 被丢弃者直接放行 gate/done，让调用方走占位或快照路径，不再干等。
            while (pendingQueue.size > MAX_PENDING_QUEUE) {
                val dropped = pendingQueue.removeAt(pendingQueue.size - 1)
                if (dropped.key != key) {
                    dropped.dropped = true
                    dropped.gate.complete(Unit)
                    dropped.done.complete(Unit)
                    runCatching {
                        TavernRuntimeManager.appendLog(
                            "debug",
                            "[panel-queue] dropped tail key=${dropped.key} (queue=${pendingQueue.size}/$MAX_PENDING_QUEUE)",
                        )
                    }
                }
            }
            publishSnapshotLocked()
            EnsurePumpLocked()
        }
        return req
    }

    fun markDone(key: String) {
        synchronized(lock) {
            val active = activeRequest
            if (active != null && active.key == key) {
                active.done.complete(Unit)
            }
            val idx = pendingQueue.indexOfFirst { it.key == key }
            if (idx >= 0) {
                val removed = pendingQueue.removeAt(idx)
                removed.done.complete(Unit)
                publishSnapshotLocked()
            }
        }
    }

    fun cancel(key: String) {
        synchronized(lock) {
            val active = activeRequest
            if (active != null && active.key == key) {
                active.gate.cancel()
                active.done.complete(Unit)
            }
            val idx = pendingQueue.indexOfFirst { it.key == key }
            if (idx >= 0) {
                val removed = pendingQueue.removeAt(idx)
                removed.gate.cancel()
                removed.done.complete(Unit)
                publishSnapshotLocked()
            }
        }
    }

    internal fun resetForTest() {
        _isListScrollingFlow.value = false
        lastScrollStoppedAtMs = 0L
        synchronized(lock) {
            pumpJob?.cancel()
            pumpJob = null
            activeRequest?.done?.complete(Unit)
            activeRequest = null
            pendingQueue.clear()
            _queueSnapshot.value = emptyList()
        }
    }

    private fun publishSnapshotLocked() {
        val list = buildList {
            activeRequest?.let { add(it.key) }
            pendingQueue.forEach { add(it.key) }
        }
        _queueSnapshot.value = list
    }

    private fun EnsurePumpLocked() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            pumpLoop()
        }
    }

    private suspend fun pumpLoop() {
        var waited = 0L
        while (!hostReadyProvider() && waited < HOST_READY_WAIT_MAX_MS) {
            delay(50L)
            waited += 50L
        }

        while (true) {
            // 阶段 6.2：列表快速滑动期间延后挂载新 WebView，停止滚动 150ms 后再放行
            if (scrollDeferEnabled) {
                while (_isListScrollingFlow.value) {
                    delay(50L)
                }
                val stoppedAt = lastScrollStoppedAtMs
                if (stoppedAt > 0L) {
                    val now = runCatching { android.os.SystemClock.uptimeMillis() }.getOrDefault(0L)
                    val remaining = SCROLL_SETTLE_DELAY_MS - (now - stoppedAt)
                    if (remaining > 0L && remaining <= SCROLL_SETTLE_DELAY_MS) {
                        delay(remaining)
                    }
                }
            }
            var sendGuardWaited = 0L
            while (TavernRuntimeManager.isSendOrHookInFlight && sendGuardWaited < 3500L) {
                delay(60L)
                sendGuardWaited += 60L
            }

            val next: QueueRequest = synchronized(lock) {
                if (pendingQueue.isEmpty()) {
                    activeRequest = null
                    publishSnapshotLocked()
                    return
                }
                val popped = pendingQueue.removeFirst()
                activeRequest = popped
                publishSnapshotLocked()
                popped
            }

            next.gate.complete(Unit)

            val slotTimeout = if (next.heavy) SINGLE_PANEL_TIMEOUT_HEAVY_MS else SINGLE_PANEL_TIMEOUT_MS
            withTimeoutOrNull(slotTimeout) {
                next.done.await()
            }

            synchronized(lock) {
                if (activeRequest?.key == next.key) {
                    activeRequest = null
                }
                publishSnapshotLocked()
            }

            val cooldown = cooldownMsOverride ?: INTER_PANEL_COOLDOWN_MS
            if (cooldown > 0) {
                delay(cooldown)
            }
        }
    }
}

/**
 * 全局活跃面板 WebView 实例池（LRU 上限控制）：
 * - Android 所有 WebView 共享单渲染进程 `CrRendererMain`，当会话中同时挂载过多重度 HTML/Canvas/iframe 面板时，
 *   极易耗尽 Native/GPU 内存引发 `RenderProcessGone` 或整进程 `SIGABRT` 闪退。
 * - 本池将同时存活的面板 WebView 严格限制在 [MAX_ACTIVE_PANEL_WEBVIEWS]（默认 3 个）以内；
 * - 当第 4 个新面板挂载时，自动淘汰最早挂载的历史面板 WebView。
 */
/**
 * 面板活跃池淘汰优先级（数值越大越不容易被淘汰）：
 * 1. [INTERACTING] 正在被触摸/操作：不淘汰
 * 2. [LATEST_FLOOR] 最新一楼：不淘汰
 * 3. [VISIBLE] 屏幕内可见：不淘汰
 * 4. [ADJACENT_OFFSCREEN] 屏幕外相邻
 * 5. [BACKGROUND] 其余历史楼层
 */
internal enum class PanelPoolPriority(val rank: Int) {
    BACKGROUND(0),
    ADJACENT_OFFSCREEN(1),
    VISIBLE(2),
    LATEST_FLOOR(3),
    INTERACTING(4),
}

internal object ActivePanelWebViewPool {
    // 保留原有公开预算常量（既有回归测试依赖它们）；实际运行使用内存分级或手动覆盖预算。
    const val MAX_ACTIVE_PANEL_WEBVIEWS = 3
    const val LIVE_QUIESCENT_MAX_ACTIVE_WEBVIEWS = 4
    const val DEFAULT_SAFE_MAX_ACTIVE_WEBVIEWS = 6

    const val RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS = 3
    const val RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS = 2
    const val RUNTIME_LIVE_MAX_ACTIVE_WEBVIEWS = 3

    // 阶段 1 新增常量：硬上限 16，按设备总内存分级（初值，待真机实测微调）
    const val HARD_MAX_ACTIVE_WEBVIEWS = 16
    const val DEFAULT_TARGET_ACTIVE_WEBVIEWS = 8
    const val MEM_TIER_HIGH_LIMIT = 16
    const val MEM_TIER_MID_LIMIT = 8
    const val MEM_TIER_LOW_LIMIT = 4
    const val MEM_TIER_HIGH_THRESHOLD_BYTES = 10_500_000_000L
    const val MEM_TIER_MID_THRESHOLD_BYTES = 6_800_000_000L

    @Volatile
    private var cachedTotalMemBytes: Long = -1L

    fun detectDeviceTotalMemBytes(context: Context): Long {
        val cached = cachedTotalMemBytes
        if (cached > 0L) return cached
        val detected = runCatching {
            val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val info = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        if (detected > 0L) {
            cachedTotalMemBytes = detected
        }
        return detected
    }

    fun resolveMemoryTierMaxActive(totalMemBytes: Long, userOverride: Int = 0): Int {
        if (userOverride > 0) {
            return userOverride.coerceIn(1, HARD_MAX_ACTIVE_WEBVIEWS)
        }
        return when {
            totalMemBytes >= MEM_TIER_HIGH_THRESHOLD_BYTES -> MEM_TIER_HIGH_LIMIT
            totalMemBytes >= MEM_TIER_MID_THRESHOLD_BYTES -> MEM_TIER_MID_LIMIT
            else -> MEM_TIER_LOW_LIMIT
        }
    }

    fun resolveEffectiveMaxActive(
        totalMemBytes: Long,
        perfEnabled: Boolean,
        perfMode: WebViewPerfMode,
        userOverride: Int = 0,
    ): Int {
        if (userOverride > 0) {
            return userOverride.coerceIn(1, HARD_MAX_ACTIVE_WEBVIEWS)
        }
        val tierLimit = resolveMemoryTierMaxActive(totalMemBytes, 0)
        // 方案一（HIBERNATE_POOL，默认模式）此前被 RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS(=2) 硬压，
        // 导致内存分级对它完全无效 —— 12GB 机器也只有 2 个活跃面板，达不到计划第 4 节「默认 6~8 个」。
        // 现改为：方案一同样接受内存分级上限；仅在低内存档（4 个）时再收紧到 2，保住小内存机的安全边际。
        return when {
            perfEnabled && perfMode == WebViewPerfMode.HIBERNATE_POOL ->
                if (tierLimit <= MEM_TIER_LOW_LIMIT) {
                    minOf(tierLimit, RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS)
                } else {
                    tierLimit
                }
            else -> tierLimit
        }
    }
    data class PoolStats(
        val activeCount: Int,
        val peakActiveCount: Int,
        val evictionCount: Long,
        val renderProcessGoneCount: Long,
        val lastLimit: Int,
    )

    private val lock = Any()
    private val lruKeys = java.util.LinkedHashSet<String>()
    private val priorities = HashMap<String, PanelPoolPriority>()
    private val _activeKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    val activeKeysFlow: StateFlow<Set<String>> = _activeKeysFlow.asStateFlow()
    private var peakActiveCount: Int = 0
    private var evictionCount: Long = 0L
    private var renderProcessGoneCount: Long = 0L
    private var lastLimit: Int = RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS

    fun stats(): PoolStats = synchronized(lock) {
        PoolStats(
            activeCount = lruKeys.size,
            peakActiveCount = peakActiveCount,
            evictionCount = evictionCount,
            renderProcessGoneCount = renderProcessGoneCount,
            lastLimit = lastLimit,
        )
    }

    fun recordRenderProcessGone(key: String?, didCrash: Boolean, priority: Int) {
        val snapshot = synchronized(lock) {
            renderProcessGoneCount++
            PoolStats(lruKeys.size, peakActiveCount, evictionCount, renderProcessGoneCount, lastLimit)
        }
        runCatching {
            TavernRuntimeManager.appendLog(
                "error",
                "[panel-pool] renderProcessGone key=$key didCrash=$didCrash priority=$priority " +
                    "active=${snapshot.activeCount}/${snapshot.lastLimit} peak=${snapshot.peakActiveCount} " +
                    "evictions=${snapshot.evictionCount} goneCount=${snapshot.renderProcessGoneCount}",
            )
        }
    }

    fun acquire(
        key: String,
        maxActive: Int = MAX_ACTIVE_PANEL_WEBVIEWS,
        priority: PanelPoolPriority = PanelPoolPriority.BACKGROUND,
    ) {
        val limit = maxActive.coerceIn(1, HARD_MAX_ACTIVE_WEBVIEWS)
        synchronized(lock) {
            lastLimit = limit
            lruKeys.remove(key)
            lruKeys.add(key)
            priorities[key] = priority
            while (lruKeys.size > limit) {
                val hardExceeded = lruKeys.size > HARD_MAX_ACTIVE_WEBVIEWS
                var victim: String? = null
                var victimRank = Int.MAX_VALUE
                for (candidate in lruKeys) {
                    if (candidate == key) continue
                    val p = priorities[candidate] ?: PanelPoolPriority.BACKGROUND
                    // 正常超限时跳过正在交互/最新楼/屏幕内可见的受保护面板；仅在突破硬上限 16 时强制回收最低优先级非交互面板
                    if (!hardExceeded && p.rank >= PanelPoolPriority.VISIBLE.rank) continue
                    if (hardExceeded && p == PanelPoolPriority.INTERACTING) continue
                    if (p.rank < victimRank) {
                        victimRank = p.rank
                        victim = candidate
                        if (victimRank == PanelPoolPriority.BACKGROUND.rank) break
                    }
                }
                if (victim == null) break
                lruKeys.remove(victim)
                val evictedPriority = priorities.remove(victim) ?: PanelPoolPriority.BACKGROUND
                evictionCount++
                DetachedPanelWebViewCache.evictAndDestroy(victim)
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "info",
                        "[panel-pool] evicted panel WebView key=$victim priority=$evictedPriority " +
                            "(active=${lruKeys.size}/$limit peak=$peakActiveCount evictions=$evictionCount gone=$renderProcessGoneCount)",
                    )
                }
            }
            if (lruKeys.size > peakActiveCount) {
                peakActiveCount = lruKeys.size
            }
            _activeKeysFlow.value = lruKeys.toSet()
            runCatching {
                TavernRuntimeManager.appendLog(
                    "debug",
                    "[panel-pool] acquire key=$key priority=$priority active=${lruKeys.size}/$limit " +
                        "peak=$peakActiveCount evictions=$evictionCount gone=$renderProcessGoneCount",
                )
            }
        }
    }

    fun updatePriority(key: String, priority: PanelPoolPriority) {
        synchronized(lock) {
            if (key in lruKeys) {
                priorities[key] = priority
            }
        }
    }

    fun priorityOf(key: String): PanelPoolPriority = synchronized(lock) {
        priorities[key] ?: PanelPoolPriority.BACKGROUND
    }

    fun touch(key: String) {
        synchronized(lock) {
            if (key in lruKeys) {
                lruKeys.remove(key)
                lruKeys.add(key)
                _activeKeysFlow.value = lruKeys.toSet()
            }
        }
    }

    fun release(key: String) {
        synchronized(lock) {
            priorities.remove(key)
            if (lruKeys.remove(key)) {
                _activeKeysFlow.value = lruKeys.toSet()
            }
        }
    }

    fun activeCount(): Int = synchronized(lock) { lruKeys.size }

    fun clearAll() {
        synchronized(lock) {
            lruKeys.clear()
            priorities.clear()
            _activeKeysFlow.value = emptySet()
        }
        DetachedPanelWebViewCache.clearAll()
    }

    internal fun resetForTest() {
        synchronized(lock) {
            peakActiveCount = 0
            evictionCount = 0L
            renderProcessGoneCount = 0L
            lastLimit = RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS
        }
        clearAll()
    }
}

/**
 * 离屏面板 WebView 保活复用池（解决「从聊天页进入偏好设置再返回，或在方案一/二/三之间切换时，面板每次都重新渲染」的问题）：
 * - 当 `MessageHtmlBlock` 因页面导航（ChatPage -> Settings -> ChatPage）、LazyColumn 短暂滑出屏幕、或从方案三切回方案一/二而执行 `onRelease` 时，
 *   不再立即销毁 `WebView`，而是将其从父容器解绑并存入本 LRU 缓存（最多保留 4 个最近渲染完成的面板实例）；
 * - 当再次回到该楼层或切回方案一/二时，`AndroidView(factory)` 直接取回已挂载完成的 `PanelScrollBridge` 与 `PanelMountState`，
 *   0ms 原地恢复显示，彻底杜绝无谓的二次重渲染！
 */
internal object DetachedPanelWebViewCache {
    // 离屏保活上限。原值 2 太小：滚动时滑出屏幕的面板会立刻被挤出，
    // 回到屏幕就退化成"已自动休眠"卡片（真机已复现）。
    // 现按内存分级动态设定（见 detachedCapFor）。
    private const val MAX_DETACHED_WEBVIEWS = 2

    /** 动态上限：高内存设备可保活更多离屏面板。 */
    @Volatile
    var dynamicMaxDetached: Int = 2

    private fun effectiveMaxDetached(): Int =
        dynamicMaxDetached.coerceIn(1, 8)

    @Volatile
    var offscreenPauseEnabled: Boolean = true

    internal class CachedPanelEntry(
        val key: String,
        val container: FrameLayout,
        val mountState: PanelMountState,
    )

    private val lock = Any()
    private val map = java.util.LinkedHashMap<String, CachedPanelEntry>(8, 0.75f, true)

    fun has(key: String): Boolean = synchronized(lock) {
        val entry = map[key] ?: return false
        !entry.mountState.isDisposed && entry.mountState.webView != null
    }

    fun getMountState(key: String): PanelMountState? = synchronized(lock) {
        val entry = map[key] ?: return null
        if (entry.mountState.isDisposed || entry.mountState.webView == null) {
            map.remove(key)
            null
        } else {
            entry.mountState
        }
    }

    fun takeContainer(key: String): CachedPanelEntry? = synchronized(lock) {
        // 取走即移出离屏池：容器从此归调用方的组合所有，不再参与离屏 LRU 淘汰。
        // 原先用 map[key] 取出后条目仍留在池里，别的面板 park 触发淘汰时，
        // 可能把"屏幕上正在显示"的 WebView release() 掉，UI 就会永远转圈。
        val entry = map.remove(key) ?: return null
        if (entry.mountState.isDisposed || entry.mountState.webView == null) {
            return null
        }
        runCatching {
            (entry.container.parent as? ViewGroup)?.removeView(entry.container)
        }
        // 与 parkContainer 的 onPause() 配对恢复该面板自身的渲染与动画/rAF。
        runCatching { entry.mountState.webView?.onResume() }
        runCatching {
            entry.mountState.webView?.evaluateJavascript(
                "try{if(window.__rikkaResumeOffscreen)window.__rikkaResumeOffscreen();if(window.__rikkaPanelAwaitSettled)window.__rikkaPanelAwaitSettled();}catch(e){}",
                null,
            )
        }
        entry
    }

    fun parkContainer(key: String, container: FrameLayout, mountState: PanelMountState) {
        if (mountState.isDisposed || mountState.webView == null) return
        val evictedList = mutableListOf<CachedPanelEntry>()
        synchronized(lock) {
            runCatching {
                (container.parent as? ViewGroup)?.removeView(container)
            }
            val previous = map.put(key, CachedPanelEntry(key = key, container = container, mountState = mountState))
            if (previous != null && previous.container !== container) {
                // 同 key 之前还停着另一个容器（重叠组合的遗留）。直接覆盖会让它的 WebView 泄漏，
                // 交给下面统一的 evictedList 释放。
                evictedList += previous
            }
            // 离屏立即暂停该面板自身的定时器/动画/媒体，减少多个 WebView 同时占用主线程。
            //
            // 边界说明（已按官方文档核实，勿改口径）：
            // WebView.onPause() 的契约是"best-effort 暂停可安全暂停的处理"，官方明确写着
            // **does not pause JavaScript；要全局停 JS 定时器必须用 pauseTimers()**。
            // 本工程从未调用 pauseTimers()/resumeTimers()，因此这里**不会**影响同进程其他
            // WebView（包括主运行时）的 setTimeout/Promise。
            // 曾被怀疑为「上游收不到请求」的根因，但该因果链与官方文档冲突，已排除。
            if (offscreenPauseEnabled) {
                runCatching {
                    mountState.webView?.evaluateJavascript(
                        "try{if(window.__rikkaPauseOffscreen)window.__rikkaPauseOffscreen();}catch(e){}",
                        null,
                    )
                }
            }
            runCatching { mountState.webView?.onPause() }
            while (map.size > effectiveMaxDetached()) {
                val it = map.entries.iterator()
                if (!it.hasNext()) break
                val oldest = it.next().value
                it.remove()
                if (oldest.key != key) {
                    evictedList += oldest
                }
            }
        }
        evictedList.forEach { old ->
            runCatching {
                (old.container.parent as? ViewGroup)?.removeView(old.container)
                old.mountState.release()
            }
        }
    }

    fun size(): Int = synchronized(lock) { map.size }

    fun evictAndDestroy(key: String) {
        val removed = synchronized(lock) { map.remove(key) } ?: return
        Handler(Looper.getMainLooper()).post {
            runCatching {
                (removed.container.parent as? ViewGroup)?.removeView(removed.container)
                removed.mountState.release()
            }
        }
    }

    fun clearAll() {
        val removedList = synchronized(lock) {
            val all = map.values.toList()
            map.clear()
            all
        }
        if (removedList.isEmpty()) return
        Handler(Looper.getMainLooper()).post {
            removedList.forEach { removed ->
                runCatching {
                    (removed.container.parent as? ViewGroup)?.removeView(removed.container)
                    removed.mountState.release()
                }
            }
        }
    }
}

@Composable
internal fun PanelHibernatedPlaceholderCard(
    panelTitle: String,
    onWakeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onWakeUp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = panelTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "历史交互面板已自动休眠（防多 WebView 卡顿与闪退）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onWakeUp) {
                Text("唤醒面板", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 流式生成期间的 HTML 面板占位卡片。
 *
 * 为什么需要它：面板要等整条消息生成完才挂 WebView（streaming 期间每个 token 都重建面板代价太高），
 * 但这段窗口里也**不能**把 HTML 原文扔给 MarkdownBlock —— 那会把 `<style>`/`<div>` 显示成代码。
 * 所以：能抽出可读正文就显示正文预览（见 [StTextContent]），抽不出正文时显示这张卡片。
 */
@Composable
internal fun PanelStreamingPlaceholderCard(
    panelTitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContainedLoadingIndicator(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = panelTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "正在生成界面内容，完成后自动渲染",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 面板分步加载期间的显式动态占位提示卡片（展示具体面板名称、当前阶段与「优先加载」插队按钮）。
 */
data class PanelDiagnostic(
    val messageKey: String?,
    val panelTitle: String,
    val stage: String,
    val mounted: Boolean,
    val heightDp: Int,
    val retries: Int,
    val lastError: String?,
)

@Composable
internal fun PanelLoadingPlaceholderCard(
    stage: PanelLoadStage,
    panelTitle: String,
    onPrioritize: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    val statusText = when (stage) {
        is PanelLoadStage.Queued ->
            "正在按序排队加载「${stage.panelTitle}」（队列 ${stage.position}/${stage.totalQueued}）…"
        is PanelLoadStage.WaitingHost ->
            "正在等待酒馆主运行时就绪以加载「${stage.panelTitle}」…"
        is PanelLoadStage.LoadingWebView ->
            "正在加载「${stage.panelTitle}」WebView 渲染容器…"
        is PanelLoadStage.MountingDom ->
            "正在渲染「${stage.panelTitle}」界面排版与脚本…"
        is PanelLoadStage.WaitingVars ->
            "正在同步「${stage.panelTitle}」状态变量数据…"
        is PanelLoadStage.ResourcesLoading ->
            "骨架已显示，正在加载「${stage.panelTitle}」的图片/字体/脚本…"
        PanelLoadStage.Ready ->
            "「$panelTitle」资源已就绪"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = panelTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stage is PanelLoadStage.Queued && stage.position > 1 && onPrioritize != null) {
                    TextButton(onClick = onPrioritize) {
                        Text("优先加载", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onReload != null) {
                    TextButton(onClick = onReload) {
                        Text("重载面板", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (!errorText.isNullOrBlank()) {
            Text(
                text = "错误：$errorText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 检测消息文本是否需要「内嵌 HTML/JS 渲染」（v182 C4，v183 放宽）。
 */
private val NON_HTML_CODE_FENCE_RE = Regex(
    """```(?!html\b|htm\b|\s*\r?\n)[a-zA-Z0-9_+-]+[^\n]*\n[\s\S]*?```""",
    RegexOption.IGNORE_CASE,
)

fun looksLikeInteractiveHtml(text: String): Boolean {
    if (text.length < 32) return false
    val cleaned = if (text.contains("```")) NON_HTML_CODE_FENCE_RE.replace(text, "") else text
    if (cleaned.length < 24) return false
    if (Regex("<script[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return true
    if (Regex("\\son[a-z]{3,}\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return true
    if (Regex("<(!doctype\\s+html|html[\\s>])", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return true
    if (Regex("<(iframe|canvas|svg|audio|video)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return true
    if (cleaned.contains("data-sttts-", ignoreCase = true) || cleaned.contains("sttts-", ignoreCase = true)) return true
    val hasContainer = Regex("<(div|span|section|details|main|article|table|aside|header|footer)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    if (
        hasContainer &&
        Regex("<(style|link\\s+[^>]*rel\\s*=\\s*['\"]?stylesheet)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    ) {
        return true
    }
    if (
        hasContainer &&
        Regex("(background(?:-image)?\\s*:\\s*[^;\"']*url\\s*\\(|display\\s*:\\s*(?:inline-)?(?:flex|grid)\\b)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    ) {
        return true
    }
    val hasWidget = Regex("<(button|input|select|textarea|form)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    val hasStruct = Regex("<(div|section|form|details|table)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned) &&
        Regex("</(div|section|form|details|table)>", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    return hasWidget && hasStruct
}

/**
 * 识别静态结构化 HTML 卡片片段（如 `<div class="post-card">...</div>`，即使不含 `<script>` 且 `<style>` 位于相邻块或内联属性中）。
 * 先剥离非 HTML 代码围栏（如 ```xml ... ```），防止 XML/代码片段误判为 HTML 面板。
 */
fun looksLikeHtmlFragment(text: String): Boolean {
    if (text.length < 24) return false
    val cleaned = if (text.contains("```")) NON_HTML_CODE_FENCE_RE.replace(text, "") else text
    val unwrapped = FENCED_HTML_BLOCK_RE.matchEntire(cleaned.trim())?.groupValues?.getOrNull(1)?.trim() ?: cleaned.trim()
    if (unwrapped.length < 24) return false
    val hasOpen = Regex("<(div|section|article|main|aside|header|footer|table|details|form|svg)\\b[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(unwrapped)
    val hasClose = Regex("</(div|section|article|main|aside|header|footer|table|details|form|svg)>", RegexOption.IGNORE_CASE).containsMatchIn(unwrapped)
    if (!hasOpen || !hasClose) return false
    val hasClassOrStyle = Regex("\\b(?:class|style|id)\\s*=\\s*['\"][^'\"]+['\"]", RegexOption.IGNORE_CASE).containsMatchIn(unwrapped)
    if (hasClassOrStyle) return true
    val closeCount = Regex("</[a-zA-Z][a-zA-Z0-9_-]*>").findAll(unwrapped).count()
    return closeCount >= 2
}

fun shouldRenderAsHtmlPanel(text: String): Boolean =
    looksLikeInteractiveHtml(text) || looksLikeHtmlFragment(text)

/**
 * 阶段 3：HTML 面板五类内容分级。
 * - [STATIC] 静态美化：渲染一次 -> 快照 -> 永久释放 WebView
 * - [ANIMATED] 动态展示：有动画/粒子但无交互；仅最新楼或可见焦点挂 WebView，历史楼显示快照
 * - [LIGHT] 轻交互：展开收起、details、纯显示切换；旧楼冻结，点击唤醒
 * - [SEND] 发送类：调用宿主桥发消息；仅最新楼存活，旧楼冻结
 * - [BIG] 角色卡大面板：内部滚动容器/手势/复杂内部状态；仅最新楼存活，旧楼冻结，点击唤醒
 */
enum class PanelClass {
    STATIC,
    ANIMATED,
    LIGHT,
    SEND,
    BIG,
}

private val PANEL_SEND_RE = Regex(
    """(?:triggerSlash|RikkaBridge\s*\.\s*(?:send|trigger|emit)|sendMessage|sendTextMessage|createChatMessages|TavernHelper\s*\.\s*trigger|data-st-send|/send\s)""",
    RegexOption.IGNORE_CASE,
)
private val PANEL_BIG_SCROLL_RE = Regex(
    """(?:overflow(?:-[xy])?\s*:\s*(?:auto|scroll)|touchstart|touchmove|pointermove|scrollLeft|scrollTop|scrollIntoView|swiper|carousel)""",
    RegexOption.IGNORE_CASE,
)
private val PANEL_LIGHT_INTERACT_RE = Regex(
    """(?:<(?:details|button|input|select|textarea|form)\b|\son[a-z]{3,}\s*=|addEventListener\s*\(|:target\b|:checked\b|<a\s+[^>]*href\s*=)""",
    RegexOption.IGNORE_CASE,
)
private val PANEL_ANIMATED_RE = Regex(
    """(?:@keyframes\b|animation(?:-name)?\s*:|requestAnimationFrame\s*\(|setInterval\s*\(|<(?:canvas|video)\b|\.gif\b|particles|lottie)""",
    RegexOption.IGNORE_CASE,
)
private val PANEL_BUTTON_TAG_RE = Regex("""<button\b""", RegexOption.IGNORE_CASE)
private val PANEL_SCRIPT_TAG_RE = Regex("""<script[\s>]""", RegexOption.IGNORE_CASE)
private val PANEL_STYLE_BLOCK_RE = Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE)
private val PANEL_SCRIPT_BLOCK_RE = Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE)
private val PANEL_HTML_TAG_RE = Regex("""<[^>]+>""")

fun classifyPanelStatic(html: String): PanelClass {
    val cleaned = if (html.contains("```")) NON_HTML_CODE_FENCE_RE.replace(html, "") else html
    if (PANEL_SEND_RE.containsMatchIn(cleaned)) return PanelClass.SEND
    val hasScript = PANEL_SCRIPT_TAG_RE.containsMatchIn(cleaned)
    val buttonCount = PANEL_BUTTON_TAG_RE.findAll(cleaned).count()
    if (PANEL_BIG_SCROLL_RE.containsMatchIn(cleaned) || (hasScript && buttonCount >= 5)) {
        return PanelClass.BIG
    }
    if (PANEL_LIGHT_INTERACT_RE.containsMatchIn(cleaned)) return PanelClass.LIGHT
    if (PANEL_ANIMATED_RE.containsMatchIn(cleaned) || hasScript) return PanelClass.ANIMATED
    return PanelClass.STATIC
}

fun extractStaticPanelPlainText(html: String): String {
    val withoutBlocks = PANEL_SCRIPT_BLOCK_RE.replace(PANEL_STYLE_BLOCK_RE.replace(html, " "), " ")
    val stripped = PANEL_HTML_TAG_RE.replace(withoutBlocks, " ")
    return stripped
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal object PanelClassificationCache {
    private const val MAX_ENTRIES = 256
    private val map = java.util.LinkedHashMap<Int, PanelClass>(64, 0.75f, true)

    fun getOrClassify(html: String): PanelClass {
        val hash = html.hashCode()
        synchronized(map) {
            map[hash]?.let { return it }
        }
        val classified = classifyPanelStatic(html)
        synchronized(map) {
            map[hash] = classified
            while (map.size > MAX_ENTRIES) {
                val it = map.keys.iterator()
                if (!it.hasNext()) break
                it.next()
                it.remove()
            }
        }
        return classified
    }

    fun recordRuntimeClass(contentHash: Int, probed: PanelClass) {
        synchronized(map) {
            val existing = map[contentHash]
            // 运行时探测只向更重类别升级，或修正静态漏判（例如动态脚本绑定了点击/滚动/发送）
            val merged = when {
                existing == null -> probed
                probed.ordinal > existing.ordinal -> probed
                else -> existing
            }
            map[contentHash] = merged
        }
    }

    fun getCached(contentHash: Int): PanelClass? = synchronized(map) { map[contentHash] }

    internal fun resetForTest() {
        synchronized(map) { map.clear() }
    }
}


/**
 * 单个 HTML 面板 WebView 纹理层的像素预算（高度方向）。
 *
 * [v251 根因修复] 原实现直接用 GPU 硬上限 4096px 当预算 —— 这是「频闪」的真因：
 *
 *  - Android 单层硬件加速纹理的硬上限确实是 4096px，但**贴着上限分配 = 零余量**。
 *    Chromium 的合成器还需要额外的 tile 来支持滚动/重绘，一旦总量超预算就打印
 *    `tile_manager.cc: tile memory limits exceeded, some content may not draw`
 *    并**随机丢弃部分 tile** —— 用户看到的就是「一半闪一半不闪」。
 *  - 真机日志实证（2026-11-24 21:39~21:43）：该警告出现 **164 次**，
 *    且每次都紧跟面板高度上报（1365dp × 3 = 4095px，正好贴死 4096）。
 *  - 4 个面板同时存活时：4 × 1080 × 4095px ≈ 71MB 纯纹理，远超合成器预算。
 *
 * 取 2048px 的理由：
 *  - 留出 2× 余量给合成器 tile（这是「不闪」的充要条件）；
 *  - 2048 / 3(dpi) ≈ 682dp，仍覆盖手机视口高度的 85%，视觉上几乎无感；
 *  - 超过此高度的面板本来就已经启用内部滚动（`isCappedScrollable = true`），
 *    多给的那部分高度用户根本看不到，纯属浪费。
 */
internal const val MAX_PANEL_TEXTURE_PX = 2048

/** 面板高度的 dp 上限（由 [MAX_PANEL_TEXTURE_PX] 与当前 density 共同决定）。 */
internal fun panelMaxHeightDp(density: Float): Int =
    minOf(1600, (MAX_PANEL_TEXTURE_PX / density.coerceAtLeast(1f)).toInt()).coerceAtLeast(480)

/**
 * 计算 HTML 面板在 GPU 硬件加速纹理限制下的安全显示高度（dp）与是否启用内部滚动。
 */
internal fun computePanelHeightConstraint(
    rawContentHeightDp: Int,
    density: Float,
    maxTexturePx: Int = MAX_PANEL_TEXTURE_PX,
): Pair<Int, Boolean> {
    val safeDensity = density.coerceAtLeast(1f)
    val gpuSafeMaxDp = minOf(1600, (maxTexturePx / safeDensity).toInt()).coerceAtLeast(480)
    val isCappedScrollable = rawContentHeightDp > gpuSafeMaxDp
    val displayHeightDp = rawContentHeightDp.coerceIn(48, gpuSafeMaxDp)
    return displayHeightDp to isCappedScrollable
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun MessageHtmlBlock(
    html: String,
    messageKey: String? = null,
    modifier: Modifier = Modifier,
    panelTitle: String = "预设渲染面板",
    unboundedHeight: Boolean = true,
    isHistoricalFloor: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val displaySetting = LocalSettings.current.displaySetting
    val perfEnabled = displaySetting.enableWebViewPerfReduction
    val perfMode = displaySetting.webViewPerfMode
    LaunchedEffect(
        displaySetting.enableWebViewOffscreenPause,
        displaySetting.enableWebViewScrollDefer,
        displaySetting.enableWebViewImageDownsample,
    ) {
        DetachedPanelWebViewCache.offscreenPauseEnabled = displaySetting.enableWebViewOffscreenPause
        TavernPanelLoadScheduler.scrollDeferEnabled = displaySetting.enableWebViewScrollDefer
        TavernResourceRoute.downsampleLargeImages = displaySetting.enableWebViewImageDownsample
    }
    val useBoundedWebViewPool = true
    val useHibernatePool = perfEnabled && perfMode == WebViewPerfMode.HIBERNATE_POOL
    val useLiveQuiescent = !perfEnabled || perfMode == WebViewPerfMode.LIVE_QUIESCENT
    val useHistoricalSnapshot = perfEnabled && perfMode == WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE
    val totalMemBytes = remember(context) { ActivePanelWebViewPool.detectDeviceTotalMemBytes(context) }
    val maxActivePoolSize = remember(totalMemBytes, perfEnabled, perfMode, displaySetting.webViewMaxActiveOverride) {
        ActivePanelWebViewPool.resolveEffectiveMaxActive(
            totalMemBytes = totalMemBytes,
            perfEnabled = perfEnabled,
            perfMode = perfMode,
            userOverride = displaySetting.webViewMaxActiveOverride,
        )
    }
    // 离屏保活上限跟随内存分级：至少 3，最多 6，且不超过活跃池本身。
    // 否则滑出屏幕的面板会被立刻挤出，滚回来只能看到"已自动休眠"。
    LaunchedEffect(maxActivePoolSize) {
        DetachedPanelWebViewCache.dynamicMaxDetached =
            (maxActivePoolSize / 2).coerceIn(3, 6).coerceAtMost(maxActivePoolSize)
    }
    val basePoolPriority = if (!isHistoricalFloor) {
        PanelPoolPriority.LATEST_FLOOR
    } else {
        PanelPoolPriority.VISIBLE
    }
    val screenHeightDp = configuration.screenHeightDp.coerceAtLeast(480)
    val minHeightDp = 48
    // [v251] 统一走 panelMaxHeightDp()：与 computePanelHeightConstraint 共享同一份纹理预算，
    // 杜绝「两处各写一个 4096」这种迟早会漂移的重复定义。
    val gpuSafeMaxDp = panelMaxHeightDp(density.density)
    val safetyMaxHeightDp = if (unboundedHeight) {
        gpuSafeMaxDp
    } else {
        minOf(screenHeightDp * 2, gpuSafeMaxDp).coerceAtLeast(480)
    }

    val rendererGen by TavernRuntimeManager.rendererGeneration.collectAsState()
    var localCrashCount by remember(messageKey) { mutableIntStateOf(0) }
    val contentHash = remember(html) { html.hashCode() }
    val panelClass = remember(contentHash) { PanelClassificationCache.getOrClassify(html) }
    val staticPlainText = remember(contentHash, panelClass) {
        if (panelClass == PanelClass.STATIC) extractStaticPanelPlainText(html) else ""
    }
    val effectiveQueueKey = remember(messageKey, contentHash, localCrashCount) {
        "${messageKey ?: "anon"}#$contentHash#$localCrashCount"
    }

    var heightDp by remember(messageKey, contentHash) {
        val cached = PanelHeightCache.get(messageKey, contentHash)
        mutableIntStateOf(cached)
    }
    val isPanelTouchingRef = remember(effectiveQueueKey) { AtomicBoolean(false) }
    val deferredTouchHeightRef = remember(effectiveQueueKey) { java.util.concurrent.atomic.AtomicInteger(-1) }
    // B 方案（最后手段）：BIG 类面板（角色卡大面板等）强制使用「固定视口高度」，
    // 让 WebView 内部滚动成为一个独立滚动区，彻底绕开与外层 LazyColumn 的嵌套冲突。
    // 依据：真机反复验证 requestDisallowIntercept / nestedScroll / pointerInteropFilter
    // 都挡不住 LazyColumn 抢事件（gesture-probe 显示大量 ACTION_CANCEL）。
    // 固定高度后，面板高度不再随内容/滚动变化，外层列表也不需要跟随，
    // 用户手感上就是「面板内部独立滚动」。
    val forceFixedViewport = panelClass == PanelClass.BIG

    val (computedHeightDp, computedScrollable) = computePanelHeightConstraint(
        rawContentHeightDp = heightDp,
        density = density.density,
    )
    val isCappedScrollable = computedScrollable || heightDp > safetyMaxHeightDp
    val boundedHeightDp = computedHeightDp.coerceIn(minHeightDp, safetyMaxHeightDp)

    // 组合刚创建时离屏池里可能还没有这个面板（上一份组合还没 park），这时先拿到一个空的占位实例。
    // 等 factory 真正取回缓存容器时，把引用改成"容器所属的那一份"（见 factory 缓存命中分支），
    // 保证 UI 观察的状态和 JS 桥写入的状态是同一个对象。
    var mountStateRef by remember(effectiveQueueKey) {
        mutableStateOf(
            DetachedPanelWebViewCache.getMountState(effectiveQueueKey) ?: PanelMountState()
        )
    }
    val mountState = mountStateRef
    val initialScrolling = TavernPanelLoadScheduler.isListScrollingFlow.value &&
        displaySetting.enableWebViewScrollDefer &&
        !mountState.mounted
    val isListScrolling by remember(mountState, displaySetting.enableWebViewScrollDefer) {
        TavernPanelLoadScheduler.isListScrollingFlow
            .map { scrolling -> scrolling && displaySetting.enableWebViewScrollDefer && !mountState.mounted }
            .distinctUntilChanged()
    }.collectAsState(initial = initialScrolling)
    // 把"当前存活组合"的 heightDp 挂到 mountState 上。
    // JS 桥(PanelBridge)是 WebView 创建时就绑定好的；容器被别的组合复用后，
    // 它仍然只认 mountState。这里让它总是写到最新那份组合的 heightDp，
    // 而不是创建它的那份已经销毁的组合。
    DisposableEffect(mountState) {
        val sink: (Int) -> Unit = { h -> heightDp = h }
        mountState.heightSink = sink
        onDispose {
            if (mountState.heightSink === sink) mountState.heightSink = null
        }
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val foregroundHex = remember(onSurfaceColor) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    }

    // 串行分步加载队列状态：若 DetachedPanelWebViewCache 已有存活实例，则直接放行（0ms 恢复，不重新排队渲染）
    var slotGranted by remember(effectiveQueueKey) {
        mutableStateOf(DetachedPanelWebViewCache.has(effectiveQueueKey))
    }
    val queueProgress by remember(effectiveQueueKey, slotGranted) {
        TavernPanelLoadScheduler.queueSnapshot
            .map { keys ->
                if (slotGranted) {
                    0 to 0
                } else {
                    val pos = (keys.indexOf(effectiveQueueKey) + 1).coerceAtLeast(1)
                    val total = keys.size.coerceAtLeast(pos)
                    pos to total
                }
            }
            .distinctUntilChanged()
    }.collectAsState(initial = 0 to 0)
    var stage by remember(effectiveQueueKey) {
        mutableStateOf<PanelLoadStage>(
            if (DetachedPanelWebViewCache.has(effectiveQueueKey)) {
                PanelLoadStage.ResourcesLoading(panelTitle)
            } else {
                PanelLoadStage.Queued(panelTitle, 1, 1)
            }
        )
    }

    LaunchedEffect(queueProgress, slotGranted, panelTitle) {
        if (!slotGranted) {
            val (pos, total) = queueProgress
            val safePos = pos.coerceAtLeast(1)
            val safeTotal = total.coerceAtLeast(safePos)
            stage = if (!TavernRuntimeManager.isPageReady && safePos == 1) {
                PanelLoadStage.WaitingHost(panelTitle)
            } else {
                PanelLoadStage.Queued(panelTitle, safePos, safeTotal)
            }
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerWidthPx = (configuration.screenWidthDp * density.density).toInt().coerceAtLeast(1)
    val effectiveFontScale = (density.fontScale * displaySetting.fontSizeRatio).coerceIn(0.5f, 3.0f)
    val snapshotCompositeKey = remember(messageKey, contentHash, isDarkTheme, containerWidthPx, effectiveFontScale) {
        PanelSnapshotStore.compositeKey(
            messageKey = messageKey,
            contentHash = contentHash,
            isDarkTheme = isDarkTheme,
            widthPx = containerWidthPx,
            fontScale = effectiveFontScale,
        )
    }
    var snapshotEntry by remember(snapshotCompositeKey) {
        mutableStateOf(PanelSnapshotStore.getMemory(snapshotCompositeKey))
    }

    // ---- 内容级快照缓存（跨楼层共享） ----
    // 同一段 HTML 在多楼出现时（典型：发送按钮面板、状态栏），外观完全一致，
    // 只需渲染一次。这里按「内容 + 主题 + 宽度 + 字号」寻址，key 里不含 messageKey。
    val contentSnapshotKey = remember(contentHash, isDarkTheme, containerWidthPx, effectiveFontScale) {
        PanelSnapshotStore.contentKey(
            contentHash = contentHash,
            isDarkTheme = isDarkTheme,
            widthPx = containerWidthPx,
            fontScale = effectiveFontScale,
        )
    }
    var contentSnapshot by remember(contentSnapshotKey) {
        mutableStateOf(PanelSnapshotStore.getMemory(contentSnapshotKey))
    }
    LaunchedEffect(contentSnapshotKey) {
        if (contentSnapshot == null) {
            contentSnapshot = PanelSnapshotStore.loadFromDiskIfNeeded(
                context = context,
                compositeKey = contentSnapshotKey,
                fallbackHeightDp = 0,
                density = density.density,
            )
        }
    }

    var userWokenUp by remember(effectiveQueueKey) { mutableStateOf(false) }
    var pendingWakeClickCss by remember(effectiveQueueKey) { mutableStateOf<Pair<Float, Float>?>(null) }
    var wakeStartedAtMs by remember(effectiveQueueKey) { mutableLongStateOf(0L) }
    // 阶段 4 修复：唤醒时优先挂载「脚本执行后的真实 DOM 快照」(state_<key>.html)。
    // 原先只把 exportedHtml 存盘从不回灌，导致脚本驱动面板（角色卡大面板 / MVU 状态栏）
    // 重新渲染后回到初始态，再用裸 index 补水必然错位 -> 冻结状态等于白存。
    var frozenExportHtml by remember(effectiveQueueKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(userWokenUp, snapshotCompositeKey) {
        if (!userWokenUp || snapshotCompositeKey == null || frozenExportHtml != null) return@LaunchedEffect
        val frozen = PanelFrozenStateStore.loadIfNeeded(context, snapshotCompositeKey)
        if (frozen != null && frozen.exportedHtml.isNotBlank()) {
            frozenExportHtml = frozen.exportedHtml
            TavernRuntimeManager.appendLog(
                "info",
                "[panel-wake] loaded frozen dom key=$effectiveQueueKey htmlLen=${frozen.exportedHtml.length}",
            )
        } else {
            TavernRuntimeManager.appendLog(
                "info",
                "[panel-wake] no frozen dom, fallback to re-render key=$effectiveQueueKey",
            )
        }
    }
    val isInteractiveClass = panelClass == PanelClass.LIGHT || panelClass == PanelClass.SEND || panelClass == PanelClass.BIG
    val canFreezeInteractiveHistorical = isInteractiveClass && isHistoricalFloor && !userWokenUp && !useHistoricalSnapshot

    // 内容快照命中：同一段 HTML 之前渲染过（任意楼层），外观可直接复用。
    // 但用户主动点开过（userWokenUp）或当前楼层需要现场动画时不走快照。
    // 额外安全条件：快照必须是「有像素 + 比例合法」的图，否则宁可现场渲染，
    // 避免出现「命中一张坏图 -> 整块面板空白」的回归。
    // 暂时关闭「内容快照直接顶替渲染」的快速路径。
    // 原因：真机验证发现内容级快照有可能捕获到未完成渲染的空图，
    // 直接顶替会出现整块面板空白（比慢加载更糟）。
    // 现只保留「捕获 + 落盘」能力（用于后续做正确的就绪判定后再启用）。
    val contentSnapshotUsable = false && contentSnapshot != null && !userWokenUp

    // 关键：任何「用快照顶替渲染」的路径都必须要求快照 ready=true。
    // ready 只在 renderReady 之后捕获才为真；磁盘读回的旧图一律视为未就绪，
    // 因此「每个面板必须先真实渲染一次，才有资格被快照复用」。
    // 快照可用性 = 快照标记 ready（捕获时已渲染完成）
    //               且 该内容已被登记为「至少真实渲染成功过一次」。
    // 后者是用户提出的兜底规则：避免任何异常路径下用空白图顶替渲染。
    val renderedOnceVersion by PanelSnapshotStore.renderedOnceVersion.collectAsState()
    val contentRenderedOnce = remember(contentSnapshotKey, renderedOnceVersion) {
        PanelSnapshotStore.isRenderedOnce(contentSnapshotKey, context)
    }
    val hasUsableSnapshot = contentRenderedOnce &&
        (snapshotEntry?.ready == true || contentSnapshot?.ready == true)

    val shouldPreferSnapshot = hasUsableSnapshot && (
        contentSnapshotUsable ||
            (panelClass == PanelClass.STATIC) ||
            (panelClass == PanelClass.ANIMATED && isHistoricalFloor && !userWokenUp) ||
            (useHistoricalSnapshot && isHistoricalFloor) ||
            canFreezeInteractiveHistorical ||
            (displaySetting.enableWebViewScrollDefer && isListScrolling && !mountState.mounted)
        )

    LaunchedEffect(snapshotCompositeKey, shouldPreferSnapshot) {
        if (shouldPreferSnapshot && snapshotEntry == null && snapshotCompositeKey != null) {
            val loaded = PanelSnapshotStore.loadFromDiskIfNeeded(
                context = context,
                compositeKey = snapshotCompositeKey,
                fallbackHeightDp = heightDp,
                density = density.density,
            )
            if (loaded != null) {
                snapshotEntry = loaded
                if (heightDp <= 0 && loaded.heightDp > 0) {
                    heightDp = loaded.heightDp
                }
            }
        }
    }

    // 静态面板（STATIC）、历史动态展示（ANIMATED）、已冻结的历史交互面板或方案三历史楼层：
    // 命中快照直接显示同宽高图片（高度不跳），交互类面板支持点击坐标唤醒并补发点击。
    val preferEntry = (contentSnapshot?.takeIf { it.ready }) ?: snapshotEntry?.takeIf { it.ready }
    if (shouldPreferSnapshot && preferEntry != null) {
        val snap = preferEntry
        val safeAspectRatio = snap.aspectRatio.coerceIn(0.05f, 20f)
        val allowTapWake = canFreezeInteractiveHistorical || (panelClass == PanelClass.ANIMATED && isHistoricalFloor)
        Image(
            bitmap = snap.imageBitmap,
            contentDescription = if (staticPlainText.isNotEmpty()) staticPlainText else panelTitle,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(safeAspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (allowTapWake) {
                        Modifier.pointerInput(snapshotCompositeKey, effectiveQueueKey) {
                            detectTapGestures { offset ->
                                val xCss = offset.x / density.density.coerceAtLeast(1f)
                                val yCss = offset.y / density.density.coerceAtLeast(1f)
                                wakeStartedAtMs = android.os.SystemClock.uptimeMillis()
                                pendingWakeClickCss = xCss to yCss
                                userWokenUp = true
                                ActivePanelWebViewPool.acquire(
                                    key = effectiveQueueKey,
                                    maxActive = maxActivePoolSize,
                                    priority = PanelPoolPriority.INTERACTING,
                                )
                                TavernPanelLoadScheduler.enqueue(effectiveQueueKey, panelTitle, prioritize = true)
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        )
        return
    }

    val isInActivePool by remember(effectiveQueueKey) {
        ActivePanelWebViewPool.activeKeysFlow
            .map { effectiveQueueKey in it }
            .distinctUntilChanged()
    }.collectAsState(initial = effectiveQueueKey in ActivePanelWebViewPool.activeKeysFlow.value)

    LaunchedEffect(effectiveQueueKey, useBoundedWebViewPool, maxActivePoolSize) {
        if (!slotGranted) {
            val req = TavernPanelLoadScheduler.enqueue(
                effectiveQueueKey,
                panelTitle,
                prioritize = false,
                // 角色卡大面板更容易在首帧耗时较长，给它更宽松的槽位超时。
                heavy = panelClass == PanelClass.BIG,
            )
            req.gate.await()
            // 被队列挤掉的请求：给一小段退避后重排，而不是立刻假装"已拿到槽位"，
            // 否则会停在"正在排队加载"的死状态（真机已复现）。
            while (req.dropped && !mountState.isDisposed) {
                req.dropped = false
                delay(180L)
                val retry = TavernPanelLoadScheduler.enqueue(
                    effectiveQueueKey,
                    panelTitle,
                    prioritize = false,
                    heavy = panelClass == PanelClass.BIG,
                )
                retry.gate.await()
                if (!retry.dropped) break
                req.dropped = retry.dropped
            }
        }
        kotlinx.coroutines.currentCoroutineContext().let { ctx ->
            if (!ctx[Job]?.isActive.let { it ?: true } || mountState.isDisposed) return@LaunchedEffect
        }
        if (useBoundedWebViewPool) {
            ActivePanelWebViewPool.acquire(
                key = effectiveQueueKey,
                maxActive = maxActivePoolSize,
                priority = basePoolPriority,
            )
        }
        slotGranted = true
        if (!mountState.mounted) {
            stage = PanelLoadStage.LoadingWebView(panelTitle)
        } else {
            stage = if (mountState.resourcesSettled || mountState.renderReady) {
                PanelLoadStage.Ready
            } else {
                PanelLoadStage.ResourcesLoading(panelTitle)
            }
        }
    }

    DisposableEffect(effectiveQueueKey, useBoundedWebViewPool) {
        onDispose {
            TavernPanelLoadScheduler.cancel(effectiveQueueKey)
            if (useBoundedWebViewPool) {
                ActivePanelWebViewPool.release(effectiveQueueKey)
            }
        }
    }

    LaunchedEffect(rendererGen) {
        if (rendererGen > 0) {
            mountState.onHostRendererRestarted()
        }
    }

    LaunchedEffect(isCappedScrollable, mountState.mounted) {
        if (mountState.mounted) {
            mountState.setInternalScrollEnabled(isCappedScrollable)
        }
    }

    LaunchedEffect(displaySetting.enableWebViewGpuReduction, mountState.mounted) {
        if (mountState.mounted && !mountState.isDisposed) {
            val enabled = displaySetting.enableWebViewGpuReduction
            runCatching {
                mountState.webView?.evaluateJavascript(
                    "try{if(window.__rikkaSetGpuReduction)window.__rikkaSetGpuReduction($enabled);}catch(e){}",
                    null,
                )
            }
        }
    }

    // 仅对真正拿到渲染槽位的面板预取外链图片，离屏/排队面板不抢网络。
    LaunchedEffect(slotGranted, contentHash) {
        if (slotGranted) {
            TavernResourceRoute.prefetchImages(context, html)
        }
    }

    // renderReady 一旦为真，立刻把 stage 推到 Ready —— 这是最可靠的"渲染完成"信号。
    // 之前依赖 resources_settled 回调，回调丢失时 UI 会永远停在"正在加载"。
    LaunchedEffect(mountState.renderReady) {
        if (mountState.renderReady && stage !is PanelLoadStage.Ready) {
            stage = PanelLoadStage.Ready
        }
    }

    // 高度抖动抑制：真机观察到同一面板在 90/92dp 之间反复跳变，
    // 每次跳变都会触发重组 -> stage 重算 -> UI 闪烁（"一直转"的直接成因）。
    // 这里把 2dp 以内的抖动吸收掉，只有真实变化才更新。
    LaunchedEffect(mountState.mounted, mountState.resourcesSettled, mountState.resourcesTimedOut, heightDp) {
        if (mountState.mounted && heightDp > 0) {
            stage = if (mountState.resourcesSettled || mountState.renderReady) {
                PanelLoadStage.Ready
            } else {
                PanelLoadStage.ResourcesLoading(panelTitle)
            }
        }
    }

    // JS 侧 settle 回调缺失（旧页面/脚本异常）时的宿主兜底：4 秒后明确标成“仍有资源在加载”，
    // 不允许一个小转圈永久挂在面板底部。
    LaunchedEffect(mountState.mounted, mountState.resourcesSettled, heightDp) {
        if (mountState.mounted && heightDp > 0 && !mountState.resourcesSettled) {
            delay(4_000)
            if (!mountState.resourcesSettled) {
                mountState.markResourcesSettled(timedOut = true)
            }
        }
    }

    LaunchedEffect(slotGranted, messageKey, contentHash, localCrashCount) {
        if (!slotGranted) return@LaunchedEffect
        delay(5000)
        if (!mountState.isDisposed && (!mountState.mounted || heightDp <= 0)) {
            val retried = mountState.onWatchdogTimeout()
            if (retried) {
                TavernRuntimeManager.appendLog(
                    "warn",
                    "[panel] watchdog retry key=$messageKey mounted=${mountState.mounted} heightDp=$heightDp",
                )
            }
        }
    }

    if (localCrashCount > MAX_PANEL_CRASH_REBUILDS) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "面板「$panelTitle」渲染器连续异常退出（已自动重试 $MAX_PANEL_CRASH_REBUILDS 次）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { localCrashCount = 0 }) {
                Text("手动重新加载面板")
            }
        }
        return
    }

    // 活跃池之外的面板一律休眠（不仅历史楼层），确保“活跃 WebView 数量上限”是真实上限。
    val isHibernated = useBoundedWebViewPool && slotGranted && !isInActivePool
    val isPanelVisiblyReady = slotGranted && !isHibernated && mountState.mounted && heightDp > 0

    // 面板完成渲染后：
    // 1. 始终在后台预捕获一张等比例高清快照存入 PanelSnapshotStore（无论当前处于方案一、二还是三），
    //    这样用户在偏好设置里切换到方案三时直接秒切静态图，完全无需重新渲染 WebView！
    // 2. 若开启方案二（全视觉保活+静默降频），自动静默后台轮询定时器。
    // 阶段 4：唤醒就绪后先复水保存的 DOM 状态（防止脚本重跑重置状态），再补发点击坐标并记录唤醒耗时
    LaunchedEffect(mountState.renderReady, userWokenUp, snapshotCompositeKey) {
        if (!mountState.renderReady || mountState.isDisposed) return@LaunchedEffect
        val wv = mountState.webView ?: return@LaunchedEffect
        val frozen = PanelFrozenStateStore.loadIfNeeded(context, snapshotCompositeKey)
        if (frozen != null && frozen.stateJson.isNotEmpty()) {
            val quoted = JSONObject.quote(frozen.stateJson)
            runCatching {
                wv.evaluateJavascript(
                    "try{if(window.__rikkaHydrateState)window.__rikkaHydrateState($quoted);}catch(e){}",
                    null,
                )
            }
        }
        val tapCoords = pendingWakeClickCss
        if (tapCoords != null) {
            pendingWakeClickCss = null
            val (xCss, yCss) = tapCoords
            val elapsedMs = if (wakeStartedAtMs > 0L) {
                (android.os.SystemClock.uptimeMillis() - wakeStartedAtMs).coerceAtLeast(0L)
            } else 0L
            // renderReady 后脚本可能仍在跑（尤其先 hydrate 再重排的面板），单次点击可能落空。
            // 最多重试 3 次（间隔 120ms），命中即停；全部落空明确记 warn，不静默吞掉。
            var clickHit = false
            for (attempt in 0 until 3) {
                if (attempt > 0) delay(120L)
                if (mountState.isDisposed) break
                val hit = suspendCoroutine<Boolean> { cont ->
                    val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                    runCatching {
                        wv.evaluateJavascript(
                            "try{String(!!(window.__rikkaReplayClick&&window.__rikkaReplayClick($xCss,$yCss)))}catch(e){\"false\"}",
                        ) { result ->
                            if (resumed.compareAndSet(false, true)) {
                                cont.resume(result?.contains("true") == true)
                            }
                        }
                    }.onFailure {
                        if (resumed.compareAndSet(false, true)) {
                            cont.resume(false)
                        }
                    }
                }
                if (hit) {
                    clickHit = true
                    break
                }
            }
            TavernRuntimeManager.appendLog(
                if (clickHit) "info" else "warn",
                "[panel-wake] key=$effectiveQueueKey class=$panelClass elapsedMs=$elapsedMs " +
                    "x=${xCss.toInt()} y=${yCss.toInt()} clickHit=$clickHit frozenDom=${frozenExportHtml != null}",
            )
        }
    }

    LaunchedEffect(isPanelVisiblyReady, heightDp, mountState.resourcesSettled, mountState.renderReady, useLiveQuiescent, useHistoricalSnapshot, isHistoricalFloor) {
        if (!isPanelVisiblyReady || heightDp <= 0) return@LaunchedEffect
        delay(420L)
        if (!mountState.isDisposed && mountState.mounted) {
            // 只认 renderReady（JS 明确报告「布局与资源都稳定了」）。
            // 旧逻辑在 resourcesTimedOut 时也放行捕获 —— 真机上那正是空白帧的时段，
            // 结果把空白图缓存下来并在下次顶替渲染，造成整块面板空白。
            val readyForSnapshot = mountState.renderReady
            // 捕获「内容级快照」（跨楼层共享）。注意：必须等 readyForSnapshot，
            // 否则会抓到未完成渲染的空图 —— 真机上已因此出现过整块面板空白。
            if (readyForSnapshot && snapshotEntry == null && snapshotCompositeKey != null) {
                // 若面板在滑动中就绪或停滑 220ms 内再次起滑，循环等待真正静止后再捕获，避免永久漏抓快照
                while (!mountState.isDisposed && mountState.mounted && TavernPanelLoadScheduler.isListScrollingFlow.value) {
                    TavernPanelLoadScheduler.isListScrollingFlow.first { !it }
                    delay(220L)
                }
                if (!mountState.isDisposed && mountState.mounted && !TavernPanelLoadScheduler.isListScrollingFlow.value) {
                    val captured = PanelSnapshotStore.captureFromWebView(
                        context = context,
                        compositeKey = snapshotCompositeKey,
                        webView = mountState.webView,
                        heightDp = boundedHeightDp,
                        // 只有 renderReady 之后捕获的帧才可信；否则标记未就绪，
                        // 该快照不会被用来顶替渲染（避免把空白图固化下来）。
                        renderReadyAtCapture = mountState.renderReady,
                        contentKey = contentSnapshotKey,
                    )
                    if (captured != null) {
                        snapshotEntry = captured
                        // 同时写入**内容级** key：同内容的其它楼层可立即复用，
                        // 磁盘文件已由 captureFromWebView 复用同一份 Bitmap 异步写入，避免二次拷贝与二次 PNG 压缩。
                        contentSnapshot = captured
                        PanelSnapshotStore.putContentEntry(contentSnapshotKey, captured, context, writeDisk = false)
                        // 登记「已成功真实渲染一次」，之后该内容的快照才允许复用。
                        PanelSnapshotStore.markRenderedOnce(contentSnapshotKey, context)
                    // 捕获后**不主动释放** WebView。
                    // 真机反复验证：一旦释放，滚回来时该面板已不在活跃池，会退化成
                    //「面板过多已自动休眠/唤醒面板」占位卡（用户明确反馈这是最糟的观感）。
                        // 让淘汰完全交给池子的优先级 + LRU 自然处理即可。
                    }
                }
            }
            if (useLiveQuiescent && (perfEnabled || isHistoricalFloor)) {
                delay(280L)
                runCatching {
                    mountState.webView?.evaluateJavascript(
                        "try{if(window.__rikkaQuiescePanel)window.__rikkaQuiescePanel();}catch(e){}",
                        null,
                    )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isHibernated) {
            // 休眠时优先显示已缓存的外观图（有就显示，没有才用占位卡片），
            // 这样用户在长列表里滚动时看到的仍然是真实面板外观，而不是一堆提示卡。
            val hibernatedSnapshot = (contentSnapshot?.takeIf { it.ready }) ?: snapshotEntry?.takeIf { it.ready }
            if (hibernatedSnapshot != null) {
                val safeAspect = hibernatedSnapshot.aspectRatio.coerceIn(0.05f, 20f)
                Image(
                    bitmap = hibernatedSnapshot.imageBitmap,
                    contentDescription = panelTitle,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(safeAspect)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(effectiveQueueKey) {
                            detectTapGestures {
                                stage = PanelLoadStage.LoadingWebView(panelTitle)
                                ActivePanelWebViewPool.acquire(
                                    key = effectiveQueueKey,
                                    maxActive = maxActivePoolSize,
                                    priority = PanelPoolPriority.INTERACTING,
                                )
                            }
                        },
                )
            } else {
                PanelHibernatedPlaceholderCard(
                    panelTitle = panelTitle,
                    onWakeUp = {
                        stage = PanelLoadStage.LoadingWebView(panelTitle)
                        ActivePanelWebViewPool.acquire(
                            key = effectiveQueueKey,
                            maxActive = maxActivePoolSize,
                            priority = PanelPoolPriority.INTERACTING,
                        )
                    },
                )
            }
        } else if (!isPanelVisiblyReady && !mountState.renderReady && (!slotGranted || !mountState.mounted)) {
            PanelLoadingPlaceholderCard(
                stage = stage,
                panelTitle = panelTitle,
                errorText = mountState.lastError,
                onPrioritize = {
                    TavernPanelLoadScheduler.enqueue(effectiveQueueKey, panelTitle, prioritize = true)
                },
                onReload = {
                    PanelHeightCache.invalidateForMessage(messageKey)
                    DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)
                    mountState.reloadNow()
                },
            )
        }

        if (slotGranted && !isHibernated) {
            key(effectiveQueueKey) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPanelVisiblyReady) boundedHeightDp.dp else 0.dp),
                    factory = { ctx ->
                        TavernRuntimeManager.ensureContext(ctx)
                        TavernRuntimeManager.ensureStarted(ctx)
                        // 优先从离屏保活池取回已渲染好的容器，杜绝切换页面或切换方案时重新创建 WebView
                        val cachedEntry = DetachedPanelWebViewCache.takeContainer(effectiveQueueKey)
                        if (cachedEntry != null) {
                            // ① 先认领：容器里的 PanelBridge 绑定的是 cachedEntry.mountState，
                            //    组合必须观察同一个实例，否则永远看不到 mounted / renderReady。
                            if (mountStateRef !== cachedEntry.mountState) {
                                TavernRuntimeManager.appendLog(
                                    "info",
                                    "[panel] adopt cached mountState key=$effectiveQueueKey " +
                                        "composed=${System.identityHashCode(mountStateRef)} " +
                                        "cached=${System.identityHashCode(cachedEntry.mountState)}",
                                )
                                mountStateRef = cachedEntry.mountState
                            }
                            val frozenForCached = frozenExportHtml
                            if (frozenForCached != null) {
                                cachedEntry.mountState.submitFrozen(frozenForCached, foregroundHex, messageKey, screenHeightDp)
                            } else {
                                cachedEntry.mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                            }
                            // ② 高度和阶段无条件同步。原来整段包在 if (cachedHeight > 0) 里，
                            //    高度缓存没命中时什么都不同步，面板高度会一直是 0。
                            val cachedHeight = maxOf(
                                PanelHeightCache.get(messageKey, contentHash),
                                cachedEntry.mountState.lastReportedHeightDp,
                            )
                            if (cachedHeight > 0) {
                                heightDp = cachedHeight
                            }
                            // 缓存高度只能用于占位，必须重新等本轮 resources_settled；
                            // 否则缓存复挂载会瞬间显示“加载完成”，实际图片还没回来。
                            stage = if (cachedEntry.mountState.resourcesSettled) {
                                PanelLoadStage.Ready
                            } else {
                                PanelLoadStage.ResourcesLoading(panelTitle)
                            }
                            TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                            return@AndroidView cachedEntry.container
                        }

                        val rendererGoneHandled = AtomicBoolean(false)
                        val webView = WebView(ctx).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.blockNetworkImage = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            isNestedScrollingEnabled = false
                            overScrollMode = View.OVER_SCROLL_NEVER
                            isVerticalScrollBarEnabled = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            var firstInteractionLogged = false
                            setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_DOWN) {
                                    ActivePanelWebViewPool.updatePriority(effectiveQueueKey, PanelPoolPriority.INTERACTING)
                                    ActivePanelWebViewPool.touch(effectiveQueueKey)
                                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                    ActivePanelWebViewPool.updatePriority(effectiveQueueKey, basePoolPriority)
                                }
                                if (event.action == MotionEvent.ACTION_DOWN) {
                                    if (!firstInteractionLogged) {
                                        firstInteractionLogged = true
                                        TavernRuntimeManager.recordWebViewBootStage(
                                            hostType = "panel",
                                            hostId = messageKey ?: "anon",
                                            stage = "first_interaction",
                                            detail = "action=ACTION_DOWN",
                                        )
                                    }
                                }
                                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                                    val wv = v as? WebView
                                    val hitType = runCatching { wv?.hitTestResult?.type }.getOrNull()
                                        ?: WebView.HitTestResult.UNKNOWN_TYPE
                                    val needsImeFocus = hitType == WebView.HitTestResult.EDIT_TEXT_TYPE
                                    if (needsImeFocus) {
                                        if (!v.isFocusable || !v.isFocusableInTouchMode) {
                                            v.isFocusable = true
                                            v.isFocusableInTouchMode = true
                                        }
                                        if (event.action == MotionEvent.ACTION_UP) {
                                            v.requestFocus()
                                        }
                                    } else if (event.action == MotionEvent.ACTION_DOWN && !v.hasFocus()) {
                                        v.isFocusable = false
                                        v.isFocusableInTouchMode = false
                                    }
                                }
                                false
                            }
                            webChromeClient = RikkaWebChromeClient(
                                context = ctx,
                                sourceTag = "panel:${messageKey ?: "anon"}",
                                onConsoleError = { errMsg ->
                                    mountState.lastError = errMsg
                                },
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val targetUrl = request.url?.toString()?.trim().orEmpty()
                                    if (targetUrl.startsWith("https://appassets.androidplatform.net", ignoreCase = true) ||
                                        targetUrl.equals("about:blank", ignoreCase = true)
                                    ) {
                                        return false
                                    }
                                    if (request.isForMainFrame &&
                                        (targetUrl.startsWith("http://", ignoreCase = true) || targetUrl.startsWith("https://", ignoreCase = true)) &&
                                        request.hasGesture()
                                    ) {
                                        runCatching {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                request.url,
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            ctx.startActivity(intent)
                                        }
                                        return true
                                    }
                                    TavernRuntimeManager.appendLog("warn", "[panel] blocked top-level navigation to $targetUrl")
                                    return true
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    return TavernRuntimeManager.interceptAsset(request.url, request.requestHeaders)
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (mountState.isDisposed || rendererGoneHandled.get()) return
                                    stage = PanelLoadStage.MountingDom(panelTitle)
                                    TavernRuntimeManager.recordWebViewBootStage(
                                        hostType = "panel",
                                        hostId = messageKey ?: "anon",
                                        stage = "dom_content_loaded",
                                        detail = "url=$url (onPageFinished != runtime_ready)",
                                    )
                                    view.evaluateJavascript("typeof window.__rikkaPanelMount") { r ->
                                        if (mountState.isDisposed || rendererGoneHandled.get()) return@evaluateJavascript
                                        val ok = r != null && r.contains("function")
                                        if (ok) {
                                            TavernRuntimeManager.recordWebViewBootStage(
                                                hostType = "panel",
                                                hostId = messageKey ?: "anon",
                                                stage = "runtime_ready",
                                                detail = "url=$url",
                                            )
                                            mountState.onPageReady()
                                            mountState.tryMountIfNeeded()
                                        } else {
                                            // ES module script may still be linking asynchronously after onPageFinished;
                                            // re-probe once after 220ms if onPanelRuntimeReady() has not fired yet.
                                            mountState.scheduleReadyProbe(220L) {
                                                if (mountState.isDisposed || rendererGoneHandled.get() || mountState.mounted) return@scheduleReadyProbe
                                                view.evaluateJavascript("typeof window.__rikkaPanelMount") { r2 ->
                                                    if (mountState.isDisposed || rendererGoneHandled.get() || mountState.mounted) return@evaluateJavascript
                                                    val ok2 = r2 != null && r2.contains("function")
                                                    if (ok2) {
                                                        mountState.onPageReady()
                                                        mountState.tryMountIfNeeded()
                                                    } else {
                                                        TavernRuntimeManager.appendLog(
                                                            "warn",
                                                            "[panel] page finished but runtime missing (typeof=$r2) key=$messageKey url=$url — module link error or asset miss",
                                                        )
                                                        mountState.onRuntimeMissing()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail?,
                                ): Boolean {
                                    if (!rendererGoneHandled.compareAndSet(false, true)) {
                                        return true
                                    }
                                    val wasDisposed = mountState.isDisposed
                                    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.didCrash() == true
                                    } else false
                                    val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.rendererPriorityAtExit() ?: -1
                                    } else -1
                                    ActivePanelWebViewPool.recordRenderProcessGone(messageKey, didCrash, priority)
                                    DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)
                                    mountState.onRendererGone()
                                    TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                                    TavernRuntimeManager.onPanelRendererGone(view, messageKey, didCrash, priority)
                                    runCatching {
                                        (view.parent as? ViewGroup)?.removeView(view)
                                        view.destroy()
                                    }
                                    if (!wasDisposed) {
                                        handler.post {
                                            if (!mountState.isDisposed) {
                                                localCrashCount += 1
                                            }
                                        }
                                    }
                                    return true
                                }
                            }
                            addJavascriptInterface(
                                PanelBridge(
                                    handler = handler,
                                    mountState = mountState,
                                    onHeight = { h ->
                                        if (mountState.isDisposed || rendererGoneHandled.get()) return@PanelBridge
                                        val currentKey = mountState.currentPendingKey().ifEmpty { messageKey.orEmpty() }
                                        val currentHash = mountState.currentPendingHtmlHash()
                                        if (mountState.mounted && h >= minHeightDp) {
                                            PanelHeightCache.put(currentKey, currentHash, h)
                                        }
                                        val physicalPx = (h * density.density).toInt()
                                        if (this.layerType != View.LAYER_TYPE_NONE) {
                                            this.setLayerType(View.LAYER_TYPE_NONE, null)
                                        }
                                        if (physicalPx > 4096 || h >= safetyMaxHeightDp) {
                                            TavernRuntimeManager.appendLog(
                                                "warn",
                                                "[panel] height capped for internal scroll key=$currentKey h=${h}dp bounded=${h.coerceIn(minHeightDp, safetyMaxHeightDp)}dp " +
                                                    "screenHeightDp=$screenHeightDp safetyMaxHeightDp=$safetyMaxHeightDp physicalPx=${physicalPx}px",
                                            )
                                        }
                                        if (mountState.panelTouching) {
                                            mountState.deferredTouchHeightDp = h
                                        } else {
                                            val sink = mountState.heightSink
                                            if (sink != null) {
                                                sink(h)
                                            } else {
                                                heightDp = h
                                            }
                                        }
                                        if (h > 0) {
                                            // 高度 > 0 只代表骨架已经排版，绝不再冒充“加载完成”。
                                            if (!mountState.resourcesSettled) {
                                                stage = PanelLoadStage.ResourcesLoading(panelTitle)
                                            }
                                            TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                                        }
                                    },
                                ),
                                "RikkaPanel",
                            )
                            addJavascriptInterface(TavernRuntimeManager.TavernJsBridge(), "RikkaBridge")
                            mountState.attach(this)
                            val frozenForFresh = frozenExportHtml
                            if (frozenForFresh != null) {
                                mountState.submitFrozen(frozenForFresh, foregroundHex, messageKey, screenHeightDp)
                            } else {
                                mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                            }
                            TavernRuntimeManager.registerAuxWebView(this, "panel")
                            TavernRuntimeManager.recordWebViewBootStage(
                                hostType = "panel",
                                hostId = messageKey ?: "anon",
                                stage = "webview_create",
                                detail = "created panel WebView",
                            )
                            TavernRuntimeManager.recordWebViewBootStage(
                                hostType = "panel",
                                hostId = messageKey ?: "anon",
                                stage = "first_navigation",
                                detail = "url=${TavernRuntimeManager.BASE_URL}panel.html",
                            )
                            loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
                        }
                        PanelScrollBridge(ctx).apply {
                            // 这个容器里的 WebView 真正归属哪份 PanelMountState（onRelease 要用）
                            ownerState = mountState
                            verticalScrollProbe = {
                                webView.canScrollVertically(-1) || webView.canScrollVertically(1)
                            }
                            // 注：横向手势已由 JS 侧逐元素接管（hscroll 增强），
                            // 这里只需保证事件能进 WebView，不必单独探测横向可滚性。
                            // 纵向边界：dir=1 手指下滑（看是否已在顶部），dir=-1 手指上滑（看是否到底部）。
                            // 到边界就让外层列表接管，保证竖滑自然衔接。
                            verticalEdgeProbe = { dir ->
                                if (dir > 0) !webView.canScrollVertically(-1) else !webView.canScrollVertically(1)
                            }
                            // 落点探测：手指按下的位置是否落在内层 overflow-y 滚动区里。
                            // webView.canScrollVertically() 只看根文档，看不到 .roster 这类内部滚动容器。
                            // 返回 null = 不在任何内层滚动区（回退到上面的整页探测）；
                            // true/false = 该滑动方向内层还能不能继续滚。
                            verticalZoneProbe = { x, y, fingerDown ->
                                val zones = mountState.innerZones
                                val pxPerCssPx = if (zones.viewportCssWidth > 0f && webView.width > 0) {
                                    webView.width / zones.viewportCssWidth
                                } else {
                                    density.density
                                }
                                zones.canScrollAt(x / pxPerCssPx, y / pxPerCssPx, fingerDown)
                            }
                            onTouchActiveChanged = { touching ->
                                isPanelTouchingRef.set(touching)
                                mountState.panelTouching = touching
                                if (!touching) {
                                    val deferredH = mountState.deferredTouchHeightDp
                                    mountState.deferredTouchHeightDp = -1
                                    if (deferredH > 0) {
                                        val sink = mountState.heightSink
                                        if (sink != null) {
                                            sink(deferredH)
                                        } else {
                                            heightDp = deferredH
                                        }
                                    }
                                }
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
                    update = {
                        // 已用冻结 DOM 挂载时，不要再被原始 html 覆盖（否则唤醒立即失效）。
                        if (frozenExportHtml == null) {
                            mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                        }
                    },
                    onRelease = { container ->
                        // 容器真正的归属状态：组合里的 mountState 可能是一个从未挂过 WebView 的占位实例，
                        // 用它判断会把"其实已经挂载好"的容器当成未挂载，直接丢弃。
                        val ownerState = (container as? PanelScrollBridge)?.ownerState ?: mountState
                        TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                        if (isInteractiveClass && ownerState.mounted && !ownerState.isDisposed && snapshotCompositeKey != null) {
                            val wvForFreeze = ownerState.webView
                            val keyForFreeze = snapshotCompositeKey
                            runCatching {
                                wvForFreeze?.evaluateJavascript(
                                    "window.__rikkaDehydratePanel?window.__rikkaDehydratePanel():''"
                                ) { raw ->
                                    PanelFrozenStateStore.parseDehydratePayload(raw)?.let { (stateJson, exportedHtml) ->
                                        PanelFrozenStateStore.save(context, keyForFreeze, stateJson, exportedHtml)
                                    }
                                }
                            }
                            // 滑出视口（onRelease）正处于滑动关键帧，禁止在此主线程同步截大图；
                            // 快照由上方 renderReady + 列表静止时的异步路径统一捕获。
                        }
                        if (useBoundedWebViewPool && !isInActivePool) {
                            DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)
                            ownerState.release()
                        } else if (ownerState.mounted && !ownerState.isDisposed) {
                            DetachedPanelWebViewCache.parkContainer(effectiveQueueKey, container, ownerState)
                        } else {
                            ownerState.release()
                        }
                    },
                )
                // 只有真正需要时才显示底部状态行：加载中 / 超时 / 出错。
                // 面板健康（renderReady 且无错误）时不显示任何按钮，避免"明明已渲染完却挂着重载"的误判。
                // 只有「确实还在加载」或「确实超时」才显示状态行。
                // 不再因为 console 里的一条 js 警告(lastError) 就报"面板资源或脚本异常"，
                // 那会让正常渲完的动画面板被误标成故障（真机已复现）。
                val showPanelStatusRow = isPanelVisiblyReady && (
                    (!mountState.resourcesSettled && !mountState.renderReady) ||
                        mountState.resourcesTimedOut
                    )
                if (showPanelStatusRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            // renderReady 已经代表「图片/字体/布局都稳定了」，此时不该再提示加载中。
                            !mountState.resourcesSettled && !mountState.renderReady -> {
                                Text(
                                    text = "正在加载图片/字体/脚本…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                            }
                            mountState.resourcesTimedOut -> {
                                Text(
                                    text = "部分图片仍在加载，可下拉稍候或点右侧重载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                            }
                            !mountState.lastError.isNullOrBlank() -> {
                                Text(
                                    text = "面板资源或脚本异常，可点右侧重载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                            }
                            else -> Spacer(modifier = Modifier.weight(1f))
                        }
                        Surface(
                            onClick = {
                                PanelHeightCache.invalidateForMessage(messageKey)
                                DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)
                                mountState.reloadNow()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!mountState.lastError.isNullOrBlank()) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Refresh03,
                                    contentDescription = "重载面板",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (!mountState.lastError.isNullOrBlank()) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    text = "重载面板",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!mountState.lastError.isNullOrBlank()) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 消息文本渲染：
 * - 若包含交互式 HTML 面板，通过 [splitMixedMessagePieces] 拆分为「Markdown 正文（原生 0 延迟秒出）+ 局部 HTML 面板（按序分步加载）」；
 * - 否则直接走原生 [MarkdownBlock]。
 */
@Composable
internal fun rememberAfterMarkdownHtml(content: String): String {
    val formatterVersion by TavernRuntimeManager.messageFormatterVersionFlow.collectAsState()
    val hasHooks = TavernRuntimeManager.hasMessageFormatterHooks
    if (!hasHooks || content.isEmpty() || !shouldInvokeWebViewMessageFormatter(content)) {
        return content
    }
    val out by produceState(initialValue = content, content, formatterVersion, hasHooks) {
        value = withContext(Dispatchers.Default) {
            TavernRuntimeManager.runMessageFormatterStageAsync("afterMarkdown", content, "", false)
        }
    }
    return out
}

@Composable
internal fun StTextContent(
    content: String,
    loading: Boolean,
    onClickCitation: (String) -> Unit,
    messageKey: String? = null,
    modifier: Modifier = Modifier,
    isHistoricalFloor: Boolean = false,
) {
    val html = if (!loading) rememberAfterMarkdownHtml(content) else content
    val panelCandidate = remember(html) { shouldRenderAsHtmlPanel(html) }
    if (loading) {
        // [P0 修复] 流式生成期间绝不能把 HTML 面板原文交给 MarkdownBlock。
        //
        // 旧行为：`if (!loading && shouldRenderAsHtmlPanel(html))` —— loading=true 时直接落到
        // MarkdownBlock(content)。实测两种后果（用仓库真实依赖 markdown 0.7.3 + jsoup 复现）：
        //   · 面板列首 0 缩进（酒馆卡标准形状）→ `<style>` 落进 Jsoup 的 DataNode，
        //     而 MarkdownNew 只处理 Element/TextNode → CSS 被静默丢弃，只剩无样式平文本或空白；
        //   · 面板带 4 空格/TAB 缩进或 ```html 围栏（含未闭合的流式态）→ 命中
        //     Markdown.kt 的 HighlightCodeBlock → **整块显示成代码**（带语言标签和复制按钮）。
        // 这就是用户说的「点发送后 WebView 界面概率变成代码」。
        //
        // 这里改为：流式期间命中面板就显示**剥标签后的可读正文预览**，既不出代码，
        // 也不在 streaming 期间挂 WebView —— 后者不可行：PanelMountState.decideUpdateAction
        // 对含 <script> 的面板一律 RELOAD_DOCUMENT，等于每个 token 全量重载 WebView。
        // 预览计算必须离开组合期主线程（长开场白上 5 趟正则/每 token 会吃掉帧预算）。
        if (panelCandidate) {
            // [P0] 预览必须节流：content 每个 token 都变，若以 content 为 key，
            // produceState 会每 token 重启一次协程并跑一遍正则（长开场白上等于逐字做重活）。
            // 这里按「长度分桶」取样 + 120ms 去抖：视觉上仍然是持续增长的正文，
            // 但计算次数从 O(字符数) 降到 O(秒数)。
            var previewSource by remember(messageKey) { mutableStateOf(content) }
            val latestContent by rememberUpdatedState(content)
            LaunchedEffect(messageKey) {
                var lastUpdateMs = 0L
                androidx.compose.runtime.snapshotFlow { latestContent }
                    .collect { current ->
                        val now = android.os.SystemClock.uptimeMillis()
                        val elapsed = now - lastUpdateMs
                        if (lastUpdateMs == 0L || elapsed >= 150L) {
                            previewSource = current
                            lastUpdateMs = now
                        } else {
                            delay(150L - elapsed)
                            previewSource = latestContent
                            lastUpdateMs = android.os.SystemClock.uptimeMillis()
                        }
                    }
            }
            val preview by produceState(initialValue = "", previewSource) {
                value = withContext(Dispatchers.Default) { extractQuickPlainPreview(previewSource) }
            }
            Column(modifier = modifier.fillMaxWidth()) {
                if (preview.isNotBlank()) {
                    MarkdownBlock(
                        content = preview,
                        onClickCitation = onClickCitation,
                    )
                } else {
                    PanelStreamingPlaceholderCard(panelTitle = extractPanelDisplayName(content, 0))
                }
            }
            return
        }
        MarkdownBlock(content = content, modifier = modifier, onClickCitation = onClickCitation)
        return
    }
    if (panelCandidate) {
        val pieces = remember(html) { splitMixedMessagePieces(html) }
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pieces.forEach { piece ->
                when (piece) {
                    is MixedContentPiece.MarkdownBody -> {
                        MarkdownBlock(
                            content = piece.text,
                            onClickCitation = onClickCitation,
                        )
                    }
                    is MixedContentPiece.HtmlPanel -> {
                        val subKey = if (piece.subIndex == 0 && pieces.size == 1) {
                            messageKey
                        } else {
                            "${messageKey ?: "msg"}#p${piece.subIndex}#${piece.html.hashCode()}"
                        }
                        MessageHtmlBlock(
                            html = piece.html,
                            messageKey = subKey,
                            panelTitle = piece.panelTitle,
                            isHistoricalFloor = isHistoricalFloor,
                        )
                    }
                }
            }
        }
    } else {
        MarkdownBlock(content = content, modifier = modifier, onClickCitation = onClickCitation)
    }
}

/**
 * 方案三（[WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE]）历史楼层 WebView 静态图片快照存储：
 * - 使用严格的**等比例缩放因子**（`uniformScale = minOf(1f, maxDimW / w, maxDimH / h)`）绘制快照，
 *   保证 `scaleX == scaleY`，杜绝长图被上下非等比挤压导致人像变形；
 * - 记录精确的 `aspectRatio = width / height`，供 Compose `Image` 通过 `.fillMaxWidth().aspectRatio(aspectRatio)` 1:1 还原。
 */
internal object PanelSnapshotStore {
    data class SnapshotEntry(
        val imageBitmap: ImageBitmap,
        val heightDp: Int,
        val aspectRatio: Float = (imageBitmap.width.toFloat() / imageBitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 20f),
        /**
         * 该快照是否在「渲染确认完成」(renderReady) 之后捕获。
         *
         * 只有 ready=true 的快照才允许顶替实时渲染。
         * 早期实现不看这个标记，结果把「面板还没画完时截到的空白图」缓存下来，
         * 下次直接顶替渲染 -> 整块面板空白（真机已复现）。
         * 从磁盘读回的旧快照默认按未就绪对待，必须重新渲染一次确认。
         */
        val ready: Boolean = false,
    )

    private const val MAX_MEMORY_ENTRIES = 12
    private const val MAX_MEMORY_BYTES = 24L * 1024L * 1024L
    private const val MAX_SNAPSHOT_WIDTH = 1080
    private const val MAX_SNAPSHOT_HEIGHT = 3072
    private val memoryMap = java.util.LinkedHashMap<String, SnapshotEntry>(16, 0.75f, true)

    fun memoryCount(): Int = synchronized(memoryMap) { memoryMap.size }

    fun memoryBytes(): Long = synchronized(memoryMap) {
        memoryMap.values.sumOf { entry ->
            entry.imageBitmap.width.toLong() * entry.imageBitmap.height.toLong() * 4L
        }
    }

    fun compositeKey(messageKey: String?, contentHash: Int): String? {
        if (messageKey.isNullOrEmpty()) return null
        return "$messageKey#$contentHash"
    }

    fun compositeKey(
        messageKey: String?,
        contentHash: Int,
        isDarkTheme: Boolean,
        widthPx: Int,
        fontScale: Float,
    ): String? {
        if (messageKey.isNullOrEmpty()) return null
        val themeTag = if (isDarkTheme) "dark" else "light"
        val fontBucket = (fontScale * 100f).toInt().coerceIn(50, 300)
        return "$messageKey#$contentHash#$themeTag#w${widthPx.coerceAtLeast(1)}#fs$fontBucket"
    }

    /**
     * **内容级**快照 key（不含 messageKey）。
     *
     * 用途：同一段 HTML 在多处出现时（例如第 3 楼和第 8 楼都是同一个「发送按钮」面板），
     * 只需渲染一次并共享同一张快照。
     *
     * 为什么安全：快照只承载**外观**，不承载状态。交互仍然通过点击快照唤醒真实 WebView
     * 来完成（见 MessageHtmlBlock 的冻结唤醒链路），因此复用外观不会串状态。
     */
    fun contentKey(
        contentHash: Int,
        isDarkTheme: Boolean,
        widthPx: Int,
        fontScale: Float,
    ): String {
        val themeTag = if (isDarkTheme) "dark" else "light"
        val fontBucket = (fontScale * 100f).toInt().coerceIn(50, 300)
        return "c$contentHash#$themeTag#w${widthPx.coerceAtLeast(1)}#fs$fontBucket"
    }

    private fun safeFileName(compositeKey: String): String {
        val sanitized = compositeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        val hashHex = Integer.toHexString(compositeKey.hashCode())
        return "snap_${sanitized}_$hashHex.png"
    }

    private fun ImageBitmap.asAndroidBitmapCompat(): Bitmap? = runCatching {
        val w = this.width
        val h = this.height
        if (w <= 0 || h <= 0) return@runCatching null
        val pixels = IntArray(w * h)
        this.readPixels(pixels, 0, w, 0, 0, w, h)
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    fun getMemory(compositeKey: String?): SnapshotEntry? {
        if (compositeKey.isNullOrEmpty()) return null
        return synchronized(memoryMap) { memoryMap[compositeKey] }
    }

    /**
     * 「已确认渲染完成」的内容登记表。
     *
     * 规则（用户提出的方案）：
     * **任何内容都必须先真实渲染过一次，之后它的快照才有资格被复用。**
     * 这样即使某次捕获到的是空白帧，只要该内容尚未登记为"已成功渲染"，
     * 就不会用快照顶替渲染，用户至少能看到一次真实面板，而不是空白。
     *
     * 只记录 key（很小），不记图；图仍由 panel_snapshots 管理。
     */
    private val snapshotIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderedOnceKeys = java.util.Collections.synchronizedSet(HashSet<String>())
    private val renderedOnceLoadStarted = AtomicBoolean(false)
    private val _renderedOnceVersion = MutableStateFlow(0)
    val renderedOnceVersion: StateFlow<Int> = _renderedOnceVersion.asStateFlow()

    /** 标记该内容已真实渲染完成一次（renderReady 之后调用）。仅首次新增时追加落盘，防止文件无限膨胀。 */
    fun markRenderedOnce(contentKey: String, context: Context?) {
        if (contentKey.isEmpty()) return
        if (!renderedOnceKeys.add(contentKey)) return
        val ctx = context?.applicationContext ?: return
        snapshotIoScope.launch {
            runCatching {
                val dir = File(ctx.filesDir, "panel_snapshots").apply { mkdirs() }
                File(dir, "rendered_once.txt").appendText(contentKey + "\n")
            }
        }
    }

    /** 该内容是否已经真实渲染完成过一次。首次调用异步加载磁盘登记表，杜绝主线程同步 readLines。 */
    fun isRenderedOnce(contentKey: String, context: Context?): Boolean {
        if (contentKey.isEmpty()) return false
        if (renderedOnceLoadStarted.compareAndSet(false, true)) {
            val ctx = context?.applicationContext
            if (ctx != null) {
                snapshotIoScope.launch {
                    runCatching {
                        val f = File(ctx.filesDir, "panel_snapshots/rendered_once.txt")
                        if (f.isFile) {
                            f.readLines(Charsets.UTF_8).forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty()) renderedOnceKeys.add(trimmed)
                            }
                            _renderedOnceVersion.value = _renderedOnceVersion.value + 1
                        }
                    }
                }
            }
        }
        return renderedOnceKeys.contains(contentKey)
    }

    /**
     * 写入内容级快照（同一段 HTML 跨楼层共享），可选落盘以便下次冷启动复用。
     */
    fun putContentEntry(
        contentKey: String,
        entry: SnapshotEntry,
        context: Context?,
        writeDisk: Boolean = true,
    ) {
        if (contentKey.isEmpty()) return
        putMemory(contentKey, entry)
        if (!writeDisk) return
        val ctx = context?.applicationContext ?: return
        snapshotIoScope.launch {
            runCatching {
                val dir = File(ctx.filesDir, "panel_snapshots").apply { mkdirs() }
                val outFile = File(dir, safeFileName(contentKey))
                if (!outFile.isFile) {
                    val bmp = entry.imageBitmap.asAndroidBitmapCompat()
                    if (bmp != null) {
                        FileOutputStream(outFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
                        }
                    }
                }
            }
        }
    }

    private fun putMemory(compositeKey: String, entry: SnapshotEntry) {
        synchronized(memoryMap) {
            memoryMap[compositeKey] = entry
            fun currentBytes(): Long = memoryMap.values.sumOf {
                it.imageBitmap.width.toLong() * it.imageBitmap.height.toLong() * 4L
            }
            while (memoryMap.size > MAX_MEMORY_ENTRIES || currentBytes() > MAX_MEMORY_BYTES) {
                val it = memoryMap.keys.iterator()
                if (!it.hasNext()) break
                val oldest = it.next()
                if (oldest == compositeKey && memoryMap.size == 1) break
                it.remove()
            }
        }
    }

    suspend fun loadFromDiskIfNeeded(
        context: Context,
        compositeKey: String?,
        fallbackHeightDp: Int,
        density: Float,
    ): SnapshotEntry? {
        if (compositeKey.isNullOrEmpty()) return null
        getMemory(compositeKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "panel_snapshots")
                val file = File(dir, safeFileName(compositeKey))
                if (!file.isFile || file.length() <= 0L) return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > MAX_SNAPSHOT_WIDTH ||
                    bounds.outHeight / sample > MAX_SNAPSHOT_HEIGHT
                ) {
                    sample *= 2
                }
                val bmp = BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return@withContext null
                val resolvedHeightDp = if (fallbackHeightDp > 0) {
                    fallbackHeightDp
                } else {
                    (bmp.height / density.coerceAtLeast(1f)).toInt().coerceAtLeast(48)
                }
                val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.05f, 20f)
                val entry = SnapshotEntry(
                    imageBitmap = bmp.asImageBitmap(),
                    heightDp = resolvedHeightDp,
                    aspectRatio = ratio,
                    // 从磁盘读回的旧快照无法确认当时是否画完 —— 按未就绪处理，
                    // 必须让该面板重新渲染一次并重新捕获。
                    ready = false,
                )
                putMemory(compositeKey, entry)
                entry
            }.getOrNull()
        }
    }

    /**
     * 判断位图是否「几乎空白」：抽样统计不同颜色数量，过少则认为是未渲染完成的帧。
     */
    private fun isLikelyBlankBitmap(bmp: Bitmap): Boolean = runCatching {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) return@runCatching true
        val sampleCols = 12
        val sampleRows = 16
        val distinct = HashSet<Int>()
        for (row in 0 until sampleRows) {
            val y = (row * (h - 1) / (sampleRows - 1).coerceAtLeast(1))
            for (col in 0 until sampleCols) {
                val x = (col * (w - 1) / (sampleCols - 1).coerceAtLeast(1))
                distinct.add(bmp.getPixel(x, y))
                if (distinct.size > 4) return@runCatching false
            }
        }
        // 采样点只有 <=4 种颜色 -> 视为空白/纯色帧
        distinct.size <= 4
    }.getOrDefault(true)

    fun captureFromWebView(
        context: Context,
        compositeKey: String?,
        webView: WebView?,
        heightDp: Int,
        /** 是否在渲染确认完成(renderReady)之后捕获；false 表示可能是空白帧。 */
        renderReadyAtCapture: Boolean = false,
        contentKey: String? = null,
    ): SnapshotEntry? {
        if (compositeKey.isNullOrEmpty() || webView == null || heightDp <= 0) return null
        val w = webView.width
        val h = webView.height
        if (w < 16 || h < 16) return null
        // 超高面板：draw() 单次绘制超过 GPU 纹理上限会拿到空白图。
        // 但下面的绘制逻辑本身已按 2048px 分块（h > 4096 分支），所以只有极端高度才该放弃。
        // 原先的 h > 14000 提前 return 把分块路径彻底挡死 —— 超高大面板永远没有快照。
        // 现改为：超过硬性物理阈值（32768px，ARGB_8888 单图上限量级）才放弃。
        if (h > MAX_TILE_CAPTURE_HEIGHT_PX) {
            runCatching {
                TavernRuntimeManager.appendLog(
                    "warn",
                    "[panel-snapshot] skipped extremely tall panel key=$compositeKey h=${h}px > $MAX_TILE_CAPTURE_HEIGHT_PX (keep alive)",
                )
            }
            return null
        }
        return runCatching {
            val maxDimW = MAX_SNAPSHOT_WIDTH.toFloat()
            val maxDimH = MAX_SNAPSHOT_HEIGHT.toFloat()
            // 强制使用单一等比例缩放因子 uniformScale，保证水平与垂直缩放率 100% 相同，绝不压扁长图！
            val uniformScale = minOf(1f, maxDimW / w.toFloat(), maxDimH / h.toFloat()).coerceAtLeast(0.1f)
            val targetW = (w * uniformScale).toInt().coerceAtLeast(16)
            val targetH = (h * uniformScale).toInt().coerceAtLeast(16)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (h > 4096) {
                // 超高面板分块绘制（每块 2048px），避免单次 Canvas 绘制超出纹理上限
                val tileHeightPx = 2048
                var offsetY = 0
                while (offsetY < h) {
                    val sliceH = minOf(tileHeightPx, h - offsetY)
                    canvas.save()
                    canvas.scale(uniformScale, uniformScale)
                    canvas.clipRect(0, offsetY, w, offsetY + sliceH)
                    webView.draw(canvas)
                    canvas.restore()
                    offsetY += sliceH
                }
            } else {
                if (uniformScale != 1f) {
                    canvas.scale(uniformScale, uniformScale)
                }
                webView.draw(canvas)
            }
            val ratio = (targetW.toFloat() / targetH.toFloat()).coerceIn(0.05f, 20f)
            // 空白帧检测：整张图若几乎只有一种颜色（透明/纯白/纯黑），
            // 说明这个时刻面板还没画出内容，不能作为可复用快照。
            if (isLikelyBlankBitmap(bmp)) {
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "warn",
                        "[panel-snapshot] blank frame rejected key=$compositeKey (${targetW}x${targetH})",
                    )
                }
                return@runCatching null
            }
            val entry = SnapshotEntry(
                imageBitmap = bmp.asImageBitmap(),
                heightDp = heightDp,
                aspectRatio = ratio,
                ready = renderReadyAtCapture,
            )
            putMemory(compositeKey, entry)
            val appCtx = context.applicationContext
            snapshotIoScope.launch {
                runCatching {
                    val dir = File(appCtx.filesDir, "panel_snapshots").apply { mkdirs() }
                    val outFile = File(dir, safeFileName(compositeKey))
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 92, baos)
                    val pngBytes = baos.toByteArray()
                    outFile.writeBytes(pngBytes)
                    if (!contentKey.isNullOrEmpty() && contentKey != compositeKey) {
                        val contentFile = File(dir, safeFileName(contentKey))
                        if (!contentFile.isFile) {
                            contentFile.writeBytes(pngBytes)
                        }
                    }
                }
            }
            entry
        }.getOrNull()
    }
}

/**
 * 阶段 4：冻结面板状态存储（表单值 / details[open] / 滚动位置 / active 标记 JSON + 导出 HTML）。
 * 存盘路径：`filesDir/panel_states/state_<safeKey>.json` 与 `.html`。
 */
internal object PanelFrozenStateStore {
    data class FrozenState(
        val stateJson: String,
        val exportedHtml: String,
        val savedAtMs: Long = System.currentTimeMillis(),
    )

    private const val MAX_MEMORY_ENTRIES = 32
    private val map = java.util.LinkedHashMap<String, FrozenState>(32, 0.75f, true)

    private fun safeBaseName(compositeKey: String): String {
        val sanitized = compositeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        val hashHex = Integer.toHexString(compositeKey.hashCode())
        return "state_${sanitized}_$hashHex"
    }

    fun getMemory(compositeKey: String?): FrozenState? {
        if (compositeKey.isNullOrEmpty()) return null
        return synchronized(map) { map[compositeKey] }
    }

    fun save(context: Context, compositeKey: String?, stateJson: String, exportedHtml: String) {
        if (compositeKey.isNullOrEmpty()) return
        val entry = FrozenState(stateJson = stateJson, exportedHtml = exportedHtml)
        synchronized(map) {
            map[compositeKey] = entry
            while (map.size > MAX_MEMORY_ENTRIES) {
                val it = map.keys.iterator()
                if (!it.hasNext()) break
                it.next()
                it.remove()
            }
        }
        val appCtx = context.applicationContext
        val base = safeBaseName(compositeKey)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val dir = File(appCtx.filesDir, "panel_states").apply { mkdirs() }
                File(dir, "$base.json").writeText(stateJson, Charsets.UTF_8)
                if (exportedHtml.isNotEmpty()) {
                    File(dir, "$base.html").writeText(exportedHtml, Charsets.UTF_8)
                }
            }
        }
    }

    suspend fun loadIfNeeded(context: Context, compositeKey: String?): FrozenState? {
        if (compositeKey.isNullOrEmpty()) return null
        getMemory(compositeKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "panel_states")
                val base = safeBaseName(compositeKey)
                val jsonFile = File(dir, "$base.json")
                if (!jsonFile.isFile) return@withContext null
                val stateJson = jsonFile.readText(Charsets.UTF_8)
                val htmlFile = File(dir, "$base.html")
                val exportedHtml = if (htmlFile.isFile) htmlFile.readText(Charsets.UTF_8) else ""
                val entry = FrozenState(stateJson = stateJson, exportedHtml = exportedHtml)
                synchronized(map) { map[compositeKey] = entry }
                entry
            }.getOrNull()
        }
    }

    fun parseDehydratePayload(evalResult: String?): Pair<String, String>? {
        if (evalResult.isNullOrBlank() || evalResult == "null" || evalResult == "\"\"") return null
        return runCatching {
            val unquoted = if (evalResult.startsWith("\"") && evalResult.endsWith("\"")) {
                JSONObject("{\"v\":$evalResult}").getString("v")
            } else {
                evalResult
            }
            if (unquoted.isBlank()) return null
            val obj = JSONObject(unquoted)
            val stateObj = obj.optJSONObject("state") ?: return null
            val htmlStr = obj.optString("html", "")
            stateObj.toString() to htmlStr
        }.getOrNull()
    }
}

/**
 * 面板已测高度缓存（按 `messageKey + contentHash` 复合键存储，避免消息编辑或切换 swipe 分支后命中旧高度）。
 */
internal object PanelHeightCache {
    private const val MAX_ENTRIES = 64
    private val map = java.util.LinkedHashMap<String, Int>(16, 0.75f, true)

    private fun compositeKey(key: String?, contentHash: Int): String? {
        if (key.isNullOrEmpty()) return null
        return "$key#$contentHash"
    }

    fun get(key: String?, contentHash: Int = 0): Int {
        val ck = compositeKey(key, contentHash) ?: return 0
        return synchronized(map) { map[ck] ?: 0 }
    }

    fun put(key: String?, contentHash: Int = 0, height: Int) {
        val ck = compositeKey(key, contentHash) ?: return
        if (height <= 0) return
        synchronized(map) {
            map[ck] = height
            while (map.size > MAX_ENTRIES) {
                val it = map.keys.iterator()
                if (!it.hasNext()) break
                it.next()
                it.remove()
            }
        }
    }

    fun invalidateForMessage(key: String?) {
        if (key.isNullOrEmpty()) return
        val prefix = "$key#"
        synchronized(map) {
            val toRemove = map.keys.filter { it == key || it.startsWith(prefix) }
            toRemove.forEach { map.remove(it) }
        }
    }
}

/**
 * 阶段 5：方向角手势锁容器。
 * - `ACTION_DOWN` 先锁外层拦截，待超过 `touchSlop` 后判定方向：
 *   - `dx >= dy * 0.7f` 视为横滑，持续锁死外层 `LazyColumn`，保留横滑与松手惯性；
 *   - 否则视为竖滑：仅当 WebView 内部可竖滚时保留拦截，否则放行给外层聊天列表。
 * - 触摸期间通知外层暂停更新 Compose 高度状态，避免重排打断滑动惯性。
 */
/**
 * 面板触摸桥：决定「这次手势交给面板内部，还是让外层聊天列表接管」。
 *
 * ## 为什么不再用固定角度锁
 * 旧实现用 `dx >= dy * 0.7` 单一判据：手指斜 35 度以上横滑就被判成竖滑，
 * 事件交还外层，宽面板的横向标签页/横向滚动容器直接断掉，也拿不到惯性。
 * 用户的诉求是「不要只有严格向左滑的趋势才认」。
 *
 * ## 现在的策略（按优先级）
 * 1. 按下后先暂扣事件，等首个有效位移明确方向；
 * 2. 横向位移占优（dx >= dy）即判为横向意图，整轮手势锁给面板 ——
 *    横向容器获得完整连续事件流，WebView 自身的 fling/惯性得以保留；
 * 3. 纵向占优时看面板此刻能否真的往该方向滚：
 *    - 能滚 -> 锁给面板（如角色卡 12 人列表的内部滚动）；
 *    - 已到边界（顶部继续上滑 / 底部继续下滑）-> 让外层列表接走，自然衔接；
 * 4. 一旦锁定，同一轮手势内不再改判，避免中途横跳造成抖动。
 *
 * 注意：不调用 pauseTimers，也不替换 WebView 的滚动实现，
 * 只为面板争取完整的事件流。
 */
/**
 * 面板触摸桥：决定「手势交给面板内部 WebView，还是让外层聊天列表接管」。
 *
 * ## 设计原则（针对"卡手、没惯性"的修复）
 * 旧实现在 ACTION_MOVE 里反复调用 requestDisallowInterceptTouchEvent，
 * 而且先「暂扣」事件再判定 —— 这会让 WebView 内部的滚动被打断，
 * 松手时的 fling/惯性被吃掉，手感发黏。
 *
 * 现在的做法：
 * - 按下时**不抢**，让事件自然流向 WebView（保持原生手感与惯性）；
 * - 只在手指位移超过 slop 的**第一瞬间**判定一次轴向，然后立刻做最终决定：
 *   * 横向意图 -> 锁给面板，整轮不再干预；
 *   * 纵向且面板能滚 -> 锁给面板，整轮不再干预；
 *   * 纵向且面板已到边界 -> 让外层列表接管（自然衔接滚动）；
 * - 判定完成后不再重判、不再反复 requestDisallow，避免抖动与惯性丢失。
 */
/**
 * 面板触摸桥。
 *
 * ## 血泪教训（真机多次验证）
 * 之前几版都试图在 dispatchTouchEvent 里"判定方向 + 抢/放事件"：
 * 结果一律导致 WebView 收不到 ACTION_DOWN，
 *  -> 面板内部纵向滚动失效
 *  -> 横向标签栏完全不动
 *  -> 按钮点不动
 *  -> 松手没有惯性
 *
 * ## 现在：纯旁观者
 * 只把触摸起止登记下来（用于暂停高度上报），**绝不拦截、绝不 requestDisallow**，
 * 事件一律原样交给 WebView，由 WebView 自己处理滚动/惯性/点击。
 *
 * 外层 LazyColumn 与 WebView 的冲突交给 WebView 自己按需处理：
 * WebView 在内容可滚动时会自己调用 requestDisallowInterceptTouchEvent(true)，
 * 这是 Chromium 内建行为，比外层猜测可靠得多。
 */
/**
 * 面板触摸桥：把「面板内部可滚动时的手势」保留给 WebView，只在到达边界时交给外层列表。
 *
 * ## 关键时序（真机调出来的）
 * - ACTION_DOWN：**必须立刻 requestDisallowIntercept(true)**。
 *   WebView 才能收到完整事件流；否则内层滚动与 DOM 点击都不会发生。
 * - ACTION_MOVE（首次超过 slop）：判定轴向一次：
 *   * 纵向且面板还能往该方向滚  -> 整轮锁给面板（保持 disallow=true）
 *   * 纵向但已到边界            -> 放行外层（disallow=false），自然衔接列表滚动
 *   * 横向                      -> 锁给面板（横向容器/标签栏需要连续事件流）
 * - 判定后不再反复切换，避免抖动吃惯性。
 */
private class PanelScrollBridge(context: Context) : FrameLayout(context) {
    /** 面板此刻能否上下滚动。 */
    var verticalScrollProbe: (() -> Boolean)? = null

    /** 面板是否已到达纵向边界。dir=1 手指下滑查顶部，dir=-1 手指上滑查底部。 */
    var verticalEdgeProbe: ((Int) -> Boolean)? = null

    /** 落点判定：返回 null = 触点不在内层滚动区；true/false = 该滑动方向内层还能不能继续滚。 */
    var verticalZoneProbe: ((x: Float, y: Float, fingerDown: Boolean) -> Boolean?)? = null

    /** 这个容器里的 WebView 真正归属的 PanelMountState（onRelease 使用）。 */
    var ownerState: PanelMountState? = null

    var onTouchActiveChanged: ((Boolean) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var decided = false
    private var moveCount = 0
    private var lockedToPanel = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                decided = false
                lockedToPanel = false
                moveCount = 0
                onTouchActiveChanged?.invoke(true)
                // 先扣住：保证 WebView 收到完整事件流（内层滚动 + DOM 点击的前提）。
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                moveCount++
                if (!decided) {
                    val dx = kotlin.math.abs(ev.x - downX)
                    val dy = kotlin.math.abs(ev.y - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        decided = true
                        lockedToPanel = if (dx > dy) {
                            // 横向：交给面板里的横向容器
                            true
                        } else {
                            // 纵向：先看按下的位置是不是在内层滚动区里
                            val fingerDown = ev.y > downY
                            val zoneVerdict = verticalZoneProbe?.invoke(downX, downY, fingerDown)
                            if (zoneVerdict != null) {
                                // 在内层滚动区：还能朝这个方向滚就留给面板；已到顶/底就交还外层列表
                                zoneVerdict
                            } else {
                                // 不在内层滚动区：沿用原逻辑（整页能不能滚 + 是否到边界）
                                val canScroll = verticalScrollProbe?.invoke() == true
                                val atEdge = verticalEdgeProbe?.invoke(if (fingerDown) 1 else -1) == true
                                canScroll && !atEdge
                            }
                        }
                        parent?.requestDisallowInterceptTouchEvent(lockedToPanel)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 诊断：确认 WebView 到底收到多少个 MOVE 事件
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "debug",
                        "[gesture-probe] action=" + ev.actionMasked + " moveCount=" + moveCount +
                            " lockedToPanel=" + lockedToPanel,
                    )
                }
                decided = false
                lockedToPanel = false
                onTouchActiveChanged?.invoke(false)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // 已判定交给面板：外层不许抢
        if (decided && lockedToPanel && disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(true)
            return
        }
        // 已判定交给外层：不再被锁回去
        if (decided && !lockedToPanel) {
            super.requestDisallowInterceptTouchEvent(false)
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}

internal enum class PanelUpdateAction {
    NO_OP,
    RELOAD_DOCUMENT,
    REMOUNT_IN_PLACE,
    REMOUINT_IN_PLACE,
}

/** 面板挂载状态（WebView 页面就绪后注入 HTML；`mountedHtml` 使用 Compose `mutableStateOf` 响应式状态）。 */
internal class PanelMountState {
    // ── 宿主回调：由"当前存活的组合"通过 DisposableEffect 注册 ──
    @Volatile var heightSink: ((Int) -> Unit)? = null
    @Volatile var panelTouching: Boolean = false
    @Volatile var deferredTouchHeightDp: Int = -1
    /** JS 最近一次上报的高度(dp)。缓存命中时用来恢复高度，不依赖 PanelHeightCache 是否命中。 */
    @Volatile var lastReportedHeightDp: Int = 0

    // ── 内层纵向滚动区（JS 上报，Native 手势判定时同步读取）──
    @Volatile var innerZones: InnerScrollZones = InnerScrollZones.EMPTY
        private set

    fun updateInnerZones(json: String) {
        if (isDisposed) return
        runCatching {
            val o = JSONObject(json)
            val arr = o.getJSONArray("z")
            val data = FloatArray(arr.length() * 6)
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                for (k in 0 until 6) data[i * 6 + k] = a.getDouble(k).toFloat()
            }
            innerZones = InnerScrollZones(o.optDouble("vw", 0.0).toFloat(), data)
        }
    }

    var webView: WebView? = null
        private set
    private var pageReady = false
    @Volatile
    var isDisposed: Boolean = false
        private set

    internal companion object {
        const val MAX_PAGE_LOAD_RETRIES = 3
        const val STREAMING_REMOUNT_COALESCE_MS = 48L
        val SCRIPT_TAG_RE = Regex("<script[\\s>]", RegexOption.IGNORE_CASE)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    internal var clockMsProvider: () -> Long = {
        runCatching { android.os.SystemClock.uptimeMillis() }.getOrDefault(0L)
    }
    private var lastRemountTimestampMs: Long = -STREAMING_REMOUNT_COALESCE_MS
    private var coalescedRemountScheduled: Boolean = false
    private var pendingReadyProbeRunnable: Runnable? = null
    private val coalescedRemountRunnable = Runnable {
        coalescedRemountScheduled = false
        if (!isDisposed) {
            lastRemountTimestampMs = clockMsProvider()
            tryMount()
        }
    }

    fun scheduleReadyProbe(delayMs: Long = 220L, probe: () -> Unit) {
        cancelReadyProbe()
        if (isDisposed || pageReady || mounted) return
        val r = Runnable {
            pendingReadyProbeRunnable = null
            if (!isDisposed && !pageReady && !mounted) {
                probe()
            }
        }
        pendingReadyProbeRunnable = r
        runCatching { mainHandler.postDelayed(r, delayMs) }
    }

    fun cancelReadyProbe() {
        pendingReadyProbeRunnable?.let { r ->
            pendingReadyProbeRunnable = null
            runCatching { mainHandler.removeCallbacks(r) }
        }
    }

    fun flushPendingRemount() {
        if (coalescedRemountScheduled) {
            coalescedRemountScheduled = false
            runCatching { mainHandler.removeCallbacks(coalescedRemountRunnable) }
        }
        if (!isDisposed && pageReady && pendingHtml != null && pendingHtml != mountedHtml) {
            lastRemountTimestampMs = clockMsProvider()
            tryMount()
        }
    }

    private var everMounted = false
    private var lastReloadGen = 0
    private var pageLoadRetries = 0
    var reloadCount: Int = 0
        private set
    var inPlaceRemountCount: Int = 0
        private set
    var mountedHtml by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
    var resourcesSettled by mutableStateOf(false)
        private set
    var resourcesTimedOut by mutableStateOf(false)
        private set
    var renderReady by mutableStateOf(false)
        private set
    var renderReadyHeightPx: Int = 0
        private set
    @Volatile
    private var onRenderReadyListener: ((Int) -> Unit)? = null

    fun setOnRenderReadyListener(listener: ((Int) -> Unit)?) {
        onRenderReadyListener = listener
        if (listener != null && renderReady && !isDisposed) {
            listener(renderReadyHeightPx)
        }
    }

    fun markRenderReady(scrollHeightPx: Int) {
        if (isDisposed || !mounted) return
        renderReadyHeightPx = scrollHeightPx
        renderReady = true
        onRenderReadyListener?.invoke(scrollHeightPx)
    }

    fun markResourcesSettled(timedOut: Boolean) {
        if (isDisposed || !mounted) return
        resourcesTimedOut = timedOut
        resourcesSettled = true
    }

    private fun resetResourceSettleState() {
        resourcesSettled = false
        resourcesTimedOut = false
        renderReady = false
        renderReadyHeightPx = 0
    }

    fun reloadNow() {
        if (isDisposed) return
        cancelReadyProbe()
        if (coalescedRemountScheduled) {
            coalescedRemountScheduled = false
            runCatching { mainHandler.removeCallbacks(coalescedRemountRunnable) }
        }
        val wv = webView ?: return
        lastError = null
        resetResourceSettleState()
        pageLoadRetries = 0
        pageReady = false
        everMounted = false
        mountedHtml = null
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
        runCatching {
            wv.loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
        }
    }

    fun decideUpdateAction(html: String, reloadGen: Int = lastReloadGen): PanelUpdateAction {
        val prev = mountedHtml
        if (!everMounted || prev == null || reloadGen != lastReloadGen) {
            everMounted = true
            lastReloadGen = reloadGen
            mountedHtml = html
            reloadCount++
            return PanelUpdateAction.RELOAD_DOCUMENT
        }
        if (html == prev) {
            return PanelUpdateAction.NO_OP
        }
        val hasScript = SCRIPT_TAG_RE.containsMatchIn(html) || SCRIPT_TAG_RE.containsMatchIn(prev)
        return if (hasScript) {
            mountedHtml = html
            reloadCount++
            PanelUpdateAction.RELOAD_DOCUMENT
        } else {
            mountedHtml = html
            inPlaceRemountCount++
            PanelUpdateAction.REMOUINT_IN_PLACE
        }
    }
    private var desiredInternalScroll: Boolean = false
    @Volatile
    private var pendingHtml: String? = null
    @Volatile
    private var pendingHtmlHash: Int = 0
    private var pendingForeground: String? = null
    private var appliedForeground: String? = null
    @Volatile
    private var pendingKey: String? = null
    private var appliedKey: String? = null
    private var appliedViewportPx: Int = 0
    private var pendingViewportPx: Int = 0

    fun currentPendingHtml(): String = pendingHtml ?: ""
    fun currentPendingHtmlHash(): Int = pendingHtmlHash
    fun currentPendingKey(): String = pendingKey ?: ""

    fun attach(wv: WebView) {
        isDisposed = false
        if (webView !== wv) {
            webView = wv
            pageReady = false
            everMounted = false
            mountedHtml = null
            resetResourceSettleState()
            appliedForeground = null
            appliedKey = null
            appliedViewportPx = 0
        }
    }

    fun onPageReady() {
        if (isDisposed) return
        cancelReadyProbe()
        pageReady = true
        tryMount()
    }

    fun tryMountIfNeeded() {
        if (!isDisposed && pageReady && !mounted) {
            tryMount()
        }
    }

    fun onHostRendererRestarted() {
        if (isDisposed) return
        val wv = webView ?: return
        if (mounted && pageReady) {
            runCatching {
                wv.evaluateJavascript(
                    "try{if(window.__rikkaSyncAfterHostRestart)window.__rikkaSyncAfterHostRestart();}catch(e){}",
                    null,
                )
            }
        } else {
            tryMountIfNeeded()
        }
    }

    fun setInternalScrollEnabled(scrollable: Boolean) {
        desiredInternalScroll = scrollable
        if (isDisposed) return
        val wv = webView ?: return
        wv.isVerticalScrollBarEnabled = scrollable
        if (!pageReady) return
        val overflowVal = if (scrollable) "auto" else "visible"
        runCatching {
            wv.evaluateJavascript(
                "try{" +
                    "document.documentElement.style.overflowY='$overflowVal';" +
                    "document.body.style.overflowY='$overflowVal';" +
                    "}catch(e){}",
                null,
            )
        }
    }

    val mounted: Boolean get() = mountedHtml != null

    fun onRuntimeMissing(): Boolean {
        if (isDisposed || pageReady || mounted) return false
        val wv = webView ?: return false
        if (pageLoadRetries >= MAX_PAGE_LOAD_RETRIES) {
            TavernRuntimeManager.appendLog(
                "error",
                "[panel] runtime still missing after $MAX_PAGE_LOAD_RETRIES retries key=$pendingKey; giving up",
            )
            return false
        }
        pageLoadRetries++
        pageReady = false
        everMounted = false
        mountedHtml = null
        resetResourceSettleState()
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
        return try {
            wv.loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun onWatchdogTimeout(): Boolean {
        if (isDisposed) return false
        val wv = webView ?: return false
        if (pageLoadRetries >= MAX_PAGE_LOAD_RETRIES) return false
        pageLoadRetries++
        resetResourceSettleState()
        if (pageReady && pendingHtml != null) {
            everMounted = false
            mountedHtml = null
            tryMount()
            return true
        }
        pageReady = false
        everMounted = false
        mountedHtml = null
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
        return try {
            wv.loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 冻结唤醒用：把「脚本执行后的真实 DOM 快照 HTML」作为挂载内容。
     * 与 [submit] 的区别是会强制重新挂载（即使 html 相同），因为旧 DOM 已被丢弃。
     */
    fun submitFrozen(exportedHtml: String, foregroundHex: String?, messageKey: String? = null, viewportPx: Int = 0) {
        if (isDisposed) return
        frozenMountHtml = exportedHtml
        pendingHtml = exportedHtml
        pendingHtmlHash = exportedHtml.hashCode()
        pendingForeground = foregroundHex
        pendingKey = messageKey
        if (viewportPx > 0) pendingViewportPx = viewportPx
        resetResourceSettleState()
        everMounted = false
        mountedHtml = null
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
        tryMount()
    }

    /** 清除冻结挂载内容，回到常规 html 挂载路径（回退用）。 */
    fun clearFrozenMount() {
        frozenMountHtml = null
    }

    @Volatile
    private var frozenMountHtml: String? = null

    fun submit(html: String, foregroundHex: String?, messageKey: String? = null, viewportPx: Int = 0) {
        if (isDisposed) return
        val isIdentical = html == pendingHtml && foregroundHex == pendingForeground && messageKey == pendingKey
        pendingHtml = html
        pendingHtmlHash = html.hashCode()
        pendingForeground = foregroundHex
        pendingKey = messageKey
        if (viewportPx > 0) pendingViewportPx = viewportPx
        if (isIdentical && mounted) return
        resetResourceSettleState()
        if (everMounted && pageReady) {
            val now = clockMsProvider()
            val elapsed = now - lastRemountTimestampMs
            if (elapsed in 0 until STREAMING_REMOUNT_COALESCE_MS) {
                if (!coalescedRemountScheduled) {
                    coalescedRemountScheduled = true
                    runCatching { mainHandler.postDelayed(coalescedRemountRunnable, STREAMING_REMOUNT_COALESCE_MS - elapsed) }
                }
                return
            }
            lastRemountTimestampMs = now
        }
        if (coalescedRemountScheduled) {
            coalescedRemountScheduled = false
            runCatching { mainHandler.removeCallbacks(coalescedRemountRunnable) }
        }
        tryMount()
    }

    fun onRendererGone() {
        cancelReadyProbe()
        if (coalescedRemountScheduled) {
            coalescedRemountScheduled = false
            runCatching { mainHandler.removeCallbacks(coalescedRemountRunnable) }
        }
        webView = null
        pageReady = false
        everMounted = false
        mountedHtml = null
        resetResourceSettleState()
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
    }

    fun release() {
        isDisposed = true
        cancelReadyProbe()
        coalescedRemountScheduled = false
        runCatching { mainHandler.removeCallbacks(coalescedRemountRunnable) }
        webView?.let { wv ->
            webView = null
            pageReady = false
            everMounted = false
            mountedHtml = null
            resetResourceSettleState()
            TavernRuntimeManager.unregisterAuxWebView(wv)
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    internal fun simulateMountForTest(html: String) {
        pageReady = true
        everMounted = true
        mountedHtml = html
        if (desiredInternalScroll) {
            setInternalScrollEnabled(true)
        }
    }

    private fun tryMount() {
        if (isDisposed) return
        val wv = webView ?: return
        val html = pendingHtml ?: return
        if (!pageReady) return
        val fg = pendingForeground
        if (fg != null && fg != appliedForeground) {
            appliedForeground = fg
            wv.evaluateJavascript(
                "document.documentElement.style.setProperty('--rikka-fg',${JSONObject.quote(fg)});",
                null,
            )
        }
        val key = pendingKey
        if (key != appliedKey) {
            appliedKey = key
            wv.evaluateJavascript(
                "window.__rikkaPanelMessageKey=" + JSONObject.quote(key ?: "") + ";",
                null,
            )
        }
        val viewportPx = pendingViewportPx
        if (viewportPx > 0 && viewportPx != appliedViewportPx) {
            appliedViewportPx = viewportPx
            wv.evaluateJavascript(
                "window.__rikkaViewportHeightPx=$viewportPx;" +
                    "try{document.documentElement.style.setProperty('--rikka-panel-vh','${viewportPx}px');}catch(e){}",
                null,
            )
        }

        if (html == mountedHtml) return
        if (everMounted) {
            val prevHtml = mountedHtml.orEmpty()
            val hasScript = Regex("<script[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
                Regex("<script[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(prevHtml)
            if (hasScript) {
                reloadCount++
                everMounted = false
                mountedHtml = null
                appliedForeground = null
                appliedKey = null
                appliedViewportPx = 0
                pageReady = false
                try {
                    wv.loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
                } catch (_: Throwable) {
                }
                return
            } else {
                inPlaceRemountCount++
                mountedHtml = html
                resetResourceSettleState()
                if (desiredInternalScroll) {
                    setInternalScrollEnabled(true)
                }
                val keyQuotedRemount = JSONObject.quote(key ?: "")
                wv.evaluateJavascript(
                    "try{" +
                        "window.__rikkaPanelMessageKey=$keyQuotedRemount;" +
                        "var __h=(window.RikkaPanel&&window.RikkaPanel.getPendingHtml)?String(window.RikkaPanel.getPendingHtml()||''):'';" +
                        "if(__h&&window.__rikkaPanelMount){window.__rikkaPanelMount(__h,'remount');}" +
                        "}catch(e){console.error('[panel] remount err',e);}",
                    null,
                )
                return
            }
        }
        everMounted = true
        mountedHtml = html
        resetResourceSettleState()
        if (desiredInternalScroll) {
            setInternalScrollEnabled(true)
        }
        TavernRuntimeManager.appendLog(
            "debug",
            "[panel] mount key=$key len=${html.length} retries=$pageLoadRetries",
        )
        val keyQuoted = JSONObject.quote(key ?: "")
        wv.evaluateJavascript(
            "try{" +
                "window.__rikkaPanelMessageKey=$keyQuoted;" +
                "var __h=(window.RikkaPanel&&window.RikkaPanel.getPendingHtml)?String(window.RikkaPanel.getPendingHtml()||''):'';" +
                "if(__h){window.__rikkaPanelMount?window.__rikkaPanelMount(__h):(window.__rikkaPanelPendingHtml=__h);}" +
                "}catch(e){console.error('[panel] mount err',e);}",
            null,
        )
    }
}

/** JS 上报的内层纵向滚动区。每个区域 6 个值：left, top, right, bottom, canUp, canDown（CSS px，视口坐标）。 */
internal class InnerScrollZones(
    val viewportCssWidth: Float,
    private val data: FloatArray,
) {
    /**
     * 返回 null = 落点不在任何内层滚动区；
     * true/false = 该滑动方向上内层还能不能继续滚（会沿着包含落点的祖先滚动区一路向外找，找到任何一个能滚的就返回 true）。
     * fingerDown=true 表示手指向下滑（内容回滚，看 canUp）；false 表示手指向上滑（看 canDown）。
     */
    fun canScrollAt(xCss: Float, yCss: Float, fingerDown: Boolean): Boolean? {
        var hit = false
        var i = data.size / 6 - 1 // JS 按文档顺序上报，后者更深，从最内层开始找
        while (i >= 0) {
            val b = i * 6
            if (xCss >= data[b] && xCss <= data[b + 2] && yCss >= data[b + 1] && yCss <= data[b + 3]) {
                hit = true
                val can = if (fingerDown) data[b + 4] else data[b + 5]
                if (can > 0f) return true
            }
            i--
        }
        return if (hit) false else null
    }

    companion object {
        val EMPTY = InnerScrollZones(0f, FloatArray(0))
    }
}

@Keep
private class PanelBridge(
    private val handler: Handler,
    private val mountState: PanelMountState,
    private val onHeight: (Int) -> Unit,
) {
    private var firstMountLogged = false
    private var firstZonesLogged = false

    @JavascriptInterface
    fun onPanelRuntimeReady() {
        if (mountState.isDisposed) return
        handler.post {
            if (mountState.isDisposed) return@post
            TavernRuntimeManager.recordWebViewBootStage(
                hostType = "panel",
                hostId = mountState.currentPendingKey().ifEmpty { "anon" },
                stage = "runtime_ready",
                detail = "source=onPanelRuntimeReady",
            )
            mountState.onPageReady()
            mountState.tryMountIfNeeded()
        }
    }

    @JavascriptInterface
    fun onRenderReady(panelId: String, scrollHeight: Int) {
        if (mountState.isDisposed) return
        handler.post {
            if (mountState.isDisposed) return@post
            TavernRuntimeManager.appendLog(
                "debug",
                "[panel] onRenderReady panelId=$panelId scrollHeight=$scrollHeight",
            )
            mountState.markRenderReady(scrollHeight.coerceAtLeast(0))
        }
    }

    @JavascriptInterface
    fun onPanelClassProbed(contentHash: Int, className: String) {
        if (mountState.isDisposed) return
        val parsed = runCatching { PanelClass.valueOf(className.uppercase(Locale.US)) }.getOrNull() ?: return
        PanelClassificationCache.recordRuntimeClass(contentHash, parsed)
        TavernRuntimeManager.appendLog(
            "debug",
            "[panel-class] runtime probe hash=$contentHash class=$parsed",
        )
    }

    @JavascriptInterface
    fun onPanelResourcesSettled(timedOut: Boolean) {
        if (mountState.isDisposed) return
        handler.post {
            if (mountState.isDisposed) return@post
            TavernRuntimeManager.recordWebViewBootStage(
                hostType = "panel",
                hostId = mountState.currentPendingKey().ifEmpty { "anon" },
                stage = "resources_settled",
                detail = if (timedOut) "timed_out=true (仍有图片/字体/脚本在加载)" else "complete",
            )
            mountState.markResourcesSettled(timedOut)
        }
    }

    @JavascriptInterface
    fun panelResize(height: Int) {
        if (mountState.isDisposed) return
        handler.post {
            if (mountState.isDisposed) return@post
            if (height > 0 && !firstMountLogged) {
                firstMountLogged = true
                TavernRuntimeManager.recordWebViewBootStage(
                    hostType = "panel",
                    hostId = mountState.currentPendingKey().ifEmpty { "anon" },
                    stage = "first_panel_mount",
                    detail = "height=${height}dp",
                )
            }
            TavernRuntimeManager.appendLog("debug", "[panel] resize h=$height")
            mountState.lastReportedHeightDp = height.coerceIn(0, 2_000_000)
            onHeight(height.coerceIn(0, 2_000_000))
        }
    }

    @JavascriptInterface
    fun onInnerVScrollZones(json: String) {
        if (mountState.isDisposed) return
        mountState.updateInnerZones(json)
        if (!firstZonesLogged) {
            firstZonesLogged = true
            TavernRuntimeManager.appendLog(
                "debug",
                "[panel] inner-zones key=" + mountState.currentPendingKey() + " json=" + json.take(260),
            )
        }
    }

    @JavascriptInterface
    fun getPendingHtml(): String = if (mountState.isDisposed) "" else mountState.currentPendingHtml()

    @JavascriptInterface
    fun getPendingKey(): String = if (mountState.isDisposed) "" else mountState.currentPendingKey()

    @JavascriptInterface
    fun log(level: String, message: String) {
        if (mountState.isDisposed) return
        TavernRuntimeManager.appendLog(level, "[panel] $message")
    }

    @JavascriptInterface
    fun onPanelError(message: String) {
        if (mountState.isDisposed) return
        handler.post {
            if (!mountState.isDisposed) {
                mountState.lastError = message
            }
        }
        TavernRuntimeManager.appendLog("error", "[panel] $message")
    }
}
