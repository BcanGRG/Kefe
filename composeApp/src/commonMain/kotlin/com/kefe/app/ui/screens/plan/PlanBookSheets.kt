package com.kefe.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.label
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefeChip
import com.kefe.app.ui.components.KefeDestructiveTextButton
import com.kefe.app.ui.components.KefeFieldLabel
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeTextButton
import com.kefe.app.ui.components.KefeTextField
import com.kefe.app.ui.components.ThousandsSeparatorTransformation
import com.kefe.app.ui.components.asAmountInput
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Sizes
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular

// Defter sayfalari: gelir, harcama, butce. Her rakam elle girilir; bos alan
// "girilmedi" demektir ve kayitta satiri siler - 0 yazilmaz.

/**
 * Bir uyenin ayin geliri. Baslik "Gelir", kim ve hangi ay alt baslikta: adsiz
 * profillerde ("Ben", "Eşim") iyelik eki "Ben'in geliri" gibi bozuk okunurdu.
 */
@Composable
internal fun PlanIncomeSheet(
    visible: Boolean,
    editor: IncomeEditor?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = "Gelir",
        subtitle = editor?.let { "${it.memberName} · ${it.month.label()}" },
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        footer = { SaveFooter(onSave = { onIntent(PlanIntent.SaveIncome) }) },
    ) {
        if (editor != null) {
            IncomeField(
                label = IncomeKind.Salary.label(),
                value = editor.salaryText,
                onValueChange = { onIntent(PlanIntent.IncomeSalary(it.asAmountInput())) },
                last = editor.lastSalary,
                onUseLast = { onIntent(PlanIntent.IncomeUseLastSalary) },
            )
            Spacer(Modifier.height(Space.x16))
            IncomeField(
                label = IncomeKind.Extra.label(),
                value = editor.extraText,
                onValueChange = { onIntent(PlanIntent.IncomeExtra(it.asAmountInput())) },
                last = editor.lastExtra,
                onUseLast = { onIntent(PlanIntent.IncomeUseLastExtra) },
            )
        }
    }
}

/** Tutar alani + gecen ayin girisi varsa tek dokunusla "aynısı" cipi. */
@Composable
private fun IncomeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    last: Double?,
    onUseLast: () -> Unit,
) {
    AmountTextField(label = label, value = value, onValueChange = onValueChange)
    if (last != null && last > 0.0) {
        Spacer(Modifier.height(Space.x8))
        KefeChip(
            text = "Geçen ay: ${Money.tl(last)} — aynısı",
            selected = false,
            onClick = onUseLast,
            height = Sizes.chipSmall,
        )
    }
}

