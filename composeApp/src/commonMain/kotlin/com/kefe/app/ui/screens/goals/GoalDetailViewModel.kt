package com.kefe.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalMilestone
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.plusMonths
import com.kefe.app.domain.model.progress
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Tek hedefin detayi: kefe kavisi, projeksiyon, senaryo ve katki gecmisi.
 *
 * Senaryo egrisi tasarimdaki degerleri birebir uretir. Formul
 *   ay = K / (aylik katki [bin TL] + ScenarioInertia)
 * seklindedir ve K hedefin KENDI verisinden turetilir: hedefin bugunku aylik
 * katkisi verildiginde hedefin kendi tahmini varis ayini dondurmelidir. Boylece
 * sabit bir demo katsayisi yerine her hedefte tutarli bir egri cikar.
 */
class GoalDetailViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val goalId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalDetailUiState())
    val state: StateFlow<GoalDetailUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    fun onIntent(intent: GoalDetailIntent) {
        when (intent) {
            is GoalDetailIntent.SetScenarioContribution -> {
                _state.value = _state.value.withScenario(
                    intent.thousands.coerceIn(ScenarioMinThousands, ScenarioMaxThousands),
                )
            }

            GoalDetailIntent.ToggleAllRows -> {
                _state.value = _state.value.copy(showAllRows = !_state.value.showAllRows)
            }

            GoalDetailIntent.CloseGoal -> {
                val goal = _state.value.goal ?: return
                viewModelScope.launch {
                    portfolioRepository.upsertGoal(goal.copy(status = GoalStatus.Completed))
                }
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observeGoals(),
                portfolioRepository.observePositions(),
                portfolioRepository.observeSnapshots(),
            ) { goals, positions, snapshots ->
                val goal = goals.firstOrNull { it.id == goalId }
                if (goal == null) {
                    _state.value.copy(stage = GoalDetailStage.Missing, goal = null)
                } else {
                    build(goal, positions.sumOf { it.value }, snapshots)
                }
            }.collect { _state.value = it }
        }
    }

    private fun build(
        goal: Goal,
        wealth: Double,
        snapshots: List<DailySnapshot>,
    ): GoalDetailUiState {
        val previous = _state.value
        val today = snapshots.lastOrNull()?.date ?: previous.today
        val rows = buildRows(snapshots)
        val emptyIndex = SampleSeries.monthlyContributions.indexOfFirst { it.isEmpty }
        val emptyDate = snapshots.getOrNull(emptyIndex + 1)?.date

        val base = previous.copy(
            stage = GoalDetailStage.Ready,
            goal = goal,
            today = today,
            currentWealth = wealth,
            progress = goal.progress(wealth),
            monthsToTarget = (goal.targetDate.monthIndex() - today.monthIndex())
                .coerceAtLeast(0),
            delayMonths = (goal.estimatedArrival ?: goal.targetDate).monthIndex() -
                goal.targetDate.monthIndex(),
            projectionActual = SampleSeries.projectionActual,
            projectionForecast = SampleSeries.projectionForecast,
            bandLow = SampleSeries.bandLow,
            bandHigh = SampleSeries.bandHigh,
            milestones = milestonesFor(goal, wealth),
            months = SampleSeries.monthlyContributions,
            emptyMonthLocative = emptyDate?.let { trMonthLocative(it.month) },
            rows = rows,
            collapsedRows = collapseRows(rows),
            baseContribution = goal.monthlyContribution,
        )

        // Kaydirici hedefin kendi katkisinin bir tik uzerinde acilir: kullanici
        // "biraz artirsam ne olur" sorusunun cevabini ilk bakista gorur.
        val start = if (previous.stage == GoalDetailStage.Ready) {
            previous.scenarioContribution
        } else {
            ((goal.monthlyContribution / 1000.0).toFloat() + ScenarioOpeningStep)
                .coerceIn(ScenarioMinThousands, ScenarioMaxThousands)
        }
        return base.withScenario(start)
    }

    private fun buildRows(snapshots: List<DailySnapshot>): List<ContributionRow> =
        SampleSeries.monthlyContributions.mapIndexed { index, month ->
            // Katki listesi anlik goruntularle hizalidir: snapshots[i] ay basi,
            // snapshots[i + 1] ay sonu.
            val end = snapshots.getOrNull(index + 1)
            val endValue = end?.totalValue ?: 0.0
            val startValue = snapshots.getOrNull(index)?.totalValue ?: endValue
            ContributionRow(
                monthLabel = end?.date?.let { "${it.monthLabel()} ${it.year}" }
                    ?: month.monthLabel,
                contribution = month.total,
                monthEnd = endValue,
                // Getiri = ay sonu - ay basi - o ay eklenen para.
                gain = endValue - startValue - month.total,
            )
        }.reversed()

    /** Tasarim: en yeni uc ay, ayrica katkisiz ay listede degilse o da eklenir. */
    private fun collapseRows(rows: List<ContributionRow>): List<ContributionRow> {
        val head = rows.take(3)
        val empty = rows.firstOrNull { it.contribution <= 0.0 }
        return if (empty == null || empty in head) head else head + empty
    }

    private fun milestonesFor(goal: Goal, wealth: Double): List<GoalMilestone> =
        SampleSeries.milestones.map { milestone ->
            val amount = goal.amount * milestone.percent / 100.0
            milestone.copy(amount = amount, reached = wealth >= amount)
        }
}

// --- Senaryo ---------------------------------------------------------------

/**
 * Katkisiz da olsa birikimin kendi getirisiyle ilerledigini temsil eden atalet
 * terimi (bin TL). Paydanin sifirlanmasini da engeller.
 */
private const val ScenarioInertia = 31.0

/** Kaydirici acilis konumu: mevcut katkinin 15 bin TL uzeri. */
private const val ScenarioOpeningStep = 15f

private fun GoalDetailUiState.withScenario(thousands: Float): GoalDetailUiState {
    val goal = goal ?: return copy(scenarioContribution = thousands)

    val arrivalMonths = ((goal.estimatedArrival ?: goal.targetDate).monthIndex() -
        today.monthIndex()).coerceAtLeast(1)
    val k = arrivalMonths * (goal.monthlyContribution / 1000.0 + ScenarioInertia)

    val months = (k / (thousands + ScenarioInertia)).roundToInt().coerceAtLeast(1)
    val arrival = today.plusMonths(months)

    return copy(
        scenarioContribution = thousands,
        scenarioArrival = "${arrival.monthName()} ${arrival.year}",
        scenarioDiffMonths = months - (goal.targetDate.monthIndex() - today.monthIndex()),
    )
}
