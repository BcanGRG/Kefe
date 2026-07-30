package com.kefe.app.ui.screens.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.TopMover
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.formatMonthYear
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.progress
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.charts.ChartLegendItem
import com.kefe.app.ui.charts.DonutSlice
import com.kefe.app.ui.charts.KefeChartLegend
import com.kefe.app.ui.charts.KefeDonutChart
import com.kefe.app.ui.charts.KefeDonutLegend
import com.kefe.app.ui.charts.KefeNetWorthChart
import com.kefe.app.ui.charts.LegendMark
import com.kefe.app.ui.charts.Point
import com.kefe.app.ui.components.KefeAvatar
import com.kefe.app.ui.components.KefeAvatarStack
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeDashedCard
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefeMainGoalBadge
import com.kefe.app.ui.components.KefeManualBadge
import com.kefe.app.ui.components.KefeOfflineBanner
import com.kefe.app.ui.components.KefePendingBadge
import com.kefe.app.ui.components.KefePeriodChips
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeProgressBarThin
import com.kefe.app.ui.components.KefePullToRefresh
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeStaleBanner
import com.kefe.app.ui.components.KefeSyncChip
import com.kefe.app.ui.components.SyncStatus
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Radius
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Ozet - en kritik ekran. "Toplam ne kadar var, hedefe ne kaldi" sorusunu bir
 * saniyede yanitlar. Tek kahraman rakam kurali burada uygulanir: hero disindaki
 * hicbir rakam Display olcegine cikmaz.
 *
 * OLCULER: 390x844 telefon cercevesindeki handoff'tan birebir alinmistir. Kart
 * bosluklari LazyColumn'un ortak araligiyla degil, her blogun kendi dolgusuyla
 * verilir; cunku handoff'ta bolum basliklari (20/6) kartlardan (0/12) farkli
 * bosluk tasir.
 */
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onAddAsset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SummaryTopBar(state, onIntent, onOpenMarket)

        // Seritler kaydirma alaninin DISINDA kalir - fiyat guveni her zaman gorunur.
        when (state.freshness) {
            // Ilk fiyatlar yolday iken serit cizilmez: uyaracak bir sey yok.
            PriceFreshness.Loading -> Unit
            PriceFreshness.Stale -> KefeStaleBanner(
                text = "Fiyatlar 2 saatten eski",
                actionText = "Yenile",
                onAction = { onIntent(SummaryIntent.Refresh) },
                clockIcon = KefeIcons.Clock,
                strip = true,
            )

            PriceFreshness.Offline -> KefeOfflineBanner(
                line1 = "Çevrimdışı · Son bilinen fiyatlarla",
                line2 = if (state.pendingSyncCount > 0) {
                    "${state.pendingSyncCount} kayıt eşitlenmeyi bekliyor"
                } else {
                    "Bağlanınca fiyatlar güncellenecek"
                },
                cloudOffIcon = KefeIcons.CloudOff,
                strip = true,
            )

            PriceFreshness.Fresh -> Unit
        }

        // Bos durumda cekip-yenileme YOK: tazelenecek fiyat yok, jest anlamsiz.
        // Ilk kayittan sonra (Loading/Ready) devreye girer.
        if (state.stage == SummaryStage.Empty) {
            SummaryEmpty(onOpenGoals = onOpenGoals, onAddAsset = onAddAsset)
        } else {
            // Asagi cekip yenileme - fiyat tazelemenin telefondaki refleks hareketi.
            // Ust bardaki dugme duruyor; bu onun yerine degil yanina.
            KefePullToRefresh(
                refreshing = state.refreshing,
                onRefresh = { onIntent(SummaryIntent.Refresh) },
            ) {
                when (state.stage) {
                    SummaryStage.Loading -> SummarySkeleton()
                    SummaryStage.Ready -> SummaryContent(
                        state = state,
                        onIntent = onIntent,
                        onOpenGoal = onOpenGoal,
                        onOpenGoals = onOpenGoals,
                        onOpenActivity = onOpenActivity,
                        onOpenMarket = onOpenMarket,
                    )
                    // Bos durum yukarida ayrildi; buraya dusmez.
                    SummaryStage.Empty -> Unit
                }
            }
        }
    }
}

// --- Ust bar ---------------------------------------------------------------

