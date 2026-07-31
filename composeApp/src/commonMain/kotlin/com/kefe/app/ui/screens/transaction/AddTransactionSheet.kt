package com.kefe.app.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.Karat
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.formatLong
import com.kefe.app.domain.model.label
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.ThousandsSeparatorTransformation
import com.kefe.app.ui.components.asAmountInput
import androidx.compose.ui.text.input.VisualTransformation
import com.kefe.app.ui.components.KefeHairline
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeStepDots
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/** 844 - 772: tasarimdaki sayfanin ust boslugu. Ekran boyu degisse de sabit kalir. */
private val SheetTopInset = 72.dp

/**
 * Islem ekle sayfasi. Iki adim: once varlik ("Ne?"), sonra miktar/fiyat
 * ("Ne kadar?"). Alanlar sayfanin kendi yuzeyinden (surfaceElevated) bir ton
 * koyu olan `surface` uzerinde durur; bu yuzden ortak alan bilesenleri
 * (surfaceSunken zeminli) yerine yerel kutular kullanilir.
 *
 * Bilesen DURUMSUZDUR: tum karar [AddTransactionViewModel] tarafinda.
 */
@Composable
fun AddTransactionSheet(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = SheetTopInset)
                .clip(KefeShapes.sheet)
                .background(c.surfaceElevated)
                .border(Sizes.hairline, c.outline, KefeShapes.sheet)
                // Panele yapilan dokunuslar scrim'e gecip sayfayi kapatmasin
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Spacer(Modifier.height(Space.x10))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(KefeShapes.pill)
                    .background(c.outline),
            )
            Spacer(Modifier.height(2.dp))

            SheetHeader(state, onDismiss)

            // Serit YALNIZ buluta ulasilamiyorsa. Once fiyat tazeliginden
            // suruluyordu: fiyat ucu tokezleyince "bağlanınca eşitlenir" yaziyor,
            // esitleme ise gayet calisiyordu.
            if (state.cloudUnreachable) OfflineStrip()

            // "Tekrar ekle" duzenlemede gizlenir: kisayol YENI kayit icindir,
            // burada dokunmak duzeltilen islemi baska bir kaydin degerleriyle
            // ezerdi.
            state.lastAdded?.takeUnless { state.isEditing }?.let { last ->
                RepeatRow(
                    label = last.label,
                    offline = state.cloudUnreachable,
                    onRepeat = { onIntent(AddTransactionIntent.RepeatLast) },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.x16),
            ) {
                if (state.isFirstStep) {
                    StepAsset(state, onIntent)
                } else {
                    StepAmount(state, onIntent)
                }
            }

            SheetFooter(state, onIntent)
        }
    }
}

// --- Baslik ve ust seritler --------------------------------------------------

@Composable
private fun SheetHeader(state: AddTransactionUiState, onDismiss: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x16, top = Space.x4, end = Space.x8, bottom = Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.stepTitle,
                style = t.h2,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = state.stepLabel,
                style = t.micro.tabular(),
                color = c.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x8))
        KefeStepDots(count = 2, activeIndex = if (state.isFirstStep) 0 else 1)
        Spacer(Modifier.width(Space.x8))
        KefeIconButton(
            icon = KefeIcons.Close,
            contentDescription = "Kapat",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun OfflineStrip() {
    val c = KefeTheme.colors

    Row(
        modifier = Modifier
            .padding(start = Space.x16, end = Space.x16, bottom = Space.x8)
            .fillMaxWidth()
            .clip(KefeShapes.button)
            .background(c.surfaceSunken)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(horizontal = Space.x16, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.CloudOff,
            contentDescription = null,
            size = IconSize.tiny,
            tint = c.syncOffline,
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = "Çevrimdışı · Kayıt cihazda tutulur, bağlanınca eşitlenir",
            style = KefeTheme.type.caption,
            color = c.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RepeatRow(label: String, offline: Boolean, onRepeat: () -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Row(
        modifier = Modifier
            .padding(start = Space.x16, end = Space.x16, bottom = Space.x12)
            .fillMaxWidth()
            .clip(KefeShapes.button)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(horizontal = Space.x12, vertical = Space.x10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KefeIcon(
            icon = KefeIcons.Refresh,
            contentDescription = null,
            size = IconSize.small,
            tint = c.onSurfaceMuted,
        )
        Spacer(Modifier.width(Space.x10))
        Text(
            text = buildAnnotatedString {
                append("Son eklediğin: ")
                withStyle(SpanStyle(color = c.onSurface)) { append(label) }
            },
            style = t.caption,
            color = c.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x10))
        if (offline) {
            PendingChip()
        } else {
            PillButton(
                text = "Tekrar ekle",
                onClick = onRepeat,
                background = c.accentMuted,
                borderColor = c.accent,
                contentColor = c.onSurface,
            )
        }
    }
}

/**
 * Bekleyen esitleme rozeti. Ortak [com.kefe.app.ui.components.KefePendingBadge]
 * buyuk harfe cevirir ve baska dolgu kullanir; buradaki serit tasarimda kucuk
 * harf ve daha dar oldugu icin yerel cizilir.
 */
@Composable
private fun PendingChip() {
    val c = KefeTheme.colors

    Row(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(c.syncPending.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(c.syncPending))
        Spacer(Modifier.width(Space.x4))
        Text(
            text = "Bekliyor",
            style = KefeTheme.type.nano.copy(fontWeight = FontWeight.SemiBold),
            color = c.syncPending,
            maxLines = 1,
        )
    }
}

// --- 1. adim: varlik ---------------------------------------------------------

@Composable
private fun StepAsset(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    // Tasarimda 3 sutunlu izgara: ikinci satirda iki kart, ucuncu hucre bos.
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
        AssetClassCard(AssetClass.Gold, state.assetClass, onIntent)
        AssetClassCard(AssetClass.Silver, state.assetClass, onIntent)
        AssetClassCard(AssetClass.Fx, state.assetClass, onIntent)
    }
    Spacer(Modifier.height(Space.x8))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
        AssetClassCard(AssetClass.Fund, state.assetClass, onIntent)
        AssetClassCard(AssetClass.Cash, state.assetClass, onIntent)
        Spacer(Modifier.weight(1f))
    }

    when (state.assetClass) {
        AssetClass.Gold -> {
            Spacer(Modifier.height(Space.x20))
            SectionLabel("Alt tür")
            Spacer(Modifier.height(Space.x8))
            SubtypeList(state, onIntent)
            if (state.selectedSubtype == GoldSubtype.Jewelry) {
                KaratPanel(state, onIntent)
            }
        }

        AssetClass.Fund -> {
            Spacer(Modifier.height(Space.x20))
            SectionLabel("Fon ara")
            Spacer(Modifier.height(Space.x8))
            FundSearchField(state, onIntent)
            Spacer(Modifier.height(Space.x10))
            FundResults(state, onIntent)
            // Girilen kod yereldeki 5 fonda yoksa TEFAS'ta canli arama satiri.
            FundOnlineSearch(state, onIntent)
            Spacer(Modifier.height(Space.x10))
            InfoLine("Fon fiyatları günde bir kez, TEFAS kapanışıyla güncellenir.")
        }

        // Doviz secilebilir olmali: once secim YOKTU ve her kayit sessizce
        // dolar oluyordu - euro girmenin bir yolu bulunmuyordu.
        AssetClass.Fx -> {
            Spacer(Modifier.height(Space.x20))
            SectionLabel("Para birimi")
            Spacer(Modifier.height(Space.x8))
            CurrencyList(state, onIntent)
            Spacer(Modifier.height(Space.x10))
            InfoLine("Kurlar TCMB günlük bülteninden gelir; hafta içi bir kez yayınlanır.")
        }

        // Gumus ve nakitte alt tur yoktur; dogrudan ikinci adima gecilir.
        else -> Unit
    }
}

@Composable
private fun CurrencyList(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.card),
    ) {
        state.currencyOptions.forEachIndexed { index, option ->
            if (index > 0) KefeHairline()
            val selected = option.currency == state.currency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) c.accentMuted else Color.Transparent)
                    .clickable(role = Role.RadioButton) {
                        onIntent(AddTransactionIntent.SelectCurrency(option.currency))
                    }
                    .padding(horizontal = Space.x14, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioDot(selected)
                Spacer(Modifier.width(Space.x12))
                Text(
                    text = option.currency.label(),
                    style = t.body,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x12))
                Text(
                    // Kuru cekilemeyen para birimi "—" ile gosterilir; sifir
                    // yazmak fiyatin sifir oldugunu soylerdi.
                    text = option.priceText.ifBlank { "—" },
                    style = t.caption.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RowScope.AssetClassCard(
    assetClass: AssetClass,
    selectedClass: AssetClass,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val selected = assetClass == selectedClass

    Column(
        modifier = Modifier
            .weight(1f)
            .height(88.dp)
            .clip(KefeShapes.card)
            .background(if (selected) c.accentMuted else c.surface)
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.card,
            )
            .clickable(role = Role.RadioButton) {
                onIntent(AddTransactionIntent.SelectAssetClass(assetClass))
            },
        verticalArrangement = Arrangement.spacedBy(Space.x8, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KefeIcon(
            icon = assetClass.icon(),
            contentDescription = null,
            size = 26.dp,
            tint = c.assetClass(assetClass.color()),
        )
        Text(
            text = assetClass.label(),
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = c.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubtypeList(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.card),
    ) {
        state.subtypes.forEachIndexed { index, option ->
            if (index > 0) KefeHairline()
            val selected = option.subtype == state.selectedSubtype
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) c.accentMuted else Color.Transparent)
                    .clickable(role = Role.RadioButton) {
                        onIntent(AddTransactionIntent.SelectSubtype(option.subtype))
                    }
                    .padding(horizontal = Space.x14, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioDot(selected)
                Spacer(Modifier.width(Space.x12))
                Text(
                    text = option.subtype.label(),
                    style = t.body,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x12))
                Text(
                    text = option.priceText,
                    style = t.caption.tabular(),
                    color = c.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val c = KefeTheme.colors
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) c.surface else Color.Transparent)
            .border(
                width = if (selected) 6.dp else 1.5.dp,
                color = if (selected) c.accent else c.onSurfaceMuted,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun KaratPanel(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .padding(top = Space.x12)
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.accentMuted)
            .padding(Space.x14),
    ) {
        SectionLabel("Ayar")
        Spacer(Modifier.height(Space.x8))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            Karat.entries.forEach { karat ->
                KaratChip(
                    karat = karat,
                    selected = karat == state.karat,
                    onClick = { onIntent(AddTransactionIntent.SelectKarat(karat)) },
                )
            }
        }

        Spacer(Modifier.height(Space.x16))
        SectionLabel("Gram")
        Spacer(Modifier.height(Space.x8))
        FieldRow(height = Sizes.fieldLarge, borderColor = c.accent, gap = Space.x8) {
            SheetInput(
                value = state.gramText,
                onValueChange = { onIntent(AddTransactionIntent.ChangeGram(it.asAmountInput())) },
                keyboardOptions = AmountKeyboard,
                visualTransformation = ThousandsSeparatorTransformation(),
                textStyle = amountStyle(),
                modifier = Modifier.weight(1f),
                placeholder = "0",
            )
            Text("gr", style = t.body, color = c.onSurfaceMuted)
        }

        Spacer(Modifier.height(Space.x10))
        Text(
            text = state.karatLine,
            style = t.caption.tabular(),
            color = c.onSurfaceMuted,
        )
    }
}

@Composable
private fun RowScope.KaratChip(karat: Karat, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.touchTarget)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accent else c.surface)
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.pill,
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = karat.shortLabel(),
            style = KefeTheme.type.bodyStrong,
            color = if (selected) c.onAccent else c.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

