package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Bir ayin seri hucresindeki isaret. Alan tipinden bagimsiz: grafikler `domain`
 * import etmez, ekran kendi tipini buna esler.
 */
enum class StreakMark { Full, Partial, Missed, NoPlan, BeforeStart, InProgress }

/** Tek ay hucresi. [description] ekran okuyucunun okudugu tam cumledir ("Ekim 2026: sürüyor"). */
@Immutable
data class StreakGridCell(
    val label: String,
    val mark: StreakMark,
    val description: String,
    val current: Boolean = false,
)

/**
 * Aylik seri izgarasi - eskiden yeniye tek satir.
 *
 * Isaretler RENKTEN BAGIMSIZ ayrisir: dolu kare + tik, yarim dolu, carpi, nokta,
 * kesikli kenar. Renk korlugunde ya da gri tonlu ekranda da her ay okunur.
 * Hucre genislige gore kuculur (en fazla [maxCellSize]); dar ekranda ay etiketi
 * ilk harfe iner ki 12 ay tek satira sigsin.
 */
@Composable
fun KefeStreakGrid(
    cells: List<StreakGridCell>,
    modifier: Modifier = Modifier,
    maxCellSize: Dp = 28.dp,
    gap: Dp = 6.dp,
) {
    if (cells.isEmpty()) return
    val colors = KefeTheme.colors
    val type = KefeTheme.type
    val palette = StreakPalette(colors.accent, colors.onAccent, colors.outline, colors.onSurfaceMuted)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val count = cells.size
        val cell = ((maxWidth - gap * (count - 1)) / count)
            .coerceAtLeast(0.dp)
            .coerceAtMost(maxCellSize)
        val initialsOnly = cell < FullLabelMinCell

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            cells.forEach { item ->
                // Hucre ve etiketi TEK dugum: aciklama zaten ayi soyler, etiket
                // ikinci kez "Eki" diye okunmasin.
                Column(
                    modifier = Modifier
                        .width(cell)
                        .clearAndSetSemantics { contentDescription = item.description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(cell)) { drawStreakMark(item.mark, palette) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (initialsOnly) item.label.take(1) else item.label,
                        style = if (item.current) type.nano.copy(fontWeight = FontWeight.SemiBold) else type.nano,
                        color = if (item.current) colors.onSurface else colors.onSurfaceMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Izgaranin aciklamasi: her isaret kelimesiyle. "Plan başlamadan önce" yok - o
 * aylar ilk plandan onceki soluk noktadir, okunacak bir durum degil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KefeStreakLegend(modifier: Modifier = Modifier) {
    val colors = KefeTheme.colors
    val palette = StreakPalette(colors.accent, colors.onAccent, colors.outline, colors.onSurfaceMuted)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendEntries.forEach { (mark, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(LegendCellSize)) { drawStreakMark(mark, palette) }
                Text(text = label, style = KefeTheme.type.micro, color = colors.onSurfaceMuted)
            }
        }
    }
}

/** Cizimin renkleri - composable disinda (DrawScope) tema okunamaz. */
@Immutable
private class StreakPalette(
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val muted: Color,
)

private fun DrawScope.drawStreakMark(mark: StreakMark, p: StreakPalette) {
    val stroke = 1.5.dp.toPx()
    // 12dp aciklama hucresinde 6dp kose daireye donerdi; kose boyla orantili kalir.
    val radius = CornerRadius(min(6.dp.toPx(), size.minDimension * 0.25f))
    // Kenar cizgisi hucrenin icinde kalsin diye yarim kalinlik iceri alinir.
    val edgeTopLeft = Offset(stroke / 2f, stroke / 2f)
    val edgeSize = Size(size.width - stroke, size.height - stroke)

    when (mark) {
        StreakMark.Full -> {
            drawRoundRect(color = p.accent, cornerRadius = radius)
            val w = size.width
            val h = size.height
            val check = Path().apply {
                moveTo(w * 0.27f, h * 0.52f)
                lineTo(w * 0.43f, h * 0.68f)
                lineTo(w * 0.73f, h * 0.36f)
            }
            drawPath(
                path = check,
                color = p.onAccent,
                style = Stroke(
                    width = max(stroke, size.minDimension * 0.09f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        StreakMark.Partial -> {
            // Alt %55 dolu: "esik asildi, tamami degil" - doluluk orani seklin kendisi.
            clipRect(top = size.height * 0.45f) {
                drawRoundRect(color = p.accent, cornerRadius = radius)
            }
            drawRoundRect(
                color = p.accent,
                topLeft = edgeTopLeft,
                size = edgeSize,
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
        }

        StreakMark.Missed -> {
            drawRoundRect(
                color = p.outline,
                topLeft = edgeTopLeft,
                size = edgeSize,
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
            val arm = size.minDimension * 0.17f
            val c = center
            drawLine(p.muted, Offset(c.x - arm, c.y - arm), Offset(c.x + arm, c.y + arm), stroke, StrokeCap.Round)
            drawLine(p.muted, Offset(c.x + arm, c.y - arm), Offset(c.x - arm, c.y + arm), stroke, StrokeCap.Round)
        }

        StreakMark.NoPlan -> drawCircle(color = p.outline, radius = DotRadius.toPx(), center = center)

        // Ilk plandan onceki ay: ayni nokta, soluk - seri henuz baslamamisti.
        StreakMark.BeforeStart ->
            drawCircle(color = p.outline, radius = DotRadius.toPx(), center = center, alpha = 0.4f)

        StreakMark.InProgress -> drawRoundRect(
            color = p.accent,
            topLeft = edgeTopLeft,
            size = edgeSize,
            cornerRadius = radius,
            style = Stroke(width = stroke, pathEffect = dashEffect(3.dp.toPx(), 3.dp.toPx())),
        )
    }
}

private val LegendEntries = listOf(
    StreakMark.Full to "Tamamı",
    StreakMark.Partial to "%80 ve üstü",
    StreakMark.Missed to "Kaçtı",
    StreakMark.NoPlan to "Plan yok",
    StreakMark.InProgress to "Sürüyor",
)

/** Bundan dar hucrede "Eki" sigmaz; etiket ilk harfe iner. */
private val FullLabelMinCell = 24.dp

private val LegendCellSize = 12.dp

/** "Plan yok" noktasi 4dp. */
private val DotRadius = 2.dp
