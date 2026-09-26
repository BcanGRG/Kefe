package com.kefe.app.ui.screens.summary

import com.kefe.app.data.sync.CloudMode
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.TopMover
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.ui.format.UnknownChangeText
import com.kefe.app.ui.components.longLabel
import com.kefe.app.ui.components.shortLabel

/**
 * Hero rakaminin gosterim birimi. Turkiye'de "kac gram altin ediyor" sorusu
 * TL karsiligi kadar anlamli oldugu icin birinci sinif ozelliktir.
 */
enum class DisplayUnit(val chipLabel: String) {
    Try("₺"),
    Usd("$"),
    Eur("€"),
    GoldGram("gr altın"),
}

/**
 * Hero cevrimi icin kurlar. Sabit YAZILMAZ - fiyat deposundan gelir, boylece
 * kaynak degistiginde ekran degismez ve gosterilen karsilik piyasa tablosuyla
 * tutarli kalir.
 */
data class UnitRates(
    /** null = kur HENUZ BILINMIYOR (bkz. [rateFor]). */
    val usdTry: Double? = null,
    val eurTry: Double? = null,
    val goldGramTry: Double? = null,
)

/**
 * Bu birimin TL kuru; bilinmiyorsa null.
 *
 * Once eksik kur icin 1.0'a dusuluyordu ve bu, TL tutarini oldugu gibi dolar
 * diye yaziyordu: fiyat tahtasi yuklenmeden "$" cipine dokunan biri
 * ₺3.180.400 yerine "$ 3.180.400" goruyor, servetini ~62 kat buyuk saniyordu.
 * Ekran Ready durumuna pozisyonlarla geciyor, fiyat beklenmiyor - o pencere
 * gercekten yasaniyor.
 *
 * `safeDiv` diye bir koruma vardi ama yalniz kur <= 0 iken devreye giriyordu;
 * eksik kur 1.0'a dustugu icin HIC calismiyordu. Korumanin varligi eksik kurda
 * bos gosterme niyetini zaten kanitliyor.
 */
fun DisplayUnit.rateFor(rates: UnitRates): Double? = when (this) {
    // TL'nin kuru her zaman bellidir.
    DisplayUnit.Try -> 1.0
    DisplayUnit.Usd -> rates.usdTry
    DisplayUnit.Eur -> rates.eurTry
    DisplayUnit.GoldGram -> rates.goldGramTry
}

/** Kuru gelmemis birim SECILEMEZ - ekran cipi buna gore kilitler. */
fun DisplayUnit.rateKnown(rates: UnitRates): Boolean = rateFor(rates) != null

/** Toplamin bu birimdeki karsiligi; kur bilinmiyorsa "—". */
fun DisplayUnit.formatTotal(tryValue: Double, rates: UnitRates): String {
    if (this == DisplayUnit.Try) return Money.tl(tryValue, spaced = true)
    val rate = rateFor(rates) ?: return UnknownChangeText
    val converted = tryValue / rate
    return when (this) {
        DisplayUnit.Usd -> "$ " + Money.number(converted)
        DisplayUnit.Eur -> "€ " + Money.number(converted)
        DisplayUnit.GoldGram -> Money.quantity(converted, "gr", decimals = 1)
        DisplayUnit.Try -> Money.tl(tryValue, spaced = true)
    }
}

/** Ekranin yuklenme/veri durumu. Tasarimda her biri ayri cerceve olarak var. */
enum class SummaryStage { Loading, Empty, Ready }

/**
 * Net deger grafiginin ARALIGI.
 *
 * Cipler once yalniz baslikaltindaki aciklamayi degistiriyordu; egri her zaman
 * TUM seriyi ciziyordu, yani "3A" ile "Tümü" ayni resmi veriyordu. Artik seriyi
 * gercekten pencereliyorlar.
 *
 * Gün ve Hafta bu yuzden eklendi: fotograflar gunluk oldugu icin kisa vade de
 * cizilebilir ve "bu hafta ne oldu" sorusunun karsiligi grafikte gorunur.
 *
 * [days] bugunden geriye kac gun; null = tum kayitlar. Gün'de 1 gun geriye
 * bakmak dunku ve bugunku fotografi verir - iki nokta, yani bir cizgi.
 */
enum class NetWorthRange(val label: String, val caption: String, val days: Int?) {
    Day("Gün", "Dün → bugün", 1),
    Week("Hafta", "Son 7 gün", 7),
    Month1("1A", "Son 1 ay", 30),
    Month3("3A", "Son 3 ay", 90),
    Month6("6A", "Son 6 ay", 180),
    Year1("1Y", "Son 12 ay", 365),
    All("Tümü", "Tüm zamanlar", null),
}

