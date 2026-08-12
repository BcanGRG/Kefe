package com.kefe.app.ui.screens.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hero'daki birim cevrimi, kur HENUZ GELMEMISKEN.
 *
 * Eksik kur icin 1.0'a dusuluyordu ve bu, TL tutarini OLDUGU GIBI dolar diye
 * yaziyordu: fiyat tahtasi yuklenmeden "$" cipine dokunan biri ₺3.180.400
 * yerine "$ 3.180.400" goruyor, servetini yaklasik 62 kat buyuk saniyordu.
 * Ekran Ready durumuna pozisyonlarla gectigi ve fiyat beklenmedigi icin o
 * pencere gercekten yasaniyordu.
 *
 * Bir `safeDiv` korumasi vardi ama yalniz kur <= 0 iken devreye giriyordu;
 * eksik kur 1.0'a dustugu icin HIC calismiyordu. Korumanin varligi, eksik kurda
 * bos gosterme niyetini zaten kanitliyor.
 */
class UnitRatesTest {

    private val yok = UnitRates()
    private val var_ = UnitRates(usdTry = 62.0, eurTry = 68.0, goldGramTry = 6_660.55)

    /** TL'nin kuru her zaman bellidir - cevrimdisi de olsa gosterilebilir. */
    @Test
    fun TLKurBEKLEMEZ() {
        assertEquals(1.0, DisplayUnit.Try.rateFor(yok))
        assertTrue(DisplayUnit.Try.rateKnown(yok))
    }

    @Test
    fun kurYokkenORANBilinmiyor() {
        assertNull(DisplayUnit.Usd.rateFor(yok))
        assertNull(DisplayUnit.Eur.rateFor(yok))
        assertNull(DisplayUnit.GoldGram.rateFor(yok))
    }

    /**
     * ASIL HATA: kur yokken TL tutari o birimin etiketiyle YAZILMAZ.
     *
     * Eski davranista "$ 3.180.400" cikiyordu; dogrusu 3.180.400 / 62 ≈ 51.297,
     * ve o bilinmiyorsa cevap yoktur.
     */
    @Test
    fun kurYokkenTLTutariOLDUGUGIBIYazilmaz() {
        val yazi = DisplayUnit.Usd.formatTotal(3_180_400.0, yok)
        assertFalse("3.180.400" in yazi, "TL tutari dolar diye yazildi: $yazi")
        assertEquals("—", yazi)
        assertEquals("—", DisplayUnit.Eur.formatTotal(3_180_400.0, yok))
        assertEquals("—", DisplayUnit.GoldGram.formatTotal(3_180_400.0, yok))
    }

    /** TL her durumda yazilir - kur beklemedigi icin "—" olmaz. */
    @Test
    fun TLTutariKurYokkenDeYAZILIR() {
        val yazi = DisplayUnit.Try.formatTotal(3_180_400.0, yok)
        assertTrue("3.180.400" in yazi, "TL tutari yazilmadi: $yazi")
    }

    @Test
    fun kurVarkenCevrimDOGRU() {
        // 3.180.400 / 62 = 51.296,77 -> tam sayiya yuvarlanir.
        assertEquals("$ 51.297", DisplayUnit.Usd.formatTotal(3_180_400.0, var_))
        // 3.180.400 / 6.660,55 = 477,5 gram.
        assertEquals("477,5 gr", DisplayUnit.GoldGram.formatTotal(3_180_400.0, var_))
    }

    /** Ekran kuru gelmemis cipi kilitler. */
    @Test
    fun kurYokkenCipKILITLI() {
        assertFalse(DisplayUnit.Usd.rateKnown(yok))
        assertFalse(DisplayUnit.GoldGram.rateKnown(yok))
        assertTrue(DisplayUnit.Usd.rateKnown(var_))
        assertTrue(DisplayUnit.GoldGram.rateKnown(var_))
    }

    /** Kurlar BAGIMSIZ degerlendirilir: biri gelmis olabilir, digeri gelmemis. */
    @Test
    fun kurlarBAGIMSIZ() {
        val yalnizDolar = UnitRates(usdTry = 62.0)
        assertTrue(DisplayUnit.Usd.rateKnown(yalnizDolar))
        assertFalse(DisplayUnit.Eur.rateKnown(yalnizDolar))
        assertEquals("—", DisplayUnit.Eur.formatTotal(1_000.0, yalnizDolar))
    }

    /** Varsayilan durum: hicbir kur bilinmiyor - ilk acilista boyle baslar. */
    @Test
    fun varsayilanDurumdaKurYOK() {
        val state = SummaryUiState()
        assertNull(DisplayUnit.Usd.rateFor(state.rates))
        assertFalse(DisplayUnit.Usd.rateKnown(state.rates))
    }
}
