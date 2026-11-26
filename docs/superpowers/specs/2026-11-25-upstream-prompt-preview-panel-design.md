# 上游消息结构预览与自由拖拽调序面板 — 正式设计文档

**日期**：2026-11-25  
**适用项目**：rikkaST（基于 `2fd2c8c` + `rikkast-perf-full-fix-v2`）  
**状态**：定稿待批准（已通过 Builder Check 并合入全部架构拍板）  
**阅读约定**：本文档所有技术章节均严格遵循 **「代码实现优先 → 自然语言通俗解释」** 结构。

---

## 1. 目标、非目标与红线约束

### 1.1 已拍板的核心架构决策
1. **底层彻底改造（真自由重排）**：同步升级 `GenerationLoop` 与 `PromptInjectionTransformer` 的相对区组装逻辑，使全部 12 个内置 Marker（`main`、`worldInfoBefore`、`personaDescription`、`charDescription`、`charPersonality`、`scenario`、`enhanceDefinitions`、`nsfw`、`worldInfoAfter`、`dialogueExamples`、`chatHistory`、`jailbreak`）以及预设/世界书/规则注入/作者注/人设条目**真正支持任意重排并在发送时 100% 生效**。同时保留默认顺序快速路径，确保未自定义顺序的现有用户行为与老版本逐字节兼容。
2. **预设完整双写（Kotlin ↔ JS 零分裂）**：点击「保存」时，同步更新 `Settings.promptInjections` **与** `Settings.tavernPresets[group]` 原始 JSON 中的 `prompts[]`（`injection_position` / `injection_depth` / `injection_order`）及 `prompt_order[]`（同时兼容多 `character_id` 块与 `100001` 默认块），确保 Kotlin 发送管线与酒馆 JS 运行时 `getPreset()` 看到完全一致的顺序。
3. **全局共享配置持久生效（暂与 SillyTavern 原生心智对齐）**：点击「保存」通过单次原子 `PreferencesStore.update` 立即持久写入全局 `Settings`（对共享该预设/世界书/作者注/人设的助手立即生效），面板底部明确提示“将同步更新共享配置”；接口层预留 `scope: PromptOrderScope` 扩展点以便未来平滑演进到单助手覆写。

### 1.2 我们为 Builder Check 次要细节做出的定案（供你审阅确认）
* **定案 A（`depth=0` 标尺对齐 + 同深度角色顺序）**：展开聊天记录时，底部追加一条只读的 **「下一条用户消息（草稿预览）」** 标尺（输入框有字则显示当前草稿，否则显示占位），确保用户把条目拖到 `depth=0` 与真实点击发送后的相对位置完全一致（消除 off-by-one）。同一 `injectDepth` 内不擅自篡改条目 `role`，保留原生 `system → user → assistant` 排序规则并在同深度多角色条目上显示轻提示。
* **定案 B（折叠态扁平化 + 未触碰深层 `depth > 30` 防误改）**：聊天记录折叠态不再把内嵌 `AT_DEPTH` 条目藏进不可拖动的子卡片里，而是用一对视觉边界锚点 `ChatRegionStart` / `ChatRegionEnd` 包裹扁平的 `ReorderableItem`；点击聊天记录区标题或长按其中任意 `depth` 条目均可直接展开近期 30 条纯正文消息。`PromptOrderSyncWriter` 仅回写被用户实际移动过的 `dirtyKeys`（或受影响的相对区顺序），**绝不覆盖用户未触碰的 `depth > 30` 深层条目**。
* **定案 C（正文预览与本轮未触发条目）**：第一阶段预览仅做零副作用的轻量宏替换（`{{char}}` / `{{user}}`），绝不执行会写变量表的 EJS `setvar`；对“已启用但本轮因间隔/向量跳过未触发”的条目（如 `authorNoteInterval > 1` 的非触发轮、跳过远程 embedding 的 `vectorized` 世界书词条），以灰态 + `本轮未触发` 标签展示，方便用户依然可以调整其顺序与深度。

### 1.3 反目标与发送防卡死红线（Anti-Goals）
* **绝对禁止发送卡死**：`PromptInjectionTransformer` 曾经历大修且极易因死循环/异常吞没/协程挂起导致发消息卡住本地。本次底层重排改造**禁止引入任何同步 I/O、禁止引入递归循环、禁止改变现有 `onCompletion` / `try-catch` 兜底路径**，所有新增排序与合并逻辑必须为纯函数 + 全覆盖单元测试（含空预设、缺失 marker、重复 identifier、损坏 JSON、多预设组、超长会话等极端边界）。
* **绝对禁止预览副作用**：打开预览绝不推进世界书 `sticky`/`cooldown`、绝不消费 JSR `once = true` 注入、绝不写回 `TavernVariableStore`、绝不发远程 embedding 请求。
* **绝对不破坏 v251/P0 WebView 防线**。

---

## 2. 方案选型（Approaches）

| 维度 | 方案 A（已选定）：统一纯函数排序核 + 只读 `PromptPreviewAssembler` + `PromptOrderSyncWriter` | 方案 B（已淘汰）：在 16 个生产 Transformer 内侵入式透传 `dryRun` |
| :--- | :--- | :--- |
| **核心思路** | 抽离一份无副作用的纯函数 `UnifiedPromptOrderEngine` 同时服务「真实发送（`GenerationLoop` + `PromptInjectionTransformer`）」与「面板预览（`PromptPreviewAssembler`）」，由 `PromptOrderSyncWriter` 负责「保存」时的完整双写 | 修改全部 16 个线上 Transformer 加入 `if (!dryRun)` 分支并插桩记录轨迹 |
| **一致性与风险** | **零漂移 + 极低风险**：预览和发送共用同一份纯函数排序核，且不触碰其余 14 个 Transformer 的副作用路径 | 任意一处漏判 `dryRun` 即污染真实对话状态，回归面大 |

---

## 3. 核心数据模型与纯正文提取

### 3.1 代码实现

```kotlin
package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

/** 每一块预览条目的精确溯源坐标（100% 可逆回写） */
sealed interface PromptSlotOrigin {
    /** ST 预设 12 个内置 Marker（main / worldInfoBefore / personaDescription / charDescription / ...） */
    data class PresetMarker(val group: String, val identifier: String) : PromptSlotOrigin

    /** 预设非 Marker 条目（presetGroup != null）或用户自建独立规则/模式注入（presetGroup == null） */
    data class ModeInjectionItem(
        val id: Uuid,
        val presetGroup: String?,
        val presetIdentifier: String?, // 反向匹配 tavernPresets[group].prompts[].identifier 用
    ) : PromptSlotOrigin

    /** 世界书（Lorebook）单条词条 */
    data class LorebookEntry(val lorebookId: Uuid, val entryId: Uuid, val lorebookName: String) : PromptSlotOrigin

    /** 全局 Author's Note */
    data object AuthorsNote : PromptSlotOrigin

    /** 当前激活的用户人设（Persona） */
    data class Persona(val personaId: Uuid) : PromptSlotOrigin

    /** 聊天记录区顶/底视觉边界锚点（折叠与展开态共用，保证内嵌 AT_DEPTH 条目可作为扁平 ReorderableItem 自由拖出） */
    data class ChatRegionHeader(val totalMessageCount: Int, val embeddedDepthCount: Int, val expanded: Boolean) : PromptSlotOrigin
    data object ChatRegionFooter : PromptSlotOrigin

    /** 展开态下的单条近期历史消息标尺 */
    data class ChatMessageAnchor(
        val messageId: String,
        val role: MessageRole,
        val depthFromEnd: Int, // 含底部待发送用户消息标尺后的精确深度（depth=0 位于待发送消息之后）
        val floorNumber: Int,
        val isPendingDraft: Boolean = false,
    ) : PromptSlotOrigin

    /** 只读系统/运行时块（工具说明、时间提醒、JSR 动态注入等） */
    data class ReadOnlyRuntime(val sourceTag: String) : PromptSlotOrigin
}

data class PromptPreviewBlock(
    val key: String,
    val origin: PromptSlotOrigin,
    val categoryLabel: String,
    val title: String,
    val role: MessageRole,
    val plainBodyPreview: String,       // 已剥离 HTML/WebView 面板代码的纯正文（UI maxLines = 4）
    val position: InjectionPosition?,
    val injectDepth: Int?,
    val priority: Int?,
    val draggable: Boolean,
    val isInsideChatHistory: Boolean = false,
    val inactiveReason: String? = null, // 非空时以灰态“本轮未触发”展示，但仍允许调序
)

enum class ProcessorChannel { PRE_SEND, RENDER_ONLY, BOTH }

data class ActiveProcessorBadge(
    val id: String,
    val name: String,
    val category: String,               // 正则 / 酒馆脚本 / 提示词模板 / MVU / SPreset / 第三方扩展
    val channel: ProcessorChannel,
    val isBlackBoxJsHook: Boolean = false,
    val detail: String = "",
)

data class PromptPreviewSnapshot(
    val blocks: List<PromptPreviewBlock>,
    val chatMessagesForExpansion: List<PromptPreviewBlock>, // 最多最近 30 条纯正文消息 + 底部待发送用户消息标尺
    val activeProcessors: List<ActiveProcessorBadge>,
    val activePresetGroup: String?,
    val secondaryPresetGroups: List<String>,
)

/** 剥离助手消息中的 HTML/WebView 面板代码，仅提取人类可读纯正文 */
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
            .replace(MULTI_BLANK_REGEX, " ")
            .trim()
        return if (cleaned.length > maxChars) cleaned.take(maxChars) + "…" else cleaned
    }
}
```

