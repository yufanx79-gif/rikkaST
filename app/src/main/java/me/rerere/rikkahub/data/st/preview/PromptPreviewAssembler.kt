package me.rerere.rikkahub.data.st.preview

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.export.StActivePresetConfig
import me.rerere.rikkahub.data.export.parseActiveStPresetConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.isTriggered
import me.rerere.rikkahub.data.st.extensions.listExtensions
import me.rerere.rikkahub.data.st.regex.mergeActiveRegexScripts
import me.rerere.rikkahub.data.st.runtime.InjectedPromptStore
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.buildTavernScriptsJson
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import kotlin.uuid.Uuid

/**
 * 只读 Dry-Run 上游消息结构组装器（零副作用、运行在 Dispatchers.Default）。
 *
 * - 绝不推进世界书 sticky/cooldown；
 * - 绝不消费 JSR once=true 注入；
 * - 绝不执行会写变量表的 EJS setvar；
 * - 绝不发起远程 embedding 请求；
 * - 完整检测全部 8 类生效的消息处理器（正则/脚本/EJS/MVU/SPreset/第三方扩展等）。
 */
class PromptPreviewAssembler(
    private val context: Context,
) {
    suspend fun buildSnapshot(
        settings: Settings,
        assistant: Assistant,
        conversation: Conversation,
        draftUserText: String = "",
    ): PromptPreviewSnapshot = withContext(Dispatchers.Default) {
        val activeGroupSet = enabledPresetGroups(assistant, settings, conversation.modeInjectionIds)
        val activeGroups = activeGroupSet.toList()
        val primaryGroup = activeGroups.firstOrNull()
        val secondaryGroups = if (activeGroups.size > 1) activeGroups.drop(1) else emptyList()
        val presetCfg = parseActiveStPresetConfig(settings.tavernPresets, activeGroupSet)

        // 1. 收集顶部已启用消息处理器
        val processors = collectActiveProcessors(settings, assistant, conversation, presetCfg)

        // 2. 反查主预设中每个 ModeInjection.id 对应的原始 identifier（与 ExportSerializer.kt:516 完全一致）
        val presetIdentifierById = buildPresetIdentifierLookup(settings.tavernPresets, primaryGroup)

        // 3. 收集激活的 ModeInjection（严格对齐 PromptInjectionTransformer.kt:290-319）
        val allPromptInjectionIds = settings.promptInjections.map { it.id }.toSet()
        val enabledGlobalPromptIds = if (assistant.modeInjectionIds.intersect(allPromptInjectionIds).isEmpty()) {
            settings.promptInjections.filter { it.enabled }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val activeModeIds = assistant.modeInjectionIds +
            enabledGlobalPromptIds +
            if (assistant.allowConversationPromptInjection) conversation.modeInjectionIds else emptySet()
        val activeModes = (settings.modeInjections + settings.promptInjections)
            .distinctBy { it.id }
            .filter { item ->
                if (!item.enabled) return@filter false
                val matchedId = item.id in activeModeIds
                val matchedPreset = item.presetGroup != null && item.presetGroup in activeGroupSet
                if (!matchedId && !matchedPreset) return@filter false
                // 避免与 PresetMarker 重复展示 main / jailbreak
                val ident = presetIdentifierById[item.id]
                if (ident == "main" || ident == "jailbreak") return@filter false
                true
            }

        // 4. 收集近期聊天记录（过滤 /hide 隐藏消息，限制 contextMessageLimit）
        val visibleChat = conversation.currentMessages.filterNot { it.isHidden }
        val limit = assistant.contextMessageLimit
        val limitedChat = if (limit > 0 && visibleChat.size > limit) visibleChat.takeLast(limit) else visibleChat
        val scanDepth = settings.worldInfoDepth.coerceAtLeast(1)
        val scanWindowText = buildString {
            limitedChat.takeLast(scanDepth).forEach { msg ->
                append(msg.toText()).append('\n')
            }
            if (draftUserText.isNotBlank()) append(draftUserText)
        }

        // 5. 只读扫描激活的世界书词条（对齐 PromptInjectionTransformer：绑定书 + 全局非角色卡书 + 同名角色卡内嵌书）
        val enabledGlobalLorebookIds = settings.lorebooks
            .filter { it.enabled && !it.isCharacterBook }
            .map { it.id }
            .toSet()
        val embeddedBookName = assistant.tavernData?.embeddedBook?.name?.trim().orEmpty()
        val matchedCharLorebookIds = if (embeddedBookName.isNotEmpty()) {
            settings.lorebooks
                .filter { it.enabled && it.name.trim().equals(embeddedBookName, ignoreCase = true) }
                .map { it.id }
                .toSet()
        } else {
            emptySet()
        }
        val activeLorebookIds = assistant.lorebookIds +
            (if (assistant.allowConversationPromptInjection) conversation.lorebookIds else emptySet()) +
            enabledGlobalLorebookIds +
            matchedCharLorebookIds
        val activeLorebooks = settings.lorebooks.filter { book ->
            book.enabled && book.id in activeLorebookIds
        }
        val userName = settings.displaySetting.userNickname.ifBlank { "User" }
        val charName = assistant.name.ifBlank { "助手" }

        val matchedLoreEntries = mutableListOf<Triple<Uuid, String, Pair<PromptInjection.RegexInjection, String?>>>()
        for (book in activeLorebooks) {
            for (entry in book.entries) {
                if (!entry.enabled || entry.position == InjectionPosition.OUTLET) continue
                val triggered = entry.isTriggered(
                    context = scanWindowText,
                    activeSticky = false,
                    rollProbability = false,
                )
                val inactiveReason = when {
                    triggered -> null
                    entry.vectorized -> "本轮未触发（跳过向量检索）"
                    else -> continue
                }
                matchedLoreEntries += Triple(book.id, book.name, entry to inactiveReason)
            }
        }

        // 6. 构造展开态近期聊天消息标尺（最多最近 30 条纯正文 + 底部待发送用户消息 depth=0 标尺）
        val recentSlice = limitedChat.takeLast(30)
        val floorOffset = limitedChat.size - recentSlice.size
        val chatExpansionBlocks = buildList {
            recentSlice.forEachIndexed { idx, msg ->
                // 底部还有 1 条待发送用户消息，因此历史最后一条的 depthFromEnd = 1
                val depthFromEnd = (recentSlice.size - idx)
                val floorNum = floorOffset + idx + 1
                val speaker = if (msg.role == MessageRole.USER) userName else charName
                add(
                    PromptPreviewBlock(
                        key = "chat_msg_${msg.id}",
                        origin = PromptSlotOrigin.ChatMessageAnchor(
                            messageId = msg.id.toString(),
                            role = msg.role,
                            depthFromEnd = depthFromEnd,
                            floorNumber = floorNum,
                            isPendingDraft = false,
                        ),
                        categoryLabel = if (msg.role == MessageRole.USER) "用户消息" else "助手消息",
                        title = "#$floorNum · $speaker (depth=$depthFromEnd)",
                        role = msg.role,
                        plainBodyPreview = PlainBodyPreviewExtractor.extract(msg.toText()),
                        position = InjectionPosition.AT_DEPTH,
                        injectDepth = depthFromEnd,
                        priority = null,
                        draggable = false,
                        isInsideChatHistory = true,
                    )
                )
            }
            add(
                PromptPreviewBlock(
                    key = "chat_msg_pending_draft",
                    origin = PromptSlotOrigin.ChatMessageAnchor(
                        messageId = "__pending_draft__",
                        role = MessageRole.USER,
                        depthFromEnd = 0,
                        floorNumber = limitedChat.size + 1,
                        isPendingDraft = true,
                    ),
                    categoryLabel = "待发送消息",
                    title = "下一条用户消息（草稿预览 · depth=0 标尺）",
                    role = MessageRole.USER,
                    plainBodyPreview = if (draftUserText.isNotBlank()) {
                        PlainBodyPreviewExtractor.extract(draftUserText)
                    } else {
                        "（当前输入框为空，此行代表你下一次发送的消息位置）"
                    },
                    position = InjectionPosition.AT_DEPTH,
                    injectDepth = 0,
                    priority = null,
                    draggable = false,
                    isInsideChatHistory = true,
                )
            )
        }

        // 7. 切分相对区 vs 聊天内部 AT_DEPTH，并按 UnifiedPromptOrderEngine 同源顺序组装主列表
        val (relativeModes, depthModes) = activeModes.partition { it.position != InjectionPosition.AT_DEPTH }
        val (relativeLores, depthLores) = matchedLoreEntries.partition { it.third.first.position != InjectionPosition.AT_DEPTH }

        val activePersona = settings.personas.find { it.id == settings.activePersonaId }
            ?.takeIf { it.enabled && it.description.isNotBlank() && (it.lockedCharacterIds.isEmpty() || assistant.id in it.lockedCharacterIds) }

        val embeddedDepthBlocks = buildList {
            depthModes.sortedWith(compareByDescending<PromptInjection.ModeInjection> { it.injectDepth }.thenBy { it.priority }).forEach { item ->
                add(
                    PromptPreviewBlock(
                        key = "mode_${item.id}",
                        origin = PromptSlotOrigin.ModeInjectionItem(
                            id = item.id,
                            presetGroup = item.presetGroup,
                            presetIdentifier = presetIdentifierById[item.id],
                        ),
                        categoryLabel = if (item.presetGroup != null) "预设" else "规则注入",
                        title = "${item.name.ifBlank { "未命名注入" }} · depth=${item.injectDepth}",
                        role = item.role,
                        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(item.content), charName, userName),
                        position = InjectionPosition.AT_DEPTH,
                        injectDepth = item.injectDepth,
                        priority = item.priority,
                        draggable = true,
                        isInsideChatHistory = true,
                    )
                )
            }
            depthLores.sortedWith(compareByDescending<Triple<Uuid, String, Pair<PromptInjection.RegexInjection, String?>>> { it.third.first.injectDepth }.thenBy { it.third.first.priority }).forEach { (bookId, bookName, pair) ->
                val (entry, inactiveReason) = pair
                add(
                    PromptPreviewBlock(
                        key = "lore_${bookId}_${entry.id}",
                        origin = PromptSlotOrigin.LorebookEntry(bookId, entry.id, bookName),
                        categoryLabel = "世界书",
                        title = "${entry.name.ifBlank { entry.keywords.firstOrNull() ?: "词条" }} · depth=${entry.injectDepth}",
                        role = entry.role,
                        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(entry.content), charName, userName),
                        position = InjectionPosition.AT_DEPTH,
                        injectDepth = entry.injectDepth,
                        priority = entry.priority,
                        draggable = true,
                        isInsideChatHistory = true,
                        inactiveReason = inactiveReason,
                    )
                )
            }
            if (settings.authorNoteEnabled && settings.authorNote.isNotBlank() && settings.authorNotePosition == AuthorNotePosition.IN_CHAT) {
                val interval = settings.authorNoteInterval
                val userMsgCount = limitedChat.count { it.role == MessageRole.USER } + 1
                val inactiveReason = when {
                    interval <= 0 -> "本轮未触发（间隔为 0 已暂停）"
                    interval > 1 && (userMsgCount % interval != 0) -> "本轮未触发（间隔 $interval 轮）"
                    else -> null
                }
                add(
                    PromptPreviewBlock(
                        key = "authors_note",
                        origin = PromptSlotOrigin.AuthorsNote,
                        categoryLabel = "作者注",
                        title = "Author's Note · depth=${settings.authorNoteDepth}",
                        role = settings.authorNoteRole,
                        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(settings.authorNote), charName, userName),
                        position = InjectionPosition.AT_DEPTH,
                        injectDepth = settings.authorNoteDepth,
                        priority = 0,
                        draggable = true,
                        isInsideChatHistory = true,
                        inactiveReason = inactiveReason,
                    )
                )
            }
            if (activePersona != null && activePersona.position == PersonaInjectionPosition.AT_DEPTH && "personaDescription" !in presetCfg.disabledMarkers) {
                add(
                    PromptPreviewBlock(
                        key = "persona_${activePersona.id}",
                        origin = PromptSlotOrigin.Persona(activePersona.id),
                        categoryLabel = "人设",
                        title = "${activePersona.name.ifBlank { "用户人设" }} · depth=${activePersona.depth}",
                        role = activePersona.role,
                        plainBodyPreview = PlainBodyPreviewExtractor.extract(activePersona.description),
                        position = InjectionPosition.AT_DEPTH,
                        injectDepth = activePersona.depth,
                        priority = 0,
                        draggable = true,
                        isInsideChatHistory = true,
                    )
                )
            }
        }

        // 折叠态下所有内嵌 AT_DEPTH 条目按统一规则排序：depth 降序 -> 同深度角色序 (system/user/assistant) -> priority 升序
        val sortedEmbeddedDepthBlocks = embeddedDepthBlocks.sortedWith(
            compareByDescending<PromptPreviewBlock> { it.injectDepth ?: 0 }
                .thenBy { previewAtDepthRoleRank(it.role) }
                .thenBy { it.priority ?: 0 }
        )

        val orderedSlots = if (presetCfg.orderedMarkers.isNotEmpty()) presetCfg.orderedMarkers else ST_BUILTIN_MARKERS
        val emittedModeIds = HashSet<Uuid>()
        val blocks = buildList {
            // Author's Note (BEFORE_PROMPT) 位于最顶部
            if (settings.authorNoteEnabled && settings.authorNote.isNotBlank() && settings.authorNotePosition == AuthorNotePosition.BEFORE_PROMPT) {
                add(
                    PromptPreviewBlock(
                        key = "authors_note",
                        origin = PromptSlotOrigin.AuthorsNote,
                        categoryLabel = "作者注",
                        title = "Author's Note (BEFORE_PROMPT)",
                        role = settings.authorNoteRole,
                        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(settings.authorNote), charName, userName),
                        position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                        injectDepth = null,
                        priority = null,
                        draggable = true,
                    )
                )
            }
            // BEFORE_SYSTEM_PROMPT 独立相对注入
            relativeModes.filter { it.position == InjectionPosition.BEFORE_SYSTEM_PROMPT }
                .sortedWith(modeComparator())
                .forEach {
                    emittedModeIds.add(it.id)
                    add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                }

            val emittedMarkers = HashSet<String>()
            val effectiveOrder = orderedSlots + ST_BUILTIN_MARKERS.filterNot { it in orderedSlots }
            for (marker in effectiveOrder) {
                if (!emittedMarkers.add(marker)) continue
                if (marker in presetCfg.disabledMarkers) continue
                when (marker) {
                    "main" -> {
                        val rawMain = presetCfg.mainPromptContent?.takeIf { it.isNotBlank() } ?: assistant.systemPrompt
                        add(
                            PromptPreviewBlock(
                                key = "marker_main",
                                origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "main"),
                                categoryLabel = "预设",
                                title = "主系统提示词 (main)",
                                role = MessageRole.SYSTEM,
                                plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(rawMain), charName, userName).ifBlank { "默认系统提示词" },
                                position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                                injectDepth = null,
                                priority = null,
                                draggable = primaryGroup != null,
                            )
                        )
                        // AFTER_SYSTEM_PROMPT 相对条目
                        relativeModes.filter { it.position == InjectionPosition.AFTER_SYSTEM_PROMPT }
                            .sortedWith(modeComparator())
                            .forEach {
                                emittedModeIds.add(it.id)
                                add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                            }
                        // Author's Note (IN_PROMPT) 紧跟主系统提示词之后（对齐 PromptInjectionTransformer.kt:1644）
                        if (settings.authorNoteEnabled && settings.authorNote.isNotBlank() && settings.authorNotePosition == AuthorNotePosition.IN_PROMPT) {
                            add(
                                PromptPreviewBlock(
                                    key = "authors_note",
                                    origin = PromptSlotOrigin.AuthorsNote,
                                    categoryLabel = "作者注",
                                    title = "Author's Note (IN_PROMPT)",
                                    role = settings.authorNoteRole,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(settings.authorNote), charName, userName),
                                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = true,
                                )
                            )
                        }
                    }
                    "worldInfoBefore" -> {
                        relativeLores.filter { it.third.first.position == InjectionPosition.BEFORE_CHARACTER || it.third.first.position == InjectionPosition.AFTER_SYSTEM_PROMPT }
                            .sortedBy { it.third.first.priority }
                            .forEach { (bookId, bookName, pair) ->
                                add(pair.first.toRelativeLoreBlock(bookId, bookName, pair.second, charName, userName))
                            }
                        relativeModes.filter { it.position == InjectionPosition.BEFORE_CHARACTER }
                            .sortedWith(modeComparator())
                            .forEach {
                                emittedModeIds.add(it.id)
                                add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                            }
                    }
                    "personaDescription" -> {
                        if (activePersona != null && activePersona.position == PersonaInjectionPosition.IN_PROMPT) {
                            add(
                                PromptPreviewBlock(
                                    key = "persona_${activePersona.id}",
                                    origin = PromptSlotOrigin.Persona(activePersona.id),
                                    categoryLabel = "人设",
                                    title = activePersona.name.ifBlank { "用户人设 (personaDescription)" },
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = PlainBodyPreviewExtractor.extract(activePersona.description),
                                    position = InjectionPosition.BEFORE_CHARACTER,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = true,
                                )
                            )
                        }
                    }
                    "charDescription" -> {
                        val desc = assistant.tavernData?.description.orEmpty()
                        if (desc.isNotBlank()) {
                            add(
                                PromptPreviewBlock(
                                    key = "marker_charDescription",
                                    origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "charDescription"),
                                    categoryLabel = "角色卡",
                                    title = "角色描述 (charDescription)",
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(desc), charName, userName),
                                    position = null,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = primaryGroup != null,
                                )
                            )
                        }
                    }
                    "charPersonality" -> {
                        val pers = assistant.tavernData?.personality.orEmpty()
                        if (pers.isNotBlank()) {
                            add(
                                PromptPreviewBlock(
                                    key = "marker_charPersonality",
                                    origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "charPersonality"),
                                    categoryLabel = "角色卡",
                                    title = "角色性格 (charPersonality)",
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(pers), charName, userName),
                                    position = null,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = primaryGroup != null,
                                )
                            )
                        }
                    }
                    "scenario" -> {
                        val scen = assistant.tavernData?.scenario.orEmpty()
                        if (scen.isNotBlank()) {
                            add(
                                PromptPreviewBlock(
                                    key = "marker_scenario",
                                    origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "scenario"),
                                    categoryLabel = "角色卡",
                                    title = "场景设定 (scenario)",
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(scen), charName, userName),
                                    position = null,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = primaryGroup != null,
                                )
                            )
                        }
                    }
                    "worldInfoAfter" -> {
                        relativeLores.filter {
                            it.third.first.position != InjectionPosition.BEFORE_CHARACTER &&
                                it.third.first.position != InjectionPosition.AFTER_SYSTEM_PROMPT
                        }.sortedBy { it.third.first.priority }
                            .forEach { (bookId, bookName, pair) ->
                                add(pair.first.toRelativeLoreBlock(bookId, bookName, pair.second, charName, userName))
                            }
                        relativeModes.filter {
                            it.position == InjectionPosition.AFTER_CHARACTER ||
                                it.position == InjectionPosition.EM_TOP ||
                                it.position == InjectionPosition.EM_BOTTOM
                        }.sortedWith(modeComparator())
                            .forEach {
                                emittedModeIds.add(it.id)
                                add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                            }
                    }
                    "dialogueExamples" -> {
                        val ex = assistant.tavernData?.mesExample.orEmpty()
                        if (ex.isNotBlank()) {
                            add(
                                PromptPreviewBlock(
                                    key = "marker_dialogueExamples",
                                    origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "dialogueExamples"),
                                    categoryLabel = "角色卡",
                                    title = "示例对话 (dialogueExamples)",
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(ex), charName, userName),
                                    position = null,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = primaryGroup != null,
                                )
                            )
                        }
                    }
                    "chatHistory" -> {
                        relativeModes.filter { it.position == InjectionPosition.TOP_OF_CHAT }
                            .sortedWith(modeComparator())
                            .forEach {
                                emittedModeIds.add(it.id)
                                add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                            }
                        // 聊天记录区顶/底视觉边界锚点 + 扁平可拖拽的内嵌 depth 条目
                        add(
                            PromptPreviewBlock(
                                key = "chat_region_header",
                                origin = PromptSlotOrigin.ChatRegionHeader(
                                    totalMessageCount = limitedChat.size,
                                    embeddedDepthCount = sortedEmbeddedDepthBlocks.size,
                                    expanded = false,
                                ),
                                categoryLabel = "聊天记录",
                                title = "聊天记录区（共 ${limitedChat.size} 条，点击展开/收起近期正文）",
                                role = MessageRole.USER,
                                plainBodyPreview = if (sortedEmbeddedDepthBlocks.isEmpty()) {
                                    "当前无深度注入条目，点击展开后可将条目拖到具体消息位置精调 depth"
                                } else {
                                    "已包含 ${sortedEmbeddedDepthBlocks.size} 条深度注入条目（长按右侧 ≡ 可调序或拖出）"
                                },
                                position = null,
                                injectDepth = null,
                                priority = null,
                                draggable = false,
                            )
                        )
                        addAll(sortedEmbeddedDepthBlocks)
                        add(
                            PromptPreviewBlock(
                                key = "chat_region_footer",
                                origin = PromptSlotOrigin.ChatRegionFooter,
                                categoryLabel = "聊天记录",
                                title = "聊天记录区结束",
                                role = MessageRole.USER,
                                plainBodyPreview = "",
                                position = null,
                                injectDepth = null,
                                priority = null,
                                draggable = false,
                            )
                        )
                        relativeModes.filter {
                            it.position == InjectionPosition.BOTTOM_OF_CHAT ||
                                it.position == InjectionPosition.AFTER_DIALOG
                        }.sortedWith(modeComparator())
                            .forEach {
                                emittedModeIds.add(it.id)
                                add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName))
                            }
                    }
                    "jailbreak" -> {
                        val jb = presetCfg.jailbreakPromptContent.orEmpty()
                        if (jb.isNotBlank()) {
                            add(
                                PromptPreviewBlock(
                                    key = "marker_jailbreak",
                                    origin = PromptSlotOrigin.PresetMarker(primaryGroup.orEmpty(), "jailbreak"),
                                    categoryLabel = "预设",
                                    title = "破限/后置指令 (jailbreak)",
                                    role = MessageRole.SYSTEM,
                                    plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(jb), charName, userName),
                                    position = InjectionPosition.AFTER_DIALOG,
                                    injectDepth = null,
                                    priority = null,
                                    draggable = primaryGroup != null,
                                )
                            )
                        }
                    }
                }
            }

            // 兜底：其余尚未挂载的相对区 ModeInjection（如 ANTAGONIZE / AUTHOR_NOTE 等）
            relativeModes.filter { it.id !in emittedModeIds }
                .sortedWith(modeComparator())
                .forEach { add(it.toRelativePreviewBlock(presetIdentifierById[it.id], charName, userName)) }
        }

        PromptPreviewSnapshot(
            blocks = blocks,
            chatMessagesForExpansion = chatExpansionBlocks,
            activeProcessors = processors,
            activePresetGroup = primaryGroup,
            secondaryPresetGroups = secondaryGroups,
        )
    }

    private fun modeComparator(): Comparator<PromptInjection.ModeInjection> = compareBy(
        // 严格满足传递性，与 PromptInjectionTransformer.kt 保持完全一致：预设条目升序(桶0)，独立规则降序(桶1)
        { if (it.presetGroup == null) 1 else 0 },
        { if (it.presetGroup == null) -it.priority else it.priority },
    )

    private fun previewAtDepthRoleRank(role: MessageRole): Int = when (role) {
        MessageRole.SYSTEM -> 0
        MessageRole.USER -> 1
        MessageRole.ASSISTANT -> 2
        else -> 3
    }

    private fun PromptInjection.ModeInjection.toRelativePreviewBlock(
        presetIdentifier: String?,
        charName: String,
        userName: String,
    ): PromptPreviewBlock = PromptPreviewBlock(
        key = "mode_$id",
        origin = PromptSlotOrigin.ModeInjectionItem(id, presetGroup, presetIdentifier),
        categoryLabel = if (presetGroup != null) "预设" else "规则注入",
        title = "${name.ifBlank { "未命名条目" }} · ${position.name}",
        role = role,
        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(content), charName, userName),
        position = position,
        injectDepth = null,
        priority = priority,
        draggable = true,
    )

    private fun PromptInjection.RegexInjection.toRelativeLoreBlock(
        bookId: Uuid,
        bookName: String,
        inactiveReason: String?,
        charName: String,
        userName: String,
    ): PromptPreviewBlock = PromptPreviewBlock(
        key = "lore_${bookId}_$id",
        origin = PromptSlotOrigin.LorebookEntry(bookId, id, bookName),
        categoryLabel = "世界书",
        title = "${name.ifBlank { keywords.firstOrNull() ?: "词条" }} · ${position.name}",
        role = role,
        plainBodyPreview = lightweightMacroPreview(PlainBodyPreviewExtractor.extract(content), charName, userName),
        position = position,
        injectDepth = null,
        priority = priority,
        draggable = true,
        inactiveReason = inactiveReason,
    )

    private fun lightweightMacroPreview(text: String, charName: String, userName: String): String =
        text.replace("{{char}}", charName, ignoreCase = true)
            .replace("{{user}}", userName, ignoreCase = true)

    private fun buildPresetIdentifierLookup(rawPresetsJson: String, primaryGroup: String?): Map<Uuid, String> {
        if (primaryGroup.isNullOrBlank() || rawPresetsJson.isBlank()) return emptyMap()
        return runCatching {
            val root = Json.parseToJsonElement(rawPresetsJson) as? JsonObject ?: return emptyMap()
            val groupEl = root[primaryGroup] ?: return emptyMap()
            val presetObj = when (groupEl) {
                is JsonPrimitive -> Json.parseToJsonElement(groupEl.contentOrNull ?: return emptyMap()) as? JsonObject
                is JsonObject -> groupEl
                else -> null
            } ?: return emptyMap()
            val prompts = presetObj["prompts"] as? JsonArray ?: return emptyMap()
            buildMap {
                for (el in prompts) {
                    val ident = (el as? JsonObject)?.get("identifier")?.jsonPrimitive?.contentOrNull
                    if (!ident.isNullOrBlank()) {
                        put(PromptOrderSyncWriter.deterministicPresetEntryId(primaryGroup, ident), ident)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private suspend fun collectActiveProcessors(
        settings: Settings,
        assistant: Assistant,
        conversation: Conversation,
        presetCfg: StActivePresetConfig,
    ): List<ActiveProcessorBadge> {
        val installedExtensions = withContext(Dispatchers.IO) { listExtensions(context) }
        return buildList {
        // 1) ST 正则脚本（全局 + 预设内嵌 + 角色卡内嵌）
        mergeActiveRegexScripts(settings, assistant, conversation.modeInjectionIds)
            .filter { !it.disabled }
            .forEach { script ->
                val channel = when {
                    script.promptOnly -> ProcessorChannel.PRE_SEND
                    script.markdownOnly -> ProcessorChannel.RENDER_ONLY
                    else -> ProcessorChannel.BOTH
                }
                add(
                    ActiveProcessorBadge(
                        id = "st_regex_${script.id.ifBlank { script.scriptName }}",
                        name = script.scriptName.ifBlank { "ST 正则" },
                        category = "正则",
                        channel = channel,
                        detail = if (script.promptOnly) "仅处理发送提示词" else if (script.markdownOnly) "仅处理界面渲染" else "发送与渲染均生效",
                    )
                )
            }
        // 2) 原生助手正则（区分 visualOnly 仅渲染 vs 同步改写发送内容）
        assistant.regexes.filter { it.enabled }.forEach { rx ->
            add(
                ActiveProcessorBadge(
                    id = "native_regex_${rx.id}",
                    name = rx.name.ifBlank { "助手正则" },
                    category = "正则",
                    channel = if (rx.visualOnly) ProcessorChannel.RENDER_ONLY else ProcessorChannel.BOTH,
                    detail = if (rx.visualOnly) "原生视觉渲染正则" else "原生发送与渲染正则",
                )
            )
        }
        // 3) 酒馆助手 / JS-Slash-Runner 脚本
        parseEnabledTavernScripts(buildTavernScriptsJson(assistant, settings)).forEach { scriptName ->
            add(
                ActiveProcessorBadge(
                    id = "jsr_$scriptName",
                    name = scriptName,
                    category = "酒馆脚本",
                    channel = ProcessorChannel.BOTH,
                    isBlackBoxJsHook = true,
                    detail = "酒馆助手脚本（含运行时事件钩子）",
                )
            )
        }
        // 4) 当前会话 JSR 动态 injectPrompts
        val dynamicInjects = InjectedPromptStore.snapshot(conversation.id.toString())
        if (dynamicInjects.isNotEmpty()) {
            add(
                ActiveProcessorBadge(
                    id = "jsr_dynamic_inject",
                    name = "JSR 动态注入 (${dynamicInjects.size})",
                    category = "酒馆脚本",
                    channel = ProcessorChannel.PRE_SEND,
                    detail = "脚本向本次请求注入了 ${dynamicInjects.size} 条临时提示词",
                )
            )
        }
        // 5) EJS 提示词模板
        if (settings.tavernEjsRendering) {
            add(
                ActiveProcessorBadge(
                    id = "ejs_builtin",
                    name = "EJS 提示词模板",
                    category = "提示词模板",
                    channel = ProcessorChannel.PRE_SEND,
                    detail = "发前将展开 <% %> 模板逻辑",
                )
            )
        }
        // 6) MVU 变量更新
        if (TavernRuntimeManager.isMvuActive) {
            add(
                ActiveProcessorBadge(
                    id = "mvu_active",
                    name = "MVU 变量系统",
                    category = "MVU",
                    channel = ProcessorChannel.BOTH,
                    isBlackBoxJsHook = true,
                    detail = "发前剥离历史变量标签，输出后由 JS 更新变量状态",
                )
            )
        }
        // 7) SPreset 扩展能力（仅在真实存在 SPreset 配置项时显示，避免默认 enabled=true 误报）
        val sp = presetCfg.spresetConfig
        val hasActiveSpreset = sp.enabled && (
            sp.raw.isNotEmpty() ||
                sp.toolBindings.isNotEmpty() ||
                sp.messageInjections.isNotEmpty() ||
                sp.chatSquash.enabled ||
                sp.macroNestDepth > 0 ||
                sp.outputPreprocessing.enabled ||
                sp.forcedPostProcessing.enabled ||
                sp.finalRequestProcessing.enabled
            )
        if (hasActiveSpreset) {
            add(
                ActiveProcessorBadge(
                    id = "spreset_pipeline",
                    name = "SPreset 预设管线",
                    category = "SPreset",
                    channel = ProcessorChannel.PRE_SEND,
                    isBlackBoxJsHook = true,
                    detail = "发前执行预设扩展注入 / 合并 / 最终请求改写",
                )
            )
        }
        // 8) 第三方扩展（st-memory-enhancement / LittleWhiteBox / ST-Prompt-Template 等，已在 IO 线程读取）
        installedExtensions
            .filter { it.hasManifest && it.js.isNotBlank() && it.folder !in settings.tavernThirdPartyDisabled }
            .forEach { ext ->
                add(
                    ActiveProcessorBadge(
                        id = "ext_${ext.folder}",
                        name = ext.displayName.ifBlank { ext.folder },
                        category = "第三方扩展",
                        channel = ProcessorChannel.BOTH,
                        isBlackBoxJsHook = true,
                        detail = "第三方扩展（发送瞬间可能通过 JS 钩子二次修改消息）",
                    )
                )
            }
        }
    }

    private fun parseEnabledTavernScripts(scriptsJson: String): List<String> {
        if (scriptsJson.isBlank()) return emptyList()
        return runCatching {
            val parsed = Json.parseToJsonElement(scriptsJson)
            val arr = (parsed as? JsonArray)
                ?: (parsed as? JsonObject)?.get("scripts") as? JsonArray
                ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                if (!enabled) return@mapNotNull null
                obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }.distinct()
        }.getOrDefault(emptyList())
    }
}
