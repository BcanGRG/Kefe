package com.kefe.app.data.sample

import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.GoalMilestone
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.plusMonths
import kotlin.math.pow

/** Aylik katkinin varlik sinifina gore parcalanmasi. */
data class ContributionSegment(
    val assetClass: AssetClass,
    val amount: Double,
)

/** Tek ay. Segment listesi bos ise o ay katki yapilmamistir. */
data class MonthlyContribution(
    val monthLabel: String,
    val segments: List<ContributionSegment>,
) {
    val total: Double get() = segments.sumOf { it.amount }
    val isEmpty: Boolean get() = segments.isEmpty()
}

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

    /** Belirsizlik bandi - zaman ilerledikce genisler. */
    val bandLow: List<Double> = projectionForecast.mapIndexed { i, v ->
        v * (1.0 - 0.11 * forecastProgress(i))
    }

    val bandHigh: List<Double> = projectionForecast.mapIndexed { i, v ->
        v * (1.0 + 0.13 * forecastProgress(i))
    }

    private fun forecastProgress(index: Int): Double {
        val steps = projectionTargetIndex - todayIndex
        return if (steps <= 0) 0.0 else index.toDouble() / steps.toDouble()
    }

    private fun buildForecast(): List<Double> {
        val start = netWorthTotal.last()
        val steps = projectionTargetIndex - todayIndex
        val growth = projectionTarget / start
        return (0..steps).map { i ->
            start * growth.pow(i.toDouble() / steps.toDouble())
        }
    }

    /** Hedef yolundaki duraklar. */
    val milestones: List<GoalMilestone> = listOf(
        GoalMilestone(25, projectionTarget * 0.25, "Mart 2025'te geçildi", reached = true),
        GoalMilestone(50, projectionTarget * 0.50, "≈ Nisan 2027", reached = false),
        GoalMilestone(75, projectionTarget * 0.75, "≈ Ağustos 2028", reached = false),
        GoalMilestone(100, projectionTarget, "≈ Mart 2029", reached = false),
    )

    // --- Aylik katkilar: son 12 ay -----------------------------------------

    /** Kasım'da katki yok - bos ay grafikte bosluk olarak gosterilir. */
    val monthlyContributions: List<MonthlyContribution> = listOf(
        MonthlyContribution(
            "Ağu",
            listOf(
                ContributionSegment(AssetClass.Gold, 22000.0),
                ContributionSegment(AssetClass.Fund, 12000.0),
                ContributionSegment(AssetClass.Cash, 8000.0),
            ),
        ),
        MonthlyContribution(
            "Eyl",
            listOf(
                ContributionSegment(AssetClass.Gold, 18000.0),
                ContributionSegment(AssetClass.Fund, 14000.0),
                ContributionSegment(AssetClass.Fx, 10000.0),
            ),
        ),
        MonthlyContribution(
            "Eki",
            listOf(
                ContributionSegment(AssetClass.Gold, 26000.0),
                ContributionSegment(AssetClass.Cash, 6000.0),
                ContributionSegment(AssetClass.Silver, 4000.0),
            ),
        ),
        MonthlyContribution("Kas", emptyList()),
        MonthlyContribution(
            "Ara",
            listOf(
                ContributionSegment(AssetClass.Gold, 30000.0),
                ContributionSegment(AssetClass.Fund, 15000.0),
                ContributionSegment(AssetClass.Cash, 10000.0),
            ),
        ),
        MonthlyContribution(
            "Oca",
            listOf(
                ContributionSegment(AssetClass.Gold, 20000.0),
                ContributionSegment(AssetClass.Fund, 12000.0),
                ContributionSegment(AssetClass.Fx, 9000.0),
            ),
        ),
        MonthlyContribution(
            "Şub",
            listOf(
                ContributionSegment(AssetClass.Gold, 24000.0),
                ContributionSegment(AssetClass.Cash, 12000.0),
                ContributionSegment(AssetClass.Silver, 5000.0),
            ),
        ),
        MonthlyContribution(
            "Mar",
            listOf(
                ContributionSegment(AssetClass.Gold, 28000.0),
                ContributionSegment(AssetClass.Fund, 18000.0),
            ),
        ),
        MonthlyContribution(
            "Nis",
            listOf(
                ContributionSegment(AssetClass.Gold, 21000.0),
                ContributionSegment(AssetClass.Fx, 11000.0),
                ContributionSegment(AssetClass.Cash, 7000.0),
            ),
        ),
        MonthlyContribution(
            "May",
            listOf(
                ContributionSegment(AssetClass.Gold, 32000.0),
                ContributionSegment(AssetClass.Fund, 10000.0),
                ContributionSegment(AssetClass.Silver, 3000.0),
            ),
        ),
        MonthlyContribution(
            "Haz",
            listOf(
                ContributionSegment(AssetClass.Gold, 25000.0),
                ContributionSegment(AssetClass.Fund, 14000.0),
                ContributionSegment(AssetClass.Cash, 9000.0),
            ),
        ),
        MonthlyContribution(
            "Tem",
            listOf(
                ContributionSegment(AssetClass.Gold, 27000.0),
                ContributionSegment(AssetClass.Fund, 12000.0),
                ContributionSegment(AssetClass.Fx, 6000.0),
            ),
        ),
    )

    /** Aylik katki hedefi - bar grafigindeki kesikli cizgi. */
    const val monthlyContributionTarget: Double = 50000.0
}
