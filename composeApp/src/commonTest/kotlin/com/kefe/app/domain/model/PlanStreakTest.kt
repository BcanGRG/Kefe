package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * "Kac aydir duzenli". Iki kural sabitleniyor: %80 esigi seriyi surdurur, ve
 * icinde bulunulan ay seriyi ASLA bozmaz - ay bitmeden "yapilmadi" denmez.
 */
class PlanStreakTest {

    private val gram = planPosition("pos_gold_gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24)
    private val positions = listOf(gram)

    private fun item(month: YearMonth, target: Double = 10.0) = PlanItem(
        planItemId(month, "gold_gram"), month, "gold_gram", "Gram Altın", PlanTargetMode.Quantity, target, unitPriceAtPlan = 100.0,
    )

    private fun buyIn(month: YearMonth, quantity: Double) = planBuy(gram, quantity, 100.0, date = month.firstDay())

    private val jul = YearMonth(2026, 7)
    private val aug = YearMonth(2026, 8)
    private val sep = YearMonth(2026, 9)
    private val oct = YearMonth(2026, 10)
    private val today = KefeDate(2026, 10, 5)

    @Test
    fun hicPlanYoksaSeriYok() {
        assertNull(planStreak(emptyList(), emptyList(), positions, today))
    }

    @Test
    fun ucTamAyUcAylikSeri() {
        val items = listOf(item(jul), item(aug), item(sep), item(oct))
        val txs = listOf(buyIn(jul, 10.0), buyIn(aug, 10.0), buyIn(sep, 10.0))
        val s = planStreak(items, txs, positions, today)!!
        // Ekim henuz esikte degil ama seriyi BOZMAZ.
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
        assertEquals(StreakCell.InProgress, s.grid.last().second)
    }

    /** %80-99 arasi ay sari ama seriye SAYILIR. */
    @Test
    fun yuzdeSeksenSeriyiSurdurur() {
        val items = listOf(item(jul), item(aug), item(sep))
        val txs = listOf(buyIn(jul, 10.0), buyIn(aug, 8.5), buyIn(sep, 10.0))
        val s = planStreak(items, txs, positions, today)!!
        assertEquals(3, s.current)
        assertEquals(StreakCell.Partial, s.grid.first { it.first == aug }.second)
    }

    @Test
    fun esikAltiAySeriyiKirar() {
        val items = listOf(item(jul), item(aug), item(sep))
        val txs = listOf(buyIn(jul, 10.0), buyIn(aug, 5.0), buyIn(sep, 10.0))
        val s = planStreak(items, txs, positions, today)!!
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertEquals(StreakCell.Missed, s.grid.first { it.first == aug }.second)
    }

    /** Plan yapilmayan gecmis ay da seriyi kirar. */
    @Test
    fun plansizAySeriyiKirar() {
        val items = listOf(item(jul), item(sep))
        val txs = listOf(buyIn(jul, 10.0), buyIn(sep, 10.0))
        val s = planStreak(items, txs, positions, today)!!
        assertEquals(1, s.current)
        assertEquals(StreakCell.NoPlan, s.grid.first { it.first == aug }.second)
    }

    /** Bu ay esigi astiysa hemen seriye eklenir. */
    @Test
    fun buAyEsigiAstiysaSayilir() {
        val items = listOf(item(sep), item(oct))
        val txs = listOf(buyIn(sep, 10.0), buyIn(oct, 9.0))
        val s = planStreak(items, txs, positions, today)!!
        assertEquals(2, s.current)
        assertEquals(StreakCell.Partial, s.grid.last().second)
    }

    @Test
    fun izgaraOnIkiAyVeIlkPlandanOncesi() {
        val s = planStreak(listOf(item(sep)), listOf(buyIn(sep, 10.0)), positions, today)!!
        assertEquals(12, s.grid.size)
        assertEquals(oct, s.grid.last().first)
        assertEquals(StreakCell.BeforeStart, s.grid.first().second)
        assertEquals(1, s.regularInWindow)
    }

    @Test
    fun enUzunSeriGecmisteKalabilir() {
        val may = YearMonth(2026, 5)
        val jun = YearMonth(2026, 6)
        val items = listOf(item(may), item(jun), item(jul), item(aug), item(sep))
        val txs = listOf(buyIn(may, 10.0), buyIn(jun, 10.0), buyIn(jul, 10.0), buyIn(sep, 10.0))
        val s = planStreak(items, txs, positions, today)!!
        assertEquals(1, s.current)
        assertEquals(3, s.longest)
    }
}
