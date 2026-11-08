package com.example

import com.example.modules.audio_capture.AudioNoiseReducerModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule
import com.example.modules.translation.RealtimeTranslatorModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тестирование независимых функциональных модулей приложения VoxScribe
 */
class ExampleUnitTest {

    @Test
    fun testSpeakerDiarizationGenderClassification() {
        val diarizer = SpeakerDiarizerModule()

        // Мужской голос (низкий тон, < 165 Гц)
        val maleGender = diarizer.classifyGender(120f)
        assertEquals(SpeakerDiarizerModule.SpeakerGender.MALE, maleGender)

        // Женский голос (высокий тон, >= 165 Гц)
        val femaleGender = diarizer.classifyGender(215f)
        assertEquals(SpeakerDiarizerModule.SpeakerGender.FEMALE, femaleGender)

        // Проверка обработки реплики
        val segment = diarizer.processSpeechSegment(
            pcmSamples = null,
            sampleRate = 16000,
            recognizedText = "Тестовая реплика",
            timestampMs = 1000L
        )
        assertNotNull(segment)
        assertEquals("Тестовая реплика", segment.text)
    }

    @Test
    fun testRealtimeTranslationModule() {
        val translator = RealtimeTranslatorModule()

        // Проверка базового словаря Ru -> En
        val resHello = translator.translate("Привет", "ru", "en")
        assertEquals("Hello", resHello.translatedText)

        val resMeeting = translator.translate("совещание", "ru", "en")
        assertEquals("meeting", resMeeting.translatedText)

        // Проверка определения языка
        val langRu = translator.detectLanguage("Добрый день")
        assertEquals("ru", langRu)

        val langEn = translator.detectLanguage("Good morning")
        assertEquals("en", langEn)
    }

    @Test
    fun testAudioNoiseReducerGate() {
        val reducer = AudioNoiseReducerModule()

        // Создаем тихий фрейм (шум)
        val quietSamples = ShortArray(256) { 50 } // Очень маленькая амплитуда
        val resultQuiet = reducer.filterPcmFrame(quietSamples, noiseGateThreshold = 0.05f)
        assertTrue(resultQuiet.rmsAmplitude < 0.05f)

        // Создаем громкий речевой фрейм
        val loudSamples = ShortArray(256) { 15000 }
        val resultLoud = reducer.filterPcmFrame(loudSamples, noiseGateThreshold = 0.05f)
        assertTrue(resultLoud.isSpeechDetected)
    }

    @Test
    fun testAiSummaryModuleHeuristicAnalysis() {
        val aiModule = com.example.modules.ai_summary.AiSummaryModule()
        val sampleTranscript = """
            Спикер 1: Отлично, мы успешно завершили первую фазу проекта.
            Спикер 2: Да, все работает замечательно. Нужно подготовить отчет для руководства.
            Спикер 1: Согласен, я отправлю презентацию завтра.
        """.trimIndent()

        val result = aiModule.performOfflineHeuristicAnalysis(
            title = "Планерка",
            transcriptText = sampleTranscript
        )

        assertNotNull(result)
        assertEquals("Позитивная", result.sentiment)
        assertTrue(result.sentimentScore >= 70)
        assertTrue(result.actionItems.isNotEmpty())
        assertTrue(result.keyTopics.isNotEmpty())
    }

    @Test
    fun testFileManagementSortingAndFormatting() {
        assertEquals("02:30", com.example.modules.file_management.FileManagementModule.formatDuration(150))
        assertEquals("00:45", com.example.modules.file_management.FileManagementModule.formatDuration(45))
        assertEquals("10:00", com.example.modules.file_management.FileManagementModule.formatDuration(600))
    }

    @Test
    fun testOpenAiEndpointsDefaultConfig() {
        assertEquals("https://api.openai.com/v1", com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule.DEFAULT_OPENAI_BASE_URL)
        assertEquals("gpt-4o-mini", com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule.DEFAULT_LLM_MODEL)
        assertEquals("tts-1", com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule.DEFAULT_TTS_MODEL)
        assertEquals("alloy", com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule.DEFAULT_TTS_VOICE)
        assertEquals("whisper-1", com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule.DEFAULT_STT_MODEL)
    }

    @Test
    fun testWordTimestampModuleAlignment() {
        val words = com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
            text = "Привет мир Android",
            segmentStartMs = 0L,
            segmentEndMs = 3000L,
            speakerId = "speaker_1"
        )

        assertEquals(3, words.size)
        assertEquals("Привет", words[0].word)
        assertEquals("мир", words[1].word)
        assertEquals("Android", words[2].word)
        assertTrue(words[0].startMs < words[0].endMs)
        assertTrue(words[1].startMs >= words[0].endMs)
    }

    @Test
    fun testOpenAiModelsFetcherFilter() {
        val fetcher = com.example.modules.openai_endpoints.OpenAiModelsFetcherModule()
        val allModels = listOf("gpt-4o", "gpt-4o-mini", "whisper-1", "tts-1", "dall-e-3", "llama-3.3-70b")

        val llmModels = fetcher.filterLlmModels(allModels)
        assertTrue(llmModels.contains("gpt-4o"))
        assertTrue(llmModels.contains("llama-3.3-70b"))
        assertTrue(!llmModels.contains("whisper-1"))
        assertTrue(!llmModels.contains("tts-1"))

        val sttModels = fetcher.filterSttModels(allModels)
        assertTrue(sttModels.contains("whisper-1"))

        val ttsModels = fetcher.filterTtsModels(allModels)
        assertTrue(ttsModels.contains("tts-1"))
    }
}
