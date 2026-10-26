package me.rerere.rikkahub.ui.components.chat

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.ValueCallback
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import kotlin.math.roundToInt

/**
 * 聊天页右侧常驻悬浮球与酒馆通用悬浮窗宿主（[TavernFloatingWindowOverlay]）：
 *
 * 1. **小手机 (`ST-iPhonie`) 右侧常驻**：
 *    在聊天页面右侧边缘常驻显示可上下拖拽的「📱 小手机 / 悬浮窗」胶囊按钮；当小手机正在播放角色 TTS 语音时，
 *    胶囊实时展示「🔊 配音中」状态并提供一键停止按钮，方便随时点开听声音或切歌/看朋友圈。
 * 2. **角色卡与多插件通用悬浮窗适配（由此及彼）**：
 *    同时支持切换至「📱 小手机 (ST-iPhonie)」、「📊 记忆表格 (st-memory-enhancement)」、
 *    「📦 小白X (LittleWhiteBox)」以及「🗺️ 角色卡悬浮窗」（自动捕获角色卡 `TH-script` 脚本挂载的手机、地图、背包、状态 HUD 等悬浮窗）。
 * 3. **完整支持二级界面、文件选择、JS 弹窗与「本聊天不再显示」**：
 *    - 接线 `webViewFileChooserLauncher` 处理 `<input type="file">`（换头像、换壁纸、导入配置）；
 *    - 接线 `webViewJsDialogFlow` 处理 `alert` / `confirm` / `prompt`；
 *    - 支持「本聊天不再显示」与 JS 侧关闭回执，真正收起弹窗并记忆当前会话隐藏状态。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.TavernFloatingWindowOverlay() {
    val context = LocalContext.current
    val summary by TavernRuntimeManager.floatingWindowsFlow.collectAsState()
    val ttsState by TavernRuntimeManager.stIphonieTtsStateFlow.collectAsState()
    val requestedTarget by TavernRuntimeManager.requestedFloatingTargetFlow.collectAsState()
    val isBootReady by TavernRuntimeManager.isBootReadyFlow.collectAsState()
    val hiddenVersion by TavernRuntimeManager.floatingHiddenVersionFlow.collectAsState()
    val jsDialogEvent by TavernRuntimeManager.webViewJsDialogFlow.collectAsState()
    val activeConvId = TavernRuntimeManager.activeConversationId

    // 接线 WebView <input type="file"> 文件选择器（供小手机与角色浮窗换头像、上传背景、导入配置）
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val cb = pendingFileCallback
        pendingFileCallback = null
        if (cb != null) {
            if (uri != null) {
                cb.onReceiveValue(arrayOf(uri))
            } else {
                cb.onReceiveValue(null)
            }
        }
    }

    DisposableEffect(Unit) {
        TavernRuntimeManager.webViewFileChooserLauncher = { mimeTypes, _, callback ->
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = callback
            val filter = mimeTypes.filter { it.isNotBlank() }.toTypedArray().ifEmpty { arrayOf("*/*") }
            runCatching {
                filePickerLauncher.launch(filter)
                true
            }.getOrElse {
                pendingFileCallback = null
                false
            }
        }
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
        }
    }

    // 渲染 WebView JS alert / confirm / prompt 弹窗
    jsDialogEvent?.let { dialog ->
        var promptText by remember(dialog) { mutableStateOf(dialog.defaultValue) }
        AlertDialog(
            onDismissRequest = { dialog.onCancel() },
            title = {
                Text(
                    when (dialog.type) {
                        "confirm" -> "确认操作"
                        "prompt" -> "输入内容"
                        else -> "提示"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dialog.message.isNotBlank()) {
                        Text(dialog.message)
                    }
                    if (dialog.type == "prompt") {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialog.onConfirm(promptText) }) {
                    Text("确定")
                }
            },
            dismissButton = if (dialog.type == "confirm" || dialog.type == "prompt") {
                {
                    TextButton(onClick = { dialog.onCancel() }) {
                        Text("取消")
                    }
                }
            } else null,
        )
    }

    // [问题 3] 只显示 DOM 真正就绪的目标；扩展“已安装”不再冒充可打开。
    val hasIphonie = summary.hasIphonie
    val hasMemory = summary.hasMemory
    val hasLittleWhiteBox = summary.hasLittleWhiteBox
    val hasCardFloating = summary.hasCardFloating &&
        !TavernRuntimeManager.isFloatingTargetHidden(activeConvId, "card_floating")
    val showResidentDock = hasIphonie || hasMemory || hasLittleWhiteBox || summary.hasCardFloating

    if (!showResidentDock && requestedTarget == null) return

    var dragOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var activeTarget by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedTarget, hiddenVersion) {
        if (requestedTarget != null) {
            if (!TavernRuntimeManager.isFloatingTargetHidden(activeConvId, requestedTarget!!)) {
                activeTarget = requestedTarget
            } else {
                activeTarget = null
            }
        } else {
            activeTarget = null
        }
    }

    // 右侧常驻可上下拖拽悬浮胶囊
    Surface(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-680f, 680f)
                }
            }
            .padding(end = 4.dp),
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
        color = Color(0xE61B1E2B),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    // 用户主动点右侧胶囊时，若此前点过「本聊天不再显示」，允许恢复打开
                    if (summary.hasCardFloating && !hasCardFloating && !hasIphonie) {
                        TavernRuntimeManager.clearHiddenFloatingTargetsForCurrentChat()
                    }
                    val candidates = listOfNotNull(
                        TavernRuntimeManager.lastSuccessfulFloatingTarget,
                        "memory_table".takeIf { hasMemory },
                        "card_floating".takeIf { summary.hasCardFloating },
                        "iphonie".takeIf { hasIphonie },
                        "littlewhitebox".takeIf { hasLittleWhiteBox },
                    )
                    val defaultTarget = candidates.firstOrNull { target ->
                        when (target) {
                            "iphonie" -> hasIphonie
                            "memory_table", "memory" -> hasMemory
                            "littlewhitebox", "lwb" -> hasLittleWhiteBox
                            "card_floating", "card" -> summary.hasCardFloating
                            else -> false
                        }
                    } ?: "iphonie"
                    activeTarget = defaultTarget
                    TavernRuntimeManager.requestOpenFloatingWindow(defaultTarget)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when {
                    ttsState.playing -> "🔊 配音中"
                    hasIphonie -> "📱 小手机"
                    hasCardFloating -> "🗺️ 悬浮窗"
                    hasMemory -> "📊 记忆表"
                    else -> "📦 悬浮窗"
                },
                color = Color(0xFFEAF0FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (ttsState.playing) {
                Text(
                    text = "⏹",
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            TavernRuntimeManager.stopStIphonieTts("dock_stop_button")
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }

    val currentModalTarget = activeTarget ?: requestedTarget
    if (currentModalTarget != null) {
        TavernFloatingWindowModal(
            initialTarget = currentModalTarget,
            hasIphonie = hasIphonie,
            hasMemory = hasMemory,
            hasLittleWhiteBox = hasLittleWhiteBox,
            hasCardFloating = summary.hasCardFloating,
            ttsPlaying = ttsState.playing,
            isBootReady = isBootReady,
            onHideForCurrentChat = { targetToHide ->
                activeTarget = null
                TavernRuntimeManager.hideFloatingTargetForCurrentChat(targetToHide)
            },
            onDismiss = {
                activeTarget = null
                TavernRuntimeManager.dismissFloatingWindow()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TavernFloatingWindowModal(
    initialTarget: String = "iphonie",
    hasIphonie: Boolean = true,
    hasMemory: Boolean = true,
    hasLittleWhiteBox: Boolean = true,
    hasCardFloating: Boolean = false,
    ttsPlaying: Boolean = false,
    isBootReady: Boolean = true,
    onHideForCurrentChat: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var selectedTarget by remember(initialTarget) { mutableStateOf(initialTarget) }
    val containerHolder = remember { arrayOfNulls<FrameLayout>(1) }
    val targetState by TavernRuntimeManager.floatingTargetStateFlow.collectAsState()

    LaunchedEffect(selectedTarget, isBootReady) {
        containerHolder[0]?.let { frame ->
            TavernRuntimeManager.attachHostWebViewToContainer(frame, selectedTarget)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            containerHolder[0]?.let { frame ->
                TavernRuntimeManager.detachHostWebViewFromContainer(frame)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(18.dp)),
            color = Color(0xFF121318),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部通用悬浮窗切换栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B1E28))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (hasIphonie) {
                            FilterChip(
                                selected = selectedTarget == "iphonie",
                                onClick = {
                                    if (selectedTarget != "iphonie") {
                                        selectedTarget = "iphonie"
                                        TavernRuntimeManager.switchFloatingWindowTarget("iphonie")
                                    }
                                },
                                label = { Text("📱 小手机", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF355691),
                                    selectedLabelColor = Color.White,
                                    labelColor = Color(0xFFD5DBE8),
                                ),
                            )
                        }
                        if (hasMemory) {
                            FilterChip(
                                selected = selectedTarget == "memory_table",
                                onClick = {
                                    if (selectedTarget != "memory_table") {
                                        selectedTarget = "memory_table"
                                        TavernRuntimeManager.switchFloatingWindowTarget("memory_table")
                                    }
                                },
                                label = { Text("📊 记忆表格", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF355691),
                                    selectedLabelColor = Color.White,
                                    labelColor = Color(0xFFD5DBE8),
                                ),
                            )
                        }
                        if (hasLittleWhiteBox) {
                            FilterChip(
                                selected = selectedTarget == "littlewhitebox",
                                onClick = {
                                    if (selectedTarget != "littlewhitebox") {
                                        selectedTarget = "littlewhitebox"
                                        TavernRuntimeManager.switchFloatingWindowTarget("littlewhitebox")
                                    }
                                },
                                label = { Text("📦 小白X", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF355691),
                                    selectedLabelColor = Color.White,
                                    labelColor = Color(0xFFD5DBE8),
                                ),
                            )
                        }
                        FilterChip(
                            selected = selectedTarget == "card_floating",
                            onClick = {
                                if (selectedTarget != "card_floating") {
                                    selectedTarget = "card_floating"
                                    TavernRuntimeManager.switchFloatingWindowTarget("card_floating")
                                }
                            },
                            label = {
                                Text(
                                    text = if (hasCardFloating) "🗺️ 角色卡悬浮窗 ●" else "🗺️ 角色卡悬浮窗",
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF355691),
                                selectedLabelColor = Color.White,
                                labelColor = Color(0xFFD5DBE8),
                            ),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ttsPlaying) {
                            TextButton(
                                onClick = { TavernRuntimeManager.stopStIphonieTts("modal_stop_btn") },
                            ) {
                                Text("⏹ 停音", color = Color(0xFFFF8A80), fontSize = 12.sp)
                            }
                        }
                        if (onHideForCurrentChat != null) {
                            TextButton(
                                onClick = { onHideForCurrentChat(selectedTarget) },
                            ) {
                                Text("不再显示", color = Color(0xFF9EA4B8), fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        TextButton(onClick = onDismiss) {
                            Text("✕ 收起", color = Color(0xFFE4E6EF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val failedState = targetState as? TavernRuntimeManager.FloatingTargetState.Failed
                if (failedState != null && failedState.target == selectedTarget) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3A1E22))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "无法打开该悬浮窗：${failedState.reason}",
                            color = Color(0xFFFFC2C7),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                TavernRuntimeManager.retryFloatingWindowTarget(selectedTarget)
                            },
                        ) {
                            Text("重试", color = Color(0xFFEAF0FF), fontSize = 12.sp)
                        }
                    }
                }

                // 挂载常驻主运行时 WebView（不重新加载页面、不断开 IndexedDB、不中断音频）
                // 注意：不在 update 回调中无条件重调 attachHostWebViewToContainer，防止 Compose 重组（如 TTS 状态跳动）
                // 反复执行 __rikkaOpenFloatingTarget 导致小手机二级菜单/弹窗（sheet/dialog）被重置关闭！
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(AndroidColor.parseColor("#121318"))
                            containerHolder[0] = this
                            TavernRuntimeManager.attachHostWebViewToContainer(this, selectedTarget)
                        }
                    },
                    update = { container ->
                        containerHolder[0] = container
                    },
                    onRelease = { container ->
                        if (containerHolder[0] === container) containerHolder[0] = null
                        TavernRuntimeManager.detachHostWebViewFromContainer(container)
                    },
                )
            }
        }
    }
}
