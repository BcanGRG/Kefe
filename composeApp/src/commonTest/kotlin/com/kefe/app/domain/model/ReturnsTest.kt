package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun buy(
    id: String,
    date: KefeDate,
    quantity: Double,
    unitPrice: Double,
    fee: Double = 0.0,
): Transaction = Transaction(
    id = id,
    positionId = "pos-1",
    date = date,
    side = TradeSide.Buy,
    quantity = quantity,
    unitPrice = unitPrice,
    fee = fee,
    addedByMemberId = "uye-1",
)

class ReturnsTest {

    // --- xirr: sonuc uretilemeyen durumlar ---

    @Test
    fun tekHareketNullDoner() {
        // Bugunku deger yoksa (currentValue = 0) tek hareket kalir, kok aranamaz.
        val result = annualizedReturnPercent(
            transactions = listOf(buy("1", KefeDate(2025, 1, 1), quantity = 10.0, unitPrice = 100.0)),
            currentValue = 0.0,
            today = KefeDate(2026, 1, 1),
        )
        assertNull(result)
    }

    @Test
    fun ayniTarihliHareketlerNullDoner() {
        val gun = KefeDate(2026, 3, 10)
        val result = xirr(
            listOf(
                CashFlow(gun, -1000.0),
                CashFlow(gun, 1100.0),
            ),
        )
        assertNull(result)
    }

    @Test
    fun isaretDegisimiYoksaNullDoner() {
        val result = xirr(
            listOf(
                CashFlow(KefeDate(2025, 1, 1), -1000.0),
                CashFlow(KefeDate(2026, 1, 1), -500.0),
            ),
        )
        assertNull(result)
    }

    // --- xirr: bilinen degerler ---

    @Test
    fun birYildaYuzdeOn() {
        // 2025-01-01 -> 2026-01-01 tam 365 gun (2025 artik yil degil)
        val result = xirr(
            listOf(
                CashFlow(KefeDate(2025, 1, 1), -1000.0),
                CashFlow(KefeDate(2026, 1, 1), 1100.0),
            ),
        )
        assertEquals(0.10, assertNotNull(result), 1e-4)
    }

    @Test
    fun ikiYildaBilesikYuzdeOn() {
        // 1000 * 1.1 * 1.1 = 1210 ; 2025-01-01 -> 2027-01-01 tam 730 gun
        val result = xirr(
            listOf(
                CashFlow(KefeDate(2025, 1, 1), -1000.0),
                CashFlow(KefeDate(2027, 1, 1), 1210.0),
            ),
        )
        assertEquals(0.10, assertNotNull(result), 1e-4)
    }

    @Test
    fun zararEksiYuzdeOn() {
        val result = xirr(
            listOf(
                CashFlow(KefeDate(2025, 1, 1), -1000.0),
                CashFlow(KefeDate(2026, 1, 1), 900.0),
            ),
        )
        assertEquals(-0.10, assertNotNull(result), 1e-4)
    }

    @Test
    fun duzensizAraliklarMakulSonucVerir() {
        // -1000 (t=0), -1000 (t=1 yil), +2400 (t=2 yil)
        // Kok: x^2 + x - 2.4 = 0, x = (-1 + sqrt(10.6)) / 2 -> r = 0.1278821...
        val result = xirr(
            listOf(
                CashFlow(KefeDate(2025, 1, 1), -1000.0),
                CashFlow(KefeDate(2026, 1, 1), -1000.0),
                CashFlow(KefeDate(2027, 1, 1), 2400.0),
            ),
        )
        val rate = assertNotNull(result)
        assertEquals(0.1278821, rate, 1e-6)
        assertTrue(rate > 0.0, "Kar eden defterde oran pozitif olmali")
    }

    // --- annualizedReturnPercent ---

    @Test
    fun annualizedReturnYuzdeDoner() {
        // 10 adet x 100 = 1000 yatirildi, bir yil sonra deger 1100 -> %10
        val result = annualizedReturnPercent(
            transactions = listOf(buy("1", KefeDate(2025, 1, 1), quantity = 10.0, unitPrice = 100.0)),
            currentValue = 1100.0,
            today = KefeDate(2026, 1, 1),
        )
        // 0.10 degil 10.0 beklenir
        assertEquals(10.0, assertNotNull(result), 1e-4)
    }

    @Test
    fun annualizedReturnIscilikMaliyeteGirer() {
        // 1000 + 100 iscilik = 1100 cikti, bir yil sonra 1210 -> %10
        val result = annualizedReturnPercent(
            transactions = listOf(
                buy("1", KefeDate(2025, 1, 1), quantity = 10.0, unitPrice = 100.0, fee = 100.0),
            ),
            currentValue = 1210.0,
            today = KefeDate(2026, 1, 1),
        )
        assertEquals(10.0, assertNotNull(result), 1e-4)
    }

    @Test
    fun bosDefterNullDoner() {
        assertNull(annualizedReturnPercent(emptyList(), 1000.0, KefeDate(2026, 1, 1)))
    }

    // --- holdingDays ---

    @Test
    fun holdingDaysGunFarkiVerir() {
        assertEquals(10L, holdingDays(KefeDate(2025, 1, 1), KefeDate(2025, 1, 11)))
        assertEquals(366L, holdingDays(KefeDate(2024, 1, 1), KefeDate(2025, 1, 1)))
    }

    @Test
    fun holdingDaysNegatifDegerSifiraKirpilir() {
        assertEquals(0L, holdingDays(KefeDate(2026, 1, 1), KefeDate(2025, 1, 1)))
    }

    @Test
    fun holdingDaysAlimYoksaNull() {
        assertNull(holdingDays(null, KefeDate(2026, 1, 1)))
    }
}
