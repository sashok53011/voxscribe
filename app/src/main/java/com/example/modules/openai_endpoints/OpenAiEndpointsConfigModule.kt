package com.example.modules.openai_endpoints

import android.content.Context
import android.content.SharedPreferences

/**
 * =========================================================================================
 * МОДУЛЬ: OpenAiEndpointsConfigModule (Управление конфигурацией OpenAI-совместимых эндпоинтов)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Хранение и управление настройками кастомных OpenAI-совместимых эндпоинтов для трех сервисов:
 * 1. LLM (Языковая модель для суммаризации, анализа тональности и извлечения задач).
 * 2. TTS (Синтез речи Text-to-Speech через endpoint /v1/audio/speech).
 * 3. STT (Распознавание речи Speech-to-Text через endpoint /v1/audio/transcriptions).
 * 
 * Совместимо с OpenAI API, LocalAI, Ollama, LM Studio, vLLM, Groq, OpenRouter и любыми
 * локальными серверами, поддерживающими спецификацию OpenAI REST API.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст для доступа к постоянному хранилищу SharedPreferences.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Настройки URL, API-ключей, моделей и переключателей активности сервисов.
 */
class OpenAiEndpointsConfigModule(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "openai_compatible_endpoints_prefs"

        // Ключи настроек LLM
        private const val KEY_LLM_BASE_URL = "openai_llm_base_url"
        private const val KEY_LLM_API_KEY = "openai_llm_api_key"
        private const val KEY_LLM_MODEL = "openai_llm_model"
        private const val KEY_USE_OPENAI_LLM = "use_openai_llm_instead_of_gemini"

        // Ключи настроек TTS
        private const val KEY_TTS_BASE_URL = "openai_tts_base_url"
        private const val KEY_TTS_API_KEY = "openai_tts_api_key"
        private const val KEY_TTS_MODEL = "openai_tts_model"
        private const val KEY_TTS_VOICE = "openai_tts_voice"
        private const val KEY_USE_OPENAI_TTS = "use_openai_tts_instead_of_android"

        // Ключи настроек STT
        private const val KEY_STT_BASE_URL = "openai_stt_base_url"
        private const val KEY_STT_API_KEY = "openai_stt_api_key"
        private const val KEY_STT_MODEL = "openai_stt_model"
        private const val KEY_STT_PROVIDER_MODE = "stt_active_provider_mode"

        // Значения по умолчанию
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"
        const val DEFAULT_TTS_MODEL = "tts-1"
        const val DEFAULT_TTS_VOICE = "alloy"
        const val DEFAULT_STT_MODEL = "whisper-1"
    }

    // --- LLM Настройки ---
    fun getLlmBaseUrl(): String = prefs.getString(KEY_LLM_BASE_URL, DEFAULT_OPENAI_BASE_URL) ?: DEFAULT_OPENAI_BASE_URL
    fun setLlmBaseUrl(url: String) = prefs.edit().putString(KEY_LLM_BASE_URL, url.trim().trimEnd('/')).apply()

    fun getLlmApiKey(): String = prefs.getString(KEY_LLM_API_KEY, "") ?: ""
    fun setLlmApiKey(key: String) = prefs.edit().putString(KEY_LLM_API_KEY, key.trim()).apply()

    fun getLlmModel(): String = prefs.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
    fun setLlmModel(model: String) = prefs.edit().putString(KEY_LLM_MODEL, model.trim()).apply()

    fun isUseOpenAiLlm(): Boolean = prefs.getBoolean(KEY_USE_OPENAI_LLM, false)
    fun setUseOpenAiLlm(enabled: Boolean) = prefs.edit().putBoolean(KEY_USE_OPENAI_LLM, enabled).apply()

    // --- TTS Настройки ---
    fun getTtsBaseUrl(): String = prefs.getString(KEY_TTS_BASE_URL, DEFAULT_OPENAI_BASE_URL) ?: DEFAULT_OPENAI_BASE_URL
    fun setTtsBaseUrl(url: String) = prefs.edit().putString(KEY_TTS_BASE_URL, url.trim().trimEnd('/')).apply()

    fun getTtsApiKey(): String = prefs.getString(KEY_TTS_API_KEY, "") ?: ""
    fun setTtsApiKey(key: String) = prefs.edit().putString(KEY_TTS_API_KEY, key.trim()).apply()

    fun getTtsModel(): String = prefs.getString(KEY_TTS_MODEL, DEFAULT_TTS_MODEL) ?: DEFAULT_TTS_MODEL
    fun setTtsModel(model: String) = prefs.edit().putString(KEY_TTS_MODEL, model.trim()).apply()

    fun getTtsVoice(): String = prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE
    fun setTtsVoice(voice: String) = prefs.edit().putString(KEY_TTS_VOICE, voice.trim()).apply()

    fun isUseOpenAiTts(): Boolean = prefs.getBoolean(KEY_USE_OPENAI_TTS, false)
    fun setUseOpenAiTts(enabled: Boolean) = prefs.edit().putBoolean(KEY_USE_OPENAI_TTS, enabled).apply()

    // --- STT Настройки ---
    fun getSttBaseUrl(): String = prefs.getString(KEY_STT_BASE_URL, DEFAULT_OPENAI_BASE_URL) ?: DEFAULT_OPENAI_BASE_URL
    fun setSttBaseUrl(url: String) = prefs.edit().putString(KEY_STT_BASE_URL, url.trim().trimEnd('/')).apply()

    fun getSttApiKey(): String = prefs.getString(KEY_STT_API_KEY, "") ?: ""
    fun setSttApiKey(key: String) = prefs.edit().putString(KEY_STT_API_KEY, key.trim()).apply()

    fun getSttModel(): String = prefs.getString(KEY_STT_MODEL, DEFAULT_STT_MODEL) ?: DEFAULT_STT_MODEL
    fun setSttModel(model: String) = prefs.edit().putString(KEY_STT_MODEL, model.trim()).apply()

    fun getSttProviderMode(): String {
        val savedMode = prefs.getString(KEY_STT_PROVIDER_MODE, "GROQ") ?: "GROQ"
        return if (savedMode == "OPENAI") "OPENAI" else "GROQ"
    }

    fun setSttProviderMode(mode: String) {
        require(mode == "GROQ" || mode == "OPENAI") { "Unsupported STT provider: $mode" }
        prefs.edit().putString(KEY_STT_PROVIDER_MODE, mode).apply()
    }
}
