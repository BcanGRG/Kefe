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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.Goal
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
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeOfflineBanner
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeStaleBanner
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.ui.layout.KefeSidePanel
import com.kefe.app.ui.layout.KefeTopBar
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import com.kefe.app.ui.format.UnknownChangeText

/**
 * Ozet - masaustu yerlesimi (1440x900 cercevesi).
 *
 * Telefon yerlesiminin genisletilmisi DEGILDIR, farkli bir kompozisyondur:
 *  - hero dikey yigin olmaktan cikip TEK SATIRA yayilir (rakam solda, iki
 *    degisim satiri ortada yan yana, birim cipleri sagda),
 *  - ana hedef ve dagilim ayri ayri satirlar yerine 1.35fr / 1fr IZGARADA
 *    yan yana durur; dagilim halkasi ile okuma anahtari da yan yanadir,
 *  - net deger grafigi genisler (196dp) ve donem cipleri baslik satirinin
 *    sagina gecer,
 *  - piyasa kalici bir SAG PANELE tasinir.
 *
 * Yan navigasyon (240dp) uygulama kabugunun isidir; bkz. KefeSideNavigation.
 */
@Composable
fun SummaryScreenDesktop(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onAddAsset: () -> Unit = {},
    modifier: Modifier = Modifier,
    marketRows: List<KefeMarketRow> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onOpenMarketRow: ((KefeMarketRow) -> Unit)? = null,
) {
    Column(modifier.fillMaxSize()) {
        KefeTopBar(
            title = "Özet",
            subtitle = state.desktopContextLine(),
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            masked = state.masked,
            onToggleMask = { onIntent(SummaryIntent.ToggleMask) },
            onRefresh = { onIntent(SummaryIntent.Refresh) },
        )

        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ContentPad,
                        end = ContentPad,
                        top = Space.x20,
                        bottom = Space.x28,
                    ),
            ) {
                when (state.freshness) {
                    // Ilk fiyatlar yolday iken serit cizilmez: uyaracak bir sey yok.
                    PriceFreshness.Loading -> Unit

                    PriceFreshness.Stale -> {
                        KefeStaleBanner(
                            text = "Fiyatlar 2 saatten eski",
                            actionText = "Yenile",
                            onAction = { onIntent(SummaryIntent.Refresh) },
                            clockIcon = KefeIcons.Clock,
                        )
                        Spacer(Modifier.height(Space.x16))
                    }

                    PriceFreshness.Offline -> {
                        KefeOfflineBanner(
                            line1 = "Çevrimdışı · Son bilinen fiyatlarla",
                            line2 = if (state.pendingSyncCount > 0) {
                                "${state.pendingSyncCount} kayıt eşitlenmeyi bekliyor"
                            } else {
                                "Bağlanınca fiyatlar güncellenecek"
                            },
                            cloudOffIcon = KefeIcons.CloudOff,
                        )
                        Spacer(Modifier.height(Space.x16))
                    }

                    PriceFreshness.Fresh -> Unit
                }

                val totals = state.totals
                when {
                    state.stage == SummaryStage.Loading || totals == null -> DesktopSkeleton()
                    state.stage == SummaryStage.Empty -> DesktopEmpty(onOpenGoals, onAddAsset)
                    else -> DesktopContent(
                        state = state,
                        totals = totals,
                        onIntent = onIntent,
                        onOpenGoal = onOpenGoal,
                        onOpenGoals = onOpenGoals,
                    )
                }
            }

            KefeSidePanel(
                title = "Piyasa",
                updatedAt = state.pricesUpdatedAt,
                rows = marketRows,
                note = "Fon fiyatları günde bir kez güncellenir. " +
                    "Kaynak çökerse fiyatı elle girebilirsiniz.",
                onRowClick = onOpenMarketRow,
            )
        }
    }
}

// --- Icerik ----------------------------------------------------------------

@Composable
private fun ColumnScope.DesktopContent(
    state: SummaryUiState,
    totals: PortfolioTotals,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    HeroRow(state, totals, onIntent)

    Spacer(Modifier.height(Space.x10))
    Text(
        text = monthLine(totals, state.masked),
        style = t.caption.tabular(),
        color = c.onSurfaceMuted,
        maxLines = 1,
    )

    Spacer(Modifier.height(Space.x20))
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Space.x16),
    ) {
        val goal = state.mainGoal
        if (goal != null) {
            DesktopGoalCard(
                goal = goal,
                currentWealth = totals.totalValue,
                masked = state.masked,
                otherGoalCount = state.otherGoalCount,
                onClick = { onOpenGoal(goal.id) },
                onSeeAll = onOpenGoals,
                modifier = Modifier.weight(1.35f).fillMaxHeight(),
            )
        }
        DesktopAllocationCard(
            allocation = state.allocation,
            masked = state.masked,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    Spacer(Modifier.height(Space.x16))
    DesktopNetWorthCard(state, onIntent)
}

// --- Hero satiri -----------------------------------------------------------

@Composable
private fun HeroRow(
    state: SummaryUiState,
    totals: PortfolioTotals,
    onIntent: (SummaryIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.x32),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                text = "toplam birikim".trUpper(),
                style = t.label(11, 0.11, FontWeight.SemiBold),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.masked) {
                    Money.masked()
                } else {
                    state.unit.formatTotal(totals.totalValue, state.rates)
                },
                style = t.display.tabular(),
                color = c.onSurface,
                maxLines = 1,
            )
        }

        Column(
            modifier = Modifier.padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DesktopDeltaRow(totals.todayChange, totals.todayChangePercent, "bugün", state.masked)
            DesktopDeltaRow(
                totals.profit,
                totals.profitPercent,
                "toplam getiri",
                state.masked,
                decimals = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
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
    }
}

