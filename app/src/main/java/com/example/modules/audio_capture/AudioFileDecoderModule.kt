package com.example.modules.audio_capture

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * =========================================================================================
 * МОДУЛЬ: AudioFileDecoderModule (Декодер и извлечение метаданных аудиофайлов)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Обработка внешних аудиофайлов (WAV, MP3, M4A, OGG, FLAC, AAC), извлечение
 * их структуры, продолжительности, битрейта, канальности и подготовка к транскрибации.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст приложения для доступа к ContentResolver.
 * - audioUri (Uri): URI выбранного пользователем аудиофайла.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - AudioFileMetadata: Метаданные (название, размер, длительность в мс, формат, sampleRate, channels).
 * - ByteArray/PCM поток при декодировании для анализа.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val decoder = AudioFileDecoderModule(context)
 * val meta = decoder.extractMetadata(fileUri)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Извлечение метаданных и структуры аудио выполняется нативными компонентами Android MediaMetadataRetriever
 * и MediaExtractor.
 * В продакшене для пакетной транскрибации длинных файлов (1-2 часа) без перегрузки памяти
 * декодирование в PCM сэмплы связывается с MediaCodec потоковым генератором (chunked streaming),
 * либо локальной моделью Whisper.cpp через JNI.
 */
class AudioFileDecoderModule(private val context: Context) {

    data class AudioFileMetadata(
        val fileName: String,
        val sizeBytes: Long,
        val durationMs: Long,
        val mimeType: String,
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * Извлечение метаданных аудиофайла через MediaMetadataRetriever
     */
    fun extractMetadata(uri: Uri): AudioFileMetadata {
        var fileName = "audio_record"
        var sizeBytes = 0L

        // Получение имени файла и размера через ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioFileDecoder", "Не удалось прочитать имя файла через ContentResolver: ${e.message}")
        }

        var durationMs = 0L
        var mimeType = "audio/*"
        var sampleRate = 16000
        var channelCount = 1

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mimeStr.isNullOrEmpty()) mimeType = mimeStr

            // Извлечение информации о дорожке через MediaExtractor
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    break
                }
            }
            extractor.release()
        } catch (e: Exception) {
            Log.e("AudioFileDecoder", "Ошибка при анализе аудиофайла: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return AudioFileMetadata(
            fileName = fileName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            mimeType = mimeType,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }
}
