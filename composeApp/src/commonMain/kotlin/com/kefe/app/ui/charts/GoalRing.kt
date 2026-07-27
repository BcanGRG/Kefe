package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.tabular
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Kefe kavisi - hedef ilerlemesi halkasi.
 *
 * Bilincli olarak ACIK bir yaydir: mil ve kiris ile birlikte bir terazi kefesi
 * okunur. Dagilim halkasi ([KefeDonutChart]) kapali ringdir; ikisinin gorsel
 * olarak ayrisabilmesi icin mil ve kiris atlanamaz.
 */

// --- Referans cizim uzayi (200 x 150) ---

private const val VIEW_W = 200f
private const val VIEW_H = 150f

/** Mil: dikey cizgi. */
private const val MAST_X = 100f
private const val MAST_TOP = 8f

/** Kiris: yatay cizgi; kase yayinin iki ucunu birlestirir. */
private const val BEAM_Y = 38f
private const val BEAM_LEFT = 39.9f
private const val BEAM_RIGHT = 160.1f

/** Kase yayi: M 39.9 38.1 A 64 64 0 1 0 160.1 38.1 */
private const val ARC_START_X = 39.9f
private const val ARC_END_X = 160.1f
private const val ARC_Y = 38.1f
private const val ARC_RADIUS = 64f
private const val ARC_STROKE = 14f

private const val LINE_STROKE = 2f

/** Centik genisligi - yay uzerinde 2 birimlik radyal ayirici. */
private const val NOTCH_WIDTH = 2f

private val DEG = 180f / kotlin.math.PI.toFloat()

/** Yay merkezi: kirisin altinda kalir, boylece yay asagi dogru bombelenir. */
private val arcCenterX = (ARC_START_X + ARC_END_X) / 2f
private val arcCenterY = ARC_Y + sqrt(
    ARC_RADIUS * ARC_RADIUS - ((ARC_END_X - ARC_START_X) / 2f) * ((ARC_END_X - ARC_START_X) / 2f),
)

/** Sol uctan (yaklasik 200 derece) baslanir. */
private val arcStartAngle = atan2(ARC_Y - arcCenterY, ARC_START_X - arcCenterX) * DEG

/** Toplam supurme ~ -220 derece: saat yonunun tersine, sol ustten asagi. */
private val arcSweepAngle = run {
    val end = atan2(ARC_Y - arcCenterY, ARC_END_X - arcCenterX) * DEG
    var delta = end - arcStartAngle
    while (delta < 0f) delta += 360f
    while (delta >= 360f) delta -= 360f
    // Kucuk yay kubbe olur; kase icin ters yondeki buyuk yay (~220 derece) alinir.
    delta - 360f
}

/**
 * Hedef ilerlemesi halkasi.
 *
 * @param progress 0..1 arasi doluluk.
 * @param milestones yay uzerinde ayirici cizilecek oranlar.
 */
@Composable
fun KefeGoalRing(
    progress: Float,
    centerPercent: String,
    centerAmount: String,
    modifier: Modifier = Modifier,
    color: Color = KefeTheme.colors.accent,
    trackColor: Color = KefeTheme.colors.surfaceSunken,
    milestones: List<Float> = listOf(.25f, .5f, .75f),
) {
    val colors = KefeTheme.colors
    val value = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.width(230.dp).height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = kotlin.math.min(size.width / VIEW_W, size.height / VIEW_H)
            val dx = (size.width - VIEW_W * scale) / 2f
            val dy = (size.height - VIEW_H * scale) / 2f
            fun px(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)

            val lineStroke = LINE_STROKE * scale
            val r = ARC_RADIUS * scale
            val arcTopLeft = px(arcCenterX - ARC_RADIUS, arcCenterY - ARC_RADIUS)
            val arcSize = Size(r * 2f, r * 2f)
            val ringStroke = Stroke(width = ARC_STROKE * scale, cap = StrokeCap.Round)
            val notchStroke = Stroke(width = ARC_STROKE * scale, cap = StrokeCap.Butt)

            // 1. Mil
            drawLine(
                color = colors.onSurfaceMuted,
                start = px(MAST_X, MAST_TOP),
                end = px(MAST_X, BEAM_Y),
                strokeWidth = lineStroke,
                cap = StrokeCap.Butt,
            )

            // 2. Kiris
            drawLine(
                color = colors.onSurfaceMuted,
                start = px(BEAM_LEFT, BEAM_Y),
                end = px(BEAM_RIGHT, BEAM_Y),
                strokeWidth = lineStroke,
                cap = StrokeCap.Butt,
            )

            // 3. Kase yayi - once iz, sonra doluluk
            drawArc(
                color = trackColor,
                startAngle = arcStartAngle,
                sweepAngle = arcSweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = ringStroke,
            )
            if (value > 0f) {
                drawArc(
                    color = color,
                    startAngle = arcStartAngle,
                    sweepAngle = arcSweepAngle * value,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = ringStroke,
                )
            }

            // 4. Centikler - yay uzerinde surface renginde radyal ayirici
            val notchDeg = NOTCH_WIDTH / ARC_RADIUS * DEG
            milestones.forEach { m ->
                if (m > 0f && m < 1f) {
                    drawArc(
                        color = colors.surface,
                        startAngle = arcStartAngle + arcSweepAngle * m - notchDeg / 2f,
                        sweepAngle = notchDeg,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = notchStroke,
                    )
                }
            }
        }

        // Metin blogu kasenin ic bosluguna oturur - kirisin biraz altina
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.offset(y = 5.dp),
        ) {
            Text(
                text = centerPercent,
                style = KefeTheme.type.display.tabular(),
                color = colors.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = centerAmount,
                style = KefeTheme.type.caption.copy(fontSize = 12.sp).tabular(),
                color = colors.onSurfaceMuted,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
