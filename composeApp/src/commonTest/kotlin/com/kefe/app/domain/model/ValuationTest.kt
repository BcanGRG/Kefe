package com.kefe.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val EPS = 1e-9

private fun position(
    id: String = "pos_gold_quarter",
    assetClass: AssetClass = AssetClass.Gold,
    subtype: GoldSubtype? = GoldSubtype.Quarter,
    karat: Karat? = null,
    quantity: Double = 3.0,
    unitPrice: Double = 10_000.0,
): Position = Position(
    id = id,
    name = "Çeyrek",
    assetClass = assetClass,
    subtype = subtype,
    karat = karat,
    quantity = quantity,
    unit = QuantityUnit.Piece,
    unitPrice = unitPrice,
    value = quantity * unitPrice,
    cost = quantity * unitPrice,
)

private fun price(
    assetKey: String = "gold_quarter",
    bid: Double? = 9_850.0,
    ask: Double = 10_018.0,
    changePercent: Double = 1.5,
): Price = Price(
    assetKey = assetKey,
    label = "Çeyrek Altın",
    bid = bid,
    ask = ask,
    changePercent = changePercent,
    timestamp = "14:32",
    source = PriceSource.FreeMarket,
    assetClass = AssetClass.Gold,
)

class ValuationTest {

    // --- Hangi fiyat ---

    @Test
    fun degerlemeAlisTarafiniKullanir() {
        // "Elimdekini bugun satsam ne alirim" = kuyumcunun ALIS fiyati.
        assertEquals(9_850.0, price().sellPrice(), EPS)
    }

    @Test
    fun odenecekTutarSatisTarafi() {
        assertEquals(10_018.0, price().buyPrice(), EPS)
    }

    @Test
    fun alisKotasyonuYoksaTekFiyat() {
        // Fonda alis kotasyonu yoktur; iki taraf da ayni fiyattir.
        val fund = price(bid = null, ask = 42.0)
        assertEquals(42.0, fund.sellPrice(), EPS)
        assertEquals(42.0, fund.buyPrice(), EPS)
        assertNull(fund.spreadPercent())
    }

    @Test
    fun makasSatisFiyatininYuzdesi() {
        // 168 / 10.018 = %1,677 - payda SATIS fiyatidir, alis degil.
        assertEquals(168.0 / 10_018.0 * 100.0, price().spreadPercent()!!, EPS)
    }

    // --- Degerleme ---

    @Test
    fun degerGuncelFiyattanHesaplanir() {
        // Saklanan birim fiyat 10.000'di ve hic guncellenmiyordu.
        val valued = position().valuedAt(price())

        assertEquals(9_850.0, valued.unitPrice, EPS)
        assertEquals(29_550.0, valued.value, EPS)
        assertEquals(1.5, valued.dailyChangePercent, EPS)
    }

    @Test
    fun maliyetVeMiktarDokunulmaz() {
        // Ikisi de DEFTERDEN turer; fiyat onlari degistirmez.
        val original = position()
        val valued = original.valuedAt(price())

        assertEquals(original.cost, valued.cost, EPS)
        assertEquals(original.quantity, valued.quantity, EPS)
    }

    @Test
    fun fiyatYoksaSonBilinenDegerKorunur() {
        // Sifire dusurmek "varligin degeri yok" demek olurdu.
        val original = position()
        assertEquals(original, original.valuedAt(null))
    }

    @Test
    fun sifirFiyatDegerlemeSayilmaz() {
        val original = position()
        assertEquals(original, original.valuedAt(price(bid = 0.0, ask = 0.0)))
    }

    // --- Anahtar cozumleme ---

    @Test
    fun altinAltTurundenAnahtar() {
        assertEquals("gold_quarter", position().priceKey())
        assertEquals("gold_gram", position(subtype = GoldSubtype.Gram).priceKey())
        assertEquals("gold_ata", position(subtype = GoldSubtype.Ata).priceKey())
    }

    @Test
    fun bilezikAyardanAnahtar() {
        val jewelry = position(subtype = GoldSubtype.Jewelry, karat = Karat.K18)
        assertEquals("gold_k18", jewelry.priceKey())
    }

    @Test
    fun fonAnahtariKimlikten() {
        // Fon kodu baska hicbir alanda tasinmiyor.
        val fund = position(id = "pos_fund_afa", assetClass = AssetClass.Fund, subtype = null)
        assertEquals("fund_afa", fund.priceKey())
    }

    @Test
    fun nakitAnahtarsiz() {
        // Nakdin birim fiyati yoktur; girilen tutar dogrudan degerdir.
        val cash = position(id = "pos_cash", assetClass = AssetClass.Cash, subtype = null)
        assertNull(cash.priceKey())
    }

    @Test
    fun gumusSabitAnahtar() {
        val silver = position(id = "pos_silver_gram", assetClass = AssetClass.Silver, subtype = null)
        assertEquals("silver_gram", silver.priceKey())
    }
}
