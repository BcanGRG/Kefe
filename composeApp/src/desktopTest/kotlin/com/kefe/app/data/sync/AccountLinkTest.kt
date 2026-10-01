package com.kefe.app.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kefe.app.data.db.LocalOwnerMemberId
import com.kefe.app.data.db.LocalPartnerMemberId
import com.kefe.app.data.db.bootstrapIfNeeded
import com.kefe.app.data.db.createKefeDatabase
import com.kefe.app.data.remote.PostgrestApi
import com.kefe.app.data.repository.NoPrices
import com.kefe.app.data.repository.SqlDelightPortfolioRepository
import com.kefe.app.data.repository.SqlDelightPreferencesRepository
import com.kefe.app.db.KefeDatabase
import com.kefe.app.domain.FixedKefeClock
import com.kefe.app.domain.model.ExpenseCategory
import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.budgetId
import com.kefe.app.domain.model.incomeIdOf
import com.kefe.app.domain.model.planItemId
import com.kefe.app.domain.repository.AuthRepository
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.testing.TestMain
import com.kefe.app.ui.screens.account.ProfileSetupIntent
import com.kefe.app.ui.screens.account.ProfileSetupPhase
import com.kefe.app.ui.screens.account.ProfileSetupUiState
import com.kefe.app.ui.screens.account.ProfileSetupViewModel
import com.kefe.app.ui.screens.account.failureMessage
import com.kefe.app.ui.screens.account.remapNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hesaba baglanma: ONCE BAK, karar verilince TEK ISLEMDE YAZ.
 *
 * NEYDI. Baglanti adimi hesabi indirip hemen uyguluyordu. Cihazda da kayit
 * varsa iki portfoy kullaniciya sorulmadan karisiyordu; ilk pull patlayinca da
 * push yine gidiyor, yerelde yazilmis adlar hesabin ustune itiliyordu. Burada
 * sabitlenen:
 *   - onizleme HICBIR SEY yazmaz, patlarsa sunucuya tek satir gitmez;
 *   - bos cihaz hesabi indirir ve adlari devralir, secim zorunlu;
 *   - bos hesaba cihazdakilerin HEPSI gider (watermark sifirlanir);
 *   - "Birleştir": birlesim, ayni gunun fotografi hesaptan, tek ana hedef,
 *     cihazda girilenler secilen profile aktarilir;
 *   - "Hesaptakileri kullan": cihazdakiler gider - plan, gelir, gider ve butce
 *     dahil, yerlerine hesabinkiler gelir - geri hicbir sey gonderilmez;
 *   - ayni hesaba donus sorusuz baglanir; geri yukleme izi soru sordurur;
 *   - yalniz plani olan cihaz da "kayitli"dir: dolu hesapta sorulur; hesabin
 *     mezar tasi cihazin plan/butce satirini silmez; profil degisince harcama
 *     ve gelir de aktarilir, gelir yeni kimlikle yazilir.
 *
 * Sunucu sahte ve her upsert'i kaydeder. (Yardimcilar Link* onekli: ayni
 * paketteki diger testlerle ad cakismasin.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountLinkTest {

    /** Kurulan VM'ler ([viewModel]) testten sonra durdurulur (bkz. [TestMain]). */
    private val main = TestMain()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.release()

    // LinkHarness dosya duzeyinde, [main]'i goremez: VM kaydedilmek uzere burada kurulur.
    private fun LinkHarness.viewModel() = main.track(ProfileSetupViewModel(repo, prefs, auth, pull, linker))

    // --- Indirme (cihaz bos) ------------------------------------------------

    @Test
    fun `bos cihaz hesabi indirir ve adlari devralir`() = runTest {
        val h = LinkHarness()
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB)),
            goals = listOf(linkGoal(UuidG2, main = true)),
            snapshots = listOf(linkSnapshot(day = 26, value = 100.0, stamp = 5_000L)),
        )
        // Acilista cekilmis "bugun" fotografi: deger 0, damga yeni.
        h.local(PullBatch(snapshots = listOf(linkSnapshot(day = 26, value = 0.0, stamp = 49_000L))))

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Download, prepared.decision)
        assertEquals("Burak Can", prepared.serverOwnerName)
        // Onizleme bir sey yazmaz.
        assertEquals(emptyList(), h.txIds())
        assertEquals(listOf("Ben", "Eşim"), h.names())
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))

        h.linker.commit(prepared, null, "u1", "e@k.app", LocalPartnerMemberId)

        assertEquals(listOf(UuidB), h.txIds())
        assertEquals(listOf("Burak Can", "Merve"), h.names())
        assertEquals(listOf(5_000L, 5_000L), h.stamps(), "damga hesabinki - geri itilmez")
        assertEquals(100.0, h.snapshotValue(26), "ayni gunun fotografi hesaptan")
        assertEquals("u1", h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(LocalPartnerMemberId, h.prefs.get(PreferenceKeys.ActiveMemberId))
        assertNull(h.prefs.get(PreferenceKeys.LastPushedAt))
        assertEquals(emptyList(), h.api.upserts, "baglanti adimi hicbir sey gondermez")
    }

    /** Bos cihazda da secim ZORUNLU: cihazin eski secimi hesabin adlarini bilmiyordu. */
    @Test
    fun `indirmede secim zorunlu`() = runTest {
        val h = LinkHarness()
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.server(members = h.namedMembers(), transactions = listOf(linkTx(UuidB)))
        val vm = h.viewModel()
        vm.onIntent(ProfileSetupIntent.Load)
        val s = vm.await { it.phase == ProfileSetupPhase.Ready }

        assertTrue(s.linking)
        assertTrue(s.accountHasProfiles)
        assertEquals("Burak Can", s.ownerName)
        assertNull(s.thisDeviceIsOwner)
        assertFalse(s.canSave)
        assertEquals(emptyList(), h.txIds(), "secimden once hicbir sey inmez")
    }

    // --- Yukleme (hesap bos) ------------------------------------------------

    @Test
    fun `bos hesaba cihazdakilerin hepsi gider`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                positions = listOf(linkPosition(stamp = 3_000L)),
                transactions = listOf(linkTx(UuidA, stamp = 3_000L)),
                goals = listOf(linkGoal(UuidG1, stamp = 3_000L)),
            ),
        )
        // Onceki bir baglantinin watermark'i: kalsaydi 3000'deki kayitlar gitmezdi.
        h.prefs.put(PreferenceKeys.LastPushedAt, "8000")

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Upload, prepared.decision)
        h.linker.commit(prepared, null, "u1", "e@k.app", LocalOwnerMemberId)
        assertNull(h.prefs.get(PreferenceKeys.LastPushedAt))

        h.push.pushOnce("u1")
        val tables = h.api.upserts.map { it.first }
        assertTrue("transactions" in tables && "goals" in tables && "positions" in tables, "$tables")
        assertTrue(h.api.upserts.first { it.first == "transactions" }.second.contains(UuidA))
    }

    // --- Birlestir ------------------------------------------------------------

    @Test
    fun `birlestir iki tarafi birlestirir ve cihazda girilenleri aktarir`() = runTest {
        val h = LinkHarness()
        // Bu telefon hesapsizken "Merve"yi ilk profil yazmis ve kendini secmis.
        h.local(
            PullBatch(
                members = listOf(
                    MemberDto(LocalOwnerMemberId, "u1", "Merve", "M", 0, 9_000L),
                    MemberDto(LocalPartnerMemberId, "u1", "Burak", "B", 1, 9_000L),
                ),
                positions = listOf(linkPosition(stamp = 9_000L)),
                transactions = listOf(linkTx(UuidA, author = LocalOwnerMemberId, stamp = 9_000L)),
                goals = listOf(linkGoal(UuidG1, main = true, stamp = 9_000L)),
                snapshots = listOf(
                    linkSnapshot(day = 25, value = 1.0, stamp = 9_000L),
                    linkSnapshot(day = 24, value = 7.0, stamp = 9_000L),
                ),
                activity = listOf(linkActivity("act_$UuidA", LocalOwnerMemberId)),
            ),
        )
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB, author = LocalOwnerMemberId)),
            goals = listOf(linkGoal(UuidG2, main = true)),
            snapshots = listOf(linkSnapshot(day = 25, value = 2.0, stamp = 5_000L)),
            activity = listOf(linkActivity("act_$UuidB", LocalOwnerMemberId)),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision)
        assertEquals(mapOf(LocalOwnerMemberId to 1), prepared.localOnlyByAuthor)

        // Hesapta ilk profil "Burak Can": Merve ikinciyi secer.
        h.linker.commit(prepared, ConflictChoice.Merge, "u1", "e@k.app", LocalPartnerMemberId)

        assertEquals(setOf(UuidA, UuidB), h.txIds().toSet(), "iki tarafin kayitlari birlikte")
        val tx = h.txRows()
        assertEquals(LocalPartnerMemberId, tx.getValue(UuidA).addedByMemberId, "cihazda girilen aktarilir")
        assertEquals(50_000L, tx.getValue(UuidA).updatedAt, "aktarim bir yazma - hesaba gitmeli")
        assertEquals(LocalOwnerMemberId, tx.getValue(UuidB).addedByMemberId, "hesabin gecmisine dokunulmaz")
        assertEquals(5_000L, tx.getValue(UuidB).updatedAt)
        assertEquals(LocalPartnerMemberId, h.activityMember("act_$UuidA"))
        assertEquals(LocalOwnerMemberId, h.activityMember("act_$UuidB"))

        assertEquals(2.0, h.snapshotValue(25), "ayni gun: hesap kazanir")
        assertEquals(7.0, h.snapshotValue(24), "yalniz cihazdaki gun kalir")

        val goals = h.goalRows()
        assertTrue(goals.getValue(UuidG2).isMain, "ana hedef hesabinki")
        assertFalse(goals.getValue(UuidG1).isMain, "ana hedef tek")
        assertEquals(50_000L, goals.getValue(UuidG1).updatedAt)

        assertEquals(listOf("Burak Can", "Merve"), h.names())
        assertNull(h.prefs.get(PreferenceKeys.LastPushedAt), "birlesen her sey hesaba gitmeli")
        assertEquals(emptyList(), h.api.upserts)
    }

    // --- Hesaptakileri kullan -------------------------------------------------

    /**
     * NEYDI: plan tablolari esitlenmezken bu secim onlara dokunmuyordu. Artik
     * esitleniyorlar: "Bu cihazdaki kayıtlar silinir" onlar icin de dogru
     * olmali, yoksa cihazin plani hesabinkiyle karisip ilk push'la hesaba
     * giderdi.
     */
    @Test
    fun `hesaptakileri kullan cihazdakileri siler, plani da hesabinkiyle degistirir, geri gondermez`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                members = listOf(MemberDto(LocalOwnerMemberId, "u1", "Volkan", "V", 0, 9_000L)),
                positions = listOf(linkPosition(stamp = 9_000L)),
                transactions = listOf(linkTx(UuidA, stamp = 9_000L)),
                goals = listOf(linkGoal(UuidG1, stamp = 9_000L)),
                activity = listOf(linkActivity("act_$UuidA", LocalOwnerMemberId)),
                planItems = listOf(linkPlan("gold_gram", month = 9, target = 10.0, stamp = 9_000L)),
                incomes = listOf(linkIncome(LocalOwnerMemberId, month = 9, amount = 80_000.0, stamp = 9_000L)),
                expenses = listOf(linkExpense(UuidE1, stamp = 9_000L)),
                budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 9, amount = 9_000.0, stamp = 9_000L)),
            ),
        )
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB)),
            goals = listOf(linkGoal(UuidG2)),
            planItems = listOf(linkPlan("fund_afa", month = 9, target = 3_000.0)),
            incomes = listOf(linkIncome(LocalPartnerMemberId, month = 9, amount = 70_000.0)),
            expenses = listOf(linkExpense(UuidE2)),
            // Cihazdakiyle AYNI satir (Eylul market butcesi), daha eski damgali.
            budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 9, amount = 12_000.0)),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision)
        assertEquals(6, prepared.preview.localRecords, "islem + hedef + plan + gelir + harcama + butce")
        h.linker.commit(prepared, ConflictChoice.UseAccount, "u1", "e@k.app", LocalOwnerMemberId)

        assertEquals(listOf(UuidB), h.txIds())
        assertEquals(setOf(UuidG2), h.goalRows().keys)
        assertNull(h.database.activityQueries.selectActivityById("act_$UuidA").executeAsOneOrNull())
        assertEquals(listOf("Burak Can", "Merve"), h.names())
        assertEquals(setOf("pi_2026_09_fund_afa"), h.planRows().keys, "cihazin plani gider, hesabinki gelir")
        assertEquals(setOf("inc_2026_09_member_partner_Salary"), h.incomeRows().keys)
        assertEquals(setOf(UuidE2), h.expenseRows().keys)
        val budget = h.budgetRows().values.single()
        assertEquals(12_000.0, budget.amount, "ayni satirda da hesabinki - damgasi eski olsa bile")
        assertEquals(5_000L, budget.updatedAt)
        assertEquals("50000", h.prefs.get(PreferenceKeys.LastPushedAt))

        // Cihaz artik hesabin kopyasi: ilk push hicbir sey gondermez.
        h.push.pushOnce("u1")
        assertEquals(emptyList(), h.api.upserts, "silinen cihaz kayitlari hesaba geri gitmemeli")
    }

    // --- Plan tablolari -------------------------------------------------------

    /**
     * Cihazda YALNIZ plan var, hesapta kayit var: sorulur (bkz.
     * buildLinkPreview). Sayilmasaydi cihaz "bos" gorunur, sorusuz inerdi.
     *
     * "Birleştir": iki tarafin plani ve harcamalari birlikte kalir. Ayni ayin
     * ayni satiri (kimlik icerikten) hesaptan gelir. Hesabin MEZAR TASI ise
     * cihazin canli satirini silmez: cihazinki simdi damgalanir ki push'ta
     * sunucunun LWW korumasini gecsin - yoksa sonraki pull yine silerdi.
     */
    @Test
    fun `yalniz plani olan cihaz sorulur, birlestirde iki tarafin plani kalir`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                planItems = listOf(
                    linkPlan("gold_gram", month = 10, target = 10.0, stamp = 9_000L),
                    linkPlan("fund_afa", month = 10, target = 3_000.0, stamp = 3_000L),
                ),
                incomes = listOf(linkIncome(LocalOwnerMemberId, month = 10, amount = 80_000.0, stamp = 9_000L)),
                expenses = listOf(linkExpense(UuidE1, stamp = 9_000L)),
                budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 10, amount = 9_000.0, stamp = 3_000L)),
            ),
        )
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB)),
            planItems = listOf(
                linkPlan("gold_gram", month = 10, target = 20.0, stamp = 5_000L),
                linkPlan("fund_afa", month = 10, target = 1_000.0, stamp = 6_000L, deleted = 6_000L),
                linkPlan("gold_gram", month = 11, target = 5.0),
            ),
            expenses = listOf(linkExpense(UuidE2)),
            budgets = listOf(
                linkBudget(ExpenseCategory.Groceries, month = 10, amount = 12_000.0, stamp = 6_000L, deleted = 6_000L),
            ),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision, "yalniz plan da kayittir")
        assertEquals(5, prepared.preview.localRecords, "2 plan + 1 butce + 1 gelir + 1 harcama")
        assertEquals(4, prepared.preview.serverRecords, "islem + 2 canli plan + harcama; mezar taslari sayilmaz")
        assertEquals(emptyList(), h.api.upserts)
        h.linker.commit(prepared, ConflictChoice.Merge, "u1", "e@k.app", LocalOwnerMemberId)

        val plans = h.planRows()
        assertEquals(20.0, plans.getValue("pi_2026_10_gold_gram").target, "ayni satir: hesabinki")
        assertEquals(5.0, plans.getValue("pi_2026_11_gold_gram").target, "hesabin satiri gelir")
        val afa = plans.getValue("pi_2026_10_fund_afa")
        assertNull(afa.deletedAt, "hesabin mezar tasi cihazin planini silmez")
        assertEquals(3_000.0, afa.target)
        assertEquals(50_000L, afa.updatedAt, "cihazinki simdi damgalanir - hesaba gitmeli")
        val budget = h.budgetRows().getValue("eb_2026_10_Groceries")
        assertNull(budget.deletedAt)
        assertEquals(9_000.0, budget.amount)
        assertEquals(50_000L, budget.updatedAt)
        assertEquals(setOf(UuidE1, UuidE2), h.expenseRows().keys, "iki tarafin harcamalari birlikte")
        assertEquals(80_000.0, h.incomeRows().getValue("inc_2026_10_member_owner_Salary").amount)

        // Birlesen her sey hesaba gider; korunan satir mezar tasindan yeni damgali.
        h.push.pushOnce("u1")
        val pushed = linkJson.decodeFromString<List<PlanItemDto>>(h.api.upserts.first { it.first == "plan_items" }.second)
        val pushedAfa = pushed.single { it.id == "pi_2026_10_fund_afa" }
        assertNull(pushedAfa.deletedAt)
        assertTrue(pushedAfa.updatedAt > 6_000L, "sunucunun LWW korumasini gecmeli")
        assertTrue("expense_budgets" in h.api.upserts.map { it.first })
    }

    /** Hesap bos (yalniz eski bir mezar tasi): cihazin plani sorusuz, silinmeden hesaba gider. */
    @Test
    fun `yalniz plani olan cihaz bos hesaba yukler, eski mezar tasi butceyi silmez`() = runTest {
        val h = LinkHarness()
        h.local(PullBatch(budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 10, amount = 9_000.0, stamp = 3_000L))))
        h.server(
            budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 10, amount = 1.0, stamp = 6_000L, deleted = 6_000L)),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Upload, prepared.decision)
        h.linker.commit(prepared, null, "u1", "e@k.app", LocalOwnerMemberId)

        val budget = h.budgetRows().getValue("eb_2026_10_Groceries")
        assertNull(budget.deletedAt)
        assertEquals(9_000.0, budget.amount)
        assertEquals(50_000L, budget.updatedAt)
        h.push.pushOnce("u1")
        val pushed = linkJson.decodeFromString<List<ExpenseBudgetDto>>(
            h.api.upserts.first { it.first == "expense_budgets" }.second,
        ).single()
        assertNull(pushed.deletedAt)
        assertEquals(50_000L, pushed.updatedAt)
    }

    /**
     * Bu telefon hesapsizken ilk profildi, hesapta ikinciyi secti. Cihazda
     * girilen harcama ve gelir de yeni profile gecer (islem ve akistaki
     * kural). Gelirin kimligi kisiyi tasir: satir yeni kimlikle yazilir, eskisi
     * mezar taslanir - ikisi de hesaba gider. Hedef kimlik doluysa: hesabinki
     * kalir (ayni kisinin ayni ayki geliri tek bilgidir); cihazin kendi
     * satiriysa hicbiri silinmez, tasinmaz.
     */
    @Test
    fun `profil degisince harcama ve gelir de aktarilir, gelir yeni kimlikle yazilir`() = runTest {
        val h = LinkHarness()
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.local(
            PullBatch(
                // Harcama UUID: hesapta da olsaydi "ayni hesaba donus" olurdu.
                expenses = listOf(linkExpense(UuidE1, author = LocalOwnerMemberId, stamp = 9_000L)),
                incomes = listOf(
                    // Hedef kimlik bos.
                    linkIncome(LocalOwnerMemberId, month = 10, amount = 60_000.0, stamp = 9_000L),
                    // Bu surumun tanimadigi tur: metni korunur.
                    linkIncome(LocalOwnerMemberId, month = 10, amount = 5_000.0, stamp = 9_000L, kind = "Bonus"),
                    // Hedef kimlik hesapta dolu.
                    linkIncome(LocalOwnerMemberId, month = 11, amount = 61_000.0, stamp = 9_000L),
                    // Hedef kimlik bu cihazda dolu.
                    linkIncome(LocalOwnerMemberId, month = 12, amount = 62_000.0, stamp = 9_000L),
                    linkIncome(LocalPartnerMemberId, month = 12, amount = 40_000.0, stamp = 9_000L),
                ),
            ),
        )
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB)),
            expenses = listOf(linkExpense(UuidE2, author = LocalOwnerMemberId)),
            incomes = listOf(linkIncome(LocalPartnerMemberId, month = 11, amount = 70_000.0)),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision)
        // Ekranin "n kayıt aktarılır"i yalniz TASINACAKLARI sayar: harcama, Ekim
        // maasi ve bonus. Kasim hesabinkine birakilir, Aralik yerinde kalir;
        // ikinci profilin Aralik geliri de (hedefi cihazda dolu) tasinmaz.
        assertEquals(mapOf(LocalOwnerMemberId to 3), prepared.localOnlyByAuthor)
        h.linker.commit(prepared, ConflictChoice.Merge, "u1", "e@k.app", LocalPartnerMemberId)

        val expenses = h.expenseRows()
        assertEquals(LocalPartnerMemberId, expenses.getValue(UuidE1).addedByMemberId, "cihazda girilen aktarilir")
        assertEquals(50_000L, expenses.getValue(UuidE1).updatedAt, "aktarim bir yazma - hesaba gitmeli")
        assertEquals(LocalOwnerMemberId, expenses.getValue(UuidE2).addedByMemberId, "hesabin gecmisine dokunulmaz")

        val incomes = h.incomeRows()
        val moved = incomes.getValue("inc_2026_10_member_partner_Salary")
        assertEquals(60_000.0, moved.amount)
        assertEquals(LocalPartnerMemberId, moved.memberId)
        assertEquals(50_000L, moved.updatedAt)
        assertNull(moved.deletedAt)
        assertEquals(50_000L, incomes.getValue("inc_2026_10_member_owner_Salary").deletedAt, "eski kimlik mezar tasi")

        val bonus = incomes.getValue("inc_2026_10_member_partner_Bonus")
        assertEquals("Bonus", bonus.kind, "tanimadigi tur metni aynen tasinir")
        assertEquals(5_000.0, bonus.amount)
        assertEquals(50_000L, incomes.getValue("inc_2026_10_member_owner_Bonus").deletedAt)

        val nov = incomes.getValue("inc_2026_11_member_partner_Salary")
        assertEquals(70_000.0, nov.amount, "hedef hesapta: hesabinki kalir")
        assertNull(nov.deletedAt)
        assertEquals(50_000L, incomes.getValue("inc_2026_11_member_owner_Salary").deletedAt, "iki kez sayilmaz")

        val decOwner = incomes.getValue("inc_2026_12_member_owner_Salary")
        assertNull(decOwner.deletedAt, "hedef cihazda: tasinmaz, silinmez")
        assertEquals(62_000.0, decOwner.amount)
        assertEquals(9_000L, decOwner.updatedAt)
        assertEquals(40_000.0, incomes.getValue("inc_2026_12_member_partner_Salary").amount, "toplanmaz")

        // Yeni kimlik canli, eski kimlik mezar tasi olarak hesaba gider.
        h.push.pushOnce("u1")
        val pushed = linkJson.decodeFromString<List<IncomeEntryDto>>(
            h.api.upserts.first { it.first == "income_entries" }.second,
        ).associateBy { it.id }
        assertNull(pushed.getValue("inc_2026_10_member_partner_Salary").deletedAt)
        assertEquals(50_000L, pushed.getValue("inc_2026_10_member_owner_Salary").deletedAt)
    }

    /**
     * Gelirin kimligi kisiyi tasir ama ayni uye kimligi iki yanda BASKA
     * kisidir: cihazda ilk profil Merve, hesapta Burak. Merve'nin Ekim maasi
     * Burak'inkiyle AYNI kimlikte durur.
     *
     * NEYDI: aktarim hesabin satirlari uygulandiktan SONRA calisiyor ve
     * kimligi hesapta olan satiri atliyordu. "Birleştir" Merve'nin maasini
     * Burak'inkiyle ezdi, aktarim onu gormedi - sessizce kayboldu. Hesapta o
     * kimlikte yalniz mezar tasi olsaydi Merve'nin maasi Burak'in adina
     * hesaba gidiyordu. Hesapta ayni tutarla duran satir ise hesabin kendi
     * gecmisidir (geri yukleme): tasinmaz.
     */
    @Test
    fun `birlestirde hesapla ayni kimlikteki cihaz geliri ezilmez, yeni profile tasinir`() = runTest {
        val h = LinkHarness()
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.local(
            PullBatch(
                incomes = listOf(
                    // Merve'nin Ekim maasi; hesapta ayni kimlikte Burak'in maasi var.
                    linkIncome(LocalOwnerMemberId, month = 10, amount = 60_000.0, stamp = 9_000L),
                    // Merve'nin Kasim maasi; hesapta ayni kimlikte yalniz mezar tasi.
                    linkIncome(LocalOwnerMemberId, month = 11, amount = 61_000.0, stamp = 3_000L),
                    // Ayni hesabin yedeginden: Burak'in Eylul maasi, hesaptakiyle ayni.
                    linkIncome(LocalOwnerMemberId, month = 9, amount = 75_000.0, stamp = 9_000L),
                ),
            ),
        )
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidB)),
            incomes = listOf(
                linkIncome(LocalOwnerMemberId, month = 10, amount = 75_000.0),
                linkIncome(LocalOwnerMemberId, month = 11, amount = 75_000.0, stamp = 6_000L, deleted = 6_000L),
                linkIncome(LocalOwnerMemberId, month = 9, amount = 75_000.0),
            ),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision)
        assertEquals(mapOf(LocalOwnerMemberId to 2), prepared.localOnlyByAuthor, "Ekim ve Kasim tasinir, Eylul hesabin")
        h.linker.commit(prepared, ConflictChoice.Merge, "u1", "e@k.app", LocalPartnerMemberId)

        val incomes = h.incomeRows()
        val oct = incomes.getValue("inc_2026_10_member_partner_Salary")
        assertEquals(60_000.0, oct.amount, "Merve'nin maasi ezilmedi, onun profiline gecti")
        assertNull(oct.deletedAt)
        val burakOct = incomes.getValue("inc_2026_10_member_owner_Salary")
        assertEquals(75_000.0, burakOct.amount, "hesabin satiri kendi kimliginde kalir")
        assertNull(burakOct.deletedAt)
        assertEquals(5_000L, burakOct.updatedAt)

        val nov = incomes.getValue("inc_2026_11_member_partner_Salary")
        assertEquals(61_000.0, nov.amount)
        assertNull(nov.deletedAt)
        assertNotNull(incomes.getValue("inc_2026_11_member_owner_Salary").deletedAt, "Burak'in adina kalmadi")

        val sep = incomes.getValue("inc_2026_09_member_owner_Salary")
        assertNull(sep.deletedAt, "hesabin kendi satiri tasinmaz")
        assertNull(incomes["inc_2026_09_member_partner_Salary"])

        h.push.pushOnce("u1")
        val pushed = linkJson.decodeFromString<List<IncomeEntryDto>>(
            h.api.upserts.first { it.first == "income_entries" }.second,
        ).associateBy { it.id }
        assertEquals(60_000.0, pushed.getValue("inc_2026_10_member_partner_Salary").amount)
        assertNull(pushed.getValue("inc_2026_10_member_partner_Salary").deletedAt)
        assertEquals(61_000.0, pushed.getValue("inc_2026_11_member_partner_Salary").amount)
        val pushedNovOwner = pushed["inc_2026_11_member_owner_Salary"]
        assertTrue(pushedNovOwner == null || pushedNovOwner.deletedAt != null, "Merve'nin maasi Burak'in adina gitmez")
    }

    /**
     * "Bu cihazı sıfırla" plani da siler; onay metni "aynı e-postayla
     * girdiğinizde geri gelir" der. Plan esitlenince bu onun icin de dogru:
     * ayni hesaba yeniden baglanan bos cihaz plani hesaptan geri alir.
     */
    @Test
    fun `sifirlanan cihaz ayni hesaba donunce plan da geri gelir`() = runTest {
        val h = LinkHarness()
        h.server(
            members = h.namedMembers(),
            planItems = listOf(linkPlan("gold_gram", month = 10, target = 10.0)),
            incomes = listOf(linkIncome(LocalOwnerMemberId, month = 10, amount = 80_000.0)),
            expenses = listOf(linkExpense(UuidE1)),
            budgets = listOf(linkBudget(ExpenseCategory.Groceries, month = 10, amount = 12_000.0)),
        )
        val first = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Download, first.decision)
        h.linker.commit(first, null, "u1", "e@k.app", LocalOwnerMemberId)
        assertEquals(1, h.planRows().size)

        h.repo.deleteAllData()
        assertTrue(h.planRows().isEmpty() && h.incomeRows().isEmpty() && h.expenseRows().isEmpty())
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))

        val again = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Download, again.decision, "sifirlanan cihaz bos: sorusuz iner")
        h.linker.commit(again, null, "u1", "e@k.app", LocalOwnerMemberId)
        assertEquals(10.0, h.planRows().getValue("pi_2026_10_gold_gram").target)
        assertEquals(80_000.0, h.incomeRows().values.single().amount)
        assertEquals(setOf(UuidE1), h.expenseRows().keys)
        assertEquals(12_000.0, h.budgetRows().values.single().amount)
    }

    // --- Onizleme patlarsa ----------------------------------------------------

    @Test
    fun `onizleme patlarsa baglanti yazilmaz ve hicbir sey gitmez`() = runTest {
        val h = LinkHarness()
        h.local(PullBatch(transactions = listOf(linkTx(UuidA, stamp = 9_000L)), positions = listOf(linkPosition())))
        h.api.failWith = "Unable to resolve host"
        assertFailsWith<IllegalStateException> { h.linker.preview() }

        val vm = h.viewModel()
        vm.onIntent(ProfileSetupIntent.Load)
        val s = vm.await { it.phase == ProfileSetupPhase.Failed }
        assertEquals("Unable to resolve host", s.failureDetail)

        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(listOf(UuidA), h.txIds(), "cihazdakiler yerinde")
        assertEquals(emptyList(), h.api.upserts)
    }

    // --- Ayni hesaba donus ve geri yukleme --------------------------------------

    /** Acik cikistan sonra ayni hesaba giris: soru yok, secim yok - dogrudan baglanir. */
    @Test
    fun `ayni hesaba donus sorusuz baglanir`() = runTest {
        val h = LinkHarness()
        h.local(PullBatch(positions = listOf(linkPosition()), transactions = listOf(linkTx(UuidA, stamp = 9_000L))))
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalPartnerMemberId)
        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidA), linkTx(UuidB)),
        )
        val vm = h.viewModel()
        vm.onIntent(ProfileSetupIntent.Load)
        val s = vm.await { it.done }

        assertTrue(s.linkedNow)
        assertFalse(s.phase == ProfileSetupPhase.Conflict)
        assertEquals("u1", h.prefs.get(PreferenceKeys.CloudLinkUserId))
        assertEquals(LocalPartnerMemberId, h.prefs.get(PreferenceKeys.ActiveMemberId), "secim korunur")
        assertEquals(setOf(UuidA, UuidB), h.txIds().toSet())
        assertEquals(9_000L, h.txRows().getValue(UuidA).updatedAt, "LWW: cihazin yeni hali kalir")
    }

    /**
     * Hesapsizken geri yuklenen cihaz, ortak kayit olsa da soruyla baglanir.
     * Geri yukleme izini AYNI islemde yazar: ayri bir yazmada kalsaydi, arada
     * kapanan uygulama korumayi kaybederdi.
     */
    @Test
    fun `geri yukleme izi soru sordurur`() = runTest {
        val h = LinkHarness()
        h.local(PullBatch(positions = listOf(linkPosition()), transactions = listOf(linkTx(UuidA, stamp = 9_000L))))
        h.server(transactions = listOf(linkTx(UuidA)))
        assertEquals(LinkDecision.Relink, assertNotNull(h.linker.preview()).decision, "iz yokken ayni gecmis")

        h.repo.restoreBackup(h.repo.exportBackup(takenOn = "2026-09-26"))
        assertEquals("9000", h.prefs.get(PreferenceKeys.LocalRestoredAt))
        assertEquals(LinkDecision.Conflict, assertNotNull(h.linker.preview()).decision)
    }

    /**
     * Hesapsizken geri yuklenen cihaz "Birleştir"i secti. Yedekteki her satir
     * "simdi" damgali; hesap o zamandan beri bir islemi silmis, bir hedefi
     * yeniden adlandirmis. NEYDI: Birlestir LWW'ydi - yedegin eski kopyasi
     * hesabin yeni halinin ustune yaziliyor, silinen islem diriliyor ve push'la
     * iki telefona birden gidiyordu. Iki tarafta da olan kayit hesaptan gelir.
     */
    @Test
    fun `geri yuklenen cihazda birlestir hesabin halini korur`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                positions = listOf(linkPosition(stamp = 4_000L)),
                transactions = listOf(linkTx(UuidA, stamp = 4_000L)),
                goals = listOf(linkGoal(UuidG1, stamp = 4_000L)),
            ),
        )
        h.repo.restoreBackup(h.repo.exportBackup(takenOn = "2026-09-26"))
        assertEquals(9_000L, h.txRows().getValue(UuidA).updatedAt, "geri yukleme simdi damgalar")

        h.server(
            members = h.namedMembers(),
            positions = listOf(linkPosition()),
            transactions = listOf(linkTx(UuidA, stamp = 6_000L, deleted = 6_000L), linkTx(UuidB)),
            goals = listOf(linkGoal(UuidG1, stamp = 6_000L, name = "Araba")),
        )
        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Conflict, prepared.decision)
        h.linker.commit(prepared, ConflictChoice.Merge, "u1", "e@k.app", LocalOwnerMemberId)

        val a = h.txRows().getValue(UuidA)
        assertEquals(6_000L, a.deletedAt, "hesapta silinen islem geri gelmez")
        assertEquals(6_000L, a.updatedAt, "yedegin damgasi kalmaz - geri itilmez")
        assertEquals(listOf(UuidB), h.txIds())
        assertEquals("Araba", h.goalRows().getValue(UuidG1).name, "hesaptaki duzeltme kalir")
        assertNotNull(
            h.database.positionQueries.selectPositionById("pos_gold_quarter").executeAsOneOrNull(),
            "hesabin canli islemi pozisyonu canli tutar",
        )
    }

    /**
     * Pozisyon kimligi her portfoyde ayni (pos_<varlik>). Hesap bir zamanlar
     * ceyrek tutmus ve silmis; bu cihaz hala tutuyor, satiri da daha eski
     * damgali. NEYDI: hesabin mezar tasi LWW ile cihazin pozisyonuna dusuyor,
     * islemler canli kaliyor ama varlik "Hesaba bağlandı"nin hemen ardindan
     * toplamlardan kayboluyordu. Cihazin kendi hedefine yaptigi atama da
     * hesabin olu atamasiyla siliniyordu.
     */
    @Test
    fun `yuklemede hesabin pozisyon mezar tasi cihazin varligini silmez`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                positions = listOf(linkPosition(stamp = 3_000L)),
                transactions = listOf(linkTx(UuidA, stamp = 3_000L)),
                goals = listOf(linkGoal(UuidG1, stamp = 3_000L)),
                goalAssets = listOf(GoalAssetDto("pos_gold_quarter", "u1", UuidG1, 1.0, 3_000L, null)),
            ),
        )
        h.server(
            positions = listOf(linkPosition(stamp = 6_000L, deleted = 6_000L)),
            goals = listOf(linkGoal(UuidG2, stamp = 6_000L, deleted = 6_000L)),
            goalAssets = listOf(GoalAssetDto("pos_gold_quarter", "u1", UuidG2, -1.0, 6_000L, 6_000L)),
        )

        val prepared = assertNotNull(h.linker.preview())
        assertEquals(LinkDecision.Upload, prepared.decision)
        h.linker.commit(prepared, null, "u1", "e@k.app", LocalOwnerMemberId)

        val position = assertNotNull(
            h.database.positionQueries.selectPositionById("pos_gold_quarter").executeAsOneOrNull(),
            "canli islemi olan varlik silinmez",
        )
        assertEquals(1.0, position.quantity)
        assertEquals(50_000L, position.updatedAt, "dirilis bir yazma - hesaba gitmeli")
        val assignment = assertNotNull(
            h.database.goalAssetQueries.selectGoalAssetByPosition("pos_gold_quarter").executeAsOneOrNull(),
            "cihazin kendi hedefine atamasi kalir",
        )
        assertEquals(UuidG1, assignment.goalId)
        assertEquals(1.0, assignment.quantity)

        h.push.pushOnce("u1")
        val pushed = h.api.upserts.first { it.first == "positions" }.second
        assertTrue("\"deleted_at\":null" in pushed, pushed)
    }

    /**
     * Hesap okundu ama baglanti CIHAZA yazilamadi (burada: hesabin islemi,
     * hesapta olmayan bir pozisyona bakiyor - yabanci anahtar). NEYDI: ekran
     * "Hesabınıza ulaşılamadı. İnternet bağlantınızı kontrol edin" diyordu.
     */
    @Test
    fun `yerel yazma patlarsa ekran ag hatasi demez`() = runTest {
        val h = LinkHarness(foreignKeys = true)
        h.server(members = h.namedMembers(), transactions = listOf(linkTx(UuidB)))
        val vm = h.viewModel()
        vm.onIntent(ProfileSetupIntent.Load)
        vm.await { it.phase == ProfileSetupPhase.Ready }
        vm.onIntent(ProfileSetupIntent.SelectThisDevice(true))
        vm.onIntent(ProfileSetupIntent.Save)

        val s = vm.await { it.phase == ProfileSetupPhase.Failed }
        assertTrue(s.commitFailed)
        assertEquals(
            "Bağlantı kaydedilemedi; bu cihazda hiçbir şey değişmedi. Tekrar deneyin.",
            s.failureMessage(),
        )
        assertNull(h.prefs.get(PreferenceKeys.CloudLinkUserId), "baglanti yazilmaz")
        assertNull(h.prefs.get(PreferenceKeys.ActiveMemberId))
        assertEquals(emptyList(), h.txIds())
        assertEquals(listOf("Ben", "Eşim"), h.names(), "tek islem - adlar da inmez")

        // "Tekrar dene" yeniden bakar: metin yine ag metnine doner.
        vm.onIntent(ProfileSetupIntent.Retry)
        val again = vm.await { it.phase == ProfileSetupPhase.Ready }
        assertFalse(again.commitFailed)
        assertEquals(
            "Hesabınıza ulaşılamadı. İnternet bağlantınızı kontrol edip tekrar deneyin.",
            again.copy(phase = ProfileSetupPhase.Failed).failureMessage(),
        )
    }

    // --- Cakisma ekrani -----------------------------------------------------------

    @Test
    fun `cakismada secim sonra profil ve aktarim notu`() = runTest {
        val h = LinkHarness()
        h.local(
            PullBatch(
                positions = listOf(linkPosition()),
                transactions = listOf(linkTx(UuidA, author = LocalOwnerMemberId, stamp = 9_000L)),
            ),
        )
        h.prefs.put(PreferenceKeys.ActiveMemberId, LocalOwnerMemberId)
        h.server(members = h.namedMembers(), positions = listOf(linkPosition()), transactions = listOf(linkTx(UuidB)))
        val vm = h.viewModel()
        vm.onIntent(ProfileSetupIntent.Load)

        val conflict = vm.await { it.phase == ProfileSetupPhase.Conflict }
        assertEquals(1, conflict.conflictLocal)
        assertEquals(1, conflict.conflictServer)
        assertEquals(listOf(UuidA), h.txIds(), "soru sorulurken hicbir sey yazilmaz")

        vm.onIntent(ProfileSetupIntent.ChooseConflict(ConflictChoice.Merge))
        val pick = vm.await { it.phase == ProfileSetupPhase.Ready }
        assertEquals(ConflictChoice.Merge, pick.conflictChoice)
        assertNull(pick.thisDeviceIsOwner, "secim zorunlu")

        // Secim degistirilebilir.
        vm.onIntent(ProfileSetupIntent.BackToConflict)
        assertEquals(ProfileSetupPhase.Conflict, vm.state.value.phase)
        vm.onIntent(ProfileSetupIntent.ChooseConflict(ConflictChoice.Merge))
        vm.await { it.phase == ProfileSetupPhase.Ready }

        vm.onIntent(ProfileSetupIntent.SelectThisDevice(false))
        assertEquals(
            "Bu cihazda daha önce girilen 1 kayıt da Merve adına aktarılır.",
            vm.state.value.remapNote(),
        )
        vm.onIntent(ProfileSetupIntent.Save)
        val done = vm.await { it.done }
        assertTrue(done.linkedNow)
        assertEquals(LocalPartnerMemberId, h.txRows().getValue(UuidA).addedByMemberId)
        assertEquals(setOf(UuidA, UuidB), h.txIds().toSet())
        assertEquals(emptyList(), h.api.upserts)
    }
}

