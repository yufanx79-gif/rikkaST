package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.withHidden
import me.rerere.rikkahub.data.model.withoutHidden

/**
 * 把当前会话转换为 JSR `getChatMessages` / ST `chat[]` 语义的完整消息数组 JSON。
 *
 * 字段对齐 ST 原生聊天记录（供 runtime.js / 第三方扩展 / MVU 消费）：
 * - `name` / `is_user` / `is_system`(隐藏) / `mes`(当前 swipe 文本) / `extra`；
 * - `swipe_id`：当前选中的 swipe 序号（0-based，= MessageNode.selectIndex）；
 * - `swipes`：全部 swipe 变体文本（字符串数组，MVU 会对条目调用 matchAll，严禁对象占位）；
 * - `swipe_info`：与 swipes 等长的变体元数据（本实现为 `{}` 占位）；
 * - `variables`：消息级变量（数组套数组，按 swipe 索引对齐；缺省为等长 `{}`）。
 *
 * [messageVariables] 传入 `TavernVariableStore.getMessageVars(chatId)` 的原始 JSON。
 */
fun buildTavernChatJson(
    conversation: Conversation,
    messageVariables: JsonElement? = null,
): String {
    val varsArray = messageVariables as? JsonArray
    return buildJsonArray {
        conversation.messageNodes.forEachIndexed { index, node ->
            if (node.messages.isEmpty()) return@forEachIndexed
            val sel = node.selectIndex.coerceIn(0, node.messages.lastIndex)
            val current = node.messages[sel]
            val texts = node.messages.map { it.toText() }
            val perSwipe = varsArray?.getOrNull(index) as? JsonArray
            add(
                buildJsonObject {
                    put("name", current.name ?: defaultNameForRole(current.role))
                    put("id", node.id.toString()) // 批次十三：面板靠它定位「本条消息」
                    put("is_user", current.role == MessageRole.USER)
                    put("is_system", current.isHidden)
                    put("mes", texts[sel])
                    put("swipe_id", sel)
                    put("swipes", buildJsonArray { texts.forEach { add(it) } })
                    put("swipe_info", buildJsonArray { repeat(texts.size) { add(buildJsonObject { }) } })
                    put(
                        "variables",
                        buildJsonArray {
                            repeat(texts.size) { i ->
                                add(perSwipe?.getOrNull(i) ?: buildJsonObject { })
                            }
                        },
                    )
                    put("extra", buildJsonObject { })
                },
            )
        }
    }.toString()
}

private fun defaultNameForRole(role: MessageRole): String = when (role) {
    MessageRole.USER -> "user"
    MessageRole.ASSISTANT -> "assistant"
    MessageRole.SYSTEM -> "system"
    else -> role.name.lowercase()
}

/**
 * 应用 JS 侧（JSR `setChatMessages` / MVU `saveChat`）对聊天记录的内容变更。
 *
 * 输入为楼层更新数组：`[{index, mes?, swipes?, swipe_id?, is_system?, name?}, ...]`，
 * 由 runtime.js 对工作副本与宿主快照做 diff 后仅推送变更楼层。
 * 返回更新后的会话；无任何变更时返回 null（调用方跳过持久化）。
 *
 * 语义对齐 ST：
 * - `swipes` 为变体全文数组（该楼层的变体列表以它为准重建，长度可增可减）；
 * - `mes` 写入当前选中变体（JS 侧 `swipes[swipe_id]` 同步由 shim 保证）；
 * - `is_system` 即"隐藏"（映射 UIMessageAnnotation.Hidden）；
 * - `name` 应用到该楼层全部变体。
 */
