package com.kefe.app.navigation

import com.kefe.app.ui.screens.account.SignInPurpose
import com.kefe.app.ui.screens.account.isLaunchLocked
import com.kefe.app.ui.screens.account.launchSetupDone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Acilista yiginin koku.
 *
 * NEYDI: "onboarded degil ya da kilitli" tek bir LoginKey'e gidiyordu; giris
 * formu, kilit ve ilk acilis ayni ekranin asamalariydi. Yeni kurulum hesapsiz
 * kullanmak istese de once bir e-posta formuyla karsilaniyordu; kod dogrulanip
 * profil secilmeden kapatilan telefon yine e-posta soruyordu.
 */
class RootRouteTest {

    private val member = "member_owner"

    @Test
    fun `kilitliyse her durumda kilit ekrani`() {
        for (onboarded in listOf(true, false)) for (signedIn in listOf(true, false)) {
            assertEquals(LockKey, rootFor(locked = true, onboarded, member, signedIn))
        }
    }

    @Test
    fun `kurulum bitmisse Ozet`() {
        assertEquals(SummaryKey, rootFor(locked = false, onboarded = true, activeMemberId = member, signedIn = false))
        assertEquals(SummaryKey, rootFor(locked = false, onboarded = true, activeMemberId = member, signedIn = true))
    }

    @Test
    fun `tanitim gecilmis ama profil secilmemisse profil adimi`() {
        // "Atla" deyip profil ekraninda kapatilan uygulama.
        assertEquals(ProfileSetupKey, rootFor(locked = false, onboarded = true, activeMemberId = null, signedIn = false))
        assertEquals(ProfileSetupKey, rootFor(locked = false, onboarded = true, activeMemberId = null, signedIn = true))
    }

    @Test
    fun `oturum acik ama tanitim gecilmemisse profil adimi`() {
        // Kod dogrulandi, profil secilmeden kapatildi: e-posta yeniden sorulmaz.
        assertEquals(ProfileSetupKey, rootFor(locked = false, onboarded = false, activeMemberId = null, signedIn = true))
    }

    @Test
    fun `ilk acilista hosgeldin`() {
        assertEquals(WelcomeKey, rootFor(locked = false, onboarded = false, activeMemberId = null, signedIn = false))
        // Profil secili ama tanitim bayragi yok (orn. bayrak silinmis): yine
        // hosgeldin - Ozet'e yalniz kurulum bitince girilir.
        assertEquals(WelcomeKey, rootFor(locked = false, onboarded = false, activeMemberId = member, signedIn = false))
    }

    /**
     * Yeni kurulum HICBIR kombinasyonda kilit ekranina acilmaz: kilit yalniz
     * kurulum bitmisken (tanitim + profil) devrede. Kilit ve kok kurallari
     * birlikte: kurulum bitmemisse kok ya hosgeldin ya profil adimi.
     */
    @Test
    fun `kurulmamis uygulama kilide acilmaz`() {
        for (lockEnabled in listOf(true, false)) for (gate in listOf(true, false)) for (signedIn in listOf(true, false)) {
            for (onboarded in listOf(false, true)) {
                val setupDone = launchSetupDone(onboarded, activeMemberId = null)
                val locked = isLaunchLocked(lockEnabled, setupDone, gate, unlockedThisLaunch = false)
                val root = rootFor(locked, onboarded, activeMemberId = null, signedIn = signedIn)
                assertTrue(root == WelcomeKey || root == ProfileSetupKey, "kok $root olmamali")
            }
        }
    }

    /**
     * "Tüm verileri sil" sonrasi kok soguk acilisla ayni kuraldan gelir. Oturum
     * silmeden sag kaliyorsa hosgeldin DEGIL profil adimi. NEYDI: kok sabit
     * hosgeldindi; "Bu cihazda kullan" ("Hesap gerekmez") secen girisli
     * kullanici profil adiminda hesabi yeniden indirip ona baglaniyordu.
     */
    @Test
    fun `silme sonrasi kok oturuma gore`() {
        assertEquals(WelcomeKey, rootFor(locked = false, onboarded = false, activeMemberId = null, signedIn = false))
        assertEquals(ProfileSetupKey, rootFor(locked = false, onboarded = false, activeMemberId = null, signedIn = true))
    }

    /**
     * "Farklı e-postayla gir": giris ekraninin altinda donulecek bir yer kalir.
     * NEYDI: ilk acilista hesapla giren profil adimina KOK olarak geliyordu;
     * geri tusu uygulamadan cikiyordu, yanlis e-postayi duzeltmenin yolu yoktu.
     */
    @Test
    fun `farkli e-postayla giris donulecek yeri birakir`() {
        // Yeni kurulum: hosgeldin, giris ilk acilis amaciyla.
        assertEquals(
            SignInRestart(WelcomeKey, SignInPurpose.FirstRun),
            restartSignIn(pushedOverApp = false, activeMemberId = null),
        )
        // Ayarlar'dan baglanan kurulu cihaz: Ozet, giris baglama amaciyla.
        assertEquals(
            SignInRestart(SummaryKey, SignInPurpose.Link),
            restartSignIn(pushedOverApp = false, activeMemberId = member),
        )
        // "Tamamla" ile uygulamanin ustune itilmis: yigin degismez.
        assertEquals(
            SignInRestart(null, SignInPurpose.Link),
            restartSignIn(pushedOverApp = true, activeMemberId = member),
        )
    }

    @Test
    fun `hesap akisi ekranlari kromsuz`() {
        assertTrue(WelcomeKey.isAccountFlow())
        assertTrue(LockKey.isAccountFlow())
        assertTrue(OnboardingKey.isAccountFlow())
        assertTrue(ProfileSetupKey.isAccountFlow())
        for (purpose in SignInPurpose.entries) assertTrue(SignInKey(purpose).isAccountFlow())

        assertFalse(SummaryKey.isAccountFlow())
        assertFalse(SettingsKey.isAccountFlow())
        assertFalse(AssetsKey.isAccountFlow())
        assertFalse(GoalsKey.isAccountFlow())
    }
}
