package com.example

import com.example.modules.transcription_filter.TranscriptionSanitizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionSanitizerTest {

    @Test
    fun `empty text is hallucination`() {
        assertTrue(TranscriptionSanitizer.isHallucination(""))
        assertTrue(TranscriptionSanitizer.isHallucination("   "))
    }

    @Test
    fun `known hallucination phrases are filtered`() {
        assertTrue(TranscriptionSanitizer.isHallucination("Продолжение следует"))
        assertTrue(TranscriptionSanitizer.isHallucination("thank you for watching"))
        assertTrue(TranscriptionSanitizer.isHallucination("Danke fürs Zuschauen"))
        assertTrue(TranscriptionSanitizer.isHallucination("[музыка]"))
    }

    @Test
    fun `high no speech prob is filtered`() {
        assertTrue(TranscriptionSanitizer.isHallucination("hello", noSpeechProb = 0.8f))
        assertFalse(TranscriptionSanitizer.isHallucination("hello", noSpeechProb = 0.1f))
    }

    @Test
    fun `normal speech is allowed`() {
        assertFalse(TranscriptionSanitizer.isHallucination("Мы начинаем совещание"))
        assertFalse(TranscriptionSanitizer.isHallucination("Hello world", noSpeechProb = 0.1f))
    }

    @Test
    fun `normalize lowercases and trims`() {
        assertEqualsNormalized("hello world", TranscriptionSanitizer.normalize("  Hello, World!  "))
    }

    private fun assertEqualsNormalized(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}