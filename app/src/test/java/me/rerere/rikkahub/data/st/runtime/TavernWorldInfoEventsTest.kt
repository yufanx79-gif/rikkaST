package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v228 S5] 世界书事件载荷（`worldinfo_entries_loaded` / `worldinfo_scan_done`）。
 *
 * 判据（必须唯一）：字段名逐字对齐 ST 真源 `world-info.js:4604` / `:5150-5175`；
 * 只发语义等价的字段（不编默认值）；状态数值走官方 `scan_state`（NONE=0/INITIAL=1/RECURSION=2/MIN=3）。
 */
class TavernWorldInfoEventsTest {

    private fun entry(
        name: String,
        keywords: List<String> = listOf("kw-$name"),
        enabled: Boolean = true,
        priority: Int = 100,
    ) = PromptInjection.RegexInjection(
        name = name,
        keywords = keywords,
        content = "content-$name",
        enabled = enabled,
        priority = priority,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
    )

    @Test
    fun entriesLoadedPayloadHasStFieldNames() {
        val o = TavernWorldInfoEvents.parse(
            TavernWorldInfoEvents.entriesLoadedPayload(
                globalLore = listOf(entry("g1")),
                characterLore = listOf(entry("c1"), entry("c2")),
            )
        )
        assertEquals(
            setOf("globalLore", "characterLore", "chatLore", "personaLore"),
            o.keys,
        )
        assertEquals(1, o["globalLore"]!!.jsonArray.size)
        assertEquals(2, o["characterLore"]!!.jsonArray.size)
        assertEquals(0, o["chatLore"]!!.jsonArray.size)
        assertEquals(0, o["personaLore"]!!.jsonArray.size)
        // 条目 uid = 载荷内下标（可索引、唯一）
        assertEquals(0, o["globalLore"]!!.jsonArray[0].jsonObject["uid"]!!.jsonPrimitive.int)
        assertEquals(1, o["characterLore"]!!.jsonArray[1].jsonObject["uid"]!!.jsonPrimitive.int)
    }

    @Test
    fun entryFieldsMapToStNamesOnly() {
        val disabled = entry("d", enabled = false, priority = 42)
        val o = TavernWorldInfoEvents.entryJson(disabled, uid = 7)
        assertEquals(7, o["uid"]!!.jsonPrimitive.int)
        assertEquals("d", o["comment"]!!.jsonPrimitive.content)
        assertEquals(listOf("kw-d"), o["key"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("content-d", o["content"]!!.jsonPrimitive.content)
        assertTrue(o["disable"]!!.jsonPrimitive.content.toBoolean())   // ST disable = !enabled
        assertEquals(42, o["order"]!!.jsonPrimitive.int)               // ST order = 本地 priority
        assertTrue(o.containsKey("keysecondary"))
        assertTrue(o.containsKey("useProbability"))
        assertTrue(o.containsKey("automationId"))
        // 不编造：本地语义不同的 ST 字段不许凭空出现
        assertFalse(o.containsKey("position"))
        assertFalse(o.containsKey("depth"))
    }

    @Test
    fun scanDonePayloadMatchesStArgShape() {
        val sorted = listOf(Lorebook(name = "book") to entry("s1"))
        val o = TavernWorldInfoEvents.parse(
            TavernWorldInfoEvents.scanDonePayload(
                loopCount = 3,
                currentState = 0,     // 本地 INITIAL
                nextState = -1,       // 本地「无下一步」
                newAll = listOf(entry("n1")),
                newSuccessful = listOf(entry("n1")),
                activatedEntries = listOf(entry("a1")),
                activatedText = "text",
                sortedEntriesJson = TavernWorldInfoEvents.sortedEntriesJson(sorted),
                recursionLevel = 0,
                availableLevels = listOf(1, 2),
                budget = 512,
                overflowed = false,
            )
        )
        assertEquals(
            setOf("state", "new", "activated", "sortedEntries", "recursionDelay", "budget", "timedEffects"),
            o.keys,
        )
        // 官方 scan_state 数值换算：本地 0(INITIAL) -> 1；本地 -1(NONE) -> 0
        assertEquals(1, o["state"]!!.jsonObject["current"]!!.jsonPrimitive.int)
        assertEquals(0, o["state"]!!.jsonObject["next"]!!.jsonPrimitive.int)
        assertEquals(3, o["state"]!!.jsonObject["loopCount"]!!.jsonPrimitive.int)
        assertEquals(1, o["new"]!!.jsonObject["all"]!!.jsonArray.size)
        assertEquals(1, o["new"]!!.jsonObject["successful"]!!.jsonArray.size)
        assertEquals(1, o["activated"]!!.jsonObject["entries"]!!.jsonArray.size)
        assertEquals("text", o["activated"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(1, o["sortedEntries"]!!.jsonArray.size)
        assertEquals(2, o["recursionDelay"]!!.jsonObject["availableLevels"]!!.jsonArray.size)
        assertEquals(512, o["budget"]!!.jsonObject["current"]!!.jsonPrimitive.int)
        assertFalse(o["budget"]!!.jsonObject["overflowed"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(0, o["timedEffects"]!!.jsonObject.size)
    }

    @Test
    fun stateMappingMatchesOfficialEnum() {
        assertEquals(1, TavernWorldInfoEvents.mapState(0))
        assertEquals(2, TavernWorldInfoEvents.mapState(1))
        assertEquals(3, TavernWorldInfoEvents.mapState(2))
        assertEquals(0, TavernWorldInfoEvents.mapState(-1))
    }

    @Test
    fun eventNamesMatchStEventsJs() {
        assertEquals("worldinfo_entries_loaded", TavernWorldInfoEvents.EVENT_ENTRIES_LOADED)
        assertEquals("worldinfo_scan_done", TavernWorldInfoEvents.EVENT_SCAN_DONE)
    }
}
