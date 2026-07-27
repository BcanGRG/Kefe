package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import kotlin.math.round

/**
 * Elle yazilmis kaydirici. Material3 Slider kendi olculerini ve ripple'ini
 * dayattigi icin kanal/knob dogrudan cizilir.
 *
 * `steps` ARALIK sayisidir: 30.000-120.000 arasi 5.000 adim icin steps = 18.
 */

private val TrackHeight = 6.dp
private val KnobSize = 24.dp
private val KnobBorder = 3.dp

@Composable
fun KefeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.touchTarget),
    ) {
        val density = LocalDensity.current
        val travel = (maxWidth - KnobSize).coerceAtLeast(0.dp)
        val knobLeft = travel * fraction
        val filledWidth = knobLeft + KnobSize / 2

        val travelPx = with(density) { travel.toPx() }
        val knobRadiusPx = with(density) { (KnobSize / 2).toPx() }

        // Piksel -> deger: knob merkezinin gezindigi araliga gore oran, sonra adima yuvarlama.
        fun emitAt(xPx: Float) {
            val raw = if (travelPx <= 0f) 0f else ((xPx - knobRadiusPx) / travelPx).coerceIn(0f, 1f)
            val snapped = if (steps > 0) round(raw * steps) / steps else raw
            currentOnValueChange(valueRange.start + snapped * span)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.touchTarget)
                .pointerInput(travelPx, knobRadiusPx, steps, valueRange) {
                    detectTapGestures { offset -> emitAt(offset.x) }
                }
                .pointerInput(travelPx, knobRadiusPx, steps, valueRange) {
                    detectDragGestures(
                        onDragStart = { offset -> emitAt(offset.x) },
                        onDrag = { change, _ ->
                            change.consume()
                            emitAt(change.position.x)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .clip(KefeShapes.pill)
                    .background(colors.surfaceSunken),
            )
            Box(
                modifier = Modifier
                    .width(filledWidth)
                    .height(TrackHeight)
                    .clip(KefeShapes.pill)
                    .background(colors.accent),
            )
            Box(
                modifier = Modifier
                    .offset(x = knobLeft)
                    .size(KnobSize)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(KnobBorder, colors.surfaceElevated, CircleShape),
            )
        }
    }
}
