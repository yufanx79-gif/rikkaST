package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.mergeActiveRegexScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v247] B 组（角色卡 / 人设）+ D 组（正则）数据源回归测试。
 *
 * 背景：此前 `state.characters` 只有当前一张卡、`power_user.personas` 恒为 `{}`、
 * `globalThis.__rikkahub_regex_scripts__` 从没人写。结果是 JSR 的
 * `getCharacterNames()` / `getPersona()` / `getTavernRegexes()` 全部返回空，
 * 脚本里所有「按名字查卡」「读人设」「跑正则」的分支静默走空路径。
 *
 * 本测试锁定三个构建器的输出契约（字段名必须与 ST 真源逐字一致）：
 *  - [buildTavernCharactersJson] → `characters[]`（v1CharData 形状，当前卡在首位）
 *  - [buildTavernPersonasJson]  → `power_user` 双表（personas + persona_descriptions）
 *  - [buildTavernRegexScriptsJson] → `RegexScriptData[]`（ST char-data.js 形状）
 */
class TavernDataSourceTest {

    // ---------------------------------------------------------------- 构造器

    private fun card(name: String, desc: String = "", world: String = ""): Assistant = Assistant(
        id = Uuid.random(),
        name = name,
        tavernData = TavernCharacterData(
            name = name,
            description = desc,
            personality = "personality-$name",
            scenario = "scenario-$name",
            firstMessage = "hello-$name",
            mesExample = "example-$name",
            extensionsRaw = if (world.isBlank()) "{}" else """{"world":"$world"}""",
        ),
    )

    private fun persona(
        name: String,
        description: String = "",
        position: PersonaInjectionPosition = PersonaInjectionPosition.IN_PROMPT,
        depth: Int = 2,
        role: MessageRole = MessageRole.SYSTEM,
        enabled: Boolean = true,
        title: String = "",
    ): Persona = Persona(
        id = Uuid.random(),
        name = name,
        title = title,
        description = description,
        position = position,
        depth = depth,
        role = role,
        enabled = enabled,
    )

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    // ---------------------------------------------------------------- B 组：characters[]

