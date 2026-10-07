package com.kefe.app.ui.screens.plan

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthBook
import com.kefe.app.domain.model.YearMonth
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

    private val oct = RealOctober.oct
    private val today = RealOctober.today

    private val kk = RealOctober.kk
    private val pilates = RealOctober.pilates
    private val yemek = RealOctober.yemek
    private val halisaha = RealOctober.halisaha
    private val market = RealOctober.market
    private val fatura = RealOctober.fatura
    private val diger = RealOctober.diger

    private val book = RealOctober.book

    @Test
    fun `tumu ayin toplamini ve kalemlere gore dagilimi yazar`() {
        val page = expensesPage(book, today, ExpenseFilter.All, ExpenseSort.Date)
        assertEquals("Harcamalar", page.title)
        assertEquals("Ekim 2026 · 24 gün kaldı", page.subtitle)
        val summary = assertIs<ExpensesSummaryUi.Overview>(page.summary)
        assertEquals("₺17.754,24", summary.total)
        assertEquals("30 harcama · plan dışı ₺1.020", summary.line)
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
    fun `tumu butun aylik giderlere gore gidisi yazar`() {
        // Kullanici karari: BUTUN aylik giderler (₺87.000), kira gibi harcamasi girilmemisler dahil.
        val budget = assertNotNull(assertIs<ExpensesSummaryUi.Overview>(expensesPage(book, today, ExpenseFilter.All, ExpenseSort.Date).summary).budget)
        assertEquals("₺16.734,24", budget.spent)
        assertEquals("₺87.000", budget.limit)
        assertEquals("%19 harcandı", budget.spentText)
        assertEquals("bugün · %23", budget.todayText)
        assertEquals(PaceUi("Plana uygun: harcanan %19, ayın geçen kısmı %23.", PaceTone.OnTrack), budget.pace)
        assertEquals(
            listOf(
                StatUi("KALAN", "₺70.265,76", "aylık giderlerden"),
                // 70.265,76 / 24 = 2.927,74 -> asagi.
                StatUi("GÜNDE", "≈ ₺2.927", "kalan 24 gün için"),
                // Kalem kalem: Faturalar'in asimini digerlerinin artani kapatmaz.
                StatUi("AŞILAN", "₺58,32", "Faturalar", negative = true),
                StatUi("PLAN DIŞI", "₺1.020", "3 harcama"),
            ),
            budget.stats,
        )
        // Aylik gider girilmemis ayda kart yok.
        val bare = expensesPage(book.copy(budgets = emptyList()), today, ExpenseFilter.All, ExpenseSort.Date)
        assertNull(assertIs<ExpensesSummaryUi.Overview>(bare.summary).budget)
    }

    @Test
    fun `sinirin altinda giden kalem plana uygun der`() {
        // Dışarıda yemek ₺1.175 / ₺5.000 = %24, ayin %23'u gecti.
        val s = assertIs<ExpensesSummaryUi.Budgeted>(expensesPage(book, today, ExpenseFilter.Category(yemek), ExpenseSort.Date).summary)
        assertEquals(PaceTone.OnTrack, s.pace?.tone)
        assertEquals("Plana uygun: harcanan %24, ayın geçen kısmı %23.", s.pace?.text)
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
        assertEquals(PaceTone.Fast, s.pace?.tone)
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
    fun `kalem listesi gun gun, en yeni gun ve en son giris ustte, saat sol sutunda`() {
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
        // Kompakt: saat sol sutunda, satir tek satir (alt satir yok).
        assertEquals(listOf("17:32", "17:04", "09:08"), fifth.items.map { it.lead })
        assertTrue(fifth.items.all { it.sub.isEmpty() && !it.leadEarly })
        // Gununden once girilen: saat yerine giris gunu, altin renkli.
        val avokado = page.groups.first().items.single()
        assertEquals("29 Eyl", avokado.lead)
        assertTrue(avokado.leadEarly)
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
        assertNull(avokado.lead, "tumunde sol sutun yok, kalem alt satirda")
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
        assertNull(kkPage.ranked.first().lead)
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
        assertEquals(PaceTone.Over, s.pace?.tone)
        assertEquals(StatUi("AŞIM", "₺58,32", "sınırın üstünde", negative = true), s.stats.first())
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

}
