package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.extractContextForMatching
import me.rerere.rikkahub.data.model.parseWorldInfoDecorators
import me.rerere.rikkahub.data.model.applyWorldInfoDecoratorOverrides
import me.rerere.rikkahub.data.export.parseActiveStPresetConfig
import me.rerere.rikkahub.data.st.runtime.enabledPresetGroups
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_ACTIVATE
import me.rerere.rikkahub.data.model.WORLD_INFO_DECORATOR_DONT_ACTIVATE
import me.rerere.rikkahub.data.st.regex.RegexPlacement
import me.rerere.rikkahub.data.st.runtime.TavernWorldInfoEvents
import me.rerere.rikkahub.data.st.tokenizer.TokenizerMode
import me.rerere.rikkahub.data.st.tokenizer.countTokens
import me.rerere.rikkahub.data.st.tokenizer.estimateTokensHeuristic
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import me.rerere.rikkahub.data.st.regex.mergeRegexScripts
import me.rerere.rikkahub.data.model.isTriggered
import me.rerere.rikkahub.data.model.matchedKeyScore
import me.rerere.rikkahub.data.model.matchesAnyKey
import kotlin.uuid.Uuid
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * 提示词注入转换器
 *
 * 根据 Assistant 关联的 ModeInjection 和 Lorebook 进行提示词注入
 */
object PromptInjectionTransformer : InputMessageTransformer {

    // 粘性追踪：assistantId:conversationId → (injectionId → 剩余轮数)
    private val stickyTracker = mutableMapOf<String, MutableMap<Uuid, Int>>()
    // 冷却追踪：assistantId:conversationId → (injectionId → 剩余冷却轮数)
    private val cooldownTracker = mutableMapOf<String, MutableMap<Uuid, Int>>()
    // 延迟覆盖追踪（供 /wi-set-timed-effect effect=delay 动态覆写）
    private val delayOverrides = mutableMapOf<String, MutableMap<Uuid, Int>>()
    // 最近一次扫描激活的 automationId 列表：assistantId:conversationId → List<String>
    private val activatedAutomationIds = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /** 世界书向量语义激活开关（供 /vector-worldinfo-state 控制，默认开启） */
    @Volatile
    var vectorWorldInfoEnabled: Boolean = true

    /** 向量相似度激活阈值（供 /vector-threshold 控制，默认 0.35） */
    @Volatile
    var vectorThreshold: Double = 0.35

    /** 向量检索最大召回条目数（供 /vector-max-entries 控制，默认 5） */
    @Volatile
    var vectorMaxEntries: Int = 5

    fun getTimedEffect(scopeKey: String, entryId: Uuid, effect: String): Int {
        return when (effect.trim().lowercase()) {
            "sticky" -> stickyTracker[scopeKey]?.get(entryId) ?: 0
            "cooldown" -> cooldownTracker[scopeKey]?.get(entryId) ?: 0
            "delay" -> delayOverrides[scopeKey]?.get(entryId) ?: 0
            else -> 0
        }
    }

    fun setTimedEffect(scopeKey: String, entryId: Uuid, effect: String, value: Int) {
        val v = value.coerceAtLeast(0)
        when (effect.trim().lowercase()) {
            "sticky" -> {
                val map = stickyTracker.getOrPut(scopeKey) { mutableMapOf() }
                if (v == 0) map.remove(entryId) else map[entryId] = v
            }
            "cooldown" -> {
                val map = cooldownTracker.getOrPut(scopeKey) { mutableMapOf() }
                if (v == 0) map.remove(entryId) else map[entryId] = v
            }
            "delay" -> {
                val map = delayOverrides.getOrPut(scopeKey) { mutableMapOf() }
                if (v == 0) map.remove(entryId) else map[entryId] = v
            }
        }
    }

    fun findTimedEffectScopeKey(conversationId: String?): String {
        val suffix = ":${conversationId ?: "no-conversation"}"
        return stickyTracker.keys.firstOrNull { it.endsWith(suffix) }
            ?: cooldownTracker.keys.firstOrNull { it.endsWith(suffix) }
            ?: delayOverrides.keys.firstOrNull { it.endsWith(suffix) }
            ?: "default$suffix"
    }

    fun recordActivatedAutomationIds(scopeKey: String, ids: List<String>) {
        activatedAutomationIds[scopeKey] = ids
    }

    fun getLastActivatedAutomationIds(scopeKey: String): List<String> =
        activatedAutomationIds[scopeKey].orEmpty()

    /**
     * 计算查询文本与世界书条目文本的二元/词袋余弦相似度（[0.0, 1.0]），严格防止除零。
     */
    internal fun computeVectorSimilarity(queryText: String, docText: String): Double {
        if (queryText.isBlank() || docText.isBlank()) return 0.0
        fun tokenize(s: String): Map<String, Int> {
            val lower = s.lowercase()
            val counts = HashMap<String, Int>()
            // 英文/数字词元
            Regex("[a-z0-9_]{2,}").findAll(lower).forEach { m ->
                val tok = m.value
                counts[tok] = (counts[tok] ?: 0) + 1
            }
            // CJK 字符与二元语法（bigrams）
            val cjk = lower.filter { it.code in 0x4E00..0x9FFF }
            for (i in cjk.indices) {
                val ch = cjk[i].toString()
                counts[ch] = (counts[ch] ?: 0) + 1
                if (i + 1 < cjk.length) {
                    val bg = cjk.substring(i, i + 2)
                    counts[bg] = (counts[bg] ?: 0) + 1
                }
            }
            return counts
        }
        val qVec = tokenize(queryText)
        val dVec = tokenize(docText)
        if (qVec.isEmpty() || dVec.isEmpty()) return 0.0
        var dot = 0.0
        var normQ = 0.0
        var normD = 0.0
        for ((k, qv) in qVec) {
            val qd = qv.toDouble()
            normQ += qd * qd
            val dv = dVec[k]
            if (dv != null) {
                dot += qd * dv.toDouble()
            }
        }
        for (dv in dVec.values) {
            val dd = dv.toDouble()
            normD += dd * dd
        }
        val denom = kotlin.math.sqrt(normQ) * kotlin.math.sqrt(normD)
        return if (denom <= 1e-12) 0.0 else (dot / denom).coerceIn(0.0, 1.0)
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 官方把 sticky/cooldown 存在 chat_metadata（按对话），这里也必须按对话隔离，
        // 避免 A 对话的粘性/冷却泄漏到同一助手的 B 对话
        val key = "${ctx.assistant.id}:${ctx.conversationId ?: "no-conversation"}"
        val activeSticky = stickyTracker.getOrPut(key) { mutableMapOf() }
        val cooldowns = cooldownTracker.getOrPut(key) { mutableMapOf() }
        val delayMap = delayOverrides.getOrPut(key) { mutableMapOf() }

        val activeGroups = enabledPresetGroups(ctx.assistant, ctx.settings, ctx.conversationModeInjectionIds)
        val activePresetConfig = parseActiveStPresetConfig(ctx.settings.tavernPresets, activeGroups)
        val activePersona = ctx.settings.personas
            .firstOrNull { p -> p.id == ctx.settings.activePersonaId && p.enabled }
        val effectiveUserName = activePersona?.name?.takeIf { it.isNotBlank() }
            ?: ctx.settings.displaySetting.userNickname.ifBlank { "User" }
        val userMsgCount = messages.count { it.role == MessageRole.USER }
        val anActiveThisTurn = ctx.settings.authorNoteEnabled &&
            ctx.settings.authorNote.isNotBlank() &&
            ctx.settings.authorNoteInterval > 0 &&
            userMsgCount > 0 &&
            (userMsgCount % ctx.settings.authorNoteInterval == 0)
        val result = transformMessages(
            messages = messages,
            assistant = ctx.assistant,
            // [第6轮 D1 方案 A] 两通道并集：预设仓（modeInjections）+ 提示词注入（promptInjections）。
            // 启用集共用 assistant.modeInjectionIds / conversation.modeInjectionIds（UUID 唯一），
            // 所以这里只需把两个列表合起来交给下游 filter(enabled && id in ids)。
            // promptInjections 为空时与旧行为**逐字等价**（+ emptyList()）。
            modeInjections = ctx.settings.modeInjections + ctx.settings.promptInjections,
            lorebooks = ctx.settings.lorebooks,
            regexScripts = mergeRegexScripts(
                ctx.settings.regexScripts,
                ctx.assistant,
                activeGroups,
                activePresetConfig.spresetEnabledRegexIds,
                activePresetConfig.spresetDisabledRegexIds,
            ),
            wiFormat = activePresetConfig.wiFormat,
            forbidOverridesMain = activePresetConfig.forbidOverridesMain,
            forbidOverridesJailbreak = activePresetConfig.forbidOverridesJailbreak,
            maxContextTokens = activePresetConfig.maxContext ?: 0,
            userName = effectiveUserName,
            orderedMarkers = activePresetConfig.orderedMarkers,
            disabledMarkers = activePresetConfig.disabledMarkers,
            conversationModeInjectionIds = ctx.conversationModeInjectionIds,
            conversationLorebookIds = ctx.conversationLorebookIds,
            activeStickyEntries = activeSticky,
            cooldownEntries = cooldowns,
            delayOverrideEntries = delayMap,
            authorNotePosition = ctx.settings.authorNotePosition,
            authorNoteDepth = ctx.settings.authorNoteDepth,
            authorNote = if (anActiveThisTurn) ctx.settings.authorNote else "",
            authorNoteRole = ctx.settings.authorNoteRole,
            worldInfoBudget = ctx.settings.worldInfoBudget,
            worldInfoBudgetCap = ctx.settings.worldInfoBudgetCap,
            worldInfoMinActivations = ctx.settings.worldInfoMinActivations,
            worldInfoMinActivationsDepthMax = ctx.settings.worldInfoMinActivationsDepthMax,
            worldInfoRecursive = ctx.settings.worldInfoRecursive,
            worldInfoMaxRecursionSteps = ctx.settings.worldInfoMaxRecursionSteps,
            worldInfoDepth = ctx.settings.worldInfoDepth,
            worldInfoCharacterStrategy = ctx.settings.worldInfoCharacterStrategy,
            worldInfoOverflowAlert = ctx.settings.worldInfoOverflowAlert,
            worldInfoUseGroupScoring = ctx.settings.worldInfoUseGroupScoring,
            // [v240 W5] 真 tokenizer 模式（cl100k / o200k / off）；off 或词表缺失时回退启发式
            tokenizerMode = ctx.settings.tokenizerMode,
            generationType = ctx.generationType,
            onOverflow = { ctx.processingStatus?.value = "世界书预算已满，部分条目未注入" },
            personaDescription = ctx.settings.personas
                .firstOrNull { p -> p.id == ctx.settings.activePersonaId && p.enabled }
                ?.description ?: "",
            // 官方 outlet（position=7）汇聚结果发布给宏引擎（{{outlet::name}} 读取）
            outletSink = { outlets ->
                WorldInfoOutlets.publish(ctx.conversationId?.toString(), outlets)
            },
            // [v228 S5] 世界书事件出口：会话级守卫 + developerMode 显式同步（[event] 日志可见）
            worldInfoEventSink = { name, payload ->
                me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.fireConversationEvent(
                    conversationId = ctx.conversationId?.toString(),
                    name = name,
                    payloadJson = payload,
                    developerMode = ctx.settings.developerMode,
                )
            },
        )

        return result
    }
}

