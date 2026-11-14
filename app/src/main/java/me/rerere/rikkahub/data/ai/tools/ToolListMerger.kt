package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import java.util.Locale

/**
 * 把「调用方已经组装好的工具清单」和「记忆工具」合成最终发给模型的清单。
 *
 * 存在的理由是一个真实事故：`ChatToolFactory.createTools()` 在 `assistant.enableMemory`
 * 时已经注入了一组 `memory_*` 工具，而 `GenerationLoop` 又无条件补了一遍，于是每次请求
 * 都带 6 个重名工具。DeepSeek 对同名工具直接拒单 —— `Tool names must be unique.` ——
 * 整个请求 400，用户看到的就是「一点发送就失败」。
 *
 * 但直接把补记忆工具那段删掉也不行：群聊（`ChatService.generateForAssistant`）是手写工具
 * 清单、不走 [ChatToolFactory]，它的记忆工具本来就靠这里补上。
 *
 * 所以规则是：**缺了才补，补完按名去重**。
 *
 * @param existing 调用方已组装好的工具（可能已含记忆工具，也可能不含）
 * @param memoryTools 记忆工具的工厂，只在 [existing] 里一个记忆工具都没有时才调用
 * @param includeMemory 是否允许补记忆工具（工具策略为 NONE 时传 false）
 */
internal fun mergeToolsWithMemory(
    existing: List<Tool>,
    includeMemory: Boolean,
    memoryTools: () -> List<Tool>,
): List<Tool> {
    val merged = ArrayList<Tool>(existing.size + MemoryToolNames.ALL.size)
    merged += existing
    if (includeMemory && existing.none { it.name in MemoryToolNames.ALL }) {
        merged += memoryTools()
    }
    return dedupeToolNames(merged)
}

/**
 * 按工具名去重，保留先出现的那个。
 *
 * 大小写不敏感：MCP 服务器名会拼进工具名（`mcp__<server>__<tool>`），只差大小写也算重名。
 * Provider 侧是精确字符串比较，这里放宽一格只会更安全。
 *
 * 上游 `deduplicateTools` 只在主聊天路径调用过，群聊 / Codex 等路径没有，所以这里必须再兜一层。
 */
internal fun dedupeToolNames(tools: List<Tool>): List<Tool> {
    if (tools.size < 2) return tools
    val seen = LinkedHashSet<String>(tools.size)
    return tools.filter { seen.add(it.name.lowercase(Locale.ROOT)) }
}

/** [dedupeToolNames] 丢掉了哪些名字，用于诊断日志 */
internal fun duplicateToolNames(tools: List<Tool>): List<String> {
    if (tools.size < 2) return emptyList()
    val seen = HashSet<String>(tools.size)
    return tools.mapNotNull { tool ->
        if (seen.add(tool.name.lowercase(Locale.ROOT))) null else tool.name
    }
}
