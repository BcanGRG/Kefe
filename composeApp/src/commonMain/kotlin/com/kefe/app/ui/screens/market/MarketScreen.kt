package com.kefe.app.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.color
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.asAmountInput
import com.kefe.app.ui.components.KefeDashedCard
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefePullToRefresh
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeTextButton
import com.kefe.app.ui.components.KefeTextField
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeColors
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Piyasa - emniyet supabi ekrani. Fiyat kaynagi coktugunde uygulamayi ayakta
 * tutan sey buradaki kalem butonudur: kullanici fiyati kendi girer, satir "Elle"
 * isaretlenir ve hesap kesintisiz surer.
 *
 * Tablo hizalamasi: yapiskan baslik ile satirlar AYNI olcu duzenini kullanir
 * ([ColBid]/[ColAsk]/[ColChange]/[ColAction] + [ColGap]); basligin yan dolgusu
 * satir icerigi ile ayni noktaya denk gelsin diye [TableStart]/[TableEnd] olarak
 * kart disi dolgu + satir ic dolgusu toplanarak yazilmistir.
 */
@Composable
fun MarketScreen(
    state: MarketUiState,
    onIntent: (MarketIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MarketTopBar(state = state, onIntent = onIntent, onBack = onBack)

            KefePullToRefresh(
                refreshing = state.refreshing,
                onRefresh = { onIntent(MarketIntent.Refresh) },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    stickyHeader { MarketTableHeader() }

                    items(
                        items = state.sections,
                        key = { it.assetClass.name },
                    ) { section ->
                        MarketSectionBlock(section = section, onIntent = onIntent)
                    }

                    item { MarketSafetyValveNote() }
                }
            }
        }

        ManualPriceSheet(edit = state.edit, onIntent = onIntent)
    }
}

// --- Ust bar ---------------------------------------------------------------

@Composable
private fun MarketTopBar(
    state: MarketUiState,
    onIntent: (MarketIntent) -> Unit,
    onBack: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = Space.x12)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = Space.x16),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Baslangic 6dp: tasarimda 16dp kap dolgusu icinde -10px negatif kenar
            // bosluklu 44'luk buton var; gorsel hizasi 6dp'ye denk gelir.
            KefeIconButton(
                icon = KefeIcons.ArrowBack,
                contentDescription = "Geri",
                onClick = onBack,
                tint = c.onSurface,
            )
            Text(
                text = "Piyasa",
                style = t.h1,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MarketRefreshButton(onClick = { onIntent(MarketIntent.Refresh) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.x16, end = Space.x16, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.freshnessDot(state.freshness))
                )
                Text(
                    text = state.updatedLine(),
                    style = t.micro.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }

            Box(Modifier.width(Sizes.hairline).height(11.dp).background(c.outline))

            Text(
                text = "Kaynak: Serbest piyasa · Fonlar: TEFAS",
                style = t.micro,
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Tasarimdaki hap bicimli "Yenile": accent kenarlik + accentMuted zemin. */
@Composable
private fun MarketRefreshButton(onClick: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .height(Sizes.chipLarge)
            .clip(KefeShapes.pill)
            .background(c.accentMuted)
            .border(Sizes.hairline, c.accent, KefeShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x14),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.Refresh,
            contentDescription = null,
            size = IconSize.tiny,
            tint = c.accent,
        )
        Text(
            text = "Yenile",
            style = t.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurface,
            maxLines = 1,
        )
    }
}

// --- Tablo basligi ---------------------------------------------------------

@Composable
private fun MarketTableHeader() {
    val c = KefeTheme.colors
    val style = KefeTheme.type.label(10, 0.07, FontWeight.Bold)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(start = TableStart, end = TableEnd, bottom = Space.x8),
        horizontalArrangement = Arrangement.spacedBy(ColGap),
    ) {
        Text("ürün".trUpper(), style = style, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
        Text(
            text = "alış".trUpper(),
            style = style,
            color = c.onSurfaceMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(ColBid),
        )
        Text(
            text = "satış".trUpper(),
            style = style,
            color = c.onSurfaceMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(ColAsk),
        )
        Text(
            text = "değ.".trUpper(),
            style = style,
            color = c.onSurfaceMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(ColChange),
        )
        Spacer(Modifier.width(ColAction))
    }
}

