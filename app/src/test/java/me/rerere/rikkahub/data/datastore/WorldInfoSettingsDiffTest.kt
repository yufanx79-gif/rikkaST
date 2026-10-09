package me.rerere.rikkahub.data.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v227 N1] 世界书设置差分门控（`worldinfo_settings_updated`）。
 *
 * 判据必须「只对世界书字段敏感」：不敏感 -> 扩展面板不刷新；过于敏感 -> 每次无关设置写入都让扩展重渲染。
 */
class WorldInfoSettingsDiffTest {

    private val base = Settings(init = true)

    @Test
    fun identicalSettingsDoNotTrigger() {
        assertFalse(worldInfoSettingsChanged(base, base.copy()))
    }

    @Test
    fun unrelatedSettingsDoNotTrigger() {
        // 非世界书字段变化（开关 / 主题 / 模型）都不该触发
        val next = base.copy(
            dynamicColor = !base.dynamicColor,
            enableSuggestion = !base.enableSuggestion,   // 非世界书字段（避开 Uuid 的 opt-in）
            developerMode = !base.developerMode,
        )
        assertFalse(worldInfoSettingsChanged(base, next))
    }

    @Test
    fun everyWorldInfoFieldTriggers() {
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
            assertTrue("worldInfo 字段 #$i 变化必须触发", worldInfoSettingsChanged(base, next))
        }
    }
}