/**
 * Ust bar: `padding:2px 16px 10px`, satir yuksekligi 32dp.
 *
 * Ikon butonlari 44dp dokunma hedefini korur ama satiri buyutmez: handoff'ta
 * `margin:-6px` ile satirin disina tasarlar. Karsiligi `requiredSize` - satir
 * 32dp kalirken buton 44dp olculur ve dikeyde ortalanip tasar.
 */
@Composable
private fun SummaryTopBar(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenMarket: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier.padding(
            start = Space.x16,
            // Son ikon butonu handoff'ta `margin-right:-10px` ile saga tasar.
            end = Space.x16 - 10.dp,
            top = 2.dp,
            bottom = Space.x10,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TopBarRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.portfolioName,
                    style = t.bodyStrong,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.members.isNotEmpty()) {
                    Spacer(Modifier.width(Space.x8))
                    KefeAvatarStack(
                        members = state.members.mapIndexed { index, m -> m.initials to index },
                    )
                }
            }

            // Bos durumda (ilk kayittan once) senkron cipi, gizle ve yenile
            // CIZILMEZ: eslenecek veri, gizlenecek bakiye, tazeleyip
            // gorunur kilacak fiyat yok. Ilk varlik eklenince hepsi gelir.
            if (state.stage != SummaryStage.Empty) {
                Spacer(Modifier.width(Space.x8))
                KefeSyncChip(
                    state = when (state.freshness) {
                        PriceFreshness.Offline -> SyncStatus.Offline
                        else -> if (state.refreshing) SyncStatus.Pending else SyncStatus.Synced
                    },
                )

                Spacer(Modifier.width(Space.x8))
                KefeIconButton(
                    icon = if (state.masked) KefeIcons.EyeOff else KefeIcons.Eye,
                    contentDescription = if (state.masked) "Bakiyeleri göster" else "Bakiyeleri gizle",
                    onClick = { onIntent(SummaryIntent.ToggleMask) },
                    modifier = Modifier.requiredSize(Sizes.touchTarget),
                )
                // Goz butonunun `margin-right:-4px` degeri araligi 8'den 4'e indirir.
                Spacer(Modifier.width(Space.x4))
                KefeIconButton(
                    icon = KefeIcons.Refresh,
                    contentDescription = "Fiyatları yenile",
                    onClick = { onIntent(SummaryIntent.Refresh) },
                    modifier = Modifier.requiredSize(Sizes.touchTarget),
                )
            }
        }

        state.priceLine()?.let { line ->
            Spacer(Modifier.height(Space.x4))
            // Fiyat satiri Piyasa ekranina goturur - telefonda oraya baska yol yok.
            Text(
                text = line,
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onOpenMarket,
                ),
            )
        }
    }
}

/** Ust bardaki fiyat satiri; yukleme ve bos halde yazilmaz. */
private fun SummaryUiState.priceLine(): String? = when {
    stage != SummaryStage.Ready -> null
    // Devam eden istek once yazilir. Satir, istek yolday iken bile eski damgayi
    // gosterip duruyordu; ekranda hicbir sey degismedigi icin yenileme calismiyor
    // gibi okunuyordu.
    refreshing -> "Fiyatlar güncelleniyor…"
    freshness == PriceFreshness.Loading -> "Fiyatlar alınıyor…"
    pricesUpdatedAt.isBlank() -> null
    freshness == PriceFreshness.Offline -> "Son bilinen fiyatlar · $pricesUpdatedAt"
    else -> "Fiyatlar $pricesUpdatedAt${timeLocative(pricesUpdatedAt)} güncellendi"
}

/**
 * "14:32'de" / "12:05'te" - saat metnine Turkce bulunma eki.
 *
 * Ek son SAYININ OKUNUSUNA gore secilir (otuz iki -> "de", beş -> "te");
 * tek bir sabit ek her saatte yanlis okunur.
 */
private fun timeLocative(time: String): String {
    val minute = time.trim().substringAfterLast(':').takeLast(2).toIntOrNull() ?: return "'de"
    return when (minute % 10) {
        1, 2, 7, 8 -> "'de"
        3, 4, 5 -> "'te"
        6, 9 -> "'da"
        else -> when (minute / 10) {
            2, 5 -> "'de"   // yirmi, elli
            4 -> "'ta"      // kirk
            else -> "'da"   // sifir, on, otuz
        }
    }
}

// --- Icerik ----------------------------------------------------------------

