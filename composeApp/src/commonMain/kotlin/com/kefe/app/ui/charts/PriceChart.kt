package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.max

/** Fiyat serisi uzerinde islem isareti. `isBuy` false ise satis. */
@Immutable
data class TradeMarker(val index: Int, val isBuy: Boolean)

/**
 * Fiyat grafigi. Alim ve satim elmas isaretlerle ayrilir; dolu/ici bos ayrimi
 * renkten bagimsiz oldugu icin isaretler renk korlugunde de okunur.
 */
@Composable
fun KefePriceChart(
    points: List<Point>,
    trades: List<TradeMarker> = emptyList(),
    lineColor: Color = KefeTheme.colors.gold,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors

    if (points.size < 2) {
        ChartEmptyState(
            label = "Grafik için en az 2 gün veri gerekli",
            modifier = modifier.fillMaxWidth().height(120.dp),
        )
        return
    }

    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val pad = 8.dp.toPx()
        val left = pad
        val right = max(left + 1f, size.width - pad)
        val top = pad
        val bottom = size.height - pad

        val range = valueRangeOf(points.map { it.y.toDouble() }).padded(0.10)
        val scale = ChartScale(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            range = range,
            count = points.size,
        )
        val pts = points.mapIndexed { i, p -> Point(scale.x(i), scale.y(p.y.toDouble())) }

        drawPath(
            path = areaToBaseline(pts, bottom),
            color = lineColor,
            alpha = 0.10f,
        )
        drawPath(
            path = pts.smoothPath(),
            color = lineColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )

        val half = 4.5.dp.toPx()
        trades.forEach { trade ->
            if (!trade.index.isValidIndexIn(pts.size)) return@forEach
            val p = pts[trade.index]
            val path = diamondPath(p, half, half)
            if (trade.isBuy) {
                drawPath(path = path, color = colors.accent)
            } else {
                drawPath(path = path, color = colors.surface)
                drawPath(
                    path = path,
                    color = colors.onSurfaceMuted,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
