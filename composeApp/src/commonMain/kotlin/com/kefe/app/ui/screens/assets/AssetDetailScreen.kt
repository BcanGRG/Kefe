package com.kefe.app.ui.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.formatLong
import com.kefe.app.domain.model.formatShort
import com.kefe.app.domain.model.label
import com.kefe.app.ui.charts.KefePriceChart
import com.kefe.app.ui.charts.Point
import com.kefe.app.ui.components.KefeAvatar
import com.kefe.app.ui.components.KefeBuyBadge
import com.kefe.app.ui.components.KefeCard
import com.kefe.app.ui.components.KefeConfirmDialog
import com.kefe.app.ui.components.KefeEmptyState
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSecondaryButton
import com.kefe.app.ui.components.KefeSellBadge
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.components.KefeSwipeableRow
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.moneyTl
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Varlik detayi: guncel deger, fiyat grafigi (alim/satim isaretleriyle), ozet
 * satirlari, islem gecmisi ve alt aksiyonlar.
 *
 * Tek kahraman rakam kurali: yalniz "Güncel değer" Display olcegindedir.
 */
@Composable
fun AssetDetailScreen(
    state: AssetDetailUiState,
    onIntent: (AssetDetailIntent) -> Unit,
    onBack: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onAddBuy: () -> Unit,
    onAddSell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = state.position

    // Menu ve onay kutusu icerigin USTUNDE cizilmeli; Column olsaydi dikey
    // akisa katilir, sayfanin altina bir kutu olarak eklenirdi.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            DetailTopBar(
                position = position,
                onBack = onBack,
                onOpenMenu = { onIntent(AssetDetailIntent.OpenMenu) },
            )

            when {
                position == null && state.loading -> DetailSkeleton()

                position == null -> KefeEmptyState(
                    icon = KefeIcons.Wallet,
                    title = "Varlık bulunamadı",
                    body = "Bu varlık silinmiş olabilir. Listeye dönüp yeniden deneyin.",
                )

                else -> {
                    LazyColumn(Modifier.weight(1f)) {
                        item { CurrentValueBlock(position, state.holdingLabel) }

                        if (state.priceSeries.size >= 2) {
                            item { PriceCard(state) }
                        }

                        item { SummaryCard(state, position) }

                        item { HistoryHeader(state.transactions.size) }

                        if (state.transactions.isNotEmpty()) {
                            item {
                                HistoryCard(
                                    state = state,
                                    position = position,
                                    onEditTransaction = onEditTransaction,
                                    onDeleteTransaction = {
                                        onIntent(AssetDetailIntent.DeleteTransaction(it))
                                    },
                                )
                            }
                            item { SwipeHint() }
                        }
                    }

                    DetailBottomActions(onAddBuy = onAddBuy, onAddSell = onAddSell)
                }
            }
        }

        if (state.menuOpen) {
            DetailMenu(
                onDelete = { onIntent(AssetDetailIntent.RequestDelete) },
                onDismiss = { onIntent(AssetDetailIntent.CloseMenu) },
            )
        }

        if (state.confirmDelete) {
            KefeConfirmDialog(
                title = "Varlığı sil",
                // Defter de gider: kullanici yalniz satiri degil, o varliga ait
                // TUM alim satim gecmisini kaybediyor.
                message = "${position?.name ?: "Bu varlık"} ve ona ait " +
                    "${state.transactions.size} işlem silinecek. Bu işlem geri alınamaz.",
                confirmLabel = "Sil",
                onConfirm = { onIntent(AssetDetailIntent.ConfirmDelete) },
                onDismiss = { onIntent(AssetDetailIntent.DismissDeleteConfirm) },
            )
        }
    }
}

/**
 * Ust bardaki "..." menusu.
 *
 * Tek bir gercek eylem tasiyor - varligi silmek. Once hicbir sey yapmiyordu ve
 * varliktan kurtulmanin yolu islemleri tek tek silmekti.
 */
