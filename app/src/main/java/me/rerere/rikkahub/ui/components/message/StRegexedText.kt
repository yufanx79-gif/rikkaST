package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.st.macro.DynamicMacro
import me.rerere.rikkahub.data.st.macro.MacroDefinitions
import me.rerere.rikkahub.data.st.macro.MacroEngine
import me.rerere.rikkahub.data.st.macro.MacroEnv
import me.rerere.rikkahub.data.st.macro.MacroRegistry
import me.rerere.rikkahub.data.st.macro.MacroVarKeyOps
import me.rerere.rikkahub.data.st.macro.MacroVariableStore
import me.rerere.rikkahub.data.st.macro.VariableStores
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore

internal data class StRegexRenderResult(
    val text: String,
    val isComputing: Boolean,
)

private val QUICK_STRIP_BLOCKS_RE = Regex(
    """<(style|script|UpdateVariable|Analysis|JSONPatch|thinking|TASK_CHECK_PASS|Interleaving)\b[^>]*>[\s\S]*?</\1>""",
    RegexOption.IGNORE_CASE,
)
private val QUICK_STRIP_TAGS_RE = Regex("""<[^>]+>""")
private val QUICK_HTML_COMMENT_RE = Regex("""<!--[\s\S]*?-->""")
private val RESIDUAL_THINK_STRIP_RE = Regex(
    """<(think|thinking|thought)\b[^>]*>[\s\S]*?(?:</\1>|\z)""",
    RegexOption.IGNORE_CASE,
)

/**
 * 快速检测文本是否可能命中 JS 侧 `MessageFormatter` 扩展钩子（如 ST-iPhonie 的 `<tts>`、`<pic>`、`<bgm>`、`<sfx>`、`<phone>` 或自定义测试钩子）。
 * 若当前文本不包含任何扩展多媒体/协议标签且未注入测试 evaluator，则无需跨线程等待 WebView IPC 往返，
 * 直接在 Kotlin 侧同步执行 `RegexScriptEngine`（耗时 < 2ms），彻底消除「正在应用显示正则与预设排版规则…」长等待！
 */
internal fun shouldInvokeWebViewMessageFormatter(rawText: String): Boolean {
    if (!TavernRuntimeManager.hasMessageFormatterHooks) return false
    if (TavernRuntimeManager.customMessageFormatterEvaluator != null) return true
    if (rawText.isEmpty()) return false
    if (rawText.contains("<tts", ignoreCase = true) ||
        rawText.contains("</tts>", ignoreCase = true) ||
        rawText.contains("<pic", ignoreCase = true) ||
        rawText.contains("<bgm", ignoreCase = true) ||
        rawText.contains("<sfx", ignoreCase = true) ||
        rawText.contains("<phone", ignoreCase = true) ||
        rawText.contains("data-sttts", ignoreCase = true) ||
        rawText.contains("sttts-", ignoreCase = true)
    ) {
        return true
    }
    return false
}

/**
 * 当 >50KB 超长开场白在后台计算正则期间（或开场白选择器预览时），快速提取可读纯文字正文，杜绝返回 "" 空串导致白条或裸露 CSS。
 */
