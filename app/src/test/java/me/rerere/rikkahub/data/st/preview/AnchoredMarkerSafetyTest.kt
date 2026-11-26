package me.rerere.rikkahub.data.st.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「12 Marker 全自由重排」保险档回归测试。
 *
 * 面板过去对所有 marker 都显示拖拽手柄，但真实发送路径只接管了其中 7 个槽位。
 * 用户把 nsfw / enhanceDefinitions 拖到最前面，能存进 prompt_order，
 * 发出去时却完全没变——UI 承诺超过了引擎能力。
 *
 * 保险档的做法是：做不到的明确标成「锚定 · 不可拖动」并给出原因，
 * 而不是继续假装能拖。这些用例锁死那份能力清单。
 */
class AnchoredMarkerSafetyTest {

    @Test
    fun `nsfw 与 enhanceDefinitions 必须被标记为完全锚定`() {
        assertTrue("nsfw 应被标记为锚定", "nsfw" in ST_ANCHORED_MARKERS)
        assertTrue("enhanceDefinitions 应被标记为锚定", "enhanceDefinitions" in ST_ANCHORED_MARKERS)
    }

    @Test
    fun `真正支持自由重排的 7 个槽位不能被误标为锚定`() {
        val freeSlots = listOf(
            "main",
            "charDescription",
            "charPersonality",
            "scenario",
            "dialogueExamples",
            "chatHistory",
            "personaDescription",
        )
        freeSlots.forEach { slot ->
            assertTrue("$slot 应可自由重排，不该出现在锚定清单里", slot !in ST_ANCHORED_MARKERS)
            assertNull("$slot 不该有锚定原因文案", anchoredMarkerReason(slot))
        }
    }

    @Test
    fun `相对区锚定的 marker 仍可拖动但必须给出原因`() {
        listOf("worldInfoBefore", "worldInfoAfter", "jailbreak").forEach { slot ->
            assertTrue("$slot 属于相对区锚定", slot in ST_RELATIVE_ANCHORED_MARKERS)
            assertNotNull("$slot 必须说明为什么不能跨区移动", anchoredMarkerReason(slot))
        }
    }

    @Test
    fun `锚定清单必须都落在 12 个内置 marker 之内`() {
        (ST_ANCHORED_MARKERS + ST_RELATIVE_ANCHORED_MARKERS).forEach { marker ->
            assertTrue(
                "$marker 不是 ST 内置 marker，锚定清单写错了",
                marker in ST_BUILTIN_MARKERS,
            )
        }
    }

    @Test
    fun `12 个 marker 必须被完整划分`() {
        assertEquals("内置 marker 应为 12 个", 12, ST_BUILTIN_MARKERS.size)
        // 每个 marker 要么自由、要么相对锚定、要么完全锚定，不能有落在定义之外的
        ST_BUILTIN_MARKERS.forEach { marker ->
            val isFree = marker !in ST_ANCHORED_MARKERS && marker !in ST_RELATIVE_ANCHORED_MARKERS
            val isRelative = marker in ST_RELATIVE_ANCHORED_MARKERS
            val isAnchored = marker in ST_ANCHORED_MARKERS
            assertEquals(
                "$marker 的能力分类必须唯一",
                1,
                listOf(isFree, isRelative, isAnchored).count { it },
            )
        }
    }
}
