package com.kefe.app.ui.screens.account

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Acilis kilidi yalniz KURULUMDAN SONRA.
 *
 * Kilit varsayilan olarak acik ve once kurulum durumuna bakmiyordu: yeni
 * kurulumun ilk ekrani "Kefe kilitli" oluyor, acilinca giris formu hic
 * gorunmeden "Profiller"e geciliyordu. Hesabi olan kullanici e-postasini
 * yazacagi yeri gormedi, telefon hesaba baglanmadi, kayitlar inmedi.
 */
class LaunchLockTest {

    @Test
    fun yeniKurulumdaKilitYOK() {
        assertFalse(isLaunchLocked(lockEnabled = true, onboarded = false, unlockedThisLaunch = false))
    }

    @Test
    fun kurulumdanSonraKilitVar() {
        assertTrue(isLaunchLocked(lockEnabled = true, onboarded = true, unlockedThisLaunch = false))
    }

    @Test
    fun buAcilistaAcildiysaKilitYok() {
        assertFalse(isLaunchLocked(lockEnabled = true, onboarded = true, unlockedThisLaunch = true))
    }

    @Test
    fun kilitKapaliysaHicYok() {
        assertFalse(isLaunchLocked(lockEnabled = false, onboarded = true, unlockedThisLaunch = false))
    }
}
