package com.kefe.app.domain.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Haftalik/aylik degisim CIHAZDAKI gecmisten hesaplanir; kaynaklar gecmis
 * vermiyor (serbest piyasa yalniz bugunu, TCMB gunluk bulteni). Fonlar istisna:
 * TEFAS'in bir aylik serisi gecmise yazildigi icin ilk gunden dolular.
 *
 * Bu testlerin kilitledigi kural TEK: veri yoksa null. Sifir donmek "degismedi"
 * demek olurdu ve depo uydurma sayi uretmez.
 */
class PeriodChangeTest {

    private val today = KefeDate(2026, 8, 1)

    private fun daysAgo(days: Int, price: Double): PricePoint =
        PricePoint(dateOf(today.toEpochDay() - days), price)

    /** Test icin gun sayisindan tarihe - Howard Hinnant'in civil_from_days'i. */
    private fun dateOf(epochDay: Long): KefeDate {
        val z = epochDay + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        val y = yoe + era * 400L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = if (mp < 10L) mp + 3L else mp - 9L
        return KefeDate((y + if (m <= 2L) 1L else 0L).toInt(), m.toInt(), d.toInt())
    }

    @Test
    fun tarihYardimcisiToEpochDayIleTutarli() {
        // Testin kendi yardimcisi yanlissa butun dosya yanlis olur.
        assertEquals(today, dateOf(today.toEpochDay()))
        assertEquals(KefeDate(2026, 7, 25), dateOf(today.toEpochDay() - 7))
        assertEquals(KefeDate(2026, 7, 2), dateOf(today.toEpochDay() - 30))
    }

    @Test
    fun tamGunVarsaHesaplanir() {
        val history = listOf(daysAgo(7, 100.0), daysAgo(1, 104.0))
        val change = periodChangePercent(history, latest = 110.0, today, daysBack = 7, tolerance = 3)
        assertEquals(10.0, change!!, 1e-9)
    }

    @Test
    fun tamGunYokAmaToleransIcindeyse() {
        // Hafta sonu ya da uygulamanin acilmadigi bir gun: 9 gun onceki fiyat
        // 7 gunluk pencerede kabul edilir.
        val history = listOf(daysAgo(9, 200.0))
        val change = periodChangePercent(history, latest = 210.0, today, daysBack = 7, tolerance = 3)
        assertEquals(5.0, change!!, 1e-9)
    }

    @Test
    fun toleransDisiVeriKullanilmaz() {
        // 40 gun onceki fiyati "haftalik degisim" diye yazmak uydurmaktir.
        val history = listOf(daysAgo(40, 200.0))
        assertNull(periodChangePercent(history, latest = 210.0, today, daysBack = 7, tolerance = 3))
    }

    @Test
    fun pencereninENYENIsiSecilir() {
        // 8 gun onceki fiyat, 10 gun onceki fiyattan daha dogru bir kiyastir.
        val history = listOf(daysAgo(10, 100.0), daysAgo(8, 105.0))
        val change = periodChangePercent(history, latest = 105.0, today, daysBack = 7, tolerance = 3)
        assertEquals(0.0, change!!, 1e-9)
    }

    @Test
    fun bugunkuNoktaHaftalikSayilmaz() {
        // Elde yalniz bugunun fiyati varsa hafta BILINMIYOR - sifir degil.
        val history = listOf(daysAgo(0, 100.0), daysAgo(1, 99.0))
        assertNull(periodChangePercent(history, latest = 100.0, today, daysBack = 7, tolerance = 3))
    }

    @Test
    fun bosGecmisNullDoner() {
        assertNull(periodChangePercent(emptyList(), latest = 100.0, today, 7, 3))
    }

    @Test
    fun gecersizFiyatNullDoner() {
        assertNull(periodChangePercent(listOf(daysAgo(7, 0.0)), latest = 100.0, today, 7, 3))
        assertNull(periodChangePercent(listOf(daysAgo(7, 100.0)), latest = 0.0, today, 7, 3))
    }

