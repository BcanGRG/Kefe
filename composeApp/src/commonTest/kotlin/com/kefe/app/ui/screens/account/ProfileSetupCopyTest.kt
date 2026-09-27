package com.kefe.app.ui.screens.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Profiller / Bu telefon kimin?" ekraninin metinleri.
 *
 * NEYDI: secim metni "Hesabınızda iki profil bulduk" diyordu; profillerin cihazda
 * adlandirilmis olmasi yetiyordu - hic hesabi olmayan biri de ("Tüm verileri
 * sil" sonrasi) bunu, yaninda "giriş yap" baglantisiyla goruyordu.
 */
class ProfileSetupCopyTest {

    private val ready = ProfileSetupUiState(phase = ProfileSetupPhase.Ready)

    @Test
    fun `hesap sozu yalniz girisliyken`() {
        val local = ready.copy(
            signedIn = false,
            editingNames = false,
            profilesNamed = true,
            accountHasProfiles = false,
            ownerName = "Volkan",
            partnerName = "Ayşe",
        )
        assertEquals("Bu cihazda iki profil var. Hangisi sizsiniz?", local.readyBody())

        val account = local.copy(signedIn = true, accountHasProfiles = true, ownerName = "Burak Can", partnerName = "Merve")
        assertEquals("Hesabınızda iki profil var: Burak Can ve Merve. Bu cihaz hangisinin?", account.readyBody())
    }

    @Test
    fun `hesapsiz olusturma profillerin nerede durdugunu soyler`() {
        val body = ready.copy(signedIn = false).readyBody()
        assertTrue(body.startsWith("Kefe iki kişilik: siz ve eşiniz."), body)
        assertTrue("Hesap kullanmadığınız için profiller yalnız bu cihazda durur." in body, body)
    }

    @Test
    fun `bos hesapta olusturma adlarin hesaba gidecegini soyler`() {
        val body = ready.copy(signedIn = true).readyBody()
        assertTrue("hesabınıza da kaydedilir" in body, body)
        assertFalse("yalnız bu cihazda" in body, body)
    }

    @Test
    fun `adlari duzenlerken duzenleme metni`() {
        val body = ready.copy(editingNames = true, profilesNamed = true).readyBody()
        assertTrue(body.startsWith("Profil adlarını düzenleyin."), body)
    }

    @Test
    fun `hesap indirilemeden adsiz secim Tamamlayi soyler`() {
        val body = ready.copy(signedIn = true, editingNames = false, profilesNamed = false).readyBody()
        assertTrue("\"Tamamla\"" in body, body)
    }

    @Test
    fun `hesaba bagla yalniz girissiz ve bulut varken`() {
        assertTrue(ready.copy(signedIn = false, cloudConfigured = true).showLinkFooter)
        assertFalse(ready.copy(signedIn = true, cloudConfigured = true).showLinkFooter)
        assertFalse(ready.copy(signedIn = false, cloudConfigured = false).showLinkFooter)
    }

    /** Adsiz secimde (hesap indirilemedi) adlar duzenlenmez: hesabinkiler gelecek. */
    @Test
    fun `adlari duzenle yalniz adlandirilmis secimde`() {
        assertTrue(ready.copy(editingNames = false, profilesNamed = true).canEditNames)
        assertFalse(ready.copy(editingNames = false, profilesNamed = false, signedIn = true).canEditNames)
        assertFalse(ready.copy(editingNames = true, profilesNamed = true).canEditNames)
        assertTrue(
            ready.copy(editingNames = false, profilesNamed = true, signedIn = true, accountDownloaded = true).canEditNames,
        )
    }

    /**
     * Hesap indirilemeden gecildi ("Şimdilik hesapsız devam et"), cihazda adlar
     * var: "Hesabınızda" DENMEZ ve adlar duzenlenmez. NEYDI: cihazda yazilmis
     * adlar "Hesabınızda iki profil var: Volkan ve Ayşe" diye sunuluyordu.
     */
    @Test
    fun `indirilemeyen hesapta cihaz adlari hesabin sayilmaz`() {
        val offline = ready.copy(
            signedIn = true,
            accountDownloaded = false,
            editingNames = false,
            profilesNamed = true,
            ownerName = "Volkan",
            partnerName = "Ayşe",
        )
        assertTrue(offline.offlinePick)
        assertFalse("Hesabınızda" in offline.readyBody(), offline.readyBody())
        assertTrue(offline.readyBody().startsWith("Bu cihazda iki profil var."), offline.readyBody())
        assertTrue("\"Tamamla\"" in offline.readyBody(), offline.readyBody())
        assertFalse(offline.canEditNames)
    }

    /** Hesap indirildi ama bos: cihazdaki adlar baglantiyla hesaba gider. */
    @Test
    fun `bos hesapta cihaz adlari hesaba gidecek`() {
        val body = ready.copy(
            signedIn = true,
            accountDownloaded = true,
            editingNames = false,
            profilesNamed = true,
        ).readyBody()
        assertTrue(body.startsWith("Bu cihazda iki profil var."), body)
        assertTrue("hesabınıza da kaydedilir" in body, body)
    }

    /** Girisliyken e-posta ve "Farklı e-postayla gir" gorunur; girissizken yok. */
    @Test
    fun `girisliyken e-posta satiri`() {
        assertTrue(ready.copy(signedIn = true, accountEmail = "a@k.app").showAccountFooter)
        assertFalse(ready.copy(signedIn = false, accountEmail = "a@k.app").showAccountFooter)
        assertFalse(ready.copy(signedIn = true, accountEmail = null).showAccountFooter)
    }
}
