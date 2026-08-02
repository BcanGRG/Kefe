package com.kefe.app.data.remote

import com.kefe.app.domain.model.KefeDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Yanit ayristirmasi. Govdeler CANLI UCTAN alindi, uydurulmadi.
 *
 * Bu dosyanin var olma sebebi somut: para birimini buyuk harfe ceviren bir
 * satir Londra'nin `GBp` isaretini siliyordu ve hata yalnizca gercek cihazda
 * goruldu - ayristirma o zaman `fetch`'in icinde, agin arkasindaydi.
 */
class StockParseTest {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private fun chart(body: String) = json.parseToJsonElement(body).jsonObject

    @Test
    fun londraPeniIsaretiKORUNUR() {
        val quote = parseStockQuote(
            chart(
                """
                {"meta":{"symbol":"SHEL.L","currency":"GBp","fullExchangeName":"LSE",
                 "longName":"Shell plc","regularMarketPrice":3383.5,"gmtoffset":3600},
                 "timestamp":[1785510000],
                 "indicators":{"quote":[{"close":[3383.5]}]}}
                """.trimIndent(),
            ),
            fallbackSymbol = "SHEL.L",
        )

        // Kod OLDUGU GIBI tasinmali; "GBP" olsaydi fiyat yuz kat sisecekti.
        assertEquals("GBp", quote?.currencyCode)
        assertEquals(100.0, currencyConversion(quote!!.currencyCode)?.divisor)
    }

    @Test
    fun bistVeAbdKotasyonlariCevrilmeden_okunur() {
        val bist = parseStockQuote(
            chart(
                """
                {"meta":{"symbol":"ASELS.IS","currency":"TRY","fullExchangeName":"Istanbul",
                 "longName":"ASELSAN","regularMarketPrice":342.25,"gmtoffset":10800},
                 "timestamp":[1785510000],"indicators":{"quote":[{"close":[342.25]}]}}
                """.trimIndent(),
            ),
            fallbackSymbol = "ASELS.IS",
        )
        assertEquals("TRY", bist?.currencyCode)
        assertNull(currencyConversion(bist!!.currencyCode)?.code)

        val us = parseStockQuote(
            chart(
                """
                {"meta":{"symbol":"AAPL","currency":"USD","fullExchangeName":"NasdaqGS",
                 "longName":"Apple Inc.","regularMarketPrice":308.91,"gmtoffset":-14400},
                 "timestamp":[1785528000],"indicators":{"quote":[{"close":[308.91]}]}}
                """.trimIndent(),
            ),
            fallbackSymbol = "AAPL",
        )
        assertEquals("USD", us?.currencyCode)
    }

    /**
     * Gunluk degisim SERIDEN gelir, meta'daki previousClose'dan degil - egri ve
     * yuzde ayni kaynagi kullansin diye.
     */
    @Test
    fun gunlukDegisimSonIkiKapanistan() {
        val quote = parseStockQuote(
            chart(
                """
                {"meta":{"symbol":"AAPL","currency":"USD","regularMarketPrice":308.91,
                 "gmtoffset":-14400},
                 "timestamp":[1785441600,1785528000],
                 "indicators":{"quote":[{"close":[333.02,308.91]}]}}
                """.trimIndent(),
            ),
            fallbackSymbol = "AAPL",
        )
        // (308,91 - 333,02) / 333,02 = -%7,24
        assertEquals(-7.24, quote!!.changePercent, absoluteTolerance = 0.01)
        assertEquals(2, quote.history.size)
        assertEquals(KefeDate(2026, 7, 31), quote.date)
    }

    /** Tatil gununde kapanis null gelir; o gun ATLANIR, uydurulmaz. */
    @Test
    fun bosKapanisAtlanir() {
        val quote = parseStockQuote(
            chart(
                """
                {"meta":{"symbol":"AAPL","currency":"USD","regularMarketPrice":100.0,"gmtoffset":0},
                 "timestamp":[1785441600,1785528000,1785614400],
                 "indicators":{"quote":[{"close":[98.0,null,100.0]}]}}
                """.trimIndent(),
            ),
            fallbackSymbol = "AAPL",
        )
        assertEquals(2, quote?.history?.size)
    }

    @Test
    fun fiyatiOlmayanYanitNullDoner() {
        assertNull(
            parseStockQuote(chart("""{"meta":{"symbol":"X","currency":"USD"}}"""), "X"),
        )
        assertNull(
            parseStockQuote(
                chart("""{"meta":{"symbol":"X","currency":"USD","regularMarketPrice":0}}"""),
                "X",
            ),
        )
        // Para birimi yoksa cevrilemez; satir hic olusmamali.
        assertNull(
            parseStockQuote(
                chart("""{"meta":{"symbol":"X","regularMarketPrice":10.0}}"""),
                "X",
            ),
        )
    }

    // --- Arama suzgeci -------------------------------------------------------

    private fun searchRow(symbol: String, exchange: String, type: String = "EQUITY") = chart(
        """{"symbol":"$symbol","exchange":"$exchange","quoteType":"$type",
           "shortname":"$symbol","exchDisp":"$exchange"}""".trimIndent(),
    )

    @Test
    fun desteklenenBorsalarGecer() {
        listOf("IST", "NMS", "NYQ", "LSE", "PAR", "AMS", "GER").forEach { exchange ->
            assertTrue(
                parseSearchResult(searchRow("X", exchange)) != null,
                "borsa $exchange gecmeliydi",
            )
        }
    }

    /**
     * Kuru olmayan borsalar ELENIR. Suzgec olmadan "AAPL" aramasi her seferinde
     * Buenos Aires ve Sao Paulo satirlari getiriyordu; secilseler fiyat hic
     * gelmiyordu cunku peso kurumuz yok.
     */
    @Test
    fun kuruOlmayanBorsalarElenir() {
        listOf("BUE", "SAO", "SET", "TOR", "MEX", "EBS", "PNK").forEach { exchange ->
            assertNull(parseSearchResult(searchRow("X", exchange)), "borsa $exchange elenmeliydi")
        }
    }

    @Test
    fun hisseDisiSonuclarElenir() {
        assertNull(parseSearchResult(searchRow("X", "NMS", type = "ETF")))
        assertNull(parseSearchResult(searchRow("X", "NMS", type = "INDEX")))
        assertNull(parseSearchResult(searchRow("X", "NMS", type = "CRYPTOCURRENCY")))
    }
}
