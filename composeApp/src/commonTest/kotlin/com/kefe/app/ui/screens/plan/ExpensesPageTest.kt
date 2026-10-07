package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.ExpenseBudget
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Harcamalar sayfasi - kullanicinin GERCEK Ekim 2026 defteriyle (telefondan, 7 Ekim):
 * 30 harcama, 10 aylik gider. Tasarimdaki rakamlar buradan.
 */
class ExpensesPageTest {

    private val oct = YearMonth(2026, 10)
    private val today = KefeDate(2026, 10, 7)

    private val kk = ExpenseCategory.custom("Kredi Kartı Limit")!!
    private val pilates = ExpenseCategory.custom("Merve Pilates")!!
    private val yemek = ExpenseCategory.custom("Dışarıda yemek")!!
    private val halisaha = ExpenseCategory.custom("Halisaha")!!
    private val market = ExpenseCategory.Groceries
    private val fatura = ExpenseCategory.Bills
    private val diger = ExpenseCategory.Other

    private val book = MonthBook(
        month = oct,
        budgets = listOf(
            budget(ExpenseCategory.Housing, 25_000.0),
            budget(kk, 15_000.0),
            budget(ExpenseCategory.custom("Merve Pastacılık")!!, 12_000.0),
            budget(ExpenseCategory.custom("Bolu Gezisi")!!, 10_000.0),
            budget(market, 7_000.0),
            budget(yemek, 5_000.0),
            budget(ExpenseCategory.custom("Bağış")!!, 5_000.0),
            budget(ExpenseCategory.Transport, 3_000.0),
            budget(pilates, 3_000.0),
            budget(fatura, 2_000.0),
        ),
        expenses = listOf(
            e(1, market, 66.38, "Macrocenter muz", at(1, 15, 27)),
            e(1, kk, 190.0, "Damacana su", at(1, 15, 28)),
            e(1, kk, 1_090.0, "Merve kiyafet", at(1, 18, 27)),
            e(1, kk, 140.0, "Dis fircasi basligi", at(1, 18, 28)),
            e(1, kk, 810.97, "Merve sampuan", at(1, 18, 29)),
            e(1, market, 205.0, "Pazar alisverisi", at(1, 18, 29, 30)),
            e(1, pilates, 3_000.0, null, at(1, 18, 30)),
            e(1, kk, 260.0, "Dondurma", at(1, 21, 55)),
            e(2, diger, 500.0, "Ecem düğün", at(2, 9, 34)),
            e(2, market, 207.0, null, at(2, 9, 59)),
            e(2, kk, 130.0, "Merve ayakkabı bağcık", at(2, 18, 36)),
            e(2, kk, 1_800.0, "Merve mont", at(2, 18, 58)),
            e(2, market, 2_024.0, "Gimat", at(2, 19, 58)),
            e(2, market, 100.0, "Bim", at(2, 20, 15)),
            e(3, fatura, 800.0, "İnternet", at(3, 12, 28)),
            e(3, market, 101.5, null, at(3, 13, 9)),
            e(4, kk, 200.0, null, at(4, 10, 27)),
            e(5, kk, 200.0, "Su", at(5, 9, 8)),
            e(5, kk, 623.57, "Merve yüz krem", at(5, 17, 4)),
            e(5, kk, 130.0, "Dondurma", at(5, 17, 32)),
            e(6, kk, 680.0, "Merve çiçek", at(6, 8, 15)),
            e(6, fatura, 693.32, "Su", at(6, 10, 35)),
            e(6, fatura, 565.0, "Elektrik", at(6, 14, 26)),
            e(7, diger, 150.0, "Oğuzhan çiçek", at(7, 8, 43)),
            e(7, kk, 120.0, "Su", at(7, 11, 49)),
            // Eylul sonunda Ekim'e girildi - eski kural ayin son gununu yaziyordu.
            e(31, market, 277.5, null, sept(27, 19, 36)),
            e(31, market, 95.0, null, sept(28, 20, 10)),
            e(31, kk, 1_050.0, "Avokado", sept(29, 21, 10)),
            e(31, yemek, 1_175.0, "Masabasi burak", sept(30, 21, 49)),
            e(31, halisaha, 370.0, null, sept(30, 21, 49, 30)),
        ),
    )

