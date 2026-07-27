package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.tabular

/**
 * Varlik dagilimi halkasi - KAPALI ring. Hedef ilerlemesi icin
 * [KefeGoalRing] kullanilir; ikisi gorsel olarak birbirine benzememelidir.
 */

/** Halkanin tek dilimi. [value] mutlak tutardir, yuzde iceride hesaplanir. */
@Immutable
data class DonutSlice(val label: String, val value: Double, val color: Color)

/** Referans tasarimin cizim kutusu - tum olculer bu kutuya goredir. */
private const val VIEWPORT = 148f

/** Halka yaricapi ve kalinligi (viewport birimi). */
private const val RADIUS = 48f
private const val STROKE = 14f

/** Dilimler arasi bosluk - cevre birimi cinsinden. */
private const val GAP_LENGTH = 1.5f

/** Cevre = 2 * PI * 48 = 301,59 */
private val CIRCUMFERENCE = 2f * kotlin.math.PI.toFloat() * RADIUS

/** Bosluk dusuldukten sonra da gorunur kalmasi gereken en kucuk supurme. */
private const val MIN_SWEEP_DEG = 0.6f

/** En fazla bu kadar dilim cizilir; artan degerler tek dilimde toplanir. */
const val DonutMaxSlices: Int = 5

/**
 * Dilim sayisini [maxSlices] ile sinirlar: en buyuk (maxSlices - 1) dilim
 * korunur, kalanlar tek bir toplu dilimde birlestirilir. Cok sayida kucuk
 * dilim halkayi okunmaz hale getirdigi icin gereklidir.
 */
fun collapseDonutSlices(
    slices: List<DonutSlice>,
    otherColor: Color,
    maxSlices: Int = DonutMaxSlices,
    otherLabel: String = "Diğer",
): List<DonutSlice> {
    val positive = slices.filter { it.value > 0.0 }
    if (positive.size <= maxSlices) return positive
    val sorted = positive.sortedByDescending { it.value }
    val kept = sorted.take(maxSlices - 1)
    val rest = sorted.drop(maxSlices - 1).sumOf { it.value }
    return kept + DonutSlice(otherLabel, rest, otherColor)
}

/**
 * Varlik dagilimi halkasi.
 *
 * Dilim uzunlugu = (deger orani * cevre) - dilim boslugu; boylece boslugun
 * tamami dilimin sonundan dusulur ve oranlar cevrede birebir okunur.
 */
@Composable
fun KefeDonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
) {
    val colors = KefeTheme.colors
    val drawn = collapseDonutSlices(slices, colors.onSurfaceMuted)
    val total = drawn.sumOf { it.value }
    val empty = drawn.isEmpty() || total <= 0.0

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = kotlin.math.min(this.size.width, this.size.height) / VIEWPORT
            val r = RADIUS * scale
            val stroke = Stroke(width = STROKE * scale, cap = StrokeCap.Butt)
            val topLeft = Offset(
                x = this.size.width / 2f - r,
                y = this.size.height / 2f - r,
            )
            val box = Size(r * 2f, r * 2f)

            if (empty) {
                drawArc(
                    color = colors.outline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = stroke,
                )
                return@Canvas
            }

            // Bosluk aci karsiligi: 1,5 cevre birimi -> derece
            val gapDeg = GAP_LENGTH / CIRCUMFERENCE * 360f
            var cursor = -90f
            drawn.forEach { slice ->
                val fraction = (slice.value / total).toFloat()
                val full = fraction * 360f
                val sweep = (full - gapDeg).coerceAtLeast(MIN_SWEEP_DEG)
                drawArc(
                    color = slice.color,
                    startAngle = cursor,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = box,
                    style = stroke,
                )
                cursor += full
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.padding(horizontal = (STROKE + 8).dp),
        ) {
            Text(
                text = centerLabel,
                style = KefeTheme.type.caption,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (empty) "—" else centerValue,
                style = KefeTheme.type.h2.tabular(),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
