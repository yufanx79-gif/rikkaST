package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v34 -> v35：补齐高频查询复合索引，消除侧边栏会话列表全表扫描与消息节点加载临时排序。
 */
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_assistant_id_folder_id_is_pinned_update_at` " +
                "ON `ConversationEntity` (`assistant_id`, `folder_id`, `is_pinned`, `update_at`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_message_node_conversation_id_node_index` " +
                "ON `message_node` (`conversation_id`, `node_index`)"
        )
    }
}
