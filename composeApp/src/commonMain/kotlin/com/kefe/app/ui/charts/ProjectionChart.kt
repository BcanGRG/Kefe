package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.max

/** Tasarimdaki grafik alani: 326 x 178. */
private val ChartHeight = 178.dp

/**
 * Projeksiyon grafigi: gerceklesen seri dolu, tahmin kesikli cizilir ve
 * tahminin etrafinda belirsizlik bandi gosterilir.
 *
 * Kesikli cizgi ve bant opsiyonel degildir - tahmini kesin bir sonucmus gibi
 * gostermek yanlis yonlendirir.
 */
@Composable
fun KefeProjectionChart(
    actual: List<Point>,
    forecast: List<Point>,
    bandLow: List<Point>,
    bandHigh: List<Point>,
    goal: Double,
    goalLabel: String,
    todayLabel: String = "bugün",
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val measurer = rememberTextMeasurer()

    if (actual.size + forecast.size < 2) {
        ChartEmptyState(
            label = "Grafik için en az 2 gün veri gerekli",
            modifier = modifier.fillMaxWidth().height(ChartHeight),
        )
        return
    }

    val microStyle = type.micro

    Canvas(modifier.fillMaxWidth().height(ChartHeight)) {
        val left = 12.dp.toPx()
        val right = max(left + 1f, size.width - 4.dp.toPx())
        val plotTop = 18.dp.toPx()
        // Eksen cizgisi tasarimda 178 yuksekliginde 160'ta durur.
        val plotBottom = size.height - 18.dp.toPx()
        val plotHeight = plotBottom - plotTop

        // Hedef cizgisi ust %12-de sabit; veri bandi altta kalir.
        val goalY = plotTop + plotHeight * 0.12f
        val dataTop = plotTop + plotHeight * 0.28f

        val allValues = buildList {
            actual.forEach { add(it.y.toDouble()) }
            forecast.forEach { add(it.y.toDouble()) }
            bandLow.forEach { add(it.y.toDouble()) }
            bandHigh.forEach { add(it.y.toDouble()) }
        }
        val range = valueRangeOf(allValues).padded(0.08)

        // Gerceklesen ve tahmin arka arkaya tek bir zaman ekseninde durur.
        val totalCount = actual.size + forecast.size
        val scale = ChartScale(
            left = left,
            top = dataTop,
            right = right,
            bottom = plotBottom,
            range = range,
            count = totalCount,
        )

        val actualPts = actual.mapIndexed { i, p -> Point(scale.x(i), scale.y(p.y.toDouble())) }
        val forecastStart = actual.size - 1
        val forecastPts = forecast.mapIndexed { i, p ->
            Point(scale.x(actual.size + i), scale.y(p.y.toDouble()))
        }
        // Tahmin cizgisi gecis noktasindan baslar, kopuk gorunmez.
        val forecastLine = if (actualPts.isNotEmpty()) {
            listOf(actualPts.last()) + forecastPts
        } else {
            forecastPts
        }

        fun bandPoints(list: List<Point>): List<Point> = list.mapIndexed { i, p ->
            Point(scale.x((forecastStart + 1 + i).coerceAtLeast(0)), scale.y(p.y.toDouble()))
        }

        val highPts = bandPoints(bandHigh)
        val lowPts = bandPoints(bandLow)

        // Taban ekseni
        drawLine(
            color = colors.outline,
            start = Offset(left, plotBottom),
            end = Offset(right, plotBottom),
            strokeWidth = ChartDefaults.axisStroke.toPx(),
        )

        // Belirsizlik bandi tahmin cizgisinin altinda kalir.
        if (highPts.size >= 2 && lowPts.size >= 2) {
            drawPath(
                path = areaBetween(highPts, lowPts),
                color = colors.accent,
                alpha = 0.14f,
            )
        }

        // Hedef: kesikli yatay cizgi + cizginin ustunde sola hizali etiket.
        drawDashedLine(
            color = colors.accent,
            start = Offset(left, goalY),
            end = Offset(right, goalY),
            strokeWidth = ChartDefaults.goalStroke.toPx(),
            on = ChartDefaults.goalDashOn.toPx(),
            off = ChartDefaults.goalDashOff.toPx(),
            alpha = 0.75f,
        )
        drawChartText(
            measurer = measurer,
            text = goalLabel,
            style = microStyle.copy(color = colors.accent),
            x = left,
            y = goalY - 5.dp.toPx(),
            vAnchor = LabelAnchor.End,
        )

        if (forecastLine.size >= 2) {
            drawPath(
                path = forecastLine.smoothPath(),
                color = colors.accent,
                // Tahmin cizgisi gerceklesenden daha ince ve kesiklidir.
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect(6.dp.toPx(), 5.dp.toPx()),
                ),
            )
        }

        val actualLine = actualPts.ensureSegment(left, scale.x(max(0, forecastStart)))
        if (actualLine.size >= 2) {
            drawPath(
                path = actualLine.smoothPath(),
                color = colors.accent,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Gecis noktasi = bugun: dikey kesikli cizgi + halka isaret + yan etiket.
        if (actualPts.isNotEmpty()) {
            val today = actualPts.last()
            // Cizgi hedef cizgisinin altindan baslar, ustune binmez.
            val todayTop = goalY + 10.dp.toPx()
            drawDashedLine(
                color = colors.onSurfaceMuted,
                start = Offset(today.x, todayTop),
                end = Offset(today.x, plotBottom),
                strokeWidth = ChartDefaults.axisStroke.toPx(),
                on = 3.dp.toPx(),
                off = 4.dp.toPx(),
                alpha = 0.6f,
            )
            drawCircle(
                color = colors.surfaceElevated,
                radius = ChartDefaults.markerRadius.toPx(),
                center = Offset(today.x, today.y),
            )
            drawCircle(
                color = colors.accent,
                radius = ChartDefaults.markerRadius.toPx(),
                center = Offset(today.x, today.y),
                style = Stroke(width = ChartDefaults.markerStroke.toPx()),
            )
            // Etiket cizginin sagina yazilir; sigmazsa soluna gecer.
            val gap = 6.dp.toPx()
            val labelWidth = measurer.widthOf(todayLabel, microStyle)
            val fitsRight = today.x + gap + labelWidth <= right
            drawChartText(
                measurer = measurer,
                text = todayLabel,
                style = microStyle.copy(color = colors.onSurfaceMuted),
                x = if (fitsRight) today.x + gap else today.x - gap,
                y = todayTop + 3.dp.toPx(),
                hAnchor = if (fitsRight) LabelAnchor.Start else LabelAnchor.End,
            )
        }
    }
}
