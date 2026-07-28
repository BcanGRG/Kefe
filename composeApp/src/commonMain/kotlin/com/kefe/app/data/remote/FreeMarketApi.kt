package com.kefe.app.data.remote

import com.kefe.app.domain.model.Currency
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serbest piyasa altin ve gumus fiyatlari.
 *
 * Neden bu kaynak: ceyrek/yarim/tam altin fiyatlari ons altindan HESAPLANAMAZ.
 * Bir ceyregin icindeki saf altin ~1,60 gram ama piyasa fiyati darphane payi ve
 * isciligi de tasir; hesaplayarak bulunan deger sistematik olarak dusuk cikar.
 * Bu yuzden bu turleri dogrudan kote eden bir kaynak gerekiyor.
 *
 * Anahtar istemez, ucretsizdir. Kirilgandir - kaynak degisirse ya da bicim
 * bozulursa [PriceRefreshException] atar ve son bilinen fiyatlar ekranda kalir.
 * Kullanici her fiyati zaten elle de girebiliyor.
 */
class FreeMarketApi(private val client: HttpClient) {

    suspend fun fetch(): FreeMarketQuotes {
        // Govde ONCE metin olarak tam okunur, sonra ayristirilir.
        //
        // Dogrudan body<JsonObject>() akistan ayristiriyor ve bu kaynak baglantiyi
        // erken kapatiyor: ayristirici yarim govdeyi gorup "Unexpected JSON token
        // at offset 6811" gibi bir hata veriyordu. Once tamamini okumak, kesik
        // yaniti ayristirma hatasi degil AG hatasi olarak gostermeyi de saglar -
        // yeniden deneme ancak o zaman anlamli.
        val raw: JsonObject = try {
            retryOnTransient {
                val text = client.get(ENDPOINT) {
                    // Baglanti YENIDEN KULLANILMAZ. Bu sunucu havuzdan gelen
                    // baglantiyi yaniti bitirmeden kapatiyor ve istek arka arkaya
                    // yapildiginda "server prematurely closed the connection"
                    // veriyor. Her cagriya taze baglanti acmak sorunu kaldiriyor;
                    // fiyat cekme dakikada bir olan bir is, maliyeti onemsiz.
                    header(HttpHeaders.Connection, "close")
                    header(HttpHeaders.UserAgent, BrowserUserAgent)
                }.bodyAsText()
                lenientJson.parseToJsonElement(text) as JsonObject
            }
        } catch (error: Exception) {
            throw PriceRefreshException("Serbest piyasa fiyatlari alinamadi", error)
        }

        val updatedAt = raw["Update_Date"]?.jsonPrimitive?.contentOrNullSafe()
        val quotes = SYMBOLS.mapNotNull { symbol ->
            raw[symbol]?.toQuote()?.let { symbol to it }
        }.toMap()

        // Tek bir sembol bile gelmiyorsa kaynak bozulmus demektir; yarim bir
        // tabloyu "guncellendi" diye gostermek yaniltici olur.
        if (quotes.isEmpty()) {
            throw PriceRefreshException("Serbest piyasa yanitinda tanidik sembol yok")
        }
        return FreeMarketQuotes(updatedAt = updatedAt, quotes = quotes)
    }

    private fun JsonElement.toQuote(): FreeMarketQuote? {
        val obj = this as? JsonObject ?: return null
        // Alis bazi sembollerde bos gelir (endeks, emtia); satis zorunlu.
        val selling = obj["Selling"]?.jsonPrimitive?.doubleOrNullSafe() ?: return null
        if (selling <= 0.0) return null
        return FreeMarketQuote(
            buying = obj["Buying"]?.jsonPrimitive?.doubleOrNullSafe()?.takeIf { it > 0.0 },
            selling = selling,
            changePercent = obj["Change"]?.jsonPrimitive?.doubleOrNullSafe() ?: 0.0,
        )
    }

    private companion object {
        const val ENDPOINT = "https://finans.truncgil.com/v4/today.json"

        val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

        const val BrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /**
         * Yalniz kullandigimiz semboller okunur - kaynak 80'den fazla sembol
         * donuyor, gerisi bu uygulamada karsiliksiz.
         *
         * YIA = yirmi iki ayar. GRA = gram altin (24 ayar).
         *
         * Doviz de buradan gelir: kaynak 64 para birimini DAKIKADA BIR, alis ve
         * satisiyla veriyor. Ayni istek oldugu icin ek ag maliyeti yok.
         */
        val SYMBOLS = listOf(
            "GRA", "CEYREKALTIN", "YARIMALTIN", "TAMALTIN", "ATAALTIN",
            "YIA", "18AYARALTIN", "14AYARALTIN", "HAS", "GUMUS",
        ) + Currency.entries.map { it.code }
    }
}

data class FreeMarketQuotes(
    val updatedAt: String?,
    val quotes: Map<String, FreeMarketQuote>,
)

data class FreeMarketQuote(
    val buying: Double?,
    val selling: Double,
    val changePercent: Double,
)

/** Fiyat yenileme hatasi - cagiran taraf son bilinen fiyatlari korur. */
class PriceRefreshException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
