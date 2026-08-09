package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Gunluk degisimde BILINMIYOR durumu.
 *
 * `dailyChangePercent` non-null'di ve veri yoklugu sifir okunuyordu. Iki zarari
 * vardi: ekranda "Gün" penceresi hicbir zaman "—" olamiyordu, ve o sahte sifir
 * grup toplamlarina paya ve PAYDAYA girip grubun gercek yuzdesini
 * sulandiriyordu - haftalik ve aylikta bilerek kacinilan seyin ta kendisi.
 */
class UnknownDailyChangeTest {

    private fun position(
        id: String,
        value: Double,
        daily: Double?,
    ) = Position(
        id = id,
        name = id,
        assetClass = AssetClass.Gold,
        quantity = 1.0,
        unit = QuantityUnit.Piece,
        unitPrice = value,
        value = value,
        cost = value,
        dailyChangePercent = daily,
    )

    /** Varsayilan BILINMIYOR - sifir degil. */
    @Test
    fun varsayilanBILINMIYOR() {
        assertNull(position("a", 100.0, null).dailyChangePercent)
    }

    /**
     * ASIL ZARAR: yuzdesi bilinmeyen pozisyon grup yuzdesini SULANDIRMAMALI.
     *
     * ₺1.000'lik varlik %10 oynadi, ₺9.000'lik varligin rakami bilinmiyor.
     * Bilinmeyeni sifir saymak grubu %1'e cekerdi; dogru cevap, bilinen kismin
     * kendi yuzdesi olan %10'dur.
     */
    @Test
    fun bilinmeyenPozisyonGrupYuzdesiniSULANDIRMAZ() {
        val bilinen = position("bilinen", 1_100.0, 10.0)
        val bilinmeyen = position("bilinmeyen", 9_000.0, null)

        val yalnizBilinen = listOf(bilinen).todayChange()!!
        val karisik = listOf(bilinen, bilinmeyen).todayChange()!!

        assertEquals(yalnizBilinen.percent, karisik.percent, 1e-9)
        assertEquals(yalnizBilinen.amount, karisik.amount, 1e-9)
        assertEquals(10.0, karisik.percent, 1e-9)
    }

    /** Hicbirinin rakami bilinmiyorsa cevap YOK - ekran "—" yazar. */
    @Test
    fun hicbiriBilinmiyorsaNULL() {
        assertNull(listOf(position("a", 100.0, null), position("b", 200.0, null)).todayChange())
        assertNull(emptyList<Position>().todayChange())
    }

    /** Bilinen SIFIR hesaba girer: "degismedi" bir cevaptir. */
    @Test
    fun bilinenSifirHesabaGIRER() {
        val total = listOf(position("a", 1_000.0, 0.0)).todayChange()!!
        assertEquals(0.0, total.amount, 1e-9)
        assertEquals(0.0, total.percent, 1e-9)
    }

    /**
     * Ozet toplamlari da bilinmeyeni tasir: hicbir pozisyonun rakami yoksa
     * "bugün" satiri "—" yazmali, ₺0 degil.
     */
    @Test
    fun ozetToplamiBILINMEYENITasir() {
        val bilinmeyen = portfolioTotals(
            positions = listOf(position("a", 1_000.0, null)),
            transactions = emptyList(),
            today = KefeDate(2026, 8, 9),
            monthTarget = 0.0,
        )
        assertNull(bilinmeyen.todayChange)
        assertNull(bilinmeyen.todayChangePercent)

        val bilinen = portfolioTotals(
            positions = listOf(position("a", 1_100.0, 10.0)),
            transactions = emptyList(),
            today = KefeDate(2026, 8, 9),
            monthTarget = 0.0,
        )
        assertEquals(100.0, bilinen.todayChange!!, 1e-9)
        assertEquals(10.0, bilinen.todayChangePercent!!, 1e-9)
    }
}
