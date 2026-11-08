package com.example.modules.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modules.word_timestamps.WordTimestamp

/**
 * =========================================================================================
 * КОМПОНЕНТ: RealtimeWordHighlightTextView (Подсветка произносимых слов в реальном времени)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Отображение текста стенограммы с динамической пословной подсветкой в такт воспроизведению
 * аудиозаписи или синтеза речи TTS.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - words (List<WordTimestamp>): Список слов с миллисекундными таймкодами (startMs, endMs).
 * - fallbackText (String): Текст на случай, если пословная разметка еще не построена.
 * - currentPlaybackPositionMs (Long): Текущая позиция воспроизведения в миллисекундах.
 * - isPlaybackActive (Boolean): Активно ли воспроизведение звука прямо сейчас.
 * - onWordClick ((Long) -> Unit)?: Коллбэк для перемотки плеера при нажатии на конкретное слово.
 * 
 * КАК ИСПОЛЬЗОВАТЬ:
 * ```kotlin
 * RealtimeWordHighlightTextView(
 *     words = segment.words,
 *     fallbackText = segment.text,
 *     currentPlaybackPositionMs = currentAudioMs,
 *     isPlaybackActive = isPlaying,
 *     onWordClick = { seekMs -> audioPlayer.seekTo(seekMs) }
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealtimeWordHighlightTextView(
    words: List<WordTimestamp>,
    fallbackText: String,
    currentPlaybackPositionMs: Long,
    isPlaybackActive: Boolean,
    onWordClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty()) {
        Text(
            text = fallbackText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
        return
    }

    FlowRow(
        modifier = modifier.testTag("word_highlight_container")
    ) {
        for (i in words.indices) {
            val item = words[i]
            val isActive = isPlaybackActive &&
                    currentPlaybackPositionMs >= item.startMs &&
                    currentPlaybackPositionMs <= item.endMs

            val animatedBgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 150),
                label = "word_bg_anim"
            )

            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(durationMillis = 150),
                label = "word_text_anim"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(animatedBgColor)
                    .then(
                        if (onWordClick != null) {
                            Modifier.clickable { onWordClick(item.startMs) }
                        } else Modifier
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag(if (isActive) "active_spoken_word" else "word_token_$i")
            ) {
                Text(
                    text = item.word,
                    fontSize = 15.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                    color = textColor,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
