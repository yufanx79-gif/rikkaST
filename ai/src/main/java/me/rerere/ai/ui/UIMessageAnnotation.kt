package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UIMessageAnnotation {
    @Serializable
    @SerialName("url_citation")
    data class UrlCitation(
        val title: String,
        val url: String
    ) : UIMessageAnnotation()

    /** 角色卡 mes_example 解析出的示例消息标记（EMTop/EMBottom 锚点用） */
    @Serializable
    @SerialName("example_message")
    data object ExampleMessage : UIMessageAnnotation()

    /** 角色卡字段消息标记（官方独立 system 消息，世界书 before/after char 锚点用） */
    @Serializable
    @SerialName("character_card")
    data object CharacterCardData : UIMessageAnnotation()

    /** ST /hide：消息从提示词中隐藏（对齐 ST `is_system` 隐藏标记；UI 淡化显示、可 /unhide 恢复）。 */
    @Serializable
    @SerialName("hidden")
    data object Hidden : UIMessageAnnotation()

    /** ST Checkpoints（书签）：消息链接的检查点会话（对齐 ST `extra.bookmark_link`）。 */
    @Serializable
    @SerialName("st_checkpoint_link")
    data class StCheckpointLink(
        val conversationId: String,
        val name: String,
    ) : UIMessageAnnotation()
}