/**
 * 核心注入逻辑（可测试的纯函数）
 */
internal fun transformMessages(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    activeStickyEntries: MutableMap<Uuid, Int> = mutableMapOf(),
    cooldownEntries: MutableMap<Uuid, Int> = mutableMapOf(),
    delayOverrideEntries: Map<Uuid, Int> = emptyMap(),
    authorNotePosition: AuthorNotePosition = AuthorNotePosition.IN_CHAT,
    authorNoteDepth: Int = 4,
    worldInfoBudget: Int = 25,
    worldInfoBudgetCap: Int = 0,
    worldInfoMinActivations: Int = 0,
    worldInfoMinActivationsDepthMax: Int = 0,
    worldInfoRecursive: Boolean = false,
    worldInfoMaxRecursionSteps: Int = 0,
    worldInfoDepth: Int = 2,
    worldInfoCharacterStrategy: Int = 1,
    worldInfoOverflowAlert: Boolean = false,
    worldInfoUseGroupScoring: Boolean = false,
    generationType: me.rerere.rikkahub.data.model.GenerationType = me.rerere.rikkahub.data.model.GenerationType.NORMAL,
    personaDescription: String = "",
    onOverflow: () -> Unit = {},
    regexScripts: List<RegexScript> = emptyList(),
    /**
     * 官方 outlet（position=7）汇聚结果回调：扫描结束后把 `outlet_name -> 内容(join "\n")`
     * 发布给宏引擎（`{{outlet::name}}`）。为空表表示本次未激活任何 outlet 条目。
     * 默认 null = 纯函数模式（测试不影响运行时状态）。
     */
    outletSink: ((Map<String, String>) -> Unit)? = null,
    /**
     * [v228 S5] 世界书事件出口（ST `WORLDINFO_ENTRIES_LOADED` / `WORLDINFO_SCAN_DONE`）。
     * null = 纯函数模式（单测不受影响）；运行时由 [PromptInjectionTransformer.transform] 注入。
     */
    worldInfoEventSink: ((String, String) -> Unit)? = null,
    /** [v240 W5] tokenizer 模式：cl100k / o200k / off（off 或词表缺失时回退启发式估算） */
    tokenizerMode: TokenizerMode = TokenizerMode.CL100K,
    wiFormat: String? = null,
    forbidOverridesMain: Boolean = false,
    forbidOverridesJailbreak: Boolean = false,
    maxContextTokens: Int = 0,
    userName: String = "User",
    orderedMarkers: List<String> = emptyList(),
    disabledMarkers: Set<String> = emptySet(),
    authorNote: String = "",
    authorNoteRole: MessageRole = MessageRole.SYSTEM,
): List<UIMessage> {
    // 收集所有需要注入的内容
    val injections = collectInjections(
        wiFormat = wiFormat,
        forbidOverridesMain = forbidOverridesMain,
        forbidOverridesJailbreak = forbidOverridesJailbreak,
        maxContextTokens = maxContextTokens,
        userName = userName,
        disabledMarkers = disabledMarkers,
        messages = messages,
        assistant = assistant,
        modeInjections = modeInjections,
        lorebooks = lorebooks,
        conversationModeInjectionIds = conversationModeInjectionIds,
        regexScripts = regexScripts,
        conversationLorebookIds = conversationLorebookIds,
        activeStickyEntries = activeStickyEntries,
        cooldownEntries = cooldownEntries,
        delayOverrideEntries = delayOverrideEntries,
        worldInfoBudget = worldInfoBudget,
        worldInfoBudgetCap = worldInfoBudgetCap,
        worldInfoMinActivations = worldInfoMinActivations,
        worldInfoMinActivationsDepthMax = worldInfoMinActivationsDepthMax,
        worldInfoRecursive = worldInfoRecursive,
        worldInfoMaxRecursionSteps = worldInfoMaxRecursionSteps,
        worldInfoDepth = worldInfoDepth,
        worldInfoCharacterStrategy = worldInfoCharacterStrategy,
        worldInfoOverflowAlert = worldInfoOverflowAlert,
        worldInfoUseGroupScoring = worldInfoUseGroupScoring,
        tokenizerMode = tokenizerMode,
        generationType = generationType,
        personaDescription = personaDescription,
        onOverflow = onOverflow,
        worldInfoEventSink = worldInfoEventSink,
    )

    if (injections.isEmpty()) {
        // 无注入时仍要推进粘性和冷却状态
        tickSticky(activeStickyEntries, cooldownEntries, emptyList())
        tickCooldowns(cooldownEntries)
        // 本次未激活任何条目 → 清除旧 outlet 快照（官方 extension_prompts 每次扫描重建）
        outletSink?.invoke(emptyMap())
        return messages
    }

    // 按位置分组：
    // - 预设条目（presetGroup != null）与世界书条目（RegexInjection）按 priority 升序保持 prompt_order / insertion_order；
    // - 用户自建独立 ModeInjection（presetGroup == null）按 priority 降序（高权重在前）。
    val sortedInjections = injections.sortedWith { a, b ->
        when {
            a is PromptInjection.ModeInjection && b is PromptInjection.ModeInjection &&
                a.presetGroup == null && b.presetGroup == null -> b.priority.compareTo(a.priority)
            else -> a.priority.compareTo(b.priority)
        }
    }
    val byPosition = sortedInjections.groupBy { it.position }

    // 官方 outlet（position=7）：自身不注入任何位置，按 outlet_name 汇聚（多个同名 "\n" 连接），
    // 发布给 {{outlet::name}} 宏（对齐 world-info.js setExtensionPrompt(CUSTOM_WI_OUTLET)）
    val outletValues = LinkedHashMap<String, MutableList<String>>()
    for (injection in sortedInjections) {
        if (injection !is PromptInjection.RegexInjection) continue
        if (injection.position != InjectionPosition.OUTLET) continue
        val name = injection.outletName.trim()
        if (name.isEmpty() || injection.content.isEmpty()) continue
        outletValues.getOrPut(name) { mutableListOf() }.add(injection.content)
    }
    outletSink?.invoke(outletValues.mapValues { (_, contents) -> contents.joinToString("\n") })

    // 应用注入
    val result = applyInjections(
        messages = messages,
        byPosition = byPosition,
        authorNotePosition = authorNotePosition,
        authorNoteDepth = authorNoteDepth,
        authorNote = authorNote,
        authorNoteRole = authorNoteRole,
        orderedMarkers = orderedMarkers,
    )

    // 推进粘性和冷却
    tickSticky(activeStickyEntries, cooldownEntries, injections.filterIsInstance<PromptInjection.RegexInjection>())
    tickCooldowns(cooldownEntries)

    return result
}

