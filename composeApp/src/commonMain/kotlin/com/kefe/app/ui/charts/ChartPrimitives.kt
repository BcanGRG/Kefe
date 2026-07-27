package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.theme.KefeTheme

/**
 * Grafik bilesenlerinin ortak yapi taslari: nokta, deger-piksel donusumu,
 * duzgunlestirilmis yol uretimi, kesikli cizgi ve eksen etiketi cizimi.
 *
 * Tum grafikler Compose Canvas ile cizilir; harici grafik kutuphanesi yoktur.
 */

/** Piksel uzayinda tek nokta. */
@Immutable
data class Point(val x: Float, val y: Float)

fun Point.toOffset(): Offset = Offset(x, y)

/** Eksen uzerinde belirli bir indeksi etiketleyen metin. */
@Immutable
data class ChartAxisLabel(val index: Int, val text: String)

/** Bir veri kumesinin alt/ust sinirlari. */
@Immutable
data class ValueRange(val min: Double, val max: Double) {
    val span: Double get() = max - min

    /** Ust ve alt uca oransal bosluk ekler. Duz seride sabit bosluk kullanilir. */
    fun padded(fraction: Double = 0.0): ValueRange {
        if (fraction <= 0.0) return this
        val pad = if (span > 0.0) span * fraction else (if (max != 0.0) max * fraction else 1.0)
        return ValueRange(min - pad, max + pad)
    }

    fun include(value: Double): ValueRange =
        ValueRange(kotlin.math.min(min, value), kotlin.math.max(max, value))
}

/** Verilen serilerin ortak deger araligi. Bos girdide 0..1 doner. */
fun valueRangeOf(vararg series: List<Double>): ValueRange {
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    var seen = false
    for (list in series) {
        for (v in list) {
            if (v < lo) lo = v
            if (v > hi) hi = v
            seen = true
        }
    }
    return if (!seen) ValueRange(0.0, 1.0) else ValueRange(lo, hi)
}

/**
 * Deger -> piksel donusumu. Cizim alani sol/ust/sag/alt piksel sinirlariyla,
 * dikey eksen ise deger araligiyla tanimlanir.
 */
@Immutable
class ChartScale(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val range: ValueRange,
    val count: Int,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun x(index: Int): Float = when {
        count <= 0 -> left
        count == 1 -> (left + right) / 2f
        else -> left + width * index / (count - 1).toFloat()
    }

    fun y(value: Double): Float {
        val span = range.span
        if (span <= 0.0) return (top + bottom) / 2f
        val t = ((value - range.min) / span).toFloat()
        return bottom - height * t
    }

    fun point(index: Int, value: Double): Point = Point(x(index), y(value))

    /** Seriyi bastan sona noktalara cevirir. */
    fun points(values: List<Double>, startIndex: Int = 0): List<Point> =
        values.mapIndexed { i, v -> point(startIndex + i, v) }

    /** x pikselinden en yakin veri indeksi - dokunma esleme icin. */
    fun indexAt(xPx: Float): Int {
        if (count <= 1) return 0
        val step = width / (count - 1).toFloat()
        if (step <= 0f) return 0
        val raw = ((xPx - left) / step)
        return raw.toInt().let { base ->
            val frac = raw - base
            (if (frac >= 0.5f) base + 1 else base).coerceIn(0, count - 1)
        }
    }
}

/**
 * Tek veri noktasi cizgi olusturmaz. Bu durumda nokta ikilenerek yatay bir
 * parca cizilir - grafik bos gorunmez, sahte egilim de uretilmez.
 */
fun List<Point>.ensureSegment(left: Float, right: Float): List<Point> =
    if (size == 1) listOf(Point(left, this[0].y), Point(right, this[0].y)) else this

/** Hafif duzgunlestirme katsayisi - cizgiler koseli degil, abartili da degil. */
const val DefaultSmoothing: Float = 0.16f

/** Catmull-Rom benzeri kubik bezier ile duzgunlestirilmis yol. */
fun List<Point>.smoothPath(smoothing: Float = DefaultSmoothing): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(this[0].x, this[0].y)
    appendSmoothTo(path, this, smoothing)
    return path
}

/** Verilen noktalari, ilk noktaya zaten gelinmis varsayarak yola ekler. */
private fun appendSmoothTo(path: Path, pts: List<Point>, smoothing: Float) {
    if (pts.size < 2) return
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i == 0) 0 else i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 <= pts.size - 1) i + 2 else i + 1]
        path.cubicTo(
            p1.x + (p2.x - p0.x) * smoothing,
            p1.y + (p2.y - p0.y) * smoothing,
            p2.x - (p3.x - p1.x) * smoothing,
            p2.y - (p3.y - p1.y) * smoothing,
            p2.x,
            p2.y,
        )
    }
}

/** Iki seri arasindaki kapali alan (ust seri ileri, alt seri geri). */
fun areaBetween(
    upper: List<Point>,
    lower: List<Point>,
    smoothing: Float = DefaultSmoothing,
): Path {
    val path = Path()
    if (upper.isEmpty() || lower.isEmpty()) return path
    path.moveTo(upper[0].x, upper[0].y)
    appendSmoothTo(path, upper, smoothing)
    val back = lower.asReversed()
    path.lineTo(back[0].x, back[0].y)
    appendSmoothTo(path, back, smoothing)
    path.close()
    return path
}

