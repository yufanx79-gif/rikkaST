package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.QuickMessage
import kotlin.uuid.Uuid

class QuickMessagesVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun addQuickMessage(title: String, content: String, autoExecute: Boolean = false) {
        val item = QuickMessage(
            title = title,
            content = content,
            autoExecute = autoExecute,
        )
        addQuickMessageItem(item)
    }

    fun addQuickMessageItem(item: QuickMessage) {
        updateQuickMessages(
            quickMessages = settings.value.quickMessages + item,
            autoBindIds = setOf(item.id),
        )
    }

    fun updateQuickMessage(updated: QuickMessage) {
        updateQuickMessages(
            settings.value.quickMessages.map { quickMessage ->
                if (quickMessage.id == updated.id) updated else quickMessage
            }
        )
    }

    fun deleteQuickMessage(id: Uuid) {
        updateQuickMessages(
            settings.value.quickMessages.filterNot { quickMessage ->
                quickMessage.id == id
            }
        )
    }

    /**
     * 导入快捷消息 / SillyTavern Quick Reply v2 JSON：
     * 兼容格式：
     * 1. SillyTavern Quick Reply v2：`{ "version": 2, "name": "SetName", "qrList": [ { "label": "...", "message": "...", ... } ] }`
     * 2. RikkaHub 导出格式：`{ "quickMessages": [ ... ] }` 或 `[ { "title": "...", "content": "..." }, ... ]`
     * 3. 单条对象：`{ "label": "...", "message": "..." }` 或 `{ "title": "...", "content": "..." }`
     *
     * 导入后自动将新增的快捷消息 ID 绑定到现有助手的 `quickMessageIds`，确保导入后在聊天输入栏立即可见可用。
     * @return 成功导入的快捷消息条数
     */
    fun importQuickMessagesFromJson(jsonText: String): Int {
        val parsed = parseQuickMessagesFromJson(jsonText)
        if (parsed.isEmpty()) return 0
        val current = settings.value.quickMessages
        val existingSignatures = current.map { "${it.setName}::${it.title}::${it.content}" }.toSet()
        val dedupedNew = parsed.filterNot { "${it.setName}::${it.title}::${it.content}" in existingSignatures }
        val toAdd = dedupedNew.ifEmpty { parsed }
        updateQuickMessages(
            quickMessages = current + toAdd,
            autoBindIds = toAdd.map { it.id }.toSet(),
        )
        return toAdd.size
    }

    /**
     * 导出当前全部快捷消息为兼容 SillyTavern Quick Reply v2 与 RikkaHub 的 JSON 字符串。
     */
    fun exportQuickMessagesToJson(setNameFilter: String? = null): String {
        val list = settings.value.quickMessages.filter {
            setNameFilter.isNullOrBlank() || it.setName == setNameFilter
        }
        val setName = setNameFilter?.takeIf { it.isNotBlank() }
            ?: list.firstOrNull()?.setName?.takeIf { it.isNotBlank() }
            ?: "Default"
        val json = Json { prettyPrint = true }
        val root = buildJsonObject {
            put("version", JsonPrimitive(2))
            put("name", JsonPrimitive(setName))
            put("disableSend", JsonPrimitive(false))
            put("placeBeforeInput", JsonPrimitive(false))
            put("injectInput", JsonPrimitive(false))
            put(
                "qrList",
                buildJsonArray {
                    list.forEachIndexed { idx, qm ->
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive(idx + 1))
                                put("uid", JsonPrimitive(qm.id.toString()))
                                put("label", JsonPrimitive(qm.title))
                                put("title", JsonPrimitive(qm.title))
                                put("message", JsonPrimitive(qm.content))
                                put("content", JsonPrimitive(qm.content))
                                put("setName", JsonPrimitive(qm.setName))
                                put("automationId", JsonPrimitive(qm.automationId))
                                put("autoExecute", JsonPrimitive(qm.autoExecute))
                                put("executeOnStartup", JsonPrimitive(qm.executeOnStartup))
                                put("executeOnUser", JsonPrimitive(qm.executeOnUser))
                                put("executeOnAi", JsonPrimitive(qm.executeOnAi))
                                put("executeOnChatChange", JsonPrimitive(qm.executeOnChatChange))
                                put("executeOnGroupMemberDraft", JsonPrimitive(qm.executeOnGroupMemberDraft))
                                put("isHidden", JsonPrimitive(qm.hidden))
                                put("hidden", JsonPrimitive(qm.hidden))
                                put("preventAutoExecute", JsonPrimitive(qm.preventAutoExecute))
                            }
                        )
                    }
                }
            )
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun updateQuickMessages(
        quickMessages: List<QuickMessage>,
        autoBindIds: Set<Uuid> = emptySet(),
    ) {
        val validIds = quickMessages.map { it.id }.toSet()
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    quickMessages = quickMessages,
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            quickMessageIds = (assistant.quickMessageIds.filter { it in validIds }.toSet() + autoBindIds)
                        )
                    }
                )
            }
        }
    }

    companion object {
        internal fun parseQuickMessagesFromJson(jsonText: String): List<QuickMessage> {
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) return emptyList()
            val root = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()
            val results = mutableListOf<QuickMessage>()

            fun JsonObject.str(vararg keys: String): String {
                for (k in keys) {
                    val v = (this[k] as? JsonPrimitive)?.contentOrNull
                    if (!v.isNullOrBlank()) return v
                }
                return ""
            }

            fun JsonObject.bool(defaultVal: Boolean = false, vararg keys: String): Boolean {
                for (k in keys) {
                    val p = this[k] as? JsonPrimitive ?: continue
                    p.booleanOrNull?.let { return it }
                    when (p.contentOrNull?.lowercase()) {
                        "true", "1", "yes", "on" -> return true
                        "false", "0", "no", "off" -> return false
                    }
                }
                return defaultVal
            }

            fun parseItem(obj: JsonObject, defaultSetName: String): QuickMessage? {
                val title = obj.str("label", "title", "name").trim()
                val content = obj.str("message", "content", "action", "script").trim()
                if (title.isEmpty() && content.isEmpty()) return null
                val setName = obj.str("setName", "set", "group").ifBlank { defaultSetName }.ifBlank { "Default" }
                val execStartup = obj.bool(false, "executeOnStartup", "execute_on_startup")
                val execUser = obj.bool(false, "executeOnUser", "execute_on_user")
                val execAi = obj.bool(false, "executeOnAi", "execute_on_ai")
                val execChat = obj.bool(false, "executeOnChatChange", "execute_on_chat_change")
                val execGroup = obj.bool(false, "executeOnGroupMemberDraft", "execute_on_group_member_draft")
                val preventAuto = obj.bool(false, "preventAutoExecute", "prevent_auto_execute")
                val isSlashScript = content.startsWith("/") || content.lineSequence().any { it.trim().startsWith("/") }
                val autoExec = obj.bool(
                    isSlashScript || execStartup || execUser || execAi || execChat || execGroup,
                    "autoExecute",
                    "auto_execute",
                )
                return QuickMessage(
                    title = title.ifEmpty { content.lineSequence().firstOrNull()?.take(24) ?: "快捷回复" },
                    content = content,
                    autoExecute = autoExec,
                    setName = setName,
                    automationId = obj.str("automationId", "automation_id"),
                    executeOnStartup = execStartup,
                    executeOnUser = execUser,
                    executeOnAi = execAi,
                    executeOnChatChange = execChat,
                    executeOnGroupMemberDraft = execGroup,
                    hidden = obj.bool(false, "isHidden", "hidden", "is_hidden"),
                    preventAutoExecute = preventAuto,
                )
            }

            fun walkElement(el: JsonElement, defaultSetName: String = "Default") {
                when (el) {
                    is JsonArray -> el.forEach { walkElement(it, defaultSetName) }
                    is JsonObject -> {
                        val setName = el.str("name", "setName").ifBlank { defaultSetName }
                        val qrList = el["qrList"] as? JsonArray
                            ?: el["quickMessages"] as? JsonArray
                            ?: el["items"] as? JsonArray
                            ?: el["list"] as? JsonArray
                        if (qrList != null) {
                            qrList.forEach { item ->
                                (item as? JsonObject)?.let { obj ->
                                    parseItem(obj, setName)?.let { results.add(it) }
                                }
                            }
                        } else {
                            parseItem(el, setName)?.let { results.add(it) }
                        }
                    }
                    else -> Unit
                }
            }

            walkElement(root)
            return results
        }
    }
}
