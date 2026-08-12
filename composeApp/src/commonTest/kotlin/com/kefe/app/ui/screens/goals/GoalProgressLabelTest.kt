package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.progress
import com.kefe.app.ui.format.Money
import kotlin.math.min
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hedef kartindaki yuzde ULASILMADAN %100 yazamaz.
 *
 * Yaziyordu: yuzde sifir ondaliga yuvarlaniyor ve %99,6 ilerleme "%100"
 * gorunuyordu - ama renk hala "ulasilmadi" diyor, cubuk da dolmamis
 * gorunuyordu. Ekran kendi kendisiyle celisiyordu.
 *
 * Kart yuzdeyi `reached` bayragindan bagimsiz yaziyor, o yuzden kirpma
 * bicimlendirmede yapilir: ulasilmamis ilerleme en cok %99'dur.
 */
class GoalProgressLabelTest {

    private fun goal(amount: Double) = Goal(
        id = "g",
        name = "Ev",
        iconKey = "ev",
        amount = amount,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2028, 12, 1),
        monthlyContribution = 1_000.0,
    )

    /** Kartin yazdigi metin - ekrandaki ifadenin aynisi. */
    private fun label(wealth: Double, amount: Double): String {
        val progress = goal(amount).progress(wealth)
        val reached = progress >= 1f
        return if (reached) {
            Money.ratio(100.0)
        } else {
            Money.ratio(min(round(progress.toDouble() * 100.0), 99.0))
        }
    }

    @Test
    fun ULASILMADAN100Yazilmaz() {
        // 996.000 / 1.000.000 = %99,6 -> sifir ondalikta "%100" cikiyordu.
        assertEquals("%99", label(wealth = 996_000.0, amount = 1_000_000.0))
        assertEquals("%99", label(wealth = 999_999.0, amount = 1_000_000.0))
    }

    @Test
    fun ULASILINCA100Yazilir() {
        assertEquals("%100", label(wealth = 1_000_000.0, amount = 1_000_000.0))
    }

    /** Hedef asilsa da %100'de durur - %200 hata gibi okunur. */
    @Test
    fun ASILINCADA100dur() {
        assertEquals("%100", label(wealth = 2_000_000.0, amount = 1_000_000.0))
    }

    @Test
    fun siradanIlerlemeAYNIKalir() {
        assertEquals("%35", label(wealth = 350_000.0, amount = 1_000_000.0))
        assertEquals("%0", label(wealth = 0.0, amount = 1_000_000.0))
    }

    /** Yazi ile renk ARTIK CELISMEZ: %100 yalnizca ulasildiginda cikar. */
    @Test
    fun yuzdeIleReachedTUTARLI() {
        for (wealth in listOf(0.0, 1.0, 500_000.0, 996_000.0, 999_999.0, 1_000_000.0)) {
            val reached = goal(1_000_000.0).progress(wealth) >= 1f
            val yuzYazdi = label(wealth, 1_000_000.0) == "%100"
            assertTrue(yuzYazdi == reached, "celiski: $wealth -> ${label(wealth, 1_000_000.0)}")
        }
    }
}