internal fun extractQuickPlainPreview(rawText: String): String {
    return rawText
        .replace(QUICK_HTML_COMMENT_RE, "")
        .replace(QUICK_STRIP_BLOCKS_RE, "")
        .replace(QUICK_STRIP_TAGS_RE, "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/**
 * 进程级 LRU 缓存：避免超长消息（如 218KB 的大小姐开场白）在 LazyColumn 回收重建或 Settings 发射时
 * 反复执行耗时正则替换（实测单次可达 1.1 秒）。
 *
 * 关键优化（解决在预设消息之后中间楼层发送消息时卡死与 WebView 闪退）：
 * - 发送新消息会使 `lastMessageIndex` 递增，导致历史所有楼层的 `depth = lastMessageIndex - index` 全部变化。
 * - 若当前生效的 `scripts` 没有任何脚本配置了深度过滤（`minDepth > 0` 或 `maxDepth != null`），
 *   `keyOf` 将 `effectiveDepth` 归一化为 `null`；同时对已渲染过的同一消息保留 `lastRenderedText` 作为过渡初值，
 *   彻底杜绝每次点击发送时全会话所有楼层缓存集体失效、所有内嵌 WebView 面板被瞬间销毁并二次重建引发的卡死与崩溃。
 */
internal object StRegexTextCache {
    private const val MAX_ENTRIES = 160

    data class CacheKey(
        val textHash: Int,
        val textLen: Int,
        val scripts: List<RegexScript>,
        val assistantId: String?,
        val assistantName: String?,
        val assistantRegexes: List<AssistantRegex>,
        val scope: AssistantAffectScope,
        val placement: Int,
        val depth: Int?,
        val userName: String?,
        val conversationId: String?,
        val varVersion: Long = 0L,
        val formatterVersion: Long = 0L,
    )

    private val map = java.util.LinkedHashMap<CacheKey, Pair<String, String>>(32, 0.75f, true)

    fun scriptsUseDepthFilter(scripts: List<RegexScript>, placement: Int): Boolean {
        if (scripts.isEmpty()) return false
        return scripts.any { s ->
            !s.disabled &&
                (s.placement.isEmpty() || placement in s.placement) &&
                ((s.minDepth != null && s.minDepth > 0) || s.maxDepth != null)
        }
    }

    fun keyOf(
        text: String,
        scripts: List<RegexScript>,
        assistant: Assistant?,
        scope: AssistantAffectScope,
        placement: Int,
        depth: Int? = null,
        userName: String? = null,
        conversationId: String? = null,
        varVersion: Long = TavernVariableStore.mutationVersion,
        formatterVersion: Long = TavernRuntimeManager.messageFormatterVersion,
    ): CacheKey {
        val effectiveDepth = if (depth != null && scriptsUseDepthFilter(scripts, placement)) depth else null
        return CacheKey(
            textHash = text.hashCode(),
            textLen = text.length,
            scripts = scripts,
            assistantId = assistant?.id?.toString(),
            assistantName = assistant?.tavernData?.nickname?.takeIf { it.isNotBlank() } ?: assistant?.name,
            assistantRegexes = assistant?.regexes.orEmpty(),
            scope = scope,
            placement = placement,
            depth = effectiveDepth,
            userName = userName,
            conversationId = conversationId,
            varVersion = varVersion,
            formatterVersion = formatterVersion,
        )
    }

    fun get(key: CacheKey, rawText: String): String? = synchronized(map) {
        val hit = map[key] ?: return null
        if (hit.first == rawText) hit.second else null
    }

    fun put(key: CacheKey, rawText: String, rendered: String) {
        synchronized(map) {
            map[key] = rawText to rendered
            while (map.size > MAX_ENTRIES) {
                val it = map.keys.iterator()
                if (!it.hasNext()) break
                it.next()
                it.remove()
            }
        }
    }

    fun clear() {
        synchronized(map) {
            map.clear()
        }
    }
}

private val CHAR_MACRO_REGEX = Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE)
private val USER_MACRO_REGEX = Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE)
private val GETVAR_MACRO_REGEX = Regex("""\{\{\s*getvar::([^}]+)\}\}""", RegexOption.IGNORE_CASE)
private val GETGLOBALVAR_MACRO_REGEX = Regex("""\{\{\s*getglobalvar::([^}]+)\}\}""", RegexOption.IGNORE_CASE)

private val macroDefsInitialized: Boolean by lazy {
    MacroDefinitions.registerAll()
    true
}

