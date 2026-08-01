package com.kefe.app.ui.format

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ondalik hane sayisi DEGERDEN turer, sabit yazilmaz.
 *
 * Sabit hane sayisi iki yonde de yaniltiyordu: fon payi (TEFAS alti haneye
 * kadar veriyor) iki haneye yuvarlaniyor, varlik detayinda 100 TL ustundeki
 * fiyat SIFIR haneye iniyordu - kullanicinin kurusuna kadar girdigi ₺108,394521
 * ekranda "₺108" gorunuyordu. Tersi de dogru: 40.359 TL'lik tam altini alti
 * haneyle yazmak okumayi zorlastirir.
 */
class PrecisionTest {

    // --- Money.decimals ------------------------------------------------------

    @Test
    fun tamSayidaOndalikYok() {
        assertEquals(0, Money.decimals(40_359.0, max = 6))
    }

    @Test
    fun tasidigiKadarHane() {
        assertEquals(2, Money.decimals(10_063.88, max = 6))
        assertEquals(6, Money.decimals(108.394521, max = 6))
        assertEquals(1, Money.decimals(3.5, max = 6))
    }

    @Test
    fun ustSinirAsilmaz() {
        // Alti haneden uzun bir deger sinirda kesilir - sonsuza kadar uzamaz.
        assertEquals(2, Money.decimals(108.394521, max = 2))
    }

    @Test
    fun altSinirKorunur() {
        // Piyasa tablosu kurus tabani ister: tam sayi da "₺10.063,00" yazilir.
        assertEquals(2, Money.decimals(10_063.0, max = 6, min = 2))
        // Alt sinir ustunu kirpmaz.
        assertEquals(6, Money.decimals(108.394521, max = 6, min = 2))
    }

    @Test
    fun kayanNoktaArtigiHaneUretmez() {
        // 108,39 diskte 108.38999999999999 olarak durabiliyor; bu, alti haneli
        // bir fiyat DEGILDIR. Bagil tolerans olmasa "108,390000" yazilirdi.
        assertEquals(2, Money.decimals(108.38999999999999, max = 6))
        assertEquals(0, Money.decimals(0.1 + 0.2 - 0.3, max = 6))
    }

    @Test
    fun buyukSayidaBagilTolerans() {
        // Mutlak tolerans kullanilsaydi buyuk sayilarda hane sayisi sisiyordu.
        assertEquals(0, Money.decimals(1_234_567.0, max = 6))
    }

    // --- Money.tlExact / tlSignedExact ---------------------------------------

    @Test
    fun tlExactKurusuYazar() {
        // ASIL SIKAYET: varlik detayindaki "Güncel değer" ve varlik
        // listesindeki kar/zarar kurusu kirpiyordu.
        assertEquals("₺147.581,36", Money.tlExact(147_581.3567))
        assertEquals("+₺39.291,32", Money.tlSignedExact(39_291.32))
        assertEquals("−₺8.240,55", Money.tlSignedExact(-8_240.55))
    }

    @Test
    fun tlExactTamSayiyaOndalikEklemez() {
        // Gurultu eklenmez: 40.359 TL "₺40.359,00" olmaz.
        assertEquals("₺40.359", Money.tlExact(40_359.0))
        assertEquals("+₺40.359", Money.tlSignedExact(40_359.0))
    }

    @Test
    fun tlExactTekHaneYazmaz() {
        // Emulatorde yakalandi: "₺670.503,6" cikiyordu. Parada ya kurus vardir
        // ya yoktur; ust uste dizilen bir sutunda tek hane rakamlari egri
        // gosteriyordu.
        assertEquals("₺670.503,60", Money.tlExact(670_503.6))
        assertEquals("+₺27.040,20", Money.tlSignedExact(27_040.2))
    }

    @Test
    fun tlExactKayanNoktaArtiginiYutar() {
        // 39.291,32 diskte 39291.319999999996 durabiliyor.
        assertEquals("₺39.291,32", Money.tlExact(39_291.319999999996))
    }

    @Test
    fun tlExactKurustanOteyeGitmez() {
        // Fon fiyati alti haneye iner ama bir TUTAR kurustan oteye gitmez.
        assertEquals("₺108,39", Money.tlExact(108.394521))
    }

    @Test
    fun tlExactSifirIsaretsizdir() {
        // Kar da zarar da yoksa "+" ya da "−" yazmak yon uydurmak olurdu.
        assertEquals("₺0", Money.tlSignedExact(0.0))
    }

    // --- Varlik sinifina gore ust sinir --------------------------------------

    @Test
    fun fonAltiHaneyeKadar() {
        assertEquals(6, AssetClass.Fund.maxPriceDecimals())
        assertEquals(6, QuantityUnit.Share.maxQuantityDecimals())
    }

    @Test
    fun adetBolunmez() {
        assertEquals(0, QuantityUnit.Piece.maxQuantityDecimals())
    }

    // --- Pozisyon uzerinden ---------------------------------------------------

    private fun fund(price: Double, quantity: Double) = Position(
        id = "pos_fund_an1",
        name = "AN1",
        assetClass = AssetClass.Fund,
        quantity = quantity,
        unit = QuantityUnit.Share,
        unitPrice = price,
        value = price * quantity,
        cost = 0.0,
    )

    @Test
    fun fonFiyatiKirpilmaz() {
        // ASIL SIKAYET: kullanici fiyati kurusun altina kadar giriyor.
        assertEquals(6, fund(price = 108.394521, quantity = 1.0).priceDecimals())
    }

    @Test
    fun fonPayiKesirliYazilir() {
        // ₺1.000'lik emir tam sayi pay etmez.
        val position = fund(price = 108.394521, quantity = 9.226847)
        assertEquals(6, position.quantityDecimalsOf())
        assertEquals("9,226847 pay", position.shortQuantityLabel())
    }

    @Test
    fun altinFiyatiSisirilmez() {
        val gold = Position(
            id = "pos_gold_quarter",
            name = "Çeyrek",
            assetClass = AssetClass.Gold,
            quantity = 15.0,
            unit = QuantityUnit.Piece,
            unitPrice = 10_063.88,
            value = 150_958.2,
            cost = 0.0,
        )

        assertEquals(2, gold.priceDecimals())
        // Adet bolunmez: "15 adet", "15,000000 adet" degil.
        assertEquals("15 adet", gold.shortQuantityLabel())
    }
}
