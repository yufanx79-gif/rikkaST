package me.rerere.rikkahub.data.st.script

/**
 * STscript 简化版解析器（对齐 SillyTavern `slash-commands` 的语法核心子集）。
 *
 * 支持：
 * - `|` 管道串联：`/send hi | /trigger`；
 * - 引号/括号感知：`"a | b"`、`'a | b'`、`{{pick::a|b}}`、`(a|b)`、`[a|b]` 内的 `|` 不切分；
 * - 每段形如 `/name args...`：命令名取到首个空白并小写化，args 保留原文；
 * - 非 `/` 开头的段视为纯文本（管道值）；
 * - 命名参数工具：`key=value` / `key="quoted value"` 提取。
 *
 * 说明：这不是 ST 完整 AST解析器（控制流 /if /while 等暂不含），
 * 是面向脚本生态高频用法的务实子集；后续可在此稳步扩展。
 */
object StSlashParser {

    data class Segment(
        /** 原文（trim 后） */
        val raw: String,
        /** 命令名（小写，不含 `/`）；非命令段为空串 */
        val command: String,
        /** 命令参数原文；非命令段为整段文本 */
        val args: String,
        /** 是否为 `/` 命令段 */
        val isCommand: Boolean,
        /** `||` 标志：该段禁用"上一段输出自动注入"（对齐官方 no pipe injection；`{{pipe}}` 宏不受影响） */
        val noPipeInject: Boolean = false,
    )

    /** 切分片段（含 `||` 标志）。 */
    data class Part(val text: String, val noPipeInject: Boolean)

    /** 按 `|` 切分（引号/括号感知），返回非空片段（含 `||` 标志）。 */
    fun splitParts(text: String): List<Part> {
        val out = mutableListOf<Part>()
        val sb = StringBuilder()
        var pendingNoPipe = false
        var inDouble = false
        var inSingle = false
        var braceDepth = 0
        var parenDepth = 0
        var bracketDepth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            // 转义字符：反斜杠后接引号或竖线时跳过（原样保留）
            if (c == '\\' && i + 1 < text.length &&
                (text[i + 1] == '"' || text[i + 1] == '\'' || text[i + 1] == '|')
            ) {
                sb.append(c).append(text[i + 1])
                i += 2
                continue
            }
            when {
                c == '"' && !inSingle -> inDouble = !inDouble
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '{' && !inDouble && !inSingle -> braceDepth++
                c == '}' && !inDouble && !inSingle -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
                c == '(' && !inDouble && !inSingle -> parenDepth++
                c == ')' && !inDouble && !inSingle -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                c == '[' && !inDouble && !inSingle -> bracketDepth++
                c == ']' && !inDouble && !inSingle -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
            }
            if (c == '|' && !inDouble && !inSingle &&
                braceDepth == 0 && parenDepth == 0 && bracketDepth == 0
            ) {
                val piece = sb.toString()
                sb.clear()
                if (piece.isNotBlank()) {
                    out.add(Part(piece.trim(), pendingNoPipe))
                    pendingNoPipe = false
                }
                // `||`：下一段禁用管道注入（对齐官方 no pipe injection）
                if (i + 1 < text.length && text[i + 1] == '|') {
                    pendingNoPipe = true
                    i++
                }
                i++
                continue
            }
            sb.append(c)
            i++
        }
        if (sb.isNotBlank()) out.add(Part(sb.toString().trim(), pendingNoPipe))
        return out
    }

    /** 按 `|` 切分（仅文本）。 */
    fun splitPipelines(text: String): List<String> = splitParts(text).map { it.text }

    /** 解析为命令片段列表（含 `||` 标志）。 */
    fun parseParts(text: String): List<Segment> = splitParts(text).map { part ->
        val t = part.text
        if (t.startsWith("/")) {
            val body = t.substring(1).trimStart()
            val name = body.takeWhile { !it.isWhitespace() }.lowercase()
            val args = body.drop(name.length).trim()
            Segment(raw = t, command = name, args = args, isCommand = true, noPipeInject = part.noPipeInject)
        } else {
            Segment(raw = t, command = "", args = t, isCommand = false, noPipeInject = part.noPipeInject)
        }
    }

    /** 解析为命令片段列表（兼容入口）。 */
    fun parse(text: String): List<Segment> = parseParts(text)

    /**
     * 提取命名参数 `key=value` / `key="quoted value"`。
     *
     * @return (命名参数表（key 小写）, 其余文本)
     */
    fun extractNamedArgs(args: String): Pair<Map<String, String>, String> {
        val named = linkedMapOf<String, String>()
        val rest = StringBuilder()
        var i = 0
        val n = args.length
        while (i < n) {
            while (i < n && args[i].isWhitespace()) i++
            if (i >= n) break
            val start = i
            while (i < n && !args[i].isWhitespace() && args[i] != '=') i++
            if (i < n && args[i] == '=' && i > start) {
                val key = args.substring(start, i)
                i++ // 跳过 '='
                val (value, next) = readValue(args, i)
                named[key.lowercase()] = value
                i = next
            } else {
                // 非命名参数（普通 token）落入其余文本
                rest.append(args, start, i).append(' ')
            }
        }
        return named to rest.toString().trim()
    }

    /**
     * 去除整段外层引号（`"..."` / `'...'`），无引号时原样返回。
     * 用于命令文本参数：`/send "hello world"` → `hello world`。
     */
    fun unwrapQuotes(text: String): String {
        val t = text.trim()
        if (t.length >= 2 && (t.first() == '"' && t.last() == '"' || t.first() == '\'' && t.last() == '\'')) {
            return t.substring(1, t.length - 1)
        }
        return t
    }

    /** 裸值读取：带引号 → 读到配对引号（支持 \\ 转义）；否则读到空白。 */
    private fun readValue(args: String, from: Int): Pair<String, Int> {
        var i = from
        if (i < args.length && (args[i] == '"' || args[i] == '\'')) {
            val quote = args[i]
            val sb = StringBuilder()
            i++
            while (i < args.length && args[i] != quote) {
                if (args[i] == '\\' && i + 1 < args.length) {
                    sb.append(args[i + 1])
                    i += 2
                } else {
                    sb.append(args[i])
                    i++
                }
            }
            if (i < args.length) i++ // 跳过闭合引号
            return sb.toString() to i
        }
        val start = i
        while (i < args.length && !args[i].isWhitespace()) i++
        return args.substring(start, i) to i
    }
}