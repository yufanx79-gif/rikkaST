package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryFileEntity

@Dao
interface MemoryDAO {
    // --- RikkaHub 3.1.0 MemoryFile DAO methods ---
    @Query("SELECT * FROM memory_file WHERE assistant_id = :assistantId ORDER BY path")
    fun getFilesOfAssistantFlow(assistantId: String): Flow<List<MemoryFileEntity>>

    @Query("SELECT * FROM memory_file WHERE assistant_id = :assistantId ORDER BY path")
    suspend fun getFilesOfAssistant(assistantId: String): List<MemoryFileEntity>

    @Query("SELECT * FROM memory_file WHERE assistant_id = :assistantId AND path = :path")
    suspend fun getFile(assistantId: String, path: String): MemoryFileEntity?

    @Upsert
    suspend fun upsertFile(file: MemoryFileEntity)

    @Upsert
    suspend fun upsertFiles(files: List<MemoryFileEntity>)

    @Query("DELETE FROM memory_file WHERE assistant_id = :assistantId AND path = :path")
    suspend fun deleteFile(assistantId: String, path: String)

    @Query("DELETE FROM memory_file WHERE assistant_id = :assistantId")
    suspend fun deleteFilesOfAssistant(assistantId: String)

    // --- rikkaST legacy MemoryEntity DAO methods ---
    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("DELETE FROM memoryentity WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)
}
