package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsFieldsTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every settings property has at most one field`() {
        // SETTINGS_FIELDS 目前只覆盖"能用统一编解码处理"的一部分字段；其余字段仍由
        // PreferencesStore.persistSettings 手写读写（历史原因）。这里守的是"不漏、不重"：
        // 任何字段最多出现一次，出现两次会导致读回时后写的覆盖先写的。
        val covered = SETTINGS_FIELDS.map { it.property.name }
        assertEquals("SETTINGS_FIELDS 里有重复字段", covered.size, covered.distinct().size)

        val allProperties = Settings.serializer().descriptor.elementNames.toSet()
        val unknown = covered.filterNot { it in allProperties }
        assertEquals("SETTINGS_FIELDS 里有 Settings 不存在的字段", emptyList<String>(), unknown)
    }

    @Test
    fun `settings survive a round trip through preferences`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        val restored = preferences.toSettings()

        // SAMPLE 里刻意设成非默认值的字段，往返后必须一模一样。
        // 和默认值相同的字段测不出读写是否对得上，跳过它们（SETTINGS_FIELDS 里有些字段
        // 还没在 SAMPLE 里给到非默认值，那是样本没覆盖，不是读写坏了）。
        val defaults = emptyPreferences().toSettings()
        val probed = SETTINGS_FIELDS.filter { it.changed(defaults, SAMPLE) }
        assertEquals(
            "SETTINGS_FIELDS 里没有一个字段取到非默认值，这个测试就白跑了",
            true,
            probed.isNotEmpty(),
        )
        probed.forEach { field ->
            assertEquals(
                "${field.property.name} 往返后不一致",
                field.property.get(SAMPLE),
                field.property.get(restored),
            )
        }
    }

    @Test
    fun `upstream backup fields are written and read back`() {
        // 这三个字段是上游 RikkaHub 3.2.0 settings.json 里就有、我们之前缺失的：
        // 不认它们，用户从上游导入的备份里这三项会被静默丢弃。
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        assertEquals(false, preferences[SettingsStore.ENABLE_AUTO_TITLE])
        assertEquals(true, preferences[SettingsStore.ENABLE_AUTO_COMPRESS])
        assertEquals(0.65f, preferences[SettingsStore.AUTO_COMPRESS_THRESHOLD])

        val restored = preferences.toSettings()
        assertEquals(false, restored.enableAutoTitle)
        assertEquals(true, restored.enableAutoCompress)
        assertEquals(0.65f, restored.autoCompressThreshold)
    }

    // 这里的 key 名和取值就是用户盘上的格式，改动它们等于丢掉已有的设置
    @Test
    fun `stored format stays compatible with existing data`() {
        val stored = mutablePreferencesOf().apply { putSettings(SAMPLE) }.asMap().mapKeys { it.key.name }

        // SETTINGS_FIELDS 只覆盖一部分字段，剩下的是 persistSettings 手写读写的。
        // 这里断言：凡是 SETTINGS_FIELDS 写出来的 key，都必须是用户盘上早已存在的名字，
        // 不能引入新 key 把老数据晾在一边。
        assertEquals(
            "SETTINGS_FIELDS 写出了不在既有格式里的 key",
            emptySet<String>(),
            stored.keys - STORED_KEYS,
        )
        assertEquals(false, stored["dynamic_color"])
        assertEquals(9090, stored["web_server_port"])
        assertEquals(1.5f, stored["default_tts_playback_speed"])
        assertEquals("title", stored["title_prompt"])
        assertEquals("00000000-0000-0000-0000-000000000001", stored["chat_model"])
        assertEquals("LOW", stored["fast_model_reasoning_level"])
        assertEquals("""[{"id":"00000000-0000-0000-0000-000000000007","name":"tag"}]""", stored["assistant_tags"])
        assertEquals("""{"resultSize":3}""", stored["search_common"])
    }

    @Test
    fun `missing keys read as first launch values`() {
        val settings = emptyPreferences().toSettings()

        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.chatModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.fastModelId)
        assertEquals(DEFAULT_AUTO_MODEL_ID, settings.translateModeId)
        assertEquals(DEFAULT_MODE_INJECTIONS, settings.modeInjections)
        assertEquals(DEFAULT_ASSISTANT_ID, settings.assistantId)
        assertEquals(DEFAULT_SYSTEM_TTS_ID, settings.selectedTTSProviderId)
        assertEquals(null, settings.selectedASRProviderId)
        assertEquals(true, settings.dynamicColor)
        assertEquals(8080, settings.webServerPort)
    }

    @Test
    fun `unknown reasoning level reads as auto`() {
        val preferences = mutablePreferencesOf(SettingsStore.FAST_MODEL_REASONING_LEVEL to "REMOVED_LEVEL")

        assertEquals(ReasoningLevel.AUTO, preferences.toSettings().fastModelReasoningLevel)
    }

    @Test
    fun `clearing the asr provider removes its key`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }

        preferences.putSettings(SAMPLE.copy(selectedASRProviderId = null), persisted = SAMPLE)

        assertFalse(SettingsStore.SELECTED_ASR_PROVIDER in preferences)
        assertEquals(null, preferences.toSettings().selectedASRProviderId)
    }

    @Test
    fun `only changed fields are written`() {
        val preferences = mutablePreferencesOf().apply { putSettings(SAMPLE) }
        // 盘上的值故意和已落盘的那份不一致：没变的字段如果也被重写，它就会变回去
        preferences[SettingsStore.TITLE_PROMPT] = "on disk"

        preferences.putSettings(SAMPLE.copy(themeId = "changed"), persisted = SAMPLE)

        assertEquals("changed", preferences[SettingsStore.THEME_ID])
        assertEquals("on disk", preferences[SettingsStore.TITLE_PROMPT])
    }
}

private fun id(n: Int) = Uuid.parse("00000000-0000-0000-0000-%012d".format(n))

// 每个字段都取了和默认值不同的值
private val SAMPLE = Settings(
    dynamicColor = false,
    themeId = "custom",
    customThemes = listOf(CustomTheme(id = "theme", name = "Theme")),
    developerMode = true,
    displaySetting = DisplaySetting(userNickname = "me"),
    networkSetting = NetworkSetting(userAgent = "agent"),
    favoriteModels = listOf(id(30)),
    chatModelId = id(1),
    fastModelId = id(2),
    fastModelReasoningLevel = ReasoningLevel.LOW,
    imageGenerationModelId = id(3),
    enableAutoTitle = false,
    enableAutoCompress = true,
    autoCompressThreshold = 0.65f,
    titlePrompt = "title",
    translateModeId = id(4),
    translatePrompt = "translate",
    translateThinkingBudget = 1024,
    translateTargetLanguage = "ja",
    enableSuggestion = false,
    suggestionPrompt = "suggestion",
    ocrModelId = id(5),
    ocrPrompt = "ocr",
    assistantId = id(20),
    providers = listOf(ProviderSetting.OpenAI(id = id(31), name = "Provider")),
    assistants = listOf(Assistant(id = id(20), name = "Assistant")),
    assistantTags = listOf(Tag(id = id(7), name = "tag")),
    searchServices = listOf(
        SearchServiceOptions.BingLocalOptions(id = id(21)),
        SearchServiceOptions.BingLocalOptions(id = id(22)),
    ),
    searchCommonOptions = SearchCommonOptions(resultSize = 3),
    searchServiceSelected = 1,
    mcpServers = listOf(McpServerConfig.SseTransportServer(id = id(8), url = "https://example.com/sse")),
    webDavConfig = WebDavConfig(url = "https://dav.example.com"),
    s3Config = S3Config(bucket = "backup"),
    uploadS3Config = S3Config(bucket = "upload"),
    mediaGenerationProviders = listOf(MediaGenerationProviderSetting.OpenAI(id = id(9))),
    ttsProviders = DEFAULT_TTS_PROVIDERS.take(1),
    selectedTTSProviderId = id(10),
    defaultTTSPlaybackSpeed = 1.5f,
    asrProviders = listOf(ASRProviderSetting.OpenAIRealtime(id = id(11))),
    selectedASRProviderId = id(11),
    modeInjections = listOf(PromptInjection.ModeInjection(id = id(14), name = "mode")),
    lorebooks = listOf(Lorebook(id = id(12), name = "lorebook")),
    quickMessages = listOf(QuickMessage(id = id(13), title = "hi")),
    webServerEnabled = true,
    webServerPort = 9090,
    webServerJwtEnabled = true,
    webServerAccessPassword = "secret",
    webServerLocalhostOnly = true,
    backupReminderConfig = BackupReminderConfig(enabled = true),
    sponsorAlertDismissedAt = 7,
)

private val STORED_KEYS = setOf(
    "dynamic_color",
    "theme_id",
    "custom_themes",
    "display_setting",
    "network_setting",
    "developer_mode",
    "favorite_models",
    "chat_model",
    "fast_model",
    "fast_model_reasoning_level",
    "translate_model",
    "enable_suggestion",
    "enable_auto_title",
    "enable_auto_compress",
    "auto_compress_threshold",
    "image_generation_model",
    "title_prompt",
    "translation_prompt",
    "translate_thinking_budget",
    "translate_target_language",
    "suggestion_prompt",
    "enable_web_search",
    "message_style",
    "memory_model",
    "memory_model_reasoning_level",
    "title_model",
    "suggestion_model",
    "compress_model",
    "compress_prompt",
    "kb_injection_settings",
    "prompt_injections",
    "tavern_presets",
    "tavern_global_scripts",
    "tavern_global_scripts_enabled",
    "tavern_preset_scripts",
    "tavern_third_party_disabled",
    "tavern_extension_settings",
    "regex_scripts",
    "ocr_model",
    "ocr_prompt",
    "providers",
    "select_assistant",
    "assistants",
    "assistant_tags",
    "search_services",
    "search_common",
    "search_selected",
    "mcp_servers",
    "webdav_config",
    "s3_config",
    "upload_s3_config",
    "media_generation_providers",
    "tts_providers",
    "selected_tts_provider",
    "default_tts_playback_speed",
    "asr_providers",
    "selected_asr_provider",
    "web_server_enabled",
    "web_server_port",
    "web_server_jwt_enabled",
    "web_server_access_password",
    "web_server_localhost_only",
    "mode_injections",
    "lorebooks",
    "quick_messages",
    "backup_reminder_config",
    "sponsor_alert_dismissed_at",
)
