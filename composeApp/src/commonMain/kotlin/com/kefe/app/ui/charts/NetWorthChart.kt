package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Net deger grafigi: toplam ve anapara cizgileri, aradaki alan getiriyi gosterir.
 *
 * Hedef cizgisi veri bandinin cok uzerinde kalabildigi icin olcek kirilir:
 * veri alt banda, hedef ust banda yerlesir; arada kirik eksen isareti cizilir.
 * Bu kirilma gorsel olarak isaretlenmeden yapilamaz, aksi halde grafik
 * hedefe olan mesafeyi oldugundan kucuk gosterir.
 */
@Composable
fun KefeNetWorthChart(
    total: List<Point>,
    principal: List<Point>,
    goalLine: Double? = null,
    goalLabel: String? = null,
    selectedIndex: Int? = null,
    selectedLabel: String? = null,
    selectedValue: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val measurer = rememberTextMeasurer()

    if (total.size < 2) {
        ChartEmptyState(
            label = "Grafik için en az 2 gün veri gerekli",
            modifier = modifier.fillMaxWidth().height(160.dp),
        )
        return
    }

    val microStyle = type.micro
    val captionStyle = type.caption

    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val goalTextWidth = if (goalLabel != null && goalLine != null) {
            measurer.widthOf(goalLabel, microStyle)
        } else {
            0f
        }
        val rightPad = if (goalTextWidth > 0f) goalTextWidth + 10.dp.toPx() else 4.dp.toPx()
        val left = 14.dp.toPx()
        val right = max(left + 1f, size.width - rightPad)
        val topPad = 20.dp.toPx()
        val bottomPad = 10.dp.toPx()
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotHeight = plotBottom - plotTop

        // Hedef varsa: veri bandi alt %70, hedef cizgisi ust %15 hizasinda.
        val hasGoal = goalLine != null
        val dataTop = if (hasGoal) plotTop + plotHeight * 0.30f else plotTop
        val goalY = if (hasGoal) plotTop + plotHeight * 0.15f else plotTop

        val values = buildList {
            total.forEach { add(it.y.toDouble()) }
            principal.forEach { add(it.y.toDouble()) }
        }
        val range = valueRangeOf(values).padded(0.08)
        val scale = ChartScale(
            left = left,
            top = dataTop,
            right = right,
            bottom = plotBottom,
            range = range,
            count = total.size,
        )

        val totalPts = total.mapIndexed { i, p -> Point(scale.x(i), scale.y(p.y.toDouble())) }
        val principalPts = principal
            .mapIndexed { i, p -> Point(scale.x(i), scale.y(p.y.toDouble())) }
            .ensureSegment(left, right)

        // Getiri alani: toplam ile anapara arasinda kalan bolge.
        if (principalPts.size == totalPts.size && totalPts.size >= 2) {
            drawPath(
                path = areaBetween(totalPts, principalPts),
                color = colors.accent,
                alpha = 0.18f,
            )
        }

        if (principalPts.size >= 2) {
            drawPath(
                path = principalPts.smoothPath(),
                color = colors.onSurfaceMuted,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawPath(
            path = totalPts.smoothPath(),
            color = colors.accent,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )

        if (hasGoal) {
            drawDashedLine(
                color = colors.accent,
                start = Offset(left, goalY),
                end = Offset(right, goalY),
                strokeWidth = ChartDefaults.goalStroke.toPx(),
                on = ChartDefaults.goalDashOn.toPx(),
                off = ChartDefaults.goalDashOff.toPx(),
                alpha = 0.5f,
            )
            if (goalLabel != null) {
                drawChartText(
                    measurer = measurer,
                    text = goalLabel,
                    style = microStyle.copy(color = colors.onSurfaceMuted),
                    x = right + 6.dp.toPx(),
                    y = goalY,
                    vAnchor = LabelAnchor.Center,
                )
            }
            // Kirik eksen isareti: hedef ile veri bandi arasindaki bosluga.
            drawAxisBreak(
                x = left,
                y = (goalY + dataTop) / 2f,
                length = 8.dp.toPx(),
                stroke = ChartDefaults.axisStroke.toPx(),
                color = colors.onSurfaceMuted,
            )
        }

        // Cizgide surekli nokta gosterilmez; yalnizca secili nokta isaretlenir.
        val idx = selectedIndex
        if (idx != null && idx.isValidIndexIn(totalPts.size)) {
            val p = totalPts[idx]
            drawCircle(
                color = colors.accent,
                radius = ChartDefaults.markerRadius.toPx(),
                center = Offset(p.x, p.y),
            )
            drawSelectionBubble(
                measurer = measurer,
                anchorX = p.x,
                anchorY = p.y,
                boundsLeft = left,
                boundsRight = right,
                boundsTop = plotTop,
                labelText = selectedLabel,
                valueText = selectedValue,
                labelStyle = microStyle.copy(color = colors.onSurfaceMuted),
                valueStyle = captionStyle.copy(color = colors.onSurface),
                bg = colors.surfaceElevated,
                border = colors.outline,
            )
        }
    }
}

/** Iki kucuk egik paralel cizgi - olcegin kirildigini bildirir. */
private fun DrawScope.drawAxisBreak(
    x: Float,
    y: Float,
    length: Float,
    stroke: Float,
    color: Color,
) {
    // ~30 derece egim: yatay bilesen dikeyin yaklasik 1.7 kati.
    val dy = length / 2f
    val dx = dy * 1.73f
    val gap = 5f
    listOf(-gap, gap).forEach { off ->
        drawLine(
            color = color,
            start = Offset(x - dx / 2f, y + dy + off),
            end = Offset(x + dx / 2f, y - dy + off),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = 0.7f,
        )
    }
}

/** Secili nokta balonu: iki satir metin, yuzey tonu farkiyla yukseltilmis kutu. */
private fun DrawScope.drawSelectionBubble(
    measurer: TextMeasurer,
    anchorX: Float,
    anchorY: Float,
    boundsLeft: Float,
    boundsRight: Float,
    boundsTop: Float,
    labelText: String?,
    valueText: String?,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    bg: Color,
    border: Color,
) {
    if (labelText.isNullOrEmpty() && valueText.isNullOrEmpty()) return
    val padH = 8.dp.toPx()
    val padV = 6.dp.toPx()
    val labelLayout = labelText?.takeIf { it.isNotEmpty() }?.let { measurer.measure(it, labelStyle) }
    val valueLayout = valueText?.takeIf { it.isNotEmpty() }?.let { measurer.measure(it, valueStyle) }
    val lineGap = if (labelLayout != null && valueLayout != null) 2.dp.toPx() else 0f
    val contentW = max(
        labelLayout?.size?.width?.toFloat() ?: 0f,
        valueLayout?.size?.width?.toFloat() ?: 0f,
    )
    val contentH = (labelLayout?.size?.height?.toFloat() ?: 0f) +
        (valueLayout?.size?.height?.toFloat() ?: 0f) + lineGap
    val boxW = contentW + padH * 2f
    val boxH = contentH + padV * 2f

    val rawLeft = anchorX - boxW / 2f
    val boxLeft = rawLeft.clampTo(boundsLeft, max(boundsLeft, boundsRight - boxW))
    val desiredTop = anchorY - 12.dp.toPx() - boxH
    val boxTop = max(boundsTop - 16.dp.toPx(), min(desiredTop, anchorY))

    val radius = CornerRadius(10.dp.toPx())
    drawRoundRect(
        color = bg,
        topLeft = Offset(boxLeft, boxTop),
        size = Size(boxW, boxH),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = border,
        topLeft = Offset(boxLeft, boxTop),
        size = Size(boxW, boxH),
        cornerRadius = radius,
        style = Stroke(width = 1.dp.toPx()),
    )

    var cy = boxTop + padV
    if (labelLayout != null) {
        drawText(textLayoutResult = labelLayout, topLeft = Offset(boxLeft + padH, cy))
        cy += labelLayout.size.height + lineGap
    }
    if (valueLayout != null) {
        drawText(textLayoutResult = valueLayout, topLeft = Offset(boxLeft + padH, cy))
    }
}
