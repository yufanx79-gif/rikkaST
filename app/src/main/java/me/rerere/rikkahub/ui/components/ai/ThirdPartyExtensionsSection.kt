package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import me.rerere.rikkahub.data.st.extensions.installFromUrl
import me.rerere.rikkahub.data.st.extensions.installFromZipUri
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster

/**
 * 第三方扩展（SillyTavern third-party extensions）管理区。
 *
 * 同时服务两处入口（助手编辑 → 扩展；聊天输入框 → 扩展快捷面板）：
 * - 安装：SAF 选择 zip / http(s) URL 直链（zip 内需含 manifest.json，js 字段指向入口模块）；
 * - 列表：展示已安装扩展（名/目录/版本/作者/可加载性），支持启停（tavernThirdPartyDisabled）；
 * - 删除：移除扩展目录（二次确认）；
 * - 所有变更后触发运行时重载（若运行时已启动）：window.__rikkaReloadThirdPartyExtensions()。
 */
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
    var pendingDelete by remember { mutableStateOf<TavernExtensionInfo?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    fun reloadRuntime() {
        runCatching {
            TavernRuntimeManager.eval("window.__rikkaReloadThirdPartyExtensions && window.__rikkaReloadThirdPartyExtensions()")
        }
    }

    suspend fun refresh() {
        extensions = withContext(Dispatchers.IO) { listExtensions(context) }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = installFromZipUri(context, uri)
                busy = false
                result.onSuccess { info ->
                    toaster.show("已安装扩展：${info.displayName.ifBlank { info.folder }}")
                    reloadRuntime()
                }.onFailure { e ->
                    toaster.show("安装失败：${e.message ?: "未知错误"}")
                }
                refresh()
            }
        }
    }

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "第三方扩展",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "全局生效，所有角色共用（对齐 ST：扩展不绑定角色卡）。" +
                    "安装 SillyTavern 格式扩展：支持仓库 URL（GitHub / GitLab / Codeberg 等，自动探测分支）、manifest.json 直链、zip。" +
                    "扩展以原生 ES Module 注入酒馆运行时，相对导入自动映射到 rikkaST 兼容层（/script.js、/scripts/*.js）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                ) {
                    Icon(HugeIcons.FileImport, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("安装 ZIP")
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

        when {
            loading -> {
                Text(
                    text = "正在扫描扩展目录…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            extensions.isEmpty() -> {
                Text(
                    text = "暂未安装第三方扩展。\n可从 GitHub Releases 等处获取扩展 zip 后在此安装。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(extensions, key = { it.folder }) { ext ->
                        ExtensionRow(
                            ext = ext,
                            enabled = ext.folder !in settings.tavernThirdPartyDisabled,
                            onToggle = { checked ->
                                val disabled = settings.tavernThirdPartyDisabled
                                val newSet = if (checked) disabled - ext.folder else disabled + ext.folder
                                onUpdateSettings(settings.copy(tavernThirdPartyDisabled = newSet))
                                reloadRuntime()
                            },
                            onOpen = { navController.navigate(Screen.TavernExtensionSettings(ext.folder)) },
                            onDelete = { pendingDelete = ext },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { ext ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除扩展") },
            text = {
                Text("确定删除「${ext.displayName.ifBlank { ext.folder }}」？该操作会移除扩展目录且不可恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
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