// --- Bolum -----------------------------------------------------------------

@Composable
private fun MarketSectionBlock(
    section: MarketSection,
    onIntent: (MarketIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.x16)) {
        Row(
            modifier = Modifier.padding(top = Space.x12, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kare degil, 3dp yumusatilmis kare: donut/legend noktalarindan ayrisir.
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.assetClass(section.assetClass.color()))
            )
            Text(
                text = section.title,
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.card)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.card)
        ) {
            section.rows.forEachIndexed { index, row ->
                if (index > 0) KefeHairline()
                MarketPriceRow(row = row, onIntent = onIntent)
            }
        }

        if (section.note != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.x8),
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                KefeIcon(
                    icon = KefeIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp),
                    size = 14.dp,
                    tint = c.onSurfaceMuted,
                )
                Text(
                    text = section.note,
                    style = t.micro.copy(lineHeight = 16.sp),
                    color = c.onSurfaceMuted,
                )
            }
        }
    }
}

// --- Satir -----------------------------------------------------------------

@Composable
private fun MarketPriceRow(
    row: MarketRow,
    onIntent: (MarketIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Elle girilen satir zeminiyle ayrisir; rozet ve kaynak satiri da
            // ayni bilgiyi metinle tekrarlar - renk tek sinyal degildir.
            .background(if (row.isManual) c.accentMuted else Color.Transparent)
            .padding(start = 14.dp, top = 11.dp, end = Space.x12, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(ColGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name,
                    style = t.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isManual) MarketManualBadge()
            }

            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.sourceLine,
                    style = t.nano.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isManual) {
                    Text(
                        text = "sıfırla",
                        style = t.nano.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = c.accent,
                        maxLines = 1,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = null,
                            role = Role.Button,
                        ) { onIntent(MarketIntent.ClearManualPrice(row.assetKey)) },
                    )
                }
            }
        }

        Text(
            text = row.bidText,
            style = t.caption.tabular(),
            color = c.onSurfaceMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(ColBid),
        )
        Text(
            text = row.askText,
            style = t.caption.copy(fontWeight = FontWeight.SemiBold).tabular(),
            color = c.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(ColAsk),
        )
        Text(
            // Isaret her zaman yazilir: yon bilgisi renge birakilmaz.
            text = Money.delta(row.changePercent),
            style = t.captionSmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
            color = c.delta(row.changePercent),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(ColChange),
        )

        MarketPencilButton(onClick = { onIntent(MarketIntent.OpenManualPrice(row.assetKey)) })
    }
}

/**
 * Satirdaki "Elle" rozeti. Ortak [com.kefe.app.ui.components.KefeManualBadge]
 * sunken zemin/notr metin tasir; tabloda ise accent zemin ve 9px metin isteniyor -
 * ortak bileseni bozmamak icin buraya ozel yazildi.
 */
@Composable
private fun MarketManualBadge() {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(c.accentMuted)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(icon = KefeIcons.Pencil, contentDescription = null, size = 8.dp, tint = c.accent)
        Text(
            text = "elle".trUpper(),
            style = t.nano.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
            color = c.accent,
            maxLines = 1,
        )
    }
}

/**
 * Kalem butonu: tasarimda 32x32 kutu (ustune -4px dikey negatif bosluk, yani
 * satir yuksekligini hic buyutmez). Kutu 44dp yapilirsa satir ~10dp sisiyordu;
 * ad + kaynak sutunu zaten 32dp'den yuksek oldugu icin dokunma alani satir
 * boyunca korunur.
 */
@Composable
private fun MarketPencilButton(onClick: () -> Unit) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .width(ColAction)
            .height(PencilBoxSize)
            .clip(KefeShapes.boxSmall)
            .background(if (hovered) c.surfaceSunken else Color.Transparent)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = KefeIcons.Pencil,
            contentDescription = "Fiyatı elle gir",
            size = 17.dp,
            tint = if (hovered) c.accent else c.onSurfaceMuted,
        )
    }
}

