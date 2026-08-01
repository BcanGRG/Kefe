package com.kefe.app.ui.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.label
import com.kefe.app.ui.components.KefeEmptyState
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeListRow
import com.kefe.app.ui.components.KefeManualBadge
import com.kefe.app.ui.components.KefePeriodChips
import com.kefe.app.ui.components.KefeSectionHeader
import com.kefe.app.ui.components.KefeSkeletonBlock
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.quantityLabel
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Varliklar listesi. Baslik + toplam, siralama cipleri ve varlik sinifina gore
 * katlanabilir gruplar. Nakit grubu kapali baslar.
 *
 * Gorsel yukselti golgeyle degil yuzey tonu ve kenarlikla anlatilir; gruplar
 * tek bir yuzey kabi icinde ince ayiricilarla bolunur.
 */
@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onIntent: (AssetsIntent) -> Unit,
    onOpenPosition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AssetsHeader(state, onIntent)

        when {
            state.loading -> AssetsSkeleton()
            state.groups.isEmpty() -> KefeEmptyState(
                icon = KefeIcons.Wallet,
                title = "Henüz varlık yok",
                body = "Altın, gümüş, döviz, fon ve nakit birikiminizi ekleyin; " +
                    "Kefe güncel fiyatlarla TL karşılığını hesaplasın.",
            )

            else -> AssetsList(state, onIntent, onOpenPosition)
        }
    }
}

// --- Baslik ----------------------------------------------------------------

@Composable
private fun AssetsHeader(state: AssetsUiState, onIntent: (AssetsIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier.padding(
            start = Space.x16,
            end = Space.x16,
            top = 2.dp,
            bottom = Space.x10,
        )
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Varlıklar",
                style = t.h1,
                color = c.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(Space.x8))
            Text(
                text = Money.tl(state.totalValue),
                style = t.bodyStrong.tabular(),
                color = c.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
        }

        // ELDE VARLIK YOKKEN SECICILER CIZILMEZ.
        //
        // Siralanacak bir liste, olculecek bir degisim yok; ipuclarini bos bir
        // ekranin ustune koymak "bir sey eksik" hissi veriyordu. Ozet'in bos
        // durumunda senkron cipi ve yenile de ayni gerekceyle gizleniyor
        // (adim 9b). Ilk varlikla birlikte gelirler.
        if (state.groups.isEmpty()) return@Column

        Spacer(Modifier.height(Space.x12))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            Text(
                text = "Sırala".trUpper(),
                style = t.label(11, 0.06, FontWeight.SemiBold),
                color = c.onSurfaceMuted,
            )
            AssetSort.entries.forEach { sort ->
                SortChip(
                    text = sort.label,
                    selected = state.sort == sort,
                    onClick = { onIntent(AssetsIntent.SelectSort(sort)) },
                )
            }
        }

        Spacer(Modifier.height(Space.x8))

        // Degisim penceresi. TEK rakam cifti gosterilir ve cip hangi soruyu
        // sordugunu soyler: TL de yuzde de birlikte degisir. Once TL kar (hep
        // toplam) ile donem yuzdesi ust uste duruyordu; ayni satirda iki farkli
        // soruya cevap vermek karisikti.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            Text(
                text = "Değişim".trUpper(),
                style = t.label(11, 0.06, FontWeight.SemiBold),
                color = c.onSurfaceMuted,
            )
            KefePeriodChips(
                selectedIndex = AssetChange.entries.indexOf(state.change),
                onSelect = { onIntent(AssetsIntent.SelectChange(AssetChange.entries[it])) },
                options = AssetChange.entries.map { it.label },
                lastWeight = 1f,
            )
        }
    }
}

/**
 * Siralama cipi: 32px, 12/600 metin. Secili hal renk disinda kenarlikla da
 * ayirt edilir - renk tek sinyal degildir.
 */
@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(
        modifier = Modifier
            .height(Sizes.chipSmall)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accentMuted else Color.Transparent)
            .border(
                width = Sizes.hairline,
                color = if (selected) c.accent else c.outline,
                shape = KefeShapes.pill,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.x12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = t.captionSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) c.onSurface else c.onSurfaceMuted,
        )
    }
}

// --- Liste -----------------------------------------------------------------

