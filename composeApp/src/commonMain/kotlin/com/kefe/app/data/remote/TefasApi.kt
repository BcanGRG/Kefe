package com.kefe.app.data.remote

import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PricePoint
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * TEFAS fon fiyatlari.
 *
 * Fonlar GUNDE BIR fiyatlanir; bu yuzden ayri bir kaynak ve ayri tazelik
 * beklentisi var. Bir aylik seri istenir cunku gunluk degisim yuzdesi ancak iki
 * ardisik fiyattan hesaplanabilir - tek fiyat "bugun ne kadar degisti"yi vermez.
 *
 * O SERI ARTIK ATILMIYOR. Once yalniz son iki satiri okunuyor, gerisi cope
 * gidiyordu; oysa haftalik ve aylik degisim tam olarak orada duruyor. Seri
 * [TefasQuote.history] ile tasinir ve depo onu `price_history`'ye yazar -
 * boylece fonlarda hafta/ay ILK GUNDEN gercek olur (altin ve dovizde oyle
 * degil: onlarin kaynagi gecmis vermiyor, cihazda birikmesi gerekiyor).
 *
 * NOT: eski `/api/DB/BindHistoryInfo` ucu 2026'da kaldirildi, 404 donuyor.
 * Kullanilan uc `/api/funds/fonFiyatBilgiGetir`.
 */
class TefasApi(private val client: HttpClient) {

    /**
     * Tek fon.
     *
     * Hatayi YUTMAZ - yutulursa "fon fiyati neden gelmiyor" sorusu hicbir yerde
     * cevaplanamaz hale gelir (bir kez bu yuzden sessizce bos dondu). Tek bir
     * fonun dusmesi butun yenilemeyi dusurmemeli; bu karari cagiran taraf verir.
     */
    suspend fun fetchFund(code: String): TefasQuote? {
        // Govde metin olarak okunup ELLE cozulur.
        //
        // Dogrudan body<TefasResponse>() bos bir resultList donduruyordu: cagri
        // reified tipi generic bir lambda icinde cozemiyor. Metin-once okuma
        // hem bu tuzagi kaldiriyor hem de hata halinde ham yanit gorulebiliyor.
        val response: TefasResponse = retryOnTransient {
            val text = client.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                // TEFAS tarayici disi istemcileri reddediyor: varsayilan Ktor
                // User-Agent'i ile yanit gelmiyor.
                header(HttpHeaders.UserAgent, BrowserUserAgent)
                header(HttpHeaders.Referrer, "https://www.tefas.gov.tr/")
                setBody(REQUEST_TEMPLATE.replace("{code}", code))
            }.bodyAsText()
            tefasJson.decodeFromString<TefasResponse>(text)
        }

        if (response.errorCode != null) return null
        // Yanit tarihe gore ARTAN sirali gelir; son iki fiyat gunluk degisimi verir.
        val series = response.resultList.orEmpty()
        val latest = series.lastOrNull() ?: return null
        val previous = series.getOrNull(series.lastIndex - 1)

        val change = if (previous != null && previous.fiyat > 0.0) {
            (latest.fiyat - previous.fiyat) / previous.fiyat * 100.0
        } else {
            0.0
        }

        return TefasQuote(
            code = latest.fonKodu ?: code,
            name = latest.fonUnvan.orEmpty(),
            price = latest.fiyat,
            changePercent = change,
            date = latest.tarih.orEmpty(),
            // Tarihi cozulemeyen satir ATLANIR - bicim degisirse hafta/ay
            // sessizce yanlis cikmaz, yalnizca "—" kalir.
            history = series.mapNotNull { row ->
                val date = parseIsoDate(row.tarih) ?: return@mapNotNull null
                if (row.fiyat <= 0.0) null else PricePoint(date, row.fiyat)
            },
        )
    }

    private companion object {
        const val ENDPOINT = "https://www.tefas.gov.tr/api/funds/fonFiyatBilgiGetir"

        const val BrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /** periyod yalniz belirli degerleri kabul ediyor (1, 3, 6, 12, 36, 60 ay). */
        const val REQUEST_TEMPLATE = """{"fonKodu":"{code}","dil":"TR","periyod":1}"""

        val tefasJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

@Serializable
private data class TefasResponse(
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val resultList: List<TefasRow>? = null,
)

@Serializable
private data class TefasRow(
    val fonKodu: String? = null,
    val fonUnvan: String? = null,
    val tarih: String? = null,
    val fiyat: Double = 0.0,
)

data class TefasQuote(
    val code: String,
    val name: String,
    val price: Double,
    val changePercent: Double,
    val date: String,
    /** Bir aylik gunluk seri, eskiden yeniye. Haftalik/aylik degisimin kaynagi. */
    val history: List<PricePoint> = emptyList(),
)

/**
 * "2026-07-31" -> KefeDate. Bicim canli sonda ile dogrulandi
 * (`-Pprobe --tests "*LivePriceProbeTest"`).
 *
 * Baska bir bicim gelirse null doner: yanlis bir tarih uydurmaktansa o satiri
 * hic saymamak dogru - hafta/ay o zaman yerel gecmise duser.
 */
internal fun parseIsoDate(text: String?): KefeDate? {
    val parts = text?.trim()?.split('-') ?: return null
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return KefeDate(year = year, month = month, day = day)
}
