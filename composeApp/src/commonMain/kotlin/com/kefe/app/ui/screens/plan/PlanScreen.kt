package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.StreakCell
import com.kefe.app.domain.model.color
import com.kefe.app.ui.charts.KefeStreakGrid
import com.kefe.app.ui.charts.KefeStreakLegend
import com.kefe.app.ui.charts.StreakGridCell
import com.kefe.app.ui.charts.StreakMark
import com.kefe.app.ui.components.KefeAvatar
import com.kefe.app.ui.components.KefeBadge
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeDashedCard
import com.kefe.app.ui.components.KefeEmptyState
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefeListRow
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeProgressBar
import com.kefe.app.ui.components.KefeProgressBarThin
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeTextButton
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.icons.icon
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Plan sekmesi: ayin yatirim plani, para akisi, giderleri ve serisi - tek kolon.
 *
 * Ekran sheet CIZMEZ: plan sheet'leri kabukta, her ekranin ustunde tek yerde
 * cizilir (bkz. App.kt, PlanSheets). Masaustunde genislik ContentWidth ile
 * 880dp'ye sinirli. Bos kart cizilmez; bilinmeyen deger "—".
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onIntent: (PlanIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PlanHeaderBar(
            header = state.content.header,
            // Yuklenirken sinirlar henuz veriden gelmedi; gecis dugmeleri kapali.
            enabled = state.stage == PlanStage.Ready,
            onIntent = onIntent,
        )
        when (state.stage) {
            PlanStage.Loading -> PlanSkeleton()
            PlanStage.Ready -> PlanBody(state.content, onIntent, onOpenGoal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlanBody(
    content: PlanContent,
    onIntent: (PlanIntent) -> Unit,
    onOpenGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Space.x16, end = Space.x16, bottom = Space.x24),
        verticalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        content.investment?.let { InvestmentPlanCard(it, onIntent) }
        content.emptyPlan?.let { EmptyPlanCardView(it, onIntent) }
        content.extras?.let { ExtrasCardView(it) }
        if (content.goalContributions.isNotEmpty()) {
            GoalContributionCard(content.goalContributions, onOpenGoal)
        }
        content.flow?.let { MoneyFlowCardView(it, onIntent) }
        content.expenses?.let { ExpensesCardView(it, onIntent) }
        content.streak?.let { StreakCardView(it) }
    }
}

// --- Baslik ----------------------------------------------------------------

@Composable
private fun PlanHeaderBar(
    header: PlanHeader,
    enabled: Boolean,
    onIntent: (PlanIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x16, end = Space.x16, top = 2.dp, bottom = Space.x12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = t.h1, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            // Baska bir aydayken tek dokunusla bu aya donus.
            if (header.showThisMonthChip) {
                KefeChip(
                    text = "Bu ay",
                    selected = false,
                    onClick = { onIntent(PlanIntent.ThisMonth) },
                    height = Sizes.chipSmall,
                    enabled = enabled,
                )
            }
        }

        Spacer(Modifier.height(Space.x4))

        // Ok butonlari 44dp dokunma hedefini korur; soldaki okun IKONU basliktaki
        // "Plan" ile ayni hizaya gelsin diye satir ikon boslugu kadar sola kayar.
        Row(
            modifier = Modifier.offset(x = -SwitcherInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeIconButton(
                icon = KefeIcons.ChevronLeft,
                contentDescription = "Önceki ay",
                onClick = { onIntent(PlanIntent.PreviousMonth) },
                enabled = enabled && header.canGoBack,
                tint = c.onSurface,
            )
            Text(
                text = header.title,
                style = t.h2.tabular(),
                color = c.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Ay degisince ekran okuyucu yeni ayi okusun: "Önceki ay"a basan
                // kullanici nereye geldigini baska turlu duymaz.
                modifier = Modifier
                    .widthIn(min = MonthTitleMinWidth)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            KefeIconButton(
                icon = KefeIcons.ChevronRight,
                contentDescription = "Sonraki ay",
                onClick = { onIntent(PlanIntent.NextMonth) },
                enabled = enabled && header.canGoForward,
                tint = c.onSurface,
            )
        }

        Text(header.subtitle, style = t.caption, color = c.onSurfaceMuted)
    }
}

// --- Yatirim plani -----------------------------------------------------------

@Composable
private fun InvestmentPlanCard(card: InvestmentCard, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yatırım planı", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            card.streakText?.let { streak ->
                KefeBadge(
                    text = streak,
                    background = c.accentMuted,
                    contentColor = c.accent,
                    uppercase = false,
                )
                Spacer(Modifier.width(Space.x8))
            }
            Text(card.scoreText, style = t.h2.tabular(), color = c.onSurface)
        }
        card.score?.let { score ->
            Spacer(Modifier.height(Space.x8))
            KefeProgressBar(progress = score)
        }
        Spacer(Modifier.height(Space.x8))
        Text(card.summary, style = t.caption.tabular(), color = c.onSurfaceMuted)

        Spacer(Modifier.height(Space.x8))
        card.rows.forEachIndexed { index, row ->
            if (index > 0) KefeHairline()
            PlanRow(row, onIntent)
        }

        Spacer(Modifier.height(Space.x12))
        AddDashedCard(
            icon = KefeIcons.Plus,
            text = "Kalem ekle",
            onClick = { onIntent(PlanIntent.AddItem) },
        )
        // Yalniz kaynakta bu ayda olmayan bir varlik varken (bkz. planContent).
        card.copyLabel?.let { label ->
            Spacer(Modifier.height(Space.x8))
            AddDashedCard(
                icon = KefeIcons.Copy,
                text = label,
                onClick = { onIntent(PlanIntent.OpenCopy) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanRow(row: PlanRowUi, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val tint = row.assetClass?.let { c.assetClass(it.color()) } ?: c.onSurfaceMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(onClickLabel = "Düzenle", role = Role.Button) { onIntent(PlanIntent.EditItem(row.itemId)) }
            .padding(vertical = Space.x12),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(AssetBoxSize)
                .clip(KefeShapes.boxSmall)
                .background(c.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            KefeIcon(
                icon = row.assetClass?.icon() ?: KefeIcons.Wallet,
                contentDescription = null,
                size = IconSize.small,
                tint = tint,
            )
        }
        Spacer(Modifier.width(Space.x12))

        Column(Modifier.weight(1f)) {
            // Akan satir: hedef cipi sigmazsa alt satira iner. NEDEN: ayni satirda
            // once olculen cip, "Al" gorunen dar telefonda varligin adini tek harfe
            // indiriyordu; varligin adi once gelir, cip kalan yere ya da alta.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                Text(
                    text = row.name,
                    style = t.bodyStrong,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                row.goalName?.let { goal ->
                    KefeBadge(
                        text = goal,
                        background = c.surfaceSunken,
                        contentColor = c.onSurfaceMuted,
                        leadingIcon = KefeIcons.Target,
                        uppercase = false,
                        iconSize = 10.dp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            Spacer(Modifier.height(Space.x4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tek satira zorlanmaz: dar ekranda "₺12.500 /" diye kesilip hedef
                // tutari sessizce kayboluyordu; sarilan metin iki sayiyi da gosterir.
                Text(
                    text = row.progressText,
                    style = t.caption.tabular(),
                    color = c.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x8))
                StatusBadge(row.status)
            }
            Spacer(Modifier.height(Space.x8))
            KefeProgressBarThin(
                progress = row.ratio,
                color = if (row.status == PlanItemStatus.Done) c.positive else c.accent,
            )
            row.notes.forEach { note ->
                Spacer(Modifier.height(Space.x4))
                Text(note, style = t.micro.tabular(), color = c.onSurfaceMuted)
            }
        }

        if (row.canBuy) {
            Spacer(Modifier.width(Space.x4))
            // Her satirda ayni "Al" var: ekran okuyucu hangi varligin alinacagini
            // ancak adla soyler ("Gram Altın al").
            KefeTextButton(
                text = "Al",
                onClick = { onIntent(PlanIntent.Buy(row.itemId)) },
                leadingIcon = KefeIcons.Cart,
                modifier = Modifier.semantics { contentDescription = "${row.name} al" },
            )
        }
    }
}

/**
 * Kalemin durum rozeti: kelime + ikon, renk ikincil sinyal. Bekliyor ile Yakinda
 * ayni sonuk zemini paylasir; Yakinda kesikli kenar ve takvimle ayrilir.
 */
@Composable
private fun StatusBadge(status: PlanItemStatus) {
    val c = KefeTheme.colors
    val (icon, background, content) = when (status) {
        PlanItemStatus.Done -> Triple(KefeIcons.Check, c.buyBadgeBg, c.positive)
        PlanItemStatus.Partial -> Triple(KefeIcons.HalfCircle, c.staleBannerBg, c.warning)
        PlanItemStatus.Waiting -> Triple(KefeIcons.Clock, c.surfaceSunken, c.onSurfaceMuted)
        PlanItemStatus.Missed -> Triple(KefeIcons.Close, c.sellBadgeBg, c.negative)
        PlanItemStatus.Upcoming -> Triple(KefeIcons.Calendar, c.surfaceSunken, c.onSurfaceMuted)
    }
    KefeBadge(
        text = status.label(),
        background = background,
        contentColor = content,
        dashed = status == PlanItemStatus.Upcoming,
        leadingIcon = icon,
        uppercase = false,
    )
}

/** Kesikli "ekle" karti: ikon + metin, kartin tamami dokunulur. */
@Composable
private fun AddDashedCard(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    KefeDashedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget),
        contentPadding = PaddingValues(horizontal = Space.x16, vertical = Space.x12),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KefeIcon(icon, contentDescription = null, size = IconSize.small, tint = c.accent)
            Spacer(Modifier.width(Space.x8))
            Text(text, style = KefeTheme.type.bodyStrong, color = c.accent)
        }
    }
}

// --- Bos plan ------------------------------------------------------------------

@Composable
private fun EmptyPlanCardView(card: EmptyPlanCard, onIntent: (PlanIntent) -> Unit) {
    KefeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        KefeEmptyState(
            icon = KefeIcons.Calendar,
            title = card.title,
            body = card.body,
            action = {
                // Onayli sira: [Geçen ayı kopyala (Eylül)] [Kalem ekle]. Kaynak ay
                // yoksa tek birincil eylem "Kalem ekle".
                Column(
                    modifier = Modifier.widthIn(max = EmptyActionsMaxWidth),
                    verticalArrangement = Arrangement.spacedBy(Space.x8),
                ) {
                    val copyLabel = card.copyLabel
                    if (copyLabel != null) {
                        KefePrimaryButton(
                            text = copyLabel,
                            onClick = { onIntent(PlanIntent.OpenCopy) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = KefeIcons.Copy,
                        )
                        KefeSecondaryButton(
                            text = "Kalem ekle",
                            onClick = { onIntent(PlanIntent.AddItem) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = KefeIcons.Plus,
                        )
                    } else {
                        KefePrimaryButton(
                            text = "Kalem ekle",
                            onClick = { onIntent(PlanIntent.AddItem) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = KefeIcons.Plus,
                        )
                    }
                }
            },
        )
    }
}

// --- Plan disi alimlar -----------------------------------------------------------

@Composable
private fun ExtrasCardView(card: ExtrasCard) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Yatay dolgu 4dp: KefeListRow kendi 12dp dolgusunu tasir, satirlar ve baslik
    // boylece diger kartlarin 16dp icerik hizasinda durur.
    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Space.x4, end = Space.x4, top = Space.x16, bottom = Space.x8),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Plan dışı alımlar", style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
            Text(card.total, style = t.bodyStrong.tabular(), color = c.onSurface)
        }
        Spacer(Modifier.height(Space.x4))
        card.rows.forEach { row ->
            KefeListRow(
                title = row.name,
                subtitle = row.detail,
                value = row.amount,
                leadingIcon = row.assetClass?.icon() ?: KefeIcons.Wallet,
                leadingTint = row.assetClass?.let { c.assetClass(it.color()) } ?: c.onSurfaceMuted,
            )
        }
    }
}

