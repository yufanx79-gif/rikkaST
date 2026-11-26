package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.st.preview.PromptPreviewBlock
import me.rerere.rikkahub.data.st.preview.PromptSlotOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptStructurePreviewDragTest {

    private fun sampleCollapsedBlocks(modeId: Uuid): List<PromptPreviewBlock> = listOf(
        PromptPreviewBlock(
            key = "marker_main",
            origin = PromptSlotOrigin.PresetMarker("P1", "main"),
            categoryLabel = "预设",
            title = "主系统提示词 (main)",
            role = MessageRole.SYSTEM,
            plainBodyPreview = "main",
            position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            injectDepth = null,
            priority = null,
            draggable = true,
        ),
        PromptPreviewBlock(
            key = "mode_$modeId",
            origin = PromptSlotOrigin.ModeInjectionItem(modeId, "P1", "custom1"),
            categoryLabel = "预设",
            title = "自定义规则",
            role = MessageRole.SYSTEM,
            plainBodyPreview = "rule",
            position = InjectionPosition.AFTER_CHARACTER,
            injectDepth = null,
            priority = 10,
            draggable = true,
        ),
        PromptPreviewBlock(
            key = "chat_region_header",
            origin = PromptSlotOrigin.ChatRegionHeader(totalMessageCount = 2, embeddedDepthCount = 0, expanded = false),
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
        ),
    )

    private fun sampleChatExpansion(): List<PromptPreviewBlock> = listOf(
        PromptPreviewBlock(
            key = "chat_msg_1",
            origin = PromptSlotOrigin.ChatMessageAnchor("1", MessageRole.USER, depthFromEnd = 2, floorNumber = 1),
            categoryLabel = "用户消息",
            title = "#1",
            role = MessageRole.USER,
            plainBodyPreview = "hi",
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 2,
            priority = null,
            draggable = false,
            isInsideChatHistory = true,
        ),
        PromptPreviewBlock(
            key = "chat_msg_2",
            origin = PromptSlotOrigin.ChatMessageAnchor("2", MessageRole.ASSISTANT, depthFromEnd = 1, floorNumber = 2),
            categoryLabel = "助手消息",
            title = "#2",
            role = MessageRole.ASSISTANT,
            plainBodyPreview = "hello",
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 1,
            priority = null,
            draggable = false,
            isInsideChatHistory = true,
        ),
        PromptPreviewBlock(
            key = "chat_msg_pending_draft",
            origin = PromptSlotOrigin.ChatMessageAnchor("__pending_draft__", MessageRole.USER, depthFromEnd = 0, floorNumber = 3, isPendingDraft = true),
            categoryLabel = "待发送消息",
            title = "草稿",
            role = MessageRole.USER,
            plainBodyPreview = "",
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 0,
            priority = null,
            draggable = false,
            isInsideChatHistory = true,
        ),
    )

    @Test
    fun `dragging external mode injection into collapsed chat region auto expands chat history`() {
        val modeId = Uuid.random()
        val blocks = sampleCollapsedBlocks(modeId)
        val expansion = sampleChatExpansion()

        // Drag mode (index 1) into index 2 (between ChatRegionHeader and ChatRegionFooter)
        val outcome = applyPreviewDragMove(
            currentBlocks = blocks,
            dirtyKeys = emptySet(),
            isChatExpanded = false,
            chatMessagesForExpansion = expansion,
            fromIndex = 1,
            toIndex = 2,
        )

        assertNotNull(outcome)
        assertTrue("Chat history must auto-expand on drag into chat region", outcome!!.isChatExpanded)
        assertTrue("mode_$modeId" in outcome.dirtyKeys)
        assertTrue(outcome.blocks.any { it.origin is PromptSlotOrigin.ChatMessageAnchor })
        val header = outcome.blocks.first { it.origin is PromptSlotOrigin.ChatRegionHeader }
        assertTrue((header.origin as PromptSlotOrigin.ChatRegionHeader).expanded)
    }

    @Test
    fun `dragging PresetMarker into chat region leapfrogs cleanly outside chat boundaries`() {
        val modeId = Uuid.random()
        val blocks = sampleCollapsedBlocks(modeId)
        val expansion = sampleChatExpansion()

        // Drag marker_main (index 0) down into index 2 (header..footer) -> leapfrogs after ChatRegionFooter
        val downOutcome = applyPreviewDragMove(
            currentBlocks = blocks,
            dirtyKeys = emptySet(),
            isChatExpanded = false,
            chatMessagesForExpansion = expansion,
            fromIndex = 0,
            toIndex = 2,
        )
        assertNotNull(downOutcome)
        val downKeys = downOutcome!!.blocks.map { it.key }
        assertEquals(listOf("mode_$modeId", "chat_region_header", "chat_region_footer", "marker_main"), downKeys)

        // Drag marker_main (now index 3) up into index 2 (header..footer) -> leapfrogs before ChatRegionHeader
        val upOutcome = applyPreviewDragMove(
            currentBlocks = downOutcome.blocks,
            dirtyKeys = downOutcome.dirtyKeys,
            isChatExpanded = false,
            chatMessagesForExpansion = expansion,
            fromIndex = 3,
            toIndex = 2,
        )
        assertNotNull(upOutcome)
        val upKeys = upOutcome!!.blocks.map { it.key }
        assertEquals(listOf("mode_$modeId", "marker_main", "chat_region_header", "chat_region_footer"), upKeys)
    }

    @Test
    fun `dragging non-draggable ChatRegionHeader is ignored`() {
        val modeId = Uuid.random()
        val blocks = sampleCollapsedBlocks(modeId)

        val outcome = applyPreviewDragMove(
            currentBlocks = blocks,
            dirtyKeys = emptySet(),
            isChatExpanded = false,
            chatMessagesForExpansion = sampleChatExpansion(),
            fromIndex = 2,
            toIndex = 0,
        )

        assertNull(outcome)
    }

    @Test
    fun `interleaveEmbeddedDepthWithMessages aligns depth 0 after pending draft and depth 1 before pending draft`() {
        val chat = sampleChatExpansion()
        val d0 = PromptPreviewBlock(
            key = "d0",
            origin = PromptSlotOrigin.AuthorsNote,
            categoryLabel = "作者注",
            title = "d0",
            role = MessageRole.SYSTEM,
            plainBodyPreview = "",
            position = InjectionPosition.AT_DEPTH,
            injectDepth = 0,
            priority = 0,
            draggable = true,
            isInsideChatHistory = true,
        )
        val d1 = d0.copy(key = "d1", title = "d1", injectDepth = 1)

        val merged = interleaveEmbeddedDepthWithMessages(listOf(d0, d1), chat)
        val keys = merged.map { it.key }
        assertEquals(
            listOf("chat_msg_1", "chat_msg_2", "d1", "chat_msg_pending_draft", "d0"),
            keys,
        )
    }
}
