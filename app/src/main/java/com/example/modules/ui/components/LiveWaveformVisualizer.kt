package com.example.modules.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AudioWaveformColor
import kotlin.math.sin

/**
 * =========================================================================================
 * МОДУЛЬ: LiveWaveformVisualizer (Живая визуализация звуковой волны в реальном времени)
 * =========================================================================================
 * 
 * НАЗНАЧЕНИЕ:
 * Рендеринг динамического эквалайзера/осциллографа звука на Canvas с плавной реакцией
 * на уровень RMS громкости входящего микрофона и фоновые колебания.
 * 
 * ВХОДНЫЕ ДАННЫЕ (Inputs):
 * - rmsLevel (Float): Нормализованная амплитуда звука (0.0 .. 1.0).
 * - isRecording (Boolean): Флаг активности записи.
 */
@Composable
fun LiveWaveformVisualizer(
    rmsLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val tealColor = AudioWaveformColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val barCount = 36
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = (totalWidth / barCount) * 0.55f
            val spacing = (totalWidth / barCount) * 0.45f
            val midY = totalHeight / 2f

            for (i in 0 until barCount) {
                val x = i * (barWidth + spacing) + spacing / 2f
                val normalizedIndex = i.toFloat() / barCount
                
                // Модуляция высоты бара синусоидой и входным RMS
                val baseWave = sin(normalizedIndex * 4.0 * Math.PI + phase).toFloat()
                val activeRms = if (isRecording) (rmsLevel.coerceIn(0.1f, 1.0f)) else 0.05f
                val barHeight = ((0.15f + kotlin.math.abs(baseWave) * 0.45f * activeRms) * totalHeight)
                    .coerceIn(6f, totalHeight * 0.88f)

                val barTop = midY - (barHeight / 2f)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(tealColor, primaryColor),
                        startY = barTop,
                        endY = barTop + barHeight
                    ),
                    topLeft = Offset(x, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
    }
}
