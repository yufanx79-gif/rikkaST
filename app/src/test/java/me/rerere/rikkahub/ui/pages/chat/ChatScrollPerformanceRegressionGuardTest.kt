package me.rerere.rikkahub.ui.pages.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聊天页历史消息滚动与流式交互性能回归守卫。
 *
 * 锁住两类关键不变量：
 * 1. **WebView 全保活与防退化红线**：确保 v251（发消息 depth+1 不闪回原文）和
 *    P0（loading=true 面板不落入 MarkdownBlock 变代码/裸 HTML）防线不被破坏。
 * 2. **滚动/流式性能根治不变量**：防止主线程同步截大图、无条件离屏 FBO、引号着色触发
 *    MarkdownNew+Jsoup 双重解析、逐 token 调 startService()、主线程全量序列化 Settings
 *    等已知根因回退。
 */
class ChatScrollPerformanceRegressionGuardTest {

    private fun src(rel: String): String {
        val f = listOf(
            File(rel),
            File("app/$rel"),
            File("../$rel"),
            File("../app/$rel"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 $rel（cwd=${File(".").absolutePath}）")
        return f.readText()
    }

    private fun codeLines(source: String): List<String> =
        source.lines().filter {
            val t = it.trim()
            !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
        }

    // ---------------------------------------------------------------- WebView 防退化红线

    @Test
    fun `v251 防线完整保留：超过 50KB 且未命中缓存时必须返回空串加 isComputing`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/message/StRegexedText.kt")
        assertTrue(
            "StRegexedText 必须保留冷态占位返回 StRegexRenderResult(text = \"\", isComputing = true)",
            s.contains("StRegexRenderResult(text = \"\", isComputing = true)"),
        )
        assertTrue(
            "StRegexTextCache 容量应合理扩容（至少 256）以减少长会话历史楼层缓存抖动并控制堆内存上限",
            Regex("""MAX_ENTRIES\s*=\s*(256|[3-9]\d{2}|[1-9]\d{3,})""").containsMatchIn(s),
        )
    }

    @Test
    fun `P0 面板防线完整保留：loading 期间命中 HTML 面板不得落入 MarkdownBlock 渲染原文`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt")
        assertTrue(
            "StTextContent 在 loading 期间必须保留 panelCandidate 拦截分支",
            s.contains("if (loading)") && s.contains("if (panelCandidate)"),
        )
        assertFalse(
            "流式 HTML 面板预览的 previewSource 不应再用 remember(content)（会导致 120ms 防抖完全失效）",
            codeLines(s).any { it.contains("var previewSource by remember(content)") },
        )
    }

    // ---------------------------------------------------------------- 滚动与渲染性能守卫

