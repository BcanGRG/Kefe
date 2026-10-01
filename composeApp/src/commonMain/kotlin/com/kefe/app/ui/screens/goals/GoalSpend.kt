package com.kefe.app.ui.screens.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kefe.app.domain.model.GoalAsset
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.ui.components.AmountKeyboard
import com.kefe.app.ui.components.KefeBottomSheet
import com.kefe.app.ui.components.KefePrimaryButton
import com.kefe.app.ui.components.KefeSwitchRow
import com.kefe.app.ui.components.KefeTextField
import com.kefe.app.ui.components.asAmountInput
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.parseTrAmountOrNull
import com.kefe.app.ui.format.rawAmount
import com.kefe.app.ui.icons.KefeIcons
import com.kefe.app.ui.theme.KefeTheme
import com.kefe.app.ui.theme.Space
import com.kefe.app.ui.theme.tabular
import kotlin.math.min

/**
 * "Hedeften harca": hedefe ayrilan varliklardan harcama.
 *
 * Iki durum ayni akistan gecer:
 * - Hedefin kendisi icin (tatile gidildi): hepsi harcanir, hedef "Harcandı" kapanir.
 * - Acil bir sey icin (tatil parasindan araba tamiri): bir kismi harcanir, hedef
 *   ACIK kalir; ilerleme duser ve "gereken aylık" kendiliginden artar.
 *
 * Kayit uc yazimdir (bkz. GoalDetailViewModel.confirmSpend): varliklar bugunku
 * SATIS fiyatindan satilir, ayni tutarda bir harcama girilir ve hedef atamasi
 * harcanan kadar duser. Plan'daki "Kalan" degismez: para gelirden degil
 * birikimden harcanmistir (satis yatirimi azaltir, harcama gideri arttirir).
 */
data class SpendSheet(
    val lines: List<SpendLine>,
    /** Harcamanin adi - varsayilan hedefin adi ("Yurtdışı tatili"), "Araba tamiri" olabilir. */
    val name: String,
    /** Hedef "Harcandı" olarak kapansin mi. Hepsi harcaninca acik gelir. */
    val closeGoal: Boolean,
    val nameError: Boolean = false,
    val amountError: Boolean = false,
) {
    /** Harcanacak TL - her satir hedefe ayrilan miktarla sinirli. */
    val total: Double get() = lines.sumOf { it.spendQuantity * it.unitPrice }

    val spendsAll: Boolean get() = lines.all { it.spendQuantity >= it.assigned - 1e-9 }
}

data class SpendLine(
    val positionId: String,
    val title: String,
    val unit: QuantityUnit,
    /** Hedefe ayrilan miktar - harcanabilecek en cok. */
    val assigned: Double,
    /** Bugunku SATIS fiyati: "bugun satsam" tutari. */
    val unitPrice: Double,
    val quantityText: String,
) {
    val spendQuantity: Double get() = min(quantityText.parseTrAmountOrNull() ?: 0.0, assigned).coerceAtLeast(0.0)
}

/** Hedefin bugunku varliklarindan acilis hali: hepsi harcanir, hedef kapanir. */
internal fun spendSheetOf(goalName: String, assets: List<GoalAsset>): SpendSheet = SpendSheet(
    lines = assets.filter { it.quantity > 0.0 }.map { asset ->
        SpendLine(
            positionId = asset.position.id,
            title = asset.position.name,
            unit = asset.position.unit,
            assigned = asset.quantity,
            unitPrice = asset.position.unitPrice,
            quantityText = rawAmount(asset.quantity),
        )
    },
    name = goalName,
    closeGoal = true,
)

@Composable
internal fun GoalSpendSheet(
    sheet: SpendSheet?,
    goalName: String,
    onIntent: (GoalDetailIntent) -> Unit,
) {
    val c = KefeTheme.colors
    val t = KefeTheme.type

    KefeBottomSheet(
        visible = sheet != null,
        onDismiss = { onIntent(GoalDetailIntent.CloseSpend) },
        title = "Hedeften harca",
        subtitle = goalName,
        closeIcon = KefeIcons.Close,
        footer = {
            if (sheet != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.x20, vertical = Space.x8)) {
                    Text(
                        "${Money.tlExact(sheet.total)} harcanacak",
                        style = t.bodyStrong.tabular(),
                        color = c.onSurface,
                    )
                    if (sheet.amountError) {
                        Text("Harcanacak bir miktar girin.", style = t.caption, color = c.negative)
                    }
                    Spacer(Modifier.height(Space.x8))
                    KefePrimaryButton(
                        text = "Harcadım",
                        onClick = { onIntent(GoalDetailIntent.ConfirmSpend) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        if (sheet == null) return@KefeBottomSheet
        Text(
            "Seçtiğin miktar bugünkü fiyattan satılmış sayılır ve aynı tutarda bir harcama girilir. " +
                "Para birikimden harcandığı için Plan'daki Kalan değişmez.",
            style = t.caption,
            color = c.onSurfaceMuted,
        )
        sheet.lines.forEach { line ->
            Spacer(Modifier.height(Space.x16))
            KefeTextField(
                value = line.quantityText,
                onValueChange = { onIntent(GoalDetailIntent.SpendQuantity(line.positionId, it.asAmountInput())) },
                modifier = Modifier.fillMaxWidth(),
                label = line.title,
                helper = "Hedefe ayrılan ${Money.number(line.assigned, Money.decimals(line.assigned, 4))} · " +
                    "≈ ${Money.tlExact(line.spendQuantity * line.unitPrice)}",
                textStyle = t.body.tabular(),
                keyboardOptions = AmountKeyboard,
            )
        }
        Spacer(Modifier.height(Space.x16))
        KefeTextField(
            value = sheet.name,
            onValueChange = { onIntent(GoalDetailIntent.SpendName(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "Harcama",
            placeholder = "ör. Yurtdışı tatili, Araba tamiri",
            error = if (sheet.nameError) "Harcamanın adını yazın." else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(Space.x16))
        KefeSwitchRow(
            title = "Hedefi kapat",
            subtitle = if (sheet.closeGoal) {
                "Hedef \"Harcandı\" olarak kapanır."
            } else {
                "Hedef açık kalır; ilerleme düşer, gereken aylık tutar artar."
            },
            checked = sheet.closeGoal,
            onCheckedChange = { onIntent(GoalDetailIntent.SpendCloseGoal(it)) },
        )
    }
}
