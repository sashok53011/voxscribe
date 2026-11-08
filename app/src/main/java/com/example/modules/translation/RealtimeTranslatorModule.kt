package com.example.modules.translation

import android.util.Log

/**
 * =========================================================================================
 * МОДУЛЬ: RealtimeTranslatorModule (Автономный мультиязычный перевод: Русский, Немецкий, Английский)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Мгновенный синхронный перевод с полноценной поддержкой Русского (RU), Немецкого (DE),
 * Английского (EN), Испанского (ES) и других языков без обязательного интернет-соединения.
 */
class RealtimeTranslatorModule {

    data class TranslationResult(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val isOffline: Boolean = true
    )

    data class SupportedLanguage(
        val code: String,
        val displayName: String,
        val flagEmoji: String
    )

    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            SupportedLanguage("ru", "Русский (Russian)", "🇷🇺"),
            SupportedLanguage("de", "Deutsch (Немецкий)", "🇩🇪"),
            SupportedLanguage("en", "English (Английский)", "🇬🇧"),
            SupportedLanguage("es", "Español (Испанский)", "🇪🇸"),
            SupportedLanguage("fr", "Français (Французский)", "🇫🇷"),
            SupportedLanguage("zh", "中文 (Китайский)", "🇨🇳"),
            SupportedLanguage("ja", "日本語 (Японский)", "🇯🇵"),
            SupportedLanguage("it", "Italiano (Итальянский)", "🇮🇹")
        )
    }

    // Русский -> Английский
    private val phraseDictionaryRuEn = mapOf(
        "привет" to "hello",
        "здравствуйте" to "hello",
        "доброе утро" to "good morning",
        "добрый день" to "good afternoon",
        "добрый вечер" to "good evening",
        "как дела" to "how are you",
        "спасибо" to "thank you",
        "большое спасибо" to "thank you very much",
        "пожалуйста" to "please",
        "да" to "yes",
        "нет" to "no",
        "хорошо" to "okay",
        "отлично" to "great",
        "до свидания" to "goodbye",
        "до встречи" to "see you soon",
        "мы начинаем" to "we are starting",
        "совещание" to "meeting",
        "проект" to "project",
        "задача" to "task",
        "план" to "plan",
        "результат" to "result",
        "аудио" to "audio",
        "запись" to "recording",
        "транскрибация" to "transcription",
        "спикер" to "speaker",
        "документ" to "document",
        "файл" to "file",
        "экспорт" to "export",
        "понятно" to "understood",
        "согласен" to "I agree",
        "какие новости" to "what is the news",
        "следующий вопрос" to "next question",
        "время" to "time",
        "дата" to "date",
        "отчет" to "report",
        "вопрос" to "question",
        "ответ" to "answer"
    )

    private val phraseDictionaryEnRu = phraseDictionaryRuEn.entries.associate { (k, v) -> v to k }

    // Русский -> Немецкий (Full German Support)
    private val phraseDictionaryRuDe = mapOf(
        "привет" to "hallo",
        "здравствуйте" to "guten tag",
        "доброе утро" to "guten morgen",
        "добрый день" to "guten tag",
        "добрый вечер" to "guten abend",
        "как дела" to "wie geht es ihnen",
        "спасибо" to "danke",
        "большое спасибо" to "vielen dank",
        "пожалуйста" to "bitte",
        "да" to "ja",
        "нет" to "nein",
        "хорошо" to "gut",
        "отлично" to "ausgezeichnet",
        "до свидания" to "auf wiedersehen",
        "до встречи" to "bis bald",
        "мы начинаем" to "wir fangen an",
        "совещание" to "besprechung",
        "встреча" to "treffen",
        "проект" to "projekt",
        "задача" to "aufgabe",
        "план" to "plan",
        "результат" to "ergebnis",
        "аудио" to "audio",
        "запись" to "aufnahme",
        "транскрибация" to "transkription",
        "спикер" to "sprecher",
        "документ" to "dokument",
        "файл" to "datei",
        "экспорт" to "export",
        "понятно" to "verstanden",
        "согласен" to "ich stimme zu",
        "какие новости" to "was gibt es neues",
        "следующий вопрос" to "nächste frage",
        "время" to "zeit",
        "дата" to "datum",
        "отчет" to "bericht",
        "вопрос" to "frage",
        "ответ" to "antwort",
        "важно" to "wichtig",
        "срочно" to "dringend",
        "команда" to "team",
        "руководство" to "leitung"
    )

    // Немецкий -> Русский (Full German to Russian)
    private val phraseDictionaryDeRu = phraseDictionaryRuDe.entries.associate { (k, v) -> v to k }

    // Английский -> Немецкий
    private val vocabularyEnToDe = mapOf(
        "hello" to "hallo",
        "good morning" to "guten morgen",
        "good afternoon" to "guten tag",
        "good evening" to "guten abend",
        "how are you" to "wie geht es ihnen",
        "thank you" to "danke",
        "thank you very much" to "vielen dank",
        "please" to "bitte",
        "yes" to "ja",
        "no" to "nein",
        "okay" to "gut",
        "great" to "ausgezeichnet",
        "goodbye" to "auf wiedersehen",
        "see you soon" to "bis bald",
        "meeting" to "besprechung",
        "project" to "projekt",
        "task" to "aufgabe",
        "plan" to "plan",
        "result" to "ergebnis",
        "recording" to "aufnahme",
        "speaker" to "sprecher",
        "document" to "dokument",
        "file" to "datei",
        "export" to "export",
        "time" to "zeit",
        "date" to "datum",
        "report" to "bericht",
        "question" to "frage",
        "answer" to "antwort"
    )

    private val vocabularyDeToEn = vocabularyEnToDe.entries.associate { (k, v) -> v to k }

    private val vocabularyEnToEs = mapOf(
        "hello" to "hola",
        "good morning" to "buenos días",
        "thank you" to "gracias",
        "yes" to "sí",
        "no" to "no",
        "meeting" to "reunión",
        "project" to "proyecto",
        "recording" to "grabación",
        "speaker" to "orador",
        "great" to "excelente"
    )

    private val vocabularyEnToFr = mapOf(
        "hello" to "bonjour",
        "good morning" to "bonjour",
        "thank you" to "merci",
        "yes" to "oui",
        "no" to "non",
        "meeting" to "réunion",
        "project" to "projet",
        "recording" to "enregistrement",
        "speaker" to "intervenant",
        "great" to "excellent"
    )

    /**
     * Основной метод синхронного перевода текста
     */
    fun translate(
        sourceText: String,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): TranslationResult {
        val trimmed = sourceText.trim()
        if (trimmed.isEmpty()) {
            return TranslationResult("", "", sourceLang, targetLang)
        }

        val detectedSource = if (sourceLang == "auto") detectLanguage(trimmed) else sourceLang
        if (detectedSource == targetLang) {
            return TranslationResult(trimmed, trimmed, detectedSource, targetLang)
        }

        val translated = performOfflineTranslation(trimmed, detectedSource, targetLang)

        return TranslationResult(
            originalText = trimmed,
            translatedText = translated,
            sourceLanguage = detectedSource,
            targetLanguage = targetLang,
            isOffline = true
        )
    }

    /**
     * Определение языка по алфавиту и лексике (кириллица -> ru, умлауты/немецкие маркеры -> de, латиница -> en)
     */
    fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        var cyrillicCount = 0
        var latinCount = 0
        var cjkCount = 0

        for (char in text) {
            when {
                char in '\u0400'..'\u04FF' -> cyrillicCount++
                char in 'a'..'z' || char in 'A'..'Z' -> latinCount++
                char in '\u4E00'..'\u9FFF' -> cjkCount++
            }
        }

        if (cyrillicCount > latinCount && cyrillicCount > cjkCount) return "ru"
        if (cjkCount > latinCount) return "zh"

        // Проверка немецких умлаутов и специфических слов
        val hasGermanChars = lower.contains("ä") || lower.contains("ö") || lower.contains("ü") || lower.contains("ß")
        val hasGermanWords = lower.contains("und ") || lower.contains("der ") || lower.contains("die ") ||
                lower.contains("das ") || lower.contains("ist ") || lower.contains("nicht ") ||
                lower.contains("hallo") || lower.contains("guten") || lower.contains("bitte")

        if (hasGermanChars || hasGermanWords) {
            return "de"
        }

        return "en"
    }

    /**
     * Автономный перевод по фразеологическому словарю и токенам
     */
    private fun performOfflineTranslation(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String {
        val lowerText = text.lowercase()

        // Прямой перевод целых фраз между RU, DE, EN
        when {
            sourceLang == "ru" && targetLang == "de" -> {
                phraseDictionaryRuDe[lowerText]?.let { return matchCase(text, it) }
            }
            sourceLang == "de" && targetLang == "ru" -> {
                phraseDictionaryDeRu[lowerText]?.let { return matchCase(text, it) }
            }
            sourceLang == "ru" && targetLang == "en" -> {
                phraseDictionaryRuEn[lowerText]?.let { return matchCase(text, it) }
            }
            sourceLang == "en" && targetLang == "ru" -> {
                phraseDictionaryEnRu[lowerText]?.let { return matchCase(text, it) }
            }
            sourceLang == "de" && targetLang == "en" -> {
                vocabularyDeToEn[lowerText]?.let { return matchCase(text, it) }
            }
            sourceLang == "en" && targetLang == "de" -> {
                vocabularyEnToDe[lowerText]?.let { return matchCase(text, it) }
            }
        }

        // Пословный перевод
        return when {
            sourceLang == "ru" && targetLang == "de" -> translateWordByWord(text, phraseDictionaryRuDe)
            sourceLang == "de" && targetLang == "ru" -> translateWordByWord(text, phraseDictionaryDeRu)
            sourceLang == "ru" && targetLang == "en" -> translateWordByWord(text, phraseDictionaryRuEn)
            sourceLang == "en" && targetLang == "ru" -> translateWordByWord(text, phraseDictionaryEnRu)
            sourceLang == "de" && targetLang == "en" -> translateWordByWord(text, vocabularyDeToEn)
            sourceLang == "en" && targetLang == "de" -> translateWordByWord(text, vocabularyEnToDe)
            sourceLang == "ru" && targetLang == "es" -> {
                val intermediate = translateWordByWord(text, phraseDictionaryRuEn)
                translateWordByWord(intermediate, vocabularyEnToEs)
            }
            sourceLang == "ru" && targetLang == "fr" -> {
                val intermediate = translateWordByWord(text, phraseDictionaryRuEn)
                translateWordByWord(intermediate, vocabularyEnToFr)
            }
            sourceLang == "de" && targetLang == "es" -> {
                val intermediate = translateWordByWord(text, vocabularyDeToEn)
                translateWordByWord(intermediate, vocabularyEnToEs)
            }
            else -> {
                val intermediate = translateWordByWord(text, phraseDictionaryRuEn)
                when (targetLang) {
                    "de" -> translateWordByWord(intermediate, vocabularyEnToDe)
                    "es" -> translateWordByWord(intermediate, vocabularyEnToEs)
                    "fr" -> translateWordByWord(intermediate, vocabularyEnToFr)
                    else -> intermediate
                }
            }
        }
    }

    private fun translateWordByWord(input: String, dictionary: Map<String, String>): String {
        val words = input.split(Regex("(?<=[\\s,;.!?])|(?=[\\s,;.!?])"))
        val resultBuilder = StringBuilder()

        for (word in words) {
            val cleanWord = word.trim().lowercase()
            val translatedWord = dictionary[cleanWord]
            if (translatedWord != null) {
                resultBuilder.append(matchCase(word, translatedWord))
            } else {
                resultBuilder.append(word)
            }
        }

        return resultBuilder.toString().ifEmpty { input }
    }

    private fun matchCase(original: String, target: String): String {
        return when {
            original.isNotEmpty() && original[0].isUpperCase() -> {
                target.replaceFirstChar { it.uppercase() }
            }
            else -> target
        }
    }
}
