package com.kefe.app.ui.screens.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.formatMonthYear
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.progress
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.charts.DonutSlice
import com.kefe.app.ui.charts.KefeDonutChart
import com.kefe.app.ui.charts.KefeNetWorthChart
import com.kefe.app.ui.charts.Point
import com.kefe.app.ui.components.KefeAccentCard
import com.kefe.app.ui.components.KefeAvatar
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeOfflineBanner
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeProgressBarThin
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeStaleBanner
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import com.kefe.app.ui.format.UnknownChangeText

/**
 * Ozet - tablet yerlesimi (840x1180 cercevesi).
 *
 * Telefonun tek kolonu IKI KOLONA acilir ve sirasi degisir: solda kahraman
 * rakam, ana hedef ve "bu ay"; sagda dagilim, net deger ve son hareketler.
 * Hero artik cerceve icindedir (telefonda ciplakti) ve birim cipleri hero'dan
 * cikip ust cubuga tasinir.
 *
 * Navigasyon rayi bu ekranin PARCASI DEGILDIR; uygulama kabugu saglar
 * (bkz. KefeNavigationRail).
 */
@Composable
fun SummaryScreenTablet(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onAddAsset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TabletTopBar(state = state, onIntent = onIntent, onOpenMarket = onOpenMarket)

        when (state.freshness) {
            // Ilk fiyatlar yolday iken serit cizilmez: uyaracak bir sey yok.
            PriceFreshness.Loading -> Unit
            PriceFreshness.Stale -> KefeStaleBanner(
                text = "Fiyatlar 2 saatten eski",
                actionText = "Yenile",
                onAction = { onIntent(SummaryIntent.Refresh) },
                clockIcon = KefeIcons.Clock,
                modifier = Modifier.padding(horizontal = PagePad, vertical = Space.x12),
            )

            PriceFreshness.Offline -> KefeOfflineBanner(
                line1 = "Çevrimdışı · Son bilinen fiyatlarla",
                line2 = if (state.pendingSyncCount > 0) {
                    "${state.pendingSyncCount} kayıt eşitlenmeyi bekliyor"
                } else {
                    "Bağlanınca fiyatlar güncellenecek"
                },
                cloudOffIcon = KefeIcons.CloudOff,
                modifier = Modifier.padding(horizontal = PagePad, vertical = Space.x12),
            )

            PriceFreshness.Fresh -> Unit
        }

        val totals = state.totals
        when {
            state.stage == SummaryStage.Loading || totals == null -> TabletSkeleton()
            state.stage == SummaryStage.Empty -> TabletEmpty(onOpenGoals, onAddAsset)
            else -> TabletContent(
                state = state,
                totals = totals,
                onOpenGoal = onOpenGoal,
                onOpenGoals = onOpenGoals,
                onOpenActivity = onOpenActivity,
                onIntent = onIntent,
            )
        }
    }
}

// --- Ust cubuk -------------------------------------------------------------

@Composable
private fun TabletTopBar(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenMarket: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = Sizes.hairline.toPx()
                drawRect(
                    color = c.outline,
                    topLeft = Offset(0f, size.height - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .padding(start = PagePad, end = PagePad, top = Space.x20, bottom = Space.x16),
        horizontalArrangement = Arrangement.spacedBy(Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Özet", style = t.h2, color = c.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.contextLine(),
                // Baglam satiri Piyasa ekranina goturur.
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onOpenMarket,
                ),
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            // Birim cipi: 40dp, 14dp yan dolgu, iki durumda da 13sp/600 (handoff).
            val chipText = t.caption.copy(fontWeight = FontWeight.SemiBold)
            DisplayUnit.entries.forEach { unit ->
                KefeChip(
                    text = unit.chipLabel,
                    selected = state.unit == unit,
                    onClick = { onIntent(SummaryIntent.SelectUnit(unit)) },
                    height = Sizes.chipLarge,
                    horizontalPadding = Space.x14,
                    textStyle = chipText,
                    selectedTextStyle = chipText,
                )
            }
        }

        OutlinedIconButton(
            icon = if (state.masked) KefeIcons.EyeOff else KefeIcons.Eye,
            contentDescription = if (state.masked) "Tutarları göster" else "Tutarları gizle",
            onClick = { onIntent(SummaryIntent.ToggleMask) },
        )
        OutlinedIconButton(
            icon = KefeIcons.Refresh,
            contentDescription = "Fiyatları yenile",
            onClick = { onIntent(SummaryIntent.Refresh) },
        )
    }
}

// --- Icerik ----------------------------------------------------------------

