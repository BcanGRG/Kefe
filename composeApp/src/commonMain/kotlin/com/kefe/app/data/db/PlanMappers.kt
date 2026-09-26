package com.kefe.app.data.db

import com.kefe.app.db.Expense_budgets
import com.kefe.app.db.Expense_entries
import com.kefe.app.db.Income_entries
import com.kefe.app.db.Plan_items
import com.kefe.app.domain.model.ExpenseBudget
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.IncomeEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.YearMonth

// Plan/butce satirlari -> alan. Enum benzeri kolonlar DUZ METIN (bkz. 12.sqm);
// burada SAVUNMACI cevrilir: daha yeni bir surumden gelen deger bu telefonu
// cokertmemeli.

/**
 * Bilinmeyen [PlanTargetMode] ile gelen satir null doner ve hesaplardan
 * dusulur - ama tabloda KALIR ve esitlemede aynen tasinir. Birimi
 * bilinmeyen bir hedefi miktar ya da tutar diye tahmin etmek skoru yanlis
 * hesaplardi.
 */
internal fun Plan_items.toDomain(): PlanItem? {
    val mode = PlanTargetMode.entries.firstOrNull { it.name == mode } ?: return null
    val month = runCatching { YearMonth(periodYear.toInt(), periodMonth.toInt()) }.getOrNull() ?: return null
    return PlanItem(
        id = id,
        month = month,
        assetKey = assetKey,
        assetName = assetName,
        mode = mode,
        target = target,
        goalId = goalId,
        unitPriceAtPlan = unitPriceAtPlan,
    )
}

internal fun Income_entries.toDomain(): IncomeEntry? {
    val month = runCatching { YearMonth(periodYear.toInt(), periodMonth.toInt()) }.getOrNull() ?: return null
    return IncomeEntry(
        id = id,
        month = month,
        memberId = memberId,
        kind = IncomeKind.fromName(kind),
        amount = amount,
    )
}

internal fun Expense_entries.toDomain(): ExpenseEntry = ExpenseEntry(
    id = id,
    date = KefeDate(dateYear.toInt(), dateMonth.toInt(), dateDay.toInt()),
    category = ExpenseCategory.fromName(category),
    amount = amount,
    note = note,
    addedByMemberId = addedByMemberId,
    createdAt = createdAt,
)

internal fun Expense_budgets.toDomain(): ExpenseBudget? {
    val month = runCatching { YearMonth(periodYear.toInt(), periodMonth.toInt()) }.getOrNull() ?: return null
    return ExpenseBudget(
        id = id,
        month = month,
        category = ExpenseCategory.fromName(category),
        amount = amount,
    )
}
