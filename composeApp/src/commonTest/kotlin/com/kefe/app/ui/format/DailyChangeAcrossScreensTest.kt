package com.kefe.app.ui.format

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.valuedAt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "Gün" penceresi UC EKRANDA AYNI sayiyi vermeli.
 *
 * Vermiyordu. Piyasa tablosu ham [Price.changePercent] okuyordu; Varliklar ve
 * Ozet ise [com.kefe.app.domain.model.todayChangePercent] kapisindan geciyordu.
 * Borsa kapaliyken (hafta sonu, tatil) ayni veriden iki farkli cevap cikiyordu:
 *
 *   Pazar gunu, cuma kapanisindan kalma kotasyon (+%1,50)
 *     Piyasa    : +1,50%   <- cumanin hareketi, "bugün" diye yaziliyor
 *     Varliklar :  0,00%   <- dogru: bugun oynamadi
 *     Ozet      : ₺0       <- dogru
 *
 * Ozet ekraninda celiski AYNI EKRANDA gorunuyordu: piyasa karti dolu, hemen
 * ustundeki "bugün" satiri bos.
 *
 * Dogru cevap sifir: kotasyon cumadan kalmaysa kullanicinin serveti pazar gunu
 * degismemistir (bkz. Valuation.kt'deki kural).
 */
class DailyChangeAcrossScreensTest {

    private val pazar = KefeDate(2026, 8, 9)
    private val cuma = KefeDate(2026, 8, 7)

    private fun price(quoteDate: KefeDate?, changePercent: Double = 1.5) = Price(
        assetKey = "gold_quarter",
        label = "Çeyrek Altın",
        bid = 9_850.0,
        ask = 10_018.0,
        changePercent = changePercent,
        timestamp = "18:05",
        source = PriceSource.FreeMarket,
        assetClass = AssetClass.Gold,
        weekChangePercent = 2.4,
        monthChangePercent = 5.1,
        quoteDate = quoteDate,
    )

    private fun position() = Position(
        id = "pos_gold_quarter",
        name = "Çeyrek",
        assetClass = AssetClass.Gold,
        subtype = GoldSubtype.Quarter,
        quantity = 3.0,
        unit = QuantityUnit.Piece,
        unitPrice = 10_000.0,
        value = 30_000.0,
        cost = 30_000.0,
    )

    /** Asil kural: kotasyon bugunden degilse gunluk degisim SIFIRDIR. */
    @Test
    fun borsaKapaliykenPiyasaDaSIFIRGosterir() {
        val cumadanKalma = price(quoteDate = cuma)
        assertEquals(0.0, cumadanKalma.changeIn(ChangePeriod.Day, pazar))
    }

    /** Uc ekranin okudugu sayi AYNI olmali. */
    @Test
    fun piyasaVeVarliklarAYNISayiyiVerir() {
        listOf(
            price(quoteDate = cuma),
            price(quoteDate = pazar),
            price(quoteDate = null),
        ).forEach { p ->
            val fromMarket = p.changeIn(ChangePeriod.Day, pazar)
            val fromAssets = position().valuedAt(p, pazar).changeIn(ChangePeriod.Day)
            assertEquals(
                fromAssets,
                fromMarket,
                "Piyasa ile Varliklar ayrisiyor (quoteDate=${p.quoteDate})",
            )
        }
    }

    /** Kotasyon bugunden ise rakam AYNEN gecer - kural yalniz eski kotasyonu susturur. */
    @Test
    fun bugunkuKotasyonAYNENGecer() {
        assertEquals(1.5, price(quoteDate = pazar).changeIn(ChangePeriod.Day, pazar))
    }

    /** Gunu bilinmeyen kotasyon (elle girilen fiyat, TCMB) bugun sayilmaz. */
    @Test
    fun gunuBilinmeyenKotasyonSIFIRSayilir() {
        assertEquals(0.0, price(quoteDate = null).changeIn(ChangePeriod.Day, pazar))
    }

    /**
     * Hafta ve ay BU KURALDAN ETKILENMEZ: onlar gecmis serisinden ve bir
     * tolerans penceresiyle hesaplaniyor, yani zaten kapali gunlere dayanikli.
     */
    @Test
    fun haftaVeAyKotasyonGunundenETKILENMEZ() {
        val cumadanKalma = price(quoteDate = cuma)
        assertEquals(2.4, cumadanKalma.changeIn(ChangePeriod.Week, pazar))
        assertEquals(5.1, cumadanKalma.changeIn(ChangePeriod.Month, pazar))
    }
}
