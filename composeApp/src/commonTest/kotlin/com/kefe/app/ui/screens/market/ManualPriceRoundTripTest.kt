package com.kefe.app.ui.screens.market

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.format.parseTrAmountOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Elle fiyat alaninin YAZ-OKU ozdesligi.
 *
 * Piyasa ekrani alani mevcut fiyatla doldurur ve kullanici cogu zaman hicbir
 * seyi degistirmeden kaydeder. O yuzden alana yazdigimiz metin, ayristiricidan
 * gecince AYNI fiyati vermek zorundadir.
 *
 * Vermiyordu: alan gruplanmis metinle ("41.457") doluyor, ayristirici da
 * virgul gormeyince noktayi ondalik sayiyordu. On bin lira ustu fiyatlarda
 * kurus hic yazilmadigi icin (bkz. [MarketPriceDecimalsTest]) metinde virgul
 * bulunmuyor ve ceyrek, yarim, tam, ata satirlarinin HEPSI bu yola dusuyordu:
 * ata altini ₺41.457,30 yerine ₺41,46 olarak kaydediliyordu.
 *
 * Iki kural birbirine bagli oldugu icin test ikisini birlikte tutar.
 */
class ManualPriceRoundTripTest {

    /** Tabloda gercekten gorunen fiyatlar - on bin esiginin iki yanindan. */
    private val board = listOf(
        AssetClass.Gold to 41_457.30,
        AssetClass.Gold to 39_985.00,
        AssetClass.Gold to 20_054.16,
        AssetClass.Gold to 10_027.02,
        AssetClass.Gold to 6_175.37,
        AssetClass.Gold to 5_593.04,
        AssetClass.Silver to 87.90,
        AssetClass.Fx to 47.5563,
        AssetClass.Fund to 3.714523,
        AssetClass.Stock to 14_690.96,
    )

    @Test
    fun alanaYazilanFiyatAYNENGeriOkunur() {
        board.forEach { (assetClass, ask) ->
            val decimals = assetClass.priceDecimals(ask)
            val seeded = Money.plain(ask, decimals)
            val parsed = seeded.parseTrAmountOrNull()

            assertEquals(
                Money.plain(ask, decimals),
                parsed?.let { Money.plain(it, decimals) },
                "yaz-oku ozdesligi bozuldu: $ask -> \"$seeded\" -> $parsed",
            )
        }
    }

    /**
     * Ozdesligin dayandigi sart: alan metninde binlik ayraci OLMAMALI.
     *
     * Ayristirici noktayi her zaman binlik sayarak da, hep ondalik sayarak da
     * yazilabilir; hangisi secilirse secilsin alan metni belirsizlik
     * tasimamali. Bu satir, alani yeniden [Money.number] ile doldurmaya
     * kalkan bir degisikligi yakalar.
     */
    @Test
    fun alanMetnindeBinlikAyraciBULUNMAZ() {
        board.forEach { (assetClass, ask) ->
            val seeded = Money.plain(ask, assetClass.priceDecimals(ask))
            assertTrue('.' !in seeded, "alan metninde binlik ayraci var: \"$seeded\"")
        }
    }

    /**
     * Bin kat hatanin kendisi: eski davranista "41.457" 41,457 donuyordu ve
     * pozitif oldugu icin gecerli sayilip kaydediliyordu.
     */
    @Test
    fun binlikNoktaliFiyatBINKATKUCULMEZ() {
        val seeded = Money.plain(41_457.30, AssetClass.Gold.priceDecimals(41_457.30))
        val parsed = seeded.parseTrAmountOrNull()!!
        assertTrue(parsed > 40_000.0, "fiyat bin kat kuculdu: $parsed")
        assertEquals(41_457.0, parsed)
    }
}