private class TavernVarMacroStoreAdapter(
    private val conversationId: String?,
) : MacroVariableStore {
    override fun get(name: String): String? = TavernVariableStore.macroGet(conversationId, name)
    override fun has(name: String): Boolean = TavernVariableStore.macroGet(conversationId, name) != null
    override fun set(name: String, value: String) {
        TavernVariableStore.macroSet(conversationId, name, value)
    }
    override fun del(name: String) {
        TavernVariableStore.macroDelete(conversationId, name)
    }
    override fun inc(name: String): String {
        val next = (TavernVariableStore.macroGet(conversationId, name)?.toDoubleOrNull() ?: 0.0) + 1.0
        val formatted = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
        TavernVariableStore.macroSet(conversationId, name, formatted)
        return formatted
    }
    override fun dec(name: String): String {
        val next = (TavernVariableStore.macroGet(conversationId, name)?.toDoubleOrNull() ?: 0.0) - 1.0
        val formatted = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
        TavernVariableStore.macroSet(conversationId, name, formatted)
        return formatted
    }
    override fun add(name: String, value: String) {
        val cur = TavernVariableStore.macroGet(conversationId, name)
        val curNum = cur?.toDoubleOrNull()
        val addNum = value.toDoubleOrNull()
        val next = if (curNum != null && addNum != null) {
            val s = curNum + addNum
            if (s % 1.0 == 0.0) s.toLong().toString() else s.toString()
        } else {
            (cur ?: "") + value
        }
        TavernVariableStore.macroSet(conversationId, name, next)
    }
    override fun getAtKey(name: String, key: String): String? =
        MacroVarKeyOps.getAtKey(TavernVariableStore.macroGet(conversationId, name), key)
    override fun setAtKey(name: String, key: String, value: String) {
        val updated = MacroVarKeyOps.setAtKey(TavernVariableStore.macroGet(conversationId, name), key, value)
        TavernVariableStore.macroSet(conversationId, name, updated)
    }
}

private fun buildMacroSubstitutor(
    charName: String,
    effectiveUser: String,
    conversationId: String?,
): (String) -> String = { s ->
    if (!s.contains("{{") && !s.contains("<USER>", ignoreCase = true) && !s.contains("<BOT>", ignoreCase = true) && !s.contains("<CHAR>", ignoreCase = true)) {
        s
    } else {
        var out = s
        if (charName.isNotEmpty()) {
            out = CHAR_MACRO_REGEX.replace(out, charName)
        }
        out = USER_MACRO_REGEX.replace(out, effectiveUser)
        out = GETVAR_MACRO_REGEX.replace(out) { m ->
            val varKey = m.groupValues[1].trim()
            TavernVariableStore.macroGet(conversationId, varKey).orEmpty()
        }
        out = GETGLOBALVAR_MACRO_REGEX.replace(out) { m ->
            val varKey = m.groupValues[1].trim()
            TavernVariableStore.macroGet(null, varKey).orEmpty()
        }
        if (out.contains("{{") || out.contains("<USER>", ignoreCase = true) || out.contains("<BOT>", ignoreCase = true) || out.contains("<CHAR>", ignoreCase = true)) {
            runCatching {
                @Suppress("UNUSED_EXPRESSION") macroDefsInitialized
                val dyn = LinkedHashMap<String, DynamicMacro>()
                for ((name, entry) in TavernRuntimeManager.macrosRegistry.get()) {
                    val key = name.lowercase()
                    if (key !in dyn && !MacroRegistry.hasMacro(key) && entry.first != "function") {
                        dyn[key] = DynamicMacro.Value(entry.second)
                    }
                }
                val env = MacroEnv(
                    names = MacroEnv.Names(
                        user = effectiveUser,
                        char = charName.ifEmpty { "Assistant" },
                        group = charName.ifEmpty { "Assistant" },
                        groupNotMuted = charName.ifEmpty { "Assistant" },
                        notChar = effectiveUser,
                    ),
                    variables = VariableStores(
                        local = TavernVarMacroStoreAdapter(conversationId),
                        global = TavernVarMacroStoreAdapter(null),
                    ),
                    dynamicMacros = dyn,
                )
                out = MacroEngine.evaluate(out, env)
            }
        }
        out
    }
}

