package com.kefe.app.ui.screens.plan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.SqlDelightPlanRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.testing.TestMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Harcamalar sayfasinin VM'i GERCEK veritabaniyla: kaleme suzulu acilir, cip ve
 * siralama yerinde degisir, kaydedilen harcama defter akisindan kendiliginden gelir.
 */
class PlanExpensesViewModelTest {

    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    private val october = YearMonth(2026, 10)

    @Test
    fun `kaleme suzulu acilir, cip ve siralama degisir, yeni harcama kendiliginden gelir`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver).also { it.bootstrapIfNeeded() }
        val clock = FixedKefeClock(KefeDate(2026, 10, 7), millis = 1_000L)
        val plan = SqlDelightPlanRepository(database, clock)
        plan.setBudgets(october, mapOf(ExpenseCategory.Groceries to 7_000.0))
        plan.upsertExpense(ExpenseEntry("a", KefeDate(2026, 10, 5), ExpenseCategory.Groceries, 100.0, note = "Bim"))
        plan.upsertExpense(ExpenseEntry("b", KefeDate(2026, 10, 6), ExpenseCategory.Bills, 565.0, note = "Elektrik"))

        val vm = main.track(PlanExpensesViewModel(plan, clock, october, ExpenseFilter.Category(ExpenseCategory.Groceries)))
        val opened = realTime { vm.state.first { it.page != null } }.page!!
        assertEquals("Market", opened.title)
        assertIs<ExpensesSummaryUi.Budgeted>(opened.summary)
        assertEquals(listOf("Bim"), opened.groups.flatMap { it.items }.map { it.title })

        vm.onIntent(PlanExpensesIntent.SelectFilter(ExpenseFilter.All))
        assertEquals(listOf("Elektrik", "Bim"), vm.state.value.page!!.groups.flatMap { it.items }.map { it.title })

        vm.onIntent(PlanExpensesIntent.SelectSort(ExpenseSort.Amount))
        assertEquals(listOf("Elektrik", "Bim"), vm.state.value.page!!.ranked.map { it.title })

        // Kabuktaki formdan kaydedilen harcama: sayfa yenilenmeden gelir.
        plan.upsertExpense(ExpenseEntry("c", KefeDate(2026, 10, 7), ExpenseCategory.Groceries, 2_000.0, note = "Gimat"))
        val after = realTime { vm.state.first { it.page?.countLabel == "3 harcama" } }.page!!
        assertEquals("Gimat", after.ranked.first().title)
    }
}

/** Depo akislari gercek is parcaciklarinda; runTest'in sanal saati onlari beklemez. */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
