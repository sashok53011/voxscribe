package com.example.modules.file_management

import android.content.Context
import com.example.modules.storage_search.FolderDao
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionDao
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.storage_search.TranscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * =========================================================================================
 * МОДУЛЬ: FileManagementModule (Слой управления файлами и каталогами Room)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Высокоуровневый модуль для организации аудиостенограмм в пользовательские папки,
 * вычисления статистики (количество файлов, суммарная длительность), перемещения записей
 * между каталогами, безопасного удаления и гибкой сортировки/фильтрации.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - repository (TranscriptionRepository) или DAO базы данных Room.
 * - Параметры папок: название, hex-цвет оформления, иконка.
 * - Идентификаторы записей (transcriptionId) и целевых каталогов (folderId).
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Список папок со статистикой [FolderWithStats].
 * - Отсортированные списки [TranscriptionEntity].
 * - Результаты операций [Result<Unit>] / [Result<FolderEntity>].
 * 
 * ПОВТОРНОЕ ИСПОЛЬЗОВАНИЕ:
 * Модуль полностью автономен, не зависит от UI-фреймворка и может подключаться к любому
 * слою представления (Jetpack Compose, фрагменты, фоновые воркеры).
 * 
 * ЗАГЛУШКИ И ПРОДАКШН (Stubs & Production):
 * Заглушек нет — реализовано прямое обращение к локальной реляционной базе данных Room
 * с транзакционным сохранением целостности при переносе и каскадном удалении.
 */
class FileManagementModule(
    private val repository: TranscriptionRepository
) {

    private val folderDao: FolderDao = repository.folderDao
    private val transcriptionDao: TranscriptionDao = repository.transcriptionDao

    /**
     * Модель папки с расширенной аналитикой содержимого
     */
    data class FolderWithStats(
        val folder: FolderEntity,
        val fileCount: Int,
        val totalDurationSeconds: Long,
        val formattedDuration: String
    )

    /**
     * Критерии сортировки файлов в каталогах
     */
    enum class SortOption(val titleRu: String, val titleDe: String) {
        DATE_DESC("Сначала новые", "Neueste zuerst"),
        DATE_ASC("Сначала старые", "Älteste zuerst"),
        TITLE_ASC("По названию (А-Я)", "Titel (A-Z)"),
        TITLE_DESC("По названию (Я-А)", "Titel (Z-A)"),
        DURATION_DESC("По длительности", "Nach Dauer"),
        SPEAKERS_DESC("По числу спикеров", "Nach Sprecheranzahl")
    }

    /**
     * Создание новой пользовательской папки
     * 
     * @param name Название папки
     * @param colorHex Цветовая метка (например, #1A237E, #00796B)
     * @param iconName Идентификатор иконки ("folder", "work", "mic", "notes")
     */
    suspend fun createFolder(
        name: String,
        colorHex: String = "#1A237E",
        iconName: String = "folder"
    ): Result<FolderEntity> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Имя папки не может быть пустым"))
        }

        try {
            val entity = FolderEntity(
                name = trimmed,
                colorHex = colorHex,
                iconName = iconName
            )
            folderDao.insertFolder(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Переименование и изменение стиля существующей папки
     */
    suspend fun updateFolder(
        folderId: String,
        newName: String,
        newColorHex: String,
        newIconName: String = "folder"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Имя папки не может быть пустым"))
        }

        try {
            folderDao.updateFolderMetadata(
                folderId = folderId,
                newName = trimmed,
                newColorHex = newColorHex,
                newIconName = newIconName
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Удаление папки с возможностью безопасного перемещения файлов в запасную папку
     * 
     * @param folderId Идентификатор удаляемой папки
     * @param fallbackFolderId Если указан, файлы перед удалением папки переносятся сюда
     */
    suspend fun deleteFolder(
        folderId: String,
        fallbackFolderId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (fallbackFolderId != null && fallbackFolderId != folderId) {
                transcriptionDao.reassignFolderTranscriptions(
                    sourceFolderId = folderId,
                    targetFolderId = fallbackFolderId
                )
            }
            folderDao.deleteFolderById(folderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Перемещение отдельной аудиозаписи в другой каталог
     */
    suspend fun moveTranscription(
        transcriptionId: String,
        newFolderId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transcriptionDao.moveTranscriptionToFolder(
                transcriptionId = transcriptionId,
                newFolderId = newFolderId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Пакетное перемещение списка записей в указанную папку
     */
    suspend fun moveBatchTranscriptions(
        transcriptionIds: List<String>,
        newFolderId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (id in transcriptionIds) {
                transcriptionDao.moveTranscriptionToFolder(id, newFolderId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получение статистики по всем папкам (кол-во записей, хронометраж)
     */
    suspend fun getAllFoldersWithStats(): List<FolderWithStats> = withContext(Dispatchers.IO) {
        val folders = folderDao.getAllFolders()
        folders.map { folder ->
            val count = transcriptionDao.countTranscriptionsInFolder(folder.id)
            val duration = transcriptionDao.getTotalDurationInFolder(folder.id) ?: 0L
            FolderWithStats(
                folder = folder,
                fileCount = count,
                totalDurationSeconds = duration,
                formattedDuration = formatDuration(duration)
            )
        }
    }

    /**
     * Сортировка списка стенограмм по заданному критерию
     */
    fun sortTranscriptions(
        items: List<TranscriptionEntity>,
        option: SortOption
    ): List<TranscriptionEntity> {
        return when (option) {
            SortOption.DATE_DESC -> items.sortedByDescending { it.createdAt }
            SortOption.DATE_ASC -> items.sortedBy { it.createdAt }
            SortOption.TITLE_ASC -> items.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> items.sortedByDescending { it.title.lowercase() }
            SortOption.DURATION_DESC -> items.sortedByDescending { it.durationSeconds }
            SortOption.SPEAKERS_DESC -> items.sortedByDescending { it.speakerCount }
        }
    }

    companion object {
        fun formatDuration(totalSeconds: Long): String {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