fun applyTavernChatUpdates(conversation: Conversation, jsonText: String): Conversation? {
    val root = runCatching { Json.parseToJsonElement(jsonText) }.getOrNull() as? JsonArray ?: return null
    if (root.isEmpty()) return null

    val nodes = conversation.messageNodes.toMutableList()
    var changed = false

    for (el in root) {
        val obj = el as? JsonObject ?: continue
        val index = (obj["index"] as? JsonPrimitive)?.intOrNull ?: continue
        if (index !in nodes.indices) continue
        val node = nodes[index]
        if (node.messages.isEmpty()) continue

        var sel = node.selectIndex.coerceIn(0, node.messages.lastIndex)
        var msgs = node.messages

        // 1) swipe_id 先行（后续字段以它为基准）
        (obj["swipe_id"] as? JsonPrimitive)?.intOrNull?.let { raw ->
            if (raw >= 0) sel = raw.coerceAtMost(node.messages.lastIndex)
        }

        // 2) swipes：变体全文重建（长度以输入为准）
        val swipesEl = obj["swipes"] as? JsonArray
        if (swipesEl != null) {
            val texts = swipesEl.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
            if (texts.isNotEmpty() && texts != msgs.map { it.toText() }) {
                val existing = msgs
                val proto = existing.getOrNull(sel) ?: existing.last()
                msgs = List(texts.size) { i ->
                    if (i < existing.size) existing[i].withMesText(texts[i])
                    else proto.withMesText(texts[i])
                }
            }
        }
        // 重建后修正选中索引（变体数可能变少）
        sel = sel.coerceIn(0, msgs.lastIndex)

        // 3) mes：写入当前选中变体
        val mesEl = (obj["mes"] as? JsonPrimitive)?.contentOrNull
        if (mesEl != null) {
            val cur = msgs.getOrNull(sel)
            if (cur != null && cur.toText() != mesEl) {
                msgs = msgs.toMutableList().also { it[sel] = cur.withMesText(mesEl) }
            }
        }

        // 4) is_system：隐藏标记（作用于当前选中变体）
        val hiddenEl = (obj["is_system"] as? JsonPrimitive)?.booleanOrNull
        if (hiddenEl != null) {
            val cur = msgs.getOrNull(sel)
            if (cur != null && cur.isHidden != hiddenEl) {
                val updated = if (hiddenEl) cur.withHidden() else cur.withoutHidden()
                msgs = msgs.toMutableList().also { it[sel] = updated }
            }
        }

        // 5) name：应用到该楼层全部变体
        val nameEl = (obj["name"] as? JsonPrimitive)?.contentOrNull
        if (nameEl != null && msgs.any { it.name != nameEl }) {
            msgs = msgs.map { it.copy(name = nameEl) }
        }

        if (msgs !== node.messages || sel != node.selectIndex) {
            nodes[index] = node.copy(messages = msgs, selectIndex = sel)
            changed = true
        }
    }

    if (!changed) return null
    return conversation.copy(messageNodes = nodes)
}

/** 替换消息的文本内容（替换第一个 Text part；无 Text part 时前置插入；保留图片等其它 parts）。 */
private fun UIMessage.withMesText(text: String): UIMessage {
    val idx = parts.indexOfFirst { it is UIMessagePart.Text }
    return if (idx >= 0) {
        val np = parts.toMutableList()
        np[idx] = UIMessagePart.Text(text)
        copy(parts = np)
    } else {
        copy(parts = listOf(UIMessagePart.Text(text)) + parts)
    }
}

// ============================================================
// [v243 BUGFIX A2] JS `RikkaBridge.insertChatMessages` 载荷解析
// ============================================================

/** `createChatMessages` 的单条插入载荷。 */
data class RuntimeChatInsert(
    val role: MessageRole,
    val name: String?,
    val message: String,
    val data: JsonObject?,
)

/**
 * 解析 JS 侧 `createChatMessages([...])` 归一化后的载荷：
 * `[{"role":"user"|"assistant"|"system","name":String?,"message":String,"data":Object?}]`。
 * 非法/空返回空列表（调用方据此返回 false，不静默吞掉）。
 */