/**
 * 纯 Kotlin 侧同步执行助手正则与酒馆 RegexScriptEngine（不阻塞等待 WebView IPC）：
 * 耗时通常 < 2ms，可在首帧直接提供排版后 HTML/Markdown，避免出现「正在应用显示正则与预设排版规则…」等待条。
 */
internal fun computePureKotlinRegexedText(
    rawInputText: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): String {
    val text = if (scope == AssistantAffectScope.ASSISTANT &&
        (rawInputText.contains("<think", ignoreCase = true) || rawInputText.contains("<thought", ignoreCase = true))
    ) {
        RESIDUAL_THINK_STRIP_RE.replace(rawInputText, "").trimStart('\r', '\n')
    } else {
        rawInputText
    }
    val charName = assistant?.tavernData?.nickname?.takeIf { it.isNotBlank() }
        ?: assistant?.name?.takeIf { it.isNotBlank() }
        ?: ""
    val effectiveUser = userName?.takeIf { it.isNotBlank() } ?: "User"
    val sub = buildMacroSubstitutor(charName, effectiveUser, conversationId)
    val afterAssistantRegex = text.replaceRegexes(assistant = assistant, scope = scope, visual = true)
    return RegexScriptEngine.getRegexedString(
        rawString = afterAssistantRegex,
        scripts = scripts,
        placement = placement,
        isMarkdown = true,
        isPrompt = false,
        depth = depth,
        substitute = sub,
    )
}

internal fun computeStRegexedText(
    rawInputText: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): String {
    val text = if (scope == AssistantAffectScope.ASSISTANT &&
        (rawInputText.contains("<think", ignoreCase = true) || rawInputText.contains("<thought", ignoreCase = true))
    ) {
        RESIDUAL_THINK_STRIP_RE.replace(rawInputText, "").trimStart('\r', '\n')
    } else {
        rawInputText
    }
    val cacheKey = StRegexTextCache.keyOf(
        text = text,
        scripts = scripts,
        assistant = assistant,
        scope = scope,
        placement = placement,
        depth = depth,
        userName = userName,
        conversationId = conversationId,
    )
    StRegexTextCache.get(cacheKey, text)?.let { return it }
    val charName = assistant?.tavernData?.nickname?.takeIf { it.isNotBlank() }
        ?: assistant?.name?.takeIf { it.isNotBlank() }
        ?: ""
    val effectiveUser = userName?.takeIf { it.isNotBlank() } ?: "User"
    val sub = buildMacroSubstitutor(charName, effectiveUser, conversationId)
    val isUserMsg = (placement == 1)
    val invokeFormatter = shouldInvokeWebViewMessageFormatter(text)
    val beforeRegexText = if (invokeFormatter) {
        TavernRuntimeManager.runMessageFormatterStageBlocking("beforeRegex", text, charName, isUserMsg)
    } else {
        text
    }
    val afterAssistantRegex = beforeRegexText.replaceRegexes(assistant = assistant, scope = scope, visual = true)
    val regexed = RegexScriptEngine.getRegexedString(
        rawString = afterAssistantRegex,
        scripts = scripts,
        placement = placement,
        isMarkdown = true,
        isPrompt = false,
        depth = depth,
        substitute = sub,
    )
    val rendered = if (invokeFormatter || shouldInvokeWebViewMessageFormatter(regexed)) {
        val afterRegex = TavernRuntimeManager.runMessageFormatterStageBlocking("afterRegex", regexed, charName, isUserMsg)
        TavernRuntimeManager.runMessageFormatterStageBlocking("afterMarkdown", afterRegex, charName, isUserMsg)
    } else {
        regexed
    }
    StRegexTextCache.put(cacheKey, text, rendered)
    return rendered
}

/**
 * 挂起式（非阻塞）消息正则与 ST 1.19.0 MessageFormatter 三阶段管线：
 * 供 Compose `produceState`（后台 `Dispatchers.Default`）或协程直接调用，
 * 通过 `runMessageFormatterStageAsync` 挂起等待 WebView JS 钩子完成，
 * 彻底消除主线程 `Looper.getMainLooper()` 提前返回导致 `MessageFormatter` 钩子被绕过的缺陷。
 */
