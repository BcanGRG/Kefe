package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cok kisa tutuslarda yillik getiri HESAPLANMAZ, ve -100 girdisinde payda
 * sifira dusmez.
 *
 * Yillıklandirmak donem getirisini usse cikarmaktir: uc gunluk %2, 121'inci
 * kuvvete cikiyordu ve ekrana `+14.213.458.746.011.397,12%` gibi rakamlar
 * dusuyordu (ILERLEME.md sonunda kayitli). Rakam yanlis degil - ANLAMSIZ.
 */
class ShortHoldingTest {

    private val bugun = KefeDate(2026, 8, 12)

    private fun alim(date: KefeDate, quantity: Double, unitPrice: Double) = Transaction(
        id = "tx-${date.day}",
        positionId = "pos",
        date = date,
        side = TradeSide.Buy,
        quantity = quantity,
        unitPrice = unitPrice,
        addedByMemberId = "m1",
    )

    @Test
    fun UCGUNLUKTutusYillıklandirilmaz() {
        val uc = listOf(alim(KefeDate(2026, 8, 9), 1.0, 100.0))

        assertNull(
            annualizedReturnPercent(uc, currentValue = 102.0, today = bugun),
            "uc gunluk tutus yillıklandirildi",
        )
    }

    @Test
    fun ayniGUNDeHesaplanmaz() {
        val bugunAlinan = listOf(alim(bugun, 1.0, 100.0))

        assertNull(annualizedReturnPercent(bugunAlinan, currentValue = 150.0, today = bugun))
    }

    @Test
    fun YETERINCEUzunTutusHesaplanir() {
        // Bir yil once alinmis, degeri %20 artmis.
        val yilOnce = listOf(alim(KefeDate(2025, 8, 12), 1.0, 100.0))

        val sonuc = annualizedReturnPercent(yilOnce, currentValue = 120.0, today = bugun)

        assertNotNull(sonuc, "bir yillik tutus hesaplanmadi")
        // Bir yilda %20: yillik getiri de yaklasik %20.
        assertTrue(sonuc in 18.0..22.0, "makul olmayan yillik getiri: $sonuc")
    }

    /** Sinirin hemen ustu: otuz gun hesaplanir. */
    @Test
    fun otuzGunHESAPLANIR() {
        val otuzGunOnce = listOf(alim(KefeDate(2026, 7, 13), 1.0, 100.0))

        assertNotNull(annualizedReturnPercent(otuzGunOnce, currentValue = 101.0, today = bugun))
    }

    // --- -100 girdisinde payda ----------------------------------------------

    /**
     * `1 + (-100)/100` tam SIFIRDIR. Bolum sonsuza gidiyor ve `previous <= 0`
     * kontrolu sonsuzu yakalamadigi icin toplam sessizce sonsuz/NaN oluyordu.
     * Bu girdi bugun uretilemiyor; test o kapinin kapali kalmasi icin.
     */
    @Test
    fun yuzEksiYUZToplamiBozmaz() {
        val toplam = weightedPeriodTotal(
            listOf(
                1_000.0 to 10.0,
                500.0 to -100.0,
            ),
        )

        assertNotNull(toplam)
        assertTrue(toplam.amount.isFinite(), "toplam sonsuz/NaN: ${toplam.amount}")
        assertTrue(toplam.percent.isFinite())
        // Yalniz saglam kalem sayilir: 1.000 bugun, donem basi 909,09.
        assertEquals(10.0, toplam.percent, 1e-9)
    }

    @Test
    fun yuzEksiYUZTekKalemdeNULL() {
        assertNull(periodTotalOf(value = 500.0, percent = -100.0))
    }

    @Test
    fun siradanYuzdeAYNIKalir() {
        val tek = periodTotalOf(value = 110.0, percent = 10.0)

        assertNotNull(tek)
        assertEquals(10.0, tek.amount, 1e-9)
    }
}
