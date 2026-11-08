package com.example.modules.file_export_manager

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.modules.ai_summary.AiSummaryModule.TranscriptionAnalysisResult
import com.example.modules.document_export.DocxExporterModule
import com.example.modules.document_export.PdfExporterModule
import com.example.modules.document_export.TxtExporterModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import com.example.modules.storage_search.TranscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: FileExportManagerModule (Менеджер локального экспорта документов PDF / DOCX / TXT)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Комплексный менеджер для сохранения стенограмм в локальное долговременное хранилище
 * смартфона в виде форматированных файлов PDF или DOCX.
 * Обеспечивает каталогизацию созданных документов, управление опциями форматирования
 * (спикеры, таймкоды, перевод, выводы ИИ), просмотр истории экспорта, открытие
 * через внешние просмотрщики и шеринг через мессенджеры/почту.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст Android для доступа к внешнему хранилищу документов и FileProvider.
 * - transcription (TranscriptionEntity): Данные стенограммы из Room.
 * - segments (List<SpeakerSegment>): Сегменты реплик спикеров с таймкодами.
 * - summary (SummaryResult?): Аналитика от Gemini или OpenAI (задачи, резюме, тональность).
 * - options (ExportFormatOptions): Настройки оформления документа.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - [ExportedFileItem]: Описание сохраненного файла с метаданными.
 * - Список [List<ExportedFileItem>]: История всех экспортированных документов.
 * - Результаты операций открытия и отправки через Intent.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val exportManager = FileExportManagerModule(context)
 * val exportedItem = exportManager.exportToFormattedPdf(
 *     transcription = entity,
 *     folderName = "Работа",
 *     segments = segmentsList,
 *     summary = aiSummary,
 *     options = ExportFormatOptions()
 * )
 * exportManager.openFile(context, exportedItem)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН (Stubs & Production):
 * Файлы сохраняются в директорию документов приложения (Documents/VoxScribe_Exports),
 * что гарантирует сохранность без запроса опасных системных разрешений `MANAGE_EXTERNAL_STORAGE`.
 * В продакшене можно дополнительно подключить Android Storage Access Framework (SAF)
 * с диалогом `ACTION_CREATE_DOCUMENT` для выбора произвольной папки на SD-карте.
 */
class FileExportManagerModule(private val context: Context) {

    private val pdfExporter = PdfExporterModule(context)
    private val docxExporter = DocxExporterModule(context)
    private val txtExporter = TxtExporterModule(context)

    /**
     * Формат экспортируемого документа
     */
    enum class DocumentFormat(val extension: String, val mimeType: String, val displayName: String) {
        PDF("pdf", "application/pdf", "Adobe PDF (.pdf)"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Microsoft Word (.docx)"),
        TXT("txt", "text/plain", "Текстовый файл (.txt)")
    }

    /**
     * Пользовательские параметры форматирования документа при экспорте
     */
    data class ExportFormatOptions(
        val includeTitle: Boolean = true,
        val includeMetadataHeader: Boolean = true,
        val includeSpeakerBadges: Boolean = true,
        val includeTimestamps: Boolean = true,
        val includeTranslations: Boolean = true,
        val includeAiSummary: Boolean = true
    )

    /**
     * Метаданные о локально сохраненном экспортированном файле
     */
    data class ExportedFileItem(
        val file: File,
        val fileName: String,
        val format: DocumentFormat,
        val sizeBytes: Long,
        val formattedSize: String,
        val lastModifiedMs: Long,
        val formattedDate: String
    )

