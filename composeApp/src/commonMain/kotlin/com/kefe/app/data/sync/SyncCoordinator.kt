package com.kefe.app.data.sync

import com.kefe.app.data.remote.RealtimeApi
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Bulutla esitlemenin GORUNEN durumu.
 *
 * FIYAT TAZELIGIYLE ILGISI YOKTUR. Once ekrandaki "Eşit / Çevrimdışı" cipi ve
 * "Kayıt cihazda tutulur; bağlanınca eşitlenir" seridi fiyat tazeliginden
 * suruluyordu: ucretsiz fiyat ucu tokezleyince uygulama, senkron gayet
 * calisirken bile kendini cevrimdisi ilan ediyor ve kaydi "Bekliyor" damgasiyla
 * DISKE yaziyordu. Adim 9b'nin acik notu ("çip aslında fiyat tazeliğini
 * gösteriyor, bulut senkronunu değil") tam olarak buydu; adim 11 gercek bulut
 * sinyalini getirdigi icin ayrim artik yapilabiliyor.
 */
enum class CloudState {
    /** Giris yapilmamis: esitleme kapali, kayitlar yalniz bu cihazda. */
    Off,

    /** Girisli ve son alisveris basarili. */
    Synced,

    /** Girisli ama son push/pull patladi: kayitlar cihazda bekliyor. */
    Unreachable,
}

/**
 * Push ve pull'u NE ZAMAN calistiracagina karar veren yer. Tamamen olay-guduml u,
 * ARKA PLAN TICKER'I YOK:
 *
 *   1. Girisli oldugumuz surece [SyncLocalSource.localChanges] dinlenir - yerelde
 *      bir yazma olunca (SQLDelight tablo bildirimi) push tetiklenir.
 *   2. debounce: ard arda yazmalar (bir islem + pozisyon yeniden hesabi +
 *      aktivite hepsi tek saniyede) tek push'a toplanir.
 *   3. localChanges'in ILK emisyonu acilis/giris push'ini da kapsar: dinlemeye
 *      baslar baslamaz bir kez emit eder, yani girer girmez yereldeki her sey
 *      (watermark 0'dan) sunucuya gider.
 *   4. GERCEK ZAMANLI (adim 11): girisli VE uygulama ON PLANDA iken
 *      [RealtimeApi.serverChanges] dinlenir - karsi cihazin yazdigi, biz hicbir
 *      seye dokunmadan pull tetikler.
 *
 * SUREC OMURLU. start() Compose agacindan cagrilir; Android'de Activity yeniden
 * yaratilinca Koin grafigi (dolayisiyla bu nesne) yeniden kurulur - tipki
 * veritabani gibi. Isler companion'daki TEK scope'ta ve TEK sefer baslar, yoksa
 * her donuste yeni bir dinleyici sizar ve ayni degisiklik defalarca push'lanirdi.
 * Bagimliliklar hep kalici veritabanina dayandigi icin ilk kurulumunkiler gecerli
 * kalir. start() ana is parcacigindan geldigi icin bayrak yalin olabilir.
 */