/**
 * 收集需要注入的内容
 */
internal fun collectInjections(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    activeStickyEntries: MutableMap<Uuid, Int> = mutableMapOf(),
    cooldownEntries: MutableMap<Uuid, Int> = mutableMapOf(),
    delayOverrideEntries: Map<Uuid, Int> = emptyMap(),
    worldInfoBudget: Int = 25,
    worldInfoBudgetCap: Int = 0,
    worldInfoMinActivations: Int = 0,
    worldInfoMinActivationsDepthMax: Int = 0,
    worldInfoRecursive: Boolean = false,
    worldInfoMaxRecursionSteps: Int = 0,
    worldInfoDepth: Int = 2,
    worldInfoCharacterStrategy: Int = 1,
    worldInfoOverflowAlert: Boolean = false,
    worldInfoUseGroupScoring: Boolean = false,
    generationType: me.rerere.rikkahub.data.model.GenerationType = me.rerere.rikkahub.data.model.GenerationType.NORMAL,
    personaDescription: String = "",
    onOverflow: () -> Unit = {},
    regexScripts: List<RegexScript> = emptyList(),
    /**
     * [v228 S5] 世界书事件出口（ST `WORLDINFO_ENTRIES_LOADED` / `WORLDINFO_SCAN_DONE`）。
     * null = 纯函数模式（单测不受影响）；运行时由 [PromptInjectionTransformer.transform] 注入。
     */
    worldInfoEventSink: ((String, String) -> Unit)? = null,
    /** [v240 W5] tokenizer 模式：cl100k / o200k / off（off 或词表缺失时回退启发式估算） */
    tokenizerMode: TokenizerMode = TokenizerMode.CL100K,
    wiFormat: String? = null,
    forbidOverridesMain: Boolean = false,
    forbidOverridesJailbreak: Boolean = false,
    maxContextTokens: Int = 0,
    userName: String = "User",
    disabledMarkers: Set<String> = emptySet(),
): List<PromptInjection> {
    val injections = mutableListOf<PromptInjection>()
    val effectiveModeInjectionIds = if (assistant.allowConversationPromptInjection) {
        conversationModeInjectionIds
    } else {
        assistant.modeInjectionIds
    }
    val effectiveLorebookIds = if (assistant.allowConversationPromptInjection) {
        conversationLorebookIds
    } else {
        assistant.lorebookIds
    }

    val cardSysPrompt = assistant.tavernData?.systemPrompt?.trim().orEmpty()
    val cardPhi = assistant.tavernData?.postHistoryInstructions?.trim().orEmpty()
    val firstSysMsgText = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.toText().orEmpty()
    val hasEmbeddedPhiInLorebook = lorebooks.any { b ->
        b.enabled && effectiveLorebookIds.contains(b.id) && b.isCharacterBook &&
            b.entries.any { e -> e.enabled && e.position == InjectionPosition.AFTER_DIALOG && e.name == "历史后续指令" }
    }
    var presetJailbreakBaseContent: String? = null

    // 1. 获取关联的 ModeInjection（同时按 generationType 过滤 injection_trigger，并处理 main / jailbreak 的 forbid_overrides 与 {{original}}）
    modeInjections
        .filter {
            it.enabled &&
                effectiveModeInjectionIds.contains(it.id) &&
                (it.triggers.isEmpty() || generationType.value in it.triggers)
        }
        .mapNotNull { inj ->
            val pGroup = inj.presetGroup
            if (!pGroup.isNullOrBlank()) {
                val mainId = Uuid.parse(java.util.UUID.nameUUIDFromBytes("$pGroup::main".toByteArray(Charsets.UTF_8)).toString())
                val jbId = Uuid.parse(java.util.UUID.nameUUIDFromBytes("$pGroup::jailbreak".toByteArray(Charsets.UTF_8)).toString())
                val nsfwId = Uuid.parse(java.util.UUID.nameUUIDFromBytes("$pGroup::nsfw".toByteArray(Charsets.UTF_8)).toString())
                if (inj.id == mainId) {
                    if ("main" in disabledMarkers) return@mapNotNull null
                    val effectiveForbidMain = forbidOverridesMain || inj.forbidOverrides
                    if (effectiveForbidMain) {
                        if (firstSysMsgText.isNotEmpty() && firstSysMsgText.contains(inj.content)) {
                            return@mapNotNull null
                        }
                        return@mapNotNull inj
                    }
                    if (cardSysPrompt.isNotEmpty()) {
                        val resolvedMain = if (cardSysPrompt.contains("{{original}}", ignoreCase = true)) {
                            cardSysPrompt.replace(Regex("\\{\\{\\s*original\\s*\\}\\}", RegexOption.IGNORE_CASE), inj.content)
                        } else {
                            cardSysPrompt
                        }
                        if (firstSysMsgText.isNotEmpty() && firstSysMsgText.contains(resolvedMain)) {
                            return@mapNotNull null
                        }
                        return@mapNotNull inj.copy(content = resolvedMain)
                    }
                    if (firstSysMsgText.isNotEmpty() && firstSysMsgText.contains(inj.content)) {
                        return@mapNotNull null
                    }
                }
                if (inj.id == jbId || inj.id == nsfwId) {
                    if ("jailbreak" in disabledMarkers && inj.id == jbId) return@mapNotNull null
                    if ("nsfw" in disabledMarkers && inj.id == nsfwId) return@mapNotNull null
                    if (presetJailbreakBaseContent == null || inj.id == jbId) {
                        presetJailbreakBaseContent = inj.content
                    }
                    val effectiveForbidJb = forbidOverridesJailbreak || inj.forbidOverrides
                    if (!effectiveForbidJb && cardPhi.isNotEmpty()) {
                        return@mapNotNull if (hasEmbeddedPhiInLorebook) {
                            null
                        } else {
                            val resolvedJb = if (cardPhi.contains("{{original}}", ignoreCase = true)) {
                                cardPhi.replace(Regex("\\{\\{\\s*original\\s*\\}\\}", RegexOption.IGNORE_CASE), inj.content)
                            } else {
                                cardPhi
                            }
                            inj.copy(content = resolvedJb)
                        }
                    }
                }
            }
            inj
        }
        .forEach { injections.add(it) }

    // 2. 获取关联的 Lorebook 中被触发的 RegexInjection。
    //    官方模型（world-info.js checkWorldInfo）：角色卡内嵌书在导入时转成独立外置书并绑定，
    //    注入只读外置绑定；解绑后不再注入。所有选中书的条目合并成一个列表统一扫描（全局设置）
    val enabledLorebooks = lorebooks.filter {
        it.enabled && effectiveLorebookIds.contains(it.id)
    }
    if (enabledLorebooks.isNotEmpty()) {
        // 提取上下文用于匹配（只取非 SYSTEM 消息）
        val nonSystemMessages = messages.filter { it.role != MessageRole.SYSTEM }

        // 官方 world-info.js：关键词扫描前先对消息内容执行正则（isMarkdown=false, isPrompt=true）
        val scanMessages = if (regexScripts.isEmpty()) {
            nonSystemMessages
        } else {
            nonSystemMessages.mapIndexed { index, message ->
                val regexDepth = nonSystemMessages.size - 1 - index
                message.copy(
                    parts = message.parts.map { part ->
                        if (part is UIMessagePart.Text) {
                            part.copy(
                                text = RegexScriptEngine.getRegexedString(
                                    rawString = part.text,
                                    scripts = regexScripts,
                                    placement = if (message.role == MessageRole.USER) RegexPlacement.USER_INPUT else RegexPlacement.AI_OUTPUT,
                                    isPrompt = true,
                                    depth = regexDepth,
                                )
                            )
                        } else {
                            part
                        }
                    }
                )
            }
        }
        // 官方 failedProbabilityChecks：本次扫描中概率未通过的条目，后续递归/补扫不再重新掷
        val failedProbabilityIds = mutableSetOf<Uuid>()
        // 官方 match_* 开关：按条目决定哪些角色卡字段纳入扫描（默认只扫聊天）
        val tav = assistant.tavernData
        fun buildCharScanContext(entry: PromptInjection.RegexInjection): String = buildString {
            if (tav == null) return@buildString
            if (entry.matchCharacterDescription && tav.description.isNotBlank()) appendLine(tav.description)
            if (entry.matchCharacterPersonality && tav.personality.isNotBlank()) appendLine(tav.personality)
            if (entry.matchScenario && tav.scenario.isNotBlank()) appendLine(tav.scenario)
            if (entry.matchCreatorNotes && tav.creatorNotes.isNotBlank()) appendLine(tav.creatorNotes)
            if (entry.matchCharacterDepthPrompt && tav.depthPrompt.isNotBlank()) appendLine(tav.depthPrompt)
            if (entry.matchPersonaDescription && personaDescription.isNotBlank()) appendLine(personaDescription)
        }.trim()

        // 官方 getSortedEntries（world-info.js:4515-4525）：条目拼装后先 parseDecorators 剥壳，
        // @@activate / @@dont_activate 存进 entry.decorators，content 换成剥壳后的文本，再按策略排序、
        // 参与关键词扫描。剥离只作用在「扫描副本」上：存储里的 content 原样保留 → 导入/导出仍然无损。
        val decoratorsByEntryId = mutableMapOf<Uuid, List<String>>()
        val sortedEntries = enabledLorebooks
            .flatMap { book ->
                book.entries.mapNotNull { entry ->
                    // 若预设 jailbreak 声明 forbid_overrides = true，抑制角色卡内嵌的 post_history_instructions 覆盖条目；
                    // 若 forbid_overrides = false 且条目包含 {{original}}，展开为预设 jailbreak 内容
                    if (book.isCharacterBook &&
                        entry.position == InjectionPosition.AFTER_DIALOG && entry.name == "历史后续指令"
                    ) {
                        val anyPresetForbidsJb = forbidOverridesJailbreak || modeInjections.any { m ->
                            m.enabled && effectiveModeInjectionIds.contains(m.id) && m.forbidOverrides && !m.presetGroup.isNullOrBlank() &&
                                (m.id == Uuid.parse(java.util.UUID.nameUUIDFromBytes("${m.presetGroup}::jailbreak".toByteArray(Charsets.UTF_8)).toString()) ||
                                    m.id == Uuid.parse(java.util.UUID.nameUUIDFromBytes("${m.presetGroup}::nsfw".toByteArray(Charsets.UTF_8)).toString()))
                        }
                        if (anyPresetForbidsJb) {
                            return@mapNotNull null
                        }
                        if (entry.content.contains("{{original}}", ignoreCase = true)) {
                            val expandedPhi = entry.content.replace(
                                Regex("\\{\\{\\s*original\\s*\\}\\}", RegexOption.IGNORE_CASE),
                                presetJailbreakBaseContent.orEmpty(),
                            )
                            return@mapNotNull book to entry.copy(content = expandedPhi)
                        }
                    }
                    val parsed = parseWorldInfoDecorators(entry.content)
                    // 官方无条件用 parseDecorators 的结果替换 content：
                    // 即使装饰器列表为空，未知 @@ 行 / @@@ 转义行也可能被剥掉；并应用扩展装饰器属性覆盖。
                    if (parsed.decorators.isEmpty() && parsed.content == entry.content) {
                        book to entry
                    } else {
                        decoratorsByEntryId[entry.id] = parsed.decorators
                        book to entry.copy(content = parsed.content).applyWorldInfoDecoratorOverrides(parsed.decorators)
                    }
                }
            }
            .let { pairs ->
                when (worldInfoCharacterStrategy) {
                    // 0 = evenly：全局与角色卡条目混排（官方 [...globalLore, ...characterLore].sort(sortFn)）
                    0 -> pairs.sortedWith(compareByDescending { it.second.priority })
                    // 1 = character_first：角色卡条目在前
                    1 -> pairs.sortedWith(
                        compareByDescending<Pair<Lorebook, PromptInjection.RegexInjection>> { it.first.isCharacterBook }
                            .thenByDescending { it.second.priority }
                    )
                    // 2 = global_first：全局条目在前
                    else -> pairs.sortedWith(
                        compareBy<Pair<Lorebook, PromptInjection.RegexInjection>> { it.first.isCharacterBook }
                            .thenByDescending { it.second.priority }
                    )
                }
            }

        // [v228 S5] ST WORLDINFO_ENTRIES_LOADED（world-info.js:4604，events.js:97）：
        //   全部世界书**加载完成、关键词扫描开始之前**，载荷 {globalLore, characterLore, chatLore, personaLore}。
        //   本地无 chat 级 / persona 级世界书（chatLore / personaLore 恒空数组，而不是编条目）。
        worldInfoEventSink?.invoke(
            TavernWorldInfoEvents.EVENT_ENTRIES_LOADED,
            TavernWorldInfoEvents.entriesLoadedPayload(
                globalLore = enabledLorebooks.filter { !it.isCharacterBook }.flatMap { it.entries },
                characterLore = enabledLorebooks.filter { it.isCharacterBook }.flatMap { it.entries },
            ),
        )
        // [v228 S5] sortedEntries 的 ST 形状 JSON 只构造一次（循环里每轮 SCAN_DONE 复用，避免大世界书重复序列化）。
        // ⚠️ 只有真的有人订阅（sink != null）才构造：没人订阅时纯函数路径零开销，
        //    也让 JVM 单测（WorldInfoOutletTest 等）不依赖 JSON 构造。
        val sortedEntriesJson = if (worldInfoEventSink != null) {
            TavernWorldInfoEvents.sortedEntriesJson(sortedEntries)
        } else {
            null
        }

        // 官方 checkWorldInfo 单循环共享状态：
        // allActivatedEntries（Map，key=world.uid 去重）、递归缓冲、failedProbabilityChecks、skew、预算
        val activatedEntries = mutableListOf<PromptInjection.RegexInjection>()
        val knownIds = mutableSetOf<Uuid>()
        var recursionContext = ""
        var skew = 0
        var currentLevel = 0
        var overflowed = false
        var count = 0
        // 官方 scan_state：INITIAL / RECURSION / MIN_ACTIVATIONS（官方 world_info_scan_type 枚举）
        var scanState = 0 // 0=INITIAL 1=RECURSION 2=MIN_ACTIVATIONS
        // 官方 availableRecursionDelayLevels：全部条目的 delay_until_recursion 去重升序，逐级开放
        val availableLevels = sortedEntries
            .map { it.second.delayUntilRecursion }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toMutableList()
        // 官方预算（world-info.js）：budget = round(world_info_budget% × maxContext / 100) || 1；cap > 0 时封顶。
        // maxContext 必须取模型/预设上下文窗口上限（默认 16384），绝不能用当前聊天消息实时 token 数作为分母（否则新对话只有几十 token 会导致正常世界书条目全部被判溢出丢弃）。
        val effectiveMaxContext = when {
            maxContextTokens > 0 -> maxContextTokens
            assistant.maxTokens != null && assistant.maxTokens > 0 -> maxOf(assistant.maxTokens * 4, 16384)
            else -> 16384
        }
        val budget = ((worldInfoBudget * effectiveMaxContext) / 100.0).roundToInt().coerceAtLeast(1)
            .let { if (worldInfoBudgetCap > 0 && it > worldInfoBudgetCap) worldInfoBudgetCap else it }

        // 官方 while (scanState)：INITIAL → (RECURSION / MIN_ACTIVATIONS / 层级开放) 循环
        // max_recursion_steps 语义（官方）：每轮开头检查 count >= 上限则停止，count 从 0 起算，
        // 即总扫描轮数上限 = max_recursion_steps；0 = 不限制
        while (worldInfoMaxRecursionSteps <= 0 || count < worldInfoMaxRecursionSteps) {
            count++
            val isRecursion = scanState == 1
            val newlyTriggered = mutableListOf<PromptInjection.RegexInjection>()
            // 触发时的关键词匹配分（酒馆 use_group_scoring 用）
            val triggeredScores = mutableMapOf<Uuid, Int>()

            for ((lorebook, entry) in sortedEntries) {
                // 官方：已激活条目和概率失败过的条目直接跳过
                if (knownIds.contains(entry.id) || failedProbabilityIds.contains(entry.id)) continue

                // 官方：disable 条目跳过（在 triggers 过滤之前；官方 disable 字段导入后映射为 enabled）
                if (!entry.enabled) continue

                // 官方 checkWorldInfo characterFilter：按角色名或标签黑白名单过滤
                if (!entry.matchesCharacterFilter(assistant)) continue

                // 生成类型过滤（酒馆 triggers）
                if (entry.triggers.isNotEmpty() && generationType.value !in entry.triggers) continue

                // 官方：delay 中的条目跳过（在 cooldown 之前，无豁免；支持 /wi-set-timed-effect effect=delay 动态覆写）
                val effectiveDelay = delayOverrideEntries[entry.id] ?: entry.delay
                if (effectiveDelay > 0 && nonSystemMessages.size < effectiveDelay) continue

                // 冷却中的条目跳过（官方 isCooldown && !isSticky：粘性豁免）
                if (cooldownEntries.containsKey(entry.id) && !activeStickyEntries.containsKey(entry.id)) continue

                // 官方：非递归扫描跳过所有 delay_until_recursion 条目（粘性豁免）
                if (entry.delayUntilRecursion > 0 &&
                    !isRecursion &&
                    !activeStickyEntries.containsKey(entry.id)
                ) {
                    continue
                }

                // 官方：递归扫描只放行层级 <= 当前开放层级的条目（粘性豁免）
                if (isRecursion &&
                    entry.delayUntilRecursion > currentLevel &&
                    !activeStickyEntries.containsKey(entry.id)
                ) {
                    continue
                }

                // 官方：exclude_recursion 条目在递归扫描中被跳过（官方还要求全局递归开关开启；
                // 全局递归关时 delay_until_recursion 层级开放也走 RECURSION 状态，但官方不视其为递归，不排除）
                if (isRecursion && worldInfoRecursive && entry.excludeRecursion && !activeStickyEntries.containsKey(entry.id)) continue

                // 官方 world-info.js:4763-4770：装饰器判定在关键词扫描之前、constant/sticky 之前。
                // @@activate → 无条件激活（仍走后续分组/概率/预算检查）；@@dont_activate → 直接跳过。
                val decorators = decoratorsByEntryId[entry.id].orEmpty()
                if (decorators.contains(WORLD_INFO_DECORATOR_ACTIVATE)) {
                    newlyTriggered.add(entry)
                    continue
                }
                if (decorators.contains(WORLD_INFO_DECORATOR_DONT_ACTIVATE)) {
                    continue
                }

                // 官方 WorldInfoBuffer.get：条目 scanDepth 优先，否则全局深度 + skew；
                // 官方 startDepth 恒为 0（advanceScan 只增 skew），min_activations 推进时整段重扫
                val depth = entry.scanDepth ?: (worldInfoDepth + skew)
                val chatContext = extractContextForMatching(scanMessages, depth, 0)
                // 官方 match_*：只把该条目开启的角色卡字段纳入扫描
                val entryCharScan = buildCharScanContext(entry)
                val context = buildString {
                    if (entryCharScan.isNotEmpty()) {
                        append(entryCharScan)
                        appendLine()
                    }
                    // 官方 buffer.get：递归缓冲拼入除 MIN_ACTIVATIONS 外的所有扫描
                    if (scanState != 2 && recursionContext.isNotEmpty()) {
                        append(recursionContext)
                        appendLine()
                    }
                    append(chatContext)
                }

                // CCv3 @@exclude_keys：只要扫描上下文命中任一排除词（含 constantActive 常驻条目，仅 @@activate 除外），本轮立即跳过
                if (entry.excludeKeys.isNotEmpty() &&
                    entry.matchesAnyKey(entry.excludeKeys, context)
                ) {
                    continue
                }

                // 官方：constant / 激活中 sticky 条目直接加入（constant 在前，sticky 在后）
                if (entry.constantActive || activeStickyEntries.containsKey(entry.id)) {
                    newlyTriggered.add(entry)
                    continue
                }

                if (entry.isTriggered(context, rollProbability = false)) {
                    newlyTriggered.add(entry)
                    triggeredScores[entry.id] = entry.matchedKeyScore(context)
                } else if (entry.vectorized && PromptInjectionTransformer.vectorWorldInfoEnabled) {
                    val docText = (entry.keywords + entry.secondaryKeys + listOf(entry.name, entry.content)).joinToString(" ")
                    val sim = PromptInjectionTransformer.computeVectorSimilarity(context, docText)
                    if (sim >= PromptInjectionTransformer.vectorThreshold) {
                        newlyTriggered.add(entry)
                        triggeredScores[entry.id] = (sim * 100.0).roundToInt().coerceAtLeast(1)
                    }
                }
            }

            // 官方：组选后逐条掷概率 + 预算（newEntries.sort：粘性优先，再按 sortedEntries 顺序）
            val found = selectGroupWinners(
                newlyTriggered = newlyTriggered,
                triggeredScores = triggeredScores,
                activeStickyEntries = activeStickyEntries,
                alreadyActivated = activatedEntries,
                globalUseGroupScoring = worldInfoUseGroupScoring,
            )
            val accepted = mutableListOf<PromptInjection.RegexInjection>()
            var pendingIgnoreBudget = found.count { it.ignoreBudget }
            // 官方 newContent：本轮概率已通过的条目内容（含预算溢出的，官方 += 在预算检查之前）
            var newContentTokens = 0
            for (entry in found.sortedWith(
                compareByDescending<PromptInjection.RegexInjection> { activeStickyEntries.containsKey(it.id) }
                    .thenByDescending { it.priority }
            )) {
                pendingIgnoreBudget -= if (entry.ignoreBudget) 1 else 0
                // 官方：预算溢出后非 ignoreBudget 条目不再注入（后面还有 ignoreBudget 则跳过，否则停止）
                if (overflowed && !entry.ignoreBudget) {
                    if (pendingIgnoreBudget > 0) continue else break
                }
                // 官方 verifyProbability：useProbability 且 <100 才掷；sticky 免掷；失败记入 failedProbabilityChecks
                if (entry.useProbability && entry.probability < 100 && !activeStickyEntries.containsKey(entry.id)) {
                    if (Random.nextInt(100) >= entry.probability) {
                        failedProbabilityIds.add(entry.id)
                        continue
                    }
                }
                if (!entry.ignoreBudget) {
                    // 官方预算检查：递归缓冲 token + 本轮内容 token >= 预算 → 溢出，该条目也不注入
                    if (countTokens(recursionContext, tokenizerMode) + newContentTokens +
                        countTokens(entry.content, tokenizerMode) >= budget
                    ) {
                        overflowed = true
                        if (worldInfoOverflowAlert) onOverflow()
                        newContentTokens += countTokens(entry.content, tokenizerMode)
                        continue
                    }
                    newContentTokens += countTokens(entry.content, tokenizerMode)
                }
                accepted.add(entry)
            }

            val newOnes = accepted.filter { it.id !in knownIds }
            newOnes.forEach { knownIds.add(it.id) }
            activatedEntries.addAll(newOnes)

            // 官方 successfulNewEntriesForRecursion：prevent_recursion 条目的内容不进递归缓冲，逐轮累积
            val newRecursionText = newOnes
                .filter { !it.preventRecursion }
                .joinToString("\n") { it.content }
            if (newRecursionText.isNotBlank()) {
                recursionContext = listOf(recursionContext, newRecursionText)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            }

            // 官方状态机：
            // 1. 本轮有成功新条目（不含 prevent_recursion）且未溢出 → 递归扫描
            var nextScanState = -1 // -1 = 无下一步（停止）
            if (worldInfoRecursive && !overflowed && newRecursionText.isNotBlank()) {
                nextScanState = 1
            }
            // 2. min_activations 扫描中且有递归缓冲 → 先递归一次（官方 buffer.hasRecurse() 分支）
            if (nextScanState == -1 && worldInfoRecursive && !overflowed &&
                scanState == 2 && recursionContext.isNotBlank()
            ) {
                nextScanState = 1
            }
            // 3. min_activations 未满足 → 扫描深度 +1 重扫（官方 buffer.advanceScan），
            //    深度超限（min_activations_depth_max 或全部消息）才停；min 扫描不带递归缓冲
            val minNotSatisfied = worldInfoMinActivations > 0 && activatedEntries.size < worldInfoMinActivations
            val overMaxDepth = (worldInfoMinActivationsDepthMax > 0 &&
                worldInfoDepth + skew > worldInfoMinActivationsDepthMax) ||
                (worldInfoDepth + skew > nonSystemMessages.size)
            if (nextScanState == -1 && !overflowed && minNotSatisfied && !overMaxDepth) {
                nextScanState = 2
                skew++
            }
            // 4. 还有未开放的 delay_until_recursion 层级 → 开放下一级继续扫描
            if (nextScanState == -1 && availableLevels.isNotEmpty()) {
                nextScanState = 1
                currentLevel = availableLevels.removeAt(0)
            }
            // [v228 S5] ST WORLDINFO_SCAN_DONE（world-info.js:5149-5175，events.js:98）：
            //   官方注释「after each scan loop」——**每轮扫描结束后发一次**（含最后一轮）。
            //   载荷形状逐字对齐 :5150-5174（state/new/activated/sortedEntries/recursionDelay/budget/timedEffects）。
            //   ⚠️ 本地是 fireEvent（单向）：ST 允许监听者改写 args，这里不回读（已在 KDoc 登记）。
            if (sortedEntriesJson != null) worldInfoEventSink?.invoke(
                TavernWorldInfoEvents.EVENT_SCAN_DONE,
                TavernWorldInfoEvents.scanDonePayload(
                    loopCount = count,
                    currentState = scanState,
                    nextState = nextScanState,
                    newAll = accepted,
                    newSuccessful = newOnes,
                    activatedEntries = activatedEntries,
                    activatedText = activatedEntries.joinToString("\n") { it.content },
                    sortedEntriesJson = sortedEntriesJson,
                    recursionLevel = currentLevel,
                    availableLevels = availableLevels.toList(),
                    budget = budget,
                    overflowed = overflowed,
                ),
            )
            if (nextScanState == -1) break
            scanState = nextScanState
        }

        val autoIds = activatedEntries.mapNotNull { it.automationId.trim().takeIf { id -> id.isNotEmpty() } }.distinct()
        PromptInjectionTransformer.recordActivatedAutomationIds("${assistant.id}:default", autoIds)

        val charName = assistant.tavernData?.nickname?.takeIf { it.isNotBlank() } ?: assistant.name
        val resolvedUserName = userName.ifBlank { "User" }
        val wiMacroSub: (String) -> String = { s ->
            s.replace("{{char}}", charName, ignoreCase = true)
                .replace("{{user}}", resolvedUserName, ignoreCase = true)
        }
        for (entry in activatedEntries) {
            if ("worldInfoBefore" in disabledMarkers && entry.position == InjectionPosition.BEFORE_CHARACTER) {
                continue
            }
            if ("worldInfoAfter" in disabledMarkers && entry.position == InjectionPosition.AFTER_CHARACTER) {
                continue
            }
            val regexedContent = if (regexScripts.isNotEmpty() && entry.content.isNotEmpty()) {
                RegexScriptEngine.getRegexedString(
                    rawString = entry.content,
                    scripts = regexScripts,
                    placement = RegexPlacement.WORLD_INFO,
                    isMarkdown = false,
                    isPrompt = true,
                    depth = entry.injectDepth,
                    substitute = wiMacroSub,
                )
            } else {
                entry.content
            }
            val formattedContent = if (entry.position != InjectionPosition.OUTLET) {
                formatWorldInfoContent(wiFormat, regexedContent)
            } else {
                regexedContent
            }
            val formattedEntry = if (formattedContent != entry.content) {
                entry.copy(content = formattedContent)
            } else {
                entry
            }
            injections.add(formattedEntry)
            handleStickyCooldown(entry, activeStickyEntries, cooldownEntries)
        }
    }

    return injections
}

