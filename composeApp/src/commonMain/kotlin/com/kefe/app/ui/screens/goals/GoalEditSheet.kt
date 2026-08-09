package com.kefe.app.ui.screens.goals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kefe.app.domain.model.GoalAllocation
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.formatMonthYear
import com.kefe.app.domain.model.monthName
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.KefeBackHandler
import com.kefe.app.ui.components.KefeIconButton
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSwitch
import com.kefe.app.ui.components.ThousandsSeparatorTransformation
import com.kefe.app.ui.components.asAmountInput
import com.kefe.app.ui.components.bringFieldIntoView
import com.kefe.app.ui.components.defaultKeyboardActions
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

/** 844 - 756: tasarimdaki sayfanin ust bosluğu. Ekran boyu degisse de sabit kalir. */
private val SheetTopInset = 88.dp

/**
 * Hedef olustur/duzenle sayfasi. Tasarimda alanlar sayfanin kendi yuzeyinden
 * (surfaceElevated) bir ton koyu olan `surface` uzerinde durur - bu yuzden
 * ortak alan bilesenleri (surfaceSunken zeminli) yerine yerel kutular kullanilir.
 */
@Composable
fun GoalEditSheet(
    state: GoalEditorState?,
    onIntent: (GoalsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Geri tusu sayfayi kapatir. Isleyici erken donusun ONUNDE ve KOSULSUZ:
    // sonrasina konsaydi sayfa her acilip kapandiginda bestelemeye girip cikacak
    // ve sira bozulacakti (bkz. KefeBackHandler).
    KefeBackHandler(enabled = state != null) { onIntent(GoalsIntent.DismissEditor) }
    if (state == null) return

    val c = KefeTheme.colors
    val t = KefeTheme.type

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onIntent(GoalsIntent.DismissEditor) },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x16, end = Space.x8, top = 6.dp, bottom = Space.x10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isNew) "Yeni hedef" else "Hedefi düzenle",
                    style = t.h2,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x8))
                KefeIconButton(
                    icon = KefeIcons.Close,
                    contentDescription = "Kapat",
                    onClick = { onIntent(GoalsIntent.DismissEditor) },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.x16),
            ) {
                SheetBody(state, onIntent)
            }

            SheetFooter(state, onIntent)
        }
    }
}