// --- Emniyet supabi notu ---------------------------------------------------

@Composable
private fun MarketSafetyValveNote() {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeDashedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x20,
                bottom = Space.x24,
            ),
        contentPadding = PaddingValues(Space.x14),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x10)) {
            KefeIcon(
                icon = KefeIcons.Balance,
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp),
                size = IconSize.small,
                tint = c.onSurfaceMuted,
            )
            Text(
                text = "Kaynak çökerse hiçbir şey durmaz: kalem simgesiyle fiyatı " +
                    "kendiniz girin, Kefe onu \"Elle\" olarak işaretler ve hesabı sürdürür.",
                style = t.caption.copy(lineHeight = 19.sp),
                color = c.onSurfaceMuted,
            )
        }
    }
}

// --- Elle fiyat sayfasi ----------------------------------------------------

@Composable
private fun ManualPriceSheet(
    edit: MarketPriceEdit?,
    onIntent: (MarketIntent) -> Unit,
) {
    // Kapanis animasyonu sirasinda durum null olur; baslik ve alan bir anda
    // bosalmasin diye son gorunen deger tutulur.
    var shown by remember { mutableStateOf<MarketPriceEdit?>(null) }
    LaunchedEffect(edit) { if (edit != null) shown = edit }
    val current = shown

    KefeBottomSheet(
        visible = edit != null,
        onDismiss = { onIntent(MarketIntent.DismissManualPrice) },
        title = "Fiyatı elle gir",
        subtitle = current?.name,
        closeIcon = KefeIcons.Close,
        footer = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x20, vertical = Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                KefePrimaryButton(
                    text = "Kaydet",
                    onClick = { onIntent(MarketIntent.SetManualPrice) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current?.isManual == true) {
                    KefeTextButton(
                        text = "Elle girileni sıfırla",
                        onClick = { onIntent(MarketIntent.ClearManualPrice(current.assetKey)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        KefeTextField(
            value = current?.input.orEmpty(),
            onValueChange = { onIntent(MarketIntent.ChangeManualPrice(it.asAmountInput())) },
            keyboardOptions = AmountKeyboard,
            modifier = Modifier.fillMaxWidth(),
            label = "Satış fiyatı",
            placeholder = "0,00",
            helper = "Elle girilen fiyat otomatik yenilemede ezilmez; " +
                "satırdaki sıfırla ile kaynağa dönersiniz.",
            error = if (current?.invalid == true) "Geçerli bir tutar girin." else null,
            textStyle = KefeTheme.type.body.tabular(),
        )
    }
}

// --- Olculer ---------------------------------------------------------------

/** Baslik ve satirlarin ortak sutun duzeni. Ikisi de ayni degerleri kullanir. */
private val ColBid = 58.dp
private val ColAsk = 58.dp
private val ColChange = 48.dp
private val ColAction = 32.dp
private val ColGap = Space.x8

/** Kalem butonunun kutusu - tasarimdaki 32x32. */
private val PencilBoxSize = 32.dp

/**
 * 16dp kart disi dolgu + 14dp satir ic dolgusu. Kenarlik EKLENMEZ: Compose'da
 * `Modifier.border` cizgiyi olculerin icine cizer, CSS'teki gibi icerigi 1px
 * itmez - eklenirse baslik satirlardan 1dp kayar.
 */
private val TableStart = 30.dp

/** 16dp kart disi dolgu + 12dp satir ic dolgusu (kenarlik yine eklenmez). */
private val TableEnd = 28.dp

// --- Yardimci --------------------------------------------------------------

private fun KefeColors.freshnessDot(freshness: PriceFreshness): Color = when (freshness) {
    PriceFreshness.Fresh -> syncOk
    PriceFreshness.Stale -> syncPending
    PriceFreshness.Offline -> syncOffline
}

private fun MarketUiState.updatedLine(): String = when {
    refreshing -> "Güncelleniyor…"
    updatedAtLabel.isBlank() || updatedAtLabel == "—" -> "Henüz güncellenmedi"
    else -> "$updatedAtLabel'de güncellendi"
}
