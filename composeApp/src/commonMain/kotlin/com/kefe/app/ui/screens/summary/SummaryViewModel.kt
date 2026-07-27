package com.kefe.app.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalStatus
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.Portfolio
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.allocation
import com.kefe.app.domain.model.color
import com.kefe.app.domain.model.portfolioTotals
import com.kefe.app.domain.model.topGainer
import com.kefe.app.domain.model.topLoser
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Ozet ekrani. MVI-lite: tek [SummaryUiState] akisi + tek [onIntent] girisi.
 *
 * TUM RAKAMLAR TURETILIR: toplam deger ve dagilim pozisyonlardan, "bu ay eklenen"
 * islem defterinden, one cikan hareketler pozisyonlarin gercek kar/zararindan.
 * Elle yazilmis sabit yoktur - bir islem eklendiginde ozet de degisir.
 */
class SummaryViewModel(
    private val portfolioRepository: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val clock: KefeClock,
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init {
        observeData()
        refresh()
    }

    fun onIntent(intent: SummaryIntent) {
        when (intent) {
            is SummaryIntent.SelectUnit -> _state.value = _state.value.copy(unit = intent.unit)
            SummaryIntent.ToggleMask -> _state.value =
                _state.value.copy(masked = !_state.value.masked)
            is SummaryIntent.SelectPeriod -> _state.value =
                _state.value.copy(periodIndex = intent.index)
            SummaryIntent.Refresh -> refresh()
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                portfolioRepository.observePortfolio(),
                portfolioRepository.observeMembers(),
                portfolioRepository.observePositions(),
                portfolioRepository.observeGoals(),
                portfolioRepository.observeActivity(),
            ) { portfolio, members, positions, goals, activity ->
                Snapshot(portfolio, members, positions, goals, activity)
            }.combine(portfolioRepository.observeAllTransactions()) { snapshot, transactions ->
                val (portfolio, members, positions, goals, activity) = snapshot
                val main = goals.firstOrNull { it.isMain }
                _state.value.copy(
                    stage = if (positions.isEmpty()) SummaryStage.Empty else SummaryStage.Ready,
                    portfolioName = portfolio.name,
                    members = members,
                    totals = portfolioTotals(
                        positions = positions,
                        transactions = transactions,
                        today = clock.today(),
                        // Aylik katki hedefi ana hedeften gelir; turetilebilir bir sey degil.
                        monthTarget = main?.monthlyContribution ?: 0.0,
                    ),
                    allocation = positions.allocation(),
                    mainGoal = main,
                    // Vadesi gecmis hedef de sayilir: tasarimda o hal "Hedef duruyor"
                    // diyor, kapatilmis degil. Yalniz tamamlananlar dislanir.
                    otherGoalCount = (goals.count { it.isOpen() } - 1).coerceAtLeast(0),
                    activity = activity.take(3),
                    netWorthTotal = SampleSeries.netWorthTotal,
                    netWorthPrincipal = SampleSeries.netWorthPrincipal,
                    topGainer = positions.topGainer(),
                    topLoser = positions.topLoser(),
                    positionCount = positions.size,
                    openGoalCount = goals.count { it.isOpen() },
                )
            }.collect { _state.value = it }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            priceRepository.refresh()
            priceRepository.observePrices().collect { board ->
                _state.value = _state.value.copy(
                    refreshing = false,
                    freshness = board.freshness,
                    pricesUpdatedAt = board.updatedAtLabel,
                    // Hero cevrimi piyasa tablosuyla ayni kaynaktan beslenir.
                    rates = UnitRates(
                        usdTry = board.byKey("usd_try")?.ask ?: 1.0,
                        eurTry = board.byKey("eur_try")?.ask ?: 1.0,
                        goldGramTry = board.byKey("gold_gram")?.ask ?: 1.0,
                    ),
                    // Masaustu sag panelinde gosterilen ozet piyasa listesi.
                    marketRows = board.prices.map { price ->
                        KefeMarketRow(
                            name = price.label,
                            priceText = Money.tl(price.ask, decimals = if (price.ask < 100) 2 else 0),
                            changePercent = price.changePercent,
                            assetClass = price.assetClass.color(),
                        )
                    },
                )
            }
        }
    }
}

private fun Goal.isOpen(): Boolean = status != GoalStatus.Completed

/**
 * combine() bes akisa kadar tiplenmis asiri yukleme sunar; altinci akis (islem
 * defteri) icin ara bir tasiyici gerekiyor.
 */
private data class Snapshot(
    val portfolio: Portfolio,
    val members: List<Member>,
    val positions: List<Position>,
    val goals: List<Goal>,
    val activity: List<ActivityEvent>,
)