// --- Hedeflere katki -------------------------------------------------------------

@Composable
private fun GoalContributionCard(rows: List<GoalContributionRow>, onOpenGoal: (String) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(Modifier.fillMaxWidth()) {
        Text("Hedeflere katkı", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x4))
        rows.forEachIndexed { index, row ->
            if (index > 0) KefeHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .clickable(onClickLabel = "Hedefi aç", role = Role.Button) { onOpenGoal(row.goalId) }
                    .padding(vertical = Space.x12),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(AssetBoxSize)
                        .clip(KefeShapes.boxSmall)
                        .background(c.surfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    KefeIcon(KefeIcons.goalIcon(row.iconKey), null, size = IconSize.small, tint = c.accent)
                }
                Spacer(Modifier.width(Space.x12))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = t.bodyStrong,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(row.line, style = t.caption.tabular(), color = c.onSurfaceMuted)
                    row.ratio?.let { ratio ->
                        Spacer(Modifier.height(Space.x8))
                        KefeProgressBarThin(progress = ratio)
                    }
                }
                Spacer(Modifier.width(Space.x8))
                KefeIcon(KefeIcons.ChevronRight, null, size = IconSize.small, tint = c.onSurfaceMuted)
            }
        }
    }
}

// --- Para akisi ---------------------------------------------------------------