internal suspend fun computeStRegexedTextAsync(
    text: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): String {
    val rawInputText = text
    val cleanedText = if (scope == AssistantAffectScope.ASSISTANT &&
        (rawInputText.contains("<think", ignoreCase = true) || rawInputText.contains("<thought", ignoreCase = true))
    ) {
        RESIDUAL_THINK_STRIP_RE.replace(rawInputText, "").trimStart('\r', '\n')
    } else {
        rawInputText
    }
    val cacheKey = StRegexTextCache.keyOf(
        text = cleanedText,
        scripts = scripts,
        assistant = assistant,
        scope = scope,
        placement = placement,
        depth = depth,
        userName = userName,
        conversationId = conversationId,
    )
    StRegexTextCache.get(cacheKey, cleanedText)?.let { return it }
    val charName = assistant?.tavernData?.nickname?.takeIf { it.isNotBlank() }
        ?: assistant?.name?.takeIf { it.isNotBlank() }
        ?: ""
    val effectiveUser = userName?.takeIf { it.isNotBlank() } ?: "User"
    val sub = buildMacroSubstitutor(charName, effectiveUser, conversationId)
    val isUserMsg = (placement == 1)
    val invokeFormatter = shouldInvokeWebViewMessageFormatter(cleanedText)
    val beforeRegexText = if (invokeFormatter) {
        TavernRuntimeManager.runMessageFormatterStageAsync("beforeRegex", cleanedText, charName, isUserMsg)
    } else {
        cleanedText
    }
    val afterAssistantRegex = beforeRegexText.replaceRegexes(assistant = assistant, scope = scope, visual = true)
    val regexed = RegexScriptEngine.getRegexedString(
        rawString = afterAssistantRegex,
        scripts = scripts,
        placement = placement,
        isMarkdown = true,
        isPrompt = false,
        depth = depth,
        substitute = sub,
    )
    val rendered = if (invokeFormatter || shouldInvokeWebViewMessageFormatter(regexed)) {
        val afterRegex = TavernRuntimeManager.runMessageFormatterStageAsync("afterRegex", regexed, charName, isUserMsg)
        TavernRuntimeManager.runMessageFormatterStageAsync("afterMarkdown", afterRegex, charName, isUserMsg)
    } else {
        regexed
    }
    StRegexTextCache.put(cacheKey, cleanedText, rendered)
    return rendered
}

/**
 * 显示通道非阻塞渐进式正则与 MessageFormatter 管线（带首帧同步计算加速与稳定过渡保持）：
 * - 对于常规长度（<= 50KB）消息：
 *   1. 若无需触发 WebView `MessageFormatter` 钩子（例如未包含 `<tts>` / `<pic>` / `<bgm>` 等扩展标签）：
 *      直接在首帧同步执行 Kotlin `RegexScriptEngine` 并写入缓存返回（`isComputing = false`，耗时 < 2ms），0 延迟秒出！
 *   2. 若需要触发 WebView `MessageFormatter` 钩子：首帧先同步给出 Kotlin `RegexScriptEngine` 排版结果（`isComputing = false`），
 *      同时在后台协程通过 `computeStRegexedTextAsync` 执行 WebView `MessageFormatter` 钩子并无感更新。
 * - 仅当遇到 > 50KB 的超巨型原始消息且尚未命中缓存时，才展示短暂的「正在应用显示正则与预设排版规则…」提示条。
 */
