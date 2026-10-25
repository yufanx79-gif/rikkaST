package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.parseCardRegexScripts
import me.rerere.rikkahub.data.st.preset.SpresetCompat
import me.rerere.rikkahub.data.st.preset.StSpresetConfig
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

/**
 * 预设内嵌子字段（如 `extensions.regex_scripts`、`extensions.tavern_helper.scripts`）的解析状态：
 * - [PRESENT_VALID]：字段存在且为合法数组（可能非空，也可能为空数组 `[]`）→ 同名预设覆盖时替换该组旧条目；
 * - [ABSENT]：字段缺失（完整预设快照下，同名预设覆盖时同样清理该预设原先残留的旧条目）；
 * - [CORRUPTED]：字段存在但格式非法（如非数组、内部元素损坏）→ 严禁清空该预设已有的旧数据，保留旧条目并告警。
 */
enum class PresetSubfieldState {
    PRESENT_VALID,
    ABSENT,
    CORRUPTED,
}

/**
 * [P0-3 / P1-3 / P2 / D3] 酒馆预设整包导入产物：
 * 一次性读取并解析单个预设文件的全部通道，供调用方做**单次原子 Settings 更新**，
 * 根除旧实现分 3~4 次调 `vm.updateSettings(settings.copy(...))` 因组合期旧快照互相覆盖导致预设丢失的致命竞态。
 */
