package com.example.modules.transcription_filter

/**
 * Фильтрация галлюцинаций STT (Whisper и совместимые), которые часто появляются
 * на тишине/шуме: «продолжение следует», «danke», «thank you for watching» и т.п.
 */
object TranscriptionSanitizer {

    private val hardHallucinationPhrases = setOf(
        "продолжение следует",
        "fortsetzung folgt",
        "thank you for watching",
        "thanks for watching",
        "please subscribe",
        "subtitles by",
        "amara.org",
        "translated by",
        "субтитры",
        "редактор субтитров",
        "спасибо за просмотр",
        "спасибо за внимание",
        "untertitel",
        "danke fürs zuschauen",
        "danke fuer's zuschauen"
    )

    private val softFillerPhrases = setOf(
        "vielen dank",
        "vielen danke",
        "thank you",
        "thanks",
        "danke",
        "спасибо",
        "bitte",
        "okay",
        "ok"
    )

    private val nonSpeechMarkers = setOf(
        "[музыка]", "(музыка)", "[music]", "(music)",
        "[applause]", "(applause)", "[laughter]", "(laughter)",
        "[шум]", "(шум)", "[тишина]", "(тишина)",
        "[music playing]", "(music playing)"
    )

    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[\\p{Punct}…\\[\\]()]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * @param noSpeechProb значение no_speech_prob от Whisper (0..1), если доступно
     * @param durationMs длительность фрагмента в мс
     */
    fun isHallucination(text: String, noSpeechProb: Float? = null, durationMs: Long = 0L, avgLogProb: Float? = null): Boolean {
        val rawLower = text.lowercase().trim()
        val n = normalize(text)
        if (n.isBlank()) return true

        val isSoftFiller = n in softFillerPhrases || softFillerPhrases.any { n == it }

        if (noSpeechProb != null && noSpeechProb >= 0.55f) return true
        if (avgLogProb != null && avgLogProb < -1.0f && (isSoftFiller || n.length < 20)) return true

        if (n in hardHallucinationPhrases) return true
        if (hardHallucinationPhrases.any { n == it || n.startsWith("$it ") || n.endsWith(" $it") }) return true

        // Мягкие филлеры отбрасываем только для коротких/сомнительных фрагментов
        if (isSoftFiller && (noSpeechProb == null || noSpeechProb >= 0.3f || durationMs < 1500L)) return true

        if (nonSpeechMarkers.any { rawLower.contains(it) }) return true
        if (nonSpeechMarkers.map { it.trim('[', ']', '(', ')', ' ') }.any { it.isNotBlank() && n == it }) return true

        // Короткие фиксированные фразы на длинных фрагментах тишины
        if (durationMs >= 1500L && n.length <= 12 && (n == "danke" || n == "thank you" || n == "спасибо")) {
            if (noSpeechProb == null || noSpeechProb >= 0.35f) return true
        }

        return false
    }

    fun isAllowed(text: String, noSpeechProb: Float? = null, durationMs: Long = 0L): Boolean =
        !isHallucination(text, noSpeechProb, durationMs)
}