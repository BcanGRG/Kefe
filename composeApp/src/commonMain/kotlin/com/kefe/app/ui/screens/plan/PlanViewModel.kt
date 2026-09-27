package com.kefe.app.ui.screens.plan

import androidx.lifecycle.viewModelScope
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.Goal
import com.kefe.app.domain.model.GoalAssignment
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Member
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.repository.PlanRepository
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceBoard
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.ui.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plan sekmesi. Akislari toplar ve niyetleri isler; butun hesap saf
 * turetimdedir (bkz. PlanDerive.kt).
 *
 * KABUKTA yasar (GoalsViewModel gibi): sheet'leri kabuk tek yerde cizer ve yan
 * menunun Plan rozeti de buradan beslenir. Bu yuzden surec boyunca yasar; secili
 * ay, sinirlar daralinca (ayin tek islemi silindi) sinirlarin disinda kalabilir -
 * turetim onu bu aya kirpar (bkz. PlanInputs.month) ve secim birakilir. Gecen ay
 * veri olmadan da sinir icinde kaldigi icin "Tüm verileri sil"de kabuk secimi
 * ayrica sifirlar (ThisMonth).
 *
 * "BUGUN" NEREDEN GELIR: yalniz [dayTicks]'ten. Saat ([clock]) tek bir kez, gun
 * akisini tohumlamak icin okunur. NEDEN: testler gun akisini elle ilerletir
 * (sabit saat 22 Ekim'de kalir); saatten okuyan tek bir satir gece yarisi
 * donumunde ve gun donumu testlerinde sayfayla farkli bir "bugun" gorurdu.
 */
class PlanViewModel(
    private val planRepository: PlanRepository,
    private val portfolioRepository: PortfolioRepository,
    private val priceRepository: PriceRepository,
    private val preferences: PreferencesRepository,
    clock: KefeClock,
    /** Testler sabit/elle ilerleyen bir akis verir; sonsuz delay dongusu runTest'i kilitlemesin. */
    dayTicks: Flow<KefeDate> = deviceDays(clock),
) : MviViewModel<PlanUiState, PlanIntent, PlanEffect>(PlanUiState()) {

    // SIRA ONEMLI: Kotlin ozellik ilklendiricilerini ve init bloklarini METIN
    // sirasiyla calistirir. observe() init icinde baslar ve viewModelScope'ta
    // hemen kosabilir (uygulamada Main.immediate, testte Unconfined); init'in
    // ALTINDA tanimlanan bir ozellik o anda henuz null olurdu.

    private val today: StateFlow<KefeDate> =
        dayTicks.stateIn(viewModelScope, SharingStarted.Eagerly, clock.today())

    /** null = "bu ay": gun donunce sayfa kendiliginden yeni aya gecer. */
    private val selection = MutableStateFlow<YearMonth?>(null)

    /** Son turetimin girdisi; niyetler bunu okur (null iken yuklenme suruyor, yok sayilir). */
    private var latest: PlanInputs? = null

    init {
        // Yuklenirken de baslik dogru ayi gosterir; gecis dugmeleri Ready'ye kadar kapali.
        val day = today.value
        val bounds = planMonthBounds(day, items = emptyList(), transactions = emptyList(), books = emptyList())
        setState { copy(content = PlanContent(header = planHeader(YearMonth.of(day), day, bounds))) }
        observe()
    }

    override fun onIntent(intent: PlanIntent) {
        when (intent) {
            // Girdiye bakmaz: secimi bosaltmak yuklenirken de dogrudur. Kabuk onu
            // "Tüm verileri sil"den sonra da gonderir (bkz. App.kt).
            PlanIntent.ThisMonth -> selection.value = null
            // Gecisler son turetimin ayina gore; yuklenirken (latest null) yok sayilir.
            PlanIntent.PreviousMonth -> latest?.let { inputs ->
                if (current.content.header.canGoBack) selectMonth(inputs.month.previous(), inputs)
            }
            PlanIntent.NextMonth -> latest?.let { inputs ->
                if (current.content.header.canGoForward) selectMonth(inputs.month.next(), inputs)
            }
        }
    }

    /**
     * Bu aya donuluyorsa secim BOSALTILIR: "bu ay" gun donunce kendiliginden
     * ilerlemeli; sabit bir Ekim secimi Kasim'da gecmis ay olarak kalirdi.
     * Sinir disi secim burada degil turetimde kirpilir (bkz. PlanInputs.month) ve
     * toplayicida birakilir.
     */
    private fun selectMonth(target: YearMonth, inputs: PlanInputs) {
        selection.value = target.takeIf { it != inputs.current }
    }

    private fun observe() {
        val ledger = combine(
            planRepository.observePlanItems(),
            portfolioRepository.observeAllTransactions(),
            portfolioRepository.observeAllPositions(),
            portfolioRepository.observeGoals(),
            portfolioRepository.observeGoalAssets(),
        ) { items, transactions, positions, goals, assignments ->
            Ledger(items, transactions, positions, goals, assignments)
        }

        val context = combine(
            portfolioRepository.observeMembers(),
            priceRepository.observePrices(),
            preferences.observeAll().map { it[PreferenceKeys.ActiveMemberId] }.distinctUntilChanged(),
        ) { members, board, active -> Context(members, board, active) }

        viewModelScope.launch {
            // Defter TEK abonelikle gelir (observeAllBooks); ay ve gun secimi yalniz yeniden
            // TURETIR. NEDEN: baslik ve defter hep ayni PlanInputs'tan cikar - aya gore ayri
            // abonelikte baslik yeni ayi, defter bir kare eski ayi gosterebilirdi.
            combine(ledger, context, planRepository.observeAllBooks(), selection, today) { l, c, books, sel, day ->
                PlanInputs(
                    selection = sel,
                    today = day,
                    items = l.items,
                    transactions = l.transactions,
                    positions = l.positions,
                    goals = l.goals,
                    assignments = l.assignments,
                    members = c.members,
                    board = c.board,
                    activeMemberId = c.activeMemberId,
                    books = books,
                )
            }
                // Turetim (butun aylarin serisi dahil) ana is parcaciginda yapilmaz: fiyat tiki hem
                // pozisyonlari hem fiyat tablosunu yeniden yayar ve iki turetim ust uste gelir.
                // conflate: yetisilemeyen ara sonuc atlanir, en yenisi cizilir.
                .map { inputs -> inputs to planContent(inputs) }
                .flowOn(Dispatchers.Default)
                .conflate()
                .collect { (inputs, content) ->
                    // Sinirin disina dusup bu aya kirpilan secim BIRAKILIR. NEDEN: kirpma
                    // yalniz turetimde kalsaydi, ayin verisi geri geldiginde (esitleme,
                    // yedekten donus) sayfa kullanici bir sey yapmadan o aya atlardi.
                    // Yalniz secim hala buysa: arada secilen yeni bir aya dokunulmaz.
                    // Durumdan ONCE: bu durumu goren her okuyucu secimi de bos gorur.
                    if (inputs.selectionClamped) selection.compareAndSet(inputs.selection, null)
                    latest = inputs
                    // Durum YALNIZ burada indirgenir; donusum (map) icinde durum okunmaz -
                    // Default'ta calisir ve arada yapilan bir degisikligi ezerdi.
                    setState { copy(stage = PlanStage.Ready, content = content) }
                }
        }
    }
}

/**
 * Cihazin gunu - dakikada bir bakilir, YALNIZ gun degisince yayilir.
 *
 * SummaryViewModel.dayTicker ile ayni gerekce (gece acik kalan uygulama dunun
 * ayinda asili kalmasin), ama yalniz CIHAZ gunu: plan ayi cihaz gunune gore
 * belirlenir (bkz. monthPlanProgress) - piyasa gunu ay donumunde bir alimi iki
 * aydan da dusururdu.
 */
internal fun deviceDays(clock: KefeClock): Flow<KefeDate> = flow {
    while (true) {
        emit(clock.today())
        delay(DayCheckMillis)
    }
}.distinctUntilChanged()

/** combine() bes akisa kadar tiplenir; defter tarafi tek tasiyicida toplanir. */
private data class Ledger(
    val items: List<PlanItem>,
    val transactions: List<Transaction>,
    val positions: List<Position>,
    val goals: List<Goal>,
    val assignments: Map<String, GoalAssignment>,
)

/** Kimlik ve fiyat tarafi: uyeler, fiyat tablosu, bu cihazin profili. */
private data class Context(
    val members: List<Member>,
    val board: PriceBoard,
    val activeMemberId: String?,
)

/** Gun degisimini yakalamak icin yeterli siklik - dakikada bir. */
private const val DayCheckMillis = 60_000L
