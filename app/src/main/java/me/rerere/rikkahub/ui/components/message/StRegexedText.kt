package me.rerere.rikkahub.ui.components.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.st.regex.RegexScript
import me.rerere.rikkahub.data.st.regex.RegexScriptEngine
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

/**
 * 当 >20KB 超长开场白在后台计算正则期间（或开场白选择器预览时），快速提取可读纯文字正文，杜绝返回 "" 空串导致白条或裸露 CSS。
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
 * 缓存键完整包含文本、脚本列表、助手 ID/名称/内嵌正则、作用域、placement、楼层深度、用户名与会话 ID，
 * 杜绝切换预设、编辑正则或切换助手后命中陈旧缓存。
 */
internal object StRegexTextCache {
    private const val MAX_ENTRIES = 64

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
    )

    private val map = java.util.LinkedHashMap<CacheKey, Pair<String, String>>(16, 0.75f, true)

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
    ): CacheKey = CacheKey(
        textHash = text.hashCode(),
        textLen = text.length,
        scripts = scripts,
        assistantId = assistant?.id?.toString(),
        assistantName = assistant?.tavernData?.nickname?.takeIf { it.isNotBlank() } ?: assistant?.name,
        assistantRegexes = assistant?.regexes.orEmpty(),
        scope = scope,
        placement = placement,
        depth = depth,
        userName = userName,
        conversationId = conversationId,
        varVersion = varVersion,
    )

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
}

private val CHAR_MACRO_REGEX = Regex("""\{\{\s*char\s*\}\}""", RegexOption.IGNORE_CASE)
private val USER_MACRO_REGEX = Regex("""\{\{\s*user\s*\}\}""", RegexOption.IGNORE_CASE)
private val GETVAR_MACRO_REGEX = Regex("""\{\{\s*getvar::([^}]+)\}\}""", RegexOption.IGNORE_CASE)
private val GETGLOBALVAR_MACRO_REGEX = Regex("""\{\{\s*getglobalvar::([^}]+)\}\}""", RegexOption.IGNORE_CASE)

internal fun computeStRegexedText(
    text: String,
    scripts: List<RegexScript>,
    assistant: Assistant?,
    scope: AssistantAffectScope,
    placement: Int,
    depth: Int? = null,
    userName: String? = null,
    conversationId: String? = null,
): String {
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
    val sub: (String) -> String = { s ->
        if (!s.contains("{{")) {
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
            out
        }
    }
    val afterAssistantRegex = text.replaceRegexes(assistant = assistant, scope = scope, visual = true)
    val rendered = RegexScriptEngine.getRegexedString(
        rawString = afterAssistantRegex,
        scripts = scripts,
        placement = placement,
        isMarkdown = true,
        isPrompt = false,
        depth = depth,
        substitute = sub,
    )
    StRegexTextCache.put(cacheKey, text, rendered)
    return rendered
}

/**
 * 显示通道非阻塞渐进式正则管线（带状态指示与快速剥壳预览）：
 * - 短文本（<= 20KB）或已命中 LRU 缓存：直接同步返回（`isComputing = false`）；
 * - 超长文本（> 20KB）且首次未命中缓存：首帧立即返回 `extractQuickPlainPreview(text)` 纯文字正文预览
 *   并标记 `isComputing = true`，后台 `Dispatchers.Default` 计算完成后平滑切换为正式结果（`isComputing = false`），
 *   彻底根除首帧 `""` 空白气泡（小白条）。
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
    if (scripts.isEmpty() && assistant?.regexes.isNullOrEmpty()) {
        return StRegexRenderResult(text = text, isComputing = false)
    }
    val varVersion = TavernVariableStore.mutationVersion
    val cacheKey = remember(text, scripts, assistant, scope, placement, depth, userName, conversationId, varVersion) {
        StRegexTextCache.keyOf(text, scripts, assistant, scope, placement, depth, userName, conversationId, varVersion)
    }
    val cached = remember(cacheKey, text) {
        StRegexTextCache.get(cacheKey, text)
    }
    if (cached != null) {
        return StRegexRenderResult(text = cached, isComputing = false)
    }

    if (text.length <= 20_000) {
        val syncResult = remember(cacheKey, text, scripts, assistant, scope, placement, depth, userName, conversationId) {
            computeStRegexedText(text, scripts, assistant, scope, placement, depth, userName, conversationId)
        }
        return StRegexRenderResult(text = syncResult, isComputing = false)
    }

    val initialPreview = remember(text) {
        StRegexTextCache.get(cacheKey, text)?.let { StRegexRenderResult(text = it, isComputing = false) }
            ?: StRegexRenderResult(
                text = extractQuickPlainPreview(text).ifBlank { text.take(1000) },
                isComputing = true,
            )
    }
    val asyncResult by produceState(
        initialValue = initialPreview,
        cacheKey,
        text,
    ) {
        val hit = StRegexTextCache.get(cacheKey, text)
        value = if (hit != null) {
            StRegexRenderResult(text = hit, isComputing = false)
        } else {
            val computed = withContext(Dispatchers.Default) {
                computeStRegexedText(text, scripts, assistant, scope, placement, depth, userName, conversationId)
            }
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
