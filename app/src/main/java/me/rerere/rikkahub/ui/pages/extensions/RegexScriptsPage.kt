package me.rerere.rikkahub.ui.pages.extensions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Share03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import me.rerere.rikkahub.data.st.regex.SubstituteRegex
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** 正则脚本导入/导出用 Json（忽略未知键，兼容各版本酒馆导出） */
private val RegexJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

@Composable
fun RegexScriptsPage() {
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editing by remember { mutableStateOf<RegexScript?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val updateList: (List<RegexScript>) -> Unit = { newList ->
        scope.launch { settingsStore.update(settings.copy(regexScripts = newList)) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.st_regex_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
                actions = {
                    IconButton(onClick = {
                        showImport = true
                    }) { Icon(HugeIcons.FileImport, null) }
                    IconButton(onClick = { showExport = true }) { Icon(HugeIcons.Share03, null) }
                    IconButton(onClick = {
                        editing = RegexScript(
                            id = Uuid.random().toString(),
                            scriptName = "新脚本",
                            disabled = true,
                        )
                    }) { Icon(HugeIcons.Add01, null) }
                },
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.regexScripts.isEmpty()) {
                item {
                    Text(
                        "还没有正则脚本。\n点“+”新建，或用右上角导入按钮粘贴酒馆导出的脚本。",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(settings.regexScripts, key = { it.id }) { script ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = script },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                script.scriptName.ifBlank { "(unnamed)" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                scriptSummary(script),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = !script.disabled,
                            onCheckedChange = { enabled ->
                                updateList(
                                    settings.regexScripts.map {
                                        if (it.id == script.id) it.copy(disabled = !enabled) else it
                                    }
                                )
                            },
                        )
                        IconButton(onClick = {
                            updateList(settings.regexScripts.filterNot { it.id == script.id })
                        }) { Icon(HugeIcons.Delete01, null) }
                    }
                }
            }
        }
    }

    editing?.let { script ->
        RegexScriptEditorDialog(
            script = script,
            onDismiss = { editing = null },
            onSave = { saved ->
                val exists = settings.regexScripts.any { it.id == saved.id }
                updateList(
                    if (exists) {
                        settings.regexScripts.map { if (it.id == saved.id) saved else it }
                    } else {
                        settings.regexScripts + saved
                    }
                )
                editing = null
            },
        )
    }

    if (showImport) {
        ImportScriptsDialog(
            onDismiss = { showImport = false },
            onImport = { scripts ->
                val existingIds = settings.regexScripts.map { it.id }.toMutableSet()
                val merged = scripts.map { s ->
                    val id = s.id.ifBlank { Uuid.random().toString() }.let {
                        if (it in existingIds) Uuid.random().toString() else it
                    }
                    existingIds += id
                    s.copy(id = id)
                }
                updateList(settings.regexScripts + merged)
                showImport = false
            },
        )
    }

    if (showExport) {
        val json = remember(showExport, settings.regexScripts) {
            RegexJson.encodeToString(ListSerializer(RegexScript.serializer()), settings.regexScripts)
        }
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("导出正则脚本") },
            text = {
                SelectionContainer {
                    Text(
                        json,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showExport = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun ImportScriptsDialog(
    onDismiss: () -> Unit,
    onImport: (List<RegexScript>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(picked)?.bufferedReader()?.use { reader ->
                            reader.readText()
                        } ?: error("无法读取所选文件")
                    }
                }.onSuccess { content ->
                    text = content.trim()
                    error = null
                }.onFailure { e ->
                    error = "文件读取失败：${e.message ?: "未知错误"}"
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入正则脚本") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "选择酒馆（SillyTavern）导出的正则脚本 JSON 文件，或直接粘贴 JSON 文本。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/*", "application/json"))
                        },
                    ) {
                        Icon(HugeIcons.FileImport, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("选择文件")
                    }
                    if (text.isNotBlank()) {
                        Text(
                            "已载入 ${text.length} 字符",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 120.dp),
                    minLines = 5,
                    maxLines = 12,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching {
                    RegexJson.decodeFromString(ListSerializer(RegexScript.serializer()), text.trim())
                }.getOrNull()
                if (parsed == null) {
                    error = "JSON 无效：需要酒馆脚本数组。"
                    return@TextButton
                }
                onImport(parsed)
            }) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun RegexScriptEditorDialog(
    script: RegexScript,
    onDismiss: () -> Unit,
    onSave: (RegexScript) -> Unit,
) {
    var draft by remember(script.id) { mutableStateOf(script) }
    var testInput by remember(script.id) { mutableStateOf("") }
    var testOutput by remember(script.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.scriptName.ifBlank { "正则脚本" }) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.scriptName,
                    onValueChange = { draft = draft.copy(scriptName = it) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.findRegex,
                    onValueChange = { draft = draft.copy(findRegex = it) },
                    label = { Text("查找正则（/pattern/flags）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                )
                OutlinedTextField(
                    value = draft.replaceString,
                    onValueChange = { draft = draft.copy(replaceString = it) },
                    label = { Text("替换为（$1、$<name>、{{match}}）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                )
                OutlinedTextField(
                    value = draft.trimStrings.joinToString("\n"),
                    onValueChange = { v ->
                        draft = draft.copy(
                            trimStrings = v.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    },
                    label = { Text("剔除字符串（每行一条）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 4,
                )
                Text("应用到", style = MaterialTheme.typography.labelLarge)
                SwitchRow("用户输入", RegexPlacement.USER_INPUT in draft.placement) { on ->
                    draft = draft.copy(placement = togglePlacement(draft.placement, RegexPlacement.USER_INPUT, on))
                }
                SwitchRow("AI 输出", RegexPlacement.AI_OUTPUT in draft.placement) { on ->
                    draft = draft.copy(placement = togglePlacement(draft.placement, RegexPlacement.AI_OUTPUT, on))
                }
                SwitchRow("斜杠命令", RegexPlacement.SLASH_COMMAND in draft.placement) { on ->
                    draft = draft.copy(placement = togglePlacement(draft.placement, RegexPlacement.SLASH_COMMAND, on))
                }
                SwitchRow("世界书", RegexPlacement.WORLD_INFO in draft.placement) { on ->
                    draft = draft.copy(placement = togglePlacement(draft.placement, RegexPlacement.WORLD_INFO, on))
                }
                SwitchRow("思维链", RegexPlacement.REASONING in draft.placement) { on ->
                    draft = draft.copy(placement = togglePlacement(draft.placement, RegexPlacement.REASONING, on))
                }
                Text("选项", style = MaterialTheme.typography.labelLarge)
                SwitchRow("启用", !draft.disabled) { draft = draft.copy(disabled = !it) }
                SwitchRow("仅显示（Markdown）", draft.markdownOnly) { draft = draft.copy(markdownOnly = it) }
                SwitchRow("仅提示词", draft.promptOnly) { draft = draft.copy(promptOnly = it) }
                SwitchRow("编辑时运行", draft.runOnEdit) { draft = draft.copy(runOnEdit = it) }
                Text("查找串内宏替换", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.substituteRegex == SubstituteRegex.NONE,
                        onClick = { draft = draft.copy(substituteRegex = SubstituteRegex.NONE) },
                        label = { Text("无") },
                    )
                    FilterChip(
                        selected = draft.substituteRegex == SubstituteRegex.RAW,
                        onClick = { draft = draft.copy(substituteRegex = SubstituteRegex.RAW) },
                        label = { Text("宏") },
                    )
                    FilterChip(
                        selected = draft.substituteRegex == SubstituteRegex.ESCAPED,
                        onClick = { draft = draft.copy(substituteRegex = SubstituteRegex.ESCAPED) },
                        label = { Text("转义") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.minDepth?.toString() ?: "",
                        onValueChange = { draft = draft.copy(minDepth = it.trim().toIntOrNull()) },
                        label = { Text("最小深度") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.maxDepth?.toString() ?: "",
                        onValueChange = { draft = draft.copy(maxDepth = it.trim().toIntOrNull()) },
                        label = { Text("最大深度") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Text("测试", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text("示例文本") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Button(onClick = {
                    testOutput = RegexScriptEngine.runRegexScript(draft.copy(disabled = false), testInput)
                }) { Text("运行测试") }
                testOutput?.let { out ->
                    Text("结果", style = MaterialTheme.typography.labelMedium)
                    Text(out, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun togglePlacement(current: List<Int>, value: Int, on: Boolean): List<Int> =
    if (on) (current + value).distinct() else current - value

internal fun scriptSummary(script: RegexScript): String {
    val placements = script.placement.mapNotNull { p ->
        when (p) {
            RegexPlacement.USER_INPUT -> "用户输入"
            RegexPlacement.AI_OUTPUT -> "AI 输出"
            RegexPlacement.SLASH_COMMAND -> "斜杠"
            RegexPlacement.WORLD_INFO -> "世界书"
            RegexPlacement.REASONING -> "思维链"
            RegexPlacement.MD_DISPLAY -> "MD 显示"
            else -> null
        }
    }.joinToString("/").ifBlank { "未设置" }
    val flags = buildList {
        if (script.markdownOnly) add("仅显示")
        if (script.promptOnly) add("仅提示词")
        if (script.runOnEdit) add("编辑时")
        if (script.substituteRegex != SubstituteRegex.NONE) add(
            if (script.substituteRegex == SubstituteRegex.RAW) "宏（原文）" else "宏（转义）"
        )
    }.joinToString(", ")
    return if (flags.isBlank()) placements else "$placements · $flags"
}