// --- 1. adim: fon ------------------------------------------------------------

@Composable
private fun FundSearchField(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors

    FieldRow(
        height = Sizes.fieldDefault,
        borderColor = c.accent,
        gap = Space.x10,
    ) {
        KefeIcon(
            icon = KefeIcons.Search,
            contentDescription = null,
            size = IconSize.medium,
            tint = c.onSurfaceMuted,
        )
        SheetInput(
            value = state.fundQuery,
            onValueChange = { onIntent(AddTransactionIntent.ChangeFundQuery(it)) },
            textStyle = KefeTheme.type.body,
            modifier = Modifier.weight(1f),
            placeholder = "Fon adı veya kodu",
        )
        if (state.fundQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c.surfaceSunken)
                    .clickable(role = Role.Button) {
                        onIntent(AddTransactionIntent.ChangeFundQuery(""))
                    },
                contentAlignment = Alignment.Center,
            ) {
                KefeIcon(
                    icon = KefeIcons.Close,
                    contentDescription = "Aramayı temizle",
                    size = 14.dp,
                    tint = c.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun FundResults(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    if (state.fundResults.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.card),
    ) {
        state.fundResults.forEachIndexed { index, fund ->
            if (index > 0) KefeHairline()
            val selected = fund.assetKey == state.selectedFundKey
            val stripe = 3.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) {
                        onIntent(AddTransactionIntent.SelectFund(fund.assetKey))
                    }
                    // Secili satirin sol cizgisi; icerik tasarimdaki gibi 3dp kayar.
                    .drawBehind {
                        if (selected) {
                            drawRect(
                                color = c.accent,
                                size = Size(stripe.toPx(), size.height),
                            )
                        }
                    }
                    .padding(
                        start = Space.x14 + (if (selected) stripe else 0.dp),
                        end = Space.x14,
                        top = Space.x12,
                        bottom = Space.x12,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fund.code,
                    style = t.caption.copy(fontWeight = FontWeight.Bold),
                    color = c.fund,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp),
                )
                Spacer(Modifier.width(Space.x12))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = fund.name,
                        style = t.caption.copy(lineHeight = 17.sp),
                        color = c.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Kurucu yalniz katalog/canli sonuclarda var; tutulan fon
                    // onerisinde bos kalir, o zaman satir hic cizilmez.
                    if (fund.issuer.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = fund.issuer,
                            style = t.micro,
                            color = c.onSurfaceMuted,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(Space.x12))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Money.tl(fund.price, decimals = 2),
                        style = t.caption.tabular(),
                        color = c.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        // Isaret her zaman yazilir - renk tek sinyal degildir.
                        text = Money.delta(fund.changePercent),
                        style = t.micro.tabular(),
                        color = c.delta(fund.changePercent),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Girilen kod yereldeki 5 fonda yoksa TEFAS'ta canli arama. Uc hal: aranirken
 * metin, sonuc yoksa uyari, aksi halde "ara" satiri. Bosta hicbir sey cizmez -
 * kendi ust boslugunu da yalniz icerik varken ekler.
 */
@Composable
private fun FundOnlineSearch(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    val hasContent = state.fundSearching ||
        state.fundSearchError != null ||
        state.fundSearchCode != null
    if (!hasContent) return

    Spacer(Modifier.height(Space.x10))
    when {
        state.fundSearching -> Text(
            "TEFAS'ta aranıyor…",
            style = t.caption,
            color = c.onSurfaceMuted,
        )

        state.fundSearchError != null -> Text(
            state.fundSearchError,
            style = t.caption,
            color = c.negative,
        )

        state.fundSearchCode != null -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .border(Sizes.hairline, c.accent, KefeShapes.button)
                .clickable(role = Role.Button) {
                    onIntent(AddTransactionIntent.SearchFundOnline)
                }
                .padding(horizontal = Space.x14, vertical = Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeIcon(
                icon = KefeIcons.Search,
                contentDescription = null,
                size = IconSize.medium,
                tint = c.accent,
            )
            Spacer(Modifier.width(Space.x10))
            Text(
                "TEFAS'ta \"${state.fundSearchCode}\" ara",
                style = t.caption.copy(fontWeight = FontWeight.Bold),
                color = c.accent,
            )
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    val c = KefeTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        KefeIcon(
            icon = KefeIcons.Info,
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp),
            size = 14.dp,
            tint = c.onSurfaceMuted,
        )
        Spacer(Modifier.width(Space.x8))
        Text(
            text = text,
            style = KefeTheme.type.micro.copy(lineHeight = 16.sp),
            color = c.onSurfaceMuted,
        )
    }
}

// --- 2. adim: miktar ve fiyat ------------------------------------------------

@Composable
private fun StepAmount(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    SideSelector(state.side) { onIntent(AddTransactionIntent.SelectSide(it)) }

    Spacer(Modifier.height(18.dp))
    SectionLabel("Miktar")
    Spacer(Modifier.height(6.dp))
    QuantityField(state, onIntent)

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel("Birim fiyat")
        PriceBadge(manual = state.priceManual, hasMarket = state.hasMarketPrice)
    }
    Spacer(Modifier.height(6.dp))
    FieldRow(height = Sizes.fieldLarge, borderColor = c.outline, gap = Space.x8) {
        Text(Money.LIRA, style = t.body, color = c.onSurfaceMuted)
        SheetInput(
            value = state.unitPriceText,
            onValueChange = { onIntent(AddTransactionIntent.ChangeUnitPrice(it.asAmountInput())) },
            keyboardOptions = AmountKeyboard,
            visualTransformation = ThousandsSeparatorTransformation(),
            textStyle = t.h2.tabular(),
            modifier = Modifier.weight(1f),
            placeholder = "0",
        )
        // Donulecek bir piyasa fiyati yoksa dugme de yok.
        if (state.priceManual && state.hasMarketPrice) {
            PillButton(
                text = "Güncele dön",
                onClick = { onIntent(AddTransactionIntent.ResetPriceToMarket) },
                background = Color.Transparent,
                borderColor = c.outline,
                contentColor = c.accent,
            )
        }
    }
    // Fiyat alinamadiysa kullanici kilitli kalmasin: alan bos gelir, ne
    // yapmasi gerektigi yazar. Once "0" yaziyordu ve Kaydet sebepsiz pasifti.
    if (!state.hasMarketPrice && !state.priceManual) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bu varlık için güncel fiyat alınamadı — ödediğiniz birim fiyatı yazın.",
            style = t.micro,
            color = c.onSurfaceMuted,
        )
    } else if (state.priceManual && state.hasMarketPrice) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.priceCompareLine,
            style = t.micro.tabular(),
            color = c.onSurfaceMuted,
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel("Tarih")
    Spacer(Modifier.height(6.dp))
    DateField(state.date, state.isToday)

    // Hedef secici YALNIZ hedef varken cizilir. Secilirse varlik o hedefe atanir;
    // secilmezse dogrudan toplam birikime eklenir.
    if (state.availableGoals.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        GoalPicker(state, onIntent)
    }

    Spacer(Modifier.height(18.dp))
    ExtraFields(state, onIntent)
    Spacer(Modifier.height(Space.x16))
}

