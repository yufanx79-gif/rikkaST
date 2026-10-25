package me.rerere.rikkahub.data.st.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.export.ModeInjectionSerializer
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups

/**
 * 合并当前会话生效的全部 ST 正则脚本（全局 → 已启用预设内嵌 → 角色卡内嵌）。
 *
 * 对齐 SillyTavern `getRegexScripts({ allowedOnly: true })` 的三源合并顺序：
 * 1. `GLOBAL`：`Settings.regexScripts`
 * 2. `PRESET`：当前助手/会话已启用预设组对应的 `Settings.tavernPresets[group].extensions.regex_scripts`
 * 3. `SCOPED`：`Assistant.tavernData.embeddedRegexScripts`
 *
 * 去重规则：按 `sourceTag`（`global` / `preset:<group>` / `card`）隔离去重键，
 * 避免不同来源的脚本因复用默认 id（如 `"1"` / `"default"`）或空 id 而互相覆盖，
 * 同时消除同一来源内的重复条目。
 */
fun mergeActiveRegexScripts(
    settings: Settings,
    assistant: Assistant?,
    extraModeInjectionIds: Set<kotlin.uuid.Uuid> = emptySet(),
): List<RegexScript> {
    val activeGroups = enabledPresetGroups(assistant, settings, extraModeInjectionIds)
    val presetScriptsByGroup = LinkedHashMap<String, List<RegexScript>>()
    val presetRoot = runCatching {
        Json.parseToJsonElement(settings.tavernPresets) as? JsonObject
    }.getOrNull()
    for (group in activeGroups) {
        val rawPreset = when (val el = presetRoot?.get(group)) {
            is JsonPrimitive -> el.contentOrNull
            is JsonObject -> el.toString()
            else -> null
        }
        val scripts = if (rawPreset.isNullOrBlank()) {
            emptyList()
        } else {
            ModeInjectionSerializer.inspectPresetRegexScripts(rawPreset, group).second
        }
        if (scripts.isNotEmpty()) {
            presetScriptsByGroup[group] = scripts
        }
    }
    val flatPresetScripts = presetScriptsByGroup.values.flatten()
    return mergeRegexScripts(
        global = settings.regexScripts,
        card = assistant?.tavernData?.embeddedRegexScripts,
        preset = flatPresetScripts,
        presetByGroup = presetScriptsByGroup,
    )
}

/**
 * 合并全局 ST 正则脚本、预设内嵌正则脚本与当前角色卡内嵌正则脚本。
 *
 * 执行顺序对齐 SillyTavern `getRegexScripts()`：
 * 全局脚本 (`settings.regexScripts`) → 预设脚本 (`preset`) → 角色卡脚本 (`tavernData.embeddedRegexScripts`)。
 */
fun mergeRegexScripts(
    global: List<RegexScript>,
    card: List<RegexScript>?,
    preset: List<RegexScript> = emptyList(),
    presetByGroup: Map<String, List<RegexScript>> = emptyMap(),
): List<RegexScript> {
    val cardList = card.orEmpty()
    if (cardList.isEmpty() && preset.isEmpty() && presetByGroup.isEmpty()) return global
    val seenKeys = HashSet<String>(global.size + preset.size + cardList.size)
    return buildList(global.size + preset.size + cardList.size) {
        for (script in global) {
            if (seenKeys.add(dedupKey("global", script))) {
                add(script)
            }
        }
        if (presetByGroup.isNotEmpty()) {
            for ((group, scripts) in presetByGroup) {
                val tag = "preset:$group"
                for (script in scripts) {
                    if (seenKeys.add(dedupKey(tag, script))) {
                        add(script)
                    }
                }
            }
        } else {
            for (script in preset) {
                if (seenKeys.add(dedupKey("preset", script))) {
                    add(script)
                }
            }
        }
        for (script in cardList) {
            if (seenKeys.add(dedupKey("card", script))) {
                add(script)
            }
        }
    }
}

private fun dedupKey(sourceTag: String, script: RegexScript): String {
    val normalizedId = script.id.trim()
    return if (normalizedId.isNotEmpty()) {
        "$sourceTag#id:$normalizedId#${script.findRegex.hashCode()}#${script.replaceString.hashCode()}"
    } else {
        "$sourceTag#sig:${script.scriptName}#${script.findRegex}#${script.replaceString}#${script.placement.joinToString(",")}"
    }
}

