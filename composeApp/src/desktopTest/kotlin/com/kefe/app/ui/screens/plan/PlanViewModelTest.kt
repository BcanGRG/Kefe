package com.kefe.app.ui.screens.plan

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.repository.SqlDelightPlanRepository
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan sekmesinin ViewModel'i GERCEK veritabaniyla.
 *
 * Sabitlenen: sayfa bu ayla acilir, ileriye en fazla bir ay gidilir, "Bu ay"
 * geri getirir; gun donunce "bu ay" kendiliginden ilerler ama SECILMIS gecmis ay
 * yerinde kalir; sinirlar daralinca (ilk islem silindi) secim bu aya kirpilir ve
 * birakilir - ay sinira donunce sayfa oraya kendiliginden atlamaz.
 *
 * "Bugun" YALNIZ gun akisindan gelir: saat 22 Ekim'de sabit kalirken gun akisi
 * 1 Kasim'a ilerletilir - saatten okuyan tek satir burada yakalanir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Fiyat tablosu elle surulur; bos tablo yeterli olan testler varsayilani kullanir. */
    private class BoardPrices(prices: List<Price> = emptyList()) : PriceRepository {
        val board = MutableStateFlow(PriceBoard(prices, "", PriceFreshness.Fresh))
        override fun observePrices(): Flow<PriceBoard> = board
        override fun observePriceHistory(assetKey: String): Flow<List<PricePoint>> = flowOf(emptyList())
        override suspend fun refresh(): Result<RefreshOutcome> = Result.success(RefreshOutcome.Fetched)
        override suspend fun setManualPrice(assetKey: String, value: Double) = Unit
        override suspend fun clearManualPrice(assetKey: String) = Unit
    }

    private class Env {
        val database: KefeDatabase
        val prices = BoardPrices()
        val portfolio: SqlDelightPortfolioRepository
        val plan: SqlDelightPlanRepository
        val prefs: SqlDelightPreferencesRepository
        val clock = FixedKefeClock(KefeDate(2026, 10, 22), millis = 1_000L)

        /** Gun akisi - testler elle ilerletir; saat yerinde kalir. */
        val days = MutableStateFlow(KefeDate(2026, 10, 22))

        init {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            KefeDatabase.Schema.create(driver)
            database = createKefeDatabase(driver)
            database.bootstrapIfNeeded()
            portfolio = SqlDelightPortfolioRepository(database, clock, prices)
            plan = SqlDelightPlanRepository(database, clock)
            prefs = SqlDelightPreferencesRepository(database)
        }

        /** Veri once tohumlanir, VM sonra kurulur: ilk Ready durumu veriyi gorur. */
        fun vm() = PlanViewModel(plan, portfolio, prices, prefs, clock, dayTicks = days)

        suspend fun buyGram(id: String, date: KefeDate) {
            portfolio.upsertPosition(gram())
            portfolio.addTransaction(
                Transaction(
                    id = id,
                    positionId = GramId,
                    date = date,
                    side = TradeSide.Buy,
                    quantity = 1.0,
                    unitPrice = 6_700.0,
                    addedByMemberId = "member_owner",
                ),
            )
        }
    }

    // --- Acilis ve ay gecisi -------------------------------------------------

    @Test
    fun `bu ayla acilir - geri ve ileri acik, cip yok`() = runTest {
        val vm = Env().vm()
        // Yuklenirken de baslik dogru ay: gecis dugmeleri Ready'ye kadar kapali (ekran).
        assertEquals("Ekim 2026", vm.state.value.content.header.title)

        val header = vm.awaitHeader { it.title == "Ekim 2026" }
        assertEquals("Ekim · 9 gün kaldı", header.subtitle)
        assertEquals(MonthRelation.Current, header.relation)
        assertTrue(header.canGoBack)
        assertTrue(header.canGoForward)
        assertFalse(header.showThisMonthChip)
    }

    @Test
    fun `ileriye en fazla bir ay, Bu ay geri getirir`() = runTest {
        val vm = Env().vm()
        vm.awaitHeader { it.title == "Ekim 2026" }

        vm.onIntent(PlanIntent.NextMonth)
        val next = vm.awaitHeader { it.title == "Kasım 2026" }
        assertFalse(next.canGoForward)
        assertTrue(next.showThisMonthChip)
        assertEquals(MonthRelation.Future, next.relation)
        assertEquals("Gelecek ay · alımlar ay başlayınca sayılır.", next.subtitle)

        vm.onIntent(PlanIntent.ThisMonth)
        val back = vm.awaitHeader { it.title == "Ekim 2026" }
        assertFalse(back.showThisMonthChip)
    }

    @Test
    fun `ikinci ileri hicbir sey secmez`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.NextMonth)
        vm.awaitHeader { it.title == "Kasım 2026" }

        vm.onIntent(PlanIntent.NextMonth)
        // Ikinci dokunus Aralik'i secseydi gun Kasim'a donunce sayfa Aralik'a
        // (artik sinir icinde) atlardi. Secili kalan Kasim bu ay olur.
        env.days.value = KefeDate(2026, 11, 1)
        val nov = vm.awaitHeader { it.relation == MonthRelation.Current }
        assertEquals("Kasım 2026", nov.title)
        assertFalse(nov.showThisMonthChip)
    }

    @Test
    fun `veri yokken geriye yalniz gecen ay`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }

        vm.onIntent(PlanIntent.PreviousMonth)
        val sep = vm.awaitHeader { it.title == "Eylül 2026" }
        assertFalse(sep.canGoBack)
        assertEquals("Geçmiş ay · alımlar işlem tarihine göre sayılır.", sep.subtitle)

        vm.onIntent(PlanIntent.PreviousMonth)
        // Yok sayilan dokunusun sonucu hemen okunamaz: turetim Default'ta, yeni bir
        // secim duruma ancak SONRAKI turetimle yansir. Agustos islemi o turetimi
        // zorlar ve Eylul'un geri gecisini acar. Dokunus Agustos'u secmis olsaydi
        // sayfa once bu aya kirpilir (Ekim), sonra Agustos'a gecerdi - Eylul degil.
        env.buyGram("tx_agustos", KefeDate(2026, 8, 5))
        val after = vm.awaitHeader { it.canGoBack }
        assertEquals("Eylül 2026", after.title)
    }

    // --- Gun donumu ----------------------------------------------------------

    @Test
    fun `gun donunce bu ay kendiliginden ilerler`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }

        // Saat hala 22 Ekim: baslik yalniz gun akisindan ilerleyebilir.
        env.days.value = KefeDate(2026, 11, 1)
        val nov = vm.awaitHeader { it.title == "Kasım 2026" }
        assertEquals("Kasım · 29 gün kaldı", nov.subtitle)
        assertEquals(MonthRelation.Current, nov.relation)
        assertFalse(nov.showThisMonthChip)
        assertTrue(nov.canGoForward)
    }

    @Test
    fun `secilmis gecmis ay gun donumunde yerinde kalir`() = runTest {
        val env = Env()
        env.buyGram("tx_eylul", KefeDate(2026, 9, 10))
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitHeader { it.title == "Eylül 2026" && !it.canGoBack }

        env.days.value = KefeDate(2026, 11, 1)
        // Gunden SONRA gelen bir veri emisyonu: bu turetim yeni gunu gormus olur.
        // Agustos islemi siniri genisletir; sayfa Eylul'de kalmali.
        env.buyGram("tx_agustos", KefeDate(2026, 8, 5))
        val sep = vm.awaitHeader { it.canGoBack }
        assertEquals("Eylül 2026", sep.title)
        assertEquals(MonthRelation.Past, sep.relation)

        // Artik "bu ay" Kasim: Eylul'den bir ileri Ekim, o da gecmis ay.
        vm.onIntent(PlanIntent.NextMonth)
        val oct = vm.awaitHeader { it.title == "Ekim 2026" }
        assertEquals(MonthRelation.Past, oct.relation)
        assertTrue(oct.showThisMonthChip)
    }

    // --- Sinirlar ------------------------------------------------------------

    @Test
    fun `sinirlar daralinca secim bu aya kirpilir`() = runTest {
        val env = Env()
        env.buyGram("tx_agustos", KefeDate(2026, 8, 5))
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitHeader { it.title == "Eylül 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        val aug = vm.awaitHeader { it.title == "Ağustos 2026" }
        assertFalse(aug.canGoBack)

        // Agustos'un tek islemi silinir: secim sinirin disinda kalir, bu aya donulur.
        env.portfolio.deleteTransaction("tx_agustos")
        vm.awaitHeader { it.title == "Ekim 2026" }

        // Geriye artik yalniz Eylul.
        vm.onIntent(PlanIntent.PreviousMonth)
        val sep = vm.awaitHeader { it.title == "Eylül 2026" }
        assertFalse(sep.canGoBack)
    }

    @Test
    fun `kirpilan secim birakilir - ay sinira donunce sayfa oraya atlamaz`() = runTest {
        val env = Env()
        env.buyGram("tx_agustos", KefeDate(2026, 8, 5))
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitHeader { it.title == "Eylül 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitHeader { it.title == "Ağustos 2026" }

        env.portfolio.deleteTransaction("tx_agustos")
        vm.awaitHeader { it.title == "Ekim 2026" }

        // Agustos sinira geri doner: cihaz saati 5 Eylul'e alininca gecen ay Agustos
        // olur (ayin verisi esitlemeyle ya da yedekten geri geldiginde de oyle).
        // Tutulan bir secim sayfayi kendiliginden Agustos'a atlatirdi; birakilmis
        // secimle sayfa yeni "bu ay"da, Eylul'de kalir.
        env.days.value = KefeDate(2026, 9, 5)
        val shown = vm.awaitHeader { it.title != "Ekim 2026" }
        assertEquals("Eylül 2026", shown.title)
        assertEquals(MonthRelation.Current, shown.relation)
    }

    @Test
    fun `yalniz harcamasi olan ay ulasilabilir`() = runTest {
        val env = Env()
        env.plan.upsertExpense(
            ExpenseEntry(
                id = "e_agustos",
                date = KefeDate(2026, 8, 15),
                category = ExpenseCategory.Groceries,
                amount = 1_500.0,
            ),
        )
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.PreviousMonth)
        val sep = vm.awaitHeader { it.title == "Eylül 2026" }
        assertTrue(sep.canGoBack)
        vm.onIntent(PlanIntent.PreviousMonth)
        val aug = vm.awaitHeader { it.title == "Ağustos 2026" }
        assertFalse(aug.canGoBack)
    }
}

private const val GramId = "pos_gold_gram"

private fun gram() = Position(
    id = GramId,
    name = "Gram Altın",
    assetClass = AssetClass.Gold,
    subtype = GoldSubtype.Gram,
    quantity = 0.0,
    unit = QuantityUnit.Gram,
    unitPrice = 6_700.0,
    value = 0.0,
    cost = 0.0,
)

/** Ready durumunda ve [predicate]'i saglayan ilk baslik. */
private suspend fun PlanViewModel.awaitHeader(predicate: (PlanHeader) -> Boolean): PlanHeader =
    realTime { state.first { it.stage == PlanStage.Ready && predicate(it.content.header) } }.content.header

/**
 * Depo akislari ve turetim GERCEK is parcaciklarinda (Dispatchers.Default) calisiyor;
 * runTest'in sanal saati onlari beklemez, zaman asimi hemen dolardi.
 */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }
