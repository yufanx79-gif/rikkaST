package me.rerere.rikkahub.data.model

/**
 * 世界书条目装饰器（`@@activate` / `@@dont_activate`）。
 *
 * 移植自 SillyTavern 1.18.0 `public/scripts/world-info.js`：
 * - [WORLD_INFO_DECORATORS] ← `KNOWN_DECORATORS`（world-info.js:100，官方**只有这 2 个**）；
 * - [parseWorldInfoDecorators] ← `parseDecorators()`（world-info.js:4538-4582）；
 * - 扫描循环里的判定顺序 ← `checkWorldInfo`（world-info.js:4763-4770）：
 *   `@@activate` 无条件激活 → `@@dont_activate` 无条件跳过，且都发生在关键词匹配之前。
 *
 * 与存储层的关系（硬约束 #1）：官方在 `getSortedEntries` 里把剥壳后的结果写进条目副本
 * （`{ ...entry, decorators, content }`），rikkaST 不改存储结构 —— [PromptInjection.RegexInjection]
 * 的持久化字段保持原样，装饰器通过下面的**派生**属性在扫描时即时解析，
 * 因此角色卡/世界书的导入导出仍然无损（`@@` 行原样保留）。
 *
 * 官方 parseDecorators 的逐字行为（含 `@@@` 转义细节，已按源码对齐）：
 * 1. content 不以 `@@` 开头 → 无装饰器、内容原样；
 * 2. 逐行扫描开头连续的 `@@` 行：
 *    - 以 `@@@` 开头且尚未 fallback → 跳过（不记录、不打断）；
 *    - 行（剥掉至多一个 `@` 后）`startsWith` 任一已知装饰器 → 记录该行（`@@@x` 记录成 `@@x`），
 *      并复位 fallback 标记；
 *    - 否则（未知装饰器）→ fallback 标记置位（后续 `@@@x` 按转义还原成 `@@x` 记录）；
 *    - 否则该行不属于装饰器 → 从该行起（含）作为内容，结束扫描；
 * 3. 若所有行都是 `@@` 行（没有触发结束分支），内容保持**原样**（官方同样如此）。
 *
 * 注意：官方判定「已知」用的是 `startsWith`，而扫描处判定激活用的是 `includes`（精确相等），
 * 因此 `@@activate_extra` 会被当作「已知装饰器」记录、但**不会**触发激活 —— 本实现保持一致。
 */
const val WORLD_INFO_DECORATOR_ACTIVATE = "@@activate"
const val WORLD_INFO_DECORATOR_DONT_ACTIVATE = "@@dont_activate"

/** 官方 KNOWN_DECORATORS（world-info.js:100） */
val WORLD_INFO_DECORATORS = listOf(WORLD_INFO_DECORATOR_ACTIVATE, WORLD_INFO_DECORATOR_DONT_ACTIVATE)

/** 装饰器解析结果：装饰器列表 + 剥壳后的内容（官方 parseDecorators 的返回值） */
data class ParsedWorldInfoDecorators(
    val decorators: List<String>,
    val content: String,
)

/** 官方 `isKnownDecorator`：`@@@x` 先剥一个 `@`，再按 startsWith 判定。 */
private fun isKnownWorldInfoDecorator(data: String): Boolean {
    val normalized = if (data.startsWith("@@@")) data.substring(1) else data
    return WORLD_INFO_DECORATORS.any { normalized.startsWith(it) }
}

/** 官方 `parseDecorators(content)` 的逐字移植（见文件头注释）。 */
fun parseWorldInfoDecorators(content: String): ParsedWorldInfoDecorators {
    if (!content.startsWith("@@")) return ParsedWorldInfoDecorators(emptyList(), content)

    var newContent = content
    val lines = content.split("\n")
    val decorators = mutableListOf<String>()
    var fallbacked = false

    for (i in lines.indices) {
        val line = lines[i]
        if (line.startsWith("@@")) {
            if (line.startsWith("@@@") && !fallbacked) continue
            if (isKnownWorldInfoDecorator(line)) {
                decorators += if (line.startsWith("@@@")) line.substring(1) else line
                fallbacked = false
            } else {
                fallbacked = true
            }
        } else {
            newContent = lines.subList(i, lines.size).joinToString("\n")
            break
        }
    }
    return ParsedWorldInfoDecorators(decorators, newContent)
}

/**
 * 派生字段：条目装饰器列表（官方 `entry.decorators`）。
 * 不落库、不改序列化，供世界书扫描在关键词判定前读取。
 */
val PromptInjection.RegexInjection.worldInfoDecorators: List<String>
    get() = parseWorldInfoDecorators(content).decorators

/**
 * 派生字段：剥壳后的扫描内容（官方 `entry.content` 改写后的值）。
 * 仅用于构造扫描副本；条目存储的 [PromptInjection.RegexInjection.content] 不会被修改。
 */
val PromptInjection.RegexInjection.contentWithoutDecorators: String
    get() = parseWorldInfoDecorators(content).content