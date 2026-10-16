package me.rerere.rikkahub.data.model

/**
 * 世界书条目装饰器（`@@activate` / `@@dont_activate` 及 CCv3 扩展装饰器）。
 *
 * 移植自 SillyTavern 1.18.0 `public/scripts/world-info.js` (`parseDecorators`) 与 CCv3 规范。
 */
const val WORLD_INFO_DECORATOR_ACTIVATE = "@@activate"
const val WORLD_INFO_DECORATOR_DONT_ACTIVATE = "@@dont_activate"
const val WORLD_INFO_DECORATOR_DEPTH = "@@depth"
const val WORLD_INFO_DECORATOR_ROLE = "@@role"
const val WORLD_INFO_DECORATOR_POSITION = "@@position"
const val WORLD_INFO_DECORATOR_SCAN_DEPTH = "@@scan_depth"
const val WORLD_INFO_DECORATOR_INSTRUCT_SCAN_DEPTH = "@@instruct_scan_depth"
const val WORLD_INFO_DECORATOR_OUTLET = "@@outlet"
const val WORLD_INFO_DECORATOR_IGNORE_MAX_CONTEXT = "@@ignore_on_max_context"
const val WORLD_INFO_DECORATOR_ADDITIONAL_KEYS = "@@additional_keys"
const val WORLD_INFO_DECORATOR_EXCLUDE_KEYS = "@@exclude_keys"

/** 官方 KNOWN_DECORATORS（world-info.js:100）+ CCv3 扩展装饰器 */
val WORLD_INFO_DECORATORS = listOf(
    WORLD_INFO_DECORATOR_ACTIVATE,
    WORLD_INFO_DECORATOR_DONT_ACTIVATE,
    WORLD_INFO_DECORATOR_DEPTH,
    WORLD_INFO_DECORATOR_ROLE,
    WORLD_INFO_DECORATOR_POSITION,
    WORLD_INFO_DECORATOR_SCAN_DEPTH,
    WORLD_INFO_DECORATOR_INSTRUCT_SCAN_DEPTH,
    WORLD_INFO_DECORATOR_OUTLET,
    WORLD_INFO_DECORATOR_IGNORE_MAX_CONTEXT,
    WORLD_INFO_DECORATOR_ADDITIONAL_KEYS,
    WORLD_INFO_DECORATOR_EXCLUDE_KEYS,
)

private fun extractDecoratorArg(line: String, prefix: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith(prefix)) return null
    val rest = trimmed.substring(prefix.length)
    if (rest.isEmpty() || (rest[0] != ' ' && rest[0] != '=' && rest[0] != ':')) return null
    return rest.trimStart(' ', '=', ':').trim()
}

private fun mapDecoratorPosition(arg: String): InjectionPosition? = when (arg.lowercase()) {
    "before_char", "before", "0" -> InjectionPosition.BEFORE_CHARACTER
    "after_char", "after", "1" -> InjectionPosition.AFTER_CHARACTER
    "antop", "before_an", "2" -> InjectionPosition.BEFORE_AUTHOR_NOTE
    "anbottom", "after_an", "3" -> InjectionPosition.AFTER_AUTHOR_NOTE
    "at_depth", "atdepth", "in_chat", "4" -> InjectionPosition.AT_DEPTH
    "emtop", "5" -> InjectionPosition.EM_TOP
    "embottom", "6" -> InjectionPosition.EM_BOTTOM
    "outlet", "7" -> InjectionPosition.OUTLET
    else -> null
}

/** 装饰器解析结果：装饰器列表 + 剥壳后的内容（官方 parseDecorators 的返回值）及 CCv3 派生覆盖属性 */
data class ParsedWorldInfoDecorators(
    val decorators: List<String>,
    val content: String,
) {
    val depthOverride: Int?
        get() = decorators.mapNotNull { raw ->
            extractDecoratorArg(raw, WORLD_INFO_DECORATOR_DEPTH)?.toIntOrNull()?.takeIf { it >= 0 }
        }.lastOrNull()

    val roleOverride: String?
        get() = decorators.mapNotNull { raw ->
            val arg = extractDecoratorArg(raw, WORLD_INFO_DECORATOR_ROLE)?.lowercase() ?: return@mapNotNull null
            when (arg) {
                "user", "1" -> "user"
                "assistant", "2" -> "assistant"
                "system", "0" -> "system"
                else -> null
            }
        }.lastOrNull()

    val positionOverride: InjectionPosition?
        get() = decorators.mapNotNull { raw ->
            when {
                extractDecoratorArg(raw, WORLD_INFO_DECORATOR_DEPTH)?.toIntOrNull()?.let { it >= 0 } == true ->
                    InjectionPosition.AT_DEPTH
                extractDecoratorArg(raw, WORLD_INFO_DECORATOR_OUTLET)?.isNotEmpty() == true ->
                    InjectionPosition.OUTLET
                else -> extractDecoratorArg(raw, WORLD_INFO_DECORATOR_POSITION)?.let(::mapDecoratorPosition)
            }
        }.lastOrNull()

    val scanDepthOverride: Int?
        get() = decorators.mapNotNull { raw ->
            val arg = extractDecoratorArg(raw, WORLD_INFO_DECORATOR_INSTRUCT_SCAN_DEPTH)
                ?: extractDecoratorArg(raw, WORLD_INFO_DECORATOR_SCAN_DEPTH)
            arg?.toIntOrNull()?.takeIf { it >= 0 }
        }.lastOrNull()

    val outletOverride: String?
        get() = decorators.mapNotNull { raw ->
            extractDecoratorArg(raw, WORLD_INFO_DECORATOR_OUTLET)?.takeIf { it.isNotEmpty() }
        }.lastOrNull()

    val ignoreOnMaxContext: Boolean
        get() = decorators.any { it.trim() == WORLD_INFO_DECORATOR_IGNORE_MAX_CONTEXT }

    val additionalKeys: List<String>
        get() = decorators.flatMap { raw ->
            extractDecoratorArg(raw, WORLD_INFO_DECORATOR_ADDITIONAL_KEYS)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }.distinct()

    val excludeKeys: List<String>
        get() = decorators.flatMap { raw ->
            extractDecoratorArg(raw, WORLD_INFO_DECORATOR_EXCLUDE_KEYS)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        }.distinct()

    fun applyWorldInfoDecoratorOverrides(entry: PromptInjection.RegexInjection): PromptInjection.RegexInjection =
        entry.applyWorldInfoDecoratorOverrides(decorators)
}

