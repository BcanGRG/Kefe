package com.kefe.app.data.sync

import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Push'u NE ZAMAN calistiracagina karar veren yer. Tamamen olay-guduml u,
 * ARKA PLAN TICKER'I YOK:
 *
 *   1. Girisli oldugumuz surece [SyncLocalSource.localChanges] dinlenir - yerelde
 *      bir yazma olunca (SQLDelight tablo bildirimi) tetiklenir.
 *   2. debounce: ard arda yazmalar (bir islem + pozisyon yeniden hesabi +
 *      aktivite hepsi tek saniyede) tek push'a toplanir.
 *   3. localChanges'in ILK emisyonu acilis/giris push'ini da kapsar: dinlemeye
 *      baslar baslamaz bir kez emit eder, yani girer girmez yereldeki her sey
 *      (watermark 0'dan) sunucuya gider.
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
) {

    // Conflated: bekleyen istek zaten varken gelen yenisi eskiyi duser - kuyruk
    // sismez, her tetik "en guncel haliyle bir kez daha push'la" demek.
    private val pushRequests = Channel<String>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!claimStart()) return

        // Tek tuketici: seri push. Hata kullaniciya YANSIMAZ - watermark
        // ilerlemedigi icin veri kaybi yok, degisim bir sonraki tetikte yeniden
        // denenir. Yalniz tanisal bir satir birakiriz (logcat/stdout): sessiz bir
        // senkron, calisan bir senkrondan ayirt edilemez olurdu.
        processScope.launch {
            for (userId in pushRequests) {
                runCatching { pushEngine.pushOnce(userId) }
                    .onFailure { println("Kefe senkron: push basarisiz - ${it.message}") }
            }
        }

        // Oturum acikken degisimleri dinle, kapaninca birak.
        processScope.launch {
            var listener: Job? = null
            authRepository.observeAuthState().collect { state ->
                val userId = (state as? AuthState.SignedIn)?.session?.userId?.takeIf { it.isNotBlank() }
                if (userId != null) {
                    if (listener == null) {
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
    }

    private companion object {
        // Yazma firtinasi dinsin diye kisa bekleme; ekleme sonrasi push'i gozle
        // gorulur geciktirmeyecek kadar da kisa.
        const val DebounceMillis = 1500L

        // Surec omurlu: Koin yeniden kurulsa da isler burada tek sefer yasar.
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var started = false

        /** Ilk cagri true, sonrakiler false. Ana is parcacigindan cagrilir. */
        fun claimStart(): Boolean {
            if (started) return false
            started = true
            return true
        }
    }
}
