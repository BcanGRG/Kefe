package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanItemProgress
import com.kefe.app.domain.model.PlanItemStatus
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan sekmesinin basligi, ay gecisinin sinirlari ve kalem metinleri - saf metin
 * ve turetim.
 *
 * Sayfa gecmis ve gelecek ayi da gosterir; "bu ay" diyen bir metin orada yanlis
 * aya isaret eder. Sinirlar veriden gelir: geriye ilk kaydin ayina kadar, ileriye
 * en fazla bir ay (onayli karar). Skor tamamlanmadan "%100" yazmaz; kaydedilen
 * fiyat ALIS fiyatidir (kulcede istisna).
 */
class PlanTextTest {

    private val today = KefeDate(2026, 10, 22)
    private val oct = YearMonth(2026, 10)

    // --- Baslik alt satiri ---------------------------------------------------

    @Test
    fun `bu ay kalan gunu soyler`() {
        assertEquals("Ekim · 9 gün kaldı", headerSubtitle(oct, today))
        assertEquals("Ekim · 30 gün kaldı", headerSubtitle(oct, KefeDate(2026, 10, 1)))
    }

    @Test
    fun `ayin son gunu - sifir gun kaldi yazmaz`() {
        assertEquals("Ekim · son gün", headerSubtitle(oct, KefeDate(2026, 10, 31)))
        // Subat 28 cekerken de.
        assertEquals("Şubat · son gün", headerSubtitle(YearMonth(2027, 2), KefeDate(2027, 2, 28)))
    }

    @Test
    fun `gecmis ay olcuyu soyler, bu ay demez`() {
        assertEquals("Geçmiş ay · alımlar işlem tarihine göre sayılır.", headerSubtitle(YearMonth(2026, 9), today))
        assertEquals("Gelecek ay · alımlar ay başlayınca sayılır.", headerSubtitle(YearMonth(2026, 11), today))
    }

    @Test
    fun `baslik ayi, iliskiyi ve gecisleri tasir`() {
        val bounds = YearMonth(2026, 9)..YearMonth(2026, 11)
        val current = planHeader(oct, today, bounds)
        assertEquals("Ekim 2026", current.title)
        assertEquals(MonthRelation.Current, current.relation)
        assertEquals(true, current.canGoBack)
        assertEquals(true, current.canGoForward)
        assertEquals(false, current.showThisMonthChip)

        val first = planHeader(YearMonth(2026, 9), today, bounds)
        assertEquals(MonthRelation.Past, first.relation)
        assertEquals(false, first.canGoBack)
        assertEquals(true, first.showThisMonthChip)

        val last = planHeader(YearMonth(2026, 11), today, bounds)
        assertEquals(MonthRelation.Future, last.relation)
        assertEquals(false, last.canGoForward)
        assertEquals(true, last.showThisMonthChip)
    }

    // --- Sinirlar ------------------------------------------------------------

    @Test
    fun `veri yokken gecen aydan gelecek aya`() {
        val bounds = planMonthBounds(today, emptyList(), emptyList(), emptyList())
        assertEquals(YearMonth(2026, 9), bounds.start)
        assertEquals(YearMonth(2026, 11), bounds.endInclusive)
    }

    @Test
    fun `bas ilk plan, ilk islem ve ilk defter ayinin en eskisi`() {
        val plan = listOf(item(YearMonth(2026, 7)))
        val trades = listOf(trade(KefeDate(2026, 5, 3)))
        val books = listOf(expenseBook(KefeDate(2026, 3, 15)))

        assertEquals(YearMonth(2026, 7), planMonthBounds(today, plan, emptyList(), emptyList()).start)
        assertEquals(YearMonth(2026, 5), planMonthBounds(today, plan, trades, emptyList()).start)
        // Yalniz harcamasi olan bir ay da (plansiz, islemsiz) ulasilabilir.
        assertEquals(YearMonth(2026, 3), planMonthBounds(today, emptyList(), emptyList(), books).start)
        assertEquals(YearMonth(2026, 3), planMonthBounds(today, plan, trades, books).start)
        // Son her zaman gelecek ay - gelecekteki bir plan satiri sinirlari itmez.
        assertEquals(
            YearMonth(2026, 11),
            planMonthBounds(today, listOf(item(YearMonth(2027, 1))), trades, books).endInclusive,
        )
    }