@Composable
private fun SummaryContent(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMarket: () -> Unit,
) {
    val totals = state.totals ?: return

    LazyColumn(Modifier.fillMaxWidth()) {
        item { HeroSection(state, totals, onIntent) }

        state.mainGoal?.let { goal ->
            item {
                MainGoalCard(
                    goal = goal,
                    currentWealth = state.mainGoalWealth,
                    masked = state.masked,
                    otherGoalCount = state.otherGoalCount,
                    onClick = { onOpenGoal(goal.id) },
                    onSeeAll = onOpenGoals,
                    modifier = Modifier.cardMargin(),
                )
            }
        }

        item {
            AllocationCard(
                allocation = state.allocation,
                total = totals.totalValue,
                masked = state.masked,
                modifier = Modifier.cardMargin(),
            )
        }

        item { NetWorthCard(state, onIntent, Modifier.cardMargin()) }

        item {
            MonthCard(
                totals = totals,
                masked = state.masked,
                // Bu ay karti ile "Son hareketler" basligi arasindaki bosluk
                // basligin kendi 20dp ust dolgusundan gelir.
                modifier = Modifier.padding(horizontal = Space.x16),
            )
        }

        // Piyasa telefonda GORUNUR bir yer kazandi.
        //
        // Tek girisi ust bardaki "Fiyatlar 19:38'de güncellendi" satiriydi: gri,
        // kucuk ve durum etiketi gibi duruyordu; masaustunde sag panelde acikca
        // duran bolumun telefonda karsiligi yok sanildi.
        if (state.marketRows.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Piyasa",
                    actionText = "Tümünü gör",
                    onAction = onOpenMarket,
                )
            }
            item {
                MarketCard(
                    rows = state.marketRows.take(MarketPreviewCount),
                    onOpen = onOpenMarket,
                    modifier = Modifier.padding(horizontal = Space.x16),
                )
            }
        }

        if (state.activity.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Son hareketler",
                    actionText = "Tümünü gör",
                    onAction = onOpenActivity,
                )
            }
            item {
                ActivityCard(
                    activity = state.activity,
                    members = state.members,
                    pendingCount = if (state.freshness == PriceFreshness.Offline) {
                        state.pendingSyncCount
                    } else {
                        0
                    },
                    onOpen = onOpenActivity,
                    modifier = Modifier.padding(horizontal = Space.x16),
                )
            }
        }

        if (state.topGainer != null || state.topLoser != null) {
            item { SectionHeader(title = "Öne çıkan hareketler") }
            item { TopMoversRow(state.topGainer, state.topLoser, state.masked) }
        }

        // Listenin alt dolgusu: hangi blok sonuncu olursa olsun 24dp bosluk kalir.
        item { Spacer(Modifier.height(Space.x24)) }
    }
}

/** Kart bloklarinin ortak kenar boslugu: `margin:0 16px 12px`. */
private fun Modifier.cardMargin(): Modifier =
    this.padding(start = Space.x16, end = Space.x16, bottom = Space.x12)

/** Bolum basligi: `padding:0 16px; margin:20px 0 6px`. */
@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x20,
                bottom = SectionHeaderGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = t.bodyStrong, color = c.onSurface)
        if (actionText != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = actionText,
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickableText(onAction),
            )
        }
    }
}

// --- Hero ------------------------------------------------------------------

@Composable
private fun HeroSection(
    state: SummaryUiState,
    totals: PortfolioTotals,
    onIntent: (SummaryIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier.padding(
            start = Space.x16,
            end = Space.x16,
            top = Space.x14,
            bottom = HeroBottomPadding,
        ),
    ) {
        Text(
            text = "toplam birikim".trUpper(),
            style = t.label(11, 0.11, FontWeight.SemiBold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(HeroLabelGap))

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

        Spacer(Modifier.height(Space.x10))
        DeltaRow(
            amount = totals.todayChange,
            percent = totals.todayChangePercent,
            caption = "bugün",
            masked = state.masked,
            decimals = 2,
        )
        Spacer(Modifier.height(DeltaRowGap))
        DeltaRow(
            amount = totals.profit,
            percent = totals.profitPercent,
            caption = "toplam getiri",
            masked = state.masked,
            decimals = 1,
        )

        Spacer(Modifier.height(Space.x14))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            val chipText = t.caption.copy(fontWeight = FontWeight.SemiBold)
            DisplayUnit.entries.forEach { unit ->
                KefeChip(
                    text = unit.chipLabel,
                    selected = state.unit == unit,
                    onClick = { onIntent(SummaryIntent.SelectUnit(unit)) },
                    height = Sizes.chipLarge,
                    horizontalPadding = Space.x16,
                    textStyle = chipText,
                    selectedTextStyle = chipText,
                )
            }
        }
    }
}

