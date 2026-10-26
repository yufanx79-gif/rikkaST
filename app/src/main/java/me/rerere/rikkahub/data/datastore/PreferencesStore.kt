package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV4Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV5Migration
import me.rerere.rikkahub.data.datastore.migration.migrateProviderQuotaQuery
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.model.parseAuthorNotePosition
import me.rerere.rikkahub.data.st.expressions.ExpressionClassifier
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.tokenizer.TokenizerMode
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private const val SETTINGS_STORE_NAME = "settings"

// 读取失败时的最大重试次数
private const val READ_MAX_RETRIES = 3

private val SUPPORTED_PROVIDER_TYPES = setOf("openai", "google", "claude", "chatgpt", "grok")
private val SUPPORTED_MODEL_TYPES = setOf("CHAT", "IMAGE", "EMBEDDING")
private val SUPPORTED_MODALITIES = setOf("TEXT", "IMAGE")
private val SUPPORTED_ABILITIES = setOf("TOOL", "REASONING")
private val SUPPORTED_BUILTIN_TOOLS = setOf("search", "url_context", "image_generation")

private fun deterministicUuid(seed: String): Uuid {
    val jvmUuid = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8))
    return Uuid.parse(jvmUuid.toString())
}

private fun parseUuidOrNull(raw: String?): Uuid? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Uuid.parse(trimmed) }.getOrNull()
}

private fun parseUuidOrDefault(raw: String?, fallback: Uuid): Uuid =
    parseUuidOrNull(raw) ?: fallback

/**
 * 清洗并修复单个 [Model] JSON 对象：
 * - 缺失或非法的 UUID `id` 使用基于 `modelId`/`displayName` 的确定性 UUID 修复；
 * - 未知枚举值（`type` / `inputModalities` / `outputModalities` / `abilities` / `tools`）自动收敛为安全合法值；
 * - 损坏的嵌套 `providerOverwrite` 被隔离降级为 `null`，绝不连带丢弃该模型或所属提供商。
 */
