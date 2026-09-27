package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceFreshness
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan sekmesinin basligi ve ay gecisinin sinirlari - saf metin ve turetim.
 *
 * Sayfa gecmis ve gelecek ayi da gosterir; "bu ay" diyen bir metin orada yanlis
 * aya isaret eder. Sinirlar veriden gelir: geriye ilk kaydin ayina kadar, ileriye
 * en fazla bir ay (onayli karar).
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

    // --- Yardimcilar ---------------------------------------------------------

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
