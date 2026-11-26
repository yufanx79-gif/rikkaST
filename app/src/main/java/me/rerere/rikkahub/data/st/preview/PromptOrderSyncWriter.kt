package me.rerere.rikkahub.data.st.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import kotlin.uuid.Uuid

internal data class ModePatch(val position: InjectionPosition, val depth: Int?, val priority: Int)
internal data class LorePatch(val position: InjectionPosition, val depth: Int?, val priority: Int)
internal data class PresetPromptPatch(val injectionPosition: Int, val injectionDepth: Int?, val injectionOrder: Int)

/**
 * 双向同步回写引擎（完整双写 Kotlin Settings + 原始 ST 预设 JSON）。
 *
 * - 严格按 `dirtyKeys` 保护未触碰的 `AT_DEPTH`（含 depth > 30）条目不被展开窗口截断；
 * - 自动区分 `presetGroup != null`（priority 升序）与 `presetGroup == null`（priority 降序）；
 * - 同步改写 `Settings.tavernPresets[group]` 中的 `prompts[]` 与 `prompt_order[]`，
 *   保证 Kotlin 发送管线与酒馆 JS 运行时 `getPreset()` 100% 一致；
 * - 单次原子 `preferencesStore.update` 落盘，复用已加锁的 `persistMutex` 链路。
 */