### 3.2 自然语言说明
这一层定义了面板和底层管线共同使用的「积木语言」：
* 每块内容都有精确的 `PromptSlotOrigin`（来源身份证），哪怕多个世界书和预设条在最终发请求时被拼成同一条消息，预览层依然清清楚楚知道每一小块的出处。
* 聊天记录区使用 `ChatRegionHeader` 和 `ChatRegionFooter` 夹住所有内嵌的 `AT_DEPTH` 条目：平时折叠不显示历史消息，但已经插在聊天记录里的深度条目依然是独立可长按拖拽的卡片，你可以直接把它们拖出聊天区，或者点一下/长按展开最近 30 条纯正文消息微调深度。
* `PlainBodyPreviewExtractor` 会把助手消息里的 ` ```html ` 面板代码、`<style>` / `<script>` 和所有标签全部剥干净，只保留 3~4 行人类可读剧情正文。

---

## 4. 底层彻底改造：真正支持全部 12 个 Marker 与相对区自由重排

### 4.1 代码实现（`UnifiedPromptOrderEngine` + `GenerationLoop` / `PromptInjectionTransformer` 接入）

```kotlin
package me.rerere.rikkahub.data.st.preview

import me.rerere.ai.ui.UIMessage

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
 * 统一槽位组装引擎（纯函数、无 I/O、无副作用、O(N) 确定性执行）：
 * 同时被真实发送路径（GenerationLoop + PromptInjectionTransformer）与预览路径（PromptPreviewAssembler）调用，
 * 保证“你在面板里拖成什么顺序，发给上游的就是什么顺序”。
 */
object UnifiedPromptOrderEngine {