@Composable
private fun DetailMenu(onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(
        Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                // Ust barin altina oturur; bar sabit yukseklikte degil, kendi
                // icerigiyle olculuyor - 56dp o olcunun karsiligi.
                .padding(top = 56.dp, end = Space.x12)
                // Sabit genisik: icerideki satir fillMaxWidth kullandigi icin
                // yalniz alt sinir verilse kutu ekran genisligine yayiliyordu.
                .width(200.dp)
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
                .padding(vertical = Space.x8),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onDelete)
                    .padding(horizontal = Space.x14, vertical = Space.x10),
                horizontalArrangement = Arrangement.spacedBy(Space.x10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KefeIcon(KefeIcons.Trash, null, size = 18.dp, tint = c.negative)
                Text("Varlığı sil", style = t.body, color = c.negative)
            }
        }
    }
}

// --- Ust bar ---------------------------------------------------------------

@Composable
private fun DetailTopBar(position: Position?, onBack: () -> Unit, onOpenMenu: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x8, end = Space.x8, bottom = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        KefeIconButton(
            icon = KefeIcons.ArrowBack,
            contentDescription = "Geri",
            onClick = onBack,
            tint = c.onSurface,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = position?.name.orEmpty(),
                style = t.h2,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (position != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = position.detailSubtitle(),
                    style = t.micro.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        KefeIconButton(
            icon = KefeIcons.MoreHorizontal,
            contentDescription = "Varlık menüsü",
            onClick = onOpenMenu,
        )
    }
}

// --- Guncel deger ----------------------------------------------------------

@Composable
private fun CurrentValueBlock(position: Position, holdingLabel: String?) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val profit = position.profit

    Column(
        Modifier.padding(
            start = Space.x16,
            end = Space.x16,
            top = Space.x4,
            bottom = Space.x16,
        )
    ) {
        Text(
            text = "Güncel değer".trUpper(),
            style = t.label(11, 0.11, FontWeight.SemiBold),
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x4))
        Text(
            text = Money.tl(position.value, spaced = true),
            style = t.display.tabular(),
            color = c.onSurface,
        )
        Spacer(Modifier.height(Space.x8))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Maliyet ${moneyTl(position.cost)}",
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.width(Space.x8))
            Box(
                Modifier
                    .width(Sizes.hairline)
                    .height(Space.x12)
                    .background(c.outline)
            )
            Spacer(Modifier.width(Space.x8))
            // Kazanc/kayip asla yalniz renkle: ok ikonu + isaretli metin birlikte.
            KefeIcon(
                icon = if (profit >= 0) KefeIcons.ArrowUpRight else KefeIcons.ArrowDownRight,
                contentDescription = null,
                size = 13.dp,
                tint = c.delta(profit),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "${Money.tlSigned(profit)} (${Money.delta(position.profitPercent, 1)})",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold).tabular(),
                color = c.delta(profit),
            )
            // Yuzde tek basina yaniltir: 3 ayda %20 ile 3 yilda %20 ayni gorunur.
            // Sureyi hemen yanina koyuyoruz.
            holdingLabel?.let { label ->
                Spacer(Modifier.width(Space.x4))
                Text(
                    text = "· ${label}da",
                    style = t.caption,
                    color = c.onSurfaceMuted,
                )
            }
        }
    }
}

// --- Fiyat grafigi ---------------------------------------------------------

@Composable
private fun PriceCard(state: AssetDetailUiState) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeCard(
        modifier = Modifier.padding(
            start = Space.x16,
            end = Space.x16,
            bottom = Space.x12,
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Birim fiyat", style = t.bodyStrong, color = c.onSurface)
            Spacer(Modifier.weight(1f))
            Text(state.periodLabel, style = t.caption, color = c.onSurfaceMuted)
        }

        Spacer(Modifier.height(Space.x10))
        KefePriceChart(
            points = state.priceSeries.mapIndexed { index, value ->
                Point(index.toFloat(), value.toFloat())
            },
            trades = state.tradeMarkers,
            lineColor = c.gold,
        )
        KefeHairline()

        if (state.axisLabels.size == 3) {
            Spacer(Modifier.height(Space.x4))
            Row(Modifier.fillMaxWidth()) {
                state.axisLabels.forEachIndexed { index, label ->
                    Text(label, style = t.nano, color = c.onSurfaceMuted)
                    if (index < state.axisLabels.lastIndex) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x14)) {
            ChartLegendItem(label = "Alım", filled = true)
            ChartLegendItem(label = "Satış", filled = false)
        }
    }
}

