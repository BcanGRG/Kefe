package com.kefe.app.ui.screens.summary

import com.kefe.app.domain.repository.PriceFreshness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Fiyat seridi ve yan navigasyonun fiyat satiri.
 *
 * NEYDI: alinamayan fiyat "Çevrimdışı · Son bilinen fiyatlarla" yaziyordu, ustu
 * cizili bulut ikonuyla - ucretsiz fiyat ucunun tokezlemesi internetin, hatta
 * hesabin koptugu gibi okunuyordu. Yan navigasyon da fiyat saatini "Eşit ·
 * 14:32'te güncellendi" diye yaziyordu: hem "eşit" kelimesi fiyat icin
 * kullaniliyor hem de bulunma eki her saatte "'te" idi.
 */
class PriceBannerTest {

    @Test
    fun `alinamayan fiyat ne oldugunu soyler`() {
        val lines = assertNotNull(priceBannerLines(PriceFreshness.Offline))
        assertEquals("Fiyatlar alınamadı · son bilinen fiyatlarla", lines.line1)
        assertEquals("Bağlantı gelince fiyatlar güncellenir", lines.line2)
    }

    @Test
    fun `eski fiyat tek satir`() {
        val lines = assertNotNull(priceBannerLines(PriceFreshness.Stale))
        assertEquals("Fiyatlar 2 saatten eski", lines.line1)
        assertNull(lines.line2)
    }

    /** Taze fiyatta ve ilk istek yoldayken uyaracak bir sey yok. */
    @Test
    fun `taze ya da yolda serit yok`() {
        assertNull(priceBannerLines(PriceFreshness.Fresh))
        assertNull(priceBannerLines(PriceFreshness.Loading))
    }

    @Test
    fun `hicbir fiyat metninde Cevrimdisi ya da esit yok`() {
        PriceFreshness.entries.forEach { freshness ->
            val texts = listOfNotNull(
                priceBannerLines(freshness)?.line1,
                priceBannerLines(freshness)?.line2,
                SummaryUiState(freshness = freshness, pricesUpdatedAt = "14:32").navPriceLine,
            )
            texts.forEach { text ->
                assertFalse("Çevrimdışı" in text, "'$text'")
                // "Eşit" hesabin kelimesi; fiyat satirinda gecmemeli.
                assertFalse("Eşit" in text || "eşit" in text, "'$text'")
            }
        }
    }

    @Test
    fun `yan navigasyon fiyat satiri`() {
        fun line(freshness: PriceFreshness, at: String = "") =
            SummaryUiState(freshness = freshness, pricesUpdatedAt = at).navPriceLine

        assertEquals("Fiyatlar 14:32'de güncellendi", line(PriceFreshness.Fresh, "14:32"))
        assertEquals("Fiyatlar 12:05'te güncellendi", line(PriceFreshness.Fresh, "12:05"))
        assertEquals("Fiyatlar 09:40'ta güncellendi", line(PriceFreshness.Fresh, "09:40"))
        assertEquals("Fiyatlar güncel", line(PriceFreshness.Fresh))
        assertEquals("Fiyatlar alınamadı · son bilinen fiyatlar", line(PriceFreshness.Offline))
        assertEquals("Fiyatlar 2 saatten eski", line(PriceFreshness.Stale))
        assertEquals("Fiyatlar alınıyor…", line(PriceFreshness.Loading))
    }
}
