package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Instruct 序列模板（对齐 SillyTavern Advanced Formatting → Instruct Mode）。
 *
 * 语义说明：
 * - RikkaHub 是 Chat-Completion 架构；本模板用于把「文本补全风格的指令序列」应用到消息数组上，
 *   off 时完全不动消息（默认）。
 * - 故事串（首条系统消息）使用 [storyStringPrefix] / [storyStringSuffix] 包裹；
 * - 其余消息按角色取序列：SYSTEM → system 对；USER → input 对；ASSISTANT → output 对；
 * - 首/末条用户/助手消息优先使用 first/last 变体（为空回退默认序列）；
 * - [wrap] 时前缀与内容之间以换行分隔，且无后缀时自动补 '\n'（对齐 ST `formatInstructModeChat`）；
 * - [macro] 时序列内展开宏（{{char}}/{{user}}/{{name}} 等，{{name}} 取消息说话人）。
 * - [skipExamples] 时带 ExampleMessage 注解的消息不做序列化（对齐 ST `skip_examples`）。
 * - [activationRegex] 非空且不匹配模型 ID 时，整个模板停用（对齐 ST `activation_regex`）。
 *
 * 未实现（属 ST Text-Completion 专属语义，聊天架构不适用）：user_alignment_message / stop_sequence /
 * sequences_as_stop_strings / bind_to_context / last_system_sequence（quiet prompt 专用）。
 */
@Serializable
data class InstructTemplate(
    /** 总开关；false = 不干预消息（默认） */
    val enabled: Boolean = false,
    /** 故事串（首条系统消息）前缀 */
    val storyStringPrefix: String = "",
    /** 故事串（首条系统消息）后缀 */
    val storyStringSuffix: String = "",
    /** 系统消息序列 */
    val systemSequence: String = "",
    val systemSuffix: String = "",
    /** 用户消息序列（first/last 变体优先） */
    val inputSequence: String = "",
    val inputSuffix: String = "",
    val firstInputSequence: String = "",
    val lastInputSequence: String = "",
    /** 助手消息序列（first/last 变体优先） */
    val outputSequence: String = "",
    val outputSuffix: String = "",
    val firstOutputSequence: String = "",
    val lastOutputSequence: String = "",
    /** 序列换行包裹（对齐 ST wrap；无后缀时自动补 '\n'） */
    val wrap: Boolean = true,
    /** 序列内展开宏（对齐 ST macro） */
    val macro: Boolean = true,
    /** 名字包含策略（对齐 ST names_behavior；默认 NONE 更保守） */
    val namesBehavior: InstructNamesBehavior = InstructNamesBehavior.NONE,
    /** 系统消息按用户序列处理（对齐 ST system_same_as_user） */
    val systemSameAsUser: Boolean = false,
    /** 跳过示例消息的序列化（对齐 ST skip_examples） */
    val skipExamples: Boolean = false,
    /** 激活正则：非空且不匹配模型 ID 时停用（对齐 ST activation_regex） */
    val activationRegex: String = "",
)

/** 名字包含策略（对齐 ST names_behavior：none / force / always）。 */
@Serializable
enum class InstructNamesBehavior {
    @SerialName("none")
    NONE,

    /** 仅当消息自带说话人名（群聊/多角色）时包含 */
    @SerialName("force")
    FORCE,

    /** 总是包含（用户 → 用户名；助手 → 角色名） */
    @SerialName("always")
    ALWAYS,
}