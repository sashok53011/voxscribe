package com.example.modules.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.modules.file_export_manager.FileExportManagerModule.DocumentFormat
import com.example.modules.file_export_manager.FileExportManagerModule.ExportFormatOptions
import com.example.modules.file_export_manager.FileExportManagerModule.ExportedFileItem

/**
 * =========================================================================================
 * КОМПОНЕНТ: ExportManagerDialog (Менеджер сохраненных локально PDF / DOCX файлов)
 * =========================================================================================
 */

/**
 * Диалог просмотра и управления сохраненными экспортированными файлами
 */
@Composable
fun ExportManagerDialog(
    exportedFiles: List<ExportedFileItem>,
    onOpenFile: (ExportedFileItem) -> Unit,
    onShareFile: (ExportedFileItem) -> Unit,
    onDeleteFile: (ExportedFileItem) -> Unit,
    onDismiss: () -> Unit
) {
    var fileToDelete by remember { mutableStateOf<ExportedFileItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Менеджер экспорта файлов",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Сохраненные PDF и DOCX документы",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            if (exportedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Нет экспортированных файлов",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Экспортируйте стенограмму в PDF или DOCX",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exportedFiles) { item ->
                        ExportedFileRow(
                            item = item,
                            onOpen = { onOpenFile(item) },
                            onShare = { onShareFile(item) },
                            onDelete = { fileToDelete = item }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("btn_close_export_manager")) {
                Text("Закрыть")
            }
        }
    )

    // Подтверждение удаления файла
    fileToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Удалить файл?") },
            text = { Text("Файл '${item.fileName}' будет удален из локального хранилища.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(item)
                        fileToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun ExportedFileRow(
    item: ExportedFileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val (badgeColor, badgeText) = when (item.format) {
        DocumentFormat.PDF -> Color(0xFFD32F2F) to "PDF"
        DocumentFormat.DOCX -> Color(0xFF1976D2) to "DOCX"
        DocumentFormat.TXT -> Color(0xFF558B2F) to "TXT"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = badgeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.formattedSize} • ${item.formattedDate}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Открыть",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Диалог настройки форматирования экспорта в PDF или DOCX
 */
@Composable
fun ExportOptionsDialog(
    initialFormat: DocumentFormat = DocumentFormat.PDF,
    onDismiss: () -> Unit,
    onConfirmExport: (format: DocumentFormat, options: ExportFormatOptions) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var includeSpeakerBadges by remember { mutableStateOf(true) }
    var includeTimestamps by remember { mutableStateOf(true) }
    var includeTranslations by remember { mutableStateOf(true) }
    var includeAiSummary by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Экспорт стенограммы", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Формат файла:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedFormat = DocumentFormat.PDF }) {
                        RadioButton(
                            selected = selectedFormat == DocumentFormat.PDF,
                            onClick = { selectedFormat = DocumentFormat.PDF }
                        )
                        Text("PDF", fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedFormat = DocumentFormat.DOCX }) {
                        RadioButton(
                            selected = selectedFormat == DocumentFormat.DOCX,
                            onClick = { selectedFormat = DocumentFormat.DOCX }
                        )
                        Text("Word DOCX", fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedFormat = DocumentFormat.TXT }) {
                        RadioButton(
                            selected = selectedFormat == DocumentFormat.TXT,
                            onClick = { selectedFormat = DocumentFormat.TXT }
                        )
                        Text("TXT", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Параметры оформления:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                OptionCheckboxRow(
                    label = "Имена спикеров и бейджи",
                    checked = includeSpeakerBadges,
                    onCheckedChange = { includeSpeakerBadges = it }
                )

                OptionCheckboxRow(
                    label = "Таймкоды реплик (00:00)",
                    checked = includeTimestamps,
                    onCheckedChange = { includeTimestamps = it }
                )

                OptionCheckboxRow(
                    label = "Перевод на иностранный язык",
                    checked = includeTranslations,
                    onCheckedChange = { includeTranslations = it }
                )

                OptionCheckboxRow(
                    label = "Резюме и задачи ИИ (Gemini / OpenAI)",
                    checked = includeAiSummary,
                    onCheckedChange = { includeAiSummary = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmExport(
                        selectedFormat,
                        ExportFormatOptions(
                            includeSpeakerBadges = includeSpeakerBadges,
                            includeTimestamps = includeTimestamps,
                            includeTranslations = includeTranslations,
                            includeAiSummary = includeAiSummary
                        )
                    )
                },
                modifier = Modifier.testTag("btn_start_export")
            ) {
                Text("Экспортировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun OptionCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp)
    }
}
