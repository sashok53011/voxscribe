package com.example.modules.translation

import android.content.Context
import android.util.Log
import com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule
import com.example.modules.stt_groq.GroqSttProviderModule
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
 * МОДУЛЬ: LlmRealtimeTranslatorModule (Синхронный перевод на лету с помощью нейросетей LLM)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Высокоточный, контекстный синхронный перевод речи «на лету» с использованием больших
 * языковых моделей (LLM). Поддерживает ультрабыстрый перевод через Groq LLM (Llama 3.3 70B / 8B),
 * кастомные OpenAI-совместимые серверы (Ollama, vLLM, LocalAI, GPT-4o-mini) и надежный
 * оффлайн-фоллбек без интернета.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - text (String): Распознанная речевая реплика.
 * - sourceLang (String): Язык оригинала ("ru", "de", "en", "es", "fr" или "auto").
 * - targetLang (String): Целевой язык перевода ("de", "ru", "en", "es", "fr", "zh", "it").
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - LlmTranslationResult:
 *   * originalText (String): Исходный текст.
 *   * translatedText (String): Качественный перевод от LLM без лишних пояснений.
 *   * providerName (String): "Groq LPU (Llama 3.3)", "OpenAI LLM", "Offline Dictionary".
 *   * latencyMs (Long): Задержка обработки в миллисекундах.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val llmTranslator = LlmRealtimeTranslatorModule(context)
 * val result = llmTranslator.translateOnTheFly("Мы начинаем совещание", "ru", "de")
 * println("Перевод: ${result.translatedText}")
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль отправляет прямой HTTP запрос с жесткой инструкцией системного промпта:
 * «Ты — синхронный переводчик. Переведи реплику на целевой язык. Выведи ТОЛЬКО чистый перевод
 * без кавычек, вводных слов и пояснений».
 * При отсутствии ключей или отсутствии сети бесшовно переключается на встроенный оффлайн-словарь.
 */
