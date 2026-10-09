package me.rerere.rikkahub.data.st.expressions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 表情分类方式（对齐官方 `EXPRESSION_API` 的本地子集）。
 *
 * 官方 5 种（index.js:81-87）：local / extras / llm / webllm / none。
 * rikkaST 本身就客户端模型，天然走 `llm`（用当前聊天模型分类，无需任何服务端接口），
 * 因此本批只保留两种：
 * - [LLM]：调用该助手当前配置的模型，用官方同款提示词分类；
 * - [NONE]：关闭分类（等价官方 none，显示端会回退兜底标签/不显示）。
 */
@Serializable
enum class ExpressionClassifier {
    @SerialName("llm")
    LLM,

    @SerialName("none")
    NONE;

    companion object {
        /** 宽松解析（未知值回退 LLM，保证旧版本/坏数据可读） */
        fun parse(raw: String?): ExpressionClassifier = when (raw?.trim()?.lowercase()) {
            "none" -> NONE
            else -> LLM
        }
    }
}
