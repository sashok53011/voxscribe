# VoxScribe — Modulare Android-App für Sprachtranskription

**VoxScribe** ist eine Android-App zur Aufnahme und Cloud-Transkription von Sprache und Audiodateien über Groq Whisper oder einen OpenAI-kompatiblen STT-Endpunkt. Enthalten sind Sprecherdiarisierung, Echtzeit-Übersetzung, Room-Datenbank, PDF/DOCX-Export, KI-Zusammenfassungen über Gemini/OpenAI, wortgenaue Zeitstempel und TTS mit Audio-Ducking. Die Android-Spracherkennung auf dem Gerät wird nicht verwendet.

## Build
```bash
gradle :app:assembleDebug
```
oder Android Studio.

## Tests
```bash
gradle :app:testDebugUnitTest
```

## Module
Code unter `com.example.modules`: audio_capture, speaker_diarization, translation, tts_ducking, storage_search, openai_endpoints, stt_groq, ai_summary, document_export, file_management, ui.

## Themes
8 Material-3-Themes: 4 hell, 4 dunkel.

## Sprachen
Russisch, Deutsch, Englisch.