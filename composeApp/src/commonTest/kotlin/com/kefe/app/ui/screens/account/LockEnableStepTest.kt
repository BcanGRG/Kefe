package com.kefe.app.ui.screens.account

import com.kefe.app.security.BiometricAvailability
import com.kefe.app.security.BiometricResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Açılış kilidi" anahtarini acmak.
 *
 * NEYDI: anahtar dokunulur dokunulmaz "true" yaziyordu. Kimligi tanimsiz
 * telefonda kilit acik gorunup hicbir sey sormuyor, tanimli olanda da kullanici
 * kilidin calistigini ancak bir sonraki acilista ogreniyordu. Burada sabitlenen:
 * kilit yalniz basarili bir dogrulamadan sonra yazilir; acilamiyorsa sebebi
 * soylenir; karsiligi olmayan cihazda satir hic cizilmez.
 */
class LockEnableStepTest {

    @Test
    fun kimlikSorulabiliyorsaOnceDogrulanir() {
        assertEquals(LockEnableStep.Authenticate, lockEnableStep(BiometricAvailability.Available))
    }

    @Test
    fun kimlikTanimsizsaAcilmazVeSebebiSoylenir() {
        val step = assertIs<LockEnableStep.Refuse>(lockEnableStep(BiometricAvailability.NotEnrolled))
        assertTrue("tanımlı değil" in step.message)
        assertTrue("cihaz ayarlarından" in step.message)
    }

    @Test
    fun donanimYaDaPlatformYoksaHicbirSeyYapilmaz() {
        assertEquals(LockEnableStep.Unavailable, lockEnableStep(BiometricAvailability.NoHardware))
        assertEquals(LockEnableStep.Unavailable, lockEnableStep(BiometricAvailability.Unsupported))
    }

    @Test
    fun satirYalnizKilidinKarsiligiOlanCihazdaGorunur() {
        assertTrue(lockRowVisible(BiometricAvailability.Available))
        // Kimligi tanimsiz cihazda satir durur: acilmak istenince sebebini yazar.
        assertTrue(lockRowVisible(BiometricAvailability.NotEnrolled))
        assertFalse(lockRowVisible(BiometricAvailability.NoHardware))
        // Masaustu hep buraya duser.
        assertFalse(lockRowVisible(BiometricAvailability.Unsupported))
    }

    @Test
    fun gorunenHerSatirinBirYanitiVarGizleneninYok() {
        // Iki karar ayrismasin: gorunen satir dokununca sessiz kalmamali,
        // gizlenen satirin yolu da hicbir sey yapmamali.
        BiometricAvailability.entries.forEach { availability ->
            val step = lockEnableStep(availability)
            assertEquals(
                lockRowVisible(availability),
                step != LockEnableStep.Unavailable,
                "$availability icin satir ve adim tutarsiz",
            )
        }
    }

    @Test
    fun kimligiTanimsizEskiKurulumdaAnahtarKAPALIGorunur() {
        // Anahtari olmayan eski kurulum "acik" okunur; ama kimlik
        // sorulamiyorsa acilis hicbir sey sormaz. Anahtar "acik" ve "sorulur"
        // dememeli.
        assertFalse(lockSwitchOn(lockEnabled = true, availability = BiometricAvailability.NotEnrolled))
        assertTrue(lockSwitchOn(lockEnabled = true, availability = BiometricAvailability.Available))
        assertFalse(lockSwitchOn(lockEnabled = false, availability = BiometricAvailability.Available))
    }

    @Test
    fun anahtarAcikGorunuyorsaAcilisGercektenKilitlenir() {
        // Anahtar ile acilis kapisi ayni kurali okumali: kurulmus bir
        // uygulamada anahtar acik <=> acilista kimlik sorulur.
        for (enabled in listOf(true, false)) {
            BiometricAvailability.entries.forEach { availability ->
                val gate = lockCanApply(availability)
                assertEquals(
                    lockSwitchOn(enabled, availability),
                    isLaunchLocked(enabled, setupDone = true, gateAvailable = gate, unlockedThisLaunch = false),
                    "enabled=$enabled availability=$availability icin anahtar ile kapi tutarsiz",
                )
            }
        }
    }

    @Test
    fun kilitYalnizKimlikSorulabilenCihazdaUygulanir() {
        assertTrue(lockCanApply(BiometricAvailability.Available))
        assertFalse(lockCanApply(BiometricAvailability.NotEnrolled))
        assertFalse(lockCanApply(BiometricAvailability.NoHardware))
        assertFalse(lockCanApply(BiometricAvailability.Unsupported))
    }

    @Test
    fun dogrulamaBasarirsaKilitYazilirVeSoylenir() {
        val outcome = lockEnableOutcome(BiometricResult.Success)
        assertTrue(outcome.enable)
        assertEquals("Açılış kilidi açık. Kefe her açılışta kimliğinizi soracak.", outcome.notice)
    }

    @Test
    fun vazgecilirseKilitKapaliKalirVeHicbirSeyYazilmaz() {
        val outcome = lockEnableOutcome(BiometricResult.Cancelled)
        assertFalse(outcome.enable)
        assertNull(outcome.notice)
    }

    @Test
    fun dogrulanamazsaKilitKapaliKalirVeSoylenir() {
        val outcome = lockEnableOutcome(BiometricResult.Failed("sensor hatasi"))
        assertFalse(outcome.enable)
        assertEquals("Doğrulanamadı; kilit açılmadı.", outcome.notice)
    }
}
