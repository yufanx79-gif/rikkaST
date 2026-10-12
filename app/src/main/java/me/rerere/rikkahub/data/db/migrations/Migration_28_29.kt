package me.rerere.rikkahub.data.db.migrations

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 从版本28升级到29：吸收上游 2.5.6 schema 24→25 的 workspaces.shellCompatibilityMode 列。
 *
 * 本地版本号 28 与上游 2.5.6 的 25 不是同一条迁移链，因此不能回退版本号，
 * 而在本地当前版本 28 之上追加本迁移。列带默认值，老库与新装均安全。
 */
val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "workspaces", "shell_compatibility_mode")) {
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `shellCompatibilityMode` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor: Cursor = db.query("PRAGMA table_info('$tableName')")
        cursor.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == columnName) return true
            }
        }
        return false
    }
}
