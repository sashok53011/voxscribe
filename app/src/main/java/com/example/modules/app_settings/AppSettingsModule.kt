package com.example.modules.app_settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Модуль сохранения пользовательских настроек приложения (язык интерфейса,
 * язык распознавания речи и язык перевода). Использует SharedPreferences.
 */
class AppSettingsModule(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "voxscribe_app_settings"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_SPEECH_INPUT_LANGUAGE = "speech_input_language"
        private const val KEY_TARGET_LANGUAGE_CODE = "target_language_code"
        private const val KEY_THEME = "app_theme"
        private const val KEY_NOISE_GATE_THRESHOLD = "noise_gate_threshold"
        private const val KEY_VAD_MIN_PHRASE_MS = "vad_min_phrase_ms"
    }

    fun getTheme(default: String = "DARK_SPACE"): String =
        prefs.getString(KEY_THEME, default) ?: default

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getNoiseGateThreshold(default: Float = 0.065f): Float =
        prefs.getFloat(KEY_NOISE_GATE_THRESHOLD, default)

    fun setNoiseGateThreshold(value: Float) {
        prefs.edit().putFloat(KEY_NOISE_GATE_THRESHOLD, value).apply()
    }

    fun getVadMinPhraseMs(default: Int = 1000): Int =
        prefs.getInt(KEY_VAD_MIN_PHRASE_MS, default)

    fun setVadMinPhraseMs(value: Int) {
        prefs.edit().putInt(KEY_VAD_MIN_PHRASE_MS, value).apply()
    }

    fun getAppLanguage(default: String = "ru"): String =
        prefs.getString(KEY_APP_LANGUAGE, default) ?: default

    fun setAppLanguage(code: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, code).apply()
    }

    fun getSpeechInputLanguage(default: String = "ru"): String =
        prefs.getString(KEY_SPEECH_INPUT_LANGUAGE, default) ?: default

    fun setSpeechInputLanguage(code: String) {
        prefs.edit().putString(KEY_SPEECH_INPUT_LANGUAGE, code).apply()
    }

    fun getTargetLanguageCode(default: String = "de"): String =
        prefs.getString(KEY_TARGET_LANGUAGE_CODE, default) ?: default

    fun setTargetLanguageCode(code: String) {
        prefs.edit().putString(KEY_TARGET_LANGUAGE_CODE, code).apply()
    }
}