    @Test
    fun `bos defter ayi siniri genisletmez`() {
        val bounds = planMonthBounds(today, emptyList(), emptyList(), listOf(MonthBook(YearMonth(2025, 1))))
        assertEquals(YearMonth(2026, 9), bounds.start)
    }

    // --- Gosterilen ay -------------------------------------------------------

    @Test
    fun `secim yoksa bu ay`() {
        assertEquals(oct, inputs(selection = null).month)
    }

    @Test
    fun `sinirlar icindeki secim korunur`() {
        assertEquals(YearMonth(2026, 9), inputs(selection = YearMonth(2026, 9)).month)
        assertEquals(YearMonth(2026, 11), inputs(selection = YearMonth(2026, 11)).month)
        val withTrade = inputs(selection = YearMonth(2026, 8), trades = listOf(trade(KefeDate(2026, 8, 5))))
        assertEquals(YearMonth(2026, 8), withTrade.month)
    }

    @Test
    fun `sinirlarin disina dusen secim bu aya kirpilir`() {
        // Agustos islemi silindi: secim Agustos'ta kaldi ama artik sinir Eylul.
        val aug = inputs(selection = YearMonth(2026, 8))
        assertEquals(oct, aug.month)
        assertEquals(true, aug.selectionClamped)
        // Iki ay ileri hic olamaz.
        val dec = inputs(selection = YearMonth(2026, 12))
        assertEquals(oct, dec.month)
        assertEquals(true, dec.selectionClamped)
    }

    @Test
    fun `secim yoksa ya da sinir icindeyse kirpilmis sayilmaz`() {
        assertEquals(false, inputs(selection = null).selectionClamped)
        assertEquals(false, inputs(selection = YearMonth(2026, 9)).selectionClamped)
        assertEquals(false, inputs(selection = YearMonth(2026, 11)).selectionClamped)
    }

    @Test
    fun `defter gosterilen ayin defteri`() {
        val aug = expenseBook(KefeDate(2026, 8, 10))
        val sep = expenseBook(KefeDate(2026, 9, 12))
        val shown = inputs(selection = YearMonth(2026, 9), books = listOf(aug, sep))
        assertEquals(sep, shown.book)
        assertEquals(aug, shown.previousBook)
        // Defteri olmayan ay bos defterle gelir, null degil.
        val empty = inputs(selection = null, books = listOf(aug, sep))
        assertEquals(MonthBook(oct), empty.book)
        assertEquals(sep, empty.previousBook)
    }

    // --- Kalem metinleri -----------------------------------------------------

    @Test
    fun `ilerleme metni birimiyle`() {
        assertEquals("6 / 10 gr", progressText(progress("gold_gram", PlanTargetMode.Quantity, 10.0, actual = 6.0)))
        assertEquals("2,5 / 10 gr", progressText(progress("gold_gram", PlanTargetMode.Quantity, 10.0, actual = 2.5)))
        assertEquals("₺2.000 / ₺3.000", progressText(progress("fund_afa", PlanTargetMode.Amount, 3_000.0, actual = 2_000.0)))
        assertEquals("300 / 500 $", progressText(progress("usd_try", PlanTargetMode.Quantity, 500.0, actual = 300.0)))
        assertEquals("1 / 3 adet", progressText(progress("gold_quarter", PlanTargetMode.Quantity, 3.0, actual = 1.0)))
    }

    @Test
    fun `notlar - fazla ve satis, onek aya gore`() {
        val over = progress("gold_gram", PlanTargetMode.Quantity, 10.0, actual = 12.0, over = 2.0)
        assertEquals(listOf("+2 gr fazla"), rowNotes(over, MonthRelation.Current))

        val sold = progress("gold_gram", PlanTargetMode.Quantity, 10.0, actual = 6.0, soldQuantity = 1.0, soldTl = 6_700.0)
        assertEquals(listOf("Bu ay 1 gr satıldı"), rowNotes(sold, MonthRelation.Current))
        // Gecmis ayda "Bu ay" yanlis aya isaret eder.
        assertEquals(listOf("Ay içinde 1 gr satıldı"), rowNotes(sold, MonthRelation.Past))

        val amount = progress(
            "fund_afa", PlanTargetMode.Amount, 3_000.0,
            actual = 3_500.0, over = 500.0, soldQuantity = 3.0, soldTl = 1_200.0,
        )
        assertEquals(listOf("+₺500 fazla", "Bu ay ₺1.200 satıldı"), rowNotes(amount, MonthRelation.Current))

        assertEquals(emptyList(), rowNotes(progress("gold_gram", PlanTargetMode.Quantity, 10.0, actual = 3.0), MonthRelation.Current))
    }

