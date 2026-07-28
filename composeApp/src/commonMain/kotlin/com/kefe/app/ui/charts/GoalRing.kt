package com.kefe.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.tabular
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kefe kavisi - hedef ilerlemesi halkasi.
 *
 * Bilincli olarak ACIK bir yaydir: mil ve kiris ile birlikte bir terazi kefesi
 * okunur. Dagilim halkasi ([KefeDonutChart]) kapali ringdir; ikisinin gorsel
 * olarak ayrisabilmesi icin mil ve kiris atlanamaz.
 */

// --- Referans cizim uzayi (200 x 148) ---

private const val VIEW_W = 200f
private const val VIEW_H = 148f

/** Mil: 100,6 - 100,30 arasi dikey cizgi. */
private const val MAST_X = 100f
private const val MAST_TOP = 6f
private const val MAST_BOTTOM = 30f
private const val MAST_STROKE = 2f

/** Askilik: kase yayinin iki ucunu tepe noktasinda birlestiren V. */
private const val BEAM_Y = 38.1f
private const val BEAM_LEFT = 39.9f
private const val BEAM_RIGHT = 160.1f
private const val BEAM_APEX_X = 100f
private const val BEAM_APEX_Y = 22f
private const val BEAM_STROKE = 1.4f

/** Milin ucundaki donme noktasi. */
private const val PIVOT_X = 100f
private const val PIVOT_Y = 20f
private const val PIVOT_RADIUS = 3f

/** Kase yayi: M 39.9 38.1 A 64 64 0 1 0 160.1 38.1 */
private const val ARC_START_X = 39.9f
private const val ARC_END_X = 160.1f
private const val ARC_Y = 38.1f
private const val ARC_RADIUS = 64f
private const val ARC_STROKE = 14f

/** Kilometre tasi noktasi - yay uzerine oturan kucuk daire. */
private const val NOTCH_RADIUS = 2f

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
 * Tutar satirinin kasenin ICINE sigabilecegi en genis olcu.
 *
 * Kasenin ic yaricapi 57 birim (yaricap 64, kalinlik 14). Tutar satiri yay
 * merkezinin ~35 birim altinda durur; o yukseklikteki kiris yariyarıya
 * sqrt(57^2 - 35^2) = 45 birim eder. Cizim olcegi ~1 oldugu icin dp ile ayni.
 */
private val AmountMaxWidth = 90.dp

/**
 * Hedef ilerlemesi halkasi.
 *
 * @param progress 0..1 arasi doluluk.
 * @param centerAmount tam yazim ("₺19.587 / ₺100.000").
 * @param centerAmountShort kisa yazim ("₺19,6B / ₺100B") - tam yazim kaseye
 *   sigmadiginda kullanilir. Verilmezse tam yazim kucultulmeden yazilir.
 * @param milestones yay uzerinde ayirici cizilecek oranlar.
 */
@Composable
fun KefeGoalRing(
    progress: Float,
    centerPercent: String,
    centerAmount: String,
    modifier: Modifier = Modifier,
    centerAmountShort: String? = null,
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

            val r = ARC_RADIUS * scale
            val arcTopLeft = px(arcCenterX - ARC_RADIUS, arcCenterY - ARC_RADIUS)
            val arcSize = Size(r * 2f, r * 2f)
            val ringStroke = Stroke(width = ARC_STROKE * scale, cap = StrokeCap.Round)

            // 1. Mil
            drawLine(
                color = colors.outline,
                start = px(MAST_X, MAST_TOP),
                end = px(MAST_X, MAST_BOTTOM),
                strokeWidth = MAST_STROKE * scale,
                cap = StrokeCap.Round,
            )

            // 2. Askilik - iki kol tepe noktasinda birlesir
            drawLine(
                color = colors.outline,
                start = px(BEAM_LEFT, BEAM_Y),
                end = px(BEAM_APEX_X, BEAM_APEX_Y),
                strokeWidth = BEAM_STROKE * scale,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.outline,
                start = px(BEAM_APEX_X, BEAM_APEX_Y),
                end = px(BEAM_RIGHT, BEAM_Y),
                strokeWidth = BEAM_STROKE * scale,
                cap = StrokeCap.Round,
            )

            // 3. Donme noktasi
            drawCircle(
                color = colors.outline,
                radius = PIVOT_RADIUS * scale,
                center = px(PIVOT_X, PIVOT_Y),
            )

            // 4. Kase yayi - once iz, sonra doluluk
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

            // 5. Kilometre taslari - yayin uzerine oturan kucuk noktalar
            milestones.forEach { m ->
                if (m > 0f && m < 1f) {
                    val rad = (arcStartAngle + arcSweepAngle * m) / DEG
                    drawCircle(
                        color = colors.outline,
                        radius = NOTCH_RADIUS * scale,
                        center = px(
                            arcCenterX + ARC_RADIUS * cos(rad),
                            arcCenterY + ARC_RADIUS * sin(rad),
                        ),
                    )
                }
            }
        }

        // Metin blogu kasenin ic bosluguna oturur. Tasarimda iki taban cizgisi
        // arasi 20px; bunun icin satir yuksekligi punto degerine cekilir.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = centerPercent,
                style = KefeTheme.type.display.copy(lineHeight = 40.sp).tabular(),
                color = colors.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            // Tutar kasenin ICINDE kalmali. Once tam yazim kasenin disina
            // tasiyor, yayin ve uzerindeki kilometre noktalarinin ustune
            // biniyordu: rakam okunakli ama halka bozuk gorunuyordu.
            val amountStyle = KefeTheme.type.caption
                .copy(fontSize = 12.sp, lineHeight = 14.sp)
                .tabular()
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val amountText = remember(centerAmount, centerAmountShort, amountStyle, density) {
                val width = measurer.measure(centerAmount, amountStyle).size.width
                val fits = with(density) { width.toDp() } <= AmountMaxWidth
                if (fits || centerAmountShort == null) centerAmount else centerAmountShort
            }
            Text(
                text = amountText,
                style = amountStyle,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = AmountMaxWidth),
            )
        }
    }
}
