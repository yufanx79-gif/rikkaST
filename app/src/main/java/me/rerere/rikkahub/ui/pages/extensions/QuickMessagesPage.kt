package me.rerere.rikkahub.ui.pages.extensions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Share03
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuickMessagesPage(vm: QuickMessagesVM = koinViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuickMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<QuickMessage?>(null) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }.orEmpty()
            val addedCount = vm.importQuickMessagesFromJson(text)
            if (addedCount > 0) {
                toaster.show("成功导入 $addedCount 条快捷消息（已自动绑定到助手）")
            } else {
                toaster.show("导入失败：未识别到有效的快捷消息或 ST Quick Reply JSON")
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val jsonText = vm.exportQuickMessagesToJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    }
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                toaster.show("已导出 ${settings.quickMessages.size} 条快捷消息 JSON")
            } else {
                toaster.show("导出快捷消息失败")
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(HugeIcons.FileImport, contentDescription = "导入快捷消息 JSON")
                    }
                    if (settings.quickMessages.isNotEmpty()) {
                        IconButton(onClick = { exportJsonLauncher.launch("quick_replies_export.json") }) {
                            Icon(HugeIcons.Share03, contentDescription = "导出快捷消息 JSON")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.quickMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Zap,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }) {
                            Icon(HugeIcons.FileImport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "导入快捷消息 JSON（兼容酒馆 Quick Reply v2）",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }

            items(settings.quickMessages, key = { it.id }) { quickMessage ->
                QuickMessageCard(
                    quickMessage = quickMessage,
                    onEdit = { editTarget = quickMessage },
                    onDelete = { deleteTarget = quickMessage },
                )
            }
        }
    }

    if (showAddDialog) {
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_add_title),
            initialQuickMessage = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { updated ->
                vm.addQuickMessageItem(updated)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { quickMessage ->
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_edit_title),
            initialQuickMessage = quickMessage,
            onDismiss = { editTarget = null },
            onConfirm = { updated ->
                vm.updateQuickMessage(updated)
                editTarget = null
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.quick_messages_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteQuickMessage(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.quick_messages_page_delete_message, deleteTarget?.title ?: ""))
    }
}

@Composable
private fun QuickMessageCard(
    quickMessage: QuickMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.Zap,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quickMessage.title.ifBlank { stringResource(R.string.quick_messages_page_untitled) },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (quickMessage.setName.isNotBlank() && quickMessage.setName != "Default") {
                        Text(
                            text = "[${quickMessage.setName}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (quickMessage.autoExecute) {
                        Text(
                            text = stringResource(R.string.quick_messages_page_auto_execute_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Text(
                    text = quickMessage.content.ifBlank { stringResource(R.string.quick_messages_page_empty_content) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        onClick = onDelete,
                    ),
                )
            )
        }
    }
}

@Composable
private fun EditQuickMessageDialog(
    title: String,
    initialQuickMessage: QuickMessage?,
    onDismiss: () -> Unit,
    onConfirm: (QuickMessage) -> Unit,
) {
    var quickMessageTitle by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.title ?: "")
    }
    var quickMessageContent by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.content ?: "")
    }
    var quickMessageAutoExecute by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.autoExecute ?: false)
    }
    var setName by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.setName ?: "Default")
    }
    var automationId by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.automationId ?: "")
    }
    var executeOnStartup by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.executeOnStartup ?: false)
    }
    var executeOnChatChange by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.executeOnChatChange ?: false)
    }
    var executeOnUser by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.executeOnUser ?: false)
    }
    var executeOnAi by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.executeOnAi ?: false)
    }
    var executeOnGroup by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.executeOnGroupMemberDraft ?: false)
    }
    var hidden by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.hidden ?: false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = quickMessageTitle,
                    onValueChange = { quickMessageTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quickMessageContent,
                    onValueChange = { quickMessageContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_quick_message_content)) },
                    minLines = 3,
                    maxLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = setName,
                        onValueChange = { setName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("QR 集合名") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = automationId,
                        onValueChange = { automationId = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Automation ID") },
                        singleLine = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.quick_messages_page_auto_execute),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_auto_execute_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = quickMessageAutoExecute,
                        onCheckedChange = { quickMessageAutoExecute = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("切会话触发", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = executeOnChatChange, onCheckedChange = { executeOnChatChange = it })
                    Text("AI回复后触发", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = executeOnAi, onCheckedChange = { executeOnAi = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启动时触发", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = executeOnStartup, onCheckedChange = { executeOnStartup = it })
                    Text("发送时触发", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = executeOnUser, onCheckedChange = { executeOnUser = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("群聊发言前", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = executeOnGroup, onCheckedChange = { executeOnGroup = it })
                    Text("隐藏按钮", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = initialQuickMessage ?: QuickMessage()
                    onConfirm(
                        base.copy(
                            title = quickMessageTitle.trim(),
                            content = quickMessageContent.trim(),
                            autoExecute = quickMessageAutoExecute,
                            setName = setName.trim().ifEmpty { "Default" },
                            automationId = automationId.trim(),
                            executeOnStartup = executeOnStartup,
                            executeOnChatChange = executeOnChatChange,
                            executeOnUser = executeOnUser,
                            executeOnAi = executeOnAi,
                            executeOnGroupMemberDraft = executeOnGroup,
                            hidden = hidden,
                        )
                    )
                },
                enabled = quickMessageTitle.isNotBlank() && quickMessageContent.isNotBlank(),
            ) {
                Text(stringResource(R.string.assistant_page_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