/** Degisim satiri: [ok] [tutar] [yuzde] [aciklama], aralar 6dp. */
@Composable
private fun DeltaRow(
    amount: Double,
    percent: Double,
    caption: String,
    masked: Boolean,
    decimals: Int,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val positive = amount >= 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DeltaGap),
    ) {
        KefeIcon(
            icon = if (positive) KefeIcons.ArrowUp else KefeIcons.ArrowDown,
            contentDescription = null,
            size = DeltaArrowSize,
            tint = c.delta(amount),
        )
        Text(
            text = if (masked) maskedSigned(positive) else Money.tlSigned(amount),
            style = t.bodyStrong.tabular(),
            color = c.delta(amount),
        )
        // Yuzde maskelenmez: oran bilgisi bakiye kadar hassas degil.
        Text(Money.delta(percent, decimals), style = t.body.tabular(), color = c.delta(amount))
        Text(caption, style = t.caption, color = c.onSurfaceMuted)
    }
}

/** Gizli bakiyede de isaret korunur - renk tek sinyal olamaz. */
private fun maskedSigned(positive: Boolean): String =
    (if (positive) "+" else Money.MINUS.toString()) + Money.masked(digits = 4)

// --- Ana hedef -------------------------------------------------------------

@Composable
private fun MainGoalCard(
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

    KefeCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    size = IconSize.default,
                    tint = c.accent,
                )
            }
            Spacer(Modifier.width(Space.x12))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = goal.name,
                        style = t.bodyStrong,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (goal.isMain) {
                        Spacer(Modifier.width(Space.x8))
                        KefeMainGoalBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = goal.targetDate.formatMonthYear(),
                    style = t.caption,
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Space.x12))
            KefeIcon(KefeIcons.ChevronRight, null, size = IconSize.medium)
        }

        Spacer(Modifier.height(Space.x14))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = buildString {
                    append(if (masked) Money.masked(digits = 4) else Money.tl(currentWealth))
                    append(" / ")
                    append(Money.tl(goal.amount))
                },
                style = t.body.tabular(),
                color = c.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Money.ratioOf(progress.toDouble()),
                style = t.h2.tabular(),
                color = c.accent,
                modifier = Modifier.alignByBaseline(),
            )
        }

        Spacer(Modifier.height(Space.x8))
        KefeProgressBar(progress = progress)

        Spacer(Modifier.height(Space.x10))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (masked) Money.masked(digits = 4) else Money.tl(remaining)) + " kaldı",
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
            )
            goal.estimatedArrival?.let { arrival ->
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(Space.x8))
                Row(
                    modifier = Modifier
                        .dashedUnderline(c.outline)
                        .padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EstimateGap),
                ) {
                    KefeIcon(KefeIcons.Info, null, size = DeltaArrowSize)
                    // "Tahmini" ifadesi bilerek one cikar: kesin tarih vaadi degildir.
                    Text(
                        text = "Tahmini varış ≈ ${arrival.formatMonthYear()}",
                        style = t.caption,
                        color = c.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
            }
        }

        if (otherGoalCount > 0) {
            Spacer(Modifier.height(Space.x12))
            KefeHairline()
            Spacer(Modifier.height(Space.x12))
            Row(
                modifier = Modifier.clickableText(onSeeAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                Text(
                    text = "+$otherGoalCount hedef daha",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                )
                KefeIcon(KefeIcons.ChevronRight, null, size = IconSize.tiny, tint = c.accent)
            }
        }
    }
}

// --- Dagilim ---------------------------------------------------------------

@Composable
private fun AllocationCard(
    allocation: List<AllocationSlice>,
    total: Double,
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

    KefeCard(modifier = modifier) {
        Text("Ne kadarı nerede", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x14))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KefeDonutChart(
                slices = slices,
                centerLabel = largest?.assetClass?.label().orEmpty(),
                centerValue = Money.ratio(largest?.percent ?: 0.0),
            )
        }
        // Halka ile anahtar arasindaki 6dp; satirlarin kendi 10dp dolgusu ustune biner.
        Spacer(Modifier.height(DonutLegendGap))
        KefeDonutLegend(slices = slices, total = total, masked = masked, divided = true)
    }
}

