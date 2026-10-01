package com.kefe.app.ui.screens.goals

import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** "Tarihe yetişmek için ayda": kalan / kalan ay, hedefin biriminde ve katkiyla kiyasli. */
class GoalRequiredTest {

    private val today = KefeDate(2026, 10, 1)

    /** €1.800, kur 55 -> ₺99.000; Nisan 2027'ye 6 ay; aylik €250 (₺13.750). */
    private val trip = Goal(
        id = "g_trip",
        name = "Amsterdam Gezi",
        iconKey = "ucak",
        amount = 99_000.0,
        unit = GoalUnit.Eur,
        targetDate = KefeDate(2027, 4, 1),
        monthlyContribution = 13_750.0,
        anchorAmount = 1_800.0,
        contributionAnchor = 250.0,
    )

    @Test
    fun euroHedefindeGerekenEuroVeTlYazilir() {
        // 100 € ayrilmis (₺5.500): (99.000 - 5.500) / 6 = ₺15.583,33 = €283,33.
        val required = assertNotNull(trip.requiredMonthlyOf(wealth = 5_500.0, today = today))
        assertEquals(6, required.months)
        assertEquals(15_583.333, required.tl, 1e-3)
        assertEquals("€283,33 · ₺15.583,33", required.amountText())
        assertEquals("Tarihe yetişmek için ayda €283,33 · ₺15.583,33", required.cardLine())
        assertEquals("Aylık katkın €250 · ayda €33,33 eksik", required.contributionLine())
    }

    @Test
    fun fazlaBiriktirinceGerekenDuser() {
        val planned = assertNotNull(trip.requiredMonthlyOf(wealth = 5_500.0, today = today))
        // Ayda 100 yerine 150 € ayrildi: kalan azalir, gereken duser.
        val more = assertNotNull(trip.requiredMonthlyOf(wealth = 8_250.0, today = today))
        assertEquals(15_125.0, more.tl, 1e-6)
        assertEquals("€275 · ₺15.125", more.amountText())
        assert(more.tl < planned.tl)
    }

    @Test
    fun katkiYetiyorsaOyleDer() {
        val required = assertNotNull(trip.copy(monthlyContribution = 16_500.0, contributionAnchor = 300.0)
            .requiredMonthlyOf(wealth = 5_500.0, today = today))
        assertEquals(0.0, required.shortfall)
        assertEquals("Aylık katkın €300 yetiyor", required.contributionLine())
    }

    @Test
    fun tlHedefindeYalnizTlVeKatkisizHedefNeYapacaginiSoyler() {
        val goal = trip.copy(unit = GoalUnit.Try, anchorAmount = null, contributionAnchor = null, monthlyContribution = 0.0)
        val required = assertNotNull(goal.requiredMonthlyOf(wealth = 9_000.0, today = today))
        assertEquals("₺15.000", required.amountText())
        assertNull(required.shortfall)
        assertEquals(
            "Aylık katkı girilmedi. Bu tutarı katkı olarak girersen varış tarihi de hesaplanır.",
            required.contributionLine(),
        )
    }

    @Test
    fun ulasilmisKapanmisYaDaTarihiGecmisHedefteYok() {
        assertNull(trip.requiredMonthlyOf(wealth = 99_000.0, today = today))
        assertNull(trip.copy(status = GoalStatus.Completed).requiredMonthlyOf(wealth = 0.0, today = today))
        assertNull(trip.requiredMonthlyOf(wealth = 0.0, today = KefeDate(2027, 5, 1)))
    }
}
