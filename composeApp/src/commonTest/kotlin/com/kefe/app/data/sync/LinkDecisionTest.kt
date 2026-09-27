package com.kefe.app.data.sync

import com.kefe.app.domain.model.YearMonth
import com.kefe.app.domain.model.incomeIdOf
import com.kefe.app.domain.repository.PreferenceKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hesaba baglanmanin KARAR TABLOSU (bkz. classifyLink) ve onizlemenin sayimi.
 *
 * NEYDI. Baglanti adimi hesabi indirip hemen uyguluyordu; cihazda da kayit
 * varsa iki portfoy kullanici hicbir sey gormeden karisiyordu. Bu kurallar
 * ekranda tek basina gorunmez: hangi durumda sorulacagi, hangisinde sorusuz
 * gecilecegi burada sabitlenir.
 */
class LinkDecisionTest {

    private fun preview(
        local: Int,
        server: Int,
        shared: Int = 0,
        restored: Boolean = false,
    ) = LinkPreview(
        localRecords = local,
        serverRecords = server,
        shared = shared,
        serverNamed = false,
        localRestored = restored,
    )

    // --- Tablo -----------------------------------------------------------

    @Test
    fun `bos cihaz her durumda indirir`() {
        assertEquals(LinkDecision.Download, classifyLink(preview(local = 0, server = 0)))
        assertEquals(LinkDecision.Download, classifyLink(preview(local = 0, server = 12)))
        // Geri yukleme izi bos cihazda soru sordurmaz: karistirilacak bir sey yok.
        assertEquals(LinkDecision.Download, classifyLink(preview(local = 0, server = 12, restored = true)))
    }

    @Test
    fun `bos hesaba cihazdakiler gider`() {
        assertEquals(LinkDecision.Upload, classifyLink(preview(local = 5, server = 0)))
        assertEquals(LinkDecision.Upload, classifyLink(preview(local = 5, server = 0, restored = true)))
    }

    @Test
    fun `ortak kayit ayni hesaba donustur`() {
        assertEquals(LinkDecision.Relink, classifyLink(preview(local = 5, server = 9, shared = 1)))
        assertEquals(LinkDecision.Relink, classifyLink(preview(local = 5, server = 9, shared = 5)))
    }

    @Test
    fun `ortak gecmis yoksa sorulur`() {
        assertEquals(LinkDecision.Conflict, classifyLink(preview(local = 5, server = 9, shared = 0)))
    }

    /**
     * Geri yukleme her satiri "simdi" damgalar: ortak kimlikler olsa bile LWW
     * yedegin eski halini hesabin ustune yazardi. Karar HER ZAMAN sorulur.
     */
    @Test
    fun `geri yukleme izi ortak kayda ragmen sordurur`() {
        assertEquals(LinkDecision.Conflict, classifyLink(preview(local = 5, server = 9, shared = 5, restored = true)))
    }

    // --- Ortaklik ----------------------------------------------------------

    /**
     * `pos_*` ve `member_*` her cihazda ayni turetilir: iki yabanci portfoy de
     * onlari paylasir. Sayilsalardi baska bir hesaba giris sorusuz birlesirdi.
     */
    @Test
    fun `belirlenimci kimlikler ortak sayilmaz`() {
        val deterministic = setOf("pos_gold_quarter", "member_owner", "member_partner", "act_del_pos_gold_quarter")
        assertEquals(0, sharedRecordCount(deterministic, deterministic))
        // Eski surumlerin icerikten turettigi kimlikler de iki yabanci portfoyde
        // ayni cikabilir.
        val legacy = setOf("tx_pos_gold_quarter_2026-07-30_2.0", "goal_ev_1")
        assertEquals(0, sharedRecordCount(legacy, legacy))
        assertEquals(1, sharedRecordCount(setOf(UuidA, "pos_x"), setOf(UuidA, "pos_x")))
    }

    @Test
    fun `rastgele kimlik bicimi`() {
        assertTrue(UuidA.isRandomRecordId())
        assertTrue(UuidA.uppercase().isRandomRecordId())
        assertFalse("pos_gold_quarter".isRandomRecordId())
        assertFalse("act_$UuidA".isRandomRecordId())
        assertFalse(UuidA.replace('-', 'x').isRandomRecordId())
        assertFalse(UuidA.dropLast(1).isRandomRecordId())
    }

    // --- Onizleme ------------------------------------------------------------

