package com.kefe.app.data.remote

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Currency
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource

/**
 * Gercek fiyat kaynagi: uc ayri servisi tek tabloya indirir.
 *
 *   Altin, gumus  -> serbest piyasa (surekli guncellenir)
 *   Doviz         -> TCMB gunluk bulteni
 *   Fon           -> TEFAS (gunde bir fiyatlanir)
 *
 * Uc kaynagin tazeligi FARKLIDIR ve bu ekranda gorunur: her satir kendi
 * zaman damgasini tasir.
 *
 * HICBIR KAYNAK TEK BASINA YENILEMEYI DUSURMEZ. Once serbest piyasa cagrisi
 * korumasizdi: o tokezleyince TCMB ve TEFAS cevap verse bile tum tablo
 * "Çevrimdışı" oluyordu - ucretsiz bir ucun gunde birkac kez takilmasi
 * uygulamayi cevrimdisi gostermeye yetiyordu. Artik olcut SONUC: elde tek satir
 * fiyat varsa yenileme basarilidir. Hicbiri gelmediyse [PriceRefreshException]
 * atilir ve cagiran cevrimdisi damgasini o zaman basar.
 *
 * Altin yine ayricalikli DEGIL ama kayipsiz da degil: gelmeyen satirin yerine
 * onbellekteki son bilinen fiyat ekranda kalir (bkz. SqlDelightPriceRepository).
 */
class LivePriceRemoteDataSource(
    private val freeMarket: FreeMarketApi,
    private val tcmb: TcmbApi,
    private val tefas: TefasApi,
    // Cekilecek fon kodlari CALISMA ANINDA belli olur: kullanicinin tuttugu
    // fonlar (bkz. DI). Sabit liste yalniz varsayilan/test icin. Boylece eklenen
    // fon gunluk tazelenir, satilan fon bosuna cekilmez.
    private val fundCodes: suspend () -> List<String> = { DefaultFundCodes },
) : PriceRemoteDataSource {

    override suspend fun fetchPrices(): List<Price> {
        // Serbest piyasa da diger ikisi gibi DUSEBILIR. Yutulmasi degil,
        // digerlerini engellememesi onemli: bos kotasyonla devam edilir, doviz
        // TCMB'den gelir, fonlar TEFAS'tan.
        val metals = runCatching { freeMarket.fetch() }.getOrNull()
        val metalStamp = metals?.updatedAt.toClockLabel()

        val prices = mutableListOf<Price>()
        val quotes = metals?.quotes.orEmpty()

        MetalMapping.forEach { (assetKey, mapping) ->
            val quote = quotes[mapping.symbol] ?: return@forEach
            prices += Price(
                assetKey = assetKey,
                label = mapping.label,
                bid = quote.buying,
                ask = quote.selling,
                changePercent = quote.changePercent,
                timestamp = metalStamp,
                source = PriceSource.FreeMarket,
                assetClass = mapping.assetClass,
            )
        }

        // --- Doviz -----------------------------------------------------------
        //
        // ONCE SERBEST PIYASA, TCMB yedek.
        //
        // TCMB resmi bultendir ve HAFTA ICI GUNDE BIR yayinlanir: gun icinde hic
        // degismez, hafta sonu bir oncekini verir. Ustelik referans kurdur,
        // kimsenin alip sattigi fiyat degil. Serbest piyasa ayni istekte dakikalik
        // ve gercek alis/satis makasiyla geliyor - "bugun bozdursam ne alirim"
        // sorusu ancak onunla yanitlanir.
        //
        // TCMB yine de duruyor: serbest piyasada olmayan bir para birimi ya da
        // eksik bir satir icin tek cagriyla devreye giriyor.
        val missingFromFreeMarket = CurrencyMapping.filterValues { quotes[it.symbol] == null }
        val official = if (missingFromFreeMarket.isEmpty()) {
            emptyMap()
        } else {
            runCatching { tcmb.fetch() }.getOrNull().orEmpty()
        }

        CurrencyMapping.forEach { (assetKey, mapping) ->
            val free = quotes[mapping.symbol]
            val rate = official[mapping.symbol]
            val bid = free?.buying ?: rate?.buying
            val ask = free?.selling ?: rate?.selling ?: return@forEach
            prices += Price(
                assetKey = assetKey,
                label = mapping.label,
                bid = bid,
                ask = ask,
                // TCMB gunluk bulten; gun ici degisim yuzdesi vermez.
                changePercent = free?.changePercent ?: 0.0,
                timestamp = if (free != null) metalStamp else "TCMB",
                source = PriceSource.FreeMarket,
                assetClass = AssetClass.Fx,
            )
        }

        // Okuma patlarsa sabit listeye duseriz - yenileme fonsuz da olsa altin
        // ve doviz gelsin.
        val codes = runCatching { fundCodes() }.getOrDefault(DefaultFundCodes)
        codes.distinct().forEach { code ->
            // Tek fonun dusmesi butun yenilemeyi dusurmemeli: altin ve doviz
            // gelmisken tablo bos kalmasin.
            val quote = runCatching { tefas.fetchFund(code) }.getOrNull() ?: return@forEach
            prices += Price(
                assetKey = "fund_${code.lowercase()}",
                label = code,
                // Fonda alis kotasyonu yok, tek fiyat vardir.
                bid = null,
                ask = quote.price,
                changePercent = quote.changePercent,
                timestamp = quote.date,
                source = PriceSource.Tefas,
                assetClass = AssetClass.Fund,
                // TEFAS'in bir aylik serisi: depo bunu gecmise yazar, boylece
                // fonlarda haftalik/aylik degisim ilk gunden gercek olur.
                history = quote.history,
            )
        }

        // Uc kaynagin UCU DE dustu: gosterecek yeni bir sey yok, yenileme
        // basarisiz. Bos liste dondurmek "basariyla hicbir sey geldi" demek
        // olurdu ve onbellegi taze sayardik.
        if (prices.isEmpty()) {
            throw PriceRefreshException("Hicbir fiyat kaynagi yanit vermedi")
        }

        return prices
    }

    /** "2026-07-28 14:47:02" -> "14:47". Ekranda yalniz saat gosteriliyor. */
    private fun String?.toClockLabel(): String {
        val text = this ?: return ""
        val time = text.substringAfter(' ', "")
        return if (time.length >= 5) time.take(5) else text
    }
}

