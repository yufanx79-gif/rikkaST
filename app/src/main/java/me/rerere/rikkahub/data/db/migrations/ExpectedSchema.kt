package me.rerere.rikkahub.data.db.migrations

/**
 * 当前版本期望的表结构（表名 → 列名集合）。
 *
 * 由 Room 生成的 `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/34.json` 逐字导出，供
 * [me.rerere.rikkahub.data.db.BackupSchemaGuard] 在恢复备份前做结构预检：
 * 备份库若出现这里的列集合之外的列，说明它来自更新版本，应当明确报错而不是静默丢弃。
 *
 * ## 维护方式
 *
 * 每次新增表或列（Room schema 版本 +1）后，必须同步更新本文件 ——
 * 否则新列会被误判成「不认识的列」，导致恢复自家备份也报错。
 * 有单测 `ExpectedSchemaTest` 校验本文件与最新 schema JSON 完全一致。
 */
internal object ExpectedSchema {

    private val TABLE_COLUMNS: Map<String, Set<String>> = mapOf(
        "ConversationEntity" to setOf("assistant_id", "config", "create_at", "custom_system_prompt", "folder_id", "id", "is_pinned", "lorebook_ids", "mode_injection_ids", "nodes", "st_parent_conversation_id", "suggestions", "title", "update_at", "workspace_cwd"),
        "GenMediaEntity" to setOf("create_at", "id", "model_id", "path", "prompt", "source_paths", "type"),
        "MemoryEntity" to setOf("assistant_id", "content", "id"),
        "conversation_folder" to setOf("assistant_id", "create_at", "id", "name", "sort_index"),
        "favorites" to setOf("created_at", "id", "meta_json", "ref_json", "ref_key", "snapshot_json", "type", "updated_at"),
        "knowledge_chunks" to setOf("chunk_index", "embedding", "embedding_dim", "id", "parent_chunk_id", "sentence_end", "sentence_start", "source_id", "text"),
        "knowledge_source_assistants" to setOf("assistant_id", "source_id"),
        "knowledge_sources" to setOf("assistant_id", "chunk_count", "created_at", "file_path", "file_size", "id", "name", "tags", "type"),
        "managed_files" to setOf("created_at", "display_name", "folder", "id", "mime_type", "relative_path", "size_bytes", "updated_at"),
        "media_creation_node" to setOf("create_at", "id", "selected_record_id", "session_id"),
        "media_creation_record" to setOf("create_at", "error", "id", "inputs", "kind", "model_id", "node_id", "outputs", "params", "prompt", "provider_id", "provider_name", "session_id", "status", "task_id", "update_at"),
        "media_creation_session" to setOf("create_at", "draft", "id", "title", "update_at"),
        "memory_consolidation" to setOf("consolidated_at", "conversation_id"),
        "memory_file" to setOf("assistant_id", "content", "path", "updated_at"),
        "message_node" to setOf("conversation_id", "id", "messages", "node_index", "select_index"),
        "translation_history" to setOf("create_at", "id", "model_id", "source_text", "target_language", "translated_text"),
        "usage_record" to setOf("assistant_id", "cached_tokens", "completion_tokens", "conversation_id", "created_at", "id", "model_id", "model_name", "prompt_tokens", "provider_id", "source"),
        "workspaces" to setOf("created_at", "id", "last_access_at", "name", "root", "shell_compatibility_mode", "shell_status", "ssh_config", "tool_approvals", "type", "updated_at"),
    )

    fun columnsOf(table: String): Set<String>? = TABLE_COLUMNS[table]

    /** 仅用于测试：导出全部已知表。 */
    internal fun knownTables(): Set<String> = TABLE_COLUMNS.keys
}
