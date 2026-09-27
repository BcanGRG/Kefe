package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.repository.SqlDelightPlanRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.KefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.ExpenseEntry
import com.kefe.app.domain.model.IncomeKind
import com.kefe.app.domain.model.KefeDate
import com.kefe.app.domain.model.PlanItem
import com.kefe.app.domain.model.PlanTargetMode
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.budgetId
import com.kefe.app.domain.model.incomeId
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aylik plan, gelir, gider ve butce hesapla ESITLENIR - oteki tablolar gibi.
 *
 * NEYDI. Dort tablo (12.sqm) yalniz cihazda yasiyordu: esin telefonu plani
 * gormuyordu, "Bu cihazı sıfırla" onlari geri gelmemek uzere siliyordu. Burada
 * sabitlenen: push dordunu de (mezar taslariyla) gonderir; pull LWW ve mezar
 * tasiyla uygular; bu telefonun tanimadigi bir tur/kategori metni tabloda
 * AYNEN durur, okurken eslenir ve sunucuya aynen geri gider; bir telefonun
 * yazdigi otekinin PlanRepository'sinde gorunur.
 *
 * Sunucu sahte ama gercegin iki kuralini tasir: satir (hesap, kimlik) uzerinden
 * upsert edilir ve eski/esit damgali guncelleme yok sayilir (kefe_lww_guard).
 * (Yardimcilar Plan* onekli: ayni paketteki diger testlerle ad cakismasin.)
 */
class PlanSyncTest {

    private val oct = YearMonth(2026, 10)

    // --- Push ------------------------------------------------------------------

    @Test
    fun `push dort plan tablosunu da mezar taslariyla gonderir`() = runTest {
        val server = PlanServer()
        val a = PlanDevice(server)
        a.writeMonth(oct)

        a.pushAt(2_000L)

        val tables = server.upserts.map { it.first }
        assertTrue(
            tables.containsAll(listOf("plan_items", "income_entries", "expense_entries", "expense_budgets")),
            "$tables",
        )
        val plan = server.decoded<PlanItemDto>("plan_items").single()
        assertEquals("u1", plan.userId)
        assertEquals(planItemId(oct, "gold_gram"), plan.id)
        assertEquals("Quantity", plan.mode, "tur metin olarak gider")
        assertEquals(6_700.0, plan.unitPriceAtPlan)
        assertNull(plan.deletedAt)
        val income = server.decoded<IncomeEntryDto>("income_entries").single()
        assertEquals(LocalOwnerMemberId, income.memberId)
        assertEquals("Salary", income.kind)
        val expense = server.decoded<ExpenseEntryDto>("expense_entries").single()
        assertEquals("Groceries", expense.category)
        assertEquals(1_000L, expense.createdAt)
        assertEquals(LocalOwnerMemberId, expense.addedByMemberId)
        val budget = server.decoded<ExpenseBudgetDto>("expense_budgets").single()
        assertEquals(12_000.0, budget.amount)
        // NULL'lar acikca gider: dirilen satirin mezar tasi ancak boyle temizlenir.
        assertTrue("\"deleted_at\":null" in server.lastJson("plan_items"))

        // Silmeler de yazmadir: dordu de mezar tasiyla gider.
        server.upserts.clear()
        a.clock.millis = 3_000L
        a.plans.deletePlanItem(plan.id)
        a.plans.setIncome(oct, LocalOwnerMemberId, IncomeKind.Salary, null)
        a.plans.deleteExpense(expense.id)
        a.plans.setBudgets(oct, mapOf(ExpenseCategory.Groceries to null))
        a.pushAt(4_000L)

        for (table in listOf("plan_items", "income_entries", "expense_entries", "expense_budgets")) {
            val json = server.lastJson(table)
            assertTrue("\"deleted_at\":3000" in json, "$table: $json")
        }
    }

    // --- Pull ------------------------------------------------------------------

