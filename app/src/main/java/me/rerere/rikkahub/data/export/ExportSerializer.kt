package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.pages.assistant.detail.mapSelectiveLogic
import me.rerere.rikkahub.ui.pages.assistant.detail.parseDelayUntilRecursionInt
import me.rerere.rikkahub.ui.pages.assistant.detail.mapTavernRole
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class ExportData(
    val version: Int = 1,
    val type: String,
    val data: JsonElement
)

interface ExportSerializer<T> {
    val type: String

    fun export(data: T): ExportData
    fun import(context: Context, uri: Uri): Result<T>

    // 获取导出文件名
    fun getExportFileName(data: T): String = "${type}.json"

    // 便捷方法：直接导出为 JSON 字符串
    fun exportToJson(data: T, json: Json = DefaultJson): String {
        return json.encodeToString(ExportData.serializer(), export(data))
    }

    // 读取 URI 内容的便捷方法
    fun readUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Failed to read file")
    }

    fun getUriFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) cursor.getString(nameIndex) else null
            } else null
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}

object ModeInjectionSerializer : ExportSerializer<PromptInjection.ModeInjection> {
    override val type = "mode_injection"

    override fun getExportFileName(data: PromptInjection.ModeInjection): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: PromptInjection.ModeInjection): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<PromptInjection.ModeInjection> {
        return runCatching {
            importList(context, uri).getOrThrow().firstOrNull()
                ?: throw IllegalArgumentException("导入失败：文件内容为空")
        }
    }

    /**
     * 导入为列表（支持酒馆预设：一个文件生成多条 ModeInjection）
     */
    fun importList(context: Context, uri: Uri): Result<List<PromptInjection.ModeInjection>> {
        return runCatching {
            val json = readUri(context, uri)
            val fileName = getUriFileName(context, uri)?.removeSuffix(".json")
            // 优先尝试酒馆（SillyTavern）预设格式，其次本应用自有格式
            tryImportSillyTavernPreset(json, fileName)
                ?: tryImportNative(json)?.let { listOf(it) }
                ?: throw IllegalArgumentException("不支持的格式。请选择从本应用导出的 JSON 或酒馆预设 JSON 文件")
        }
    }

    /**
     * B6：从酒馆预设文件中提取「酒馆助手」嵌入式脚本（extensions.tavern_helper.scripts）。
     * 组名与 [importList] 的 presetLabel 保持同一规则（文件名去 .json；兜底“酒馆预设”）。
     * @return (预设组名, 脚本数组)；无脚本或解析失败返回 null。
     */
    fun importPresetScripts(context: Context, uri: Uri): Pair<String, List<JsonElement>>? {
        return runCatching {
            val json = readUri(context, uri)
            val fileName = getUriFileName(context, uri)?.removeSuffix(".json")
            val label = fileName ?: "酒馆预设"
            val root = ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject
                ?: return@runCatching null
            val scripts = ((root["extensions"] as? JsonObject)
                ?.get("tavern_helper") as? JsonObject)
                ?.get("scripts") as? JsonArray ?: return@runCatching null
            if (scripts.isEmpty()) return@runCatching null
            label to scripts.toList()
        }.getOrNull()
    }

    private fun tryImportNative(json: String): PromptInjection.ModeInjection? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<PromptInjection.ModeInjection>(exportData.data)
                .copy(id = Uuid.random())
        }.getOrNull()
    }

    /**
     * 酒馆（SillyTavern）提示词预设导入。
     *
     * 将每个非占位（marker）提示词条目映射为独立的 [PromptInjection.ModeInjection]：
     * - 顺序与启停由 `prompt_order`（优先 character_id=100001 全局组）决定；缺失时回退 `prompts` 原序。
     * - `injection_position=1`（absolute，按深度插入）→ [InjectionPosition.AT_DEPTH]；
     *   `injection_position=0`（relative，就地拼接）→ system 角色映射到系统提示词之后，其余映射到聊天顶部。
     * - marker 条目（chatHistory / worldInfoBefore 等插槽）与空内容条目跳过；identifier 重复保留首个。
     */
    internal fun tryImportSillyTavernPreset(json: String, fileName: String?): List<PromptInjection.ModeInjection>? {
        return runCatching {
            val stPreset = ExportSerializer.DefaultJson.decodeFromString(
                SillyTavernPresetPrompts.serializer(),
                json
            )
            val rawPrompts = stPreset.prompts
            if (rawPrompts.isEmpty()) return null

            // identifier 重复保留首个；identifier 缺失的条目用下标合成 key（无法被 prompt_order 引用，只走原序回退）
            val promptsByKey = linkedMapOf<String, SillyTavernPromptLite>()
            rawPrompts.forEachIndexed { index, prompt ->
                val key = prompt.identifier?.takeIf { it.isNotBlank() } ?: "#$index"
                if (!promptsByKey.containsKey(key)) promptsByKey[key] = prompt
            }

            // 顺序与启用优先由 prompt_order 决定；100001 是全局默认组、100000 是 dummy 组，故优先 100001。
            // 缺失 prompt_order 时回退 prompts[] 原序，全部视为启用。
            val orderGroups = stPreset.promptOrder.filter { it.order.isNotEmpty() }
            val orderList = (orderGroups.firstOrNull { it.characterId == ST_PRESET_DEFAULT_CHARACTER_ID }
                ?: orderGroups.firstOrNull())
                ?.order
            val orderedKeys: List<Pair<String, Boolean>> = if (orderList != null) {
                val seen = mutableSetOf<String>()
                orderList.mapNotNull { orderEntry ->
                    val key = orderEntry.identifier?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // 引用了不存在的 identifier 直接跳过
                    if (!promptsByKey.containsKey(key) || !seen.add(key)) return@mapNotNull null
                    key to (orderEntry.enabled ?: true)
                }
            } else {
                promptsByKey.keys.map { it to true }
            }

            val presetLabel = fileName ?: "酒馆预设"
            val injections = mutableListOf<PromptInjection.ModeInjection>()
            orderedKeys.forEach { (key, orderEnabled) ->
                val prompt = promptsByKey[key] ?: return@forEach
                // marker = true 的系统占位条目无实际内容，跳过
                if (prompt.marker) return@forEach
                val content = prompt.content.orEmpty()
                if (content.isBlank()) return@forEach
                val stRole = mapSillyTavernPromptRole(prompt.role)
                injections += PromptInjection.ModeInjection(
                    id = Uuid.random(),
                    name = prompt.name?.takeIf { it.isNotBlank() }
                        ?: prompt.identifier?.takeIf { it.isNotBlank() }
                        ?: "$presetLabel - 条目${injections.size + 1}",
                    // 与官方一致：启停只认 prompt_order 的 enabled（prompts[].enabled 为历史噪声字段，忽略）
                    enabled = orderEnabled,
                    priority = injections.size,
                    position = mapSillyTavernPresetPosition(prompt.injectionPosition, stRole),
                    content = content.trim(),
                    injectDepth = prompt.injectionDepth ?: 4,
                    role = stRole,
                    presetGroup = presetLabel,
                )
            }
            if (injections.isEmpty()) return null
            injections
        }.getOrNull()
    }

    /**
     * 酒馆预设注入位置映射（与官方 PromptManager.js 语义对齐）。
     * absolute（1）= 按深度插入聊天；relative（0）或缺失 = 按角色归入系统块 / 聊天顶部。
     */
    internal fun mapSillyTavernPresetPosition(
        injectionPosition: Int?,
        role: MessageRole,
    ): InjectionPosition = when {
        injectionPosition == 1 -> InjectionPosition.AT_DEPTH
        role == MessageRole.SYSTEM -> InjectionPosition.AFTER_SYSTEM_PROMPT
        else -> InjectionPosition.TOP_OF_CHAT
    }

    /** 酒馆 role 字符串 → [MessageRole]，缺失/非法按 SYSTEM 处理（同时用于位置判定与条目角色） */
    internal fun mapSillyTavernPromptRole(role: String?): MessageRole = when (role?.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    }

    private const val ST_PRESET_DEFAULT_CHARACTER_ID = 100001
}

