package com.kefe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kefe.app.ui.format.trUpper
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeShapes
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import kotlinx.coroutines.delay

/**
 * Metin girisi bilesenleri. Material3 TextField kendi ic dolgusunu ve
 * etiket animasyonunu dayattigi icin BasicTextField uzerine elle kuruldu.
 */

/**
 * Odaklanan alani TAMAMIYLA gorunur kilar - alanin KUTUSUNA konur, girisin
 * kendisine degil.
 *
 * NEDEN. Compose zaten odaklanan metni gorunur kiliyor, ama getirdigi sey
 * metnin kendi siniridir: alanin cercevesi, etiketi ve yardim satiri disarida
 * kalabiliyor. Gercek cihazda ekleme sayfasinda klavye acilinca "Birim fiyat"
 * kutusunun alt kenari sayfanin sabit altligi tarafindan kirpiliyordu - rakam
 * okunuyor ama alan yarim gorunuyordu.
 *
 * `onFocusChanged` KAPSAYICIYA konur ve `hasFocus` ile alt agactaki girisin
 * odagini da gorur; boylece her giris bileseninin ayri ayri bilmesi gerekmez.
 *
 * IKI KEZ ISTENIR: klavye acilirken gorunur alan kucululmeye devam ediyor, ilk
 * istek eski olcuye gore kaydiriyor.
 */
@Composable
fun Modifier.bringFieldIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        requester.bringIntoView()
        delay(ImeSettleMillis)
        requester.bringIntoView()
    }

    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.hasFocus }
}

/**
 * Klavyenin eylem tusunun VARSAYILAN davranisi: "İleri" bir sonraki alana
 * gecer, "Tamam" klavyeyi kapatir.
 *
 * Once hicbir sey yapmiyordu: ard arda iki alan varken kullanici "Tamam"a
 * basip klavyeyi kapatiyor, sonra ikinci alana elle dokunuyordu.
 */
@Composable
fun defaultKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return remember(focusManager) {
        KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        )
    }
}

/** Alan ustu etiket: microLabel + Turkce buyuk harf. */
@Composable
fun KefeFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.trUpper(),
        style = KefeTheme.type.microLabel,
        color = KefeTheme.colors.onSurfaceMuted,
        modifier = modifier,
    )
}

@Composable
fun KefeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    height: Dp = Sizes.fieldDefault,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    textStyle: TextStyle = KefeTheme.type.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /**
     * Klavyenin eylem tusu. null ise VARSAYILAN davranis:
     * "İleri" bir sonraki alana gecer, "Tamam" klavyeyi kapatir.
     */
    keyboardActions: KeyboardActions? = null,
) {
    val colors = KefeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hasError = error != null
    val actions = keyboardActions ?: defaultKeyboardActions()

    // Hata odaktan onceliklidir: kullanici duzeltirken de kirmizi kalir.
    val borderColor = when {
        hasError -> colors.negative
        focused -> colors.accent
        else -> colors.outline
    }

    Column(modifier.bringFieldIntoView()) {
        if (label != null) {
            KefeFieldLabel(label)
            Spacer(Modifier.height(Space.x8))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (singleLine) Modifier.height(height)
                    else Modifier.heightIn(min = height),
                )
                .clip(KefeShapes.boxMedium)
                .background(colors.surfaceSunken)
                .border(Sizes.hairline, borderColor, KefeShapes.boxMedium)
                .padding(
                    horizontal = Space.x14,
                    vertical = if (singleLine) 0.dp else Space.x12,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.onSurfaceMuted,
                        modifier = Modifier.size(IconSize.medium),
                    )
                    Spacer(Modifier.width(Space.x10))
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    textStyle = textStyle.copy(color = colors.onSurface),
                    singleLine = singleLine,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = actions,
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textStyle,
                                    color = colors.onSurfaceMuted,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (trailingContent != null) {
                    Spacer(Modifier.width(Space.x10))
                    trailingContent()
                }
            }
        }

        val message = error ?: helper
        if (message != null) {
            Spacer(Modifier.height(Space.x8))
            Text(
                text = message,
                style = KefeTheme.type.caption,
                color = if (hasError) colors.negative else colors.onSurfaceMuted,
            )
        }
    }
}

/**
 * Miktar girisi: buyuk tabular rakam, birim etiketi ve 44x44 azalt/artir
 * butonlari. Ikonlar disaridan verilir - bilesen ikon setine bagli degildir.
 */
