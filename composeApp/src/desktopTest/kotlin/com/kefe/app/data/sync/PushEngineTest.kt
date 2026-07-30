package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.remote.SyncException
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.AssetClass
import com.kefe.app.domain.model.GoldSubtype
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.Position
import com.kefe.app.domain.model.QuantityUnit
import com.kefe.app.domain.model.TradeSide
import com.kefe.app.domain.model.Transaction
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Push kurallari GERCEK veritabani + sahte sunucu ile dogrulanir.
 *
 * Bu kurallar ne ekranda ne de sunucuda tek basina gorunur: neyin "degismis"
 * sayildigi (watermark), mezar tasinin gidip gitmedigi, hata olunca ilerlemenin
 * durup durmadigi. Emulatorde tiklayarak sinanamaz.
 */

private class MutableClock(var millis: Long) : KefeClock {
    override fun today(): KefeDate = KefeDate(2026, 7, 30)
    override fun nowEpochMillis(): Long = millis
}

private class FakeAuth(private val token: String?) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(
        if (token != null) AuthState.SignedIn(AuthSession("u1", "e@k.app", token, "r", 0L))
        else AuthState.SignedOut,
    )
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = token
    override suspend fun signOut() = Unit
}

/** Gonderilen her upsert'i kaydeder; istenirse bir tabloda patlar. */
private class RecordingPostgrest(var failTable: String? = null) : PostgrestApi {
    val calls = mutableListOf<Pair<String, String>>()
    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        if (table == failTable) throw SyncException("boom $table")
        calls += table to rowsJson
    }
    fun tables(): List<String> = calls.map { it.first }
    fun jsonFor(table: String): String = calls.first { it.first == table }.second
    fun clear() = calls.clear()
}

private class FakePreferences : PreferencesRepository {
    val map = mutableMapOf<String, String>()
    override fun observeAll(): Flow<Map<String, String>> = flowOf(map.toMap())
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun get(key: String): String? = map[key]
}

private class Harness(val token: String? = "tok") {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val database: KefeDatabase
    val clock = MutableClock(millis = 1_000L)
    val postgrest = RecordingPostgrest()
    val prefs = FakePreferences()
    val repo: SqlDelightPortfolioRepository
    val engine: PushEngine

    init {
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        repo = SqlDelightPortfolioRepository(database, clock, NoPrices())
        engine = PushEngine(FakeAuth(token), SyncLocalSource(database), postgrest, prefs, clock)
    }

    /**
     * Push, yazmalardan SONRA olur (uretimde debounce bekler); bu yuzden testte de
     * once saati ilerletiriz. syncStart yazmalarin damgasindan buyuk olsun ki
     * gonderilen satirlar bir sonraki turda "yine degismis" sayilmasin.
     */
    suspend fun pushAt(millis: Long) {
        clock.millis = millis
        engine.pushOnce("u1")
    }
    fun watermark(): Long? = prefs.map[PreferenceKeys.LastPushedAt]?.toLong()
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun quarter() = Position(
    id = "pos_q", name = "Çeyrek", assetClass = AssetClass.Gold, subtype = GoldSubtype.Quarter,
    quantity = 0.0, unit = QuantityUnit.Piece, unitPrice = 10_000.0, value = 0.0, cost = 0.0,
)

private fun buy(id: String, qty: Double) = Transaction(
    id = id, positionId = "pos_q", date = KefeDate(2026, 7, 30), side = TradeSide.Buy,
    quantity = qty, unitPrice = 10_000.0, addedByMemberId = "member_owner",
)

class PushEngineTest {

