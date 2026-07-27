package com.kefe.app.ui.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.formatMonthYear
import com.kefe.app.ui.charts.BarSegment
import com.kefe.app.ui.charts.KefeGoalRing
import com.kefe.app.ui.charts.KefeProjectionChart
import com.kefe.app.ui.charts.KefeStackedBarChart
import com.kefe.app.ui.charts.MonthBar
import com.kefe.app.ui.charts.Point
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeEmptyState
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefeSlider
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/** Bloklar arasi dikey bosluk - tasarimdaki `margin-bottom: 12px`. */
private val BlockGap = Space.x12

/**
 * Hedef detayi. Halka, projeksiyon, senaryo ve katki gecmisi tek kaydirmada.
 *
 * Asilan ve tarihi gecmis hedeflerde analiz bolumleri yerine iki eylem butonu
 * gosterilir - tasarimdaki "asildi" ve "tarihi gecti" cerceveleri boyle.
 */
@Composable
fun GoalDetailScreen(
    state: GoalDetailUiState,
    onIntent: (GoalDetailIntent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = state.goal

    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = goal?.name.orEmpty(),
            onBack = onBack,
            onEdit = onEdit,
            editEnabled = goal != null,
        )

        if (goal == null) {
            KefeEmptyState(
                icon = KefeIcons.Target,
                title = "Hedef bulunamadı",
                body = "Bu hedef silinmiş olabilir. Hedefler listesine dönebilirsiniz.",
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailBody(goal, state, onIntent, onEdit)
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    editEnabled: Boolean,
) {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x8, end = Space.x8, bottom = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIconButton(
            icon = KefeIcons.ArrowBack,
            contentDescription = "Geri",
            onClick = onBack,
            tint = c.onSurface,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            text = title,
            style = KefeTheme.type.h2,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x4))
        KefeIconButton(
            icon = KefeIcons.Pencil,
            contentDescription = "Hedefi düzenle",
            onClick = onEdit,
            enabled = editEnabled,
        )
    }
}

@Composable
private fun DetailBody(
    goal: Goal,
    state: GoalDetailUiState,
    onIntent: (GoalDetailIntent) -> Unit,
    onEdit: () -> Unit,
) {
    val c = KefeTheme.colors

    if (state.exceeded) {
        StatusBanner(
            icon = KefeIcons.Check,
            text = "Hedefe ulaşıldı · Fazlası birikimde kalır",
            contentColor = c.positive,
            background = c.positive.copy(alpha = 0.14f),
            borderColor = c.positive.copy(alpha = 0.30f),
            bold = true,
        )
        Spacer(Modifier.height(BlockGap))
    } else if (state.overdue) {
        StatusBanner(
            icon = KefeIcons.Clock,
            text = "Hedef tarihi geçti · Hedef duruyor",
            contentColor = c.onSurfaceMuted,
            background = c.surfaceSunken,
            borderColor = c.outline,
            bold = false,
        )
        Spacer(Modifier.height(BlockGap))
    }

    RingCard(goal, state, onIntent, onEdit)
    Spacer(Modifier.height(BlockGap))

    if (state.showAnalysis) {
        ProjectionCard(goal, state)
        Spacer(Modifier.height(BlockGap))
        ScenarioCard(state, onIntent)
        Spacer(Modifier.height(BlockGap))
        MilestonesCard(state)
        Spacer(Modifier.height(BlockGap))
        HistoryCard(state, onIntent)
        Spacer(Modifier.height(Space.x24))
    }
}

// --- Durum seridi ----------------------------------------------------------

@Composable
private fun StatusBanner(
    icon: ImageVector,
    text: String,
    contentColor: Color,
    background: Color,
    borderColor: Color,
    bold: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x16)
            .clip(KefeShapes.button)
            .background(background)
            .border(Sizes.hairline, borderColor, KefeShapes.button)
            .padding(horizontal = Space.x14, vertical = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(icon, null, size = IconSize.small, tint = contentColor)
        Spacer(Modifier.width(Space.x8))
        Text(
            text = text,
            style = if (bold) {
                KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold)
            } else {
                KefeTheme.type.caption
            },
            color = contentColor,
        )
    }
}

