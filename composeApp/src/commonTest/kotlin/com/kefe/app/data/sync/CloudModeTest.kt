package com.kefe.app.data.sync

import com.kefe.app.domain.model.SyncState
import com.kefe.app.domain.repository.AuthSession
import com.kefe.app.domain.repository.AuthState
import com.kefe.app.domain.repository.PriceFreshness
import com.kefe.app.ui.components.CloudTone
import com.kefe.app.ui.components.accountBannerCopy
import com.kefe.app.ui.components.longLabel
import com.kefe.app.ui.components.shortLabel
import com.kefe.app.ui.components.tone
import com.kefe.app.ui.screens.account.AccountAction
import com.kefe.app.ui.screens.account.accountSection
import com.kefe.app.ui.screens.account.profileSetupAfterSignIn
import com.kefe.app.ui.screens.account.profilesNote
import com.kefe.app.ui.screens.summary.SummaryUiState
import com.kefe.app.ui.screens.transaction.AddTransactionStep
import com.kefe.app.ui.screens.transaction.AddTransactionUiState
import com.kefe.app.ui.screens.transaction.UnreachableStripText
import com.kefe.app.ui.screens.transaction.cloudUnreachable
import com.kefe.app.ui.screens.transaction.ctaText
import com.kefe.app.ui.screens.transaction.footNote
import com.kefe.app.ui.screens.transaction.recordSyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hesap modu: TEK turetme ve her yuzeyin ayni sozlugu.
 *
 * NEYDI. Ekranlar uc ayri sinyalden besleniyordu: cip bulutu, ray ve yan nav
 * FIYAT tazeligini gosteriyordu; ayni an rayda "Bekliyor", cipte "Eşitleniyor",
 * seritte "Çevrimdışı" yaziyordu. "Girisli" ile "bu cihaz hesaba bagli" ayni
 * sey sayiliyordu. Burada sabitlenen:
 *   - modun tam dogruluk tablosu (oturum x baglanti x durum);
 *   - fiyat tazeligi rozeti HIC degistirmez;
 *   - hicbir etikette "Çevrimdışı" yok - hesapsiz kullanim bir ariza degil.
 */
class CloudModeTest {

    private fun signedIn(userId: String = "u1", email: String = "e@k.app") =
        AuthState.SignedIn(AuthSession(userId, email, "tok", "r", 0L))

    private val everyMode: List<CloudMode> = buildList {
        add(CloudMode.Local)
        add(CloudMode.LinkPending("e@k.app"))
        add(CloudMode.SessionLost("e@k.app"))
        CloudStatus.entries.forEach { add(CloudMode.Cloud("e@k.app", it)) }
    }

    // --- Dogruluk tablosu ---------------------------------------------------

    @Test
    fun `oturum okunmadan mod yok`() {
        assertNull(deriveCloudMode(AuthState.Unknown, null, null, CloudStatus.Synced))
        assertNull(deriveCloudMode(AuthState.Unknown, "u1", "e@k.app", CloudStatus.Synced))
    }

    @Test
    fun `oturum yok baglanti yok bu cihazda`() {
        assertEquals(CloudMode.Local, deriveCloudMode(AuthState.SignedOut, null, null, CloudStatus.Synced))
        // Bos metin baglanti sayilmaz.
        assertEquals(CloudMode.Local, deriveCloudMode(AuthState.SignedOut, "", "e@k.app", CloudStatus.Synced))
    }

    @Test
    fun `oturum yok baglanti var oturum kapandi`() {
        assertEquals(
            CloudMode.SessionLost("e@k.app"),
            deriveCloudMode(AuthState.SignedOut, "u1", "e@k.app", CloudStatus.Synced),
        )
        // E-posta kaybolmussa da mod ayni; yalniz metin bos.
        assertEquals(
            CloudMode.SessionLost(""),
            deriveCloudMode(AuthState.SignedOut, "u1", null, CloudStatus.Synced),
        )
    }

    @Test
    fun `oturum var baglanti yok baglanti yarim`() {
        assertEquals(
            CloudMode.LinkPending("e@k.app"),
            deriveCloudMode(signedIn(), null, null, CloudStatus.Synced),
        )
    }

