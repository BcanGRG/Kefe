package com.kefe.app.ui.screens.quick

import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.ui.screens.plan.RealOctober
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ana ekran widget'i ve hizli giris penceresi - kullanicinin GERCEK Ekim defteriyle
 * (7 Ekim). Tasarimdaki rakamlar buradan: bugun ₺270, gunluk pay ≈ ₺2.927, ayin %19'u.
 */
class QuickExpenseTest {

    private val books = listOf(RealOctober.book)
    private val today = RealOctober.today
    private val kk = RealOctober.kk

    @Test
    fun `widget bugunu, ayin gidisini ve en cok girilen dort kalemi yazar`() {
        val ui = expenseWidget(books, today)
        assertEquals("BUGÜN · 7 EKİM", ui.todayLabel)
        // Bugun: Oğuzhan çiçek ₺150 + Su ₺120.
        assertEquals("₺270", ui.todayTotal)
        assertEquals("2 harcama · günlük pay ≈ ₺2.927", ui.todayLine)
        assertEquals("2 harcama", ui.countText)

        val month = assertNotNull(ui.month)
        assertEquals("Ekim · ₺16.734 / ₺87.000", month.text)
        assertEquals("%19 · ayın %23'ü", month.ratioText)
        assertEquals(false, month.over)

        // Son 30 gunde: Kredi Kartı Limit 13, Market 6, Faturalar 3, Diğer 2 giris.
        assertEquals(listOf("Kredi Kartı Limit", "Market", "Faturalar", "Diğer"), ui.tiles.map { it.label })
        assertEquals(listOf("K", "M", "F", "D"), ui.tiles.map { it.initial })
        // Renk Harcamalar sayfasindaki sirayla; Diğer plan disi, soluk.
        assertEquals(listOf(0, 1, 3, -1), ui.tiles.map { it.colorIndex })

        assertEquals(listOf("Su", "Oğuzhan çiçek", "Elektrik"), ui.recent.map { it.title })
        assertEquals(listOf("11:49", "08:43", "dün"), ui.recent.map { it.whenText })
        assertEquals(listOf("₺120", "₺150", "₺565"), ui.recent.map { it.amount })
        assertEquals("Harcamalar · 30 harcama", ui.pageLink)
    }

    @Test
    fun `harcamasiz gunde ve aylik gidersiz ayda widget sade kalir`() {
        val quiet = expenseWidget(books, KefeDate(2026, 10, 8))
        assertEquals("₺0", quiet.todayTotal)
        assertEquals("Harcama yok · günlük pay ≈ ₺3.055", quiet.todayLine)

        val bare = expenseWidget(listOf(RealOctober.book.copy(budgets = emptyList())), today)
        assertNull(bare.month)
        assertEquals("2 harcama", bare.todayLine)
    }

    @Test
    fun `kalem sirasi once en cok girilenler, sonra aylik giderler ve hazir kalemler`() {
        val order = quickCategories(books, today).map { it.label() }
        assertEquals(
            listOf("Kredi Kartı Limit", "Market", "Faturalar", "Diğer", "Merve Pilates", "Konut/Kira", "Merve Pastacılık"),
            order.take(7),
        )
        // Hepsi bir kez; hazir dokuz da listede.
        assertEquals(order.size, order.distinct().size)
        ExpenseCategory.entries.forEach { assertTrue(it.label() in order, it.label()) }
    }

    @Test
    fun `not onerileri en cok tekrar eden once, tutar son girisinden`() {
        val notes = noteSuggestions(books, kk, today)
        // Su ve Dondurma ikiser kez; esitlikte en son girilen once. Sonra en yeni tekler.
        assertEquals(listOf("Su", "Dondurma", "Merve çiçek", "Merve yüz krem"), notes.map { it.note })
        assertEquals(listOf("₺120", "₺130", "₺680", "₺623,57"), notes.map { it.amountText })
        assertEquals(emptyList(), noteSuggestions(books, ExpenseCategory.Transport, today))
    }

    @Test
    fun `kalem satiri kalani, asimi ve plan disini soyler`() {
        val book = RealOctober.book
        assertEquals(QuickLineUi("Kredi Kartı Limit · kalan ₺7.575,46", QuickTone.Muted), quickLine(book, kk, 0.0))
        assertEquals(QuickLineUi("Kalan ₺7.575,46 → ₺7.455,46", QuickTone.Muted), quickLine(book, kk, 120.0))
        // Merve Pilates ₺3.000 / ₺3.000: kalan yok, yazilan asar.
        assertEquals(QuickLineUi("Kalan ₺0 · bununla ₺50 aşılır", QuickTone.Warning), quickLine(book, RealOctober.pilates, 50.0))
        // Faturalar zaten ₺58,32 asildi.
        assertEquals(QuickLineUi("₺58,32 aşıldı", QuickTone.Negative), quickLine(book, RealOctober.fatura, 0.0))
        assertEquals(QuickLineUi("₺58,32 aşıldı · bununla ₺158,32", QuickTone.Negative), quickLine(book, RealOctober.fatura, 100.0))
        assertEquals(QuickLineUi("Bu ayın aylık giderlerinde yok · plan dışı sayılır", QuickTone.Muted), quickLine(book, RealOctober.diger, 0.0))
        assertNull(quickLine(book.copy(budgets = emptyList()), kk, 0.0))

        assertEquals(QuickLineUi("Kalan ₺7.455,46", QuickTone.Muted), quickSavedLine(book, kk, 120.0))
        assertEquals(QuickLineUi("₺158,32 aşıldı", QuickTone.Negative), quickSavedLine(book, RealOctober.fatura, 100.0))
        assertEquals(QuickLineUi("Plan dışı harcama", QuickTone.Muted), quickSavedLine(book, RealOctober.diger, 150.0))
    }

    @Test
    fun `tus takimi virgulu bir kez, kurusu iki hane alir`() {
        fun type(vararg keys: QuickKey) = keys.fold("") { text, key -> typeKey(text, key) }
        val d = { c: Char -> QuickKey.Digit(c) }
        assertEquals("120", type(d('1'), d('2'), d('0')))
        assertEquals("5", type(d('0'), d('5')))
        assertEquals("0,5", type(QuickKey.Comma, d('5')))
        assertEquals("12,5", type(d('1'), d('2'), QuickKey.Comma, QuickKey.Comma, d('5')))
        assertEquals("12,50", type(d('1'), d('2'), QuickKey.Comma, d('5'), d('0'), d('9')))
        assertEquals("123456789", type(*"1234567890".map(d).toTypedArray()))
        assertEquals("12", type(d('1'), d('2'), QuickKey.Comma, QuickKey.Back))
        assertEquals("", type(d('1'), d('2'), QuickKey.Clear))

        assertEquals("₺0", shownAmount(""))
        assertEquals("₺1.050", shownAmount("1050"))
        assertEquals("₺12,", shownAmount("12,"))
        assertEquals("₺1.234.567,5", shownAmount("1234567,5"))
    }

    @Test
    fun `yuzde iyelik eki okunusa uyar`() {
        assertEquals(
            listOf("'ü", "'u", "'si", "'sı", "'ü", "'ı", "'u", "'si", "'i", "'ü", "'i"),
            listOf("%23", "%19", "%50", "%6", "%100", "%40", "%10", "%2", "%70", "%3", "%5").map(::possessive),
        )
    }
}