// --- Govde -----------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetBody(state: GoalEditorState, onIntent: (GoalsIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    // Bos zorunlu alan uyarisi: hataliysa alan kirmizi cizilir, altina mesaj yazilir
    // ve o alana kaydirilir. Ad hem ilk zorunlu alan; iki alan da bossa ona gidilir.
    val nameRequester = remember { BringIntoViewRequester() }
    val amountRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.nameError) { if (state.nameError) nameRequester.bringIntoView() }
    LaunchedEffect(state.amountError) {
        if (state.amountError && !state.nameError) amountRequester.bringIntoView()
    }

    SheetLabel("Ad")
    Spacer(Modifier.height(6.dp))
    SheetTextField(
        value = state.name,
        onValueChange = { onIntent(GoalsIntent.EditorName(it)) },
        placeholder = "Örneğin: Ev",
        isError = state.nameError,
        modifier = Modifier.bringIntoViewRequester(nameRequester),
    )
    if (state.nameError) {
        Spacer(Modifier.height(6.dp))
        Text("Hedefe bir isim verin.", style = t.caption, color = c.negative)
    }

    Spacer(Modifier.height(Space.x16 + 2.dp))
    SheetLabel("İkon")
    Spacer(Modifier.height(Space.x8))
    GoalIconKeys.chunked(IconGridColumns).forEachIndexed { rowIndex, keys ->
        if (rowIndex > 0) Spacer(Modifier.height(Space.x8))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x8)) {
            keys.forEach { key ->
                IconCell(
                    key = key,
                    selected = key == state.iconKey,
                    onClick = { onIntent(GoalsIntent.EditorIcon(key)) },
                )
            }
        }
    }

    Spacer(Modifier.height(Space.x16 + 2.dp))
    SheetLabel("Tutar")
    Spacer(Modifier.height(6.dp))
    SheetTextField(
        value = state.amountText,
        onValueChange = { onIntent(GoalsIntent.EditorAmount(it.asAmountInput())) },
        keyboardOptions = AmountKeyboard,
        visualTransformation = ThousandsSeparatorTransformation(),
        placeholder = "0",
        height = Sizes.fieldLarge,
        textStyle = t.h1.copy(letterSpacing = 0.em).tabular(),
        isError = state.amountError,
        modifier = Modifier.bringIntoViewRequester(amountRequester),
        trailing = {
            Text(state.unit.suffix(), style = t.body, color = c.onSurfaceMuted)
        },
    )
    if (state.amountError) {
        Spacer(Modifier.height(6.dp))
        Text("Bir hedef tutarı girin.", style = t.caption, color = c.negative)
    }

    Spacer(Modifier.height(Space.x16 + 2.dp))
    SheetLabel("Hedef birimi")
    Spacer(Modifier.height(Space.x8))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.pill)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.pill)
            .padding(Space.x4),
        horizontalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        GoalUnit.entries.forEach { unit ->
            // Kuru gelmemis birim SECILEMEZ. Once secilebiliyordu ve cevrim
            // 1.0 kuruyla yapiliyordu: "400 gram" hedefi ₺400 olarak
            // kaydediliyordu.
            val enabled = state.rateKnown(unit)
            UnitSegment(
                text = unit.label(),
                selected = unit == state.unit,
                enabled = enabled,
                onClick = { onIntent(GoalsIntent.EditorUnit(unit)) },
            )
        }
    }

    Spacer(Modifier.height(Space.x10))
    SheetInfoBox(
        text = "Fiyatlar TL'de artıyor. Hedefi altın veya dolar cinsinden " +
            "sabitlerseniz hedef de piyasayla birlikte güncellenir.",
        background = c.surface,
        bordered = true,
    )

    if (state.unit == GoalUnit.GoldGram) {
        val grams = state.amountText.parseAmount()
        val price = state.goldGramPrice
        Spacer(Modifier.height(Space.x10))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KefeShapes.button)
                .background(c.accentMuted)
                .padding(horizontal = Space.x14, vertical = Space.x12),
        ) {
            Text(
                "Hedef ${Money.quantity(grams, "gr altın")}",
                style = t.bodyStrong.tabular(),
                color = c.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Bugünkü kurla ${Money.tl(grams * price)} · gram ${Money.tl(price)}",
                style = t.caption.tabular(),
                color = c.onSurfaceMuted,
            )
        }
    }

    Spacer(Modifier.height(Space.x16 + 2.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x10)) {
        Column(Modifier.weight(1f)) {
            SheetLabel("Hedef tarihi")
            Spacer(Modifier.height(6.dp))
            // Alan ay-yil gosterir, secici de o incelikte: tam takvim bu ekranda
            // gereksiz - hedefin gunu diye bir sey yok.
            SheetDisplayBox(
                onClick = { onIntent(GoalsIntent.ToggleEditorDatePicker) },
            ) {
                KefeIcon(
                    KefeIcons.Calendar,
                    null,
                    size = IconSize.small,
                    tint = c.onSurfaceMuted,
                )
                Text(
                    state.targetDate.formatMonthYear(),
                    style = t.body.tabular(),
                    color = c.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            SheetLabel("Aylık katkı")
            Spacer(Modifier.height(6.dp))
            SheetTextField(
                value = state.contributionText,
                onValueChange = { onIntent(GoalsIntent.EditorContribution(it.asAmountInput())) },
                keyboardOptions = AmountKeyboard,
                visualTransformation = ThousandsSeparatorTransformation(),
                placeholder = "0",
                textStyle = t.body.tabular(),
                // Tasarimda alan tek parca "₺50.000" gosterir - araya bosluk girmez.
                spacing = 0.dp,
                leading = { Text("₺", style = t.body, color = c.onSurface) },
            )
        }
    }

    if (state.datePickerOpen) {
        GoalDatePicker(
            date = state.targetDate,
            onShift = { onIntent(GoalsIntent.EditorShiftTargetDate(it)) },
            onClose = { onIntent(GoalsIntent.ToggleEditorDatePicker) },
        )
    }

    // "Gelismis > ayrilan pay" (allocation) KALDIRILDI: toggle hicbir seyi
    // degistirmiyordu (goalWealth atamaya gore calisir, allocation'a degil) ve
    // kafa karistiriyordu. Hedefi olusturan varliklar Hedef Detayi'nda gorulur;
    // atama yoksa tum birikim sayilir.

    Spacer(Modifier.height(Space.x16 + 2.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.button)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(Space.x14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Ana hedef yap", style = t.body, color = c.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Özet ekranında bu hedef görünür",
                style = t.micro,
                color = c.onSurfaceMuted,
            )
        }
        Spacer(Modifier.width(Space.x12))
        KefeSwitch(
            checked = state.isMain,
            onCheckedChange = { onIntent(GoalsIntent.EditorMain(it)) },
        )
    }
    Spacer(Modifier.height(Space.x24))
}

