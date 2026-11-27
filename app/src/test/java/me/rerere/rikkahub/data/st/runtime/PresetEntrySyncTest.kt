package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.st.preview.PromptOrderSyncWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v252] 预设条目开关「双向对账」回归测试。
 *
 * ## 真机故障
 * 用户在悬浮窗（WebView 面板）里把色色相关条目全关了，回到「预设管理」页发现那些条目还是开的，
 * 发消息时内容照样下发。根因是同一批条目存在两份互不相干的存储：
 *
 * - 悬浮窗读写的 `Settings.tavernPresets[group]` 原始 JSON（`prompts[].enabled` /
 *   `prompt_order[].order[].enabled`）；
 * - 真实发送链路只读 `Settings.modeInjections[].enabled`。
 *
 * 两边只在导入预设那一刻对齐过一次。本测试锁定新加的两段单向同步：
 * 1. [reconcileModeInjectionsFromPreset]：预设 JSON 被写 → 回灌 modeInjections
 * 2. [applyEntryEnabledToPresetJson] / [applyModeInjectionEnabledDiffToPresets]：modeInjections 被改 → 回写预设 JSON
 */
class PresetEntrySyncTest {

    private val group = "测试预设"

    /** 构造一份最小但结构真实的 ST 预设 JSON。 */
    private fun presetJson(
        prompts: List<Triple<String, String, Boolean?>>,
        order: List<Pair<String, Boolean?>>,
    ): String {
        val promptsJson = prompts.joinToString(",") { (ident, name, en) ->
            val enabledField = if (en == null) "" else ",\"enabled\":$en"
            "{\"identifier\":\"$ident\",\"name\":\"$name\"$enabledField,\"content\":\"body-$ident\"}"
        }
        val orderJson = order.joinToString(",") { (ident, en) ->
            val enabledField = if (en == null) "" else ",\"enabled\":$en"
            "{\"identifier\":\"$ident\"$enabledField}"
        }
        return """{"prompts":[$promptsJson],"prompt_order":[{"character_id":100001,"order":[$orderJson]}],"temperature":0.8}"""
    }

    private fun settingsWith(
        injections: List<PromptInjection.ModeInjection>,
        presets: String,
    ) = Settings(modeInjections = injections, tavernPresets = presets)

    private fun entry(group: String, ident: String, enabled: Boolean) = PromptInjection.ModeInjection(
        id = PromptOrderSyncWriter.deterministicPresetEntryId(group, ident),
        name = "name-$ident",
        enabled = enabled,
        presetGroup = group,
    )

    // ---------------------------------------------------------------- 方向一：悬浮窗 → 发送链路

    @Test
    fun presetDisableFlowsIntoModeInjections() {
        // 真机现场：悬浮窗把 ⛔️关闭NSFW 关掉（prompt_order.enabled=false）
        val json = presetJson(
            prompts = listOf(
                Triple("main", "主提示", null),
                Triple("nsfw_off", "关闭NSFW", false),
            ),
            order = listOf("main" to true, "nsfw_off" to false),
        )
        val before = settingsWith(
            injections = listOf(entry(group, "main", true), entry(group, "nsfw_off", true)),
            presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString(),
        )

        val after = reconcileModeInjectionsFromPreset(before, group, json)

        assertEquals(
            "悬浮窗关掉的条目必须同步到发送链路",
            false,
            after.modeInjections.first { it.id == entry(group, "nsfw_off", true).id }.enabled,
        )
        assertEquals(
            "没被改的条目不能被动",
            true,
            after.modeInjections.first { it.id == entry(group, "main", true).id }.enabled,
        )
    }

    /**
     * [v253] 第四份存储：assistant.modeInjectionIds。
     *
     * 真机取证（2026-11-27 13:07）：前两份存储（tavern_presets.prompt_order /
     * modeInjections.enabled）已经逐条一致（分歧 0），但猫神菜单显示 1/3、
     * 消息里三条全都不生效。发送链路判据是
     *     it.enabled && assistant.modeInjectionIds.contains(it.id)
     * 悬浮窗那条路径只改预设 JSON，从不碰 assistant.modeInjectionIds，
     * 于是「面板里开了，消息里照样不发」。
     */
    @Test
    fun presetToggleAlsoSyncsAssistantBinding() {
        val assistantId = Uuid.random()
        val json = presetJson(
            prompts = listOf(
                Triple("main", "主提示", null),
                Triple("nsfw_off", "关闭NSFW", false),
            ),
            order = listOf("main" to true, "nsfw_off" to false),
        )
        val mainId = entry(group, "main", true).id
        val nsfwId = entry(group, "nsfw_off", true).id
        val assistant = me.rerere.rikkahub.data.model.Assistant(
            id = assistantId,
            name = "测试助手",
            // 两条都绑着（导入时的状态），但预设里 nsfw_off 是关的
            modeInjectionIds = setOf(mainId, nsfwId),
        )
        val before = Settings(
            modeInjections = listOf(entry(group, "main", true), entry(group, "nsfw_off", true)),
            tavernPresets = JsonObject(mapOf(group to JsonPrimitive(json))).toString(),
            assistants = listOf(assistant),
        )

        val after = reconcileModeInjectionsFromPreset(before, group, json, assistantId)

        val bound = after.assistants.first { it.id == assistantId }.modeInjectionIds
        assertTrue("开着的主提示必须仍然绑定", mainId in bound)
        assertFalse("预设里关掉的条目必须从助手绑定里移除，否则消息里照样发", nsfwId in bound)
    }

