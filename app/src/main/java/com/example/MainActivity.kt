package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.app_settings.AppSettingsModule
import com.example.modules.storage_search.TranscriptionRepository
import com.example.modules.stt_groq.GroqSttProviderModule
import com.example.modules.ui.screens.DetailScreen
import com.example.modules.ui.screens.FileTranscriptionScreen
import com.example.modules.ui.screens.HomeScreen
import com.example.modules.ui.screens.RecordingScreen
import com.example.modules.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.VoxScribeTheme
import kotlinx.coroutines.launch

/**
 * =========================================================================================
 * ТОЧКА ВХОДА: MainActivity (Маршрутизация экранов, 8 тем, локализация RU/DE/EN, Groq STT)
 * =========================================================================================
 */
class MainActivity : ComponentActivity() {

    sealed class Screen {
        object Home : Screen()
        object Recording : Screen()
        data class Detail(val transcriptionId: String) : Screen()
        object FileTranscription : Screen()
        object Settings : Screen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация Firebase App Check для Gemini API перед UI и ViewModels
        com.example.modules.ai_summary.configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            val appSettings = remember { AppSettingsModule(this@MainActivity) }
            var currentTheme by remember {
                mutableStateOf(
                    runCatching { AppThemeMode.valueOf(appSettings.getTheme()) }.getOrDefault(
                        if (systemDark) AppThemeMode.DARK_SPACE else AppThemeMode.LIGHT_INDIGO
                    )
                )
            }
            var currentAppLanguage by remember { mutableStateOf(appSettings.getAppLanguage()) }

            VoxScribeTheme(themeMode = currentTheme) {
                MainAppNavHost(
                    currentTheme = currentTheme,
                    onThemeSelected = { theme ->
                        currentTheme = theme
                        appSettings.setTheme(theme.name)
                    },
                    currentAppLanguage = currentAppLanguage,
                    onLanguageSelected = { code ->
                        currentAppLanguage = code
                        appSettings.setAppLanguage(code)
                    },
                    appSettings = appSettings
                )
            }
        }
    }
}

@Composable
fun MainAppNavHost(
    currentTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    currentAppLanguage: String,
    onLanguageSelected: (String) -> Unit,
    appSettings: AppSettingsModule
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TranscriptionRepository(context) }
    val groqStt = remember { GroqSttProviderModule(context) }

    var groqApiKey by remember { mutableStateOf(groqStt.getGroqApiKey()) }
    var isGroqDefault by remember { mutableStateOf(groqStt.isGroqDefault()) }

    var currentScreen by remember { mutableStateOf<MainActivity.Screen>(MainActivity.Screen.Home) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Начальное заполнение стандартными папками
    LaunchedEffect(Unit) {
        repository.seedDefaultFoldersIfEmpty()
    }

    val folders by repository.allFoldersFlow.collectAsState(initial = emptyList())
    val transcriptions by if (searchQuery.isNotBlank()) {
        repository.searchTranscriptions(searchQuery).collectAsState(initial = emptyList())
    } else if (selectedFolderId != null) {
        repository.getTranscriptionsByFolder(selectedFolderId!!).collectAsState(initial = emptyList())
    } else {
        repository.allTranscriptionsFlow.collectAsState(initial = emptyList())
    }

    when (val screen = currentScreen) {
        is MainActivity.Screen.Home -> {
            HomeScreen(
                folders = folders,
                transcriptions = transcriptions,
                selectedFolderId = selectedFolderId,
                searchQuery = searchQuery,
                onFolderSelected = { selectedFolderId = it },
                onSearchQueryChanged = { searchQuery = it },
                onTranscriptionClicked = { currentScreen = MainActivity.Screen.Detail(it.id) },
                onNewRecordingClicked = { currentScreen = MainActivity.Screen.Recording },
                onImportFileClicked = { currentScreen = MainActivity.Screen.FileTranscription },
                onSettingsClicked = { currentScreen = MainActivity.Screen.Settings },
                onCreateFolder = { folderName ->
                    scope.launch {
                        repository.insertFolder(folderName)
                    }
                },
                onDeleteTranscription = { item ->
                    scope.launch {
                        repository.deleteTranscription(item)
                    }
                },
                repository = repository,
                modifier = Modifier.fillMaxSize()
            )
        }

        is MainActivity.Screen.Recording -> {
            RecordingScreen(
                repository = repository,
                folders = folders,
                initialFolderId = selectedFolderId,
                currentAppLanguage = currentAppLanguage,
                appSettings = appSettings,
                onBack = { currentScreen = MainActivity.Screen.Home },
                onSaved = { savedId ->
                    currentScreen = MainActivity.Screen.Detail(savedId)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        is MainActivity.Screen.Detail -> {
            DetailScreen(
                transcriptionId = screen.transcriptionId,
                repository = repository,
                folders = folders,
                onBack = { currentScreen = MainActivity.Screen.Home },
                modifier = Modifier.fillMaxSize()
            )
        }

        is MainActivity.Screen.FileTranscription -> {
            FileTranscriptionScreen(
                repository = repository,
                folders = folders,
                currentAppLanguage = currentAppLanguage,
                appSettings = appSettings,
                onBack = { currentScreen = MainActivity.Screen.Home },
                onTranscriptionCompleted = { savedId ->
                    currentScreen = MainActivity.Screen.Detail(savedId)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        is MainActivity.Screen.Settings -> {
            SettingsScreen(
                currentTheme = currentTheme,
                onThemeSelected = onThemeSelected,
                currentAppLanguage = currentAppLanguage,
                onLanguageSelected = onLanguageSelected,
                groqApiKey = groqApiKey,
                onSaveGroqApiKey = { key ->
                    groqApiKey = key
                    groqStt.saveGroqApiKey(key)
                },
                isGroqEnabled = isGroqDefault,
                onToggleGroqDefault = { enabled ->
                    isGroqDefault = enabled
                    groqStt.setGroqAsDefault(enabled)
                },
                onBack = { currentScreen = MainActivity.Screen.Home },
                appSettings = appSettings,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
