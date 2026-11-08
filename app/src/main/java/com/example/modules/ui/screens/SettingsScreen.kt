package com.example.modules.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.modules.app_settings.AppSettingsModule
import com.example.modules.openai_endpoints.OpenAiEndpointsConfigModule
import com.example.modules.openai_endpoints.OpenAiLlmModule
import com.example.modules.openai_endpoints.OpenAiSttModule
import com.example.modules.openai_endpoints.OpenAiTtsModule
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.TranslationTeal
import kotlinx.coroutines.launch

/**
 * =========================================================================================
 * МОДУЛЬ: SettingsScreen (8 тем, языки, кастомные OpenAI эндпоинты LLM/TTS/STT, Groq и экспорт)
 * =========================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    currentAppLanguage: String,
    onLanguageSelected: (String) -> Unit,
    appSettings: AppSettingsModule? = null,
    groqApiKey: String,
    onSaveGroqApiKey: (String) -> Unit,
    isGroqEnabled: Boolean,
    onToggleGroqDefault: (Boolean) -> Unit,
    onOpenExportManager: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openAiConfig = remember { OpenAiEndpointsConfigModule(context) }
    val openAiLlm = remember { OpenAiLlmModule(context, openAiConfig) }
    val openAiTts = remember { OpenAiTtsModule(context, openAiConfig) }
    val openAiStt = remember { OpenAiSttModule(context, openAiConfig) }

    val settings = appSettings ?: remember { AppSettingsModule(context) }
    var noiseSensitivity by remember { mutableFloatStateOf(settings.getNoiseGateThreshold(0.065f)) }
    var ttsSpeechRate by remember { mutableFloatStateOf(1.0f) }
    var enteredGroqKey by remember { mutableStateOf(groqApiKey) }
    var showSaveKeyToast by remember { mutableStateOf(false) }

    // OpenAI LLM State
    var useOpenAiLlm by remember { mutableStateOf(openAiConfig.isUseOpenAiLlm()) }
    var llmBaseUrl by remember { mutableStateOf(openAiConfig.getLlmBaseUrl()) }
    var llmApiKey by remember { mutableStateOf(openAiConfig.getLlmApiKey()) }
    var llmModel by remember { mutableStateOf(openAiConfig.getLlmModel()) }
    var llmTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingLlm by remember { mutableStateOf(false) }

    // OpenAI TTS State
    var useOpenAiTts by remember { mutableStateOf(openAiConfig.isUseOpenAiTts()) }
    var ttsBaseUrl by remember { mutableStateOf(openAiConfig.getTtsBaseUrl()) }
    var ttsApiKey by remember { mutableStateOf(openAiConfig.getTtsApiKey()) }
    var ttsModel by remember { mutableStateOf(openAiConfig.getTtsModel()) }
    var ttsVoice by remember { mutableStateOf(openAiConfig.getTtsVoice()) }
    var ttsTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingTts by remember { mutableStateOf(false) }

    // OpenAI STT State
    var sttProviderMode by remember { mutableStateOf(openAiConfig.getSttProviderMode()) }
    var sttBaseUrl by remember { mutableStateOf(openAiConfig.getSttBaseUrl()) }
    var sttApiKey by remember { mutableStateOf(openAiConfig.getSttApiKey()) }
    var sttModel by remember { mutableStateOf(openAiConfig.getSttModel()) }
    var sttTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingStt by remember { mutableStateOf(false) }

    // Модули опроса моделей и проверки Groq
    val modelsFetcher = remember { com.example.modules.openai_endpoints.OpenAiModelsFetcherModule() }
    val groqSttModule = remember { com.example.modules.stt_groq.GroqSttProviderModule(context) }

    // Состояния для кнопки "Загрузить список моделей" (Fetch model list)
    var isFetchingLlmModels by remember { mutableStateOf(false) }
    var llmModelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLlmModelsDialog by remember { mutableStateOf(false) }

    var isFetchingTtsModels by remember { mutableStateOf(false) }
    var ttsModelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showTtsModelsDialog by remember { mutableStateOf(false) }

    var isFetchingSttModels by remember { mutableStateOf(false) }
    var sttModelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSttModelsDialog by remember { mutableStateOf(false) }

    // Состояния проверки ключа Groq STT
    var isTestingGroqKey by remember { mutableStateOf(false) }
    var groqKeyStatus by remember { mutableStateOf<String?>(null) }
    var groqModelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGroqModelsDialog by remember { mutableStateOf(false) }

    val exportManager = remember { com.example.modules.file_export_manager.FileExportManagerModule(context) }
    var showExportManagerDialog by remember { mutableStateOf(false) }
    var exportedFilesList by remember { mutableStateOf<List<com.example.modules.file_export_manager.FileExportManagerModule.ExportedFileItem>>(emptyList()) }

    fun refreshExportedFiles() {
        scope.launch {
            exportedFilesList = exportManager.getAllExportedFiles()
        }
    }

    val isGerman = currentAppLanguage == "de"

    Scaffold(
        modifier = modifier.testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isGerman) "Einstellungen" else "Настройки VoxScribe",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            refreshExportedFiles()
                            showExportManagerDialog = true
                            onOpenExportManager()
                        },
                        modifier = Modifier.testTag("btn_open_export_manager")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Менеджер экспорта", tint = MaterialTheme.colorScheme.primary)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Кнопка быстрого перехода в Менеджер сохраненных экспортов
            Card(
                onClick = {
                    refreshExportedFiles()
                    showExportManagerDialog = true
                    onOpenExportManager()
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().testTag("card_export_manager")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Менеджер экспортированных файлов",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Управление локальными PDF, DOCX и TXT документами",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // =========================================================
            // 1. ВЫБОР ЯЗЫКА ИНТЕРФЕЙСА И РЕЧИ (RU / DE / EN)
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isGerman) "App- und Erkennungssprache" else "Язык интерфейса и распознавания",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = if (isGerman) "Russisch, Deutsch und Englisch werden vollständig unterstützt" else "Полная поддержка русского, немецкого и английского",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentAppLanguage == "ru",
                            onClick = { onLanguageSelected("ru") },
                            label = { Text("🇷🇺 Русский") },
                            modifier = Modifier.weight(1f).testTag("lang_chip_ru")
                        )

                        FilterChip(
                            selected = currentAppLanguage == "de",
                            onClick = { onLanguageSelected("de") },
                            label = { Text("🇩🇪 Deutsch") },
                            modifier = Modifier.weight(1f).testTag("lang_chip_de")
                        )

                        FilterChip(
                            selected = currentAppLanguage == "en",
                            onClick = { onLanguageSelected("en") },
                            label = { Text("🇬🇧 English") },
                            modifier = Modifier.weight(1f).testTag("lang_chip_en")
                        )
                    }
                }
            }

            // =========================================================
            // 2. 8 РАЗНОЦВЕТНЫХ ТЕМ: 4 ДНЕВНЫХ И 4 НОЧНЫХ
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isGerman) "Farbthemen (8 Themen)" else "Цветовые темы (8 тем)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.5.sp
                            )
                            Text(
                                text = if (isGerman) "4 Tagesthemen und 4 Nachtthemen" else "4 дневных и 4 ночных темы",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ☀️ 4 ДНЕВНЫХ ТЕМЫ
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LightMode, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGerman) "4 Tagesthemen (Hell):" else "4 Дневных темы (Светлые):",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val dayThemes = listOf(
                        AppThemeMode.LIGHT_INDIGO,
                        AppThemeMode.LIGHT_EMERALD,
                        AppThemeMode.LIGHT_AMBER,
                        AppThemeMode.LIGHT_AMETHYST
                    )

                    dayThemes.forEach { theme ->
                        ThemeSelectionRow(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            isGerman = isGerman,
                            onSelect = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🌙 4 НОЧНЫХ ТЕМЫ
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGerman) "4 Nachtthemen (Dunkel):" else "4 Ночных темы (Темные):",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val nightThemes = listOf(
                        AppThemeMode.DARK_SPACE,
                        AppThemeMode.DARK_CYBERPUNK,
                        AppThemeMode.DARK_OBSIDIAN,
                        AppThemeMode.DARK_AURORA
                    )

                    nightThemes.forEach { theme ->
                        ThemeSelectionRow(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            isGerman = isGerman,
                            onSelect = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // =========================================================
            // 3. КАСТОМНЫЙ OPENAI-СОВМЕСТИМЫЙ LLM ЭНДПОИНТ
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("OpenAI-совместимый LLM", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Для резюме и анализа задач", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = useOpenAiLlm,
                            onCheckedChange = {
                                useOpenAiLlm = it
                                openAiConfig.setUseOpenAiLlm(it)
                            },
                            modifier = Modifier.testTag("switch_openai_llm")
                        )
                    }

                    if (useOpenAiLlm) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = llmBaseUrl,
                            onValueChange = {
                                llmBaseUrl = it
                                openAiConfig.setLlmBaseUrl(it)
                            },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1 или http://192.168.1.1:11434/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_llm_url")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = llmApiKey,
                            onValueChange = {
                                llmApiKey = it
                                openAiConfig.setLlmApiKey(it)
                            },
                            label = { Text("API Key (необязателен для локальных моделей)") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_llm_key")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = llmModel,
                                onValueChange = {
                                    llmModel = it
                                    openAiConfig.setLlmModel(it)
                                },
                                label = { Text("Модель") },
                                placeholder = { Text("gpt-4o-mini, llama3...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("input_openai_llm_model")
                            )

                            OutlinedButton(
                                onClick = {
                                    isFetchingLlmModels = true
                                    scope.launch {
                                        val res = modelsFetcher.fetchModelsList(llmBaseUrl, llmApiKey)
                                        isFetchingLlmModels = false
                                        res.onSuccess { models ->
                                            llmModelsList = modelsFetcher.filterLlmModels(models)
                                            showLlmModelsDialog = true
                                        }.onFailure { err ->
                                            llmTestStatus = "Ошибка списка: ${err.message}"
                                        }
                                    }
                                },
                                enabled = !isFetchingLlmModels,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("btn_fetch_llm_models")
                            ) {
                                if (isFetchingLlmModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Модели", fontSize = 11.5.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                isTestingLlm = true
                                llmTestStatus = null
                                scope.launch {
                                    val res = openAiLlm.testConnection(llmBaseUrl, llmApiKey, llmModel)
                                    isTestingLlm = false
                                    llmTestStatus = res.getOrElse { it.message ?: "Ошибка" }
                                }
                            },
                            enabled = !isTestingLlm,
                            modifier = Modifier.fillMaxWidth().testTag("btn_test_openai_llm")
                        ) {
                            Text(if (isTestingLlm) "Проверка..." else "Проверить связь с LLM")
                        }

                        llmTestStatus?.let { status ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                fontSize = 11.5.sp,
                                color = if (status.startsWith("Соединение успешно")) TranslationTeal else MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "По умолчанию используется встроенный Gemini 3.8 Flash через Firebase AI Logic.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // =========================================================
            // 4. КАСТОМНЫЙ OPENAI-СОВМЕСТИМЫЙ TTS ЭНДПОИНТ (С AUDIO DUCKING)
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("OpenAI-совместимый TTS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Синтез речи с Audio Ducking", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = useOpenAiTts,
                            onCheckedChange = {
                                useOpenAiTts = it
                                openAiConfig.setUseOpenAiTts(it)
                            },
                            modifier = Modifier.testTag("switch_openai_tts")
                        )
                    }

                    if (useOpenAiTts) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ttsBaseUrl,
                            onValueChange = {
                                ttsBaseUrl = it
                                openAiConfig.setTtsBaseUrl(it)
                            },
                            label = { Text("TTS Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_tts_url")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = ttsApiKey,
                            onValueChange = {
                                ttsApiKey = it
                                openAiConfig.setTtsApiKey(it)
                            },
                            label = { Text("API Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_tts_key")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = ttsModel,
                                onValueChange = {
                                    ttsModel = it
                                    openAiConfig.setTtsModel(it)
                                },
                                label = { Text("Модель") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("input_openai_tts_model")
                            )

                            OutlinedButton(
                                onClick = {
                                    isFetchingTtsModels = true
                                    scope.launch {
                                        val res = modelsFetcher.fetchModelsList(ttsBaseUrl, ttsApiKey)
                                        isFetchingTtsModels = false
                                        res.onSuccess { models ->
                                            ttsModelsList = modelsFetcher.filterTtsModels(models)
                                            showTtsModelsDialog = true
                                        }.onFailure { err ->
                                            ttsTestStatus = "Ошибка списка: ${err.message}"
                                        }
                                    }
                                },
                                enabled = !isFetchingTtsModels,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("btn_fetch_tts_models")
                            ) {
                                if (isFetchingTtsModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Модели", fontSize = 11.5.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = ttsVoice,
                            onValueChange = {
                                ttsVoice = it
                                openAiConfig.setTtsVoice(it)
                            },
                            label = { Text("Голос") },
                            placeholder = { Text("alloy, nova, onyx, echo...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_tts_voice")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                isTestingTts = true
                                ttsTestStatus = null
                                scope.launch {
                                    val res = openAiTts.testTts(ttsBaseUrl, ttsApiKey, ttsModel, ttsVoice)
                                    isTestingTts = false
                                    ttsTestStatus = res.getOrElse { it.message ?: "Ошибка" }
                                }
                            },
                            enabled = !isTestingTts,
                            modifier = Modifier.fillMaxWidth().testTag("btn_test_openai_tts")
                        ) {
                            Text(if (isTestingTts) "Синтез аудио..." else "Проверить синтез речи (Duck)")
                        }

                        ttsTestStatus?.let { status ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                fontSize = 11.5.sp,
                                color = if (status.startsWith("Синтез успешен")) TranslationTeal else MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Используется встроенный оффлайн Text-to-Speech Android с авто-выбором мужского/женского голоса.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // =========================================================
            // 5. STT РАСПОЗНАВАНИЕ РЕЧИ: ОБЛАЧНЫЕ ПРОВАЙДЕРЫ
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Провайдер распознавания речи (STT)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Выбор движка для расшифровки аудиофайлов", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            sttProviderMode = "GROQ"
                            openAiConfig.setSttProviderMode("GROQ")
                            groqSttModule.setGroqAsDefault(true)
                            onToggleGroqDefault(true)
                        }) {
                            RadioButton(selected = sttProviderMode == "GROQ", onClick = {
                                sttProviderMode = "GROQ"
                                openAiConfig.setSttProviderMode("GROQ")
                                groqSttModule.setGroqAsDefault(true)
                                onToggleGroqDefault(true)
                            })
                            Text("Groq Whisper Cloud (whisper-large-v3, без бипов 🔇)", fontSize = 13.5.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            sttProviderMode = "OPENAI"
                            openAiConfig.setSttProviderMode("OPENAI")
                            groqSttModule.setGroqAsDefault(false)
                            onToggleGroqDefault(false)
                        }) {
                            RadioButton(selected = sttProviderMode == "OPENAI", onClick = {
                                sttProviderMode = "OPENAI"
                                openAiConfig.setSttProviderMode("OPENAI")
                                groqSttModule.setGroqAsDefault(false)
                                onToggleGroqDefault(false)
                            })
                            Text("Кастомный OpenAI Whisper STT", fontSize = 13.5.sp)
                        }
                    }

                    if (sttProviderMode == "GROQ") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = enteredGroqKey,
                            onValueChange = { enteredGroqKey = it },
                            label = { Text("Groq API Key (gsk_...)") },
                            placeholder = { Text("Введите ключ Groq") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onSaveGroqApiKey(enteredGroqKey)
                                    groqSttModule.saveGroqApiKey(enteredGroqKey)
                                    showSaveKeyToast = true
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Сохранить", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_groq_api_key")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Кнопка проверки ключа Groq STT
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isTestingGroqKey = true
                                    groqKeyStatus = null
                                    onSaveGroqApiKey(enteredGroqKey)
                                    groqSttModule.saveGroqApiKey(enteredGroqKey)
                                    scope.launch {
                                        val res = groqSttModule.validateGroqApiKey(enteredGroqKey)
                                        isTestingGroqKey = false
                                        res.onSuccess { models ->
                                            groqModelsList = models
                                            val whisperModels = models.filter { it.contains("whisper", ignoreCase = true) }
                                            groqKeyStatus = "✓ Ключ действителен! Доступно ${models.size} моделей Groq (${whisperModels.joinToString()})"
                                        }.onFailure { err ->
                                            groqKeyStatus = "✗ Ошибка проверки ключа Groq: ${err.message}"
                                        }
                                    }
                                },
                                enabled = !isTestingGroqKey,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("btn_validate_groq_key")
                            ) {
                                if (isTestingGroqKey) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Проверка...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Проверить ключ Groq STT", fontSize = 12.sp)
                                }
                            }

                            if (groqModelsList.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showGroqModelsDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("btn_show_groq_models")
                                ) {
                                    Text("Модели (${groqModelsList.size})", fontSize = 11.5.sp)
                                }
                            }
                        }

                        groqKeyStatus?.let { status ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (status.startsWith("✓")) TranslationTeal else MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (sttProviderMode == "OPENAI") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sttBaseUrl,
                            onValueChange = {
                                sttBaseUrl = it
                                openAiConfig.setSttBaseUrl(it)
                            },
                            label = { Text("Whisper Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_stt_url")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = sttApiKey,
                            onValueChange = {
                                sttApiKey = it
                                openAiConfig.setSttApiKey(it)
                            },
                            label = { Text("API Key") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_openai_stt_key")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = sttModel,
                                onValueChange = {
                                    sttModel = it
                                    openAiConfig.setSttModel(it)
                                },
                                label = { Text("Модель") },
                                placeholder = { Text("whisper-1") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("input_openai_stt_model")
                            )

                            OutlinedButton(
                                onClick = {
                                    isFetchingSttModels = true
                                    scope.launch {
                                        val res = modelsFetcher.fetchModelsList(sttBaseUrl, sttApiKey)
                                        isFetchingSttModels = false
                                        res.onSuccess { models ->
                                            sttModelsList = modelsFetcher.filterSttModels(models)
                                            showSttModelsDialog = true
                                        }.onFailure { err ->
                                            sttTestStatus = "Ошибка списка: ${err.message}"
                                        }
                                    }
                                },
                                enabled = !isFetchingSttModels,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("btn_fetch_stt_models")
                            ) {
                                if (isFetchingSttModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Модели", fontSize = 11.5.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                isTestingStt = true
                                sttTestStatus = null
                                scope.launch {
                                    val res = openAiStt.testConnection(sttBaseUrl, sttApiKey, sttModel)
                                    isTestingStt = false
                                    sttTestStatus = res.getOrElse { it.message ?: "Ошибка" }
                                }
                            },
                            enabled = !isTestingStt,
                            modifier = Modifier.fillMaxWidth().testTag("btn_test_openai_stt")
                        ) {
                            Text(if (isTestingStt) "Проверка..." else "Проверить связь со Whisper STT")
                        }

                        sttTestStatus?.let { status ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status,
                                fontSize = 11.5.sp,
                                color = if (status.startsWith("Подключение успешно")) TranslationTeal else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // =========================================================
            // 6. ШУМОПОДАВЛЕНИЕ И AUDIO DUCKING
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NoiseAware, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isGerman) "Rauschunterdrückung (Noise Gate)" else "Шумоподавление (Noise Gate)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${if (isGerman) "Schwellenwert" else "Порог отсечения фонового шума"}: ${(noiseSensitivity * 100).toInt()}%",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = noiseSensitivity,
                        onValueChange = { noiseSensitivity = it
                            settings.setNoiseGateThreshold(it)
                        },
                        valueRange = 0.01f..0.15f,
                        modifier = Modifier.testTag("slider_noise_gate")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = TranslationTeal)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio Ducking",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isGerman)
                            "Musik im Hintergrund wird bei Sprachausgabe sanft gedämpft (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) und nicht gestoppt."
                        else
                            "Фоновая музыка не выключается при синтезе речи, а плавно приглушается (Audio Ducking) и сразу восстанавливается.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // =========================================================
            // 7. СКОРОСТЬ TTS
            // =========================================================
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isGerman) "Sprachausgabe-Geschwindigkeit" else "Скорость воспроизведения речи (TTS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", ttsSpeechRate)}x",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = ttsSpeechRate,
                        onValueChange = { ttsSpeechRate = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.testTag("slider_tts_speed")
                    )
                }
            }
        }
    }

    if (showExportManagerDialog) {
        com.example.modules.ui.components.ExportManagerDialog(
            exportedFiles = exportedFilesList,
            onOpenFile = { exportManager.openFile(context, it) },
            onShareFile = { exportManager.shareFile(context, it) },
            onDeleteFile = {
                exportManager.deleteExportedFile(it)
                refreshExportedFiles()
            },
            onDismiss = { showExportManagerDialog = false }
        )
    }

    // Диалог выбора модели LLM
    if (showLlmModelsDialog) {
        AlertDialog(
            onDismissRequest = { showLlmModelsDialog = false },
            title = { Text("Выберите модель LLM (${llmModelsList.size})") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(llmModelsList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    llmModel = item
                                    openAiConfig.setLlmModel(item)
                                    showLlmModelsDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.5.sp,
                                fontWeight = if (item == llmModel) FontWeight.Bold else FontWeight.Normal,
                                color = if (item == llmModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLlmModelsDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Диалог выбора модели TTS
    if (showTtsModelsDialog) {
        AlertDialog(
            onDismissRequest = { showTtsModelsDialog = false },
            title = { Text("Выберите модель TTS (${ttsModelsList.size})") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(ttsModelsList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ttsModel = item
                                    openAiConfig.setTtsModel(item)
                                    showTtsModelsDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.5.sp,
                                fontWeight = if (item == ttsModel) FontWeight.Bold else FontWeight.Normal,
                                color = if (item == ttsModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTtsModelsDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Диалог выбора модели STT
    if (showSttModelsDialog) {
        AlertDialog(
            onDismissRequest = { showSttModelsDialog = false },
            title = { Text("Выберите модель Whisper STT (${sttModelsList.size})") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(sttModelsList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sttModel = item
                                    openAiConfig.setSttModel(item)
                                    showSttModelsDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.5.sp,
                                fontWeight = if (item == sttModel) FontWeight.Bold else FontWeight.Normal,
                                color = if (item == sttModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSttModelsDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Диалог моделей Groq
    if (showGroqModelsDialog) {
        AlertDialog(
            onDismissRequest = { showGroqModelsDialog = false },
            title = { Text("Доступные модели Groq (${groqModelsList.size})") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    items(groqModelsList) { item ->
                        val isWhisper = item.contains("whisper", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.sp,
                                fontWeight = if (isWhisper) FontWeight.Bold else FontWeight.Normal,
                                color = if (isWhisper) TranslationTeal else MaterialTheme.colorScheme.onSurface
                            )
                            if (isWhisper) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = TranslationTeal.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "STT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TranslationTeal,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroqModelsDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }
}

@Composable
private fun ThemeSelectionRow(
    theme: AppThemeMode,
    isSelected: Boolean,
    isGerman: Boolean,
    onSelect: () -> Unit
) {
    val name = if (isGerman) theme.displayNameDe else theme.displayNameRu

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("theme_row_${theme.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(theme.previewPrimaryColor)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = name,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