    @Test
    fun presetEnableBindsEntryToAssistant() {
        val assistantId = Uuid.random()
        val json = presetJson(
            prompts = listOf(
                Triple("main", "主提示", null),
                Triple("nsfw_off", "关闭NSFW", true),
            ),
            order = listOf("main" to true, "nsfw_off" to true),
        )
        val mainId = entry(group, "main", true).id
        val nsfwId = entry(group, "nsfw_off", true).id
        val assistant = me.rerere.rikkahub.data.model.Assistant(
            id = assistantId,
            name = "测试助手",
            modeInjectionIds = setOf(mainId), // 导入时 nsfw_off 没绑
        )
        val before = Settings(
            modeInjections = listOf(entry(group, "main", true), entry(group, "nsfw_off", false)),
            tavernPresets = JsonObject(mapOf(group to JsonPrimitive(json))).toString(),
            assistants = listOf(assistant),
        )

        val after = reconcileModeInjectionsFromPreset(before, group, json, assistantId)

        val bound = after.assistants.first { it.id == assistantId }.modeInjectionIds
        assertTrue("预设里打开的条目必须补进助手绑定，否则开了也不生效", nsfwId in bound)
        assertTrue("原有绑定不能被冲掉", mainId in bound)
    }

    @Test
    fun withoutAssistantIdBindingsAreLeftAlone() {
        val assistantId = Uuid.random()
        val json = presetJson(
            prompts = listOf(Triple("main", "主提示", null)),
            order = listOf("main" to true),
        )
        val mainId = entry(group, "main", true).id
        val before = Settings(
            modeInjections = listOf(entry(group, "main", true)),
            tavernPresets = JsonObject(mapOf(group to JsonPrimitive(json))).toString(),
            assistants = listOf(
                me.rerere.rikkahub.data.model.Assistant(
                    id = assistantId,
                    name = "测试助手",
                    modeInjectionIds = setOf(mainId),
                ),
            ),
        )

        // 不传 assistantId（例如没有活跃会话的场景）时，助手绑定必须原样保留。
        val after = reconcileModeInjectionsFromPreset(before, group, json)
        assertEquals(before.assistants, after.assistants)
    }

    @Test
    fun promptOrderWinsOverPromptsEnabled() {
        // prompts[].enabled=true 但 prompt_order.enabled=false —— ST 以 prompt_order 为准
        val json = presetJson(
            prompts = listOf(Triple("x", "条目X", true)),
            order = listOf("x" to false),
        )
        val before = settingsWith(listOf(entry(group, "x", true)), "{}")
        val after = reconcileModeInjectionsFromPreset(before, group, json)
        assertFalse(after.modeInjections.single().enabled)
    }

    @Test
    fun entriesFromOtherGroupsAreUntouched() {
        val json = presetJson(listOf(Triple("x", "条目X", false)), listOf("x" to false))
        val other = PromptInjection.ModeInjection(
            id = PromptOrderSyncWriter.deterministicPresetEntryId("别的预设", "x"),
            name = "name-x",
            enabled = true,
            presetGroup = "别的预设",
        )
        val before = settingsWith(listOf(entry(group, "x", true), other), "{}")
        val after = reconcileModeInjectionsFromPreset(before, group, json)
        assertTrue("别的预设组不能被牵连", after.modeInjections.first { it.id == other.id }.enabled)
    }

    @Test
    fun malformedPresetJsonNeverClearsSwitches() {
        val before = settingsWith(listOf(entry(group, "x", true)), "{}")
        assertEquals(before, reconcileModeInjectionsFromPreset(before, group, "{not json"))
        assertEquals(before, reconcileModeInjectionsFromPreset(before, group, ""))
        // 结构合法但没有 prompts：索引为空 → 原样返回
        assertEquals(before, reconcileModeInjectionsFromPreset(before, group, """{"temperature":1}"""))
    }

    // ---------------------------------------------------------------- 方向二：预设管理 → 悬浮窗

