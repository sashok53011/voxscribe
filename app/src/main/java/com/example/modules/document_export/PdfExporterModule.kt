package com.example.modules.document_export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: PdfExporterModule (Экспорт транскрипций в форматированный документ PDF)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Генерация красивых PDF-документов с разметкой спикеров, временными метками,
 * оригинальным и переведенным текстом, названием папки и метаданными сессии.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст для работы с файловой системой.
 * - title (String): Заголовок записи.
 * - folderName (String): Название папки/проекта.
 * - segments (List<SpeakerSegment>): Список сегментов реплик спикеров.
 * - creationDateMs (Long): Дата и время создания записи.
 * - translatedLanguage (String?): Язык перевода, если есть.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - File: Локальный PDF-файл в кэше/документах приложения, готовый к отправке через Intent.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val exporter = PdfExporterModule(context)
 * val pdfFile = exporter.exportToPdf(
 *     title = "План спринта",
 *     folderName = "Работа",
 *     segments = list,
 *     creationDateMs = System.currentTimeMillis()
 * )
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль использует нативный Android SDK PdfDocument.
 * Для верстки больших документов свыше 50 страниц в продакшене можно интегрировать iText или
 * OpenPDF с автоматическим переносом таблиц и колонтитулов.
 */
class PdfExporterModule(private val context: Context) {

    fun exportToPdf(
        title: String,
        folderName: String,
        segments: List<SpeakerSegment>,
        creationDateMs: Long,
        targetLanguageName: String? = null
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4: 595 x 842 points
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.rgb(26, 35, 126) // Deep Indigo
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintSubtitle = Paint().apply {
            color = Color.rgb(90, 100, 120)
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintSpeaker = Paint().apply {
            color = Color.rgb(13, 71, 161)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val paintText = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val paintTranslation = Paint().apply {
            color = Color.rgb(0, 121, 107) // Teal
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        val paintLine = Paint().apply {
            color = Color.rgb(220, 225, 235)
            strokeWidth = 1f
        }

        val marginX = 40f
        var currentY = 50f
        val maxY = 790f
        var pageNumber = 1

        // 1. Шапка документа
        canvas.drawText("VoxScribe — Стенограмма аудио", marginX, currentY, paintSubtitle)
        currentY += 24f
        canvas.drawText(title, marginX, currentY, paintTitle)
        currentY += 18f

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val metaString = "Дата: ${dateFormat.format(Date(creationDateMs))} | Папка: $folderName" +
                if (targetLanguageName != null) " | Перевод: $targetLanguageName" else ""
        canvas.drawText(metaString, marginX, currentY, paintSubtitle)
        currentY += 12f

        canvas.drawLine(marginX, currentY, 555f, currentY, paintLine)
        currentY += 24f

        fun checkPageBreak(neededHeight: Float) {
            if (currentY + neededHeight > maxY) {
                pdfDocument.finishPage(page)
                pageNumber++
                val nextInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(nextInfo)
                canvas = page.canvas
                currentY = 45f
                canvas.drawText("VoxScribe | $title (Стр. $pageNumber)", marginX, currentY, paintSubtitle)
                currentY += 18f
                canvas.drawLine(marginX, currentY, 555f, currentY, paintLine)
                currentY += 20f
            }
        }

        // 2. Рендеринг реплик спикеров
        for (segment in segments) {
            checkPageBreak(50f)

            // Заголовок реплики (спикер и таймкод)
            val timeOffset = SimpleDateFormat("mm:ss", Locale.getDefault()).format(Date(segment.startTimeMs))
            val speakerHeader = "• ${segment.speakerDisplayName}  [$timeOffset]"
            canvas.drawText(speakerHeader, marginX, currentY, paintSpeaker)
            currentY += 16f

            // Разбивка текста на строки по ширине листа
            val originalLines = wrapText(segment.text, paintText, 510f)
            for (line in originalLines) {
                checkPageBreak(16f)
                canvas.drawText(line, marginX + 12f, currentY, paintText)
                currentY += 15f
            }

            // Рендеринг перевода (если присутствует)
            if (segment.translatedText.isNotBlank()) {
                val translationLines = wrapText("↳ [Перевод]: " + segment.translatedText, paintTranslation, 510f)
                for (line in translationLines) {
                    checkPageBreak(15f)
                    canvas.drawText(line, marginX + 16f, currentY, paintTranslation)
                    currentY += 14f
                }
            }

            currentY += 12f
        }

        pdfDocument.finishPage(page)

        // Сохранение в файл
        val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
        val outputFile = File(outputDir, "${cleanFileName}_${System.currentTimeMillis()}.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        Log.d("PdfExporter", "PDF успешно создан: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }
}