// --- Yardimcilar -------------------------------------------------------------------

private const val UuidA = "0f8c2a4e-1b2c-4d3e-8f9a-0b1c2d3e4f5a"
private const val UuidB = "1a2b3c4d-5e6f-4a1b-9c2d-3e4f5a6b7c8d"
private const val UuidG1 = "2b3c4d5e-6f7a-4b2c-8d3e-4f5a6b7c8d9e"
private const val UuidG2 = "3c4d5e6f-7a8b-4c3d-9e4f-5a6b7c8d9e0f"
private const val UuidE1 = "4d5e6f7a-8b9c-4d4e-8f5a-6b7c8d9e0f1a"
private const val UuidE2 = "5e6f7a8b-9c0d-4e5f-9a6b-7c8d9e0f1a2b"

private class LinkAuth : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.SignedIn(AuthSession("u1", "e@k.app", "tok", "r", 0L)))
    override fun observeAuthState(): Flow<AuthState> = state
    override val isCloudConfigured: Boolean = true
    override suspend fun sendCode(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun verifyCode(email: String, code: String): Result<Unit> = Result.success(Unit)
    override suspend fun validAccessToken(): String? = (state.value as? AuthState.SignedIn)?.session?.accessToken
    override suspend fun signOut() {
        state.value = AuthState.SignedOut
    }
}

