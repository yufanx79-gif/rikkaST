package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.isHidden
import me.rerere.rikkahub.data.model.withHidden
import me.rerere.rikkahub.data.model.withoutHidden
import me.rerere.rikkahub.data.st.expressions.ExpressionLabels
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager
import me.rerere.rikkahub.data.st.script.StSlashHost
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.SlashVarOp
import me.rerere.rikkahub.ui.components.ai.applyMacroVarSlash
import me.rerere.rikkahub.ui.hooks.ChatInputState
import kotlin.uuid.Uuid

/**
 * 构建聊天页 STscript 宿主：把 [me.rerere.rikkahub.data.st.script.StSlashExecutor]
 * 的命令路由到 ChatVM / 酒馆运行时。
 *
 * 供两条通道共用：快速回复自动执行（UI 线程）与 JS `triggerSlash`（JavaBridge 线程）。
 * 全部状态经 VM 的 StateFlow 读取，故可在任意线程调用。
 */
internal fun buildChatSlashHost(
    context: Context,
    vm: ChatVM,
    toaster: ToasterState? = null,
    inputState: ChatInputState? = null,
    onOpenConversation: ((Uuid) -> Unit)? = null,
): StSlashHost {
    fun conversationKey(): String = vm.conversation.value.id.toString()
    fun latestSettings() = vm.settings.value
    fun currentAssistant() = latestSettings().let { s ->
        s.getAssistantById(vm.conversation.value.assistantId) ?: s.getCurrentAssistant()
    }

    fun hasModel() = vm.currentChatModel.value != null
    fun toast(text: String, type: ToastType = ToastType.Normal) {
        toaster?.show(text, type = type)
    }

    /** 对话是否正在生成（等价 ST is_send_press / is_group_generating 信号）。 */
    fun isGenerating(): Boolean = vm.conversationJob.value?.isActive == true

    /** 等待生成解锁（对齐 ST waitUntilCondition）：最多等 [maxWaitMs] 毫秒；超时仍忙碌返回 false。 */
    suspend fun waitUntilNotGenerating(maxWaitMs: Long): Boolean = withTimeoutOrNull(maxWaitMs) {
        while (isGenerating()) delay(100)
    } != null

    /** 切换某个消息节点的当前 swipe 变体索引（写回会话并持久化）。 */
    fun setNodeSelectIndex(nodeId: Uuid, newIndex: Int) {
        val c = vm.conversation.value
        vm.updateConversation(
            c.copy(
                messageNodes = c.messageNodes.map { node ->
                    if (node.id == nodeId) node.copy(selectIndex = newIndex) else node
                }
            )
        )
        vm.saveConversationAsync()
    }

    return object : StSlashHost {
        override val chatKey: String?
            get() = conversationKey()

        override fun varOp(cmd: String, name: String, value: String): String? {
            val op = when (cmd) {
                "setvar" -> SlashVarOp.SET
                "getvar" -> SlashVarOp.GET
                "addvar" -> SlashVarOp.ADD
                "incvar" -> SlashVarOp.INC
                "decvar" -> SlashVarOp.DEC
                "flushvar" -> SlashVarOp.FLUSH
                "listvar" -> SlashVarOp.LIST
                else -> return null
            }
            val setting = latestSettings()
            val (newSettings, result) = applyMacroVarSlash(
                context = context,
                settings = setting,
                op = op,
                name = name,
                value = value,
                chatKey = conversationKey(),
            )
            if (newSettings !== setting) {
                vm.updateSettings(newSettings)
            }
            return result
        }

        override fun insertMessage(role: MessageRole, text: String, name: String?, at: Int?): String? {
            vm.handleInsertMessage(role, text, name, at)
            return ""
        }

        override fun trigger(): String? {
            if (!hasModel()) return "未选择模型，无法生成"
            if (vm.conversation.value.currentMessages.isEmpty()) return "没有可触发的消息"
            vm.handleTriggerGeneration()
            return ""
        }

        override fun continueGen(prompt: String): String? {
            if (!hasModel()) return "未选择模型，无法生成"
            vm.continueGeneration(prompt)
            return ""
        }

        override fun impersonate(prompt: String): String? {
            if (!hasModel()) return "未选择模型，无法生成"
            val state = inputState ?: return null
            var announced = false
            vm.impersonateDraft(prompt) { draft ->
                // 官方流式写输入框：每次 chunk 全量替换
                state.setMessageText(draft)
                if (!announced) {
                    announced = true
                    toast(context.getString(R.string.slash_toast_draft_ready))
                }
            }
            return ""
        }

        override fun sysgen(prompt: String, name: String?, at: Int?, trim: Boolean): String? {
            if (!hasModel()) return "未选择模型，无法生成"
            vm.handleGenerateSystemNarration(prompt, name, at, trim)
            return ""
        }

        override fun gen(
            prompt: String,
            asRole: String,
            lock: Boolean,
            length: Int,
            name: String?,
            trim: Boolean,
        ): String? {
            if (!hasModel()) return "未选择模型，无法生成"
            val state = inputState ?: return null
            val asEnum = when (asRole.lowercase()) {
                "char" -> ChatService.QuietPromptAs.CHAR
                else -> ChatService.QuietPromptAs.SYSTEM
            }
            val args = ChatService.GenArgs(
                prompt = prompt,
                asRole = asEnum,
                lock = lock,
                length = length,
                name = name,
                trim = trim,
            )
            state.clearInput()
            vm.quietGenerate(args) { draft ->
                state.setMessageText(draft)
                toast(
                    if (trim) context.getString(R.string.slash_toast_gen_done_replace)
                    else context.getString(R.string.slash_toast_gen_done_append)
                )
            }
            return ""
        }

        override fun persona(name: String, mode: String): String? {
            val s = latestSettings()
            val personas = s.personas
            val current = personas.find { it.id == s.activePersonaId }
            return when {
                name.isBlank() -> {
                    if (current != null) {
                        context.getString(R.string.slash_toast_persona_current, current.name)
                    } else {
                        context.getString(
                            R.string.slash_toast_persona_none_active,
                            personas.joinToString(context.getString(R.string.slash_field_sep)) { it.name },
                        )
                    }
                }

                name.equals("off", ignoreCase = true) || name.equals("none", ignoreCase = true) -> {
                    if (s.activePersonaId != null) {
                        vm.updateSettings(s.copy(activePersonaId = null))
                        context.getString(R.string.slash_toast_persona_off)
                    } else {
                        context.getString(R.string.slash_toast_persona_already_off)
                    }
                }

                else -> {
                    val target = personas.firstOrNull {
                        it.name.equals(name, ignoreCase = true) || it.title.equals(name, ignoreCase = true)
                    }
                    when {
                        // 官方 /persona-set mode=lookup：只选已有人设
                        target == null && mode == "lookup" ->
                            context.getString(R.string.slash_toast_persona_not_found, name)

                        // 官方 /persona-set mode=temp：只设置临时用户名，不找/不选人设
                        target != null && mode == "temp" -> {
                            vm.updateSettings(s.copy(displaySetting = s.displaySetting.copy(userNickname = name)))
                            context.getString(R.string.slash_toast_persona_temp_set, name)
                        }

                        // 官方 /persona-set mode=all（默认）：先找已有，找不到则设置临时用户名
                        target != null -> {
                            vm.updateSettings(s.copy(activePersonaId = target.id))
                            context.getString(R.string.slash_toast_persona_switched, target.name)
                        }

                        else -> {
                            vm.updateSettings(s.copy(displaySetting = s.displaySetting.copy(userNickname = name)))
                            context.getString(R.string.slash_toast_persona_fallback, name, name)
                        }
                    }
                }
            }
        }

        override fun renameChar(name: String): String? {
            val s = latestSettings()
            val assistant = currentAssistant()
            val updated = assistant.copy(
                name = name,
                tavernData = assistant.tavernData?.copy(name = name),
            )
            vm.updateSettings(
                s.copy(
                    assistants = s.assistants.map { if (it.id == updated.id) updated else it },
                    assistantId = updated.id,
                )
            )
            return context.getString(R.string.slash_toast_renamed, name)
        }

        override fun js(code: String): String? {
            TavernRuntimeManager.runScript(code)
            return ""
        }

        override fun tavern(sub: String): String? {
            return when (sub) {
                "reload" -> {
                    TavernRuntimeManager.reloadMvu()
                    toast(
                        context.getString(R.string.slash_toast_tavern_reloading),
                        type = ToastType.Success,
                    )
                    ""
                }

                "status" -> {
                    TavernRuntimeManager.queryMvuStatus { status ->
                        toast(context.getString(R.string.slash_toast_tavern_status, status))
                    }
                    ""
                }

                else -> "用法：/tavern reload|status"
            }
        }

        override fun echo(text: String, title: String?): String? {
            toast(text)
            return text
        }

        /**
         * /expression-fallback：读取/设置表情立绘兜底标签。
         *
         * 官方语义（expressions/index.js:792-813）：不传参数 → 返回当前兜底；
         * 传合法标签 → 设为兜底并返回；匹配不到 → 返回空串且不修改。
         * 兜底额外支持官方两个特殊选项 #none / #emoji。
         */
        override fun expressionFallback(label: String): String? {
            val s = latestSettings()
            if (label.isBlank()) return s.expressionFallbackLabel
            val normalized = when {
                label.equals(ExpressionLabels.OPTION_NONE, ignoreCase = true) -> ExpressionLabels.OPTION_NONE
                label.equals(ExpressionLabels.OPTION_EMOJI, ignoreCase = true) -> ExpressionLabels.OPTION_EMOJI
                else -> ExpressionLabels.matchLabel(label)
            } ?: return ""
            vm.updateSettings(s.copy(expressionFallbackLabel = normalized))
            return normalized
        }

        override fun hideMessages(value: String, unhide: Boolean, nameFilter: String?): String? {
            val conv = vm.conversation.value
            val nodes = conv.messageNodes
            if (nodes.isEmpty()) return ""
            val maxIndex = nodes.lastIndex
            val range = if (value.isBlank()) {
                maxIndex to maxIndex
            } else {
                parseMessageRange(value, maxIndex) ?: run {
                    toast(
                        context.getString(R.string.slash_toast_hide_invalid, value),
                        type = ToastType.Warning,
                    )
                    return ""
                }
            }
            var changed = 0
            val updatedNodes = nodes.mapIndexed { index, node ->
                if (index < range.first || index > range.second) return@mapIndexed node
                if (nameFilter != null) {
                    val current = node.messages.getOrNull(node.selectIndex) ?: return@mapIndexed node
                    if (current.name != nameFilter) return@mapIndexed node
                }
                // 对齐 ST hideChatMessageRange：隐藏作用于整条消息（全部 swipe 变体）
                val newMessages = node.messages.map { msg ->
                    if (unhide) msg.withoutHidden() else msg.withHidden()
                }
                if (newMessages == node.messages) {
                    node
                } else {
                    changed++
                    node.copy(messages = newMessages)
                }
            }
            if (changed > 0) {
                vm.updateConversation(conv.copy(messageNodes = updatedNodes))
                vm.saveConversationAsync()
                toast(
                    context.getString(
                        if (unhide) R.string.slash_toast_unhide_done else R.string.slash_toast_hide_done,
                        changed,
                    ),
                    type = ToastType.Success,
                )
            }
            return ""
        }

        override fun swipe(direction: String, await: Boolean): String? {
            if (vm.conversation.value.messageNodes.isEmpty()) {
                toast(context.getString(R.string.slash_toast_swipe_none), type = ToastType.Warning)
                return ""
            }
            vm.viewModelScope.launch {
                // 对齐 ST swipeChatCallback：先等生成解锁（10 秒超时），仍忙碌则提示并放弃
                if (!waitUntilNotGenerating(10_000)) {
                    toast(context.getString(R.string.slash_toast_swipe_busy), type = ToastType.Warning)
                    return@launch
                }
                val conv = vm.conversation.value
                val node = conv.messageNodes.lastOrNull() ?: return@launch
                if (node.messages.isEmpty()) return@launch
                val current = node.messages.getOrNull(node.selectIndex) ?: return@launch
                val lastIndex = node.messages.lastIndex

                if (direction == "left") {
                    // 对齐 ST：左向越界回绕到最后一条变体
                    val newIndex = if (node.selectIndex > 0) node.selectIndex - 1 else maxOf(0, lastIndex)
                    if (newIndex != node.selectIndex) setNodeSelectIndex(node.id, newIndex)
                    return@launch
                }

                // 右向：优先切换到下一条变体
                if (node.selectIndex < lastIndex) {
                    setNodeSelectIndex(node.id, node.selectIndex + 1)
                    return@launch
                }

                // 右向越界：对齐 ST getOverswipeBehavior —— 非用户且未隐藏 → 重新生成（生成新 swipe 变体）
                if (current.role != MessageRole.USER && !current.isHidden) {
                    if (!hasModel()) {
                        toast("未选择模型，无法生成", type = ToastType.Warning)
                        return@launch
                    }
                    vm.regenerateAtMessage(current)
                    if (await) {
                        // 先等本轮生成真正开始，再等它结束（避免任务启动竞态）
                        withTimeoutOrNull(15_000) { while (!isGenerating()) delay(50) }
                        while (isGenerating()) delay(100)
                    }
                } else {
                    // 其余（用户消息 / 已隐藏消息）：回绕到第一条变体（ST LOOP）
                    if (lastIndex > 0 && node.selectIndex != 0) setNodeSelectIndex(node.id, 0)
                }
            }
            return ""
        }

        // ---- ST Checkpoints（书签）----

        override fun checkpointCreate(mesId: Int?, name: String?): String? {
            vm.viewModelScope.launch {
                try {
                    val checkpoint = vm.createCheckpoint(mesId, name)
                    toast(
                        context.getString(R.string.slash_toast_checkpoint_created, checkpoint.title),
                        type = ToastType.Success,
                    )
                } catch (e: Exception) {
                    toast(
                        context.getString(
                            R.string.slash_toast_checkpoint_create_failed,
                            e.message ?: e.javaClass.simpleName,
                        ),
                        type = ToastType.Error,
                    )
                }
            }
            return ""
        }

        override fun checkpointGo(mesId: Int?): String? {
            val link = vm.getCheckpointLinkAt(mesId)
            if (link == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_missing), type = ToastType.Warning)
                return ""
            }
            val checkpointId = runCatching { Uuid.parse(link.conversationId) }.getOrNull()
            if (checkpointId != null) onOpenConversation?.invoke(checkpointId)
            return ""
        }

        override fun checkpointExit(): String? {
            val parentId = vm.conversation.value.stParentConversationId
            if (parentId == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_not_child), type = ToastType.Warning)
                return ""
            }
            onOpenConversation?.invoke(parentId)
            return ""
        }

        override fun checkpointParent(): String? {
            val conv = vm.conversation.value
            if (conv.stParentConversationId == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_not_child), type = ToastType.Warning)
                return ""
            }
            val link = vm.getCheckpointParentLink()
            if (link == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_parent_missing), type = ToastType.Warning)
                return ""
            }
            return link.name
        }

        override fun checkpointGet(mesId: Int?): String? {
            val link = vm.getCheckpointLinkAt(mesId)
            if (link == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_missing), type = ToastType.Warning)
                return ""
            }
            return link.name
        }

        override fun checkpointList(links: Boolean): String? {
            return buildJsonArray {
                vm.listCheckpointLinks().forEach { (index, link) ->
                    add(if (links) JsonPrimitive(link.name) else JsonPrimitive(index))
                }
            }.toString()
        }

        override fun branchCreate(mesId: Int?): String? {
            val conv = vm.conversation.value
            val index = mesId ?: conv.messageNodes.lastIndex
            val node = conv.messageNodes.getOrNull(index)
            if (node == null) {
                toast(context.getString(R.string.slash_toast_checkpoint_missing), type = ToastType.Warning)
                return ""
            }
            val message = node.messages.firstOrNull() ?: return ""
            vm.viewModelScope.launch {
                try {
                    val fork = vm.forkMessage(message)
                    onOpenConversation?.invoke(fork.id)
                } catch (e: Exception) {
                    toast(
                        context.getString(
                            R.string.slash_toast_branch_create_failed,
                            e.message ?: e.javaClass.simpleName,
                        ),
                        type = ToastType.Error,
                    )
                }
            }
            return ""
        }
    }
}

/** ST stringToRange 移植：解析 `N` 或 `start-end`（闭区间，0 基）；非法/越界返回 null。 */
internal fun parseMessageRange(value: String, maxIndex: Int): Pair<Int, Int>? {
    val text = value.trim()
    val dash = text.indexOf('-')
    val start: Int
    val end: Int
    if (dash >= 0) {
        start = text.substring(0, dash).trim().toIntOrNull() ?: return null
        end = text.substring(dash + 1).trim().toIntOrNull() ?: return null
    } else {
        start = text.toIntOrNull() ?: return null
        end = start
    }
    if (start > end || start < 0 || end > maxIndex) return null
    return start to end
}
