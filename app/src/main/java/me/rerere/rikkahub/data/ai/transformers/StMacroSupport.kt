package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.macro.DynamicMacro
import me.rerere.rikkahub.data.st.macro.MacroChatMessage
import me.rerere.rikkahub.data.st.macro.MacroDefinitions
import me.rerere.rikkahub.data.st.macro.MacroEngine
import me.rerere.rikkahub.data.st.macro.MacroEnv
import me.rerere.rikkahub.data.st.macro.MacroHash
import me.rerere.rikkahub.data.st.macro.MacroRegistry
import me.rerere.rikkahub.data.st.macro.MacroVariableStore
import me.rerere.rikkahub.data.st.macro.VariableStores
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import java.util.Locale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * ST 宏引擎（高保真移植，`data.st.macro`）接入层。
 *
 * 职责：
 * 1. 将 plus 侧 [PlaceholderCtx] / [MacroVars] 适配为 ST 引擎所需的 [MacroEnv]；
 * 2. 既有 `{{占位符}}`（cur_date / model_id / battery_level / ...）自动注册为
 *    动态宏（仅当官方宏注册表中不存在同名宏时），保证零回归；
 * 3. 官方宏（char/user/description/if/setvar/pick/time/...）走引擎内置实现，
 *    对齐 SillyTavern 1.18 语义 —— 即“高保真”。
 *
 * 说明：SDK 单聊场景下 group/charIfNotGroup 经既有占位符解析器取值；
 * 变量（setvar/getvar/...）经 [MacroVars] 复用既有 DataStore 持久化，不引入第二套存储。
 */
internal object StMacroSupport {

    /** 一次性注册全部内置宏（幂等；首次使用时执行）。 */
    private val ready: Boolean = run {
        MacroDefinitions.registerAll()
        true
    }

    /** 本地变量作用域键：无会话时使用保留键，避免误写全局桶。 */
    private const val LOCAL_SCOPE_FALLBACK = "__st_local_default__"

    /**
     * 用高保真 ST 宏引擎替换文本中的 `{{...}}`。
     *
     * @param text 待替换文本
     * @param ctx  plus 侧占位符上下文
     * @param vars plus 侧变量存取接口（DataStore 持久化由调用方 flush）
     * @param provider 既有占位符提供者（作为动态宏兜底）
     */
    fun substitute(
        text: String,
        ctx: PlaceholderCtx,
        vars: MacroVars,
        provider: PlaceholderProvider,
    ): String {
        if (!text.contains("{{")) return text
        @Suppress("UNUSED_EXPRESSION") ready
        return MacroEngine.evaluate(text, buildEnv(ctx, vars, provider))
    }

    // ==================== 环境构建 ====================

