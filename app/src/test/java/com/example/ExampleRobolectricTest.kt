package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.modules.document_export.DocxExporterModule
import com.example.modules.document_export.PdfExporterModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule
import com.example.modules.storage_search.TranscriptionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VoxScribe", appName)
    }

    @Test
    fun `test repository serialization`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TranscriptionRepository(context)

        val testSegments = listOf(
            SpeakerDiarizerModule.SpeakerSegment(
                segmentId = "seg_1",
                speakerId = "speaker_1",
                speakerDisplayName = "Спикер 1 (Мужской)",
                gender = SpeakerDiarizerModule.SpeakerGender.MALE,
                pitchHz = 120f,
                text = "Привет мир",
                translatedText = "Hello world",
                startTimeMs = 0L,
                endTimeMs = 2500L
            )
        )

        val json = repository.serializeSegments(testSegments)
        assertTrue(json.contains("Привет мир"))

        val deserialized = repository.deserializeSegments(json)
        assertEquals(1, deserialized.size)
        assertEquals("Привет мир", deserialized[0].text)
        assertEquals(SpeakerDiarizerModule.SpeakerGender.MALE, deserialized[0].gender)
    }

    @Test
    fun `test docx exporter file generation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val docxExporter = DocxExporterModule(context)

        val testSegments = listOf(
            SpeakerDiarizerModule.SpeakerSegment(
                speakerId = "speaker_1",
                speakerDisplayName = "Спикер 1",
                gender = SpeakerDiarizerModule.SpeakerGender.MALE,
                pitchHz = 120f,
                text = "Тестовый текст",
                translatedText = "Test text",
                startTimeMs = 0L,
                endTimeMs = 2000L
            )
        )

        val docxFile = docxExporter.exportToDocx(
            title = "Тест",
            folderName = "Встречи",
            segments = testSegments,
            creationDateMs = System.currentTimeMillis()
        )

        assertNotNull(docxFile)
        assertTrue(docxFile.exists())
        assertTrue(docxFile.length() > 0)
    }

    @Test
    fun `test word timestamp serialization and deserialization`() {
        val words = com.example.modules.word_timestamps.WordTimestampModule.alignWordsToDuration(
            text = "Проверка пословных меток",
            segmentStartMs = 100L,
            segmentEndMs = 2500L,
            speakerId = "speaker_1"
        )
        val json = com.example.modules.word_timestamps.WordTimestampModule.serializeWords(words)
        assertTrue(json.contains("Проверка"))

        val deserialized = com.example.modules.word_timestamps.WordTimestampModule.deserializeWords(json)
        assertEquals(3, deserialized.size)
        assertEquals("Проверка", deserialized[0].word)
        assertEquals(100L, deserialized[0].startMs)
    }

    @Test
    fun `test llm realtime translator module offline fallback`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val translator = com.example.modules.translation.LlmRealtimeTranslatorModule(context)
        val result = translator.translateOnTheFly("Привет", "ru", "de")

        assertNotNull(result)
        assertTrue(result.translatedText.isNotBlank())
        assertEquals("Привет", result.originalText)
        assertTrue(result.latencyMs >= 0)
    }
}
