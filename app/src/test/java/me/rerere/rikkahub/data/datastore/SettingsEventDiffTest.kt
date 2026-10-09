package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v228 S2] 设置变更 -> ST 事件 的差分门控（`SETTINGS_UPDATED` / `WORLDINFO_SETTINGS_UPDATED`）。
 *
 * 这条判据同时被**两个**写入口使用：全量 `SettingsStore.update()` 与窄写回 `patchKey()`。
 * 判据必须：无关写入不发（否则扩展每次无关写入都重渲染）、真变了必发（否则滑块类窄写回静默丢事件）。
 */
class SettingsEventDiffTest {

    private val base = Settings(init = true)

    @Test
    fun identicalSettingsFireNothing() {
        assertTrue(settingsEventsFor(base, base.copy()).isEmpty())
    }

    @Test
    fun unrelatedChangeFiresSettingsUpdatedOnly() {
        val next = base.copy(
            dynamicColor = !base.dynamicColor,
            developerMode = !base.developerMode,
        )
        assertEquals(listOf("settings_updated"), settingsEventsFor(base, next))
    }

    @Test
    fun worldInfoChangeFiresBothInOrder() {
        val next = base.copy(worldInfoDepth = base.worldInfoDepth + 1)
        assertEquals(listOf("settings_updated", "worldinfo_settings_updated"), settingsEventsFor(base, next))
    }

    @Test
    fun everyWorldInfoFieldAlsoFiresWorldInfoEvent() {
        val cases = listOf(
            base.copy(worldInfoBudget = base.worldInfoBudget + 1),
            base.copy(worldInfoBudgetCap = base.worldInfoBudgetCap + 1),
            base.copy(worldInfoMinActivations = base.worldInfoMinActivations + 1),
            base.copy(worldInfoMinActivationsDepthMax = base.worldInfoMinActivationsDepthMax + 1),
            base.copy(worldInfoRecursive = !base.worldInfoRecursive),
            base.copy(worldInfoMaxRecursionSteps = base.worldInfoMaxRecursionSteps + 1),
            base.copy(worldInfoDepth = base.worldInfoDepth + 1),
            base.copy(worldInfoCharacterStrategy = base.worldInfoCharacterStrategy + 1),
            base.copy(worldInfoOverflowAlert = !base.worldInfoOverflowAlert),
            base.copy(worldInfoUseGroupScoring = !base.worldInfoUseGroupScoring),
        )
        cases.forEachIndexed { i, next ->
            assertEquals("worldInfo 字段 #$i", listOf("settings_updated", "worldinfo_settings_updated"), settingsEventsFor(base, next))
        }
    }
}
