package com.example.modules.ai_summary

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * =========================================================================================
 * МОДУЛЬ: AiSummaryModule (Интеллектуальный анализ стенограмм через Gemini API)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Извлечение ключевых задач (Action Items), анализа тональности/эмоций (Sentiment Analysis)
 * и кратких итогов (Executive Summary) из расшифрованных аудиозаписей с помощью модели Gemini 3.8 Flash
 * через Firebase AI Logic со строгой валидацией схемы JSON.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - transcriptText (String): Полный текст стенограммы с репликами спикеров.
 * - title (String): Заголовок записи.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - TranscriptionAnalysisResult:
 *   * executiveSummary (String): Краткое резюме диалога/встречи.
 *   * sentiment (String): Тональность ("Позитивная", "Конструктивная", "Нейтральная", "Напряженная").
 *   * sentimentScore (Int): Оценка тональности от 0 до 100%.
 *   * sentimentRationale (String): Обоснование эмоционального фона.
 *   * actionItems (List<ActionItem>): Задачи с исполнителем, приоритетом и дедлайном.
 *   * keyTopics (List<String>): Ключевые темы и принятые решения.
 *   * isAiGenerated (Boolean): Флаг облачной генерации через Gemini.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val aiSummary = AiSummaryModule()
 * val result = aiSummary.analyzeTranscription(title, fullText)
 * println("Итоги: ${result.executiveSummary}")
 * println("Тональность: ${result.sentiment} (${result.sentimentScore}%)")
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль использует официальный Firebase AI Logic SDK с моделью `gemini-3.8-flash` и жесткой JSON-схемой.
 * При отсутствии интернет-подключения или ошибке сети автоматически активируется встроенный
 * автономный лингвистический анализатор (Offline Rule-Based Heuristic), гарантирующий бесперебойную
 * работу приложения без падений.
 */
class AiSummaryModule {

    companion object {
        private const val TAG = "AiSummaryModule"
        private const val MODEL_NAME = "gemini-3.8-flash"
    }

    data class ActionItem(
        val id: String = java.util.UUID.randomUUID().toString(),
        val task: String,
        val assignee: String,
        val priority: String, // "Высокий", "Средний", "Низкий"
        val deadline: String,
        var isCompleted: Boolean = false
    )

    data class TranscriptionAnalysisResult(
        val executiveSummary: String,
        val sentiment: String,
        val sentimentScore: Int,
        val sentimentRationale: String,
        val actionItems: List<ActionItem>,
        val keyTopics: List<String>,
        val isAiGenerated: Boolean = true,
        val errorNotice: String? = null
    )

