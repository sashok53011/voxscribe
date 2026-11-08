package com.example.modules.document_export

import android.content.Context
import android.util.Log
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * =========================================================================================
 * МОДУЛЬ: DocxExporterModule (Экспорт транскрипций в документ Microsoft Word DOCX)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Создание валидных документов формата DOCX (Office OpenXML), с выделением заголовков,
 * реплик спикеров жирным шрифтом, курсивного перевода и метаданных.
 * Не требует внешних тяжелых библиотек (Apache POI), формируется через нативный ZIP OpenXML.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст для доступа к каталогу кэша/файлов.
 * - title (String): Название документа.
 * - folderName (String): Имя папки.
 * - segments (List<SpeakerSegment>): Сегменты речи.
 * - creationDateMs (Long): Время создания.
 * - targetLanguageName (String?): Название языка перевода при наличии.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - File: Готовый `.docx` файл, готовый к открытию в Word или отправке.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val docxExporter = DocxExporterModule(context)
 * val docxFile = docxExporter.exportToDocx(title, folder, segments, date)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль формирует спецификацию ECMA-376 OpenXML пакета через потоковый ZipOutputStream.
 * В продакшене для добавления сложных графиков, многоуровневых колонтитулов и встроенных
 * аудио-вложений можно использовать библиотеку docx4j.
 */
class DocxExporterModule(private val context: Context) {

    fun exportToDocx(
        title: String,
        folderName: String,
        segments: List<SpeakerSegment>,
        creationDateMs: Long,
        targetLanguageName: String? = null
    ): File {
        val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9а-яА-Я._-]"), "_")
        val outputFile = File(outputDir, "${cleanFileName}_${System.currentTimeMillis()}.docx")

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(creationDateMs))

        val documentXml = buildDocumentXml(title, folderName, dateStr, targetLanguageName, segments)
        val contentTypesXml = buildContentTypesXml()
        val relsXml = buildRelsXml()
        val documentRelsXml = buildDocumentRelsXml()

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(relsXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. word/_rels/document.xml.rels
                zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zos.write(documentRelsXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 4. word/document.xml
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(documentXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        Log.d("DocxExporter", "DOCX успешно сформирован: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildDocumentXml(
        title: String,
        folderName: String,
        dateStr: String,
        targetLanguageName: String?,
        segments: List<SpeakerSegment>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
        sb.append("<w:body>")

        // Заголовок
        sb.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/><w:jc w:val=\"left\"/></w:pPr>")
        sb.append("<w:r><w:rPr><w:b/><w:sz w:val=\"40\"/><w:color w:val=\"1A237E\"/></w:rPr>")
        sb.append("<w:t>${escapeXml(title)}</w:t></w:r></w:p>")

        // Метаинформация
        val meta = "Дата: $dateStr | Папка: $folderName" + if (targetLanguageName != null) " | Перевод: $targetLanguageName" else ""
        sb.append("<w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr>")
        sb.append("<w:r><w:rPr><w:i/><w:color w:val=\"666666\"/><w:sz w:val=\"20\"/></w:rPr>")
        sb.append("<w:t>${escapeXml(meta)}</w:t></w:r></w:p>")

        // Разделитель
        sb.append("<w:p><w:r><w:t>────────────────────────────────────────</w:t></w:r></w:p>")

        // Реплики спикеров
        for (segment in segments) {
            val timeOffset = SimpleDateFormat("mm:ss", Locale.getDefault()).format(Date(segment.startTimeMs))
            val speakerHeader = "• ${segment.speakerDisplayName} [$timeOffset]"

            // Имя спикера
            sb.append("<w:p><w:pPr><w:spacing w:before=\"160\" w:after=\"40\"/></w:pPr>")
            sb.append("<w:r><w:rPr><w:b/><w:color w:val=\"0D47A1\"/><w:sz w:val=\"24\"/></w:rPr>")
            sb.append("<w:t>${escapeXml(speakerHeader)}</w:t></w:r></w:p>")

            // Текст
            sb.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr>")
            sb.append("<w:r><w:rPr><w:sz w:val=\"22\"/></w:rPr>")
            sb.append("<w:t>${escapeXml(segment.text)}</w:t></w:r></w:p>")

            // Перевод (если есть)
            if (segment.translatedText.isNotBlank()) {
                sb.append("<w:p><w:pPr><w:spacing w:after=\"120\"/></w:pPr>")
                sb.append("<w:r><w:rPr><w:i/><w:color w:val=\"00796B\"/><w:sz w:val=\"20\"/></w:rPr>")
                sb.append("<w:t>${escapeXml("↳ [Перевод]: " + segment.translatedText)}</w:t></w:r></w:p>")
            }
        }

        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
    }

    private fun buildRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun buildDocumentRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
</Relationships>"""
    }
}
