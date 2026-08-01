package com.kefe.app.ui.screens.summary

import com.kefe.app.data.sync.CloudState
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.TopMover
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.layout.KefeMarketRow

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
    val usdTry: Double,
    val eurTry: Double,
    val goldGramTry: Double,
)

fun DisplayUnit.formatTotal(tryValue: Double, rates: UnitRates): String = when (this) {
    DisplayUnit.Try -> Money.tl(tryValue, spaced = true)
    DisplayUnit.Usd -> "$ " + Money.number(safeDiv(tryValue, rates.usdTry))
    DisplayUnit.Eur -> "€ " + Money.number(safeDiv(tryValue, rates.eurTry))
    DisplayUnit.GoldGram -> Money.quantity(safeDiv(tryValue, rates.goldGramTry), "gr", decimals = 1)
}

private fun safeDiv(value: Double, rate: Double): Double = if (rate <= 0.0) 0.0 else value / rate

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
    val rates: UnitRates = UnitRates(1.0, 1.0, 1.0),
    val masked: Boolean = false,
    val range: NetWorthRange = NetWorthRange.Year1,
    /** FIYAT tazeligi - "son bilinen fiyatlar" seridini bu surer. */
    val freshness: PriceFreshness = PriceFreshness.Fresh,
    val pricesUpdatedAt: String = "",
    /** ESITLEME durumu - basliktaki cipi bu surer. Fiyatla ilgisi yok. */
    val cloudState: CloudState = CloudState.Off,
    /**
     * Ana hedefin karsiligi. Hedefe varlik atanmissa TOPLAM birikimden farklidir;
     * o yuzden ayri tutulur.
     */
    val mainGoalWealth: Double = 0.0,
    val pendingSyncCount: Int = 0,
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
    /** Masaustu yan navigasyonunun alt satiri: "Eşit · 14:32'de güncellendi". */
    val syncLine: String
        get() = when (freshness) {
            PriceFreshness.Loading -> "Fiyatlar alınıyor…"
            PriceFreshness.Offline -> "Çevrimdışı · son bilinen fiyatlar"
            PriceFreshness.Stale -> "Fiyatlar 2 saatten eski"
            PriceFreshness.Fresh -> "Eşit · $pricesUpdatedAt'te güncellendi"
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
}