// --- Halka karti -----------------------------------------------------------

@Composable
private fun RingCard(
    goal: Goal,
    state: GoalDetailUiState,
    onIntent: (GoalDetailIntent) -> Unit,
    onEdit: () -> Unit,
) {
    val c = KefeTheme.colors
    val surplus = state.currentWealth - goal.amount

    KefeCard(
        modifier = Modifier.padding(horizontal = Space.x16),
        contentPadding = PaddingValues(
            start = Space.x16,
            top = Space.x16,
            end = Space.x16,
            bottom = Space.x20,
        ),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KefeGoalRing(
                progress = state.progress,
                centerPercent = Money.ratioOf(state.progress.toDouble()),
                centerAmount = "${Money.tl(state.currentWealth)} / ${Money.tl(goal.amount)}",
                color = if (state.exceeded) c.positive else c.accent,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x10)) {
            SunkenInfoBox(
                label = if (state.exceeded) "Fazla" else "Kalan",
                value = if (state.exceeded) {
                    Money.tlSigned(surplus)
                } else {
                    Money.tl((-surplus).coerceAtLeast(0.0))
                },
                modifier = Modifier.weight(1f),
            )
            SunkenInfoBox(
                label = "Hedef tarihi",
                value = if (state.showAnalysis) {
                    "${goal.targetDate.formatMonthYear()} · ${state.monthsToTarget} ay"
                } else {
                    goal.targetDate.formatMonthYear()
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.exceeded) {
            Spacer(Modifier.height(Space.x12))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                PrimaryAction("Hedefi kapat") { onIntent(GoalDetailIntent.CloseGoal) }
                SecondaryAction("Tutarı yükselt", onEdit)
            }
        } else if (state.overdue) {
            Spacer(Modifier.height(Space.x12))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                PrimaryAction("Tarihi güncelle", onEdit)
                SecondaryAction("Tutarı düşür", onEdit)
            }
        }
    }
}

@Composable
private fun SunkenInfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column(
        modifier
            .clip(KefeShapes.button)
            .background(c.surfaceSunken)
            .padding(Space.x12),
    ) {
        Text(
            text = label.trUpper(),
            style = t.label(11, 0.06, FontWeight.SemiBold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x4))
        Text(value, style = t.bodyStrong.tabular(), color = c.onSurface)
    }
}

@Composable
private fun RowScope.PrimaryAction(text: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .background(c.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KefeTheme.type.bodyStrong, color = c.onAccent, maxLines = 1)
    }
}

@Composable
private fun RowScope.SecondaryAction(text: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KefeTheme.type.bodyStrong, color = c.onSurface, maxLines = 1)
    }
}

// --- Hedefe gidis ----------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectionCard(goal: Goal, state: GoalDetailUiState) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(modifier = Modifier.padding(horizontal = Space.x16)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hedefe gidiş", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                text = "Tahmin",
                style = t.micro,
                color = c.onSurfaceMuted,
                modifier = Modifier
                    .clip(KefeShapes.pill)
                    .border(Sizes.hairline, c.outline, KefeShapes.pill)
                    .padding(horizontal = Space.x8, vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(Space.x10))
        KefeProjectionChart(
            actual = state.projectionActual.toPoints(),
            forecast = state.projectionForecast.toPoints(),
            bandLow = state.bandLow.toPoints(),
            bandHigh = state.bandHigh.toPoints(),
            goal = goal.amount,
            goalLabel = "₺${Money.compact(goal.amount)} hedef",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.x8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalArrangement = Arrangement.spacedBy(Space.x12),
        ) {
            LegendItem("Gerçekleşen") {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.accent),
                )
            }
            LegendItem("Tahmin") {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .drawBehind {
                            drawLine(
                                color = c.accent,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                                ),
                            )
                        },
                )
            }
            LegendItem("Belirsizlik aralığı") {
                Box(
                    Modifier
                        .width(12.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.accent.copy(alpha = 0.25f)),
                )
            }
        }

        Spacer(Modifier.height(Space.x14))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.surfaceSunken)
                .padding(Space.x12),
        ) {
            Text(
                text = projectionSummary(goal, state.delayMonths),
                style = t.caption.copy(lineHeight = 20.sp),
                color = c.onSurface,
            )
            Spacer(Modifier.height(Space.x8))
            // Tahmin kesin bir tarih vaadi degildir - bu cumle atlanamaz.
            Text(
                "Tahmin kesin bir tarih değildir; piyasa ve katkılar değiştikçe güncellenir.",
                style = t.micro,
                color = c.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, marker: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        marker()
        Text(label, style = KefeTheme.type.micro, color = KefeTheme.colors.onSurfaceMuted)
    }
}

