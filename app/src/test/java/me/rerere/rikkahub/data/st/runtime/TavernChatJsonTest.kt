package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.withHidden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * TavernChatJson（M1 swipe 桥接）单元测试：
 * - build：ST 原生字段（swipes / swipe_id / mes / is_system / extra）；
 * - apply：JS → Kotlin 回写（swipe 切换 / 文本写入 / 变体重建 / 隐藏标记 / 名称 / 无变化）。
 *
 * `variables` / `swipe_info` 不再进楼层 JSON：runtime.js 的 pullChat() 每轮用轻量级的
 * `getMessageVariablesJson`（只传变量树，不传 218KB 消息文本）补上 `m.variables`，
 * 全量 getChatJson 里再带一份纯属重复序列化。`swipe_info` 全仓库无任何消费者。
 */
class TavernChatJsonTest {

    // buildTavernChatJson 现在从 Settings 里解析发言人名（助手卡名 / 用户昵称）。
    // 单测没有 Settings，所以每条消息显式带上名字，断言才不依赖默认值。
    private fun msg(role: MessageRole, text: String, name: String? = null) =
        UIMessage(
            role = role,
            parts = listOf(UIMessagePart.Text(text)),
            name = name ?: if (role == MessageRole.USER) "user" else "assistant",
        )

    private fun conv(vararg nodes: MessageNode): Conversation = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        messageNodes = nodes.toList(),
    )

    private fun node(vararg texts: String, select: Int = 0, role: MessageRole = MessageRole.ASSISTANT) =
        MessageNode(messages = texts.map { msg(role, it) }, selectIndex = select)

    // ------------------------------------------------------------
    // build
    // ------------------------------------------------------------

    @Test
    fun buildIncludesSwipesAndSwipeId() {
        val c = conv(
            node("v0", "v1", "v2", select = 2),
            node("hello", role = MessageRole.USER),
        )
        val arr = Json.parseToJsonElement(buildTavernChatJson(c, null)).jsonArray
        assertEquals(2, arr.size)

        val first = arr[0].jsonObject
        assertEquals("assistant", first["name"]!!.jsonPrimitive.content)
        assertFalse(first["is_user"]!!.jsonPrimitive.boolean)
        assertEquals(2, first["swipe_id"]!!.jsonPrimitive.int)
        assertEquals("v2", first["mes"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("v0", "v1", "v2"),
            first["swipes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertFalse(first["is_system"]!!.jsonPrimitive.boolean)
        // 变量与 swipe 元数据由 runtime.js 的 pullChat() 单独合并，楼层 JSON 里不再重复
        assertNull(first["variables"])
        assertNull(first["swipe_info"])

        val second = arr[1].jsonObject
        assertTrue(second["is_user"]!!.jsonPrimitive.boolean)
        assertEquals("user", second["name"]!!.jsonPrimitive.content)
        assertEquals(1, second["swipes"]!!.jsonArray.size)
    }

    @Test
    fun buildOmitsMessageVariablesSoTheyAreFetchedSeparately() {
        // 消息变量走 getMessageVariablesJson（数组套数组，按楼层索引对齐），
        // 楼层 JSON 不再内嵌，避免长对话每次 fireEvent 都重复序列化整棵变量树。
        val vars = Json.parseToJsonElement("""[[{"hp":5}],[{"x":1},{"x":2}]]""").jsonArray
        assertEquals(2, vars.size)
        val c = conv(node("a"), node("b", "c", select = 1))
        val arr = Json.parseToJsonElement(buildTavernChatJson(c, null)).jsonArray
        assertEquals(2, arr.size)
        arr.forEach { assertNull(it.jsonObject["variables"]) }
    }

    @Test
    fun buildHiddenFlag() {
        val hidden = msg(MessageRole.ASSISTANT, "secret").withHidden()
        val c = conv(MessageNode(messages = listOf(hidden), selectIndex = 0))
        val arr = Json.parseToJsonElement(buildTavernChatJson(c, null)).jsonArray
        assertTrue(arr[0].jsonObject["is_system"]!!.jsonPrimitive.boolean)
    }

    // ------------------------------------------------------------
    // apply
    // ------------------------------------------------------------

    @Test
    fun applySwipeIdSwitch() {
        val c = conv(node("a", "b", "c", select = 0))
        val out = applyTavernChatUpdates(c, """[{"message_id":0,"swipe_id":2}]""")!!
        assertEquals(2, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applyMesWritesCurrentVariant() {
        val c = conv(node("a", "b", "c", select = 1))
        val out = applyTavernChatUpdates(c, """[{"message_id":0,"mes":"B2"}]""")!!
        assertEquals(listOf("a", "B2", "c"), out.messageNodes[0].messages.map { it.toText() })
    }

    @Test
    fun applySwipesRebuildsVariants() {
        val c = conv(node("a", "b", select = 1))
        val out = applyTavernChatUpdates(c, """[{"message_id":0,"swipes":["x","y","z"]}]""")!!
        assertEquals(listOf("x", "y", "z"), out.messageNodes[0].messages.map { it.toText() })
        assertEquals(1, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applySwipesShrinkClampsSelect() {
        val c = conv(node("a", "b", "c", select = 2))
        val out = applyTavernChatUpdates(c, """[{"message_id":0,"swipes":["only"]}]""")!!
        assertEquals(listOf("only"), out.messageNodes[0].messages.map { it.toText() })
        assertEquals(0, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applyHiddenAndName() {
        val c = conv(node("a", select = 0))
        val out = applyTavernChatUpdates(c, """[{"message_id":0,"is_system":true,"name":"Chara"}]""")!!
        assertTrue(out.messageNodes[0].messages[0].isHidden)
        assertEquals("Chara", out.messageNodes[0].messages[0].name)

        val out2 = applyTavernChatUpdates(out, """[{"message_id":0,"is_system":false}]""")!!
        assertFalse(out2.messageNodes[0].messages[0].isHidden)
    }

    @Test
    fun applyNoChangeReturnsNull() {
        val c = conv(node("a", select = 0))
        assertNull(applyTavernChatUpdates(c, """[{"message_id":0,"mes":"a"}]"""))
        assertNull(applyTavernChatUpdates(c, "[]"))
        assertNull(applyTavernChatUpdates(c, "not json"))
        assertNull(applyTavernChatUpdates(c, """[{"message_id":99,"mes":"x"}]"""))
    }

    @Test
    fun applyMultiFloorUpdates() {
        val c = conv(
            node("u1", role = MessageRole.USER),
            node("a1", "a2", select = 0),
        )
        val out = applyTavernChatUpdates(
            c,
            """[{"message_id":0,"name":"user"},{"message_id":1,"swipe_id":1,"mes":"A2!"}]""",
        )!!
        assertEquals("user", out.messageNodes[0].messages[0].name)
        assertEquals(1, out.messageNodes[1].selectIndex)
        assertEquals("A2!", out.messageNodes[1].messages[1].toText())
    }
}