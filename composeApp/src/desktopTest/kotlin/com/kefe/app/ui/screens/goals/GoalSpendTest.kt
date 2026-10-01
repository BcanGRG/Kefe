package com.kefe.app.ui.screens.goals

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPlanRepository
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * "Hedeften harca" GERCEK veritabaniyla: satis, harcama, atamanin dusmesi ve
 * hedefin acik kalmasi ya da kapanmasi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalSpendTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val october = YearMonth(2026, 10)

    private class Env {
        val database: KefeDatabase
        val clock = FixedKefeClock(KefeDate(2026, 10, 22), millis = 5_000L)
        val portfolio: SqlDelightPortfolioRepository
        val plan: SqlDelightPlanRepository
        val prefs: SqlDelightPreferencesRepository

        init {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            KefeDatabase.Schema.create(driver)
            database = createKefeDatabase(driver)
            database.bootstrapIfNeeded()
            portfolio = SqlDelightPortfolioRepository(database, clock, NoPrices())
            plan = SqlDelightPlanRepository(database, clock)
            prefs = SqlDelightPreferencesRepository(database)
        }

        /** 450 € (55'ten), 300'u tatile ayrilmis. */
        suspend fun seed() {
            portfolio.upsertPosition(
                Position(
                    id = "pos_eur_try", name = "Euro", assetClass = AssetClass.Fx, quantity = 0.0,
                    unit = QuantityUnit.Currency, unitPrice = 55.0, value = 0.0, cost = 0.0,
                ),
            )
            portfolio.addTransaction(
                Transaction(
                    id = "tx_eur", positionId = "pos_eur_try", date = KefeDate(2026, 9, 1), side = TradeSide.Buy,
                    quantity = 450.0, unitPrice = 55.0, addedByMemberId = "member_owner",
                ),
            )
            portfolio.upsertGoal(
                Goal(
                    id = "g_trip", name = "Yurtdışı tatili", iconKey = "ucak", amount = 165_000.0,
                    unit = GoalUnit.Eur, targetDate = KefeDate(2027, 4, 1), monthlyContribution = 25_000.0,
                    anchorAmount = 3_000.0,
                ),
            )
            portfolio.assignPositionToGoal("pos_eur_try", "g_trip", 300.0)
        }

        fun vm() = GoalDetailViewModel(portfolio, clock, "g_trip", plan, prefs)
    }

    private suspend fun GoalDetailViewModel.awaitAssets(): GoalDetailUiState =
        realTime { state.first { it.composingAssets.isNotEmpty() } }

    @Test
    fun acilHarcamaBirKisminiDuserHedefAcikKalir() = runTest {
        val env = Env()
        env.seed()
        val vm = env.vm()
        vm.awaitAssets()

        vm.onIntent(GoalDetailIntent.OpenSpend)
        val opened = assertNotNull(vm.state.value.spend)
        assertEquals(300.0, opened.lines.single().assigned, 1e-9)
        // Bos acilir: tek bir yanlis dokunus butun birikimi satmasin.
        assertEquals(0.0, opened.total, 1e-9)
        assertEquals(false, opened.closeGoal)

        vm.onIntent(GoalDetailIntent.SpendQuantity("pos_eur_try", "200"))
        assertEquals(false, vm.state.value.spend?.closeGoal, "bir kismi harcaninca hedef acik kalir")
        vm.onIntent(GoalDetailIntent.SpendName("Araba tamiri"))
        vm.onIntent(GoalDetailIntent.ConfirmSpend)
        assertNull(vm.state.value.spend)

        // 200 € satildi; atama 300 -> 100 (elde kalan 250 degil).
        val assignment = realTime {
            var a = env.portfolio.goalAssignmentOf("pos_eur_try")
            while (a?.quantity != 100.0) { kotlinx.coroutines.delay(10); a = env.portfolio.goalAssignmentOf("pos_eur_try") }
            a
        }
        assertEquals("g_trip", assignment.goalId)
        assertEquals(250.0, env.portfolio.observePositions().first().single { it.id == "pos_eur_try" }.quantity, 1e-9)

        val expense = env.plan.observeMonthBook(october).first().expenses.single()
        assertEquals("Araba tamiri", expense.category.label())
        assertEquals(11_000.0, expense.amount, 1e-9)
        assertEquals("Yurtdışı tatili hedefinden", expense.note)

        val goal = env.portfolio.observeGoals().first().single()
        assertEquals(GoalStatus.Active, goal.status)
        assertNull(goal.spentAt)
    }

    @Test
    fun hepsiHarcanincaHedefHarcandiKapanir() = runTest {
        val env = Env()
        env.seed()
        val vm = env.vm()
        vm.awaitAssets()

        vm.onIntent(GoalDetailIntent.OpenSpend)
        vm.onIntent(GoalDetailIntent.SpendAll)
        assertEquals(true, vm.state.value.spend?.closeGoal, "tumu harcaninca hedef kapanir")
        vm.onIntent(GoalDetailIntent.ConfirmSpend)

        val goal = realTime {
            var g = env.portfolio.observeGoals().first().single()
            while (g.spentAt == null) { kotlinx.coroutines.delay(10); g = env.portfolio.observeGoals().first().single() }
            g
        }
        assertEquals(GoalStatus.Completed, goal.status)
        assertEquals(5_000L, goal.spentAt)
        assertEquals(150.0, env.portfolio.observePositions().first().single { it.id == "pos_eur_try" }.quantity, 1e-9)
        assertNull(env.portfolio.goalAssignmentOf("pos_eur_try"))
        assertEquals(16_500.0, env.plan.observeMonthBook(october).first().expenses.single().amount, 1e-9)
    }

    @Test
    fun adsizYaDaMiktarsizKaydetmez() = runTest {
        val env = Env()
        env.seed()
        val vm = env.vm()
        vm.awaitAssets()

        vm.onIntent(GoalDetailIntent.OpenSpend)
        vm.onIntent(GoalDetailIntent.SpendQuantity("pos_eur_try", "0"))
        vm.onIntent(GoalDetailIntent.SpendName(" "))
        vm.onIntent(GoalDetailIntent.ConfirmSpend)
        val sheet = assertNotNull(vm.state.value.spend)
        assertTrue(sheet.nameError)
        assertTrue(sheet.amountError)
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
