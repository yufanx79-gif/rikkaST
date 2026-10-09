package me.rerere.rikkahub.ui.pages.extensions

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.st.extensions.TavernExtensionInfo
import me.rerere.rikkahub.data.st.extensions.buildRuntimeExtensionsJson
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

/**
 * 单个第三方扩展的设置页。
 *
 * v197：解决「第三方扩展行点了没反应」：
 * - 列表永远不能直接挂载 ST 扩展的设置 DOM，必须给它们一个真实 WebView 宿主；
 * - 该宿主加载 `extension-settings.html?ext=<folder>`，runtime.js 只装载被点击的那个扩展；
 * - `#rikka-st-compat-dom` 在该页面里是可见的，扩展 append 到 `#extensions_settings` /
 *   `#translation_container` / `#extensionsMenu` 等节点的 UI 才能真正显示出来。
 *
 * 以后的扩展不管有几个，只要走同一套 ST DOM / ES Module 契约，都会复用这个页面。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TavernExtensionSettingsPage(folder: String) {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
    val latestSettings = rememberUpdatedState(settings)
    val scope = rememberCoroutineScope()

    var extension by remember(folder) { mutableStateOf<TavernExtensionInfo?>(null) }
    var runtimeExtensionsJson by remember(folder) { mutableStateOf<String?>(null) }
    val webViewHolder = remember(folder) { arrayOfNulls<WebView>(1) }

    LaunchedEffect(folder, settings.tavernThirdPartyDisabled) {
        val list = withContext(Dispatchers.IO) { listExtensions(context) }
        extension = list.firstOrNull { it.folder == folder }
        // 设置页即使扩展当前被停用，也允许加载它自己的 UI；因此这里从 disabled 集合里临时移除本扩展。
        runtimeExtensionsJson = withContext(Dispatchers.Default) {
            buildRuntimeExtensionsJson(list, settings.tavernThirdPartyDisabled - folder)
        }
    }

    DisposableEffect(settingsStore, folder) {
        val previousExtensions = TavernRuntimeManager.thirdPartyExtensionsSupplier
        val previousSettings = TavernRuntimeManager.thirdPartySettingsSupplier
        val previousSaver = TavernRuntimeManager.thirdPartySettingsSaver

        TavernRuntimeManager.thirdPartyExtensionsSupplier = { runtimeExtensionsJson ?: "[]" }
        TavernRuntimeManager.thirdPartySettingsSupplier = { latestSettings.value.tavernExtensionSettings }
        TavernRuntimeManager.thirdPartySettingsSaver = { json ->
            scope.launch {
                settingsStore.update(latestSettings.value.copy(tavernExtensionSettings = json))
            }
        }

        onDispose {
            TavernRuntimeManager.thirdPartyExtensionsSupplier = previousExtensions
            TavernRuntimeManager.thirdPartySettingsSupplier = previousSettings
            TavernRuntimeManager.thirdPartySettingsSaver = previousSaver
        }
    }

    val title = extension?.displayName?.ifBlank { folder } ?: folder

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { webViewHolder[0]?.reload() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "重新加载扩展设置")
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val json = runtimeExtensionsJson
        if (json == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { ctx ->
                    // 只用 ensureContext 设置资产拦截上下文，不启动聊天主运行时 WebView。
                    TavernRuntimeManager.ensureContext(ctx)
                    WebView(ctx).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        this.settings.javaScriptEnabled = true
                        this.settings.domStorageEnabled = true
                        this.settings.allowFileAccess = false
                        this.settings.allowContentAccess = false
                        this.settings.mediaPlaybackRequiresUserGesture = false
                        this.settings.blockNetworkImage = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? = TavernRuntimeManager.interceptAsset(request.url)

                            override fun onPageFinished(view: WebView, url: String) {
                                TavernRuntimeManager.appendLog(
                                    "info",
                                    "[ext-settings] page ready folder=$folder url=$url",
                                )
                            }
                        }
                        // 设置页里的扩展也可能使用 confirm/alert；给一个最小 WebChromeClient，避免静默失败。
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                TavernRuntimeManager.appendLog(
                                    "debug",
                                    "[ext-settings][console] ${consoleMessage.message()} " +
                                        "@${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                                )
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
                                android.net.Uri.encode(folder),
                        )
                    }
                },
                onRelease = { released ->
                    if (webViewHolder[0] === released) webViewHolder[0] = null
                    TavernRuntimeManager.unregisterAuxWebView(released)
                    runCatching {
                        released.stopLoading()
                        released.loadUrl("about:blank")
                        released.destroy()
                    }
                },
            )
        }
    }
}
