package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.remote.RealtimeApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Esitlemenin KAPISI: push, pull ve soket yalniz cihaz hesaba BAGLIYKEN calisir.
 *
 * NEYDI. Kapi "girisli mi" idi. Hesaba giren cihaz hesap inmeden ve "bu telefon
 * kimin" sorulmadan push'a basliyordu; ilk pull patlarsa yerelde yazilmis
 * profil adlari (daha yeni damgayla) hesabin ustune gidiyordu. Ilk baglanti da
 * "hic push'lamadi mi" diye tahmin ediliyordu - bir push gecince isaret kalici
 * kayboluyordu. Artik baglanti ([PreferenceKeys.CloudLinkUserId]) acik bir
 * karar; oturum onunla ayni hesabi gostermedikce hicbir sey gitmez ve gelmez.
 *
 * Butun veritabani akislari test zamanlayicisinda calisir (bkz. [GateHarness]):
 * debounce ve "once pull, sonra push" sirasi sanal zamanda gorulur.
 *
 * (Yardimcilar Gate* onekli: ayni paketteki diger testlerle ad cakismasin.)
 */

private class GateAuth : AuthRepository {
    val states = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var token: String? = "tok"
    var signOuts = 0
    override fun observeAuthState(): Flow<AuthState> = states
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = token.takeIf { states.value is AuthState.SignedIn }
    override suspend fun signOut() {
        signOuts++
        states.value = AuthState.SignedOut
    }
}

/** Sunucu: her cagriyi sirasiyla kaydeder ("select:members", "upsert:members"). */
private class GateApi : PostgrestApi {
    val calls = mutableListOf<String>()
    var failWith: String? = null
    val tables = mutableMapOf<String, String>()
    val selects: Int get() = calls.count { it.startsWith("select:") }
    val upserts: Int get() = calls.count { it.startsWith("upsert:") }

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        failWith?.let { error(it) }
        calls += "upsert:$table"
    }

    override suspend fun selectAll(table: String, accessToken: String): String {
        failWith?.let { error(it) }
        calls += "select:$table"
        return tables[table] ?: "[]"
    }
}

private class GateRealtime : RealtimeApi {
    override fun serverChanges(): Flow<Unit> = emptyFlow()
}

private fun gateSession(userId: String = "u1") =
    AuthState.SignedIn(AuthSession(userId, "e@k.app", "tok", "r", 0L))

private class GateHarness(scheduler: TestCoroutineScheduler) {
    val dispatcher = StandardTestDispatcher(scheduler)
    val clock = FixedKefeClock(millis = 50_000L)
    val auth = GateAuth()
    val api = GateApi()
    val database: KefeDatabase
    val prefs: SqlDelightPreferencesRepository
    val repo: SqlDelightPortfolioRepository
    val coordinator: SyncCoordinator

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        prefs = SqlDelightPreferencesRepository(database, dispatcher)
        repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices(), dispatcher)
        val localSource = SyncLocalSource(database, dispatcher)
        coordinator = SyncCoordinator(
            authRepository = auth,
            localSource = localSource,
            pushEngine = PushEngine(auth, localSource, api, prefs, clock),
            pullEngine = PullEngine(auth, api, SyncLocalSink(database, dispatcher)),
            realtimeApi = GateRealtime(),
            preferences = prefs,
            clock = clock,
            // Surec-omurlu durum testte her seferinde taze: sizinti yok.
            runtime = SyncRuntime(),
        )
    }

    /** Uretimdeki start()'in isleri, testin arka plan kapsaminda. */
    fun startIn(scope: CoroutineScope) {
        scope.launch { coordinator.consumePushes() }
        scope.launch { coordinator.consumePulls() }
        scope.launch { coordinator.followLink() }
    }

    suspend fun link(userId: String = "u1") = prefs.putAll(
        mapOf(
            PreferenceKeys.CloudLinkUserId to userId,
            PreferenceKeys.CloudLinkEmail to "e@k.app",
        ),
    )

    fun serverMembers(owner: String, partner: String, stamp: Long) {
        api.tables["members"] = Json.encodeToString(
            listOf(
                MemberDto(LocalOwnerMemberId, "u1", owner, owner.take(1), 0, stamp),
                MemberDto(LocalPartnerMemberId, "u1", partner, partner.take(1), 1, stamp),
            ),
        )
    }
}

/** Debounce (1,5 sn) ve push-sonrasi pull'un rahatca gecmesi icin sanal sure. */
private fun TestScope.settle() {
    advanceTimeBy(10_000)
    runCurrent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudGateTest {

    /** Girisli ama baglanmamis cihaz (Bağlantı yarım): ne push, ne pull, ne soket. */
    @Test
    fun `yarim baglantida hicbir sey gitmez ve gelmez`() = runTest {
        val h = GateHarness(testScheduler)
        h.auth.states.value = gateSession()
        h.coordinator.setForeground(true)
        var socketPulls = 0
        h.startIn(backgroundScope)
        backgroundScope.launch {
            h.coordinator.listenServerChanges(h.coordinator.socketGates()) { socketPulls++ }
        }
        settle()

        assertEquals(CloudMode.LinkPending("e@k.app"), h.coordinator.mode().first())
        // "Şimdi eşitle" de bagli olmayan cihazda bir sey yapmaz.
        h.coordinator.syncNow()
        settle()

        assertEquals(emptyList(), h.api.calls, "sunucuya tek istek gitmemeli")
        assertEquals(0, socketPulls, "soket pull'u istenmemeli")
        assertNull(h.prefs.get(PreferenceKeys.LastPushedAt), "watermark ilerlememeli")
        assertNull(h.prefs.get(PreferenceKeys.LastSyncedAt))
    }

    /** Baska bir hesaba girilmis cihaz (baglanti u2, oturum u1) da yarim sayilir. */
    @Test
    fun `baska hesabin oturumu esitlemez`() = runTest {
        val h = GateHarness(testScheduler)
        h.link("u2")
        h.auth.states.value = gateSession("u1")
        h.startIn(backgroundScope)
        settle()

        assertEquals(CloudMode.LinkPending("e@k.app"), h.coordinator.mode().first())
        assertEquals(emptyList(), h.api.calls)
    }

    /** Bagli cihaz: ONCE hesabin hali alinir, SONRA yereldeki degisiklik gider. */
    @Test
    fun `bagliyken once pull sonra push`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        h.auth.states.value = gateSession()
        h.startIn(backgroundScope)
        settle()

        assertTrue(h.api.upserts > 0, "push gitmeli")
        val firstUpsert = h.api.calls.indexOfFirst { it.startsWith("upsert:") }
        assertTrue(
            h.api.calls.take(firstUpsert).count { it.startsWith("select:") } >= 7,
            "ilk push'tan once tam bir pull (7 tablo) bitmeli: ${h.api.calls}",
        )
        assertNotNull(h.prefs.get(PreferenceKeys.LastPushedAt))
        assertEquals("50000", h.prefs.get(PreferenceKeys.LastSyncedAt))
        assertEquals(CloudMode.Cloud("e@k.app", CloudStatus.Synced), h.coordinator.mode().first())
    }

    /**
     * Giris aninda "Eşitlendi" YAZILMAZ: ilk tur bitene kadar "Eşitleniyor".
     * Once oturum acilir acilmaz Synced yaziliyordu - tek istek gitmeden.
     */
    @Test
    fun `giriste once Esitleniyor sonra Esitlendi`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        val seen = mutableListOf<CloudMode>()
        backgroundScope.launch { h.coordinator.mode().toList(seen) }
        h.startIn(backgroundScope)
        runCurrent()
        assertEquals(listOf<CloudMode>(CloudMode.SessionLost("e@k.app")), seen, "baglanti var, oturum yok")

        h.auth.states.value = gateSession()
        settle()

        val syncing = seen.indexOf(CloudMode.Cloud("e@k.app", CloudStatus.Syncing))
        val synced = seen.indexOf(CloudMode.Cloud("e@k.app", CloudStatus.Synced))
        assertTrue(syncing > 0, "Eşitleniyor gorunmeli: $seen")
        assertTrue(synced > syncing, "Eşitlendi ancak ilk turdan sonra: $seen")
    }

    /** Sunucuya ulasilamazsa "Eşitlenemiyor"; "Şimdi eşitle" duzelince geri getirir. */
    @Test
    fun `ulasilamayan hesap Esitlenemiyor ve simdi esitle toparlar`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        h.auth.states.value = gateSession()
        h.api.failWith = "Unable to resolve host"
        h.startIn(backgroundScope)
        settle()

        assertEquals(CloudMode.Cloud("e@k.app", CloudStatus.Unreachable), h.coordinator.mode().first())
        assertNull(h.prefs.get(PreferenceKeys.LastSyncedAt), "basarisiz tur 'son eşitleme' yazmamali")

        h.api.failWith = null
        h.coordinator.syncNow()
        settle()

        assertEquals(CloudMode.Cloud("e@k.app", CloudStatus.Synced), h.coordinator.mode().first())
        assertEquals("50000", h.prefs.get(PreferenceKeys.LastSyncedAt))
    }

    /**
     * Jetonsuz tur BASARI sayilmaz. Motorlar jeton yoksa sessizce 0 donuyordu;
     * o "basari" cipi "Eşitlendi"ye ceviriyordu - sunucuya hic gidilmeden.
     */
    @Test
    fun `jetonsuz tur Esitlendi saymaz`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        h.auth.token = null
        h.auth.states.value = gateSession()
        h.startIn(backgroundScope)
        settle()

        assertEquals(CloudMode.Cloud("e@k.app", CloudStatus.Unreachable), h.coordinator.mode().first())
        assertEquals(emptyList(), h.api.calls)
    }

    /**
     * Kordinator ASLA adlari devralmaz: bagli cihazda pull duz LWW. NEYDI: "hic
     * push'lamadi" tahminiyle devralma, sonraki acilista da tekrar calisip
     * kullanicinin yeni yaptigi yeniden adlandirmayi geri aliyordu.
     */
    @Test
    fun `bagli pull yerelde yeni adi korur`() = runTest {
        val h = GateHarness(testScheduler)
        h.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        h.serverMembers("Burak Can", "Merve", 5_000L)
        h.link()
        h.auth.states.value = gateSession()
        h.startIn(backgroundScope)
        settle()

        val names = h.repo.observeMembers().first().map { it.name }
        assertEquals("Yerel", names.first(), "yerel (9000) hesabinkinden (5000) yeni")
        assertEquals("Merve", names[1], "adsiz yerel satir hesabinkini alir")
    }

    /**
     * Baglanti birakilinca ("Vazgeç", "Hesapsız devam et", acik cikis) oturum da
     * kapanir, mod "Bu cihazda" olur ve bir daha hicbir sey gitmez.
     */
    @Test
    fun `baglanti birakilinca esitleme durur`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        h.auth.states.value = gateSession()
        h.startIn(backgroundScope)
        settle()
        val before = h.api.calls.size

        h.coordinator.dropLink()
        settle()
        assertEquals(1, h.auth.signOuts)
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkEmail))
        assertEquals(CloudMode.Local, h.coordinator.mode().first())

        h.coordinator.syncNow()
        settle()
        assertEquals(before, h.api.calls.size, "baglanti yokken istek gitmemeli")
    }

    /** Oturumsuz cihazda "Hesapsız devam et": cikis cagrilmaz, yalniz baglanti gider. */
    @Test
    fun `oturumsuz baglanti birakma cikis cagirmaz`() = runTest {
        val h = GateHarness(testScheduler)
        h.link()
        assertEquals(CloudMode.SessionLost("e@k.app"), h.coordinator.mode().first())

        h.coordinator.dropLink()

        assertEquals(0, h.auth.signOuts)
        assertEquals(CloudMode.Local, h.coordinator.mode().first())
    }
}