/**
 * "Para akışı": plan ve gerceklesen iki sutunda, altta uye uye gelir. Gelir
 * satirlari hep cizilir - gelirin giris kapisi burasi.
 */
@Composable
private fun MoneyFlowCardView(card: MoneyFlowCard, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(Modifier.fillMaxWidth()) {
        Text("Para akışı", style = t.bodyStrong, color = c.onSurface)

        card.table?.let { rows ->
            Spacer(Modifier.height(Space.x12))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                FlowHeaderCell("Plan")
                FlowHeaderCell("Gerçekleşen")
            }
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Satir tek cumle okunur ("Gelir: plan ₺85.000, gerçekleşen yok");
                        // uc ayri hucre birbirinden kopuk okunurdu.
                        .semantics(mergeDescendants = true) { contentDescription = row.spoken }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.label, style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
                    FlowValueCell(row.planned)
                    FlowValueCell(row.actual)
                }
            }
        }

        val lines = listOfNotNull(card.savingsLine, card.planShareLine, card.salesLine)
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(Space.x8))
            lines.forEach { line -> Text(line, style = t.caption.tabular(), color = c.onSurfaceMuted) }
        }

        if (card.incomeRows.isNotEmpty()) {
            Spacer(Modifier.height(Space.x12))
            KefeHairline()
            Spacer(Modifier.height(Space.x12))
            Text("Gelir", style = t.micro, color = c.onSurfaceMuted)
            card.incomeRows.forEach { row -> IncomeRow(row, onIntent) }
        }
    }
}

