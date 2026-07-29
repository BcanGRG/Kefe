package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mezar tasi kurallari GERCEK veritabaniyla dogrulanir.
 *
 * Bu kurallar SQL'de yasiyor - "silinen satir okumalarda gorunmez", "silinen
 * islem hesaba katilmaz", "yeniden eklenen varlik dirilir". Sahte bir depoyla
 * hicbiri sinanmis olmaz.
 *
 * Emulatorde elle denemek de yetmedi: ekran gezinmesi kaydi ve bu kurallarin
 * hangisinin bozuldugunu ayirt edemedi.
 */
internal class NoPrices : PriceRepository {
    override fun observePrices(): Flow<PriceBoard> =
        flowOf(PriceBoard(emptyList(), "", PriceFreshness.Offline))

    override fun observePriceHistory(assetKey: String): Flow<List<Double>> = flowOf(emptyList())
    override suspend fun refresh(): Result<RefreshOutcome> =
        Result.success(RefreshOutcome.Fetched)
    override suspend fun setManualPrice(assetKey: String, value: Double) = Unit
    override suspend fun clearManualPrice(assetKey: String) = Unit
}

private fun newRepository(clock: KefeClock = FixedKefeClock(millis = 1_000L)): SqlDelightPortfolioRepository {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KefeDatabase.Schema.create(driver)
    val database = createKefeDatabase(driver)
    return SqlDelightPortfolioRepository(database, clock, NoPrices())
}

private val today = KefeDate(2026, 7, 28)

private fun quarter(id: String = "pos_gold_quarter") = Position(
    id = id,
    name = "Çeyrek",
    assetClass = AssetClass.Gold,
    subtype = GoldSubtype.Quarter,
    quantity = 0.0,
    unit = QuantityUnit.Piece,
    unitPrice = 10_000.0,
    value = 0.0,
    cost = 0.0,
)

private fun buy(id: String, quantity: Double) = Transaction(
    id = id,
    positionId = "pos_gold_quarter",
    date = today,
    side = TradeSide.Buy,
    quantity = quantity,
    unitPrice = 10_000.0,
    addedByMemberId = "member_owner",
)

class SoftDeleteTest {

    @Test
    fun silinenIslemDefterdenDuser() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.addTransaction(buy("tx-1", 2.0))
        repo.addTransaction(buy("tx-2", 1.0))

        assertEquals(3.0, repo.observePositions().first().single().quantity)

        repo.deleteTransaction("tx-2")

        // Miktar defterden YENIDEN hesaplanir; silinen satir sayilmaz.
        assertEquals(2.0, repo.observePositions().first().single().quantity)
        assertEquals(1, repo.observeAllTransactions().first().size)
    }

    @Test
    fun varlikSilininceDefteriDeDuser() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.addTransaction(buy("tx-1", 2.0))

        repo.deletePosition("pos_gold_quarter")

        assertTrue(repo.observePositions().first().isEmpty())
        // ASIL KURAL: varligin defteri de mezar tasi alir. Almasaydi varlik
        // yeniden eklendiginde eski islemler geri sayilirdi.
        assertTrue(repo.observeAllTransactions().first().isEmpty())
    }

    @Test
    fun silinenVarlikYenidenEklenincePeSirasiTemiz() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.addTransaction(buy("tx-1", 2.0))
        repo.deletePosition("pos_gold_quarter")

        // Kullanici ayni varligi yeniden ekliyor: pozisyon kimligi deterministik
        // oldugu icin AYNI satir dirilir - iki "Ceyrek" olusmaz.
        repo.upsertPosition(quarter())
        repo.addTransaction(buy("tx-3", 1.0))

        val positions = repo.observePositions().first()
        assertEquals(1, positions.size)
        // Eski iki ceyrek geri gelmemeli.
        assertEquals(1.0, positions.single().quantity)
    }

    @Test
    fun hedefSilininceListedenDuser() = runTest {
        val repo = newRepository()
        repo.upsertGoal(
            com.kefe.app.domain.model.Goal(
                id = "goal-1",
                name = "Ev",
                iconKey = "ev",
                amount = 100_000.0,
                unit = com.kefe.app.domain.model.GoalUnit.Try,
                targetDate = KefeDate(2028, 12, 1),
                monthlyContribution = 5_000.0,
            ),
        )
        assertEquals(1, repo.observeGoals().first().size)

        repo.deleteGoal("goal-1")
        assertTrue(repo.observeGoals().first().isEmpty())
    }

    @Test
    fun atamaKaldirilincaGorunmez() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(
            com.kefe.app.domain.model.Goal(
                id = "goal-1",
                name = "Ev",
                iconKey = "ev",
                amount = 100_000.0,
                unit = com.kefe.app.domain.model.GoalUnit.Try,
                targetDate = KefeDate(2028, 12, 1),
                monthlyContribution = 5_000.0,
            ),
        )

        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")
        assertEquals(mapOf("pos_gold_quarter" to "goal-1"), repo.observeGoalAssets().first())

        repo.assignPositionToGoal("pos_gold_quarter", null)
        assertTrue(repo.observeGoalAssets().first().isEmpty())

        // Yeniden atanabilmeli: mezar tasi kalici bir engel degil.
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")
        assertEquals(1, repo.observeGoalAssets().first().size)
    }
}
