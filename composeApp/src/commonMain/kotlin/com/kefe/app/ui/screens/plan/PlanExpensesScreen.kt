package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.ui.components.KefeBadge
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeProgressBarThin
import com.kefe.app.ui.components.KefeSegmentedControl
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeTextButton
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * "Harcamalar" sayfasi (bkz. ExpensesPage.kt): ustte kalem cipleri, ozet, tek
 * kalemde gunluk grafik, altta gun gun liste. Satira dokunmak harcamayi, alttaki
 * dugme yeni harcamayi acar - ikisi de kabuktaki Plan sheet'i.
 *
 * Liste KOMPAKT (Ekim 2026): gun basligi kucuk, soluk bir etiket; o gunun
 * harcamalari altinda AYRI bir kartta. Once baslik ve satirlar ayni boyda, ayni
 * hizadaydi ve kullaniciya "ic ice" gorunuyordu.
 */
@Composable
fun PlanExpensesScreen(
    state: PlanExpensesUiState,
    onIntent: (PlanExpensesIntent) -> Unit,
    onBack: () -> Unit,
    onAdd: (ExpenseCategory?) -> Unit,
    onEdit: (String) -> Unit,
    onEditBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val page = state.page

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = page?.title ?: "Harcamalar",
                subtitle = page?.subtitle.orEmpty(),
                onBack = onBack,
            )
            if (page == null) {
                Column(
                    Modifier.padding(horizontal = Space.x16, vertical = Space.x8),
                    verticalArrangement = Arrangement.spacedBy(Space.x12),
                ) {
                    KefeSkeletonBlock(height = 36.dp, radius = 18.dp)
                    KefeSkeletonBlock(height = 200.dp, radius = 16.dp)
                    KefeSkeletonBlock(height = 320.dp, radius = 16.dp)
                }
            } else {
                PageBody(page, state.sort, onIntent, onEdit, onEditBudget, Modifier.weight(1f))
            }
        }

        // Ekleme dugmesi listenin USTUNDE, altta sabit: uzun listede de bir dokunus.
        if (page != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(start = Space.x16, end = Space.x16, top = Space.x12, bottom = Space.x16),
            ) {
                KefePrimaryButton(
                    text = page.addLabel,
                    onClick = { onAdd(page.addCategory) },
                    leadingIcon = KefeIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String, onBack: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x8, end = Space.x16, bottom = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIconButton(icon = KefeIcons.ArrowBack, contentDescription = "Geri", onClick = onBack, tint = c.onSurface)
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.h2, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = t.caption, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PageBody(
    page: ExpensesPageUi,
    sort: ExpenseSort,
    onIntent: (PlanExpensesIntent) -> Unit,
    onEdit: (String) -> Unit,
    onEditBudget: () -> Unit,
    modifier: Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val select = { filter: ExpenseFilter -> onIntent(PlanExpensesIntent.SelectFilter(filter)) }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = BottomBarSpace)) {
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.x16),
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                modifier = Modifier.padding(top = Space.x8, bottom = Space.x12),
            ) {
                items(page.chips, key = { it.filter.key() ?: "all" }) { chip ->
                    KefeChip(
                        text = "${chip.label} ${chip.count}",
                        selected = chip.selected,
                        onClick = { select(chip.filter) },
                    )
                }
            }
        }
        item(key = "summary") {
            Column(
                Modifier.padding(horizontal = Space.x16),
                verticalArrangement = Arrangement.spacedBy(Space.x12),
            ) {
                SummaryCards(page.summary, select, onEditBudget)
                page.daily?.let { DailyCard(it) }
            }
        }

        item(key = "listHeader") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x16, end = Space.x16, top = Space.x16, bottom = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (page.countLabel ?: "harcama yok").trUpper(),
                    style = t.micro.copy(fontWeight = FontWeight.SemiBold),
                    color = c.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                if (page.countLabel != null) {
                    KefeSegmentedControl(
                        options = listOf("Tarih", "Tutar"),
                        selectedIndex = if (sort == ExpenseSort.Date) 0 else 1,
                        onSelect = { onIntent(PlanExpensesIntent.SelectSort(if (it == 0) ExpenseSort.Date else ExpenseSort.Amount)) },
                        modifier = Modifier.width(SortWidth),
                    )
                }
            }
        }

        page.emptyText?.let { text ->
            item(key = "empty") {
                Text(
                    text,
                    style = t.body,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = Space.x16, vertical = Space.x16),
                )
            }
        }

        // Her gun: kucuk etiket + o gunun harcamalari tek kartta.
        items(page.groups, key = { "g-${it.title}" }) { group ->
            Column(Modifier.padding(horizontal = Space.x16)) {
                DayHeader(group)
                LinesCard(group.items, onEdit)
            }
        }
        if (page.ranked.isNotEmpty()) {
            item(key = "ranked") {
                Column(Modifier.padding(start = Space.x16, end = Space.x16, top = Space.x12)) {
                    LinesCard(page.ranked, onEdit)
                }
            }
        }
    }
}

