package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.CopyRow
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.monthName
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSegmentedControl
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * "Kopyala/Devir" sayfasi: kaynak ayin satirlari; eksik kalanlarda Taşı/Bırak.
 *
 * DEVIR SORULUR: varsayilan "Bırak" (onayli karar). Otomatik devirde iki ay ust
 * uste eksik kalan satir ucuncu ayda uc katina cikardi.
 */
@Composable
internal fun PlanCopySheet(
    visible: Boolean,
    draft: CopyDraftUi?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = draft?.let { "${it.source.monthName()} planını kopyala" }.orEmpty(),
        subtitle = draft?.let(::copySubtitle),
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        footer = { if (draft != null) CopyFooter(draft, onIntent) },
    ) {
        if (draft != null) {
            draft.rows.forEachIndexed { index, row ->
                if (index > 0) KefeHairline()
                CopyRowView(row, carried = row.assetKey in draft.carry, onIntent = onIntent)
            }
        }
    }
}

/**
 * "Ekim 2026 planına" + kaynak ay bitmediyse "(Ekim henüz bitmedi)": eksik miktar
 * o zaman kesin degil, ay icinde alim yapilabilir.
 */
internal fun copySubtitle(draft: CopyDraftUi): String {
    val base = "${draft.target.label()} planına"
    return if (draft.rows.any { it.provisional }) "$base (${draft.source.monthName()} henüz bitmedi)" else base
}

@Composable
private fun CopyRowView(row: CopyRow, carried: Boolean, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val carrying = carried && row.shortfall > 0.0
    val base = planTargetText(row.mode, row.baseTarget, row.assetKey)

    Column(Modifier.fillMaxWidth().padding(vertical = Space.x12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = t.bodyStrong,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.alreadyInTarget) {
                Spacer(Modifier.width(Space.x8))
                Text("Zaten planlı", style = t.caption, color = c.onSurfaceMuted)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            // Taşı secilince yeni hedef okla yazilir: "10 gr → 14 gr".
            text = if (carrying) {
                "$base → ${planTargetText(row.mode, row.baseTarget + row.shortfall, row.assetKey)}"
            } else {
                base
            },
            style = t.caption.tabular(),
            color = c.onSurfaceMuted,
        )
        if (row.shortfall > 0.0) {
            Spacer(Modifier.height(Space.x4))
            Text(
                "${planTargetText(row.mode, row.shortfall, row.assetKey)} eksik kaldı",
                style = t.caption.tabular(),
                color = c.onSurface,
            )
            Spacer(Modifier.height(Space.x8))
            KefeSegmentedControl(
                options = listOf("Taşı", "Bırak"),
                selectedIndex = if (carrying) 0 else 1,
                onSelect = { index -> onIntent(PlanIntent.CopyCarry(row.assetKey, carry = index == 0)) },
            )
        }
    }
}

@Composable
private fun CopyFooter(draft: CopyDraftUi, onIntent: (PlanIntent) -> Unit) {
    // Sayfa yalniz yeni satir varken acilir; bu etiket acikken veri degisirse diye.
    val writable = draft.writableCount() > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x20, vertical = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        KefePrimaryButton(
            text = if (writable) "Kopyala" else "Kopyalanacak kalem yok",
            onClick = { onIntent(PlanIntent.ConfirmCopy) },
            modifier = Modifier.fillMaxWidth(),
            enabled = writable,
        )
    }
}
