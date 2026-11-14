package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Avatar
import java.io.File

/**
 * 启动时的**路径自愈**：把「指向别的包名」的绝对路径改回本机路径。
 *
 * ## 为什么需要它（而不是只在恢复时改写）
 *
 * 2.5.14 之前的版本恢复官方 RikkaHub 备份时，会把
 * `file:///data/user/0/me.rerere.rikkahub/files/upload/xxx.jpg` 原样写进设置和消息里。
 * 本应用包名是 `me.rerere.rikkahub.st`，这些路径指向不存在的目录，
 * 于是头像、聊天背景、消息附件全部空白。
 *
 * 只在「恢复」时修的话，已经恢复过的用户不会自动好转，必须重装重恢复 —— 体验很差。
 * 这里在每次启动时扫一遍。
 *
 * ## 安全边界（关键）
 *
 * 只有**同时**满足才改写：
 *  1. 形如 `[file://]/data/user/<n>/<包名>/files/<相对路径>`；
 *  2. `<包名>` 不等于当前包名；
 *  3. 改写后对应的**文件在本机真实存在**。
 *
 * 第 3 条是安全底线：它保证只修「文件已经恢复了、只是路径写错」的情况，
 * 绝不会把一条本来有效的外部路径改成无效路径。
 * 也正因如此，本方法**幂等** —— 修好之后路径已是本机包名，不再匹配条件。
 */
internal object BackupPathHealer {

    private const val TAG = "BackupPathHealer"

    /** `[file://]/data/user/<n>/<pkg>/files/` 之后的部分就是相对 filesDir 的路径。 */
    private val ABSOLUTE_PATH = Regex(
        """^(?:file://)?/data/user/\d+/([A-Za-z0-9_.]+)/files/(.+)$"""
    )

    /**
     * 把一条绝对路径改写成「本机 filesDir 下的相对路径」。
     *
     * @return 改写后的路径；无需改写、或改写目标文件不存在时返回 `null`。
     */
    fun rebase(context: Context, path: String): String? =
        rebase(path, context.packageName, context.filesDir.absolutePath) { relative ->
            File(context.filesDir, relative).exists()
        }

    /**
     * 不依赖 [Context] 的核心实现，便于单测覆盖安全边界。
     *
     * @param path 原始路径
     * @param currentPackage 当前应用包名
     * @param filesDirAbsolute 当前应用 filesDir 的绝对路径
     * @param exists 判断「相对 filesDir 的路径」在本机是否存在
     * @return 改写后的路径；无需改写或目标不存在时返回 `null`
     */
    internal fun rebase(
        path: String,
        currentPackage: String,
        filesDirAbsolute: String,
        exists: (String) -> Boolean,
    ): String? {
        val match = ABSOLUTE_PATH.matchEntire(path) ?: return null
        val packageName = match.groupValues[1]
        val relative = match.groupValues[2]
        // 已经是本机包名：幂等，不动。
        if (packageName == currentPackage) return null

        // 安全底线：目标文件必须真的在本机存在，否则不改。
        // 用户可能只恢复了设置而没恢复文件（只勾选「设置」），
        // 那种情况下保持原样，比改成一个同样不存在的路径更好排查。
        if (!exists(relative)) return null

        val scheme = if (path.startsWith("file://")) "file://" else ""
        return "$scheme$filesDirAbsolute/$relative"
    }

    /**
     * 修复设置里的路径：助手头像、助手聊天背景、用户头像。
     *
     * @return 修复的条目数。
     */
    suspend fun healSettings(context: Context, settingsStore: SettingsStore): Int {
        return runCatching {
            var changes = 0
            settingsStore.update { settings ->
                var touched = false

                val assistants = settings.assistants.map { assistant ->
                    var updated = assistant
                    // 头像
                    val avatar = assistant.avatar
                    if (avatar is Avatar.Image) {
                        rebase(context, avatar.url)?.let { fixed ->
                            updated = updated.copy(avatar = Avatar.Image(fixed))
                            touched = true
                        }
                    }
                    // 聊天背景
                    assistant.background?.let { background ->
                        rebase(context, background)?.let { fixed ->
                            updated = updated.copy(background = fixed)
                            touched = true
                        }
                    }
                    updated
                }

                // 用户头像
                var displaySetting = settings.displaySetting
                val userAvatar = displaySetting.userAvatar
                if (userAvatar is Avatar.Image) {
                    rebase(context, userAvatar.url)?.let { fixed ->
                        displaySetting = displaySetting.copy(userAvatar = Avatar.Image(fixed))
                        touched = true
                    }
                }

                if (!touched) return@update settings
                changes++
                settings.copy(assistants = assistants, displaySetting = displaySetting)
            }
            changes
        }.onFailure {
            Log.e(TAG, "healSettings failed", it)
        }.getOrDefault(0)
    }

