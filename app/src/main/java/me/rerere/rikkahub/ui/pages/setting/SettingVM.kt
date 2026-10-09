package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.MessageStyleSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /**
     * [v227 D4] 窄写回：只改「显示设置」一个 key，且变换作用在**当前**值上。
     *
     * 设置页的开关一律走这里，不要再 `updateSettings(settings.copy(...))` —— 后者会用组合期
     * 捕获的旧快照覆盖并发改动（连点两个开关时前一个被回滚），并把 ~80 个 key 全部重写一遍。
     */
    fun patchDisplaySetting(transform: (DisplaySetting) -> DisplaySetting) {
        viewModelScope.launch {
            settingsStore.patchDisplaySetting(transform)
        }
    }

    /**
     * [v227 D4] 通用「变换作用在当前值上」的写回。
     *
     * 与 [patchDisplaySetting] 的区别：它不限制改哪个字段，但仍然**不用组合期快照** ——
     * `SettingsStore.update(fn)` 会把 `fn` 作用在 `settingsFlow.value`（当前值）上。
     * 用于那些「一个页面里散落十几个字段」的入口（模型页 / 主题页 / MCP 页 …）：
     * 旧写法 `updateSettings(settings.copy(x = v))` 里的 `settings` 是组合期捕获的旧快照，
     * 连点两个开关时第二次点击会把第一次的改动**回滚**（真机「开关卡动 / 跳一下」）。
     *
     * ⚠️ 仍然会重写全量 key；能定位到单一字段的入口请优先用 [patchDisplaySetting] / [patchMessageStyle]。
     */
    fun patchSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    /** [v227 D4] 窄写回：只改「消息样式」一个 key。 */
    fun patchMessageStyle(transform: (MessageStyleSetting) -> MessageStyleSetting) {
        viewModelScope.launch {
            settingsStore.patchMessageStyle(transform)
        }
    }
}
