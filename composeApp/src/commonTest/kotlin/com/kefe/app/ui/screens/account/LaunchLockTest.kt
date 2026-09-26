package com.kefe.app.ui.screens.account

import com.kefe.app.domain.repository.PreferenceKeys
import com.kefe.app.domain.repository.lockEnabled
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Acilis kilidi ne zaman devrede.
 *
 * NEYDI:
 *   - kilit yalniz "onboarded"a bakiyordu; o bayrak profil seciminden ONCE
 *     yazildigi icin yeni kurulum, henuz tek kaydi yokken bir sonraki acilista
 *     "Kefe kilitli" ile aciliyordu;
 *   - masaustunde ve kimligi tanimsiz telefonda kilit ekrani bir an cizilip
 *     hemen aciliyordu;
 *   - varsayilan iki ayri yerde "acik" yaziliydi.
 */
class LaunchLockTest {

    private fun locked(
        lockEnabled: Boolean = true,
        setupDone: Boolean = true,
        gateAvailable: Boolean = true,
        unlockedThisLaunch: Boolean = false,
    ) = isLaunchLocked(lockEnabled, setupDone, gateAvailable, unlockedThisLaunch)

    @Test
    fun kurulumBitmisKilitAcikVeKimlikSorulabiliyorsaKilitli() {
        assertTrue(locked())
    }

    @Test
    fun kurulumBitmedenHICBIRDurumdaKilitYok() {
        for (enabled in listOf(true, false)) for (gate in listOf(true, false)) for (unlocked in listOf(true, false)) {
            assertFalse(
                locked(lockEnabled = enabled, setupDone = false, gateAvailable = gate, unlockedThisLaunch = unlocked),
                "setupDone=false kilitlememeli (enabled=$enabled gate=$gate unlocked=$unlocked)",
            )
        }
    }

    @Test
    fun kimlikSorulamiyorsaHICBIRDurumdaKilitYok() {
        for (enabled in listOf(true, false)) for (setup in listOf(true, false)) for (unlocked in listOf(true, false)) {
            assertFalse(
                locked(lockEnabled = enabled, setupDone = setup, gateAvailable = false, unlockedThisLaunch = unlocked),
                "gateAvailable=false kilitlememeli (enabled=$enabled setup=$setup unlocked=$unlocked)",
            )
        }
    }

    @Test
    fun buAcilistaAcildiysaKilitYok() {
        assertFalse(locked(unlockedThisLaunch = true))
    }

    @Test
    fun kilitKapaliysaKilitYok() {
        assertFalse(locked(lockEnabled = false))
    }

    @Test
    fun anahtariOlmayanEskiKurulumACIKOkunur() {
        // Kilit once varsayilan acikti ve anahtar yalniz dokunulunca yaziliyordu;
        // o telefonlar kilitli acilisa alisik, sessizce kapanmamali.
        assertTrue(emptyMap<String, String>().lockEnabled())
    }

    @Test
    fun yazilmisDegerAynenOkunur() {
        assertFalse(mapOf(PreferenceKeys.BiometricLock to "false").lockEnabled())
        assertTrue(mapOf(PreferenceKeys.BiometricLock to "true").lockEnabled())
    }

    @Test
    fun profilSecilmedenKurulumBitmisSayilmaz() {
        assertTrue(launchSetupDone(onboarded = true, activeMemberId = "member_owner"))
        // "Atla" deyip profil ekraninda kapatilan uygulama: onboarded yazili,
        // profil yok.
        assertFalse(launchSetupDone(onboarded = true, activeMemberId = null))
        assertFalse(launchSetupDone(onboarded = false, activeMemberId = "member_owner"))
        // Disk henuz okunmadi.
        assertFalse(launchSetupDone(onboarded = null, activeMemberId = "member_owner"))
    }

    @Test
    fun kilitliBaslayanAcilisAcilanaKadarKilitli() {
        val start = unlockedAtLaunchStart(lockEnabled = true, setupDone = true, gateAvailable = true)
        assertFalse(start)
        assertTrue(locked(unlockedThisLaunch = start))
    }

    @Test
    fun surecOrtasindaAcilanKilitBuAcilistaKilitlemez() {
        // Kilitsiz baslayan acilis "acilmis" sayilir; Ayarlar'dan kilit acilinca
        // `locked` true'ya donmemeli - yoksa "Tüm verileri sil" sonrasi kok
        // giris ekrani "Kilitli" gosteriyordu.
        val start = unlockedAtLaunchStart(lockEnabled = false, setupDone = true, gateAvailable = true)
        assertTrue(start)
        assertFalse(locked(lockEnabled = true, unlockedThisLaunch = start))
    }

    @Test
    fun kurulumSurecOrtasindaBitinceKilitBuAcilistaGelmez() {
        // Kurulumu yarida kalmis eski kurulum (anahtar yok, kilit acik okunur):
        // profil secilip iceri girilince ayni acilista kimlik sorulmaz.
        val start = unlockedAtLaunchStart(lockEnabled = true, setupDone = false, gateAvailable = true)
        assertTrue(start)
        assertFalse(locked(unlockedThisLaunch = start))
    }

    /**
     * Activity yeniden yaratildi (katlama, bolunmus ekran, dil): Compose durumu
     * sifirlanir ama kilit VM'i kalir. Bu bir ACILIS degil - kilit bu
     * Activity'de acildiysa kok kilide donmez. NEYDI: kok kilit oluyor, kalan
     * `unlocked` kullaniciyi hemen iceri aliyor, kilit ekrani da ayni karede
     * bakiyelerin ustunde sistem istemi aciyordu.
     */
    @Test
    fun yenidenYaratilanEkranAcilmisKilidiKorur() {
        val start = unlockedAtLaunchStart(
            lockEnabled = true,
            setupDone = true,
            gateAvailable = true,
            unlockedBefore = true,
        )
        assertTrue(start)
        assertFalse(locked(unlockedThisLaunch = start))
    }

    @Test
    fun acilmisKilitIkinciIstemAcmaz() {
        assertTrue(LockUiState().canStartUnlock())
        assertFalse(LockUiState(unlocking = true).canStartUnlock(), "istem zaten acik")
        assertFalse(LockUiState(unlocked = true).canStartUnlock(), "kilit zaten acildi")
    }

    @Test
    fun ekranDurumununVarsayilaniKAPALI() {
        // Diske bakilmadan once kilit "yok" sayilir; eski kurulumu diskteki
        // okuma acar (prefsLoaded beklenir).
        assertFalse(SettingsUiState().biometricLock)
        assertFalse(SettingsUiState().lockAvailable)
    }
}