// --- Net deger -------------------------------------------------------------

@Composable
private fun NetWorthCard(
    state: SummaryUiState,
    onIntent: (SummaryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = KefeTheme.type
    val c = KefeTheme.colors
    if (state.netWorthTotal.size < 2) return

    KefeCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Net değer", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            Text(periodCaption(state.periodIndex), style = t.caption, color = c.onSurfaceMuted)
        }

        Spacer(Modifier.height(Space.x12))
        KefePeriodChips(
            selectedIndex = state.periodIndex,
            onSelect = { onIntent(SummaryIntent.SelectPeriod(it)) },
        )

        Spacer(Modifier.height(Space.x12))
        KefeNetWorthChart(
            total = state.netWorthTotal.toPoints(),
            principal = state.netWorthPrincipal.toPoints(),
            goalLine = state.mainGoal?.amount,
            goalLabel = state.mainGoal?.let { "₺${Money.compact(it.amount)} ana hedef" },
            selectedIndex = state.netWorthTotal.lastIndex,
            selectedLabel = "Tem 2026",
            selectedValue = if (state.masked) {
                Money.masked(digits = 4)
            } else {
                Money.tl(state.netWorthTotal.last())
            },
            modifier = Modifier.fillMaxWidth().height(NetWorthChartHeight),
        )

        Spacer(Modifier.height(Space.x14))
        KefeChartLegend(
            items = listOf(
                ChartLegendItem("Toplam değer", c.accent, LegendMark.Line),
                ChartLegendItem("Yatırdığım anapara", c.onSurfaceMuted, LegendMark.ThinLine),
                ChartLegendItem("Getiri", c.accent, LegendMark.Area),
            ),
        )
    }
}

/** Grafik basligindaki donem aciklamasi - secili cip ile ayni kaynaktan. */
private fun periodCaption(index: Int): String = when (index) {
    0 -> "Son 1 ay"
    1 -> "Son 3 ay"
    2 -> "Son 6 ay"
    3 -> "Son 12 ay"
    else -> "Tüm zamanlar"
}

private fun List<Double>.toPoints(): List<Point> =
    mapIndexed { index, value -> Point(index.toFloat(), value.toFloat()) }

// --- Bu ay -----------------------------------------------------------------

@Composable
private fun MonthCard(
    totals: PortfolioTotals,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val progress = if (totals.monthTarget <= 0.0) {
        0f
    } else {
        (totals.monthAdded / totals.monthTarget).toFloat().coerceIn(0f, 1f)
    }

    KefeCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Bu ay eklenen", style = t.caption, color = c.onSurfaceMuted)
                Spacer(Modifier.height(Space.x4))
                Row {
                    Text(
                        text = if (masked) Money.masked(digits = 4) else Money.tl(totals.monthAdded),
                        style = t.h2.tabular(),
                        color = c.onSurface,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(DeltaGap))
                    Text(
                        text = "/ ${Money.tl(totals.monthTarget)} hedef",
                        style = t.caption.tabular(),
                        color = c.onSurfaceMuted,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Text(
                text = Money.ratioOf(progress.toDouble()),
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
            )
        }
        Spacer(Modifier.height(Space.x12))
        KefeProgressBarThin(progress = progress)
    }
}

// --- Piyasa ----------------------------------------------------------------

/** Ozet'te gosterilen piyasa satiri sayisi; tamami Piyasa ekraninda. */
private const val MarketPreviewCount = 5

/**
 * Piyasa onizlemesi.
 *
 * Masaustunde sag panelde surekli duran tablo telefonda YOKTU; tek giris ust
 * bardaki gri fiyat satiriydi ve o bir baglanti gibi gorunmuyordu.
 *
 * Fiyatlar burada portfoy tutarlarindan DAHA COK ondalikla yazilir: gram altin
 * dakikalar icinde kurus mertebesinde oynar, tam liraya yuvarlanirsa ekran
 * "hic degismiyor" gibi gorunur.
 */
@Composable
private fun MarketCard(
    rows: List<KefeMarketRow>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) KefeHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = null, onClick = onOpen)
                    .padding(horizontal = ActivityRowPaddingH, vertical = Space.x12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c.assetClass(row.assetClass)),
                )
                Spacer(Modifier.width(Space.x10))
                Text(
                    text = row.name,
                    style = t.body,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x8))
                Text(
                    text = row.priceText,
                    style = t.body.tabular(),
                    color = c.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.x10))
                // Isaret her zaman yazilir; yon bilgisi renge birakilmaz.
                Text(
                    text = Money.delta(row.changePercent),
                    style = t.caption.tabular(),
                    color = c.delta(row.changePercent),
                    maxLines = 1,
                )
            }
        }
    }
}

