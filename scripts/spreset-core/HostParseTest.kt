package me.rerere.rikkahub.data.st.preset

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * host Kotlin 自测：验证真实预设的解析、条件 ChatSquash 的严格三条结构、
 * MessageInjections 展开与 snake_case/camelCase 兼容。
 *
 * 不依赖 Android/QuickJS；运行时把 squashed_post_script 关闭，脚本部分由 Node 自测覆盖。
 */
fun main(args: Array<String>) {
    val presetPath = args.firstOrNull()
        ?: "E:\\Localsend download\\【小猫之神·喵赛亚】1.5.json"
    val text = File(presetPath).readText(Charsets.UTF_8).removePrefix("\uFEFF")
    val root = Json.parseToJsonElement(text).jsonObject
    val spreset = root["extensions"]?.jsonObject?.get("SPreset")?.jsonObject
        ?: error("extensions.SPreset missing")
    val config = SpresetCompat.parse(spreset)

    check(config.toolBindings.size == 2) { "ToolBindings count=${config.toolBindings.size}" }
    check(config.toolBindings.count { it.enabled } == 1)
    val gameTool = config.toolBindings.first { it.enabled }
    check(gameTool.effectiveName == "game_content")
    check(gameTool.requiredParameters() == listOf("content"))
    check(config.outputPreprocessing.enabled)
    check(config.outputPreprocessing.consumeToolCalls)
    check(config.outputPreprocessing.toolCallFormatter.enabled)
    check(config.finalRequestProcessing.enabled)
    check(config.fixedPresetName.isEmpty())
    check(config.forcedPostProcessing.enabled)
    check(config.forcedPostProcessing.mode.isEmpty())
    check(config.messageInjections.size == 2)
    check(config.messageInjections.none { it.enabled })
    check(config.chatSquash.conditionalEnabled)
    check(config.chatSquash.conditionalTag == "<|merge|>")
    check(config.chatSquash.squashedSeparatorString == "<|sep|>")
    check(config.chatSquash.squashedPostScriptEnable)
    check(config.regexBinding.isNotEmpty())
    check(config.macroNestDepth == 0)
    check(
        SpresetCompat.supportedKeys().containsAll(
            listOf(
                "ChatSquash", "RegexBinding", "MacroNest", "ToolBindings",
                "MessageInjections", "OutputPreprocessing", "FixedPresetName",
                "ForcedPostProcessing", "FinalRequestProcessing", "version", "enabled",
            )
        )
    )
    println("✓ 真实预设六键解析：tools=2(enabled 1), output=true/consume=true, final=true, injections=2 disabled")

    // ---------- createDynamicTools：真实 form.parameters 直接生成 Tool（无需 QuickJS） ----------
    val dynamicTools = runBlocking {
        SpresetCompat.createDynamicTools(config, existingNames = setOf("taken"))
    }
    check(dynamicTools.size == 1) { "dynamicTools size=${dynamicTools.size}" }
    val dynamicTool = dynamicTools.single()
    check(dynamicTool.name == "game_content")
    val schema = dynamicTool.parameters() as? InputSchema.Obj
        ?: error("dynamic tool parameters is not InputSchema.Obj")
    check(schema.properties.containsKey("content"))
    check(schema.required == listOf("content"))
    println("✓ createDynamicTools：form.parameters → InputSchema.Obj(content required)")

    // ---------- processStream 禁用时纯透传（不启动 QuickJS） ----------
    val plainConfig = StSpresetConfig(outputPreprocessing = StSpresetOutputPreprocessing())
    val plainChunks = runBlocking {
        SpresetCompat.processStream(
            flowOf(
                StreamChunk.TextStart("t1"),
                StreamChunk.TextDelta("t1", "hello"),
                StreamChunk.TextEnd("t1"),
            ),
            plainConfig,
        ).toList()
    }
    check(plainChunks.size == 3)
    check((plainChunks[1] as StreamChunk.TextDelta).text == "hello")
    println("✓ processStream：OutputPreprocessing 禁用时原样透传 chunk")
    // ---------- parseActivePreset：enabledGroups 为空时按 FixedPresetName 回退 ----------
    val fixedSpreset = buildJsonObject {
        put("FixedPresetName", "fixed-group")
        put("ChatSquash", buildJsonObject { put("enabled", true) })
    }
    val fixedTarget = buildJsonObject {
        put("ChatSquash", buildJsonObject {
            put("enabled", true)
            put("conditional_enabled", true)
            put("conditional_tag", "<m>")
        })
    }
    val tavernPresets = buildJsonObject {
        put("declaring-group", buildJsonObject {
            put("extensions", buildJsonObject { put("SPreset", fixedSpreset) })
        })
        put("fixed-group", buildJsonObject {
            put("extensions", buildJsonObject { put("SPreset", fixedTarget) })
        })
    }.toString()
    val active = SpresetCompat.parseActivePreset(tavernPresets, emptySet())
    check(active.chatSquash.enabled)
    check(active.chatSquash.conditionalEnabled)
    println("✓ parseActivePreset：enabledGroups 为空时 FixedPresetName 回退成功")

    // ---------- 条件 ChatSquash：严格三条，lastUser 之后的 AFTER_DIALOG system 不丢 ----------
    val squashCfg = config.chatSquash.copy(squashedPostScriptEnable = false)
    val marker = UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text("{{trim}}<|sep|><|merge|><run_tool_replacement>")),
    )
    val initialSystem = UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text("<初始角色卡>")),
    )
    val historyAssistant = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("|小猫之神|喵~")),
    )
    val lastUser = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text("继续")),
    )
    val afterDialog = UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text("AFTER_DIALOG：历史后置指令")),
    )
    val squashed = runBlocking {
        SpresetCompat.applyChatSquash(
            messages = listOf(initialSystem, marker, historyAssistant, lastUser, afterDialog),
            cfg = squashCfg,
            userName = "小明",
            charName = "小猫之神",
        )
    }
    check(squashed.size == 3) { "conditional squash size=${squashed.size}" }
    check(squashed.map { it.role } == listOf(MessageRole.SYSTEM, MessageRole.ASSISTANT, MessageRole.USER))
    check(squashed[0].toText().contains("<run_tool_replacement>"))
    check(!squashed[0].toText().contains("<|merge|>"))
    check(!squashed[0].toText().contains("<|sep|>"))
    check(squashed[1].toText().contains("AFTER_DIALOG：历史后置指令")) { "lastUser 之后的 system 被丢弃" }
    check(squashed[1].toText().contains("<初始角色卡>"))
    check(squashed[1].toText().contains("|小猫之神|"))
    check(squashed[2].toText() == "继续")
    println("✓ 条件 ChatSquash：严格 [system, assistant, user]，AFTER_DIALOG 后置 system 保留")

    // ---------- MessageInjections：reasoning + toolCalls + toolResults ----------
    val injectionPreset = buildJsonObject {
        put("MessageInjections", buildJsonObject {
            put("inj-1", buildJsonObject {
                put("enabled", true)
                put("reasoning", "先想一下")
                put("signature", "sig-1")
                put("toolCalls", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "call-1")
                        put("name", "game_content")
                        put("arguments", "{\"content\":\"你好\"}")
                    })
                })
                put("toolResults", buildJsonArray {
                    add(buildJsonObject {
                        put("id", "call-1")
                        put("content", "ok")
                    })
                })
            })
        })
    }
    val injectionConfig = SpresetCompat.parse(injectionPreset)
    val injected = runBlocking {
        SpresetCompat.applyMessageInjections(listOf(UIMessage.user("u")), injectionConfig)
    }
    check(injected.size == 2)
    check(injected[0].role == MessageRole.ASSISTANT)
    check(injected[0].parts.any { it is UIMessagePart.Reasoning })
    val toolPart = injected[0].parts.filterIsInstance<UIMessagePart.Tool>().single()
    check(toolPart.toolName == "game_content")
    check(toolPart.input.contains("你好"))
    check(toolPart.output.filterIsInstance<UIMessagePart.Text>().single().text == "ok")
    println("✓ MessageInjections：reasoning + toolCall + toolResult 注入到末尾 user 前")

    // ---------- camelCase/snake_case 双变体 ----------
    val variant = buildJsonObject {
        put("chat_squash", buildJsonObject {
            put("enabled", true)
            put("conditional_enabled", true)
            put("conditional_tag", "<m>")
        })
        put("tool_bindings", buildJsonArray {
            add(buildJsonObject {
                put("enabled", true)
                put("name", "variant_tool")
                put("form", buildJsonObject {
                    put("parameters", buildJsonArray {
                        add(buildJsonObject {
                            put("name", "x")
                            put("type", "string")
                            put("required", true)
                        })
                    })
                })
            })
        })
        put("output_preprocessing", buildJsonObject {
            put("enabled", true)
            put("script", "x => x")
            put("consume_tool_calls", true)
        })
        put("final_request_processing", buildJsonObject {
            put("enabled", true)
            put("script", "x => x")
        })
    }
    val variantConfig = SpresetCompat.parse(variant)
    check(variantConfig.chatSquash.conditionalEnabled)
    check(variantConfig.toolBindings.single().effectiveName == "variant_tool")
    check(variantConfig.toolBindings.single().requiredParameters() == listOf("x"))
    check(variantConfig.outputPreprocessing.consumeToolCalls)
    check(variantConfig.finalRequestProcessing.enabled)
    check(JsonArray(emptyList()).isEmpty())
    println("✓ camelCase/snake_case 兼容与 form.parameters → JSON Schema")

    println("\n全部 SPreset host Kotlin 自测通过。")
}