/** Sahte sunucu: her upsert'i (tablo, JSON) kaydeder. */
private class LinkApi : PostgrestApi {
    val tables = mutableMapOf<String, String>()
    val upserts = mutableListOf<Pair<String, String>>()
    var failWith: String? = null

    override suspend fun upsert(table: String, rowsJson: String, accessToken: String) {
        upserts += table to rowsJson
    }

    override suspend fun selectAll(table: String, accessToken: String): String {
        failWith?.let { error(it) }
        return tables[table] ?: "[]"
    }
}

private val linkJson = Json { explicitNulls = true; encodeDefaults = true }

/**
 * [foreignKeys]: uretimdeki surucu gibi yabanci anahtarlar acik (bkz.
 * DatabaseDriver.desktop.kt) - yerel yazmanin patladigi durumu kurmak icin.
 */
private class LinkHarness(foreignKeys: Boolean = false) {
    val database: KefeDatabase
    val auth = LinkAuth()
    val api = LinkApi()
    val prefs: SqlDelightPreferencesRepository
    val repo: SqlDelightPortfolioRepository
    val sink: SyncLocalSink
    val pull: PullEngine
    val linker: AccountLinker
    val push: PushEngine

    init {
        val driver = if (foreignKeys) {
            JdbcSqliteDriver(
                JdbcSqliteDriver.IN_MEMORY,
                properties = Properties().apply { setProperty("foreign_keys", "true") },
            )
        } else {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        }
        KefeDatabase.Schema.create(driver)
        database = createKefeDatabase(driver)
        database.bootstrapIfNeeded()
        prefs = SqlDelightPreferencesRepository(database)
        repo = SqlDelightPortfolioRepository(database, FixedKefeClock(millis = 9_000L), NoPrices())
        sink = SyncLocalSink(database)
        pull = PullEngine(auth, api, sink)
        val now = FixedKefeClock(millis = 50_000L)
        linker = AccountLinker(pull, sink, prefs, now)
        push = PushEngine(auth, SyncLocalSource(database), api, prefs, now)
    }

