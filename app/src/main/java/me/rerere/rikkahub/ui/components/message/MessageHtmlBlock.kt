package me.rerere.rikkahub.ui.components.message

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    const val SINGLE_PANEL_TIMEOUT_MS = 2500L
    const val INTER_PANEL_COOLDOWN_MS = 60L

    data class QueueRequest(
        val key: String,
        val title: String,
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

    fun enqueue(key: String, title: String, prioritize: Boolean = false): QueueRequest {
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
            req = QueueRequest(key = key, title = title)
            if (prioritize) {
                pendingQueue.addFirst(req)
            } else {
                pendingQueue.addLast(req)
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

            withTimeoutOrNull(SINGLE_PANEL_TIMEOUT_MS) {
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
internal object ActivePanelWebViewPool {
    // 保留原有公开预算常量（既有回归测试依赖它们）；实际运行使用下方更保守的 runtime 预算。
    const val MAX_ACTIVE_PANEL_WEBVIEWS = 3
    const val LIVE_QUIESCENT_MAX_ACTIVE_WEBVIEWS = 4
    const val DEFAULT_SAFE_MAX_ACTIVE_WEBVIEWS = 6

    const val RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS = 3
    const val RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS = 2
    const val RUNTIME_LIVE_MAX_ACTIVE_WEBVIEWS = 3
    private val lock = Any()
    private val lruKeys = java.util.LinkedHashSet<String>()
    private val _activeKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    val activeKeysFlow: StateFlow<Set<String>> = _activeKeysFlow.asStateFlow()

    fun acquire(key: String, maxActive: Int = MAX_ACTIVE_PANEL_WEBVIEWS) {
        val limit = maxActive.coerceAtLeast(1)
        synchronized(lock) {
            lruKeys.remove(key)
            lruKeys.add(key)
            while (lruKeys.size > limit) {
                val oldest = lruKeys.iterator()
                if (!oldest.hasNext()) break
                val evicted = oldest.next()
                oldest.remove()
                DetachedPanelWebViewCache.evictAndDestroy(evicted)
                runCatching {
                    TavernRuntimeManager.appendLog(
                        "info",
                        "[panel-pool] evicted oldest panel WebView key=$evicted (active=${lruKeys.size}/$limit)",
                    )
                }
            }
            _activeKeysFlow.value = lruKeys.toSet()
        }
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
            if (lruKeys.remove(key)) {
                _activeKeysFlow.value = lruKeys.toSet()
            }
        }
    }

    fun activeCount(): Int = synchronized(lock) { lruKeys.size }

    fun clearAll() {
        synchronized(lock) {
            lruKeys.clear()
            _activeKeysFlow.value = emptySet()
        }
        DetachedPanelWebViewCache.clearAll()
    }

    internal fun resetForTest() {
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
    private const val MAX_DETACHED_WEBVIEWS = 2

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
        val entry = map[key] ?: return null
        if (entry.mountState.isDisposed || entry.mountState.webView == null) {
            map.remove(key)
            return null
        }
        runCatching {
            (entry.container.parent as? ViewGroup)?.removeView(entry.container)
        }
        // 与 parkContainer 的 onPause() 配对恢复该面板自身的渲染。
        runCatching { entry.mountState.webView?.onResume() }
        runCatching {
            entry.mountState.webView?.evaluateJavascript(
                "try{if(window.__rikkaPanelAwaitSettled)window.__rikkaPanelAwaitSettled();}catch(e){}",
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
            map[key] = CachedPanelEntry(key = key, container = container, mountState = mountState)
            // 离屏立即暂停该面板自身的定时器/动画/媒体，减少多个 WebView 同时占用主线程。
            //
            // 边界说明（已按官方文档核实，勿改口径）：
            // WebView.onPause() 的契约是"best-effort 暂停可安全暂停的处理"，官方明确写着
            // **does not pause JavaScript；要全局停 JS 定时器必须用 pauseTimers()**。
            // 本工程从未调用 pauseTimers()/resumeTimers()，因此这里**不会**影响同进程其他
            // WebView（包括主运行时）的 setTimeout/Promise。
            // 曾被怀疑为「上游收不到请求」的根因，但该因果链与官方文档冲突，已排除。
            runCatching { mountState.webView?.onPause() }
            while (map.size > MAX_DETACHED_WEBVIEWS) {
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
 * 计算 HTML 面板在 GPU 硬件加速纹理限制下的安全显示高度（dp）与是否启用内部滚动。
 */
internal fun computePanelHeightConstraint(
    rawContentHeightDp: Int,
    density: Float,
    maxTexturePx: Int = 4096,
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
    val useBoundedWebViewPool = true
    val useHibernatePool = perfEnabled && perfMode == WebViewPerfMode.HIBERNATE_POOL
    val useLiveQuiescent = !perfEnabled || perfMode == WebViewPerfMode.LIVE_QUIESCENT
    val useHistoricalSnapshot = perfEnabled && perfMode == WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE
    val maxActivePoolSize = when {
        !perfEnabled -> ActivePanelWebViewPool.RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS
        useHibernatePool -> ActivePanelWebViewPool.RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS
        useLiveQuiescent -> ActivePanelWebViewPool.RUNTIME_LIVE_MAX_ACTIVE_WEBVIEWS
        else -> ActivePanelWebViewPool.RUNTIME_HIBERNATE_MAX_ACTIVE_WEBVIEWS
    }
    val screenHeightDp = configuration.screenHeightDp.coerceAtLeast(480)
    val minHeightDp = 48
    val gpuSafeMaxDp = minOf(1600, (4096f / density.density.coerceAtLeast(1f)).toInt()).coerceAtLeast(480)
    val safetyMaxHeightDp = if (unboundedHeight) {
        gpuSafeMaxDp
    } else {
        minOf(screenHeightDp * 2, gpuSafeMaxDp).coerceAtLeast(480)
    }

    val rendererGen by TavernRuntimeManager.rendererGeneration.collectAsState()
    var localCrashCount by remember(messageKey) { mutableIntStateOf(0) }
    val contentHash = remember(html) { html.hashCode() }
    val effectiveQueueKey = remember(messageKey, contentHash, localCrashCount) {
        "${messageKey ?: "anon"}#$contentHash#$localCrashCount"
    }

    var heightDp by remember(messageKey, contentHash) {
        val cached = PanelHeightCache.get(messageKey, contentHash)
        mutableIntStateOf(cached)
    }
    val (computedHeightDp, computedScrollable) = computePanelHeightConstraint(
        rawContentHeightDp = heightDp,
        density = density.density,
    )
    val isCappedScrollable = computedScrollable || heightDp > safetyMaxHeightDp
    val boundedHeightDp = computedHeightDp.coerceIn(minHeightDp, safetyMaxHeightDp)

    val mountState = remember(effectiveQueueKey) {
        DetachedPanelWebViewCache.getMountState(effectiveQueueKey) ?: PanelMountState()
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val foregroundHex = remember(onSurfaceColor) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    }

    // 串行分步加载队列状态：若 DetachedPanelWebViewCache 已有存活实例，则直接放行（0ms 恢复，不重新排队渲染）
    val queueKeys by TavernPanelLoadScheduler.queueSnapshot.collectAsState()
    var slotGranted by remember(effectiveQueueKey) {
        mutableStateOf(DetachedPanelWebViewCache.has(effectiveQueueKey))
    }
    var stage by remember(effectiveQueueKey) {
        mutableStateOf<PanelLoadStage>(
            if (DetachedPanelWebViewCache.has(effectiveQueueKey)) {
                PanelLoadStage.ResourcesLoading(panelTitle)
            } else {
                PanelLoadStage.Queued(panelTitle, 1, 1)
            }
        )
    }

    LaunchedEffect(queueKeys, slotGranted, panelTitle) {
        if (!slotGranted) {
            val pos = (queueKeys.indexOf(effectiveQueueKey) + 1).coerceAtLeast(1)
            val total = queueKeys.size.coerceAtLeast(pos)
            stage = if (!TavernRuntimeManager.isPageReady && pos == 1) {
                PanelLoadStage.WaitingHost(panelTitle)
            } else {
                PanelLoadStage.Queued(panelTitle, pos, total)
            }
        }
    }

    val snapshotCompositeKey = remember(messageKey, contentHash) {
        PanelSnapshotStore.compositeKey(messageKey, contentHash)
    }
    var snapshotEntry by remember(snapshotCompositeKey) {
        mutableStateOf(PanelSnapshotStore.getMemory(snapshotCompositeKey))
    }

    LaunchedEffect(snapshotCompositeKey, useHistoricalSnapshot) {
        if (useHistoricalSnapshot && snapshotEntry == null && snapshotCompositeKey != null) {
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

    // 方案三（历史楼层固化为图片）：
    // 使用等比例缩放快照与 `.fillMaxWidth().aspectRatio(snap.aspectRatio)` 渲染，
    // 100% 保持原面板真实宽高比，彻底杜绝长图被上下挤压导致人物脸部变形！
    if (useHistoricalSnapshot && isHistoricalFloor && snapshotEntry != null) {
        val snap = snapshotEntry!!
        val safeAspectRatio = snap.aspectRatio.coerceIn(0.05f, 20f)
        Image(
            bitmap = snap.imageBitmap,
            contentDescription = panelTitle,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(safeAspectRatio)
                .clip(RoundedCornerShape(8.dp)),
        )
        return
    }

    val activePoolKeys by ActivePanelWebViewPool.activeKeysFlow.collectAsState()

    LaunchedEffect(effectiveQueueKey, useBoundedWebViewPool, maxActivePoolSize) {
        if (!slotGranted) {
            val req = TavernPanelLoadScheduler.enqueue(effectiveQueueKey, panelTitle, prioritize = false)
            req.gate.await()
        }
        kotlinx.coroutines.currentCoroutineContext().let { ctx ->
            if (!ctx[Job]?.isActive.let { it ?: true } || mountState.isDisposed) return@LaunchedEffect
        }
        if (useBoundedWebViewPool) {
            ActivePanelWebViewPool.acquire(effectiveQueueKey, maxActive = maxActivePoolSize)
        }
        slotGranted = true
        if (!mountState.mounted) {
            stage = PanelLoadStage.LoadingWebView(panelTitle)
        } else {
            stage = if (mountState.resourcesSettled) {
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

    // 仅对真正拿到渲染槽位的面板预取外链图片，离屏/排队面板不抢网络。
    LaunchedEffect(slotGranted, contentHash) {
        if (slotGranted) {
            TavernResourceRoute.prefetchImages(context, html)
        }
    }

    LaunchedEffect(mountState.mounted, mountState.resourcesSettled, mountState.resourcesTimedOut, heightDp) {
        if (mountState.mounted && heightDp > 0) {
            stage = if (mountState.resourcesSettled) {
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
    val isHibernated = useBoundedWebViewPool && slotGranted && effectiveQueueKey !in activePoolKeys
    val isPanelVisiblyReady = slotGranted && !isHibernated && mountState.mounted && heightDp > 0

    // 面板完成渲染后：
    // 1. 始终在后台预捕获一张等比例高清快照存入 PanelSnapshotStore（无论当前处于方案一、二还是三），
    //    这样用户在偏好设置里切换到方案三时直接秒切静态图，完全无需重新渲染 WebView！
    // 2. 若开启方案二（全视觉保活+静默降频），自动静默后台轮询定时器。
    LaunchedEffect(isPanelVisiblyReady, heightDp, mountState.resourcesSettled, useLiveQuiescent, useHistoricalSnapshot, isHistoricalFloor) {
        if (!isPanelVisiblyReady || heightDp <= 0) return@LaunchedEffect
        delay(420L)
        if (!mountState.isDisposed && mountState.mounted) {
            if (useHistoricalSnapshot && mountState.resourcesSettled && snapshotEntry == null && snapshotCompositeKey != null) {
                val captured = PanelSnapshotStore.captureFromWebView(
                    context = context,
                    compositeKey = snapshotCompositeKey,
                    webView = mountState.webView,
                    heightDp = boundedHeightDp,
                )
                if (captured != null) {
                    snapshotEntry = captured
                    if (isHistoricalFloor) {
                        ActivePanelWebViewPool.release(effectiveQueueKey)
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
            PanelHibernatedPlaceholderCard(
                panelTitle = panelTitle,
                onWakeUp = {
                    stage = PanelLoadStage.LoadingWebView(panelTitle)
                    ActivePanelWebViewPool.acquire(effectiveQueueKey, maxActive = maxActivePoolSize)
                },
            )
        } else if (!isPanelVisiblyReady) {
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
                        .height(if (isPanelVisiblyReady) boundedHeightDp.dp else 1.dp),
                    factory = { ctx ->
                        TavernRuntimeManager.ensureContext(ctx)
                        TavernRuntimeManager.ensureStarted(ctx)
                        // 优先从离屏保活池取回已渲染好的容器，杜绝切换页面或切换方案时重新创建 WebView
                        val cachedEntry = DetachedPanelWebViewCache.takeContainer(effectiveQueueKey)
                        if (cachedEntry != null) {
                            cachedEntry.mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                            val cachedHeight = PanelHeightCache.get(messageKey, contentHash)
                            if (cachedHeight > 0) {
                                heightDp = cachedHeight
                                // 缓存高度只能用于占位，必须重新等本轮 resources_settled；
                                // 否则缓存复挂载会瞬间显示“加载完成”，实际图片还没回来。
                                stage = if (cachedEntry.mountState.resourcesSettled) {
                                    PanelLoadStage.Ready
                                } else {
                                    PanelLoadStage.ResourcesLoading(panelTitle)
                                }
                                TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                            }
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
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            isVerticalScrollBarEnabled = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            var firstInteractionLogged = false
                            setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_DOWN) {
                                    ActivePanelWebViewPool.touch(effectiveQueueKey)
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
                                        heightDp = h
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
                            mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
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
                    update = {
                        mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                    },
                    onRelease = { container ->
                        TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                        if (useBoundedWebViewPool && effectiveQueueKey !in activePoolKeys) {
                            DetachedPanelWebViewCache.evictAndDestroy(effectiveQueueKey)
                            mountState.release()
                        } else if (mountState.mounted && !mountState.isDisposed) {
                            DetachedPanelWebViewCache.parkContainer(effectiveQueueKey, container, mountState)
                        } else {
                            mountState.release()
                        }
                    },
                )
                if (isPanelVisiblyReady) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            !mountState.resourcesSettled -> {
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
    val panelCandidate = shouldRenderAsHtmlPanel(html)
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
            var previewSource by remember(content) { mutableStateOf(content) }
            LaunchedEffect(content) {
                delay(120)
                previewSource = content
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

    private fun safeFileName(compositeKey: String): String {
        val sanitized = compositeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        val hashHex = Integer.toHexString(compositeKey.hashCode())
        return "snap_${sanitized}_$hashHex.png"
    }

    fun getMemory(compositeKey: String?): SnapshotEntry? {
        if (compositeKey.isNullOrEmpty()) return null
        return synchronized(memoryMap) { memoryMap[compositeKey] }
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
                )
                putMemory(compositeKey, entry)
                entry
            }.getOrNull()
        }
    }

    fun captureFromWebView(
        context: Context,
        compositeKey: String?,
        webView: WebView?,
        heightDp: Int,
    ): SnapshotEntry? {
        if (compositeKey.isNullOrEmpty() || webView == null || heightDp <= 0) return null
        val w = webView.width
        val h = webView.height
        if (w < 16 || h < 16) return null
        return runCatching {
            val maxDimW = MAX_SNAPSHOT_WIDTH.toFloat()
            val maxDimH = MAX_SNAPSHOT_HEIGHT.toFloat()
            // 强制使用单一等比例缩放因子 uniformScale，保证水平与垂直缩放率 100% 相同，绝不压扁长图！
            val uniformScale = minOf(1f, maxDimW / w.toFloat(), maxDimH / h.toFloat()).coerceAtLeast(0.1f)
            val targetW = (w * uniformScale).toInt().coerceAtLeast(16)
            val targetH = (h * uniformScale).toInt().coerceAtLeast(16)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (uniformScale != 1f) {
                canvas.scale(uniformScale, uniformScale)
            }
            webView.draw(canvas)
            val ratio = (targetW.toFloat() / targetH.toFloat()).coerceIn(0.05f, 20f)
            val entry = SnapshotEntry(
                imageBitmap = bmp.asImageBitmap(),
                heightDp = heightDp,
                aspectRatio = ratio,
            )
            putMemory(compositeKey, entry)
            val appCtx = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val dir = File(appCtx.filesDir, "panel_snapshots").apply { mkdirs() }
                    val outFile = File(dir, safeFileName(compositeKey))
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
                    }
                }
            }
            entry
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

private class PanelScrollBridge(context: Context) : FrameLayout(context) {
    var verticalScrollProbe: (() -> Boolean)? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && verticalScrollProbe?.invoke() == false) {
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

    fun markResourcesSettled(timedOut: Boolean) {
        if (isDisposed || !mounted) return
        resourcesTimedOut = timedOut
        resourcesSettled = true
    }

    private fun resetResourceSettleState() {
        resourcesSettled = false
        resourcesTimedOut = false
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

@Keep
private class PanelBridge(
    private val handler: Handler,
    private val mountState: PanelMountState,
    private val onHeight: (Int) -> Unit,
) {
    private var firstMountLogged = false

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
            onHeight(height.coerceIn(0, 2_000_000))
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