class PromptOrderSyncWriter(
    private val settingsStore: SettingsStore,
) {
    suspend fun commitDraft(
        draftBlocks: List<PromptPreviewBlock>,
        dirtyKeys: Set<String>,
        activePresetGroup: String?,
    ) {
        if (dirtyKeys.isEmpty()) return
        val plan = computeSyncPlan(draftBlocks, dirtyKeys, activePresetGroup)
        settingsStore.update { current ->
            val updatedModeInjections = current.modeInjections.map { item ->
                val p = plan.modeUpdates[item.id] ?: return@map item
                item.copy(
                    position = p.position,
                    injectDepth = p.depth ?: item.injectDepth,
                    priority = p.priority,
                    injectionOrder = p.priority,
                )
            }
            val updatedInjections = current.promptInjections.map { item ->
                val p = plan.modeUpdates[item.id] ?: return@map item
                item.copy(
                    position = p.position,
                    injectDepth = p.depth ?: item.injectDepth,
                    priority = p.priority,
                    injectionOrder = p.priority,
                )
            }
            val updatedLorebooks = current.lorebooks.map { book ->
                book.copy(
                    entries = book.entries.map { entry ->
                        val p = plan.loreUpdates[book.id to entry.id] ?: return@map entry
                        entry.copy(
                            position = p.position,
                            injectDepth = p.depth ?: entry.injectDepth,
                            priority = p.priority,
                        )
                    }
                )
            }
            val updatedTavernPresets = if (activePresetGroup != null &&
                (plan.presetIdentifierOrder.isNotEmpty() || plan.presetPromptJsonPatches.isNotEmpty())
            ) {
                syncTavernPresetJson(
                    rawPresetsJson = current.tavernPresets,
                    group = activePresetGroup,
                    orderedIdentifiers = plan.presetIdentifierOrder,
                    promptPatches = plan.presetPromptJsonPatches,
                )
            } else {
                current.tavernPresets
            }
            val updatedPersonas = plan.personaUpdate?.let { (pid, pair) ->
                current.personas.map { persona ->
                    if (persona.id == pid) {
                        persona.copy(position = pair.first, depth = pair.second)
                    } else {
                        persona
                    }
                }
            } ?: current.personas

            current.copy(
                modeInjections = updatedModeInjections,
                promptInjections = updatedInjections,
                lorebooks = updatedLorebooks,
                tavernPresets = updatedTavernPresets,
                authorNotePosition = plan.newAuthorNotePos ?: current.authorNotePosition,
                authorNoteDepth = plan.newAuthorNoteDepth ?: current.authorNoteDepth,
                personas = updatedPersonas,
            )
        }
    }

    companion object {
        private const val ST_PRESET_DEFAULT_CHARACTER_ID = 100001

        internal data class SyncPlan(
            val modeUpdates: Map<Uuid, ModePatch>,
            val loreUpdates: Map<Pair<Uuid, Uuid>, LorePatch>,
            val presetIdentifierOrder: List<String>,
            val presetPromptJsonPatches: Map<String, PresetPromptPatch>,
            val newAuthorNotePos: AuthorNotePosition?,
            val newAuthorNoteDepth: Int?,
            val personaUpdate: Pair<Uuid, Pair<PersonaInjectionPosition, Int>>?,
        )

        /** 纯函数计算同步计划，供 JVM 单元测试直接验证全部边界情况 */
        internal fun computeSyncPlan(
            draftBlocks: List<PromptPreviewBlock>,
            dirtyKeys: Set<String>,
            activePresetGroup: String?,
        ): SyncPlan {
            val headerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
            val footerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
            val isChatExpanded = draftBlocks.any { it.origin is PromptSlotOrigin.ChatMessageAnchor }

            val modeUpdates = mutableMapOf<Uuid, ModePatch>()
            val loreUpdates = mutableMapOf<Pair<Uuid, Uuid>, LorePatch>()
            val presetIdentifierOrder = mutableListOf<String>()
            val presetPromptJsonPatches = mutableMapOf<String, PresetPromptPatch>()
            var newAuthorNotePos: AuthorNotePosition? = null
            var newAuthorNoteDepth: Int? = null
            var personaUpdate: Pair<Uuid, Pair<PersonaInjectionPosition, Int>>? = null

            val hasTouchedMode = draftBlocks.any {
                it.key in dirtyKeys && (
                    it.origin is PromptSlotOrigin.ModeInjectionItem ||
                        (it.origin is PromptSlotOrigin.PresetMarker && it.origin.identifier == "jailbreak")
                    )
            }
            val hasTouchedLore = draftBlocks.any { it.key in dirtyKeys && it.origin is PromptSlotOrigin.LorebookEntry }

            draftBlocks.forEachIndexed { index, block ->
                val insideChat = headerIdx >= 0 && footerIdx > headerIdx && index in (headerIdx + 1) until footerIdx
                val isTouched = block.key in dirtyKeys

                // 收集主预设中实际出现在预览里的标识符顺序（含人设 marker、内置 marker、主预设条目、chatHistory）
                when (val o = block.origin) {
                    is PromptSlotOrigin.PresetMarker -> {
                        if (activePresetGroup == null || o.group == activePresetGroup) {
                            presetIdentifierOrder.add(o.identifier)
                        }
                    }
                    is PromptSlotOrigin.Persona -> {
                        if (activePresetGroup != null && !insideChat && "personaDescription" !in presetIdentifierOrder) {
                            presetIdentifierOrder.add("personaDescription")
                        }
                    }
                    is PromptSlotOrigin.ModeInjectionItem -> {
                        if (activePresetGroup != null && o.presetGroup == activePresetGroup && !o.presetIdentifier.isNullOrBlank()) {
                            presetIdentifierOrder.add(o.presetIdentifier)
                        }
                    }
                    is PromptSlotOrigin.ChatRegionHeader -> {
                        if (activePresetGroup != null && "chatHistory" !in presetIdentifierOrder) {
                            presetIdentifierOrder.add("chatHistory")
                        }
                    }
                    else -> Unit
                }

                // 未被拖动且原本已在聊天记录内（含 depth > 30）的条目：严格保留原 injectDepth，绝不误改！
                if (!isTouched && block.isInsideChatHistory && insideChat) {
                    return@forEachIndexed
                }

                val resolvedDepth = if (insideChat) {
                    if (isChatExpanded) computeDepthFromExpandedChat(draftBlocks, index) else (block.injectDepth ?: 0)
                } else {
                    block.injectDepth ?: 4
                }
                val resolvedPos = if (insideChat) {
                    InjectionPosition.AT_DEPTH
                } else {
                    inferRelativePosition(draftBlocks, index, headerIdx, footerIdx, block.position)
                }

                when (val origin = block.origin) {
                    is PromptSlotOrigin.ModeInjectionItem -> {
                        // 关键：预设条目 (presetGroup != null) 升序；独立自建规则 (presetGroup == null) 降序！
                        val prio = if (origin.presetGroup != null) index * 10 else (draftBlocks.size - index) * 10
                        if (isTouched || (hasTouchedMode && !insideChat)) {
                            // 未触碰的相对区条目保留原 position 与原 injectDepth，仅重算相对顺序 priority，防止位置漂移与深度覆写
                            val finalPos = if (isTouched) resolvedPos else (block.position ?: resolvedPos)
                            val finalDepth = if (isTouched) resolvedDepth else null
                            modeUpdates[origin.id] = ModePatch(finalPos, finalDepth, prio)
                            if (activePresetGroup != null && origin.presetGroup == activePresetGroup && !origin.presetIdentifier.isNullOrBlank()) {
                                presetPromptJsonPatches[origin.presetIdentifier] = PresetPromptPatch(
                                    injectionPosition = if (finalPos == InjectionPosition.AT_DEPTH) 1 else 0,
                                    injectionDepth = finalDepth,
                                    injectionOrder = prio,
                                )
                            }
                        }
                    }
                    is PromptSlotOrigin.LorebookEntry -> {
                        // 拖动任一世界书词条时，同步按视觉顺序重算同相对区所有世界书词条的 priority（保留未触碰项原 position 与 injectDepth），
                        // 避免旧词条保留默认 priority=100/500 导致新 priority=index*10 拖拽不生效。
                        if (isTouched || (hasTouchedLore && !insideChat)) {
                            val finalPos = if (isTouched) resolvedPos else (block.position ?: resolvedPos)
                            val finalDepth = if (isTouched) resolvedDepth else null
                            loreUpdates[origin.lorebookId to origin.entryId] =
                                LorePatch(finalPos, finalDepth, index * 10)
                        }
                    }
                    is PromptSlotOrigin.AuthorsNote -> {
                        if (isTouched) {
                            val mainIdx = draftBlocks.indexOfFirst {
                                (it.origin as? PromptSlotOrigin.PresetMarker)?.identifier == "main"
                            }
                            newAuthorNotePos = when {
                                insideChat -> AuthorNotePosition.IN_CHAT
                                mainIdx >= 0 && index < mainIdx -> AuthorNotePosition.BEFORE_PROMPT
                                else -> AuthorNotePosition.IN_PROMPT
                            }
                            newAuthorNoteDepth = resolvedDepth
                        }
                    }
                    is PromptSlotOrigin.PresetMarker -> {
                        // jailbreak 在真实发送路径中由 PromptInjectionTransformer 消费其确定性 ModeInjection，
                        // 必须同步回写 ModeInjection 的 position/priority 与 prompts[] 补丁，保证预览与发包一致。
                        if (origin.identifier == "jailbreak" && activePresetGroup != null &&
                            (isTouched || (hasTouchedMode && !insideChat))
                        ) {
                            val jbId = deterministicPresetEntryId(activePresetGroup, "jailbreak")
                            val prio = index * 10
                            val finalPos = if (isTouched) resolvedPos else (block.position ?: resolvedPos)
                            val finalDepth = if (isTouched) resolvedDepth else null
                            modeUpdates[jbId] = ModePatch(finalPos, finalDepth, prio)
                            presetPromptJsonPatches["jailbreak"] = PresetPromptPatch(
                                injectionPosition = if (finalPos == InjectionPosition.AT_DEPTH) 1 else 0,
                                injectionDepth = finalDepth,
                                injectionOrder = prio,
                            )
                        }
                    }
                    is PromptSlotOrigin.Persona -> {
                        if (isTouched) {
                            val pPos = if (insideChat) PersonaInjectionPosition.AT_DEPTH else PersonaInjectionPosition.IN_PROMPT
                            personaUpdate = origin.personaId to (pPos to resolvedDepth)
                        }
                    }
                    else -> Unit
                }
            }

            return SyncPlan(
                modeUpdates = modeUpdates,
                loreUpdates = loreUpdates,
                presetIdentifierOrder = presetIdentifierOrder.distinct(),
                presetPromptJsonPatches = presetPromptJsonPatches,
                newAuthorNotePos = newAuthorNotePos,
                newAuthorNoteDepth = newAuthorNoteDepth,
                personaUpdate = personaUpdate,
            )
        }

        /**
         * 统计当前位置下方还有多少条 ChatMessageAnchor（含底部待发送用户消息标尺）：
         * - 紧贴待发送用户消息之后：0 条在下方 -> depth = 0
         * - 紧贴待发送用户消息之前：1 条在下方 -> depth = 1
         */
        internal fun computeDepthFromExpandedChat(blocks: List<PromptPreviewBlock>, index: Int): Int {
            var messagesBelow = 0
            for (i in (index + 1) until blocks.size) {
                if (blocks[i].origin is PromptSlotOrigin.ChatMessageAnchor) {
                    messagesBelow++
                }
            }
            return messagesBelow.coerceAtLeast(0)
        }

        internal fun inferRelativePosition(
            blocks: List<PromptPreviewBlock>,
            index: Int,
            headerIdx: Int,
            footerIdx: Int,
            previousPosition: InjectionPosition?,
        ): InjectionPosition {
            val mainIdx = blocks.indexOfFirst {
                (it.origin as? PromptSlotOrigin.PresetMarker)?.identifier == "main"
            }
            val firstCharIdx = blocks.indexOfFirst {
                (it.origin as? PromptSlotOrigin.PresetMarker)?.identifier in setOf("charDescription", "charPersonality", "scenario")
            }
            val lastCharIdx = blocks.indexOfLast {
                (it.origin as? PromptSlotOrigin.PresetMarker)?.identifier in setOf("charDescription", "charPersonality", "scenario")
            }
            // 仅当聊天记录区确实位于 main 与角色卡之后（常规顺序）时，chatFooter 之后才映射为 AFTER_DIALOG；
            // 若用户将 chatHistory 拖到前面，则后续位置继续按 main / 角色卡锚点准确判定。
            val chatIsAtTail = footerIdx >= 0 &&
                (mainIdx < 0 || footerIdx > mainIdx) &&
                (lastCharIdx < 0 || footerIdx > lastCharIdx)
            if (chatIsAtTail && index > footerIdx) {
                return InjectionPosition.AFTER_DIALOG
            }
            if (mainIdx >= 0 && index < mainIdx) {
                return InjectionPosition.BEFORE_SYSTEM_PROMPT
            }
            if (firstCharIdx >= 0 && index < firstCharIdx) {
                return if (previousPosition == InjectionPosition.AFTER_SYSTEM_PROMPT) {
                    InjectionPosition.AFTER_SYSTEM_PROMPT
                } else {
                    InjectionPosition.BEFORE_CHARACTER
                }
            }
            if (lastCharIdx >= 0 && index > lastCharIdx) {
                return InjectionPosition.AFTER_CHARACTER
            }
            if (firstCharIdx in 0..index && index <= lastCharIdx) {
                return InjectionPosition.AFTER_CHARACTER
            }
            return if (previousPosition != null && previousPosition != InjectionPosition.AT_DEPTH) {
                previousPosition
            } else {
                InjectionPosition.AFTER_SYSTEM_PROMPT
            }
        }

        /**
         * 按 `ExportSerializer.kt:516` 一致的确定性 UUID 规则构造预设条目 ID。
         */
        fun deterministicPresetEntryId(presetGroup: String, identifier: String): Uuid =
            Uuid.parse(
                java.util.UUID.nameUUIDFromBytes("$presetGroup::$identifier".toByteArray(Charsets.UTF_8)).toString()
            )

        /**
         * 完整双写 `Settings.tavernPresets[group]` 原始 JSON：
         * 1. 更新 `prompts[]` 中匹配 identifier 的 `injection_position` / `injection_depth` / `injection_order`；
         * 2. 重排 `prompt_order[]` 中 `character_id == 100001`（无则取首个有效桶）的 `order[]`，
         *    保留所有未参与预览的禁用项与原始字段，损坏 JSON 时安全回退原字符串不崩溃。
         */
        internal fun syncTavernPresetJson(
            rawPresetsJson: String,
            group: String,
            orderedIdentifiers: List<String>,
            promptPatches: Map<String, PresetPromptPatch>,
        ): String {
            if (rawPresetsJson.isBlank() || group.isBlank()) return rawPresetsJson
            return runCatching {
                val root = Json.parseToJsonElement(rawPresetsJson) as? JsonObject ?: return rawPresetsJson
                val groupEl = root[group] ?: return rawPresetsJson
                val wasStringEncoded = groupEl is JsonPrimitive
                val presetObj = when (groupEl) {
                    is JsonPrimitive -> {
                        val content = groupEl.contentOrNull ?: return rawPresetsJson
                        Json.parseToJsonElement(content) as? JsonObject ?: return rawPresetsJson
                    }
                    is JsonObject -> groupEl
                    else -> return rawPresetsJson
                }

                val updatedPresetObj = buildJsonObject {
                    presetObj.forEach { (k, v) ->
                        when (k) {
                            "prompts" -> {
                                val arr = v as? JsonArray
                                if (arr == null || promptPatches.isEmpty()) {
                                    put(k, v)
                                } else {
                                    put(k, buildJsonArray {
                                        for (item in arr) {
                                            val obj = item as? JsonObject
                                            val ident = obj?.get("identifier")?.jsonPrimitive?.contentOrNull
                                            val patch = if (ident != null) promptPatches[ident] else null
                                            if (obj == null || patch == null) {
                                                add(item)
                                            } else {
                                                add(buildJsonObject {
                                                    obj.forEach { (pk, pv) ->
                                                        when (pk) {
                                                            "injection_position" -> put(pk, JsonPrimitive(patch.injectionPosition))
                                                            "injection_depth" -> put(pk, patch.injectionDepth?.let { JsonPrimitive(it) } ?: pv)
                                                            "injection_order" -> put(pk, JsonPrimitive(patch.injectionOrder))
                                                            else -> put(pk, pv)
                                                        }
                                                    }
                                                    if ("injection_position" !in obj) put("injection_position", JsonPrimitive(patch.injectionPosition))
                                                    if ("injection_depth" !in obj && patch.injectionDepth != null) put("injection_depth", JsonPrimitive(patch.injectionDepth))
                                                    if ("injection_order" !in obj) put("injection_order", JsonPrimitive(patch.injectionOrder))
                                                })
                                            }
                                        }
                                    })
                                }
                            }
                            "prompt_order" -> {
                                val arr = v as? JsonArray
                                if (arr == null || orderedIdentifiers.isEmpty()) {
                                    put(k, v)
                                } else {
                                    // 同步更新所有含 order[] 的 character_id 桶：
                                    // 仅主桶（character_id == 100001 或首桶）允许追加新 identifier，其余模板桶（如 100000）仅原位置换已有项，不污染模板桶。
                                    val primaryBucketIdx = arr.indexOfFirst { bucket ->
                                        (bucket as? JsonObject)?.get("character_id")?.jsonPrimitive?.intOrNull == ST_PRESET_DEFAULT_CHARACTER_ID
                                    }.let { if (it >= 0) it else 0 }
                                    put(k, buildJsonArray {
                                        arr.forEachIndexed { bIdx, bucket ->
                                            val bObj = bucket as? JsonObject
                                            val orderArr = bObj?.get("order") as? JsonArray
                                            if (bObj == null || orderArr == null) {
                                                add(bucket)
                                            } else {
                                                val reorderedOrder = reorderOrderEntries(
                                                    existingOrder = orderArr,
                                                    orderedIdentifiers = orderedIdentifiers,
                                                    appendMissingIds = bIdx == primaryBucketIdx,
                                                )
                                                add(buildJsonObject {
                                                    bObj.forEach { (bk, bv) ->
                                                        if (bk == "order") put(bk, reorderedOrder) else put(bk, bv)
                                                    }
                                                })
                                            }
                                        }
                                    })
                                }
                            }
                            else -> put(k, v)
                        }
                    }
                }

                val updatedRoot = buildJsonObject {
                    root.forEach { (rk, rv) ->
                        if (rk == group) {
                            put(
                                rk,
                                if (wasStringEncoded) JsonPrimitive(updatedPresetObj.toString()) else updatedPresetObj,
                            )
                        } else {
                            put(rk, rv)
                        }
                    }
                }
                updatedRoot.toString()
            }.getOrElse { rawPresetsJson }
        }

        /**
         * 原位槽位置换（In-place slot permutation）：
         * 仅在 `existingOrder` 中被 `orderedIdentifiers` 占用的下标集合上按新顺序回填，
         * 未出现在预览中的条目（如 worldInfoBefore / worldInfoAfter / enhanceDefinitions / nsfw / 禁用项）
         * 严格保留在各自原有的相对位置，绝不被误甩到 prompt_order 末尾！
         */
        private fun reorderOrderEntries(
            existingOrder: JsonArray,
            orderedIdentifiers: List<String>,
            appendMissingIds: Boolean = true,
        ): JsonArray {
            val distinctOrdered = orderedIdentifiers.distinct()
            val byId = LinkedHashMap<String, JsonElement>()
            for (el in existingOrder) {
                val id = (el as? JsonObject)?.get("identifier")?.jsonPrimitive?.contentOrNull
                if (id != null && id !in byId) {
                    byId[id] = el
                }
            }
            val presentOrdered = distinctOrdered.filter { it in byId }
            val presentSet = presentOrdered.toHashSet()
            val newIds = if (appendMissingIds) distinctOrdered.filter { it !in byId } else emptyList()

            var nextPresentIdx = 0
            val resultList = ArrayList<JsonElement>(existingOrder.size + newIds.size)
            val seenIds = HashSet<String>()
            for (el in existingOrder) {
                val id = (el as? JsonObject)?.get("identifier")?.jsonPrimitive?.contentOrNull
                if (id != null && id in presentSet && seenIds.add(id)) {
                    val replacementId = presentOrdered[nextPresentIdx++]
                    resultList.add(byId[replacementId]!!)
                } else {
                    resultList.add(el)
                }
            }
            for (newId in newIds) {
                resultList.add(
                    buildJsonObject {
                        put("identifier", JsonPrimitive(newId))
                        put("enabled", JsonPrimitive(true))
                    }
                )
            }
            return buildJsonArray {
                resultList.forEach { add(it) }
            }
        }
    }
}