    /** Cihazda duran satirlar (verilen damgalarla). */
    suspend fun local(batch: PullBatch) {
        sink.apply(batch)
    }

    fun namedMembers() = listOf(
        MemberDto(LocalOwnerMemberId, "u1", "Burak Can", "B", 0, 5_000L),
        MemberDto(LocalPartnerMemberId, "u1", "Merve", "M", 1, 5_000L),
    )

    fun server(
        members: List<MemberDto> = emptyList(),
        positions: List<PositionDto> = emptyList(),
        transactions: List<TransactionDto> = emptyList(),
        goals: List<GoalDto> = emptyList(),
        snapshots: List<SnapshotDto> = emptyList(),
        activity: List<ActivityDto> = emptyList(),
        goalAssets: List<GoalAssetDto> = emptyList(),
        planItems: List<PlanItemDto> = emptyList(),
        incomes: List<IncomeEntryDto> = emptyList(),
        expenses: List<ExpenseEntryDto> = emptyList(),
        budgets: List<ExpenseBudgetDto> = emptyList(),
    ) {
        api.tables["members"] = linkJson.encodeToString(members)
        api.tables["positions"] = linkJson.encodeToString(positions)
        api.tables["transactions"] = linkJson.encodeToString(transactions)
        api.tables["goals"] = linkJson.encodeToString(goals)
        api.tables["goal_assets"] = linkJson.encodeToString(goalAssets)
        api.tables["daily_snapshots"] = linkJson.encodeToString(snapshots)
        api.tables["activity_events"] = linkJson.encodeToString(activity)
        api.tables["plan_items"] = linkJson.encodeToString(planItems)
        api.tables["income_entries"] = linkJson.encodeToString(incomes)
        api.tables["expense_entries"] = linkJson.encodeToString(expenses)
        api.tables["expense_budgets"] = linkJson.encodeToString(budgets)
    }

