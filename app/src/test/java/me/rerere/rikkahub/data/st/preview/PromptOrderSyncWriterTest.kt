package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.export.parseActiveStPresetConfig
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptOrderSyncWriterTest {

    @Test
    fun `拖入展开聊天记录精准计算 depth 且未触碰的 depth50 条目原封不动`() {
        val bookId = Uuid.random()
        val movedLoreId = Uuid.random()
        val untouchedDeepLoreId = Uuid.random()
        val draftBlocks = listOf(
            PromptPreviewBlock(
                key = "chat_region_header",
                origin = PromptSlotOrigin.ChatRegionHeader(50, 2, expanded = true),
                categoryLabel = "聊天记录",
                title = "聊天记录区",
                role = MessageRole.USER,
                plainBodyPreview = "",
                position = null,
                injectDepth = null,
                priority = null,
                draggable = false,
            ),
            PromptPreviewBlock(
                key = "lore_deep",
                origin = PromptSlotOrigin.LorebookEntry(bookId, untouchedDeepLoreId, "世界书"),
                categoryLabel = "世界书",
                title = "深层词条",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "deep",
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 50,
                priority = 10,
                draggable = true,
                isInsideChatHistory = true,
            ),
            PromptPreviewBlock(
                key = "chat_msg_1",
                origin = PromptSlotOrigin.ChatMessageAnchor("m1", MessageRole.USER, 2, 49),
                categoryLabel = "用户消息",
                title = "#49",
                role = MessageRole.USER,
                plainBodyPreview = "u1",
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 2,
                priority = null,
                draggable = false,
                isInsideChatHistory = true,
            ),
            PromptPreviewBlock(
                key = "lore_moved",
                origin = PromptSlotOrigin.LorebookEntry(bookId, movedLoreId, "世界书"),
                categoryLabel = "世界书",
                title = "拖入词条",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "moved",
                position = InjectionPosition.AFTER_CHARACTER,
                injectDepth = null,
                priority = 20,
                draggable = true,
                isInsideChatHistory = false,
            ),
            PromptPreviewBlock(
                key = "chat_msg_2",
                origin = PromptSlotOrigin.ChatMessageAnchor("m2", MessageRole.ASSISTANT, 1, 50),
                categoryLabel = "助手消息",
                title = "#50",
                role = MessageRole.ASSISTANT,
                plainBodyPreview = "a1",
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 1,
                priority = null,
                draggable = false,
                isInsideChatHistory = true,
            ),
            PromptPreviewBlock(
                key = "chat_msg_pending_draft",
                origin = PromptSlotOrigin.ChatMessageAnchor("__pending__", MessageRole.USER, 0, 51, isPendingDraft = true),
                categoryLabel = "待发送消息",
                title = "待发送",
                role = MessageRole.USER,
                plainBodyPreview = "",
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 0,
                priority = null,
                draggable = false,
                isInsideChatHistory = true,
            ),
            PromptPreviewBlock(
                key = "chat_region_footer",
                origin = PromptSlotOrigin.ChatRegionFooter,
                categoryLabel = "聊天记录",
                title = "结束",
                role = MessageRole.USER,
                plainBodyPreview = "",
                position = null,
                injectDepth = null,
                priority = null,
                draggable = false,
            ),
        )

        val plan = PromptOrderSyncWriter.computeSyncPlan(
            draftBlocks = draftBlocks,
            dirtyKeys = setOf("lore_moved"),
            activePresetGroup = null,
        )
        // lore_moved 下方有 2 条 ChatMessageAnchor (#50 与 待发送) -> depth = 2
        val movedPatch = plan.loreUpdates[bookId to movedLoreId]!!
        assertEquals(InjectionPosition.AT_DEPTH, movedPatch.position)
        assertEquals(2, movedPatch.depth)
        // untouchedDeepLoreId 未被拖动，不得进入 loreUpdates
        assertFalse((bookId to untouchedDeepLoreId) in plan.loreUpdates)
    }

    @Test
    fun `预设完整双写 JSON 同步 prompts 与 prompt_order 并通过 parseActiveStPresetConfig 回读一致`() {
        val rawPreset = """
            {
              "prompts": [
                {"identifier": "main", "name": "Main", "role": "system", "content": "hi", "marker": false},
                {"identifier": "custom_jb", "name": "JB", "role": "system", "content": "jb", "injection_position": 0, "injection_depth": 4, "injection_order": 100}
              ],
              "prompt_order": [
                {
                  "character_id": 100001,
                  "order": [
                    {"identifier": "main", "enabled": true},
                    {"identifier": "chatHistory", "enabled": true},
                    {"identifier": "custom_jb", "enabled": true},
                    {"identifier": "disabled_slot", "enabled": false}
                  ]
                }
              ]
            }
        """.trimIndent()
        val container = """{"TestPreset":${kotlinx.serialization.json.JsonPrimitive(rawPreset)}}"""

        val updated = PromptOrderSyncWriter.syncTavernPresetJson(
            rawPresetsJson = container,
            group = "TestPreset",
            orderedIdentifiers = listOf("chatHistory", "main", "custom_jb"),
            promptPatches = mapOf("custom_jb" to PresetPromptPatch(injectionPosition = 1, injectionDepth = 2, injectionOrder = 30)),
        )
        val updatedRoot = kotlinx.serialization.json.Json.parseToJsonElement(updated) as kotlinx.serialization.json.JsonObject
        val unwrappedPreset = (updatedRoot["TestPreset"] as kotlinx.serialization.json.JsonPrimitive).content
        assertTrue(unwrappedPreset.contains("\"injection_position\":1"))
        assertTrue(unwrappedPreset.contains("\"injection_depth\":2"))
        assertTrue(unwrappedPreset.contains("\"disabled_slot\""))

        val parsed = parseActiveStPresetConfig(updated, setOf("TestPreset"))
        val chatIdx = parsed.orderedMarkers.indexOf("chatHistory")
        val mainIdx = parsed.orderedMarkers.indexOf("main")
        assertTrue("回读后 chatHistory 应排在 main 之前", chatIdx >= 0 && mainIdx >= 0 && chatIdx < mainIdx)
    }

    @Test
    fun `损坏预设 JSON 双写安全兜底返回原串不抛异常`() {
        val broken = "{not valid json"
        assertEquals(
            broken,
            PromptOrderSyncWriter.syncTavernPresetJson(broken, "G", listOf("main"), emptyMap()),
        )
    }

    @Test
    fun `原位槽位置换不得将未展示的 worldInfoBefore 与 nsfw 甩到 prompt_order 末尾`() {
        val rawPreset = """
            {
              "prompts": [],
              "prompt_order": [
                {
                  "character_id": 100000,
                  "order": [
                    {"identifier": "main", "enabled": true},
                    {"identifier": "worldInfoBefore", "enabled": true},
                    {"identifier": "personaDescription", "enabled": true},
                    {"identifier": "charDescription", "enabled": true},
                    {"identifier": "nsfw", "enabled": false},
                    {"identifier": "chatHistory", "enabled": true}
                  ]
                },
                {
                  "character_id": 100001,
                  "order": [
                    {"identifier": "main", "enabled": true},
                    {"identifier": "worldInfoBefore", "enabled": true},
                    {"identifier": "personaDescription", "enabled": true},
                    {"identifier": "charDescription", "enabled": true},
                    {"identifier": "nsfw", "enabled": false},
                    {"identifier": "chatHistory", "enabled": true}
                  ]
                }
              ]
            }
        """.trimIndent()
        val container = """{"TestPreset":${kotlinx.serialization.json.JsonPrimitive(rawPreset)}}"""

        // 仅重排预览中出现的 4 个槽位（把 chatHistory 移到 main 之前），未出现 worldInfoBefore 与 nsfw
        val updated = PromptOrderSyncWriter.syncTavernPresetJson(
            rawPresetsJson = container,
            group = "TestPreset",
            orderedIdentifiers = listOf("chatHistory", "main", "personaDescription", "charDescription"),
            promptPatches = emptyMap(),
        )

        val root = kotlinx.serialization.json.Json.parseToJsonElement(updated) as kotlinx.serialization.json.JsonObject
        val presetStr = (root["TestPreset"] as kotlinx.serialization.json.JsonPrimitive).content
        val presetObj = kotlinx.serialization.json.Json.parseToJsonElement(presetStr) as kotlinx.serialization.json.JsonObject
        val buckets = presetObj["prompt_order"] as kotlinx.serialization.json.JsonArray
        assertEquals(2, buckets.size)
        for (bucket in buckets) {
            val order = (bucket as kotlinx.serialization.json.JsonObject)["order"] as kotlinx.serialization.json.JsonArray
            val ids = order.map { ((it as kotlinx.serialization.json.JsonObject)["identifier"] as kotlinx.serialization.json.JsonPrimitive).content }
            // worldInfoBefore 必须留在下标 1，nsfw 必须留在下标 4（原位不动！）
            assertEquals(listOf("chatHistory", "worldInfoBefore", "main", "personaDescription", "nsfw", "charDescription"), ids)
        }
    }

    @Test
    fun `拖动世界书词条越过 priority100 词条时同步重算同区顺序使拖拽生效`() {
        val bookId = Uuid.random()
        val entryA = Uuid.random()
        val entryB = Uuid.random()
        val draftBlocks = listOf(
            PromptPreviewBlock(
                key = "lore_b",
                origin = PromptSlotOrigin.LorebookEntry(bookId, entryB, "书"),
                categoryLabel = "世界书",
                title = "B(原priority=100)",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "b",
                position = InjectionPosition.AFTER_CHARACTER,
                injectDepth = null,
                priority = 100,
                draggable = true,
            ),
            PromptPreviewBlock(
                key = "lore_a",
                origin = PromptSlotOrigin.LorebookEntry(bookId, entryA, "书"),
                categoryLabel = "世界书",
                title = "A(被拖到B后面)",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "a",
                position = InjectionPosition.AFTER_CHARACTER,
                injectDepth = null,
                priority = 10,
                draggable = true,
            ),
        )
        val plan = PromptOrderSyncWriter.computeSyncPlan(
            draftBlocks = draftBlocks,
            dirtyKeys = setOf("lore_a"),
            activePresetGroup = null,
        )
        val patchB = plan.loreUpdates[bookId to entryB]!!
        val patchA = plan.loreUpdates[bookId to entryA]!!
        assertTrue("B 应排在 A 前面（priority 更小）", patchB.priority < patchA.priority)
    }

    @Test
    fun `chatHistory 拖到 main 之前时 inferRelativePosition 不应将后续条目误判为 AFTER_DIALOG`() {
        val draftBlocks = listOf(
            PromptPreviewBlock("chat_region_header", PromptSlotOrigin.ChatRegionHeader(2, 0, false), "聊天", "h", MessageRole.USER, "", null, null, null, false),
            PromptPreviewBlock("chat_region_footer", PromptSlotOrigin.ChatRegionFooter, "聊天", "f", MessageRole.USER, "", null, null, null, false),
            PromptPreviewBlock("marker_main", PromptSlotOrigin.PresetMarker("G", "main"), "预设", "main", MessageRole.SYSTEM, "", null, null, null, true),
            PromptPreviewBlock("moved_after_main", PromptSlotOrigin.ModeInjectionItem(Uuid.random(), null, null), "规则", "m", MessageRole.SYSTEM, "", InjectionPosition.AFTER_SYSTEM_PROMPT, null, 10, true),
        )
        val pos = PromptOrderSyncWriter.inferRelativePosition(
            blocks = draftBlocks,
            index = 3,
            headerIdx = 0,
            footerIdx = 1,
            previousPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        )
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, pos)
    }

    @Test
    fun `dragging jailbreak preset marker syncs both prompt_order and deterministic ModeInjection`() {
        val blocks = listOf(
            PromptPreviewBlock(
                key = "marker_main",
                origin = PromptSlotOrigin.PresetMarker("P1", "main"),
                categoryLabel = "预设",
                title = "main",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "",
                position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                injectDepth = null,
                priority = null,
                draggable = true,
            ),
            PromptPreviewBlock(
                key = "marker_charDescription",
                origin = PromptSlotOrigin.PresetMarker("P1", "charDescription"),
                categoryLabel = "角色卡",
                title = "charDescription",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "",
                position = null,
                injectDepth = null,
                priority = null,
                draggable = true,
            ),
            PromptPreviewBlock(
                key = "marker_jailbreak",
                origin = PromptSlotOrigin.PresetMarker("P1", "jailbreak"),
                categoryLabel = "预设",
                title = "jailbreak",
                role = MessageRole.SYSTEM,
                plainBodyPreview = "jb",
                position = InjectionPosition.AFTER_DIALOG,
                injectDepth = null,
                priority = null,
                draggable = true,
            ),
            PromptPreviewBlock(
                key = "chat_region_header",
                origin = PromptSlotOrigin.ChatRegionHeader(totalMessageCount = 1, embeddedDepthCount = 0, expanded = false),
                categoryLabel = "聊天记录",
                title = "chat",
                role = MessageRole.USER,
                plainBodyPreview = "",
                position = null,
                injectDepth = null,
                priority = null,
                draggable = false,
            ),
            PromptPreviewBlock(
                key = "chat_region_footer",
                origin = PromptSlotOrigin.ChatRegionFooter,
                categoryLabel = "聊天记录",
                title = "end",
                role = MessageRole.USER,
                plainBodyPreview = "",
                position = null,
                injectDepth = null,
                priority = null,
                draggable = false,
            ),
        )

        val plan = PromptOrderSyncWriter.computeSyncPlan(
            draftBlocks = blocks,
            dirtyKeys = setOf("marker_jailbreak"),
            activePresetGroup = "P1",
        )

        val jbId = PromptOrderSyncWriter.deterministicPresetEntryId("P1", "jailbreak")
        assertEquals(InjectionPosition.AFTER_CHARACTER, plan.modeUpdates[jbId]?.position)
        assertEquals(0, plan.presetPromptJsonPatches["jailbreak"]?.injectionPosition)
        assertEquals(listOf("main", "charDescription", "jailbreak", "chatHistory"), plan.presetIdentifierOrder)
    }
}
