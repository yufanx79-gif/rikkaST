package me.rerere.rikkahub.ui.components.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.export.ModeInjectionSerializer
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
import me.rerere.rikkahub.ui.pages.extensions.ModeInjectionEditSheet
import me.rerere.rikkahub.ui.pages.extensions.RegexScriptEditorDialog
import me.rerere.rikkahub.ui.pages.extensions.scriptSummary

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


/**
 * 原子更新助手的预设组启用状态（单次变换同时更新 `modeInjections` 条目启用态与 `assistant.modeInjectionIds`，消除二次回调覆盖竞态）。
 */
internal fun togglePresetGroupForAssistant(
    current: Settings,
    assistantId: Uuid,
    groupName: String,
    enable: Boolean,
): Settings {
    val groupEntries = current.modeInjections.filter { it.presetGroup == groupName }
    if (groupEntries.isEmpty()) return current
    val updatedModeInjections = if (enable && groupEntries.none { it.enabled }) {
        current.modeInjections.map { e ->
            if (e.presetGroup == groupName) e.copy(enabled = true) else e
        }
    } else {
        current.modeInjections
    }
    val groupIds = groupEntries.map { it.id }.toSet()
    val updatedAssistants = current.assistants.map { a ->
        if (a.id == assistantId) {
            val newIds = if (enable) a.modeInjectionIds + groupIds else a.modeInjectionIds - groupIds
            a.copy(modeInjectionIds = newIds)
        } else {
            a
        }
    }
    return current.copy(
        modeInjections = updatedModeInjections,
        assistants = updatedAssistants,
    )
}

/**
 * 原子更新助手的单个预设条目启用状态（单次变换同时更新 `modeInjections` 条目启用态与 `assistant.modeInjectionIds`）。
 */
internal fun togglePresetEntryForAssistant(
    current: Settings,
    assistantId: Uuid,
    entryId: Uuid,
    enable: Boolean,
): Settings {
    val target = current.modeInjections.firstOrNull { it.id == entryId } ?: return current
    val updatedModeInjections = if (enable && !target.enabled) {
        current.modeInjections.map { e ->
            if (e.id == entryId) e.copy(enabled = true) else e
        }
    } else {
        current.modeInjections
    }
    val updatedAssistants = current.assistants.map { a ->
        if (a.id == assistantId) {
            val newIds = if (enable) a.modeInjectionIds + entryId else a.modeInjectionIds - entryId
            a.copy(modeInjectionIds = newIds)
        } else {
            a
        }
    }
    return current.copy(
        modeInjections = updatedModeInjections,
        assistants = updatedAssistants,
    )
}

