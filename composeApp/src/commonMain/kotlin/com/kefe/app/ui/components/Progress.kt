package com.kefe.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes

/**
 * Ilerleme cubuklari. Canvas ile cizilir: pill kose yaricapi cubuk
 * yuksekligine bagli oldugu icin tek yerde hesaplanir.
 */

private const val ProgressAnimationMs = 320
private val MilestoneWidth = 2.dp

@Composable
fun KefeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.progressBar,
    color: Color = KefeTheme.colors.accent,
    trackColor: Color = KefeTheme.colors.surfaceSunken,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = ProgressAnimationMs),
        label = "progress",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        drawProgressTrack(animated, color, trackColor)
    }
}

@Composable
fun KefeProgressBarThin(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.progressBarThin,
    color: Color = KefeTheme.colors.accent,
    trackColor: Color = KefeTheme.colors.surfaceSunken,
) {
    KefeProgressBar(
        progress = progress,
        modifier = modifier,
        height = height,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * Kilometre tasli ilerleme cubugu. Centikler cubugu keserek gecilen ve
 * kalan esikleri gosterir - renk tek basina sinyal degildir.
 */
@Composable
fun KefeMilestoneBar(
    progress: Float,
    modifier: Modifier = Modifier,
    milestones: List<Float> = listOf(0.25f, 0.5f, 0.75f),
    height: Dp = Sizes.progressBar,
    color: Color = KefeTheme.colors.accent,
    trackColor: Color = KefeTheme.colors.surfaceSunken,
    milestoneColor: Color = KefeTheme.colors.surface,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = ProgressAnimationMs),
        label = "milestoneProgress",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        drawProgressTrack(animated, color, trackColor)

        val notchWidth = MilestoneWidth.toPx()
        milestones.forEach { milestone ->
            val center = size.width * milestone.coerceIn(0f, 1f)
            val left = (center - notchWidth / 2f)
                .coerceIn(0f, (size.width - notchWidth).coerceAtLeast(0f))
            drawRect(
                color = milestoneColor,
                topLeft = Offset(left, 0f),
                size = Size(notchWidth, size.height),
            )
        }
    }
}

/** Zemin + dolu kisim. Dolu kisim en az bir yuksekligi kadar genis olur ki pill sekli bozulmasin. */
private fun DrawScope.drawProgressTrack(
    fraction: Float,
    color: Color,
    trackColor: Color,
) {
    val radius = CornerRadius(size.height / 2f)
    drawRoundRect(color = trackColor, cornerRadius = radius)
    if (fraction > 0f) {
        val minFilled = kotlin.math.min(size.height, size.width)
        val filled = (size.width * fraction).coerceIn(minFilled, size.width)
        drawRoundRect(
            color = color,
            size = Size(filled, size.height),
            cornerRadius = radius,
        )
    }
}