class LlmRealtimeTranslatorModule(
    private val context: Context,
    private val openAiConfig: OpenAiEndpointsConfigModule = OpenAiEndpointsConfigModule(context),
    private val groqModule: GroqSttProviderModule = GroqSttProviderModule(context),
    private val offlineTranslator: RealtimeTranslatorModule = RealtimeTranslatorModule()
) {

    companion object {
        private const val TAG = "LlmRealtimeTranslator"
        private const val GROQ_CHAT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val DEFAULT_GROQ_LLM_MODEL = "llama-3.3-70b-versatile"
    }

    data class LlmTranslationResult(
        val originalText: String,
        val translatedText: String,
        val providerName: String,
        val latencyMs: Long,
        val isLlmPowered: Boolean = true
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Быстрый перевод реплики на лету через доступный LLM с переключением на оффлайн
     */
    suspend fun translateOnTheFly(
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "de"
    ): LlmTranslationResult = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext LlmTranslationResult(text, "", "Empty", 0L, false)
        }

        val startTime = System.currentTimeMillis()
        val targetLanguageName = getLanguageName(targetLang)

        // 1. Попытка перевода через Groq LLM (сверхбыстрый LPU, задержка < 250мс)
        val groqApiKey = groqModule.getGroqApiKey()
        if (groqApiKey.isNotBlank()) {
            try {
                val groqTranslation = callChatCompletion(
                    endpoint = GROQ_CHAT_ENDPOINT,
                    apiKey = groqApiKey,
                    model = DEFAULT_GROQ_LLM_MODEL,
                    textToTranslate = cleanText,
                    targetLanguageName = targetLanguageName
                )
                if (groqTranslation.isNotBlank()) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Groq LLM перевод завершен за ${duration}мс: $groqTranslation")
                    return@withContext LlmTranslationResult(
                        originalText = cleanText,
                        translatedText = groqTranslation,
                        providerName = "Groq LPU ($DEFAULT_GROQ_LLM_MODEL)",
                        latencyMs = duration,
                        isLlmPowered = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка перевода через Groq LLM: ${e.message}, пробуем следующий вариант")
            }
        }

        // 2. Попытка перевода через кастомный OpenAI-совместимый LLM эндпоинт (если включен)
        if (openAiConfig.isUseOpenAiLlm()) {
            val openAiUrl = openAiConfig.getLlmBaseUrl().trimEnd('/') + "/chat/completions"
            val openAiKey = openAiConfig.getLlmApiKey()
            val openAiModel = openAiConfig.getLlmModel().ifBlank { "gpt-4o-mini" }
            try {
                val openAiTranslation = callChatCompletion(
                    endpoint = openAiUrl,
                    apiKey = openAiKey,
                    model = openAiModel,
                    textToTranslate = cleanText,
                    targetLanguageName = targetLanguageName
                )
                if (openAiTranslation.isNotBlank()) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "OpenAI LLM перевод завершен за ${duration}мс: $openAiTranslation")
                    return@withContext LlmTranslationResult(
                        originalText = cleanText,
                        translatedText = openAiTranslation,
                        providerName = "OpenAI ($openAiModel)",
                        latencyMs = duration,
                        isLlmPowered = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка перевода через OpenAI LLM: ${e.message}")
            }
        }

        // 3. Надежный автономный оффлайн-перевод (словарь и эвристики)
        val offlineRes = offlineTranslator.translate(cleanText, sourceLang, targetLang)
        val duration = System.currentTimeMillis() - startTime
        return@withContext LlmTranslationResult(
            originalText = cleanText,
            translatedText = offlineRes.translatedText,
            providerName = "Оффлайн-словарь",
            latencyMs = duration,
            isLlmPowered = false
        )
    }

    suspend fun translateOnTheFlyStreaming(
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "de",
        onTranslationChunk: (String) -> Unit
    ): LlmTranslationResult = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext LlmTranslationResult(text, "", "Empty", 0L, false)
        }

        val startTime = System.currentTimeMillis()
        val targetLanguageName = getLanguageName(targetLang)
        val groqApiKey = groqModule.getGroqApiKey()

        if (groqApiKey.isNotBlank()) {
            try {
                val translation = callChatCompletionStream(
                    endpoint = GROQ_CHAT_ENDPOINT,
                    apiKey = groqApiKey,
                    model = DEFAULT_GROQ_LLM_MODEL,
                    textToTranslate = cleanText,
                    targetLanguageName = targetLanguageName,
                    onTranslationChunk = onTranslationChunk
                )
                if (translation.isNotBlank()) {
                    return@withContext LlmTranslationResult(
                        originalText = cleanText,
                        translatedText = translation,
                        providerName = "Groq LPU ($DEFAULT_GROQ_LLM_MODEL)",
                        latencyMs = System.currentTimeMillis() - startTime,
                        isLlmPowered = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка потокового перевода через Groq: ${e.message}")
            }
        }

        if (openAiConfig.isUseOpenAiLlm()) {
            val openAiKey = openAiConfig.getLlmApiKey()
            try {
                val translation = callChatCompletionStream(
                    endpoint = openAiConfig.getLlmBaseUrl().trimEnd('/') + "/chat/completions",
                    apiKey = openAiKey,
                    model = openAiConfig.getLlmModel().ifBlank { "gpt-4o-mini" },
                    textToTranslate = cleanText,
                    targetLanguageName = targetLanguageName,
                    onTranslationChunk = onTranslationChunk
                )
                if (translation.isNotBlank()) {
                    return@withContext LlmTranslationResult(
                        originalText = cleanText,
                        translatedText = translation,
                        providerName = "OpenAI (${openAiConfig.getLlmModel()})",
                        latencyMs = System.currentTimeMillis() - startTime,
                        isLlmPowered = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка потокового перевода через OpenAI: ${e.message}")
            }
        }

        val offlineResult = offlineTranslator.translate(cleanText, sourceLang, targetLang)
        if (offlineResult.translatedText.isNotBlank()) {
            onTranslationChunk(offlineResult.translatedText)
        }
        LlmTranslationResult(
            originalText = cleanText,
            translatedText = offlineResult.translatedText,
            providerName = "Оффлайн-словарь",
            latencyMs = System.currentTimeMillis() - startTime,
            isLlmPowered = false
        )
    }

    private fun callChatCompletionStream(
        endpoint: String,
        apiKey: String,
        model: String,
        textToTranslate: String,
        targetLanguageName: String,
        onTranslationChunk: (String) -> Unit
    ): String {
        val systemPrompt = "You are a live speech simultaneous translator. Translate the user's spoken phrase into $targetLanguageName accurately, keeping natural conversational tone. Output ONLY the direct translation text. Do not add quotes, markdown, greetings or explanations."
        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", textToTranslate)
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 256)
            put("stream", true)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }

            val fullTranslation = StringBuilder()
            val pendingChunk = StringBuilder()
            val reader = response.body?.charStream()?.buffered()
                ?: throw Exception("Пустой поток ответа LLM")

            reader.useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.substringAfter("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") return@forEach

                    val delta = JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "")
                        .orEmpty()
                    if (delta.isEmpty()) return@forEach

                    fullTranslation.append(delta)
                    pendingChunk.append(delta)
                    emitReadyTranslationChunks(pendingChunk, onTranslationChunk)
                }
            }

            pendingChunk.toString().trim().takeIf { it.isNotEmpty() }?.let(onTranslationChunk)
            return fullTranslation.toString().trim()
        }
    }

    private fun emitReadyTranslationChunks(
        pendingChunk: StringBuilder,
        onTranslationChunk: (String) -> Unit
    ) {
        while (pendingChunk.isNotEmpty()) {
            val content = pendingChunk.toString()
            val sentenceEnd = content.indexOfFirst { it in ".!?;:" }
            val wordEnd = content.indexOfLast { it.isWhitespace() }
            val boundary = when {
                sentenceEnd >= 0 -> sentenceEnd + 1
                Regex("\\S+").findAll(content).count() >= 4 -> wordEnd
                else -> -1
            }
            if (boundary < 0) return

            val chunk = content.substring(0, boundary).trim()
            pendingChunk.delete(0, boundary)
            if (chunk.isNotEmpty()) onTranslationChunk(chunk)
        }
    }

    /**
     * Вызов эндпоинта chat/completions с компактным и быстрым промптом
     */
    private fun callChatCompletion(
        endpoint: String,
        apiKey: String,
        model: String,
        textToTranslate: String,
        targetLanguageName: String
    ): String {
        val systemPrompt = "You are a live speech simultaneous translator. Translate the user's spoken phrase into $targetLanguageName accurately, keeping natural conversational tone. Output ONLY the direct translation text. Do not add quotes, markdown, greetings or explanations."

        val json = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", textToTranslate)
                })
            }
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 256)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: $body")
        }

        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: throw Exception("Нет choices в ответе")
        val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content") ?: ""

        return content
            .replace(Regex("^[\"']"), "")
            .replace(Regex("[\"']$"), "")
            .trim()
    }

    private fun getLanguageName(code: String): String {
        return when (code.lowercase()) {
            "ru" -> "Russian"
            "de" -> "German"
            "en" -> "English"
            "es" -> "Spanish"
            "fr" -> "French"
            "zh" -> "Chinese"
            "it" -> "Italian"
            "ja" -> "Japanese"
            else -> "English"
        }
    }
}
