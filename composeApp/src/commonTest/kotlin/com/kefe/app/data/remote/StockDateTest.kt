package com.kefe.app.data.remote

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.toEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Borsa serisinin zaman damgasi UTC SANIYESIDIR, tarih degil.
 *
 * Damga borsanin `gmtoffset`'i eklenmeden boluneydi New York kapanisi (20:00
 * UTC) ertesi gune duserdi - gecmis bir gun kayinca haftalik degisim yanlis
 * noktadan olculur ve varlik detayindaki egri bir gun ileri kayar.
 */
class StockDateTest {

    @Test
    fun newYorkKapanisiKENDIGununeDuser() {
        // 2026-07-31 16:00 New York = 20:00 UTC. Ofset -4 saat (yaz saati).
        val closeUtc = 1_785_528_000L
        assertEquals(KefeDate(2026, 7, 31), dateOfEpochSeconds(closeUtc, -4 * 3600L))
    }

    @Test
    fun istanbulKapanisiKENDIGununeDuser() {
        // 2026-07-31 18:00 Istanbul = 15:00 UTC. Ofset +3 saat.
        val closeUtc = 1_785_510_000L
        assertEquals(KefeDate(2026, 7, 31), dateOfEpochSeconds(closeUtc, 3 * 3600L))
    }

    /**
     * Ofsetin isini yaptigi yer GUN SINIRIDIR.
     *
     * BIST ve ABD seanslari UTC gunune sigdigi icin gundelik ornekte iki sonuc
     * ayni cikar - bu, ofsetin gereksiz oldugu anlamina GELMEZ. Sinirda ayrisir:
     * damga UTC gununun son yarim saatindeyse borsanin yerel takvimi coktan
     * ertesi gundedir. `gmtoffset` okunmazsa o gun bir geri kayar ve haftalik
     * degisim yanlis noktadan olculur.
     */
    @Test
    fun gunSinirindaOfsetGunuBelirler() {
        // 2026-07-31 23:30 UTC = Istanbul'da 2026-08-01 02:30.
        val utc = 1_785_540_600L
        assertEquals(KefeDate(2026, 8, 1), dateOfEpochSeconds(utc, 3 * 3600L))
        assertEquals(KefeDate(2026, 7, 31), dateOfEpochSeconds(utc, 0L))
    }

    @Test
    fun gunSayisindanTakvimeCevrim() {
        // Sifirinci gun Unix baslangicidir.
        assertEquals(KefeDate(1970, 1, 1), dateOfEpochDay(0L))
        // Artik gun.
        assertEquals(KefeDate(2024, 2, 29), dateOfEpochDay(19_782L))
        // Baslangictan ONCE - negatif gun de dogru cozulmeli.
        assertEquals(KefeDate(1969, 12, 31), dateOfEpochDay(-1L))
    }

    @Test
    fun cevrimKefeDateIleTutarli() {
        // Iki yon birbirinin tersi olmali; biri degisirse digeri de degismeli.
        val date = KefeDate(2026, 7, 31)
        assertEquals(date, dateOfEpochDay(date.toEpochDay()))
    }
}
