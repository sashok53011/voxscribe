package com.example.modules.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.modules.file_export_manager.FileExportManagerModule
import com.example.modules.file_export_manager.FileExportManagerModule.DocumentFormat
import com.example.modules.file_export_manager.FileExportManagerModule.ExportFormatOptions
import com.example.modules.file_export_manager.FileExportManagerModule.ExportedFileItem
import com.example.modules.file_management.FileManagementModule
import com.example.modules.file_management.FileManagementModule.FolderWithStats
import com.example.modules.file_management.FileManagementModule.SortOption
import com.example.modules.storage_search.FolderEntity
import com.example.modules.storage_search.TranscriptionEntity
import com.example.modules.storage_search.TranscriptionRepository
import com.example.modules.ui.components.CreateOrEditFolderDialog
import com.example.modules.ui.components.ExportManagerDialog
import com.example.modules.ui.components.ExportOptionsDialog
import com.example.modules.ui.components.FolderManagerDialog
import com.example.modules.ui.components.MoveToFolderDialog
import com.example.ui.theme.TranslationTeal
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: HomeScreen (Главный экран: папки Room, менеджер файлов, экспорт PDF/DOCX)
 * =========================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    folders: List<FolderEntity>,
    transcriptions: List<TranscriptionEntity>,
    selectedFolderId: String?,
    searchQuery: String,
    onFolderSelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTranscriptionClicked: (TranscriptionEntity) -> Unit,
    onNewRecordingClicked: () -> Unit,
    onImportFileClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteTranscription: (TranscriptionEntity) -> Unit,
    repository: TranscriptionRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileManager = remember { FileManagementModule(repository) }
    val exportManager = remember { FileExportManagerModule(context) }

    // Состояния диалогов
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }
    var showFolderManagerDialog by remember { mutableStateOf(false) }
    var foldersWithStats by remember { mutableStateOf<List<FolderWithStats>>(emptyList()) }

    var itemToMove by remember { mutableStateOf<TranscriptionEntity?>(null) }
    var itemToExport by remember { mutableStateOf<TranscriptionEntity?>(null) }

    var showExportManagerDialog by remember { mutableStateOf(false) }
    var exportedFilesList by remember { mutableStateOf<List<ExportedFileItem>>(emptyList()) }

    var currentSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Загрузка статистики папок при открытии диалога управления
    fun refreshFolderStats() {
        scope.launch {
            foldersWithStats = fileManager.getAllFoldersWithStats()
        }
    }

    // Загрузка экспортированных файлов
    fun refreshExportedFiles() {
        scope.launch {
            exportedFilesList = exportManager.getAllExportedFiles()
        }
    }

    // Применение сортировки
    val sortedTranscriptions = remember(transcriptions, currentSortOption) {
        fileManager.sortTranscriptions(transcriptions, currentSortOption)
    }

    Scaffold(
        modifier = modifier.testTag("home_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VoxScribe",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Оффлайн транскрибатор",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Кнопка открытия менеджера сохраненных локально файлов экспорта
                    IconButton(
                        onClick = {
                            refreshExportedFiles()
                            showExportManagerDialog = true
                        },
                        modifier = Modifier.testTag("btn_top_export_manager")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Экспортированные файлы",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Кнопка сортировки
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("btn_sort_transcriptions")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Сортировка",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = opt.titleRu,
                                            fontWeight = if (currentSortOption == opt) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        currentSortOption = opt
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Кнопка перехода в настройки
                    IconButton(
                        onClick = onSettingsClicked,
                        modifier = Modifier.testTag("btn_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Вторичная кнопка импорта файла
                ExtendedFloatingActionButton(
                    onClick = onImportFileClicked,
                    icon = { Icon(Icons.Default.AudioFile, contentDescription = null) },
                    text = { Text("Импорт файла") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp).testTag("fab_import_file")
                )

                // Главная кнопка записи с микрофона
                ExtendedFloatingActionButton(
                    onClick = onNewRecordingClicked,
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    text = { Text("Запись речи") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_record")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Поле живого поиска
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Поиск по тексту, заголовку, переводу...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_input")
            )

            // Горизонтальный список папок и действия управления
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Чип "Все записи"
                FilterChip(
                    selected = selectedFolderId == null,
                    onClick = { onFolderSelected(null) },
                    label = { Text("Все (${transcriptions.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.padding(end = 8.dp).testTag("folder_chip_all")
                )

                for (folder in folders) {
                    val isSelected = selectedFolderId == folder.id
                    val folderColor = try { Color(android.graphics.Color.parseColor(folder.colorHex)) } catch (e: Exception) { Color.Gray }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFolderSelected(if (isSelected) null else folder.id) },
                        label = { Text(folder.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(folderColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(end = 8.dp).testTag("folder_chip_${folder.id}")
                    )
                }

                // Кнопка быстрого создания новой папки
                IconButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("btn_add_folder")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Создать папку",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Кнопка диалога расширенного управления папками (статистика, удаление)
                IconButton(
                    onClick = {
                        refreshFolderStats()
                        showFolderManagerDialog = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("btn_manage_folders")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Управление папками",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Список стенограмм или заглушка пустого состояния
            if (sortedTranscriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Ничего не найдено" else "Нет сохраненных стенограмм",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Попробуйте изменить поисковый запрос" else "Нажмите 'Запись речи' или импортируйте аудиофайл",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedTranscriptions, key = { it.id }) { item ->
                        val folder = folders.firstOrNull { it.id == item.folderId }
                        val folderName = folder?.name ?: "Без категории"
                        val folderColorHex = folder?.colorHex ?: "#1A237E"

                        TranscriptionCard(
                            item = item,
                            folderName = folderName,
                            folderColorHex = folderColorHex,
                            onClick = { onTranscriptionClicked(item) },
                            onDelete = { onDeleteTranscription(item) },
                            onMoveToFolder = { itemToMove = item },
                            onExportCustom = { itemToExport = item }
                        )
                    }
                }
            }
        }
    }

    // Диалог создания папки
    if (showCreateFolderDialog) {
        CreateOrEditFolderDialog(
            initialFolder = null,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color, icon ->
                showCreateFolderDialog = false
                scope.launch {
                    fileManager.createFolder(name, color, icon)
                }
            }
        )
    }

    // Диалог редактирования папки
    folderToEdit?.let { targetFolder ->
        CreateOrEditFolderDialog(
            initialFolder = targetFolder,
            onDismiss = { folderToEdit = null },
            onConfirm = { name, color, icon ->
                folderToEdit = null
                scope.launch {
                    fileManager.updateFolder(targetFolder.id, name, color, icon)
                    refreshFolderStats()
                }
            }
        )
    }

    // Диалог управления всеми папками (статистика и удаление)
    if (showFolderManagerDialog) {
        FolderManagerDialog(
            foldersWithStats = foldersWithStats,
            onDismiss = { showFolderManagerDialog = false },
            onCreateNew = {
                showFolderManagerDialog = false
                showCreateFolderDialog = true
            },
            onEditFolder = { folder ->
                folderToEdit = folder
            },
            onDeleteFolder = { folder ->
                scope.launch {
                    val fallback = folders.firstOrNull { it.id != folder.id }?.id
                    fileManager.deleteFolder(folder.id, fallback)
                    refreshFolderStats()
                }
            }
        )
    }

    // Диалог перемещения записи в другую папку
    itemToMove?.let { item ->
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = item.folderId,
            onDismiss = { itemToMove = null },
            onSelectFolder = { newFolderId ->
                itemToMove = null
                scope.launch {
                    fileManager.moveTranscription(item.id, newFolderId)
                    Toast.makeText(context, "Запись перемещена в новую папку", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Диалог форматированного экспорта
    itemToExport?.let { item ->
        val folder = folders.firstOrNull { it.id == item.folderId }
        val folderName = folder?.name ?: "Общее"
        val segments = repository.deserializeSegments(item.segmentsJson)

        ExportOptionsDialog(
            initialFormat = DocumentFormat.PDF,
            onDismiss = { itemToExport = null },
            onConfirmExport = { format, options ->
                itemToExport = null
                scope.launch {
                    val exported = when (format) {
                        DocumentFormat.PDF -> exportManager.exportToFormattedPdf(item, folderName, segments, null, options)
                        DocumentFormat.DOCX -> exportManager.exportToFormattedDocx(item, folderName, segments, null, options)
                        DocumentFormat.TXT -> exportManager.exportToFormattedTxt(item, segments)
                    }
                    Toast.makeText(context, "Сохранено: ${exported.fileName}", Toast.LENGTH_SHORT).show()
                    exportManager.openFile(context, exported)
                }
            }
        )
    }

    // Диалог просмотра сохраненных файлов экспорта
    if (showExportManagerDialog) {
        ExportManagerDialog(
            exportedFiles = exportedFilesList,
            onOpenFile = { fileItem -> exportManager.openFile(context, fileItem) },
            onShareFile = { fileItem -> exportManager.shareFile(context, fileItem) },
            onDeleteFile = { fileItem ->
                exportManager.deleteExportedFile(fileItem)
                refreshExportedFiles()
            },
            onDismiss = { showExportManagerDialog = false }
        )
    }
}

@Composable
fun TranscriptionCard(
    item: TranscriptionEntity,
    folderName: String,
    folderColorHex: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit,
    onExportCustom: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(item.createdAt))
    val badgeColor = try { Color(android.graphics.Color.parseColor(folderColorHex)) } catch (e: Exception) { Color.DarkGray }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("card_transcription_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Бейдж папки и источника
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = folderName,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (item.audioSourceType == "FILE") "Файл" else "Микрофон",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp).testTag("menu_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Действия",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Форматированный экспорт...") },
                            onClick = {
                                menuExpanded = false
                                onExportCustom()
                            },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Переместить в папку...") },
                            onClick = {
                                menuExpanded = false
                                onMoveToFolder()
                            },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Заголовок
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Фрагмент текста
            Text(
                text = item.fullText.ifEmpty { "Нет реплик" },
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Нижняя строка: спикеры, дата, бейдж перевода
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Спикеров: ${item.speakerCount}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (item.translatedFullText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = TranslationTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Перевод: ${item.targetLanguageCode.uppercase()}",
                                fontSize = 11.sp,
                                color = TranslationTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
