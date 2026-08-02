package com.kefe.app.data.remote

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.Currency
import com.kefe.app.domain.model.Price
import com.kefe.app.domain.model.PriceSource

/**
 * Gercek fiyat kaynagi: dort ayri servisi tek tabloya indirir.
 *
 *   Altin, gumus  -> serbest piyasa (surekli guncellenir)
 *   Doviz         -> TCMB gunluk bulteni
 *   Fon           -> TEFAS (gunde bir fiyatlanir)
 *   Hisse         -> borsa kotasyonu (BIST ve ABD)
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
    private val stocks: StockApi,
    // Cekilecek fon kodlari CALISMA ANINDA belli olur: kullanicinin tuttugu
    // fonlar (bkz. DI). Sabit liste yalniz varsayilan/test icin. Boylece eklenen
    // fon gunluk tazelenir, satilan fon bosuna cekilmez.
    private val fundCodes: suspend () -> List<String> = { DefaultFundCodes },
    // Hissede SABIT LISTE YOK - fondan farki bu. Fonda uc kodluk bir varsayilan
    // vardi cunku katalog kucuk; borsada iki ulkede binlerce sembol var, hicbir
    // secim "varsayilan" olamaz. Kullanici hisse tutmuyorsa hic cagri yapilmaz.
    private val stockSymbols: suspend () -> List<String> = { emptyList() },
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

        // Yabanci borsadaki hisseyi TL'ye cevirmek icin kur lazim; bu dongude
        // zaten hesaplaniyor, ikinci kez cagri yapmayalim.
        val fxRates = mutableMapOf<String, Double>()

        CurrencyMapping.forEach { (assetKey, mapping) ->
            val free = quotes[mapping.symbol]
            val rate = official[mapping.symbol]
            val bid = free?.buying ?: rate?.buying
            val ask = free?.selling ?: rate?.selling ?: return@forEach
            fxRates[mapping.symbol] = ask
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

        // --- Hisse -----------------------------------------------------------
        //
        // Kotasyon borsanin KENDI para biriminde gelir; uygulama bastan sona
        // TL ile calisir, o yuzden burada cevrilir. Kur yoksa satir ATLANIR -
        // 180 sayisini TL sanip portfoye yazmaktansa fiyat gelmemis olsun.
        runCatching { stockSymbols() }.getOrDefault(emptyList()).distinct().forEach { symbol ->
            val quote = runCatching { stocks.fetch(symbol) }.getOrNull() ?: return@forEach
            val conversion = currencyConversion(quote.currencyCode) ?: return@forEach
            val rate = when (val code = conversion.code) {
                null -> 1.0
                else -> fxRates[code] ?: return@forEach
            } / conversion.divisor
            if (rate <= 0.0) return@forEach

            prices += Price(
                assetKey = stockAssetKey(symbol),
                label = quote.symbol,
                // Borsada tek fiyat vardir - fon gibi, makas yok.
                bid = null,
                ask = quote.price * rate,
                // Yuzde CEVRILMEZ: oran para biriminden bagimsizdir. Ama kur da
                // oynadigi icin TL bazindaki gunluk degisim bundan farkli olur;
                // gosterilen, hissenin kendi borsasindaki degisimidir.
                changePercent = quote.changePercent,
                // TEFAS satirlariyla AYNI bicim (ISO). Once "30.7" yaziyordu ve
                // Piyasa tablosunda hisse satiri tek basina baska turlu
                // gorunuyordu - bicim farki bilgi tasimiyor, yalniz goze batiyor.
                timestamp = quote.date?.isoText().orEmpty(),
                source = PriceSource.Exchange,
                assetClass = AssetClass.Stock,
                // TL disinda fiyatlanan borsalarda kaynagin kendi rakami da
                // tasinir - ekranda TL degerin yaninda gorunsun diye. BIST'te
                // null: orada zaten TL yaziyor, ikinci kez yazmak gurultu.
                //
                // PENI DEGIL STERLIN yazilir: Londra 3383,5 `GBp` doner, ekrana
                // "£3.383,50" basmak fiyati yuz kat buyuk gostermek olurdu.
                // Bolen burada da ayni yerden gelir ([currencyConversion]).
                nativePrice = (quote.price / conversion.divisor)
                    .takeIf { conversion.code != null },
                nativeCurrency = conversion.code,
                // TEFAS ile ayni karar: bir aylik seri gecmise yazilir, boylece
                // haftalik/aylik degisim ve detaydaki egri ilk gunden gercek.
                //
                // Seri BUGUNKU kurla cevrilir - gecmis kur elimizde yok. Yuzdesel
                // degisim bundan etkilenmez (sabitle carpmak orani degistirmez),
                // ABD hissesinde okunan da zaten hissenin kendi getirisidir.
                history = quote.history.map { it.copy(price = it.price * rate) },
            )
        }

        // Kaynaklarin HEPSI dustu: gosterecek yeni bir sey yok, yenileme
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

/**
 * Sembol -> fiyat tablosu anahtari: `THYAO.IS` -> `stock_thyao.is`.
 *
 * Nokta anahtarda KALIR. Temizlenseydi `BRK.B` ile `BRKB` ayni anahtara duserdi;
 * anahtar zaten yalniz tablo icinde kullaniliyor, okunakliligi sart degil.
 */
fun stockAssetKey(symbol: String): String = "stock_" + symbol.lowercase()

/** Anahtardan sembole geri: `stock_thyao.is` -> `THYAO.IS`. */
fun stockSymbolOf(assetKey: String): String = assetKey.removePrefix("stock_").uppercase()
