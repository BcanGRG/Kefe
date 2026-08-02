package com.kefe.app.data.remote

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PricePoint
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hisse senedi fiyatlari - Borsa Istanbul ve Amerikan borsalari.
 *
 * TEK KAYNAK, IKI BORSA. Ayri bir BIST ucuyla ayri bir ABD ucu tutmak, ayni
 * sorunun (fiyat + gecmis + sembol arama) iki ayri bicimde cozulmesi demekti.
 * Yahoo iki borsayi da ayni yanit bicimiyle veriyor; BIST sembolleri `.IS`
 * ekiyle (`THYAO.IS`), ABD sembolleri ciplak (`AAPL`).
 *
 * Anahtar istemez. Kirilgandir - serbest piyasa ve TEFAS uclariyla ayni sinifta:
 * bicim degisirse fiyat gelmez, son bilinen deger ekranda kalir ve kullanici
 * her fiyati zaten elle de girebilir.
 *
 * BIR AYLIK SERI istenir. Gunluk degisim iki ardisik kapanistan hesaplanir;
 * ayrica seri gecmise yazilir, boylece haftalik/aylik degisim ve varlik
 * detayindaki egri ILK GUNDEN gercek olur (TEFAS ile ayni karar).
 */
class StockApi(private val client: HttpClient) {

    /**
     * Tek sembol. Bulunamazsa null - tek hissenin dusmesi butun yenilemeyi
     * dusurmemeli, bu karari cagiran taraf verir.
     */
    suspend fun fetch(symbol: String): StockQuote? {
        val text = retryOnTransient {
            client.get("$CHART_ENDPOINT/$symbol") {
                header(HttpHeaders.UserAgent, BrowserUserAgent)
                header(HttpHeaders.Accept, "application/json")
                // ARALIK VE PERIYOT SART. Parametresiz cagri GUN ICI seri
                // donuyor: canli sondada 78 nokta geldi ve HEPSI ayni gundeydi
                // (31 Temmuz). Bu seri gecmise yazilsa gunluk degisim iki
                // dakikalik cubuktan hesaplanir, haftalik/aylik degisim ise
                // hic olusmazdi - tek gunun icinde 7 gun oncesi yok.
                url {
                    parameters.append("range", "1mo")
                    parameters.append("interval", "1d")
                }
            }.bodyAsText()
        }

        val result = lenientJson.parseToJsonElement(text)
            .jsonObject["chart"]?.jsonObject
            ?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: return null

        val meta = result["meta"]?.jsonObject ?: return null
        val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNullSafe() ?: return null
        if (price <= 0.0) return null

        val currency = meta["currency"]?.jsonPrimitive?.contentOrNullSafe()?.uppercase()
            ?: return null

        // Kapanis serisi: gun sayisi kadar zaman damgasi ve kapanis. Tatil
        // gunlerinde kapanis null gelir - o gun atlanir, uydurulmaz.
        val stamps = result["timestamp"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonPrimitive.longOrNullSafe() }
        val closes = result["indicators"]?.jsonObject
            ?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("close")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.doubleOrNullSafe() }

        val history = stamps.indices.mapNotNull { i ->
            val close = closes.getOrNull(i) ?: return@mapNotNull null
            if (close <= 0.0) return@mapNotNull null
            val date = dateOfEpochSeconds(stamps[i], meta["gmtoffset"]?.jsonPrimitive?.longOrNullSafe() ?: 0L)
            PricePoint(date, close)
        }

        // Gunluk degisim seriden: son iki kapanis. Meta'daki previousClose gun
        // ici degisince kayabiliyor; seri ile ekrandaki egri ayni kaynaktan
        // gelsin diye buradan hesaplanir.
        val previous = history.getOrNull(history.lastIndex - 1)?.price
        val changePercent = if (previous != null && previous > 0.0) {
            (price - previous) / previous * 100.0
        } else {
            0.0
        }

        return StockQuote(
            symbol = meta["symbol"]?.jsonPrimitive?.contentOrNullSafe() ?: symbol,
            name = meta["longName"]?.jsonPrimitive?.contentOrNullSafe()
                ?: meta["shortName"]?.jsonPrimitive?.contentOrNullSafe()
                ?: symbol,
            price = price,
            currencyCode = currency,
            exchange = meta["fullExchangeName"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
            changePercent = changePercent,
            date = history.lastOrNull()?.date,
            history = history,
        )
    }

    /**
     * Ada gore sembol arama - "aselsan" -> ASELS.IS.
     *
     * Fon aramasindaki ile ayni ihtiyac: kullanici sembolu degil SIRKETIN ADINI
     * biliyor. Yalniz hisse senetleri suzulur; endeks, kripto ve fon sonuclari
     * bu ekranda karsiliksiz.
     */
    suspend fun search(query: String): List<StockSearchResult> {
        val text = retryOnTransient {
            client.get(SEARCH_ENDPOINT) {
                header(HttpHeaders.UserAgent, BrowserUserAgent)
                header(HttpHeaders.Accept, "application/json")
                url { parameters.append("q", query); parameters.append("quotesCount", "12") }
            }.bodyAsText()
        }

        return lenientJson.parseToJsonElement(text)
            .jsonObject["quotes"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it["quoteType"]?.jsonPrimitive?.contentOrNullSafe() == "EQUITY" }
            .mapNotNull { quote ->
                val symbol = quote["symbol"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
                StockSearchResult(
                    symbol = symbol,
                    name = quote["longname"]?.jsonPrimitive?.contentOrNullSafe()
                        ?: quote["shortname"]?.jsonPrimitive?.contentOrNullSafe()
                        ?: symbol,
                    exchange = quote["exchDisp"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
                )
            }
    }

    private companion object {
        const val CHART_ENDPOINT = "https://query1.finance.yahoo.com/v8/finance/chart"
        const val SEARCH_ENDPOINT = "https://query1.finance.yahoo.com/v1/finance/search"

        const val BrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }
    }
}

/** Yahoo'nun tek sembol yaniti - TL'ye cevirme cagiran tarafin isi. */
data class StockQuote(
    val symbol: String,
    val name: String,
    /** Borsanin KENDI para birimindeki fiyat (ABD hissesinde USD). */
    val price: Double,
    val currencyCode: String,
    val exchange: String,
    val changePercent: Double,
    val date: KefeDate?,
    /** Bir aylik gunluk kapanis serisi, eskiden yeniye - borsanin kendi biriminde. */
    val history: List<PricePoint> = emptyList(),
)

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val exchange: String,
)

/**
 * Borsanin yerel gunune cevirir.
 *
 * Damga UTC saniyesidir; borsanin `gmtoffset`'i eklenmeden bolunurse New York
 * kapanisi (20:00 UTC) bir sonraki gune, Istanbul kapanisi bazen bir onceki
 * gune duserdi - gecmis bir gun kayarsa haftalik degisim yanlis noktadan
 * olculur.
 */
internal fun dateOfEpochSeconds(epochSeconds: Long, gmtOffsetSeconds: Long): KefeDate {
    val days = floorDiv(epochSeconds + gmtOffsetSeconds, 86_400L)
    return dateOfEpochDay(days)
}

private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && (a xor b) < 0L) q - 1 else q
}

/** Gun sayisindan takvime - Howard Hinnant'in civil_from_days'i (bkz. KefeDate.toEpochDay). */
internal fun dateOfEpochDay(epochDay: Long): KefeDate {
    val z = epochDay + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    return KefeDate(
        year = (y + if (m <= 2L) 1L else 0L).toInt(),
        month = m.toInt(),
        day = d.toInt(),
    )
}
