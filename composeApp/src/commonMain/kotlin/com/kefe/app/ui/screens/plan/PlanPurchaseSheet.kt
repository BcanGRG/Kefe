package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.assetKey
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.parseAssetKey
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeDestructiveTextButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Ayin bir varliktaki alimlari - plan ile varlik arasindaki bagi gosteren sayfa.
 *
 * Plan kalemi alimlara DOGRUDAN bagli degil: ayin ayni varliktaki alimlari ona
 * sayilir. Kullanici 100 € alip kalemi silince alim varliklarda kaldi ve "Plan
 * dışı alımlar"a dustu; bunu beklemiyordu. Iki yerde sorulur (onayli karar):
 * - alimi sayilmis kalem silinirken: "Alımı da sil" ya da "Yalnız planı sil";
 * - plan disi satira dokununca: "Plana ekle" ya da "Alımı sil".
 */
data class PurchaseSheet(
    val month: YearMonth,
    val assetKey: String,
    val name: String,
    /** Kalem silinirken acildi: silinecek kalem. Plan disi satirdan acildiysa null. */
    val itemId: String?,
    /** Ayin bu varliktaki ALIMLARI - "Alımı sil" bunlari siler, satislara dokunmaz. */
    val transactionIds: List<String>,
    val quantity: Double,
    /** Komisyon dahil odenen TL. */
    val tl: Double,
) {
    val deletingItem: Boolean get() = itemId != null

    private val isCash: Boolean get() = parseAssetKey(assetKey)?.assetClass == AssetClass.Cash

    /** "1 alım · 100 € · ₺5.523,98"; nakitte miktar zaten TL oldugu icin bir kez. */
    val summary: String
        get() = buildList {
            add("${transactionIds.size} alım")
            if (!isCash) add(quantityLabel(quantity, assetKey))
            add(Money.tlExact(tl))
        }.joinToString(" · ")

    /** Silinince varliktan dusen: "100 €" ya da nakitte "₺5.000". */
    val removedText: String get() = if (isCash) Money.tlExact(tl) else quantityLabel(quantity, assetKey)
}

/** Ayin [assetKey] alimlari; alim yoksa null - sorulacak bir sey yok. */
internal fun purchaseSheetOf(
    inputs: PlanInputs,
    month: YearMonth,
    assetKey: String,
    itemId: String?,
    name: String,
): PurchaseSheet? {
    // Satilip sifirlanan pozisyon da sayilir (inputs.positions hepsini tasir).
    val keyOf = inputs.positions.associate { it.id to it.assetKey() }
    val buys = inputs.transactions.filter {
        it.side == TradeSide.Buy && it.date in month && keyOf[it.positionId] == assetKey
    }
    if (buys.isEmpty()) return null
    return PurchaseSheet(
        month = month,
        assetKey = assetKey,
        name = name,
        itemId = itemId,
        transactionIds = buys.map { it.id },
        quantity = buys.sumOf { it.quantity },
        tl = buys.sumOf { it.total },
    )
}

@Composable
internal fun PlanPurchaseSheet(
    visible: Boolean,
    sheet: PurchaseSheet?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = sheet?.let { if (it.deletingItem) "Kalemi sil" else it.name }.orEmpty(),
        subtitle = sheet?.let {
            if (it.deletingItem) "${it.name} · ${it.month.label()}" else "Plan dışı alım · ${it.month.label()}"
        },
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        footer = { if (sheet != null) PurchaseFooter(sheet, onIntent) },
    ) {
        if (sheet != null) PurchaseBody(sheet)
    }
}

@Composable
private fun PurchaseBody(sheet: PurchaseSheet) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column(Modifier.fillMaxWidth().padding(vertical = Space.x8)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.surfaceSunken)
                .padding(Space.x12),
        ) {
            Text(
                text = if (sheet.deletingItem) "Bu kaleme sayılan alım" else "Bu aydaki alım",
                style = t.caption,
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(Space.x4))
            Text(sheet.summary, style = t.bodyStrong.tabular(), color = c.onSurface)
        }
        Spacer(Modifier.height(Space.x16))
        if (sheet.deletingItem) {
            OptionLine(
                title = "Yalnız planı sil",
                body = "Alım varlıklarında kalır ve plan dışı alım olarak görünür.",
            )
            Spacer(Modifier.height(Space.x12))
            OptionLine(
                title = "Alımı da sil",
                body = "İşlem varlıklarından da silinir: ${sheet.name} ${sheet.removedText} azalır.",
            )
        } else {
            OptionLine(
                title = "Plana ekle",
                body = "Alınan miktarla bu aya kalem açılır; alım plana sayılır.",
            )
            Spacer(Modifier.height(Space.x12))
            OptionLine(
                title = "Alımı sil",
                body = "İşlem varlıklarından da silinir: ${sheet.name} ${sheet.removedText} azalır.",
            )
        }
    }
}

@Composable
private fun OptionLine(title: String, body: String) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Column {
        Text(title, style = t.caption.copy(fontWeight = FontWeight.SemiBold), color = c.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(body, style = t.caption, color = c.onSurfaceMuted)
    }
}

@Composable
private fun PurchaseFooter(sheet: PurchaseSheet, onIntent: (PlanIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x20, vertical = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        // Geri alinabilen secenek ustte ve belirgin; varliktan silen alttaki kirmizi metin.
        if (sheet.deletingItem) {
            KefeSecondaryButton(
                text = "Yalnız planı sil",
                onClick = { onIntent(PlanIntent.DeleteItemOnly) },
                modifier = Modifier.fillMaxWidth(),
            )
            KefeDestructiveTextButton(
                text = "Alımı da sil",
                onClick = { onIntent(PlanIntent.DeletePurchases) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            KefePrimaryButton(
                text = "Plana ekle",
                onClick = { onIntent(PlanIntent.AddPurchaseToPlan) },
                modifier = Modifier.fillMaxWidth(),
            )
            KefeDestructiveTextButton(
                text = "Alımı sil",
                onClick = { onIntent(PlanIntent.DeletePurchases) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
