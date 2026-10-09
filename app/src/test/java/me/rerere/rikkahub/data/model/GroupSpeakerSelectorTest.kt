package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * E3：群聊发言人策略 vs ST `group-chats.js` 对照测试（纯逻辑，无 UI 依赖）。
 *
 * 金标准（SillyTavern 1.19.0 `public/scripts/group-chats.js`）：
 * - `group_activation_strategy = { NATURAL:0, LIST:1, MANUAL:2, POOLED:3 }`（:122-127）
 * - `group_generation_mode  = { SWAP:0, APPEND:1, APPEND_DISABLED:2 }`（:129-133）
 * - `DEFAULT_AUTO_MODE_DELAY = 5`（:135）
 * - 策略分发（:1002-1031）：
 *   force_chid → 单人；quiet/swipe/continue/impersonate 特殊分支；
 *   NATURAL → activateNaturalOrder；LIST → activateListOrder；
 *   POOLED → activatePooledOrder；MANUAL 且非用户输入 → shuffle 取 1
 * - talkativeness 掷骰（:1282-1288）：`talkativeness >= rollValue` 才入选；>0 才算 chatty
 */
class GroupSpeakerSelectorTest {

    private fun member(name: String, talkativeness: Float = 0.5f, id: Uuid = Uuid.random()): Assistant =
        Assistant(id = id, name = name, talkativeness = talkativeness)

    // ==================== ST 枚举 / 默认值 对照 ====================

    @Test
    fun `activation strategy ordinals match ST numeric enum`() {
        assertEquals(0, GroupActivationStrategy.NATURAL.ordinal)
        assertEquals(1, GroupActivationStrategy.LIST.ordinal)
        assertEquals(2, GroupActivationStrategy.MANUAL.ordinal)
        assertEquals(3, GroupActivationStrategy.POOLED.ordinal)
    }

    @Test
    fun `generation mode ordinals match ST numeric enum`() {
        assertEquals(0, GroupGenerationMode.SWAP.ordinal)
        assertEquals(1, GroupGenerationMode.APPEND.ordinal)
        assertEquals(2, GroupGenerationMode.APPEND_DISABLED.ordinal)
    }

    @Test
    fun `default auto mode delay matches ST DEFAULT_AUTO_MODE_DELAY`() {
        assertEquals(5, GroupChat().autoModeDelay)
    }

    // ==================== LIST ====================

    @Test
    fun `LIST activates every enabled member in order`() {
        val a = member("A")
        val b = member("B")
        val c = member("C")
        assertEquals(listOf(a.id, b.id, c.id), GroupSpeakerSelector.pickList(listOf(a, b, c)))
        assertEquals(
            listOf(a.id, b.id),
            GroupSpeakerSelector.pick(
                strategy = GroupActivationStrategy.LIST,
                members = listOf(a, b, c),
                enabledMembers = listOf(a, b),
            ),
        )
    }

    // ==================== NATURAL ====================

    @Test
    fun `NATURAL empty members returns empty`() {
        assertTrue(GroupSpeakerSelector.pickNatural(emptyList(), "hi", null, false).isEmpty())
    }

    @Test
    fun `NATURAL talkativeness 1 activates everyone except banned last speaker`() {
        val a = member("Alice", 1.0f)
        val b = member("Bob", 1.0f)
        val c = member("Carol", 1.0f)
        val all = GroupSpeakerSelector.pickNatural(listOf(a, b, c), "", null, false)
        assertEquals(setOf(a.id, b.id, c.id), all.toSet())

        // allowSelfResponses=false → 上一位发言者被 ban（对齐 ST bannedId）
        val banned = GroupSpeakerSelector.pickNatural(listOf(a, b, c), "", b.id, false)
        assertEquals(setOf(a.id, c.id), banned.toSet())
        assertFalse(banned.contains(b.id))

        // allowSelfResponses=true → 不 ban
        val allowed = GroupSpeakerSelector.pickNatural(listOf(a, b, c), "", b.id, true)
        assertEquals(setOf(a.id, b.id, c.id), allowed.toSet())
    }

    @Test
    fun `NATURAL mention activates the named member`() {
        val alice = member("Alice", 0.0f)
        val bob = member("Bob", 0.0f)
        val picked = GroupSpeakerSelector.pickNatural(listOf(alice, bob), "Alice, 你怎么看？", null, false)
        assertTrue("被 @ 的成员必须入选", picked.contains(alice.id))
    }

    @Test
    fun `NATURAL mention is case-insensitive and ignores short tokens`() {
        val alice = member("Alice", 0.0f)
        val picked = GroupSpeakerSelector.pickNatural(listOf(alice), "hey ALICE!", null, false)
        assertTrue(picked.contains(alice.id))

        // 单字符名字 token（<2）不参与匹配 → 退回兜底路径，不应命中「A」
        val single = member("A", 0.0f)
        val out = GroupSpeakerSelector.pickNatural(listOf(single), "a", null, false)
        // 兜底：chatty 为空 → 全体随机，最多 1 个
        assertTrue(out.size <= 1)
    }

    @Test
    fun `NATURAL banned member is not activated by mention`() {
        // bob talkativeness=1 -> 骰子必中，避开「全员掷不进」的兜底路径，专测“@ 不能绕过 ban”
        val alice = member("Alice", 0.0f)
        val bob = member("Bob", 1.0f)
        val picked = GroupSpeakerSelector.pickNatural(listOf(alice, bob), "Alice 你说", alice.id, false)
        assertFalse("被 ban 的成员不得因 @ 入选", picked.contains(alice.id))
        assertTrue("非 ban 成员必须入选", picked.contains(bob.id))
    }