data class SummaryUiState(
    val stage: SummaryStage = SummaryStage.Loading,
    val portfolioName: String = "",
    val members: List<Member> = emptyList(),
    val totals: PortfolioTotals? = null,
    val allocation: List<AllocationSlice> = emptyList(),
    val mainGoal: Goal? = null,
    val otherGoalCount: Int = 0,
    val activity: List<ActivityEvent> = emptyList(),
    /** SECILI ARALIGA kirpilmis seri - grafik bunu cizer. */
    val netWorthTotal: List<Double> = emptyList(),
    val netWorthPrincipal: List<Double> = emptyList(),
    /**
     * Elde TOPLAM kac fotograf var (aralik uygulanmadan).
     *
     * Secili aralikta iki noktadan az kayit varsa grafik yerine kisa bir not
     * cizilir; ama hic fotograf yokken kartin kendisi hic cizilmez. Ikisi ayri
     * durum: "bu aralikta yok" ile "hic yok".
     */
    val netWorthSnapshotCount: Int = 0,
    val topGainer: TopMover? = null,
    val topLoser: TopMover? = null,

    val unit: DisplayUnit = DisplayUnit.Try,
    val rates: UnitRates = UnitRates(),
    val masked: Boolean = false,
    val range: NetWorthRange = NetWorthRange.Year1,
    /** FIYAT tazeligi - "son bilinen fiyatlar" seridini bu surer. */
    val freshness: PriceFreshness = PriceFreshness.Fresh,
    val pricesUpdatedAt: String = "",
    /**
     * HESAP modu - cip, ray, yan navigasyon ve hesap seridi bunu okur. Fiyatla
     * ilgisi yok. null = oturum henuz okunmadi; o ana kadar cip cizilmez ki
     * "Bu cihazda" deyip bir kare sonra "Eşitlendi"ye atlamasin.
     */
    val cloudMode: CloudMode? = null,
    /** Son basarili esitleme ("az önce", "5 dk önce"); hic yoksa null. */
    val syncedAgo: String? = null,
    /** Bulut anahtarlari bu surumde var mi - yoksa "Hesaba bağla" onerilmez. */
    val cloudConfigured: Boolean = false,
    /**
     * Ana hedefin karsiligi. Hedefe varlik atanmissa TOPLAM birikimden farklidir;
     * o yuzden ayri tutulur.
     */
    val mainGoalWealth: Double = 0.0,
    /**
     * Ana hedefe TAHMINI VARIS - hedefin kendi birikimi ve aylik katkisindan
     * hesaplanir (bkz. goalProjection). Katki yoksa null.
     *
     * Once `Goal.estimatedArrival` diye kalici bir alandi ama onu hesaplayan kod
     * yolu yoktu; kart bu satiri hic cizmiyordu.
     */
    val mainGoalArrival: KefeDate? = null,
    val refreshing: Boolean = false,
    /**
     * Son yenilemenin hatasi.
     *
     * Once `refresh()` sonucu ATILIYORDU: ag yoksa ekranda hicbir sey degismiyor
     * ve basarili bir yenilemeden ayirt edilemiyordu. Fiyatlar dakikalar icinde
     * cok az oynadigi icin "yenilemiyor" gibi gorunen sey buydu.
     */
    val refreshError: String? = null,

    /**
     * Hata olmayan yenileme bilgisi - su an yalniz "az once guncellendi".
     *
     * [refreshError] ile AYRI TUTULUR: biri kirmizi bir uyari, digeri notr bir
     * bilgi. Ayni alandan gecselerdi kisitlama da hata gibi gorunurdu.
     */
    val refreshNotice: String? = null,

    // --- Uygulama kabugunun (rail / yan nav / ust cubuk) ihtiyaclari ---------
    val positionCount: Int = 0,
    val openGoalCount: Int = 0,
    val marketRows: List<KefeMarketRow> = emptyList(),
) {
    /**
     * Cipin ve rayin etiketi. YALNIZ moddan gelir: fiyat tazeligi ne olursa
     * olsun degismez (bkz. CloudModeTest). NEYDI: ray ve yan nav fiyat ucunun
     * durumunu gosteriyordu - fiyat tokezleyince "Çevrimdışı", hesap gayet
     * esitlenirken.
     */
    val badgeLabel: String?
        get() = cloudMode?.shortLabel()

    /**
     * Masaustu yan navigasyonunun UST satiri: hesap modu, uzun bicimde.
     * Hesapsiz ve bulut yapilandirilmissa bir sonraki adimi da soyler.
     */
    val navModeLine: String
        get() {
            val mode = cloudMode ?: return ""
            val line = mode.longLabel(syncedAgo)
            return if (mode == CloudMode.Local && cloudConfigured) "$line · Hesaba bağla" else line
        }

    /**
     * Masaustu yan navigasyonunun ALT satiri: fiyatlarin durumu. Once tek satir
     * ikisini karistiriyordu ("Eşit · 14:32'de güncellendi") - "eşit" kelimesi
     * fiyat icin yaziliyordu, hesap hic sorulmadan.
     */
    val navPriceLine: String
        get() = when (freshness) {
            PriceFreshness.Loading -> "Fiyatlar alınıyor…"
            PriceFreshness.Offline -> "Fiyatlar alınamadı · son bilinen fiyatlar"
            PriceFreshness.Stale -> "Fiyatlar 2 saatten eski"
            PriceFreshness.Fresh ->
                if (pricesUpdatedAt.isBlank()) {
                    "Fiyatlar güncel"
                } else {
                    "Fiyatlar $pricesUpdatedAt${timeLocative(pricesUpdatedAt)} güncellendi"
                }
        }

    /** Masaustu ust cubugunun baglam satiri. */
    val contextLine: String
        get() = buildString {
            append(portfolioName)
            if (members.isNotEmpty()) {
                append(" · ")
                append(members.joinToString(" ve ") { it.name })
            }
            if (pricesUpdatedAt.isNotBlank()) append(" · fiyatlar $pricesUpdatedAt'de güncellendi")
        }
}

