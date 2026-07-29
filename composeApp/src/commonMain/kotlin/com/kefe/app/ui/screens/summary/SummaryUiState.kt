package com.kefe.app.ui.screens.summary

import com.kefe.app.domain.model.AllocationSlice
import com.kefe.app.domain.model.PortfolioTotals
import com.kefe.app.domain.model.TopMover
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.Member
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

data class SummaryUiState(
    val stage: SummaryStage = SummaryStage.Loading,
    val portfolioName: String = "",
    val members: List<Member> = emptyList(),
    val totals: PortfolioTotals? = null,
    val allocation: List<AllocationSlice> = emptyList(),
    val mainGoal: Goal? = null,
    val otherGoalCount: Int = 0,
    val activity: List<ActivityEvent> = emptyList(),
    val netWorthTotal: List<Double> = emptyList(),
    val netWorthPrincipal: List<Double> = emptyList(),
    val topGainer: TopMover? = null,
    val topLoser: TopMover? = null,

    val unit: DisplayUnit = DisplayUnit.Try,
    val rates: UnitRates = UnitRates(1.0, 1.0, 1.0),
    val masked: Boolean = false,
    val periodIndex: Int = 3,
    val freshness: PriceFreshness = PriceFreshness.Fresh,
    val pricesUpdatedAt: String = "",
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
    data class SelectPeriod(val index: Int) : SummaryIntent
    data object Refresh : SummaryIntent
    data object DismissRefreshError : SummaryIntent
}
