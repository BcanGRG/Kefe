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
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalUnit
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PricePoint
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import com.kefe.app.testing.TestMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.kefe.app.ui.screens.transaction.AddTransactionPrefill

/**
 * Plan sekmesinin ViewModel'i GERCEK veritabaniyla.
 *
 * Sabitlenen: sayfa bu ayla acilir, ileriye en fazla bir ay gidilir, "Bu ay"
 * geri getirir; gun donunce "bu ay" kendiliginden ilerler ama SECILMIS gecmis ay
 * yerinde kalir; sinirlar daralinca (ilk islem silindi) secim bu aya kirpilir ve
 * birakilir - ay sinira donunce sayfa oraya kendiliginden atlamaz.
 *
 * Kalem editoru: kayit guncel ALIS fiyatini saklar (gecmis ayin ayni varligi
 * kayitli fiyatini korur), sheet ilk dokunusta kapanir; bu ay planli bir varlik
 * secilince ya da kodu yazilinca o kaleme gecilir, hicbir sey ezilmez; yazilan
 * hedef veri emisyonlarinda yerinde kalir. Devir varsayilan "Bırak"tir ve tasinan
 * eksik yalniz bir kez eklenir.
 *
 * "Bugun" YALNIZ gun akisindan gelir: saat 22 Ekim'de sabit kalirken gun akisi
 * 1 Kasim'a ilerletilir - saatten okuyan tek satir burada yakalanir.
 *
 * Defter: gelir kisi basinadir ve bosaltilan alan satiri siler ("—", 0 degil);
 * yeni harcamanin tarihi bu ayda bugun, gecmis ayda ayin son gunudur; kimlik
 * editor acilirken uretildigi icin cift kayit tek satir birakir; gider
 * girilmemis ayda tasarruf orani ve gerceklesen Kalan yazilmaz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    /** Kurulan VM'ler ([Env.vm]) testten sonra durdurulur (bkz. [TestMain]). */
    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    /** Fiyat tablosu elle surulur; bos tablo yeterli olan testler varsayilani kullanir. */
    private class BoardPrices(prices: List<Price> = emptyList()) : PriceRepository {
        val board = MutableStateFlow(PriceBoard(prices, "", PriceFreshness.Fresh))
        override fun observePrices(): Flow<PriceBoard> = board
        override fun observePriceHistory(assetKey: String): Flow<List<PricePoint>> = flowOf(emptyList())
        override suspend fun refresh(): Result<RefreshOutcome> = Result.success(RefreshOutcome.Fetched)
        override suspend fun setManualPrice(assetKey: String, value: Double) = Unit
        override suspend fun clearManualPrice(assetKey: String) = Unit
    }

    private inner class Env {
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
        fun vm() = main.track(PlanViewModel(plan, portfolio, prices, prefs, clock, dayTicks = days))

        suspend fun buyGram(id: String, date: KefeDate, quantity: Double = 1.0) {
            portfolio.upsertPosition(gram())
            portfolio.addTransaction(
                Transaction(
                    id = id,
                    positionId = GramId,
                    date = date,
                    side = TradeSide.Buy,
                    quantity = quantity,
                    unitPrice = 6_700.0,
                    addedByMemberId = "member_owner",
                ),
            )
        }

        suspend fun planItem(
            month: YearMonth,
            key: String,
            target: Double,
            mode: PlanTargetMode = PlanTargetMode.Quantity,
            goalId: String? = null,
            price: Double? = null,
            name: String = key,
        ): PlanItem = PlanItem(
            id = planItemId(month, key),
            month = month,
            assetKey = key,
            assetName = name,
            mode = mode,
            target = target,
            goalId = goalId,
            unitPriceAtPlan = price,
        ).also { plan.upsertPlanItem(it) }

        suspend fun goal(id: String, name: String) = portfolio.upsertGoal(
            Goal(
                id = id,
                name = name,
                iconKey = "car",
                amount = 500_000.0,
                unit = GoalUnit.Try,
                targetDate = KefeDate(2027, 10, 1),
                monthlyContribution = 50_000.0,
            ),
        )

        /** Depodaki (silinmemis) plan satirlari - [predicate] saglanana kadar beklenir. */
        suspend fun awaitItems(predicate: (List<PlanItem>) -> Boolean): List<PlanItem> =
            realTime { plan.observePlanItems().first(predicate) }

        suspend fun items(): List<PlanItem> = realTime { plan.observePlanItems().first() }

        suspend fun book(month: YearMonth): MonthBook = realTime { plan.observeMonthBook(month).first() }

        suspend fun positions(): List<Position> = realTime { portfolio.observeAllPositions().first() }

        suspend fun expense(id: String, date: KefeDate, amount: Double, category: ExpenseCategory = ExpenseCategory.Groceries) =
            plan.upsertExpense(ExpenseEntry(id = id, date = date, category = category, amount = amount))
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

    @Test
    fun `bu aya donusen secili gelecek ay birakilir - sayfa sonraki ayda ilerler`() = runTest {
        val env = Env()
        env.days.value = KefeDate(2026, 10, 31)
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.NextMonth)
        vm.awaitHeader { it.title == "Kasım 2026" && it.relation == MonthRelation.Future }

        env.days.value = KefeDate(2026, 11, 1)
        vm.awaitHeader { it.title == "Kasım 2026" && it.relation == MonthRelation.Current }
        // Secim Kasim'da kalsaydi Aralik'ta sayfa Kasim'da gecmis ay olarak asili kalirdi.
        env.days.value = KefeDate(2026, 12, 1)
        val dec = vm.awaitHeader { it.relation == MonthRelation.Current && it.title != "Kasım 2026" }
        assertEquals("Aralık 2026", dec.title)
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

    // --- Kalem editoru -------------------------------------------------------

    @Test
    fun `kaydet guncel alis fiyatiyla yazar ve sheet'i kapatir`() = runTest {
        val env = Env()
        // Satis (bid) 6.700, alis (ask) 6.800: kalem ALIS fiyatini saklar.
        env.prices.board.value = PriceBoard(listOf(gramPrice(bid = 6_700.0, ask = 6_800.0)), "", PriceFreshness.Fresh)
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        assertEquals(PlanTargetMode.Quantity, vm.itemEditor()?.mode)
        vm.onIntent(PlanIntent.ItemTarget("10"))
        assertEquals("≈ ₺68.000 (güncel fiyatla)", vm.itemEditor()?.estimateText())
        vm.onIntent(PlanIntent.SaveItem)
        // Sheet yazma bitmeden, ayni dokunusta kapanir: ikinci dokunus yazacak bir sey bulamaz.
        assertNull(vm.state.value.sheet)

        val saved = env.awaitItems { it.isNotEmpty() }.single()
        assertEquals(planItemId(October, "gold_gram"), saved.id)
        assertEquals(PlanTargetMode.Quantity, saved.mode)
        assertEquals(10.0, saved.target)
        assertEquals(6_800.0, saved.unitPriceAtPlan)
        assertEquals("Gram Altın", saved.assetName)

        val card = vm.awaitState { it.content.investment != null }.content.investment!!
        assertEquals("0/1 kalem · ₺68.000 planlandı", card.summary)
    }

    @Test
    fun `varligi degisen kalemin eski kimligi silinir`() = runTest {
        val env = Env()
        env.planItem(October, "gold_gram", 10.0)
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_k22"))
        vm.onIntent(PlanIntent.SaveItem)

        val items = env.awaitItems { list -> list.any { it.assetKey == "gold_k22" } }
        assertEquals(listOf(planItemId(October, "gold_k22")), items.map { it.id })
        assertEquals(10.0, items.single().target)
    }

    @Test
    fun `bu ay planli varlik secilince o kaleme gecilir`() = runTest {
        val env = Env()
        env.planItem(October, "gold_quarter", 3.0)
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemTarget("5"))
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_quarter"))
        val editor = assertNotNull(vm.itemEditor())
        assertEquals(planItemId(October, "gold_quarter"), editor.editingId)
        assertEquals("3", editor.targetText)
        assertTrue(editor.switchedToExisting)
    }

    @Test
    fun `ceyrekte kesirli hedef kaydedilmez`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_quarter"))
        vm.onIntent(PlanIntent.ItemTarget("2,5"))
        vm.onIntent(PlanIntent.SaveItem)

        val editor = assertNotNull(vm.itemEditor())
        assertEquals("Adetle alınan altında tam sayı girin.", editor.targetError)
        // Yazma hic baslamadi: depo bos.
        assertTrue(env.items().isEmpty())

        // Bos hedef ve secilmemis varlik da soylenir.
        vm.onIntent(PlanIntent.DismissSheet)
        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.SaveItem)
        val empty = assertNotNull(vm.itemEditor())
        assertEquals("Bir varlık seçin.", empty.assetError)
        assertEquals("Miktar girin.", empty.targetError)
    }

    /**
     * Varlik secilince liste tek satira iner; miktar alani hemen altinda kalir.
     * NEYDI: liste hep acikti ve telefonda miktar/hedef alanlari listenin
     * altinda kaliyordu - her kalemde en asagi kaydirmak gerekiyordu.
     */
    @Test
    fun `varlik secilince liste kapanir ve degistir ile yeniden acilir`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.AddItem)
        assertFalse(vm.itemEditor()!!.pickerCollapsed, "yeni kalemde liste acik baslar")

        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        assertTrue(vm.itemEditor()!!.pickerCollapsed)

        vm.onIntent(PlanIntent.ItemChangeAsset)
        assertFalse(vm.itemEditor()!!.pickerCollapsed)
        // Ayni varliga yeniden dokunmak listeyi kapatir, secimi degistirmez.
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        assertTrue(vm.itemEditor()!!.pickerCollapsed)
        assertEquals("gold_gram", vm.itemEditor()!!.assetKey)

        // Kod alani acikken liste acik kalir (alan listenin icinde).
        vm.onIntent(PlanIntent.ItemChangeAsset)
        vm.onIntent(PlanIntent.ItemOpenCode(AssetClass.Fund))
        assertFalse(vm.itemEditor()!!.pickerCollapsed)
    }

    @Test
    fun `yazilan hedef ilgisiz bir veri emisyonunda yerinde kalir`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        vm.onIntent(PlanIntent.ItemTarget("10"))

        // Arada bir alim: pozisyonlar yeniden yayilir, editorun baglami tazelenir.
        env.buyGram("tx_ara", KefeDate(2026, 10, 20))
        val after = vm.awaitState { state ->
            state.itemEditor?.options?.firstOrNull { it.assetKey == "gold_gram" }?.held == true
        }
        val editor = assertNotNull(after.itemEditor)
        assertEquals("10", editor.targetText)
        assertEquals("gold_gram", editor.assetKey)
    }

    @Test
    fun `secili cipe yeniden dokunmak hedefi korur, birimi ayni varlik da`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        vm.onIntent(PlanIntent.ItemTarget("10"))
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_gram"))
        assertEquals("10", vm.itemEditor()?.targetText)
        assertEquals(PlanTargetMode.Quantity, vm.itemEditor()?.mode)

        // gram -> 22 ayar gram: ikisi de gr, "10" ayni seyi ifade eder.
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_k22"))
        assertEquals("10", vm.itemEditor()?.targetText)

        // gram -> ceyrek: gr -> adet, sayi tasinmaz.
        vm.onIntent(PlanIntent.ItemSelectAsset("gold_quarter"))
        assertEquals("", vm.itemEditor()?.targetText)
        assertEquals(PlanTargetMode.Quantity, vm.itemEditor()?.mode)

        // Tutar yazilmissa her varlikta ayni anlami tasir.
        vm.onIntent(PlanIntent.ItemMode(PlanTargetMode.Amount))
        vm.onIntent(PlanIntent.ItemTarget("3000"))
        vm.onIntent(PlanIntent.ItemSelectAsset("cash"))
        assertEquals("3000", vm.itemEditor()?.targetText)
        assertEquals(PlanTargetMode.Amount, vm.itemEditor()?.mode)
    }

    @Test
    fun `bu ay planli fon kodu yazilinca kayit o kaleme gecer, hicbir sey yazilmaz`() = runTest {
        val env = Env()
        env.goal("g_car", "Araba")
        env.planItem(October, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, goalId = "g_car", name = "AFA")
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.AddItem)
        vm.onIntent(PlanIntent.ItemOpenCode(AssetClass.Fund))
        vm.onIntent(PlanIntent.ItemCode("afa"))
        vm.onIntent(PlanIntent.ItemTarget("5000"))
        vm.onIntent(PlanIntent.SaveItem)

        val editor = assertNotNull(vm.itemEditor())
        assertEquals(planItemId(October, "fund_afa"), editor.editingId)
        assertTrue(editor.switchedToExisting)
        assertEquals("g_car", editor.goalId)
        assertEquals("1000", editor.targetText)
        val stored = env.items().single()
        assertEquals(1_000.0, stored.target)
    }

    @Test
    fun `elde olmayan fon kalemi cipi secili acilir`() = runTest {
        val env = Env()
        env.planItem(October, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, name = "AFA")
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "fund_afa")))
        val editor = assertNotNull(vm.itemEditor())
        assertEquals("fund_afa", editor.assetKey)
        assertNull(editor.codeClass)
        assertTrue(editor.options.any { it.assetKey == "fund_afa" && !it.held })
        assertFalse(editor.isNew)
    }

    @Test
    fun `gecmis ayin kalemi kayitli fiyatini korur, bu ayinki guncel fiyati alir`() = runTest {
        val env = Env()
        env.prices.board.value = PriceBoard(listOf(gramPrice(bid = 6_700.0, ask = 6_800.0)), "", PriceFreshness.Fresh)
        env.goal("g_car", "Araba")
        env.planItem(September, "gold_gram", 10.0, price = 6_000.0)
        env.planItem(October, "gold_gram", 10.0, price = 6_000.0)
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        // Ekim: yalniz hedef degisir ama bu ayin kalemi bugunku alis fiyatini alir.
        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        vm.onIntent(PlanIntent.ItemGoal("g_car"))
        vm.onIntent(PlanIntent.SaveItem)
        val octSaved = env.awaitItems { list -> list.any { it.month == October && it.goalId == "g_car" } }
            .first { it.month == October }
        assertEquals(6_800.0, octSaved.unitPriceAtPlan)

        // Eylul: ayni degisiklik anlik goruntuyu KORUR - gecmis ayin skoru oynamasin.
        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitState { it.content.header.title == "Eylül 2026" && it.content.investment != null }
        vm.onIntent(PlanIntent.EditItem(planItemId(September, "gold_gram")))
        vm.onIntent(PlanIntent.ItemGoal("g_car"))
        vm.onIntent(PlanIntent.SaveItem)
        val sepSaved = env.awaitItems { list -> list.any { it.month == September && it.goalId == "g_car" } }
            .first { it.month == September }
        assertEquals(6_000.0, sepSaved.unitPriceAtPlan)
    }

    @Test
    fun `silinmis hedef editorde Hedefsiz olur`() = runTest {
        val env = Env()
        env.goal("g_car", "Araba")
        env.planItem(October, "gold_gram", 10.0, goalId = "g_car")
        val vm = env.vm()
        vm.awaitState { it.content.investment?.rows?.single()?.goalName == "Araba" }

        env.portfolio.deleteGoal("g_car")
        vm.awaitState { state -> state.content.investment?.rows?.single()?.goalName == null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        val editor = assertNotNull(vm.itemEditor())
        assertNull(editor.goalId)
        assertTrue(editor.goalChips.none { it.goalId == "g_car" })
    }

    // --- Al ------------------------------------------------------------------

    @Test
    fun `Al varligi ve kalan miktari gonderir, tutar satirinda miktar bos`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5))
        env.planItem(October, "gold_gram", 10.0)
        env.planItem(October, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount, name = "AFA")
        val vm = env.vm()
        val rows = vm.awaitState { it.content.investment?.rows?.size == 2 }.content.investment!!.rows
        assertTrue(rows.all { it.canBuy })

        vm.onIntent(PlanIntent.Buy(planItemId(October, "gold_gram")))
        // 1 / 10 gr alindi: kalan 9 gr.
        assertEquals(
            PlanEffect.OpenAddTransaction(AddTransactionPrefill("gold_gram", "9")),
            realTime { vm.effects.first() },
        )
        // Tutar satirinda fonun pay adedi uydurulmaz.
        vm.onIntent(PlanIntent.Buy(planItemId(October, "fund_afa")))
        assertEquals(
            PlanEffect.OpenAddTransaction(AddTransactionPrefill("fund_afa", null)),
            realTime { vm.effects.first() },
        )
    }

    @Test
    fun `Al yalniz bu ayda ve tamamlanmamis kalemde`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5))
        env.planItem(September, "gold_gram", 10.0)
        env.planItem(October, "gold_gram", 1.0)
        env.planItem(October, "silver_gram", 5.0)
        env.planItem(November, "gold_gram", 10.0)
        val vm = env.vm()

        val octRows = vm.awaitState { it.content.investment?.rows?.size == 2 }.content.investment!!.rows
        assertFalse(octRows.first { it.assetKey == "gold_gram" }.canBuy) // tamam
        assertTrue(octRows.first { it.assetKey == "silver_gram" }.canBuy)

        vm.onIntent(PlanIntent.PreviousMonth)
        val sepRows = vm.awaitState { it.content.header.title == "Eylül 2026" }.content.investment!!.rows
        assertFalse(sepRows.single().canBuy)

        vm.onIntent(PlanIntent.ThisMonth)
        vm.awaitState { it.content.header.title == "Ekim 2026" }
        vm.onIntent(PlanIntent.NextMonth)
        val novRows = vm.awaitState { it.content.header.title == "Kasım 2026" }.content.investment!!.rows
        assertEquals(PlanItemStatus.Upcoming, novRows.single().status)
        assertFalse(novRows.single().canBuy)
    }

    // --- Kopyala/Devir -------------------------------------------------------

    @Test
    fun `kopya varsayilan birakir`() = runTest {
        val env = Env()
        // Eylul 10 gr planlandi, 6 gr alindi.
        env.planItem(September, "gold_gram", 10.0)
        env.buyGram("tx_eylul", KefeDate(2026, 9, 10), quantity = 6.0)
        val vm = env.vm()
        val empty = vm.awaitState { it.content.emptyPlan != null }.content.emptyPlan!!
        assertEquals("Geçen ayı kopyala (Eylül)", empty.copyLabel)

        vm.onIntent(PlanIntent.OpenCopy)
        val draft = assertNotNull((vm.state.value.sheet as? PlanSheet.Copy)?.draft)
        assertTrue(draft.carry.isEmpty())
        assertEquals(4.0, draft.rows.single().shortfall)

        vm.onIntent(PlanIntent.ConfirmCopy)
        assertNull(vm.state.value.sheet)
        val copied = env.awaitItems { list -> list.any { it.month == October } }.first { it.month == October }
        assertEquals(10.0, copied.target)
        assertEquals(PlanEffect.Message("1 kalem kopyalandı."), realTime { vm.effects.first() })
    }

    @Test
    fun `tasinan eksik bir kez eklenir - ikinci dokunus ve ikinci kopya yok`() = runTest {
        val env = Env()
        env.planItem(September, "gold_gram", 10.0)
        env.buyGram("tx_eylul", KefeDate(2026, 9, 10), quantity = 6.0)
        val vm = env.vm()
        vm.awaitState { it.content.emptyPlan != null }

        vm.onIntent(PlanIntent.OpenCopy)
        vm.onIntent(PlanIntent.CopyCarry("gold_gram", carry = true))
        vm.onIntent(PlanIntent.ConfirmCopy)
        // Cift dokunus: sheet zaten kapali, ikinci onay hicbir sey yazmaz.
        vm.onIntent(PlanIntent.ConfirmCopy)

        val copied = env.awaitItems { list -> list.any { it.month == October } }.first { it.month == October }
        assertEquals(14.0, copied.target)

        // Kaynagin her varligi artik Ekim'de: giris kapanir, sayfa da acilmaz.
        val card = vm.awaitState { it.content.investment != null }.content.investment!!
        assertNull(card.copyLabel)
        vm.onIntent(PlanIntent.OpenCopy)
        assertNull(vm.state.value.sheet)
        assertEquals(14.0, env.items().first { it.month == October }.target)
    }

    @Test
    fun `gecmis ayin planli kalemine devir kayitli fiyati korur`() = runTest {
        val env = Env()
        env.prices.board.value = PriceBoard(listOf(gramPrice(bid = 6_700.0, ask = 6_800.0)), "", PriceFreshness.Fresh)
        // Agustos: 10 gr planlandi, 6 gr alindi (eksik 4); Eylul'de olmayan gumus da var.
        env.planItem(August, "gold_gram", 10.0, price = 5_900.0)
        env.planItem(August, "silver_gram", 5.0)
        env.buyGram("tx_agustos", KefeDate(2026, 8, 5), quantity = 6.0)
        env.planItem(September, "gold_gram", 10.0, price = 6_000.0)
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }

        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitState { it.content.header.title == "Eylül 2026" && it.content.investment?.copyLabel != null }
        vm.onIntent(PlanIntent.OpenCopy)
        vm.onIntent(PlanIntent.CopyCarry("gold_gram", carry = true))
        vm.onIntent(PlanIntent.ConfirmCopy)

        val sep = env.awaitItems { list -> list.any { it.month == September && it.target == 14.0 } }
            .first { it.month == September && it.assetKey == "gold_gram" }
        // Gecmis ayin TL agirligi bugunku 6.800 ile yeniden yazilmamali.
        assertEquals(6_000.0, sep.unitPriceAtPlan)
    }

    // --- Defter: gelir -------------------------------------------------------

    @Test
    fun `gelir kisi basina, bosaltilan alan satiri siler`() = runTest {
        val env = Env()
        env.plan.setIncome(September, "member_owner", IncomeKind.Salary, 80_000.0)
        val vm = env.vm()
        vm.awaitState { it.incomeOf("member_owner") == "—" }

        vm.onIntent(PlanIntent.EditIncome("member_owner"))
        assertEquals(80_000.0, vm.incomeEditor()?.lastSalary)
        vm.onIntent(PlanIntent.IncomeUseLastSalary)
        assertEquals("80000", vm.incomeEditor()?.salaryText)
        vm.onIntent(PlanIntent.IncomeSalary("85000"))
        vm.onIntent(PlanIntent.SaveIncome)
        assertNull(vm.state.value.sheet)

        val saved = vm.awaitState { it.incomeOf("member_owner") == "₺85.000" }
        // Gelir kisinin: esin satiri girilmedi olarak kalir.
        assertEquals("—", saved.incomeOf("member_partner"))

        vm.onIntent(PlanIntent.EditIncome("member_owner"))
        assertEquals("85000", vm.incomeEditor()?.salaryText)
        vm.onIntent(PlanIntent.IncomeSalary(""))
        vm.onIntent(PlanIntent.SaveIncome)
        vm.awaitState { it.incomeOf("member_owner") == "—" }
        assertTrue(env.book(October).incomes.isEmpty())
    }

    // --- Defter: gider -------------------------------------------------------

    @Test
    fun `yeni harcama bu ayda bugune, gecmis ayda ayin son gunune yazilir`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }

        vm.onIntent(PlanIntent.AddExpense)
        assertEquals(KefeDate(2026, 10, 22), vm.expenseEditor()?.date)
        vm.onIntent(PlanIntent.DismissSheet)

        vm.onIntent(PlanIntent.PreviousMonth)
        vm.awaitHeader { it.title == "Eylül 2026" }
        vm.onIntent(PlanIntent.AddExpense)
        assertEquals(KefeDate(2026, 9, 30), vm.expenseEditor()?.date)
    }

    @Test
    fun `gun donunce yeni harcama saatin degil gun akisinin gunune yazilir`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitHeader { it.title == "Ekim 2026" }
        // Saat 22 Ekim'de kalir; gun akisi 1 Kasim'a ilerler.
        env.days.value = KefeDate(2026, 11, 1)
        vm.awaitHeader { it.title == "Kasım 2026" }

        vm.onIntent(PlanIntent.AddExpense)
        assertEquals(KefeDate(2026, 11, 1), vm.expenseEditor()?.date)
    }

    @Test
    fun `cift kayit tek harcama birakir, eksik alan kaydetmez`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.expenses != null }

        vm.onIntent(PlanIntent.AddExpense)
        vm.onIntent(PlanIntent.ExpenseAmount("1500"))
        vm.onIntent(PlanIntent.SaveExpense)
        // Kategori secilmedi: sheet acik kalir, hata gorunur.
        assertEquals(true, vm.expenseEditor()?.categoryError)

        vm.onIntent(PlanIntent.ExpenseSelectCategory(ExpenseCategory.Groceries))
        vm.onIntent(PlanIntent.ExpenseNote(" market "))
        vm.onIntent(PlanIntent.SaveExpense)
        vm.onIntent(PlanIntent.SaveExpense)
        assertNull(vm.state.value.sheet)

        val card = vm.awaitState { it.content.expenses?.recent?.isNotEmpty() == true }.content.expenses!!
        assertEquals("₺1.500", card.totalLine)
        val book = env.book(October)
        assertEquals(1, book.expenses.size)
        assertEquals("market", book.expenses.single().note)
    }

    @Test
    fun `kendi kalemi yazilir, kartta ayri satir olur ve sonra cip olarak gelir`() = runTest {
        val env = Env()
        val vm = env.vm()
        vm.awaitState { it.content.expenses != null }

        vm.onIntent(PlanIntent.AddExpense)
        vm.onIntent(PlanIntent.ExpenseOpenNewCategory)
        vm.onIntent(PlanIntent.ExpenseAmount("12000"))
        vm.onIntent(PlanIntent.SaveExpense)
        // Ad yazilmadi: sheet acik, hata gorunur.
        assertEquals(true, vm.expenseEditor()?.categoryError)

        vm.onIntent(PlanIntent.ExpenseNewCategoryText("Tatil"))
        vm.onIntent(PlanIntent.SaveExpense)
        assertNull(vm.state.value.sheet)

        // Aylik gideri yok: kartta satir degil, plan disi giris.
        val card = vm.awaitState { state -> state.content.expenses?.recent?.any { it.title == "Tatil" } == true }
            .content.expenses!!
        assertTrue(card.recent.single { it.title == "Tatil" }.unplanned)
        assertEquals("Plan dışı ₺12.000", card.unplannedLine)
        assertEquals("c:Tatil", env.book(October).expenses.single().category.name)

        // Sonraki harcamada hazir cip; farkli yazim ayni kaleme duser.
        vm.onIntent(PlanIntent.AddExpense)
        assertEquals(listOf("Tatil"), vm.expenseEditor()?.customCategories?.map { it.label() })
        vm.onIntent(PlanIntent.ExpenseOpenNewCategory)
        vm.onIntent(PlanIntent.ExpenseNewCategoryText("tatil"))
        vm.onIntent(PlanIntent.ExpenseAmount("3000"))
        vm.onIntent(PlanIntent.SaveExpense)
        val merged = vm.awaitState { state -> state.content.expenses?.unplannedLine == "Plan dışı ₺15.000" }
        assertEquals(setOf("Tatil"), merged.content.expenses!!.recent.map { it.title }.toSet())

        // Butce sayfasi kalemi de listeler; kaydedilen butce kalemin kimligiyle yazilir.
        vm.onIntent(PlanIntent.EditBudget)
        val budget = assertNotNull((vm.state.value.sheet as? PlanSheet.Budget)?.editor)
        val trip = budget.categories.single { it.isCustom }
        vm.onIntent(PlanIntent.BudgetAmount(trip, "20000"))
        vm.onIntent(PlanIntent.SaveBudget)
        vm.awaitState { state -> state.content.expenses?.categories?.any { it.amounts == "₺15.000 / ₺20.000" } == true }
        assertEquals(listOf("eb_2026_10_c_tatil"), env.book(October).budgets.map { it.id })
    }

    @Test
    fun `butcede harcamasi olmayan kaleme butce konur, eski kalem ciple eklenir`() = runTest {
        val env = Env()
        // Agustos'ta kullanilan bir kalem: Ekim'in butce sayfasinda alani yok, cip olarak gelir.
        env.plan.upsertExpense(ExpenseEntry("e_agu", KefeDate(2026, 8, 10), ExpenseCategory.custom("Düğün hediyesi")!!, 5_000.0))
        val vm = env.vm()
        vm.awaitState { it.content.expenses != null }

        vm.onIntent(PlanIntent.EditBudget)
        fun editor() = assertNotNull((vm.state.value.sheet as? PlanSheet.Budget)?.editor)
        assertTrue(editor().categories.none { it.isCustom })
        assertEquals(listOf("Düğün hediyesi"), editor().olderCustom.map { it.label() })

        vm.onIntent(PlanIntent.BudgetOpenAdd)
        vm.onIntent(PlanIntent.BudgetAddConfirm)
        // Ad yok: alan acik kalir, hata gorunur.
        assertTrue(editor().addOpen)
        assertTrue(editor().addError)

        vm.onIntent(PlanIntent.BudgetAddText("Tatil"))
        vm.onIntent(PlanIntent.BudgetAddConfirm)
        assertFalse(editor().addOpen)
        assertEquals("Tatil", editor().categories.last().label())

        // Ayni kalem baska yazimla: ikinci alan acilmaz. Hazir kategorinin adi da.
        vm.onIntent(PlanIntent.BudgetOpenAdd)
        vm.onIntent(PlanIntent.BudgetAddText("tatil"))
        vm.onIntent(PlanIntent.BudgetAddConfirm)
        vm.onIntent(PlanIntent.BudgetOpenAdd)
        vm.onIntent(PlanIntent.BudgetAddText("market"))
        vm.onIntent(PlanIntent.BudgetAddConfirm)
        assertEquals(10, editor().categories.size)

        vm.onIntent(PlanIntent.BudgetOpenAdd)
        vm.onIntent(PlanIntent.BudgetAddExisting(editor().olderCustom.single()))
        assertTrue(editor().olderCustom.isEmpty())
        assertEquals(listOf("Tatil", "Düğün hediyesi"), editor().categories.filter { it.isCustom }.map { it.label() })

        val trip = editor().categories.first { it.label() == "Tatil" }
        vm.onIntent(PlanIntent.BudgetAmount(trip, "20000"))
        vm.onIntent(PlanIntent.SaveBudget)
        val card = vm.awaitState { state -> state.content.expenses?.categories?.any { it.label == "Tatil" } == true }
            .content.expenses!!
        // Harcamasi olmayan aylik gider: yalniz ayrilan tutar.
        assertEquals("₺20.000", card.categories.single { it.label == "Tatil" }.amounts)
        // Tutar yazilmayan kalem butce satiri acmaz.
        assertEquals(listOf("eb_2026_10_c_tatil"), env.book(October).budgets.map { it.id })
    }

    @Test
    fun `butce asimi metinle gorunur`() = runTest {
        val env = Env()
        env.plan.setBudgets(October, mapOf(ExpenseCategory.Groceries to 10_000.0))
        env.expense("e1", KefeDate(2026, 10, 5), 12_300.0)
        val vm = env.vm()
        val card = vm.awaitState { it.content.expenses?.categories?.singleOrNull()?.overText != null }.content.expenses!!
        assertEquals("₺2.300 aşıldı", card.categories.single().overText)
        assertEquals("1 kalem · harcanan ₺12.300 · ₺2.300 aşıldı", card.plannedLine)
    }

    // --- Defter: butce -------------------------------------------------------

    @Test
    fun `butce gecen aydan kopyalanir ve bu aya yazilir`() = runTest {
        val env = Env()
        env.plan.setBudgets(September, mapOf(ExpenseCategory.Groceries to 10_000.0, ExpenseCategory.Housing to 20_000.0))
        env.expense("e_eylul", KefeDate(2026, 9, 12), 9_000.0)
        val vm = env.vm()
        vm.awaitState { it.content.expenses != null }

        vm.onIntent(PlanIntent.EditBudget)
        val editor = assertNotNull(vm.budgetEditor())
        assertEquals(9_000.0, editor.lastSpent[ExpenseCategory.Groceries])
        vm.onIntent(PlanIntent.BudgetCopyLastMonth)
        assertEquals(
            mapOf(ExpenseCategory.Groceries to "10000", ExpenseCategory.Housing to "20000"),
            vm.budgetEditor()?.texts,
        )
        vm.onIntent(PlanIntent.BudgetAmount(ExpenseCategory.Groceries, "12000"))
        vm.onIntent(PlanIntent.SaveBudget)
        assertNull(vm.state.value.sheet)

        val card = vm.awaitState { it.content.expenses?.plannedTotal == "₺32.000" }.content.expenses!!
        assertEquals(2, card.categories.size)
        val budgets = env.book(October).budgets.associate { it.category to it.amount }
        assertEquals(mapOf(ExpenseCategory.Groceries to 12_000.0, ExpenseCategory.Housing to 20_000.0), budgets)
    }

    // --- Para akisi ----------------------------------------------------------

    @Test
    fun `gelir var gider yok - elde kalan ve dagilim yok`() = runTest {
        val env = Env()
        env.plan.setIncome(October, "member_owner", IncomeKind.Salary, 85_000.0)
        val vm = env.vm()
        val state = vm.awaitState { it.flowLine(FlowLineKind.Income)?.amount == "₺85.000" }
        assertNull(state.content.flow?.split)
        assertEquals("—", state.flowLine(FlowLineKind.Remaining)?.amount)
        assertEquals("—", state.flowLine(FlowLineKind.Expense)?.amount)
    }

    @Test
    fun `gelecek ayda satirlar plan, dagilim yok`() = runTest {
        val env = Env()
        env.plan.setIncome(October, "member_owner", IncomeKind.Salary, 85_000.0)
        env.plan.setIncome(November, "member_owner", IncomeKind.Salary, 85_000.0)
        env.expense("e_ekim", KefeDate(2026, 10, 5), 20_000.0)
        val vm = env.vm()
        val october = vm.awaitState { it.content.flow?.split != null }
        assertEquals("%76", october.content.flow?.split?.remainingText)
        assertEquals("₺65.000", october.flowLine(FlowLineKind.Remaining)?.amount)

        vm.onIntent(PlanIntent.NextMonth)
        val november = vm.awaitState { it.content.header.title == "Kasım 2026" }
        assertEquals("Plan", november.content.flow?.caption)
        assertEquals("₺85.000", november.flowLine(FlowLineKind.Income)?.amount)
        assertNull(november.content.flow?.split)
    }

    // --- Seri ve rozet -------------------------------------------------------

    @Test
    fun `hic plan yoksa seri karti yok`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5))
        val vm = env.vm()
        // Plan disi alim gorundu: defter yuklendi, yine de plan yok.
        val unplanned = vm.awaitState { it.content.extras != null }
        assertNull(unplanned.content.streak)

        env.planItem(October, "gold_gram", 1.0)
        val planned = vm.awaitState { it.content.streak != null }
        assertEquals("Bu ay düzenli", planned.content.streak?.headline)
        // Seri 1: plan kartinda "Seri N ay" yalniz 2'den itibaren.
        assertNull(planned.content.investment?.streakText)
    }

    @Test
    fun `rozet bu ayin acik kalemlerini sayar, secili ayi degil`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5))
        env.planItem(October, "gold_gram", 1.0) // tamam
        env.planItem(October, "silver_gram", 5.0) // acik
        env.planItem(September, "gold_gram", 10.0)
        env.planItem(September, "silver_gram", 10.0)
        env.planItem(September, "fund_afa", 1_000.0, mode = PlanTargetMode.Amount)
        val vm = env.vm()
        val october = vm.awaitState { it.content.investment?.rows?.size == 2 }
        assertEquals(1, october.content.currentMonthOpenCount)

        // Eylul'un uc acik kalemi rozete girmez.
        vm.onIntent(PlanIntent.PreviousMonth)
        val september = vm.awaitState { it.content.header.title == "Eylül 2026" }
        assertEquals(3, september.content.investment?.rows?.size)
        assertEquals(1, september.content.currentMonthOpenCount)
    }

    // --- Kalem silme ve plan disi alim -----------------------------------------

    /** Ekim'de 2 gr alinmis, 10 gr planli. */
    private suspend fun Env.boughtAndPlanned() {
        buyGram("tx_ekim", KefeDate(2026, 10, 5), quantity = 2.0)
        planItem(October, "gold_gram", 10.0, name = "Gram Altın")
    }

    @Test
    fun `alimi sayilmis kalem silinirken sorulur, yalniz plan silinince alim kalir`() = runTest {
        val env = Env()
        env.boughtAndPlanned()
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        vm.onIntent(PlanIntent.DeleteItem)
        val sheet = assertNotNull(vm.purchaseSheet(), "alim varken kalem hemen silinmez")
        assertEquals(planItemId(October, "gold_gram"), sheet.itemId)
        assertEquals(listOf("tx_ekim"), sheet.transactionIds)
        assertEquals("1 alım · 2 gr · ₺13.400", sheet.summary)
        assertTrue(env.items().isNotEmpty())

        vm.onIntent(PlanIntent.DeleteItemOnly)
        assertNull(vm.state.value.sheet)
        env.awaitItems { it.isEmpty() }
        // Alim varliklarda kalir ve plan disina duser.
        val extras = vm.awaitState { it.content.extras != null }.content.extras
        assertEquals("gold_gram", extras?.rows?.single()?.assetKey)
        assertEquals(2.0, env.positions().single { it.id == GramId }.quantity, 1e-9)
    }

    @Test
    fun `alimi da sil kalemi ve ayin alimini siler`() = runTest {
        val env = Env()
        env.boughtAndPlanned()
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        vm.onIntent(PlanIntent.DeleteItem)
        vm.onIntent(PlanIntent.DeletePurchases)
        assertNull(vm.state.value.sheet)

        env.awaitItems { it.isEmpty() }
        val gram = realTime {
            env.portfolio.observeAllPositions().first { list -> list.none { it.id == GramId && it.quantity > 0.0 } }
        }
        assertTrue(gram.none { it.id == GramId && it.quantity > 0.0 })
        // Plan disi alim da kalmaz.
        val after = vm.awaitState { it.content.investment == null }
        assertNull(after.content.extras)
    }

    @Test
    fun `alimi olmayan kalem sorulmadan silinir`() = runTest {
        val env = Env()
        env.planItem(October, "gold_gram", 10.0)
        val vm = env.vm()
        vm.awaitState { it.content.investment != null }

        vm.onIntent(PlanIntent.EditItem(planItemId(October, "gold_gram")))
        vm.onIntent(PlanIntent.DeleteItem)
        assertNull(vm.state.value.sheet)
        env.awaitItems { it.isEmpty() }
    }

    @Test
    fun `plan disi alim plana eklenince kalem alinan miktarla acilir`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5), quantity = 2.0)
        val vm = env.vm()
        vm.awaitState { it.content.extras != null }

        vm.onIntent(PlanIntent.OpenExtra("gold_gram"))
        val sheet = assertNotNull(vm.purchaseSheet())
        assertNull(sheet.itemId)
        assertEquals("Gram Altın", sheet.name)

        vm.onIntent(PlanIntent.AddPurchaseToPlan)
        val editor = assertNotNull(vm.itemEditor())
        assertEquals("gold_gram", editor.assetKey)
        assertEquals(PlanTargetMode.Quantity, editor.mode)
        assertEquals("2", editor.targetText)

        vm.onIntent(PlanIntent.SaveItem)
        val saved = env.awaitItems { it.isNotEmpty() }.single()
        assertEquals(2.0, saved.target, 1e-9)
        // Alim artik plana sayilir; plan disi kart kalkar.
        val after = vm.awaitState { it.content.investment != null && it.content.extras == null }
        assertEquals(1, after.content.investment?.rows?.size)
    }

    @Test
    fun `plan disi alim silinince varliktan da duser`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5), quantity = 2.0)
        val vm = env.vm()
        vm.awaitState { it.content.extras != null }

        vm.onIntent(PlanIntent.OpenExtra("gold_gram"))
        vm.onIntent(PlanIntent.DeletePurchases)
        val after = vm.awaitState { it.content.extras == null }
        assertNull(after.content.extras)
        assertTrue(env.positions().none { it.id == GramId && it.quantity > 0.0 })
    }

    @Test
    fun `hedefin ustundeki alim satiri kalemin editorunu acar`() = runTest {
        val env = Env()
        env.buyGram("tx_ekim", KefeDate(2026, 10, 5), quantity = 3.0)
        env.planItem(October, "gold_gram", 1.0)
        val vm = env.vm()
        vm.awaitState { it.content.extras != null }

        vm.onIntent(PlanIntent.OpenExtra("gold_gram"))
        assertEquals(planItemId(October, "gold_gram"), vm.itemEditor()?.editingId)
    }
}