    private fun buildEnv(
        ctx: PlaceholderCtx,
        vars: MacroVars,
        provider: PlaceholderProvider,
    ): MacroEnv {
        val settings = ctx.settingsStore.settingsFlow.value
        val userName = settings.displaySetting.userNickname.ifBlank { "user" }
        val charName = ctx.assistant.name.ifBlank { "assistant" }
        val groupName = resolvePlaceholder(provider, "group", ctx)?.takeIf { it.isNotBlank() } ?: charName
        val tav = ctx.assistant.tavernData

        return MacroEnv(
            names = MacroEnv.Names(
                user = userName,
                char = charName,
                group = groupName,
                groupNotMuted = groupName,
                notChar = userName,
            ),
            character = MacroEnv.CharacterInfo(
                charPrompt = tav?.systemPrompt ?: "",
                charInstruction = tav?.postHistoryInstructions ?: "",
                description = tav?.description ?: "",
                personality = tav?.personality ?: "",
                scenario = tav?.scenario ?: "",
                persona = settings.personas
                    .firstOrNull { p -> p.id == settings.activePersonaId && p.enabled }
                    ?.description ?: "",
                mesExamplesRaw = tav?.mesExample ?: "",
                version = tav?.characterVersion ?: "",
                charDepthPrompt = tav?.depthPrompt ?: "",
                creatorNotes = tav?.creatorNotes ?: "",
                firstMessage = tav?.firstMessage ?: "",
                alternateGreetings = tav?.alternateGreetings ?: emptyList(),
            ),
            system = MacroEnv.SystemInfo(
                model = ctx.model.displayName,
                chatIdHash = ctx.conversationId?.let { MacroHash.getStringHash(it.toString()) },
                generationType = ctx.generationType?.value ?: "",
                // [v234 S1] systemPrompt 宏数据源：宿主助手系统提示词充当 ST sysprompt
                syspromptEnabled = ctx.assistant.systemPrompt.isNotBlank(),
                syspromptContent = ctx.assistant.systemPrompt,
            ),
            // [v234 S1] instruct* 系宏数据源（对齐 ST Advanced Formatting → Instruct Mode）
            instruct = MacroEnv.InstructInfo(
                enabled = ctx.assistant.instructTemplate.enabled,
                storyStringPrefix = ctx.assistant.instructTemplate.storyStringPrefix,
                storyStringSuffix = ctx.assistant.instructTemplate.storyStringSuffix,
                userPrefix = ctx.assistant.instructTemplate.inputSequence,
                userSuffix = ctx.assistant.instructTemplate.inputSuffix,
                assistantPrefix = ctx.assistant.instructTemplate.outputSequence,
                assistantSuffix = ctx.assistant.instructTemplate.outputSuffix,
                systemPrefix = ctx.assistant.instructTemplate.systemSequence,
                systemSuffix = ctx.assistant.instructTemplate.systemSuffix,
                firstAssistantPrefix = ctx.assistant.instructTemplate.firstOutputSequence.ifBlank { ctx.assistant.instructTemplate.outputSequence },
                lastAssistantPrefix = ctx.assistant.instructTemplate.lastOutputSequence.ifBlank { ctx.assistant.instructTemplate.outputSequence },
                firstUserPrefix = ctx.assistant.instructTemplate.firstInputSequence.ifBlank { ctx.assistant.instructTemplate.inputSequence },
                lastUserPrefix = ctx.assistant.instructTemplate.lastInputSequence.ifBlank { ctx.assistant.instructTemplate.inputSequence },
            ),
            // 导演备注三宏（authorsNote / charAuthorsNote / defaultAuthorsNote）：
            // rikkaST 只有全局 Settings.authorNote（无 chat_metadata / 角色卡绑定 / 默认模板），
            // 故 current 与 default 同源；character 无数据源恒空串（见 MacroEnv.AuthorNotes 注释）。
            authorNotes = MacroEnv.AuthorNotes(
                current = settings.authorNote,
                character = "",
                default = settings.authorNote,
            ),
            variables = VariableStores(
                local = MacroVarsStore(vars, ctx.conversationId?.toString() ?: LOCAL_SCOPE_FALLBACK),
                global = MacroVarsStore(vars, null),
            ),
            chat = buildChat(ctx),
            dynamicMacros = buildDynamicMacros(provider, ctx),
            // 世界书 outlet 快照（本次生成流程由 PromptInjectionTransformer 发布）
            outlets = WorldInfoOutlets.snapshot(ctx.conversationId?.toString()),
            originalFn = {
                lastRealMessage(ctx.messages, MessageRole.USER)?.let(::macroTextOf) ?: ""
            },
            locale = Locale.getDefault(),
        )
    }

    /** 聊天记录 → 引擎轻量视图（跳过注入块与示例消息，与既有语义一致）。 */
    private fun buildChat(ctx: PlaceholderCtx): List<MacroChatMessage> =
        ctx.messages
            .filter { msg ->
                !msg.isInjectedBlock() &&
                    msg.annotations.none { a -> a is UIMessageAnnotation.ExampleMessage }
            }
            .map { msg ->
                val swipe = ctx.swipeMeta[msg.id]
                MacroChatMessage(
                    text = macroTextOf(msg),
                    isUser = msg.role == MessageRole.USER,
                    isSystem = msg.role == MessageRole.SYSTEM,
                    sentAt = msg.toJavaInstant(),
                    currentSwipeId = swipe?.currentSwipeId,
                    swipeCount = swipe?.swipeCount,
                )
            }

