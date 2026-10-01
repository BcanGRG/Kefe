package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HoldingGainTest {

    private val today = KefeDate(2026, 10, 20)
    private fun gold(quantity: Double, price: Double) =
        planPosition("pos_gold_gram", AssetClass.Gold, GoldSubtype.Gram, Karat.K24, quantity = quantity, unitPrice = price)

    @Test
    fun donemIciIslemYoksaFiyatHareketiyleAyni() {
        // 10 gr, fiyat 100 -> 110 (+%10).
        val gain = gold(10.0, 110.0).gainIn(10.0, emptyList(), today, GainWeekDays)!!
        assertEquals(100.0, gain.amount, 1e-9)
        assertEquals(1000.0, gain.base, 1e-9)
        assertEquals(10.0, gain.toPeriodTotal()!!.percent, 1e-9)
    }

    @Test
    fun donemIcindeAlinanAlisFiyatindanSayilir() {
        // Hafta basinda 10 gr (fiyat 100). 3 gun once 5 gr 105'ten alindi; simdi 110.
        val position = gold(15.0, 110.0)
        val buy = planBuy(position, 5.0, 105.0, date = KefeDate(2026, 10, 17))
        val gain = position.gainIn(10.0, listOf(buy), today, GainWeekDays)!!
        // Eski 10 gr: +100; yeni 5 gr: 5 x (110 - 105) = +25.
        assertEquals(125.0, gain.amount, 1e-9)
        assertEquals(1525.0, gain.base, 1e-9)
        // Eski hesap butun 15 gr'a %10 uyguluyordu: 1650 / 1,1 -> +150.
    }

    @Test
    fun donemIcindeSatilanSatisTutarindanSayilir() {
        // Hafta basinda 10 gr (fiyat 100); 4 gr 108'den satildi, kalan 6 gr simdi 110.
        val position = gold(6.0, 110.0)
        val sell = planSell(position, 4.0, 108.0, date = KefeDate(2026, 10, 16))
        val gain = position.gainIn(10.0, listOf(sell), today, GainWeekDays)!!
        // Kalan 6 gr: +60; satilan 4 gr: 4 x (108 - 100) = +32.
        assertEquals(92.0, gain.amount, 1e-9)
    }

    @Test
    fun bugunAlinanYuzdeBilinmeseDeHesaplanir() {
        // Hepsi bugun 105'ten alindi, deger satis fiyatiyla 100: makas kadar eksi.
        val position = gold(5.0, 100.0)
        val buy = planBuy(position, 5.0, 105.0, date = today)
        val gain = position.gainIn(null, listOf(buy), today, GainDayDays)!!
        assertEquals(-25.0, gain.amount, 1e-9)
        assertEquals(525.0, gain.base, 1e-9)
    }

    @Test
    fun donemBasindaEldeVarYuzdeBilinmiyorsaNull() {
        assertNull(gold(10.0, 110.0).gainIn(null, emptyList(), today, GainMonthDays))
    }

    @Test
    fun donemDisindakiIslemSayilmaz() {
        val position = gold(10.0, 110.0)
        // Gunluk pencere: dunku alim donem basinda elde sayilir.
        val yesterday = planBuy(position, 5.0, 90.0, date = KefeDate(2026, 10, 19))
        val gain = position.gainIn(10.0, listOf(yesterday), today, GainDayDays)!!
        assertEquals(100.0, gain.amount, 1e-9)
    }

    @Test
    fun toplamTlleriToplarYuzdeleriOrtalamaz() {
        val total = listOf(HoldingGain(100.0, 1000.0), null, HoldingGain(-10.0, 100.0)).total()!!
        assertEquals(90.0, total.amount, 1e-9)
        assertEquals(90.0 / 1100.0 * 100.0, total.percent, 1e-9)
        assertNull(listOf<HoldingGain?>(null).total())
    }
}
