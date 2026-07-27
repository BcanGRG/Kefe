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
            modifier = modifier.fillMaxWidth().height(180.dp),
        )
        return
    }

    val microStyle = type.micro

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val goalTextWidth = measurer.widthOf(goalLabel, microStyle)
        val rightPad = if (goalTextWidth > 0f) goalTextWidth + 10.dp.toPx() else 6.dp.toPx()
        val left = 6.dp.toPx()
        val right = max(left + 1f, size.width - rightPad)
        val plotTop = 18.dp.toPx()
        val plotBottom = size.height - 22.dp.toPx()
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

        // Belirsizlik bandi tahmin cizgisinin altinda kalir.
        if (highPts.size >= 2 && lowPts.size >= 2) {
            drawPath(
                path = areaBetween(highPts, lowPts),
                color = colors.accent,
                alpha = 0.14f,
            )
        }

        // Hedef: kesikli yatay cizgi + sag etiket.
        drawDashedLine(
            color = colors.accent,
            start = Offset(left, goalY),
            end = Offset(right, goalY),
            strokeWidth = ChartDefaults.goalStroke.toPx(),
            on = ChartDefaults.goalDashOn.toPx(),
            off = ChartDefaults.goalDashOff.toPx(),
            alpha = 0.5f,
        )
        drawChartText(
            measurer = measurer,
            text = goalLabel,
            style = microStyle.copy(color = colors.onSurfaceMuted),
            x = right + 6.dp.toPx(),
            y = goalY,
            vAnchor = LabelAnchor.Center,
        )

        if (forecastLine.size >= 2) {
            drawPath(
                path = forecastLine.smoothPath(),
                color = colors.accent,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashEffect(6f, 5f),
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

        // Gecis noktasi = bugun: dikey kesikli cizgi + dolu isaret + etiket.
        if (actualPts.isNotEmpty()) {
            val today = actualPts.last()
            drawDashedLine(
                color = colors.outline,
                start = Offset(today.x, plotTop),
                end = Offset(today.x, plotBottom),
                strokeWidth = ChartDefaults.axisStroke.toPx(),
                on = 4f,
                off = 4f,
            )
            drawCircle(
                color = colors.accent,
                radius = ChartDefaults.markerRadius.toPx(),
                center = Offset(today.x, today.y),
            )
            val labelX = today.x.clampTo(left + 12.dp.toPx(), right - 12.dp.toPx())
            drawChartText(
                measurer = measurer,
                text = todayLabel,
                style = microStyle.copy(color = colors.onSurfaceMuted),
                x = labelX,
                y = plotBottom + 5.dp.toPx(),
                hAnchor = LabelAnchor.Center,
            )
        }
    }
}