    /**
     * 按生效的 `orderedMarkers`（及用户在 prompt_order 中插入的非 marker 相对条目）
     * 把各个具名槽位对应的消息片段拼成最终有序序列。
     *
     * 防卡死与兼容性保证：
     * 1. 若 `orderedMarkers` 为空（无 ST 预设），自动退回 ST_BUILTIN_MARKERS 默认顺序，与旧版 100% 等价；
     * 2. 每个槽位只消费一次（visited 集合防护），绝不可能因损坏 JSON 中的重复 identifier 死循环或重复注入；
     * 3. 未在 orderedMarkers 中出现的遗留槽位按默认相对顺序安全兜底追加，绝不丢消息。
     */
    fun assembleSlotsInOrder(
        orderedIdentifiers: List<String>,
        disabledIdentifiers: Set<String>,
        slotMessages: Map<String, List<UIMessage>>,
        fallbackOrder: List<String> = ST_BUILTIN_MARKERS,
    ): List<UIMessage> {
        val effectiveOrder = if (orderedIdentifiers.isEmpty()) fallbackOrder else orderedIdentifiers
        val result = ArrayList<UIMessage>(slotMessages.values.sumOf { it.size })
        val consumed = HashSet<String>(effectiveOrder.size + fallbackOrder.size)

        for (id in effectiveOrder) {
            if (!consumed.add(id)) continue
            if (id in disabledIdentifiers) continue
            slotMessages[id]?.let { result.addAll(it) }
        }
        // 安全兜底：确保任何未出现在 prompt_order 中的必需槽位（如 chatHistory）不会静默丢失
        for (id in fallbackOrder) {
            if (!consumed.add(id)) continue
            if (id in disabledIdentifiers) continue
            slotMessages[id]?.let { result.addAll(it) }
        }
        return result
    }
}
```

**对现有发送管线的精确改造点（最小侵入、纯函数化）**：
1. **`GenerationLoop.kt`**（`buildCachedSystemPrompt` 与 `generateInternal`）：
   * 原本硬编码按 `main → charCard → dialogueExamples → newChatPrompt → chatHistory → persona` 拼接；
   * 改造后先把各槽位产出放进 `slotMessages: Map<String, List<UIMessage>>`（`main`、`personaDescription`（当 `IN_PROMPT` 时）、`charDescription`、`charPersonality`、`scenario`、`dialogueExamples`、`chatHistory`（含 `newChatPrompt` 头）），连同 `PromptInjectionTransformer` 产出的 `worldInfoBefore`、`worldInfoAfter`、以及各非 marker 预设相对条目的 identifier 槽位，统一交由 `UnifiedPromptOrderEngine.assembleSlotsInOrder(...)` 一次按 `prompt_order` 顺序拼出！
   * `AT_DEPTH` / `IN_CHAT` 条目仍按精确 `injectDepth` 插入到 `chatHistory` 槽位的消息列表内部（在 `chatHistory` 整体被放到 `orderedIdentifiers` 的任意位置后，其内部 `AT_DEPTH` 依然精确跟随聊天记录！）。
2. **`PromptInjectionTransformer.kt`**：
   * 抽离出纯只读核心函数 `matchLorebooksReadOnly(...)`（供预览与发送共用匹配/过滤逻辑；仅真实发送时由外层写入 `stickyTracker`/`cooldownTracker` 并按需触发远程 embedding）；
   * 当存在激活的 ST 预设 `orderedIdentifiers` 时，相对区预设条目（`ModeInjection` with `presetGroup != null`）直接按其 `identifier` 挂入 `UnifiedPromptOrderEngine`，不再被强行挤进有限的 4 个静态 `InjectionPosition` 桶里！独立自建规则注入（`presetGroup == null`）与世界书条目按 `InjectionPosition` 锚定到对应 marker 槽位前后。

### 4.2 自然语言说明
为什么以前很多项目做“提示词排序”都是假的？因为底层发送代码把「主提示词 → 角色卡 → 示例 → 聊天记录」写死了，改了预设顺序代码根本不看。
* 这次我们把这段写死的拼接抽成一个极简、纯内存的 `UnifiedPromptOrderEngine`：把每一类内容（主提示词、角色卡、世界书前/后、示例、人设、聊天记录、破限、以及预设里的自定义条目）各自打包成带名字的「槽位」，然后严格照着 `prompt_order` 的清单逐个装车。
* **怎么保证绝不会像以前那样把发消息卡死？** 这个引擎不碰网络、不碰磁盘、不碰协程、没有递归，还带了“已消费集合去重 + 缺失槽位安全兜底”双保险——就算用户导入了一份格式烂掉的预设 JSON，它也只会 O(N) 毫秒级跑完并兜底吐出合法消息列表，绝不会挂起或抛异常卡住发送。

---

## 5. 完整双写回写引擎（`PromptOrderSyncWriter`）

### 5.1 代码实现

```kotlin
package me.rerere.rikkahub.data.st.preview

