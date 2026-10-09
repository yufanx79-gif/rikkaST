package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic
import me.rerere.rikkahub.data.model.TavernBookEntry
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.model.TavernEmbeddedBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6 三源脚本合并 + 格式解析/写回 单测（纯 JVM，无 Android 依赖）。
 *
 * 覆盖对象：TavernScripts.kt（JSR global/preset/character 三来源语义）。
 * 注意：Settings 构造显式传入 themeId/providers/assistants/ttsProviders，
 * 避免在 JVM 单测中触发 Compose 主题等默认值链路。
 */
class TavernScriptsTest {

    private val json = Json

    // ---------- helpers ----------

    private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    private fun scriptsOf(merged: String): JsonArray =
        obj(merged)["tavern_helper"]!!.jsonObject["scripts"]!!.jsonArray

    private fun scriptObj(id: String, enabled: Boolean? = null, name: String = id): JsonObject =
        buildJsonObject {
            put("id", id)
            put("name", name)
            put("type", "script")
            if (enabled != null) put("enabled", enabled)
        }

    private fun testSettings(
        modeInjections: List<PromptInjection.ModeInjection> = emptyList(),
        tavernGlobalScripts: String = "[]",
        tavernGlobalScriptsEnabled: Boolean = true,
        tavernPresetScripts: String = "{}",
    ): Settings = Settings(
        themeId = "sakura",
        providers = emptyList(),
        assistants = emptyList(),
        ttsProviders = emptyList(),
        modeInjections = modeInjections,
        tavernGlobalScripts = tavernGlobalScripts,
        tavernGlobalScriptsEnabled = tavernGlobalScriptsEnabled,
        tavernPresetScripts = tavernPresetScripts,
    )

    // ---------- extractScriptsFromJson ----------

