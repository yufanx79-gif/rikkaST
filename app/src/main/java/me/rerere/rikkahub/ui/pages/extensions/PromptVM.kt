package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Lorebook

class PromptVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    // [P0-3 修复] 改为 Eagerly 并直接用 settingsStore.settingsFlow.value 作为初始值，
    // 避免进入页面首帧拿到 Settings.dummy() (init=true) 导致紧接着的写操作被拒绝或基于旧快照覆盖。
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settingsFlow.value)

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /**
     * [P0-3 修复] 基于 SettingsStore 当前最新权威快照的 CAS 更新，
     * 彻底杜绝同一帧内多次调用 `updateSettings(settings.copy(...))` 时因组合期快照未刷新而互相回滚覆盖。
     */
    fun patchSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    fun addLorebook(lorebook: Lorebook) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(lorebooks = current.lorebooks + lorebook)
            }
        }
    }
}