private fun PlanViewModel.purchaseSheet(): PurchaseSheet? = (state.value.sheet as? PlanSheet.Purchase)?.sheet

/** Acik harcama editoru; baska sheet ya da hic sheet yoksa null. */
private fun PlanViewModel.expenseEditor(): ExpenseEditor? = (state.value.sheet as? PlanSheet.Expense)?.editor

private fun PlanViewModel.budgetEditor(): BudgetEditor? = (state.value.sheet as? PlanSheet.Budget)?.editor

private fun PlanViewModel.incomeEditor(): IncomeEditor? = (state.value.sheet as? PlanSheet.Income)?.editor

private fun PlanUiState.incomeOf(memberId: String): String? =
    content.flow?.incomeRows?.firstOrNull { it.memberId == memberId }?.amount

private fun PlanUiState.flowLine(kind: FlowLineKind): FlowLine? = content.flow?.lines?.firstOrNull { it.kind == kind }

private val October = YearMonth(2026, 10)
private val September = YearMonth(2026, 9)
private val November = YearMonth(2026, 11)
private val August = YearMonth(2026, 8)

private fun gramPrice(bid: Double, ask: Double) = Price(
    assetKey = "gold_gram",
    label = "Gram Altın",
    bid = bid,
    ask = ask,
    changePercent = null,
    timestamp = "",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Gold,
)

/** Acik kalem editoru; baska sheet ya da hic sheet yoksa null. */
private val PlanUiState.itemEditor: PlanItemEditor? get() = (sheet as? PlanSheet.Item)?.editor

private fun PlanViewModel.itemEditor(): PlanItemEditor? = state.value.itemEditor

/** Ready durumunda ve [predicate]'i saglayan ilk durum. */
private suspend fun PlanViewModel.awaitState(predicate: (PlanUiState) -> Boolean): PlanUiState =
    realTime { state.first { it.stage == PlanStage.Ready && predicate(it) } }

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
