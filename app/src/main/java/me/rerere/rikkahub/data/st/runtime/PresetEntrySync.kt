package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.st.preview.PromptOrderSyncWriter
import kotlin.uuid.Uuid

/**
 * [v252] 预设条目开关的**双向对账**。
 *
 * ## 为什么需要
 *
 * 真机上出现「悬浮窗里把色色条目关了，预设管理里还是开的，消息里照样发」。
 * 根因是同一批「预设条目」有**两份互不相干的存储**：
 *
 * | 存储 | 谁在写 | 谁在读 |
 * |---|---|---|
 * | `Settings.tavernPresets[group]` 原始 JSON 的 `prompts[].enabled` / `prompt_order[].order[].enabled` | 悬浮窗 WebView（JSR `replacePreset` → `saveTavernPreset`） | 悬浮窗 `getPreset()`、12 个内置 marker（`parseActiveStPresetConfig`） |
 * | `Settings.modeInjections[].enabled` | 预设管理页、助手扩展页 | **真实发送链路**（`collectInjections` 的 `it.enabled` 过滤）、预设管理页 |
 *
 * 两边只在一处对齐过 —— 导入预设时（`tryImportSillyTavernPreset` 一次性把
 * `prompt_order[].enabled` 读进 `modeInjections[].enabled`）。导入之后再改任何一边，
 * 另一边都不会动，于是出现「面板关了但消息里还在」。
 *
 * 本文件补上两段单向同步，合起来形成闭环：
 * 1. [reconcileModeInjectionsFromPreset]：预设 JSON 被写 → 回灌 `modeInjections`（悬浮窗 → 发送链路）
 * 2. [applyEntryEnabledToPresetJson]：`modeInjections` 被改 → 回写预设 JSON（预设管理 → 悬浮窗）
 *
 * 两个函数都是**纯函数**（无 I/O、无副作用、不依赖 Android），供 JVM 单测直接覆盖。
 */

private const val ST_PRESET_DEFAULT_CHARACTER_ID = "100001"

/** 预设条目 id 与 identifier 的映射关系（用于在不改数据模型的前提下把两边对上）。 */
internal data class PresetEntryIndex(
    /** deterministic ModeInjection id → 预设 identifier */
    val identifierById: Map<Uuid, String>,
    /** 预设 identifier → 最终启用态（prompt_order 优先，回落 prompts[].enabled，缺省 true） */
    val enabledByIdentifier: Map<String, Boolean>,
) {
    val isEmpty: Boolean get() = identifierById.isEmpty()
}

/**
 * 解析一份 ST 预设 JSON，建立「id ↔ identifier ↔ enabled」索引。
 *
 * 启用态取值顺序严格对齐 ST：
 * 1. `prompt_order[character_id=100001].order[].enabled`（ST 的权威开关）
 * 2. 缺失时回落 `prompts[].enabled`
 * 3. 再缺省按 `true`（ST 默认开启）
 */