/**
 * 官方 filterByInclusionGroups 的本地实现：
 * 1. 同组内有粘性条目 → 只保留粘性条目（官方 filterGroupsByTimedEffects）
 * 2. 本次运行已有同组条目被激活 → 整组跳过（官方 allActivatedEntries 检查）
 * 3. group_override 条目 → 取优先级（order）最高的
 * 4. use_group_scoring 生效 → 移除分数低于组内最高分的条目（官方仅移除非最高分，未开启评分的条目保留）
 * 5. 剩余条目按 group_weight 加权随机选 1 条
 *
 * 分组跨全部 Lorebook（官方 global/character/chat/persona 世界书共同参与分组）。
 */
private fun selectGroupWinners(
    newlyTriggered: List<PromptInjection.RegexInjection>,
    triggeredScores: Map<Uuid, Int>,
    activeStickyEntries: Map<Uuid, Int>,
    alreadyActivated: List<PromptInjection.RegexInjection>,
    globalUseGroupScoring: Boolean = false,
): List<PromptInjection.RegexInjection> {
    if (newlyTriggered.isEmpty()) return emptyList()

    val grouped = newlyTriggered
        .filter { it.group.isNotBlank() || it.inclusionGroup.isNotBlank() }
        .flatMap { entry ->
            val labels = buildList {
                if (entry.group.isNotBlank()) add(entry.group)
                entry.inclusionGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.let { addAll(it) }
            }.distinct()
            labels.map { label -> label to entry }
        }
        .groupBy({ it.first }, { it.second })
    val ungrouped = newlyTriggered.filter { it.group.isBlank() && it.inclusionGroup.isBlank() }
    val activated = mutableListOf<PromptInjection.RegexInjection>()
    activated.addAll(ungrouped)

    for ((_, entries) in grouped) {
        // 官方 filterGroupsByTimedEffects：组内粘性条目胜出，非粘性全部移除
        val stickyEntries = entries.filter { activeStickyEntries.containsKey(it.id) }
        if (stickyEntries.isNotEmpty()) {
            activated.addAll(stickyEntries)
            continue
        }

        // 官方：该组标签在本次扫描中已激活过任何条目 → 其余条目全部移除
        // （官方按 group 标签比对 allActivatedEntries，即使上轮的胜者本轮不再命中也要拦下）
        val alreadyActivatedLabels = alreadyActivated.flatMap { entry ->
            buildList {
                if (entry.group.isNotBlank()) add(entry.group)
                entry.inclusionGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.let { addAll(it) }
            }.distinct()
        }.toSet()
        if (entries.any { entry ->
                buildList {
                    if (entry.group.isNotBlank()) add(entry.group)
                    entry.inclusionGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.let { addAll(it) }
                }.any { it in alreadyActivatedLabels }
            }
        ) continue

        // 官方 filterGroupsByScoring：全局 use_group_scoring 与条目开关取或
        // 先移除“参与评分且分数低于组内最高”的条目（未参与评分的条目保留，随后仍参与 override/加权随机）
        val isScored = { entry: PromptInjection.RegexInjection -> entry.useGroupScoring || globalUseGroupScoring }
        val scored = entries.filter(isScored)
        val survivors = if (scored.isNotEmpty()) {
            val maxScore = scored.maxOf { triggeredScores[it.id] ?: 0 }
            entries.filter { !isScored(it) || (triggeredScores[it.id] ?: 0) == maxScore }
        } else {
            entries
        }

        // 官方 groupOverride：在评分幸存者中取 order（优先级）最高的覆盖条目
        val overrides = survivors.filter { it.groupOverride || it.groupPriority }
        val finalCandidates = if (overrides.isNotEmpty()) {
            listOf(overrides.maxByOrNull { it.priority } ?: overrides.first())
        } else {
            survivors
        }

        // 官方：加权随机选 1 条
        val totalWeight = finalCandidates.sumOf { it.groupWeight.toLong() }
        val selected = if (totalWeight <= 0) {
            finalCandidates.firstOrNull()
        } else {
            var roll = Random.nextLong(totalWeight)
            var picked: PromptInjection.RegexInjection? = null
            for (entry in finalCandidates) {
                roll -= entry.groupWeight.toLong()
                if (roll < 0) {
                    picked = entry
                    break
                }
            }
            picked ?: finalCandidates.first()
        }
        selected?.let { activated.add(it) }
    }
    return activated
}

