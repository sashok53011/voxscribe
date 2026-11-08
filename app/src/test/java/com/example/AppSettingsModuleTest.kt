package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.modules.app_settings.AppSettingsModule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsModuleTest {

    @Test
    fun `language persists`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = AppSettingsModule(context)
        settings.setAppLanguage("de")
        settings.setSpeechInputLanguage("en")
        settings.setTargetLanguageCode("ru")
        assertEquals("de", settings.getAppLanguage())
        assertEquals("en", settings.getSpeechInputLanguage())
        assertEquals("ru", settings.getTargetLanguageCode())
    }

    @Test
    fun `theme and noise gate persist`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = AppSettingsModule(context)
        settings.setTheme("DARK_SPACE")
        settings.setNoiseGateThreshold(0.12f)
        settings.setVadMinPhraseMs(900)
        assertEquals("DARK_SPACE", settings.getTheme())
        assertEquals(0.12f, settings.getNoiseGateThreshold(), 0.0001f)
        assertEquals(900, settings.getVadMinPhraseMs())
    }
}