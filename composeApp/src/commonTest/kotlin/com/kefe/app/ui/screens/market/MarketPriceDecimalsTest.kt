package com.kefe.app.ui.screens.market

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.ui.format.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Piyasa tablosunda hane sayisi.
 *
 * Iki kural carpisiyor ve ikisi de gercek: kucuk fiyatta kurus GEREKLI (kaynak
 * dakikada bir guncelleniyor, tam liraya yuvarlanan satir donmus gorunuyordu),
 * buyuk fiyatta kurus hem GEREKSIZ hem de sutuna sigmiyor.
 *
 * Sigmamak burada sessiz bir hataydi: metin tasinca ellipsis bile cizilmiyor,
 * cihazda "₺41.457,00" -> "₺41.457," olarak, yani EKSIK ama dogru gorunen bir
 * rakam olarak duruyordu.
 */
class MarketPriceDecimalsTest {

    @Test
    fun kucukFiyatKurusuKORUR() {
        // Gram gumus, ayar altini, fon payi - hareket kurus mertebesinde.
        assertEquals(2, AssetClass.Gold.priceDecimals(5_593.04))
        assertEquals(2, AssetClass.Silver.priceDecimals(87.9))
        // Tam sayida bile iki hane: satirin donmus gorunmemesi icin taban bu.
        assertEquals(2, AssetClass.Gold.priceDecimals(6_175.0))
    }

    @Test
    fun onBininUstundeKurusDUSER() {
        assertEquals(0, AssetClass.Gold.priceDecimals(10_027.02))
        assertEquals(0, AssetClass.Gold.priceDecimals(41_457.30))
        assertEquals(0, AssetClass.Stock.priceDecimals(14_690.96))
    }

    /** Esik tam sinirda: 9.999,99 kurus tasir, 10.000 tasimaz. */
    @Test
    fun esikSinirda() {
        assertEquals(2, AssetClass.Gold.priceDecimals(9_999.99))
        assertEquals(0, AssetClass.Gold.priceDecimals(10_000.0))
    }

    /** Fon payi alti haneye kadar iner; esik ona dokunmaz, zaten kucuk. */
    @Test
    fun fonPayiHassasiyetiniKORUR() {
        assertEquals(6, AssetClass.Fund.priceDecimals(3.714523))
    }

    /**
     * Asil olcu: yazilan metin sutuna SIGMALI. Sutun 68dp ve tabular rakam
     * yaklasik 7,5dp - yani en cok dokuz karakter.
     */
    @Test
    fun yazilanMetinSutunaSigacakUzunluktaKalir() {
        listOf(
            AssetClass.Gold to 41_457.30,
            AssetClass.Gold to 10_027.02,
            AssetClass.Gold to 6_175.37,
            AssetClass.Stock to 14_690.96,
            AssetClass.Fx to 47.5563,
        ).forEach { (assetClass, value) ->
            val text = Money.tl(value, decimals = assetClass.priceDecimals(value))
            assertTrue(
                text.length <= 9,
                "sutuna sigmiyor: $text (${text.length} karakter)",
            )
        }
    }
}