    /** Baska bir hesaba girildi: bu cihazin kayitlari o hesaba gitmez. */
    @Test
    fun `oturum baska hesapta baglanti yarim`() {
        assertEquals(
            CloudMode.LinkPending("b@k.app"),
            deriveCloudMode(signedIn("u2", "b@k.app"), "u1", "e@k.app", CloudStatus.Synced),
        )
    }

    @Test
    fun `oturum baglantiyla ayni hesapta esitleme acik`() {
        CloudStatus.entries.forEach { status ->
            assertEquals(
                CloudMode.Cloud("e@k.app", status),
                deriveCloudMode(signedIn(), "u1", "eski@k.app", status),
                "durum $status",
            )
        }
    }

    /** Esitleme durumu yalniz bagli cihazda gorunur; digerlerini degistirmez. */
    @Test
    fun `durum bagli olmayan modu degistirmez`() {
        val local = CloudStatus.entries.map { deriveCloudMode(AuthState.SignedOut, null, null, it) }.toSet()
        val pending = CloudStatus.entries.map { deriveCloudMode(signedIn(), null, null, it) }.toSet()
        assertEquals(setOf<CloudMode?>(CloudMode.Local), local)
        assertEquals(setOf<CloudMode?>(CloudMode.LinkPending("e@k.app")), pending)
    }

    @Test
    fun `esitleme yalniz bagli hesapta calisir`() {
        assertEquals("u1", linkedUserId(signedIn("u1"), "u1"))
        assertNull(linkedUserId(signedIn("u1"), "u2"))
        assertNull(linkedUserId(signedIn("u1"), null))
        assertNull(linkedUserId(signedIn("u1"), ""))
        assertNull(linkedUserId(AuthState.SignedOut, "u1"))
        assertNull(linkedUserId(AuthState.Unknown, "u1"))
    }

    // --- Sozluk ------------------------------------------------------------

    @Test
    fun `hicbir etikette Cevrimdisi yok`() {
        val labels = everyMode.flatMap { listOf(it.shortLabel(), it.longLabel(), it.longLabel("az önce")) } +
            everyMode.mapNotNull { accountBannerCopy(it) }
                .flatMap { listOf(it.line1, it.line2, it.primary, it.secondary) } +
            everyMode.mapNotNull { accountSection(it, cloudConfigured = true, lastSyncedLabel = "az önce") }
                .flatMap { s ->
                    listOfNotNull(s.statusTitle, s.statusSubtitle, s.lastSynced) +
                        s.actions.flatMap { listOfNotNull(it.title, it.subtitle) }
                } +
            (everyMode + null).map { profilesNote(it) } +
            UnreachableStripText
        labels.forEach { label ->
            assertFalse("Çevrimdışı" in label, "'$label' Çevrimdışı iceriyor")
            assertFalse("çevrimdışı" in label.lowercase(), "'$label' çevrimdışı iceriyor")
        }
    }

    @Test
    fun `sabit sozluk`() {
        assertEquals("Bu cihazda", CloudMode.Local.shortLabel())
        assertEquals("Bağlantı yarım", CloudMode.LinkPending("e").shortLabel())
        assertEquals("Oturum kapandı", CloudMode.SessionLost("e").shortLabel())
        assertEquals("Eşitleniyor", CloudMode.Cloud("e", CloudStatus.Syncing).shortLabel())
        assertEquals("Eşitlendi", CloudMode.Cloud("e", CloudStatus.Synced).shortLabel())
        assertEquals("Eşitlenemiyor", CloudMode.Cloud("e", CloudStatus.Unreachable).shortLabel())

        assertEquals("Yalnız bu cihazda", CloudMode.Local.longLabel())
        assertEquals("Hesap bağlantısı tamamlanmadı", CloudMode.LinkPending("e").longLabel())
        assertEquals("Oturum kapandı · yeniden giriş yapın", CloudMode.SessionLost("e").longLabel())
        assertEquals("Hesapla eşitleniyor…", CloudMode.Cloud("e", CloudStatus.Syncing).longLabel())
        assertEquals("Hesapla eşitlendi · az önce", CloudMode.Cloud("e", CloudStatus.Synced).longLabel("az önce"))
        assertEquals("Hesapla eşitlendi", CloudMode.Cloud("e", CloudStatus.Synced).longLabel())
        assertEquals(
            "Hesaba ulaşılamıyor · kayıtlar bu cihazda bekliyor",
            CloudMode.Cloud("e", CloudStatus.Unreachable).longLabel(),
        )
    }

