package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryFileEntity

internal class FakeMemoryDAO : MemoryDAO {
    private val files = MutableStateFlow<List<MemoryFileEntity>>(emptyList())

    override fun getFilesOfAssistantFlow(assistantId: String): Flow<List<MemoryFileEntity>> =
        files.map { all -> all.filter { it.assistantId == assistantId }.sortedBy { it.path } }

    override suspend fun getFilesOfAssistant(assistantId: String): List<MemoryFileEntity> =
        files.value.filter { it.assistantId == assistantId }.sortedBy { it.path }

    override suspend fun getFile(assistantId: String, path: String): MemoryFileEntity? =
        files.value.find { it.assistantId == assistantId && it.path == path }

    override suspend fun upsertFile(file: MemoryFileEntity) = upsertFiles(listOf(file))

    override suspend fun upsertFiles(files: List<MemoryFileEntity>) {
        val replaced = files.map { it.assistantId to it.path }.toSet()
        this.files.value = this.files.value.filterNot { (it.assistantId to it.path) in replaced } + files
    }

    override suspend fun deleteFile(assistantId: String, path: String) {
        files.value = files.value.filterNot { it.assistantId == assistantId && it.path == path }
    }

    override suspend fun deleteFilesOfAssistant(assistantId: String) {
        files.value = files.value.filterNot { it.assistantId == assistantId }
    }

    // --- rikkaST legacy MemoryEntity 部分：记忆库已迁到 memory_file，这里只是接口占位 ---
    private val legacy = MutableStateFlow<List<MemoryEntity>>(emptyList())

    override fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>> =
        legacy.map { all -> all.filter { it.assistantId == assistantId } }

    override suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity> =
        legacy.value.filter { it.assistantId == assistantId }

    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = legacy

    override suspend fun getAllMemories(): List<MemoryEntity> = legacy.value

    override suspend fun getMemoryById(id: Int): MemoryEntity? =
        legacy.value.find { it.id == id }

    override suspend fun insertMemory(memory: MemoryEntity): Long {
        val assigned = if (memory.id == 0) (legacy.value.maxOfOrNull { it.id } ?: 0) + 1 else memory.id
        legacy.value = legacy.value + memory.copy(id = assigned)
        return assigned.toLong()
    }

    override suspend fun insertMemories(memories: List<MemoryEntity>) {
        memories.forEach { insertMemory(it) }
    }

    override suspend fun updateMemory(memory: MemoryEntity) {
        legacy.value = legacy.value.map { if (it.id == memory.id) memory else it }
    }

    override suspend fun deleteMemory(id: Int) {
        legacy.value = legacy.value.filterNot { it.id == id }
    }

    override suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        legacy.value = legacy.value.filterNot { it.assistantId == assistantId }
    }
}
