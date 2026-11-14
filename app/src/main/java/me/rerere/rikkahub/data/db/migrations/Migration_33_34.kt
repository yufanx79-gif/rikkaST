package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v33 → v34：**对齐上游 rikkahub 的 workspaces 表结构**，并补齐上游备份缺失的对象。
 *
 * ## 背景：为什么需要这次迁移
 *
 * 我们这条分支和上游 rikkahub 在 v32 分叉，两边**同一个版本号但 schema 不同**：
 *
 * | | 我们 v33 | 上游 v32 |
 * |---|---|---|
 * | `workspaces.type` / `ssh_config` | 无 | **有**（远程 SSH 工作区） |
 * | `ConversationEntity.st_parent_conversation_id` | **有** | 无 |
 * | `MemoryEntity` + 3 张 knowledge 表 | **有** | 无 |
 *
 * Room 的 TableInfo 校验是**双向精确相等**：库里多一列、少一列都算迁移失败。
 * 于是「把上游备份恢复进来」会在打开数据库时报
 * `Migration didn't properly handle workspaces`，整个恢复被拒。
 *
 * ## 本迁移做三件事
 *
 * 1. **补 `workspaces.type` / `ssh_config`**（上游有、我们没有）
 *    —— 声明这两个列只为通过校验；业务代码不读不写，`REMOTE` 工作区在 UI 上提示不支持。
 * 2. **补 `ConversationEntity.st_parent_conversation_id`**（我们有、上游没有）
 *    —— 恢复上游备份时这列不存在，必须补上并给默认值。
 * 3. **补 4 张我们独有的空表**（`MemoryEntity` + knowledge 三张）
 *    —— 上游备份里没有这些表，但我们的 DAO 会访问它们，缺表会崩。
 *
 * ## 幂等性
 *
 * 每个步骤都先检查再执行：本机从 v33 升级时，第 2、3 步是空操作（对象已存在）；
 * 恢复上游 v32 备份时，第 1 步才是空操作。两种情况共用同一条迁移路径。
 */
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── 1. workspaces：补上游的两个列 ──
        // type 上游默认 'LOCAL'（NOT NULL），ssh_config 可空。
        // 已有工作区全是本地工作区，补上默认值后行为完全不变。
        db.addColumnIfMissing(
            table = "workspaces",
            column = "type",
            definition = "TEXT NOT NULL DEFAULT 'LOCAL'",
        )
        db.addColumnIfMissing(
            table = "workspaces",
            column = "ssh_config",
            definition = "TEXT",
        )

        // ── 2. ConversationEntity：补我们自己的 checkpoint 链接列 ──
        db.addColumnIfMissing(
            table = "ConversationEntity",
            column = "st_parent_conversation_id",
            definition = "TEXT NOT NULL DEFAULT ''",
        )

        // ── 3. 我们独有的 4 张表：上游备份里没有，必须补建 ──
        // 注意：Room 的 TableInfo 校验不只比列，**索引和外键也参与比对**。
        // 只建表不建索引同样会报 `Migration didn't properly handle knowledge_sources`。
        // 下面每条 SQL 都与 app/schemas/.../34.json 的 createSql / createIndexSql 逐字一致。
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `MemoryEntity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`assistant_id` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_sources` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`assistant_id` TEXT, `file_path` TEXT, `file_size` INTEGER NOT NULL, " +
                "`chunk_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`tags` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_sources_assistant_id` " +
                "ON `knowledge_sources` (`assistant_id`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_chunks` (" +
                "`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `chunk_index` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, `sentence_start` INTEGER NOT NULL, " +
                "`sentence_end` INTEGER NOT NULL, `embedding` BLOB, " +
                "`embedding_dim` INTEGER NOT NULL, `parent_chunk_id` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_source_id` " +
                "ON `knowledge_chunks` (`source_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_chunk_index` " +
                "ON `knowledge_chunks` (`chunk_index`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `knowledge_source_assistants` (" +
                "`source_id` TEXT NOT NULL, `assistant_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`source_id`, `assistant_id`), " +
                "FOREIGN KEY(`source_id`) REFERENCES `knowledge_sources`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_assistant_id` " +
                "ON `knowledge_source_assistants` (`assistant_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_knowledge_source_assistants_source_id` " +
                "ON `knowledge_source_assistants` (`source_id`)"
        )
    }
}

/**
 * 只在列不存在时 `ALTER TABLE ... ADD COLUMN`。
 *
 * SQLite 没有 `ADD COLUMN IF NOT EXISTS`，重复执行会抛
 * `duplicate column name`。本迁移要同时服务「本机 v33 升级」和
 * 「上游 v32 备份恢复」两条路径，两者已有的列不同，所以必须先探测。
 */
private fun SupportSQLiteDatabase.addColumnIfMissing(
    table: String,
    column: String,
    definition: String,
) {
    val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                found = true
                break
            }
        }
        found
    }
    if (!exists) {
        execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}
