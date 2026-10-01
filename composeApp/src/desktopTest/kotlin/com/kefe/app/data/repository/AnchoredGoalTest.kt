package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Kura bagli hedef GERCEK veritabaniyla: saklama bicimi eski surumle uyumlu mu
 * (unit kolonunda "Eur" yok), okuma guncel kurla mi.
 */
class AnchoredGoalTest {

    private class Prices : PriceRepository {
        val board = MutableStateFlow(PriceBoard(emptyList(), "", PriceFreshness.Fresh))
        override fun observePrices(): Flow<PriceBoard> = board
        override fun observePriceHistory(assetKey: String): Flow<List<PricePoint>> = flowOf(emptyList())
        override suspend fun refresh(): Result<RefreshOutcome> = Result.success(RefreshOutcome.Fetched)
        override suspend fun setManualPrice(assetKey: String, value: Double) = Unit
        override suspend fun clearManualPrice(assetKey: String) = Unit
    }

    private fun eur(ask: Double) = Price(
        assetKey = "eur_try", label = "Euro", bid = ask - 0.5, ask = ask, changePercent = 0.0,
        timestamp = "10:00", source = PriceSource.FreeMarket, assetClass = AssetClass.Fx,
    )

    private val trip = Goal(
        id = "g_trip",
        name = "Yurtdışı tatili",
        iconKey = "ucak",
        amount = 165_000.0,
        unit = GoalUnit.Eur,
        targetDate = KefeDate(2027, 4, 1),
        monthlyContribution = 25_000.0,
        anchorAmount = 3_000.0,
    )

    private fun harness(): Triple<SqlDelightPortfolioRepository, KefeDatabase, Prices> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val db = createKefeDatabase(driver)
        val prices = Prices()
        return Triple(SqlDelightPortfolioRepository(db, FixedKefeClock(millis = 1_000L), prices), db, prices)
    }

    @Test
    fun euroHedefiGuncelKurlaOkunur() = runTest {
        val (repo, _, prices) = harness()
        repo.upsertGoal(trip)
        prices.board.value = PriceBoard(listOf(eur(56.0)), "", PriceFreshness.Fresh)
        // €3.000 x 56 (SATIS kuru - odenecek taraf).
        assertEquals(168_000.0, repo.observeGoals().first().single().amount, 1e-6)
        prices.board.value = PriceBoard(listOf(eur(60.0)), "", PriceFreshness.Fresh)
        assertEquals(180_000.0, repo.observeGoals().first().single().amount, 1e-6)
    }

    @Test
    fun kurYoksaSonBilinenTlKalir() = runTest {
        val (repo, _, _) = harness()
        repo.upsertGoal(trip)
        val read = repo.observeGoals().first().single()
        assertEquals(165_000.0, read.amount, 1e-6)
        assertEquals(GoalUnit.Eur, read.unit)
        assertEquals(3_000.0, read.anchorAmount)
    }

    @Test
    fun eskiSurumIcinUnitKolonundaEuroYazilmaz() = runTest {
        val (repo, db, _) = harness()
        repo.upsertGoal(trip)
        val row = db.goalQueries.selectGoalById("g_trip").executeAsOne()
        // Euro'yu bilmeyen surum "Eur" metninde cokerdi; gercek birim anchorUnit'te.
        assertEquals(GoalUnit.Try, row.unit)
        assertEquals("Eur", row.anchorUnit)
        assertEquals(3_000.0, row.anchorAmount)
    }

    @Test
    fun tlHedefiSabitKalirVeKolonlarBos() = runTest {
        val (repo, db, prices) = harness()
        repo.upsertGoal(trip.copy(unit = GoalUnit.Try, anchorAmount = null))
        prices.board.value = PriceBoard(listOf(eur(60.0)), "", PriceFreshness.Fresh)
        assertEquals(165_000.0, repo.observeGoals().first().single().amount, 1e-6)
        val row = db.goalQueries.selectGoalById("g_trip").executeAsOne()
        assertNull(row.anchorUnit)
        assertNull(row.anchorAmount)
    }
}
