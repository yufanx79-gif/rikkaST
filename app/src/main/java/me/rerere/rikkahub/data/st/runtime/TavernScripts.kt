package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.data.model.TavernBookEntry
import me.rerere.rikkahub.data.model.TavernEmbeddedBook
import me.rerere.ai.core.MessageRole
import kotlinx.serialization.json.intOrNull

/**
 * 「酒馆助手」（JS-Slash-Runner）脚本桥 + 三源脚本合并（B6）。
 *
 * 数据流（对齐 JSR 三来源语义）：
 * - global（全局脚本）：`Settings.tavernGlobalScripts`（JSON 数组字符串）+ 总开关 `tavernGlobalScriptsEnabled`；
 * - character（角色卡脚本）：`Assistant.tavernData.extensionsRaw`（"data.extensions" 原始 JSON）内 `tavern_helper.scripts`；
 * - preset（预设脚本）：`Settings.tavernPresetScripts`（{"预设组名": [script, ...]}），
 *   仅当当前助手已启用该预设组（`modeInjectionIds` 含该组条目）时装载。
 *
 * 合并结果经 [TavernRuntimeManager.TavernJsBridge.getTavernScriptsJson] 透传给运行时；
 * runtime.js 按 JSR 约定把 `type == "script" && enabled` 的脚本装载到 `TH-script--<name>--<id>` iframe。
 */

/** 脚本简要信息（UI 展示模型，folder 已展开）。 */
data class TavernScriptBrief(
    val id: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val info: String,
    val content: String,
)

// ============================================================
// 装载：三源合并（runtime.js 消费）
// ============================================================

fun buildTavernScriptsJson(assistant: Assistant?, settings: Settings?): String {
    val helper = cardHelper(assistant)
    val merged = buildJsonArray {
        // 1) 全局脚本（JSR global；总开关关闭则跳过）
        if (settings?.tavernGlobalScriptsEnabled != false) {
            flattenTree(parseScriptItems(settings?.tavernGlobalScripts)).forEach { add(it) }
        }
        // 2) 卡内脚本（JSR character）
        (helper?.get("scripts") as? JsonArray)?.let { arr ->
            flattenTree(arr.toList()).forEach { add(it) }
        }
        // 3) 预设脚本（JSR preset；仅装载「当前助手已启用预设组」旗下的）
        val groups = enabledPresetGroups(assistant, settings)
        if (groups.isNotEmpty()) {
            val buckets = parsePresetBucketsRaw(settings?.tavernPresetScripts)
            groups.forEach { g ->
                flattenTree(buckets[g].orEmpty()).forEach { add(it) }
            }
        }
    }
    return buildJsonObject {
        put(
            "tavern_helper",
            buildJsonObject {
                put("scripts", merged)
                put("variables", (helper?.get("variables") as? JsonObject) ?: JsonObject(emptyMap()))
            },
        )
    }.toString()
}

/** 当前助手已启用的预设组集合（来自 modeInjectionIds → presetGroup 推导）。 */
fun enabledPresetGroups(assistant: Assistant?, settings: Settings?): Set<String> {
    if (assistant == null || settings == null) return emptySet()
    val ids = assistant.modeInjectionIds
    return settings.modeInjections
        .filter { it.id in ids && !it.presetGroup.isNullOrBlank() }
        .mapNotNull { it.presetGroup }
        .toSet()
}

// ============================================================
// UI 列表
// ============================================================

fun listCardScripts(assistant: Assistant?): List<TavernScriptBrief> {
    val helper = cardHelper(assistant) ?: return emptyList()
    val scripts = helper["scripts"] as? JsonArray ?: return emptyList()
    return briefsOf(scripts.toList())
}

fun listGlobalScripts(scriptsJson: String?): List<TavernScriptBrief> =
    briefsOf(parseScriptItems(scriptsJson))

fun listPresetScriptBuckets(scriptsJson: String?): Map<String, List<TavernScriptBrief>> =
    parsePresetBucketsRaw(scriptsJson).mapValues { (_, v) -> briefsOf(v) }

// ============================================================
// 写回辅助（UI 开关 / 导入 / 删除）
// ============================================================

/** 卡内脚本开关：返回新的 extensionsRaw；解析失败或未找到返回 null。 */
fun setCardScriptEnabled(extensionsRaw: String?, scriptId: String, enabled: Boolean): String? {
    if (extensionsRaw.isNullOrBlank() || scriptId.isBlank()) return null
    return try {
        val root = Json.parseToJsonElement(extensionsRaw) as? JsonObject ?: return null
        val helper = root["tavern_helper"] as? JsonObject ?: return null
        val scripts = helper["scripts"] as? JsonArray ?: return null
        val (newList, found) = updateEnabledInItems(scripts.toList(), scriptId, enabled)
        if (!found) return null
        root.withField("tavern_helper", helper.withField("scripts", JsonArray(newList))).toString()
    } catch (_: Exception) {
        null
    }
}