    @Test
    fun extractScriptsFromJsonParsesArrayAndWrapperFormats() {
        assertEquals(1, extractScriptsFromJson("""[{"id":"a"}]""").size)

        val byScripts = extractScriptsFromJson("""{"scripts":[{"id":"b"}]}""")
        assertEquals(1, byScripts.size)
        assertEquals("b", (byScripts[0] as JsonObject)["id"]?.jsonPrimitive?.content)

        val byHelper = extractScriptsFromJson("""{"tavern_helper":{"scripts":[{"id":"c"}]}}""")
        assertEquals(1, byHelper.size)
        assertEquals("c", (byHelper[0] as JsonObject)["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun extractScriptsFromJsonParsesSingleObjectAndRejectsGarbage() {
        val single = extractScriptsFromJson("""{"id":"d","type":"script","name":"n"}""")
        assertEquals(1, single.size)

        assertTrue(extractScriptsFromJson("not a json").isEmpty())
        assertTrue(extractScriptsFromJson("").isEmpty())
        assertTrue(extractScriptsFromJson("""{"foo":1}""").isEmpty())
    }

    // ---------- appendGlobalScripts / removeGlobalScript ----------

    @Test
    fun appendGlobalScriptsDeduplicatesById() {
        val (json1, added1) = appendGlobalScripts(
            "[]",
            listOf(scriptObj("a"), scriptObj("b")),
        )
        assertEquals(2, added1)
        assertEquals(2, json.parseToJsonElement(json1).jsonArray.size)

        // 已存在的 id 不重复添加；非对象元素跳过
        val (json2, added2) = appendGlobalScripts(
            """[{"id":"a","type":"script"}]""",
            listOf(scriptObj("a"), scriptObj("c"), JsonPrimitive("bad")),
        )
        assertEquals(1, added2)
        assertEquals(2, json.parseToJsonElement(json2).jsonArray.size)
    }

    @Test
    fun removeGlobalScriptRemovesByIdOnly() {
        val removed = removeGlobalScript(
            """[{"id":"a","type":"script"},{"id":"b","type":"script"}]""",
            "a",
        )
        assertNotNull(removed)
        val list = json.parseToJsonElement(removed!!).jsonArray
        assertEquals(1, list.size)
        assertEquals("b", list[0].jsonObject["id"]?.jsonPrimitive?.content)

        assertNull(removeGlobalScript("[]", "a"))
    }

    // ---------- 开关写回 ----------

    @Test
    fun setGlobalScriptEnabledTogglesFlatAndNestedItems() {
        val flat = setGlobalScriptEnabled("""[{"id":"a","type":"script","enabled":true}]""", "a", false)
        assertNotNull(flat)
        val flatItem = json.parseToJsonElement(flat!!).jsonArray[0].jsonObject
        assertEquals(JsonPrimitive(false), flatItem["enabled"])

        // folder 内脚本递归更新
        val folder = setGlobalScriptEnabled(
            """[{"type":"folder","scripts":[{"id":"a","type":"script","enabled":true}]}]""",
            "a",
            false,
        )
        assertNotNull(folder)
        val inner = json.parseToJsonElement(folder!!).jsonArray[0].jsonObject["scripts"]!!.jsonArray[0].jsonObject
        assertEquals(JsonPrimitive(false), inner["enabled"])

        assertNull(setGlobalScriptEnabled("""[{"id":"a"}]""", "zzz", false))
    }

    @Test
    fun setPresetScriptEnabledTogglesWithinGroup() {
        val input = """{"组A":[{"id":"p1","type":"script","enabled":true}]}"""
        val out = setPresetScriptEnabled(input, "组A", "p1", false)
        assertNotNull(out)
        val item = obj(out!!)["组A"]!!.jsonArray[0].jsonObject
        assertEquals(JsonPrimitive(false), item["enabled"])

        assertNull(setPresetScriptEnabled(input, "组X", "p1", false))
    }

    @Test
    fun setCardScriptEnabledWritesBackPreservingVariables() {
        val raw = """{"regex_scripts":[],"tavern_helper":{"scripts":[{"id":"s1","type":"script","enabled":true}],"variables":{"hp":5}}}"""
        val out = setCardScriptEnabled(raw, "s1", false)
        assertNotNull(out)
        val helper = obj(out!!)["tavern_helper"]!!.jsonObject
        assertEquals(JsonPrimitive(false), helper["scripts"]!!.jsonArray[0].jsonObject["enabled"])
        assertEquals(JsonPrimitive(5), helper["variables"]!!.jsonObject["hp"])

        assertNull(setCardScriptEnabled("garbage", "s1", false))
        assertNull(setCardScriptEnabled(raw, "zzz", false))
    }

    // ---------- 列表 ----------

    @Test
    fun listGlobalScriptsFlattensFoldersAndDefaultsEnabledTrue() {
        val items = listGlobalScripts(
            """[{"id":"a","name":"A","type":"script","content":"ccc","info":"ii"},""" +
                """{"id":"b","type":"script","enabled":false},""" +
                """{"type":"folder","scripts":[{"id":"c","type":"script","enabled":true}]}]""",
        )
        assertEquals(3, items.size)
        assertEquals("A", items[0].name)
        assertTrue(items[0].enabled)
        assertEquals("ccc", items[0].content)
        assertEquals("ii", items[0].info)
        assertEquals("script", items[0].type)
        assertFalse(items[1].enabled)
        assertEquals("c", items[2].id)
    }

    @Test
    fun listPresetScriptBucketsSupportsArrayAndObjectBuckets() {
        val buckets = listPresetScriptBuckets(
            """{"组A":[{"id":"p1","type":"script"}],""" +
                """"组B":{"scripts":[{"id":"p2","type":"script"},{"id":"p3","type":"script"}]},""" +
                """"组C":[]}""",
        )
        assertEquals(3, buckets.size)
        assertEquals(1, buckets["组A"]!!.size)
        assertEquals(2, buckets["组B"]!!.size)
        assertEquals(0, buckets["组C"]!!.size)
    }

    @Test
    fun mergePresetScriptsReplacesSameGroupAndKeepsOthers() {
        var merged = mergePresetScripts(null, "组A", listOf(scriptObj("p1")))
        merged = mergePresetScripts(merged, "组B", listOf(scriptObj("p2")))
        merged = mergePresetScripts(merged, "组A", listOf(scriptObj("p1b"), scriptObj("p1c")))

        val root = obj(merged)
        assertEquals(2, root["组A"]!!.jsonArray.size)
        assertEquals(1, root["组B"]!!.jsonArray.size)
        assertEquals("p1c", root["组A"]!!.jsonArray[1].jsonObject["id"]?.jsonPrimitive?.content)
    }

    // ---------- 启用组推导与三源合并 ----------

    @Test
    fun enabledPresetGroupsDerivesFromModeInjectionIds() {
        val injA = PromptInjection.ModeInjection(name = "a", presetGroup = "组A")
        val injB = PromptInjection.ModeInjection(name = "b", presetGroup = "组B")
        val injUnbound = PromptInjection.ModeInjection(name = "c", presetGroup = null)
        val settings = testSettings(modeInjections = listOf(injA, injB, injUnbound))
        val assistant = Assistant(modeInjectionIds = setOf(injA.id, injUnbound.id))

        assertEquals(setOf("组A"), enabledPresetGroups(assistant, settings))
        assertTrue(enabledPresetGroups(null, settings).isEmpty())
        assertTrue(enabledPresetGroups(assistant, null).isEmpty())
    }

    @Test
    fun buildTavernScriptsJsonMergesThreeSourcesInOrder() {
        val injA = PromptInjection.ModeInjection(name = "a", presetGroup = "组A")
        val injB = PromptInjection.ModeInjection(name = "b", presetGroup = "组B")
        val settings = testSettings(
            modeInjections = listOf(injA, injB),
            tavernGlobalScripts = """[{"id":"g1","type":"script","name":"G1"}]""",
            tavernPresetScripts = """{"组A":[{"id":"p1","type":"script","name":"P1"}],"组B":[{"id":"p2","type":"script","name":"P2"}]}""",
        )
        val assistant = Assistant(
            modeInjectionIds = setOf(injA.id),
            tavernData = TavernCharacterData(
                name = "卡",
                extensionsRaw = """{"tavern_helper":{"scripts":[{"id":"c1","type":"script","name":"C1"}],"variables":{"hp":5}}}""",
            ),
        )

        val helper = obj(buildTavernScriptsJson(assistant, settings))["tavern_helper"]!!.jsonObject
        val ids = helper["scripts"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        // 顺序：global → card → preset（仅启用的组A）
        assertEquals(listOf("g1", "c1", "p1"), ids)
        assertEquals(JsonPrimitive(5), helper["variables"]!!.jsonObject["hp"])
    }

    @Test
    fun buildTavernScriptsJsonRespectsGlobalToggleAndNullInputs() {
        val settings = testSettings(
            tavernGlobalScripts = """[{"id":"g1","type":"script"}]""",
            tavernGlobalScriptsEnabled = false,
        )
        assertEquals(0, scriptsOf(buildTavernScriptsJson(Assistant(), settings)).size)

        val empty = obj(buildTavernScriptsJson(null, null))["tavern_helper"]!!.jsonObject
        assertEquals(0, empty["scripts"]!!.jsonArray.size)
        assertEquals(0, empty["variables"]!!.jsonObject.size)
    }

    // ---------- buildCharacterJson（v204：ST-PT getCharacterDefine() 的 .trim() 崩溃根因） ----------

    private fun testAssistant(
        name: String = "阿库娅",
        tav: TavernCharacterData = TavernCharacterData(),
    ): Assistant = Assistant(name = name, tavernData = tav)

    @Test
    fun buildCharacterJsonEmitsFullStV1CharDataShape() {
        val assistant = testAssistant(
            tav = TavernCharacterData(
                spec = "chara_card_v3",
                specVersion = "3.0",
                name = "阿库娅",
                description = "水之女神",
                personality = "自大又善良",
                scenario = "阿克塞尔酒馆",
                firstMessage = "你好，我是阿库娅！",
                alternateGreetings = listOf("备选开场白"),
                mesExample = "<START>\n{{user}}: 你好\n{{char}}: 你好呀",
                systemPrompt = "你是阿库娅",
                creator = "作者",
                creatorNotes = "作者备注",
                characterVersion = "1.2",
                tags = listOf("女神", "搞笑"),
                postHistoryInstructions = "保持角色",
                depthPrompt = "深度提示",
                extensionsRaw = """{"world":"我的世界书","tavern_helper":{"scripts":[]}}""",
            ),
        )

        val root = obj(buildCharacterJson(assistant))

        // ① 顶层：ST-PT getCharacterDefine() 直接读取、或 .trim()/.replace() 的字段必须存在且为 string
        assertEquals("阿库娅", root["name"]!!.jsonPrimitive.content)
        assertEquals("水之女神", root["description"]!!.jsonPrimitive.content)
        assertEquals("自大又善良", root["personality"]!!.jsonPrimitive.content)
        assertEquals("阿克塞尔酒馆", root["scenario"]!!.jsonPrimitive.content)
        assertEquals("你好，我是阿库娅！", root["first_mes"]!!.jsonPrimitive.content)
        assertEquals("<START>\n{{user}}: 你好\n{{char}}: 你好呀", root["mes_example"]!!.jsonPrimitive.content)
        assertEquals("作者备注", root["creatorcomment"]!!.jsonPrimitive.content)
        assertTrue(root["avatar"]!!.jsonPrimitive.content.endsWith(".png"))
        assertTrue(root["tags"] is JsonArray)
        assertEquals("chara_card_v3", root["spec"]!!.jsonPrimitive.content)

        // ② data：ST 真源的 data 子对象（JSR 读 data.extensions.world / data.alternate_greetings）
        val data = root["data"]!!.jsonObject
        val ext = data["extensions"]!!.jsonObject
        assertEquals("我的世界书", ext["world"]!!.jsonPrimitive.content)
        assertNotNull(ext["tavern_helper"]) // 卡内脚本不能被丢
        assertEquals("你是阿库娅", data["system_prompt"]!!.jsonPrimitive.content)
        assertEquals("保持角色", data["post_history_instructions"]!!.jsonPrimitive.content)
        assertEquals("备选开场白", data["alternate_greetings"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("作者", data["creator"]!!.jsonPrimitive.content)
        assertEquals("1.2", data["character_version"]!!.jsonPrimitive.content)
        assertEquals("深度提示", data["depth_prompt"]!!.jsonObject["prompt"]!!.jsonPrimitive.content)
        assertEquals("4", data["depth_prompt"]!!.jsonObject["depth"]!!.jsonPrimitive.content)
        assertEquals("system", data["depth_prompt"]!!.jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildCharacterJsonKeepsEveryStFieldAsSafeTypeWhenCardIsBlank() {
        val root = obj(buildCharacterJson(testAssistant(tav = TavernCharacterData())))
        val topStrings = listOf(
            "name", "avatar", "description", "personality", "scenario", "first_mes",
            "mes_example", "creatorcomment", "chat", "talkativeness", "spec", "spec_version",
        )
        for (k in topStrings) {
            val v = root[k]
            assertTrue("顶层 $k 必须是 string（扩展会 .trim()/.replace() 它），实为 $v", v is JsonPrimitive && v.isString)
        }
        val data = root["data"]!!.jsonObject
        val dataStrings = listOf(
            "name", "description", "personality", "scenario", "first_mes", "mes_example",
            "creator_notes", "system_prompt", "post_history_instructions", "creator", "character_version",
        )
        for (k in dataStrings) {
            val v = data[k]
            assertTrue("data.$k 必须是 string，实为 $v", v is JsonPrimitive && v.isString)
        }
        assertTrue(data["alternate_greetings"] is JsonArray)
        assertTrue(data["group_only_greetings"] is JsonArray)
        assertTrue(data["tags"] is JsonArray)
        assertTrue(data["extensions"] is JsonObject)
        assertEquals(JsonNull, data["character_book"])
    }

    @Test
    fun buildCharacterJsonKeepsWorldAndFallsBackToAssistantName() {
        val root = obj(
            buildCharacterJson(
                Assistant(
                    name = "助手名",
                    tavernData = TavernCharacterData(extensionsRaw = """{"world":"世界书 A"}"""),
                ),
            ),
        )
        assertEquals("世界书 A", root["data"]!!.jsonObject["extensions"]!!.jsonObject["world"]!!.jsonPrimitive.content)

        // 卡名为空 → 回退到助手名（两层都要回退）
        val fallback = obj(buildCharacterJson(Assistant(name = "助手名", tavernData = TavernCharacterData(extensionsRaw = "{}"))))
        assertEquals("助手名", fallback["name"]!!.jsonPrimitive.content)
        assertEquals("助手名", fallback["data"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertNull(fallback["data"]!!.jsonObject["extensions"]!!.jsonObject["world"])
    }

    @Test
    fun buildCharacterJsonReturnsEmptyObjectWithoutCard() {
        assertEquals("{}", buildCharacterJson(null))
        assertEquals("{}", buildCharacterJson(Assistant(name = "无卡助手")))
    }

    // ---------- [v214] 全局世界书读面 + 写回补丁 ----------

    private fun testGlobalLorebook(): Lorebook = Lorebook(
        name = "全局书A",
        entries = listOf(
            PromptInjection.RegexInjection(
                name = "条目一",
                enabled = true,
                content = "内容一",
                keywords = listOf("k1", "k2"),
                secondaryKeys = listOf("s1"),
                constantActive = true,
                selective = true,
                selectiveLogic = SelectiveLogic.NOT_ANY,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 3,
                priority = 42,
                probability = 77,
                role = MessageRole.USER,
                scanDepth = 5,
                caseSensitive = true,
                matchWholeWords = true,
                group = "g1",
                groupWeight = 60,
                groupOverride = true,
                sticky = 2,
                cooldown = 3,
                delay = 4,
                excludeRecursion = true,
                preventRecursion = true,
                delayUntilRecursion = 2,
                useProbability = true,
                useGroupScoring = true,
                automationId = "auto1",
                displayIndex = 7,
                triggers = listOf("t1"),
                ignoreBudget = true,
                outletName = "out1",
            ),
        ),
    )

    @Test
    fun lorebookToStJsonMapsFieldsAndKeepsStEntryShape() {
        val root = obj(lorebookToStJson(testGlobalLorebook()))
        assertEquals("全局书A", root["name"]!!.jsonPrimitive.content)
        val entries = root["entries"]!!.jsonObject
        assertEquals(1, entries.size)
        val e = entries["0"]!!.jsonObject
        // 与 entryToStJson 同形的 41 键（一个键都不能少）
        assertEquals(41, e.size)
        assertEquals(0, e["uid"]!!.jsonPrimitive.int)
        assertEquals("条目一", e["comment"]!!.jsonPrimitive.content)
        assertEquals("内容一", e["content"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(false), e["disable"]) // enabled=true → disable=false
        assertEquals(JsonPrimitive(true), e["constant"])
        assertEquals(JsonPrimitive(true), e["selective"])
        assertEquals(2, e["selectiveLogic"]!!.jsonPrimitive.int) // NOT_ANY → 2
        assertEquals(4, e["position"]!!.jsonPrimitive.int)       // AT_DEPTH → 4
        assertEquals(1, e["role"]!!.jsonPrimitive.int)           // USER → 1
        assertEquals(3, e["depth"]!!.jsonPrimitive.int)
        assertEquals(42, e["order"]!!.jsonPrimitive.int)
        assertEquals(77, e["probability"]!!.jsonPrimitive.int)
        assertEquals(5, e["scanDepth"]!!.jsonPrimitive.int)
        assertEquals(listOf("k1", "k2"), e["key"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("s1"), e["keysecondary"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("g1", e["group"]!!.jsonPrimitive.content)
        assertEquals("out1", e["outletName"]!!.jsonPrimitive.content)
    }

    @Test
    fun lorebookToStJsonInvertsEnabledIntoDisable() {
        val book = testGlobalLorebook().let { b ->
            b.copy(entries = listOf(b.entries[0].copy(enabled = false)))
        }
        val e = obj(lorebookToStJson(book))["entries"]!!.jsonObject["0"]!!.jsonObject
        assertEquals(JsonPrimitive(true), e["disable"])
    }

    @Test
    fun buildLorebookJsonFallsBackToGlobalLorebooks() {
        val settings = testSettings().copy(lorebooks = listOf(testGlobalLorebook()))
        val root = obj(buildLorebookJson(null, "全局书A", settings))
        assertEquals("全局书A", root["name"]!!.jsonPrimitive.content)
        assertEquals(1, root["entries"]!!.jsonObject.size)
        // 未命中 → "null"
        assertEquals("null", buildLorebookJson(null, "不存在的书", settings))
        // 空名 → "null"
        assertEquals("null", buildLorebookJson(null, "", settings))
    }

    @Test
    fun buildLorebookJsonPrefersEmbeddedBookOverGlobal() {
        val embedded = TavernEmbeddedBook(
            name = "同名书",
            entries = listOf(TavernBookEntry(id = 1, comment = "卡内", content = "c")),
        )
        val settings = testSettings().copy(lorebooks = listOf(testGlobalLorebook()))
        val assistant = Assistant(
            tavernData = TavernCharacterData(embeddedBook = embedded),
        )
        val root = obj(buildLorebookJson(assistant, "同名书", settings))
        assertEquals("同名书", root["name"]!!.jsonPrimitive.content)
        val entries = root["entries"]!!.jsonObject
        assertEquals("卡内", entries["1"]!!.jsonObject["comment"]!!.jsonPrimitive.content)
    }

    @Test
    fun applyLorebookDisablePatchEmbeddedHitMissAndEmpty() {
        val book = TavernEmbeddedBook(
            name = "卡内书",
            entries = listOf(
                TavernBookEntry(id = 1, comment = "a", disable = false),
                TavernBookEntry(id = 2, comment = "b", disable = false),
            ),
        )
        val patched = applyLorebookDisablePatch(book, """[{"uid":2,"disable":true}]""")
        assertNotNull(patched)
        assertEquals(false, patched!!.entries[0].disable)
        assertEquals(true, patched.entries[1].disable)

        // uid 不存在 / 空补丁 / 非法 JSON / 状态未变 → null
        assertNull(applyLorebookDisablePatch(book, """[{"uid":99,"disable":true}]"""))
        assertNull(applyLorebookDisablePatch(book, "[]"))
        assertNull(applyLorebookDisablePatch(book, ""))
        assertNull(applyLorebookDisablePatch(book, "not json"))
        assertNull(applyLorebookDisablePatch(book, """[{"uid":1,"disable":false}]"""))
    }

    @Test
    fun applyLorebookDisablePatchGlobalHitMissAndEmpty() {
        val book = testGlobalLorebook()
        val patched = applyLorebookDisablePatch(book, """[{"uid":0,"disable":true}]""")
        assertNotNull(patched)
        assertEquals(false, patched!!.entries[0].enabled) // enabled = !disable

        assertNull(applyLorebookDisablePatch(book, """[{"uid":9,"disable":true}]"""))
        assertNull(applyLorebookDisablePatch(book, "[]"))
        assertNull(applyLorebookDisablePatch(book, "garbage"))
        // 状态未变 → null（原 enabled=true，disable=false）
        assertNull(applyLorebookDisablePatch(book, """[{"uid":0,"disable":false}]"""))
    }

    // ---------- [v216] 世界书书名归一化（千纱卡开关面板 · 存量书名尾随空格） ----------

    @Test
    fun lorebookNameMatchesTrimsBothSidesButKeepsBlankAsMiss() {
        assertTrue(lorebookNameMatches("全局书A", "全局书A"))
        assertTrue(lorebookNameMatches("全局书A ", "全局书A"))   // 存量脏名（尾随空格）
        assertTrue(lorebookNameMatches(" 全局书A", "全局书A "))  // 双侧 trim 兜底
        assertFalse(lorebookNameMatches("全局书A", "全局书B"))
        assertFalse(lorebookNameMatches(null, "全局书A"))
        assertFalse(lorebookNameMatches("   ", "全局书A"))
        assertFalse(lorebookNameMatches("全局书A", ""))
    }

    @Test
    fun findLorebookIndexByNamePrefersExactThenTrimFallback() {
        val dirty = testGlobalLorebook().copy(name = "全局书A ")
        val exact = testGlobalLorebook().copy(name = "全局书A")
        val books = listOf(dirty, exact)
        // 精确优先：请求 "全局书A" 必须命中干净那本（下标 1），而不是靠前但带空格的 dirty（下标 0）
        assertEquals(1, findLorebookIndexByName(books, "全局书A"))
        assertEquals(0, findLorebookIndexByName(books, "全局书A "))
        assertEquals(-1, findLorebookIndexByName(books, "不存在的书"))
    }

    @Test
    fun buildLorebookJsonMatchesGlobalBookNameWithTrailingSpace() {
        val settings = testSettings().copy(lorebooks = listOf(testGlobalLorebook().copy(name = "全局书A ")))
        // 请求名已 trim（JS getWorldbook 行为）→ 必须命中带尾随空格的存量书
        val root = obj(buildLorebookJson(null, "全局书A", settings))
        assertEquals("全局书A ", root["name"]!!.jsonPrimitive.content) // 下发真实存量 name
        assertEquals(1, root["entries"]!!.jsonObject.size)
        // 原始带空格名同样命中（ST-PT 直接 loadWorldInfo 的路径）
        assertEquals(1, obj(buildLorebookJson(null, "全局书A ", settings))["entries"]!!.jsonObject.size)
        // 纯空白名仍 "null"
        assertEquals("null", buildLorebookJson(null, "  ", settings))
    }

    @Test
    fun buildLorebookJsonReportsTrimFallbackOnlyWhenNeeded() {
        val settings = testSettings().copy(lorebooks = listOf(testGlobalLorebook().copy(name = "全局书A ")))
        val hits = mutableListOf<Pair<String, String>>()
        buildLorebookJson(null, "全局书A", settings) { req, stored -> hits.add(req to stored) }
        assertEquals(listOf("全局书A" to "全局书A "), hits)
        // 精确命中（请求名本身就是存量原名）→ 不触发回调
        val hits2 = mutableListOf<Pair<String, String>>()
        buildLorebookJson(null, "全局书A ", settings) { req, stored -> hits2.add(req to stored) }
        assertTrue(hits2.isEmpty())
    }

    @Test
    fun buildWorldNamesJsonDropsUnresolvableButKeepsDirtyResolvable() {
        val dirty = testGlobalLorebook().copy(name = "带空格的世界书 ", enabled = true)
        val clean = testGlobalLorebook().copy(name = "干净的世界书", enabled = true)
        val disabled = testGlobalLorebook().copy(name = "停用的世界书", enabled = false)
        val settings = testSettings().copy(lorebooks = listOf(dirty, clean, disabled))
        // 卡 extensions.world 指向一本不存在的书 → 不可解析，必须被过滤（否则卡侧 Promise.all 整体报废）
        val assistant = Assistant(
            tavernData = TavernCharacterData(extensionsRaw = """{"world":"不存在的书"}"""),
        )
        val root = obj(buildWorldNamesJson(settings, assistant))
        val names = root["world_names"]!!.jsonArray.map { it.jsonPrimitive.content }
        val selected = root["selected"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("带空格的世界书 ", "干净的世界书", "停用的世界书"), names)
        assertEquals(listOf("带空格的世界书 ", "干净的世界书"), selected)
        assertFalse(names.contains("不存在的书"))
        // 不变式（scout-worldbook §6.3）：下发的每个名字都必须能被 buildLorebookJson 命中
        // —— 卡侧 M() 用 Promise.all 无 per-book 容错，任何一个不可解析的名字都会让整个开关面板报废。
        (names + selected).forEach { n ->
            assertTrue("getter 下发的名字必须可解析: $n", buildLorebookJson(assistant, n, settings) != "null")
        }
    }
}
