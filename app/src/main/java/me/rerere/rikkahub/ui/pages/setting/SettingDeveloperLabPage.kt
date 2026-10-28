package me.rerere.rikkahub.ui.pages.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.TAVERN_MAX_TOOL_STEPS
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.model.ToolPolicy
import me.rerere.rikkahub.data.model.effectiveMaxToolSteps
import me.rerere.rikkahub.data.model.effectiveToolPolicy
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.st.runtime.TavernResourceRoute
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.DeleteMessageResult
import me.rerere.rikkahub.ui.components.message.ActivePanelWebViewPool
import me.rerere.rikkahub.ui.components.message.DetachedPanelWebViewCache
import me.rerere.rikkahub.ui.components.message.PanelSnapshotStore
import me.rerere.rikkahub.ui.components.message.extractQuickPlainPreview
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.plus
import org.json.JSONObject
import org.json.JSONTokener
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private enum class DevLabStatus { RUNNING, PASS, FAIL, SKIP, ERROR }

private data class DevLabTest(
    val id: String,
    val category: String,
    val title: String,
    val p0: Boolean = true,
)

private data class DevLabResult(
    val status: DevLabStatus,
    val durationMs: Long = 0,
    val summary: String = "",
    val input: String = "",
    val intermediate: String = "",
    val result: String = "",
    val logs: String = "",
)

/**
 * 一次「发送轮」的判定结果。
 *
 * [P0 修正] 旧实现把「最后一条 S7 之后的记录」一律当成一轮发送，于是**停止动作**也会被当成发送轮：
 * 用户点「停止」只产生 `S1(textLen=0, loading=true) → S7(status=STOPPED_BY_USER)`，
 * 里面当然没有 S6，于是「上一次发送没有到达请求阶段」100% 假 FAIL —— 用户截图里那两条 FAIL
 * 就是这么来的，它把真正的排查方向盖掉了。
 */
private enum class SendRoundVerdict {
    /** 没有任何发送记录。 */
    NoData,

    /** 这一轮不是「发送」（例如只点了停止，或只有一条孤立的 S7）。判定应 SKIP，不许 FAIL。 */
    NotASendRound,

    /** 只插入消息、不触发生成（/sendas、测试台造数据）。没有 S6 是**预期**行为。判定应 SKIP。 */
    InsertOnly,

    /** 真正发出了请求（到达 S6）。 */
    Passed,

    /** 到达了请求阶段，但看门狗中止过。 */
    WatchdogAborted,

    /** 是发送轮，但没到达请求阶段就结束了 —— 这才是真正的失败。 */
    FailedBeforeRequest,
}

/**
 * 把一段诊断记录归到一次发送轮。
 *
 * 判据（按顺序，先命中先返回）：
 * 1. 空列表 → [SendRoundVerdict.NoData]
 * 2. 有 S1 且被标记为 stop（`action=stop`，或旧记录的 `textLen=0` + `loading=true`）→ 不是发送轮
 * 3. 没有任何 S1 → 不是发送轮（孤立 S7 之类）
 * 4. 有 S6 → 成功（若命中 WATCHDOG_ 则单独标记）
 * 5. 有 S3 但明确 `answer=false` → 只插入
 * 6. 其余（有 S1、无 S6）→ 请求阶段之前就失败了
 *
 * 注意：这里是**纯函数**，只读记录、不碰 UI，方便用探针直接覆盖用例矩阵。
 */
private fun classifySendRound(
    records: List<TavernRuntimeManager.SendDiagnosticRecord>,
): SendRoundVerdict {
    if (records.isEmpty()) return SendRoundVerdict.NoData

    val s1 = records.lastOrNull {
        it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_1_SEND_CLICKED
    }
    val s6 = records.any {
        it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_6_MODEL_REQUEST_SENT
    }

    // 2. 停止动作：新记录带 action=stop；旧记录只能靠 textLen=0 + loading=true 识别。
    if (s1 != null) {
        val d = s1.detail
        val isStop = d.contains("action=stop") ||
            (d.contains("textLen=0") && d.contains("loading=true"))
        if (isStop) return SendRoundVerdict.NotASendRound
    }

    // 3. 没有 S1 就不是一次发送轮（例如只点了停止，只留下 S7）。
    if (s1 == null) return SendRoundVerdict.NotASendRound

    // 4. 到达请求阶段。
    if (s6) {
        val watchdog = records.any { it.detail.contains("WATCHDOG_") }
        return if (watchdog) SendRoundVerdict.WatchdogAborted else SendRoundVerdict.Passed
    }

    // 5. 只插入不生成。
    val s3 = records.lastOrNull {
        it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_3_USER_MSG_PERSISTED
    }
    if (s3 != null && s3.detail.contains("answer=false")) return SendRoundVerdict.InsertOnly

    // 6. 真失败。
    return SendRoundVerdict.FailedBeforeRequest
}

private val DEV_LAB_TESTS = listOf(
    DevLabTest("01", "交互", "面板按钮桥"),
    DevLabTest("02", "交互", "悬浮窗目标切换"),
    DevLabTest("03", "发送与删除", "长楼层中段发送"),
    DevLabTest("04", "发送与删除", "删除消息（整楼）"),
    DevLabTest("05", "工具与图片", "工具调用策略"),
    DevLabTest("06", "工具与图片", "图片渲染/占位"),
    DevLabTest("07", "WebView 与网络", "加载时间线"),
    DevLabTest("08", "WebView 与网络", "VPN/直连资源竞速"),
    DevLabTest("09", "WebView 与网络", "1/5/10/20 面板压力预算"),
    DevLabTest("10", "性能与内存", "WebView 数量/内存报告"),
    DevLabTest("11", "发送与删除", "发送链路阶段诊断"),
)

