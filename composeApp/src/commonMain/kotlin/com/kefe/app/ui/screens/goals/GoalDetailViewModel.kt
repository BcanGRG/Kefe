package com.kefe.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.DailySnapshot
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.MonthlyContribution
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.assetsOf
import com.kefe.app.domain.model.goalMilestones
import com.kefe.app.domain.model.goalProjection
import com.kefe.app.domain.model.goalWealth
import com.kefe.app.domain.model.monthLabel
import com.kefe.app.domain.model.monthName
import com.kefe.app.domain.model.monthOrdinal
import com.kefe.app.domain.model.monthlyContributions
import com.kefe.app.domain.model.monthsToReach
import com.kefe.app.domain.model.otherGoalOf
import com.kefe.app.domain.model.plusMonths
import com.kefe.app.domain.model.progress
import com.kefe.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Katki gecmisi tablosunun penceresi - tasarimdaki gibi son 12 ay. */
private const val ContributionMonths = 12

/**
 * Tek hedefin detayi: kefe kavisi, projeksiyon, senaryo ve katki gecmisi.
 *
 * Projeksiyon ve katki gecmisi ONCE ORNEK SERIDEN geliyordu: kullanicinin kendi
 * rakami tepede dururken altinda baskasinin gecmisi ve baskasinin egrisi
 * duruyordu. Artik ucu de kullanicinin verisinden turer - gerceklesen egri
 * gunluk fotograflardan, katkilar islem defterinden, tahmin bugunku birikim ile
 * hedefin aylik katkisindan.
 *
 * Senaryo kaydiricisi da AYNI kurali kullanir: bugunku birikim + secilen aylik
 * katki. Onceden ayri, uydurma bir formulu vardi.
 */
class GoalDetailViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val clock: KefeClock,
    private val goalId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalDetailUiState())
    val state: StateFlow<GoalDetailUiState> = _state.asStateFlow()

    /** Hedef kimligi -> ad. Secicide "Araba'da" uyarisini yazmak icin. */
    private var goalNames: Map<String, String> = emptyMap()

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

            GoalDetailIntent.OpenAssetPicker ->
                _state.value = _state.value.copy(assetPickerOpen = true)

            GoalDetailIntent.CloseAssetPicker ->
                _state.value = _state.value.copy(assetPickerOpen = false)

            // Atanmisi tekrar secmek atamayi KALDIRIR - ayni dokunusla geri
            // alinabilmesi icin ayri bir "kaldir" dugmesi yok.
            is GoalDetailIntent.ToggleAsset -> viewModelScope.launch {
                val alreadyMine = _state.value.assignedAssets.any { it.id == intent.positionId }
                portfolioRepository.assignPositionToGoal(
                    positionId = intent.positionId,
                    goalId = if (alreadyMine) null else goalId,
                )
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
                portfolioRepository.observeAllTransactions(),
                portfolioRepository.observeGoalAssets(),
            ) { goals, positions, snapshots, transactions, assignments ->
                val goal = goals.firstOrNull { it.id == goalId }
                // Secicideki "Araba'da" uyarisi icin: kimlikten ada.
                goalNames = goals.associate { it.id to it.name }
                if (goal == null) {
                    _state.value.copy(stage = GoalDetailStage.Missing, goal = null)
                } else {
                    build(goal, positions, snapshots, transactions, assignments)
                }
            }.collect { _state.value = it }
        }
    }

    private fun build(
        goal: Goal,
        positions: List<Position>,
        snapshots: List<DailySnapshot>,
        transactions: List<Transaction>,
        assignments: Map<String, GoalAssignment>,
    ): GoalDetailUiState {
        val previous = _state.value
        // Hedefe varlik atanmissa yalniz onlar sayilir; atanmamissa tum birikim.
        val wealth = goalWealth(goal, positions, assignments)
        val today = clock.today()

        // Katki gecmisi DEFTERDEN, projeksiyon FOTOGRAFLARDAN. Ikisi de once
        // ornek seriden okunuyordu: kullanicinin kendi rakami tepede dururken
        // altinda baskasinin gecmisi ve baskasinin egrisi duruyordu.
        val months = monthlyContributions(transactions, positions, ContributionMonths, today)
        val projection = goalProjection(snapshots, goal, wealth, today)
        val rows = buildRows(months, snapshots)

        val base = previous.copy(
            stage = GoalDetailStage.Ready,
            goal = goal,
            today = today,
            currentWealth = wealth,
            progress = goal.progress(wealth),
            monthsToTarget = (goal.targetDate.monthIndex() - today.monthIndex())
                .coerceAtLeast(0),
            delayMonths = (projection.arrival ?: goal.targetDate).monthIndex() -
                goal.targetDate.monthIndex(),
            projectionActual = projection.actual,
            projectionForecast = projection.forecast,
            projectedArrival = projection.arrival,
            milestones = goalMilestones(goal, wealth, projection.forecast, today),
            months = months,
            emptyMonthLocative = gapMonth(months)?.let { trMonthLocative(it.date.month) },
            rows = rows,
            collapsedRows = collapseRows(rows),
            baseContribution = goal.monthlyContribution,
            assignedAssets = assetsOf(goal, positions, assignments),
            // Kati atama: hedefi olusturan varliklar = yalniz ona atananlar.
            // Atama yoksa liste bos (ilerleme %0), tum birikim GELMEZ.
            composingAssets = assetsOf(goal, positions, assignments),
            // Secici: her varlik, varsa baska hedefin adiyla. Secmek TASIR;
            // kullanici bunu okumadan yapmamali.
            assignableAssets = positions.map { position ->
                AssignableAsset(
                    position = position,
                    assignedToThis = assignments[position.id]?.goalId == goal.id,
                    otherGoalName = otherGoalOf(position.id, goal, assignments)
                        ?.let { otherId -> goalNames[otherId] },
                )
            },
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

    /**
     * Katki tablosu: her ay ne konuldu, ay sonu ne oldu, ne kadari getiri.
     *
     * Ay sonu degeri o aya ait SON fotograftan gelir. Fotograf yoksa - ki yeni
     * kullanicida cogu ay boyledir - ay sonu ve getiri bos birakilir; bilinmeyen
     * yerine sifir yazmak "o ay hicbir sey kazanmadin" demek olurdu.
     */
    private fun buildRows(
        months: List<MonthlyContribution>,
        snapshots: List<DailySnapshot>,
    ): List<ContributionRow> {
        val lastOfMonth = snapshots.groupBy { it.date.monthOrdinal() }
            .mapValues { (_, list) -> list.maxByOrNull { it.date.day }?.totalValue }

        return months.map { month ->
            val ordinal = month.date.monthOrdinal()
            val end = lastOfMonth[ordinal]
            val start = lastOfMonth[ordinal - 1]
            ContributionRow(
                monthLabel = "${month.date.monthLabel()} ${month.date.year}",
                contribution = month.total,
                monthEnd = end,
                // Getiri = ay sonu - ay basi - o ay eklenen para. Iki ucundan
                // biri bilinmiyorsa hesaplanamaz.
                gain = if (end != null && start != null) end - start - month.total else null,
            )
        }.reversed()
    }

    /**
     * Grafigin altindaki "Kasım'da katkı yok" notunun konusu: katki YAPILAN iki
     * ayin ARASINDA kalan bos ay.
     *
     * Tasarim bu notu dolu bir yilin icindeki tek boslugu anlatmak icin
     * koymustu. Yeni kullanicida on ay bos oldugu icin not once "Haziran'da
     * katki yok" diyordu - hem gurultu, hem de cumlenin devami ("ay sonu deger
     * piyasa hareketiyle degisti") yanlisti: o ayin bir ay sonu degeri yok.
     * Gercek bir bosluk yoksa not hic cikmaz.
     */
    private fun gapMonth(months: List<MonthlyContribution>): MonthlyContribution? {
        val first = months.indexOfFirst { !it.isEmpty }
        val last = months.indexOfLast { !it.isEmpty }
        if (first < 0 || last - first < 2) return null
        return months.subList(first + 1, last).lastOrNull { it.isEmpty }
    }

    /** Tasarim: en yeni uc ay, ayrica katkisiz ay listede degilse o da eklenir. */
    private fun collapseRows(rows: List<ContributionRow>): List<ContributionRow> {
        val head = rows.take(3)
        val empty = rows.firstOrNull { it.contribution <= 0.0 }
        return if (empty == null || empty in head) head else head + empty
    }
}

// --- Senaryo ---------------------------------------------------------------

/** Kaydirici acilis konumu: mevcut katkinin 15 bin TL uzeri. */
private const val ScenarioOpeningStep = 15f

/**
 * Kaydiricinin verdigi katkiyla hedefe varis.
 *
 * Tahmin egrisiyle AYNI kurali kullanir ([monthsToReach]). Once ayri bir formul
 * vardi - `K / (katki + atalet)` - ve icindeki "atalet" terimi birikimin
 * kendiliginden buyudugunu varsayiyordu. Uydurmaydi ve sonucu da yanlisti:
 * aylik katkiyi ikiye katlamak "8 ay SONRA" diyebiliyordu, cunku karsilastirma
 * hedef tarihine gore yapiliyordu, bugunku tahmine gore degil.
 *
 * Karsilastirma artik SIMDIKI PLANA gore: "bu kadar ay erken/gec" derken neye
 * gore erken oldugu bellidir.
 */
private fun GoalDetailUiState.withScenario(thousands: Float): GoalDetailUiState {
    val goal = goal ?: return copy(scenarioContribution = thousands)

    val months = monthsToReach(currentWealth, goal.amount, thousands * 1000.0)
    val baseMonths = monthsToReach(currentWealth, goal.amount, goal.monthlyContribution)

    return copy(
        scenarioContribution = thousands,
        scenarioMonths = months,
        scenarioArrival = months
            ?.let { today.plusMonths(it) }
            ?.let { "${it.monthName()} ${it.year}" }
            .orEmpty(),
        // Ikisinden biri hesaplanamiyorsa kiyas da yapilamaz.
        scenarioDiffMonths = if (months != null && baseMonths != null) {
            months - baseMonths
        } else {
            null
        },
    )
}