/**
 * 估算文本 token 数（启发式，降级路径）。
 *
 * [v240 W5] 起真值由 [countTokens]（tiktoken BPE）提供；本函数保留为「关闭 / 词表缺失 /
 * 加载失败」时的回退，语义不变：中日韩字符按 1 token，其余字符按 4 字符 1 token（近似）。
 */
internal fun estimateTokens(text: String): Int = estimateTokensHeuristic(text)

/** 处理粘性和冷却状态 */
private fun handleStickyCooldown(
    entry: PromptInjection.RegexInjection,
    activeStickyEntries: MutableMap<Uuid, Int>,
    cooldownEntries: MutableMap<Uuid, Int>,
) {
    // 官方 setTimedEffectOfType：效果已存在时不重置计数（重复激活不延长粘性）
    if (entry.sticky > 0 && !activeStickyEntries.containsKey(entry.id)) {
        activeStickyEntries[entry.id] = entry.sticky
    }
    if (entry.cooldown > 0 && !cooldownEntries.containsKey(entry.id)) {
        cooldownEntries[entry.id] = entry.cooldown
    }
}

/** 推进粘性计数器：每次调用减1，到0时若条目有cooldown则自动进入冷却 */
private fun tickSticky(activeStickyEntries: MutableMap<Uuid, Int>, cooldownTracker: MutableMap<Uuid, Int>, entries: List<PromptInjection.RegexInjection>) {
    val entriesById = entries.associateBy { it.id }
    val toRemove = mutableListOf<Uuid>()
    for ((id, remaining) in activeStickyEntries) {
        if (remaining <= 1) {
            toRemove.add(id)
            // sticky 到期 → 自动设 cooldown（对齐酒馆）
            val entry = entriesById[id]
            if (entry != null && entry.cooldown > 0) {
                cooldownTracker[id] = entry.cooldown
            }
        } else {
            activeStickyEntries[id] = remaining - 1
        }
    }
    toRemove.forEach { activeStickyEntries.remove(it) }
}