// --- Altlik ----------------------------------------------------------------

@Composable
private fun SheetFooter(state: GoalEditorState, onIntent: (GoalsIntent) -> Unit) {
    val c = KefeTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surfaceElevated)) {
        Box(Modifier.fillMaxWidth().height(Sizes.hairline).background(c.outline))
        Column(
            Modifier.padding(
                start = Space.x16,
                end = Space.x16,
                top = Space.x12,
                bottom = Space.x20,
            ),
        ) {
            KefePrimaryButton(
                text = "Kaydet",
                onClick = { onIntent(GoalsIntent.SaveEditor) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isNew) {
                Spacer(Modifier.height(Space.x4))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.touchTarget)
                        .clip(KefeShapes.button)
                        .clickable { onIntent(GoalsIntent.DeleteEditorGoal) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Hedefi sil",
                        style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.negative,
                    )
                }
            }
        }
    }
}

// --- Parcalar --------------------------------------------------------------

private const val IconGridColumns = 6

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.trUpper(),
        style = KefeTheme.type.label(11, 0.06, FontWeight.SemiBold),
        color = KefeTheme.colors.onSurfaceMuted,
    )
}

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.fieldDefault,
    textStyle: TextStyle = KefeTheme.type.body,
    /** Bas/son ek ile deger arasindaki bosluk. Para on eki metne bitisiktir. */
    spacing: Dp = Space.x8,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** Tutar alanlari icin binlik ayrac (deger ham kalir; bkz. imlec sorunu). */
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** Bos birakilip kaydedilmeye calisilan zorunlu alan: kirmizi cerceve. */
    isError: Boolean = false,
) {
    val c = KefeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = modifier
            // Klavye acilinca alanin TAMAMI gorunsun - kutuya konur, girise
            // degil; yoksa cerceve altligin altinda kirpiliyor.
            .bringFieldIntoView()
            .fillMaxWidth()
            .height(height)
            .clip(KefeShapes.button)
            .background(c.surface)
            .border(
                Sizes.hairline,
                when {
                    isError -> c.negative
                    focused -> c.accent
                    else -> c.outline
                },
                KefeShapes.button,
            )
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        leading?.invoke()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = textStyle.copy(color = c.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = defaultKeyboardActions(),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = c.onSurfaceMuted,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}

@Composable
private fun SheetDisplayBox(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.fieldDefault)
            .clip(KefeShapes.button)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = Space.x14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        content = content,
    )
}

/**
 * Ay-yil secici.
 *
 * Alan zaten yalniz ay ve yil gosteriyor; tam takvim hem yer kaplar hem de
 * olmayan bir inceligi (gun) varmis gibi gosterir. Yil adimi ayri duruyor cunku
 * hedefler cogu zaman yillar sonrasina konuyor - 48 kez aya basmak istemezsin.
 */
