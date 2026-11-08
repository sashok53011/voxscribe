package com.example.modules.speaker_diarization

import android.util.Log
import com.example.modules.word_timestamps.WordTimestamp
import com.example.modules.word_timestamps.WordTimestampModule

/**
 * =========================================================================================
 * МОДУЛЬ: SpeakerDiarizerModule (Диаризация и автоматическое определение спикеров и пола)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Разделение аудиопотока на реплики разных говорящих (Спикер 1, Спикер 2...) и автоматическое
 * определение пола голоса (мужской/женский) по фундаментальной частоте тона (F0 / Pitch).
 * Это обеспечивает правильную разметку диалогов и автоматический подбор мужского или женского
 * голоса для последующего воспроизведения через TTS.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - pcmSamples (ShortArray): Массив аудиосэмплов текущего фрагмента речи.
 * - sampleRate (Int): Частота дискретизации (обычно 16000 Гц).
 * - recognizedText (String): Распознанный текст фрагмента.
 * - timestampMs (Long): Временная метка начала фрагмента.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - SpeakerSegment: Объект сегмента речи, содержащий ID спикера, отображаемое имя,
 *   определенный пол (MALE / FEMALE), частоту тона Pitch в Гц, текст и таймкоды.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val diarizer = SpeakerDiarizerModule()
 * val segment = diarizer.processSpeechSegment(
 *     pcmSamples = pcmChunk,
 *     sampleRate = 16000,
 *     recognizedText = "Привет, как дела?",
 *     timestampMs = System.currentTimeMillis()
 * )
 * println("Говорит: ${segment.speakerDisplayName}, Пол: ${segment.gender}")
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль использует встроенный акустический алгоритм автокорреляции (Autocorrelation F0 Extraction)
 * с физиологическими границами человеческого голоса:
 * - Мужской голос: F0 в диапазоне ~85-165 Гц.
 * - Женский голос: F0 в диапазоне ~165-265 Гц.
 * В продакшене для дифференциации 10+ человек с одинаковым тембром данную реализацию можно дополнить
 * нейросетевыми эмбеддингами голоса d-vector / x-vector (например, модель PyAnnote или ResNet TFLite),
 * сохраняющими голосовой отпечаток каждого участника.
 */
class SpeakerDiarizerModule {

    enum class SpeakerGender {
        MALE,
        FEMALE,
        UNKNOWN
    }

    data class SpeakerProfile(
        val id: String,
        val displayName: String,
        val gender: SpeakerGender,
        val averagePitchHz: Float,
        var turnCount: Int = 0
    )

    data class SpeakerSegment(
        val segmentId: String = java.util.UUID.randomUUID().toString(),
        val speakerId: String,
        val speakerDisplayName: String,
        val gender: SpeakerGender,
        val pitchHz: Float,
        val text: String,
        val translatedText: String = "",
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<WordTimestamp> = emptyList()
    )

    private val activeSpeakers = mutableListOf<SpeakerProfile>()
    private var lastSpeakerId: String? = null
    private var lastSpeechEndTimeMs: Long = 0L

    init {
        // Инициализируем стандартные базовые профили собеседников
        activeSpeakers.add(
            SpeakerProfile(
                id = "speaker_1",
                displayName = "Спикер 1 (Мужской)",
                gender = SpeakerGender.MALE,
                averagePitchHz = 125.0f
            )
        )
        activeSpeakers.add(
            SpeakerProfile(
                id = "speaker_2",
                displayName = "Спикер 2 (Женский)",
                gender = SpeakerGender.FEMALE,
                averagePitchHz = 210.0f
            )
        )
    }