@Composable
private fun FlowHeaderCell(text: String) {
    Text(
        text = text,
        style = KefeTheme.type.micro,
        color = KefeTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(FlowColumnWidth),
    )
}

@Composable
private fun FlowValueCell(text: String) {
    Text(
        text = text,
        style = KefeTheme.type.body.tabular(),
        color = KefeTheme.colors.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(FlowColumnWidth),
    )
}

@Composable
private fun IncomeRow(row: IncomeRowUi, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(onClickLabel = "Düzenle", role = Role.Button) { onIntent(PlanIntent.EditIncome(row.memberId)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeAvatar(initials = row.initials, index = row.index, size = Sizes.avatarSmall)
        Spacer(Modifier.width(Space.x12))
        Text(
            text = row.name,
            style = t.body,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x8))
        Text(row.amount, style = t.body.tabular(), color = c.onSurface)
        Spacer(Modifier.width(Space.x8))
        KefeIcon(KefeIcons.ChevronRight, null, size = IconSize.small, tint = c.onSurfaceMuted)
    }
}

// --- Giderler ------------------------------------------------------------------

/**
 * "Giderler": toplam (butceye karsi), kategoriler ve son girisler. Bos ayda da
 * cizilir - harcama ve butcenin giris kapisi. Asim METINLE soylenir ("₺2.300
 * aşıldı"); kirmizi yalniz eslik eder.
 */
