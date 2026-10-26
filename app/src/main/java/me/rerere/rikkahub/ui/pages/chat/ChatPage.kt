@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.blur
import me.rerere.rikkahub.ui.components.frosted.ChatTopFadeOverlay
import me.rerere.rikkahub.data.datastore.BlurStyle
import me.rerere.rikkahub.data.datastore.BlurStrength
import me.rerere.rikkahub.data.st.extensions.buildRuntimeExtensionsJson
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore
import me.rerere.rikkahub.data.st.runtime.buildCharacterJson
import me.rerere.rikkahub.data.st.runtime.buildLorebookJson
import me.rerere.rikkahub.data.st.runtime.buildTavernChatJson
import me.rerere.rikkahub.data.st.runtime.buildTavernScriptsJson
import me.rerere.rikkahub.data.st.runtime.buildWorldNamesJson
import me.rerere.rikkahub.data.st.script.StSlashExecutor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppType
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.TavernScriptButtonsRow
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.SlashVarOp
import me.rerere.rikkahub.ui.components.ai.applyMacroVarSlash
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.chat.TavernFloatingWindowOverlay
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.ai.ui.isEmptyInputMessage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(
    id: Uuid,
    text: String?,
    files: List<Uri>,
    nodeId: Uuid? = null,
    // [v245 BUGFIX H1] 本 Screen.Chat 是否位于导航栈顶（由 RouteActivity 按 backStack 判定）。
    // 默认 true：直接调用 ChatPage 的地方（预览 / 测试）行为不变。
    isOnTop: Boolean = true,
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val openConversation: (Uuid) -> Unit = { targetId ->
        scope.launch { navigateToChatPage(navController, chatId = targetId) }
    }

    // 酒馆运行时：注册当前会话上下文（变量 / MVU / 脚本宿主）
    val chatListState = rememberLazyListState()
    val tavernLatestConversation = rememberUpdatedState(conversation)
    val tavernLatestSettings = rememberUpdatedState(setting)
    // [v214 / P0-1] key 追加 assistantId 与 setting.init：会话/设置数据从 dummy 切换到真实值时必须重跑
    LaunchedEffect(conversation.id, conversation.assistantId, setting.init) {
        TavernRuntimeManager.ensureStarted(context)
        TavernRuntimeManager.activeAssistantSupplier = {
            tavernLatestSettings.value.getAssistantById(tavernLatestConversation.value.assistantId)
        }
        TavernRuntimeManager.setActiveAssistantId(tavernLatestConversation.value.assistantId.toString())
        TavernRuntimeManager.chatJsonSupplier = {
            val conv = tavernLatestConversation.value
            buildTavernChatJson(conv, tavernLatestSettings.value)
        }
        TavernRuntimeManager.chatUpdater = { jsonText -> vm.applyRuntimeChatUpdates(jsonText) }
        TavernRuntimeManager.tavernScriptsSupplier = {
            buildTavernScriptsJson(
                tavernLatestSettings.value.getAssistantById(tavernLatestConversation.value.assistantId),
                tavernLatestSettings.value,
            )
        }
        TavernRuntimeManager.tavernLorebookSupplier = { name ->
            buildLorebookJson(
                tavernLatestSettings.value.getAssistantById(tavernLatestConversation.value.assistantId),
                name,
                tavernLatestSettings.value,
            ) { requested, stored ->
                // [v216] 命中「双侧 trim 兜底」时留证：存量世界书名可能带尾随空格（千纱卡开关面板）
                TavernRuntimeManager.appendLog("warn", "[lorebook] trim-match requested='$requested' stored='$stored'")
            }
        }
        TavernRuntimeManager.tavernCharacterSupplier = {
            buildCharacterJson(
                tavernLatestSettings.value.getAssistantById(tavernLatestConversation.value.assistantId)
            )
        }
        // [v208] 世界书名列表（ST world_names / selected_world_info）
        TavernRuntimeManager.worldNamesSupplier = {
            buildWorldNamesJson(
                tavernLatestSettings.value,
                tavernLatestSettings.value.getAssistantById(tavernLatestConversation.value.assistantId)
            )
        }
        // 第三方扩展（third-party extensions）：装载列表 + 设置读写
        TavernRuntimeManager.thirdPartyExtensionsSupplier = {
            buildRuntimeExtensionsJson(
                listExtensions(context),
                tavernLatestSettings.value.tavernThirdPartyDisabled,
            )
        }
        TavernRuntimeManager.thirdPartySettingsSupplier = {
            tavernLatestSettings.value.tavernExtensionSettings
        }
        TavernRuntimeManager.thirdPartySettingsSaver = { json ->
            // [P0-3 修复] 必须走 patchSettings 基于 SettingsStore 最新权威值写回，
            // 否则当用户切去「预设管理」页导入预设触发 settings_updated -> JS saveSettingsDebounced 时，
            // 已离开前景的 ChatPage 持有的 tavernLatestSettings.value 是旧快照，会在 300ms 后把刚导入的预设覆盖回滚！
            vm.patchSettings { current -> current.copy(tavernExtensionSettings = json) }
        }
        // [v214] 世界书写回桥（JS RikkaBridge.updateLorebookEntries）
        TavernRuntimeManager.lorebookUpdater = { name, json ->
            vm.applyLorebookUpdate(name, json)
        }
        // 外链图片双通道：跟随设置实时切换 AUTO / 直连 / VPN。
        TavernRuntimeManager.resourceNetworkMode = setting.displaySetting.resourceNetworkMode
        // [v214 / Bug B 修复] 面板表单发送桥：带真实落库回执，只有用户消息写入成功才滚动。
        TavernRuntimeManager.userMessageSender = { text ->
            vm.handleMessageSend(listOf(UIMessagePart.Text(text)))
        }
        TavernRuntimeManager.userMessageSenderWithReceipt = { text, answer, onPersisted ->
            TavernRuntimeManager.recordSendDiagnostic(
                stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_1_SEND_CLICKED,
                conversationId = tavernLatestConversation.value.id.toString(),
                detail = "source=panel_sendUserMessage textLen=${text.length}",
            )
            val shouldScroll = !chatListState.canScrollForward
            val sent = vm.handleMessageSend(
                content = listOf(UIMessagePart.Text(text)),
                answer = answer,
                onPersisted = { persisted ->
                    onPersisted(persisted)
                    if (persisted && shouldScroll) {
                        val targetIdx = vm.conversation.value.messageNodes.lastIndex.coerceAtLeast(0)
                        TavernRuntimeManager.recordSendDiagnostic(
                            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_4_SCROLL_TRIGGERED,
                            conversationId = tavernLatestConversation.value.id.toString(),
                            detail = "source=panel_sendUserMessage targetIdx=$targetIdx firstVisible=${chatListState.firstVisibleItemIndex}",
                        )
                        scope.launch { chatListState.requestScrollToItem(targetIdx) }
                    }
                },
            )
            if (!sent) onPersisted(false)
            sent
        }
        TavernRuntimeManager.assistantMessageInserter = { text ->
            // [P0 修复] 空文本 / 会话未就绪时不能假装成功。
            if (text.isBlank()) {
                false
            } else {
                vm.handleInsertMessage(MessageRole.ASSISTANT, text)
                true
            }
        }
        TavernRuntimeManager.chatSaver = {
            // [P0 修复] 之前无条件返回 true。这里至少确认会话已加载且 id 一致，
            // 否则「保存成功」是假的（卡脚本会据此认为写盘完成）。
            val conv = vm.conversation.value
            if (!vm.chatListInitialized) {
                TavernRuntimeManager.appendLog("warn", "[panel-cmd] saveChat rejected: conversation not loaded yet")
                false
            } else {
                vm.saveConversationAsync()
                true
            }
        }
        // [v243 BUGFIX A2] JSR createChatMessages / deleteChatMessages 落点（同步更新，不触发生成）
        TavernRuntimeManager.chatInserter = { jsonText -> vm.insertRuntimeChatMessages(jsonText) }
        TavernRuntimeManager.chatDeleter = { idsJson -> vm.deleteRuntimeChatMessages(idsJson) }
        TavernRuntimeManager.generationStopper = {
            val hadActive = vm.conversationJob.value != null
            if (hadActive) vm.stopGeneration()
            hadActive
        }
        // [v243 BUGFIX A3] JSR 世界书条目写入与整书 CRUD / 绑定落点
        TavernRuntimeManager.lorebookUpserter = { name, json -> vm.applyLorebookUpsert(name, json) }
        TavernRuntimeManager.lorebookCreator = { name, entriesJson, replace ->
            vm.createOrReplaceLorebook(name, entriesJson, replace)
        }
        TavernRuntimeManager.lorebookDeleter = { name -> vm.deleteLorebookByName(name) }
        TavernRuntimeManager.lorebookRebinder = { scopeName, namesJson ->
            vm.rebindLorebooks(scopeName, namesJson)
        }
        // [P0-1 优化] 冷启动首帧若仍处于 Settings.dummy() (init=true) 且会话尚未从数据库加载完毕，
        // 仅完成上方 supplier 注册，跳过第一轮针对默认占位助手 (0950e2dc) 的虚假 setActiveConversation 广播，
        // 待 ~150ms 后真实设置/助手到位时再触发一次完整的会话绑定。
        // Quick Reply v2 会话打开/切换时自动触发钩子（executeOnStartup / executeOnChatChange）
        if (!setting.init) {
            val isFirstProcessStart = TavernRuntimeManager.markStartupQrExecuted()
            val startupList = if (isFirstProcessStart) {
                selectAutoTriggeredQuickReplies(setting.quickMessages, "startup")
            } else emptyList()
            val chatChangeList = selectAutoTriggeredQuickReplies(setting.quickMessages, "chatchange")
            val combinedQrs = (startupList + chatChangeList).distinctBy { it.id }
            if (combinedQrs.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val host = buildChatSlashHost(context, vm, toaster, vm.inputState, openConversation)
                    combinedQrs.forEach { qm ->
                        val c = qm.content.trim()
                        if (c.startsWith("/") || c.lineSequence().any { it.trim().startsWith("/") }) {
                            StSlashExecutor.execute(c, host)
                        }
                    }
                }
            }
        }
        if (setting.init && conversation.messageNodes.isEmpty()) {
            TavernRuntimeManager.appendLog(
                "debug",
                "[route] tavern bind deferred (dummy settings init=true): conv=${conversation.id} assistant=${conversation.assistantId}",
            )
        } else {
            // [v214] supplier 全部就位后再切 active conversation，消除「先切后读旧 assistant」时序竞态
            TavernRuntimeManager.setActiveConversation(conversation.id.toString())
            TavernRuntimeManager.appendLog(
                "info",
                "[route] tavern bind: conv=${conversation.id} assistant=${conversation.assistantId}",
            )
        }
    }

    LaunchedEffect(setting.displaySetting.resourceNetworkMode) {
        TavernRuntimeManager.resourceNetworkMode = setting.displaySetting.resourceNetworkMode
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // [v245 DIAG H0] ChatPage 组合进出留痕 —— 用来判定「设置页在栈顶时 ChatPage 到底还在不在组合里」。
    // 下一轮真机复现路径：打开抽屉 → 点设置 → 返回。
    //   若日志里在进入设置页时就出现 `ChatPage leave composition`，说明 ChatPage 已被销毁，
    //   H1 的门槛是冗余的（但无害），两次返回的真因在别处；
    //   若**没有**出现 leave，说明 ChatPage 一直活着、BackHandler 确实在设置页生效，H1 就是正解。
    val drawerOpenState = rememberUpdatedState(drawerState.isOpen)
    DisposableEffect(Unit) {
        TavernRuntimeManager.appendLog(
            "info",
            "[nav] ChatPage enter composition conv=$id isOnTop=$isOnTop drawerOpen=${drawerOpenState.value}",
        )
        onDispose {
            TavernRuntimeManager.appendLog(
                "info",
                "[nav] ChatPage leave composition conv=$id isOnTop=$isOnTop drawerOpen=${drawerOpenState.value}",
            )
        }
    }

    // [v245 BUGFIX H1] 抽屉的返回手势处理。
    //
    // 真机实测（用户原话）：「第五设置页返回又变成了需要全面屏手势返回两次了。」
    // 三/四轮对比（同一条路径、只差「进设置时是否主动收抽屉」）：
    //   · 第三轮主动收抽屉 → 设置页一次返回就离开 ✅，但回到聊天页时抽屉是关的
    //     （用户：「跳过『有侧边栏』那一层」）；
    //   · 第四轮撤销收抽屉 → 抽屉保持打开，设置页要滑两次 ❌。
    // ⇒ 唯一变量是 drawerState.isOpen ⇒ **只有本 BackHandler 会在设置页吃掉第一次返回**。
    //
    // 所以门槛必须加「本页是栈顶」：抽屉开着、但当前显示的是设置页时，这个 handler
    // 不该拦截系统返回 —— 交给 NavDisplay 弹栈，抽屉状态由 rememberSaveable 原样保留，
    // 回到聊天页时侧边栏那一层还在（用户要的语义）。
    BackHandler(enabled = isOnTop && drawerState.isOpen) {
        TavernRuntimeManager.appendLog("info", "[nav] ChatPage BackHandler 吃掉了返回（收起抽屉）conv=${id}")
        scope.launch {
            drawerState.close()
        }
    }

    // Clear input focus so popup transitions cannot reopen the keyboard.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    // 进入大屏（永久抽屉）模式时重置抽屉状态为关闭，
    // 避免从横屏旋转回竖屏后，模态抽屉残留为打开状态且无法关闭（#1304）
    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.messageNodes.lastIndex.coerceAtLeast(0))
            }
            vm.chatListInitialized = true
        }
    }

    // 开场白选择器：新对话且有多条开场白时自动弹出
    var showGreetingPicker by rememberSaveable { mutableStateOf(false) }
    var greetingPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(conversation.newConversation, setting.assistants, setting.assistantId) {
        if (conversation.newConversation && !greetingPicked) {
            val assistant = setting.getAssistantById(conversation.assistantId)
                ?: setting.getCurrentAssistant()
            val tav = assistant?.tavernData
            val allGreetings = listOfNotNull(tav?.firstMessage?.takeIf { it.isNotBlank() }) +
                (tav?.alternateGreetings?.filter { it.isNotBlank() } ?: emptyList())
            if (allGreetings.size > 1) {
                showGreetingPicker = true
            }
        }
    }

    if (showGreetingPicker && !greetingPicked) {
        val assistant = setting.getAssistantById(conversation.assistantId)
            ?: setting.getCurrentAssistant()
        val tav = assistant?.tavernData
        val allGreetings = listOfNotNull(tav?.firstMessage?.takeIf { it.isNotBlank() }) +
            (tav?.alternateGreetings?.filter { it.isNotBlank() } ?: emptyList())

        GreetingPickerDialog(
            greetings = allGreetings,
            currentGreeting = assistant?.presetMessages
                ?.firstOrNull { it.role == me.rerere.ai.core.MessageRole.ASSISTANT }
                ?.toText() ?: "",
            onSelect = { greeting ->
                val updatedAssistant = assistant?.copy(
                    presetMessages = listOf(UIMessage.assistant(greeting))
                )
                if (updatedAssistant != null) {
                    val newConv = conversation.copy(
                        messageNodes = listOf()
                    ).updateCurrentMessages(updatedAssistant.presetMessages)
                    vm.updateConversation(newConv)
                }
                greetingPicked = true
                showGreetingPicker = false
            },
            onDismiss = {
                greetingPicked = true
                showGreetingPicker = false
            },
        )
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting,
                        drawerState = drawerState,
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    // 发送防抖：一条消息从「点击发送」到「真正落库」之间锁住发送按钮
    var sendPending by remember { mutableStateOf(false) }
    // 酒馆运行时：/js 执行结果 → Toast（M1 可见反馈）
    val tavernJsResult by TavernRuntimeManager.lastJsResult.collectAsStateWithLifecycle()
    val slashModalRequest by TavernRuntimeManager.pendingSlashModal.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        TavernRuntimeManager.isSlashModalUiAttached = true
        onDispose {
            TavernRuntimeManager.isSlashModalUiAttached = false
        }
    }

    slashModalRequest?.let { modalReq ->
        when (modalReq) {
            is me.rerere.rikkahub.data.st.runtime.SlashModalRequest.Popup -> {
                AlertDialog(
                    onDismissRequest = {
                        modalReq.deferred.complete(if (modalReq.expectResult) "0" else "")
                    },
                    title = { Text(modalReq.title?.takeIf { it.isNotBlank() } ?: "提示") },
                    text = { Text(modalReq.text) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                modalReq.deferred.complete(if (modalReq.expectResult) "1" else modalReq.text)
                            }
                        ) {
                            Text(modalReq.okButton?.takeIf { it.isNotBlank() } ?: stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = if (modalReq.expectResult || !modalReq.cancelButton.isNullOrBlank()) {
                        {
                            TextButton(
                                onClick = {
                                    modalReq.deferred.complete(if (modalReq.expectResult) "0" else "")
                                }
                            ) {
                                Text(modalReq.cancelButton?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cancel))
                            }
                        }
                    } else null,
                )
            }
            is me.rerere.rikkahub.data.st.runtime.SlashModalRequest.Input -> {
                var inputVal by remember(modalReq) { mutableStateOf(modalReq.defaultValue) }
                AlertDialog(
                    onDismissRequest = { modalReq.deferred.complete("") },
                    title = { Text(modalReq.title?.takeIf { it.isNotBlank() } ?: "输入") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (modalReq.prompt.isNotBlank()) {
                                Text(modalReq.prompt)
                            }
                            OutlinedTextField(
                                value = inputVal,
                                onValueChange = { inputVal = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { modalReq.deferred.complete(inputVal) }) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { modalReq.deferred.complete("") }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
            is me.rerere.rikkahub.data.st.runtime.SlashModalRequest.Buttons -> {
                AlertDialog(
                    onDismissRequest = { modalReq.deferred.complete("") },
                    title = { Text("请选择") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (modalReq.prompt.isNotBlank()) {
                                Text(modalReq.prompt)
                            }
                            modalReq.labels.forEach { label ->
                                OutlinedButton(
                                    onClick = {
                                        val out = if (modalReq.multiple) "[\"$label\"]" else label
                                        modalReq.deferred.complete(out)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { modalReq.deferred.complete("") }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    // 酒馆运行时事件 → 应用内提示（离屏 WebView 的 toast / MVU 状态桥）
    LaunchedEffect(Unit) {
        var lastToastAt = 0L
        var lastToastMsg = ""
        // [v245 BUGFIX E2] 接线留痕（真机可判定）
        TavernRuntimeManager.appendLog("info", "[tavern] onJsEvent bound conv=${conversation.id}")
        TavernRuntimeManager.onJsEvent = { name, payloadJson ->
            val now = System.currentTimeMillis()
            scope.launch {
                when (name) {
                    "mvu_ready" -> toaster.show(message = "酒馆运行时就绪：MVU 已激活", type = ToastType.Success)
                    "mvu_error" -> {
                        val errBrief = runCatching {
                            org.json.JSONObject(payloadJson).optString("error")
                        }.getOrNull().orEmpty()
                            .replace(Regex("<[^>]+>"), " ")
                            .trim()
                            .take(200)
                        toaster.show(
                            message = if (errBrief.isBlank()) {
                                "酒馆运行时：MVU 初始化失败（输入 /js __rikkaMvuReload() 可重载）"
                            } else {
                                "酒馆运行时：MVU 初始化失败：$errBrief"
                            },
                            type = ToastType.Error,
                        )
                    }
                    "toast" -> {
                        val parsed = runCatching {
                            val obj = org.json.JSONObject(payloadJson)
                            val args = obj.optJSONArray("args")
                            val text = args?.optString(0).orEmpty()
                            val title = args?.optString(1).orEmpty()
                            val type = when (obj.optString("level")) {
                                "error" -> ToastType.Error
                                "warning", "warn" -> ToastType.Warning
                                else -> ToastType.Normal
                            }
                            Triple(text, title, type)
                        }.getOrNull()
                        if (parsed != null) {
                            val plain = parsed.first.replace(Regex("<[^>]+>"), " ").trim().take(280)
                            val title = parsed.second.replace(Regex("<[^>]+>"), " ").trim().take(60)
                            if (plain.isNotBlank() && !(now - lastToastAt < 900 && plain == lastToastMsg)) {
                                lastToastAt = now
                                lastToastMsg = plain
                                toaster.show(
                                    message = if (title.isNotBlank()) "$title：$plain" else plain,
                                    type = parsed.third,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    // [v245 BUGFIX E2] 不再在 onDispose 里清空 onJsEvent（与 E1 同一缺陷类）。
    // ChatUtil.navigateToChatPage 走 `navigator.clearAndNavigate(...)`（清栈）→ NavDisplay 转场期间
    // outgoing / incoming 两个 entry 同时组合，incoming 的 side-effect 先跑、outgoing 的 onDispose
    // 在转场结束后才跑 → 新页面刚接好的 onJsEvent 被旧页面置回 null。
    // 此后 TavernRuntimeManager 里 `onJsEvent?.invoke(...)` 会**静默丢弃**所有 toast / MVU 事件
    // （日志里 [js-event] 仍有记录，用户却看不到任何提示）。
    // 旧闭包 capture 的 rememberCoroutineScope 已随组合取消，残留不会真的执行 UI 操作。
    // [v245 BUGFIX E2] 真机可判定的留痕：下一轮日志里能直接看到「离开组合时没有清空 onJsEvent」。
    DisposableEffect(Unit) {
        onDispose {
            TavernRuntimeManager.appendLog(
                "info",
                "[slash] onJsEvent retained after onDispose conv=${conversation.id}（不再置空）",
            )
        }
    }
    LaunchedEffect(tavernJsResult) {
        val r = tavernJsResult ?: return@LaunchedEffect
        toaster.show(
            context.getString(
                if (r.ok) R.string.slash_toast_js_result else R.string.slash_toast_js_error,
                r.text,
            )
        )
    }
    // STscript 中枢：JS triggerSlash 与快速回复自动执行共用同一宿主
    val currentContext = rememberUpdatedState(context)
    val currentToaster = rememberUpdatedState(toaster)
    val currentInputState = rememberUpdatedState(inputState)
    // 打开会话（/checkpoint-go /checkpoint-exit /branch-create 的导航目标）
    val openConversation: (Uuid) -> Unit = { id ->
        scope.launch { navigateToChatPage(navController, chatId = id) }
    }
    DisposableEffect(Unit) {
        // [v240 W4] 斜杠命令诊断日志 → tavern-runtime.log（真机验证闭包 / /run 用）
        StSlashExecutor.debugLogger = { msg -> TavernRuntimeManager.appendLog("info", msg) }
        TavernRuntimeManager.slashRunner = { script ->
            StSlashExecutor.execute(
                script,
                buildChatSlashHost(currentContext.value, vm, currentToaster.value, currentInputState.value, openConversation),
            )
        }
        TavernRuntimeManager.quietGenerator = { prompt, raw, sys ->
            val host = buildChatSlashHost(currentContext.value, vm, currentToaster.value, currentInputState.value, openConversation)
            if (raw) {
                host.genRaw(prompt = prompt, systemPrompt = sys).orEmpty()
            } else {
                host.gen(prompt = prompt, asRole = "system", lock = false, length = 0, name = null, trim = false).orEmpty()
            }
        }
        // [v245 BUGFIX E1] 接线留痕（真机可判定）：日志里能直接看到「谁在什么时候接的线」。
        TavernRuntimeManager.appendLog("info", "[slash] slashRunner bound conv=${conversation.id}")
        onDispose {
            // [v245 BUGFIX E1] **不再置空 slashRunner / debugLogger**（T1「自动发送」根因）。
            //
            // 真机取证（round-4 tavern-runtime.log，同一现象两次，00:26:31 与 00:37:29 完全一致）：
            //   [10-14 00:26:31.943][info] [chat] insertChatMessages n=1 roles=[USER]
            //   [10-14 00:26:32.016][error] [slash] triggerSlash: slashRunner 未接线…script='/trigger'
            // chatInserter / lorebookUpserter / lorebookUpdater / userMessageSender 与它由**同一批**
            // 注入点设置，却**只有 slashRunner 被 onDispose 清空** —— 这就是不对称的证据。
            //
            // 机制（已用调用链证实，非推测）：
            //   ① app/src/main/java/me/rerere/rikkahub/utils/ChatUtil.kt 的 navigateToChatPage(...)
            //      走 `navigator.clearAndNavigate(...)` → 切会话 / 新建会话会**清空 back stack**，
            //      旧 Screen.Chat entry 被移除；
            //   ② NavDisplay 的转场（RouteActivity.kt:325，Chat 相关走 fadeIn togetherWith fadeOut）
            //      要求 outgoing / incoming 两个 entry **同时**在组合里；Compose 的 DisposableEffect
            //      顺序是「incoming 先跑 side-effect，outgoing 的 onDispose 在转场结束后才跑」；
            //   ③ 于是新会话刚把 slashRunner 接好，旧会话的 onDispose 立刻把它置回 null。
            //   卡脚本的固定序列 `await createChatMessages(...); await triggerSlash('/trigger')`
            //   必然拿到 null → 开局链路断在 /trigger，而 insertChatMessages 成功（正是日志的样子）。
            //
            // 修法：与其它桥保持**同一生命周期**——「最后一个绑定的 ChatPage 拥有运行时桥」是本工程
            // v214 起的既定约定（chatInserter 等从来不清空）。旧闭包最多存活到下一个 ChatPage 绑定；
            // 它 capture 的 scope 已随组合取消，不会真的执行导航 / 写输入框。
            TavernRuntimeManager.appendLog(
                "info",
                "[slash] slashRunner retained after onDispose conv=${conversation.id}（不再置空）",
            )
        }
    }
    val workspaceRepository: WorkspaceRepository = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        setting.getCurrentChatModel()?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    // [v240 W3] 表情立绘（Character Expressions）：生成完成后按 messageId 分类，结果进 ExpressionStore
    CharacterExpressionHost(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        // [v225 F4] 主人拍板（P1 = A）：磨砂的作用对象 = 输入栏 + 气泡本身，
        // 不得把整张聊天壁纸模糊掉。v222 的「壁纸侧一次性 blur」在真机被验收判为 bug
        // （原话：不仅是气泡磨砂了，输入框没有磨砂，反而背景磨砂了），本轮撤掉。
        // 输入栏的模糊走 Haze 真实 backdrop blur（只糊输入栏那一小块，见 ChatInput.kt）；
        // 气泡保持「半透明底 + hairline 细边框」，不做「背后模糊」。
        AssistantBackground(
            setting = setting,
            modifier = Modifier.hazeSource(hazeState),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    }
                )
            },
            bottomBar = {
                Column {
                    if (!setting.init && currentChatModel == null && setting.getCurrentChatModel() == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "尚未配置默认聊天模型，完成 2 步配置即可开聊",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { navController.navigate(Screen.ModelWizard) }) {
                                        Text("极速配置", style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(onClick = { navController.navigate(Screen.Onboarding) }) {
                                        Text("新手向导", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    TavernScriptButtonsRow()
                    val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                    ChatInput(
                        onStartVoiceMode = onStartVoiceMode,
                        voiceState = voiceState,
                        onStopVoiceMode = vm.voiceSession::stop,
                        state = inputState,
                        messageQueue = messageQueue,
                        onRemoveQueuedMessage = vm::removeQueuedMessage,
                        onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                        onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                        onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    enableSearch = enableWebSearch,
                    onUpdateSearchMode = { mode ->
                        val current = setting.getCurrentAssistant()
                        val model = setting.getCurrentChatModel()
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == current.id) {
                                        assistant.copy(enableWebSearch = mode == SearchMode.LOCAL)
                                    } else {
                                        assistant
                                    }
                                },
                                providers = if (model == null) {
                                    setting.providers
                                } else {
                                    setting.providers.map { provider ->
                                        provider.editModel(
                                            model.copy(
                                                tools = if (mode == SearchMode.BUILT_IN) {
                                                    model.tools + BuiltInTools.Search
                                                } else {
                                                    model.tools - BuiltInTools.Search
                                                }
                                            )
                                        )
                                    }
                                },
                            )
                        )
                    },

                    onCancelClick = { vm.stopGeneration() },
                    onSendClick = {
                        val effectiveModel = currentChatModel
                            ?: setting.getCurrentChatModel()
                            ?: setting.findModelById(setting.chatModelId)
                        if (effectiveModel == null) {
                            TavernRuntimeManager.appendLog(
                                "warn",
                                "[send-diag] onSendClick aborted: no chat model selected conv=${conversation.id}",
                            )
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                            return@ChatInput
                        }
                        val contents = inputState.getContents()
                        if (contents.isEmptyInputMessage()) {
                            return@ChatInput
                        }
                        val editingId = inputState.editingMessage
                        val isValidEditTarget = editingId != null &&
                            conversation.messageNodes.any { node -> node.messages.any { it.id == editingId } }
                        if (isValidEditTarget) {
                            vm.handleMessageEdit(contents, editingId!!)
                            inputState.clearInput()
                        } else {
                            if (editingId != null) {
                                // 若曾误触历史消息遗留了已失效的 editingMessage，自动清除并按正常新消息发送
                                inputState.editingMessage = null
                            }
                            if (sendPending) {
                                toaster.show("上一条消息正在发送，请稍候…")
                                return@ChatInput
                            }
                            val draftSnapshot = contents
                            val shouldAutoScroll = !chatListState.canScrollForward
                            sendPending = true
                            val sent = vm.handleMessageSend(contents, onPersisted = { persisted ->
                                sendPending = false
                                if (persisted) {
                                    // 只有用户消息真正落库后才清空输入，避免发送失败时草稿消失。
                                    // 同时比较完整草稿（含附件），避免误清空等待期间新加的附件。
                                    if (!inputState.isEditing() && inputState.getContents() == draftSnapshot) {
                                        inputState.clearInput()
                                    }
                                    if (shouldAutoScroll) {
                                        val target = vm.conversation.value.messageNodes.lastIndex.coerceAtLeast(0)
                                        TavernRuntimeManager.recordSendDiagnostic(
                                            stage = TavernRuntimeManager.SendDiagnosticStage.STAGE_4_SCROLL_TRIGGERED,
                                            conversationId = conversation.id.toString(),
                                            detail = "source=ChatPage_onSendClick targetIdx=$target firstVisible=${chatListState.firstVisibleItemIndex}",
                                        )
                                        scope.launch { chatListState.requestScrollToItem(target) }
                                    }
                                } else {
                                    // 失败时恢复草稿（仅当用户没有继续输入新内容）。
                                    if (inputState.getContents() == draftSnapshot || inputState.isEmpty()) {
                                        inputState.setContents(draftSnapshot)
                                    }
                                    toaster.show("发送失败，草稿已保留，请重试", type = ToastType.Error)
                                }
                            })
                            if (!sent) {
                                sendPending = false
                                toaster.show("发送失败，草稿已保留", type = ToastType.Error)
                            } else {
                                scope.launch {
                                    delay(15.seconds)
                                    if (sendPending) {
                                        // 排队等待生成结束时不会立刻落库；给用户明确的“已排队”状态，
                                        // 避免发送按钮永久卡在“正在发送”。若用户已继续输入，则只解除发送锁，
                                        // 不清掉新草稿。
                                        sendPending = false
                                        if (inputState.getContents() == draftSnapshot) {
                                            inputState.clearInput()
                                        }
                                        toaster.show("消息已加入发送队列，将在当前回复结束后发送")
                                    }
                                }
                                val userQrs = selectAutoTriggeredQuickReplies(setting.quickMessages, "user")
                                if (userQrs.isNotEmpty()) {
                                    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val host = buildChatSlashHost(context, vm, toaster, inputState, openConversation)
                                        userQrs.forEach { qm ->
                                            val c = qm.content.trim()
                                            if (c.startsWith("/") || c.lineSequence().any { it.trim().startsWith("/") }) {
                                                StSlashExecutor.execute(c, host)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                            inputState.clearInput()
                        } else {
                            val draft = inputState.getContents()
                            val sent = vm.handleMessageSend(content = draft, answer = false, onPersisted = { persisted ->
                                if (persisted) {
                                    if (!inputState.isEditing() && inputState.getContents() == draft) inputState.clearInput()
                                } else {
                                    if (inputState.getContents() == draft || inputState.isEmpty()) {
                                        inputState.setContents(draft)
                                    }
                                    toaster.show("发送失败，草稿已保留", type = ToastType.Error)
                                }
                            })
                            if (!sent) {
                                toaster.show("发送失败，草稿已保留", type = ToastType.Error)
                            }
                        }
                    },
                    onSlashDuplicate = {
                        val src = setting.getCurrentAssistant()
                        val dup = src.copy(
                            id = kotlin.uuid.Uuid.random(),
                            name = src.name.ifBlank { context.getString(R.string.slash_ui_unnamed) } + context.getString(R.string.slash_ui_copy_suffix),
                        )
                        vm.updateSettings(
                            setting.copy(assistants = setting.assistants + dup)
                        )
                        toaster.show(context.getString(R.string.slash_toast_char_duplicated, dup.name))
                    },
                    onSlashInsert = { role, text, name, at ->
                        vm.handleInsertMessage(role, text, name, at)
                    },
                    onSlashPersona = { query, mode ->
                        val personas = setting.personas
                        val current = personas.find { it.id == setting.activePersonaId }
                        when {
                            query.isBlank() -> {
                                toaster.show(
                                    if (current != null) {
                                        context.getString(R.string.slash_toast_persona_current, current.name)
                                    } else {
                                        context.getString(R.string.slash_toast_persona_none_active, personas.joinToString(context.getString(R.string.slash_field_sep)) { it.name })
                                    }
                                )
                            }

                            query.equals("off", ignoreCase = true) || query.equals("none", ignoreCase = true) -> {
                                if (setting.activePersonaId != null) {
                                    vm.updateSettings(setting.copy(activePersonaId = null))
                                    toaster.show(context.getString(R.string.slash_toast_persona_off))
                                } else {
                                    toaster.show(context.getString(R.string.slash_toast_persona_already_off))
                                }
                            }

                            else -> {
                                val target = personas.firstOrNull {
                                    it.name.equals(query, ignoreCase = true) || it.title.equals(query, ignoreCase = true)
                                }
                                when {
                                    // 官方 /persona-set mode=lookup：只选已有人设
                                    target == null && mode == "lookup" -> {
                                        toaster.show(context.getString(R.string.slash_toast_persona_not_found, query))
                                    }

                                    // 官方 /persona-set mode=temp：只设置临时用户名，不找/不选人设
                                    target != null && mode == "temp" -> {
                                        vm.updateSettings(setting.copy(displaySetting = setting.displaySetting.copy(userNickname = query)))
                                        toaster.show(context.getString(R.string.slash_toast_persona_temp_set, query))
                                    }

                                    // 官方 /persona-set mode=all（默认）：先找已有，找不到则设置临时用户名
                                    target != null -> {
                                        vm.updateSettings(setting.copy(activePersonaId = target.id))
                                        toaster.show(context.getString(R.string.slash_toast_persona_switched, target.name))
                                    }

                                    else -> {
                                        vm.updateSettings(setting.copy(displaySetting = setting.displaySetting.copy(userNickname = query)))
                                        toaster.show(context.getString(R.string.slash_toast_persona_fallback, query, query))
                                    }
                                }
                            }
                        }
                    },
                    onSlashTrigger = {
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                        } else if (vm.conversation.value.currentMessages.isEmpty()) {
                            toaster.show(context.getString(R.string.slash_toast_trigger_no_messages), type = ToastType.Warning)
                        } else {
                            vm.handleTriggerGeneration()
                        }
                    },
                    onSlashContinue = { prompt ->
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                        } else {
                            vm.continueGeneration(prompt)
                        }
                    },
                    onSlashImpersonate = { prefill ->
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                        } else {
                            var announced = false
                            vm.impersonateDraft(prefill) { draft ->
                                // 官方流式写输入框：每次 chunk 全量替换
                                inputState.setMessageText(draft)
                                if (!announced) {
                                    announced = true
                                    toaster.show(context.getString(R.string.slash_toast_draft_ready))
                                }
                            }
                        }
                    },
                    onSlashRerollPick = { seedArg ->
                        val chatKey = conversation.id.toString()
                        val latest = vm.latestSettingsSnapshot()
                        val current = latest.macroChatVariables[chatKey]?.get("__pick_reroll_seed")?.toLongOrNull() ?: 0L
                        val next = seedArg.toLongOrNull() ?: (current + 1)
                        vm.patchSettings { s ->
                            applyMacroVarSlash(
                                context = context,
                                settings = s,
                                op = SlashVarOp.SET,
                                name = "__pick_reroll_seed",
                                value = next.toString(),
                                chatKey = chatKey,
                            ).first
                        }
                        toaster.show(context.getString(R.string.slash_toast_reroll_done, next))
                    },
                    onSlashSysgen = { prompt, name, at, trim ->
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                        } else {
                            vm.handleGenerateSystemNarration(prompt, name, at, trim)
                        }
                    },
                    onSlashJs = { code ->
                        TavernRuntimeManager.runScript(code)
                    },
                    onSlashTavern = { sub ->
                        when (sub) {
                            "reload" -> {
                                TavernRuntimeManager.reloadMvu()
                                toaster.show(
                                    context.getString(R.string.slash_toast_tavern_reloading),
                                    type = ToastType.Success,
                                )
                            }
                            "status" -> {
                                TavernRuntimeManager.queryMvuStatus { status ->
                                    toaster.show(context.getString(R.string.slash_toast_tavern_status, status))
                                }
                            }
                        }
                    },
                    // 消息级操作（/hide /unhide /swipe）：复用 STscript 宿主，保证两条通道语义一致
                    onSlashHide = { value, unhide, nameFilter ->
                        buildChatSlashHost(context, vm, toaster, inputState, openConversation)
                            .hideMessages(value, unhide, nameFilter)
                    },
                    onSlashSwipe = { direction, await ->
                        buildChatSlashHost(context, vm, toaster, inputState, openConversation)
                            .swipe(direction, await)
                    },
                    // STscript 统一执行通道：移入后台协程执行，严禁在 UI 主线程同步调用 runBlocking 造成 WebView 死锁或 ANR
                    onSlashScript = { script ->
                        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            val result = StSlashExecutor.execute(
                                script,
                                buildChatSlashHost(context, vm, toaster, inputState, openConversation),
                            )
                            if (result.startsWith("未知命令") || result.startsWith("命令执行失败") ||
                                result.startsWith("命令不可用") || result.startsWith("用法：")
                            ) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    toaster.show(result)
                                }
                            }
                        }
                        null
                    },
                    // 快速回复自动执行（对齐酒馆 QR executeWithOptions）：斜杠开头 → 后台协程 STscript 执行；纯文本 → 直接发送
                    onQuickMessageExecute = { quickMessage ->
                        val content = quickMessage.content.trim()
                        if (content.startsWith("/") || content.lineSequence().any { it.trim().startsWith("/") }) {
                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                val result = StSlashExecutor.execute(
                                    content,
                                    buildChatSlashHost(context, vm, toaster, inputState, openConversation),
                                )
                                if (result.startsWith("未知命令") || result.startsWith("命令执行失败") ||
                                    result.startsWith("命令不可用") || result.startsWith("用法：")
                                ) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        toaster.show(result)
                                    }
                                }
                            }
                        } else if (content.isNotEmpty()) {
                            if (currentChatModel == null) {
                                toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                            } else {
                                val shouldScroll = !chatListState.canScrollForward
                                vm.handleMessageSend(
                                    listOf(UIMessagePart.Text(content)),
                                    onPersisted = { persisted ->
                                        if (persisted && shouldScroll) {
                                            scope.launch {
                                                chatListState.requestScrollToItem(
                                                    vm.conversation.value.messageNodes.lastIndex.coerceAtLeast(0)
                                                )
                                            }
                                        } else if (!persisted) {
                                            toaster.show("发送失败，请重试", type = ToastType.Error)
                                        }
                                    },
                                )
                            }
                        }
                    },
                    onSlashGen = { args, onDraft ->
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                        } else {
                            vm.quietGenerate(args, onDraft)
                        }
                    },
                    onSlashVar = { op, name, value ->
                        val chatKey = conversation.id.toString()
                        val latest = vm.latestSettingsSnapshot()
                        val ( previewSettings, result) = applyMacroVarSlash(
                            context = context,
                            settings = latest,
                            op = op,
                            name = name,
                            value = value,
                            chatKey = chatKey,
                        )
                        if (previewSettings !== latest) {
                            vm.patchSettings { s ->
                                applyMacroVarSlash(
                                    context = context,
                                    settings = s,
                                    op = op,
                                    name = name,
                                    value = value,
                                    chatKey = chatKey,
                                ).first
                            }
                        }
                        result
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                },
                                assistantId = it.id
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
                }
            },
        ) { innerPadding ->
            // [v222 R3] 顶栏「磨砂玻璃 + 渐变」遮罩：覆盖在消息列表之上的独立装饰层（A4 常驻 / A5 不跟随消息样式）。
            // 只加一层装饰，不动 LazyColumn 的 clip / padding，不碰 MessageHtmlBlock / PanelScrollBridge / message key。
            Box(modifier = Modifier.fillMaxSize()) {
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = { vm.regenerateAtMessage(it) },
                onImpersonate = { msg ->
                    inputState.setMessageText(msg.toText())
                },
                onEdit = { msg ->
                    if (msg.role == MessageRole.ASSISTANT &&
                        setting.displaySetting.enableWebViewPerfReduction &&
                        setting.displaySetting.webViewPerfMode == me.rerere.rikkahub.data.datastore.WebViewPerfMode.HISTORICAL_SNAPSHOT_IMAGE
                    ) {
                        toaster.show("当前已选择历史楼层图片固化方案，不能修改助手的消息")
                    } else {
                        inputState.setContents(msg.parts)
                        inputState.editingMessage = msg.id
                    }
                },
                onForkMessage = { msg ->
                    scope.launch {
                        val fork = vm.forkMessage(message = msg)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onCreateCheckpointMessage = { msg ->
                    scope.launch {
                        val index = vm.conversation.value.messageNodes
                            .indexOfFirst { node -> node.messages.any { it.id == msg.id } }
                        if (index >= 0) {
                            try {
                                val checkpoint = vm.createCheckpoint(nodeIndex = index)
                                toaster.show(
                                    context.getString(R.string.slash_toast_checkpoint_created, checkpoint.title),
                                    type = ToastType.Success,
                                )
                            } catch (e: Exception) {
                                toaster.show(
                                    context.getString(
                                        R.string.slash_toast_checkpoint_create_failed,
                                        e.message ?: e.javaClass.simpleName,
                                    ),
                                    type = ToastType.Error,
                                )
                            }
                        }
                    }
                },
                onDelete = {
                    // 删除整楼是会话原子操作；不再被 UI 的 loadingJob 状态拦截（生成中也允许删历史楼）。
                    vm.deleteMessage(it)
                },
                onDeleteSwipe = { node ->
                    if (node.messages.size > 1) {
                        vm.deleteSwipeVariant(node)
                    }
                },

                onUpdateMessage = { newNode ->
                    val oldNode = conversation.messageNodes.firstOrNull { it.id == newNode.id }
                    if (oldNode != null && oldNode.selectIndex != newNode.selectIndex) {
                        vm.notifyMessageSwiped(newNode)
                    }
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.requestScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onToggleHidden = { node ->
                    vm.toggleMessageHidden(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },

            )

                // [v240 W3] 表情立绘叠加层：位于消息列表之上、顶栏遮罩之下（默认关闭）
                CharacterExpressionOverlay(
                    setting = setting,
                    conversation = conversation,
                )

                // [ST 1.19.0 体验升级] 常驻右侧悬浮球与全能悬浮窗容器（ST-iPhonie 小手机 / 记忆表格 / 小白X / 角色卡悬浮窗）
                TavernFloatingWindowOverlay()

                // [v233 主人拍板] 顶栏遮罩**总开关**（默认关：「顶栏模糊直接给我删掉吧，太难看」）。
                // 开 = 按「消息样式 → 顶栏」里选的样式渲染；关 = 完全不渲染。
                if (setting.messageStyle.topFadeEnabled) {
                    ChatTopFadeOverlay(
                        modifier = Modifier.align(Alignment.TopCenter),
                        topInset = innerPadding.calculateTopPadding(),
                        // [v229 P1-4] 两版参数由「消息样式 → 顶栏」开关切换（默认 = 主人描述那版）。
                        //   用全限定名调用：本文件没有 import 该 enum，避免为一个参数新增 import。
                        style = if (setting.messageStyle.topFadeBottomAnchored) {
                            me.rerere.rikkahub.ui.components.frosted.ChatTopFadeStyle.BOTTOM_ANCHORED
                        } else {
                            me.rerere.rikkahub.ui.components.frosted.ChatTopFadeStyle.KELIVO_TOP_DOWN
                        },
                    )
                }
            }
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onDismiss = { showFilesSheet = false },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val chatModel = setting.getCurrentChatModel()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, keepRecentMessages)
            },
            onUpdateAssistant = vm::updateAssistant,
            onUpdateSettings = { vm.updateSettings(it) },
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
        )
    }
}

/**
 * 开场白选择弹窗 — 新对话有多条开场白时自动弹出
 */
@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    bigScreen: Boolean,
    drawerState: DrawerState,
    previewMode: Boolean,
    onNewChat: () -> Unit,
    onClickMenu: () -> Unit,
    onUpdateTitle: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }
    TopAppBar(
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.getCurrentAssistant()
                    val model = settings.getCurrentChatModel()
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = AppType.micro,
                            )
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (!bigScreen) {
                IconButton(onClick = {
                    scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                }) {
                    Icon(HugeIcons.Menu03, contentDescription = null)
                }
            }
        },
        actions = {
            IconButton(onClick = onClickMenu) {
                Icon(
                    if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet,
                    contentDescription = null,
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(HugeIcons.MessageAdd01, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = { titleState.dismiss() },
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { titleState.confirm() }) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { titleState.dismiss() }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }
}

@Composable
private fun GreetingPickerDialog(
    greetings: List<String>,
    currentGreeting: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CustomColors.listItemColors.containerColor,
        dragHandle = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_page_select_opening),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            itemsIndexed(greetings) { index, greeting ->
                val isSelected = greeting == currentGreeting
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelected) { if (!isSelected) onSelect(greeting) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            CustomColors.listItemColors.containerColor,
                    ),
                    shape = RoundedCornerShape(AppRadii.xl),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        val previewText = remember(greeting) {
                            me.rerere.rikkahub.ui.components.message.extractQuickPlainPreview(greeting)
                                .take(300)
                                .ifBlank { greeting.take(300).trim() }
                        }
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.chat_page_opening_current),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