    /**
     * Получение постоянной папки экспорта документов
     */
    fun getExportsDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
            ?: context.filesDir
        val exportDir = File(baseDir, "VoxScribe_Exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Сохранение форматированного PDF файла в локальное хранилище
     */
    suspend fun exportToFormattedPdf(
        transcription: TranscriptionEntity,
        folderName: String,
        segments: List<SpeakerSegment>,
        summary: TranscriptionAnalysisResult? = null,
        options: ExportFormatOptions = ExportFormatOptions()
    ): ExportedFileItem = withContext(Dispatchers.IO) {
        // Подготовка сегментов с учетом выбранных опций форматирования
        val effectiveSegments = segments.map { seg ->
            seg.copy(
                speakerDisplayName = if (options.includeSpeakerBadges) seg.speakerDisplayName else "",
                translatedText = if (options.includeTranslations) seg.translatedText else "",
                startTimeMs = if (options.includeTimestamps) seg.startTimeMs else 0L,
                endTimeMs = if (options.includeTimestamps) seg.endTimeMs else 0L
            )
        }

        // Если есть выводы ИИ и включена опция, добавляем сводку в начало
        val combinedSegments = if (options.includeAiSummary && summary != null && summary.executiveSummary.isNotBlank()) {
            val summaryHeader = "📊 [РЕЗЮМЕ И ВЫВОДЫ]:\n${summary.executiveSummary}" +
                    if (summary.actionItems.isNotEmpty()) "\n\n📌 ЗАДАЧИ:\n" + summary.actionItems.joinToString("\n") { "• ${it.task} (${it.assignee})" } else ""
            listOf(
                SpeakerSegment(
                    segmentId = "summary_header",
                    speakerId = "ai_analyst",
                    speakerDisplayName = "ИИ Аналитик",
                    gender = com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender.UNKNOWN,
                    pitchHz = 160f,
                    text = summaryHeader,
                    translatedText = "",
                    startTimeMs = 0L,
                    endTimeMs = 0L
                )
            ) + effectiveSegments
        } else {
            effectiveSegments
        }

        val tempFile = pdfExporter.exportToPdf(
            title = transcription.title,
            folderName = folderName,
            segments = combinedSegments,
            creationDateMs = transcription.createdAt,
            targetLanguageName = if (options.includeTranslations && transcription.targetLanguageCode.isNotBlank()) transcription.targetLanguageCode else null
        )

        // Копируем файл в постоянную директорию экспорта
        val exportDir = getExportsDirectory()
        val safeTitle = sanitizeFileName(transcription.title)
        val finalFileName = "${safeTitle}_${System.currentTimeMillis()}.pdf"
        val destinationFile = File(exportDir, finalFileName)
        tempFile.copyTo(destinationFile, overwrite = true)

        mapToFileItem(destinationFile, DocumentFormat.PDF)
    }

    /**
     * Сохранение форматированного DOCX файла в локальное хранилище
     */
    suspend fun exportToFormattedDocx(
        transcription: TranscriptionEntity,
        folderName: String,
        segments: List<SpeakerSegment>,
        summary: TranscriptionAnalysisResult? = null,
        options: ExportFormatOptions = ExportFormatOptions()
    ): ExportedFileItem = withContext(Dispatchers.IO) {
        val effectiveSegments = segments.map { seg ->
            seg.copy(
                speakerDisplayName = if (options.includeSpeakerBadges) seg.speakerDisplayName else "",
                translatedText = if (options.includeTranslations) seg.translatedText else ""
            )
        }

        val combinedSegments = if (options.includeAiSummary && summary != null && summary.executiveSummary.isNotBlank()) {
            val summaryHeader = "РЕЗЮМЕ ВСТРЕЧИ:\n${summary.executiveSummary}" +
                    if (summary.actionItems.isNotEmpty()) "\n\nКЛЮЧЕВЫЕ ЗАДАЧИ:\n" + summary.actionItems.joinToString("\n") { "• ${it.task} (${it.assignee})" } else ""
            listOf(
                SpeakerSegment(
                    segmentId = "summary_header",
                    speakerId = "ai_analyst",
                    speakerDisplayName = "ИИ Анализ",
                    gender = com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender.UNKNOWN,
                    pitchHz = 160f,
                    text = summaryHeader,
                    translatedText = "",
                    startTimeMs = 0L,
                    endTimeMs = 0L
                )
            ) + effectiveSegments
        } else {
            effectiveSegments
        }

        val tempFile = docxExporter.exportToDocx(
            title = transcription.title,
            folderName = folderName,
            segments = combinedSegments,
            creationDateMs = transcription.createdAt,
            targetLanguageName = if (options.includeTranslations && transcription.targetLanguageCode.isNotBlank()) transcription.targetLanguageCode else null
        )

        val exportDir = getExportsDirectory()
        val safeTitle = sanitizeFileName(transcription.title)
        val finalFileName = "${safeTitle}_${System.currentTimeMillis()}.docx"
        val destinationFile = File(exportDir, finalFileName)
        tempFile.copyTo(destinationFile, overwrite = true)

        mapToFileItem(destinationFile, DocumentFormat.DOCX)
    }

    /**
     * Сохранение простого текстового TXT файла
     */
    suspend fun exportToFormattedTxt(
        transcription: TranscriptionEntity,
        segments: List<SpeakerSegment>
    ): ExportedFileItem = withContext(Dispatchers.IO) {
        val tempFile = txtExporter.exportToTxt(
            title = transcription.title,
            folderName = "Общее",
            segments = segments,
            creationDateMs = transcription.createdAt,
            targetLanguageName = transcription.targetLanguageCode.ifBlank { null }
        )

        val exportDir = getExportsDirectory()
        val safeTitle = sanitizeFileName(transcription.title)
        val finalFileName = "${safeTitle}_${System.currentTimeMillis()}.txt"
        val destinationFile = File(exportDir, finalFileName)
        tempFile.copyTo(destinationFile, overwrite = true)

        mapToFileItem(destinationFile, DocumentFormat.TXT)
    }

    /**
     * Получение списка всех сохраненных файлов из локального каталога экспорта
     */
    suspend fun getAllExportedFiles(): List<ExportedFileItem> = withContext(Dispatchers.IO) {
        val dir = getExportsDirectory()
        val files = dir.listFiles() ?: return@withContext emptyList()
        files
            .filter { it.isFile && (it.name.endsWith(".pdf") || it.name.endsWith(".docx") || it.name.endsWith(".txt")) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val format = when {
                    file.name.endsWith(".pdf", ignoreCase = true) -> DocumentFormat.PDF
                    file.name.endsWith(".docx", ignoreCase = true) -> DocumentFormat.DOCX
                    else -> DocumentFormat.TXT
                }
                mapToFileItem(file, format)
            }
    }

    /**
     * Открытие экспортированного файла через установленные приложения (Adobe Reader, Word, Google Документы)
     */
    fun openFile(context: Context, item: ExportedFileItem): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                item.file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.format.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Открыть ${item.fileName}"))
            true
        } catch (e: Exception) {
            android.util.Log.e("FileExportManager", "Не удалось открыть файл: ${e.message}")
            false
        }
    }

    /**
     * Отправка файла через системный диалог "Поделиться"
     */
    fun shareFile(context: Context, item: ExportedFileItem): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                item.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Поделиться файлом").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            android.util.Log.e("FileExportManager", "Не удалось поделиться файлом: ${e.message}")
            false
        }
    }

    /**
     * Удаление отдельного экспортированного файла
     */
    fun deleteExportedFile(item: ExportedFileItem): Boolean {
        return try {
            if (item.file.exists()) {
                item.file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun mapToFileItem(file: File, format: DocumentFormat): ExportedFileItem {
        val size = file.length()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return ExportedFileItem(
            file = file,
            fileName = file.name,
            format = format,
            sizeBytes = size,
            formattedSize = formatFileSize(size),
            lastModifiedMs = file.lastModified(),
            formattedDate = dateFormat.format(Date(file.lastModified()))
        )
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f МБ", size.toFloat() / (1024 * 1024))
            size >= 1024 -> String.format(Locale.getDefault(), "%.1f КБ", size.toFloat() / 1024)
            else -> "$size Б"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
        return clean.take(40).ifBlank { "Стенограмма" }
    }
}