class SyncCoordinator(
    private val authRepository: AuthRepository,
    private val localSource: SyncLocalSource,
    private val pushEngine: PushEngine,
    private val pullEngine: PullEngine,
    private val realtimeApi: RealtimeApi,
) {

    // Conflated: bekleyen istek zaten varken gelen yenisi eskiyi duser - kuyruk
    // sismez, her tetik "en guncel haliyle bir kez daha push'la" demek.
    private val pushRequests = Channel<String>(Channel.CONFLATED)

    // Pull istekleri ayri kanal: giriste, her push'tan sonra ve realtime sinyalinde.
    private val pullRequests = Channel<Unit>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!claimStart()) return

        // Tek tuketici: seri push. Hata kullaniciya YANSIMAZ - watermark
        // ilerlemedigi icin veri kaybi yok, degisim bir sonraki tetikte yeniden
        // denenir. Yalniz tanisal bir satir birakiriz (logcat/stdout): sessiz bir
        // senkron, calisan bir senkrondan ayirt edilemez olurdu.
        //
        // Push'tan SONRA pull tetiklenir: benimkini gonderdim, simdi seninkini al.
        processScope.launch {
            for (userId in pushRequests) {
                runCatching { pushEngine.pushOnce(userId) }
                    .onSuccess { markReachable() }
                    .onFailure {
                        println("Kefe senkron: push basarisiz - ${it.message}")
                        markUnreachable()
                    }
                pullRequests.trySend(Unit)
            }
        }

        // Tek tuketici: seri pull. Ayni gerekce - hata yutulur, tanisal log kalir.
        processScope.launch {
            for (unit in pullRequests) {
                runCatching { pullEngine.pullOnce() }
                    .onSuccess { markReachable() }
                    .onFailure {
                        println("Kefe senkron: pull basarisiz - ${it.message}")
                        markUnreachable()
                    }
            }
        }

        // Oturum acikken degisimleri dinle, kapaninca birak. Girer girmez BIR pull
        // istenir: yerel degisiklik olmasa da (bos ikinci telefon) sunucudakini ceker.
        processScope.launch {
            var listener: Job? = null
            authRepository.observeAuthState().collect { state ->
                val userId = (state as? AuthState.SignedIn)?.session?.userId?.takeIf { it.isNotBlank() }
                // Cikisliyken bulut KAPALI - "ulasilamiyor" degil. Ikisini ayni
                // gostermek "baglanmayi bekle" izlenimi verirdi; oysa giris
                // yapilana kadar esitlenecek bir sey yok.
                cloudStateFlow.value = if (userId == null) CloudState.Off else CloudState.Synced
                if (userId != null) {
                    if (listener == null) {
                        pullRequests.trySend(Unit)
                        listener = processScope.launch {
                            localSource.localChanges()
                                .debounce(DebounceMillis)
                                .collect { pushRequests.trySend(userId) }
                        }
                    }
                } else {
                    listener?.cancel()
                    listener = null
                }
            }
        }

        // Gercek zamanli dinleme. Ayri launch: yasam omru ustteki push
        // dinleyicisinden FARKLI - o yalniz girise, bu girise VE on plana bakar.
        processScope.launch {
            listenServerChanges(socketGates()) { pullRequests.trySend(Unit) }
        }
    }

    /** Uygulama on plana girdi/cikti. Compose agacindan surulur (bkz. App.kt). */
    fun setForeground(active: Boolean) {
        foreground.value = active
    }

    /** Ekranin okudugu bulut durumu - fiyat tazeliginden BAGIMSIZ. */
    fun cloudState(): StateFlow<CloudState> = cloudStateFlow.asStateFlow()

    // Cikisliyken basari/hata bildirimi gelirse durum Off kalmali: motorlar
    // jetonsuz sessizce cikiyor, o "basari" bulut aciktir demek degil.
    private fun markReachable() {
        if (cloudStateFlow.value != CloudState.Off) cloudStateFlow.value = CloudState.Synced
    }

    private fun markUnreachable() {
        if (cloudStateFlow.value != CloudState.Off) cloudStateFlow.value = CloudState.Unreachable
    }

    /**
     * "Soket ne zaman acik olmali": girisli VE on planda iken true.
     *
     * Arka planda soket KAPANIR - acik kalsa Phoenix heartbeat'i kullanicinin
     * hic bakmadigi bir ekran icin pil yakardi. Bedeli, kapaliyken olan
     * degisiklikleri kacirmak; onu da geri donusteki tek pull toparlar.
     */
    internal fun socketGates(): Flow<Boolean> =
        combine(authRepository.observeAuthState(), foreground) { state, active ->
            state is AuthState.SignedIn && state.session.userId.isNotBlank() && active
        }.distinctUntilChanged()

    /**
     * [gates] true oldugu her aralikta: ONCE bir pull istenir (soket kapaliyken
     * olanlari toparlar), sonra realtime sinyalleri dinlenir. false olunca
     * dinleme biter - collectLatest iptali bedavaya yapar.
     *
     * Ayri fonksiyon ve pull istegi disaridan (`onPullNeeded`) veriliyor cunku
     * [start] surec-omurlu bayrakla korunuyor, testten ikinci kez cagrilamaz;
     * buraya sahte kapilarla dogrudan girilebilir.
     */
    @OptIn(FlowPreview::class)
    internal suspend fun listenServerChanges(gates: Flow<Boolean>, onPullNeeded: () -> Unit) {
        gates.collectLatest { open ->
            if (!open) {
                println("Kefe senkron: realtime dinleme kapali (cikis ya da arka plan)")
                return@collectLatest
            }
            onPullNeeded()
            realtimeApi.serverChanges()
                // Karsi tarafta tek islem 3-4 tabloya dokunur; hepsi tek pull olsun.
                .debounce(RealtimeDebounceMillis)
                .collect {
                    // Tanisal: baglanmis ama HIC OLAY GELMEYEN bir soket, calisan
                    // bir soketle disaridan ayni gorunur. Tablolar
                    // supabase_realtime yayinina eklenmemisse tam boyle olur.
                    println("Kefe senkron: realtime sinyali - pull isteniyor")
                    onPullNeeded()
                }
        }
    }

    private companion object {
        // Yazma firtinasi dinsin diye kisa bekleme; ekleme sonrasi push'i gozle
        // gorulur geciktirmeyecek kadar da kisa.
        const val DebounceMillis = 1500L

        // Realtime sinyalleri icin daha kisa: burada beklenen sey bir kullanici
        // yazmasi degil, sunucudan gelen olay dizisi.
        const val RealtimeDebounceMillis = 1000L

        // Surec omurlu: Koin yeniden kurulsa da isler burada tek sefer yasar.
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var started = false

        // Uygulama on planda mi. Companion'da: Activity yeniden yaratilip Koin
        // grafigi degisse de surec-omurlu dinleyici ayni bayragi okur.
        private val foreground = MutableStateFlow(false)

        // Bulut durumu da surec-omurlu: isleri tutan scope burada, durumu baska
        // yerde tutmak Activity donusunde ekrani "Off"a dusururdu.
        private val cloudStateFlow = MutableStateFlow(CloudState.Off)

        /** Ilk cagri true, sonrakiler false. Ana is parcacigindan cagrilir. */
        fun claimStart(): Boolean {
            if (started) return false
            started = true
            return true
        }
    }
}
