package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

/**
 * V4：内置 LaTeX 渲染默认关闭（为后续前端渲染让路）。
 *
 * 旧存档的 display_setting 是整块 JSON（encodeDefaults=true），其中显式保存了
 * enableLatexRendering=true；光改数据类默认值救不了老数据，这里做一次性迁移：
 * 若旧数据里该字段为 true，则翻转为 false。迁移只执行一次，之后用户仍可在
 * 「设置」中手动重新开启。
 */
class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 4
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val raw = prefs[SettingsStore.DISPLAY_SETTING]
        if (raw != null) {
            runCatching {
                val obj = JsonInstant.parseToJsonElement(raw).jsonObject
                if (obj["enableLatexRendering"] != null) {
                    prefs[SettingsStore.DISPLAY_SETTING] = JsonInstant.encodeToString(
                        JsonObject(
                            obj.toMutableMap().apply {
                                put("enableLatexRendering", JsonPrimitive(false))
                            }
                        )
                    )
                }
            }.onFailure {
                // 解析失败不阻断启动；读取侧新的默认值 false 仍会兜底
            }
        }
        prefs[SettingsStore.VERSION] = 4
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}
