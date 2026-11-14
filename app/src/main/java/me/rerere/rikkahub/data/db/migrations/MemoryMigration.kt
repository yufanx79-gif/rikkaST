package me.rerere.rikkahub.data.db.migrations

/**
 * 把 rikkaST 旧版逐条记忆（`MemoryEntity.content`）拼成一份 `profile.md` 文件。
 *
 * 旧实现每条记忆都会完整注入 prompt，迁到同样会完整注入的 `profile` 里，
 * 升级后模型看到的内容不变。每行加上 `[stated]` 标记，与新的记忆格式对齐。
 *
 * 抽成顶层纯函数是为了让单测能直接验证拼接结果 —— 迁移只跑一次，
 * 跑错了用户的老记忆就没了，必须能脱离 Room 测。
 *
 * @return 没有任何有效内容时返回 null（调用方据此跳过该助手，不写空文件）
 */
internal fun legacyMemoriesToProfile(contents: List<String>): String? {
    val lines = contents
        .map { it.lines().joinToString(" ") { line -> line.trim() }.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    return buildString {
        appendLine("---")
        appendLine("name: profile")
        appendLine("description: Who the user is")
        appendLine("---")
        appendLine()
        lines.forEach { appendLine("- [stated] $it") }
    }
}
