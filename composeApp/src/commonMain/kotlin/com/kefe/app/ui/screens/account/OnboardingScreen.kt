package com.kefe.app.ui.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import kotlin.math.PI

/** Tasarimda ucu var: dagilim, hedef, kurulum. */
const val OnboardingPageCount: Int = 3

/**
 * Karsilama akisi. Tamamen durumsuzdur: hangi sayfadayiz bilgisi disaridan
 * gelir, ekran yalnizca cizer ve iki eylem bildirir.
 */
@Composable
fun OnboardingScreen(
    pageIndex: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val page = pageIndex.coerceIn(0, OnboardingPageCount - 1)

    // Tanitim sayfasi tek kolondur: gorsel kolonun ortasinda, metin kolonun sol
    // kenarinda durur. Kolon genislemezse bu iki hiza masaustunde de telefondaki
    // gibi ayni eksende kalir. Telefonda (390) sinir devreye girmez.
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // widthIn ONCE gelmeli: fillMaxWidth once uygulanirsa asagi kesin
        // genislik kisiti iner ve sinir hicbir sey yapmaz.
        Column(
            Modifier
                .widthIn(max = Sizes.formMaxWidth)
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Space.x24),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .defaultMinSize(minHeight = 420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> AllocationRingArt()
                        1 -> GoalGaugeArt()
                        else -> SetupStepsArt()
                    }
                }

                Column(Modifier.fillMaxWidth().padding(bottom = Space.x8)) {
                    Text(OnboardingTitles[page], style = t.h1, color = c.onSurface)
                    Spacer(Modifier.height(Space.x10))
                    Text(
                        OnboardingBodies[page],
                        style = t.body.copy(lineHeight = 22.sp),
                        color = c.onSurfaceMuted,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Space.x24,
                        end = Space.x24,
                        top = Space.x12,
                        bottom = Space.x24,
                    ),
                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingDots(activeIndex = page, modifier = Modifier.weight(1f))
                AccountFlatButton(
                    text = "Atla",
                    onClick = onSkip,
                    contentColor = c.onSurfaceMuted,
                    horizontalPadding = Space.x14,
                )
                AccountFilledButton(
                    text = if (page == OnboardingPageCount - 1) "Kuruluma başla" else "Devam",
                    onClick = onNext,
                    horizontalPadding = Space.x24,
                )
            }
        }
    }
}

private val OnboardingTitles = listOf(
    "Biriktirdiğiniz her şey tek ekranda",
    "Hedefinizi koyun, ne kadar kaldığını görün",
    "Üç adımda kurulum",
)

private val OnboardingBodies = listOf(
    "Altın, gümüş, döviz, fon, nakit — hepsi elle girilir, güncel fiyatla " +
        "TL'ye çevrilir. Kefe bankaya bağlanmaz.",
    "Hedefi TL, gram altın veya dolar cinsinden sabitleyin. İlerleme ve " +
        "tahmini varış tarihi kendiliğinden hesaplanır.",
    "Önce hedef, sonra hangi varlıkları tuttuğunuz. Kalanı sırayla " +
        "girersiniz — sonradan da eklenir.",
)

/**
 * Adim noktalari. Aktif adim hem genisligi hem rengiyle ayrisir; ortak
 * `KefeStepDots` 18/6dp kullanir, tasarim burada 22/7dp ister.
 */
@Composable
private fun OnboardingDots(activeIndex: Int, modifier: Modifier = Modifier) {
    val c = KefeTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until OnboardingPageCount) {
            val active = index == activeIndex
            Box(
                Modifier
                    .width(if (active) 22.dp else 7.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (active) c.accent else c.outline),
            )
        }
    }
}

// --- 1. gorsel: dagilim halkasi --------------------------------------------

/** Halkanin kesik uzunluklari tasarimdaki `stroke-dasharray` degerleridir. */
private class RingSlice(val length: Float, val offset: Float, val color: Color)

@Composable
private fun AllocationRingArt() {
    val c = KefeTheme.colors
    val slices = listOf(
        RingSlice(240f, 0f, c.gold),
        RingSlice(80f, 242f, c.fund),
        RingSlice(44f, 324f, c.cash),
        RingSlice(42f, 370f, c.fx),
        RingSlice(8f, 414f, c.silver),
    )

    Box(Modifier.size(width = 240.dp, height = 200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 240f
            val radius = 66f * u
            val stroke = 14f * u
            val center = Offset(120f * u, 100f * u)
            val circumference = (2.0 * PI * 66f).toFloat()

            drawRing(center, radius, stroke, c.outline, -90f, 360f)
            slices.forEach { slice ->
                drawRing(
                    center = center,
                    radius = radius,
                    stroke = stroke,
                    color = slice.color,
                    startAngle = -90f + slice.offset / circumference * 360f,
                    sweep = slice.length / circumference * 360f,
                )
            }
        }
        Text(
            "Tek ekran",
            style = KefeTheme.type.h2,
            color = KefeTheme.colors.onSurface,
        )
    }
}

