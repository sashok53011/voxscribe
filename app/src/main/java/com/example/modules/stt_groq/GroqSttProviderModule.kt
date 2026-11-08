package com.example.modules.stt_groq

import android.content.Context
import android.util.Log
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import com.example.modules.transcription_filter.TranscriptionSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * МОДУЛЬ: GroqSttProviderModule (Альтернативный сверхбыстрый облачный STT на базе Groq Whisper)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Высокоскоростная транскрибация аудиозаписей через специализированный LPU-ускоритель Groq
 * с использованием модели `whisper-large-v3` или `whisper-large-v3-turbo`.
 * Служит мощной альтернативой встроенному оффлайн-распознавателю Android.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - audioFile (File) / audioBytes (ByteArray): Аудиозапись (WAV, MP3, M4A, OGG).
 * - language (String): Язык аудио ("ru", "de", "en" или "auto").
 * - apiKey (String): API-ключ Groq (настраивается пользователем в приложении).
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - GroqTranscriptionResult:
 *   * text (String): Полный текст транскрипции.
 *   * segments (List<SpeakerSegment>): Сегменты реплик с таймкодами.
 *   * detectedLanguage (String): Автоопределенный язык.
 *   * durationSeconds (Float): Длительность аудио.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val groqStt = GroqSttProviderModule(context)
 * val result = groqStt.transcribeAudioFile(file, language = "de", apiKey = "gsk_...")
 * println("Транскрипция Groq: ${result.text}")
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль выполняет реальный REST API запрос через OkHttp к официальному эндпоинту:
 * `https://api.groq.com/openai/v1/audio/transcriptions`.
 * При отсутствии введенного ключа Groq или ошибке сети возвращает понятное уведомление и
 * позволяет переключиться обратно на встроенный оффлайн-движок Android.
 */
class GroqSttProviderModule(private val context: Context) {