    @Test
    fun `tumu ayin toplamini ve kalemlere gore dagilimi yazar`() {
        val page = expensesPage(book, today, ExpenseFilter.All, ExpenseSort.Date)
        assertEquals("Harcamalar", page.title)
        assertEquals("Ekim 2026 · 24 gün kaldı", page.subtitle)
        val summary = assertIs<ExpensesSummaryUi.Overview>(page.summary)
        assertEquals("₺17.754,24", summary.total)
        assertEquals("30 harcama · aylık giderlerden ₺16.734,24 · plan dışı ₺1.020", summary.line)
        val first = summary.split.first()
        assertEquals("Kredi Kartı Limit", first.label)
        assertEquals("₺7.424,54", first.amount)
        assertEquals("%42", first.share)
        assertEquals(ExpenseFilter.Category(kk), first.filter)
        val unplanned = summary.split.last()
        assertEquals("₺1.020", unplanned.amount)
        assertEquals(ExpenseFilter.Unplanned, unplanned.filter)
        assertTrue("Diğer" in unplanned.label && "Halisaha" in unplanned.label)
        assertNull(page.daily, "gunluk grafik yalniz tek kalemde")
        assertEquals("30 harcama", page.countLabel)
        assertEquals("Harcama ekle", page.addLabel)
        assertNull(page.addCategory)
    }

    @Test
    fun `cipler tumu, harcanan kalemler ve plan disi - secili olan isaretli`() {
        val chips = expensesPage(book, today, ExpenseFilter.Category(kk), ExpenseSort.Date).chips
        assertEquals(ExpenseChipUi("Tümü", 30, ExpenseFilter.All, selected = false), chips.first())
        assertEquals(ExpenseChipUi("Kredi Kartı Limit", 14, ExpenseFilter.Category(kk), selected = true), chips[1])
        assertEquals(ExpenseChipUi("Market", 8, ExpenseFilter.Category(market)), chips[2])
        assertEquals(ExpenseChipUi("Plan dışı", 3, ExpenseFilter.Unplanned), chips.last())
    }

    @Test
    fun `kalem sayfasi sinira karsi, hiz uyarisi ve gunluk pay`() {
        val page = expensesPage(book, today, ExpenseFilter.Category(kk), ExpenseSort.Date)
        assertEquals("Kredi Kartı Limit", page.title)
        assertEquals("Ekim 2026 · aylık gider", page.subtitle)
        val s = assertIs<ExpensesSummaryUi.Budgeted>(page.summary)
        assertEquals("₺7.424,54", s.spent)
        assertEquals("₺15.000", s.limit)
        assertEquals("%49 harcandı", s.spentText)
        assertEquals("bugün · %23", s.todayText)
        assertEquals(7f / 31f, assertNotNull(s.todayRatio), 1e-6f)
        assertEquals("Hızlı gidiyor: harcanan %49, ayın geçen kısmı %23.", s.pace?.text)
        assertEquals(false, s.pace?.over)
        assertEquals(
            listOf(
                StatUi("KALAN", "₺7.575,46", "sınıra kadar"),
                // 7.575,46 / 24 gun = 315,64 -> asagi yuvarlanir: guvenli rehber.
                StatUi("GÜNDE", "≈ ₺315", "kalan 24 gün için"),
                StatUi("ORTALAMA", "₺530", "14 harcama"),
                StatUi("EN BÜYÜK", "₺1.800", "Merve mont · 2 Eki"),
            ),
            s.stats,
        )
        assertEquals("Bu kaleme harcama ekle", page.addLabel)
        assertEquals(kk, page.addCategory)
    }

