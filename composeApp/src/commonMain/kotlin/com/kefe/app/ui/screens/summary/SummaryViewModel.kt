package com.kefe.app.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sample.SampleSeries
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ActivityEvent
import com.kefe.app.domain.model.DailySnapshot
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
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.layout.KefeMarketRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    /**
     * Acilis akisi gecildi mi. Ekranin durumuna DEGIL kabuga ait - hangi ekranla
     * acilacagini o karar veriyor - bu yuzden ayri bir akis.
     */
    val onboarded: StateFlow<Boolean> = portfolioRepository.observeOnboarded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** En son yazilan fotograf - ayni degerin tekrar yazilmasini onler. */
    private var lastRecorded: DailySnapshot? = null

    init {
        observeData()
        observeHistory()
        observePrices()
        refresh()
    }

    /** Giris/onboarding tamamlandi - bir daha sorulmayacak. */
    fun markOnboarded() {
        viewModelScope.launch { portfolioRepository.markOnboarded() }
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
                    topGainer = positions.topGainer(),
                    topLoser = positions.topLoser(),
                    positionCount = positions.size,
                    openGoalCount = goals.count { it.isOpen() },
                )
            }.collect { next ->
                _state.value = next
                recordTodaySnapshot(next)
            }
        }
    }

    /**
     * Net deger gecmisi - grafigin kaynagi.
     *
     * AYRI bir toplayici olmak ZORUNDA. Yukaridaki combine'in icindeyken her
     * emisyon [recordTodaySnapshot] cagiriyordu; yazma SQLDelight dinleyicisini
     * tetikliyor, dinleyici combine'i yeniden calistiriyor, o da yeniden
     * yaziyordu. Kapanmayan bir dongu: uygulama bosta dururken CPU'nun ucte
     * birini yakiyor ve ekran titriyordu. Buradan yazma YAPILMAZ.
     */
    private fun observeHistory() {
        viewModelScope.launch {
            portfolioRepository.observeSnapshots().collect { history ->
                _state.value = _state.value.copy(
                    // Gercek fotograflar. Ornek seri kullanilamaz: kullanicinin
                    // kendi rakami tepede dururken altinda baskasinin egrisini
                    // cizmek "param buyumus" dedirtirdi.
                    netWorthTotal = history.map { it.totalValue },
                    netWorthPrincipal = history.map { it.principal },
                )
            }
        }
    }

    /**
     * Gunun fotografini ceker.
     *
     * Gecmis bir gunun degeri sonradan hesaplanamaz - o gunku fiyatlari da bilmek
     * gerekir. Toplam her degistiginde yazmak, seriyi biriktirmenin tek yolu.
     */
    private fun recordTodaySnapshot(state: SummaryUiState) {
        val totals = state.totals ?: return
        // Bos portfoy icin fotograf cekmek seriyi sifirlarla doldururdu.
        if (state.positionCount == 0) return

        val snapshot = DailySnapshot(
            date = clock.today(),
            totalValue = totals.totalValue,
            principal = totals.principal,
        )
        // Ayni degeri yeniden yazmak bir sey degistirmez ama dinleyicileri
        // uyandirir. Ikinci bir savunma hatti: yukaridaki dongu geri gelse bile
        // burada durur.
        if (snapshot == lastRecorded) return
        lastRecorded = snapshot

        viewModelScope.launch { portfolioRepository.recordSnapshot(snapshot) }
    }

    /**
     * Fiyatlari BIR KEZ dinler.
     *
     * Toplama eskiden refresh() icindeydi ve refresh her yenilemede tekrar
     * cagriliyordu: her cagri yeni bir toplayici aciyor, eskisi kapanmiyordu.
     * Depo soguk bir akis donduruyor, yani her toplayici cached_prices ve
     * manual_prices icin AYRI birer SQLDelight dinleyicisi kaydediyor. Kullanici
     * asagi cektikce dinleyiciler birikiyor ve her fiyat yazmasinda durum N kez
     * guncelleniyordu.
     */
    private fun observePrices() {
        viewModelScope.launch {
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

    /** Yalniz cekme yapar; sonucu yukaridaki tek toplayici gorur. */
    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            priceRepository.refresh()
            _state.value = _state.value.copy(refreshing = false)
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
