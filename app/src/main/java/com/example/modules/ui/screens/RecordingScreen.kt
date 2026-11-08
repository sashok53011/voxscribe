package com.example.modules.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.modules.audio_capture.SilentAudioRecordModule
import com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule
import com.example.modules.openai_endpoints.OpenAiSttModule
import com.example.modules.openai_endpoints.OpenAiTtsModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.app_settings.AppSettingsModule
import com.example.modules.storage_search.TranscriptionRepository
import com.example.modules.stt_groq.GroqSttProviderModule
import com.example.modules.translation.LlmRealtimeTranslatorModule
import com.example.modules.translation.RealtimeTranslatorModule
import com.example.modules.tts_ducking.AudioDuckingTtsModule
import com.example.modules.ui.components.LiveWaveformVisualizer
import com.example.modules.ui.components.SpeakerSegmentItem
import com.example.modules.word_timestamps.WordTimestamp
import com.example.modules.word_timestamps.WordTimestampModule
import com.example.ui.theme.TranslationTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: RecordingScreen (Экран записи и распознавания речи в реальном времени)
 * =========================================================================================
 * 
 * ОСОБЕННОСТИ:
 * 1. Захват микрофона через AudioRecord с потоковой расшифровкой облачными STT-провайдерами.
 * 2. Адаптивная нижняя панель с Modifier.navigationBarsPadding():
 *    Кнопка "Завершить и сохранить" никогда не перекрывается системными кнопками навигации.
 * 3. Полная поддержка пословных таймкодов (WordTimestamp) и автоматического перевода с Ducking TTS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    repository: TranscriptionRepository,
    folders: List<FolderEntity>,
    initialFolderId: String?,
    currentAppLanguage: String = "ru",
    appSettings: AppSettingsModule? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Провайдеры и модули
    val groqStt = remember { GroqSttProviderModule(context) }
    val openAiConfig = remember { OpenAiEndpointsConfigModule(context) }
    val openAiStt = remember { OpenAiSttModule(context, openAiConfig) }
    val openAiTts = remember { OpenAiTtsModule(context, openAiConfig) }
    val silentRecorder = remember { SilentAudioRecordModule(context) }
    val diarizer = remember { SpeakerDiarizerModule() }
    val translator = remember { RealtimeTranslatorModule() }
    val llmTranslator = remember { LlmRealtimeTranslatorModule(context, openAiConfig, groqStt, translator) }
    val ttsModule = remember { AudioDuckingTtsModule(context) }

    // Состояния
    val silentState by silentRecorder.state.collectAsState()
    val segments = remember { mutableStateListOf<SpeakerDiarizerModule.SpeakerSegment>() }
    var lastRecognizedText by remember { mutableStateOf("") }
    val collectedWords = remember { mutableStateListOf<WordTimestamp>() }
    val cloudChunkMutex = remember { Mutex() }

    var isRecording by remember { mutableStateOf(false) }
    var isFinalizingRecording by remember { mutableStateOf(false) }
    var isLlmTranslatingOnTheFly by remember { mutableStateOf(false) }
    var groqStatusNotice by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var startTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var sttProviderMode by remember { mutableStateOf(openAiConfig.getSttProviderMode()) }

    // Настройки сессии
    val settings = appSettings ?: remember { AppSettingsModule(context) }
    var speechInputLanguage by remember { mutableStateOf(settings.getSpeechInputLanguage(currentAppLanguage)) }
    var targetLanguageCode by remember { mutableStateOf(settings.getTargetLanguageCode(if (currentAppLanguage == "de") "ru" else "de")) }
    var isTranslationEnabled by remember { mutableStateOf(true) }
    var isAutoSpeakEnabled by remember { mutableStateOf(true) } // Ducking TTS на лету (по умолчанию ВКЛ)
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showSourceLanguageMenu by remember { mutableStateOf(false) }

    // Диалог сохранения
    var showSaveDialog by remember { mutableStateOf(false) }
    var recordTitle by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf(initialFolderId ?: folders.firstOrNull()?.id ?: "") }

    val listState = rememberLazyListState()

    // Функция потокового озвучивания текста на лету с Audio Ducking
    fun speakTranslatedOnTheFly(
        textToSpeak: String,
        gender: SpeakerDiarizerModule.SpeakerGender,
        languageCode: String = targetLanguageCode,
        queueMode: Int = android.speech.tts.TextToSpeech.QUEUE_FLUSH
    ) {
        if (textToSpeak.isBlank()) return
        scope.launch {
            if (!isAutoSpeakEnabled) return@launch
            if (openAiConfig.isUseOpenAiTts()) {
                openAiTts.speakText(text = textToSpeak, gender = gender, languageCode = languageCode)
                    .onFailure { error ->
                        groqStatusNotice = "Ошибка TTS: ${error.localizedMessage ?: error.message}"
                    }
            } else {
                ttsModule.speak(
                    text = textToSpeak,
                    gender = gender,
                    languageCode = targetLanguageCode,
                    queueMode = queueMode
                )
            }
        }
    }

    suspend fun translateAndSpeakOnTheFly(
        text: String,
        gender: SpeakerDiarizerModule.SpeakerGender
    ): LlmRealtimeTranslatorModule.LlmTranslationResult =
        llmTranslator.translateOnTheFlyStreaming(
            text = text,
            sourceLang = speechInputLanguage,
            targetLang = targetLanguageCode
        ) { translatedChunk ->
            speakTranslatedOnTheFly(
                textToSpeak = translatedChunk,
                gender = gender,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
            )
        }

    // Проверка разрешений на микрофон
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun startActiveRecording() {
        isRecording = true
        val threshold = settings.getNoiseGateThreshold(0.065f)
        val minPhraseMs = settings.getVadMinPhraseMs(1000)
        if (!silentRecorder.startRecording(noiseGateThreshold = threshold, minPhraseDurationMs = minPhraseMs)) {
            isRecording = false
            groqStatusNotice = silentRecorder.state.value.errorMessage ?: "Не удалось запустить запись микрофона."
        }
    }

    fun stopActiveRecording(): File? {
        isRecording = false
        return silentRecorder.stopRecording()
    }

    fun selectSttProvider(provider: String) {
        if (provider == sttProviderMode) return
        val resumeRecording = isRecording
        val pendingWav = if (resumeRecording) stopActiveRecording() else null
        sttProviderMode = provider
        openAiConfig.setSttProviderMode(provider)
        groqStt.setGroqAsDefault(provider == "GROQ")
        if (resumeRecording) {
            scope.launch {
                silentRecorder.awaitRecordingStopped(pendingWav)
                cloudChunkMutex.withLock { }
                startActiveRecording()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) {
            startActiveRecording()
        }
    }

    // Transcribe every captured speech chunk with the selected cloud provider.
    LaunchedEffect(Unit) {
        ttsModule.initialize {}

        silentRecorder.onSpeechPhraseCaptured = { phraseFile, phraseDurationMs, pcmCopy ->
            scope.launch {
                cloudChunkMutex.lock()
                try {
                    try {
                        isLlmTranslatingOnTheFly = true
                        val recognizedSegments = if (sttProviderMode == "GROQ") {
                            val result = groqStt.transcribeAudioFile(phraseFile, language = speechInputLanguage)
                            if (!result.isSuccess) {
                                groqStatusNotice = result.errorMessage ?: "Ошибка распознавания Groq"
                                emptyList()
                            } else {
                                result.segments.filter { seg ->
                                    !com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)
                                }
                            }
                        } else {
                            openAiStt.transcribeAudioFile(phraseFile, languageCode = speechInputLanguage)
                                .getOrElse { error ->
                                    groqStatusNotice = "Ошибка OpenAI STT: ${error.localizedMessage ?: error.message}"
                                    null
                                }?.segments.orEmpty().filter { seg ->
                                    !com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)
                                }
                        }

                        val phraseTimestamp = System.currentTimeMillis() - phraseDurationMs
                        recognizedSegments.filter { seg ->
                            com.example.modules.transcription_filter.TranscriptionSanitizer.normalize(seg.text) != com.example.modules.transcription_filter.TranscriptionSanitizer.normalize(lastRecognizedText)
                        }.forEach { recognized ->
                            val baseSegment = diarizer.processSpeechSegment(
                                pcmSamples = pcmCopy,
                                sampleRate = 16000,
                                recognizedText = recognized.text,
                                timestampMs = phraseTimestamp + recognized.startTimeMs,
                                durationMs = phraseDurationMs,
                                explicitWords = recognized.words
                            )
                            val finalSegment = if (isTranslationEnabled) {
                                val llmResult = translateAndSpeakOnTheFly(baseSegment.text, baseSegment.gender)
                                groqStatusNotice = "Перевод (${llmResult.providerName}): ${llmResult.translatedText}"
                                baseSegment.copy(translatedText = llmResult.translatedText)
                            } else {
                                speakTranslatedOnTheFly(
                                    textToSpeak = baseSegment.text,
                                    gender = baseSegment.gender,
                                    languageCode = speechInputLanguage,
                                    queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                                )
                                baseSegment
                            }

                            segments.add(finalSegment)
                            collectedWords.addAll(finalSegment.words)
                            lastRecognizedText = finalSegment.text
                        }
                        if (segments.isNotEmpty()) {
                            listState.animateScrollToItem(segments.size - 1)
                        }
                    } catch (e: Exception) {
                        Log.e("RecordingScreen", "Ошибка потоковой цепочки на лету: ${e.message}", e)
                        groqStatusNotice = "Ошибка потоковой обработки: ${e.localizedMessage ?: e.message}"
                    } finally {
                        isLlmTranslatingOnTheFly = false
                        phraseFile.delete()
                    }
                } finally {
                    cloudChunkMutex.unlock()
                }
            }
        }
    }

    // Таймер записи
    LaunchedEffect(isRecording) {
        if (isRecording) {
            startTimeMs = System.currentTimeMillis()
            while (isRecording) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // Автостарт при входе если есть разрешения
    LaunchedEffect(Unit) {
        if (hasRecordPermission) {
            startActiveRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Очистка ресурсов при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            silentRecorder.release()
            ttsModule.release()
        }
    }

    fun finishRecordingAndShowSave() {
        val recordedWav = stopActiveRecording()
        val defaultTitle = "Запись " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        recordTitle = defaultTitle

        isFinalizingRecording = true
        groqStatusNotice = "Завершение записи и потоковой обработки..."
        scope.launch {
            try {
                val completedWav = silentRecorder.awaitRecordingStopped(recordedWav)
                cloudChunkMutex.withLock {
                    if (completedWav != null && completedWav.length() > 44L && segments.isEmpty()) {
                        groqStatusNotice = "Финальная транскрибация через $sttProviderMode..."
                        val transcribedSegments = if (sttProviderMode == "GROQ") {
                            val result = groqStt.transcribeAudioFile(completedWav, language = speechInputLanguage)
                            if (result.isSuccess) result.segments.filter { seg ->
                                !com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)
                            } else {
                                groqStatusNotice = result.errorMessage ?: "Ошибка распознавания Groq"
                                emptyList()
                            }
                        } else {
                            openAiStt.transcribeAudioFile(completedWav, languageCode = speechInputLanguage)
                                .getOrElse { error ->
                                    groqStatusNotice = "Ошибка OpenAI STT: ${error.localizedMessage ?: error.message}"
                                    null
                                }?.segments.orEmpty().filter { seg ->
                                    !com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)
                                }
                        }
                        if (transcribedSegments.isNotEmpty()) {
                            segments.clear()
                            collectedWords.clear()
                            val processed = transcribedSegments.map { segment ->
                                if (isTranslationEnabled) {
                                    val translation = translateAndSpeakOnTheFly(segment.text, segment.gender)
                                    segment.copy(translatedText = translation.translatedText)
                                } else {
                                    speakTranslatedOnTheFly(
                                        textToSpeak = segment.text,
                                        gender = segment.gender,
                                        languageCode = speechInputLanguage,
                                        queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                                    )
                                    segment
                                }
                            }
                            segments.addAll(processed)
                            collectedWords.addAll(processed.flatMap { it.words })
                            groqStatusNotice = "Распознано успешно: ${processed.size} реплик"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordingScreen", "Ошибка завершения облачной транскрибации: ${e.message}", e)
                groqStatusNotice = "Ошибка завершения записи: ${e.localizedMessage ?: e.message}"
            } finally {
                isFinalizingRecording = false
                showSaveDialog = true
            }
        }
    }

    BackHandler {
        if (isRecording || segments.isNotEmpty()) {
            recordTitle = "Запись " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
            finishRecordingAndShowSave()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.testTag("recording_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Color.Red else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val minutes = elapsedSeconds / 60
                        val seconds = elapsedSeconds % 60
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (segments.isNotEmpty()) {
                                recordTitle = "Запись " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
                                finishRecordingAndShowSave()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("btn_rec_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsPanel = !showSettingsPanel },
                        modifier = Modifier.testTag("btn_toggle_filters")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Настройки движка и TTS",
                            tint = if (showSettingsPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // КРИТИЧЕСКИЙ ФИКС: navigationBarsPadding() гарантирует, что системные кнопки навигации
            // Android не накладываются на кнопку "Завершить и сохранить"!
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка Пауза / Возобновить
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                stopActiveRecording()
                            } else {
                                startActiveRecording()
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("btn_rec_pause_resume")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Пауза" else "Продолжить",
                            tint = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Кнопка завершения и сохранения — адаптирована по ширине через weight(1f)
                    Button(
                        onClick = { finishRecordingAndShowSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(24.dp),
                        enabled = !isFinalizingRecording,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_stop_and_save")
                    ) {
                        if (isFinalizingRecording) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обработка...", fontSize = 13.sp, maxLines = 1)
                        } else {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Завершить и сохранить",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Информационная панель статуса
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TranslationTeal.copy(alpha = 0.18f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (sttProviderMode == "GROQ") "Groq Whisper (облачный)" else "OpenAI Whisper (облачный)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TranslationTeal
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TranslationTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isAutoSpeakEnabled) "Ducking TTS: ВКЛ" else "Ducking: Готов",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TranslationTeal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LiveWaveformVisualizer(
                rmsLevel = silentState.rmsDb,
                isRecording = isRecording
            )

            // Панель настроек сессии записи
            AnimatedVisibility(visible = showSettingsPanel) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Движок распознавания речи:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = sttProviderMode == "GROQ",
                                onClick = { selectSttProvider("GROQ") },
                                label = { Text("Groq Whisper", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = sttProviderMode == "OPENAI",
                                onClick = { selectSttProvider("OPENAI") },
                                label = { Text("OpenAI Whisper", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // STT language is also the source language for translation.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Язык распознавания (STT) и источник перевода:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Один выбранный язык используется для распознавания и как исходный при переводе.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { showSourceLanguageMenu = true },
                                    label = {
                                        val language = RealtimeTranslatorModule.SUPPORTED_LANGUAGES
                                            .first { it.code == speechInputLanguage }
                                        Text("${language.flagEmoji} ${language.displayName}")
                                    },
                                    modifier = Modifier.testTag("chip_stt_language")
                                )
                                DropdownMenu(
                                    expanded = showSourceLanguageMenu,
                                    onDismissRequest = { showSourceLanguageMenu = false }
                                ) {
                                    RealtimeTranslatorModule.SUPPORTED_LANGUAGES.forEach { language ->
                                        DropdownMenuItem(
                                            text = { Text("${language.flagEmoji} ${language.displayName}") },
                                            onClick = {
                                                speechInputLanguage = language.code
                                                showSourceLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Перевод
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Автоперевод речи", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = isTranslationEnabled,
                                onCheckedChange = { isTranslationEnabled = it },
                                modifier = Modifier.testTag("switch_translation")
                            )
                        }

                        if (isTranslationEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Язык перевода и TTS:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box {
                                    Button(
                                        onClick = { showLanguageMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp).testTag("btn_select_lang")
                                    ) {
                                        val language = RealtimeTranslatorModule.SUPPORTED_LANGUAGES
                                            .first { it.code == targetLanguageCode }
                                        Text("${language.flagEmoji} ${language.displayName}", fontSize = 11.sp)
                                    }

                                    DropdownMenu(
                                        expanded = showLanguageMenu,
                                        onDismissRequest = { showLanguageMenu = false }
                                    ) {
                                        RealtimeTranslatorModule.SUPPORTED_LANGUAGES.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text("${lang.flagEmoji} ${lang.displayName}") },
                                                onClick = {
                                                    targetLanguageCode = lang.code
                                                    settings.setTargetLanguageCode(lang.code)
                                                    showLanguageMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Ducking TTS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Озвучивать речь (TTS)", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Приглушает музыку во время речи",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoSpeakEnabled,
                                onCheckedChange = { isAutoSpeakEnabled = it },
                                modifier = Modifier.testTag("switch_auto_tts")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Сообщение о статусе Groq
            groqStatusNotice?.let { notice ->
                Text(
                    text = notice,
                    fontSize = 11.5.sp,
                    color = if (notice.contains("Ошибка")) MaterialTheme.colorScheme.error else TranslationTeal,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Список зафиксированных реплик с диаризацией и пословными таймкодами
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(segments, key = { it.segmentId }) { seg ->
                    SpeakerSegmentItem(
                        segment = seg,
                        onSpeakClicked = { item ->
                            val textToSpeak = if (item.translatedText.isNotBlank()) item.translatedText else item.text
                            val lang = if (item.translatedText.isNotBlank()) targetLanguageCode else speechInputLanguage
                            if (openAiConfig.isUseOpenAiTts()) {
                                scope.launch {
                                    openAiTts.speakText(textToSpeak, item.gender, languageCode = lang)
                                        .onFailure { error ->
                                            groqStatusNotice = "Ошибка TTS: ${error.localizedMessage ?: error.message}"
                                        }
                                }
                            } else {
                                ttsModule.speak(textToSpeak, item.gender, lang)
                            }
                        }
                    )
                }
            }
        }
    }

    // Диалог сохранения стенограммы
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить стенограмму") },
            text = {
                Column {
                    OutlinedTextField(
                        value = recordTitle,
                        onValueChange = { recordTitle = it },
                        label = { Text("Название записи") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_save_title")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Выберите папку:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (folder in folders.take(3)) {
                            val isSelected = selectedFolderId == folder.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFolderId = folder.id },
                                label = { Text(folder.name, fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fullTranscript = segments.joinToString("\n") { "${it.speakerDisplayName}: ${it.text}" }
                        val fullTranslated = segments.mapNotNull { it.translatedText.ifBlank { null } }.joinToString("\n")
                        val uniqueSpeakers = segments.map { it.speakerId }.distinct().size.coerceAtLeast(1)

                        val allWordsList = if (collectedWords.isNotEmpty()) collectedWords else segments.flatMap { it.words }

                        val entity = TranscriptionEntity(
                            title = recordTitle.ifBlank { "Запись без названия" },
                            folderId = selectedFolderId.ifBlank { folders.firstOrNull()?.id ?: "folder_notes" },
                            fullText = fullTranscript,
                            translatedFullText = fullTranslated,
                            targetLanguageCode = if (isTranslationEnabled) targetLanguageCode else "",
                            segmentsJson = repository.serializeSegments(segments),
                            wordTimestampsJson = WordTimestampModule.serializeWords(allWordsList),
                            durationSeconds = elapsedSeconds,
                            speakerCount = uniqueSpeakers,
                            audioSourceType = "MIC"
                        )

                        scope.launch {
                            repository.insertOrUpdateTranscription(entity)
                            showSaveDialog = false
                            onSaved(entity.id)
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_save_transcription")
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    onBack()
                }) {
                    Text("Выйти без сохранения")
                }
            }
        )
    }
}
