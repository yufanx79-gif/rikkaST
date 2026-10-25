package me.rerere.rikkahub.ui.pages.onboarding

import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANTS_IDS
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel

/**
 * 首启就绪度评估结果：
 * - [hasConfiguredProvider]：是否至少有一个已启用且含 CHAT 模型的服务商；
 * - [hasSelectedChatModel]：当前助手或全局是否已选中可用的聊天模型；
 * - [hasImportedCard]：是否已导入至少一张自定义/酒馆角色卡（非默认内置助手）。
 */
data class OnboardingReadiness(
    val hasConfiguredProvider: Boolean,
    val hasSelectedChatModel: Boolean,
    val hasImportedCard: Boolean,
) {
    val isReadyToChat: Boolean
        get() = hasConfiguredProvider && hasSelectedChatModel
}

fun evaluateOnboardingReadiness(settings: Settings): OnboardingReadiness {
    val hasProviderWithChatModel = settings.providers.any { provider ->
        provider.enabled && provider.models.any { it.type == ModelType.CHAT }
    }
    val currentAssistant = settings.getCurrentAssistant()
    val effectiveModel = currentAssistant.chatModelId?.let { settings.findModelById(it) }
        ?: settings.getCurrentChatModel()
        ?: settings.findModelById(settings.chatModelId)
    val hasImportedCard = settings.assistants.any { assistant ->
        assistant.id !in DEFAULT_ASSISTANTS_IDS || assistant.tavernData != null
    }
    return OnboardingReadiness(
        hasConfiguredProvider = hasProviderWithChatModel,
        hasSelectedChatModel = effectiveModel != null,
        hasImportedCard = hasImportedCard,
    )
}

/**
 * 判断是否应当展示首启向导横幅/入口：
 * - 当 `settings.init == true`（DataStore 尚未完成首帧加载）时返回 `false`，避免冷启动闪烁；
 * - 当用户已主动点击跳过（`dismissedByUser == true`）时返回 `false`；
 * - 当尚未选中任何可用聊天模型时返回 `true`。
 */
fun shouldShowOnboarding(
    settings: Settings,
    dismissedByUser: Boolean,
): Boolean {
    if (settings.init || dismissedByUser) return false
    val readiness = evaluateOnboardingReadiness(settings)
    return !readiness.isReadyToChat
}