/** 推进冷却计数器：每次调用减1，到0移除 */
private fun tickCooldowns(cooldownEntries: MutableMap<Uuid, Int>) {
    val toRemove = mutableListOf<Uuid>()
    for ((id, remaining) in cooldownEntries) {
        if (remaining <= 1) {
            toRemove.add(id)
        } else {
            cooldownEntries[id] = remaining - 1
        }
    }
    toRemove.forEach { cooldownEntries.remove(it) }
}

/**
 * 应用注入到消息列表
 */
private val WI_FORMAT_TEXT_MACRO_RE = Regex("""\{\{\s*text\s*\}\}""", RegexOption.IGNORE_CASE)

/**
 * 格式化世界书条目内容（同时支持官方 `{0}` 与 `{{text}}` 占位符，大小写不敏感）。
 */
internal fun formatWorldInfoContent(wiFormat: String?, content: String): String {
    if (wiFormat.isNullOrBlank() || content.isEmpty()) return content
    if (wiFormat == "{0}" || wiFormat.trim().equals("{{text}}", ignoreCase = true)) return content
    var out = wiFormat.replace("{0}", content)
    if (WI_FORMAT_TEXT_MACRO_RE.containsMatchIn(out)) {
        out = WI_FORMAT_TEXT_MACRO_RE.replace(out) { content }
    }
    return out
}