private data class SymbolMapping(
    val symbol: String,
    val label: String,
    val assetClass: AssetClass,
)

/**
 * Uygulama anahtari -> kaynak sembolu.
 *
 * Etiketler kaynaktan DEGIL buradan gelir: kaynak adlari degisebilir ve
 * ekrandaki metin tasarima ait bir karardir.
 */
private val MetalMapping: Map<String, SymbolMapping> = mapOf(
    "gold_gram" to SymbolMapping("GRA", "Gram Altın", AssetClass.Gold),
    "gold_quarter" to SymbolMapping("CEYREKALTIN", "Çeyrek Altın", AssetClass.Gold),
    "gold_half" to SymbolMapping("YARIMALTIN", "Yarım Altın", AssetClass.Gold),
    "gold_full" to SymbolMapping("TAMALTIN", "Tam Altın", AssetClass.Gold),
    "gold_ata" to SymbolMapping("ATAALTIN", "Ata Altın", AssetClass.Gold),
    "gold_k22" to SymbolMapping("YIA", "22 Ayar", AssetClass.Gold),
    "gold_k18" to SymbolMapping("18AYARALTIN", "18 Ayar", AssetClass.Gold),
    "gold_k14" to SymbolMapping("14AYARALTIN", "14 Ayar", AssetClass.Gold),
    "gold_bullion" to SymbolMapping("HAS", "Has Altın", AssetClass.Gold),
    "silver_gram" to SymbolMapping("GUMUS", "Gram Gümüş", AssetClass.Silver),
)

/**
 * Portfoye girilebilen her para birimi icin bir satir - liste [Currency]'den
 * turer ki secilebilen ama fiyati olmayan bir para birimi olusmasin.
 */
private val CurrencyMapping: Map<String, SymbolMapping> = Currency.entries.associate { currency ->
    currency.priceKey() to SymbolMapping(
        symbol = currency.code,
        label = "${currency.code}/TRY",
        assetClass = AssetClass.Fx,
    )
}

/**
 * Baslangicta cekilen fonlar.
 *
 * Kullanicinin portfoyundeki fonlardan turetilmesi gerekir - simdilik sabit bir
 * liste, cunku fiyat tablosu portfoyu bilmiyor.
 */
val DefaultFundCodes: List<String> = listOf("AFA", "IPV", "TTE")
