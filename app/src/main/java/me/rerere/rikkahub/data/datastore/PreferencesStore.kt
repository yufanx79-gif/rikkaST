package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV4Migration
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.parseAuthorNotePosition
import me.rerere.rikkahub.data.st.expressions.ExpressionClassifier
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.tokenizer.TokenizerMode
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration(),
            PreferenceStoreV4Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        /** [v225 F2] 消息样式（MessageStyleSetting）。v222~v224 漏读漏写 -> 值被默认值覆盖。 */
        val MESSAGE_STYLE = stringPreferencesKey(MESSAGE_STYLE_KEY_NAME)
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 模型选择
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val FAST_MODEL = stringPreferencesKey("fast_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val ENABLE_SUGGESTION = booleanPreferencesKey("enable_suggestion")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val DEFAULT_TTS_PLAYBACK_SPEED = floatPreferencesKey("default_tts_playback_speed")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_LOCALHOST_ONLY = booleanPreferencesKey("web_server_localhost_only")

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
val LOREBOOKS = stringPreferencesKey("lorebooks")
        // ST 正则脚本（全字段对齐酒馆 RegexScriptData，可直接导入/导出 ST JSON）
        val REGEX_SCRIPTS = stringPreferencesKey("regex_scripts")
        val WORLD_INFO_BUDGET = intPreferencesKey("world_info_budget")
        val WORLD_INFO_BUDGET_CAP = intPreferencesKey("world_info_budget_cap")
        val WORLD_INFO_MIN_ACTIVATIONS = intPreferencesKey("world_info_min_activations")
        val WORLD_INFO_MIN_ACTIVATIONS_DEPTH_MAX = intPreferencesKey("world_info_min_activations_depth_max")
        val WORLD_INFO_RECURSIVE = booleanPreferencesKey("world_info_recursive")
        val WORLD_INFO_MAX_RECURSION_STEPS = intPreferencesKey("world_info_max_recursion_steps")
        val WORLD_INFO_DEPTH = intPreferencesKey("world_info_depth")
        val WORLD_INFO_CHARACTER_STRATEGY = intPreferencesKey("world_info_character_strategy")
        val WORLD_INFO_OVERFLOW_ALERT = booleanPreferencesKey("world_info_overflow_alert")
        val WORLD_INFO_USE_GROUP_SCORING = booleanPreferencesKey("world_info_use_group_scoring")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")
        // 宏引擎变量（酒馆 Macro 2.0 变量持久化）
        val MACRO_GLOBAL_VARIABLES = stringPreferencesKey("macro_global_variables")
        val MACRO_CHAT_VARIABLES = stringPreferencesKey("macro_chat_variables")
        // DeepSeek 前缀缓存优化（动态上下文移至请求末尾，最大化缓存命中）
        val DEEPSEEK_CACHE_OPTIMIZATION = booleanPreferencesKey("deepseek_cache_optimization")
        // EJS 提示词模板渲染（ST-Prompt-Template 语义）
        val TAVERN_EJS_RENDERING = booleanPreferencesKey("tavern_ejs_rendering")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // GitHub
        val GITHUB_TOKEN = stringPreferencesKey("github_token")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // 赞助提醒
        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")

        // Custom HTTP API

        // 人设 & 导演备注（补全）
        val PERSONAS = stringPreferencesKey("personas")
        val ACTIVE_PERSONA_ID = stringPreferencesKey("active_persona_id")
        val AUTHOR_NOTE = stringPreferencesKey("author_note")
        val AUTHOR_NOTE_ENABLED = booleanPreferencesKey("author_note_enabled")
        val AUTHOR_NOTE_POSITION = stringPreferencesKey("author_note_position")
        val AUTHOR_NOTE_DEPTH = intPreferencesKey("author_note_depth")
        val AUTHOR_NOTE_ROLE = stringPreferencesKey("author_note_role")
        val AUTHOR_NOTE_INTERVAL = intPreferencesKey("author_note_interval")
        val GROUP_CHATS = stringPreferencesKey("group_chats")

        // [v240 W3] Character Expressions（立绘）：总开关 / 分类方式 / 兜底标签
        val EXPRESSION_ENABLED = booleanPreferencesKey("expression_enabled")
        val EXPRESSION_CLASSIFIER = stringPreferencesKey("expression_classifier")
        val EXPRESSION_FALLBACK_LABEL = stringPreferencesKey("expression_fallback_label")

        // [v240 W5] 真 tokenizer 模式（cl100k / o200k / off；世界书预算 + 上下文估算）
        val TOKENIZER_MODE = stringPreferencesKey("tokenizer_mode")
    }

    private val dataStore = context.settingsStore


    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            Settings(
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                fastModelId = preferences[FAST_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) },
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                enableSuggestion = preferences[ENABLE_SUGGESTION] != false,
                deepseekCacheOptimization = preferences[DEEPSEEK_CACHE_OPTIMIZATION] ?: true,
                tavernEjsRendering = preferences[TAVERN_EJS_RENDERING] ?: true,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) },
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_AUTO_MODEL_ID,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                messageStyle = decodeMessageStyle(preferences[MESSAGE_STYLE]),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                defaultTTSPlaybackSpeed = preferences[DEFAULT_TTS_PLAYBACK_SPEED]?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) },
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                regexScripts = preferences[REGEX_SCRIPTS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                // 官方 world_info_budget：世界书预算 = 上下文 token 的百分比（0-100，官方默认 25）
                worldInfoBudget = preferences[WORLD_INFO_BUDGET]?.coerceIn(0, 100) ?: 25,
                worldInfoBudgetCap = preferences[WORLD_INFO_BUDGET_CAP] ?: 0,
                worldInfoMinActivations = preferences[WORLD_INFO_MIN_ACTIVATIONS] ?: 0,
                worldInfoMinActivationsDepthMax = preferences[WORLD_INFO_MIN_ACTIVATIONS_DEPTH_MAX] ?: 0,
                worldInfoRecursive = preferences[WORLD_INFO_RECURSIVE] ?: false,
                worldInfoMaxRecursionSteps = preferences[WORLD_INFO_MAX_RECURSION_STEPS] ?: 0,
                worldInfoDepth = preferences[WORLD_INFO_DEPTH] ?: 2,
                worldInfoCharacterStrategy = preferences[WORLD_INFO_CHARACTER_STRATEGY] ?: 1,
                worldInfoOverflowAlert = preferences[WORLD_INFO_OVERFLOW_ALERT] ?: false,
                worldInfoUseGroupScoring = preferences[WORLD_INFO_USE_GROUP_SCORING] ?: false,
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true,
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: BackupReminderConfig(),
                githubToken = preferences[GITHUB_TOKEN] ?: "",
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
                personas = preferences[PERSONAS]?.let { JsonInstant.decodeFromString(it) } ?: DEFAULT_PERSONAS,
                activePersonaId = preferences[ACTIVE_PERSONA_ID]?.let { Uuid.parse(it) },
                authorNote = preferences[AUTHOR_NOTE] ?: "",
                authorNoteEnabled = preferences[AUTHOR_NOTE_ENABLED] ?: false,
                authorNotePosition = parseAuthorNotePosition(preferences[AUTHOR_NOTE_POSITION]),
                authorNoteDepth = preferences[AUTHOR_NOTE_DEPTH] ?: 4,
                authorNoteRole = preferences[AUTHOR_NOTE_ROLE]?.let { MessageRole.valueOf(it) } ?: MessageRole.SYSTEM,
                authorNoteInterval = preferences[AUTHOR_NOTE_INTERVAL] ?: 1,
                // [v240 W3] 立绘：默认关闭；分类方式默认 llm（rikkaST 天然客户端模型）；兜底默认 joy
                expressionEnabled = preferences[EXPRESSION_ENABLED] ?: false,
                expressionClassifier = ExpressionClassifier.parse(preferences[EXPRESSION_CLASSIFIER]),
                expressionFallbackLabel = preferences[EXPRESSION_FALLBACK_LABEL]
                    ?.takeIf { it.isNotBlank() } ?: ExpressionLabels.DEFAULT_FALLBACK,
                // [v240 W5] 词表缺失/关闭时自动回退启发式估算
                tokenizerMode = TokenizerMode.parse(preferences[TOKENIZER_MODE]),
                groupChats = preferences[GROUP_CHATS]?.let { JsonInstant.decodeFromString(it) } ?: emptyList(),
                macroGlobalVariables = preferences[MACRO_GLOBAL_VARIABLES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyMap(),
                macroChatVariables = preferences[MACRO_CHAT_VARIABLES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyMap(),
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders,
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                regexScripts = settings.regexScripts.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
            )
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    // [v222 R2-P0] 「设置开关卡顿」的首要根因（jank-recon 实证，notes/recon-switch-jank-20261004.md §2）：
    // `dataStore.data` 的 collector 跑在 Main（AppScope = SupervisorJob() + Dispatchers.Main），
    // 上面这条链里有 ~30 个 JsonInstant.decodeFromString + 3 段 map + distinctUntilChanged 深比较，
    // 每次写回都在主线程跑完 → 动画期间掉帧（thumb 走「左 中 右」）。
    // 修法：把上游（解码/比较）整体挪到 Default，**不动任何动画时长**。
    //
    /**
     * [v229 A4/P0-2] DataStore **旧回声**门控 =「本地权威值 + 落盘确认」。
     *
     * 真机根因（v227/v228 两轮「松手后自己滑 / 开关弹回」都落在这里）：
     *   `settingsFlow.value = next`（乐观写）之后，`dataStore.data` 仍会把**更早那笔写的旧快照**
     *   解码后推给同一个 `settingsFlow` —— 上一笔的发射晚于这一笔的本地写到达，UI 刚设的值被拉回；
     *   滑块连续拖动时表现为「松手后数值自己慢慢挪」。
     *
     * 结构（对齐 Kelivo 的「内存权威 + 落盘只写不读回」，但不改冷启动语义）：
     *   1. 每笔本地写在 [settingsFlow] 生效的同时，把该值记给 [SettingsEchoGate]（权威值）；
     *   2. collector 只在「上游发射 == 权威值」(= 我们这笔已落盘并被确认) 或「无待确认写」时才回灌；
     *   3. 其余发射一律判定为**旧回声**并丢弃（[SettingsEchoGate.droppedCount] 计数供真机取证）；
     *   4. 写入口唯一（本类 update/patchKey 是 DataStore 的**唯一写者**，已核查）-> 待确认值必然被
     *      自己那笔写的发射确认，不会永久卡住；没有待确认写时（冷启动/外部导入）照常回灌。
     */

    /** [v229 A4] 上游回灌闸门（纯逻辑在 [SettingsEchoGate]，可单测）。 */
    private val echoGate = SettingsEchoGate()

    /**
     * 本地写入口（`update()` 与 `patchKey()` 唯一共用）：先立权威值，再改内存 flow。
     * 顺序不能反 —— 必须先让 gate 记下权威值，否则 collector 可能先看到本笔的 echo 而误判。
     */
    private fun markLocalWrite(next: Settings) {
        echoGate.mark(next)
        settingsFlow.value = next
    }
    val settingsFlow = MutableStateFlow(Settings.dummy())

    init {
        scope.launch {
            settingsFlowRaw
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .catch { t -> Log.e(TAG, "settingsFlow collect failed: ${t.message}", t) }
                .collect { incoming ->
                    if (echoGate.accept(incoming)) {
                        settingsFlow.value = incoming
                    } else {
                        val n = echoGate.droppedCount()
                        if (n <= 3L || n % 20L == 0L) {
                            Log.d(TAG, "[a4] dropped stale DataStore echo #$n (pending local write)")
                        }
                    }
                }
        }
    }

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        val previous = settingsFlow.value
        markLocalWrite(settings)
        // [v227 N1] ST 宿主事件 SETTINGS_UPDATED（ST events.js:30；真源在 script.js:8110 发，无载荷）。
        // 差分门控：只有真的变了才发 —— 无脑发会让扩展在每次无关写入时重跑一遍设置迁移
        // （ST-Prompt-Template 与 power-user.js:2713 都用 once(SETTINGS_UPDATED) 等它）。
        // 单点发射：SettingsStore.update 是唯一全量设置写入口。
        // [v228 S2] 差分门控抽成公共函数（settingsEventsFor）：全量 update() 与窄写回 patchKey() 共用一处判据。
        fireSettingsEvents(previous, settings)
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settings.dynamicColor
            preferences[THEME_ID] = settings.themeId
            preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
            preferences[DEVELOPER_MODE] = settings.developerMode
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)
            preferences[MESSAGE_STYLE] = encodeMessageStyle(settings.messageStyle)

            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
            preferences[SELECT_MODEL] = settings.chatModelId.toString()
            preferences[FAST_MODEL] = settings.fastModelId.toString()
            settings.titleModelId?.let {
                preferences[TITLE_MODEL] = it.toString()
            } ?: preferences.remove(TITLE_MODEL)
            preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
            preferences[ENABLE_SUGGESTION] = settings.enableSuggestion
            preferences[DEEPSEEK_CACHE_OPTIMIZATION] = settings.deepseekCacheOptimization
            preferences[TAVERN_EJS_RENDERING] = settings.tavernEjsRendering
            settings.suggestionModelId?.let {
                preferences[SUGGESTION_MODEL] = it.toString()
            } ?: preferences.remove(SUGGESTION_MODEL)
            preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settings.titlePrompt
            preferences[TRANSLATION_PROMPT] = settings.translatePrompt
            preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
            preferences[OCR_MODEL] = settings.ocrModelId.toString()
            preferences[OCR_PROMPT] = settings.ocrPrompt
            preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
            preferences[COMPRESS_PROMPT] = settings.compressPrompt

            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[DEFAULT_TTS_PLAYBACK_SPEED] = settings.defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f)
            preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
            settings.selectedASRProviderId?.let {
                preferences[SELECTED_ASR_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_ASR_PROVIDER)
            preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
            preferences[REGEX_SCRIPTS] = JsonInstant.encodeToString(settings.regexScripts)
            preferences[WORLD_INFO_BUDGET] = settings.worldInfoBudget
            preferences[WORLD_INFO_BUDGET_CAP] = settings.worldInfoBudgetCap
            preferences[WORLD_INFO_MIN_ACTIVATIONS] = settings.worldInfoMinActivations
            preferences[WORLD_INFO_MIN_ACTIVATIONS_DEPTH_MAX] = settings.worldInfoMinActivationsDepthMax
            preferences[WORLD_INFO_RECURSIVE] = settings.worldInfoRecursive
            preferences[WORLD_INFO_MAX_RECURSION_STEPS] = settings.worldInfoMaxRecursionSteps
            preferences[WORLD_INFO_DEPTH] = settings.worldInfoDepth
            preferences[WORLD_INFO_CHARACTER_STRATEGY] = settings.worldInfoCharacterStrategy
            preferences[WORLD_INFO_OVERFLOW_ALERT] = settings.worldInfoOverflowAlert
            preferences[WORLD_INFO_USE_GROUP_SCORING] = settings.worldInfoUseGroupScoring
            preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
            preferences[WEB_SERVER_ENABLED] = settings.webServerEnabled
            preferences[WEB_SERVER_PORT] = settings.webServerPort
            preferences[WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
            preferences[WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
            preferences[WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
            preferences[GITHUB_TOKEN] = settings.githubToken
            preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
            preferences[LAUNCH_COUNT] = settings.launchCount
            preferences[SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
            preferences[PERSONAS] = JsonInstant.encodeToString(settings.personas)
            settings.activePersonaId?.let { preferences[ACTIVE_PERSONA_ID] = it.toString() }
                ?: preferences.remove(ACTIVE_PERSONA_ID)
            preferences[AUTHOR_NOTE] = settings.authorNote
            preferences[AUTHOR_NOTE_ENABLED] = settings.authorNoteEnabled
            preferences[AUTHOR_NOTE_POSITION] = settings.authorNotePosition.name
            preferences[AUTHOR_NOTE_DEPTH] = settings.authorNoteDepth
            preferences[AUTHOR_NOTE_ROLE] = settings.authorNoteRole.name
            preferences[AUTHOR_NOTE_INTERVAL] = settings.authorNoteInterval
            preferences[EXPRESSION_ENABLED] = settings.expressionEnabled
            preferences[EXPRESSION_CLASSIFIER] = settings.expressionClassifier.name.lowercase()
            preferences[EXPRESSION_FALLBACK_LABEL] = settings.expressionFallbackLabel
            preferences[TOKENIZER_MODE] = when (settings.tokenizerMode) {
                TokenizerMode.OFF -> "off"
                TokenizerMode.CL100K -> "cl100k"
                TokenizerMode.O200K -> "o200k"
            }
            preferences[GROUP_CHATS] = JsonInstant.encodeToString(settings.groupChats)
            preferences[MACRO_GLOBAL_VARIABLES] = JsonInstant.encodeToString(settings.macroGlobalVariables)
            preferences[MACRO_CHAT_VARIABLES] = JsonInstant.encodeToString(settings.macroChatVariables)
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    /**
     * [v227 D4] 窄写回：只改一个 key，且把变换作用在 settingsFlow 的**当前**值上。
     *
     * ## 为什么需要它（v226 真机反馈「开关不丝滑 / 开关卡动」的结构性来源）
     * 设置页旧路径是 `vm.updateSettings(settings.copy(displaySetting = ...))`：
     * 1. `settings` 是**组合期捕获**的值 —— 连点两个开关时第二次点击用的还是旧快照，
     *    会把第一次的改动**回滚**（真机表现：开关弹回去 / 跳一下）；
     * 2. `update()` 会把 ~80 个 key 全部重写一遍（含多次 JSON 编码 + 整文件重写），
     *    每次点击都在 IO 上制造一大坨无谓写盘，并让 DataStore 再发一次全量事件。
     *
     * 窄写回：① 变换基于 `settingsFlow.value`（当前值，CAS 语义，不丢并发改动）
     * ② 只写这一个 key（写盘量 80 -> 1）。UI 侧仍是乐观更新（先改内存 flow，再落盘）。
     */
    private suspend fun patchKey(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        transform: (Settings) -> Settings,
        encode: (Settings) -> String,
    ): Boolean {
        val current = settingsFlow.value
        if (current.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return false
        }
        val next = transform(current)
        if (next == current) return false
        markLocalWrite(next)
        settingsFlow.value = next
        // [v228 S2] 窄写回同样要发事件（v227 N1 只覆盖了全量 update() -> patchKey 是漏口）。
        // 差分门控与 update() 完全同一处判据：调用 settingsEventsFor(previous, next)。
        fireSettingsEvents(current, next)
        dataStore.edit { preferences ->
            preferences[key] = encode(next)
        }
        return true
    }

    /** [v227 D4] 只改 [DisplaySetting]（1 个 key）。 */
    suspend fun patchDisplaySetting(transform: (DisplaySetting) -> DisplaySetting): Boolean =
        patchKey(
            key = DISPLAY_SETTING,
            transform = { it.copy(displaySetting = transform(it.displaySetting)) },
            encode = { JsonInstant.encodeToString(it.displaySetting) },
        )

    /** [v227 D4] 只改 [MessageStyleSetting]（1 个 key）。 */
    suspend fun patchMessageStyle(transform: (MessageStyleSetting) -> MessageStyleSetting): Boolean =
        patchKey(
            key = MESSAGE_STYLE,
            transform = { it.copy(messageStyle = transform(it.messageStyle)) },
            encode = { encodeMessageStyle(it.messageStyle) },
        )

    /**
     * [v228 S2] 设置变更 -> ST 事件的唯一发射点（`update()` 全量写回与 `patchKey()` 窄写回共用）。
     *
     * 只做两件事：① 取判据（[settingsEventsFor]，纯函数，可单测）；② 逐个交给
     * [TavernRuntimeManager.fireEvent]（Kotlin -> JS，developerMode 下写 `[event]` 日志）。
     *
     * ⚠️ 发射点在 **`settingsFlow.value` 已更新之后**：监听方从 `settings_updated` 回调里读设置时
     * 必须拿到新值（v227 注释里的老规矩）。差分用的 `previous` 由调用方在更新前抓好。
     */
    private fun fireSettingsEvents(previous: Settings, next: Settings) {
        for (name in settingsEventsFor(previous, next)) {
            TavernRuntimeManager.fireEvent(name)
        }
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(enableWebSearch = enabled)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val enableWebSearch: Boolean = true, // 全局网络搜索开关
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    // [v222 R5] 消息样式（全局唯一；气泡 + 输入栏，A5）。R1-(2) 的模糊强度模块与
    // 本页 blurStrength 是同一份数据。
    val messageStyle: MessageStyleSetting = MessageStyleSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid? = null,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val enableSuggestion: Boolean = true,
    val suggestionModelId: Uuid? = null,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val embeddingModelId: Uuid? = null, // 全局embedding模型（null=使用chatModelId）
    val kbInjectionSettings: KbInjectionSettings = KbInjectionSettings(),
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val regexScripts: List<RegexScript> = emptyList(),   // ST 正则脚本（酒馆导出 JSON 兼容）
    // B6: JSR 酒馆助手脚本源（global / preset；character 来自角色卡 extensionsRaw，无需存储）
    val tavernGlobalScripts: String = "[]", // 全局脚本（JSON 数组字符串，无损透传）
    val tavernGlobalScriptsEnabled: Boolean = true, // 全局脚本总开关（对齐 JSR script.enabled.global）
    val tavernPresetScripts: String = "{}", // 预设脚本（JSON 对象：{"预设组名": [script, ...]}）
    val tavernThirdPartyDisabled: Set<String> = emptySet(), // 被禁用的第三方扩展（文件夹名集合）
    val tavernExtensionSettings: String = "{}", // 第三方扩展设置（extension_settings 非 variables 部分，JSON 字符串）
    val worldInfoBudget: Int = 25,                  // 官方 world_info_budget：世界书预算 = 上下文 token 的百分比（官方默认 25%）
    val worldInfoBudgetCap: Int = 0,                // 官方 world_info_budget_cap：预算绝对 token 上限（0=不限制，官方默认 0）
    val worldInfoMinActivations: Int = 0,           // 世界书最少激活数（0=关闭，酒馆 min_activations）
    val worldInfoMinActivationsDepthMax: Int = 0,   // 官方 world_info_min_activations_depth_max：min_activations 最大扫描深度（0=不限制）
    val worldInfoRecursive: Boolean = false,        // 递归扫描（酒馆 world_info_recursive）
    val worldInfoMaxRecursionSteps: Int = 0,            // 官方 world_info_max_recursion_steps：总扫描轮数上限（0=不限制，官方默认0）
    val worldInfoDepth: Int = 2,                    // 官方 world_info_depth：条目未设置扫描深度时的默认值（官方默认2）
    val worldInfoCharacterStrategy: Int = 1,        // 官方 world_info_character_strategy：0=均匀 1=角色卡优先 2=全局优先
    val worldInfoOverflowAlert: Boolean = false,    // 官方 world_info_overflow_alert：预算溢出时提示
    val worldInfoUseGroupScoring: Boolean = false,  // 官方 world_info_use_group_scoring：组评分全局默认        // 递归最大层数（0=不限制，酒馆 max_recursion_steps）
    val deepseekCacheOptimization: Boolean = true,  // DeepSeek 缓存优化：动态上下文（日期/记忆）移至请求末尾，最大化前缀缓存命中
    val tavernEjsRendering: Boolean = true,         // EJS 提示词模板渲染（ST-Prompt-Template）：发送前对 <% %> 模板执行渲染
    val quickMessages: List<QuickMessage> = emptyList(),
    val personas: List<Persona> = DEFAULT_PERSONAS,
    val activePersonaId: Uuid? = null,             // 当前激活的 Persona
    val authorNote: String = "",                    // Author's Note 内容
    val authorNoteEnabled: Boolean = false,         // Author's Note 总开关
    val authorNotePosition: AuthorNotePosition = AuthorNotePosition.IN_CHAT,
    val authorNoteDepth: Int = 4,                   // Author's Note 插入深度
    val authorNoteRole: MessageRole = MessageRole.SYSTEM, // 注入角色（官方默认 SYSTEM）
    val authorNoteInterval: Int = 1,                // 官方语义：1=每次注入，0=关闭，N=每N条用户消息注入一次
    // [v240 W3] Character Expressions（表情立绘）：默认关闭（设置页打开后，给角色放好立绘才会显示）
    val expressionEnabled: Boolean = false,                                  // 立绘总开关
    val expressionClassifier: ExpressionClassifier = ExpressionClassifier.LLM, // 分类方式：llm / none
    val expressionFallbackLabel: String = ExpressionLabels.DEFAULT_FALLBACK, // 分类失败/无立绘时的兜底标签（官方默认 joy）
    // [v240 W5] 世界书预算 / 上下文估算的真 tokenizer（默认 cl100k；off 或词表缺失回退启发式）
    val tokenizerMode: TokenizerMode = TokenizerMode.CL100K,
    val groupChats: List<GroupChat> = emptyList(),   // 群聊列表
    val macroGlobalVariables: Map<String, String> = emptyMap(),        // 宏引擎全局变量（跨对话持久）
    val macroChatVariables: Map<String, Map<String, String>> = emptyMap(), // 宏引擎会话变量（conversationId → 变量）
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val githubToken: String = "",
    val launchCount: Int = 0,
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = true,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = false,
    // [v222 R1] 兼容旧值：仅作「迁移读取 + 回滚镜像」用，UI 的真源是
    // Settings.messageStyle.style（ON <-> style != DEFAULT）。默认 true 对齐 A6「气泡模糊默认开」。
    val enableBlurEffect: Boolean = true,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val enableTextColor: Boolean = true,
    val quoteColor: String = "",  // empty = theme-follow, otherwise hex like "#E18A24"
    val italicsColor: String = "",  // empty = default (#919191), otherwise hex
    val autoEmbedOnImport: Boolean = true,  // 导入后自动向量化
    val embeddingEnabled: Boolean = true,   // 向量搜索总开关（关则仅使用FTS5文本搜索）
)

@Serializable
data class KbInjectionSettings(
    val enabled: Boolean = true,
    val chunkCount: Int = 3,
    val tokenBudget: Int = 2048,
    val scoreThreshold: Float = 0.25f,
    val useHybridSearch: Boolean = true,
    val useQueryRewrite: Boolean = true,
    val enableDedup: Boolean = true,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    return selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "",
        systemPrompt = """
            You are a helpful assistant, called {{char}}, based on model {{model_name}}.

            ## Info
            - Date: {{cur_date}}
            - Locale: {{locale}}
            - Timezone: {{timezone}}
            - Device Info: {{device_info}}
            - System Version: {{system_version}}
            - User Nickname: {{user}}

            ## Hint
            - If the user does not specify a language, reply in the user's primary language.
            - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
        """.trimIndent()
    ),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "AiHubMix",
        baseUrl = "https://aihubmix.com/v1",
        model = "gpt-4o-mini-tts",
        voice = "alloy",
    )
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)

val DEFAULT_PERSONAS = listOf(
    Persona(
        id = Uuid.parse("c0010000-0000-0000-0000-000000000001"),
        name = "Default",
        description = "",
        enabled = false,
    )
)
