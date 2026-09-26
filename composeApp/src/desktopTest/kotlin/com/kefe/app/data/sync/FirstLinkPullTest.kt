package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
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

/**
 * Hesaba ILK baglanis: profil adlari hesaptan devralinir.
 *
 * NEYDI. Yeni telefonda "Profiller" ekranina yazilan adlar `updatedAt = simdi`
 * ile damgalaniyordu - hesaptaki gercek adlardan YENI. Giris yapilinca LWW yerel
 * adi korudu ve push onu iki telefonun ustune itti. Ilk baglanista hesap
 * devralinir; sonraki pull'lar normal LWW'dir (yerel ad degisikligi kaybolmaz).
 *
 * (Yardimcilar First* onekli: ayni paketteki diger testlerle ad cakismasin.)
 */

private class FirstClock(var millis: Long) : KefeClock {
    override fun today(): KefeDate = KefeDate(2026, 9, 26)
    override fun nowEpochMillis(): Long = millis
}

private class FirstAuth : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L)))
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = "tok"
    override suspend fun signOut() = Unit
}

private class FirstApi : PostgrestApi {
    val tables = mutableMapOf<String, String>()
    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) = Unit
    override suspend fun selectAll(table: String, accessToken: String): String = tables[table] ?: "[]"
}

private class FirstHarness {
    val database: KefeDatabase
    val clock = FirstClock(1_000L)
    val api = FirstApi()
    val repo: SqlDelightPortfolioRepository
    val engine: PullEngine

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        repo = SqlDelightPortfolioRepository(database, clock, NoPrices())
        engine = PullEngine(FirstAuth(), api, SyncLocalSink(database))
    }

    fun serverMembers(owner: String, partner: String, stamp: Long) {
        api.tables["members"] = Json.encodeToString(
            listOf(
                MemberDto(LocalOwnerMemberId, "u1", owner, owner.take(1), 0, stamp),
                MemberDto(LocalPartnerMemberId, "u1", partner, partner.take(1), 1, stamp),
            ),
        )
    }

    suspend fun names(): List<String> = repo.observeMembers().first().map { it.name }
    suspend fun stamps(): List<Long> = repo.observeMembers().first().map { it.updatedAt }
}

class FirstLinkPullTest {

    @Test
    fun `bos cihaz hesabin adlarini alir ve damgasini tasir`() = runTest {
        val h = FirstHarness()
        h.serverMembers("Burak Can", "Merve", 5_000L)
        h.engine.pullOnce()
        assertEquals(listOf("Burak Can", "Merve"), h.names())
        // Damga sunucununki: sonraki push'ta sunucu korumasi (<=) esitligi reddeder.
        assertEquals(listOf(5_000L, 5_000L), h.stamps())
    }

    /** Normal pull'da yerelde daha yeni ad KORUNUR - LWW degismedi. */
    @Test
    fun `normal pull yerel yeni adi korur`() = runTest {
        val h = FirstHarness()
        h.clock.millis = 9_000L
        h.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        h.serverMembers("Burak Can", "Merve", 5_000L)
        h.engine.pullOnce(adoptServerMembers = false)
        assertEquals("Yerel", h.names().first())
    }

    /** Ilk baglanista hesap devralinir - yerelde yazilmis ad hesabi EZMEZ. */
    @Test
    fun `ilk baglanista hesabin adlari devralinir`() = runTest {
        val h = FirstHarness()
        h.clock.millis = 9_000L
        h.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        h.serverMembers("Burak Can", "Merve", 5_000L)
        h.engine.pullOnce(adoptServerMembers = true)
        assertEquals(listOf("Burak Can", "Merve"), h.names())
        assertEquals(listOf(5_000L, 5_000L), h.stamps())
    }

    /** Hic adlandirilmamis (damgasi 0) hesap satiri yerel adi silmez. */
    @Test
    fun `adsiz hesap satiri devralinmaz`() = runTest {
        val h = FirstHarness()
        h.clock.millis = 9_000L
        h.repo.renameMember(LocalOwnerMemberId, "Yerel", "Y")
        h.serverMembers("Ben", "Eşim", 0L)
        h.engine.pullOnce(adoptServerMembers = true)
        assertEquals("Yerel", h.names().first())
    }

    /**
     * Sunucuda sonradan eklenen kolon eski satirda acik NULL tasiyabilir; tek
     * satir BUTUN pull'u dusurmemeli.
     */
    @Test
    fun `acik null sonradan gelen alan pull'u dusurmez`() = runTest {
        val h = FirstHarness()
        h.api.tables["positions"] = """[{"id":"pos_q","user_id":"u1","name":"Çeyrek","asset_class":"Gold",
            "subtype":"Quarter","karat":null,"unit":"Piece","unit_price":10000.0,"manual_price":false,
            "updated_at":1000,"deleted_at":null}]"""
        h.api.tables["transactions"] = """[{"id":"tx1","user_id":"u1","position_id":"pos_q","date_year":2026,
            "date_month":7,"date_day":30,"side":"Buy","quantity":2.0,"unit_price":10000.0,"fee":0.0,
            "note":null,"storage":null,"added_by_member_id":"member_owner","updated_at":1000,
            "deleted_at":null,"created_at":null,"goal_id":null,"goal_delta":null}]"""
        h.engine.pullOnce()
        assertEquals(2.0, h.repo.observePositions().first().single().quantity)
    }
}
