package com.kefe.app.ui.screens.transaction

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.MonthPlanProgress
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.monthName
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.screens.plan.planTargetText
import com.kefe.app.ui.screens.plan.quantityLabel

/**
 * Islem sayfasinin plan satiri.
 *
 * - Varlik planda: "Eylül planında: 10 gr · Ev · kalan 4 gr" (bitmisse "tamamlandı").
 * - Ayin plani var ama varlik yok: "Eylül planında yok · plan dışı sayılır".
 * - Ayin plani yok: null - plan yapmayan kullaniciya her alimda "plan dışı" demek
 *   gurultu olurdu.
 *
 * Kalan, bu kayit HENUZ yazilmadan onceki kalandir; kullanici miktari ona gore
 * secer.
 */
internal fun planHintOf(
    progress: MonthPlanProgress,
    assetKey: String,
    goals: List<Goal>,
    conflictGoalId: String?,
): PlanHint? {
    if (progress.items.isEmpty()) return null
    val month = progress.month.monthName()
    val line = progress.items.firstOrNull { it.item.assetKey == assetKey }
        ?: return PlanHint(text = "$month planında yok · plan dışı sayılır", inPlan = false)

    val item = line.item
    val goalName = item.goalId?.let { id -> goals.firstOrNull { it.id == id }?.name }
    val left = when {
        line.remaining <= 0.0 -> "tamamlandı"
        item.mode == PlanTargetMode.Quantity -> "kalan ${quantityLabel(line.remaining, item.assetKey)}"
        else -> "kalan ${Money.tlExact(line.remaining)}"
    }
    val text = listOfNotNull(
        "$month planında: ${planTargetText(item.mode, item.target, item.assetKey)}",
        goalName,
        left,
    ).joinToString(" · ")

    // Cakisma: varlik baska hedefte, onsecim onu korudu. Hedef adlari tirnakli -
    // ek almadan okunur ("Ev'e" / "Araba'ya" her adda ayri kural isterdi).
    val conflict = conflictGoalId?.takeIf { goalName != null }
        ?.let { id -> goals.firstOrNull { it.id == id }?.name }
        ?.let { current -> "Bu varlık şu an “$current” hedefinde; plan “$goalName” diyor. Değiştirmek için hedefi aşağıdan seçin." }

    return PlanHint(text = text, inPlan = true, conflict = conflict)
}