    /**
     * Оценка фундаментальной частоты тона (F0 / Pitch) через автокорреляцию
     */
    fun estimateFundamentalFrequency(samples: ShortArray, sampleRate: Int = 16000): Float {
        if (samples.size < 512) return 150f

        // Физиологические рамки голоса человека: от 70 Гц до 350 Гц
        val minLag = (sampleRate / 350).coerceAtLeast(1)
        val maxLag = (sampleRate / 70).coerceAtMost(samples.size / 2)

        var bestLag = -1
        var maxCorrelation = 0.0

        for (lag in minLag..maxLag) {
            var correlation = 0.0
            for (i in 0 until (samples.size - lag)) {
                correlation += samples[i].toDouble() * samples[i + lag].toDouble()
            }
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestLag = lag
            }
        }

        if (bestLag > 0) {
            val estimatedF0 = sampleRate.toFloat() / bestLag
            if (estimatedF0 in 70f..350f) {
                return estimatedF0
            }
        }

        // Значение по умолчанию, если шум или шепот
        return 150f
    }

    /**
     * Определение пола по частоте тона (F0):
     * Мужской: < 165 Гц
     * Женский: >= 165 Гц
     */
    fun classifyGender(pitchHz: Float): SpeakerGender {
        return if (pitchHz <= 0f) {
            SpeakerGender.UNKNOWN
        } else if (pitchHz < 165.0f) {
            SpeakerGender.MALE
        } else {
            SpeakerGender.FEMALE
        }
    }

    /**
     * Обработка реплики: выбор или создание спикера, сопоставление по полу и динамике диалога
     */
    fun processSpeechSegment(
        pcmSamples: ShortArray?,
        sampleRate: Int = 16000,
        recognizedText: String,
        timestampMs: Long,
        durationMs: Long = 2500L,
        explicitWords: List<WordTimestamp>? = null
    ): SpeakerSegment {
        val pitch = if (pcmSamples != null && pcmSamples.isNotEmpty()) {
            estimateFundamentalFrequency(pcmSamples, sampleRate)
        } else {
            // Эвристика по чередованию при отсутствии сырых PCM сэмплов
            if (lastSpeakerId == "speaker_1") 205f else 120f
        }

        val gender = classifyGender(pitch)

        // Поиск наиболее подходящего профиля
        val matchedProfile = activeSpeakers.find { it.gender == gender } ?: run {
            val newIndex = activeSpeakers.size + 1
            val label = if (gender == SpeakerGender.MALE) "Спикер $newIndex (Мужской)" else "Спикер $newIndex (Женский)"
            val newProfile = SpeakerProfile(
                id = "speaker_$newIndex",
                displayName = label,
                gender = gender,
                averagePitchHz = pitch
            )
            activeSpeakers.add(newProfile)
            newProfile
        }

        matchedProfile.turnCount++
        lastSpeakerId = matchedProfile.id
        lastSpeechEndTimeMs = timestampMs + durationMs

        val calculatedWords = if (!explicitWords.isNullOrEmpty()) {
            explicitWords
        } else {
            WordTimestampModule.alignWordsToDuration(
                text = recognizedText,
                segmentStartMs = timestampMs,
                segmentEndMs = lastSpeechEndTimeMs,
                speakerId = matchedProfile.id
            )
        }

        return SpeakerSegment(
            speakerId = matchedProfile.id,
            speakerDisplayName = matchedProfile.displayName,
            gender = matchedProfile.gender,
            pitchHz = pitch,
            text = recognizedText.trim(),
            translatedText = "",
            startTimeMs = timestampMs,
            endTimeMs = lastSpeechEndTimeMs,
            words = calculatedWords
        )
    }

    /**
     * Сброс истории диаризации для новой сессии записи
     */
    fun reset() {
        lastSpeakerId = null
        lastSpeechEndTimeMs = 0L
        activeSpeakers.clear()
        activeSpeakers.add(
            SpeakerProfile("speaker_1", "Спикер 1 (Мужской)", SpeakerGender.MALE, 125.0f)
        )
        activeSpeakers.add(
            SpeakerProfile("speaker_2", "Спикер 2 (Женский)", SpeakerGender.FEMALE, 210.0f)
        )
    }
}
