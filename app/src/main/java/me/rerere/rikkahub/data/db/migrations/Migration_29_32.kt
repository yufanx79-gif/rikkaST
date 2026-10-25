package me.rerere.rikkahub.data.db.migrations

import android.database.Cursor
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_29_32"

private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
    val cursor: Cursor = db.query("PRAGMA table_info('$tableName')")
    cursor.use { c ->
        while (c.moveToNext()) {
            if (c.getString(1) == columnName) return true
        }
    }
    return false
}

internal fun executeIdempotentUpgradeTo32(db: SupportSQLiteDatabase, fromVersion: Int) {
    Log.i(TAG, "migrate: start migrate from $fromVersion to 32 (RikkaHub 3.1.0 + rikkaST schema merge)")
    DatabaseMigrationTracker.onMigrationStart(fromVersion, 32)
    db.beginTransaction()
    try {
        if (!hasColumn(db, "workspaces", "shell_compatibility_mode")) {
            db.execSQL(
                "ALTER TABLE `workspaces` ADD COLUMN `shell_compatibility_mode` INTEGER NOT NULL DEFAULT 0"
            )
        }
        if (!hasColumn(db, "ConversationEntity", "st_parent_conversation_id")) {
            db.execSQL(
                "ALTER TABLE `ConversationEntity` ADD COLUMN `st_parent_conversation_id` TEXT NOT NULL DEFAULT ''"
            )
        }
        if (!hasColumn(db, "ConversationEntity", "config")) {
            db.execSQL(
                "ALTER TABLE `ConversationEntity` ADD COLUMN `config` TEXT NOT NULL DEFAULT ''"
            )
        }

        // 0. Ensure rikkaST tables exist even when upgrading from an upstream RikkaHub 29/30/31 database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `MemoryEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, `content` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_sources` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `assistant_id` TEXT, `file_path` TEXT, `file_size` INTEGER NOT NULL, " +
                "`chunk_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_sources_assistant_id` ON `knowledge_sources` (`assistant_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_chunks` (`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                "`chunk_index` INTEGER NOT NULL, `text` TEXT NOT NULL, `sentence_start` INTEGER NOT NULL, " +
                "`sentence_end` INTEGER NOT NULL, `embedding` BLOB, `embedding_dim` INTEGER NOT NULL, " +
                "`parent_chunk_id` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_source_id` ON `knowledge_chunks` (`source_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_chunk_index` ON `knowledge_chunks` (`chunk_index`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_source_assistants` (`source_id` TEXT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, PRIMARY KEY(`source_id`, `assistant_id`), " +
                "FOREIGN KEY(`source_id`) REFERENCES `knowledge_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_assistant_id` ON `knowledge_source_assistants` (`assistant_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_source_id` ON `knowledge_source_assistants` (`source_id`)"
        )

        // 1. memory_file
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memory_file` (`assistant_id` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`assistant_id`, `path`))"
        )
        runCatching {
            val memories = linkedMapOf<String, MutableList<String>>()
            db.query("SELECT assistant_id, content FROM MemoryEntity ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    memories.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
                }
            }
            val now = System.currentTimeMillis()
            memories.forEach { (assistantId, contents) ->
                val lines = contents
                    .map { it.lines().joinToString(" ") { line -> line.trim() }.trim() }
                    .filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    val profile = buildString {
                        appendLine("---")
                        appendLine("name: profile")
                        appendLine("description: Who the user is")
                        appendLine("---")
                        appendLine()
                        lines.forEach { appendLine("- [stated] $it") }
                    }
                    db.execSQL(
                        "INSERT OR IGNORE INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                        arrayOf<Any?>(assistantId, "profile.md", profile, now)
                    )
                }
            }
        }

        // 2. memory_consolidation
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memory_consolidation` (`conversation_id` TEXT NOT NULL, " +
                "`consolidated_at` INTEGER NOT NULL, PRIMARY KEY(`conversation_id`), " +
                "FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO memory_consolidation (conversation_id, consolidated_at) " +
                "SELECT id, update_at FROM ConversationEntity"
        )

        // 3. media_creation_session / node / record
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_creation_session` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`draft` TEXT NOT NULL, `create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_creation_node` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
                "`selected_record_id` TEXT NOT NULL, `create_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_creation_node_session_id` ON `media_creation_node` (`session_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_creation_record` (`id` TEXT NOT NULL, `session_id` TEXT NOT NULL, " +
                "`node_id` TEXT NOT NULL, `provider_id` TEXT NOT NULL, `provider_name` TEXT NOT NULL, " +
                "`model_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `prompt` TEXT NOT NULL, `params` TEXT NOT NULL, " +
                "`inputs` TEXT NOT NULL, `status` TEXT NOT NULL, `task_id` TEXT, `error` TEXT, " +
                "`outputs` TEXT NOT NULL, `create_at` INTEGER NOT NULL, `update_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`session_id`) REFERENCES `media_creation_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`node_id`) REFERENCES `media_creation_node`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_creation_record_session_id` ON `media_creation_record` (`session_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_creation_record_node_id` ON `media_creation_record` (`node_id`)"
        )

        // 4. translation_history
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `translation_history` (`id` TEXT NOT NULL, `source_text` TEXT NOT NULL, " +
                "`translated_text` TEXT NOT NULL, `target_language` TEXT NOT NULL, `model_id` TEXT NOT NULL, " +
                "`create_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_translation_history_create_at` ON `translation_history` (`create_at`)"
        )

        // 5. usage_record (only backfill if table was just created and is empty)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usage_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `source` TEXT NOT NULL, `provider_id` TEXT, `model_id` TEXT, " +
                "`model_name` TEXT, `assistant_id` TEXT, `conversation_id` TEXT, " +
                "`prompt_tokens` INTEGER NOT NULL, `completion_tokens` INTEGER NOT NULL, " +
                "`cached_tokens` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_usage_record_created_at` ON `usage_record` (`created_at`)"
        )
        val existingUsageRows = runCatching {
            db.query("SELECT COUNT(*) FROM usage_record").use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        }.getOrDefault(0L)
        if (existingUsageRows == 0L) {
            runCatching {
                db.execSQL(
                    """
                    INSERT INTO usage_record (
                        created_at, source, provider_id, model_id, model_name, assistant_id, conversation_id,
                        prompt_tokens, completion_tokens, cached_tokens
                    )
                    SELECT
                        COALESCE(
                            CAST(strftime('%s', substr(json_extract(j.value, '$.createdAt'), 1, 19), 'utc') AS INTEGER) * 1000,
                            c.create_at
                        ),
                        'CHAT',
                        NULL,
                        json_extract(j.value, '$.modelId'),
                        json_extract(j.value, '$.modelSnapshot.modelId'),
                        c.assistant_id,
                        mn.conversation_id,
                        COALESCE(json_extract(j.value, '$.usage.promptTokens'), 0),
                        COALESCE(json_extract(j.value, '$.usage.completionTokens'), 0),
                        COALESCE(json_extract(j.value, '$.usage.cachedTokens'), 0)
                    FROM message_node mn
                    JOIN ConversationEntity c ON c.id = mn.conversation_id,
                        json_each(CASE WHEN json_valid(mn.messages) THEN mn.messages ELSE '[]' END) j
                    WHERE json_extract(j.value, '$.role') = 'assistant'
                        AND json_type(j.value, '$.usage') = 'object'
                        AND (
                            COALESCE(json_extract(j.value, '$.usage.promptTokens'), 0) > 0
                            OR COALESCE(json_extract(j.value, '$.usage.completionTokens'), 0) > 0
                        )
                    """.trimIndent()
                )
            }
        }

        db.setTransactionSuccessful()
        Log.i(TAG, "migrate: migrate from $fromVersion to 32 success")
    } finally {
        db.endTransaction()
        DatabaseMigrationTracker.onMigrationEnd()
    }
}

val Migration_29_32 = object : Migration(29, 32) {
    override fun migrate(db: SupportSQLiteDatabase) = executeIdempotentUpgradeTo32(db, 29)
}

val Migration_30_32 = object : Migration(30, 32) {
    override fun migrate(db: SupportSQLiteDatabase) = executeIdempotentUpgradeTo32(db, 30)
}

val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) = executeIdempotentUpgradeTo32(db, 31)
}
