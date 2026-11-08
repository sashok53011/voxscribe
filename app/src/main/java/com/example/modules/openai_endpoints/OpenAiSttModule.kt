package com.example.modules.openai_endpoints

import android.content.Context
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import com.example.modules.transcription_filter.TranscriptionSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * МОДУЛЬ: OpenAiSttModule (Кастомный OpenAI-совместимый эндпоинт распознавания речи Whisper)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Обращение к любому сервису распознавания речи, совместимому со спецификацией OpenAI Whisper
 * `POST {baseUrl}/audio/transcriptions` (OpenAI Whisper-1, локальный faster-whisper,
 * whisper.cpp, LocalAI, vLLM).
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - audioFile (File): Аудиофайл (WAV, MP3, M4A, OGG).
 * - languageCode (String): Код языка ("ru", "de", "en", "").
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - [OpenAiTranscriptionResult]: Полный текст и список реплик с таймкодами.
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН (Stubs & Production):
 * Заглушек нет — отправляет multipart-запрос с реальным аудиофайлом.
 * При отсутствии ответа сервера возвращает структурированную ошибку.
 */
class OpenAiSttModule(
    private val context: Context,
    private val config: OpenAiEndpointsConfigModule = OpenAiEndpointsConfigModule(context)
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class OpenAiTranscriptionResult(
        val fullText: String,
        val segments: List<SpeakerSegment>,
        val detectedLanguage: String = ""
    )

    /**
     * Отправка аудиофайла на OpenAI-совместимый эндпоинт STT
     */
    suspend fun transcribeAudioFile(
        audioFile: File,
        languageCode: String = "ru"
    ): Result<OpenAiTranscriptionResult> = withContext(Dispatchers.IO) {
        val baseUrl = config.getSttBaseUrl().trimEnd('/')
        val apiKey = config.getSttApiKey()
        val model = config.getSttModel().ifBlank { "whisper-1" }

        val mimeType = when (audioFile.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/m4a"
            "ogg" -> "audio/ogg"
            else -> "audio/*"
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mimeType.toMediaType())
            )

        if (languageCode.isNotBlank()) {
            requestBody.addFormDataPart("language", languageCode.lowercase())
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .post(requestBody.build())

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val fullText = json.optString("text", "")
            val detectedLang = json.optString("language", languageCode)

            val segmentsList = mutableListOf<SpeakerSegment>()
            val rawSegments = json.optJSONArray("segments")

            if (rawSegments != null && rawSegments.length() > 0) {
                for (i in 0 until rawSegments.length()) {
                    val segObj = rawSegments.getJSONObject(i)
                    val startSec = segObj.optDouble("start", 0.0)
                    val endSec = segObj.optDouble("end", 0.0)
                    val segText = segObj.optString("text", "").trim()
                    if (segText.isEmpty()) continue
                    val noSpeechProb = segObj.optDouble("no_speech_prob", 0.0).toFloat()
                    val avgLogProb = segObj.optDouble("avg_logprob", 0.0).toFloat()
                    val durationMs = ((endSec - startSec) * 1000).toLong()
                    if (TranscriptionSanitizer.isHallucination(segText, noSpeechProb, durationMs, avgLogProb)) continue

                    // Простая эвристика чередования спикеров при отсутствии встроенной диаризации
                    val speakerIndex = (i % 2) + 1
                    val speakerGender = if (speakerIndex == 1) SpeakerGender.MALE else SpeakerGender.FEMALE

                    val startMs = (startSec * 1000).toLong()
                    val endMs = (endSec * 1000).toLong()
                    val words = com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                        text = segText,
                        segmentStartMs = startMs,
                        segmentEndMs = endMs,
                        speakerId = "speaker_$speakerIndex"
                    )

                    segmentsList.add(
                        SpeakerSegment(
                            segmentId = UUID.randomUUID().toString(),
                            speakerId = "speaker_$speakerIndex",
                            speakerDisplayName = "Спикер $speakerIndex",
                            gender = speakerGender,
                            pitchHz = if (speakerIndex == 1) 130f else 210f,
                            text = segText,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            words = words
                        )
                    )
                }
            } else if (fullText.isNotBlank() && !TranscriptionSanitizer.isHallucination(fullText)) {
                val words = com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                    text = fullText,
                    segmentStartMs = 0L,
                    segmentEndMs = 5000L,
                    speakerId = "speaker_1"
                )
                segmentsList.add(
                    SpeakerSegment(
                        segmentId = UUID.randomUUID().toString(),
                        speakerId = "speaker_1",
                        speakerDisplayName = "Спикер 1",
                        gender = SpeakerGender.MALE,
                        pitchHz = 140f,
                        text = fullText,
                        startTimeMs = 0L,
                        endTimeMs = 5000L,
                        words = words
                    )
                )
            }

            Result.success(
                OpenAiTranscriptionResult(
                    fullText = fullText,
                    segments = segmentsList,
                    detectedLanguage = detectedLang
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверка связи с эндпоинтом Whisper STT
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')

        // Для проверки делаем запрос к эндпоинту или моделям
        val requestBuilder = Request.Builder()
            .url("$cleanUrl/models")
            .get()

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success("Подключение успешно! Эндпоинт STT доступен.")
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка соединения: ${e.message}"))
        }
    }
}