// --- Aktivite --------------------------------------------------------------

/**
 * Son hareketler karti. Satirlar kartin kendi dolgusunu degil kendi
 * `12px 14px` dolgularini kullanir; boylece dokunma alani satirin tamamidir
 * ve ayirici cizgiler kart kenarina kadar uzanir.
 */
@Composable
private fun ActivityCard(
    activity: List<ActivityEvent>,
    members: List<Member>,
    pendingCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        activity.forEachIndexed { index, event ->
            if (index > 0) KefeHairline()
            val memberIndex = members.indexOfFirst { it.id == event.memberId }.coerceAtLeast(0)
            val member = members.getOrNull(memberIndex)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = null, onClick = onOpen)
                    .padding(horizontal = ActivityRowPaddingH, vertical = Space.x12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KefeAvatar(
                    initials = member?.initials.orEmpty(),
                    index = memberIndex,
                    size = Sizes.avatarMedium,
                )
                Spacer(Modifier.width(Space.x10))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Bekleyen kayit rozeti satirin ICINDE, metnin hemen ardinda durur.
                    if (index < pendingCount) {
                        Spacer(Modifier.width(DeltaGap))
                        KefePendingBadge()
                    }
                }
                Spacer(Modifier.width(Space.x10))
                Text(event.timeLabel, style = t.micro, color = c.onSurfaceMuted, maxLines = 1)
            }
        }
    }
}

// --- One cikan hareketler --------------------------------------------------

@Composable
private fun TopMoversRow(gainer: TopMover?, loser: TopMover?, masked: Boolean) {
    if (gainer == null && loser == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(horizontal = Space.x16),
        horizontalArrangement = Arrangement.spacedBy(MoverGridGap),
    ) {
        MoverSlot(gainer, "en çok kazandıran", masked)
        MoverSlot(loser, "en çok gerileyen", masked)
    }
}

/** Bos slot da yer tutar: iki sutunlu izgara tek kartla bozulmaz. */
@Composable
private fun RowScope.MoverSlot(mover: TopMover?, label: String, masked: Boolean) {
    if (mover == null) {
        Spacer(Modifier.weight(1f))
    } else {
        TopMoverCard(
            label = label,
            mover = mover,
            masked = masked,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TopMoverCard(
    label: String,
    mover: TopMover,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val positive = mover.profit >= 0

    KefeCard(modifier = modifier, contentPadding = PaddingValues(Space.x12)) {
        Text(
            text = label.trUpper(),
            style = t.label(10, 0.06, FontWeight.Bold),
            color = c.onSurfaceMuted,
        )

        Spacer(Modifier.height(Space.x8))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MoverIconGap),
        ) {
            KefeIcon(
                icon = assetIcon(mover.position.assetClass),
                contentDescription = null,
                size = IconSize.medium,
                tint = c.assetClass(mover.position.assetClass.color()),
            )
            Text(
                text = mover.position.name,
                style = t.caption.copy(fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
                color = c.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(Space.x8))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EstimateGap),
        ) {
            KefeIcon(
                icon = if (positive) KefeIcons.ArrowUp else KefeIcons.ArrowDown,
                contentDescription = null,
                size = MoverArrowSize,
                tint = c.delta(mover.profit),
            )
            Text(
                text = if (masked) maskedSigned(positive) else Money.tlSigned(mover.profit),
                style = t.bodyStrong.tabular(),
                color = c.delta(mover.profit),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(Space.x8))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeltaGap),
        ) {
            Text(
                text = Money.delta(mover.profitPercent, 1),
                style = t.micro.tabular(),
                color = c.delta(mover.profit),
                maxLines = 1,
            )
            if (mover.position.manualPrice) {
                KefeManualBadge(icon = KefeIcons.Pencil, text = "Elle fiyat")
            }
        }
    }
}

private fun assetIcon(assetClass: AssetClass) = when (assetClass) {
    AssetClass.Gold -> KefeIcons.Gold
    AssetClass.Silver -> KefeIcons.Silver
    AssetClass.Fx -> KefeIcons.Fx
    AssetClass.Fund -> KefeIcons.Fund
    AssetClass.Cash -> KefeIcons.Cash
}

