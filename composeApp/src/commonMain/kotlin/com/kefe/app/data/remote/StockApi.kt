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
 * Hisse senedi fiyatlari - Borsa Istanbul, ABD ve Avrupa borsalari.
 *
 * TEK KAYNAK, COK BORSA. Her borsa icin ayri bir uc tutmak, ayni sorunun
 * (fiyat + gecmis + sembol arama) defalarca cozulmesi demekti. Kaynak hepsini
 * ayni yanit biciminde veriyor; BIST sembolleri `.IS`, Londra `.L`, Paris `.PA`
 * ekiyle, ABD sembolleri ciplak.
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

        return parseStockQuote(result, fallbackSymbol = symbol)
    }

    /**
     * Ada gore sembol arama - "aselsan" -> ASELS.IS.
     *
     * Fon aramasindaki ile ayni ihtiyac: kullanici sembolu degil SIRKETIN ADINI
     * biliyor. Yalniz hisse senetleri suzulur; endeks, kripto ve fon sonuclari
     * bu ekranda karsiliksiz. Sonuclar ayrica BORSAYA gore de suzulur -
     * bkz. [SupportedExchanges].
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
            .mapNotNull(::parseSearchResult)
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

/**
 * Yanittaki `chart.result[0]`'i [StockQuote]'a cevirir.
 *
 * AGDAN AYRI DURUYOR ki sinanabilsin. Ilk hali `fetch`'in icindeydi ve bu yuzden
 * test edilemiyordu: para birimini buyuk harfe ceviren TEK BIR SATIR Londra'nin
 * peni isaretini siliyordu ve hata ancak gercek cihazda goruldu - SHEL.L
 * ₺217.325 yaziyordu, dogrusunun yuz kati (bkz. [currencyConversion]).
 */
