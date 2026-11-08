package com.example.modules.openai_endpoints

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * =========================================================================================
 * МОДУЛЬ: OpenAiTtsModule (Кастомный OpenAI-совместимый эндпоинт синтеза речи с Audio Ducking)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Синтез речи через любой OpenAI-совместимый эндпоинт `POST {baseUrl}/audio/speech`
 * (OpenAI TTS-1, Kokoro, Piper, LocalAI, vLLM).
 * Автоматически подбирает мужской/женский голос в зависимости от спикера и
 * реализует **Audio Ducking**: если на устройстве играет музыка (Spotify, VK Музыка, YouTube),
 * она плавно приглушается на время озвучивания и восстанавливается по окончании.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - text (String): Текст для озвучивания.
 * - gender (SpeakerGender): Пол спикера (MALE -> onyx/echo, FEMALE -> nova/shimmer).
 * - onComplete (() -> Unit): Колбэк по завершении воспроизведения.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Воспроизведение аудиопотока через MediaPlayer с приглушением фоновой музыки.
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН (Stubs & Production):
 * Заглушек нет — аудио скачивается в формате MP3 во временный кэш и воспроизводится
 * через системный медиаплеер с управлением аудиофокусом.
 */
class OpenAiTtsModule(
    private val context: Context,
    private val config: OpenAiEndpointsConfigModule = OpenAiEndpointsConfigModule(context)
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isDuckingActive = false
    private val playbackMutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "OpenAiTtsModule"
        val SUPPORTED_VOICES = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
    }

    /**
     * Озвучить текст через OpenAI TTS API с приглушением музыки (Audio Ducking)
     */
    suspend fun speakText(
        text: String,
        gender: SpeakerGender = SpeakerGender.UNKNOWN,
        languageCode: String = "",
        onComplete: () -> Unit = {}
    ): Result<Unit> {
        playbackMutex.lock()
        try {
            return withContext(Dispatchers.IO) {
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) { onComplete() }
                    return@withContext Result.success(Unit)
                }

                val baseUrl = config.getTtsBaseUrl().trimEnd('/')
                val apiKey = config.getTtsApiKey()
                val model = config.getTtsModel().ifBlank { "tts-1" }
                val voice = when (gender) {
                    SpeakerGender.MALE -> "onyx"
                    SpeakerGender.FEMALE -> "nova"
                    SpeakerGender.UNKNOWN -> config.getTtsVoice().ifBlank { "alloy" }
                }

                val jsonBody = JSONObject().apply {
                    put("model", model)
                    put("input", text)
                    put("voice", voice)
                    put("response_format", "mp3")
                    if (languageCode.isNotBlank() && model.startsWith("gpt-4o-mini-tts")) {
                        put("instructions", "Speak in ${languageName(languageCode)}.")
                    }
                }
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/audio/speech")
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                var generatedFile: File? = null
                try {
                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }

                    val audioBytes = response.body?.bytes()
                        ?: return@withContext Result.failure(Exception("Пустой ответ от сервера"))
                    val audioFile = File(context.cacheDir, "openai_tts_${System.currentTimeMillis()}.mp3")
                    generatedFile = audioFile
                    FileOutputStream(audioFile).use { it.write(audioBytes) }
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            playAudioFileWithDucking(audioFile) {
                                try {
                                    onComplete()
                                } finally {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка генерации речи: ${e.message}")
                    generatedFile?.delete()
                    if (e is CancellationException) throw e
                    withContext(Dispatchers.Main) { onComplete() }
                    Result.failure(e)
                }
            }
        } finally {
            playbackMutex.unlock()
        }
    }

    private fun languageName(languageCode: String): String = when (languageCode.lowercase()) {
        "de" -> "German"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "it" -> "Italian"
        "ja" -> "Japanese"
        "ru" -> "Russian"
        "zh" -> "Chinese"
        else -> languageCode
    }

    /**
     * Воспроизведение скачанного файла с запросом Audio Ducking
     */
    private fun playAudioFileWithDucking(file: File, onComplete: () -> Unit) {
        stop()

        requestDuckingAudioFocus()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnCompletionListener {
                    abandonDuckingAudioFocus()
                    file.delete()
                    onComplete()
                }
                setOnErrorListener { _, _, _ ->
                    abandonDuckingAudioFocus()
                    file.delete()
                    onComplete()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка MediaPlayer: ${e.message}")
            abandonDuckingAudioFocus()
            onComplete()
        }
    }

    /**
     * Запрос аудиофокуса с флагом AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
     * Фоновая музыка не выключается, а лишь приглушается!
     */
    private fun requestDuckingAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stop()
                    }
                }
                .build()

            audioFocusRequest = focusRequest
            val res = audioManager.requestAudioFocus(focusRequest)
            isDuckingActive = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            isDuckingActive = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }

    /**
     * Возврат исходной громкости музыке
     */
    private fun abandonDuckingAudioFocus() {
        if (!isDuckingActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        isDuckingActive = false
    }

    /**
     * Остановка синтеза и освобождение аудиофокуса
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки плеера: ${e.message}")
        } finally {
            mediaPlayer = null
            abandonDuckingAudioFocus()
        }
    }

    /**
     * Проверка работы TTS эндпоинта
     */
    suspend fun testTts(
        baseUrl: String,
        apiKey: String,
        model: String,
        voice: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val jsonBody = JSONObject().apply {
            put("model", model.ifBlank { "tts-1" })
            put("input", "Тестовый синтез речи. VoxScribe готов к работе.")
            put("voice", voice.ifBlank { "alloy" })
            put("response_format", "mp3")
        }

        val requestBuilder = Request.Builder()
            .url("$cleanUrl/audio/speech")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val testFile = File(context.cacheDir, "test_tts.mp3")
                    FileOutputStream(testFile).use { it.write(bytes) }
                    withContext(Dispatchers.Main) {
                        playAudioFileWithDucking(testFile) {}
                    }
                    Result.success("Синтез успешен! Воспроизводится аудио (${bytes.size} байт).")
                } else {
                    Result.failure(Exception("Получен пустой аудиофайл"))
                }
            } else {
                val err = response.body?.string() ?: ""
                Result.failure(Exception("HTTP ${response.code}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка соединения: ${e.message}"))
        }
    }
}
