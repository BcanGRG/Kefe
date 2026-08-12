package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Goal.unit] bir GIRIS KOLAYLIGIDIR, canli bir capa degil.
 *
 * Tutar giris aninda o gunun kuruyla TL'ye cevrilip oyle kaydediliyor; hicbir
 * hesap birimi okumuyor. Ekran bir zamanlar bunun aksini vaat ediyordu
 * ("hedefi altin veya dolar cinsinden sabitlerseniz hedef de piyasayla birlikte
 * guncellenir") - metin duzeltildi.
 *
 * Bu test o karari YAZILI hale getiriyor: birim hesabi etkilemiyor. Biri
 * hedefi gercekten altina capalamak isterse bu test duser ve capanin
 * eklenmesi gerektigini soyler - sessizce yarim kalmaz.
 */
class GoalUnitTest {

    private fun goal(unit: GoalUnit) = Goal(
        id = "goal-1",
        name = "Ev",
        iconKey = "ev",
        // TL. Gram/dolar secili olsa da burada TL durur.
        amount = 2_000_000.0,
        unit = unit,
        targetDate = KefeDate(2028, 12, 1),
        monthlyContribution = 10_000.0,
    )

    /** Ayni tutar, farkli birim: ilerleme BIREBIR ayni. */
    @Test
    fun birimILERLEMEYIEtkilemez() {
        val wealth = 500_000.0
        val tl = goal(GoalUnit.Try).progress(wealth)
        val gram = goal(GoalUnit.GoldGram).progress(wealth)
        val usd = goal(GoalUnit.Usd).progress(wealth)

        assertEquals(0.25f, tl)
        assertEquals(tl, gram, "gram altin hedefi TL hedefinden farkli davraniyor")
        assertEquals(tl, usd, "dolar hedefi TL hedefinden farkli davraniyor")
    }

    /** Kilometre taslari da tutardan turer, birimden degil. */
    @Test
    fun birimKILOMETRETASLARINIEtkilemez() {
        val bugun = KefeDate(2026, 8, 11)
        val tl = goalMilestones(goal(GoalUnit.Try), 0.0, emptyList(), bugun)
        val gram = goalMilestones(goal(GoalUnit.GoldGram), 0.0, emptyList(), bugun)

        assertEquals(tl.map { it.amount }, gram.map { it.amount })
        assertEquals(listOf(500_000.0, 1_000_000.0, 1_500_000.0, 2_000_000.0), tl.map { it.amount })
    }

    /** Projeksiyon da oyle: varis ayi yalniz tutar ve aylik katkiyla belirlenir. */
    @Test
    fun birimPROJEKSIYONUEtkilemez() {
        val bugun = KefeDate(2026, 8, 11)
        val tl = goalProjection(goal(GoalUnit.Try), 1_900_000.0, bugun)
        val gram = goalProjection(goal(GoalUnit.GoldGram), 1_900_000.0, bugun)

        assertEquals(tl.arrival, gram.arrival)
        assertEquals(tl.forecast, gram.forecast)
    }
}