    @Test
    fun `pull plan tablolarini LWW ve mezar tasiyla uygular`() = runTest {
        val server = PlanServer()
        val b = PlanDevice(server)
        b.clock.millis = 5_000L
        b.plans.upsertPlanItem(gram(target = 10.0))                  // yerel 5000
        b.clock.millis = 1_000L
        b.plans.upsertPlanItem(afa(target = 1_000.0))                // yerel 1000
        b.plans.setIncome(oct, LocalOwnerMemberId, IncomeKind.Salary, 80_000.0)

        server.put(
            "plan_items",
            listOf(
                planDto(planItemId(oct, "gold_gram"), "gold_gram", target = 99.0, stamp = 2_000L),   // eski
                planDto(planItemId(oct, "fund_afa"), "fund_afa", target = 3_000.0, stamp = 9_000L),  // yeni
            ),
        )
        server.put(
            "income_entries",
            listOf(incomeDto(incomeId(oct, LocalOwnerMemberId, IncomeKind.Salary), stamp = 9_000L, deleted = 9_000L)),
        )
        // Sira damgasi tasimayan (eski) bir harcama: updatedAt'e duser.
        server.put("expense_entries", listOf(expenseDto(PlanUuid, stamp = 7_000L, createdAt = 0L)))
        server.put("expense_budgets", listOf(budgetDto("Groceries", 12_000.0, stamp = 7_000L)))

        b.pull.pullOnce()

        val items = b.plans.observePlanItems().first().associateBy { it.assetKey }
        assertEquals(10.0, items.getValue("gold_gram").target, "yerel daha yeni: korunur")
        assertEquals(3_000.0, items.getValue("fund_afa").target, "sunucu daha yeni: uygulanir")
        val book = b.plans.observeMonthBook(oct).first()
        assertTrue(book.incomes.isEmpty(), "mezar tasi geliri siler")
        assertEquals(listOf(PlanUuid), book.expenses.map { it.id })
        assertEquals(7_000L, book.expenses.single().createdAt)
        assertEquals(12_000.0, book.budgets.single().amount)
    }

    /**
     * Daha yeni bir surum yeni bir tur/kategori ekleyebilir. Bu telefon onu
     * tanimaz ama BOZMAZ: tabloda metin aynen durur, okurken savunmaci eslenir
     * (bilinmeyen gelir "Ek gelir", kategori "Diğer", plan satiri hesaptan
     * duser) ve sunucuya aynen geri gider. Enum'a cevrilseydi bu telefon
     * "Bonus"u "Extra"ya cevirip iki telefona birden itiyordu.
     */
    @Test
    fun `bilinmeyen tur metni aynen saklanir, okurken eslenir, aynen geri gider`() = runTest {
        val server = PlanServer()
        val b = PlanDevice(server)
        server.put("plan_items", listOf(planDto("pi_2026_10_gold_gram", "gold_gram", target = 5.0, stamp = 3_000L, mode = "Percent")))
        server.put("income_entries", listOf(incomeDto("inc_2026_10_member_owner_Bonus", stamp = 3_000L, kind = "Bonus")))
        server.put("expense_entries", listOf(expenseDto(PlanUuid, stamp = 3_000L, category = "Pets")))
        server.put("expense_budgets", listOf(budgetDto("Pets", 900.0, stamp = 3_000L)))

        b.pull.pullOnce()

        // Tabloda ham metin.
        assertEquals("Percent", b.database.planItemQueries.selectPlanItemsChangedSince(0).executeAsOne().mode)
        assertEquals("Bonus", b.database.incomeQueries.selectIncomeChangedSince(0).executeAsOne().kind)
        assertEquals("Pets", b.database.expenseQueries.selectExpensesChangedSince(0).executeAsOne().category)
        assertEquals("Pets", b.database.expenseQueries.selectBudgetsChangedSince(0).executeAsOne().category)

        // Okurken esleme.
        assertTrue(b.plans.observePlanItems().first().isEmpty(), "birimi bilinmeyen plan satiri hesaba girmez")
        val book = b.plans.observeMonthBook(oct).first()
        assertEquals(IncomeKind.Extra, book.incomes.single().kind)
        assertEquals(ExpenseCategory.Other, book.expenses.single().category)
        assertEquals(ExpenseCategory.Other, book.budgets.single().category)

        // Geri aynen gider (watermark yok: cihazdaki her sey gider).
        server.upserts.clear()
        b.pushAt(4_000L)
        assertTrue("\"mode\":\"Percent\"" in server.lastJson("plan_items"))
        assertTrue("\"kind\":\"Bonus\"" in server.lastJson("income_entries"))
        assertTrue("\"category\":\"Pets\"" in server.lastJson("expense_entries"))
        assertTrue("\"category\":\"Pets\"" in server.lastJson("expense_budgets"))
    }

    // --- Iki telefon -------------------------------------------------------------

