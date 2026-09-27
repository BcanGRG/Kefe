package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.kefe.app.domain.model.color
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
 * Plan sekmesi: ayin yatirim plani, para akisi ve serisi - tek kolon.
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = t.bodyStrong,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                row.goalName?.let { goal ->
                    Spacer(Modifier.width(Space.x8))
                    KefeBadge(
                        text = goal,
                        background = c.surfaceSunken,
                        contentColor = c.onSurfaceMuted,
                        leadingIcon = KefeIcons.Target,
                        uppercase = false,
                        iconSize = 10.dp,
                        modifier = Modifier.widthIn(max = GoalBadgeMaxWidth),
                    )
                }
            }
            Spacer(Modifier.height(Space.x4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.progressText,
                    style = t.caption.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
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

/** Uzun bir hedef adi varligin adini satirdan itmesin. */
private val GoalBadgeMaxWidth = 140.dp

/** Bos durum dugmeleri genis ekranda satir boyu uzamasin. */
private val EmptyActionsMaxWidth = 320.dp
