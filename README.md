# VoxScribe — Modular Android App for Speech Transcription

**VoxScribe** is an Android application for recording and cloud-based transcription of speech and audio files via Groq Whisper or any OpenAI-compatible STT endpoint. It includes speaker diarization, real-time translation, Room database file management, PDF/DOCX export, AI summaries via Gemini/OpenAI, word-level timestamps, and TTS with audio ducking. On-device Android speech recognition is not used.

## Features
- Silent microphone capture via `AudioRecord` (no system beeps)
- Voice Activity Detection (VAD) with configurable noise gate
- Cloud STT: Groq Whisper or OpenAI-compatible Whisper
- Speaker diarization and gender classification
- Real-time translation and TTS with audio ducking
- Room database, folders, full-text search
- PDF/DOCX/TXT export
- AI summaries (Gemini / OpenAI-compatible LLM)
- Word-level timestamps and playback highlighting
- 8 Material 3 themes
- Multilingual UI: Russian, German, English

## Build
```bash
gradle :app:assembleDebug
```
or use Android Studio.

## Tests
```bash
gradle :app:testDebugUnitTest
```

## Project Structure
See `app/src/main/java/com/example/modules`.

## License
Add your license here.