import kotlinx.serialization.json.*
import me.rerere.rikkahub.data.datastore.PreferencesStore
import me.rerere.rikkahub.data.model.AuthorNotePosition
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PersonaInjectionPosition
import kotlin.uuid.Uuid

class PromptOrderSyncWriter(
    private val preferencesStore: PreferencesStore,
) {
    /**
     * 点“保存”时调用：
     * 1. 仅对用户实际移动过的 dirtyKeys 与新的全局相对区顺序执行更新，绝不误改未触碰的 depth > 30 深层条目；
     * 2. 完整双写 Settings.promptInjections 与 Settings.tavernPresets[group]（prompts[] + prompt_order[]）；
     * 3. 同步更新 Settings.lorebooks / authorNotePosition / authorNoteDepth / personas；
     * 4. 单次原子 preferencesStore.update 落盘，StPresetConfigCache 因 JSON 变化自动失效，JS 侧同步刷新。
     */
    suspend fun commitDraft(
        draftBlocks: List<PromptPreviewBlock>,
        dirtyKeys: Set<String>,
        activePresetGroup: String?,
    ) {
        if (dirtyKeys.isEmpty()) return

        val headerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionHeader }
        val footerIdx = draftBlocks.indexOfFirst { it.origin is PromptSlotOrigin.ChatRegionFooter }
        val isChatExpanded = draftBlocks.any { it.origin is PromptSlotOrigin.ChatMessageAnchor }

        val modeUpdates = mutableMapOf<Uuid, ModePatch>()
        val loreUpdates = mutableMapOf<Pair<Uuid, Uuid>, LorePatch>()
        val presetIdentifierOrder = mutableListOf<String>()
        val presetPromptJsonPatches = mutableMapOf<String, PresetPromptPatch>() // identifier -> patch
        var newAuthorNotePos: AuthorNotePosition? = null
        var newAuthorNoteDepth: Int? = null
        var personaUpdate: Pair<Uuid, Pair<PersonaInjectionPosition, Int>>? = null

        draftBlocks.forEachIndexed { index, block ->
            val insideChat = headerIdx >= 0 && footerIdx > headerIdx && index in (headerIdx + 1) until footerIdx
            val isTouched = block.key in dirtyKeys

            // 收集主预设的完整相对顺序（含 12 个内置 marker + 所有非 marker 预设条目）
            when (val o = block.origin) {
                is PromptSlotOrigin.PresetMarker -> if (o.group == activePresetGroup) {
                    presetIdentifierOrder.add(o.identifier)
                }
                is PromptSlotOrigin.ModeInjectionItem -> if (o.presetGroup == activePresetGroup && o.presetIdentifier != null) {
                    presetIdentifierOrder.add(o.presetIdentifier)
                }
                else -> Unit
            }

            // 未被拖动且原本已在聊天记录内（含 depth > 30）的条目：严格保留原 injectDepth，禁止被展开窗口截断！
            if (!isTouched && block.isInsideChatHistory && insideChat) return@forEachIndexed

            val resolvedDepth = if (insideChat) {
                if (isChatExpanded) computeDepthFromExpandedChat(draftBlocks, index) else (block.injectDepth ?: 0)
            } else {
                block.injectDepth ?: 4
            }
            val resolvedPos = if (insideChat) {
                InjectionPosition.AT_DEPTH
            } else {
                inferRelativePosition(draftBlocks, index, headerIdx, footerIdx, block.position)
            }

            when (val origin = block.origin) {
                is PromptSlotOrigin.ModeInjectionItem -> {
                    val prio = if (origin.presetGroup != null) index * 10 else (draftBlocks.size - index) * 10
                    modeUpdates[origin.id] = ModePatch(resolvedPos, resolvedDepth, prio)
                    if (origin.presetGroup == activePresetGroup && origin.presetIdentifier != null) {
                        presetPromptJsonPatches[origin.presetIdentifier] = PresetPromptPatch(
                            injectionPosition = if (insideChat) 1 else 0, // ST: 0=relative, 1=in-chat @ depth
                            injectionDepth = resolvedDepth,
                            injectionOrder = prio,
                        )
                    }
                }
                is PromptSlotOrigin.LorebookEntry -> if (isTouched) {
                    loreUpdates[origin.lorebookId to origin.entryId] = LorePatch(resolvedPos, resolvedDepth, index * 10)
                }
                is PromptSlotOrigin.AuthorsNote -> if (isTouched) {
                    newAuthorNotePos = if (insideChat) AuthorNotePosition.IN_CHAT else AuthorNotePosition.IN_PROMPT
                    newAuthorNoteDepth = resolvedDepth
                }
                is PromptSlotOrigin.Persona -> if (isTouched) {
                    val pPos = if (insideChat) PersonaInjectionPosition.AT_DEPTH else PersonaInjectionPosition.IN_PROMPT
                    personaUpdate = origin.personaId to (pPos to resolvedDepth)
                }
                else -> Unit
            }
        }

        preferencesStore.update { current ->
            val updatedInjections = current.promptInjections.map { item ->
                val p = modeUpdates[item.id] ?: return@map item
                item.copy(position = p.position, injectDepth = p.depth, priority = p.priority, injectionOrder = p.priority)
            }
            val updatedLorebooks = current.lorebooks.map { book ->
                book.copy(entries = book.entries.map { entry ->
                    val p = loreUpdates[book.id to entry.id] ?: return@map entry
                    entry.copy(position = p.position, injectDepth = p.depth, priority = p.priority)
                })
            }
            val updatedTavernPresets = if (activePresetGroup != null) {
                syncTavernPresetJson(
                    rawPresetsJson = current.tavernPresets,
                    group = activePresetGroup,
                    orderedIdentifiers = presetIdentifierOrder,
                    promptPatches = presetPromptJsonPatches,
                )
            } else {
                current.tavernPresets
            }
            val updatedPersonas = personaUpdate?.let { (pid, pair) ->
                current.personas.map { if (it.id == pid) it.copy(position = pair.first, depth = pair.second) else it }
            } ?: current.personas

            current.copy(
                promptInjections = updatedInjections,
                lorebooks = updatedLorebooks,
                tavernPresets = updatedTavernPresets,
                authorNotePosition = newAuthorNotePos ?: current.authorNotePosition,
                authorNoteDepth = newAuthorNoteDepth ?: current.authorNoteDepth,
                personas = updatedPersonas,
            )
        }
    }

    /**
     * 完整双写 Settings.tavernPresets[group] 原始预设 JSON：
     * 1. 更新 prompts[] 中匹配 identifier 的 injection_position (0=相对 / 1=深度)、injection_depth、injection_order；
     * 2. 更新 prompt_order[] 中 character_id == 100001（或首个有效桶）的 order 数组，按 orderedIdentifiers 重排，
     *    同时保留所有未参与本次预览的禁用项与原始字段，绝不丢数据。
     * 注：反向匹配 identifier 使用 ExportSerializer.kt:516 完全一致的确定性 UUID 规则：
     *     UUID.nameUUIDFromBytes("$group::$identifier".toByteArray(Charsets.UTF_8))
     */
    internal fun syncTavernPresetJson(
        rawPresetsJson: String,
        group: String,
        orderedIdentifiers: List<String>,
        promptPatches: Map<String, PresetPromptPatch>,
    ): String { /* 完整 JSON 双向同步实现，含损坏 JSON 兜底返回原串 */ return rawPresetsJson }

    private fun computeDepthFromExpandedChat(blocks: List<PromptPreviewBlock>, index: Int): Int {
        var messagesBelow = 0
        for (i in (index + 1) until blocks.size) {
            if (blocks[i].origin is PromptSlotOrigin.ChatMessageAnchor) messagesBelow++
        }
        return messagesBelow.coerceAtLeast(0)
    }
}

