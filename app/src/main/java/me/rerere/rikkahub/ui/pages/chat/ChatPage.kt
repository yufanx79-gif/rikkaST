package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.appTempFolder
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.ui.theme.AppRadii
import me.rerere.rikkahub.ui.theme.AppSpacing
import me.rerere.rikkahub.ui.theme.AppType
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.TavernScriptButtonsRow
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.SlashVarOp
import me.rerere.rikkahub.ui.components.ai.applyMacroVarSlash
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.ai.ui.isEmptyInputMessage
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
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

    // 酒馆运行时：注册当前会话上下文（变量 / MVU / 脚本宿主）
    val tavernLatestConversation = rememberUpdatedState(conversation)
    val tavernLatestSettings = rememberUpdatedState(setting)
    // [v214] key 追加 assistantId：会话数据晚到时 assistantId 变化必须重跑，否则卡脚本永不重载（P0）
    LaunchedEffect(conversation.id, conversation.assistantId) {
        TavernRuntimeManager.ensureStarted(context)
        TavernRuntimeManager.chatJsonSupplier = {
            val conv = tavernLatestConversation.value
            buildTavernChatJson(conv, TavernVariableStore.getMessageVars(conv.id.toString()))
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
            vm.updateSettings(tavernLatestSettings.value.copy(tavernExtensionSettings = json))
        }
        // [v214] 世界书写回桥（JS RikkaBridge.updateLorebookEntries）
        TavernRuntimeManager.lorebookUpdater = { name, json ->
            vm.applyLorebookUpdate(name, json)
        }
        // [v214] 面板表单发送桥（JS RikkaBridge.sendUserMessage）
        TavernRuntimeManager.userMessageSender = { text ->
            vm.handleMessageSend(listOf(UIMessagePart.Text(text)))
            true
        }
        // [v214] supplier 全部就位后再切 active conversation，消除「先切后读旧 assistant」时序竞态
        TavernRuntimeManager.setActiveConversation(conversation.id.toString())
        TavernRuntimeManager.appendLog(
            "info",
            "[route] tavern bind: conv=${conversation.id} assistant=${conversation.assistantId}",
        )
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
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

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
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
    // 酒馆运行时：/js 执行结果 → Toast（M1 可见反馈）
    val tavernJsResult by TavernRuntimeManager.lastJsResult.collectAsStateWithLifecycle()

    // 酒馆运行时事件 → 应用内提示（离屏 WebView 的 toast / MVU 状态桥）
    LaunchedEffect(Unit) {
        var lastToastAt = 0L
        var lastToastMsg = ""
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
    DisposableEffect(Unit) {
        onDispose { TavernRuntimeManager.onJsEvent = null }
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
        onDispose {
            TavernRuntimeManager.slashRunner = null
            StSlashExecutor.debugLogger = null
        }
    }
    val workspaceRepository: WorkspaceRepository = koinInject()
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val assistant = setting.getCurrentAssistant()
    var showFilesSheet by remember { mutableStateOf(false) }
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
                    TavernScriptButtonsRow()
                    ChatInput(
                        state = inputState,
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
                        if (currentChatModel == null) {
                            toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                            inputState.clearInput()
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                            inputState.clearInput()
                        }
                        scope.launch {
                            chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                        }
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                        }
                        inputState.clearInput()
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
                        val current = setting.macroChatVariables[chatKey]?.get("__pick_reroll_seed")?.toLongOrNull() ?: 0L
                        val next = seedArg.toLongOrNull() ?: (current + 1)
                        val (newSettings, _) = applyMacroVarSlash(
                            context = context,
                            settings = setting,
                            op = SlashVarOp.SET,
                            name = "__pick_reroll_seed",
                            value = next.toString(),
                            chatKey = chatKey,
                        )
                        if (newSettings !== setting) {
                            vm.updateSettings(newSettings)
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
                    // checkpoint 族（/checkpoint-*、/branch-create）：整条命令转交 STscript 宿主执行
                    onSlashScript = { script ->
                        StSlashExecutor.execute(
                            script,
                            buildChatSlashHost(context, vm, toaster, inputState, openConversation),
                        )
                    },
                    // 快速回复自动执行（对齐酒馆 QR executeWithOptions）：斜杠开头 → STscript 执行；纯文本 → 直接发送
                    onQuickMessageExecute = { quickMessage ->
                        val content = quickMessage.content.trim()
                        if (content.startsWith("/")) {
                            val result = StSlashExecutor.execute(
                                content,
                                buildChatSlashHost(context, vm, toaster, inputState, openConversation),
                            )
                            if (result.startsWith("未知命令") || result.startsWith("命令执行失败") ||
                                result.startsWith("命令不可用") || result.startsWith("用法：")
                            ) {
                                toaster.show(result)
                            }
                        } else if (content.isNotEmpty()) {
                            if (currentChatModel == null) {
                                toaster.show(context.getString(R.string.slash_toast_no_model), type = ToastType.Error)
                            } else {
                                vm.handleMessageSend(listOf(UIMessagePart.Text(content)))
                                scope.launch {
                                    chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                                }
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
                        val (newSettings, result) = applyMacroVarSlash(
                            context = context,
                            settings = setting,
                            op = op,
                            name = name,
                            value = value,
                            chatKey = conversation.id.toString(),
                        )
                        if (newSettings !== setting) {
                            vm.updateSettings(newSettings)
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
                    inputState.setContents(msg.parts)
                    inputState.editingMessage = msg.id
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
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onDeleteSwipe = { node ->
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else if (node.messages.size > 1) {
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    var showInjectionSheet by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showInjectionSheet = false
        showCompressDialog = false
        onDismiss()
    }

    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val (_, launchCameraCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                dismissAll()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onLaunchCamera: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    var preCropTempFile by remember { mutableStateOf<File?>(null) }
    val (_, launchImageCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            dismissAll()
        },
        onCleanup = {
            preCropTempFile?.delete()
            preCropTempFile = null
        }
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (setting.displaySetting.skipCropImage) {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    dismissAll()
                } else if (selectedUris.size == 1) {
                    val tempFile = File(context.appTempFolder, "pick_temp_${System.currentTimeMillis()}.jpg")
                    runCatching {
                        val source = selectedUris.first()
                        // HEIF/HEIC（尤其 HDR HEIF）交给 UCrop 前先解码转为 JPEG，规避裁剪解码失败
                        val converted = ImageUtils.isHeifImage(context, source) &&
                            ImageUtils.convertHeifToJpeg(context, source, tempFile)
                        if (!converted) {
                            context.contentResolver.openInputStream(source)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        preCropTempFile = tempFile
                        launchImageCrop(tempFile.toUri())
                    }.onFailure {
                        Log.e("ImagePickButton", "Failed to copy image to temp, falling back", it)
                        launchImageCrop(selectedUris.first())
                    }
                } else {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    dismissAll()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addVideos(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris))
                dismissAll()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                    val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                    if (isAllowedFileType(fileName, mime)) {
                        val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            ?: run {
                                toaster.show(
                                    context.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                                return@mapNotNull null
                            }
                        UIMessagePart.Document(url = localUri.toString(), fileName = fileName, mime = mime)
                    } else {
                        toaster.show(
                            context.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        null
                    }
                }
                if (documents.isNotEmpty()) {
                    inputState.addFiles(documents)
                    dismissAll()
                }
            }
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
            mcpManager = vm.mcpManager,
            onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
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
                        }
                    )
                )
            },
            onUpdateConversation = {
                vm.updateConversation(it)
                vm.saveConversationAsync()
            },
            onUpdateSettings = { vm.updateSettings(it) },
            showInjectionSheet = showInjectionSheet,
            onShowInjectionSheetChange = { showInjectionSheet = it },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = onLaunchCamera,
            onPickImage = { imagePickerLauncher.launch("image/*") },
            onPickVideo = { videoPickerLauncher.launch("video/*") },
            onPickAudio = { audioPickerLauncher.launch("audio/*") },
            onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
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
                        Text(
                            text = greeting,
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