internal fun buildPresetEntryIndex(presetObj: JsonObject?, group: String): PresetEntryIndex {
    if (presetObj == null || group.isBlank()) return PresetEntryIndex(emptyMap(), emptyMap())
    val prompts = presetObj["prompts"] as? JsonArray ?: return PresetEntryIndex(emptyMap(), emptyMap())

    val orderEnabled = LinkedHashMap<String, Boolean>()
    val orderBuckets = (presetObj["prompt_order"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()
    val activeBucket = orderBuckets.firstOrNull {
        (it["character_id"] as? JsonPrimitive)?.contentOrNull == ST_PRESET_DEFAULT_CHARACTER_ID
    } ?: orderBuckets.firstOrNull { (it["order"] as? JsonArray)?.isNotEmpty() == true }
    (activeBucket?.get("order") as? JsonArray)?.forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        val ident = (o["identifier"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
        val en = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: return@forEach
        orderEnabled[ident] = en
    }

    val identifierById = LinkedHashMap<Uuid, String>()
    val enabledByIdentifier = LinkedHashMap<String, Boolean>()
    prompts.forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        val ident = (o["identifier"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
        if (ident.isBlank()) return@forEach
        identifierById[PromptOrderSyncWriter.deterministicPresetEntryId(group, ident)] = ident
        enabledByIdentifier[ident] = orderEnabled[ident]
            ?: (o["enabled"] as? JsonPrimitive)?.booleanOrNull
            ?: true
    }
    return PresetEntryIndex(identifierById, enabledByIdentifier)
}

/** 把 `tavernPresets` 里的某个组解析成 JsonObject（兼容 JSON 字符串桶与对象桶两种存法）。 */
internal fun parseRawPresetGroup(tavernPresetsJson: String?, group: String): JsonObject? {
    if (tavernPresetsJson.isNullOrBlank() || group.isBlank()) return null
    val root = runCatching { Json.parseToJsonElement(tavernPresetsJson) as? JsonObject }.getOrNull()
        ?: return null
    return when (val el = root[group]) {
        is JsonObject -> el
        is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        }
        else -> null
    }
}

/**
 * 方向一：预设 JSON 刚被写回 → 把该组的条目启用态回灌到 `modeInjections`。
 *
 * 调用点：`ChatPage.tavernPresetUpdater`（悬浮窗 `replacePreset` / `saveTavernPreset` 的落点）。
 *
 * 不变量：
 * - 只动 `presetGroup == group` 且 id 能对上某个 identifier 的条目；
 * - 预设里没有对应 identifier 的条目（用户手工加进该组的）原样保留；
 * - 解析失败 / 索引为空时**原样返回**，绝不因为一次坏 JSON 把整组开关清空。
 */
internal fun reconcileModeInjectionsFromPreset(
    current: Settings,
    group: String,
    rawPresetJson: String,
    assistantId: Uuid? = null,
): Settings {
    if (group.isBlank() || rawPresetJson.isBlank()) return current
    val parsed = runCatching { Json.parseToJsonElement(rawPresetJson) as? JsonObject }.getOrNull()
        ?: return current
    // 入参可能是「预设对象本身」（悬浮窗 saveTavernPreset 的 rawJson），
    // 也可能是「整个 tavernPresets 根对象」（调用方图省事直接传根）。两种都吃。
    val presetObj = if (parsed["prompts"] is JsonArray) parsed else parseRawPresetGroup(rawPresetJson, group)
        ?: return current
    val index = buildPresetEntryIndex(presetObj, group)
    if (index.isEmpty) return current

    // [v253] 第三处必须一起同步：assistant.modeInjectionIds。
    //
    // 真机取证（2026-11-27 13:07）：修完前两份存储后，tavern_presets 的 prompt_order
    // 与 modeInjections[].enabled 已经**逐条一致（分歧 0）**，但猫神菜单显示「1/3」、
    // 预设管理页显示另一套、消息里三条全都不生效。
    //
    // 原因是发送链路（PromptInjectionTransformer.kt:655）的判据是
    //     it.enabled && effectiveModeInjectionIds.contains(it.id)
    // 其中 effectiveModeInjectionIds = assistant.modeInjectionIds（会话级注入关闭时）。
    // 也就是**条目既要 enabled，又要被绑进助手**才会真正下发。
    //
    // 而悬浮窗那条路径（JSR replacePreset / saveTavernPreset）只改预设 JSON，
    // 从来不碰 assistant.modeInjectionIds —— 于是「面板里开了，消息里照样不发」。
    // 这里把「该组内 enabled 的条目」补进助手绑定，把「enabled 的条目」移除，
    // 让第四份存储跟着前两份一起收敛。
    val enabledIds = index.identifierById
        .filter { (id, ident) -> index.enabledByIdentifier[ident] == true }
        .keys
    val groupIds = index.identifierById.keys
    var changed = false
    val next = current.modeInjections.map { item ->
        if (item.presetGroup != group) return@map item
        val ident = index.identifierById[item.id] ?: return@map item
        val enabled = index.enabledByIdentifier[ident] ?: return@map item
        if (item.enabled == enabled) return@map item
        changed = true
        item.copy(enabled = enabled)
    }
    var nextAssistants = current.assistants
    if (assistantId != null) {
        val target = current.assistants.firstOrNull { it.id == assistantId }
        if (target != null) {
            val updatedIds = (target.modeInjectionIds - groupIds) + enabledIds
            if (updatedIds != target.modeInjectionIds) {
                changed = true
                nextAssistants = current.assistants.map { a ->
                    if (a.id == assistantId) a.copy(modeInjectionIds = updatedIds) else a
                }
            }
        }
    }
    return if (changed) {
        current.copy(modeInjections = next, assistants = nextAssistants)
    } else {
        current
    }
}

/**
 * 方向二：`modeInjections` 里的某条被开关 → 把启用态回写进预设 JSON。
 *
 * 调用点：`togglePresetEntryForAssistant` / `togglePresetGroupForAssistant`
 * （预设管理页、助手→扩展→预设页的单条与整组开关）。
 *
 * 同时改写 `prompts[].enabled` 与 `prompt_order[].order[].enabled`（两处都写，因为
 * 不同版本的 ST / 不同脚本读的地方不一样），其余字段逐字保留。
 *
 * @return 新的 `tavernPresets` JSON；找不到该组/该条目、或值本来就一致时返回 null（调用方据此跳过写盘）。
 */
internal fun applyEntryEnabledToPresetJson(
    tavernPresetsJson: String?,
    group: String?,
    entryId: Uuid,
    enabled: Boolean,
): String? {
    if (tavernPresetsJson.isNullOrBlank() || group.isNullOrBlank()) return null
    val root = runCatching { Json.parseToJsonElement(tavernPresetsJson) as? JsonObject }.getOrNull()
        ?: return null
    val groupEl = root[group] ?: return null
    val wasStringEncoded = groupEl is JsonPrimitive
    val presetObj = when (groupEl) {
        is JsonObject -> groupEl
        is JsonPrimitive -> groupEl.contentOrNull?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        }
        else -> null
    } ?: return null

    val index = buildPresetEntryIndex(presetObj, group)
    val identifier = index.identifierById[entryId] ?: return null

    var changed = false
    fun patchElement(el: JsonElement): JsonElement {
        val o = el as? JsonObject ?: return el
        val ident = (o["identifier"] as? JsonPrimitive)?.contentOrNull
        if (ident != identifier) return el
        val existing = (o["enabled"] as? JsonPrimitive)?.booleanOrNull
        if (existing == enabled) return el
        changed = true
        return buildJsonObject {
            o.forEach { (k, v) -> if (k != "enabled") put(k, v) }
            put("enabled", JsonPrimitive(enabled))
        }
    }

    val newPresetObj = buildJsonObject {
        presetObj.forEach { (k, v) ->
            when (k) {
                "prompts" -> {
                    val arr = v as? JsonArray
                    if (arr == null) put(k, v)
                    else put(k, buildJsonArray { arr.forEach { add(patchElement(it)) } })
                }
                "prompt_order" -> {
                    val arr = v as? JsonArray
                    if (arr == null) put(k, v)
                    else put(k, buildJsonArray {
                        arr.forEach { bucket ->
                            val bObj = bucket as? JsonObject
                            val orderArr = bObj?.get("order") as? JsonArray
                            if (bObj == null || orderArr == null) {
                                add(bucket)
                            } else {
                                add(buildJsonObject {
                                    bObj.forEach { (bk, bv) ->
                                        if (bk == "order") {
                                            put(bk, buildJsonArray { orderArr.forEach { add(patchElement(it)) } })
                                        } else {
                                            put(bk, bv)
                                        }
                                    }
                                })
                            }
                        }
                    })
                }
                else -> put(k, v)
            }
        }
    }
    if (!changed) return null

    val newRoot = buildJsonObject {
        root.forEach { (k, v) -> if (k != group) put(k, v) }
        put(
            group,
            if (wasStringEncoded) JsonPrimitive(newPresetObj.toString()) else newPresetObj,
        )
    }
    return newRoot.toString()
}

/**
 * 方向二的批量入口：按 `modeInjections` 的 enabled 差异，把每一条变化写回预设 JSON。
 *
 * 为什么要「差异」而不是全量：预设 JSON 里还有很多不来自 `modeInjections` 的字段
 * （prompt_order 顺序、injection_position/depth/order、extensions…），全量重写会把
 * PromptOrderSyncWriter 刚写进去的东西覆盖掉。只处理 `enabled` 真正变了的条目。
 *
 * @return 新的 `tavernPresets` JSON；没有任何变化（或对不上预设）时返回 null。
 */
internal fun applyModeInjectionEnabledDiffToPresets(
    tavernPresetsJson: String?,
    previous: List<me.rerere.rikkahub.data.model.PromptInjection.ModeInjection>,
    next: List<me.rerere.rikkahub.data.model.PromptInjection.ModeInjection>,
): String? {
    if (tavernPresetsJson.isNullOrBlank() || previous.isEmpty()) return null
    val previousEnabled = HashMap<Uuid, Boolean>(previous.size)
    for (item in previous) previousEnabled[item.id] = item.enabled
    var current = tavernPresetsJson
    var touched = false
    for (item in next) {
        val before = previousEnabled[item.id] ?: continue
        if (before == item.enabled) continue
        val patched = applyEntryEnabledToPresetJson(
            tavernPresetsJson = current,
            group = item.presetGroup,
            entryId = item.id,
            enabled = item.enabled,
        ) ?: continue
        current = patched
        touched = true
    }
    return if (touched) current else null
}
