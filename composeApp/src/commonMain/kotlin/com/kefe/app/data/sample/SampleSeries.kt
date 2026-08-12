package com.kefe.app.data.sample

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.ContributionSlice
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.MonthlyContribution
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.plusMonths
import kotlin.math.pow

/** Grafiklerin ornek serileri. Son degerler ozet rakamlarla birebir tutar. */
object SampleSeries {

    // --- Net servet: son 13 ay ---------------------------------------------

    private val netWorthStart = KefeDate(2025, 7, 1)

    val netWorthTotal: List<Double> = listOf(
        2380000.0, 2452000.0, 2510000.0, 2588000.0, 2641000.0,
        2725000.0, 2790000.0, 2848000.0, 2905000.0, 2984000.0,
        3042000.0, 3118000.0, 3180400.0,
    )

    val netWorthPrincipal: List<Double> = listOf(
        2150000.0, 2196000.0, 2242000.0, 2290000.0, 2336000.0,
        2384000.0, 2430000.0, 2478000.0, 2528000.0, 2582000.0,
        2640000.0, 2702000.0, 2768400.0,
    )

    val netWorthDates: List<KefeDate> =
        netWorthTotal.indices.map { netWorthStart.plusMonths(it) }

    val netWorthLabels: List<String> = netWorthDates.map { it.monthLabel() }

    val netWorthSnapshots: List<DailySnapshot> = netWorthTotal.indices.map { i ->
        DailySnapshot(
            date = netWorthDates[i],
            totalValue = netWorthTotal[i],
            principal = netWorthPrincipal[i],
        )
    }

    // --- Ana hedef projeksiyonu --------------------------------------------

    /** Ana hedef tutari (Ev). */
    const val projectionTarget: Double = 7800000.0

    /** Gecmis (13 ay) + tahmin (Mart 2029'a kadar) = 45 nokta. */
    val projectionDates: List<KefeDate> = (0..44).map { netWorthStart.plusMonths(it) }

    val projectionLabels: List<String> =
        projectionDates.map { "${it.monthLabel()} ${it.year % 100}" }

    /** Bugunun indeksi - gerceklesen seri burada biter. */
    const val todayIndex: Int = 12

    /** Hedefe varilan indeks (Mart 2029). */
    const val projectionTargetIndex: Int = 44

    /** Gerceklesen: toplam birikim. Hedef ilerlemesi bu tabana gore olculur. */
    val projectionActual: List<Double> = netWorthTotal

    /**
     * Tahmin bugunku degerden baslar (iki seri kesintisiz birlesir) ve
     * hedefe Mart 2029'da ulasir. Bilesik buyume ile uretilir.
     */
    val projectionForecast: List<Double> = buildForecast()

    private fun buildForecast(): List<Double> {
        val start = netWorthTotal.last()
        val steps = projectionTargetIndex - todayIndex
        val growth = projectionTarget / start
        return (0..steps).map { i ->
            start * growth.pow(i.toDouble() / steps.toDouble())
        }
    }

    // --- Aylik katkilar: son 12 ay -----------------------------------------

    /**
     * Bilesen katalogunun yiginli bar ornegi - YALNIZ galeri kullanir. Urundeki
     * katki gecmisi artik islem defterinden turer (monthlyContributions).
     *
     * Kasim bos: katkisiz ayin nasil gorundugu de katalogda durmali.
     */
    val galleryContributions: List<MonthlyContribution> = listOf(
        listOf(AssetClass.Gold to 22000.0, AssetClass.Fund to 12000.0, AssetClass.Cash to 8000.0),
        listOf(AssetClass.Gold to 18000.0, AssetClass.Fund to 14000.0, AssetClass.Fx to 10000.0),
        listOf(AssetClass.Gold to 26000.0, AssetClass.Cash to 6000.0, AssetClass.Silver to 4000.0),
        emptyList(),
        listOf(AssetClass.Gold to 30000.0, AssetClass.Fund to 15000.0, AssetClass.Cash to 10000.0),
        listOf(AssetClass.Gold to 20000.0, AssetClass.Fund to 12000.0, AssetClass.Fx to 9000.0),
        listOf(AssetClass.Gold to 24000.0, AssetClass.Cash to 12000.0, AssetClass.Silver to 5000.0),
        listOf(AssetClass.Gold to 28000.0, AssetClass.Fund to 18000.0),
        listOf(AssetClass.Gold to 21000.0, AssetClass.Fx to 11000.0, AssetClass.Cash to 7000.0),
        listOf(AssetClass.Gold to 32000.0, AssetClass.Fund to 10000.0, AssetClass.Silver to 3000.0),
        listOf(AssetClass.Gold to 25000.0, AssetClass.Fund to 14000.0, AssetClass.Cash to 9000.0),
        listOf(AssetClass.Gold to 27000.0, AssetClass.Fund to 12000.0, AssetClass.Fx to 6000.0),
    ).mapIndexed { index, slices ->
        MonthlyContribution(
            date = netWorthStart.plusMonths(index + 1),
            slices = slices.map { (assetClass, amount) ->
                ContributionSlice(assetClass, amount)
            },
            // Katalogda cikis yok: toplam parcalarin toplami.
            total = slices.sumOf { it.second },
        )
    }
}
