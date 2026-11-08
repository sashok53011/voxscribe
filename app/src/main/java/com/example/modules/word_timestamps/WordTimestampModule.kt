package com.example.modules.word_timestamps

import org.json.JSONArray
import org.json.JSONObject

/**
 * =========================================================================================
 * МОДУЛЬ: WordTimestampModule (Хранение, сериализация и расчет пословных таймкодов речи)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Обеспечивает точную пословную привязку расшифрованного текста к временной шкале аудио (startMs, endMs).
 * Позволяет интерфейсу (UI) подсвечивать каждое отдельное слово в реальном времени при воспроизведении
 * аудиофайла или при синтезе речи (TTS).
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - word (String): Отдельное слово или токен пунктуации.
 * - startMs (Long): Время начала произнесения слова в миллисекундах.
 * - endMs (Long): Время окончания произнесения слова в миллисекундах.
 * - speakerId (String): Идентификатор говорящего (например, "speaker_1").
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - WordTimestamp: Неизменяемая модель таймкода слова.
 * - JSON-строки для хранения в Room Database.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val word = WordTimestamp(word = "Привет", startMs = 120L, endMs = 450L, speakerId = "speaker_1")
 * val json = WordTimestampModule.serializeWords(listOf(word))
 * val restoredWords = WordTimestampModule.deserializeWords(json)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН (Stubs & Production):
 * 1. При работе с облачным Groq Whisper API (модель `whisper-large-v3` с параметром
 *    `timestamp_granularities[]=["word"]`) таймкоды приходят напрямую от нейросети с миллисекундной точностью.
 * 2. При оффлайн-распознавании или при отсутствии посекундных меток модуль использует встроенный
 *    эвристический алгоритм пропорционального распределения длительности с учетом слоговой структуры
 *    и длины слов (`alignWordsToDuration`).
 * 3. В продакшене для 100% автономного выравнивания локального звука эту эвристику можно заменить на
 *    библиотеку Forced Alignment (например, Chkpt CTC Forced Aligner или Vosk Word Timestamps).
 */
data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String = ""
)

object WordTimestampModule {

    /**
     * Сериализация списка таймкодов слов в компактный JSON для сохранения в Room
     */
    fun serializeWords(words: List<WordTimestamp>): String {
        if (words.isEmpty()) return "[]"
        val array = JSONArray()
        for (w in words) {
            val obj = JSONObject().apply {
                put("w", w.word)
                put("s", w.startMs)
                put("e", w.endMs)
                if (w.speakerId.isNotBlank()) {
                    put("spk", w.speakerId)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Десериализация JSON из базы данных Room в список объектов WordTimestamp
     */
    fun deserializeWords(json: String): List<WordTimestamp> {
        if (json.isBlank() || json == "[]") return emptyList()
        val list = mutableListOf<WordTimestamp>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WordTimestamp(
                        word = obj.optString("w", obj.optString("word", "")),
                        startMs = obj.optLong("s", obj.optLong("startMs", 0L)),
                        endMs = obj.optLong("e", obj.optLong("endMs", 0L)),
                        speakerId = obj.optString("spk", obj.optString("speakerId", ""))
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WordTimestampModule", "Ошибка парсинга таймкодов слов: ${e.message}")
        }
        return list
    }

    /**
     * Эвристическое пропорциональное выравнивание слов по временному интервалу сегмента.
     * Используется для оффлайн-записей и когда провайдер STT вернул только границы реплики.
     * Учитывает длину каждого слова и паузы между ними.
     */
    fun alignWordsToDuration(
        text: String,
        segmentStartMs: Long,
        segmentEndMs: Long,
        speakerId: String = ""
    ): List<WordTimestamp> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return emptyList()

        val rawTokens = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return emptyList()

        val totalDurationMs = (segmentEndMs - segmentStartMs).coerceAtLeast(rawTokens.size * 180L)

        // Вес каждого слова пропорционален количеству символов плюс базовое время произнесения (слог)
        val wordWeights = rawTokens.map { token ->
            val charCount = token.length.coerceAtLeast(1)
            // Гласные буквы для приблизительной оценки слогов
            val vowelsCount = token.count { it.lowercaseChar() in "аеёиоуыэюяaeiouyäöü" }.coerceAtLeast(1)
            charCount + (vowelsCount * 2)
        }

        val totalWeight = wordWeights.sum().coerceAtLeast(1)
        val result = mutableListOf<WordTimestamp>()
        var currentCursorMs = segmentStartMs

        for (i in rawTokens.indices) {
            val token = rawTokens[i]
            val weight = wordWeights[i]
            val durationShare = (totalDurationMs * weight) / totalWeight
            val wordDurationMs = durationShare.coerceAtLeast(120L)

            val wordEndMs = (currentCursorMs + wordDurationMs).coerceAtMost(segmentStartMs + totalDurationMs)
            result.add(
                WordTimestamp(
                    word = token,
                    startMs = currentCursorMs,
                    endMs = wordEndMs,
                    speakerId = speakerId
                )
            )
            // Микро-пауза 20 мс между словами
            currentCursorMs = wordEndMs + 20L
        }

        return result
    }
}