private fun DrawScope.drawRing(
    center: Offset,
    radius: Float,
    stroke: Float,
    color: Color,
    startAngle: Float,
    sweep: Float,
    cap: StrokeCap = StrokeCap.Butt,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = cap),
    )
}

// --- 2. gorsel: hedef kefesi -----------------------------------------------

/**
 * Kefe kavisi: askidan sarkan tek kefe. Yay tasarimdaki
 * `A 72 72 0 1 0` komutundan turetildi - merkez (120, 96.78), yaricap 72,
 * baslangic 203.55 derece, toplam 227.1 derece ters yonde.
 */
@Composable
private fun GoalGaugeArt() {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(Modifier.size(width = 240.dp, height = 200.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 240f

            // Ask cubugu ve kiris
            drawLine(
                color = c.outline,
                start = Offset(120f * u, 26f * u),
                end = Offset(120f * u, 58f * u),
                strokeWidth = 2.5f * u,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = c.outline,
                start = Offset(54f * u, 68f * u),
                end = Offset(120f * u, 44f * u),
                strokeWidth = 1.8f * u,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = c.outline,
                start = Offset(120f * u, 44f * u),
                end = Offset(186f * u, 68f * u),
                strokeWidth = 1.8f * u,
                cap = StrokeCap.Round,
            )
            drawCircle(color = c.outline, radius = 4f * u, center = Offset(120f * u, 41f * u))

            val center = Offset(120f * u, 96.775f * u)
            val radius = 72f * u
            val stroke = 16f * u
            drawRing(center, radius, stroke, c.surfaceSunken, 203.55f, -227.1f, StrokeCap.Round)
            drawRing(center, radius, stroke, c.accent, 203.55f, -89.92f, StrokeCap.Round)
        }

        Text(
            "%41",
            style = t.display.copy(fontSize = 34.sp, lineHeight = 34.sp).tabular(),
            color = c.onSurface,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 92.dp),
        )
        Text(
            "hedefe gidiş",
            style = t.caption,
            color = c.onSurfaceMuted,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 130.dp),
        )
    }
}

// --- 3. gorsel: uc adimli kurulum ------------------------------------------

@Composable
private fun SetupStepsArt() {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.x10),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.accent, KefeShapes.card)
                .padding(Space.x14),
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepBullet(number = "1", active = true)
            Text("Hedefini belirle", style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
            KefeIcon(KefeIcons.ChevronRight, null, size = 18.dp, tint = c.accent)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
                .padding(Space.x14),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepBullet(number = "2", active = false)
                Text(
                    "Varlık sınıflarını seç",
                    style = t.bodyStrong,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            SetupClassChips()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
                .padding(Space.x14),
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepBullet(number = "3", active = false)
            Text("Sırayla gir", style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
            Text("5 dk", style = t.micro, color = c.onSurfaceMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupClassChips() {
    // 40dp sol dolgu numara madalyonu ile hizalar.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.x12, start = Space.x40),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SetupChip("Altın", selected = true)
        SetupChip("Döviz", selected = true)
        SetupChip("Gümüş", selected = false)
        SetupChip("Fon", selected = false)
        SetupChip("Nakit", selected = false)
    }
}

@Composable
private fun SetupChip(text: String, selected: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(
        modifier = Modifier
            .height(Sizes.chipSmall)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accentMuted else Color.Transparent)
            .border(
                width = Sizes.hairline,
                color = if (selected) c.accent else c.outline,
                shape = KefeShapes.pill,
            )
            .padding(horizontal = Space.x12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (selected) {
                t.captionSmall.copy(fontWeight = FontWeight.SemiBold)
            } else {
                t.captionSmall
            },
            color = if (selected) c.onSurface else c.onSurfaceMuted,
        )
    }
}

@Composable
private fun StepBullet(number: String, active: Boolean) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .size(Sizes.avatarMedium)
            .clip(CircleShape)
            .background(if (active) c.accent else c.surfaceSunken)
            .then(
                if (active) Modifier
                else Modifier.border(Sizes.hairline, c.outline, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (active) c.onAccent else c.onSurfaceMuted,
        )
    }
}