fun parseRuntimeChatInserts(jsonText: String): List<RuntimeChatInsert> {
    if (jsonText.isBlank()) return emptyList()
    return try {
        val arr = Json.parseToJsonElement(jsonText) as? JsonArray ?: return emptyList()
        arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val role = when ((obj["role"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
                "assistant" -> MessageRole.ASSISTANT
                "system" -> MessageRole.SYSTEM
                else -> MessageRole.USER
            }
            val text = (obj["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (text.isBlank()) return@mapNotNull null
            RuntimeChatInsert(
                role = role,
                name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                message = text,
                data = obj["data"] as? JsonObject,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}


/**
 * 将 Conversation 导出为官方 SillyTavern `.jsonl` 聊天记录格式（首行为对话元数据头，后续每行为一条消息 JSON）。
 */
fun exportConversationToStJsonl(
    conversation: Conversation,
    userName: String = "User",
    characterName: String = "Assistant",
    chatMetadata: JsonObject = buildJsonObject {},
): String {
    val lines = mutableListOf<String>()
    val header = buildJsonObject {
        put("user_name", JsonPrimitive(userName))
        put("character_name", JsonPrimitive(characterName))
        put("create_date", JsonPrimitive(conversation.createAt.toString()))
        put("chat_metadata", chatMetadata)
    }
    lines += header.toString()
    val chatArrayJson = buildTavernChatJson(conversation, TavernVariableStore.getMessageVars(conversation.id.toString()))
    val arr = runCatching { Json.parseToJsonElement(chatArrayJson) as? JsonArray }.getOrNull() ?: JsonArray(emptyList())
    for (el in arr) {
        val obj = el as? JsonObject ?: continue
        lines += obj.toString()
    }
    return lines.joinToString("\n")
}

/**
 * 从官方 SillyTavern `.jsonl` 聊天记录文本无损导入为 Conversation（支持多 swipes、is_user、is_system 隐藏标志与消息级 variables）。
 */
fun importConversationFromStJsonl(
    jsonl: String,
    assistantId: kotlin.uuid.Uuid = kotlin.uuid.Uuid.random(),
    title: String = "",
): Conversation {
    val rawLines = jsonl.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val nodes = mutableListOf<me.rerere.rikkahub.data.model.MessageNode>()
    var resolvedTitle = title
    for ((idx, line) in rawLines.withIndex()) {
        val obj = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
        // 首行若是 header（包含 user_name / character_name / chat_metadata 且无 mes 字段）则跳过消息构造
        if (idx == 0 && !obj.containsKey("mes") && (obj.containsKey("user_name") || obj.containsKey("character_name") || obj.containsKey("chat_metadata"))) {
            if (resolvedTitle.isBlank()) {
                resolvedTitle = (obj["character_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            continue
        }
        val isUser = (obj["is_user"] as? JsonPrimitive)?.booleanOrNull == true
        val isSystemHidden = (obj["is_system"] as? JsonPrimitive)?.booleanOrNull == true
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull
        val mes = (obj["mes"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val swipesArr = (obj["swipes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val swipeTexts = if (!swipesArr.isNullOrEmpty()) swipesArr else listOf(mes)
        val swipeId = ((obj["swipe_id"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, (swipeTexts.size - 1).coerceAtLeast(0))
        val role = if (isUser) MessageRole.USER else MessageRole.ASSISTANT
        val messages = swipeTexts.map { text ->
            val base = UIMessage(
                role = role,
                parts = listOf(UIMessagePart.Text(text)),
                name = name,
            )
            if (isSystemHidden) base.withHidden() else base.withoutHidden()
        }
        nodes += me.rerere.rikkahub.data.model.MessageNode(
            id = kotlin.uuid.Uuid.random(),
            messages = messages,
            selectIndex = swipeId,
        )
    }
    return Conversation(
        id = kotlin.uuid.Uuid.random(),
        assistantId = assistantId,
        title = resolvedTitle.ifBlank { "Imported ST Chat" },
        messageNodes = nodes,
    )
}