    fun txIds(): List<String> =
        database.transactionQueries.selectAllTransactions().executeAsList().map { it.id }

    fun txRows() = database.transactionQueries.selectTransactionsChangedSince(0).executeAsList().associateBy { it.id }

    fun goalRows() = database.goalQueries.selectGoals().executeAsList().associateBy { it.id }

    fun activityMember(id: String): String? =
        database.activityQueries.selectActivityById(id).executeAsOneOrNull()?.memberId

    // Plan tablolarinin mezar taslari dahil butun satirlari.
    fun planRows() = database.planItemQueries.selectPlanItemsChangedSince(0).executeAsList().associateBy { it.id }
    fun incomeRows() = database.incomeQueries.selectIncomeChangedSince(0).executeAsList().associateBy { it.id }
    fun expenseRows() = database.expenseQueries.selectExpensesChangedSince(0).executeAsList().associateBy { it.id }
    fun budgetRows() = database.expenseQueries.selectBudgetsChangedSince(0).executeAsList().associateBy { it.id }

    fun snapshotValue(day: Long): Double? =
        database.snapshotQueries.selectSnapshots().executeAsList().firstOrNull { it.dateDay == day }?.totalValue

    suspend fun names(): List<String> = repo.observeMembers().first().map { it.name }
    suspend fun stamps(): List<Long> = repo.observeMembers().first().map { it.updatedAt }
}