    /** Hesapsiz kullanim ne "eşit" ne "kopuk": notr, esitleme kelimesi yok. */
    @Test
    fun `bu cihazda esitleme etiketi degildir`() {
        assertFalse("Eşit" in CloudMode.Local.shortLabel())
        assertFalse("eşit" in CloudMode.Local.longLabel())
        assertEquals(CloudTone.Neutral, CloudMode.Local.tone())
        assertEquals(CloudTone.Offline, CloudMode.Cloud("e", CloudStatus.Unreachable).tone())
        assertEquals(CloudTone.Ok, CloudMode.Cloud("e", CloudStatus.Synced).tone())
    }

    /** Fiyat tazeligi ne olursa olsun rozet ayni: ray ve yan nav fiyati gostermez. */
    @Test
    fun `fiyat tazeligi rozeti degistirmez`() {
        everyMode.forEach { mode ->
            val labels = PriceFreshness.entries.map { freshness ->
                SummaryUiState(cloudMode = mode, freshness = freshness, refreshing = true).badgeLabel
            }.toSet()
            assertEquals(setOf<String?>(mode.shortLabel()), labels, "mod $mode")

            val modeLines = PriceFreshness.entries.map { freshness ->
                SummaryUiState(cloudMode = mode, freshness = freshness).navModeLine
            }.toSet()
            assertEquals(1, modeLines.size, "yan navigasyonun mod satiri fiyattan etkilenmemeli: $modeLines")
        }
    }

    @Test
    fun `yan navigasyon mod satiri`() {
        assertEquals(
            "Yalnız bu cihazda · Hesaba bağla",
            SummaryUiState(cloudMode = CloudMode.Local, cloudConfigured = true).navModeLine,
        )
        // Bulut yapilandirilmamis surumde baglanacak hesap yok.
        assertEquals("Yalnız bu cihazda", SummaryUiState(cloudMode = CloudMode.Local).navModeLine)
        assertEquals(
            "Hesapla eşitlendi · 5 dk önce",
            SummaryUiState(
                cloudMode = CloudMode.Cloud("e", CloudStatus.Synced),
                syncedAgo = "5 dk önce",
            ).navModeLine,
        )
        assertEquals("", SummaryUiState(cloudMode = null).navModeLine)
    }

    // --- Seritler ve Ayarlar ----------------------------------------------

    @Test
    fun `ozet seridi yalniz yarim baglanti ve dusen oturumda`() {
        val pending = assertNotNull(accountBannerCopy(CloudMode.LinkPending("e")))
        assertEquals("Tamamla", pending.primary)
        assertEquals("Vazgeç", pending.secondary)
        val lost = assertNotNull(accountBannerCopy(CloudMode.SessionLost("e")))
        assertEquals("Yeniden giriş yap", lost.primary)
        assertEquals("Hesapsız devam et", lost.secondary)

        assertNull(accountBannerCopy(CloudMode.Local))
        assertNull(accountBannerCopy(null))
        CloudStatus.entries.forEach { assertNull(accountBannerCopy(CloudMode.Cloud("e", it))) }
    }

    @Test
    fun `ayarlar hesap bolumu moda gore`() {
        fun actions(mode: CloudMode) =
            accountSection(mode, cloudConfigured = true, lastSyncedLabel = "az önce")!!.actions.map { it.action }

        assertEquals(listOf(AccountAction.Link), actions(CloudMode.Local))
        assertEquals(listOf(AccountAction.CompleteLink, AccountAction.DropLink), actions(CloudMode.LinkPending("e")))
        assertEquals(listOf(AccountAction.Relogin, AccountAction.DropLink), actions(CloudMode.SessionLost("e")))
        assertEquals(listOf(AccountAction.SignOut), actions(CloudMode.Cloud("e", CloudStatus.Synced)))
        assertEquals(listOf(AccountAction.SignOut), actions(CloudMode.Cloud("e", CloudStatus.Syncing)))
        // "Şimdi eşitle" YALNIZ ulasilamiyorken.
        assertEquals(
            listOf(AccountAction.SyncNow, AccountAction.SignOut),
            actions(CloudMode.Cloud("e", CloudStatus.Unreachable)),
        )

        val cloud = accountSection(CloudMode.Cloud("e@k.app", CloudStatus.Synced), true, "5 dk önce")!!
        assertEquals("e@k.app", cloud.statusSubtitle)
        assertEquals("5 dk önce", cloud.lastSynced)
        // "Son eşitleme" yalniz bagli cihazda.
        assertNull(accountSection(CloudMode.Local, true, "az önce")!!.lastSynced)

        assertEquals("Yalnız bu cihazda", accountSection(CloudMode.Local, true, "")!!.statusTitle)
    }