internal fun sanitizeModelJsonObject(
    obj: JsonObject,
    index: Int = 0,
    onDiagnostic: ((String) -> Unit)? = null,
): JsonObject {
    val map = LinkedHashMap<String, JsonElement>()
    for ((k, v) in obj) {
        if (v !is JsonNull) {
            map[k] = v
        }
    }
    val modelId = (map["modelId"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?: (map["name"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?: ""
    val displayName = (map["displayName"] as? JsonPrimitive)?.contentOrNull?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: modelId
    map["modelId"] = JsonPrimitive(modelId)
    map["displayName"] = JsonPrimitive(displayName)

    val rawId = (map["id"] as? JsonPrimitive)?.contentOrNull
    val validId = parseUuidOrNull(rawId) ?: deterministicUuid("model:$modelId:$displayName:$index")
    map["id"] = JsonPrimitive(validId.toString())

    val rawType = (map["type"] as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()
    map["type"] = JsonPrimitive(if (rawType in SUPPORTED_MODEL_TYPES) rawType!! else "CHAT")

    (map["inputModalities"] as? JsonArray)?.let { arr ->
        val filtered = arr.mapNotNull { el ->
            val s = (el as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()
            if (s in SUPPORTED_MODALITIES) JsonPrimitive(s!!) else null
        }
        map["inputModalities"] = JsonArray(filtered.ifEmpty { listOf(JsonPrimitive("TEXT")) })
    } ?: map.remove("inputModalities")

    (map["outputModalities"] as? JsonArray)?.let { arr ->
        val filtered = arr.mapNotNull { el ->
            val s = (el as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()
            if (s in SUPPORTED_MODALITIES) JsonPrimitive(s!!) else null
        }
        map["outputModalities"] = JsonArray(filtered.ifEmpty { listOf(JsonPrimitive("TEXT")) })
    } ?: map.remove("outputModalities")

    (map["abilities"] as? JsonArray)?.let { arr ->
        val filtered = arr.mapNotNull { el ->
            val s = (el as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()
            if (s in SUPPORTED_ABILITIES) JsonPrimitive(s!!) else null
        }
        map["abilities"] = JsonArray(filtered)
    } ?: map.remove("abilities")

    (map["tools"] as? JsonArray)?.let { arr ->
        val filtered = arr.mapNotNull { el ->
            val s = (el as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
            when {
                s in SUPPORTED_BUILTIN_TOOLS -> JsonPrimitive(s!!)
                el is JsonObject -> {
                    val t = (el["type"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
                    if (t in SUPPORTED_BUILTIN_TOOLS) JsonObject(mapOf("type" to JsonPrimitive(t!!))) else null
                }
                else -> null
            }
        }
        map["tools"] = JsonArray(filtered)
    } ?: map.remove("tools")

    val overwriteEl = map["providerOverwrite"]
    if (overwriteEl != null) {
        val sanitizedOverwrite = (overwriteEl as? JsonObject)?.let {
            sanitizeProviderJsonObject(it, index = index, onDiagnostic = onDiagnostic)
        }
        if (sanitizedOverwrite != null) {
            val canDecodeOverwrite = runCatching {
                JsonInstant.decodeFromJsonElement(ProviderSetting.serializer(), sanitizedOverwrite)
            }.isSuccess
            if (canDecodeOverwrite) {
                map["providerOverwrite"] = sanitizedOverwrite
            } else {
                map.remove("providerOverwrite")
                onDiagnostic?.invoke("[providers] dropped malformed providerOverwrite on model index=$index")
            }
        } else {
            map.remove("providerOverwrite")
            onDiagnostic?.invoke("[providers] dropped non-object providerOverwrite on model index=$index")
        }
    }

    return JsonObject(map)
}

internal fun sanitizeProviderJsonObject(
    rawObj: JsonObject,
    index: Int = 0,
    onDiagnostic: ((String) -> Unit)? = null,
): JsonObject? {
    val migratedObj = migrateProviderQuotaQuery(rawObj) as? JsonObject ?: rawObj
    val map = LinkedHashMap<String, JsonElement>()
    for ((k, v) in migratedObj) {
        if (v !is JsonNull) {
            map[k] = v
        }
    }

    val rawType = (map["type"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
    val resolvedType = when {
        rawType in SUPPORTED_PROVIDER_TYPES -> rawType!!
        rawType == "anthropic" -> "claude"
        rawType == "gemini" || rawType == "vertex" || rawType == "vertexai" -> "google"
        rawType == "openai-compatible" || rawType == "custom" -> "openai"
        rawType == null -> when {
            "vertexAI" in map || "serviceAccountEmail" in map || "projectId" in map -> "google"
            "promptCaching" in map || "promptCacheTtl" in map -> "claude"
            "credential" in map -> "chatgpt"
            "baseUrl" in map || "apiKey" in map || "models" in map -> "openai"
            else -> null
        }
        else -> null
    }
    if (resolvedType == null) {
        onDiagnostic?.invoke("[providers] skipped provider at index=$index: unsupported type='${rawType ?: "missing"}'")
        return null
    }
    map["type"] = JsonPrimitive(resolvedType)

    val name = (map["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: resolvedType.replaceFirstChar { it.uppercase() }
    map["name"] = JsonPrimitive(name)

    val rawId = (map["id"] as? JsonPrimitive)?.contentOrNull
    val validId = parseUuidOrNull(rawId) ?: deterministicUuid("provider:$resolvedType:$name:$index")
    map["id"] = JsonPrimitive(validId.toString())

    val rawModels = map["models"]
    if (rawModels is JsonArray) {
        val validModels = ArrayList<JsonElement>(rawModels.size)
        rawModels.forEachIndexed { mIdx, mEl ->
            val mObj = mEl as? JsonObject
            if (mObj == null) {
                onDiagnostic?.invoke("[providers] skipped non-object model at providerIndex=$index modelIndex=$mIdx")
                return@forEachIndexed
            }
            val sanitizedModel = sanitizeModelJsonObject(mObj, mIdx, onDiagnostic)
            val decodedModel = runCatching {
                JsonInstant.decodeFromJsonElement(Model.serializer(), sanitizedModel)
            }.onFailure { err ->
                onDiagnostic?.invoke(
                    "[providers] skipped malformed model at providerIndex=$index modelIndex=$mIdx error=${err.javaClass.simpleName}"
                )
            }.getOrNull()
            if (decodedModel != null) {
                validModels.add(sanitizedModel)
            }
        }
        map["models"] = JsonArray(validModels)
    } else if (rawModels != null) {
        map.remove("models")
    }

    if (resolvedType == "claude") {
        val rawTtl = (map["promptCacheTtl"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (rawTtl != null && rawTtl != "5m" && rawTtl != "1h") {
            map["promptCacheTtl"] = JsonPrimitive("5m")
        }
    }

    if (map["customHeaders"] !is JsonArray) {
        map.remove("customHeaders")
    }
    if (map["quotaQuery"] != null && map["quotaQuery"] !is JsonObject) {
        map.remove("quotaQuery")
    }

    return JsonObject(map)
}

/**
 * 逐条容错解码提供商列表（Task C 核心根因修复）：
 * - 先幂等执行 `migrateProviderQuotaQuery`（兼容升级前未跑完 V5 迁移或备份导入的旧 `balanceOption` 字段）；
 * - 逐元素独立解码 `ProviderSetting` 与其内部 `models`：单条损坏、未知 discriminator、非法 UUID 或损坏的 `providerOverwrite`
 *   只跳过故障项并记录脱敏分类日志（绝不打印 `apiKey` / `accessToken` / `privateKey`），其余合法提供商 100% 保留。
 */
internal fun sanitizeProviderLogSummary(message: String): String {
    return message
        .replace(Regex("(apiKey|accessToken|refreshToken|idToken|privateKey|secretKey|jwt|password)\\s*[:=]\\s*[^,\\s}\\]]+", RegexOption.IGNORE_CASE), "$1=***")
        .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "Bearer ***")
        .replace(Regex("sk-[A-Za-z0-9._\\-]{6,}"), "sk-***")
}

internal fun sanitizeProviderLogSummary(
    rawObj: JsonObject,
    index: Int,
    reason: String,
): String {
    val rawType = (rawObj["type"] as? JsonPrimitive)?.contentOrNull?.trim() ?: "missing"
    val rawId = (rawObj["id"] as? JsonPrimitive)?.contentOrNull?.trim() ?: "missing"
    val rawName = (rawObj["name"] as? JsonPrimitive)?.contentOrNull?.trim() ?: "unnamed"
    val redactedKeys = rawObj.keys.filter { key ->
        val lower = key.lowercase()
        lower.contains("key") || lower.contains("token") || lower.contains("secret") ||
            lower.contains("password") || lower.contains("credential") || lower.contains("jwt")
    }.sorted()
    val redactedSummary = if (redactedKeys.isEmpty()) "none" else redactedKeys.joinToString(",") { "$it=<REDACTED>" }
    return "[providers] index=$index type=$rawType id=$rawId name=$rawName reason=$reason redacted=[$redactedSummary]"
}

internal fun decodeProvidersResilient(
    rawJson: String?,
    json: Json = JsonInstant,
    onCorruptBackup: ((String) -> Unit)? = null,
    onDiagnostic: ((String) -> Unit)? = null,
): List<ProviderSetting> {
    val text = rawJson?.trim().orEmpty()
    if (text.isEmpty() || text == "[]") return emptyList()

    val logDiag: (String) -> Unit = { msg ->
        val safeMsg = sanitizeProviderLogSummary(msg)
        onDiagnostic?.invoke(safeMsg)
        runCatching { Log.w(TAG, safeMsg) }
        runCatching { TavernRuntimeManager.appendLog("warn", safeMsg) }
    }

    val root = runCatching { json.parseToJsonElement(text) }.getOrElse { err ->
        onCorruptBackup?.invoke(text)
        logDiag("[providers] top-level JSON parse failed: category=${err.javaClass.simpleName}")
        return emptyList()
    }
    val array = root as? JsonArray ?: run {
        onCorruptBackup?.invoke(text)
        logDiag("[providers] top-level JSON is not an array: actual=${root::class.simpleName}")
        return emptyList()
    }

    val result = ArrayList<ProviderSetting>(array.size)
    var skippedCount = 0
    array.forEachIndexed { index, element ->
        val obj = element as? JsonObject
        if (obj == null) {
            skippedCount++
            logDiag("[providers] skipped non-object provider record at index=$index")
            return@forEachIndexed
        }
        val sanitized = sanitizeProviderJsonObject(obj, index, logDiag)
        if (sanitized == null) {
            skippedCount++
            logDiag(sanitizeProviderLogSummary(obj, index, "unsupported_or_malformed_object"))
            return@forEachIndexed
        }
        val decoded = runCatching {
            json.decodeFromJsonElement(ProviderSetting.serializer(), sanitized)
        }.onFailure { err ->
            skippedCount++
            logDiag(sanitizeProviderLogSummary(obj, index, "decode_${err.javaClass.simpleName}"))
        }.getOrNull()
        if (decoded != null) {
            result.add(decoded)
        }
    }
    if (skippedCount > 0) {
        onCorruptBackup?.invoke(text)
        logDiag("[providers] resilient decode summary: total=${array.size} valid=${result.size} skipped=$skippedCount")
    }
    return result
}

private inline fun <reified T> decodeListResilient(
    rawJson: String?,
    fieldName: String,
    json: Json = JsonInstant,
): List<T> {
    val text = rawJson?.trim().orEmpty()
    if (text.isEmpty() || text == "[]") return emptyList()
    runCatching { return json.decodeFromString<List<T>>(text) }
    val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
    val out = ArrayList<T>(arr.size)
    arr.forEachIndexed { idx, el ->
        runCatching {
            out.add(json.decodeFromString<T>(el.toString()))
        }.onFailure { err ->
            runCatching {
                Log.w(TAG, "[$fieldName] skipped malformed element at index=$idx (${err.javaClass.simpleName})")
            }
        }
    }
    return out
}

private inline fun <reified T> decodeObjectOrDefault(
    rawJson: String?,
    defaultValue: T,
    json: Json = JsonInstant,
): T {
    val text = rawJson?.trim().orEmpty()
    if (text.isEmpty()) return defaultValue
    return runCatching { json.decodeFromString<T>(text) }.getOrDefault(defaultValue)
}

@Volatile
private var settingsDataStore: DataStore<Preferences>? = null

// 进程内单例, 同一文件只能存在一个 DataStore 实例
private val Context.settingsStore: DataStore<Preferences>
    get() = settingsDataStore ?: synchronized(SettingsStore::class) {
        settingsDataStore ?: createSettingsDataStore(applicationContext).also { settingsDataStore = it }
    }

private fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    val file = context.preferencesDataStoreFile(SETTINGS_STORE_NAME)
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { exception ->
            // 文件已损坏无法解析, 先留一份原文件用于排查/抢救, 再重建为空
            Log.e(TAG, "Settings datastore corrupted, resetting", exception)
            runCatching {
                file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            }.onFailure {
                Log.e(TAG, "Failed to backup corrupted settings file", it)
            }
            emptyPreferences()
        },
        migrations = listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration(),
            PreferenceStoreV4Migration(),
            PreferenceStoreV5Migration(),
        ),
        produceFile = { file },
    )
}

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        /** [v225 F2] 消息样式（MessageStyleSetting）。v222~v224 漏读漏写 -> 值被默认值覆盖。 */
        val MESSAGE_STYLE = stringPreferencesKey(MESSAGE_STYLE_KEY_NAME)
        val NETWORK_SETTING = stringPreferencesKey("network_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 模型选择
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val FAST_MODEL = stringPreferencesKey("fast_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val FAST_MODEL_REASONING_LEVEL = stringPreferencesKey("fast_model_reasoning_level")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val ENABLE_SUGGESTION = booleanPreferencesKey("enable_suggestion")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val TRANSLATE_TARGET_LANGUAGE = stringPreferencesKey("translate_target_language")
        val UPLOAD_S3_CONFIG = stringPreferencesKey("upload_s3_config")
        val MEDIA_GENERATION_PROVIDERS = stringPreferencesKey("media_generation_providers")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")
        val EMBEDDING_MODEL = stringPreferencesKey("embedding_model")
        val KB_INJECTION_SETTINGS = stringPreferencesKey("kb_injection_settings")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")
        val PROVIDERS_CORRUPT_BACKUP = stringPreferencesKey("providers_corrupt_backup")

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
        val PROMPT_INJECTIONS = stringPreferencesKey("prompt_injections")
        val TAVERN_PRESETS = stringPreferencesKey("tavern_presets")
        val TAVERN_GLOBAL_SCRIPTS = stringPreferencesKey("tavern_global_scripts")
        val TAVERN_GLOBAL_SCRIPTS_ENABLED = booleanPreferencesKey("tavern_global_scripts_enabled")
        val TAVERN_PRESET_SCRIPTS = stringPreferencesKey("tavern_preset_scripts")
        val TAVERN_THIRD_PARTY_DISABLED = stringPreferencesKey("tavern_third_party_disabled")
        val TAVERN_EXTENSION_SETTINGS = stringPreferencesKey("tavern_extension_settings")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
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
        val MACRO_GLOBAL_VARIABLES = stringPreferencesKey("macro_global_variables")
        val MACRO_CHAT_VARIABLES = stringPreferencesKey("macro_chat_variables")
        val DEEPSEEK_CACHE_OPTIMIZATION = booleanPreferencesKey("deepseek_cache_optimization")
        val TAVERN_EJS_RENDERING = booleanPreferencesKey("tavern_ejs_rendering")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // GitHub
        val GITHUB_TOKEN = stringPreferencesKey("github_token")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // 赞助提醒
        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")

        // 人设 & 导演备注
        val PERSONAS = stringPreferencesKey("personas")
        val ACTIVE_PERSONA_ID = stringPreferencesKey("active_persona_id")
        val AUTHOR_NOTE = stringPreferencesKey("author_note")
        val AUTHOR_NOTE_ENABLED = booleanPreferencesKey("author_note_enabled")
        val AUTHOR_NOTE_POSITION = stringPreferencesKey("author_note_position")
        val AUTHOR_NOTE_DEPTH = intPreferencesKey("author_note_depth")
        val AUTHOR_NOTE_ROLE = stringPreferencesKey("author_note_role")
        val AUTHOR_NOTE_INTERVAL = intPreferencesKey("author_note_interval")
        val GROUP_CHATS = stringPreferencesKey("group_chats")

        // [v240 W3] Character Expressions（立绘）
        val EXPRESSION_ENABLED = booleanPreferencesKey("expression_enabled")
        val EXPRESSION_CLASSIFIER = stringPreferencesKey("expression_classifier")
        val EXPRESSION_FALLBACK_LABEL = stringPreferencesKey("expression_fallback_label")

        // [v240 W5] 真 tokenizer 模式
        val TOKENIZER_MODE = stringPreferencesKey("tokenizer_mode")

        internal suspend fun restoreBeforeInitialization(
            context: Context,
            settings: Settings,
            launchCount: Int = settings.launchCount,
        ) {
            require(!settings.init) { "Cannot restore uninitialized settings" }
            persistSettings(context.settingsStore, settings.copy(launchCount = launchCount))
        }

        private suspend fun persistSettings(dataStore: DataStore<Preferences>, settings: Settings) {
            dataStore.edit { preferences ->
                preferences[DYNAMIC_COLOR] = settings.dynamicColor
                preferences[ENABLE_WEB_SEARCH] = settings.enableWebSearch
                preferences[THEME_ID] = settings.themeId
                preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
                preferences[DEVELOPER_MODE] = settings.developerMode
                preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)
                preferences[MESSAGE_STYLE] = encodeMessageStyle(settings.messageStyle)

                preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
                preferences[SELECT_MODEL] = settings.chatModelId.toString()
                preferences[FAST_MODEL] = settings.fastModelId.toString()
                preferences[FAST_MODEL_REASONING_LEVEL] = settings.fastModelReasoningLevel.name
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
                preferences[TRANSLATE_TARGET_LANGUAGE] = settings.translateTargetLanguage
                preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
                preferences[OCR_MODEL] = settings.ocrModelId.toString()
                preferences[OCR_PROMPT] = settings.ocrPrompt
                preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
                preferences[COMPRESS_PROMPT] = settings.compressPrompt
                settings.embeddingModelId?.let {
                    preferences[EMBEDDING_MODEL] = it.toString()
                } ?: preferences.remove(EMBEDDING_MODEL)
                preferences[KB_INJECTION_SETTINGS] = JsonInstant.encodeToString(settings.kbInjectionSettings)

                preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

                preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
                preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
                preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

                preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
                preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
                preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))

                preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
                preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
                preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
                preferences[UPLOAD_S3_CONFIG] = JsonInstant.encodeToString(settings.uploadS3Config)
                preferences[MEDIA_GENERATION_PROVIDERS] = JsonInstant.encodeToString(settings.mediaGenerationProviders)
                preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
                preferences[SELECTED_TTS_PROVIDER] = settings.selectedTTSProviderId.toString()
                preferences[DEFAULT_TTS_PLAYBACK_SPEED] = settings.defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f)
                preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
                settings.selectedASRProviderId?.let {
                    preferences[SELECTED_ASR_PROVIDER] = it.toString()
                } ?: preferences.remove(SELECTED_ASR_PROVIDER)
                preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
                preferences[PROMPT_INJECTIONS] = JsonInstant.encodeToString(settings.promptInjections)
                preferences[TAVERN_PRESETS] = settings.tavernPresets
                preferences[TAVERN_GLOBAL_SCRIPTS] = settings.tavernGlobalScripts
                preferences[TAVERN_GLOBAL_SCRIPTS_ENABLED] = settings.tavernGlobalScriptsEnabled
                preferences[TAVERN_PRESET_SCRIPTS] = settings.tavernPresetScripts
                preferences[TAVERN_THIRD_PARTY_DISABLED] = JsonInstant.encodeToString(settings.tavernThirdPartyDisabled)
                preferences[TAVERN_EXTENSION_SETTINGS] = settings.tavernExtensionSettings
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
                preferences[NETWORK_SETTING] = JsonInstant.encodeToString(settings.networkSetting)
            }
        }
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .retryWhen { cause, attempt ->
            val shouldRetry = cause is IOException && cause !is CorruptionException && attempt < READ_MAX_RETRIES
            if (shouldRetry) {
                Log.w(TAG, "Failed to read settings, retrying (${attempt + 1}/$READ_MAX_RETRIES)", cause)
                delay((100L shl attempt.toInt()).milliseconds)
            }
            shouldRetry
        }.map { preferences ->
            Settings(
                favoriteModels = decodeListResilient<Uuid>(preferences[FAVORITE_MODELS], "favoriteModels"),
                chatModelId = parseUuidOrDefault(preferences[SELECT_MODEL], DEFAULT_AUTO_MODEL_ID),
                fastModelId = parseUuidOrDefault(preferences[FAST_MODEL], DEFAULT_AUTO_MODEL_ID),
                fastModelReasoningLevel = preferences[FAST_MODEL_REASONING_LEVEL]
                    ?.let { value -> ReasoningLevel.entries.find { it.name == value } }
                    ?: ReasoningLevel.AUTO,
                titleModelId = parseUuidOrNull(preferences[TITLE_MODEL]),
                translateModeId = parseUuidOrDefault(preferences[TRANSLATE_MODEL], DEFAULT_AUTO_MODEL_ID),
                enableSuggestion = preferences[ENABLE_SUGGESTION] != false,
                deepseekCacheOptimization = preferences[DEEPSEEK_CACHE_OPTIMIZATION] ?: true,
                tavernEjsRendering = preferences[TAVERN_EJS_RENDERING] ?: true,
                suggestionModelId = parseUuidOrNull(preferences[SUGGESTION_MODEL]),
                imageGenerationModelId = parseUuidOrDefault(preferences[IMAGE_GENERATION_MODEL], Uuid.random()),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                translateTargetLanguage = preferences[TRANSLATE_TARGET_LANGUAGE] ?: "zh-CN",
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = parseUuidOrDefault(preferences[OCR_MODEL], Uuid.random()),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = parseUuidOrDefault(preferences[COMPRESS_MODEL], DEFAULT_AUTO_MODEL_ID),
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                embeddingModelId = parseUuidOrNull(preferences[EMBEDDING_MODEL]),
                kbInjectionSettings = decodeObjectOrDefault(preferences[KB_INJECTION_SETTINGS], KbInjectionSettings()),
                assistantId = parseUuidOrDefault(preferences[SELECT_ASSISTANT], DEFAULT_ASSISTANT_ID),
                assistantTags = decodeListResilient<Tag>(preferences[ASSISTANT_TAGS], "assistantTags"),
                providers = decodeProvidersResilient(
                    rawJson = preferences[PROVIDERS],
                    onCorruptBackup = { corruptRaw ->
                        if (preferences[PROVIDERS_CORRUPT_BACKUP].isNullOrEmpty() && corruptRaw.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    dataStore.edit { editPrefs ->
                                        if (editPrefs[PROVIDERS_CORRUPT_BACKUP].isNullOrEmpty()) {
                                            editPrefs[PROVIDERS_CORRUPT_BACKUP] = corruptRaw
                                        }
                                    }
                                }
                            }
                        }
                    },
                ),
                assistants = decodeListResilient<Assistant>(preferences[ASSISTANTS], "assistants"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                enableWebSearch = preferences[ENABLE_WEB_SEARCH] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = decodeListResilient<CustomTheme>(preferences[CUSTOM_THEMES], "customThemes"),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = decodeObjectOrDefault(preferences[DISPLAY_SETTING], DisplaySetting()),
                messageStyle = decodeMessageStyle(preferences[MESSAGE_STYLE]),
                networkSetting = decodeObjectOrDefault(preferences[NETWORK_SETTING], NetworkSetting()),
                searchServices = decodeListResilient<SearchServiceOptions>(preferences[SEARCH_SERVICES], "searchServices")
                    .ifEmpty { listOf(SearchServiceOptions.DEFAULT) },
                searchCommonOptions = decodeObjectOrDefault(preferences[SEARCH_COMMON], SearchCommonOptions()),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = decodeListResilient<McpServerConfig>(preferences[MCP_SERVERS], "mcpServers"),
                webDavConfig = decodeObjectOrDefault(preferences[WEBDAV_CONFIG], WebDavConfig()),
                s3Config = decodeObjectOrDefault(preferences[S3_CONFIG], S3Config()),
                uploadS3Config = decodeObjectOrDefault(preferences[UPLOAD_S3_CONFIG], S3Config()),
                mediaGenerationProviders = decodeListResilient<MediaGenerationProviderSetting>(
                    preferences[MEDIA_GENERATION_PROVIDERS],
                    "mediaGenerationProviders",
                ),
                ttsProviders = decodeListResilient<TTSProviderSetting>(preferences[TTS_PROVIDERS], "ttsProviders"),
                selectedTTSProviderId = parseUuidOrDefault(preferences[SELECTED_TTS_PROVIDER], DEFAULT_SYSTEM_TTS_ID),
                defaultTTSPlaybackSpeed = preferences[DEFAULT_TTS_PLAYBACK_SPEED]?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                asrProviders = decodeListResilient<ASRProviderSetting>(preferences[ASR_PROVIDERS], "asrProviders"),
                selectedASRProviderId = parseUuidOrNull(preferences[SELECTED_ASR_PROVIDER]),
                modeInjections = decodeListResilient<PromptInjection.ModeInjection>(preferences[MODE_INJECTIONS], "modeInjections"),
                promptInjections = decodeListResilient<PromptInjection.ModeInjection>(preferences[PROMPT_INJECTIONS], "promptInjections"),
                tavernPresets = preferences[TAVERN_PRESETS] ?: "{}",
                tavernGlobalScripts = preferences[TAVERN_GLOBAL_SCRIPTS] ?: "[]",
                tavernGlobalScriptsEnabled = preferences[TAVERN_GLOBAL_SCRIPTS_ENABLED] ?: true,
                tavernPresetScripts = preferences[TAVERN_PRESET_SCRIPTS] ?: "{}",
                tavernThirdPartyDisabled = preferences[TAVERN_THIRD_PARTY_DISABLED]?.let {
                    runCatching { JsonInstant.decodeFromString<Set<String>>(it) }.getOrDefault(emptySet())
                } ?: emptySet(),
                tavernExtensionSettings = preferences[TAVERN_EXTENSION_SETTINGS] ?: "{}",
                lorebooks = decodeListResilient<Lorebook>(preferences[LOREBOOKS], "lorebooks"),
                regexScripts = decodeListResilient<RegexScript>(preferences[REGEX_SCRIPTS], "regexScripts"),
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
                quickMessages = decodeListResilient<QuickMessage>(preferences[QUICK_MESSAGES], "quickMessages"),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true,
                backupReminderConfig = decodeObjectOrDefault(preferences[BACKUP_REMINDER_CONFIG], BackupReminderConfig()),
                githubToken = preferences[GITHUB_TOKEN] ?: "",
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
                personas = decodeListResilient<Persona>(preferences[PERSONAS], "personas").ifEmpty { DEFAULT_PERSONAS },
                activePersonaId = parseUuidOrNull(preferences[ACTIVE_PERSONA_ID]),
                authorNote = preferences[AUTHOR_NOTE] ?: "",
                authorNoteEnabled = preferences[AUTHOR_NOTE_ENABLED] ?: false,
                authorNotePosition = parseAuthorNotePosition(preferences[AUTHOR_NOTE_POSITION]),
                authorNoteDepth = preferences[AUTHOR_NOTE_DEPTH] ?: 4,
                authorNoteRole = preferences[AUTHOR_NOTE_ROLE]?.let {
                    runCatching { MessageRole.valueOf(it) }.getOrNull()
                } ?: MessageRole.SYSTEM,
                authorNoteInterval = preferences[AUTHOR_NOTE_INTERVAL] ?: 1,
                expressionEnabled = preferences[EXPRESSION_ENABLED] ?: false,
                expressionClassifier = ExpressionClassifier.parse(preferences[EXPRESSION_CLASSIFIER]),
                expressionFallbackLabel = preferences[EXPRESSION_FALLBACK_LABEL]
                    ?.takeIf { it.isNotBlank() } ?: ExpressionLabels.DEFAULT_FALLBACK,
                tokenizerMode = TokenizerMode.parse(preferences[TOKENIZER_MODE]),
                groupChats = decodeListResilient<GroupChat>(preferences[GROUP_CHATS], "groupChats"),
                macroGlobalVariables = decodeObjectOrDefault(preferences[MACRO_GLOBAL_VARIABLES], emptyMap()),
                macroChatVariables = decodeObjectOrDefault(preferences[MACRO_CHAT_VARIABLES], emptyMap()),
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
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds =
                (settings.modeInjections + settings.promptInjections).map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
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
                promptInjections = settings.promptInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                regexScripts = settings.regexScripts.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
            )
        }
        .onEach {
            runCatching { get<PebbleEngine>().templateCache.invalidateAll() }
        }

    private val echoGate = SettingsEchoGate()

    private fun markLocalWrite(next: Settings) {
        echoGate.mark(next)
        settingsFlow.value = next
    }

    val settingsFlow = MutableStateFlow(Settings.dummy())
    val launchCountFlow: Flow<Int> = settingsFlowRaw.map { it.launchCount }
    suspend fun awaitLoaded(): Settings = settingsFlow.first { !it.init }
    suspend fun selectAssistant(assistantId: Uuid) { updateAssistant(assistantId) }

    init {
        scope.launch {
            settingsFlowRaw
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .catch { t ->
                    Log.e(TAG, "settingsFlow collect failed: ${t.javaClass.simpleName}: ${t.message}")
                    if (settingsFlow.value.init) {
                        settingsFlow.value = Settings().normalized()
                    }
                }
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
        if (settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            TavernRuntimeManager.appendLog("warn", "[settings] update rejected: dummy (init=true)")
            return
        }
        val previous = settingsFlow.value
        markLocalWrite(settings)
        fireSettingsEvents(previous, settings)
        persistSettings(dataStore, settings)
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    /** CAS 原子更新当前 Settings（基于最新 settingsFlow.value）。 */
    suspend fun patch(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    private suspend fun patchKey(
        key: Preferences.Key<String>,
        transform: (Settings) -> Settings,
        encode: (Settings) -> String,
    ): Boolean {
        val current = settingsFlow.value
        if (current.init) {
            Log.w(TAG, "Cannot update dummy settings")
            TavernRuntimeManager.appendLog("warn", "[settings] patchKey rejected: dummy (init=true) key=${key.name}")
            return false
        }
        val next = transform(current)
        if (next == current) return false
        markLocalWrite(next)
        settingsFlow.value = next
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

    private fun fireSettingsEvents(previous: Settings, next: Settings) {
        for (name in settingsEventsFor(previous, next)) {
            TavernRuntimeManager.fireEvent(name)
        }
    }

    // 只原子地修改单个 key, 不能用 update() 写回整份快照
    suspend fun incrementLaunchCount(): Int {
        var count = 0
        dataStore.edit { preferences ->
            count = (preferences[LAUNCH_COUNT] ?: 0) + 1
            preferences[LAUNCH_COUNT] = count
        }
        return count
    }

    suspend fun setLaunchCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[LAUNCH_COUNT] = count
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
enum class WebViewPerfMode {
    @SerialName("hibernate_pool")
    HIBERNATE_POOL,

    @SerialName("live_quiescent")
    LIVE_QUIESCENT,

    @SerialName("historical_snapshot_image")
    HISTORICAL_SNAPSHOT_IMAGE,
}

@Serializable
enum class ResourceFetchMode {
    @SerialName("auto")
    AUTO,

    @SerialName("direct")
    DIRECT,

    @SerialName("vpn")
    VPN,
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
    val enableBlurEffect: Boolean = true,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    val enableTextColor: Boolean = true,
    val quoteColor: String = "",
    val italicsColor: String = "",
    val autoEmbedOnImport: Boolean = true,
    val embeddingEnabled: Boolean = true,
    val voiceModeListenWhileReplying: Boolean = false,
    val enableWebViewPerfReduction: Boolean = false,
    val webViewPerfMode: WebViewPerfMode = WebViewPerfMode.HIBERNATE_POOL,
    val resourceNetworkMode: ResourceFetchMode = ResourceFetchMode.AUTO,
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
