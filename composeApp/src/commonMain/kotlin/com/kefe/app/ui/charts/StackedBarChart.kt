package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.AssetClassColor
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.abs
import kotlin.math.max

/** Bir ay icin yigilmis bar. `segments` bos ise o ay katkisizdir. */
@Immutable
data class MonthBar(
    val label: String,
    val segments: List<BarSegment>,
    val note: String? = null,
)

/** Yigilmis barin tek dilimi; renk varlik sinifindan gelir. */
@Immutable
data class BarSegment(val colorKey: AssetClassColor, val value: Double)

/**
 * Aylik katki grafigi. Katkisiz aylar suclayici bir dille degil, notr bir
 * taban cizgisiyle gosterilir - eksik ay bir hata degil, sadece bos veridir.
 */
@Composable
fun KefeStackedBarChart(
    months: List<MonthBar>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 18.dp,
    step: Dp = 26.dp,
    height: Dp = 150.dp,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val measurer = rememberTextMeasurer()

    if (months.isEmpty()) {
        ChartEmptyState(
            label = "Henüz katkı verisi yok",
            modifier = modifier.fillMaxWidth().height(height),
        )
        return
    }

    val microStyle = type.micro
    val positiveSums = months.map { m -> m.segments.filter { it.value > 0.0 }.sumOf { it.value } }
    val negativeSums = months.map { m -> m.segments.filter { it.value < 0.0 }.sumOf { -it.value } }
    val maxUp = positiveSums.maxOrNull() ?: 0.0
    val maxDown = negativeSums.maxOrNull() ?: 0.0

    Canvas(modifier.fillMaxWidth().height(height)) {
        val stepPx = step.toPx()
        val barPx = barWidth.toPx()
        val labelBand = 16.dp.toPx()
        val plotTop = 4.dp.toPx()
        val plotBottom = size.height - labelBand
        val plotHeight = max(1f, plotBottom - plotTop)

        // Sifir cizgisi negatif veri oraninda asagi kayar.
        val total = maxUp + maxDown
        val downShare = if (total > 0.0) (maxDown / total).toFloat() else 0f
        val zeroY = plotBottom - plotHeight * downShare.coerceIn(0f, 0.5f)
        val upHeight = zeroY - plotTop
        val downHeight = plotBottom - zeroY

        fun upPx(value: Double): Float =
            if (maxUp <= 0.0) 0f else (value / maxUp).toFloat() * upHeight

        fun downPx(value: Double): Float =
            if (maxDown <= 0.0) 0f else (value / maxDown).toFloat() * downHeight

        val startX = 0f
        val radius = CornerRadius(2.dp.toPx())

        months.forEachIndexed { index, month ->
            val cx = startX + stepPx * index + barPx / 2f
            if (cx + barPx / 2f > size.width) return@forEachIndexed
            val barLeft = cx - barPx / 2f

            if (month.segments.isEmpty()) {
                // Katkisiz ay: bar yok, yerinde notr taban cizgisi.
                drawRoundRect(
                    color = colors.surfaceSunken,
                    topLeft = Offset(barLeft, zeroY - 1.dp.toPx()),
                    size = Size(barPx, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            } else {
                var topCursor = zeroY
                month.segments.filter { it.value > 0.0 }.forEach { seg ->
                    val h = upPx(seg.value)
                    if (h > 0f) {
                        topCursor -= h
                        drawRoundRect(
                            color = colors.assetClass(seg.colorKey),
                            topLeft = Offset(barLeft, topCursor),
                            size = Size(barPx, h),
                            cornerRadius = radius,
                        )
                    }
                }
                var bottomCursor = zeroY
                month.segments.filter { it.value < 0.0 }.forEach { seg ->
                    val h = downPx(abs(seg.value))
                    if (h > 0f) {
                        drawRoundRect(
                            color = colors.assetClass(seg.colorKey),
                            topLeft = Offset(barLeft, bottomCursor),
                            size = Size(barPx, h),
                            cornerRadius = radius,
                        )
                        bottomCursor += h
                    }
                }
            }

            // Etiketler sigmazsa her ikinci ay gosterilir.
            val labelEvery = if (stepPx < 34.dp.toPx()) 2 else 1
            if (index % labelEvery == 0) {
                drawChartText(
                    measurer = measurer,
                    text = month.label,
                    style = microStyle.copy(
                        color = colors.onSurfaceMuted.copy(
                            alpha = if (month.segments.isEmpty()) 0.6f else 1f,
                        ),
                    ),
                    x = cx,
                    y = plotBottom + 3.dp.toPx(),
                    hAnchor = LabelAnchor.Center,
                )
            }
        }

        // Sifir cizgisi barlarin uzerinde net durur.
        drawLine(
            color = colors.outline,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = ChartDefaults.axisStroke.toPx(),
        )
    }
}