@Composable
internal fun PresetManagementSection(
    /** 预设仓：`settings.modeInjections`（**不是** promptInjections） */
    modeInjections: List<PromptInjection.ModeInjection>,
    /** 该条目是否处于「开」态。全局页 = `enabled`；助手页 = `id in assistant.modeInjectionIds` */
    entryChecked: (PromptInjection.ModeInjection) -> Boolean = { it.enabled },
    onToggleEntry: ((PromptInjection.ModeInjection, Boolean) -> Unit)? = null,
    /** 整组开关。默认 = 把整组 `enabled` 一起改 */
    onToggleGroup: ((List<PromptInjection.ModeInjection>, Boolean) -> Unit)? = null,
    /** 条目内容被编辑（单条编辑保存）。null = 不允许编辑（只读视图） */
    onUpdateEntries: ((List<PromptInjection.ModeInjection>) -> Unit)? = null,
    /** 底部「前往提示词管理」按钮；null = 不显示（全局页自己就是提示词管理） */
    onNavigateToPrompts: (() -> Unit)? = null,
    /** 顶部「导入预设」；null = 不显示（助手页只做开关） */
    onImport: (() -> Unit)? = null,
    /** 「新建预设」；null = 不显示 */
    onCreatePreset: (() -> Unit)? = null,
    /** 「编辑预设」保存回调（整组批量改写后的完整列表） */
    onApplyPresetEdit: ((List<PromptInjection.ModeInjection>) -> Unit)? = null,
    /** 预设重命名或整组删除回调（oldName, newNameOrNullWhenDeleted, updatedList） */
    onRenameOrDeletePresetGroup: ((String, String?, List<PromptInjection.ModeInjection>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val grouped: Map<String, List<PromptInjection.ModeInjection>> = remember(modeInjections) {
        modeInjections.filter { !it.presetGroup.isNullOrBlank() }.groupBy { it.presetGroup!! }
    }
    val ungrouped = remember(modeInjections) {
        modeInjections.filter { it.presetGroup.isNullOrBlank() }
    }
    // 展开状态：预设名 -> 是否展开（"" 代表「未分组条目」这一行）
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var editingEntry by remember { mutableStateOf<PromptInjection.ModeInjection?>(null) }
    var editingPreset by remember { mutableStateOf<String?>(null) }

    fun toggleExpand(key: String) {
        expanded = if (expanded.contains(key)) expanded - key else expanded + key
    }

    fun applyEntry(edited: PromptInjection.ModeInjection) {
        val cb = onUpdateEntries ?: return
        cb(modeInjections.map { if (it.id == edited.id) edited else it })
    }

    fun deleteEntry(target: PromptInjection.ModeInjection) {
        val cb = onUpdateEntries ?: return
        cb(modeInjections.filterNot { it.id == target.id })
    }

    if (grouped.isEmpty() && ungrouped.isEmpty()) {
        EmptyPresetHint(hasImport = onImport != null, onImport = onImport)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            grouped.forEach { (name, entries) ->
                item(key = "preset:$name") {
                    PresetRow(
                        title = name,
                        entries = entries,
                        expanded = expanded.contains(name),
                        checked = entries.isNotEmpty() && entries.all { entryChecked(it) },
                        partial = entries.any { entryChecked(it) } && !entries.all { entryChecked(it) },
                        onToggle = { c ->
                            if (onToggleGroup != null) {
                                // 由 onToggleGroup 单次原子完成组内全关条目启用联动与 modeInjectionIds 绑定，避免双回调竞态
                                onToggleGroup.invoke(entries, c)
                            } else {
                                onUpdateEntries?.invoke(
                                    modeInjections.map { e ->
                                        if (e.presetGroup == name) e.copy(enabled = c) else e
                                    }
                                )
                            }
                        },
                        onExpand = { toggleExpand(name) },
                        onEdit = if (onApplyPresetEdit != null) { { editingPreset = name } } else null,
                    )
                }
                if (expanded.contains(name)) {
                    items(entries, key = { "preset:$name:${it.id}" }) { entry ->
                        EntryRow(
                            entry = entry,
                            checked = entryChecked(entry),
                            indented = true,
                            onToggle = { c ->
                                if (onToggleEntry != null) {
                                    // 由 onToggleEntry 单次原子完成条目 enabled 联动与 modeInjectionIds 绑定，避免双回调竞态
                                    onToggleEntry.invoke(entry, c)
                                } else {
                                    onUpdateEntries?.invoke(
                                        modeInjections.map { e ->
                                            if (e.id == entry.id) e.copy(enabled = c) else e
                                        }
                                    )
                                }
                            },
                            onEdit = if (onUpdateEntries != null) { { editingEntry = entry } } else null,
                        )
                    }
                }
            }

            if (ungrouped.isNotEmpty()) {
                val key = UNGROUPED_KEY
                item(key = "preset:$key") {
                    PresetRow(
                        title = "未分组条目",
                        entries = ungrouped,
                        expanded = expanded.contains(key),
                        checked = ungrouped.all { entryChecked(it) },
                        partial = ungrouped.any { entryChecked(it) } && !ungrouped.all { entryChecked(it) },
                        subtitleSuffix = "（历史手写注入 / 未带预设分组的导入）",
                        onToggle = { c ->
                            onToggleGroup?.invoke(ungrouped, c)
                                ?: onUpdateEntries?.invoke(
                                    modeInjections.map { e ->
                                        if (e.presetGroup.isNullOrBlank()) e.copy(enabled = c) else e
                                    }
                                )
                        },
                        onExpand = { toggleExpand(key) },
                        onEdit = null,
                    )
                }
                if (expanded.contains(key)) {
                    items(ungrouped, key = { "preset:$key:${it.id}" }) { entry ->
                        EntryRow(
                            entry = entry,
                            checked = entryChecked(entry),
                            indented = true,
                            onToggle = { c ->
                                onToggleEntry?.invoke(entry, c)
                                    ?: onUpdateEntries?.invoke(
                                        modeInjections.map { e ->
                                            if (e.id == entry.id) e.copy(enabled = c) else e
                                        }
                                    )
                            },
                            onEdit = if (onUpdateEntries != null) { { editingEntry = entry } } else null,
                        )
                    }
                }
            }

            if (onImport != null || onCreatePreset != null) {
                item(key = "tail") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onImport != null) {
                            TextButton(onClick = onImport) {
                                Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("导入预设")
                            }
                        }
                        if (onCreatePreset != null) {
                            TextButton(onClick = onCreatePreset) {
                                Icon(HugeIcons.Add01, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("新建预设")
                            }
                        }
                    }
                }
            }

            if (onNavigateToPrompts != null) {
                item(key = "goto") {
                    TextButton(onClick = onNavigateToPrompts, modifier = Modifier.fillMaxWidth()) {
                        Text("前往提示词管理")
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ---- 单条「编辑条目」
    editingEntry?.let { entry ->
        ModeInjectionEditSheet(
            injection = entry,
            onDismiss = { editingEntry = null },
            // ⚠️ ModeInjectionEditSheet 的签名是 onConfirm: () -> Unit（内部靠 onEdit 把每次改动
            // 同步回上层状态），所以这里必须读**最新的** editingEntry，不能用组合期捕获的 entry。
            onConfirm = {
                editingEntry?.let { latest -> applyEntry(latest) }
                editingEntry = null
            },
            onEdit = { editingEntry = it },
        )
    }

    // ---- 预设级「编辑预设」（世界书 GroupSettingsDialog 同款批量范式）
    editingPreset?.let { name ->
        val entries = grouped[name].orEmpty()
        PresetGroupEditDialog(
            presetName = name,
            entries = entries,
            onDismiss = { editingPreset = null },
            onConfirm = { newName, updated ->
                val renamed = modeInjections.map { e ->
                    when {
                        e.presetGroup != name -> e
                        updated.updated.containsKey(e.id) ->
                            updated.updated.getValue(e.id).copy(presetGroup = newName)
                        else -> e.copy(presetGroup = newName)
                    }
                }.filterNot { it.id in updated.deletedIds }
                val allDeleted = entries.isNotEmpty() && entries.all { it.id in updated.deletedIds }
                val targetGroupName = if (allDeleted) null else newName.ifBlank { name }
                if (onRenameOrDeletePresetGroup != null) {
                    onRenameOrDeletePresetGroup(name, targetGroupName, renamed)
                } else {
                    onApplyPresetEdit?.invoke(renamed)
                }
                editingPreset = null
            },
        )
    }
}

private const val UNGROUPED_KEY = "\u0000ungrouped"

private data class PresetEditResult(
    val updated: Map<Uuid, PromptInjection.ModeInjection> = emptyMap(),
    val deletedIds: Set<Uuid> = emptySet(),
)

@Composable
private fun PresetRow(
    title: String,
    entries: List<PromptInjection.ModeInjection>,
    expanded: Boolean,
    checked: Boolean,
    partial: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
    onEdit: (() -> Unit)?,
    subtitleSuffix: String = "",
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "presetChevron")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onEdit != null) { onEdit?.invoke() }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entries.size} 条注入" +
                        if (partial) " · 部分开启" else "" +
                        subtitleSuffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle)
            IconButton(onClick = onExpand) {
                Icon(
                    HugeIcons.ArrowDown01,
                    contentDescription = if (expanded) "收起条目" else "展开条目",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: PromptInjection.ModeInjection,
    checked: Boolean,
    indented: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.name.ifBlank { "未命名" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(entry.position.name)
                    append(" · 深度 ").append(entry.injectDepth)
                    append(" · ").append(entry.role.name)
                    if (entry.content.isNotBlank()) append(" · ").append(entry.content.length).append(" 字符")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .then(if (indented) Modifier.padding(start = 20.dp) else Modifier)
            .clickable(enabled = onEdit != null) { onEdit?.invoke() },
    )
}

@Composable
private fun EmptyPresetHint(hasImport: Boolean, onImport: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "暂无预设。导入酒馆预设 JSON 后，一个预设会显示成这里的一行。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (hasImport && onImport != null) {
            TextButton(onClick = onImport) {
                Icon(HugeIcons.FileImport, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("导入预设")
            }
        }
    }
}

/** 预设级「编辑预设」对话框：改名 + 整组批量改写（位置/深度/角色/启用），确认时应用到该组全部条目。 */
@Composable
private fun PresetGroupEditDialog(
    presetName: String,
    entries: List<PromptInjection.ModeInjection>,
    onDismiss: () -> Unit,
    onConfirm: (String, PresetEditResult) -> Unit,
) {
    var name by remember(presetName) { mutableStateOf(presetName) }
    var batchEnabledOverride by remember(presetName) { mutableStateOf<Boolean?>(null) }
    var requestDeleteAll by remember(presetName) { mutableStateOf(false) }
    val enabledCount = when (val ov = batchEnabledOverride) {
        true -> entries.size
        false -> 0
        null -> entries.count { it.enabled }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑预设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    text = "该预设共 ${entries.size} 条注入（当前启用 $enabledCount 条）。可在此一键全部启用/全部禁用或删除整组预设。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { batchEnabledOverride = true }) {
                        Text("全部启用")
                    }
                    TextButton(onClick = { batchEnabledOverride = false }) {
                        Text("全部禁用")
                    }
                    TextButton(
                        onClick = {
                            requestDeleteAll = true
                            onConfirm(
                                presetName,
                                PresetEditResult(deletedIds = entries.map { it.id }.toSet()),
                            )
                        }
                    ) {
                        Text("删除预设", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedMap = batchEnabledOverride?.let { target ->
                        entries.associate { it.id to it.copy(enabled = target) }
                    } ?: emptyMap()
                    onConfirm(name.ifBlank { presetName }, PresetEditResult(updated = updatedMap))
                },
                enabled = entries.isNotEmpty() && !requestDeleteAll,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 全局「预设管理」tab（Page 0）。
 *
 * 与助手页共用 [PresetManagementSection]，差别只有：
 *   · 有「导入预设」/「新建预设」；
 *   · 开关语义 = 条目的 `enabled`（助手页那条才是「本助手是否启用该预设」）；
 *   · 没有「前往提示词管理」（本页自己就是）。
 */
@Composable
internal fun PresetManagementTab(
    modeInjections: List<PromptInjection.ModeInjection>,
    onUpdate: (List<PromptInjection.ModeInjection>) -> Unit,
    onPresetScripts: (String, List<kotlinx.serialization.json.JsonElement>) -> Unit = { _, _ -> },
    onStoreRawPreset: (String, String) -> Unit = { _, _ -> },
    onImportBundle: ((me.rerere.rikkahub.data.export.PresetImportBundle) -> Unit)? = null,
    onRenameOrDeletePresetGroup: ((String, String?, List<PromptInjection.ModeInjection>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.appendLog(
                "info",
                "[preset] import start: uri=$uri",
            )
            val bundleResult = withContext(Dispatchers.IO) {
                ModeInjectionSerializer.importPresetBundle(context, uri)
            }
            bundleResult.onSuccess { rawBundle ->
                if (rawBundle.injections.isEmpty()) {
                    me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.appendLog(
                        "warn",
                        "[preset] import empty injections: uri=$uri group='${rawBundle.presetGroup}'",
                    )
                    return@onSuccess
                }
                // [P0-3 修复] 严格忠实保留来源预设及 prompt_order 定义的 enabled 状态，
                // 绝不因「当前没有已启用条目」而擅自把所有条目改写为 enabled = true。
                val bundle = rawBundle
                val enabledCount = bundle.injections.count { it.enabled }
                me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.appendLog(
                    "info",
                    "[preset] parsed: group='${bundle.presetGroup}' prompts=${bundle.injections.size} " +
                        "(enabled=$enabledCount) scripts=${bundle.presetScripts.size}(${bundle.scriptsFieldState}) " +
                        "regex=${bundle.regexScripts.size}(${bundle.regexFieldState}) rawLen=${bundle.rawPresetJson?.length ?: 0} " +
                        "unsupported=${bundle.unsupportedExtensions} warnings=${bundle.warnings}",
                )
                if (bundle.unsupportedExtensions.isNotEmpty() || bundle.warnings.isNotEmpty()) {
                    me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.appendLog(
                        "warn",
                        "[preset] 预设 '${bundle.presetGroup}' 诊断告警: " +
                            (bundle.unsupportedExtensions + bundle.warnings).joinToString("; "),
                    )
                }
                if (onImportBundle != null) {
                    onImportBundle(bundle)
                } else {
                    val mergedInjections = modeInjections.filterNot { it.presetGroup == bundle.presetGroup } + bundle.injections
                    onUpdate(mergedInjections)
                    bundle.rawPresetJson?.let { json -> onStoreRawPreset(bundle.presetGroup, json) }
                    if (bundle.scriptsFieldState != me.rerere.rikkahub.data.export.PresetSubfieldState.CORRUPTED) {
                        onPresetScripts(bundle.presetGroup, bundle.presetScripts)
                    }
                }
                val parts = mutableListOf("${bundle.injections.size} 条注入（启用 $enabledCount 条）")
                if (bundle.presetScripts.isNotEmpty()) parts += "${bundle.presetScripts.size} 个预设脚本"
                if (bundle.regexScripts.isNotEmpty()) parts += "${bundle.regexScripts.size} 条内嵌正则"
                val allDisabledNote = if (enabledCount == 0) "；当前条目均为关闭状态，可点击右侧开关一键启用" else ""
                val baseMsg = "导入成功：预设「${bundle.presetGroup}」（${parts.joinToString("，")}）$allDisabledNote"
                val allNotices = bundle.unsupportedExtensions.map { "不支持的扩展私有配置 $it" } + bundle.warnings
                if (allNotices.isNotEmpty()) {
                    toaster.show(
                        "$baseMsg；注意：${allNotices.joinToString("；")}",
                        type = com.dokar.sonner.ToastType.Warning,
                    )
                } else {
                    toaster.show(baseMsg, type = com.dokar.sonner.ToastType.Success)
                }
            }.onFailure { error ->
                me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.appendLog(
                    "error",
                    "[preset] import failed: uri=$uri error=${error.message}",
                )
                toaster.show("导入失败：${error.message}", type = com.dokar.sonner.ToastType.Error)
            }
        }
    }

    var newPresetDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        PresetManagementSection(
            modeInjections = modeInjections,
            entryChecked = { it.enabled },
            onUpdateEntries = onUpdate,
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onCreatePreset = { newPresetDialog = true },
            onApplyPresetEdit = { list -> onUpdate(list) },
            onRenameOrDeletePresetGroup = onRenameOrDeletePresetGroup,
            contentPadding = PaddingValues(vertical = 8.dp),
        )
    }

    if (newPresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPresetDialog = false },
            title = { Text("新建预设") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("预设名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        text = "会创建一个只含 1 条空注入的预设；之后可在该预设里继续添加条目。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val label = name.ifBlank { "新预设" }
                        onUpdate(
                            modeInjections + PromptInjection.ModeInjection(
                                name = "$label - 条目1",
                                content = "",
                                presetGroup = label,
                                enabled = true,
                            )
                        )
                        newPresetDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { newPresetDialog = false }) { Text("取消") } },
        )
    }
}