sealed interface SummaryIntent {
    data class SelectUnit(val unit: DisplayUnit) : SummaryIntent
    data object ToggleMask : SummaryIntent
    data class SelectPeriod(val range: NetWorthRange) : SummaryIntent
    data object Refresh : SummaryIntent
    data object DismissRefreshError : SummaryIntent
    data object DismissRefreshNotice : SummaryIntent

    /**
     * Hesap seridindeki "Vazgeç" / "Hesapsız devam et": baglanti birakilir,
     * varsa oturum kapanir. Kayitlar cihazda kalir.
     */
    data object DropLink : SummaryIntent
}

/**
 * Fiyat seridinin metni: iki satir ve "Yenile". Fiyatlar tazeyse ya da ilk
 * istek yoldaysa null - uyaracak bir sey yok.
 *
 * NEYDI: alinamayan fiyat "Çevrimdışı · Son bilinen fiyatlarla" diye yaziyordu,
 * ustu cizili bulut ikonuyla. Ucretsiz fiyat ucunun tokezlemesi uygulamanin
 * internetsiz oldugu, hatta hesabin koptugu gibi okunuyordu. Artik ne oldugunu
 * soyler: fiyatlar alinamadi, eldekiyle gosteriliyor. Ikon saat.
 */
data class PriceBannerLines(val line1: String, val line2: String?)

fun priceBannerLines(freshness: PriceFreshness): PriceBannerLines? = when (freshness) {
    PriceFreshness.Loading, PriceFreshness.Fresh -> null
    PriceFreshness.Stale -> PriceBannerLines(line1 = "Fiyatlar 2 saatten eski", line2 = null)
    PriceFreshness.Offline -> PriceBannerLines(
        line1 = "Fiyatlar alınamadı · son bilinen fiyatlarla",
        line2 = "Bağlantı gelince fiyatlar güncellenir",
    )
}

/**
 * "14:32'de" / "12:05'te" - saat metnine Turkce bulunma eki.
 *
 * Ek son SAYININ OKUNUSUNA gore secilir (otuz iki -> "de", beş -> "te");
 * tek bir sabit ek her saatte yanlis okunur. Ust cubuk ve yan navigasyon ayni
 * yerden alir; once yan nav her saate "'te" ekliyordu.
 */
internal fun timeLocative(time: String): String {
    val minute = time.trim().substringAfterLast(':').takeLast(2).toIntOrNull() ?: return "'de"
    return when (minute % 10) {
        1, 2, 7, 8 -> "'de"
        3, 4, 5 -> "'te"
        6, 9 -> "'da"
        else -> when (minute / 10) {
            2, 5 -> "'de"   // yirmi, elli
            4 -> "'ta"      // kirk
            else -> "'da"   // sifir, on, otuz
        }
    }
}
