package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.st.runtime.TavernVariableStore

/**
 * 变量斜杠命令操作（对齐酒馆官方 variables.js 的变量家族语义）。
 * 支持本对话级（chatKey 非空且 global=false）与全局级（global=true）。
 */
enum class SlashVarOp {
    SET,
    GET,
    ADD,
    INC,
    DEC,
    FLUSH,
    LIST,
}

/**
 * 应用变量命令到 Settings 快照，并同步写入 [TavernVariableStore]（使 JS TavernHelper 与 STscript 零延迟互通）。
 *
 * @param rawScriptMode 为 true 时（STscript 管道内部调用），SET/FLUSH 返回空串或当前值、GET 未命中返回空串（对齐 ST 官方管道返回值）；
 *                      为 false 时（聊天输入框手动执行），返回人类可读提示文案。
 */
fun applyMacroVarSlash(
    context: Context,
    settings: Settings,
    op: SlashVarOp,
    name: String,
    value: String,
    chatKey: String,
    global: Boolean = false,
    rawScriptMode: Boolean = false,
): Pair<Settings, String> {
    runCatching { TavernVariableStore.ensureInitialized(context) }

    if (global) {
        val globalVars = if (TavernVariableStore.isGlobalInitialized()) {
            mutableMapOf()
        } else {
            settings.macroGlobalVariables
                .filterKeys { !TavernVariableStore.isDeleted(null, it) }
                .toMutableMap()
        }
        // 以 TavernVariableStore 中的实时全局变量为准覆盖旧快照
        runCatching {
            TavernVariableStore.getGlobal().forEach { (k, v) ->
                val strVal = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                globalVars[k] = me.rerere.rikkahub.data.st.macro.JsNumberNormalizer.getNormalized(strVal)
            }
        }
        fun currentGlobal(): String? = globalVars[name]
        val result = when (op) {
            SlashVarOp.SET -> {
                globalVars[name] = value
                if (rawScriptMode) value else "$name = $value"
            }
            SlashVarOp.GET -> currentGlobal() ?: if (rawScriptMode) "" else context.getString(R.string.slash_var_unset)
            SlashVarOp.ADD -> {
                val left = currentGlobal()?.toDoubleOrNull()
                val right = value.toDoubleOrNull()
                val next = when {
                    left != null && right != null -> {
                        val sum = left + right
                        if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
                    }
                    currentGlobal() == null -> value
                    else -> (currentGlobal() ?: "") + value
                }
                globalVars[name] = next
                next
            }
            SlashVarOp.INC -> {
                val next = (currentGlobal()?.toLongOrNull() ?: 0L) + 1L
                globalVars[name] = next.toString()
                next.toString()
            }
            SlashVarOp.DEC -> {
                val next = (currentGlobal()?.toLongOrNull() ?: 0L) - 1L
                globalVars[name] = next.toString()
                next.toString()
            }
            SlashVarOp.FLUSH -> {
                globalVars.remove(name)
                if (rawScriptMode) "" else context.getString(R.string.slash_var_deleted, name)
            }
            SlashVarOp.LIST -> {
                if (globalVars.isEmpty()) {
                    if (rawScriptMode) "[]" else context.getString(R.string.slash_var_empty)
                } else if (rawScriptMode) {
                    globalVars.keys.joinToString(",")
                } else {
                    context.getString(R.string.slash_var_list_prefix) +
                        globalVars.entries.joinToString(context.getString(R.string.slash_field_sep)) { "${it.key}=${it.value}" }
                }
            }
        }
        if (op != SlashVarOp.GET && op != SlashVarOp.LIST) {
            runCatching {
                if (op == SlashVarOp.FLUSH) {
                    TavernVariableStore.macroDelete(null, name)
                } else {
                    val existing = TavernVariableStore.getGlobal().toMutableMap()
                    globalVars[name]?.let { existing[name] = JsonPrimitive(it) }
                    TavernVariableStore.replaceGlobal(JsonObject(existing))
                }
            }
        }
        val newSettings = if (op == SlashVarOp.LIST || op == SlashVarOp.GET) settings else settings.copy(macroGlobalVariables = globalVars)
        return newSettings to result
    }

    val chatVars = settings.macroChatVariables.toMutableMap()
    val chat = if (TavernVariableStore.isChatInitialized(chatKey)) {
        mutableMapOf()
    } else {
        chatVars[chatKey]
            ?.filterKeys { !TavernVariableStore.isDeleted(chatKey, it) }
            ?.toMutableMap()
            ?: mutableMapOf()
    }
    // 以 TavernVariableStore 中的实时会话变量为准覆盖 Settings 旧快照
    runCatching {
        TavernVariableStore.getChat(chatKey).forEach { (k, v) ->
            val strVal = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            chat[k] = me.rerere.rikkahub.data.st.macro.JsNumberNormalizer.getNormalized(strVal)
        }
    }

    fun current(): String? = chat[name]
    fun store(): MutableMap<String, String> = chat

    val result = when (op) {
        SlashVarOp.SET -> {
            store()[name] = value
            if (rawScriptMode) value else "$name = $value"
        }

        SlashVarOp.GET -> current() ?: if (rawScriptMode) "" else context.getString(R.string.slash_var_unset)

        SlashVarOp.ADD -> {
            // 官方 Number() 语义：小数也按数值相加（10.5+1.5=12），结果整数时去掉 .0
            val left = current()?.toDoubleOrNull()
            val right = value.toDoubleOrNull()
            val next = when {
                left != null && right != null -> {
                    val sum = left + right
                    if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
                }
                current() == null -> value
                else -> (current() ?: "") + value
            }
            store()[name] = next
            next
        }

        SlashVarOp.INC -> {
            val next = (current()?.toLongOrNull() ?: 0L) + 1L
            store()[name] = next.toString()
            next.toString()
        }

        SlashVarOp.DEC -> {
            val next = (current()?.toLongOrNull() ?: 0L) - 1L
            store()[name] = next.toString()
            next.toString()
        }

        SlashVarOp.FLUSH -> {
            store().remove(name)
            if (rawScriptMode) "" else context.getString(R.string.slash_var_deleted, name)
        }

        SlashVarOp.LIST -> {
            if (chat.isEmpty()) {
                if (rawScriptMode) "" else context.getString(R.string.slash_var_empty)
            } else {
                context.getString(R.string.slash_var_list_prefix) + chat.entries.joinToString(context.getString(R.string.slash_field_sep)) { "${it.key}=${it.value}" }
            }
        }
    }

    if (op != SlashVarOp.GET && op != SlashVarOp.LIST) {
        runCatching {
            if (op == SlashVarOp.FLUSH) {
                TavernVariableStore.macroDelete(chatKey, name)
            } else {
                val existing = TavernVariableStore.getChat(chatKey).toMutableMap()
                chat[name]?.let { existing[name] = JsonPrimitive(it) }
                TavernVariableStore.replaceChat(chatKey, JsonObject(existing))
            }
        }
    }

    val newSettings = when {
        op == SlashVarOp.LIST || op == SlashVarOp.GET -> settings
        else -> settings.copy(macroChatVariables = chatVars.apply { put(chatKey, chat) })
    }
    return newSettings to result
}
