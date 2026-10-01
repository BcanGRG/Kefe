package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.isAnchored
import com.kefe.app.domain.model.monthOrdinal
import com.kefe.app.domain.model.requiredMonthly
import com.kefe.app.ui.format.Money

/**
 * "Tarihe yetismek icin ayda ne kadar" - hedef karti ve detayi icin.
 *
 * Hesap [requiredMonthly]'de: (hedef - hedefe ayrilan birikim) / kalan ay. Birikim
 * her acilista yeniden olculur: planladigindan fazla biriktiren kullanicinin
 * gereken tutari kendiliginden duser, eksik biriktirenin artar.
 */
data class RequiredMonthly(
    /** Ayda gereken TL; kura bagli hedefte bugunku kurla. */
    val tl: Double,
    /** Hedefin birimi; TL hedefte [GoalUnit.Try]. */
    val unit: GoalUnit,
    /** Bir TL'nin hedefin birimindeki karsiligi; TL hedefte null. */
    val unitPerTl: Double?,
    /** Kalan ay - hesaptaki bolen. */
    val months: Int,
    /** Aylik katki (TL); girilmemisse null. */
    val contribution: Double?,
) {
    /** Katki gerekeni karsilamiyorsa ayda eksik kalan TL; katki yoksa null. */
    val shortfall: Double? get() = contribution?.let { (tl - it).takeIf { gap -> gap > Tolerance } ?: 0.0 }

    /** "€257,45 · ₺14.231,10" ya da TL hedefte "₺14.231,10". */
    fun amountText(): String = text(tl, withTl = true)

    /** Hedefin biriminde (kura bagli hedefte yalniz birim, TL hedefte TL). */
    fun text(valueTl: Double, withTl: Boolean = false): String {
        val units = unitPerTl?.let { valueTl * it } ?: return Money.tlExact(valueTl)
        val unitText = when (unit) {
            GoalUnit.Eur -> Money.foreign(units, "EUR", decimals = Money.decimals(units, max = 2))
            GoalUnit.Usd -> Money.foreign(units, "USD", decimals = Money.decimals(units, max = 2))
            GoalUnit.GoldGram -> Money.quantity(units, "gr altın", Money.decimals(units, max = 2))
            GoalUnit.Try -> return Money.tlExact(valueTl)
        }
        return if (withTl) "$unitText · ${Money.tlExact(valueTl)}" else unitText
    }

    private companion object {
        /** Kurus alti fark "eksik" sayilmaz. */
        const val Tolerance = 0.005
    }
}

/**
 * Tarihine yetismek icin ayda gereken. Ulasilmis, kapatilmis ya da tarihi gecmis
 * hedefte null - orada "gereken" bir rakam anlamsiz.
 */
internal fun Goal.requiredMonthlyOf(wealth: Double, today: KefeDate): RequiredMonthly? {
    val tl = requiredMonthly(wealth, today)?.takeIf { it > 0.0 } ?: return null
    return RequiredMonthly(
        tl = tl,
        unit = unit,
        // Kura bagli hedefte tutar = birim x kur; oran kuru yeniden aramadan verir.
        unitPerTl = anchorAmount?.takeIf { isAnchored && amount > 0.0 }?.let { it / amount },
        // requiredMonthly ile AYNI ay sayimi (ay farki, en az 1).
        months = (targetDate.monthOrdinal() - today.monthOrdinal()).coerceAtLeast(1),
        contribution = monthlyContribution.takeIf { it > 0.0 },
    )
}

/** Kart satiri: "Tarihe yetişmek için ayda €257,45 · ₺14.231,10". */
internal fun RequiredMonthly.cardLine(): String = "Tarihe yetişmek için ayda ${amountText()}"

/**
 * Katkiyla kiyas: "Aylık katkın €250 · ayda €7,45 eksik" / "Aylık katkın €250 yetiyor".
 * Katki girilmemisse ne yapilacagini soyler.
 */
internal fun RequiredMonthly.contributionLine(): String {
    val contribution = contribution ?: return "Aylık katkı girilmedi. Bu tutarı katkı olarak girersen varış tarihi de hesaplanır."
    val gap = shortfall ?: 0.0
    return if (gap > 0.0) {
        "Aylık katkın ${text(contribution)} · ayda ${text(gap)} eksik"
    } else {
        "Aylık katkın ${text(contribution)} yetiyor"
    }
}