/** 官方 `isKnownDecorator`：`@@@x` 先剥一个 `@`，再按 startsWith 判定。 */
private fun isKnownWorldInfoDecorator(data: String): Boolean {
    val normalized = if (data.startsWith("@@@")) data.substring(1) else data
    return WORLD_INFO_DECORATORS.any { normalized.startsWith(it) }
}

/** 官方 `parseDecorators(content)` 的逐字移植（见文件头注释）。 */
fun parseWorldInfoDecorators(content: String): ParsedWorldInfoDecorators {
    if (!content.startsWith("@@")) return ParsedWorldInfoDecorators(decorators = emptyList(), content = content)

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
    return ParsedWorldInfoDecorators(decorators = decorators, content = newContent)
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

/**
 * 顶层重载：满足 `me.rerere.rikkahub.data.model.applyWorldInfoDecoratorOverrides(baseEntry)` 直接调用。
 */
@JvmName("applyWorldInfoDecoratorOverridesTopLevel")
fun applyWorldInfoDecoratorOverrides(
    entry: PromptInjection.RegexInjection,
    decorators: List<String> = entry.worldInfoDecorators,
): PromptInjection.RegexInjection = entry.applyWorldInfoDecoratorOverrides(decorators)

/**
 * 将条目装饰器中的属性覆盖（`@@depth`、`@@role`、`@@position`、`@@scan_depth`、`@@outlet`、
 * `@@ignore_on_max_context`、`@@additional_keys`、`@@exclude_keys`）应用到扫描副本上。
 */
fun PromptInjection.RegexInjection.applyWorldInfoDecoratorOverrides(
    decorators: List<String> = worldInfoDecorators,
): PromptInjection.RegexInjection {
    if (decorators.isEmpty()) return this
    var current = this
    for (raw in decorators) {
        val trimmed = raw.trim()
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_DEPTH)?.toIntOrNull()?.takeIf { it >= 0 }?.let { d ->
            current = current.copy(position = InjectionPosition.AT_DEPTH, injectDepth = d)
            continue
        }
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_ROLE)?.lowercase()?.let { arg ->
            val r = when (arg) {
                "user", "1" -> me.rerere.ai.core.MessageRole.USER
                "assistant", "2" -> me.rerere.ai.core.MessageRole.ASSISTANT
                "system", "0" -> me.rerere.ai.core.MessageRole.SYSTEM
                else -> null
            }
            if (r != null) current = current.copy(role = r)
            continue
        }
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_POSITION)?.let { arg ->
            val pos = mapDecoratorPosition(arg)
            if (pos != null) current = current.copy(position = pos)
            continue
        }
        val sdArg = extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_INSTRUCT_SCAN_DEPTH)
            ?: extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_SCAN_DEPTH)
        if (sdArg != null) {
            val sd = sdArg.toIntOrNull()
            if (sd != null && sd >= 0) current = current.copy(scanDepth = sd)
            continue
        }
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_OUTLET)?.takeIf { it.isNotEmpty() }?.let { name ->
            current = current.copy(position = InjectionPosition.OUTLET, outletName = name)
            continue
        }
        if (trimmed == WORLD_INFO_DECORATOR_IGNORE_MAX_CONTEXT) {
            current = current.copy(ignoreBudget = true)
            continue
        }
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_ADDITIONAL_KEYS)?.let { arg ->
            val keys = arg.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (keys.isNotEmpty()) {
                current = current.copy(keywords = (current.keywords + keys).distinct())
            }
            continue
        }
        extractDecoratorArg(trimmed, WORLD_INFO_DECORATOR_EXCLUDE_KEYS)?.let { arg ->
            val keys = arg.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (keys.isNotEmpty()) {
                current = current.copy(excludeKeys = (current.excludeKeys + keys).distinct())
            }
            continue
        }
    }
    return current
}
