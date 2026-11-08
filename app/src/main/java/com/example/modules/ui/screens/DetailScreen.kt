package com.example.modules.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modules.ai_summary.AiSummaryModule
import com.example.modules.file_export_manager.FileExportManagerModule
import com.example.modules.file_export_manager.FileExportManagerModule.DocumentFormat
import com.example.modules.file_export_manager.FileExportManagerModule.ExportedFileItem
import com.example.modules.app_settings.AppSettingsModule
import com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule
import com.example.modules.openai_endpoints.OpenAiLlmModule
import com.example.modules.openai_endpoints.OpenAiTtsModule
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.storage_search.TranscriptionRepository
import com.example.modules.translation.RealtimeTranslatorModule
import com.example.modules.tts_ducking.AudioDuckingTtsModule
import com.example.modules.ui.components.ExportManagerDialog
import com.example.modules.ui.components.ExportOptionsDialog
import com.example.modules.ui.components.MoveToFolderDialog
import com.example.modules.ui.components.SpeakerSegmentItem
import com.example.modules.ui.components.SummaryCardsView
import com.example.ui.theme.TranslationTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: DetailScreen (Просмотр, перевод, суммаризация Gemini/OpenAI, экспорт PDF/DOCX)
 * =========================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    transcriptionId: String,
    repository: TranscriptionRepository,
    folders: List<FolderEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val openAiConfig = remember { OpenAiEndpointsConfigModule(context) }
    val openAiLlm = remember { OpenAiLlmModule(context, openAiConfig) }
    val openAiTts = remember { OpenAiTtsModule(context, openAiConfig) }
    val nativeTts = remember { AudioDuckingTtsModule(context) }
    val aiSummaryModule = remember { AiSummaryModule() }
    val exportManager = remember { FileExportManagerModule(context) }
    val translator = remember { RealtimeTranslatorModule() }
    val appSettings = remember { AppSettingsModule(context) }

    var transcription by remember { mutableStateOf<TranscriptionEntity?>(null) }
    var segments by remember { mutableStateOf<List<SpeakerSegment>>(emptyList()) }
    var inTranscriptSearch by remember { mutableStateOf("") }
    var isPlayingAll by remember { mutableStateOf(false) }
    var currentlyPlayingIndex by remember { mutableIntStateOf(-1) }

    // Вкладки: 0 - Стенограмма, 1 - Итоги ИИ (Gemini / OpenAI)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Анализ ИИ
    var analysisResult by remember { mutableStateOf<AiSummaryModule.TranscriptionAnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Диалоги
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf("") }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var showExportManagerDialog by remember { mutableStateOf(false) }
    var exportedFilesList by remember { mutableStateOf<List<ExportedFileItem>>(emptyList()) }
    var showTranslateMenu by remember { mutableStateOf(false) }

    LaunchedEffect(transcriptionId) {
        val item = repository.getTranscriptionById(transcriptionId)
        transcription = item
        if (item != null) {
            segments = repository.deserializeSegments(item.segmentsJson)
            editTitleText = item.title
        }
    }

    LaunchedEffect(Unit) {
        nativeTts.initialize {}
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeTts.release()
            openAiTts.stop()
        }
    }

    BackHandler {
        nativeTts.stop()
        openAiTts.stop()
        onBack()
    }

    // Запуск анализа (Gemini или кастомный OpenAI LLM в зависимости от настроек)
    fun runAiAnalysis() {
        val item = transcription ?: return
        isAnalyzing = true
        scope.launch {
            if (openAiConfig.isUseOpenAiLlm()) {
                val res = openAiLlm.generateSummary(item.fullText, item.targetLanguageCode.ifBlank { appSettings.getTargetLanguageCode("de") })
                analysisResult = res.getOrElse { err ->
                    // Fallback на Gemini при ошибке OpenAI
                    aiSummaryModule.analyzeTranscription(item.title, item.fullText)
                }
            } else {
                val result = aiSummaryModule.analyzeTranscription(item.title, item.fullText)
                analysisResult = result
            }
            isAnalyzing = false
        }
    }

    var currentPlaybackPositionMs by remember { mutableLongStateOf(0L) }

    // Озвучивание реплики с приглушением фоновой музыки (Audio Ducking) и пословной подсветкой
    fun speakSingleSegment(seg: SpeakerSegment) {
        val text = if (seg.translatedText.isNotBlank()) seg.translatedText else seg.text
        val segIdx = segments.indexOf(seg)
        currentlyPlayingIndex = segIdx

        scope.launch {
            if (openAiConfig.isUseOpenAiTts()) {
                val lang = if (seg.translatedText.isNotBlank()) {
                    transcription?.targetLanguageCode ?: appSettings.getTargetLanguageCode("de")
                } else {
                    "ru"
                }
                openAiTts.speakText(text, seg.gender, languageCode = lang)
            } else {
                val lang = if (seg.translatedText.isNotBlank()) (transcription?.targetLanguageCode ?: appSettings.getTargetLanguageCode("de")) else appSettings.getSpeechInputLanguage("ru")
                nativeTts.speak(text, seg.gender, lang)
            }

            val readingTimeMs = (text.length * 75L).coerceIn(1800L, 8000L)
            val stepMs = 70L
            var progress = 0L
            val startMs = seg.startTimeMs
            val endMs = if (seg.endTimeMs > seg.startTimeMs) seg.endTimeMs else seg.startTimeMs + readingTimeMs

            while (progress < readingTimeMs && currentlyPlayingIndex == segIdx) {
                currentPlaybackPositionMs = startMs + ((progress.toFloat() / readingTimeMs) * (endMs - startMs)).toLong()
                delay(stepMs)
                progress += stepMs
            }
            if (currentlyPlayingIndex == segIdx) {
                currentlyPlayingIndex = -1
                currentPlaybackPositionMs = 0L
            }
        }
    }

    // Проигрывание всех реплик подряд с Audio Ducking и динамической пословной подсветкой
    LaunchedEffect(isPlayingAll) {
        if (isPlayingAll) {
            for (i in segments.indices) {
                if (!isPlayingAll) break
                currentlyPlayingIndex = i
                val seg = segments[i]
                val textToSpeak = if (seg.translatedText.isNotBlank()) seg.translatedText else seg.text

                if (openAiConfig.isUseOpenAiTts()) {
                    val lang = if (seg.translatedText.isNotBlank()) {
                        transcription?.targetLanguageCode ?: appSettings.getTargetLanguageCode("de")
                    } else {
                        "ru"
                    }
                    openAiTts.speakText(textToSpeak, seg.gender, languageCode = lang)
                } else {
                    val lang = if (seg.translatedText.isNotBlank()) (transcription?.targetLanguageCode ?: appSettings.getTargetLanguageCode("de")) else appSettings.getSpeechInputLanguage("ru")
                    nativeTts.speak(textToSpeak, seg.gender, lang)
                }

                val readingTimeMs = (textToSpeak.length * 75L).coerceIn(1800L, 8000L)
                val stepMs = 70L
                var progress = 0L
                val startMs = seg.startTimeMs
                val endMs = if (seg.endTimeMs > seg.startTimeMs) seg.endTimeMs else seg.startTimeMs + readingTimeMs

                while (progress < readingTimeMs && isPlayingAll) {
                    currentPlaybackPositionMs = startMs + ((progress.toFloat() / readingTimeMs) * (endMs - startMs)).toLong()
                    delay(stepMs)
                    progress += stepMs
                }
            }
            isPlayingAll = false
            currentlyPlayingIndex = -1
            currentPlaybackPositionMs = 0L
        } else {
            nativeTts.stop()
            openAiTts.stop()
            currentlyPlayingIndex = -1
            currentPlaybackPositionMs = 0L
        }
    }

    val currentItem = transcription
    if (currentItem == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Загрузка...")
        }
        return
    }

    val currentFolder = folders.find { it.id == currentItem.folderId }
    val currentFolderColor = try { Color(android.graphics.Color.parseColor(currentFolder?.colorHex ?: "#1A237E")) } catch (e: Exception) { Color.DarkGray }
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    val filteredSegments = remember(segments, inTranscriptSearch) {
        if (inTranscriptSearch.isBlank()) segments
        else segments.filter {
            it.text.contains(inTranscriptSearch, ignoreCase = true) ||
            it.translatedText.contains(inTranscriptSearch, ignoreCase = true) ||
            it.speakerDisplayName.contains(inTranscriptSearch, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier.testTag("detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentItem.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateFormat.format(Date(currentItem.createdAt)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Кликабельный бейдж папки для быстрого перемещения
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = currentFolderColor.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { showMoveFolderDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = currentFolderColor, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = currentFolder?.name ?: "Переместить",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = currentFolderColor
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            nativeTts.stop()
                            openAiTts.stop()
                            onBack()
                        },
                        modifier = Modifier.testTag("btn_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Кнопка перехода к Итогам ИИ
                    IconButton(
                        onClick = {
                            selectedTab = 1
                            if (analysisResult == null && !isAnalyzing) {
                                runAiAnalysis()
                            }
                        },
                        modifier = Modifier.testTag("btn_topbar_gemini_summary")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Итоги ИИ",
                            tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Кнопка форматированного экспорта в PDF/DOCX
                    IconButton(
                        onClick = { showExportOptionsDialog = true },
                        modifier = Modifier.testTag("btn_export_detail")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Экспорт",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Переименование записи
                    IconButton(
                        onClick = { showEditTitleDialog = true },
                        modifier = Modifier.testTag("btn_edit_title")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать заголовок",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
        ) {
            // Вкладки переключения: Стенограмма <-> Итоги ИИ
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Стенограмма (${segments.size})") },
                    modifier = Modifier.testTag("tab_transcription")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (analysisResult == null && !isAnalyzing) {
                            runAiAnalysis()
                        }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (openAiConfig.isUseOpenAiLlm()) "Итоги OpenAI" else "Итоги Gemini ✨")
                        }
                    },
                    modifier = Modifier.testTag("tab_gemini_summary")
                )
            }

            if (selectedTab == 0) {
                // ВКЛАДКА 1: Стенограмма и перевод
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Панель действий: Перевод, Audio Ducking воспроизведение
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Кнопка перевода
                        Box {
                            FilterChip(
                                selected = currentItem.translatedFullText.isNotBlank(),
                                onClick = { showTranslateMenu = true },
                                label = {
                                    Text(
                                        text = if (currentItem.translatedFullText.isNotBlank())
                                            "Переведено: ${currentItem.targetLanguageCode.uppercase()}"
                                        else "Перевести",
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.testTag("chip_translate")
                            )

                            DropdownMenu(
                                expanded = showTranslateMenu,
                                onDismissRequest = { showTranslateMenu = false }
                            ) {
                                val languages = listOf(
                                    "ru" to "Русский 🇷🇺",
                                    "de" to "Deutsch 🇩🇪",
                                    "en" to "English 🇬🇧"
                                )
                                languages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            showTranslateMenu = false
                                            scope.launch {
                                                val translated = segments.map { seg ->
                                                    val tr = translator.translate(seg.text, "auto", code)
                                                    seg.copy(translatedText = tr.translatedText)
                                                }
                                                segments = translated
                                                val fullTr = translated.joinToString("\n") { it.translatedText }
                                                val updated = currentItem.copy(
                                                    translatedFullText = fullTr,
                                                    targetLanguageCode = code,
                                                    segmentsJson = repository.serializeSegments(translated),
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                                transcription = updated
                                                repository.insertOrUpdateTranscription(updated)
                                                Toast.makeText(context, "Перевод завершен", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Кнопка Audio Ducking воспроизведения всех реплик
                        Button(
                            onClick = { isPlayingAll = !isPlayingAll },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAll) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("btn_playback_all_ducking")
                        ) {
                            Icon(
                                imageVector = if (isPlayingAll) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlayingAll) "Стоп" else if (openAiConfig.isUseOpenAiTts()) "OpenAI TTS (Duck)" else "Озвучить (Duck)",
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // Поле локального поиска по стенограмме
                    OutlinedTextField(
                        value = inTranscriptSearch,
                        onValueChange = { inTranscriptSearch = it },
                        placeholder = { Text("Поиск в этой стенограмме...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("search_in_transcript")
                    )

                    // Список сегментов спикеров
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSegments, key = { it.segmentId }) { seg ->
                            val isCurrentlySpeaking = segments.indexOf(seg) == currentlyPlayingIndex
                            Box(
                                modifier = if (isCurrentlySpeaking) {
                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                } else Modifier
                            ) {
                                SpeakerSegmentItem(
                                    segment = seg,
                                    currentPlaybackPositionMs = currentPlaybackPositionMs,
                                    isPlaybackActive = isCurrentlySpeaking,
                                    onSpeakClicked = { item ->
                                        speakSingleSegment(item)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // ВКЛАДКА 2: Итоги ИИ (Cards UI)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    SummaryCardsView(
                        analysisResult = analysisResult,
                        isLoading = isAnalyzing,
                        onRefreshAnalysis = { runAiAnalysis() }
                    )
                }
            }
        }
    }

    // Диалог редактирования заголовка
    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Изменить заголовок") },
            text = {
                OutlinedTextField(
                    value = editTitleText,
                    onValueChange = { editTitleText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = currentItem.copy(
                            title = editTitleText.ifBlank { currentItem.title },
                            updatedAt = System.currentTimeMillis()
                        )
                        transcription = updated
                        scope.launch {
                            repository.insertOrUpdateTranscription(updated)
                        }
                        showEditTitleDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог быстрого перемещения в другую папку
    if (showMoveFolderDialog) {
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = currentItem.folderId,
            onDismiss = { showMoveFolderDialog = false },
            onSelectFolder = { newFolderId ->
                showMoveFolderDialog = false
                scope.launch {
                    val updated = currentItem.copy(folderId = newFolderId)
                    transcription = updated
                    repository.insertOrUpdateTranscription(updated)
                    Toast.makeText(context, "Папка изменена", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Диалог форматированного экспорта в PDF или DOCX
    if (showExportOptionsDialog) {
        val currentFolderObj = folders.find { it.id == currentItem.folderId }
        val folderName = currentFolderObj?.name ?: "Общее"

        ExportOptionsDialog(
            initialFormat = DocumentFormat.PDF,
            onDismiss = { showExportOptionsDialog = false },
            onConfirmExport = { format, options ->
                showExportOptionsDialog = false
                scope.launch {
                    val exported = when (format) {
                        DocumentFormat.PDF -> exportManager.exportToFormattedPdf(currentItem, folderName, segments, analysisResult, options)
                        DocumentFormat.DOCX -> exportManager.exportToFormattedDocx(currentItem, folderName, segments, analysisResult, options)
                        DocumentFormat.TXT -> exportManager.exportToFormattedTxt(currentItem, segments)
                    }
                    Toast.makeText(context, "Документ сохранен: ${exported.fileName}", Toast.LENGTH_SHORT).show()
                    exportManager.openFile(context, exported)
                }
            }
        )
    }

    // Диалог просмотра сохраненных файлов экспорта
    if (showExportManagerDialog) {
        ExportManagerDialog(
            exportedFiles = exportedFilesList,
            onOpenFile = { exportManager.openFile(context, it) },
            onShareFile = { exportManager.shareFile(context, it) },
            onDeleteFile = {
                exportManager.deleteExportedFile(it)
                scope.launch { exportedFilesList = exportManager.getAllExportedFiles() }
            },
            onDismiss = { showExportManagerDialog = false }
        )
    }
}