@Composable
private fun TabletContent(
    state: SummaryUiState,
    totals: PortfolioTotals,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onIntent: (SummaryIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = PagePad, end = PagePad, top = Space.x20, bottom = Space.x28),
        horizontalArrangement = Arrangement.spacedBy(Space.x20),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.x16),
        ) {
            HeroCard(state, totals)
            state.mainGoal?.let { goal ->
                TabletGoalCard(
                    goal = goal,
                    currentWealth = totals.totalValue,
                    masked = state.masked,
                    onClick = { onOpenGoal(goal.id) },
                )
            }
            MonthCard(totals, state.masked)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.x16),
        ) {
            AllocationCard(state.allocation, totals.totalValue, state.masked)
            NetWorthCard(state)
            if (state.activity.isNotEmpty()) {
                ActivityCard(state.activity, state.members, state.masked, onOpenActivity)
            }
        }
    }
}

// --- Hero ------------------------------------------------------------------

@Composable
private fun HeroCard(state: SummaryUiState, totals: PortfolioTotals) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.x24),
    ) {
        Text(
            text = "toplam birikim".trUpper(),
            style = t.label(11, 0.11, FontWeight.SemiBold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x8))
        Text(
            text = if (state.masked) {
                Money.masked()
            } else {
                state.unit.formatTotal(totals.totalValue, state.rates)
            },
            style = t.display.tabular(),
            color = c.onSurface,
        )
        Spacer(Modifier.height(Space.x14))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x24)) {
            DeltaItem(totals.todayChange, "bugün", state.masked)
            DeltaItem(totals.profit, "toplam getiri", state.masked)
        }
    }
}

/** Tablet hero'sunda yuzde YOKTUR - yalniz tutar ve etiket. */
@Composable
private fun DeltaItem(amount: Double?, caption: String, masked: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    // Bilinmeyen degisim "—" yazilir (bkz. SummaryScreen.DeltaRow).
    if (amount == null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(UnknownChangeText, style = t.bodyStrong.tabular(), color = c.onSurfaceMuted)
            Text(caption, style = t.caption, color = c.onSurfaceMuted)
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = if (amount >= 0) KefeIcons.ArrowUpRight else KefeIcons.ArrowDownRight,
            contentDescription = null,
            size = 14.dp,
            tint = c.delta(amount),
        )
        Text(
            text = if (masked) Money.masked(4, false) else Money.tlSigned(amount),
            style = t.bodyStrong.tabular(),
            color = c.delta(amount),
        )
        Text(caption, style = t.caption, color = c.onSurfaceMuted)
    }
}

// --- Ana hedef -------------------------------------------------------------

@Composable
private fun TabletGoalCard(
    goal: Goal,
    currentWealth: Double,
    masked: Boolean,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val progress = goal.progress(currentWealth)
    val remaining = (goal.amount - currentWealth).coerceAtLeast(0.0)

    KefeAccentCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(Space.x20),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(GoalIconBoxTablet)
                    .clip(KefeShapes.button)
                    .background(c.accentMuted),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(
                    icon = KefeIcons.goalIcon(goal.iconKey),
                    contentDescription = null,
                    size = 24.dp,
                    tint = c.accent,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.x8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(goal.name, style = t.bodyStrong, color = c.onSurface, maxLines = 1)
                    if (goal.isMain) MainGoalPill()
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = goal.targetDate.formatMonthYear(),
                    style = t.caption,
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }
            Text(
                text = Money.ratioOf(progress.toDouble()),
                style = t.h1.copy(letterSpacing = 0.em).tabular(),
                color = c.accent,
            )
        }

        Spacer(Modifier.height(Space.x16))
        KefeProgressBar(progress = progress)

        Spacer(Modifier.height(Space.x10))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(if (masked) Money.masked(4, false) else Money.tl(currentWealth))
                    append(" / ")
                    append(Money.tl(goal.amount))
                    append(" · ")
                    append(if (masked) Money.masked(4, false) else Money.tl(remaining))
                    append(" kaldı")
                },
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            goal.estimatedArrival?.let { arrival ->
                Spacer(Modifier.width(Space.x8))
                Text(
                    // "Tahmini" bilgisi kesikli alt cizgiyle isaretlenir: kesin tarih degildir.
                    text = "≈ ${arrival.formatMonthYear()}",
                    style = t.caption.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    modifier = Modifier.dashedUnderline(c.outline),
                )
            }
        }
    }
}

// --- Bu ay -----------------------------------------------------------------

