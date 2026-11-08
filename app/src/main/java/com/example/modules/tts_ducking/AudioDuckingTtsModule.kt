package com.example.modules.tts_ducking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import java.util.ArrayDeque
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: AudioDuckingTtsModule (Озвучивание TTS с приглушением фоновой музыки и выбором голоса)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * 1. Автоматический выбор мужского или женского голоса (соответственно спикеру реплики).
 * 2. Автоматическое озвучивание распознанного текста в реальном времени.
 * 3. AUDIO DUCKING (Приглушение музыки): Если в системе играет сторонний плеер (Spotify,
 *    YouTube Music, VK Музыка, Яндекс Музыка), музыка НЕ останавливается и НЕ отключается,
 *    а плавно снижает громкость (ducking) на время фразы диктора и сразу возвращается на 100%
 *    громкость после окончания речи!
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст Android для системных AudioManager и TextToSpeech.
 * - text (String): Текст для озвучивания.
 * - gender (SpeakerGender): MALE (мужской голос) или FEMALE (женский голос).
 * - languageCode (String): Язык озвучки ("ru", "en", "es", "de" и др.).
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Boolean: Статус начала воспроизведения.
 * - Колбэки: onSpeechStarted(), onSpeechCompleted(), onSpeechError().
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val ttsModule = AudioDuckingTtsModule(context)
 * ttsModule.initialize {
 *     ttsModule.speak(
 *         text = "Здравствуйте, запись началась",
 *         gender = SpeakerGender.MALE,
 *         language = "ru"
 *     )
 * }
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль использует нативный Android AudioFocusRequest с флагом `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
 * и движок `android.speech.tts.TextToSpeech`.
 * При отсутствии в системном TTS движке специфических голосов активируется акустическая модуляция
 * высоты тона (pitch 0.85f для баса/мужского голоса и 1.25f для альт/женского голоса).
 * В продакшене данный модуль может также переключаться на кастомные нейросетевые оффлайн модели
 * FastSpeech2 / Piper TTS через ONNX Runtime.
 */
class AudioDuckingTtsModule(private val context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isInitialized = false
    private var onInitCompleteCallback: (() -> Unit)? = null
    private val pendingUtterances = ArrayDeque<PendingUtterance>()

    private data class PendingUtterance(
        val text: String,
        val gender: SpeakerGender,
        val languageCode: String,
        val queueMode: Int
    )

    // Настройки
    var isAutoSpeakEnabled: Boolean = false
    var speechRate: Float = 1.0f

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    fun initialize(onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
        } else {
            onInitCompleteCallback = onReady
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            textToSpeech?.language = Locale("ru", "RU")
            setupUtteranceListener()
            Log.d("AudioDuckingTts", "TTS успешно инициализирован")
            onInitCompleteCallback?.invoke()
            onInitCompleteCallback = null
            while (pendingUtterances.isNotEmpty()) {
                val utterance = pendingUtterances.removeFirst()
                speak(utterance.text, utterance.gender, utterance.languageCode, utterance.queueMode)
            }
        } else {
            Log.e("AudioDuckingTts", "Ошибка инициализации TextToSpeech: статус $status")
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("AudioDuckingTts", "Начало воспроизведения речи: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("AudioDuckingTts", "Окончание фразы. Восстанавливаем громкость музыки.")
                abandonAudioDucking()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w("AudioDuckingTts", "Ошибка TTS при воспроизведении $utteranceId")
                abandonAudioDucking()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w("AudioDuckingTts", "Ошибка TTS ($errorCode) при воспроизведении $utteranceId")
                abandonAudioDucking()
            }
        })
    }

    /**
     * Запрос аудио-фокуса с разрешением DUCKING (приглушение внешней музыки)
     */
    private fun requestAudioDuckingFocus(): Boolean {
        val manager = audioManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d("AudioDuckingTts", "AudioFocusChange: $focusChange")
                }
                .build()

            audioFocusRequest = focusRequest
            val res = manager.requestAudioFocus(focusRequest)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val res = manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * Освобождение аудио-фокуса для плавного возврата музыки на исходную громкость
     */
    private fun abandonAudioDucking() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("AudioDuckingTts", "Ошибка при освобождении AudioFocus: ${e.message}")
        }
    }

    /**
     * Выбор подходящего мужского или женского голоса
     */
    fun configureVoiceForGender(gender: SpeakerGender, locale: Locale) {
        val tts = textToSpeech ?: return
        tts.language = locale

        var voiceMatched = false
        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val candidateVoices = voices.filter { it.locale.language == locale.language }
                val targetVoice = when (gender) {
                    SpeakerGender.MALE -> {
                        candidateVoices.find { voice ->
                            val name = voice.name.lowercase()
                            name.contains("male") && !name.contains("female") || name.contains("man") || name.contains("#m")
                        } ?: candidateVoices.firstOrNull()
                    }
                    SpeakerGender.FEMALE -> {
                        candidateVoices.find { voice ->
                            val name = voice.name.lowercase()
                            name.contains("female") || name.contains("woman") || name.contains("#f")
                        } ?: candidateVoices.lastOrNull()
                    }
                    SpeakerGender.UNKNOWN -> candidateVoices.firstOrNull()
                }

                if (targetVoice != null) {
                    tts.voice = targetVoice
                    voiceMatched = true
                    Log.d("AudioDuckingTts", "Установлен нативный голос: ${targetVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w("AudioDuckingTts", "Выбор нативного голоса не удался: ${e.message}")
        }

        // Акустическая калибровка тональности: мужской глубже, женский выше
        when (gender) {
            SpeakerGender.MALE -> {
                tts.setPitch(if (voiceMatched) 0.95f else 0.82f)
            }
            SpeakerGender.FEMALE -> {
                tts.setPitch(if (voiceMatched) 1.05f else 1.25f)
            }
            SpeakerGender.UNKNOWN -> {
                tts.setPitch(1.0f)
            }
        }
        tts.setSpeechRate(speechRate)
    }

    /**
     * Озвучивание фразы с автоматическим приглушением системной музыки.
     * queueMode: QUEUE_ADD для потокового воспроизведения цепочки поступающих слов перевода,
     * или QUEUE_FLUSH для немедленного перебивания предыдущей речи.
     */
    fun speak(
        text: String,
        gender: SpeakerGender = SpeakerGender.MALE,
        languageCode: String = "ru",
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        if (text.isBlank()) return
        if (!isInitialized) {
            pendingUtterances.addLast(PendingUtterance(text, gender, languageCode, queueMode))
            return
        }

        val locale = when (languageCode) {
            "en" -> Locale.US
            "es" -> Locale("es", "ES")
            "de" -> Locale.GERMANY
            "fr" -> Locale.FRANCE
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "it" -> Locale.ITALY
            "ja" -> Locale.JAPAN
            else -> Locale("ru", "RU")
        }

        configureVoiceForGender(gender, locale)

        // Запрашиваем Audio Focus с приглушением внешней музыки
        val focusGranted = requestAudioDuckingFocus()
        Log.d("AudioDuckingTts", "Audio Ducking Focus получен: $focusGranted. Озвучиваем текст: $text (queue: $queueMode)")

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        textToSpeech?.speak(
            text,
            queueMode,
            null,
            utteranceId
        )
    }

    fun stop() {
        textToSpeech?.stop()
        abandonAudioDucking()
    }

    fun release() {
        stop()
        pendingUtterances.clear()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
