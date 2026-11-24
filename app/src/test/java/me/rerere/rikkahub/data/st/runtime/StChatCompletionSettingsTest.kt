package me.rerere.rikkahub.data.st.runtime

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v250] `chatCompletionSettings` 契约回归。
 *
 * 真机背景：双马尾 / SPreset inject.js 等社区脚本一律通过
 * `SillyTavern.getContext().chatCompletionSettings` 读「当前模型」与 `prompts`。
 * 我们此前给的是写死 stub（`openai_model:'unknown'`、无 `custom_model`、无 `prompts`），
 * 于是面板显示「未读取到酒馆当前模型」、SPreset 直接 `prompts is not iterable` 抛错。
 *
 * 本测试锁定三件事：
 *  1. 模型名能落到**所有** source 字段（任何读取顺序都能拿到）；
 *  2. `chat_completion_source` 按 provider 类型正确映射；
 *  3. `prompts` / `prompt_order` / `extensions` 一定存在（不能 undefined）。
 */
class StChatCompletionSettingsTest {

    private fun googleProvider(modelId: String = "gemini-3-pro") = ProviderSetting.Google(
        id = Uuid.random(),
        name = "Google",
        models = listOf(
            Model(
                modelId = modelId,
                displayName = "Gemini 3 Pro",
                id = Uuid.random(),
                type = ModelType.CHAT,
            ),
        ),
    )

    private fun openaiProvider() = ProviderSetting.OpenAI(
        id = Uuid.random(),
        name = "OpenAI",
        models = listOf(
            Model(modelId = "gpt-5.6", displayName = "GPT-5.6", id = Uuid.random(), type = ModelType.CHAT),
        ),
    )

    @Test
    fun googleProviderMapsToMakersuiteAndWritesGoogleModel() {
        val provider = googleProvider()
        val model = provider.models.first()
        val settings = Settings(providers = listOf(provider), chatModelId = model.id)
        val json = StChatCompletionSettings.buildJson(settings)
        assertTrue(json, json.contains("\"chat_completion_source\":\"makersuite\""))
        assertTrue(json, json.contains("\"google_model\":\"Gemini 3 Pro\""))
        // 通用回退字段也要有，脚本按 custom_model / openai_model 读同样能拿到
        assertTrue(json, json.contains("\"custom_model\":\"Gemini 3 Pro\""))
        assertTrue(json, json.contains("\"openai_model\":\"Gemini 3 Pro\""))
    }

    @Test
    fun vertexAiMapsToVertexai() {
        val provider = googleProvider().copy(vertexAI = true)
        val model = provider.models.first()
        val settings = Settings(providers = listOf(provider), chatModelId = model.id)
        val json = StChatCompletionSettings.buildJson(settings)
        assertTrue(json, json.contains("\"chat_completion_source\":\"vertexai\""))
        assertTrue(json, json.contains("\"vertexai_model\":\"Gemini 3 Pro\""))
    }

    @Test
    fun openaiCompatibleMapsToCustom() {
        val provider = openaiProvider()
        val model = provider.models.first()
        val settings = Settings(providers = listOf(provider), chatModelId = model.id)
        val json = StChatCompletionSettings.buildJson(settings)
        assertTrue(json, json.contains("\"chat_completion_source\":\"custom\""))
        assertTrue(json, json.contains("\"custom_model\":\"GPT-5.6\""))
    }

    @Test
    fun promptsAndExtensionsAlwaysPresent() {
        val provider = googleProvider()
        val model = provider.models.first()
        val settings = Settings(providers = listOf(provider), chatModelId = model.id)
        val json = StChatCompletionSettings.buildJson(settings)
        // SPreset inject.js 会直接 `.prompts` 迭代 —— 缺字段就是 TypeError
        assertTrue(json, json.contains("\"prompts\":[]"))
        assertTrue(json, json.contains("\"prompt_order\":[]"))
        assertTrue(json, json.contains("\"extensions\":{}"))
    }

    @Test
    fun emptySettingsStillReturnsValidShape() {
        val json = StChatCompletionSettings.buildJson(Settings())
        assertTrue(json, json.contains("\"chat_completion_source\""))
        assertTrue(json, json.contains("\"prompts\""))
    }

    @Test
    fun sourceOfAndModelFieldAreConsistent() {
        assertEquals("makersuite", StChatCompletionSettings.sourceOf(googleProvider()))
        assertEquals("vertexai", StChatCompletionSettings.sourceOf(googleProvider().copy(vertexAI = true)))
        assertEquals("custom", StChatCompletionSettings.sourceOf(openaiProvider()))
        assertEquals("google_model", StChatCompletionSettings.modelFieldOf("makersuite"))
        assertEquals("vertexai_model", StChatCompletionSettings.modelFieldOf("vertexai"))
        assertEquals("custom_model", StChatCompletionSettings.modelFieldOf("custom"))
    }
}