    @Test
    fun dususNegatifYazilir() {
        val history = listOf(daysAgo(7, 100.0))
        val change = periodChangePercent(history, latest = 92.0, today, daysBack = 7, tolerance = 3)
        assertTrue(change!! < 0.0)
        assertEquals(-8.0, change, 1e-9)
    }

    @Test
    fun haftaVeAyAyriPencerelerdenGelir() {
        // Bir aylik seri: hafta 7 gun oncesine, ay 30 gun oncesine bakar.
        val history = listOf(daysAgo(30, 100.0), daysAgo(7, 110.0), daysAgo(1, 118.0))
        val changes = periodChangesOf(history, latest = 121.0, today)
        assertEquals(10.0, changes.week!!, 1e-9)
        assertEquals(21.0, changes.month!!, 1e-9)
    }

    @Test
    fun ayYokkenHaftaYineDolar() {
        // Cihaz bir haftadir acik ama bir aylik gecmis yok: hafta yazilir, ay "—".
        val history = listOf(daysAgo(8, 100.0), daysAgo(1, 104.0))
        val changes = periodChangesOf(history, latest = 105.0, today)
        assertEquals(5.0, changes.week!!, 1e-9)
        assertNull(changes.month)
    }

    // --- Grup agirligi --------------------------------------------------------

    @Test
    fun grupDegisimiDegerAgirliklidir() {
        // ₺900.000'lik altinin %1'i ile ₺1.000'lik fonun %10'u ayni agirlikta
        // DEGILDIR. Yuzde ortalamasi (5,5) yanlis cevaptir.
        val weighted = weightedPeriodTotal(
            listOf(900_000.0 to 1.0, 1_000.0 to 10.0),
        )!!
        assertTrue(
            abs(weighted.percent - 1.01) < 0.01,
            "beklenen ~1,01 idi: ${weighted.percent}",
        )
    }

    @Test
    fun grupTutariVeYuzdesiAyniHesaptan() {
        // TL ve yuzde AYNI cozumden gelir; ayri hesaplansalar yuvarlamada
        // ayrisir ve ekranda "+₺100 · +0,00%" gibi celiskiler cikardi.
        val total = weightedPeriodTotal(listOf(1_100.0 to 10.0))!!
        assertEquals(100.0, total.amount, 1e-9)
        assertEquals(10.0, total.percent, 1e-9)
    }

    @Test
    fun yuzdesiBilinmeyenPozisyonHesabaGirmez() {
        // Bilinmeyeni sifir saymak grubun degisimini sulandirirdi.
        val onlyKnown = weightedPeriodTotal(listOf(1_000.0 to 10.0))!!
        val withUnknown = weightedPeriodTotal(
            listOf(1_000.0 to 10.0, 9_000.0 to null),
        )!!
        assertEquals(onlyKnown.percent, withUnknown.percent, 1e-9)
        assertEquals(onlyKnown.amount, withUnknown.amount, 1e-9)
    }

    @Test
    fun hicbiriBilinmiyorsaGrupNullDoner() {
        assertNull(weightedPeriodTotal(listOf(1_000.0 to null, 2_000.0 to null)))
        assertNull(weightedPeriodTotal(emptyList()))
    }

    // --- Tek pozisyon ---------------------------------------------------------

    @Test
    fun tekPozisyonunTLKarsiligi() {
        // %10 artmis ₺1.100'luk bir varlik ₺100 kazandirmistir - sadelestirilmis
        // "deger x yuzde" (110) YANLIS olurdu, cunku yuzde eski degere gore.
        val total = periodTotalOf(value = 1_100.0, percent = 10.0)!!
        assertEquals(100.0, total.amount, 1e-9)
        assertEquals(10.0, total.percent, 1e-9)
    }

    @Test
    fun yuzdesiBilinmeyenPozisyonNullDoner() {
        assertNull(periodTotalOf(value = 1_000.0, percent = null))
    }
}
