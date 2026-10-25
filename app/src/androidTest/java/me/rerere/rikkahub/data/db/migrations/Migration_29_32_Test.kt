package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_29_32_Test {
    private val testDbPrefix = "migration-v32-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate29To32PreservesExistingRowsAndAdds310Schema() {
        val dbName = "$testDbPrefix-29-32"
        helper.createDatabase(dbName, 29).apply {
            execSQL(
                "INSERT INTO ConversationEntity (id, assistant_id, title, nodes, create_at, update_at, st_parent_conversation_id) " +
                    "VALUES ('c1', 'a1', 'Legacy Chat', '[]', 1000, 2000, 'parent-conv-1')"
            )
            execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                    "VALUES ('n1', 'c1', 0, '[{\"role\":\"assistant\",\"modelId\":\"gpt-4o\",\"createdAt\":\"2026-10-20T12:00:00Z\",\"usage\":{\"promptTokens\":120,\"completionTokens\":45,\"cachedTokens\":10}},{\"role\":\"assistant\",\"modelId\":\"claude-3-7\",\"createdAt\":\"2026-10-20T12:01:00Z\",\"usage\":{\"promptTokens\":80,\"completionTokens\":30,\"cachedTokens\":0}}]', 1)"
            )
            execSQL(
                "INSERT INTO knowledge_sources (id, name, type, assistant_id, file_path, file_size, chunk_count, created_at, tags) " +
                    "VALUES ('k1', 'Legacy Knowledge', 'FILE', 'a1', '/tmp/doc.txt', 512, 1, 1000, 'tag1')"
            )
            execSQL(
                "INSERT INTO MemoryEntity (assistant_id, content) VALUES ('a1', 'Likes coffee')"
            )
            execSQL(
                "INSERT INTO GenMediaEntity (id, path, model_id, prompt, create_at, type, source_paths) " +
                    "VALUES (1, 'images/sample.png', 'gpt-image-1', '1girl', 1500, 'image_generation', NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            32,
            true,
            Migration_28_29,
            Migration_29_32,
            Migration_30_31,
            Migration_30_32,
            Migration_31_32,
        )

        migrated.query("SELECT title, st_parent_conversation_id, config FROM ConversationEntity WHERE id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Legacy Chat", c.getString(0))
            assertEquals("parent-conv-1", c.getString(1))
            assertEquals("", c.getString(2))
        }

        migrated.query("SELECT COUNT(*) FROM knowledge_sources WHERE id = 'k1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }

        migrated.query("SELECT COUNT(*) FROM MemoryEntity WHERE assistant_id = 'a1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }

        migrated.query("SELECT COUNT(*) FROM memory_file WHERE assistant_id = 'a1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }

        migrated.query("SELECT consolidated_at FROM memory_consolidation WHERE conversation_id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2000L, c.getLong(0))
        }

        migrated.query("SELECT COUNT(*) FROM usage_record WHERE conversation_id = 'c1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        migrated.query("PRAGMA foreign_key_check").use { c ->
            assertFalse("Expected 0 foreign key violations", c.moveToFirst())
        }

        migrated.query("PRAGMA integrity_check").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ok", c.getString(0))
        }

        migrated.close()
    }

    @Test
    fun migrateAllPredecessorPaths_28To29To32_30To32_31To32_andEmptyDatabasesValidateCleanly() {
        for (startVersion in listOf(28, 29, 30, 31)) {
            val emptyDbName = "$testDbPrefix-empty-$startVersion-to-32"
            helper.createDatabase(emptyDbName, startVersion).close()
            val migratedEmpty = helper.runMigrationsAndValidate(
                emptyDbName,
                32,
                true,
                Migration_28_29,
                Migration_29_32,
                Migration_30_31,
                Migration_30_32,
                Migration_31_32,
            )
            migratedEmpty.query("PRAGMA integrity_check").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0))
            }
            migratedEmpty.close()
        }
    }
}