private fun linkPosition(stamp: Long = 5_000L, deleted: Long? = null) = PositionDto(
    id = "pos_gold_quarter", userId = "u1", name = "Çeyrek", assetClass = "Gold", subtype = "Quarter",
    karat = null, unit = "Piece", unitPrice = 10_000.0, manualPrice = false, updatedAt = stamp, deletedAt = deleted,
)

private fun linkTx(
    id: String,
    author: String = LocalOwnerMemberId,
    stamp: Long = 5_000L,
    deleted: Long? = null,
) = TransactionDto(
    id = id, userId = "u1", positionId = "pos_gold_quarter", dateYear = 2026, dateMonth = 9, dateDay = 20,
    side = "Buy", quantity = 1.0, unitPrice = 10_000.0, fee = 0.0, note = null, storage = null,
    addedByMemberId = author, updatedAt = stamp, deletedAt = deleted,
)

private fun linkGoal(
    id: String,
    main: Boolean = false,
    stamp: Long = 5_000L,
    name: String = "Ev",
    deleted: Long? = null,
) = GoalDto(
    id = id, userId = "u1", name = name, iconKey = "home", amount = 1_000_000.0, unit = "Try",
    targetYear = 2030, targetMonth = 1, targetDay = 1, monthlyContribution = 0.0, isMain = main,
    status = "Active", sortOrder = 0, updatedAt = stamp, deletedAt = deleted,
)

