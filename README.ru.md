# VoxScribe — Модульное Android-приложение автономной транскрибации речи

**VoxScribe** — Android-приложение для записи и облачной транскрибации речи и аудиофайлов через Groq Whisper или OpenAI-совместимый STT, с разделением реплик нескольких спикеров (диаризация), синхронным переводом, менеджером файлов в базе данных Room, экспортом в PDF и DOCX, анализом итогов через Gemini/OpenAI, захватом звука через AudioRecord, пословными таймкодами и синтезом речи (TTS) с приглушением фоновой музыки (Audio Ducking). Распознавание речи на устройстве Android не используется.

## Сборка
```bash
gradle :app:assembleDebug
```
или через Android Studio.

## Тесты
```bash
gradle :app:testDebugUnitTest
```

## Модули
Код разложен по `com.example.modules`: audio_capture, speaker_diarization, translation, tts_ducking, storage_search, openai_endpoints, stt_groq, ai_summary, document_export, file_management, ui.

## Темы
8 тем Material 3: 4 дневные и 4 ночные.

## Языки интерфейса
Русский, немецкий, английский.