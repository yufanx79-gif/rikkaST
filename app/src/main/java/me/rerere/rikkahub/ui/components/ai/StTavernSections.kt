package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.runtime.TavernScriptBrief
import me.rerere.rikkahub.data.st.runtime.appendGlobalScripts
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.rikkahub.data.st.runtime.extractScriptsFromJson
import me.rerere.rikkahub.data.st.runtime.listCardScripts
import me.rerere.rikkahub.data.st.runtime.listGlobalScripts
import me.rerere.rikkahub.data.st.runtime.listPresetScriptBuckets
import me.rerere.rikkahub.data.st.runtime.removeGlobalScript
import me.rerere.rikkahub.data.st.runtime.setCardScriptEnabled
import me.rerere.rikkahub.data.st.runtime.setGlobalScriptEnabled
import me.rerere.rikkahub.data.st.runtime.setPresetScriptEnabled
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.extensions.RegexScriptEditorDialog
import me.rerere.rikkahub.ui.pages.extensions.scriptSummary
import kotlin.uuid.Uuid

/**
 * 共享自 AssistantExtensionsPage 的「正则 / 预设 / 脚本」三个页签内容。
 *
 * 同时服务两处入口：
 * 1) 助手编辑 → 扩展（AssistantExtensionsPage）
 * 2) 聊天输入框 → 扩展管理快捷面板（ExtensionSelector）
 *
 * 保证两处入口展示与操作完全一致，避免能力缺口。
 */

/** 助手「正则」页签：卡内嵌正则脚本（查看/开关/编辑 + 前往管理）。 */
@Composable
internal fun EmbeddedRegexSection(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onNavigateToRegexScripts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tavData = assistant.tavernData
    val embeddedRegex = tavData?.embeddedRegexScripts.orEmpty()
    var editingRegex by remember { mutableStateOf<RegexScript?>(null) }
    if (embeddedRegex.isEmpty()) {
        ExtensionEmptyState(
            message = "此助手没有内嵌正则脚本。导入带正则的角色卡后会自动显示在这里。",
            buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
            onAction = onNavigateToRegexScripts,
        )
    } else {
        Column(modifier = modifier) {
            EmbeddedRegexScriptsContent(
                modifier = Modifier.weight(1f),
                scripts = embeddedRegex,
                onEdit = { editingRegex = it },
                onToggle = { script, checked ->
                    val newList = embeddedRegex.map {
                        if (it.id == script.id) it.copy(disabled = !checked) else it
                    }
                    onUpdateAssistant(assistant.copy(tavernData = tavData?.copy(embeddedRegexScripts = newList)))
                },
            )
            TextButton(
                onClick = onNavigateToRegexScripts,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往正则脚本管理")
            }
        }
    }
    editingRegex?.let { script ->
        RegexScriptEditorDialog(
            script = script,
            onDismiss = { editingRegex = null },
            onSave = { updated ->
                val newList = embeddedRegex.map {
                    if (it.id == updated.id) updated else it
                }
                onUpdateAssistant(assistant.copy(tavernData = tavData?.copy(embeddedRegexScripts = newList)))
                editingRegex = null
            },
        )
    }
}

