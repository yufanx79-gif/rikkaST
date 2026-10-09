package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 从版本 26 升级到 27：为 ConversationEntity 添加 st_parent_conversation_id 列。
 *
 * ST Checkpoints 内化：记录检查点会话的父会话 id（/checkpoint-exit 返回目标）。
 */
val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `ConversationEntity` ADD COLUMN `st_parent_conversation_id` TEXT NOT NULL DEFAULT ''"
        )
    }
}
