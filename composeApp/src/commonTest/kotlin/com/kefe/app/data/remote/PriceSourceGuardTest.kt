package com.kefe.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fiyat kaynaklarinin kenar durumlari.
 *
 * Ikisi de "kaynak duzgun davranirsa" dogru, bozuk veri gelince yanlisti:
 *   - Borsa yanitinda zaman damgasi dizisi elenip kapanis dizisi elenmiyordu;
 *     tek bozuk damga listeyi KAYDIRIYOR ve o noktadan sonraki her kapanis bir
 *     onceki gunun tarihine yaziliyordu.
 *   - TEFAS'ta sifir fiyat kontrolu yoktu; sifir gelirse saglam onbellek
 *     fiyatinin uzerine 0 yaziliyor, fon %-100 gorunuyordu.
 */
class PriceSourceGuardTest {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private fun chart(timestamps: String, closes: String): JsonObject = json.decodeFromString(
        JsonObject.serializer(),
        """
        {
          "meta": { "regularMarketPrice": 100.0, "currency": "USD", "gmtoffset": 0 },
          "timestamp": [$timestamps],
          "indicators": { "quote": [ { "close": [$closes] } ] }
        }
        """.trimIndent(),
    )

    /** 1 Ocak, 2 Ocak, 3 Ocak 2021 (UTC). */
    private val day1 = 1_609_459_200L
    private val day2 = day1 + 86_400L
    private val day3 = day2 + 86_400L

    @Test
    fun saglamSeriDOGRUEslesir() {
        val quote = parseStockQuote(chart("$day1, $day2, $day3", "10.0, 11.0, 12.0"), "AAPL")!!

        assertEquals(listOf(10.0, 11.0, 12.0), quote.history.map { it.price })
        assertEquals(1, quote.history.first().date.day)
        assertEquals(3, quote.history.last().date.day)
    }

    /**
     * ASIL HATA: ortadaki damga bozuk. Eski surumde damga dizisi eleniyor,
     * kapanis dizisi elenmiyordu; 12.0 kapanisi 3 Ocak yerine 2 OCAK'a
     * yaziliyordu.
     */
    @Test
    fun BOZUKDamgaSeriyiKaydirmaz() {
        val quote = parseStockQuote(chart("$day1, null, $day3", "10.0, 11.0, 12.0"), "AAPL")!!

        // Bozuk damganin satiri duser; kalanlar KENDI tarihlerinde kalir.
        assertEquals(listOf(10.0, 12.0), quote.history.map { it.price })
        assertEquals(listOf(1, 3), quote.history.map { it.date.day })
    }

    @Test
    fun kapanisiOlmayanGunATLANIR() {
        val quote = parseStockQuote(chart("$day1, $day2, $day3", "10.0, null, 12.0"), "AAPL")!!

        assertEquals(listOf(10.0, 12.0), quote.history.map { it.price })
        assertEquals(listOf(1, 3), quote.history.map { it.date.day })
    }

    @Test
    fun fiyatiOlmayanYanitKOTASYONDegil() {
        val bos: JsonObject = json.decodeFromString(JsonObject.serializer(), """{ "meta": {} }""")

        assertNull(parseStockQuote(bos, "AAPL"))
    }

    /** Gecmis bos gelse bile kotasyonun kendisi kullanilabilir olmali. */
    @Test
    fun gecmissizKotasyonYineDeDoner() {
        val quote = parseStockQuote(chart("", ""), "AAPL")

        assertTrue(quote != null && quote.history.isEmpty())
    }
}
