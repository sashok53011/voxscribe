package com.example.modules.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerGender
import com.example.modules.speaker_diarization.SpeakerDiarizerModule.SpeakerSegment
import com.example.ui.theme.SpeakerFemale
import com.example.ui.theme.SpeakerMale
import com.example.ui.theme.TranslationTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * МОДУЛЬ: SpeakerSegmentItem (Карточка реплики с распознаванием спикера и переводом)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Отображение отдельной реплики диалога с бейджем спикера (пол, частота тона),
 * оригинальным текстом, синхронным переводом и быстрыми действиями (озвучивание TTS с ducking, копирование).
 */
@Composable
fun SpeakerSegmentItem(
    segment: SpeakerSegment,
    onSpeakClicked: (SpeakerSegment) -> Unit,
    currentPlaybackPositionMs: Long = 0L,
    isPlaybackActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(segment.startTimeMs))

    val (badgeBgColor, badgeIcon) = when (segment.gender) {
        SpeakerGender.MALE -> Pair(SpeakerMale, Icons.Default.Male)
        SpeakerGender.FEMALE -> Pair(SpeakerFemale, Icons.Default.Female)
        SpeakerGender.UNKNOWN -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.Person)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("segment_${segment.segmentId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Верхняя плашка: иконка пола, имя спикера, частота, таймкод
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(badgeBgColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = "Пол спикера",
                            tint = badgeBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = segment.speakerDisplayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (segment.pitchHz > 0) {
                            Text(
                                text = "Тон: ${segment.pitchHz.toInt()} Гц",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Кнопка TTS озвучки с ducking
                    IconButton(
                        onClick = { onSpeakClicked(segment) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("speak_btn_${segment.segmentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Озвучить реплику с приглушением музыки",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Кнопка копирования
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Transcription", segment.text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_btn_${segment.segmentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Скопировать текст",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Оригинальный распознанный текст с пословной подсветкой
            if (segment.words.isNotEmpty()) {
                RealtimeWordHighlightTextView(
                    words = segment.words,
                    fallbackText = segment.text,
                    currentPlaybackPositionMs = currentPlaybackPositionMs,
                    isPlaybackActive = isPlaybackActive,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            } else {
                Text(
                    text = segment.text,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            // Блок перевода (если есть)
            if (segment.translatedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TranslationTeal.copy(alpha = 0.08f))
                        .border(1.dp, TranslationTeal.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Синхронный перевод",
                            tint = TranslationTeal,
                            modifier = Modifier
                                .padding(top = 2.dp, end = 6.dp)
                                .size(14.dp)
                        )
                        Text(
                            text = segment.translatedText,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