internal fun parseStockQuote(result: JsonObject, fallbackSymbol: String): StockQuote? {
    val meta = result["meta"]?.jsonObject ?: return null
    val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNullSafe() ?: return null
    if (price <= 0.0) return null

    // BUYUK HARFE CEVIRMEYIN - kucuk `p` bilgi tasiyor (yukariya bkz.).
    val currency = meta["currency"]?.jsonPrimitive?.contentOrNullSafe() ?: return null

    // Kapanis serisi: gun sayisi kadar zaman damgasi ve kapanis. Tatil
    // gunlerinde kapanis null gelir - o gun atlanir, uydurulmaz.
    val offset = meta["gmtoffset"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
    val stamps = result["timestamp"]?.jsonArray.orEmpty()
        .mapNotNull { it.jsonPrimitive.longOrNullSafe() }
    val closes = result["indicators"]?.jsonObject
        ?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("close")?.jsonArray.orEmpty()
        .map { it.jsonPrimitive.doubleOrNullSafe() }

    val history = stamps.indices.mapNotNull { i ->
        val close = closes.getOrNull(i) ?: return@mapNotNull null
        if (close <= 0.0) return@mapNotNull null
        PricePoint(dateOfEpochSeconds(stamps[i], offset), close)
    }

    // Gunluk degisim seriden: son iki kapanis. Meta'daki previousClose gun ici
    // degisince kayabiliyor; seri ile ekrandaki egri ayni kaynaktan gelsin diye
    // buradan hesaplanir.
    val previous = history.getOrNull(history.lastIndex - 1)?.price
    val changePercent = if (previous != null && previous > 0.0) {
        (price - previous) / previous * 100.0
    } else {
        0.0
    }

    return StockQuote(
        symbol = meta["symbol"]?.jsonPrimitive?.contentOrNullSafe() ?: fallbackSymbol,
        name = meta["longName"]?.jsonPrimitive?.contentOrNullSafe()
            ?: meta["shortName"]?.jsonPrimitive?.contentOrNullSafe()
            ?: fallbackSymbol,
        price = price,
        currencyCode = currency,
        exchange = meta["fullExchangeName"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
        changePercent = changePercent,
        date = history.lastOrNull()?.date,
        history = history,
    )
}

/** Arama sonucu satiri; hisse olmayan ya da desteklenmeyen borsa null doner. */
internal fun parseSearchResult(quote: JsonObject): StockSearchResult? {
    if (quote["quoteType"]?.jsonPrimitive?.contentOrNullSafe() != "EQUITY") return null
    if (quote["exchange"]?.jsonPrimitive?.contentOrNullSafe() !in SupportedExchanges) return null
    val symbol = quote["symbol"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
    return StockSearchResult(
        symbol = symbol,
        name = quote["longname"]?.jsonPrimitive?.contentOrNullSafe()
            ?: quote["shortname"]?.jsonPrimitive?.contentOrNullSafe()
            ?: symbol,
        exchange = quote["exchDisp"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
    )
}

/**
 * Aramada gosterilen borsalar - kodlar olcumle alindi, tahmin edilmedi
 * (`?q=SHELL` -> NYQ, AMS, LSE, GER, FRA; `?q=AAPL` -> NMS, BUE, SAO, SET).
 *
 * SUZGECI KUR BELIRLIYOR, cografya degil. Uygulama TL, dolar, euro ve sterlin
 * kurunu biliyor; baska bir para biriminde fiyatlanan bir kotasyon secilse
 * cevrilemez ve satirda hep "—" kalirdi. Buenos Aires ve Sao Paulo bu yuzden
 * disarida - "AAPL" arayan biri her seferinde uc karsiliksiz satir goruyordu.
 *
 * Ayni gerekceyle Isvicre (EBS/CHF), Iskandinav borsalari ve OTC/pink sheet
 * (PNK, OID) yok. Almanya'nin bolgesel borsalari (Stuttgart, Hannover...)
 * XETRA'nin kopyasi oldugu icin listeyi sisirmesin diye alinmadi.
 */
internal val SupportedExchanges: Set<String> = setOf(
    // Turkiye
    "IST",
    // ABD - Nasdaq katmanlari ve NYSE aileleri
    "NMS", "NCM", "NGM", "NYQ", "NYS", "ASE", "PCX", "BTS",
    // Avrupa - euro bolgesi
    "GER", "FRA", "PAR", "AMS", "MIL", "MCE", "BRU", "LIS", "VIE",
    // Avrupa - sterlin
    "LSE",
)

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
 * Borsanin para birimi kodundan cevrim tarifi.
 *
 * TEK YERDE, cunku iki cagiran var (gunluk yenileme ve islem ekleme ekrani) ve
 * ayrisirlarsa ayni hisse iki ekranda iki fiyat gosterir.
 *
 * PENI TUZAGI: Londra `GBP` DEGIL `GBp` doner ve rakam penidir - SHEL.L canli
 * sondada 3383,5 geldi, yani £33,84. Sterlin sanilsaydi portfoye yuz kat buyuk
 * bir tutar yazilirdi. Kucuk harf `p` tek isarettir, o yuzden kod BUYUK-KUCUK
 * HARF DUYARLI kiyaslanir ve yol boyunca hicbir yerde normallestirilmez.
 *
 * Tanimadigimiz para birimi null doner: cagiran taraf satiri atlar. Bilmedigimiz
 * bir kuru 1.0 varsaymak sessizce yanlis bir rakam uretirdi.
 */
fun currencyConversion(currencyCode: String): CurrencyConversion? = when (currencyCode) {
    "TRY" -> CurrencyConversion(code = null, divisor = 1.0)
    "USD", "EUR", "GBP" -> CurrencyConversion(code = currencyCode, divisor = 1.0)
    "GBp" -> CurrencyConversion(code = "GBP", divisor = 100.0)
    else -> null
}

/**
 * @param code Kurunu aramamiz gereken para birimi; null ise zaten TL.
 * @param divisor Kur uygulanmadan ONCE bolunecek deger (peni -> sterlin).
 */
data class CurrencyConversion(
    val code: String?,
    val divisor: Double,
)

/** "2026-07-30" - kaynaklarin fiyat tablosunda kullandigi ortak bicim. */
internal fun KefeDate.isoText(): String {
    val mm = if (month < 10) "0$month" else "$month"
    val dd = if (day < 10) "0$day" else "$day"
    return "$year-$mm-$dd"
}

/**
 * Borsanin yerel gunune cevirir.
 *
 * Damga UTC saniyesidir; borsanin `gmtoffset`'i eklenmeden bolunurse gun
 * sinirinda bir gun kayar ve haftalik degisim yanlis noktadan olculur.
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
