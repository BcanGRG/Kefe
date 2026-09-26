package com.kefe.app.data.sync

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
    }
}