    // JSON-схема для строгой структурированной генерации через Gemini API
    private val summarySchema = Schema.obj(
        properties = mapOf(
            "executiveSummary" to Schema.string(description = "Краткое саммари встречи или аудиозаписи (3-5 предложений)"),
            "sentiment" to Schema.string(description = "Общая тональность разговора: Позитивная, Конструктивная, Нейтральная или Напряженная"),
            "sentimentScore" to Schema.integer(description = "Оценка позитивности и продуктивности в процентах от 0 до 100"),
            "sentimentRationale" to Schema.string(description = "Пояснение тональности, эмоций участников и динамики общения"),
            "keyTopics" to Schema.array(
                items = Schema.string(),
                description = "Список из 3-5 ключевых тем, идей или решений, затронутых в разговоре"
            ),
            "actionItems" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "task" to Schema.string(description = "Конкретная задача или договоренность"),
                        "assignee" to Schema.string(description = "Ответственный (например: Спикер 1, Спикер 2 или Все участники)"),
                        "priority" to Schema.string(description = "Приоритет задачи: Высокий, Средний или Низкий"),
                        "deadline" to Schema.string(description = "Срок исполнения, если упомянут в диалоге, иначе 'Не указан'")
                    )
                ),
                description = "Список задач к исполнению (Action Items), извлеченных из стенограммы"
            )
        )
    )

    private val modelConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = summarySchema
    }

    // Ленивая инициализация модели Firebase AI Logic
    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = modelConfig
        )
    }

    /**
     * Основной метод извлечения саммари, задач и тональности с таймаутом 60с
     */
    suspend fun analyzeTranscription(title: String, transcriptText: String): TranscriptionAnalysisResult {
        if (transcriptText.isBlank()) {
            return TranscriptionAnalysisResult(
                executiveSummary = "Стенограмма пуста. Нет данных для анализа.",
                sentiment = "Нейтральная",
                sentimentScore = 50,
                sentimentRationale = "Отсутствуют реплики для оценки настроения.",
                actionItems = emptyList(),
                keyTopics = emptyList(),
                isAiGenerated = false
            )
        }

        val prompt = """
            Ты — эксперт по анализу деловых переговоров, встреч и аудиостенограмм.
            Проанализируй следующую стенограмму записи '$title'.
            
            Извлеки:
            1. Краткое резюме (Executive Summary) сути разговора.
            2. Анализ тональности (Sentiment): определи общее настроение, оценку продуктивности (0-100%) и краткое объяснение.
            3. Ключевые темы и решения (Key Topics).
            4. Конкретные задачи и договоренности (Action Items) с указанием ответственного лица, приоритета и срока.
            
            Стенограмма:
            $transcriptText
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                generativeModel.generateContent(prompt)
            }

            val jsonString = response.text
            if (!jsonString.isNullOrBlank()) {
                parseStructuredJson(jsonString)
            } else {
                Log.w(TAG, "Gemini вернул пустой ответ, используем локальный эвристический анализ.")
                performOfflineHeuristicAnalysis(title, transcriptText, "Облачный сервис вернул пустой ответ.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Таймаут обращения к Gemini (60с)", e)
            performOfflineHeuristicAnalysis(title, transcriptText, "Время ожидания ответа Gemini истекло (60с). Применен локальный анализ.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка генерации через Firebase AI Logic: ${e.message}", e)
            performOfflineHeuristicAnalysis(title, transcriptText, "Gemini временно недоступен (${e.localizedMessage ?: "Сетевая ошибка"}). Применен локальный анализ.")
        }
    }

    private fun parseStructuredJson(jsonString: String): TranscriptionAnalysisResult {
        val json = JSONObject(jsonString)

        val summary = json.optString("executiveSummary", "Резюме сформировано.")
        val sentiment = json.optString("sentiment", "Конструктивная")
        val sentimentScore = json.optInt("sentimentScore", 75).coerceIn(0, 100)
        val rationale = json.optString("sentimentRationale", "Спокойное деловое обсуждение вопросов.")

        val topicsList = mutableListOf<String>()
        val topicsArray = json.optJSONArray("keyTopics")
        if (topicsArray != null) {
            for (i in 0 until topicsArray.length()) {
                topicsList.add(topicsArray.getString(i))
            }
        }

        val actionItemsList = mutableListOf<ActionItem>()
        val actionsArray = json.optJSONArray("actionItems")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val itemObj = actionsArray.getJSONObject(i)
                actionItemsList.add(
                    ActionItem(
                        task = itemObj.optString("task", "Задача"),
                        assignee = itemObj.optString("assignee", "Команда"),
                        priority = itemObj.optString("priority", "Средний"),
                        deadline = itemObj.optString("deadline", "Не указан")
                    )
                )
            }
        }

        return TranscriptionAnalysisResult(
            executiveSummary = summary,
            sentiment = sentiment,
            sentimentScore = sentimentScore,
            sentimentRationale = rationale,
            actionItems = actionItemsList,
            keyTopics = topicsList,
            isAiGenerated = true
        )
    }

    /**
     * Автономный оффлайн-анализатор (фоллбек при отсутствии сети или таймауте)
     */
    fun performOfflineHeuristicAnalysis(
        title: String,
        transcriptText: String,
        notice: String? = null
    ): TranscriptionAnalysisResult {
        val lower = transcriptText.lowercase()

        // Оценка тональности по ключевым словам
        var positivePoints = 0
        var negativePoints = 0

        val positiveWords = listOf("отлично", "хорошо", "согласен", "успешно", "принято", "замечательно", "спасибо", "решено", "готово", "great", "good", "agree")
        val negativeWords = listOf("проблема", "ошибка", "сложно", "задержка", "не согласен", "отклонено", "риск", "провал", "issue", "problem", "error")

        for (w in positiveWords) {
            if (lower.contains(w)) positivePoints++
        }
        for (w in negativeWords) {
            if (lower.contains(w)) negativePoints++
        }

        val (sentiment, score, rationale) = when {
            positivePoints > negativePoints -> Triple(
                "Позитивная",
                (70 + positivePoints * 5).coerceAtMost(95),
                "В диалоге преобладают утвердительные согласия, позитивные маркеры и конструктивный настрой."
            )
            negativePoints > positivePoints -> Triple(
                "Напряженная",
                (45 - negativePoints * 5).coerceAtLeast(15),
                "В разговоре зафиксированы спорные моменты, упоминания трудностей или несогласий."
            )
            else -> Triple(
                "Конструктивная",
                70,
                "Деловой, уравновешенный диалог, ориентированный на обсуждение текущих вопросов."
            )
        }

        // Извлечение задач по ключевым речевым конструкциям
        val actionItems = mutableListOf<ActionItem>()
        val lines = transcriptText.split("\n", ".")
        for (line in lines) {
            val clean = line.trim()
            if (clean.length > 10) {
                val lowerLine = clean.lowercase()
                val isAction = lowerLine.contains("нужно") ||
                        lowerLine.contains("сделать") ||
                        lowerLine.contains("подготовить") ||
                        lowerLine.contains("проверить") ||
                        lowerLine.contains("отправить") ||
                        lowerLine.contains("договорились") ||
                        lowerLine.contains("must") ||
                        lowerLine.contains("will")

                if (isAction && actionItems.size < 4) {
                    val priority = if (lowerLine.contains("срочно") || lowerLine.contains("важно")) "Высокий" else "Средний"
                    val speaker = if (clean.startsWith("•") || clean.contains(":")) {
                        clean.substringBefore(":").replace("•", "").trim()
                    } else "Команда"

                    actionItems.add(
                        ActionItem(
                            task = clean.substringAfter(":").ifBlank { clean }.trim(),
                            assignee = speaker.ifBlank { "Ответственный" },
                            priority = priority,
                            deadline = if (lowerLine.contains("завтра")) "Завтра" else if (lowerLine.contains("недел")) "До конца недели" else "В ближайшее время"
                        )
                    )
                }
            }
        }

        if (actionItems.isEmpty()) {
            actionItems.add(
                ActionItem(
                    task = "Ознакомиться с деталями стенограммы и утвердить план",
                    assignee = "Все участники",
                    priority = "Средний",
                    deadline = "В ближайшее время"
                )
            )
        }

        val topics = listOf(
            "Обсуждение записи '$title'",
            "Координация совместных действий",
            "Фиксация договоренностей и сроков"
        )

        return TranscriptionAnalysisResult(
            executiveSummary = "В рамках записи '$title' участники провели структурированное обсуждение ключевых аспектов, выделили основные приоритеты и наметили шаги дальнейшей реализации.",
            sentiment = sentiment,
            sentimentScore = score,
            sentimentRationale = rationale,
            actionItems = actionItems,
            keyTopics = topics,
            isAiGenerated = false,
            errorNotice = notice
        )
    }
}
