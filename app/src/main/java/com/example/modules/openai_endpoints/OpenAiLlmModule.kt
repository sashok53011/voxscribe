package com.example.modules.openai_endpoints

import android.content.Context
import com.example.modules.ai_summary.AiSummaryModule.ActionItem
import com.example.modules.ai_summary.AiSummaryModule.TranscriptionAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * МОДУЛЬ: OpenAiLlmModule (Кастомный OpenAI-совместимый LLM эндпоинт для суммаризации)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Обращение к любому OpenAI-совместимому API (OpenAI gpt-4o, LocalAI, Ollama, LM Studio,
 * vLLM, DeepSeek, Mistral) через эндпоинт `POST {baseUrl}/chat/completions`.
 * Извлекает краткое резюме, список задач (Action Items), анализ тональности и ключевые темы.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - transcriptionText (String): Текст стенограммы для анализа.
 * - languageCode (String): Код языка вывода ("ru", "de", "en").
 * - overrideConfig (опционально): Возможность передать кастомный URL, ключ и модель.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - [TranscriptionAnalysisResult]: Структурированный объект с резюме, задачами, тональностью и темами.
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН (Stubs & Production):
 * Заглушек нет — отправляется реальный сетевой запрос через OkHttp с таймаутом 60 сек.
 * Если эндпоинт недоступен или API-ключ неверен, возвращается информативная ошибка.
 */
class OpenAiLlmModule(
    private val context: Context,
    private val config: OpenAiEndpointsConfigModule = OpenAiEndpointsConfigModule(context)
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Вызов суммаризации через OpenAI-совместимый эндпоинт
     */
    suspend fun generateSummary(
        transcriptionText: String,
        languageCode: String = "ru",
        baseUrlOverride: String? = null,
        apiKeyOverride: String? = null,
        modelOverride: String? = null
    ): Result<TranscriptionAnalysisResult> = withContext(Dispatchers.IO) {
        val baseUrl = (baseUrlOverride ?: config.getLlmBaseUrl()).trimEnd('/')
        val apiKey = apiKeyOverride ?: config.getLlmApiKey()
        val model = (modelOverride ?: config.getLlmModel()).ifBlank { "gpt-4o-mini" }

        val langPrompt = when (languageCode) {
            "de" -> "Antworte bitte ausschließlich auf Deutsch."
            "en" -> "Please answer strictly in English."
            else -> "Отвечай строго на русском языке."
        }

        val systemPrompt = """
            Ты — профессиональный секретарь и бизнес-аналитик.
            Проанализируй предоставленный текст стенограммы совещания или аудиозаписи.
            $langPrompt
            
            Верни СТРОГИЙ валидный JSON без markdown-разметки (без ```json), содержащий поля:
            {
              "executiveSummary": "Краткая суть встречи и главные договоренности (2-4 предложения)",
              "sentiment": "Позитивная" или "Конструктивная" или "Нейтральная" или "Напряженная",
              "sentimentScore": число от 0 до 100,
              "sentimentRationale": "Краткое обоснование тональности встречи (1 предложение)",
              "actionItems": [
                {
                  "task": "Конкретная задача",
                  "assignee": "Имя ответственного или Команда",
                  "priority": "Высокий" или "Средний" или "Низкий",
                  "deadline": "В ближайшее время"
                }
              ],
              "keyTopics": ["Тема 1", "Тема 2", "Тема 3"]
            }
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Текст стенограммы:\n\n$transcriptionText")
                })
            }
            put("messages", messages)
            put("temperature", 0.3)
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: $responseBody")
                )
            }

            val parsedJson = JSONObject(responseBody)
            val choices = parsedJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("Пустой ответ от модели (нет choices)"))
            }

            val messageObj = choices.getJSONObject(0).optJSONObject("message")
            val rawContent = messageObj?.optString("content") ?: ""

            // Очищаем от возможных markdown-тегов ```json ... ```
            val cleanedJson = rawContent
                .replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```$"), "")
                .trim()

            val summaryObj = JSONObject(cleanedJson)
            val executiveSummary = summaryObj.optString("executiveSummary", "Резюме сформировано.")
            val sentiment = summaryObj.optString("sentiment", "Конструктивная")
            val sentimentScore = summaryObj.optInt("sentimentScore", 70).coerceIn(0, 100)
            val sentimentRationale = summaryObj.optString("sentimentRationale", "Деловое обсуждение по существу.")

            val actionItems = mutableListOf<ActionItem>()
            val actionsArray = summaryObj.optJSONArray("actionItems")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actObj = actionsArray.optJSONObject(i)
                    if (actObj != null) {
                        actionItems.add(
                            ActionItem(
                                task = actObj.optString("task", "Задача"),
                                assignee = actObj.optString("assignee", "Команда"),
                                priority = actObj.optString("priority", "Средний"),
                                deadline = actObj.optString("deadline", "Скоро")
                            )
                        )
                    } else {
                        val taskStr = actionsArray.optString(i, "")
                        if (taskStr.isNotBlank()) {
                            actionItems.add(ActionItem(task = taskStr, assignee = "Команда", priority = "Средний", deadline = "Скоро"))
                        }
                    }
                }
            }

            val keyTopics = mutableListOf<String>()
            val topicsArray = summaryObj.optJSONArray("keyTopics")
            if (topicsArray != null) {
                for (i in 0 until topicsArray.length()) {
                    keyTopics.add(topicsArray.getString(i))
                }
            }

            Result.success(
                TranscriptionAnalysisResult(
                    executiveSummary = executiveSummary,
                    sentiment = sentiment,
                    sentimentScore = sentimentScore,
                    sentimentRationale = sentimentRationale,
                    actionItems = actionItems,
                    keyTopics = keyTopics,
                    isAiGenerated = true,
                    errorNotice = "Сгенерировано через OpenAI-совместимый LLM ($model)"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверка связи с эндпоинтом LLM
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val jsonBody = JSONObject().apply {
            put("model", model.ifBlank { "gpt-4o-mini" })
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Ping. Ответь одним словом 'OK'.")
                })
            }
            put("messages", messages)
            put("max_tokens", 10)
        }

        val requestBuilder = Request.Builder()
            .url("$cleanUrl/chat/completions")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val parsed = JSONObject(body)
                val reply = parsed.optJSONArray("choices")?.getJSONObject(0)?.optJSONObject("message")?.optString("content") ?: "Успешно"
                Result.success("Соединение успешно! Ответ модели: ${reply.trim()}")
            } else {
                Result.failure(Exception("Ошибка сервера HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось подключиться: ${e.message}"))
        }
    }
}
