package com.kefe.app.domain.model

/**
 * "Gecen ayi kopyala" ve eksiklerin devri.
 *
 * DEVIR SORULUR, KENDILIGINDEN OLMAZ. Otomatik devirde iki ay ust uste eksik
 * kalan bir satir ucuncu ayda uc katina cikar ve plan gercekci olmaktan
 * cikar. Her eksik satir icin "Taşı" ya da "Bırak" secilir; varsayilan
 * Bırak'tir.
 */

/** Hedef aydan onceki, satiri olan en yakin ay. */
fun copySourceFor(target: YearMonth, items: List<PlanItem>): YearMonth? =
    items.filter { it.month < target }.maxOfOrNull { it.month }

data class CopyRow(
    val assetKey: String,
    val name: String,
    val mode: PlanTargetMode,
    /** Hedef ayda zaten planliysa onun hedefi, degilse kaynak ayinki. */
    val baseTarget: Double,
    val alreadyInTarget: Boolean,
    /** Kaynak ayda eksik kalan; sifirsa devredecek bir sey yok. */
    val shortfall: Double,
    val goalId: String?,
    val unitPriceAtPlan: Double?,
    /** Kaynak ay henuz bitmedi - eksik miktar kesin degil. */
    val provisional: Boolean,
)

fun planCopyDraft(
    source: MonthPlanProgress,
    target: YearMonth,
    existingTarget: List<PlanItem>,
    today: KefeDate,
): List<CopyRow> {
    val existing = existingTarget.filter { it.month == target }.associateBy { it.assetKey }
    val provisional = source.month >= YearMonth.of(today)
    return source.items.map { p ->
        val already = existing[p.item.assetKey]
        CopyRow(
            assetKey = p.item.assetKey,
            name = p.item.assetName,
            mode = already?.mode ?: p.item.mode,
            baseTarget = already?.target ?: p.item.target,
            alreadyInTarget = already != null,
            // Birimi degismis satirda (miktar <-> tutar) eksik tasinamaz.
            shortfall = if (already == null || already.mode == p.item.mode) p.remaining else 0.0,
            goalId = already?.goalId ?: p.item.goalId,
            unitPriceAtPlan = already?.unitPriceAtPlan ?: p.item.unitPriceAtPlan,
            provisional = provisional,
        )
    }
}

/**
 * Taslaktan yazilacak satirlar. Zaten planli olup devri secilmeyen satir
 * DOKUNULMADAN birakilir - listede yer almaz.
 */
fun List<CopyRow>.toItems(
    target: YearMonth,
    carry: Set<String>,
    /** Guncel alis fiyati; yoksa kaynaktaki anlik goruntu kullanilir. */
    priceOf: (String) -> Double?,
): List<PlanItem> = mapNotNull { row ->
    val carried = row.assetKey in carry && row.shortfall > 0.0
    if (row.alreadyInTarget && !carried) return@mapNotNull null
    PlanItem(
        id = planItemId(target, row.assetKey),
        month = target,
        assetKey = row.assetKey,
        assetName = row.name,
        mode = row.mode,
        target = row.baseTarget + if (carried) row.shortfall else 0.0,
        goalId = row.goalId,
        unitPriceAtPlan = priceOf(row.assetKey) ?: row.unitPriceAtPlan,
    )
}