/** 助手「预设」页签：模式注入预设分组（整组开关 + 查看条目）。 */
@Composable
internal fun TavernPresetsSection(
    modeInjections: List<PromptInjection.ModeInjection>,
    selectedIds: Set<Uuid>,
    onChangeSelectedIds: (Set<Uuid>) -> Unit,
    onNavigateToPrompts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = modeInjections
        .filter { !it.presetGroup.isNullOrBlank() }
        .groupBy { it.presetGroup!! }
    val ungrouped = modeInjections.filter { it.presetGroup.isNullOrBlank() }
    var showGroup by remember { mutableStateOf<String?>(null) }
    if (grouped.isEmpty() && ungrouped.isEmpty()) {
        ExtensionEmptyState(
            message = "暂无预设。在主设置 → 模式注入中导入酒馆预设后，可在这里为助手一键开启。",
            buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
            onAction = onNavigateToPrompts,
        )
    } else {
        Column(modifier = modifier) {
            PresetsContent(
                modifier = Modifier.weight(1f),
                presetGroups = grouped,
                ungrouped = ungrouped,
                selectedIds = selectedIds,
                onToggleGroup = { ids, checked ->
                    onChangeSelectedIds(if (checked) selectedIds + ids else selectedIds - ids)
                },
                onShowEntries = { showGroup = it },
            )
            TextButton(
                onClick = onNavigateToPrompts,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
            }
        }
    }
    showGroup?.let { groupName ->
        val entries = grouped[groupName].orEmpty()
        AlertDialog(
            onDismissRequest = { showGroup = null },
            title = { Text(groupName) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        Text(
                            text = "· " + entry.name.ifBlank { "未命名" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroup = null }) { Text("关闭") }
            },
        )
    }
}