    @Test
    fun `durum kelimeleri`() {
        assertEquals("Tamam", PlanItemStatus.Done.label())
        assertEquals("Kısmen", PlanItemStatus.Partial.label())
        assertEquals("Bekliyor", PlanItemStatus.Waiting.label())
        assertEquals("Kaçtı", PlanItemStatus.Missed.label())
        assertEquals("Yakında", PlanItemStatus.Upcoming.label())
    }

    @Test
    fun `skor tamamlanmadan yuzde yuz yazmaz`() {
        assertEquals("%99", scoreText(0.996))
        assertEquals("%100", scoreText(1.0))
        assertEquals("%72", scoreText(0.72))
        assertEquals("%0", scoreText(0.0))
        assertEquals("—", scoreText(null))
    }

    @Test
    fun `kopyalama etiketleri - gecen ay ve daha eski ay`() {
        val sep = YearMonth(2026, 9)
        val aug = YearMonth(2026, 8)
        assertEquals("Geçen aydan kopyala", copyButtonLabel(sep, oct))
        assertEquals("Ağustos planından kopyala", copyButtonLabel(aug, oct))
        assertEquals("Geçen ayı kopyala (Eylül)", emptyCopyLabel(sep, oct))
        assertEquals("Ağustos planını kopyala", emptyCopyLabel(aug, oct))
    }

    @Test
    fun `birim etiketleri`() {
        assertEquals("gr", planUnitLabel("gold_gram"))
        assertEquals("gr", planUnitLabel("gold_jewelry_916"))
        assertEquals("adet", planUnitLabel("gold_quarter"))
        assertEquals("pay", planUnitLabel("fund_afa"))
        assertEquals("adet", planUnitLabel("stock_thyao.is"))
        assertEquals("$", planUnitLabel("usd_try"))
        assertEquals("€", planUnitLabel("eur_try"))
        assertEquals("₺", planUnitLabel("cash"))
        assertEquals("", planUnitLabel("bilinmeyen"))
    }

    // --- Editor okuyuculari --------------------------------------------------

    @Test
    fun `yazilan kod anahtara cevrilir`() {
        val fund = PlanItemEditor(month = oct, codeClass = AssetClass.Fund, codeText = "afa")
        assertEquals("fund_afa", fund.resolvedKey())
        val stock = PlanItemEditor(month = oct, codeClass = AssetClass.Stock, codeText = "THYAO.IS")
        assertEquals("stock_thyao.is", stock.resolvedKey())
        // Bicime uymayan kod (tek harf, bosluk, isaret) anahtar uretmez.
        assertEquals(null, fund.copy(codeText = "A").resolvedKey())
        assertEquals(null, fund.copy(codeText = "A F").resolvedKey())
        assertEquals(null, fund.copy(codeText = "").resolvedKey())
        // Cip secimi koddan once gelir.
        assertEquals("gold_gram", PlanItemEditor(month = oct, assetKey = "gold_gram").resolvedKey())
    }

    @Test
    fun `hedef alani birimi ve tahmini`() {
        val gram = PlanItemEditor(month = oct, assetKey = "gold_gram", targetText = "10", unitPrice = 6_733.0)
        assertEquals("gr", gram.unitLabel())
        assertEquals("Miktar", gram.targetLabel())
        assertEquals("≈ ₺67.330 (güncel fiyatla)", gram.estimateText())
        // Tutar hedefinde rakam zaten TL; fiyat bilinmiyorsa tahmin uydurulmaz.
        val amount = gram.copy(mode = PlanTargetMode.Amount)
        assertEquals("₺", amount.unitLabel())
        assertEquals("Tutar", amount.targetLabel())
        assertEquals(null, amount.estimateText())
        assertEquals(null, gram.copy(unitPrice = null).estimateText())
        // Nakitte Miktar/Tutar secimi yok.
        assertEquals(false, PlanItemEditor(month = oct, assetKey = "cash").showModeSwitch())
        assertEquals(true, gram.showModeSwitch())
    }

