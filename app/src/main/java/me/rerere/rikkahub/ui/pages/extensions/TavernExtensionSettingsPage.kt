package me.rerere.rikkahub.ui.pages.extensions

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.st.extensions.TavernExtensionInfo
import me.rerere.rikkahub.data.st.extensions.buildRuntimeExtensionsJson
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.extensions.normalizeExtensionFolderAlias
import me.rerere.rikkahub.data.st.runtime.TavernNodeServiceManager
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.appendGlobalScripts
import me.rerere.rikkahub.data.st.runtime.extractScriptsFromJson
import me.rerere.rikkahub.data.st.runtime.listGlobalScripts
import me.rerere.rikkahub.ui.components.chat.TavernFloatingWindowModal
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

private val extSettingsPersistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 单个第三方扩展的混合设置页（Native Material3 快捷配置顶栏 + ST WebView 扩展设置面板）：
 *
 * [10-19-21 关键修复]：
 * 1. **彻底根除第 2 次及之后点进小手机/记忆增强设置页白屏问题**：
 *    - 不再等待 `runtimeExtensionsJson != null` 才创建 WebView（`RikkaBridge.getAllThirdPartyExtensionsJson` 已直接扫描磁盘）；
 *    - WebView 底色固定为 `#121318`（与 `extension-settings.css` 一致），绝不透出浅色主题白底；
 *    - 退出页面 `onRelease` 时先调用 `window.__rikkaCleanupExtSettings()` 主动关闭全部 `IndexedDB` 连接并冲刷设置，
 *      延迟 90ms 后再 `destroy()`，彻底消除第 1 次打开的 WebView 遗留 IndexedDB 锁导致第 2 次打开卡死白屏；
 *    - `DisposableEffect` 的 `onDispose` 不再将 `thirdPartySettingsSupplier` / `thirdPartySettingsSaver` 回退为 `null` 或已销毁页面的失效闭包；
 * 2. **点击「打开小手机主屏幕」与「打开记忆表格工作台」直连常驻运行时**：
 *    - 支持直接弹出与聊天页共享同一实例的 [TavernFloatingWindowModal]，实现 0ms 打开小手机主屏幕且不糊屏。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TavernExtensionSettingsPage(folder: String) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
    val latestSettings = rememberUpdatedState(settings)
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var extension by remember(folder) { mutableStateOf<TavernExtensionInfo?>(null) }
    var runtimeExtensionsJson by remember(folder) { mutableStateOf<String?>(null) }
    val webViewHolder = remember(folder) { arrayOfNulls<WebView>(1) }
    var crashRebuildCount by remember(folder) { mutableIntStateOf(0) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var activeFloatingModalTarget by remember(folder) { mutableStateOf<String?>(null) }
    val ttsState by TavernRuntimeManager.stIphonieTtsStateFlow.collectAsState()

    val webViewFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val cb = pendingFileCallback
        pendingFileCallback = null
        cb?.onReceiveValue(uris.toTypedArray())
    }

    val nativeJsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }.orEmpty()
            if (text.isBlank()) {
                toaster.show("所选文件内容为空")
                return@launch
            }
            val alias = normalizeExtensionFolderAlias(folder)
            if (alias.contains("js-slash-runner") || alias.contains("tavern_helper")) {
                val scripts = extractScriptsFromJson(text)
                if (scripts.isNotEmpty()) {
                    val (newJson, added) = appendGlobalScripts(settings.tavernGlobalScripts, scripts)
                    settingsStore.update { cur -> cur.copy(tavernGlobalScripts = newJson) }
                    toaster.show("已导入 $added 个酒馆助手全局脚本")
                    webViewHolder[0]?.reload()
                    return@launch
                }
            }
            // 通用扩展 JSON 配置导入：支持原始配置对象、ST-iPhonie 备份包 ({settings:{...}}) 以及记忆增强表格模板数组 ([...])，
            // 并以增量合并方式写入真实扩展命名空间（stTtsDialogue / muyoo_dataTable / EjsTemplate / LittleWhiteBox），立即触发主运行时热同步。
            val importedOk = runCatching {
                val parsed = Json.parseToJsonElement(text)
                val curRoot = (settings.tavernExtensionSettings.takeIf { it.isNotBlank() }?.let {
                    Json.parseToJsonElement(it)
                } as? JsonObject)?.toMutableMap() ?: mutableMapOf()

                fun mergeIntoKey(key: String, incoming: JsonObject) {
                    val existing = (curRoot[key] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                    for ((k, v) in incoming) {
                        val prevObj = existing[k] as? JsonObject
                        if (prevObj != null && v is JsonObject) {
                            existing[k] = JsonObject(prevObj + v)
                        } else {
                            existing[k] = v
                        }
                    }
                    curRoot[key] = JsonObject(existing)
                }

                when {
                    parsed is JsonArray && alias.contains("st-memory-enhancement") -> {
                        val tablePatch = JsonObject(
                            mapOf(
                                "tableStructure" to parsed,
                                "updateIndex" to JsonPrimitive(3),
                            )
                        )
                        mergeIntoKey("muyoo_dataTable", tablePatch)
                        mergeIntoKey("st_memory_enhancement", tablePatch)
                        val nextSettingsJson = JsonObject(curRoot).toString()
                        settingsStore.update { cur -> cur.copy(tavernExtensionSettings = nextSettingsJson) }
                        true
                    }
                    parsed is JsonObject -> {
                        when {
                            alias.contains("st-prompt-template") -> {
                                val unwrapped = (parsed["EjsTemplate"] as? JsonObject)
                                    ?: (parsed["ST_Prompt_Template"] as? JsonObject)
                                    ?: parsed
                                mergeIntoKey("EjsTemplate", unwrapped)
                                mergeIntoKey("ST_Prompt_Template", unwrapped)
                            }
                            alias.contains("st-memory-enhancement") -> {
                                val rawUnwrapped = (parsed["muyoo_dataTable"] as? JsonObject)
                                    ?: (parsed["st_memory_enhancement"] as? JsonObject)
                                    ?: parsed
                                val unwrapped = if ("tableStructure" in rawUnwrapped && "updateIndex" !in rawUnwrapped) {
                                    JsonObject(rawUnwrapped + ("updateIndex" to JsonPrimitive(3)))
                                } else {
                                    rawUnwrapped
                                }
                                mergeIntoKey("muyoo_dataTable", unwrapped)
                                mergeIntoKey("st_memory_enhancement", unwrapped)
                            }
                            alias.contains("littlewhitebox") -> {
                                val unwrapped = (parsed["LittleWhiteBox"] as? JsonObject) ?: parsed
                                mergeIntoKey("LittleWhiteBox", unwrapped)
                            }
                            alias.contains("st-iphonie") -> {
                                val unwrapped = (parsed["stTtsDialogue"] as? JsonObject)
                                    ?: (parsed["settings"] as? JsonObject)
                                    ?: parsed
                                mergeIntoKey("stTtsDialogue", unwrapped)
                                mergeIntoKey("st-iphonie", unwrapped)
                            }
                            else -> {
                                val unwrapped = (parsed[folder] as? JsonObject) ?: parsed
                                mergeIntoKey(folder, unwrapped)
                            }
                        }
                        val nextSettingsJson = JsonObject(curRoot).toString()
                        settingsStore.update { cur -> cur.copy(tavernExtensionSettings = nextSettingsJson) }
                        true
                    }
                    else -> false
                }
            }.getOrDefault(false)
            if (importedOk) {
                toaster.show("已导入扩展配置并同步生效")
                TavernRuntimeManager.fireEvent(TavernRuntimeManager.EVENT_SETTINGS_UPDATED, "{}")
                TavernRuntimeManager.reloadThirdPartyExtensions()
                webViewHolder[0]?.reload()
            } else {
                toaster.show("文件格式未识别为脚本或扩展 JSON 配置")
            }
        }
    }

    LaunchedEffect(folder, settings.tavernThirdPartyDisabled) {
        val list = withContext(Dispatchers.IO) { listExtensions(context) }
        extension = list.firstOrNull {
            it.folder == folder || normalizeExtensionFolderAlias(it.folder) == normalizeExtensionFolderAlias(folder)
        }
        runtimeExtensionsJson = withContext(Dispatchers.Default) {
            buildRuntimeExtensionsJson(list, settings.tavernThirdPartyDisabled - folder)
        }
    }

    // 绑定扩展设置读写桥（使用进程级 extSettingsPersistScope，且不在 onDispose 中置空，防止页面切换动画期间旧页把新页的 supplier 冲掉）
    DisposableEffect(settingsStore, folder) {
        val appCtx = context.applicationContext
        TavernRuntimeManager.ensureContext(appCtx)
        TavernRuntimeManager.ensureStarted(appCtx)
        TavernRuntimeManager.thirdPartyExtensionsSupplier = {
            runtimeExtensionsJson
                ?: buildRuntimeExtensionsJson(listExtensions(appCtx), latestSettings.value.tavernThirdPartyDisabled - folder)
        }
        TavernRuntimeManager.thirdPartySettingsSupplier = {
            latestSettings.value.tavernExtensionSettings
        }
        TavernRuntimeManager.thirdPartySettingsSaver = { json ->
            extSettingsPersistScope.launch {
                settingsStore.update { current -> current.copy(tavernExtensionSettings = json) }
                TavernRuntimeManager.fireEvent(TavernRuntimeManager.EVENT_SETTINGS_UPDATED, "{}")
            }
        }
        onDispose { }
    }

    val title = extension?.displayName?.ifBlank { folder } ?: folder
    val isEnabled = folder !in settings.tavernThirdPartyDisabled

    fun triggerWebViewAction(action: String) {
        if (action == "open_iphonie_app") {
            activeFloatingModalTarget = "iphonie"
            return
        }
        val escaped = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(action))
        webViewHolder[0]?.evaluateJavascript(
            "window.__rikkaExtSettingsAction && window.__rikkaExtSettingsAction($escaped);",
            null,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { nativeJsonImportLauncher.launch(arrayOf("*/*")) }) {
                        Icon(HugeIcons.FileImport, contentDescription = "导入脚本或配置 JSON")
                    }
                    IconButton(onClick = { webViewHolder[0]?.reload() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "重新加载扩展设置")
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ExtensionNativeShellCard(
                folder = folder,
                extension = extension,
                enabled = isEnabled,
                settings = settings,
                onToggleEnabled = { checked ->
                    val nextDisabled = if (checked) {
                        settings.tavernThirdPartyDisabled - folder
                    } else {
                        settings.tavernThirdPartyDisabled + folder
                    }
                    scope.launch {
                        settingsStore.update { cur -> cur.copy(tavernThirdPartyDisabled = nextDisabled) }
                        TavernRuntimeManager.reloadThirdPartyExtensions()
                        webViewHolder[0]?.reload()
                    }
                },
                onToggleGlobalScripts = { checked ->
                    scope.launch {
                        settingsStore.update { cur -> cur.copy(tavernGlobalScriptsEnabled = checked) }
                        webViewHolder[0]?.reload()
                    }
                },
                onImportJson = { nativeJsonImportLauncher.launch(arrayOf("*/*")) },
                onTriggerAction = ::triggerWebViewAction,
            )

            // 直接首帧挂载 WebView（底色固定为深色 #121318，绝不出现浅色白屏）
            key(folder, crashRebuildCount) {
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        TavernRuntimeManager.ensureContext(ctx)
                        TavernRuntimeManager.ensureStarted(ctx)
                        WebView(ctx).apply {
                            setBackgroundColor(Color.parseColor("#121318"))
                            this.settings.javaScriptEnabled = true
                            this.settings.domStorageEnabled = true
                            this.settings.allowFileAccess = false
                            this.settings.allowContentAccess = false
                            this.settings.mediaPlaybackRequiresUserGesture = false
                            this.settings.blockNetworkImage = false
                            this.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? = TavernRuntimeManager.interceptAsset(request.url, request.requestHeaders)

                                override fun onPageFinished(view: WebView, url: String) {
                                    TavernRuntimeManager.appendLog(
                                        "info",
                                        "[ext-settings] page ready folder=$folder url=$url",
                                    )
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail?,
                                ): Boolean {
                                    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.didCrash() == true
                                    } else false
                                    val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.rendererPriorityAtExit() ?: -1
                                    } else -1
                                    TavernRuntimeManager.appendLog(
                                        "error",
                                        "[webview] renderer gone (ext-settings:$folder) didCrash=$didCrash priority=$priority -> recreating WebView",
                                    )
                                    if (webViewHolder[0] === view) webViewHolder[0] = null
                                    TavernRuntimeManager.unregisterAuxWebView(view)
                                    runCatching {
                                        (view.parent as? ViewGroup)?.removeView(view)
                                        view.destroy()
                                    }
                                    TavernRuntimeManager.bumpRendererGeneration()
                                    crashRebuildCount += 1
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    TavernRuntimeManager.appendLog(
                                        "debug",
                                        "[ext-settings][console] ${consoleMessage.message()} " +
                                            "@${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                                    )
                                    return true
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = filePathCallback
                                    val mimeTypes = fileChooserParams?.acceptTypes
                                        ?.filter { it.isNotBlank() }
                                        ?.toTypedArray()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?: arrayOf("*/*")
                                    runCatching {
                                        webViewFileChooserLauncher.launch(mimeTypes)
                                    }.onFailure {
                                        pendingFileCallback = null
                                        filePathCallback?.onReceiveValue(null)
                                    }
                                    return true
                                }

                                override fun onJsAlert(
                                    view: WebView,
                                    url: String,
                                    message: String,
                                    result: JsResult,
                                ): Boolean {
                                    runCatching {
                                        AlertDialog.Builder(view.context)
                                            .setMessage(message)
                                            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                            .setOnCancelListener { result.cancel() }
                                            .show()
                                    }.onFailure { result.cancel() }
                                    return true
                                }

                                override fun onJsConfirm(
                                    view: WebView,
                                    url: String,
                                    message: String,
                                    result: JsResult,
                                ): Boolean {
                                    runCatching {
                                        AlertDialog.Builder(view.context)
                                            .setMessage(message)
                                            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                                            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                                            .setOnCancelListener { result.cancel() }
                                            .show()
                                    }.onFailure { result.cancel() }
                                    return true
                                }
                            }
                            addJavascriptInterface(TavernRuntimeManager.TavernJsBridge(), "RikkaBridge")
                            TavernRuntimeManager.registerAuxWebView(this, "ext-settings:$folder")
                            webViewHolder[0] = this
                            loadUrl(
                                TavernRuntimeManager.BASE_URL +
                                    "extension-settings.html?ext=" +
                                    Uri.encode(folder),
                            )
                        }
                    },
                    onRelease = { released ->
                        if (webViewHolder[0] === released) webViewHolder[0] = null
                        TavernRuntimeManager.unregisterAuxWebView(released)
                        runCatching {
                            released.evaluateJavascript(
                                "try{window.__rikkaCleanupExtSettings&&window.__rikkaCleanupExtSettings();}catch(e){}",
                                null,
                            )
                            released.stopLoading()
                        }
                        mainHandler.postDelayed({
                            runCatching {
                                (released.parent as? ViewGroup)?.removeView(released)
                                released.destroy()
                            }
                        }, 90L)
                    },
                )
            }
        }
    }

    activeFloatingModalTarget?.let { target ->
        TavernFloatingWindowModal(
            initialTarget = target,
            hasIphonie = true,
            hasMemory = true,
            hasLittleWhiteBox = true,
            hasCardFloating = false,
            ttsPlaying = ttsState.playing,
            onDismiss = {
                activeFloatingModalTarget = null
                TavernRuntimeManager.dismissFloatingWindow()
                webViewHolder[0]?.evaluateJavascript(
                    "window.__rikkaExtSettingsAction && window.__rikkaExtSettingsAction('reload_settings');",
                    null,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtensionNativeShellCard(
    folder: String,
    extension: TavernExtensionInfo?,
    enabled: Boolean,
    settings: Settings,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleGlobalScripts: (Boolean) -> Unit,
    onImportJson: () -> Unit,
    onTriggerAction: (String) -> Unit,
) {
    val alias = remember(folder) { normalizeExtensionFolderAlias(folder) }
    val globalScriptCount = remember(settings.tavernGlobalScripts) {
        listGlobalScripts(settings.tavernGlobalScripts).size
    }
    val nodeStatus by TavernNodeServiceManager.statusFlow.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append(extension?.displayName?.ifBlank { folder } ?: folder)
                            extension?.version?.takeIf { it.isNotBlank() }?.let { append(" · v$it") }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (enabled) "当前已启用 · 生成与渲染钩子自动生效" else "当前已停用 · 开启右侧开关即可在会话中生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }

            if (alias.contains("js-slash-runner")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "全局脚本总开关（已装载 $globalScriptCount 个脚本）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(
                        checked = settings.tavernGlobalScriptsEnabled,
                        onCheckedChange = onToggleGlobalScripts,
                    )
                }
            }

            if (alias.contains("littlewhitebox") && extension?.requiresNode == true) {
                Text(
                    text = "Node 伴生服务：${nodeStatus.state}（端口 ${nodeStatus.port}）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { onTriggerAction("expand_all") },
                    label = { Text("展开全部子面板") },
                )
                when {
                    alias.contains("js-slash-runner") -> {
                        AssistChip(
                            onClick = { onTriggerAction("open_jsr_scripts") },
                            label = { Text("脚本库 / 变量管理器") },
                        )
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入 JSR 脚本 JSON") },
                        )
                    }
                    alias.contains("st-prompt-template") -> {
                        AssistChip(
                            onClick = { onTriggerAction("open_stpt_modal") },
                            label = { Text("打开 EJS 模板测试台") },
                        )
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入配置 JSON") },
                        )
                    }
                    alias.contains("st-memory-enhancement") -> {
                        AssistChip(
                            onClick = { onTriggerAction("open_memory_table") },
                            label = { Text("打开记忆表格工作台") },
                        )
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入表格模板 JSON") },
                        )
                    }
                    alias.contains("littlewhitebox") -> {
                        AssistChip(
                            onClick = { onTriggerAction("open_xbx_panel") },
                            label = { Text("打开小白盒控制台") },
                        )
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入配置 JSON") },
                        )
                    }
                    alias.contains("st-iphonie") -> {
                        AssistChip(
                            onClick = { onTriggerAction("open_iphonie_app") },
                            label = { Text("打开小手机主屏幕") },
                        )
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入手机配置 JSON") },
                        )
                    }
                    else -> {
                        Spacer(Modifier.width(2.dp))
                        AssistChip(
                            onClick = onImportJson,
                            leadingIcon = { Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("导入配置 JSON") },
                        )
                    }
                }
            }
        }
    }
}