data class PresetImportBundle(
    val presetGroup: String,
    val injections: List<PromptInjection.ModeInjection>,
    val rawPresetJson: String?,
    val presetScripts: List<JsonElement>,
    val regexScripts: List<RegexScript>,
    val unsupportedExtensions: List<String>,
    val regexFieldState: PresetSubfieldState = PresetSubfieldState.PRESENT_VALID,
    val scriptsFieldState: PresetSubfieldState = PresetSubfieldState.PRESENT_VALID,
    val warnings: List<String> = emptyList(),
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
     * [第6轮 D3 · 无损透传] 读取酒馆预设文件的**原始 root JSON**。
     *
     * 为什么需要：实测用户给的 3 个预设，root 层各有 **44 个字段**（temperature / top_p /
     * openai_max_tokens / new_chat_prompt / new_example_chat_prompt / continue_nudge_prompt /
     * impersonation_prompt / scenario_format / personality_format / wi_format / group_nudge_prompt /
     * assistant_prefill / use_sysprompt / squash_system_messages / stream_openai …）以及
     * `extensions.SPreset`（第三方扩展配置）、`extensions.regex_scripts`（预设内嵌正则）。
     * 这些本轮**不逐个映射**（映射生成参数会污染用户的模型设置），但**一行都不许丢** ——
     * 原样存进 `Settings.tavernPresets`（同 tavernGlobalScripts / tavernPresetScripts 的无损透传范式），
     * 以后想补解析随时能取，也保证「导出→再导入」能还原。
     *
     * @return (预设组名, 原始 JSON 文本)；非酒馆预设形态（无 prompts 数组）返回 null。
     */
    fun readRawPreset(context: Context, uri: Uri): Pair<String, String>? = runCatching {
        val json = readUri(context, uri)
        val root = ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject
            ?: return@runCatching null
        if (root["prompts"] !is JsonArray) return@runCatching null
        val label = getUriFileName(context, uri)?.removeSuffix(".json") ?: "酒馆预设"
        label to json
    }.getOrNull()

    /**
     * B6：从酒馆预设文件中提取「酒馆助手」嵌入式脚本（extensions.tavern_helper.scripts）。
     * 组名与 [importList] 的 presetLabel 保持同一规则（文件名去 .json；兜底“酒馆预设”）。
     * @return (预设组名, 脚本数组)；无脚本或解析失败返回 null。
     */
    fun importPresetScripts(context: Context, uri: Uri): Pair<String, List<JsonElement>>? {
        return runCatching {
            val json = readUri(context, uri)
            val fileName = getUriFileName(context, uri)?.removeSuffix(".json")
            extractPresetScripts(json, fileName)
        }.getOrNull()
    }

    internal fun inspectPresetScripts(
        json: String,
        fileName: String?,
    ): Pair<PresetSubfieldState, List<JsonElement>> {
        val root = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject }.getOrNull()
            ?: return PresetSubfieldState.CORRUPTED to emptyList()
        val extEl = root["extensions"] ?: return PresetSubfieldState.ABSENT to emptyList()
        if (extEl is JsonNull) return PresetSubfieldState.ABSENT to emptyList()
        val extObj = extEl as? JsonObject ?: return PresetSubfieldState.CORRUPTED to emptyList()
        val helperEl = extObj["tavern_helper"] ?: return PresetSubfieldState.ABSENT to emptyList()
        if (helperEl is JsonNull) return PresetSubfieldState.ABSENT to emptyList()
        val helperObj = helperEl as? JsonObject ?: return PresetSubfieldState.CORRUPTED to emptyList()
        val scriptsEl = helperObj["scripts"] ?: return PresetSubfieldState.ABSENT to emptyList()
        if (scriptsEl is JsonNull) return PresetSubfieldState.ABSENT to emptyList()
        val arr = scriptsEl as? JsonArray ?: return PresetSubfieldState.CORRUPTED to emptyList()
        if (arr.any { it !is JsonObject }) return PresetSubfieldState.CORRUPTED to emptyList()
        return PresetSubfieldState.PRESENT_VALID to arr.toList()
    }

    internal fun extractPresetScripts(json: String, fileName: String?): Pair<String, List<JsonElement>>? {
        val label = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "酒馆预设"
        val (state, list) = inspectPresetScripts(json, label)
        if (state != PresetSubfieldState.PRESENT_VALID || list.isEmpty()) return null
        return label to list
    }

    /**
     * 严格检查预设内嵌正则（`extensions.regex_scripts` 及 `extensions.SPreset.RegexBinding.regexes`）：
     * - 区分字段缺失（[PresetSubfieldState.ABSENT]）、字段合法（[PresetSubfieldState.PRESENT_VALID]，含合法空数组 `[]`）、
     *   以及字段格式损坏（[PresetSubfieldState.CORRUPTED]）。
     */
    internal fun inspectPresetRegexScripts(
        json: String,
        fileName: String?,
    ): Pair<PresetSubfieldState, List<RegexScript>> {
        val label = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "酒馆预设"
        val root = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject }.getOrNull()
            ?: return PresetSubfieldState.CORRUPTED to emptyList()
        val extEl = root["extensions"] ?: return PresetSubfieldState.ABSENT to emptyList()
        if (extEl is JsonNull) return PresetSubfieldState.ABSENT to emptyList()
        val extensionsObj = extEl as? JsonObject ?: return PresetSubfieldState.CORRUPTED to emptyList()

        val rawRegexEl = extensionsObj["regex_scripts"] ?: extensionsObj["regexScripts"]
        val spresetRegexEl = ((extensionsObj["SPreset"] as? JsonObject)?.get("RegexBinding") as? JsonObject)
            ?.let { it["regexes"] ?: it["scripts"] }

        if (rawRegexEl == null && spresetRegexEl == null) {
            return PresetSubfieldState.ABSENT to emptyList()
        }
        val combinedRaw = mutableListOf<JsonElement>()
        if (rawRegexEl != null && rawRegexEl !is JsonNull) {
            val arr = rawRegexEl as? JsonArray ?: return PresetSubfieldState.CORRUPTED to emptyList()
            for (item in arr) {
                val obj = item as? JsonObject ?: return PresetSubfieldState.CORRUPTED to emptyList()
                val findRx = (obj["findRegex"] ?: obj["find_regex"]) as? JsonPrimitive
                if (findRx == null || !findRx.isString || findRx.contentOrNull.isNullOrBlank()) {
                    return PresetSubfieldState.CORRUPTED to emptyList()
                }
                combinedRaw += obj
            }
        }
        if (spresetRegexEl != null && spresetRegexEl !is JsonNull) {
            val arr = spresetRegexEl as? JsonArray ?: return PresetSubfieldState.CORRUPTED to emptyList()
            for (item in arr) {
                val obj = item as? JsonObject ?: return PresetSubfieldState.CORRUPTED to emptyList()
                val findRx = (obj["findRegex"] ?: obj["find_regex"]) as? JsonPrimitive
                if (findRx != null && findRx.isString && !findRx.contentOrNull.isNullOrBlank()) {
                    combinedRaw += obj
                }
            }
        }
        if (combinedRaw.isEmpty()) {
            return PresetSubfieldState.PRESENT_VALID to emptyList()
        }
        val syntheticExt = JsonObject(mapOf("regex_scripts" to JsonArray(combinedRaw)))
        val parsed = parseCardRegexScripts(syntheticExt)
        val tagged = parsed.map { s ->
            s.copy(
                id = s.id.ifBlank { Uuid.random().toString() },
                sourcePresetGroup = label,
            )
        }.distinctBy { it.id }
        return PresetSubfieldState.PRESENT_VALID to tagged
    }

    /**
     * [P1-3] 从酒馆预设文件中提取内嵌正则脚本（extensions.regex_scripts）。
     */
    fun importPresetRegexScripts(context: Context, uri: Uri): Pair<String, List<RegexScript>>? {
        return runCatching {
            val json = readUri(context, uri)
            val fileName = getUriFileName(context, uri)?.removeSuffix(".json")
            extractPresetRegexScripts(json, fileName)
        }.getOrNull()
    }

    internal fun extractPresetRegexScripts(json: String, fileName: String?): Pair<String, List<RegexScript>>? {
        val label = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "酒馆预设"
        val (state, tagged) = inspectPresetRegexScripts(json, label)
        if (state != PresetSubfieldState.PRESENT_VALID || tagged.isEmpty()) return null
        return label to tagged
    }

    /**
     * [P0-2 / P1-3] 将预设内嵌正则并入 `Settings.regexScripts`（完整预设快照语义）：
     * 1. [fieldState] 为 [PresetSubfieldState.CORRUPTED] 时：拒绝清空该预设已有的旧正则，原样保留并报错/告警；
     * 2. [fieldState] 为 [PresetSubfieldState.PRESENT_VALID] 或 [PresetSubfieldState.ABSENT] 时：
     *    清理该 `presetGroup` 先前残留的旧正则，并用本次导入的有效条目替换（若本次为空或字段移除，则该组旧正则被干净移除）；
     * 3. 绝不删除或覆盖用户手动创建的全局正则（`sourcePresetGroup == null`）或其他预设组的正则；
     * 4. 若预设正则 `id` 与用户已有正则或其他预设正则冲突，保留既有正则不变，并为当前预设正则派生作用域唯一 ID（`${presetGroup}::${id}`）。
     * @return (合并后的完整列表, 本次写入的预设正则条数)
     */
    fun mergePresetRegexScripts(
        existing: List<RegexScript>,
        presetGroup: String,
        imported: List<RegexScript>,
        fieldState: PresetSubfieldState = PresetSubfieldState.PRESENT_VALID,
    ): Pair<List<RegexScript>, Int> {
        if (presetGroup.isBlank() || fieldState == PresetSubfieldState.CORRUPTED) {
            return existing to 0
        }
        // 保留非本预设的所有条目（含用户自建 sourcePresetGroup == null 与其他预设的正则）
        val base = existing.filterNot { it.sourcePresetGroup == presetGroup }.toMutableList()
        if (imported.isEmpty()) {
            return base to 0
        }
        val occupiedIds = base.mapTo(mutableSetOf()) { it.id }
        val seenInCurrentPreset = mutableSetOf<String>()
        var addedCount = 0
        for (raw in imported) {
            val rawId = raw.id.ifBlank { Uuid.random().toString() }
            // 同预设内部重复 ID 去重
            if (!seenInCurrentPreset.add(rawId)) continue
            val finalId = if (rawId !in occupiedIds) {
                rawId
            } else {
                val scopedCandidate = "${presetGroup}::${rawId}"
                if (scopedCandidate !in occupiedIds) scopedCandidate else "${presetGroup}::${Uuid.random()}"
            }
            occupiedIds.add(finalId)
            base.add(raw.copy(id = finalId, sourcePresetGroup = presetGroup))
            addedCount++
        }
        return base to addedCount
    }

    /**
     * [P2 + SPreset] 检测酒馆预设中尚未完全支持的第三方私有扩展子键（已支持 ChatSquash / RegexBinding / MacroNest），
     * 仅对仍未支持的外部工具绑定（如 ToolBindings 等）提示告警。
     */
    fun detectUnsupportedPresetExtensions(context: Context, uri: Uri): List<String> = runCatching {
        detectUnsupportedPresetExtensionsFromJson(readUri(context, uri))
    }.getOrDefault(emptyList())

    internal fun detectUnsupportedPresetExtensionsFromJson(json: String): List<String> = runCatching {
        val root = ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject
            ?: return@runCatching emptyList()
        val ext = root["extensions"] as? JsonObject ?: return@runCatching emptyList()
        val out = mutableListOf<String>()
        val spreset = ext["SPreset"] as? JsonObject
        if (spreset != null && spreset.isNotEmpty()) {
            val supportedKeys = SpresetCompat.supportedKeys()
            val unsupportedKeys = spreset.keys.filterNot { it in supportedKeys }
            if (unsupportedKeys.isNotEmpty()) {
                out += "SPreset(${unsupportedKeys.take(6).joinToString(", ")})"
            }
        }
        out
    }.getOrDefault(emptyList())

    /**
     * [P0-3 / P1-3 / P2 / D3] 一次性读取并解析预设文件的全部内容（prompts + raw JSON + 预设脚本 + 内嵌正则 + 校验状态）。
     */
    fun importPresetBundle(context: Context, uri: Uri): Result<PresetImportBundle> = runCatching {
        val json = readUri(context, uri)
        val fileName = getUriFileName(context, uri)?.removeSuffix(".json")
        importPresetBundleFromString(json, fileName).getOrThrow()
    }

    internal fun importPresetBundleFromString(json: String, fileName: String?): Result<PresetImportBundle> = runCatching {
        val cleanName = fileName?.trim()?.removeSuffix(".json")?.trim()?.takeIf { it.isNotEmpty() }
        val stInjections = tryImportSillyTavernPreset(json, cleanName)
        if (stInjections != null) {
            val group = cleanName ?: "酒馆预设"
            val root = runCatching { ExportSerializer.DefaultJson.parseToJsonElement(json) as? JsonObject }.getOrNull()
            val hasPromptsArray = root?.get("prompts") is JsonArray
            val (scriptsState, scripts) = inspectPresetScripts(json, group)
            val (regexState, regexes) = inspectPresetRegexScripts(json, group)
            val unsupported = detectUnsupportedPresetExtensionsFromJson(json)
            val warnings = mutableListOf<String>()
            if (regexState == PresetSubfieldState.CORRUPTED) {
                warnings += "extensions.regex_scripts 格式损坏，已保留该预设原有正则"
            }
            if (scriptsState == PresetSubfieldState.CORRUPTED) {
                warnings += "extensions.tavern_helper.scripts 格式损坏，已保留该预设原有脚本"
            }
            PresetImportBundle(
                presetGroup = group,
                injections = stInjections,
                rawPresetJson = if (hasPromptsArray) json else null,
                presetScripts = scripts,
                regexScripts = regexes,
                unsupportedExtensions = unsupported,
                regexFieldState = regexState,
                scriptsFieldState = scriptsState,
                warnings = warnings,
            )
        } else {
            val native = tryImportNative(json)
                ?: throw IllegalArgumentException("不支持的格式。请选择从本应用导出的 JSON 或酒馆预设 JSON 文件")
            val group = native.presetGroup?.takeIf { it.isNotBlank() } ?: cleanName ?: "默认预设"
            PresetImportBundle(
                presetGroup = group,
                injections = listOf(native),
                rawPresetJson = null,
                presetScripts = emptyList(),
                regexScripts = emptyList(),
                unsupportedExtensions = emptyList(),
                regexFieldState = PresetSubfieldState.ABSENT,
                scriptsFieldState = PresetSubfieldState.ABSENT,
            )
        }
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
            // 缺失 prompt_order 时回退 prompts[] 原序（此时保留 prompt.enabled ?: true）。
            val orderGroups = stPreset.promptOrder.filter { it.order.isNotEmpty() }
            val orderList = (orderGroups.firstOrNull { it.characterId == ST_PRESET_DEFAULT_CHARACTER_ID }
                ?: orderGroups.firstOrNull())
                ?.order
            val chatHistoryOrderIdx = orderList?.indexOfFirst { it.identifier == "chatHistory" } ?: -1
            val charBlockMarkers = setOf("charDescription", "charPersonality", "scenario", "personaDescription")
            val hasCharBlockMarkers = orderList?.any { it.identifier in charBlockMarkers } == true
            val firstCharBlockIdx = orderList?.indexOfFirst { it.identifier in charBlockMarkers } ?: -1
            val lastCharBlockIdx = orderList?.indexOfLast { it.identifier in (charBlockMarkers + "worldInfoAfter") } ?: -1
            val worldInfoBeforeIdx = orderList?.indexOfFirst { it.identifier == "worldInfoBefore" } ?: -1
            val dialogueExamplesIdx = orderList?.indexOfFirst { it.identifier == "dialogueExamples" } ?: -1

            data class OrderedPromptSlot(
                val key: String,
                val enabled: Boolean,
                val afterChatHistory: Boolean,
                val relativeSlotHint: InjectionPosition?,
            )

            val orderedKeys: List<OrderedPromptSlot> = if (orderList != null) {
                val seen = mutableSetOf<String>()
                orderList.mapIndexedNotNull { idx, orderEntry ->
                    val key = orderEntry.identifier?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    if (!promptsByKey.containsKey(key) || !seen.add(key)) return@mapIndexedNotNull null
                    val afterChat = chatHistoryOrderIdx > 0 && idx > chatHistoryOrderIdx
                    val slotHint = if (!hasCharBlockMarkers) {
                        null
                    } else when {
                        afterChat -> InjectionPosition.AFTER_DIALOG
                        dialogueExamplesIdx >= 0 && idx > dialogueExamplesIdx &&
                            (chatHistoryOrderIdx < 0 || idx < chatHistoryOrderIdx) -> InjectionPosition.EM_BOTTOM
                        lastCharBlockIdx >= 0 && idx > lastCharBlockIdx &&
                            (dialogueExamplesIdx < 0 || idx < dialogueExamplesIdx) -> InjectionPosition.AFTER_CHARACTER
                        worldInfoBeforeIdx >= 0 && firstCharBlockIdx > worldInfoBeforeIdx &&
                            idx in (worldInfoBeforeIdx + 1) until firstCharBlockIdx -> InjectionPosition.BEFORE_CHARACTER
                        else -> null
                    }
                    OrderedPromptSlot(
                        key = key,
                        enabled = orderEntry.enabled ?: promptsByKey[key]?.enabled ?: true,
                        afterChatHistory = afterChat,
                        relativeSlotHint = slotHint,
                    )
                }
            } else {
                promptsByKey.map { (k, p) ->
                    OrderedPromptSlot(k, p.enabled ?: true, false, null)
                }
            }

            val presetLabel = fileName ?: "酒馆预设"
            val injections = mutableListOf<PromptInjection.ModeInjection>()
            orderedKeys.forEach { (key, orderEnabled, afterChatHistory, slotHint) ->
                val prompt = promptsByKey[key] ?: return@forEach
                // marker = true 的系统占位条目无实际内容，跳过
                if (prompt.marker) return@forEach
                val content = prompt.content.orEmpty()
                if (content.isBlank()) return@forEach
                val stRole = mapSillyTavernPromptRole(prompt.role)
                val basePos = mapSillyTavernPresetPosition(
                    injectionPosition = prompt.injectionPosition,
                    role = stRole,
                    systemPrompt = prompt.systemPrompt == true,
                    afterChatHistory = afterChatHistory,
                )
                val finalPos = if (prompt.injectionPosition != 1 && slotHint != null) slotHint else basePos
                val deterministicId = Uuid.parse(
                    java.util.UUID.nameUUIDFromBytes("$presetLabel::$key".toByteArray(Charsets.UTF_8)).toString()
                )
                injections += PromptInjection.ModeInjection(
                    id = deterministicId,
                    name = prompt.name?.takeIf { it.isNotBlank() }
                        ?: prompt.identifier?.takeIf { it.isNotBlank() }
                        ?: "$presetLabel - 条目${injections.size + 1}",
                    enabled = orderEnabled,
                    priority = injections.size,
                    position = finalPos,
                    content = content.trim(),
                    injectDepth = prompt.injectionDepth ?: 4,
                    role = stRole,
                    presetGroup = presetLabel,
                    presetSystemPrompt = prompt.systemPrompt == true,
                    injectionOrder = prompt.injectionOrder ?: 100,
                    triggers = parseInjectionTriggers(prompt.injectionTrigger),
                    forbidOverrides = prompt.forbidOverrides == true,
                )
            }
            if (injections.isEmpty()) return null
            // 官方 ST 语义：相对位置条目（position != AT_DEPTH）严格保持 prompt_order 的先后顺序，
            // 仅同深度下的 AT_DEPTH 条目按 injectionOrder 排序。
            val result = injections.toMutableList()
            val atDepthByDepth = result.withIndex()
                .filter { it.value.position == InjectionPosition.AT_DEPTH }
                .groupBy { it.value.injectDepth }
            for ((_, indexedList) in atDepthByDepth) {
                if (indexedList.size <= 1) continue
                val indices = indexedList.map { it.index }
                val sortedAtDepth = indexedList.map { it.value }
                    .sortedWith(compareBy({ it.injectionOrder }, { it.priority }))
                indices.zip(sortedAtDepth).forEach { (slotIdx, item) ->
                    result[slotIdx] = item
                }
            }
            result.mapIndexed { index, inj -> inj.copy(priority = index) }
        }.getOrNull()
    }

    /**
     * 酒馆预设注入位置映射（与官方 PromptManager.js / openai.js 语义对齐）。
     * absolute（1）= 按深度插入聊天；
     * relative（0）或缺失：若在 prompt_order 中位于 chatHistory 之后，则归入 AFTER_DIALOG（历史后）；
     * 否则按角色归入系统块 / 聊天顶部。
     */
    internal fun mapSillyTavernPresetPosition(
        injectionPosition: Int?,
        role: MessageRole,
        systemPrompt: Boolean = false,
        afterChatHistory: Boolean = false,
    ): InjectionPosition = when {
        injectionPosition == 1 -> InjectionPosition.AT_DEPTH
        afterChatHistory -> InjectionPosition.AFTER_DIALOG
        systemPrompt || role == MessageRole.SYSTEM -> InjectionPosition.AFTER_SYSTEM_PROMPT
        else -> InjectionPosition.TOP_OF_CHAT
    }

    /** 酒馆 role 字符串 → [MessageRole]，缺失/非法按 SYSTEM 处理（同时用于位置判定与条目角色） */
    internal fun mapSillyTavernPromptRole(role: String?): MessageRole = when (role?.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    }

    /** 成员方法重载：解析激活酒馆预设根级配置（兼容 JSON 字符串桶或预设 Map） */
    fun parseActiveStPresetConfig(
        tavernPresetsJson: String?,
        enabledGroups: Set<String>,
    ): StActivePresetConfig = me.rerere.rikkahub.data.export.parseActiveStPresetConfig(tavernPresetsJson, enabledGroups)

    fun parseActiveStPresetConfig(
        rawPresets: Map<String, String>,
        enabledGroups: Set<String>,
    ): StActivePresetConfig {
        if (rawPresets.isEmpty() || enabledGroups.isEmpty()) return StActivePresetConfig()
        val json = kotlinx.serialization.json.buildJsonObject {
            for ((k, v) in rawPresets) {
                put(k, JsonPrimitive(v))
            }
        }.toString()
        return me.rerere.rikkahub.data.export.parseActiveStPresetConfig(json, enabledGroups)
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
    // ===== [第6轮 D3] 以下 4 个字段旧版**一个都没读**，是「导进来字段就没了」的直接原因。
    // 实测用户给的 3 个预设：system_prompt 118/66/144 条、forbid_overrides 118/66/142、
    // injection_order 112/66/80、injection_trigger 3/66/79。全部可选解析，缺省不报错。
    @SerialName("system_prompt")
    val systemPrompt: Boolean? = null,
    @SerialName("injection_order")
    val injectionOrder: Int? = null,
    @SerialName("forbid_overrides")
    val forbidOverrides: Boolean? = null,
    @SerialName("injection_trigger")
    val injectionTrigger: JsonElement? = null,
)

