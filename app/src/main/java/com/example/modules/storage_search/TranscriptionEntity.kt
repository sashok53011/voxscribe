package com.example.modules.storage_search

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * =========================================================================================
 * МОДУЛЬ: TranscriptionEntity (Сущность стенограммы в локальной базе Room)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Надежное автономное хранение расшифрованных записей, реплик спикеров, переводов
 * и метаданных с возможностью полнотекстового поиска.
 */
@Entity(
    tableName = "transcriptions",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("folderId"),
        Index("createdAt"),
        Index("title")
    ]
)
data class TranscriptionEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val folderId: String,
    val fullText: String,
    val translatedFullText: String = "",
    val targetLanguageCode: String = "",
    val segmentsJson: String = "[]",
    val wordTimestampsJson: String = "[]",
    val durationSeconds: Int = 0,
    val speakerCount: Int = 1,
    val audioSourceType: String = "MIC", // "MIC" или "FILE"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