/** Bir serinin taban cizgisine kadar doldurulmus alani. */
fun areaToBaseline(
    points: List<Point>,
    baselineY: Float,
    smoothing: Float = DefaultSmoothing,
): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    appendSmoothTo(path, points, smoothing)
    path.lineTo(points.last().x, baselineY)
    path.lineTo(points[0].x, baselineY)
    path.close()
    return path
}

/** Kesikli cizgi efekti. `on`/`off` piksel cinsindendir. */
fun dashEffect(on: Float, off: Float, phase: Float = 0f): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(on, off), phase)

/** Yatay kesikli cizgi - hedef cizgisi ve "bugun" isareti icin. */
fun DrawScope.drawDashedLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    on: Float,
    off: Float,
    alpha: Float = 1f,
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        pathEffect = dashEffect(on, off),
        alpha = alpha,
        cap = StrokeCap.Butt,
    )
}

/** Elmas isaret yolu - alim/satim noktalari icin. */
fun diamondPath(center: Point, rx: Float, ry: Float): Path = Path().apply {
    moveTo(center.x, center.y - ry)
    lineTo(center.x + rx, center.y)
    lineTo(center.x, center.y + ry)
    lineTo(center.x - rx, center.y)
    close()
}

/** Metin kutusunun verilen noktaya gore hizasi. */
enum class LabelAnchor { Start, Center, End }

/**
 * Canvas icine eksen/balon etiketi cizer. Metin Compose Text ile
 * konumlandirilamayan yerlerde (grafik geometrisine bagli etiketler) kullanilir.
 *
 * `x`/`y` referans nokta; hiza `hAnchor`/`vAnchor` ile belirlenir
 * (End = saga hizali / alta hizali).
 */
fun DrawScope.drawChartText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    hAnchor: LabelAnchor = LabelAnchor.Start,
    vAnchor: LabelAnchor = LabelAnchor.Start,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style = style)
    val dx = when (hAnchor) {
        LabelAnchor.Start -> 0f
        LabelAnchor.Center -> -layout.size.width / 2f
        LabelAnchor.End -> -layout.size.width.toFloat()
    }
    val dy = when (vAnchor) {
        LabelAnchor.Start -> 0f
        LabelAnchor.Center -> -layout.size.height / 2f
        LabelAnchor.End -> -layout.size.height.toFloat()
    }
    drawText(layout, topLeft = Offset(x + dx, y + dy))
}

/** Etiket genisligi - kutu/balon olcusu hesaplamak icin. */
fun TextMeasurer.widthOf(text: String, style: TextStyle): Float =
    if (text.isEmpty()) 0f else measure(text, style = style).size.width.toFloat()

/**
 * Bos veri hali. Grafik yerine notr bir aciklama gosterilir - sahte cizgi
 * veya sifir degerli grafik cizilmez.
 */
@Composable
fun ChartEmptyState(label: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = KefeTheme.type.micro,
            color = KefeTheme.colors.onSurfaceMuted,
        )
    }
}

/** Legend isaretinin gorsel bicimi. */
enum class LegendMark { Line, ThinLine, Dashed, Area, Diamond, DiamondOutline, Dot }

@Immutable
data class ChartLegendItem(
    val label: String,
    val color: Color,
    val mark: LegendMark,
)

/**
 * Grafik alti legend satiri. Renk tek sinyal olmadigi icin her ogede
 * her zaman metin etiketi bulunur.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KefeChartLegend(
    items: List<ChartLegendItem>,
    modifier: Modifier = Modifier,
) {
    val colors = KefeTheme.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendMarkGlyph(item.color, item.mark)
                Text(
                    text = item.label,
                    style = KefeTheme.type.micro,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun LegendMarkGlyph(color: Color, mark: LegendMark) {
    Canvas(Modifier.width(14.dp).height(10.dp)) {
        val cy = size.height / 2f
        when (mark) {
            LegendMark.Line -> drawLine(
                color = color,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            LegendMark.ThinLine -> drawLine(
                color = color,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )

            LegendMark.Dashed -> drawDashedLine(
                color = color,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 2.dp.toPx(),
                on = 4.dp.toPx(),
                off = 3.dp.toPx(),
            )

            LegendMark.Area -> drawRoundRect(
                color = color,
                topLeft = Offset(1f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width - 2f, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                alpha = 0.3f,
            )

            LegendMark.Diamond -> drawPath(
                path = diamondPath(Point(size.width / 2f, cy), 5.dp.toPx(), 5.dp.toPx()),
                color = color,
            )

            LegendMark.DiamondOutline -> drawPath(
                path = diamondPath(Point(size.width / 2f, cy), 5.dp.toPx(), 5.dp.toPx()),
                color = color,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            LegendMark.Dot -> drawRoundRect(
                color = color,
                topLeft = Offset(size.width / 2f - 4.5.dp.toPx(), cy - 4.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(9.dp.toPx(), 9.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }
    }
}

/** Sik kullanilan olculer - grafik dosyalari arasinda tekrar etmesin. */
internal object ChartDefaults {
    val axisStroke = 1.dp
    val goalStroke = 1.2.dp
    val goalDashOn = 5.dp
    val goalDashOff = 4.dp
    val markerRadius = 4.dp
    val markerStroke = 2.4.dp
}

/** Bos veya tek elemanli seride "isaret + balon" cizmek anlamsiz olur. */
internal fun Int.isValidIndexIn(size: Int): Boolean = this in 0 until size

/** Kutu kenarlarindan tasmayacak sekilde sikistirir. */
internal fun Float.clampTo(min: Float, max: Float): Float =
    if (max <= min) min else this.coerceIn(min, max)