internal data class ModePatch(val position: InjectionPosition, val depth: Int, val priority: Int)
internal data class LorePatch(val position: InjectionPosition, val depth: Int, val priority: Int)
internal data class PresetPromptPatch(val injectionPosition: Int, val injectionDepth: Int, val injectionOrder: Int)
```

### 5.2 自然语言说明
这一段负责把你的拖拽结果「严丝合缝地写回两个仓库」：
* **防误伤机制（`dirtyKeys`）**：如果你对话里有 100 楼，某条世界书本来设在 `depth = 50`，而预览只展开最近 30 条消息——因为你没有拖过这条世界书，它不在 `dirtyKeys` 里，点「保存」时程序会原封不动保留它的 `depth = 50`，绝不会误把它截断成 30。
* **完整双写（Kotlin + 原始预设 JSON）**：当你把某条预设破限从系统提示词拖进聊天记录 `depth = 1`，程序会同时改两处：一边改 Kotlin 的 `Settings.promptInjections`（立刻影响原生发送），另一边通过 `nameUUIDFromBytes("$group::$identifier")` 精准反查并改写 `Settings.tavernPresets[group]` 原始 JSON 里的 `prompts[]` 和 `prompt_order[]`（立刻影响酒馆 JS 插件读取 `getPreset()`），两边永远保持 100% 一致。

---

## 6. 顶部处理器检测与前端 UI 交互

### 6.1 视觉布局与交互流程

```text
+-------------------------------------------------------------------+
|  (深色半透明底 scrim: Color.Black.copy(alpha = 0.62f))              |
|  +-------------------------------------------------------------+  |
|  | [浅色卡片 Surface · 约 4/5 屏高 · 圆角 24.dp]                 |  |
|  | 上游消息结构预览                                         [×] |  |
|  | ─────────────────────────────────────────────────────────── |  |
|  | 已启用处理器（横向滚动，点击可查看完整清单）：                  |  |
|  | [正则·3] [酒馆脚本·2 ⚡] [EJS模板] [MVU ⚡] [记忆增强 ⚡]       |  |
|  | ─────────────────────────────────────────────────────────── |  |
|  | ┌───────────────────────────────────────────────┬─────────┐ |  |
|  | │ [预设] 主系统提示词 (system)                    │   ≡     │ |  |
|  | │ 你是一个...                                    │         │ |  |
|  | └───────────────────────────────────────────────┴─────────┘ |  |
|  | ┌───────────────────────────────────────────────┬─────────┐ |  |
|  | │ [世界书] 学院设定 · AFTER_CHARACTER            │   ≡     │ |  |
|  | └───────────────────────────────────────────────┴─────────┘ |  |
|  | ╔═════════════════════════════════════════════════════════╗ |  |
|  | ║ ▼ 聊天记录区（共 42 条，点击或拖入此处展开近期消息）       ║ |  |
|  | ╠─────────────────────────────────────────────────────────╣ |  |
|  | ║ ┌───────────────────────────────────────────┬─────────┐ ║ |  |
|  | ║ │ [世界书] 状态栏规则 · depth=1              │   ≡     │ ║ |  |
|  | ║ └───────────────────────────────────────────┴─────────┘ ║ |  |
|  | ║   (点击标题 / 长按内嵌项 / 外部拖入时自动展开：)          ║ |  |
|  | ║   #41 用户：我们去图书馆看看吧...              (锁定)    ║ |  |
|  | ║   #42 助手：她轻轻推开木门，阳光洒在书架上...   (锁定)    ║ |  |
|  | ║       (已剥离 HTML/面板代码，最多显示 3~4 行纯正文)      ║ |  |
|  | ║   ▶ 下一条用户消息（草稿预览 · depth=0 标尺）  (锁定)    ║ |  |
|  | ╚═════════════════════════════════════════════════════════╝ |  |
|  | ─────────────────────────────────────────────────────────── |  |
|  | 共 12 块 · 保存将同步更新共享配置          [ 取消 ]  [ 保存 ] |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+
```

### 6.2 代码实现要点
* **入口挂载**（`ChatInput.kt:560-570`）：紧跟 `ReasoningButton` 后添加 `PromptPreviewButton`（复用 `ToggleSurface` 样式，放在 `ModelAbility.REASONING` 条件分支外，确保所有模型均可见）。
* **弹窗容器**：自定义 `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`，外层 `Color.Black.copy(alpha = 0.62f)` 深色遮罩，内部 `fillMaxWidth(0.94f).fillMaxHeight(0.80f)` 浅色 `Surface`，彻底避免 `ModalBottomSheet` 下拉关闭与拖拽冲突。
* **右侧三横杠拖拽**：复用仓库现有 `sh.calvin.reorderable`，仅在右侧三横杠图标上挂 `Modifier.longPressDraggableHandle()`；当拖拽物跨入 `ChatRegionHeader`~`ChatRegionFooter` 且聊天区尚处于折叠态时，自动插入 `snapshot.chatMessagesForExpansion` 展开为近期 30 条纯正文消息 + 底部待发送用户消息标尺。
* **顶部 8 类处理器检测**：聚合 `mergeActiveRegexScripts`（ST 正则）、`assistant.regexes`（原生正则）、`buildTavernScriptsJson`（酒馆脚本）、`InjectedPromptStore.snapshot`（JSR 动态注入）、`settings.tavernEjsRendering`（EJS 模板）、`TavernRuntimeManager.isMvuActive`（MVU）、`spresetConfig`（SPreset）及 `listExtensions(context)`（已启用第三方插件），对黑盒 JS 扩展标注 `⚡ 运行时钩子`。

### 6.3 自然语言说明
* 你点开输入框下方「思考强度」旁边的按钮，就会弹出这张 4/5 屏的浅色结构卡片。
* 最上面一眼就能看到“这次发消息会被哪些正则、脚本、模板、插件动过”；中间每一条都是真实的发送积木，右侧三横杠长按就能拖；拖进聊天记录区时，聊天记录会丝滑展开成最近每条纯文本正文（3~4 行、绝不夹杂乱七八糟的 HTML 面板代码），方便你精准插到某条对话前后；底部点「取消」无事发生，点「保存」立刻永久生效并弹出确认提示。

---

## 7. 全量测试与防退化验证矩阵（强制门禁）

为兑现“绝不弄坏原有功能、绝不卡死发送、经得起其他 AI 与真机编译严查”的底线，实现阶段必须配套以下完整测试套件：
1. **`UnifiedPromptOrderEngineTest`**（纯 JVM 单元测试）：
   * 覆盖全部 12 个内置 marker 任意倒序/交错排列后消息顺序 100% 精确生效；
   * 覆盖空 `orderedMarkers` 退回默认顺序与旧版逐条一致；
   * 覆盖损坏输入（重复 identifier、缺失 `chatHistory`、全部禁用、未知 identifier）绝不死循环、绝不丢关键消息。
2. **`PromptInjectionTransformerRegressionTest`**（发送防卡死回归）：
   * 覆盖现有全部 15 种 `InjectionPosition`、`AT_DEPTH` 深度注入、多世界书递归、同深度 `system→user→assistant` 排序、以及 `presetGroup == null`（降序）与 `!= null`（升序）行为零退化。
3. **`PromptOrderSyncWriterTest`**（完整双写往返测试）：
   * 拖拽非 marker 预设条目进出 `AT_DEPTH`，验证 `Settings.promptInjections` 与 `Settings.tavernPresets[group]`（`prompts[]` + `prompt_order[]`）双写完全一致，再重新 `parseActiveStPresetConfig` 回读结果 100% 匹配；
   * 验证未触碰的 `depth = 50` 深层世界书条目在保存后原封不动；
   * 验证多 `character_id` 预设与损坏预设 JSON 安全兜底不崩溃。
4. **`PlainBodyPreviewExtractorTest` & `PromptPreviewAssemblerTest`**：
   * 验证复杂 HTML 面板 / `<script>` / `<style>` / Markdown 代码块剥离为干净纯正文；
   * 验证 Dry-Run 打开 10 次后世界书 `sticky`/`cooldown` 与 JSR `once` 注入零变动；
   * 验证 8 类顶部处理器检测准确无漏报。
5. **独立对抗式 Code Review 门禁**：实现完成并跑完全部静态/逻辑校验后，强制再过一遍零上下文独立审查，清零所有 Critical/Important 问题再交付。
