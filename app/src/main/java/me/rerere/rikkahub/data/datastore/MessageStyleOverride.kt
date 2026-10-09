package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

/**
 * [v233 主人补充需求] 助手级「消息样式」覆盖。
 *
 * 背景：主人指出「有些角色卡不适合用来做消息分块这种东西」，要求在**助手编辑页**里单独改，
 * 范围只在该助手内。设计原则：
 *  - 每个字段三态：null = **跟随全局**（settings.messageStyle 同名字段）；
 *  - 只覆盖「分块/贴合」这类观感字段，不覆盖主题色（light/dark 仍走全局，避免调色被单卡劫持）；
 *  - [Assistant.messageStyleOverride] 为 null = 完全跟随全局（存量助手零行为变化）。
 */
@Serializable
data class MessageStyleOverride(
    /** 覆盖「助手回复遇空行拆分多气泡」。null = 跟随全局。 */
    val splitSegmentsAsBubbles: Boolean? = null,
    /** 覆盖「助手气泡贴合内容」。null = 跟随全局。 */
    val assistantBubbleWrapContent: Boolean? = null,
)

/** 解析某助手实际生效的消息样式：助手覆盖优先，其余字段回落全局。 */
fun resolveMessageStyle(global: MessageStyleSetting, override: MessageStyleOverride?): MessageStyleSetting {
    if (override == null) return global
    return global.copy(
        splitSegmentsAsBubbles = override.splitSegmentsAsBubbles ?: global.splitSegmentsAsBubbles,
        assistantBubbleWrapContent = override.assistantBubbleWrapContent ?: global.assistantBubbleWrapContent,
    )
}
