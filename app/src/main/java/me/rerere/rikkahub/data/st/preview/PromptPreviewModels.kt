package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

/**
 * 12 个 SillyTavern 内置标准 Marker 全集（与 ExportSerializer.parseActiveStPresetConfig 对齐）。
 */
val ST_BUILTIN_MARKERS: List<String> = listOf(
    "main",
    "worldInfoBefore",
    "personaDescription",
    "charDescription",
    "charPersonality",
    "scenario",
    "enhanceDefinitions",
    "nsfw",
    "worldInfoAfter",
    "dialogueExamples",
    "chatHistory",
    "jailbreak",
)

/**
 * 每一块预览条目的精确溯源坐标，用于点击“保存”时 100% 可逆回写配置。
 */
sealed interface PromptSlotOrigin {
    /** ST 预设内置 12 个 Marker */
    data class PresetMarker(val group: String, val identifier: String) : PromptSlotOrigin

    /** 预设非 Marker 条目（presetGroup != null）或用户自建独立规则/模式注入（presetGroup == null） */
    data class ModeInjectionItem(
        val id: Uuid,
        val presetGroup: String?,
        val presetIdentifier: String?,
    ) : PromptSlotOrigin

    /** 世界书（Lorebook）单条词条 */
    data class LorebookEntry(
        val lorebookId: Uuid,
        val entryId: Uuid,
        val lorebookName: String,
    ) : PromptSlotOrigin

    /** 全局 Author's Note */
    data object AuthorsNote : PromptSlotOrigin

    /** 当前激活的用户人设（Persona） */
    data class Persona(val personaId: Uuid) : PromptSlotOrigin

    /** 聊天记录区顶/底视觉边界锚点（保证内嵌 AT_DEPTH 条目可作为扁平 ReorderableItem 自由拖出） */
    data class ChatRegionHeader(
        val totalMessageCount: Int,
        val embeddedDepthCount: Int,
        val expanded: Boolean,
    ) : PromptSlotOrigin

    data object ChatRegionFooter : PromptSlotOrigin

    /** 展开态下的单条近期历史消息标尺（isPendingDraft = true 表示底部待发送用户消息 depth=0 标尺） */
    data class ChatMessageAnchor(
        val messageId: String,
        val role: MessageRole,
        val depthFromEnd: Int,
        val floorNumber: Int,
        val isPendingDraft: Boolean = false,
    ) : PromptSlotOrigin

    /** 只读系统/运行时块（工具说明、时间提醒、JSR 动态注入等） */
    data class ReadOnlyRuntime(val sourceTag: String) : PromptSlotOrigin
}

/** 面板中渲染的单行结构块 */
data class PromptPreviewBlock(
    val key: String,
    val origin: PromptSlotOrigin,
    val categoryLabel: String,
    val title: String,
    val role: MessageRole,
    val plainBodyPreview: String,
    val position: InjectionPosition?,
    val injectDepth: Int?,
    val priority: Int?,
    val draggable: Boolean,
    val isInsideChatHistory: Boolean = false,
    val inactiveReason: String? = null,
)

enum class ProcessorChannel { PRE_SEND, RENDER_ONLY, BOTH }

/** 顶部状态栏展示的“已启用消息处理器”徽章 */
data class ActiveProcessorBadge(
    val id: String,
    val name: String,
    val category: String,
    val channel: ProcessorChannel,
    val isBlackBoxJsHook: Boolean = false,
    val detail: String = "",
)

/** 一次只读组装产出的完整快照 */
data class PromptPreviewSnapshot(
    val blocks: List<PromptPreviewBlock>,
    val chatMessagesForExpansion: List<PromptPreviewBlock>,
    val activeProcessors: List<ActiveProcessorBadge>,
    val activePresetGroup: String?,
    val secondaryPresetGroups: List<String>,
)

/**
 * 剥离助手消息中的 HTML / WebView 面板代码、style/script/svg 与标签，仅提取人类可读纯正文。
 */
object PlainBodyPreviewExtractor {
    private val FENCE_HTML_REGEX = Regex("```(?:html|htm|xml|svg)[\\s\\S]*?```", RegexOption.IGNORE_CASE)
    private val STYLE_SCRIPT_REGEX = Regex("<(script|style|template|svg)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val MULTI_BLANK_REGEX = Regex("\\s+")

    fun extract(raw: String, maxChars: Int = 220): String {
        if (raw.isBlank()) return ""
        val cleaned = raw
            .replace(FENCE_HTML_REGEX, " [HTML面板] ")
            .replace(STYLE_SCRIPT_REGEX, " ")
            .replace(COMMENT_REGEX, " ")
            .replace(HTML_TAG_REGEX, " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(MULTI_BLANK_REGEX, " ")
            .trim()
        return if (cleaned.length > maxChars) cleaned.take(maxChars) + "…" else cleaned
    }
}
