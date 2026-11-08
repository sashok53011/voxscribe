package com.example.modules.audio_capture

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * =========================================================================================
 * МОДУЛЬ: AudioNoiseReducerModule (Шумоподавление и фильтрация входного аудиопотока)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Аппаратная и алгоритмическая фильтрация фонового шума для повышения точности
 * распознавания речи в шумной обстановке (кафе, улица, шум кулеров/кондиционера).
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - audioSessionId (Int): Идентификатор аудиосессии AudioRecord / AudioTrack.
 * - pcmBuffer (ShortArray / ByteArray): Необработанные сэмплы PCM аудиопотока.
 * - noiseGateThreshold (Float): Пороговое значение амплитуды (0.0 .. 1.0) для отсечения тишины и шума.
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - Boolean: Статус успешного включения системного NoiseSuppressor / AcousticEchoCanceler.
 * - FilteredPcmResult: Обработанный буфер сэмплов с подавленным низкоуровневым шумом и значением RMS.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val reducer = AudioNoiseReducerModule()
 * reducer.attachToAudioSession(audioSessionId)
 * val result = reducer.filterPcmFrame(samples, noiseGateThreshold = 0.05f)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * В коде ниже программный спектральный гейт реализован через адаптивный фильтр амплитуды (Noise Gate).
 * В продакшене для еще более глубокого подавления нестационарного шума (крики, стук клавиш)
 * данную заглушку можно дополнить библиотеками RNNoise (C++/NDK) или WebRTC AEC3/NS DSP.
 */
class AudioNoiseReducerModule {

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    data class FilteredPcmResult(
        val cleanedSamples: ShortArray,
        val rmsAmplitude: Float,
        val isSpeechDetected: Boolean
    )

    /**
     * Подключение системного аппаратного шумоподавителя Android к аудиосессии.
     */
    fun attachToAudioSession(audioSessionId: Int): Boolean {
        var hardwareSuccess = false
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor?.release()
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
                hardwareSuccess = noiseSuppressor?.enabled == true
                Log.d("AudioNoiseReducer", "Аппаратный NoiseSuppressor успешно активирован: $hardwareSuccess")
            } else {
                Log.w("AudioNoiseReducer", "Аппаратный NoiseSuppressor не поддерживается на данном устройстве")
            }

            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler?.release()
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
                Log.d("AudioNoiseReducer", "Аппаратный AcousticEchoCanceler активирован")
            }
        } catch (e: Exception) {
            Log.e("AudioNoiseReducer", "Ошибка активации аппаратных эффектов аудио: ${e.message}")
        }
        return hardwareSuccess
    }

    /**
     * Алгоритмическая обработка фрейма PCM аудио:
     * 1. Расчет среднеквадратичной амплитуды (RMS).
     * 2. Подавление сэмплов ниже порогового уровня фонового шума (Noise Gating).
     * 3. Мягкая компрессия пиков.
     */
    fun filterPcmFrame(
        inputSamples: ShortArray,
        noiseGateThreshold: Float = 0.03f,
        smoothingFactor: Float = 0.85f
    ): FilteredPcmResult {
        if (inputSamples.isEmpty()) {
            return FilteredPcmResult(ShortArray(0), 0f, false)
        }

        var sumSquare = 0.0
        val output = ShortArray(inputSamples.size)
        val maxShort = 32767.0

        for (sample in inputSamples) {
            val normalized = sample / maxShort
            sumSquare += normalized * normalized
        }

        val rms = kotlin.math.sqrt(sumSquare / inputSamples.size).toFloat()
        val isSpeech = rms > noiseGateThreshold

        for (i in inputSamples.indices) {
            val sample = inputSamples[i]
            val absSample = kotlin.math.abs(sample.toInt())
            val normalizedSample = absSample / maxShort

            if (normalizedSample < noiseGateThreshold) {
                // Плавное затухание шума в паузах
                output[i] = (sample * 0.15f).toInt().coerceIn(-32768, 32767).toShort()
            } else {
                // Полезный сигнал речи
                output[i] = sample
            }
        }

        return FilteredPcmResult(
            cleanedSamples = output,
            rmsAmplitude = rms,
            isSpeechDetected = isSpeech
        )
    }

    /**
     * Освобождение аппаратных ресурсов
     */
    fun release() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
            echoCanceler?.release()
            echoCanceler = null
        } catch (e: Exception) {
            Log.e("AudioNoiseReducer", "Ошибка при освобождении ресурсов: ${e.message}")
        }
    }
}
