package com.example.modules.storage_search

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * =========================================================================================
 * МОДУЛЬ: TranscriptionDao (Полнотекстовый поиск, фильтрация и хранение стенограмм)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Выполнение поисковых SQL-запросов по заголовку, расшифрованному тексту и переводам.
 */
@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions ORDER BY createdAt DESC")
    fun getAllTranscriptionsFlow(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getTranscriptionsByFolderFlow(folderId: String): Flow<List<TranscriptionEntity>>

    @Query("""
        SELECT * FROM transcriptions 
        WHERE title LIKE '%' || :query || '%' 
           OR fullText LIKE '%' || :query || '%' 
           OR translatedFullText LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchTranscriptionsFlow(query: String): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    suspend fun getTranscriptionById(id: String): TranscriptionEntity?

    @Query("SELECT COUNT(*) FROM transcriptions WHERE folderId = :folderId")
    suspend fun countTranscriptionsInFolder(folderId: String): Int

    @Query("SELECT SUM(durationSeconds) FROM transcriptions WHERE folderId = :folderId")
    suspend fun getTotalDurationInFolder(folderId: String): Long?

    @Query("UPDATE transcriptions SET folderId = :newFolderId WHERE id = :transcriptionId")
    suspend fun moveTranscriptionToFolder(transcriptionId: String, newFolderId: String)

    @Query("UPDATE transcriptions SET folderId = :targetFolderId WHERE folderId = :sourceFolderId")
    suspend fun reassignFolderTranscriptions(sourceFolderId: String, targetFolderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(entity: TranscriptionEntity)

    @Update
    suspend fun updateTranscription(entity: TranscriptionEntity)

    @Delete
    suspend fun deleteTranscription(entity: TranscriptionEntity)
}
