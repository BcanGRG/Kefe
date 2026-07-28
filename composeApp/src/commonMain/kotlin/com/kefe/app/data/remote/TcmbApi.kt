package com.kefe.app.data.remote

import com.kefe.app.domain.model.Currency
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType

/**
 * TCMB gunluk doviz kuru bulteni.
 *
 * Ucretsiz, anahtarsiz ve resmidir. GUNDE BIR yayinlanir (hafta ici ~15:30);
 * "today.xml" o gun henuz yayinlanmadiysa bir onceki bulteni dondurur, yani
 * hafta sonu ve tatillerde de calisir.
 *
 * Bu RESMI kurdur, serbest piyasa kuru degil - aradaki fark bugun binde iki
 * mertebesinde. Dovizini bozdurdugunda alacagin rakam serbest piyasa kurudur;
 * Piyasa ekranindaki "Serbest piyasa" etiketiyle tam ortusmez.
 */
class TcmbApi(private val client: HttpClient) {

    suspend fun fetch(): Map<String, TcmbRate> {
        val xml = try {
            retryOnTransient { client.get(ENDPOINT) { accept(ContentType.Text.Xml) }.bodyAsText() }
        } catch (error: Exception) {
            throw PriceRefreshException("TCMB kurlari alinamadi", error)
        }

        val rates = CURRENCIES.mapNotNull { code -> parseCurrency(xml, code)?.let { code to it } }
            .toMap()
        if (rates.isEmpty()) throw PriceRefreshException("TCMB yanitinda kur bulunamadi")
        return rates
    }

    /**
     * Genel bir XML ayristiricisi DEGIL.
     *
     * Kaynak tek, bicimi yillardir sabit ve yalniz iki para birimi okunuyor.
     * Bunun icin bir XML kutuphanesi (ve KMP'de calisan bir tanesini bulma
     * derdi) eklemeye degmez. Bicim degisirse null doner, cagiran taraf
     * PriceRefreshException'a cevirir - sessizce yanlis sayi uretmez.
     */
    private fun parseCurrency(xml: String, code: String): TcmbRate? {
        val start = xml.indexOf("CurrencyCode=\"$code\"")
        if (start < 0) return null
        val end = xml.indexOf("</Currency>", start)
        if (end < 0) return null
        val block = xml.substring(start, end)

        val buying = block.tagValue("ForexBuying")?.toDoubleOrNull()
        val selling = block.tagValue("ForexSelling")?.toDoubleOrNull() ?: return null
        if (selling <= 0.0) return null
        return TcmbRate(buying = buying?.takeIf { it > 0.0 }, selling = selling)
    }

    private fun String.tagValue(tag: String): String? {
        val open = indexOf("<$tag>")
        if (open < 0) return null
        val close = indexOf("</$tag>", open)
        if (close < 0) return null
        return substring(open + tag.length + 2, close).trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val ENDPOINT = "https://www.tcmb.gov.tr/kurlar/today.xml"
        // Portfoye girilebilenlerle ayni liste - bkz. Currency.
        val CURRENCIES = Currency.entries.map { it.code }
    }
}

data class TcmbRate(val buying: Double?, val selling: Double)
