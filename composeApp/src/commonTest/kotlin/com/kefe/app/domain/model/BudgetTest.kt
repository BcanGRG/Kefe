package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // Aylik giderler 40.000 tam sayilir; market 2.000 asti, asim eklenir. Plan disi yok.
        assertEquals(42000.0, f.spentInPlan, 1e-9)
        assertEquals(2000.0, f.overPlan, 1e-9)
        assertEquals(0.0, f.unplannedSpent, 1e-9)
        assertEquals(42000.0, f.outgoing, 1e-9)
        assertEquals(145000.0 - 42000.0 - 60500.0, f.remaining!!, 1e-9)
        assertEquals(145000.0 - 40000.0 - 70000.0, f.plannedRemaining!!, 1e-9)
        assertEquals((145000.0 - 42000.0) / 145000.0, f.savingsRate!!, 1e-12)
    }

    /**
     * Aylik gider AYRILAN paradir: harcama girilmese de tam duser, kalemin icindeki
     * harcama ayrica dusmez. Aylik gideri olmayan kalemdeki harcama plan disidir.
     */
    @Test
    fun aylikGiderAyrilanParaHarcamaKalemindenYenir() {
        val halisaha = ExpenseCategory.custom("Halisaha")!!
        val f = monthFlow(
            MonthBook(
                month = oct,
                incomes = listOf(IncomeEntry("a", oct, "member_owner", IncomeKind.Salary, 170000.0)),
                expenses = listOf(
                    ExpenseEntry("e1", KefeDate(2026, 10, 2), ExpenseCategory.Groceries, 95.0),
                    ExpenseEntry("e2", KefeDate(2026, 10, 3), ExpenseCategory.Groceries, 280.0),
                    ExpenseEntry("e3", KefeDate(2026, 10, 4), halisaha, 370.0),
                ),
                budgets = listOf(
                    ExpenseBudget("b1", oct, ExpenseCategory.Housing, 25000.0),
                    ExpenseBudget("b2", oct, ExpenseCategory.Groceries, 7000.0),
                ),
            ),
            emptyList(),
            plannedInvest = null,
        )
        assertEquals(745.0, f.expenses, 1e-9)
        assertEquals(375.0, f.spentInPlan, 1e-9)
        assertEquals(0.0, f.overPlan, 1e-9)
        assertEquals(370.0, f.unplannedSpent, 1e-9)
        assertEquals(listOf(halisaha), f.unplannedCategories)
        assertEquals(32370.0, f.outgoing, 1e-9)
        assertEquals(170000.0 - 32370.0, f.remaining!!, 1e-9)
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

    // --- Ozel kalem -------------------------------------------------------------

    @Test
    fun ozelKalemAyniKolondaSaklanir() {
        val trip = ExpenseCategory.custom("  Düğün   hediyesi ")!!
        assertEquals("c:Düğün hediyesi", trip.name)
        assertEquals("Düğün hediyesi", trip.label())
        assertTrue(trip.isCustom)
        // Diskten geri: ayni kalem.
        assertEquals(trip, ExpenseCategory.fromName("c:Düğün hediyesi"))
        assertEquals("Düğün hediyesi", ExpenseCategory.fromName("c:Düğün hediyesi").label())
    }

    @Test
    fun ozelKalemHarfBuyuklugunaBakmaz() {
        val a = ExpenseCategory.custom("Tatil")!!
        val b = ExpenseCategory.custom("tatil")!!
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, mapOf(a to 1.0, b to 2.0).size)
        assertEquals("eb_2026_10_c_tatil", budgetId(oct, a))
        assertEquals(budgetId(oct, a), budgetId(oct, b))
        assertEquals("eb_2026_10_c_düğün-hediyesi", budgetId(oct, ExpenseCategory.custom("Düğün hediyesi")!!))
    }

    @Test
    fun hazirKategoriAdiYazilirsaHazirKategori() {
        assertEquals(ExpenseCategory.Groceries, ExpenseCategory.custom("market"))
        assertFalse(ExpenseCategory.custom("MARKET")!!.isCustom)
        assertNull(ExpenseCategory.custom("   "))
        assertEquals(ExpenseCategory.MaxCustomLength, ExpenseCategory.custom("x".repeat(50))!!.label().length)
    }

    @Test
    fun bilinmeyenMetinDigerSayilir() {
        assertEquals(ExpenseCategory.Other, ExpenseCategory.fromName("Pets"))
        assertEquals(ExpenseCategory.Other, ExpenseCategory.fromName("c:   "))
        assertEquals(ExpenseCategory.Groceries, ExpenseCategory.fromName("Groceries"))
        // Hazir kimlikler degismedi.
        assertEquals("eb_2026_10_Housing", budgetId(oct, ExpenseCategory.Housing))
    }
}