/** Alim/satim isareti: dolu elmas alim, ici bos elmas satim. Renkten bagimsiz ayrim. */
@Composable
private fun ChartLegendItem(label: String, filled: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .rotate(45f)
                .then(
                    if (filled) {
                        Modifier.background(c.accent)
                    } else {
                        Modifier.border(1.4.dp, c.onSurfaceMuted)
                    }
                )
        )
        Text(label, style = t.micro, color = c.onSurfaceMuted)
    }
}

// --- Ozet satirlari --------------------------------------------------------

@Composable
private fun SummaryCard(state: AssetDetailUiState, position: Position) {
    val c = KefeTheme.colors

    Column(
        Modifier
            .padding(start = Space.x16, end = Space.x16, bottom = Space.x12)
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card)
    ) {
        // "Ortalama alış" degil "maliyet": artik ekranda iki ayri "alis" var -
        // kullanicinin odedigi ve piyasanin alis tarafi. Ayni kelime ikisini de
        // anlatirsa hangisinin ne oldugu okunmaz.
        SummaryRow(
            "Ortalama maliyet",
            Money.tl(state.averageBuyPrice, decimals = position.priceDecimals()),
        )
        KefeHairline()
        SummaryRow(
            "Güncel birim fiyat",
            Money.tl(position.unitPrice, decimals = position.priceDecimals()),
        )
        // Makas gorunur olmali: deger piyasanin ALIS tarafiyla olculuyor, yani
        // "bugun satsam ne alirim". Bu satir olmasa bugun alinan varlik ilk anda
        // zararda gorunur ve bu bir hata sanilir.
        state.spreadPercent?.let { percent ->
            KefeHairline()
            SummaryRow(
                "Alış / satış makası",
                Money.tl(state.spreadAmount, decimals = position.priceDecimals()) +
                    " · " + Money.ratio(percent, decimals = 1),
            )
        }
        KefeHairline()
        SummaryRow("İlk alım", state.firstBuyDate?.formatLong() ?: "—")
        // Farkli tarihlerde alinan bir varlikta "asil performans ne" sorusunun
        // dogru olcusu budur: her liranin kac gun calistigini hesaba katar.
        // Hesaplanamiyorsa (tek islem, ayni gun) satir HIC gosterilmez.
        state.annualizedReturnPercent?.let { annual ->
            SummaryRow("Yıllık getiri", Money.delta(annual, 1))
        }
        if (state.realizedProfit != 0.0) {
            SummaryRow("Kesinleşmiş kâr", Money.tlSigned(state.realizedProfit))
        }
        KefeHairline()
        SummaryRow("Fiyat kaynağı", state.priceSourceLabel)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x16, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = t.caption, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.x10))
        Text(value, style = t.caption.tabular(), color = c.onSurface)
    }
}

// --- Islem gecmisi ---------------------------------------------------------

@Composable
private fun HistoryHeader(count: Int) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x16, end = Space.x16, top = Space.x20, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("İşlem geçmişi", style = t.bodyStrong, color = c.onSurface)
        Spacer(Modifier.weight(1f))
        Text("$count işlem", style = t.caption.tabular(), color = c.onSurfaceMuted)
    }
}