/**
 * Tek harcama ya da ay sonu toplami. Tarih sorulmaz: bu ayda bugun, baska ayda o
 * ayin son gunu (onayli kural, bkz. PlanDerive.newExpenseEditor).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanExpenseSheet(
    visible: Boolean,
    editor: ExpenseEditor?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = if (editor == null || editor.isNew) "Harcama ekle" else "Harcamayı düzenle",
        subtitle = editor?.month?.label(),
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        footer = {
            SaveFooter(
                onSave = { onIntent(PlanIntent.SaveExpense) },
                onDelete = if (editor != null && !editor.isNew) ({ onIntent(PlanIntent.DeleteExpense) }) else null,
            )
        },
    ) {
        if (editor != null) {
            KefeFieldLabel("Kategori")
            Spacer(Modifier.height(Space.x8))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x8),
                verticalArrangement = Arrangement.spacedBy(Space.x8),
            ) {
                ExpenseCategory.entries.forEach { category ->
                    val isSelected = category == editor.category
                    KefeChip(
                        text = category.label(),
                        selected = isSelected,
                        onClick = { onIntent(PlanIntent.ExpenseSelectCategory(category)) },
                        height = Sizes.chipSmall,
                        modifier = Modifier.semantics { selected = isSelected },
                    )
                }
            }
            // Ekstre ile tek tek girilen kart harcamalari ayni parayi iki kez sayardi.
            if (editor.category == ExpenseCategory.Debt) {
                Spacer(Modifier.height(Space.x8))
                NoteLine(
                    icon = KefeIcons.Info,
                    text = "Kartla yaptığınız harcamaları tek tek girdiyseniz ekstreyi ayrıca girmeyin.",
                    color = c.onSurfaceMuted,
                )
            }
            if (editor.categoryError) {
                Spacer(Modifier.height(Space.x8))
                Text("Bir kategori seçin.", style = t.caption, color = c.negative)
            }

            Spacer(Modifier.height(Space.x16))
            AmountTextField(
                label = "Tutar",
                value = editor.amountText,
                onValueChange = { onIntent(PlanIntent.ExpenseAmount(it.asAmountInput())) },
                error = if (editor.amountError) "Tutar girin." else null,
            )

            Spacer(Modifier.height(Space.x16))
            KefeTextField(
                value = editor.note,
                onValueChange = { onIntent(PlanIntent.ExpenseNote(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = "Not",
                placeholder = "İsteğe bağlı",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

/**
 * Ayin kategori butceleri. Her alanin altinda gecen ay o kategoride harcanan -
 * butceyi bir oncekine bakarak koymak icin; altta toplam ve gelire orani.
 */
@Composable
internal fun PlanBudgetSheet(
    visible: Boolean,
    editor: BudgetEditor?,
    onIntent: (PlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeBottomSheet(
        visible = visible,
        onDismiss = { onIntent(PlanIntent.DismissSheet) },
        title = "Bütçe",
        subtitle = editor?.month?.label(),
        closeIcon = KefeIcons.Close,
        modifier = modifier,
        header = if (editor != null && editor.lastBudgets.isNotEmpty()) {
            {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.x12)) {
                    KefeTextButton(
                        text = "Geçen aydan kopyala",
                        onClick = { onIntent(PlanIntent.BudgetCopyLastMonth) },
                        leadingIcon = KefeIcons.Copy,
                    )
                }
            }
        } else {
            null
        },
        footer = {
            if (editor != null) {
                SaveFooter(onSave = { onIntent(PlanIntent.SaveBudget) }) {
                    Text(editor.totalLine(), style = t.bodyStrong.tabular(), color = c.onSurface)
                }
            }
        },
    ) {
        if (editor != null) {
            ExpenseCategory.entries.forEachIndexed { index, category ->
                if (index > 0) Spacer(Modifier.height(Space.x16))
                AmountTextField(
                    label = category.label(),
                    value = editor.texts[category].orEmpty(),
                    onValueChange = { onIntent(PlanIntent.BudgetAmount(category, it.asAmountInput())) },
                    helper = editor.lastSpent[category]
                        ?.takeIf { it > 0.0 }
                        ?.let { "geçen ay ${Money.tl(it)} harcandı" },
                )
            }
        }
    }
}

/** TL tutar alani: ham metin, cizimde binlik ayrac, sagda "₺". */
@Composable
private fun AmountTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    helper: String? = null,
) {
    val c = KefeTheme.colors
    KefeTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        helper = helper,
        error = error,
        textStyle = KefeTheme.type.body.tabular(),
        keyboardOptions = AmountKeyboard,
        visualTransformation = ThousandsSeparatorTransformation(),
        trailingContent = { Text(Money.LIRA, style = KefeTheme.type.body, color = c.onSurfaceMuted) },
    )
}

/** "Kaydet" (+ duzenlemede "Sil"); ustunde istege bagli bir ozet satiri. */
@Composable
private fun SaveFooter(
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x20, vertical = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x8),
    ) {
        summary?.invoke()
        KefePrimaryButton(text = "Kaydet", onClick = onSave, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) {
            KefeDestructiveTextButton(text = "Sil", onClick = onDelete, modifier = Modifier.fillMaxWidth())
        }
    }
}
