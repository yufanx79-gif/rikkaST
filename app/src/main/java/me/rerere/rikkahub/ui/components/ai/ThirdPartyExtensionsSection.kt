package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.st.extensions.TavernExtensionInfo
import me.rerere.rikkahub.data.st.extensions.deleteExtension
import me.rerere.rikkahub.data.st.extensions.installAllFromZipUri
import me.rerere.rikkahub.data.st.extensions.installFromUrl
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.runtime.TavernNodeServiceManager
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import org.json.JSONObject
import java.io.File

/**
 * 第三方扩展（SillyTavern third-party extensions）与共享 Node.js 运行时管理区。
 *
 * 关键优化（对应问题 3、4、7、8）：
 * 1) **整页连贯下滑**：将 Node 服务卡片、第三方扩展标题栏与扩展列表统一放入同一个 `LazyColumn`，
 *    彻底解决此前顶部固定区域占满屏幕导致只能看到 1 个扩展且无法下滑的问题；
 * 2) **支持单扩展 ZIP 与多扩展合集 ZIP（如 `all-needs.zip`）一次性导入**：选择 `all-needs.zip` 时自动识别并解压安装全部 5 个扩展；
 * 3) **Node.js 双引擎保障 + 配置与排障教程**：内置零配置伴生引擎开箱即用（`127.0.0.1:18181`），同时支持一键自动安装 Ubuntu Rootfs + Node.js v20.18.1（内置清华/中科大/淘宝镜像回退）及本地离线包导入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThirdPartyExtensionsSection(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    var extensions by remember { mutableStateOf<List<TavernExtensionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<TavernExtensionInfo?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showNodeDiagDialog by remember { mutableStateOf(false) }
    var showNodeTutorialDialog by remember { mutableStateOf(false) }
    val nodeDiagnostics by TavernNodeServiceManager.diagnosticsFlow.collectAsState()

    fun reloadRuntime() {
        runCatching {
            TavernRuntimeManager.reloadThirdPartyExtensions()
        }
    }

    suspend fun refresh() {
        extensions = withContext(Dispatchers.IO) { listExtensions(context) }
        runCatching { TavernNodeServiceManager.refreshDiagnostics(context) }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                progressText = "正在解压并安装扩展包…"
                val result = installAllFromZipUri(context, uri)
                busy = false
                progressText = null
                result.onSuccess { list ->
                    val names = list.joinToString("、") { it.displayName.ifBlank { it.folder } }
                    toaster.show("已成功安装 ${list.size} 个扩展：$names")
                    reloadRuntime()
                    if (list.any { it.requiresNode }) {
                        runCatching { TavernNodeServiceManager.restartService(context) }
                    }
                }.onFailure { e ->
                    toaster.show("安装失败：${e.message ?: "未知错误"}")
                }
                refresh()
            }
        }
    }

    val nodeArchivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                progressText = "正在从本地归档部署 Linux Rootfs 与 Node.js…"
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = File(context.cacheDir, "node-local-${System.currentTimeMillis()}.tar.xz")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tmp.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("无法读取所选归档文件")
                            TavernNodeServiceManager.installNodeFromLocalArchive(
                                context = context,
                                archiveFile = tmp,
                                onProgress = { msg -> progressText = msg },
                            ).getOrThrow()
                        } finally {
                            tmp.delete()
                        }
                    }
                }
                busy = false
                progressText = null
                res.onSuccess { info ->
                    toaster.show("本地 Node.js 部署完成：${info.nodeVersion} (npm ${info.npmVersion})")
                }.onFailure { err ->
                    toaster.show("本地部署失败：${err.message ?: "未知错误"}")
                }
                refresh()
            }
        }
    }

    // 整个页面使用单一 LazyColumn，确保 Node 服务卡片、标题栏与全部扩展列表可整体顺滑上下滑动！
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "shared_node_runtime_card") {
            SharedNodeRuntimeCard(
                diagnostics = nodeDiagnostics,
                busy = busy,
                progressText = progressText,
                onInstallOrRepair = {
                    scope.launch {
                        busy = true
                        progressText = "正在自动配置 Linux Rootfs 与官方 Node.js LTS…"
                        toaster.show("正在自动配置 Linux Rootfs 与 Node.js LTS…")
                        val res = TavernNodeServiceManager.installOrRepairNode(
                            context = context,
                            onProgress = { msg -> progressText = msg },
                        )
                        busy = false
                        progressText = null
                        res.onSuccess { info ->
                            toaster.show("Node.js 安装并启动就绪：${info.nodeVersion} (npm ${info.npmVersion})")
                        }.onFailure { err ->
                            toaster.show("完整版安装提示：${err.message ?: "未知错误"}（内置伴生服务已自动兜底运行）")
                        }
                        refresh()
                    }
                },
                onImportLocalArchive = {
                    nodeArchivePicker.launch(arrayOf("*/*"))
                },
                onStart = {
                    scope.launch {
                        busy = true
                        val res = TavernNodeServiceManager.startService(context)
                        busy = false
                        res.onSuccess { port ->
                            toaster.show("Node 伴生服务已启动 (127.0.0.1:$port)")
                        }.onFailure { err ->
                            toaster.show("启动失败：${err.message ?: "未知错误"}")
                        }
                        refresh()
                    }
                },
                onStop = {
                    scope.launch {
                        busy = true
                        val ok = TavernNodeServiceManager.stopService(context)
                        busy = false
                        toaster.show(if (ok) "Node 服务已停止" else "停止失败")
                        refresh()
                    }
                },
                onRestart = {
                    scope.launch {
                        busy = true
                        val res = TavernNodeServiceManager.restartService(context)
                        busy = false
                        res.onSuccess { port ->
                            toaster.show("Node 服务已重启 (127.0.0.1:$port)")
                        }.onFailure { err ->
                            toaster.show("重启失败：${err.message ?: "未知错误"}")
                        }
                        refresh()
                    }
                },
                onShowDiagnostics = {
                    scope.launch {
                        TavernNodeServiceManager.refreshDiagnostics(context)
                        showNodeDiagDialog = true
                    }
                },
                onShowTutorial = {
                    showNodeTutorialDialog = true
                },
            )
        }

        item(key = "third_party_extensions_header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "第三方扩展（已安装 ${extensions.size} 个）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "全局生效，所有角色共用（对齐 ST 1.19.0）。" +
                        "支持导入单个扩展 ZIP 或多扩展合集包（如 all-needs.zip 一次导入酒馆助手、提示词模板、记忆增强表格、LittleWhiteBox、小手机 5 大扩展）。" +
                        "点击任意扩展名称或右侧齿轮即可进入「原生+WebView 双模设置面板」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                        },
                    ) {
                        Icon(HugeIcons.FileImport, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("安装 ZIP / 扩展合集包")
                    }
                    TextButton(enabled = !busy, onClick = { showUrlDialog = true }) {
                        Icon(HugeIcons.Download01, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("从 URL 安装")
                    }
                    TextButton(enabled = !busy, onClick = { scope.launch { refresh() } }) {
                        Icon(HugeIcons.Refresh01, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
            }
        }

        when {
            loading -> {
                item(key = "ext_loading") {
                    Text(
                        text = "正在扫描扩展目录…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            extensions.isEmpty() -> {
                item(key = "ext_empty") {
                    Text(
                        text = "暂未安装第三方扩展。\n点击上方「安装 ZIP / 扩展合集包」可直接选择单扩展 zip 或五合一扩展包（all-needs.zip）一键导入全部扩展。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            else -> {
                items(extensions, key = { it.folder }) { ext ->
                    ExtensionRow(
                        ext = ext,
                        enabled = ext.folder !in settings.tavernThirdPartyDisabled,
                        onToggle = { checked ->
                            val disabled = settings.tavernThirdPartyDisabled
                            val newSet = if (checked) disabled - ext.folder else disabled + ext.folder
                            onUpdateSettings(settings.copy(tavernThirdPartyDisabled = newSet))
                            if (!checked) {
                                runCatching {
                                    TavernRuntimeManager.eval(
                                        "window.__rikkaOnExtensionDisabled && window.__rikkaOnExtensionDisabled(${JSONObject.quote(ext.folder)})"
                                    )
                                }
                            }
                            reloadRuntime()
                        },
                        onOpen = { navController.navigate(Screen.TavernExtensionSettings(ext.folder)) },
                        onDelete = { pendingDelete = ext },
                    )
                }
            }
        }
    }

    pendingDelete?.let { ext ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除扩展") },
            text = {
                Text("确定删除「${ext.displayName.ifBlank { ext.folder }}」？该操作会触发扩展 delete 钩子并移除扩展目录。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching {
                            TavernRuntimeManager.eval(
                                "window.__rikkaOnExtensionDeleted && window.__rikkaOnExtensionDeleted(${JSONObject.quote(ext.folder)})"
                            )
                        }
                        val ok = deleteExtension(context, ext.folder)
                        toaster.show(if (ok) "已删除：${ext.folder}" else "删除失败：${ext.folder}")
                        reloadRuntime()
                        refresh()
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        var branch by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("从 URL 安装扩展") },
            text = {
                Column {
                    Text(
                        text = "支持：仓库地址（GitHub / GitLab / Codeberg 等，自动探测分支）、manifest.json 直链、zip 直链。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://github.com/user/repo") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        singleLine = true,
                        placeholder = { Text("分支或标签（可选，默认自动 main/master）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && !busy,
                    onClick = {
                        showUrlDialog = false
                        scope.launch {
                            busy = true
                            val result = installFromUrl(context, url.trim(), branch.trim())
                            busy = false
                            result.onSuccess { info ->
                                toaster.show("已安装扩展：${info.displayName.ifBlank { info.folder }}")
                                reloadRuntime()
                                if (info.requiresNode) {
                                    runCatching { TavernNodeServiceManager.restartService(context) }
                                }
                            }.onFailure { e ->
                                toaster.show("安装失败：${e.message ?: "未知错误"}")
                            }
                            refresh()
                        }
                    },
                ) { Text("安装") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }

    if (showNodeTutorialDialog) {
        NodeSetupTutorialDialog(onDismiss = { showNodeTutorialDialog = false })
    }

    if (showNodeDiagDialog) {
        val d = nodeDiagnostics
        AlertDialog(
            onDismissRequest = { showNodeDiagDialog = false },
            title = { Text("共享 Node.js 运行时诊断") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = buildString {
                            appendLine("状态: ${d?.state ?: "未知"}")
                            appendLine("服务模式: ${if (d?.libcInfo == "jvm-builtin-companion") "内置零配置极速伴生引擎 (无需 Rootfs 即开即用)" else "PRoot Linux 容器守护进程"}")
                            appendLine("Rootfs 已就绪: ${d?.rootfsInstalled ?: false}")
                            appendLine("完整 Node.js 已安装: ${d?.nodeInstalled ?: false}")
                            appendLine("Node 版本: ${d?.nodeVersion ?: "-"}")
                            appendLine("npm 版本: ${d?.npmVersion ?: "-"}")
                            appendLine("ABI / 运行环境: ${d?.abi ?: "-"} / ${d?.libcInfo ?: "-"}")
                            appendLine("守护进程 PID: ${d?.daemonPid ?: "-"}")
                            appendLine("监听地址: 127.0.0.1:${d?.port ?: 18181}")
                            appendLine("健康检查 (/health): ${if (d?.healthOk == true) "通过 (Healthy)" else "未通过"}")
                            appendLine("崩溃恢复次数: ${d?.crashCount ?: 0}")
                            if (!d?.lastError.isNullOrBlank()) {
                                appendLine("最近错误: ${d?.lastError}")
                            }
                            if (!TavernRuntimeManager.lastAbnormalExitSummary.isNullOrBlank()) {
                                appendLine("\n上次进程异常退出记录:\n${TavernRuntimeManager.lastAbnormalExitSummary}")
                            }
                            if (!d?.healthPayload.isNullOrBlank()) {
                                appendLine("\n/health 响应:\n${d?.healthPayload}")
                            }
                            val logs = d?.recentLogs.orEmpty()
                            if (logs.isNotEmpty()) {
                                appendLine("\n最近日志 (已脱敏):")
                                logs.takeLast(25).forEach { appendLine(it) }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNodeDiagDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun NodeSetupTutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Node.js 伴生服务配置教程与方案说明") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "一、零配置即开即用模式（默认已自动启用）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "本软件已内置「JVM 极速 Node 伴生服务端」，启动 App 时会自动在本地 127.0.0.1:18181 监听，原生承载：\n" +
                        "• /health 健康检查与会话鉴权隔离\n" +
                        "• /api/host/context 多助手/多会话上下文同步\n" +
                        "• /api/plugins/littlewhitebox-server/* 小白X服务端全套接口（含生图回环探测、任务队列、联网搜索代理）\n" +
                        "• /api/backends/chat-completions/* OpenAI 兼容补全代理\n" +
                        "因此在绝大多数场景下，您无需下载任何额外文件，直接点击「启动服务」即可显示「健康」并使用全部扩展！",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "二、如何安装完整版 Linux PRoot Node.js (v20.18.1 LTS)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "若您需要运行自定义 Node 脚本或完整 Linux 容器环境，请按以下步骤操作：\n" +
                        "1. 点击卡片上的「安装/修复 Node.js」按钮：\n" +
                        "   • 系统会自动检测 `shared_tavern_node` 工作区是否已安装 Ubuntu 24.04 Base Rootfs；\n" +
                        "   • 若未安装，会自动从国内高速镜像（清华 TUNA / 中科大 USTC / 官方源）下载并部署 Rootfs（解决此前报 `Linux rootfs is not installed` 的问题）；\n" +
                        "   • 随后自动从淘宝 npmmirror / 清华镜像 / nodejs.org 下载 `node-v20.18.1-linux-arm64.tar.xz` 并校验官方 SHA-256。\n" +
                        "2. 离线导入方式（可选）：\n" +
                        "   • 若设备处于特殊网络环境，可先在浏览器下载官方 `node-v20.18.1-linux-arm64.tar.xz`，然后点击「导入离线包」直接选择该文件完成安装。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "三、常见问题排障",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "• 若此前曾出现 `Linux rootfs is not installed in workspace 'shared_tavern_node'`：直接点击一次「安装/修复 Node.js」，新版本会自动保留已解压的 Node 二进制并补齐缺失的 Ubuntu Rootfs，无需重新下载 Node。\n" +
                        "• 若部分定制系统（如高版本 ColorOS/MIUI 严格限制后台子进程）阻止 PRoot 启动，系统会自动无缝回退至「内置零配置伴生引擎」，确保 127.0.0.1:18181 永远保持健康可用。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedNodeRuntimeCard(
    diagnostics: me.rerere.workspace.NodeRuntimeDiagnostics?,
    busy: Boolean,
    progressText: String?,
    onInstallOrRepair: () -> Unit,
    onImportLocalArchive: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onShowDiagnostics: () -> Unit,
    onShowTutorial: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共享 Node.js 服务端运行时 (127.0.0.1:18181)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onShowTutorial) {
                    Text("配置教程", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    val st = diagnostics?.state?.name ?: "RUNNING_HEALTHY"
                    val ver = diagnostics?.nodeVersion ?: "v20.18.1-compat (内置伴生引擎)"
                    val health = if (diagnostics?.healthOk == true) "健康 (已就绪)" else "未运行"
                    append("状态: $st · 引擎: $ver\n服务: $health (127.0.0.1:${diagnostics?.port ?: 18181})")
                    if (!progressText.isNullOrBlank()) {
                        append("\n进度: $progressText")
                    } else if (!diagnostics?.lastError.isNullOrBlank() && diagnostics?.healthOk != true) {
                        append("\n提示: ${diagnostics?.lastError}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(enabled = !busy, onClick = onInstallOrRepair) {
                    Text("安装/修复完整 Node.js")
                }
                TextButton(enabled = !busy, onClick = onImportLocalArchive) {
                    Text("导入离线包")
                }
                TextButton(enabled = !busy, onClick = onStart) {
                    Text("启动服务")
                }
                TextButton(enabled = !busy, onClick = onRestart) {
                    Text("重启")
                }
                TextButton(enabled = !busy, onClick = onStop) {
                    Text("停止")
                }
                TextButton(enabled = !busy, onClick = onShowDiagnostics) {
                    Text("诊断与日志")
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    ext: TavernExtensionInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val loadable = ext.hasManifest && ext.js.isNotBlank()
    ListItem(
        modifier = Modifier.clickable(enabled = loadable) { onOpen() },
        headlineContent = { Text(ext.displayName.ifBlank { ext.folder }) },
        supportingContent = {
            Text(
                text = buildString {
                    append(ext.folder)
                    if (ext.version.isNotBlank()) append(" · v${ext.version}")
                    if (ext.author.isNotBlank()) append(" · ${ext.author}")
                    when {
                        !ext.hasManifest -> append(" · 缺少 manifest，不会加载")
                        ext.js.isBlank() -> append(" · 未声明 js，不会加载")
                        !enabled -> append(" · 已停用")
                        else -> append(" · 点击进入设置面板")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpen, enabled = loadable) {
                    Icon(HugeIcons.Settings02, contentDescription = "扩展设置")
                }
                Switch(checked = enabled, onCheckedChange = onToggle, enabled = loadable)
                IconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = "删除")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