    @Test
    fun `ilk push tum senkron tablolarini gonderir`() = runTest {
        val h = Harness()
        h.repo.upsertPosition(quarter())          // yazmalar 1000'de
        h.repo.addTransaction(buy("tx-1", 2.0))

        h.pushAt(2_000L)

        // Bootstrap uyeleri + varlik + islem + ondan turetilen aktivite. Watermark
        // 0'dan basladigi icin ilk push YERELDEKI HER SEYI gonderir.
        val tables = h.postgrest.tables()
        assertTrue("members" in tables, "uyeler gitmeli")
        assertTrue("positions" in tables)
        assertTrue("transactions" in tables)
        assertTrue("activity_events" in tables)

        // Islem dogru bicimde: snake_case, tombstone bos, user_id dolu.
        val txs = json.decodeFromString<List<TransactionDto>>(h.postgrest.jsonFor("transactions"))
        assertEquals(1, txs.size)
        assertEquals("tx-1", txs.single().id)
        assertEquals("u1", txs.single().userId)
        assertNull(txs.single().deletedAt)

        // Watermark push aninin damgasina ilerledi.
        assertEquals(2_000L, h.watermark())
    }

    @Test
    fun `degisiklik yoksa ikinci push bir sey gondermez`() = runTest {
        val h = Harness()
        h.repo.upsertPosition(quarter())
        h.repo.addTransaction(buy("tx-1", 2.0))
        h.pushAt(2_000L)
        h.postgrest.clear()

        // Saat ilerledi ama veri degismedi.
        h.pushAt(5_000L)

        assertTrue(h.postgrest.calls.isEmpty(), "degismeyen veri yeniden gitmemeli")
        assertEquals(5_000L, h.watermark())
    }

    @Test
    fun `watermark sonrasi degisen satir yeniden gonderilir`() = runTest {
        val h = Harness()
        h.repo.upsertPosition(quarter())
        h.repo.addTransaction(buy("tx-1", 2.0))
        h.pushAt(2_000L)
        h.postgrest.clear()

        // Yeni islem, watermark'tan (2000) SONRAKI damgayla.
        h.clock.millis = 3_000L
        h.repo.addTransaction(buy("tx-2", 1.0))
        h.pushAt(4_000L)

        val tables = h.postgrest.tables()
        assertTrue("transactions" in tables)
        assertTrue("positions" !in tables, "degismeyen varlik gitmemeli")
        val txs = json.decodeFromString<List<TransactionDto>>(h.postgrest.jsonFor("transactions"))
        assertEquals(listOf("tx-2"), txs.map { it.id })
    }

    @Test
    fun `silinen islem mezar tasiyla gider`() = runTest {
        val h = Harness()
        h.repo.upsertPosition(quarter())
        h.repo.addTransaction(buy("tx-1", 2.0))
        h.pushAt(2_000L)
        h.postgrest.clear()

        h.clock.millis = 3_000L
        h.repo.deleteTransaction("tx-1")          // deletedAt = 3000
        h.pushAt(4_000L)

        val txs = json.decodeFromString<List<TransactionDto>>(h.postgrest.jsonFor("transactions"))
        assertEquals("tx-1", txs.single().id)
        assertEquals(3_000L, txs.single().deletedAt, "mezar tasi damgasi gitmeli")
    }

    @Test
    fun `jeton yoksa hicbir sey gitmez`() = runTest {
        val h = Harness(token = null)
        h.repo.upsertPosition(quarter())
        h.repo.addTransaction(buy("tx-1", 2.0))

        h.pushAt(2_000L)

        assertTrue(h.postgrest.calls.isEmpty())
        assertNull(h.watermark(), "girisli degilken watermark yazilmamali")
    }

    @Test
    fun `upsert patlarsa watermark ilerlemez ve sonraki push yeniden dener`() = runTest {
        val h = Harness()
        h.repo.upsertPosition(quarter())
        h.repo.addTransaction(buy("tx-1", 2.0))

        h.postgrest.failTable = "transactions"
        runCatching { h.pushAt(2_000L) }
        assertNull(h.watermark(), "hata olunca watermark durmali")

        // Ag/sunucu duzeldi: watermark hala 0, ayni degisiklikler bastan gider.
        h.postgrest.failTable = null
        h.postgrest.clear()
        h.pushAt(3_000L)
        assertTrue("transactions" in h.postgrest.tables())
        assertEquals(3_000L, h.watermark())
    }
}
