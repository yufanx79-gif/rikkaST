package me.rerere.rikkahub.data.export

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 酒馆预设 → 模式注入（ModeInjection）导入测试。
 *
 * 解析与映射逻辑移植自 RikkaRs（issue #188），目标模型为本项目
 * [me.rerere.rikkahub.data.model.PromptInjection.ModeInjection]。
 * 金标准：SillyTavern 1.18.0 PromptManager.js / openai.js 的 injection_position 语义。
 */
class SillyTavernPresetImportTest {

    private fun parse(json: String) =
        ModeInjectionSerializer.tryImportSillyTavernPreset(json, "测试预设")

    @Test
    fun fullPresetMapsOrderPositionDepthRoleAndEnabled() {
        val json = """
            {
              "prompts": [
                {"identifier":"main","name":"主提示词","role":"system","content":"MAIN 内容","enabled":true,"injection_position":0},
                {"identifier":"jailbreak","name":"越狱","role":"system","content":"JB 内容","enabled":true,"injection_position":0},
                {"identifier":"reminder","name":"深度提醒","role":"user","content":"REMIND 内容","enabled":true,"injection_position":1,"injection_depth":2},
                {"identifier":"disabled_entry","name":"被禁用的条目","role":"assistant","content":"OFF 内容","enabled":false,"injection_position":0}
              ],
              "prompt_order": [
                {"character_id":100001,"order":[
                  {"identifier":"main","enabled":true},
                  {"identifier":"jailbreak","enabled":true},
                  {"identifier":"reminder","enabled":true},
                  {"identifier":"disabled_entry","enabled":false}
                ]}
              ]
            }
        """.trimIndent()

        val list = parse(json)
        assertNotNull(list)
        assertEquals(4, list!!.size)
        assertEquals(listOf("主提示词", "越狱", "深度提醒", "被禁用的条目"), list.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), list.map { it.priority })
        assertEquals(
            listOf(
                InjectionPosition.AFTER_SYSTEM_PROMPT,
                InjectionPosition.AFTER_SYSTEM_PROMPT,
                InjectionPosition.AT_DEPTH,
                InjectionPosition.TOP_OF_CHAT,
            ),
            list.map { it.position },
        )
        assertEquals(2, list[2].injectDepth)
        assertEquals(
            listOf(MessageRole.SYSTEM, MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT),
            list.map { it.role },
        )
        assertEquals(listOf(true, true, true, false), list.map { it.enabled })
    }

    @Test
    fun promptOrderDecidesOrderAndEnabledAndPrefersDefaultGroup() {
        val json = """
            {
              "prompts": [
                {"identifier":"a","name":"A","role":"system","content":"AAA"},
                {"identifier":"b","name":"B","role":"system","content":"BBB"},
                {"identifier":"c","name":"C","role":"system","content":"CCC"}
              ],
              "prompt_order": [
                {"character_id":100000,"order":[
                  {"identifier":"a","enabled":true},
                  {"identifier":"b","enabled":true},
                  {"identifier":"c","enabled":true}
                ]},
                {"character_id":100001,"order":[
                  {"identifier":"b","enabled":true},
                  {"identifier":"a","enabled":false},
                  {"identifier":"c","enabled":true}
                ]}
              ]
            }
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(listOf("B", "A", "C"), list.map { it.name })
        assertEquals(listOf(true, false, true), list.map { it.enabled })
    }

    @Test
    fun missingPromptOrderFallsBackToPromptsOrderAndEnablesAll() {
        val json = """
            {"prompts":[
              {"identifier":"x","name":"X","role":"user","content":"XXX"},
              {"identifier":"y","name":"Y","role":"user","content":"YYY"}
            ]}
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(listOf("X", "Y"), list.map { it.name })
        assertEquals(listOf(true, true), list.map { it.enabled })
    }

    @Test
    fun skipsMarkersBlankContentAndDuplicateIdentifiers() {
        val json = """
            {
              "prompts": [
                {"identifier":"chatHistory","name":"聊天记录","role":"system","content":"","marker":true},
                {"identifier":"worldInfoBefore","name":"世界书前","role":"system","content":"WIB","marker":true},
                {"identifier":"dup","name":"第一次","role":"user","content":"FIRST"},
                {"identifier":"dup","name":"第二次","role":"user","content":"SECOND"},
                {"identifier":"blank","name":"空白","role":"user","content":" "}
              ],
              "prompt_order": [{"character_id":100001,"order":[
                {"identifier":"chatHistory","enabled":true},
                {"identifier":"worldInfoBefore","enabled":true},
                {"identifier":"dup","enabled":true},
                {"identifier":"blank","enabled":true}
              ]}]
            }
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(1, list.size)
        assertEquals("第一次", list[0].name)
    }

    @Test
    fun positionMatrixMatchesSillyTavernSemantics() {
        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.USER),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.ASSISTANT),
        )
        assertEquals(
            InjectionPosition.AT_DEPTH,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(1, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.AT_DEPTH,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(1, MessageRole.USER),
        )
        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(null, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(null, MessageRole.USER),
        )
    }

    @Test
    fun roleMappingHandlesCaseAndUnknown() {
        assertEquals(MessageRole.USER, ModeInjectionSerializer.mapSillyTavernPromptRole("USER"))
        assertEquals(MessageRole.ASSISTANT, ModeInjectionSerializer.mapSillyTavernPromptRole("Assistant"))
        assertEquals(MessageRole.SYSTEM, ModeInjectionSerializer.mapSillyTavernPromptRole("something"))
        assertEquals(MessageRole.SYSTEM, ModeInjectionSerializer.mapSillyTavernPromptRole(null))
    }

    @Test
    fun promptLevelEnabledFieldIsIgnored() {
        val json = """
            {"prompts":[
              {"identifier":"p","name":"P","role":"system","content":"CC","enabled":false}
            ],
            "prompt_order":[{"character_id":100001,"order":[{"identifier":"p","enabled":true}]}]}
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(1, list.size)
        assertEquals(true, list[0].enabled)
    }

    @Test
    fun invalidOrEmptyPresetReturnsNull() {
        assertNull(parse("""{"prompt_order":[]}"""))
        assertNull(parse("""{"prompts":[{"identifier":"m","marker":true,"content":"x"}]}"""))
        assertNull(parse("not a json at all"))
    }

    @Test
    fun relativePromptsAfterChatHistoryMapToAfterDialog() {
        val json = """
            {
              "prompts": [
                {"identifier":"main","name":"Main","role":"system","content":"MAIN","injection_position":0},
                {"identifier":"chatHistory","name":"Chat History","role":"system","content":"","marker":true},
                {"identifier":"jailbreak","name":"PostHistory","role":"system","content":"JB","injection_position":0,"forbid_overrides":true}
              ],
              "prompt_order": [
                {"character_id":100001,"order":[
                  {"identifier":"main","enabled":true},
                  {"identifier":"chatHistory","enabled":true},
                  {"identifier":"jailbreak","enabled":true}
                ]}
              ],
              "wi_format": "[Lore: {0}]",
              "scenario_format": "Scenario: {{scenario}}",
              "personality_format": "Personality: {{personality}}",
              "new_chat_prompt": "[Start]",
              "assistant_prefill": "Sure,",
              "squash_system_messages": true
            }
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(2, list.size)
        assertEquals(InjectionPosition.AFTER_SYSTEM_PROMPT, list[0].position)
        assertEquals(InjectionPosition.AFTER_DIALOG, list[1].position)

        val buckets = kotlinx.serialization.json.buildJsonObject {
            put("测试预设", kotlinx.serialization.json.Json.parseToJsonElement(json))
        }.toString()
        val cfg = ModeInjectionSerializer.parseActiveStPresetConfig(buckets, setOf("测试预设"))
        assertNotNull(cfg)
        assertEquals("[Lore: {0}]", cfg!!.wiFormat)
        assertEquals("Scenario: {{scenario}}", cfg.scenarioFormat)
        assertEquals("Personality: {{personality}}", cfg.personalityFormat)
        assertEquals("[Start]", cfg.newChatPrompt)
        assertEquals("Sure,", cfg.assistantPrefill)
        assertEquals(true, cfg.squashSystemMessages)
        assertEquals(true, cfg.forbidOverridesJailbreak)
    }

    @Test
    fun mergePresetRegexScriptsHonorsSnapshotAndCorruptedStatesAndPreservesUserRegex() {
        val userRx = me.rerere.rikkahub.data.st.regex.RegexScript(
            id = "shared-id",
            scriptName = "UserGlobal",
            findRegex = "foo",
            replaceString = "bar",
            sourcePresetGroup = null,
        )
        val otherPresetRx = me.rerere.rikkahub.data.st.regex.RegexScript(
            id = "other-1",
            scriptName = "OtherPreset",
            findRegex = "baz",
            replaceString = "qux",
            sourcePresetGroup = "PresetB",
        )
        val initial = listOf(userRx, otherPresetRx)

        // 1. 首次导入含 2 条正则的 PresetA（其中 1 条与用户正则同 ID）
        val presetAFirst = listOf(
            me.rerere.rikkahub.data.st.regex.RegexScript(id = "shared-id", scriptName = "A1", findRegex = "a1", replaceString = "r1"),
            me.rerere.rikkahub.data.st.regex.RegexScript(id = "a-2", scriptName = "A2", findRegex = "a2", replaceString = "r2"),
        )
        val (afterFirst, added1) = ModeInjectionSerializer.mergePresetRegexScripts(
            existing = initial,
            presetGroup = "PresetA",
            imported = presetAFirst,
            fieldState = PresetSubfieldState.PRESENT_VALID,
        )
        assertEquals(2, added1)
        assertEquals(4, afterFirst.size)
        // 用户自建正则绝不被覆盖
        assertEquals("UserGlobal", afterFirst.first { it.id == "shared-id" }.scriptName)
        assertNull(afterFirst.first { it.id == "shared-id" }.sourcePresetGroup)
        // 冲突的预设正则获得作用域 ID
        assertEquals("A1", afterFirst.first { it.id == "PresetA::shared-id" }.scriptName)

        // 2. 重导入损坏的 regex_scripts（CORRUPTED）-> 拒绝删除 PresetA 原有正则
        val (afterCorrupted, addedCorrupted) = ModeInjectionSerializer.mergePresetRegexScripts(
            existing = afterFirst,
            presetGroup = "PresetA",
            imported = emptyList(),
            fieldState = PresetSubfieldState.CORRUPTED,
        )
        assertEquals(0, addedCorrupted)
        assertEquals(4, afterCorrupted.size)

        // 3. 重导入合法空列表（PRESENT_VALID + empty）-> 清理 PresetA 旧残留，保留用户正则与 PresetB
        val (afterEmpty, addedEmpty) = ModeInjectionSerializer.mergePresetRegexScripts(
            existing = afterFirst,
            presetGroup = "PresetA",
            imported = emptyList(),
            fieldState = PresetSubfieldState.PRESENT_VALID,
        )
        assertEquals(0, addedEmpty)
        assertEquals(2, afterEmpty.size)
        assertEquals(listOf("shared-id", "other-1"), afterEmpty.map { it.id })
    }

    @Test
    fun allDisabledPresetPreservesFalseEnabledStates() {
        val json = """
            {
              "prompts": [
                {"identifier":"p1","name":"P1","role":"system","content":"C1","enabled":false},
                {"identifier":"p2","name":"P2","role":"user","content":"C2","enabled":false}
              ],
              "prompt_order": [{"character_id":100001,"order":[
                {"identifier":"p1","enabled":false},
                {"identifier":"p2","enabled":false}
              ]}]
            }
        """.trimIndent()
        val bundle = ModeInjectionSerializer.importPresetBundleFromString(json, "全关预设").getOrThrow()
        assertEquals(listOf(false, false), bundle.injections.map { it.enabled })
    }

    @Test
    fun relativeEntriesKeepPromptOrderRegardlessOfInjectionOrderAndReimportPreservesBinding() {
        val json = """
            {
              "prompts": [
                {"identifier":"rel_first","name":"第一条相对","role":"system","content":"R1","injection_position":0,"injection_order":100},
                {"identifier":"rel_second","name":"第二条相对","role":"system","content":"R2","injection_position":0,"injection_order":10},
                {"identifier":"rel_third","name":"第三条相对","role":"user","content":"R3","injection_position":0,"injection_order":50}
              ],
              "prompt_order": [{"character_id":100001,"order":[
                {"identifier":"rel_first","enabled":true},
                {"identifier":"rel_second","enabled":true},
                {"identifier":"rel_third","enabled":true}
              ]}],
              "extensions": {
                "regex_scripts": [
                  {"id":"rx1","scriptName":"PresetRx","findRegex":"foo","replaceString":"bar"}
                ],
                "tavern_helper": {
                  "scripts": [{"id":"sc1","name":"PresetScript","content":"console.log(1)"}]
                }
              }
            }
        """.trimIndent()

        val firstImport = parse(json)!!
        assertEquals(listOf("第一条相对", "第二条相对", "第三条相对"), firstImport.map { it.name })
        val secondImport = parse(json)!!
        assertEquals(firstImport.map { it.id }, secondImport.map { it.id })

        val bundle = ModeInjectionSerializer.importPresetBundleFromString(json, "预设A").getOrThrow()
        val assistant = me.rerere.rikkahub.data.model.Assistant(
            name = "测试助手",
            modeInjectionIds = bundle.injections.map { it.id }.toSet(),
        )
        val initialSettings = me.rerere.rikkahub.data.datastore.Settings(
            init = true,
            themeId = "sakura",
            providers = emptyList(),
            assistants = listOf(assistant),
            ttsProviders = emptyList(),
            modeInjections = emptyList(),
        )
        val (afterImport, _) = me.rerere.rikkahub.ui.components.ai.applyPresetBundleToSettings(initialSettings, bundle)
        assertEquals(
            setOf("预设A"),
            me.rerere.rikkahub.data.st.runtime.enabledPresetGroups(afterImport.assistants.first(), afterImport),
        )

        // 重命名 预设A -> 预设B
        val renamedList = afterImport.modeInjections.map {
            if (it.presetGroup == "预设A") it.copy(presetGroup = "预设B") else it
        }
        val afterRename = me.rerere.rikkahub.ui.components.ai.applyPresetGroupLifecycleChange(
            current = afterImport,
            oldName = "预设A",
            newName = "预设B",
            updatedModeInjections = renamedList,
        )
        assertEquals(
            setOf("预设B"),
            me.rerere.rikkahub.data.st.runtime.enabledPresetGroups(afterRename.assistants.first(), afterRename),
        )
        assertEquals(listOf("预设B"), afterRename.regexScripts.mapNotNull { it.sourcePresetGroup })
        org.junit.Assert.assertTrue(afterRename.tavernPresets.contains("预设B"))
        org.junit.Assert.assertFalse(afterRename.tavernPresets.contains("预设A"))
        org.junit.Assert.assertTrue(afterRename.tavernPresetScripts.contains("预设B"))
        org.junit.Assert.assertFalse(afterRename.tavernPresetScripts.contains("预设A"))

        // 删除 预设B
        val afterDelete = me.rerere.rikkahub.ui.components.ai.applyPresetGroupLifecycleChange(
            current = afterRename,
            oldName = "预设B",
            newName = null,
            updatedModeInjections = emptyList(),
        )
        assertEquals(emptyList< Any>(), afterDelete.modeInjections)
        assertEquals(emptyList<Any>(), afterDelete.regexScripts)
        org.junit.Assert.assertFalse(afterDelete.tavernPresets.contains("预设B"))
        org.junit.Assert.assertFalse(afterDelete.tavernPresetScripts.contains("预设B"))
    }

    @Test
    fun d10AtomicPresetGroupToggleAndSillyTavernLorebookExportRoundTrip() {
        val json = """
            {
              "prompts": [
                {"identifier":"p1","name":"P1","role":"system","content":"C1","enabled":false},
                {"identifier":"p2","name":"P2","role":"user","content":"C2","enabled":false}
              ],
              "prompt_order": [{"character_id":100001,"order":[
                {"identifier":"p1","enabled":false},
                {"identifier":"p2","enabled":false}
              ]}]
            }
        """.trimIndent()
        val bundle = ModeInjectionSerializer.importPresetBundleFromString(json, "全关预设").getOrThrow()
        val assistant = me.rerere.rikkahub.data.model.Assistant(name = "A", modeInjectionIds = emptySet())
        val s0 = me.rerere.rikkahub.data.datastore.Settings(
            init = true,
            themeId = "sakura",
            providers = emptyList(),
            assistants = listOf(assistant),
            ttsProviders = emptyList(),
            modeInjections = bundle.injections,
        )
        val s1 = me.rerere.rikkahub.ui.components.ai.togglePresetGroupForAssistant(s0, assistant.id, "全关预设", enable = true)
        org.junit.Assert.assertTrue(s1.modeInjections.all { it.enabled })
        assertEquals(bundle.injections.map { it.id }.toSet(), s1.assistants.first().modeInjectionIds)

        // 验证世界书导出为官方 SillyTavern JSON 格式并可无损再导入
        val sampleBook = me.rerere.rikkahub.data.model.Lorebook(
            name = "测试世界书",
            entries = listOf(
                me.rerere.rikkahub.data.model.PromptInjection.RegexInjection(
                    name = "条目A",
                    content = "世界观内容A",
                    keywords = listOf("关键字1", "关键字2"),
                    constantActive = true,
                    priority = 42,
                )
            ),
        )
        val stJson = LorebookSerializer.exportToSillyTavernJson(sampleBook)
        val reimported = LorebookSerializer.importFromString(stJson, "测试世界书")
        assertNotNull(reimported)
        assertEquals(1, reimported!!.entries.size)
        assertEquals("条目A", reimported.entries[0].name)
        assertEquals("世界观内容A", reimported.entries[0].content)
        assertEquals(listOf("关键字1", "关键字2"), reimported.entries[0].keywords)
    }

    @Test
    fun presetSamplerAndPostProcessingAndCustomBodiesAreParsedAndApplied() {
        val json = """
            {
              "prompts": [
                {"identifier":"main","name":"Main","role":"system","content":"MAIN","injection_position":0}
              ],
              "prompt_order": [{"character_id":100001,"order":[{"identifier":"main","enabled":true}]}],
              "temp": 1.15,
              "top_p": 0.92,
              "top_k": 40,
              "min_p": 0.05,
              "top_a": 0.1,
              "freq_pen": 0.25,
              "pres_pen": 0.15,
              "rep_pen": 1.08,
              "openai_max_tokens": 1024,
              "seed": 42,
              "wrap_in_quotes": true,
              "start_reply_with": "Understood,",
              "prompt_post_processing": "semi",
              "custom_stopping_strings": "[\"\\nUser:\", \"\\nSystem:\"]",
              "logit_bias": [{"text": "banword", "value": -100}]
            }
        """.trimIndent()
        val buckets = kotlinx.serialization.json.buildJsonObject {
            put("完整采样预设", kotlinx.serialization.json.Json.parseToJsonElement(json))
        }.toString()
        val cfg = ModeInjectionSerializer.parseActiveStPresetConfig(buckets, setOf("完整采样预设"))
        assertNotNull(cfg)
        assertEquals(1.15f, cfg.temperature!!, 0.001f)
        assertEquals(0.92f, cfg.topP!!, 0.001f)
        assertEquals(40, cfg.topK)
        assertEquals(0.05f, cfg.minP!!, 0.0001f)
        assertEquals(0.1f, cfg.topA!!, 0.0001f)
        assertEquals(0.25f, cfg.frequencyPenalty!!, 0.0001f)
        assertEquals(0.15f, cfg.presencePenalty!!, 0.0001f)
        assertEquals(1.08f, cfg.repetitionPenalty!!, 0.0001f)
        assertEquals(1024, cfg.maxTokens)
        assertEquals(42, cfg.seed)
        assertEquals(true, cfg.wrapInQuotes)
        assertEquals("Understood,", cfg.startReplyWith)
        assertEquals("semi", cfg.promptPostProcessing)
        assertEquals(listOf("\nUser:", "\nSystem:"), cfg.customStoppingStrings)
        assertEquals(-100f, cfg.logitBias["banword"]!!, 0.001f)

        // 验证 prompt_post_processing = semi 将非首条 SYSTEM 消息降级为 USER 消息
        val rawMessages = listOf(
            me.rerere.ai.ui.UIMessage.system("System 1"),
            me.rerere.ai.ui.UIMessage.user("Hello"),
            me.rerere.ai.ui.UIMessage.system("Mid-chat System Note"),
        )
        val processed = me.rerere.rikkahub.data.ai.applyPromptPostProcessing(rawMessages, "semi")
        assertEquals(MessageRole.SYSTEM, processed[0].role)
        assertEquals(MessageRole.USER, processed[1].role)
        assertEquals(MessageRole.USER, processed[2].role)

        // 验证 buildPresetMergedCustomBodies 将预设采样器透传到 CustomBody
        val mergedBodies = me.rerere.rikkahub.data.ai.buildPresetMergedCustomBodies(emptyList(), emptyList(), cfg)
        val bodyKeys = mergedBodies.map { it.key }.toSet()
        org.junit.Assert.assertTrue(bodyKeys.containsAll(setOf("stop", "frequency_penalty", "presence_penalty", "repetition_penalty", "min_p", "top_a", "seed")))
    }
}