/**
 * [第6轮 D3] ST `injection_trigger` → List<String>。
 * ST 该字段形态不固定：字符串 / 字符串数组 / 缺失（实测三种都存在）。
 */
private fun parseInjectionTriggers(el: JsonElement?): List<String> {
    return when (el) {
        null -> emptyList()
        is JsonNull -> emptyList()
        is JsonPrimitive -> {
            val raw = el.contentOrNull?.takeIf { it.isNotBlank() } ?: return emptyList()
            // ST 预设把数组存成「JSON 字符串」形式（如 custom_stopping_strings: "[\"\\nUser:\", ...]"），
            // 必须二次解析并把 \n 等转义还原成真实字符，否则整段 JSON 文本会被当成单个停止词传给模型。
            parseJsonEncodedStringArray(raw) ?: listOf(raw)
        }
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        else -> emptyList()
    }
}

/** 解析「JSON 数组字符串」形式；非该形式或解析失败返回 null（调用方回退为单元素列表）。 */
private fun parseJsonEncodedStringArray(raw: String): List<String>? {
    if (!raw.trimStart().startsWith("[")) return null
    val arr = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonArray ?: return null
    val items = arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
    return items.ifEmpty { null }
}

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

    /**
     * 导出为官方 SillyTavern 原生世界书 JSON（`{ "entries": { "0": { ... } } }`），支持与酒馆双向无损互导。
     */
    fun exportToSillyTavernJson(data: Lorebook): String {
        val entriesObj = kotlinx.serialization.json.buildJsonObject {
            data.entries.forEachIndexed { index, entry ->
                val stPos = when (entry.position) {
                    InjectionPosition.BEFORE_CHARACTER, InjectionPosition.BEFORE_SYSTEM_PROMPT -> 0
                    InjectionPosition.AFTER_CHARACTER, InjectionPosition.AFTER_SYSTEM_PROMPT -> 1
                    InjectionPosition.AUTHOR_NOTE, InjectionPosition.BEFORE_AUTHOR_NOTE, InjectionPosition.TOP_OF_CHAT -> 2
                    InjectionPosition.AFTER_AUTHOR_NOTE, InjectionPosition.BOTTOM_OF_CHAT -> 3
                    InjectionPosition.AT_DEPTH -> 4
                    InjectionPosition.EM_TOP -> 5
                    InjectionPosition.EM_BOTTOM -> 6
                    InjectionPosition.OUTLET, InjectionPosition.ANTAGONIZE, InjectionPosition.AFTER_DIALOG -> 7
                }
                val stRole = when (entry.role) {
                    MessageRole.USER -> 1
                    MessageRole.ASSISTANT -> 2
                    else -> 0
                }
                val logicInt = when (entry.selectiveLogic) {
                    SelectiveLogic.NOT_ALL -> 1
                    SelectiveLogic.NOT_ANY -> 2
                    SelectiveLogic.AND_ALL -> 3
                    SelectiveLogic.AND_ANY, SelectiveLogic.OR_ANY -> 0
                }
                put(
                    index.toString(),
                    kotlinx.serialization.json.buildJsonObject {
                        put("uid", JsonPrimitive(index))
                        put("key", kotlinx.serialization.json.JsonArray(entry.keywords.map { JsonPrimitive(it) }))
                        put("keysecondary", kotlinx.serialization.json.JsonArray(entry.secondaryKeys.map { JsonPrimitive(it) }))
                        put("comment", JsonPrimitive(entry.name))
                        put("content", JsonPrimitive(entry.content))
                        put("constant", JsonPrimitive(entry.constantActive))
                        put("selective", JsonPrimitive(entry.selective))
                        put("selectiveLogic", JsonPrimitive(logicInt))
                        put("order", JsonPrimitive(entry.priority))
                        put("position", JsonPrimitive(stPos))
                        put("disable", JsonPrimitive(!entry.enabled))
                        put("excludeRecursion", JsonPrimitive(entry.excludeRecursion))
                        put("preventRecursion", JsonPrimitive(entry.preventRecursion))
                        put("delayUntilRecursion", JsonPrimitive(entry.delayUntilRecursion))
                        put("probability", JsonPrimitive(entry.probability))
                        put("useProbability", JsonPrimitive(entry.useProbability))
                        put("depth", JsonPrimitive(entry.injectDepth))
                        put("group", JsonPrimitive(entry.group))
                        put("groupOverride", JsonPrimitive(entry.groupOverride))
                        put("groupWeight", JsonPrimitive(entry.groupWeight))
                        if (entry.scanDepth != null) put("scanDepth", JsonPrimitive(entry.scanDepth))
                        put("caseSensitive", JsonPrimitive(entry.caseSensitive))
                        put("matchWholeWords", JsonPrimitive(entry.matchWholeWords))
                        put("useGroupScoring", JsonPrimitive(entry.useGroupScoring))
                        put("automationId", JsonPrimitive(entry.automationId))
                        put("role", JsonPrimitive(stRole))
                        put("sticky", JsonPrimitive(entry.sticky))
                        put("cooldown", JsonPrimitive(entry.cooldown))
                        put("delay", JsonPrimitive(entry.delay))
                        put("ignoreBudget", JsonPrimitive(entry.ignoreBudget))
                        put("outletName", JsonPrimitive(entry.outletName))
                        put("vectorized", JsonPrimitive(entry.vectorized))
                        put("addMemo", JsonPrimitive(entry.addMemo))
                        if (entry.characterFilterNames.isNotEmpty() || entry.characterFilterTags.isNotEmpty()) {
                            put("characterFilter", kotlinx.serialization.json.buildJsonObject {
                                put("isExclude", JsonPrimitive(entry.characterFilterIsExclude))
                                put("names", kotlinx.serialization.json.JsonArray(entry.characterFilterNames.map { JsonPrimitive(it) }))
                                put("tags", kotlinx.serialization.json.JsonArray(entry.characterFilterTags.map { JsonPrimitive(it) }))
                            })
                        }
                    }
                )
            }
        }
        return kotlinx.serialization.json.buildJsonObject {
            put("entries", entriesObj)
        }.toString()
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
                        vectorized = entry.vectorized ?: extBool(entry.extensions, "vectorized"),
                        addMemo = entry.addMemo ?: true,
                        characterFilterNames = parseFilterList(entry.characterFilter ?: entry.extensions?.jsonObject?.get("character_filter"), "names"),
                        characterFilterTags = parseFilterList(entry.characterFilter ?: entry.extensions?.jsonObject?.get("character_filter"), "tags"),
                        characterFilterIsExclude = parseFilterExclude(entry.characterFilter ?: entry.extensions?.jsonObject?.get("character_filter")),
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
            2 -> InjectionPosition.BEFORE_AUTHOR_NOTE   // ANTop
            3 -> InjectionPosition.AFTER_AUTHOR_NOTE    // ANBottom
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

    private fun parseFilterList(el: JsonElement?, key: String): List<String> {
        val obj = el as? JsonObject ?: return emptyList()
        val arr = obj[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }

    private fun parseFilterExclude(el: JsonElement?): Boolean {
        val obj = el as? JsonObject ?: return false
        return ((obj["isExclude"] ?: obj["is_exclude"]) as? JsonPrimitive)?.booleanOrNull == true
    }

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

/**
 * 将 Lorebook 直接导出为官方 SillyTavern 世界书 JSON（`{ "entries": { "0": { ... } } }`）的序列化器。
 */
object SillyTavernLorebookSerializer : ExportSerializer<Lorebook> {
    override val type: String = "sillytavern_lorebook"

    override fun getExportFileName(data: Lorebook): String {
        return "${data.name.ifEmpty { "worldinfo" }}.st.json"
    }

    override fun export(data: Lorebook): ExportData = LorebookSerializer.export(data)

    override fun exportToJson(data: Lorebook, json: Json): String {
        return LorebookSerializer.exportToSillyTavernJson(data)
    }

    override fun import(context: Context, uri: Uri): Result<Lorebook> = LorebookSerializer.import(context, uri)
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
    val selective: Boolean = true,
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
    val vectorized: Boolean? = null,
    val addMemo: Boolean? = null,
    val characterFilter: JsonElement? = null,
    val extensions: JsonElement? = null,
)


/**
 * 激活酒馆预设的根级模板与格式化配置（对齐 SillyTavern openai.js default_settings / preparePromptsForChatCompletion）。
 */
data class StActivePresetConfig(
    val wiFormat: String? = null,
    val scenarioFormat: String? = null,
    val personalityFormat: String? = null,
    val newChatPrompt: String? = null,
    val newGroupChatPrompt: String? = null,
    val newExampleChatPrompt: String? = null,
    val continueNudgePrompt: String? = null,
    val impersonationPrompt: String? = null,
    val groupNudgePrompt: String? = null,
    val assistantPrefill: String? = null,
    val continuePostfix: String? = null,
    val squashSystemMessages: Boolean = false,
    val forbidOverridesMain: Boolean = false,
    val forbidOverridesJailbreak: Boolean = false,
    /** 预设中 main 条目的原始内容（若存在且启用） */
    val mainPromptContent: String? = null,
    /** 预设中 jailbreak / nsfw 条目的原始内容（若存在且启用） */
    val jailbreakPromptContent: String? = null,
    /** 预设中 openai_max_context / max_context 上下文窗口上限（供世界书百分比预算计算） */
    val maxContext: Int? = null,
    /** prompt_order 中启用的内置插槽顺序（如 charDescription, charPersonality, scenario 等） */
    val orderedMarkers: List<String> = emptyList(),
    /** prompt_order 中被显式禁用的内置插槽集合（如 enabled=false 的 charPersonality / scenario / dialogueExamples / worldInfoBefore 等） */
    val disabledMarkers: Set<String> = emptySet(),
    /** extensions.SPreset.ChatSquash 分隔符（启用 ChatSquash 时生效） */
    val spresetChatSquashSeparator: String = "\n\n",
    /** extensions.SPreset.RegexBinding 强制启用的正则 ID 或名称集合 */
    val spresetEnabledRegexIds: Set<String> = emptySet(),
    /** extensions.SPreset.RegexBinding 强制禁用的正则 ID 或名称集合 */
    val spresetDisabledRegexIds: Set<String> = emptySet(),
    /** extensions.SPreset.MacroNest 嵌套宏展开最大轮次 */
    val spresetMacroNestDepth: Int = 0,
    /** extensions.SPreset 全量配置（ToolBindings / MessageInjections / OutputPreprocessing / ...） */
    val spresetConfig: StSpresetConfig = StSpresetConfig(),
    /** 预设中的 logit_bias 映射（Token ID/文本 -> 偏置值 -100..100） */
    val logitBias: Map<String, Float> = emptyMap(),
    /** 预设中的 guidance_scale / cfg_scale（默认 1.0f） */
    val cfgScale: Float = 1.0f,
    /** 预设中的 negative_prompt（CFG 负向提示词） */
    val negativePrompt: String = "",
    /** 预设中的 custom_stopping_strings / stopping_strings */
    val customStoppingStrings: List<String> = emptyList(),
    /** 预设中的 prompt_post_processing 策略 */
    val promptPostProcessing: String = "",
    /** 预设中的 start_reply_with 强制回复前缀 */
    val startReplyWith: String = "",
    /** 预设中的采样与生成控制参数（当助手未单独覆盖时作为默认采样参数传入生成请求） */
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val topA: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxTokens: Int? = null,
    val seed: Int = -1,
    val wrapInQuotes: Boolean = false,
)

/**
 * 从 `Settings.tavernPresets`（`{"预设名": "<raw json>"}`）中解析当前已启用预设组的根级字段、
 * `prompt_order` 插槽顺序/禁用状态以及 `extensions.SPreset` 配置。
 */
fun parseActiveStPresetConfig(
    tavernPresetsJson: String?,
    enabledGroups: Set<String>,
): StActivePresetConfig {
    if (tavernPresetsJson.isNullOrBlank() || enabledGroups.isEmpty()) return StActivePresetConfig()
    return runCatching {
        val mapObj = ExportSerializer.DefaultJson.parseToJsonElement(tavernPresetsJson) as? JsonObject
            ?: return@runCatching StActivePresetConfig()
        val root = enabledGroups.firstNotNullOfOrNull { g ->
            when (val el = mapObj[g]) {
                is JsonObject -> el
                is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }?.let { rawStr ->
                    runCatching { ExportSerializer.DefaultJson.parseToJsonElement(rawStr) as? JsonObject }.getOrNull()
                }
                else -> null
            }
        } ?: return@runCatching StActivePresetConfig()

        fun str(key: String): String? = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun bool(key: String): Boolean = (root[key] as? JsonPrimitive)?.booleanOrNull == true

        val promptsArr = root["prompts"] as? JsonArray
        var forbidMain = false
        var forbidJb = false
        var mainContent: String? = null
        var jbContent: String? = null
        promptsArr?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = (o["identifier"] as? JsonPrimitive)?.contentOrNull
            val fob = (o["forbid_overrides"] as? JsonPrimitive)?.booleanOrNull == true
            val c = (o["content"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            if (id == "main") {
                if (fob) forbidMain = true
                if (c != null) mainContent = c
            }
            if (id == "jailbreak" || id == "nsfw") {
                if (fob) forbidJb = true
                if (c != null && (jbContent == null || id == "jailbreak")) jbContent = c
            }
        }
        val presetMaxContext = (root["openai_max_context"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: (root["max_context"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

        val knownMarkers = setOf(
            "main", "worldInfoBefore", "personaDescription", "charDescription",
            "charPersonality", "scenario", "enhanceDefinitions", "nsfw",
            "worldInfoAfter", "dialogueExamples", "chatHistory", "jailbreak",
        )
        val orderedMarkers = mutableListOf<String>()
        val disabledMarkers = mutableSetOf<String>()
        val promptOrderArr = root["prompt_order"] as? JsonArray
        val activeOrderObj = promptOrderArr
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { (it["character_id"] as? JsonPrimitive)?.contentOrNull == "100001" }
            ?: promptOrderArr?.firstOrNull() as? JsonObject
        (activeOrderObj?.get("order") as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = (o["identifier"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val en = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true
            if (id in knownMarkers) {
                if (en) orderedMarkers += id else disabledMarkers += id
            }
        }

        // 解析 extensions.SPreset（ChatSquash / RegexBinding / MacroNest）
        val spreset = (root["extensions"] as? JsonObject)?.get("SPreset") as? JsonObject
        val spresetConfig = SpresetCompat.parse(spreset)
        val chatSquashObj = spreset?.get("ChatSquash") as? JsonObject
        val spresetSquash = (chatSquashObj?.get("enabled") as? JsonPrimitive)?.booleanOrNull == true ||
            (spreset?.get("chat_squash") as? JsonPrimitive)?.booleanOrNull == true
        val spresetSeparator = (chatSquashObj?.get("separator") as? JsonPrimitive)?.contentOrNull ?: "\n\n"

        val regexBindingObj = spreset?.get("RegexBinding") as? JsonObject
        val spresetEnabledRx = mutableSetOf<String>()
        val spresetDisabledRx = mutableSetOf<String>()
        (regexBindingObj?.get("enabled") as? JsonArray)?.forEach { el ->
            (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { spresetEnabledRx += it }
        }
        (regexBindingObj?.get("disabled") as? JsonArray)?.forEach { el ->
            (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { spresetDisabledRx += it }
        }
        (regexBindingObj?.get("bindings") as? JsonObject)?.forEach { (rxId, stateEl) ->
            val en = (stateEl as? JsonPrimitive)?.booleanOrNull
                ?: ((stateEl as? JsonObject)?.get("enabled") as? JsonPrimitive)?.booleanOrNull
            if (en == true) spresetEnabledRx += rxId
            else if (en == false) spresetDisabledRx += rxId
        }

        val macroNestObj = spreset?.get("MacroNest") as? JsonObject
        val macroNestDepth = if ((macroNestObj?.get("enabled") as? JsonPrimitive)?.booleanOrNull == true) {
            (macroNestObj["maxDepth"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceIn(1, 5) ?: 2
        } else 0

        StActivePresetConfig(
            wiFormat = str("wi_format"),
            scenarioFormat = str("scenario_format"),
            personalityFormat = str("personality_format"),
            newChatPrompt = str("new_chat_prompt"),
            newGroupChatPrompt = str("new_group_chat_prompt"),
            newExampleChatPrompt = str("new_example_chat_prompt"),
            continueNudgePrompt = str("continue_nudge_prompt"),
            impersonationPrompt = str("impersonation_prompt"),
            groupNudgePrompt = str("group_nudge_prompt"),
            assistantPrefill = str("assistant_prefill"),
            continuePostfix = str("continue_postfix"),
            squashSystemMessages = bool("squash_system_messages") || spresetSquash,
            forbidOverridesMain = forbidMain,
            forbidOverridesJailbreak = forbidJb,
            mainPromptContent = if ("main" in disabledMarkers) null else mainContent,
            jailbreakPromptContent = if ("jailbreak" in disabledMarkers) null else jbContent,
            maxContext = presetMaxContext,
            orderedMarkers = orderedMarkers,
            disabledMarkers = disabledMarkers,
            spresetChatSquashSeparator = spresetSeparator,
            spresetEnabledRegexIds = spresetEnabledRx,
            spresetDisabledRegexIds = spresetDisabledRx,
            spresetMacroNestDepth = macroNestDepth,
            spresetConfig = spresetConfig,
            logitBias = parsePresetLogitBias(root["logit_bias"]),
            cfgScale = (root["guidance_scale"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: (root["cfg_scale"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: 1.0f,
            negativePrompt = str("negative_prompt").orEmpty(),
            customStoppingStrings = parseInjectionTriggers(root["custom_stopping_strings"] ?: root["stopping_strings"]),
            promptPostProcessing = str("prompt_post_processing").orEmpty(),
            startReplyWith = str("start_reply_with").orEmpty(),
            temperature = (root["temperature"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: (root["temp"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            topP = (root["top_p"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            topK = (root["top_k"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            minP = (root["min_p"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            topA = (root["top_a"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            frequencyPenalty = (root["frequency_penalty"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: (root["freq_pen"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            presencePenalty = (root["presence_penalty"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: (root["pres_pen"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            repetitionPenalty = (root["repetition_penalty"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
                ?: (root["rep_pen"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull(),
            maxTokens = (root["openai_max_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: (root["max_tokens"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            seed = (root["seed"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1,
            wrapInQuotes = bool("wrap_in_quotes"),
        )
    }.getOrDefault(StActivePresetConfig())
}


private fun parsePresetLogitBias(el: JsonElement?): Map<String, Float> {
    if (el == null) return emptyMap()
    val out = LinkedHashMap<String, Float>()
    when (el) {
        is JsonArray -> el.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: return@forEach
            val value = (obj["value"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: return@forEach
            out[text] = value
        }
        is JsonObject -> el.forEach { (k, v) ->
            val num = (v as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: return@forEach
            out[k] = num
        }
        else -> {}
    }
    return out
}