    @Test
    fun `bulut yapilandirilmamissa hesap bolumu yok`() {
        everyMode.forEach { assertNull(accountSection(it, cloudConfigured = false, lastSyncedLabel = "")) }
        assertNull(accountSection(null, cloudConfigured = true, lastSyncedLabel = ""))
    }

    @Test
    fun `profiller notu moda gore`() {
        assertTrue("yalnız bu cihazda" in profilesNote(CloudMode.Local))
        assertTrue("iki telefona eşitlenir" in profilesNote(CloudMode.Cloud("e", CloudStatus.Synced)))
        // Esitlenmeyen modlarda "iki telefona eşitlenir" YAZILMAZ.
        listOf(CloudMode.LinkPending("e"), CloudMode.SessionLost("e"), null).forEach {
            assertFalse("eşitlenir" in profilesNote(it), "mod $it")
        }
    }

    // --- Islem ekleme ------------------------------------------------------

    /** Serit ve "Cihaza kaydet" YALNIZ bagli cihaz hesaba ulasamiyorken. */
    @Test
    fun `ekleme seridi yalniz Esitlenemiyor`() {
        (everyMode + null).forEach { mode ->
            val s = AddTransactionUiState(step = AddTransactionStep.Amount, cloudMode = mode)
            val unreachable = mode == CloudMode.Cloud("e@k.app", CloudStatus.Unreachable)
            assertEquals(unreachable, s.cloudUnreachable, "mod $mode")
            assertEquals(if (unreachable) "Cihaza kaydet" else "Kaydet", s.ctaText, "mod $mode")
            assertFalse("Çevrimdışı" in s.ctaText)
        }
        val offline = AddTransactionUiState(
            step = AddTransactionStep.Amount,
            cloudMode = CloudMode.Cloud("e", CloudStatus.Unreachable),
            partnerName = "Merve",
        )
        assertTrue("bağlantı gelince" in offline.footNote, offline.footNote)
    }

    /**
     * Kaydin damgasi: yalniz bagli ve ulasan cihazda "Eşit". Eşitleniyor da
     * sayilir - baglanti var, kayit bir sonraki push'la gider.
     */
    @Test
    fun `kaydin esitleme damgasi`() {
        assertEquals(SyncState.Synced, CloudMode.Cloud("e", CloudStatus.Synced).recordSyncState())
        assertEquals(SyncState.Synced, CloudMode.Cloud("e", CloudStatus.Syncing).recordSyncState())
        assertEquals(SyncState.Pending, CloudMode.Cloud("e", CloudStatus.Unreachable).recordSyncState())
        assertEquals(SyncState.Pending, CloudMode.Local.recordSyncState())
        assertEquals(SyncState.Pending, CloudMode.LinkPending("e").recordSyncState())
        assertEquals(SyncState.Pending, CloudMode.SessionLost("e").recordSyncState())
        assertEquals(SyncState.Pending, (null as CloudMode?).recordSyncState())
    }

    // --- Giristen sonra nereye ------------------------------------------------

    /**
     * Kod dogrulaninca: yalniz AYNI hesaba yeniden giren ve profili secili cihaz
     * dogrudan Ozet'e gider; digerleri "bu telefon kimin" adimina. NEYDI:
     * Ayarlar'dan giren kurulu cihaz Ozet'e gidiyor, hesabin adlari devralininca
     * eski secim (member_owner) kaliyor ve telefon sessizce diger kisi oluyordu.
     */
    @Test
    fun `giristen sonra profil adimi`() {
        assertFalse(profileSetupAfterSignIn(CloudMode.Cloud("e", CloudStatus.Syncing), "member_owner"))
        assertTrue(profileSetupAfterSignIn(CloudMode.Cloud("e", CloudStatus.Syncing), null))
        assertTrue(profileSetupAfterSignIn(CloudMode.LinkPending("e"), "member_owner"))
        assertTrue(profileSetupAfterSignIn(CloudMode.Local, "member_owner"))
        assertTrue(profileSetupAfterSignIn(null, "member_owner"))
    }
}