/**
 * [第6轮 D3] 把预设的**原始 root JSON** 存进 `Settings.tavernPresets`（形如 `{"预设名": "<raw json 字符串>"}`）。
 *
 * 与 `mergePresetScripts` 同款「无损透传」范式：本 App 现在不解析这 44 个 root 字段
 *（temperature / new_chat_prompt / scenario_format / …）与 `extensions.SPreset`，
 * 但必须原样留着 —— 以后想补映射随时能取，且不会因为一次导入就永久丢字段。
 * 同名预设重导入 = 覆盖（符合「再导入一次就是更新那个预设」的直觉）。
 */
internal fun mergeRawPreset(existing: String?, label: String, raw: String): String = runCatching {
    val obj = (Json.parseToJsonElement(existing ?: "{}") as? JsonObject) ?: JsonObject(emptyMap())
    JsonObject(obj + (label to JsonPrimitive(raw))).toString()
}.getOrElse { existing ?: "{}" }

/** 预设重命名或删除时，同步迁移或清理 `Settings.tavernPresets` 中的原始 JSON。 */
internal fun renameOrRemoveRawPreset(existing: String?, oldLabel: String, newLabel: String?): String = runCatching {
    if (oldLabel.isBlank()) return@runCatching existing ?: "{}"
    val obj = (Json.parseToJsonElement(existing ?: "{}") as? JsonObject) ?: return@runCatching existing ?: "{}"
    if (!obj.containsKey(oldLabel)) return@runCatching existing ?: "{}"
    val mutable = obj.toMutableMap()
    val rawVal = mutable.remove(oldLabel)
    if (!newLabel.isNullOrBlank() && rawVal != null) {
        mutable[newLabel] = rawVal
    }
    JsonObject(mutable).toString()
}.getOrElse { existing ?: "{}" }


