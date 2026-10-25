# RikkaHub 3.1.0 融合与升级记录

- upstream tag: `3.1.0`
- upstream commit: `eeef60b22ba24383daf51f3c6c2e5b75bbab6bba`
- Room Database 目标版本: `32`（显式 `Migration_29_32`）
- 保留本地核心资产: `app/src/main/assets/st-runtime/**`、`app/src/main/java/me/rerere/rikkahub/data/st/**`、`node-host/**`、`videogen/**`、`applicationId = "me.rerere.rikkahub.st"`
- 吸收上游 3.1.0 核心改进:
  1. `ChatGenerationForegroundService` 的 `startId` 竞态修复（防 `ForegroundServiceDidNotStartInTimeException`）
  2. `ConversationRepository.loadMessageNodes` 的 `CancellationException` 捕获顺序修复（防事务内死循环）
  3. `ConversationConfig` 与 `Conversation.upsertMessage` / `mapLocalFileUrls`
  4. 新增 3.1.0 实体与 DAO：`memory_file`、`memory_consolidation`、`media_creation_session`、`media_creation_node`、`media_creation_record`、`translation_history`、`usage_record`
  5. `Migration_29_32` 无损保留 `st_parent_conversation_id`、`knowledge_*` 与 `MemoryEntity`，并同步回填 `memory_file`、`memory_consolidation` 与 `usage_record`