@Composable
private fun HistoryCard(
    state: AssetDetailUiState,
    position: Position,
    onEditTransaction: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit,
) {
    val c = KefeTheme.colors

    Column(
        Modifier
            .padding(start = Space.x16, end = Space.x16, bottom = Space.x12)
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card)
    ) {
        state.transactions.forEachIndexed { index, transaction ->
            if (index > 0) KefeHairline()
            KefeSwipeableRow(
                onEdit = { onEditTransaction(transaction.id) },
                onDelete = { onDeleteTransaction(transaction.id) },
                editIcon = KefeIcons.Pencil,
                deleteIcon = KefeIcons.Trash,
                onRightClick = { onEditTransaction(transaction.id) },
            ) {
                TransactionRow(transaction = transaction, position = position, state = state)
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    position: Position,
    state: AssetDetailUiState,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Kaydirilabilir satirin kendi zemini kart yuzeyiyle ayni tonda kalmali.
            .background(c.surfaceElevated)
            .padding(horizontal = Space.x14, vertical = Space.x12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                if (transaction.side == TradeSide.Buy) KefeBuyBadge() else KefeSellBadge()
                Text(
                    text = transaction.lineLabel(position),
                    style = t.caption.tabular(),
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = transaction.dateLabel(),
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x10))
        Text(
            // Tasarimda satir tutari isciligi icermez: iscilik alt satirda ayrica yazilir.
            text = moneyTl(transaction.quantity * transaction.unitPrice),
            style = t.body.tabular(),
            color = c.onSurface,
        )
        Spacer(Modifier.width(Space.x10))
        KefeAvatar(
            initials = state.memberInitials(transaction.addedByMemberId),
            index = state.memberIndexOf(transaction.addedByMemberId),
            size = 26.dp,
        )
    }
}

@Composable
private fun SwipeHint() {
    Text(
        text = "Satırı sola kaydırın: düzenle veya sil. Fareyle sağ tık da aynı menüyü açar.",
        style = KefeTheme.type.micro,
        color = KefeTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(start = Space.x16, end = Space.x16, bottom = Space.x24),
    )
}

// --- Alt aksiyonlar --------------------------------------------------------

@Composable
private fun DetailBottomActions(onAddBuy: () -> Unit, onAddSell: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(KefeTheme.colors.surface)) {
        KefeHairline()
        Row(
            modifier = Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x12,
                bottom = Space.x20,
            ),
            horizontalArrangement = Arrangement.spacedBy(Space.x10),
        ) {
            KefePrimaryButton("Alım Ekle", onClick = onAddBuy, modifier = Modifier.weight(1f))
            KefeSecondaryButton("Satış Ekle", onClick = onAddSell, modifier = Modifier.weight(1f))
        }
    }
}

// --- Iskelet ---------------------------------------------------------------

@Composable
private fun DetailSkeleton() {
    Column(
        Modifier.padding(horizontal = Space.x16, vertical = Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x12),
    ) {
        KefeSkeletonBlock(width = 104.dp, height = 10.dp)
        KefeSkeletonBlock(width = 232.dp, height = 40.dp)
        KefeSkeletonBlock(width = 184.dp, height = 14.dp)
        KefeSkeletonBlock(height = 212.dp, radius = 16.dp)
        KefeSkeletonBlock(height = 196.dp, radius = 16.dp)
    }
}

// --- Yardimci --------------------------------------------------------------

/** Baslik alt satiri: "8 adet · Altın". */
private fun Position.detailSubtitle(): String {
    val quantityText = when (unit) {
        QuantityUnit.Piece -> Money.quantity(quantity, unit.label())
        QuantityUnit.Gram -> Money.quantity(quantity, unit.label(), detailQuantityDecimals())
        QuantityUnit.Share -> Money.quantity(quantity, unit.label())
        QuantityUnit.Currency ->
            if (assetClass == AssetClass.Cash) Money.tl(quantity) else Money.number(quantity)
    }
    return "$quantityText · ${assetClass.label()}"
}

private fun Position.detailQuantityDecimals(): Int =
    if (quantity == quantity.toLong().toDouble()) 0 else 1

/** Kucuk birim fiyatlarda (fon payi) kurus gosterilir, buyuklerde gosterilmez. */
private fun Position.priceDecimals(): Int = if (unitPrice < 100.0) 2 else 0

/** "2 adet × ₺26.400" */
private fun Transaction.lineLabel(position: Position): String {
    val unitLabel = position.unit.label()
    val quantityText = if (unitLabel.isBlank()) {
        Money.number(quantity, if (quantity == quantity.toLong().toDouble()) 0 else 2)
    } else {
        Money.quantity(quantity, unitLabel, if (quantity == quantity.toLong().toDouble()) 0 else 1)
    }
    return "$quantityText × ${Money.tl(unitPrice, decimals = position.priceDecimals())}"
}

/** "12 Tem 2026" · gerekirse "işçilik ₺600" eklenir. */
private fun Transaction.dateLabel(): String =
    if (fee > 0.0) "${date.formatShort()} · işçilik ${Money.tl(fee)}" else date.formatShort()
