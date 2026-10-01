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
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.goalWealth
import com.kefe.app.domain.model.monthPlanProgress
import com.kefe.app.testing.TestMain
import com.kefe.app.ui.screens.plan.plannedForGoal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Kura bagli hedefin rakamlari HEDEFIN BIRIMINDE: "€100 / €1.800", TL kucuk not.
 * Elde tutulan euro adediyle sayilir - satis/alis makasi 100 euroyu €99,51 yapmasin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalUnitDisplayTest {

    /** Kurulan VM testten sonra durdurulur (bkz. [TestMain]). */
    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    /** €1.800 Nisan 2027, euro alis kuru 55,27 -> ₺99.486. */
    private val trip = Goal(
        id = "g_trip",
        name = "Amsterdam Gezi",
        iconKey = "ucak",
        amount = 1_800.0 * 55.27,
        unit = GoalUnit.Eur,
        targetDate = KefeDate(2027, 4, 1),
        monthlyContribution = 0.0,
        anchorAmount = 1_800.0,
    )

    /** 100 € satis fiyatindan (55,00) degerlenmis: ₺5.500. */
    private val euro = Position(
        id = "pos_eur_try", name = "Euro", assetClass = AssetClass.Fx, quantity = 100.0,
        unit = QuantityUnit.Currency, unitPrice = 55.0, value = 5_500.0, cost = 5_527.0,
    )

    private val gram = Position(
        id = "pos_gold_gram", name = "Gram Altın", assetClass = AssetClass.Gold, subtype = GoldSubtype.Gram,
        quantity = 1.0, unit = QuantityUnit.Gram, unitPrice = 6_000.0, value = 6_000.0, cost = 6_000.0,
    )

    @Test
    fun ayniBirimdekiVarlikAdediylesayilir() {
        val assigned = mapOf("pos_eur_try" to GoalAssignment("g_trip"), "pos_gold_gram" to GoalAssignment("g_trip"))
        // Euro hedefin kuruyla (100 x 55,27), altin TL degeriyle.
        val wealth = goalWealth(trip, listOf(euro, gram), assigned)
        assertEquals(100.0 * 55.27 + 6_000.0, wealth, 1e-6)
        assertEquals("€100", trip.money.main(goalWealth(trip, listOf(euro), assigned)))

        // Kismi atama: 450'nin 300'u -> €300.
        val partial = goalWealth(trip, listOf(euro.copy(quantity = 450.0, value = 24_750.0)), mapOf("pos_eur_try" to GoalAssignment("g_trip", 300.0)))
        assertEquals("€300", trip.money.main(partial))
    }

    @Test
    fun tlHedefindeVarlikTlDegeriyleSayilir() {
        val tlGoal = trip.copy(unit = GoalUnit.Try, anchorAmount = null, amount = 100_000.0)
        val wealth = goalWealth(tlGoal, listOf(euro), mapOf("pos_eur_try" to GoalAssignment("g_trip")))
        assertEquals(5_500.0, wealth, 1e-9)
        assertEquals("₺5.500", tlGoal.money.main(wealth))
        assertNull(tlGoal.money.note(wealth))
        assertNull(tlGoal.tlNote(wealth))
    }

    @Test
    fun anaRakamBirimdeTlNotKucuk() {
        val money = trip.money
        assertEquals("€1.800", money.main(trip.amount))
        assertEquals("₺99.486", money.note(trip.amount))
        assertEquals("−€20", money.signed(-20.0 * 55.27))
        assertEquals("+€100", money.signedUnits(100.0))
        assertEquals("₺5.527 / ₺99.486 · güncel kurla", trip.tlNote(100.0 * 55.27))
    }

    @Test
    fun plandaEuroKalemiAdediyleYazilir() {
        val october = YearMonth(2026, 10)
        // Plan 55,27'den yapildi, kur artik 56: TL'den geri cevirmek €98,70 derdi.
        val goal = trip.copy(amount = 1_800.0 * 56.0)
        val items = listOf(
            PlanItem(
                id = "pi_eur", month = october, assetKey = "eur_try", assetName = "Euro",
                mode = PlanTargetMode.Quantity, target = 100.0, goalId = "g_trip", unitPriceAtPlan = 55.27,
            ),
        )
        val lines = monthPlanProgress(october, items, emptyList(), emptyList(), KefeDate(2026, 10, 1)).items
        assertEquals("€100", plannedForGoal(goal, lines))
        assertEquals("₺5.527", plannedForGoal(goal.copy(unit = GoalUnit.Try, anchorAmount = null), lines))
    }

    @Test
    fun senaryoKaydiricisiHedefinBiriminde() {
        // €1.800 / 6 ay = €300 -> ust sinir 2 x 300 = 600'un ustundeki duz sayi: €1.000.
        val scale = scenarioScaleOf(trip, KefeDate(2026, 10, 1))
        assertEquals(50f, scale.min)
        assertEquals(1_000f, scale.max)
        assertEquals(19, scale.steps)
        assertEquals(50f, scale.step)
        assertEquals(55.27, scale.tlPerUnit, 1e-9)
        // TL hedef eski olcekte kalir.
        assertEquals(ScenarioScale.Thousands, scenarioScaleOf(trip.copy(unit = GoalUnit.Try, anchorAmount = null), KefeDate(2026, 10, 1)))
    }

    @Test
    fun detaydaBirikimGerekenVeGecmisEuroIle() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database = createKefeDatabase(driver).also { it.bootstrapIfNeeded() }
        val clock = FixedKefeClock(KefeDate(2026, 10, 1), millis = 5_000L)
        val portfolio = SqlDelightPortfolioRepository(database, clock, NoPrices())
        portfolio.upsertPosition(euro.copy(quantity = 0.0, value = 0.0, cost = 0.0))
        portfolio.addTransaction(
            Transaction(
                id = "tx_eur", positionId = "pos_eur_try", date = KefeDate(2026, 10, 1), side = TradeSide.Buy,
                quantity = 100.0, unitPrice = 55.0, addedByMemberId = "member_owner",
            ),
        )
        portfolio.upsertGoal(trip)
        portfolio.assignPositionToGoal("pos_eur_try", "g_trip", GoalAssignment.WholePosition)
        val vm = main.track(
            GoalDetailViewModel(
                portfolio, clock, "g_trip", SqlDelightPlanRepository(database, clock), SqlDelightPreferencesRepository(database),
            ),
        )

        val state = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(10_000) { vm.state.first { it.composingAssets.isNotEmpty() } }
        }
        val goal = assertNotNull(state.goal)
        assertEquals("€100", goal.money.main(state.currentWealth))
        // (1.800 - 100) / 6 ay.
        assertEquals("€283,33", state.required?.main)
        assertEquals(50f, state.scenarioScale.min)
        // Ekim'in katkisi euro adediyle.
        assertEquals(100.0, state.rows.first().unitContribution)
    }
}