    /**
     * 修复数据库里消息附件的绝对路径（`message_node.messages`）。
     *
     * 走 SQLite 直连而不是 Room：这里只想改数据，
     * 不想因为打开 Room 而触发迁移/校验的副作用。
     *
     * @return 被更新的行数。
     */
    fun healDatabase(
        context: Context,
        databaseFile: File,
        filesRoot: File = context.filesDir,
    ): Int {
        if (!databaseFile.isFile || databaseFile.length() == 0L) return 0
        return runCatching {
            val configuration = me.rerere.rikkahub.data.db.SQLiteConfiguration.configure(
                context,
                io.requery.android.database.sqlite.SQLiteDatabaseConfiguration(
                    databaseFile.absolutePath,
                    io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
                ),
            )
            val database = io.requery.android.database.sqlite.SQLiteDatabase
                .openDatabase(configuration, null) { error("Database is corrupt") }
            var updated = 0
            database.use { db ->
                // 只扫描可能含路径的列：消息 JSON 和若干文本列。
                val candidates = mutableListOf<Pair<String, String>>()
                db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex < 0) continue
                        val table = cursor.getString(nameIndex) ?: continue
                        if (table.startsWith("sqlite_") || table.startsWith("message_fts")) continue
                        db.query("PRAGMA table_info(`$table`)").use { columnCursor ->
                            val columnNameIndex = columnCursor.getColumnIndex("name")
                            while (columnCursor.moveToNext()) {
                                if (columnNameIndex < 0) continue
                                val column = columnCursor.getString(columnNameIndex) ?: continue
                                candidates += table to column
                            }
                        }
                    }
                }

                candidates.forEach { (table, column) ->
                    val rowIds = mutableListOf<Long>()
                    db.query(
                        "SELECT rowid FROM `$table` WHERE `$column` LIKE '%/data/user/%'"
                    ).use { cursor ->
                        while (cursor.moveToNext()) rowIds += cursor.getLong(0)
                    }
                    rowIds.forEach { rowId ->
                        var value: String? = null
                        db.query(
                            "SELECT `$column` FROM `$table` WHERE rowid = ?",
                            arrayOf(rowId),
                        ).use { cursor ->
                            if (cursor.moveToFirst()) value = cursor.getString(0)
                        }
                        val original = value ?: return@forEach
                        val fixed = rewriteText(
                            text = original,
                            currentPackage = context.packageName,
                            filesDirAbsolute = context.filesDir.absolutePath,
                        ) { relative -> File(filesRoot, relative).exists() }
                        if (fixed != original) {
                            db.execSQL(
                                "UPDATE `$table` SET `$column` = ? WHERE rowid = ?",
                                arrayOf<Any>(fixed, rowId),
                            )
                            updated++
                        }
                    }
                }
            }
            updated
        }.onFailure {
            Log.e(TAG, "healDatabase failed", it)
        }.getOrDefault(0)
    }

    /**
     * 在一个较大的文本（通常是消息 JSON）里替换所有可安全改写的绝对路径。
     *
     * 只替换**文件确实存在**的那些，因此不会误伤用户自己填的外部链接。
     */
    fun rewriteText(context: Context, text: String): String =
        rewriteText(text, context.packageName, context.filesDir.absolutePath) { relative ->
            File(context.filesDir, relative).exists()
        }

    /** 不依赖 [Context] 的文本改写实现，便于单测。 */
    internal fun rewriteText(
        text: String,
        currentPackage: String,
        filesDirAbsolute: String,
        exists: (String) -> Boolean,
    ): String {
        if (!text.contains("/data/user/")) return text
        return USER_PATH_IN_TEXT.replace(text) { match ->
            rebase(match.value, currentPackage, filesDirAbsolute, exists) ?: match.value
        }
    }

    /**
     * 在文本中定位候选路径。
     *
     * 字符集收窄到「不含引号、反斜杠、空白、逗号、括号、右花括号」，
     * 避免把 JSON 结构吃进来。
     */
    private val USER_PATH_IN_TEXT = Regex(
        """(?:file://)?/data/user/\d+/[A-Za-z0-9_.]+/files/[^"'\\\s,)\]}]+"""
    )
}