// --- Ozet ------------------------------------------------------------------------

@Composable
private fun SummaryCards(summary: ExpensesSummaryUi, select: (ExpenseFilter) -> Unit, onEditBudget: () -> Unit) {
    when (summary) {
        is ExpensesSummaryUi.Overview -> Column(verticalArrangement = Arrangement.spacedBy(Space.x12)) {
            summary.budget?.let { budget ->
                KefeCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Aylık giderlere göre")
                    Spacer(Modifier.height(Space.x8))
                    BudgetedSummary(budget, editLabel = null, onEditBudget = onEditBudget)
                }
            }
            KefeCard(Modifier.fillMaxWidth()) {
                if (summary.budget != null) {
                    SectionLabel("Kalemlere göre")
                    Spacer(Modifier.height(Space.x8))
                }
                Headline(summary.total, summary.line, large = summary.budget == null)
                if (summary.split.isNotEmpty()) {
                    Spacer(Modifier.height(Space.x12))
                    SplitBar(summary.split)
                    Spacer(Modifier.height(Space.x4))
                    summary.split.forEach { SplitLegendRow(it, select) }
                }
            }
        }

        is ExpensesSummaryUi.Unbudgeted -> KefeCard(Modifier.fillMaxWidth()) {
            Headline(summary.total, summary.line, large = true)
            if (summary.split.size > 1) {
                Spacer(Modifier.height(Space.x8))
                summary.split.forEach { SplitLegendRow(it, select) }
            }
            StatsGrid(summary.stats)
        }

        is ExpensesSummaryUi.Budgeted -> KefeCard(Modifier.fillMaxWidth()) {
            BudgetedSummary(summary, editLabel = "Sınırı düzenle", onEditBudget = onEditBudget)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.trUpper(),
        style = KefeTheme.type.micro.copy(fontWeight = FontWeight.SemiBold),
        color = KefeTheme.colors.onSurfaceMuted,
    )
}

@Composable
private fun Headline(total: String, line: String, large: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Text(total, style = (if (large) t.h1 else t.h2).tabular(), color = c.onSurface)
    Text(line, style = t.caption.tabular(), color = c.onSurfaceMuted)
}

@Composable
private fun SplitBar(rows: List<SplitRowUi>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(SplitBarHeight)
            .clip(CircleShape)
            .background(KefeTheme.colors.surfaceSunken),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.filter { it.weight > 0f }.forEach { row ->
            Box(Modifier.weight(row.weight).fillMaxHeight().background(categoryColor(row.colorIndex)))
        }
    }
}

@Composable
private fun SplitLegendRow(row: SplitRowUi, select: (ExpenseFilter) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(onClickLabel = "Harcamalarını göster", role = Role.Button) { select(row.filter) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(categoryColor(row.colorIndex)))
        Spacer(Modifier.width(Space.x10))
        Text(row.label, style = t.body, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.x8))
        Text(row.share, style = t.micro.tabular(), color = c.onSurfaceMuted)
        Spacer(Modifier.width(Space.x8))
        Text(row.amount, style = t.body.tabular(), color = c.onSurface)
    }
}

