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
 * - build：ST 原生字段（swipes / swipe_id / mes / is_system / variables / swipe_info）；
 * - apply：JS → Kotlin 回写（swipe 切换 / 文本写入 / 变体重建 / 隐藏标记 / 名称 / 无变化）。
 */
class TavernChatJsonTest {

    private fun msg(role: MessageRole, text: String, name: String? = null) =
        UIMessage(role = role, parts = listOf(UIMessagePart.Text(text)), name = name)

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
        val arr = Json.parseToJsonElement(buildTavernChatJson(c)).jsonArray
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
        assertEquals(3, first["swipe_info"]!!.jsonArray.size)
        assertEquals(3, first["variables"]!!.jsonArray.size)
        assertFalse(first["is_system"]!!.jsonPrimitive.boolean)

        val second = arr[1].jsonObject
        assertTrue(second["is_user"]!!.jsonPrimitive.boolean)
        assertEquals("user", second["name"]!!.jsonPrimitive.content)
        assertEquals(1, second["swipes"]!!.jsonArray.size)
    }

    @Test
    fun buildEmbedsMessageVariables() {
        val vars = Json.parseToJsonElement("""[[{"hp":5}],[{"x":1},{"x":2}]]""")
        val c = conv(node("a"), node("b", "c", select = 1))
        val arr = Json.parseToJsonElement(buildTavernChatJson(c, vars)).jsonArray
        assertEquals(5, arr[0].jsonObject["variables"]!!.jsonArray[0].jsonObject["hp"]!!.jsonPrimitive.int)
        assertEquals(2, arr[1].jsonObject["variables"]!!.jsonArray.size)
    }

    @Test
    fun buildHiddenFlag() {
        val hidden = msg(MessageRole.ASSISTANT, "secret").withHidden()
        val c = conv(MessageNode(messages = listOf(hidden), selectIndex = 0))
        val arr = Json.parseToJsonElement(buildTavernChatJson(c)).jsonArray
        assertTrue(arr[0].jsonObject["is_system"]!!.jsonPrimitive.boolean)
    }

    // ------------------------------------------------------------
    // apply
    // ------------------------------------------------------------

    @Test
    fun applySwipeIdSwitch() {
        val c = conv(node("a", "b", "c", select = 0))
        val out = applyTavernChatUpdates(c, """[{"index":0,"swipe_id":2}]""")!!
        assertEquals(2, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applyMesWritesCurrentVariant() {
        val c = conv(node("a", "b", "c", select = 1))
        val out = applyTavernChatUpdates(c, """[{"index":0,"mes":"B2"}]""")!!
        assertEquals(listOf("a", "B2", "c"), out.messageNodes[0].messages.map { it.toText() })
    }

    @Test
    fun applySwipesRebuildsVariants() {
        val c = conv(node("a", "b", select = 1))
        val out = applyTavernChatUpdates(c, """[{"index":0,"swipes":["x","y","z"]}]""")!!
        assertEquals(listOf("x", "y", "z"), out.messageNodes[0].messages.map { it.toText() })
        assertEquals(1, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applySwipesShrinkClampsSelect() {
        val c = conv(node("a", "b", "c", select = 2))
        val out = applyTavernChatUpdates(c, """[{"index":0,"swipes":["only"]}]""")!!
        assertEquals(listOf("only"), out.messageNodes[0].messages.map { it.toText() })
        assertEquals(0, out.messageNodes[0].selectIndex)
    }

    @Test
    fun applyHiddenAndName() {
        val c = conv(node("a", select = 0))
        val out = applyTavernChatUpdates(c, """[{"index":0,"is_system":true,"name":"Chara"}]""")!!
        assertTrue(out.messageNodes[0].messages[0].isHidden)
        assertEquals("Chara", out.messageNodes[0].messages[0].name)

        val out2 = applyTavernChatUpdates(out, """[{"index":0,"is_system":false}]""")!!
        assertFalse(out2.messageNodes[0].messages[0].isHidden)
    }

    @Test
    fun applyNoChangeReturnsNull() {
        val c = conv(node("a", select = 0))
        assertNull(applyTavernChatUpdates(c, """[{"index":0,"mes":"a"}]"""))
        assertNull(applyTavernChatUpdates(c, "[]"))
        assertNull(applyTavernChatUpdates(c, "not json"))
        assertNull(applyTavernChatUpdates(c, """[{"index":99,"mes":"x"}]"""))
    }

    @Test
    fun applyMultiFloorUpdates() {
        val c = conv(
            node("u1", role = MessageRole.USER),
            node("a1", "a2", select = 0),
        )
        val out = applyTavernChatUpdates(
            c,
            """[{"index":0,"name":"user"},{"index":1,"swipe_id":1,"mes":"A2!"}]""",
        )!!
        assertEquals("user", out.messageNodes[0].messages[0].name)
        assertEquals(1, out.messageNodes[1].selectIndex)
        assertEquals("A2!", out.messageNodes[1].messages[1].toText())
    }
}