/**
 * Varligi bir hedefe atamak icin cip seridi. "Hedefsiz" + her hedef. Bu, hedef
 * detayindaki "Varlık seç" ile ayni atamayi ONDEN yapar - kullanici varligi
 * eklerken hangi hedefe saydigina karar verir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalPicker(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    SectionLabel("Hedef")
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        GoalChip(
            label = "Hedefsiz",
            selected = state.selectedGoalId == null,
            onClick = { onIntent(AddTransactionIntent.SelectGoal(null)) },
        )
        state.availableGoals.forEach { goal ->
            GoalChip(
                label = goal.name,
                selected = state.selectedGoalId == goal.id,
                onClick = { onIntent(AddTransactionIntent.SelectGoal(goal.id)) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = if (state.selectedGoalId == null) {
            "Toplam birikime eklenir."
        } else {
            "Seçilen hedefin \"karşılayanlar\" listesine sayılır."
        },
        style = t.caption,
        color = c.onSurfaceMuted,
    )
}

@Composable
private fun GoalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(if (selected) c.accentMuted else c.surface)
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.pill,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x14, vertical = Space.x8),
    ) {
        Text(
            text = label,
            style = KefeTheme.type.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) c.accent else c.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SideSelector(side: TradeSide, onSelect: (TradeSide) -> Unit) {
    val c = KefeTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.pill)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .padding(Space.x4),
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        SideSegment("Alış", side == TradeSide.Buy) { onSelect(TradeSide.Buy) }
        SideSegment("Satış", side == TradeSide.Sell) { onSelect(TradeSide.Sell) }
    }
}

@Composable
private fun RowScope.SideSegment(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.chipLarge)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accent else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.bodyStrong,
            color = if (selected) c.onAccent else c.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuantityField(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors

    FieldRow(
        height = Sizes.fieldAmount,
        borderColor = c.accent,
        gap = Space.x8,
        paddingEnd = Space.x8,
    ) {
        SheetInput(
            value = state.quantityText,
            onValueChange = { onIntent(AddTransactionIntent.ChangeQuantity(it.asAmountInput())) },
            keyboardOptions = AmountKeyboard,
            visualTransformation = ThousandsSeparatorTransformation(),
            textStyle = amountStyle(),
            modifier = Modifier.weight(1f),
            placeholder = "0",
        )
        // Nakitte birim yazilmaz (tutar zaten TL). Dovizde ise para biriminin
        // simgesi yazilir: "250" tek basina hangi paradan 250 oldugunu
        // soylemiyor.
        val unit = if (state.assetClass == AssetClass.Fx) {
            state.currency.symbol()
        } else {
            state.quantityUnit.label()
        }
        if (unit.isNotEmpty()) {
            Text(text = unit, style = KefeTheme.type.body, color = c.onSurfaceMuted)
        }
        StepButton(KefeIcons.MinusSmall, "Azalt") {
            onIntent(AddTransactionIntent.DecrementQuantity)
        }
        StepButton(KefeIcons.PlusSmall, "Artır") {
            onIntent(AddTransactionIntent.IncrementQuantity)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = IconSize.medium,
            tint = c.onSurface,
        )
    }
}

/**
 * Birim fiyatin nereden geldigini soyleyen rozet.
 *
 * Ucuncu bir hal var: fiyat HIC yok. Cevrimdisi ilk acilista ya da beslemenin
 * kapsamadigi bir varlikta piyasa fiyati 0 geliyordu ve rozet buna "Güncel
 * fiyat" diyordu - sifir bir fiyat degil, fiyatin olmamasidir.
 */
