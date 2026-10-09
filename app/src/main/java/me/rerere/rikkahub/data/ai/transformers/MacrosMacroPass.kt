package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager

/**
 * [v234 S2] MacrosParser 函数宏展开 pass。
 *
 * 背景：ST MacrosParser.registerMacro(name, fn) 的函数宏在旧解析器里按 `fn(nonce)` 调用；
 * 宿主宏引擎是纯 Kotlin（data.st.macro），无法直接执行 JS 函数。
 *
 * 方案（对齐 ST scripts/macros.js #registerMacroInNewEngine 的 handler 包装语义）：
 * 1. StMacroSupport 把 JS 函数宏注册为动态宏，展开时产出哨兵 {{__rikka_fn::name::nonce}}；
 * 2. 本 pass 在 PlaceholderTransformer 之后扫描消息文本，收集全部哨兵；
 * 3. 经 TavernRuntimeManager.runMacrosBatch 挂起回运行时 JS 逐个调用 fn(nonce)；
 * 4. 用返回值（sanitizeMacroValue 语义：null→空串、对象→JSON）回填文本；
 *    超时 / WebView 未就绪 / 无函数宏时零开销直通（哨兵保留原文，绝不破坏 prompt）。
 */
object MacrosMacroPass : InputMessageTransformer {

    // [v236.1 P0] ICU（Android）拒绝裸 `}}`：`Pattern.compile` 在桌面 JVM 容忍、在 Android
    // 的 com.android.icu 实现直接抛 PatternSyntaxException（oc7.<clinit> -> ExceptionInInitializerError
    // -> NoClassDefFoundError，表现为「消息生成失败 / 群聊闪退」）。必须写成 \}\}。
    internal val SENTINEL = Regex("\\{\\{__rikka_fn::([^:}]+)::([^}]+)\\}\\}")

    /** 文本中是否含函数宏哨兵（快速短路） */
    fun containsSentinel(text: String): Boolean = text.contains("{{__rikka_fn::")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 收集 [messageIndex, partIndex] + 每个文本的全部哨兵
        val targets = mutableListOf<IntArray>()
        messages.forEachIndexed { mi, msg ->
            msg.parts.forEachIndexed { pi, part ->
                if (part is UIMessagePart.Text && containsSentinel(part.text)) {
                    targets.add(intArrayOf(mi, pi))
                }
            }
        }
        if (targets.isEmpty()) return messages

        // 全量请求（跨消息去重 nonce）
        data class Req(val id: Int, val name: String, val nonce: String, val mi: Int, val pi: Int)
        val requests = mutableListOf<Req>()
        val perText = HashMap<Int, List<Req>>()
        targets.forEachIndexed { idx, coord ->
            val text = (messages[coord[0]].parts[coord[1]] as UIMessagePart.Text).text
            val reqs = SENTINEL.findAll(text).mapIndexed { i, m ->
                Req(idx * 1000 + i, m.groupValues[1], m.groupValues[2], coord[0], coord[1])
            }.toList()
            if (reqs.isNotEmpty()) {
                perText[idx] = reqs
                requests.addAll(reqs)
            }
        }
        if (requests.isEmpty()) return messages

        val requestsJson = buildString {
            append("[")
            requests.forEachIndexed { i, r ->
                if (i > 0) append(",")
                append("{\"id\":").append(r.id)
                    .append(",\"name\":\"").append(r.name.replace("\"", ""))
                    .append("\",\"nonce\":\"").append(r.nonce).append("\"}")
            }
            append("]")
        }
        val results = TavernRuntimeManager.runMacrosBatch(requestsJson) ?: return messages
        val valueById = results.associate { it.first to it.second }

        // 回填：按 messageIndex 分组替换
        val replByMsg = HashMap<Int, HashMap<Int, String>>()
        perText.forEach { (idx, reqs) ->
            val coord = targets[idx]
            val text = (messages[coord[0]].parts[coord[1]] as UIMessagePart.Text).text
            var out = text
            reqs.forEach { r ->
                val v = valueById[r.id]
                if (v != null) {
                    out = out.replace("{{__rikka_fn::" + r.name + "::" + r.nonce + "}}", v)
                }
            }
            replByMsg.getOrPut(coord[0]) { HashMap() }[coord[1]] = out
        }

        return messages.mapIndexed { mi, msg ->
            val partMap = replByMsg[mi] ?: return@mapIndexed msg
            msg.copy(
                parts = msg.parts.mapIndexed { pi, part ->
                    val t = partMap[pi]
                    if (t != null && part is UIMessagePart.Text) part.copy(text = t) else part
                }
            )
        }
    }
}