/**
 * 预设组重命名（newName != null）或整组删除（newName == null）时的原子状态迁移函数：
 * 同步更新 `modeInjections`、`tavernPresets`、`tavernPresetScripts`、`regexScripts`（`sourcePresetGroup`）及助手的 `modeInjectionIds`。
 */
internal fun applyPresetGroupLifecycleChange(
    current: Settings,
    oldName: String,
    newName: String?,
    updatedModeInjections: List<PromptInjection.ModeInjection>,
): Settings {
    val effectiveNewName = newName?.trim()?.takeIf { it.isNotEmpty() }
    val nextRawPresets = renameOrRemoveRawPreset(current.tavernPresets, oldName, effectiveNewName)
    val nextPresetScripts = me.rerere.rikkahub.data.st.runtime.renameOrRemovePresetScriptsGroup(
        current.tavernPresetScripts,
        oldName,
        effectiveNewName,
    )
    val nextRegexScripts = when {
        effectiveNewName == null -> current.regexScripts.filterNot { it.sourcePresetGroup == oldName }
        effectiveNewName != oldName -> current.regexScripts.map { rx ->
            if (rx.sourcePresetGroup == oldName) rx.copy(sourcePresetGroup = effectiveNewName) else rx
        }
        else -> current.regexScripts
    }
    val survivingIds = updatedModeInjections.map { it.id }.toSet()
    val oldGroupIds = current.modeInjections.filter { it.presetGroup == oldName }.map { it.id }.toSet()
    val removedIds = oldGroupIds - survivingIds
    val nextAssistants = if (removedIds.isNotEmpty()) {
        current.assistants.map { a ->
            if (a.modeInjectionIds.any { it in removedIds }) {
                a.copy(modeInjectionIds = a.modeInjectionIds - removedIds)
            } else a
        }
    } else current.assistants

    return current.copy(
        modeInjections = updatedModeInjections,
        tavernPresets = nextRawPresets,
        tavernPresetScripts = nextPresetScripts,
        regexScripts = nextRegexScripts,
        assistants = nextAssistants,
    )
}

