package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.remote.RealtimeApi
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Adim 11'in kalbi: soket NE ZAMAN dinlenir ve sinyal gelince ne olur.
 *
 * Bunlarin hicbiri ekranda tek basina gorunmez - "arka plana gecince soket
 * kapandi" ancak logcat'e bakan biri icin vardir, ve yanlis oldugunda belirtisi
 * sessiz pil tuketimi olur. [SyncCoordinator.start] surec-omurlu bayrakla
 * korundugu icin testten cagrilamaz; dinleme mantigi bu yuzden ayri bir
 * fonksiyonda duruyor ve sahte kapilarla dogrudan surulur.
 */

private class TriggerClock : KefeClock {
    override fun today(): KefeDate = KefeDate(2026, 7, 31)
    override fun nowEpochMillis(): Long = 1_000L
}

private class TriggerAuth(val states: MutableStateFlow<AuthState>) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = states
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = "tok"
    override suspend fun signOut() = Unit
}

private class TriggerApi : PostgrestApi {
    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) = Unit
    override suspend fun selectAll(table: String, accessToken: String): String = "[]"
}

/** Sinyalleri disaridan surulen sahte soket; kac dinleyicisi oldugunu sayar. */
private class TriggerRealtime(private val signals: Flow<Unit>) : RealtimeApi {
    var listeners = 0
        private set

    override fun serverChanges(): Flow<Unit> = signals
        .onStart { listeners++ }
        .onCompletion { listeners-- }
}

private fun signedIn(userId: String = "u1") =
    AuthState.SignedIn(AuthSession(userId, "e@k.app", "tok", "r", 0L))

private class TriggerHarness(signals: Flow<Unit>) {
    val auth = TriggerAuth(MutableStateFlow<AuthState>(AuthState.SignedOut))
    val realtime = TriggerRealtime(signals)
    val coordinator: SyncCoordinator

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val database: KefeDatabase = createKefeDatabase(driver)
        database.bootstrapIfNeeded()

        val localSource = SyncLocalSource(database)
        val api = TriggerApi()
        val preferences = SqlDelightPreferencesRepository(database)
        coordinator = SyncCoordinator(
            authRepository = auth,
            localSource = localSource,
            pushEngine = PushEngine(auth, localSource, api, preferences, TriggerClock()),
            pullEngine = PullEngine(auth, api, SyncLocalSink(database)),
            realtimeApi = realtime,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeTriggerTest {

    // debounce(1000) sonrasi sinyalin gecmesi icin yeterli sanal zaman.
    private val afterDebounce = 1_500L

    @Test
    fun `kapi acilinca sinyal beklemeden bir pull istenir`() = runTest {
        // Soket kapaliyken sunucuda olan degisiklikleri toparlayan pull budur.
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val h = TriggerHarness(signals)
        val gates = MutableStateFlow(false)
        var pulls = 0

        backgroundScope.launch { h.coordinator.listenServerChanges(gates) { pulls++ } }
        runCurrent()
        assertEquals(0, pulls, "kapi kapaliyken pull istenmemeli")

        gates.value = true
        runCurrent()

        assertEquals(1, pulls)
    }

    @Test
    fun `sinyal gelince pull istenir`() = runTest {
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val h = TriggerHarness(signals)
        val gates = MutableStateFlow(true)
        var pulls = 0

        backgroundScope.launch { h.coordinator.listenServerChanges(gates) { pulls++ } }
        runCurrent()
        assertEquals(1, pulls, "acilis pull'u")

        signals.emit(Unit)
        advanceTimeBy(afterDebounce)
        runCurrent()

        assertEquals(2, pulls)
    }

    @Test
    fun `ard arda sinyaller tek pull'a toplanir`() = runTest {
        // Karsi cihazda tek islem 3-4 tabloya dokunur: dort olay, tek pull.
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val h = TriggerHarness(signals)
        val gates = MutableStateFlow(true)
        var pulls = 0

        backgroundScope.launch { h.coordinator.listenServerChanges(gates) { pulls++ } }
        runCurrent()

        repeat(4) { signals.emit(Unit) }
        advanceTimeBy(afterDebounce)
        runCurrent()

        assertEquals(2, pulls, "acilis pull'u + toplanmis tek pull")
    }

    @Test
    fun `kapi kapaninca dinleme durur ve sinyal pull uretmez`() = runTest {
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val h = TriggerHarness(signals)
        val gates = MutableStateFlow(true)
        var pulls = 0

        backgroundScope.launch { h.coordinator.listenServerChanges(gates) { pulls++ } }
        runCurrent()
        assertEquals(1, h.realtime.listeners, "kapi acikken soket dinleniyor olmali")

        gates.value = false
        runCurrent()
        assertEquals(0, h.realtime.listeners, "arka planda soket birakilmali")

        signals.emit(Unit)
        advanceTimeBy(afterDebounce)
        runCurrent()

        assertEquals(1, pulls, "kapali kapida sinyal pull uretmemeli")
    }

    @Test
    fun `kapi yeniden acilinca dinleme ve toparlama pull'u geri gelir`() = runTest {
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val h = TriggerHarness(signals)
        val gates = MutableStateFlow(true)
        var pulls = 0

        backgroundScope.launch { h.coordinator.listenServerChanges(gates) { pulls++ } }
        runCurrent()
        gates.value = false
        runCurrent()
        gates.value = true
        runCurrent()

        assertEquals(2, pulls, "her acilista bir toparlama pull'u")
        assertEquals(1, h.realtime.listeners)
    }

    @Test
    fun `soket yalniz girisli VE on planda acilir`() = runTest {
        val h = TriggerHarness(MutableSharedFlow())
        // foreground bayragi surec-omurlu (companion): testin basinda ve sonunda
        // acikca sifirlanir ki baska bir teste sizmasin.
        h.coordinator.setForeground(false)

        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { h.coordinator.socketGates().toList(seen) }
        runCurrent()
        assertEquals(listOf(false), seen, "cikisli + arka plan")

        h.auth.states.value = signedIn()
        runCurrent()
        assertEquals(listOf(false), seen, "girisli ama arka planda: soket acilmamali")

        h.coordinator.setForeground(true)
        runCurrent()
        assertEquals(listOf(false, true), seen, "girisli + on plan")

        h.auth.states.value = AuthState.SignedOut
        runCurrent()
        assertEquals(listOf(false, true, false), seen, "cikista soket kapanmali")

        h.coordinator.setForeground(false)
    }
}
