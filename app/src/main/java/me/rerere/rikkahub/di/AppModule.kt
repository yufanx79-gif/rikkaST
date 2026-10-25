package me.rerere.rikkahub.di

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.ai.transformers.KnowledgeBaseTransformer
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.knowledge.DocumentChunker
import me.rerere.rikkahub.data.knowledge.KnowledgeBaseService
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.MediaCreationService
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.NsdServiceRegistrar
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    single { AppScope() }
    single { AppEventBus() }
    single<Json> { JsonInstant }
    singleOf(::UpdateChecker)
    single<EmojiData> { EmojiUtils.loadEmoji(get()) }
    single { TTSManager(get()) }
    single { SoundEffectPlayer(get()) }
    single(createdAtStart = true) { ChatNotificationManager(get(), get(), get(), get()) }
    single { WorkspaceTerminalSessionManager(get(), get()) }
    single {
        LocalTools(
            context = get(),
            eventBus = get(),
            ttsManager = get(),
            settingsStore = get(),
        )
    }
    single { DocumentChunker() }
    single {
        val db = get<AppDatabase>()
        KnowledgeBaseService(
            context = get(),
            database = db,
            chunker = get(),
            providerManager = get(),
            settingsStore = get(),
        )
    }
    single {
        KnowledgeBaseTransformer(
            knowledgeBaseService = get(),
        )
    }
    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    single {
        ChatRequestFactory(
            context = get(),
            providerManager = get(),
            usageRecorder = get(),
            chatToolFactory = get(),
            memoryRepository = get(),
            templateTransformer = get(),
            workspaceRepository = get(),
        )
    }
    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            workspaceRepository = get(),
            generationLoop = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            knowledgeBaseTransformer = get(),
            folderRepository = get(),
        )
    }
    single {
        MediaCreationService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            repository = get(),
            manager = get(),
            remoteFileStore = get(),
            okHttpClient = get(),
        )
    }
    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get(),
        )
    }
    single { NsdServiceRegistrar(get()) }
}
