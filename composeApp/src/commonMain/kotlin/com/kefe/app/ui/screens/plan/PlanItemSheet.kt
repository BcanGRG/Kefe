package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.label
import com.kefe.app.domain.model.monthName
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.IntegerKeyboard
import com.kefe.app.ui.components.KefeAmountField
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeDestructiveTextButton
import com.kefe.app.ui.components.KefeFieldLabel
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSegmentedControl
import com.kefe.app.ui.components.KefeTextField
import com.kefe.app.ui.components.ThousandsSeparatorTransformation
import com.kefe.app.ui.components.asAmountInput
import com.kefe.app.ui.icons.KefeIcon
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.IconSize
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

/**
 * Plan kalemi sayfasi: varlik (hafif secici), Miktar/Tutar, hedef miktari ve
 * hedef cipi. Kaydederken guncel ALIS fiyati kalemle saklanir (bkz. PlanViewModel).
 */
@Composable
internal fun PlanItemSheet(
    visible: Boolean,
    editor: PlanItemEditor?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = if (editor == null || editor.isNew) "Plan kalemi" else "Kalemi düzenle",
        subtitle = editor?.month?.label(),
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        footer = { if (editor != null) ItemFooter(editor, onIntent) },
    ) {
        if (editor != null) ItemBody(editor, onIntent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ItemBody(editor: PlanItemEditor, onIntent: (PlanIntent) -> Unit) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    if (editor.switchedToExisting) {
        NoteLine(
            icon = KefeIcons.Info,
            text = "Bu varlık ${editor.month.monthName()} planında zaten var — o kalemi düzenliyorsunuz.",
            color = c.onSurfaceMuted,
        )
        Spacer(Modifier.height(Space.x16))
    }

    // --- Varlik ---
    KefeFieldLabel("Varlık")
    Spacer(Modifier.height(Space.x8))
    editor.groups().forEach { (assetClass, options) ->
        Text(assetClass.label(), style = t.micro, color = c.onSurfaceMuted)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x8),
            verticalArrangement = Arrangement.spacedBy(Space.x8),
        ) {
            options.forEach { option ->
                val isSelected = editor.codeClass == null && option.assetKey == editor.assetKey
                KefeChip(
                    text = option.name,
                    selected = isSelected,
                    onClick = { onIntent(PlanIntent.ItemSelectAsset(option.assetKey)) },
                    height = Sizes.chipSmall,
                    // Secim ekran okuyucuya da soylenir. Bilesende degil burada: "Bu ay"
                    // gibi eylem cipleri "seçili değil" diye okunmamali.
                    modifier = Modifier.semantics { selected = isSelected },
                )
            }
            codeChipLabel(assetClass)?.let { label ->
                KefeChip(
                    text = label,
                    selected = editor.codeClass == assetClass,
                    onClick = { onIntent(PlanIntent.ItemOpenCode(assetClass)) },
                    height = Sizes.chipSmall,
                )
            }
        }
        if (editor.codeClass == assetClass) {
            Spacer(Modifier.height(Space.x10))
            CodeField(editor, assetClass, onIntent)
        }
        Spacer(Modifier.height(Space.x12))
    }
    // Kod alani aciksa hata alanin altinda; degilse ciplerin altinda.
    if (editor.assetError != null && editor.codeClass == null) {
        Text(editor.assetError, style = t.caption, color = c.negative)
        Spacer(Modifier.height(Space.x12))
    }

    // --- Miktar / Tutar ---
    if (editor.showModeSwitch()) {
        KefeSegmentedControl(
            options = listOf("Miktar", "Tutar"),
            selectedIndex = if (editor.mode == PlanTargetMode.Quantity) 0 else 1,
            onSelect = { index ->
                onIntent(PlanIntent.ItemMode(if (index == 0) PlanTargetMode.Quantity else PlanTargetMode.Amount))
            },
        )
        Spacer(Modifier.height(Space.x16))
    }

    KefeFieldLabel(editor.targetLabel())
    Spacer(Modifier.height(Space.x8))
    // Adetle alinan altin bolunmez: ondaliksiz klavye (kayit da tam sayi ister).
    val wholePieces = editor.mode == PlanTargetMode.Quantity && editor.selectedUnit() == QuantityUnit.Piece
    KefeAmountField(
        value = editor.targetText,
        onValueChange = { onIntent(PlanIntent.ItemTarget(it.asAmountInput())) },
        unitLabel = editor.unitLabel(),
        onIncrement = { onIntent(PlanIntent.ItemStep(up = true)) },
        onDecrement = { onIntent(PlanIntent.ItemStep(up = false)) },
        minusIcon = KefeIcons.MinusSmall,
        plusIcon = KefeIcons.PlusSmall,
        keyboardOptions = if (wholePieces) IntegerKeyboard else AmountKeyboard,
        visualTransformation = ThousandsSeparatorTransformation(),
    )
    editor.targetError?.let { error ->
        Spacer(Modifier.height(Space.x8))
        Text(error, style = t.caption, color = c.negative)
    }
    editor.estimateText()?.let { estimate ->
        Spacer(Modifier.height(Space.x8))
        Text(estimate, style = t.caption.tabular(), color = c.onSurfaceMuted)
    }

    // --- Hedef ---
    Spacer(Modifier.height(Space.x16))
    KefeFieldLabel("Hedef")
    Spacer(Modifier.height(Space.x8))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        val none = editor.goalId == null
        KefeChip(
            text = "Hedefsiz",
            selected = none,
            onClick = { onIntent(PlanIntent.ItemGoal(null)) },
            height = Sizes.chipSmall,
            modifier = Modifier.semantics { selected = none },
        )
        editor.goalChips.forEach { goal ->
            val isSelected = goal.goalId == editor.goalId
            KefeChip(
                text = goal.name,
                selected = isSelected,
                onClick = { onIntent(PlanIntent.ItemGoal(goal.goalId)) },
                height = Sizes.chipSmall,
                modifier = Modifier.semantics { selected = isSelected },
            )
        }
    }
    editor.conflictGoalName?.let { name ->
        Spacer(Modifier.height(Space.x8))
        // Plan atamayi TASIMAZ; yalniz bilgi. Renk tek sinyal degil: ikon ve metin.
        NoteLine(
            icon = KefeIcons.Info,
            text = "Bu varlık şu an “$name” hedefinde. Plan atamayı değiştirmez.",
            color = c.warning,
        )
    }
}