    companion object {
        private const val TAG = "GroqSttProvider"
        private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val DEFAULT_MODEL = "whisper-large-v3"
        private const val PREFS_NAME = "groq_stt_settings"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_IS_GROQ_DEFAULT = "is_groq_default"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class GroqTranscriptionResult(
        val text: String,
        val segments: List<SpeakerSegment>,
        val detectedLanguage: String,
        val durationSeconds: Float,
        val allWords: List<com.example.modules.word_timestamps.WordTimestamp> = emptyList(),
        val isSuccess: Boolean = true,
        val errorMessage: String? = null
    )

    /**
     * Валидация / Проверка API-ключа Groq через официальный эндпоинт GET /openai/v1/models
     * Позволяет проверить доступность сервиса и получить список доступных моделей
     */
    suspend fun validateGroqApiKey(apiKey: String = getGroqApiKey()): Result<List<String>> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("Ключ Groq пуст. Введите ключ вида gsk_..."))
        }

        try {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data")
                    val models = mutableListOf<String>()
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).optString("id", "")
                            if (id.isNotBlank()) models.add(id)
                        }
                    }
                    val whisperModels = models.filter { it.contains("whisper", ignoreCase = true) }
                    Log.d(TAG, "Проверка Groq успешна. Найдено моделей: ${models.size}, whisper: $whisperModels")
                    Result.success(models.sorted())
                } else {
                    val errMsg = try {
                        JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $body"
                    }
                    Result.failure(Exception("Ошибка Groq [$errMsg]"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Сетевая ошибка при проверке Groq: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Сохранение ключа Groq в зашифрованных/локальных SharedPreferences
     */
    fun saveGroqApiKey(apiKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROQ_API_KEY, apiKey.trim())
            .apply()
    }

    /**
     * Получение сохраненного ключа Groq
     */
    fun getGroqApiKey(): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GROQ_API_KEY, "") ?: ""
    }

    /**
     * Флаг: выбран ли Groq STT основным провайдером
     */
    fun setGroqAsDefault(isDefault: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_GROQ_DEFAULT, isDefault)
            .apply()
    }

    fun isGroqDefault(): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_GROQ_DEFAULT, false)
    }

    /**
     * Отправка аудиофайла в Groq Whisper API с пословными таймкодами
     */
    suspend fun transcribeAudioFile(
        audioFile: File,
        language: String = "auto",
        apiKey: String = getGroqApiKey()
    ): GroqTranscriptionResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GroqTranscriptionResult(
                text = "",
                segments = emptyList(),
                detectedLanguage = language,
                durationSeconds = 0f,
                isSuccess = false,
                errorMessage = "API-ключ Groq не указан. Введите ключ в настройках приложения или выберите другой облачный STT-провайдер."
            )
        }

        try {
            val fileBody = audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, fileBody)
                .addFormDataPart("model", DEFAULT_MODEL)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("temperature", "0.0")
                .addFormDataPart("timestamp_granularities[]", "word")
                .addFormDataPart("timestamp_granularities[]", "segment")

            if (language.isNotBlank() && language != "auto") {
                multipartBuilder.addFormDataPart("language", language)
            }

            val request = Request.Builder()
                .url(GROQ_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errDetail = try {
                        JSONObject(bodyString).optJSONObject("error")?.optString("message") ?: bodyString
                    } catch (e: Exception) {
                        bodyString
                    }
                    Log.e(TAG, "Ошибка Groq API (${response.code}): $errDetail")
                    return@withContext GroqTranscriptionResult(
                        text = "",
                        segments = emptyList(),
                        detectedLanguage = language,
                        durationSeconds = 0f,
                        isSuccess = false,
                        errorMessage = "Ошибка Groq API [${response.code}]: $errDetail"
                    )
                }

                return@withContext parseVerboseJsonResponse(bodyString, language)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Сетевая ошибка при обращении к Groq: ${e.message}", e)
            return@withContext GroqTranscriptionResult(
                text = "",
                segments = emptyList(),
                detectedLanguage = language,
                durationSeconds = 0f,
                isSuccess = false,
                errorMessage = "Сетевая ошибка Groq: ${e.localizedMessage ?: "Проверьте интернет-соединение"}"
            )
        }
    }

    private fun parseVerboseJsonResponse(jsonString: String, requestedLanguage: String): GroqTranscriptionResult {
        val root = JSONObject(jsonString)
        val fullText = root.optString("text", "").trim()
        val detectedLang = root.optString("language", requestedLanguage)
        val duration = root.optDouble("duration", 0.0).toFloat()

        // 1. Извлекаем пословные таймкоды от Groq Whisper
        val allWordsList = mutableListOf<com.example.modules.word_timestamps.WordTimestamp>()
        val wordsArray = root.optJSONArray("words")
        if (wordsArray != null) {
            for (wIdx in 0 until wordsArray.length()) {
                val wObj = wordsArray.getJSONObject(wIdx)
                val wText = wObj.optString("word", "").trim()
                if (wText.isEmpty()) continue
                val wStart = (wObj.optDouble("start", 0.0) * 1000).toLong()
                val wEnd = (wObj.optDouble("end", (wStart + 250).toDouble() / 1000.0) * 1000).toLong()
                allWordsList.add(
                    com.example.modules.word_timestamps.WordTimestamp(
                        word = wText,
                        startMs = wStart,
                        endMs = wEnd
                    )
                )
            }
        }

        val parsedSegments = mutableListOf<SpeakerSegment>()
        val segmentsArray = root.optJSONArray("segments")

        if (segmentsArray != null && segmentsArray.length() > 0) {
            for (i in 0 until segmentsArray.length()) {
                val segObj = segmentsArray.getJSONObject(i)
                val segText = segObj.optString("text", "").trim()
                if (segText.isEmpty()) continue

                val noSpeechProb = segObj.optDouble("no_speech_prob", 0.0).toFloat()
                val avgLogProb = segObj.optDouble("avg_logprob", 0.0).toFloat()
                val startSec = segObj.optDouble("start", 0.0)
                val endSec = segObj.optDouble("end", startSec + 2.0)
                val durationMs = ((endSec - startSec) * 1000).toLong()
                if (TranscriptionSanitizer.isHallucination(segText, noSpeechProb, durationMs, avgLogProb)) continue
                val startMs = (startSec * 1000).toLong()
                val endMs = (endSec * 1000).toLong()

                // Эвристика спикеров по чередованию сегментов
                val isEven = (i % 2 == 0)
                val speakerLabel = if (isEven) "Спикер 1" else "Спикер 2"
                val speakerId = if (isEven) "speaker_1" else "speaker_2"
                val gender = if (isEven) SpeakerGender.MALE else SpeakerGender.FEMALE

                // Привязываем слова к этому сегменту по времени
                val segmentWords = if (allWordsList.isNotEmpty()) {
                    allWordsList.filter { it.startMs >= startMs - 200 && it.startMs <= endMs + 200 }
                        .map { it.copy(speakerId = speakerId) }
                } else {
                    com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                        text = segText,
                        segmentStartMs = startMs,
                        segmentEndMs = endMs,
                        speakerId = speakerId
                    )
                }

                parsedSegments.add(
                    SpeakerSegment(
                        segmentId = "groq_seg_$i",
                        speakerId = speakerId,
                        speakerDisplayName = speakerLabel,
                        gender = gender,
                        pitchHz = if (isEven) 120f else 210f,
                        text = segText,
                        translatedText = "",
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        words = segmentWords
                    )
                )
            }
        } else if (!TranscriptionSanitizer.isHallucination(fullText, null, (duration * 1000).toLong())) {
            // Если массив segments пуст, формируем единый сегмент
            val singleWords = if (allWordsList.isNotEmpty()) allWordsList else {
                com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
                    text = fullText,
                    segmentStartMs = 0L,
                    segmentEndMs = (duration * 1000).toLong(),
                    speakerId = "speaker_1"
                )
            }
            parsedSegments.add(
                SpeakerSegment(
                    segmentId = "groq_seg_single",
                    speakerId = "speaker_1",
                    speakerDisplayName = "Спикер 1",
                    gender = SpeakerGender.UNKNOWN,
                    pitchHz = 150f,
                    text = fullText,
                    translatedText = "",
                    startTimeMs = 0L,
                    endTimeMs = (duration * 1000).toLong(),
                    words = singleWords
                )
            )
        }

        val sanitizedFullText = if (parsedSegments.isEmpty()) "" else parsedSegments.joinToString(" ") { it.text }.ifBlank { fullText }
        return GroqTranscriptionResult(
            text = if (TranscriptionSanitizer.isHallucination(sanitizedFullText)) "" else sanitizedFullText,
            segments = parsedSegments,
            detectedLanguage = detectedLang,
            durationSeconds = duration,
            allWords = if (allWordsList.isNotEmpty()) allWordsList else parsedSegments.flatMap { it.words },
            isSuccess = true
        )
    }
}