@Composable
private fun MonthCard(totals: PortfolioTotals, masked: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val progress = if (totals.monthTarget <= 0.0) {
        0f
    } else {
        (totals.monthAdded / totals.monthTarget).toFloat().coerceIn(0f, 1f)
    }

    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.x20),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Bu ay eklenen", style = t.caption, color = c.onSurfaceMuted)
                Spacer(Modifier.height(Space.x4))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (masked) Money.masked(4, false) else Money.tl(totals.monthAdded),
                        style = t.h2.tabular(),
                        color = c.onSurface,
                    )
                    Spacer(Modifier.width(Space.x8))
                    Text(
                        text = "/ ${Money.tl(totals.monthTarget)} hedef",
                        style = t.caption.tabular(),
                        color = c.onSurfaceMuted,
                    )
                }
            }
            Text(
                text = Money.ratioOf(progress.toDouble()),
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
            )
        }
        Spacer(Modifier.height(Space.x14))
        KefeProgressBarThin(progress = progress)
    }
}

// --- Dagilim ---------------------------------------------------------------

@Composable
private fun AllocationCard(allocation: List<AllocationSlice>, total: Double, masked: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    if (allocation.isEmpty()) return

    val slices = allocation.map {
        DonutSlice(it.assetClass.label(), it.value, c.assetClass(it.assetClass.color()))
    }
    val largest = allocation.maxByOrNull { it.value }

    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.x20),
    ) {
        Text("Ne kadarı nerede", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x14))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x20),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeDonutChart(
                slices = slices,
                centerLabel = largest?.assetClass?.label().orEmpty(),
                centerValue = Money.ratio(largest?.percent ?: 0.0),
                size = 148.dp,
            )
            Column(Modifier.weight(1f)) {
                slices.forEachIndexed { index, slice ->
                    if (index > 0) KefeHairline()
                    val percent = if (total > 0.0) slice.value / total * 100.0 else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.x10),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(slice.color),
                        )
                        Text(
                            text = slice.label,
                            style = t.caption,
                            color = c.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (masked) {
                                Money.masked(4, false)
                            } else {
                                Money.tl(slice.value)
                            },
                            style = t.caption.tabular(),
                            color = c.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = Money.ratio(percent, 1),
                            style = t.micro.tabular(),
                            color = c.onSurfaceMuted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.width(42.dp),
                        )
                    }
                }
            }
        }
    }
}

// --- Net deger -------------------------------------------------------------

@Composable
private fun NetWorthCard(state: SummaryUiState) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    if (state.netWorthTotal.size < 2) return

    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Space.x20),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Net değer",
                style = t.bodyStrong,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text("Son 12 ay", style = t.caption, color = c.onSurfaceMuted)
        }
        Spacer(Modifier.height(Space.x12))
        KefeNetWorthChart(
            total = state.netWorthTotal.toChartPoints(),
            principal = state.netWorthPrincipal.toChartPoints(),
            goalLine = state.mainGoal?.amount,
            goalLabel = state.mainGoal?.let { "₺${Money.compact(it.amount)} ana hedef" },
            selectedIndex = state.netWorthTotal.lastIndex,
            selectedLabel = NetWorthLastLabel,
            selectedValue = if (state.masked) {
                Money.masked(4, false)
            } else {
                Money.tl(state.netWorthTotal.last())
            },
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Spacer(Modifier.height(Space.x10))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x14)) {
            LegendItem("Toplam değer") { LineSwatch(c.accent, 2.4.dp) }
            LegendItem("Anapara") { LineSwatch(c.onSurfaceMuted, 1.6.dp) }
            LegendItem("Getiri") { AreaSwatch(c.accent) }
        }
    }
}

// --- Son hareketler --------------------------------------------------------

@Composable
private fun ActivityCard(
    activity: List<ActivityEvent>,
    members: List<Member>,
    masked: Boolean,
    onSeeAll: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.x20, end = Space.x20, top = Space.x16, bottom = Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Son hareketler",
                style = t.bodyStrong,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Tümünü gör",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = null,
                    role = Role.Button,
                    onClick = onSeeAll,
                ),
            )
        }

        activity.forEach { event ->
            KefeHairline()
            val memberIndex = members.indexOfFirst { it.id == event.memberId }.coerceAtLeast(0)
            val member = members.getOrNull(memberIndex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x20, vertical = Space.x12),
                horizontalArrangement = Arrangement.spacedBy(Space.x10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KefeAvatar(
                    initials = member?.initials.orEmpty(),
                    index = memberIndex,
                    size = Sizes.avatarMedium,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(member?.name.orEmpty())
                        }
                        append(" · ")
                        append(event.description)
                    },
                    style = t.caption,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                event.amount?.let { amount ->
                    Text(
                        text = if (masked) Money.masked(4, false) else Money.tl(amount),
                        style = t.caption.tabular(),
                        color = c.onSurface,
                        maxLines = 1,
                    )
                }
                Text(
                    text = event.timeLabel,
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
    }
}

