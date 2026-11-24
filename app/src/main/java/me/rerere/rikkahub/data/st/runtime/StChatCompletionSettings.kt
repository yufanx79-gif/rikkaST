package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentChatModel

/**
 * [v250] 构造 ST `oai_settings` / `SillyTavern.getContext().chatCompletionSettings` 的真实值。
 *
 * ## 为什么需要（不是「给某个插件单独适配」）
 *
 * 社区预设脚本判断「当前用什么模型」的**唯一**标准接口就是
 * `getContext().chatCompletionSettings`（ST st-context.js:166 / openai.js:520 `oai_settings`）。
 * 典型用法：
 * - 双马尾 v4.6.1 `detectActiveModelName()` 读 `custom_model / google_model / openai_model …`；
 * - 各种「按模型分流思考档位 / 抗截断」脚本读 `chat_completion_source`；
 * - `getChatCompletionModel(settings)`（st-compat/openai.js:73）先读 `${source}_model` 再回退。
 *
 * 我们此前给的是硬编码 stub：`{ chat_completion_source:'custom', openai_model:'unknown' }`，
 * 没有 `custom_model`，于是**所有**依赖该接口的脚本都读到空 → GUI 显示「未读取到当前模型」，
 * 按模型分支的逻辑全部落到 unknown。这是宿主契约缺字段，一次补齐全体受益。
 *
 * ## 对齐口径
 *
 * - `chat_completion_source`：按 provider 类型映射到 ST 的 source 名
 *   （openai / claude / makersuite / vertexai / custom …）。非官方 provider 一律 `custom`，
 *   与 ST「自定义 OpenAI 兼容端点」的语义一致。
 * - `<source>_model`：ST 每个 source 有独立的 model 字段名，这里**同时**写入
 *   当前 source 对应的字段与通用回退字段（`custom_model` / `openai_model` / `model`），
 *   让任何读取顺序都能拿到真实模型名。
 * - `preset_settings_openai`：当前预设名（部分脚本用它做展示）。
 * - 采样默认值（temperature / top_p / openai_max_tokens）：取助手覆盖优先级，
 *   与真实生成请求一致，避免脚本读到的值与实际发送不一致。
 *
 * 只返回**非敏感**字段：不含 apiKey / baseUrl / 代理密码。
 */
object StChatCompletionSettings {

    /** provider 类型 → ST `chat_completion_source`。 */
    internal fun sourceOf(provider: ProviderSetting?): String = when (provider) {
        is ProviderSetting.Google -> if (provider.vertexAI) "vertexai" else "makersuite"
        is ProviderSetting.Claude -> "claude"
        is ProviderSetting.ChatGPT -> "openai"
        is ProviderSetting.Grok -> "xai"
        is ProviderSetting.OpenAI -> "custom"
        else -> "custom"
    }

    /** ST 每个 source 对应的模型字段名（oai_settings 里是并列的多个 `*_model`）。 */
    internal fun modelFieldOf(source: String): String = when (source) {
        "openai" -> "openai_model"
        "claude" -> "claude_model"
        "makersuite" -> "google_model"
        "vertexai" -> "vertexai_model"
        "openrouter" -> "openrouter_model"
        "xai" -> "xai_model"
        else -> "custom_model"
    }

    /** 当前生效的模型：优先助手绑定，其次全局。 */
    internal fun activeModel(settings: Settings, assistantChatModelId: kotlin.uuid.Uuid? = null): Pair<Model, ProviderSetting>? {
        val model = settings.findModelById(assistantChatModelId ?: settings.chatModelId) ?: settings.getCurrentChatModel() ?: return null
        var owner: ProviderSetting? = null
        for (p in settings.providers) {
            if (p.models.any { it.id == model.id }) { owner = p; break }
        }
        val provider = model.providerOverwrite ?: owner ?: return null
        return model to provider
    }

    fun buildJson(
        settings: Settings,
        assistantChatModelId: kotlin.uuid.Uuid? = null,
        assistantTemperature: Float? = null,
        assistantTopP: Float? = null,
        assistantMaxTokens: Int? = null,
        assistantStreamOutput: Boolean = true,
        presetName: String = "",
        presetTemperature: Float? = null,
        presetTopP: Float? = null,
        presetMaxTokens: Int? = null,
        /** 当前预设的 prompt 条目（ST `oai_settings.prompts`；SPreset inject.js 之类扩展会遍历它）。 */
        presetPrompts: JsonArray? = null,
        /** 当前预设的 prompt_order（ST `oai_settings.prompt_order`）。 */
        presetPromptOrder: JsonArray? = null,
        /** ST `oai_settings.extensions`（扩展把配置写在这里，脚本读回时不能是 undefined）。 */
        presetExtensions: JsonObject? = null,
    ): String {
        val (model, provider) = activeModel(settings, assistantChatModelId)
            ?: return buildJsonObject {
                // 未配置模型时也必须给出**完整 ST 形状**：脚本会直接遍历 prompts、
                // 往 extensions 写配置，缺字段就是 "is not iterable" 直接抛错。
                put("chat_completion_source", JsonPrimitive("custom"))
                put("prompts", presetPrompts ?: JsonArray(emptyList()))
                put("prompt_order", presetPromptOrder ?: JsonArray(emptyList()))
                put("extensions", presetExtensions ?: JsonObject(emptyMap()))
            }.toString()
        val modelName = model.displayName.ifBlank { model.modelId }
        val source = sourceOf(provider)
        val primaryField = modelFieldOf(source)
        return buildJsonObject {
            put("chat_completion_source", JsonPrimitive(source))
            // 主字段 + 通用回退：任何读取顺序都能拿到真实模型名
            put(primaryField, JsonPrimitive(modelName))
            if (primaryField != "custom_model") put("custom_model", JsonPrimitive(modelName))
            if (primaryField != "openai_model") put("openai_model", JsonPrimitive(modelName))
            put("model", JsonPrimitive(modelName))
            put("reverse_proxy", JsonPrimitive(""))
            put("stream_openai", JsonPrimitive(assistantStreamOutput))
            put("temperature", JsonPrimitive(assistantTemperature ?: presetTemperature ?: 1.0f))
            put("top_p", JsonPrimitive(assistantTopP ?: presetTopP ?: 1.0f))
            put("openai_max_tokens", JsonPrimitive(assistantMaxTokens ?: presetMaxTokens ?: 4096))
            put("preset_settings_openai", JsonPrimitive(presetName))
            // [v250] ST 的 oai_settings 一定带这三个键 —— 扩展脚本（如 SPreset inject.js）
            // 会直接遍历 prompts / 往 extensions 写配置；缺了就是 "xxx is not iterable" 直接抛错。
            put("prompts", presetPrompts ?: JsonArray(emptyList()))
            put("prompt_order", presetPromptOrder ?: JsonArray(emptyList()))
            put("extensions", presetExtensions ?: JsonObject(emptyMap()))
        }.toString()
    }
}