/** Sinira karsi durum: tek kalemde "Sınırı düzenle"li, tumunde aylik giderlere gore. */
@Composable
private fun BudgetedSummary(s: ExpensesSummaryUi.Budgeted, editLabel: String?, onEditBudget: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(verticalAlignment = Alignment.Bottom) {
        Text(s.spent, style = t.h1.tabular(), color = c.onSurface)
        Spacer(Modifier.width(Space.x8))
        Text("/ ${s.limit}", style = t.body.tabular(), color = c.onSurfaceMuted, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
        editLabel?.let { KefeTextButton(text = it, onClick = onEditBudget) }
    }
    Spacer(Modifier.height(Space.x8))
    // Cubuk ve ustunde "bugun" cizgisi: ayin ne kadari gectiyse orada.
    BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
        KefeProgressBarThin(
            progress = s.ratio,
            color = if (s.over) c.negative else c.accent,
            height = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        s.todayRatio?.let { ratio ->
            Box(
                Modifier
                    .offset(x = maxWidth * ratio - 1.dp)
                    .width(2.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(c.onSurface),
            )
        }
    }
    Spacer(Modifier.height(Space.x4))
    Row(Modifier.fillMaxWidth()) {
        Text(s.spentText, style = t.micro.tabular(), color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
        s.todayText?.let { Text(it, style = t.micro.tabular(), color = c.onSurfaceMuted) }
    }
    s.pace?.let { pace ->
        Spacer(Modifier.height(Space.x10))
        val color = when (pace.tone) {
            PaceTone.Over -> c.negative
            PaceTone.Fast -> c.warning
            PaceTone.OnTrack -> c.positive
        }
        Row(verticalAlignment = Alignment.Top) {
            KefeIcon(
                if (pace.tone == PaceTone.OnTrack) KefeIcons.Check else KefeIcons.Info,
                null,
                size = 16.dp,
                tint = color,
                modifier = Modifier.padding(top = 1.dp),
            )
            Spacer(Modifier.width(Space.x8))
            Text(pace.text, style = t.caption, color = color)
        }
    }
    StatsGrid(s.stats)
}

@Composable
private fun StatsGrid(stats: List<StatUi>) {
    if (stats.isEmpty()) return
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Spacer(Modifier.height(Space.x12))
    Column(verticalArrangement = Arrangement.spacedBy(Space.x8)) {
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
                pair.forEach { stat ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(KefeShapes.button)
                            .background(c.surfaceSunken)
                            .padding(horizontal = Space.x12, vertical = Space.x10),
                    ) {
                        Text(stat.label, style = t.micro.copy(fontWeight = FontWeight.SemiBold), color = c.onSurfaceMuted)
                        Text(
                            stat.value,
                            style = t.bodyStrong.tabular(),
                            color = if (stat.negative) c.negative else c.onSurface,
                            maxLines = 1,
                        )
                        Text(stat.note, style = t.micro, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// --- Gunluk ----------------------------------------------------------------------

@Composable
private fun DailyCard(daily: DailySpendUi) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    KefeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Günlük", style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
            Text(daily.peak, style = t.micro.tabular(), color = c.onSurfaceMuted)
        }
        Spacer(Modifier.height(Space.x10))
        Row(
            Modifier.fillMaxWidth().height(DailyChartHeight),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            daily.bars.forEach { bar ->
                val height = if (bar.kind == DayBarKind.Empty) 2.dp else maxOf(DailyChartHeight * bar.ratio, 4.dp)
                val color = when (bar.kind) {
                    DayBarKind.Empty -> c.surfaceSunken
                    DayBarKind.Spent -> c.accent
                    DayBarKind.Today -> c.onSurface
                    DayBarKind.Ahead -> c.accent.copy(alpha = 0.45f)
                }
                Box(Modifier.weight(1f).height(height).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            daily.axis.forEach { Text(it, style = t.micro.tabular(), color = c.onSurfaceMuted) }
        }
    }
}

// --- Liste -----------------------------------------------------------------------

/** "5 EKİM PAZARTESİ  [bugün]  ₺953,57" - kartin USTUNDE kucuk etiket, satirlardan ayri. */
@Composable
private fun DayHeader(group: ExpenseDayGroupUi) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column(Modifier.fillMaxWidth().padding(start = Space.x4, end = Space.x4, top = Space.x16, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.title.trUpper(),
                style = t.micro.copy(fontWeight = FontWeight.SemiBold),
                color = c.onSurfaceMuted,
                maxLines = 1,
            )
            group.tag?.let { tag ->
                Spacer(Modifier.width(Space.x8))
                KefeBadge(text = tag, background = c.accentMuted, contentColor = c.accent, uppercase = false)
            }
            Spacer(Modifier.weight(1f))
            Text(group.total, style = t.micro.tabular(), color = c.onSurfaceMuted)
        }
        group.hint?.let { hint ->
            Text(hint, style = t.micro, color = c.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Bir gunun (ya da tutar sirasinin) satirlari tek kartta, aralarinda ince cizgi. */
@Composable
private fun LinesCard(lines: List<ExpenseLineUi>, onEdit: (String) -> Unit) {
    val c = KefeTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.boxMedium)
            .background(c.surfaceElevated)
            .border(1.dp, c.outline, KefeShapes.boxMedium),
    ) {
        lines.forEachIndexed { index, line ->
            if (index > 0) KefeHairline()
            if (line.lead != null) CompactRow(line, onEdit) else LineRow(line, onEdit)
        }
    }
}

/** Tek kalem, gun gun: "17:32  Dondurma  ₺130" - tek satir. */
@Composable
private fun CompactRow(line: ExpenseLineUi, onEdit: (String) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(onClickLabel = "Düzenle", role = Role.Button) { onEdit(line.id) }
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            line.lead.orEmpty(),
            style = t.micro.tabular(),
            color = if (line.leadEarly) c.accent else c.onSurfaceMuted,
            maxLines = 1,
            modifier = Modifier.width(LeadWidth),
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            line.title,
            style = t.body,
            color = if (line.titleMuted) c.onSurfaceMuted else c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x8))
        Text(line.amount, style = t.body.tabular(), color = c.onSurface)
    }
}

/** Tumu ya da tutar sirasi: harf kutusu, not ve kalem, tutar. */
@Composable
private fun LineRow(line: ExpenseLineUi, onEdit: (String) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "Düzenle", role = Role.Button) { onEdit(line.id) }
            .padding(horizontal = Space.x12, vertical = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.initial?.let { initial ->
            Box(
                Modifier.size(30.dp).clip(KefeShapes.boxSmall).background(c.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, style = t.caption.copy(fontWeight = FontWeight.SemiBold), color = categoryColor(line.colorIndex))
            }
            Spacer(Modifier.width(Space.x10))
        }
        Column(Modifier.weight(1f)) {
            Text(
                line.title,
                style = t.body,
                color = if (line.titleMuted) c.onSurfaceMuted else c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.sub.isNotEmpty()) {
                Text(line.sub, style = t.micro, color = c.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (line.unplanned) {
            Spacer(Modifier.width(Space.x8))
            KefeBadge(text = "plan dışı", background = c.surfaceSunken, contentColor = c.onSurfaceMuted, uppercase = false)
        }
        Spacer(Modifier.width(Space.x8))
        Text(line.amount, style = t.body.tabular(), color = c.onSurface)
    }
}

/** Kalem renkleri ayin harcama sirasiyla; plan disi sonuk. Varlik paletiyle ayni aile. */
@Composable
private fun categoryColor(index: Int): Color {
    val c = KefeTheme.colors
    if (index < 0) return c.onSurfaceMuted
    val palette = listOf(c.gold, c.fx, c.fund, c.stock, c.cash, c.silver)
    return palette[index % palette.size]
}

private val BottomBarSpace = 96.dp
private val SortWidth = 148.dp
private val SplitBarHeight = 10.dp
private val DailyChartHeight = 64.dp
private val LeadWidth = 44.dp
