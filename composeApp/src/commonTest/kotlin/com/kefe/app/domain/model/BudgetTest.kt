package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ayin para akisi: Gelir - Gider - Yatirim = Kalan.
 *
 * Yatirim Ozet'teki "Bu ay eklenen" ile AYNI tanimdir; iki ekran ayni ay icin
 * iki farkli rakam yazmamali.
 */
class BudgetTest {

    private val oct = YearMonth(2026, 10)
    private val gram = planPosition("pos_gold_gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24)

    private val book = MonthBook(
        month = oct,
        incomes = listOf(
            IncomeEntry("a", oct, "member_owner", IncomeKind.Salary, 80000.0),
            IncomeEntry("b", oct, "member_partner", IncomeKind.Salary, 60000.0),
            IncomeEntry("c", oct, "member_owner", IncomeKind.Extra, 5000.0),
        ),
        expenses = listOf(
            ExpenseEntry("e1", KefeDate(2026, 10, 3), ExpenseCategory.Housing, 30000.0),
            ExpenseEntry("e2", KefeDate(2026, 10, 9), ExpenseCategory.Groceries, 8000.0),
            ExpenseEntry("e3", KefeDate(2026, 10, 20), ExpenseCategory.Groceries, 4000.0),
        ),
        budgets = listOf(
            ExpenseBudget("b1", oct, ExpenseCategory.Housing, 30000.0),
            ExpenseBudget("b2", oct, ExpenseCategory.Groceries, 10000.0),
        ),
    )

    private val txs = listOf(
        planBuy(gram, 10.0, 6700.0, fee = 100.0, date = KefeDate(2026, 10, 5)),
        planSell(gram, 1.0, 6600.0, date = KefeDate(2026, 10, 6)),
        planBuy(gram, 3.0, 6000.0, date = KefeDate(2026, 9, 28)),
    )

    @Test
    fun paraAkisi() {
        val f = monthFlow(book, txs, plannedInvest = 70000.0)
        assertEquals(145000.0, f.income)
        assertEquals(85000.0, f.incomeByMember["member_owner"])
        assertEquals(42000.0, f.expenses)
        assertEquals(12000.0, f.expensesByCategory[ExpenseCategory.Groceries])
        assertEquals(40000.0, f.budgetTotal)
        // 67.100 alim - 6.600 satis; Eylul'un alimi sayilmaz.
        assertEquals(60500.0, f.investedNet, 1e-9)
        assertEquals(67100.0, f.grossBuys, 1e-9)
        assertEquals(6600.0, f.sells, 1e-9)
        assertEquals(145000.0 - 42000.0 - 60500.0, f.remaining!!, 1e-9)
        assertEquals(145000.0 - 40000.0 - 70000.0, f.plannedRemaining!!, 1e-9)
        assertEquals((145000.0 - 42000.0) / 145000.0, f.savingsRate!!, 1e-12)
    }

    /** Ozet'teki "Bu ay eklenen" ile ayni rakam. */
    @Test
    fun yatirimOzetleAyniTanim() {
        assertEquals(txs.netContributionIn(2026, 10), monthFlow(book, txs, null).investedNet)
    }

    /** Gelir girilmemisse oranlar "bilinmiyor" - sifir DEGIL. */
    @Test
    fun gelirYoksaOranlarBilinmiyor() {
        val f = monthFlow(MonthBook(oct), txs, null)
        assertNull(f.income)
        assertNull(f.remaining)
        assertNull(f.savingsRate)
        assertNull(f.budgetTotal)
    }

    @Test
    fun bilinmeyenKategoriDigerOlur() {
        assertEquals(ExpenseCategory.Other, ExpenseCategory.fromName("Pets"))
        assertEquals(IncomeKind.Extra, IncomeKind.fromName("Bonus"))
        assertEquals(ExpenseCategory.Groceries, ExpenseCategory.fromName("Groceries"))
    }

    @Test
    fun kimliklerIcerikten() {
        assertEquals("pi_2026_03_gold_gram", planItemId(YearMonth(2026, 3), "gold_gram"))
        assertEquals("inc_2026_10_member_owner_Salary", incomeId(oct, "member_owner", IncomeKind.Salary))
        assertEquals("eb_2026_10_Groceries", budgetId(oct, ExpenseCategory.Groceries))
    }
}