// --- Durumlar --------------------------------------------------------------

@Composable
private fun TabletSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = PagePad, end = PagePad, top = Space.x20, bottom = Space.x28),
        horizontalArrangement = Arrangement.spacedBy(Space.x20),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.x16),
        ) {
            KefeSkeletonBlock(height = 168.dp, radius = 16.dp)
            KefeSkeletonBlock(height = 148.dp, radius = 16.dp)
            KefeSkeletonBlock(height = 96.dp, radius = 16.dp)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.x16),
        ) {
            KefeSkeletonBlock(height = 220.dp, radius = 16.dp)
            KefeSkeletonBlock(height = 268.dp, radius = 16.dp)
        }
    }
}

@Composable
private fun TabletEmpty(onOpenGoals: () -> Unit, onAddAsset: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        modifier = Modifier
            .padding(horizontal = PagePad, vertical = Space.x24)
            .width(EmptyColumnWidth),
        verticalArrangement = Arrangement.spacedBy(Space.x16),
    ) {
        Text("Birikiminiz burada görünecek.", style = t.h1, color = c.onSurface)
        Text(
            text = "Kefe bankaya bağlanmaz. Varlıklarınızı siz girersiniz, " +
                "Kefe güncel fiyatlarla TL karşılığını hesaplar.",
            style = t.body,
            color = c.onSurfaceMuted,
        )
        KefePrimaryButton("Hedef ekle", onClick = onOpenGoals, modifier = Modifier.fillMaxWidth())
        KefeSecondaryButton("Varlık ekle", onClick = onAddAsset, modifier = Modifier.fillMaxWidth())
    }
}

// --- Ortak kucuk parcalar --------------------------------------------------

/** 44dp kenarlikli ikon butonu - tablet ust cubugu. */
@Composable
private fun OutlinedIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .border(
                width = Sizes.hairline,
                color = if (hovered) c.onSurfaceMuted else c.outline,
                shape = KefeShapes.button,
            )
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = 22.dp,
            tint = if (hovered) c.onSurface else c.onSurfaceMuted,
        )
    }
}

/** "Ana hedef" rozeti - tasarimda 10sp/700/.08em ve 8x3 dolgulu. */
@Composable
internal fun MainGoalPill() {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(c.accentMuted)
            .padding(horizontal = Space.x8, vertical = 3.dp),
    ) {
        Text(
            text = "ana hedef".trUpper(),
            style = KefeTheme.type.label(10, 0.08, FontWeight.Bold),
            color = c.accent,
            maxLines = 1,
        )
    }
}

@Composable
internal fun LegendItem(
    text: String,
    color: Color = KefeTheme.colors.onSurfaceMuted,
    swatch: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatch()
        Text(text, style = KefeTheme.type.micro, color = color, maxLines = 1)
    }
}

@Composable
internal fun LineSwatch(color: Color, thickness: Dp) {
    Box(
        Modifier
            .width(14.dp)
            .height(thickness)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
internal fun AreaSwatch(color: Color) {
    Box(
        Modifier
            .width(12.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.3f)),
    )
}

/** Kesikli alt cizgi - "tahmin" isaretlemesi. */
internal fun Modifier.dashedUnderline(color: Color): Modifier = this.drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2f),
        end = Offset(size.width, size.height - stroke / 2f),
        strokeWidth = stroke,
        cap = StrokeCap.Butt,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

internal fun List<Double>.toChartPoints(): List<Point> =
    mapIndexed { index, value -> Point(index.toFloat(), value.toFloat()) }

/** Ust cubuk baglam satiri: portfoy adi + fiyat saati. */
internal fun SummaryUiState.contextLine(): String = buildString {
    append(portfolioName)
    if (pricesUpdatedAt.isNotBlank()) {
        append(" · Fiyatlar ")
        append(pricesUpdatedAt)
        append("'de güncellendi")
    }
}

// --- Olculer ---------------------------------------------------------------

private val PagePad = Space.x24
private val GoalIconBoxTablet = 40.dp
private val EmptyColumnWidth = 360.dp

/** Seri son noktasinin etiketi - ornek veride Temmuz 2026. */
internal const val NetWorthLastLabel = "Tem 2026"