/** 助手「脚本」页签：卡内脚本 / 全局脚本 / 预设脚本（酒馆助手脚本管理）。 */
@Composable
internal fun TavernScriptsSection(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSettings: (Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scriptTavData = assistant.tavernData
    val cardScripts = remember(scriptTavData) { listCardScripts(assistant) }
    val globalScripts = remember(settings.tavernGlobalScripts) {
        listGlobalScripts(settings.tavernGlobalScripts)
    }
    val presetBuckets = remember(settings.tavernPresetScripts) {
        listPresetScriptBuckets(settings.tavernPresetScripts)
    }
    val enabledGroups = remember(assistant.modeInjectionIds, settings.modeInjections) {
        enabledPresetGroups(assistant, settings)
    }
    var viewingScript by remember { mutableStateOf<Pair<TavernScriptBrief, String>?>(null) }
    val scriptsContext = LocalContext.current
    val scriptsToaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    scriptsContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            val imported = extractScriptsFromJson(text.orEmpty())
            if (imported.isEmpty()) {
                scriptsToaster.show("未识别到脚本（支持 JSR / 酒馆助手脚本 JSON）")
            } else {
                val (newJson, added) = appendGlobalScripts(settings.tavernGlobalScripts, imported)
                onUpdateSettings(settings.copy(tavernGlobalScripts = newJson))
                scriptsToaster.show("已导入 $added 个全局脚本")
            }
        }
    }
    ScriptsContent(
        modifier = modifier.fillMaxSize(),
        cardScripts = cardScripts,
        globalScripts = globalScripts,
        globalScriptsEnabled = settings.tavernGlobalScriptsEnabled,
        presetBuckets = presetBuckets,
        enabledGroups = enabledGroups,
        onToggleCard = { brief, checked ->
            val newRaw = setCardScriptEnabled(scriptTavData?.extensionsRaw, brief.id, checked)
            if (newRaw != null) {
                onUpdateAssistant(assistant.copy(tavernData = scriptTavData?.copy(extensionsRaw = newRaw)))
            }
        },
        onToggleGlobal = { brief, checked ->
            val newJson = setGlobalScriptEnabled(settings.tavernGlobalScripts, brief.id, checked)
            if (newJson != null) {
                onUpdateSettings(settings.copy(tavernGlobalScripts = newJson))
            }
        },
        onToggleGlobalMaster = { checked ->
            onUpdateSettings(settings.copy(tavernGlobalScriptsEnabled = checked))
        },
        onTogglePresetScript = { group, brief, checked ->
            val newJson = setPresetScriptEnabled(settings.tavernPresetScripts, group, brief.id, checked)
            if (newJson != null) {
                onUpdateSettings(settings.copy(tavernPresetScripts = newJson))
            }
        },
        onTogglePresetGroup = { group, checked ->
            val ids = settings.modeInjections
                .filter { it.presetGroup == group }
                .map { it.id }
                .toSet()
            val newIds = if (checked) assistant.modeInjectionIds + ids
            else assistant.modeInjectionIds - ids
            onUpdateAssistant(assistant.copy(modeInjectionIds = newIds))
        },
        onImportGlobal = { importLauncher.launch(arrayOf("*/*")) },
        onView = { brief, tag -> viewingScript = brief to tag },
    )
    viewingScript?.let { (brief, tag) ->
        val deleteAction: () -> Unit = {
            val newJson = removeGlobalScript(settings.tavernGlobalScripts, brief.id)
            if (newJson != null) {
                onUpdateSettings(settings.copy(tavernGlobalScripts = newJson))
            }
            viewingScript = null
        }
        ScriptDetailDialog(
            script = brief,
            sourceTag = tag,
            onDismiss = { viewingScript = null },
            onDelete = if (tag == "global") deleteAction else null,
        )
    }
}
@Composable
private fun EmbeddedRegexScriptsContent(
    scripts: List<RegexScript>,
    onEdit: (RegexScript) -> Unit,
    onToggle: (RegexScript, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(scripts, key = { it.id }) { script ->
            ListItem(
                headlineContent = { Text(script.scriptName.ifBlank { "未命名正则" }) },
                supportingContent = {
                    Text(
                        text = scriptSummary(script),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = !script.disabled,
                        onCheckedChange = { checked -> onToggle(script, checked) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onEdit(script) },
            )
        }
    }
}


@Composable
private fun PresetsContent(
    presetGroups: Map<String, List<PromptInjection.ModeInjection>>,
    ungrouped: List<PromptInjection.ModeInjection>,
    selectedIds: Set<kotlin.uuid.Uuid>,
    onToggleGroup: (Set<kotlin.uuid.Uuid>, Boolean) -> Unit,
    onShowEntries: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presetGroups.forEach { (name, list) ->
            item(key = "preset:" + name) {
                val allSelected = list.all { selectedIds.contains(it.id) }
                val someSelected = list.any { selectedIds.contains(it.id) }
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = {
                        Text(
                            text = "${list.size} 条注入" + if (someSelected && !allSelected) "（部分开启）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = allSelected,
                            onCheckedChange = { checked -> onToggleGroup(list.map { it.id }.toSet(), checked) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onShowEntries(name) },
                )
            }
        }
        if (ungrouped.isNotEmpty()) {
            item(key = "preset:__ungrouped__") {
                val allSelected = ungrouped.all { selectedIds.contains(it.id) }
                ListItem(
                    headlineContent = { Text("未分组条目") },
                    supportingContent = {
                        Text(
                            text = "${ungrouped.size} 条注入（历史导入或单独添加）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = allSelected,
                            onCheckedChange = { checked -> onToggleGroup(ungrouped.map { it.id }.toSet(), checked) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}


@Composable
private fun ScriptsContent(
    cardScripts: List<TavernScriptBrief>,
    globalScripts: List<TavernScriptBrief>,
    globalScriptsEnabled: Boolean,
    presetBuckets: Map<String, List<TavernScriptBrief>>,
    enabledGroups: Set<String>,
    onToggleCard: (TavernScriptBrief, Boolean) -> Unit,
    onToggleGlobal: (TavernScriptBrief, Boolean) -> Unit,
    onToggleGlobalMaster: (Boolean) -> Unit,
    onTogglePresetScript: (String, TavernScriptBrief, Boolean) -> Unit,
    onTogglePresetGroup: (String, Boolean) -> Unit,
    onImportGlobal: () -> Unit,
    onView: (TavernScriptBrief, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "hdr:card") { ScriptSectionHeader("卡内脚本（角色卡）") }
        if (cardScripts.isEmpty()) {
            item(key = "empty:card") {
                ScriptHint("无卡内脚本。导入带「酒馆助手」脚本的角色卡后会自动显示。")
            }
        } else {
            items(cardScripts, key = { "card:" + it.id }) { s ->
                ScriptRow(
                    script = s,
                    onToggle = { c -> onToggleCard(s, c) },
                    onClick = { onView(s, "card") },
                )
            }
        }

        item(key = "hdr:global") { ScriptSectionHeader("全局脚本") }
        item(key = "global:master") {
            ListItem(
                headlineContent = { Text("总开关") },
                supportingContent = {
                    Text(
                        text = "关闭后所有全局脚本不装载（对齐酒馆助手 enabled.global）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                },
                trailingContent = {
                    Switch(checked = globalScriptsEnabled, onCheckedChange = onToggleGlobalMaster)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        item(key = "global:import") {
            TextButton(onClick = onImportGlobal, modifier = Modifier.fillMaxWidth()) {
                Text("导入脚本文件（JSR / 酒馆助手脚本 JSON）")
            }
        }
        if (globalScripts.isEmpty()) {
            item(key = "empty:global") {
                ScriptHint("暂无全局脚本。可导入脚本 JSON（支持数组 / scripts / tavern_helper 结构）。")
            }
        } else {
            items(globalScripts, key = { "global:" + it.id }) { s ->
                ScriptRow(
                    script = s,
                    onToggle = { c -> onToggleGlobal(s, c) },
                    onClick = { onView(s, "global") },
                )
            }
        }

        item(key = "hdr:preset") { ScriptSectionHeader("预设脚本") }
        if (presetBuckets.isEmpty()) {
            item(key = "empty:preset") {
                ScriptHint("暂无预设脚本。导入带「酒馆助手」脚本的酒馆预设后会自动显示。")
            }
        } else {
            presetBuckets.forEach { (group, list) ->
                item(key = "pg:" + group) {
                    val active = enabledGroups.contains(group)
                    ListItem(
                        headlineContent = { Text(group) },
                        supportingContent = {
                            Text(
                                text = if (active) "已随预设启用 · ${list.size} 个脚本"
                                else "未启用（在「预设」页签开启该预设后生效）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        },
                        trailingContent = {
                            Switch(checked = active, onCheckedChange = { c -> onTogglePresetGroup(group, c) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                items(list, key = { "pg:" + group + ":" + it.id }) { s ->
                    ScriptRow(
                        script = s,
                        onToggle = { c -> onTogglePresetScript(group, s, c) },
                        onClick = { onView(s, "preset:" + group) },
                        indented = true,
                    )
                }
            }
        }
        item(key = "tail") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun ScriptSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScriptHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ScriptRow(
    script: TavernScriptBrief,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    indented: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(script.name.ifBlank { "未命名脚本" }) },
        supportingContent = {
            Text(
                text = buildString {
                    if (script.info.isNotBlank()) append(script.info.take(60))
                    else append("${script.content.length} 字符")
                    if (!script.enabled) append(" · 已停用")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        },
        trailingContent = { Switch(checked = script.enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable { onClick() }
            .then(if (indented) Modifier.padding(start = 12.dp) else Modifier),
    )
}

@Composable
private fun ScriptDetailDialog(
    script: TavernScriptBrief,
    sourceTag: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(script.name.ifBlank { "未命名脚本" }) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val sourceLabel = when {
                    sourceTag == "card" -> "角色卡"
                    sourceTag == "global" -> "全局"
                    sourceTag.startsWith("preset:") -> "预设 · " + sourceTag.removePrefix("preset:")
                    else -> sourceTag
                }
                Text(
                    text = "来源：$sourceLabel",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (script.info.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = script.info, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ID: ${script.id} · ${script.content.length} 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                val preview = if (script.content.length > 4000) {
                    script.content.take(4000) + "\n…（已截断，完整 ${script.content.length} 字符）"
                } else {
                    script.content
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            if (onDelete != null && sourceTag == "global") {
                TextButton(onClick = onDelete) { Text("删除") }
            }
        },
    )
}