/** 酒馆预设 DTO：prompts + prompt_order（宽松解析，未知字段忽略） */
@Serializable
private data class SillyTavernPresetPrompts(
    val prompts: List<SillyTavernPromptLite> = emptyList(),
    @SerialName("prompt_order")
    val promptOrder: List<SillyTavernPromptOrder> = emptyList(),
)

@Serializable
private data class SillyTavernPromptLite(
    val identifier: String? = null,
    val name: String? = null,
    val enabled: Boolean? = null,
    val role: String? = null,
    val content: String? = null,
    val marker: Boolean = false,
    @SerialName("injection_position")
    val injectionPosition: Int? = null,
    @SerialName("injection_depth")
    val injectionDepth: Int? = null,
)

@Serializable
private data class SillyTavernPromptOrder(
    @SerialName("character_id")
    val characterId: Int? = null,
    val order: List<SillyTavernPromptOrderEntry> = emptyList(),
)

@Serializable
private data class SillyTavernPromptOrderEntry(
    val identifier: String? = null,
    val enabled: Boolean? = null,
)

object LorebookSerializer : ExportSerializer<Lorebook> {
    override val type = "lorebook"

    override fun getExportFileName(data: Lorebook): String {
        return "${data.name.ifEmpty { type }}.json"
    }

