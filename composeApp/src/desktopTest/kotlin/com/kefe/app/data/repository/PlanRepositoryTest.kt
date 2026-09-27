package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Plan/butce tablolari.
 *
 * Asil risk IKI TELEFON: ayni ayin ayni satirini iki cihaz ayri ayri yazarsa
 * tek satirda bulusmali - UUID ile iki satir olur ve toplamlar ikiye katlanirdi.
 * Kimlikler icerikten turuyor; burada bunun tabloda da tek satir oldugu ve
 * silmenin bir YAZMA (mezar tasi) oldugu sabitleniyor.
 */
class PlanRepositoryTest {

    private val oct = YearMonth(2026, 10)

    private class Env(val db: KefeDatabase, val repo: SqlDelightPlanRepository, val portfolio: SqlDelightPortfolioRepository)

    private fun env(): Env {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties().apply { setProperty("foreign_keys", "true") },
        )
        KefeDatabase.Schema.create(driver)
        val db = createKefeDatabase(driver)
        val clock = FixedKefeClock(millis = 5_000L)
        return Env(db, SqlDelightPlanRepository(db, clock), SqlDelightPortfolioRepository(db, clock, NoPrices()))
    }

    private fun gram(target: Double) = PlanItem(
        id = planItemId(oct, "gold_gram"),
        month = oct,
        assetKey = "gold_gram",
        assetName = "Gram Altın",
        mode = PlanTargetMode.Quantity,
        target = target,
        goalId = "goal_ev",
        unitPriceAtPlan = 6700.0,
    )

    @Test
    fun ayniAyAyniVarlikTEKSatir() = runTest {
        val e = env()
        e.repo.upsertPlanItem(gram(10.0))
        e.repo.upsertPlanItem(gram(12.0))
        val items = e.repo.observePlanItems().first()
        assertEquals(1, items.size)
        assertEquals(12.0, items.single().target)
        assertEquals("goal_ev", items.single().goalId)
    }

    @Test
    fun silmeMezarTasiVeYenidenEklemeDiriltir() = runTest {
        val e = env()
        e.repo.upsertPlanItem(gram(10.0))
        e.repo.deletePlanItem(gram(10.0).id)
        assertTrue(e.repo.observePlanItems().first().isEmpty())
        // Mezar tasi esitlemeye gider.
        val tomb = e.db.planItemQueries.selectPlanItemsChangedSince(0).executeAsList().single()
        assertEquals(5_000L, tomb.deletedAt)

        e.repo.upsertPlanItem(gram(8.0))
        assertEquals(8.0, e.repo.observePlanItems().first().single().target)
    }

    @Test
    fun varlikDegisinceEskiSatirSilinir() = runTest {
        val e = env()
        e.repo.upsertPlanItem(gram(10.0))
        val silver = gram(5.0).copy(id = planItemId(oct, "silver_gram"), assetKey = "silver_gram")
        e.repo.replacePlanItem(gram(10.0).id, silver)
        assertEquals(listOf("silver_gram"), e.repo.observePlanItems().first().map { it.assetKey })
    }

    @Test
    fun gelirKisiBazliVeSifirSiler() = runTest {
        val e = env()
        e.repo.setIncome(oct, "member_owner", IncomeKind.Salary, 80_000.0)
        e.repo.setIncome(oct, "member_owner", IncomeKind.Salary, 85_000.0)
        e.repo.setIncome(oct, "member_partner", IncomeKind.Salary, 60_000.0)
        var book = e.repo.observeMonthBook(oct).first()
        assertEquals(2, book.incomes.size)
        assertEquals(145_000.0, book.incomes.sumOf { it.amount })

        e.repo.setIncome(oct, "member_partner", IncomeKind.Salary, null)
        book = e.repo.observeMonthBook(oct).first()
        assertEquals(listOf("member_owner"), book.incomes.map { it.memberId })
    }

    @Test
    fun butceSifirKategoriyiCikarir() = runTest {
        val e = env()
        e.repo.setBudgets(oct, mapOf(ExpenseCategory.Groceries to 10_000.0, ExpenseCategory.Housing to 30_000.0))
        e.repo.setBudgets(oct, mapOf(ExpenseCategory.Groceries to 0.0))
        val book = e.repo.observeMonthBook(oct).first()
        assertEquals(listOf(ExpenseCategory.Housing), book.budgets.map { it.category })
    }

    @Test
    fun ozelKalemHarcamasiVeButcesiGidipGelir() = runTest {
        val e = env()
        val trip = ExpenseCategory.custom("Tatil")!!
        e.repo.upsertExpense(ExpenseEntry("x_tatil", KefeDate(2026, 10, 12), trip, 12_000.0))
        e.repo.setBudgets(oct, mapOf(trip to 15_000.0))
        val book = e.repo.observeMonthBook(oct).first()
        assertEquals("Tatil", book.expenses.single().category.label())
        assertTrue(book.expenses.single().category.isCustom)
        assertEquals(listOf("eb_2026_10_c_tatil"), book.budgets.map { it.id })
        assertEquals(trip, book.budgets.single().category)

        // Farkli yazimla ayni kalem: ikinci bir butce satiri acilmaz, ayni satir silinir.
        e.repo.setBudgets(oct, mapOf(ExpenseCategory.custom("tatil")!! to null))
        assertTrue(e.repo.observeMonthBook(oct).first().budgets.isEmpty())
    }

    @Test
    fun harcamaAyaTarihindenDuser() = runTest {
        val e = env()
        e.repo.upsertExpense(ExpenseEntry("x1", KefeDate(2026, 10, 31), ExpenseCategory.Groceries, 500.0))
        e.repo.upsertExpense(ExpenseEntry("x2", KefeDate(2026, 11, 1), ExpenseCategory.Groceries, 700.0))
        assertEquals(listOf(500.0), e.repo.observeMonthBook(oct).first().expenses.map { it.amount })
        e.repo.deleteExpense("x1")
        assertTrue(e.repo.observeMonthBook(oct).first().expenses.isEmpty())
    }

    /** Daha yeni bir surumden gelen kategori bu telefonu cokertmez; "Diğer" sayilir. */
    @Test
    fun bilinmeyenKategoriCOKERTMEZ() = runTest {
        val e = env()
        e.db.expenseQueries.applyExpensePull(
            id = "y", dateYear = 2026, dateMonth = 10, dateDay = 5, category = "Pets", amount = 90.0,
            note = null, addedByMemberId = null, createdAt = 1, updatedAt = 1, deletedAt = null,
        )
        e.db.planItemQueries.applyPlanItemPull(
            id = "z", periodYear = 2026, periodMonth = 10, assetKey = "gold_gram", assetName = "x",
            mode = "Percent", target = 1.0, goalId = null, unitPriceAtPlan = null, updatedAt = 1, deletedAt = null,
        )
        assertEquals(ExpenseCategory.Other, e.repo.observeMonthBook(oct).first().expenses.single().category)
        // Birimi bilinmeyen satir hesaplara girmez ama tabloda kalir.
        assertTrue(e.repo.observePlanItems().first().isEmpty())
        assertEquals(1, e.db.planItemQueries.selectPlanItems().executeAsList().size)
    }

    @Test
    fun yedekGidisDonus() = runTest {
        val e = env()
        e.repo.upsertPlanItem(gram(10.0))
        e.repo.setIncome(oct, "member_owner", IncomeKind.Salary, 80_000.0)
        e.repo.upsertExpense(ExpenseEntry("x1", KefeDate(2026, 10, 3), ExpenseCategory.Housing, 30_000.0, note = "kira"))
        e.repo.setBudgets(oct, mapOf(ExpenseCategory.Housing to 30_000.0))
        val backup = e.portfolio.exportBackup("2026-10-05")
        assertEquals(1, backup.planItems.size)
        assertEquals(1, backup.incomes.size)
        assertEquals(1, backup.expenses.size)
        assertEquals(1, backup.budgets.size)

        val other = env()
        other.repo.upsertExpense(ExpenseEntry("eski", KefeDate(2026, 9, 3), ExpenseCategory.Other, 1.0))
        other.portfolio.restoreBackup(backup)
        assertEquals(gram(10.0), other.repo.observePlanItems().first().single())
        val book = other.repo.observeMonthBook(oct).first()
        assertEquals(80_000.0, book.incomes.single().amount)
        assertEquals("kira", book.expenses.single().note)
        assertEquals(30_000.0, book.budgets.single().amount)
        // Geri yukleme oncekini SILER.
        assertTrue(other.repo.observeMonthBook(YearMonth(2026, 9)).first().expenses.isEmpty())
    }

    @Test
    fun herSeyiSilPlaniDaSiler() = runTest {
        val e = env()
        e.repo.upsertPlanItem(gram(10.0))
        e.repo.setIncome(oct, "member_owner", IncomeKind.Salary, 80_000.0)
        e.portfolio.deleteAllData()
        assertTrue(e.repo.observePlanItems().first().isEmpty())
        assertTrue(e.repo.observeMonthBook(oct).first().isEmpty)
    }
}