private fun projectionSummary(goal: Goal, delayMonths: Int) = buildAnnotatedString {
    val arrival = (goal.estimatedArrival ?: goal.targetDate).formatMonthYear()
    append("Son 6 ayın ortalama katkısı ve getirisiyle devam ederseniz hedefe ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(arrival) }
    append(" civarında ulaşırsınız")
    append(
        when {
            delayMonths > 0 -> " — planladığınızdan $delayMonths ay geç."
            delayMonths < 0 -> " — planladığınızdan ${-delayMonths} ay erken."
            else -> " — tam hedef tarihinde."
        },
    )
}

// --- Senaryo ---------------------------------------------------------------

@Composable
private fun ScenarioCard(state: GoalDetailUiState, onIntent: (GoalDetailIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val amount = state.scenarioContribution.toDouble() * 1000.0

    KefeCard(modifier = Modifier.padding(horizontal = Space.x16)) {
        Text("Senaryo", style = t.bodyStrong, color = c.onSurface)

        Spacer(Modifier.height(Space.x12))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Aylık katkı",
                style = t.caption,
                color = c.onSurfaceMuted,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                Money.tl(amount),
                style = t.h2.tabular(),
                color = c.accent,
                modifier = Modifier.alignByBaseline(),
            )
        }

        KefeSlider(
            value = state.scenarioContribution,
            onValueChange = { onIntent(GoalDetailIntent.SetScenarioContribution(it)) },
            valueRange = ScenarioMinThousands..ScenarioMaxThousands,
            steps = ScenarioSteps,
        )

        Row(Modifier.fillMaxWidth()) {
            Text(
                Money.tl(ScenarioMinThousands.toDouble() * 1000.0),
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Money.tl(ScenarioMaxThousands.toDouble() * 1000.0),
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
            )
        }

        Spacer(Modifier.height(Space.x12))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.accentMuted)
                .padding(Space.x12),
        ) {
            Text(
                text = "Aylık katkıyı ${Money.tl(state.baseContribution)} → " +
                    "${Money.tl(amount)} yaparsanız: ${state.scenarioArrival} · " +
                    scenarioComparison(state.scenarioDiffMonths),
                style = t.caption.copy(lineHeight = 20.sp),
                color = c.onSurface,
            )
        }
    }
}

private fun scenarioComparison(diffMonths: Int): String = when {
    diffMonths == 0 -> "hedef tarihine tam yetişir"
    diffMonths < 0 -> "${-diffMonths} ay önce"
    else -> "$diffMonths ay sonra"
}

// --- Kilometre taslari -----------------------------------------------------

@Composable
private fun MilestonesCard(state: GoalDetailUiState) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(modifier = Modifier.padding(horizontal = Space.x16)) {
        Text("Kilometre taşları", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x4))

        state.milestones.forEachIndexed { index, milestone ->
            if (index > 0) KefeHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (milestone.reached) 1f else 0.6f)
                    .padding(vertical = Space.x12),
                verticalAlignment = Alignment.Top,
            ) {
                if (milestone.reached) {
                    Box(
                        modifier = Modifier
                            .size(Sizes.avatarSmall)
                            .clip(KefeShapes.pill)
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        KefeIcon(KefeIcons.Check, null, size = 14.dp, tint = c.onAccent)
                    }
                } else {
                    Box(
                        Modifier.size(Sizes.avatarSmall).drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawCircle(
                                color = c.onSurfaceMuted,
                                radius = size.minDimension / 2f - stroke / 2f,
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                                    ),
                                ),
                            )
                        },
                    )
                }
                Spacer(Modifier.width(Space.x12))
                Text(
                    text = Money.ratio(milestone.percent.toDouble()),
                    style = if (milestone.reached) t.bodyStrong.tabular() else t.body.tabular(),
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x8))
                Text(milestone.label, style = t.caption.tabular(), color = c.onSurfaceMuted)
            }
        }
    }
}

