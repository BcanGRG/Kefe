package com.kefe.app.ui.screens.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.data.sync.MemberDto
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.SyncLocalSink
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Profiller / Bu telefon kimin?" akisi.
 *
 * NEYDI. Hesabi olan kullanici yeni telefonda "İki profil oluşturun" goruyordu:
 * ekran pull'u beklemiyor, bos alanlara yazilan adlar da hesaptaki gercek
 * adlarin ustune (daha yeni damgayla) yaziliyordu. Burada sabitlenen:
 *   - girisliyse once hesap indirilir, profil varsa YALNIZ secim yapilir;
 *   - secim modunda kaydetmek adlara DOKUNMAZ (damga degismez);
 *   - hesaba ulasilamazsa ekran bunu soyler, sessizce "olustur"a dusmez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupFlowTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FlowAuth(signedIn: Boolean) : AuthRepository {
        val state = MutableStateFlow<AuthState>(
            if (signedIn) AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L)) else AuthState.SignedOut,
        )
        override fun observeAuthState(): Flow<AuthState> = state
        override val isCloudConfigured: Boolean = true
        override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
        override suspend fun validAccessToken(): String? = (state.value as? AuthState.SignedIn)?.session?.accessToken
        override suspend fun signOut() = Unit
    }

    private class FlowApi : PostgrestApi {
        var calls = 0
        var failWith: String? = null
        val tables = mutableMapOf<String, String>()
        override suspend fun upsert(table: String, rowsJson: String, accessToken: String) = Unit
        override suspend fun selectAll(table: String, accessToken: String): String {
            calls++
            failWith?.let { error(it) }
            return tables[table] ?: "[]"
        }
    }

    private class Env(signedIn: Boolean) {
        val database: KefeDatabase
        val auth = FlowAuth(signedIn)
        val api = FlowApi()
        val repo: SqlDelightPortfolioRepository
        val prefs: SqlDelightPreferencesRepository
        val vm: ProfileSetupViewModel

        init {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            KefeDatabase.Schema.create(driver)
            database = createKefeDatabase(driver)
            database.bootstrapIfNeeded()
            repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices())
            prefs = SqlDelightPreferencesRepository(database)
            vm = ProfileSetupViewModel(repo, prefs, auth, PullEngine(auth, api, SyncLocalSink(database)))
        }

        fun cloudMembers(owner: String = "Burak Can", partner: String = "Merve") {
            api.tables["members"] = Json.encodeToString(
                listOf(
                    MemberDto(LocalOwnerMemberId, "u1", owner, owner.take(1), 0, 5_000L),
                    MemberDto(LocalPartnerMemberId, "u1", partner, partner.take(1), 1, 5_000L),
                ),
            )
        }

        suspend fun awaitPhase(phase: ProfileSetupPhase): ProfileSetupUiState =
            realTime { vm.state.first { it.phase == phase } }

        suspend fun awaitDone() = realTime { vm.state.first { it.done } }

        suspend fun stamps(): List<Long> = repo.observeMembers().first().map { it.updatedAt }
        suspend fun names(): List<String> = repo.observeMembers().first().map { it.name }
    }

    @Test
    fun `hesapta profil varsa yalniz secim ve adlara dokunulmaz`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertFalse(s.editingNames)
        assertTrue(s.accountHasProfiles)
        assertEquals("Burak Can", s.ownerName)
        assertEquals("Merve", s.partnerName)
        // Bilerek secili gelmez: iki telefon ayni profili secmesin.
        assertNull(s.thisDeviceIsOwner)
        assertFalse(s.canSave)

        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(LocalPartnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
        // Damga hesabinki kaldi: push bu satirlari sunucuya yeniden itemez.
        assertEquals(listOf(5_000L, 5_000L), e.stamps())
    }

    @Test
    fun `girissiz ilk telefon profil olusturur ve sunucuya gitmez`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)

        assertTrue(s.editingNames)
        assertFalse(s.signedIn)
        assertEquals("", s.ownerName)
        assertEquals(0, e.api.calls)

        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Volkan"))
        e.vm.onIntent(ProfileSetupIntent.ChangePartnerName("Ayşe"))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf("Volkan", "Ayşe"), e.names())
        assertEquals(LocalOwnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
    }

    @Test
    fun `hesaba ulasilamazsa soylenir ve tekrar denenebilir`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.api.failWith = "Unable to resolve host"
        e.vm.onIntent(ProfileSetupIntent.Load)
        val failed = e.awaitPhase(ProfileSetupPhase.Failed)
        assertEquals("Unable to resolve host", failed.failureDetail)

        e.api.failWith = null
        e.vm.onIntent(ProfileSetupIntent.Retry)
        val ready = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(ready.editingNames)
        assertEquals("Burak Can", ready.ownerName)
    }

    /** Baglanmadan devam: yalniz secim, adlar yazilmaz - hesap gelince onunkiler gecer. */
    @Test
    fun `baglanmadan devam adlari yazmaz`() = runTest {
        val e = Env(signedIn = true)
        e.api.failWith = "offline"
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Failed)

        e.vm.onIntent(ProfileSetupIntent.ContinueOffline)
        val s = e.awaitPhase(ProfileSetupPhase.Ready)
        assertFalse(s.editingNames)
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf(0L, 0L), e.stamps(), "kurulum adlari damgasiz kalmali")
        assertEquals(LocalOwnerMemberId, e.prefs.get(PreferenceKeys.ActiveMemberId))
    }

    /** Adlari duzenlemede yalniz DEGISEN ad yazilir. */
    @Test
    fun `yalniz degisen ad yeniden yazilir`() = runTest {
        val e = Env(signedIn = true)
        e.cloudMembers()
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)

        e.vm.onIntent(ProfileSetupIntent.EditNames)
        e.vm.onIntent(ProfileSetupIntent.ChangeOwnerName("Burak"))
        e.vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        e.vm.onIntent(ProfileSetupIntent.Save)
        e.awaitDone()

        assertEquals(listOf("Burak", "Merve"), e.names())
        assertEquals(listOf(9_000L, 5_000L), e.stamps())
    }

    /** VM surec boyunca yasiyor: cikista sifirlanir, sonraki gorunus eski "done"u gormez. */
    @Test
    fun `reset eski durumu siler`() = runTest {
        val e = Env(signedIn = false)
        e.vm.onIntent(ProfileSetupIntent.Load)
        e.awaitPhase(ProfileSetupPhase.Ready)
        e.vm.onIntent(ProfileSetupIntent.Reset)
        assertEquals(ProfileSetupUiState(), e.vm.state.value)
    }
}

/**
 * Depo ve pull GERCEK is parcaciklarinda (Dispatchers.Default) calisiyor;
 * runTest'in sanal saati onlari beklemez, zaman asimi hemen dolardi.
 */
private suspend fun <T> realTime(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }