package com.example.modules.audio_capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * =========================================================================================
 * МОДУЛЬ: SilentAudioRecordModule (Бесшумный микрофонный захват через нативный AudioRecord)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Захват высококачественного звука (16 кГц, 16 бит моно PCM) напрямую с микрофона без
 * запуска распознавания речи.
 * 
 * ПОЧЕМУ ЭТО РЕШАЕТ ПРОБЛЕМУ СИСТЕМНОГО ЗВУКА/БИПА:
 * Системное распознавание при каждом запуске и циклическом перезапуске
 * принудительно проигрывает звуковой сигнал (системный chime / beep).
 * `AudioRecord` работает напрямую с аппаратным аудиоустройством Android HAL.
 * Он НЕ использует службу распознавания Google и работает НА 100% БЕСШУМНО — никаких звуковых
 * эффектов, щелчков или бипов в системе не происходит!
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - context (Context): Контекст приложения.
 * - sampleRate (Int): Частота дискретизации (по умолчанию 16000 Гц — стандарт Whisper).
 * 
 * ВЫХОДНЫЕ ДАННЫЕ (Outputs):
 * - StateFlow<SilentRecordState>:
 *   * isRecording (Boolean): Идет ли запись.
 *   * rmsDb (Float): Нормализованный уровень громкости (0.0 .. 1.0) для живой волны.
 *   * currentDurationMs (Long): Длительность текущей записи в мс.
 * - awaitRecordingStopped(file): Ожидание закрытия WAV и получения его финального заголовка.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * val recorder = SilentAudioRecordModule(context)
 * recorder.startRecording()
 * recorder.state.collect { state ->
 *     waveformLevel = state.rmsDb
 * }
 * val stoppedFile = recorder.stopRecording()
 * val wavFile = recorder.awaitRecordingStopped(stoppedFile)
 * ```
 * 
 * ЗАГЛУШКИ И ПРОДАКШЕН:
 * Модуль пишет реальный стандартный RIFF WAV с корректным 44-байтным заголовком.
 * Поддерживает авто-определение пауз (VAD) и потоковую нарезку сэмплов для диаризации.
 */
class SilentAudioRecordModule(private val context: Context) {

    companion object {
        private const val TAG = "SilentAudioRecord"
        const val DEFAULT_SAMPLE_RATE = 16000
    }

    data class SilentRecordState(
        val isRecording: Boolean = false,
        val rmsDb: Float = 0f,
        val currentDurationMs: Long = 0L,
        val errorMessage: String? = null
    )

    private val _state = MutableStateFlow(SilentRecordState())
    val state: StateFlow<SilentRecordState> = _state.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    // Слушатель сэмплов для диаризации в реальном времени
    var onPcmChunkAvailable: ((ShortArray, Int) -> Unit)? = null

    // Коллбэк выделения завершенной речевой фразы на лету: (WavFile, DurationMs, PcmSamples)
    var onSpeechPhraseCaptured: ((File, Long, ShortArray) -> Unit)? = null

    /**
     * Старт бесшумной записи звука в WAV файл с распознаванием пауз и выделением фраз на лету
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        noiseGateThreshold: Float = 0.065f,
        minPhraseDurationMs: Int = 1000
    ): Boolean {
        stopRecording()

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Неподдерживаемая конфигурация AudioRecord")
            _state.value = _state.value.copy(errorMessage = "Ошибка конфигурации микрофона")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord = recorder

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord не инициализирован")
                recorder.release()
                audioRecord = null
                _state.value = _state.value.copy(errorMessage = "Микрофон недоступен")
                return false
            }

            val wavFile = File(context.cacheDir, "silent_rec_${System.currentTimeMillis()}.wav")
            currentOutputFile = wavFile

            recorder.startRecording()
            recordingStartTimeMs = System.currentTimeMillis()
            _state.value = SilentRecordState(isRecording = true)

            recordJob = CoroutineScope(Dispatchers.IO).launch {
                val shortBuffer = ShortArray(bufferSize / 2)
                val fos = FileOutputStream(wavFile)
                // Резервируем 44 байта под заголовок основного WAV
                fos.write(ByteArray(44))
                var totalBytesWritten = 0L

                // Буфер для захвата отдельной фразы на лету (Voice Activity Detection)
                val phraseShorts = ArrayList<Short>()
                var isSpeakingPhrase = false
                var speechStartTimeMs = 0L
                var lastSpeechLoudTimeMs = 0L

                try {
                    while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readShorts = recorder.read(shortBuffer, 0, shortBuffer.size)
                        if (readShorts > 0) {
                            // 1. Расчет RMS громкости для живой звуковой волны
                            var sumSquares = 0.0
                            for (i in 0 until readShorts) {
                                val s = shortBuffer[i].toDouble()
                                sumSquares += s * s
                            }
                            val rms = Math.sqrt(sumSquares / readShorts)
                            val normalizedRms = (rms / 8000.0).toFloat().coerceIn(0f, 1f)

                            val duration = System.currentTimeMillis() - recordingStartTimeMs
                            _state.value = _state.value.copy(
                                rmsDb = normalizedRms,
                                currentDurationMs = duration
                            )

                            // 2. Передача сэмплов для диаризатора
                            val chunkCopy = shortBuffer.copyOf(readShorts)
                            onPcmChunkAvailable?.invoke(chunkCopy, sampleRate)

                            // 3. Запись в общий бинарный поток WAV (little-endian)
                            val byteBuffer = ByteBuffer.allocate(readShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readShorts) {
                                byteBuffer.putShort(shortBuffer[i])
                            }
                            val byteArray = byteBuffer.array()
                            fos.write(byteArray)
                            totalBytesWritten += byteArray.size

                            // 4. Детектор речевых фраз на лету (VAD)
                            val now = System.currentTimeMillis()
                            val isCurrentFrameLoud = normalizedRms >= noiseGateThreshold

                            if (isCurrentFrameLoud) {
                                if (!isSpeakingPhrase) {
                                    isSpeakingPhrase = true
                                    speechStartTimeMs = now
                                    phraseShorts.clear()
                                }
                                lastSpeechLoudTimeMs = now
                            }

                            if (isSpeakingPhrase) {
                                for (i in 0 until readShorts) {
                                    phraseShorts.add(shortBuffer[i])
                                }

                                val silenceDuration = now - lastSpeechLoudTimeMs
                                val phraseDuration = now - speechStartTimeMs

                                // Срабатывание завершения порции речи для потокового синхронного перевода:
                                // Пауза всего 420 мс (естественная микропауза между словами) или накопление первых слов (~1800 мс)
                                val isPauseDetected = (silenceDuration >= 420L && phraseDuration >= 450L)
                                val isEarlyChunkReached = (phraseDuration >= 1800L)

                                if (isPauseDetected || isEarlyChunkReached) {
                                    if (phraseShorts.size >= (sampleRate * minPhraseDurationMs / 1000).toInt()) {
                                        val phraseCopy = ShortArray(phraseShorts.size)
                                        for (pIdx in phraseShorts.indices) {
                                            phraseCopy[pIdx] = phraseShorts[pIdx]
                                        }

                                        val phraseFile = File(context.cacheDir, "phrase_${System.currentTimeMillis()}.wav")
                                        saveShortsToWav(phraseFile, phraseCopy, sampleRate)

                                        Log.d(TAG, "Сформирована порция речи (первые слова): ${phraseDuration}мс, файл: ${phraseFile.name}")
                                        onSpeechPhraseCaptured?.invoke(phraseFile, phraseDuration, phraseCopy)
                                    }

                                    phraseShorts.clear()
                                    isSpeakingPhrase = false
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка в цикле записи: ${e.message}")
                } finally {
                    try {
                        emitRemainingPhrase(phraseShorts, sampleRate, minPhraseDurationMs)
                    } finally {
                        fos.flush()
                        fos.close()
                        writeWavHeader(wavFile, totalBytesWritten, sampleRate, 1, 16)
                        if (audioRecord === recorder) {
                            audioRecord = null
                            recorder.release()
                        }
                        Log.d(TAG, "Бесшумная запись WAV завершена: ${wavFile.absolutePath}, размер: $totalBytesWritten байт")
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка старта AudioRecord: ${e.message}", e)
            audioRecord?.release()
            audioRecord = null
            _state.value = SilentRecordState(isRecording = false, errorMessage = e.message)
            return false
        }
    }

    private fun emitRemainingPhrase(phraseShorts: ArrayList<Short>, sampleRate: Int, minPhraseDurationMs: Int = 1000) {
        if (phraseShorts.size < (sampleRate * minPhraseDurationMs / 1000).toInt()) return

        val phraseCopy = ShortArray(phraseShorts.size) { phraseShorts[it] }
        val phraseFile = File(context.cacheDir, "phrase_tail_${System.currentTimeMillis()}.wav")
        saveShortsToWav(phraseFile, phraseCopy, sampleRate)
        try {
            onSpeechPhraseCaptured?.invoke(
                phraseFile,
                phraseCopy.size * 1000L / sampleRate,
                phraseCopy
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка передачи последней речевой фразы: ${e.message}", e)
        }
    }

    /**
     * Вспомогательный метод сохранения массива PCM сэмплов Short в отдельный WAV файл фразы
     */
    private fun saveShortsToWav(file: File, shorts: ShortArray, sampleRate: Int) {
        try {
            val fos = FileOutputStream(file)
            fos.write(ByteArray(44)) // резервируем под заголовок
            val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in shorts) {
                byteBuffer.putShort(s)
            }
            val bytes = byteBuffer.array()
            fos.write(bytes)
            fos.flush()
            fos.close()
            writeWavHeader(file, bytes.size.toLong(), sampleRate, 1, 16)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения фразы в WAV: ${e.message}")
        }
    }

    /**
     * Останавливает захват и начинает финализацию WAV в фоновой задаче.
     */
    fun stopRecording(): File? {
        val wasRecording = _state.value.isRecording
        _state.value = _state.value.copy(isRecording = false, rmsDb = 0f)

        val recorder = audioRecord
        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка остановки AudioRecord: ${e.message}")
        } finally {
            if (audioRecord === recorder) {
                audioRecord = null
                recorder?.release()
            }
        }

        return if (wasRecording) currentOutputFile else null
    }

    /**
     * Возвращает файл только после закрытия потока и записи финального WAV-заголовка.
     */
    suspend fun awaitRecordingStopped(file: File?): File? {
        recordJob?.join()
        return file?.takeIf { it.exists() && it.length() >= 44L }
    }

    /**
     * Запись 44-байтного RIFF/WAVE заголовка
     */
    private fun writeWavHeader(
        file: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        if (!file.exists() || file.length() < 44) return

        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        try {
            val raf = RandomAccessFile(file, "rw")
            raf.seek(0)
            raf.write(header)
            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи заголовка WAV: ${e.message}")
        }
    }

    fun release() {
        stopRecording()
    }
}