// --- Durumlar --------------------------------------------------------------

/**
 * Yukleme iskeleti. Shimmer YOKTUR; olculer dolu halin yerlesimini birebir
 * taklit eder ki icerik gelince siçrama olmasin.
 */
@Composable
private fun SummarySkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x14,
                bottom = HeroBottomPadding,
            ),
        ) {
            KefeSkeletonBlock(width = 104.dp, height = 10.dp, radius = 6.dp)
            Spacer(Modifier.height(Space.x10))
            KefeSkeletonBlock(width = 232.dp, height = 40.dp, radius = 10.dp)
            Spacer(Modifier.height(Space.x14))
            KefeSkeletonBlock(width = 184.dp, height = 14.dp, radius = 6.dp)
            Spacer(Modifier.height(Space.x8))
            KefeSkeletonBlock(width = 160.dp, height = 14.dp, radius = 6.dp)
            Spacer(Modifier.height(Space.x16))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                repeat(3) {
                    KefeSkeletonBlock(
                        width = SkeletonChipWidth,
                        height = Sizes.chipLarge,
                        radius = Radius.pill,
                    )
                }
                KefeSkeletonBlock(
                    width = SkeletonWideChipWidth,
                    height = Sizes.chipLarge,
                    radius = Radius.pill,
                )
            }
            Spacer(Modifier.height(Space.x16))
            KefeSkeletonBlock(width = 264.dp, height = 12.dp, radius = 6.dp)
        }

        KefeSkeletonBlock(
            height = 168.dp,
            radius = Radius.card,
            bordered = true,
            modifier = Modifier.cardMargin(),
        )

        // Dagilim karti iskeleti - kart ici bloklar sunken tonda.
        Column(
            Modifier
                .cardMargin()
                .clip(KefeShapes.card)
                .background(KefeTheme.colors.surfaceElevated)
                .cardOutline()
                .padding(Space.x16),
        ) {
            KefeSkeletonBlock(width = 132.dp, height = 14.dp, radius = 6.dp, inCard = true)
            Spacer(Modifier.height(Space.x16))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Dolu daire degil HALKA: dolu halin 14dp kalinligini taklit eder.
                Box(
                    Modifier
                        .size(DonutSize)
                        .border(DonutStroke, KefeTheme.colors.surfaceSunken, CircleShape),
                )
            }
            Spacer(Modifier.height(Space.x16))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x14)) {
                listOf(1f, 0.88f, 0.76f, 0.64f).forEach { fraction ->
                    KefeSkeletonBlock(
                        height = 12.dp,
                        radius = 6.dp,
                        inCard = true,
                        modifier = Modifier.fillMaxWidth(fraction),
                    )
                }
            }
        }

        KefeSkeletonBlock(
            height = 212.dp,
            radius = Radius.card,
            bordered = true,
            modifier = Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                bottom = Space.x24,
            ),
        )
    }
}

