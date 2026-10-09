package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant

/**
 * [v225 F2] 消息样式在 DataStore 里的键名。
 *
 * 抽成常量：单测可以钉住磁盘键名不被误改（改名 = 老用户存档读不回来）。
 */
internal const val MESSAGE_STYLE_KEY_NAME: String = "message_style"

/**
 * [v225 F2] 消息样式的 DataStore 编解码（纯函数，可单测）。
 *
 * 缺陷回顾：v222~v224 的 PreferencesStore 既没有在 settingsFlowRaw 里读这个键，
 * 也没有在 update() 里写这个键 —— 于是 update() 先改内存（settingsFlow.value = settings），
 * 随后 DataStore 重新发射，settingsFlowRaw 解出默认 MessageStyleSetting()，
 * 把内存里刚改好的值覆盖回默认（开关回弹、磨砂玻璃不生效）。
 */
internal fun encodeMessageStyle(value: MessageStyleSetting): String =
    JsonInstant.encodeToString(MessageStyleSetting.serializer(), value)

/** 老存档没有这个键（null）→ 回落默认值。 */
internal fun decodeMessageStyle(raw: String?): MessageStyleSetting =
    raw?.let { JsonInstant.decodeFromString(MessageStyleSetting.serializer(), it) }
        ?: MessageStyleSetting()