    @Test
    fun `kalem listesi gun gun, en yeni gun ve en son giris ustte, saatiyle`() {
        val page = expensesPage(book, today, ExpenseFilter.Category(kk), ExpenseSort.Date)
        assertEquals(
            listOf(
                "31 Ekim Cumartesi", "7 Ekim Çarşamba", "6 Ekim Salı", "5 Ekim Pazartesi",
                "4 Ekim Pazar", "2 Ekim Cuma", "1 Ekim Perşembe",
            ),
            page.groups.map { it.title },
        )
        assertEquals(listOf("ileri tarihli", "bugün", null), page.groups.take(3).map { it.tag })
        val fifth = page.groups.single { it.title == "5 Ekim Pazartesi" }
        assertEquals("₺953,57", fifth.total)
        assertEquals(listOf("Dondurma", "Merve yüz krem", "Su"), fifth.items.map { it.title })
        assertEquals(listOf("17:32", "17:04", "09:08"), fifth.items.map { it.sub })
        // Gununden once girilen: saat yerine giris gunu.
        assertEquals("29 Eylül'de girildi", page.groups.first().items.single().sub)
        // Tek kalemde ust yazi tekrar olurdu.
        assertNull(page.groups.first().hint)
        val noNote = page.groups.single { it.title == "4 Ekim Pazar" }.items.single()
        assertEquals("Not girilmedi", noNote.title)
        assertTrue(noNote.titleMuted)
        assertNull(noNote.initial, "tek kalemde harf kutusu cizilmez")
    }

    @Test
    fun `tumunde ileri tarihli gun ne zaman girildigini soyler, satir kalemini yazar`() {
        val page = expensesPage(book, today, ExpenseFilter.All, ExpenseSort.Date)
        val ahead = page.groups.first()
        assertEquals("31 Ekim Cumartesi", ahead.title)
        assertEquals("ileri tarihli", ahead.tag)
        assertEquals("27–30 Eylül'de girildi", ahead.hint)
        val avokado = ahead.items.single { it.title == "Avokado" }
        assertEquals("Kredi Kartı Limit", avokado.sub)
        assertEquals("K", avokado.initial)
        assertEquals(0, avokado.colorIndex, "en cok harcanan kalem ilk renk")
        val halisahaLine = ahead.items.single { it.title == "Halisaha" }
        assertTrue(halisahaLine.unplanned)
        assertEquals(-1, halisahaLine.colorIndex)
        // Notsuz satir: kalem adi baslikta, giris ani altta.
        assertEquals("30 Eylül'de girildi", halisahaLine.sub)
    }

    @Test
    fun `gunluk grafik bugunu ve ileri tarihli gunu ayirir`() {
        val daily = assertNotNull(expensesPage(book, today, ExpenseFilter.Category(kk), ExpenseSort.Date).daily)
        assertEquals(31, daily.bars.size)
        assertEquals("en yüksek 1 Eki · ₺2.490,97", daily.peak)
        assertEquals(1f, daily.bars[0].ratio)
        assertEquals(DayBarKind.Spent, daily.bars[0].kind)
        assertEquals(DayBarKind.Empty, daily.bars[2].kind)
        assertEquals(DayBarKind.Today, daily.bars[6].kind)
        assertEquals(DayBarKind.Ahead, daily.bars[30].kind)
        assertEquals(listOf("1", "8", "15", "22", "31"), daily.axis)
    }

    @Test
    fun `tutara gore duz liste, buyukten kucuge`() {
        val kkPage = expensesPage(book, today, ExpenseFilter.Category(kk), ExpenseSort.Amount)
        assertTrue(kkPage.groups.isEmpty())
        assertEquals("Merve mont", kkPage.ranked.first().title)
        assertEquals("2 Eki", kkPage.ranked.first().sub)
        val all = expensesPage(book, today, ExpenseFilter.All, ExpenseSort.Amount)
        assertEquals("Merve Pilates", all.ranked.first().title)
        assertEquals("Kredi Kartı Limit", all.ranked[2].sub.substringAfter(" · "))
    }