@Composable
private fun DesktopDeltaRow(
    amount: Double?,
    percent: Double?,
    caption: String,
    masked: Boolean,
    // Tasarim gunluk degisimi iki, toplam getiriyi tek ondalikla yazar.
    decimals: Int = 2,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    // Bilinmeyen degisim "—" yazilir (bkz. SummaryScreen.DeltaRow).
    if (amount == null || percent == null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(UnknownChangeText, style = t.bodyStrong.tabular(), color = c.onSurfaceMuted)
            Text(caption, style = t.caption, color = c.onSurfaceMuted, maxLines = 1)
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
            maxLines = 1,
        )
        // Yuzde maskelenmez: oran bilgisi bakiye kadar hassas degil.
        Text(
            text = Money.delta(percent, decimals),
            style = t.body.tabular(),
            color = c.delta(amount),
            maxLines = 1,
        )
        Text(caption, style = t.caption, color = c.onSurfaceMuted, maxLines = 1)
    }
}

// --- Ana hedef -------------------------------------------------------------

@Composable
private fun DesktopGoalCard(
    goal: Goal,
    currentWealth: Double,
    masked: Boolean,
    otherGoalCount: Int,
    onClick: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val progress = goal.progress(currentWealth)
    val remaining = (goal.amount - currentWealth).coerceAtLeast(0.0)

    KefeAccentCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(CardPad),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(GoalIconBox)
                    .clip(KefeShapes.button)
                    .background(c.accentMuted),
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(
                    icon = KefeIcons.goalIcon(goal.iconKey),
                    contentDescription = null,
                    size = 22.dp,
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
                    text = buildString {
                        append(if (masked) Money.masked(4, false) else Money.tl(currentWealth))
                        append(" / ")
                        append(Money.tl(goal.amount))
                        append(" · ")
                        append(if (masked) Money.masked(4, false) else Money.tl(remaining))
                        append(" kaldı")
                    },
                    style = t.micro.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = Money.ratioOf(progress.toDouble()),
                style = t.h1.copy(letterSpacing = 0.em).tabular(),
                color = c.accent,
            )
        }

        Spacer(Modifier.height(Space.x14))
        KefeProgressBar(progress = progress)

        Spacer(Modifier.height(Space.x10))
        Row(verticalAlignment = Alignment.CenterVertically) {
            goal.estimatedArrival?.let { arrival ->
                Text(
                    // Kesikli alt cizgi "tahmin" isareti: kesin tarih vaadi degildir.
                    text = "Tahmini varış ≈ ${arrival.formatMonthYear()}",
                    style = t.caption,
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    modifier = Modifier.dashedUnderline(c.outline),
                )
            }
            Spacer(Modifier.weight(1f))
            if (otherGoalCount > 0) {
                Text(
                    text = "+$otherGoalCount hedef daha",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    maxLines = 1,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = null,
                        role = Role.Button,
                        onClick = onSeeAll,
                    ),
                )
            }
        }
    }
}

// --- Dagilim ---------------------------------------------------------------