    @Test
    fun charactersJsonPutsCurrentCardFirst() {
        val a = card("Alice")
        val b = card("Bob")
        val c = card("Carol")
        val settings = Settings(assistants = listOf(a, b, c))
        val arr = Json.parseToJsonElement(buildTavernCharactersJson(settings, c.id.toString())).jsonArray

        assertEquals(3, arr.size)
        // ST 语义：characters[this_chid] 就是当前角色；runtime 里 this_chid 恒为 0
        assertEquals("Carol", arr[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Alice", arr[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Bob", arr[2].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun charactersJsonEmitsEveryV1CharDataField() {
        val settings = Settings(assistants = listOf(card("Alice", "desc", "MyBook")))
        val first = Json.parseToJsonElement(buildTavernCharactersJson(settings, null)).jsonArray[0].jsonObject

        // 顶层：JSR 直接读这些（character.ts:63-69 / raw_character.ts:95-101 会 .toLowerCase()）
        listOf(
            "name", "avatar", "description", "personality", "scenario",
            "first_mes", "mes_example", "creatorcomment", "chat", "talkativeness",
            "fav", "tags", "spec", "spec_version", "data",
        ).forEach { key ->
            assertTrue("characters[0] 缺字段 $key", first.containsKey(key))
        }
        // data 层：ST-PT 的 getCharacterDefine() 直接 .trim()
        val data = first["data"]!!.jsonObject
        listOf(
            "name", "description", "personality", "scenario", "first_mes", "mes_example",
            "creator_notes", "system_prompt", "post_history_instructions",
            "alternate_greetings", "character_book", "tags", "creator",
            "character_version", "extensions", "group_only_greetings", "depth_prompt",
        ).forEach { key ->
            assertTrue("characters[0].data 缺字段 $key", data.containsKey(key))
        }
        // 世界书绑定落在 data.extensions.world（JSR getCharWorldbookNames 读这里）
        val ext = data["extensions"]!!.jsonObject
        assertEquals("MyBook", ext["world"]!!.jsonPrimitive.content)
        // avatar 必须是字符串且以 .png 结尾：JSR 拿它当 per-character store 的键并 .replace()
        val avatar = first["avatar"]!!.jsonPrimitive.content
        assertTrue("avatar 应为 *.png，实际 $avatar", avatar.endsWith(".png"))
    }

    @Test
    fun charactersJsonReturnsEmptyArrayWhenNoAssistants() {
        assertEquals("[]", buildTavernCharactersJson(null, null))
        assertEquals("[]", buildTavernCharactersJson(Settings(assistants = emptyList()), null))
        // 必须是数组而不是对象：JSR 直接 characters.map(...)
        assertTrue(buildTavernCharactersJson(Settings(assistants = emptyList()), null).startsWith("["))
    }

    // ---------------------------------------------------------------- B 组：personas

    @Test
    fun personasJsonEmitsPowerUserDoubleTable() {
        val p1 = persona("User A", "desc A", PersonaInjectionPosition.AT_DEPTH, 4, MessageRole.USER, title = "T1")
        val p2 = persona("User B", "desc B")
        val settings = Settings(personas = listOf(p1, p2), activePersonaId = p1.id)
        val root = obj(buildTavernPersonasJson(settings))

        // JSR getPersonaIds() = Object.keys(power_user.personas)
        val personas = root["personas"]!!.jsonObject
        assertEquals(2, personas.size)
        assertEquals("User A", personas[p1.id.toString()]!!.jsonPrimitive.content)
        assertEquals("User B", personas[p2.id.toString()]!!.jsonPrimitive.content)

        // JSR toPersona() 展开 persona_descriptions[avatar_id]
        val descs = root["persona_descriptions"]!!.jsonObject
        val d1 = descs[p1.id.toString()]!!.jsonObject
        assertEquals("desc A", d1["description"]!!.jsonPrimitive.content)
        // ST persona_description_positions.AT_DEPTH = 4（personas.js:96）
        assertEquals(4, d1["position"]!!.jsonPrimitive.int)
        assertEquals(4, d1["depth"]!!.jsonPrimitive.int)
        // ST extension_prompt_roles.USER = 1（script.js:88）
        assertEquals(1, d1["role"]!!.jsonPrimitive.int)
        assertEquals("T1", d1["title"]!!.jsonPrimitive.content)
        // lorebook / connections 必须存在（JSR toPersona 会 klona(descriptor.connections)）
        assertTrue(d1.containsKey("lorebook"))
        assertTrue(d1["connections"] is JsonArray)

        // user_avatar = 当前人设（st-compat/personas.js getUserAvatar() 读 ctx.userAvatar）
        assertEquals(p1.id.toString(), root["user_avatar"]!!.jsonPrimitive.content)
        // user_name 用于 {{user}} 宏
        assertEquals("User A", root["user_name"]!!.jsonPrimitive.content)
        assertNotNull(root["default_persona"])
    }

    @Test
    fun personasJsonKeepsFullSkeletonWhenEmpty() {
        val root = obj(buildTavernPersonasJson(Settings(personas = emptyList(), activePersonaId = null)))
        // 缺 persona_descriptions 会让 JSR toPersona() 抛
        // "Cannot convert undefined or null to object"（persona.ts:112 展开运算）
        assertTrue(root.containsKey("personas"))
        assertTrue(root.containsKey("persona_descriptions"))
        assertTrue(root.containsKey("default_persona"))
        assertTrue(root.containsKey("user_avatar"))
        assertTrue(root["personas"]!!.jsonObject.isEmpty())
        assertTrue(root["persona_descriptions"]!!.jsonObject.isEmpty())
        assertEquals("null", buildTavernPersonasJson(null).let { obj(it)["default_persona"].toString() })
    }

    @Test
    fun personaPositionAndRoleMapToStNumbers() {
        // ST personas.js:88-98
        assertEquals(0, personaPositionToStNumber(PersonaInjectionPosition.IN_PROMPT))
        assertEquals(2, personaPositionToStNumber(PersonaInjectionPosition.TOP_OF_CHAT))
        assertEquals(3, personaPositionToStNumber(PersonaInjectionPosition.BOTTOM_OF_CHAT))
        assertEquals(4, personaPositionToStNumber(PersonaInjectionPosition.AT_DEPTH))
        assertEquals(9, personaPositionToStNumber(PersonaInjectionPosition.NONE))
        // ST script.js:87-91 extension_prompt_roles
        assertEquals(0, personaRoleToStNumber(MessageRole.SYSTEM))
        assertEquals(1, personaRoleToStNumber(MessageRole.USER))
        assertEquals(2, personaRoleToStNumber(MessageRole.ASSISTANT))
    }

    // ---------------------------------------------------------------- D 组：regex

    @Test
    fun regexJsonUsesStCamelCaseFieldNames() {
        val script = RegexScript(
            id = "r1",
            scriptName = "去括号",
            findRegex = "/\\((.+?)\\)/g",
            replaceString = "$1",
            trimStrings = listOf("a", "b"),
            placement = listOf(RegexPlacement.AI_OUTPUT, RegexPlacement.USER_INPUT),
            disabled = false,
            markdownOnly = true,
            promptOnly = false,
            runOnEdit = true,
            substituteRegex = 1,
            minDepth = 0,
            maxDepth = 5,
        )
        val first = Json.parseToJsonElement(buildTavernRegexScriptsJson(listOf(script))).jsonArray[0].jsonObject

        // 字段名必须与 st-compat/char-data.js 的 RegexScriptData 逐字一致
        assertEquals("r1", first["id"]!!.jsonPrimitive.content)
        assertEquals("去括号", first["scriptName"]!!.jsonPrimitive.content)
        assertEquals("/\\((.+?)\\)/g", first["findRegex"]!!.jsonPrimitive.content)
        assertEquals("$1", first["replaceString"]!!.jsonPrimitive.content)
        assertEquals(2, first["trimStrings"]!!.jsonArray.size)
        assertEquals(2, first["placement"]!!.jsonArray.size)
        assertEquals(false, first["disabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(true, first["markdownOnly"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(false, first["promptOnly"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(true, first["runOnEdit"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, first["substituteRegex"]!!.jsonPrimitive.int)
        assertEquals(0, first["minDepth"]!!.jsonPrimitive.int)
        assertEquals(5, first["maxDepth"]!!.jsonPrimitive.int)
    }

    @Test
    fun regexJsonUsesNullForUnboundedDepth() {
        val script = RegexScript(id = "r2", scriptName = "x", findRegex = "a", replaceString = "b")
        val first = Json.parseToJsonElement(buildTavernRegexScriptsJson(listOf(script))).jsonArray[0].jsonObject
        // ST 真源用 null 表示「不限深度」；JS 侧判定是 typeof === 'number'，
        // 若写成 0 会让 depth 过滤把深层消息全部误杀。
        assertEquals("null", first["minDepth"].toString())
        assertEquals("null", first["maxDepth"].toString())
        assertNull(first["minDepth"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun regexJsonIsArrayNotObject() {
        assertTrue(buildTavernRegexScriptsJson(emptyList()).startsWith("["))
        assertEquals("[]", buildTavernRegexScriptsJson(emptyList()))
    }

    @Test
    fun regexSourceMergesGlobalPresetAndCardInStOrder() {
        val injId = Uuid.random()
        val global = RegexScript(id = "g", scriptName = "全局", findRegex = "g", replaceString = "", placement = listOf(RegexPlacement.AI_OUTPUT))
        val presetRaw = """
            {"extensions":{"regex_scripts":[
              {"id":"p","scriptName":"预设","findRegex":"p","replaceString":"","placement":[2]}
            ]}}
        """.trimIndent()
        val card = Assistant(
            id = Uuid.random(),
            name = "card",
            modeInjectionIds = setOf(injId),
            tavernData = TavernCharacterData(
                embeddedRegexScripts = listOf(
                    RegexScript(id = "c", scriptName = "卡片", findRegex = "c", replaceString = "", placement = listOf(RegexPlacement.AI_OUTPUT))
                )
            ),
        )
        val settings = Settings(
            regexScripts = listOf(global),
            tavernPresets = JsonObject(mapOf("G" to JsonPrimitive(presetRaw))).toString(),
            modeInjections = listOf(
                PromptInjection.ModeInjection(id = injId, name = "e", enabled = true, priority = 0, content = "x", presetGroup = "G")
            ),
        )
        val merged = mergeActiveRegexScripts(settings, card)
        // ST getRegexScripts() 顺序：全局 → 预设 → 卡片
        assertEquals(listOf("g", "p", "c"), merged.map { it.id })
        // 序列化后仍保持该顺序
        val ids = Json.parseToJsonElement(buildTavernRegexScriptsJson(merged)).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("g", "p", "c"), ids)
    }

    @Test
    fun regexDisabledFlagSurvivesSerialization() {
        // engine.js 会 `if (script.disabled) continue;` —— 字段丢了就会把禁用脚本当启用跑
        val disabled = RegexScript(id = "d", scriptName = "disabled", findRegex = "x", replaceString = "y", disabled = true)
        val first = Json.parseToJsonElement(buildTavernRegexScriptsJson(listOf(disabled))).jsonArray[0].jsonObject
        assertEquals("true", first["disabled"].toString())
    }
}