@Composable
fun KefeAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    unitLabel: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    minusIcon: ImageVector,
    plusIcon: ImageVector,
    modifier: Modifier = Modifier,
    /**
     * Klavye turu. Once HIC VERILMIYORDU: miktar alani tam metin klavyesi
     * aciyor, eylem tusu de hicbir sey yapmiyordu.
     */
    keyboardOptions: KeyboardOptions = AmountKeyboard,
    keyboardActions: KeyboardActions? = null,
) {
    val colors = KefeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val actions = keyboardActions ?: defaultKeyboardActions()

    Box(
        modifier = modifier
            .bringFieldIntoView()
            .fillMaxWidth()
            .height(Sizes.fieldAmount)
            .clip(KefeShapes.boxMedium)
            .background(colors.surfaceSunken)
            .border(
                width = Sizes.hairline,
                color = if (focused) colors.accent else colors.outline,
                shape = KefeShapes.boxMedium,
            )
            .padding(horizontal = Space.x8),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmountStepButton(
                icon = minusIcon,
                contentDescription = "Azalt",
                onClick = onDecrement,
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = KefeTheme.type.h1.tabular().copy(
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = actions,
                interactionSource = interactionSource,
            )

            Spacer(Modifier.width(Space.x8))
            Text(
                text = unitLabel.trUpper(),
                style = KefeTheme.type.microLabel,
                color = colors.onSurfaceMuted,
            )
            Spacer(Modifier.width(Space.x8))

            AmountStepButton(
                icon = plusIcon,
                contentDescription = "Artir",
                onClick = onIncrement,
            )
        }
    }
}

@Composable
private fun AmountStepButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(KefeShapes.boxSmall)
            .background(KefeTheme.colors.surfaceElevated)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = KefeTheme.colors.onSurface,
            modifier = Modifier.size(IconSize.medium),
        )
    }
}

/** Arama alani: sol arama ikonu, doluyken sagda temizleme butonu. */
@Composable
fun KefeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    searchIcon: ImageVector,
    clearIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val clearButton: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(Sizes.touchTarget)
                .clip(KefeShapes.boxSmall)
                .clickable(role = Role.Button) { onValueChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = clearIcon,
                contentDescription = "Temizle",
                tint = KefeTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(IconSize.small),
            )
        }
    }

    KefeTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = searchIcon,
        trailingContent = if (value.isEmpty()) null else clearButton,
    )
}

// --- Klavye turleri ---------------------------------------------------------

/**
 * Tutar/miktar alanlari icin ONDALIKLI SAYI klavyesi.
 *
 * Butun alanlar tam metin klavyesi aciyordu: kullanici tutar yazmak icin once
 * sayi sekmesine gecmek zorunda kaliyor ve harf yazabiliyordu.
 *
 * Sayi tuslari ekranda kalsin diye `Decimal`; tr-TR'de ondalik ayirici virgul
 * oldugu icin klavye onu da gosterir.
 */
val AmountKeyboard: KeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)

/** Ondalik istemeyen alanlar (adet, kod) icin. */
val IntegerKeyboard: KeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)

/**
 * Ayni klavye ama eylem tusu "İleri": ARDINDAN BASKA BIR ALAN GELIYORSA.
 *
 * Varsayilan `Done`: son alanda klavyeyi kapatmak dogru davranis. Aradaki
 * alanlarda ise kullanici "Tamam"a basip klavyeyi kapatiyor, sonra bir sonraki
 * alana elle dokunuyordu.
 */
fun KeyboardOptions.next(): KeyboardOptions = copy(imeAction = ImeAction.Next)

/**
 * Tutar girisini temizler: yalniz rakam ve TEK ondalik ayirici kalir.
 *
 * Klavye turu yeterli DEGIL - donanim klavyesi, yapistirma ve bazi ekran
 * klavyeleri harf gonderebiliyor. Ayrica ikinci bir virgul "12,3,4" gibi
 * ayristirilamayan bir metin uretirdi.
 *
 * Nokta virgule cevrilir: kullanici hangi tusa basarsa bassin ayni sey demek
 * istiyor ve ayristirici tr-TR bekliyor.
 */
fun String.asAmountInput(): String {
    val builder = StringBuilder()
    var separatorUsed = false
    for (ch in this) {
        when {
            ch.isDigit() -> builder.append(ch)
            (ch == ',' || ch == '.') && !separatorUsed && builder.isNotEmpty() -> {
                separatorUsed = true
                builder.append(',')
            }
        }
    }
    return builder.toString()
}

/**
 * Klavye acilma animasyonunun oturma suresi.
 *
 * Sistem IME'si yaklasik 250 ms'de acilir ve bu sure boyunca gorunur alan
 * kuculmeye devam eder; alani gorunur kilma istegi bir kez daha bu sureden
 * SONRA yapilir.
 */
private const val ImeSettleMillis = 320L