@Composable
private fun SummaryEmpty(onOpenGoals: () -> Unit, onAddAsset: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x24,
                bottom = Space.x8,
            ),
        ) {
            Text("Birikiminiz burada\ngörünecek.", style = t.h1, color = c.onSurface)
            Spacer(Modifier.height(Space.x12))
            Text(
                text = "Kefe bankaya bağlanmaz. Varlıklarınızı siz eklersiniz, " +
                    "biz güncel fiyatlarla TL'ye çeviririz.",
                style = t.body.copy(lineHeight = 22.sp),
                color = c.onSurfaceMuted,
            )
        }

        Column(
            Modifier
                .padding(
                    start = Space.x16,
                    end = Space.x16,
                    top = Space.x20,
                    bottom = Space.x12,
                )
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .cardOutline(),
        ) {
            EmptyStep(
                index = "1",
                title = "Hedefini belirle",
                subtitle = "Ne için biriktiriyorsunuz?",
                actionText = "Hedef ekle",
                primary = true,
                onAction = onOpenGoals,
            )
            KefeHairline()
            EmptyStep(
                index = "2",
                title = "İlk varlığını ekle",
                subtitle = "Altın, gümüş, döviz, fon, nakit",
                actionText = "Varlık ekle",
                primary = false,
                onAction = onAddAsset,
            )
        }

        KefeDashedCard(
            modifier = Modifier.padding(horizontal = Space.x16),
            contentPadding = PaddingValues(horizontal = Space.x16, vertical = Space.x20),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EmptyBoxGap),
            ) {
                KefeIcon(KefeIcons.Balance, null, size = IconSize.default)
                Text(
                    text = Money.tl(0.0, spaced = true),
                    style = t.h2.tabular(),
                    color = c.onSurfaceMuted,
                )
                Text(
                    text = "İki kefe de boş. İlk kaydınızdan sonra toplam ve " +
                        "hedef ilerlemesi burada.",
                    style = t.caption,
                    color = c.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EmptyStep(
    index: String,
    title: String,
    subtitle: String,
    actionText: String,
    primary: Boolean,
    onAction: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier.fillMaxWidth().padding(Space.x16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.avatarMedium)
                .clip(KefeShapes.pill)
                .background(if (primary) c.accentMuted else c.surfaceSunken)
                .then(if (primary) Modifier else Modifier.cardOutline(KefeShapes.pill)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = t.caption.copy(fontWeight = FontWeight.Bold),
                color = if (primary) c.accent else c.onSurfaceMuted,
            )
        }
        Spacer(Modifier.width(Space.x12))

        Column(Modifier.weight(1f)) {
            Text(title, style = t.bodyStrong, color = c.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = t.caption, color = c.onSurfaceMuted, maxLines = 1)
        }
        Spacer(Modifier.width(Space.x12))

        // Adim butonlari 44dp'dir (52dp birincil butondan farkli) - handoff.
        Box(
            modifier = Modifier
                .height(Sizes.touchTarget)
                .clip(KefeShapes.button)
                .background(if (primary) c.accent else Color.Transparent)
                .then(if (primary) Modifier else Modifier.cardOutline(KefeShapes.button))
                .clickable(onClick = onAction)
                .padding(horizontal = Space.x16),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionText,
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (primary) c.onAccent else c.onSurface,
                maxLines = 1,
            )
        }
    }
}

// --- Yardimci --------------------------------------------------------------

/** Metin baglantilari icin dokunma alani; gorsel geri bildirim istenmez. */
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(indication = null, interactionSource = null, onClick = onClick)

/** Kart kenarligi - golge yok, yalnizca 1dp cizgi. */
@Composable
private fun Modifier.cardOutline(shape: Shape = KefeShapes.card): Modifier =
    this.border(Sizes.hairline, KefeTheme.colors.outline, shape)

// Kesikli alt cizgi (`Modifier.dashedUnderline`) ayni pakette SummaryScreenTablet
// icinde tanimli; tekrar edilmez.

// --- Olculer (handoff birebir) ---------------------------------------------

/** Ust bar satiri: ikon butonlari tasar, satir esitleme cipiyle ayni yukseklikte. */
private val TopBarRowHeight: Dp = 32.dp

/** Hero: etiket -> rakam 6dp, blogun alt dolgusu 18dp. */
private val HeroLabelGap: Dp = 6.dp
private val HeroBottomPadding: Dp = 18.dp

/** Degisim satirlari: ic aralik 6dp, iki satir arasi 5dp, ok 14dp. */
private val DeltaGap: Dp = 6.dp
private val DeltaRowGap: Dp = 5.dp
private val DeltaArrowSize: Dp = 14.dp

/** Bolum basligi ile altindaki blok arasi. */
private val SectionHeaderGap: Dp = 6.dp

/** Ana hedef karti ikon kutusu. */
private val GoalIconBox: Dp = 40.dp

/** "Tahmini varış" satirindaki ikon/metin araligi. */
private val EstimateGap: Dp = 5.dp

/** Halka ile anahtar arasi. */
private val DonutLegendGap: Dp = 6.dp
private val DonutSize: Dp = 148.dp
private val DonutStroke: Dp = 14.dp

/** Net deger grafigi yuksekligi. */
private val NetWorthChartHeight: Dp = 162.dp

/** Aktivite satirinin yatay dolgusu (dikey 12dp). */
private val ActivityRowPaddingH: Dp = 14.dp

/** One cikan hareketler izgarasi. */
private val MoverGridGap: Dp = 10.dp
private val MoverIconGap: Dp = 7.dp
private val MoverArrowSize: Dp = 13.dp

/** Iskeletteki birim cipleri. */
private val SkeletonChipWidth: Dp = 48.dp
private val SkeletonWideChipWidth: Dp = 92.dp

/** Bos haldeki kesikli kutunun ic araligi. */
private val EmptyBoxGap: Dp = 6.dp
