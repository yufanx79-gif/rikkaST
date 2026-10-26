package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.WebViewPerfMode
import me.rerere.rikkahub.ui.components.ui.CardGroup

/**
 * 问题四专属设置组件：性能降低开关（WebView 楼层渲染性能优化）及三套可选方案。
 *
 * - 当「性能降低开关」开启时，向用户展示三个方案：
 *   1. 方案一（[WebViewPerfMode.HIBERNATE_POOL]）：后台面板休眠方案（保留最近活跃面板，早期面板自动休眠并支持手动唤醒）；
 *   2. 方案二（[WebViewPerfMode.LIVE_QUIESCENT]）：全视觉保活 + 静默期降频方案（所有楼层保持完整视觉显示，渲染稳定后自动冻结后台轮询定时器）；
 *   3. 方案三（[WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE]）：历史楼层固化为图片方案。
 *      点击方案三时弹窗提醒用户：“如果选择这个方案，你就不能修改助手的消息。”
 *      - 选择“好的”：切换到方案三，历史楼层 WebView 渲染结果固化为静态图片且锁定助手消息编辑；
 *      - 选择“取消”：不做任何改变，维持用户原来的选择。
 */
@Composable
fun WebViewPerfReductionCard(
    displaySetting: DisplaySetting,
    onUpdateDisplaySetting: (DisplaySetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSnapshotModeConfirmDialog by remember { mutableStateOf(false) }

    if (showSnapshotModeConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                // 点击遮罩或返回键等同于“取消”，维持用户原来的选择
                showSnapshotModeConfirmDialog = false
            },
            title = { Text("提示") },
            text = {
                Text("如果选择这个方案，你就不能修改助手的消息。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSnapshotModeConfirmDialog = false
                        onUpdateDisplaySetting(
                            displaySetting.copy(
                                enableWebViewPerfReduction = true,
                                webViewPerfMode = WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE,
                            )
                        )
                    }
                ) {
                    Text("好的")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSnapshotModeConfirmDialog = false
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }

    CardGroup(
        modifier = modifier,
        title = { Text("WebView 渲染与性能降低设置") },
    ) {
        item(
            headlineContent = { Text("性能降低开关") },
            supportingContent = {
                Text("基础安全预算（首屏懒加载、活跃 WebView 数量上限、离屏暂停、快照内存上限）始终生效；这里选择历史楼层的额外降载方案。")
            },
            trailingContent = {
                Switch(
                    checked = displaySetting.enableWebViewPerfReduction,
                    onCheckedChange = { enabled ->
                        onUpdateDisplaySetting(
                            displaySetting.copy(enableWebViewPerfReduction = enabled)
                        )
                    },
                )
            },
        )

        if (displaySetting.enableWebViewPerfReduction) {
            item(
                headlineContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerfModeOptionItem(
                            title = "方案一：后台面板休眠方案",
                            description = "限制同时存活的 WebView 数量（最多保留 2 个活跃面板），更早的历史楼层自动休眠为轻量占位卡片，点击「唤醒面板」可随时恢复。",
                            selected = displaySetting.webViewPerfMode == WebViewPerfMode.HIBERNATE_POOL,
                            onClick = {
                                if (displaySetting.webViewPerfMode != WebViewPerfMode.HIBERNATE_POOL) {
                                    onUpdateDisplaySetting(
                                        displaySetting.copy(webViewPerfMode = WebViewPerfMode.HIBERNATE_POOL)
                                    )
                                }
                            },
                        )

                        PerfModeOptionItem(
                            title = "方案二：全视觉保活 + 静默降频方案",
                            description = "当前可见面板保持完整显示，离屏面板自动暂停定时器/动画并按预算休眠；滚动回来可即时唤醒。",
                            selected = displaySetting.webViewPerfMode == WebViewPerfMode.LIVE_QUIESCENT,
                            onClick = {
                                if (displaySetting.webViewPerfMode != WebViewPerfMode.LIVE_QUIESCENT) {
                                    onUpdateDisplaySetting(
                                        displaySetting.copy(webViewPerfMode = WebViewPerfMode.LIVE_QUIESCENT)
                                    )
                                }
                            },
                        )

                        PerfModeOptionItem(
                            title = "方案三：历史楼层固化为图片方案",
                            description = "历史楼层的 WebView 渲染完成后直接转换为静态图片保存在用户与助手的历史消息中，彻底省去历史楼层轮询开销（开启后不可修改助手消息）。",
                            selected = displaySetting.webViewPerfMode == WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE,
                            onClick = {
                                if (displaySetting.webViewPerfMode != WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE) {
                                    showSnapshotModeConfirmDialog = true
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PerfModeOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