private fun linkSnapshot(day: Long, value: Double, stamp: Long) = SnapshotDto(
    userId = "u1", dateYear = 2026, dateMonth = 9, dateDay = day, totalValue = value, principal = value,
    updatedAt = stamp,
)

private fun linkActivity(id: String, member: String) = ActivityDto(
    id = id, userId = "u1", memberId = member, kind = "AddTransaction", description = "1 Çeyrek ekledi",
    amount = 10_000.0, isManualPrice = false, occurredYear = 2026, occurredMonth = 9, occurredDay = 20,
    timeLabel = null, updatedAt = 5_000L, deletedAt = null,
)

private fun linkPlan(
    assetKey: String,
    month: Int,
    target: Double,
    stamp: Long = 5_000L,
    deleted: Long? = null,
) = PlanItemDto(
    id = planItemId(YearMonth(2026, month), assetKey), userId = "u1", periodYear = 2026,
    periodMonth = month.toLong(), assetKey = assetKey, assetName = assetKey, mode = "Quantity",
    target = target, updatedAt = stamp, deletedAt = deleted,
)

private fun linkIncome(
    member: String,
    month: Int,
    amount: Double,
    stamp: Long = 5_000L,
    kind: String = "Salary",
    deleted: Long? = null,
) = IncomeEntryDto(
    id = incomeIdOf(YearMonth(2026, month), member, kind), userId = "u1", periodYear = 2026,
    periodMonth = month.toLong(), memberId = member, kind = kind, amount = amount, updatedAt = stamp,
    deletedAt = deleted,
)

private fun linkExpense(id: String, author: String? = LocalOwnerMemberId, stamp: Long = 5_000L) = ExpenseEntryDto(
    id = id, userId = "u1", dateYear = 2026, dateMonth = 10, dateDay = 3, category = "Groceries",
    amount = 1_500.0, addedByMemberId = author, createdAt = stamp, updatedAt = stamp,
)

private fun linkBudget(
    category: ExpenseCategory,
    month: Int,
    amount: Double,
    stamp: Long = 5_000L,
    deleted: Long? = null,
) = ExpenseBudgetDto(
    id = budgetId(YearMonth(2026, month), category), userId = "u1", periodYear = 2026,
    periodMonth = month.toLong(), category = category.name, amount = amount, updatedAt = stamp,
    deletedAt = deleted,
)

/**
 * VM gercek is parcaciklarinda (Dispatchers.Default) calisan depolari bekler;
 * runTest'in sanal saati onlari beklemez.
 */
private suspend fun ProfileSetupViewModel.await(
    predicate: (ProfileSetupUiState) -> Boolean,
): ProfileSetupUiState =
    withContext(Dispatchers.Default.limitedParallelism(1)) {
        withTimeout(10_000) { state.first(predicate) }
    }