@Composable
private fun PriceBadge(manual: Boolean, hasMarket: Boolean) {
    val c = KefeTheme.colors
    val muted = manual || !hasMarket
    val contentColor = if (muted) c.onSurfaceMuted else c.accent

    Row(
        modifier = Modifier
            .clip(KefeShapes.pill)
            .background(if (muted) c.surfaceSunken else c.accentMuted)
            // Tasarimda guncel fiyat rozetinin cercevesi saydamdir ama yer kaplar:
            // iki rozet de ayni boyda durur, gecerken 2dp ziplamaz.
            .border(
                Sizes.hairline,
                if (muted) c.outline else Color.Transparent,
                KefeShapes.pill,
            )
            .padding(horizontal = Space.x8, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (manual) {
            KefeIcon(
                icon = KefeIcons.Pencil,
                contentDescription = null,
                size = 11.dp,
                tint = contentColor,
            )
            Spacer(Modifier.width(Space.x4))
        }
        Text(
            text = when {
                manual -> "Elle girildi"
                !hasMarket -> "Fiyat yok"
                else -> "Güncel fiyat"
            }.trUpper(),
            style = KefeTheme.type.label(10, 0.06, FontWeight.Bold),
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun DateField(date: KefeDate, isToday: Boolean) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    FieldRow(height = Sizes.fieldDefault, borderColor = c.outline, gap = Space.x10) {
        KefeIcon(
            icon = KefeIcons.Calendar,
            contentDescription = null,
            size = IconSize.small,
            tint = c.onSurfaceMuted,
        )
        Text(
            text = date.formatLong(),
            style = t.body.tabular(),
            color = c.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (isToday) {
            Box(
                modifier = Modifier
                    .clip(KefeShapes.pill)
                    .background(c.surfaceSunken)
                    .padding(horizontal = Space.x8, vertical = 3.dp),
            ) {
                Text("Bugün", style = t.micro, color = c.onSurfaceMuted, maxLines = 1)
            }
        }
    }
}

// --- 2. adim: ek alanlar -----------------------------------------------------

@Composable
private fun ExtraFields(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.card)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIntent(AddTransactionIntent.ToggleExtra) }
                .padding(Space.x14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KefeIcon(
                icon = if (state.extraExpanded) KefeIcons.ChevronDown else KefeIcons.ChevronRight,
                contentDescription = null,
                size = IconSize.small,
                tint = c.onSurfaceMuted,
            )
            Spacer(Modifier.width(Space.x10))
            Text("Ek alanlar", style = t.body, color = c.onSurface, modifier = Modifier.weight(1f))
            if (!state.extraExpanded) {
                Spacer(Modifier.width(Space.x10))
                Text("işçilik, not, saklama", style = t.caption, color = c.onSurfaceMuted)
            }
        }

        if (state.extraExpanded) {
            Column(
                Modifier.padding(start = Space.x14, end = Space.x14, bottom = Space.x14),
                verticalArrangement = Arrangement.spacedBy(Space.x12),
            ) {
                Column {
                    ExtraLabel("İşçilik / komisyon")
                    ExtraField {
                        Text(Money.LIRA, style = t.caption, color = c.onSurfaceMuted)
                        Spacer(Modifier.width(6.dp))
                        SheetInput(
                            value = state.feeText,
                            onValueChange = { onIntent(AddTransactionIntent.ChangeFee(it.asAmountInput())) },
                            keyboardOptions = AmountKeyboard,
                            visualTransformation = ThousandsSeparatorTransformation(),
                            textStyle = t.body.tabular(),
                            modifier = Modifier.weight(1f),
                            placeholder = "0",
                        )
                    }
                }

                Column {
                    ExtraLabel("Not")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .clip(KefeShapes.button)
                            .background(c.surfaceElevated)
                            .border(Sizes.hairline, c.outline, KefeShapes.button)
                            .padding(horizontal = Space.x12, vertical = Space.x10),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SheetInput(
                            value = state.note,
                            onValueChange = { onIntent(AddTransactionIntent.ChangeNote(it)) },
                            textStyle = t.body,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Kuyumcu Ali · pazar",
                            singleLine = false,
                        )
                    }
                }

                Column {
                    ExtraLabel("Nerede saklanıyor")
                    ExtraField {
                        KefeIcon(
                            icon = KefeIcons.Lock,
                            contentDescription = null,
                            size = IconSize.tiny,
                            tint = c.onSurfaceMuted,
                        )
                        Spacer(Modifier.width(Space.x8))
                        SheetInput(
                            value = state.storage,
                            onValueChange = { onIntent(AddTransactionIntent.ChangeStorage(it)) },
                            textStyle = t.body,
                            modifier = Modifier.weight(1f),
                            placeholder = "Evdeki kasa",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraLabel(text: String) {
    Text(
        text = text,
        style = KefeTheme.type.micro,
        color = KefeTheme.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ExtraField(content: @Composable RowScope.() -> Unit) {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .background(c.surfaceElevated)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(horizontal = Space.x12),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// --- Altlik ------------------------------------------------------------------

@Composable
private fun SheetFooter(
    state: AddTransactionUiState,
    onIntent: (AddTransactionIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Column(Modifier.fillMaxWidth().background(c.surfaceElevated)) {
        KefeHairline()
        Column(
            Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x12,
                bottom = Space.x20,
            ),
        ) {
            if (!state.isFirstStep) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Toplam",
                        style = t.caption,
                        color = c.onSurfaceMuted,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = Money.tl(state.total),
                        style = amountStyle(),
                        color = c.onSurface,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Spacer(Modifier.height(Space.x10))
            }

            KefePrimaryButton(
                text = state.ctaText,
                onClick = {
                    onIntent(
                        if (state.isFirstStep) {
                            AddTransactionIntent.Continue
                        } else {
                            AddTransactionIntent.Save
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
                leadingIcon = if (state.cloudUnreachable && !state.isFirstStep) {
                    KefeIcons.CloudOff
                } else {
                    null
                },
            )

            if (!state.isFirstStep) {
                Spacer(Modifier.height(Space.x8))
                Text(
                    text = state.footNote,
                    style = t.micro,
                    color = c.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- Ortak parcalar ----------------------------------------------------------

/** Alan ustu baslik: 11px, .06em, yari kalin, Turkce buyuk harf. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.trUpper(),
        style = KefeTheme.type.label(11, 0.06, FontWeight.SemiBold),
        color = KefeTheme.colors.onSurfaceMuted,
    )
}

/** Tasarimdaki 28px yari kalin rakam: harf araligi sifirlanir, tabular acilir. */
@Composable
private fun amountStyle(): TextStyle =
    KefeTheme.type.h1.copy(letterSpacing = 0.em).tabular()

@Composable
private fun FieldRow(
    height: Dp,
    borderColor: Color,
    gap: Dp,
    modifier: Modifier = Modifier,
    background: Color = KefeTheme.colors.surface,
    paddingStart: Dp = Space.x14,
    paddingEnd: Dp = Space.x14,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(KefeShapes.button)
            .background(background)
            .border(Sizes.hairline, borderColor, KefeShapes.button)
            .padding(start = paddingStart, end = paddingEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** Tutar alanlari icin binlik ayrac (deger ham kalir; bkz. imlec sorunu). */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = KefeTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle.copy(color = c.onSurface),
        singleLine = singleLine,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = c.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    background: Color,
    borderColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .height(Sizes.chipSmall)
            .clip(KefeShapes.pill)
            .background(background)
            .border(Sizes.hairline, borderColor, KefeShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.captionSmall.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1,
        )
    }
}

// --- Esleme yardimcilari -----------------------------------------------------

private fun AssetClass.icon(): ImageVector = when (this) {
    AssetClass.Gold -> KefeIcons.Gold
    AssetClass.Silver -> KefeIcons.Silver
    AssetClass.Fx -> KefeIcons.Fx
    AssetClass.Fund -> KefeIcons.Fund
    AssetClass.Cash -> KefeIcons.Cash
}

/** Cip uzerinde yalniz sayi durur: "14", "18", "22", "24". */
private fun Karat.shortLabel(): String = when (this) {
    Karat.K14 -> "14"
    Karat.K18 -> "18"
    Karat.K22 -> "22"
    Karat.K24 -> "24"
}
