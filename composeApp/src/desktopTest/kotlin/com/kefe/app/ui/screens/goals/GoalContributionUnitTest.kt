package com.kefe.app.ui.screens.goals

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Euro hedefinde aylik katki istege bagli olarak euro: kayit, okuma ve cevrim. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalContributionUnitTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Prices(ask: Double) : PriceRepository {
        val board = MutableStateFlow(
            PriceBoard(
                listOf(
                    Price(
                        assetKey = "eur_try", label = "Euro", bid = ask - 0.5, ask = ask, changePercent = 0.0,
                        timestamp = "10:00", source = PriceSource.FreeMarket, assetClass = AssetClass.Fx,
                    ),
                ),
                "",
                PriceFreshness.Fresh,
            ),
        )
        override fun observePrices(): Flow<PriceBoard> = board
        override fun observePriceHistory(assetKey: String): Flow<List<PricePoint>> = flowOf(emptyList())
        override suspend fun refresh(): Result<RefreshOutcome> = Result.success(RefreshOutcome.Fetched)
        override suspend fun setManualPrice(assetKey: String, value: Double) = Unit
        override suspend fun clearManualPrice(assetKey: String) = Unit
    }

    private fun env(prices: Prices): Pair<SqlDelightPortfolioRepository, GoalsViewModel> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val clock = FixedKefeClock(KefeDate(2026, 10, 1), millis = 1_000L)
        val repo = SqlDelightPortfolioRepository(createKefeDatabase(driver), clock, prices)
        return repo to GoalsViewModel(repo, prices, clock)
    }

    private fun GoalsViewModel.editor() = assertNotNull(state.value.editor)

    @Test
    fun euroHedefindeKatkiEuroOlurVeGuncelKurlaOkunur() = runTest {
        val prices = Prices(ask = 56.0)
        val (repo, vm) = env(prices)
        vm.onIntent(GoalsIntent.CreateGoal(null))
        vm.onIntent(GoalsIntent.EditorName("Yurtdışı tatili"))
        vm.onIntent(GoalsIntent.EditorUnit(GoalUnit.Eur))
        // Katki bos iken hedefin birimini izler.
        assertEquals(true, vm.editor().contributionInUnit)
        vm.onIntent(GoalsIntent.EditorAmount("3000"))
        vm.onIntent(GoalsIntent.EditorContribution("250"))
        vm.onIntent(GoalsIntent.SaveEditor)

        val goal = realTime {
            var g = repo.observeGoals().first().firstOrNull()
            while (g == null) { delay(10); g = repo.observeGoals().first().firstOrNull() }
            g
        }
        assertEquals(250.0, goal.contributionAnchor)
        assertEquals(250.0 * 56.0, goal.monthlyContribution, 1e-6)
        // Euro yukselince katkinin TL'si de buyur.
        prices.board.value = prices.board.value.copy(
            prices = prices.board.value.prices.map { it.copy(ask = 60.0) },
        )
        assertEquals(250.0 * 60.0, repo.observeGoals().first().single().monthlyContribution, 1e-6)
    }

    @Test
    fun tlyeCevrilenKatkiKurusunuKorur() = runTest {
        val (_, vm) = env(Prices(ask = 55.2687))
        vm.onIntent(GoalsIntent.CreateGoal(null))
        vm.onIntent(GoalsIntent.EditorUnit(GoalUnit.Eur))
        vm.onIntent(GoalsIntent.EditorContribution("250"))
        vm.onIntent(GoalsIntent.EditorContributionInUnit(false))
        // 250 x 55,2687 = 13.817,175 -> 13.817,18; tam liraya yuvarlanmaz.
        assertEquals(13_817.18, vm.editor().contributionText.replace(",", ".").toDouble(), 1e-9)
    }

    @Test
    fun katkiTlyeAlinincaCevrilirVeTlKaydedilir() = runTest {
        val prices = Prices(ask = 50.0)
        val (repo, vm) = env(prices)
        vm.onIntent(GoalsIntent.CreateGoal(null))
        vm.onIntent(GoalsIntent.EditorName("Tatil"))
        vm.onIntent(GoalsIntent.EditorUnit(GoalUnit.Eur))
        vm.onIntent(GoalsIntent.EditorAmount("3000"))
        vm.onIntent(GoalsIntent.EditorContribution("200"))
        // €200 -> ₺10.000: yazilan deger anlamini korur.
        vm.onIntent(GoalsIntent.EditorContributionInUnit(false))
        assertEquals(false, vm.editor().contributionInUnit)
        assertEquals("10000", vm.editor().contributionText)
        vm.onIntent(GoalsIntent.SaveEditor)

        val goal = realTime {
            var g = repo.observeGoals().first().firstOrNull()
            while (g == null) { delay(10); g = repo.observeGoals().first().firstOrNull() }
            g
        }
        assertNull(goal.contributionAnchor)
        assertEquals(10_000.0, goal.monthlyContribution, 1e-6)
        // Hedef kurla buyur, TL katki sabit kalir.
        prices.board.value = prices.board.value.copy(prices = prices.board.value.prices.map { it.copy(ask = 60.0) })
        val later = repo.observeGoals().first().single()
        assertEquals(180_000.0, later.amount, 1e-6)
        assertEquals(10_000.0, later.monthlyContribution, 1e-6)
    }
}

private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
