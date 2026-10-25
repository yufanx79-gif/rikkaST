package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.reflect.KProperty1
import kotlin.uuid.Uuid

/**
 * [Settings] 的一个字段和它在 DataStore 里的 key 的对应关系。
 *
 * 读、写、判断有没有变化都从这里走，新增字段只要在 [SETTINGS_FIELDS] 里加一行。
 */
internal class SettingsField<T, P : Any>(
    val key: Preferences.Key<P>,
    val property: KProperty1<Settings, T>,
    private val set: Settings.(T) -> Settings,
    private val encode: (T) -> P?,
    private val decode: (P) -> T,
) {
    fun changed(old: Settings, new: Settings): Boolean = property.get(old) != property.get(new)

    /** key 不存在时保留 [settings] 里原有的值。 */
    fun read(preferences: Preferences, settings: Settings): Settings {
        val stored = preferences[key] ?: return settings
        return runCatching { settings.set(decode(stored)) }.getOrDefault(settings)
    }

    /** 编码结果为 null 时删除 key。 */
    fun write(preferences: MutablePreferences, settings: Settings) {
        val encoded = encode(property.get(settings))
        if (encoded != null) preferences[key] = encoded else preferences.remove(key)
    }
}

/** 原样存取的基本类型。 */
private fun <T : Any> plain(
    key: Preferences.Key<T>,
    property: KProperty1<Settings, T>,
    set: Settings.(T) -> Settings,
) = SettingsField(key, property, set, encode = { it }, decode = { it })

/** 存成 JSON 字符串。 */
private inline fun <reified T> json(
    key: Preferences.Key<String>,
    property: KProperty1<Settings, T>,
    noinline set: Settings.(T) -> Settings,
) = SettingsField(
    key, property, set,
    encode = { JsonInstant.encodeToString(it) },
    decode = { JsonInstant.decodeFromString<T>(it) },
)

private fun uuid(
    key: Preferences.Key<String>,
    property: KProperty1<Settings, Uuid>,
    set: Settings.(Uuid) -> Settings,
) = SettingsField(key, property, set, encode = { it.toString() }, decode = { Uuid.parse(it) })

private fun nullableUuid(
    key: Preferences.Key<String>,
    property: KProperty1<Settings, Uuid?>,
    set: Settings.(Uuid?) -> Settings,
) = SettingsField(
    key, property, set,
    encode = { it?.toString() },
    decode = { runCatching { Uuid.parse(it) }.getOrNull() },
)

