package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.st.expressions.ExpressionClassifier
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.st.expressions.SpriteRepository
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.PageScaffold
import me.rerere.rikkahub.ui.components.ui.SegmentedTabs
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * [v240 W3] 表情立绘设置页（Character Expressions）。
 *
 * 对齐官方 expressions 扩展的设置面（总开关 / 分类方式 / 兜底标签 / 标签表），
 * 并补上 rikkaST 特有的「按角色立绘文件夹」管理（多选图片导入、删除、切换目标角色）。
 *
 * 分类方式只保留 llm / none（rikkaST 是客户端模型，天然走 llm；官方 local/extras/webllm
 * 需要服务端或浏览器环境，本批不移植 —— 见 docs/ST-FEATURE-MATRIX.md §17）。
 */
@Composable
fun SettingPreferencesExpressionPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val assistants = settings.assistants
    var selectedAssistantId by rememberSaveable { mutableStateOf(settings.assistantId.toString()) }
    val selectedAssistant = assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: assistants.firstOrNull()

    var spriteLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var showFallbackDialog by remember { mutableStateOf(false) }
    var showAssistantDialog by remember { mutableStateOf(false) }
    var showLabelsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedAssistant?.id, refreshTick) {
        val assistant = selectedAssistant
        spriteLabels = if (assistant == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { SpriteRepository.listLabels(context, assistant.id) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        val target = selectedAssistant
        if (uris.isEmpty()) {
            toaster.show(
                context.getString(R.string.setting_expression_import_empty),
                type = ToastType.Warning,
            )
            return@rememberLauncherForActivityResult
        }
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (ok, skipped) = withContext(Dispatchers.IO) {
                var okCount = 0
                var skippedCount = 0
                uris.forEach { uri ->
                    val label = SpriteRepository.importFromUri(context, target.id, uri)
                    if (label != null) okCount++ else skippedCount++
                }
                okCount to skippedCount
            }
            refreshTick++
            toaster.show(
                context.getString(R.string.setting_expression_import_done, ok, skipped),
                type = if (skipped > 0) ToastType.Warning else ToastType.Success,
            )
        }
    }

    PageScaffold(
        title = stringResource(R.string.setting_expression_title),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_expression_enabled_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_expression_enabled_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.expressionEnabled,
                                onCheckedChange = { enabled ->
                                    vm.patchSettings { it.copy(expressionEnabled = enabled) }
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_expression_classifier_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_expression_classifier_desc)) },
                    )
                    item(
                        headlineContent = {
                            SegmentedTabs(
                                items = listOf(ExpressionClassifier.LLM, ExpressionClassifier.NONE),
                                selected = settings.expressionClassifier,
                                onSelect = { classifier ->
                                    vm.patchSettings { it.copy(expressionClassifier = classifier) }
                                },
                                label = { classifier ->
                                    when (classifier) {
                                        ExpressionClassifier.LLM -> stringResourceOf(context, R.string.setting_expression_classifier_llm)
                                        ExpressionClassifier.NONE -> stringResourceOf(context, R.string.setting_expression_classifier_none)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        onClick = { showFallbackDialog = true },
                        headlineContent = { Text(stringResource(R.string.setting_expression_fallback_title)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.setting_expression_current,
                                    settings.expressionFallbackLabel,
                                )
                            )
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.setting_expression_change),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    item(
                        onClick = { showLabelsDialog = true },
                        headlineContent = { Text(stringResource(R.string.setting_expression_labels_title)) },
                        supportingContent = {
                            Text(
                                ExpressionLabels.ALL.joinToString("、")
                            )
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.setting_expression_view),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }

            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        onClick = { showAssistantDialog = true },
                        headlineContent = { Text(stringResource(R.string.setting_expression_sprites_title)) },
                        supportingContent = {
                            Column {
                                Text(
                                    stringResource(
                                        R.string.setting_expression_sprites_assistant,
                                        selectedAssistant?.name ?: "-",
                                    )
                                )
                                Text(stringResource(R.string.setting_expression_sprites_desc))
                            }
                        },
                        trailingContent = {
                            Text(
                                text = stringResource(R.string.setting_expression_change),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    item(
                        headlineContent = {
                            if (spriteLabels.isEmpty()) {
                                Text(stringResource(R.string.setting_expression_sprites_empty))
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(spriteLabels, key = { it }) { label ->
                                        SpriteThumb(
                                            label = label,
                                            file = remember(label, selectedAssistant?.id, refreshTick) {
                                                selectedAssistant?.let { assistant ->
                                                    SpriteRepository.resolveFile(context, assistant.id, label)
                                                }
                                            },
                                            onDelete = {
                                                val assistant = selectedAssistant ?: return@SpriteThumb
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        SpriteRepository.deleteLabel(context, assistant.id, label)
                                                    }
                                                    refreshTick++
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    item(
                        headlineContent = {
                            TextButton(
                                onClick = {
                                    if (selectedAssistant == null) {
                                        toaster.show(
                                            context.getString(R.string.setting_expression_no_assistant),
                                            type = ToastType.Warning,
                                        )
                                    } else {
                                        importLauncher.launch("image/*")
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.setting_expression_import))
                            }
                        },
                    )
                }
            }
        }
    }

    if (showFallbackDialog) {
        ExpressionPickerDialog(
            title = stringResource(R.string.setting_expression_fallback_title),
            options = listOf(ExpressionLabels.OPTION_NONE, ExpressionLabels.OPTION_EMOJI) + ExpressionLabels.ALL,
            selected = settings.expressionFallbackLabel,
            onDismiss = { showFallbackDialog = false },
            onSelect = { label ->
                showFallbackDialog = false
                vm.patchSettings { it.copy(expressionFallbackLabel = label) }
            },
        )
    }

    if (showAssistantDialog) {
        ExpressionPickerDialog(
            title = stringResource(R.string.setting_expression_sprites_title),
            options = assistants.map { it.id.toString() } + assistants.map { it.name },
            selected = selectedAssistant?.id?.toString(),
            labelOf = { value ->
                assistants.firstOrNull { it.id.toString() == value }?.name ?: value
            },
            onDismiss = { showAssistantDialog = false },
            onSelect = { value ->
                showAssistantDialog = false
                selectedAssistantId = value
            },
        )
    }

    if (showLabelsDialog) {
        AlertDialog(
            onDismissRequest = { showLabelsDialog = false },
            confirmButton = {
                TextButton(onClick = { showLabelsDialog = false }) {
                    Text(stringResource(R.string.setting_expression_close))
                }
            },
            title = { Text(stringResource(R.string.setting_expression_labels_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpressionLabels.ALL.forEach { label ->
                        Text(text = label, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun SpriteThumb(
    label: String,
    file: java.io.File?,
    onDelete: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = file?.let { Uri.fromFile(it) },
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(72.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onDelete) {
            Text(text = stringResource(R.string.setting_expression_delete))
        }
    }
}

@Composable
private fun ExpressionPickerDialog(
    title: String,
    options: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String = { it },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.setting_expression_cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.distinct().forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = labelOf(option))
                        TextButton(onClick = { onSelect(option) }) {
                            Text(
                                text = if (option == selected) {
                                    stringResource(R.string.setting_expression_selected)
                                } else {
                                    stringResource(R.string.setting_expression_select)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 在非 Composable 回调里取字符串（SegmentedTabs 的 label 是普通 lambda） */
private fun stringResourceOf(context: android.content.Context, id: Int): String =
    context.getString(id)
