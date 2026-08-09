package com.kefe.app.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoalStatus
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Hedefi KAYDETMEK, o hedefin varlik atamalarini silmemeli.
 *
 * Siliyordu. `upsertGoal` tek bir INSERT OR REPLACE idi ve goal_assets.goalId
 * hedefe ON DELETE CASCADE bagli: REPLACE catisan satiri once SILDIGI icin
 * hedefi duzenleyip kaydetmek, tamamlandi isaretlemek ya da baska bir hedefi
 * ana hedef yapmak o hedefin butun atamalarini goturuyor, goalWealth() atama
 * bulamayip 0 donuyor ve ilerleme %0'a dusuyordu.
 *
 * BU TEST YABANCI ANAHTAR ZORLAMASINI ACIK KURAR. Suitteki diger veritabani
 * testleri surucuyu ciplak kuruyor; SQLite'ta zorlama baglanti basina ve
 * VARSAYILAN OLARAK KAPALI oldugu icin CASCADE orada hic atesleniyor ve hata
 * testlerden gorunmez kaliyordu. Uc platform surucusunun ucu de zorlamayi
 * aciyor - test ortami uretimden farkli davraniyordu.
 */
class GoalAssignmentSurvivalTest {

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

    private fun goal(
        name: String = "Ev",
        isMain: Boolean = false,
        status: GoalStatus = GoalStatus.Active,
        id: String = "goal-1",
    ) = Goal(
        id = id,
        name = name,
        iconKey = "ev",
        amount = 100_000.0,
        unit = GoalUnit.Try,
        targetDate = KefeDate(2028, 12, 1),
        monthlyContribution = 5_000.0,
        isMain = isMain,
        status = status,
    )

    private fun quarter(id: String = "pos_gold_quarter") = Position(
        id = id,
        name = "Çeyrek",
        assetClass = AssetClass.Gold,
        subtype = GoldSubtype.Quarter,
        quantity = 10.0,
        unit = QuantityUnit.Piece,
        unitPrice = 10_000.0,
        value = 100_000.0,
        cost = 100_000.0,
    )

    /**
     * Once ZORLAMANIN ACIK oldugunu kanitla.
     *
     * Bu satir olmadan asagidaki testler yanlis sebeple gecerdi: zorlama kapali
     * bir veritabaninda CASCADE hic calismaz ve hatali kod da testi gecer.
     */
    @Test
    fun yabanciAnahtarZorlamasiACIK() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        assertFailsWith<Exception> {
            repo.assignPositionToGoal("pos_gold_quarter", "boyle-bir-hedef-yok")
        }
    }

    @Test
    fun hedefiDuzenleyipKaydetmekATAMALARISilmez() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")

        repo.upsertGoal(goal(name = "Ev peşinatı"))

        assertEquals(
            mapOf("pos_gold_quarter" to GoalAssignment("goal-1")),
            repo.observeGoalAssets().first(),
            "hedefi kaydetmek atamayi sildi",
        )
        assertEquals("Ev peşinatı", repo.observeGoals().first().single().name)
    }

    @Test
    fun hedefiTamamlandiIsaretlemekATAMALARISilmez() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")

        repo.upsertGoal(goal(status = GoalStatus.Completed))

        assertEquals(
            mapOf("pos_gold_quarter" to GoalAssignment("goal-1")),
            repo.observeGoalAssets().first(),
            "hedefi tamamlamak atamayi sildi",
        )
    }

    /**
     * Ana hedefi degistirmek ESKI ana hedefe de bir kaydetme yazar
     * (`isMain = false`); atamalari kaybeden hedef, kullanicinin hic
     * dokunmadigi hedef oluyordu.
     */
    @Test
    fun anaHedefiDegistirmekESKIHedefinAtamalariniSilmez() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal(isMain = true))
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")

        repo.upsertGoal(goal(isMain = false))

        assertEquals(
            mapOf("pos_gold_quarter" to GoalAssignment("goal-1")),
            repo.observeGoalAssets().first(),
            "ana hedef isareti kalkinca atama silindi",
        )
    }

    /** Kismi atama (miktarli) da korunmali - deger okuma aninda kirpiliyor. */
    @Test
    fun kismiAtamaMiktariniKORUR() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1", 4.0)

        repo.upsertGoal(goal(name = "Ev peşinatı"))

        assertEquals(
            mapOf("pos_gold_quarter" to GoalAssignment("goal-1", 4.0)),
            repo.observeGoalAssets().first(),
        )
    }

    /**
     * Hedef silinince ATAMALARI da mezar taslanmali.
     *
     * Silme yumusak oldugu icin (deletedAt) CASCADE calismiyordu ve atama satiri
     * diri kaliyordu: yedege giriyor ama hedefi girmiyordu (selectGoals silinmisi
     * filtreliyor), geri yuklemede FOREIGN KEY hatasi atiyor ve TUM geri yukleme
     * geri aliniyordu - tek artik satir yedegin tamamini kullanilamaz kiliyordu.
     */
    @Test
    fun hedefSilininceATAMASIDaDuser() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")

        repo.deleteGoal("goal-1")

        assertTrue(
            repo.observeGoalAssets().first().isEmpty(),
            "silinen hedefin atamasi diri kaldi - yedegi bozar",
        )
    }

    /** Varlik silinince de ayni kural gecerli. */
    @Test
    fun varlikSilininceATAMASIDaDuser() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")

        repo.deletePosition("pos_gold_quarter")

        assertTrue(
            repo.observeGoalAssets().first().isEmpty(),
            "silinen varligin atamasi diri kaldi - yedegi bozar",
        )
    }

    /**
     * Silme sonrasi alinan yedek GERI YUKLENEBILMELI.
     *
     * Tetikleyici tamamen siradan: hedefe varlik ata, hedefi sil, yedek al.
     */
    @Test
    fun silmeSonrasiYedekGERIYUKLENIR() = runTest {
        val repo = newRepository()
        repo.upsertPosition(quarter())
        // Miktar DEFTERDEN turer: geri yukleme recomputePosition calistirdigi
        // icin islemsiz bir pozisyon sifirlanip listeden duserdi.
        repo.addTransaction(
            Transaction(
                id = "tx-1",
                positionId = "pos_gold_quarter",
                date = KefeDate(2026, 7, 28),
                side = TradeSide.Buy,
                quantity = 10.0,
                unitPrice = 10_000.0,
                addedByMemberId = "member_owner",
            ),
        )
        repo.upsertGoal(goal())
        repo.assignPositionToGoal("pos_gold_quarter", "goal-1")
        repo.deleteGoal("goal-1")

        val backup = repo.exportBackup("2026-08-09")
        // Atlanmasi gereken satir yedege hic girmemeli.
        assertTrue(backup.goalAssets.none { it.goalId == "goal-1" }, "oksuz atama yedege girdi")

        // Asil olcu: geri yukleme dusmemeli ve veri yerine oturmali.
        repo.restoreBackup(backup)
        assertEquals(1, repo.observePositions().first().size)
        assertTrue(repo.observeGoals().first().isEmpty())
    }

    /** Yeni hedef yine de yazilabilmeli - iki adima bolmek eklemeyi bozmamali. */
    @Test
    fun yeniHedefYAZILIR() = runTest {
        val repo = newRepository()
        repo.upsertGoal(goal())
        val goals = repo.observeGoals().first()
        assertEquals(1, goals.size)
        assertEquals("Ev", goals.single().name)
        assertTrue(goals.single().amount == 100_000.0)
    }
}