    @Test
    fun `MessageHtmlBlock 滑出视口 onRelease 不得主线程同步截大图且必须隔离全局 Flow`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt")
        val onReleaseIdx = s.indexOf("onRelease = {")
        assertTrue("未找到 AndroidView 的 onRelease 块", onReleaseIdx >= 0)
        val onReleaseBlock = s.substring(onReleaseIdx, (onReleaseIdx + 2200).coerceAtMost(s.length))
        assertFalse(
            "onRelease（滑出视口帧）不得同步调用 PanelSnapshotStore.captureFromWebView —— 会造成滑动瞬间掉帧",
            codeLines(onReleaseBlock).any { it.contains("PanelSnapshotStore.captureFromWebView") },
        )
        assertFalse(
            "不应在每个面板直接 collect 整份 ActivePanelWebViewPool.activeKeysFlow 引发全屏连坐重组",
            codeLines(s).any { it.contains("val activePoolKeys by ActivePanelWebViewPool.activeKeysFlow.collectAsState()") },
        )
    }

    @Test
    fun `ChatMessage 无气泡模式 bareContent 必须透传 isHistoricalFloor`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt")
        val bareIdx = s.indexOf("bareContent = {")
        assertTrue("未找到 bareContent 块", bareIdx >= 0)
        val bareBlock = s.substring(bareIdx, (bareIdx + 3200).coerceAtMost(s.length))
        assertTrue(
            "bareContent 内的 StTextContent 必须透传 isHistoricalFloor = isHistoricalFloor",
            bareBlock.contains("isHistoricalFloor = isHistoricalFloor"),
        )
    }

    @Test
    fun `ChatList 必须配置 contentType 且纯色背景不得无条件挂 Offscreen 离屏合成`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt")
        assertTrue(
            "itemsIndexed 必须提供 contentType 提升 LazyColumn 复用率",
            s.contains("contentType = {"),
        )
        assertTrue(
            "topFadingEdge 必须区分自定义背景与纯色背景，避免纯色模式下每帧全屏 Offscreen FBO",
            s.contains("hasCustomBackground"),
        )
        assertTrue(
            "selectedMessages 仅应在 showExportSheet 为真时过滤映射",
            s.contains("if (showExportSheet)"),
        )
    }

    @Test
    fun `Markdown 引号着色不得强制触发 MarkdownNew 双重解析且 parseMarkdown 需有缓存`() {
        val md = src("app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt")
        val mdNew = src("app/src/main/java/me/rerere/rikkahub/ui/components/richtext/MarkdownNew.kt")
        assertTrue(
            "Markdown.kt 应通过 LocalMarkdownQuoteColor 原生着色，避免纯文本被 <span> 强行拖入 MarkdownNew",
            md.contains("LocalMarkdownQuoteColor"),
        )
        assertTrue(
            "Markdown.kt 应为 parseMarkdown 提供 LruCache 缓存",
            md.contains("markdownParseCache"),
        )
        assertFalse(
            "MarkdownNew.kt 不应在主线程 remember(html) 中同步执行 Jsoup.parse(html)",
            codeLines(mdNew).any { it.contains("runCatching { Jsoup.parse(html) }") },
        )
    }

    @Test
    fun `ChatService 停止前台服务必须带运行态守卫且 PreferencesStore 落盘必须在 IO 线程`() {
        val chatService = src("app/src/main/java/me/rerere/rikkahub/service/ChatService.kt")
        val prefStore = src("app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt")
        assertTrue(
            "ChatService 必须用运行态标志保护 stopGenerationForeground，避免流式逐 token 调 startService()",
            chatService.contains("isGenerationForegroundRunning"),
        )
        assertTrue(
            "PreferencesStore.persistSettings 必须切到 Dispatchers.IO，避免主线程全量序列化 Settings JSON",
            prefStore.contains("NonCancellable + Dispatchers.IO"),
        )
    }

    @Test
    fun `RegexScriptEngine 的 ReDoS 静态预检应按 pattern 缓存`() {
        val engine = src("app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptEngine.kt")
        assertTrue(
            "RegexScriptEngine 应对 isPotentiallyCatastrophicRegex / isDangerousVariableLookbehind 结果按 pattern 缓存",
            engine.contains("catastrophicRegexCache") && engine.contains("variableLookbehindCache"),
        )
    }

    // ---------------------------------------------------------------- 第二轮回归修复与全应用卡顿守卫

    @Test
    fun `MessageHtmlBlock 滚动隔离、停滑补抓快照与单次 PNG 编码不变量`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/ui/components/message/MessageHtmlBlock.kt")
        assertFalse(
            "MessageHtmlBlock 不应顶层裸订阅 isListScrollingFlow 导致已挂载面板起停滑全屏重组",
            codeLines(s).any { it.contains("val isListScrolling by TavernPanelLoadScheduler.isListScrollingFlow.collectAsState()") },
        )
        assertTrue(
            "面板在滑动中就绪时应挂起等待停滑再捕获快照，避免永久漏抓",
            s.contains("TavernPanelLoadScheduler.isListScrollingFlow.first { !it }"),
        )
        assertTrue(
            "PanelSnapshotStore 应复用单例 snapshotIoScope 并在 captureFromWebView 中复用同一份 PNG 字节",
            s.contains("private val snapshotIoScope =") && s.contains("writeDisk = false"),
        )
        assertTrue(
            "markRenderedOnce 必须先去重再追加写盘，isRenderedOnce 必须异步预加载不得阻塞主线程",
            s.contains("if (!renderedOnceKeys.add(contentKey)) return") &&
                s.contains("renderedOnceLoadStarted.compareAndSet(false, true)"),
        )
    }

    @Test
    fun `PreferencesStore 必须通过 Mutex 串行落盘最新值防止乱序覆盖`() {
        val s = src("app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt")
        assertTrue(
            "PreferencesStore 必须使用 persistMutex 串行化 IO 落盘并写入最新 settingsFlow.value",
            s.contains("private val persistMutex = Mutex()") && s.contains("persistMutex.withLock"),
        )
    }

    @Test
    fun `前台服务防残留、多会话计数、流式节流与单列 DAO 更新不变量`() {
        val fgs = src("app/src/main/java/me/rerere/rikkahub/service/GenerationForegroundService.kt")
        val cs = src("app/src/main/java/me/rerere/rikkahub/service/ChatService.kt")
        assertTrue(
            "GenerationForegroundService 必须返回 START_NOT_STICKY 并在 intent == null 时立即自停",
            fgs.contains("return START_NOT_STICKY") && !codeLines(fgs).any { it.contains("return START_STICKY") },
        )
        assertTrue(
            "ChatService 必须跟踪多会话活跃后台生成集合且流式发射带 50ms 节流合并",
            cs.contains("activeForegroundGenerationIds") && cs.contains("pendingUiMessages"),
        )
        assertTrue(
            "generateTitle 与 generateSuggestion 必须走单列 DAO 更新，避免全会话 message_node 全删全插",
            cs.contains("updateTitle(conversationId, newTitle)") &&
                cs.contains("conversationRepo.updateChatSuggestions(conversationId, limitedSuggestions)") &&
                cs.contains("synchronized(throttleLock)"),
        )
    }

    @Test
    fun `全应用 P0 根因守卫：头像空闲零动画、预设正则缓存、输入栏隔离与侧边栏稳定参数`() {
        val avatar = src("app/src/main/java/me/rerere/rikkahub/ui/hooks/AvatarShape.kt")
        val regexSrc = src("app/src/main/java/me/rerere/rikkahub/data/st/regex/RegexScriptSources.kt")
        val chatInput = src("app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt")
        val chatDrawer = src("app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatDrawer.kt")
        val assistantVm = src("app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/AssistantVM.kt")
        val runtime = src("app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernRuntimeManager.kt")

        assertTrue(
            "AvatarShape 在 loading == false 时必须短路返回 CircleShape，禁止常驻 InfiniteTransition",
            avatar.contains("if (!loading) return CircleShape"),
        )
        assertTrue(
            "RegexScriptSources 必须缓存解析后的 tavernPresets 正则脚本，避免每气泡重复解析大 JSON",
            regexSrc.contains("parsedPresetRegexCache"),
        )
        assertTrue(
            "ChatInput 必须用 derivedStateOf 隔离 isEmpty 并用 snapshotFlow 隔离斜杠检测且异步加载技能",
            chatInput.contains("derivedStateOf { state.isEmpty() }") &&
                chatInput.contains("snapshotFlow { state.textContent.text }") &&
                chatInput.contains("produceState("),
        )
        assertTrue(
            "ChatDrawerContent 必须接收 currentConversationId: Uuid 且列表提供稳定 key",
            chatDrawer.contains("currentConversationId: Uuid") &&
                chatDrawer.contains("items(folders, key = { it.id.toString() })") &&
                chatDrawer.contains("items(settings.assistants, key = { it.id.toString() })"),
        )
        assertTrue(
            "AssistantVM.getMemories 必须按助手缓存 Flow 避免 N+1 订阅",
            assistantVm.contains("memoriesFlowCache"),
        )
        assertTrue(
            "TavernRuntimeManager 日志必须使用 ThreadLocal 时间格式与单消费者 Channel 批量写盘",
            runtime.contains("ThreadLocal<SimpleDateFormat>") && runtime.contains("logWriteChannel"),
        )
    }

    @Test
    fun `Room 数据库 v35 复合索引与迁移不变量`() {
        val conv = src("app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt")
        val node = src("app/src/main/java/me/rerere/rikkahub/data/db/entity/MessageNodeEntity.kt")
        val db = src("app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt")
        val factory = src("app/src/main/java/me/rerere/rikkahub/data/db/AppDatabaseFactory.kt")
        val mig = src("app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_34_35.kt")
        assertTrue(conv.contains("""Index(value = ["assistant_id", "folder_id", "is_pinned", "update_at"])"""))
        assertTrue(node.contains("""Index(value = ["conversation_id", "node_index"])"""))
        assertTrue(db.contains("version = 35"))
        assertTrue(factory.contains("Migration_34_35"))
        assertTrue(mig.contains("index_ConversationEntity_assistant_id_folder_id_is_pinned_update_at"))
        assertTrue(mig.contains("index_message_node_conversation_id_node_index"))
    }

    @Test
    fun `markdownNew does not key remember on content while collecting in LaunchedEffect Unit`() {
        val src = src("src/main/java/me/rerere/rikkahub/ui/components/richtext/MarkdownNew.kt")
        assertFalse(
            "MarkdownNew 不得在 LaunchedEffect(Unit) 搭配 remember(content) 导致长消息流式更新后状态孤立白屏",
            src.contains("var document by remember(content)"),
        )
        assertTrue(src.contains("var document by remember {"))
    }

    @Test
    fun `chatService stream throttle uses monotonic System nanoTime instead of Android SystemClock`() {
        val src = src("src/main/java/me/rerere/rikkahub/service/ChatService.kt")
        assertTrue(src.contains("System.nanoTime() / 1_000_000L"))
        assertFalse(src.contains("SystemClock.uptimeMillis()"))
    }

    @Test
    fun `preferencesStore uses monotonic persistWriteSeq instead of fragile identity skip`() {
        val src = src("src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt")
        assertTrue(src.contains("persistWriteSeq"))
        assertFalse(src.contains("latest !== lastPersistedSettings"))
    }
}
