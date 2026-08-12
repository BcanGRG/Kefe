package com.kefe.app.domain.model

import com.kefe.app.data.remote.parseIsoDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Takvimin kenarlari.
 *
 * Ucu de sessizce YANLIS TARIH uretiyordu, hata atmadan:
 *   - `plusMonths` ay sonu kiskaci yapmiyordu: 31 Ocak + 1 ay = "31 Subat".
 *     toEpochDay gunu 1..31'e cekip kabul ediyor, yani tarih baska bir gune
 *     tasiniyor; ustelik bu gecersiz tarih hedefler tablosuna yazilabiliyordu.
 *   - `parseIsoDate` ay uzunlugunu dogrulamiyordu: "2026-02-30" kabul.
 */
class DateEdgeTest {

    @Test
    fun ayEkleyinceGunAYSONUNAKisilir() {
        assertEquals(KefeDate(2026, 2, 28), KefeDate(2026, 1, 31).plusMonths(1))
        assertEquals(KefeDate(2026, 4, 30), KefeDate(2026, 3, 31).plusMonths(1))
    }

    @Test
    fun ARTIKYildaSubat29Cekiyor() {
        assertEquals(KefeDate(2028, 2, 29), KefeDate(2028, 1, 31).plusMonths(1))
        // 2100 artik yil DEGIL (100'e bolunur, 400'e bolunmez).
        assertEquals(KefeDate(2100, 2, 28), KefeDate(2100, 1, 31).plusMonths(1))
        // 2000 artik yil (400'e bolunur).
        assertEquals(KefeDate(2000, 2, 29), KefeDate(2000, 1, 31).plusMonths(1))
    }

    @Test
    fun kisilmaGEREKMEYENTarihDegismez() {
        assertEquals(KefeDate(2026, 9, 15), KefeDate(2026, 8, 15).plusMonths(1))
        assertEquals(KefeDate(2027, 1, 31), KefeDate(2026, 12, 31).plusMonths(1))
    }

    @Test
    fun geriyeAyEklemekDeKisar() {
        assertEquals(KefeDate(2026, 2, 28), KefeDate(2026, 3, 31).plusMonths(-1))
    }

    /** Kisilan tarih GECERLIDIR: gidip gelince ayni gune duser. */
    @Test
    fun kisilanTarihGERCEKBirGundur() {
        val subat = KefeDate(2026, 1, 31).plusMonths(1)

        assertEquals(subat, kefeDateOfEpochDay(subat.toEpochDay()))
    }

    // --- epochDay cevrimi ---------------------------------------------------

    @Test
    fun gunSayisiCevrimiTERSINEDeCalisir() {
        val gunler = listOf(
            KefeDate(1970, 1, 1),
            KefeDate(2026, 8, 12),
            KefeDate(2000, 2, 29),
            KefeDate(1969, 12, 31),
            KefeDate(2100, 3, 1),
        )
        for (gun in gunler) {
            assertEquals(gun, kefeDateOfEpochDay(gun.toEpochDay()), "cevrim bozuldu: $gun")
        }
    }

    @Test
    fun sifirinciGun1970BasiDir() {
        assertEquals(KefeDate(1970, 1, 1), kefeDateOfEpochDay(0L))
        assertEquals(KefeDate(1969, 12, 31), kefeDateOfEpochDay(-1L))
    }

    // --- ISO cozumleme ------------------------------------------------------

    @Test
    fun gecerliISOTarihiCozulur() {
        assertEquals(KefeDate(2026, 7, 31), parseIsoDate("2026-07-31"))
    }

    @Test
    fun OLMAYANGunReddedilir() {
        assertNull(parseIsoDate("2026-02-30"), "30 Subat kabul edildi")
        assertNull(parseIsoDate("2026-04-31"))
        // 2026 artik yil degil: 29 Subat da yok.
        assertNull(parseIsoDate("2026-02-29"))
    }

    @Test
    fun ARTIKYildaSubat29Kabul() {
        assertEquals(KefeDate(2028, 2, 29), parseIsoDate("2028-02-29"))
    }

    @Test
    fun bozukBicimReddedilir() {
        assertNull(parseIsoDate("31.07.2026"))
        assertNull(parseIsoDate(null))
        assertNull(parseIsoDate("2026-13-01"))
    }

    @Test
    fun daysInMonthTemelDegerler() {
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2028, 2))
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(31, daysInMonth(2026, 12))
    }
}