@Composable
private fun ExpensesCardView(card: ExpensesCard, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Yatay dolgu 4dp: "Son girişler" KefeListRow'un kendi 12dp dolgusunu tasir; geri
    // kalan icerik 12dp ile diger kartlarin 16dp hizasinda durur (Plan dışı alımlar gibi).
    KefeCard(
        modifier = Modifier.fillMaxWidth(),
        // Ust dolgu 4dp: basliktaki "Bütçe" dugmesi 44dp; "Giderler" boylece diger
        // kartlarin basligiyla ayni yukseklikte durur.
        contentPadding = PaddingValues(start = Space.x4, end = Space.x4, top = Space.x4, bottom = Space.x8),
    ) {
        Column(Modifier.padding(horizontal = Space.x12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Giderler", style = t.bodyStrong, color = c.onSurface, modifier = Modifier.weight(1f))
                KefeTextButton(text = "Bütçe", onClick = { onIntent(PlanIntent.EditBudget) })
            }
            Text(card.totalLine, style = t.h2.tabular(), color = c.onSurface)
            val over = card.totalOverText != null
            card.totalRatio?.let { ratio ->
                Spacer(Modifier.height(Space.x8))
                KefeProgressBar(progress = ratio, color = if (over) c.negative else c.accent)
            }
            card.totalOverText?.let { text ->
                Spacer(Modifier.height(Space.x4))
                Text(text, style = t.caption.tabular(), color = c.negative)
            }

            card.categories.forEach { row ->
                Spacer(Modifier.height(Space.x12))
                CategoryRow(row)
            }

            Spacer(Modifier.height(Space.x8))
            KefeTextButton(
                text = "Harcama ekle",
                onClick = { onIntent(PlanIntent.AddExpense) },
                leadingIcon = KefeIcons.Plus,
            )
        }

        if (card.recent.isNotEmpty()) {
            Spacer(Modifier.height(Space.x4))
            Text(
                "Son girişler",
                style = t.micro,
                color = c.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = Space.x12),
            )
            card.recent.forEach { row ->
                KefeListRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    value = row.amount,
                    leadingIcon = KefeIcons.Receipt,
                    onClick = { onIntent(PlanIntent.EditExpense(row.id)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(row: CategoryRowUi) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val over = row.overText != null

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = t.body,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.x8))
            Text(row.amounts, style = t.caption.tabular(), color = c.onSurfaceMuted)
        }
        row.ratio?.let { ratio ->
            Spacer(Modifier.height(6.dp))
            KefeProgressBarThin(progress = ratio, color = if (over) c.negative else c.accent)
        }
        row.overText?.let { text ->
            Spacer(Modifier.height(Space.x4))
            Text(text, style = t.micro.tabular(), color = c.negative)
        }
    }
}

// --- Seri ------------------------------------------------------------------------

/**
 * "Seri": kac ay ust uste duzenli, son 12 ayin izgarasi ve %80 kurali. Kural
 * yazili durur: "düzenli"nin ne demek oldugu baska hicbir yerde soylenmez.
 */
@Composable
private fun StreakCardView(card: StreakCard) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val cells = remember(card.cells) { card.cells.map { it.toGridCell() } }

    KefeCard(Modifier.fillMaxWidth()) {
        // Diger kartlar gibi basligi var: seri 0 cumlesi tek basina kartin ne
        // oldugunu soylemiyordu.
        Text("Seri", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.height(Space.x4))
        Text(card.headline, style = t.body, color = c.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(card.detail, style = t.caption.tabular(), color = c.onSurfaceMuted)
        Spacer(Modifier.height(Space.x12))
        KefeStreakGrid(cells = cells)
        Spacer(Modifier.height(Space.x12))
        KefeStreakLegend()
        Spacer(Modifier.height(Space.x8))
        Text(card.rule, style = t.micro, color = c.onSurfaceMuted)
    }
}

/** Grafik alan tipini bilmez (charts `domain` import etmez); esleme 1:1 burada. */
private fun StreakCellUi.toGridCell(): StreakGridCell = StreakGridCell(
    label = label,
    mark = when (cell) {
        StreakCell.Full -> StreakMark.Full
        StreakCell.Partial -> StreakMark.Partial
        StreakCell.Missed -> StreakMark.Missed
        StreakCell.NoPlan -> StreakMark.NoPlan
        StreakCell.BeforeStart -> StreakMark.BeforeStart
        StreakCell.InProgress -> StreakMark.InProgress
    },
    description = description,
    current = isCurrent,
)

// --- Yuklenme --------------------------------------------------------------

@Composable
private fun PlanSkeleton() {
    Column(
        Modifier.padding(horizontal = Space.x16),
        verticalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        repeat(2) { KefeSkeletonBlock(height = 180.dp, radius = 16.dp) }
    }
}

// --- Olculer ---------------------------------------------------------------

/** 44dp dokunma hedefi ile 24dp ikon arasindaki bosluk - okun gorunur kenari. */
private val SwitcherInset = (Sizes.touchTarget - IconSize.default) / 2

/** "Ağustos 2026" sigsin, aylar arasinda oklar yer degistirmesin. */
private val MonthTitleMinWidth = 132.dp

/** Satirin varlik/hedef ikon kutusu. */
private val AssetBoxSize = 36.dp

/** Para akisi tablosunun Plan ve Gerçekleşen sutunlari - "₺185.000" sigar. */
private val FlowColumnWidth = 104.dp

/** Bos durum dugmeleri genis ekranda satir boyu uzamasin. */
private val EmptyActionsMaxWidth = 320.dp