/** Katalogda olmayan fon/hisse icin kod alani. */
@Composable
private fun CodeField(editor: PlanItemEditor, assetClass: AssetClass, onIntent: (PlanIntent) -> Unit) {
    val stock = assetClass == AssetClass.Stock
    KefeTextField(
        value = editor.codeText,
        onValueChange = { onIntent(PlanIntent.ItemCode(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = if (stock) "Hisse sembolü" else "Fon kodu",
        placeholder = if (stock) "THYAO.IS" else "AFA",
        helper = if (stock) "BIST için .IS ekleyin" else null,
        error = editor.assetError,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Next,
        ),
    )
}

/**
 * Fon ve hisse katalogda yok (binlercesi var); grubun sonundaki cip kod alanini acar.
 * Hic fon/hisse tutulmasa da cizilir.
 */
private fun codeChipLabel(assetClass: AssetClass): String? = when (assetClass) {
    AssetClass.Fund -> "+ Fon kodu"
    AssetClass.Stock -> "+ Hisse sembolü"
    else -> null
}

@Composable
private fun ItemFooter(editor: PlanItemEditor, onIntent: (PlanIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x20, vertical = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        KefePrimaryButton(
            text = "Kaydet",
            onClick = { onIntent(PlanIntent.SaveItem) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!editor.isNew) {
            KefeDestructiveTextButton(
                text = "Sil",
                onClick = { onIntent(PlanIntent.DeleteItem) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Ikonlu tek satir bilgi/uyari - renk metin ve ikonla birlikte. */
@Composable
internal fun NoteLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        KefeIcon(icon, contentDescription = null, size = IconSize.tiny, tint = color, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(Space.x8))
        Text(text, style = KefeTheme.type.caption, color = color)
    }
}