    @Test
    fun `NATURAL all-zero talkativeness fallback matches ST quirk`() {
        // ST group-chats.js:1296 `randomPool = chattyMembers.length > 0 ? chattyMembers : members`
        // -- 兜底池在 chatty 为空时是【全体成员】，**不过滤 bannedUser**（ST 原生行为）。
        // 宿主保持同款语义；此用例锁定该 quirk，避免被误「修正」成与 ST 不一致。
        val alice = member("Alice", 0.0f)
        val bob = member("Bob", 0.0f)
        val picked = GroupSpeakerSelector.pickNatural(listOf(alice, bob), "", alice.id, false)
        assertEquals(1, picked.size)
        assertTrue(picked[0] == alice.id || picked[0] == bob.id)
    }

    @Test
    fun `NATURAL falls back to chatty members when nobody rolled in`() {
        val quiet = member("Quiet", 0.0f)
        val chatty = member("Chatty", 0.0f) // 也掷不进，但 chatty 判定按 >0
        val real = member("Real", 0.0f)
        // 全部 talkativeness=0 → 兜底 pool = chatty(空) → members
        val out = GroupSpeakerSelector.pickNatural(listOf(quiet, chatty, real), "", null, false)
        assertEquals(1, out.size)
        assertTrue(out[0] in listOf(quiet.id, chatty.id, real.id))

        // 有 chatty 时兜底只从 chatty 里选
        val talky = member("Talky", 0.6f)
        val silent = member("Silent", 0.0f)
        val out2 = GroupSpeakerSelector.pickNatural(listOf(silent, talky), "", null, false)
        // talky 可能已被骰子选中；无论哪条路径都只能是 talky
        assertTrue(out2.isNotEmpty())
        assertTrue(out2.all { it == talky.id })
    }

    // ==================== POOLED ====================

    @Test
    fun `POOLED user input picks from all members`() {
        val a = member("A")
        val b = member("B")
        val picked = GroupSpeakerSelector.pickPooled(
            members = listOf(a, b), lastSpeakerId = null, speakerHistory = listOf(a.id, b.id),
            allowSelfResponses = false, isUserInput = true,
        )
        assertEquals(1, picked.size)
        assertTrue(picked[0] == a.id || picked[0] == b.id)
    }

    @Test
    fun `POOLED non-user input prefers members who have not spoken`() {
        val a = member("A")
        val b = member("B")
        val c = member("C")
        val picked = GroupSpeakerSelector.pickPooled(
            members = listOf(a, b, c), lastSpeakerId = null, speakerHistory = listOf(a.id, b.id),
            allowSelfResponses = false, isUserInput = false,
        )
        assertEquals(listOf(c.id), picked)
    }

    @Test
    fun `POOLED everyone spoke avoids last speaker when possible`() {
        val a = member("A")
        val b = member("B")
        val picked = GroupSpeakerSelector.pickPooled(
            members = listOf(a, b), lastSpeakerId = b.id,
            speakerHistory = listOf(a.id, b.id), allowSelfResponses = false, isUserInput = false,
        )
        assertEquals(listOf(a.id), picked)
    }

    @Test
    fun `POOLED empty members returns empty`() {
        assertTrue(
            GroupSpeakerSelector.pickPooled(emptyList(), null, emptyList(), false, true).isEmpty(),
        )
    }

    // ==================== MANUAL ====================

    @Test
    fun `MANUAL with explicit speaker returns that speaker`() {
        val a = member("A")
        val b = member("B")
        assertEquals(
            listOf(b.id),
            GroupSpeakerSelector.pick(
                strategy = GroupActivationStrategy.MANUAL,
                members = listOf(a, b), enabledMembers = listOf(a, b),
                manualSpeakerId = b.id, isUserInput = false,
            ),
        )
    }

    @Test
    fun `MANUAL with disabled speaker returns empty`() {
        val a = member("A")
        val b = member("B")
        assertTrue(
            GroupSpeakerSelector.pick(
                strategy = GroupActivationStrategy.MANUAL,
                members = listOf(a, b), enabledMembers = listOf(a),
                manualSpeakerId = b.id, isUserInput = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `MANUAL user input without explicit speaker generates nothing`() {
        val a = member("A")
        assertTrue(
            GroupSpeakerSelector.pick(
                strategy = GroupActivationStrategy.MANUAL,
                members = listOf(a), enabledMembers = listOf(a),
                manualSpeakerId = null, isUserInput = true,
            ).isEmpty(),
        )
    }

    @Test
    fun `MANUAL auto mode picks exactly one enabled member`() {
        val a = member("A")
        val b = member("B")
        val picked = GroupSpeakerSelector.pick(
            strategy = GroupActivationStrategy.MANUAL,
            members = listOf(a, b), enabledMembers = listOf(a, b),
            manualSpeakerId = null, isUserInput = false,
        )
        assertEquals(1, picked.size)
        assertTrue(picked[0] == a.id || picked[0] == b.id)
    }

    // ==================== 统一入口 ====================

    @Test
    fun `pick dispatches each strategy without throwing`() {
        val a = member("A", 0.5f)
        val b = member("B", 0.5f)
        for (s in GroupActivationStrategy.entries) {
            GroupSpeakerSelector.pick(
                strategy = s,
                members = listOf(a, b), enabledMembers = listOf(a, b),
                userInput = "hello", lastSpeakerId = null,
            )
        }
        assertTrue(true)
    }
}
