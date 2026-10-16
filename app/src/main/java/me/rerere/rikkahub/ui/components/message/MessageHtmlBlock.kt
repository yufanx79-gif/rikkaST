package me.rerere.rikkahub.ui.components.message

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
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
 * 扫描文本中的所有顶层 HTML 复合块区间 `[start, endExclusive)`（合并相邻的 `<!-- -->`/`<style>`/`<script>`/`<div>` 等标签）。
 */
private fun findInteractiveHtmlSpans(content: String): List<Pair<Int, Int>> {
    val rawSpans = mutableListOf<Pair<Int, Int>>()

    // 1. 先识别 ```html ... ``` 围栏块
    for (m in FENCED_HTML_BLOCK_RE.findAll(content)) {
        val inner = m.groupValues[1]
        if (looksLikeInteractiveHtml(inner)) {
            rawSpans += m.range.first to (m.range.last + 1)
        }
    }

    // 2. 扫描顶层 <style>/<script>/<div>/<section>/<main>/<article>/<details>/<form> 连续复合块
    val tagStartRe = Regex(
        """<!--[\s\S]*?-->|<(style|script|div|section|main|article|details|form)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    var idx = 0
    while (idx < content.length) {
        if (rawSpans.any { idx in it.first until it.second }) {
            idx = rawSpans.first { idx in it.first until it.second }.second
            continue
        }
        val m = tagStartRe.find(content, idx) ?: break
        if (rawSpans.any { m.range.first in it.first until it.second }) {
            idx = m.range.last + 1
            continue
        }
        val blockStart = m.range.first
        var blockEnd = if (m.value.startsWith("<!--")) {
            m.range.last + 1
        } else {
            val tag = m.groupValues[1].lowercase()
            val end = findBalancedTagEnd(content, m.range.first, tag)
            if (end > m.range.first) end else (m.range.last + 1)
        }

        // 继续吸收紧随其后（仅隔空白）的其他顶层 HTML 块（如 <style> 后紧跟 <div> 和 <script>）
        while (blockEnd < content.length) {
            var wsEnd = blockEnd
            while (wsEnd < content.length && content[wsEnd].isWhitespace()) wsEnd++
            val nextMatch = tagStartRe.find(content, wsEnd)
            if (nextMatch == null || nextMatch.range.first != wsEnd) break
            val nextEnd = if (nextMatch.value.startsWith("<!--")) {
                nextMatch.range.last + 1
            } else {
                val nextTag = nextMatch.groupValues[1].lowercase()
                val balanced = findBalancedTagEnd(content, nextMatch.range.first, nextTag)
                if (balanced > nextMatch.range.first) balanced else break
            }
            blockEnd = nextEnd
        }

        val candidate = content.substring(blockStart, blockEnd)
        if (looksLikeInteractiveHtml(candidate)) {
            rawSpans += blockStart to blockEnd
            idx = blockEnd
        } else {
            idx = m.range.last + 1
        }
    }

    return rawSpans.sortedBy { it.first }
}

/**
 * 将单条消息拆分为「Markdown 正文分片 + 局部 HTML 面板分片」。
 */
fun splitMixedMessagePieces(content: String): List<MixedContentPiece> {
    val trimmed = content.trim()
    if (!looksLikeInteractiveHtml(trimmed)) {
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
        if (leadingMd.isNotEmpty() && looksLikeInteractiveHtml(trailingHtml)) {
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
    data object Ready : PanelLoadStage
}

/**
 * 全局 WebView 面板串行分步加载调度器：
 * - 主运行时优先屏障：等待 `TavernRuntimeManager.isPageReady`（最多 2500ms 超时自动放行）；
 * - 串行单令牌放行：同一时刻只允许 1 个面板实例化 WebView 并加载 `panel.html`；
 * - 单面板完成后留出 180ms 帧间冷却间隔，彻底避免多个 WebView 同帧哄抢 `CrRendererMain`。
 */
internal object TavernPanelLoadScheduler {
    const val HOST_READY_WAIT_MAX_MS = 1500L
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
        markDone(key)
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
        // 主运行时优先屏障（最多等待 2500ms）
        var waited = 0L
        while (!hostReadyProvider() && waited < HOST_READY_WAIT_MAX_MS) {
            delay(50L)
            waited += 50L
        }

        while (true) {
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

            // 放行该面板创建 WebView
            next.gate.complete(Unit)

            // 等待该面板首次回报有效高度 h > 0 或超时
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
 * 面板分步加载期间的显式动态占位提示卡片（展示具体面板名称、当前阶段与「优先加载」插队按钮）。
 */
@Composable
internal fun PanelLoadingPlaceholderCard(
    stage: PanelLoadStage,
    panelTitle: String,
    onPrioritize: (() -> Unit)? = null,
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
        PanelLoadStage.Ready ->
            "「$panelTitle」加载完成"
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
            if (stage is PanelLoadStage.Queued && stage.position > 1 && onPrioritize != null) {
                TextButton(onClick = onPrioritize) {
                    Text("优先加载", style = MaterialTheme.typography.labelSmall)
                }
            }
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
    if (Regex("<(iframe|canvas|svg)[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return true
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
 * 消息内嵌 HTML/JS 面板渲染（千纱状态栏 / 文爱社区 / Vue 面板类）。
 *
 * 1) 严格遵守 `1 CSS px == 1 dp` 契约；
 * 2) 全局串行分步加载调度（[TavernPanelLoadScheduler]）：未获槽位前仅渲染轻量 [PanelLoadingPlaceholderCard]，
 *    获批槽位后才创建 WebView 实例，杜绝多面板同帧并发打爆 `CrRendererMain`；
 * 3) 解除 2666dp (`8000f / density`) 高度硬截断：默认 `unboundedHeight = true`（`safetyMaxHeightDp = 100_000`），
 *    当物理像素高度 `physicalPx > 14_000` 时自动切换 `View.LAYER_TYPE_SOFTWARE` 避免 GPU 硬件纹理超限白屏；
 * 4) 响应式 `mountState.mounted`（`mutableStateOf`）确保高度缓存命中或重挂载时内部滚动即时同步。
 */
/**
 * 计算 HTML 面板在 GPU 硬件加速纹理限制下的安全显示高度（dp）与是否启用内部滚动。
 *
 * 将容器显示高度封顶在 GPU 纹理安全高度（`minOf(1600, maxTexturePx / density)`）以内，
 * 当内容高度超出封顶值时返回 `isCappedScrollable = true` 并启用 WebView 内部滚动，
 * 避免切换 `LAYER_TYPE_SOFTWARE` 分配数十至上百 MB 的 CPU 位图引发 OOM。
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
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
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
        if (!messageKey.isNullOrEmpty()) {
            runCatching {
                TavernRuntimeManager.appendLog(
                    "debug",
                    "[panel] height cache key=$messageKey hash=$contentHash -> $cached (screenHeightDp=$screenHeightDp safetyMaxHeightDp=$safetyMaxHeightDp)",
                )
            }
        }
        mutableIntStateOf(cached)
    }
    val (computedHeightDp, computedScrollable) = computePanelHeightConstraint(
        rawContentHeightDp = heightDp,
        density = density.density,
    )
    val isCappedScrollable = computedScrollable || heightDp > safetyMaxHeightDp
    val boundedHeightDp = computedHeightDp.coerceIn(minHeightDp, safetyMaxHeightDp)

    val mountState = remember(messageKey, localCrashCount) { PanelMountState() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val foregroundHex = remember(onSurfaceColor) {
        String.format(Locale.US, "#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    }

    // 串行分步加载队列状态
    val queueKeys by TavernPanelLoadScheduler.queueSnapshot.collectAsState()
    var slotGranted by remember(effectiveQueueKey) { mutableStateOf(false) }
    var stage by remember(effectiveQueueKey) {
        mutableStateOf<PanelLoadStage>(PanelLoadStage.Queued(panelTitle, 1, 1))
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

    LaunchedEffect(effectiveQueueKey) {
        val req = TavernPanelLoadScheduler.enqueue(effectiveQueueKey, panelTitle, prioritize = false)
        req.gate.await()
        slotGranted = true
        stage = PanelLoadStage.LoadingWebView(panelTitle)
    }

    DisposableEffect(effectiveQueueKey) {
        onDispose {
            TavernPanelLoadScheduler.cancel(effectiveQueueKey)
        }
    }

    // 主运行时发生自愈重启时：对已挂载的健康面板做非破坏性状态同步；对尚未挂载成功的面板触发补挂
    LaunchedEffect(rendererGen) {
        if (rendererGen > 0) {
            mountState.onHostRendererRestarted()
        }
    }

    // 高度触顶状态或挂载状态变化时，响应式同步切换面板内部滚动能力
    LaunchedEffect(isCappedScrollable, mountState.mounted) {
        if (mountState.mounted) {
            mountState.setInternalScrollEnabled(isCappedScrollable)
        }
    }

    // 看门狗延后到获批槽位（slotGranted == true）后才启动计时（5000ms），杜绝排队等待期间被误杀重载
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

    val isPanelVisiblyReady = slotGranted && mountState.mounted && heightDp > 0

    Column(modifier = modifier.fillMaxWidth()) {
        if (!isPanelVisiblyReady) {
            PanelLoadingPlaceholderCard(
                stage = stage,
                panelTitle = panelTitle,
                onPrioritize = {
                    TavernPanelLoadScheduler.enqueue(effectiveQueueKey, panelTitle, prioritize = true)
                },
            )
        }

        if (slotGranted) {
            key(messageKey, localCrashCount) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPanelVisiblyReady) boundedHeightDp.dp else 1.dp),
                    factory = { context ->
                        TavernRuntimeManager.ensureContext(context)
                        TavernRuntimeManager.ensureStarted(context)
                        val rendererGoneHandled = AtomicBoolean(false)
                        val webView = WebView(context).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.blockNetworkImage = false
                            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                            isVerticalScrollBarEnabled = false
                            isFocusable = false
                            isFocusableInTouchMode = false
                            setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_DOWN) {
                                    if (!v.isFocusable || !v.isFocusableInTouchMode) {
                                        v.isFocusable = true
                                        v.isFocusableInTouchMode = true
                                    }
                                }
                                false
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    return TavernRuntimeManager.interceptAsset(request.url)
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (mountState.isDisposed || rendererGoneHandled.get()) return
                                    stage = PanelLoadStage.MountingDom(panelTitle)
                                    mountState.onPageReady()
                                    view.evaluateJavascript("typeof window.__rikkaPanelMount") { r ->
                                        if (mountState.isDisposed || rendererGoneHandled.get()) return@evaluateJavascript
                                        val ok = r != null && r.contains("function")
                                        if (ok) {
                                            TavernRuntimeManager.appendLog("debug", "[panel] page ready (runtime ok) key=$messageKey url=$url")
                                            mountState.tryMountIfNeeded()
                                        } else {
                                            TavernRuntimeManager.appendLog(
                                                "warn",
                                                "[panel] page finished but runtime missing (typeof=$r) key=$messageKey url=$url — asset intercept miss?",
                                            )
                                            mountState.onRuntimeMissing()
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
                                        // 始终保持 LAYER_TYPE_NONE，依靠 safetyMaxHeightDp 封顶与 WebView 内部滚动规避大纹理与软件位图 OOM
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
                                            stage = PanelLoadStage.Ready
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
                            loadUrl(TavernRuntimeManager.BASE_URL + "panel.html")
                        }
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
                    update = {
                        mountState.submit(html, foregroundHex, messageKey, screenHeightDp)
                    },
                    onRelease = {
                        TavernPanelLoadScheduler.markDone(effectiveQueueKey)
                        mountState.release()
                    },
                )
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
internal fun StTextContent(
    content: String,
    loading: Boolean,
    onClickCitation: (String) -> Unit,
    messageKey: String? = null,
    modifier: Modifier = Modifier,
) {
    if (!loading && looksLikeInteractiveHtml(content)) {
        val pieces = remember(content) { splitMixedMessagePieces(content) }
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
                        val subKey = if (piece.subIndex == 0) {
                            messageKey
                        } else {
                            "${messageKey ?: "msg"}#p${piece.subIndex}"
                        }
                        MessageHtmlBlock(
                            html = piece.html,
                            messageKey = subKey,
                            panelTitle = piece.panelTitle,
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

    private companion object {
        const val MAX_PAGE_LOAD_RETRIES = 3
        val SCRIPT_TAG_RE = Regex("<script[\\s>]", RegexOption.IGNORE_CASE)
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

    /**
     * 决策下一次 HTML 更新动作（纯状态机，供 `tryMount` 与单测直接验证）：
     * - 首次挂载、强制代次递增或新旧 HTML 任一包含 `<script` 时，返回 [PanelUpdateAction.RELOAD_DOCUMENT]；
     * - 相同 HTML 且代次未变时，返回 [PanelUpdateAction.NO_OP]；
     * - 纯 HTML/CSS 更新时，返回 [PanelUpdateAction.REMOUINT_IN_PLACE]。
     */
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
            appliedForeground = null
            appliedKey = null
            appliedViewportPx = 0
        }
    }

    fun onPageReady() {
        if (isDisposed) return
        pageReady = true
        tryMount()
    }

    fun tryMountIfNeeded() {
        if (!isDisposed && pageReady && !mounted) {
            tryMount()
        }
    }

    /** 主运行时重启后：若本面板已挂载且存活，仅同步扩展元数据与变量并重测高度，不销毁 DOM。 */
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

    /** 切换面板在达到最大安全高度时的内部垂直滚动开关，防止超长内容被截断。 */
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
        if (isDisposed) return false
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
        pendingHtml = html
        pendingHtmlHash = html.hashCode()
        pendingForeground = foregroundHex
        pendingKey = messageKey
        if (viewportPx > 0) pendingViewportPx = viewportPx
        tryMount()
    }

    fun onRendererGone() {
        webView = null
        pageReady = false
        everMounted = false
        mountedHtml = null
        appliedForeground = null
        appliedKey = null
        appliedViewportPx = 0
    }

    fun release() {
        isDisposed = true
        webView?.let { wv ->
            webView = null
            pageReady = false
            everMounted = false
            mountedHtml = null
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
    @JavascriptInterface
    fun panelResize(height: Int) {
        if (mountState.isDisposed) return
        handler.post {
            if (mountState.isDisposed) return@post
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
        TavernRuntimeManager.appendLog("error", "[panel] $message")
    }
}
