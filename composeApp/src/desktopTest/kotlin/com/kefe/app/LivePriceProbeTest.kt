package com.kefe.app

import com.kefe.app.data.remote.FreeMarketApi
import com.kefe.app.data.remote.LivePriceRemoteDataSource
import com.kefe.app.data.remote.StockApi
import com.kefe.app.data.remote.TcmbApi
import com.kefe.app.data.remote.TefasApi
import com.kefe.app.data.remote.createKefeHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * AGA VURAN test - gercek servislere baglanir.
 *
 * Otomatik kosuda tutulmamali; kaynaklarin bicimi degistiginde ya da fiyat
 * yenilemesi sessizce bosa dustugunde ELLE calistirilir.
 */
class LivePriceProbeTest {

    @Test
    fun probe() = runBlocking {
        val client = createKefeHttpClient()

        runCatching { FreeMarketApi(client).fetch() }
            .onSuccess { println("SERBEST PIYASA OK  guncelleme=${it.updatedAt}  sembol=${it.quotes.size}") }
            .onFailure { println("SERBEST PIYASA HATA: ${it::class.simpleName} ${it.message} / neden=${it.cause}") }

        runCatching { TcmbApi(client).fetch() }
            .onSuccess { println("TCMB OK  $it") }
            .onFailure { println("TCMB HATA: ${it::class.simpleName} ${it.message} / neden=${it.cause}") }

        // TEFAS null donerse sebebini ham govdeden gor.
        runCatching { TefasApi(client).fetchFund("AFA") }
            .onSuccess { quote ->
                println("TEFAS OK  $quote")
                if (quote == null) {
                    val raw = client.post("https://www.tefas.gov.tr/api/funds/fonFiyatBilgiGetir") {
                        contentType(ContentType.Application.Json)
                        header(
                            HttpHeaders.UserAgent,
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        )
                        setBody("""{"fonKodu":"AFA","dil":"TR","periyod":1}""")
                    }.bodyAsText()
                    println("TEFAS HAM (ilk 400): ${raw.take(400)}")
                }
            }
            .onFailure { println("TEFAS HATA: ${it::class.simpleName} ${it.message} / neden=${it.cause}") }

        runCatching {
            LivePriceRemoteDataSource(
                FreeMarketApi(client),
                TcmbApi(client),
                TefasApi(client),
                StockApi(client),
                stockSymbols = { listOf("THYAO.IS", "AAPL") },
            ).fetchPrices()
        }
            .onSuccess { list -> println("BIRLESIK OK  ${list.size} satir"); list.forEach { println("   $it") } }
            .onFailure { println("BIRLESIK HATA: ${it::class.simpleName} ${it.message} / neden=${it.cause}") }

        client.close()
    }
}
