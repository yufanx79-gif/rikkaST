package me.rerere.rikkahub.ui.pages.onboarding

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TavernCharacterData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class OnboardingDecisionTest {

    @Test
    fun dummyInitSettingsDoesNotTriggerOnboarding() {
        val dummy = Settings.dummy()
        assertFalse(shouldShowOnboarding(dummy, dismissedByUser = false))
    }

    @Test
    fun unconfiguredLoadedSettingsTriggersOnboardingUntilModelSelectedOrDismissed() {
        val loadedNoModel = Settings(
            init = false,
            providers = emptyList(),
        )
        assertTrue(shouldShowOnboarding(loadedNoModel, dismissedByUser = false))
        assertFalse(shouldShowOnboarding(loadedNoModel, dismissedByUser = true))

        val chatModel = Model(
            id = Uuid.random(),
            modelId = "deepseek-chat",
            displayName = "DeepSeek Chat",
            type = ModelType.CHAT,
        )
        val provider = ProviderSetting.OpenAI(
            id = Uuid.random(),
            enabled = true,
            name = "DeepSeek",
            models = listOf(chatModel),
            apiKey = "sk-test",
            baseUrl = "https://api.deepseek.com/v1",
        )
        val configured = loadedNoModel.copy(
            providers = listOf(provider),
            chatModelId = chatModel.id,
            assistants = listOf(
                Assistant(
                    id = Uuid.random(),
                    name = "早川",
                    tavernData = TavernCharacterData(name = "早川"),
                )
            ),
        )
        val readiness = evaluateOnboardingReadiness(configured)
        assertTrue(readiness.hasConfiguredProvider)
        assertTrue(readiness.hasSelectedChatModel)
        assertTrue(readiness.hasImportedCard)
        assertFalse(shouldShowOnboarding(configured, dismissedByUser = false))
    }
}