    @Test
    fun modeInjectionToggleWritesBackToPresetJson() {
        val json = presetJson(
            prompts = listOf(Triple("x", "条目X", true), Triple("y", "条目Y", true)),
            order = listOf("x" to true, "y" to true),
        )
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        val id = PromptOrderSyncWriter.deterministicPresetEntryId(group, "x")

        val patched = applyEntryEnabledToPresetJson(presets, group, id, false)
        assertNotNull("开关变化必须写回预设 JSON", patched)

        val root = Json.parseToJsonElement(patched!!).jsonObject
        val presetObj = Json.parseToJsonElement(root[group]!!.jsonPrimitive.content).jsonObject
        val prompts = presetObj["prompts"]!!.jsonArray
        val x = prompts.first { it.jsonObject["identifier"]!!.jsonPrimitive.content == "x" }.jsonObject
        val y = prompts.first { it.jsonObject["identifier"]!!.jsonPrimitive.content == "y" }.jsonObject
        assertFalse("目标条目 prompts[].enabled 必须变 false", x["enabled"]!!.jsonPrimitive.boolean)
        assertTrue("其它条目不能被改", y["enabled"]!!.jsonPrimitive.boolean)

        val orderEntry = presetObj["prompt_order"]!!.jsonArray[0].jsonObject["order"]!!
            .jsonArray.first { it.jsonObject["identifier"]!!.jsonPrimitive.content == "x" }.jsonObject
        assertFalse("prompt_order 也要同步（悬浮窗以它为准）", orderEntry["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("预设其它字段必须逐字保留", "0.8", presetObj["temperature"]!!.jsonPrimitive.content)
    }

    @Test
    fun noOpWhenValueUnchanged() {
        val json = presetJson(listOf(Triple("x", "条目X", true)), listOf("x" to true))
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        val id = PromptOrderSyncWriter.deterministicPresetEntryId(group, "x")
        assertNull("值本来就一致时不应产生写盘", applyEntryEnabledToPresetJson(presets, group, id, true))
    }

    @Test
    fun unknownEntryOrGroupIsNoOp() {
        val json = presetJson(listOf(Triple("x", "条目X", true)), listOf("x" to true))
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        assertNull(applyEntryEnabledToPresetJson(presets, group, Uuid.random(), false))
        assertNull(applyEntryEnabledToPresetJson(presets, "不存在的预设", PromptOrderSyncWriter.deterministicPresetEntryId(group, "x"), false))
        assertNull(applyEntryEnabledToPresetJson(null, group, PromptOrderSyncWriter.deterministicPresetEntryId(group, "x"), false))
    }

    @Test
    fun diffHelperWritesOnlyChangedEntries() {
        val json = presetJson(
            prompts = listOf(Triple("a", "A", true), Triple("b", "B", true), Triple("c", "C", true)),
            order = listOf("a" to true, "b" to true, "c" to true),
        )
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        val prev = listOf(entry(group, "a", true), entry(group, "b", true), entry(group, "c", true))
        val next = listOf(entry(group, "a", true), entry(group, "b", false), entry(group, "c", false))

        val patched = applyModeInjectionEnabledDiffToPresets(presets, prev, next)
        assertNotNull(patched)
        val presetObj = Json.parseToJsonElement(
            Json.parseToJsonElement(patched!!).jsonObject[group]!!.jsonPrimitive.content,
        ).jsonObject
        val byIdent = presetObj["prompts"]!!.jsonArray.associate {
            val o = it.jsonObject
            o["identifier"]!!.jsonPrimitive.content to o["enabled"]!!.jsonPrimitive.boolean
        }
        assertTrue("未变化的条目保持原值", byIdent.getValue("a"))
        assertFalse(byIdent.getValue("b"))
        assertFalse(byIdent.getValue("c"))
    }

    @Test
    fun diffHelperReturnsNullWhenNothingChanged() {
        val json = presetJson(listOf(Triple("a", "A", true)), listOf("a" to true))
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        val same = listOf(entry(group, "a", true))
        assertNull(applyModeInjectionEnabledDiffToPresets(presets, same, same))
    }

    // ---------------------------------------------------------------- 闭环

    @Test
    fun roundTripIsIdempotent() {
        val json = presetJson(
            prompts = listOf(Triple("x", "条目X", true)),
            order = listOf("x" to true),
        )
        val presets = JsonObject(mapOf(group to JsonPrimitive(json))).toString()
        val id = PromptOrderSyncWriter.deterministicPresetEntryId(group, "x")

        // 预设管理页关掉 → 写回 JSON（根对象形态，与 Settings.tavernPresets 一致）
        val patched = applyEntryEnabledToPresetJson(presets, group, id, false)!!
        // 悬浮窗再写一次同样的值 → 回灌发送链路
        val before = settingsWith(listOf(entry(group, "x", true)), patched)

        // ① 传根对象（Settings.tavernPresets 整体）
        val afterRoot = reconcileModeInjectionsFromPreset(before, group, patched)
        assertFalse("根对象形态也必须能回灌", afterRoot.modeInjections.single().enabled)

        // ② 传预设对象本身（悬浮窗 saveTavernPreset 的 rawJson 就是这个形态）
        val presetOnly = Json.parseToJsonElement(patched).jsonObject[group]!!.jsonPrimitive.content
        val afterPreset = reconcileModeInjectionsFromPreset(before, group, presetOnly)
        assertFalse("预设对象形态也必须能回灌", afterPreset.modeInjections.single().enabled)

        // 再跑一遍不应有任何变化
        assertEquals(afterRoot, reconcileModeInjectionsFromPreset(afterRoot, group, patched))
    }
}
