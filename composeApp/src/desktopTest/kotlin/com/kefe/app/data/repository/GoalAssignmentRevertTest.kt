package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
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
 * ISLEM SILININCE HEDEF ATAMASI DA GERI ALINIR.
 *
 * Alinmiyordu. Atama her kayitta artirilip azaltiliyor ama hicbir yerde "bu
 * kaydin katkisi neydi" yazmiyordu; silme atamaya hic dokunmuyor, duzenleme de
 * (once yeni kaydi yaz, sonra eskisini sil) katkiyi IKINCI KEZ uyguluyordu.
 *
 * Katki artik kaydin kendi alani ([Transaction.goalDelta], bkz. 9.sqm) ve silme
 * onu geri veriyor.
 */
class GoalAssignmentRevertTest {

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

    private fun goal(id: String = "goal-1", name: String = "Ev") = Goal(
        id = id,
        name = name,
        iconKey = "ev",
        amount = 100_000.0,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2028, 12, 1),
        monthlyContribution = 5_000.0,
    )

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

    private fun transaction(
        id: String,
        quantity: Double,
        side: TradeSide = TradeSide.Buy,
        goalDelta: Double = 0.0,
        goalId: String? = null,
    ) = Transaction(
        id = id,
        positionId = POSITION,
        date = KefeDate(2026, 8, 1),
        side = side,
        quantity = quantity,
        unitPrice = 10_000.0,
        addedByMemberId = "m1",
        goalId = goalId,
        goalDelta = goalDelta,
    )

    private suspend fun SqlDelightPortfolioRepository.assignedQuantity(): Double =
        observeGoalAssets().first()[POSITION]?.quantity ?: 0.0

    /** Hazirlik: 10 ceyrek alinmis ve hepsi Ev'e sayilmis. */
    private suspend fun SqlDelightPortfolioRepository.seed() {
        upsertPosition(quarter())
        upsertGoal(goal())
        addTransaction(transaction("tx1", quantity = 10.0, goalId = "goal-1", goalDelta = 10.0))
        assignPositionToGoal(POSITION, "goal-1", quantity = 10.0)
    }

    @Test
    fun silinenALIMAtamadanDusulur() = runTest {
        val repo = newRepository()
        repo.seed()
        // 3 ceyrek daha, yine Ev'e.
        repo.addTransaction(transaction("tx2", quantity = 3.0, goalId = "goal-1", goalDelta = 3.0))
        repo.assignPositionToGoal(POSITION, "goal-1", quantity = 13.0)

        repo.deleteTransaction("tx2")

        assertEquals(10.0, repo.assignedQuantity(), EPS, "silinen alim atamadan dusulmedi")
    }

    @Test
    fun silinenSATISAtamayaGeriEklenir() = runTest {
        val repo = newRepository()
        repo.seed()
        // 4 ceyrek satildi: atama 6'ya dustu.
        repo.addTransaction(
            transaction("tx2", quantity = 4.0, side = TradeSide.Sell, goalId = "goal-1", goalDelta = -4.0),
        )
        repo.assignPositionToGoal(POSITION, "goal-1", quantity = 6.0)

        repo.deleteTransaction("tx2")

        assertEquals(10.0, repo.assignedQuantity(), EPS, "silinen satis atamaya geri eklenmedi")
    }

    @Test
    fun katkisizKayitAtamayaDOKUNMAZ() = runTest {
        val repo = newRepository()
        repo.seed()
        // "Hedefsiz" eklenmis bir alim: atamaya katkisi yok.
        repo.addTransaction(transaction("tx2", quantity = 3.0))

        repo.deleteTransaction("tx2")

        assertEquals(10.0, repo.assignedQuantity(), EPS)
    }

    @Test
    fun varlikBaskaHedefeTASINDIYSAGeriAlmaYok() = runTest {
        val repo = newRepository()
        repo.seed()
        repo.upsertGoal(goal(id = "goal-2", name = "Araba"))
        // Varlik artik Araba'da.
        repo.assignPositionToGoal(POSITION, "goal-2", quantity = 10.0)

        repo.deleteTransaction("tx1")

        assertEquals(
            GoalAssignment("goal-2", 10.0),
            repo.observeGoalAssets().first()[POSITION],
            "silme baska hedefin rakamini kurcaladi",
        )
    }

    @Test
    fun atamayiYaratanKayitSilininceAtamaSIFIRLANIR() = runTest {
        val repo = newRepository()
        repo.seed()

        repo.deleteTransaction("tx1")

        // Hedefe sayilan tek kayit gitti: sayilacak bir sey kalmadi.
        assertEquals(0.0, repo.assignedQuantity(), EPS)
    }

    private companion object {
        const val POSITION = "pos_gold_quarter"
        const val EPS = 1e-9
    }
}