@Composable
internal fun rememberStRegexedTextWithStatus(
    text: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): StRegexRenderResult {
    val formatterVersion by TavernRuntimeManager.messageFormatterVersionFlow.collectAsState()
    val hasFormatterHooks = TavernRuntimeManager.hasMessageFormatterHooks
    if (scripts.isEmpty() && assistant?.regexes.isNullOrEmpty() && !hasFormatterHooks) {
        return StRegexRenderResult(text = text, isComputing = false)
    }
    val lastRenderedHolder = remember(text) { mutableStateOf<String?>(null) }
    val varVersion = TavernVariableStore.mutationVersion
    val cacheKey = remember(
        text,
        scripts,
        assistant,
        scope,
        placement,
        depth,
        userName,
        conversationId,
        varVersion,
        formatterVersion,
        hasFormatterHooks,
    ) {
        StRegexTextCache.keyOf(
            text = text,
            scripts = scripts,
            assistant = assistant,
            scope = scope,
            placement = placement,
            depth = depth,
            userName = userName,
            conversationId = conversationId,
            varVersion = varVersion,
            formatterVersion = formatterVersion,
        )
    }
    val cached = remember(cacheKey, text) {
        StRegexTextCache.get(cacheKey, text)
    }
    if (cached != null) {
        lastRenderedHolder.value = cached
        return StRegexRenderResult(text = cached, isComputing = false)
    }

    val needsWebViewFormatter = remember(text, hasFormatterHooks, formatterVersion) {
        shouldInvokeWebViewMessageFormatter(text)
    }

    // 快速通道 1：<= 50KB 且无需等待 WebView MessageFormatter 钩子 → 首帧同步计算并返回，0ms 等待
    if (!needsWebViewFormatter && text.length <= 50_000) {
        val syncResult = remember(cacheKey, text, scripts, assistant, scope, placement, depth, userName, conversationId) {
            computeStRegexedText(text, scripts, assistant, scope, placement, depth, userName, conversationId)
        }
        lastRenderedHolder.value = syncResult
        return StRegexRenderResult(text = syncResult, isComputing = false)
    }

    // 快速通道 2：<= 50KB 但包含 <tts>/<pic> 等需 WebView MessageFormatter 处理的标签 →
    // 首帧先同步执行纯 Kotlin 正则（isComputing = false，立刻呈现排版界面），后台异步追加 MessageFormatter 钩子结果
    val initialPreview = remember(cacheKey, text) {
        StRegexTextCache.get(cacheKey, text)?.let {
            lastRenderedHolder.value = it
            StRegexRenderResult(text = it, isComputing = false)
        } ?: lastRenderedHolder.value?.let { prev ->
            StRegexRenderResult(text = prev, isComputing = false)
        } ?: if (text.length <= 50_000) {
            val fastKotlin = computePureKotlinRegexedText(
                rawInputText = text,
                scripts = scripts,
                assistant = assistant,
                scope = scope,
                placement = placement,
                depth = depth,
                userName = userName,
                conversationId = conversationId,
            )
            lastRenderedHolder.value = fastKotlin
            StRegexRenderResult(text = fastKotlin, isComputing = false)
        } else {
            StRegexRenderResult(
                text = extractQuickPlainPreview(text).ifBlank { text.take(1000) },
                isComputing = true,
            )
        }
    }
    val asyncResult by produceState(
        initialValue = initialPreview,
        cacheKey,
        text,
    ) {
        val hit = StRegexTextCache.get(cacheKey, text)
        value = if (hit != null) {
            lastRenderedHolder.value = hit
            StRegexRenderResult(text = hit, isComputing = false)
        } else {
            val computed = withContext(Dispatchers.Default) {
                computeStRegexedTextAsync(
                    text = text,
                    scripts = scripts,
                    assistant = assistant,
                    scope = scope,
                    placement = placement,
                    depth = depth,
                    userName = userName,
                    conversationId = conversationId,
                )
            }
            lastRenderedHolder.value = computed
            StRegexRenderResult(text = computed, isComputing = false)
        }
    }
    return asyncResult
}

@Composable
internal fun rememberStRegexedText(
    text: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): String = rememberStRegexedTextWithStatus(
    text = text,
    scripts = scripts,
    assistant = assistant,
    scope = scope,
    placement = placement,
    depth = depth,
    userName = userName,
    conversationId = conversationId,
).text
