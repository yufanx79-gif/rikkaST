package me.rerere.rikkahub.ui.theme

import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * 自定义主题的 JSON 编解码器（v217 / A5）。
 *
 * 这是 rikkaST 与 Kelivo 之间**唯一**一个真实的互操作点：
 * Kelivo（Flutter / AGPL-3.0）的 lib/theme/custom_theme.dart 导出的 key 就是
 * primaryColorArgb / secondaryColorArgb / tertiaryColorArgb，与本包 [CustomTheme] 的字段
 * 逐字同名 —— Kelivo 源码注释里明确写了「兼容 RikkaHub 导出格式」。
 * 所以主人可以在 Kelivo 里调好主题、导出、直接粘进 rikkaST 的导入框（反向也成立）。
 *
 * 这里没有一行 Kelivo 的代码，只是对齐了一个 JSON 字段约定，不触碰 AGPL。
 *
 * [CustomThemeJson] 的配置属于**对外契约**，不要随手改：
 *  - ignoreUnknownKeys = true：容忍对方多出来的 key（两边都会各自加字段），
 *    多余 key 一律忽略，不影响导入。
 *  - encodeDefaults = true：**必须**。默认的 encodeDefaults=false 会「值等于默认值就不写」，
 *    于是当主题的 primaryColorArgb 恰好是默认色 0xFF6750A4 时，导出的 JSON 会丢掉
 *    primaryColorArgb —— Kelivo 导入后只能退回它自己的默认色（v217 单测实测抓到的真 bug）。
 *    主题 JSON 是对外契约，必须字段齐全，不能省。
 *  - prettyPrint = true：导出文本是给人看的（当前走剪贴板）。
 *
 * 字段名与容错行为由 CustomThemeJsonTest 锁死 —— 改坏了单测会红。
 */
val CustomThemeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * 解析一段自定义主题 JSON（Kelivo 导出格式 / 本 App 导出格式都能吃）。
 *
 * 容错规则：
 *  - 多余 key 忽略（见 [CustomThemeJson] 的 ignoreUnknownKeys）。
 *  - 缺 id 或 id 全是空白 → 补一个新的 UUID。Kelivo 的导出格式本来就不保证带 id，
 *    这里显式兜底，避免出现 id="" 的「幽灵主题」（选中态 / 删除都会错乱）。
 *
 * @throws kotlinx.serialization.SerializationException 文本不是合法主题 JSON 时抛出；
 *         调用方（导入对话框）负责把 message 显示给用户。
 */
fun decodeCustomTheme(text: String): CustomTheme {
    val theme = CustomThemeJson.decodeFromString(CustomTheme.serializer(), text)
    return if (theme.id.isBlank()) theme.copy(id = Uuid.random().toString()) else theme
}

/** 把主题导出成 JSON 文本（当前用于剪贴板）。 */
fun encodeCustomTheme(theme: CustomTheme): String =
    CustomThemeJson.encodeToString(CustomTheme.serializer(), theme)
