package com.example.modules.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modules.audio_capture.AudioFileDecoderModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.app_settings.AppSettingsModule
import com.example.modules.storage_search.TranscriptionRepository
import com.example.modules.stt_groq.GroqSttProviderModule
import com.example.modules.translation.RealtimeTranslatorModule
import com.example.modules.ui.components.SpeakerSegmentItem
import com.example.ui.theme.TranslationTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * =========================================================================================
 * МОДУЛЬ: FileTranscriptionScreen (Транскрибация аудиофайлов облачными STT-провайдерами)
 * =========================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTranscriptionScreen(
    repository: TranscriptionRepository,
    folders: List<FolderEntity>,
    currentAppLanguage: String = "ru",
    appSettings: AppSettingsModule? = null,
    onBack: () -> Unit,
    onTranscriptionCompleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fileDecoder = remember { AudioFileDecoderModule(context) }
    val diarizer = remember { SpeakerDiarizerModule() }
    val translator = remember { RealtimeTranslatorModule() }
    val groqStt = remember { GroqSttProviderModule(context) }
    val openAiConfig = remember { com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule(context) }
    val openAiStt = remember { com.example.modules.openai_endpoints.OpenAiSttModule(context, openAiConfig) }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var fileMetadata by remember { mutableStateOf<AudioFileDecoderModule.AudioFileMetadata?>(null) }
    var selectedFolderId by remember { mutableStateOf(folders.firstOrNull()?.id ?: "") }
    val settings = appSettings ?: remember { AppSettingsModule(context) }
    var speechInputLanguage by remember { mutableStateOf(settings.getSpeechInputLanguage(currentAppLanguage)) }
    var targetLanguageCode by remember { mutableStateOf(settings.getTargetLanguageCode(if (currentAppLanguage == "de") "ru" else "de")) }
    var sttProviderChoice by remember { mutableStateOf(openAiConfig.getSttProviderMode()) }

    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentProcessingStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val segments = remember { mutableStateListOf<SpeakerDiarizerModule.SpeakerSegment>() }

    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showSourceLanguageDropdown by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val meta = fileDecoder.extractMetadata(uri)
            fileMetadata = meta
            segments.clear()
            progress = 0f
            errorMessage = null
            currentProcessingStatus = "Файл выбран: ${meta.fileName}"
        }
    }

    BackHandler {
        if (!isProcessing) onBack()
    }

    Scaffold(
        modifier = modifier.testTag("file_transcription_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Транскрибация аудиофайла", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing, modifier = Modifier.testTag("btn_file_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Карточка выбора файла
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileMetadata?.fileName ?: "Аудиофайл не выбран",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            val metaInfo = fileMetadata?.let {
                                val durSec = it.durationMs / 1000
                                "Формат: ${it.mimeType} | Длительность: ${durSec / 60}м ${durSec % 60}с"
                            } ?: "Поддерживаются WAV, MP3, M4A, OGG, AAC"
                            Text(text = metaInfo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("audio/*") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_select_audio_file")
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (selectedFileUri == null) "Выбрать аудиофайл" else "Выбрать другой файл")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Выбор облачного STT-провайдера
            Text("Провайдер распознавания речи (STT):", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = sttProviderChoice == "GROQ",
                    onClick = {
                        sttProviderChoice = "GROQ"
                        openAiConfig.setSttProviderMode("GROQ")
                    },
                    label = { Text("Groq", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.weight(1f).testTag("chip_stt_groq")
                )

                FilterChip(
                    selected = sttProviderChoice == "OPENAI",
                    onClick = {
                        sttProviderChoice = "OPENAI"
                        openAiConfig.setSttProviderMode("OPENAI")
                    },
                    label = { Text("OpenAI", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.weight(1f).testTag("chip_stt_openai")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explicit source language for STT and translation.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showSourceLanguageDropdown = true },
                        label = {
                            val language = RealtimeTranslatorModule.SUPPORTED_LANGUAGES
                                .first { it.code == speechInputLanguage }
                            Text("Распознавание и источник: ${language.flagEmoji} ${language.displayName}")
                        },
                        modifier = Modifier.testTag("chip_file_source_language")
                    )

                    DropdownMenu(
                        expanded = showSourceLanguageDropdown,
                        onDismissRequest = { showSourceLanguageDropdown = false }
                    ) {
                        RealtimeTranslatorModule.SUPPORTED_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.flagEmoji} ${lang.displayName}") },
                                onClick = {
                                    speechInputLanguage = lang.code
                                    showSourceLanguageDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Translation target; saved with the transcription and reused for translated TTS.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showLanguageDropdown = true },
                        label = {
                            val language = RealtimeTranslatorModule.SUPPORTED_LANGUAGES
                                .first { it.code == targetLanguageCode }
                            Text("Язык перевода и TTS: ${language.flagEmoji} ${language.displayName}")
                        },
                        modifier = Modifier.testTag("chip_file_lang")
                    )

                    DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false }
                    ) {
                        RealtimeTranslatorModule.SUPPORTED_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.flagEmoji} ${lang.displayName}") },
                                onClick = {
                                    targetLanguageCode = lang.code
                                    settings.setTargetLanguageCode(lang.code)
                                    showLanguageDropdown = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Папка: ${folders.find { it.id == selectedFolderId }?.name ?: "Общее"}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопка запуска транскрибации файла
            Button(
                onClick = {
                    val uri = selectedFileUri
                    if (uri != null && !isProcessing) {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            currentProcessingStatus = "Чтение файла и подготовка аудио..."
                            progress = 0.2f

                            val tempFile = withContext(Dispatchers.IO) {
                                val f = File(context.cacheDir, "audio_upload_${System.currentTimeMillis()}.wav")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(f).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                f
                            }

                            if (sttProviderChoice == "GROQ") {
                                // Транскрибация через Groq Whisper
                                currentProcessingStatus = "Отправка в Groq Whisper LPU..."
                                progress = 0.45f

                                val groqResult = groqStt.transcribeAudioFile(tempFile, speechInputLanguage)
                                if (groqResult.isSuccess && groqResult.text.isNotBlank()) {
                                    currentProcessingStatus = "Диаризация и синхронный перевод..."
                                    progress = 0.85f

                                    segments.clear()
                                    var lastSegText = ""
                                    for (seg in groqResult.segments) {
                                        val norm = com.example.modules.transcription_filter.TranscriptionSanitizer.normalize(seg.text)
                                        if (com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)) continue
                                        if (norm == lastSegText) continue
                                        lastSegText = norm
                                        val trans = translator.translate(seg.text, speechInputLanguage, targetLanguageCode)
                                        segments.add(seg.copy(translatedText = trans.translatedText))
                                    }

                                    progress = 1.0f
                                    currentProcessingStatus = "Транскрибация Groq завершена!"
                                    delay(400L)

                                    val fullText = segments.joinToString("\n") { "${it.speakerDisplayName}: ${it.text}" }
                                    val fullTrans = segments.joinToString("\n") { it.translatedText }
                                    val fileNameClean = fileMetadata?.fileName ?: "Groq_Аудио"

                                    val allWordsList = if (groqResult.allWords.isNotEmpty()) groqResult.allWords else segments.flatMap { it.words }
                                    val entity = TranscriptionEntity(
                                        title = fileNameClean,
                                        folderId = selectedFolderId.ifBlank { folders.firstOrNull()?.id ?: "folder_notes" },
                                        fullText = fullText,
                                        translatedFullText = fullTrans,
                                        targetLanguageCode = targetLanguageCode,
                                        segmentsJson = repository.serializeSegments(segments),
                                        wordTimestampsJson = com.example.modules.word_timestamps.WordTimestampModule.serializeWords(allWordsList),
                                        durationSeconds = groqResult.durationSeconds.toInt(),
                                        speakerCount = 2,
                                        audioSourceType = "FILE_GROQ"
                                    )

                                    repository.insertOrUpdateTranscription(entity)
                                    isProcessing = false
                                    onTranscriptionCompleted(entity.id)
                                } else {
                                    isProcessing = false
                                    errorMessage = groqResult.errorMessage ?: "Ошибка распознавания Groq"
                                }
                            } else if (sttProviderChoice == "OPENAI") {
                                // Транскрибация через кастомный OpenAI Whisper
                                currentProcessingStatus = "Отправка в OpenAI Whisper эндпоинт..."
                                progress = 0.45f

                                val openAiResult = openAiStt.transcribeAudioFile(tempFile, speechInputLanguage)
                                openAiResult.fold(
                                    onSuccess = { res ->
                                        currentProcessingStatus = "Синхронный перевод реплик..."
                                        progress = 0.85f
                                        segments.clear()
                                        var lastSegText2 = ""
                                        for (seg in res.segments) {
                                            val norm = com.example.modules.transcription_filter.TranscriptionSanitizer.normalize(seg.text)
                                            if (com.example.modules.transcription_filter.TranscriptionSanitizer.isHallucination(seg.text)) continue
                                            if (norm == lastSegText2) continue
                                            lastSegText2 = norm
                                            val trans = translator.translate(seg.text, speechInputLanguage, targetLanguageCode)
                                            segments.add(seg.copy(translatedText = trans.translatedText))
                                        }

                                        progress = 1.0f
                                        currentProcessingStatus = "Транскрибация OpenAI Whisper завершена!"
                                        delay(400L)

                                        val fullText = if (segments.isNotEmpty()) segments.joinToString("\n") { "${it.speakerDisplayName}: ${it.text}" } else res.fullText
                                        val fullTrans = segments.joinToString("\n") { it.translatedText }
                                        val fileNameClean = fileMetadata?.fileName ?: "OpenAI_Whisper_Аудио"

                                        val allWordsList = segments.flatMap { it.words }
                                        val entity = TranscriptionEntity(
                                            title = fileNameClean,
                                            folderId = selectedFolderId.ifBlank { folders.firstOrNull()?.id ?: "folder_notes" },
                                            fullText = fullText,
                                            translatedFullText = fullTrans,
                                            targetLanguageCode = targetLanguageCode,
                                            segmentsJson = repository.serializeSegments(segments),
                                            wordTimestampsJson = com.example.modules.word_timestamps.WordTimestampModule.serializeWords(allWordsList),
                                            durationSeconds = (fileMetadata?.durationMs?.div(1000) ?: 30).toInt(),
                                            speakerCount = if (segments.size > 1) 2 else 1,
                                            audioSourceType = "FILE_OPENAI"
                                        )

                                        repository.insertOrUpdateTranscription(entity)
                                        isProcessing = false
                                        onTranscriptionCompleted(entity.id)
                                    },
                                    onFailure = { err ->
                                        isProcessing = false
                                        errorMessage = "Ошибка OpenAI Whisper: ${err.message}"
                                    }
                                )
                            } else {
                                isProcessing = false
                                errorMessage = "Выбран неизвестный облачный STT-провайдер."
                            }
                        }
                    }
                },
                enabled = selectedFileUri != null && !isProcessing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_start_file_transcription")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (sttProviderChoice) {
                        "GROQ" -> "Транскрибировать через Groq Whisper"
                        "OPENAI" -> "Транскрибировать через OpenAI Whisper"
                        else -> "Начать транскрибацию"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = currentProcessingStatus,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Список сегментов
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(segments, key = { it.segmentId }) { seg ->
                    SpeakerSegmentItem(
                        segment = seg,
                        onSpeakClicked = {}
                    )
                }
            }
        }
    }
}