/**
 * 预设导入/重导入时的原子合并函数：
 * 除合并 `modeInjections`、`tavernPresets`、`tavernPresetScripts`、`regexScripts` 外，
 * 若某个助手已绑定了同名预设的旧条目 ID，重导入后自动同步其 `modeInjectionIds` 以保持绑定不丢失。
 */
internal fun applyPresetBundleToSettings(
    current: Settings,
    bundle: me.rerere.rikkahub.data.export.PresetImportBundle,
): Pair<Settings, Int> {
    val oldGroupEntries = current.modeInjections.filter { it.presetGroup == bundle.presetGroup }
    val oldGroupIds = oldGroupEntries.map { it.id }.toSet()
    val oldIdByName = oldGroupEntries.associate { it.name to it.id }
    val reconciledInjections = bundle.injections.map { inj ->
        val existingId = oldIdByName[inj.name]
        if (existingId != null) inj.copy(id = existingId) else inj
    }
    val newGroupIds = reconciledInjections.map { it.id }.toSet()
    val nextInjections = current.modeInjections.filterNot { it.presetGroup == bundle.presetGroup } + reconciledInjections
    val nextRawPresets = if (bundle.rawPresetJson != null) {
        mergeRawPreset(current.tavernPresets, bundle.presetGroup, bundle.rawPresetJson)
    } else {
        current.tavernPresets
    }
    val nextPresetScripts = if (bundle.scriptsFieldState != me.rerere.rikkahub.data.export.PresetSubfieldState.CORRUPTED) {
        me.rerere.rikkahub.data.st.runtime.mergePresetScripts(
            current.tavernPresetScripts,
            bundle.presetGroup,
            bundle.presetScripts,
            removeWhenEmpty = true,
        )
    } else {
        current.tavernPresetScripts
    }
    val (nextRegexScripts, addedRegex) = ModeInjectionSerializer.mergePresetRegexScripts(
        existing = current.regexScripts,
        presetGroup = bundle.presetGroup,
        imported = bundle.regexScripts,
        fieldState = bundle.regexFieldState,
    )
    val nextAssistants = if (oldGroupIds.isNotEmpty()) {
        current.assistants.map { assistant ->
            if (assistant.modeInjectionIds.any { it in oldGroupIds }) {
                assistant.copy(modeInjectionIds = (assistant.modeInjectionIds - oldGroupIds) + newGroupIds)
            } else {
                assistant
            }
        }
    } else {
        current.assistants
    }
    return current.copy(
        modeInjections = nextInjections,
        tavernPresets = nextRawPresets,
        tavernPresetScripts = nextPresetScripts,
        regexScripts = nextRegexScripts,
        assistants = nextAssistants,
    ) to addedRegex
}
