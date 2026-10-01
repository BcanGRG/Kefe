package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.kefeDateOfEpochDay
import com.kefe.app.domain.model.toEpochDay
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.ui.screens.account.MemoryPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PriceHistorySyncTest {

    private val today = KefeDate(2026, 10, 1)

    private class Harness(val db: KefeDatabase, val server: HistoryServer, val prefs: MemoryPreferences, val sync: PriceHistorySync)

    private fun harness(clockDay: KefeDate = today): Harness {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        val db = createKefeDatabase(driver)
        val server = HistoryServer()
        val prefs = MemoryPreferences()
        val clock = object : KefeClock {
            override fun today(): KefeDate = clockDay
            override fun marketToday(): KefeDate = clockDay
            override fun nowEpochMillis(): Long = 0L
        }
        return Harness(db, server, prefs, PriceHistorySync(db, server, HistoryAuth, prefs, clock))
    }

    private fun KefeDatabase.local(key: String, date: KefeDate, price: Double) =
        priceQueries.upsertPriceHistory(key, date.year.toLong(), date.month.toLong(), date.day.toLong(), price)

    private fun KefeDatabase.prices(key: String): Map<String, Double> =
        priceQueries.selectPriceHistory(key).executeAsList()
            .associate { "%04d-%02d-%02d".format(it.dateYear, it.dateMonth, it.dateDay) to it.price }

    @Test
    fun yerelGunlerHesabaGider() = runTest {
        val h = harness()
        h.db.local("gold_k22", KefeDate(2026, 9, 24), 6132.0)
        h.db.local("gold_k22", today, 6017.0)
        h.sync.syncOnce("u1")
        assertEquals(6132.0, h.server.rows["gold_k22" to "2026-09-24"])
        assertEquals(6017.0, h.server.rows["gold_k22" to "2026-10-01"])
        // Satir hesabin kimligini tasir (RLS ve bilesik anahtar icin).
        assertTrue(h.server.lastBody.contains("\"user_id\":\"u1\""))
    }

    @Test
    fun hesaptanYalnizEksikGunlerGelirKendiGozlemiEzilmez() = runTest {
        val h = harness()
        h.db.local("gold_k22", KefeDate(2026, 9, 24), 6132.0)
        // Oteki telefon: ayni gunu farkli saatte gormus, ayrica 1 Eylul'u de.
        h.server.rows["gold_k22" to "2026-09-24"] = 6100.0
        h.server.rows["gold_k22" to "2026-09-01"] = 6236.0
        h.sync.syncOnce("u1")
        val local = h.db.prices("gold_k22")
        assertEquals(6132.0, local["2026-09-24"], "kendi gozlemi kalir")
        assertEquals(6236.0, local["2026-09-01"], "bos gun hesaptan dolar")
    }

    @Test
    fun cekimGundeBirKez() = runTest {
        val h = harness()
        h.sync.syncOnce("u1")
        h.server.rows["gold_k22" to "2026-09-20"] = 6200.0
        h.sync.syncOnce("u1")
        assertTrue(h.db.prices("gold_k22").isEmpty(), "ayni gun ikinci cekim yapilmaz")
        assertEquals(1, h.server.selects)
    }

    @Test
    fun binSatiriAsanGecmisSayfalanir() = runTest {
        val h = harness()
        // 3 varlik x 400 gun = 1200 satir: iki sayfa.
        listOf("a", "b", "c").forEach { key ->
            (0 until 400).forEach { back ->
                val d = kefeDateOfEpochDay(today.toEpochDay() - back)
                h.server.rows[key to "%04d-%02d-%02d".format(d.year, d.month, d.day)] = 1.0 + back
            }
        }
        h.sync.syncOnce("u1")
        assertEquals(2, h.server.selects)
        assertEquals(400, h.db.prices("b").size)
    }

    @Test
    fun baskaHesabaBaglanincaBastanBaslar() = runTest {
        val h = harness()
        h.prefs.put(PreferenceKeys.PriceHistoryPulled, "u0:${today.toEpochDay()}")
        h.server.rows["gold_k22" to "2026-09-20"] = 6200.0
        h.sync.syncOnce("u1")
        assertEquals(6200.0, h.db.prices("gold_k22")["2026-09-20"])
    }
}

private object HistoryAuth : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L)))
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = "tok"
    override suspend fun signOut() = Unit
}

/** Tek hesabin price_history tablosu: (asset_key, day) -> fiyat; PostgREST sayfalamasini taklit eder. */
private class HistoryServer : PostgrestApi {
    val rows = sortedMapOf<Pair<String, String>, Double>(compareBy({ it.second }, { it.first }))
    var lastBody = ""
    var selects = 0

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        lastBody = rowsJson
        Json.parseToJsonElement(rowsJson).jsonArray.forEach { element ->
            val row = element.jsonObject
            rows[row.getValue("asset_key").jsonPrimitive.content to row.getValue("day").jsonPrimitive.content] =
                row.getValue("price").jsonPrimitive.double
        }
    }

    override suspend fun selectAll(table: String, accessToken: String): String = error("kullanilmaz")

    override suspend fun select(table: String, query: String, accessToken: String): String {
        selects++
        val params = query.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        val since = params.getValue("day").removePrefix("gte.")
        val limit = params.getValue("limit").toInt()
        val offset = params.getValue("offset").toInt()
        val page = rows.entries.filter { it.key.second >= since }.drop(offset).take(limit)
        return JsonArray(
            page.map { (key, price) ->
                JsonObject(
                    mapOf(
                        "asset_key" to JsonPrimitive(key.first),
                        "day" to JsonPrimitive(key.second),
                        "price" to JsonPrimitive(price),
                    ),
                )
            },
        ).toString()
    }
}
