package com.kefe.app.ui.screens.goals

import com.kefe.app.data.sample.MonthlyContribution
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalMilestone
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.KefeDate

enum class GoalDetailStage { Loading, Ready, Missing }

/** Aylik katki gecmisi tablosunun tek satiri. */
data class ContributionRow(
    /** "Tem 2026" */
    val monthLabel: String,
    /** 0.0 ise o ay katki yapilmamistir - tabloda "katkı yok" yazar. */
    val contribution: Double,
    val monthEnd: Double,
    val gain: Double,
)

data class GoalDetailUiState(
    val stage: GoalDetailStage = GoalDetailStage.Loading,
    val goal: Goal? = null,
    /** Son anlik goruntunun tarihi - "bugun" tum hesaplarda buradan gelir. */
    val today: KefeDate = KefeDate(2026, 7, 1),

    /** Ilerleme TOPLAM birikime gore olculur. */
    val currentWealth: Double = 0.0,
    val progress: Float = 0f,

    /** Bugunden hedef tarihine kalan ay. */
    val monthsToTarget: Int = 0,
    /** Tahmin hedef tarihini kac ay asiyor (negatifse erken). */
    val delayMonths: Int = 0,

    val projectionActual: List<Double> = emptyList(),
    val projectionForecast: List<Double> = emptyList(),
    val bandLow: List<Double> = emptyList(),
    val bandHigh: List<Double> = emptyList(),

    val milestones: List<GoalMilestone> = emptyList(),
    val months: List<MonthlyContribution> = emptyList(),
    /** Katkisiz ayin bulunma ekli adi ("Kasım'da") - grafigin altindaki notr not. */
    val emptyMonthLocative: String? = null,
    val rows: List<ContributionRow> = emptyList(),
    /** Tasarimdaki kisa liste: en yeni uc ay + varsa katkisiz ay. */
    val collapsedRows: List<ContributionRow> = emptyList(),
    val showAllRows: Boolean = false,

    /** Senaryo kaydiricisi: aylik katki, BIN TL cinsinden (30..120). */
    val scenarioContribution: Float = ScenarioMinThousands,
    val baseContribution: Double = 0.0,
    /** Senaryonun verdigi varis ayi etiketi: "Ekim 2028". */
    val scenarioArrival: String = "",
    /** Senaryo varisinin hedef tarihine gore farki (ay). */
    val scenarioDiffMonths: Int = 0,
) {
    /** Hedefe ulasildi - fazlasi birikimde kalir. */
    val exceeded: Boolean get() = progress >= 1f

    /** Tarihi gecti ama hedef duruyor. */
    val overdue: Boolean get() = !exceeded && goal?.status == GoalStatus.Overdue

    /** Projeksiyon/senaryo/kilometre taslari yalniz yolundaki hedefte gosterilir. */
    val showAnalysis: Boolean get() = !exceeded && !overdue
}

const val ScenarioMinThousands: Float = 30f
const val ScenarioMaxThousands: Float = 120f

/** 30.000 - 120.000 arasi 5.000'lik adimlar -> 18 aralik. */
const val ScenarioSteps: Int = 18

sealed interface GoalDetailIntent {
    data class SetScenarioContribution(val thousands: Float) : GoalDetailIntent
    data object ToggleAllRows : GoalDetailIntent

    /** "Hedefi kapat" - asilan hedefi tamamlandi olarak isaretler. */
    data object CloseGoal : GoalDetailIntent
}

/** Ay sirasi - iki tarih arasindaki ay farkini hesaplamak icin. */
internal fun KefeDate.monthIndex(): Int = year * 12 + (month - 1)

/** Ay adindan bulunma eki: "Kasım'da", "Mart'ta", "Eylül'de". */
internal fun trMonthLocative(month: Int): String = when (month) {
    1 -> "Ocak'ta"
    2 -> "Şubat'ta"
    3 -> "Mart'ta"
    4 -> "Nisan'da"
    5 -> "Mayıs'ta"
    6 -> "Haziran'da"
    7 -> "Temmuz'da"
    8 -> "Ağustos'ta"
    9 -> "Eylül'de"
    10 -> "Ekim'de"
    11 -> "Kasım'da"
    else -> "Aralık'ta"
}