@Composable
private fun AssetsList(
    state: AssetsUiState,
    onIntent: (AssetsIntent) -> Unit,
    onOpenPosition: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.x16,
            end = Space.x16,
            bottom = Space.x24,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.x10),
    ) {
        items(state.groups, key = { it.assetClass.name }) { group ->
            AssetGroupCard(
                group = group,
                mode = state.change,
                expanded = group.assetClass !in state.collapsed,
                onToggle = { onIntent(AssetsIntent.ToggleGroup(group.assetClass)) },
                onOpenPosition = onOpenPosition,
            )
        }
    }
}

@Composable
private fun AssetGroupCard(
    group: AssetGroup,
    mode: AssetChange,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenPosition: (String) -> Unit,
) {
    val c = KefeTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.card)
    ) {
        KefeSectionHeader(
            dotColor = c.assetClass(group.assetClass.color()),
            title = group.assetClass.label(),
            total = Money.tlExact(group.total),
            // Pay yuzdesi yerine TL. Pay zaten Ozet'teki "Ne kadari nerede"
            // halkasinda duruyordu; "ne kadar kazandik" ise hicbir listede
            // yoktu. Maliyeti olmayan grupta (nakit) oran anlamsiz - yalniz
            // tutar yazilir. Kurus HER ZAMAN gorunur (bkz. Money.tlExact):
            // kar/zarar ayarin "ana toplam" istisnasina girmez.
            //
            // Secili pencerenin TL'si ve yuzdesi BIRLIKTE, tek renkte. Veri
            // yoksa tire ve notr renk: bilinmeyen bir sayiya yon vermek yanlis
            // olurdu.
            percent = changeLabel(group.change, mode),
            percentColor = group.change?.let { c.delta(it.amount) } ?: c.onSurfaceMuted,
            expanded = expanded,
            onToggle = onToggle,
            chevronIcon = KefeIcons.ChevronRight,
        )

        if (expanded) {
            group.positions.forEach { position ->
                KefeHairline()
                AssetRow(position, mode, onClick = { onOpenPosition(position.id) })
            }
        }
    }
}

@Composable
private fun AssetRow(position: Position, mode: AssetChange, onClick: () -> Unit) {
    val c = KefeTheme.colors

    // Elle girilen fiyat rozetle isaretlenir - hesabin nereden geldigi gizlenmez.
    val badges: (@Composable RowScope.() -> Unit)? = if (position.manualPrice) {
        { KefeManualBadge(icon = KefeIcons.Pencil) }
    } else {
        null
    }

    // Alt rakam SECILI pencerenin TL'si ve yuzdesi. Once iki ayri sayi ust uste
    // duruyordu (hep toplam TL kar, altinda donem yuzdesi); ayni satirda iki
    // farkli soruya cevap vermek karisikti.
    val change = position.changeIn(mode)

    KefeListRow(
        title = position.name,
        subtitle = position.quantityLabel(),
        value = Money.tlExact(position.value),
        delta = change?.amount,
        deltaText = changeLabel(change, mode),
        // Veri yoksa tire, notr renkte: satirin bos kalmasi "degismedi" gibi
        // okunurdu, renk vermek de bilinmeyene yon vermek olurdu.
        deltaColor = change?.let { c.delta(it.amount) } ?: c.onSurfaceMuted,
        leadingIcon = position.assetClass.icon(),
        leadingTint = c.assetClass(position.assetClass.color()),
        onClick = onClick,
        badges = badges,
    )
}

// --- Iskelet ---------------------------------------------------------------

@Composable
private fun AssetsSkeleton() {
    Column(
        Modifier.padding(horizontal = Space.x16),
        verticalArrangement = Arrangement.spacedBy(Space.x10),
    ) {
        repeat(4) { KefeSkeletonBlock(height = 132.dp, radius = 16.dp) }
    }
}

// --- Yardimci --------------------------------------------------------------

private fun AssetClass.icon() = when (this) {
    AssetClass.Gold -> KefeIcons.Gold
    AssetClass.Silver -> KefeIcons.Silver
    AssetClass.Fx -> KefeIcons.Fx
    AssetClass.Fund -> KefeIcons.Fund
    AssetClass.Cash -> KefeIcons.Cash
}

