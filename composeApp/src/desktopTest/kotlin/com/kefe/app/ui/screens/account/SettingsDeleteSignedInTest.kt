package com.kefe.app.ui.screens.account

import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.backup.FileTransfer
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.remote.RealtimeApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.data.sync.CloudMode
import com.kefe.app.data.sync.MemberDto
import com.kefe.app.data.sync.PositionDto
import com.kefe.app.data.sync.PullEngine
import com.kefe.app.data.sync.PushEngine
import com.kefe.app.data.sync.SyncCoordinator
import com.kefe.app.data.sync.SyncLocalSink
import com.kefe.app.data.sync.SyncLocalSource
import com.kefe.app.data.sync.SyncRuntime
import com.kefe.app.data.sync.TransactionDto
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.security.BiometricGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ayarlar'da hesaptan cikis, "Bu cihazı sıfırla" ve geri yukleme kapisi -
 * GERCEK kordinatorle.
 *
 * NEYDI. "Tüm verileri sil" oturumu ve baglantiyi birakiyordu: silmenin
 * tetikledigi push -> pull ~1,5 sn icinde hesabin butun kayitlarini silinmis
 * veritabanina geri indiriyordu. "Çıkış yap" onaysiz cikiyordu. Geri yukleme
 * bagli cihazda yedegin eski halini hesabin ustune yaziyordu.
 *
 * Butun akislar test zamanlayicisinda (sanal saat) calisir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDeleteSignedInTest {

    @Test
    fun `sifirlama once hesaptan cikar, sonra siler ve veri geri gelmez`() = resetTest { h ->
        h.link()
        h.auth.states.value = resetSession()
        h.serverHasOneTransaction()
        h.startSync(backgroundScope)
        settle()
        assertEquals(1, h.liveTransactions(), "bagli cihaz hesabi indirmis olmali")

        val vm = h.settings()
        settle()
        assertEquals("Bu cihazı sıfırla", deleteRowLabel(vm.state.value.cloudMode))

        vm.onIntent(SettingsIntent.DeleteAllData)
        vm.onIntent(SettingsIntent.ConfirmDeleteAllData)
        settle()

        assertEquals(1, h.auth.signOuts)
        assertEquals(1, h.auth.transactionsAtSignOut, "cikis SILMEDEN ONCE olmali")
        assertEquals(0, h.liveTransactions())
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(CloudMode.Local, h.coordinator.mode().first())
        assertEquals(listOf("Ben", "Eşim"), h.names(), "profiller adsiz haline doner")
        assertEquals(SettingsEffect.AllDataDeleted, vm.effects.first())

        // Bir pull turu daha: silinen cihaza hesap GERI INMEZ.
        val selects = h.api.selects
        h.runtime.pullRequests.trySend(Unit)
        h.coordinator.syncNow()
        settle()
        assertEquals(0, h.liveTransactions())
        assertEquals(selects, h.api.selects, "bagli olmayan cihaz sunucuya gitmez")
    }

    /**
     * Sifirlama onaylandiginda bir pull ZATEN suruyor (karsi telefonun realtime
     * sinyali, push-sonrasi pull, on plana donus): jetonu almis, indirmenin
     * ortasinda. NEYDI: silme kilitsiz calisiyordu; indirme silmeden SONRA
     * bitiyor ve hesabin satirlari bos veritabanina geri yaziliyordu - cihaz
     * "Yalnız bu cihazda" ama hesabin tamamini tasiyordu.
     */
    @Test
    fun `sifirlama suren pull'u bekler ve hesap geri yazilmaz`() = resetTest { h ->
        h.link()
        h.auth.states.value = resetSession()
        h.serverHasOneTransaction()
        h.startSync(backgroundScope)
        settle()
        assertEquals(1, h.liveTransactions())
        val vm = h.settings()
        settle()

        // Bir pull indirmenin ortasinda takili kalsin.
        val gate = CompletableDeferred<Unit>()
        h.api.gate = gate
        h.runtime.pullRequests.trySend(Unit)
        settle()
        assertTrue(h.api.waiting > 0, "pull indirmede beklemeli")

        vm.onIntent(SettingsIntent.ConfirmDeleteAllData)
        settle()
        assertEquals(1, h.auth.signOuts, "once hesaptan cikilir")
        assertEquals(1, h.liveTransactions(), "silme suren pull'u bekler")

        // Indirme simdi biter: baglanti artik yok, hicbir sey yazilmaz.
        gate.complete(Unit)
        settle()
        assertEquals(0, h.liveTransactions(), "hesap silinen cihaza geri yazilmamali")
        assertEquals(listOf("Ben", "Eşim"), h.names())
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(SettingsEffect.AllDataDeleted, vm.effects.first())
    }

    @Test
    fun `oturumu dusmus cihazin sifirlanmasi baglantiyi da siler`() = resetTest { h ->
        h.link()
        h.startSync(backgroundScope)
        val vm = h.settings()
        settle()
        assertEquals(CloudMode.SessionLost("e@k.app"), vm.state.value.cloudMode)

        vm.onIntent(SettingsIntent.ConfirmDeleteAllData)
        settle()
        assertEquals(0, h.auth.signOuts, "oturum yok - cikis cagrilmaz")
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(CloudMode.Local, h.coordinator.mode().first())
    }

    @Test
    fun `hesapsiz cihazda silme cikis cagirmaz`() = resetTest { h ->
        h.localTransaction()
        h.startSync(backgroundScope)
        val vm = h.settings()
        settle()
        assertEquals(CloudMode.Local, vm.state.value.cloudMode)
        assertEquals("Tüm verileri sil", deleteRowLabel(vm.state.value.cloudMode))

        vm.onIntent(SettingsIntent.ConfirmDeleteAllData)
        settle()
        assertEquals(0, h.auth.signOuts)
        assertEquals(0, h.liveTransactions())
        assertEquals(emptyList(), h.api.calls)
    }

    /**
     * "Hesaptan çık" ONAY ister ve gitmemis degisikligi soyler; onaylaninca
     * yalniz baglanti ve oturum gider - kayitlar, profil secimi ve watermark
     * kalir.
     */
    @Test
    fun `hesaptan cikis onay ister ve kayitlari birakir`() = resetTest { h ->
        h.link()
        h.auth.states.value = resetSession()
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.startSync(backgroundScope)
        settle()
        val watermark = assertNotNull(h.prefs.get(PreferenceKeys.LastPushedAt))
        // Push'tan sonra yerelde yeni bir kayit: hesaba henuz gitmedi.
        h.localTransaction(stamp = watermark.toLong() + 1)
        h.repo.renameMember(LocalPartnerMemberId, "Merve", "M")

        val vm = h.settings()
        settle()
        vm.onIntent(SettingsIntent.SignOut)
        runCurrent()
        assertTrue(vm.state.value.confirmSignOut)
        assertEquals(0, h.auth.signOuts, "onaysiz cikilmaz")
        val dialog = signOutDialog(vm.state.value.partnerName, vm.state.value.unsentChanges)
        assertTrue("Merve'nin telefonu" in dialog.message, dialog.message)
        assertTrue("henüz gönderilmemiş" in dialog.message, dialog.message)

        vm.onIntent(SettingsIntent.ConfirmSignOut)
        settle()
        assertFalse(vm.state.value.confirmSignOut)
        assertEquals(1, h.auth.signOuts)
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(1, h.liveTransactions(), "kayitlar cihazda kalir")
        assertEquals(LocalOwnerMemberId, h.prefs.get(PreferenceKeys.ActiveMemberId))
        assertNotNull(h.prefs.get(PreferenceKeys.LastPushedAt))
        assertEquals(SettingsEffect.SignedOut, vm.effects.first())
    }

    @Test
    fun `bagliyken geri yukleme acilmaz, nedenini soyler`() = resetTest { h ->
        h.link()
        h.auth.states.value = resetSession()
        h.startSync(backgroundScope)
        val vm = h.settings()
        settle()

        vm.onIntent(SettingsIntent.Restore)
        runCurrent()
        assertFalse(vm.state.value.confirmRestore)
        assertEquals(SettingsEffect.Notice(restoreLockedMessage(vm.state.value.cloudMode)), vm.effects.first())
    }
}