    @Test
    fun `iki telefon - A yazar, B ceker ve PlanRepository'de gorur, geri de akar`() = runTest {
        val server = PlanServer()
        val a = PlanDevice(server)
        val b = PlanDevice(server)

        a.writeMonth(oct)
        a.pushAt(2_000L)
        b.pull.pullOnce()

        val bItems = b.plans.observePlanItems().first()
        assertEquals(listOf(gram(target = 10.0)), bItems, "plan satiri alan alan ayni")
        val bBook = b.plans.observeMonthBook(oct).first()
        assertEquals(80_000.0, bBook.incomes.single().amount)
        assertEquals(LocalOwnerMemberId, bBook.incomes.single().memberId)
        assertEquals(ExpenseCategory.Groceries, bBook.expenses.single().category)
        assertEquals("Market", bBook.expenses.single().note)
        assertEquals(12_000.0, bBook.budgets.single().amount)

        // B duzenler ve ekler; A ceker.
        b.clock.millis = 3_000L
        b.plans.upsertPlanItem(gram(target = 12.0))
        b.plans.setIncome(oct, LocalPartnerMemberId, IncomeKind.Salary, 70_000.0)
        b.plans.deleteExpense(bBook.expenses.single().id)
        b.pushAt(4_000L)
        a.pull.pullOnce()

        assertEquals(12.0, a.plans.observePlanItems().first().single().target)
        val aBook = a.plans.observeMonthBook(oct).first()
        assertEquals(
            mapOf(LocalOwnerMemberId to 80_000.0, LocalPartnerMemberId to 70_000.0),
            aBook.incomes.associate { it.memberId to it.amount },
        )
        assertTrue(aBook.expenses.isEmpty(), "B'nin sildigi harcama A'da da silinir")

        // Ayni ayin ayni satiri tek satir kalir - iki telefon da ayni kimligi yazdi.
        assertEquals(1L, a.database.planItemQueries.countPlanItems().executeAsOne())
    }

    // --- Yerel sinyaller ----------------------------------------------------------

    /**
     * Push'u tetikleyen sinyal ve "Hesaptan çık"in "gönderilmemiş değişiklik"
     * uyarisi plan tablolarini da gormeli: gormeseler yalniz plan degisen bir
     * gunde hicbir sey gitmez, cikis da uyarmazdi.
     */
    @Test
    fun `plan yazmasi push'u tetikler ve gonderilmemis degisiklik sayilir`() = runTest {
        val server = PlanServer()
        val a = PlanDevice(server)
        assertFalse(a.source.hasChangesSince(null), "kurulumun adsiz profilleri degisiklik degil")

        val signals = Channel<Unit>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.Default) { a.source.localChanges().collect { signals.send(Unit) } }
        awaitSignal(signals)                       // ilk deger

        a.clock.millis = 2_000L
        a.plans.upsertExpense(groceries())
        awaitSignal(signals)
        assertTrue(a.source.hasChangesSince(1_500L))
        assertFalse(a.source.hasChangesSince(2_500L))

        a.clock.millis = 3_000L
        a.plans.setBudgets(oct, mapOf(ExpenseCategory.Bills to 4_000.0))
        awaitSignal(signals)
        a.clock.millis = 4_000L
        a.plans.upsertPlanItem(afa(target = 2_000.0))
        awaitSignal(signals)
        a.clock.millis = 5_000L
        a.plans.setIncome(oct, LocalOwnerMemberId, IncomeKind.Extra, 1_000.0)
        awaitSignal(signals)
        assertTrue(a.source.hasChangesSince(4_500L), "gelir de gonderilmemis degisiklik")
    }

    // --- Yardimcilar ---------------------------------------------------------------

    private suspend fun awaitSignal(signals: Channel<Unit>) {
        // Akis gercek is parcaciginda; runTest'in sanal saati onu beklemez.
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { signals.receive() } }
    }

    private fun gram(target: Double) = PlanItem(
        id = planItemId(oct, "gold_gram"),
        month = oct,
        assetKey = "gold_gram",
        assetName = "Gram Altın",
        mode = PlanTargetMode.Quantity,
        target = target,
        goalId = null,
        unitPriceAtPlan = 6_700.0,
    )

    private fun afa(target: Double) = PlanItem(
        id = planItemId(oct, "fund_afa"),
        month = oct,
        assetKey = "fund_afa",
        assetName = "AFA",
        mode = PlanTargetMode.Amount,
        target = target,
    )

    private fun groceries() = ExpenseEntry(
        id = PlanUuid,
        date = KefeDate(2026, 10, 3),
        category = ExpenseCategory.Groceries,
        amount = 1_500.0,
        note = "Market",
        addedByMemberId = LocalOwnerMemberId,
    )

    /** Ekim'in butun defteri, saat 1000'de: plan, maas, harcama, butce. */
    private suspend fun PlanDevice.writeMonth(month: YearMonth) {
        clock.millis = 1_000L
        plans.upsertPlanItem(gram(target = 10.0))
        plans.setIncome(month, LocalOwnerMemberId, IncomeKind.Salary, 80_000.0)
        plans.upsertExpense(groceries())
        plans.setBudgets(month, mapOf(ExpenseCategory.Groceries to 12_000.0))
    }

    private fun planDto(
        id: String,
        assetKey: String,
        target: Double,
        stamp: Long,
        mode: String = "Quantity",
    ) = PlanItemDto(
        id = id, userId = "u1", periodYear = 2026, periodMonth = 10, assetKey = assetKey,
        assetName = assetKey, mode = mode, target = target, updatedAt = stamp,
    )

    private fun incomeDto(id: String, stamp: Long, deleted: Long? = null, kind: String = "Salary") = IncomeEntryDto(
        id = id, userId = "u1", periodYear = 2026, periodMonth = 10, memberId = LocalOwnerMemberId,
        kind = kind, amount = 80_000.0, updatedAt = stamp, deletedAt = deleted,
    )

    private fun expenseDto(id: String, stamp: Long, createdAt: Long = stamp, category: String = "Groceries") =
        ExpenseEntryDto(
            id = id, userId = "u1", dateYear = 2026, dateMonth = 10, dateDay = 3, category = category,
            amount = 1_500.0, note = null, addedByMemberId = LocalOwnerMemberId, createdAt = createdAt,
            updatedAt = stamp,
        )

    private fun budgetDto(category: String, amount: Double, stamp: Long) = ExpenseBudgetDto(
        id = "eb_2026_10_$category", userId = "u1", periodYear = 2026, periodMonth = 10,
        category = category, amount = amount, updatedAt = stamp,
    )
}