@Composable
private fun GoalDatePicker(
    date: KefeDate,
    onShift: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    Spacer(Modifier.height(Space.x10))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KefeShapes.button)
            .background(c.surfaceSunken)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .padding(Space.x12),
    ) {
        DateStepperRow(label = "Ay", value = date.monthName(), onBack = { onShift(-1) }) {
            onShift(1)
        }
        Spacer(Modifier.height(Space.x8))
        DateStepperRow(label = "Yıl", value = date.year.toString(), onBack = { onShift(-12) }) {
            onShift(12)
        }
        Spacer(Modifier.height(Space.x10))
        Text(
            text = "Tamam",
            style = t.bodyStrong,
            color = c.accent,
            modifier = Modifier
                .align(Alignment.End)
                .clip(KefeShapes.button)
                .clickable(onClick = onClose)
                .padding(horizontal = Space.x12, vertical = Space.x4),
        )
    }
}

@Composable
private fun DateStepperRow(
    label: String,
    value: String,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        Text(label, style = t.caption, color = c.onSurfaceMuted, modifier = Modifier.weight(1f))
        StepperButton(KefeIcons.MinusSmall, onBack)
        Text(
            text = value,
            style = t.bodyStrong.tabular(),
            color = c.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp),
        )
        StepperButton(KefeIcons.PlusSmall, onForward)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .clip(KefeShapes.button)
            .background(c.surface)
            .border(Sizes.hairline, c.outline, KefeShapes.button)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(icon, null, size = IconSize.small, tint = c.onSurface)
    }
}

@Composable
private fun SheetInfoBox(text: String, background: Color, bordered: Boolean) {
    val c = KefeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KefeShapes.button)
            .background(background)
            .then(
                if (bordered) Modifier.border(Sizes.hairline, c.outline, KefeShapes.button)
                else Modifier,
            )
            .padding(Space.x12),
        verticalAlignment = Alignment.Top,
    ) {
        KefeIcon(
            icon = KefeIcons.Info,
            contentDescription = null,
            size = IconSize.small,
            tint = c.onSurfaceMuted,
            modifier = Modifier.padding(top = 1.dp),
        )
        Spacer(Modifier.width(Space.x10))
        Text(
            text = text,
            style = KefeTheme.type.caption.copy(lineHeight = 19.sp),
            color = c.onSurfaceMuted,
        )
    }
}

@Composable
private fun RowScope.IconCell(key: String, selected: Boolean, onClick: () -> Unit) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.fieldDefault)
            .clip(KefeShapes.button)
            .background(if (selected) c.accentMuted else c.surface)
            .border(
                Sizes.hairline,
                if (selected) c.accent else c.outline,
                KefeShapes.button,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KefeIcon(
            icon = KefeIcons.goalIcon(key),
            contentDescription = null,
            size = 22.dp,
            tint = if (selected) c.accent else c.onSurfaceMuted,
        )
    }
}

@Composable
private fun RowScope.UnitSegment(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = KefeTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Sizes.segment)
            .clip(KefeShapes.pill)
            .background(if (selected) c.accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KefeTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            // Kuru gelmemis birim SOLUK cizilir: dokunulamadigi gorunsun.
            color = when {
                selected -> c.onAccent
                !enabled -> c.onSurfaceMuted.copy(alpha = 0.4f)
                else -> c.onSurfaceMuted
            },
            maxLines = 1,
        )
    }
}

/** Tutar alanindaki birim son eki. */
private fun GoalUnit.suffix(): String = when (this) {
    GoalUnit.Try -> "₺"
    GoalUnit.GoldGram -> "gr altın"
    GoalUnit.Usd -> "USD"
}

/** Gelismis bolumun sag ustundeki ozet etiketi. */
private fun GoalAllocation.label(): String = when (this) {
    GoalAllocation.AllWealth -> "Tüm birikim"
    GoalAllocation.FixedShare -> "Belirli bir pay"
}
