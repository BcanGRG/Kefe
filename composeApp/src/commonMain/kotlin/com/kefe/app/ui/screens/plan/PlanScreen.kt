package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Plan sekmesi: ayin yatirim plani, para akisi ve serisi - tek kolon.
 *
 * Ekran sheet CIZMEZ: plan sheet'leri kabukta, her ekranin ustunde tek yerde
 * cizilir (bkz. App.kt). Masaustunde genislik ContentWidth ile 880dp'ye sinirli.
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PlanHeaderBar(
            header = state.content.header,
            // Yuklenirken sinirlar henuz veriden gelmedi; gecis dugmeleri kapali.
            enabled = state.stage == PlanStage.Ready,
            onIntent = onIntent,
        )
        if (state.stage == PlanStage.Loading) PlanSkeleton()
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