    // --- Fiyat ---------------------------------------------------------------

    @Test
    fun `alis fiyati - gramda satis kotasyonu, kulcede alis, nakitte bir`() {
        val board = PriceBoard(
            listOf(
                price("gold_gram", bid = 6_700.0, ask = 6_800.0),
                price("gold_bullion", bid = 6_600.0, ask = 6_750.0),
                price("gold_k22", bid = 6_100.0, ask = 6_200.0),
            ),
            "",
            PriceFreshness.Fresh,
        )
        assertEquals(6_800.0, buyPriceOf("gold_gram", board, emptyList()))
        assertEquals(6_600.0, buyPriceOf("gold_bullion", board, emptyList()))
        // Takinin fiyati ayarinin gram kotasyonu.
        assertEquals(6_200.0, buyPriceOf("gold_jewelry_916", board, emptyList()))
        assertEquals(1.0, buyPriceOf("cash", board, emptyList()))
    }

    @Test
    fun `tabloda yoksa eldeki pozisyonun fiyati, o da yoksa null`() {
        val empty = PriceBoard(emptyList(), "", PriceFreshness.Offline)
        val afa = Position(
            id = "pos_fund_afa",
            name = "AFA · Ak Portföy Altın",
            assetClass = AssetClass.Fund,
            quantity = 100.0,
            unit = QuantityUnit.Share,
            unitPrice = 12.5,
            value = 1_250.0,
            cost = 1_000.0,
        )
        assertEquals(12.5, buyPriceOf("fund_afa", empty, listOf(afa)))
        assertEquals(null, buyPriceOf("fund_xyz", empty, listOf(afa)))
    }

    // --- Yardimcilar ---------------------------------------------------------

    private fun progress(
        key: String,
        mode: PlanTargetMode,
        target: Double,
        actual: Double,
        over: Double = 0.0,
        soldQuantity: Double = 0.0,
        soldTl: Double = 0.0,
    ) = PlanItemProgress(
        item = PlanItem(
            id = planItemId(oct, key),
            month = oct,
            assetKey = key,
            assetName = key,
            mode = mode,
            target = target,
        ),
        boughtQuantity = actual,
        boughtTl = actual,
        soldQuantity = soldQuantity,
        soldTl = soldTl,
        actual = actual,
        ratio = (actual / target).coerceAtMost(1.0),
        remaining = (target - actual).coerceAtLeast(0.0),
        over = over,
        plannedTl = null,
        status = PlanItemStatus.Partial,
    )

    private fun price(key: String, bid: Double, ask: Double) = Price(
        assetKey = key,
        label = key,
        bid = bid,
        ask = ask,
        changePercent = null,
        timestamp = "",
        source = PriceSource.FreeMarket,
        assetClass = AssetClass.Gold,
    )

    private fun item(month: YearMonth) = PlanItem(
        id = planItemId(month, "gold_gram"),
        month = month,
        assetKey = "gold_gram",
        assetName = "Gram Altın",
        mode = PlanTargetMode.Quantity,
        target = 10.0,
    )

    private fun trade(date: KefeDate) = Transaction(
        id = "tx_${date.year}_${date.month}_${date.day}",
        positionId = "pos_gold_gram",
        date = date,
        side = TradeSide.Buy,
        quantity = 1.0,
        unitPrice = 6_700.0,
        addedByMemberId = "member_owner",
    )

    private fun expenseBook(date: KefeDate) = MonthBook(
        month = YearMonth.of(date),
        expenses = listOf(
            ExpenseEntry(id = "e_${date.month}", date = date, category = ExpenseCategory.Groceries, amount = 1_500.0),
        ),
    )

    private fun inputs(
        selection: YearMonth?,
        trades: List<Transaction> = emptyList(),
        books: List<MonthBook> = emptyList(),
    ) = PlanInputs(
        selection = selection,
        today = today,
        items = emptyList(),
        transactions = trades,
        positions = emptyList(),
        goals = emptyList(),
        assignments = emptyMap(),
        members = emptyList(),
        board = PriceBoard(emptyList(), "", PriceFreshness.Offline),
        activeMemberId = null,
        books = books,
    )
}