// --- Yardimcilar ---------------------------------------------------------------

private fun resetSession() = AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L))

private class ResetAuth(private val countTransactions: () -> Int) : AuthRepository {
    val states = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var signOuts = 0
    var transactionsAtSignOut = -1
    override fun observeAuthState(): Flow<AuthState> = states
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = "tok".takeIf { states.value is AuthState.SignedIn }
    override suspend fun signOut() {
        signOuts++
        transactionsAtSignOut = countTransactions()
        states.value = AuthState.SignedOut
    }
}

private class ResetApi : PostgrestApi {
    val calls = mutableListOf<String>()
    val tables = mutableMapOf<String, String>()
    val selects: Int get() = calls.count { it.startsWith("select:") }

    /** Doluysa her okuma bu tamamlanana kadar bekler (suren bir indirme). */
    var gate: CompletableDeferred<Unit>? = null

    /** Su an kapida bekleyen okuma sayisi. */
    var waiting = 0

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        calls += "upsert:$table"
    }
    override suspend fun selectAll(table: String, accessToken: String): String {
        calls += "select:$table"
        gate?.let {
            waiting++
            it.await()
            waiting--
        }
        return tables[table] ?: "[]"
    }
}

private class ResetRealtime : RealtimeApi {
    override fun serverChanges(): Flow<Unit> = emptyFlow()
}