    @Test
    fun `onizleme canli kayitlari sayar, mezar taslarini ortaklikta kullanir`() {
        val local = LocalRecords(
            liveTransactions = mapOf(UuidA to "member_owner"),
            liveGoals = setOf(UuidC),
            allRecordIds = setOf(UuidA, UuidB, UuidC),
            namedMembers = true,
        )
        val batch = PullBatch(
            members = listOf(member("member_owner", "Burak Can", 5_000L), member("member_partner", "Eşim", 0L)),
            transactions = listOf(tx(UuidB, deletedAt = 7_000L), tx(UuidD)),
            goals = listOf(goal(UuidE)),
        )
        val p = buildLinkPreview(local, batch, localRestored = false)
        assertEquals(2, p.localRecords)
        assertEquals(2, p.serverRecords, "sunucudaki mezar tasi sayilmaz")
        assertEquals(1, p.shared, "silinmis ortak islem yine ayni gecmis")
        assertTrue(p.serverNamed)
        assertEquals(LinkDecision.Relink, classifyLink(p))
    }

    @Test
    fun `adsiz hesap profili adlandirilmis sayilmaz`() {
        val batch = PullBatch(members = listOf(member("member_owner", "Ben", 0L)))
        val prepared = prepareLink(batch, LocalRecords(), localRestored = false)
        assertFalse(prepared.preview.serverNamed)
        assertFalse(prepared.serverNamed)
        assertNull(prepared.serverOwnerName)
    }

    @Test
    fun `hazirlik hesabin adlarini ve cihaza ozgu kayitlari tasir`() {
        val local = LocalRecords(
            liveTransactions = mapOf(UuidA to "member_owner", UuidB to "member_owner", UuidC to "member_partner"),
            allRecordIds = setOf(UuidA, UuidB, UuidC),
        )
        val batch = PullBatch(
            members = listOf(member("member_owner", "Burak Can", 5_000L), member("member_partner", "Merve", 5_000L)),
            transactions = listOf(tx(UuidB), tx(UuidD)),
        )
        val prepared = prepareLink(batch, local, localRestored = true)
        assertEquals("Burak Can", prepared.serverOwnerName)
        assertEquals("Merve", prepared.serverPartnerName)
        assertEquals(LinkDecision.Conflict, prepared.decision)
        // UuidB hesapta da var: aktarilmaz. Kalan: owner'da 1, partner'da 1.
        assertEquals(mapOf("member_owner" to 1, "member_partner" to 1), prepared.localOnlyByAuthor)
    }

    // --- Plan, gelir, harcama ve butce ------------------------------------------

    /**
     * Yalniz plan girilmis cihaz BOS sayilsaydi dolu bir hesaba sorusuz
     * inerdi: cihazin Ekim maasi hesabinkiyle ayni kimlikte bulusup sessizce
     * hesabinkine donerdi. Plan satirlari da kayittir; iki yanda da kayit
     * varsa sorulur.
     */
    @Test
    fun `yalniz plani olan cihaz dolu hesapta sorulur`() {
        val local = LocalRecords(
            liveIncomes = listOf(localIncome("member_owner", month = 10)),
            liveExpenses = mapOf(UuidA to "member_owner"),
            livePlanRows = 2,
        )
        val batch = PullBatch(transactions = listOf(tx(UuidB)))
        val p = buildLinkPreview(local, batch, localRestored = false)
        assertEquals(4, p.localRecords, "gelir + harcama + plan satiri ve butce")
        assertEquals(1, p.serverRecords)
        assertEquals(0, p.shared)
        assertEquals(LinkDecision.Conflict, classifyLink(p))
    }

    @Test
    fun `yalniz plani olan cihaz bos hesaba yukler, bos cihaz yalniz plani olan hesabi indirir`() {
        val planOnly = LocalRecords(livePlanRows = 1)
        assertEquals(LinkDecision.Upload, prepareLink(PullBatch(), planOnly, localRestored = false).decision)

        val accountPlanOnly = PullBatch(
            planItems = listOf(planItem("pi_2026_10_gold_gram")),
            budgets = listOf(budget("eb_2026_10_Groceries", deletedAt = 7_000L)),
        )
        val download = prepareLink(accountPlanOnly, LocalRecords(), localRestored = false)
        assertEquals(LinkDecision.Download, download.decision)
        assertEquals(1, download.preview.serverRecords, "hesabin mezar tasi sayilmaz")

        // Hesapta yalniz plan var, cihazda islem: ikisi de kayit - sorulur.
        val deviceTx = LocalRecords(liveTransactions = mapOf(UuidA to "member_owner"), allRecordIds = setOf(UuidA))
        assertEquals(LinkDecision.Conflict, prepareLink(accountPlanOnly, deviceTx, localRestored = false).decision)
    }

    /**
     * Plan, gelir ve butce kimlikleri icerikten turer: iki yabanci portfoy de
     * "pi_2026_10_gold_gram" tasiyabilir. Ortak sayilsalardi baska bir hesaba
     * giris sorusuz birlesirdi. Harcama kimligi UUID - o ortak gecmisi soyler.
     */
    @Test
    fun `plan kimlikleri ortaklik saymaz, harcama UUID sayar`() {
        val contentIds = setOf("pi_2026_10_gold_gram", "inc_2026_10_member_owner_Salary", "eb_2026_10_Groceries")
        assertEquals(0, sharedRecordCount(contentIds, contentIds))

        val local = LocalRecords(
            liveExpenses = mapOf(UuidA to "member_owner"),
            allRecordIds = setOf(UuidA),
            livePlanRows = 1,
        )
        val sameAccount = PullBatch(expenses = listOf(expense(UuidA)), planItems = listOf(planItem("pi_2026_10_gold_gram")))
        val p = buildLinkPreview(local, sameAccount, localRestored = false)
        assertEquals(1, p.shared)
        assertEquals(LinkDecision.Relink, classifyLink(p))
    }

    /**
     * Ekrandaki "n kayıt aktarılır"in n'i aktarimin YAPACAGI seyi sayar.
     * NEYDI: hesapta olmayan her canli gelir sayiliyordu; hedefi hesapta dolu
     * olan (hesabinki kalir) ve bu cihazda dolu olan (yerinde kalir) satirlar
     * da "aktarılır" deniyordu.
     */
    @Test
    fun `aktarim sayisi harcamayi ve yalniz gercekten tasinacak geliri sayar`() {
        val local = LocalRecords(
            liveTransactions = mapOf(UuidA to "member_owner"),
            liveExpenses = mapOf(UuidB to "member_owner", UuidC to null, UuidD to "member_owner"),
            liveIncomes = listOf(
                // Hesapta ayni kimlik, ayni tutar: hesabin kendi satiri (geri yukleme).
                localIncome("member_owner", month = 9, amount = 75_000.0),
                // Hesapta ayni kimlik, BASKA tutar: baska kisinin maasi - tasinir.
                localIncome("member_owner", month = 10, amount = 60_000.0),
                // Hedef hesapta canli: hesabinki kalir, tasinmaz.
                localIncome("member_owner", month = 11, amount = 61_000.0),
                // Hedef bu cihazda canli: iki satir da yerinde kalir.
                localIncome("member_owner", month = 12, amount = 62_000.0),
                localIncome("member_partner", month = 12, amount = 40_000.0),
                // Ikinci profilden ilkine: hedef bos, tasinir.
                localIncome("member_partner", month = 10, amount = 5_000.0, kind = "Bonus"),
            ),
        )
        val batch = PullBatch(
            expenses = listOf(expense(UuidD)),
            incomes = listOf(
                income(incomeIdOf(YearMonth(2026, 9), "member_owner", "Salary"), "member_owner", month = 9, amount = 75_000.0),
                income(incomeIdOf(YearMonth(2026, 10), "member_owner", "Salary"), "member_owner", amount = 90_000.0),
                income(incomeIdOf(YearMonth(2026, 11), "member_partner", "Salary"), "member_partner", month = 11),
            ),
        )
        // Ekleyeni bos eski harcama (UuidC) hic kimseye sayilmaz; UuidD hesapta.
        // Ilk profil: islem + harcama + Ekim maasi. Ikinci: Ekim bonusu.
        assertEquals(mapOf("member_owner" to 3, "member_partner" to 1), localOnlyByAuthor(local, batch))

        val moves = planIncomeRemap(local.liveIncomes, batch.incomes, AuthorRemap("member_owner", "member_partner"))
        assertEquals(
            listOf(
                IncomeMove.Move(local.liveIncomes[1], "inc_2026_10_member_partner_Salary"),
                IncomeMove.YieldToAccount("inc_2026_11_member_owner_Salary"),
            ),
            moves,
        )
    }

    // --- Uygulama bicimi ve tercihler -----------------------------------------

    @Test
    fun `karar ve secimden uygulama bicimi`() {
        assertEquals(ApplyMode.Merge, applyModeFor(LinkDecision.Download, null))
        assertEquals(ApplyMode.Adopt, applyModeFor(LinkDecision.Upload, null))
        assertEquals(ApplyMode.Lww, applyModeFor(LinkDecision.Relink, null))
        assertEquals(ApplyMode.Replace, applyModeFor(LinkDecision.Conflict, ConflictChoice.UseAccount))
        assertEquals(ApplyMode.Merge, applyModeFor(LinkDecision.Conflict, ConflictChoice.Merge))
        assertFailsWith<IllegalStateException> { applyModeFor(LinkDecision.Conflict, null) }
    }

