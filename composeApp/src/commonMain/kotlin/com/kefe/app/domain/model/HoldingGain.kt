package com.kefe.app.domain.model

import kotlin.math.abs

/**
 * Bir varligin bir donemdeki KAZANCI - fiyatin hareketi degil, kullanicinin parasi.
 *
 * NEYDI: donem degisimi "fiyat yuzdesi x bugunku deger" idi. Sali gunu alinan
 * altin, pazartesinin yukselisini de kullanicinin kazanci diye yaziyordu; ay
 * icinde alinan 10 gr'in ay basindan beri olan butun hareketi "bu ay kazandin"
 * gorunuyordu.
 *
 * Simdi donem basinda ELDE OLAN miktar donem basi fiyatiyla, donem icinde
 * alinip satilan ise ISLEM TUTARIYLA sayilir:
 *
 *   kazanc = bugunku deger - donem basi degeri - (donem ici alimlar - satislar)
 *   yuzde  = kazanc / (donem basi degeri + donem ici alimlar)
 *
 * Donem basi fiyati varligin donem yuzdesinden geri cozulur (bugunku fiyat /
 * (1 + yuzde)); yani gunluk ve haftalik yuzdenin butun kurallari (kotasyon gunu,
 * tolerans, elle fiyat) aynen gecerli. Donem basinda elde hic yoksa fiyat
 * yuzdesine gerek kalmaz: kazanc dogrudan "bugunku deger - odenen"dir.
 *
 * Deger SATIS (alis kotasyonu) fiyatiyla olculdugu icin bugun alinan bir
 * varligin kazanci kuyumcu makasi kadar eksidir - "bugun satsam" sorusunun
 * dogru cevabi.
 */
data class HoldingGain(
    val amount: Double,
    /** Donem basi degeri + donem ici alimlar: yuzdenin paydasi. */
    val base: Double,
) {
    fun toPeriodTotal(): PeriodTotal? =
        if (base > 0.0) PeriodTotal(amount = amount, percent = amount / base * 100.0) else null
}

/**
 * [since] gununden SONRAKI donemin kazanci: donem ici islemler tarihi [since]'ten
 * sonra olanlardir (gunlukte [since] dun, yani yalniz bugun). [since], yuzdenin
 * olculdugu gun olmali - yaklasik olcude (bkz. periodChange) islem penceresi de
 * o gunden baslar. Bilinemiyorsa null: donem basinda elde varlik var ama donem
 * yuzdesi bilinmiyor.
 *
 * [transactions] bu pozisyonun islemleri olmali.
 */
fun Position.gainIn(
    percent: Double?,
    transactions: List<Transaction>,
    since: KefeDate,
): HoldingGain? {
    val sinceEpoch = since.toEpochDay()
    val inPeriod = transactions.filter { it.date.toEpochDay() > sinceEpoch }
    val bought = inPeriod.filter { it.side == TradeSide.Buy }
    val sold = inPeriod.filter { it.side == TradeSide.Sell }
    val boughtTl = bought.sumOf { it.quantity * it.unitPrice + it.fee }
    val soldTl = sold.sumOf { it.quantity * it.unitPrice - it.fee }
    val startQuantity = (quantity - bought.sumOf { it.quantity } + sold.sumOf { it.quantity })
        .let { if (abs(it) < QuantityTolerance) 0.0 else it.coerceAtLeast(0.0) }

    val startValue = if (startQuantity == 0.0) {
        0.0
    } else {
        val pct = percent ?: return null
        // Bkz. weightedPeriodTotal: -100'de payda sifirdir.
        val factor = 1.0 + pct / 100.0
        if (factor <= 0.0 || unitPrice <= 0.0) return null
        startQuantity * unitPrice / factor
    }
    if (!startValue.isFinite()) return null
    return HoldingGain(
        amount = value - startValue - (boughtTl - soldTl),
        base = startValue + boughtTl,
    )
}

/** Bilinen kazanclarin toplami; hicbiri bilinmiyorsa null. */
fun List<HoldingGain?>.total(): PeriodTotal? {
    val known = filterNotNull()
    if (known.isEmpty()) return null
    return HoldingGain(amount = known.sumOf { it.amount }, base = known.sumOf { it.base }).toPeriodTotal()
}

/** Donemin geriye bakilan gunu - gun, hafta, ay. Fiyat yuzdesinin pencereleriyle ayni. */
const val GainDayDays: Int = 1
const val GainWeekDays: Int = 7
const val GainMonthDays: Int = 30

/** Bugunden [days] gun once. */
fun KefeDate.minusDays(days: Int): KefeDate = kefeDateOfEpochDay(toEpochDay() - days)

/** Ondalik miktar artigi: 0,1 + 0,2 - 0,3 tam sifir cikmaz. */
private const val QuantityTolerance = 1e-9