/**
 * 从角色卡 `data.extensions.regex_scripts`（酒馆 RegexScriptData 数组）解析内嵌正则。
 *
 * [v241] 宽容解析升级：不同版本 ST 与第三方制卡工具的字段类型漂移**不再丢弃脚本**。
 * 旧实现用严格 kotlinx 序列化，以下真实卡片形态会整条静默丢失：
 * - `id` 是数字（旧版酒馆 / 部分工具）；
 * - `placement` 是单个数字而不是数组（旧版酒馆正则）；
 * - `trimStrings` 是字符串/null；
 * - 布尔字段写成 0/1。
 *
 * 兼容规则：
 * - `id`：字符串或数字；
 * - `placement`：数字数组（ST 1.18）或单个数字；
 * - `trimStrings`：字符串数组、单个字符串或 null；
 * - 布尔字段：true/false、0/1、yes/no、on/off；
 * - `substituteRegex`：数字或布尔；
 * - 同时兼容 camelCase 与 snake_case 字段名。
 *
 * 对齐 ST：无 id 的脚本生成新 id；无 findRegex 的脚本丢弃；单条异常只丢该条。
 */
fun parseCardRegexScripts(extensionsObj: JsonObject?): List<RegexScript> {
    val raw = extensionsObj?.get("regex_scripts")
        ?: extensionsObj?.get("regexScripts")
        ?: return emptyList()
    val arr = raw as? JsonArray ?: return emptyList()
    return arr.mapNotNull { parseRegexScript(it) }
        .map { if (it.id.isBlank()) it.copy(id = Uuid.random().toString()) else it }
        .filter { it.findRegex.isNotBlank() }
}

/** 单条内嵌正则的宽容解析（规则见 [parseCardRegexScripts]）；解析失败返回 null。 */
internal fun parseRegexScript(element: JsonElement): RegexScript? {
    val obj = element as? JsonObject ?: return null

    fun raw(key: String, alt: String? = null): JsonElement? = obj[key] ?: alt?.let { obj[it] }

    fun primitive(key: String, alt: String? = null): JsonPrimitive? = raw(key, alt) as? JsonPrimitive

    fun stringOf(key: String, alt: String? = null, default: String = ""): String =
        primitive(key, alt)?.contentOrNull ?: default

    fun intOf(key: String, alt: String? = null): Int? {
        val text = primitive(key, alt)?.contentOrNull?.trim() ?: return null
        if (text.isEmpty()) return null
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt()
    }

    /** 数字或布尔（`substituteRegex` 两种历史写法都存在） */
    fun intOrBoolOf(key: String, alt: String? = null): Int? {
        intOf(key, alt)?.let { return it }
        return when (primitive(key, alt)?.contentOrNull?.trim()?.lowercase()) {
            "true" -> 1
            "false" -> 0
            else -> null
        }
    }

    fun boolOf(key: String, alt: String? = null, default: Boolean = false): Boolean {
        val text = primitive(key, alt)?.contentOrNull?.trim()?.lowercase() ?: return default
        return text == "true" || text == "1" || text == "yes" || text == "on"
    }

    fun intListOf(key: String): List<Int> = when (val el = obj[key]) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull() }
        is JsonPrimitive -> listOfNotNull(el.contentOrNull?.trim()?.toIntOrNull())
        else -> emptyList()
    }

    fun stringListOf(key: String, alt: String? = null): List<String> = when (val el = raw(key, alt)) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(el.contentOrNull?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }

    val disabled = when {
        primitive("disabled") != null -> boolOf("disabled")
        primitive("enabled") != null -> !boolOf("enabled", default = true)
        else -> false
    }

    return RegexScript(
        id = stringOf("id"),
        scriptName = stringOf("scriptName", "script_name", "name"),
        findRegex = stringOf("findRegex", "find_regex"),
        replaceString = stringOf("replaceString", "replace_string"),
        trimStrings = stringListOf("trimStrings", "trim_strings"),
        placement = intListOf("placement"),
        disabled = disabled,
        markdownOnly = boolOf("markdownOnly", "markdown_only"),
        promptOnly = boolOf("promptOnly", "prompt_only"),
        runOnEdit = boolOf("runOnEdit", "run_on_edit"),
        substituteRegex = intOrBoolOf("substituteRegex", "substitute_regex") ?: 0,
        minDepth = intOf("minDepth", "min_depth"),
        maxDepth = intOf("maxDepth", "max_depth"),
    )
}
