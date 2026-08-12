package com.kefe.app.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefe.app.data.sync.CloudState
import com.kefe.app.data.sync.SyncCoordinator
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
import com.kefe.app.domain.model.goalProjection
import com.kefe.app.domain.model.goalWealth
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.model.portfolioTotals
import com.kefe.app.domain.model.todayChangePercent
import com.kefe.app.domain.model.topGainer
import com.kefe.app.domain.model.topLoser
import com.kefe.app.domain.repository.PortfolioRepository
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import com.kefe.app.domain.repository.PriceRepository
import com.kefe.app.domain.repository.RefreshOutcome
import com.kefe.app.domain.model.sellPrice
import com.kefe.app.ui.screens.market.priceDecimals
import com.kefe.app.ui.format.Money
import com.kefe.app.ui.layout.KefeMarketRow
import com.kefe.app.domain.model.KefeDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
    // Baslikta "Eşit / Çevrimdışı" cipi BUNU gosterir - fiyat tazeligini degil.
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryUiState())

    /**
     * "Açılışta bakiyeyi gizle" tercihi yalniz ILK emisyonda uygulanir.
     *
     * preferences.observeAll() her ayar degisiminde emisyon yapiyor; kosulsuz
     * baglansaydi kullanicinin goz simgesine dokunup actigi bakiye, bir sonraki
     * tercih emisyonunda tekrar kapanirdi. Bu bayrak ilk okumadan sonra kilitler.
     */
    private var maskInitialized = false
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    /**
     * Acilis akisi gecildi mi. Ekranin durumuna DEGIL kabuga ait - hangi ekranla
     * acilacagini o karar veriyor - bu yuzden ayri bir akis.
     *
     * NULL = HENUZ BILINMIYOR. Once false ile basliyordu ve "diske bakilmadi" ile
     * "girilmemis" ayirt edilemiyordu: oturumu acik bir kullanicida uygulama
     * acilisinda bir an giris ekranini gosterip sonra ana ekrana atliyordu.
     * Kabuk artik null oldugu surece hicbir ekran cizmez.
     */
    val onboarded: StateFlow<Boolean?> = portfolioRepository.observeOnboarded()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** En son yazilan fotograf - ayni degerin tekrar yazilmasini onler. */
    private var lastRecorded: DailySnapshot? = null

    init {
        observeData()
        observeHistory()
        observePrices()
        observeMaskPreference()
        observeCloud()
        refresh()
    }

    /**
     * Bulut durumu fiyat tazeliginden AYRI. 9b'nin acik notu buydu: baslikta
     * "Eşit" yazan cip aslinda fiyatlarin tazeligini gosteriyordu, esitlemeyi
     * degil - fiyat ucu tokezleyince senkron calisirken "Çevrimdışı" yaziyordu.
     */
    private fun observeCloud() {
        viewModelScope.launch {
            syncCoordinator.cloudState().collect { cloud ->
                _state.value = _state.value.copy(cloudState = cloud)
            }
        }
    }

    private fun observeMaskPreference() {
        viewModelScope.launch {
            preferences.observeAll().collect { prefs ->
                if (maskInitialized) return@collect
                maskInitialized = true
                // Varsayilan GIZLI: bakiye omuz ustunden bakisa kapali baslasin.
                val hide = prefs[PreferenceKeys.HideBalanceOnStart]?.toBooleanStrictOrNull() ?: true
                _state.value = _state.value.copy(masked = hide)
            }
        }
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
            // Aralik degisince aga cikilmaz: seri elde, yalniz penceresi degisir.
            is SummaryIntent.SelectPeriod -> applyRange(intent.range)
            SummaryIntent.Refresh -> refresh()
            SummaryIntent.DismissRefreshError -> _state.value =
                _state.value.copy(refreshError = null)

            SummaryIntent.DismissRefreshNotice ->
                _state.value = _state.value.copy(refreshNotice = null)
        }
    }

    /**
     * GUN DONUNCE ekran kendiliginden tazelensin.
     *
     * `clock.today()` yalniz akis emisyonunda okunuyordu: uygulama gece
     * boyunca acik kalirsa ve arada yeni bir emisyon olmazsa (ag yok, kayit
     * girilmedi) "Bu ay eklenen" dunun ayina gore hesaplanmis halde asili
     * kaliyor, kotasyon-gunu kapisi da dunku gune bakiyordu.
     *
     * Dakikada bir bakilir ama YALNIZ GUN DEGISTIGINDE emisyon olur
     * ([distinctUntilChanged]); yani saatte altmis uyanma, sifir gereksiz
     * yeniden hesap. Piyasa gunu sorulur, cihazin gunu degil - kotasyon
     * gunleriyle kiyaslanan tarih o (bkz. KefeClock.marketToday).
     */
    private fun dayTicker(): Flow<KefeDate> = flow {
        while (true) {
            emit(clock.marketToday())
            delay(DayCheckMillis)
        }
    }.distinctUntilChanged()

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
                snapshot to transactions
            }.combine(portfolioRepository.observeGoalAssets()) { pair, assignments ->
                Triple(pair.first, pair.second, assignments)
            }.combine(dayTicker()) { triple, today ->
                val (snapshot, transactions, assignments) = triple
                val (portfolio, members, positions, goals, activity) = snapshot
                val main = goals.firstOrNull { it.isMain }
                _state.value.copy(
                    stage = if (positions.isEmpty()) SummaryStage.Empty else SummaryStage.Ready,
                    portfolioName = portfolio.name,
                    members = members,
                    totals = portfolioTotals(
                        positions = positions,
                        transactions = transactions,
                        today = today,
                        // Aylik katki hedefi ana hedeften gelir; turetilebilir bir sey degil.
                        monthTarget = main?.monthlyContribution ?: 0.0,
                    ),
                    allocation = positions.allocation(),
                    mainGoal = main,
                    // Kati atama: ana hedef ilerlemesi YALNIZ kendine atanan
                    // varliklardan; atama yoksa 0 (kart %0 gosterir).
                    mainGoalWealth = main
                        ?.let { goalWealth(it, positions, assignments) }
                        ?: 0.0,
                    mainGoalArrival = main?.let {
                        goalProjection(it, goalWealth(it, positions, assignments), today)
                            .arrival
                    },
                    // Vadesi gecmis hedef de sayilir: tasarimda o hal "Hedef duruyor"
                    // diyor, kapatilmis degil. Yalniz tamamlananlar dislanir.
                    // Ana hedefin KENDISI dislanir - "acik sayidan bir eksik"
                    // degil. Tamamlanmis bir hedef ana hedef olarak kalabilir ve
                    // o zaman acik sayida zaten yoktur; eksiltmek sayiyi bir
                    // dusuruyordu.
                    otherGoalCount = goals.count { it.isOpen() && it.id != main?.id },
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
                // Gercek fotograflar. Ornek seri kullanilamaz: kullanicinin
                // kendi rakami tepede dururken altinda baskasinin egrisini
                // cizmek "param buyumus" dedirtirdi.
                snapshots = history
                applyRange(_state.value.range)
            }
        }
    }

    /** Aralik uygulanmadan onceki TUM fotograflar; pencere bundan kesilir. */
    private var snapshots: List<DailySnapshot> = emptyList()

    /**
     * Secili araligi seriye uygular.
     *
     * Cipler once yalniz basliktaki aciklamayi degistiriyordu - egri her zaman
     * tum seriydi, yani "3A" ile "Tümü" ayni resmi veriyordu.
     */
    private fun applyRange(range: NetWorthRange) {
        val days = range.days
        val visible = if (days == null) {
            snapshots
        } else {
            val cutoff = clock.today().toEpochDay() - days
            snapshots.filter { it.date.toEpochDay() >= cutoff }
        }
        _state.value = _state.value.copy(
            range = range,
            netWorthTotal = visible.map { it.totalValue },
            netWorthPrincipal = visible.map { it.principal },
            netWorthSnapshotCount = snapshots.size,
        )
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
                    //
                    // Eksik kur NULL kalir, 1.0'a DUSMEZ: 1.0 TL tutarini oldugu
                    // gibi dolar diye yazdiriyordu (bkz. DisplayUnit.rateFor).
                    //
                    // TARAF, PORTFOY DEGERLEMESIYLE AYNI. Kur `ask`ten
                    // (odeyecegimiz fiyat) aliniyordu ama pay zaten `sellPrice`
                    // ile, yani makasin alt ucundan hesaplanmis bir TL toplami:
                    // bolen makasin ust ucundan gelince makas IKI KEZ dusuluyor
                    // ve dolar karsiligi oldugundan kucuk cikiyordu. Ayni
                    // varligin iki farkli fiyatini tek bolmede kullanmak, hangi
                    // sayinin ne demek oldugunu belirsizlestirir.
                    rates = UnitRates(
                        usdTry = board.byKey("usd_try")?.sellPrice()?.takeIf { it > 0.0 },
                        eurTry = board.byKey("eur_try")?.sellPrice()?.takeIf { it > 0.0 },
                        goldGramTry = board.byKey("gold_gram")?.sellPrice()?.takeIf { it > 0.0 },
                    ),
                    // Ozet'teki piyasa karti ve masaustu sag paneli.
                    //
                    // Fiyatlar kurusla yazilir - portfoy tutarlari gibi tam
                    // liraya yuvarlanmaz. Gram altin dakikalar icinde kurus
                    // mertebesinde oynuyor; yuvarlaninca tablo "hic degismiyor"
                    // gibi gorunuyordu, oysa deger her yenilemede tazeleniyordu.
                    //
                    // HANE SAYISI PIYASA EKRANIYLA AYNI KURALDAN gelir. Burasi
                    // sabit iki haneydi ve ayni kotasyon iki ekranda farkli
                    // yaziliyordu: Ata altini Ozet'te "₺45.375,74", Piyasa'da
                    // "₺45.376". Kural degerden turer (bkz. priceDecimals) -
                    // buyuk rakamda kurus zaten sutuna sigmiyor.
                    marketRows = board.prices.map { price ->
                        KefeMarketRow(
                            name = price.label,
                            priceText = Money.tl(
                                price.ask,
                                decimals = price.assetClass.priceDecimals(price.ask),
                            ),
                            // Kotasyon gunu kapisindan GECER - hemen ustundeki
                            // "bugün" satiri da oradan geciyor. Once ham
                            // changePercent okunuyordu ve pazar gunu ayni
                            // ekranda kart cumanin hareketini yazarken "bugün"
                            // satiri dogru sekilde ₺0 gosteriyordu.
                            // PIYASA gunu: kotasyon gunleri kaynagin
                            // takviminden geliyor (bkz. KefeClock.marketToday).
                            changePercent = price.todayChangePercent(clock.marketToday()),
                            assetClass = price.assetClass.color(),
                        )
                    },
                )
            }
        }
    }

    /**
     * Yalniz cekme yapar; yeni fiyatlari yukaridaki tek toplayici gorur.
     *
     * Sonuc ATILAMAZ: basarisiz yenileme ekranda hicbir iz birakmiyordu ve
     * basarili olanindan ayirt edilemiyordu. Fiyatlar dakikalar icinde cok az
     * oynadigi icin "yenilemiyor" sanilan sey buydu.
     */
    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                refreshing = true,
                refreshError = null,
                refreshNotice = null,
            )
            val result = priceRepository.refresh()
            val error = result.exceptionOrNull()
            _state.value = _state.value.copy(
                refreshing = false,
                // Sebep de yazilir. "Güncellenemedi" tek basina ne kullaniciya
                // ne bize bir sey soyluyor: ag mi yok, kaynak mi dustu, sertifika
                // mi bozuk - hepsi ayni cumleye cikiyordu.
                refreshError = error?.let {
                    "Fiyatlar güncellenemedi — son bilinen değerler gösteriliyor. " +
                        "(${it.shortReason()})"
                },
                // Kisitlanan yenileme HATA DEGIL. Ayri bir alanda tasinir ve
                // ekranda kirmizi degil vurgu renginde cikar; kullanici yanlis
                // bir sey yapmadi, elindeki zaten taze.
                refreshNotice = (result.getOrNull() as? RefreshOutcome.Throttled)?.let {
                    "Fiyatlar az önce güncellendi — ${it.retryInSeconds} sn sonra tekrar denenebilir."
                },
            )
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

/** Gun degisimini yakalamak icin yeterli siklik - dakikada bir. */
private const val DayCheckMillis = 60_000L

/**
 * Hatanin kisa sebebi.
 *
 * Ortak kodda gunluk altyapisi yok; sebep ekranda gorunmezse hicbir yerde
 * gorunmuyor. Tip adi cogu zaman yeterli (UnknownHostException, SSLException),
 * mesaj varsa daha aciklayici oldugu icin o tercih edilir.
 */
private fun Throwable.shortReason(): String {
    val text = message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "bilinmeyen hata"
    return text.take(120)
}