internal val SETTINGS_FIELDS: List<SettingsField<*, *>> = with(SettingsStore) {
    listOf(
        // UI设置
        plain(DYNAMIC_COLOR, Settings::dynamicColor) { copy(dynamicColor = it) },
        plain(ENABLE_WEB_SEARCH, Settings::enableWebSearch) { copy(enableWebSearch = it) },
        plain(THEME_ID, Settings::themeId) { copy(themeId = it) },
        json(CUSTOM_THEMES, Settings::customThemes) { copy(customThemes = it) },
        json(DISPLAY_SETTING, Settings::displaySetting) { copy(displaySetting = it) },
        SettingsField(
            MESSAGE_STYLE, Settings::messageStyle, { copy(messageStyle = it) },
            encode = { encodeMessageStyle(it) },
            decode = { decodeMessageStyle(it) },
        ),
        json(NETWORK_SETTING, Settings::networkSetting) { copy(networkSetting = it) },
        plain(DEVELOPER_MODE, Settings::developerMode) { copy(developerMode = it) },

        // 模型选择
        json(FAVORITE_MODELS, Settings::favoriteModels) { copy(favoriteModels = it) },
        uuid(SELECT_MODEL, Settings::chatModelId) { copy(chatModelId = it) },
        uuid(FAST_MODEL, Settings::fastModelId) { copy(fastModelId = it) },
        SettingsField(
            FAST_MODEL_REASONING_LEVEL, Settings::fastModelReasoningLevel, { copy(fastModelReasoningLevel = it) },
            encode = { it.name },
            decode = { name -> ReasoningLevel.entries.find { it.name == name } ?: ReasoningLevel.AUTO },
        ),
        nullableUuid(TITLE_MODEL, Settings::titleModelId) { copy(titleModelId = it) },
        uuid(TRANSLATE_MODEL, Settings::translateModeId) { copy(translateModeId = it) },
        plain(ENABLE_SUGGESTION, Settings::enableSuggestion) { copy(enableSuggestion = it) },
        nullableUuid(SUGGESTION_MODEL, Settings::suggestionModelId) { copy(suggestionModelId = it) },
        uuid(IMAGE_GENERATION_MODEL, Settings::imageGenerationModelId) { copy(imageGenerationModelId = it) },
        plain(TITLE_PROMPT, Settings::titlePrompt) { copy(titlePrompt = it) },
        plain(TRANSLATION_PROMPT, Settings::translatePrompt) { copy(translatePrompt = it) },
        plain(TRANSLATE_THINKING_BUDGET, Settings::translateThinkingBudget) { copy(translateThinkingBudget = it) },
        plain(TRANSLATE_TARGET_LANGUAGE, Settings::translateTargetLanguage) { copy(translateTargetLanguage = it) },
        plain(SUGGESTION_PROMPT, Settings::suggestionPrompt) { copy(suggestionPrompt = it) },
        uuid(OCR_MODEL, Settings::ocrModelId) { copy(ocrModelId = it) },
        plain(OCR_PROMPT, Settings::ocrPrompt) { copy(ocrPrompt = it) },
        uuid(COMPRESS_MODEL, Settings::compressModelId) { copy(compressModelId = it) },
        plain(COMPRESS_PROMPT, Settings::compressPrompt) { copy(compressPrompt = it) },
        nullableUuid(EMBEDDING_MODEL, Settings::embeddingModelId) { copy(embeddingModelId = it) },
        json(KB_INJECTION_SETTINGS, Settings::kbInjectionSettings) { copy(kbInjectionSettings = it) },

        // 提供商（使用逐条容错解码，单条损坏绝不抹空整表）
        SettingsField(
            PROVIDERS, Settings::providers, { copy(providers = it) },
            encode = { JsonInstant.encodeToString(it) },
            decode = { decodeProvidersResilient(it) },
        ),

        // 助手
        uuid(SELECT_ASSISTANT, Settings::assistantId) { copy(assistantId = it) },
        json(ASSISTANTS, Settings::assistants) { copy(assistants = it) },
        json(ASSISTANT_TAGS, Settings::assistantTags) { copy(assistantTags = it) },

        // 搜索
        json(SEARCH_SERVICES, Settings::searchServices) { copy(searchServices = it) },
        json(SEARCH_COMMON, Settings::searchCommonOptions) { copy(searchCommonOptions = it) },
        plain(SEARCH_SELECTED, Settings::searchServiceSelected) { copy(searchServiceSelected = it) },

        // MCP
        json(MCP_SERVERS, Settings::mcpServers) { copy(mcpServers = it) },

        // WebDAV
        json(WEBDAV_CONFIG, Settings::webDavConfig) { copy(webDavConfig = it) },

        // S3
        json(S3_CONFIG, Settings::s3Config) { copy(s3Config = it) },
        json(UPLOAD_S3_CONFIG, Settings::uploadS3Config) { copy(uploadS3Config = it) },

        // 媒体生成
        json(MEDIA_GENERATION_PROVIDERS, Settings::mediaGenerationProviders) { copy(mediaGenerationProviders = it) },

        // TTS
        json(TTS_PROVIDERS, Settings::ttsProviders) { copy(ttsProviders = it) },
        uuid(SELECTED_TTS_PROVIDER, Settings::selectedTTSProviderId) { copy(selectedTTSProviderId = it) },
        plain(DEFAULT_TTS_PLAYBACK_SPEED, Settings::defaultTTSPlaybackSpeed) { copy(defaultTTSPlaybackSpeed = it) },

        // ASR
        json(ASR_PROVIDERS, Settings::asrProviders) { copy(asrProviders = it) },
        nullableUuid(SELECTED_ASR_PROVIDER, Settings::selectedASRProviderId) { copy(selectedASRProviderId = it) },

        // Web Server
        plain(WEB_SERVER_ENABLED, Settings::webServerEnabled) { copy(webServerEnabled = it) },
        plain(WEB_SERVER_PORT, Settings::webServerPort) { copy(webServerPort = it) },
        plain(WEB_SERVER_JWT_ENABLED, Settings::webServerJwtEnabled) { copy(webServerJwtEnabled = it) },
        plain(WEB_SERVER_ACCESS_PASSWORD, Settings::webServerAccessPassword) { copy(webServerAccessPassword = it) },
        plain(WEB_SERVER_LOCALHOST_ONLY, Settings::webServerLocalhostOnly) { copy(webServerLocalhostOnly = it) },

        // 提示词注入 & 酒馆配置
        json(MODE_INJECTIONS, Settings::modeInjections) { copy(modeInjections = it) },
        json(PROMPT_INJECTIONS, Settings::promptInjections) { copy(promptInjections = it) },
        plain(TAVERN_PRESETS, Settings::tavernPresets) { copy(tavernPresets = it) },
        plain(TAVERN_GLOBAL_SCRIPTS, Settings::tavernGlobalScripts) { copy(tavernGlobalScripts = it) },
        plain(TAVERN_GLOBAL_SCRIPTS_ENABLED, Settings::tavernGlobalScriptsEnabled) { copy(tavernGlobalScriptsEnabled = it) },
        plain(TAVERN_PRESET_SCRIPTS, Settings::tavernPresetScripts) { copy(tavernPresetScripts = it) },
        json(TAVERN_THIRD_PARTY_DISABLED, Settings::tavernThirdPartyDisabled) { copy(tavernThirdPartyDisabled = it) },
        plain(TAVERN_EXTENSION_SETTINGS, Settings::tavernExtensionSettings) { copy(tavernExtensionSettings = it) },
        json(LOREBOOKS, Settings::lorebooks) { copy(lorebooks = it) },
        json(REGEX_SCRIPTS, Settings::regexScripts) { copy(regexScripts = it) },
        json(QUICK_MESSAGES, Settings::quickMessages) { copy(quickMessages = it) },

        // 备份提醒
        json(BACKUP_REMINDER_CONFIG, Settings::backupReminderConfig) { copy(backupReminderConfig = it) },

        // 赞助提醒
        plain(SPONSOR_ALERT_DISMISSED_AT, Settings::sponsorAlertDismissedAt) { copy(sponsorAlertDismissedAt = it) },
    )
}

private val MISSING_KEY_DEFAULTS = Settings(
    chatModelId = DEFAULT_AUTO_MODEL_ID,
    fastModelId = DEFAULT_AUTO_MODEL_ID,
    translateModeId = DEFAULT_AUTO_MODEL_ID,
)

/** 解码出的是盘上的原始值，还没有经过 [normalized]。 */
internal fun Preferences.toSettings(): Settings =
    SETTINGS_FIELDS.fold(MISSING_KEY_DEFAULTS) { settings, field -> field.read(this, settings) }

/**
 * 把 [settings] 写进 Preferences。
 *
 * 给了 [persisted]（盘上现有的那一份）时只写和它不同的字段：整份设置的 JSON 编码很重，没变的字段不用重新编码。
 */
internal fun MutablePreferences.putSettings(settings: Settings, persisted: Settings? = null) {
    SETTINGS_FIELDS.forEach { field ->
        if (persisted == null || field.changed(persisted, settings)) {
            field.write(this, settings)
        }
    }
}
