package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * ISLEM DUZENLEMEK ATAMAYI BOZMAZ.
 *
 * Bozuyordu. Duzenleme "once yeni kaydi yaz, sonra eskisini sil" sirasiyla
 * calisiyor ve katkilarin toplanabilir oldugu varsayiliyordu; ama satis dali
 * atamayi ELDE KALANA kirpiyor, yani katki o anki duruma bagli. Yeni katki,
 * silinmek uzere olan kaydin etkisi hala ortadayken hesaplaniyor, silme ise
 * eski kaydin TAM deltasini geri veriyordu: 8 satip sonra satisi 3'e cekmek
 * atamayi 7 yerine 8 birakiyordu ve bu rakam ese ve yedege gidiyordu.
 *
 * Artik geri alma ve yazma TEK yazmada, dogru sirayla.
 */
class TransactionEditTest {

    private fun newRepository(): SqlDelightPortfolioRepository {
        val driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties().apply { setProperty("foreign_keys", "true") },
        )
        KefeDatabase.Schema.create(driver)
        return SqlDelightPortfolioRepository(
            createKefeDatabase(driver),
            FixedKefeClock(millis = 1_000L),
            NoPrices(),
        )
    }

    private fun quarter() = Position(
        id = POSITION,
        name = "Çeyrek",
        assetClass = AssetClass.Gold,
        subtype = GoldSubtype.Quarter,
        quantity = 0.0,
        unit = QuantityUnit.Piece,
        unitPrice = 10_000.0,
        value = 0.0,
        cost = 0.0,
    )

    private fun goal() = Goal(
        id = GOAL,
        name = "Ev",
        iconKey = "ev",
        amount = 1_000_000.0,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2028, 12, 1),
        monthlyContribution = 5_000.0,
    )

    private fun tx(id: String, quantity: Double, side: TradeSide = TradeSide.Buy) = Transaction(
        id = id,
        positionId = POSITION,
        date = KefeDate(2026, 8, 1),
        side = side,
        quantity = quantity,
        unitPrice = 10_000.0,
        addedByMemberId = "m1",
    )

    private suspend fun SqlDelightPortfolioRepository.assigned(): Double =
        observeGoalAssets().first()[POSITION]?.quantity ?: 0.0

    private suspend fun SqlDelightPortfolioRepository.held(): Double =
        observePositions().first().firstOrNull { it.id == POSITION }?.quantity ?: 0.0

    /** Hazirlik: 10 ceyrek alindi ve hepsi Ev'e sayildi. */
    private suspend fun SqlDelightPortfolioRepository.seed() {
        upsertPosition(quarter())
        upsertGoal(goal())
        replaceTransaction(tx("tx1", 10.0), replacing = null, selectedGoalId = GOAL)
    }

    @Test
    fun alimHedefeSAYILIR() = runTest {
        val repo = newRepository()
        repo.seed()

        assertEquals(10.0, repo.assigned(), EPS)
        assertEquals(10.0, repo.held(), EPS)
    }

    /**
     * ASIL HATA. 8 satildi (atama 2'ye indi), sonra satis 3'e cekildi: elde 7
     * ceyrek var, hedef de 7 saymali.
     */
    @Test
    fun satisiKUCULTMEKAtamayiSisirmez() = runTest {
        val repo = newRepository()
        repo.seed()
        repo.replaceTransaction(tx("tx2", 8.0, TradeSide.Sell), null, selectedGoalId = GOAL)
        assertEquals(2.0, repo.assigned(), EPS)

        // Duzenleme: ayni kaydin yerine 3'luk satis.
        repo.replaceTransaction(tx("tx3", 3.0, TradeSide.Sell), replacing = "tx2", selectedGoalId = GOAL)

        assertEquals(7.0, repo.held(), EPS)
        assertEquals(7.0, repo.assigned(), EPS, "duzenleme atamayi elde olandan fazla birakti")
    }

    @Test
    fun satisiBUYUTMEKDeDogru() = runTest {
        val repo = newRepository()
        repo.seed()
        repo.replaceTransaction(tx("tx2", 3.0, TradeSide.Sell), null, selectedGoalId = GOAL)
        assertEquals(7.0, repo.assigned(), EPS)

        repo.replaceTransaction(tx("tx3", 8.0, TradeSide.Sell), replacing = "tx2", selectedGoalId = GOAL)

        assertEquals(2.0, repo.held(), EPS)
        assertEquals(2.0, repo.assigned(), EPS)
    }

    @Test
    fun alimiKUCULTMEKAtamayiDusurur() = runTest {
        val repo = newRepository()
        repo.seed()

        // 10'luk alim 6'ya cekiliyor.
        repo.replaceTransaction(tx("tx2", 6.0), replacing = "tx1", selectedGoalId = GOAL)

        assertEquals(6.0, repo.held(), EPS)
        assertEquals(6.0, repo.assigned(), EPS)
    }

    /**
     * SATIS, DOKUNMADIGI BIRIMLERI HEDEFTEN SILMEZ.
     *
     * 10 ceyregin 4'u Ev'e atanmisken atanmamis 6 tanesi satiliyor. Elde tam da
     * soz verilen 4 ceyrek kaliyor; hedef de 4 saymali. Eski kural
     * `min(satilan, atanan)` dusurdugu icin atamayi SIFIRLIYORDU.
     */
    @Test
    fun ATANMAMISIsatmakHedefeDokunmaz() = runTest {
        val repo = newRepository()
        repo.seed()
        // Kullanici hedef detayindan payi 4'e cekiyor.
        repo.assignPositionToGoal(POSITION, GOAL, quantity = 4.0)

        repo.replaceTransaction(tx("tx2", 6.0, TradeSide.Sell), null, selectedGoalId = null)

        assertEquals(4.0, repo.held(), EPS)
        assertEquals(4.0, repo.assigned(), EPS, "satis atanmamis birimleri hedeften sildi")
    }

    /** Satis atanana girdiginde fazlasi kadar duser. */
    @Test
    fun atanaGIRENSatisKadariniDusurur() = runTest {
        val repo = newRepository()
        repo.seed()
        repo.assignPositionToGoal(POSITION, GOAL, quantity = 8.0)

        // 6 satildi: 2 atanmamis + 4 atanan gitti.
        repo.replaceTransaction(tx("tx2", 6.0, TradeSide.Sell), null, selectedGoalId = null)

        assertEquals(4.0, repo.held(), EPS)
        assertEquals(4.0, repo.assigned(), EPS)
    }

    /** Duzenleme akisa "sildi" satiri dusmez - kullanici silmedi, duzeltti. */
    @Test
    fun duzenlemeSILDIkaydiYazmaz() = runTest {
        val repo = newRepository()
        repo.seed()

        repo.replaceTransaction(tx("tx2", 6.0), replacing = "tx1", selectedGoalId = GOAL)

        val silmeler = repo.observeActivity().first().filter { "sildi" in it.description }
        assertEquals(0, silmeler.size, "duzenleme silme kaydi yazdi: $silmeler")
    }

    private companion object {
        const val POSITION = "pos_gold_quarter"
        const val GOAL = "goal-1"
        const val EPS = 1e-9
    }
}
