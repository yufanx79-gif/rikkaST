package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 从版本27升级到28：重建知识库表（knowledge_sources / knowledge_chunks / knowledge_source_assistants + FTS5）。
 *
 * 说明：知识库系统在 v25→26 曾被移除（表被删），本版本（v28）恢复该功能，
 * 以全新迁移重建全部表结构，供新装与老库统一使用。
 * 索引名与 Room 实体默认命名保持一致（index_<table>_<column>），避免 schema 校验失败。
 */
val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 知识源
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `knowledge_sources` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL DEFAULT '',
                `type` TEXT NOT NULL DEFAULT 'FILE',
                `assistant_id` TEXT,
                `file_path` TEXT,
                `file_size` INTEGER NOT NULL DEFAULT 0,
                `chunk_count` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL DEFAULT 0,
                `tags` TEXT NOT NULL DEFAULT ''
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_sources_assistant_id` ON `knowledge_sources`(`assistant_id`)")

        // 知识分块
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `knowledge_chunks` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `source_id` TEXT NOT NULL,
                `chunk_index` INTEGER NOT NULL DEFAULT 0,
                `text` TEXT NOT NULL DEFAULT '',
                `sentence_start` INTEGER NOT NULL DEFAULT 0,
                `sentence_end` INTEGER NOT NULL DEFAULT 0,
                `embedding` BLOB,
                `embedding_dim` INTEGER NOT NULL DEFAULT 0,
                `parent_chunk_id` TEXT
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_source_id` ON `knowledge_chunks`(`source_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_chunk_index` ON `knowledge_chunks`(`chunk_index`)")

        // 知识源 ↔ 助理 多对多关联
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `knowledge_source_assistants` (
                `source_id` TEXT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                PRIMARY KEY(`source_id`, `assistant_id`),
                FOREIGN KEY(`source_id`) REFERENCES `knowledge_sources`(`id`) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_assistant_id` ON `knowledge_source_assistants`(`assistant_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_source_id` ON `knowledge_source_assistants`(`source_id`)")

        // FTS5 全文索引（精确关键字搜索；非 Room 实体，Service 启动时亦会补齐）
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_fts` USING fts5(
                `text`,
                `chunk_id` UNINDEXED,
                `source_id` UNINDEXED,
                tokenize='unicode61'
            )
        """)
    }
}
