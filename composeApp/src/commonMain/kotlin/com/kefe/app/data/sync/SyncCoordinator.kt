package com.kefe.app.data.sync

import com.kefe.app.data.remote.RealtimeApi
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Surec boyunca yasayan senkron durumu: istek kanallari, bagli hesap, esitleme
 * durumu ve on plan bayragi.
 *
 * NEDEN AYRI NESNE. Kordinator Koin grafigiyle birlikte (Activity yeniden
 * yaratilinca) yeniden kurulur, isler ise surecte TEK sefer baslar. Kanallar
 * once ornek alanlariydi: ikinci grafigin kordinatorune gelen "Şimdi eşitle"
 * kimsenin dinlemedigi bir kanala dusuyordu. Hepsi burada toplanir; uretimde
 * [SyncCoordinator.Process] tektir, testler her seferinde tazesini verir.
 */
class SyncRuntime {
    // Conflated: bekleyen istek zaten varken gelen yenisi eskiyi duser - kuyruk
    // sismez, her tetik "en guncel haliyle bir kez daha push'la" demek.
    internal val pushRequests = Channel<String>(Channel.CONFLATED)

    // Pull istekleri ayri kanal: baglaninca, her push'tan sonra ve realtime sinyalinde.
    internal val pullRequests = Channel<Unit>(Channel.CONFLATED)

    /** Esitlemenin calistigi hesap; null = bagli degil, hicbir sey gitmez/gelmez. */
    internal val linkedUser = MutableStateFlow<String?>(null)

    /** Bagli cihazin son turunun sonucu. Baglanti degisince Syncing'e doner. */
    internal val status = MutableStateFlow(CloudStatus.Syncing)

    /** Uygulama on planda mi (soket yalniz o zaman acik). */
    internal val foreground = MutableStateFlow(false)

    internal var started = false
}

/**
 * Push ve pull'u NE ZAMAN calistiracagina karar veren yer. Tamamen olay-gudumlu,
 * ARKA PLAN TICKER'I YOK:
 *
 *   1. Cihaz hesaba BAGLIYKEN (bkz. [CloudMode.Cloud]) [SyncLocalSource.localChanges]
 *      dinlenir - yerelde bir yazma olunca (SQLDelight tablo bildirimi) push
 *      tetiklenir.
 *   2. debounce: ard arda yazmalar (bir islem + pozisyon yeniden hesabi +
 *      aktivite hepsi tek saniyede) tek push'a toplanir.
 *   3. localChanges'in ILK emisyonu baglanti push'ini da kapsar: dinlemeye
 *      baslar baslamaz bir kez emit eder, yani yereldeki degisiklikler
 *      watermark'tan itibaren sunucuya gider.
 *   4. GERCEK ZAMANLI (adim 11): bagli VE uygulama ON PLANDA iken
 *      [RealtimeApi.serverChanges] dinlenir - karsi cihazin yazdigi, biz hicbir
 *      seye dokunmadan pull tetikler.
 *
 * YALNIZ BAGLIYKEN. Once kapi "girisli mi" idi: hesaba giren cihaz, hesap
 * inmeden ve "bu telefon kimin" sorulmadan push'a basliyordu. Ilk pull
 * patlarsa bile push gidiyor, yerelde yazilmis profil adlari hesabin ustune
 * yaziliyordu. Artik oturum ile baglanti ([PreferenceKeys.CloudLinkUserId])
 * ayni hesabi gostermedikce ne push, ne pull, ne soket calisir; baglantiyi
 * yalniz hesabi basariyla indiren adim yazar.
 *
 * SUREC OMURLU. start() Compose agacindan cagrilir; Android'de Activity yeniden
 * yaratilinca Koin grafigi (dolayisiyla bu nesne) yeniden kurulur - tipki
 * veritabani gibi. Isler [SyncRuntime] uzerinde TEK sefer baslar, yoksa her
 * donuste yeni bir dinleyici sizar ve ayni degisiklik defalarca push'lanirdi.
 * Bagimliliklar hep kalici veritabanina dayandigi icin ilk kurulumunkiler gecerli
 * kalir. start() ana is parcacigindan geldigi icin bayrak yalin olabilir.
 */
class SyncCoordinator(
    private val authRepository: AuthRepository,
    private val localSource: SyncLocalSource,
    private val pushEngine: PushEngine,
    private val pullEngine: PullEngine,
    private val realtimeApi: RealtimeApi,
    private val preferences: PreferencesRepository,
    private val clock: KefeClock,
    private val runtime: SyncRuntime = Process,
    /** Gunluk fiyat gecmisinin paylasimi; null ise (testler) atlanir. */
    private val priceHistory: PriceHistorySync? = null,
) {

    /** Bulut anahtarlari bu surumde var mi; yoksa hesap satirlari hic cizilmez. */
    val cloudConfigured: Boolean get() = authRepository.isCloudConfigured

    fun start() {
        if (runtime.started) return
        runtime.started = true

        processScope.launch { consumePushes() }
        processScope.launch { consumePulls() }
        // Baglanti geldikce ONCE pull, SONRA push dinleyicisi; gidince birak.
        processScope.launch { followLink() }
        // Gercek zamanli dinleme. Ayri launch: yasam omru ustteki push
        // dinleyicisinden FARKLI - o yalniz baglantiya, bu baglantiya VE on
        // plana bakar.
        processScope.launch { listenServerChanges(socketGates()) { runtime.pullRequests.trySend(Unit) } }
    }

    /**
     * Ekranlarin okudugu TEK durum: cip, ray, yan navigasyon, Ayarlar ve ekleme
     * seridi hepsi buradan. Fiyat tazeliginden BAGIMSIZ.
     *
     * Oturum henuz okunmadiysa emisyon yok (bkz. [deriveCloudMode]).
     */
    fun mode(): Flow<CloudMode> =
        combine(
            authRepository.observeAuthState(),
            preferences.observeAll()
                .map { it[PreferenceKeys.CloudLinkUserId] to it[PreferenceKeys.CloudLinkEmail] }
                .distinctUntilChanged(),
            runtime.status,
        ) { auth, link, status ->
            deriveCloudMode(auth, link.first, link.second, status)
        }.filterNotNull().distinctUntilChanged()

    /** Uygulama on plana girdi/cikti. Compose agacindan surulur (bkz. App.kt). */
    fun setForeground(active: Boolean) {
        runtime.foreground.value = active
    }

    /**
     * "Şimdi eşitle": bagliysa bir push (ardindan pull) ister. Durum hemen
     * "Eşitleniyor"a doner ki dokunus bir sey yapmis gibi gorunsun; sonuc
     * gelince Eşitlendi ya da Eşitlenemiyor olur.
     */
    fun syncNow() {
        val userId = runtime.linkedUser.value ?: return
        runtime.status.value = CloudStatus.Syncing
        runtime.pushRequests.trySend(userId)
    }

    /**
     * Baglantiyi birakir: "Vazgeç", "Hesapsız devam et" ve acik cikis.
     *
     * Kayitlar CIHAZDA KALIR; yalniz hesapla bag ve (varsa) oturum gider. Once
     * baglanti silinir, sonra oturum: ters sira bir an "Oturum kapandı" gosterirdi
     * (baglanti var, oturum yok).
     */
    suspend fun dropLink() {
        preferences.putAll(
            mapOf(
                PreferenceKeys.CloudLinkUserId to null,
                PreferenceKeys.CloudLinkEmail to null,
            ),
        )
        val auth = authRepository.observeAuthState().first { it !is AuthState.Unknown }
        if (auth is AuthState.SignedIn) authRepository.signOut()
    }

    /**
     * "Bu cihazı sıfırla": ONCE baglanti ve oturum birakilir, SONRA [wipe]
     * pull'larla ayni kilitte calisir (bkz. [PullEngine.exclusive]).
     *
     * Iki koruma birlikte: kilit, o an suren bir pull'un silmeden SONRA
     * uygulanmasini engeller (once o biter, sonra silinir); pull da uygulamadan
     * once baglantiyi yeniden okur (bkz. pullLinked), kilidi silmeden sonra alan
     * tur hicbir sey yazmaz. Yalniz sira (cikis, sonra silme) yetmiyordu: cikis
     * yeni turlari durdurur, suren turu durdurmaz.
     */
    suspend fun resetDevice(wipe: suspend () -> Unit) {
        dropLink()
        pullEngine.exclusive { wipe() }
    }

    /**
     * Hesaba henuz gitmemis yerel yazma var mi (bkz. [SyncLocalSource.hasChangesSince]).
     * "Hesaptan çık" onayi bunu okur: cikis esitlemeyi durdurur, gitmemis
     * degisiklik yalniz bu cihazda kalir - kullanici bunu cikmadan once bilmeli.
     */
    suspend fun hasUnsentChanges(): Boolean =
        localSource.hasChangesSince(preferences.get(PreferenceKeys.LastPushedAt)?.toLongOrNull())

    // --- Isciler -----------------------------------------------------------

    /**
     * Tek tuketici: seri push. Hata kullaniciya YANSIMAZ - watermark
     * ilerlemedigi icin veri kaybi yok, degisim bir sonraki tetikte yeniden
     * denenir. Yalniz tanisal bir satir birakiriz (logcat/stdout): sessiz bir
     * senkron, calisan bir senkrondan ayirt edilemez olurdu.
     *
     * Push'tan SONRA pull tetiklenir: benimkini gonderdim, simdi seninkini al.
     */
    internal suspend fun consumePushes() {
        for (userId in runtime.pushRequests) {
            // Istek kuyrukta beklerken baglanti kalkmis ya da hesap degismis
            // olabilir: o hesaba artik hicbir sey gitmemeli.
            if (runtime.linkedUser.value != userId) continue
            runCatching {
                requireToken()
                pushEngine.pushOnce(userId)
            }
                .onSuccess { markReachable() }
                .onFailure {
                    if (it is CancellationException) throw it
                    println("Kefe senkron: push basarisiz - ${it.message}")
                    markUnreachable()
                }
            runtime.pullRequests.trySend(Unit)
        }
    }

    /** Tek tuketici: seri pull. Ayni gerekce - hata yutulur, tanisal log kalir. */
    internal suspend fun consumePulls() {
        for (unit in runtime.pullRequests) {
            if (runtime.linkedUser.value == null) continue
            runCatching { pullLinked() }
                .onSuccess { markReachable() }
                .onFailure {
                    if (it is CancellationException) throw it
                    println("Kefe senkron: pull basarisiz - ${it.message}")
                    markUnreachable()
                }
        }
    }

    /**
     * Baglantiyi izler: bagli hesap her degistiginde eski dinleyici biter,
     * durum "Eşitleniyor"a doner ve (bagliysa) yenisi [pullThenListen] ile
     * baslar. Cocuk isler cagiranin kapsaminda - testte backgroundScope'ta.
     */
    internal suspend fun followLink() = coroutineScope {
        var listener: Job? = null
        var listenerUser: String? = null
        linkedUserChanges().collect { userId ->
            runtime.linkedUser.value = userId
            if (userId == listenerUser) return@collect
            listener?.cancel()
            listener = null
            listenerUser = userId
            runtime.status.value = CloudStatus.Syncing
            if (userId != null) {
                listener = launch {
                    pullThenListen(
                        pull = { pullLinked() },
                        changes = localSource.localChanges(),
                        requestPush = { runtime.pushRequests.trySend(userId) },
                    )
                }
            }
        }
    }

    /**
     * Baglaninca ONCE pull, BITINCE push dinleyicisi.
     *
     * NEYDI. Ikisi ayni anda baslatiliyordu: pull istegi kanala birakiliyor,
     * push dinleyicisi de hemen kuruluyordu. localChanges ilk emisyonunu hemen
     * verdigi icin 1,5 sn sonra watermark'tan push gidiyordu - pull'un bitip
     * bitmedigine bakmadan. Once hesabin halini al, sonra kendi degisikligini
     * gonder.
     *
     * Pull patlarsa push YINE baslar: cevrimdisi yazilan kayitlar sonsuza kadar
     * bekletilmemeli. Bu artik guvenli, cunku buraya yalniz BAGLI cihaz gelir ve
     * baglanti ancak hesabin adlari basariyla indirildikten sonra yazilir. Once
     * ilk baglanista da buradan geciliyordu: ilk pull patlayinca yerelde yazilmis
     * adlar (daha yeni damgayla - sunucunun LWW korumasi onlari "eski" saymaz)
     * hesabin ustune gidiyordu.
     */
    @OptIn(FlowPreview::class)
    internal suspend fun pullThenListen(
        pull: suspend () -> Unit,
        changes: Flow<Unit>,
        requestPush: () -> Unit,
    ) {
        runCatching { pull() }
            .onSuccess { markReachable() }
            .onFailure {
                if (it is CancellationException) throw it
                println("Kefe senkron: ilk pull basarisiz - ${it.message}")
                markUnreachable()
            }
        changes.debounce(DebounceMillis).collect { requestPush() }
    }

    /** Bagli hesabin kimligi ya da null; ayni degeri tekrar yaymaz. */
    internal fun linkedUserChanges(): Flow<String?> =
        combine(
            authRepository.observeAuthState(),
            preferences.observeAll().map { it[PreferenceKeys.CloudLinkUserId] }.distinctUntilChanged(),
        ) { auth, link -> linkedUserId(auth, link) }
            .distinctUntilChanged()

    /**
     * Jetonsuz tur BASARI SAYILMAZ. Motorlar jeton yoksa sessizce 0 donuyor ya
     * da hic istek atmiyordu; o "basari" cipi "Eşitlendi"ye ceviriyordu - oysa
     * sunucuya hic gidilmemisti (yenileme gecici olarak patlamis).
     */
    private suspend fun requireToken() {
        authRepository.validAccessToken() ?: throw IllegalStateException("Oturum doğrulanamadı")
    }

    // Koordinator ASLA adlari devralmaz: devralma baglanti adiminin isi (hesap
    // indirilip "bu telefon kimin" sorulurken). Bagli cihazda pull duz LWW'dir.
    //
    // Uygulamadan once baglanti DISKTEN yeniden okunur: indirme surerken cihaz
    // hesaptan cikmis ya da sifirlanmis olabilir. runtime.linkedUser yetmez -
    // tercih akisindan gecikmeli guncellenir; dropLink ise diske beklenerek yazar.
    private suspend fun pullLinked() {
        requireToken()
        val userId = runtime.linkedUser.value ?: return
        pullEngine.pullOnce(adoptServerMembers = false) {
            preferences.get(PreferenceKeys.CloudLinkUserId) == userId
        }
        // Fiyat gecmisi esitlemenin PARCASI DEGIL: patlarsa yalniz loglanir, cip
        // "Eşitlenemiyor"a dusmez - kayitlar gitti, eksik olan bir gozlem.
        priceHistory?.let { sync ->
            runCatching { sync.syncOnce(userId) }.onFailure {
                if (it is CancellationException) throw it
                println("Kefe senkron: fiyat gecmisi - ${it.message}")
            }
        }
    }

    // Bagli degilken gelen bildirim (baglanti tam o anda kalkti) durumu
    // degistirmez: artik gosterilen mod Cloud degil.
    private suspend fun markReachable() {
        if (runtime.linkedUser.value == null) return
        runtime.status.value = CloudStatus.Synced
        preferences.put(PreferenceKeys.LastSyncedAt, clock.nowEpochMillis().toString())
    }

    private fun markUnreachable() {
        if (runtime.linkedUser.value == null) return
        runtime.status.value = CloudStatus.Unreachable
    }

    /**
     * "Soket ne zaman acik olmali": BAGLI VE on planda iken true.
     *
     * Arka planda soket KAPANIR - acik kalsa Phoenix heartbeat'i kullanicinin
     * hic bakmadigi bir ekran icin pil yakardi. Bedeli, kapaliyken olan
     * degisiklikleri kacirmak; onu da geri donusteki tek pull toparlar.
     * Girisli ama bagli degilken de kapali: o hesabin degisikligini cekmek,
     * baglanti adiminin sorusunu atlamak olurdu.
     */
    internal fun socketGates(): Flow<Boolean> =
        combine(linkedUserChanges(), runtime.foreground) { userId, active ->
            userId != null && active
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
                println("Kefe senkron: realtime dinleme kapali (bagli degil ya da arka plan)")
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

    companion object {
        // Yazma firtinasi dinsin diye kisa bekleme; ekleme sonrasi push'i gozle
        // gorulur geciktirmeyecek kadar da kisa.
        internal const val DebounceMillis = 1500L

        // Realtime sinyalleri icin daha kisa: burada beklenen sey bir kullanici
        // yazmasi degil, sunucudan gelen olay dizisi.
        internal const val RealtimeDebounceMillis = 1000L

        // Surec omurlu: Koin yeniden kurulsa da isler burada tek sefer yasar.
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Uretimin tek durumu. Durum da surec-omurlu: isleri tutan scope burada,
         * durumu kordinator orneginde tutmak Activity donusunde ekrani
         * "Eşitleniyor"a dusururdu.
         */
        val Process: SyncRuntime = SyncRuntime()
    }
}
