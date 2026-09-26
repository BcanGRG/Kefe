package com.kefe.app.domain.model

/**
 * "Kac aydir duzenli" - TURETILIR, saklanmaz.
 *
 * Bir ay, TL agirlikli plan skoru %80'e ulastiginda duzenli sayilir
 * ([MonthPlanProgress.isRegular]). %80-99 arasi ay sari gorunur ama seriyi
 * SURDURUR: aylik bir seride her kirilma pahalidir (yilda yalniz 12 firsat) ve
 * tek bir kacirilan kalemin butun seriyi sifirlamasi insanlari tamamen
 * birakmaya itiyor.
 *
 * ICINDE BULUNULAN AY SERIYI ASLA BOZMAZ. Ay bitmeden "yapilmadi" demek
 * yanlis olur; esik asilmissa seriye eklenir, asilmamissa atlanir.
 */
enum class StreakCell {
    /** Ilk plandan onceki ay - seri henuz baslamamisti. */
    BeforeStart,
    NoPlan,
    Missed,

    /** Esik asildi, tamami degil. */
    Partial,
    Full,

    /** Bu ay, esik henuz asilmadi. */
    InProgress,
}

data class PlanStreak(
    val current: Int,
    val longest: Int,
    /** Eskiden yeniye, son ay bugunun ayi. */
    val grid: List<Pair<YearMonth, StreakCell>>,
    /** Izgaradaki duzenli ay sayisi - "Son 12 ayda 9/12". */
    val regularInWindow: Int,
)

/** Hic plan yapilmamissa null - gosterilecek bir seri yok. */
fun planStreak(
    items: List<PlanItem>,
    transactions: List<Transaction>,
    positions: List<Position>,
    today: KefeDate,
    gridMonths: Int = 12,
): PlanStreak? {
    val current = YearMonth.of(today)
    val first = items.filter { it.month <= current }.minOfOrNull { it.month } ?: return null

    // Islemler aylara bir kez bolunur; her ay icin butun defteri taramayalim.
    val keyOf = positions.associate { it.id to it.assetKey() }
    val txByMonth = transactions.groupBy { YearMonth.of(it.date) }
    val itemsByMonth = items.groupBy { it.month }

    val verdicts = LinkedHashMap<YearMonth, MonthVerdict>()
    var m = first
    while (m <= current) {
        val monthItems = itemsByMonth[m].orEmpty()
        verdicts[m] = if (monthItems.isEmpty()) {
            MonthVerdict.NoPlan
        } else {
            val txs = txByMonth[m].orEmpty().filter { keyOf[it.positionId] != null }
            monthPlanProgressOf(m, monthItems, assetFlowsIn(m, txs, positions), positions, today).verdict
        }
        m = m.next()
    }

    fun counts(month: YearMonth) = verdicts[month].let { it == MonthVerdict.Full || it == MonthVerdict.Partial }

    var streak = if (counts(current)) 1 else 0
    var back = current.previous()
    while (back >= first && counts(back)) {
        streak++
        back = back.previous()
    }

    var longest = 0
    var run = 0
    verdicts.keys.forEach { month ->
        if (counts(month)) {
            run++
            longest = maxOf(longest, run)
        } else if (month != current) {
            run = 0
        }
    }

    val grid = (gridMonths - 1 downTo 0).map { back ->
        val month = current - back
        val cell = when {
            month < first -> StreakCell.BeforeStart
            else -> when (verdicts[month]) {
                MonthVerdict.Full -> StreakCell.Full
                MonthVerdict.Partial -> StreakCell.Partial
                MonthVerdict.InProgress -> StreakCell.InProgress
                MonthVerdict.Missed -> StreakCell.Missed
                MonthVerdict.NoPlan, MonthVerdict.Future, null ->
                    if (month == current) StreakCell.InProgress else StreakCell.NoPlan
            }
        }
        month to cell
    }

    return PlanStreak(
        current = streak,
        longest = longest,
        grid = grid,
        regularInWindow = grid.count { it.second == StreakCell.Full || it.second == StreakCell.Partial },
    )
}