internal fun applyInjections(
    messages: List<UIMessage>,
    byPosition: Map<InjectionPosition, List<PromptInjection>>,
    authorNotePosition: AuthorNotePosition = AuthorNotePosition.IN_CHAT,
    authorNoteDepth: Int = 4,
    authorNote: String = "",
    authorNoteRole: MessageRole = MessageRole.SYSTEM,
    orderedMarkers: List<String> = emptyList(),
): List<UIMessage> {
    val result = messages.toMutableList()
    val charSlotMarkers = setOf("charDescription", "charPersonality", "scenario")

    // 若预设 orderedMarkers 显式指定了 dialogueExamples 在角色卡字段之前，按 orderedMarkers 调整两者相对顺序
    if (orderedMarkers.isNotEmpty()) {
        val exOrderIdx = orderedMarkers.indexOf("dialogueExamples")
        val firstCharOrderIdx = orderedMarkers.indexOfFirst { it in charSlotMarkers }
        if (exOrderIdx >= 0 && firstCharOrderIdx >= 0 && exOrderIdx < firstCharOrderIdx) {
            val exMsgs = result.filter { m -> m.annotations.any { it is UIMessageAnnotation.ExampleMessage } }
            val cardMsgs = result.filter { m -> m.annotations.any { it is UIMessageAnnotation.CharacterCardData } }
            if (exMsgs.isNotEmpty() && cardMsgs.isNotEmpty()) {
                val firstSpecialIdx = result.indexOfFirst { m ->
                    m.annotations.any { it is UIMessageAnnotation.ExampleMessage || it is UIMessageAnnotation.CharacterCardData }
                }
                if (firstSpecialIdx >= 0) {
                    result.removeAll { m ->
                        m.annotations.any { it is UIMessageAnnotation.ExampleMessage || it is UIMessageAnnotation.CharacterCardData }
                    }
                    result.addAll(firstSpecialIdx, exMsgs + cardMsgs)
                }
            }
        }
    }

    // 无示例消息时退化为系统消息之后（官方 story string 之后）
    val fallbackAfterSystem = result.indexOfFirst { it.role == MessageRole.SYSTEM }
        .let { if (it >= 0) it + 1 else 0 }

    fun resolveMarkerInsertIndex(markerName: String, defaultBeforeChar: Boolean): Int {
        val curCardIndices = result.indices.filter { idx ->
            result[idx].annotations.any { it is UIMessageAnnotation.CharacterCardData }
        }
        val curExampleIndices = result.indices.filter { idx ->
            result[idx].annotations.any { it is UIMessageAnnotation.ExampleMessage }
        }
        val markerIdx = orderedMarkers.indexOf(markerName)
        if (markerIdx >= 0) {
            val lastCharOrderIdx = orderedMarkers.indexOfLast { it in charSlotMarkers }
            val firstCharOrderIdx = orderedMarkers.indexOfFirst { it in charSlotMarkers }
            val exOrderIdx = orderedMarkers.indexOf("dialogueExamples")
            if (exOrderIdx >= 0 && markerIdx > exOrderIdx && curExampleIndices.isNotEmpty() &&
                (lastCharOrderIdx < 0 || exOrderIdx > lastCharOrderIdx || markerIdx < firstCharOrderIdx)
            ) {
                return curExampleIndices.last() + 1
            }
            if (lastCharOrderIdx >= 0 && markerIdx > lastCharOrderIdx && curCardIndices.isNotEmpty()) {
                return curCardIndices.last() + 1
            }
            if (firstCharOrderIdx >= 0 && markerIdx < firstCharOrderIdx && curCardIndices.isNotEmpty()) {
                return curCardIndices.first()
            }
        }
        return if (defaultBeforeChar) {
            if (curCardIndices.isNotEmpty()) curCardIndices.first() else fallbackAfterSystem
        } else {
            if (curCardIndices.isNotEmpty()) curCardIndices.last() + 1 else fallbackAfterSystem
        }
    }

    // 决定 worldInfoBefore 与 worldInfoAfter 的处理顺序（遵循 orderedMarkers）
    val wibOrder = orderedMarkers.indexOf("worldInfoBefore")
    val wiaOrder = orderedMarkers.indexOf("worldInfoAfter")
    val afterBeforeFirst = wibOrder >= 0 && wiaOrder >= 0 && wiaOrder < wibOrder

    fun insertBeforeCharBlock() {
        val beforeCharInjections = byPosition[InjectionPosition.BEFORE_CHARACTER]
        if (!beforeCharInjections.isNullOrEmpty()) {
            var insertIndex = resolveMarkerInsertIndex("worldInfoBefore", defaultBeforeChar = true)
            insertIndex = findSafeInsertIndex(result, insertIndex)
            createMergedInjectionMessages(beforeCharInjections).forEach { message ->
                result.add(insertIndex, message)
                insertIndex++
            }
        }
    }

    fun insertAfterCharBlock() {
        val afterCharInjections = byPosition[InjectionPosition.AFTER_CHARACTER]
        if (!afterCharInjections.isNullOrEmpty()) {
            var insertIndex = resolveMarkerInsertIndex("worldInfoAfter", defaultBeforeChar = false)
            insertIndex = findSafeInsertIndex(result, insertIndex)
            createMergedInjectionMessages(afterCharInjections).forEach { message ->
                result.add(insertIndex, message)
                insertIndex++
            }
        }
    }

    if (afterBeforeFirst) {
        insertAfterCharBlock()
        insertBeforeCharBlock()
    } else {
        insertBeforeCharBlock()
        insertAfterCharBlock()
    }

    // 处理 EM_TOP：第一条示例消息之前
    val emTopInjections = byPosition[InjectionPosition.EM_TOP]
    if (!emTopInjections.isNullOrEmpty()) {
        val currentExampleIndices = result.indices.filter { idx ->
            result[idx].annotations.any { it is UIMessageAnnotation.ExampleMessage }
        }
        var insertIndex = if (currentExampleIndices.isNotEmpty()) currentExampleIndices.first() else fallbackAfterSystem
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(emTopInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 EM_BOTTOM：最后一条示例消息之后
    val emBottomInjections = byPosition[InjectionPosition.EM_BOTTOM]
    if (!emBottomInjections.isNullOrEmpty()) {
        // EM_TOP 插入后重新定位示例消息（插入内容不带标记，索引可能已变化）
        val currentExampleIndices = result.indices.filter { idx ->
            result[idx].annotations.any { it is UIMessageAnnotation.ExampleMessage }
        }
        var insertIndex = if (currentExampleIndices.isNotEmpty()) currentExampleIndices.last() + 1 else fallbackAfterSystem
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(emBottomInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 找到系统消息的索引（通常是第一条）
    val systemIndex = result.indexOfFirst { it.role == MessageRole.SYSTEM }

    // 处理 BEFORE_SYSTEM_PROMPT 和 AFTER_SYSTEM_PROMPT
    if (systemIndex >= 0) {
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        if (beforeContent.isNotEmpty() || afterContent.isNotEmpty()) {
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }

            val newText = buildString {
                if (beforeContent.isNotEmpty()) {
                    append(beforeContent)
                    appendLine()
                }
                append(originalText)
                if (afterContent.isNotEmpty()) {
                    appendLine()
                    append(afterContent)
                }
            }

            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText)),
                isSynthetic = true,
            )
        }
    } else {
        // 没有系统消息时，创建一个新的系统消息
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        val combinedContent = buildString {
            if (beforeContent.isNotEmpty()) {
                append(beforeContent)
                if (afterContent.isNotEmpty()) appendLine()
            }
            if (afterContent.isNotEmpty()) {
                append(afterContent)
            }
        }

        if (combinedContent.isNotEmpty()) {
            result.add(0, UIMessage.system(combinedContent).copy(isSynthetic = true))
        }
    }

    // 处理 ANTAGONIZE：角色卡（系统消息）之后、第一条对话消息之前
    val antagonizeInjections = byPosition[InjectionPosition.ANTAGONIZE]
    if (!antagonizeInjections.isNullOrEmpty()) {
        var insertIndex = result.indexOfFirst { it.role != MessageRole.SYSTEM }
            .takeIf { it >= 0 } ?: result.size
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(antagonizeInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 TOP_OF_CHAT：在第一条用户消息之前插入
    val topInjections = byPosition[InjectionPosition.TOP_OF_CHAT]
    if (!topInjections.isNullOrEmpty()) {
        // 重新计算索引（因为可能插入了系统消息）
        var insertIndex = result.indexOfFirst { it.role == MessageRole.USER }
            .takeIf { it >= 0 } ?: result.size
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(topInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 BOTTOM_OF_CHAT：在最后一条消息之前插入
    val bottomInjections = byPosition[InjectionPosition.BOTTOM_OF_CHAT]
    if (!bottomInjections.isNullOrEmpty()) {
        var insertIndex = (result.size - 1).coerceAtLeast(0)
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(bottomInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 AFTER_DIALOG：在最后一条 AI 回复之后插入
    val afterDialogInjections = byPosition[InjectionPosition.AFTER_DIALOG]
    if (!afterDialogInjections.isNullOrEmpty()) {
        val lastAssistantIndex = result.indexOfLast { it.role == MessageRole.ASSISTANT }
        var insertIndex = if (lastAssistantIndex >= 0) lastAssistantIndex + 1 else result.size
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(afterDialogInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 AT_DEPTH：在指定深度位置插入（从最新消息往前数）
    // 按 injectDepth 分组，相同深度的合并，按深度从大到小处理（避免索引变化问题）
    val atDepthInjections = byPosition[InjectionPosition.AT_DEPTH]
    if (!atDepthInjections.isNullOrEmpty()) {
        val byDepth = atDepthInjections.groupBy { it.injectDepth }
        byDepth.keys.sortedDescending().forEach { depth ->
            val injections = byDepth[depth] ?: return@forEach
            // 官方 populationInjectionPrompts（openai.js:846-868）：同一 depth 内按角色固定顺序
            // [system, user, assistant] 生成多条消息（各角色内容 '\n' 连接），再整体 splice 到该深度。
            // 此处先按该角色序稳定排序（同角色保持 priority 升序），再交给合并函数。
            val ordered = injections.sortedWith(compareBy { atDepthRoleRank(it.role) })
            // 计算插入位置：result.size - depth，但要确保在有效范围内
            // depth=0 表示在最后一条消息之后，depth=1 表示在最后一条消息之前...
            var insertIndex = (result.size - depth).coerceIn(0, result.size)
            insertIndex = findSafeInsertIndex(result, insertIndex)
            createMergedInjectionMessages(ordered).forEach { message ->
                result.add(insertIndex, message)
                insertIndex++
            }
        }
    }

    // 处理 BEFORE_AUTHOR_NOTE (ANTop, position=2) / AUTHOR_NOTE 与 AFTER_AUTHOR_NOTE (ANBottom, position=3)：
    // 严格按「BEFORE_AUTHOR_NOTE → 作者备注本体 → AFTER_AUTHOR_NOTE」顺序注入。
    val anTopList = (byPosition[InjectionPosition.AUTHOR_NOTE].orEmpty() +
        byPosition[InjectionPosition.BEFORE_AUTHOR_NOTE].orEmpty()).sortedBy { it.priority }
    val anBottomList = byPosition[InjectionPosition.AFTER_AUTHOR_NOTE].orEmpty().sortedBy { it.priority }
    if (anTopList.isNotEmpty() || anBottomList.isNotEmpty()) {
        val existingAnIdx = result.indexOfFirst { msg ->
            msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }.startsWith("[Author's Note]")
        }
        if (existingAnIdx >= 0) {
            var topIdx = findSafeInsertIndex(result, existingAnIdx)
            val topMsgs = createMergedInjectionMessages(anTopList)
            topMsgs.forEach { msg ->
                result.add(topIdx, msg)
                topIdx++
            }
            val newAnIdx = result.indexOfFirst { msg ->
                msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }.startsWith("[Author's Note]")
            }.takeIf { it >= 0 } ?: topIdx
            var bottomIdx = (newAnIdx + 1).coerceIn(0, result.size)
            createMergedInjectionMessages(anBottomList).forEach { msg ->
                result.add(bottomIdx, msg)
                bottomIdx++
            }
        } else if (authorNote.isNotBlank()) {
            // 对齐官方 world-info.js (shouldWIAddPrompt)：仅当本轮激活 Author's Note（或已有 [Author's Note]）时，才注入 ANTop / Author's Note / ANBottom
            val targetIdx = when (authorNotePosition) {
                AuthorNotePosition.BEFORE_PROMPT -> 0
                AuthorNotePosition.IN_PROMPT -> {
                    (result.indexOfFirst { it.role == MessageRole.SYSTEM } + 1).coerceAtLeast(0)
                }
                AuthorNotePosition.IN_CHAT -> {
                    val d = authorNoteDepth.coerceAtLeast(0)
                    findSafeInsertIndex(result, (result.size - d).coerceIn(0, result.size))
                }
            }
            var curIdx = targetIdx.coerceIn(0, result.size)
            createMergedInjectionMessages(anTopList).forEach { msg ->
                result.add(curIdx, msg)
                curIdx++
            }
            if (authorNote.isNotBlank()) {
                val body = "[Author's Note]\n$authorNote"
                val noteMsg = when (authorNoteRole) {
                    MessageRole.ASSISTANT -> UIMessage.assistant(body)
                    MessageRole.USER -> UIMessage.user(body)
                    else -> UIMessage.system(body)
                }.copy(isSynthetic = true)
                result.add(curIdx, noteMsg)
                curIdx++
            }
            createMergedInjectionMessages(anBottomList).forEach { msg ->
                result.add(curIdx, msg)
                curIdx++
            }
        }
    }

    return result
}

/**
 * 将相邻且相同 role 的注入合并成消息列表（Run-Length Encoding），
 * 严禁使用全局 groupBy { it.role } 跨不同角色跳跃归组，确保预设 SYSTEM -> USER -> ASSISTANT -> SYSTEM 交错顺序完全保留。
 */
private fun createMergedInjectionMessages(injections: List<PromptInjection>): List<UIMessage> {
    if (injections.isEmpty()) return emptyList()
    val result = mutableListOf<UIMessage>()
    var currentRole: MessageRole? = null
    val currentContents = mutableListOf<String>()

    fun flushGroup() {
        val role = currentRole ?: return
        if (currentContents.isEmpty()) return
        val mergedContent = currentContents.joinToString("\n")
        val msg = when (role) {
            MessageRole.ASSISTANT -> UIMessage.assistant(mergedContent)
            MessageRole.SYSTEM -> UIMessage.system(mergedContent)
            else -> UIMessage.user(mergedContent)
        }.copy(isSynthetic = true)
        result.add(msg)
        currentContents.clear()
    }

    for (inj in injections) {
        if (inj.role != currentRole) {
            flushGroup()
            currentRole = inj.role
        }
        currentContents.add(inj.content)
    }
    flushGroup()
    return result
}

/**
 * 查找安全的插入位置，避免注入到 USER → ASSISTANT(含Tool) 之间
 *
 * 某些提供商（如 deepseek）要求 USER 之后紧跟带工具的 ASSISTANT，
 * 在两者之间插入消息会导致报错或破坏推理连续性。
 */
internal fun findSafeInsertIndex(messages: List<UIMessage>, targetIndex: Int): Int {
    var index = targetIndex.coerceIn(0, messages.size)

    // 向前查找，直到找到一个安全的位置
    while (index > 0) {
        val prevMessage = messages.getOrNull(index - 1)
        val currentMessage = messages.getOrNull(index)

        // 不能插入到 USER → ASSISTANT(含Tool) 之间
        val isPrevUser = prevMessage?.role == MessageRole.USER
        val isCurrentAssistantWithTools = currentMessage?.role == MessageRole.ASSISTANT
            && currentMessage.getTools().isNotEmpty()

        if (isPrevUser && isCurrentAssistantWithTools) {
            index--
        } else {
            break
        }
    }

    return index
}

/**
 * 官方 AT_DEPTH 同深度多角色消息顺序（openai.js:847 `const roles = ['system','user','assistant']`）。
 * 同深度内若同时有多个角色命中，落位顺序必须是 system → user → assistant，
 * 否则提示词结构与 ST 不一致（宿主旧实现按遇到顺序，已修正）。
 */
private fun atDepthRoleRank(role: MessageRole): Int = when (role) {
    MessageRole.SYSTEM -> 0
    MessageRole.USER -> 1
    MessageRole.ASSISTANT -> 2
    else -> 3
}