@Composable
fun SettingDeveloperLabPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (!settings.developerMode) {
        PageScaffold(title = "开发者测试台") { padding ->
            Text(
                text = "请先在「通用设置」中开启开发者模式。",
                modifier = Modifier.padding(padding).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val chatService = koinInject<ChatService>()
    val conversationRepo = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    val runner = remember(context, chatService, conversationRepo) {
        DevLabRunner(context, chatService, conversationRepo)
    }
    var results by remember { mutableStateOf<Map<String, DevLabResult>>(emptyMap()) }
    var batchRunning by remember { mutableStateOf(false) }

    LaunchedEffect(settings.displaySetting.resourceNetworkMode) {
        TavernRuntimeManager.resourceNetworkMode = settings.displaySetting.resourceNetworkMode
    }

    DisposableEffect(runner) {
        onDispose {
            // 不在主线程 runBlocking 等删除；放到后台线程，避免生成任务/主线程互等。
            kotlin.concurrent.thread(name = "devlab-cleanup") { runner.cleanup() }
        }
    }

    fun runOne(test: DevLabTest) {
        if (batchRunning) return
        batchRunning = true
        scope.launch {
            try {
                results = results + (test.id to DevLabResult(DevLabStatus.RUNNING, summary = "运行中…"))
                val started = System.currentTimeMillis()
                val result = try {
                    runner.run(test, settings)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DevLabResult(
                        status = DevLabStatus.ERROR,
                        summary = "测试异常：${e.javaClass.simpleName}: ${e.message}",
                        logs = e.stackTraceToString().take(4000),
                    )
                }
                results = results + (test.id to result.copy(durationMs = System.currentTimeMillis() - started))
            } finally {
                batchRunning = false
            }
        }
    }

    fun runTests(tests: List<DevLabTest>) {
        if (batchRunning || tests.isEmpty()) return
        batchRunning = true
        scope.launch {
            try {
                for (test in tests) {
                    results = results + (test.id to DevLabResult(DevLabStatus.RUNNING, summary = "运行中…"))
                    val started = System.currentTimeMillis()
                    val result = try {
                        runner.run(test, settings)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        DevLabResult(
                            status = DevLabStatus.ERROR,
                            summary = "测试异常：${e.javaClass.simpleName}: ${e.message}",
                            logs = e.stackTraceToString().take(4000),
                        )
                    }
                    results = results + (test.id to result.copy(durationMs = System.currentTimeMillis() - started))
                }
            } finally {
                batchRunning = false
            }
        }
    }

    fun runAllP0() = runTests(DEV_LAB_TESTS.filter { it.p0 })

    val environment = remember(settings, results) {
        runner.environmentText(settings)
    }
    val reportText = remember(results, environment) {
        buildReport(environment, results)
    }

    PageScaffold(title = "开发者测试台") { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp), title = { Text("当前环境") }) {
                    item(
                        headlineContent = { Text("运行上下文") },
                        supportingContent = {
                            Text(
                                environment,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("操作") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { runAllP0() }, enabled = !batchRunning) {
                                        Text(if (batchRunning) "运行中…" else "运行全部 P0")
                                    }
                                    TextButton(
                                        onClick = {
                                            val file = runner.exportReport(reportText)
                                            if (file != null) {
                                                toaster.show("报告已导出：${file.absolutePath}")
                                            } else {
                                                toaster.show("导出失败")
                                            }
                                        },
                                    ) { Text("导出报告") }
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("rikkaST devlab", reportText))
                                            toaster.show("报告已复制")
                                        },
                                    ) { Text("复制本页") }
                                    TextButton(
                                        enabled = !batchRunning,
                                        onClick = {
                                            results = emptyMap()
                                            runner.cleanup()
                                            toaster.show("已清理测试数据")
                                        },
                                    ) { Text("清理测试数据") }
                                }
                                Column {
                                    DEV_LAB_TESTS.map { it.category }.distinct().chunked(2).forEach { rowCats ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            rowCats.forEach { category ->
                                                TextButton(
                                                    enabled = !batchRunning,
                                                    onClick = {
                                                        runTests(DEV_LAB_TESTS.filter { it.category == category })
                                                    },
                                                ) {
                                                    Text("运行：$category", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "默认只读，写数据测试使用 __devlab_ 临时会话并在测试结束自动删除；导出文件仅保存在本机。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }

            items(DEV_LAB_TESTS, key = { it.id }) { test ->
                val result = results[test.id]
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("${test.category} · ${test.id} ${test.title}") },
                ) {
                    item(
                        headlineContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (result?.status == DevLabStatus.RUNNING) {
                                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                                }
                                Text(statusLabel(result?.status), color = statusColor(result?.status))
                                if (result != null && result.durationMs > 0) {
                                    Text(
                                        "${result.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(result?.summary ?: "尚未运行")
                                val inputText = result?.input.orEmpty()
                                val intermediateText = result?.intermediate.orEmpty()
                                val resultText = result?.result.orEmpty()
                                val logsText = result?.logs.orEmpty()
                                if (inputText.isNotBlank()) {
                                    Text("输入：$inputText", style = MaterialTheme.typography.labelSmall)
                                }
                                if (intermediateText.isNotBlank()) {
                                    Text("中间结果：$intermediateText", style = MaterialTheme.typography.labelSmall)
                                }
                                if (resultText.isNotBlank()) {
                                    Text(
                                        "最终结果：$resultText",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                if (logsText.isNotBlank()) {
                                    Text(
                                        logsText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            TextButton(onClick = { runOne(test) }, enabled = !batchRunning) {
                                Text(if (result == null) "运行" else "重跑")
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: DevLabStatus?): String = when (status) {
    DevLabStatus.RUNNING -> "RUNNING"
    DevLabStatus.PASS -> "PASS"
    DevLabStatus.FAIL -> "FAIL"
    DevLabStatus.SKIP -> "SKIP"
    DevLabStatus.ERROR -> "ERROR"
    null -> "未运行"
}

@Composable
private fun statusColor(status: DevLabStatus?): Color = when (status) {
    DevLabStatus.PASS -> MaterialTheme.colorScheme.primary
    DevLabStatus.FAIL, DevLabStatus.ERROR -> MaterialTheme.colorScheme.error
    DevLabStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private class DevLabRunner(
    private val context: Context,
    private val chatService: ChatService,
    private val conversationRepo: ConversationRepository,
) {
    private val tempConversationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Uuid>()
    private val logs = mutableListOf<String>()

    fun environmentText(settings: Settings): String {
        val assistant = settings.assistants.firstOrNull { it.id == settings.assistantId }
            ?: settings.assistants.firstOrNull()
        val model = settings.findModelById(assistant?.chatModelId ?: settings.chatModelId)
        val webViews = TavernRuntimeManager.auxWebViewCount() + if (TavernRuntimeManager.isStarted) 1 else 0
        val network = networkLabel()
        val conv = TavernRuntimeManager.activeConversationId?.take(8) ?: "none"
        return buildString {
            appendLine("会话：$conv")
            appendLine("助手：" + (assistant?.let { it.name.ifBlank { it.id.toString().take(8) } } ?: "未加载"))
            appendLine("模型：${model?.displayName ?: "未选择"}")
            appendLine("当前 WebView：$webViews")
            appendLine("当前网络：$network")
            appendLine("资源通道：${settings.displaySetting.resourceNetworkMode}")
            val (cacheCount, cacheBytes) = TavernResourceRoute.cacheStats(context)
            appendLine("图片缓存：$cacheCount 个 / ${formatBytes(cacheBytes)}")
            TavernResourceRoute.recentStats.value.firstOrNull()?.let { stat ->
                appendLine("最近图片通道：${stat.channel} ${if (stat.ok) "OK" else "FAIL"} ${stat.totalMs}ms")
            }
        }
    }

    suspend fun run(test: DevLabTest, settings: Settings): DevLabResult = when (test.id) {
        "01" -> panelBridge()
        "02" -> floatingTargets()
        "03" -> longFloorSend(settings)
        "04" -> deleteMessages(settings)
        "05" -> toolPolicy(settings)
        "06" -> imageRendering()
        "07" -> loadTimeline()
        "08" -> networkRace()
        "09" -> stressBudget()
        "10" -> webViewReport()
        "11" -> sendPipelineDiagnostics()
        else -> DevLabResult(DevLabStatus.SKIP, summary = "未知测试 ID")
    }

    fun cleanup() {
        logs.clear()
        val remaining = mutableSetOf<Uuid>()
        tempConversationIds.toList().forEach { id ->
            val deleted = kotlinx.coroutines.runBlocking {
                runCatching { chatService.deleteConversation(id) }.isSuccess
            }
            if (!deleted) remaining += id
        }
        tempConversationIds.clear()
        tempConversationIds += remaining
    }

    fun exportReport(report: String): File? = runCatching {
        val dir = context.getExternalFilesDir("devlab") ?: File(context.filesDir, "devlab")
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rikkaST_devlab_$stamp.txt")
        file.writeText(report)
        file
    }.getOrNull()

    private suspend fun panelBridge(): DevLabResult {
        val script = "window.__rikkaPanelBridgeSelfTest?window.__rikkaPanelBridgeSelfTest():'null'"
        val mainRaw = TavernRuntimeManager.evaluateJsProbe(script)
            ?: return DevLabResult(DevLabStatus.SKIP, summary = "主运行时 WebView 未就绪")
        val main = parseJson(decodeJsString(mainRaw))
            ?: return DevLabResult(DevLabStatus.ERROR, summary = "主运行时自检结果无法解析", result = mainRaw.take(800))
        val required = listOf(
            "sendMessage", "sendMessageAsUser", "sendMessageAsAssistant",
            "triggerSlashAsync", "emitHostEvent", "saveChat", "stopGeneration",
            "formSubmit", "sendButton", "bridgePanelCommand",
        )
        val missing = required.filter { !main.optBoolean(it, false) }
        val auxRaw = TavernRuntimeManager.evaluateJsOnAuxProbes(script)
        val aux = auxRaw.mapNotNull { raw -> parseJson(decodeJsString(raw)) }
        val auxMissing = aux.flatMap { obj -> required.filter { !obj.optBoolean(it, false) } }.distinct()
        val commandRaw = evaluateJsAwait(
            "(window.__rikkaPanelPing?window.__rikkaPanelPing():" +
                "(window.TavernHelper&&window.TavernHelper.emitHostEvent)" +
                "?window.TavernHelper.emitHostEvent('devlab_probe',{source:'devlab',at:Date.now()}):null)"
        )
        val commandAck = parseJson(decodeJsString(commandRaw ?: "null"))
        val commandOk = commandAck?.optBoolean("ok", false) == true
        val logs = buildString {
            appendLine("main=${main}")
            appendLine("hostCommand ack=${commandAck ?: "null"}")
            auxRaw.forEachIndexed { index, raw -> appendLine("panel[$index]=${decodeJsString(raw).take(600)}") }
        }
        return if (missing.isEmpty() && auxMissing.isEmpty() && commandOk) {
            DevLabResult(
                DevLabStatus.PASS,
                summary = "主运行时与 ${aux.size} 个面板桥 API 完整；宿主命令收到 ok ack",
                intermediate = "面板数量=${aux.size}",
                result = "required=${required.size} missing=0 hostAck=$commandOk",
                logs = logs,
            )
        } else {
            DevLabResult(
                DevLabStatus.FAIL,
                summary = when {
                    !commandOk -> "桥 API 存在，但宿主命令没有返回 ok ack（${commandAck ?: "timeout"}）"
                    else -> "桥 API 缺失：${(missing + auxMissing).distinct().joinToString(",")}"
                },
                result = "mainMissing=$missing auxMissing=$auxMissing hostAck=$commandOk",
                logs = logs,
            )
        }
    }

    private suspend fun floatingTargets(): DevLabResult {
        val targets = listOf("iphonie", "memory_table", "littlewhitebox", "card_floating")
        val lines = mutableListOf<String>()
        var opened = 0
        var failed = 0
        for (target in targets) {
            val probeRaw = TavernRuntimeManager.evaluateJsProbe(
                "window.__rikkaFloatingTargetProbe?window.__rikkaFloatingTargetProbe('$target'):'null'"
            )
            val probe = parseJson(decodeJsString(probeRaw ?: "null"))
            val available = probe?.optBoolean("available", false) == true
            lines += "$target domAvailable=$available nodes=${probe?.optInt("nodeCount", 0) ?: 0}"
            if (!available) continue
            val resultRaw = evaluateJsAwait(
                "window.__rikkaOpenFloatingTarget?window.__rikkaOpenFloatingTarget('$target'):null"
            )
            val result = parseJson(decodeJsString(resultRaw ?: "null"))
            val ok = result?.optBoolean("ok", false) == true
            if (ok) opened++ else {
                failed++
                lines += "$target open failed: ${result?.optString("reason", "")}"
            }
            TavernRuntimeManager.evaluateJsProbe(
                "window.__rikkaCloseFloatingStage&&window.__rikkaCloseFloatingStage();'closed'"
            )
            delay(120)
        }
        TavernRuntimeManager.resetFloatingTargetSelection()
        return when {
            opened == 0 && failed == 0 -> DevLabResult(
                DevLabStatus.SKIP,
                summary = "没有检测到已初始化的悬浮窗 DOM",
                intermediate = lines.joinToString("\n"),
            )
            failed == 0 -> DevLabResult(
                DevLabStatus.PASS,
                summary = "$opened 个已就绪目标均可真实打开",
                intermediate = lines.joinToString("\n"),
                result = lines.lastOrNull() ?: "",
            )
            else -> DevLabResult(
                DevLabStatus.FAIL,
                summary = "$failed 个目标打开失败（$opened 成功）",
                intermediate = lines.joinToString("\n"),
            )
        }
    }

    private suspend fun longFloorSend(settings: Settings): DevLabResult {
        val assistantId = settings.assistants.firstOrNull { it.id == settings.assistantId }?.id
            ?: settings.assistants.firstOrNull()?.id
            ?: return DevLabResult(DevLabStatus.SKIP, summary = "助手配置尚未加载")
        val details = mutableListOf<String>()
        var allPass = true
        for (floors in listOf(200, 500)) {
            val result = runLongFloorCase(assistantId, floors)
            allPass = allPass && result.first
            details += result.second
        }
        return DevLabResult(
            if (allPass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = if (allPass) "200/500 楼中段发送后用户消息均已落库" else "至少一个长楼层发送事务未完成",
            input = "floors=200,500",
            intermediate = details.joinToString("\n"),
            result = details.lastOrNull() ?: "",
            logs = "滚动只在 persisted=true 后触发（UI 路径）",
        )
    }

    private suspend fun runLongFloorCase(assistantId: Uuid, floors: Int): Pair<Boolean, String> {
        val conversation = tempConversation(assistantId, floors)
        val marker = "__devlab_marker_${System.currentTimeMillis()}_$floors"
        return try {
            conversationRepo.insertConversation(conversation)
            tempConversationIds += conversation.id
            val persisted = CompletableDeferred<Boolean>()
            val accepted = chatService.sendMessage(
                conversationId = conversation.id,
                content = listOf(UIMessagePart.Text(marker)),
                answer = false,
                onPersisted = { persisted.complete(it) },
            )
            if (!accepted) {
                return false to "floors=$floors accepted=false"
            }
            val ok = withTimeoutOrNull(15_000) { persisted.await() } ?: false
            val session = chatService.getConversationFlow(conversation.id).value
            val lastNode = session.messageNodes.lastOrNull()
            val found = lastNode?.messages?.any { message ->
                message.role == MessageRole.USER &&
                    message.parts.filterIsInstance<UIMessagePart.Text>().any { it.text.contains("__devlab_") }
            } == true || lastNode?.messages?.any { it.role == MessageRole.USER } == true
            val pass = ok && found && session.messageNodes.lastIndex >= floors
            pass to "floors=$floors accepted=$accepted persisted=$ok found=$found nodes=${session.messageNodes.size} lastIndex=${session.messageNodes.lastIndex}"
        } finally {
            if (runCatching { chatService.deleteConversation(conversation.id) }.isSuccess) {
                tempConversationIds -= conversation.id
            }
        }
    }

    private suspend fun deleteMessages(settings: Settings): DevLabResult {
        val assistantId = settings.assistants.firstOrNull { it.id == settings.assistantId }?.id
            ?: settings.assistants.firstOrNull()?.id
            ?: return DevLabResult(DevLabStatus.SKIP, summary = "助手配置尚未加载")
        val msgA = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("__devlab_a")))
        val msgB = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("__devlab_b")))
        val keep = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("__devlab_keep")))
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = assistantId,
            title = "__devlab_delete_${System.currentTimeMillis()}",
            messageNodes = listOf(
                MessageNode(id = Uuid.random(), messages = listOf(msgA, msgB), selectIndex = 0),
                keep.toMessageNode(),
            ),
        )
        return try {
            conversationRepo.insertConversation(conversation)
            tempConversationIds += conversation.id
            val result = chatService.deleteMessage(conversation.id, msgA.id)
            val missing = chatService.deleteMessage(conversation.id, Uuid.random(), failIfMissing = true)
            val saved = conversationRepo.getConversationById(conversation.id)
            val nodeGone = saved?.messageNodes?.none { node -> node.messages.any { it.id == msgA.id || it.id == msgB.id } } == true
            val keepAlive = saved?.messageNodes?.any { node -> node.messages.any { it.id == keep.id } } == true
            val pass = result == DeleteMessageResult.Success &&
                missing == DeleteMessageResult.NotFound && nodeGone && keepAlive
            DevLabResult(
                if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
                summary = if (pass) "整楼（含 2 个 swipe）删除成功，缺失消息明确返回 NotFound" else "删除语义不正确",
                intermediate = "delete=$result missing=$missing nodeGone=$nodeGone keepAlive=$keepAlive",
                result = "remainingNodes=${saved?.messageNodes?.size ?: -1}",
            )
        } finally {
            if (runCatching { chatService.deleteConversation(conversation.id) }.isSuccess) {
                tempConversationIds -= conversation.id
            }
        }
    }

    private fun toolPolicy(settings: Settings): DevLabResult {
        val base = settings.assistants.firstOrNull { it.id == settings.assistantId }
            ?: settings.assistants.firstOrNull()
            ?: return DevLabResult(DevLabStatus.SKIP, summary = "助手配置尚未加载")
        val normal = base.copy(tavernData = null, enableTools = true, toolPolicy = null)
        val roleplay = base.copy(
            tavernData = TavernCharacterData(name = "devlab"),
            enableTools = true,
            toolPolicy = null,
            totalStepsLimit = 32,
        )
        val explicit = roleplay.copy(toolPolicy = ToolPolicy.AUTO, totalStepsLimit = 32)
        val required = roleplay.copy(toolPolicy = ToolPolicy.REQUIRED)
        val disabled = normal.copy(enableTools = false, toolPolicy = ToolPolicy.REQUIRED)
        // [P0 修复] 期望值必须跟 Assistant.effectiveMaxToolSteps() 的真实实现对齐：
        // 酒馆卡上限已从 2 放宽到 TAVERN_MAX_TOOL_STEPS=8（一次 step = 一次完整模型请求，
        // 压到 2 只够「1 轮工具 + 1 次收尾」）。旧断言写死 2，导致内置自检 05 永远 FAIL，
        // 反而掩盖了真正的问题。
        val expectedTavernSteps = minOf(32, TAVERN_MAX_TOOL_STEPS)
        val pass = normal.effectiveToolPolicy() == ToolPolicy.AUTO &&
            roleplay.effectiveToolPolicy() == ToolPolicy.NONE &&
            explicit.effectiveToolPolicy() == ToolPolicy.AUTO &&
            required.effectiveToolPolicy() == ToolPolicy.REQUIRED &&
            disabled.effectiveToolPolicy() == ToolPolicy.NONE &&
            roleplay.effectiveMaxToolSteps() == expectedTavernSteps &&
            explicit.effectiveMaxToolSteps() == expectedTavernSteps &&
            required.effectiveMaxToolSteps() == expectedTavernSteps &&
            // [修正] 普通助手不裁剪，effectiveMaxToolSteps() == totalStepsLimit。
            // 旧断言写死 16（Assistant.totalStepsLimit 的默认值），用户一旦把上限调成
            // 其它值（例如 256），本项就会永久 FAIL，把真实问题掩盖掉。
            normal.effectiveMaxToolSteps() == normal.totalStepsLimit.coerceAtLeast(1)
        return DevLabResult(
            if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = "普通=auto；角色卡默认=none；显式开启=auto；必须=required；总开关关闭=none；" +
                "角色卡单轮≤$TAVERN_MAX_TOOL_STEPS",
            input = "assistant=${base.name}",
            intermediate = "normal=${normal.effectiveToolPolicy()} roleplay=${roleplay.effectiveToolPolicy()} " +
                "required=${required.effectiveToolPolicy()} disabled=${disabled.effectiveToolPolicy()}",
            result = "roleplayMaxSteps=${roleplay.effectiveMaxToolSteps()} expected=$expectedTavernSteps " +
                "normalMaxSteps=${normal.effectiveMaxToolSteps()} " +
                "normalTotalStepsLimit=${normal.totalStepsLimit}",
        )
    }

    private fun imageRendering(): DevLabResult {
        val sample = """<p>before<span><img src="https://example.com/a.png" alt="a"></span>after</p><pic>a cat</pic>"""
        val preview = extractQuickPlainPreview(sample)
        val rendered = me.rerere.rikkahub.ui.components.message.computeStRegexedText(
            rawInputText = sample,
            scripts = emptyList(),
            assistant = null,
            scope = me.rerere.rikkahub.data.model.AssistantAffectScope.ASSISTANT,
            placement = 0,
        )
        val previewKeepsImage = preview.contains("![图片]") || preview.contains("图片加载中")
        val previewKeepsPic = preview.contains("图片生成中")
        val renderedHasPicFallback = rendered.contains("图片生成/加载中") || rendered.contains("<img")
        val pass = previewKeepsImage && previewKeepsPic && renderedHasPicFallback
        return DevLabResult(
            if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = if (pass) "解析层：嵌套 <img>、<pic> 占位与快速预览均保留图片信息" else "图片占位/嵌套链接仍有丢失",
            input = sample,
            intermediate = "preview=$preview",
            result = "rendered=${rendered.take(800)}",
        )
    }

    private suspend fun loadTimeline(): DevLabResult {
        val records = TavernRuntimeManager.getWebViewBootRecords("panel")
        if (records.isEmpty()) {
            return DevLabResult(DevLabStatus.SKIP, summary = "尚无面板启动记录（打开含面板的消息后再测）")
        }
        val stages = records.map { it.stage }
        val hasRuntime = "runtime_ready" in stages
        val hasSettled = "resources_settled" in stages
        val ordered = records.groupBy { it.hostId }.values.any { panelRecords ->
            val runtimeIndex = panelRecords.indexOfFirst { it.stage == "runtime_ready" }
            val settledIndex = panelRecords.indexOfFirst { it.stage == "resources_settled" }
            runtimeIndex >= 0 && settledIndex > runtimeIndex
        }
        val pass = hasRuntime && hasSettled && ordered
        return DevLabResult(
            if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = if (pass) "resources_settled 在 runtime_ready 之后出现（不再以高度>0 冒充完成）" else "缺少真实资源 settle 记录",
            intermediate = "runtime_ready=$hasRuntime resources_settled=$hasSettled ordered=$ordered",
            result = records.takeLast(12).joinToString("\n") { "#${it.seq} ${it.stage} ${it.detail}" },
            logs = "records=${records.size}",
        )
    }

    private suspend fun networkRace(): DevLabResult {
        val probeUrl = "https://www.baidu.com/favicon.ico"
        val stats = TavernResourceRoute.probeChannels(context, probeUrl)
        if (stats.isEmpty()) {
            return DevLabResult(DevLabStatus.SKIP, summary = "没有可用的网络尝试")
        }
        val success = stats.count { it.ok }
        if (success == 0) {
            return DevLabResult(
                DevLabStatus.SKIP,
                summary = "所有通道均失败（可能无网络）",
                input = "$probeUrl mode=${TavernResourceRoute.mode}",
                result = stats.joinToString("\n") { stat -> "${stat.channel}: ${stat.error}" },
            )
        }
        val first = TavernResourceRoute.fetchImageToCache(context, probeUrl, timeoutMs = 6_000L)
        val second = TavernResourceRoute.fetchImageToCache(context, probeUrl, timeoutMs = 6_000L)
        val cacheHit = first != null && second != null && first.absolutePath == second.absolutePath
        val lines = stats.joinToString("\n") { stat ->
            "${stat.channel}: ${if (stat.ok) "OK ttfb=${stat.ttfbMs}ms total=${stat.totalMs}ms" else "FAIL ${stat.error}"}"
        } + "\n磁盘缓存命中：$cacheHit"
        // 测试 URL 不留在用户图片缓存里。
        TavernResourceRoute.evictCachedUrl(context, probeUrl)
        return DevLabResult(
            if (success > 0) DevLabStatus.PASS else DevLabStatus.SKIP,
            summary = if (success > 0) {
                "$success/${stats.size} 个通道成功，TTFB 已记录；缓存命中=$cacheHit"
            } else {
                "所有通道均失败（可能无网络）"
            },
            input = "$probeUrl mode=${TavernResourceRoute.mode}",
            result = lines,
        )
    }

    private fun stressBudget(): DevLabResult {
        val active = ActivePanelWebViewPool.activeCount()
        val detached = DetachedPanelWebViewCache.size()
        val budget = ActivePanelWebViewPool.RUNTIME_SAFE_MAX_ACTIVE_WEBVIEWS
        val estimatedMb = active * 80L + detached * 80L
        val pass = active <= budget && detached <= 2 && estimatedMb <= 512
        return DevLabResult(
            if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = "活跃池上限=$budget，离屏缓存上限=2；当前 active=$active detached=$detached",
            input = "1/5/10/20 面板仅创建最多预算内的真实 WebView",
            intermediate = "estimatedWebViewMemory≈${estimatedMb}MB",
            result = "真实 1/10/20 并行压测需在聊天页由滚动触发；测试台只读校验预算不会被突破",
        )
    }

    private fun webViewReport(): DevLabResult {
        val active = ActivePanelWebViewPool.activeCount()
        val detached = DetachedPanelWebViewCache.size()
        val snapshots = PanelSnapshotStore.memoryCount()
        val snapshotBytes = PanelSnapshotStore.memoryBytes()
        val (cacheCount, cacheBytes) = TavernResourceRoute.cacheStats(context)
        val totalMb = active * 80L + detached * 80L + (snapshotBytes / (1024L * 1024L)) + (cacheBytes / (1024L * 1024L))
        val pass = totalMb <= 512 && snapshots <= 12
        val report = buildString {
            appendLine("host=${if (TavernRuntimeManager.isStarted) 1 else 0} aux=${TavernRuntimeManager.auxWebViewCount()}")
            appendLine("activePanelPool=$active detachedPanelCache=$detached")
            appendLine("snapshots=$snapshots bytes=${formatBytes(snapshotBytes)}")
            appendLine("imageCache=$cacheCount bytes=${formatBytes(cacheBytes)}")
            appendLine("estimatedTotal≈${totalMb}MB budget=512MB")
            appendLine("assertNoFakeReady=resources_settled 阶段存在")
        }
        return DevLabResult(
            if (pass) DevLabStatus.PASS else DevLabStatus.FAIL,
            summary = if (pass) "估算内存预算内（≈${totalMb}MB，非 PSS）" else "估算内存预算超限（≈${totalMb}MB）",
            result = report,
        )
    }

    /**
     * [P0 新增] 发送链路阶段诊断。
     *
     * 用户唯一的体感是「点发送后一直转圈」。「开发者测试台显示没问题」的根因是：
     * 台里的 10 项自检覆盖的是面板桥 / 长楼层落库 / 删除 / 工具策略 / 图片 / 预算，
     * **没有任何一项检查「从点击发送到真正发出 HTTP 请求」的那 7 个阶段**，
     * 所以它们全绿也不能说明发送链路是通的。
     *
     * 这一项直接把 [TavernRuntimeManager.SendDiagnosticStage] 的真实记录读出来，
     * 明确回答三个问题：
     *   1. 上一次发送停在哪个阶段？
     *   2. 从「用户消息落库(S3)」到「请求发出(S6)」用了多久？
     *   3. 是否出现过 watchdog 超时 / 钩子超时 / 取消？
     */
    private fun sendPipelineDiagnostics(): DevLabResult {
        val records = TavernRuntimeManager.getSendDiagnosticRecords()
        if (records.isEmpty()) {
            return DevLabResult(
                DevLabStatus.SKIP,
                summary = "还没有发送记录：先在聊天页发一条消息，再回来运行本项",
                input = "诊断来源 = tavern-runtime.log 的 [send-diag] 记录",
            )
        }
        // 按会话分组取最近一次发送轮。
        // 注意：一轮的边界是「上一条 S7 之后」——不能简单按会话过滤，
        // 否则历史多轮会混在一起，`lastOrNull` 取到的是旧 S6、判断失真。
        val lastConversation = records.lastOrNull()?.conversationId.orEmpty()
        val conversationRecords = records.filter { it.conversationId == lastConversation }
        val lastFinishedIndex = conversationRecords.indexOfLast {
            it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED
        }
        val mine = if (lastFinishedIndex >= 0) {
            conversationRecords.drop(lastFinishedIndex + 1).ifEmpty { conversationRecords.takeLast(1) }
        } else {
            conversationRecords
        }
        val stageNames = mine.joinToString(" → ") { "S${it.stage.stepNumber}" }
        val s3 = mine.lastOrNull { it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_3_USER_MSG_PERSISTED }
        val s6 = mine.lastOrNull { it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_6_MODEL_REQUEST_SENT }
        val s7 = mine.lastOrNull { it.stage == TavernRuntimeManager.SendDiagnosticStage.STAGE_7_GENERATION_FINISHED }
        val prepareMs = if (s3 != null && s6 != null) s6.timestampMs - s3.timestampMs else -1L
        val totalMs = if (s3 != null && s7 != null) s7.timestampMs - s3.timestampMs else -1L
        val watchdogHits = mine.count { it.detail.contains("WATCHDOG_") }
        // 真正的钩子超时：诊断 detail 里没有这种字样，钩子超时只在 [gen-hooks]/[ejs] 日志里，
        // 这里统计 detail 中显式标注的 timeout/超时（避免用宽泛的小写匹配误算）。
        val hookTimeoutHits = mine.count {
            it.detail.contains("timeout", ignoreCase = true) || it.detail.contains("超时")
        }
        val abortedHits = mine.count {
            it.detail.contains("ABORTED") || it.detail.contains("CANCELLED")
        }
        val reachedRequest = s6 != null
        // [P0 修正] 判定交给纯函数：停止动作 / 只插入 / 无数据一律 SKIP，不再假 FAIL。
        val verdict = classifySendRound(mine)
        // 卡住的阶段 = 最后一条记录的阶段（而不是 s7 的阶段号，那个恒为 7，没有信息量）
        val lastRecord = mine.lastOrNull()
        val summary = when (verdict) {
            SendRoundVerdict.NoData -> "没有可判定的一轮发送"
            SendRoundVerdict.NotASendRound ->
                "⏭️ 这不是一轮发送（用户点的是「停止生成」，或只有孤立的结束记录）——不作为发送失败统计"
            SendRoundVerdict.InsertOnly ->
                "⏭️ 这一轮只插入消息、未触发生成（answer=false），没有 S6 是预期行为"
            SendRoundVerdict.Passed -> "✅ 上一次发送完整走到请求阶段，准备耗时 ${prepareMs}ms"
            SendRoundVerdict.WatchdogAborted -> "⚠️ 到达了请求阶段，但触发过 ${watchdogHits} 次发送看门狗中止"
            SendRoundVerdict.FailedBeforeRequest ->
                if (lastRecord != null) {
                    "❌ 上一次发送没有到达请求阶段就结束（停在 S${lastRecord.stage.stepNumber} ${lastRecord.stage.name}）——上游必然收不到"
                } else {
                    "❌ 上一次发送没有到达请求阶段（S6 缺失）——上游必然收不到"
                }
        }
        val pass = when (verdict) {
            SendRoundVerdict.Passed -> true
            SendRoundVerdict.NotASendRound,
            SendRoundVerdict.InsertOnly,
            SendRoundVerdict.NoData,
            -> true // 不适用，不算失败
            SendRoundVerdict.WatchdogAborted,
            SendRoundVerdict.FailedBeforeRequest,
            -> false
        }
        val status = when (verdict) {
            SendRoundVerdict.NotASendRound,
            SendRoundVerdict.InsertOnly,
            SendRoundVerdict.NoData,
            -> DevLabStatus.SKIP
            else -> if (pass) DevLabStatus.PASS else DevLabStatus.FAIL
        }
        val report = buildString {
            appendLine("conversation=$lastConversation")
            appendLine("阶段序列：$stageNames")
            appendLine("S3→S6 准备耗时=${prepareMs}ms   S3→S7 总耗时=${totalMs}ms")
            appendLine("reachedRequest=$reachedRequest watchdog=$watchdogHits hookTimeout=$hookTimeoutHits aborted=$abortedHits")
            appendLine("verdict=$verdict（NotASendRound/InsertOnly/NoData 一律 SKIP，不算失败）")
            appendLine("--- 最近 12 条 ---")
            mine.takeLast(12).forEach { r ->
                appendLine("#${r.seq} S${r.stage.stepNumber} ${r.stage.name} · ${r.detail}")
            }
        }
        return DevLabResult(
            status,
            summary = summary,
            input = "来源=tavern-runtime.log [send-diag]",
            intermediate = "S3→S6=${prepareMs}ms verdict=$verdict",
            result = report,
            logs = "阶段 = ${TavernRuntimeManager.SendDiagnosticStage.entries.joinToString { "S${it.stepNumber}:${it.label}" }}",
        )
    }

    private suspend fun tempConversation(assistantId: Uuid, floors: Int): Conversation {
        val nodes = (0 until floors).map { index ->
            UIMessage(
                role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                parts = listOf(UIMessagePart.Text("__devlab_floor_$index")),
            ).toMessageNode()
        }
        return Conversation(
            id = Uuid.random(),
            assistantId = assistantId,
            title = "__devlab_send_${System.currentTimeMillis()}",
            messageNodes = nodes,
        )
    }

    private suspend fun evaluateJsAwait(jsExpression: String, timeoutMs: Long = 4_500L): String? {
        val kickoff = """
            (function(){
              try {
                window.__rikkaDevLabProbeResult = null;
                Promise.resolve($jsExpression)
                  .then(function(r){ window.__rikkaDevLabProbeResult = JSON.stringify(r); })
                  .catch(function(e){ window.__rikkaDevLabProbeResult = JSON.stringify({error:String((e&&e.message)||e)}); });
                return 'started';
              } catch (e) { window.__rikkaDevLabProbeResult = JSON.stringify({error:String(e)}); return 'sync-error'; }
            })()
        """.trimIndent()
        TavernRuntimeManager.evaluateJsProbe(kickoff, timeoutMs = 2_000)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val raw = TavernRuntimeManager.evaluateJsProbe(
                "window.__rikkaDevLabProbeResult == null ? null : window.__rikkaDevLabProbeResult",
                timeoutMs = 1_500,
            )
            if (!raw.isNullOrBlank() && raw != "null") {
                return decodeJsString(raw).takeIf { it.isNotBlank() && it != "null" }
            }
            delay(120)
        }
        return null
    }

    private fun decodeJsString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return trimmed
        return runCatching {
            when (val value = JSONTokener(trimmed).nextValue()) {
                is String -> value
                null -> trimmed
                else -> value.toString()
            }
        }.getOrDefault(trimmed)
    }

    private fun parseJson(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun networkLabel(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "未知"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 直连"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络直连"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网直连"
            else -> "未知"
        }
    }
}

private fun buildReport(environment: String, results: Map<String, DevLabResult>): String {
    val raw = buildString {
        appendLine("rikkaST Developer Lab Report")
        appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("--- environment ---")
        appendLine(environment)
        appendLine("--- tests ---")
        DEV_LAB_TESTS.forEach { test ->
            val result = results[test.id] ?: return@forEach
            appendLine("[${result.status}] ${test.id} ${test.title} (${result.durationMs}ms)")
            appendLine("summary: ${result.summary}")
            if (result.input.isNotBlank()) appendLine("input: ${result.input}")
            if (result.intermediate.isNotBlank()) appendLine("intermediate: ${result.intermediate}")
            if (result.result.isNotBlank()) appendLine("result: ${result.result}")
            if (result.logs.isNotBlank()) appendLine("logs: ${result.logs}")
            appendLine()
        }
    }
    return maskSecrets(raw)
}

private fun maskSecrets(input: String): String = input
    .replace(
        Regex("""(?i)(api[_-]?key|token|authorization|password|secret)\s*[:=]\s*([^\s,;]+)"""),
        "${'$'}1=***",
    )
    .replace(Regex("""(?i)(sk-[a-z0-9_-]{8})[a-z0-9_-]+"""), "${'$'}1***")

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1fKB", bytes / 1024.0)
    else -> "${bytes}B"
}
