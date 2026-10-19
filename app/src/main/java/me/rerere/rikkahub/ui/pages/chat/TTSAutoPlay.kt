package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.stripStIphonieProtocolTagsForNativeTts
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    // Auto-play TTS after generation completes (Channel A: Upstream Native TTS)
    // 若当前回复已包含 ST-iPhonie `<tts>` 标签且小手机已启用，或 ST-iPhonie 正在播放角色语音（Channel B），
    // 自动让出声道给 ST-iPhonie，避免上游原生自动朗读与小手机角色语音同时触发冲突。
    val tts = LocalTTSState.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)
    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { conversationId ->
            if (conversationId == currentConversation.id &&
                !vm.voiceSession.state.value.isActive &&
                updatedSetting.displaySetting.autoPlayTTSAfterGeneration &&
                !TavernRuntimeManager.isStIphonieSpeaking
            ) {
                val lastMessage = currentConversation.currentMessages.lastOrNull()
                if (lastMessage != null && lastMessage.role == MessageRole.ASSISTANT) {
                    val rawText = lastMessage.toText()
                    val iphonieActive = TavernRuntimeManager.floatingWindowsFlow.value.hasIphonie ||
                        updatedSetting.tavernExtensionSettings.contains("stTtsDialogue", ignoreCase = true)
                    val hasIphonieTtsTag = rawText.contains("<tts>", ignoreCase = true) && iphonieActive
                    if (!hasIphonieTtsTag) {
                        var textToSpeak = stripStIphonieProtocolTagsForNativeTts(rawText)
                        if (updatedSetting.displaySetting.ttsOnlyReadQuoted) {
                            textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                        }
                        if (updatedSetting.displaySetting.ttsOnlyReadOutsideBrackets) {
                            textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                        }
                        if (textToSpeak.isNotBlank()) {
                            tts.speak(textToSpeak)
                        }
                    }
                }
            }
        }
    }
}
