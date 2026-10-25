package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v32 之前的一次性修复：旧迁移给 MemoryEntity 建了一个实体里并不存在的索引，
 * Room 校验 TableInfo 时会因为多出的索引判定迁移失败（备份恢复同样会失败）。
 * 这里把它删掉；全新 v32 数据库没有这个索引，DROP IF EXISTS 是空操作。
 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_MemoryEntity_assistant_id`")
    }
}