private const val PlanUuid = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b"

private class PlanClock(var millis: Long) : KefeClock {
    override fun today(): KefeDate = KefeDate(2026, 10, 5)
    override fun nowEpochMillis(): Long = millis
}

private class PlanAuth : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L)))
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = "tok"
    override suspend fun signOut() = Unit
}

private class PlanPrefs : PreferencesRepository {
    val map = mutableMapOf<String, String>()
    override fun observeAll(): Flow<Map<String, String>> = flowOf(map.toMap())
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun get(key: String): String? = map[key]
    override suspend fun putAll(changes: Map<String, String?>) {
        changes.forEach { (key, value) -> if (value == null) map.remove(key) else map[key] = value }
    }
}

private val planJson = Json { explicitNulls = true; encodeDefaults = true }

/**
 * Tek hesabin sahte sunucusu. Upsert satiri kimligiyle saklar ve eski/esit
 * damgali guncellemeyi yok sayar - canlidaki kefe_lww_guard ile ayni kural.
 */
private class PlanServer : PostgrestApi {
    val rows = mutableMapOf<String, MutableMap<String, JsonObject>>()
    val upserts = mutableListOf<Pair<String, String>>()

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        upserts += table to rowsJson
        val stored = rows.getOrPut(table) { linkedMapOf() }
        Json.parseToJsonElement(rowsJson).jsonArray.forEach { element ->
            val row = element.jsonObject
            val key = row.rowKey()
            val old = stored[key]
            if (old == null || row.stamp() > old.stamp()) stored[key] = row
        }
    }

    override suspend fun selectAll(table: String, accessToken: String): String =
        JsonArray(rows[table]?.values?.toList().orEmpty()).toString()

    inline fun <reified T> put(table: String, list: List<T>) {
        val stored = rows.getOrPut(table) { linkedMapOf() }
        Json.parseToJsonElement(planJson.encodeToString(list)).jsonArray.forEach {
            stored[it.jsonObject.rowKey()] = it.jsonObject
        }
    }

    fun lastJson(table: String): String = upserts.last { it.first == table }.second

    inline fun <reified T> decoded(table: String): List<T> = Json.decodeFromString<List<T>>(lastJson(table))

    private fun JsonObject.stamp(): Long = getValue("updated_at").jsonPrimitive.long
}

// Satirin sunucudaki anahtari (hesap tek, yalniz kimlik yeter). Kimligi
// olmayan tablolar icin (atama, gunluk fotograf) dogal anahtar.
private fun JsonObject.rowKey(): String =
    this["id"]?.jsonPrimitive?.content
        ?: this["position_id"]?.jsonPrimitive?.content
        ?: listOf("date_year", "date_month", "date_day").joinToString("-") { this[it]?.toString().orEmpty() }

/** Tek telefon: kendi veritabani ve saati, ortak sunucu. */
private class PlanDevice(server: PlanServer) {
    val database: KefeDatabase
    val clock = PlanClock(1_000L)
    val prefs = PlanPrefs()
    val plans: SqlDelightPlanRepository
    val source: SyncLocalSource
    val push: PushEngine
    val pull: PullEngine

    init {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        plans = SqlDelightPlanRepository(database, clock)
        source = SyncLocalSource(database)
        val auth = PlanAuth()
        push = PushEngine(auth, source, server, prefs, clock)
        pull = PullEngine(auth, server, SyncLocalSink(database))
    }

    /** Push yazmalardan SONRA olur (uretimde debounce); saat once ilerler. */
    suspend fun pushAt(millis: Long) {
        clock.millis = millis
        push.pushOnce("u1")
    }
}