    override fun export(data: Lorebook): ExportData {
        return ExportData(
            type = type,
            data = ExportSerializer.DefaultJson.encodeToJsonElement(data)
        )
    }

    override fun import(context: Context, uri: Uri): Result<Lorebook> {
        return runCatching {
            val json = readUri(context, uri)
            importFromString(json, getUriFileName(context, uri)?.removeSuffix(".json"))
                ?: throw IllegalArgumentException("Unsupported format")
        }
    }

    /**
     * 从 JSON 字符串导入（本应用格式或酒馆世界书格式）。
     * 供「从 SillyTavern 备份全量导入」批量复用。
     */
    internal fun importFromString(json: String, fileName: String?): Lorebook? {
        // 首先尝试解析为自己的格式，然后尝试解析为 SillyTavern 格式
        return tryImportNative(json)
            ?: tryImportSillyTavern(json, fileName)
    }

    private fun tryImportNative(json: String): Lorebook? {
        return runCatching {
            val exportData = ExportSerializer.DefaultJson.decodeFromString(
                ExportData.serializer(),
                json
            )
            if (exportData.type != type) return null
            ExportSerializer.DefaultJson
                .decodeFromJsonElement<Lorebook>(exportData.data)
                .copy(
                    id = Uuid.random(),
                    entries = ExportSerializer.DefaultJson
                        .decodeFromJsonElement<Lorebook>(exportData.data)
                        .entries.map { it.copy(id = Uuid.random()) }
                )
        }.getOrNull()
    }

