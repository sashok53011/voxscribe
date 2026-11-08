package com.example.modules.openai_endpoints

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * МОДУЛЬ: OpenAiModelsFetcherModule (Получение списка доступных моделей через GET /v1/models)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Динамический опрос эндпоинта `GET {baseUrl}/models` любого OpenAI-совместимого провайдера
 * (OpenAI, Groq, Ollama, vLLM, LM Studio, OpenRouter, Together AI, Mistral).
 * Позволяет пользователю в настройках нажать кнопку "Загрузить список моделей" (Fetch model list)
 * и выбрать нужную модель из выпадающего списка, вместо ручного ввода названия.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - baseUrl (String): Базовый адрес API (например "https://api.openai.com/v1" или "http://192.168.1.5:11434/v1").
 * - apiKey (String): Токен авторизации (Bearer-токен).
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Result<List<String>>: Список уникальных идентификаторов моделей (например: ["gpt-4o", "whisper-1", ...]).
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val fetcher = OpenAiModelsFetcherModule()
 * val result = fetcher.fetchModelsList(baseUrl = "https://api.openai.com/v1", apiKey = "sk-...")
 * result.onSuccess { models ->
 *     println("Доступные модели: $models")
 * }
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль выполняет прямой HTTP GET запрос к стандартному маршруту `/models`.
 * Если провайдер не поддерживает эндпоинт `/models` (например, некоторые приватные прокси),
 * возвращается внятная ошибка и список популярных дефолтных моделей в качестве подсказки.
 */
class OpenAiModelsFetcherModule {

    companion object {
        private const val TAG = "OpenAiModelsFetcher"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Запрос списка всех моделей
     */
    suspend fun fetchModelsList(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Базовый URL не указан"))
        }

        val targetUrl = if (cleanUrl.endsWith("/models")) cleanUrl else "$cleanUrl/models"
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                Log.e(TAG, "Ошибка загрузки списка моделей: $errorMsg")
                return@withContext Result.failure(Exception("Сервер вернул ошибку [$errorMsg]"))
            }

            val root = JSONObject(responseBody)
            val dataArray = root.optJSONArray("data") ?: root.optJSONArray("models")

            val modelIds = mutableListOf<String>()
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.opt(i)
                    if (item is JSONObject) {
                        val id = item.optString("id", item.optString("name", ""))
                        if (id.isNotBlank()) modelIds.add(id)
                    } else if (item is String && item.isNotBlank()) {
                        modelIds.add(item)
                    }
                }
            }

            if (modelIds.isEmpty()) {
                return@withContext Result.failure(Exception("Список моделей пуст на данном сервере"))
            }

            val sorted = modelIds.distinct().sorted()
            Log.d(TAG, "Успешно получено ${sorted.size} моделей от $cleanUrl")
            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при получении моделей: ${e.message}", e)
            Result.failure(Exception("Не удалось связаться с сервером: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Фильтрация моделей по типу назначения
     */
    fun filterLlmModels(allModels: List<String>): List<String> {
        val nonLlmKeywords = listOf("whisper", "tts", "dall-e", "embedding", "moderation", "davinci-instruct")
        val filtered = allModels.filter { model ->
            nonLlmKeywords.none { kw -> model.contains(kw, ignoreCase = true) }
        }
        return if (filtered.isNotEmpty()) filtered else allModels
    }

    fun filterTtsModels(allModels: List<String>): List<String> {
        val filtered = allModels.filter { it.contains("tts", ignoreCase = true) }
        return if (filtered.isNotEmpty()) filtered else listOf("tts-1", "tts-1-hd")
    }

    fun filterSttModels(allModels: List<String>): List<String> {
        val filtered = allModels.filter { it.contains("whisper", ignoreCase = true) }
        return if (filtered.isNotEmpty()) filtered else listOf("whisper-1", "whisper-large-v3", "whisper-large-v3-turbo")
    }
}