    @Test
    fun `plan disi suzgeci kalemlere boler`() {
        val page = expensesPage(book, today, ExpenseFilter.Unplanned, ExpenseSort.Date)
        assertEquals("Plan dışı", page.title)
        val s = assertIs<ExpensesSummaryUi.Unbudgeted>(page.summary)
        assertEquals("₺1.020", s.total)
        assertEquals(listOf("Diğer" to "₺650", "Halisaha" to "₺370"), s.split.map { it.label to it.amount })
        assertEquals(ExpenseFilter.Category(halisaha), s.split.last().filter)
        assertEquals("3 harcama", page.countLabel)
    }

    @Test
    fun `gecmis ayda gunluk pay ve hiz uyarisi yok, asim yazilir`() {
        val page = expensesPage(book, KefeDate(2026, 11, 3), ExpenseFilter.Category(fatura), ExpenseSort.Date)
        val s = assertIs<ExpensesSummaryUi.Budgeted>(page.summary)
        assertNull(s.todayRatio)
        assertEquals("₺58,32 aşıldı", s.pace?.text)
        assertEquals(true, s.over)
        assertEquals(StatUi("AŞIM", "₺58,32", "sınırın üstünde"), s.stats.first())
        assertTrue(s.stats.none { it.label == "GÜNDE" })
        assertEquals("Ekim 2026", expensesPage(book, KefeDate(2026, 11, 3), ExpenseFilter.All, ExpenseSort.Date).subtitle)
    }

    @Test
    fun `harcamasi olmayan kalem bos durum yazar ve cipi secili gelir`() {
        val bolu = ExpenseCategory.custom("Bolu Gezisi")!!
        val page = expensesPage(book, today, ExpenseFilter.Category(bolu), ExpenseSort.Date)
        assertEquals("Bu kaleme bu ay harcama girilmedi.", page.emptyText)
        assertNull(page.countLabel)
        assertNull(page.daily)
        assertEquals(ExpenseChipUi("Bolu Gezisi", 0, ExpenseFilter.Category(bolu), selected = true), page.chips.last())
        val nov = expensesPage(MonthBook(YearMonth(2026, 11)), today, ExpenseFilter.All, ExpenseSort.Date)
        assertEquals("Kasım'da harcama girilmedi.", nov.emptyText)
    }

    @Test
    fun `suzgec gezinme anahtarina gidip doner`() {
        listOf(ExpenseFilter.All, ExpenseFilter.Unplanned, ExpenseFilter.Category(kk), ExpenseFilter.Category(market))
            .forEach { assertEquals(it, ExpenseFilter.of(it.key())) }
        assertNull(ExpenseFilter.All.key())
    }

    @Test
    fun `haftanin gunu`() {
        assertEquals("Perşembe", KefeDate(1970, 1, 1).weekdayName())
        assertEquals("Çarşamba", KefeDate(2026, 10, 7).weekdayName())
        assertEquals("Cumartesi", KefeDate(2026, 10, 31).weekdayName())
        assertEquals("Pazar", KefeDate(2026, 10, 4).weekdayName())
    }

    // --- Yardimcilar ---------------------------------------------------------

    private var seq = 0

    private fun e(day: Int, category: ExpenseCategory, amount: Double, note: String?, createdAt: Long) = ExpenseEntry(
        id = "x${seq++}",
        date = KefeDate(2026, 10, day),
        category = category,
        amount = amount,
        note = note,
        createdAt = createdAt,
    )

    private fun budget(category: ExpenseCategory, amount: Double) =
        ExpenseBudget("b_${category.name}", oct, category, amount)

    /** Ekim'in [day]'i, Turkiye saatiyle hh:mm(:ss). */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) = istanbul(KefeDate(2026, 10, day), hour, minute, second)

    private fun sept(day: Int, hour: Int, minute: Int, second: Int = 0) = istanbul(KefeDate(2026, 9, day), hour, minute, second)

    private fun istanbul(date: KefeDate, hour: Int, minute: Int, second: Int): Long =
        date.toEpochDay() * 86_400_000L + ((hour * 60L + minute) * 60L + second) * 1_000L - 3L * 3_600_000L
}