    /**
     * 既有占位符 → 动态宏（仅注册官方注册表中不存在的名字，避免遮蔽官方实现）。
     * 解析失败返回空串（对齐 plus 旧行为）。
     */
    private fun buildDynamicMacros(
        provider: PlaceholderProvider,
        ctx: PlaceholderCtx,
    ): Map<String, DynamicMacro> {
        val out = LinkedHashMap<String, DynamicMacro>()
        for ((key, info) in provider.placeholders) {
            if (MacroRegistry.hasMacro(key)) continue
            out[key.lowercase()] = DynamicMacro.Handler {
                try {
                    info.resolver(ctx)
                } catch (_: Exception) {
                    ""
                }
            }
        }
        // [v234 S2] MacrosParser JS 注册宏 → 动态宏（仅补官方注册表没有的名字）：
        // - 字符串宏：Kotlin 侧直接展开（零跨层开销）；
        // - 函数宏：返回哨兵 {{__rikka_fn::name::nonce}}，由 MacrosMacroPass 挂起回 JS 执行
        //   （对齐 ST MacrosParser 函数宏的 nonce 调用语义）。
        for ((name, entry) in TavernRuntimeManager.macrosRegistry.get()) {
            val key = name.lowercase()
            if (key in out || MacroRegistry.hasMacro(key)) continue
            out[key] = if (entry.first == "function") {
                val macroName = name
                val nonce = java.util.UUID.randomUUID().toString()
                DynamicMacro.Value("{{__rikka_fn::" + macroName + "::" + nonce + "}}")
            } else {
                DynamicMacro.Value(entry.second)
            }
        }
        return out
    }

    private fun resolvePlaceholder(
        provider: PlaceholderProvider,
        key: String,
        ctx: PlaceholderCtx,
    ): String? = try {
        provider.placeholders[key]?.resolver?.invoke(ctx)
    } catch (_: Exception) {
        null
    }

    /** 从消息列表末尾找真正的用户/角色消息（跳过注入块与示例消息）。 */
    private fun lastRealMessage(messages: List<UIMessage>, role: MessageRole): UIMessage? =
        messages.lastOrNull { m ->
            m.role == role &&
                !m.isInjectedBlock() &&
                m.annotations.none { a -> a is UIMessageAnnotation.ExampleMessage }
        }

    private fun macroTextOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    /** UIMessage.createdAt（本地时间）→ java.time.Instant；异常时返回 null（宏侧降级）。 */
    private fun UIMessage.toJavaInstant(): java.time.Instant? = try {
        val kInstant = createdAt.toInstant(TimeZone.currentSystemDefault())
        java.time.Instant.ofEpochMilli(kInstant.toEpochMilliseconds())
    } catch (_: Throwable) {
        null
    }
}

/** [MacroVars] → ST 引擎 [MacroVariableStore] 适配器（chatKey=null 表示全局）。 */
private class MacroVarsStore(
    private val vars: MacroVars,
    private val chatKey: String?,
) : MacroVariableStore {
    override fun get(name: String): String? = vars.get(chatKey, name)
    override fun has(name: String): Boolean = vars.has(chatKey, name)
    override fun set(name: String, value: String) = vars.set(chatKey, name, value)
    override fun del(name: String) = vars.delete(chatKey, name)
    override fun inc(name: String): String = vars.inc(chatKey, name)
    override fun dec(name: String): String = vars.dec(chatKey, name)
    override fun add(name: String, value: String) = vars.add(chatKey, name, value)
    override fun getAtKey(name: String, key: String): String? = vars.getAtKey(chatKey, name, key)
    override fun setAtKey(name: String, key: String, value: String) = vars.setAtKey(chatKey, name, key, value)
}