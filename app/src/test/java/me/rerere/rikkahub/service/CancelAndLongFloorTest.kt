package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.st.runtime.TavernChatJsonCache
import me.rerere.rikkahub.data.st.runtime.applyTavernChatUpdates
import me.rerere.rikkahub.data.st.runtime.buildTavernChatJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.uuid.Uuid

class CancelAndLongFloorTest {

    @Test
    fun cancelJobs_immediatelyResetsGenerationJobStateWithin200ms_andFencesGenerationEpoch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val convId = Uuid.random()
        val session = ConversationSession(
            id = convId,
            initial = Conversation.ofId(convId),
            scope = scope,
        )
        session.updateConversation(Conversation.ofId(convId))

        val initialEpoch = session.currentGenerationEpoch()
        var lateTokenAppended = false
        val slowStreamingJob = scope.launch {
            session.processingStatus.value = "Generating..."
            delay(5_000)
            lateTokenAppended = true
        }
        session.setJob(slowStreamingJob)
        val firstJobEpoch = session.currentGenerationEpoch()
        assertNotEquals(initialEpoch, firstJobEpoch)
        assertTrue(session.isGenerating)

        val cancelUiElapsedMs = measureTimeMillis {
            session.cancelJobs()
        }
        val afterCancelEpoch = session.currentGenerationEpoch()
        assertTrue("Epoch must advance on cancelJobs()", afterCancelEpoch > firstJobEpoch)

        // UI state (generationJob & processingStatus) must clear immediately (<200ms)
        assertTrue("UI state recovery took ${cancelUiElapsedMs}ms (>200ms)", cancelUiElapsedMs < 200)
        assertNull(session.getJob())
        assertNull(session.processingStatus.value)
        assertFalse(session.isGenerating)
        assertFalse(lateTokenAppended)

        // Subsequent send job must succeed cleanly with a new epoch
        var secondSendCompleted = false
        val secondJob = scope.launch {
            delay(10)
            secondSendCompleted = true
        }
        session.setJob(secondJob)
        val secondJobEpoch = session.currentGenerationEpoch()
        assertTrue("Epoch must advance on subsequent setJob()", secondJobEpoch > afterCancelEpoch)
        val beforeNullResetEpoch = session.currentGenerationEpoch()
        secondJob.join()
        assertTrue(secondSendCompleted)
        session.setJob(null, cancelPrevious = false)
        assertEquals(beforeNullResetEpoch, session.currentGenerationEpoch())
    }

    @Test
    fun buildTavernChatJson_memoizes500Floors_andUpdatesIncrementallyOnNewMessage() {
        TavernChatJsonCache.clear()
        val convId = Uuid.random()
        val nodes = (0 until 500).map { idx ->
            MessageNode(
                id = Uuid.random(),
                messages = listOf(
                    UIMessage(
                        role = if (idx % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("Floor #$idx content with HTML <div class=\"post-card\">Card $idx</div>")),
                    )
                ),
                selectIndex = 0,
            )
        }
        val conv500 = Conversation(
            id = convId,
            title = "500 Floor Perf Test",
            messageNodes = nodes,
        )

        val firstBuildJson = buildTavernChatJson(conv500, null)
        assertTrue(firstBuildJson.contains("Floor #499"))

        // Second build with 1 appended user message should hit cache for all 500 historical floors
        val appendedConv = conv500.copy(
            messageNodes = nodes + MessageNode(
                id = Uuid.random(),
                messages = listOf(UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Floor #500 user send")))),
                selectIndex = 0,
            )
        )
        var secondBuildJson = ""
        val elapsedMs = measureTimeMillis {
            secondBuildJson = buildTavernChatJson(appendedConv, null)
        }
        assertTrue(secondBuildJson.contains("Floor #500 user send"))
        assertTrue("Incremental 501-floor build took ${elapsedMs}ms", elapsedMs < 150)
        assertEquals(500, TavernChatJsonCache.lastCacheHits)
    }

    @Test
    fun buildTavernChatJson_invalidatesWhenNonSelectedSwipeOrCollidingTextChanges() {
        TavernChatJsonCache.clear()
        val convId = Uuid.random()
        val nodeId = Uuid.random()
        val msg0 = UIMessage(
            id = Uuid.random(),
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("Selected swipe 0")),
        )
        val msg1Before = UIMessage(
            id = Uuid.random(),
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("Unselected swipe 1 BEFORE")),
        )
        val convBefore = Conversation(
            id = convId,
            title = "Swipe Cache Invalidation Test",
            messageNodes = listOf(
                MessageNode(
                    id = nodeId,
                    messages = listOf(msg0, msg1Before),
                    selectIndex = 0,
                )
            ),
        )
        val jsonBefore = buildTavernChatJson(convBefore, null)
        assertTrue(jsonBefore.contains("Unselected swipe 1 BEFORE"))
        assertEquals(0, TavernChatJsonCache.lastCacheHits)

        // Mutate unselected swipe[1] without changing selected swipe[0], selectIndex, or messages.size
        val msg1After = msg1Before.copy(parts = listOf(UIMessagePart.Text("Unselected swipe 1 AFTER")))
        val convAfter = convBefore.copy(
            messageNodes = listOf(
                MessageNode(
                    id = nodeId,
                    messages = listOf(msg0, msg1After),
                    selectIndex = 0,
                )
            ),
        )
        val jsonAfter = buildTavernChatJson(convAfter, null)
        assertEquals(0, TavernChatJsonCache.lastCacheHits)
        assertTrue(jsonAfter.contains("Unselected swipe 1 AFTER"))
        assertFalse(jsonAfter.contains("Unselected swipe 1 BEFORE"))
    }
}