@Composable
private fun DesktopAllocationCard(
    allocation: List<AllocationSlice>,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    if (allocation.isEmpty()) return

    val slices = allocation.map {
        DonutSlice(it.assetClass.label(), it.value, c.assetClass(it.assetClass.color()))
    }
    val largest = allocation.maxByOrNull { it.value }

    KefeCard(modifier = modifier, contentPadding = PaddingValues(CardPad)) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(CardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeDonutChart(
                slices = slices,
                centerLabel = largest?.assetClass?.label().orEmpty(),
                centerValue = Money.ratio(largest?.percent ?: 0.0),
                size = DonutSize,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                slices.forEach { slice ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.x8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(LegendDot)
                                .clip(RoundedCornerShape(2.dp))
                                .background(slice.color),
                        )
                        Text(
                            text = slice.label,
                            style = t.captionSmall,
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
                            style = t.captionSmall.tabular(),
                            color = c.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// --- Net deger -------------------------------------------------------------

@Composable
private fun DesktopNetWorthCard(state: SummaryUiState, onIntent: (SummaryIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    if (state.netWorthTotal.size < 2) return

    KefeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(CardPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Net değer",
                style = t.bodyStrong,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Masaustu donem cipi: 32dp, 12dp yan dolgu, 12sp (handoff).
                DesktopPeriods.forEach { range ->
                    KefeChip(
                        text = range.label,
                        selected = state.range == range,
                        onClick = { onIntent(SummaryIntent.SelectPeriod(range)) },
                        height = Sizes.chipSmall,
                        horizontalPadding = Space.x12,
                        textStyle = t.captionSmall,
                        selectedTextStyle = t.captionSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.x14))
        KefeNetWorthChart(
            total = state.netWorthTotal.toDesktopPoints(),
            principal = state.netWorthPrincipal.toDesktopPoints(),
            goalLine = state.mainGoal?.amount,
            // Hedef etiketi grafigin icinde degil, altta okuma anahtarinda durur.
            goalLabel = null,
            selectedIndex = null,
            modifier = Modifier.fillMaxWidth().height(ChartHeight),
        )

        Spacer(Modifier.height(Space.x10))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Space.x16),
            ) {
                LegendItem("Toplam değer") { LineSwatch(c.accent, 2.4.dp) }
                LegendItem("Yatırdığım anapara") { LineSwatch(c.onSurfaceMuted, 1.6.dp) }
                LegendItem("Getiri") { AreaSwatch(c.accent) }
                state.mainGoal?.let { goal ->
                    LegendItem(
                        text = "₺${Money.compact(goal.amount)} ana hedef",
                        color = c.accent,
                    ) { DashedSwatch(c.accent) }
                }
            }
            Text(
                text = SeriesRangeLabel,
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
            )
        }
    }
}

/** Kesikli cizgi ornegi - hedef cizgisi okuma anahtari. */
@Composable
private fun DashedSwatch(color: Color) {
    Box(Modifier.width(14.dp).height(2.dp).dashedUnderline(color))
}

// --- Durumlar --------------------------------------------------------------

@Composable
private fun DesktopSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x16)) {
        KefeSkeletonBlock(width = 104.dp, height = 10.dp)
        KefeSkeletonBlock(width = 260.dp, height = 40.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x16)) {
            KefeSkeletonBlock(height = 168.dp, radius = 16.dp, modifier = Modifier.weight(1.35f))
            KefeSkeletonBlock(height = 168.dp, radius = 16.dp, modifier = Modifier.weight(1f))
        }
        KefeSkeletonBlock(height = 300.dp, radius = 16.dp)
    }
}

@Composable
private fun DesktopEmpty(onOpenGoals: () -> Unit, onAddAsset: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        modifier = Modifier.width(EmptyWidth),
        verticalArrangement = Arrangement.spacedBy(Space.x16),
    ) {
        Text("Birikiminiz burada görünecek.", style = t.h1, color = c.onSurface)
        Text(
            text = "Kefe bankaya bağlanmaz. Varlıklarınızı siz girersiniz, " +
                "Kefe güncel fiyatlarla TL karşılığını hesaplar.",
            style = t.body,
            color = c.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x12)) {
            KefePrimaryButton("Hedef ekle", onClick = onOpenGoals, modifier = Modifier.weight(1f))
            KefeSecondaryButton("Varlık ekle", onClick = onAddAsset, modifier = Modifier.weight(1f))
        }
    }
}

// --- Yardimci --------------------------------------------------------------

/**
 * Tasarimda bu satir "Hedeflere sayılan · Hariç · Bu ay eklenen" seklinde uc
 * parcaliydi. Hedeflerden haric tutma urunden cikarildigi icin yalniz
 * "bu ay eklenen" kismi kalir.
 */
private fun monthLine(totals: PortfolioTotals, masked: Boolean): String = buildString {
    append("Bu ay eklenen ")
    append(if (masked) Money.masked(4, false) else Money.tl(totals.monthAdded))
    append(" / ")
    append(Money.tl(totals.monthTarget))
}

/** Ust cubuk baglam satiri: portfoy · uyeler · fiyat saati. */
private fun SummaryUiState.desktopContextLine(): String = buildString {
    append(portfolioName)
    if (members.isNotEmpty()) {
        append(" · ")
        append(members.joinToString(" ve ") { it.name })
    }
    if (pricesUpdatedAt.isNotBlank()) {
        append(" · fiyatlar ")
        append(pricesUpdatedAt)
        append("'de güncellendi")
    }
}

private fun List<Double>.toDesktopPoints(): List<Point> =
    mapIndexed { index, value -> Point(index.toFloat(), value.toFloat()) }

/**
 * Masaustunde gorunen donemler.
 *
 * Telefondaki yedi secenegin hepsi degil: genis ekranda grafik zaten aylik
 * hareketi okunur kiliyor, kisa vade (Gün/Hafta) telefonun isi.
 */
private val DesktopPeriods = listOf(
    NetWorthRange.Week,
    NetWorthRange.Month3,
    NetWorthRange.Month6,
    NetWorthRange.Year1,
    NetWorthRange.All,
)

// --- Olculer ---------------------------------------------------------------

private val ContentPad = Space.x28
private val CardPad = 18.dp
private val GoalIconBox = 38.dp
private val DonutSize = 118.dp
private val LegendDot = 8.dp
private val ChartHeight = 196.dp
private val EmptyWidth = 420.dp

/** Ornek seride grafik araligi. */
private const val SeriesRangeLabel = "Ağu 2025 — Tem 2026"