// --- Aylik katki gecmisi ---------------------------------------------------

@Composable
private fun HistoryCard(state: GoalDetailUiState, onIntent: (GoalDetailIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val rows = if (state.showAllRows) state.rows else state.collapsedRows

    KefeCard(modifier = Modifier.padding(horizontal = Space.x16)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aylık katkı geçmişi", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            Text("${state.months.size} ay", style = t.caption, color = c.onSurfaceMuted)
        }

        Spacer(Modifier.height(Space.x12))
        KefeStackedBarChart(
            months = state.months.map { month ->
                MonthBar(
                    label = month.monthLabel,
                    segments = month.segments.map {
                        BarSegment(it.assetClass.color(), it.amount)
                    },
                )
            },
            height = 122.dp,
        )

        state.emptyMonthLocative?.let { locative ->
            Spacer(Modifier.height(2.dp))
            // Katkisiz ay suclayici degil, notr anlatilir.
            Text(
                "$locative katkı yok — ay sonu değer piyasa hareketiyle değişti.",
                style = t.micro,
                color = c.onSurfaceMuted,
            )
        }

        Spacer(Modifier.height(Space.x14))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.x8),
            horizontalArrangement = Arrangement.spacedBy(Space.x10),
        ) {
            HeaderCell("Ay", 1.1f, TextAlign.Start)
            HeaderCell("Katkı", 1f, TextAlign.End)
            HeaderCell("Ay sonu", 1.2f, TextAlign.End)
            HeaderCell("Getiri", 1f, TextAlign.End)
        }
        KefeHairline()

        rows.forEachIndexed { index, row ->
            if (index > 0) KefeHairline()
            val muted = row.contribution <= 0.0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.x10),
            ) {
                BodyCell(
                    text = row.monthLabel,
                    weight = 1.1f,
                    align = TextAlign.Start,
                    color = if (muted) c.onSurfaceMuted else c.onSurface,
                )
                BodyCell(
                    text = if (muted) "katkı yok" else Money.tl(row.contribution),
                    weight = 1f,
                    align = TextAlign.End,
                    color = if (muted) c.onSurfaceMuted else c.onSurface,
                )
                BodyCell(
                    text = Money.tl(row.monthEnd),
                    weight = 1.2f,
                    align = TextAlign.End,
                    color = c.onSurface,
                )
                // Isaret her zaman yazilir: renk tek sinyal degildir.
                BodyCell(
                    text = Money.tlSigned(row.gain),
                    weight = 1f,
                    align = TextAlign.End,
                    color = c.delta(row.gain),
                )
            }
        }

        if (state.rows.size > state.collapsedRows.size) {
            Spacer(Modifier.height(Space.x8))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.touchTarget)
                    .clip(KefeShapes.button)
                    .clickable { onIntent(GoalDetailIntent.ToggleAllRows) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.showAllRows) {
                        "Daha az göster"
                    } else {
                        "${state.rows.size} ayın tamamını gör"
                    },
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                )
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign) {
    Text(
        text = text.trUpper(),
        style = KefeTheme.type.label(11, 0.06, FontWeight.SemiBold),
        color = KefeTheme.colors.onSurfaceMuted,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.BodyCell(text: String, weight: Float, align: TextAlign, color: Color) {
    Text(
        text = text,
        style = KefeTheme.type.caption.tabular(),
        color = color,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

// --- Yardimci --------------------------------------------------------------

private fun List<Double>.toPoints(): List<Point> =
    mapIndexed { index, value -> Point(index.toFloat(), value.toFloat()) }