    /**
     * "Hesaptakileri kullan"dan sonra cihaz hesabin tam kopyasi: watermark
     * SIMDI, geri gidecek bir sey yok. Digerlerinde watermark silinir - cihazdaki
     * her sey hesaba gider.
     */
    @Test
    fun `baglanti tercihleri`() {
        val replace = linkSettings("u1", "e@k.app", "member_partner", ApplyMode.Replace, now = 42L)
        assertEquals("42", replace[PreferenceKeys.LastPushedAt])
        assertEquals("u1", replace[PreferenceKeys.CloudLinkUserId])
        assertEquals("e@k.app", replace[PreferenceKeys.CloudLinkEmail])
        assertEquals("member_partner", replace[PreferenceKeys.ActiveMemberId])
        assertTrue(PreferenceKeys.LocalRestoredAt in replace && replace[PreferenceKeys.LocalRestoredAt] == null)
        assertTrue(PreferenceKeys.LastSyncedAt in replace && replace[PreferenceKeys.LastSyncedAt] == null)

        for (mode in listOf(ApplyMode.Lww, ApplyMode.Adopt, ApplyMode.Merge)) {
            val s = linkSettings("u1", "e@k.app", "member_owner", mode, now = 42L)
            assertTrue(PreferenceKeys.LastPushedAt in s && s[PreferenceKeys.LastPushedAt] == null, "$mode")
        }
    }

    private companion object {
        const val UuidA = "0f8c2a4e-1b2c-4d3e-8f9a-0b1c2d3e4f5a"
        const val UuidB = "1a2b3c4d-5e6f-4a1b-9c2d-3e4f5a6b7c8d"
        const val UuidC = "2b3c4d5e-6f7a-4b2c-8d3e-4f5a6b7c8d9e"
        const val UuidD = "3c4d5e6f-7a8b-4c3d-9e4f-5a6b7c8d9e0f"
        const val UuidE = "4d5e6f7a-8b9c-4d4e-8f5a-6b7c8d9e0f1a"

        fun member(id: String, name: String, stamp: Long) =
            MemberDto(id, "u1", name, name.take(1), 0, stamp)

        fun tx(id: String, deletedAt: Long? = null) = TransactionDto(
            id = id, userId = "u1", positionId = "pos_gold_quarter", dateYear = 2026, dateMonth = 9,
            dateDay = 26, side = "Buy", quantity = 1.0, unitPrice = 10_000.0, fee = 0.0, note = null,
            storage = null, addedByMemberId = "member_owner", updatedAt = 5_000L, deletedAt = deletedAt,
        )

        fun goal(id: String) = GoalDto(
            id = id, userId = "u1", name = "Ev", iconKey = "home", amount = 1.0, unit = "Money",
            targetYear = 2030, targetMonth = 1, targetDay = 1, monthlyContribution = 0.0,
            isMain = true, status = "Active", sortOrder = 0, updatedAt = 5_000L, deletedAt = null,
        )

        fun planItem(id: String) = PlanItemDto(
            id = id, userId = "u1", periodYear = 2026, periodMonth = 10, assetKey = "gold_gram",
            assetName = "Gram Altın", mode = "Quantity", target = 10.0, updatedAt = 5_000L,
        )

        fun income(id: String, member: String, month: Int = 10, amount: Double = 80_000.0) = IncomeEntryDto(
            id = id, userId = "u1", periodYear = 2026, periodMonth = month.toLong(), memberId = member,
            kind = "Salary", amount = amount, updatedAt = 5_000L,
        )

        fun localIncome(member: String, month: Int, amount: Double = 80_000.0, kind: String = "Salary") = LocalIncome(
            id = incomeIdOf(YearMonth(2026, month), member, kind), periodYear = 2026, periodMonth = month.toLong(),
            memberId = member, kind = kind, amount = amount,
        )

        fun expense(id: String) = ExpenseEntryDto(
            id = id, userId = "u1", dateYear = 2026, dateMonth = 10, dateDay = 3, category = "Groceries",
            amount = 1_500.0, addedByMemberId = "member_owner", updatedAt = 5_000L,
        )

        fun budget(id: String, deletedAt: Long? = null) = ExpenseBudgetDto(
            id = id, userId = "u1", periodYear = 2026, periodMonth = 10, category = "Groceries",
            amount = 12_000.0, updatedAt = 5_000L, deletedAt = deletedAt,
        )
    }
}
