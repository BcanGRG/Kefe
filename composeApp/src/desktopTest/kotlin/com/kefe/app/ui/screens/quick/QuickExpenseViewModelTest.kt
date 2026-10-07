package com.kefe.app.ui.screens.quick

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPlanRepository
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.testing.TestMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Hizli giris penceresinin VM'i GERCEK veritabaniyla: widget'taki kalemle acilir,
 * tus takimi ve not onerisiyle kaydeder, "Geri al" kaydi siler, kayittan sonra
 * pencere kendiliginden kapanir.
 */
class QuickExpenseViewModelTest {

    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    private val october = YearMonth(2026, 10)
    private val clock = FixedKefeClock(KefeDate(2026, 10, 7), millis = 1_000L)

    @Test
    fun `kalemle acilir, oneriyle doldurur, dune kaydeder, geri al siler`() = runTest {
        val (plan, vm) = setUpVm(ExpenseCategory.Groceries)

        val ready = realTime { vm.state.first { !it.loading } }
        assertEquals(ExpenseCategory.Groceries, ready.selected)
        // Sira widget'takiyle ayni: en cok girilen once (ikisi de birer kez; en son girilen once).
        assertEquals(listOf("Faturalar", "Market"), ready.categories.take(2).map { it.label })
        assertEquals(listOf("Bim"), ready.suggestions.map { it.note })
        assertEquals("Market · kalan ₺6.900", ready.line?.text)
        assertEquals("Tutar girin", ready.saveText)

        vm.onIntent(QuickExpenseIntent.PickSuggestion(ready.suggestions.single()))
        vm.onIntent(QuickExpenseIntent.Key(QuickKey.Digit('5')))
        vm.onIntent(QuickExpenseIntent.Key(QuickKey.Back))
        assertEquals("₺100", vm.state.value.shownAmount)
        assertEquals("Kaydet · ₺100", vm.state.value.saveText)
        assertEquals("Kalan ₺6.900 → ₺6.800", vm.state.value.line?.text)

        vm.onIntent(QuickExpenseIntent.SelectDay(QuickDay.Yesterday))
        vm.onIntent(QuickExpenseIntent.Save)
        val saved = realTime { vm.state.first { it.saved != null } }.saved!!
        assertEquals("₺100 eklendi", saved.title)
        assertEquals("Market · Bim · dün", saved.sub)
        assertEquals("Kalan ₺6.800", saved.line?.text)

        val stored = realTime { plan.observeMonthBook(october).first { it.expenses.size == 3 } }
            .expenses.single { it.id == saved.id }
        assertEquals(KefeDate(2026, 10, 6), stored.date)
        assertEquals(100.0, stored.amount)
        assertEquals("Bim", stored.note)
        assertEquals("m1", stored.addedByMemberId)

        // Geri al: kayit gider, form degerleriyle geri gelir.
        vm.onIntent(QuickExpenseIntent.Undo)
        realTime { plan.observeMonthBook(october).first { it.expenses.size == 2 } }
        assertNull(vm.state.value.saved)
        assertEquals("100", vm.state.value.amountText)
        assertEquals("Bim", vm.state.value.note)
    }

    @Test
    fun `kalemsiz acilinca en cok girilen secili, kayittan sonra pencere kapanir`() = runTest {
        val (_, vm) = setUpVm(null, autoCloseMillis = 10)
        val ready = realTime { vm.state.first { !it.loading } }
        assertEquals(ExpenseCategory.Bills, ready.selected)

        // Tutarsiz kayit olmaz.
        vm.onIntent(QuickExpenseIntent.Save)
        assertNull(vm.state.value.saved)

        "250".forEach { vm.onIntent(QuickExpenseIntent.Key(QuickKey.Digit(it))) }
        vm.onIntent(QuickExpenseIntent.Save)
        assertEquals(QuickExpenseEffect.Close, realTime { vm.effects.first() })
    }

    private suspend fun setUpVm(category: ExpenseCategory?, autoCloseMillis: Long = 60_000): Pair<SqlDelightPlanRepository, QuickExpenseViewModel> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver).also { it.bootstrapIfNeeded() }
        val plan = SqlDelightPlanRepository(database, clock)
        val prefs = SqlDelightPreferencesRepository(database)
        prefs.put(PreferenceKeys.ActiveMemberId, "m1")
        plan.setBudgets(october, mapOf(ExpenseCategory.Groceries to 7_000.0))
        plan.upsertExpense(ExpenseEntry("a", KefeDate(2026, 10, 5), ExpenseCategory.Groceries, 100.0, note = "Bim", createdAt = 10L))
        plan.upsertExpense(ExpenseEntry("b", KefeDate(2026, 10, 6), ExpenseCategory.Bills, 565.0, note = "Elektrik", createdAt = 20L))
        val vm = main.track(
            QuickExpenseViewModel(
                planRepository = plan,
                portfolioRepository = SqlDelightPortfolioRepository(database, clock, NoPrices()),
                preferences = prefs,
                clock = clock,
                initialCategory = category,
                autoCloseMillis = autoCloseMillis,
            ),
        )
        return plan to vm
    }
}

/** Depo akislari gercek is parcaciklarinda; runTest'in sanal saati onlari beklemez. */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
