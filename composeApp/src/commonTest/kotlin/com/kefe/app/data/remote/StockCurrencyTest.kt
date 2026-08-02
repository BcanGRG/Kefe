package com.kefe.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Borsa para birimlerinin TL'ye cevrim tarifi.
 *
 * Kodlar CANLI SONDA ile olculdu: `ASELS.IS` -> TRY, `AAPL` -> USD,
 * `MC.PA`/`SAP.DE` -> EUR, `SHEL.L` -> **GBp** (3383,5 = £33,84).
 */
class StockCurrencyTest {

    @Test
    fun tlBorsasindaCevrimYok() {
        val conversion = currencyConversion("TRY")
        assertEquals(null, conversion?.code)
        assertEquals(1.0, conversion?.divisor)
    }

    @Test
    fun dolarEuroSterlinDogrudanCevrilir() {
        listOf("USD", "EUR", "GBP").forEach { code ->
            val conversion = currencyConversion(code)
            assertEquals(code, conversion?.code, "kod $code")
            assertEquals(1.0, conversion?.divisor, "bolen $code")
        }
    }

    /**
     * ASIL TUZAK. Londra sterlin degil PENI doner ve tek isaret kucuk `p`.
     * Bu satir giderse SHEL.L portfoye yuz kat buyuk yazilir.
     */
    @Test
    fun londraPenisiYuzeBolunur() {
        val conversion = currencyConversion("GBp")
        assertEquals("GBP", conversion?.code)
        assertEquals(100.0, conversion?.divisor)

        // Canli sondadaki gercek rakamla: 3383,5 peni = 33,835 sterlin.
        val pounds = 3383.5 / (conversion?.divisor ?: 1.0)
        assertEquals(33.835, pounds, absoluteTolerance = 1e-9)
    }

    /**
     * Kuru olmayan borsa SESSIZCE 1.0 sayilmaz. Aksi halde 12.500 peso'luk bir
     * kotasyon portfoye 12.500 TL diye girerdi.
     */
    @Test
    fun tanimadigimizParaBirimiNullDoner() {
        assertNull(currencyConversion("ARS"))
        assertNull(currencyConversion("BRL"))
        assertNull(currencyConversion("CHF"))
        // Buyuk-kucuk harf duyarli: "gbp" Yahoo'nun donduru bir kod degil.
        assertNull(currencyConversion("gbp"))
        assertNull(currencyConversion(""))
    }
}
