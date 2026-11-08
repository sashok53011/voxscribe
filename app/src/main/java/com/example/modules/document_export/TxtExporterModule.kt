package com.example.modules.document_export

import android.content.Context
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: TxtExporterModule (Экспорт транскрипций в форматированный текстовый файл TXT)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Создание чистого текстового файла с таймкодами, именами спикеров и переводами.
 */
class TxtExporterModule(private val context: Context) {

    fun exportToTxt(
        title: String,
        folderName: String,
        segments: List<SpeakerSegment>,
        creationDateMs: Long,
        targetLanguageName: String? = null
    ): File {
        val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
        val outputFile = File(outputDir, "${cleanFileName}_${System.currentTimeMillis()}.txt")

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        sb.append("=== $title ===\n")
        sb.append("Дата: ${dateFormat.format(Date(creationDateMs))}\n")
        sb.append("Папка: $folderName\n")
        if (targetLanguageName != null) {
            sb.append("Перевод: $targetLanguageName\n")
        }
        sb.append("----------------------------------------\n\n")

        for (segment in segments) {
            val offset = timeFormat.format(Date(segment.startTimeMs))
            sb.append("[${segment.speakerDisplayName}] ($offset):\n")
            sb.append("${segment.text}\n")
            if (segment.translatedText.isNotBlank()) {
                sb.append("↳ Перевод: ${segment.translatedText}\n")
            }
            sb.append("\n")
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        return outputFile
    }
}