    private fun tryImportSillyTavern(json: String, fileName: String?): Lorebook? {
        return runCatching {
            val stLorebook = ExportSerializer.DefaultJson.decodeFromString(
                SillyTavernLorebook.serializer(),
                json
            )
            Lorebook(
                id = Uuid.random(),
                // [v216] 数据卫生：文件名可能带尾随空格（如「… 0.96 世界书 .json」）→ 存量数据不动，新增一律 trim。
                name = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: LocalDateTime.now().toLocalString(),
                description = "",
                enabled = true,
                entries = stLorebook.entries.values.map { entry ->
                    PromptInjection.RegexInjection(
                        id = Uuid.random(),
                        name = entry.comment.orEmpty().ifEmpty { entry.key.firstOrNull().orEmpty() },
                        enabled = !entry.disable,
                        priority = entry.order,
                        position = mapSillyTavernPosition(entry.position),
                        injectDepth = entry.depth,
                        content = entry.content,
                        keywords = entry.key,
                        secondaryKeys = entry.keysecondary,
                        useRegex = false, // 官方键始终支持 /regex/ 语法，keyMatches 会自动识别
                        caseSensitive = entry.caseSensitive ?: false,
                        matchWholeWords = entry.matchWholeWords ?: extBool(entry.extensions, "match_whole_words"),
                        excludeRecursion = entry.excludeRecursion ?: extBool(entry.extensions, "exclude_recursion"),
                        preventRecursion = entry.preventRecursion ?: extBool(entry.extensions, "prevent_recursion"),
                        delayUntilRecursion = parseDelayUntilRecursionInt(entry.delayUntilRecursion)
                            ?: parseDelayUntilRecursionInt(
                                entry.extensions?.jsonObject?.get("delay_until_recursion")
                            ) ?: 0,
                        scanDepth = entry.scanDepth,
                        constantActive = entry.constant,
                        selective = entry.selective,
                        selectiveLogic = mapSelectiveLogic(entry.selectiveLogic),
                        probability = entry.probability ?: 100,
                        useProbability = entry.useProbability ?: true,
                        group = entry.group.orEmpty(),
                        groupWeight = entry.groupWeight ?: 100,
                        groupOverride = entry.groupOverride ?: false,
                        role = mapTavernRole((entry.role as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "0"),
                        outletName = extString(entry.extensions, "outlet_name"),
                        sticky = entry.sticky ?: 0,
                        cooldown = entry.cooldown ?: 0,
                        delay = entry.delay ?: 0,
                        automationId = extString(entry.extensions, "automation_id"),
                        displayIndex = extInt(entry.extensions, "display_index"),
                        displayPosition = extInt(entry.extensions, "display_position"),
                        useGroupScoring = extBool(entry.extensions, "use_group_scoring"),
                        ignoreBudget = extBool(entry.extensions, "ignore_budget"),
                        triggers = extStringArray(entry.extensions, "triggers"),
                        matchPersonaDescription = extBool(entry.extensions, "match_persona_description"),
                        matchCharacterDescription = extBool(entry.extensions, "match_character_description"),
                        matchCharacterPersonality = extBool(entry.extensions, "match_character_personality"),
                        matchCharacterDepthPrompt = extBool(entry.extensions, "match_character_depth_prompt"),
                        matchScenario = extBool(entry.extensions, "match_scenario"),
                        matchCreatorNotes = extBool(entry.extensions, "match_creator_notes"),
                    )
                }
            )
        }.getOrNull()
    }

    /** 官方 world_info_position：0=before 1=after 2=ANTop 3=ANBottom 4=atDepth 5=EMTop 6=EMBottom 7=outlet */
    private fun mapSillyTavernPosition(position: Int): InjectionPosition {
        return when (position) {
            0 -> InjectionPosition.BEFORE_CHARACTER
            1 -> InjectionPosition.AFTER_CHARACTER
            2 -> InjectionPosition.AUTHOR_NOTE   // ANTop
            3 -> InjectionPosition.AUTHOR_NOTE   // ANBottom
            4 -> InjectionPosition.AT_DEPTH
            5 -> InjectionPosition.EM_TOP
            6 -> InjectionPosition.EM_BOTTOM
            7 -> InjectionPosition.OUTLET       // outlet：不直接注入，经 {{outlet::name}} 宏展开
            else -> InjectionPosition.AFTER_CHARACTER
        }
    }

    private fun extBool(extensions: JsonElement?, key: String): Boolean =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.booleanOrNull ?: false

    private fun extInt(extensions: JsonElement?, key: String): Int =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

    private fun extString(extensions: JsonElement?, key: String): String =
        extensions?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull ?: ""

    private fun extStringArray(extensions: JsonElement?, key: String): List<String> {
        val element = extensions?.jsonObject?.get(key) ?: return emptyList()
        return if (element is kotlinx.serialization.json.JsonArray) {
            element.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        } else {
            (element as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }
    }
}

@Serializable
private data class SillyTavernLorebook(
    val entries: Map<String, SillyTavernEntry> = emptyMap(),
)

@Serializable
private data class SillyTavernEntry(
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val content: String = "",
    val comment: String? = null,
    val constant: Boolean = false,
    val position: Int = 0,
    val order: Int = 100,
    val disable: Boolean = false,
    val depth: Int = 4,
    val scanDepth: Int? = null,
    val caseSensitive: Boolean? = null,
    val selective: Boolean = false,
    val selectiveLogic: Int = 0,
    val probability: Int? = 100,
    val useProbability: Boolean? = true,
    val group: String? = null,
    val groupWeight: Int? = 100,
    val groupOverride: Boolean? = null,
    val role: JsonElement? = null,
    val sticky: Int? = null,
    val cooldown: Int? = null,
    val delay: Int? = null,
    val excludeRecursion: Boolean? = null,
    val preventRecursion: Boolean? = null,
    val delayUntilRecursion: JsonElement? = null,
    val matchWholeWords: Boolean? = null,
    val extensions: JsonElement? = null,
)
