package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import me.rerere.rikkahub.data.db.migrations.ExpectedSchema
import java.io.File

/**
 * 恢复备份前的**结构预检**。
 *
 * ## 要解决的问题
 *
 * 上游 rikkahub 与本分支在 v32 分叉，之后各自演进。恢复「更新版本应用」导出的备份时，
 * Room 会在打开数据库那一刻抛出 `Migration didn't properly handle <table>`，
 * 用户只看到一句英文栈信息，既不知道哪张表出问题，也不知道该怎么办。
 *
 * 这里在**交给 Room 之前**先比一遍：
 *  - 备份库里出现了我们不认识的列 → 说明备份来自更新的版本，明确告知并中止（原库不动）。
 *  - 备份库缺列 / 缺表 → 正常情况，由 [me.rerere.rikkahub.data.db.migrations.Migration_33_34]
 *    这类迁移补齐，继续走 Room。
 *
 * ## 为什么「多出来的列」必须报错而不是忽略
 *
 * 多出来的列意味着**上游新增了功能**。静默删掉它等于丢掉用户数据，而且下次上游再改我们依然不知道。
 * 报错能让用户知道「升级应用就好」，也让我们明确知道该适配什么。
 */
internal object BackupSchemaGuard {

    /**
     * 检查备份库是否包含当前版本不认识的列。
     *
     * @return 人类可读的问题描述；`null` 表示结构可接受。
     */
    fun findUnknownColumns(context: Context, databaseFile: File): String? {
        if (!databaseFile.isFile || databaseFile.length() == 0L) return null
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READONLY),
        )
        return runCatching {
            SQLiteDatabase.openDatabase(configuration, null) { error("Backup database is corrupt") }
                .use { database -> scan(database) }
        }.getOrNull()
    }

    private fun scan(database: SupportSQLiteDatabase): String? {
        // 备份库里实际存在的表（排除 Room/SQLite 内部表与 FTS 影子表）
        val actualTables = mutableMapOf<String, Set<String>>()
        database.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex < 0) continue
                val table = cursor.getString(nameIndex) ?: continue
                if (table.isInternalTable()) continue
                actualTables[table] = columnsOf(database, table)
            }
        }

        val unknown = mutableListOf<String>()
        for ((table, actualColumns) in actualTables) {
            // 我们不认识这张表：可能是上游新增的功能表。
            // 不删、不报错 —— Room 不会校验它，留着无害，将来适配时数据还在。
            val expectedColumns = ExpectedSchema.columnsOf(table) ?: continue
            val extra = actualColumns - expectedColumns
            if (extra.isNotEmpty()) {
                extra.sorted().forEach { column -> unknown += "$table.$column" }
            }
        }

        if (unknown.isEmpty()) return null
        // 只列前几项，避免超长；用「一句话说清」的语气，不抛栈。
        val shown = unknown.take(3).joinToString("、")
        val more = if (unknown.size > 3) " 等 ${unknown.size} 项" else ""
        return "这个备份来自更新版本的应用，包含当前版本还不支持的数据（$shown$more）。请先升级应用再恢复。"
    }

    private fun columnsOf(database: SupportSQLiteDatabase, table: String): Set<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { add(it) }
                }
            }
        }

    /** Room 内部表、SQLite 内部表、FTS5 影子表都不参与业务校验。 */
    private fun String.isInternalTable(): Boolean =
        startsWith("sqlite_") ||
            startsWith("room_") ||
            startsWith("android_metadata") ||
            // FTS5 影子表：message_fts_data / _idx / _content / _docsize / _config
            startsWith("message_fts")
}
