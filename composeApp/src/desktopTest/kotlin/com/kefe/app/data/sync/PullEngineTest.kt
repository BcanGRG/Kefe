package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pull/merge kurallari GERCEK veritabani + sahte sunucu ile dogrulanir.
 *
 * LWW (son yazan kazanir), mezar tasi uygulama, turetilenin yeniden hesabi -
 * hicbiri ekranda tek basina gorunmez, elle denenemez.
 *
 * (Yardimcilar Pull* onekli: ayni paketteki PushEngineTest ile ad cakismasin.)
 */

private class PullClock(var millis: Long) : KefeClock {
    override fun today(): KefeDate = KefeDate(2026, 7, 30)
    override fun nowEpochMillis(): Long = millis
}

private class PullAuth(private val token: String?) : AuthRepository {
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

/** selectAll icin tablo -> JSON dizisi dondurur; upsert pull testinde kullanilmaz. */
private class PullApi : PostgrestApi {
    val tables = mutableMapOf<String, String>()
    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) = Unit
    override suspend fun selectAll(table: String, accessToken: String): String =
        tables[table] ?: "[]"
}

private val pullJson = Json { explicitNulls = true; encodeDefaults = true }

private inline fun <reified T> PullApi.put(table: String, rows: List<T>) {
    tables[table] = pullJson.encodeToString(rows)
}

private class PullHarness(token: String? = "tok") {
    val database: KefeDatabase
    val clock = PullClock(1_000L)
    val postgrest = PullApi()
    val repo: SqlDelightPortfolioRepository
    val engine: PullEngine

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        repo = SqlDelightPortfolioRepository(database, clock, NoPrices())
        engine = PullEngine(PullAuth(token), postgrest, SyncLocalSink(database))
    }

    suspend fun pull() = engine.pullOnce()
}

private fun pullPositionDto(id: String, name: String, updatedAt: Long, deletedAt: Long? = null) = PositionDto(
    id = id, userId = "u1", name = name, assetClass = AssetClass.Gold.name,
    subtype = GoldSubtype.Quarter.name, karat = null, unit = QuantityUnit.Piece.name,
    unitPrice = 10_000.0, manualPrice = false, updatedAt = updatedAt, deletedAt = deletedAt,
)

private fun pullTxDto(id: String, positionId: String, qty: Double, updatedAt: Long) = TransactionDto(
    id = id, userId = "u1", positionId = positionId, dateYear = 2026, dateMonth = 7, dateDay = 30,
    side = TradeSide.Buy.name, quantity = qty, unitPrice = 10_000.0, fee = 0.0, note = null,
    storage = null, addedByMemberId = "member_owner", updatedAt = updatedAt, deletedAt = null,
)

private fun pullLocalPosition(id: String = "pos_q", name: String = "Çeyrek") = Position(
    id = id, name = name, assetClass = AssetClass.Gold, subtype = GoldSubtype.Quarter,
    quantity = 0.0, unit = QuantityUnit.Piece, unitPrice = 10_000.0, value = 0.0, cost = 0.0,
)

class PullEngineTest {

    @Test
    fun `bos cihaz sunucudan varlik ve islem ceker`() = runTest {
        val h = PullHarness()
        h.postgrest.put("positions", listOf(pullPositionDto("pos_q", "Çeyrek", 1_000L)))
        h.postgrest.put("transactions", listOf(pullTxDto("tx1", "pos_q", 2.0, 1_000L)))

        val applied = h.pull()

        assertTrue(applied >= 2, "en az varlik + islem uygulanmali")
        val positions = h.repo.observePositions().first()
        assertEquals(1, positions.size)
        // Miktar DEFTERDEN yeniden hesaplandi.
        assertEquals(2.0, positions.single().quantity)
    }

    // Miktar 0 (islemsiz) pozisyon observePositions'ta gorunmez (quantity > 0
    // filtresi); LWW meta karsilastirmasi icin satiri DOGRUDAN okuruz.
    private fun PullHarness.positionName(id: String = "pos_q"): String? =
        database.positionQueries.selectPositionById(id).executeAsOneOrNull()?.name

    @Test
    fun `yerelde daha yeni satir korunur (LWW)`() = runTest {
        val h = PullHarness()
        h.clock.millis = 5_000L
        h.repo.upsertPosition(pullLocalPosition(name = "Yerel"))   // yerel updatedAt = 5000

        // Sunucu daha ESKI (1000): uygulanmamali.
        h.postgrest.put("positions", listOf(pullPositionDto("pos_q", "Sunucu", 1_000L)))
        h.pull()

        assertEquals("Yerel", h.positionName())
    }

    @Test
    fun `sunucu daha yeni ise uygulanir`() = runTest {
        val h = PullHarness()
        h.clock.millis = 1_000L
        h.repo.upsertPosition(pullLocalPosition(name = "Yerel"))   // yerel updatedAt = 1000

        h.postgrest.put("positions", listOf(pullPositionDto("pos_q", "Sunucu", 5_000L)))
        h.pull()

        assertEquals("Sunucu", h.positionName())
    }

    @Test
    fun `mezar tasi silinmis olarak uygulanir`() = runTest {
        val h = PullHarness()
        h.clock.millis = 1_000L
        h.repo.upsertPosition(pullLocalPosition())
        h.repo.addTransaction(
            Transaction(
                id = "tx1", positionId = "pos_q", date = KefeDate(2026, 7, 30),
                side = TradeSide.Buy, quantity = 2.0, unitPrice = 10_000.0,
                addedByMemberId = "member_owner",
            ),
        )
        assertEquals(1, h.repo.observePositions().first().size)

        // Sunucu varligi SILMIS (mezar tasi, daha yeni damga).
        h.postgrest.put("positions", listOf(pullPositionDto("pos_q", "Çeyrek", 9_000L, deletedAt = 9_000L)))
        h.pull()

        assertTrue(h.repo.observePositions().first().isEmpty(), "silinen varlik listede olmamali")
    }

    @Test
    fun `jeton yoksa cekmez`() = runTest {
        val h = PullHarness(token = null)
        h.postgrest.put("positions", listOf(pullPositionDto("pos_q", "Çeyrek", 1_000L)))

        val applied = h.pull()

        assertEquals(0, applied)
        assertTrue(h.repo.observePositions().first().isEmpty())
    }
}