private class ResetHarness(scope: TestScope) {
    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val clock = FixedKefeClock(millis = 50_000L)
    val database: KefeDatabase
    val auth: ResetAuth
    val api = ResetApi()
    val prefs: SqlDelightPreferencesRepository
    val repo: SqlDelightPortfolioRepository
    val runtime = SyncRuntime()
    val coordinator: SyncCoordinator

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        auth = ResetAuth { liveTransactions() }
        prefs = SqlDelightPreferencesRepository(database, dispatcher)
        repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices(), dispatcher)
        val localSource = SyncLocalSource(database, dispatcher)
        coordinator = SyncCoordinator(
            authRepository = auth,
            localSource = localSource,
            pushEngine = PushEngine(auth, localSource, api, prefs, clock),
            pullEngine = PullEngine(auth, api, SyncLocalSink(database, dispatcher)),
            realtimeApi = ResetRealtime(),
            preferences = prefs,
            clock = clock,
            runtime = runtime,
        )
    }

    /** Kurulan VM'ler: testin sonunda kapsamlari kapatilir (dakikalik saat durur). */
    val viewModels = mutableListOf<SettingsViewModel>()

    fun settings() = SettingsViewModel(
        portfolioRepository = repo,
        preferences = prefs,
        files = FileTransfer(),
        clock = clock,
        authRepository = auth,
        biometric = BiometricGate(),
        syncCoordinator = coordinator,
    ).also { viewModels += it }

    fun startSync(scope: CoroutineScope) {
        scope.launch { coordinator.consumePushes() }
        scope.launch { coordinator.consumePulls() }
        scope.launch { coordinator.followLink() }
    }

    suspend fun link() = prefs.putAll(
        mapOf(
            PreferenceKeys.CloudLinkUserId to "u1",
            PreferenceKeys.CloudLinkEmail to "e@k.app",
        ),
    )

    fun serverHasOneTransaction() {
        val json = Json { explicitNulls = true; encodeDefaults = true }
        api.tables["members"] = json.encodeToString(
            listOf(
                MemberDto(LocalOwnerMemberId, "u1", "Burak Can", "B", 0, 5_000L),
                MemberDto(LocalPartnerMemberId, "u1", "Merve", "M", 1, 5_000L),
            ),
        )
        api.tables["positions"] = json.encodeToString(listOf(resetPosition()))
        api.tables["transactions"] = json.encodeToString(listOf(resetTx("tx-server", 5_000L)))
    }

    fun localTransaction(stamp: Long = 9_000L) {
        database.transaction {
            database.positionQueries.insertOrIgnorePosition(
                id = "pos_gold_quarter",
                name = "Çeyrek",
                assetClass = com.kefe.app.domain.model.AssetClass.Gold,
                subtype = com.kefe.app.domain.model.GoldSubtype.Quarter,
                karat = null,
                unit = com.kefe.app.domain.model.QuantityUnit.Piece,
                unitPrice = 10_000.0,
                manualPrice = false,
                dailyChangePercent = 0.0,
                updatedAt = stamp,
            )
            database.transactionQueries.insertTransaction(
                id = "tx-local-$stamp",
                positionId = "pos_gold_quarter",
                dateYear = 2026, dateMonth = 9, dateDay = 20,
                side = com.kefe.app.domain.model.TradeSide.Buy,
                quantity = 1.0, unitPrice = 10_000.0, fee = 0.0, note = null, storage = null,
                addedByMemberId = LocalOwnerMemberId,
                syncState = com.kefe.app.domain.model.SyncState.Synced,
                updatedAt = stamp, createdAt = 1L, goalId = null, goalDelta = 0.0,
            )
        }
    }

    fun liveTransactions(): Int =
        database.transactionQueries.countTransactions().executeAsOne().toInt()

    suspend fun names(): List<String> = repo.observeMembers().first().map { it.name }
}

private fun resetPosition() = PositionDto(
    id = "pos_gold_quarter", userId = "u1", name = "Çeyrek", assetClass = "Gold", subtype = "Quarter",
    karat = null, unit = "Piece", unitPrice = 10_000.0, manualPrice = false, updatedAt = 5_000L, deletedAt = null,
)

private fun resetTx(id: String, stamp: Long) = TransactionDto(
    id = id, userId = "u1", positionId = "pos_gold_quarter", dateYear = 2026, dateMonth = 9, dateDay = 20,
    side = "Buy", quantity = 1.0, unitPrice = 10_000.0, fee = 0.0, note = null, storage = null,
    addedByMemberId = LocalOwnerMemberId, updatedAt = stamp, deletedAt = null,
)

/**
 * Test govdesi: Main de test zamanlayicisinda (SettingsViewModel viewModelScope'u
 * orada calisir); bitince geri alinir. VM'lerin kapsami kapatilir: "Son
 * eşitleme"nin dakikalik saati sonsuz bir dongu - acik kalsa runTest'in son
 * bosaltmasi bitmezdi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun resetTest(body: suspend TestScope.(ResetHarness) -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val harness = ResetHarness(this)
    try {
        body(harness)
    } finally {
        harness.viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
}

/** Debounce (1,5 sn) ve push-sonrasi pull'un rahatca gecmesi icin sanal sure. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.settle() {
    advanceTimeBy(10_000)
    runCurrent()
}