/** 全局脚本开关：返回新的全局脚本 JSON；未找到返回 null。 */
fun setGlobalScriptEnabled(globalJson: String?, scriptId: String, enabled: Boolean): String? {
    if (scriptId.isBlank()) return null
    val items = parseScriptItems(globalJson)
    val (newList, found) = updateEnabledInItems(items, scriptId, enabled)
    if (!found) return null
    return JsonArray(newList).toString()
}

/** 预设脚本开关：返回新的预设脚本 JSON；未找到返回 null。 */
fun setPresetScriptEnabled(presetJson: String?, group: String, scriptId: String, enabled: Boolean): String? {
    if (group.isBlank() || scriptId.isBlank()) return null
    return try {
        val root = (presetJson?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it) } as? JsonObject)
            ?: return null
        val bucket = root[group] ?: return null
        val items = when (bucket) {
            is JsonArray -> bucket.toList()
            is JsonObject -> (bucket["scripts"] as? JsonArray)?.toList().orEmpty()
            else -> return null
        }
        val (newList, found) = updateEnabledInItems(items, scriptId, enabled)
        if (!found) return null
        root.withField(group, JsonArray(newList)).toString()
    } catch (_: Exception) {
        null
    }
}

/** 追加全局脚本（按 id 去重）；返回 (新 JSON, 新增数量)。 */
fun appendGlobalScripts(globalJson: String?, imported: List<JsonElement>): Pair<String, Int> {
    val existing = parseScriptItems(globalJson).toMutableList()
    val existingIds = existing
        .mapNotNull { (it as? JsonObject)?.str("id")?.takeIf { s -> s.isNotBlank() } }
        .toSet()
    var added = 0
    imported.forEach { el ->
        val obj = el as? JsonObject ?: return@forEach
        val id = obj.str("id")
        if (id.isNotBlank() && id in existingIds) return@forEach
        existing += el
        added++
    }
    return JsonArray(existing).toString() to added
}

/** 删除一个全局脚本（按 id）；未找到返回 null。 */
fun removeGlobalScript(globalJson: String?, scriptId: String): String? {
    if (scriptId.isBlank()) return null
    val items = parseScriptItems(globalJson)
    val newList = items.filterNot { (it as? JsonObject)?.str("id") == scriptId }
    if (newList.size == items.size) return null
    return JsonArray(newList).toString()
}

/** 合并一个预设组下的脚本（同名组整体替换，重复导入即更新）。 */
fun mergePresetScripts(presetJson: String?, group: String, scripts: List<JsonElement>): String {
    val root = try {
        (presetJson?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it) } as? JsonObject)
            ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
    return root.withField(group, JsonArray(scripts)).toString()
}

/**
 * 从任意「酒馆助手脚本」JSON 文本中提取脚本列表，兼容格式：
 * `[script, ...]` / `{"scripts":[...]}` / `{"tavern_helper":{"scripts":[...]}}` / 单个脚本对象。
 */
fun extractScriptsFromJson(text: String): List<JsonElement> {
    if (text.isBlank()) return emptyList()
    return try {
        when (val el = Json.parseToJsonElement(text)) {
            is JsonArray -> el.toList()
            is JsonObject -> {
                val helperScripts = (el["tavern_helper"] as? JsonObject)?.get("scripts") as? JsonArray
                val directScripts = el["scripts"] as? JsonArray
                when {
                    helperScripts != null -> helperScripts.toList()
                    directScripts != null -> directScripts.toList()
                    el["type"] != null -> listOf(el)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// ============================================================
// 内部工具
// ============================================================

private fun cardHelper(assistant: Assistant?): JsonObject? {
    val raw = assistant?.tavernData?.extensionsRaw ?: return null
    if (raw.isBlank()) return null
    return try {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: return null
        root["tavern_helper"] as? JsonObject
    } catch (_: Exception) {
        null
    }
}

/** 解析脚本数组：兼容 `[...]` 与 `{"scripts":[...]}` 两种形态。 */
private fun parseScriptItems(json: String?): List<JsonElement> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        when (val el = Json.parseToJsonElement(json)) {
            is JsonArray -> el.toList()
            is JsonObject -> (el["scripts"] as? JsonArray)?.toList().orEmpty()
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 解析预设脚本桶：{"组名": [script, ...]}。 */
private fun parsePresetBucketsRaw(json: String?): Map<String, List<JsonElement>> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        root.mapValues { (_, v) ->
            when (v) {
                is JsonArray -> v.toList()
                is JsonObject -> (v["scripts"] as? JsonArray)?.toList().orEmpty()
                else -> emptyList()
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

/** ScriptTree[] → 平铺脚本（folder 展开；仅合并/展示时进行，存储保持原样）。 */
private fun flattenTree(items: List<JsonElement>): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    fun take(item: JsonElement) {
        val obj = item as? JsonObject ?: return
        when (obj.str("type")) {
            "folder" -> (obj["scripts"] as? JsonArray)?.forEach { take(it) }
            else -> out += obj
        }
    }
    items.forEach { take(it) }
    return out
}

private fun briefsOf(items: List<JsonElement>): List<TavernScriptBrief> =
    flattenTree(items).map { o ->
        TavernScriptBrief(
            id = o.str("id"),
            name = o.str("name"),
            type = o.str("type").ifBlank { "script" },
            enabled = o.boolOrTrue("enabled"),
            info = o.str("info"),
            content = o.str("content"),
        )
    }

/** 在 ScriptTree 列表里递归更新指定 id 的 enabled；返回 (新列表, 是否找到)。 */
private fun updateEnabledInItems(
    items: List<JsonElement>,
    scriptId: String,
    enabled: Boolean,
): Pair<List<JsonElement>, Boolean> {
    var found = false
    fun update(item: JsonElement): JsonElement {
        val obj = item as? JsonObject ?: return item
        if (obj.str("type") == "folder") {
            val inner = obj["scripts"] as? JsonArray ?: return obj
            return obj.withField("scripts", JsonArray(inner.map { update(it) }))
        }
        if (obj.str("id") == scriptId) {
            found = true
            return obj.withField("enabled", JsonPrimitive(enabled))
        }
        return obj
    }
    val newItems = items.map { update(it) }
    return newItems to found
}

private fun JsonObject.withField(key: String, value: JsonElement): JsonObject {
    val map = toMutableMap()
    map[key] = value
    return JsonObject(map)
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

/** 宽松布尔：与 runtime.js `scriptEnabled` 语义一致（缺失/未知按 true，显式 false/0/"false" 为 false）。 */
private fun JsonObject.boolOrTrue(key: String): Boolean {
    val p = this[key] as? JsonPrimitive ?: return true
    p.booleanOrNull?.let { return it }
    return when (p.contentOrNull?.lowercase()) {
        "false", "0", "no", "off" -> false
        else -> true
    }
}

// ============================================================
// B4：世界书桥 + 角色桥（JSR `loadWorldInfo` / `characters[i]` 语义）
// ============================================================

/** 取 `data.extensions.world`（角色绑定的世界书名）。 */
fun extractWorldName(extensionsRaw: String?): String? {
    if (extensionsRaw.isNullOrBlank()) return null
    return try {
        val root = Json.parseToJsonElement(extensionsRaw) as? JsonObject ?: return null
        (root["world"] as? JsonPrimitive)?.contentOrNull
    } catch (_: Exception) {
        null
    }
}

/**
 * [v216] 世界书名匹配：精确优先 → 双侧 trim 兜底。
 *
 * 存量全局库里存在书名带尾随空格的历史数据（导入时用文件名做 name，如「… 0.96 世界书 」）；
 * 卡侧 getGlobalWorldbookNames() 原样下发 → getWorldbook(name) 里 JS/Kotlin 都 trim 了
 * 「请求名」，但存量 name 未 trim → 精确比对不命中 → 返回 "null" → 卡抛错。见交接文档 §11.6。
 */
internal fun lorebookNameMatches(stored: String?, requested: String): Boolean {
    if (stored.isNullOrBlank()) return false
    return stored == requested || stored.trim() == requested.trim()
}

/**
 * [v216] 全局世界书查找：精确优先 → 双侧 trim 兜底（避免脏名/同名书时选错）。
 * @return 命中下标；未命中返回 -1。
 */
internal fun findLorebookIndexByName(books: List<Lorebook>, requested: String): Int {
    val exact = books.indexOfFirst { it.name == requested }
    if (exact >= 0) return exact
    return books.indexOfFirst { it.name.trim() == requested.trim() }
}

/** [v216] 世界书请求的命中结果（读面与可解析性判定共用，避免两处匹配逻辑漂移）。 */
private sealed interface LorebookHit {
    data class Embedded(val book: TavernEmbeddedBook, val bookName: String) : LorebookHit
    data class Global(val book: Lorebook, val trimmedFallback: Boolean) : LorebookHit
}

/**
 * [v216] 解析世界书请求（卡内嵌书优先，其次全局库；精确优先 → trim 兜底）；未命中返回 null。
 *
 * embeddedBook / worldName 由调用方预算好传入：批量判定（buildWorldNamesJson）时避免重复解析
 * 卡 extensionsRaw（千纱卡那份可达数百 KB）。
 */
private fun matchLorebook(
    embeddedBook: TavernEmbeddedBook?,
    worldName: String?,
    requestedRaw: String,
    settings: Settings?,
): LorebookHit? {
    val requested = requestedRaw.trim()
    // ST 语义：空名 = 未指定书（loadWorldInfo 无参返回 undefined），不能当成 "current" 去命中卡内书
    if (requested.isEmpty() || requested == "null" || requested == "undefined") return null

    // ① 卡内嵌书（原样优先 → 双侧 trim 兜底）
    if (embeddedBook != null) {
        val bookName = embeddedBook.name.ifBlank { worldName ?: "" }
        if (requested == "current" || lorebookNameMatches(bookName, requestedRaw) || lorebookNameMatches(worldName, requestedRaw)) {
            return LorebookHit.Embedded(embeddedBook, bookName)
        }
    }

    // ② [v214] 全局世界书库（Settings.lorebooks）；[v216] 精确优先（原样）→ 双侧 trim 兜底
    val books = settings?.lorebooks.orEmpty()
    val index = findLorebookIndexByName(books, requestedRaw)
    if (index >= 0) {
        return LorebookHit.Global(books[index], trimmedFallback = books[index].name != requestedRaw)
    }
    return null
}

/**
 * 世界书 → ST 风格 world info JSON（JSR `ctx.loadWorldInfo(name)` 语义）。
 *
 * 数据源优先级（与 [buildWorldNamesJson] 一致）：
 * 1. 内存中的卡内嵌书 [me.rerere.rikkahub.data.model.TavernCharacterData.embeddedBook]；
 * 2. [Settings.lorebooks] 宿主全局世界书库（[Lorebook.entries] 为 [PromptInjection.RegexInjection]）。
 *
 * [v208] P0-1：未命中返回 JSON "null"（ST loadWorldInfo 未命中返回 null/undefined）。
 * 此前返回 "{}" —— 真值！ST-PT worldinfo.ts:216-222 先 `if (!lorebook) return []` 再
 * `Object.values(lorebook.entries)`，把 {} 当有效书 → 对 undefined.entries 取 values → TypeError。
 *
 * [v216] 全局书分支改为「精确优先 → 双侧 trim 兜底」（存量书名可能带尾随空格，见 §11.6）。
 * @param onTrimFallback 命中 trim 兜底时回调 (requested, stored)；纯函数不直接打日志，由宿主决定。
 */
fun buildLorebookJson(
    assistant: Assistant?,
    name: String,
    settings: Settings? = null,
    onTrimFallback: ((requested: String, stored: String) -> Unit)? = null,
): String {
    val tav = assistant?.tavernData
    val hit = matchLorebook(tav?.embeddedBook, extractWorldName(tav?.extensionsRaw), name, settings)
    return when (hit) {
        is LorebookHit.Embedded -> embeddedBookToStJson(hit.book, hit.bookName)
        is LorebookHit.Global -> {
            if (hit.trimmedFallback) onTrimFallback?.invoke(name, hit.book.name)
            lorebookToStJson(hit.book)
        }
        null -> "null"
    }
}

/** 卡内嵌书 → ST world info JSON（entries 以 [TavernBookEntry.id] 为 uid）。 */
private fun embeddedBookToStJson(book: TavernEmbeddedBook, bookName: String): String = buildJsonObject {
    put("name", JsonPrimitive(bookName))
    put(
        "entries",
        buildJsonObject {
            book.entries.forEach { e ->
                put(e.id.toString(), entryToStJson(e))
            }
        },
    )
}.toString()

/**
 * [v214] 全局世界书 → ST world info JSON。
 *
 * [Lorebook.entries] 是 [PromptInjection.RegexInjection]（Uuid 主键），而 ST `uid` 契约是数字：
 * 这里用条目在书内的下标作为 uid（与 [applyLorebookDisablePatch] 写回规则一致）。
 */
fun lorebookToStJson(book: Lorebook): String = buildJsonObject {
    put("name", JsonPrimitive(book.name))
    put(
        "entries",
        buildJsonObject {
            book.entries.forEachIndexed { index, entry ->
                put(index.toString(), entryToStJson(globalInjectionToTavernEntry(entry, index)))
            }
        },
    )
}.toString()

/** [PromptInjection.RegexInjection] → [TavernBookEntry]（uid 用下标），复用 [entryToStJson] 保证 41 键同形。 */
private fun globalInjectionToTavernEntry(entry: PromptInjection.RegexInjection, uid: Int): TavernBookEntry =
    TavernBookEntry(
        id = uid,
        keys = entry.keywords,
        secondaryKeys = entry.secondaryKeys,
        comment = entry.name,
        content = entry.content,
        constant = entry.constantActive,
        selective = entry.selective,
        selectiveLogic = selectiveLogicToStNumber(entry.selectiveLogic),
        group = entry.group,
        position = injectionPositionToStNumber(entry.position),
        priority = entry.priority,
        disable = !entry.enabled,
        caseSensitive = entry.caseSensitive,
        matchWholeWords = entry.matchWholeWords,
        useRegex = entry.useRegex,
        probability = entry.probability,
        sticky = entry.sticky,
        cooldown = entry.cooldown,
        depth = entry.injectDepth,
        scanDepth = entry.scanDepth,
        role = when (entry.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            else -> "system"
        },
        groupWeight = entry.groupWeight,
        groupOverride = entry.groupOverride,
        delay = entry.delay,
        excludeRecursion = entry.excludeRecursion,
        preventRecursion = entry.preventRecursion,
        delayUntilRecursion = entry.delayUntilRecursion,
        useProbability = entry.useProbability,
        inclusionGroup = entry.inclusionGroup,
        useGroupScoring = entry.useGroupScoring,
        groupPriority = entry.groupPriority,
        automationId = entry.automationId,
        displayIndex = entry.displayIndex,
        displayPosition = entry.displayPosition,
        triggers = entry.triggers,
        matchPersonaDescription = entry.matchPersonaDescription,
        matchCharacterDescription = entry.matchCharacterDescription,
        matchCharacterPersonality = entry.matchCharacterPersonality,
        matchCharacterDepthPrompt = entry.matchCharacterDepthPrompt,
        matchScenario = entry.matchScenario,
        matchCreatorNotes = entry.matchCreatorNotes,
        ignoreBudget = entry.ignoreBudget,
        outletName = entry.outletName,
    )

/** 官方 world_info_logic：0=AND_ANY 1=NOT_ALL 2=NOT_ANY 3=AND_ALL（OR_ANY 为本地遗留 → 0）。 */
private fun selectiveLogicToStNumber(logic: SelectiveLogic): Int = when (logic) {
    SelectiveLogic.AND_ANY -> 0
    SelectiveLogic.NOT_ALL -> 1
    SelectiveLogic.NOT_ANY -> 2
    SelectiveLogic.AND_ALL -> 3
    SelectiveLogic.OR_ANY -> 0
}

/** 官方 world_info_position：0=before_char 1=after_char 2=ANTop 3=ANBottom 4=atDepth 5=EMTop 6=EMBottom 7=outlet。 */
private fun injectionPositionToStNumber(pos: InjectionPosition): Int = when (pos) {
    InjectionPosition.BEFORE_CHARACTER -> 0
    InjectionPosition.AFTER_CHARACTER -> 1
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> 0
    InjectionPosition.AFTER_SYSTEM_PROMPT -> 1
    InjectionPosition.TOP_OF_CHAT -> 2
    InjectionPosition.BOTTOM_OF_CHAT -> 3
    InjectionPosition.AT_DEPTH -> 4
    InjectionPosition.AUTHOR_NOTE -> 2
    InjectionPosition.ANTAGONIZE, InjectionPosition.AFTER_DIALOG -> 7
    InjectionPosition.EM_TOP -> 5
    InjectionPosition.EM_BOTTOM -> 6
    InjectionPosition.OUTLET -> 7
}

/** 世界书写回补丁项：uid + 目标 disable 状态。 */
data class LorebookDisablePatch(val uid: Int, val disable: Boolean)

/**
 * [v214] 解析 JS `RikkaBridge.updateLorebookEntries` 的整本书补丁：
 * `[{"uid":<number>,"disable":<bool>}, ...]`；非法/空返回 null。
 */
fun parseLorebookDisablePatch(patchJson: String): List<LorebookDisablePatch>? {
    if (patchJson.isBlank()) return null
    return try {
        val arr = Json.parseToJsonElement(patchJson) as? JsonArray ?: return null
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val uid = (obj["uid"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val disable = (obj["disable"] as? JsonPrimitive)?.booleanOrNull ?: return@mapNotNull null
            LorebookDisablePatch(uid, disable)
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * [v214] 卡内嵌书写回：按 [TavernBookEntry.id] == uid 改写 disable。
 * @return 有实际变更的新书；空补丁 / 无 uid 命中 / 状态未变返回 null。
 */
fun applyLorebookDisablePatch(book: TavernEmbeddedBook, patchJson: String): TavernEmbeddedBook? {
    val patches = parseLorebookDisablePatch(patchJson) ?: return null
    if (patches.isEmpty()) return null
    val byUid = patches.associateBy { it.uid }
    var changed = false
    val entries = book.entries.map { e ->
        val patch = byUid[e.id] ?: return@map e
        if (e.disable == patch.disable) return@map e
        changed = true
        e.copy(disable = patch.disable)
    }
    return if (changed) book.copy(entries = entries) else null
}

/**
 * [v214] 全局书写回：按条目下标（= [lorebookToStJson] 下发的 uid）改写 enabled = !disable。
 * @return 有实际变更的新书；空补丁 / 无 uid 命中 / 状态未变返回 null。
 */
fun applyLorebookDisablePatch(book: Lorebook, patchJson: String): Lorebook? {
    val patches = parseLorebookDisablePatch(patchJson) ?: return null
    if (patches.isEmpty()) return null
    val byUid = patches.associateBy { it.uid }
    var changed = false
    val entries = book.entries.mapIndexed { index, e ->
        val patch = byUid[index] ?: return@mapIndexed e
        val enabled = !patch.disable
        if (e.enabled == enabled) return@mapIndexed e
        changed = true
        e.copy(enabled = enabled)
    }
    return if (changed) book.copy(entries = entries) else null
}

/** [TavernBookEntry] → ST 扁平条目的字段名（与 JSR lorebook-shims `toLorebookEntry` 读取的字段对齐）。 */
/** ST `extension_prompt_roles`：0=system 1=user 2=assistant（ST 4117 / script.js:63 / JSR trace.ts:134）。 */
private fun roleToStNumber(role: String?): Int = when (role?.lowercase()) {
    "user" -> 1
    "assistant" -> 2
    "1" -> 1
    "2" -> 2
    else -> 0
}

private fun entryToStJson(e: TavernBookEntry): JsonObject = buildJsonObject {
    put("uid", e.id)
    put("comment", e.comment)
    put("content", e.content)
    put("disable", e.disable)
    put("constant", e.constant)
    put("vectorized", false)
    // ---- [v208] 以下 20 键此前缺失（审计 notes/audit-world-info-shim.md §3.2）----
    // selective：ST-PT worldinfo.ts:390 `data.selective && ...` —— 缺失 = 二级关键词全废（P1-1）
    put("selective", e.selective)
    // group：ST-PT worldinfo.ts:438 `_.groupBy(..., d => d.group)` —— 缺失 = 全部命中条目并成一组只随机回 1 条（P1-2）
    put("group", e.group)
    put("groupOverride", e.groupOverride)
    put("groupWeight", e.groupWeight)
    put("useGroupScoring", e.useGroupScoring)
    // useProbability：ST-PT worldinfo.ts:359 `if (data.useProbability && ...)` —— 缺失 = 概率过滤恒跳过（P1-4）
    put("useProbability", e.useProbability)
    put("addMemo", e.comment.isNotEmpty())
    put("ignoreBudget", e.ignoreBudget)
    put("outletName", e.outletName)
    put("automationId", e.automationId)
    put("displayIndex", if (e.displayIndex != 0) e.displayIndex else e.id)
    put("triggers", buildJsonArray { e.triggers.forEach { add(JsonPrimitive(it)) } })
    put("caseSensitive", e.caseSensitive)
    put("matchWholeWords", e.matchWholeWords)
    put("matchPersonaDescription", e.matchPersonaDescription)
    put("matchCharacterDescription", e.matchCharacterDescription)
    put("matchCharacterPersonality", e.matchCharacterPersonality)
    put("matchCharacterDepthPrompt", e.matchCharacterDepthPrompt)
    put("matchScenario", e.matchScenario)
    put("matchCreatorNotes", e.matchCreatorNotes)
    put("key", buildJsonArray { e.keys.forEach { add(JsonPrimitive(it)) } })
    put("keysecondary", buildJsonArray { e.secondaryKeys.forEach { add(JsonPrimitive(it)) } })
    put("selectiveLogic", e.selectiveLogic)
    put("position", e.position)
    // [v208] role 数值化：ST 契约是 0/1/2（此前下发字符串 "system" → JSR Number("system")=NaN，depth 桶配不上，P1-6）
    put("role", roleToStNumber(e.role))
    put("depth", e.depth)
    put("order", e.priority)
    put("probability", e.probability)
    if (e.scanDepth != null) put("scanDepth", e.scanDepth) else put("scanDepth", JsonNull)
    put("excludeRecursion", e.excludeRecursion)
    put("preventRecursion", e.preventRecursion)
    put("delayUntilRecursion", e.delayUntilRecursion)
    put("sticky", e.sticky)
    put("cooldown", e.cooldown)
    put("delay", e.delay)
}

/** `data.extensions` 原始 JSON → JsonObject（空/非法/非对象 → 空对象）。 */
private fun parseExtensionsObject(raw: String?): JsonObject {
    if (raw.isNullOrBlank()) return JsonObject(emptyMap())
    return try {
        Json.parseToJsonElement(raw) as? JsonObject ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}

/**
 * [v211] UIMessage 列表 ↔ ST Chat Completion messages（[{role, content}]）互转。
 *
 * ST 契约（openai.js:1616-1623）：生成前把最终 messages 交给扩展改写后继续使用。
 * 只转文本 part（与 ST 的 chat completion 语义一致）；role 映射 system/user/assistant。
 * 注意：size 必须守恒 —— Kotlin 侧以此判断改写是否可信，不等长直接拒绝（防止扩展丢消息）。
 */
fun buildStMessagesJson(messages: List<me.rerere.ai.ui.UIMessage>): String = buildJsonArray {
    messages.forEach { m ->
        add(buildJsonObject {
            put("role", m.role.name.lowercase())
            put("content", m.toText())
            if (!m.name.isNullOrBlank()) put("name", m.name)
        })
    }
}.toString()

/** 反序列化改写结果；字段缺失/role 非法时返回 null（调用方保留原文）。 */
fun parseStMessagesJson(json: String): List<me.rerere.ai.ui.UIMessage>? {
    return runCatching {
        val arr = Json.parseToJsonElement(json)
        if (arr !is JsonArray) return null
        arr.map { el ->
            val obj = el as? JsonObject ?: return null
            val roleRaw = (obj["role"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: return null
            val role = when (roleRaw) {
                "system" -> me.rerere.ai.core.MessageRole.SYSTEM
                "user" -> me.rerere.ai.core.MessageRole.USER
                "assistant" -> me.rerere.ai.core.MessageRole.ASSISTANT
                else -> return null
            }
            val content = (obj["content"] as? JsonPrimitive)?.contentOrNull ?: return null
            me.rerere.ai.ui.UIMessage(role = role, parts = listOf(me.rerere.ai.ui.UIMessagePart.Text(content)))
        }
    }.getOrNull()
}

/**
 * [v208] P0-3：世界书名列表 + 全局启用书（ST `world_names` / `selected_world_info` / `world_info`）。
 *
 * 此前 JS 侧三者恒为空：
 *  - ST-PT worldinfo.ts:660 `results.filter(e => e && world_names.includes(e))` 把所有书过滤光（WI 全灭）；
 *  - ST-PT worldinfo.ts:597-618/630-655 依赖 selected_world_info，空数组 → 全局/角色附加书全漏；
 *  - JSR lorebook.ts:191-193 setLorebookSettings 对非空书单 throw Error。
 *
 * 数据源：Settings.lorebooks（RikkaHub 自己的世界书库，含 isCharacterBook 标记）+ 卡内嵌书名。
 */
fun buildWorldNamesJson(settings: Settings?, assistant: Assistant?): String {
    val names = LinkedHashSet<String>()
    val selected = LinkedHashSet<String>()
    // ① 宿主世界书库（enabled 的进 selected_world_info = ST「全局启用」语义）
    if (settings != null) {
        settings.lorebooks.forEach { book ->
            if (book.name.isNotBlank()) {
                names.add(book.name)
                if (book.enabled) selected.add(book.name)
            }
        }
    }
    // ② 角色绑定的主世界书（data.extensions.world）+ 卡内嵌书名：算可用，也并入全局启用（ST 行为）
    val tav = assistant?.tavernData
    val embeddedBook = tav?.embeddedBook
    val worldName = extractWorldName(tav?.extensionsRaw)
    if (tav != null) {
        worldName?.let { if (it.isNotBlank()) { names.add(it); selected.add(it) } }
        embeddedBook?.name?.takeIf { it.isNotBlank() }?.let { names.add(it); selected.add(it) }
    }
    // [v216] 只下发「可解析」的书名：卡侧 M() 用 Promise.all 且无 per-book 容错，
    // 用户全局库里任何一本坏书（含脏名）都会让整个开关面板报废（见交接文档 §11.6）。
    fun resolvable(n: String): Boolean = matchLorebook(embeddedBook, worldName, n, settings) != null
    return buildJsonObject {
        put("world_names", buildJsonArray {
            names.filter { resolvable(it) }.forEach { add(JsonPrimitive(it)) }
        })
        put("selected", buildJsonArray {
            selected.filter { resolvable(it) }.forEach { add(JsonPrimitive(it)) }
        })
        // charLore：按文件名的角色附加书（ST world_info.charLore: [{name, extraBooks}]），宿主暂无此概念 → 空数组保形
        put("charLore", buildJsonArray {})
    }.toString()
}

/**
 * 当前角色卡 → ST `characters[i]`（char-data.js 的 v1CharData）**完整形态** JSON。
 *
 * 消费方：
 *  - ST-Prompt-Template：`getCharacterDefine()`（src/function/characters.ts:92）第一句就是 `char.mes_example.trim()`；
 *  - JSR：`characters[i].avatar`（per-character store 的键）、`data.extensions.world`、`data.alternate_greetings`、`data.mes_example`；
 *  - EJS 模板：`getCharData()` / `getCharacterDefine()`。
 *
 * ⚠️ v204 真机事故（交接文档 §5.A）：本函数早期只输出 `{name, world}`，
 * 于是 `char.mes_example` 是 undefined → 扩展在 `.trim()` 处抛
 * `TypeError: Cannot read properties of undefined (reading 'trim')`；又因为抛在
 * `handlePreloadWorldInfo()` 的 try 里，真机日志顶着
 * `[Prompt Template] Error processing world info:` 的壳 —— **世界书背了黑锅**。
 * 结论（项目第一原则）：**形态必须与 ST 真源同构，一个键都不能少**。
 */
fun buildCharacterJson(assistant: Assistant?): String {
    val tav = assistant?.tavernData ?: return "{}"
    val name = tav.name.ifBlank { assistant?.name ?: "" }
    val world = extractWorldName(tav.extensionsRaw)
    // data.extensions 是 ST 真源位置（world / tavern_helper / regex_scripts 都挂在这里）
    val extensionMap = parseExtensionsObject(tav.extensionsRaw).toMutableMap()
    if (world != null) extensionMap["world"] = JsonPrimitive(world)
    val extensions = JsonObject(extensionMap)

    val tagsArray = buildJsonArray { tav.tags.forEach { add(JsonPrimitive(it)) } }
    val book = tav.embeddedBook
    val characterBook: JsonElement = if (book == null) {
        JsonNull
    } else {
        buildJsonObject {
            put("name", book.name)
            put("description", book.description)
            put("entries", buildJsonArray { book.entries.forEach { add(entryToStJson(it)) } })
        }
    }

    val data = buildJsonObject {
        put("name", name)
        put("description", tav.description)
        put("personality", tav.personality)
        put("scenario", tav.scenario)
        put("first_mes", tav.firstMessage)
        put("mes_example", tav.mesExample)
        put("creator_notes", tav.creatorNotes)
        put("system_prompt", tav.systemPrompt)
        put("post_history_instructions", tav.postHistoryInstructions)
        put("alternate_greetings", buildJsonArray { tav.alternateGreetings.forEach { add(JsonPrimitive(it)) } })
        put("character_book", characterBook)
        put("tags", tagsArray)
        put("creator", tav.creator)
        put("character_version", tav.characterVersion)
        put("extensions", extensions)
        put("group_only_greetings", buildJsonArray { tav.groupOnlyGreetings.forEach { add(JsonPrimitive(it)) } })
        put("depth_prompt", buildJsonObject {
            put("prompt", tav.depthPrompt)
            put("depth", tav.depthPromptDepth)
            put("role", tav.depthPromptRole)
        })
    }

    return buildJsonObject {
        put("name", name)
        // ST 拿「角色文件名」当 avatar（JSR 用它做 per-character store 的键、还会 .replace() 它）：
        // 宿主没有这个概念 → 用助手 id 派生一个稳定标识，保证扩展永远拿到字符串而非 undefined。
        put("avatar", (assistant?.id?.toString() ?: "character") + ".png")
        put("description", tav.description)
        put("personality", tav.personality)
        put("scenario", tav.scenario)
        put("first_mes", tav.firstMessage)
        put("mes_example", tav.mesExample)
        put("creatorcomment", tav.creatorNotes) // ST 兼容旧字段（等价 data.creator_notes）
        put("chat", "")
        put("talkativeness", (assistant?.talkativeness ?: 0.5f).toString())
        put("fav", false)
        put("tags", tagsArray)
        put("spec", tav.spec)
        put("spec_version", tav.specVersion)
        put("data", data)
    }.toString()
}
